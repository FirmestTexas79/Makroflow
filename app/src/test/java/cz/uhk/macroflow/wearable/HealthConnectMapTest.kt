package cz.uhk.macroflow.wearable

import org.junit.Assert.*
import org.junit.Test

class HealthConnectMapTest {
    private val h = 3600_000L
    private fun st(type: Int, from: Double, to: Double) = HcStage(type, (from * h).toLong(), (to * h).toLong())

    @Test fun stagesAreSummedPerType() {
        val s = HcSleepSession(0, 8 * h, listOf(st(4, 0.0, 4.0), st(5, 4.0, 5.3), st(6, 5.3, 7.0), st(1, 7.0, 7.5), st(2, 7.5, 8.0)))
        val f = HealthConnectMap.sleep(s)
        assertEquals(4 * 3600, f.lightSec); assertEquals(4680, f.deepSec); assertEquals(6120, f.remSec)
        assertEquals(1800, f.awakeSec); assertEquals(1, f.awakeCount)
        assertEquals(f.lightSec!! + f.deepSec!! + f.remSec!! + 1800, f.sleepSec)
        assertNotNull(f.score)
    }

    @Test fun withoutStagesOnlyDurationIsKnown() {
        val f = HealthConnectMap.sleep(HcSleepSession(0, 7 * h, emptyList()))
        assertEquals(7 * 3600, f.sleepSec); assertNull(f.deepSec); assertNull(f.score)
    }

    @Test fun genericSleepingStageGivesNoFakeSplit() {
        val f = HealthConnectMap.sleep(HcSleepSession(0, 7 * h, listOf(st(2, 0.0, 7.0))))
        assertEquals(7 * 3600, f.sleepSec); assertNull(f.deepSec); assertNull(f.lightSec); assertNull(f.score)
    }

    @Test fun idealNightScoresHundredAndShortLightNightLow() {
        assertEquals(100, HealthConnectMap.score(28800, (28800 * 0.16).toInt(), (28800 * 0.20).toInt(), 0))
        val poor = HealthConnectMap.score(4 * 3600, 0, 0, 3600)!!
        assertTrue(poor < 40)
    }

    @Test fun longestSessionIsTheNightAndNapIsIgnored() {
        val night = HcSleepSession(0, 7 * h, emptyList()); val nap = HcSleepSession(10 * h, 11 * h, emptyList())
        assertSame(night, HealthConnectMap.nightOf(listOf(nap, night)))
        assertNull(HealthConnectMap.nightOf(listOf(nap)))
    }

    @Test fun wornDependsOnStepsAndKcalNotStress() {
        val d = WearableDayEntity(date = "2026-11-01", source = HealthConnectMap.SOURCE, steps = 6000, totalKcal = 2500)
        assertTrue(HealthConnectMap.worn(d)); assertTrue(GarminParser.isWorn(d))
        assertFalse(GarminParser.isWorn(d.copy(steps = 100)))
        assertFalse(GarminParser.isWorn(d.copy(totalKcal = null)))
        assertFalse(GarminParser.isWorn(d.copy(source = "garmin"))) // Garmin bez stresu = nenošeno
    }

    @Test fun summaryCountsOnlyHealthConnectRows() {
        val days = listOf(
            WearableDayEntity("2026-11-01", HealthConnectMap.SOURCE, steps = 5000, restingHr = 55, sleepSec = 25000),
            WearableDayEntity("2026-11-02", HealthConnectMap.SOURCE, steps = 0),
            WearableDayEntity("2026-11-03", "garmin", steps = 9000, sleepSec = 20000))
        val s = HealthConnectMap.summary(days, 2)
        assertEquals(1, s.nights); assertEquals(1, s.stepDays); assertEquals(1, s.restHrDays); assertEquals(2, s.workouts)
        assertTrue(s.any); assertFalse(HealthConnectMap.summary(emptyList(), 0).any)
    }
}
