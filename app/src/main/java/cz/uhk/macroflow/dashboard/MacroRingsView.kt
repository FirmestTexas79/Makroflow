package cz.uhk.macroflow.dashboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Tři soustředné prstence maker na hlavní obrazovce (docs/adr/0074): vnější bílkoviny, prostřední
 * sacharidy, vnitřní tuky. Každý ukazuje podíl snědeného z cíle; přes 100 % se prstenec dotočí
 * a přebytek se kreslí tmavším odstínem se stínem na konci, jako druhé kolo.
 */
class MacroRingsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    /** Barvy prstenců zvenku dovnitř (na tmavém pozadí karty). */
    var colors = intArrayOf(Color.parseColor("#B5CC8E"), Color.parseColor("#E9B072"), Color.parseColor("#E5824A"))

    private val target = FloatArray(3)
    private val shown = FloatArray(3)
    private var animator: ValueAnimator? = null

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val cap = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    /** Podíly 0…∞ (1 = cíl). Animuje z aktuálního stavu. */
    fun setProgress(protein: Float, carbs: Float, fat: Float, animate: Boolean = true) {
        target[0] = protein.coerceAtLeast(0f); target[1] = carbs.coerceAtLeast(0f); target[2] = fat.coerceAtLeast(0f)
        animator?.cancel()
        if (!animate) { target.copyInto(shown); invalidate(); return }
        val from = shown.copyOf()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100; interpolator = DecelerateInterpolator(1.6f)
            // prstence se rozjedou s lehkým zpožděním za sebou
            addUpdateListener { a ->
                val k = a.animatedValue as Float
                for (i in 0..2) {
                    val ki = ((k - i * 0.08f) / (1f - 0.16f)).coerceIn(0f, 1f)
                    shown[i] = from[i] + (target[i] - from[i]) * ki
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f; val cy = height / 2f
        val stroke = size * 0.078f
        val gap = stroke * 0.42f
        for (i in 0..2) {
            val r = size / 2f - stroke / 2f - 2f - i * (stroke + gap)
            oval.set(cx - r, cy - r, cx + r, cy + r)
            val c = colors[i]
            track.strokeWidth = stroke; track.color = withAlpha(c, 0.16f)
            canvas.drawCircle(cx, cy, r, track)

            val p = shown[i]
            if (p <= 0.001f) continue
            arc.strokeWidth = stroke; arc.color = c
            canvas.drawArc(oval, -90f, min(p, 1f) * 360f, false, arc)
            if (p > 1f) {
                // druhé kolo: tmavší odstín, na konci stín, ať je vidět, kde přebytek končí
                val over = min(p - 1f, 0.999f)
                val endA = Math.toRadians((-90.0 + over * 360.0))
                val ex = cx + (r * cos(endA)).toFloat(); val ey = cy + (r * sin(endA)).toFloat()
                cap.color = Color.argb(90, 0, 0, 0)
                canvas.drawCircle(ex + stroke * 0.08f, ey + stroke * 0.12f, stroke * 0.55f, cap)
                arc.color = darken(c, 0.78f)
                canvas.drawArc(oval, -90f, over * 360f, false, arc)
            }
        }
    }

    private fun withAlpha(c: Int, a: Float) = Color.argb((a * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))
    private fun darken(c: Int, k: Float) = Color.rgb((Color.red(c) * k).toInt(), (Color.green(c) * k).toInt(), (Color.blue(c) * k).toInt())
}
