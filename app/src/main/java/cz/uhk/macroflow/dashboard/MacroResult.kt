package cz.uhk.macroflow.dashboard

data class MacroResult(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val water: Double,
    val trainingType: String,
    val weight: Double,
    val isEliteMode: Boolean = false
)