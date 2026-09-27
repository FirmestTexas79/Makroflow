package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.pokemon.cave.ForestMap
import cz.uhk.macroflow.pokemon.cave.SkyPassArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestHeartTest {

    @Test fun mydrusWaitsForTheLegend() {
        assertTrue(!ForestHeart.questAvailable(legendFaced = false))
        assertTrue(ForestHeart.questAvailable(legendFaced = true))
    }

    @Test fun rotSpotsAndActorsLieInsideTheForest() {
        val m = ForestMap.MAP
        (ForestHeart.ROT_SPOTS.map { it.first to it.second } + ForestHeart.MYDRUS_POS + ForestHeart.SOULORD_POS + ForestHeart.MYCIT_POS)
            .forEach { (x, y) -> assertTrue("$x,$y", x in 0 until m.artW && y in 0 until m.artH) }
        // Mydrus stojí v oblasti klepnutí mýtiny – klepnutí na něj otevře rozhovor
        val (mx, my) = ForestHeart.MYDRUS_POS
        assertEquals(ForestHeart.MYDRUS_NODE, m.tapAreaAt(mx.toFloat(), my - 6f))
    }

    @Test fun rotPatchesAreSmallPurpleBlotchesWithMushrooms() {
        for ((i, spot) in ForestHeart.ROT_SPOTS.withIndex()) {
            val r = spot.third
            val p = ForestHeart.rotPatch(r, i + 1)
            assertEquals((2 * r + 1) * (r + 1), p.size)
            val filled = p.count { (it ushr 24) > 0 }
            assertTrue("$i: $filled", filled > p.size / 3)
            assertTrue(p.any { it == 0xFFE84A3A.toInt() })               // klobouček houby
            assertTrue(ForestHeart.rotPatch(r, i + 1).contentEquals(p))   // deterministické
        }
        assertTrue(!ForestHeart.rotPatch(8, 1).contentEquals(ForestHeart.rotPatch(8, 2)))
    }

    @Test fun heartIconIsAnAmberSeedWithALeaf() {
        val px = ForestHeart.iconPixels()
        assertEquals(ForestHeart.ICON * ForestHeart.ICON, px.size)
        assertEquals(0, px[0])
        assertTrue(px.any { it == 0xFFFFF0A0.toInt() })   // záře uvnitř
        assertTrue(px.any { it == 0xFF4EB84A.toInt() })   // lístek
    }

    @Test fun gateDetailShowsVeilAndHeartOnlyWhenOpen() {
        val w = 80; val h = 76; val cx = 40; val cy = 158
        val base = IntArray(w * h) { 0xFF404040.toInt() }
        val sealed = SkyPassArt.gateDetail(base, cx, cy, w, h, open = false)
        val open = SkyPassArt.gateDetail(base, cx, cy, w, h, open = true)
        val centre = (SkyPassArt.GATE_Y - cy) * w + (SkyPassArt.GATE_X - cx)
        assertTrue(sealed[centre] != base[centre])                           // závoj přes střed
        val socket = (SkyPassArt.SOCKET_Y - cy) * w + (SkyPassArt.SOCKET_X - cx)
        assertEquals(0xFFF4B840.toInt(), open[socket])                       // jantar v lůžku
        assertTrue(sealed[socket] != 0xFFF4B840.toInt())
        assertTrue(base.all { it == 0xFF404040.toInt() })                    // vstup se nemění
        assertEquals(0xFF808080.toInt(), SkyPassArt.blend(0xFF000000.toInt(), 0x80FFFFFF.toInt()))
    }
}
