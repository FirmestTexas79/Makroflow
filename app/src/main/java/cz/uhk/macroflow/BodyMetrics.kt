package cz.uhk.macroflow

data class BodyMetrics(
    val date: Long = System.currentTimeMillis(),
    val neck: Double = 0.0,
    val chest: Double = 0.0,
    val bicep: Double = 0.0,
    val forearm: Double = 0.0,
    val waist: Double = 0.0,
    val abdomen: Double = 0.0,
    val thigh: Double = 0.0,
    val calf: Double = 0.0
)