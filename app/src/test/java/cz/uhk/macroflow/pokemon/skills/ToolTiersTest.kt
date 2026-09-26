package cz.uhk.macroflow.pokemon.skills

import cz.uhk.macroflow.pokemon.quests.QuestRewards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolTiersTest {
    private val axes = listOf(Gear.OLD_AXE, Gear.COPPER_AXE, Gear.SILVER_AXE, Gear.GOLD_AXE, Gear.MAKRO_AXE)
    private val picks = listOf(Gear.OLD_PICKAXE, Gear.COPPER_PICKAXE, Gear.SILVER_PICKAXE, Gear.GOLD_PICKAXE, Gear.MAKRO_PICKAXE)

    @Test fun everyTierIsBetterThanThePrevious() {
        for ((line, skill) in listOf(axes to Skill.LOGGING, picks to Skill.MINING)) {
            for (i in 1 until line.size) {
                val a = line[i - 1]; val b = line[i]
                assertTrue("${b.name} síla", b.power > a.power)
                assertTrue("${b.name} XP", (b.xpBonus[skill] ?: 0.0) > (a.xpBonus[skill] ?: 0.0))
                assertTrue("${b.name} multi", (b.multiBonus[skill] ?: 0.0) >= (a.multiBonus[skill] ?: 0.0))
            }
        }
        assertEquals(4, SkillState(gear = setOf(Gear.GOLD_AXE)).afkCapHours(Skill.LOGGING) - SkillState().afkCapHours(Skill.LOGGING))
    }

    /** Každý stupeň se dá vyrobit ze surovin, které jde natěžit předchozím stupněm (a obsahuje bobule). */
    @Test fun recipesClimbTheLadder() {
        for (g in GearCrafting.TOOLS) {
            val r = GearCrafting.recipe(g)!!
            assertTrue(g.name, r.keys.any { it.startsWith("berry_") })
        }
        fun spotsReachable(tool: Gear, level: Int) = GatherSpot.entries.filter { it.toolSlot == tool.slot &&
            Gathering.efficiency(tool.power, level, 0.0) >= it.required }.map { it.resource.itemId }
        // měděný nástroj (Lv 1) těží jen měď / dub, stříbro chce trochu levelů
        assertTrue(spotsReachable(Gear.COPPER_PICKAXE, 1) == listOf("ore_copper"))
        assertTrue("ore_silver" in spotsReachable(Gear.COPPER_PICKAXE, 4))
        assertTrue("ore_gold" in spotsReachable(Gear.SILVER_PICKAXE, 6))
        // na stříbrný krumpáč stačí suroviny ze stříbrné žíly + staršího kácení
        val silver = GearCrafting.recipe(Gear.SILVER_PICKAXE)!!.keys
        assertTrue("ore_gold" !in silver && "log_maple" !in silver)
    }

    @Test fun questsGiveTheOldTools() {
        val axe = QuestRewards.forStage("meadow_mastery", 0)!!
        val pick = QuestRewards.forStage("mountains_macro_king", 0)!!
        assertEquals(Gear.OLD_AXE.id, axe.itemId)
        assertEquals(Gear.OLD_PICKAXE.id, pick.itemId)
        assertTrue(axe.line.contains("lovec") && pick.line.contains("krk"))
        assertEquals(null, QuestRewards.forStage("meadow_mastery", 1))
    }

    @Test fun toolsHaveIconsAndInfo() {
        for (g in GearCrafting.TOOLS) {
            assertEquals(256, GearArt.gearIcon(g).size)
            assertTrue(ItemInfo.sources(g.id).isNotEmpty())
        }
    }
}
