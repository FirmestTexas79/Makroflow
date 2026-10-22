package cz.uhk.macroflow.pokemon.cave

import cz.uhk.macroflow.pokemon.BiomeType
import cz.uhk.macroflow.pokemon.wild.WildLevels
import cz.uhk.macroflow.pokemon.wildBiome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AshPlainTest {
    private val plain = AshPlain.MAP

    @Test fun celaPlanJeDosazitelnaAVedeZpetKBrane() {
        assertEquals(plain.nodes.map { it.id }.toSet(), plain.reachableFrom(plain.exitNode))
        assertEquals("SKY_PASS", plain.parentBiome)
        assertEquals(SkyPass.GATE_NODE, plain.mountainNode)
        assertTrue(!plain.isCave)
    }

    @Test fun branaSvetuNahoreMestoDole() {
        assertTrue(plain.node(AshPlain.ENTRY_NODE)!!.y < plain.artH * 0.2)
        assertTrue(plain.node(AshPlain.CITY_GATE_NODE)!!.y > plain.artH * 0.85)
    }

    @Test fun mistaMajiOblastKlepnutiATexty() {
        (AshPlain.ACTION_NODES + plain.encounterNodes).forEach {
            assertTrue(it, plain.node(it) != null)
            assertTrue(it, plain.tapAreas.containsKey(it))
        }
        plain.encounterNodes.forEach { assertTrue(it, AshPlain.emptyEncounterText(it) != null) }
    }

    @Test fun divociJsouSilnejsiNezVDolech() {
        assertTrue(WildLevels.range(BiomeType.ASH_PLAIN).first > WildLevels.range(BiomeType.MINES).first)
        assertEquals(BiomeType.MOUNTAINS, BiomeType.ASH_PLAIN.wildBiome)
    }
}
