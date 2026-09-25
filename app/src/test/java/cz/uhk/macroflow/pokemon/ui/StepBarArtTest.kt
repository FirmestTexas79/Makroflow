package cz.uhk.macroflow.pokemon.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepBarArtTest {

    private fun green(c: Int) = ((c shr 8) and 0xFF) > ((c shr 16) and 0xFF) && ((c shr 8) and 0xFF) > (c and 0xFF)

    @Test
    fun fillColumns() {
        assertEquals(0, StepBarArt.fillColumns(0f))
        assertEquals(1, StepBarArt.fillColumns(0.001f))        // i malý postup je vidět
        assertEquals(StepBarArt.INNER_W, StepBarArt.fillColumns(1f))
        assertEquals(StepBarArt.INNER_W / 2, StepBarArt.fillColumns(0.5f))
    }

    @Test
    fun barFillIsGreenOnlyUpToProgress() {
        val w = StepBarArt.BAR_W
        val px = StepBarArt.bar(20)
        val midY = (StepBarArt.INNER_TOP + StepBarArt.INNER_BOTTOM) / 2
        assertTrue(green(px[midY * w + 10]))
        assertTrue(!green(px[midY * w + 40]))
        // zaoblené rohy průhledné
        assertEquals(0, px[0]); assertEquals(0, px[w - 1]); assertEquals(0, px[(StepBarArt.H - 1) * w])
        assertEquals(w * StepBarArt.H, px.size)
    }

    @Test
    fun checkHasGreenTick() {
        val w = StepBarArt.SQUARE
        val px = StepBarArt.check()
        assertEquals(w * StepBarArt.H, px.size)
        assertTrue((0 until px.size).count { green(px[it]) } >= 16)
    }
}
