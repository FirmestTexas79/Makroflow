package cz.uhk.macroflow.analytics

import cz.uhk.macroflow.data.AnalyticsCacheEntity
import cz.uhk.macroflow.data.CheckInEntity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

object BioLogicEngine {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Vypočítá analytiku z historie check-inů.
     * History musí obsahovat alespoň 1 záznam — CheckInFragment to zaručuje
     * tím, že volá tuto funkci AŽ po uložení check-inu do DB.
     *
     * Hodí IllegalArgumentException pokud je history prázdná.
     */
    fun calculateFullAnalytics(history: List<CheckInEntity>): AnalyticsCacheEntity {
        require(history.isNotEmpty()) { "BioLogicEngine: history nesmí být prázdná" }

        val sorted = history.sortedBy { it.date }
        val latest = sorted.last()

        val smoothed    = calculateEma(sorted)
        val trendResult = analyzeTrend(sorted)
        val vitality    = calculateVitalityModifier(sorted.takeLast(3))

        // FIX: Tady ten slope "zlidštíme".
        // I když regrese řekne šílený pád, zastropujeme to na cca 1.5kg/týden max pro graf.
        // 0.2 kg/den je už hodně drsný trend (1.4kg týdně).
        val cappedSlope = (trendResult.slope * vitality).coerceIn(-0.2, 0.2)

        return AnalyticsCacheEntity(
            date                = latest.date,
            smoothedWeight      = smoothed,
            trendSlope          = cappedSlope, // Teď už to nebude padat o 5 kilo
            standardDeviation   = trendResult.sigma,
            confidenceScore     = calculateConfidence(sorted.size),
            metabolicEfficiency = vitality
        )
    }

    // Exponenciální klouzavý průměr (alpha=0.3 = střední citlivost)
    private fun calculateEma(history: List<CheckInEntity>): Double {
        val alpha = 0.3
        var ema   = history.first().weight
        for (i in 1 until history.size) {
            ema = alpha * history[i].weight + (1 - alpha) * ema
        }
        return ema
    }

    // Mezistav (2–6 záznamů): delta-průměr
    // Profi mód (7+ záznamů): lineární regrese nejmenšími čtverci
    private fun analyzeTrend(history: List<CheckInEntity>): InternalTrend {
        if (history.size < 2) return InternalTrend(0.0, 0.5)

        if (history.size < 7) {
            val days  = getDayDiff(history.first().date, history.last().date).toDouble().coerceAtLeast(1.0)
            val slope = (history.last().weight - history.first().weight) / days
            return InternalTrend(slope, 0.4)
        }

        val n   = history.size
        val x   = DoubleArray(n) { it.toDouble() }
        val y   = DoubleArray(n) { history[it].weight }

        val sumX  = x.sum()
        val sumY  = y.sum()
        val sumXY = x.zip(y).sumOf { it.first * it.second }
        val sumX2 = x.sumOf { it * it }
        val denom = n * sumX2 - sumX.pow(2)

        if (denom == 0.0) return InternalTrend(0.0, 0.5)

        val slope     = (n * sumXY - sumX * sumY) / denom
        val intercept = (sumY - slope * sumX) / n
        val variance  = y.indices.sumOf { i -> (y[i] - (slope * x[i] + intercept)).pow(2) } / n

        return InternalTrend(slope, sqrt(variance).coerceAtLeast(0.1))
    }

    // Modifikátor vitality podle průměrné energie za poslední 3 dny
    private fun calculateVitalityModifier(recent: List<CheckInEntity>): Double {
        if (recent.isEmpty()) return 1.0
        return when {
            recent.map { it.energyLevel }.average() >= 4.5 -> 1.05
            recent.map { it.energyLevel }.average() >= 3.0 -> 1.00
            else -> 0.92
        }
    }

    // Spolehlivost: 0.1 (1 záznam) → 1.0 (14+ záznamů)
    private fun calculateConfidence(dataPoints: Int): Float =
        (dataPoints / 14f).coerceIn(0.1f, 1.0f)

    private fun getDayDiff(start: String, end: String): Long {
        return try {
            val d1 = sdf.parse(start) ?: return 1
            val d2 = sdf.parse(end)   ?: return 1
            (d2.time - d1.time) / (1000L * 60 * 60 * 24)
        } catch (e: Exception) { 1 }
    }

    // BioLogicEngine.kt - Rozšíření pro Predikci
    fun generatePredictionPath(lastAnalytics: AnalyticsCacheEntity, days: Int = 14): List<Pair<Long, Double>> {
        val prediction = mutableListOf<Pair<Long, Double>>()
        val lastDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(lastAnalytics.date) ?: Date()

        for (i in 1..days) {
            val futureTime = lastDate.time + (i * 24 * 60 * 60 * 1000L)
            // Predikovaná váha = poslední vyhlazená váha + (trend * počet dní)
            val predictedWeight = lastAnalytics.smoothedWeight + (lastAnalytics.trendSlope * i)
            prediction.add(futureTime to predictedWeight)
        }
        return prediction
    }

    private data class InternalTrend(val slope: Double, val sigma: Double)
}