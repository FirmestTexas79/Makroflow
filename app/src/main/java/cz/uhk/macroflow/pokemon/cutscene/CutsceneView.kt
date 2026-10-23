package cz.uhk.macroflow.pokemon.cutscene

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import cz.uhk.macroflow.R

/**
 * Cutscéna přes kód (docs/adr/0096) – stejný přístup jako úvodní let Makrosvětem (IntroFlyoverView):
 * kamera jede nad pixelovou mapou ([world], 1 px = 1 art px), přes ni se kreslí herci ([drawActors]),
 * nahoře a dole černé pruhy, titulky v dolním pruhu, prolínání z/do černé a „Přeskočit“.
 * Podtřída dodá mapu, délku, záběry kamery ([camera]) a titulky ([captions]).
 */
abstract class CutsceneView(context: Context) : View(context) {

    /** Titulek: od–do (ms) a text; ▒ se při kreslení přesýpá (mizející jména). */
    data class Caption(val from: Float, val to: Float, val text: String)

    /** Záběr kamery v čase t: střed (art px) a šířka výřezu (art px). */
    data class Shot(val t: Float, val cx: Float, val cy: Float, val viewW: Float)

    protected abstract val world: Bitmap
    protected abstract val totalMs: Float
    protected abstract val shots: List<Shot>
    protected abstract val captions: List<Caption>

    /** Mapa v čase t – cutscéna může v půlce přestřihnout na jinou lokaci (záběry pak platí pro ni). */
    protected open fun worldAt(t: Float): Bitmap = world

    /** Otřes kamery v čase t (px obrazovky). */
    protected open fun shake(t: Float): Pair<Float, Float> = 0f to 0f

    /** Kreslení herců nad mapou; [toX]/[toY] převádí art px na obrazovku, [scale] = px obrazovky na art px. */
    protected abstract fun drawActors(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, scale: Float)

    var onDone: (() -> Unit)? = null

    protected var time = 0f
        private set
    private var endAt = Float.MAX_VALUE
    private var animator: ValueAnimator? = null

    private val font = runCatching { ResourcesCompat.getFont(context, R.font.jersey_15) }.getOrNull()
    private val pixel = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = font }
    private val matrix = Matrix()
    private val skipRect = RectF()

    init {
        isClickable = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, totalMs).apply {
            duration = totalMs.toLong(); interpolator = LinearInterpolator()
            addUpdateListener {
                time = it.animatedValue as Float
                if (time >= minOf(totalMs, endAt + FADE_MS)) finish()
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun finish() {
        val done = onDone ?: return
        onDone = null
        animator?.cancel()
        done()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP && skipRect.contains(e.x, e.y) && endAt == Float.MAX_VALUE) {
            endAt = time
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
        return true
    }

    private fun ease(x: Float) = x * x * (3 - 2 * x)

    /** Kamera v čase t (plynulý přechod mezi záběry). */
    private fun cameraAt(t: Float): Shot {
        val i = shots.indexOfLast { it.t <= t }.coerceAtLeast(0)
        val a = shots[i]; val b = shots.getOrNull(i + 1) ?: return a
        val k = ease(((t - a.t) / (b.t - a.t)).coerceIn(0f, 1f))
        return Shot(t, a.cx + (b.cx - a.cx) * k, a.cy + (b.cy - a.cy) * k, a.viewW + (b.viewW - a.viewW) * k)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(Color.BLACK)
        if (w <= 0f) return
        val cam = cameraAt(time)
        val map = worldAt(time)
        val scale = w / cam.viewW
        val viewH = h / scale
        // svisle smí kamera přejet okraj mapy o výšku černého pruhu – přesah schovají pruhy, takže postava
        // u okraje mapy nezmizí pod titulky
        val over = viewH * BAR
        val left = (cam.cx - cam.viewW / 2).coerceIn(0f, (map.width - cam.viewW).coerceAtLeast(0f))
        val top = (cam.cy - viewH / 2).coerceIn(-over, (map.height - viewH + over).coerceAtLeast(-over))
        val (sx, sy) = shake(time)
        c.save(); c.translate(sx, sy)
        matrix.setScale(scale, scale); matrix.postTranslate(-left * scale, -top * scale)
        c.drawBitmap(map, matrix, pixel)
        drawActors(c, time, { (it - left) * scale }, { (it - top) * scale }, scale)
        c.restore()

        // černé pruhy (vjedou na začátku) a titulky v dolním
        val bar = h * BAR * ease((time / 900f).coerceIn(0f, 1f))
        fill.color = Color.BLACK; fill.alpha = 255
        c.drawRect(0f, 0f, w, bar, fill); c.drawRect(0f, h - bar, w, h, fill)
        captions.firstOrNull { time in it.from..it.to }?.let { cap ->
            val a = minOf(1f, (time - cap.from) / 400f, (cap.to - time) / 400f).coerceIn(0f, 1f)
            text.textSize = w * 0.052f; text.alpha = (a * 255).toInt()
            val s = scramble(cap.text)
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, text, (w * 0.86f).toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
            c.save(); c.translate(w * 0.07f, h - bar / 2 - layout.height / 2f); layout.draw(c); c.restore()
        }

        // prolínání: z černé na začátku, do černé na konci (nebo po „Přeskočit“)
        val end = minOf(totalMs, endAt + FADE_MS)
        val fadeIn = 1f - (time / FADE_MS).coerceIn(0f, 1f)
        val fadeOut = ((time - (end - FADE_MS)) / FADE_MS).coerceIn(0f, 1f)
        val black = maxOf(fadeIn, fadeOut)
        if (black > 0f) { fill.color = Color.BLACK; fill.alpha = (black * 255).toInt(); c.drawRect(0f, 0f, w, h, fill) }

        if (endAt == Float.MAX_VALUE && time < totalMs - FADE_MS) skipButton(c, w)
    }

    /** ▒ v titulku se každých 90 ms přesype – jméno, které se nedá dočíst. */
    private fun scramble(s: String): String {
        if ('▒' !in s) return s
        val seed = (time / 90f).toInt()
        return s.mapIndexed { i, ch -> if (ch == '▒') GLITCH[(seed * 7 + i * 13) % GLITCH.length] else ch }.joinToString("")
    }

    private fun skipButton(c: Canvas, w: Float) {
        text.textSize = w * 0.04f; text.alpha = 200
        val label = "Přeskočit >"
        val tw = text.measureText(label); val pad = w * 0.03f
        val top = height * BAR + pad
        skipRect.set(w - tw - pad * 3, top, w - pad, top + text.textSize + pad * 1.4f)
        fill.color = 0xFF0A0F06.toInt(); fill.alpha = 140
        c.drawRoundRect(skipRect, skipRect.height() / 2, skipRect.height() / 2, fill)
        c.drawText(label, skipRect.left + pad, skipRect.bottom - pad * 0.95f, text)
    }

    companion object {
        const val FADE_MS = 900f
        /** Výška černého pruhu nahoře i dole (podíl výšky obrazovky). */
        const val BAR = 0.12f
        private const val GLITCH = "░▒▓█▌▐■□"

        /** Přehraje cutscénu přes [root]; [done] se zavolá po skončení (i po přeskočení). */
        fun play(root: ViewGroup, view: CutsceneView, done: () -> Unit = {}) {
            view.elevation = 500f
            view.onDone = { root.removeView(view); done() }
            root.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }
}
