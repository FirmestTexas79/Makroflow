package cz.uhk.macroflow.pokemon.cave

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
 * Přehrává [CaveEncounterScene] (intro setkání v jeskyni): nízké rozlišení (~120 px na šířku)
 * a zvětšuje se celočíselným násobkem bez vyhlazení → ostrý pixel art na každém displeji.
 */
class CaveEncounterView(context: Context) : View(context) {

    /** Zavolá se jednou, když má přijít záblesk a souboj (úlomky ještě dolétají pod zábleskem). */
    var onReveal: (() -> Unit)? = null

    private var scene: CaveEncounterScene? = null
    private var pixels = IntArray(0)
    private var buffer: Bitmap? = null
    private val dst = Rect()
    private val blit = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }

    private var startAt = -1L
    /** Posun času při přeskočení (klepnutí skočí rovnou na výpad očí). */
    private var skipped = 0L
    private var revealed = false

    fun start() {
        startAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    fun skip() {
        if (startAt < 0) start()
        val t = elapsed()
        if (t < CaveEncounter.LUNGE_START) skipped += CaveEncounter.LUNGE_START - t
    }

    private fun elapsed(): Long = SystemClock.uptimeMillis() - startAt + skipped

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        val scale = (w / TARGET_WIDTH).coerceAtLeast(1)
        val lw = ceil(w / scale.toFloat()).toInt()
        val lh = ceil(h / scale.toFloat()).toInt()
        scene = CaveEncounterScene(lw, lh)
        pixels = IntArray(lw * lh)
        buffer?.recycle()
        buffer = Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888)
        // Celočíselný násobek; přesah o pár pixelů za okraj se ořízne
        dst.set(0, 0, lw * scale, lh * scale)
    }

    override fun onDraw(canvas: Canvas) {
        val s = scene; val b = buffer
        if (s == null || b == null || startAt < 0) { canvas.drawColor(Color.BLACK); return }
        val t = elapsed().coerceAtMost(CaveEncounter.END)
        s.render(t, pixels)
        b.setPixels(pixels, 0, s.w, 0, 0, s.w, s.h)
        canvas.drawBitmap(b, null, dst, blit)

        if (!revealed && t >= CaveEncounter.REVEAL_AT) {
            revealed = true
            post { onReveal?.invoke() }
        }
        if (t < CaveEncounter.END && isAttachedToWindow) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        buffer?.recycle(); buffer = null
    }

    companion object {
        /** Cílová šířka scény v „herních“ pixelech. */
        private const val TARGET_WIDTH = 120
    }
}
