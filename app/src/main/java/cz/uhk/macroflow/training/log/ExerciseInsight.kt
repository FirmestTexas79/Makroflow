package cz.uhk.macroflow.training.log

import cz.uhk.macroflow.training.exercises.Exercise
/**
 * Co ukázat u cviku v tréninku a ve výpisu pro trenéra (čistý Kotlin, pokryto testy) – docs/adr/0024.
 * Váha a opakování na příště z [ProgressiveOverload] (6–8, při 10 přidat), model síly ([StrengthModel])
 * jen ukazuje odhad 1RM a trend. Bez historie: střed rozsahu, váhu nenavrhuje.
 */
data class ExerciseInsight(
    val exercise: Exercise,
    val estimate: StrengthModel.Estimate?,
    val readyWeightKg: Double?,
    val readyReps: Int,
    val suggestion: ProgressiveOverload.Decision?,
    val last: Progression.Session?,
    val nextInDays: Int,
    val nextEstimate: StrengthModel.Estimate?
) {
    companion object {
        fun of(e: Exercise, allSets: List<LoggedSet>, today: Int): ExerciseInsight {
            val sets = allSets.filter { it.exerciseId == e.id }
            val last = Progression.lastSession(sets, e.id, beforeDay = today)
            val suggestion = ProgressiveOverload.decide(e, sets, today)
            val range = Progression.repRange(e)
            val reps = suggestion?.reps ?: (range.min + range.max) / 2
            val inc = Progression.increment(e)
            val estimate = StrengthModel.estimate(sets.filter { it.day < today }, today)
            val ready = if (inc <= 0.0) null else suggestion?.weightKg
            val interval = StrengthModel.typicalInterval(sets)
            val next = StrengthModel.estimate(sets.filter { it.day <= today }, today + interval)
            return ExerciseInsight(e, estimate, ready?.takeIf { it > 0.0 }, reps, suggestion, last, interval, next)
        }
    }
}
