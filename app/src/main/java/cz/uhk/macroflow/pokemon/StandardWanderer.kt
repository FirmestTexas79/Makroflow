package cz.uhk.macroflow.pokemon

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.common.MainActivity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────
// ROZHRANÍ PRO PŘECHODY
// ─────────────────────────────────────────────

interface TransitionEffect {
    fun playDisappear(view: View, baseScale: Float, onDone: () -> Unit)
    fun playAppear(view: View, baseScale: Float, targetY: Float, onDone: () -> Unit)
}

// ─────────────────────────────────────────────
// 👻 GENGAR — fialový kouřový přechod
// ─────────────────────────────────────────────

class SmokeTransitionEffect(private val purple: Boolean = false) : TransitionEffect {
    private val dp = android.content.res.Resources.getSystem().displayMetrics.density

    private val colorA get() = if (purple) Color.parseColor("#9167AB") else Color.parseColor("#CCE0E0E0")
    private val colorB get() = if (purple) Color.parseColor("#703F8F") else Color.parseColor("#CCBDBDBD")
    private val colorC get() = if (purple) Color.parseColor("#E6D7FF") else Color.parseColor("#99C0C0C0")

    private fun randomColor() = listOf(colorA, colorB, colorC).random()

    override fun playDisappear(view: View, baseScale: Float, onDone: () -> Unit) {
        val parent = view.parent as? ViewGroup ?: run { onDone(); return }
        val cx = view.x + view.width / 2f
        val cy = view.y + view.height / 2f

        for (i in 0..7) {
            val size = ((18 * dp)).toInt()
            val smoke = View(view.context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(randomColor())
                }
                layoutParams = ViewGroup.LayoutParams(size, size)
                x = cx - size / 2f
                y = cy - size / 2f
                alpha = 0.7f
            }
            parent.addView(smoke)

            smoke.animate()
                .translationXBy((Random.nextFloat() - 0.5f) * 120f * dp)
                .translationYBy((Random.nextFloat() - 0.5f) * 120f * dp)
                .alpha(0f).scaleX(2f).scaleY(2f)
                .setDuration(Random.nextLong(400, 700))
                .withEndAction { parent.removeView(smoke) }
                .start()
        }

        view.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(350).withEndAction { onDone() }.start()
    }

    override fun playAppear(view: View, baseScale: Float, targetY: Float, onDone: () -> Unit) {
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.scaleX = 0f
        view.scaleY = 0f
        view.translationY = targetY

        view.animate()
            .alpha(1f).scaleX(baseScale).scaleY(baseScale)
            .setDuration(600)
            .setInterpolator(OvershootInterpolator())
            .withEndAction { onDone() }
            .start()
    }
}

// ─────────────────────────────────────────────
// ⛏️ DIGLETT — postupné zakopávání
// ─────────────────────────────────────────────

class DigTransitionEffect : TransitionEffect {
    private val dp = android.content.res.Resources.getSystem().displayMetrics.density

    override fun playDisappear(view: View, baseScale: Float, onDone: () -> Unit) {
        val parent = view.parent as? ViewGroup ?: run { onDone(); return }
        val cx = view.x + view.width / 2f
        val groundY = view.y + view.height

        // Efekt hlíny
        repeat(8) {
            val dirt = View(view.context).apply {
                setBackgroundColor(Color.parseColor(if (it % 2 == 0) "#5C4033" else "#3E2723"))
                layoutParams = ViewGroup.LayoutParams((6 * dp).toInt(), (6 * dp).toInt())
                x = cx
                y = groundY - 5 * dp
            }
            parent.addView(dirt)
            dirt.animate()
                .translationXBy((Random.nextFloat() - 0.5f) * 80f * dp)
                .translationYBy(-60f * dp)
                .alpha(0f).setDuration(500)
                .withEndAction { parent.removeView(dirt) }
                .start()
        }

        view.animate()
            .translationYBy(150f * dp)
            .setDuration(500)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { onDone() }
            .start()
    }

    override fun playAppear(view: View, baseScale: Float, targetY: Float, onDone: () -> Unit) {
        view.visibility = View.VISIBLE
        // ✅ Fix řetězení: Vždy začínáme z targetY + offsetu, ne z aktuální pozice
        view.translationY = targetY + 150f * dp
        view.scaleX = baseScale
        view.scaleY = baseScale
        view.alpha = 1f

        view.animate()
            .translationY(targetY)
            .setDuration(700)
            .setInterpolator(OvershootInterpolator(1.2f))
            .withEndAction { onDone() }
            .start()
    }
}

// ─────────────────────────────────────────────
// 🌀 HLAVNÍ TŘÍDA WANDERER
// ─────────────────────────────────────────────

class StandardWanderer(
    private val context: Context,
    private val pokemonView: ImageView,
    private val pokemonId: String,
    private val baseScale: Float = 1.0f,
    private val effect: TransitionEffect = if (pokemonId == "050") DigTransitionEffect()
    else SmokeTransitionEffect(purple = (pokemonId == "094"))
) : PokemonBehavior {

    var onKilled: (() -> Unit)? = null
    private val db = AppDatabase.getDatabase(context)
    private var running = false
    private var targetTranslationY = 0f

    private var wobbleAnim: ValueAnimator? = null
    private var moveAnim: ObjectAnimator? = null

    private var facingRight = true
    private val CENTER_START = 0.38f
    private val CENTER_END   = 0.62f
    private val viewW get() = pokemonView.width.toFloat().takeIf { it > 10f } ?: 100f

    override fun start() {
        if (running) return
        running = true

        pokemonView.post {
            val dp = context.resources.displayMetrics.density
            pokemonView.pivotY = pokemonView.height.toFloat()
            // ✅ Fix baseline Y
            targetTranslationY = if (pokemonId == "050") -8f * dp else -12f * dp

            applyFacing(facingRight)

            effect.playAppear(pokemonView, baseScale, targetTranslationY) {
                if (!running) return@playAppear
                startWobble()
                scheduleStep(Random.nextLong(1000, 2500))
            }
        }
    }

    override fun stop() {
        running = false
        moveAnim?.cancel()
        wobbleAnim?.cancel()
    }

    override fun onSpriteClicked() { killPokemon() }

    private fun applyFacing(right: Boolean) {
        // ✅ OPRAVA SMĚRU: Pokud sprite míří vlevo, scaleX 1 = vlevo, scaleX -1 = vpravo
        pokemonView.scaleX = baseScale * (if (right) -1f else 1f)
    }

    private fun startWobble() {
        wobbleAnim = ValueAnimator.ofFloat(-4f, 4f).apply {
            duration = 500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { pokemonView.rotation = it.animatedValue as Float }
            start()
        }
    }

    private fun scheduleStep(delay: Long) {
        pokemonView.postDelayed({ if (running) performStep() }, delay)
    }

    private fun performStep() {
        if (!running) return
        val parent = pokemonView.parent as? View ?: return
        val parentW = parent.width.toFloat()

        val currentX = pokemonView.x
        val gapStart = parentW * CENTER_START
        val gapEnd   = parentW * CENTER_END
        val currentlyOnLeft = currentX < gapStart

        // 40% šance na náhodný pokus o překonání středu
        val tryCrossing = Random.nextFloat() < 0.4f
        val targetX: Float
        val isCrossing: Boolean

        if (tryCrossing) {
            isCrossing = true
            targetX = if (currentlyOnLeft) {
                gapEnd + Random.nextFloat() * (parentW - viewW - gapEnd)
            } else {
                Random.nextFloat() * (gapStart - viewW)
            }
        } else {
            isCrossing = false
            targetX = if (currentlyOnLeft) {
                Random.nextFloat() * (gapStart - viewW)
            } else {
                gapEnd + Random.nextFloat() * (parentW - viewW - gapEnd)
            }
        }

        if (isCrossing) {
            // ✅ VYUŽITÍ SCHOPNOSTI PRO PŘECHOD PŘES STŘED
            moveAnim?.cancel()
            wobbleAnim?.cancel()
            effect.playDisappear(pokemonView, baseScale) {
                if (!running) return@playDisappear
                pokemonView.x = targetX
                facingRight = (targetX > currentX) // Logicky kam směřuje teleport
                applyFacing(facingRight)
                effect.playAppear(pokemonView, baseScale, targetTranslationY) {
                    if (running) {
                        startWobble()
                        scheduleStep(Random.nextLong(1500, 3000))
                    }
                }
            }
        } else {
            // Normální chůze v zóně
            val movingRight = targetX > currentX
            applyFacing(movingRight)

            val dist = abs(targetX - currentX)
            moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", currentX, targetX).apply {
                duration = (dist * 10).toLong().coerceIn(800, 2500)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) {
                        if (running) scheduleStep(Random.nextLong(1500, 4000))
                    }
                })
                start()
            }
        }
    }

    private fun killPokemon() {
        if (!running) return
        running = false
        moveAnim?.cancel()
        wobbleAnim?.cancel()
        Thread { db.capturedPokemonDao().deletePokemonById(pokemonId) }.start()

        pokemonView.animate()
            .rotation(720f).scaleX(0f).scaleY(0f).alpha(0f)
            .setDuration(700)
            .withEndAction {
                pokemonView.visibility = View.GONE
                onKilled?.invoke()
            }.start()
    }
}