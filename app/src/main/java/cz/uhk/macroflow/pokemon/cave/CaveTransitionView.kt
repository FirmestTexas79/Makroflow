package cz.uhk.macroflow.pokemon.cave

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import kotlin.math.ceil

/**
 * Přehrává [CaveTransitionScene] v nízkém rozlišení (~120 px na šířku), zvětšené celočíselným
 * násobkem bez vyhlazení. [onCovered] přijde jednou, když ústí pokryje celou obrazovku
 * (pod překryvem se vymění mapa), [onFinished] na konci scény.
 */
class CaveTransitionView(context: Context, private val exiting: Boolean) : View(context) {

    var onCovered: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null

    private var scene: CaveTransitionScene? = null
    private var pixels = IntArray(0)
    private var buffer: Bitmap? = null
    private val dst = Rect()
    private val blit = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private var startAt = -1L
    private var covered = false
    private var finished = false

    fun start() {
        startAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        val scale = (w / TARGET_WIDTH).coerceAtLeast(1)
        val lw = ceil(w / scale.toFloat()).toInt()
        val lh = ceil(h / scale.toFloat()).toInt()
        scene = CaveTransitionScene(lw, lh, exiting)
        pixels = IntArray(lw * lh)
        buffer?.recycle()
        buffer = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        dst.set(0, 0, lw * scale, lh * scale)
    }

    override fun onDraw(canvas: Canvas) {
        val s = scene; val b = buffer
        if (s == null || b == null || startAt < 0) { canvas.drawColor(CaveTransition.VOID); return }
        val t = (SystemClock.uptimeMillis() - startAt).coerceAtMost(CaveTransition.END)
        s.render(t, pixels)
        b.setPixels(pixels, 0, s.w, 0, 0, s.w, s.h)
        canvas.drawBitmap(b, null, dst, blit)

        if (!covered && t >= CaveTransition.COVERED_AT) { covered = true; post { onCovered?.invoke() } }
        if (!finished && t >= CaveTransition.END) { finished = true; post { onFinished?.invoke() } }
        if (t < CaveTransition.END && isAttachedToWindow) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        buffer?.recycle(); buffer = null
    }

    private companion object {
        const val TARGET_WIDTH = 120
    }
}
