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

    var navigationGraph: List<Waypoint> = emptyList()
    private var currentPosition = PointF(0.480f, 0.275f)
    var isWalking = false
    var currentSpeed = NORMAL_SPEED
    private val handler = Handler(Looper.getMainLooper())
    private var currentDirection = 0

    fun getCurrentPosition(): PointF = currentPosition

    fun updateBiome(newGraph: List<Waypoint>, startPos: PointF) {
        cancel()
        this.navigationGraph = newGraph
        resetToPosition(startPos)
    }

    // ── Chůze ────────────────────────────────────────────────────────────────
    //
    // Každá trasa má „token“. Zrušená animace volá onAnimationEnd stejně jako dokončená –
    // dřív to postavu přeteleportovalo na konec úseku a stará trasa běžela souběžně s novou
    // (cukání při dvojkliku). Teď se reaguje jen na dokončení aktuální trasy.

    private var walkToken = 0
    private var currentTarget: String? = null
    private var pendingFinish: (() -> Unit)? = null
    private var routeIds: List<String> = emptyList()
    private var routePoints: List<PointF> = emptyList()
    private var segment = 0

    fun walkToNode(targetId: String, onFinished: () -> Unit = {}) {
        pendingFinish = onFinished

        // Dvojklik na stejný cíl = jen zrychlit; úsek pokračuje z místa, kde postava je
        if (isWalking && targetId == currentTarget) {
            startSegment(++walkToken)
            return
        }

        // Za chůze nová trasa začíná uzlem, ke kterému postava právě jde (žádné couvání)
        val startNode = if (isWalking) routeIds.getOrNull(segment) ?: findClosestNode(currentPosition)
            else findClosestNode(currentPosition)

        if (!isWalking && startNode == targetId) {
            finishWalk()
            return
        }

        val path = findPath(startNode, targetId) ?: return
        routeIds = path
        routePoints = path.map { id ->
            val wp = navigationGraph.find { it.id == id } ?: return@map PointF(0f, 0f)
            PointF(wp.pos.x * mapBackground.width, wp.pos.y * mapBackground.height)
        }
        segment = 0
        currentTarget = targetId
        isWalking = true
        startAnimationLoop()
        startSegment(++walkToken)
    }

    /** Dojde (nebo pokračuje) k bodu routePoints[segment] aktuální rychlostí. */
    private fun startSegment(token: Int) {
        if (token != walkToken) return
        if (segment >= routePoints.size) { finishWalk(); return }

        val targetX = routePoints[segment].x - ashView.width / 2f
        val targetY = routePoints[segment].y - ashView.height.toFloat()
        val dx = targetX - ashView.x
        val dy = targetY - ashView.y
        if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
            currentDirection = if (abs(dx) > abs(dy)) (if (dx > 0) 3 else 2) else (if (dy > 0) 0 else 1)
        }
        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        ashView.animate().cancel()
        ashView.animate()
            .x(targetX).y(targetY)
            .setDuration((dist * currentSpeed).toLong())
            .setInterpolator(LinearInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(a: Animator) { cancelled = true }
                override fun onAnimationEnd(a: Animator) {
                    if (cancelled || token != walkToken) return
                    navigationGraph.find { it.id == routeIds[segment] }?.let { currentPosition = it.pos }
                    segment++
                    startSegment(token)
                }
            }).start()
    }

    private fun finishWalk() {
        currentTarget?.let { id -> navigationGraph.find { it.id == id }?.let { currentPosition = it.pos } }
        isWalking = false
        currentTarget = null
        ashView.animate().setListener(null)
        handler.removeCallbacksAndMessages(null)
        updateSprite(1, currentDirection)
        val done = pendingFinish
        pendingFinish = null
        done?.invoke()
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

    private fun startAnimationLoop() {
        handler.removeCallbacksAndMessages(null)
        val runnable = object : Runnable {
            override fun run() {
                if (!isWalking) return
                // Rychlá chůze = rychlejší kroky
                val frame = if (currentSpeed == FAST_SPEED) ANIM_FRAME_MS / 2 else ANIM_FRAME_MS
                updateSprite(if (System.currentTimeMillis() % (frame * 4) < frame * 2) 0 else 2, currentDirection)
                handler.postDelayed(this, frame)
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

    /** Snímky postavy s odstraněným pozadím – dřív se dekódovaly a přebarvovaly každých 110 ms (záseky GC). */
    private val spriteCache = HashMap<Int, Bitmap?>()
    private var shownSprite = 0

    private fun updateSprite(step: Int, direction: Int) {
        val dirKey = when (direction) { 0 -> "down"; 1 -> "up"; 2 -> "left"; 3 -> "right"; else -> "down" }
        val suffix = when (step) { 1 -> "idle"; 0 -> "1"; else -> "2" }
        val resId = context.resources.getIdentifier("ash_${dirKey}_$suffix", "drawable", context.packageName)
        if (resId == 0 || resId == shownSprite) return
        val bmp = spriteCache.getOrPut(resId) {
            ContextCompat.getDrawable(context, resId)?.let { removeBackground(it, TRANSPARENT_TARGET) }
        } ?: return
        shownSprite = resId
        ashView.setImageBitmap(bmp)
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
        walkToken++
        ashView.animate().cancel()
        ashView.animate().setListener(null)
        isWalking = false
        currentTarget = null
        pendingFinish = null
        handler.removeCallbacksAndMessages(null)
    }
}