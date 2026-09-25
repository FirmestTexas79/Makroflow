package cz.uhk.macroflow.training.log

import cz.uhk.macroflow.training.body.Muscle
import cz.uhk.macroflow.training.exercises.Equipment
import cz.uhk.macroflow.training.exercises.Exercise
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Jedna zapsaná série (bez Androidu). [day] = epochDay, [order] = pořadí v rámci dne,
 * [slowEccentric] = záměrně pomalé spouštění (~3 s a víc), [template] = šablona dne („PUSH_A“),
 * [rir] = opakování v rezervě do selhání (výchozí 2).
 */
data class LoggedSet(
    val id: Long = 0,
    val day: Int,
    val exerciseId: String,
    val weightKg: Double,
    val reps: Int,
    val order: Int = 0,
    val slowEccentric: Boolean = false,
    val template: String? = null,
    val rir: Int = StrengthModel.DEFAULT_RIR
)

/**
 * Tréninkový deník – progrese a objem (čistý Kotlin, pokryto testy). Podklady v docs/adr/0023.
 *
 *  - odhad 1RM Epleyho vzorcem (Epley 1985): w · (1 + r / 30), spolehlivý hlavně do ~10 opakování,
 *  - návrh na příště „dvojitou progresí“: v rozsahu opakování nejdřív přidávej opakování,
 *    po dosažení horní hranice ve všech pracovních sériích přidej váhu a začni na spodní hranici,
 *  - týdenní objem: tvrdé série na partii (hlavní sval 1, pomocný 0,5); pro růst se doporučuje
 *    zhruba 10–20 sérií na partii týdně (Schoenfeld, Ogborn & Krieger 2017).
 */
object Progression {

    const val WEEKLY_SETS_MIN = 10.0
    const val WEEKLY_SETS_MAX = 20.0

    data class RepRange(val min: Int, val max: Int)

    /** Epleyho odhad maxima na 1 opakování; 1 opakování = váha sama. */
    fun e1rm(weightKg: Double, reps: Int): Double = when {
        reps <= 0 || weightKg <= 0.0 -> 0.0
        reps == 1 -> weightKg
        else -> weightKg * (1.0 + reps / 30.0)
    }

    /** Rozsah opakování podle typu cviku: vícekloubové s osou níž, izolované na kladce/stroji výš. */
    fun repRange(e: Exercise): RepRange = when (e.equipment) {
        Equipment.BARBELL -> RepRange(6, 10)
        Equipment.DUMBBELL -> RepRange(8, 12)
        Equipment.BAR, Equipment.BODYWEIGHT -> RepRange(6, 15)
        Equipment.CABLE, Equipment.MACHINE, Equipment.OTHER -> RepRange(10, 15)
    }

    /** Nejmenší rozumný přírůstek váhy (kg); 0 = cvik s vlastní vahou, progrese přes opakování. */
    fun increment(e: Exercise): Double = when (e.equipment) {
        Equipment.BARBELL -> 2.5
        Equipment.DUMBBELL -> 2.0
        Equipment.CABLE, Equipment.MACHINE, Equipment.OTHER -> 2.5
        Equipment.BAR, Equipment.BODYWEIGHT -> 0.0
    }

    data class Session(val day: Int, val sets: List<LoggedSet>) {
        /** Pracovní série = série s nejvyšší váhou dne (rozcvičovací lehčí série se nepočítají). */
        val workingSets: List<LoggedSet>
            get() { val top = sets.maxOf { it.weightKg }; return sets.filter { it.weightKg == top } }
    }

    /** Poslední den s cvikem před [beforeDay] (dnešek se nepočítá – ten se právě cvičí). */
    fun lastSession(sets: List<LoggedSet>, exerciseId: String, beforeDay: Int): Session? {
        val mine = sets.filter { it.exerciseId == exerciseId && it.day < beforeDay }
        val day = mine.maxOfOrNull { it.day } ?: return null
        return Session(day, mine.filter { it.day == day }.sortedBy { it.order })
    }

    enum class Kind { ADD_WEIGHT, ADD_REPS, HOLD, HARDER_VARIANT }

    data class Suggestion(val weightKg: Double, val reps: Int, val kind: Kind, val reason: String)

    fun suggest(e: Exercise, last: Session?): Suggestion? {
        if (last == null || last.sets.isEmpty()) return null
        val range = repRange(e)
        val work = last.workingSets
        val weight = work.first().weightKg
        val minReps = work.minOf { it.reps }
        val maxReps = work.maxOf { it.reps }
        val inc = increment(e)
        return when {
            minReps >= range.max && inc > 0.0 -> Suggestion(
                roundTo(weight + inc, inc), range.min, Kind.ADD_WEIGHT,
                "Minule všechny pracovní série na ${range.max}+ opakování – přidej váhu."
            )
            minReps >= range.max -> Suggestion(
                weight, range.max, Kind.HARDER_VARIANT,
                "Zvládáš ${range.max}+ opakování – přidej zátěž (vesta, kotouč) nebo těžší variantu."
            )
            minReps < range.min -> Suggestion(
                weight, range.min, Kind.HOLD,
                "Drž váhu a dostaň všechny série aspoň na ${range.min} opakování."
            )
            else -> Suggestion(
                weight, (maxReps + 1).coerceAtMost(range.max), Kind.ADD_REPS,
                "Stejná váha, o opakování víc (cíl ${range.min}–${range.max})."
            )
        }
    }

    /** Nejlepší série podle odhadu 1RM. */
    fun best(sets: List<LoggedSet>): LoggedSet? = sets.maxByOrNull { e1rm(it.weightKg, it.reps) }

    /** Je [set] nový osobní rekord (odhad 1RM) proti [previous]? První zápis rekord není. */
    fun isPersonalRecord(set: LoggedSet, previous: List<LoggedSet>): Boolean {
        val before = best(previous.filter { it.exerciseId == set.exerciseId && it.id != set.id }) ?: return false
        return e1rm(set.weightKg, set.reps) > e1rm(before.weightKg, before.reps) + 1e-9
    }

    /** Tvrdé série na partii: hlavní sval 1, pomocný 0,5. */
    fun weeklySets(sets: List<LoggedSet>, exercises: (String) -> Exercise?): Map<Muscle, Double> {
        val out = Muscle.entries.associateWith { 0.0 }.toMutableMap()
        sets.forEach { s ->
            val e = exercises(s.exerciseId) ?: return@forEach
            e.primary.forEach { out[it] = out.getValue(it) + 1.0 }
            e.secondary.forEach { out[it] = out.getValue(it) + 0.5 }
        }
        return out
    }

    enum class Volume { NONE, LOW, OK, HIGH }

    fun volume(setsPerWeek: Double): Volume = when {
        setsPerWeek <= 0.0 -> Volume.NONE
        setsPerWeek < WEEKLY_SETS_MIN -> Volume.LOW
        setsPerWeek > WEEKLY_SETS_MAX -> Volume.HIGH
        else -> Volume.OK
    }

    /** Zaokrouhlení na násobek kroku (kotouče) – 61,3 → 62,5 při kroku 2,5. */
    fun roundTo(value: Double, step: Double): Double =
        if (step <= 0.0) value else (floor(value / step + 0.5) * step * 100).roundToInt() / 100.0
}
