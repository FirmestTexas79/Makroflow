package cz.uhk.macroflow.pokemon.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jiskřivý a Duhový set (docs/adr/0053). */
class ArmorSetsTest {

    private fun needs(g: Gear, r: Resource) = (GearCrafting.recipe(g)?.get(r.itemId) ?: 0) > 0

    @Test fun sparkSetIsSilverSparksAndMonsterDrops() {
        val set = GearCrafting.SPARK_SET
        assertEquals(listOf(GearSlot.HELMET, GearSlot.CHEST, GearSlot.LEGS, GearSlot.BOOTS), set.map { it.slot })
        for (g in set) {
            assertTrue(g.id, needs(g, Resource.ORE_SILVER) && needs(g, Resource.BUG_SPARK))
            assertTrue(g.id, GearCrafting.recipe(g)!!.keys.any { Resource.from(it)?.isMonsterMaterial == true })
        }
        assertTrue(needs(Gear.SPARK_BOOTS, Resource.BUG_CRYSTAL))                  // boty i druhé mušky
        assertTrue(set.dropLast(1).none { needs(it, Resource.BUG_CRYSTAL) })
    }

    @Test fun prismSetIsGoldCrystalsAndMagmaBoots() {
        val set = GearCrafting.PRISM_SET
        assertEquals(listOf(GearSlot.HELMET, GearSlot.CHEST, GearSlot.LEGS, GearSlot.BOOTS), set.map { it.slot })
        for (g in set) assertTrue(g.id, needs(g, Resource.ORE_GOLD))
        set.dropLast(1).forEach { assertTrue(it.id, needs(it, Resource.BUG_CRYSTAL) && !needs(it, Resource.BUG_MAGMA)) }
        assertTrue(needs(Gear.MAGMA_BOOTS, Resource.BUG_MAGMA) && needs(Gear.MAGMA_BOOTS, Resource.MAGMA_ORB))
    }

    @Test fun eachTierIsStrongerThanThePrevious() {
        val tiers = listOf(GearCrafting.SET, GearCrafting.SPARK_SET, GearCrafting.PRISM_SET)
        fun total(set: List<Gear>) = set.sumOf { it.xpBonus.values.sum() }
        assertTrue(total(tiers[1]) > total(tiers[0]) && total(tiers[2]) > total(tiers[1]))
        assertTrue(Gear.MAGMA_BOOTS.efficiencyBonus.getValue(Skill.MINING) > Gear.SPARK_BOOTS.efficiencyBonus.getValue(Skill.MINING))
        assertTrue(GearCrafting.xp(Gear.MAGMA_BOOTS) > GearCrafting.xp(Gear.SPARK_BOOTS))
    }

    @Test fun setBonusNeedsAllFourPieces() {
        val three = SkillState(gear = GearCrafting.PRISM_SET.dropLast(1).toSet())
        val four = SkillState(gear = GearCrafting.PRISM_SET.toSet())
        assertEquals(0.06, three.gearMulti(Skill.MINING), 1e-9)
        assertEquals(0.11, four.gearMulti(Skill.MINING), 1e-9)                    // +5 % za celou sadu
        assertTrue(four.gain(Skill.LOGGING, 100.0) > three.gain(Skill.LOGGING, 100.0))
        val spark = SkillState(gear = GearCrafting.SPARK_SET.toSet())
        assertEquals(12 + 1, spark.afkCapHours(Skill.BUG_CATCHING))
        assertEquals(12, SkillState(gear = GearCrafting.SPARK_SET.drop(1).toSet()).afkCapHours(Skill.BUG_CATCHING))
        assertTrue(!GearSet.ADVENTURER.hasBonus && GearSet.SPARK.hasBonus && GearSet.PRISM.hasBonus)
        assertEquals(1.1, four.dropRate, 1e-9)                                   // magmové boty
    }

    @Test fun iconsInfoAndUniqueCodes() {
        for (g in GearCrafting.SPARK_SET + GearCrafting.PRISM_SET) {
            assertEquals(256, GearArt.gearIcon(g).size)
            assertTrue(g.id, GearArt.gearIcon(g).any { it != 0 })
            assertTrue(g.id, ItemInfo.sources(g.id).any { it.where.contains(GearSet.of(g)!!.label) })
        }
        assertEquals(Gear.entries.size, Gear.entries.map { it.code }.toSet().size)
        assertEquals(Gear.entries.size, Gear.entries.map { it.id }.toSet().size)
    }
}
