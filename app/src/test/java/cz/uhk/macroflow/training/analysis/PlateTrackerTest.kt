package cz.uhk.macroflow.training.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateTrackerTest {

    private fun plate(cx: Float, cy: Float, r: Float = 60f, id: Int? = 7) =
        PlateTracker.Detection(cx - r, cy - r, cx + r, cy + r, id)

    private val person = PlateTracker.Detection(100f, 50f, 300f, 700f, 1)  // vysoký obdélník

    @Test fun `vybere kotouč, ne člověka`() {
        val t = PlateTracker()
        val tr = t.update(listOf(person, plate(400f, 300f)), 0)!!
        assertEquals(400f, tr.x, 1f)
    }

    @Test fun `natočený kotouč (elipsa) se nezahodí`() {
        val t = PlateTracker()
        val ellipse = PlateTracker.Detection(340f, 250f, 460f, 350f, null) // poměr 1,2
        assertNotNull(t.update(listOf(ellipse), 0))
    }

    @Test fun `neskočí na jiný kulatý předmět daleko od kotouče`() {
        val t = PlateTracker()
        t.update(listOf(plate(400f, 300f, id = null)), 0)
        // kotouč na chvíli zmizí, v záběru je jiný kulatý předmět 500 px daleko
        val tr = t.update(listOf(plate(900f, 700f, 55f, id = null)), 33)
        assertTrue(tr == null || tr.x < 500f)
    }

    @Test fun `krátký výpadek - doběhne po predikci, dlouhý - ztráta`() {
        val t = PlateTracker()
        var time = 0L
        for (i in 0 until 10) { t.update(listOf(plate(400f, 300f + i * 10f)), time); time += 33 }
        val coast = t.update(emptyList(), time)!!
        assertTrue(!coast.measured && coast.y > 390f)
        assertNull(t.update(emptyList(), time + 1000))
    }

    @Test fun `drží zámek podle trackingId i když je jiný objekt blíž predikci`() {
        val t = PlateTracker()
        t.update(listOf(plate(400f, 300f, id = 7)), 0)
        val tr = t.update(listOf(plate(402f, 305f, id = 99), plate(440f, 330f, id = 7)), 33)!!
        assertTrue(tr.x > 405f)
    }

    @Test fun `ťuknutím se přezamkne na jiný kotouč`() {
        val t = PlateTracker()
        val dets = listOf(plate(200f, 300f, id = 1), plate(600f, 300f, id = 2))
        t.update(dets, 0)
        assertTrue(t.lockAt(610f, 290f, dets, 10))
        assertEquals(2, t.lockedId)
    }
}
