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
        view.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(350).withEndAction { onDone() }.start()
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
    val pokemonId: String,
    private val baseScale: Float = 1.0f,
    private val effect: TransitionEffect = SmokeTransitionEffect(false)
) : PokemonBehavior {

    var onKilled: (() -> Unit)? = null
    private val db = AppDatabase.getDatabase(context)
    private val dp = context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private var running = false
    private var targetTranslationY = 0f
    private var wobbleAnim: ValueAnimator? = null
    private var moveAnim: ObjectAnimator? = null
    private var idleAnim: Animator? = null     // extra idle animace (Snorlax bubbles, Mew spiral…)
    private var facingRight = true

    // Letecké pokémony — pohybují se výš a jinak
    private val isFlying get() = pokemonId in listOf("006", "150", "151")

    private val CENTER_START = 0.38f
    private val CENTER_END   = 0.62f
    private val viewW get() = pokemonView.width.toFloat().takeIf { it > 10f } ?: (48f * dp)

    // ── Public API ────────────────────────────────────────────────────

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
                if (!isFlying) startWobble()
                scheduleStep(Random.nextLong(1000, 2500))
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

    override fun onSpriteClicked() { killPokemon() }

    // ── Pozice Y ──────────────────────────────────────────────────────

    private fun baseTranslationY(): Float = when {
        isFlying -> -pokemonView.height * 0.6f * dp   // ve vzduchu
        pokemonId == "143" -> -4f * dp                // Snorlax — sedí níž
        pokemonId == "050" -> -8f * dp
        else -> -12f * dp
    }

    // ── Facing ────────────────────────────────────────────────────────

    private fun applyFacing(right: Boolean) {
        pokemonView.scaleX = baseScale * (if (right) -1f else 1f)
    }

    // ── Idle animace ──────────────────────────────────────────────────

    private fun startIdleAnimation() {
        idleAnim?.cancel()
        idleAnim = when (pokemonId) {
            "143" -> startSnorlaxIdle()   // bublinky ze spánku
            "151" -> startMewIdle()        // spirálové kroužení + bublina
            "150" -> startMewtwoIdle()     // plynulá levitace
            "093" -> startHaunterIdle()    // levitace nahoru-dolů
            "092" -> startGastlyIdle()     // pulzování
            "006" -> startCharizardIdle()  // letový pohyb
            else  -> startDefaultIdle()   // jemné kývání
        }
    }

    // Snorlax — sedí, bublinky ze spánku
    private fun startSnorlaxIdle(): Animator {
        val set = AnimatorSet()
        // Jemné dýchání (scale)
        val breathe = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.05f, baseScale).apply {
            duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        set.play(breathe)
        set.start()

        // Bublinky — spouštíme zvlášť opakovaně
        spawnSnorlaxBubbles()
        return set
    }

    private fun spawnSnorlaxBubbles() {
        if (!running) return
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width * 0.6f
        val cy = pokemonView.y + pokemonView.height * 0.3f
        val size = (8 * dp).toInt()

        val bubble = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(80, 180, 220, 255))
                setStroke((1 * dp).toInt(), Color.argb(140, 100, 160, 220))
            }
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = cx; y = cy; alpha = 0.8f
        }
        parent.addView(bubble)
        bubble.animate()
            .translationYBy(-60f * dp)
            .translationXBy((Random.nextFloat() - 0.5f) * 20f * dp)
            .alpha(0f).scaleX(1.5f).scaleY(1.5f)
            .setDuration(2200)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { parent.removeView(bubble) }
            .start()

        handler.postDelayed({ spawnSnorlaxBubbles() }, Random.nextLong(1200, 2500))
    }

    // Mew — spirálové kroužení + duhová bublina
    private fun startMewIdle(): Animator {
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 8f * dp, targetTranslationY + 8f * dp).apply {
            duration = 2500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        levitate.start()
        scheduleMewBubble()
        return levitate
    }

    private fun scheduleMewBubble() {
        if (!running) return
        handler.postDelayed({
            if (!running) return@postDelayed
            spawnMewBubble()
            handler.postDelayed({ scheduleMewBubble() }, Random.nextLong(4000, 8000))
        }, Random.nextLong(3000, 6000))
    }

    private fun spawnMewBubble() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height / 2f
        val size = (36 * dp).toInt()

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

        // Bublina roste
        val growSet = AnimatorSet()
        val grow = ObjectAnimator.ofFloat(bubble, "scaleX", 0f, 1f)
        val growY = ObjectAnimator.ofFloat(bubble, "scaleY", 0f, 1f)
        val fadeIn = ObjectAnimator.ofFloat(bubble, "alpha", 0f, 0.9f)
        growSet.playTogether(grow, growY, fadeIn)
        growSet.duration = 600
        growSet.interpolator = OvershootInterpolator()

        // Duhové blikání barvy (simulace)
        val rainbow = ValueAnimator.ofArgb(
            Color.argb(180, 255, 80, 80),
            Color.argb(180, 80, 80, 255),
            Color.argb(180, 80, 255, 80),
            Color.argb(180, 255, 200, 80),
            Color.argb(180, 200, 80, 255)
        ).apply {
            duration = 1500; repeatCount = 2; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { anim ->
                (bubble.background as? GradientDrawable)
                    ?.setStroke((2 * dp).toInt(), anim.animatedValue as Int)
            }
        }

        // Bublina praskne
        val burstSet = AnimatorSet()
        val shrinkX = ObjectAnimator.ofFloat(bubble, "scaleX", 1f, 1.4f, 0f)
        val shrinkY = ObjectAnimator.ofFloat(bubble, "scaleY", 1f, 1.4f, 0f)
        val fadeOut = ObjectAnimator.ofFloat(bubble, "alpha", 0.9f, 0f)
        burstSet.playTogether(shrinkX, shrinkY, fadeOut)
        burstSet.duration = 400
        burstSet.interpolator = AccelerateInterpolator()

        val fullSeq = AnimatorSet()
        fullSeq.play(growSet).before(rainbow)
        fullSeq.play(burstSet).after(rainbow)
        fullSeq.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) { parent.removeView(bubble) }
        })
        fullSeq.start()
    }

    // Mewtwo — plynulá levitace
    private fun startMewtwoIdle(): Animator {
        return ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 10f * dp, targetTranslationY + 10f * dp).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    // Haunter — levitace nahoru-dolů
    private fun startHaunterIdle(): Animator {
        return ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 12f * dp, targetTranslationY + 6f * dp).apply {
            duration = 1500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    // Gastly — pulzování scale
    private fun startGastlyIdle(): Animator {
        val scaleX = ObjectAnimator.ofFloat(pokemonView, "scaleX", -baseScale, -baseScale * 1.15f, -baseScale)
        val scaleY = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.15f, baseScale)
        val alpha  = ObjectAnimator.ofFloat(pokemonView, "alpha", 1f, 0.6f, 1f)
        return AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1800
            (this as AnimatorSet).let {
                it.childAnimations.forEach { a ->
                    (a as? ObjectAnimator)?.repeatCount = ValueAnimator.INFINITE
                    (a as? ObjectAnimator)?.repeatMode  = ValueAnimator.RESTART
                }
            }
            start()
        }
    }

    // Charizard — letový pohyb (sinusový)
    private fun startCharizardIdle(): Animator {
        var t = 0f
        val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 100; repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if (!running) return@addUpdateListener
                t += 0.06f
                val sinY = sin(t) * 14f * dp
                pokemonView.translationY = targetTranslationY + sinY
                // Jemné naklopení křídel
                pokemonView.rotation = sin(t * 0.7f) * 8f
            }
            start()
        }
        return ticker
    }

    // Default — jemné kývání
    private fun startDefaultIdle(): Animator {
        return ObjectAnimator.ofFloat(pokemonView, "rotation", -3f, 3f).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    // ── Wobble (chůze) ────────────────────────────────────────────────

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

    // ── Krok / Pohyb ──────────────────────────────────────────────────

    private fun scheduleStep(delay: Long) {
        handler.postDelayed({ if (running) performStep() }, delay)
    }

    private fun performStep() {
        if (!running) return
        val parent = pokemonView.parent as? View ?: return
        val parentW = parent.width.toFloat()
        val currentX = pokemonView.x
        val gapStart = parentW * CENTER_START
        val gapEnd   = parentW * CENTER_END
        val currentlyOnLeft = currentX < gapStart
        val tryCrossing = Random.nextFloat() < 0.4f

        val targetX: Float
        val isCrossing: Boolean

        if (tryCrossing) {
            isCrossing = true
            targetX = if (currentlyOnLeft)
                gapEnd + Random.nextFloat() * (parentW - viewW - gapEnd)
            else
                Random.nextFloat() * (gapStart - viewW)
        } else {
            isCrossing = false
            targetX = if (currentlyOnLeft)
                Random.nextFloat() * maxOf(1f, gapStart - viewW)
            else
                gapEnd + Random.nextFloat() * maxOf(1f, parentW - viewW - gapEnd)
        }

        if (isCrossing) {
            moveAnim?.cancel()
            if (!isFlying) stopWobble()
            playCrossingAnimation(currentX, targetX)
        } else {
            val movingRight = targetX > currentX
            applyFacing(movingRight)
            if (!isFlying) startWobble()
            val dist = abs(targetX - currentX)
            val dur  = (dist * if (isFlying) 6f else 10f).toLong().coerceIn(600, 3000)
            moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", currentX, targetX).apply {
                duration = dur
                interpolator = if (isFlying) LinearInterpolator() else LinearInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) {
                        if (running) scheduleStep(Random.nextLong(1000, 3500))
                    }
                })
                start()
            }
        }
    }

    // ── Crossing animace (unikátní pro každého pokémona) ──────────────

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
            "143" -> crossingSnorlax(fromX, toX)
            "006" -> crossingCharizard(fromX, toX)    // Charizard létá, jen plynule přelétne
            "150" -> crossingMewtwo(fromX, toX)
            "151" -> crossingMew(fromX, toX)
            else  -> defaultCrossing(fromX, toX)
        }
    }

    // ⛏️ Diglett — podkope se, vyleze na druhé straně
    private fun crossingDiglett(fromX: Float, toX: Float) {
        effect.playDisappear(pokemonView, baseScale) {
            if (!running) return@playDisappear
            pokemonView.x = toX
            facingRight = toX > fromX
            applyFacing(facingRight)
            effect.playAppear(pokemonView, baseScale, targetTranslationY) {
                if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
            }
        }
    }

    // 👻 Gengar — teleport ve fialovém kouři
    private fun crossingGengar(fromX: Float, toX: Float) {
        effect.playDisappear(pokemonView, baseScale) {
            if (!running) return@playDisappear
            pokemonView.x = toX
            facingRight = toX > fromX
            applyFacing(facingRight)
            effect.playAppear(pokemonView, baseScale, targetTranslationY) {
                if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(1500, 3000)) }
            }
        }
    }

    // ⚡ Pikachu — cik-cak záblesky, teleport s bleskem
    private fun crossingPikachu(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()

        // Cik-cak pohyb
        val midX1 = (fromX + toX) / 2f - 30f * dp
        val midX2 = (fromX + toX) / 2f + 30f * dp
        var step = 0

        fun doZap() {
            if (!running) return
            when (step) {
                0 -> { spawnLightningBolt(parent, pokemonView.x, pokemonView.y, false); pokemonView.animate().x(midX1).setDuration(120).withEndAction { step++; doZap() }.start() }
                1 -> { spawnLightningBolt(parent, pokemonView.x, pokemonView.y, false); pokemonView.animate().x(midX2).setDuration(120).withEndAction { step++; doZap() }.start() }
                2 -> {
                    // Grande flash + teleport
                    spawnLightningBolt(parent, pokemonView.x, pokemonView.y, true)
                    pokemonView.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(150).withEndAction {
                        pokemonView.x = toX
                        facingRight = toX > fromX
                        applyFacing(facingRight)
                        pokemonView.animate().alpha(1f).scaleX(baseScale).scaleY(baseScale)
                            .setDuration(200).setInterpolator(OvershootInterpolator())
                            .withEndAction { if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) } }
                            .start()
                    }.start()
                }
            }
        }
        doZap()
    }

    private fun spawnLightningBolt(parent: ViewGroup, x: Float, y: Float, big: Boolean) {
        val size = ((if (big) 30 else 14) * dp).toInt()
        val bolt = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FFE000")) }
            layoutParams = ViewGroup.LayoutParams(size, size)
            this.x = x; this.y = y - size / 2f; alpha = 0.9f
        }
        parent.addView(bolt)
        bolt.animate().scaleX(if (big) 3f else 1.8f).scaleY(if (big) 3f else 1.8f).alpha(0f)
            .setDuration(if (big) 350 else 200)
            .withEndAction { parent.removeView(bolt) }.start()
    }

    // 🌀 Eevee — otočí se a přeskočí FAB
    private fun crossingEevee(fromX: Float, toX: Float) {
        stopWobble()
        val jumpHeight = -60f * dp
        // Spin
        ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, 360f).apply {
            duration = 400; interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (!running) return
                    pokemonView.rotation = 0f
                    // Skok přes FAB
                    val jumpAnim = ObjectAnimator.ofFloat(pokemonView, "translationY",
                        targetTranslationY, targetTranslationY + jumpHeight, targetTranslationY).apply {
                        duration = 500; interpolator = AccelerateDecelerateInterpolator()
                    }
                    val moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
                        duration = 500; interpolator = LinearInterpolator()
                    }
                    AnimatorSet().apply {
                        playTogether(jumpAnim, moveAnim)
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(a: Animator) {
                                if (running) {
                                    facingRight = toX > fromX; applyFacing(facingRight)
                                    startWobble(); scheduleStep(Random.nextLong(1500, 3000))
                                }
                            }
                        })
                        start()
                    }
                }
            })
            start()
        }
    }

    // 🌿 Bulbasaur — rostlinka vyroste a zmizí
    private fun crossingBulbasaur(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()

        // Vyroste lupínek
        val leafSize = (20 * dp).toInt()
        val leaf = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#4CAF50")) }
            layoutParams = ViewGroup.LayoutParams(leafSize, leafSize)
            x = pokemonView.x + pokemonView.width / 2f
            y = pokemonView.y - leafSize
            scaleX = 0f; scaleY = 0f; alpha = 0.9f
        }
        parent.addView(leaf)
        leaf.animate().scaleX(1f).scaleY(1f).setDuration(300).withEndAction {
            // Teleport
            pokemonView.animate().alpha(0f).setDuration(200).withEndAction {
                pokemonView.x = toX
                facingRight = toX > fromX; applyFacing(facingRight)
                pokemonView.animate().alpha(1f).setDuration(200).withEndAction {
                    // Lupínek zmizí
                    leaf.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(250)
                        .withEndAction { parent.removeView(leaf) }.start()
                    if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
                }.start()
            }.start()
        }.start()
    }

    // 🔥 Charmander — blikne oranžově a přeběhne
    private fun crossingCharmander(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()
        spawnFireFlash(parent, pokemonView.x, pokemonView.y)
        val dash = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            duration = 300; interpolator = AccelerateInterpolator()
        }
        dash.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                if (!running) return
                spawnFireFlash(parent, toX, pokemonView.y)
                facingRight = toX > fromX; applyFacing(facingRight)
                startWobble(); scheduleStep(Random.nextLong(1500, 3000))
            }
        })
        dash.start()
    }

    private fun spawnFireFlash(parent: ViewGroup, x: Float, y: Float) {
        val size = (28 * dp).toInt()
        val fire = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FF6B35")) }
            layoutParams = ViewGroup.LayoutParams(size, size)
            this.x = x; this.y = y; alpha = 0.85f
        }
        parent.addView(fire)
        fire.animate().scaleX(2.5f).scaleY(2.5f).alpha(0f).setDuration(400)
            .withEndAction { parent.removeView(fire) }.start()
    }

    // 🐢 Squirtle — schová se do krunýře
    private fun crossingSquirtle(fromX: Float, toX: Float) {
        stopWobble()
        // Zaboří se do krunýře
        AnimatorSet().apply {
            val hideY = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, 0.1f).apply { duration = 250 }
            val slideX = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply { duration = 400; startDelay = 200 }
            val showY  = ObjectAnimator.ofFloat(pokemonView, "scaleY", 0.1f, baseScale).apply { duration = 300; startDelay = 650 }
            playTogether(hideY, slideX, showY)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (running) {
                        facingRight = toX > fromX; applyFacing(facingRight)
                        startWobble(); scheduleStep(Random.nextLong(1500, 3000))
                    }
                }
            })
            start()
        }
    }

    // 💨 Gastly — exploduje do mlhy
    private fun crossingGastly(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        idleAnim?.cancel()
        // Velký burst
        repeat(10) {
            val size = ((12 + Random.nextInt(16)) * dp).toInt()
            val fog = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(120, 150 + Random.nextInt(60), 100, 200))
                }
                layoutParams = ViewGroup.LayoutParams(size, size)
                x = pokemonView.x + pokemonView.width / 2f - size / 2f
                y = pokemonView.y + pokemonView.height / 2f - size / 2f
                alpha = 0.7f
            }
            parent.addView(fog)
            fog.animate()
                .translationXBy((Random.nextFloat() - 0.5f) * 140f * dp)
                .translationYBy((Random.nextFloat() - 0.5f) * 80f * dp)
                .alpha(0f).scaleX(3f).scaleY(3f)
                .setDuration(Random.nextLong(500, 900))
                .withEndAction { parent.removeView(fog) }.start()
        }
        pokemonView.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(300).withEndAction {
            pokemonView.x = toX
            facingRight = toX > fromX; applyFacing(facingRight)
            pokemonView.scaleX = -baseScale; pokemonView.scaleY = baseScale
            pokemonView.animate().alpha(1f).setDuration(350).withEndAction {
                if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(1500, 3000)) }
            }.start()
        }.start()
    }

    // 👻 Haunter — zmizí a zjeví se za FAB
    private fun crossingHaunter(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        pokemonView.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(350)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (!running) return@withEndAction
                pokemonView.x = toX
                facingRight = toX > fromX; applyFacing(facingRight)
                pokemonView.translationY = targetTranslationY - 20f * dp
                pokemonView.animate().alpha(1f).scaleX(baseScale).scaleY(baseScale)
                    .translationY(targetTranslationY)
                    .setDuration(400).setInterpolator(OvershootInterpolator(1.5f))
                    .withEndAction {
                        if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(1500, 3000)) }
                    }.start()
            }.start()
    }

    // 😴 Snorlax — se probudí, mrkne a zase usne
    private fun crossingSnorlax(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        // Snorlax se "probudí" — trochu se zvětší
        val wakeUp = AnimatorSet().apply {
            val scaleUp = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.1f)
            val scaleDown = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale * 1.1f, baseScale)
            playSequentially(scaleUp, scaleDown)
            duration = 500
        }
        // Přesun — Snorlax se pomalu převalí
        val roll = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            duration = 1200; interpolator = AccelerateDecelerateInterpolator()
        }
        AnimatorSet().apply {
            playTogether(wakeUp, roll)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (running) {
                        facingRight = toX > fromX; applyFacing(facingRight)
                        startIdleAnimation()   // zase usne
                        scheduleStep(Random.nextLong(3000, 6000))  // Snorlax odpočívá déle
                    }
                }
            })
            start()
        }
    }

    // 🔥 Charizard — plynulý přelet (létá, nekrade zem)
    private fun crossingCharizard(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        spawnFireTrail(parent, fromX, pokemonView.translationY)

        val flyOver = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            duration = 800; interpolator = AccelerateDecelerateInterpolator()
        }
        flyOver.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                if (running) {
                    facingRight = toX > fromX; applyFacing(facingRight)
                    startIdleAnimation(); scheduleStep(Random.nextLong(1500, 3000))
                }
            }
        })
        flyOver.start()
    }

    private fun spawnFireTrail(parent: ViewGroup, x: Float, y: Float) {
        repeat(5) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val size = ((8 + Random.nextInt(10)) * dp).toInt()
                val fire = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (Random.nextBoolean()) Color.parseColor("#FF4500") else Color.parseColor("#FF8C00"))
                    }
                    layoutParams = ViewGroup.LayoutParams(size, size)
                    this.x = x + Random.nextFloat() * 40f * dp
                    this.y = pokemonView.y + Random.nextFloat() * pokemonView.height
                    alpha = 0.8f
                }
                parent.addView(fire)
                fire.animate().translationYBy(-30f * dp).alpha(0f).scaleX(0.5f).scaleY(0.5f)
                    .setDuration(500).withEndAction { parent.removeView(fire) }.start()
            }, i * 120L)
        }
    }

    // 🔮 Mewtwo — shake obrazovky + teleport
    private fun crossingMewtwo(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }

        // Telekinetické částice
        repeat(12) {
            val size = ((6 + Random.nextInt(10)) * dp).toInt()
            val particle = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(200, 120 + Random.nextInt(80), 50, 200 + Random.nextInt(55)))
                }
                layoutParams = ViewGroup.LayoutParams(size, size)
                x = pokemonView.x + pokemonView.width / 2f
                y = pokemonView.y + pokemonView.height / 2f
                alpha = 0.85f
            }
            parent.addView(particle)
            particle.animate()
                .translationXBy((Random.nextFloat() - 0.5f) * 200f * dp)
                .translationYBy((Random.nextFloat() - 0.5f) * 120f * dp)
                .alpha(0f).setDuration(Random.nextLong(400, 800))
                .withEndAction { parent.removeView(particle) }.start()
        }

        // Shake parent
        shakeView(parent as View)

        handler.postDelayed({
            if (!running) return@postDelayed
            pokemonView.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(200).withEndAction {
                pokemonView.x = toX
                facingRight = toX > fromX; applyFacing(facingRight)
                pokemonView.animate().alpha(1f).scaleX(baseScale).scaleY(baseScale).setDuration(350)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        if (running) { startIdleAnimation(); scheduleStep(Random.nextLong(1500, 3000)) }
                    }.start()
            }.start()
        }, 300)
    }

    private fun shakeView(v: View) {
        ObjectAnimator.ofFloat(v, "translationX", 0f, -8f * dp, 8f * dp, -6f * dp, 6f * dp, -3f * dp, 3f * dp, 0f).apply {
            duration = 400; interpolator = LinearInterpolator(); start()
        }
    }

    // ✨ Mew — spirálový pohyb letadlem
    private fun crossingMew(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        val dist = abs(toX - fromX)
        val dur  = (dist * 5f).toLong().coerceIn(800, 2000)

        // Spirálový pohyb Y
        var elapsed = 0L
        val step = 30L
        fun tick() {
            if (!running || elapsed >= dur) {
                pokemonView.x = toX
                facingRight = toX > fromX; applyFacing(facingRight)
                startIdleAnimation(); scheduleStep(Random.nextLong(1500, 3000))
                return
            }
            val progress = elapsed.toFloat() / dur.toFloat()
            val spiralX = fromX + (toX - fromX) * progress
            val spiralY = targetTranslationY + sin(progress * PI.toFloat() * 3f) * 18f * dp
            pokemonView.x = spiralX
            pokemonView.translationY = spiralY
            pokemonView.rotation = sin(progress * PI.toFloat() * 4f) * 15f
            elapsed += step
            handler.postDelayed({ tick() }, step)
        }
        facingRight = toX > fromX; applyFacing(facingRight)
        tick()
    }

    // ── Default crossing ──────────────────────────────────────────────

    private fun defaultCrossing(fromX: Float, toX: Float) {
        effect.playDisappear(pokemonView, baseScale) {
            if (!running) return@playDisappear
            pokemonView.x = toX
            facingRight = toX > fromX; applyFacing(facingRight)
            effect.playAppear(pokemonView, baseScale, targetTranslationY) {
                if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
            }
        }
    }

    // ── Kill ──────────────────────────────────────────────────────────

    private fun killPokemon() {
        if (!running) return
        running = false
        moveAnim?.cancel(); wobbleAnim?.cancel(); idleAnim?.cancel()
        handler.removeCallbacksAndMessages(null)

        Thread {
            val all = db.capturedPokemonDao().getAllCaught()
            val toDelete = all.find { it.pokemonId == pokemonId }
            if (toDelete != null) {
                db.capturedPokemonDao().deletePokemon(toDelete)
            }
        }.start()

        pokemonView.animate()
            .rotation(720f).scaleX(0f).scaleY(0f).alpha(0f).setDuration(700)
            .withEndAction {
                pokemonView.visibility = android.view.View.GONE
                onKilled?.invoke()
            }.start()
    }
}