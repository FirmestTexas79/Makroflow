package cz.uhk.macroflow.pokemon.cave

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaveTransitionTest {

    private fun frame(scene: CaveTransitionScene, t: Long) = IntArray(scene.w * scene.h).also { scene.render(t, it) }

    private fun mean(px: IntArray, shift: Int) = px.map { (it shr shift) and 0xFF }.average()

    @Test
    fun mouthCoversTheScreenBeforeTheMapSwap() {
        for ((w, h) in listOf(120 to 260, 120 to 200, 160 to 160)) {
            val s = CaveTransitionScene(w, h, exiting = false)
            assertFalse(s.isCovered(0))
            assertTrue("$w x $h", s.isCovered(CaveTransition.COVERED_AT))
        }
    }

    @Test
    fun entryIsDarkBlueWithMoss() {
        val px = frame(CaveTransitionScene(120, 260, exiting = false), 300)
        assertTrue(mean(px, 0) > mean(px, 16))                         // modrá převládá nad červenou
        val moss = px.count { val r = (it shr 16) and 0xFF; val g = (it shr 8) and 0xFF; val b = it and 0xFF; g > r + 20 && g > b }
        assertTrue("moss=$moss", moss > 100)
    }

    @Test
    fun entryEndsInDarknessExitInDaylight() {
        val dark = frame(CaveTransitionScene(120, 260, exiting = false), CaveTransition.END)
        val light = frame(CaveTransitionScene(120, 260, exiting = true), CaveTransition.END)
        assertTrue(mean(dark, 8) < 40)
        assertTrue(mean(light, 8) > 200)
    }

    @Test
    fun renderIsDeterministicAndOpaque() {
        val a = frame(CaveTransitionScene(90, 180, false), 640)
        val b = frame(CaveTransitionScene(90, 180, false), 640)
        assertArrayEquals(a, b)
        assertEquals(0, a.count { (it ushr 24) != 0xFF })
    }
}

class MineDescentTest {
    private fun frame(s: MineDescentScene, t: Long) = IntArray(s.w * s.h).also { s.render(t, it) }
    private fun mean(px: IntArray, shift: Int) = px.map { (it shr shift) and 0xFF }.average()

    @Test
    fun descentIsOpaqueFromTheFirstFrame() {
        val s = MineDescentScene(120, 260)
        for (t in listOf(0L, MineDescent.COVERED_AT, MineDescent.END)) assertEquals(0, frame(s, t).count { (it ushr 24) != 0xFF })
        assertTrue(MineDescent.COVERED_AT < MineDescent.END)
    }

    @Test
    fun heatGrowsWithDepth() {
        val s = MineDescentScene(120, 260)
        val early = frame(s, 100); val late = frame(s, MineDescent.END - 400)
        // žár z hloubky: červená roste víc než modrá
        assertTrue(mean(late, 16) - mean(late, 0) > mean(early, 16) - mean(early, 0))
        assertTrue(s.travelled(1000) > s.travelled(500))
    }

    @Test
    fun shaftHasTimberRailsAndCrystals() {
        val px = frame(MineDescentScene(120, 260), 500)
        fun rgb(c: Int) = Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        val rails = px.count { val (r, g, b) = rgb(it); r > 110 && b > 130 && kotlin.math.abs(r - g) < 24 }
        val timber = px.count { val (r, g, b) = rgb(it); r > 80 && r > g + 25 && g > b + 15 }
        val crystals = px.count { val (r, g, b) = rgb(it); b > 150 && r > 90 && g < r }
        assertTrue("rails=$rails", rails > 200)
        assertTrue("timber=$timber", timber > 1000)
        assertTrue("crystals=$crystals", crystals > 0)
    }
}
