package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.pokemon.cave.SkyPass
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.RequirementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HunfreyTest {

    private fun advice(hasHeart: Boolean = false, placed: Boolean = false, done: Int = 9, forest: Hunfrey.ForestStage? = null, forestDone: Boolean = false) =
        Hunfrey.advice(hasHeart, placed, done, 5, forest, forestDone)

    @Test
    fun `rada podle postupu hrace`() {
        assertTrue(advice(placed = true).contains("otevřená"))
        assertTrue(advice(hasHeart = true).contains("Polož ho do lůžka"))
        assertTrue(advice(done = 2).contains("máš 2"))
        assertTrue(advice().contains("Mydruse"))
        val f = advice(forest = Hunfrey.ForestStage(3, "Dary lesa", "Přines bobule."))
        assertTrue(f.contains("„Dary lesa“") && f.contains("Přines bobule."))
        assertTrue(advice(forest = Hunfrey.ForestStage(1, "Voda", null)).contains("deníku"))
    }

    @Test
    fun `quest u brany ma tri faze a radu ve treti`() {
        val q = QuestRegistry.byId(Hunfrey.QUEST_ID)!!
        assertTrue(q.side)
        assertEquals(3, q.stages.size)
        assertEquals(SkyPass.CLAWS_NODE, q.stages[0].targetId)
        assertEquals(RequirementType.STORY_FLAG, q.stages[Hunfrey.RUNES_STAGE].requirementType)
        assertEquals(SkyPass.GATE_SEEN_KEY, q.stages[1].targetId)
        assertEquals(SkyPass.HEART_PLACED_KEY, q.stages[Hunfrey.ADVICE_STAGE].targetId)
        assertTrue(q.stages.all { it.speakerName == Hunfrey.NAME })
        // stojí na skutečném uzlu průsmyku, který jde naklepnout
        assertTrue(SkyPass.MAP.nodes.any { it.id == Hunfrey.NODE })
        assertTrue(Hunfrey.NODE in SkyPass.ACTION_NODES)
    }
}
