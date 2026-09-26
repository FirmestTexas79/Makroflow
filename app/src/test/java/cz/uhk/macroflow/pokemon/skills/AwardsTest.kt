package cz.uhk.macroflow.pokemon.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AwardsTest {

    @Test fun idsAreUniqueAndEveryCategoryHasAwards() {
        assertEquals(Awards.ALL.size, Awards.ALL.map { it.id }.toSet().size)
        for (c in AwardCategory.entries) assertTrue("kategorie $c je prázdná", Awards.byCategory(c).isNotEmpty())
        for (a in Awards.ALL) {
            assertTrue(a.id, a.title.isNotBlank() && a.description.isNotBlank())
            assertTrue(a.id, a.target > 0 || a.id == "species_all")
        }
    }

    @Test fun nothingIsDoneAtStart() {
        val f = AwardFacts(speciesTotal = 30)
        for (a in Awards.ALL) assertTrue(a.id, !Awards.isDone(a, f))
    }

    @Test fun progressCountsUp() {
        val f = AwardFacts(caught = 60, shinyCaught = 1, speciesCaught = 30, speciesTotal = 30,
            gathered = mapOf(Resource.ORE_COPPER.itemId to 40), skillLevels = mapOf(Skill.MINING to 20))
        assertTrue(Awards.isDone(Awards.find("catch_50")!!, f))
        assertFalse(Awards.isDone(Awards.find("catch_500")!!, f))
        assertTrue(Awards.isDone(Awards.find("shiny_1")!!, f))
        assertTrue(Awards.isDone(Awards.find("species_all")!!, f))
        assertTrue(Awards.isDone(Awards.find("mining_20")!!, f))
        assertEquals(0.4f, Awards.fraction(Awards.find("copper_100")!!, f), 0.001f)
        assertEquals(30, Awards.target(Awards.find("species_all")!!, f))
    }

    @Test fun totalLevelCountsMissingSkillsAsOne() {
        assertEquals(Skill.entries.size, AwardFacts().totalLevel)
        assertEquals(Skill.entries.size + 9, AwardFacts(skillLevels = mapOf(Skill.CATCHING to 10)).totalLevel)
    }

    @Test fun artHasRightSizeAndLockedIsGrey() {
        for (a in Awards.ALL) {
            val on = AwardArt.icon(a, true)
            val off = AwardArt.icon(a, false)
            assertEquals(AwardArt.SIZE * AwardArt.SIZE, on.size)
            assertEquals(AwardArt.FRAME * AwardArt.FRAME, AwardArt.framed(on).size)
            assertEquals(144, AwardArt.symbol(a.symbol).size)
            for (c in off) if (c != 0) {
                val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                assertTrue(a.id, r == g && g == b)
            }
            assertTrue(a.id, on.count { it != 0 } > 150)
        }
    }
}
