package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.RequirementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kořeny, které pijí: voda a jídlo podle vlastního cíle, pak jména – a nic neprozradí, že Makromoni byli lidé. */
class SlopeWaterTest {
    @Test fun questRewardsDrinkingAndEating() {
        val q = QuestRegistry.byId(SlopeWater.QUEST_ID)!!
        assertTrue(q.side)
        assertEquals(listOf(RequirementType.WATER_DAYS, RequirementType.MEAL_STREAK, RequirementType.STORY_FLAG), q.stages.map { it.requirementType })
        assertEquals(SlopeWater.NAMES_KEY, q.stages[SlopeWater.NAMES_STAGE].targetId)
        SlopeWater.KEYS.forEach { assertTrue(it, StoryProgress.isStoryKey(it)) }
    }

    @Test fun rollCallListsTheNine() {
        val t = SlopeWater.opened()
        assertEquals(9, SlopeWater.V7.size)
        SlopeWater.V7.forEach { assertTrue(it, t.contains(it)) }
        assertFalse(t.contains("§"))
        val all = listOf(SlopeWater.INTRO, SlopeWater.RISEN, t, SlopeWater.READ, SlopeWater.FAREWELL, SlopeWater.WATER_DONE, SlopeWater.V7_DONE)
        all.forEach { assertFalse(it, it.contains("Lumenc") || it.contains("byli lidé")) }
    }

    @Test fun placesChangeOnlyWhenDone() {
        assertNull(SlopeWater.textFor(SlopeWater.WATER_NODE, false))
        assertEquals(SlopeWater.WATER_DONE, SlopeWater.textFor(SlopeWater.WATER_NODE, true))
        assertNull(SlopeWater.textFor("sb_kolik", true))
    }
}
