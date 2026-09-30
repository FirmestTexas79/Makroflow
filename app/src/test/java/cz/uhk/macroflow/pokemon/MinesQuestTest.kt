package cz.uhk.macroflow.pokemon

import cz.uhk.macroflow.pokemon.cave.MinesMap
import cz.uhk.macroflow.pokemon.quests.QuestProgression
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.QuestRewards
import cz.uhk.macroflow.pokemon.quests.RequirementType
import cz.uhk.macroflow.pokemon.skills.Gear
import cz.uhk.macroflow.pokemon.skills.Resource
import cz.uhk.macroflow.pokemon.skills.Skill
import cz.uhk.macroflow.pokemon.story.Insight
import cz.uhk.macroflow.pokemon.story.StoryProgress
import cz.uhk.macroflow.pokemon.story.Vendelin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Havíř Vendelín v Dolech (docs/adr/0050). */
class MinesQuestTest {
    private val q = QuestRegistry.MINES_QUEST

    @Test
    fun `quest učí chytat hmyz a pak zkouší všechny sběrné dovednosti`() {
        assertEquals(Vendelin.QUEST_ID, q.id)
        assertEquals(
            listOf(RequirementType.STORY_FLAG, RequirementType.DELIVER_ITEMS, RequirementType.HAVE_ITEM,
                RequirementType.DELIVER_ITEMS, RequirementType.AFK_MINUTES, RequirementType.STORY_FLAG),
            q.stages.map { it.requirementType })
        assertEquals(MinesMap.NET_TAKEN_KEY, q.stages[0].targetId)
        assertEquals(listOf(Resource.BUG_SPARK.itemId to 10), QuestProgression.deliveryItems(q.stages[1]))
        assertEquals(Gear.COPPER_NET.id, q.stages[2].targetId)
        assertEquals(listOf(Resource.BUG_CRYSTAL.itemId to 5), QuestProgression.deliveryItems(q.stages[3]))
        assertEquals(Vendelin.SIGN_STAGE, q.stages.size - 1)
        assertEquals(Vendelin.BOOK_SIGNED_KEY, q.stages[Vendelin.SIGN_STAGE].targetId)
        assertTrue(q.stages.all { it.speakerName == Vendelin.NAME })
        assertTrue(q.side && !q.secret)
    }

    @Test
    fun `zkouška chce tři hodiny kácení, těžby i chytání hmyzu`() {
        val test = q.stages[4]
        assertEquals(180, test.targetValue)
        val skills = QuestProgression.afkSkills(test)
        assertEquals(listOf(Skill.LOGGING, Skill.MINING, Skill.BUG_CATCHING).map { it.id }, skills)
        // postup = nejslabší dovednost
        assertEquals(40, QuestProgression.afkProgress(mapOf("logging" to 200, "mining" to 40, "bugcatching" to 190), test))
        assertEquals(0, QuestProgression.afkProgress(mapOf("logging" to 200), test))
        assertFalse(QuestProgression.isStageSatisfied(test, "179"))
        assertTrue(QuestProgression.isStageSatisfied(test, "180"))
        assertEquals("2 h 5 min", QuestProgression.clock(125))
        assertTrue(QuestProgression.reminder(test, "40").contains("40 min"))
    }

    @Test
    fun `odměna je kahan a příznaky se synchronizují`() {
        val r = QuestRewards.forStage(Vendelin.QUEST_ID, Vendelin.SIGN_STAGE)!!
        assertEquals(Vendelin.LAMP_ID, r.itemId)
        val lamp = Gear.from(Vendelin.LAMP_ID)!!
        for (s in listOf(Skill.MINING, Skill.LOGGING, Skill.BUG_CATCHING)) {
            assertEquals(0.10, lamp.xpBonus[s]!!, 1e-9)
            assertEquals(2, lamp.afkHours[s])
        }
        assertTrue(StoryProgress.isStoryKey(Vendelin.BOOK_SIGNED_KEY))
        assertTrue(StoryProgress.isStoryKey(MinesMap.NET_TAKEN_KEY))
        assertEquals(1, Insight.level(setOf(Vendelin.BOOK_SIGNED_KEY)))
    }

    @Test
    fun `Vendelín a kniha stojí v Dolech`() {
        val nodes = MinesMap.MAP.nodes.map { it.id }
        assertTrue(Vendelin.NODE in nodes && Vendelin.BOOK_NODE in nodes)
        assertTrue(Vendelin.NODE in MinesMap.ACTION_NODES && Vendelin.BOOK_NODE in MinesMap.ACTION_NODES)
        assertTrue(MinesMap.MAP.tapAreas.containsKey(Vendelin.NODE))
        // kniha: podpis je Okruh 213, stejně jako roztržený list č. 5
        assertTrue(Vendelin.bookText(true).contains("Okruh 213"))
        assertTrue(Insight.page(5)!!.text.contains("213"))
        assertFalse(Vendelin.bookText(false).contains("Tvou."))
    }

    @Test
    fun `deník řadí kapitoly podle příběhu, vedlejší a tajné na konec`() {
        val order = QuestRegistry.ALL.sortedBy { QuestRegistry.journalOrder(it.id) }.map { it.id }
        assertEquals(listOf("town_intro_oliver", "meadow_mastery", "mountains_macro_king", "forest_heart",
            Vendelin.QUEST_ID, "secret_grove"), order)
        assertTrue(QuestRegistry.SECRET_GROVE_QUEST.secret)
        assertEquals(Int.MAX_VALUE, QuestRegistry.journalOrder("neznamy"))
        assertTrue(QuestRegistry.ALL.all { it.chapter.isNotBlank() })
    }
}
