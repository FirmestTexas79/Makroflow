package cz.uhk.macroflow.pokemon.encounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MountainIntroTest {

    @Test
    fun timelineIsOrdered() {
        with(MountainIntro) {
            val order = listOf(SKY_IN_END, BACK_RISE_END, FRONT_RISE_END, RUMBLE_START, CRACK_START, SPLIT_START, REVEAL_AT, SPLIT_END, END)
            assertEquals(order.sorted(), order)
            assertTrue(BACK_RISE_START < FRONT_RISE_START)
        }
    }

    @Test
    fun progressClamps() {
        assertEquals(0f, MountainIntro.progress(0, 100, 200), 0f)
        assertEquals(0.5f, MountainIntro.progress(150, 100, 200), 1e-6f)
        assertEquals(1f, MountainIntro.progress(999, 100, 200), 0f)
    }

    @Test
    fun easingEndpoints() {
        assertEquals(0f, MountainIntro.easeOutCubic(0f), 1e-6f)
        assertEquals(1f, MountainIntro.easeOutCubic(1f), 1e-6f)
        assertTrue(MountainIntro.easeOutCubic(0.5f) > 0.5f)   // rychlý rozjezd, pomalý dojezd
    }

    @Test
    fun shakeRumblesThenHitsThenFades() {
        with(MountainIntro) {
            assertEquals(0f, shakeAmplitude(500), 0f)
            assertEquals(1f, shakeAmplitude(RUMBLE_START + 10), 0f)
            assertTrue(shakeAmplitude(SPLIT_START + 10) > 2.5f)
            assertEquals(0f, shakeAmplitude(END), 0f)
        }
    }

    @Test
    fun ridgeIsDeterministicAndBounded() {
        val a = MountainIntro.ridge(seed = 7, width = 120, amplitude = 30)
        val b = MountainIntro.ridge(seed = 7, width = 120, amplitude = 30)
        assertTrue(a.contentEquals(b))
        assertEquals(120, a.size)
        assertTrue(a.all { it in 0..30 })
        // Využije celý rozsah (normalizace) a není to rovná čára
        assertEquals(0, a.min()); assertTrue(a.max() >= 29)
        assertTrue(!MountainIntro.ridge(seed = 8, width = 120, amplitude = 30).contentEquals(a))
    }

    @Test
    fun ridgeIsContinuousEnough() {
        // Sousední sloupce se neliší o víc než pár pixelů – hřeben, ne šum
        val r = MountainIntro.ridge(seed = 13, width = 120, amplitude = 26)
        assertTrue(r.toList().zipWithNext().all { (x, y) -> kotlin.math.abs(x - y) <= 6 })
    }

    @Test
    fun crackStaysNarrow() {
        assertTrue((0..60).all { kotlin.math.abs(MountainIntro.crackOffset(it)) <= 3 })
    }
}
