package cz.uhk.macroflow.training.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos

/**
 * Syntetické série: známý rozsah, doby fází a šum detekce.
 * Kotouč 45 cm = 120 px → 0,375 cm/px.
 */
class RepAnalyzerTest {

    private val radiusPx = 60f
    private val cmPerPx = 45.0 / 120.0

    data class RepSpec(val romCm: Double, val firstMs: Long, val pauseMs: Long, val secondMs: Long, val restMs: Long,
                       val driftCm: Double = 0.0)

    /**
     * @param startHigh true = začíná nahoře (dřep/bench), false = na zemi (MT)
     */
    private fun simulate(reps: List<RepSpec>, startHigh: Boolean, noisePx: Double = 3.0, fps: Int = 30,
                         dropEvery: Int = 0, seed: Long = 1, leadMs: Long = 800): List<Sample> {
        val rnd = Random(seed)
        val frame = 1000L / fps
        val out = mutableListOf<Sample>()
        val baseY = 600.0
        var t = 0L
        var frameNo = 0
        fun emit(yCm: Double, xCm: Double) {
            frameNo++
            if (dropEvery > 0 && frameNo % dropEvery == 0) { t += frame; return }
            val yPx = baseY - yCm / cmPerPx + rnd.nextGaussian() * noisePx
            val xPx = 300.0 + xCm / cmPerPx + rnd.nextGaussian() * noisePx
            out += Sample(t, xPx.toFloat(), yPx.toFloat(), radiusPx + (rnd.nextGaussian() * 1.5).toFloat())
            t += frame
        }
        fun ease(p: Double) = (1 - cos(PI * p)) / 2
        val rest0 = if (startHigh) reps.first().romCm else 0.0
        var tt = 0L; while (tt < leadMs) { emit(rest0, 0.0); tt += frame }
        for (r in reps) {
            val top = if (startHigh) r.romCm else 0.0
            val turn = if (startHigh) 0.0 else r.romCm
            var e = 0L; while (e < r.firstMs) { val p = ease(e / r.firstMs.toDouble()); emit(top + (turn - top) * p, r.driftCm * p); e += frame }
            e = 0; while (e < r.pauseMs) { emit(turn, r.driftCm); e += frame }
            e = 0; while (e < r.secondMs) { val p = ease(e / r.secondMs.toDouble()); emit(turn + (top - turn) * p, r.driftCm * (1 - p)); e += frame }
            e = 0; while (e < r.restMs) { emit(top, 0.0); e += frame }
        }
        return out
    }

    @Test fun `dřep - počet, rozsah a doby fází`() {
        val spec = RepSpec(60.0, firstMs = 1200, pauseMs = 300, secondMs = 800, restMs = 1000)
        val s = RepAnalyzer.analyze(simulate(List(5) { spec }, startHigh = true), Lift.SQUAT)!!
        assertEquals(5, s.repCount)
        assertEquals(60.0, s.avgRomCm, 2.5)
        s.reps.forEach {
            assertEquals("ecc ${it.eccentricMs}", 1200.0, it.eccentricMs.toDouble(), 150.0)
            assertEquals("conc ${it.concentricMs}", 800.0, it.concentricMs.toDouble(), 150.0)
            assertTrue("pauza ${it.pauseMs}", it.pauseMs in 150..800)
            // průměrná rychlost zvedání ≈ 0,6 m / 0,8 s
            assertEquals(0.75, it.meanConcentricVelocity, 0.2)
        }
        assertTrue(s.romCvPct < 5.0)
    }

    @Test fun `odpočinek mezi repy se nezapočítá do doby repu`() {
        val spec = RepSpec(60.0, 1000, 0, 1000, restMs = 3000)
        val s = RepAnalyzer.analyze(simulate(List(3) { spec }, startHigh = true), Lift.SQUAT)!!
        s.reps.forEach { assertTrue("total ${it.totalMs}", it.totalMs < 2600) }
    }

    @Test fun `únava - zpomalení zvedání se projeví ztrátou rychlosti`() {
        val reps = (0 until 6).map { RepSpec(55.0, 1000, 200, 700L + it * 250L, 800) }
        val s = RepAnalyzer.analyze(simulate(reps, startHigh = true), Lift.SQUAT)!!
        assertEquals(6, s.repCount)
        assertTrue("ztráta ${s.velocityLossPct}", s.velocityLossPct > 40)
        val last = s.reps.last()
        assertTrue(RepRating.score(RepRating.Metric.VELOCITY, last, s) > 0.8)
        assertEquals(0.0, RepRating.score(RepRating.Metric.VELOCITY, s.reps.first(), s), 0.15)
    }

    @Test fun `mrtvý tah začíná zvedáním ze země`() {
        val spec = RepSpec(55.0, firstMs = 900, pauseMs = 200, secondMs = 1100, restMs = 1200)
        val s = RepAnalyzer.analyze(simulate(List(4) { spec }, startHigh = false), Lift.DEADLIFT)!!
        assertEquals(4, s.repCount)
        s.reps.forEach {
            assertEquals("conc ${it.concentricMs}", 900.0, it.concentricMs.toDouble(), 150.0)
            assertEquals("ecc ${it.eccentricMs}", 1100.0, it.eccentricMs.toDouble(), 150.0)
        }
    }

    @Test fun `bench s menším rozsahem`() {
        val spec = RepSpec(38.0, 1100, 400, 700, 900)
        val s = RepAnalyzer.analyze(simulate(List(8) { spec }, startHigh = true), Lift.BENCH)!!
        assertEquals(8, s.repCount)
        assertEquals(38.0, s.avgRomCm, 2.0)
    }

    @Test fun `malý pohyb (sundání z držáků) není opakování`() {
        val partial = RepSpec(6.0, 500, 0, 500, 500)
        val full = RepSpec(60.0, 1200, 200, 800, 1000)
        val s = RepAnalyzer.analyze(simulate(listOf(partial, full, full, partial), startHigh = true), Lift.SQUAT)!!
        assertEquals(2, s.repCount)
    }

    @Test fun `vypadlé snímky - repy sedí a kvalita klesne`() {
        val spec = RepSpec(60.0, 1200, 300, 800, 1000)
        val s = RepAnalyzer.analyze(simulate(List(4) { spec }, startHigh = true, dropEvery = 3), Lift.SQUAT)!!
        assertEquals(4, s.repCount)
        assertTrue("kvalita ${s.quality}", s.quality in 0.55..0.8)
        // vážený průměr existuje a je blízko prostému
        assertEquals(s.avgRomCm, s.weightedRomCm, 1.0)
    }

    @Test fun `odchylka dráhy do strany v cm`() {
        val spec = RepSpec(60.0, 1200, 300, 800, 1000, driftCm = 10.0)
        val s = RepAnalyzer.analyze(simulate(List(3) { spec }, startHigh = true, noisePx = 1.0), Lift.SQUAT)!!
        s.reps.forEach { assertEquals(10.0, it.deviationCm, 2.0) }
        assertTrue(RepRating.score(RepRating.Metric.DEVIATION, s.reps.first(), s) > 0.9) // limit dřepu 8 cm
    }

    @Test fun `příliš málo dat`() = assertNull(RepAnalyzer.analyze(emptyList(), Lift.SQUAT))

    @Test fun `barevný přechod`() {
        assertEquals(0xFF4CAF50.toInt(), RepRating.color(0.0))
        assertEquals(0xFFFFC107.toInt(), RepRating.color(0.5))
        assertEquals(0xFFE53935.toInt(), RepRating.color(1.0))
    }

    // ── Automatická série ───────────────────────────────────────────────────

    @Test fun `aktivní režim - série se sama spustí a ukončí`() {
        val spec = RepSpec(60.0, 1200, 300, 800, 1000)
        val samples = simulate(List(5) { spec }, startHigh = true, leadMs = 3000) +
            // 6 s klidu po sérii
            (1..180).map { i -> Sample(0L, 300f, 600f - (60 / cmPerPx).toFloat(), radiusPx) }
        val last = samples.take(samples.size - 180).last().tMs
        val tail = samples.takeLast(180).mapIndexed { i, s -> s.copy(tMs = last + 33L * (i + 1)) }
        val stream = samples.take(samples.size - 180) + tail

        val d = SetDetector()
        var finished: List<Sample>? = null
        for (s in stream) { d.onSample(s)?.let { finished = it } }
        val set = finished!!
        assertEquals(SetDetector.State.IDLE, d.state)
        assertEquals(5, RepAnalyzer.analyze(set, Lift.SQUAT)!!.repCount)
    }

    @Test fun `klid bez pohybu sérii nespustí`() {
        val d = SetDetector()
        repeat(300) { i -> d.onSample(Sample(i * 33L, 300f, 400f + (i % 3), radiusPx)) }
        assertEquals(SetDetector.State.IDLE, d.state)
    }
}
