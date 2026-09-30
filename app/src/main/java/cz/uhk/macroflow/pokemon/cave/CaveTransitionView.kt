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
class CaveTransitionView(context: Context, private val exiting: Boolean, private val descent: Boolean = false) : View(context) {

    var onCovered: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null

    private var scene: CaveTransitionScene? = null
    /** Sestup do Dolů (docs/adr/0049) – místo ústí jeskyně let štolou do žáru. */
    private var mine: MineDescentScene? = null
    private val coveredAt get() = if (descent) MineDescent.COVERED_AT else CaveTransition.COVERED_AT
    private val endAt get() = if (descent) MineDescent.END else CaveTransition.END
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
        if (descent) mine = MineDescentScene(lw, lh) else scene = CaveTransitionScene(lw, lh, exiting)
        pixels = IntArray(lw * lh)
        buffer?.recycle()
        buffer = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        dst.set(0, 0, lw * scale, lh * scale)
    }

    override fun onDraw(canvas: Canvas) {
        val b = buffer
        if ((scene == null && mine == null) || b == null || startAt < 0) { canvas.drawColor(CaveTransition.VOID); return }
        val t = (SystemClock.uptimeMillis() - startAt).coerceAtMost(endAt)
        mine?.render(t, pixels) ?: scene?.render(t, pixels)
        b.setPixels(pixels, 0, b.width, 0, 0, b.width, b.height)
        canvas.drawBitmap(b, null, dst, blit)

        if (!covered && t >= coveredAt) { covered = true; post { onCovered?.invoke() } }
        if (!finished && t >= endAt) { finished = true; post { onFinished?.invoke() } }
        if (t < endAt && isAttachedToWindow) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        buffer?.recycle(); buffer = null
    }

    private companion object {
        const val TARGET_WIDTH = 120
    }
}
