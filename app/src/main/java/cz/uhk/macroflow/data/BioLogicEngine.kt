package cz.uhk.macroflow.analytics

import cz.uhk.macroflow.data.AnalyticsCacheEntity
import cz.uhk.macroflow.data.CheckInEntity
import cz.uhk.macroflow.energy.WeightTrend
import java.time.LocalDate

/**
 * Analytika hmotnosti z check-inů – nad Kalmanovým filtrem [WeightTrend] (fáze B).
 *
 * Nahrazuje dřívější EMA přes záznamy + regresi přes pořadí záznamů (mezery ve vážení
 * zkreslovaly sklon) a „modifikátor vitality“ podle nálady, který neměl fyziologický základ.
 */
object BioLogicEngine {

    private val trend = WeightTrend()

    /** Dny od epochy pro klíč yyyy-MM-dd. */
    fun dayOf(date: String): Int = LocalDate.parse(date).toEpochDay().toInt()

    fun trendPoints(history: List<CheckInEntity>): List<WeightTrend.Point> =
        trend.filter(history.associate { dayOf(it.date) to it.weight })

    /** Hodí IllegalArgumentException, pokud je history prázdná. */
    fun calculateFullAnalytics(history: List<CheckInEntity>): AnalyticsCacheEntity {
        require(history.isNotEmpty()) { "BioLogicEngine: history nesmí být prázdná" }
        val latest = history.maxBy { it.date }
        val now = trendPoints(history).last()

        return AnalyticsCacheEntity(
            date                = latest.date,
            smoothedWeight      = now.level,
            trendSlope          = now.slope,          // kg/den
            standardDeviation   = now.sdLevel,
            confidenceScore     = (history.size / 14f).coerceIn(0.1f, 1.0f),
            metabolicEfficiency = 1.0                 // nahrazeno tabulkou adaptive_tdee
        )
    }

    /**
     * Predikce na [days] dní dopředu: (den, střed, spodní mez, horní mez) – 95% interval.
     * Interval vychází z nejistoty filtru, ne z ručně laděných konstant.
     */
    fun forecast(history: List<CheckInEntity>, days: Int): List<Forecast> {
        if (history.isEmpty()) return emptyList()
        val now = trendPoints(history).last()
        return (0..days).map { i ->
            val (mean, sd) = trend.forecast(now, i)
            Forecast(now.day + i, mean, mean - 1.96 * sd, mean + 1.96 * sd)
        }
    }

    data class Forecast(val day: Int, val mean: Double, val lower: Double, val upper: Double)
}
