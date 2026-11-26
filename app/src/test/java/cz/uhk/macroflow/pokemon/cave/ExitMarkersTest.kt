package cz.uhk.macroflow.pokemon.cave

import cz.uhk.macroflow.pokemon.BiomeRegistry
import cz.uhk.macroflow.pokemon.BiomeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Šipky východů: každý východ je uzel své mapy, šipka sedí uvnitř mapy a míří ke správnému okraji. */
class ExitMarkersTest {
    @Test fun everyExitIsANodeOfItsMap() {
        for ((biome, exits) in ExitMarkers.EXITS) {
            val cave = BiomeRegistry.definition(BiomeType.valueOf(biome))?.cave
            assertNotNull(biome, cave)
            for (e in exits) {
                val n = cave!!.node(e.node)
                assertNotNull("$biome/${e.node}", n)
                val d = ExitMarkers.dir(n!!.x, n.y, cave.artW, cave.artH)
                val (ax, ay) = ExitMarkers.anchor(n.x, n.y, d, cave.artW, cave.artH)
                assertTrue("$biome/${e.node} uvnitř", ax in 8..cave.artW - 8 && ay in 30..cave.artH - 30)
            }
        }
    }

    @Test fun arrowPointsToTheEdge() {
        assertEquals(ExitMarkers.Dir.UP, ExitMarkers.dir(88, 20, 176, 560))
        assertEquals(ExitMarkers.Dir.DOWN, ExitMarkers.dir(88, 550, 176, 560))
        assertEquals(ExitMarkers.Dir.LEFT, ExitMarkers.dir(10, 300, 176, 560))
        assertEquals(ExitMarkers.Dir.RIGHT, ExitMarkers.dir(170, 300, 176, 560))
        assertEquals(ExitMarkers.Dir.DOWN, ExitMarkers.dir(88, 300, 176, 560))
        val px = ExitMarkers.arrowPixels(ExitMarkers.Dir.UP, locked = false)
        assertEquals(ExitMarkers.SIZE * ExitMarkers.SIZE, px.size)
        assertTrue(px[ExitMarkers.SIZE / 2] != 0)                       // špička šipky nahoře uprostřed
    }
}
