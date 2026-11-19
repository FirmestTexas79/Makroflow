package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.pokemon.species.SpeciesRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MastersTest {
    @Test fun mastersGoInOrderAndNeedTrainings() {
        assertEquals(Masters.Status.NEED_TRAINING, Masters.status(0, 0, emptySet()))
        assertEquals(Masters.Status.OPEN, Masters.status(0, 1, emptySet()))
        assertEquals(Masters.Status.NEED_PREVIOUS, Masters.status(1, 7, emptySet()))
        assertEquals(Masters.Status.NEED_TRAINING, Masters.status(1, 1, setOf("rhenna")))
        assertEquals(Masters.Status.OPEN, Masters.status(4, 4, setOf("rhenna", "odran", "misk", "taddeo")))
        assertEquals(Masters.Status.BEATEN, Masters.status(2, 0, setOf("rhenna", "odran", "misk")))
        assertTrue(Masters.ALL.all { it.need <= Masters.MAX_NEED })
    }

    @Test fun teamsUsePlayableSpeciesAndScaleWithPlayer() {
        Masters.ALL.forEach { m -> m.team.forEach { id ->
            val sp = SpeciesRegistry.byId(id)
            assertTrue("${m.key}:$id", sp != null && sp.guardianOf == null)
        } }
        val t = Masters.trainer(Masters.ALL[1], listOf(20, 20), day = 1)
        assertEquals(3, t.team.size)
        assertTrue(t.team.all { it.level == 20 })
        assertEquals(2, Masters.trainer(Masters.ALL[0], emptyList(), day = 1).team.size)
    }

    @Test fun trainerIdRoundTripAndStoryKeys() {
        assertEquals("misk", Masters.keyOfTrainer(Masters.trainerId("misk")))
        assertNull(Masters.keyOfTrainer("ai:bencpres"))
        assertTrue(Masters.KEYS.all { StoryProgress.isStoryKey(it) })
    }
}
