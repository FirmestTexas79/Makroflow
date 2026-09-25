package cz.uhk.macroflow.training.atlas

import cz.uhk.macroflow.training.body.Muscle
import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtlasFormatTest {

    @Test
    fun frequencyUsesCzechDecimalComma() {
        assertEquals("0× týdně", AtlasFormat.frequency(0.0))
        assertEquals("2× týdně", AtlasFormat.frequency(2.0))
        assertEquals("1,5× týdně", AtlasFormat.frequency(1.5))
        assertEquals("1× týdně", AtlasFormat.frequency(0.9))   // zaokrouhlení na půlky
    }

    @Test
    fun noteReflectsTarget() {
        assertTrue(AtlasFormat.frequencyNote(Muscle.CHEST, 0.0).contains("chybí"))
        assertTrue(AtlasFormat.frequencyNote(Muscle.CHEST, 1.0).contains("Pod"))
        assertTrue(AtlasFormat.frequencyNote(Muscle.CHEST, 2.5).contains("Splňuje"))
        assertTrue(AtlasFormat.frequencyNote(Muscle.FOREARMS, 0.5).contains("jiných"))
    }

    @Test
    fun intensitiesAreCapped() {
        val i = AtlasFormat.intensities(mapOf(Muscle.CHEST to 3.0, Muscle.LATS to 1.0))
        assertEquals(1.0, i[Muscle.CHEST]!!, 1e-9)
        assertEquals(0.5, i[Muscle.LATS]!!, 1e-9)
    }

    @Test
    fun exerciseLightsPrimaryFully() {
        val bench = ExerciseLibrary.byId("bench_press")!!
        val i = AtlasFormat.exerciseIntensities(bench)
        assertEquals(1.0, i[Muscle.CHEST]!!, 1e-9)
        assertEquals(0.5, i[Muscle.TRICEPS]!!, 1e-9)
        assertEquals(null, i[Muscle.QUADS])
    }
}
