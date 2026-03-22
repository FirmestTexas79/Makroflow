package cz.uhk.macroflow

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

/**
 * WaterPillView — živý glassmorphism pill pro dashboard.
 *
 * - Plynoucí sinusoidní vlna s výškou dle % splnění
 * - Klidový stav: pomalá táhlá vlna
 * - Dehydratace (4h+ bez pití): trhané, nervózní vlny
 * - 100% achievement: zlatý gradient + stoupající bublinky
 */
class WaterPillView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Stav ──────────────────────────────────────────────────────────
    var progressFraction: Float = 0f   // 0.0 – 1.0
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    var isDehydrated: Boolean = false  // 4h+ bez pití
        set(value) { field = value; updateAnimatorSpeed(); invalidate() }

    var goalReached: Boolean = false
        set(value) { field = value; if (value) startBubbles(); invalidate() }

    // ── Vlna ──────────────────────────────────────────────────────────
    private var waveOffset = 0f
    private var waveOffset2 = 0f
    private val waveAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 3000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            waveOffset = it.animatedValue as Float
            waveOffset2 = waveOffset * 0.7f + 1f
            invalidate()
        }
    }

    // ── Bublinky ──────────────────────────────────────────────────────
    data class Bubble(var x: Float, var y: Float, var radius: Float,
                      var speed: Float, var alpha: Float, var wobble: Float)
    private val bubbles = mutableListOf<Bubble>()
    private val bubbleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 100L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { updateBubbles(); invalidate() }
    }

    // ── Barvy ─────────────────────────────────────────────────────────
    private val waterColor   = Color.parseColor("#4A8FA8")
    private val waterColor2  = Color.parseColor("#3A7A93")
    private val glassStroke  = Color.parseColor("#4A8FA8")
    private val goldStart    = Color.parseColor("#C8923A")
    private val goldEnd      = Color.parseColor("#E8B84B")
    private val waveHighlight= Color.argb(77, 255, 255, 255)  // 30% bílá
    private val textColor    = Color.parseColor("#283618")
    private val bubbleColor  = Color.parseColor("#E8B84B")

    // ── Paints ────────────────────────────────────────────────────────
    private val waterPaint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wave2Paint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 74, 143, 168)
        style = Paint.Style.FILL
    }
    private val glassPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val glowPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        alpha = 60
    }
    private val bgPaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8B84B")
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 255, 255, 255)
        style = Paint.Style.FILL
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        waveAnimator.start()
    }

    // ── Animace rychlosti ─────────────────────────────────────────────
    private fun updateAnimatorSpeed() {
        waveAnimator.duration = if (isDehydrated) 900L else 3000L
    }

    private fun startBubbles() {
        if (!bubbleAnimator.isRunning) bubbleAnimator.start()
    }

    private fun updateBubbles() {
        if (!goalReached) { bubbles.clear(); return }
        // Spawnuj nové
        if (bubbles.size < 8 && Math.random() < 0.3) {
            val bx = width * (0.2f + Math.random().toFloat() * 0.6f)
            bubbles.add(Bubble(
                x = bx, y = height * 0.85f,
                radius = 3f + Math.random().toFloat() * 5f,
                speed = 1.5f + Math.random().toFloat() * 2f,
                alpha = 0.6f + Math.random().toFloat() * 0.4f,
                wobble = Math.random().toFloat() * 0.1f
            ))
        }
        // Pohyb
        val waterY = getWaterY()
        bubbles.removeAll { b ->
            b.y -= b.speed
            b.x += sin(b.y * 0.05f) * 1.5f
            b.alpha -= 0.005f
            b.y < waterY || b.alpha <= 0f
        }
    }

    private fun getWaterY(): Float {
        val usableH = height.toFloat()
        return usableH * (1f - progressFraction.coerceIn(0.05f, 0.95f))
    }

    // ── Kreslení ──────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f  // pill radius
        val rect = RectF(0f, 0f, w, h)

        // 1. Pozadí pillu — matné sklo
        bgPaint.color = Color.argb(40, 254, 250, 224)  // #FEFAE0 @ 15%
        canvas.drawRoundRect(rect, r, r, bgPaint)

        // 2. Ořez na tvar pillu
        val clipPath = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clipPath)

        val waterY = getWaterY()

        // Amplituda — dehydratace = větší, nervóznější
        val amp = if (isDehydrated) {
            8f + sin(waveOffset * 3f).absoluteValue * 12f
        } else {
            5f + sin(waveOffset * 0.5f) * 3f
        }

        // 3. Druhá vlna (hlubší, posunuta)
        val wave2Path = buildWavePath(w, h, waterY + 8f, amp * 0.7f, waveOffset2, waveOffset2 * 1.3f)
        canvas.drawPath(wave2Path, wave2Paint)

        // 4. Hlavní vlna — vždy modrá voda (goalReached mění jen rámeček a bublinky)
        val mainPath = buildWavePath(w, h, waterY, amp, waveOffset, waveOffset * 0.8f)
        waterPaint.shader = LinearGradient(0f, waterY, 0f, h, waterColor, waterColor2, Shader.TileMode.CLAMP)
        waterPaint.style = Paint.Style.FILL
        canvas.drawPath(mainPath, waterPaint)

        // 5. Wave highlight (vždy viditelný)
        val highlightPath = buildWaveHighlightPath(w, waterY, amp, waveOffset)
        canvas.drawPath(highlightPath, highlightPaint)

        // 6. Bublinky (100% achievement)
        if (goalReached) {
            for (b in bubbles) {
                bubblePaint.alpha = (b.alpha * 255).toInt()
                canvas.drawCircle(b.x, b.y, b.radius, bubblePaint)
                // Vnitřní odlesk bublinky
                bubblePaint.alpha = (b.alpha * 80).toInt()
                canvas.drawCircle(b.x - b.radius * 0.3f, b.y - b.radius * 0.3f, b.radius * 0.4f, bubblePaint)
            }
        }

        // 7. Glassmorphism highlight — bílý odlesk nahoře vlevo
        val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.25f, h * 0.2f, h * 0.4f,
                Color.argb(60, 255, 255, 255), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, r, r, shimmerPaint)

        canvas.restore()

        // 8. Rámeček pillu
        if (goalReached) {
            // Zlatý gradient stroke
            glassPaint.shader = LinearGradient(0f, 0f, w, h, goldStart, goldEnd, Shader.TileMode.CLAMP)
            glassPaint.strokeWidth = 3f
            glowPaint.shader = LinearGradient(0f, 0f, w, h, goldStart, goldEnd, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(3f, 3f, w-3f, h-3f), r, r, glowPaint)
        } else {
            glassPaint.shader = null
            glassPaint.color = glassStroke
            glassPaint.alpha = 120
        }
        canvas.drawRoundRect(RectF(1.5f, 1.5f, w-1.5f, h-1.5f), r-1f, r-1f, glassPaint)

        // 9. Text — ml / cíl
        val mlText = "${(progressFraction * 1000).toInt()}"  // placeholder, nastavuje DashboardFragment
        canvas.drawText(tvMain, w / 2f, h * 0.48f, textPaint)
        canvas.drawText(tvSub,  w / 2f, h * 0.72f, labelPaint)
    }

    // Text properties nastavované zvenku
    var tvMain: String = "0 ml"
    var tvSub:  String = "💧 HYDRATACE"

    // ── Wave path buildery ─────────────────────────────────────────────
    private fun buildWavePath(
        w: Float, h: Float, baseY: Float,
        amp: Float, phase1: Float, phase2: Float
    ): Path {
        val path = Path()
        path.moveTo(0f, h)
        path.lineTo(0f, baseY)

        // Bezierovy kubické křivky místo lineTo — žádné ostré lomy
        val steps = 32
        val stepW = w / steps
        for (i in 0..steps) {
            val x  = w * i.toFloat() / steps
            val a1 = phase1 + (x / w) * 4f * PI.toFloat()
            val a2 = phase2 + (x / w) * 6f * PI.toFloat()
            val y  = baseY + sin(a1) * amp + sin(a2) * amp * 0.35f

            if (i == 0) {
                path.lineTo(x, y)
            } else {
                // Kontrolní body pro kubický Bezier — tangenta vlny
                val prevX  = w * (i - 1).toFloat() / steps
                val prevA1 = phase1 + (prevX / w) * 4f * PI.toFloat()
                val prevA2 = phase2 + (prevX / w) * 6f * PI.toFloat()
                val prevY  = baseY + sin(prevA1) * amp + sin(prevA2) * amp * 0.35f

                val cx1 = prevX + stepW * 0.5f
                val cy1 = prevY
                val cx2 = x - stepW * 0.5f
                val cy2 = y
                path.cubicTo(cx1, cy1, cx2, cy2, x, y)
            }
        }
        path.lineTo(w, h)
        path.close()
        return path
    }

    private fun buildWaveHighlightPath(w: Float, baseY: Float, amp: Float, phase: Float): Path {
        val path = Path()
        val steps = 32
        val stepW = w / steps
        var started = false
        var prevX = 0f; var prevY = 0f
        for (i in 0..steps) {
            val x     = w * i.toFloat() / steps
            val angle = phase + (x / w) * 4f * PI.toFloat()
            val y     = baseY + sin(angle) * amp
            val dy    = cos(angle)
            if (dy > 0.4f) {
                if (!started) {
                    path.moveTo(x, y); started = true
                } else {
                    val cx1 = prevX + stepW * 0.5f
                    val cx2 = x - stepW * 0.5f
                    path.cubicTo(cx1, prevY, cx2, y, x, y)
                }
                prevX = x; prevY = y
            } else {
                started = false
            }
        }
        return path
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator.cancel()
        bubbleAnimator.cancel()
    }
}