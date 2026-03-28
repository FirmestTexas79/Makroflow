package cz.uhk.macroflow.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class PieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var proteinPct: Float = 25f
    private var carbPct: Float = 45f
    private var fatPct: Float = 30f

    // Barvy přímo z tvé palety
    private val pPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#606C38") }
    private val cPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DDA15E") }
    private val fPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#BC6C25") }

    // Barva na mezeru (stejná jako pozadí karty)
    private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FEFAE0")
        style = Paint.Style.STROKE
        strokeWidth = 5f // Šířka mezery
    }

    private val rectF = RectF()

    fun setRatios(p: Float, c: Float, f: Float) {
        proteinPct = p
        carbPct = c
        fatPct = f
        invalidate() // Vynutí překreslení
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = minOf(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = size / 2f

        rectF.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        var startAngle = -90f // Začínáme nahoře

        // 1. Nakreslíme Bílkoviny (Protein)
        val pSweep = (proteinPct / 100f) * 360f
        canvas.drawArc(rectF, startAngle, pSweep, true, pPaint)
        startAngle += pSweep

        // 2. Nakreslíme Sacharidy (Carbs)
        val cSweep = (carbPct / 100f) * 360f
        canvas.drawArc(rectF, startAngle, cSweep, true, cPaint)
        startAngle += cSweep

        // 3. Nakreslíme Tuky (Fats)
        val fSweep = (fatPct / 100f) * 360f
        canvas.drawArc(rectF, startAngle, fSweep, true, fPaint)

        // 4. Nakreslíme mezery
        var gapAngle = -90f
        gapAngle += pSweep
        drawGap(canvas, centerX, centerY, radius, gapAngle)

        gapAngle += cSweep
        drawGap(canvas, centerX, centerY, radius, gapAngle)

        gapAngle += fSweep
        drawGap(canvas, centerX, centerY, radius, gapAngle)
    }

    private fun drawGap(canvas: Canvas, cx: Float, cy: Float, radius: Float, angle: Float) {
        val angleRad = Math.toRadians(angle.toDouble())
        val stopX = (cx + radius * Math.cos(angleRad)).toFloat()
        val stopY = (cy + radius * Math.sin(angleRad)).toFloat()
        canvas.drawLine(cx, cy, stopX, stopY, gapPaint)
    }
}