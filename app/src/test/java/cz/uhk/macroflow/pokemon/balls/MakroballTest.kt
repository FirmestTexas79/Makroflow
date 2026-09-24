package cz.uhk.macroflow.pokemon.balls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MakroballTest {

    @Test
    fun multipliersAreOneOneAndHalfTwo() {
        assertEquals(1.0f, Makroball.MAKRO.catchMultiplier, 0f)
        assertEquals(1.5f, Makroball.PROTEIN.catchMultiplier, 0f)
        assertEquals(2.0f, Makroball.KREATIN.catchMultiplier, 0f)
    }

    @Test
    fun idsStayCompatibleWithSavedItems() {
        assertEquals(Makroball.MAKRO, Makroball.from("poke_ball"))
        assertEquals(Makroball.PROTEIN, Makroball.from("great_ball"))
        assertEquals(Makroball.KREATIN, Makroball.from("ultra_ball"))
        assertNull(Makroball.from("lure_lamp"))
    }

    @Test
    fun shapeIsTwelveByTwelveAndSymmetricOutline() {
        assertEquals(Makroball.SIZE, Makroball.SHAPE.size)
        Makroball.SHAPE.forEach { assertEquals(Makroball.SIZE, it.length) }
        // Obrys koule je souměrný (zrcadlo podle svislé osy, bez ohledu na barvu uvnitř)
        Makroball.SHAPE.forEach { row -> assertEquals(row.map { it == '.' }, row.reversed().map { it == '.' }) }
    }

    @Test
    fun spritesDifferAndHaveTransparentCorners() {
        val sprites = Makroball.entries.map { it.pixels.toList() }
        assertEquals(3, sprites.distinct().size)
        Makroball.entries.forEach { b ->
            assertEquals(0, b.pixels[0])
            assertEquals(0, b.pixels[Makroball.SIZE * Makroball.SIZE - 1])
            assertTrue(b.pixels.count { it != 0 } > 100)
        }
    }

    @Test
    fun betterBallNeverWorse() {
        for (hp in listOf(1.0, 0.7, 0.3, 0.05)) {
            val m = Makroball.catchValue(hp, 1f, Makroball.MAKRO)
            val p = Makroball.catchValue(hp, 1f, Makroball.PROTEIN)
            val k = Makroball.catchValue(hp, 1f, Makroball.KREATIN)
            assertTrue("hp=$hp", m <= p && p <= k)
        }
    }

    @Test
    fun catchValueMatchesFormula() {
        // Plné HP: základ 20 → ×2 = 40
        assertEquals(20, Makroball.catchValue(1.0, 1f, Makroball.MAKRO))
        assertEquals(40, Makroball.catchValue(1.0, 1f, Makroball.KREATIN))
        // Téměř bez HP se strop 255 nepřekročí
        assertEquals(255, Makroball.catchValue(0.0, 2f, Makroball.KREATIN))
    }
}
