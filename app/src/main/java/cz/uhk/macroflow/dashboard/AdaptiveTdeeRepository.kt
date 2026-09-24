package cz.uhk.macroflow.dashboard

import android.content.Context
import android.util.Log
import cz.uhk.macroflow.data.AdaptiveTdeeEntity
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.energy.AdaptiveExpenditure
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Android adaptér nad [AdaptiveExpenditure]: posbírá posledních 28 dní
 * (vážení z check-inů, zapsaný příjem, výdej z modelu bez korekce),
 * spočítá odhad a uloží ho jako denní snapshot do `adaptive_tdee`.
 *
 * Volat mimo hlavní vlákno – po uložení check-inu (nové vážení = nová informace).
 */
object AdaptiveTdeeRepository {

    fun recompute(context: Context, today: LocalDate = LocalDate.now()): AdaptiveExpenditure.Estimate {
        val db = AppDatabase.getDatabase(context)
        val checkIns = db.checkInDao().getAllCheckInsSync().associateBy { it.date }

        val days = (AdaptiveExpenditure.WINDOW_DAYS - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            val key = date.toString() // ISO yyyy-MM-dd = formát klíčů v aplikaci
            val consumed = db.consumedSnackDao().getConsumedByDateSync(key)
            val asDate = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
            val model = MacroCalculator.calculateForDate(context, asDate, applyAdaptive = false)
            AdaptiveExpenditure.Day(
                day = date.toEpochDay().toInt(),
                weightKg = checkIns[key]?.weight,
                // Dnešek ještě není dojedený – jeho příjem do bilance nepatří
                intakeKcal = if (back == 0 || consumed.isEmpty()) null else consumed.sumOf { it.calories.toDouble() },
                modelTdeeKcal = model.expenditure?.modelTotal ?: model.calories
            )
        }

        val e = AdaptiveExpenditure.estimate(days)
        db.adaptiveTdeeDao().upsert(
            AdaptiveTdeeEntity(
                date = today.toString(),
                status = e.status.name,
                factor = e.factor,
                modelTdee = e.modelTdee,
                observedTdee = e.observedTdee,
                adaptiveTdee = e.adaptiveTdee,
                confidence = e.confidence,
                trendWeightKg = e.trendWeightKg,
                weightChangeKgPerWeek = e.weightChangeKgPerWeek,
                weighIns = e.weighIns,
                loggedDays = e.loggedDays
            )
        )
        Log.d("AdaptiveTDEE", "k=%.3f model=%.0f obs=%s adaptive=%.0f w=%.2f (%d vážení, %d dní)".format(
            e.factor, e.modelTdee, e.observedTdee?.toInt(), e.adaptiveTdee, e.confidence, e.weighIns, e.loggedDays))
        return e
    }

    /** Krátká zpráva pro uživatele, pokud se výdej znatelně upravil; jinak null. */
    fun userMessage(e: AdaptiveExpenditure.Estimate): String? {
        if (!AdaptiveExpenditure.isMeaningful(e)) return null
        val pct = ((e.factor - 1.0) * 100).toInt()
        val sign = if (pct > 0) "+" else ""
        return "Podle tvé váhy a jídelníčku upravuji výdej o $sign$pct % (≈ ${e.adaptiveTdee.toInt()} kcal)."
    }
}
