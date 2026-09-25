package cz.uhk.macroflow.training.log

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Predikční model síly pro jeden cvik (čistý Kotlin, pokryto testy) – docs/adr/0024.
 *
 * 1. **Pozorování ze série**: odhad 1RM Epleyho vzorcem w·(1 + r/30), kde r jsou „ekvivalentní
 *    opakování do selhání“: zapsaná opakování · [TEMPO_FACTOR] u pomalého spouštění (se stejnou
 *    vahou jich člověk udělá méně) + zapsaná rezerva RIR (výchozí [DEFAULT_RIR]). Série obvykle
 *    nejdou do úplného selhání – bez rezervy model sílu systematicky podhodnocoval (ADR 0024).
 *    Nejistota: σ = 3 % + 0,2 % za opakování (Epley je přesný hlavně do ~10 opakování).
 * 2. **Trénink = jedno pozorování**: nejlepší série dne. Další série jsou ovlivněné únavou,
 *    proto se nepočítají jako nezávislá měření.
 * 3. **Kalmanův filtr v log-prostoru**: stav [ln 1RM, růst za den]. Log-prostor dělá model
 *    nezávislý na velikosti vah (bench 100 kg i upažování 12 kg) a z nejistoty dostaneme 95% interval.
 * 4. **Výstupy**: 1RM teď (i ve dni bez tréninku), týdenní růst, váha „na kterou jsi připraven“
 *    pro cílová opakování s rezervou [RIR] a předpověď na další trénink.
 */
object StrengthModel {

    /** O kolik víc opakování by série zvládla s normálním tempem (modelový předpoklad). */
    const val TEMPO_FACTOR = 1.15
    /** Výchozí rezerva série, když ji uživatel nezmění (docs/adr/0025). */
    const val DEFAULT_RIR = 2
    /** Rozsah, který jde zapsat. */
    const val MAX_RIR = 5
    /** Nad tímto počtem (ekvivalentních) opakování už odhad 1RM není použitelný. */
    const val MAX_REPS = 20
    /** Rezerva opakování (RIR) při doporučení váhy. */
    const val RIR = 2

    private const val OBS_BASE = 0.03
    private const val OBS_PER_REP = 0.002
    private const val LEVEL_SD = 0.004          // náhodné výkyvy síly za den (log)
    private const val SLOPE_SD = 0.0004         // jak rychle se může měnit tempo růstu
    private const val SLOPE_PRIOR_SD = 0.003    // ±2 % týdně na začátku

    /** Ekvivalentní opakování do selhání (tempo + rezerva). */
    fun effectiveReps(reps: Int, slow: Boolean, rir: Int = DEFAULT_RIR): Double =
        (if (slow) reps * TEMPO_FACTOR else reps.toDouble()) + rir.coerceIn(0, MAX_RIR)

    /** Odhad 1RM ze série, nebo null (vlastní váha, 0 opakování, příliš mnoho opakování). */
    fun setEstimate(s: LoggedSet): Double? {
        if (s.weightKg <= 0.0 || s.reps <= 0) return null
        val r = effectiveReps(s.reps, s.slowEccentric, s.rir)
        if (r > MAX_REPS) return null
        return s.weightKg * (1.0 + r / 30.0)
    }

    fun obsSd(effectiveReps: Double): Double = OBS_BASE + OBS_PER_REP * effectiveReps

    data class Observation(val day: Int, val e1rm: Double, val sd: Double)

    /** Jedno pozorování na trénink: nejlepší série dne. */
    fun observations(sets: List<LoggedSet>): List<Observation> =
        sets.groupBy { it.day }.mapNotNull { (day, daySets) ->
            daySets.mapNotNull { s -> setEstimate(s)?.let { it to effectiveReps(s.reps, s.slowEccentric, s.rir) } }
                .maxByOrNull { it.first }
                ?.let { (e, r) -> Observation(day, e, obsSd(r)) }
        }.sortedBy { it.day }

    data class State(val day: Int, val level: Double, val slope: Double, val pLL: Double, val pLS: Double, val pSS: Double)

    private fun predict(s: State, days: Int): State {
        var x = s
        repeat(days.coerceAtLeast(0)) {
            x = State(x.day + 1, x.level + x.slope, x.slope,
                x.pLL + 2 * x.pLS + x.pSS + LEVEL_SD * LEVEL_SD, x.pLS + x.pSS, x.pSS + SLOPE_SD * SLOPE_SD)
        }
        return x
    }

    private fun update(s: State, z: Double, sd: Double): State {
        val r = sd * sd
        val inn = z - s.level
        val sInn = s.pLL + r
        val kL = s.pLL / sInn; val kS = s.pLS / sInn
        return State(s.day, s.level + kL * inn, s.slope + kS * inn,
            (1 - kL) * s.pLL, (1 - kL) * s.pLS, s.pSS - kS * s.pLS)
    }

    /** Stav filtru po posledním tréninku do [upToDay] včetně; null bez použitelného zápisu. */
    fun fit(sets: List<LoggedSet>, upToDay: Int = Int.MAX_VALUE): State? {
        val obs = observations(sets.filter { it.day <= upToDay })
        if (obs.isEmpty()) return null
        val first = obs.first()
        var s = State(first.day, ln(first.e1rm), 0.0, first.sd * first.sd, 0.0, SLOPE_PRIOR_SD * SLOPE_PRIOR_SD)
        obs.drop(1).forEach { o -> s = update(predict(s, o.day - s.day), ln(o.e1rm), o.sd) }
        return s
    }

    data class Estimate(
        val day: Int,
        val e1rm: Double,
        val lower: Double,
        val upper: Double,
        /** Změna 1RM v % za týden podle trendu. */
        val weeklyChangePct: Double,
        val sessions: Int
    )

    /** Odhad 1RM ke dni [day] (dopředu i do budoucna) z tréninků do [day]. */
    fun estimate(sets: List<LoggedSet>, day: Int): Estimate? {
        val s = fit(sets, upToDay = day) ?: return null
        val p = predict(s, day - s.day)
        val sd = sqrt(p.pLL.coerceAtLeast(0.0))
        return Estimate(day, exp(p.level), exp(p.level - 1.96 * sd), exp(p.level + 1.96 * sd),
            (exp(p.slope * 7) - 1) * 100, observations(sets.filter { it.day <= day }).size)
    }

    /**
     * Váha, na kterou je člověk připraven pro [reps] opakování s rezervou [rir]: z Epleyho vzorce
     * pro reps + rir, zaokrouhleno DOLŮ na [step] (kotouče) – bezpečnější strana.
     */
    fun readyLoad(e1rm: Double, reps: Int, step: Double, rir: Int = RIR): Double {
        val raw = e1rm / (1.0 + (reps + rir) / 30.0)
        return if (step <= 0.0) raw else floor(raw / step + 1e-9) * step
    }

    /** Obvyklý odstup tréninků cviku (medián, 2–14 dní; bez historie týden). */
    fun typicalInterval(sets: List<LoggedSet>): Int {
        val days = sets.map { it.day }.distinct().sorted()
        if (days.size < 2) return 7
        val gaps = days.zipWithNext { a, b -> b - a }.sorted()
        return gaps[gaps.size / 2].coerceIn(2, 14)
    }
}
