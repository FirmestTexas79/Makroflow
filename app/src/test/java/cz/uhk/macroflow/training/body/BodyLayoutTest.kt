package cz.uhk.macroflow.training.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BodyLayoutTest {

    private val both = listOf(BodySide.FRONT, BodySide.BACK)

    @Test
    fun singleFigureFillsHeightAndIsCentered() {
        val l = BodyLayout.compute(listOf(BodySide.FRONT), 1000, 1000, 0, 0, 0, 0, 0f)!!
        assertEquals(5f, l.scale, 1e-4f)          // 1000 / 200
        assertEquals(250f, l.offX, 1e-3f)         // (1000 - 500) / 2
        assertEquals(0f, l.offY, 1e-3f)
    }

    @Test
    fun twoFiguresFitWidth() {
        val l = BodyLayout.compute(both, 424, 2000, 0, 0, 0, 0, 0f)!!
        assertEquals(2f, l.scale, 1e-4f)          // 424 / 212
        assertEquals(0f, l.offX, 1e-3f)
        assertEquals(224f, l.originX(1), 1e-3f)   // (100 + 12) * 2
    }

    @Test
    fun touchMapsBackToBodyCoordinates() {
        val l = BodyLayout.compute(both, 424, 400, 0, 0, 0, 0, 0f)!!
        val front = l.toBody(100f, 200f)!!
        assertEquals(BodySide.FRONT, front.first)
        assertEquals(50f, front.second, 1e-3f)
        assertEquals(100f, front.third, 1e-3f)
        val back = l.toBody(224f + 20f, 10f)!!
        assertEquals(BodySide.BACK, back.first)
        assertEquals(10f, back.second, 1e-3f)
        assertNull(l.toBody(210f, 100f))          // mezera mezi postavami
        assertNull(l.toBody(100f, 401f))          // pod postavou
    }

    @Test
    fun paddingAndCaptionShrinkFigure() {
        val l = BodyLayout.compute(listOf(BodySide.BACK), 600, 440, 50, 20, 50, 0, 20f)!!
        assertEquals(2f, l.scale, 1e-4f)          // (440 - 20 - 20) / 200
        assertEquals(200f, l.offX, 1e-3f)         // 50 + (500 - 200) / 2
        assertEquals(20f, l.offY, 1e-3f)
        assertNotNull(l.toBody(300f, 220f))
    }

    @Test
    fun emptyOrTinyViewHasNoLayout() {
        assertNull(BodyLayout.compute(emptyList(), 100, 100, 0, 0, 0, 0, 0f))
        assertNull(BodyLayout.compute(both, 10, 10, 10, 0, 10, 0, 0f))
    }

    // ── výběr partie ───────────────────────────────────────────────────────

    /** Dva „svaly“ jako obdélníky: hrudník 0..10 × 0..10, biceps 8..20 × 0..10 (překryv 8..10). */
    private val rects = mapOf(
        Muscle.CHEST to floatArrayOf(0f, 0f, 10f, 10f),
        Muscle.BICEPS to floatArrayOf(8f, 0f, 20f, 10f)
    )
    private val order = listOf(Muscle.CHEST, Muscle.BICEPS)
    private val contains: (Muscle, Float, Float) -> Boolean = { m, x, y ->
        rects[m]?.let { r -> x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3] } ?: false
    }

    @Test
    fun exactHitPrefersTopmostMuscle() {
        assertEquals(Muscle.CHEST, MuscleHit.pick(2f, 5f, 0f, order, contains))
        assertEquals(Muscle.BICEPS, MuscleHit.pick(9f, 5f, 0f, order, contains))   // překryv → kreslený navrch
    }

    @Test
    fun nearMissIsForgivenWithinRadius() {
        assertEquals(Muscle.BICEPS, MuscleHit.pick(22f, 5f, 3f, order, contains))
        assertNull(MuscleHit.pick(30f, 5f, 3f, order, contains))
        assertNull(MuscleHit.pick(22f, 5f, 0f, order, contains))
    }

    @Test
    fun nearestRingWins() {
        // Bod 1 jednotku nad hrudníkem a 11 od bicepsu → hrudník.
        assertEquals(Muscle.CHEST, MuscleHit.pick(3f, -1f, 12f, order, contains))
    }
}
