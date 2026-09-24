package cz.uhk.macroflow.training.analysis

/**
 * Cvik a jeho vlastnosti, které mění výpočet.
 *
 * @param eccentricFirst dřep/bench začínají spouštěním (nahoře → dolů → nahoru),
 *                       mrtvý tah a tlaky nad hlavu zvedáním (dole → nahoru → dolů)
 * @param minRomCm menší pohyb se nepočítá jako opakování (odlehčení, přešlap, sundání z držáků)
 * @param maxDeviationCm odchylka dráhy do strany, od které je hodnocení „červené“.
 *        U benche je dráha přirozeně šikmá (tzv. J-křivka), proto víc.
 */
enum class Lift(
    val label: String,
    val eccentricFirst: Boolean,
    val minRomCm: Double,
    val maxDeviationCm: Double
) {
    SQUAT("Dřep", eccentricFirst = true, minRomCm = 20.0, maxDeviationCm = 8.0),
    BENCH("Bench press", eccentricFirst = true, minRomCm = 12.0, maxDeviationCm = 14.0),
    OHP("Tlaky nad hlavu", eccentricFirst = false, minRomCm = 20.0, maxDeviationCm = 10.0),
    DEADLIFT("Mrtvý tah", eccentricFirst = false, minRomCm = 20.0, maxDeviationCm = 8.0);

    companion object {
        fun from(name: String?): Lift = entries.firstOrNull { it.name == name } ?: SQUAT
    }
}

/** Jeden snímek z kamery (souřadnice obrazu, y roste dolů). */
data class Sample(
    val tMs: Long,
    val xPx: Float,
    val yPx: Float,
    val radiusPx: Float
)

/** Výsledek jednoho opakování. Výšky jsou v cm nad nejnižším bodem série (0 = nejníž). */
data class RepMetrics(
    val index: Int,
    val startCm: Double,        // počáteční bod rozsahu (odkud pohyb vyšel)
    val turnCm: Double,         // bod obratu (dole u dřepu/benche, nahoře u MT/tlaků)
    val endCm: Double,          // konečný bod (kam se činka vrátila)
    val romCm: Double,          // vzdálenost start ↔ obrat
    val eccentricMs: Long,      // spouštění
    val concentricMs: Long,     // zvedání
    val pauseMs: Long,          // výdrž v bodě obratu
    val totalMs: Long,          // od začátku pohybu po dokončení (bez odpočinku mezi repy)
    val meanConcentricVelocity: Double, // m/s – základ tréninku podle rychlosti (VBT)
    val peakConcentricVelocity: Double, // m/s
    val deviationCm: Double,    // max. odchylka dráhy do strany během repu
    val quality: Double         // 0..1 – podíl snímků, kdy byl kotouč opravdu vidět
)

data class SetSummary(
    val lift: Lift,
    val reps: List<RepMetrics>,
    val cmPerPx: Double,
    val avgRomCm: Double,
    val weightedRomCm: Double,  // vážený průměr – váha = kvalita sledování repu
    val romCvPct: Double,       // variabilita rozsahu (konzistence)
    val avgEccentricMs: Long,
    val avgConcentricMs: Long,
    val avgTotalMs: Long,
    val bestMcv: Double,
    val lastMcv: Double,
    val velocityLossPct: Double,
    val avgDeviationCm: Double,
    val quality: Double
) {
    val repCount: Int get() = reps.size
}
