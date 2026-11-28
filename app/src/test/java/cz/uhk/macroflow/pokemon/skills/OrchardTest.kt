package cz.uhk.macroflow.pokemon.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sadařství ve Ztichlém sadu (docs/adr/0119). */
class OrchardTest {
    private val pickers = listOf(Gear.OLD_PICKER, Gear.COPPER_PICKER, Gear.SILVER_PICKER, Gear.GOLD_PICKER)
    private val trees = listOf(GatherSpot.APPLE_TREE, GatherSpot.PEAR_TREE, GatherSpot.ORANGE_TREE, GatherSpot.BANANA_TREE)

    @Test fun pickersClimbLikeOtherTools() {
        for (i in 1 until pickers.size) {
            val a = pickers[i - 1]; val b = pickers[i]
            assertTrue(b.power > a.power)
            assertTrue((b.xpBonus[Skill.ORCHARD] ?: 0.0) > (a.xpBonus[Skill.ORCHARD] ?: 0.0))
        }
        assertEquals(pickers, Gear.fitting(GearSlot.PICKER))
        assertEquals(GearTab.TOOLS2, GearSlot.PICKER.tab)
        for (p in pickers) assertEquals(256, GearArt.gearIcon(p).size)
        assertEquals(256, GearArt.ghost(GearSlot.PICKER).size)
        assertEquals(256, SkillArt.skillIcon(Skill.ORCHARD).size)
    }

    @Test fun fourFruitTreesInTheOrchard() {
        assertEquals(trees, GatherSpot.entries.filter { it.skill == Skill.ORCHARD })
        assertEquals(listOf(Resource.FRUIT_APPLE, Resource.FRUIT_PEAR, Resource.FRUIT_ORANGE, Resource.FRUIT_BANANA), trees.map { it.resource })
        for (t in trees) {
            assertEquals("QUIET_ORCHARD", t.biome)
            assertEquals(GearSlot.PICKER, t.toolSlot)
            assertTrue(t.resource.isFruit)
            assertEquals(144, SkillArt.resourceIcon(t.resource).size)
            assertTrue(GatherLayout.PLACES.containsKey(t))
            assertNotNull(cz.uhk.macroflow.pokemon.cave.QuietOrchard.MAP.nodes.find { it.id == t.node })
            assertTrue(t.node in cz.uhk.macroflow.pokemon.cave.QuietOrchard.ACTION_NODES)
            for (st in 0..3) assertEquals(FruitArt.TINY_W * FruitArt.TINY_H, FruitArt.tiny(t.resource, st).size)
        }
        assertEquals(listOf(10, 30, 70, 120), trees.map { it.required })
        assertEquals("Česat", GatherSpot.APPLE_TREE.verb)
    }

    /** Starý česák utrhne jablka, z nich měděný; stříbrný z hrušek, zlatý z pomerančů – a ten (s levelem) dosáhne na banány. */
    @Test fun pickerRecipesUseTheFruitOfThePreviousTier() {
        fun reachable(p: Gear, level: Int) = trees.filter { Gathering.efficiency(p.power, level, 0.0) >= it.required }
        assertEquals(listOf(GatherSpot.APPLE_TREE), reachable(Gear.OLD_PICKER, 1))
        assertTrue(GearCrafting.recipe(Gear.COPPER_PICKER)!!.containsKey(Resource.FRUIT_APPLE.itemId))
        assertTrue(GearCrafting.recipe(Gear.SILVER_PICKER)!!.containsKey(Resource.FRUIT_PEAR.itemId))
        assertTrue(GearCrafting.recipe(Gear.GOLD_PICKER)!!.containsKey(Resource.FRUIT_ORANGE.itemId))
        assertTrue(GatherSpot.PEAR_TREE in reachable(Gear.COPPER_PICKER, 4))
        assertTrue(GatherSpot.ORANGE_TREE in reachable(Gear.SILVER_PICKER, 6))
        assertTrue(GatherSpot.BANANA_TREE in reachable(Gear.GOLD_PICKER, 1))
    }

    @Test fun orchardSetUsesFruitAndRegionOneResources() {
        val set = GearSet.ORCHARD
        assertEquals(GearCrafting.ORCHARD_SET, set.pieces)
        assertEquals(listOf(GearSlot.HELMET, GearSlot.CHEST, GearSlot.LEGS, GearSlot.BOOTS), set.pieces.map { it.slot })
        for (g in set.pieces) {
            val r = GearCrafting.recipe(g)!!
            assertTrue(r.keys.any { Resource.from(it)?.isFruit == true })
            assertTrue(r.keys.any { Resource.from(it)?.isFruit != true })
            assertTrue((g.xpBonus[Skill.ORCHARD] ?: 0.0) > 0)
            assertEquals(256, GearArt.gearIcon(g).size)
        }
        assertTrue(set.hasBonus)
        assertTrue(set.bonusText.contains("sadařství"))
        assertTrue(SkillState(gear = set.pieces.toSet()).afkCapHours(Skill.ORCHARD) > SkillState().afkCapHours(Skill.ORCHARD))
    }

    @Test fun orchardHasAFullSkillTree() {
        val nodes = SkillTree.of(Skill.ORCHARD)
        assertTrue(nodes.size >= 10)
        assertNotNull(SkillTree.node("orch_master"))
    }
}
