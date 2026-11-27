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

    @Test fun `pauza mezi seriemi`() {
        val t = 1_000_000_000L
        assertNull(QuickWorkout.restLeft(0, t))
        assertEquals(QuickWorkout.REST_MS, QuickWorkout.restLeft(t, t))
        assertEquals(0L, QuickWorkout.restLeft(t, t + QuickWorkout.REST_MS))
        assertEquals(30_000L, QuickWorkout.restLeft(t, t + QuickWorkout.REST_MS, extraMs = 30_000))
        assertNull(QuickWorkout.restLeft(t, t + QuickWorkout.ACTIVE_WINDOW_MS + 1))     // trénink skončil
        assertNull(QuickWorkout.restLeft(t, t + 1000, endedAt = t + 500))                  // Hotovo po sérii
        assertEquals(QuickWorkout.REST_MS - 1000, QuickWorkout.restLeft(t, t + 1000, endedAt = t - 1))  // nová série po ukončení
    }

    @Test fun `rychly trening pro energeticky model`() {
        val legs = QuickWorkout.Entry("LEGS", 'A', "18:00", startedAt = 0, finishedAt = 0)
        assertEquals(cz.uhk.macroflow.energy.StrengthKind.LEGS, QuickWorkout.strengthForModel(legs)!!.kind)
        assertNull(QuickWorkout.strengthForModel(legs.copy(kind = QuickWorkout.CARDIO)))
        val done = legs.copy(startedAt = 1_000_000, finishedAt = 1_000_000 + 45 * 60_000)
        assertEquals(45.0, QuickWorkout.strengthForModel(done)!!.minutes, 0.01)
        assertEquals(cz.uhk.macroflow.energy.StrengthKind.PUSH, QuickWorkout.strengthForModel(legs.copy(kind = QuickWorkout.OTHER))!!.kind)
        assertNull(QuickWorkout.strengthForModel(null))
        val run = QuickWorkout.Entry(QuickWorkout.CARDIO, 'R', "07:00", startedAt = 0, finishedAt = 0)
        assertEquals(cz.uhk.macroflow.energy.Exercise.Met(8.0, 30.0), QuickWorkout.cardioForModel(run))
        assertEquals(6.0, (QuickWorkout.cardioForModel(run.copy(variant = null)) as cz.uhk.macroflow.energy.Exercise.Met).met, 0.0)
        assertNull(QuickWorkout.cardioForModel(legs))
        assertEquals("Kardio · Běh", run.label)
    }
}
