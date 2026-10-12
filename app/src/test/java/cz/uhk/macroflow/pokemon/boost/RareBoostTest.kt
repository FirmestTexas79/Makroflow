package cz.uhk.macroflow.pokemon.boost

import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import cz.uhk.macroflow.pokemon.bag.BagItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rare nakopávače (docs/adr/0074). */
class RareBoostTest {

    @Test fun idsAreUniqueAndLookUpBack() {
        assertEquals(RareBoost.entries.size, RareBoost.entries.map { it.id }.toSet().size)
        RareBoost.entries.forEach { assertEquals(it, RareBoost.from(it.id)) }
        assertNull(RareBoost.from("item_caffeine"))
        assertNull(RareBoost.from(null))
    }

    @Test fun iconsAreSquare() {
        RareBoost.entries.forEach {
            assertEquals(it.id, RareBoost.SIZE * RareBoost.SIZE, it.pixels.size)
            assertTrue("${it.id} je celá průhledná", it.pixels.any { px -> px != 0 })
        }
    }

    @Test fun oatmealAddsXpAndLevelFollowsTheCurve() {
        val o = RareBoost.OATMEAL.apply(level = 1, xp = 0)
        assertEquals(250, o.xp)
        assertEquals(PokemonLevelCalc.levelFromXp(250), o.level)
        assertEquals(o.level - 1, o.levelsGained)
    }

    @Test fun oatmealKeepsLevelOfOlderCatchesWithZeroXp() {
        // chycený Makromon levelu 7 má XP 0 – nesmí spadnout zpátky
        val o = RareBoost.OATMEAL.apply(level = 7, xp = 0)
        assertEquals(PokemonLevelCalc.xpForLevel(7) + 250, o.xp)
        assertTrue(o.level >= 7)
    }

    @Test fun preworkoutIsOneLevelAndWhiteMonsterThree() {
        assertEquals(6, RareBoost.PREWORKOUT.apply(5, PokemonLevelCalc.xpForLevel(5) + 40).level)
        assertEquals(8, RareBoost.WHITE_MONSTER.apply(5, PokemonLevelCalc.xpForLevel(5) + 40).level)
        assertEquals(3, RareBoost.WHITE_MONSTER.apply(5, 0).levelsGained)
    }

    @Test fun levelBoostStartsNewLevelWithZeroProgress() {
        val o = RareBoost.PREWORKOUT.apply(4, PokemonLevelCalc.xpForLevel(4) + 100)
        assertEquals(PokemonLevelCalc.xpForLevel(5), o.xp)
        assertEquals(0f, PokemonLevelCalc.progressToNextLevel(o.xp), 0.0001f)
    }

    @Test fun levelBoostNeverGoesAboveMax() {
        val max = PokemonLevelCalc.MAX_LEVEL
        val o = RareBoost.WHITE_MONSTER.apply(max - 1, PokemonLevelCalc.xpForLevel(max - 1))
        assertEquals(max, o.level)
        assertEquals(1, o.levelsGained)
        assertEquals(PokemonLevelCalc.xpForLevel(max), o.xp)
    }

    @Test fun cannotUseOnMaxLevel() {
        val max = PokemonLevelCalc.MAX_LEVEL
        RareBoost.entries.forEach {
            assertFalse(it.id, it.canUse(max, PokemonLevelCalc.xpForLevel(max)))
            assertFalse(it.id, it.canUse(max, 0))
            assertTrue(it.id, it.canUse(max - 1, 0))
        }
    }

    @Test fun oatmealAtTopOfCurveCapsLevel() {
        val max = PokemonLevelCalc.MAX_LEVEL
        val o = RareBoost.OATMEAL.apply(max - 1, PokemonLevelCalc.xpForLevel(max - 1))
        assertTrue(o.level <= max)
    }

    @Test fun evolutionRule() {
        assertTrue(RareBoost.evolutionDue(oldLevel = 2, newLevel = 4, evolveLevel = 4))
        // přeskočení hranice (např. White Monster z 3 na 6)
        assertTrue(RareBoost.evolutionDue(3, 6, 4))
        assertFalse(RareBoost.evolutionDue(2, 3, 4))
        assertFalse(RareBoost.evolutionDue(5, 5, 4))
        assertFalse(RareBoost.evolutionDue(1, 9, 0))
    }

    @Test fun boostsLiveInTheirOwnBagPocketAndCanBeFed() {
        RareBoost.entries.forEach {
            val item = BagItems.of(it.id)!!
            assertEquals(BagItems.Pocket.BOOST, item.pocket)
            assertEquals(BagItems.Action.FEED, item.action)
            assertEquals(it.label, item.label)
            assertEquals(item.size * item.size, item.pixels.size)
        }
        val p = BagItems.pockets(mapOf(RareBoost.OATMEAL.id to 3, RareBoost.WHITE_MONSTER.id to 0))
        assertEquals(listOf(BagItems.Pocket.BOOST), p.keys.toList())
        assertEquals(3, p.getValue(BagItems.Pocket.BOOST).single().second)
    }
}
