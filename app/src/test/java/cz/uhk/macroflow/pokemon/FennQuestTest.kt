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

    @Test fun druhaCastNelleZalevaniAFinale() {
        assertEquals(6, q.stages.size)
        assertEquals(Fenn.REPORTED_KEY, q.stages[Fenn.MAREN_STAGE].targetId)
        assertEquals(RequirementType.WATER_DAYS, q.stages[Fenn.WATER_STAGE].requirementType)
        assertEquals(Fenn.WATER_DAYS, q.stages[Fenn.WATER_STAGE].targetValue)
        assertEquals(Fenn.ENDING_KEY, q.stages[Fenn.FINALE_STAGE].targetId)
        assertEquals(cz.uhk.macroflow.R.drawable.npc_orchard_hut, q.farewellSpeakerResId)
        q.stages.forEach { assertTrue(it.title, QuestProgression.isStageSatisfied(it, QuestProgression.satisfyingMetadata(it))) }
    }

    @Test fun zalevaniPocitaDnySeSplnenymCilem() {
        val log = listOf("2026-10-20" to 1500, "2026-10-20" to 1200, "2026-10-21" to 2000, "2026-10-22" to 2600,
            "2026-10-19" to 3000, "2026-10-23" to 2500)
        assertEquals(3, QuestProgression.daysAtLeast(log, "2026-10-20", 2500))
        assertEquals(0, QuestProgression.daysAtLeast(log, "2026-10-20", 0))
        assertEquals(0, QuestProgression.daysAtLeast(emptyList(), "2026-10-20", 2000))
    }

    @Test fun konceAPodminkaSkrytehoKonce() {
        assertTrue(Fenn.secretEndingOpen(3, true, Fenn.SECRET_INSIGHT))
        assertFalse(Fenn.secretEndingOpen(2, true, 9))
        assertFalse(Fenn.secretEndingOpen(3, false, 9))
        assertFalse(Fenn.secretEndingOpen(3, true, Fenn.SECRET_INSIGHT - 1))
        assertEquals(3, Fenn.Ending.entries.map { Fenn.endingKey(it) }.toSet().size)
        assertTrue(Fenn.fennStays { it == Fenn.END_A_KEY } && !Fenn.inTree { it == Fenn.END_A_KEY })
        assertTrue(Fenn.inTree { it == Fenn.END_C_KEY } && Fenn.inTree { it == Fenn.END_B_KEY })
        Fenn.Ending.entries.forEach { assertTrue(Fenn.endingText(it).contains("Zlaté jablko")) }
        assertEquals(2, cz.uhk.macroflow.pokemon.story.Insight.FLAG_WEIGHTS[Fenn.END_C_KEY])
        assertFalse(cz.uhk.macroflow.pokemon.boost.RareBoost.GOLDEN_APPLE.sold)
        assertTrue(cz.uhk.macroflow.pokemon.boost.RareBoost.PROTEIN_BAR.sold)
        assertEquals(6, Fenn.FINALE_LINES.size)
    }
}
