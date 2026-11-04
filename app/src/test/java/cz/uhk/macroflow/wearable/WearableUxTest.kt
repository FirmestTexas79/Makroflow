package cz.uhk.macroflow.wearable

import org.junit.Assert.*
import org.junit.Test

class WearableUxTest {
    private fun night(sec: Int, score: Int?, bb: Int? = null, active: Int? = null) =
        WearableDayEntity(date = "2026-11-03", sleepSec = sec, sleepScore = score, bbMax = bb, activeKcal = active)

    @Test fun badShortNightIsLow() {
        val p = RitualPrefill.predict(night(20700, 49, bb = 30), null, null, null)!!
        assertEquals(2, p.sleep); assertEquals(2, p.energy); assertEquals(4, p.hunger)
    }

    @Test fun shortSleepCapsHighScore() {
        assertEquals(3, RitualPrefill.sleepRating(night(5 * 3600 + 1800, 95)))
        assertEquals(2, RitualPrefill.sleepRating(night(4 * 3600, 95)))
    }

    @Test fun greatNightIsHigh() {
        val p = RitualPrefill.predict(night(8 * 3600, 92, bb = 85), null, null, null)!!
        assertEquals(5, p.sleep); assertEquals(5, p.energy)
    }

    @Test fun noSleepDataMeansNoPrediction() {
        assertNull(RitualPrefill.predict(null, null, null, null))
        assertNull(RitualPrefill.predict(WearableDayEntity(date = "x"), null, null, null))
    }

    @Test fun hungerRisesWithShortSleepDeficitAndBigBurn() {
        val t = night(5 * 3600, 50)
        val y = WearableDayEntity(date = "y", activeKcal = 1500)
        assertEquals(5, RitualPrefill.predict(t, y, 1000.0, -500.0)!!.hunger)
        assertEquals(2, RitualPrefill.predict(night(8 * 3600, 85), null, null, 500.0)!!.hunger)
    }

    @Test fun meanAbsError() {
        assertNull(RitualPrefill.meanAbsError(emptyList()))
        assertEquals(0.5, RitualPrefill.meanAbsError(listOf(listOf(3, 3, 3, 3, 3, 3), listOf(2, 2, 2, 3, 3, 3)))!!, 1e-9)
    }

    @Test fun groups() {
        assertEquals(SportGroup.STRENGTH, ActivityGroups.of("strength_training"))
        assertEquals(SportGroup.CARDIO, ActivityGroups.of("jump_rope"))
        assertEquals(SportGroup.WALK, ActivityGroups.of("walking"))
        assertEquals(SportGroup.OTHER, ActivityGroups.of("yoga"))
    }

    private fun act(type: String, sec: Int, kcal: Int) = WearableActivityEntity(1, "d", "d 10:00:00", type, "n", kcal, sec, null, null)

    @Test fun planNotes() {
        val lift = listOf(act("strength_training", 2800, 330))
        assertTrue(ActivityGroups.planNote("push", false, lift)!!.contains("splněn"))
        assertTrue(ActivityGroups.planNote("push", false, emptyList())!!.contains("zatím"))
        assertTrue(ActivityGroups.planNote("rest", false, lift)!!.contains("navíc"))
        assertNull(ActivityGroups.planNote("rest", false, emptyList()))
        val s = ActivityGroups.summarize(lift + act("jump_rope", 600, 100))
        assertEquals(listOf(SportGroup.STRENGTH, SportGroup.CARDIO), s.map { it.group })
        assertEquals(46, s[0].minutes)
    }
}