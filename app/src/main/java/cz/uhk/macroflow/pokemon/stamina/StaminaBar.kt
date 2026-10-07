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
 * Svislý bar energie u levého kraje Makrosvěta (docs/adr/0065): stejný dřevěný rámeček jako
 * ukazatel kroků, jantarová náplň odspodu a přes ni zlatý overstim. Nahoře blesk, číslo a „+32“.
 * Při útratě krátce zazáří „−3“.
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
            "..####",
            "..#oo#",
            ".#oo#.",
            ".#o###",
            "#xxxx#",
            "###x#.",
            "..#x#.",
            ".#x#..",
            ".##...",
            "##....")
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
        // svislý bar u levého kraje: nahoře blesk a číslo, pod nimi bar plnící se odspodu
        val u = maxOf(1, (width * 0.5f / StepBarArt.H).toInt())   // velikost art pixelu
        val thick = StepBarArt.H * u
        val boltU = maxOf(1, (width * 0.55f / BOLT[0].length).toInt()).toFloat()
        val boltH = BOLT.size * boltU
        val bx0 = (width - BOLT[0].length * boltU) / 2
        for ((row, line) in BOLT.withIndex()) for ((col, ch) in line.withIndex()) {
            if (ch == '.') continue
            boltPaint.color = if (ch == '#') OUTLINE else if (ch == 'o') BOLT_LIGHT else BOLT_FILL
            canvas.drawRect(bx0 + col * boltU, row * boltU, bx0 + (col + 1) * boltU, (row + 1) * boltU, boltPaint)
        }

        text.textSize = width * 0.62f; overText.textSize = width * 0.48f
        text.textAlign = Paint.Align.CENTER; overText.textAlign = Paint.Align.CENTER
        var y = boltH + text.textSize * 0.95f
        canvas.drawText(base.toString(), width / 2f, y, text)
        if (over > 0) { y += overText.textSize * 1.0f; canvas.drawText("+$over", width / 2f, y, overText) }
        val barTop = (y + text.textSize * 0.35f).toInt()

        val lenPx = height - barTop
        val artLen = maxOf(16, lenPx / u)
        val inner = artLen - 8
        fun cols(v: Float) = if (v <= 0f) 0 else maxOf(1, (v / Stamina.BASE_MAX * inner).toInt().coerceAtMost(inner))
        val key = "${cols(shownBase)}:${cols(shownOver)}:$artLen"
        if (key != bitmapKey || bitmap == null) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(StepBarArt.energyBar(cols(shownBase), cols(shownOver), artLen), artLen, StepBarArt.H, Bitmap.Config.ARGB_8888)
            bitmapKey = key
        }
        val len = artLen * u
        val left = (width - thick) / 2f
        canvas.save()
        // otočení o −90°: začátek baru (náplň) je dole, konec nahoře
        canvas.translate(left, (barTop + len).toFloat())
        canvas.rotate(-90f)
        dst.set(0, 0, len, thick)
        canvas.drawBitmap(bitmap!!, null, dst, pixels)
        canvas.restore()

        // útrata/zisk krátce zazáří vedle čísla a odpluje dolů
        if (deltaAnim?.isRunning == true && delta != 0) {
            deltaText.textSize = width * 0.5f; deltaText.textAlign = Paint.Align.CENTER
            deltaText.color = if (delta < 0) Color.parseColor("#FF9A7A") else Color.parseColor("#C8DC8A")
            deltaText.alpha = (255 * (1f - deltaT)).toInt()
            val label = if (delta < 0) "−${-delta}" else "+$delta"
            canvas.drawText(label, width / 2f, barTop + text.textSize * (0.8f + 1.2f * deltaT), deltaText)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel(); deltaAnim?.cancel()
        bitmap?.recycle(); bitmap = null; bitmapKey = ""
    }
}
