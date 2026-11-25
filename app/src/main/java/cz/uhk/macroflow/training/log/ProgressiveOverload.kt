package cz.uhk.macroflow.training.log

import cz.uhk.macroflow.training.exercises.Exercise
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Druh série: pracovní, rozehřívací (před pracovními, lehčí) a dojížděcí (po nich, lehčí). */
enum class SetKind(val label: String, val short: String) {
    WORK("Pracovní", ""), WARMUP("Rozehřívací", "rozehř."), BACKOFF("Dojížděcí", "dojezd")
}

/**
 * Progresivní přetížení (docs/adr/0105, čistý Kotlin, pokryto testy) – co zvednout příště.
 *
 * Kulturistické pravidlo: pracovní série v rozsahu [Params.repsMin]–[Params.repsMax] (6–8), 2–3 série.
 * Když se se stejnou vahou dostaneš na [Params.addAt] (10) opakování, přidá se váha. O kolik: Epleyho
 * vzorcem tak, aby nová váha vycházela zhruba na [Params.targetAfterAdd] opakování (střed rozsahu),
 * aspoň o jeden kotouč a nejvýš o [Params.maxJumpPct]. Když opakovaně nedáš ani [Params.repsMin],
 * váha se odlehčí o [Params.deloadPct].
 *
 * „Skóre“ tréninku = kolik opakování pracovní série zvládly: mix nejslabší a nejsilnější série
 * ([Params.minWeight]) plus rezerva RIR nad výchozí 2 ([Params.rirWeight] opakování za 1 RIR).
 * Rozehřívací a dojížděcí série se do rozhodování nepočítají.
 *
 * Všechny váhy rozhodování jsou v [Params] – dají se ladit bez sahání do logiky.
 */
object ProgressiveOverload {

    data class Params(
        val repsMin: Int = 6,
        val repsMax: Int = 8,
        /** Na tolik opakování (skóre) se stejnou vahou → přidat váhu. */
        val addAt: Int = 10,
        val setsMin: Int = 2,
        val setsMax: Int = 3,
        /** Váha nejslabší pracovní série ve skóre (zbytek = nejsilnější). 1 = musí všechny série. */
        val minWeight: Double = 0.5,
        /** Kolik opakování „navíc“ znamená 1 RIR nad výchozí rezervu. */
        val rirWeight: Double = 0.5,
        /** Po přidání váhy mířit na tolik opakování (Epley). */
        val targetAfterAdd: Double = 7.0,
        /** Největší skok váhy najednou (podíl). */
        val maxJumpPct: Double = 0.10,
        /** Kolik tréninků po sobě pod [repsMin] se stejnou vahou → odlehčit. */
        val stallSessions: Int = 2,
        val deloadPct: Double = 0.10,
        /** Rozehřívací série: podíl pracovní váhy a opakování. */
        val warmupPct: Double = 0.6,
        val warmupReps: Int = 8,
        /** Dojížděcí série: podíl pracovní váhy a opakování. */
        val backoffPct: Double = 0.8,
        val backoffReps: Int = 10
    )

    val DEFAULT = Params()

    enum class Kind { ADD_WEIGHT, PUSH_REPS, HOLD, DELOAD, HARDER_VARIANT }

    data class Decision(
        val weightKg: Double,
        /** Cílová opakování pracovní série. */
        val reps: Int,
        val kind: Kind,
        val reason: String,
        /** Skóre posledního tréninku (opakování pracovních sérií). */
        val score: Double
    )

    /** Pracovní série tréninku: druh WORK s nejvyšší vahou dne (staré zápisy bez druhu = WORK). */
    fun workingSets(day: List<LoggedSet>): List<LoggedSet> {
        val work = day.filter { it.kind == SetKind.WORK }.ifEmpty { return emptyList() }
        val top = work.maxOf { it.weightKg }
        return work.filter { it.weightKg == top }
    }

    fun score(work: List<LoggedSet>, p: Params = DEFAULT): Double {
        val eff = work.map { it.reps + p.rirWeight * (it.rir - StrengthModel.DEFAULT_RIR) }
        return p.minWeight * eff.min() + (1 - p.minWeight) * eff.max()
    }

    /** Tréninky cviku před [today] (dnešek se právě cvičí), od nejstaršího; jen dny s pracovní sérií. */
    fun sessions(sets: List<LoggedSet>, exerciseId: String, today: Int): List<List<LoggedSet>> =
        sets.filter { it.exerciseId == exerciseId && it.day < today }.groupBy { it.day }.toSortedMap().values
            .map { day -> workingSets(day) }.filter { it.isNotEmpty() }

    /** Návrh na příští trénink, nebo null bez historie. */
    fun decide(e: Exercise, sets: List<LoggedSet>, today: Int, p: Params = DEFAULT): Decision? {
        val history = sessions(sets, e.id, today)
        val work = history.lastOrNull() ?: return null
        val w = work.first().weightKg
        val s = score(work, p)
        val inc = Progression.increment(e)
        val got = s.fmt()

        if (inc <= 0.0) return when {                              // vlastní váha: progrese přes opakování
            s >= p.addAt -> Decision(w, p.addAt, Kind.HARDER_VARIANT, "Minule $got opakování – přidej zátěž (vesta, kotouč) nebo těžší variantu.", s)
            else -> Decision(w, min(ceil(s).toInt() + 1, p.addAt), Kind.PUSH_REPS, "Minule $got opakování – o jedno víc, při ${p.addAt} přidáme zátěž.", s)
        }

        return when {
            s >= p.addAt -> {
                val raw = w * (1 + s / 30.0) / (1 + p.targetAfterAdd / 30.0)
                val next = Progression.roundTo(max(w + inc, min(raw, w * (1 + p.maxJumpPct))), inc).coerceAtLeast(w + inc)
                Decision(next, p.repsMin, Kind.ADD_WEIGHT,
                    "Minule $got opakování s ${LogSetSheet.kg(w)} kg – progresivní přetížení: přidej na ${LogSetSheet.kg(next)} kg a jeď ${p.repsMin}–${p.repsMax}.", s)
            }
            s < p.repsMin -> {
                val stalls = history.takeLastWhile { it.first().weightKg == w && score(it, p) < p.repsMin }.size
                if (stalls >= p.stallSessions) {
                    val lighter = floor(w * (1 - p.deloadPct) / inc + 1e-9) * inc
                    Decision(lighter, p.repsMax, Kind.DELOAD,
                        "${stalls}× po sobě pod ${p.repsMin} opakování – uber na ${LogSetSheet.kg(lighter)} kg a znovu nahoru.", s)
                } else Decision(w, p.repsMin, Kind.HOLD, "Drž ${LogSetSheet.kg(w)} kg a dostaň pracovní série aspoň na ${p.repsMin} opakování.", s)
            }
            else -> Decision(w, min(ceil(s).toInt() + 1, p.addAt), Kind.PUSH_REPS,
                "Stejná váha, o opakování víc – při ${p.addAt} opakováních přidáme.", s)
        }
    }

    /** Váha rozehřívací série k pracovní váze (dolů na kotouče). */
    fun warmupLoad(work: Double, step: Double, p: Params = DEFAULT) = down(work * p.warmupPct, step)

    /** Váha dojížděcí série k pracovní váze (dolů na kotouče). */
    fun backoffLoad(work: Double, step: Double, p: Params = DEFAULT) = down(work * p.backoffPct, step)

    private fun down(v: Double, step: Double) = if (step <= 0.0) v else floor(v / step + 1e-9) * step

    private fun Double.fmt() = if (this % 1.0 == 0.0) toInt().toString() else String.format(java.util.Locale.US, "%.1f", this).replace('.', ',')
}
