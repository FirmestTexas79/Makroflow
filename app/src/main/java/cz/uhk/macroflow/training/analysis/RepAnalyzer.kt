package cz.uhk.macroflow.training.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Rozbor série z trajektorie kotouče (čistý Kotlin, bez Androidu – pokryto testy).
 *
 * 1. Měřítko: kotouč má známý průměr (standardně 45 cm), takže cm/px = průměr / (2·medián poloměru).
 * 2. Vyhlazení polohy klouzavým průměrem v časovém okně (±70 ms) – potlačí šum detekce.
 * 3. Body obratu „zig-zag“ algoritmem s hysterezí: obrat se uzná až po návratu o víc než
 *    práh, takže chvění kolem vrcholu nevytvoří falešné opakování.
 * 4. Opakování = trojice obratů podle cviku (nahoře-dole-nahoře / dole-nahoře-dole).
 * 5. Fáze se měří od chvíle, kdy činka opravdu opustí výchozí polohu, tedy bez odpočinku mezi repy.
 */
object RepAnalyzer {

    const val DEFAULT_PLATE_DIAMETER_CM = 45.0
    private const val SMOOTH_HALF_WINDOW_MS = 70L
    /** Rychlost, od které se činka „hýbe“ (cm/s). */
    private const val MOVE_CM_S = 8.0

    private enum class Kind { PEAK, VALLEY }
    private data class Extreme(val index: Int, val kind: Kind)

    fun analyze(
        samples: List<Sample>,
        lift: Lift,
        plateDiameterCm: Double = DEFAULT_PLATE_DIAMETER_CM
    ): SetSummary? {
        if (samples.size < 10) return null
        val s = samples.sortedBy { it.tMs }
        val t = LongArray(s.size) { s[it].tMs }

        val medianRadius = s.map { it.radiusPx }.sorted()[s.size / 2]
        if (medianRadius <= 0f) return null
        val cmPerPx = plateDiameterCm / (2.0 * medianRadius)

        val ySm = smooth(t, DoubleArray(s.size) { s[it].yPx.toDouble() })
        val xSm = smooth(t, DoubleArray(s.size) { s[it].xPx.toDouble() })
        val yLowest = ySm.max()                                   // obraz: y dolů
        val h = DoubleArray(s.size) { (yLowest - ySm[it]) * cmPerPx } // výška nad nejnižším bodem
        val x = DoubleArray(s.size) { (xSm[it] - xSm[0]) * cmPerPx }

        val dts = (1 until t.size).map { t[it] - t[it - 1] }.sorted()
        val frameMs = dts[dts.size / 2].coerceAtLeast(1L)

        val threshold = max(lift.minRomCm * 0.5, 4.0)
        val ext = zigZag(h, threshold)
        val reps = mutableListOf<RepMetrics>()

        val (first, middle) = if (lift.eccentricFirst) Kind.PEAK to Kind.VALLEY else Kind.VALLEY to Kind.PEAK
        var k = 0
        while (k + 2 < ext.size) {
            val a = ext[k]; val b = ext[k + 1]; val c = ext[k + 2]
            if (a.kind != first || b.kind != middle || c.kind != first) { k++; continue }
            val rom = abs(h[a.index] - h[b.index])
            if (rom >= lift.minRomCm) {
                reps += measureRep(reps.size + 1, a.index, b.index, c.index, h, x, t, frameMs, lift)
            }
            k += 2
        }
        if (reps.isEmpty()) return null
        return summarize(lift, reps, cmPerPx)
    }

    private fun measureRep(
        index: Int, a: Int, b: Int, c: Int,
        h: DoubleArray, x: DoubleArray, t: LongArray, frameMs: Long, lift: Lift
    ): RepMetrics {
        val rom = abs(h[a] - h[b])
        val down = lift.eccentricFirst   // první fáze jde dolů?
        val v = velocity(h, t)           // cm/s, kladná = nahoru
        // Směrová rychlost: kladná = pohyb ve směru dané fáze
        fun first(i: Int) = if (down) -v[i] else v[i]
        fun second(i: Int) = -first(i)

        // Hranice fází podle rychlosti (> 8 cm/s = činka se hýbe). Poloha by u plynulého
        // rozjezdu/dojezdu uřízla až čtvrtinu fáze; rychlost jen pár procent.
        val peak1 = (a..b).maxBy { first(it) }
        val peak2 = (b..c).maxBy { second(it) }
        val moveStart = (a..peak1).lastOrNull { first(it) < MOVE_CM_S }?.let { it + 1 }?.coerceAtMost(peak1) ?: a
        val dwellStart = (peak1..b).firstOrNull { first(it) < MOVE_CM_S } ?: b
        val dwellEnd = (dwellStart..peak2).lastOrNull { second(it) < MOVE_CM_S }?.let { it + 1 }?.coerceAtMost(peak2) ?: b
        val done = (peak2..c).firstOrNull { second(it) < MOVE_CM_S } ?: c

        val firstPhase = t[dwellStart] - t[moveStart]
        val pause = (t[dwellEnd] - t[dwellStart]).coerceAtLeast(0)
        val secondPhase = t[done] - t[dwellEnd]
        val eccentric = if (down) firstPhase else secondPhase
        val concentric = if (down) secondPhase else firstPhase

        // Koncentrická fáze = pohyb nahoru
        val (cFrom, cTo) = if (down) dwellEnd to done else moveStart to dwellStart
        val concDist = (h[cTo] - h[cFrom]).coerceAtLeast(0.0) / 100.0
        val mcv = if (concentric > 0) concDist / (concentric / 1000.0) else 0.0
        val peak = (cFrom..cTo).maxOf { v[it] } / 100.0

        val deviation = (moveStart..done).maxOf { abs(x[it] - x[moveStart]) }
        val expected = (t[done] - t[moveStart]) / frameMs.toDouble() + 1.0
        val quality = ((done - moveStart + 1) / expected).coerceIn(0.0, 1.0)

        return RepMetrics(
            index = index,
            startCm = h[a], turnCm = h[b], endCm = h[c],
            romCm = rom,
            eccentricMs = eccentric, concentricMs = concentric, pauseMs = pause,
            totalMs = t[done] - t[moveStart],
            meanConcentricVelocity = mcv,
            peakConcentricVelocity = max(peak, mcv),
            deviationCm = deviation,
            quality = quality
        )
    }

    /** Rychlost centrální diferencí přes ±60 ms (na vyhlazené výšce), cm/s. */
    private fun velocity(h: DoubleArray, t: LongArray): DoubleArray {
        val v = DoubleArray(h.size)
        var lo = 0; var hi = 0
        for (i in h.indices) {
            while (lo < i && t[lo] < t[i] - 60) lo++
            if (hi < i) hi = i
            while (hi + 1 < h.size && t[hi + 1] <= t[i] + 60) hi++
            val dt = (t[hi] - t[lo]) / 1000.0
            v[i] = if (dt > 0) (h[hi] - h[lo]) / dt else 0.0
        }
        return v
    }

    private fun summarize(lift: Lift, reps: List<RepMetrics>, cmPerPx: Double): SetSummary {
        val roms = reps.map { it.romCm }
        val avgRom = roms.average()
        val wSum = reps.sumOf { it.quality }
        val weighted = if (wSum > 0) reps.sumOf { it.romCm * it.quality } / wSum else avgRom
        val sd = if (roms.size > 1) sqrt(roms.sumOf { (it - avgRom) * (it - avgRom) } / (roms.size - 1)) else 0.0
        val best = reps.maxOf { it.meanConcentricVelocity }
        val last = reps.last().meanConcentricVelocity
        return SetSummary(
            lift = lift,
            reps = reps,
            cmPerPx = cmPerPx,
            avgRomCm = avgRom,
            weightedRomCm = weighted,
            romCvPct = if (avgRom > 0) sd / avgRom * 100.0 else 0.0,
            avgEccentricMs = reps.map { it.eccentricMs }.average().toLong(),
            avgConcentricMs = reps.map { it.concentricMs }.average().toLong(),
            avgTotalMs = reps.map { it.totalMs }.average().toLong(),
            bestMcv = best,
            lastMcv = last,
            velocityLossPct = if (best > 0) ((best - last) / best * 100.0).coerceAtLeast(0.0) else 0.0,
            avgDeviationCm = reps.map { it.deviationCm }.average(),
            quality = reps.map { it.quality }.average()
        )
    }

    /** Klouzavý průměr v časovém okně ±70 ms (dva ukazatele, O(n)). */
    private fun smooth(t: LongArray, v: DoubleArray): DoubleArray {
        val out = DoubleArray(v.size)
        var lo = 0; var hi = 0; var sum = 0.0
        for (i in v.indices) {
            while (hi < v.size && t[hi] <= t[i] + SMOOTH_HALF_WINDOW_MS) { sum += v[hi]; hi++ }
            while (t[lo] < t[i] - SMOOTH_HALF_WINDOW_MS) { sum -= v[lo]; lo++ }
            out[i] = sum / (hi - lo)
        }
        return out
    }

    /** Body obratu s hysterezí [threshold] cm; výsledek se střídá PEAK / VALLEY. */
    private fun zigZag(h: DoubleArray, threshold: Double): List<Extreme> {
        val out = mutableListOf<Extreme>()
        var dir = 0
        var lo = 0; var hi = 0; var cand = 0
        for (i in 1 until h.size) {
            when (dir) {
                0 -> {
                    if (h[i] < h[lo]) lo = i
                    if (h[i] > h[hi]) hi = i
                    if (h[i] - h[lo] >= threshold) { out += Extreme(lo, Kind.VALLEY); dir = 1; cand = i }
                    else if (h[hi] - h[i] >= threshold) { out += Extreme(hi, Kind.PEAK); dir = -1; cand = i }
                }
                1 -> if (h[i] > h[cand]) cand = i
                    else if (h[cand] - h[i] >= threshold) { out += Extreme(cand, Kind.PEAK); dir = -1; cand = i }
                -1 -> if (h[i] < h[cand]) cand = i
                    else if (h[i] - h[cand] >= threshold) { out += Extreme(cand, Kind.VALLEY); dir = 1; cand = i }
            }
        }
        if (dir == 1) out += Extreme(cand, Kind.PEAK)
        if (dir == -1) out += Extreme(cand, Kind.VALLEY)
        return out
    }
}
