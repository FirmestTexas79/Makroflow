package cz.uhk.macroflow.training.log

import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseInsightTest {

    private val press = ExerciseLibrary.byId("machine_chest_press")!!    // stroj, 6–8, při 10 přidat, +2,5 kg
    private val pullUp = ExerciseLibrary.byId("pull_up")!!

    private fun set(day: Int, w: Double, r: Int, ex: String = "machine_chest_press") =
        LoggedSet(day = day, exerciseId = ex, weightKg = w, reps = r)

    @Test
    fun noHistoryGivesRangeOnly() {
        val i = ExerciseInsight.of(press, emptyList(), 100)
        assertNull(i.estimate); assertNull(i.readyWeightKg)
        assertEquals(7, i.readyReps)           // střed 6–8
    }

    @Test
    fun readyWeightIsProgressiveOverload() {
        // minule 62,5 × 10 a 10 → přidat váhu (Epley na ~7 opakování, nejvýš +10 %)
        val sets = listOf(set(90, 60.0, 8), set(90, 60.0, 7), set(96, 62.5, 10), set(96, 62.5, 10))
        val i = ExerciseInsight.of(press, sets, 100)
        assertTrue(i.estimate != null)
        assertEquals(67.5, i.readyWeightKg!!, 1e-9)
        assertEquals(6, i.readyReps)
        assertEquals(ProgressiveOverload.Kind.ADD_WEIGHT, i.suggestion!!.kind)
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
        assertEquals(9, i.readyReps)           // 8 → o jedno víc
    }
}
