package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.pokemon.BiomeRegistry
import cz.uhk.macroflow.pokemon.BiomeType
import cz.uhk.macroflow.pokemon.cave.ExitMarkers
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sběratelské předměty zón 1, 2 a Věže: šest na sadu v šesti místech, na uzlech svých map, nekříží se s jinými předměty. */
class CollectiblesTest {
    @Test fun everyItemLiesOnAFreeNodeOfItsMap() {
        val pages = Insight.PAGES.map { it.biome to it.node }.toSet()
        val lanterns = Lanterns.ALL.map { it.biome to it.node }.toSet()
        for (i in Collectibles.ITEMS) {
            val def = BiomeRegistry.definition(BiomeType.valueOf(i.biome))!!
            val onMap = def.cave?.node(i.node) != null || def.cave == null && def.graph.any { it.id == i.node }
            assertTrue("${i.biome}/${i.node} je uzel", onMap)
            assertTrue("${i.biome}/${i.node} není východ", ExitMarkers.EXITS[i.biome].orEmpty().none { it.node == i.node })
            assertTrue("${i.biome}/${i.node} není stopa Věže", i.node !in TowerStory.TAP_NODES)
            assertTrue("${i.biome}/${i.node} není list ani lucerna", (i.biome to i.node) !in pages && (i.biome to i.node) !in lanterns)
            assertTrue(i.key, StoryProgress.isStoryKey(i.key))
        }
        assertEquals(Collectibles.ITEMS.size, Collectibles.ITEMS.map { it.key }.toSet().size)
        assertEquals(Collectibles.ITEMS.size, Collectibles.ITEMS.map { it.biome to it.node }.toSet().size)
    }

    @Test fun questsCountTheirItems() {
        for (set in Collectibles.SETS) {
            assertEquals(6, set.items.size)
            val q = QuestRegistry.byId(set.questId)!!
            assertTrue(q.side)
            assertEquals(set.items.size, q.stages[0].targetValue)
            assertEquals(set.keysCsv, q.stages[0].targetId)
        }
        assertEquals(Collectibles.LETTERS_FINAL_KEY, QuestRegistry.byId(Collectibles.LETTERS)!!.stages[Collectibles.LETTERS_FINAL_STAGE].targetId)
    }
}
