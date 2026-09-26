package cz.uhk.macroflow.pokemon.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DropsTest {

    private fun many(species: String, caught: Boolean = false, n: Int = 4000) =
        (1..n).flatMap { Drops.roll(species, 12, caught, Random(it)) }

    @Test fun familiesDropTheirMaterial() {
        val cases = mapOf("IGNAR" to Resource.EMBER, "FLORI" to Resource.LEAF_DRY, "AQULIN" to Resource.WATER_PEARL,
            "SOULU" to Resource.SOUL_WISP, "DRAKIRRA" to Resource.DRAGON_SCALE, "CHARMIRRA" to Resource.PIXIE_DUST)
        for ((sp, mat) in cases) {
            val d = many(sp, n = 300)
            assertTrue("$sp → $mat", d.any { it.itemId == mat.itemId })
            // cizí materiály ne
            val others = Resource.entries.filter { it.isMonsterMaterial && it != mat && it !in Drops.UPGRADE.values }
            assertTrue(sp, d.none { dd -> others.any { it.itemId == dd.itemId } })
        }
        assertEquals(DropFamily.NORMAL, Drops.family("SPIRRA"))
    }

    @Test fun evolutionsDropUpgrades() {
        assertTrue(many("IGNAROC", n = 300).any { it.itemId == Resource.FIRE_STONE.itemId })
        assertTrue(many("IGNAROTH", n = 300).any { it.itemId == Resource.MAGMA_ORB.itemId })
        assertTrue(many("FLORIND", n = 300).any { it.itemId == Resource.LEAF_LIVING.itemId })
        assertTrue(many("IGNAR", n = 300).none { it.itemId == Resource.MAGMA_ORB.itemId })
    }

    @Test fun normalsDropMoreEnergy() {
        assertEquals(0.67, Drops.fragmentChance(12, false, DropFamily.NORMAL), 1e-9)
        assertEquals(0.47, Drops.fragmentChance(12, false, DropFamily.FIRE), 1e-9)
        assertEquals(0.8, Drops.fragmentChance(99, false, DropFamily.NORMAL), 1e-9)
    }

    @Test fun gudwinArtifactsAreRareAndOnlyOnWin() {
        val d = many("GUDWIN", n = 20000)
        val axes = d.count { it.itemId == Gear.MAKRO_AXE.id }
        assertTrue("sekera ~5 % (bylo $axes z 20000)", axes in 700..1300)
        assertTrue(d.any { it.itemId == Gear.MAKRO_PICKAXE.id })
        assertTrue(many("GUDWIN", caught = true, n = 3000).none { Gear.from(it.itemId) != null })
    }

    @Test fun artifactsAreLegendaryTools() {
        for (g in listOf(Gear.MAKRO_AXE, Gear.MAKRO_PICKAXE)) {
            assertEquals(500, g.power)
            assertTrue(g.legendary)
            assertEquals(256, GearArt.gearIcon(g).size)
            assertTrue(ItemInfo.sources(g.id).isNotEmpty())
        }
        val st = SkillState(gear = setOf(Gear.MAKRO_AXE))
        assertEquals(150, st.gain(Skill.LOGGING, 100.0))
        assertEquals(0.10, st.gearMulti(Skill.LOGGING), 1e-9)
        assertEquals(0.0, st.gearMulti(Skill.MINING), 1e-9)
    }

    @Test fun materialsHaveArtAndInfo() {
        for (r in Resource.entries.filter { it.isMonsterMaterial }) {
            assertEquals(144, SkillArt.resourceIcon(r).size)
            assertTrue(r.name, ItemInfo.sources(r.itemId).isNotEmpty())
        }
    }
}
