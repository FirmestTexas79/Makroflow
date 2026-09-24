package cz.uhk.macroflow.pokemon.cave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaveEncounterTest {

    private val scene = CaveEncounterScene(120, 200)
    private fun frame(t: Long) = IntArray(120 * 200).also { scene.render(t, it) }

    private fun isEye(c: Int): Boolean {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return r > 230 && g > 220 && b > 150 && b < 200
    }

    @Test
    fun timelineIsOrdered() {
        with(CaveEncounter) {
            assertTrue(FADE_IN_END < BATS_END && BATS_START < EYES_OPEN)
            assertTrue(EYES_OPEN < BLINK_AT && BLINK_AT < LUNGE_START && LUNGE_START < REVEAL_AT && REVEAL_AT < END)
        }
    }

    @Test
    fun eyesOpenOnlyAfterBatsAndBlinkOnce() {
        assertEquals(0, frame(700).count(::isEye))
        assertTrue(frame(1350).count(::isEye) > 0)
        assertEquals(0f, CaveEncounter.eyeOpen(CaveEncounter.BLINK_AT + 70), 0.01f)       // zavřené při mrknutí
        assertEquals(1f, CaveEncounter.eyeOpen(CaveEncounter.LUNGE_START), 0f)
    }

    @Test
    fun eyesGrowDuringLungeAndScreenShakes() {
        assertTrue(frame(1880).count(::isEye) > frame(1450).count(::isEye) * 3)
        assertEquals(0, CaveEncounter.shake(1000))
        assertTrue(CaveEncounter.shake(1700) != 0)
    }

    @Test
    fun batsFlyOutEarly() {
        val bat = frame(700).count { it == CaveEncounter.BAT }
        assertTrue("bat=$bat", bat > 5)
        assertEquals(0, frame(1500).count { it == CaveEncounter.BAT })
    }

    @Test
    fun caveIsDarkBlueNotDesertAndFadesIn() {
        val f = frame(900)
        val r = f.map { (it shr 16) and 0xFF }.average(); val b = f.map { it and 0xFF }.average()
        assertTrue(b > r * 1.3)
        val start = frame(0).map { (it shr 8) and 0xFF }.average()
        assertTrue(start < f.map { (it shr 8) and 0xFF }.average())
    }
}
