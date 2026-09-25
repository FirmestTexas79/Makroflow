package cz.uhk.macroflow.training.log

import cz.uhk.macroflow.training.exercises.Exercise
import kotlin.math.min

/**
 * Co ukázat u cviku v tréninku a ve výpisu pro trenéra (čistý Kotlin, pokryto testy) – docs/adr/0024.
 * Spojuje model síly ([StrengthModel]) s dvojitou progresí ([Progression]):
 *  - cílová opakování z progrese (přidat opakování / držet / po přidání váhy spodní hranice),
 *  - váha z modelu síly pro tato opakování se 2 v rezervě,
 *  - pojistka: nikdy víc než poslední pracovní váha + 2 přírůstky (model po pauze nebo z jediné
 *    lehké série může sílu nadhodnotit).
 */
data class ExerciseInsight(
    val exercise: Exercise,
    val estimate: StrengthModel.Estimate?,
    val readyWeightKg: Double?,
    val readyReps: Int,
    val suggestion: Progression.Suggestion?,
    val last: Progression.Session?,
    val nextInDays: Int,
    val nextEstimate: StrengthModel.Estimate?
) {
    companion object {
        fun of(e: Exercise, allSets: List<LoggedSet>, today: Int): ExerciseInsight {
            val sets = allSets.filter { it.exerciseId == e.id }
            val last = Progression.lastSession(sets, e.id, beforeDay = today)
            val suggestion = Progression.suggest(e, last)
            val range = Progression.repRange(e)
            val reps = suggestion?.reps ?: (range.min + range.max) / 2
            val inc = Progression.increment(e)
            val estimate = StrengthModel.estimate(sets.filter { it.day < today }, today)
            var ready = if (inc <= 0.0) null else estimate?.let { StrengthModel.readyLoad(it.e1rm, reps, inc) }
            val lastTop = last?.workingSets?.firstOrNull()?.weightKg
            if (ready != null && lastTop != null && lastTop > 0.0) ready = min(ready, lastTop + 2 * inc)
            val interval = StrengthModel.typicalInterval(sets)
            val next = StrengthModel.estimate(sets.filter { it.day <= today }, today + interval)
            return ExerciseInsight(e, estimate, ready?.takeIf { it > 0.0 }, reps, suggestion, last, interval, next)
        }
    }
}
