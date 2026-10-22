package cz.uhk.macroflow.pokemon.cave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoCanyonTest {
    private val kanon = EchoCanyon.MAP

    @Test fun celyKanonJeDosazitelnyAVstupJeZleva() {
        assertEquals(kanon.nodes.map { it.id }.toSet(), kanon.reachableFrom(kanon.exitNode))
        assertEquals("ASH_PLAIN", kanon.parentBiome)
        assertTrue(kanon.node(EchoCanyon.ENTRY_NODE)!!.x < 16)            // z pláně se jde doprava
        assertTrue(EchoCanyon.PLAIN_NODE in AshPlain.ACTION_NODES)
        assertTrue(AshPlain.MAP.node(EchoCanyon.PLAIN_NODE)!!.x > 140)
    }

    @Test fun mistaMajiOblastKlepnutiATexty() {
        (EchoCanyon.ACTION_NODES + kanon.encounterNodes).forEach {
            assertTrue(it, kanon.node(it) != null); assertTrue(it, kanon.tapAreas.containsKey(it))
        }
        kanon.encounterNodes.forEach { assertTrue(it, EchoCanyon.emptyEncounterText(it) != null) }
    }

    @Test fun ozvenaSeStridaPodleDne() {
        assertNotEquals(EchoCanyon.echo(1), EchoCanyon.echo(2))
        assertEquals(EchoCanyon.echo(3), EchoCanyon.echo(7))
    }
}
