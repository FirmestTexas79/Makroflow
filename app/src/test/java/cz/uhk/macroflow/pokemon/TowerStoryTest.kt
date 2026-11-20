package cz.uhk.macroflow.pokemon

import cz.uhk.macroflow.pokemon.quests.QuestProgression
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.RequirementType
import cz.uhk.macroflow.pokemon.story.Calibration
import cz.uhk.macroflow.pokemon.story.StoryProgress
import cz.uhk.macroflow.pokemon.story.TowerStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kapitola V ve Věži Řádu Váhy (docs/adr/0104). */
class TowerStoryTest {
    private val q = QuestRegistry.OZVENA_QUEST

    @Test fun vezNavazujeNaKalibraciAMaVsechnyFaze() {
        assertEquals(Calibration.STAGE + 1, TowerStory.FIRST)
        assertEquals(TowerStory.FIRST + TowerStory.COUNT, q.stages.size)
        assertEquals(RequirementType.MEAL_STREAK, q.stages[Calibration.STAGE].requirementType)
        assertEquals(RequirementType.SLEEP_NIGHTS, q.stages[TowerStory.SPIS_212].requirementType)
        assertEquals("6", q.stages[TowerStory.DORM].targetId)                          // Dormitoř chce pořádný spánek
        assertEquals(TowerStory.FRAGMENTS.size, q.stages[TowerStory.MAP].targetValue)
        assertEquals(RequirementType.HIT_TARGET, q.stages[TowerStory.READY].requirementType)
        assertEquals(TowerStory.OBJ019_KEY, q.stages[TowerStory.OBJ019].targetId)              // Objekt 019 hned po Registru nádob
        assertEquals(TowerStory.OBJ019_KEY, cz.uhk.macroflow.pokemon.legend.SpecialBattle.OBJEKT_019.winKey)
        assertTrue(cz.uhk.macroflow.pokemon.legend.SpecialBattle.OBJEKT_019.chained)
        q.stages.drop(TowerStory.FIRST).forEach { assertTrue(it.title, !it.hint.isNullOrBlank()) }
        q.stages.drop(TowerStory.FIRST).forEach { assertTrue(QuestProgression.reminder(it, "0").isNotBlank()) }
    }

    @Test fun vytahPoustiJenSchvalenaPatra() {
        assertTrue(TowerStory.approved(0, 0, false))
        assertFalse(TowerStory.approved(1, Calibration.STAGE, false))                  // před kalibrací nic
        assertTrue(TowerStory.approved(1, TowerStory.SPIS_212, false))
        assertFalse(TowerStory.approved(2, TowerStory.SPIS_212, false))
        assertTrue(TowerStory.approved(2, TowerStory.MEASURE, false))
        assertTrue(TowerStory.approved(13, TowerStory.OFFICE, false))
        assertFalse(TowerStory.approved(-1, TowerStory.MAP, false))                     // −1 až s celou mapou
        assertTrue(TowerStory.approved(-1, TowerStory.HIDEOUT, false))
        assertFalse(TowerStory.approved(-2, TowerStory.READY, false))                   // −2 až po vyváženém dni
        assertTrue(TowerStory.approved(-2, TowerStory.READY, true))
        for (f in 1..13) assertTrue(TowerStory.approved(f, TowerStory.FIRST + f - 1, false))
    }

    @Test fun stopyAUtrzkySeSbirajiAzVeSvouChvili() {
        val none: (String) -> Boolean = { false }
        assertNull(TowerStory.tap("meri_vahy", TowerStory.SPIS_212, false, none))         // křída až ve fázi Měřírny
        val chalk = TowerStory.tap("meri_vahy", TowerStory.MEASURE, false, none)
        assertNotNull(chalk); assertEquals(listOf(TowerStory.MEASURE_KEY), chalk!!.setKeys)
        assertNull(TowerStory.tap("les_dutina", TowerStory.SPIS_212, false, none))        // útržky až po Spisu 212
        assertEquals(listOf("vez_utrzek_1"), TowerStory.tap("les_dutina", TowerStory.MEASURE, false, none)!!.setKeys)
        // komora 212 je stopa i útržek zároveň
        assertEquals(2, TowerStory.tap("led_komory_p", TowerStory.ICE, false, none)!!.setKeys.size)
        val all: (String) -> Boolean = { true }
        assertTrue(TowerStory.tap("led_komory_p", TowerStory.ICE, false, all)!!.setKeys.isEmpty())   // podruhé se jen čte
        assertTrue(TowerStory.TAP_NODES.containsAll(listOf("meri_vahy", "kanc_globus", "minus_sesit")))
        assertTrue(TowerStory.KEYS.all { StoryProgress.isStoryKey(it) })
    }
}
