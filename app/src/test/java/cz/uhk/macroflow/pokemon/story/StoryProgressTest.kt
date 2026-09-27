package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.pokemon.cave.SkyPass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryProgressTest {

    @Test fun storyKeysCoverLegendAndSkyPass() {
        listOf("boss_defeated_BLUE", "boss_defeated_RED", "crystal_BLUE", "crystal_RED", "crystals_placed",
            "legend_faced", SkyPass.VISITED_KEY, SkyPass.GATE_SEEN_KEY, SkyPass.HEART_PLACED_KEY)
            .forEach { assertTrue(it, StoryProgress.isStoryKey(it)) }
        listOf("LAST_BIOME", "DEBUG_FORCE_SHINY", "FORCE_ENCOUNTER_ID", "SPECIAL_BATTLE")
            .forEach { assertTrue(it, !StoryProgress.isStoryKey(it)) }
    }

    @Test fun itemIdsRoundTrip() {
        assertEquals("story_legend_faced", StoryProgress.itemId("legend_faced"))
        assertEquals("legend_faced", StoryProgress.keyOf("story_legend_faced"))
        assertNull(StoryProgress.keyOf("crystal_blue"))          // krystal v batohu není příznak
        assertNull(StoryProgress.keyOf("story_neco_ciziho"))     // neznámý klíč se nepřebírá
    }

    @Test fun reinstallRestoresProgressFromCloud() {
        // nový telefon: GamePrefs prázdné, v cloudu je postup → vše zpět do GamePrefs
        val cloud = setOf("boss_defeated_BLUE", "crystal_BLUE", "legend_faced", SkyPass.VISITED_KEY)
        val m = StoryProgress.merge(prefs = emptySet(), items = cloud)
        assertEquals(cloud, m.toPrefs)
        assertTrue(m.toItems.isEmpty())
    }

    @Test fun oldProgressIsBackfilledToCloud() {
        // hráč získal postup ještě před synchronizací → nahraje se do předmětů
        val local = setOf("boss_defeated_RED", "crystals_placed", "legend_faced")
        val m = StoryProgress.merge(prefs = local, items = emptySet())
        assertEquals(local, m.toItems)
        assertTrue(m.toPrefs.isEmpty())
    }

    @Test fun mergeIsAUnionAndIdempotent() {
        val local = setOf("boss_defeated_RED", SkyPass.GATE_SEEN_KEY)
        val cloud = setOf("boss_defeated_BLUE", SkyPass.GATE_SEEN_KEY)
        val m = StoryProgress.merge(local, cloud)
        assertEquals(setOf("boss_defeated_BLUE"), m.toPrefs)
        assertEquals(setOf("boss_defeated_RED"), m.toItems)
        val again = StoryProgress.merge(local + m.toPrefs, cloud + m.toItems)
        assertTrue(again.toPrefs.isEmpty() && again.toItems.isEmpty())
    }

    @Test fun foreignKeysAreIgnored() {
        val m = StoryProgress.merge(setOf("LAST_BIOME"), setOf("neco"))
        assertTrue(m.toPrefs.isEmpty() && m.toItems.isEmpty())
    }
}
