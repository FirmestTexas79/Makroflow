package cz.uhk.macroflow.pokemon

import android.animation.*
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.*
import android.widget.ImageView
import android.widget.TextView
import cz.uhk.macroflow.data.AppDatabase
import kotlin.math.*
import kotlin.random.Random

// ─────────────────────────────────────────────
// ROZHRANÍ
// ─────────────────────────────────────────────

interface PokemonBehavior {
    fun start()
    fun stop()
    fun onSpriteClicked()
}

interface TransitionEffect {
    fun playDisappear(view: View, baseScale: Float, onDone: () -> Unit)
    fun playAppear(view: View, baseScale: Float, targetY: Float, onDone: () -> Unit)
}

// ─────────────────────────────────────────────
// TRANSITION EFFECTS
// ─────────────────────────────────────────────

class HeavyTransitionEffect : TransitionEffect {
    private val dp = android.content.res.Resources.getSystem().displayMetrics.density

    override fun playDisappear(view: View, baseScale: Float, onDone: () -> Unit) {
        view.animate()
            .translationYBy(100f * dp)
            .alpha(0f)
            .setDuration(400)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { onDone() }
            .start()
    }

    override fun playAppear(view: View, baseScale: Float, targetY: Float, onDone: () -> Unit) {
        val parent = view.parent as? ViewGroup
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = targetY - 50f * dp
        view.scaleX = baseScale
        view.scaleY = baseScale

        view.animate()
            .alpha(1f)
            .translationY(targetY)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator(1.4f))
            .withEndAction {
                parent?.let {
                    ObjectAnimator.ofFloat(it, "translationX", 0f, -12f * dp, 12f * dp, -6f * dp, 6f * dp, 0f)
                        .apply { duration = 450; start() }
                }
                onDone()
            }
            .start()
    }
}

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
        repeat(8) {
            val size = (18 * dp).toInt()
            val smoke = View(view.context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(randomColor()) }
                layoutParams = ViewGroup.LayoutParams(size, size)
                x = cx - size / 2f; y = cy - size / 2f; alpha = 0.7f
            }
            parent.addView(smoke)
            smoke.animate()
                .translationXBy((Random.nextFloat() - 0.5f) * 120f * dp)
                .translationYBy((Random.nextFloat() - 0.5f) * 120f * dp)
                .alpha(0f).scaleX(2f).scaleY(2f)
                .setDuration(Random.nextLong(400, 700))
                .withEndAction { parent.removeView(smoke) }.start()
        }
        view.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(350)
            .withEndAction { onDone() }.start()
    }

    override fun playAppear(view: View, baseScale: Float, targetY: Float, onDone: () -> Unit) {
        view.visibility = View.VISIBLE
        view.alpha = 0f; view.scaleX = 0f; view.scaleY = 0f; view.translationY = targetY
        view.animate().alpha(1f).scaleX(baseScale).scaleY(baseScale)
            .setDuration(600).setInterpolator(OvershootInterpolator())
            .withEndAction { onDone() }.start()
    }
}

class DigTransitionEffect : TransitionEffect {
    private val dp = android.content.res.Resources.getSystem().displayMetrics.density

    override fun playDisappear(view: View, baseScale: Float, onDone: () -> Unit) {
        val parent = view.parent as? ViewGroup ?: run { onDone(); return }
        val cx = view.x + view.width / 2f
        val groundY = view.y + view.height
        repeat(8) {
            val dirt = View(view.context).apply {
                setBackgroundColor(Color.parseColor(if (it % 2 == 0) "#5C4033" else "#3E2723"))
                layoutParams = ViewGroup.LayoutParams((6 * dp).toInt(), (6 * dp).toInt())
                x = cx; y = groundY - 5 * dp
            }
            parent.addView(dirt)
            dirt.animate()
                .translationXBy((Random.nextFloat() - 0.5f) * 80f * dp)
                .translationYBy(-60f * dp).alpha(0f).setDuration(500)
                .withEndAction { parent.removeView(dirt) }.start()
        }
        view.animate().translationYBy(150f * dp).setDuration(500)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { onDone() }.start()
    }

    override fun playAppear(view: View, baseScale: Float, targetY: Float, onDone: () -> Unit) {
        view.visibility = View.VISIBLE
        view.translationY = targetY + 150f * dp
        view.scaleX = baseScale; view.scaleY = baseScale; view.alpha = 1f
        view.animate().translationY(targetY).setDuration(700)
            .setInterpolator(OvershootInterpolator(1.2f))
            .withEndAction { onDone() }.start()
    }
}

// ─────────────────────────────────────────────
// HLAVNÍ WANDERER TŘÍDA
// ─────────────────────────────────────────────

class StandardWanderer(
    private val context: Context,
    private val pokemonView: ImageView,
    val pokemonId: String,       // Makromon ID: "001" - "031"
    private val baseScale: Float = 1.0f,
    private val effect: TransitionEffect = SmokeTransitionEffect(false)
) : PokemonBehavior {

    companion object {
        // PixelTransitionEffect pro speciální Makromony (Porygon styl)
        class PixelTransitionEffect : TransitionEffect {
            private val dp = android.content.res.Resources.getSystem().displayMetrics.density

            override fun playDisappear(view: View, baseScale: Float, onDone: () -> Unit) {
                val anim = ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = 500
                    addUpdateListener {
                        val v = it.animatedValue as Float
                        view.scaleX = baseScale * (if (view.scaleX < 0) -v else v)
                        view.scaleY = baseScale * v
                        view.alpha = v
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: Animator) { onDone() }
                    })
                }
                anim.start()
            }

            override fun playAppear(view: View, baseScale: Float, targetY: Float, onDone: () -> Unit) {
                view.visibility = View.VISIBLE
                view.alpha = 0f; view.scaleX = 0f; view.scaleY = 0f; view.translationY = targetY
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 600
                    addUpdateListener {
                        val v = it.animatedValue as Float
                        view.scaleX = baseScale * -v
                        view.scaleY = baseScale * v
                        view.alpha = v
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: Animator) { onDone() }
                    })
                    start()
                }
            }
        }
    }

    private val dp      = context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private var running  = false
    private var targetTranslationY = 0f
    private var wobbleAnim: ValueAnimator? = null
    private var moveAnim:   ObjectAnimator? = null
    private var idleAnim:   Animator? = null
    private var facingRight = true

    // Létající Makromoni (drží se výš)
    private val isFlying get() = pokemonId in listOf("003", "019")
    // Spící Makromoni
    private val isSleeping get() = pokemonId == "030" // Gudwin

    private val CENTER_START = 0.38f
    private val CENTER_END   = 0.62f
    private val viewW get()  = pokemonView.width.toFloat().takeIf { it > 10f } ?: (48f * dp)

    // ─────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────

    override fun start() {
        if (running) return
        running = true
        pokemonView.post {
            pokemonView.pivotY = pokemonView.height.toFloat()
            targetTranslationY = baseTranslationY()
            applyFacing(facingRight)
            effect.playAppear(pokemonView, baseScale, targetTranslationY) {
                if (!running) return@playAppear
                startIdleAnimation()
                if (!isFlying && !isSleeping) startWobble()
                if (!isSleeping) scheduleStep(Random.nextLong(1000, 2500))
            }
        }
    }

    override fun stop() {
        running = false
        moveAnim?.cancel()
        wobbleAnim?.cancel()
        idleAnim?.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    // ─────────────────────────────────────────
    // IDLE ANIMACE – podle ID Makromona
    // ─────────────────────────────────────────

    private fun startIdleAnimation() {
        idleAnim?.cancel()
        idleAnim = when (pokemonId) {
            // Starteři – základní dýchání
            "001", "002", "003" -> startIgnarIdle()   // Oheň
            "004", "005", "006" -> startAqulinIdle()  // Voda
            "007", "008", "009" -> startFloriIdle()   // Příroda

            // Speciální
            "010" -> startUmbexIdle()    // Temná kulička – levituje
            "011" -> startLumexIdle()    // Světlá kulička – pulzuje

            // Spirra rodina – veverky
            "012" -> startSpirraIdle()
            "013" -> startFlamirraIdle() // Ohnivé záblesky
            "014" -> startAquirraIdle()  // Vodní kapky
            "015" -> startVerdirraIdle() // Lístek efekt
            "016" -> startShadirraIdle() // Ghost blikání
            "017" -> startCharmirraIdle()// Fairy jiskry
            "018" -> startGlacirraIdle() // Ledové krystaly
            "019" -> startDrakirraIdle() // Drak – létá

            // Ostatní
            "020", "021" -> startFinletIdle()   // Rybka – vlnění
            "022", "023" -> startMycitIdle()    // Myška
            "024", "025", "026" -> startSouluIdle() // Duše – levituje
            "027", "028", "029" -> startPhantilIdle() // Duch-ryba
            "030" -> startGudwinIdle()   // Medvěd – dýchání + bubliny
            "031" -> startAxluIdle()     // Axolotl – levituje + bubliny

            else  -> startDefaultIdle()
        }
    }

    // ── STARTEŘI ──────────────────────────────

    private fun startIgnarIdle(): Animator {
        // Ohnivá ještěrka – malé otřásání jako blesk
        val shakeX = ObjectAnimator.ofFloat(pokemonView, "translationX",
            0f, 3f * dp, -3f * dp, 2f * dp, -2f * dp, 0f).apply {
            duration = 800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        val breatheY = ObjectAnimator.ofFloat(pokemonView, "scaleY",
            baseScale, baseScale * 1.04f, baseScale).apply {
            duration = 1200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        return AnimatorSet().apply { playTogether(shakeX, breatheY); start() }
    }

    private fun startAqulinIdle(): Animator {
        // Vydří – houpání jako na vodě
        val bobY = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY, targetTranslationY - 6f * dp, targetTranslationY).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        bobY.start()
        return bobY
    }

    private fun startFloriIdle(): Animator {
        // Jelínek – jemné kývání jako vítr
        val sway = ObjectAnimator.ofFloat(pokemonView, "rotation",
            0f, 3f, 0f, -3f, 0f).apply {
            duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        sway.start()
        return sway
    }

    // ── SPECIÁLNÍ ─────────────────────────────

    private fun startUmbexIdle(): Animator {
        // Temná kulička – levituje a mírně pulzuje
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 8f * dp, targetTranslationY + 8f * dp).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val pulse = ObjectAnimator.ofFloat(pokemonView, "alpha", 0.85f, 1f, 0.85f).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        scheduleGhostParticles(Color.parseColor("#9167AB"))
        return AnimatorSet().apply { playTogether(levitate, pulse); start() }
    }

    private fun startLumexIdle(): Animator {
        // Světlá kulička – pulzuje jasně a trochu rotuje
        val pulse = ObjectAnimator.ofFloat(pokemonView, "scaleX",
            -baseScale, -baseScale * 1.1f, -baseScale).apply {
            duration = 1400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        val pulseY = ObjectAnimator.ofFloat(pokemonView, "scaleY",
            baseScale, baseScale * 1.1f, baseScale).apply {
            duration = 1400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 6f * dp, targetTranslationY + 6f * dp).apply {
            duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleGhostParticles(Color.parseColor("#FFD700"))
        return AnimatorSet().apply { playTogether(pulse, pulseY, levitate); start() }
    }

    // ── SPIRRA RODINA ──────────────────────────

    private fun startSpirraIdle(): Animator {
        // Základní veverka – dýchání + ocasem mává
        val breatheY = ObjectAnimator.ofFloat(pokemonView, "scaleY",
            baseScale, baseScale * 1.05f, baseScale).apply {
            duration = 1600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        breatheY.start()
        return breatheY
    }

    private fun startFlamirraIdle(): Animator {
        // Ohnivá veverka – vibrace + tepelné vlny
        val shakeX = ObjectAnimator.ofFloat(pokemonView, "translationX",
            0f, 2f * dp, -2f * dp, 1f * dp, -1f * dp, 0f).apply {
            duration = 600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        scheduleFireParticles()
        return shakeX.also { it.start() }
    }

    private fun startAquirraIdle(): Animator {
        // Vodní veverka – houpání na vlnách
        val bob = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY, targetTranslationY - 5f * dp, targetTranslationY).apply {
            duration = 1600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleWaterDrops()
        return bob.also { it.start() }
    }

    private fun startVerdirraIdle(): Animator {
        // Travní veverka – kývání ve větru
        val sway = ObjectAnimator.ofFloat(pokemonView, "rotation",
            0f, 4f, 0f, -4f, 0f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        sway.start()
        return sway
    }

    private fun startShadirraIdle(): Animator {
        // Temná veverka – ghost blikání
        val blink = ObjectAnimator.ofFloat(pokemonView, "alpha", 1f, 0.6f, 1f, 0.8f, 1f).apply {
            duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        scheduleGhostParticles(Color.parseColor("#7B1FA2"))
        return blink.also { it.start() }
    }

    private fun startCharmirraIdle(): Animator {
        // Fairy veverka – jiskry a lehké levitování
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 5f * dp, targetTranslationY + 5f * dp).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleFairySparkles()
        return levitate.also { it.start() }
    }

    private fun startGlacirraIdle(): Animator {
        // Ledová veverka – třesení z chladu
        val shiver = ObjectAnimator.ofFloat(pokemonView, "translationX",
            0f, 1.5f * dp, -1.5f * dp, 1f * dp, -1f * dp, 0f).apply {
            duration = 400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        shiver.start()
        return shiver
    }

    private fun startDrakirraIdle(): Animator {
        // Dračí veverka – létá + mocné dýchání
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 15f * dp, targetTranslationY + 15f * dp).apply {
            duration = 3000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val breatheX = ObjectAnimator.ofFloat(pokemonView, "scaleX",
            -baseScale, -baseScale * 1.06f, -baseScale).apply {
            duration = 1500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        return AnimatorSet().apply { playTogether(levitate, breatheX); start() }
    }

    // ── OSTATNÍ ───────────────────────────────

    private fun startFinletIdle(): Animator {
        // Rybka – vlnění těla
        val wave = ObjectAnimator.ofFloat(pokemonView, "rotation",
            0f, 8f, 0f, -8f, 0f).apply {
            duration = 1200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        val bob = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY, targetTranslationY - 4f * dp, targetTranslationY).apply {
            duration = 1600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        return AnimatorSet().apply { playTogether(wave, bob); start() }
    }

    private fun startMycitIdle(): Animator {
        // Myška – rychlé čichání (drobné pohyby hlavy)
        val sniff = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY, targetTranslationY - 3f * dp, targetTranslationY).apply {
            duration = 500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        sniff.start()
        return sniff
    }

    private fun startSouluIdle(): Animator {
        // Duše – klidné levitování + průsvitnost
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 10f * dp, targetTranslationY + 10f * dp).apply {
            duration = 2800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val ghostAlpha = ObjectAnimator.ofFloat(pokemonView, "alpha", 0.7f, 1f, 0.7f).apply {
            duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        scheduleGhostParticles(Color.parseColor("#CE93D8"))
        return AnimatorSet().apply { playTogether(levitate, ghostAlpha); start() }
    }

    private fun startPhantilIdle(): Animator {
        // Duch-ryba – vlnění + průsvitnost
        val wave = ObjectAnimator.ofFloat(pokemonView, "rotation",
            0f, 5f, 0f, -5f, 0f).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        val ghostAlpha = ObjectAnimator.ofFloat(pokemonView, "alpha", 0.6f, 0.95f, 0.6f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 8f * dp, targetTranslationY + 8f * dp).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
        }
        return AnimatorSet().apply { playTogether(wave, ghostAlpha, levitate); start() }
    }

    // Gudwin – přepsaný Snorlax idle
    private fun startGudwinIdle(): Animator {
        val breatheX = ObjectAnimator.ofFloat(pokemonView, "scaleX",
            -baseScale, -baseScale * 1.07f, -baseScale).apply {
            duration = 2300; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        val breatheY = ObjectAnimator.ofFloat(pokemonView, "scaleY",
            baseScale, baseScale * 1.07f, baseScale).apply {
            duration = 2300; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        spawnGudwinBubbles()
        return AnimatorSet().apply { playTogether(breatheX, breatheY); start() }
    }

    private fun spawnGudwinBubbles() {
        if (!running) return
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width * 0.62f
        val cy = pokemonView.y + pokemonView.height * 0.22f
        val size = (9 * dp).toInt()

        val bubble = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(65, 180, 140, 100)) // Teplé béžové bubliny
                setStroke((1 * dp).toInt(), Color.argb(125, 160, 100, 60))
            }
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = cx; y = cy; alpha = 0.85f
        }
        parent.addView(bubble)
        bubble.animate()
            .translationYBy(-72f * dp)
            .translationXBy((Random.nextFloat() - 0.5f) * 24f * dp)
            .alpha(0f).scaleX(1.7f).scaleY(1.7f)
            .setDuration(2600)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { parent.removeView(bubble) }
            .start()

        handler.postDelayed({ spawnGudwinBubbles() }, Random.nextLong(1600, 3200))
    }

    // Axlu – přepsaný Mew idle (levitace + duhové bubliny)
    private fun startAxluIdle(): Animator {
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 11f * dp, targetTranslationY + 11f * dp).apply {
            duration = 2500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        levitate.start()
        scheduleAxluBubble()
        return levitate
    }

    private fun scheduleAxluBubble() {
        if (!running) return
        handler.postDelayed({
            if (running) { spawnAxluBubble(); scheduleAxluBubble() }
        }, Random.nextLong(4000, 8000))
    }

    private fun spawnAxluBubble() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height / 2f
        val size = (40 * dp).toInt()

        val bubble = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(0, 255, 182, 193)) // Průhledná růžová
                setStroke((2 * dp).toInt(), Color.argb(180, 255, 105, 180))
            }
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = cx - size / 2f; y = cy - size / 2f; alpha = 0f; scaleX = 0f; scaleY = 0f
        }
        parent.addView(bubble)

        val grow = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(bubble, "scaleX", 0f, 1f),
                ObjectAnimator.ofFloat(bubble, "scaleY", 0f, 1f),
                ObjectAnimator.ofFloat(bubble, "alpha",  0f, 0.9f)
            )
            duration = 600; interpolator = OvershootInterpolator()
        }
        val rainbow = ValueAnimator.ofArgb(
            Color.argb(180, 255, 105, 180),
            Color.argb(180, 255, 182, 193),
            Color.argb(180, 173, 216, 230),
            Color.argb(180, 255, 192, 203)
        ).apply {
            duration = 1600; repeatCount = 2; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { anim ->
                (bubble.background as? GradientDrawable)
                    ?.setStroke((2 * dp).toInt(), anim.animatedValue as Int)
            }
        }
        val burst = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(bubble, "scaleX", 1f, 1.6f, 0f),
                ObjectAnimator.ofFloat(bubble, "scaleY", 1f, 1.6f, 0f),
                ObjectAnimator.ofFloat(bubble, "alpha",  0.9f, 0f)
            )
            duration = 420; interpolator = AccelerateInterpolator()
        }
        AnimatorSet().apply {
            play(grow).before(rainbow); play(burst).after(rainbow)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { parent.removeView(bubble) }
            })
            start()
        }
    }

    private fun startDefaultIdle(): Animator {
        val breathe = ObjectAnimator.ofFloat(pokemonView, "scaleY",
            baseScale, baseScale * 1.05f, baseScale).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        breathe.start()
        return breathe
    }

    // ─────────────────────────────────────────
    // CLICK REAKCE – podle ID Makromona
    // ─────────────────────────────────────────

    override fun onSpriteClicked() {
        if (!running) return
        when (pokemonId) {
            // Starteři
            "001", "002", "003" -> fireBreathReaction()    // Ignar – ohnivý záblesk
            "004", "005", "006" -> waterSplashReaction()   // Aqulin – stříknutí vody
            "007", "008", "009" -> leafSwayReaction()      // Flori – listí polétne

            // Speciální
            "010" -> ghostTapReaction()                    // Umbex – zmizí a ukáže se
            "011" -> lumexFlashReaction()                  // Lumex – oslnivý záblesk

            // Spirra rodina
            "012" -> defaultTapReaction()                  // Spirra – poskočí
            "013" -> fireBreathReaction()                  // Flamirra – oheň
            "014" -> waterSplashReaction()                 // Aquirra – voda
            "015" -> leafSwayReaction()                    // Verdirra – listí
            "016" -> ghostTapReaction()                    // Shadirra – ghost
            "017" -> fairySparkleReaction()                // Charmirra – jiskry
            "018" -> iceShardReaction()                    // Glacirra – ledový třesk
            "019" -> dragonRoarReaction()                  // Drakirra – dračí řev

            // Ostatní
            "020", "021" -> waterSplashReaction()          // Finlet/Serpfin – voda
            "022", "023" -> defaultTapReaction()           // Mycit/Mydrus – poskočí
            "024", "025", "026" -> ghostTapReaction()      // Soulu rodina – ghost
            "027", "028", "029" -> ghostTapReaction()      // Phantil rodina – ghost
            "030" -> snorlaxPoke()                         // Gudwin – zakýve se
            "031" -> axluTapReaction()                     // Axlu – roztomilá reakce

            else -> defaultTapReaction()
        }
    }

    // ── SPECIFICKÉ REAKCE ──────────────────────

    private fun fireBreathReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + (if (facingRight) pokemonView.width.toFloat() else 0f)
        val cy = pokemonView.y + pokemonView.height * 0.4f

        repeat(6) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val ember = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (i % 2 == 0) Color.parseColor("#FF6D00") else Color.parseColor("#FFD600"))
                    }
                    val s = ((8 - i) * dp).toInt().coerceAtLeast(4)
                    layoutParams = ViewGroup.LayoutParams(s, s)
                    x = cx; y = cy
                }
                parent.addView(ember)
                ember.animate()
                    .translationXBy((if (facingRight) 60f else -60f) * dp * (0.5f + Random.nextFloat()))
                    .translationYBy((Random.nextFloat() - 0.5f) * 40f * dp)
                    .alpha(0f).scaleX(2f).scaleY(2f)
                    .setDuration(600)
                    .withEndAction { parent.removeView(ember) }
                    .start()
            }, i * 60L)
        }
        pokemonView.animate().scaleX(baseScale * (if (facingRight) -1.2f else 1.2f))
            .setDuration(200).withEndAction {
                pokemonView.animate()
                    .scaleX(baseScale * (if (facingRight) -1f else 1f))
                    .setDuration(300).withEndAction { startWobble() }.start()
            }.start()
    }

    private fun waterSplashReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height * 0.3f

        repeat(8) {
            val drop = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#29B6F6"))
                }
                layoutParams = ViewGroup.LayoutParams((6 * dp).toInt(), (6 * dp).toInt())
                x = cx; y = cy
            }
            parent.addView(drop)
            drop.animate()
                .translationXBy((Random.nextFloat() - 0.5f) * 120f * dp)
                .translationYBy(-80f * dp - (Random.nextFloat() * 40f * dp))
                .alpha(0f).setDuration(700)
                .withEndAction { parent.removeView(drop) }.start()
        }
        pokemonView.animate().scaleY(baseScale * 1.2f).setDuration(150).withEndAction {
            pokemonView.animate().scaleY(baseScale).setDuration(250)
                .withEndAction { startWobble() }.start()
        }.start()
    }

    private fun leafSwayReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height * 0.2f

        repeat(5) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val leaf = TextView(context).apply {
                    text = "🍃"
                    textSize = 14f
                    x = cx; y = cy
                }
                parent.addView(leaf)
                leaf.animate()
                    .translationXBy((Random.nextFloat() - 0.5f) * 100f * dp)
                    .translationYBy(-60f * dp)
                    .rotation(Random.nextFloat() * 360f)
                    .alpha(0f).setDuration(1000)
                    .withEndAction { parent.removeView(leaf) }.start()
            }, i * 100L)
        }
        ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, -8f, 8f, -4f, 0f).apply {
            duration = 500; start()
        }
    }

    private fun ghostTapReaction() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "alpha", 1f, 0.1f, 0.8f, 0.2f, 1f),
                ObjectAnimator.ofFloat(pokemonView, "scaleX",
                    baseScale * (if (facingRight) -1f else 1f),
                    baseScale * (if (facingRight) -1.2f else 1.2f),
                    baseScale * (if (facingRight) -1f else 1f))
            )
            duration = 500; start()
        }
    }

    private fun lumexFlashReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val flash = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(200, 255, 255, 200))
            }
            layoutParams = ViewGroup.LayoutParams(pokemonView.width * 2, pokemonView.height * 2)
            x = pokemonView.x - pokemonView.width / 2f
            y = pokemonView.y - pokemonView.height / 2f
            alpha = 0f
        }
        parent.addView(flash)
        flash.animate().alpha(0.9f).setDuration(100).withEndAction {
            flash.animate().alpha(0f).setDuration(400).withEndAction {
                parent.removeView(flash)
            }.start()
        }.start()
        pokemonView.animate().scaleX(baseScale * -1.3f).scaleY(baseScale * 1.3f).setDuration(100)
            .withEndAction {
                pokemonView.animate().scaleX(baseScale * -1f).scaleY(baseScale).setDuration(300).start()
            }.start()
    }

    private fun fairySparkleReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height / 2f

        repeat(8) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val sparkle = TextView(context).apply {
                    text = "✨"
                    textSize = 12f
                    x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width
                    y = cy + (Random.nextFloat() - 0.5f) * pokemonView.height
                }
                parent.addView(sparkle)
                sparkle.animate().translationYBy(-50f * dp).alpha(0f).setDuration(800)
                    .withEndAction { parent.removeView(sparkle) }.start()
            }, i * 80L)
        }
        startWobble()
    }

    private fun iceShardReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height / 2f

        repeat(6) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val shard = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.parseColor("#B3E5FC"))
                    }
                    layoutParams = ViewGroup.LayoutParams((4 * dp).toInt(), (12 * dp).toInt())
                    x = cx; y = cy
                    rotation = (i * 60f)
                }
                parent.addView(shard)
                shard.animate()
                    .translationXBy(cos(Math.toRadians(i * 60.0)).toFloat() * 60f * dp)
                    .translationYBy(sin(Math.toRadians(i * 60.0)).toFloat() * 60f * dp)
                    .alpha(0f).setDuration(500)
                    .withEndAction { parent.removeView(shard) }.start()
            }, i * 40L)
        }
        ObjectAnimator.ofFloat(pokemonView, "scaleX",
            baseScale * -1f, baseScale * -1.15f, baseScale * -1f).apply {
            duration = 250; start()
        }
        startWobble()
    }

    private fun dragonRoarReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return

        shakeView(pokemonView)

        val ring = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke((3 * dp).toInt(), Color.parseColor("#FF6D00"))
            }
            val s = pokemonView.width
            layoutParams = ViewGroup.LayoutParams(s, s)
            x = pokemonView.x; y = pokemonView.y + pokemonView.height / 4f
        }
        parent.addView(ring)
        ring.animate().scaleX(3f).scaleY(3f).alpha(0f).setDuration(600)
            .withEndAction { parent.removeView(ring); if (running) startWobble() }.start()
    }

    private fun axluTapReaction() {
        stopWobble(); moveAnim?.cancel()

        // Axlu poskočí radostí a vysype malé bubliny
        pokemonView.animate()
            .translationYBy(-30f * dp)
            .scaleX(baseScale * -1.15f)
            .scaleY(baseScale * 1.15f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                pokemonView.animate()
                    .translationY(targetTranslationY)
                    .scaleX(baseScale * -1f)
                    .scaleY(baseScale)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction { startWobble() }
                    .start()
            }.start()

        // Malé srdíčka / bubliny
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y

        repeat(4) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val heart = TextView(context).apply {
                    text = if (i % 2 == 0) "💗" else "💧"
                    textSize = 14f
                    x = cx + (Random.nextFloat() - 0.5f) * 60f * dp
                    y = cy
                }
                parent.addView(heart)
                heart.animate().translationYBy(-70f * dp).alpha(0f).setDuration(900)
                    .withEndAction { parent.removeView(heart) }.start()
            }, i * 120L)
        }
    }

    private fun defaultTapReaction() {
        pokemonView.animate()
            .scaleX(baseScale * (if (facingRight) -1.35f else 1.35f))
            .scaleY(baseScale * 1.35f).setDuration(80)
            .withEndAction {
                pokemonView.animate()
                    .scaleX(baseScale * (if (facingRight) -1f else 1f))
                    .scaleY(baseScale).setDuration(130).start()
            }.start()
    }

    private fun snorlaxPoke() {
        ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, -6f, 6f, -4f, 4f, 0f).apply {
            duration = 650; interpolator = LinearInterpolator(); start()
        }
    }

    // ─────────────────────────────────────────
    // CROSSING ANIMACE – pohyb přes lištu
    // ─────────────────────────────────────────

    private fun playCrossingAnimation(fromX: Float, toX: Float) {
        when (pokemonId) {
            // Voda – surfing vlna (přepsaný Lapras crossing)
            "004", "005", "006",
            "014", "020", "021",
            "027", "028", "029" -> crossingWaterSurf(fromX, toX)

            // Duch – plovoucí přes lištu
            "010", "011",
            "016", "024", "025", "026" -> crossingGhost(fromX, toX)

            // Létající – oblouk ve vzduchu
            "003", "019" -> crossingFlying(fromX, toX)

            // Oheň – sprint s ohnivou stopou
            "001", "002",
            "013" -> crossingFire(fromX, toX)

            // Gudwin – těžký pád + otřes
            "030" -> crossingHeavy(fromX, toX)

            // Axlu – levituje a vlní se
            "031" -> crossingAxlu(fromX, toX)

            else -> defaultCrossing(fromX, toX)
        }
    }

    // Vodní crossing – vlna pod ním (přepsaný Lapras)
    private fun crossingWaterSurf(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return

        val waveWidth = (pokemonView.width * 2.2f).toInt()
        val waveHeight = (38 * dp).toInt()
        val dur = 3000L

        val waveContainer = android.widget.FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(waveWidth, waveHeight)
            x = pokemonView.x + pokemonView.width / 2f - waveWidth / 2f
            y = pokemonView.y + pokemonView.height - 12 * dp
            alpha = 0f; translationZ = 1f
        }

        val midLayer = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0288D1"))
                setStroke((2 * dp).toInt(), Color.WHITE)
            }
            layoutParams = android.widget.FrameLayout.LayoutParams(
                (waveWidth * 0.95f).toInt(), (waveHeight * 0.8f).toInt()
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.BOTTOM
                bottomMargin = (4 * dp).toInt()
            }
        }
        val foamLayer = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#B3E5FC"))
            }
            layoutParams = android.widget.FrameLayout.LayoutParams(
                (waveWidth * 0.6f).toInt(), (waveHeight * 0.3f).toInt()
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                topMargin = (2 * dp).toInt()
            }
            alpha = 0.8f
        }
        waveContainer.addView(midLayer); waveContainer.addView(foamLayer)
        parent.addView(waveContainer)

        val p1 = ObjectAnimator.ofFloat(midLayer, "scaleX", 1f, 1.05f, 1f).apply { duration = 1200; repeatCount = -1; start() }
        val p2 = ObjectAnimator.ofFloat(foamLayer, "translationX", -10f * dp, 10f * dp).apply {
            duration = 1500; repeatCount = -1; repeatMode = ValueAnimator.REVERSE; start()
        }

        val startAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(waveContainer, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(waveContainer, "scaleY", 0.2f, 1f),
                ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY, targetTranslationY - 40f * dp)
            )
            duration = 600
        }

        startAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!running) return
                facingRight = toX > fromX; applyFacing(facingRight)

                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = dur; interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        val p = anim.animatedValue as Float
                        val currentX = fromX + (toX - fromX) * p
                        pokemonView.x = currentX
                        val arc = sin(p * PI.toFloat()) * 60f * dp
                        pokemonView.translationY = (targetTranslationY - 40f * dp) - arc
                        waveContainer.x = currentX + pokemonView.width / 2f - waveWidth / 2f
                        waveContainer.y = pokemonView.y + pokemonView.height - 15 * dp
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: Animator) {
                            p1.cancel(); p2.cancel()
                            pokemonView.animate().translationY(targetTranslationY).setDuration(400).start()
                            waveContainer.animate().alpha(0f).setDuration(400).withEndAction {
                                parent.removeView(waveContainer)
                                if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(2000, 4000)) }
                            }.start()
                        }
                    })
                    start()
                }
            }
        })
        startAnim.start()
    }

    // Ghost crossing – levituje a bliká
    private fun crossingGhost(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        facingRight = toX > fromX; applyFacing(facingRight)

        val targetX = toX
        val dur = 2500L

        pokemonView.animate().alpha(0.5f).setDuration(300).start()

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur; interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (targetX - fromX) * p
                val wave = sin(p * PI.toFloat() * 3).toFloat()
                pokemonView.translationY = targetTranslationY + wave * 15f * dp
                pokemonView.alpha = 0.5f + wave * 0.3f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.animate().alpha(1f).translationY(targetTranslationY).setDuration(300).start()
                    if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(2000, 4000)) }
                }
            })
            start()
        }
    }

    // Létající crossing – oblouk ve vzduchu
    private fun crossingFlying(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        facingRight = toX > fromX; applyFacing(facingRight)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (toX - fromX) * p
                val arc = sin(p * PI.toFloat()) * 80f * dp
                pokemonView.translationY = targetTranslationY - arc
                pokemonView.rotation = cos(p * PI.toFloat()) * (if (facingRight) -15f else 15f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.animate().rotation(0f).translationY(targetTranslationY).setDuration(400).start()
                    if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(2000, 4000)) }
                }
            })
            start()
        }
    }

    // Ohnivý crossing – sprint + ohnivá stopa
    private fun crossingFire(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        facingRight = toX > fromX; applyFacing(facingRight)

        var lastEmberTime = 0L
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (toX - fromX) * p

                // Ohnivé jiskry za ním
                val now = System.currentTimeMillis()
                if (now - lastEmberTime > 100) {
                    lastEmberTime = now
                    spawnEmberTrail(pokemonView.x, pokemonView.y + pokemonView.height * 0.7f)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(2000, 4000)) }
                }
            })
            start()
        }
    }

    private fun spawnEmberTrail(cx: Float, cy: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val size = (6 * dp).toInt()
        val ember = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (Random.nextBoolean()) Color.parseColor("#FF6D00") else Color.parseColor("#FFD600"))
            }
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = cx; y = cy
        }
        parent.addView(ember)
        ember.animate()
            .translationXBy((Random.nextFloat() - 0.5f) * 20f * dp)
            .translationYBy(-30f * dp)
            .alpha(0f).setDuration(400)
            .withEndAction { parent.removeView(ember) }.start()
    }

    // Těžký crossing – Gudwin
    private fun crossingHeavy(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        facingRight = toX > fromX; applyFacing(facingRight)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800; interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (toX - fromX) * p
                // Waddle efekt – kývání při chůzi
                pokemonView.rotation = sin(p * PI.toFloat() * 6).toFloat() * 5f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.animate().rotation(0f).setDuration(200).start()
                    if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(2000, 4000)) }
                }
            })
            start()
        }
    }

    // Axlu crossing – levitující vlnění
    private fun crossingAxlu(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        facingRight = toX > fromX; applyFacing(facingRight)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2200; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (toX - fromX) * p
                val wave = sin(p * PI.toFloat() * 4).toFloat()
                pokemonView.translationY = targetTranslationY + wave * 12f * dp
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.animate().translationY(targetTranslationY).setDuration(300).start()
                    if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(2000, 4000)) }
                }
            })
            start()
        }
    }

    private fun defaultCrossing(fromX: Float, toX: Float) {
        facingRight = toX > fromX; applyFacing(facingRight)
        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            duration = 2500; interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(2000, 4000)) }
                }
            })
            start()
        }
    }

    // ─────────────────────────────────────────
    // POMOCNÉ METODY – pohyb a wobble
    // ─────────────────────────────────────────

    private fun scheduleStep(delayMs: Long) {
        if (!running) return
        handler.postDelayed({
            if (!running) return@postDelayed
            val parentW = (pokemonView.parent as? ViewGroup)?.width?.toFloat() ?: return@postDelayed
            val startX  = pokemonView.x
            val endX = if (facingRight)
                (parentW * CENTER_END - viewW / 2f).coerceAtMost(parentW - viewW)
            else
                (parentW * CENTER_START - viewW / 2f).coerceAtLeast(0f)

            if (abs(startX - endX) < 10f) {
                facingRight = !facingRight
                scheduleStep(Random.nextLong(500, 1500))
            } else {
                idleAnim?.cancel()
                wobbleAnim?.cancel()
                playCrossingAnimation(startX, endX)
            }
        }, delayMs)
    }

    private fun startWobble() {
        wobbleAnim?.cancel()
        wobbleAnim = ValueAnimator.ofFloat(0f, PI.toFloat() * 2f).apply {
            duration = 800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
            addUpdateListener { anim ->
                val angle = anim.animatedValue as Float
                val squishX = 1f + 0.04f * sin(angle.toDouble()).toFloat()
                val squishY = 1f - 0.04f * sin(angle.toDouble()).toFloat()
                pokemonView.scaleX = baseScale * (if (facingRight) -squishX else squishX)
                pokemonView.scaleY = baseScale * squishY
            }
            start()
        }
    }

    fun stopWobble() {
        wobbleAnim?.cancel()
        pokemonView.scaleX = baseScale * (if (facingRight) -1f else 1f)
        pokemonView.scaleY = baseScale
    }

    private fun baseTranslationY(): Float = when {
        isFlying   -> -(pokemonView.height.toFloat() * 0.85f)
        isSleeping -> -2f * dp
        else       -> -12f * dp
    }

    private fun applyFacing(right: Boolean) {
        pokemonView.scaleX = baseScale * (if (right) -1f else 1f)
    }

    // ─────────────────────────────────────────
    // PARTICLE SYSTÉMY
    // ─────────────────────────────────────────

    private fun scheduleGhostParticles(color: Int) {
        if (!running) return
        val parent = pokemonView.parent as? ViewGroup ?: run {
            handler.postDelayed({ scheduleGhostParticles(color) }, 2000); return
        }
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height / 2f
        val size = (7 * dp).toInt()

        val particle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width * 0.8f
            y = cy + (Random.nextFloat() - 0.5f) * pokemonView.height * 0.8f
            alpha = 0f
        }
        parent.addView(particle)
        particle.animate()
            .alpha(0.7f).translationYBy(-30f * dp)
            .scaleX(0.3f).scaleY(0.3f)
            .setDuration(800)
            .withEndAction {
                particle.animate().alpha(0f).setDuration(400)
                    .withEndAction { parent.removeView(particle) }.start()
            }.start()

        handler.postDelayed({ scheduleGhostParticles(color) }, Random.nextLong(800, 2000))
    }

    private fun scheduleFireParticles() {
        if (!running) return
        val parent = pokemonView.parent as? ViewGroup ?: run {
            handler.postDelayed({ scheduleFireParticles() }, 1000); return
        }
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height * 0.4f
        val size = (5 * dp).toInt()

        val ember = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (Random.nextBoolean()) Color.parseColor("#FF6D00") else Color.parseColor("#FFD600"))
            }
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = cx + (Random.nextFloat() - 0.5f) * 20f * dp; y = cy
        }
        parent.addView(ember)
        ember.animate()
            .translationYBy(-25f * dp)
            .translationXBy((Random.nextFloat() - 0.5f) * 15f * dp)
            .alpha(0f).setDuration(600)
            .withEndAction { parent.removeView(ember) }.start()

        handler.postDelayed({ scheduleFireParticles() }, Random.nextLong(400, 900))
    }

    private fun scheduleWaterDrops() {
        if (!running) return
        val parent = pokemonView.parent as? ViewGroup ?: run {
            handler.postDelayed({ scheduleWaterDrops() }, 1000); return
        }
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height * 0.3f
        val size = (4 * dp).toInt()

        val drop = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#29B6F6"))
            }
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = cx + (Random.nextFloat() - 0.5f) * 15f * dp; y = cy
        }
        parent.addView(drop)
        drop.animate()
            .translationYBy(-20f * dp).alpha(0f).setDuration(700)
            .withEndAction { parent.removeView(drop) }.start()

        handler.postDelayed({ scheduleWaterDrops() }, Random.nextLong(600, 1400))
    }

    private fun scheduleFairySparkles() {
        if (!running) return
        val parent = pokemonView.parent as? ViewGroup ?: run {
            handler.postDelayed({ scheduleFairySparkles() }, 1000); return
        }
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height / 2f

        val sparkle = TextView(context).apply {
            text = "✦"
            textSize = 10f
            setTextColor(Color.parseColor("#F8BBD0"))
            x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width
            y = cy + (Random.nextFloat() - 0.5f) * pokemonView.height
            alpha = 0f
        }
        parent.addView(sparkle)
        sparkle.animate().alpha(0.9f).translationYBy(-20f * dp).setDuration(400).withEndAction {
            sparkle.animate().alpha(0f).setDuration(400).withEndAction {
                parent.removeView(sparkle)
            }.start()
        }.start()

        handler.postDelayed({ scheduleFairySparkles() }, Random.nextLong(500, 1200))
    }

    // ─────────────────────────────────────────
    // UTILITY
    // ─────────────────────────────────────────

    private fun shakeView(target: View) {
        ObjectAnimator.ofFloat(target, "translationX",
            0f, -15f * dp, 15f * dp, -10f * dp, 10f * dp, -5f * dp, 5f * dp, 0f).apply {
            duration = 500; start()
        }
    }

    private fun spawnBolt(parent: ViewGroup, cx: Float, cy: Float, large: Boolean) {
        val size = if (large) (14 * dp).toInt() else (8 * dp).toInt()
        val bolt = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFD600"))
            }
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = cx - size / 2f; y = cy - size / 2f; alpha = 0.9f
        }
        parent.addView(bolt)
        bolt.animate().alpha(0f).scaleX(3f).scaleY(3f).setDuration(350)
            .withEndAction { parent.removeView(bolt) }.start()
    }

    private fun startLaprasIdle() = startAqulinIdle()  // Alias pro Lapras → Aqulin styl
}