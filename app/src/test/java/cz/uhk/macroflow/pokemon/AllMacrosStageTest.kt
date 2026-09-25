package cz.uhk.macroflow.pokemon

import cz.uhk.macroflow.energy.Adherence
import cz.uhk.macroflow.pokemon.quests.QuestProgression
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.QuestStage
import cz.uhk.macroflow.pokemon.quests.RequirementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllMacrosStageTest {

    private val stage = QuestStage("t", "x", 0, RequirementType.HIT_TARGET, 3, QuestProgression.ALL_MACROS)
    private val targets = Adherence.Targets(kcal = 2200.0, protein = 150.0, carbs = 250.0, fat = 70.0)

    @Test
    fun countsHitMacros() {
        val all = Adherence.Eaten(2200.0, 150.0, 250.0, 70.0)
        assertEquals(3, QuestProgression.targetPercent(stage, all, targets))
        val twoOfThree = all.copy(fat = 30.0)
        assertEquals(2, QuestProgression.targetPercent(stage, twoOfThree, targets))
        assertTrue(QuestProgression.isStageSatisfied(stage, "3"))
        assertFalse(QuestProgression.isStageSatisfied(stage, "2"))
        assertTrue(QuestProgression.reminder(stage, "2").contains("2 ze 3"))
    }

    @Test
    fun kingsLastStageIsAllMacros() {
        val last = QuestRegistry.MOUNTAINS_QUEST.stages.last()
        assertEquals(RequirementType.HIT_TARGET, last.requirementType)
        assertEquals(QuestProgression.ALL_MACROS, last.targetId)
    }
}
