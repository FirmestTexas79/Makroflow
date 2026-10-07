package cz.uhk.macroflow.training

import cz.uhk.macroflow.training.body.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickWorkoutTest {
    @Test fun `navrh treninku podle vybranych partii`() {
        assertNull(QuickWorkout.suggest(emptySet()))
        assertEquals("PUSH", QuickWorkout.suggest(setOf(Muscle.CHEST)))
        assertEquals("PULL", QuickWorkout.suggest(setOf(Muscle.LATS, Muscle.BICEPS)))
        assertEquals(QuickWorkout.OTHER, QuickWorkout.suggest(setOf(Muscle.LATS, Muscle.BICEPS, Muscle.TRICEPS)))
        assertEquals(QuickWorkout.OTHER, QuickWorkout.suggest(setOf(Muscle.CHEST, Muscle.LATS, Muscle.QUADS)))
        assertEquals("LEGS", QuickWorkout.suggest(setOf(Muscle.QUADS)))
        assertEquals(QuickWorkout.OTHER, QuickWorkout.suggest(setOf(Muscle.OBLIQUES)))
        assertEquals(setOf(Muscle.CHEST, Muscle.FRONT_DELTS, Muscle.TRICEPS), QuickWorkout.musclesOf("PUSH"))
    }

    @Test fun `cviky na vybrane partie`() {
        val sel = listOf(Muscle.CHEST, Muscle.LATS, Muscle.QUADS)
        val ids = QuickWorkout.exercisesFor(sel)
        assertEquals(6, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        sel.forEach { m -> assert(ids.count { m in cz.uhk.macroflow.training.exercises.ExerciseLibrary.byId(it)!!.primary } >= 2) { m } }
        assert(QuickWorkout.exercisesFor(emptyList()).isEmpty())
    }
}
