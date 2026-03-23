package cz.uhk.macroflow.pokemon

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import kotlin.math.abs
import kotlin.random.Random

class DiglettWanderer(
    private val context: Context,
    private val diglett: ImageView,
    private val fab: android.view.View
) {
    // Callback po zabití
    var onKilled: (() -> Unit)? = null

    private val dp = context.resources.displayMetrics.density

    // Zóny jako podíl šířky obrazovky
    private val DIG_START = 0.36f
    private val DIG_END   = 0.64f

    private val diglettW get() = diglett.width.toFloat().takeIf { it > 10f } ?: (100f * dp)
    private val screenW  get() = diglett.rootView.width.toFloat().takeIf { it > 100f } ?: (360f * dp)

    private val leftMin  get() = 0f
    private val leftMax  get() = screenW * DIG_START - diglettW
    private val rightMin get() = screenW * DIG_END
    private val rightMax get() = screenW - diglettW

    // Stav
    private var running     = false
    private var digging     = false
    private var inLeftZone  = true
    private var goingRight  = true
    private var stepsInZone = 0
    private var currentX    = 0f

    private var moveAnim:   ValueAnimator?  = null
    private var wobbleAnim: ObjectAnimator? = null

    // Klikání — 3× za 5s = smrt
    private var clickCount     = 0
    private var firstClickTime = 0L

    // ── Public API ────────────────────────────────────────────────────
    fun start() {
        if (running) return
        running     = true
        inLeftZone  = true
        goingRight  = true
        stepsInZone = 0
        currentX    = leftMin + Random.nextFloat() * (leftMax - leftMin) * 0.3f
        applyX(currentX)
        diglett.alpha  = 1f
        diglett.scaleY = 1f
        diglett.scaleX = 1f
        startWobble()
        scheduleStep(600)
    }

    fun stop() {
        running = false
        moveAnim?.cancel()
        wobbleAnim?.cancel()
    }

    fun onDiglettClicked() {
        val now = System.currentTimeMillis()
        if (clickCount == 0 || now - firstClickTime > 5000L) {
            clickCount     = 1
            firstClickTime = now
        } else {
            clickCount++
        }
        // Poskočení
        diglett.animate().scaleX(1.3f).scaleY(1.3f).setDuration(80)
            .withEndAction {
                diglett.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()

        if (clickCount >= 3) {
            clickCount = 0
            killDiglett()
        }
    }

    // ── Interní ───────────────────────────────────────────────────────
    private fun applyX(x: Float) { diglett.translationX = x }

    private fun startWobble() {
        wobbleAnim?.cancel()
        wobbleAnim = ObjectAnimator.ofFloat(diglett, "rotation", -10f, 10f).apply {
            duration      = 550
            repeatMode    = ValueAnimator.REVERSE
            repeatCount   = ValueAnimator.INFINITE
            interpolator  = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopWobble() {
        wobbleAnim?.cancel()
        diglett.animate().rotation(0f).setDuration(150).start()
    }

    private fun scheduleStep(delayMs: Long) {
        if (!running) return
        diglett.postDelayed({ if (running && !digging) pickMove() }, delayMs)
    }

    private fun pickMove() {
        if (!running || digging) return

        val zoneMin = if (inLeftZone) leftMin  else rightMin
        val zoneMax = if (inLeftZone) leftMax  else rightMax
        val digWall = if (inLeftZone) leftMax  else rightMin

        // 20% šance spontánní otočení
        if (Random.nextFloat() < 0.20f) goingRight = !goingRight
        diglett.scaleX = if (goingRight) 1f else -1f

        // Náhodný krok: 15–60% velikosti zóny
        val zoneSize = zoneMax - zoneMin
        val stepLen  = zoneSize * (0.15f + Random.nextFloat() * 0.45f)
        val rawTarget = if (goingRight) currentX + stepLen else currentX - stepLen
        val target    = rawTarget.coerceIn(zoneMin, zoneMax)

        val nearDig = if (inLeftZone) target >= digWall - (10f * dp)
        else            target <= digWall + (10f * dp)

        if (nearDig && stepsInZone >= 2) {
            val wallTarget = if (inLeftZone) (digWall - 4f * dp).coerceIn(zoneMin, zoneMax)
            else            (digWall + 4f * dp).coerceIn(zoneMin, zoneMax)
            moveTo(wallTarget) { diglett.post { digUnder() } }
        } else {
            moveTo(target) {
                stepsInZone++
                if (target <= zoneMin + 5f * dp)      goingRight = true
                else if (target >= zoneMax - 5f * dp) goingRight = false
                scheduleStep(Random.nextLong(300, 1500))
            }
        }
    }

    private fun moveTo(targetX: Float, onDone: () -> Unit) {
        moveAnim?.cancel()
        val dist   = abs(targetX - currentX)
        val speedDp = 25f + Random.nextFloat() * 20f
        val dur    = ((dist / dp) / speedDp * 1000f).toLong().coerceIn(300L, 4000L)

        moveAnim = ValueAnimator.ofFloat(currentX, targetX).apply {
            duration     = dur
            interpolator = LinearInterpolator()
            addUpdateListener {
                currentX = it.animatedValue as Float
                applyX(currentX)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (running && !digging) onDone()
                }
            })
            start()
        }
    }

    private fun digUnder() {
        if (!running || digging) return
        digging = true
        moveAnim?.cancel()
        stopWobble()

        // Animace zahrabání — ObjectAnimator zvlášť (AnimatorSet nemá interpolator property)
        val downScaleY = ObjectAnimator.ofFloat(diglett, "scaleY", 1f, 0f).apply {
            duration     = 350
            interpolator = AccelerateInterpolator(1.5f)
        }
        val downAlpha = ObjectAnimator.ofFloat(diglett, "alpha", 1f, 0f).apply {
            duration     = 280
            interpolator = AccelerateInterpolator(1.5f)
        }
        val downSet = AnimatorSet()
        downSet.playTogether(downScaleY, downAlpha)
        downSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                if (!running) return

                // Přepni zónu
                inLeftZone  = !inLeftZone
                stepsInZone = 0

                val newMin  = if (inLeftZone) leftMin  else rightMin
                val newMax  = if (inLeftZone) leftMax  else rightMax
                val safeMin = newMin + (newMax - newMin) * 0.15f
                val safeMax = newMax - (newMax - newMin) * 0.15f
                currentX    = safeMin + Random.nextFloat() * (safeMax - safeMin)

                // Jdi PRYČ od dig zóny
                goingRight          = !inLeftZone
                diglett.scaleX     = if (goingRight) 1f else -1f
                applyX(currentX)

                val pause = Random.nextLong(400, 900)
                diglett.postDelayed({
                    if (!running) return@postDelayed

                    val upScaleY = ObjectAnimator.ofFloat(diglett, "scaleY", 0f, 1.12f, 1f).apply {
                        duration     = 420
                        interpolator = OvershootInterpolator(1.8f)
                    }
                    val upAlpha = ObjectAnimator.ofFloat(diglett, "alpha", 0f, 1f).apply {
                        duration = 300
                    }
                    val upSet = AnimatorSet()
                    upSet.playTogether(upScaleY, upAlpha)
                    upSet.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: Animator) {
                            digging = false
                            if (!running) return
                            startWobble()
                            scheduleStep(Random.nextLong(400, 900))
                        }
                    })
                    upSet.start()
                }, pause)
            }
        })
        downSet.start()
    }

    private fun killDiglett() {
        if (!running) return
        running = false
        moveAnim?.cancel()
        wobbleAnim?.cancel()

        // Smaž uloženou hodnotu
        context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
            .edit().putBoolean("diglettAcquired", false).apply()

        // Animace smrti
        val spinAnim  = ObjectAnimator.ofFloat(diglett, "rotation", 0f, 720f).apply { duration = 600 }
        val scaleXAnim = ObjectAnimator.ofFloat(diglett, "scaleX", 1f, 0f).apply { duration = 600 }
        val scaleYAnim = ObjectAnimator.ofFloat(diglett, "scaleY", 1f, 0f).apply { duration = 600 }
        val alphaAnim = ObjectAnimator.ofFloat(diglett, "alpha", 1f, 0f).apply { duration = 500 }

        val deathSet = AnimatorSet()
        deathSet.playTogether(spinAnim, scaleXAnim, scaleYAnim, alphaAnim)
        deathSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                diglett.visibility = android.view.View.GONE
                diglett.rotation   = 0f
                diglett.scaleX     = 1f
                diglett.scaleY     = 1f
                diglett.alpha      = 1f
                onKilled?.invoke()
            }
        })
        deathSet.start()
    }
}