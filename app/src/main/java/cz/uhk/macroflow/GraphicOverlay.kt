package cz.uhk.macroflow

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class GraphicOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val lock = Any()
    private var previewWidth = 0
    private var previewHeight = 0
    private var scaleFactor = 1.0f
    private var postScaleWidthOffset = 0f
    private var postScaleHeightOffset = 0f

    private var currentTrackedBox: RectF? = null
    private val points = mutableListOf<TrajectoryPoint>()

    private val paintDown = Paint().apply {
        color = Color.parseColor("#BC6C25")
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val paintUp = Paint().apply {
        color = Color.parseColor("#606C38")
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val paintOutline = Paint().apply {
        color = Color.parseColor("#DDA15E")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
        isAntiAlias = true
    }

    data class TrajectoryPoint(val x: Float, val y: Float, val goingDown: Boolean, val timestamp: Long)

    fun setPreviewSize(width: Int, height: Int) {
        synchronized(lock) {
            // ML Kit landscape -> Portrait
            previewWidth = height
            previewHeight = width
            calculateTransformation()
        }
        postInvalidate()
    }

    private fun calculateTransformation() {
        if (previewWidth <= 0 || previewHeight <= 0 || width <= 0 || height <= 0) return

        val viewAspectRatio = width.toFloat() / height.toFloat()
        val imageAspectRatio = previewWidth.toFloat() / previewHeight.toFloat()

        if (viewAspectRatio > imageAspectRatio) {
            scaleFactor = width.toFloat() / previewWidth
            postScaleHeightOffset = (height - previewHeight * scaleFactor) / 2f
            postScaleWidthOffset = 0f
        } else {
            scaleFactor = height.toFloat() / previewHeight
            postScaleWidthOffset = (width - previewWidth * scaleFactor) / 2f
            postScaleHeightOffset = 0f
        }
    }

    private fun translateX(x: Float): Float = x * scaleFactor + postScaleWidthOffset
    private fun translateY(y: Float): Float = y * scaleFactor + postScaleHeightOffset

    fun setTrackedBox(rect: RectF?) {
        synchronized(lock) {
            currentTrackedBox = rect?.let {
                RectF(translateX(it.left), translateY(it.top), translateX(it.right), translateY(it.bottom))
            }
        }
        postInvalidate()
    }

    fun addPoint(cameraX: Float, cameraY: Float, isDown: Boolean) {
        synchronized(lock) {
            points.add(TrajectoryPoint(translateX(cameraX), translateY(cameraY), isDown, System.currentTimeMillis()))
        }
        postInvalidate()
    }

    fun reset() {
        synchronized(lock) { points.clear(); currentTrackedBox = null }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(lock) {
            currentTrackedBox?.let { box ->
                val radius = (box.width() / 2f) * 0.98f
                canvas.drawCircle(box.centerX(), box.centerY(), radius, paintOutline)
            }

            if (points.size < 2) return
            for (i in 1 until points.size) {
                val p1 = points[i - 1]
                val p2 = points[i]
                if (Math.abs(p2.timestamp - p1.timestamp) < 500) {
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, if (p2.goingDown) paintDown else paintUp)
                }
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        calculateTransformation()
    }
}