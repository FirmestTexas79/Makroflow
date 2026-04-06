package cz.uhk.macroflow.data

data class FoodAIResult(
    val name: String,
    val weight: String,
    val p: Float,
    val s: Float,
    val t: Float,
    val fiber: Float,
    val kj: Float
)