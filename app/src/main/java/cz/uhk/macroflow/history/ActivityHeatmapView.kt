package cz.uhk.macroflow.history

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * Heatmapa ve stylu GitHubu: sloupce = týdny (Po–Ne), řádky = dny.
 * Popisky dnů zůstávají vlevo, mřížka se posouvá tahem do stran, klepnutí vybere den.
 */
class ActivityHeatmapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onDayClick: ((LocalDate) -> Unit)? = null

    private var columns: List<List<LocalDate?>> = emptyList()
    private var levels: Map<LocalDate, Int> = emptyMap()
    private var selected: LocalDate? = null

    private val dp = resources.displayMetrics.density
    private val cell = 13 * dp
    private val gap = 3 * dp
    private val step = cell + gap
    private val labelW = 22 * dp
    private val monthH = 16 * dp
    private val legendH = 22 * dp
    private val corner = 3 * dp

    /** Tmavá karta aplikace (#283618): bez dat → nic → olivová → zlatá. */
    private val levelColors = intArrayOf(
        Color.parseColor("#2EFEFAE0"),   // 0: data, ale nic
        Color.parseColor("#606C38"),
        Color.parseColor("#8F9447"),
        Color.parseColor("#C39A50"),
        Color.parseColor("#F0C27F")
    )
    private val noDataColor = Color.parseColor("#12FEFAE0")
    private val cardColor = Color.parseColor("#283618")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f * dp; color = Color.parseColor("#FEFAE0")
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FEFAE0"); textSize = 9.5f * dp
    }
    private val rect = RectF()

    private var offsetX = 0f
    private val gridWidth get() = columns.size * step
    private val maxOffset get() = (gridWidth - (width - paddingLeft - paddingRight - labelW)).coerceAtLeast(0f)

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            // Svislý tah nechat stránce (ScrollView historie), vodorovný posouvá mřížku
            if (abs(dx) <= abs(dy)) return false
            offsetX = (offsetX + dx).coerceIn(0f, maxOffset)
            parent?.requestDisallowInterceptTouchEvent(true)
            invalidate()
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            dayAt(e.x, e.y)?.let { onDayClick?.invoke(it) }
            return true
        }
    })

    fun setData(columns: List<List<LocalDate?>>, levels: Map<LocalDate, Int>, selected: LocalDate?) {
        val first = this.columns.isEmpty()
        this.columns = columns
        this.levels = levels
        this.selected = selected
        if (first) post { offsetX = maxOffset; invalidate() }   // start na nejnovějších týdnech
        requestLayout()
        invalidate()
    }

    fun setSelected(date: LocalDate?) {
        selected = date
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = paddingTop + monthH + 7 * step + legendH + paddingBottom
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        offsetX = maxOffset
    }

    override fun onDraw(canvas: Canvas) {
        val left = paddingLeft + labelW
        val top = paddingTop + monthH

        // ── Mřížka (posouvá se) ──
        canvas.save()
        canvas.clipRect(left, 0f, (width - paddingRight).toFloat(), height.toFloat())
        var lastMonthLabelX = -1e9f
        columns.forEachIndexed { w, week ->
            val x = left + w * step - offsetX
            // Popisek měsíce nad týdnem, ve kterém měsíc začíná (a nad prvním sloupcem)
            val monthStart = week.firstOrNull { it?.dayOfMonth == 1 } ?: if (w == 0) week.first() else null
            if (monthStart != null && x - lastMonthLabelX > 3 * step) {
                canvas.drawText(monthStart.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale("cs")), x, paddingTop + 11 * dp, text)
                lastMonthLabelX = x
            }
            if (x + cell < left || x > width) return@forEachIndexed
            week.forEachIndexed { r, day ->
                if (day == null) return@forEachIndexed
                val y = top + r * step
                rect.set(x, y, x + cell, y + cell)
                val lv = levels[day] ?: Heatmap.NO_DATA
                fill.color = if (lv < 0) noDataColor else levelColors[lv.coerceIn(0, 4)]
                canvas.drawRoundRect(rect, corner, corner, fill)
                if (day == selected) canvas.drawRoundRect(rect, corner, corner, stroke)
            }
        }
        canvas.restore()

        // ── Popisky dnů (pevné) ──
        fill.color = cardColor
        canvas.drawRect(0f, 0f, left - gap, height.toFloat(), fill)
        listOf(0 to "Po", 2 to "St", 4 to "Pá").forEach { (r, s) ->
            canvas.drawText(s, paddingLeft.toFloat(), top + r * step + cell - 2 * dp, text)
        }

        // ── Legenda: Méně ▢▢▢▢▢ Více ──
        val ly = top + 7 * step + 6 * dp
        val more = "Více"
        var lx = width - paddingRight - text.measureText(more)
        canvas.drawText(more, lx, ly + cell - 2 * dp, text)
        lx -= gap + cell
        for (lv in 4 downTo 0) {
            rect.set(lx, ly, lx + cell, ly + cell)
            fill.color = levelColors[lv]
            canvas.drawRoundRect(rect, corner, corner, fill)
            lx -= step
        }
        val less = "Méně"
        canvas.drawText(less, lx + step - gap - text.measureText(less) - 2 * dp, ly + cell - 2 * dp, text)
    }

    private fun dayAt(px: Float, py: Float): LocalDate? {
        val left = paddingLeft + labelW
        val top = paddingTop + monthH
        if (px < left || py < top) return null
        val w = ((px - left + offsetX) / step).toInt()
        val r = ((py - top) / step).toInt()
        if (r !in 0..6) return null
        return columns.getOrNull(w)?.getOrNull(r)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return gestures.onTouchEvent(event) || super.onTouchEvent(event)
    }
}
