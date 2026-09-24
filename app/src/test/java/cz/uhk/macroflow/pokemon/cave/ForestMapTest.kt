package cz.uhk.macroflow.pokemon.cave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestMapTest {

    private val forest = ForestMap.MAP

    @Test
    fun wholeForestIsReachableAndHasNoCrystal() {
        assertEquals(forest.nodes.map { it.id }.toSet(), forest.reachableFrom(forest.exitNode))
        assertNull(forest.crystal); assertNull(forest.crystalNode)
        assertFalse(forest.isCave)
        assertEquals("MEADOW", forest.parentBiome)
    }

    @Test
    fun forestIsMoreTangledThanTheMine() {
        // bludiště: víc uzlů, aspoň jedna smyčka (hran ≥ uzlů) a několik slepých uliček
        assertTrue(forest.nodes.size > CaveMaps.MAZE.nodes.size)
        assertTrue(forest.edges.size >= forest.nodes.size)
        val deadEnds = forest.nodes.count { forest.neighbors(it.id).size == 1 }
        assertTrue("slepé uličky: $deadEnds", deadEnds >= 5)
    }

    @Test
    fun edgesAreStraightCorridors() {
        // palouky vedou jen vodorovně nebo svisle (generátor kreslí rovné kapsle)
        forest.edges.forEach { (a, b) ->
            val na = forest.node(a)!!; val nb = forest.node(b)!!
            assertTrue("$a-$b", na.x == nb.x || na.y == nb.y)
        }
    }

    @Test
    fun encounterSpotsAreDeadEndsOrSideBranches() {
        assertTrue(forest.encounterNodes.all { forest.node(it) != null })
        assertTrue(forest.encounterNodes.none { it == forest.exitNode })
    }

    @Test
    fun entryNeedsFiveCompletedTasks() {
        // dokončený quest (4 fáze) + rozpracovaný na fázi 1 = 5
        val quests = listOf(Triple(4, 3, true), Triple(5, 1, false))
        assertEquals(5, ForestMap.completedTasks(quests))
        assertTrue(ForestMap.canEnter(5))
        assertFalse(ForestMap.canEnter(4))
        assertEquals(0, ForestMap.completedTasks(emptyList()))
        assertEquals(3, ForestMap.completedTasks(listOf(Triple(3, 9, false))))   // index mimo rozsah se ořízne
    }
}
