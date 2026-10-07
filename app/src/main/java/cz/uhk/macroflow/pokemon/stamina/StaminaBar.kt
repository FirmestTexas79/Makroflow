package cz.uhk.macroflow.pokemon.stamina

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.res.ResourcesCompat
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.ui.StepBarArt

/**
 * Bar energie nahoře v Makrosvětu (docs/adr/0065): stejný dřevěný rámeček jako ukazatel kroků,
 * jantarová náplň a přes ni zlatý overstim. Vedle číslo „100 +32“. Při útratě krátce vyletí „−3“.
 */
class StaminaBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var base = Stamina.BASE_MAX
    private var over = 0
    private var shownBase = base.toFloat()
    private var shownOver = 0f
    private var animator: ValueAnimator? = null
    private var delta = 0
    private var deltaT = 0f
    private var deltaAnim: ValueAnimator? = null
    private var bitmap: Bitmap? = null
    private var bitmapKey = ""
    private val pixels = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val dst = Rect()
    private val font = runCatching { ResourcesCompat.getFont(context, R.font.jersey_15) }.getOrNull()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font; color = Color.parseColor("#FEFAE0"); setShadowLayer(3f, 0f, 2f, Color.parseColor("#CC1A1208"))
    }
    private val overText = Paint(text).apply { color = Color.parseColor("#FFE27A") }
    private val deltaText = Paint(text)
    private val boltPaint = Paint().apply { isAntiAlias = false }

    private companion object {
        val OUTLINE = Color.parseColor("#2E1B0E")
        val BOLT_FILL = Color.parseColor("#F2C14E")
        val BOLT_LIGHT = Color.parseColor("#FFF1B8")
        val BOLT = listOf(
            "..###",
            ".#oo#",
            ".#o#.",
            "#ox##",
            "#xxx#",
            "##x#.",
            ".#x#.",
            ".##..",
            ".#...")
    }

    fun set(state: Stamina.State, animate: Boolean = true) {
        val spent = (base + over) - state.total
        base = state.base; over = state.over
        contentDescription = "Energie ${state.base}" + if (state.over > 0) " plus ${state.over}" else ""
        animator?.cancel()
        if (!animate) { shownBase = base.toFloat(); shownOver = over.toFloat(); invalidate(); return }
        val fromB = shownBase; val fromO = shownOver
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450; interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                shownBase = fromB + (base - fromB) * t; shownOver = fromO + (over - fromO) * t; invalidate()
            }
            start()
        }
        if (spent != 0) {
            delta = -spent
            deltaAnim?.cancel()
            deltaAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 900
                addUpdateListener { deltaT = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val artW = StepBarArt.BAR_W; val artH = StepBarArt.H
        val scale = maxOf(1, height / artH)
        val baseCols = StepBarArt.fillColumns(shownBase / Stamina.BASE_MAX)
        val overCols = if (shownOver <= 0f) 0 else StepBarArt.fillColumns(shownOver / Stamina.BASE_MAX)
        val key = "$baseCols:$overCols"
        if (key != bitmapKey || bitmap == null) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(StepBarArt.energyBar(baseCols, overCols), artW, artH, Bitmap.Config.ARGB_8888)
            bitmapKey = key
        }
        val w = artW * scale; val h = artH * scale
        val top = (height - h) / 2

        // pixelový blesk před barem (5 × 9 art pixelů)
        // blesk stejně vysoký jako bar, pixely zaokrouhlené na celé px
        val u = maxOf(1, (h * 1.15f / BOLT.size).toInt()).toFloat()
        val boltW = (BOLT[0].length + 1) * u
        val by = top + (h - BOLT.size * u) / 2
        for ((row, line) in BOLT.withIndex()) for ((col, ch) in line.withIndex()) {
            if (ch == '.') continue
            boltPaint.color = if (ch == '#') OUTLINE else if (ch == 'o') BOLT_LIGHT else BOLT_FILL
            canvas.drawRect(col * u, by + row * u, (col + 1) * u, by + (row + 1) * u, boltPaint)
        }

        val bx = boltW.toInt()
        dst.set(bx, top, bx + w, top + h)
        canvas.drawBitmap(bitmap!!, null, dst, pixels)

        text.textSize = h * 1.05f; overText.textSize = text.textSize
        val baseline = top + h * 0.82f
        var x = bx + w + h * 0.3f
        val num = base.toString()
        canvas.drawText(num, x, baseline, text)
        x += text.measureText(num)
        if (over > 0) { canvas.drawText(" +$over", x, baseline, overText); x += overText.measureText(" +$over") }

        // útrata/zisk krátce zazáří vedle čísla a zmizí
        if (deltaAnim?.isRunning == true && delta != 0) {
            deltaText.textSize = text.textSize * 0.85f
            deltaText.color = if (delta < 0) Color.parseColor("#FF9A7A") else Color.parseColor("#C8DC8A")
            deltaText.alpha = (255 * (1f - deltaT)).toInt()
            val label = if (delta < 0) " −${-delta}" else " +$delta"
            canvas.drawText(label, x, baseline - h * 0.35f * deltaT, deltaText)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel(); deltaAnim?.cancel()
        bitmap?.recycle(); bitmap = null; bitmapKey = ""
    }
}
