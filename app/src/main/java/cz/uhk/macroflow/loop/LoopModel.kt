package cz.uhk.macroflow.loop

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Jeden den uživatele: všechno, co se navzájem ovlivňuje. Chybějící údaj = null (nic se nedomýšlí). */
data class DayIn(
    val day: Int,                       // epoch day
    val sleepH: Double? = null,         // spánek, který skončil tento den (noc před ním)
    val sleepScore: Int? = null,
    val restHr: Int? = null,
    val stress: Int? = null,            // denní průměr (jen Garmin)
    val battery: Int? = null,           // Body Battery po spánku (jen Garmin)
    val energy: Int? = null,            // ranní rituál 1–5
    val sleepQ: Int? = null,
    val hunger: Int? = null,
    val steps: Int? = null,
    val waterMl: Int? = null,
    val volume: Double = 0.0,           // součet váha × opakování za den
    val intakeKcal: Double? = null,
    val targetKcal: Double? = null
)

data class Situation(
    val today: Int, val days: List<DayIn>, val trainingToday: Boolean,
    val cutting: Boolean? = null,       // true = hubnutí, false = jiný cíl, null = neznámo
    val usualBedtimeMin: Int? = null,   // obvyklé usínání v minutách od půlnoci
    val weeklyLossPct: Double? = null   // úbytek váhy za týden v % váhy (kladné = hubne)
)

data class Part(val name: String, val goodness: Double, val weight: Double, val note: String)
data class Readiness(val score: Int, val parts: List<Part>, val coverage: Double)

data class Advice(
    val score: Int, val band: String, val volumeFactor: Double, val rirDelta: Int,
    val kcalPct: Double, val waterDeltaMl: Int, val bedtimeMin: Int?,
    val headline: String, val reasons: List<String>, val warnings: List<String>
) {
    /** Úprava, kterou má smysl nabídnout k potvrzení (jinak stačí text). */
    val hasProposal get() = kcalPct != 0.0 || waterDeltaMl != 0
}

data class LinkResult(val id: String, val sentence: String, val effect: Double, val n: Int, val dots: Int)

/** Čisté jádro uzavřeného kruhu: z dat uživatele spočítá připravenost a z ní úpravy. Bez Androidu. */
object LoopModel {
    const val BASELINE_DAYS = 28
    private const val MIN_COVERAGE = 0.35
    const val MAX_KCAL_PCT = 0.10

    private fun mean(l: List<Double>) = l.sum() / l.size
    private fun sd(l: List<Double>): Double { val m = mean(l); return sqrt(l.sumOf { (it - m) * (it - m) } / max(1, l.size - 1)) }

    /** Z-skóre proti vlastní historii. Málo dat = null; směrodatná odchylka má spodní mez, ať drobný šum nevyletí do extrému. */
    internal fun z(v: Double, hist: List<Double>, minN: Int, minSd: Double): Double? =
        if (hist.size < minN) null else (v - mean(hist)) / max(sd(hist), minSd)

    private fun num(v: Double, digits: Int = 1) = String.format(Locale.US, "%.${digits}f", v).replace('.', ',').removeSuffix(",0")

    private fun personal(z: Double?) = z?.let { (50 - 20 * it).coerceIn(0.0, 100.0) }

    fun readiness(s: Situation): Readiness? {
        val today = s.days.firstOrNull { it.day == s.today }
        val hist = s.days.filter { it.day in (s.today - BASELINE_DAYS) until s.today }
        fun at(d: Int) = s.days.firstOrNull { it.day == d }
        val parts = ArrayList<Part>()

        // Spánek (35 %)
        today?.sleepH?.let { h ->
            val abs = ((h - 4.0) / 4.0).coerceIn(0.0, 1.0) * 100
            val pers = z(h, hist.mapNotNull { it.sleepH }, 7, 0.4)?.let { 50 + 20 * it }?.coerceIn(0.0, 100.0)
            var g = if (pers != null) (abs + pers) / 2 else abs
            today.sleepScore?.let { g = (2 * g + it) / 3 }
            parts += Part("spánek", g, 0.35, "noc ${num(h)} h")
        }
        // Ranní rituál (25 %)
        today?.let { t ->
            val v = listOfNotNull(t.energy?.let { it * 0.6 }, t.sleepQ?.let { it * 0.4 })
            if (t.energy != null || t.sleepQ != null) {
                val w = (if (t.energy != null) 0.6 else 0.0) + (if (t.sleepQ != null) 0.4 else 0.0)
                val c = v.sum() / w
                val histC = hist.mapNotNull { d -> if (d.energy == null && d.sleepQ == null) null else
                    (listOfNotNull(d.energy?.let { it * 0.6 }, d.sleepQ?.let { it * 0.4 }).sum() /
                        ((if (d.energy != null) 0.6 else 0.0) + (if (d.sleepQ != null) 0.4 else 0.0))) }
                val abs = (c - 1) / 4 * 100
                val pers = z(c, histC, 5, 0.5)?.let { 50 + 20 * it }?.coerceIn(0.0, 100.0)
                parts += Part("ráno", if (pers != null) (abs + pers) / 2 else abs, 0.25,
                    t.energy?.let { "energie $it/5" } ?: "kvalita spánku ${t.sleepQ}/5")
            }
        }
        // Zátěž včerejška a předvčerejška vůči vlastnímu průměru tréninkového dne (20 %)
        val trainVols = hist.map { it.volume }.filter { it > 0 }
        if (trainVols.isNotEmpty()) {
            val y1 = at(s.today - 1)?.volume ?: 0.0; val y2 = at(s.today - 2)?.volume ?: 0.0
            val ratio = (y1 + 0.5 * y2) / (1.5 * mean(trainVols))
            val g = if (ratio == 0.0) 90.0 else (95 - 40 * ratio).coerceIn(10.0, 95.0)
            parts += Part("zátěž", g, 0.20, if (ratio == 0.0) "odpočatý" else if (ratio > 0.9) "těžký včerejšek" else "lehčí včerejšek")
        }
        // Fyziologie: klidový tep, stres včera, Body Battery (10 %)
        val phys = ArrayList<Double>(); val physNotes = ArrayList<String>()
        today?.restHr?.let { hr -> z(hr.toDouble(), hist.mapNotNull { it.restHr?.toDouble() }, 7, 1.5)?.let { phys += personal(it)!!; if (it > 1) physNotes += "vyšší klidový tep" } }
        at(s.today - 1)?.stress?.takeIf { it >= 0 }?.let { st -> z(st.toDouble(), hist.mapNotNull { it.stress?.takeIf { v -> v >= 0 }?.toDouble() }, 7, 5.0)?.let { phys += personal(it)!!; if (it > 1) physNotes += "stres včera" } }
        today?.battery?.let { phys += it.toDouble().coerceIn(0.0, 100.0); if (it < 40) physNotes += "nízká baterie" }
        if (phys.isNotEmpty()) parts += Part("tělo", mean(phys), 0.10, physNotes.joinToString(", ").ifEmpty { "v normě" })
        // Výživa: příjem proti cíli za poslední 3 dny (10 %)
        val nut = (1..3).mapNotNull { at(s.today - it) }.filter { it.intakeKcal != null && (it.targetKcal ?: 0.0) > 0 }
        if (nut.size >= 2) {
            val dev = (nut.sumOf { it.intakeKcal!! } - nut.sumOf { it.targetKcal!! }) / nut.sumOf { it.targetKcal!! }
            parts += Part("jídlo", (100 + 200 * min(0.0, dev)).coerceIn(20.0, 100.0), 0.10,
                if (dev < -0.1) "málo jídla (${(dev * 100).roundToInt()} %)" else "příjem v pořádku")
        }

        val cover = parts.sumOf { it.weight }
        if (cover < MIN_COVERAGE) return null
        val score = parts.sumOf { it.goodness * it.weight } / cover
        return Readiness(score.roundToInt().coerceIn(0, 100), parts, cover)
    }

    fun volumeFactor(score: Int): Double = when {
        score >= 85 -> 1.05
        score >= 65 -> 1.0
        else -> (0.8 + 0.2 * (score - 30).coerceAtLeast(0) / 35.0).let { (it * 100).roundToInt() / 100.0 }
    }

    fun rirDelta(score: Int) = if (score < 35) 2 else if (score < 50) 1 else 0

    fun band(score: Int) = when { score >= 75 -> "Jsi v dobré formě"; score >= 55 -> "Forma průměrná"; score >= 40 -> "Slabší den"; else -> "Tělo potřebuje zpomalit" }

    fun advice(s: Situation, r: Readiness): Advice {
        val today = s.days.firstOrNull { it.day == s.today }
        val vf = volumeFactor(r.score); val rir = rirDelta(r.score)
        val reasons = ArrayList<String>(); val warnings = ArrayList<String>()

        var pct = 0.0
        if (s.cutting == true && (today?.sleepH ?: 9.0) < 6.0) { pct += 0.05; reasons += "po krátké noci jemnější deficit (+5 % kcal)" }
        if (s.trainingToday && r.score >= 60) { pct += 0.03; reasons += "trénink při dobré formě: víc sacharidů (+3 % kcal)" }
        if (s.cutting == true && (s.weeklyLossPct ?: 0.0) > 1.0) {
            pct += 0.05; reasons += "hubneš rychle (${num(s.weeklyLossPct!!)} % váhy týdně): přidávám +5 % kcal"
            warnings += "Úbytek přes 1 % váhy týdně už riskuje ztrátu svalů. Zvaž pár dní na udržovací kalorie."
        }
        pct = (pct.coerceIn(-MAX_KCAL_PCT, MAX_KCAL_PCT) * 100).roundToInt() / 100.0

        val water = if (s.trainingToday) 300 else 0
        if (water > 0) reasons += "trénink: +300 ml vody"

        // Spánkový dluh za posledních 7 nocí proti 8 h
        val last = s.days.filter { it.day in (s.today - 6)..s.today }.mapNotNull { it.sleepH }
        val bedtime = if (last.size >= 3 && s.usualBedtimeMin != null) {
            val earlier = (((8.0 - mean(last)).coerceAtLeast(0.0) * 45) / 5).roundToInt() * 5
            if (earlier >= 10) { reasons += "spánkový dluh: do postele o $earlier min dřív"; Math.floorMod(s.usualBedtimeMin - earlier.coerceAtMost(60), 1440) } else null
        } else null

        val weak = r.parts.filter { it.goodness < 55 }.sortedBy { it.goodness }.take(2)
        val why = if (weak.isEmpty()) "" else ": " + weak.joinToString(" a ") { it.note }
        val train = when {
            s.trainingToday && vf < 0.97 -> " Dnes ber objem na ${(vf * 100).roundToInt()} % plánu" +
                (if (rir > 0) " a nech si o $rir opakování víc v rezervě." else ".")
            s.trainingToday && vf > 1.0 -> " Dnes můžeš objem mírně přidat (+5 %)."
            s.trainingToday -> " Trénuj podle plánu."
            r.score < 40 -> " Dnes raději odpočinek nebo klidná chůze."
            else -> ""
        }
        return Advice(r.score, band(r.score), vf, rir, pct, water, bedtime, "${band(r.score)}$why.$train", reasons, warnings)
    }

    // ── Naučené vazby ───────────────────────────────────────────────────────

    /**
     * Rozdělí dny podle mediánu x a porovná průměr y. Efekt se stáhne k nule podle počtu dní (málo dat = opatrný odhad),
     * jistota: 1 tečka do 10 dní, 2 do 20 nebo když rozdíl není nad šumem, jinak 3.
     */
    fun link(id: String, pairs: List<Pair<Double, Double>>, minEffect: Double, sentence: (median: Double, effect: Double) -> String): LinkResult? {
        if (pairs.size < 6) return null
        val xs = pairs.map { it.first }.sorted()
        val med = if (xs.size % 2 == 1) xs[xs.size / 2] else (xs[xs.size / 2 - 1] + xs[xs.size / 2]) / 2
        val low = pairs.filter { it.first < med }.map { it.second }; val high = pairs.filter { it.first >= med }.map { it.second }
        if (low.size < 3 || high.size < 3) return null
        val raw = mean(high) - mean(low)
        val shrunk = raw * pairs.size / (pairs.size + 6.0)
        if (abs(shrunk) < minEffect) return null
        val se = sqrt(sd(low).let { it * it } / low.size + sd(high).let { it * it } / high.size)
        val dots = when { pairs.size < 10 -> 1; pairs.size < 20 || abs(raw) < 1.5 * se -> 2; else -> 3 }
        return LinkResult(id, sentence(med, shrunk), shrunk, pairs.size, dots)
    }

    fun links(days: List<DayIn>): List<LinkResult> {
        val by = days.associateBy { it.day }
        fun more(e: Double) = if (e > 0) "vyšší" else "nižší"
        fun pts(e: Double) = num(abs(e))
        val out = ArrayList<LinkResult>()
        link("sleep_energy", days.mapNotNull { d -> if (d.sleepH != null && d.energy != null) d.sleepH to d.energy.toDouble() else null }, 0.3) { m, e ->
            "Po spánku nad ${num(m)} h máš ráno energii o ${pts(e)} b ${more(e)}." }?.let { out += it }
        link("sleep_hunger", days.mapNotNull { d -> if (d.sleepH != null && d.hunger != null) d.sleepH to d.hunger.toDouble() else null }, 0.3) { m, e ->
            "Po spánku nad ${num(m)} h máš ráno hlad o ${pts(e)} b ${more(e)}." }?.let { out += it }
        link("training_sleep", days.mapNotNull { d -> by[d.day - 1]?.takeIf { it.volume > 0 }?.let { p -> d.sleepH?.let { p.volume to it } } }, 0.2) { m, e ->
            "Po tréninku s objemem nad ${(m / 100).roundToInt() * 100} kg spíš o ${(abs(e) * 60).roundToInt()} min ${if (e > 0) "déle" else "méně"}." }?.let { out += it }
        link("stress_sleep", days.mapNotNull { d -> by[d.day - 1]?.stress?.takeIf { it >= 0 }?.let { st -> d.sleepH?.let { st.toDouble() to it } } }, 0.2) { m, e ->
            "Po dni se stresem nad ${m.roundToInt()} spíš o ${(abs(e) * 60).roundToInt()} min ${if (e > 0) "déle" else "méně"}." }?.let { out += it }
        link("water_energy", days.mapNotNull { d -> by[d.day - 1]?.waterMl?.takeIf { it > 0 }?.let { w -> d.energy?.let { w / 1000.0 to it.toDouble() } } }, 0.3) { m, e ->
            "Po dni s vodou nad ${num(m)} l máš ráno energii o ${pts(e)} b ${more(e)}." }?.let { out += it }
        return out
    }
}
