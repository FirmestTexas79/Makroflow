package cz.uhk.macroflow.history

import android.content.Context
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.energy.Goal
import cz.uhk.macroflow.energy.WeightProjection
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Android adaptér nad [WeightProjection]: posbírá vážení, zapsaný příjem posledního týdne a výdej
 * z modelu na následující týden. Volat mimo hlavní vlákno (MacroCalculator čte databázi synchronně).
 *
 * U dne v minulosti se používají jen data známá k tomu dni, takže projekce jde porovnat se skutečností.
 */
object ProjectionRepository {

    /** Kolik dní historie graf ukazuje před vybraným dnem. */
    const val HISTORY_DAYS = 7

    data class Result(
        val projection: WeightProjection.Projection,
        val pace: WeightProjection.Pace,
        val chart: WeightProjectionView.Data,
        /** Skutečná vážení po vybraném dni v horizontu projekce (jen u dnů v minulosti). */
        val actualAfter: Map<Int, Double>
    )

    fun load(context: Context, asOf: LocalDate, today: LocalDate = LocalDate.now()): Result? {
        val db = AppDatabase.getDatabase(context)
        val weights = db.checkInDao().getAllCheckInsSync()
            .associate { LocalDate.parse(it.date).toEpochDay().toInt() to it.weight }
        val asOfDay = asOf.toEpochDay().toInt()
        if (weights.keys.none { it <= asOfDay }) return null

        // Zapsaný příjem za posledních 7 dokončených dní (vybraný den ještě nemusí být dojedený)
        val logged = (1..7).mapNotNull { back ->
            val date = asOf.minusDays(back.toLong())
            val consumed = db.consumedSnackDao().getConsumedByDateSync(date.toString())
            if (consumed.isEmpty()) return@mapNotNull null
            val intake = consumed.sumOf { it.calories.toDouble() }
            val model = MacroCalculator.calculateForDate(context, date.toJavaDate(), applyAdaptive = false)
            val modelTdee = model.expenditure?.modelTotal ?: model.calories
            // Stejné pravidlo jako adaptivní výdej: den pod 50 % výdeje je nedopsaný
            intake.takeIf { it >= 0.5 * modelTdee }
        }

        // Následující týden: cíl kalorií (tak, jak ho aplikace uživateli ukazuje) a výdej z rovnic
        // BEZ adaptivní korekce – ta je odvozená z téhož trendu vážení (viz ADR 0020).
        val horizon = (0 until WeightProjection.HORIZON_DAYS).map { asOf.plusDays(it.toLong()).toJavaDate() }
        val targets = horizon.map { MacroCalculator.calculateForDate(context, it, applyAdaptive = true).calories }
        val expenditures = horizon.map { d ->
            MacroCalculator.calculateForDate(context, d, applyAdaptive = false).let { it.expenditure?.modelTotal ?: it.calories }
        }
        val energy = WeightProjection.energyInput(logged, targets.average(), expenditures.average())

        val projection = WeightProjection.project(weights, asOfDay, energy) ?: return null
        val goal = Goal.from(db.userProfileDao().getProfileSync()?.goal)
        val pace = WeightProjection.pace(goal, projection)

        val fromDay = asOfDay - HISTORY_DAYS
        val toDay = asOfDay + WeightProjection.HORIZON_DAYS
        val chart = WeightProjectionView.Data(
            fromDay = fromDay,
            toDay = toDay,
            asOfDay = asOfDay,
            todayDay = today.toEpochDay().toInt(),
            weighIns = weights.filterKeys { it in fromDay..toDay },
            trend = projection.trend.filter { it.day in fromDay..asOfDay }.associate { it.day to it.level },
            forecast = projection.forecast
        )
        return Result(projection, pace, chart, weights.filterKeys { it in asOfDay + 1..toDay })
    }

    private fun LocalDate.toJavaDate(): Date = Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant())
}
