package cz.uhk.macroflow.report

import cz.uhk.macroflow.energy.Adherence
import cz.uhk.macroflow.history.Heatmap
import cz.uhk.macroflow.training.log.LoggedSet
import cz.uhk.macroflow.training.log.StrengthModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Stav oblasti za týden. Bez dat se nic nehodnotí. */
enum class Status(val label: String) { GOOD("Dobré"), OK("V pořádku"), WARN("Pozor"), NONE("Bez dat") }

enum class Domain(val label: String, val icon: String) {
    FOOD("Jídlo", "🍽️"), TRAINING("Trénink", "💪"), SLEEP("Spánek", "🌙"), BODY("Tělo", "⚖️"), MOVE("Pohyb a voda", "👟")
}

/** Uzavřený den se zapsaným jídlem: snědeno a osobní cíl téhož dne. */
data class DayFood(
    val day: Int, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double,
    val targetKcal: Double, val targetProtein: Double, val targetCarbs: Double, val targetFat: Double
)

/** Noc z hodinek: [bedMin] = minuty usínání jako v SleepInsights.bedtimeMin. */
data class NightIn(val day: Int, val sec: Int, val score: Int? = null, val bedMin: Int? = null)

/**
 * Vstup jednoho týdne (pondělí až neděle). Dny po [today] se nepočítají a dnešek se do jídla, kroků a vody nebere,
 * dokud neskončí. Všechno, co chybí, je prázdné nebo null: nic se nedomýšlí.
 */
data class WeekIn(
    val monday: Int,                          // epoch day
    val today: Int,
    val goal: String = "MAINTAIN",            // CUT / MAINTAIN / BULK
    val food: List<DayFood> = emptyList(),
    val plannedSoFar: Int = 0,                // plánované tréninkové dny, které už uplynuly (nebo byly splněny)
    val doneDays: Int = 0,                    // dny s tréninkem (deník, rychlý zápis i hodinky)
    val weekSets: List<LoggedSet> = emptyList(),
    val earlierSets: List<LoggedSet> = emptyList(),   // všechno před pondělím
    val names: Map<String, String> = emptyMap(),
    val nights: List<NightIn> = emptyList(),
    val prevNights: List<NightIn> = emptyList(),
    val weighIns: Int = 0,
    val trendKg: Double? = null,              // vyhlazená hmotnost na konci týdne
    val trendChangeKg: Double? = null,        // změna trendu za 7 dní
    val steps: List<Int> = emptyList(),       // uzavřené dny s naměřenými kroky
    val stepGoal: Int = 6000,
    val waterDays: Int = 0, val waterLoggedDays: Int = 0, val closedDays: Int = 0,
    val checkInDays: Int = 0, val avgEnergy: Double? = null
) {
    val sunday get() = monday + 6
    val isFinal get() = today > sunday
}

data class Card(
    val domain: Domain, val status: Status, val big: String, val caption: String,
    val line: String, val details: List<String>
)

data class Record(val name: String, val e1rmKg: Double, val gainKg: Double)

data class Report(
    val isFinal: Boolean, val headline: String, val cards: List<Card>,
    val records: List<Record>, val actions: List<String>, val gaps: Boolean,
    /** Nejtěžší série týdne, např. „Bench 100 kg × 5“. */
    val topSet: String?
)

/**
 * Týdenní report: z dat týdne udělá stavy oblastí, hlavní větu a nejvýš tři kroky na příští týden.
 * Čistý Kotlin, pokryto testy (docs/adr/0100). Zásady: trefa cíle, ne extrém; chybějící data nejsou neúspěch;
 * nejdřív dobré zprávy, potom jedna priorita; žádné jedno číslo „skóre týdne“.
 */
object WeeklyReportModel {
    const val NEED_SLEEP_SEC = 8 * 3600
    const val MIN_FOOD_DAYS = 3
    const val MIN_NIGHTS = 4
    const val MIN_WEIGHINS = 3
    const val MIN_STEP_DAYS = 3
    const val MAX_ACTIONS = 3

    // ── Formátování ─────────────────────────────────────────────────────────
    private fun num(v: Double, digits: Int = 1) =
        String.format(Locale.US, "%.${digits}f", v).replace('.', ',').let { if (digits == 1) it.removeSuffix(",0") else it }
    private fun thousands(v: Double) = v.roundToInt().toString().reversed().chunked(3).joinToString(" ").reversed()
    private fun signed(v: Double, digits: Int = 1): String {
        val s = num(abs(v), digits)
        return if (s == "0" || s == "0,0" || s == "0,00") "±0" else (if (v > 0) "+" else "−") + s
    }
    private fun hm(min: Int) = "%d:%02d".format((min / 60) % 24, min % 60)
    private fun nights(n: Int) = "$n " + when { n == 1 -> "noc"; n in 2..4 -> "noci"; else -> "nocí" }

    // ── Sestavení ───────────────────────────────────────────────────────────
    fun build(w: WeekIn): Report {
        val cards = listOf(food(w), training(w), sleep(w), body(w), move(w))
        val gaps = cards.count { it.status == Status.NONE } >= 3
        return Report(
            isFinal = w.isFinal, headline = headline(cards, gaps), cards = cards, records = records(w),
            actions = if (gaps) gapActions(cards, w) else actions(w, cards), gaps = gaps, topSet = topSet(w)
        )
    }

    // ── Jídlo ───────────────────────────────────────────────────────────────
    private class FoodStats(
        val n: Int, val kcalDays: Int, val perfect: Int, val pct: Int, val avgKcal: Double, val avgTarget: Double,
        val avgProtein: Double, val avgProteinTarget: Double, val hit: Map<Adherence.Nutrient, Int>
    )

    private fun foodStats(w: WeekIn): FoodStats? {
        val d = w.food
        if (d.size < MIN_FOOD_DAYS) return null
        fun eaten(x: DayFood) = Adherence.Eaten(x.kcal, x.protein, x.carbs, x.fat)
        fun targets(x: DayFood) = Adherence.Targets(x.targetKcal, x.targetProtein, x.targetCarbs, x.targetFat)
        val hit = Adherence.Nutrient.entries.associateWith { nu -> d.count { Adherence.isHit(nu, eaten(it), targets(it)) } }
        val avgKcal = d.map { it.kcal }.average(); val avgTarget = d.map { it.targetKcal }.average()
        return FoodStats(
            n = d.size, kcalDays = hit.getValue(Adherence.Nutrient.KCAL),
            perfect = d.count { Adherence.isPerfectDay(eaten(it), targets(it)) },
            pct = if (avgTarget > 0) (avgKcal / avgTarget * 100).roundToInt() else 100,
            avgKcal = avgKcal, avgTarget = avgTarget,
            avgProtein = d.map { it.protein }.average(), avgProteinTarget = d.map { it.targetProtein }.average(), hit = hit
        )
    }

    private fun food(w: WeekIn): Card {
        val s = foodStats(w) ?: return Card(Domain.FOOD, Status.NONE, "–", "dní se zápisem",
            "Zapsáno jen ${Heatmap.days(w.food.size)}. Na hodnocení potřebuju aspoň $MIN_FOOD_DAYS.", emptyList())
        val status = when {
            s.pct < 85 || s.pct > 120 -> Status.WARN
            s.n >= 5 && s.kcalDays >= 4 -> Status.GOOD
            else -> Status.OK
        }
        val line = when {
            s.pct < 85 -> "Průměrně jsi snědl jen ${s.pct} % cíle. Jíst hluboko pod cílem není dobrý plán, přibliž se k němu."
            s.pct > 120 -> "Průměrně jsi snědl ${s.pct} % cíle, to je hodně nad plánem."
            else -> "Kalorie jsi trefil ${s.kcalDays} dní z ${s.n}."
        }
        val details = buildList {
            add("Zapsáno ${Heatmap.days(s.n)} (hodnotí se jen uzavřené dny).")
            add("Ø příjem ${thousands(s.avgKcal)} kcal, cíl ${thousands(s.avgTarget)} kcal (${s.pct} %).")
            add("Bílkoviny Ø ${s.avgProtein.roundToInt()} g, cíl ${s.avgProteinTarget.roundToInt()} g.")
            add("Všechny čtyři cíle splněny ${Heatmap.days(s.perfect)}.")
            s.hit.minByOrNull { it.value }?.takeIf { it.value * 2 < s.n }?.let {
                add("Nejhůř vychází ${it.key.label}: v pásmu jen ${Heatmap.days(it.value)} z ${s.n}.")
            }
        }
        return Card(Domain.FOOD, status, "${s.kcalDays} z ${s.n}", "dní s kaloriemi v cíli", line, details)
    }

    // ── Trénink ─────────────────────────────────────────────────────────────
    private fun volume(sets: List<LoggedSet>) = sets.filter { it.weightKg > 0 }.sumOf { it.weightKg * it.reps }

    private fun topSet(w: WeekIn): String? =
        w.weekSets.filter { it.weightKg > 0 && it.reps > 0 }.maxWithOrNull(compareBy<LoggedSet>({ it.weightKg }, { it.reps }))
            ?.let { "${w.names[it.exerciseId] ?: it.exerciseId} ${num(it.weightKg)} kg × ${it.reps}" }

    private fun training(w: WeekIn): Card {
        val p = w.plannedSoFar; val d = w.doneDays
        if (p == 0 && d == 0) return Card(Domain.TRAINING, Status.NONE, "–", "tréninků",
            "V plánu nemáš trénink a žádný nebyl zapsán.", emptyList())
        val vol = volume(w.weekSets)
        val prev = volume(w.earlierSets.filter { it.day in (w.monday - 7) until w.monday })
        val status = when {
            p == 0 -> Status.OK
            d >= p -> Status.GOOD
            d * 5 >= p * 3 -> Status.OK
            else -> Status.WARN
        }
        val line = when {
            p == 0 -> "Trénink navíc mimo plán: ${Heatmap.days(d)}."
            status == Status.GOOD -> "Splnil jsi celý plán."
            status == Status.OK -> "Splněno $d z $p. Chybí už jen málo k celému plánu."
            else -> "Splněno jen $d z $p tréninků. Zkus je naplánovat na konkrétní dny."
        }
        val details = buildList {
            add("Plán se bere z aktuálního týdenního rozvrhu.")
            if (vol > 0) add(
                "Objem ${thousands(vol)} kg" + if (prev > 0 && vol / prev in 0.1..4.0)
                    " (${signed((vol / prev - 1) * 100, 0)} % proti minulému týdnu)" else ""
            )
            topSet(w)?.let { add("Nejtěžší série: $it.") }
        }
        return Card(Domain.TRAINING, status, if (p == 0) "$d" else "$d z $p",
            if (p == 0) "tréninků navíc" else "tréninků podle plánu", line, details)
    }

    /** Rekord = nejlepší odhad 1RM z jedné série týdne je aspoň o 1 kg nad vším předtím (cvik s aspoň dvěma staršími tréninky). */
    private fun records(w: WeekIn): List<Record> {
        val before = w.earlierSets.groupBy { it.exerciseId }
        return w.weekSets.groupBy { it.exerciseId }.mapNotNull { (id, ws) ->
            val prior = before[id] ?: return@mapNotNull null
            if (prior.map { it.day }.distinct().size < 2) return@mapNotNull null
            val bestPrior = prior.mapNotNull { StrengthModel.setEstimate(it) }.maxOrNull() ?: return@mapNotNull null
            val best = ws.mapNotNull { StrengthModel.setEstimate(it) }.maxOrNull() ?: return@mapNotNull null
            (best - bestPrior).takeIf { it >= 1.0 }?.let { Record(w.names[id] ?: id, best, it) }
        }.sortedByDescending { it.gainKg }.take(3)
    }

    // ── Spánek ──────────────────────────────────────────────────────────────
    private class SleepStats(
        val n: Int, val avgSec: Double, val debtSec: Int, val score: Int?,
        val bedMean: Int?, val bedSd: Int?, val deltaMin: Int?
    )

    private fun sleepStats(w: WeekIn): SleepStats? {
        val n = w.nights
        if (n.size < MIN_NIGHTS) return null
        val avg = n.map { it.sec }.average()
        val bed = n.mapNotNull { it.bedMin }.takeIf { it.size >= MIN_NIGHTS }
        val mean = bed?.average()
        val sd = mean?.let { m -> sqrt(bed.sumOf { (it - m) * (it - m) } / bed.size) }
        val delta = w.prevNights.takeIf { it.size >= MIN_NIGHTS }?.let { ((avg - it.map { x -> x.sec }.average()) / 60).roundToInt() }
        return SleepStats(
            n.size, avg, n.sumOf { (NEED_SLEEP_SEC - it.sec).coerceAtLeast(0) },
            n.mapNotNull { it.score }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            mean?.roundToInt(), sd?.roundToInt(), delta
        )
    }

    private fun sleep(w: WeekIn): Card {
        val s = sleepStats(w) ?: return Card(Domain.SLEEP, Status.NONE, "–", "Ø spánek za noc",
            "Z hodinek mám jen ${nights(w.nights.size)}. Potřebuju aspoň $MIN_NIGHTS.", emptyList())
        val h = s.avgSec / 3600
        val debtH = s.debtSec / 3600.0
        val status = when {
            h < 6.0 || debtH >= 8 -> Status.WARN
            h >= 7.0 && debtH < 4 -> Status.GOOD
            else -> Status.OK
        }
        val line = when (status) {
            Status.WARN -> "Spal jsi málo: chybí ti ${num(debtH)} h oproti 8 h."
            Status.GOOD -> if ((s.bedSd ?: 0) >= 60) "Spal jsi dost, jen usínáš nepravidelně." else "Spal jsi dost a pravidelně."
            else -> "Spánek na hraně: Ø ${num(h)} h, doporučeno kolem 8."
        }
        val details = buildList {
            add("Noci s daty: ${s.n}. Chybějící spánek proti 8 h: ${num(debtH)} h.")
            s.score?.let { add("Ø skóre spánku $it.") }
            s.deltaMin?.takeIf { abs(it) >= 15 }?.let { add("O ${abs(it)} min ${if (it > 0) "víc" else "méně"} než minulý týden.") }
            if (s.bedMean != null && s.bedSd != null) add("Usínáš kolem ${hm(s.bedMean)} (±${s.bedSd} min).")
        }
        return Card(Domain.SLEEP, status, "${num(h)} h", "Ø spánek za noc", line, details)
    }

    // ── Tělo ────────────────────────────────────────────────────────────────
    /** Změna trendové váhy v % za týden, nebo null při málo vážení. */
    private fun bodyPct(w: WeekIn): Double? {
        val ch = w.trendChangeKg; val t = w.trendKg
        return if (w.weighIns >= MIN_WEIGHINS && ch != null && t != null && t > 0) ch / t * 100 else null
    }

    private fun body(w: WeekIn): Card {
        val pct = bodyPct(w)
        val ch = w.trendChangeKg
        if (pct == null || ch == null) return Card(Domain.BODY, Status.NONE, "–", "trend váhy za týden",
            "Zvážil ses jen ${w.weighIns}×. Na tempo potřebuju aspoň $MIN_WEIGHINS vážení za týden.", emptyList())
        val status = when (w.goal) {
            "CUT" -> when { pct < -Adherence.MAX_SAFE_RATE_PCT_PER_WEEK -> Status.WARN; pct <= -0.25 -> Status.GOOD; else -> Status.OK }
            "BULK" -> when { pct > 0.5 -> Status.WARN; pct >= 0.1 -> Status.GOOD; else -> Status.OK }
            else -> when { abs(ch) > 1.0 -> Status.WARN; abs(ch) < 0.3 -> Status.GOOD; else -> Status.OK }
        }
        val rate = num(abs(pct), 1)
        val line = when (w.goal) {
            "CUT" -> when (status) {
                Status.WARN -> "Hubneš rychle ($rate % váhy týdně). Nad 1 % hrozí ztráta svalů."
                Status.GOOD -> "Hubneš zdravým tempem."
                else -> "Váha zatím stojí nebo roste, ale jeden týden o trendu nerozhoduje."
            }
            "BULK" -> when (status) {
                Status.WARN -> "Přibíráš rychle ($rate % týdně). Nad 0,5 % je to už spíš tuk než svaly."
                Status.GOOD -> "Přibíráš pomalu, tempem vhodným pro nabírání."
                else -> "Váha roste pomalu nebo stojí."
            }
            else -> when (status) {
                Status.WARN -> "Váha se za týden změnila o ${num(abs(ch))} kg."
                Status.GOOD -> "Váha drží."
                else -> "Váha se mírně pohnula."
            }
        }
        val details = listOf(
            "Trendová váha ${num(w.trendKg ?: 0.0)} kg (vyhlazená, ne jedno vážení).",
            "Tempo ${signed(pct, 2)} % váhy týdně. Počet vážení: ${w.weighIns}×."
        )
        return Card(Domain.BODY, status, signed(ch) + " kg", "trend váhy za týden", line, details)
    }

    // ── Pohyb a voda ────────────────────────────────────────────────────────
    private fun move(w: WeekIn): Card {
        val avg = w.steps.takeIf { it.size >= MIN_STEP_DAYS }?.average()
        val waterUsed = w.waterLoggedDays > 0
        if (avg == null && !waterUsed) return Card(Domain.MOVE, Status.NONE, "–", "kroků denně v průměru",
            "Kroky ani vodu jsem tenhle týden nezachytil.", emptyList())
        val waterOk = w.waterDays >= 5
        val status = when {
            avg == null -> if (waterOk) Status.GOOD else Status.OK
            avg < 0.7 * w.stepGoal -> Status.WARN
            avg >= w.stepGoal && (waterOk || !waterUsed) -> Status.GOOD
            else -> Status.OK
        }
        val line = when {
            avg == null -> "Kroky mám jen z pár dní. Voda: cíl splněn ${Heatmap.days(w.waterDays)} z ${w.closedDays}."
            status == Status.GOOD -> if (waterUsed) "Kroky i voda v cíli." else "Kroky v cíli."
            status == Status.WARN -> "Chodil jsi výrazně míň, než máš v cíli."
            else -> "Ø ${thousands(avg)} kroků, cíl ${thousands(w.stepGoal.toDouble())}."
        }
        val details = buildList {
            if (avg != null) add("Dny s krokovým cílem: ${w.steps.count { it >= w.stepGoal }} z ${w.steps.size}.")
            if (waterUsed) add("Voda v cíli: ${Heatmap.days(w.waterDays)} z ${w.closedDays}.")
            add("Ranní rituál: ${w.checkInDays} z 7 dní" + (w.avgEnergy?.let { ", Ø energie ${num(it)}/5" } ?: "") + ".")
        }
        return Card(Domain.MOVE, status, avg?.let { thousands(it) } ?: "${w.waterDays} z ${w.closedDays}",
            if (avg != null) "kroků denně v průměru" else "dní s vodou v cíli", line, details)
    }

    // ── Hlavní věta ─────────────────────────────────────────────────────────
    private fun headline(cards: List<Card>, gaps: Boolean): String {
        if (gaps) return "Týden s mezerami: pro hodnocení mám málo zápisů."
        fun st(d: Domain) = cards.first { it.domain == d }.status
        val win = mapOf(Domain.TRAINING to "trénink podle plánu", Domain.FOOD to "jídlo v cíli", Domain.SLEEP to "spánek",
            Domain.BODY to "tempo změny váhy", Domain.MOVE to "pohyb")
        val focus = mapOf(Domain.BODY to "tempo změny váhy", Domain.SLEEP to "spánek", Domain.FOOD to "příjem jídla",
            Domain.TRAINING to "málo tréninků", Domain.MOVE to "kroky")
        val wins = listOf(Domain.TRAINING, Domain.FOOD, Domain.SLEEP, Domain.BODY, Domain.MOVE).filter { st(it) == Status.GOOD }.take(2).map { win.getValue(it) }
        val pick = listOf(Domain.BODY, Domain.SLEEP, Domain.FOOD, Domain.TRAINING, Domain.MOVE).firstOrNull { st(it) == Status.WARN }
        return listOfNotNull(
            wins.takeIf { it.isNotEmpty() }?.let { "Povedlo se: ${it.joinToString(" a ")}." },
            pick?.let { "Pozor na ${focus.getValue(it)}." }
        ).joinToString(" ").ifEmpty { "Týden v pořádku, nic nehoří." }
    }

    // ── Kroky na příští týden ───────────────────────────────────────────────
    private fun gapActions(cards: List<Card>, w: WeekIn): List<String> {
        fun none(d: Domain) = cards.first { it.domain == d }.status == Status.NONE
        return listOfNotNull(
            "Zapisuj jídla aspoň 5 dní v týdnu, ať má report z čeho vycházet.".takeIf { none(Domain.FOOD) },
            "Zvaž se aspoň třikrát za týden, ráno po probuzení.".takeIf { none(Domain.BODY) },
            "Nasaď hodinky i na noc, nebo vyplň kvalitu spánku v ranním rituálu.".takeIf { none(Domain.SLEEP) },
            "Vyplň ranní rituál aspoň 5 dní.".takeIf { w.checkInDays < 4 }
        ).take(MAX_ACTIONS)
    }

    /** Pořadí podle důležitosti: zdraví (váha), spánek, jídlo, trénink, kroky, voda, rituál. Nejvýš jeden krok na oblast. */
    private fun actions(w: WeekIn, cards: List<Card>): List<String> {
        fun c(d: Domain) = cards.first { it.domain == d }
        val out = ArrayList<String>()
        val pct = bodyPct(w)
        if (c(Domain.BODY).status == Status.WARN && pct != null) {
            if (w.goal == "CUT") out.add("Přidej zhruba 150 kcal denně: váha ti klesá rychleji než 1 % týdně.")
            else if (w.goal == "BULK") out.add("Uber zhruba 150 kcal denně: přibíráš rychleji, než je vhodné.")
        }
        val sl = sleepStats(w)
        if (sl != null) {
            if (c(Domain.SLEEP).status == Status.WARN || sl.debtSec >= 4 * 3600) {
                val bed = sl.bedMean
                out.add(
                    if (bed != null) "Dnes buď v posteli do ${hm(bed - 30)}, ať začneš splácet spánkový dluh."
                    else "Dnes jdi spát o půl hodiny dřív, ať začneš splácet spánkový dluh."
                )
            } else if ((sl.bedSd ?: 0) >= 60) {
                out.add("Choď spát vždy kolem stejné hodiny, teď kolísáš o ±${sl.bedSd} min.")
            }
        }
        val fs = foodStats(w)
        if (fs != null) {
            val gap = (fs.avgProteinTarget - fs.avgProtein).roundToInt()
            if (fs.pct < 85) out.add("Přidej jídlo a přibliž se cíli, zatím jsi na ${fs.pct} %.")
            else if (fs.pct > 120) out.add("Zkus jíst blíž k cíli, tenhle týden jsi byl na ${fs.pct} %.")
            else if (fs.avgProtein < 0.9 * fs.avgProteinTarget) out.add("Bílkoviny: chybí ti v průměru $gap g denně, přidej jednu porci k večeři.")
        }
        if (c(Domain.TRAINING).status == Status.WARN)
            out.add("Naplánuj si tréninky na konkrétní dny: stihl jsi ${w.doneDays} z ${w.plannedSoFar}.")
        val avg = w.steps.takeIf { it.size >= MIN_STEP_DAYS }?.average()
        if (c(Domain.MOVE).status == Status.WARN && avg != null)
            out.add("Cíl je ${thousands(w.stepGoal.toDouble())} kroků, máš v průměru ${thousands(avg)}. Zkus každý večer krátkou procházku.")
        if (w.waterLoggedDays > 0 && w.closedDays >= 4 && w.waterDays < 4)
            out.add("Voda: cíl jsi splnil jen ${Heatmap.days(w.waterDays)}. Dej si sklenici ke každému jídlu.")
        if (w.checkInDays < 4 && w.closedDays >= 4) out.add("Vyplň ranní rituál aspoň 5 dní, doporučení budou přesnější.")
        return out.take(MAX_ACTIONS).ifEmpty { listOf("Drž stávající rytmus, nic zásadního nemusíš měnit.") }
    }

    // ── Sdílení (bez jména, e-mailu a názvů jídel) ──────────────────────────
    fun exportText(r: Report, label: String): String = buildString {
        appendLine("Makroflow: týdenní report $label" + if (r.isFinal) "" else " (předběžný)")
        appendLine(r.headline)
        appendLine()
        r.cards.forEach { appendLine("${it.domain.label}: ${it.status.label}, ${it.big} ${it.caption}"); appendLine("  ${it.line}") }
        if (r.records.isNotEmpty()) {
            appendLine(); appendLine("Rekordy:")
            r.records.forEach { appendLine("  ${it.name}: 1RM ≈ ${num(it.e1rmKg)} kg (${signed(it.gainKg)} kg)") }
        }
        appendLine(); appendLine("Příští týden:")
        r.actions.forEach { appendLine("  - $it") }
    }
}
