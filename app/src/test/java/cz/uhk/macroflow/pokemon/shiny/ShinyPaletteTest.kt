package cz.uhk.macroflow.pokemon.shiny

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShinyPaletteTest {

    private fun hueOf(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f; val g = ((argb shr 8) and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b); val d = max - minOf(r, g, b)
        var h = when (max) { r -> 60f * (((g - b) / d) % 6f); g -> 60f * ((b - r) / d + 2f); else -> 60f * ((r - g) / d + 4f) }
        if (h < 0) h += 360f
        return h
    }

    @Test
    fun oddsAreRoughlyOneInHundred() {
        val rnd = Random(42)
        val hits = (1..200_000).count { ShinyPalette.roll(rnd) }
        // Očekávaně 2000; ±10 % je víc než 4 směrodatné odchylky
        assertTrue("hits=$hits", hits in 1800..2200)
    }

    @Test
    fun shiftIsStablePerSpeciesAndInRange() {
        val ids = (1..31).map { it.toString().padStart(3, '0') }
        ids.forEach { id ->
            val s = ShinyPalette.hueShiftFor(id)
            assertTrue("$id -> $s", s in ShinyPalette.MIN_SHIFT..ShinyPalette.MAX_SHIFT)
            assertEquals(s, ShinyPalette.hueShiftFor(id))
        }
        // Druhy by neměly mít všechny stejný odstín
        assertTrue(ids.map { ShinyPalette.hueShiftFor(it) }.distinct().size > 10)
    }

    @Test
    fun transparentAndGreyPixelsStay() {
        assertEquals(0x00FF0000, ShinyPalette.transform(0x00FF0000, 120))
        val black = 0xFF000000.toInt(); val white = 0xFFFFFFFF.toInt(); val grey = 0xFF808080.toInt()
        listOf(black, white, grey).forEach { assertEquals(it, ShinyPalette.transform(it, 180)) }
    }

    @Test
    fun hueRotatesByShift() {
        val red = 0xFFE03020.toInt()                 // odstín ≈ 5°
        val out = ShinyPalette.transform(red, 120, satBoost = 1f)
        assertEquals((hueOf(red) + 120f) % 360f, hueOf(out), 1.5f)
        assertEquals(0xFF, (out ushr 24) and 0xFF)   // alfa zachována
    }

    @Test
    fun brightnessIsKept() {
        val c = 0xFF3A7BD5.toInt()
        val out = ShinyPalette.transform(c, 200)
        fun v(x: Int) = maxOf((x shr 16) and 0xFF, (x shr 8) and 0xFF, x and 0xFF)
        assertTrue(abs(v(c) - v(out)) <= 1)
    }

    @Test
    fun hsvRoundTripOnPrimaries() {
        assertEquals(0xFF0000, ShinyPalette.hsvToRgb(0f, 1f, 1f))
        assertEquals(0x00FF00, ShinyPalette.hsvToRgb(120f, 1f, 1f))
        assertEquals(0x0000FF, ShinyPalette.hsvToRgb(240f, 1f, 1f))
    }

    private fun abs(i: Int) = if (i < 0) -i else i
}
