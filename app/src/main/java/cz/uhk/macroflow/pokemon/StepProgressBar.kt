package cz.uhk.macroflow.pokemon.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Ukazatel denních kroků na mapě (docs/adr/0032): pixelový dřevěný rámeček se zapuštěnou
 * drážkou a zelenou náplní. Po splnění cíle se změní na čtvereček se zelenou fajfkou.
 * Kresba je v [StepBarArt] (čistý Kotlin, testy); tady jen zvětšení bez vyhlazení a animace.
 */
class StepProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var steps = 0
    private var goal = 5000
    private var shown = 0f
    private var animator: ValueAnimator? = null
    private var bitmap: Bitmap? = null
    private var bitmapKey = ""
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val dst = Rect()

    fun setProgress(currentSteps: Int, stepGoal: Int = 5000) {
        steps = currentSteps
        goal = stepGoal.coerceAtLeast(1)
        val target = (steps.toFloat() / goal).coerceIn(0f, 1f)
        contentDescription = if (steps >= goal) "Denní krokový cíl splněn" else "Kroky: $steps z $goal"
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, target).apply {
            duration = 600; interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val done = steps >= goal
        val artW = if (done) StepBarArt.SQUARE else StepBarArt.BAR_W
        val artH = StepBarArt.H
        val scale = maxOf(1, height / artH)
        val fillPx = if (done) 0 else StepBarArt.fillColumns(shown)
        val key = "$done:$fillPx"
        if (key != bitmapKey || bitmap == null) {
            bitmap?.recycle()
            val px = if (done) StepBarArt.check() else StepBarArt.bar(fillPx)
            bitmap = Bitmap.createBitmap(px, artW, artH, Bitmap.Config.ARGB_8888)
            bitmapKey = key
        }
        val w = artW * scale; val h = artH * scale
        val left = width - w                      // zarovnáno doprava (ukazatel je v pravém rohu)
        val top = (height - h) / 2
        dst.set(left, top, left + w, top + h)
        canvas.drawBitmap(bitmap!!, null, dst, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        bitmap?.recycle(); bitmap = null; bitmapKey = ""
    }
}
