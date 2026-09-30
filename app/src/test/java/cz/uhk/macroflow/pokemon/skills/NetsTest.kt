package cz.uhk.macroflow.pokemon.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chytání hmyzu síťkou v Dolech (docs/adr/0049). */
class NetsTest {
    private val nets = listOf(Gear.OLD_NET, Gear.COPPER_NET, Gear.SILVER_NET, Gear.GOLD_NET)
    private val bugs = listOf(GatherSpot.SPARK_FLIES, GatherSpot.CRYSTAL_FLIES, GatherSpot.MAGMA_FLIES)

    @Test fun netsClimbLikeOtherTools() {
        for (i in 1 until nets.size) {
            val a = nets[i - 1]; val b = nets[i]
            assertTrue(b.power > a.power)
            assertTrue((b.xpBonus[Skill.BUG_CATCHING] ?: 0.0) > (a.xpBonus[Skill.BUG_CATCHING] ?: 0.0))
            assertTrue((b.multiBonus[Skill.BUG_CATCHING] ?: 0.0) >= (a.multiBonus[Skill.BUG_CATCHING] ?: 0.0))
        }
        assertEquals(nets, Gear.fitting(GearSlot.NET))
        assertEquals(4, SkillState(gear = setOf(Gear.GOLD_NET)).afkCapHours(Skill.BUG_CATCHING) - SkillState().afkCapHours(Skill.BUG_CATCHING))
        for (n in nets) assertEquals(256, GearArt.gearIcon(n).size)
    }

    @Test fun threeBugSpotsInTheMinesNeedANet() {
        assertEquals(bugs, GatherSpot.entries.filter { it.skill == Skill.BUG_CATCHING })
        for (s in bugs) {
            assertEquals("MINES", s.biome)
            assertEquals(GearSlot.NET, s.toolSlot)
            assertTrue(s.resource.isBug)
            assertEquals(144, SkillArt.resourceIcon(s.resource).size)
            assertTrue(GatherLayout.PLACES.containsKey(s))
        }
        assertEquals(listOf(10, 30, 70), bugs.map { it.required })
        assertEquals("Chytat", GatherSpot.SPARK_FLIES.verb)
    }

    /** Stará síťka chytí jiskřivky, z nich měděná; stříbrná z krystalových, zlatá z magmových. */
    @Test fun netRecipesUseTheBugsOfThePreviousTier() {
        fun reachable(net: Gear, level: Int) = bugs.filter { Gathering.efficiency(net.power, level, 0.0) >= it.required }
        assertEquals(listOf(GatherSpot.SPARK_FLIES), reachable(Gear.OLD_NET, 1))
        assertTrue(GatherSpot.CRYSTAL_FLIES in reachable(Gear.COPPER_NET, 4))
        assertTrue(GatherSpot.MAGMA_FLIES in reachable(Gear.SILVER_NET, 6))
        assertTrue(Resource.BUG_SPARK.itemId in GearCrafting.recipe(Gear.COPPER_NET)!!)
        assertTrue(Resource.BUG_CRYSTAL.itemId in GearCrafting.recipe(Gear.SILVER_NET)!!)
        assertTrue(Resource.BUG_MAGMA.itemId in GearCrafting.recipe(Gear.GOLD_NET)!!)
        for (n in listOf(Gear.COPPER_NET, Gear.SILVER_NET, Gear.GOLD_NET)) {
            assertTrue(n in GearCrafting.TOOLS)
            assertTrue(GearCrafting.xp(n) > 0)
        }
        assertTrue(ItemInfo.sources(Gear.OLD_NET.id).isNotEmpty())
    }

    @Test fun catchingTreeHasANetBranch() {
        val ids = SkillTree.of(Skill.BUG_CATCHING).map { it.id }
        assertTrue(ids.containsAll(listOf("net_eff", "net_xp", "net_multi", "net_afk")))
        val s = SkillState(unlocked = setOf("net_eff", "net_multi", "net_afk"), gear = setOf(Gear.SILVER_NET))
        assertEquals(0.2, s.efficiencyBonus(Skill.BUG_CATCHING), 1e-9)
        assertEquals(0.10, s.treeMulti(Skill.BUG_CATCHING), 1e-9)
        assertEquals(0.15, s.multiChance(Skill.BUG_CATCHING), 1e-9)       // level 1: pasiv 0 + síťka 5 % + strom 10 %
        assertEquals(24, s.afkCapHours(Skill.BUG_CATCHING))
        assertEquals(null, SkillTree.node("net_eff")!!.requires)
    }

    /** Chytání hmyzu je samostatná dovednost – Chytání Makromonů se ho vůbec netýká. */
    @Test fun bugCatchingIsItsOwnSkill() {
        assertTrue(Skill.BUG_CATCHING != Skill.CATCHING)
        assertEquals("skill_xp_bugcatching", Skill.BUG_CATCHING.xpItemId)
        assertTrue(SkillTree.of(Skill.CATCHING).none { it.id.startsWith("net_") })
        assertTrue(GatherSpot.entries.none { it.skill == Skill.CATCHING })
        for (n in nets) assertTrue(n.xpBonus[Skill.CATCHING] == null && n.multiBonus[Skill.CATCHING] == null)
        val st = SkillState().withXp(Skill.BUG_CATCHING, 500)
        assertEquals(1, st.level(Skill.CATCHING))
        assertTrue(st.level(Skill.BUG_CATCHING) > 1)
        assertEquals(256, SkillArt.skillIcon(Skill.BUG_CATCHING).size)
    }

    @Test fun bugAwardsExist() {
        val ids = Awards.ALL.map { it.id }
        assertTrue(ids.containsAll(listOf("spark_100", "crystal_50", "magma_25")))
        assertEquals(3, Awards.ALL.count { it.symbol.name.startsWith("BUG_") })
        assertTrue(Awards.ALL.filter { it.symbol.name.startsWith("BUG_") }.all { it.category == AwardCategory.BUG_CATCHING })
    }
}
