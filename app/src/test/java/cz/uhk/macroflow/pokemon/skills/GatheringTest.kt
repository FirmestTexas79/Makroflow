package cz.uhk.macroflow.pokemon.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GatheringTest {

    @Test fun efficiencyFromToolLevelAndTree() {
        assertEquals(10, Gathering.efficiency(10, 1, 0.0))
        assertEquals(28, Gathering.efficiency(10, 10, 0.0))
        assertEquals(33, Gathering.efficiency(10, 10, 0.2))
        assertEquals(0, Gathering.efficiency(0, 30, 0.2))    // bez nástroje nic
    }

    @Test fun timePerUnitShrinksWithEfficiency() {
        assertEquals(180L, Gathering.secondsPerUnit(GatherSpot.COPPER, 10))
        assertEquals(90L, Gathering.secondsPerUnit(GatherSpot.COPPER, 20))
        assertEquals(18L, Gathering.secondsPerUnit(GatherSpot.COPPER, 10_000))   // nejvýš 10× rychleji
        assertNull(Gathering.secondsPerUnit(GatherSpot.SILVER, 29))
        assertEquals(360L, Gathering.secondsPerUnit(GatherSpot.SILVER, 30))
    }

    @Test fun harderSpotsNeedMoreAndGiveMore() {
        for (group in GatherSpot.entries.groupBy { it.skill }.values) {
            for (i in 1 until group.size) {
                assertTrue(group[i].required > group[i - 1].required)
                assertTrue(group[i].baseSeconds > group[i - 1].baseSeconds)
                assertTrue(group[i].xp > group[i - 1].xp)
            }
        }
        assertEquals(GearSlot.PICKAXE, GatherSpot.GOLD.toolSlot)
        assertEquals(GearSlot.AXE, GatherSpot.OAK.toolSlot)
    }

    @Test fun claimKeepsPartialProgress() {
        val t0 = Gathering.EPOCH + 1000
        val a = Gathering.Activity(GatherSpot.COPPER, t0)
        // 38 minut = 2280 s → 12 kusů po 180 s, zbytek 120 s
        val c = Gathering.claim(a, t0 + 2280, 180, 0.0, 12, Random(1))
        assertEquals(12, c.units); assertEquals(12, c.amount)
        assertEquals(t0 + 12 * 180, c.newSince)
        assertFalse(c.capped)
        val (f, left) = Gathering.partial(a.copy(since = c.newSince), t0 + 2280, 180)
        assertEquals(120f / 180f, f, 0.001f); assertEquals(60L, left)
    }

    @Test fun claimRespectsAfkCapAndMulti() {
        val t0 = Gathering.EPOCH
        val a = Gathering.Activity(GatherSpot.COPPER, t0)
        val c = Gathering.claim(a, t0 + 30 * 3600, 180, 1.0, 12)
        assertTrue(c.capped)
        assertEquals(240, c.units)            // 12 h / 3 min
        assertEquals(480, c.amount)           // multi 100 % = vždy dvojnásobek
        assertEquals(t0 + 30 * 3600, c.newSince)
        assertEquals(240, Gathering.pending(a, t0 + 30 * 3600, 180, 12))
    }

    @Test fun activityEncoding() {
        val now = Gathering.EPOCH + 5_000_000
        val a = Gathering.decode(GatherSpot.BIRCH.code, Gathering.encodeSince(now))!!
        assertEquals(GatherSpot.BIRCH, a.spot); assertEquals(now, a.since)
        assertNull(Gathering.decode(0, 5))
        assertEquals(GatherSpot.MAPLE, GatherSpot.fromNode("strom_javor"))
    }

    @Test fun gearSlotsAndTabs() {
        assertEquals(4, GearSlot.of(GearTab.EQUIPS).size)
        assertEquals(4, GearSlot.of(GearTab.ACCESS).size)
        assertEquals(4, GearSlot.of(GearTab.TOOLS).size)
        assertTrue(GearSlot.MYSTERY.locked)
        assertEquals(listOf(Gear.OLD_AXE, Gear.MAKRO_AXE), Gear.fitting(GearSlot.AXE))
        assertEquals(Gear.OLD_PICKAXE, Gear.fromCode(2))
        val codes = Gear.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test fun treeNodesForNewSkills() {
        val s = SkillState(unlocked = setOf("mine_eff", "mine_afk"))
        assertEquals(0.2, s.efficiencyBonus(Skill.MINING), 1e-9)
        assertEquals(0.0, s.efficiencyBonus(Skill.LOGGING), 1e-9)
        assertEquals(24, s.afkCapHours(Skill.MINING))
        assertEquals(12, s.afkCapHours(Skill.LOGGING))
    }

    @Test fun awayText() {
        assertEquals("38 min", Gathering.awayText(38 * 60 + 5))
        assertEquals("2 h 5 min", Gathering.awayText(2 * 3600 + 5 * 60))
        assertEquals("3 h", Gathering.awayText(3 * 3600))
    }
}
