package cz.uhk.macroflow.training.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusclesTest {

    @Test
    fun pushLightsChestShouldersTriceps() {
        assertEquals(setOf(Muscle.CHEST, Muscle.FRONT_DELTS, Muscle.TRICEPS), TrainingMuscles.of("push").keys)
    }

    @Test
    fun pullLightsBackAndBiceps() {
        val m = TrainingMuscles.of("PULL")
        assertEquals(TrainingMuscles.PRIMARY, m[Muscle.LATS])
        assertEquals(TrainingMuscles.PRIMARY, m[Muscle.BICEPS])
        assertEquals(TrainingMuscles.SECONDARY, m[Muscle.LOWER_BACK])
    }

    @Test
    fun restIsEmpty() {
        assertTrue(TrainingMuscles.of("rest").isEmpty())
        assertTrue(TrainingMuscles.of(null).isEmpty())
        assertEquals("", TrainingMuscles.describe("rest"))
    }

    @Test
    fun pplTwiceHitsEverythingTwice() {
        val f = TrainingMuscles.weeklyFrequency(listOf("push", "pull", "legs", "push", "pull", "legs", "rest"))
        assertEquals(2.0, f[Muscle.CHEST]!!, 1e-9)
        assertEquals(2.0, f[Muscle.QUADS]!!, 1e-9)
        // Břicho jen vedlejší z legs: 2 × 0,5
        assertEquals(1.0, f[Muscle.ABS]!!, 1e-9)
        assertEquals(listOf(Muscle.ABS), TrainingMuscles.belowTarget(f))
    }

    @Test
    fun pplOnceFlagsMainMuscles() {
        val f = TrainingMuscles.weeklyFrequency(listOf("push", "pull", "legs", "rest", "rest", "rest", "rest"))
        assertTrue(TrainingMuscles.belowTarget(f).containsAll(listOf(Muscle.CHEST, Muscle.LATS, Muscle.QUADS)))
    }

    @Test
    fun emptyWeekHasNoHint() {
        assertTrue(TrainingMuscles.belowTarget(TrainingMuscles.weeklyFrequency(List(7) { "rest" })).isEmpty())
    }

    @Test
    fun describeSplitsMainAndSecondary() {
        assertEquals("Prsa, přední ramena, triceps", TrainingMuscles.describe("push"))
        assertEquals("Široký zádový, trapézy, zadní ramena, biceps · vedlejší: předloktí, spodní záda",
            TrainingMuscles.describe("pull"))
    }
}
