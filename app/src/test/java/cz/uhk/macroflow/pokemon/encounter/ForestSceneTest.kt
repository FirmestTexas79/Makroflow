package cz.uhk.macroflow.pokemon.encounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestSceneTest {
    @Test fun timelineIsOrdered() {
        val t = listOf(ForestIntro.FADE_IN_END, ForestIntro.RUSTLE_1, ForestIntro.EYES_1, ForestIntro.RUSTLE_2,
            ForestIntro.EYES_2, ForestIntro.SHAKE_START, ForestIntro.BURST_START, ForestIntro.BURST_PEAK,
            ForestIntro.REVEAL_AT, ForestIntro.END)
        assertEquals(t.sorted(), t)
    }

    @Test fun eyesGlowOnlyBeforeBurst() {
        assertEquals(0f, ForestIntro.eyes(500).first, 0f)
        assertTrue(ForestIntro.eyes(960).first > 0.5f)
        assertTrue(ForestIntro.eyes(ForestIntro.EYES_2 + 200).second > 1f)
        assertEquals(0f, ForestIntro.eyes(ForestIntro.BURST_START + 10).first, 0f)
        assertEquals(0f, ForestIntro.burst(ForestIntro.BURST_START - 1), 0f)
        assertEquals(1f, ForestIntro.burst(ForestIntro.END), 0f)
    }

    @Test fun rendersEveryFrameWithoutTransparentPixels() {
        val s = ForestScene(128, 286)
        val px = IntArray(128 * 286)
        for (t in 0L..ForestIntro.END step 97) {
            s.render(t, px)
            assertTrue("t=$t", px.all { (it ushr 24) == 0xFF })
        }
    }
}
