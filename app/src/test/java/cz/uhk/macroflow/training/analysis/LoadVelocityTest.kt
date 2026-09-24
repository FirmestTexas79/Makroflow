package cz.uhk.macroflow.training.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Syntetický dřepař: e1RM 150 kg, MVT 0,30 m/s, sklon −0,006 m/s na kg
 * → v(zátěž) = 1,20 − 0,006·zátěž.
 */
class LoadVelocityTest {

    private fun v(load: Double) = 1.20 - 0.006 * load

    @Test
    fun recoversE1rmFromThreeLoads() {
        val p = LoadVelocity.fit(Lift.SQUAT, listOf(LvPoint(60.0, v(60.0)), LvPoint(100.0, v(100.0)), LvPoint(120.0, v(120.0))))
        assertNotNull(p)
        assertEquals(150.0, p!!.e1rmKg, 0.01)
        assertEquals(Confidence.HIGH, p.confidence)
        assertFalse(p.fromHistory)
    }

    @Test
    fun noisyPointsStillCloseAndRegressionUsesAllSets() {
        val pts = listOf(LvPoint(60.0, v(60.0) + 0.02), LvPoint(80.0, v(80.0) - 0.02),
            LvPoint(100.0, v(100.0) + 0.01), LvPoint(120.0, v(120.0) - 0.01), LvPoint(120.0, v(120.0) + 0.005))
        val p = LoadVelocity.fit(Lift.SQUAT, pts)!!
        assertEquals(150.0, p.e1rmKg, 6.0)
        assertTrue(p.r2 > 0.95)
    }

    @Test
    fun singleLoadWithoutHistoryGivesNothing() {
        assertNull(LoadVelocity.fit(Lift.SQUAT, listOf(LvPoint(100.0, v(100.0)), LvPoint(100.0, v(100.0) - 0.03))))
    }

    @Test
    fun loadsTooCloseAreRejected() {
        // 100 vs 110 kg = 9 % rozdíl → sklon by byl šum
        assertNull(LoadVelocity.strictFit(Lift.SQUAT, listOf(LvPoint(100.0, v(100.0)), LvPoint(110.0, v(110.0)))))
    }

    @Test
    fun poorTrackingQualityIsIgnored() {
        val p = LoadVelocity.strictFit(Lift.SQUAT, listOf(LvPoint(60.0, v(60.0)), LvPoint(100.0, v(100.0)),
            LvPoint(120.0, 0.9, quality = 0.3)))!!
        assertEquals(2, p.distinctLoads)
        assertEquals(150.0, p.e1rmKg, 0.01)
    }

    @Test
    fun historySlopeAnchorsSingleLoadDay() {
        val slopes = LoadVelocity.historicalSlopes(Lift.SQUAT, listOf(
            listOf(LvPoint(60.0, v(60.0)), LvPoint(100.0, v(100.0)), LvPoint(120.0, v(120.0))),
            listOf(LvPoint(70.0, v(70.0)), LvPoint(110.0, v(110.0)), LvPoint(125.0, v(125.0)))
        ))
        assertEquals(2, slopes.size)
        // Dnes je forma horší: stejná zátěž jde o 0,06 m/s pomaleji → maximum o 10 kg níž
        val p = LoadVelocity.fit(Lift.SQUAT, listOf(LvPoint(110.0, v(110.0) - 0.06)), slopes)!!
        assertTrue(p.fromHistory)
        assertEquals(140.0, p.e1rmKg, 0.01)
        assertEquals(Confidence.MEDIUM, p.confidence)
    }

    @Test
    fun e1rmNeverBelowHeaviestLiftedLoad() {
        // Nejtěžší série šla pod MVT (grind) → maximum aspoň tahle zátěž
        val p = LoadVelocity.strictFit(Lift.BENCH, listOf(LvPoint(60.0, 0.55), LvPoint(100.0, 0.12)))!!
        assertEquals(100.0, p.e1rmKg, 1e-9)
    }

    @Test
    fun roundsToPlates() {
        assertEquals(112.5, LoadVelocity.roundToPlates(113.4), 1e-9)
        assertEquals(115.0, LoadVelocity.roundToPlates(113.8), 1e-9)
    }

    // ── Ztráta rychlosti ──

    private fun rep(i: Int, mcv: Double, rom: Double = 50.0) = RepMetrics(i, 50.0, 0.0, 50.0, rom, 1500, 800, 0, 2300, mcv, mcv * 1.3, 2.0, 1.0)

    private fun set(vararg reps: RepMetrics): SetSummary {
        val best = reps.maxOf { it.meanConcentricVelocity }
        val last = reps.last().meanConcentricVelocity
        return SetSummary(Lift.SQUAT, reps.toList(), 0.375, 50.0, 50.0, 0.0, 1500, 800, 2300, best, last,
            (best - last) / best * 100, 2.0, 1.0)
    }

    @Test
    fun stopFiresAtGoalThreshold() {
        val s = set(rep(1, 0.60), rep(2, 0.58), rep(3, 0.52), rep(4, 0.47))
        assertEquals(21.7, VelocityLoss.lossPct(s, live = true)!!, 0.1)
        assertTrue(VelocityLoss.shouldStop(s, VbtGoal.STRENGTH))
        assertFalse(VelocityLoss.shouldStop(s, VbtGoal.HYPERTROPHY))
        assertFalse(VelocityLoss.shouldStop(s, VbtGoal.FREE))
    }

    @Test
    fun liveIgnoresUnfinishedLastRep() {
        // Poslední rep je teprve v půlce zvedání: malý rozsah a nízká rychlost – nesmí spustit STOP
        val s = set(rep(1, 0.60), rep(2, 0.58), rep(3, 0.30, rom = 25.0))
        assertEquals(3.3, VelocityLoss.lossPct(s, live = true)!!, 0.1)
        assertFalse(VelocityLoss.shouldStop(s, VbtGoal.STRENGTH))
        // Po dokončení série se počítají všechny repy
        assertEquals(50.0, VelocityLoss.lossPct(s, live = false)!!, 0.1)
    }

    @Test
    fun singleRepHasNoLoss() {
        assertNull(VelocityLoss.lossPct(set(rep(1, 0.6)), live = false))
    }

    // ── Doporučení ──

    @Test
    fun suggestsLoadForGoal() {
        val p = LoadVelocity.fit(Lift.SQUAT, listOf(LvPoint(60.0, v(60.0)), LvPoint(100.0, v(100.0)), LvPoint(120.0, v(120.0))))
        val a = Autoregulation.advise(VbtGoal.STRENGTH, p, set(rep(1, v(100.0)), rep(2, v(100.0) - 0.05)), 100.0)
        // 82,5 % ze 150 = 123,75 → 125 kg (zaokrouhleno na 2,5)
        assertEquals(125.0, a.suggestedLoadKg!!, 1e-9)
        assertEquals(v(125.0), a.targetFirstRepMcv!!, 1e-9)
        assertTrue(a.lines.any { it.contains("150") })
    }

    @Test
    fun asksForLoadWhenMissing() {
        val a = Autoregulation.advise(VbtGoal.STRENGTH, null, set(rep(1, 0.6)), null)
        assertNull(a.suggestedLoadKg)
        assertTrue(a.lines.first().startsWith("Zadej zátěž"))
    }
}
