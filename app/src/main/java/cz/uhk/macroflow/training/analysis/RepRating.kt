package cz.uhk.macroflow.training.analysis

import kotlin.math.abs

/**
 * Hodnocení opakování pro barevný přechod zelená → červená.
 * Vrací 0.0 (zelená = nejlepší / v normě) až 1.0 (červená = výrazně horší).
 *
 * Srovnává se vždy uvnitř série (nebo mezi sériemi), ne s absolutní normou –
 * cílem je ukázat, jak se od sebe repy/série liší, ne známkovat techniku.
 */
object RepRating {

    enum class Metric(val label: String, val legend: String) {
        ROM("Rozsah", "odchylka od typického rozsahu série (0 % → 15 %+)"),
        ECCENTRIC("Spouštění", "odchylka od typické doby spouštění (0 % → 50 %+)"),
        CONCENTRIC("Zvedání", "pomalejší než nejrychlejší rep (0 % → 60 %+)"),
        VELOCITY("Rychlost", "ztráta proti nejrychlejšímu repu (0 % → 40 %+)"),
        DEVIATION("Dráha", "odchylka dráhy do strany (0 cm → limit cviku)")
    }

    /** Hodnocení jednoho repu v rámci série. */
    fun score(metric: Metric, rep: RepMetrics, set: SetSummary): Double {
        val reps = set.reps
        return when (metric) {
            Metric.ROM -> {
                val med = median(reps.map { it.romCm })
                ramp(abs(rep.romCm - med) / med, 0.15)
            }
            Metric.ECCENTRIC -> {
                val med = median(reps.map { it.eccentricMs.toDouble() })
                if (med <= 0) 0.0 else ramp(abs(rep.eccentricMs - med) / med, 0.50)
            }
            Metric.CONCENTRIC -> {
                val fastest = reps.minOf { it.concentricMs }.toDouble()
                if (fastest <= 0) 0.0 else ramp((rep.concentricMs - fastest) / fastest, 0.60)
            }
            // 40% ztráta rychlosti ≈ blízko selhání (González-Badillo & Sánchez-Medina 2010)
            Metric.VELOCITY -> {
                val best = set.bestMcv
                if (best <= 0) 0.0 else ramp((best - rep.meanConcentricVelocity) / best, 0.40)
            }
            Metric.DEVIATION -> ramp(rep.deviationCm / set.lift.maxDeviationCm, 1.0)
        }
    }

    /** Hodnocení série proti nejlepší sérii tréninku (průměrná rychlost a rozsah). */
    fun setScore(set: SetSummary, allSets: List<SetSummary>): Double {
        val bestV = allSets.maxOf { it.bestMcv }
        val medRom = median(allSets.map { it.weightedRomCm })
        val v = if (bestV > 0) ramp((bestV - set.bestMcv) / bestV, 0.40) else 0.0
        val r = if (medRom > 0) ramp(abs(set.weightedRomCm - medRom) / medRom, 0.15) else 0.0
        return maxOf(v, r)
    }

    /** Barva jako ARGB Int: zelená (#4CAF50) → žlutá (#FFC107) → červená (#E53935). */
    fun color(score: Double): Int {
        val s = score.coerceIn(0.0, 1.0)
        val (from, to, f) = if (s < 0.5) Triple(0x4CAF50, 0xFFC107, s / 0.5) else Triple(0xFFC107, 0xE53935, (s - 0.5) / 0.5)
        fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
        fun mix(shift: Int) = (ch(from, shift) + (ch(to, shift) - ch(from, shift)) * f).toInt()
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun ramp(value: Double, redAt: Double): Double = (value / redAt).coerceIn(0.0, 1.0)

    private fun median(v: List<Double>): Double {
        if (v.isEmpty()) return 0.0
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
