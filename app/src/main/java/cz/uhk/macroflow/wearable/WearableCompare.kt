package cz.uhk.macroflow.wearable

import kotlin.math.abs

/**
 * Srovnani vydeje: model aplikace vs. Garmin vs. "realita" (pozorovany vydej z vahy a jidla, AdaptiveExpenditure).
 * Pozorovani je okno-prumer, proto se porovnavaji prumery ze stejnych dnu.
 */
object WearableCompare {
    const val MIN_DAYS = 10

    data class Day(val modelKcal: Double, val garminKcal: Double)
    data class Result(
        val days: Int, val modelMean: Double, val garminMean: Double, val observed: Double,
        val modelErrPct: Double, val garminErrPct: Double,
        /** Vaha Garminu pri prumerovani 0.1..0.9 (inverzni kvadrat chyby, s podlahou 3 % kvuli sumu pozorovani). */
        val garminWeight: Double
    ) { val blended get() = garminWeight * garminMean + (1 - garminWeight) * modelMean }

    fun compare(days: List<Day>, observedKcal: Double?): Result? {
        if (days.size < MIN_DAYS || observedKcal == null || observedKcal <= 0) return null
        val m = days.map { it.modelKcal }.average()
        val g = days.map { it.garminKcal }.average()
        val floor = (0.03 * observedKcal).let { it * it }
        val em = (m - observedKcal).let { it * it } + floor
        val eg = (g - observedKcal).let { it * it } + floor
        val w = ((1 / eg) / (1 / eg + 1 / em)).coerceIn(0.1, 0.9)
        return Result(days.size, m, g, observedKcal, abs(m - observedKcal) / observedKcal * 100, abs(g - observedKcal) / observedKcal * 100, w)
    }
}