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
            .translationYBy(100f * dp).alpha(0f).setDuration(400)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { onDone() }.start()
    }

    override fun playAppear(view: View, baseScale: Float, targetY: Float, onDone: () -> Unit) {
        val parent = view.parent as? ViewGroup
        view.visibility = View.VISIBLE
        view.alpha = 0f; view.translationY = targetY - 50f * dp
        view.scaleX = baseScale; view.scaleY = baseScale
        view.animate().alpha(1f).translationY(targetY).setDuration(500)
            .setInterpolator(OvershootInterpolator(1.4f))
            .withEndAction {
                parent?.let {
                    ObjectAnimator.ofFloat(it, "translationX", 0f, -12f * dp, 12f * dp, -6f * dp, 6f * dp, 0f)
                        .apply { duration = 450; start() }
                }
                onDone()
            }.start()
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
        val cx = view.x + view.width / 2f; val cy = view.y + view.height / 2f
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
        val cx = view.x + view.width / 2f; val groundY = view.y + view.height
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

    companion object {
        class PixelTransitionEffect : TransitionEffect {
            private val dp = android.content.res.Resources.getSystem().displayMetrics.density
            override fun playDisappear(view: View, baseScale: Float, onDone: () -> Unit) {
                ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = 500
                    addUpdateListener {
                        val v = it.animatedValue as Float
                        view.scaleX = baseScale * (if (view.scaleX < 0) -v else v)
                        view.scaleY = baseScale * v; view.alpha = v
                    }
                    addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { onDone() } })
                    start()
                }
            }
            override fun playAppear(view: View, baseScale: Float, targetY: Float, onDone: () -> Unit) {
                view.visibility = View.VISIBLE
                view.alpha = 0f; view.scaleX = 0f; view.scaleY = 0f; view.translationY = targetY
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 600
                    addUpdateListener { val v = it.animatedValue as Float; view.scaleX = baseScale * -v; view.scaleY = baseScale * v; view.alpha = v }
                    addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { onDone() } })
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
    private var isCrossing  = false  // TRUE když probíhá crossing animace

    // Létající Makromoni
    private val isFlying get()   = pokemonId in listOf("003", "019")
    // Spící Makromoni (Gudwin)
    private val isSleeping get() = pokemonId == "030"

    // ── WANDERING KONSTANTY ──────────────────
    // Střed lišty je oblast kam přijde home tlačítko – crossing se spouští zde
    private val CENTER_START = 0.38f  // 38 % šířky – levý okraj středu
    private val CENTER_END   = 0.62f  // 62 % šířky – pravý okraj středu
    private val viewW get()  = pokemonView.width.toFloat().takeIf { it > 10f } ?: (48f * dp)

    // Jak rychle chodí daný Makromon (ms na crossing)
    private val walkDuration: Long get() = when (pokemonId) {
        "030"        -> 3200L  // Gudwin – pomalý a těžký
        "029"        -> 2800L  // Phantiax – velký
        "021"        -> 2600L  // Serpfin – velký
        "019"        -> 1600L  // Drakirra – rychlý letec
        "003"        -> 1800L  // Ignaroth – letec
        "001","004","007" -> 2800L  // Starteři první forma – pomalí
        "010","011"  -> 2200L  // Kuličky – levitují pomalu
        "012"        -> 2400L  // Spirra – normální
        "031"        -> 2000L  // Axlu – svižný
        else         -> 2500L
    }

    // Jak dlouho čeká na místě před dalším krokem
    private val idleWaitRange: Pair<Long, Long> get() = when (pokemonId) {
        "030"        -> 4000L to 8000L   // Gudwin hodně sedí
        "022","023"  -> 800L  to 2000L   // Mycit/Mydrus – nervózní, neposedí
        "012"        -> 1500L to 3500L   // Spirra – živá
        "010","011"  -> 2000L to 5000L   // Kuličky – klidné
        "031"        -> 1000L to 3000L   // Axlu – zvědavý
        else         -> 1500L to 4000L
    }

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
                if (!isSleeping) scheduleStep(Random.nextLong(idleWaitRange.first, idleWaitRange.second))
            }
        }
    }

    override fun stop() {
        running = false
        isCrossing = false
        moveAnim?.cancel()
        wobbleAnim?.cancel()
        idleAnim?.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    // ─────────────────────────────────────────
    // WANDERING – pohyb po liště
    // ─────────────────────────────────────────

    /**
     * Naplánuje další krok Makromona.
     * Pokud se Makromon nachází v oblasti středu (CENTER_START–CENTER_END), spustí crossing animaci.
     * Jinak se přesune na náhodnou pozici na své straně.
     */
    private fun scheduleStep(delayMs: Long) {
        if (!running) return
        handler.postDelayed({
            if (!running || isCrossing) return@postDelayed
            val parentW = (pokemonView.parent as? ViewGroup)?.width?.toFloat() ?: return@postDelayed
            val currentX = pokemonView.x
            val currentXFraction = currentX / parentW

            // Je Makromon v oblasti středu (home tlačítko)?
            val inCenter = currentXFraction in CENTER_START..CENTER_END

            if (inCenter) {
                // CROSSING – přejde na druhou stranu přes střed
                val targetX = if (facingRight)
                    (parentW * (CENTER_END + 0.15f)).coerceAtMost(parentW - viewW)
                else
                    (parentW * (CENTER_START - 0.15f) - viewW).coerceAtLeast(0f)

                idleAnim?.cancel()
                stopWobble()
                isCrossing = true
                playCrossingAnimation(currentX, targetX)
            } else {
                // NORMÁLNÍ CHŮZE – přesun na pozici na aktuální straně
                val targetX = pickWalkTarget(parentW, currentXFraction)
                if (abs(currentX - targetX) < 20f * dp) {
                    // Jsme už na místě, otoč se a počkej
                    facingRight = !facingRight
                    applyFacing(facingRight)
                    scheduleStep(Random.nextLong(idleWaitRange.first, idleWaitRange.second))
                } else {
                    idleAnim?.cancel()
                    stopWobble()
                    facingRight = targetX > currentX
                    applyFacing(facingRight)
                    performWalk(currentX, targetX)
                }
            }
        }, delayMs)
    }

    /**
     * Vybere cílovou X pozici pro normální chůzi.
     * Makromon chodí v rámci své "domácí" strany (levá / pravá).
     * Charakter ovlivňuje jak daleko chodí.
     */
    private fun pickWalkTarget(parentW: Float, currentFraction: Float): Float {
        val onLeftSide = currentFraction < 0.5f
        return when (pokemonId) {
            "030" -> {
                // Gudwin chodí hodně pomalu a na krátké vzdálenosti
                val base = if (onLeftSide) parentW * 0.05f else parentW * 0.75f
                base + (Random.nextFloat() - 0.5f) * parentW * 0.10f
            }
            "012" -> {
                // Spirra je živá – chodí dál a rychleji
                val base = if (onLeftSide) parentW * 0.08f else parentW * 0.72f
                base + (Random.nextFloat() - 0.5f) * parentW * 0.18f
            }
            "031" -> {
                // Axlu je zvědavý – průzkumník
                val base = if (onLeftSide) parentW * 0.06f else parentW * 0.70f
                base + (Random.nextFloat() - 0.5f) * parentW * 0.20f
            }
            "022", "023" -> {
                // Mycit/Mydrus – nervózní, krátké kroky ale rychlé
                val base = if (onLeftSide) parentW * 0.10f else parentW * 0.68f
                base + (Random.nextFloat() - 0.5f) * parentW * 0.12f
            }
            "010", "011" -> {
                // Kuličky – levitují klidně, malé pohyby
                val base = if (onLeftSide) parentW * 0.08f else parentW * 0.72f
                base + (Random.nextFloat() - 0.5f) * parentW * 0.12f
            }
            "019" -> {
                // Drakirra – letí rychle a daleko
                val base = if (onLeftSide) parentW * 0.05f else parentW * 0.68f
                base + (Random.nextFloat() - 0.5f) * parentW * 0.25f
            }
            else -> {
                val base = if (onLeftSide) parentW * 0.06f else parentW * 0.70f
                base + (Random.nextFloat() - 0.5f) * parentW * 0.15f
            }
        }.coerceIn(0f, parentW - viewW)
    }

    /**
     * Normální chůze – Makromon jde z A do B na své straně.
     * Každý Makromon má svůj styl chůze.
     */
    private fun performWalk(fromX: Float, toX: Float) {
        when (pokemonId) {
            // Starteři – základní chůze
            "001", "002", "003",
            "004", "005", "006",
            "007", "008", "009" -> walkBouncy(fromX, toX, bounceHeight = 6f)

            // Speciální kuličky – levitují plynule
            "010", "011" -> walkFloat(fromX, toX)

            // Spirra rodina
            "012" -> walkBouncy(fromX, toX, bounceHeight = 8f)
            "013" -> walkBouncy(fromX, toX, bounceHeight = 7f) // Flamirra
            "014" -> walkFloat(fromX, toX)                     // Aquirra – plave
            "015" -> walkBouncy(fromX, toX, bounceHeight = 9f) // Verdirra – skáče
            "016" -> walkGhost(fromX, toX)                     // Shadirra – ghost chůze
            "017" -> walkFloat(fromX, toX)                     // Charmirra – levituje
            "018" -> walkBouncy(fromX, toX, bounceHeight = 5f) // Glacirra
            "019" -> walkFlying(fromX, toX)                    // Drakirra – letí

            // Finlet/Serpfin – vlnění
            "020", "021" -> walkWave(fromX, toX)

            // Mycit/Mydrus – nervózní kroky
            "022", "023" -> walkNervous(fromX, toX)

            // Soulu rodina – levitují
            "024", "025", "026" -> walkGhost(fromX, toX)

            // Phantil rodina – plovoucí duchové
            "027", "028", "029" -> walkGhost(fromX, toX)

            // Gudwin – těžká pomalá chůze
            "030" -> walkHeavy(fromX, toX)

            // Axlu – svižné levitování
            "031" -> walkFloat(fromX, toX)

            else -> walkDefault(fromX, toX)
        }
    }

    // ── STYLY CHŮZE ───────────────────────────

    /** Poskakující chůze – pro pozemní Makromony */
    private fun walkBouncy(fromX: Float, toX: Float, bounceHeight: Float = 8f) {
        val duration = walkDuration
        startWobble()
        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            this.duration = duration; interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val p = (anim.animatedFraction)
                // Sinusový bounce při chůzi
                val bounce = abs(sin(p * PI.toFloat() * 4)) * bounceHeight * dp
                pokemonView.translationY = targetTranslationY - bounce
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.translationY = targetTranslationY
                    if (running && !isCrossing) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(idleWaitRange.first, idleWaitRange.second))
                    }
                }
            })
            start()
        }
    }

    /** Levitující chůze – pro létající / fairy / duchové */
    private fun walkFloat(fromX: Float, toX: Float) {
        val duration = walkDuration
        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            this.duration = duration; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedFraction
                val wave = sin(p * PI.toFloat() * 2).toFloat() * 8f * dp
                pokemonView.translationY = targetTranslationY + wave
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.translationY = targetTranslationY
                    if (running && !isCrossing) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(idleWaitRange.first, idleWaitRange.second))
                    }
                }
            })
            start()
        }
    }

    /** Ghost chůze – průsvitné levitování s blikáním */
    private fun walkGhost(fromX: Float, toX: Float) {
        val duration = walkDuration
        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            this.duration = duration; interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedFraction
                val wave = sin(p * PI.toFloat() * 3).toFloat()
                pokemonView.translationY = targetTranslationY + wave * 12f * dp
                pokemonView.alpha = 0.6f + abs(wave) * 0.4f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.alpha = 1f
                    pokemonView.translationY = targetTranslationY
                    if (running && !isCrossing) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(idleWaitRange.first, idleWaitRange.second))
                    }
                }
            })
            start()
        }
    }

    /** Létající chůze – oblouk ve vzduchu */
    private fun walkFlying(fromX: Float, toX: Float) {
        val duration = walkDuration
        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            this.duration = duration; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedFraction
                val arc = sin(p * PI.toFloat()) * 30f * dp
                pokemonView.translationY = targetTranslationY - arc
                pokemonView.rotation = cos(p * PI.toFloat()) * (if (facingRight) -10f else 10f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.rotation = 0f
                    pokemonView.translationY = targetTranslationY
                    if (running && !isCrossing) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(idleWaitRange.first, idleWaitRange.second))
                    }
                }
            })
            start()
        }
    }

    /** Vlnění těla – pro rybky */
    private fun walkWave(fromX: Float, toX: Float) {
        val duration = walkDuration
        startWobble()
        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            this.duration = duration; interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedFraction
                pokemonView.rotation = sin(p * PI.toFloat() * 6).toFloat() * 10f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.rotation = 0f
                    if (running && !isCrossing) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(idleWaitRange.first, idleWaitRange.second))
                    }
                }
            })
            start()
        }
    }

    /** Nervózní kroky – krátké zastavení pak zase pohyb */
    private fun walkNervous(fromX: Float, toX: Float) {
        val totalDist = abs(toX - fromX)
        val steps = 3
        val stepDist = totalDist / steps
        var stepsDone = 0

        fun doStep() {
            if (!running || isCrossing) return
            val stepFrom = pokemonView.x
            val stepTo = stepFrom + (if (facingRight) stepDist else -stepDist)
            startWobble()
            moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", stepFrom, stepTo).apply {
                duration = 400; interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val p = anim.animatedFraction
                    val bounce = abs(sin(p * PI.toFloat() * 2)) * 5f * dp
                    pokemonView.translationY = targetTranslationY - bounce
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) {
                        stepsDone++
                        pokemonView.translationY = targetTranslationY
                        if (stepsDone < steps && running && !isCrossing) {
                            // Krátká pauza mezi kroky (čuchá)
                            handler.postDelayed({ doStep() }, Random.nextLong(200, 600))
                        } else {
                            if (running && !isCrossing) {
                                startIdleAnimation()
                                scheduleStep(Random.nextLong(idleWaitRange.first, idleWaitRange.second))
                            }
                        }
                    }
                })
                start()
            }
        }
        doStep()
    }

    /** Těžká chůze – pro Gudwina */
    private fun walkHeavy(fromX: Float, toX: Float) {
        val duration = walkDuration
        startWobble()
        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            this.duration = duration; interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedFraction
                // Těžké dupání – pomalé kývání těla
                val waddle = sin(p * PI.toFloat() * 3).toFloat() * 5f
                pokemonView.rotation = waddle
                val stomp = abs(sin(p * PI.toFloat() * 3)) * 4f * dp
                pokemonView.translationY = targetTranslationY - stomp
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.rotation = 0f
                    pokemonView.translationY = targetTranslationY
                    if (running && !isCrossing) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(idleWaitRange.first, idleWaitRange.second))
                    }
                }
            })
            start()
        }
    }

    /** Výchozí chůze */
    private fun walkDefault(fromX: Float, toX: Float) {
        val duration = walkDuration
        startWobble()
        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            this.duration = duration; interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (running && !isCrossing) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(idleWaitRange.first, idleWaitRange.second))
                    }
                }
            })
            start()
        }
    }

    // ─────────────────────────────────────────
    // CROSSING ANIMACE – přechod přes střed
    // ─────────────────────────────────────────

    private fun playCrossingAnimation(fromX: Float, toX: Float) {
        when (pokemonId) {
            "004", "005", "006",
            "014", "020", "021",
            "027", "028", "029" -> crossingWaterSurf(fromX, toX)

            "010", "011",
            "016", "024", "025", "026" -> crossingGhost(fromX, toX)

            "003", "019" -> crossingFlying(fromX, toX)

            "001", "002", "013" -> crossingFire(fromX, toX)

            "030" -> crossingHeavy(fromX, toX)

            "031" -> crossingAxlu(fromX, toX)

            else -> defaultCrossing(fromX, toX)
        }
    }

    private fun onCrossingDone() {
        isCrossing = false
        if (running) {
            startIdleAnimation()
            if (!isFlying && !isSleeping) startWobble()
            scheduleStep(Random.nextLong(idleWaitRange.first, idleWaitRange.second))
        }
    }

    private fun crossingWaterSurf(fromX: Float, toX: Float) {
        idleAnim?.cancel(); stopWobble()
        val parent = pokemonView.parent as? ViewGroup ?: run { isCrossing = false; return }

        val waveWidth  = (pokemonView.width * 2.2f).toInt()
        val waveHeight = (38 * dp).toInt()

        val waveContainer = android.widget.FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(waveWidth, waveHeight)
            x = pokemonView.x + pokemonView.width / 2f - waveWidth / 2f
            y = pokemonView.y + pokemonView.height - 12 * dp
            alpha = 0f; translationZ = 1f
        }
        val midLayer = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#0288D1")); setStroke((2 * dp).toInt(), Color.WHITE) }
            layoutParams = android.widget.FrameLayout.LayoutParams((waveWidth * 0.95f).toInt(), (waveHeight * 0.8f).toInt()).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.BOTTOM; bottomMargin = (4 * dp).toInt() }
        }
        val foamLayer = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#B3E5FC")) }
            layoutParams = android.widget.FrameLayout.LayoutParams((waveWidth * 0.6f).toInt(), (waveHeight * 0.3f).toInt()).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP; topMargin = (2 * dp).toInt() }
            alpha = 0.8f
        }
        waveContainer.addView(midLayer); waveContainer.addView(foamLayer)
        parent.addView(waveContainer)

        val p1 = ObjectAnimator.ofFloat(midLayer, "scaleX", 1f, 1.05f, 1f).apply { duration = 1200; repeatCount = -1; start() }
        val p2 = ObjectAnimator.ofFloat(foamLayer, "translationX", -10f * dp, 10f * dp).apply { duration = 1500; repeatCount = -1; repeatMode = ValueAnimator.REVERSE; start() }

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
                    duration = walkDuration + 500L; interpolator = AccelerateDecelerateInterpolator()
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
                                onCrossingDone()
                            }.start()
                        }
                    })
                    start()
                }
            }
        })
        startAnim.start()
    }

    private fun crossingGhost(fromX: Float, toX: Float) {
        idleAnim?.cancel(); stopWobble()
        facingRight = toX > fromX; applyFacing(facingRight)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = walkDuration + 300L; interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (toX - fromX) * p
                val wave = sin(p * PI.toFloat() * 3).toFloat()
                pokemonView.translationY = targetTranslationY + wave * 15f * dp
                pokemonView.alpha = 0.5f + abs(wave) * 0.5f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.alpha = 1f; pokemonView.translationY = targetTranslationY
                    onCrossingDone()
                }
            })
            start()
        }
    }

    private fun crossingFlying(fromX: Float, toX: Float) {
        idleAnim?.cancel(); stopWobble()
        facingRight = toX > fromX; applyFacing(facingRight)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = walkDuration; interpolator = AccelerateDecelerateInterpolator()
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
                    handler.postDelayed({ onCrossingDone() }, 400)
                }
            })
            start()
        }
    }

    private fun crossingFire(fromX: Float, toX: Float) {
        idleAnim?.cancel(); stopWobble()
        facingRight = toX > fromX; applyFacing(facingRight)
        var lastEmberTime = 0L
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = walkDuration - 400L; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (toX - fromX) * p
                val now = System.currentTimeMillis()
                if (now - lastEmberTime > 80) {
                    lastEmberTime = now
                    spawnEmberTrail(pokemonView.x, pokemonView.y + pokemonView.height * 0.7f)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { onCrossingDone() }
            })
            start()
        }
    }

    private fun crossingHeavy(fromX: Float, toX: Float) {
        idleAnim?.cancel(); stopWobble()
        facingRight = toX > fromX; applyFacing(facingRight)
        val parent = pokemonView.parent as? ViewGroup

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = walkDuration + 600L; interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (toX - fromX) * p
                pokemonView.rotation = sin(p * PI.toFloat() * 5).toFloat() * 5f
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.animate().rotation(0f).setDuration(200).start()
                    // Otřes při Gudwinově příchodu
                    parent?.let {
                        ObjectAnimator.ofFloat(it, "translationX", 0f, -8f * dp, 8f * dp, -4f * dp, 4f * dp, 0f)
                            .apply { duration = 350; start() }
                    }
                    handler.postDelayed({ onCrossingDone() }, 350)
                }
            })
            start()
        }
    }

    private fun crossingAxlu(fromX: Float, toX: Float) {
        idleAnim?.cancel(); stopWobble()
        facingRight = toX > fromX; applyFacing(facingRight)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = walkDuration; interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (toX - fromX) * p
                val wave = sin(p * PI.toFloat() * 4).toFloat()
                pokemonView.translationY = targetTranslationY + wave * 12f * dp
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.animate().translationY(targetTranslationY).setDuration(300).start()
                    handler.postDelayed({ onCrossingDone() }, 300)
                }
            })
            start()
        }
    }

    private fun defaultCrossing(fromX: Float, toX: Float) {
        facingRight = toX > fromX; applyFacing(facingRight)
        startWobble()
        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            duration = walkDuration + 200L; interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { onCrossingDone() }
            })
            start()
        }
    }

    // ─────────────────────────────────────────
    // IDLE ANIMACE
    // ─────────────────────────────────────────

    private fun startIdleAnimation() {
        idleAnim?.cancel()
        idleAnim = when (pokemonId) {
            "001", "002", "003" -> startIgnarIdle()
            "004", "005", "006" -> startAqulinIdle()
            "007", "008", "009" -> startFloriIdle()
            "010" -> startUmbexIdle()
            "011" -> startLumexIdle()
            "012" -> startSpirraIdle()
            "013" -> startFlamirraIdle()
            "014" -> startAquirraIdle()
            "015" -> startVerdirraIdle()
            "016" -> startShadirraIdle()
            "017" -> startCharmirraIdle()
            "018" -> startGlacirraIdle()
            "019" -> startDrakirraIdle()
            "020", "021" -> startFinletIdle()
            "022", "023" -> startMycitIdle()
            "024", "025", "026" -> startSouluIdle()
            "027", "028", "029" -> startPhantilIdle()
            "030" -> startGudwinIdle()
            "031" -> startAxluIdle()
            else  -> startDefaultIdle()
        }
    }

    private fun startIgnarIdle(): Animator {
        val shakeX = ObjectAnimator.ofFloat(pokemonView, "translationX", 0f, 3f * dp, -3f * dp, 2f * dp, -2f * dp, 0f).apply { duration = 800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        val breatheY = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.04f, baseScale).apply { duration = 1200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        return AnimatorSet().apply { playTogether(shakeX, breatheY); start() }
    }

    private fun startAqulinIdle(): Animator {
        val bob = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY, targetTranslationY - 6f * dp, targetTranslationY).apply {
            duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART; interpolator = AccelerateDecelerateInterpolator()
        }
        bob.start(); return bob
    }

    private fun startFloriIdle(): Animator {
        val sway = ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, 3f, 0f, -3f, 0f).apply { duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART; interpolator = AccelerateDecelerateInterpolator() }
        sway.start(); return sway
    }

    private fun startUmbexIdle(): Animator {
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY - 8f * dp, targetTranslationY + 8f * dp).apply { duration = 2200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator() }
        val pulse = ObjectAnimator.ofFloat(pokemonView, "alpha", 0.85f, 1f, 0.85f).apply { duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        scheduleGhostParticles(Color.parseColor("#9167AB"))
        return AnimatorSet().apply { playTogether(levitate, pulse); start() }
    }

    private fun startLumexIdle(): Animator {
        val pulse = ObjectAnimator.ofFloat(pokemonView, "scaleX", -baseScale, -baseScale * 1.1f, -baseScale).apply { duration = 1400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        val pulseY = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.1f, baseScale).apply { duration = 1400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY - 6f * dp, targetTranslationY + 6f * dp).apply { duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator() }
        scheduleGhostParticles(Color.parseColor("#FFD700"))
        return AnimatorSet().apply { playTogether(pulse, pulseY, levitate); start() }
    }

    private fun startSpirraIdle(): Animator {
        val breathe = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.05f, baseScale).apply { duration = 1600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        breathe.start(); return breathe
    }

    private fun startFlamirraIdle(): Animator {
        val shake = ObjectAnimator.ofFloat(pokemonView, "translationX", 0f, 2f * dp, -2f * dp, 1f * dp, -1f * dp, 0f).apply { duration = 600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        scheduleFireParticles()
        return shake.also { it.start() }
    }

    private fun startAquirraIdle(): Animator {
        val bob = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY, targetTranslationY - 5f * dp, targetTranslationY).apply { duration = 1600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART; interpolator = AccelerateDecelerateInterpolator() }
        scheduleWaterDrops()
        return bob.also { it.start() }
    }

    private fun startVerdirraIdle(): Animator {
        val sway = ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, 4f, 0f, -4f, 0f).apply { duration = 2200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART; interpolator = AccelerateDecelerateInterpolator() }
        sway.start(); return sway
    }

    private fun startShadirraIdle(): Animator {
        val blink = ObjectAnimator.ofFloat(pokemonView, "alpha", 1f, 0.6f, 1f, 0.8f, 1f).apply { duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        scheduleGhostParticles(Color.parseColor("#7B1FA2"))
        return blink.also { it.start() }
    }

    private fun startCharmirraIdle(): Animator {
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY - 5f * dp, targetTranslationY + 5f * dp).apply { duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator() }
        scheduleFairySparkles()
        return levitate.also { it.start() }
    }

    private fun startGlacirraIdle(): Animator {
        val shiver = ObjectAnimator.ofFloat(pokemonView, "translationX", 0f, 1.5f * dp, -1.5f * dp, 1f * dp, -1f * dp, 0f).apply { duration = 400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        shiver.start(); return shiver
    }

    private fun startDrakirraIdle(): Animator {
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY - 15f * dp, targetTranslationY + 15f * dp).apply { duration = 3000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator() }
        val breathe = ObjectAnimator.ofFloat(pokemonView, "scaleX", -baseScale, -baseScale * 1.06f, -baseScale).apply { duration = 1500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        return AnimatorSet().apply { playTogether(levitate, breathe); start() }
    }

    private fun startFinletIdle(): Animator {
        val wave = ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, 8f, 0f, -8f, 0f).apply { duration = 1200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART; interpolator = AccelerateDecelerateInterpolator() }
        val bob = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY, targetTranslationY - 4f * dp, targetTranslationY).apply { duration = 1600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        return AnimatorSet().apply { playTogether(wave, bob); start() }
    }

    private fun startMycitIdle(): Animator {
        val sniff = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY, targetTranslationY - 3f * dp, targetTranslationY).apply { duration = 500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        sniff.start(); return sniff
    }

    private fun startSouluIdle(): Animator {
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY - 10f * dp, targetTranslationY + 10f * dp).apply { duration = 2800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator() }
        val ghostAlpha = ObjectAnimator.ofFloat(pokemonView, "alpha", 0.7f, 1f, 0.7f).apply { duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        scheduleGhostParticles(Color.parseColor("#CE93D8"))
        return AnimatorSet().apply { playTogether(levitate, ghostAlpha); start() }
    }

    private fun startPhantilIdle(): Animator {
        val wave = ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, 5f, 0f, -5f, 0f).apply { duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        val ghostAlpha = ObjectAnimator.ofFloat(pokemonView, "alpha", 0.6f, 0.95f, 0.6f).apply { duration = 2200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY - 8f * dp, targetTranslationY + 8f * dp).apply { duration = 2400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE }
        return AnimatorSet().apply { playTogether(wave, ghostAlpha, levitate); start() }
    }

    private fun startGudwinIdle(): Animator {
        val breatheX = ObjectAnimator.ofFloat(pokemonView, "scaleX", -baseScale, -baseScale * 1.07f, -baseScale).apply { duration = 2300; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        val breatheY = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.07f, baseScale).apply { duration = 2300; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        spawnGudwinBubbles()
        return AnimatorSet().apply { playTogether(breatheX, breatheY); start() }
    }

    private fun spawnGudwinBubbles() {
        if (!running) return
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width * 0.62f; val cy = pokemonView.y + pokemonView.height * 0.22f
        val size = (9 * dp).toInt()
        val bubble = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(65, 180, 140, 100)); setStroke((1 * dp).toInt(), Color.argb(125, 160, 100, 60)) }
            layoutParams = ViewGroup.LayoutParams(size, size); x = cx; y = cy; alpha = 0.85f
        }
        parent.addView(bubble)
        bubble.animate().translationYBy(-72f * dp).translationXBy((Random.nextFloat() - 0.5f) * 24f * dp)
            .alpha(0f).scaleX(1.7f).scaleY(1.7f).setDuration(2600).setInterpolator(DecelerateInterpolator())
            .withEndAction { parent.removeView(bubble) }.start()
        handler.postDelayed({ spawnGudwinBubbles() }, Random.nextLong(1600, 3200))
    }

    private fun startAxluIdle(): Animator {
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY - 11f * dp, targetTranslationY + 11f * dp).apply {
            duration = 2500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator()
        }
        levitate.start(); scheduleAxluBubble(); return levitate
    }

    private fun scheduleAxluBubble() {
        if (!running) return
        handler.postDelayed({ if (running) { spawnAxluBubble(); scheduleAxluBubble() } }, Random.nextLong(4000, 8000))
    }

    private fun spawnAxluBubble() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height / 2f
        val size = (40 * dp).toInt()
        val bubble = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(0, 255, 182, 193)); setStroke((2 * dp).toInt(), Color.argb(180, 255, 105, 180)) }
            layoutParams = ViewGroup.LayoutParams(size, size); x = cx - size / 2f; y = cy - size / 2f; alpha = 0f; scaleX = 0f; scaleY = 0f
        }
        parent.addView(bubble)
        val grow = AnimatorSet().apply { playTogether(ObjectAnimator.ofFloat(bubble, "scaleX", 0f, 1f), ObjectAnimator.ofFloat(bubble, "scaleY", 0f, 1f), ObjectAnimator.ofFloat(bubble, "alpha", 0f, 0.9f)); duration = 600; interpolator = OvershootInterpolator() }
        val rainbow = ValueAnimator.ofArgb(Color.argb(180, 255, 105, 180), Color.argb(180, 255, 182, 193), Color.argb(180, 173, 216, 230), Color.argb(180, 255, 192, 203)).apply {
            duration = 1600; repeatCount = 2; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { anim -> (bubble.background as? GradientDrawable)?.setStroke((2 * dp).toInt(), anim.animatedValue as Int) }
        }
        val burst = AnimatorSet().apply { playTogether(ObjectAnimator.ofFloat(bubble, "scaleX", 1f, 1.6f, 0f), ObjectAnimator.ofFloat(bubble, "scaleY", 1f, 1.6f, 0f), ObjectAnimator.ofFloat(bubble, "alpha", 0.9f, 0f)); duration = 420; interpolator = AccelerateInterpolator() }
        AnimatorSet().apply {
            play(grow).before(rainbow); play(burst).after(rainbow)
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { parent.removeView(bubble) } })
            start()
        }
    }

    private fun startDefaultIdle(): Animator {
        val breathe = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.05f, baseScale).apply { duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        breathe.start(); return breathe
    }

    // ─────────────────────────────────────────
    // CLICK REAKCE
    // ─────────────────────────────────────────

    override fun onSpriteClicked() {
        if (!running || isCrossing) return
        when (pokemonId) {
            "001", "002", "003" -> fireBreathReaction()
            "004", "005", "006" -> waterSplashReaction()
            "007", "008", "009" -> leafSwayReaction()
            "010" -> ghostTapReaction()
            "011" -> lumexFlashReaction()
            "012" -> defaultTapReaction()
            "013" -> fireBreathReaction()
            "014" -> waterSplashReaction()
            "015" -> leafSwayReaction()
            "016" -> ghostTapReaction()
            "017" -> fairySparkleReaction()
            "018" -> iceShardReaction()
            "019" -> dragonRoarReaction()
            "020", "021" -> waterSplashReaction()
            "022", "023" -> defaultTapReaction()
            "024", "025", "026" -> ghostTapReaction()
            "027", "028", "029" -> ghostTapReaction()
            "030" -> snorlaxPoke()
            "031" -> axluTapReaction()
            else  -> defaultTapReaction()
        }
    }

    private fun fireBreathReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + (if (facingRight) pokemonView.width.toFloat() else 0f)
        val cy = pokemonView.y + pokemonView.height * 0.4f
        repeat(6) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val ember = View(context).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (i % 2 == 0) Color.parseColor("#FF6D00") else Color.parseColor("#FFD600")) }
                    val s = ((8 - i) * dp).toInt().coerceAtLeast(4)
                    layoutParams = ViewGroup.LayoutParams(s, s); x = cx; y = cy
                }
                parent.addView(ember)
                ember.animate().translationXBy((if (facingRight) 60f else -60f) * dp * (0.5f + Random.nextFloat()))
                    .translationYBy((Random.nextFloat() - 0.5f) * 40f * dp).alpha(0f).scaleX(2f).scaleY(2f)
                    .setDuration(600).withEndAction { parent.removeView(ember) }.start()
            }, i * 60L)
        }
        pokemonView.animate().scaleX(baseScale * (if (facingRight) -1.2f else 1.2f)).setDuration(200).withEndAction {
            pokemonView.animate().scaleX(baseScale * (if (facingRight) -1f else 1f)).setDuration(300)
                .withEndAction { if (running) startWobble() }.start()
        }.start()
    }

    private fun waterSplashReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height * 0.3f
        repeat(8) {
            val drop = View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#29B6F6")) }
                layoutParams = ViewGroup.LayoutParams((6 * dp).toInt(), (6 * dp).toInt()); x = cx; y = cy
            }
            parent.addView(drop)
            drop.animate().translationXBy((Random.nextFloat() - 0.5f) * 120f * dp)
                .translationYBy(-80f * dp - (Random.nextFloat() * 40f * dp)).alpha(0f).setDuration(700)
                .withEndAction { parent.removeView(drop) }.start()
        }
        pokemonView.animate().scaleY(baseScale * 1.2f).setDuration(150).withEndAction {
            pokemonView.animate().scaleY(baseScale).setDuration(250).withEndAction { if (running) startWobble() }.start()
        }.start()
    }

    private fun leafSwayReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height * 0.2f
        repeat(5) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val leaf = TextView(context).apply { text = "🍃"; textSize = 14f; x = cx; y = cy }
                parent.addView(leaf)
                leaf.animate().translationXBy((Random.nextFloat() - 0.5f) * 100f * dp).translationYBy(-60f * dp)
                    .rotation(Random.nextFloat() * 360f).alpha(0f).setDuration(1000)
                    .withEndAction { parent.removeView(leaf) }.start()
            }, i * 100L)
        }
        ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, -8f, 8f, -4f, 0f).apply { duration = 500; start() }
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
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(200, 255, 255, 200)) }
            layoutParams = ViewGroup.LayoutParams(pokemonView.width * 2, pokemonView.height * 2)
            x = pokemonView.x - pokemonView.width / 2f; y = pokemonView.y - pokemonView.height / 2f; alpha = 0f
        }
        parent.addView(flash)
        flash.animate().alpha(0.9f).setDuration(100).withEndAction {
            flash.animate().alpha(0f).setDuration(400).withEndAction { parent.removeView(flash) }.start()
        }.start()
        pokemonView.animate().scaleX(baseScale * -1.3f).scaleY(baseScale * 1.3f).setDuration(100).withEndAction {
            pokemonView.animate().scaleX(baseScale * -1f).scaleY(baseScale).setDuration(300).start()
        }.start()
    }

    private fun fairySparkleReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height / 2f
        repeat(8) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val sparkle = TextView(context).apply { text = "✨"; textSize = 12f; x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width; y = cy + (Random.nextFloat() - 0.5f) * pokemonView.height }
                parent.addView(sparkle)
                sparkle.animate().translationYBy(-50f * dp).alpha(0f).setDuration(800)
                    .withEndAction { parent.removeView(sparkle) }.start()
            }, i * 80L)
        }
        if (running) startWobble()
    }

    private fun iceShardReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height / 2f
        repeat(6) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val shard = View(context).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(Color.parseColor("#B3E5FC")) }
                    layoutParams = ViewGroup.LayoutParams((4 * dp).toInt(), (12 * dp).toInt()); x = cx; y = cy; rotation = (i * 60f)
                }
                parent.addView(shard)
                shard.animate().translationXBy(cos(Math.toRadians(i * 60.0)).toFloat() * 60f * dp)
                    .translationYBy(sin(Math.toRadians(i * 60.0)).toFloat() * 60f * dp).alpha(0f).setDuration(500)
                    .withEndAction { parent.removeView(shard) }.start()
            }, i * 40L)
        }
        ObjectAnimator.ofFloat(pokemonView, "scaleX", baseScale * -1f, baseScale * -1.15f, baseScale * -1f).apply { duration = 250; start() }
        if (running) handler.postDelayed({ startWobble() }, 300)
    }

    private fun dragonRoarReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        shakeView(pokemonView)
        val ring = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke((3 * dp).toInt(), Color.parseColor("#FF6D00")) }
            val s = pokemonView.width
            layoutParams = ViewGroup.LayoutParams(s, s); x = pokemonView.x; y = pokemonView.y + pokemonView.height / 4f
        }
        parent.addView(ring)
        ring.animate().scaleX(3f).scaleY(3f).alpha(0f).setDuration(600)
            .withEndAction { parent.removeView(ring); if (running) startWobble() }.start()
    }

    private fun axluTapReaction() {
        stopWobble(); moveAnim?.cancel()
        pokemonView.animate().translationYBy(-30f * dp).scaleX(baseScale * -1.15f).scaleY(baseScale * 1.15f)
            .setDuration(200).setInterpolator(DecelerateInterpolator()).withEndAction {
                pokemonView.animate().translationY(targetTranslationY).scaleX(baseScale * -1f).scaleY(baseScale)
                    .setDuration(300).setInterpolator(OvershootInterpolator()).withEndAction { if (running) startWobble() }.start()
            }.start()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y
        repeat(4) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val heart = TextView(context).apply { text = if (i % 2 == 0) "💗" else "💧"; textSize = 14f; x = cx + (Random.nextFloat() - 0.5f) * 60f * dp; y = cy }
                parent.addView(heart)
                heart.animate().translationYBy(-70f * dp).alpha(0f).setDuration(900).withEndAction { parent.removeView(heart) }.start()
            }, i * 120L)
        }
    }

    private fun defaultTapReaction() {
        pokemonView.animate().scaleX(baseScale * (if (facingRight) -1.35f else 1.35f)).scaleY(baseScale * 1.35f).setDuration(80)
            .withEndAction { pokemonView.animate().scaleX(baseScale * (if (facingRight) -1f else 1f)).scaleY(baseScale).setDuration(130).start() }.start()
    }

    private fun snorlaxPoke() {
        ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, -6f, 6f, -4f, 4f, 0f).apply { duration = 650; interpolator = LinearInterpolator(); start() }
    }

    // ─────────────────────────────────────────
    // WOBBLE & POMOCNÉ METODY
    // ─────────────────────────────────────────

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

    private fun shakeView(target: View) {
        ObjectAnimator.ofFloat(target, "translationX", 0f, -15f * dp, 15f * dp, -10f * dp, 10f * dp, -5f * dp, 5f * dp, 0f)
            .apply { duration = 500; start() }
    }

    private fun spawnBolt(parent: ViewGroup, cx: Float, cy: Float, large: Boolean) {
        val size = if (large) (14 * dp).toInt() else (8 * dp).toInt()
        val bolt = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#FFD600")) }
            layoutParams = ViewGroup.LayoutParams(size, size); x = cx - size / 2f; y = cy - size / 2f; alpha = 0.9f
        }
        parent.addView(bolt)
        bolt.animate().alpha(0f).scaleX(3f).scaleY(3f).setDuration(350).withEndAction { parent.removeView(bolt) }.start()
    }

    private fun spawnEmberTrail(cx: Float, cy: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val size = (6 * dp).toInt()
        val ember = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (Random.nextBoolean()) Color.parseColor("#FF6D00") else Color.parseColor("#FFD600")) }
            layoutParams = ViewGroup.LayoutParams(size, size); x = cx; y = cy
        }
        parent.addView(ember)
        ember.animate().translationXBy((Random.nextFloat() - 0.5f) * 20f * dp).translationYBy(-30f * dp)
            .alpha(0f).setDuration(400).withEndAction { parent.removeView(ember) }.start()
    }

    // ─────────────────────────────────────────
    // PARTICLE SYSTÉMY
    // ─────────────────────────────────────────

    private fun scheduleGhostParticles(color: Int) {
        if (!running) return
        val parent = pokemonView.parent as? ViewGroup ?: run { handler.postDelayed({ scheduleGhostParticles(color) }, 2000); return }
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height / 2f
        val size = (7 * dp).toInt()
        val particle = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width * 0.8f
            y = cy + (Random.nextFloat() - 0.5f) * pokemonView.height * 0.8f; alpha = 0f
        }
        parent.addView(particle)
        particle.animate().alpha(0.7f).translationYBy(-30f * dp).scaleX(0.3f).scaleY(0.3f).setDuration(800).withEndAction {
            particle.animate().alpha(0f).setDuration(400).withEndAction { parent.removeView(particle) }.start()
        }.start()
        handler.postDelayed({ scheduleGhostParticles(color) }, Random.nextLong(800, 2000))
    }

    private fun scheduleFireParticles() {
        if (!running) return
        val parent = pokemonView.parent as? ViewGroup ?: run { handler.postDelayed({ scheduleFireParticles() }, 1000); return }
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height * 0.4f
        val size = (5 * dp).toInt()
        val ember = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (Random.nextBoolean()) Color.parseColor("#FF6D00") else Color.parseColor("#FFD600")) }
            layoutParams = ViewGroup.LayoutParams(size, size); x = cx + (Random.nextFloat() - 0.5f) * 20f * dp; y = cy
        }
        parent.addView(ember)
        ember.animate().translationYBy(-25f * dp).translationXBy((Random.nextFloat() - 0.5f) * 15f * dp)
            .alpha(0f).setDuration(600).withEndAction { parent.removeView(ember) }.start()
        handler.postDelayed({ scheduleFireParticles() }, Random.nextLong(400, 900))
    }

    private fun scheduleWaterDrops() {
        if (!running) return
        val parent = pokemonView.parent as? ViewGroup ?: run { handler.postDelayed({ scheduleWaterDrops() }, 1000); return }
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height * 0.3f
        val size = (4 * dp).toInt()
        val drop = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#29B6F6")) }
            layoutParams = ViewGroup.LayoutParams(size, size); x = cx + (Random.nextFloat() - 0.5f) * 15f * dp; y = cy
        }
        parent.addView(drop)
        drop.animate().translationYBy(-20f * dp).alpha(0f).setDuration(700).withEndAction { parent.removeView(drop) }.start()
        handler.postDelayed({ scheduleWaterDrops() }, Random.nextLong(600, 1400))
    }

    private fun scheduleFairySparkles() {
        if (!running) return
        val parent = pokemonView.parent as? ViewGroup ?: run { handler.postDelayed({ scheduleFairySparkles() }, 1000); return }
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height / 2f
        val sparkle = TextView(context).apply {
            text = "✦"; textSize = 10f; setTextColor(Color.parseColor("#F8BBD0"))
            x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width; y = cy + (Random.nextFloat() - 0.5f) * pokemonView.height; alpha = 0f
        }
        parent.addView(sparkle)
        sparkle.animate().alpha(0.9f).translationYBy(-20f * dp).setDuration(400).withEndAction {
            sparkle.animate().alpha(0f).setDuration(400).withEndAction { parent.removeView(sparkle) }.start()
        }.start()
        handler.postDelayed({ scheduleFairySparkles() }, Random.nextLong(500, 1200))
    }
}