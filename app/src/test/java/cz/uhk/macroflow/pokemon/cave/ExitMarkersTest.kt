package cz.uhk.macroflow.pokemon.cave

import cz.uhk.macroflow.pokemon.BiomeRegistry
import cz.uhk.macroflow.pokemon.BiomeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Šipky východů: každý východ je uzel své mapy a šipka sedí uvnitř mapy, mimo lištu a spodní okraj. */
class ExitMarkersTest {
    @Test fun everyExitIsANodeOfItsMap() {
        for ((biome, exits) in ExitMarkers.EXITS) {
            val cave = BiomeRegistry.definition(BiomeType.valueOf(biome))?.cave
            assertNotNull(biome, cave)
            for (e in exits) {
                val n = cave!!.node(e.node)
                assertNotNull("$biome/${e.node}", n)
                val (ax, ay) = ExitMarkers.anchor(n!!.x, n.y, e.dir, cave.artW, cave.artH)
                assertTrue("$biome/${e.node} uvnitř", ax in 8..cave.artW - 8 && ay in 30..cave.artH - 30)
            }
        }
    }

    @Test fun arrowPointsWhereYouGo() {
        assertEquals(88 to 42, ExitMarkers.anchor(88, 20, ExitMarkers.Dir.UP, 176, 560))      // u horního okraje dovnitř
        assertEquals(88 to 520, ExitMarkers.anchor(88, 550, ExitMarkers.Dir.DOWN, 176, 560))  // u spodního okraje nad uzlem
        assertEquals(134 to 240, ExitMarkers.anchor(134, 262, ExitMarkers.Dir.UP, 176, 560))  // uvnitř mapy před uzlem
        val px = ExitMarkers.arrowPixels(ExitMarkers.Dir.UP, locked = false)
        assertEquals(ExitMarkers.SIZE * ExitMarkers.SIZE, px.size)
        assertTrue(px[ExitMarkers.SIZE / 2] != 0)                       // špička šipky nahoře uprostřed
    }
}
