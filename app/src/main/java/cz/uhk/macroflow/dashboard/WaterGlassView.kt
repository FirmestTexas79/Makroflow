package cz.uhk.macroflow.dashboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

/**
 * WaterGlassView — elegantní válcová sklenice ve stylu Makroflow.
 *
 * - Přímé stěny (ne trapéz) s kulatým dnem
 * - Kremová/modrá paleta ladící s aplikací
 * - Bezierovy vlny, fyzika ledu, vortex efekt
 * - Rysky na pravé stěně, popisky vlevo
 */
class WaterGlassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Stav ──────────────────────────────────────────────────────────
    var goalMl:    Int = 2500
    var currentMl: Int = 0

    val maxSingleDrinkMl = 2000  // max naráz 2 litry

    var amountMl: Int = 300
        set(value) {
            // Ukládáme přesnou hodnotu (i 1ml), ale vizuál se snapuje na 50ml threshholdy
            val clamped = value.coerceIn(0, maxSingleDrinkMl)
            if (field != clamped) {
                field = clamped
                // Vizuální fraction — zaokrouhlení nahoru na nejbližší 50ml
                val visualMl = if (clamped == 0) 0
                else ((clamped - 1) / 50 + 1) * 50  // 1..50→50, 51..100→100
                targetFraction = visualMl.toFloat() / maxSingleDrinkMl.toFloat()
                onAmountChanged?.invoke(clamped)
            }
        }

    var onAmountChanged: ((Int) -> Unit)? = null

    private var targetFraction  = 0.3f
    private var displayFraction = 0.3f

    // ── Vlna ──────────────────────────────────────────────────────────
    private var waveOffset   = 0f
    private var waveAmplitude = 5f
    private var lastDragSpeed = 0f

    private val waveAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            waveOffset     = it.animatedValue as Float
            waveAmplitude  = waveAmplitude * 0.96f + lastDragSpeed * 0.04f
            lastDragSpeed *= 0.88f
            displayFraction += (targetFraction - displayFraction) * 0.07f
            invalidate()
        }
    }

    // ── Vortex ────────────────────────────────────────────────────────
    private var vortexActive   = false
    private var vortexProgress = 0f

    fun triggerVortex(onComplete: () -> Unit) {
        vortexActive   = true
        vortexProgress = 0f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            addUpdateListener {
                vortexProgress = it.animatedValue as Float
                invalidate()
                if (vortexProgress >= 1f) {
                    vortexActive    = false
                    displayFraction = 0f
                    targetFraction  = 0f
                    onComplete()
                }
            }
            start()
        }
    }

    // ── Kostky ledu ───────────────────────────────────────────────────
    data class IceCube(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var angle: Float, var angularV: Float,
        val size: Float
    )
    private val iceCubes = mutableListOf<IceCube>()

    // ── Touch ──────────────────────────────────────────────────────────
    private var dragStartY       = 0f
    private var dragStartFraction = 0f
    private var lastTouchY       = 0f
    private var lastTouchTime    = 0L

    // ── Barvy — Makroflow paleta ──────────────────────────────────────
    private val colorWater1  = Color.parseColor("#4A8FA8")   // modrá základ
    private val colorWater2  = Color.parseColor("#2A6F88")   // tmavší modrá
    private val colorWave2   = Color.argb(50,  74, 143, 168)
    private val colorGlass   = Color.argb(50,  254, 250, 224) // #FEFAE0 @ 20%
    private val colorGlassStroke = Color.argb(80, 254, 250, 224)
    private val colorRyska   = Color.argb(140, 254, 250, 224)
    private val colorRyskaBig= Color.argb(200, 254, 250, 224)
    private val colorRyskaText = Color.argb(180, 254, 250, 224)
    private val colorIce     = Color.argb(110, 220, 240, 255)
    private val colorIceHL   = Color.argb(160, 255, 255, 255)

    // ── Paints ────────────────────────────────────────────────────────
    private val waterPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wave2Paint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWave2; style = Paint.Style.FILL; alpha = 60
    }
    private val glassBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGlass; style = Paint.Style.FILL
    }
    private val glassStroke  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGlassStroke; style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val ryskaSmall   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorRyska; style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val ryskaBig     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorRyskaBig; style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val ryskaText    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorRyskaText; textSize = 22f
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT
    }
    private val icePaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorIce; style = Paint.Style.FILL
    }
    private val iceHL        = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorIceHL; style = Paint.Style.FILL
    }
    private val vortexPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        waveAnimator.start()
        post { repeat(2) { spawnIceCube() } }
    }

    private fun spawnIceCube() {
        if (width == 0) return
        val gx = width * 0.15f
        val gw = width * 0.70f
        iceCubes.add(IceCube(
            x = gx + 16f + Math.random().toFloat() * (gw - 32f),
            y = height * 0.55f,
            vx = (Math.random().toFloat() - 0.5f) * 1.5f,
            vy = 0f,
            angle = Math.random().toFloat() * 360f,
            angularV = (Math.random().toFloat() - 0.5f) * 1.5f,
            size = 14f + Math.random().toFloat() * 10f
        ))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (vortexActive) return true
        val glassTop    = height * 0.06f
        val glassBottom = height * 0.94f
        val glassH      = glassBottom - glassTop

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartY        = event.y
                dragStartFraction = targetFraction
                lastTouchY        = event.y
                lastTouchTime     = System.currentTimeMillis()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dy    = dragStartY - event.y
                val newFr = (dragStartFraction + dy / glassH).coerceIn(0f, 1f)
                val now   = System.currentTimeMillis()
                val dt    = (now - lastTouchTime).coerceAtLeast(1L)
                lastDragSpeed = (abs(event.y - lastTouchY) / dt * 50f).coerceAtMost(40f)
                lastTouchY    = event.y; lastTouchTime = now

                val newMl = (newFr * goalMl / 50).toInt() * 50
                if (newMl != amountMl) {
                    amountMl = newMl
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    iceCubes.forEach { it.vy = -lastDragSpeed * 0.25f }
                }
            }
            MotionEvent.ACTION_UP ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        val w  = width.toFloat()
        val h  = height.toFloat()
        val cx = w / 2f

        // Geometrie — trapéz (nahoře širší), kulaté dno
        val gTopLeft    = w * 0.10f   // nahoře širší
        val gTopRight   = w * 0.90f
        val gBottomLeft = w * 0.18f   // dole užší
        val gBottomRight= w * 0.82f
        val gTop        = h * 0.06f
        val gBottom     = h * 0.94f
        val gLeft       = gBottomLeft  // pro rysky a vlny
        val gRight      = gBottomRight
        val gH          = gBottom - gTop
        val bottomR     = (gBottomRight - gBottomLeft) * 0.10f

        // Tvar sklenice — trapéz + kulaté dno
        val glassPath = Path().apply {
            moveTo(gTopLeft, gTop)
            lineTo(gTopRight, gTop)
            lineTo(gBottomRight, gBottom - bottomR)
            quadTo(gBottomRight, gBottom, gBottomRight - bottomR, gBottom)
            lineTo(gBottomLeft + bottomR, gBottom)
            quadTo(gBottomLeft, gBottom, gBottomLeft, gBottom - bottomR)
            close()
        }

        // 1. Pozadí sklenice
        canvas.drawPath(glassPath, glassBgPaint)

        canvas.save()
        canvas.clipPath(glassPath)

        val waterY = gBottom - gH * displayFraction

        if (vortexActive) {
            drawVortex(canvas, cx, waterY, gBottom, gBottomLeft, gBottomRight)
        } else {
            // 2. Druhá vlna
            val wave2 = buildWave(gBottomLeft, gBottomRight, gBottom,
                waterY + 9f, waveAmplitude * 0.55f, waveOffset * 1.4f,
                gTopLeft, gTopRight, gTop)
            canvas.drawPath(wave2, wave2Paint)

            // 3. Hlavní voda
            waterPaint.style = Paint.Style.FILL
            waterPaint.shader = LinearGradient(
                0f, waterY, 0f, gBottom,
                colorWater1, colorWater2, Shader.TileMode.CLAMP
            )
            val mainWave = buildWave(gBottomLeft, gBottomRight, gBottom,
                waterY, waveAmplitude, waveOffset,
                gTopLeft, gTopRight, gTop)
            canvas.drawPath(mainWave, waterPaint)

            // 4. Highlight na hřebenu vlny
            drawWaveHighlight(canvas, gBottomLeft, gBottomRight, waterY, gTopLeft, gTopRight, gTop)

            // 5. Kostky ledu
            drawIceCubes(canvas, gBottomLeft, gBottomRight, gTop, gBottom, waterY)

            // 6. Shimmer — odlesk na levé stěně
            val shimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    gTopLeft, gTop, gTopLeft + 22f, gBottom,
                    Color.argb(45, 255, 255, 255), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(glassPath, shimPaint)
        }

        canvas.restore()

        // 7. Rámeček sklenice
        canvas.drawPath(glassPath, glassStroke)

        // 8. Rysky — na pravé stěně
        drawRysky(canvas, gBottomLeft, gBottomRight, gTop, gBottom, gH, gTopLeft, gTopRight)
    }

    // ── Bezierova vlna — respektuje trapézový tvar ───────────────────
    private fun buildWave(
        left: Float, right: Float, bottom: Float,
        baseY: Float, amp: Float, phase: Float,
        topLeft: Float = left, topRight: Float = right,
        glassTop: Float = 0f
    ): Path {
        val path  = Path()
        val steps = 28
        // Interpoluj šířku v dané výšce (trapéz)
        fun xL(y: Float): Float {
            if (glassTop >= bottom) return left
            val t = (y - glassTop) / (bottom - glassTop)
            return topLeft + (left - topLeft) * t
        }
        fun xR(y: Float): Float {
            if (glassTop >= bottom) return right
            val t = (y - glassTop) / (bottom - glassTop)
            return topRight + (right - topRight) * t
        }

        val xl = xL(baseY)
        val xr = xR(baseY)
        val stepW = (xr - xl) / steps

        path.moveTo(xL(bottom), bottom)
        path.lineTo(xR(bottom), bottom)
        path.lineTo(xr, baseY)

        for (i in steps downTo 0) {
            val x  = xl + (xr - xl) * i.toFloat() / steps
            val a  = phase + (i.toFloat() / steps) * 4f * PI.toFloat()
            val y  = baseY + sin(a) * amp + sin(a * 0.65f) * amp * 0.3f
            if (i == steps) {
                path.lineTo(x, y)
            } else {
                val nx = xl + (xr - xl) * (i + 1).toFloat() / steps
                val na = phase + ((i + 1).toFloat() / steps) * 4f * PI.toFloat()
                val ny = baseY + sin(na) * amp + sin(na * 0.65f) * amp * 0.3f
                path.cubicTo(nx - stepW * 0.5f, ny, x + stepW * 0.5f, y, x, y)
            }
        }
        path.lineTo(xL(bottom), bottom)
        path.close()
        return path
    }

    private fun drawWaveHighlight(canvas: Canvas, left: Float, right: Float, baseY: Float,
                                  topLeft: Float = left, topRight: Float = right, glassTop: Float = 0f) {
        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 255, 255, 255)
            style = Paint.Style.STROKE; strokeWidth = 2.5f
        }
        val steps = 28; val stepW = (right - left) / steps
        val path = Path(); var started = false; var px = 0f; var py = 0f
        for (i in 0..steps) {
            val x = left + (right - left) * i.toFloat() / steps
            val a = waveOffset + (i.toFloat() / steps) * 4f * PI.toFloat()
            val y = baseY + sin(a) * waveAmplitude
            if (cos(a) > 0.5f) {
                if (!started) { path.moveTo(x, y); started = true }
                else path.cubicTo(px + stepW * 0.5f, py, x - stepW * 0.5f, y, x, y)
                px = x; py = y
            } else started = false
        }
        canvas.drawPath(path, hPaint)
    }

    private fun drawIceCubes(
        canvas: Canvas,
        left: Float, right: Float, top: Float, bottom: Float, waterY: Float
    ) {
        iceCubes.forEach { cube ->
            cube.vy   += 0.12f
            cube.y    += cube.vy
            cube.x    += cube.vx
            cube.angle += cube.angularV
            cube.vy   *= 0.94f

            val floatY = waterY - cube.size * 0.25f
            if (cube.y > floatY) { cube.y = floatY; cube.vy *= -0.25f }

            val lBound = left + cube.size
            val rBound = right - cube.size
            if (cube.x < lBound) { cube.x = lBound; cube.vx = abs(cube.vx) }
            if (cube.x > rBound) { cube.x = rBound; cube.vx = -abs(cube.vx) }

            if (displayFraction < 0.05f) return@forEach

            canvas.save()
            canvas.translate(cube.x, cube.y)
            canvas.rotate(cube.angle)
            val s = cube.size
            canvas.drawRoundRect(RectF(-s, -s, s, s), 4f, 4f, icePaint)
            // Highlight
            val hlP = Paint(iceHL).apply { alpha = 100 }
            canvas.drawRoundRect(RectF(-s * 0.55f, -s * 0.55f, s * 0.05f, -s * 0.1f), 2f, 2f, hlP)
            canvas.restore()
        }
    }

    private fun drawRysky(
        canvas: Canvas,
        left: Float, right: Float, top: Float, bottom: Float, glassH: Float,
        topLeft: Float = left, topRight: Float = right
    ) {
        // Zobrazíme rysky jen do maxSingleDrinkMl (2000ml)
        val stepMl  = 50
        val steps   = maxSingleDrinkMl / stepMl

        // Interpolace pravé stěny trapézu v dané výšce
        fun xRight(y: Float): Float {
            val t = (y - top) / (bottom - top)
            return topRight + (right - topRight) * t
        }

        for (i in 0..steps) {
            val ml   = i * stepMl
            val frac = ml.toFloat() / maxSingleDrinkMl
            val y    = bottom - glassH * frac
            val xr   = xRight(y)

            when {
                ml % 500 == 0 && ml > 0 -> {
                    canvas.drawLine(xr - 22f, y, xr, y, ryskaBig)
                    canvas.drawText("${ml}ml", xr - 26f, y + 8f, ryskaText)
                }
                ml % 250 == 0 && ml > 0 -> {
                    canvas.drawLine(xr - 14f, y, xr, y, ryskaSmall)
                }
                ml % 100 == 0 && ml > 0 -> {
                    canvas.drawLine(xr - 8f, y, xr, y, ryskaSmall)
                }
            }
        }
    }

    private fun drawVortex(
        canvas: Canvas, cx: Float,
        waterY: Float, bottom: Float,
        left: Float, right: Float
    ) {
        val centerY  = (waterY + bottom) / 2f
        val maxR     = (right - left) / 2.5f
        val vis      = 1f - vortexProgress

        waterPaint.shader = LinearGradient(0f, waterY, 0f, bottom, colorWater1, colorWater2, Shader.TileMode.CLAMP)
        waterPaint.alpha  = (vis * 255).toInt()
        canvas.drawCircle(cx, centerY + vortexProgress * (bottom - centerY) * 0.5f, maxR * vis, waterPaint)

        for (ring in 0..3) {
            val r = maxR * (1f - ring * 0.22f) * vis
            if (r <= 0) continue
            val sweep = 260f + vortexProgress * 600f
            val start = -sweep / 2f + vortexProgress * 420f * (ring + 1)
            vortexPaint.color = Color.argb(
                ((0.65f - ring * 0.14f) * 255 * vis).toInt().coerceIn(0, 255),
                255, 255, 255
            )
            vortexPaint.strokeWidth = 2f + (3 - ring) * 0.5f
            canvas.drawArc(RectF(cx - r, centerY - r, cx + r, centerY + r), start, sweep, false, vortexPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator.cancel()
    }
}