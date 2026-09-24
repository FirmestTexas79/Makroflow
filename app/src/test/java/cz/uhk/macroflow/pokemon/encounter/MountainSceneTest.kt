package cz.uhk.macroflow.pokemon.encounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MountainSceneTest {

    private val w = 120; private val h = 260

    private fun frame(scene: MountainScene, t: Long) = IntArray(w * h).also { scene.render(t, it) }

    @Test
    fun startsFromBlack() {
        val f = frame(MountainScene(w, h), 0)
        assertTrue(f.all { (it and 0xFFFFFF) == 0 })
    }

    @Test
    fun renderIsPureFunctionOfTime() {
        // Stejný čas = stejný snímek, i když mezitím vykreslíme jiné (přeskočení = skok v čase)
        val s = MountainScene(w, h)
        val a = frame(s, 1900)
        frame(s, 500); frame(s, 2400)
        assertTrue(a.contentEquals(frame(s, 1900)))
        assertTrue(a.contentEquals(frame(MountainScene(w, h), 1900)))
    }

    @Test
    fun wholeTimelineRendersInAnySize() {
        for ((ww, hh) in listOf(120 to 260, 120 to 200, 96 to 214, 160 to 144)) {
            val s = MountainScene(ww, hh)
            val out = IntArray(ww * hh)
            var t = 0L
            while (t <= MountainIntro.END) { s.render(t, out); t += 16 }
            assertTrue(out.all { (it ushr 24) == 0xFF })   // žádné průhledné díry
        }
    }

    @Test
    fun boulderSplitsApart() {
        val s = MountainScene(w, h)
        val cx = w / 2
        val rowY = s.groundY - s.bh / 2
        val before = frame(s, MountainIntro.CRACK_START)
        val after = frame(s, MountainIntro.SPLIT_END)
        // Před rozpůlením je uprostřed kámen, po něm tam už není (jen prach/písek)
        assertNotEquals(before[rowY * w + cx - 6], after[rowY * w + cx - 6])
    }

    @Test
    fun sunAndSkyAreVisibleAfterFadeIn() {
        val f = frame(MountainScene(w, h), MountainIntro.FRONT_RISE_END)
        assertEquals(0xFF2E1C26.toInt(), f[0])   // nejtmavší pás nebe nahoře
    }
}
