package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.pokemon.BiomeRegistry
import cz.uhk.macroflow.pokemon.BiomeType
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.cave.ExitMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cesta zpátky: sedm luceren v sedmi lokacích Hlubiny, každá na uzlu své mapy a mimo východy; úkol počítá jejich příznaky. */
class LanternsTest {
    @Test fun sevenLanternsInSevenPlaces() {
        assertEquals(7, Lanterns.ALL.size)
        assertEquals(7, Lanterns.ALL.map { it.biome }.toSet().size)
        assertEquals(7, Lanterns.ALL.map { it.key }.toSet().size)
        for (l in Lanterns.ALL) {
            val cave = BiomeRegistry.definition(BiomeType.valueOf(l.biome))?.cave
            assertNotNull("${l.biome}/${l.node}", cave?.node(l.node))
            assertTrue("${l.biome}/${l.node} není východ", ExitMarkers.EXITS[l.biome].orEmpty().none { it.node == l.node })
            assertTrue(l.key, StoryProgress.isStoryKey(l.key))
        }
    }

    @Test fun questCountsLanternFlags() {
        val q = QuestRegistry.byId(Lanterns.QUEST_ID)!!
        assertEquals(Lanterns.ALL.size, q.stages[0].targetValue)
        assertEquals(Lanterns.KEYS_CSV, q.stages[0].targetId)
        assertEquals(Lanterns.FINAL_KEY, q.stages[Lanterns.FINAL_STAGE].targetId)
    }

    @Test fun cityLiftOpensAfterTheAbyssView() {
        assertTrue(!AbyssLift.open { false })
        assertTrue(AbyssLift.open { it == TowerStory.ABYSS_KEY })
        assertNotNull(cz.uhk.macroflow.pokemon.cave.TowerAbyss.MAP.node(cz.uhk.macroflow.pokemon.cave.TowerAbyss.LIFT_NODE))
    }
}
