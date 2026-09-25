package cz.uhk.macroflow.nutrition

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * Prstenec s podílem energie z bílkovin, sacharidů a tuků (barvy maker aplikace).
 * Volitelně s číslem uprostřed (kcal) – v detailu porce; v řádku seznamu bez textu.
 */
class MacroDonutView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val colors = intArrayOf(
        Color.parseColor("#606C38"),   // bílkoviny
        Color.parseColor("#E9B072"),   // sacharidy
        Color.parseColor("#BC6C25")    // tuky
    )
    var trackColor = Color.parseColor("#1F283618")
    var ringWidthDp = 5f
    var centerText: String? = null
    var centerSub: String? = null
    var centerTextColor = Color.parseColor("#283618")

    private var split = floatArrayOf(1f / 3, 1f / 3, 1f / 3)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    fun setMacros(p: Float, s: Float, t: Float) {
        val (a, b, c) = SnackCatalog.energySplit(p, s, t)
        split = floatArrayOf(a, b, c)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = ringWidthDp * d
        val size = minOf(width, height).toFloat()
        rect.set((width - size) / 2 + w / 2, (height - size) / 2 + w / 2, (width + size) / 2 - w / 2, (height + size) / 2 - w / 2)
        ring.strokeWidth = w
        ring.color = trackColor
        canvas.drawArc(rect, 0f, 360f, false, ring)
        val gap = if (split.count { it > 0.01f } > 1) 4f else 0f
        var start = -90f
        split.forEachIndexed { i, f ->
            val sweep = 360f * f
            if (sweep > gap) {
                ring.color = colors[i]
                canvas.drawArc(rect, start + gap / 2, sweep - gap, false, ring)
            }
            start += sweep
        }
        centerText?.let { t ->
            text.color = centerTextColor
            text.textSize = size * 0.26f
            val hasSub = centerSub != null
            canvas.drawText(t, width / 2f, height / 2f + text.textSize * (if (hasSub) 0.15f else 0.35f), text)
            centerSub?.let { s ->
                sub.color = centerTextColor; sub.alpha = 0xAA
                sub.textSize = size * 0.13f
                canvas.drawText(s, width / 2f, height / 2f + text.textSize * 0.15f + sub.textSize * 1.3f, sub)
            }
        }
    }
}
