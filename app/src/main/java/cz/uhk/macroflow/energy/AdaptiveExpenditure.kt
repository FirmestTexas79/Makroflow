package cz.uhk.macroflow.energy

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Adaptivní odhad skutečného výdeje z energetické bilance (fáze B).
 *
 *   příjem − výdej = změna zásob energie ≈ Δhmotnosti(trend) · 7700 kcal/kg
 *   ⇒ výdej_pozorovaný = průměrný příjem − (sklon trendu · 7700)
 *
 * Pozorování se kombinuje s odhadem modelu (fáze A) váženě podle nejistot
 * (inverse-variance / konjugovaný normální model). Výsledkem je korekční faktor k,
 * kterým se násobí výdej z modelu – denní rozdíly (kroky, trénink) tak zůstávají.
 *
 * Datová kvalita:
 *  - den se počítá jako „zapsaný“, jen když příjem ≥ 50 % modelového výdeje
 *    (prázdné nebo zjevně nedopsané dny by výdej podhodnotily),
 *  - bez dostatku dat vrací k = 1 (nic se nemění).
 */
object AdaptiveExpenditure {

    const val WINDOW_DAYS = 28
    const val MIN_SPAN_DAYS = 14
    const val MIN_WEIGH_INS = 7
    const val MIN_LOGGED_DAYS = 10

    /** Relativní nejistota prediktivních rovnic (Mifflin ±10 % u ~80 % lidí, Frankenfield 2005). */
    const val MODEL_RELATIVE_SD = 0.12
    /** Systematická nepřesnost zápisu jídla (etikety, odhad porcí). */
    const val INTAKE_RELATIVE_SD = 0.05
    const val MIN_FACTOR = 0.80
    const val MAX_FACTOR = 1.20

    data class Day(
        val day: Int,               // pořadové číslo dne (epochDay)
        val weightKg: Double?,      // ranní vážení, null = nevážil se
        val intakeKcal: Double?,    // zapsaný příjem, null = nic nezapsáno
        val modelTdeeKcal: Double   // výdej z modelu fáze A (bez korekce)
    )

    enum class Status { OK, NOT_ENOUGH_DATA }

    data class Estimate(
        val status: Status,
        val factor: Double,                 // k – násobí se jím výdej modelu
        val modelTdee: Double,              // průměr modelu v okně
        val observedTdee: Double?,          // z bilance
        val observedSd: Double?,
        val adaptiveTdee: Double,           // výsledný odhad
        val confidence: Double,             // váha pozorování 0..1
        val trendWeightKg: Double?,         // aktuální vyhlazená hmotnost
        val weightChangeKgPerWeek: Double?, // tempo z trendu
        val weighIns: Int,
        val loggedDays: Int
    )

    fun estimate(days: List<Day>, trend: WeightTrend = WeightTrend()): Estimate {
        val window = days.sortedBy { it.day }.let { all ->
            val lastDay = all.lastOrNull()?.day ?: return notEnough(0.0, null, null, 0, 0)
            all.filter { it.day > lastDay - WINDOW_DAYS }
        }
        val modelMean = window.map { it.modelTdeeKcal }.average()

        val weights = window.mapNotNull { d -> d.weightKg?.let { d.day to it } }.toMap()
        val points = trend.smooth(weights)
        val weighIns = weights.size

        val logged = window.filter { d -> d.intakeKcal != null && d.intakeKcal >= 0.5 * d.modelTdeeKcal }
        val trendNow = points.lastOrNull()
        val span = if (points.isEmpty()) 0 else points.last().day - points.first().day

        if (weighIns < MIN_WEIGH_INS || logged.size < MIN_LOGGED_DAYS || span < MIN_SPAN_DAYS || trendNow == null) {
            return notEnough(modelMean, trendNow?.level, trendNow?.slope, weighIns, logged.size)
        }

        // Průměrný sklon vyhlazeného trendu v okně (kg/den) – ne jen poslední okamžitý
        val end = points.last()
        val slope = points.map { it.slope }.average()
        val slopeSd = points[points.size / 2].sdSlope

        val intakes = logged.map { it.intakeKcal!! }
        val meanIntake = intakes.average()
        val intakeVar = intakes.sumOf { (it - meanIntake) * (it - meanIntake) } / (intakes.size - 1).coerceAtLeast(1)

        val observed = meanIntake - slope * MacroPlanner.KCAL_PER_KG_TISSUE
        val observedVar = intakeVar / intakes.size +
            (INTAKE_RELATIVE_SD * meanIntake).let { it * it } +
            (slopeSd * MacroPlanner.KCAL_PER_KG_TISSUE).let { it * it }

        val priorVar = (MODEL_RELATIVE_SD * modelMean).let { it * it }
        val w = priorVar / (priorVar + observedVar)
        val posterior = modelMean + w * (observed - modelMean)
        val factor = (posterior / modelMean).coerceIn(MIN_FACTOR, MAX_FACTOR)

        return Estimate(
            status = Status.OK,
            factor = factor,
            modelTdee = modelMean,
            observedTdee = observed,
            observedSd = sqrt(observedVar),
            adaptiveTdee = modelMean * factor,
            confidence = w,
            trendWeightKg = end.level,
            weightChangeKgPerWeek = end.slope * 7,
            weighIns = weighIns,
            loggedDays = logged.size
        )
    }

    private fun notEnough(model: Double, level: Double?, slope: Double?, weighIns: Int, logged: Int) = Estimate(
        status = Status.NOT_ENOUGH_DATA, factor = 1.0, modelTdee = model, observedTdee = null,
        observedSd = null, adaptiveTdee = model, confidence = 0.0, trendWeightKg = level,
        weightChangeKgPerWeek = slope?.times(7), weighIns = weighIns, loggedDays = logged
    )

    /** Změnil se odhad natolik, aby stálo za to ho uživateli ukázat? */
    fun isMeaningful(e: Estimate): Boolean = e.status == Status.OK && abs(e.factor - 1.0) >= 0.03
}
