package cz.uhk.macroflow

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * TrainerOverlayView
 *
 * Jednoduchý overlay pro zobrazení bodů detekovaného objektu.
 * Používá se jako alternativa k BarbellPathOverlay (která je definována
 * přímo v TrainerFragment) - například pro debug zobrazení všech
 * nalezených objektů najednou.
 *
 * Data přicházejí předpřipravená jako relativní souřadnice (0.0 - 1.0)
 * v portrait orientaci - tuto konverzi řeší TrainerFragment.
 */
class TrainerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points = listOf<PointF>()

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDA15E")
        style = Paint.Style.FILL
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BC6C25")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    /**
     * Aktualizuje seznam bodů k vykreslení.
     * Souřadnice musí být v rozsahu 0.0 - 1.0 (relativní vůči rozměrům view).
     */
    fun updatePoints(newPoints: List<PointF>) {
        this.points = newPoints
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        for (point in points) {
            val x = point.x * w
            val y = point.y * h

            // Vnější kroužek
            canvas.drawCircle(x, y, 28f, outlinePaint)
            // Vnitřní bod
            canvas.drawCircle(x, y, 10f, dotPaint)
        }
    }
}