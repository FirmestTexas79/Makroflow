package cz.uhk.macroflow.pokemon

import cz.uhk.macroflow.pokemon.quests.QuestProgression
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.QuestRewards
import cz.uhk.macroflow.pokemon.quests.QuestStage
import cz.uhk.macroflow.pokemon.quests.RequirementType
import cz.uhk.macroflow.pokemon.skills.Resource
import cz.uhk.macroflow.pokemon.story.ForestHeart
import cz.uhk.macroflow.pokemon.story.StoryProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quest Hvozdu – druid Mydrus a Starý dub (docs/adr/0045). */
class ForestQuestTest {
    private val q = QuestRegistry.FOREST_QUEST

    @Test
    fun `quest vede od průzkumu přes vodu a suroviny k bossovi`() {
        assertEquals(ForestHeart.QUEST_ID, q.id)
        assertEquals(
            listOf(RequirementType.VISIT_NODE, RequirementType.HIT_WATER, RequirementType.BATTLE_BIOME,
                RequirementType.DELIVER_ITEMS, RequirementType.WALK_STEPS, RequirementType.STORY_FLAG),
            q.stages.map { it.requirementType })
        assertEquals(ForestHeart.BOSS_STAGE, q.stages.indexOfFirst { it.requirementType == RequirementType.STORY_FLAG })
        assertEquals(ForestHeart.ROT_DEFEATED_KEY, q.stages[ForestHeart.BOSS_STAGE].targetId)
        assertTrue(StoryProgress.isStoryKey(ForestHeart.ROT_DEFEATED_KEY))     // synchronizuje se
        assertEquals("FOREST", q.stages[2].targetId)
        // Mydrus mluví všude kromě poslední fáze, kde promluví Starý dub
        assertTrue(q.stages.dropLast(1).all { it.speakerName == "Mydrus" })
        assertEquals("Starý dub", q.stages.last().speakerName)
        assertEquals("Mydrus", q.farewellSpeakerName)
    }

    @Test
    fun `průzkum míří na skutečné uzly Hvozdu`() {
        val nodes = cz.uhk.macroflow.pokemon.cave.ForestMap.MAP.nodes.map { it.id }.toSet()
        q.stages[0].targetId!!.split(",").forEach { assertTrue(it, it in nodes) }
        assertTrue(ForestHeart.MYDRUS_NODE in nodes && ForestHeart.OAK_NODE in nodes)
    }

    @Test
    fun `odvar chce suroviny z celé hry a připomínka je vypíše`() {
        val stage = q.stages[3]
        val items = QuestProgression.deliveryItems(stage)
        assertEquals(listOf("berry_blue" to 5, "log_birch" to 5, "mat_leaf_dry" to 5), items)
        items.forEach { (id, _) -> assertTrue(id, Resource.from(id) != null) }
        val owned = mapOf("berry_blue" to 7, "log_birch" to 2)
        assertEquals(listOf("log_birch" to 3, "mat_leaf_dry" to 5), QuestProgression.missingItems(stage, owned))
        assertTrue(QuestProgression.missingItems(stage, mapOf("berry_blue" to 5, "log_birch" to 5, "mat_leaf_dry" to 9)).isEmpty())
        val text = QuestProgression.deliveryText(stage, owned)
        assertTrue(text, text.contains("5× Modrá bobule (máš 5)") && text.contains("5× Březové poleno (máš 2)"))
        assertTrue(QuestProgression.isStageSatisfied(stage, "1"))
        assertFalse(QuestProgression.isStageSatisfied(stage, "0"))
    }

    @Test
    fun `voda se počítá proti osobnímu cíli`() {
        assertEquals(50, QuestProgression.waterPercent(1250, 2500))
        assertEquals(100, QuestProgression.waterPercent(2500, 2500))
        assertEquals(0, QuestProgression.waterPercent(1000, 0))
        val water = q.stages[1]
        assertFalse(QuestProgression.isStageSatisfied(water, "99"))
        assertTrue(QuestProgression.isStageSatisfied(water, "130"))
        assertTrue(QuestProgression.reminder(water, "40").contains("40 %"))
    }

    @Test
    fun `boss má vlastní připomínku a výhry se počítají ve Hvozdu`() {
        assertEquals(q.stages.last().hint, QuestProgression.reminder(q.stages.last(), "0"))
        assertTrue(QuestProgression.reminder(q.stages[2], "1").contains("ve Hvozdu"))
    }

    @Test
    fun `Starý dub na konci vydá Srdce Hvozdu`() {
        val r = QuestRewards.forStage(q.id, q.stages.size - 1)!!
        assertEquals(ForestHeart.ITEM_ID, r.itemId)
        assertEquals(ForestHeart.LABEL, r.label)
    }

    @Test
    fun `debug splnění funguje pro každou fázi každého questu`() {
        QuestRegistry.ALL.forEach { quest ->
            quest.stages.forEach { s: QuestStage ->
                assertTrue("${quest.id}/${s.title}", QuestProgression.isStageSatisfied(s, QuestProgression.satisfyingMetadata(s)))
            }
        }
    }
}
