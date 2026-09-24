package cz.uhk.macroflow.dashboard

import cz.uhk.macroflow.energy.EnergyBreakdown

data class MacroResult(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val water: Double,
    val trainingType: String,
    val weight: Double,
    val isEliteMode: Boolean = false,
    /** Rozpad výdeje (BMR, NEAT, chůze, trénink, TEF) – pro přehled a report. */
    val expenditure: EnergyBreakdown? = null
)
