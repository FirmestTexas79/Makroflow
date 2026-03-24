package cz.uhk.macroflow.pokemon

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
import kotlin.math.abs
import kotlin.random.Random

interface TransitionEffect {
    fun playDisappear(view: View, onDone: () -> Unit)
    fun playAppear(view: View, onDone: () -> Unit)
}

/**
 * 🌪️ POKROČILÝ FIALOVÝ KOUŘOVÝ PŘECHOD
 */
class SmokeTransitionEffect : TransitionEffect {

    private val dp = android.content.res.Resources.getSystem().displayMetrics.density

    // ✅ FÁZE 1: ZMIZENÍ (Gengar vlevo)
    override fun playDisappear(view: View, onDone: () -> Unit) {
        val context = view.context
        val parent = view.parent as? ViewGroup ?: run { onDone(); return }

        val smokeView = ImageView(context).apply {
            setImageResource(R.drawable.fx_purple_smoke)
            scaleType = ImageView.ScaleType.FIT_CENTER

            val smokeSize = (view.width * 1.5f).toInt()
            layoutParams = ViewGroup.LayoutParams(smokeSize, smokeSize)

            // ✅ OPRAVA: Používáme skutečné view.y místo translationY
            translationX = view.translationX + (view.width / 2f) - (smokeSize / 2f)
            translationY = view.y + (view.height / 2f) - (smokeSize / 2f)

            alpha = 0f
            scaleX = 0.1f
            scaleY = 0.1f
        }
        parent.addView(smokeView)

        val smokeAppear = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(smokeView, "alpha", 0f, 1f).apply { duration = 300 },
                ObjectAnimator.ofFloat(smokeView, "scaleX", 0.1f, 1.0f).apply { duration = 450 },
                ObjectAnimator.ofFloat(smokeView, "scaleY", 0.1f, 1.0f).apply { duration = 450 }
            )
            interpolator = AccelerateDecelerateInterpolator()
        }

        val gengarDisappear = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply { duration = 350 },
                ObjectAnimator.ofFloat(view, "scaleX", view.scaleX, view.scaleX * 0.3f).apply { duration = 400 },
                ObjectAnimator.ofFloat(view, "scaleY", view.scaleY, 0.3f).apply { duration = 400 }
            )
            interpolator = AccelerateInterpolator()
            startDelay = 250
        }

        val smokeDissipate = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(smokeView, "scaleX", 1.0f, 0.2f).apply { duration = 400 },
                ObjectAnimator.ofFloat(smokeView, "scaleY", 1.0f, 0.2f).apply { duration = 400 },
                ObjectAnimator.ofFloat(smokeView, "alpha", 1f, 0f).apply { duration = 350 }
            )
            interpolator = AccelerateInterpolator()
            startDelay = 600
        }

        AnimatorSet().apply {
            playTogether(smokeAppear, gengarDisappear, smokeDissipate)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    parent.removeView(smokeView)
                    onDone()
                }
            })
            start()
        }
    }


    // ✅ FÁZE 2: OBJEVENÍ (Gengar vpravo)
    override fun playAppear(view: View, onDone: () -> Unit) {
        val context = view.context
        val parent = view.parent as? ViewGroup ?: run { onDone(); return }

        view.alpha = 0f

        val smokeView = ImageView(context).apply {
            setImageResource(R.drawable.fx_purple_smoke)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val smokeSize = (view.width * 1.5f).toInt()
            layoutParams = ViewGroup.LayoutParams(smokeSize, smokeSize)

            // ✅ OPRAVA: Používáme skutečné view.y místo translationY
            translationX = view.translationX + (view.width / 2f) - (smokeSize / 2f)
            translationY = view.y + (view.height / 2f) - (smokeSize / 2f)

            alpha = 0f
            scaleX = 0.2f
            scaleY = 0.2f
        }
        parent.addView(smokeView)

        val smokeAppear = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(smokeView, "alpha", 0f, 1f).apply { duration = 300 },
                ObjectAnimator.ofFloat(smokeView, "scaleX", 0.2f, 1.2f).apply { duration = 450 },
                ObjectAnimator.ofFloat(smokeView, "scaleY", 0.2f, 1.2f).apply { duration = 450 }
            )
            interpolator = DecelerateInterpolator()
        }

        val isFlipped = view.scaleX < 0
        val targetScaleX = if (isFlipped) -0.7f else 0.7f

        val gengarAppear = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply { duration = 500 },
                ObjectAnimator.ofFloat(view, "scaleX", view.scaleX * 0.3f, targetScaleX).apply { duration = 600 },
                ObjectAnimator.ofFloat(view, "scaleY", 0.3f, 0.7f).apply { duration = 600 }
            )
            interpolator = DecelerateInterpolator()
            startDelay = 350
        }

        val smokeDissipate = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(smokeView, "scaleX", 1.2f, 1.5f).apply { duration = 500 },
                ObjectAnimator.ofFloat(smokeView, "scaleY", 1.2f, 1.5f).apply { duration = 500 },
                ObjectAnimator.ofFloat(smokeView, "alpha", 1f, 0f).apply { duration = 450 }
            )
            interpolator = AccelerateInterpolator()
            startDelay = 800
        }

        AnimatorSet().apply {
            playTogether(smokeAppear, gengarAppear, smokeDissipate)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    parent.removeView(smokeView)
                    view.alpha = 1f
                    view.scaleX = targetScaleX
                    view.scaleY = 0.7f
                    onDone()
                }
            })
            start()
        }
    }
}

// ... (Zbytek souboru PokemonWanderer zůstává přesně stejný)
class DigTransitionEffect : TransitionEffect {
    override fun playDisappear(view: View, onDone: () -> Unit) {
        val downScaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0f).apply { duration = 350 }
        val downAlpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply { duration = 280 }

        AnimatorSet().apply {
            playTogether(downScaleY, downAlpha)
            interpolator = AccelerateInterpolator(1.5f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onDone() }
            })
            start()
        }
    }

    override fun playAppear(view: View, onDone: () -> Unit) {
        val upScaleY = ObjectAnimator.ofFloat(view, "scaleY", 0f, 1.12f, 1f).apply {
            duration = 420
            interpolator = OvershootInterpolator(1.8f)
        }
        val upAlpha = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply { duration = 300 }

        AnimatorSet().apply {
            playTogether(upScaleY, upAlpha)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onDone() }
            })
            start()
        }
    }
}

class PokemonWanderer(
    private val context: Context,
    private val pokemonView: ImageView,
    private val pokemonId: String,
    private val effect: TransitionEffect = SmokeTransitionEffect()
) {
    var onKilled: (() -> Unit)? = null

    private val dp = context.resources.displayMetrics.density
    private val db = AppDatabase.getDatabase(context)

    private val CENTER_START = 0.38f
    private val CENTER_END   = 0.62f

    private val viewW get() = pokemonView.width.toFloat().takeIf { it > 10f } ?: (100f * dp)
    private val screenW get() = pokemonView.rootView.width.toFloat().takeIf { it > 100f } ?: (360f * dp)

    private val leftMin  get() = 0f
    private val leftMax  get() = screenW * CENTER_START - viewW
    private val rightMin get() = screenW * CENTER_END
    private val rightMax get() = screenW - viewW

    private var running     = false
    private var transitioning = false
    private var inLeftZone  = true
    private var goingRight  = true
    private var stepsInZone = 0
    private var currentX    = 0f

    private var moveAnim:   ValueAnimator?  = null
    private var wobbleAnim: ObjectAnimator? = null

    private var clickCount     = 0
    private var firstClickTime = 0L

    fun start() {
        if (running) return
        running     = true
        inLeftZone  = true
        goingRight  = true
        stepsInZone = 0
        currentX = leftMin + Random.nextFloat() * (leftMax - leftMin) * 0.3f
        applyX(currentX)
        startWobble()
        scheduleStep(600)
    }

    fun stop() {
        running = false
        moveAnim?.cancel()
        wobbleAnim?.cancel()
    }

    fun onSpriteClicked() {
        val now = System.currentTimeMillis()
        if (clickCount == 0 || now - firstClickTime > 5000L) {
            clickCount     = 1
            firstClickTime = now
        } else {
            clickCount++
        }

        val baseScale = if (pokemonId == "094") 0.7f else 1.0f
        pokemonView.animate().scaleX(pokemonView.scaleX * 1.3f).scaleY(pokemonView.scaleY * 1.3f).setDuration(80)
            .withEndAction {
                pokemonView.animate().scaleX(if (goingRight) -baseScale else baseScale).scaleY(baseScale).setDuration(120).start()
            }.start()

        if (clickCount >= 3) {
            clickCount = 0
            killPokemon()
        }
    }

    private fun applyX(x: Float) { pokemonView.translationX = x }

    private fun startWobble() {
        wobbleAnim?.cancel()
        wobbleAnim = ObjectAnimator.ofFloat(pokemonView, "rotation", -6f, 6f).apply {
            duration      = 600
            repeatMode    = ValueAnimator.REVERSE
            repeatCount   = ValueAnimator.INFINITE
            interpolator  = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopWobble() {
        wobbleAnim?.cancel()
        pokemonView.animate().rotation(0f).setDuration(150).start()
    }

    private fun scheduleStep(delayMs: Long) {
        if (!running) return
        pokemonView.postDelayed({ if (running && !transitioning) pickMove() }, delayMs)
    }

    private fun pickMove() {
        if (!running || transitioning) return

        val zoneMin = if (inLeftZone) leftMin else rightMin
        val zoneMax = if (inLeftZone) leftMax else rightMax
        val wall    = if (inLeftZone) leftMax else rightMin

        if (Random.nextFloat() < 0.25f) goingRight = !goingRight

        val baseScale = if (pokemonId == "094") 0.7f else 1.0f
        pokemonView.scaleX = if (goingRight) -baseScale else baseScale

        val zoneSize = zoneMax - zoneMin
        val stepLen  = zoneSize * (0.20f + Random.nextFloat() * 0.50f)
        val rawTarget = if (goingRight) currentX + stepLen else currentX - stepLen
        val target    = rawTarget.coerceIn(zoneMin, zoneMax)

        val nearWall = if (inLeftZone) target >= wall - (15f * dp) else target <= wall + (15f * dp)

        if (nearWall && stepsInZone >= 1) {
            moveTo(wall) { pokemonView.post { crossTheGap() } }
        } else {
            moveTo(target) {
                stepsInZone++
                if (target <= zoneMin + 5f * dp)      goingRight = true
                else if (target >= zoneMax - 5f * dp) goingRight = false
                scheduleStep(Random.nextLong(600, 2000))
            }
        }
    }

    private fun moveTo(targetX: Float, onDone: () -> Unit) {
        moveAnim?.cancel()
        val dist = abs(targetX - currentX)
        val speedDp = 20f + Random.nextFloat() * 15f
        val dur = ((dist / dp) / speedDp * 1000f).toLong().coerceIn(400L, 3500L)

        moveAnim = ValueAnimator.ofFloat(currentX, targetX).apply {
            duration     = dur
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                currentX = it.animatedValue as Float
                applyX(currentX)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (running && !transitioning) onDone()
                }
            })
            start()
        }
    }

    private fun crossTheGap() {
        if (!running || transitioning) return
        transitioning = true
        moveAnim?.cancel()
        stopWobble()

        effect.playDisappear(pokemonView) {
            if (!running) return@playDisappear

            inLeftZone = !inLeftZone
            stepsInZone = 0

            val newMin = if (inLeftZone) leftMin else rightMin
            val newMax = if (inLeftZone) leftMax else rightMax
            currentX = newMin + (newMax - newMin) * 0.2f

            goingRight = !inLeftZone
            val baseScale = if (pokemonId == "094") 0.7f else 1.0f
            pokemonView.scaleX = if (goingRight) -baseScale else baseScale
            applyX(currentX)

            pokemonView.postDelayed({
                if (!running) return@postDelayed

                effect.playAppear(pokemonView) {
                    transitioning = false
                    if (!running) return@playAppear
                    startWobble()
                    scheduleStep(Random.nextLong(500, 1200))
                }
            }, Random.nextLong(600, 1200))
        }
    }

    private fun killPokemon() {
        if (!running) return
        running = false
        moveAnim?.cancel()
        wobbleAnim?.cancel()

        Thread {
            db.capturedPokemonDao().deletePokemonById(pokemonId)
        }.start()

        val spinAnim  = ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, 720f).apply { duration = 600 }
        val scaleXAnim = ObjectAnimator.ofFloat(pokemonView, "scaleX", 1f, 0f).apply { duration = 600 }
        val scaleYAnim = ObjectAnimator.ofFloat(pokemonView, "scaleY", 1f, 0f).apply { duration = 600 }
        val alphaAnim = ObjectAnimator.ofFloat(pokemonView, "alpha", 1f, 0f).apply { duration = 500 }

        AnimatorSet().apply {
            playTogether(spinAnim, scaleXAnim, scaleYAnim, alphaAnim)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.visibility = View.GONE
                    pokemonView.rotation = 0f
                    pokemonView.scaleX = 1f
                    pokemonView.scaleY = 1f
                    pokemonView.alpha = 1f
                    onKilled?.invoke()
                }
            })
            start()
        }
    }
}