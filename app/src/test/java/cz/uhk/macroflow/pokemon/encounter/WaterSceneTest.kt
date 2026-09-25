package cz.uhk.macroflow.pokemon.encounter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterSceneTest {

    @Test
    fun timelineOrder() {
        with(WaterIntro) {
            assertTrue(FADE_IN_END < RIPPLE_START && RIPPLE_START < TUG_1 && TUG_1 < TUG_2 && TUG_2 < PULL_UNDER)
            assertTrue(PULL_UNDER < BURST_START && BURST_START < BURST_PEAK && BURST_PEAK <= REVEAL_AT && REVEAL_AT < END)
        }
    }

    @Test
    fun bobberTugsAndSinks() {
        val calm = WaterIntro.bobberOffset(900)
        val tug = WaterIntro.bobberOffset(WaterIntro.TUG_1 + WaterIntro.TUG_LEN / 2)
        assertTrue("$calm < $tug", tug > calm + 1.5f)
        assertTrue(WaterIntro.bobberVisible(WaterIntro.PULL_UNDER))
        assertFalse(WaterIntro.bobberVisible(WaterIntro.BURST_START + 50))
    }

    @Test
    fun ripplesAndShadow() {
        assertTrue(WaterIntro.ripples(200).isEmpty())
        assertTrue(WaterIntro.ripples(900).isNotEmpty())
        WaterIntro.ripples(1500).forEach { (p, a) -> assertTrue(p in 0f..1f && a in 0f..1f) }
        assertEquals(0f, WaterIntro.shadow(500).third, 0f)
        assertTrue(WaterIntro.shadow(1200).third > 0f)
        assertEquals(0f, WaterIntro.shadow(WaterIntro.BURST_START).third, 0f)
    }

    @Test
    fun columnRisesAtBurst() {
        assertEquals(0f, WaterIntro.column(WaterIntro.BURST_START - 1), 0f)
        assertEquals(1f, WaterIntro.column(WaterIntro.BURST_PEAK), 1e-3f)
        assertTrue(WaterIntro.column(WaterIntro.END) in 0.5f..0.8f)
        assertTrue(WaterIntro.shakeAmplitude(WaterIntro.BURST_START + 10) > 0f)
        assertEquals(0f, WaterIntro.shakeAmplitude(1000), 0f)
    }

    @Test
    fun renderIsDeterministicAndShowsScene() {
        val s = WaterScene(128, 286)
        val a = IntArray(s.w * s.h); val b = IntArray(s.w * s.h)
        s.render(1000, a); WaterScene(128, 286).render(1000, b)
        assertArrayEquals(a, b)
        // začátek je skoro černý, uprostřed intra světlo
        val dark = IntArray(s.w * s.h).also { s.render(0, it) }
        fun lum(c: Int) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
        assertTrue(dark.average { lum(it) } < 10)
        assertTrue(a.average { lum(it) } > 150)
        // splávek je červený, při vytrysknutí je u splávku bílý sloup
        assertTrue((-6..0).any { dy -> val c = a[(s.bobberY + dy) * s.w + s.bobberX]; ((c shr 16) and 0xFF) > 180 && (c and 0xFF) < 90 })
        val burst = IntArray(s.w * s.h).also { s.render(WaterIntro.BURST_PEAK, it) }
        assertTrue(lum(burst[(s.bobberY - 30) * s.w + s.bobberX]) > 600)
    }

    private fun IntArray.average(f: (Int) -> Int): Double = sumOf { f(it).toLong() }.toDouble() / size
}
