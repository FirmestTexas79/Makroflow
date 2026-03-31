package cz.uhk.macroflow.pokemon

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import cz.uhk.macroflow.R
import kotlin.math.abs
import kotlin.math.sqrt

class PokemonMapActivity : AppCompatActivity() {

    private lateinit var ashView: ImageView
    private lateinit var mapBackground: ImageView
    private val handler = Handler(Looper.getMainLooper())

    private var isWalking = false
    private var currentStepFrame = 1
    private var currentDirection = 0 // 0:dolů, 1:nahoru, 2:vlevo, 3:vpravo

    // --- KONFIGURACE RYCHLOSTI ---
    // Čím nižší číslo, tím rychleji Ash běží (původně bylo 6)
    private val WALK_SPEED_MULTIPLIER = 3L
    // Rychlost animace nohou (původně 160ms)
    private val ANIM_FRAME_MS = 110L

    private val TRANSPARENT_TARGET = Color.parseColor("#FF7F27")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pokemon_map)

        ashView = findViewById(R.id.ashView)
        mapBackground = findViewById(R.id.mapBackground)

        // ✅ ZMENŠENÍ HRÁČE (Zmenšujeme rozměry View o 25%)
        ashView.layoutParams.width = (36 * resources.displayMetrics.density).toInt()
        ashView.layoutParams.height = (54 * resources.displayMetrics.density).toInt()
        ashView.requestLayout()

        mapBackground.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val pctX = event.x / v.width
                val pctY = event.y / v.height
                Log.d("MAP_COORD", "PointF(${pctX}f, ${pctY}f)")
            }
            true
        }

        findViewById<View>(R.id.btnExitMap).setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this) {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }

        mapBackground.post {
            resetAshPosition()
            setupHotspots()

            val target = intent.getStringExtra("TARGET_LOCATION")
            when (target) {
                "POKEDEX" -> findViewById<View>(R.id.hotspotPokedex).performClick()
                "INVENTORY" -> findViewById<View>(R.id.hotspotHome).performClick()
            }
        }
    }

    private fun resetAshPosition() {
        val w = mapBackground.width.toFloat()
        val h = mapBackground.height.toFloat()
        if (w == 0f || h == 0f) return

        // Střed postavy na souřadnice
        ashView.x = (610f / w) * w - (ashView.width / 2f)
        ashView.y = (810f / h) * h - (ashView.height / 2f)

        updateAshSprite(1, 0)
    }

    private fun setupHotspots() {
        val w = mapBackground.width.toFloat()
        val h = mapBackground.height.toFloat()
        if (w == 0f) return

        findViewById<View>(R.id.hotspotForest).setOnClickListener {
            val path = listOf(PointF(570f / w, 810f / h), PointF(570f / w, 320f / h))
            executeWalkSequence(path) { replaceMapContent(PokemonBattleFragment()) }
        }

        findViewById<View>(R.id.hotspotPokedex).setOnClickListener {
            val path = listOf(PointF(610f / w, 960f / h), PointF(900f / w, 960f / h), PointF(900f / w, 880f / h))
            executeWalkSequence(path) { replaceMapContent(PokedexFragment()) }
        }

        findViewById<View>(R.id.hotspotHome).setOnClickListener {
            val path = listOf(PointF(610f / w, 960f / h), PointF(380f / w, 960f / h), PointF(240f / w, 960f / h), PointF(240f / w, 880f / h))
            executeWalkSequence(path) { replaceMapContent(InventoryFragment()) }
        }
    }

    private fun executeWalkSequence(points: List<PointF>, onFinished: () -> Unit) {
        if (isWalking || points.isEmpty()) return
        isWalking = true
        processNextMove(0, points, onFinished)
    }

    private fun processNextMove(index: Int, points: List<PointF>, onFinished: () -> Unit) {
        if (index >= points.size) {
            isWalking = false
            updateAshSprite(1, currentDirection)
            onFinished()
            return
        }

        val targetX = points[index].x * mapBackground.width - (ashView.width / 2f)
        val targetY = points[index].y * mapBackground.height - (ashView.height / 2f)

        val dx = targetX - ashView.x
        val dy = targetY - ashView.y

        currentDirection = if (abs(dx) > abs(dy)) {
            if (dx > 0) 3 else 2
        } else {
            if (dy > 0) 0 else 1
        }

        startAnimationLoop()

        val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        if (distance < 5f) {
            processNextMove(index + 1, points, onFinished)
            return
        }

        // ✅ RYCHLEJŠÍ POHYB (multiplier snížen z 6 na 3)
        ashView.animate()
            .x(targetX)
            .y(targetY)
            .setDuration((distance * WALK_SPEED_MULTIPLIER).toLong())
            .setInterpolator(LinearInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    processNextMove(index + 1, points, onFinished)
                }
            })
            .start()
    }

    private fun startAnimationLoop() {
        handler.removeCallbacksAndMessages(null)
        val runnable = object : Runnable {
            override fun run() {
                if (!isWalking) return
                currentStepFrame = when(currentStepFrame) {
                    1 -> if (Math.random() > 0.5) 0 else 2
                    else -> 1
                }
                updateAshSprite(currentStepFrame, currentDirection)
                // ✅ RYCHLEJŠÍ KMITÁNÍ NOHOU
                handler.postDelayed(this, ANIM_FRAME_MS)
            }
        }
        handler.post(runnable)
    }

    private fun updateAshSprite(step: Int, direction: Int) {
        val dirKey = when (direction) {
            0 -> "down"; 1 -> "up"; 2 -> "left"; 3 -> "right"; else -> "down"
        }
        val suffix = when (step) {
            1 -> "idle"; 0 -> "1"; else -> "2"
        }

        val resId = resources.getIdentifier("ash_${dirKey}_$suffix", "drawable", packageName)
        if (resId != 0) {
            val drawable = ContextCompat.getDrawable(this, resId)
            drawable?.let {
                val cleaned = removeBackground(it, TRANSPARENT_TARGET)
                ashView.setImageBitmap(cleaned)
            }
        }
    }

    private fun removeBackground(drawable: android.graphics.drawable.Drawable, color: Int): Bitmap? {
        val bitmap = (drawable as? BitmapDrawable)?.bitmap?.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
            if (pixels[i] == color) pixels[i] = Color.TRANSPARENT
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }

    private fun replaceMapContent(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.mapFragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }
}