package cz.uhk.macroflow.pokemon

import cz.uhk.macroflow.energy.Adherence
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

    private val targets = Adherence.Targets(kcal = 2500.0, protein = 160.0, carbs = 300.0, fat = 70.0)

    @Test
    fun `procento osobního cíle`() {
        val eaten = Adherence.Eaten(kcal = 2300.0, protein = 143.9, carbs = 330.0, fat = 70.0)
        assertEquals(92, QuestProgression.targetPercent(stage(RequirementType.HIT_TARGET, 100, "kcal"), eaten, targets))
        assertEquals(89, QuestProgression.targetPercent(stage(RequirementType.HIT_TARGET, 100, "protein"), eaten, targets))
        assertEquals(null, QuestProgression.targetPercent(stage(RequirementType.WALK_STEPS, 1), eaten, targets))
    }

    @Test
    fun `HIT_TARGET se splní jen v pásmu – přejedení ani hladovění nevyhrává`() {
        val kcal = stage(RequirementType.HIT_TARGET, 100, "kcal")
        assertFalse(QuestProgression.isStageSatisfied(kcal, "85"))
        assertTrue(QuestProgression.isStageSatisfied(kcal, "95"))
        assertFalse(QuestProgression.isStageSatisfied(kcal, "130"))
        val protein = stage(RequirementType.HIT_TARGET, 100, "protein")
        assertFalse(QuestProgression.isStageSatisfied(protein, "89"))
        assertTrue(QuestProgression.isStageSatisfied(protein, "120"))
    }

    @Test
    fun `quest krále má 7 fází a výživové fáze míří na osobní cíle`() {
        val q = QuestRegistry.MOUNTAINS_QUEST
        assertEquals(7, q.stages.size)
        assertTrue(q.stages.all { it.speakerName == "Král Mlsák" })
        val nutrition = q.stages.filter { it.requirementType == RequirementType.HIT_TARGET }
        assertEquals(setOf("kcal", "protein", "carbs", "fat"), nutrition.map { it.targetId }.toSet())
    }
}
