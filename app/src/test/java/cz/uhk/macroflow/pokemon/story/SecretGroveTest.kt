package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.pokemon.cave.ForestMap
import cz.uhk.macroflow.pokemon.cave.GroveMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretGroveTest {
    private val grove = GroveMap.MAP

    @Test fun groveOpensOnlyAfterTheRotIsGone() {
        assertTrue(!SecretGrove.canEnter(rotDefeated = false))
        assertTrue(SecretGrove.canEnter(rotDefeated = true))
        assertTrue(SecretGrove.thornText(false) != SecretGrove.thornText(true))
    }

    @Test fun nightIsBetweenNineAndFive() {
        listOf(21, 22, 23, 0, 1, 4).forEach { assertTrue("$it", SecretGrove.isNight(it)) }
        listOf(5, 6, 12, 18, 20).forEach { assertTrue("$it", !SecretGrove.isNight(it)) }
    }

    @Test fun hiddenPathIsASecretBranchOfTheForest() {
        val f = ForestMap.MAP
        val n = f.node(SecretGrove.FOREST_NODE)!!
        assertEquals(SecretGrove.FOREST_X, n.x); assertEquals(SecretGrove.FOREST_Y, n.y)
        assertTrue(SecretGrove.FOREST_NODE in f.reachableFrom(f.exitNode))
        assertTrue(SecretGrove.FOREST_NODE !in f.encounterNodes)
        // klepnutí na trní (modré houby) otevře mezeru, ne jezírko ani jiné místo
        SecretGrove.FOREST_MUSHROOMS.take(2).forEach { (x, y) ->
            assertEquals(SecretGrove.FOREST_NODE, f.tapAreaAt(x.toFloat(), y.toFloat()))
        }
    }

    @Test fun groveLeadsBackIntoTheForestAtTheThorns() {
        assertEquals(grove.nodes.map { it.id }.toSet(), grove.reachableFrom(grove.exitNode))
        assertEquals("FOREST", grove.parentBiome)
        assertEquals(SecretGrove.FOREST_NODE, grove.mountainNode)
        assertTrue(grove.encounterNodes.isEmpty())
        GroveMap.ACTION_NODES.forEach { assertTrue(it, grove.tapAreas.containsKey(it) && grove.node(it) != null) }
        assertEquals(SecretGrove.ALTAR_NODE, grove.tapAreaAt(80f, 84f))
        assertEquals(SecretGrove.GRAVE_NODE, grove.tapAreaAt(124f, 94f))
    }

    @Test fun threeMuralsTellTheStory() {
        assertEquals(SecretGrove.MURAL_NODES, SecretGrove.MURALS.map { it.node })
        SecretGrove.MURALS.forEach { assertTrue(it.node, it.text.length > 80 && it.drawable == it.node) }
        assertEquals(null, SecretGrove.mural("nic"))
    }

    @Test fun storyKeysSyncAndDiaryHasPages() {
        SecretGrove.KEYS.forEach { assertTrue(it, StoryProgress.isStoryKey(it)) }
        assertEquals(5, SecretGrove.DIARY_PAGES.size)
        assertTrue(SecretGrove.DIARY_PAGES.any { it.second.contains("POPEL ČEKÁ ZA MRAKY") })
        val icon = SecretGrove.diaryIcon()
        assertEquals(SecretGrove.ICON * SecretGrove.ICON, icon.size)
        assertEquals(0, icon[0])
        assertTrue(icon.any { it == 0xFF6AF0E0.toInt() })
    }

    @Test fun ghostStandsInTheGrove() {
        val (x, y) = SecretGrove.GHOST_POS
        assertTrue(x in 0 until grove.artW && y in 0 until grove.artH)
    }
}
