package cz.uhk.macroflow.pokemon.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AccessoriesTest {

    @Test fun recipesMatchTheDesign() {
        assertEquals(mapOf("mat_leaf_dry" to 3, "mat_leaf_living" to 1, "energy_fragment" to 5), GearCrafting.recipe(Gear.GRASS_RING))
        assertEquals(mapOf("mat_ember" to 3, "mat_fire_stone" to 1, "energy_fragment" to 5), GearCrafting.recipe(Gear.FIRE_RING))
        assertEquals(mapOf("mat_water_pearl" to 3, "mat_soul_wisp" to 3, "mat_pixie_dust" to 3), GearCrafting.recipe(Gear.ADV_NECKLACE))
        assertEquals(mapOf(Resource.BERRY_BLACK.itemId to 3, "mat_magma_orb" to 2, "energy_fragment" to 20), GearCrafting.recipe(Gear.FIRE_SOUL))
        assertEquals(listOf(GearSlot.RING_1, GearSlot.RING_1, GearSlot.PENDANT, GearSlot.TRINKET), GearCrafting.ACCESSORIES.map { it.slot })
        // oba prsteny jdou do obou prstenových slotů
        assertTrue(Gear.fitting(GearSlot.RING_2).containsAll(listOf(Gear.GRASS_RING, Gear.FIRE_RING)))
    }

    @Test fun ringBonuses() {
        val st = SkillState(gear = setOf(Gear.GRASS_RING, Gear.FIRE_RING))
        assertEquals(115, st.gain(Skill.CATCHING, 100.0))
        for (s in listOf(Skill.CRAFTING, Skill.HARVESTING, Skill.MINING, Skill.LOGGING)) assertEquals(105, st.gain(s, 100.0))
    }

    @Test fun fireSoulLowersFleeChance() {
        assertEquals(0.0, SkillState().catchReduction, 1e-9)
        val st = SkillState(gear = setOf(Gear.FIRE_SOUL))
        assertEquals(0.2, st.catchReduction, 1e-9)
        assertEquals(0.08, CatchRules.fleeChance(0.1, st.catchReduction), 1e-9)
    }

    @Test fun necklaceRaisesDropChances() {
        assertEquals(1.1, SkillState(gear = setOf(Gear.ADV_NECKLACE)).dropRate, 1e-9)
        fun embers(rate: Double) = (1..6000).flatMap { Drops.roll("IGNAR", 1, false, Random(it), rate) }.count { it.itemId == "mat_ember" }
        val base = embers(1.0); val boosted = embers(1.1)
        assertTrue("$base → $boosted", boosted > base * 1.04 && boosted < base * 1.16)
    }

    @Test fun iconsAndInfo() {
        for (g in GearCrafting.ACCESSORIES) {
            assertEquals(256, GearArt.gearIcon(g).size)
            assertTrue(ItemInfo.sources(g.id).isNotEmpty())
        }
    }
}
