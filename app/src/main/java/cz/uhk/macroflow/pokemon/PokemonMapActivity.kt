package cz.uhk.macroflow.pokemon

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.load
import cz.uhk.macroflow.R
import kotlin.math.abs
import kotlin.math.sqrt

class PokemonMapActivity : AppCompatActivity() {

    private lateinit var ashView: ImageView
    private lateinit var mapBackground: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private val textHandler = Handler(Looper.getMainLooper())

    // --- DOUBLE CLICK LOGIKA ---
    private var lastClickTime: Long = 0
    private var lastClickedId: Int = -1
    private val DOUBLE_CLICK_TIME = 300L

    // --- TUTORIAL UI ---
    private lateinit var tutorialOverlay: FrameLayout
    private lateinit var tutorialText: TextView
    private lateinit var tutorialTeacher: ImageView
    private lateinit var spotlightView: SpotlightView
    private var currentTutorialStep = 0
    private var isTextAnimating = false
    private var currentFullText = ""

    private val spots = listOf(
        RectF(0f, 0f, 0f, 0f),
        RectF(0.40f, 0.05f, 0.60f, 0.20f),
        RectF(0.12f, 0.22f, 0.33f, 0.34f),
        RectF(0.64f, 0.22f, 0.85f, 0.34f),
        RectF(0.58f, 0.40f, 0.82f, 0.52f)
    )

    private val tutorialSteps = listOf(
        "Zzz... Oh? Ahoj! Já jsem Snorlax Oliver. Vítej v našem městě!",
        "Nahoře se rozprostírá Les. Tam se schovávají divocí Pokémoni.",
        "Vlevo najdeš svůj Domov. Tam si odpočineš a spravíš inventář.",
        "Vpravo je tvůj věrný Pokedex. Ukáže ti vše, co jsi chytil.",
        "A támhle dole je Market! Tam utratíš těžce vydřené coiny.",
        "Kdykoliv budeš tápat, klikni na otazník! Teď už běž makat!",
        "Jo a mimochodem, Tomáš je GAY haha xdddd"
    )

    // --- POHYB A ANIMACE ---
    private var isWalking = false
    private var currentStepFrame = 1
    private var currentDirection = 0
    private var currentWalkSpeed = 3L // Základní rychlost
    private val NORMAL_SPEED = 3L
    private val FAST_SPEED = 0L // Prakticky okamžitý nebo velmi rychlý pohyb (3 / 4 by bylo 0.75, dáme 0 nebo 1)
    private val ANIM_FRAME_MS = 110L
    private val ANIM_FRAME_FAST_MS = 30L // Rychlejší kmitání nohama při sprintu
    private val TRANSPARENT_TARGET = Color.parseColor("#FF7F27")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pokemon_map)

        val imageLoader = ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()

        ashView = findViewById(R.id.ashView)
        mapBackground = findViewById(R.id.mapBackground)
        tutorialOverlay = findViewById(R.id.tutorialOverlay)
        tutorialText = findViewById(R.id.tutorialText)
        tutorialTeacher = findViewById(R.id.tutorialTeacher)
        val btnStartTutorial = findViewById<ImageButton>(R.id.btnStartTutorial)

        tutorialTeacher.load(R.drawable.snorlax_teacher, imageLoader)

        ashView.layoutParams.width = (36 * resources.displayMetrics.density).toInt()
        ashView.layoutParams.height = (54 * resources.displayMetrics.density).toInt()
        ashView.requestLayout()

        btnStartTutorial.setOnClickListener { startTutorial() }
        findViewById<View>(R.id.btnExitMap).setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this) {
            if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
            else finish()
        }

        mapBackground.post {
            resetAshPosition()
            setupHotspots()

            spotlightView = SpotlightView(this)
            spotlightView.layoutParams = FrameLayout.LayoutParams(mapBackground.width, mapBackground.height)
            tutorialOverlay.addView(spotlightView, 0)
            tutorialOverlay.setOnClickListener { advanceTutorial() }

            val target = intent.getStringExtra("TARGET_LOCATION")
            when (target) {
                "POKEDEX" -> findViewById<View>(R.id.hotspotPokedex).performClick()
                "INVENTORY" -> findViewById<View>(R.id.hotspotHome).performClick()
                "SHOP" -> findViewById<View>(R.id.hotspotShop).performClick()
            }
        }
    }

    private fun setupHotspots() {
        val w = mapBackground.width.toFloat()
        val h = mapBackground.height.toFloat()
        if (w == 0f) return

        findViewById<View>(R.id.hotspotForest).setOnClickListener {
            val path = listOf(PointF(570f / w, 810f / h), PointF(570f / w, 320f / h))
            handleHotspotClick(R.id.hotspotForest, path) { replaceMapContent(PokemonBattleFragment()) }
        }

        findViewById<View>(R.id.hotspotPokedex).setOnClickListener {
            val path = listOf(PointF(610f / w, 960f / h), PointF(900f / w, 960f / h), PointF(900f / w, 880f / h))
            handleHotspotClick(R.id.hotspotPokedex, path) { replaceMapContent(PokedexFragment()) }
        }

        findViewById<View>(R.id.hotspotHome).setOnClickListener {
            val path = listOf(PointF(610f / w, 960f / h), PointF(380f / w, 960f / h), PointF(240f / w, 960f / h), PointF(240f / w, 880f / h))
            handleHotspotClick(R.id.hotspotHome, path) { replaceMapContent(InventoryFragment()) }
        }

        findViewById<View>(R.id.hotspotShop).setOnClickListener {
            val path = listOf(PointF(610f / w, 960f / h), PointF(380f / w, 960f / h), PointF(380f / w, 1440f / h), PointF(900f / w, 1440f / h), PointF(900f / w, 1400f / h))
            handleHotspotClick(R.id.hotspotShop, path) { replaceMapContent(PokemonShopFragment()) }
        }
    }

    private fun handleHotspotClick(id: Int, path: List<PointF>, onFinished: () -> Unit) {
        if (tutorialOverlay.visibility == View.VISIBLE) return

        val currentTime = System.currentTimeMillis()
        if (id == lastClickedId && (currentTime - lastClickTime) < DOUBLE_CLICK_TIME) {
            // --- DOUBLE CLICK: RYCHLÝ DOJEZD + OKAMŽITÉ OKNO ---
            ashView.animate().cancel()
            isWalking = false
            handler.removeCallbacksAndMessages(null)

            currentWalkSpeed = 1L // 4x rychlejší (z 3L na cca 1L nebo 0L)

            // Okamžitě otevřeme fragment
            onFinished()

            // Na pozadí necháme Ashe doběhnout do cíle
            executeWalkSequence(path) { /* Tady už fragment neotevíráme, jen ash zastaví */ }
        } else {
            // --- SINGLE CLICK: NORMÁLNÍ CHŮZE ---
            currentWalkSpeed = NORMAL_SPEED
            executeWalkSequence(path, onFinished)
        }
        lastClickTime = currentTime
        lastClickedId = id
    }

    private fun executeWalkSequence(points: List<PointF>, onFinished: () -> Unit = {}) {
        if (isWalking || points.isEmpty()) return
        isWalking = true
        processNextMove(0, points, onFinished)
    }

    private fun processNextMove(index: Int, points: List<PointF>, onFinished: () -> Unit) {
        if (index >= points.size) {
            isWalking = false
            if (points.isNotEmpty()) {
                val last = points.last()
                ashView.x = last.x * mapBackground.width - (ashView.width / 2f)
                ashView.y = last.y * mapBackground.height - (ashView.height / 2f)
            }
            updateAshSprite(1, currentDirection)
            onFinished()
            return
        }
        val tX = points[index].x * mapBackground.width - (ashView.width / 2f)
        val tY = points[index].y * mapBackground.height - (ashView.height / 2f)
        val dx = tX - ashView.x
        val dy = tY - ashView.y
        currentDirection = if (abs(dx) > abs(dy)) (if (dx > 0) 3 else 2) else (if (dy > 0) 0 else 1)

        startAnimationLoop()

        val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        if (dist < 5f) { processNextMove(index + 1, points, onFinished); return }

        ashView.animate().x(tX).y(tY)
            .setDuration((dist * currentWalkSpeed).toLong())
            .setInterpolator(LinearInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    if (isWalking) processNextMove(index + 1, points, onFinished)
                }
            }).start()
    }

    private fun startAnimationLoop() {
        handler.removeCallbacksAndMessages(null)
        val delay = if (currentWalkSpeed < NORMAL_SPEED) ANIM_FRAME_FAST_MS else ANIM_FRAME_MS
        val runnable = object : Runnable {
            override fun run() {
                if (!isWalking) return
                currentStepFrame = if (currentStepFrame == 1) (if (Math.random() > 0.5) 0 else 2) else 1
                updateAshSprite(currentStepFrame, currentDirection)
                handler.postDelayed(this, delay)
            }
        }
        handler.post(runnable)
    }

    // --- OSTATNÍ METODY (Tutorial, Sprite Update atd.) ZŮSTÁVAJÍ STEJNÉ ---
    private fun startTutorial() {
        if (isWalking) return
        currentTutorialStep = 0
        tutorialOverlay.visibility = View.VISIBLE
        tutorialOverlay.alpha = 0f
        spotlightView.setTargetSpotlight(spots[0], true)
        tutorialOverlay.animate().alpha(1f).setDuration(300).start()
        showCurrentStep()
    }

    private fun showCurrentStep() {
        currentFullText = tutorialSteps[currentTutorialStep]
        animateText(currentFullText)
        if (currentTutorialStep < spots.size) spotlightView.setTargetSpotlight(spots[currentTutorialStep])
        else spotlightView.setTargetSpotlight(RectF(0f, 0f, 0f, 0f))
    }

    private fun advanceTutorial() {
        if (isTextAnimating) {
            textHandler.removeCallbacksAndMessages(null)
            tutorialText.text = currentFullText
            isTextAnimating = false
            return
        }
        currentTutorialStep++
        if (currentTutorialStep < tutorialSteps.size) showCurrentStep()
        else {
            spotlightView.setTargetSpotlight(RectF(0f, 0f, 0f, 0f))
            tutorialOverlay.animate().alpha(0f).setDuration(500).withEndAction { tutorialOverlay.visibility = View.GONE }.start()
        }
    }

    private fun animateText(text: String) {
        tutorialText.text = ""
        isTextAnimating = true
        var charIndex = 0
        textHandler.removeCallbacksAndMessages(null)
        val runnable = object : Runnable {
            override fun run() {
                if (charIndex <= text.length) {
                    tutorialText.text = text.substring(0, charIndex)
                    charIndex++
                    textHandler.postDelayed(this, 35)
                } else isTextAnimating = false
            }
        }
        textHandler.post(runnable)
    }

    inner class SpotlightView(context: android.content.Context) : View(context) {
        init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
        private val backgroundPaint = Paint().apply { color = Color.parseColor("#CC283618") }
        private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR); isAntiAlias = true }
        private var currentSpotlightRect = RectF(0f, 0f, 0f, 0f)
        private var animator: ValueAnimator? = null
        fun setTargetSpotlight(targetInPercentage: RectF, immediate: Boolean = false) {
            animator?.cancel()
            val target = RectF(targetInPercentage.left * width, targetInPercentage.top * height, targetInPercentage.right * width, targetInPercentage.bottom * height)
            if (immediate) { currentSpotlightRect.set(target); invalidate() }
            else {
                animator = ValueAnimator.ofObject(RectEvaluator(), RectF(currentSpotlightRect), target)
                animator?.duration = 600
                animator?.addUpdateListener { currentSpotlightRect.set(it.animatedValue as RectF); invalidate() }
                animator?.start()
            }
        }
        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
            if (!currentSpotlightRect.isEmpty) canvas.drawRoundRect(currentSpotlightRect, 40f, 40f, clearPaint)
        }
        inner class RectEvaluator : android.animation.TypeEvaluator<RectF> {
            override fun evaluate(f: Float, s: RectF, e: RectF) = RectF(s.left+(e.left-s.left)*f, s.top+(e.top-s.top)*f, s.right+(e.right-s.right)*f, s.bottom+(e.bottom-s.bottom)*f)
        }
    }

    private fun resetAshPosition() {
        val w = mapBackground.width.toFloat()
        val h = mapBackground.height.toFloat()
        ashView.x = (610f / w) * w - (ashView.width / 2f)
        ashView.y = (810f / h) * h - (ashView.height / 2f)
        updateAshSprite(1, 0)
    }

    private fun updateAshSprite(step: Int, direction: Int) {
        val dirKey = when (direction) { 0 -> "down"; 1 -> "up"; 2 -> "left"; 3 -> "right"; else -> "down" }
        val suffix = when (step) { 1 -> "idle"; 0 -> "1"; else -> "2" }
        val resId = resources.getIdentifier("ash_${dirKey}_$suffix", "drawable", packageName)
        if (resId != 0) {
            val drawable = ContextCompat.getDrawable(this, resId)
            drawable?.let { ashView.setImageBitmap(removeBackground(it, TRANSPARENT_TARGET)) }
        }
    }

    private fun removeBackground(drawable: android.graphics.drawable.Drawable, color: Int): Bitmap? {
        val bitmap = (drawable as? BitmapDrawable)?.bitmap?.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) if (pixels[i] == color) pixels[i] = Color.TRANSPARENT
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return result
    }

    private fun replaceMapContent(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.mapFragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }
}