package cz.uhk.macroflow.energy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

class WeightProjectionTest {

    private val kcalPerKg = MacroPlanner.KCAL_PER_KG_TISSUE

    /** Lineární průběh s volitelným šumem vážení; den 1000 = první vážení. */
    private fun series(days: Int, start: Double, perDay: Double, noise: Double = 0.0, seed: Long = 1): Map<Int, Double> {
        val rnd = Random(seed)
        return (0 until days).associate { d -> 1000 + d to start + perDay * d + noise * rnd.nextGaussian() }
    }

    private fun energyFor(perDayKg: Double, sd: Double = 150.0) = WeightProjection.Energy(
        intakeKcal = 2500.0 + perDayKg * kcalPerKg, intakeSd = sd,
        expenditureKcal = 2500.0, expenditureSd = sd, source = WeightProjection.IntakeSource.LOGGED
    )

    @Test
    fun noWeighInsNoProjection() {
        assertNull(WeightProjection.project(emptyMap(), 1000, null))
        assertNull(WeightProjection.project(mapOf(1005 to 80.0), 1000, null))   // jen budoucí vážení
    }

    @Test
    fun stableWeightProjectsFlatWithWideningBand() {
        val p = WeightProjection.project(series(21, 80.0, 0.0), 1020, null)!!
        assertEquals(80.0, p.end.mean, 0.1)
        assertEquals(8, p.forecast.size)
        val first = p.forecast.first(); val last = p.end
        assertTrue(last.upper - last.lower > first.upper - first.lower)
        assertTrue(abs(p.slopeKgPerWeek) < 0.1)
    }

    @Test
    fun steadyLossIsExtrapolated() {
        // −0,1 kg/den po 28 dní, bez šumu
        val p = WeightProjection.project(series(28, 85.0, -0.1), 1027, null)!!
        assertEquals(-0.7, p.slopeKgPerWeek, 0.08)
        assertEquals(85.0 - 0.1 * 34, p.end.mean, 0.3)
    }

    @Test
    fun energyBalanceLeadsWhenWeighInsAreFew() {
        // Jedno vážení: tempo z vážení neznámé → projekce stojí na bilanci (−770 kcal ≈ −0,1 kg/den)
        val p = WeightProjection.project(mapOf(1000 to 80.0), 1000, energyFor(-0.1))!!
        assertTrue("váha bilance ${p.energyWeight}", p.energyWeight > 0.9)
        assertEquals(-0.7, p.slopeKgPerWeek, 0.1)
    }

    @Test
    fun weighInsLeadWhenPlentiful() {
        // 28 dní stabilní váhy, ale bilance tvrdí −0,1 kg/den → věříme hlavně vážení
        val p = WeightProjection.project(series(28, 80.0, 0.0, noise = 0.5), 1027, energyFor(-0.1, sd = 250.0))!!
        assertTrue("váha bilance ${p.energyWeight}", p.energyWeight < 0.5)
        assertTrue(p.slopeKgPerWeek > -0.35)
    }

    @Test
    fun energyNarrowsTheInterval() {
        val w = series(5, 80.0, 0.0, noise = 0.6)
        val without = WeightProjection.project(w, 1004, null)!!
        val with = WeightProjection.project(w, 1004, energyFor(0.0))!!
        assertTrue(with.end.upper - with.end.lower < without.end.upper - without.end.lower)
    }

    @Test
    fun pastDayIgnoresLaterWeighIns() {
        val w = series(14, 80.0, 0.0) + series(14, 80.0, -0.3).mapKeys { it.key + 14 }
        val p = WeightProjection.project(w, 1013, null)!!
        assertEquals(1013, p.start.day)
        assertEquals(14, p.weighIns)
        assertTrue(abs(p.slopeKgPerWeek) < 0.1)
    }

    @Test
    fun selectedDayAfterLastWeighInMovesForward() {
        val w = series(14, 80.0, 0.0, noise = 0.5)
        val atLast = WeightProjection.project(w, 1013, null)!!
        val later = WeightProjection.project(w, 1016, null)!!
        assertEquals(1016, later.start.day)
        assertTrue(later.start.varLevel > atLast.start.varLevel)
    }

    @Test
    fun slopeObservationShrinksUncertainty() {
        val t = WeightTrend()
        val pt = t.filter(series(3, 80.0, 0.0)).last()
        val (upd, k) = t.observeSlope(pt, -0.05, 0.02)
        assertTrue(upd.varSlope < pt.varSlope)
        assertTrue(upd.varLevel <= pt.varLevel + 1e-12)
        assertTrue(k in 0.0..1.0)
        assertTrue(upd.slope < pt.slope)
    }

    @Test
    fun energyInputPrefersLogsOverTarget() {
        val logged = WeightProjection.energyInput(listOf(2100.0, 2300.0, 2200.0, 2250.0), 2000.0, 2600.0)
        assertEquals(WeightProjection.IntakeSource.LOGGED, logged.source)
        assertEquals(2212.5, logged.intakeKcal, 1e-9)
        val target = WeightProjection.energyInput(listOf(2100.0), 2000.0, 2600.0)
        assertEquals(WeightProjection.IntakeSource.TARGET, target.source)
        assertEquals(300.0, target.intakeSd, 1e-9)
        assertEquals(-600.0 / kcalPerKg, target.slopeKgPerDay, 1e-12)
    }

    // ── hodnocení tempa ─────────────────────────────────────────────────────

    private fun projectionAt(perDay: Double) = WeightProjection.project(series(28, 80.0, perDay), 1027, null)!!

    @Test
    fun cutPaceVerdicts() {
        assertEquals(WeightProjection.Verdict.ON_TRACK, WeightProjection.pace(Goal.CUT, projectionAt(-0.08)).verdict)   // ≈ −0,7 %/týd
        assertEquals(WeightProjection.Verdict.TOO_FAST, WeightProjection.pace(Goal.CUT, projectionAt(-0.2)).verdict)
        assertEquals(WeightProjection.Verdict.WRONG_WAY, WeightProjection.pace(Goal.CUT, projectionAt(0.03)).verdict)
        assertEquals(WeightProjection.Direction.LOSING, WeightProjection.pace(Goal.CUT, projectionAt(-0.08)).direction)
    }

    @Test
    fun bulkAndMaintainVerdicts() {
        assertEquals(WeightProjection.Verdict.ON_TRACK, WeightProjection.pace(Goal.BULK, projectionAt(0.035)).verdict)  // ≈ +0,3 %/týd
        assertEquals(WeightProjection.Verdict.TOO_FAST, WeightProjection.pace(Goal.BULK, projectionAt(0.1)).verdict)
        assertEquals(WeightProjection.Verdict.ON_TRACK, WeightProjection.pace(Goal.MAINTAIN, projectionAt(0.0)).verdict)
        assertEquals(WeightProjection.Direction.STABLE, WeightProjection.pace(Goal.MAINTAIN, projectionAt(0.0)).direction)
    }

    @Test
    fun fewWeighInsWithoutEnergyAreUncertain() {
        val p = WeightProjection.project(mapOf(1000 to 80.0, 1001 to 80.4), 1001, null)!!
        assertEquals(WeightProjection.Verdict.UNCERTAIN, WeightProjection.pace(Goal.CUT, p).verdict)
    }
}
