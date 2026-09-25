package cz.uhk.macroflow.pokemon.encounter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import kotlin.math.ceil

/**
 * Obecný přehrávač intra v pixel artu (docs/adr/0030): scéna se kreslí v nízkém rozlišení
 * (~[targetWidth] px na šířku) a zvětšuje celočíselným násobkem bez vyhlazení.
 * [revealAt] = čas záblesku a souboje, [end] = konec, [skipTo] = kam skočí klepnutí.
 */
class PixelEncounterView(
    context: Context,
    private val factory: (w: Int, h: Int) -> PixelScene,
    private val revealAt: Long,
    private val end: Long,
    private val skipTo: Long,
    private val targetWidth: Int = 128
) : View(context) {

    var onReveal: (() -> Unit)? = null

    private var scene: PixelScene? = null
    private var pixels = IntArray(0)
    private var buffer: Bitmap? = null
    private val dst = Rect()
    private val blit = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }

    private var startAt = -1L
    private var skipped = 0L
    private var revealed = false

    fun start() {
        startAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    fun skip() {
        if (startAt < 0) start()
        val t = elapsed()
        if (t < skipTo) skipped += skipTo - t
    }

    private fun elapsed(): Long = SystemClock.uptimeMillis() - startAt + skipped

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        val scale = (w / targetWidth).coerceAtLeast(1)
        val lw = ceil(w / scale.toFloat()).toInt()
        val lh = ceil(h / scale.toFloat()).toInt()
        scene = factory(lw, lh)
        pixels = IntArray(lw * lh)
        buffer?.recycle()
        buffer = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        dst.set(0, 0, lw * scale, lh * scale)
    }

    override fun onDraw(canvas: Canvas) {
        val s = scene; val b = buffer
        if (s == null || b == null || startAt < 0) { canvas.drawColor(Color.BLACK); return }
        val t = elapsed().coerceAtMost(end)
        s.render(t, pixels)
        b.setPixels(pixels, 0, s.width, 0, 0, s.width, s.height)
        canvas.drawBitmap(b, null, dst, blit)
        if (!revealed && t >= revealAt) {
            revealed = true
            post { onReveal?.invoke() }
        }
        if (t < end && isAttachedToWindow) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        buffer?.recycle(); buffer = null
    }
}
