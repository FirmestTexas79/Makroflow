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

/**
 * Hodnoty třesení vůči AKTUÁLNÍ poloze: dřív animace šly z translationX = 0, takže
 * Makromon uprostřed procházky po klepnutí (nebo při idle) skočil zpátky na výchozí místo.
 */
internal fun relTx(v: android.view.View, vararg d: Float): FloatArray { val base = v.translationX; return FloatArray(d.size) { base + d[it] } }

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
                    ObjectAnimator.ofFloat(it, "translationX", *relTx(it, 0f, -12f * dp, 12f * dp, -6f * dp, 6f * dp, 0f))
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

    /**
     * Generace idle stavu. Každý nový idle (i každá chůze/přechod) ji zvýší a všechny
     * smyčky částic (bubliny, jiskry, kapky…) z předchozí generace se samy ukončí.
     *
     * Dřív se při každém startu idle spustila NOVÁ nekonečná smyčka částic a staré běžely
     * dál – po pár procházkách jich běžely desítky (Axluovy bubliny „milion za sekundu“).
     */
    private var idleGeneration = 0

    private fun cancelIdle() {
        idleAnim?.cancel()
        idleGeneration++
    }

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
        "033", "045", "047" -> 3000L  // Mysnor, Krystalos, Magmor – těžcí
        "043"        -> 2900L  // Kendl – cupitá jako svíčka
        "042"        -> 2200L  // Hunfrey – sebevědomý krok
        else         -> 2500L
    }

    // Jak dlouho čeká na místě před dalším krokem
    private val idleWaitRange: Pair<Long, Long> get() = when (pokemonId) {
        "030"        -> 8000L to 16000L  // Gudwin hodně sedí (a spí)
        "022","023"  -> 800L  to 2000L   // Mycit/Mydrus – nervózní, neposedí
        "012"        -> 1500L to 3500L   // Spirra – živá
        "010","011"  -> 2000L to 5000L   // Kuličky – klidné
        "031"        -> 1000L to 3000L   // Axlu – zvědavý
        "033", "045", "047" -> 4000L to 9000L  // těžcí – rádi stojí
        "042"        -> 2500L to 5000L   // Hunfrey – plánuje
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
                applyFacing(facingRight)    // efekty příchodu nastavují scaleX bez ohledu na směr
                startIdleAnimation()
                if (!isFlying && !isSleeping) startWobble()
                scheduleStep(Random.nextLong(idleWaitRange.first, idleWaitRange.second))
            }
        }
    }

    override fun stop() {
        running = false
        isCrossing = false
        afterWalk = null
        idleGeneration++
        crossAnim?.cancel()
        moveAnim?.cancel()
        wobbleAnim?.cancel()
        idleAnim?.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    // ─────────────────────────────────────────
    // GEOMETRIE LIŠTY – tlačítko uprostřed je překážka
    // ─────────────────────────────────────────

    /** Kruhové tlačítko (fabHome) v souřadnicích rodiče Makromona. */
    private data class Obstacle(val left: Float, val right: Float, val top: Float, val cx: Float, val cy: Float, val r: Float)

    private fun obstacle(): Obstacle {
        val parent = pokemonView.parent as? ViewGroup
        val fab = pokemonView.rootView.findViewById<View>(cz.uhk.macroflow.R.id.fabHome)
        if (parent != null && fab != null && fab.width > 0 && fab.visibility == View.VISIBLE) {
            val p = IntArray(2); val f = IntArray(2)
            parent.getLocationInWindow(p); fab.getLocationInWindow(f)
            val left = (f[0] - p[0]).toFloat(); val top = (f[1] - p[1]).toFloat()
            val r = fab.width / 2f
            return Obstacle(left, left + fab.width, top, left + r, top + r, r)
        }
        // Záloha: tlačítko 68 dp uprostřed, 20 dp nad zemí Makromona
        val w = parent?.width?.toFloat() ?: (360f * dp)
        val r = 34f * dp
        val top = groundFeetY() - 20f * dp
        return Obstacle(w / 2 - r, w / 2 + r, top, w / 2, top + r, r)
    }

    private fun fabView(): View? = pokemonView.rootView.findViewById(cz.uhk.macroflow.R.id.fabHome)

    /** Kde stojí nohy Makromona v klidu (y v souřadnicích rodiče). */
    private fun groundFeetY(): Float = pokemonView.top + pokemonView.height + targetTranslationY

    /** Poloviční „viditelná“ šířka (sprity mají průhledný okraj, proto 0,7). */
    private val halfW get() = pokemonView.width * abs(baseScale) / 2f * 0.7f

    private fun feetX(): Float = pokemonView.x + pokemonView.width / 2f

    /** Nastaví polohu podle nohou (střed dole). */
    private fun placeFeet(fx: Float, fy: Float) {
        pokemonView.x = fx - pokemonView.width / 2f
        pokemonView.translationY = fy - pokemonView.top - pokemonView.height
    }

    /** Rozsah pozic (x nohou) na levé / pravé straně od tlačítka. */
    private fun sideRange(left: Boolean, o: Obstacle, parentW: Float): ClosedFloatingPointRange<Float> {
        val gap = 6f * dp
        return if (left) {
            val min = halfW + gap; val max = o.left - gap - halfW
            min..max.coerceAtLeast(min)
        } else {
            val min = o.right + gap + halfW; val max = parentW - gap - halfW
            min..max.coerceAtLeast(min)
        }
    }

    /** Jak rád chodí na druhou stranu (povaha). */
    private val crossChance: Float get() = when (pokemonId) {
        "030" -> 0.15f                  // Gudwin – líný
        "012", "031" -> 0.45f           // Spirra, Axlu – zvědaví
        "022", "023" -> 0.40f           // Mycit – nervózní, ale zvědavý
        "019", "003" -> 0.50f           // letci to mají nejsnazší
        "044" -> 0.45f                  // Gemak – rád se kutálí přes tlačítko
        "033", "045", "047" -> 0.18f    // těžcí – líní
        else -> 0.30f
    }

    // ─────────────────────────────────────────
    // WANDERING – pohyb po liště
    // ─────────────────────────────────────────

    /** Co udělat po doběhnutí chůze (např. přejít tlačítko); null = klid a další krok. */
    private var afterWalk: (() -> Unit)? = null

    /**
     * Další krok: buď procházka po své straně, nebo (s pravděpodobností podle povahy)
     * dojít k tlačítku, chvilku si ho prohlédnout a přejít na druhou stranu svým stylem.
     */
    private fun scheduleStep(delayMs: Long) {
        if (!running) return
        handler.postDelayed({
            if (!running || isCrossing) return@postDelayed
            val parentW = (pokemonView.parent as? ViewGroup)?.width?.toFloat() ?: return@postDelayed
            val o = obstacle()
            val fx = feetX()
            val onLeft = fx < o.cx
            val here = sideRange(onLeft, o, parentW)

            if (Random.nextFloat() < crossChance) {
                // K tlačítku, prohlédnout si ho, přejít
                val edge = if (onLeft) here.endInclusive else here.start
                val target = if (onLeft) sideRange(false, o, parentW).start else sideRange(true, o, parentW).endInclusive
                val cross: () -> Unit = {
                    // idle (dýchání) si drží původní směr – nejdřív ho zastavit, jinak se otočí zpátky
                    cancelIdle(); stopWobble()
                    facingRight = onLeft; applyFacing(facingRight)
                    handler.postDelayed({
                        if (running && !isCrossing) {
                            cancelIdle(); stopWobble()
                            isCrossing = true
                            playCrossingAnimation(feetX(), target, o)
                        }
                    }, Random.nextLong(300, 900)) // krátké zaváhání před tlačítkem
                }
                if (abs(fx - edge) < 12f * dp) cross()
                else { afterWalk = cross; walkTo(fx, edge) }
            } else {
                val target = pickWalkTarget(here)
                if (abs(fx - target) < 20f * dp) {
                    // Už je na místě – rozhlédne se a počká
                    facingRight = !facingRight
                    applyFacing(facingRight)
                    startIdleAnimation()        // nové idle už s novým směrem (staré by ho otočilo zpět)
                    if (!isFlying && !isSleeping) startWobble()
                    scheduleStep(randomIdle())
                } else walkTo(fx, target)
            }
        }, delayMs)
    }

    private fun randomIdle() = Random.nextLong(idleWaitRange.first, idleWaitRange.second)

    private fun walkTo(fromFeetX: Float, toFeetX: Float) {
        cancelIdle(); stopWobble()
        facingRight = toFeetX > fromFeetX
        applyFacing(facingRight)
        val dx = pokemonView.width / 2f
        performWalk(fromFeetX - dx, toFeetX - dx)
    }

    /** Společný konec všech stylů chůze. */
    private fun onWalkEnd() {
        if (!running || isCrossing) return
        val next = afterWalk
        afterWalk = null
        if (next != null) next() else { startIdleAnimation(); scheduleStep(randomIdle()) }
    }

    /** Cíl procházky na vlastní straně – délka kroku podle povahy. */
    private fun pickWalkTarget(range: ClosedFloatingPointRange<Float>): Float {
        val span = range.endInclusive - range.start
        val reach = when (pokemonId) {
            "030" -> 0.35f             // Gudwin – krátké přesuny
            "022", "023" -> 0.45f      // Mycit – cupitá
            "010", "011" -> 0.5f       // kuličky – klidné
            "012", "031", "019" -> 1f  // zvědaví a letci – po celé straně
            else -> 0.75f
        }
        val cur = feetX().coerceIn(range.start, range.endInclusive)
        val t = cur + (Random.nextFloat() * 2f - 1f) * span * reach
        return t.coerceIn(range.start, range.endInclusive)
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

            // Noví Makromoni (docs/adr/0084)
            "032" -> walkNervous(fromX, toX)                   // Mysnic – cupitá
            "033", "045", "047" -> walkHeavy(fromX, toX)       // Mysnor, Krystalos, Magmor – dupou
            "041" -> walkFloat(fromX, toX)                     // Psychirra – levituje
            "042" -> walkBouncy(fromX, toX, bounceHeight = 4f) // Hunfrey – klidný sebejistý krok
            "043" -> walkBouncy(fromX, toX, bounceHeight = 3f) // Kendl – svíčka poskakuje
            "044" -> walkBouncy(fromX, toX, bounceHeight = 5f) // Gemak – batolí se

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
                    onWalkEnd()
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
                    onWalkEnd()
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
                    onWalkEnd()
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
                    onWalkEnd()
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
                    onWalkEnd()
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
                        } else onWalkEnd()
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
                    onWalkEnd()
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
                    onWalkEnd()
                }
            })
            start()
        }
    }

    // ─────────────────────────────────────────
    // CROSSING – přechod přes tlačítko uprostřed (každý po svém)
    // ─────────────────────────────────────────

    private var crossAnim: Animator? = null

    private fun playCrossingAnimation(fromFx: Float, toFx: Float, o: Obstacle) {
        when (pokemonId) {
            "001", "002" -> crossJump(fromFx, toFx, o, spin = false, embers = true)   // Ignar – ohnivý skok
            "013" -> crossJump(fromFx, toFx, o, spin = true, embers = true)           // Flamirra – salto s plamenem
            "003" -> crossFly(fromFx, toFx, o)                                        // Ignaroth – přelet s máváním
            "019" -> crossLoop(fromFx, toFx, o)                                       // Drakirra – smyčka kolem tlačítka
            "004", "005", "006" -> crossSurf(fromFx, toFx, o)                         // Aqulin – surf na vlně
            "014" -> crossGeyser(fromFx, toFx, o)                                     // Aquirra – vyhodí ji gejzír
            "020", "021" -> crossDolphin(fromFx, toFx, o)                             // Finlet/Serpfin – delfíní skok
            "007", "008", "009" -> crossClimb(fromFx, toFx, o, heavy = false)         // Flori – přeleze po povrchu
            "015" -> crossSpring(fromFx, toFx, o)                                     // Verdirra – pružinový skok
            "010", "024", "025", "026" -> crossPhase(fromFx, toFx, o)                 // Umbex, Soulu – projdou skrz
            "016" -> crossShadowSink(fromFx, toFx, o)                                 // Shadirra – stínem pod tlačítkem
            "011", "017", "027", "028", "029" -> crossHover(fromFx, toFx, o)          // Lumex, Charmirra, Phantil – plují nad
            "012" -> crossPerch(fromFx, toFx, o)                                      // Spirra – vyskočí nahoru a rozhlédne se
            "018" -> crossIceSlide(fromFx, toFx, o)                                   // Glacirra – vyšplhá a sklouzne
            "022", "023" -> crossTrampoline(fromFx, toFx, o)                          // Mycit – trampolína
            "030" -> crossClimb(fromFx, toFx, o, heavy = true)                        // Gudwin – těžce přeleze
            "031" -> crossBubble(fromFx, toFx, o)                                     // Axlu – přeletí v bublině
            "032" -> crossClimb(fromFx, toFx, o, heavy = false)                       // Mysnic – přeleze
            "033", "045", "047" -> crossClimb(fromFx, toFx, o, heavy = true)          // těžcí – přelezou, tlačítko se otřese
            "041" -> crossHover(fromFx, toFx, o)                                      // Psychirra – přepluje silou mysli
            "042" -> crossPerch(fromFx, toFx, o)                                      // Hunfrey – vyleze nahoru a rozhlédne se
            "043" -> crossPhase(fromFx, toFx, o)                                      // Kendl – projde skrz jako přízrak
            "044" -> crossRoll(fromFx, toFx, o)                                       // Gemak – stočí se do klubka a přejede
            else -> crossJump(fromFx, toFx, o, spin = false, embers = false)
        }
    }

    private fun onCrossingDone() {
        isCrossing = false
        crossAnim = null
        pokemonView.rotation = 0f
        pokemonView.alpha = 1f
        pokemonView.translationY = targetTranslationY
        applyFacing(facingRight)
        if (running) {
            startIdleAnimation()
            if (!isFlying && !isSleeping) startWobble()
            scheduleStep(randomIdle())
        }
    }

    /** Výška nad zemí, ve které mají být nohy nad vrcholem tlačítka. */
    private fun clearance(o: Obstacle, extraDp: Float = 10f) =
        (groundFeetY() - o.top).coerceAtLeast(0f) + extraDp * dp

    /** Výška povrchu tlačítka pod danou x-ovou nohou (0 mimo tlačítko). */
    private fun surfaceLift(fx: Float, o: Obstacle): Float {
        val d = fx - o.cx
        if (abs(d) >= o.r) return 0f
        val surfaceY = o.cy - sqrt(o.r * o.r - d * d)
        return (groundFeetY() - surfaceY).coerceAtLeast(0f)
    }

    private fun path(duration: Long, interp: TimeInterpolator, update: (Float) -> Unit, end: () -> Unit) {
        crossAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration; interpolator = interp
            addUpdateListener { update(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(a: Animator) { cancelled = true }
                override fun onAnimationEnd(a: Animator) { if (!cancelled && running) end() }
            })
            start()
        }
    }

    /** Stisk tlačítka – když na něj Makromon doskočí nebo šlápne. */
    private fun pokeFab(strength: Float = 1f) {
        val fab = fabView() ?: return
        fab.animate().cancel()
        fab.animate().scaleY(1f - 0.12f * strength).scaleX(1f + 0.06f * strength).setDuration(90).withEndAction {
            fab.animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(OvershootInterpolator(3f)).start()
        }.start()
    }

    private fun squash(then: () -> Unit, amount: Float = 0.8f) {
        val sx = if (facingRight) -baseScale else baseScale
        pokemonView.animate().scaleY(baseScale * amount).scaleX(sx * (2f - amount)).setDuration(140).withEndAction {
            pokemonView.animate().scaleY(baseScale).scaleX(sx).setDuration(90).withEndAction { if (running) then() }.start()
        }.start()
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    // ── Styly ────────────────────────────────

    /** Odraz a skok obloukem nad tlačítko (volitelně salto a jiskry). */
    private fun crossJump(fromFx: Float, toFx: Float, o: Obstacle, spin: Boolean, embers: Boolean) {
        val lift = clearance(o, 16f)
        val g = groundFeetY()
        squash({
            var lastEmber = 0L
            path(walkDuration * 5 / 10, LinearInterpolator(), { p ->
                placeFeet(lerp(fromFx, toFx, p), g - lift * 4f * p * (1 - p))
                if (spin) pokemonView.rotation = (if (facingRight) 360f else -360f) * p
                val now = System.currentTimeMillis()
                if (embers && now - lastEmber > 60) { lastEmber = now; spawnEmberTrail(feetX() - 3 * dp, pokemonView.y + pokemonView.height * 0.8f) }
            }) { squash({ onCrossingDone() }, 0.85f) }
        })
    }

    /** Přelet s máváním – náklon podle směru letu. */
    private fun crossFly(fromFx: Float, toFx: Float, o: Obstacle) {
        val lift = clearance(o, 40f)
        val g = groundFeetY()
        path(walkDuration, AccelerateDecelerateInterpolator(), { p ->
            placeFeet(lerp(fromFx, toFx, p), g - lift * sin(PI.toFloat() * p))
            pokemonView.rotation = cos(p * PI.toFloat()) * (if (facingRight) -12f else 12f) + sin(p * 40f) * 4f
        }) { onCrossingDone() }
    }

    /** Drakirra: vzlétne a udělá celou smyčku kolem tlačítka, pak přistane. */
    private fun crossLoop(fromFx: Float, toFx: Float, o: Obstacle) {
        val g = groundFeetY()
        val loopR = o.r + 46f * dp
        val dir = if (facingRight) 1f else -1f
        path(walkDuration + 1400L, AccelerateDecelerateInterpolator(), { p ->
            when {
                p < 0.25f -> { // nálet nad tlačítko
                    val t = p / 0.25f
                    placeFeet(lerp(fromFx, o.cx, t), lerp(g, o.cy - loopR, t))
                }
                p < 0.75f -> { // smyčka kolem středu tlačítka
                    val a = (-PI / 2 + dir * 2 * PI * (p - 0.25f) / 0.5f).toFloat()
                    placeFeet(o.cx + cos(a) * loopR, o.cy + sin(a) * loopR)
                    pokemonView.rotation = dir * 360f * (p - 0.25f) / 0.5f
                }
                else -> { // přistání
                    val t = (p - 0.75f) / 0.25f
                    pokemonView.rotation = 0f
                    placeFeet(lerp(o.cx, toFx, t), lerp(o.cy - loopR, g, t))
                }
            }
        }) { onCrossingDone() }
    }

    /** Aqulin: pod ním se zvedne vlna a přenese ho přes tlačítko. */
    private fun crossSurf(fromFx: Float, toFx: Float, o: Obstacle) {
        val parent = pokemonView.parent as? ViewGroup ?: run { onCrossingDone(); return }
        val waveW = (pokemonView.width * 1.6f).toInt()
        val waveH = (30 * dp).toInt()
        val wave = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#0288D1")); setStroke((2 * dp).toInt(), Color.WHITE) }
            layoutParams = ViewGroup.LayoutParams(waveW, waveH); alpha = 0f
            elevation = pokemonView.elevation - 1f
        }
        parent.addView(wave)
        val lift = clearance(o, 22f)
        val g = groundFeetY()
        var lastDrop = 0L
        path(walkDuration + 600L, AccelerateDecelerateInterpolator(), { p ->
            val fy = g - lift * sin(PI.toFloat() * p)
            placeFeet(lerp(fromFx, toFx, p), fy)
            wave.x = feetX() - waveW / 2f; wave.y = fy - waveH * 0.45f
            wave.alpha = (sin(PI.toFloat() * p) * 1.6f).coerceIn(0f, 0.9f)
            wave.scaleX = 0.8f + 0.2f * sin(p * 20f)
            val now = System.currentTimeMillis()
            if (now - lastDrop > 90) { lastDrop = now; spawnDrop(parent, feetX() + (if (facingRight) -1 else 1) * waveW / 2.5f, fy) }
        }) {
            wave.animate().alpha(0f).setDuration(250).withEndAction { parent.removeView(wave) }.start()
            onCrossingDone()
        }
    }

    /** Aquirra: gejzír u tlačítka ji vystřelí nahoru, dopadne na druhou stranu. */
    private fun crossGeyser(fromFx: Float, toFx: Float, o: Obstacle) {
        val parent = pokemonView.parent as? ViewGroup ?: run { onCrossingDone(); return }
        repeat(10) { i -> handler.postDelayed({ if (running) spawnDrop(parent, fromFx + (Random.nextFloat() - 0.5f) * 20 * dp, groundFeetY(), up = 70f) }, i * 30L) }
        crossJump(fromFx, toFx, o, spin = false, embers = false)
    }

    /** Finlet/Serpfin: delfíní skok – tělo se natáčí podle dráhy, šplouchnutí na začátku i na konci. */
    private fun crossDolphin(fromFx: Float, toFx: Float, o: Obstacle) {
        val parent = pokemonView.parent as? ViewGroup
        val lift = clearance(o, 26f)
        val g = groundFeetY()
        parent?.let { splash(it, fromFx, g) }
        val dir = if (toFx > fromFx) 1f else -1f
        path(walkDuration * 6 / 10, LinearInterpolator(), { p ->
            placeFeet(lerp(fromFx, toFx, p), g - lift * 4f * p * (1 - p))
            // natočení podle tečny paraboly
            val slope = lift * 4f * (1 - 2 * p) / abs(toFx - fromFx)
            pokemonView.rotation = -dir * Math.toDegrees(atan(slope.toDouble())).toFloat() * 0.8f
        }) {
            parent?.let { splash(it, toFx, g) }
            onCrossingDone()
        }
    }

    /** Flori / Gudwin: přeleze po povrchu tlačítka (sleduje jeho kruhový tvar). */
    private fun crossClimb(fromFx: Float, toFx: Float, o: Obstacle, heavy: Boolean) {
        val g = groundFeetY()
        var poked = false
        path(if (heavy) walkDuration + 1600L else walkDuration + 400L, LinearInterpolator(), { p ->
            val fx = lerp(fromFx, toFx, p)
            val lift = surfaceLift(fx, o)
            placeFeet(fx, g - lift)
            // náklon podle sklonu povrchu
            val d = fx - o.cx
            val tilt = if (abs(d) < o.r && lift > 0f) Math.toDegrees(asin((d / o.r).toDouble())).toFloat() else 0f
            pokemonView.rotation = (if (heavy) 0.5f else 0.7f) * tilt + sin(p * (if (heavy) 18f else 30f)) * (if (heavy) 4f else 2f)
            if (!poked && abs(d) < o.r * 0.15f) { poked = true; pokeFab(if (heavy) 1.6f else 0.7f) }
        }) {
            if (heavy) {
                (pokemonView.parent as? ViewGroup)?.let {
                    ObjectAnimator.ofFloat(it, "translationX", *relTx(it, 0f, -8f * dp, 8f * dp, -4f * dp, 4f * dp, 0f)).apply { duration = 350; start() }
                }
            } else leafSwayReaction()
            onCrossingDone()
        }
    }

    /** Verdirra: hluboký podřep jako pružina a dlouhý vysoký skok s listím. */
    private fun crossSpring(fromFx: Float, toFx: Float, o: Obstacle) {
        val lift = clearance(o, 40f)
        val g = groundFeetY()
        squash({
            path(walkDuration * 7 / 10, DecelerateInterpolator(0.6f), { p ->
                placeFeet(lerp(fromFx, toFx, p), g - lift * 4f * p * (1 - p))
                pokemonView.scaleY = baseScale * (1f + 0.15f * sin(PI.toFloat() * p))
            }) { leafSwayReaction(); squash({ onCrossingDone() }, 0.85f) }
        }, amount = 0.6f)
    }

    /** Duchové: projdou tlačítkem skrz – zprůhlední a rozvlní se. */
    private fun crossPhase(fromFx: Float, toFx: Float, o: Obstacle) {
        val g = groundFeetY()
        val parent = pokemonView.parent as? ViewGroup
        var lastP = 0L
        path(walkDuration + 400L, LinearInterpolator(), { p ->
            placeFeet(lerp(fromFx, toFx, p), g + sin(p * PI.toFloat() * 4) * 6f * dp)
            val insideness = (1f - abs(feetX() - o.cx) / (o.r + halfW)).coerceIn(0f, 1f)
            pokemonView.alpha = 1f - 0.75f * insideness
            val now = System.currentTimeMillis()
            if (parent != null && insideness > 0.3f && now - lastP > 120) { lastP = now; spawnGhostWisp(parent) }
        }) { onCrossingDone() }
    }

    /** Shadirra: propadne se do stínu, stín přejede pod tlačítkem a na druhé straně se vynoří. */
    private fun crossShadowSink(fromFx: Float, toFx: Float, o: Obstacle) {
        val parent = pokemonView.parent as? ViewGroup ?: run { onCrossingDone(); return }
        val g = groundFeetY()
        val shadow = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(170, 40, 10, 60)) }
            layoutParams = ViewGroup.LayoutParams((halfW * 1.6f).toInt(), (10 * dp).toInt())
            x = fromFx - halfW * 0.8f; y = g - 5 * dp; alpha = 0f
        }
        parent.addView(shadow)
        path(walkDuration + 1000L, LinearInterpolator(), { p ->
            when {
                p < 0.25f -> { val t = p / 0.25f; pokemonView.scaleY = baseScale * (1f - t); shadow.alpha = t; placeFeet(fromFx, g) }
                p < 0.75f -> { val t = (p - 0.25f) / 0.5f; pokemonView.scaleY = 0f; shadow.x = lerp(fromFx, toFx, t) - halfW * 0.8f; placeFeet(lerp(fromFx, toFx, t), g) }
                else -> { val t = (p - 0.75f) / 0.25f; pokemonView.scaleY = baseScale * t; shadow.alpha = 1f - t; placeFeet(toFx, g) }
            }
        }) {
            parent.removeView(shadow)
            pokemonView.scaleY = baseScale
            onCrossingDone()
        }
    }

    /** Lumex, Charmirra, Phantil: plynule vyplují nad tlačítko a přes něj (Lumex nahoře zazáří). */
    private fun crossHover(fromFx: Float, toFx: Float, o: Obstacle) {
        val lift = clearance(o, 24f)
        val g = groundFeetY()
        val parent = pokemonView.parent as? ViewGroup
        var flashed = false; var lastS = 0L
        path(walkDuration + 1200L, AccelerateDecelerateInterpolator(), { p ->
            placeFeet(lerp(fromFx, toFx, p), g - lift * sin(PI.toFloat() * p) + sin(p * 12f) * 4f * dp)
            val now = System.currentTimeMillis()
            if (parent != null && now - lastS > 150) { lastS = now; spawnSparkle(parent) }
            if (pokemonId == "011" && !flashed && p > 0.5f) { flashed = true; lumexFlashReaction() }
        }) { onCrossingDone() }
    }

    /** Spirra: vyskočí na tlačítko, sedne si, rozhlédne se na obě strany a seskočí. */
    private fun crossPerch(fromFx: Float, toFx: Float, o: Obstacle) {
        val g = groundFeetY()
        val topLift = g - o.top
        squash({
            path(420L, DecelerateInterpolator(), { p ->
                placeFeet(lerp(fromFx, o.cx, p), g - topLift * p - 26f * dp * sin(PI.toFloat() * p))
            }) {
                pokeFab(0.8f)
                // Rozhlédne se
                handler.postDelayed({ if (running) { facingRight = !facingRight; applyFacing(facingRight) } }, 350)
                handler.postDelayed({ if (running) { facingRight = !facingRight; applyFacing(facingRight) } }, 900)
                handler.postDelayed({
                    if (!running) return@postDelayed
                    facingRight = toFx > o.cx; applyFacing(facingRight)
                    squash({
                        path(420L, AccelerateInterpolator(), { p ->
                            placeFeet(lerp(o.cx, toFx, p), g - topLift * (1 - p) - 20f * dp * sin(PI.toFloat() * p))
                        }) { squash({ onCrossingDone() }, 0.85f) }
                    }, 0.85f)
                }, 1400)
            }
        }, 0.75f)
    }

    /** Glacirra: rychle vyšplhá a po druhé straně sklouzne jako po ledu, za ní střípky. */
    private fun crossIceSlide(fromFx: Float, toFx: Float, o: Obstacle) {
        val g = groundFeetY()
        val parent = pokemonView.parent as? ViewGroup
        var lastShard = 0L
        path(walkDuration, AccelerateInterpolator(1.3f), { p ->
            val fx = lerp(fromFx, toFx, p)
            placeFeet(fx, g - surfaceLift(fx, o))
            val d = fx - o.cx
            pokemonView.rotation = if (d > 0 && abs(d) < o.r) (if (facingRight) 1f else -1f) * 25f * (d / o.r) else 0f
            val now = System.currentTimeMillis()
            if (parent != null && d * (if (facingRight) 1 else -1) > 0 && now - lastShard > 70) { lastShard = now; spawnIceShard(parent) }
        }) { onCrossingDone() }
    }

    /** Mycit: nejdřív couvne, pak skočí na tlačítko a dvakrát se od něj odrazí jako od trampolíny. */
    private fun crossTrampoline(fromFx: Float, toFx: Float, o: Obstacle) {
        val g = groundFeetY()
        val topLift = g - o.top
        val back = fromFx + (if (toFx > fromFx) -1 else 1) * 14f * dp
        // nervózní couvnutí
        path(260L, DecelerateInterpolator(), { p -> placeFeet(lerp(fromFx, back, p), g) }) {
            path(360L, LinearInterpolator(), { p -> placeFeet(lerp(back, o.cx, p), g - topLift * p - 30f * dp * sin(PI.toFloat() * p)) }) {
                pokeFab(1f)
                path(360L, LinearInterpolator(), { p -> placeFeet(o.cx, g - topLift - 40f * dp * sin(PI.toFloat() * p)) }) {
                    pokeFab(1.2f)
                    path(460L, LinearInterpolator(), { p ->
                        placeFeet(lerp(o.cx, toFx, p), g - topLift * (1 - p) - 50f * dp * sin(PI.toFloat() * p))
                        pokemonView.rotation = (if (toFx > o.cx) 1f else -1f) * 360f * p
                    }) { squash({ onCrossingDone() }, 0.85f) }
                }
            }
        }
    }

    /** Axlu: vyfoukne bublinu, ve které přepluje tlačítko, bublina na konci praskne. */
    private fun crossBubble(fromFx: Float, toFx: Float, o: Obstacle) {
        val parent = pokemonView.parent as? ViewGroup ?: run { onCrossingDone(); return }
        val size = (pokemonView.width * abs(baseScale) * 0.95f).toInt()
        val bubble = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(40, 255, 182, 193)); setStroke((2 * dp).toInt(), Color.argb(200, 255, 105, 180)) }
            layoutParams = ViewGroup.LayoutParams(size, size); alpha = 0f; scaleX = 0.2f; scaleY = 0.2f
            elevation = pokemonView.elevation + 1f
        }
        parent.addView(bubble)
        val g = groundFeetY()
        val lift = clearance(o, 30f)
        fun placeBubble() { bubble.x = feetX() - size / 2f; bubble.y = pokemonView.y + pokemonView.height - size * 0.95f }
        placeBubble()
        bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500).setInterpolator(OvershootInterpolator()).withEndAction {
            if (!running) { parent.removeView(bubble); return@withEndAction }
            path(walkDuration + 1500L, AccelerateDecelerateInterpolator(), { p ->
                placeFeet(lerp(fromFx, toFx, p), g - lift * sin(PI.toFloat() * p) + sin(p * 10f) * 5f * dp)
                placeBubble()
                bubble.rotation = p * 90f
            }) {
                bubble.animate().scaleX(1.5f).scaleY(1.5f).alpha(0f).setDuration(220).withEndAction { parent.removeView(bubble) }.start()
                repeat(6) { spawnDrop(parent, feetX() + (Random.nextFloat() - 0.5f) * size, pokemonView.y + pokemonView.height - size / 2f, up = 30f) }
                onCrossingDone()
            }
        }.start()
    }

    // ── Drobné efekty pro přechody ────────────

    private fun spawnDrop(parent: ViewGroup, x0: Float, y0: Float, up: Float = 20f) {
        val s = (4 * dp).toInt()
        val drop = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#29B6F6")) }
            layoutParams = ViewGroup.LayoutParams(s, s); x = x0; y = y0
        }
        parent.addView(drop)
        drop.animate().translationYBy(-up * dp * (0.5f + Random.nextFloat())).translationXBy((Random.nextFloat() - 0.5f) * 20 * dp)
            .alpha(0f).setDuration(600).withEndAction { parent.removeView(drop) }.start()
    }

    private fun splash(parent: ViewGroup, fx: Float, fy: Float) {
        repeat(8) { spawnDrop(parent, fx + (Random.nextFloat() - 0.5f) * 16 * dp, fy, up = 45f) }
    }

    private fun spawnGhostWisp(parent: ViewGroup) {
        val s = (6 * dp).toInt()
        val w = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#CE93D8")) }
            layoutParams = ViewGroup.LayoutParams(s, s)
            x = feetX() + (Random.nextFloat() - 0.5f) * halfW; y = pokemonView.y + pokemonView.height * 0.6f; alpha = 0.7f
        }
        parent.addView(w)
        w.animate().translationYBy(-25f * dp).alpha(0f).setDuration(700).withEndAction { parent.removeView(w) }.start()
    }

    private fun spawnSparkle(parent: ViewGroup) {
        val sp = TextView(context).apply {
            text = "✦"; textSize = 9f; setTextColor(Color.parseColor(if (pokemonId == "011") "#FFD700" else "#F8BBD0"))
            x = feetX() + (Random.nextFloat() - 0.5f) * halfW * 2; y = pokemonView.y + pokemonView.height * 0.8f
        }
        parent.addView(sp)
        sp.animate().translationYBy(18f * dp).alpha(0f).setDuration(600).withEndAction { parent.removeView(sp) }.start()
    }

    private fun spawnIceShard(parent: ViewGroup) {
        val shard = View(context).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#B3E5FC")) }
            layoutParams = ViewGroup.LayoutParams((3 * dp).toInt(), (8 * dp).toInt())
            x = feetX(); y = pokemonView.y + pokemonView.height - 6 * dp; rotation = Random.nextFloat() * 90f
        }
        parent.addView(shard)
        shard.animate().translationXBy((if (facingRight) -1 else 1) * 25f * dp).translationYBy(-10f * dp).alpha(0f)
            .setDuration(450).withEndAction { parent.removeView(shard) }.start()
    }

    // ─────────────────────────────────────────
    // IDLE ANIMACE
    // ─────────────────────────────────────────

    private fun startIdleAnimation() {
        cancelIdle()
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
            "033" -> startStoneIdle(sparkle = null)
            "041" -> startPsychirraIdle()
            "042" -> startHunfreyIdle()
            "043" -> startKendlIdle()
            "044" -> startStoneIdle(sparkle = Color.parseColor("#4DD0E1"))
            "045" -> startStoneIdle(sparkle = Color.parseColor("#4DD0E1"), heavy = true)
            "047" -> startMagmorIdle()
            else  -> startDefaultIdle()
        }
    }

    private fun startIgnarIdle(): Animator {
        val shakeX = ObjectAnimator.ofFloat(pokemonView, "translationX", *relTx(pokemonView, 0f, 3f * dp, -3f * dp, 2f * dp, -2f * dp, 0f)).apply { duration = 800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
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
        val fs = facingSign()
        val pulse = ObjectAnimator.ofFloat(pokemonView, "scaleX", fs * baseScale, fs * baseScale * 1.1f, fs * baseScale).apply { duration = 1400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
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
        val shake = ObjectAnimator.ofFloat(pokemonView, "translationX", *relTx(pokemonView, 0f, 2f * dp, -2f * dp, 1f * dp, -1f * dp, 0f)).apply { duration = 600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
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
        val shiver = ObjectAnimator.ofFloat(pokemonView, "translationX", *relTx(pokemonView, 0f, 1.5f * dp, -1.5f * dp, 1f * dp, -1f * dp, 0f)).apply { duration = 400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        shiver.start(); return shiver
    }

    private fun startDrakirraIdle(): Animator {
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY - 15f * dp, targetTranslationY + 15f * dp).apply { duration = 3000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator() }
        val fs = facingSign()
        val breathe = ObjectAnimator.ofFloat(pokemonView, "scaleX", fs * baseScale, fs * baseScale * 1.06f, fs * baseScale).apply { duration = 1500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
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
        val fs = facingSign()
        val breatheX = ObjectAnimator.ofFloat(pokemonView, "scaleX", fs * baseScale, fs * baseScale * 1.07f, fs * baseScale).apply { duration = 2300; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        val breatheY = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.07f, baseScale).apply { duration = 2300; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        spawnGudwinBubbles()
        return AnimatorSet().apply { playTogether(breatheX, breatheY); start() }
    }

    private fun spawnGudwinBubbles(gen: Int = idleGeneration) {
        if (!running || gen != idleGeneration) return
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
        handler.postDelayed({ spawnGudwinBubbles(gen) }, Random.nextLong(1600, 3200))
    }

    private fun startAxluIdle(): Animator {
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY - 11f * dp, targetTranslationY + 11f * dp).apply {
            duration = 2500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator()
        }
        levitate.start(); scheduleAxluBubble(); return levitate
    }

    private fun scheduleAxluBubble(gen: Int = idleGeneration) {
        if (!running || gen != idleGeneration) return
        handler.postDelayed({
            if (running && gen == idleGeneration) { spawnAxluBubble(); scheduleAxluBubble(gen) }
        }, Random.nextLong(4000, 8000))
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


    // ─────────────────────────────────────────
    // NOVÍ MAKROMONI (docs/adr/0084)
    // ─────────────────────────────────────────

    /** Malá částice: kolečko (nebo obdélník) v barvě, vyletí o [dy] a zmizí. */
    private fun puff(parent: ViewGroup, x0: Float, y0: Float, color: Int, sizeDp: Float, dx: Float, dy: Float, ms: Long, rect: Boolean = false, grow: Float = 1f) {
        val v = View(context).apply {
            background = GradientDrawable().apply { shape = if (rect) GradientDrawable.RECTANGLE else GradientDrawable.OVAL; setColor(color) }
            layoutParams = ViewGroup.LayoutParams((sizeDp * dp).toInt(), ((if (rect) sizeDp * 2.5f else sizeDp) * dp).toInt())
            x = x0; y = y0; if (rect) rotation = Random.nextFloat() * 360f
        }
        parent.addView(v)
        v.animate().translationXBy(dx * dp).translationYBy(dy * dp).scaleX(grow).scaleY(grow).alpha(0f)
            .setDuration(ms).withEndAction { parent.removeView(v) }.start()
    }

    /** Opakované částice během klidu (dokud běží tahle idle animace). */
    private fun scheduleIdleFx(gen: Int = idleGeneration, minMs: Long, maxMs: Long, fx: (ViewGroup) -> Unit) {
        if (!running || gen != idleGeneration) return
        (pokemonView.parent as? ViewGroup)?.let(fx)
        handler.postDelayed({ scheduleIdleFx(gen, minMs, maxMs, fx) }, Random.nextLong(minMs, maxMs))
    }

    /** Mysnor / Gemak / Krystalos: pomalé dýchání pod krunýřem, krystaly občas zajiskří. */
    private fun startStoneIdle(sparkle: Int?, heavy: Boolean = false): Animator {
        val d = if (heavy) 2600L else 2000L
        val breathe = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.03f, baseScale).apply { duration = d; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        if (sparkle != null) scheduleIdleFx(minMs = 900, maxMs = 2200) { p ->
            val sp = TextView(context).apply {
                text = "✦"; textSize = 9f; setTextColor(if (Random.nextInt(3) == 0) Color.parseColor("#FF9E40") else sparkle)
                x = pokemonView.x + pokemonView.width * (0.2f + Random.nextFloat() * 0.6f); y = pokemonView.y + pokemonView.height * (0.15f + Random.nextFloat() * 0.3f); alpha = 0f
            }
            p.addView(sp)
            sp.animate().alpha(1f).scaleX(1.4f).scaleY(1.4f).setDuration(250).withEndAction {
                sp.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(350).withEndAction { p.removeView(sp) }.start()
            }.start()
        }
        breathe.start(); return breathe
    }

    /** Psychirra: vznáší se, pomalu se točí spirála energie (fialové a růžové jiskry). */
    private fun startPsychirraIdle(): Animator {
        val levitate = ObjectAnimator.ofFloat(pokemonView, "translationY", targetTranslationY - 10f * dp, targetTranslationY - 2f * dp).apply { duration = 1800; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator() }
        val tilt = ObjectAnimator.ofFloat(pokemonView, "rotation", -3f, 3f).apply { duration = 2600; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator() }
        var a = 0.0
        scheduleIdleFx(minMs = 160, maxMs = 260) { p ->
            a += 0.9
            val r = pokemonView.width * 0.42f
            val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height * 0.5f
            puff(p, cx + cos(a).toFloat() * r, cy + sin(a).toFloat() * r * 0.5f, if (Random.nextBoolean()) Color.parseColor("#C86BE8") else Color.parseColor("#FF6EC7"), 4f, 0f, -14f, 700)
        }
        return AnimatorSet().apply { playTogether(levitate, tilt); start() }
    }

    /** Hunfrey: stojí v klidu a občas si prohlédne kartu – otočí se a nad hlavou mu problikne karta. */
    private fun startHunfreyIdle(): Animator {
        val breathe = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.03f, baseScale).apply { duration = 2200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        scheduleIdleFx(minMs = 3500, maxMs = 7000) { p ->
            val card = View(context).apply {
                background = GradientDrawable().apply { cornerRadius = 2 * dp; setColor(Color.parseColor("#26A69A")); setStroke((1.5f * dp).toInt(), Color.parseColor("#FFF8E1")) }
                layoutParams = ViewGroup.LayoutParams((10 * dp).toInt(), (14 * dp).toInt())
                x = pokemonView.x + pokemonView.width * (if (facingRight) 0.75f else 0.15f); y = pokemonView.y + pokemonView.height * 0.2f; alpha = 0f
            }
            p.addView(card)
            card.animate().alpha(1f).translationYBy(-14f * dp).rotationY(360f).setDuration(700).withEndAction {
                card.animate().alpha(0f).setDuration(300).withEndAction { p.removeView(card) }.start()
            }.start()
        }
        breathe.start(); return breathe
    }

    /** Kendl: plamínek se třese (svíčka se natahuje a smršťuje), stoupá modrý dým. */
    private fun startKendlIdle(): Animator {
        val flicker = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.05f, baseScale * 0.98f, baseScale * 1.03f, baseScale).apply { duration = 900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        val glow = ObjectAnimator.ofFloat(pokemonView, "alpha", 1f, 0.88f, 1f).apply { duration = 1300; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        scheduleIdleFx(minMs = 250, maxMs = 550) { p ->
            val cx = pokemonView.x + pokemonView.width / 2f
            puff(p, cx + (Random.nextFloat() - 0.5f) * 8f * dp, pokemonView.y + pokemonView.height * 0.08f,
                if (Random.nextBoolean()) Color.parseColor("#6FC3FF") else Color.parseColor("#B388FF"), 4f, (Random.nextFloat() - 0.5f) * 10f, -22f, 650)
        }
        return AnimatorSet().apply { playTogether(flicker, glow); start() }
    }

    /** Magmor: sopka na zádech dýmá, občas vyletí jiskra; tělo pomalu „žhne“. */
    private fun startMagmorIdle(): Animator {
        val breathe = ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.04f, baseScale).apply { duration = 2400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
        scheduleIdleFx(minMs = 300, maxMs = 700) { p ->
            val vx = pokemonView.x + pokemonView.width * (if (facingRight) 0.3f else 0.7f)
            val vy = pokemonView.y + pokemonView.height * 0.05f
            if (Random.nextInt(4) == 0) puff(p, vx, vy, if (Random.nextBoolean()) Color.parseColor("#FF6D00") else Color.parseColor("#FFD600"), 3f, (Random.nextFloat() - 0.5f) * 16f, -26f, 600)
            else puff(p, vx, vy, Color.parseColor("#80757575"), 7f, (if (facingRight) 8f else -8f), -24f, 1100, grow = 2.2f)
        }
        breathe.start(); return breathe
    }

    /** Gemak: stočí se do klubka a přejede přes tlačítko – kutálí se po jeho povrchu. */
    private fun crossRoll(fromFx: Float, toFx: Float, o: Obstacle) {
        val g = groundFeetY()
        val dir = if (toFx > fromFx) 1f else -1f
        squash({
            path(walkDuration, LinearInterpolator(), { p ->
                val fx = lerp(fromFx, toFx, p)
                placeFeet(fx, g - surfaceLift(fx, o))
                pokemonView.rotation = dir * 720f * p
                pokemonView.scaleY = baseScale * 0.8f
            }) {
                pokemonView.rotation = 0f; pokemonView.scaleY = baseScale
                (pokemonView.parent as? ViewGroup)?.let { p -> repeat(5) { puff(p, feetX(), groundFeetY() - 4 * dp, Color.parseColor("#4DD0E1"), 3f, (Random.nextFloat() - 0.5f) * 40f, -20f, 450, rect = true) } }
                onCrossingDone()
            }
        }, amount = 0.7f)
    }

    /** Mysnic / Mysnor / Gemak: schová se pod krunýř (přikrčí se), chvíli počká a vykoukne. */
    private fun shellTuckReaction(sparkle: Int?) {
        stopWobble(); moveAnim?.cancel()
        val fs = facingSign()
        pokemonView.animate().scaleY(baseScale * 0.72f).scaleX(fs * baseScale * 1.1f).translationY(targetTranslationY + 6f * dp).setDuration(120).withEndAction {
            handler.postDelayed({
                pokemonView.animate().scaleY(baseScale).scaleX(fs * baseScale).translationY(targetTranslationY).setDuration(260)
                    .setInterpolator(OvershootInterpolator()).withEndAction { if (running) startWobble() }.start()
            }, 700)
        }.start()
        val parent = pokemonView.parent as? ViewGroup ?: return
        repeat(6) { puff(parent, feetX() + (Random.nextFloat() - 0.5f) * 30f * dp, groundFeetY() - 4 * dp, sparkle ?: Color.parseColor("#A1887F"), 4f, (Random.nextFloat() - 0.5f) * 30f, -14f, 500) }
    }

    /** Psychirra: vlna psychické energie (prstenec). */
    private fun ringReaction(color: Int) {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        repeat(2) { i ->
            handler.postDelayed({
                if (!running) return@postDelayed
                val ring = View(context).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke((3 * dp).toInt(), color) }
                    val sz = pokemonView.width / 2
                    layoutParams = ViewGroup.LayoutParams(sz, sz); x = pokemonView.x + pokemonView.width / 4f; y = pokemonView.y + pokemonView.height / 4f
                }
                parent.addView(ring)
                ring.animate().scaleX(4f).scaleY(4f).alpha(0f).setDuration(700).withEndAction { parent.removeView(ring) }.start()
            }, i * 220L)
        }
        pokemonView.animate().translationY(targetTranslationY - 14f * dp).setDuration(250).withEndAction {
            pokemonView.animate().translationY(targetTranslationY).setDuration(400).withEndAction { if (running) startWobble() }.start()
        }.start()
    }

    /** Hunfrey: hodí magickou kartu, která se zatočí a rozprskne v jiskry. */
    private fun cardThrowReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val dir = if (facingRight) 1f else -1f
        val card = View(context).apply {
            background = GradientDrawable().apply { cornerRadius = 2 * dp; setColor(Color.parseColor("#26A69A")); setStroke((1.5f * dp).toInt(), Color.parseColor("#FFF8E1")) }
            layoutParams = ViewGroup.LayoutParams((10 * dp).toInt(), (14 * dp).toInt())
            x = pokemonView.x + pokemonView.width / 2f; y = pokemonView.y + pokemonView.height * 0.4f
        }
        parent.addView(card)
        card.animate().translationXBy(dir * 70f * dp).translationYBy(-20f * dp).rotation(dir * 720f).setDuration(450).withEndAction {
            repeat(6) { puff(parent, card.x, card.y, if (it % 2 == 0) Color.parseColor("#4DD0E1") else Color.parseColor("#FFF59D"), 3f, (Random.nextFloat() - 0.5f) * 40f, (Random.nextFloat() - 0.5f) * 40f, 450) }
            parent.removeView(card)
        }.start()
        ObjectAnimator.ofFloat(pokemonView, "rotation", 0f, dir * 8f, 0f).apply { duration = 300; start() }
        if (running) handler.postDelayed({ startWobble() }, 350)
    }

    /** Kendl: plamen vzplane – vyšlehne sloup modrého ohně. */
    private fun kendlFlareReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        val cx = pokemonView.x + pokemonView.width / 2f; val top = pokemonView.y + pokemonView.height * 0.05f
        repeat(10) { i ->
            handler.postDelayed({
                if (running) puff(parent, cx + (Random.nextFloat() - 0.5f) * 10f * dp, top,
                    if (i % 3 == 0) Color.parseColor("#E1F5FE") else Color.parseColor("#4FC3F7"), 6f, (Random.nextFloat() - 0.5f) * 14f, -55f, 550, grow = 0.4f)
            }, i * 35L)
        }
        ObjectAnimator.ofFloat(pokemonView, "scaleY", baseScale, baseScale * 1.15f, baseScale).apply { duration = 350; start() }
        if (running) handler.postDelayed({ startWobble() }, 400)
    }

    /** Krystalos: zatřese se a vystřelí krystalové střepy. */
    private fun crystalBurstReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        shakeView(pokemonView)
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height * 0.3f
        repeat(8) { i ->
            val a = Math.toRadians(-180.0 + i * 25.0)
            puff(parent, cx, cy, if (i % 3 == 0) Color.parseColor("#FF9E40") else Color.parseColor("#4DD0E1"), 4f,
                cos(a).toFloat() * 55f, sin(a).toFloat() * 45f, 550, rect = true)
        }
        if (running) handler.postDelayed({ startWobble() }, 450)
    }

    /** Magmor: sopka na zádech vybuchne – oblak kouře a déšť jisker. */
    private fun eruptionReaction() {
        stopWobble(); moveAnim?.cancel()
        val parent = pokemonView.parent as? ViewGroup ?: return
        shakeView(pokemonView)
        val vx = pokemonView.x + pokemonView.width * (if (facingRight) 0.3f else 0.7f); val vy = pokemonView.y
        repeat(5) { puff(parent, vx, vy, Color.parseColor("#99616161"), 9f, (Random.nextFloat() - 0.5f) * 20f, -40f, 1000, grow = 2.5f) }
        repeat(10) { i ->
            handler.postDelayed({
                if (running) puff(parent, vx, vy, if (i % 2 == 0) Color.parseColor("#FF6D00") else Color.parseColor("#FFD600"), 4f,
                    (Random.nextFloat() - 0.5f) * 70f, -30f - Random.nextFloat() * 30f, 650)
            }, i * 30L)
        }
        if (running) handler.postDelayed({ startWobble() }, 500)
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
            "032", "033" -> shellTuckReaction(null)
            "041" -> ringReaction(Color.parseColor("#C86BE8"))
            "042" -> cardThrowReaction()
            "043" -> kendlFlareReaction()
            "044" -> shellTuckReaction(Color.parseColor("#4DD0E1"))
            "045" -> crystalBurstReaction()
            "047" -> eruptionReaction()
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
        val fs = facingSign()
        pokemonView.animate().scaleX(fs * baseScale * 1.3f).scaleY(baseScale * 1.3f).setDuration(100).withEndAction {
            pokemonView.animate().scaleX(fs * baseScale).scaleY(baseScale).setDuration(300).start()
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
        val fs = facingSign()
        ObjectAnimator.ofFloat(pokemonView, "scaleX", fs * baseScale, fs * baseScale * 1.15f, fs * baseScale).apply { duration = 250; start() }
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
        val fs = facingSign()
        pokemonView.animate().translationYBy(-30f * dp).scaleX(fs * baseScale * 1.15f).scaleY(baseScale * 1.15f)
            .setDuration(200).setInterpolator(DecelerateInterpolator()).withEndAction {
                pokemonView.animate().translationY(targetTranslationY).scaleX(fs * baseScale).scaleY(baseScale)
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

    /** −1 = otočen doprava (sprity jsou kreslené doleva), +1 = doleva. */
    private fun facingSign(): Float = if (facingRight) -1f else 1f

    private fun applyFacing(right: Boolean) {
        pokemonView.scaleX = baseScale * (if (right) -1f else 1f)
    }

    private fun shakeView(target: View) {
        ObjectAnimator.ofFloat(target, "translationX", *relTx(target, 0f, -15f * dp, 15f * dp, -10f * dp, 10f * dp, -5f * dp, 5f * dp, 0f))
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

    private fun scheduleGhostParticles(color: Int, gen: Int = idleGeneration) {
        if (!running || gen != idleGeneration) return
        val parent = pokemonView.parent as? ViewGroup ?: run { handler.postDelayed({ scheduleGhostParticles(color, gen) }, 2000); return }
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
        handler.postDelayed({ scheduleGhostParticles(color, gen) }, Random.nextLong(800, 2000))
    }

    private fun scheduleFireParticles(gen: Int = idleGeneration) {
        if (!running || gen != idleGeneration) return
        val parent = pokemonView.parent as? ViewGroup ?: run { handler.postDelayed({ scheduleFireParticles(gen) }, 1000); return }
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height * 0.4f
        val size = (5 * dp).toInt()
        val ember = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (Random.nextBoolean()) Color.parseColor("#FF6D00") else Color.parseColor("#FFD600")) }
            layoutParams = ViewGroup.LayoutParams(size, size); x = cx + (Random.nextFloat() - 0.5f) * 20f * dp; y = cy
        }
        parent.addView(ember)
        ember.animate().translationYBy(-25f * dp).translationXBy((Random.nextFloat() - 0.5f) * 15f * dp)
            .alpha(0f).setDuration(600).withEndAction { parent.removeView(ember) }.start()
        handler.postDelayed({ scheduleFireParticles(gen) }, Random.nextLong(400, 900))
    }

    private fun scheduleWaterDrops(gen: Int = idleGeneration) {
        if (!running || gen != idleGeneration) return
        val parent = pokemonView.parent as? ViewGroup ?: run { handler.postDelayed({ scheduleWaterDrops(gen) }, 1000); return }
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height * 0.3f
        val size = (4 * dp).toInt()
        val drop = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#29B6F6")) }
            layoutParams = ViewGroup.LayoutParams(size, size); x = cx + (Random.nextFloat() - 0.5f) * 15f * dp; y = cy
        }
        parent.addView(drop)
        drop.animate().translationYBy(-20f * dp).alpha(0f).setDuration(700).withEndAction { parent.removeView(drop) }.start()
        handler.postDelayed({ scheduleWaterDrops(gen) }, Random.nextLong(600, 1400))
    }

    private fun scheduleFairySparkles(gen: Int = idleGeneration) {
        if (!running || gen != idleGeneration) return
        val parent = pokemonView.parent as? ViewGroup ?: run { handler.postDelayed({ scheduleFairySparkles(gen) }, 1000); return }
        val cx = pokemonView.x + pokemonView.width / 2f; val cy = pokemonView.y + pokemonView.height / 2f
        val sparkle = TextView(context).apply {
            text = "✦"; textSize = 10f; setTextColor(Color.parseColor("#F8BBD0"))
            x = cx + (Random.nextFloat() - 0.5f) * pokemonView.width; y = cy + (Random.nextFloat() - 0.5f) * pokemonView.height; alpha = 0f
        }
        parent.addView(sparkle)
        sparkle.animate().alpha(0.9f).translationYBy(-20f * dp).setDuration(400).withEndAction {
            sparkle.animate().alpha(0f).setDuration(400).withEndAction { parent.removeView(sparkle) }.start()
        }.start()
        handler.postDelayed({ scheduleFairySparkles(gen) }, Random.nextLong(500, 1200))
    }
}