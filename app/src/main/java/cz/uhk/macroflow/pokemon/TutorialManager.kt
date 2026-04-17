package cz.uhk.macroflow.pokemon

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import coil.ImageLoader
import coil.load
import cz.uhk.macroflow.R

class TutorialManager(
    private val context: Context,
    private val overlay: FrameLayout,
    private val textView: TextView,
    private val teacherImage: ImageView,
    private val imageLoader: ImageLoader
) {
    private val textHandler = Handler(Looper.getMainLooper())
    private val spotlightView = SpotlightView(context)
    private var currentStep = 0
    private var isTextAnimating = false
    private var currentFullText = ""

    private val spots = listOf(
        RectF(0f, 0f, 0f, 0f),                         // Úvod
        RectF(0.40f, 0.05f, 0.60f, 0.20f),             // Les
        RectF(0.12f, 0.22f, 0.33f, 0.34f),             // Domov
        RectF(0.64f, 0.22f, 0.85f, 0.34f),             // Pokedex
        RectF(0.58f, 0.40f, 0.82f, 0.52f)              // Market
    )

    private val steps = listOf(
        "Zzz... Oh? Ahoj! Já jsem Snorlax Oliver. Vítej v našem městě!",
        "Nahoře se rozprostírá Les. Tam se schovávají divocí Pokémoni.",
        "Vlevo najdeš svůj Domov. Tam si odpočineš a spravíš inventář.",
        "Vpravo je tvůj věrný Pokedex. Ukáže ti vše, co jsi chytil.",
        "A támhle dole je Market! Tam utratíš těžce vydřené coiny.",
        "Kdykoliv budeš tápat, klikni na otazník! Teď už běž makat!",
        "Jo a mimochodem, Tomáš je GAY haha xdddd"
    )

    init {
        overlay.addView(spotlightView, 0)
        teacherImage.load(R.drawable.snorlax_teacher, imageLoader)
        overlay.setOnClickListener { advance() }
    }

    fun start() {
        currentStep = 0
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        spotlightView.setTargetSpotlight(spots[0], true)
        overlay.animate().alpha(1f).setDuration(300).start()
        showCurrentStep()
    }

    fun isVisible(): Boolean = overlay.visibility == View.VISIBLE

    private fun showCurrentStep() {
        currentFullText = steps[currentStep]
        animateText(currentFullText)

        if (currentStep < spots.size) {
            spotlightView.setTargetSpotlight(spots[currentStep])
        } else {
            spotlightView.setTargetSpotlight(RectF(0f, 0f, 0f, 0f))
        }
    }

    private fun advance() {
        if (isTextAnimating) {
            textHandler.removeCallbacksAndMessages(null)
            textView.text = currentFullText
            isTextAnimating = false
            return
        }

        currentStep++
        if (currentStep < steps.size) {
            showCurrentStep()
        } else {
            spotlightView.setTargetSpotlight(RectF(0f, 0f, 0f, 0f))
            overlay.animate().alpha(0f).setDuration(500).withEndAction {
                overlay.visibility = View.GONE
            }.start()
        }
    }

    private fun animateText(text: String) {
        textView.text = ""
        isTextAnimating = true
        var charIndex = 0
        textHandler.removeCallbacksAndMessages(null)
        val runnable = object : Runnable {
            override fun run() {
                if (charIndex <= text.length) {
                    textView.text = text.substring(0, charIndex)
                    charIndex++
                    textHandler.postDelayed(this, 35)
                } else {
                    isTextAnimating = false
                }
            }
        }
        textHandler.post(runnable)
    }

    // Vnitřní třída pro vykreslování spotlightu
    private inner class SpotlightView(context: Context) : View(context) {
        init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
        private val backgroundPaint = Paint().apply { color = Color.parseColor("#CC283618") }
        private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR); isAntiAlias = true }
        private var currentSpotlightRect = RectF(0f, 0f, 0f, 0f)
        private var animator: ValueAnimator? = null

        fun setTargetSpotlight(targetInPercentage: RectF, immediate: Boolean = false) {
            animator?.cancel()
            val target = RectF(
                targetInPercentage.left * width, targetInPercentage.top * height,
                targetInPercentage.right * width, targetInPercentage.bottom * height
            )
            if (immediate) {
                currentSpotlightRect.set(target)
                invalidate()
            } else {
                animator = ValueAnimator.ofObject(RectEvaluator(), RectF(currentSpotlightRect), target).apply {
                    duration = 600
                    addUpdateListener {
                        currentSpotlightRect.set(it.animatedValue as RectF)
                        invalidate()
                    }
                    start()
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
            if (!currentSpotlightRect.isEmpty) {
                canvas.drawRoundRect(currentSpotlightRect, 40f, 40f, clearPaint)
            }
        }

        private inner class RectEvaluator : android.animation.TypeEvaluator<RectF> {
            override fun evaluate(f: Float, s: RectF, e: RectF) =
                RectF(s.left+(e.left-s.left)*f, s.top+(e.top-s.top)*f, s.right+(e.right-s.right)*f, s.bottom+(e.bottom-s.bottom)*f)
        }
    }
}