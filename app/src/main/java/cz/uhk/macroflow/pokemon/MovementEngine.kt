package cz.uhk.macroflow.pokemon

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

class MovementEngine(
    private val context: Context,
    private val ashView: ImageView,
    private val mapBackground: ImageView
) {
    companion object {
        const val NORMAL_SPEED = 3L
        const val FAST_SPEED = 1L
        private const val ANIM_FRAME_MS = 110L
        private val TRANSPARENT_TARGET = Color.parseColor("#FF7F27")
    }

    data class Waypoint(val id: String, val pos: PointF, val neighbors: List<String>)

    // Změněno na var, aby bylo možné nahrát graf jiného biomu
    var navigationGraph: List<Waypoint> = emptyList()

    private var currentPosition = PointF(0.480f, 0.275f)
    var isWalking = false
    var currentSpeed = NORMAL_SPEED
    private val handler = Handler(Looper.getMainLooper())
    private var currentDirection = 0

    /**
     * Klíčová metoda pro přepínání biomů.
     * Přemaže graf a resetuje postavičku na novou startovní pozici.
     */
    fun updateBiome(newGraph: List<Waypoint>, startPos: PointF) {
        cancel()
        this.navigationGraph = newGraph
        resetToPosition(startPos)
    }

    fun walkToNode(targetId: String, onFinished: () -> Unit = {}) {
        if (isWalking) return
        val startNode = findClosestNode(currentPosition)
        val path = findPath(startNode, targetId)
        if (path != null) {
            val pixelPoints = path.map { id ->
                val wp = navigationGraph.find { it.id == id } ?: return@map PointF(0f, 0f)
                PointF(wp.pos.x * mapBackground.width, wp.pos.y * mapBackground.height)
            }
            isWalking = true
            processNextMove(0, pixelPoints) {
                val finalNode = navigationGraph.find { it.id == targetId }
                if (finalNode != null) currentPosition = finalNode.pos
                onFinished()
            }
        }
    }

    private fun findPath(startId: String, endId: String): List<String>? {
        val distances = mutableMapOf<String, Float>().withDefault { Float.MAX_VALUE }
        val previous = mutableMapOf<String, String?>()
        val nodes = PriorityQueue<Pair<String, Float>>(compareBy { it.second })

        distances[startId] = 0f
        nodes.add(startId to 0f)

        while (nodes.isNotEmpty()) {
            val (current, dist) = nodes.poll()!!
            if (current == endId) break
            navigationGraph.find { it.id == current }?.neighbors?.forEach { neighborId ->
                val neighborNode = navigationGraph.find { it.id == neighborId } ?: return@forEach
                val alt = dist + getDistance(navigationGraph.find { it.id == current }!!.pos, neighborNode.pos)
                if (alt < distances.getValue(neighborId)) {
                    distances[neighborId] = alt
                    previous[neighborId] = current
                    nodes.add(neighborId to alt)
                }
            }
        }
        val path = mutableListOf<String>()
        var curr: String? = endId
        while (curr != null) { path.add(0, curr); curr = previous[curr] }
        return if (path.isNotEmpty() && path.first() == startId) path else null
    }

    private fun findClosestNode(pos: PointF): String =
        navigationGraph.minByOrNull { getDistance(pos, it.pos) }?.id ?: "spawn"

    private fun getDistance(p1: PointF, p2: PointF): Float =
        sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y))

    private fun processNextMove(index: Int, points: List<PointF>, onFinished: () -> Unit) {
        if (index >= points.size) {
            isWalking = false
            updateSprite(1, currentDirection)
            onFinished()
            return
        }
        val tX = points[index].x - ashView.width / 2f
        val tY = points[index].y - ashView.height.toFloat()
        val dx = tX - ashView.x
        val dy = tY - ashView.y

        currentDirection = if (abs(dx) > abs(dy)) (if (dx > 0) 3 else 2) else (if (dy > 0) 0 else 1)
        startAnimationLoop()

        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        ashView.animate().x(tX).y(tY).setDuration((dist * currentSpeed).toLong())
            .setInterpolator(LinearInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (isWalking) processNextMove(index + 1, points, onFinished)
                }
            }).start()
    }

    private fun startAnimationLoop() {
        handler.removeCallbacksAndMessages(null)
        val runnable = object : Runnable {
            override fun run() {
                if (!isWalking) return
                updateSprite(if (System.currentTimeMillis() % 400 < 200) 0 else 2, currentDirection)
                handler.postDelayed(this, ANIM_FRAME_MS)
            }
        }
        handler.post(runnable)
    }

    fun resetToPosition(relPos: PointF) {
        currentPosition = relPos
        mapBackground.post {
            ashView.x = relPos.x * mapBackground.width - (ashView.width / 2f)
            ashView.y = relPos.y * mapBackground.height - ashView.height.toFloat()
            updateSprite(1, 0)
        }
    }

    private fun updateSprite(step: Int, direction: Int) {
        val dirKey = when (direction) { 0 -> "down"; 1 -> "up"; 2 -> "left"; 3 -> "right"; else -> "down" }
        val suffix = when (step) { 1 -> "idle"; 0 -> "1"; else -> "2" }
        val resId = context.resources.getIdentifier("ash_${dirKey}_$suffix", "drawable", context.packageName)
        if (resId != 0) {
            ContextCompat.getDrawable(context, resId)?.let { drawable ->
                ashView.setImageBitmap(removeBackground(drawable, TRANSPARENT_TARGET))
            }
        }
    }

    private fun removeBackground(drawable: android.graphics.drawable.Drawable, color: Int): Bitmap? {
        val bitmap = (drawable as? BitmapDrawable)?.bitmap?.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) if (pixels[i] == color) pixels[i] = Color.TRANSPARENT
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }

    fun cancel() {
        ashView.animate().cancel()
        isWalking = false
        handler.removeCallbacksAndMessages(null)
    }
}