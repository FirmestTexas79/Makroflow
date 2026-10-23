package cz.uhk.macroflow.pokemon

import cz.uhk.macroflow.pokemon.quests.QuestProgression
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.RequirementType
import cz.uhk.macroflow.pokemon.story.Ozvena
import cz.uhk.macroflow.pokemon.story.StoryProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kapitola V · OZVĚNA (docs/adr/0095). */
class OzvenaQuestTest {
    @Test fun snidaneSePocitajiPoDnechJenRano() {
        val meals = listOf(
            "2026-10-20" to "07:30", "2026-10-20" to "08:10",      // dvě jídla jedno ráno = 1 den
            "2026-10-21" to "12:00",                               // oběd se nepočítá
            "2026-10-22" to "10:29", "2026-10-23" to "10:30",      // 10:30 už je dopoledne
            "2026-10-19" to "07:00",                               // před začátkem fáze
            "2026-10-24" to "03:59", "2026-10-25" to "", "2026-10-26" to "04:00"
        )
        assertEquals(3, Ozvena.breakfastDays(meals, "2026-10-20"))
        assertEquals(0, Ozvena.breakfastDays(emptyList(), "2026-10-20"))
    }

    @Test fun kapitolaZacinaUPekareAKonciKnihouVenky() {
        val q = QuestRegistry.OZVENA_QUEST
        assertEquals(RequirementType.BREAKFAST_DAYS, q.stages[0].requirementType)
        assertEquals(Ozvena.BREAKFAST_DAYS, q.stages[0].targetValue)
        assertEquals(Ozvena.BOOK_KEY, q.stages[Ozvena.NAME_STAGE].targetId)
        assertTrue(QuestProgression.isStageSatisfied(q.stages[0], "3"))
        assertTrue(!QuestProgression.isStageSatisfied(q.stages[0], "2"))
        assertTrue(StoryProgress.isStoryKey(Ozvena.BOOK_KEY))
        assertTrue(q.farewell.contains("Pamětní kniha"))
        assertTrue(QuestProgression.reminder(q.stages[0], "1").isNotBlank())
    }
}
