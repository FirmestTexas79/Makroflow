package cz.uhk.macroflow

import android.animation.*
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.*
import android.view.*
import android.view.animation.*
import android.widget.*
import android.app.Dialog
import kotlin.math.*
import kotlin.random.Random

/**
 * Epic achievement unlock — tarot coin style.
 *
 * Sekvence:
 *  0.0s  Medaile se pomalu materializuje z ničeho — malá, průhledná, pomalu se točí (0→720°)
 *  1.2s  Zpomalí rotaci, záře narůstá
 *  1.6s  Výbuch světla, mince "přistane" na finální rotaci
 *  1.8s  Prohoupnutí: nakloní se doprava, pak doleva, pak se ustálí
 *  2.2s  Texty přijedou zdola, odlesk přejede přes medaili
 */
class AchievementUnlockDialog(
    context: Context,
    private val def: AchievementDef
) : android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private lateinit var shineView: ShineView
    private lateinit var burstView: LightBurstView
    private lateinit var starField: StarFieldView
    private var soundPool: SoundPool? = null
    private var soundId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.93f)
        }
        setCanceledOnTouchOutside(true)

        val dp = context.resources.displayMetrics.density

        // ── Root ────────────────────────────────────────────────────
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#050505"))
        }

        // ── Hvězdičky ────────────────────────────────────────────────
        starField = StarFieldView(context)
        root.addView(starField, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // ── Centrální layout ─────────────────────────────────────────
        val centerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        root.addView(centerLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        // ── Medaile kontejner ────────────────────────────────────────
        // frameSize = velký aby burst nepřetékal ven jako čtvereček
        val medalSize  = (240 * dp).toInt()
        val frameSize  = (390 * dp).toInt()  // burst se vejde dovnitř

        val medalFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(frameSize, frameSize).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
            // ŽÁDNÉ pevné pozadí — frame je průhledný
        }

        // Vrstva 1: LightBurst — UVNITŘ frameSize, centrovaný
        burstView = LightBurstView(context, Color.parseColor(tierGlowColor())).apply {
            layoutParams = FrameLayout.LayoutParams(frameSize, frameSize, Gravity.CENTER)
            alpha = 0f
        }

        // Vrstva 2: Soft glow halo přímo za medailí
        val innerGlow = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                (medalSize * 1.35f).toInt(),
                (medalSize * 1.35f).toInt(),
                Gravity.CENTER
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                val color = Color.parseColor(tierGlowColor())
                colors = intArrayOf(
                    adjustAlpha(color, 0.75f),
                    adjustAlpha(color, 0.28f),
                    adjustAlpha(color, 0f)
                )
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = medalSize * 0.68f
            }
            alpha = 0f
        }

        // Vrstva 3: Medaile
        val medalView = ImageView(context).apply {
            val resName = "achievement_${def.id}"
            val resId   = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) setImageResource(resId)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(medalSize, medalSize, Gravity.CENTER)
            alpha   = 0f
            scaleX  = 0.05f
            scaleY  = 0.05f
            rotationY = 0f
        }

        // Vrstva 4: Shine
        shineView = ShineView(context, medalSize.toFloat()).apply {
            layoutParams = FrameLayout.LayoutParams(medalSize, medalSize, Gravity.CENTER)
            alpha = 0f
        }

        medalFrame.addView(burstView)   // 1. záře/burst — za vším
        medalFrame.addView(innerGlow)   // 2. halo
        medalFrame.addView(medalView)   // 3. medaile
        medalFrame.addView(shineView)   // 4. odlesk — navrchu
        centerLayout.addView(medalFrame)

        // ── Text panel ───────────────────────────────────────────────
        val textPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (8 * dp).toInt() }
        }

        val labelTv = TextView(context).apply {
            text = "✦  ACHIEVEMENT ODEMČEN  ✦"
            textSize = 11f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(Color.parseColor(tierGlowColor()))
            gravity = Gravity.CENTER
            letterSpacing = 0.25f
            alpha = 0f; translationY = 30f
        }

        val titleTv = TextView(context).apply {
            text = def.titleCs
            textSize = 30f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#FEFAE0"))
            gravity = Gravity.CENTER
            alpha = 0f; translationY = 40f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = (8 * dp).toInt()
                it.leftMargin = (28 * dp).toInt()
                it.rightMargin = (28 * dp).toInt()
            }
        }

        val tierPill = TextView(context).apply {
            text = "⬥  ${def.tier.labelCs.uppercase()}  ⬥"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(tierGlowColor()))
            gravity = Gravity.CENTER
            letterSpacing = 0.2f
            alpha = 0f; translationY = 40f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40 * dp
                setStroke((1f * dp).toInt(), Color.parseColor(tierGlowColor()))
                setColor(adjustAlpha(Color.parseColor(tierGlowColor()), 0.15f))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = (8 * dp).toInt()
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
            setPadding((18 * dp).toInt(), (4 * dp).toInt(), (18 * dp).toInt(), (4 * dp).toInt())
        }

        val descTv = TextView(context).apply {
            text = def.descCs
            textSize = 13f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color.parseColor("#70FEFAE0"))
            gravity = Gravity.CENTER
            alpha = 0f; translationY = 40f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = (8 * dp).toInt()
                it.leftMargin = (48 * dp).toInt()
                it.rightMargin = (48 * dp).toInt()
            }
        }

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams((100 * dp).toInt(), (1 * dp).toInt()).also {
                it.topMargin = (16 * dp).toInt()
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
            setBackgroundColor(adjustAlpha(Color.parseColor(tierGlowColor()), 0.4f))
            alpha = 0f
        }

        val closeBtn = TextView(context).apply {
            text = "SKVĚLÉ!"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor(tierGlowColor()))
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
            alpha = 0f; translationY = 30f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 50 * dp
                setStroke((1.5f * dp).toInt(), Color.parseColor(tierGlowColor()))
                setColor(adjustAlpha(Color.parseColor(tierGlowColor()), 0.1f))
            }
            layoutParams = LinearLayout.LayoutParams(
                (160 * dp).toInt(), (46 * dp).toInt()
            ).also {
                it.topMargin = (18 * dp).toInt()
                it.gravity = Gravity.CENTER_HORIZONTAL
            }
            setOnClickListener { dismiss() }
        }

        textPanel.addView(labelTv)
        textPanel.addView(titleTv)
        textPanel.addView(tierPill)
        textPanel.addView(descTv)
        textPanel.addView(divider)
        textPanel.addView(closeBtn)
        centerLayout.addView(textPanel)

        setContentView(root)

        vibrateDevice()
        initSound()

        Handler(Looper.getMainLooper()).postDelayed({
            starField.start()
            playEpicSequence(medalView, innerGlow, labelTv, titleTv, tierPill, descTv, divider, closeBtn)
        }, 150)
    }

    // ════════════════════════════════════════════════════════════════
    // ANIMAČNÍ SEKVENCE
    // ════════════════════════════════════════════════════════════════
    private fun playEpicSequence(
        medalView: ImageView,
        innerGlow: View,
        labelTv: TextView,
        titleTv: TextView,
        tierPill: TextView,
        descTv: TextView,
        divider: View,
        closeBtn: TextView
    ) {
        val dp = context.resources.displayMetrics.density
        // Velká camera distance = méně perspektivy = kulatější 3D rotace
        medalView.cameraDistance = 20000 * dp

        // ── FÁZE 1: Materializace z ničeho (0–1200ms) ────────────────
        // Medaile se pomalu zvětšuje ze 0 na 0.85x a pomalu se točí (2× otočení)
        val materialize = AnimatorSet().apply {
            playTogether(
                // Zvětšení z nic
                ObjectAnimator.ofFloat(medalView, "scaleX", 0.05f, 0.85f).apply {
                    duration = 1200
                    interpolator = DecelerateInterpolator(1.8f)
                },
                ObjectAnimator.ofFloat(medalView, "scaleY", 0.05f, 0.85f).apply {
                    duration = 1200
                    interpolator = DecelerateInterpolator(1.8f)
                },
                // Fade in
                ObjectAnimator.ofFloat(medalView, "alpha", 0f, 1f).apply {
                    duration = 500
                    interpolator = AccelerateInterpolator()
                },
                // Pomalé otočení 0° → 720° (2 plné otočky) — jako mince letící vzduchem
                ObjectAnimator.ofFloat(medalView, "rotationY", 0f, 720f).apply {
                    duration = 1200
                    interpolator = DecelerateInterpolator(1.5f)
                },
                // Záře se začne rozsvěcovat
                ObjectAnimator.ofFloat(innerGlow, "alpha", 0f, 0.6f).apply {
                    duration = 800
                    startDelay = 400
                }
            )
        }

        // ── FÁZE 2: Výbuch světla + finální přiblížení (1200–1700ms) ─
        val burst = AnimatorSet().apply {
            playTogether(
                // Přiblíží se — "vyskočí k divákovi"
                ObjectAnimator.ofFloat(medalView, "scaleX", 0.85f, 1.2f).apply {
                    duration = 400
                    interpolator = DecelerateInterpolator(2f)
                },
                ObjectAnimator.ofFloat(medalView, "scaleY", 0.85f, 1.2f).apply {
                    duration = 400
                    interpolator = DecelerateInterpolator(2f)
                },
                // Rotace se zastaví na 0° (plná přední strana)
                ObjectAnimator.ofFloat(medalView, "rotationY", 0f, 0f).apply {
                    duration = 400
                },
                // Burst exploduje
                ObjectAnimator.ofFloat(burstView, "alpha", 0f, 1f).apply {
                    duration = 200
                },
                // Glow na maximum
                ObjectAnimator.ofFloat(innerGlow, "alpha", 0.6f, 1f).apply {
                    duration = 250
                }
            )
        }

        // ── FÁZE 3: Bounce + prohoupnutí (1700–2400ms) ───────────────
        // Nejdřív scale bounce, pak prohoupnutí rotationY doprava a doleva
        val bounceScale = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(medalView, "scaleX", 1.2f, 0.9f, 1.06f, 0.97f, 1f).apply {
                    duration = 550
                    interpolator = LinearInterpolator()
                },
                ObjectAnimator.ofFloat(medalView, "scaleY", 1.2f, 0.9f, 1.06f, 0.97f, 1f).apply {
                    duration = 550
                    interpolator = LinearInterpolator()
                }
            )
        }

        // Prohoupnutí — doprava (+18°), doleva (-12°), doprava (+7°), doleva (-4°), 0°
        val swing = ObjectAnimator.ofFloat(
            medalView, "rotationY",
            0f, 18f, -12f, 7f, -4f, 0f
        ).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
        }

        val bounceAndSwing = AnimatorSet().apply {
            playTogether(bounceScale, swing)
        }

        // Burst ztlumení po výbuchu
        val burstFade = ObjectAnimator.ofFloat(burstView, "alpha", 1f, 0.55f).apply {
            duration = 600
        }

        // ── Celková sekvence ─────────────────────────────────────────
        val fullSeq = AnimatorSet().apply {
            playSequentially(materialize, burst, bounceAndSwing)
        }

        burst.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                burstFade.start()
            }
        })

        fullSeq.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                playSound()
                vibrateLight()
                burstView.startPulse()
                shineView.alpha = 1f
                shineView.startRepeating()
                animateTextIn(labelTv,  0L)
                animateTextIn(titleTv,  100L)
                animateTextIn(tierPill, 200L)
                animateTextIn(descTv,   300L)
                animateTextIn(divider,  380L)
                animateTextIn(closeBtn, 460L)
            }
        })

        fullSeq.start()
    }

    private fun animateTextIn(view: View, delay: Long) {
        view.animate()
            .alpha(1f).translationY(0f)
            .setDuration(360)
            .setStartDelay(delay)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    // ────────────────────────────────────────────────────────────────
    private fun vibrateDevice() {
        vibrate(longArrayOf(0, 80, 60, 120), intArrayOf(0, 180, 0, 255))
    }
    private fun vibrateLight() {
        vibrate(longArrayOf(0, 40), intArrayOf(0, 120))
    }
    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(timings, -1)
        }
    }

    private fun initSound() {
        try {
            soundPool = SoundPool.Builder().setMaxStreams(1)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .build()
            val resId = context.resources.getIdentifier("achievement_unlock", "raw", context.packageName)
            if (resId != 0) soundId = soundPool!!.load(context, resId, 1)
        } catch (_: Exception) {}
    }

    private fun playSound() {
        if (soundId != 0) soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    override fun dismiss() {
        shineView.stop()
        burstView.stop()
        starField.stop()
        soundPool?.release()
        soundPool = null
        super.dismiss()
    }

    private fun tierGlowColor() = when (def.tier) {
        AchievementTier.BRONZE  -> "#DDA15E"
        AchievementTier.SILVER  -> "#C8C8C8"
        AchievementTier.GOLD    -> "#FFD966"
        AchievementTier.DIAMOND -> "#7EC8E3"
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = (255 * factor).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}

// ══════════════════════════════════════════════════════════════════
// LIGHT BURST VIEW — paprsky světla, KRUHOVÝ canvas, bez čtverečku
// ══════════════════════════════════════════════════════════════════
class LightBurstView(context: Context, private val glowColor: Int) : View(context) {

    private val paint  = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var pulseT  = 1f
    private var rotAngle = 0f
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var pulseAnim: ValueAnimator? = null
    private val rayCount = 18

    private val rotRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            rotAngle += 0.25f
            invalidate()
            handler.postDelayed(this, 16)
        }
    }

    fun startPulse() {
        running = true
        handler.post(rotRunnable)
        pulseAnim = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            repeatMode  = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseT = (sin((it.animatedValue as Float).toDouble()).toFloat() + 1f) / 2f
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(rotRunnable)
        pulseAnim?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width  / 2f
        val cy = height / 2f
        val maxR = min(width, height) / 2f

        // Kresli do offscreen bitmapy aby clipPath fungoval správně
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        canvas.translate(cx, cy)
        canvas.rotate(rotAngle)

        val pulseAlpha = 0.45f + pulseT * 0.55f
        val pulseScale = 0.85f + pulseT * 0.2f

        // Paprsky — střídavě dlouhé a krátké
        for (i in 0 until rayCount) {
            val angleDeg = i * (360f / rayCount)
            val isLong   = i % 2 == 0
            val startR   = maxR * 0.20f
            val endR     = if (isLong) maxR * pulseScale else maxR * pulseScale * 0.65f
            val rayW     = if (isLong) maxR * 0.055f else maxR * 0.028f

            val rad = Math.toRadians(angleDeg.toDouble())
            val sx  = (cos(rad) * startR).toFloat()
            val sy  = (sin(rad) * startR).toFloat()
            val ex  = (cos(rad) * endR).toFloat()
            val ey  = (sin(rad) * endR).toFloat()

            paint.shader = LinearGradient(
                sx, sy, ex, ey,
                intArrayOf(
                    adjustAlpha(glowColor, 0.65f * pulseAlpha),
                    adjustAlpha(glowColor, 0f)
                ),
                null, Shader.TileMode.CLAMP
            )
            paint.strokeWidth = rayW
            paint.style = Paint.Style.STROKE
            canvas.drawLine(sx, sy, ex, ey, paint)
        }
        paint.shader = null

        // Centrální radiální záře
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            0f, 0f, maxR * 0.55f * pulseScale,
            intArrayOf(
                adjustAlpha(glowColor, 0.85f * pulseAlpha),
                adjustAlpha(glowColor, 0.35f * pulseAlpha),
                adjustAlpha(glowColor, 0f)
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(0f, 0f, maxR * 0.55f * pulseScale, paint)
        paint.shader = null

        // Oříznutí do kruhu — odstraní čtvercové artefakty
        canvas.rotate(-rotAngle)
        canvas.translate(-cx, -cy)
        clipPaint.shader = RadialGradient(
            cx, cy, maxR * 0.95f,
            intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0f, 0.85f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), clipPaint)
        clipPaint.shader = null

        canvas.restoreToCount(layer)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val a = (255 * factor).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }
}

// ══════════════════════════════════════════════════════════════════
// STAR FIELD VIEW
// ══════════════════════════════════════════════════════════════════
class StarFieldView(context: Context) : View(context) {

    data class Star(
        val x: Float, val y: Float,
        val size: Float,
        val twinkleSpeed: Float,
        val twinklePhase: Float,
        val isCross: Boolean
    )

    private val stars   = mutableListOf<Star>()
    private val paint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var frame   = 0f

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            frame += 0.035f
            invalidate()
            handler.postDelayed(this, 16)
        }
    }

    fun start() {
        post {
            if (stars.isEmpty()) {
                val w = width.toFloat(); val h = height.toFloat()
                repeat(60) {
                    stars.add(Star(
                        x = Random.nextFloat() * w,
                        y = Random.nextFloat() * h,
                        size = Random.nextFloat() * 3.5f + 1.2f,
                        twinkleSpeed = Random.nextFloat() * 1.8f + 0.4f,
                        twinklePhase = Random.nextFloat() * (2 * PI).toFloat(),
                        isCross = Random.nextFloat() > 0.6f
                    ))
                }
            }
            running = true
            handler.post(tickRunnable)
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tickRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        stars.forEach { s ->
            val a = ((sin((frame * s.twinkleSpeed + s.twinklePhase).toDouble()) + 1) / 2 * 0.85 + 0.08).toFloat()
            paint.alpha = (a * 255).toInt()
            paint.color = Color.WHITE
            if (s.isCross) {
                paint.strokeWidth = s.size * 0.3f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(s.x - s.size, s.y, s.x + s.size, s.y, paint)
                canvas.drawLine(s.x, s.y - s.size, s.x, s.y + s.size, paint)
                val d = s.size * 0.5f
                canvas.drawLine(s.x - d, s.y - d, s.x + d, s.y + d, paint)
                canvas.drawLine(s.x + d, s.y - d, s.x - d, s.y + d, paint)
                paint.style = Paint.Style.FILL
            } else {
                canvas.drawCircle(s.x, s.y, s.size * 0.38f, paint)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
// SHINE VIEW — šikmý odlesk každých 4.5s
// ══════════════════════════════════════════════════════════════════
class ShineView(context: Context, private val medalSize: Float) : View(context) {

    private val paint    = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = -1f
    private var running  = false
    private val handler  = Handler(Looper.getMainLooper())
    private val shineW   = medalSize * 0.22f
    private val halfDiag = sqrt(medalSize * medalSize + medalSize * medalSize) / 2f

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            sweep()
            handler.postDelayed(this, 4500L)
        }
    }

    fun startRepeating() {
        running = true
        handler.postDelayed(repeatRunnable, 900L)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(repeatRunnable)
    }

    private fun sweep() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            interpolator = LinearInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { progress = -1f; invalidate() }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (progress < 0f) return
        val cx = medalSize / 2f; val cy = medalSize / 2f
        val total  = halfDiag * 2f + shineW * 2f
        val offset = -halfDiag - shineW + total * progress

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(135f)
        canvas.clipPath(Path().apply { addCircle(0f, 0f, medalSize / 2f, Path.Direction.CW) })

        paint.shader = LinearGradient(
            offset - shineW / 2f, 0f, offset + shineW / 2f, 0f,
            intArrayOf(
                Color.argb(0, 255, 255, 255),
                Color.argb(40, 255, 255, 255),
                Color.argb(105, 255, 255, 255),
                Color.argb(40, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(
            offset - shineW / 2f, -halfDiag - shineW,
            offset + shineW / 2f,  halfDiag + shineW,
            paint
        )
        canvas.restore()
        paint.shader = null
    }
}