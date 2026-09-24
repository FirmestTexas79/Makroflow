package cz.uhk.macroflow.history

import android.content.Context
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.energy.Adherence
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Denní hodnoty pro heatmapu. Volat z Dispatchers.IO (DAO jsou synchronní).
 * Energie i cíle jdou přes MacroCalculator – stejný model jako dashboard a achievementy.
 */
object HeatmapRepository {

    /** Hodnoty pro dny [from]..[today]; dny před prvním záznamem v aplikaci jsou bez dat (null). */
    fun load(ctx: Context, metric: HeatMetric, from: LocalDate, today: LocalDate): Map<LocalDate, Double?> {
        val db = AppDatabase.getDatabase(ctx)
        val steps = db.stepsDao().getAllStepsSync().associate { LocalDate.parse(it.date) to it.count }
        val consumed = db.consumedSnackDao().getAllConsumedSync().groupBy { LocalDate.parse(it.date) }
        val checkIns = db.checkInDao().getAllCheckInsSync().mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }

        val firstUse = (steps.keys + consumed.keys + checkIns).minOrNull() ?: return emptyMap()
        val start = maxOf(from, firstUse)
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        val profile = db.userProfileDao().getProfileSync()

        return days.associateWith { d ->
            when (metric) {
                HeatMetric.STEPS -> steps[d]?.toDouble()

                HeatMetric.ACTIVE_KCAL -> {
                    // Chůze jen z naměřených kroků (bez předpokladu 6000 pro prázdný den) + trénink z plánu
                    val recorded = steps[d]
                    val exercise = MacroCalculator.calculateForDate(ctx, d.toDate()).expenditure?.exercise ?: 0.0
                    if (recorded == null && exercise <= 0.0) null
                    else MacroCalculator.walkingKcal(profile, recorded ?: 0) + exercise
                }

                HeatMetric.GOALS -> {
                    // Jen uzavřené dny se zapsaným jídlem – dnešek se může ještě přehoupnout
                    val items = consumed[d]
                    if (items.isNullOrEmpty() || !d.isBefore(today)) null
                    else {
                        val t = MacroCalculator.calculateForDate(ctx, d.toDate())
                        val targets = Adherence.Targets(t.calories, t.protein, t.carbs, t.fat)
                        val eaten = Adherence.Eaten(
                            kcal = items.sumOf { it.calories.toDouble() },
                            protein = items.sumOf { it.p.toDouble() },
                            carbs = items.sumOf { it.s.toDouble() },
                            fat = items.sumOf { it.t.toDouble() }
                        )
                        Adherence.Nutrient.entries.count { Adherence.isHit(it, eaten, targets) }.toDouble()
                    }
                }
            }
        }
    }

    private fun LocalDate.toDate(): Date = Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant())
}
