package cz.uhk.macroflow

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

/**
 * SymmetryGraphView — osmistranný pavouk v Makroflow stylu.
 *
 * Paleta: kremová pavučina, zlatý polygon, zelené/oranžové body
 * Pozadí: průhledné (karta dává kremovou)
 */
class SymmetryGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var dataPoints = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 1.0f)
        set(value) { field = value; invalidate() }

    // Popisky os — česky, zkráceně
    private val labels = arrayOf("HRU", "PAS", "BIC", "PŘE", "BŘI", "STE", "LÝT", "KRK")

    // ── Barvy Makroflow ──────────────────────────────────────────────
    private val colorGrid      = Color.parseColor("#283618")
    private val colorGridLight = Color.parseColor("#20283618")
    private val colorFill      = Color.parseColor("#50DDA15E")   // zlatá průhledná
    private val colorFillStroke= Color.parseColor("#DDA15E")     // zlatá okraj
    private val colorLabel     = Color.parseColor("#606C38")     // olivová text
    private val colorLabelBold = Color.parseColor("#283618")     // tmavá pro KRK

    // ── Paints ───────────────────────────────────────────────────────
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGridLight; style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGridLight; style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30283618"); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorFill; style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorFillStroke; style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorLabel; textSize = 26f; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val pointBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FEFAE0"); style = Paint.Style.STROKE; strokeWidth = 2f
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    // Barva bodu: zelená = blízko ideálu, oranžová = odchylka
    private fun pointColor(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        return when {
            v >= 0.85f -> Color.parseColor("#606C38")   // olivová — výborné
            v >= 0.65f -> Color.parseColor("#DDA15E")   // zlatá — dobré
            else       -> Color.parseColor("#BC6C25")   // oranžová — podprůměr
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) * 0.68f
        val labelR  = radius + 46f

        // ── 1. PAVUČINA — 4 úrovně ───────────────────────────────────
        for (level in 1..4) {
            val r = radius * level / 4f
            val path = Path()
            for (i in 0..7) {
                val angle = Math.toRadians(i * 45.0 - 90.0)
                val x = cx + r * cos(angle).toFloat()
                val y = cy + r * sin(angle).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            // Vnější okruh trochu výraznější
            canvas.drawPath(path, if (level == 4) outerRingPaint else gridPaint)
        }

        // ── 2. OSY ────────────────────────────────────────────────────
        for (i in 0..7) {
            val angle = Math.toRadians(i * 45.0 - 90.0)
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            canvas.drawLine(cx, cy, x, y, axisPaint)
        }

        // ── 3. POLYGON (výplň + okraj) ───────────────────────────────
        val polyPath = Path()
        val pts = mutableListOf<PointF>()
        for (i in 0..7) {
            val angle = Math.toRadians(i * 45.0 - 90.0)
            val r = radius * dataPoints[i].coerceIn(0.08f, 1f)
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            pts.add(PointF(x, y))
            if (i == 0) polyPath.moveTo(x, y) else polyPath.lineTo(x, y)
        }
        polyPath.close()
        canvas.drawPath(polyPath, fillPaint)
        canvas.drawPath(polyPath, strokePaint)

        // ── 4. BODY + POPISKY ─────────────────────────────────────────
        for (i in 0..7) {
            val angle = Math.toRadians(i * 45.0 - 90.0)

            // Bod na polygonu
            val col = pointColor(dataPoints[i])
            pointPaint.color = col
            pointPaint.setShadowLayer(8f, 0f, 0f, col)
            canvas.drawCircle(pts[i].x, pts[i].y, 10f, pointPaint)
            pointPaint.clearShadowLayer()
            canvas.drawCircle(pts[i].x, pts[i].y, 10f, pointBorderPaint)

            // Popisek
            val lx = cx + labelR * cos(angle).toFloat()
            val ly = cy + labelR * sin(angle).toFloat() + labelPaint.textSize * 0.35f
            labelPaint.color = if (i == 7) colorLabelBold else colorLabel
            labelPaint.textSize = if (i == 7) 28f else 24f
            canvas.drawText(labels[i], lx, ly, labelPaint)

            // Hodnota v % pod popiskem
            val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = pointColor(dataPoints[i])
                textSize = 18f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            val pct = (dataPoints[i] * 100).toInt()
            canvas.drawText("${pct}%", lx, ly + 22f, pctPaint)
        }

        // ── 5. STŘED — celkové skóre ─────────────────────────────────
        val avg = dataPoints.average().toFloat()
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pointColor(avg); textSize = 32f
            typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val scoreLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorLabel; textSize = 18f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${(avg * 100).toInt()}%", cx, cy + 10f, scorePaint)
        canvas.drawText("SYMETRIE", cx, cy + 30f, scoreLabelPaint)
    }
}