package cz.uhk.macroflow

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SymmetryGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var dataPoints = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 1.0f)
        set(value) {
            field = value
            invalidate()
        }

    private val labels = arrayOf("HRU", "PAS", "BIC", "PŘE", "BŘI", "STE", "LÝT", "KRK")

    // VÝRAZNĚJŠÍ PAVUČINA - změnili jsme barvu na tmavší olivovou/šedou pro viditelnost
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#283618") // Tvá tmavě zelená ze šablony
        style = Paint.Style.STROKE
        strokeWidth = 3f // Trochu tlustší čáry
        alpha = 100 // Vyšší viditelnost
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#283618")
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#60DDA15E") // Oranžová s 37% průhledností
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        // Vypnutí HW akcelerace pro tento View, aby stíny fungovaly a mřížka se vykreslila správně
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun getSmoothColor(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        return Color.HSVToColor(floatArrayOf(v * 120f, 0.9f, 0.9f))
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = Math.min(centerX, centerY) * 0.72f

        // --- 1. KRESLENÍ PAVUČINY (Mřížka pod grafem) ---
        // Kreslíme 4 úrovně oktagonu
        for (j in 1..4) {
            val r = radius * (j / 4f)
            val path = Path()
            for (i in 0..7) {
                val angle = Math.toRadians(i * 45.0 - 90.0)
                val x = centerX + r * Math.cos(angle).toFloat()
                val y = centerY + r * Math.sin(angle).toFloat()

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)

                // Osy a popisky kreslíme jen u nejvzdálenějšího kruhu
                if (j == 4) {
                    canvas.drawLine(centerX, centerY, x, y, gridPaint)

                    val labelR = radius + 50f
                    val lx = centerX + labelR * Math.cos(angle).toFloat()
                    val ly = centerY + labelR * Math.sin(angle).toFloat() + 10f
                    canvas.drawText(labels[i], lx, ly, textPaint)
                }
            }
            path.close()
            canvas.drawPath(path, gridPaint)
        }

        // --- 2. KRESLENÍ ORANŽOVÉHO TVARU ---
        val polyPath = Path()
        val currentPoints = mutableListOf<PointF>()
        for (i in 0..7) {
            val angle = Math.toRadians(i * 45.0 - 90.0)
            val pRadius = radius * dataPoints[i].coerceIn(0.1f, 1.0f)
            val x = centerX + pRadius * Math.cos(angle).toFloat()
            val y = centerY + pRadius * Math.sin(angle).toFloat()
            currentPoints.add(PointF(x, y))

            if (i == 0) polyPath.moveTo(x, y) else polyPath.lineTo(x, y)
        }
        polyPath.close()
        canvas.drawPath(polyPath, fillPaint)

        // --- 3. KRESLENÍ SVÍTÍCÍCH BODŮ ---
        for (i in 0..7) {
            val color = getSmoothColor(dataPoints[i])
            pointPaint.color = color
            pointPaint.setShadowLayer(12f, 0f, 0f, color) // Záře bodu
            canvas.drawCircle(currentPoints[i].x, currentPoints[i].y, 14f, pointPaint)
            pointPaint.clearShadowLayer()
        }
    }
}