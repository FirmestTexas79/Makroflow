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
        // bar v horní části, pod ním místo na vyletující „−3“
        val scale = maxOf(1, (height * 0.6f / artH).toInt())
        val baseCols = StepBarArt.fillColumns(shownBase / Stamina.BASE_MAX)
        val overCols = if (shownOver <= 0f) 0 else StepBarArt.fillColumns(shownOver / Stamina.BASE_MAX)
        val key = "$baseCols:$overCols"
        if (key != bitmapKey || bitmap == null) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(StepBarArt.energyBar(baseCols, overCols), artW, artH, Bitmap.Config.ARGB_8888)
            bitmapKey = key
        }
        val w = artW * scale; val h = artH * scale
        val top = 0
        dst.set(0, top, w, top + h)
        canvas.drawBitmap(bitmap!!, null, dst, pixels)

        text.textSize = h * 0.95f; overText.textSize = text.textSize
        val baseline = top + h * 0.8f
        val x = w + h * 0.25f
        val num = base.toString()
        canvas.drawText(num, x, baseline, text)
        if (over > 0) canvas.drawText(" +$over", x + text.measureText(num), baseline, overText)

        if (deltaAnim?.isRunning == true && delta != 0) {
            deltaText.textSize = text.textSize * 0.9f
            deltaText.color = if (delta < 0) Color.parseColor("#FF8A6A") else Color.parseColor("#B3C877")
            deltaText.alpha = (255 * (1f - deltaT)).toInt()
            val label = if (delta < 0) "−${-delta}" else "+$delta"
            canvas.drawText(label, w * 0.55f, baseline + h * 0.7f * deltaT, deltaText)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel(); deltaAnim?.cancel()
        bitmap?.recycle(); bitmap = null; bitmapKey = ""
    }
}
