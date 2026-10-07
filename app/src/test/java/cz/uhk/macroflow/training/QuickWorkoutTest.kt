package cz.uhk.macroflow.training

import cz.uhk.macroflow.training.body.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickWorkoutTest {
    @Test fun `navrh treninku podle vybranych partii`() {
        assertNull(QuickWorkout.suggest(emptySet()))
        assertEquals("PUSH", QuickWorkout.suggest(setOf(Muscle.CHEST)))
        assertEquals("PULL", QuickWorkout.suggest(setOf(Muscle.LATS, Muscle.BICEPS, Muscle.TRICEPS)))
        assertEquals("LEGS", QuickWorkout.suggest(setOf(Muscle.QUADS)))
        assertEquals(QuickWorkout.OTHER, QuickWorkout.suggest(setOf(Muscle.OBLIQUES)))
        assertEquals(setOf(Muscle.CHEST, Muscle.FRONT_DELTS, Muscle.TRICEPS), QuickWorkout.musclesOf("PUSH"))
    }
}
