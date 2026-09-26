package cz.uhk.macroflow.pokemon.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GearSetTest {

    @Test fun recipesMatchTheDesign() {
        assertEquals(mapOf("energy_fragment" to 5, Resource.BERRY_BLUE.itemId to 3), GearCrafting.recipe(Gear.ADV_CAP))
        assertEquals(mapOf("log_oak" to 15, Resource.BERRY_GREEN.itemId to 10), GearCrafting.recipe(Gear.ADV_TUNIC))
        assertEquals(mapOf("ore_copper" to 15, Resource.BERRY_BLACK.itemId to 1), GearCrafting.recipe(Gear.ADV_PANTS))
        assertEquals(mapOf("ore_silver" to 5, "log_birch" to 5), GearCrafting.recipe(Gear.ADV_SLIPPERS))
        assertEquals(listOf(GearSlot.HELMET, GearSlot.CHEST, GearSlot.LEGS, GearSlot.BOOTS), GearCrafting.SET.map { it.slot })
        assertEquals(Gear.entries.size, Gear.entries.map { it.code }.toSet().size)
    }

    @Test fun canCraftNeedsEverything() {
        val owned = mapOf("ore_silver" to 5, "log_birch" to 4)
        assertTrue(!GearCrafting.canCraft(Gear.ADV_SLIPPERS, owned))
        assertTrue(GearCrafting.canCraft(Gear.ADV_SLIPPERS, owned + ("log_birch" to 5)))
    }

    @Test fun equippedGearAddsXpBonus() {
        val base = SkillState()
        val dressed = SkillState(gear = setOf(Gear.ADV_CAP, Gear.ADV_TUNIC, Gear.ADV_PANTS, Gear.ADV_SLIPPERS))
        assertEquals(100, base.gain(Skill.CATCHING, 100.0))
        assertEquals(110, dressed.gain(Skill.CATCHING, 100.0))
        assertEquals(110, dressed.gain(Skill.CRAFTING, 100.0))
        assertEquals(110, dressed.gain(Skill.HARVESTING, 100.0))
        assertEquals(115, dressed.gain(Skill.MINING, 100.0))
        assertEquals(115, dressed.gain(Skill.LOGGING, 100.0))
        assertEquals(50, dressed.gearEfficiency(Skill.MINING))
        assertEquals(0, dressed.gearEfficiency(Skill.CATCHING))
    }

    @Test fun slippersNeedATool() {
        assertEquals(60, Gathering.efficiency(10, 1, 0.0, 50))
        assertEquals(0, Gathering.efficiency(0, 1, 0.0, 50))
    }

    @Test fun gearHasInfoAndIcons() {
        for (g in GearCrafting.SET) {
            assertEquals(256, GearArt.gearIcon(g).size)
            assertTrue(g.id, ItemInfo.sources(g.id).isNotEmpty())
        }
    }
}
