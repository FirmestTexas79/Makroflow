package cz.uhk.macroflow.pokemon

import cz.uhk.macroflow.pokemon.cave.QuietOrchard
import cz.uhk.macroflow.pokemon.quests.QuestProgression
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.RequirementType
import cz.uhk.macroflow.pokemon.story.Fenn
import cz.uhk.macroflow.pokemon.story.StoryProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zahradník, který sázel jména (Ztichlý sad, docs/adr/0096). */
class FennQuestTest {
    private val q = QuestRegistry.FENN_QUEST

    @Test fun linkaVedeOdChalupyPresNelleKFennovi() {
        assertTrue(q.side)
        assertEquals(Fenn.NELLE_KEY, q.stages[Fenn.NELLE_STAGE].targetId)
        assertEquals(Fenn.MET_KEY, q.stages[Fenn.NIGHT_STAGE].targetId)
        val names = q.stages[Fenn.NAMES_STAGE]
        assertEquals(RequirementType.FLAGS_COUNT, names.requirementType)
        assertEquals(3, names.targetValue)
        assertFalse(QuestProgression.isStageSatisfied(names, "2"))
        assertTrue(QuestProgression.isStageSatisfied(names, "3"))
        assertTrue(QuestProgression.reminder(names, "1").isNotBlank())
    }

    @Test fun stopyJsouVSaduVeDneIVNoci() {
        val nodes = QuietOrchard.MAP.nodes.map { it.id }
        Fenn.Clue.entries.forEach { assertTrue(it.name, it.node in nodes && QuietOrchard.MAP.tapAreas.containsKey(it.node)) }
        assertEquals(Fenn.Clue.entries.size, Fenn.Clue.entries.map { it.node }.toSet().size)
        assertTrue(Fenn.Clue.entries.any { it.atNight } && Fenn.Clue.entries.any { !it.atNight })
        assertEquals(Fenn.Clue.WELL, Fenn.clueAt(QuietOrchard.WELL_NODE))
        assertEquals(null, Fenn.clueAt(QuietOrchard.HUT_NODE))
        assertTrue(Fenn.NODE in QuietOrchard.ACTION_NODES && Fenn.NODE in QuietOrchard.MAP.reachableFrom(QuietOrchard.MAP.exitNode))
        assertEquals(2, Fenn.truths { it == Fenn.Clue.WELL.truthKey || it == Fenn.Clue.POND.truthKey })
        Fenn.ALL_KEYS.forEach { assertTrue(it, StoryProgress.isStoryKey(it)) }
        assertTrue(Fenn.CUTSCENE_LINES.any { '▒' in it })
    }

    @Test fun hotovyQuestSeSDalsimiFazemiZnovuOtevre() {
        val done = QuestProgressEntity(questId = q.id, currentStageIndex = q.stages.size, isCompleted = true)
        assertSame(done, QuestProgression.reopenIfExtended(done, q, 5L))
        val longer = q.copy(stages = q.stages + q.stages.last())
        val reopened = QuestProgression.reopenIfExtended(done, longer, 5L)
        assertFalse(reopened.isCompleted)
        assertEquals(q.stages.size, reopened.currentStageIndex)
        assertEquals(5L, reopened.stageStartedAt)
    }
}
