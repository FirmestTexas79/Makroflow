package cz.uhk.macroflow.pokemon

import cz.uhk.macroflow.pokemon.quests.NutritionTotals
import cz.uhk.macroflow.pokemon.quests.QuestDefinition
import cz.uhk.macroflow.pokemon.quests.QuestProgression
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.QuestStage
import cz.uhk.macroflow.pokemon.quests.RequirementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestProgressionTest {

    private fun stage(type: RequirementType, target: Int, targetId: String? = null) =
        QuestStage("t", "x", 0, type, target, targetId)

    private val quest = QuestDefinition(
        "test_quest",
        listOf(
            stage(RequirementType.VISIT_NODE, 3, "a,b,c"),
            stage(RequirementType.SCAN_BARCODE, 1)
        )
    )

    @Test
    fun `nova fáze s metadaty 0 nezapočítá 0 jako navštívený uzel`() {
        // Regrese: dřív split(",") na "0" vracel ["0"] → hráči stačily 2 budovy ze 3.
        assertEquals(0, QuestProgression.visitedNodes("0").size)
        assertEquals(setOf("a", "b"), QuestProgression.visitedNodes("0,a,b"))
    }

    @Test
    fun `visit node se splní až po všech cílech`() {
        val p = QuestProgressEntity(questId = quest.id, metadata = "0")
        val r1 = QuestProgression.apply(p, quest, "a,b", now = 10)
        assertFalse(r1.stageCompleted)
        assertEquals(0, r1.progress.currentStageIndex)

        val r2 = QuestProgression.apply(r1.progress, quest, "a,b,c", now = 20)
        assertTrue(r2.stageCompleted)
        assertEquals(1, r2.progress.currentStageIndex)
        assertEquals("0", r2.progress.metadata)
    }

    @Test
    fun `posun fáze nastaví stageStartedAt – od té doby se počítají skeny`() {
        val p = QuestProgressEntity(questId = quest.id, metadata = "0", stageStartedAt = 1)
        val r = QuestProgression.apply(p, quest, "a,b,c", now = 5_000)
        assertEquals(5_000, r.progress.stageStartedAt)
    }

    @Test
    fun `nesplněná fáze stageStartedAt nemění`() {
        val p = QuestProgressEntity(questId = quest.id, metadata = "0", stageStartedAt = 42)
        val r = QuestProgression.apply(p, quest, "a", now = 5_000)
        assertEquals(42, r.progress.stageStartedAt)
    }

    @Test
    fun `poslední fáze označí quest jako dokončený`() {
        val p = QuestProgressEntity(questId = quest.id, currentStageIndex = 1, metadata = "0")
        val r = QuestProgression.apply(p, quest, "1", now = 1)
        assertTrue(r.stageCompleted)
        assertTrue(r.progress.isCompleted)
    }

    @Test
    fun `dokončený quest se už nemění`() {
        val p = QuestProgressEntity(questId = quest.id, currentStageIndex = 2, isCompleted = true, metadata = "0")
        val r = QuestProgression.apply(p, quest, "99", now = 1)
        assertFalse(r.stageCompleted)
        assertEquals(p, r.progress)
    }

    @Test
    fun `kroky se vyhodnocují jako absolutní denní hodnota`() {
        val s = stage(RequirementType.WALK_STEPS, 5000)
        assertFalse(QuestProgression.isStageSatisfied(s, "4999"))
        assertTrue(QuestProgression.isStageSatisfied(s, "5000"))
    }

    @Test
    fun `registry najde všechny questy podle id`() {
        QuestRegistry.ALL.forEach { assertEquals(it, QuestRegistry.byId(it.id)) }
        assertEquals(null, QuestRegistry.byId("neexistuje"))
    }

    @Test
    fun `meadow quest končí skenem kódu`() {
        assertEquals(RequirementType.SCAN_BARCODE, QuestRegistry.MEADOW_QUEST.stages.last().requirementType)
    }

    private val totals = NutritionTotals(kcal = 1620, proteinG = 79.9f, carbsG = 210.4f, fatG = 51f)

    @Test
    fun `kalorie a makra se berou z dnešních součtů`() {
        assertEquals(1620, QuestProgression.nutritionValue(stage(RequirementType.LOG_CALORIES, 1500), totals))
        assertEquals(210, QuestProgression.nutritionValue(stage(RequirementType.LOG_MACROS, 200, "carbs"), totals))
        assertEquals(51, QuestProgression.nutritionValue(stage(RequirementType.LOG_MACROS, 50, "fat"), totals))
    }

    @Test
    fun `gramy se zaokrouhlují dolů – 79,9 g bílkovin nesplní cíl 80 g`() {
        val s = stage(RequirementType.LOG_MACROS, 80, "protein")
        val v = QuestProgression.nutritionValue(s, totals)!!
        assertFalse(QuestProgression.isStageSatisfied(s, v.toString()))
    }

    @Test
    fun `nevýživová fáze nemá výživovou hodnotu`() {
        assertEquals(null, QuestProgression.nutritionValue(stage(RequirementType.WALK_STEPS, 1), totals))
        assertEquals(null, QuestProgression.nutritionValue(stage(RequirementType.LOG_MACROS, 1, "cukr"), totals))
    }

    @Test
    fun `quest krále má 7 fází a všechny mluví za krále`() {
        val q = QuestRegistry.MOUNTAINS_QUEST
        assertEquals(7, q.stages.size)
        assertTrue(q.stages.all { it.speakerName == "Král Mlsák" })
        q.stages.filter { it.requirementType == RequirementType.LOG_MACROS }
            .forEach { assertTrue(it.targetId in setOf("protein", "carbs", "fat")) }
    }
}
