package cz.uhk.macroflow.training.exercises

import cz.uhk.macroflow.training.body.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseLibraryTest {

    @Test
    fun everyMuscleHasAtLeastThreeExercises() {
        Muscle.entries.forEach { m ->
            assertTrue("málo cviků pro $m", ExerciseLibrary.forMuscle(m).size >= 3)
            assertTrue("žádný hlavní cvik pro $m", ExerciseLibrary.ALL.any { m in it.primary })
        }
    }

    @Test
    fun idsAreUniqueAndResolvable() {
        val ids = ExerciseLibrary.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertNotNull(ExerciseLibrary.byId(it)) }
    }

    @Test
    fun everyExerciseIsFullyDescribed() {
        ExerciseLibrary.ALL.forEach { e ->
            assertTrue(e.id, e.steps.size >= 3)
            assertTrue(e.id, e.primary.isNotEmpty())
            assertTrue(e.id, e.latinPrimary.isNotEmpty())
            assertTrue(e.id, e.latinPrimary.all { it.startsWith("m.") || it.startsWith("mm.") })
            assertTrue(e.id, e.tips.isNotEmpty() && e.mistakes.isNotEmpty())
            assertTrue(e.id, e.level in 1..3)
            assertTrue(e.id, e.primary.intersect(e.secondary).isEmpty())
        }
    }

    @Test
    fun forMuscleListsPrimaryBeforeSecondary() {
        Muscle.entries.forEach { m ->
            val list = ExerciseLibrary.forMuscle(m)
            val firstSecondary = list.indexOfFirst { m !in it.primary }
            if (firstSecondary >= 0) assertTrue(list.drop(firstSecondary).none { m in it.primary })
            assertTrue(list.all { it.works(m) })
            assertEquals(list.size, list.toSet().size)
        }
    }

    @Test
    fun anatomyCoversAllMuscles() {
        Muscle.entries.forEach { m ->
            assertTrue(MuscleAnatomy.LATIN[m].orEmpty().isNotEmpty())
            assertTrue(MuscleAnatomy.FUNCTION[m].orEmpty().isNotBlank())
        }
    }
}
