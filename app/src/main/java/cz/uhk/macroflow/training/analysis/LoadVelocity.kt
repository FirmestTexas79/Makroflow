package cz.uhk.macroflow.training.analysis

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Trénink podle rychlosti (VBT) – čistý Kotlin, pokryto testy. Podklady v docs/adr/0006.
 *
 *  - Profil zátěž–rychlost: přímka v = a + b·zátěž z nejrychlejšího repu každé série daného dne.
 *  - Denní odhad maxima (e1RM): zátěž, při které by přímka klesla na minimální rychlost cviku [Lift.mvt].
 *  - Autoregulace: doporučená zátěž na další sérii podle cíle a živé „STOP“ při ztrátě rychlosti.
 */

/** Cíl tréninku: rozsah zátěže (podíl dnešního maxima) a kdy sérii ukončit podle ztráty rychlosti. */
enum class VbtGoal(
    val label: String,
    val pctLow: Double,
    val pctHigh: Double,
    /** Ztráta rychlosti v sérii (%), při které se série ukončí; null = bez limitu. */
    val stopLossPct: Double?
) {
    // Pareja-Blanco et al. 2017: ztráta 20 % dala stejnou sílu jako 40 % s menší únavou
    STRENGTH("Síla", 0.80, 0.85, 20.0),
    // Víc objemu na sérii; 40 % už je blízko selhání, 30 % je kompromis s únavou
    HYPERTROPHY("Objem", 0.67, 0.75, 30.0),
    // Výbušnost: lehčí zátěž, sérii ukončit, jakmile rychlost začne padat
    POWER("Výbušnost", 0.50, 0.60, 10.0),
    FREE("Bez limitu", 0.0, 0.0, null);

    val hasTarget: Boolean get() = pctHigh > 0

    companion object {
        fun from(name: String?): VbtGoal = entries.firstOrNull { it.name == name } ?: FREE
    }
}

/** Jeden bod profilu: zátěž a průměrná rychlost zvedání nejrychlejšího repu série. */
data class LvPoint(val loadKg: Double, val mcv: Double, val quality: Double = 1.0)

enum class Confidence(val label: String) {
    HIGH("vysoká"), MEDIUM("střední"), LOW("nízká")
}

data class LoadVelocityProfile(
    val lift: Lift,
    val intercept: Double,       // m/s při nulové zátěži (jen matematická veličina)
    val slope: Double,           // m/s na kg (záporná)
    val r2: Double,
    val points: Int,
    val distinctLoads: Int,
    val topLoadKg: Double,
    val e1rmKg: Double,
    val confidence: Confidence,
    /** Sklon převzatý z dřívějších dnů – dnes byla jen jedna zátěž. */
    val fromHistory: Boolean
) {
    fun velocityAt(loadKg: Double): Double = intercept + slope * loadKg
    fun loadFor(velocity: Double): Double = (velocity - intercept) / slope
}

object LoadVelocity {

    /** Série se sledováním pod touto kvalitou do profilu nevstupují (rychlost je nespolehlivá). */
    const val MIN_QUALITY = 0.6
    /** Minimální rozdíl rychlostí mezi nejlehčí a nejtěžší zátěží – menší zanikne v šumu měření. */
    private const val MIN_VELOCITY_SPREAD = 0.10

    /**
     * Profil pro dnešek. Když dnes byla jen jedna zátěž (nebo moc blízké), použije se
     * medián sklonu z dřívějších dnů a přímka se posune přes dnešní body – denní forma
     * mění hlavně výšku přímky, sklon je u člověka poměrně stálý.
     */
    fun fit(lift: Lift, today: List<LvPoint>, historicalSlopes: List<Double> = emptyList()): LoadVelocityProfile? {
        val pts = today.filter { it.loadKg > 0 && it.mcv > 0 && it.quality >= MIN_QUALITY }
        if (pts.isEmpty()) return null
        strictFit(lift, pts)?.let { return it }

        val slope = median(historicalSlopes.filter { it < 0 }) ?: return null
        val intercept = pts.map { it.mcv - slope * it.loadKg }.average()
        val top = pts.maxOf { it.loadKg }
        val e1rm = ((lift.mvt - intercept) / slope).coerceAtLeast(top)
        // Čím dál od maxima dnešní nejtěžší série, tím víc se extrapoluje
        val conf = if (top / e1rm >= 0.75) Confidence.MEDIUM else Confidence.LOW
        return LoadVelocityProfile(lift, intercept, slope, r2 = Double.NaN, points = pts.size,
            distinctLoads = pts.map { it.loadKg }.distinct().size, topLoadKg = top,
            e1rmKg = e1rm, confidence = conf, fromHistory = true)
    }

    /** Profil jen z vlastních bodů dne, bez historie; null, pokud na přímku nestačí. */
    fun strictFit(lift: Lift, points: List<LvPoint>): LoadVelocityProfile? {
        val pts = points.filter { it.loadKg > 0 && it.mcv > 0 && it.quality >= MIN_QUALITY }
        val loads = pts.map { it.loadKg }
        val distinct = loads.distinct().size
        if (distinct < 2) return null
        val lo = loads.min(); val top = loads.max()
        // Zátěže musí být od sebe aspoň 20 % (jinak je sklon jen šum)
        if (top - lo < 0.2 * top) return null
        val vFast = pts.filter { it.loadKg == lo }.maxOf { it.mcv }
        val vSlow = pts.filter { it.loadKg == top }.minOf { it.mcv }
        if (vFast - vSlow < MIN_VELOCITY_SPREAD) return null

        val mx = loads.average(); val my = pts.map { it.mcv }.average()
        val sxx = pts.sumOf { (it.loadKg - mx) * (it.loadKg - mx) }
        val sxy = pts.sumOf { (it.loadKg - mx) * (it.mcv - my) }
        val slope = sxy / sxx
        if (slope >= 0) return null
        val intercept = my - slope * mx
        val ssTot = pts.sumOf { (it.mcv - my) * (it.mcv - my) }
        val ssRes = pts.sumOf { val e = it.mcv - (intercept + slope * it.loadKg); e * e }
        val r2 = if (ssTot > 0) 1 - ssRes / ssTot else 0.0
        if (r2 < 0.6) return null

        val e1rm = ((lift.mvt - intercept) / slope).coerceAtLeast(top)
        val heavyEnough = top / e1rm >= 0.70
        val conf = when {
            distinct >= 3 && r2 >= 0.90 && heavyEnough -> Confidence.HIGH
            r2 >= 0.80 && (heavyEnough || distinct >= 3) -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        return LoadVelocityProfile(lift, intercept, slope, r2, pts.size, distinct, top, e1rm, conf, fromHistory = false)
    }

    /** Medián sklonů z dřívějších dnů (každý den zvlášť, jen spolehlivé profily). */
    fun historicalSlopes(lift: Lift, days: List<List<LvPoint>>): List<Double> =
        days.mapNotNull { strictFit(lift, it) }
            .filter { it.confidence != Confidence.LOW }
            .map { it.slope }

    fun roundToPlates(kg: Double, stepKg: Double = 2.5): Double = round(kg / stepKg) * stepKg

    internal fun median(v: List<Double>): Double? {
        if (v.isEmpty()) return null
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}

/** Ztráta rychlosti v sérii – pro živé „STOP“ i souhrn. */
object VelocityLoss {

    /**
     * Ztráta rychlosti posledního dokončeného repu proti nejrychlejšímu (%), nebo null (< 2 repy).
     *
     * @param live během série může být poslední rep teprve rozjetý: analyzátor ho uzavře
     *        podle zatím nejvyššího bodu. Takový rep má výrazně menší rozsah než ostatní,
     *        proto se při [live] rep s rozsahem pod 90 % mediánu nepočítá.
     */
    fun lossPct(set: SetSummary, live: Boolean): Double? {
        var reps = set.reps
        if (live && reps.size >= 2) {
            val medRom = LoadVelocity.median(reps.dropLast(1).map { it.romCm }) ?: 0.0
            if (reps.last().romCm < 0.9 * medRom) reps = reps.dropLast(1)
        }
        if (reps.size < 2) return null
        val best = reps.maxOf { it.meanConcentricVelocity }
        if (best <= 0) return null
        return ((best - reps.last().meanConcentricVelocity) / best * 100.0).coerceAtLeast(0.0)
    }

    fun shouldStop(set: SetSummary, goal: VbtGoal, live: Boolean = true): Boolean {
        val limit = goal.stopLossPct ?: return false
        return (lossPct(set, live) ?: return false) >= limit
    }
}

/** Doporučení po sérii (text pro uživatele + čísla pro test a UI). */
data class VbtAdvice(
    val lines: List<String>,
    val suggestedLoadKg: Double?,
    val targetFirstRepMcv: Double?
)

object Autoregulation {

    private val CZ = Locale("cs", "CZ")

    fun advise(goal: VbtGoal, profile: LoadVelocityProfile?, set: SetSummary, loadKg: Double?): VbtAdvice {
        val lines = mutableListOf<String>()
        var suggested: Double? = null
        var target: Double? = null

        if (profile != null) {
            val src = if (profile.fromHistory) "sklon z dřívějších tréninků" else
                "${profile.distinctLoads} zátěže" + (if (profile.r2.isNaN()) "" else String.format(CZ, ", R² %.2f", profile.r2))
            lines += String.format(CZ, "Odhad dnešního maxima: %.1f kg (spolehlivost %s; %s)",
                profile.e1rmKg, profile.confidence.label, src)
        } else if (loadKg == null) {
            lines += "Zadej zátěž – bez ní nejde spočítat profil zátěž–rychlost ani odhad maxima."
        } else {
            lines += "Pro odhad maxima přidej sérii s jinou zátěží (aspoň o 20 % odlišnou), třeba při rozcvičení."
        }

        if (goal.hasTarget && profile != null) {
            val pct = (goal.pctLow + goal.pctHigh) / 2
            val kg = LoadVelocity.roundToPlates(pct * profile.e1rmKg)
            val v = profile.velocityAt(kg)
            suggested = kg; target = v
            val inZone = loadKg != null && loadKg >= goal.pctLow * profile.e1rmKg - 1.25 && loadKg <= goal.pctHigh * profile.e1rmKg + 1.25
            lines += if (inZone)
                String.format(CZ, "%s: zátěž je v pásmu %.0f–%.0f %% maxima, zůstaň na ní (1. rep kolem %.2f m/s).",
                    goal.label, goal.pctLow * 100, goal.pctHigh * 100, set.reps.first().meanConcentricVelocity)
            else
                String.format(CZ, "%s: další série ~%.1f kg (%.0f %% maxima), 1. rep by měl jít kolem %.2f m/s.",
                    goal.label, kg, pct * 100, v)
        }

        val loss = set.velocityLossPct
        val limit = goal.stopLossPct
        if (limit != null && set.repCount >= 2) {
            lines += when {
                loss >= limit + 10 -> String.format(CZ, "Ztráta rychlosti %.0f %% – o hodně přes limit %.0f %%. Delší pauza nebo o opakování méně.", loss, limit)
                loss >= limit -> String.format(CZ, "Ztráta rychlosti %.0f %% – limit %.0f %% splněn, série ukončena správně.", loss, limit)
                else -> String.format(CZ, "Ztráta rychlosti %.0f %% (limit %.0f %%) – v rezervě, klidně o rep víc.", loss, limit)
            }
        }
        // Rychlost 1. repu pod cílem o víc než 0,05 m/s = den slabší, než předpovídá profil
        if (target != null && loadKg != null && abs(loadKg - suggested!!) < 1.3 &&
            set.reps.first().meanConcentricVelocity < target - 0.05) {
            lines += "První rep byl pomalejší než cíl – dnes spíš uber, únava je vyšší."
        }
        return VbtAdvice(lines, suggested, target)
    }
}
