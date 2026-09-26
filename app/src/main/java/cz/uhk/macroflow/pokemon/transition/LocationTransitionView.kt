package cz.uhk.macroflow.pokemon.transition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import kotlin.math.ceil

/**
 * Přehrává [TransitionScene] v nízkém rozlišení (~120 px na šířku) zvětšené bez vyhlazení
 * (docs/adr/0042). Průhledné pixely nechají prosvítat mapu pod překryvem.
 * [onCovered] přijde jednou, když je obrazovka celá zakrytá (výměna mapy), [onFinished] na konci.
 */
class LocationTransitionView(context: Context, private val biome: String) : View(context) {

    var onCovered: (() -> Unit)? = null
    var onFinished: (() -> Unit)? = null

    private var scene: TransitionScene? = null
    private var pixels = IntArray(0)
    private var buffer: Bitmap? = null
    private val dst = Rect()
    private val blit = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private var startAt = -1L
    private var covered = false
    private var finished = false
    /** Dokud se nová mapa nevykreslí, scéna zůstane zakrytá ([release]). */
    private var released = false
    private var heldSince = -1L

    /** Nová mapa je vykreslená – scéna se může otevřít. */
    fun release() {
        if (released) return
        released = true
        // čas, který scéna čekala zavřená, se odečte – otevírání začne od začátku
        if (heldSince >= 0) startAt += SystemClock.uptimeMillis() - heldSince
        postInvalidateOnAnimation()
    }

    fun start() {
        startAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        val scale = (w / TARGET_WIDTH).coerceAtLeast(1)
        val lw = ceil(w / scale.toFloat()).toInt()
        val lh = ceil(h / scale.toFloat()).toInt()
        scene = LocationTransitions.forBiome(biome, lw, lh)
        pixels = IntArray(lw * lh)
        buffer?.recycle()
        buffer = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        dst.set(0, 0, lw * scale, lh * scale)
    }

    override fun onDraw(canvas: Canvas) {
        val s = scene; val b = buffer
        if (s == null || b == null || startAt < 0) {
            // scéna pro tuhle lokaci není → rovnou výměna a konec
            if (s == null && startAt >= 0 && !finished) { finished = true; covered = true; post { onCovered?.invoke(); onFinished?.invoke() } }
            return
        }
        var t = (SystemClock.uptimeMillis() - startAt).coerceAtMost(s.end)
        if (!released && t >= s.openAt) {
            if (heldSince < 0) heldSince = SystemClock.uptimeMillis() - (t - s.openAt)
            t = s.openAt
            // pojistka: nejdéle 2,5 s čekání
            if (SystemClock.uptimeMillis() - heldSince > 2500) release()
        }
        s.render(t, pixels)
        b.setPixels(pixels, 0, s.w, 0, 0, s.w, s.h)
        canvas.drawBitmap(b, null, dst, blit)

        if (!covered && t >= s.coveredAt) { covered = true; post { onCovered?.invoke() } }
        if (!finished && t >= s.end) { finished = true; post { onFinished?.invoke() } }
        if ((t < s.end || !released) && isAttachedToWindow) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        buffer?.recycle(); buffer = null
    }

    private companion object {
        const val TARGET_WIDTH = 120
    }
}
