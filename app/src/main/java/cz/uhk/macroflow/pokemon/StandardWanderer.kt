package cz.uhk.macroflow.pokemon

import android.R.attr.text
import android.R.attr.textSize
import android.animation.*
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.*
import android.widget.FrameLayout
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

    private val isFlying get() = pokemonId in listOf("006", "012", "015", "150", "151")
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

    private fun startIdleAnimation() {
        idleAnim?.cancel()
        idleAnim = when (pokemonId) {
            "001" -> startBulbasaurIdle()
            "002" -> startIvysaurIdle()
            "003" -> startVenusaurIdle()
            "005" -> startCharmeleonIdle()
            "006" -> startCharizardIdle()
            "007" -> startSquirtleIdle()
            "008" -> startWartortleIdle()
            "009" -> startBlastoiseIdle()
            "010" -> startCaterpieIdle()
            "011" -> startMetapodIdle()
            "012" -> startButterfreeIdle()
            "013" -> startWeedleIdle()
            "014" -> startKakunaIdle()
            "015" -> startBeedrillIdle()
            "016" -> startPidgeyIdle()
            "017" -> startPidgeottoIdle()
            "018" -> startPidgeotIdle()
            "019" -> startRattataIdle()
            "020" -> startRaticateIdle()
            "021" -> startSpearowIdle()
            "022" -> startFearowIdle()
            "023" -> startEkansIdle()
            "024" -> startArbokIdle()

            "051" -> startDugtrioIdle()
            "092" -> startGastlyIdle()
            "093" -> startHaunterIdle()
            "131" -> startLaprasIdle()
            "132" -> startDittoIdle()
            "137" -> startPorygonIdle()
            "143" -> startSnorlaxIdle()
            "150" -> startMewtwoIdle()
            "151" -> startMewIdle()
            else  -> startDefaultIdle()
        }
    }

    override fun onSpriteClicked() {
        if (!running) return
        when (pokemonId) {

            "002" -> ivysaurTapReaction()
            "003" -> venusaurTapReaction()
            "004" -> squirtleTapReaction()
            "005" -> charmeleonTapReaction()
            "007" -> squirtleTapReaction()
            "008" -> wartortleTapReaction()
            "009" -> blastoiseTapReaction()
            "010" -> caterpieClickReaction()
            "011" -> metapodClickReaction()
            "012" -> butterfreeClickReaction()
            "013" -> weedleClickReaction()
            "014" -> kakunaClickReaction()
            "015" -> beedrillClickReaction()
            "016" -> pidgeyClickReaction()
            "017" -> pidgeottoClickReaction()
            "018" -> pidgeotClickReaction()
            "019" -> rattataClickReaction()
            "020" -> raticateClickReaction()
            "021" -> spearowClickReaction()
            "022" -> fearowClickReaction()
            "023" -> ekansClickReaction()
            "024" -> arbokClickReaction()


            "143" -> snorlaxPoke()
            "151" -> mewTapReaction()
            "150" -> mewtwoTapReaction()
            "092", "093", "094" -> ghostTapReaction()
            "025","026" -> pikachuTapReaction() // Použije blesky z Pikachu!
            "131" -> laprasTapReaction()
            "051" -> dugtrioTapReaction()
            "132" -> dittoTapReaction()
            else  -> defaultTapReaction()
        }
    }

    private fun playCrossingAnimation(fromX: Float, toX: Float) {
        when (pokemonId) {
            "001" -> crossingBulbasaur(fromX, toX)
            "002" -> crossingIvysaur(fromX, toX)
            "003" -> crossingVenusaur(fromX, toX)
            "004" -> crossingCharmander(fromX, toX)
            "005" -> crossingCharmeleon(fromX,toX)
            "006" -> crossingCharizard(fromX, toX)
            "007" -> crossingSquirtle(fromX, toX)
            "008" -> crossingWartortle(fromX, toX)
            "009" -> crossingBlastoise(fromX,toX)
            "010" -> crossingCaterpie(fromX, toX)
            "011" -> crossingMetapod(fromX, toX)
            "012" -> crossingButterfree(fromX, toX)
            "013" -> crossingWeedle(fromX, toX)
            "014" -> crossingKakuna(fromX, toX)
            "015" -> crossingBeedrill(fromX, toX)
            "016" -> crossingPidgey(fromX, toX)
            "017" -> crossingPidgeotto(fromX, toX)
            "018" -> crossingPidgeot(fromX, toX)
            "019" -> crossingRattata(fromX, toX)
            "020" -> crossingRaticate(fromX, toX)
            "021" -> crossingSpearow(fromX, toX)
            "022" -> crossingFearow(fromX, toX)
            "023" -> crossingEkans(fromX, toX)
            "024" -> crossingArbok(fromX, toX)

            "025" -> crossingPikachu(fromX, toX)

            "050" -> crossingDiglett(fromX, toX)
            "051" -> crossingDugtrio(fromX, toX)

            "092" -> crossingGastly(fromX, toX)
            "093" -> crossingHaunter(fromX, toX)
            "094" -> crossingGengar(fromX, toX)

            "115" -> crossingKangaskhan(fromX, toX)

            "131" -> crossingLapras(fromX, toX)
            "132" -> crossingDitto(fromX, toX)
            "133" -> crossingEevee(fromX, toX)
            "137" -> crossingPorygon(fromX, toX)

            "150" -> crossingMewtwo(fromX, toX)
            "151" -> crossingMew(fromX, toX)
            else  -> defaultCrossing(fromX, toX)
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



    private fun laprasTapReaction() {
        // Lapras radostně stříkne vodu (modré kuličky)
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height * 0.3f

        repeat(8) {
            val drop = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#00B0FF"))
                }
                layoutParams = ViewGroup.LayoutParams((6 * dp).toInt(), (6 * dp).toInt())
                x = cx; y = cy
            }
            parent.addView(drop)
            drop.animate()
                .translationXBy((Random.nextFloat() - 0.5f) * 150f * dp)
                .translationYBy(-100f * dp - (Random.nextFloat() * 50f * dp))
                .alpha(0f)
                .setDuration(800)
                .withEndAction { parent.removeView(drop) }
                .start()
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


    private fun crossingLapras(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return

        val waveWidth = (pokemonView.width * 2.2f).toInt()
        val waveHeight = (38 * dp).toInt()
        val dur = 3500L

        // 1. HLAVNÍ KONTEJNER
        val waveContainer = android.widget.FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(waveWidth, waveHeight)
            x = pokemonView.x + (pokemonView.width / 2f) - (waveWidth / 2f)
            y = pokemonView.y + pokemonView.height - (12 * dp)
            alpha = 0f
            translationZ = 1f
        }

        // 2. VRSTVY (Vytvoříme efekt hloubky posunutím středů)

        // Spodní tmavý stín vody
        val deepLayer = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1A237E")) // Hodně tmavě modrá
            }
            layoutParams = android.widget.FrameLayout.LayoutParams(waveWidth, (waveHeight * 0.9f).toInt()).apply {
                gravity = android.view.Gravity.BOTTOM
            }
            alpha = 0.5f
        }

        // Prostřední hlavní vlna
        val midLayer = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0288D1"))
                setStroke((2 * dp).toInt(), Color.WHITE)
            }
            layoutParams = android.widget.FrameLayout.LayoutParams((waveWidth * 0.95f).toInt(), (waveHeight * 0.8f).toInt()).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.BOTTOM
                bottomMargin = (4 * dp).toInt()
            }
        }

        // Horní jasná pěna (bílá elipsa posunutá dopředu)
        val foamLayer = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#B3E5FC")) // Světle modrá/bílá
            }
            layoutParams = android.widget.FrameLayout.LayoutParams((waveWidth * 0.6f).toInt(), (waveHeight * 0.3f).toInt()).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                topMargin = (2 * dp).toInt()
            }
            alpha = 0.8f
        }

        waveContainer.addView(deepLayer)
        waveContainer.addView(midLayer)
        waveContainer.addView(foamLayer)
        parent.addView(waveContainer)

        // 3. KOMPLEXNÍ PULZOVÁNÍ (Každá vrstva jinak, aby se to "mlelo")
        val p1 = ObjectAnimator.ofFloat(midLayer, "scaleX", 1f, 1.05f, 1f).apply { duration = 1200; repeatCount = -1; start() }
        val p2 = ObjectAnimator.ofFloat(foamLayer, "translationX", -10f * dp, 10f * dp).apply {
            duration = 1500; repeatCount = -1; repeatMode = ValueAnimator.REVERSE; start()
        }

        // --- ANIMACE STARTU (Výskok na vlnu) ---
        val startAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(waveContainer, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(waveContainer, "scaleY", 0.2f, 1f),
                ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY, targetTranslationY - 50f * dp),
                ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, if (toX > fromX) -15f else 15f)
            )
            duration = 700
        }

        startAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!running) return
                facingRight = toX > fromX
                applyFacing(facingRight)

                // --- SURFING (Sinusový let přes bar) ---
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = dur
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        val p = anim.animatedValue as Float
                        val currentX = fromX + (toX - fromX) * p

                        pokemonView.x = currentX
                        val angle = p * kotlin.math.PI.toFloat()
                        val heightArc = kotlin.math.sin(angle.toDouble()).toFloat() * 75f * dp

                        pokemonView.translationY = (targetTranslationY - 50f * dp) - heightArc
                        waveContainer.x = currentX + (pokemonView.width / 2f) - (waveWidth / 2f)
                        waveContainer.y = pokemonView.y + pokemonView.height - (15 * dp)

                        pokemonView.rotation = kotlin.math.cos(angle.toDouble()).toFloat() * (if (facingRight) -20f else 20f)
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: Animator) {
                            p1.cancel(); p2.cancel()
                            pokemonView.animate().rotation(0f).translationY(targetTranslationY).setDuration(600).start()
                            waveContainer.animate().alpha(0f).scaleX(0.5f).setDuration(600).withEndAction {
                                parent.removeView(waveContainer)
                                if (running) {
                                    startIdleAnimation()
                                    scheduleStep(Random.nextLong(2000, 4000))
                                }
                            }.start()
                        }
                    })
                    start()
                }
            }
        })
        startAnim.start()
    }


    private fun scheduleLaprasSinging() {
        // Pokud postava neběží nebo to není Lapras, ukončíme smyčku
        if (!running || pokemonId != "131") return

        handler.postDelayed({
            if (running && pokemonId == "131") {
                spawnSingingNotes() // Volá tu tvoji opravenou funkci s notami
                scheduleLaprasSinging() // Naplánuje další písničku
            }
        }, Random.nextLong(6000, 12000)) // Zazpívá každých 6 až 12 vteřin
    }

    private fun spawnSingingNotes() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + (20 * dp)

        // Lapras se při zpěvu trochu nafoukne (efekt nádechu)
        ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.1f, baseScale).apply {
            duration = 1000
            start()
        }

        repeat(3) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed

                val note = TextView(context).apply {
                    text = if (i % 2 == 0) "♪" else "♫"
                    textSize = 22f
                    setTextColor(Color.parseColor("#E1F5FE"))
                    setShadowLayer(4f, 0f, 0f, Color.WHITE)
                    x = cx
                    y = cy
                    alpha = 0f
                }
                parent.addView(note)

                // ValueAnimator s explicitní cestou ke kotlin.math
                val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 2500
                    addUpdateListener { a ->
                        val p = a.animatedValue as Float

                        // Pohyb nahoru
                        note.y = cy - (p * 120f * dp)

                        // Vlnění do stran - Tady je ta oprava:
                        val waveValue = kotlin.math.sin(p.toDouble() * kotlin.math.PI * 4.0).toFloat()
                        note.x = cx + (waveValue * 30f * dp)

                        // Zmizík a zvětšování
                        note.alpha = if (p < 0.2f) p * 5f else 1f - p
                        note.scaleX = 0.8f + p
                        note.scaleY = 0.8f + p
                    }
                }
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) {
                        parent.removeView(note)
                    }
                })
                anim.start()
            }, i * 600L)
        }
    }


    private fun startLaprasIdle(): Animator {
        // 1. Jemné houpání nahoru a dolů (jako na hladině)
        val floatAnim = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 4f * dp, targetTranslationY + 4f * dp).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 2. Spouštění efektu zpěvu (noty)
        scheduleLaprasSinging()

        floatAnim.start()
        return floatAnim
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

    private fun startCharmeleonIdle(): Animator {
        // 1. Dýchání a postoj (mírné naklánění a změna měřítka)
        val breath = ObjectAnimator.ofPropertyValuesHolder(
            pokemonView,
            PropertyValuesHolder.ofFloat("scaleY", baseScale, baseScale * 1.03f),
            PropertyValuesHolder.ofFloat("rotation", 0f, 3f)
        ).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 2. Naplánování efektů (oheň/kouř)
        scheduleCharmeleonEffects()

        breath.start()
        return breath
    }

    private fun scheduleCharmeleonEffects() {
        if (!running || pokemonId != "5") return // 5 = Charmeleon

        handler.postDelayed({
            if (running && pokemonId == "5") {
                spawnCharmeleonSmoke()
                scheduleCharmeleonEffects()
            }
        }, Random.nextLong(4000, 8000))
    }

    private fun spawnCharmeleonSmoke() {
        val parent = pokemonView.parent as? ViewGroup ?: return

        // Pozice u tlamy (odhadem podle směru pohledu)
        val offsetX = if (facingRight) (pokemonView.width * 0.7f) else (pokemonView.width * 0.3f)
        val startX = pokemonView.x + offsetX
        val startY = pokemonView.y + (pokemonView.height * 0.4f)

        repeat(2) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed

                val smoke = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.LTGRAY)
                    }
                    layoutParams = android.widget.FrameLayout.LayoutParams((8 * dp).toInt(), (8 * dp).toInt())
                    x = startX
                    y = startY
                    alpha = 0.6f
                }
                parent.addView(smoke)

                smoke.animate()
                    .translationYBy(-60f * dp)
                    .translationXBy(if (facingRight) 20f * dp else -20f * dp)
                    .alpha(0f)
                    .scaleX(3f)
                    .scaleY(3f)
                    .setDuration(1500)
                    .withEndAction { parent.removeView(smoke) }
                    .start()
            }, i * 300L)
        }
    }

    private fun crossingCharmeleon(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val dur = 850L // Rychlejší, agresivnější pohyb

        facingRight = toX > fromX
        applyFacing(facingRight)

        // --- 1. PŘÍPRAVA (Anticipace) ---
        // Charmeleon se přikrčí a mírně zčervená (nabíjení)
        val prepare = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 0.8f),
                ObjectAnimator.ofFloat(pokemonView, "scaleX", if(facingRight) baseScale else -baseScale, if(facingRight) baseScale * 1.2f else -baseScale * 1.2f),
                ValueAnimator.ofObject(ArgbEvaluator(), Color.WHITE, Color.parseColor("#FFAB91")).apply {
                    addUpdateListener { pokemonView.setColorFilter(it.animatedValue as Int) }
                }
            )
            duration = 300
        }

        prepare.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!running) return

                // --- 2. SAMOTNÝ VÝPAD ---
                pokemonView.animate()
                    .x(toX)
                    .scaleY(baseScale)
                    .scaleX(if(facingRight) baseScale else -baseScale)
                    .setDuration(dur)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .setUpdateListener {
                        // Spawnování ohnivých stop během pohybu
                        if (Random.nextInt(100) > 70) {
                            spawnFireParticle(
                                pokemonView.x + (pokemonView.width / 2f),
                                pokemonView.y + (pokemonView.height * 0.8f)
                            )
                        }
                    }
                    .withEndAction {
                        // --- 3. DOJEZD ---
                        pokemonView.clearColorFilter()
                        if (running) {
                            startIdleAnimation()
                            scheduleStep(Random.nextLong(2000, 4500))
                        }
                    }
                    .start()
            }
        })
        prepare.start()
    }

    /**
     * Pomocná funkce pro vytvoření efektu ohně za ocasem/nohama
     */
    private fun spawnFireParticle(tx: Float, ty: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val pSize = (Random.nextInt(8, 16) * dp).toInt()

        val particle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(Color.parseColor("#FFD54F"), Color.parseColor("#F44336"), Color.TRANSPARENT)
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = pSize / 2f
            }
            layoutParams = FrameLayout.LayoutParams(pSize, pSize)
            x = tx - (pSize / 2f)
            y = ty - (pSize / 2f)
            alpha = 0.8f
        }

        parent.addView(particle)

        particle.animate()
            .scaleX(0.2f)
            .scaleY(0.2f)
            .alpha(0f)
            .translationYBy(Random.nextInt(-20, 0) * dp)
            .setDuration(500)
            .withEndAction { parent.removeView(particle) }
            .start()
    }

    private fun charmeleonTapReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return

        // Zastavit veškeré plánované akce, aby se animace netloukly
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()
        wobbleAnim?.cancel()
        idleAnim?.cancel()

        // 1. DASHBOARD SHAKE (Agresivní otřes celé obrazovky)
        val shake = ObjectAnimator.ofFloat(parent, "translationX", 0f, -20f * dp, 20f * dp, -15f * dp, 15f * dp, 0f).apply {
            duration = 400
        }

        // 2. VĚTŠÍ SLASH OVERLAY (Zvětšeno na 2.5x šířku pokémona)
        val slashWidth = (pokemonView.width * 2.5f).toInt()
        val slashHeight = (pokemonView.height * 2.5f).toInt()

        val slashView = object : View(context) {
            init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                strokeWidth = 8f * dp // Silnější čáry
                strokeCap = Paint.Cap.ROUND
                style = Paint.Style.STROKE
                setShadowLayer(30f, 0f, 0f, Color.parseColor("#FF0000")) // Brutální záře
            }
            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                // Rozmáchlejší diagonální řezy
                canvas.drawLine(w * 0.1f, h * 0.2f, w * 0.6f, h * 0.9f, paint)
                canvas.drawLine(w * 0.3f, h * 0.1f, w * 0.8f, h * 0.8f, paint)
                canvas.drawLine(w * 0.5f, h * 0.2f, w * 0.9f, h * 0.7f, paint)
            }
        }.apply {
            layoutParams = ViewGroup.LayoutParams(slashWidth, slashHeight)
            // Vycentrování na Charmeleona
            x = pokemonView.x + (pokemonView.width / 2f) - (slashWidth / 2f)
            y = pokemonView.y + (pokemonView.height / 2f) - (slashHeight / 2f)
            alpha = 0f
            translationZ = 100f
        }
        parent.addView(slashView)

        // 3. ANIMACE ÚTOKU
        val attackSet = AnimatorSet().apply {
            playTogether(
                shake,
                // Charmeleon se prudce vyřítí vpřed (Scale)
                ObjectAnimator.ofFloat(pokemonView, "scaleX", pokemonView.scaleX, pokemonView.scaleX * 1.5f, pokemonView.scaleX),
                ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.5f, baseScale),

                // Postupné zrudnutí a návrat (vydrží déle)
                ValueAnimator.ofObject(ArgbEvaluator(), Color.WHITE, Color.RED, Color.RED, Color.WHITE).apply {
                    duration = 600
                    addUpdateListener { pokemonView.setColorFilter(it.animatedValue as Int, PorterDuff.Mode.MULTIPLY) }
                },

                // Slash efekty: rychlé bliknutí
                ObjectAnimator.ofFloat(slashView, "alpha", 0f, 1f, 1f, 0f).apply {
                    duration = 450
                },
                ObjectAnimator.ofFloat(slashView, "scaleX", 0.8f, 1.2f).apply { duration = 450 },
                ObjectAnimator.ofFloat(slashView, "scaleY", 0.8f, 1.2f).apply { duration = 450 }
            )
        }

        attackSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                parent.removeView(slashView)
                pokemonView.clearColorFilter()

                // Po útoku ještě vyprskne jiskry
                spawnEmbers()

                if (running) {
                    startWobble()
                    startIdleAnimation()
                    // Po takovém útoku chvíli stojí (vydýchává to)
                    scheduleStep(Random.nextLong(3000, 5000))
                }
            }
        })

        attackSet.start()
    }


    private fun spawnEmbers() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        // Jiskry letí z místa, kde má Charmeleon tlamu (přibližně horní třetina)
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + (20 * dp)

        repeat(5) { i ->
            val ember = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    // Střídáme barvy ohně
                    setColor(if (i % 2 == 0) Color.parseColor("#FFD54F") else Color.parseColor("#FF5722"))
                }
                layoutParams = ViewGroup.LayoutParams((5 * dp).toInt(), (5 * dp).toInt())
                x = cx; y = cy; alpha = 1f
            }
            parent.addView(ember)

            ember.animate()
                .translationXBy((Random.nextFloat() - 0.5f) * 120 * dp)
                .translationYBy(-(Random.nextFloat() * 80 * dp))
                .alpha(0f)
                .scaleX(0.2f).scaleY(0.2f)
                .setDuration(Random.nextLong(600, 1000))
                .withEndAction { parent.removeView(ember) }
                .start()
        }
    }

    private fun startDugtrioIdle(): Animator {
        // Dugtrio se mírně vlní (simulace pohybu tří hlav)
        val idle = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 0.92f, baseScale).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleDugtrioDust()
        idle.start()
        return idle
    }

    private fun scheduleDugtrioDust() {
        if (!running || pokemonId != "051") return
        handler.postDelayed({
            if (running && pokemonId == "051") {
                spawnDustCloud() // Prach u základny
                scheduleDugtrioDust()
            }
        }, Random.nextLong(2000, 4000))
    }

    private fun spawnDustCloud() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height - (5 * dp)

        repeat(3) {
            val dust = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#A1887F")) // Hnědá barva země
                }
                layoutParams = ViewGroup.LayoutParams((12 * dp).toInt(), (8 * dp).toInt())
                x = cx + (Random.nextFloat() - 0.5f) * 40 * dp
                y = cy
                alpha = 0.6f
            }
            parent.addView(dust)
            dust.animate()
                .translationYBy(Random.nextFloat() * -15 * dp)
                .alpha(0f).scaleX(1.5f).scaleY(1.5f)
                .setDuration(800).withEndAction { parent.removeView(dust) }.start()
        }
    }

    private fun crossingDugtrio(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val dur = 1300L

        // 1. Zaleze pod zem
        pokemonView.animate()
            .translationY(targetTranslationY + 40 * dp)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                facingRight = toX > fromX
                applyFacing(facingRight)

                // 2. Pohyb pod zemí s prachem
                val moveAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = dur
                    addUpdateListener { anim ->
                        val p = anim.animatedValue as Float
                        pokemonView.x = fromX + (toX - fromX) * p

                        // Efekt hlíny při pohybu
                        if (anim.currentPlayTime % 100 < 20) {
                            spawnDirtKickup(pokemonView.x + pokemonView.width/2f, targetTranslationY + pokemonView.height)
                        }
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: Animator) {
                            // 3. Vyleze ven
                            pokemonView.animate()
                                .translationY(targetTranslationY)
                                .setDuration(300)
                                .setInterpolator(OvershootInterpolator())
                                .withEndAction {
                                    if (running) {
                                        startIdleAnimation()
                                        scheduleStep(Random.nextLong(1000, 2500))
                                    }
                                }.start()
                        }
                    })
                }
                moveAnim.start()
            }.start()
    }

    private fun spawnDirtKickup(tx: Float, ty: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val dirt = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#795548"))
            }
            layoutParams = ViewGroup.LayoutParams((8 * dp).toInt(), (8 * dp).toInt())
            x = tx; y = ty - (10 * dp)
        }
        parent.addView(dirt, 0)
        dirt.animate().translationYBy(-20 * dp).translationXBy((Random.nextFloat()-0.5f)*30*dp)
            .alpha(0f).setDuration(400).withEndAction { parent.removeView(dirt) }.start()
    }


    private fun dugtrioTapReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()
        wobbleAnim?.cancel()

        // 1. PŘÍPRAVA (Dugtrio se naštvaně klepe)
        val charge = ObjectAnimator.ofFloat(pokemonView, "translationX", -2 * dp, 2 * dp).apply {
            duration = 50; repeatCount = 10
        }

        // 2. ZEMĚTŘESENÍ
        val shake = ObjectAnimator.ofFloat(parent, "translationY", 0f, 15f * dp, -15f * dp, 10f * dp, -10f * dp, 0f).apply {
            duration = 500
        }

        // 3. ROCK BLAST (Vylétající kameny)
        val rockAction = {
            repeat(6) {
                val rock = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.DKGRAY)
                        cornerRadius = 4 * dp
                    }
                    layoutParams = ViewGroup.LayoutParams((15 * dp).toInt(), (15 * dp).toInt())
                    x = pokemonView.x + pokemonView.width / 2f
                    y = pokemonView.y + pokemonView.height
                    rotation = Random.nextFloat() * 360f
                }
                parent.addView(rock)
                rock.animate()
                    .translationYBy(-(Random.nextFloat() * 150 * dp))
                    .translationXBy((Random.nextFloat() - 0.5f) * 200 * dp)
                    .rotationBy(Random.nextFloat() * 720f)
                    .alpha(0f).setDuration(700)
                    .withEndAction { parent.removeView(rock) }.start()
            }
        }

        AnimatorSet().apply {
            play(charge).before(shake)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(a: Animator) {
                    // Spustit kameny přesně při dopadu zemětřesení
                    handler.postDelayed({ rockAction() }, 500)
                }
                override fun onAnimationEnd(a: Animator) {
                    if (running) {
                        startWobble()
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(2000, 4000))
                    }
                }
            })
            start()
        }
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

    // ─────────────────────────────────────────────
// BULBASAUR LINE
// ─────────────────────────────────────────────

    /**
     * Bulbasaur idle — cibulovitý výhonek na zádech pravidelně pulzuje,
     * jako by absorboval sluneční energii.
     */
    private fun startBulbasaurIdle(): Animator {
        // Jemné dýchání těla
        val breathe = ObjectAnimator.ofPropertyValuesHolder(
            pokemonView,
            PropertyValuesHolder.ofFloat("scaleX", -baseScale, -baseScale * 1.04f, -baseScale),
            PropertyValuesHolder.ofFloat("scaleY", baseScale, baseScale * 1.05f, baseScale)
        ).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        // Naplánujeme občasné vypouštění spór
        scheduleBulbasaurSpores()
        breathe.start()
        return breathe
    }

    private fun scheduleBulbasaurSpores() {
        if (!running || pokemonId != "001") return
        handler.postDelayed({
            if (running && pokemonId == "001") {
                spawnBulbasaurSpores()
                scheduleBulbasaurSpores()
            }
        }, Random.nextLong(5000, 9000))
    }

    private fun spawnBulbasaurSpores() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        // Spóry letí z cibule na zádech — přibližně horní střed spritu
        val cx = pokemonView.x + pokemonView.width * 0.5f
        val cy = pokemonView.y + pokemonView.height * 0.2f

        repeat(6) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val spore = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#81C784")) // Světle zelená
                    }
                    layoutParams = ViewGroup.LayoutParams((5 * dp).toInt(), (5 * dp).toInt())
                    x = cx; y = cy; alpha = 0.9f
                }
                parent.addView(spore)
                spore.animate()
                    .translationXBy((Random.nextFloat() - 0.5f) * 60f * dp)
                    .translationYBy(-50f * dp - Random.nextFloat() * 30f * dp)
                    .alpha(0f).scaleX(1.5f).scaleY(1.5f)
                    .setDuration(1400)
                    .withEndAction { parent.removeView(spore) }
                    .start()
            }, i * 150L)
        }
    }

    /**
     * Bulbasaur přechod — vylepšená verze. Místo jen jedné kuličky
     * teď vyrazí vine whip (zelená čára) a pak se teleportuje přes spóry.
     */
    private fun crossingBulbasaur(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()

        // 1. Vine whip efekt — zelená čára vystřelí ve směru pohybu
        val vineWidth = (abs(toX - fromX) * 0.4f).toInt().coerceAtLeast((20 * dp).toInt())
        val vine = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#4CAF50"))
                cornerRadius = 3f * dp
            }
            layoutParams = ViewGroup.LayoutParams(vineWidth, (4 * dp).toInt())
            x = pokemonView.x + pokemonView.width / 2f
            y = pokemonView.y + pokemonView.height * 0.3f
            scaleX = 0f; alpha = 0.8f
            pivotX = 0f // Roste od Pokémona ven
        }
        parent.addView(vine)

        vine.animate().scaleX(1f).setDuration(200).withEndAction {
            // 2. Spóry při zmizení
            spawnBulbasaurSpores()

            pokemonView.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(200).withEndAction {
                pokemonView.x = toX
                facingRight = toX > fromX
                applyFacing(facingRight)

                // 3. Vine zmizí a Bulbasaur se znovu složí
                vine.animate().alpha(0f).setDuration(150).withEndAction {
                    parent.removeView(vine)
                }.start()

                pokemonView.animate().alpha(1f).scaleX(baseScale).scaleY(baseScale)
                    .setDuration(280)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .withEndAction {
                        if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
                    }.start()
            }.start()
        }.start()
    }

    /**
     * Ivysaur idle — květ začíná poupě, pravidelně se lehce otevírá.
     * Těžší než Bulbasaur, pohyb je pomalejší a stabilnější.
     */
    private fun startIvysaurIdle(): Animator {
        // Pomalejší dýchání — Ivysaur je těžší
        val breathe = ObjectAnimator.ofPropertyValuesHolder(
            pokemonView,
            PropertyValuesHolder.ofFloat("scaleX", -baseScale, -baseScale * 1.05f, -baseScale),
            PropertyValuesHolder.ofFloat("scaleY", baseScale, baseScale * 1.06f, baseScale)
        ).apply {
            duration = 2800 // Pomalejší než Bulbasaur
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleIvysaurBud()
        breathe.start()
        return breathe
    }

    private fun scheduleIvysaurBud() {
        if (!running || pokemonId != "002") return
        handler.postDelayed({
            if (running && pokemonId == "002") {
                spawnBudEffect()
                scheduleIvysaurBud()
            }
        }, Random.nextLong(4000, 7000))
    }

    /**
     * Poupě se lehce rozevře a pustí fialové jedovaté spóry —
     * Ivysaur je jedovatý typ.
     */
    private fun spawnBudEffect() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width * 0.5f
        val cy = pokemonView.y + pokemonView.height * 0.15f // Květ je nahoře

        // Fialový záblesk poupěte
        val budFlash = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CE93D8")) // Světle fialová
            }
            layoutParams = ViewGroup.LayoutParams((14 * dp).toInt(), (14 * dp).toInt())
            x = cx - 7 * dp; y = cy - 7 * dp; alpha = 0f
        }
        parent.addView(budFlash)

        budFlash.animate().alpha(0.8f).scaleX(1.5f).scaleY(1.5f).setDuration(300)
            .withEndAction {
                // Jedovaté spóry v kruhu
                repeat(8) { i ->
                    val angle = i * (360f / 8f)
                    val spore = View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(if (i % 2 == 0) Color.parseColor("#7B1FA2") else Color.parseColor("#AB47BC"))
                        }
                        layoutParams = ViewGroup.LayoutParams((6 * dp).toInt(), (6 * dp).toInt())
                        x = cx; y = cy; alpha = 0.85f
                    }
                    parent.addView(spore)
                    val dist = 45f * dp
                    spore.animate()
                        .translationXBy(dist * cos(Math.toRadians(angle.toDouble())).toFloat())
                        .translationYBy(dist * sin(Math.toRadians(angle.toDouble())).toFloat())
                        .alpha(0f).scaleX(0.5f).scaleY(0.5f)
                        .setDuration(900)
                        .withEndAction { parent.removeView(spore) }
                        .start()
                }
                budFlash.animate().alpha(0f).setDuration(300)
                    .withEndAction { parent.removeView(budFlash) }.start()
            }.start()
    }

    /**
     * Ivysaur přechod — používá Razor Leaf. Série lístků letí dopředu
     * a Ivysaur se teleportuje skrze zelený výbuch.
     */
    private fun crossingIvysaur(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()

        val movingRight = toX > fromX

        // 1. Razor Leaf — 4 lístky vystřelí ve směru pohybu
        repeat(4) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val leaf = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#66BB6A"))
                    }
                    layoutParams = ViewGroup.LayoutParams((10 * dp).toInt(), (5 * dp).toInt())
                    x = pokemonView.x + pokemonView.width / 2f
                    y = pokemonView.y + pokemonView.height * (0.2f + i * 0.15f)
                    rotation = if (movingRight) -20f + i * 10f else 20f - i * 10f
                    alpha = 0.9f
                }
                parent.addView(leaf)
                val dirX = if (movingRight) 200f * dp else -200f * dp
                leaf.animate()
                    .translationXBy(dirX)
                    .translationYBy((Random.nextFloat() - 0.5f) * 30f * dp)
                    .rotationBy(if (movingRight) 360f else -360f)
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction { parent.removeView(leaf) }
                    .start()
            }, i * 60L)
        }

        // 2. Po lístcích — Ivysaur se zatáhne do sebe (sbalí) a přeskočí
        handler.postDelayed({
            if (!running) return@postDelayed

            // Zelený výbuch na místě startu
            val burst = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(Color.parseColor("#4CAF50"))
                }
                layoutParams = ViewGroup.LayoutParams((30 * dp).toInt(), (30 * dp).toInt())
                x = pokemonView.x + pokemonView.width / 2f - 15 * dp
                y = pokemonView.y + pokemonView.height / 2f - 15 * dp
                alpha = 0.7f
            }
            parent.addView(burst)
            burst.animate().scaleX(2.5f).scaleY(2.5f).alpha(0f).setDuration(400)
                .withEndAction { parent.removeView(burst) }.start()

            pokemonView.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(250).withEndAction {
                pokemonView.x = toX
                facingRight = movingRight
                applyFacing(facingRight)
                pokemonView.animate().alpha(1f).scaleX(baseScale).scaleY(baseScale)
                    .setDuration(320).setInterpolator(OvershootInterpolator(1.3f))
                    .withEndAction {
                        // Malý záchvěv po dopadu (Ivysaur je těžší než Bulbasaur)
                        ObjectAnimator.ofFloat(pokemonView, "translationY",
                            targetTranslationY, targetTranslationY - 8f * dp, targetTranslationY
                        ).apply { duration = 300; start() }
                        if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
                    }.start()
            }.start()
        }, 280L)
    }

    private fun ivysaurTapReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()
        wobbleAnim?.cancel()

        // Poison Powder — velký oblak jedovatých spór
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.height * 0.15f

        // Ivysaur se nafoukne a pak pustí jedovatý oblak
        pokemonView.animate()
            .scaleX(-baseScale * 1.2f).scaleY(baseScale * 1.2f).setDuration(200)
            .withEndAction {
                pokemonView.animate()
                    .scaleX(-baseScale).scaleY(baseScale).setDuration(200).start()

                repeat(12) { i ->
                    handler.postDelayed({
                        if (!running) return@postDelayed
                        val cloud = View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.parseColor(if (i % 3 == 0) "#9C27B0" else if (i % 3 == 1) "#7B1FA2" else "#E1BEE7"))
                            }
                            val sz = (Random.nextInt(8, 16) * dp).toInt()
                            layoutParams = ViewGroup.LayoutParams(sz, sz)
                            x = cx; y = cy; alpha = 0.8f
                        }
                        parent.addView(cloud)
                        cloud.animate()
                            .translationXBy((Random.nextFloat() - 0.5f) * 100f * dp)
                            .translationYBy(-60f * dp - Random.nextFloat() * 40f * dp)
                            .alpha(0f).scaleX(3f).scaleY(3f)
                            .setDuration(Random.nextLong(800, 1400))
                            .withEndAction { parent.removeView(cloud) }
                            .start()
                    }, i * 80L)
                }

                if (running) {
                    handler.postDelayed({
                        startWobble()
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(2000, 4000))
                    }, 1200L)
                }
            }.start()
    }

    /**
     * Venusaur idle — velký plně rozvinutý květ. Těžký, pomalý.
     * Květ pravidelně září (fotosyntéza) a vypouští zlatý pyl.
     */
    private fun startVenusaurIdle(): Animator {
        val breathe = ObjectAnimator.ofPropertyValuesHolder(
            pokemonView,
            PropertyValuesHolder.ofFloat("scaleX", -baseScale, -baseScale * 1.03f, -baseScale),
            PropertyValuesHolder.ofFloat("scaleY", baseScale, baseScale * 1.04f, baseScale)
        ).apply {
            duration = 3500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleVenusaurPollen()
        breathe.start()
        return breathe
    }

    private fun scheduleVenusaurPollen() {
        if (!running || pokemonId != "003") return
        handler.postDelayed({
            if (running && pokemonId == "003") {
                spawnSolarPollen()
                scheduleVenusaurPollen()
            }
        }, Random.nextLong(3500, 6000))
    }

    /**
     * Místo setColorFilter (způsobuje bílý čtverec kolem ImageView) použijeme
     * průhledný kruhový overlay přímo nad pokemonView — zlatý záblesk bez artefaktů.
     */
    private fun flashGolden(parent: ViewGroup, duration: Long = 800L) {
        val cx = pokemonView.x + pokemonView.width * 0.5f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.3f

        repeat(8) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val glow = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        // Střídáme jasně zlatou a světle žlutou
                        setColor(if (i % 2 == 0) Color.parseColor("#FFD54F") else Color.parseColor("#FFF9C4"))
                    }
                    // Větší částice — 16–28dp
                    val sz = (Random.nextInt(10, 24) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width
                    y = cy + (Random.nextFloat() - 0.5f) * pokemonView.height * 0.6f
                    alpha = 0f
                }
                parent.addView(glow)

                // Rychle se rozsvítí a pak zmizí
                glow.animate()
                    .alpha(0.9f)
                    .setDuration(120)
                    .withEndAction {
                        glow.animate()
                            .alpha(0f)
                            .scaleX(2f).scaleY(2f)
                            .translationYBy(-20f * dp)
                            .setDuration(duration - 120)
                            .withEndAction { parent.removeView(glow) }
                            .start()
                    }.start()
            }, i * 80L)
        }
    }
    /**
     * Zlatý sluneční pyl padá z květu dolů.
     * Používá flashGolden overlay místo setColorFilter — žádný bílý čtverec.
     */
    private fun spawnSolarPollen() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        // Správná pozice — musíme přičíst translationY
        val cx = pokemonView.x + pokemonView.width * 0.5f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.1f

        flashGolden(parent, 800L)

        repeat(10) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val pollen = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (i % 2 == 0) Color.parseColor("#FFD54F") else Color.parseColor("#FFEE58"))
                    }
                    val sz = (Random.nextInt(3, 7) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width * 0.6f
                    y = cy
                    alpha = 0.9f
                }
                parent.addView(pollen)
                pollen.animate()
                    .translationYBy(50f * dp + Random.nextFloat() * 30f * dp)
                    .translationXBy((Random.nextFloat() - 0.5f) * 25f * dp)
                    .alpha(0f)
                    .setDuration(1800)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { parent.removeView(pollen) }
                    .start()
            }, i * 100L)
        }
    }

    /**
     * Venusaur přechod — Solar Beam.
     * Nabije se (flashGolden overlay), vystřelí paprsek a teleportuje se.
     * Bez setColorFilter — žádný bílý čtverec.
     */
    private fun crossingVenusaur(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()

        val movingRight = toX > fromX

        // 1. Nabíjení — zlatý overlay místo ColorFilter
        flashGolden(parent, 500L)

        // 2. Po nabití — vystřelí Solar Beam paprsek
        handler.postDelayed({
            if (!running) return@postDelayed

            val beamH = (6 * dp).toInt()
            val beamW = parent.width
            val beamStartX = if (movingRight) pokemonView.x + pokemonView.width / 2f else 0f

            val beam = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    colors = if (movingRight)
                        intArrayOf(Color.parseColor("#FFFDE7"), Color.parseColor("#FFD54F"), Color.TRANSPARENT)
                    else
                        intArrayOf(Color.TRANSPARENT, Color.parseColor("#FFD54F"), Color.parseColor("#FFFDE7"))
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                    cornerRadius = 3f * dp
                }
                layoutParams = ViewGroup.LayoutParams(beamW, beamH)
                x = beamStartX
                y = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.25f
                scaleX = 0f
                alpha = 0.85f
                // Paprsek roste od Venusaurovy strany ve správném směru
                pivotX = if (movingRight) 0f else beamW.toFloat()
            }
            parent.addView(beam)

            beam.animate().scaleX(1f).setDuration(250).withEndAction {
                // 3. Venusaur mizí v paprsku
                pokemonView.animate().alpha(0f).scaleX(0.1f).scaleY(0.1f).setDuration(200)
                    .withEndAction {
                        pokemonView.x = toX
                        facingRight = movingRight
                        applyFacing(facingRight)

                        // Paprsek zvolna zmizí
                        beam.animate().alpha(0f).setDuration(300)
                            .withEndAction { parent.removeView(beam) }.start()

                        // 4. Venusaur se složí na cíli
                        pokemonView.animate().alpha(1f).scaleX(baseScale).scaleY(baseScale)
                            .setDuration(400).setInterpolator(OvershootInterpolator(1.1f))
                            .withEndAction {
                                // Těžký dopad — otřes
                                val grandParent = parent.parent as? ViewGroup
                                grandParent?.let {
                                    ObjectAnimator.ofFloat(it, "translationY",
                                        0f, 8f * dp, -5f * dp, 3f * dp, 0f
                                    ).apply { duration = 400; start() }
                                }
                                // Zlatý pyl po dopadu
                                spawnSolarPollen()
                                if (running) { startWobble(); scheduleStep(Random.nextLong(2000, 4000)) }
                            }.start()
                    }.start()
            }.start()
        }, 500L)
    }

    private fun venusaurTapReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()
        wobbleAnim?.cancel()

        val grandParent = parent.parent as? ViewGroup

        // 1. Přískoč
        pokemonView.animate()
            .translationY(targetTranslationY - 20f * dp).setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // 2. Dopad
                pokemonView.animate()
                    .translationY(targetTranslationY).setDuration(200)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        // 3. Earthquake otřes
                        grandParent?.let {
                            ObjectAnimator.ofFloat(it, "translationX",
                                0f, -18f * dp, 18f * dp, -12f * dp, 12f * dp, -6f * dp, 6f * dp, 0f
                            ).apply { duration = 500; start() }
                            ObjectAnimator.ofFloat(it, "translationY",
                                0f, 10f * dp, -10f * dp, 6f * dp, 0f
                            ).apply { duration = 500; start() }
                        }

                        // Rázové vlny ze zemětřesení
                        repeat(3) { i ->
                            handler.postDelayed({
                                if (!running) return@postDelayed
                                spawnStompWave(
                                    parent,
                                    pokemonView.x + pokemonView.width / 2f,
                                    pokemonView.y + pokemonView.height
                                )
                            }, i * 120L)
                        }

                        if (running) {
                            handler.postDelayed({
                                startWobble()
                                startIdleAnimation()
                                scheduleStep(Random.nextLong(2500, 5000))
                            }, 600L)
                        }
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

    // ─────────────────────────────────────────────
// SQUIRTLE LINE
// ─────────────────────────────────────────────

    /**
     * Squirtle idle — jemné houpání jako na hladině vody.
     * Občas vystříkne vodní kapičky.
     */
    private fun startSquirtleIdle(): Animator {
        val float = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 3f * dp, targetTranslationY + 3f * dp).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleSquirtleWaterDrops()
        float.start()
        return float
    }

    private fun scheduleSquirtleWaterDrops() {
        if (!running || pokemonId != "007") return
        handler.postDelayed({
            if (running && pokemonId == "007") {
                spawnWaterDrops(count = 4, fromTail = false)
                scheduleSquirtleWaterDrops()
            }
        }, Random.nextLong(4000, 7000))
    }

    /**
     * Kapičky vody — sdílené pro celou linii, fromTail určuje
     * zda letí z ocásku (Squirtle) nebo z děl (Blastoise).
     */
    private fun spawnWaterDrops(count: Int, fromTail: Boolean) {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width * if (fromTail) 0.3f else 0.5f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.3f

        repeat(count) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val drop = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#4FC3F7"))
                    }
                    val sz = (Random.nextInt(4, 8) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width * 0.6f
                    y = cy
                    alpha = 0.9f
                }
                parent.addView(drop)
                drop.animate()
                    .translationYBy(-35f * dp - Random.nextFloat() * 20f * dp)
                    .translationXBy((Random.nextFloat() - 0.5f) * 30f * dp)
                    .alpha(0f)
                    .setDuration(900)
                    .withEndAction { parent.removeView(drop) }
                    .start()
            }, i * 120L)
        }
    }

    /**
     * Squirtle přechod — schová se do krunýře (scaleY → 0),
     * přejede jako modrá střela a vynoří se s vodní tříští.
     */
    private fun crossingSquirtle(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()

        // 1. Zatažení do krunýře
        pokemonView.animate()
            .scaleY(0.12f)
            .scaleX(baseScale * 1.3f) // Krunýř se roztáhne do stran
            .setDuration(220)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                facingRight = toX > fromX
                applyFacing(facingRight)

                // Vodní stopa za pohybujícím se krunýřem
                val trailRunnable = object : Runnable {
                    override fun run() {
                        if (!running) return
                        val trail = View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.parseColor("#81D4FA"))
                            }
                            val sz = (Random.nextInt(5, 10) * dp).toInt()
                            layoutParams = ViewGroup.LayoutParams(sz, sz)
                            x = pokemonView.x + pokemonView.width / 2f
                            y = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.5f
                            alpha = 0.7f
                        }
                        parent.addView(trail)
                        trail.animate()
                            .translationYBy(-15f * dp)
                            .alpha(0f).scaleX(1.5f).scaleY(1.5f)
                            .setDuration(400)
                            .withEndAction { parent.removeView(trail) }
                            .start()
                        handler.postDelayed(this, 60L)
                    }
                }
                handler.post(trailRunnable)

                // 2. Rychlý pohyb jako střela
                pokemonView.animate()
                    .x(toX)
                    .setDuration(350)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        handler.removeCallbacks(trailRunnable)

                        // 3. Vynoření z krunýře s vodní tříští
                        spawnWaterDrops(count = 6, fromTail = false)

                        pokemonView.animate()
                            .scaleY(baseScale)
                            .scaleX(baseScale * if (facingRight) -1f else 1f)
                            .setDuration(280)
                            .setInterpolator(OvershootInterpolator(1.3f))
                            .withEndAction {
                                if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
                            }.start()
                    }.start()
            }.start()
    }

    private fun squirtleTapReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        // Water Gun — vystřelí vodní paprsek
        val cx = pokemonView.x + pokemonView.width * if (facingRight) 0.2f else 0.8f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.4f

        repeat(8) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val jet = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#29B6F6"))
                    }
                    val w = (Random.nextInt(6, 12) * dp).toInt()
                    val h = (Random.nextInt(4, 7) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(w, h)
                    x = cx; y = cy; alpha = 0.9f
                }
                parent.addView(jet)
                val dirX = if (facingRight) -180f * dp else 180f * dp
                jet.animate()
                    .translationXBy(dirX)
                    .translationYBy((Random.nextFloat() - 0.3f) * 20f * dp)
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction { parent.removeView(jet) }
                    .start()
            }, i * 50L)
        }

        // Squirtle se trochu nakloní při střelbě
        pokemonView.animate()
            .rotation(if (facingRight) 15f else -15f).setDuration(150)
            .withEndAction {
                pokemonView.animate().rotation(0f).setDuration(200).start()
            }.start()
    }

    /**
     * Wartortle idle — těžší než Squirtle, vznáší se s důstojností.
     * Má velké uši a chlupatý ocas — občas zamává ocasem.
     */
    private fun startWartortleIdle(): Animator {
        val float = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 4f * dp, targetTranslationY + 4f * dp).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleWartortleTailWag()
        float.start()
        return float
    }

    private fun scheduleWartortleTailWag() {
        if (!running || pokemonId != "008") return
        handler.postDelayed({
            if (running && pokemonId == "008") {
                // Zamávání ocasem — jemné otočení
                ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, -8f, 8f, -5f, 0f).apply {
                    duration = 800; start()
                }
                spawnWaterDrops(count = 3, fromTail = true)
                scheduleWartortleTailWag()
            }
        }, Random.nextLong(4500, 8000))
    }

    /**
     * Wartortle přechod — rychlý spin s vodním vírem.
     * Rychlejší než Squirtle, sebevědomější.
     */
    private fun crossingWartortle(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()

        // 1. Spin do krunýře s vodním vírem
        val spinDisappear = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, 360f).apply { duration = 300 },
                ObjectAnimator.ofFloat(pokemonView, "scaleX", baseScale, 0.1f).apply { duration = 300 },
                ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, 0.1f).apply { duration = 300 }
            )
        }

        // Vodní vír při zmizení
        repeat(6) { i ->
            val angle = i * 60f
            val vortex = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#0288D1"))
                }
                val sz = (8 * dp).toInt()
                layoutParams = ViewGroup.LayoutParams(sz, sz)
                x = pokemonView.x + pokemonView.width / 2f
                y = pokemonView.y + pokemonView.translationY + pokemonView.height / 2f
                alpha = 0.8f
            }
            parent.addView(vortex)
            vortex.animate()
                .translationXBy(40f * dp * cos(Math.toRadians(angle.toDouble())).toFloat())
                .translationYBy(40f * dp * sin(Math.toRadians(angle.toDouble())).toFloat())
                .alpha(0f).setDuration(400)
                .withEndAction { parent.removeView(vortex) }
                .start()
        }

        spinDisappear.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                if (!running) return
                pokemonView.x = toX
                pokemonView.rotation = 0f
                facingRight = toX > fromX
                applyFacing(facingRight)

                // 2. Vynoření — spin z druhé strany
                pokemonView.animate()
                    .scaleX(baseScale).scaleY(baseScale)
                    .setDuration(320)
                    .setInterpolator(OvershootInterpolator(1.4f))
                    .withEndAction {
                        spawnWaterDrops(count = 5, fromTail = false)
                        if (running) { startWobble(); scheduleStep(Random.nextLong(1500, 3000)) }
                    }.start()
            }
        })
        spinDisappear.start()
    }

    private fun wartortleTapReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()
        wobbleAnim?.cancel()

        // Rapid Spin — rychlé otočení s vodním kruhem
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height / 2f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, 720f).apply {
                    duration = 600; interpolator = AccelerateDecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(pokemonView, "scaleX", baseScale, baseScale * 1.2f, baseScale).apply { duration = 600 },
                ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.2f, baseScale).apply { duration = 600 }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(a: Animator) {
                    // Vodní kruh při spinu
                    repeat(8) { i ->
                        val angle = i * 45f
                        val ring = View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.parseColor("#4FC3F7"))
                            }
                            val sz = (7 * dp).toInt()
                            layoutParams = ViewGroup.LayoutParams(sz, sz)
                            x = cx; y = cy; alpha = 0.85f
                        }
                        parent.addView(ring)
                        ring.animate()
                            .translationXBy(55f * dp * cos(Math.toRadians(angle.toDouble())).toFloat())
                            .translationYBy(55f * dp * sin(Math.toRadians(angle.toDouble())).toFloat())
                            .alpha(0f).scaleX(0.3f).scaleY(0.3f)
                            .setDuration(600)
                            .withEndAction { parent.removeView(ring) }
                            .start()
                    }
                }
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.rotation = 0f
                    if (running) { startWobble(); startIdleAnimation(); scheduleStep(Random.nextLong(2000, 4000)) }
                }
            })
            start()
        }
    }

    /**
     * Blastoise idle — těžký, pomalý, dominantní.
     * Vodní děla na krunýři občas vystřelí vodní paprsek.
     */
    private fun startBlastoiseIdle(): Animator {
        // Velmi pomalé dýchání — Blastoise je nejtěžší
        val breathe = ObjectAnimator.ofPropertyValuesHolder(
            pokemonView,
            PropertyValuesHolder.ofFloat("scaleX", -baseScale, -baseScale * 1.02f, -baseScale),
            PropertyValuesHolder.ofFloat("scaleY", baseScale, baseScale * 1.03f, baseScale)
        ).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleBlastoiseCannonFire()
        breathe.start()
        return breathe
    }

    private fun scheduleBlastoiseCannonFire() {
        if (!running || pokemonId != "009") return
        handler.postDelayed({
            if (running && pokemonId == "009") {
                spawnCannonBlast()
                scheduleBlastoiseCannonFire()
            }
        }, Random.nextLong(5000, 9000))
    }

    /**
     * Hydro Pump z vodních děl — dva paprsky letí diagonálně nahoru.
     */
    private fun spawnCannonBlast() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        // Levé dělo
        val lx = pokemonView.x + pokemonView.width * 0.3f
        // Pravé dělo
        val rx = pokemonView.x + pokemonView.width * 0.7f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.25f

        listOf(lx, rx).forEachIndexed { idx, cx ->
            repeat(6) { i ->
                handler.postDelayed({
                    if (!running) return@postDelayed
                    val jet = View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#0277BD"))
                        }
                        val sz = (Random.nextInt(5, 10) * dp).toInt()
                        layoutParams = ViewGroup.LayoutParams(sz, sz)
                        x = cx; y = cy; alpha = 0.9f
                    }
                    parent.addView(jet)
                    val dirX = if (idx == 0) -30f * dp else 30f * dp
                    jet.animate()
                        .translationXBy(dirX + (Random.nextFloat() - 0.5f) * 15f * dp)
                        .translationYBy(-70f * dp - Random.nextFloat() * 30f * dp)
                        .alpha(0f)
                        .setDuration(700)
                        .withEndAction { parent.removeView(jet) }
                        .start()
                }, i * 80L)
            }
        }
    }

    /**
     * Blastoise přechod — nabije vodní děla (záblesk modrých částic),
     * vystřelí Hydro Pump paprsek a teleportuje se v proudu vody.
     * Nejpomalejší a nejtěžší z linie.
     */
    private fun crossingBlastoise(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()

        val movingRight = toX > fromX

        // 1. Nabíjení děl — modré částice se soustředí kolem Pokémona
        repeat(8) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val charge = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#0288D1"))
                    }
                    val sz = (Random.nextInt(6, 14) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    val angle = i * 45f
                    x = pokemonView.x + pokemonView.width / 2f + 50f * dp * cos(Math.toRadians(angle.toDouble())).toFloat()
                    y = pokemonView.y + pokemonView.translationY + pokemonView.height / 2f + 50f * dp * sin(Math.toRadians(angle.toDouble())).toFloat()
                    alpha = 0.8f
                }
                parent.addView(charge)
                // Částice letí DO středu (nabíjení)
                charge.animate()
                    .x(pokemonView.x + pokemonView.width / 2f)
                    .y(pokemonView.y + pokemonView.translationY + pokemonView.height / 2f)
                    .alpha(0f).scaleX(0.1f).scaleY(0.1f)
                    .setDuration(400)
                    .withEndAction { parent.removeView(charge) }
                    .start()
            }, i * 50L)
        }

        // 2. Po nabití — Hydro Pump paprsek a teleport
        handler.postDelayed({
            if (!running) return@postDelayed

            // Hydro Pump — silnější a širší než Solar Beam
            val beamH = (10 * dp).toInt()
            val beamW = parent.width
            val beamStartX = if (movingRight) pokemonView.x + pokemonView.width / 2f else 0f

            val beam = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    colors = if (movingRight)
                        intArrayOf(Color.parseColor("#E3F2FD"), Color.parseColor("#0288D1"), Color.TRANSPARENT)
                    else
                        intArrayOf(Color.TRANSPARENT, Color.parseColor("#0288D1"), Color.parseColor("#E3F2FD"))
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                    cornerRadius = 5f * dp
                }
                layoutParams = ViewGroup.LayoutParams(beamW, beamH)
                x = beamStartX
                y = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.3f
                scaleX = 0f; alpha = 0.9f
                pivotX = if (movingRight) 0f else beamW.toFloat()
            }
            parent.addView(beam)

            beam.animate().scaleX(1f).setDuration(200).withEndAction {
                // 3. Blastoise mizí v proudu vody
                pokemonView.animate().alpha(0f).scaleX(0.1f).scaleY(0.3f).setDuration(180)
                    .withEndAction {
                        pokemonView.x = toX
                        facingRight = movingRight
                        applyFacing(facingRight)

                        beam.animate().alpha(0f).setDuration(350)
                            .withEndAction { parent.removeView(beam) }.start()

                        // 4. Blastoise se složí na cíli s velkým vodním výbuchem
                        pokemonView.animate().alpha(1f).scaleX(baseScale).scaleY(baseScale)
                            .setDuration(450).setInterpolator(OvershootInterpolator(1.0f))
                            .withEndAction {
                                // Těžký dopad — silnější otřes
                                val grandParent = parent.parent as? ViewGroup
                                grandParent?.let {
                                    ObjectAnimator.ofFloat(it, "translationY",
                                        0f, 12f * dp, -8f * dp, 5f * dp, 0f
                                    ).apply { duration = 450; start() }
                                }
                                // Vodní výbuch po dopadu
                                spawnWaterDrops(count = 10, fromTail = false)
                                spawnCannonBlast()
                                if (running) { startWobble(); scheduleStep(Random.nextLong(2000, 4500)) }
                            }.start()
                    }.start()
            }.start()
        }, 450L)
    }

    private fun blastoiseTapReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()
        wobbleAnim?.cancel()

        val grandParent = parent.parent as? ViewGroup

        // Earthquake + Hydro Pump combo
        // 1. Přískoč
        pokemonView.animate()
            .translationY(targetTranslationY - 25f * dp).setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // 2. Dopad s obrovským otřesem
                pokemonView.animate()
                    .translationY(targetTranslationY).setDuration(220)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        grandParent?.let {
                            ObjectAnimator.ofFloat(it, "translationX",
                                0f, -22f * dp, 22f * dp, -14f * dp, 14f * dp, -7f * dp, 7f * dp, 0f
                            ).apply { duration = 550; start() }
                            ObjectAnimator.ofFloat(it, "translationY",
                                0f, 14f * dp, -10f * dp, 6f * dp, 0f
                            ).apply { duration = 550; start() }
                        }

                        // Rázové vlny
                        repeat(3) { i ->
                            handler.postDelayed({
                                if (!running) return@postDelayed
                                spawnStompWave(parent,
                                    pokemonView.x + pokemonView.width / 2f,
                                    pokemonView.y + pokemonView.translationY + pokemonView.height
                                )
                            }, i * 100L)
                        }

                        // Hydro Pump ze všech děl po dopadu
                        handler.postDelayed({
                            if (running) spawnCannonBlast()
                        }, 300L)

                        if (running) {
                            handler.postDelayed({
                                startWobble()
                                startIdleAnimation()
                                scheduleStep(Random.nextLong(2500, 5000))
                            }, 700L)
                        }
                    }.start()
            }.start()
    }

    // ─────────────────────────────────────────────
// CATERPIE LINE
// ─────────────────────────────────────────────

    /**
     * Caterpie idle — jemné vlnění těla jako housenka.
     * Občas rychle poskočí jako při žvýkání listu.
     */
    private fun startCaterpieIdle(): Animator {
        val wiggle = ObjectAnimator.ofFloat(pokemonView, "rotation", -5f, 5f).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleCaterpieLeafChew()
        wiggle.start()
        return wiggle
    }

    private fun scheduleCaterpieLeafChew() {
        if (!running || pokemonId != "010") return
        handler.postDelayed({
            if (running && pokemonId == "010") {
                // Rychlé poskakování jako žvýkání
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(pokemonView, "scaleY",
                            baseScale, baseScale * 1.12f, baseScale * 0.9f, baseScale
                        ).apply { duration = 350 },
                        ObjectAnimator.ofFloat(pokemonView, "scaleX",
                            pokemonView.scaleX, pokemonView.scaleX * 0.9f, pokemonView.scaleX
                        ).apply { duration = 350 }
                    )
                    start()
                }
                scheduleCaterpieLeafChew()
            }
        }, Random.nextLong(3000, 6000))
    }

    /**
     * Caterpie přechod — plazí se pomalu, zanechává hedvábnou nit.
     */
    private fun crossingCaterpie(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()

        facingRight = toX > fromX
        applyFacing(facingRight)

        // Hedvábná nit za Caterpiem
        val threadRunnable = object : Runnable {
            override fun run() {
                if (!running) return
                val thread = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.parseColor("#F5F5F5"))
                        cornerRadius = 1f * dp
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        (Random.nextInt(6, 14) * dp).toInt(),
                        (2 * dp).toInt()
                    )
                    x = pokemonView.x + pokemonView.width / 2f
                    y = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.6f
                    alpha = 0.7f
                }
                parent.addView(thread)
                thread.animate()
                    .translationYBy(8f * dp)
                    .alpha(0f)
                    .setDuration(600)
                    .withEndAction { parent.removeView(thread) }
                    .start()
                handler.postDelayed(this, 80L)
            }
        }
        handler.post(threadRunnable)

        val dist = abs(toX - fromX)
        val dur = (dist * 20f).toLong().coerceIn(1500, 5000) // Caterpie je pomalý

        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            duration = dur
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    handler.removeCallbacks(threadRunnable)
                    if (running) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(1000, 3000))
                    }
                }
            })
            start()
        }
    }

    private fun caterpieClickReaction() {
        // String Shot — bílé nitě vystřelí dopředu
        val parent = pokemonView.parent as? ViewGroup ?: return
        val startX = pokemonView.x + if (facingRight) 0f else pokemonView.width.toFloat()
        val startY = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.4f

        // Caterpie se scvrkne a nafoukne
        pokemonView.animate()
            .scaleY(baseScale * 1.25f)
            .scaleX(pokemonView.scaleX * 0.8f)
            .setDuration(100)
            .withEndAction {
                pokemonView.animate()
                    .scaleY(baseScale)
                    .scaleX(if (facingRight) -baseScale else baseScale)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }.start()

        repeat(6) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val thread = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.parseColor("#ECEFF1"))
                        cornerRadius = 2f * dp
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        (Random.nextInt(10, 25) * dp).toInt(),
                        (3 * dp).toInt()
                    )
                    x = startX
                    y = startY + (Random.nextFloat() - 0.5f) * 12f * dp
                    alpha = 0.9f
                }
                parent.addView(thread)
                val dirX = if (facingRight) -100f * dp else 100f * dp
                thread.animate()
                    .translationXBy(dirX)
                    .translationYBy((Random.nextFloat() - 0.5f) * 10f * dp)
                    .alpha(0f)
                    .setDuration(450)
                    .withEndAction { parent.removeView(thread) }
                    .start()
            }, i * 55L)
        }
    }

// ─────────────────────────────────────────────

    /**
     * Metapod idle — téměř stojí, jen velmi jemně pulzuje.
     * Občas se rozsvítí metalickým leskem (Harden).
     */
    private fun startMetapodIdle(): Animator {
        // Velmi jemné pulzování — Metapod se sotva hýbe
        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            pokemonView,
            PropertyValuesHolder.ofFloat("scaleX", pokemonView.scaleX, pokemonView.scaleX * 1.04f, pokemonView.scaleX),
            PropertyValuesHolder.ofFloat("scaleY", baseScale, baseScale * 1.04f, baseScale)
        ).apply {
            duration = 2500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleMetapodHarden()
        pulse.start()
        return pulse
    }

    private fun scheduleMetapodHarden() {
        if (!running || pokemonId != "011") return
        handler.postDelayed({
            if (running && pokemonId == "011") {
                spawnMetapodHardenEffect()
                scheduleMetapodHarden()
            }
        }, Random.nextLong(5000, 9000))
    }

    private fun spawnMetapodHardenEffect() {
        val parent = pokemonView.parent as? ViewGroup ?: return

        // Pulse + metalický lesk přes sprite
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "scaleX",
                    pokemonView.scaleX, pokemonView.scaleX * 1.15f, pokemonView.scaleX
                ),
                ObjectAnimator.ofFloat(pokemonView, "scaleY",
                    baseScale, baseScale * 1.15f, baseScale
                )
            )
            duration = 700
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        val shine = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                colors = intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(200, 255, 255, 255),
                    Color.TRANSPARENT
                )
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
            }
            layoutParams = ViewGroup.LayoutParams(
                (22 * dp).toInt(),
                pokemonView.height
            )
            rotation = 30f
            x = pokemonView.x - pokemonView.width
            y = pokemonView.y + pokemonView.translationY
            alpha = 0f
        }
        parent.addView(shine)

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(shine, "alpha", 0f, 1f, 0f),
                ObjectAnimator.ofFloat(shine, "x",
                    pokemonView.x - pokemonView.width,
                    pokemonView.x + pokemonView.width * 1.5f
                )
            )
            duration = 700
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { parent.removeView(shine) }
            })
            start()
        }
    }

    /**
     * Metapod přechod — padá na zem, odskočí a přistane na druhé straně.
     * Těžký krunýř, pomalý pohyb.
     */
    private fun crossingMetapod(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()
        idleAnim?.cancel()

        facingRight = toX > fromX
        applyFacing(facingRight)

        // 1. Padnutí a odraz
        pokemonView.animate()
            .translationY(targetTranslationY + 10f * dp)
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                // Otřes při dopadu
                val grandParent = parent.parent as? ViewGroup
                grandParent?.let {
                    ObjectAnimator.ofFloat(it, "translationX", 0f, -5f * dp, 5f * dp, 0f).apply {
                        duration = 200; start()
                    }
                }

                // 2. Odskočení — Metapod se jako krunýř kutálí
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
                            duration = 1800
                            interpolator = AccelerateDecelerateInterpolator()
                        },
                        ObjectAnimator.ofFloat(pokemonView, "translationY",
                            targetTranslationY + 10f * dp,
                            targetTranslationY - 35f * dp,
                            targetTranslationY
                        ).apply {
                            duration = 1800
                            interpolator = AccelerateDecelerateInterpolator()
                        },
                        // Kutálení — rotace při letu
                        ObjectAnimator.ofFloat(pokemonView, "rotation",
                            0f,
                            if (facingRight) 180f else -180f,
                            if (facingRight) 360f else -360f
                        ).apply {
                            duration = 1800
                            interpolator = LinearInterpolator()
                        }
                    )
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: Animator) {
                            pokemonView.rotation = 0f
                            // Dopad s otřesem
                            grandParent?.let {
                                ObjectAnimator.ofFloat(it, "translationY",
                                    0f, 8f * dp, -4f * dp, 0f
                                ).apply { duration = 300; start() }
                            }
                            spawnMetapodHardenEffect() // Zasvítí při dopadu
                            if (running) {
                                startIdleAnimation()
                                scheduleStep(Random.nextLong(2000, 5000))
                            }
                        }
                    })
                    start()
                }
            }.start()
    }

    private fun metapodClickReaction() {
        spawnMetapodHardenEffect()
    }

// ─────────────────────────────────────────────

    /**
     * Butterfree idle — plynulé létání nahoru/dolů.
     * Nepřetržitě sype fialové spóry.
     */
    private fun startButterfreeIdle(): Animator {
        // Plynulé létání
        val fly = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 14f * dp, targetTranslationY + 8f * dp).apply {
            duration = 1300
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleButterfreeSpores()
        fly.start()
        return fly
    }

    private fun scheduleButterfreeSpores() {
        if (!running || pokemonId != "012") return
        handler.postDelayed({
            if (running && pokemonId == "012") {
                spawnButterfreeSpore()
                scheduleButterfreeSpores()
            }
        }, 180 + Random.nextLong(120))
    }

    private fun spawnButterfreeSpore() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val size = (5 * dp).toInt()

        val spore = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    if (Random.nextBoolean())
                        Color.parseColor("#E090F0")
                    else
                        Color.parseColor("#C878D0")
                )
            }
            layoutParams = ViewGroup.LayoutParams(size, size)
            x = pokemonView.x + pokemonView.width / 2f + (Random.nextFloat() - 0.5f) * pokemonView.width
            y = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.85f
            alpha = 0.85f
        }
        parent.addView(spore)

        spore.animate()
            .translationYBy(55f * dp)
            .translationXBy((Random.nextFloat() - 0.5f) * 22f * dp)
            .alpha(0f)
            .setDuration(1300)
            .withEndAction { parent.removeView(spore) }
            .start()
    }

    /**
     * Butterfree přechod — vzlétne výš, přeletí obloukem přes střed
     * a sestoupí na druhé straně. Elegantní motýlí oblouk.
     */
    private fun crossingButterfree(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        stopWobble()

        facingRight = toX > fromX
        applyFacing(facingRight)

        val dist = abs(toX - fromX)
        val dur = (dist * 5f).toLong().coerceIn(700, 2200)

        // Butterfree přeletí obloukem — ValueAnimator pro sinusový oblouk
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (toX - fromX) * p
                // Sinusový oblouk nahoru a pak dolů
                val arc = sin(p * PI.toFloat()) * 45f * dp
                pokemonView.translationY = targetTranslationY - arc
                // Naklopení ve směru pohybu
                pokemonView.rotation = sin(p * PI.toFloat() * 2f) * 12f * (if (facingRight) -1f else 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    pokemonView.rotation = 0f
                    pokemonView.translationY = targetTranslationY
                    if (running) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(800, 2500))
                    }
                }
            })
            start()
        }
    }

    private fun butterfreeClickReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return

        // Sleep Powder — velký kruh fialových spór
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height / 2f

        // Butterfree se nafukne
        pokemonView.animate()
            .scaleX(pokemonView.scaleX * 1.2f)
            .scaleY(baseScale * 1.2f)
            .setDuration(120)
            .withEndAction {
                pokemonView.animate()
                    .scaleX(if (facingRight) -baseScale else baseScale)
                    .scaleY(baseScale)
                    .setDuration(200)
                    .start()
            }.start()

        repeat(12) { i ->
            val angle = i * 30f
            handler.postDelayed({
                if (!running) return@postDelayed
                val spore = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(
                            when (i % 3) {
                                0 -> Color.parseColor("#CE93D8")
                                1 -> Color.parseColor("#9C27B0")
                                else -> Color.parseColor("#F3E5F5")
                            }
                        )
                    }
                    val sz = (Random.nextInt(6, 12) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx; y = cy; alpha = 0.9f
                }
                parent.addView(spore)
                spore.animate()
                    .translationXBy(70f * dp * cos(Math.toRadians(angle.toDouble())).toFloat())
                    .translationYBy(70f * dp * sin(Math.toRadians(angle.toDouble())).toFloat())
                    .alpha(0f).scaleX(2f).scaleY(2f)
                    .setDuration(900)
                    .withEndAction { parent.removeView(spore) }
                    .start()
            }, i * 30L)
        }
    }

    // ─────────────────────────────────────────────
// WEEDLE LINE
// ─────────────────────────────────────────────

    /**
     * Weedle idle — malá jedovatá housenka.
     * Bodá jehlou nahoru (varuje), občas vypustí fialový jedový obláček.
     */
    private fun startWeedleIdle(): Animator {
        // Tělo se vlní jako housenka — rychlejší než Caterpie (je agresivnější)
        val wiggle = ObjectAnimator.ofFloat(pokemonView, "rotation", -6f, 6f).apply {
            duration = 400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleWeedlePoison()
        wiggle.start()
        return wiggle
    }

    private fun scheduleWeedlePoison() {
        if (!running || pokemonId != "013") return
        handler.postDelayed({
            if (running && pokemonId == "013") {
                spawnWeedlePoisonCloud()
                scheduleWeedlePoison()
            }
        }, Random.nextLong(4000, 7000))
    }

    /**
     * Poison Sting — fialový jedový obláček stoupá z jehly.
     */
    private fun spawnWeedlePoisonCloud() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        // Jehla je přibližně na horním středu spritu
        val cx = pokemonView.x + pokemonView.width * 0.5f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.05f

        repeat(5) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val cloud = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(
                            when (i % 3) {
                                0 -> Color.parseColor("#7B1FA2")
                                1 -> Color.parseColor("#AB47BC")
                                else -> Color.parseColor("#CE93D8")
                            }
                        )
                    }
                    val sz = (Random.nextInt(5, 10) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx + (Random.nextFloat() - 0.5f) * 12f * dp
                    y = cy
                    alpha = 0.8f
                }
                parent.addView(cloud)
                cloud.animate()
                    .translationYBy(-40f * dp - Random.nextFloat() * 20f * dp)
                    .translationXBy((Random.nextFloat() - 0.5f) * 18f * dp)
                    .scaleX(2.5f).scaleY(2.5f)
                    .alpha(0f)
                    .setDuration(1000)
                    .withEndAction { parent.removeView(cloud) }
                    .start()
            }, i * 120L)
        }
    }

    /**
     * Weedle přechod — rychlé plazení s jedovou stopou.
     * Rychlejší než Caterpie — Weedle je agresivnější.
     */
    private fun crossingWeedle(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()
        idleAnim?.cancel()

        facingRight = toX > fromX
        applyFacing(facingRight)

        // Jedová stopa za Weedlem — fialové tečky
        val poisonTrailRunnable = object : Runnable {
            override fun run() {
                if (!running) return
                val dot = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#9C27B0"))
                    }
                    val sz = (Random.nextInt(3, 6) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = pokemonView.x + pokemonView.width / 2f + (Random.nextFloat() - 0.5f) * 8f * dp
                    y = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.8f
                    alpha = 0.7f
                }
                parent.addView(dot)
                dot.animate()
                    .translationYBy(10f * dp)
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction { parent.removeView(dot) }
                    .start()
                handler.postDelayed(this, 90L)
            }
        }
        handler.post(poisonTrailRunnable)

        val dist = abs(toX - fromX)
        val dur = (dist * 14f).toLong().coerceIn(900, 3500)

        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            duration = dur
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    handler.removeCallbacks(poisonTrailRunnable)
                    if (running) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(800, 2500))
                    }
                }
            })
            start()
        }
    }

    private fun weedleClickReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return

        // Poison Sting — bodnutí jehlou, jedový výbuch nahoru
        // Weedle se vzpřímí a bodne
        pokemonView.animate()
            .scaleY(baseScale * 1.3f)
            .scaleX(pokemonView.scaleX * 0.8f)
            .translationY(targetTranslationY - 8f * dp)
            .setDuration(120)
            .withEndAction {
                pokemonView.animate()
                    .scaleY(baseScale)
                    .scaleX(if (facingRight) -baseScale else baseScale)
                    .translationY(targetTranslationY)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }.start()

        // Jedový výbuch z jehly
        val cx = pokemonView.x + pokemonView.width * 0.5f
        val cy = pokemonView.y + pokemonView.translationY

        repeat(8) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val poison = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (i % 2 == 0) Color.parseColor("#7B1FA2") else Color.parseColor("#E040FB"))
                    }
                    val sz = (Random.nextInt(5, 11) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx; y = cy; alpha = 0.9f
                }
                parent.addView(poison)
                val angle = (i * 45f) - 90f // Hlavně nahoru
                val dist = (30f + Random.nextFloat() * 30f) * dp
                poison.animate()
                    .translationXBy(dist * cos(Math.toRadians(angle.toDouble())).toFloat())
                    .translationYBy(dist * sin(Math.toRadians(angle.toDouble())).toFloat())
                    .alpha(0f).scaleX(1.5f).scaleY(1.5f)
                    .setDuration(600)
                    .withEndAction { parent.removeView(poison) }
                    .start()
            }, i * 40L)
        }
    }

// ─────────────────────────────────────────────

    /**
     * Kakuna idle — jedovatý kokón, téměř se nehýbe.
     * Na rozdíl od Metapoda je tmavší a jedovatý — občas
     * vyzáří fialovým jedovým pulzem.
     */
    private fun startKakunaIdle(): Animator {
        // Sotva znatelné pulzování — Kakuna čeká na evoluci
        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            pokemonView,
            PropertyValuesHolder.ofFloat("scaleX",
                pokemonView.scaleX,
                pokemonView.scaleX * 1.03f,
                pokemonView.scaleX
            ),
            PropertyValuesHolder.ofFloat("scaleY", baseScale, baseScale * 1.03f, baseScale)
        ).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleKakunaPoisonPulse()
        pulse.start()
        return pulse
    }

    private fun scheduleKakunaPoisonPulse() {
        if (!running || pokemonId != "014") return
        handler.postDelayed({
            if (running && pokemonId == "014") {
                spawnKakunaPoisonPulse()
                scheduleKakunaPoisonPulse()
            }
        }, Random.nextLong(6000, 10000))
    }

    /**
     * Jedový pulz — fialová rázová vlna se rozletí z Kakuny.
     * Varuje: "jsem jedovatý, nesahat."
     */
    private fun spawnKakunaPoisonPulse() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height / 2f

        // Nafukne se a vypustí rázovou vlnu
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "scaleX",
                    pokemonView.scaleX,
                    pokemonView.scaleX * 1.2f,
                    pokemonView.scaleX
                ),
                ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.2f, baseScale)
            )
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // Tři soustředné fialové kruhy
        repeat(3) { ring ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val sz = (25 * dp).toInt()
                val wave = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.TRANSPARENT)
                        setStroke(
                            (2 * dp).toInt(),
                            when (ring) {
                                0 -> Color.parseColor("#7B1FA2")
                                1 -> Color.parseColor("#AB47BC")
                                else -> Color.parseColor("#CE93D8")
                            }
                        )
                    }
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx - sz / 2f
                    y = cy - sz / 2f
                    alpha = 0.9f
                }
                parent.addView(wave)
                wave.animate()
                    .scaleX(3.5f).scaleY(3.5f)
                    .alpha(0f)
                    .setDuration(700)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { parent.removeView(wave) }
                    .start()
            }, ring * 150L)
        }
    }

    /**
     * Kakuna přechod — padá, odskočí jako Metapod ale s jedovým efektem.
     * Při dopadu nechá jedovou skvrnu.
     */
    private fun crossingKakuna(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()
        idleAnim?.cancel()

        facingRight = toX > fromX
        applyFacing(facingRight)

        val grandParent = parent.parent as? ViewGroup

        // 1. Jedový záblesk před skokem
        spawnKakunaPoisonPulse()

        handler.postDelayed({
            if (!running) return@postDelayed

            // 2. Skok s rotací — těžší a pomalejší než Metapod
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
                        duration = 2200
                        interpolator = AccelerateDecelerateInterpolator()
                    },
                    ObjectAnimator.ofFloat(pokemonView, "translationY",
                        targetTranslationY,
                        targetTranslationY - 40f * dp,
                        targetTranslationY
                    ).apply {
                        duration = 2200
                        interpolator = AccelerateDecelerateInterpolator()
                    },
                    ObjectAnimator.ofFloat(pokemonView, "rotation",
                        0f,
                        if (facingRight) 270f else -270f,
                        if (facingRight) 360f else -360f
                    ).apply {
                        duration = 2200
                        interpolator = LinearInterpolator()
                    }
                )
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) {
                        pokemonView.rotation = 0f

                        // Dopad — otřes a jedová skvrna
                        grandParent?.let {
                            ObjectAnimator.ofFloat(it, "translationY",
                                0f, 10f * dp, -6f * dp, 0f
                            ).apply { duration = 350; start() }
                        }

                        // Jedová skvrna na zemi
                        spawnPoisonSplat(parent,
                            pokemonView.x + pokemonView.width / 2f,
                            pokemonView.y + pokemonView.translationY + pokemonView.height
                        )

                        if (running) {
                            startIdleAnimation()
                            scheduleStep(Random.nextLong(2500, 5500))
                        }
                    }
                })
                start()
            }
        }, 400L)
    }

    /**
     * Jedová skvrna na zemi po dopadu Kakuny.
     */
    private fun spawnPoisonSplat(parent: ViewGroup, x: Float, y: Float) {
        repeat(5) { i ->
            val splat = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#6A1B9A"))
                }
                val sz = (Random.nextInt(4, 9) * dp).toInt()
                layoutParams = ViewGroup.LayoutParams(sz, sz)
                this.x = x + (Random.nextFloat() - 0.5f) * 30f * dp
                this.y = y
                alpha = 0.8f
            }
            parent.addView(splat)
            splat.animate()
                .translationXBy((Random.nextFloat() - 0.5f) * 40f * dp)
                .translationYBy(-20f * dp - Random.nextFloat() * 15f * dp)
                .scaleX(0.2f).scaleY(0.2f)
                .alpha(0f)
                .setDuration(400)
                .withEndAction { parent.removeView(splat) }
                .start()
        }
    }

    private fun kakunaClickReaction() {
        // Harden + Poison — silnější verze než Metapod
        spawnKakunaPoisonPulse()

        // Navíc se celý Kakuna rozsvítí fialově
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height / 2f

        repeat(6) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val glow = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#9C27B0"))
                    }
                    val sz = (Random.nextInt(8, 16) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width
                    y = cy + (Random.nextFloat() - 0.5f) * pokemonView.height * 0.6f
                    alpha = 0f
                }
                parent.addView(glow)
                glow.animate()
                    .alpha(0.8f).setDuration(100)
                    .withEndAction {
                        glow.animate()
                            .alpha(0f).scaleX(2f).scaleY(2f)
                            .translationYBy(-15f * dp)
                            .setDuration(400)
                            .withEndAction { parent.removeView(glow) }
                            .start()
                    }.start()
            }, i * 70L)
        }
    }

// ─────────────────────────────────────────────

    /**
     * Beedrill idle — agresivní vosa, nepřetržitě vibruje křídly.
     * Pohybuje se rychle a nervózně, žihadla neustále hrozí.
     */
    private fun startBeedrillIdle(): Animator {
        // Vibrace křídel — rychlé třepotání (jako skutečná vosa)
        val wingBuzz = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 3f * dp, targetTranslationY + 3f * dp).apply {
            duration = 80 // Velmi rychlé — bzučení
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
        }
        scheduleBeedrillAggression()
        wingBuzz.start()
        return wingBuzz
    }

    private fun scheduleBeedrillAggression() {
        if (!running || pokemonId != "015") return
        handler.postDelayed({
            if (running && pokemonId == "015") {
                spawnBeedrillStinger()
                scheduleBeedrillAggression()
            }
        }, Random.nextLong(4000, 7000))
    }

    /**
     * Twineedle — Beedrill udělá rychlý výpad žihadlem.
     * Výrazné žluté/černé proužky letí ve směru pohledu.
     */
    private fun spawnBeedrillStinger() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width * if (facingRight) 0.2f else 0.8f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.4f

        // Rychlý výpad — Beedrill se nakloní dopředu
        pokemonView.animate()
            .translationXBy(if (facingRight) -12f * dp else 12f * dp)
            .setDuration(80)
            .withEndAction {
                pokemonView.animate()
                    .translationXBy(if (facingRight) 12f * dp else -12f * dp)
                    .setDuration(120)
                    .start()
            }.start()

        // Žihadlové paprsky
        repeat(3) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val stinger = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(if (i % 2 == 0) Color.parseColor("#F9A825") else Color.parseColor("#212121"))
                        cornerRadius = 2f * dp
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        (Random.nextInt(12, 22) * dp).toInt(),
                        (3 * dp).toInt()
                    )
                    x = cx; y = cy + (i - 1) * 6f * dp; alpha = 0.95f
                }
                parent.addView(stinger)
                val dirX = if (facingRight) -130f * dp else 130f * dp
                stinger.animate()
                    .translationXBy(dirX)
                    .translationYBy((Random.nextFloat() - 0.5f) * 8f * dp)
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { parent.removeView(stinger) }
                    .start()
            }, i * 60L)
        }
    }

    /**
     * Beedrill přechod — extrémně rychlý dash.
     * Zanechá žlutočernou stopu jako vosa v letu.
     * Nejrychlejší přechod ze všech Pokémonů.
     */
    private fun crossingBeedrill(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()
        idleAnim?.cancel()

        facingRight = toX > fromX
        applyFacing(facingRight)

        // 1. Nabíjení — Beedrill se třese agresivně
        val chargeShake = ObjectAnimator.ofFloat(pokemonView, "translationX",
            0f, -4f * dp, 4f * dp, -4f * dp, 4f * dp, 0f
        ).apply { duration = 200 }

        chargeShake.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                if (!running) return

                // 2. Extrémně rychlý dash — Beedrill je nejrychlejší létající Pokémon
                val dist = abs(toX - fromX)
                val dur = (dist * 2.5f).toLong().coerceIn(200, 600) // Mnohem rychlejší než ostatní

                // Žlutočerná stopa při pohybu
                val trailRunnable = object : Runnable {
                    override fun run() {
                        if (!running) return
                        val trail = View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(
                                    if (Random.nextBoolean())
                                        Color.parseColor("#F9A825")
                                    else
                                        Color.parseColor("#424242")
                                )
                            }
                            val sz = (Random.nextInt(4, 9) * dp).toInt()
                            layoutParams = ViewGroup.LayoutParams(sz, sz)
                            x = pokemonView.x + pokemonView.width / 2f
                            y = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.5f
                            alpha = 0.8f
                        }
                        parent.addView(trail)
                        trail.animate()
                            .translationYBy((Random.nextFloat() - 0.5f) * 20f * dp)
                            .scaleX(2f).scaleY(2f)
                            .alpha(0f)
                            .setDuration(250)
                            .withEndAction { parent.removeView(trail) }
                            .start()
                        handler.postDelayed(this, 30L) // Velmi hustá stopa
                    }
                }
                handler.post(trailRunnable)

                // Beedrill se mírně zplošti při pohybu (aerodynamika)
                pokemonView.animate()
                    .scaleY(baseScale * 0.75f)
                    .setDuration(50)
                    .start()

                moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
                    duration = dur
                    interpolator = AccelerateInterpolator()
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: Animator) {
                            handler.removeCallbacks(trailRunnable)

                            // 3. Zabrzdění s agresivním výpadem žihadlem
                            pokemonView.animate()
                                .scaleY(baseScale)
                                .setDuration(100)
                                .withEndAction {
                                    spawnBeedrillStinger()
                                    if (running) {
                                        startIdleAnimation()
                                        scheduleStep(Random.nextLong(500, 2000))
                                    }
                                }.start()
                        }
                    })
                    start()
                }
            }
        })
        chargeShake.start()
    }

    private fun beedrillClickReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()
        idleAnim?.cancel()

        val grandParent = parent.parent as? ViewGroup

        // Fury Attack — série rychlých výpadů žihadlem, každý s otřesem
        var attackCount = 0
        val totalAttacks = 4

        fun doAttack() {
            if (!running || attackCount >= totalAttacks) {
                // Po sérii — Beedrill se vrátí do idle
                if (running) {
                    startIdleAnimation()
                    scheduleStep(Random.nextLong(1000, 2500))
                }
                return
            }

            // Rychlý výpad dopředu
            pokemonView.animate()
                .translationXBy(if (facingRight) -18f * dp else 18f * dp)
                .scaleY(baseScale * 0.85f)
                .setDuration(60)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    // Žihadlo při výpadu
                    spawnBeedrillStinger()

                    // Lehký otřes lišty
                    grandParent?.let {
                        ObjectAnimator.ofFloat(it, "translationX",
                            0f,
                            if (facingRight) -6f * dp else 6f * dp,
                            0f
                        ).apply { duration = 120; start() }
                    }

                    pokemonView.animate()
                        .translationXBy(if (facingRight) 18f * dp else -18f * dp)
                        .scaleY(baseScale)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction {
                            attackCount++
                            handler.postDelayed({ doAttack() }, 80L)
                        }.start()
                }.start()
        }

        doAttack()
    }

    // ─────────────────────────────────────────────
// PIDGEY LINE
// ─────────────────────────────────────────────

    /**
     * Pidgey idle — malý plachý pták.
     * Jemně poskakuje a občas třepe křídly jako by se chtěl vzlétnout.
     * Létá nízko nad lištou — není si tak jistý jako vyšší evoluce.
     */
    private fun startPidgeyIdle(): Animator {
        // Jemné poskakování — jako pták co přešlapuje
        val hop = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY, targetTranslationY - 5f * dp, targetTranslationY
        ).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        schedulePidgeyWingFlap()
        hop.start()
        return hop
    }

    private fun schedulePidgeyWingFlap() {
        if (!running || pokemonId != "016") return
        handler.postDelayed({
            if (running && pokemonId == "016") {
                spawnPidgeyFeathers(count = 3)
                // Rychlé zakývání — pokus o vzlet
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(pokemonView, "scaleX",
                            pokemonView.scaleX,
                            pokemonView.scaleX * 1.15f,
                            pokemonView.scaleX * 0.9f,
                            pokemonView.scaleX
                        ).apply { duration = 400 },
                        ObjectAnimator.ofFloat(pokemonView, "scaleY",
                            baseScale, baseScale * 0.85f, baseScale * 1.1f, baseScale
                        ).apply { duration = 400 }
                    )
                    start()
                }
                schedulePidgeyWingFlap()
            }
        }, Random.nextLong(3500, 6000))
    }

    /**
     * Pírka letí — sdílené pro celou linii, intensity určuje počet.
     */
    private fun spawnPidgeyFeathers(count: Int) {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width * 0.5f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.5f

        repeat(count) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val feather = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        // Pidgey má hnědošedá pírka
                        setColor(
                            when (i % 3) {
                                0 -> Color.parseColor("#8D6E63")
                                1 -> Color.parseColor("#BCAAA4")
                                else -> Color.parseColor("#D7CCC8")
                            }
                        )
                    }
                    val w = (Random.nextInt(6, 12) * dp).toInt()
                    val h = (Random.nextInt(3, 6) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(w, h)
                    x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width * 0.8f
                    y = cy + (Random.nextFloat() - 0.5f) * pokemonView.height * 0.4f
                    rotation = Random.nextFloat() * 360f
                    alpha = 0.85f
                }
                parent.addView(feather)
                feather.animate()
                    .translationXBy((Random.nextFloat() - 0.5f) * 50f * dp)
                    .translationYBy(30f * dp + Random.nextFloat() * 20f * dp)
                    .rotationBy(Random.nextFloat() * 360f)
                    .alpha(0f)
                    .setDuration(1200)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { parent.removeView(feather) }
                    .start()
            }, i * 100L)
        }
    }

    /**
     * Pidgey přechod — nízký rychlý let s pírky za sebou.
     * Letí blízko lišty, ne majestátně jako Pidgeot.
     */
    private fun crossingPidgey(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        idleAnim?.cancel()
        stopWobble()

        facingRight = toX > fromX
        applyFacing(facingRight)

        val dist = abs(toX - fromX)
        val dur = (dist * 5f).toLong().coerceIn(500, 1800)

        // Pírka stopa při letu
        val featherTrail = object : Runnable {
            override fun run() {
                if (!running) return
                if (Random.nextFloat() > 0.6f) spawnPidgeyFeathers(1)
                handler.postDelayed(this, 150L)
            }
        }
        handler.post(featherTrail)

        // Mírný oblouk — Pidgey nelétá úplně rovně
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (toX - fromX) * p
                // Malý oblouk — Pidgey není tak plynulý jako Pidgeot
                pokemonView.translationY = targetTranslationY - sin(p * PI.toFloat()) * 20f * dp
                // Mírné naklopení
                pokemonView.rotation = sin(p * PI.toFloat() * 2f) * 8f * (if (facingRight) -1f else 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    handler.removeCallbacks(featherTrail)
                    pokemonView.rotation = 0f
                    pokemonView.translationY = targetTranslationY
                    if (running) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(800, 2500))
                    }
                }
            })
            start()
        }
    }

    private fun pidgeyClickReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return

        // Gust — rychlé mávnutí křídlem, vítr rozhazuje pírka
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "scaleX",
                    pokemonView.scaleX, pokemonView.scaleX * 1.3f, pokemonView.scaleX
                ).apply { duration = 300 },
                ObjectAnimator.ofFloat(pokemonView, "scaleY",
                    baseScale, baseScale * 0.7f, baseScale
                ).apply { duration = 300 }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(a: Animator) {
                    // Vítr z křídel — bílé průhledné částice
                    val cx = pokemonView.x + pokemonView.width / 2f
                    val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.3f
                    repeat(8) { i ->
                        handler.postDelayed({
                            if (!running) return@postDelayed
                            val wind = View(context).apply {
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setColor(Color.parseColor("#E0E0E0"))
                                }
                                val w = (Random.nextInt(10, 20) * dp).toInt()
                                val h = (3 * dp).toInt()
                                layoutParams = ViewGroup.LayoutParams(w, h)
                                x = cx; y = cy + (Random.nextFloat() - 0.5f) * 20f * dp
                                alpha = 0.7f
                            }
                            parent.addView(wind)
                            val dirX = if (facingRight) -150f * dp else 150f * dp
                            wind.animate()
                                .translationXBy(dirX)
                                .translationYBy((Random.nextFloat() - 0.5f) * 15f * dp)
                                .alpha(0f)
                                .setDuration(400)
                                .withEndAction { parent.removeView(wind) }
                                .start()
                        }, i * 35L)
                    }
                    spawnPidgeyFeathers(5)
                }
            })
            start()
        }
    }

// ─────────────────────────────────────────────

    /**
     * Pidgeotto idle — agresivnější, létá výše.
     * Pravidelně skenuje území — otáčí hlavu (rotace).
     * Občas vydá výstražný křik (vizuální efekt).
     */
    private fun startPidgeottoIdle(): Animator {
        // Plynulejší létání než Pidgey
        val fly = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 6f * dp, targetTranslationY + 4f * dp
        ).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        schedulePidgeottoScan()
        fly.start()
        return fly
    }

    private fun schedulePidgeottoScan() {
        if (!running || pokemonId != "017") return
        handler.postDelayed({
            if (running && pokemonId == "017") {
                spawnPidgeottoWarning()
                schedulePidgeottoScan()
            }
        }, Random.nextLong(5000, 8000))
    }

    /**
     * Výstražný výkřik — žluté zvukové kruhy.
     * Pidgeotto varuje ostatní před nebezpečím.
     */
    private fun spawnPidgeottoWarning() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.3f

        // Pidgeotto se nakloní dozadu jako při křiku
        pokemonView.animate()
            .rotation(if (facingRight) 15f else -15f)
            .setDuration(150)
            .withEndAction {
                pokemonView.animate().rotation(0f).setDuration(200).start()
            }.start()

        // Zvukové kruhy — žluté soustředné vlny
        repeat(3) { ring ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val sz = (20 * dp).toInt()
                val wave = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.TRANSPARENT)
                        setStroke((2 * dp).toInt(), Color.parseColor("#FDD835"))
                    }
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx - sz / 2f
                    y = cy - sz / 2f
                    alpha = 0.9f
                }
                parent.addView(wave)
                wave.animate()
                    .scaleX(4f).scaleY(4f)
                    .alpha(0f)
                    .setDuration(600)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { parent.removeView(wave) }
                    .start()
            }, ring * 180L)
        }
        // Pírka při křiku
        spawnPidgeyFeathers(4)
    }

    /**
     * Pidgeotto přechod — rychlejší a výše než Pidgey.
     * Agresivní oblouk s výstražným křikem při přistání.
     */
    private fun crossingPidgeotto(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        idleAnim?.cancel()
        stopWobble()

        facingRight = toX > fromX
        applyFacing(facingRight)

        val dist = abs(toX - fromX)
        val dur = (dist * 3.5f).toLong().coerceIn(400, 1400)

        // Pírka při letu
        val featherTrail = object : Runnable {
            override fun run() {
                if (!running) return
                spawnPidgeyFeathers(1)
                handler.postDelayed(this, 120L)
            }
        }
        handler.post(featherTrail)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (toX - fromX) * p
                // Vyšší a agresivnější oblouk než Pidgey
                pokemonView.translationY = targetTranslationY - sin(p * PI.toFloat()) * 35f * dp
                pokemonView.rotation = sin(p * PI.toFloat() * 2f) * 14f * (if (facingRight) -1f else 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    handler.removeCallbacks(featherTrail)
                    pokemonView.rotation = 0f
                    pokemonView.translationY = targetTranslationY
                    // Výstražný křik při přistání
                    spawnPidgeottoWarning()
                    if (running) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(700, 2200))
                    }
                }
            })
            start()
        }
    }

    private fun pidgeottoClickReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()

        // Wing Attack — silnější než Pidgey Gust, otřese lištou
        val grandParent = parent.parent as? ViewGroup

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "scaleX",
                    pokemonView.scaleX, pokemonView.scaleX * 1.4f, pokemonView.scaleX
                ).apply { duration = 350 },
                ObjectAnimator.ofFloat(pokemonView, "scaleY",
                    baseScale, baseScale * 0.65f, baseScale
                ).apply { duration = 350 }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(a: Animator) {
                    // Silný vítr
                    val cx = pokemonView.x + pokemonView.width / 2f
                    val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.3f
                    repeat(12) { i ->
                        handler.postDelayed({
                            if (!running) return@postDelayed
                            val wind = View(context).apply {
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.RECTANGLE
                                    setColor(Color.parseColor("#BDBDBD"))
                                    cornerRadius = 2f * dp
                                }
                                val w = (Random.nextInt(15, 30) * dp).toInt()
                                val h = (2 * dp).toInt()
                                layoutParams = ViewGroup.LayoutParams(w, h)
                                x = cx; y = cy + (Random.nextFloat() - 0.5f) * 25f * dp
                                alpha = 0.65f
                            }
                            parent.addView(wind)
                            val dirX = if (facingRight) -200f * dp else 200f * dp
                            wind.animate()
                                .translationXBy(dirX)
                                .alpha(0f)
                                .setDuration(350)
                                .withEndAction { parent.removeView(wind) }
                                .start()
                        }, i * 25L)
                    }
                    spawnPidgeyFeathers(6)
                    // Otřes lišty od silného máchnutí křídlem
                    grandParent?.let {
                        ObjectAnimator.ofFloat(it, "translationX",
                            0f, if (facingRight) -8f * dp else 8f * dp, 0f
                        ).apply { duration = 250; start() }
                    }
                }
                override fun onAnimationEnd(a: Animator) {
                    if (running) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(1000, 3000))
                    }
                }
            })
            start()
        }
    }

// ─────────────────────────────────────────────

    /**
     * Pidgeot idle — majestátní, rychlý, sebevědomý.
     * Létá velmi plynule, barevný chochol se třpytí.
     * Občas provede elegantní otočku — ukázkový let.
     */
    private fun startPidgeotIdle(): Animator {
        // Nejplynulejší létání z linie — velká křídla, stabilní let
        val fly = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 8f * dp, targetTranslationY + 5f * dp
        ).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        schedulePidgeotCrestShimmer()
        fly.start()
        return fly
    }

    private fun schedulePidgeotCrestShimmer() {
        if (!running || pokemonId != "018") return
        handler.postDelayed({
            if (running && pokemonId == "018") {
                spawnCrestShimmer()
                schedulePidgeotCrestShimmer()
            }
        }, Random.nextLong(4000, 7000))
    }

    /**
     * Třpyt chocholku — barevné záblesky kolem hlavy Pidgeota.
     * Červená/žlutá/bílá pírka chocholku.
     */
    private fun spawnCrestShimmer() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width * 0.5f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.1f

        repeat(6) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val shimmer = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(
                            when (i % 3) {
                                0 -> Color.parseColor("#EF5350") // Červená pírka
                                1 -> Color.parseColor("#FDD835") // Žlutá
                                else -> Color.parseColor("#FFFFFF") // Bílá
                            }
                        )
                    }
                    val sz = (Random.nextInt(4, 8) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width * 0.5f
                    y = cy
                    alpha = 0f
                }
                parent.addView(shimmer)
                shimmer.animate()
                    .alpha(0.9f).setDuration(100)
                    .withEndAction {
                        shimmer.animate()
                            .alpha(0f)
                            .translationYBy(-25f * dp)
                            .translationXBy((Random.nextFloat() - 0.5f) * 15f * dp)
                            .scaleX(1.8f).scaleY(1.8f)
                            .setDuration(500)
                            .withEndAction { parent.removeView(shimmer) }
                            .start()
                    }.start()
            }, i * 80L)
        }
    }

    /**
     * Pidgeot přechod — majestátní rychlý průlet.
     * Nejvyšší oblouk, nejvíce naklopení, nejrychlejší z linie.
     * Zanechá barevnou stopu z chocholku.
     */
    private fun crossingPidgeot(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        idleAnim?.cancel()
        stopWobble()

        facingRight = toX > fromX
        applyFacing(facingRight)

        val dist = abs(toX - fromX)
        val dur = (dist * 2.8f).toLong().coerceIn(350, 1100) // Nejrychlejší z linie

        // Barevná stopa chocholku
        val crestTrail = object : Runnable {
            override fun run() {
                if (!running) return
                val parent2 = pokemonView.parent as? ViewGroup ?: return
                val trail = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(
                            when (Random.nextInt(3)) {
                                0 -> Color.parseColor("#EF5350")
                                1 -> Color.parseColor("#FDD835")
                                else -> Color.parseColor("#FFFFFF")
                            }
                        )
                    }
                    val sz = (Random.nextInt(4, 8) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = pokemonView.x + pokemonView.width * 0.5f
                    y = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.1f
                    alpha = 0.8f
                }
                parent2.addView(trail)
                trail.animate()
                    .translationYBy(20f * dp)
                    .scaleX(0.3f).scaleY(0.3f)
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction { parent2.removeView(trail) }
                    .start()
                handler.postDelayed(this, 50L)
            }
        }
        handler.post(crestTrail)

        // Majestátní oblouk — nejvyšší ze všech ptáků
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = dur
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                pokemonView.x = fromX + (toX - fromX) * p
                // Nejvyšší oblouk — Pidgeot létá nejvýše
                pokemonView.translationY = targetTranslationY - sin(p * PI.toFloat()) * 55f * dp
                // Elegantní naklopení
                pokemonView.rotation = sin(p * PI.toFloat() * 2f) * 20f * (if (facingRight) -1f else 1f)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    handler.removeCallbacks(crestTrail)
                    pokemonView.rotation = 0f
                    pokemonView.translationY = targetTranslationY
                    // Třpyt chocholku při přistání
                    spawnCrestShimmer()
                    spawnPidgeyFeathers(6)
                    if (running) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(600, 2000))
                    }
                }
            })
            start()
        }
    }

    private fun pidgeotClickReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()
        idleAnim?.cancel()

        val grandParent = parent.parent as? ViewGroup

        // Hurricane — nejsilnější vzdušný útok, masivní větrný vír
        // 1. Nabíjení — Pidgeot se vzpřímí
        pokemonView.animate()
            .scaleY(baseScale * 1.15f)
            .translationY(targetTranslationY - 15f * dp)
            .setDuration(200)
            .withEndAction {
                // 2. Hurricane — obrovský větrný kruh
                val cx = pokemonView.x + pokemonView.width / 2f
                val cy = pokemonView.y + pokemonView.translationY + pokemonView.height / 2f

                // Soustředné větrné spirály
                repeat(4) { ring ->
                    handler.postDelayed({
                        if (!running) return@postDelayed
                        val sz = ((30 + ring * 15) * dp).toInt()
                        val spiral = View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.TRANSPARENT)
                                setStroke((2 * dp).toInt(),
                                    Color.argb(180 - ring * 30, 255, 255, 255)
                                )
                            }
                            layoutParams = ViewGroup.LayoutParams(sz, sz)
                            x = cx - sz / 2f; y = cy - sz / 2f
                            alpha = 0.9f
                        }
                        parent.addView(spiral)
                        AnimatorSet().apply {
                            playTogether(
                                ObjectAnimator.ofFloat(spiral, "scaleX", 1f, 3f),
                                ObjectAnimator.ofFloat(spiral, "scaleY", 1f, 3f),
                                ObjectAnimator.ofFloat(spiral, "alpha", 0.9f, 0f),
                                ObjectAnimator.ofFloat(spiral, "rotation", 0f,
                                    if (facingRight) 180f else -180f
                                )
                            )
                            duration = 700
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(a: Animator) { parent.removeView(spiral) }
                            })
                            start()
                        }
                    }, ring * 100L)
                }

                // Masivní větrné čáry ve všech směrech
                repeat(16) { i ->
                    handler.postDelayed({
                        if (!running) return@postDelayed
                        val wind = View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                setColor(Color.argb(160, 255, 255, 255))
                                cornerRadius = 2f * dp
                            }
                            val w = (Random.nextInt(20, 45) * dp).toInt()
                            val h = (2 * dp).toInt()
                            layoutParams = ViewGroup.LayoutParams(w, h)
                            x = cx; y = cy + (Random.nextFloat() - 0.5f) * 50f * dp
                            rotation = Random.nextFloat() * 30f - 15f
                            alpha = 0.7f
                        }
                        parent.addView(wind)
                        val dirX = if (facingRight) -250f * dp else 250f * dp
                        wind.animate()
                            .translationXBy(dirX)
                            .alpha(0f)
                            .setDuration(400)
                            .withEndAction { parent.removeView(wind) }
                            .start()
                    }, i * 30L)
                }

                // Hodně pírek
                spawnPidgeyFeathers(10)
                spawnCrestShimmer()

                // Otřes celé lišty
                grandParent?.let {
                    ObjectAnimator.ofFloat(it, "translationX",
                        0f, -12f * dp, 12f * dp, -8f * dp, 8f * dp, -4f * dp, 4f * dp, 0f
                    ).apply { duration = 500; start() }
                }

                // 3. Návrat
                pokemonView.animate()
                    .scaleY(baseScale)
                    .translationY(targetTranslationY)
                    .setDuration(300)
                    .withEndAction {
                        if (running) {
                            startIdleAnimation()
                            scheduleStep(Random.nextLong(1000, 3000))
                        }
                    }.start()
            }.start()
    }

    // ─────────────────────────────────────────────
// RATTATA LINE
// ─────────────────────────────────────────────

    /**
     * Rattata idle — nervózní hlodavec, neustále přešlapuje
     * a čmuchá. Rychlé drobné pohyby, velké zuby.
     */
    private fun startRattataIdle(): Animator {
        // Rychlé přešlapování — hlodavec nikdy nezůstane stát
        val fidget = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY, targetTranslationY - 4f * dp, targetTranslationY
        ).apply {
            duration = 300
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleRattataSniff()
        fidget.start()
        return fidget
    }

    private fun scheduleRattataSniff() {
        if (!running || pokemonId != "019") return
        handler.postDelayed({
            if (running && pokemonId == "019") {
                // Rychlé čmuchání — pohyb hlavy dopředu a zpět
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(pokemonView, "scaleX",
                            pokemonView.scaleX,
                            pokemonView.scaleX * 1.08f,
                            pokemonView.scaleX
                        ).apply { duration = 250 },
                        ObjectAnimator.ofFloat(pokemonView, "scaleY",
                            baseScale, baseScale * 0.95f, baseScale
                        ).apply { duration = 250 }
                    )
                    start()
                }
                // Malé tečky — čmuchání
                spawnSniffParticles()
                scheduleRattataSniff()
            }
        }, Random.nextLong(2000, 4500))
    }

    private fun spawnSniffParticles() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        // Nos je přibližně na přední části spritu
        val cx = pokemonView.x + pokemonView.width * (if (facingRight) 0.15f else 0.85f)
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.35f

        repeat(3) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val sniff = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#BDBDBD"))
                    }
                    val sz = (Random.nextInt(3, 6) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx; y = cy
                    alpha = 0.6f
                }
                parent.addView(sniff)
                sniff.animate()
                    .translationYBy(-15f * dp)
                    .translationXBy((Random.nextFloat() - 0.5f) * 10f * dp)
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction { parent.removeView(sniff) }
                    .start()
            }, i * 80L)
        }
    }

    /**
     * Rattata přechod — extrémně rychlý dash s rychlými kroky.
     * Quick Attack — jeden z nejrychlejších pohybů.
     * Zanechá bílou stopu rychlosti.
     */
    private fun crossingRattata(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()
        idleAnim?.cancel()

        facingRight = toX > fromX
        applyFacing(facingRight)

        // Quick Attack — krátké nabíjení
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "scaleX",
                    pokemonView.scaleX * 0.7f, pokemonView.scaleX
                ).apply { duration = 120 },
                ObjectAnimator.ofFloat(pokemonView, "scaleY",
                    baseScale * 1.2f, baseScale
                ).apply { duration = 120 }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (!running) return

                    val dist = abs(toX - fromX)
                    // Rattata je velmi rychlý — Quick Attack
                    val dur = (dist * 3.5f).toLong().coerceIn(200, 900)

                    // Bílá rychlostní stopa
                    val speedTrail = object : Runnable {
                        override fun run() {
                            if (!running) return
                            val trail = View(context).apply {
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setColor(Color.parseColor("#EEEEEE"))
                                }
                                val sz = (Random.nextInt(5, 10) * dp).toInt()
                                layoutParams = ViewGroup.LayoutParams(sz, sz)
                                x = pokemonView.x + pokemonView.width / 2f
                                y = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.6f
                                alpha = 0.7f
                            }
                            parent.addView(trail)
                            trail.animate()
                                .scaleX(2f).scaleY(2f)
                                .alpha(0f)
                                .setDuration(200)
                                .withEndAction { parent.removeView(trail) }
                                .start()
                            handler.postDelayed(this, 35L)
                        }
                    }
                    handler.post(speedTrail)

                    // Rattata se zploští při pohybu (aerodynamika)
                    pokemonView.animate()
                        .scaleY(baseScale * 0.8f)
                        .setDuration(80)
                        .start()

                    moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
                        duration = dur
                        interpolator = AccelerateInterpolator()
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(a: Animator) {
                                handler.removeCallbacks(speedTrail)
                                // Zabrzdění s poskočením
                                pokemonView.animate()
                                    .scaleY(baseScale)
                                    .setDuration(100)
                                    .withEndAction {
                                        // Malý poskak při zastavení
                                        pokemonView.animate()
                                            .translationY(targetTranslationY - 6f * dp)
                                            .setDuration(80)
                                            .withEndAction {
                                                pokemonView.animate()
                                                    .translationY(targetTranslationY)
                                                    .setDuration(100)
                                                    .start()
                                            }.start()
                                        if (running) {
                                            startIdleAnimation()
                                            scheduleStep(Random.nextLong(600, 2000))
                                        }
                                    }.start()
                            }
                        })
                        start()
                    }
                }
            })
            start()
        }
    }

    private fun rattataClickReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return

        // Bite — rychlé kousnutí velkými zuby
        // Rattata vyskočí a zakousne se
        pokemonView.animate()
            .translationY(targetTranslationY - 10f * dp)
            .scaleX(pokemonView.scaleX * 1.2f)
            .setDuration(100)
            .withEndAction {
                pokemonView.animate()
                    .translationY(targetTranslationY)
                    .scaleX(if (facingRight) -baseScale else baseScale)
                    .setDuration(150)
                    .setInterpolator(AccelerateInterpolator())
                    .start()
            }.start()

        // Efekt kousnutí — bílé čáry jako zuby
        val cx = pokemonView.x + pokemonView.width * (if (facingRight) 0.1f else 0.9f)
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.4f

        repeat(4) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val tooth = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.WHITE)
                        cornerRadius = 1f * dp
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        (3 * dp).toInt(),
                        (Random.nextInt(6, 12) * dp).toInt()
                    )
                    x = cx + (i - 2) * 5f * dp
                    y = cy
                    alpha = 0.9f
                }
                parent.addView(tooth)
                tooth.animate()
                    .translationYBy(-12f * dp)
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { parent.removeView(tooth) }
                    .start()
            }, i * 40L)
        }
    }

// ─────────────────────────────────────────────

    /**
     * Raticate idle — větší, agresivnější hlodavec.
     * Pomalejší než Rattata ale hrozivější.
     * Dupe nohama a hrozí obrovskými zuby.
     */
    private fun startRaticateIdle(): Animator {
        // Pomalejší ale těžší pohyb než Rattata
        val stomp = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY, targetTranslationY - 5f * dp, targetTranslationY
        ).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleRaticateGrowl()
        stomp.start()
        return stomp
    }

    private fun scheduleRaticateGrowl() {
        if (!running || pokemonId != "020") return
        handler.postDelayed({
            if (running && pokemonId == "020") {
                spawnRaticateGrowl()
                scheduleRaticateGrowl()
            }
        }, Random.nextLong(4000, 7000))
    }

    /**
     * Growl — výstražné vrčení. Žluté zvukové vlny jako při varování.
     * Raticate se nafoukne a zastraší okolí.
     */
    private fun spawnRaticateGrowl() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.35f

        // Nafuknutí při vrčení
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "scaleX",
                    pokemonView.scaleX, pokemonView.scaleX * 1.15f, pokemonView.scaleX
                ).apply { duration = 400 },
                ObjectAnimator.ofFloat(pokemonView, "scaleY",
                    baseScale, baseScale * 1.15f, baseScale
                ).apply { duration = 400 }
            )
            start()
        }

        // Zvukové vlny — žluté kruhy
        repeat(3) { ring ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val sz = (18 * dp).toInt()
                val wave = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.TRANSPARENT)
                        setStroke((2 * dp).toInt(), Color.parseColor("#FBC02D"))
                    }
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx - sz / 2f
                    y = cy - sz / 2f
                    alpha = 0.9f
                }
                parent.addView(wave)
                wave.animate()
                    .scaleX(3.5f).scaleY(3.5f)
                    .alpha(0f)
                    .setDuration(600)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { parent.removeView(wave) }
                    .start()
            }, ring * 160L)
        }
    }

    /**
     * Raticate přechod — rychlý ale těžší než Rattata.
     * Dupající kroky s otřesy, ne Quick Attack ale Tackle.
     */
    private fun crossingRaticate(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()
        idleAnim?.cancel()

        facingRight = toX > fromX
        applyFacing(facingRight)

        val grandParent = parent.parent as? ViewGroup
        val dist = abs(toX - fromX)

        // Raticate běží ve skocích — těžší než Rattata
        val stepCount = (dist / (40f * dp)).toInt().coerceIn(2, 6)
        val stepDist = (toX - fromX) / stepCount
        var currentStep = 0

        fun doStep() {
            if (!running || currentStep >= stepCount) {
                if (running) {
                    startIdleAnimation()
                    scheduleStep(Random.nextLong(800, 2500))
                }
                return
            }

            val stepFrom = fromX + currentStep * stepDist
            val stepTo = fromX + (currentStep + 1) * stepDist

            // Každý krok — malý skok
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(pokemonView, "x", stepFrom, stepTo).apply {
                        duration = 220
                        interpolator = AccelerateInterpolator()
                    },
                    ObjectAnimator.ofFloat(pokemonView, "translationY",
                        targetTranslationY,
                        targetTranslationY - 12f * dp,
                        targetTranslationY
                    ).apply {
                        duration = 220
                        interpolator = AccelerateDecelerateInterpolator()
                    }
                )
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) {
                        if (!running) return
                        // Otřes při dopadu každého kroku
                        grandParent?.let {
                            ObjectAnimator.ofFloat(it, "translationY",
                                0f, 4f * dp, 0f
                            ).apply { duration = 120; start() }
                        }
                        currentStep++
                        handler.postDelayed({ doStep() }, 60L)
                    }
                })
                start()
            }
        }

        doStep()
    }

    private fun raticateClickReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()
        idleAnim?.cancel()

        val grandParent = parent.parent as? ViewGroup

        // Super Fang — obrovské kousnutí, otřese celou lištou
        // 1. Nabíjení — Raticate se vzpřímí a rozevře tlamu
        pokemonView.animate()
            .scaleY(baseScale * 1.25f)
            .scaleX(pokemonView.scaleX * 0.85f)
            .translationY(targetTranslationY - 12f * dp)
            .setDuration(200)
            .withEndAction {
                // 2. Útok — prudký výpad
                pokemonView.animate()
                    .scaleY(baseScale)
                    .scaleX(if (facingRight) -baseScale else baseScale)
                    .translationY(targetTranslationY)
                    .setDuration(100)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        // 3. Obrovský otřes — Super Fang je silný
                        grandParent?.let {
                            ObjectAnimator.ofFloat(it, "translationX",
                                0f, -15f * dp, 15f * dp, -10f * dp, 10f * dp, -5f * dp, 5f * dp, 0f
                            ).apply { duration = 450; start() }
                            ObjectAnimator.ofFloat(it, "translationY",
                                0f, 8f * dp, -5f * dp, 3f * dp, 0f
                            ).apply { duration = 450; start() }
                        }

                        // Efekt kousnutí — velké bílé zuby
                        val cx = pokemonView.x + pokemonView.width * (if (facingRight) 0.1f else 0.9f)
                        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.35f

                        repeat(6) { i ->
                            handler.postDelayed({
                                if (!running) return@postDelayed
                                val tooth = View(context).apply {
                                    background = GradientDrawable().apply {
                                        shape = GradientDrawable.RECTANGLE
                                        setColor(if (i % 2 == 0) Color.WHITE else Color.parseColor("#FFF9C4"))
                                        cornerRadius = 2f * dp
                                    }
                                    layoutParams = ViewGroup.LayoutParams(
                                        (4 * dp).toInt(),
                                        (Random.nextInt(10, 18) * dp).toInt()
                                    )
                                    x = cx + (i - 3) * 6f * dp
                                    y = cy
                                    alpha = 0.95f
                                }
                                parent.addView(tooth)
                                tooth.animate()
                                    .translationYBy(-20f * dp)
                                    .scaleX(0.5f)
                                    .alpha(0f)
                                    .setDuration(400)
                                    .withEndAction { parent.removeView(tooth) }
                                    .start()
                            }, i * 35L)
                        }

                        // Rázové vlny po kousnutí
                        repeat(2) { i ->
                            handler.postDelayed({
                                if (!running) return@postDelayed
                                spawnStompWave(parent,
                                    pokemonView.x + pokemonView.width / 2f,
                                    pokemonView.y + pokemonView.translationY + pokemonView.height
                                )
                            }, i * 100L)
                        }

                        if (running) {
                            handler.postDelayed({
                                startIdleAnimation()
                                scheduleStep(Random.nextLong(1500, 3500))
                            }, 500L)
                        }
                    }.start()
            }.start()
    }

// ─────────────────────────────────────────────
// SPEAROW LINE
// ─────────────────────────────────────────────

    /**
     * Spearow idle — malý agresivní pták, nervózní a hlučný.
     * Rychle mává krátkými křídly, neustále se rozhlíží.
     * Létá nízko — krátká křídla ho omezují.
     */
    private fun startSpearowIdle(): Animator {
        // Rychlé nervózní mávání — krátká křídla pracují více
        val flutter = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 4f * dp, targetTranslationY + 3f * dp
        ).apply {
            duration = 600 // Rychlejší než Pidgey — víc mává
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleSpearowAggression()
        flutter.start()
        return flutter
    }

    private fun scheduleSpearowAggression() {
        if (!running || pokemonId != "021") return
        handler.postDelayed({
            if (running && pokemonId == "021") {
                spawnSpearowPeck()
                scheduleSpearowAggression()
            }
        }, Random.nextLong(3000, 6000))
    }

    /**
     * Peck — rychlé bodnutí zobákem dopředu.
     * Spearow je known pro agresivní zobání.
     */
    private fun spawnSpearowPeck() {
        val parent = pokemonView.parent as? ViewGroup ?: return

        // Rychlý výpad zobákem — Spearow se nakloní dopředu
        val leanX = if (facingRight) -10f * dp else 10f * dp
        pokemonView.animate()
            .translationXBy(leanX)
            .scaleY(baseScale * 0.85f)
            .setDuration(80)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                pokemonView.animate()
                    .translationXBy(-leanX)
                    .scaleY(baseScale)
                    .setDuration(150)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                // Malé hvězdičky při zobání — agresivní útok
                val cx = pokemonView.x + pokemonView.width * (if (facingRight) 0.1f else 0.9f)
                val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.3f

                repeat(4) { i ->
                    handler.postDelayed({
                        if (!running) return@postDelayed
                        val star = View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.parseColor("#FFF176"))
                            }
                            val sz = (Random.nextInt(4, 8) * dp).toInt()
                            layoutParams = ViewGroup.LayoutParams(sz, sz)
                            x = cx; y = cy; alpha = 0.9f
                        }
                        parent.addView(star)
                        val angle = i * 90f
                        star.animate()
                            .translationXBy(20f * dp * cos(Math.toRadians(angle.toDouble())).toFloat())
                            .translationYBy(20f * dp * sin(Math.toRadians(angle.toDouble())).toFloat())
                            .alpha(0f).scaleX(0.3f).scaleY(0.3f)
                            .setDuration(300)
                            .withEndAction { parent.removeView(star) }
                            .start()
                    }, i * 40L)
                }
            }.start()
    }

    /**
     * Spearow přechod — nízký agresivní let s bodáním.
     * Neletí tak elegantně jako Pidgey — zuřivé mávání.
     */
    private fun crossingSpearow(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        idleAnim?.cancel()
        stopWobble()

        facingRight = toX > fromX
        applyFacing(facingRight)

        val dist = abs(toX - fromX)
        val dur = (dist * 4f).toLong().coerceIn(350, 1300)

        // Nervózní mávání při letu — rychlé vertikální oscilace
        val wobbleTrail = object : Runnable {
            var tick = 0
            override fun run() {
                if (!running) return
                // Nervózní vertikální poskakování při letu
                val bounce = if (tick % 2 == 0) -8f * dp else 2f * dp
                pokemonView.animate()
                    .translationY(targetTranslationY + bounce)
                    .setDuration(80)
                    .start()
                tick++
                handler.postDelayed(this, 90L)
            }
        }
        handler.post(wobbleTrail)

        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            duration = dur
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    handler.removeCallbacks(wobbleTrail)
                    pokemonView.animate()
                        .translationY(targetTranslationY)
                        .setDuration(100)
                        .withEndAction {
                            // Agresivní zobnutí po přistání
                            spawnSpearowPeck()
                            if (running) {
                                startIdleAnimation()
                                scheduleStep(Random.nextLong(600, 2000))
                            }
                        }.start()
                }
            })
            start()
        }
    }

    private fun spearowClickReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return

        // Fury Attack — série rychlých zobnutí
        var pecks = 0
        val totalPecks = 5

        fun doPeck() {
            if (!running || pecks >= totalPecks) {
                if (running) {
                    startIdleAnimation()
                    scheduleStep(Random.nextLong(800, 2500))
                }
                return
            }

            val leanX = if (facingRight) -14f * dp else 14f * dp
            pokemonView.animate()
                .translationXBy(leanX)
                .setDuration(60)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    // Hvězdičky při každém zobnutí
                    val cx = pokemonView.x + pokemonView.width * (if (facingRight) 0.1f else 0.9f)
                    val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.3f
                    repeat(3) { i ->
                        val star = View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(if (i % 2 == 0) Color.parseColor("#FFF176") else Color.WHITE)
                            }
                            val sz = (Random.nextInt(3, 7) * dp).toInt()
                            layoutParams = ViewGroup.LayoutParams(sz, sz)
                            x = cx; y = cy; alpha = 0.9f
                        }
                        parent.addView(star)
                        star.animate()
                            .translationXBy((Random.nextFloat() - 0.5f) * 25f * dp)
                            .translationYBy(-15f * dp - Random.nextFloat() * 10f * dp)
                            .alpha(0f)
                            .setDuration(250)
                            .withEndAction { parent.removeView(star) }
                            .start()
                    }

                    pokemonView.animate()
                        .translationXBy(-leanX)
                        .setDuration(80)
                        .withEndAction {
                            pecks++
                            handler.postDelayed({ doPeck() }, 50L)
                        }.start()
                }.start()
        }

        doPeck()
    }

// ─────────────────────────────────────────────

    /**
     * Fearow idle — velký predátor s dlouhým zobákem.
     * Majestátnější než Spearow ale stále agresivní.
     * Plánuje útok — pomalé skenování okolí, pak rychlý výpad.
     */
    private fun startFearowIdle(): Animator {
        // Plynulejší létání — větší křídla
        val soar = ObjectAnimator.ofFloat(pokemonView, "translationY",
            targetTranslationY - 7f * dp, targetTranslationY + 4f * dp
        ).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        scheduleFearowDrill()
        soar.start()
        return soar
    }

    private fun scheduleFearowDrill() {
        if (!running || pokemonId != "022") return
        handler.postDelayed({
            if (running && pokemonId == "022") {
                spawnFearowDrillEffect()
                scheduleFearowDrill()
            }
        }, Random.nextLong(5000, 9000))
    }

    /**
     * Drill Peck — rotující spirálový výpad zobákem.
     * Fearow je known pro tuto techniku — zobák se vrtí jako vrták.
     */
    private fun spawnFearowDrillEffect() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width * (if (facingRight) 0.15f else 0.85f)
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.25f

        // Fearow se nakloní a zobák se roztočí
        pokemonView.animate()
            .translationXBy(if (facingRight) -15f * dp else 15f * dp)
            .rotation(if (facingRight) -25f else 25f)
            .setDuration(100)
            .withEndAction {
                pokemonView.animate()
                    .translationXBy(if (facingRight) 15f * dp else -15f * dp)
                    .rotation(0f)
                    .setDuration(200)
                    .start()
            }.start()

        // Spirálové kruhy z vrták-zobáku
        repeat(4) { ring ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val sz = ((8 + ring * 6) * dp).toInt()
                val spiral = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.TRANSPARENT)
                        setStroke((2 * dp).toInt(),
                            Color.argb(200 - ring * 30,
                                255, 200 - ring * 20, 100 - ring * 10)
                        )
                    }
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx - sz / 2f; y = cy - sz / 2f
                    alpha = 0.85f
                }
                parent.addView(spiral)
                spiral.animate()
                    .scaleX(2.5f).scaleY(2.5f)
                    .rotation(if (facingRight) -360f else 360f)
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction { parent.removeView(spiral) }
                    .start()
            }, ring * 80L)
        }
    }

    /**
     * Fearow přechod — Drill Run.
     * Spirálový let přes celou lištu — nejagresivnější přechod ptáků.
     * Fearow se roztočí a projede jako vrták.
     */
    private fun crossingFearow(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        idleAnim?.cancel()
        stopWobble()

        facingRight = toX > fromX
        applyFacing(facingRight)

        val dist = abs(toX - fromX)
        val dur = (dist * 3f).toLong().coerceIn(300, 1100)

        // 1. Nabíjení — Fearow se roztočí jako vrták
        val chargeRotation = ObjectAnimator.ofFloat(pokemonView, "rotation",
            0f, if (facingRight) -720f else 720f
        ).apply {
            duration = dur
            interpolator = LinearInterpolator()
        }

        // Spirálová stopa při průletu
        val drillTrail = object : Runnable {
            var tick = 0
            override fun run() {
                if (!running) return
                val trail = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(
                            if (tick % 2 == 0) Color.parseColor("#FF8F00")
                            else Color.parseColor("#FFF176")
                        )
                    }
                    val sz = (Random.nextInt(5, 10) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = pokemonView.x + pokemonView.width / 2f
                    y = pokemonView.y + pokemonView.translationY +
                            pokemonView.height * (0.2f + (tick % 5) * 0.15f)
                    alpha = 0.8f
                }
                parent.addView(trail)
                trail.animate()
                    .translationXBy((Random.nextFloat() - 0.5f) * 20f * dp)
                    .scaleX(0.3f).scaleY(0.3f)
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction { parent.removeView(trail) }
                    .start()
                tick++
                handler.postDelayed(this, 40L)
            }
        }
        handler.post(drillTrail)

        AnimatorSet().apply {
            playTogether(
                chargeRotation,
                ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
                    duration = dur
                    interpolator = AccelerateDecelerateInterpolator()
                },
                // Mírný sinusový oblouk i při vrtání
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = dur
                    addUpdateListener { anim ->
                        val p = anim.animatedValue as Float
                        pokemonView.translationY = targetTranslationY -
                                sin(p * PI.toFloat()) * 25f * dp
                    }
                }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    handler.removeCallbacks(drillTrail)
                    pokemonView.rotation = 0f
                    pokemonView.translationY = targetTranslationY

                    // Drill Peck efekt při přistání
                    spawnFearowDrillEffect()

                    if (running) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(600, 2000))
                    }
                }
            })
            start()
        }
    }

    private fun fearowClickReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()
        idleAnim?.cancel()

        val grandParent = parent.parent as? ViewGroup

        // Drill Peck — plný útok vrtákovým zobákem
        // 1. Nabíjení — Fearow se vzpřímí a začne rotovat
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "scaleY",
                    baseScale, baseScale * 1.2f
                ).apply { duration = 200 },
                ObjectAnimator.ofFloat(pokemonView, "translationY",
                    targetTranslationY, targetTranslationY - 18f * dp
                ).apply { duration = 200 }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (!running) return

                    // 2. Drill Peck — tři rychlé rotační výpady
                    var attacks = 0
                    fun doAttack() {
                        if (!running || attacks >= 3) {
                            pokemonView.rotation = 0f
                            pokemonView.animate()
                                .scaleY(baseScale)
                                .translationY(targetTranslationY)
                                .setDuration(200)
                                .withEndAction {
                                    if (running) {
                                        startIdleAnimation()
                                        scheduleStep(Random.nextLong(1000, 3000))
                                    }
                                }.start()
                            return
                        }

                        // Rotační výpad
                        val leanX = if (facingRight) -20f * dp else 20f * dp
                        AnimatorSet().apply {
                            playTogether(
                                ObjectAnimator.ofFloat(pokemonView, "translationX",
                                    0f, leanX
                                ).apply { duration = 80 },
                                ObjectAnimator.ofFloat(pokemonView, "rotation",
                                    0f, if (facingRight) -360f else 360f
                                ).apply { duration = 300 }
                            )
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(a: Animator) {
                                    // Spirálový efekt při výpadu
                                    spawnFearowDrillEffect()

                                    // Otřes lišty
                                    grandParent?.let {
                                        ObjectAnimator.ofFloat(it, "translationX",
                                            0f,
                                            if (facingRight) -10f * dp else 10f * dp,
                                            0f
                                        ).apply { duration = 200; start() }
                                    }

                                    pokemonView.animate()
                                        .translationX(0f)
                                        .setDuration(100)
                                        .withEndAction {
                                            attacks++
                                            handler.postDelayed({ doAttack() }, 100L)
                                        }.start()
                                }
                            })
                            start()
                        }
                    }
                    doAttack()
                }
            })
            start()
        }
    }

    // ─────────────────────────────────────────────
// EKANS LINE
// ─────────────────────────────────────────────

    /**
     * Ekans idle — had se pomalu vlní jako had v klidu.
     * Sinusové vlnění těla, ocasem bubnuje o zem.
     * Občas vystřelí jazyk.
     */
    private fun startEkansIdle(): Animator {
        // Sinusové vlnění — had se vlní tělem
        val slither = ValueAnimator.ofFloat(0f, (2f * PI).toFloat()).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                pokemonView.rotation = sin(t.toDouble()).toFloat() * 8f
                pokemonView.translationY = targetTranslationY +
                        sin((t * 1.5f).toDouble()).toFloat() * 3f * dp
            }
        }
        scheduleEkansTongue()
        slither.start()
        return slither
    }

    private fun scheduleEkansTongue() {
        if (!running || pokemonId != "023") return
        handler.postDelayed({
            if (running && pokemonId == "023") {
                spawnTongueFlick(small = true)
                scheduleEkansTongue()
            }
        }, Random.nextLong(3000, 6000))
    }

    /**
     * Vystřelení jazyka — červená vidlicová linie.
     * small = Ekans (kratší), !small = Arbok (delší a silnější).
     */
    private fun spawnTongueFlick(small: Boolean) {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width * (if (facingRight) 0.1f else 0.9f)
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.3f

        val tongueLen = if (small) 18f * dp else 28f * dp

        // Levá vidlice
        val fork1 = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#E53935"))
                cornerRadius = 2f * dp
            }
            layoutParams = ViewGroup.LayoutParams(tongueLen.toInt(), (3 * dp).toInt())
            x = cx; y = cy - 3f * dp
            rotation = if (facingRight) -15f else 165f
            alpha = 0f; scaleX = 0f
            pivotX = 0f
        }
        // Pravá vidlice
        val fork2 = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#E53935"))
                cornerRadius = 2f * dp
            }
            layoutParams = ViewGroup.LayoutParams(tongueLen.toInt(), (3 * dp).toInt())
            x = cx; y = cy + 3f * dp
            rotation = if (facingRight) 15f else 195f
            alpha = 0f; scaleX = 0f
            pivotX = 0f
        }

        parent.addView(fork1)
        parent.addView(fork2)

        // Vyjetí a zajetí jazyka
        listOf(fork1, fork2).forEach { fork ->
            fork.animate().alpha(0.95f).scaleX(1f).setDuration(120)
                .withEndAction {
                    handler.postDelayed({
                        fork.animate().scaleX(0f).alpha(0f).setDuration(100)
                            .withEndAction { parent.removeView(fork) }.start()
                    }, 200L)
                }.start()
        }
    }

    /**
     * Ekans přechod — plynulé plazení s vlněním těla.
     * Zanechá fialovou jedovou stopu.
     */
    private fun crossingEkans(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()
        idleAnim?.cancel()

        facingRight = toX > fromX
        applyFacing(facingRight)

        val dist = abs(toX - fromX)
        val dur = (dist * 15f).toLong().coerceIn(1000, 4000)

        // Jedová stopa za hadem
        val poisonTrail = object : Runnable {
            var tick = 0
            override fun run() {
                if (!running) return
                val trail = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(
                            if (tick % 2 == 0) Color.parseColor("#7B1FA2")
                            else Color.parseColor("#AB47BC")
                        )
                    }
                    val sz = (Random.nextInt(4, 8) * dp).toInt()
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = pokemonView.x + pokemonView.width / 2f +
                            sin(tick * 0.8).toFloat() * 8f * dp
                    y = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.8f
                    alpha = 0.6f
                }
                parent.addView(trail)
                trail.animate()
                    .translationYBy(8f * dp)
                    .alpha(0f)
                    .setDuration(600)
                    .withEndAction { parent.removeView(trail) }
                    .start()
                tick++
                handler.postDelayed(this, 80L)
            }
        }
        handler.post(poisonTrail)

        // Vlnění těla při pohybu
        var elapsed = 0L
        val stepMs = 30L
        val waveRunnable = object : Runnable {
            override fun run() {
                if (!running) return
                elapsed += stepMs
                val p = elapsed.toFloat() / dur.toFloat()
                pokemonView.rotation = sin((p * 6f * PI).toFloat()).toFloat() * 10f
                if (elapsed < dur) handler.postDelayed(this, stepMs)
            }
        }
        handler.post(waveRunnable)

        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            duration = dur
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    handler.removeCallbacks(poisonTrail)
                    handler.removeCallbacks(waveRunnable)
                    pokemonView.rotation = 0f
                    // Vystřelí jazyk při zastavení
                    spawnTongueFlick(small = true)
                    if (running) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(1000, 3000))
                    }
                }
            })
            start()
        }
    }

    private fun ekansClickReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return

        // Poison Sting — Ekans se stočí a bodne
        // 1. Stočení — zmenší se a rotuje
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "scaleX",
                    pokemonView.scaleX, pokemonView.scaleX * 0.8f
                ).apply { duration = 150 },
                ObjectAnimator.ofFloat(pokemonView, "scaleY",
                    baseScale, baseScale * 0.8f
                ).apply { duration = 150 },
                ObjectAnimator.ofFloat(pokemonView, "rotation",
                    0f, if (facingRight) -30f else 30f
                ).apply { duration = 150 }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    // 2. Výpad — Ekans se narovná a bodne
                    AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(pokemonView, "scaleX",
                                pokemonView.scaleX, if (facingRight) -baseScale * 1.3f else baseScale * 1.3f
                            ).apply { duration = 120 },
                            ObjectAnimator.ofFloat(pokemonView, "scaleY",
                                baseScale * 0.8f, baseScale
                            ).apply { duration = 120 },
                            ObjectAnimator.ofFloat(pokemonView, "rotation",
                                pokemonView.rotation, 0f
                            ).apply { duration = 120 }
                        )
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(a: Animator) {
                                // Jedový výbuch
                                val cx = pokemonView.x + pokemonView.width *
                                        (if (facingRight) 0.1f else 0.9f)
                                val cy = pokemonView.y + pokemonView.translationY +
                                        pokemonView.height * 0.3f

                                spawnTongueFlick(small = true)

                                repeat(6) { i ->
                                    handler.postDelayed({
                                        if (!running) return@postDelayed
                                        val poison = View(context).apply {
                                            background = GradientDrawable().apply {
                                                shape = GradientDrawable.OVAL
                                                setColor(
                                                    if (i % 2 == 0) Color.parseColor("#7B1FA2")
                                                    else Color.parseColor("#CE93D8")
                                                )
                                            }
                                            val sz = (Random.nextInt(5, 10) * dp).toInt()
                                            layoutParams = ViewGroup.LayoutParams(sz, sz)
                                            x = cx; y = cy; alpha = 0.9f
                                        }
                                        parent.addView(poison)
                                        val angle = (i * 60f) - 90f
                                        val dist = 35f * dp
                                        poison.animate()
                                            .translationXBy(dist * cos(Math.toRadians(angle.toDouble())).toFloat())
                                            .translationYBy(dist * sin(Math.toRadians(angle.toDouble())).toFloat())
                                            .alpha(0f).scaleX(1.5f).scaleY(1.5f)
                                            .setDuration(500)
                                            .withEndAction { parent.removeView(poison) }
                                            .start()
                                    }, i * 40L)
                                }

                                // Návrat
                                pokemonView.animate()
                                    .scaleX(if (facingRight) -baseScale else baseScale)
                                    .setDuration(200)
                                    .withEndAction {
                                        if (running) {
                                            startIdleAnimation()
                                            scheduleStep(Random.nextLong(1000, 3000))
                                        }
                                    }.start()
                            }
                        })
                        start()
                    }
                }
            })
            start()
        }
    }

// ─────────────────────────────────────────────

    /**
     * Arbok idle — kobra s rozevřenou kapucí.
     * Pomalé hrozivé vlnění, kapuce se nafukuje.
     * Občas zasyčí — silnější a děsivější než Ekans.
     */
    private fun startArbokIdle(): Animator {
        // Pomalejší ale hrozivější vlnění — kobra v pozoru
        val sway = ValueAnimator.ofFloat(0f, (2f * PI).toFloat()).apply {
            duration = 2800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                pokemonView.rotation = sin(t.toDouble()).toFloat() * 10f
                pokemonView.translationY = targetTranslationY +
                        sin((t * 0.8f).toDouble()).toFloat() * 4f * dp
            }
        }
        scheduleArbokHiss()
        sway.start()
        return sway
    }

    private fun scheduleArbokHiss() {
        if (!running || pokemonId != "024") return
        handler.postDelayed({
            if (running && pokemonId == "024") {
                spawnArbokHiss()
                scheduleArbokHiss()
            }
        }, Random.nextLong(4500, 8000))
    }

    /**
     * Hiss — Arbok rozevře kapuci a zasykne.
     * Fialové soustředné kruhy jako zvukové vlny syčení.
     * Větší a děsivější než Ekansův útok.
     */
    private fun spawnArbokHiss() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f
        val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.25f

        // Nafuknutí kapuce
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "scaleX",
                    pokemonView.scaleX,
                    pokemonView.scaleX * 1.2f,
                    pokemonView.scaleX
                ).apply { duration = 500 },
                ObjectAnimator.ofFloat(pokemonView, "scaleY",
                    baseScale, baseScale * 1.15f, baseScale
                ).apply { duration = 500 }
            )
            start()
        }

        // Syčení — fialové + červené vlny
        repeat(4) { ring ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val sz = (20 * dp).toInt()
                val wave = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.TRANSPARENT)
                        setStroke(
                            (2 * dp).toInt(),
                            when (ring % 2) {
                                0 -> Color.parseColor("#6A1B9A")
                                else -> Color.parseColor("#B71C1C")
                            }
                        )
                    }
                    layoutParams = ViewGroup.LayoutParams(sz, sz)
                    x = cx - sz / 2f; y = cy - sz / 2f
                    alpha = 0.9f
                }
                parent.addView(wave)
                wave.animate()
                    .scaleX(4f).scaleY(4f)
                    .alpha(0f)
                    .setDuration(700)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { parent.removeView(wave) }
                    .start()
            }, ring * 140L)
        }

        // Jazyk při syčení
        spawnTongueFlick(small = false)
    }

    /**
     * Arbok přechod — rychlé klouzavé plazení s vlněním.
     * Rychlejší než Ekans, zanechá silnější jedovou stopu.
     * Při přistání rozevře kapuci.
     */
    private fun crossingArbok(fromX: Float, toX: Float) {
        val parent = pokemonView.parent as? ViewGroup ?: run { defaultCrossing(fromX, toX); return }
        stopWobble()
        idleAnim?.cancel()

        facingRight = toX > fromX
        applyFacing(facingRight)

        val dist = abs(toX - fromX)
        val dur = (dist * 10f).toLong().coerceIn(800, 3000)

        // Silnější jedová stopa — větší had
        val poisonTrail = object : Runnable {
            var tick = 0
            override fun run() {
                if (!running) return
                repeat(2) { j ->
                    val trail = View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(
                                when ((tick + j) % 3) {
                                    0 -> Color.parseColor("#6A1B9A")
                                    1 -> Color.parseColor("#9C27B0")
                                    else -> Color.parseColor("#B71C1C")
                                }
                            )
                        }
                        val sz = (Random.nextInt(5, 10) * dp).toInt()
                        layoutParams = ViewGroup.LayoutParams(sz, sz)
                        x = pokemonView.x + pokemonView.width / 2f +
                                sin((tick + j) * 0.6).toFloat() * 10f * dp
                        y = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.85f
                        alpha = 0.7f
                    }
                    parent.addView(trail)
                    trail.animate()
                        .translationYBy(10f * dp)
                        .alpha(0f)
                        .setDuration(700)
                        .withEndAction { parent.removeView(trail) }
                        .start()
                }
                tick++
                handler.postDelayed(this, 70L)
            }
        }
        handler.post(poisonTrail)

        // Vlnění těla
        var elapsed = 0L
        val stepMs = 30L
        val waveRunnable = object : Runnable {
            override fun run() {
                if (!running) return
                elapsed += stepMs
                val p = elapsed.toFloat() / dur.toFloat()
                pokemonView.rotation = sin((p * 5f * PI).toFloat()).toFloat() * 12f
                if (elapsed < dur) handler.postDelayed(this, stepMs)
            }
        }
        handler.post(waveRunnable)

        moveAnim = ObjectAnimator.ofFloat(pokemonView, "x", fromX, toX).apply {
            duration = dur
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    handler.removeCallbacks(poisonTrail)
                    handler.removeCallbacks(waveRunnable)
                    pokemonView.rotation = 0f
                    // Arbok rozevře kapuci při přistání — hrozivé
                    spawnArbokHiss()
                    if (running) {
                        startIdleAnimation()
                        scheduleStep(Random.nextLong(1000, 3500))
                    }
                }
            })
            start()
        }
    }

    private fun arbokClickReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()
        idleAnim?.cancel()

        val grandParent = parent.parent as? ViewGroup

        // Glare + Acid — Arbok zírá a vystříkne kyselinu
        // 1. Nabíjení — Arbok se vztyčí a rozevře kapuci
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pokemonView, "scaleX",
                    pokemonView.scaleX, pokemonView.scaleX * 1.3f
                ).apply { duration = 300 },
                ObjectAnimator.ofFloat(pokemonView, "scaleY",
                    baseScale, baseScale * 1.3f
                ).apply { duration = 300 },
                ObjectAnimator.ofFloat(pokemonView, "translationY",
                    targetTranslationY, targetTranslationY - 15f * dp
                ).apply { duration = 300 }
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (!running) return

                    // 2. Glare — červené oči záblesk (strach efekt)
                    val cx = pokemonView.x + pokemonView.width / 2f
                    val cy = pokemonView.y + pokemonView.translationY + pokemonView.height * 0.2f

                    repeat(2) { eye ->
                        val eyeFlash = View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.parseColor("#B71C1C"))
                            }
                            val sz = (8 * dp).toInt()
                            layoutParams = ViewGroup.LayoutParams(sz, sz)
                            x = cx + (if (eye == 0) -10f else 10f) * dp
                            y = cy; alpha = 0f
                        }
                        parent.addView(eyeFlash)
                        eyeFlash.animate().alpha(1f).setDuration(100)
                            .withEndAction {
                                eyeFlash.animate().alpha(0f).scaleX(3f).scaleY(3f)
                                    .setDuration(400)
                                    .withEndAction { parent.removeView(eyeFlash) }
                                    .start()
                            }.start()
                    }

                    // 3. Acid — fialová kyselina ve velkém oblouku
                    repeat(10) { i ->
                        handler.postDelayed({
                            if (!running) return@postDelayed
                            val acid = View(context).apply {
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setColor(
                                        if (i % 2 == 0) Color.parseColor("#7B1FA2")
                                        else Color.parseColor("#558B2F")
                                    )
                                }
                                val sz = (Random.nextInt(6, 12) * dp).toInt()
                                layoutParams = ViewGroup.LayoutParams(sz, sz)
                                x = cx; y = cy; alpha = 0.9f
                            }
                            parent.addView(acid)
                            val angle = (i * 36f) - 90f
                            val dist = (40f + Random.nextFloat() * 30f) * dp
                            acid.animate()
                                .translationXBy(dist * cos(Math.toRadians(angle.toDouble())).toFloat())
                                .translationYBy(dist * sin(Math.toRadians(angle.toDouble())).toFloat())
                                .alpha(0f).scaleX(2f).scaleY(2f)
                                .setDuration(700)
                                .withEndAction { parent.removeView(acid) }
                                .start()
                        }, i * 50L)
                    }

                    // Otřes lišty
                    grandParent?.let {
                        ObjectAnimator.ofFloat(it, "translationX",
                            0f, -10f * dp, 10f * dp, -6f * dp, 6f * dp, 0f
                        ).apply { duration = 400; start() }
                    }

                    // 4. Návrat
                    handler.postDelayed({
                        if (!running) return@postDelayed
                        pokemonView.animate()
                            .scaleX(if (facingRight) -baseScale else baseScale)
                            .scaleY(baseScale)
                            .translationY(targetTranslationY)
                            .setDuration(300)
                            .withEndAction {
                                if (running) {
                                    startIdleAnimation()
                                    scheduleStep(Random.nextLong(1500, 3500))
                                }
                            }.start()
                    }, 600L)
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


    private fun startDittoIdle(): Animator {
        // Ditto se plynule roztahuje do stran a zase smršťuje
        val stretch = ObjectAnimator.ofPropertyValuesHolder(
            pokemonView,
            PropertyValuesHolder.ofFloat("scaleX", baseScale * 1.1f, baseScale * 0.9f, baseScale * 1.1f),
            PropertyValuesHolder.ofFloat("scaleY", baseScale * 0.9f, baseScale * 1.1f, baseScale * 0.9f)
        ).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        scheduleDittoSlimeDrip()
        stretch.start()
        return stretch
    }

    private fun scheduleDittoSlimeDrip() {
        if (!running || pokemonId != "132") return
        handler.postDelayed({
            if (running && pokemonId == "132") {
                spawnSlimeBubble()
                scheduleDittoSlimeDrip()
            }
        }, Random.nextLong(2500, 5000))
    }

    private fun spawnSlimeBubble() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        val bubble = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EA91E5")) // Ditto růžová
            }
            layoutParams = ViewGroup.LayoutParams((6 * dp).toInt(), (6 * dp).toInt())
            x = pokemonView.x + (Random.nextFloat() * pokemonView.width)
            y = pokemonView.y + pokemonView.height - (10 * dp)
            alpha = 0.8f
        }
        parent.addView(bubble)
        bubble.animate()
            .translationYBy(15 * dp)
            .scaleX(1.5f).scaleY(0.2f)
            .alpha(0f)
            .setDuration(1000)
            .withEndAction { parent.removeView(bubble) }.start()
    }


    private fun crossingDitto(fromX: Float, toX: Float) {
        idleAnim?.cancel()
        val dur = 1500L

        // 1. Splácnutí před startem
        pokemonView.animate()
            .scaleY(baseScale * 0.4f)
            .scaleX(baseScale * 1.5f)
            .setDuration(300)
            .withEndAction {
                facingRight = toX > fromX
                // 2. Klouzavý pohyb
                val moveAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = dur
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        val p = anim.animatedValue as Float
                        pokemonView.x = fromX + (toX - fromX) * p

                        // Jemné vlnění horní části během pohybu
                        pokemonView.scaleY = baseScale * (0.4f + sin(p * PI.toFloat() * 5f) * 0.1f)
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: Animator) {
                            // 3. Narovnání se zpět
                            pokemonView.animate()
                                .scaleX(baseScale).scaleY(baseScale)
                                .setDuration(300)
                                .withEndAction {
                                    if (running) {
                                        startIdleAnimation()
                                        scheduleStep(Random.nextLong(1000, 3000))
                                    }
                                }.start()
                        }
                    })
                }
                moveAnim.start()
            }.start()
    }

    private fun dittoTapReaction() {
        val parent = pokemonView.parent as? ViewGroup ?: return
        handler.removeCallbacksAndMessages(null)
        moveAnim?.cancel()
        wobbleAnim?.cancel()

        // Animace pokusu o transformaci
        val transform = AnimatorSet().apply {
            playTogether(
                // Zbělá jako při transformaci
                ValueAnimator.ofObject(ArgbEvaluator(), Color.WHITE, Color.parseColor("#EA91E5")).apply {
                    duration = 600
                    addUpdateListener { pokemonView.setColorFilter(it.animatedValue as Int, PorterDuff.Mode.SRC_ATOP) }
                },
                // Změna tvaru (vyskočení a deformace)
                ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY, targetTranslationY - 40 * dp, targetTranslationY).apply {
                    duration = 500
                },
                ObjectAnimator.ofFloat(pokemonView, "scaleX", baseScale, baseScale * 0.5f, baseScale * 1.4f, baseScale).apply {
                    duration = 600
                }
            )
        }

        transform.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) {
                pokemonView.clearColorFilter()
                if (running) {
                    startWobble()
                    startIdleAnimation()
                    scheduleStep(Random.nextLong(2000, 4000))
                }
            }
        })
        transform.start()
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