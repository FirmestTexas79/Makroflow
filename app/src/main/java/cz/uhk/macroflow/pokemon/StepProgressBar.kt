package cz.uhk.macroflow.pokemon.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class StepProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var steps: Int = 0
    private var goal: Int = 5000

    // Barvy z tvého manuálu[cite: 3]
    private val colorPrimary = Color.parseColor("#606C38") // Tmavě zelená
    private val colorBg = Color.parseColor("#FEFAE0")      // Krémová
    private val colorBorder = Color.parseColor("#283618")  // Nejtmavší

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun setProgress(currentSteps: Int, stepGoal: Int = 5000) {
        this.steps = currentSteps
        this.goal = stepGoal
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val padding = 4f
        val radius = 8f

        // 1. Pozadí (Border)
        paint.color = colorBorder
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)

        // 2. Vnitřní prázdná plocha
        paint.color = colorBg
        rect.set(padding, padding, width - padding, height - padding)
        canvas.drawRoundRect(rect, radius, radius, paint)

        // 3. Samotný Progress
        val progressWidth = (width - 2 * padding) * (steps.coerceAtMost(goal).toFloat() / goal)
        if (progressWidth > 0) {
            paint.color = colorPrimary
            rect.set(padding, padding, padding + progressWidth, height - padding)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}