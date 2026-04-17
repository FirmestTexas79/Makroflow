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

    // --- NAVIGAČNÍ GRAF PODLE TVÉHO GRIDU ---
    private val navigationGraph = listOf(
        Waypoint("les",        PointF(0.460f, 0.120f), listOf("spawn")),
        Waypoint("spawn",      PointF(0.480f, 0.275f), listOf("les", "krizovatka_hlavni")),
        Waypoint("krizovatka_hlavni", PointF(0.480f, 0.340f), listOf("spawn", "rozbocka_zapad", "prah_pokedex")),

        // Pokedex větev (východ)
        Waypoint("prah_pokedex", PointF(0.650f, 0.340f), listOf("krizovatka_hlavni", "pokedex")),
        Waypoint("pokedex",      PointF(0.650f, 0.300f), listOf("prah_pokedex")),

        // Domov a Obchod větev (západ)
        Waypoint("rozbocka_zapad", PointF(0.370f, 0.340f), listOf("krizovatka_hlavni", "prah_domova", "roh_obchod")),
        Waypoint("prah_domova",    PointF(0.255f, 0.340f), listOf("rozbocka_zapad", "domov")),
        Waypoint("domov",          PointF(0.255f, 0.300f), listOf("prah_domova")),

        // Obchod větev (jih)
        Waypoint("roh_obchod",     PointF(0.370f, 0.505f), listOf("rozbocka_zapad", "prah_obchodu")),
        Waypoint("prah_obchodu",   PointF(0.640f, 0.505f), listOf("roh_obchod", "obchod")),
        Waypoint("obchod",         PointF(0.640f, 0.480f), listOf("prah_obchodu"))
    )

    private var currentPosition = PointF(0.480f, 0.275f) // Start na Spawnu
    var isWalking = false
    var currentSpeed = NORMAL_SPEED
    private val handler = Handler(Looper.getMainLooper())
    private var currentDirection = 0

    fun walkToNode(targetId: String, onFinished: () -> Unit = {}) {
        if (isWalking) return
        val startNode = findClosestNode(currentPosition)
        val path = findPath(startNode, targetId)
        if (path != null) {
            val pixelPoints = path.map { id ->
                val wp = navigationGraph.find { it.id == id }!!
                PointF(wp.pos.x * mapBackground.width, wp.pos.y * mapBackground.height)
            }
            isWalking = true
            processNextMove(0, pixelPoints) {
                currentPosition = navigationGraph.find { it.id == targetId }!!.pos
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
                val neighborNode = navigationGraph.find { it.id == neighborId }!!
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
                override fun onAnimationEnd(a: Animator) { if (isWalking) processNextMove(index + 1, points, onFinished) }
            }).start()
    }

    private fun startAnimationLoop() {
        handler.removeCallbacksAndMessages(null)
        val runnable = object : Runnable {
            override fun run() {
                if (!isWalking) return
                updateSprite(if (System.currentTimeMillis() % 400 < 200) 0 else 2, currentDirection)
                handler.postDelayed(this, 110)
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

    fun cancel() { ashView.animate().cancel(); isWalking = false; handler.removeCallbacksAndMessages(null) }
}