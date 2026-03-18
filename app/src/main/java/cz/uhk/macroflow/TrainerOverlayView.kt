package cz.uhk.macroflow

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class TrainerOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var points = listOf<PointF>()
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDA15E")
        style = Paint.Style.FILL
    }

    fun updatePoints(newPoints: List<PointF>) {
        this.points = newPoints
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        for (point in points) {
            // Data už chodí předpřipravená v rozsahu 0-1 pro portrait
            val x = point.x * w
            val y = point.y * h

            dotPaint.setShadowLayer(20f, 0f, 0f, dotPaint.color)
            canvas.drawCircle(x, y, 25f, dotPaint)
            dotPaint.clearShadowLayer()
        }
    }
}