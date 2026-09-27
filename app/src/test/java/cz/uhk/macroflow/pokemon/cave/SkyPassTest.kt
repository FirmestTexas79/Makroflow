package cz.uhk.macroflow.pokemon.cave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyPassTest {
    private val pass = SkyPass.MAP

    @Test fun wholePassIsReachableAndLeadsBackToThePeak() {
        assertEquals(pass.nodes.map { it.id }.toSet(), pass.reachableFrom(pass.exitNode))
        assertEquals("MOUNTAINS", pass.parentBiome)
        assertEquals("peak", pass.mountainNode)
        assertTrue(!pass.isCave)
        assertNull(pass.crystal)
        assertTrue(pass.encounterNodes.isEmpty())           // posvátné místo, žádní divocí Makromoni
    }

    @Test fun entryAtTheBottomGateAtTheTop() {
        val entry = pass.node(pass.exitNode)!!
        val gate = pass.node(SkyPass.GATE_NODE)!!
        assertTrue(entry.y > pass.artH * 0.9)
        assertTrue(gate.y < pass.artH * 0.6)
        pass.nodes.forEach { assertTrue(it.id, it.x in 0 until pass.artW && it.y in 0 until pass.artH) }
    }

    @Test fun actionNodesExistAndHaveTapAreas() {
        SkyPass.ACTION_NODES.forEach {
            assertTrue(it, pass.node(it) != null)
            assertTrue(it, pass.tapAreas.containsKey(it))
        }
        // klepnutí na prstenec brány ji otevře, i když je nad uzlem
        assertEquals(SkyPass.GATE_NODE, pass.tapAreaAt(80f, 180f))
        assertEquals(SkyPass.CLAWS_NODE, pass.tapAreaAt(128f, 336f))
    }

    @Test fun gateBoardNamesTheMissingHeart() {
        val sealed = SkyPass.gateBoard(heartPlaced = false)
        assertEquals("Srdce Hvozdu", sealed.missing)
        assertNull(SkyPass.gateBoard(heartPlaced = true).missing)
        val (x, y, w, h) = SkyPass.GATE_CROP.toList()
        assertTrue(x >= 0 && y >= 0 && x + w <= pass.artW && y + h <= pass.artH)
    }

    @Test fun veilIsARoundLoopingShimmer() {
        val a = SkyPassArt.veil(0)
        assertEquals(SkyPassArt.VEIL_SIZE * SkyPassArt.VEIL_SIZE, a.size)
        assertEquals(0, a[0] ushr 24)                                     // roh mimo kruh průhledný
        val c = SkyPassArt.VEIL_R * SkyPassArt.VEIL_SIZE + SkyPassArt.VEIL_R
        assertTrue((a[c] ushr 24) in 1..255)                              // střed viditelný
        assertTrue(!SkyPassArt.veil(0).contentEquals(SkyPassArt.veil(3)))   // snímky se liší
        assertTrue(SkyPassArt.veil(0).contentEquals(SkyPassArt.veil(SkyPassArt.FRAMES)))  // smyčka
        // závoj sedí uvnitř prstence z gen_skypass.py (vnitřní poloměr 19)
        assertTrue(SkyPassArt.VEIL_R < 19)
    }

    @Test fun cloudsDriftAcrossAndWrap() {
        val w = 20
        val x0 = SkyPassArt.cloudX(0, 4.0, 0.0, pass.artW, w)
        val x1 = SkyPassArt.cloudX(1000, 4.0, 0.0, pass.artW, w)
        assertTrue(x1 > x0)
        val period = ((pass.artW + w) / 4.0 * 1000).toLong()
        assertTrue(kotlin.math.abs(SkyPassArt.cloudX(period, 4.0, 0.0, pass.artW, w) - x0) < 1e-6)
        for (t in 0L..60_000L step 997) {
            val x = SkyPassArt.cloudX(t, 3.0, 17.0, pass.artW, w)
            assertTrue("$x", x >= -w && x < pass.artW)
        }
        val cl = SkyPassArt.cloud(22, 7, 1)
        assertTrue(cl.count { (it ushr 24) > 0 } > 22)
        assertTrue(SkyPassArt.pulse(0) < 0.01 && SkyPassArt.pulse(1100) > 0.99)
    }
}
