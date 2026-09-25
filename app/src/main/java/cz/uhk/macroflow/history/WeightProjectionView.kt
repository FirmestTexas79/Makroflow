package cz.uhk.macroflow.history

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import cz.uhk.macroflow.energy.WeightProjection
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Graf biologické projekce (docs/adr/0020): ranní vážení jako body, vyhlazený trend jako plná čára,
 * projekce na 7 dní jako čárkovaná čára s 95% pásmem. U dne v minulosti se za předělem ukážou
 * i skutečná pozdější vážení – projekce jde porovnat se skutečností.
 *
 * Klepnutím nebo tažením prstu se ukáže hodnota ve dni. Vlastní Canvas místo knihovny: pásmo je
 * jedna uzavřená cesta (horní mez tam, spodní zpět), takže nepřekrývá mřížku ani historii.
 */
class WeightProjectionView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Data(
        val fromDay: Int,
        val toDay: Int,
        val asOfDay: Int,
        val todayDay: Int,
        /** Všechna vážení v okně – i ta po vybraném dni (zobrazí se jako „skutečnost“). */
        val weighIns: Map<Int, Double>,
        /** Vyhlazený trend do vybraného dne. */
        val trend: Map<Int, Double>,
        val forecast: List<WeightProjection.Forecast>
    )

    private val d = resources.displayMetrics.density
    private val dark = Color.parseColor("#283618")
    private val primary = Color.parseColor("#606C38")
    private val warm = Color.parseColor("#DDA15E")
    private val deep = Color.parseColor("#BC6C25")
    private val cream = Color.parseColor("#FEFAE0")

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(dark, 0x14); strokeWidth = 1 * d }
    private val axisText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(dark, 0x80); textSize = 10 * d }
    private val pastBg = Paint().apply { color = ColorUtils.setAlphaComponent(dark, 0x0A) }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(deep, 0x30) }
    private val trendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primary; style = Paint.Style.STROKE; strokeWidth = 3 * d; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val forecastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = deep; style = Paint.Style.STROKE; strokeWidth = 2.4f * d; strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(7 * d, 6 * d), 0f)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(primary, 0x99) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d; color = deep }
    private val ringFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cream }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(dark, 0x55); strokeWidth = 1.5f * d; style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4 * d, 4 * d), 0f)
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cream; textSize = 10.5f * d; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val emptyText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ColorUtils.setAlphaComponent(dark, 0x80); textSize = 13 * d; textAlign = Paint.Align.CENTER
    }
    private val tipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dark }
    private val tipTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(cream, 0xAA); textSize = 10 * d }
    private val tipLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cream; textSize = 12 * d; typeface = Typeface.DEFAULT_BOLD }
    private val scrubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(dark, 0x66); strokeWidth = 1.2f * d }

    var emptyMessage = "Zatím žádné vážení – udělej ranní check-in a projekce se začne učit."

    private var data: Data? = null
    private var reveal = 1f
    private var animator: ValueAnimator? = null
    private var scrubDay: Int? = null

    // Rozsah osy Y (zaokrouhlený na krok mřížky)
    private var yMin = 0.0
    private var yMax = 1.0
    private var yStep = 0.5

    private val left get() = paddingLeft + 34 * d
    private val right get() = width - paddingRight - 8 * d
    private val top get() = paddingTop + 26 * d
    private val bottom get() = height - paddingBottom - 22 * d

    fun setData(newData: Data?, animate: Boolean = true) {
        data = newData
        scrubDay = null
        newData?.let { computeYRange(it) }
        animator?.cancel()
        if (newData == null || !animate) { reveal = 1f; invalidate(); return }
        reveal = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 750
            interpolator = DecelerateInterpolator()
            addUpdateListener { reveal = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun computeYRange(data: Data) {
        val values = data.weighIns.filterKeys { it in data.fromDay..data.toDay }.values +
            data.trend.values + data.forecast.flatMap { listOf(it.lower, it.upper) }
        if (values.isEmpty()) return
        val lo = values.min(); val hi = values.max()
        val span = (hi - lo).coerceAtLeast(1.0)
        yStep = listOf(0.5, 1.0, 2.0, 5.0, 10.0).firstOrNull { span / it <= 4.5 } ?: 20.0
        yMin = floor((lo - 0.15 * span.coerceAtMost(2.0)) / yStep) * yStep
        yMax = ceil((hi + 0.15 * span.coerceAtMost(2.0)) / yStep) * yStep
        if (yMax - yMin < 2 * yStep) yMax = yMin + 2 * yStep
    }

    private fun x(day: Int, data: Data): Float =
        left + (day - data.fromDay).toFloat() / (data.toDay - data.fromDay).coerceAtLeast(1) * (right - left)

    private fun y(kg: Double): Float = (bottom - (kg - yMin) / (yMax - yMin) * (bottom - top)).toFloat()

    override fun onDraw(canvas: Canvas) {
        val data = data
        if (data == null) {
            val lines = wrap(emptyMessage, emptyText, width * 0.8f)
            lines.forEachIndexed { i, s -> canvas.drawText(s, width / 2f, height / 2f + (i - (lines.size - 1) / 2f) * 18 * d, emptyText) }
            return
        }
        val asX = x(data.asOfDay, data)

        // Minulost podbarvená, budoucnost čistá
        canvas.drawRoundRect(RectF(left, top - 8 * d, asX, bottom), 10 * d, 10 * d, pastBg)

        // Mřížka a popisky osy Y
        axisText.textAlign = Paint.Align.RIGHT
        var v = yMin
        while (v <= yMax + 1e-9) {
            val yy = y(v)
            canvas.drawLine(left, yy, right, yy, gridPaint)
            canvas.drawText(kg(v, yStep < 1.0), left - 6 * d, yy + 3.5f * d, axisText)
            v += yStep
        }

        // Popisky osy X – každý druhý den a vybraný den
        axisText.textAlign = Paint.Align.CENTER
        for (day in data.fromDay..data.toDay) {
            val even = (day - data.asOfDay) % 2 == 0
            if (!even) continue
            val date = LocalDate.ofEpochDay(day.toLong())
            val isAs = day == data.asOfDay
            axisText.typeface = if (isAs) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            axisText.alpha = if (isAs) 0xDD else 0x80
            canvas.drawText("${date.dayOfMonth}.${date.monthValue}.", x(day, data), bottom + 16 * d, axisText)
        }
        axisText.typeface = Typeface.DEFAULT; axisText.alpha = 0x80

        // Postupné odkrývání zleva
        val revealX = left + (right - left) * reveal
        canvas.save()
        canvas.clipRect(0f, 0f, revealX + 12 * d, height.toFloat())

        // 95% pásmo – jedna uzavřená cesta
        if (data.forecast.size >= 2) {
            val band = Path()
            data.forecast.forEachIndexed { i, f -> if (i == 0) band.moveTo(x(f.day, data), y(f.upper)) else band.lineTo(x(f.day, data), y(f.upper)) }
            data.forecast.asReversed().forEach { f -> band.lineTo(x(f.day, data), y(f.lower)) }
            band.close()
            canvas.drawPath(band, bandPaint)
        }

        // Trend
        val trendDays = data.trend.keys.filter { it in data.fromDay..data.asOfDay }.sorted()
        if (trendDays.size >= 2) {
            val p = Path()
            trendDays.forEachIndexed { i, day -> val px = x(day, data); val py = y(data.trend.getValue(day)); if (i == 0) p.moveTo(px, py) else p.lineTo(px, py) }
            canvas.drawPath(p, trendPaint)
        }

        // Projekce
        if (data.forecast.size >= 2) {
            val p = Path()
            data.forecast.forEachIndexed { i, f -> if (i == 0) p.moveTo(x(f.day, data), y(f.mean)) else p.lineTo(x(f.day, data), y(f.mean)) }
            canvas.drawPath(p, forecastPaint)
        }

        // Vážení: do vybraného dne body, po něm kroužky (skutečnost proti projekci)
        data.weighIns.filterKeys { it in data.fromDay..data.toDay }.forEach { (day, w) ->
            val px = x(day, data); val py = y(w)
            if (day <= data.asOfDay) canvas.drawCircle(px, py, 3.6f * d, dotPaint)
            else { canvas.drawCircle(px, py, 4.2f * d, ringFill); canvas.drawCircle(px, py, 4.2f * d, ringPaint) }
        }
        canvas.restore()

        // Předěl „dnes / vybraný den“
        canvas.drawLine(asX, top - 4 * d, asX, bottom, markerPaint)
        val asLabel = if (data.asOfDay == data.todayDay) "DNES" else LocalDate.ofEpochDay(data.asOfDay.toLong()).let { "${it.dayOfMonth}.${it.monthValue}." }
        pill(canvas, asLabel, asX, top - 14 * d, dark)

        // Hodnota na konci projekce
        if (reveal > 0.95f) data.forecast.lastOrNull()?.let { f ->
            val px = (x(f.day, data)).coerceAtMost(right - 26 * d)
            pill(canvas, "${kg(f.mean, true)} kg", px, (y(f.mean) - 14 * d).coerceAtLeast(top - 14 * d), deep)
        }

        scrubDay?.let { drawTooltip(canvas, data, it) }
    }

    private fun pill(canvas: Canvas, text: String, cx: Float, cy: Float, color: Int) {
        val w = pillText.measureText(text) + 14 * d
        val h = 18 * d
        pillPaint.color = color
        val l = (cx - w / 2).coerceIn(paddingLeft.toFloat(), width - paddingRight - w)
        canvas.drawRoundRect(RectF(l, cy - h / 2, l + w, cy + h / 2), h / 2, h / 2, pillPaint)
        canvas.drawText(text, l + w / 2, cy + 3.8f * d, pillText)
    }

    private fun drawTooltip(canvas: Canvas, data: Data, day: Int) {
        val px = x(day, data)
        canvas.drawLine(px, top - 4 * d, px, bottom, scrubPaint)
        val date = LocalDate.ofEpochDay(day.toLong())
        val title = "${dayName(date)} ${date.dayOfMonth}.${date.monthValue}."
        val lines = mutableListOf<String>()
        data.weighIns[day]?.let { lines += (if (day <= data.asOfDay) "Vážení " else "Skutečnost ") + "${kg(it, true)} kg" }
        if (day <= data.asOfDay) data.trend[day]?.let { lines += "Trend ${kg(it, true)} kg" }
        data.forecast.firstOrNull { it.day == day }?.takeIf { day > data.asOfDay || data.trend[day] == null }?.let {
            lines += "Projekce ${kg(it.mean, true)} kg"
            lines += "${kg(it.lower, true)}–${kg(it.upper, true)} kg (95 %)"
        }
        if (lines.isEmpty()) lines += "bez údaje"
        data.trend[day]?.let { t -> if (day <= data.asOfDay) data.weighIns[day]?.let { w ->
            val diff = w - t
            if (abs(diff) >= 0.1) lines += "voda/výkyv ${if (diff > 0) "+" else "−"}${kg(abs(diff), true)} kg"
        } }

        val w = (lines.map { tipLine.measureText(it) } + tipTitle.measureText(title)).max() + 20 * d
        val h = 22 * d + lines.size * 17 * d
        val l = if (px + 10 * d + w < right) px + 10 * d else px - 10 * d - w
        val t = top
        canvas.drawRoundRect(RectF(l, t, l + w, t + h), 12 * d, 12 * d, tipBg)
        canvas.drawText(title, l + 10 * d, t + 15 * d, tipTitle)
        lines.forEachIndexed { i, s -> canvas.drawText(s, l + 10 * d, t + 32 * d + i * 17 * d, tipLine) }
    }

    // ── Dotyk: klepnutí / tažení ukáže hodnotu dne ─────────────────────────

    private var downX = 0f
    private var downY = 0f
    private var scrubbing = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val hideTip = Runnable { scrubDay = null; invalidate() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val data = data ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; scrubbing = false; removeCallbacks(hideTip); return true }
            MotionEvent.ACTION_MOVE -> {
                if (!scrubbing && abs(e.x - downX) > slop && abs(e.x - downX) > abs(e.y - downY)) {
                    scrubbing = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (scrubbing) { scrubDay = dayAt(e.x, data); invalidate() }
            }
            MotionEvent.ACTION_UP -> {
                if (!scrubbing) { scrubDay = dayAt(e.x, data); invalidate(); performClick() }
                parent?.requestDisallowInterceptTouchEvent(false)
                postDelayed(hideTip, 2500)
            }
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); postDelayed(hideTip, 1200) }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun dayAt(px: Float, data: Data): Int {
        val f = ((px - left) / (right - left)).coerceIn(0f, 1f)
        return data.fromDay + (f * (data.toDay - data.fromDay)).roundToInt()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        removeCallbacks(hideTip)
        super.onDetachedFromWindow()
    }

    // ── Pomocné ─────────────────────────────────────────────────────────────

    private fun kg(v: Double, oneDecimal: Boolean): String =
        if (oneDecimal) String.format(java.util.Locale.US, "%.1f", v).replace('.', ',')
        else v.roundToInt().toString()

    private fun dayName(d: LocalDate) = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne")[d.dayOfWeek.value - 1]

    private fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        var line = ""
        text.split(" ").forEach { w ->
            val next = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(next) > maxW && line.isNotEmpty()) { out += line; line = w } else line = next
        }
        if (line.isNotEmpty()) out += line
        return out
    }
}
