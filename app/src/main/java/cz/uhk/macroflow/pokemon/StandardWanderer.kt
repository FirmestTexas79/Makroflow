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
        // Kangaskhan při odchodu plynule propadne dolů a zmizí
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
        view.translationY = targetY - 50f * dp // Spadne mírně shora
        view.scaleX = baseScale
        view.scaleY = baseScale

        view.animate()
            .alpha(1f)
            .translationY(targetY)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator(1.4f))
            .withEndAction {
                if (parent != null) {
                    // Otřes Dashboardu po dopadu!
                    ObjectAnimator.ofFloat(parent, "translationX", 0f, -12f * dp, 12f * dp, -6f * dp, 6f * dp, 0f).apply {
                        duration = 450
                        start()
                    }
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
        val parent  = view.parent as? ViewGroup ?: run { onDone(); return }
        val cx      = view.x + view.width / 2f
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
// CATERPIE + EVOLUTIONS BEHAVIOR (NEW)
// ─────────────────────────────────────────────

class CaterpieWanderer(
    private val context: Context,
    private val spriteView: ImageView,
    private val baseScale: Float
) : PokemonBehavior {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    override fun start() {
        if (running) return
        running = true
        ObjectAnimator.ofFloat(spriteView, View.TRANSLATION_X, -20f, 20f).apply {
            duration = 4000
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    override fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    override fun onSpriteClicked() {
        spriteView.animate().scaleX(baseScale * -1.35f).scaleY(baseScale * 1.35f).setDuration(80)
            .withEndAction { spriteView.animate().scaleX(baseScale * -1f).scaleY(baseScale).setDuration(130).start() }.start()
    }
}

class MetapodWanderer(
    private val context: Context,
    private val spriteView: ImageView,
    private val baseScale: Float
) : PokemonBehavior {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val hardenRunnable = object : Runnable {
        override fun run() {
            if (running) {
                performHardenAnimation()
                handler.postDelayed(this, 7500)
            }
        }
    }

    override fun start() {
        if (running) return
        running = true
        handler.post(hardenRunnable)
    }

    override fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun performHardenAnimation() {
        val parent = spriteView.parent as? ViewGroup ?: return
        val dp = context.resources.displayMetrics.density

        val shine = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                colors = intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.TRANSPARENT)
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            layoutParams = ViewGroup.LayoutParams((16 * dp).toInt(), spriteView.height)
            rotation = 35f
            x = spriteView.x - spriteView.width
            y = spriteView.y
            alpha = 0f
        }
        parent.addView(shine)

        val pulse = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(spriteView, View.SCALE_X, baseScale, baseScale * 1.15f, baseScale),
                ObjectAnimator.ofFloat(spriteView, View.SCALE_Y, baseScale, baseScale * 1.15f, baseScale)
            )
            duration = 1000
            interpolator = AccelerateDecelerateInterpolator()
        }

        val shineAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(shine, View.ALPHA, 0f, 0.8f, 0f),
                ObjectAnimator.ofFloat(shine, View.X, spriteView.x - spriteView.width, spriteView.x + spriteView.width * 1.5f)
            )
            duration = 900
        }

        AnimatorSet().apply {
            playTogether(pulse, shineAnim)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    parent.removeView(shine)
                }
            })
            start()
        }
    }

    override fun onSpriteClicked() {
        performHardenAnimation()
    }
}

class ButterfreeWanderer(
    private val context: Context,
    private val spriteView: ImageView,
    private val baseScale: Float
) : PokemonBehavior {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val sporesRunnable = object : Runnable {
        override fun run() {
            if (running) {
                spawnSporeParticle()
                handler.postDelayed(this, 180 + Random.nextLong(150))
            }
        }
    }

    override fun start() {
        if (running) return
        running = true

        val flyX = ObjectAnimator.ofFloat(spriteView, View.TRANSLATION_X, -30f, 30f).apply {
            duration = 2800
            interpolator = CycleInterpolator(1f)
            repeatCount = ObjectAnimator.INFINITE
        }
        val flyY = ObjectAnimator.ofFloat(spriteView, View.TRANSLATION_Y, -25f, 15f).apply {
            duration = 1400
            interpolator = CycleInterpolator(1f)
            repeatCount = ObjectAnimator.INFINITE
        }

        AnimatorSet().apply { playTogether(flyX, flyY); start() }
        handler.post(sporesRunnable)
    }

    override fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun spawnSporeParticle() {
        val parent = spriteView.parent as? ViewGroup ?: return
        val dp = context.resources.displayMetrics.density
        val size = (6 * dp).toInt()

        val spore = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (Random.nextBoolean()) Color.parseColor("#E090F0") else Color.parseColor("#C878D0"))
            }
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = spriteView.x + spriteView.width / 2f + (Random.nextFloat() - 0.5f) * spriteView.width
            y = spriteView.y + spriteView.height * 0.8f
            alpha = 0.85f
        }
        parent.addView(spore)

        spore.animate()
            .translationYBy(60f * dp)
            .translationXBy((Random.nextFloat() - 0.5f) * 20f * dp)
            .alpha(0f)
            .setDuration(1200)
            .withEndAction { parent.removeView(spore) }
            .start()
    }

    override fun onSpriteClicked() {
        spriteView.animate().scaleX(baseScale * 1.25f).scaleY(baseScale * 1.25f).setDuration(100)
            .withEndAction { spriteView.animate().scaleX(baseScale).scaleY(baseScale).setDuration(150).start() }.start()
    }
}

// ─────────────────────────────────────────────
// PORYGON
// ─────────────────────────────────────────────








// ─────────────────────────────────────────────
// HLAVNÍ WANDERER TŘÍDA
// ─────────────────────────────────────────────

class StandardWanderer(
    private val context: Context,
    private val pokemonView: ImageView,
    val pokemonId: String,
    private val baseScale: Float = 1.0f,
    private val effect: TransitionEffect = SmokeTransitionEffect(false)
) : PokemonBehavior {

    var onKilled: (() -> Unit)? = null

    private val db      = AppDatabase.getDatabase(context)
    private val dp      = context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private var running  = false
    private var targetTranslationY = 0f
    private var wobbleAnim: ValueAnimator? = null
    private var moveAnim:   ObjectAnimator? = null
    private var idleAnim:   Animator? = null
    private var facingRight = true

    private val isFlying   get() = pokemonId in listOf("006", "150", "151")
    private val isSleeping get() = pokemonId == "143"

    private val CENTER_START = 0.38f
    private val CENTER_END   = 0.62f
    private val viewW get()  = pokemonView.width.toFloat().takeIf { it > 10f } ?: (48f * dp)

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

    override fun onSpriteClicked() {
        if (!running) return
        when (pokemonId) {
            "143" -> snorlaxPoke()
            "151" -> mewTapReaction()
            "150" -> mewtwoTapReaction()
            "092", "093", "094" -> ghostTapReaction()
            "025","026" -> pikachuTapReaction() // Použije blesky z Pikachu!
            else  -> defaultTapReaction()
        }
    }

    private fun pikachuTapReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return

        // 1. Zastavíme chůzi a kolébání na místě
        stopWobble()
        moveAnim?.cancel()

        // 2. Vylekání a fialová aura (Mew bubble styl)
        val flashColor = if (pokemonId == "025") Color.argb(180, 255, 230, 0) else Color.argb(180, 255, 100, 255) // Žlutá/Fialová
        val sizeH = (12 * dp).toInt()

        val colorFlash = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(flashColor) }
            layoutParams = ViewGroup.LayoutParams(pokemonView.width, pokemonView.height)
            x = pokemonView.x; y = pokemonView.y; alpha = 0f
        }
        parent.addView(colorFlash)

        // 💥 Animace vibrace a blesků
        AnimatorSet().apply {
            playTogether(
                // Vibrace na místě
                ObjectAnimator.ofFloat(pokemonView, "scaleX", -baseScale, -baseScale * 1.15f, -baseScale),
                ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.15f, baseScale),
                // Blesky pod nohama (GradientDrawable)
                ObjectAnimator.ofFloat(colorFlash, "alpha", 0f, 0.9f, 0f)
            )
            duration = 350
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!running) return

                    // 💥 Vystřelíme 6 blesků do kruhu kolem něj!
                    val cx = pokemonView.x + pokemonView.width / 2f
                    val cy = pokemonView.y + pokemonView.height / 2f

                    repeat(6) { i ->
                        handler.postDelayed({
                            if (!running) return@postDelayed
                            val angle = i * (360f / 6f)
                            val dist = 40f * dp
                            val bx = cx + dist * cos(Math.toRadians(angle.toDouble())).toFloat()
                            val by = cy + dist * sin(Math.toRadians(angle.toDouble())).toFloat()

                            spawnBolt(parent, bx, by, i % 2 == 0) // Každý druhý je velký!
                        }, i * 40L)
                    }

                    // Vrátíme ho do normálu
                    startWobble()
                    parent.removeView(colorFlash)
                }
            })
            start()
        }
    }

    private fun defaultTapReaction() {
        pokemonView.animate().scaleX(baseScale * (if (facingRight) -1.35f else 1.35f))
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

    private fun mewTapReaction() {
        ObjectAnimator.ofFloat(pokemonView, "alpha", 1f, 0.25f, 1f, 0.5f, 1f).apply {
            duration = 520; start()
        }
    }

    private fun mewtwoTapReaction() {
        val parent = pokemonView.parent as? View ?: return
        shakeView(parent)
    }

    private fun ghostTapReaction() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "alpha", 1f, 0.1f, 0.8f, 0.2f, 1f),
                ObjectAnimator.ofFloat(pokemonView, "scaleX", baseScale * (if (facingRight) -1f else 1f),
                    baseScale * (if (facingRight) -1.2f else 1.2f),
                    baseScale * (if (facingRight) -1f else 1f))
            )
            duration = 500; start()
        }
    }

    private fun baseTranslationY(): Float = when {
        isFlying   -> -(pokemonView.height.toFloat() * 0.85f)
        isSleeping -> -2f * dp
        pokemonId == "050" -> -8f * dp
        else       -> -12f * dp
    }

    private fun applyFacing(right: Boolean) {
        pokemonView.scaleX = baseScale * (if (right) -1f else 1f)
    }

    private fun startIdleAnimation() {
        idleAnim?.cancel()
        idleAnim = when (pokemonId) {
            "143" -> startSnorlaxIdle()
            "151" -> startMewIdle()
            "150" -> startMewtwoIdle()
            "137" -> startPorygonIdle()
            "093" -> startHaunterIdle()
            "092" -> startGastlyIdle()
            "006" -> startCharizardIdle()
            else  -> startDefaultIdle()
        }
    }

    private fun startSnorlaxIdle(): Animator {
        val breatheX = ObjectAnimator.ofFloat(pokemonView, "scaleX", -baseScale, -baseScale * 1.07f, -baseScale).apply {
            duration = 2300; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        val breatheY = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.07f, baseScale).apply {
            duration = 2300; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        val set = AnimatorSet().apply { playTogether(breatheX, breatheY); start() }
        spawnSnorlaxBubbles()
        return set
    }

    private fun spawnSnorlaxBubbles() {
        if (!running) return
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx     = pokemonView.x + pokemonView.width * 0.62f
        val cy     = pokemonView.y + pokemonView.height * 0.22f
        val size   = (9 * dp).toInt()

        val bubble = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(65, 180, 220, 255))
                setStroke((1 * dp).toInt(), Color.argb(125, 100, 160, 220))
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

        handler.postDelayed({ spawnSnorlaxBubbles() }, Random.nextLong(1600, 3200))
    }

    private fun startMewIdle(): Animator {
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 11f * dp, targetTranslationY + 11f * dp).apply {
            duration = 2500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator(); start()
        }
        scheduleMewBubble()
        return levitate
    }

    private fun scheduleMewBubble() {
        if (!running) return
        handler.postDelayed({
            if (running) { spawnMewBubble(); scheduleMewBubble() }
        }, Random.nextLong(4000, 8000))
    }

    private fun spawnMewBubble() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx     = pokemonView.x + pokemonView.width / 2f
        val cy     = pokemonView.y + pokemonView.height / 2f
        val size   = (40 * dp).toInt()

        val bubble = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(0, 180, 100, 255))
                setStroke((2 * dp).toInt(), Color.argb(180, 150, 80, 255))
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
            Color.argb(180, 255, 80, 80),  Color.argb(180, 80, 80, 255),
            Color.argb(180, 80, 255, 80),  Color.argb(180, 255, 200, 80),
            Color.argb(180, 200, 80, 255)
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

    private fun startPorygonIdle(): Animator {
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 6f * dp, targetTranslationY + 6f * dp).apply {
            duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                // Digitální "jitter" - občasné náhodné cuknutí měřítka
                if (Random.nextInt(100) > 96) {
                    pokemonView.scaleX = baseScale * (if (facingRight) 1.1f else -1.1f)
                } else {
                    pokemonView.scaleX = if (facingRight) baseScale else -baseScale
                }
            }
            start()
        }
        schedulePorygonGlitch()
        return levitate
    }

    private fun schedulePorygonGlitch() {
        if (!running || (pokemonId != "137" && pokemonId != "233")) return
        handler.postDelayed({
            if (running) {
                // Rychlý digitální záškub (Glitch)
                val oldX = pokemonView.x
                pokemonView.x = oldX + (Random.nextInt(-10, 10) * dp)
                pokemonView.alpha = 0.5f
                handler.postDelayed({
                    pokemonView.x = oldX
                    pokemonView.alpha = 1.0f
                }, 50L)
                schedulePorygonGlitch()
            }
        }, Random.nextLong(3000, 7000))
    }

    private fun spawnPorygonGlitch() {
        val ox = pokemonView.x
        val oy = pokemonView.y

        // Rychlá sekvence digitálního zkratu
        val glitch = AnimatorSet().apply {
            playSequentially(
                ObjectAnimator.ofFloat(pokemonView, "translationX", ox + 10f * dp).setDuration(40),
                ObjectAnimator.ofFloat(pokemonView, "alpha", 0.3f).setDuration(20),
                ObjectAnimator.ofFloat(pokemonView, "translationX", ox - 10f * dp).setDuration(40),
                ObjectAnimator.ofFloat(pokemonView, "alpha", 1.0f).setDuration(20),
                ObjectAnimator.ofFloat(pokemonView, "translationX", ox).setDuration(40)
            )
        }
        glitch.start()
    }



    private fun startMewtwoIdle(): Animator =
        ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 13f * dp, targetTranslationY + 13f * dp).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator(); start()
        }

    private fun startHaunterIdle(): Animator =
        ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 15f * dp, targetTranslationY + 8f * dp).apply {
            duration = 1500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator(); start()
        }

    private fun startGastlyIdle(): Animator {
        val sX = ObjectAnimator.ofFloat(pokemonView, "scaleX", -baseScale, -baseScale * 1.16f, -baseScale).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        val sY = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.16f, baseScale).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        val al = ObjectAnimator.ofFloat(pokemonView, "alpha", 1f, 0.52f, 1f).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
        }
        return AnimatorSet().apply { playTogether(sX, sY, al); start() }
    }

    private fun startCharizardIdle(): Animator {
        var t = 0f
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 80; repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if (!running) return@addUpdateListener
                t += 0.058f
                pokemonView.translationY = targetTranslationY + sin(t) * 17f * dp
                pokemonView.rotation     = sin(t * 0.6f) * 7f
            }
            start()
        }
    }

    private fun startDefaultIdle(): Animator =
        ObjectAnimator.ofFloat(pokemonView, "rotation", -3f, 3f).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator(); start()
        }

    private fun startWobble() {
        wobbleAnim?.cancel()
        wobbleAnim = ValueAnimator.ofFloat(-4f, 4f).apply {
            duration = 500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { pokemonView.rotation = it.animatedValue as Float }
            start()
        }
    }

    private fun stopWobble() {
        wobbleAnim?.cancel()
        pokemonView.animate().rotation(0f).setDuration(150).start()
    }

    private fun scheduleStep(delay: Long) {
        if (isSleeping) return
        handler.postDelayed({ if (running) performStep() }, delay)
    }

    private fun performStep() {
        if (!running || isSleeping) return
        val parent  = pokemonView.parent as? View ?: return
        val parentW = parent.width.toFloat()
        val currentX  = pokemonView.x
        val gapStart  = parentW * CENTER_START
        val gapEnd    = parentW * CENTER_END
        val onLeft    = currentX < gapStart
        val tryCross  = Random.nextFloat() < 0.4f

        val targetX: Float
        val crossing: Boolean

        if (tryCross) {
            crossing = true
            targetX  = if (onLeft)
                gapEnd + Random.nextFloat() * maxOf(1f, parentW - viewW - gapEnd)
            else
                Random.nextFloat() * maxOf(1f, gapStart - viewW)
        } else {
            crossing = false
            targetX  = if (onLeft)
                Random.nextFloat() * maxOf(1f, gapStart - viewW)
            else
                gapEnd + Random.nextFloat() * maxOf(1f, parentW - viewW - gapEnd)
        }

        if (crossing) {
            moveAnim?.cancel()
            if (!isFlying) stopWobble()
            playCrossingAnimation(currentX, targetX)
        } else {
            val movingRight = targetX > currentX
            applyFacing(movingRight)
            if (!isFlying) startWobble()
            val dist = abs(targetX - currentX)
            val dur  = (dist * if (isFlying) 5f else 10f).toLong().coerceIn(600, 3000)
            moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", currentX, targetX).apply {
                duration = dur; interpolator = LinearInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) {
                        if (running) scheduleStep(Random.nextLong(1000, 3500))
                    }
                })
                start()
            }
        }
    }

    private fun playCrossingAnimation(fromX: Float, toX: Float) {
        when (pokemonId) {
            "050" -> crossingDiglett(fromX, toX)
            "094" -> crossingGengar(fromX, toX)
            "025" -> crossingPikachu(fromX, toX)
            "133" -> crossingEevee(fromX, toX)
            "001" -> crossingBulbasaur(fromX, toX)
            "004" -> crossingCharmander(fromX, toX)
            "007" -> crossingSquirtle(fromX, toX)
            "092" -> crossingGastly(fromX, toX)
            "093" -> crossingHaunter(fromX, toX)
            "137" -> crossingPorygon(fromX, toX)
            "115" -> crossingKangaskhan(fromX, toX)
            "006" -> crossingCharizard(fromX, toX)
            "150" -> crossingMewtwo(fromX, toX)
            "151" -> crossingMew(fromX, toX)
            else  -> defaultCrossing(fromX, toX)
        }
    }

    private fun crossingDiglett(fromX: Float, toX: Float) {
        effect.playDisappear(pokemonView, baseScale) {
            if (!running) return@playDisappear
            pokemonView.x = toX; facingRight = toX > fromX; applyFacing(facingRight)
            effect.playAppear(pokemonView, baseScale, targetTranslationY) {
                if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
            }
        }
    }

    private fun crossingPorygon(fromX: Float, toX: Float) {
        idleAnim?.cancel()

        // 1. Digitální rozpad (splácnutí do linky)
        pokemonView.animate()
            .scaleY(0.05f).scaleX(baseScale * 2f).alpha(0f)
            .setDuration(200)
            .withEndAction {
                if (!running) return@withEndAction

                // 2. Přesun na novou pozici
                pokemonView.x = toX
                facingRight = toX > fromX
                applyFacing(facingRight)

                // 3. Digitální složení na cíli
                pokemonView.animate()
                    .alpha(1f).scaleY(baseScale).scaleX(if (facingRight) baseScale else -baseScale)
                    .setDuration(250)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(2000, 5000))
                    }
            }
    }

    class PixelTransitionEffect : TransitionEffect {
        override fun playDisappear(view: View, baseScale: Float, onDone: () -> Unit) {
            view.animate().scaleY(0.02f).scaleX(baseScale * 2f).alpha(0f).setDuration(200).withEndAction(onDone)
        }
        override fun playAppear(view: View, baseScale: Float, targetY: Float, onDone: () -> Unit) {
            view.translationY = targetY
            view.alpha = 0f; view.scaleY = 0.02f; view.scaleX = baseScale * 2f
            view.animate().alpha(1f).scaleY(baseScale).scaleX(baseScale).setDuration(250).withEndAction(onDone)
        }
    }

    private fun crossingGengar(fromX: Float, toX: Float) {
        effect.playDisappear(pokemonView, baseScale) {
            if (!running) return@playDisappear
            pokemonView.x = toX; facingRight = toX > fromX; applyFacing(facingRight)
            effect.playAppear(pokemonView, baseScale, targetTranslationY) {
                if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(1500, 3000)) }
            }
        }
    }

    private fun crossingPikachu(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()
        val mid1 = (fromX + toX) / 2f - 32f * dp
        val mid2 = (fromX + toX) / 2f + 32f * dp
        var step = 0
        fun doZap() {
            if (!running) return
            when (step) {
                0 -> { spawnBolt(parent, pokemonView.x, pokemonView.y, false); pokemonView.animate().x(mid1).setDuration(110).withEndAction { step++; doZap() }.start() }
                1 -> { spawnBolt(parent, pokemonView.x, pokemonView.y, false); pokemonView.animate().x(mid2).setDuration(110).withEndAction { step++; doZap() }.start() }
                2 -> {
                    spawnBolt(parent, pokemonView.x, pokemonView.y, true)
                    pokemonView.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(140).withEndAction {
                        pokemonView.x = toX; facingRight = toX > fromX; applyFacing(facingRight)
                        pokemonView.animate().alpha(1f).scaleX(baseScale).scaleY(baseScale)
                            .setDuration(200).setInterpolator(OvershootInterpolator())
                            .withEndAction { if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) } }.start()
                    }.start()
                }
            }
        }
        doZap()
    }

    private fun spawnBolt(parent: ViewGroup, x: Float, y: Float, big: Boolean) {
        val size = ((if (big) 32 else 14) * dp).toInt()
        val v = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FFE000")) }
            layoutParams = ViewGroup.LayoutParams(size, size)
            this.x = x; this.y = y - size / 2f; alpha = 0.9f
        }
        parent.addView(v)
        v.animate().scaleX(if (big) 3.2f else 2f).scaleY(if (big) 3.2f else 2f).alpha(0f)
            .setDuration(if (big) 380 else 210).withEndAction { parent.removeView(v) }.start()
    }

    private fun crossingEevee(fromX: Float, toX: Float) {
        stopWobble()
        ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, 360f).apply {
            duration = 380; interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (!running) return
                    pokemonView.rotation = 0f
                    AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(pokemonView, "translationY",
                                targetTranslationY, targetTranslationY - 64f * dp, targetTranslationY).apply { interpolator = AccelerateDecelerateInterpolator() },
                            ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply { interpolator = LinearInterpolator() }
                        )
                        duration = 500
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(a: Animator) {
                                if (running) { facingRight = toX > fromX; applyFacing(facingRight); startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
                            }
                        })
                        start()
                    }
                }
            })
            start()
        }
    }


    private fun crossingKangaskhan(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()

        val stepCount = 3
        val stepDist = (toX - fromX) / stepCount
        var currentStep = 0

        // ✅ REKURZIVNÍ FUNKCE PŘESNĚ JAKO U PIKACHU!
        fun doHeavyStep() {
            if (!running) return

            if (currentStep >= stepCount) {
                pokemonView.x = toX
                facingRight = toX > fromX
                applyFacing(facingRight)
                if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
                return
            }

            val startX = fromX + (currentStep * stepDist)
            val nextX = fromX + ((currentStep + 1) * stepDist)

            // 1. Těžký skok (nahoru a dopředu)
            val jumpY = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY, targetTranslationY - 32f * dp, targetTranslationY).apply {
                duration = 600
                interpolator = AccelerateDecelerateInterpolator()
            }
            val jumpX = ObjectAnimator.ofFloat(pokemonView, "x", startX, nextX).apply {
                duration = 600
                interpolator = LinearInterpolator()
            }

            AnimatorSet().apply {
                playTogether(jumpX, jumpY)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!running) return

                        // 💥 DOPAD: Otřes Dashboardu (voláme tvé shakeView na parenta)
                        shakeView(parent)

                        // 🌀 Vykreslení kreativního efektu dopadu na zemi
                        spawnStompWave(parent, nextX + viewW / 2f, pokemonView.y + pokemonView.height)

                        currentStep++
                        handler.postDelayed({ doHeavyStep() }, 250) // Krátká pauza mezi kroky
                    }
                })
                start()
            }
        }

        facingRight = toX > fromX
        applyFacing(facingRight)
        doHeavyStep()
    }

    // ✅ KREATIVNÍ POMOCNÁ METODA PRO TLAKOVOU VLNU A ODLÉTAJÍCÍ PRACH
    private fun spawnStompWave(parent: ViewGroup, x: Float, y: Float) {
        val sizeW = (60 * dp).toInt()
        val sizeH = (20 * dp).toInt()

        // 1. Rázová kruhová vlna pod nohama
        val wave = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(0, 0, 0, 0)) // Průhledný střed
                setStroke((2.5f * dp).toInt(), Color.parseColor("#BC6C25")) // 🎨 Barva z tvého manuálu!
            }
            layoutParams = ViewGroup.LayoutParams(sizeW, sizeH)
            this.x = x - sizeW / 2f
            this.y = y - sizeH / 2f
            alpha = 1f
        }
        parent.addView(wave)

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(wave, "scaleX", 0.4f, 1.8f),
                ObjectAnimator.ofFloat(wave, "scaleY", 0.4f, 1.8f),
                ObjectAnimator.ofFloat(wave, "alpha", 1f, 0f)
            )
            duration = 450
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { parent.removeView(wave) }
            })
            start()
        }

        // 2. Odlétávající kousky hlíny do stran (kombinace tvého spawnBolt a DigTransition)
        repeat(4) { i ->
            val dirtSz = (4 * dp).toInt()
            val dirt = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#DDA15E")) // 🎨 Druhá barva z manuálu!
                }
                layoutParams = ViewGroup.LayoutParams(dirtSz, dirtSz)
                this.x = x; this.y = y; alpha = 0.9f
            }
            parent.addView(dirt)

            val dirX = if (i % 2 == 0) -1.2f else 1.2f
            val dirY = if (i < 2) -1f else -0.5f

            dirt.animate()
                .translationXBy(dirX * Random.nextFloat() * 45f * dp)
                .translationYBy(dirY * Random.nextFloat() * 30f * dp)
                .scaleX(0f).scaleY(0f).alpha(0f)
                .setDuration(450)
                .withEndAction { parent.removeView(dirt) }
                .start()
        }
    }

    private fun crossingBulbasaur(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()
        val leafSz = (22 * dp).toInt()
        val leaf = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#4CAF50")) }
            layoutParams = ViewGroup.LayoutParams(leafSz, leafSz)
            x = pokemonView.x + pokemonView.width / 2f; y = pokemonView.y - leafSz
            scaleX = 0f; scaleY = 0f; alpha = 0.9f
        }
        parent.addView(leaf)
        leaf.animate().scaleX(1f).scaleY(1f).setDuration(280).withEndAction {
            pokemonView.animate().alpha(0f).setDuration(190).withEndAction {
                pokemonView.x = toX; facingRight = toX > fromX; applyFacing(facingRight)
                pokemonView.animate().alpha(1f).setDuration(190).withEndAction {
                    leaf.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(220)
                        .withEndAction { parent.removeView(leaf) }.start()
                    if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
                }.start()
            }.start()
        }.start()
    }

    private fun crossingCharmander(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()
        spawnFire(parent, pokemonView.x, pokemonView.y)
        ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            duration = 290; interpolator = AccelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (!running) return
                    spawnFire(parent, toX, pokemonView.y)
                    facingRight = toX > fromX; applyFacing(facingRight)
                    startWobble(); scheduleStep(Random.nextLong(1500, 3000))
                }
            })
            start()
        }
    }

    private fun spawnFire(parent: ViewGroup, x: Float, y: Float) {
        val size = (30 * dp).toInt()
        val v = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FF6B35")) }
            layoutParams = ViewGroup.LayoutParams(size, size)
            this.x = x; this.y = y; alpha = 0.85f
        }
        parent.addView(v)
        v.animate().scaleX(2.6f).scaleY(2.6f).alpha(0f).setDuration(380)
            .withEndAction { parent.removeView(v) }.start()
    }

    private fun crossingSquirtle(fromX: Float, toX: Float) {
        stopWobble()
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, 0.08f).apply { duration = 230 },
                ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply { duration = 420; startDelay = 200 },
                ObjectAnimator.ofFloat(pokemonView, "scaleY", 0.08f, baseScale).apply { duration = 280; startDelay = 640 }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (running) { facingRight = toX > fromX; applyFacing(facingRight); startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
                }
            })
            start()
        }
    }

    private fun crossingGastly(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        idleAnim?.cancel()
        repeat(10) {
            val sz = ((12 + Random.nextInt(16)) * dp).toInt()
            val fog = View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(120, 150 + Random.nextInt(60), 100, 200)) }
                layoutParams = ViewGroup.LayoutParams(sz, sz)
                x = pokemonView.x + pokemonView.width / 2f - sz / 2f
                y = pokemonView.y + pokemonView.height / 2f - sz / 2f; alpha = 0.7f
            }
            parent.addView(fog)
            fog.animate()
                .translationXBy((Random.nextFloat() - 0.5f) * 145f * dp)
                .translationYBy((Random.nextFloat() - 0.5f) * 82f * dp)
                .alpha(0f).scaleX(3f).scaleY(3f)
                .setDuration(Random.nextLong(500, 900))
                .withEndAction { parent.removeView(fog) }.start()
        }
        pokemonView.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(290).withEndAction {
            pokemonView.x = toX; facingRight = toX > fromX; applyFacing(facingRight)
            pokemonView.animate().alpha(1f).setDuration(340).withEndAction {
                if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(1500, 3000)) }
            }.start()
        }.start()
    }

    private fun crossingHaunter(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        pokemonView.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(330)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (!running) return@withEndAction
                pokemonView.x = toX; facingRight = toX > fromX; applyFacing(facingRight)
                pokemonView.translationY = targetTranslationY - 22f * dp
                pokemonView.animate().alpha(1f).scaleX(baseScale).scaleY(baseScale)
                    .translationY(targetTranslationY)
                    .setDuration(420).setInterpolator(OvershootInterpolator(1.6f))
                    .withEndAction {
                        if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(1500, 3000)) }
                    }.start()
            }.start()
    }

    private fun crossingCharizard(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        spawnFireTrail(parent, fromX)
        ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            duration = 780; interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (running) { facingRight = toX > fromX; applyFacing(facingRight); startIdleAnimation(); scheduleStep(Random.nextLong(1500, 3000)) }
                }
            })
            start()
        }
    }

    private fun spawnFireTrail(parent: ViewGroup, startX: Float) {
        repeat(5) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val sz = ((8 + Random.nextInt(10)) * dp).toInt()
                val v = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (Random.nextBoolean()) Color.parseColor("#FF4500") else Color.parseColor("#FF8C00"))
                    }
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = startX + Random.nextFloat() * 44f * dp
                    y = pokemonView.y + Random.nextFloat() * pokemonView.height; alpha = 0.8f
                }
                parent.addView(v)
                v.animate().translationYBy(-32f * dp).alpha(0f).scaleX(0.5f).scaleY(0.5f)
                    .setDuration(500).withEndAction { parent.removeView(v) }.start()
            }, i * 120L)
        }
    }

    private fun crossingMewtwo(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        repeat(12) {
            val sz = ((6 + Random.nextInt(10)) * dp).toInt()
            val p = View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(200, 120 + Random.nextInt(80), 50, 200 + Random.nextInt(55))) }
                layoutParams = ViewGroup.LayoutParams(sz, sz)
                x = pokemonView.x + pokemonView.width / 2f; y = pokemonView.y + pokemonView.height / 2f; alpha = 0.85f
            }
            parent.addView(p)
            p.animate().translationXBy((Random.nextFloat() - 0.5f) * 210f * dp)
                .translationYBy((Random.nextFloat() - 0.5f) * 125f * dp)
                .alpha(0f).setDuration(Random.nextLong(400, 800))
                .withEndAction { parent.removeView(p) }.start()
        }
        shakeView(parent as View)
        handler.postDelayed({
            if (!running) return@postDelayed
            pokemonView.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(200).withEndAction {
                pokemonView.x = toX; facingRight = toX > fromX; applyFacing(facingRight)
                pokemonView.animate().alpha(1f).scaleX(baseScale).scaleY(baseScale).setDuration(350)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction { if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(1500, 3000)) } }.start()
            }.start()
        }, 300)
    }

    private fun shakeView(v: View) {
        ObjectAnimator.ofFloat(v, "translationX", 0f, -9f * dp, 9f * dp, -6f * dp, 6f * dp, -3f * dp, 3f * dp, 0f).apply {
            duration = 400; interpolator = LinearInterpolator(); start()
        }
    }

    private fun crossingMew(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        val dist = abs(toX - fromX)
        val dur  = (dist * 5f).toLong().coerceIn(800, 2000)
        var elapsed = 0L; val step = 28L
        fun tick() {
            if (!running || elapsed >= dur) {
                pokemonView.x = toX; pokemonView.rotation = 0f
                facingRight = toX > fromX; applyFacing(facingRight)
                startIdleAnimation(); scheduleStep(Random.nextLong(1500, 3000))
                return
            }
            val p = elapsed.toFloat() / dur.toFloat()
            pokemonView.x            = fromX + (toX - fromX) * p
            pokemonView.translationY = targetTranslationY + sin(p * PI.toFloat() * 3f) * 20f * dp
            pokemonView.rotation     = sin(p * PI.toFloat() * 4f) * 16f
            elapsed += step
            handler.postDelayed({ tick() }, step)
        }
        facingRight = toX > fromX; applyFacing(facingRight); tick()
    }


    private fun defaultCrossing(fromX: Float, toX: Float) {
        effect.playDisappear(pokemonView, baseScale) {
            if (!running) return@playDisappear
            pokemonView.x = toX; facingRight = toX > fromX; applyFacing(facingRight)
            effect.playAppear(pokemonView, baseScale, targetTranslationY) {
                if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
            }
        }
    }
}