package cz.uhk.macroflow.pokemon.cave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietOrchardTest {
    private val sad = QuietOrchard.MAP

    @Test fun nocJeOd19Do5() {
        assertTrue(QuietOrchard.isNight(19)); assertTrue(QuietOrchard.isNight(23)); assertTrue(QuietOrchard.isNight(0)); assertTrue(QuietOrchard.isNight(4))
        assertFalse(QuietOrchard.isNight(5)); assertFalse(QuietOrchard.isNight(12)); assertFalse(QuietOrchard.isNight(18))
    }

    @Test fun celySadJeDosazitelnyAVedeZpetNaPlan() {
        assertEquals(sad.nodes.map { it.id }.toSet(), sad.reachableFrom(sad.exitNode))
        assertEquals("ASH_PLAIN", sad.parentBiome)
        assertTrue(AshPlain.MAP.node(sad.mountainNode) != null)
        assertTrue(sad.mountainNode in AshPlain.ACTION_NODES)
        assertTrue(sad.node(QuietOrchard.ENTRY_NODE)!!.x > 140)          // z pláně se jde doleva → vstup zprava
        assertTrue(AshPlain.MAP.node(sad.mountainNode)!!.x < 20)
    }

    @Test fun mistaMajiOblastKlepnutiATexty() {
        (QuietOrchard.ACTION_NODES + sad.encounterNodes).forEach {
            assertTrue(it, sad.node(it) != null); assertTrue(it, sad.tapAreas.containsKey(it))
        }
        sad.encounterNodes.forEach { assertTrue(it, QuietOrchard.emptyEncounterText(it) != null) }
        assertTrue("jezírko = rybaření", sad.encounterNodes.any { it.startsWith("jezirko_") })
    }
}
