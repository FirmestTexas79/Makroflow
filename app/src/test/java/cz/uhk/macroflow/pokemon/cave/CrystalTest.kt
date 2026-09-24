package cz.uhk.macroflow.pokemon.cave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrystalTest {

    @Test
    fun spriteIsSymmetricShapeWithOutline() {
        CrystalColor.entries.forEach { c ->
            val p = Crystals.pixels(c)
            assertEquals(Crystals.W * Crystals.H, p.size)
            for (y in 0 until Crystals.H) for (x in 0 until Crystals.W) {
                // tvar (ne barvy) je souměrný
                assertEquals((p[y * Crystals.W + x] != 0), (p[y * Crystals.W + Crystals.W - 1 - x] != 0))
            }
            assertTrue(p.count { it == c.palette[0] } > 20)
            assertTrue(p.any { it == c.palette[4] })                    // odlesk
        }
        assertNotEquals(Crystals.pixels(CrystalColor.BLUE).toList(), Crystals.pixels(CrystalColor.RED).toList())
    }

    @Test
    fun legendaryNeedsBothCrystals() {
        assertFalse(Crystals.legendaryUnlocked(emptySet()))
        assertFalse(Crystals.legendaryUnlocked(setOf(CrystalColor.BLUE)))
        assertTrue(Crystals.legendaryUnlocked(setOf(CrystalColor.BLUE, CrystalColor.RED)))
        assertNotEquals(CrystalColor.BLUE.prefKey, CrystalColor.RED.prefKey)
    }
}
