package cz.uhk.macroflow.pokemon

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * Zlatolesklá stránka deníku pro tajné linky (docs/adr/0050): dvojitý zlatý rámeček s rohovými
 * ozdobami, přes papír pomalu přejíždí lesk a tu a tam se zatřpytí hvězdička.
 * Kreslí se jako foreground přes papír deníku; animuje se sama, dokud je vidět.
 */
class GoldLeafDrawable(private val density: Float) : Drawable() {

    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFC9A032.toInt() }
    private val frameInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xAAF4D77A.toInt() }
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9B04A.toInt() }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val shineMatrix = Matrix()
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidateSelf() }
    }

    fun start() { if (!animator.isStarted) animator.start() }
    fun stop() { animator.cancel() }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val w = b.width().toFloat(); val h = b.height().toFloat()
        // lesk: šikmý světlý pruh, který jednou za pár vteřin přejede stránku
        val band = w * 0.35f
        if (shine.shader == null) {
            shine.shader = LinearGradient(0f, 0f, band, 0f,
                intArrayOf(0x00FFF4C0, 0x55FFF4C0, 0x00FFF4C0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        }
        val x = -band + (w + band * 2) * phase
        shineMatrix.setTranslate(x, 0f)
        shineMatrix.postSkew(-0.35f, 0f)
        shine.shader.setLocalMatrix(shineMatrix)
        canvas.drawRect(b, shine)

        // dvojitý zlatý rámeček
        val m1 = 5 * density; val m2 = 9 * density
        frame.strokeWidth = 2.2f * density
        frameInner.strokeWidth = 1f * density
        canvas.drawRect(b.left + m1, b.top + m1, b.right - m1, b.bottom - m1, frame)
        canvas.drawRect(b.left + m2, b.top + m2, b.right - m2, b.bottom - m2, frameInner)
        // rohové kosočtverce
        val r = 5 * density
        for ((cx, cy) in listOf(b.left + m1 to b.top + m1, b.right - m1 to b.top + m1, b.left + m1 to b.bottom - m1, b.right - m1 to b.bottom - m1)) {
            canvas.save(); canvas.rotate(45f, cx, cy)
            canvas.drawRect(cx - r, cy - r, cx + r, cy + r, corner)
            canvas.restore()
        }
        // třpytky: malé čtyřcípé hvězdy, které se střídavě rozsvěcují
        for (i in 0 until 9) {
            val t = ((phase * 3f + i * 0.37f) % 1f)
            val a = sin(t * Math.PI).toFloat()
            if (a < 0.2f) continue
            val sx = b.left + w * ((i * 0.618f + 0.11f) % 1f)
            val sy = b.top + h * ((i * 0.382f + 0.23f) % 1f)
            val s = (1.5f + 2.5f * a) * density
            spark.alpha = (a * 200).toInt()
            canvas.drawRect(sx - s, sy - 0.6f * density, sx + s, sy + 0.6f * density, spark)
            canvas.drawRect(sx - 0.6f * density, sy - s, sx + 0.6f * density, sy + s, spark)
        }
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) { shine.shader = null }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
