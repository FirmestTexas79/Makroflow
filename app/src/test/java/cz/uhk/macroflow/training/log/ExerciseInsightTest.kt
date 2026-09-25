package cz.uhk.macroflow.training.log

import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseInsightTest {

    private val press = ExerciseLibrary.byId("machine_chest_press")!!    // stroj 10–15, +2,5 kg
    private val pullUp = ExerciseLibrary.byId("pull_up")!!

    private fun set(day: Int, w: Double, r: Int, ex: String = "machine_chest_press") =
        LoggedSet(day = day, exerciseId = ex, weightKg = w, reps = r)

    @Test
    fun noHistoryGivesRangeOnly() {
        val i = ExerciseInsight.of(press, emptyList(), 100)
        assertNull(i.estimate); assertNull(i.readyWeightKg)
        assertEquals(12, i.readyReps)          // střed 10–15
    }

    @Test
    fun readyWeightFollowsModelAndCap() {
        val sets = listOf(set(90, 60.0, 12), set(90, 60.0, 11), set(93, 60.0, 13), set(93, 60.0, 12), set(96, 62.5, 12))
        val i = ExerciseInsight.of(press, sets, 100)
        assertTrue(i.estimate != null)
        val w = i.readyWeightKg!!
        assertTrue("ready $w", w in 55.0..67.5)            // nejvýš poslední 62,5 + 2 × 2,5
        assertEquals(0.0, (w / 2.5) % 1.0, 1e-9)          // na kotouče
        assertEquals(13, i.readyReps)                      // minule 12 v rozsahu → +1
        assertTrue(i.nextEstimate!!.day > 100)
    }

    @Test
    fun todaysSetsDoNotChangeTodaysReadiness() {
        val before = listOf(set(90, 60.0, 12), set(93, 60.0, 12))
        val a = ExerciseInsight.of(press, before, 100)
        val b = ExerciseInsight.of(press, before + set(100, 80.0, 12), 100)
        assertEquals(a.readyWeightKg, b.readyWeightKg)
    }

    @Test
    fun bodyweightHasNoWeight() {
        val i = ExerciseInsight.of(pullUp, listOf(set(90, 0.0, 8, "pull_up")), 100)
        assertNull(i.readyWeightKg)
        assertEquals(9, i.readyReps)
    }
}
