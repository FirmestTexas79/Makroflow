package cz.uhk.macroflow.pokemon.transition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTransitionsTest {
    private val sizes = listOf(120 to 267, 120 to 200, 96 to 214)

    @Test fun everyTargetHasItsOwnScene() {
        val kinds = listOf("TOWN", "MEADOW", "FOREST", "MOUNTAINS").map { LocationTransitions.forBiome(it, 120, 267)!!::class }
        assertEquals(4, kinds.toSet().size)
        assertEquals(null, LocationTransitions.forBiome("CAVE_OPEN", 120, 267))
    }

    /** Na začátku je vidět stará mapa, v coveredAt je obrazovka celá zakrytá, na konci zase celá volná. */
    @Test fun coversFullyThenOpens() {
        for ((w, h) in sizes) for (b in listOf("TOWN", "MEADOW", "FOREST", "MOUNTAINS")) {
            val s = LocationTransitions.forBiome(b, w, h)!!
            assertTrue("$b ${w}x$h začátek", s.coverage(0) < 0.05f)
            assertTrue("$b ${w}x$h zakryto", s.coverage(s.coveredAt) == 1f)
            assertTrue("$b ${w}x$h konec", s.coverage(s.end) == 0f)
            assertTrue(b, s.coveredAt < s.end)
        }
    }

    @Test fun coverageGrowsThenShrinks() {
        for (b in listOf("TOWN", "MEADOW", "FOREST", "MOUNTAINS")) {
            val s = LocationTransitions.forBiome(b, 120, 267)!!
            val half = s.coverage(s.coveredAt / 2)
            assertTrue("$b v půlce zakrývání ($half)", half in 0.1f..0.95f)
            val opening = s.coverage((s.coveredAt + s.end) / 2 + 150)
            assertTrue("$b při otevírání ($opening)", opening in 0.02f..0.95f)
        }
    }
}
