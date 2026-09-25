package cz.uhk.macroflow.nutrition

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * Pruh vůči dnešnímu cíli: světlá část = už snědeno, sytá = přidávaná porce.
 * Nad 100 % se sytá část přebarví do varovné barvy.
 */
class IntakeBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var color = Color.parseColor("#606C38")
    var overColor = Color.parseColor("#E07A5F")
    var trackColor = Color.parseColor("#26FEFAE0")
    private var already = 0f
    private var adding = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    fun set(alreadyFraction: Float, addingFraction: Float) {
        already = alreadyFraction.coerceAtLeast(0f)
        adding = addingFraction.coerceAtLeast(0f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat(); val w = width.toFloat(); val rad = h / 2
        paint.color = trackColor
        r.set(0f, 0f, w, h); canvas.drawRoundRect(r, rad, rad, paint)
        val a = already.coerceAtMost(1f) * w
        val end = (already + adding).coerceAtMost(1f) * w
        if (a > 0f) {
            paint.color = ColorUtils.setAlphaComponent(color, 0x70)
            r.set(0f, 0f, a.coerceAtLeast(h), h); canvas.drawRoundRect(r, rad, rad, paint)
        }
        if (end > a) {
            paint.color = if (already + adding > 1f) overColor else color
            r.set((a - rad).coerceAtLeast(0f), 0f, end.coerceAtLeast(h), h); canvas.drawRoundRect(r, rad, rad, paint)
        }
    }
}
