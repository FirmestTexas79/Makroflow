package cz.uhk.macroflow.pokemon

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.ImageLoader
import coil.load
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.quests.QuestStage

class QuestDialogManager(
    private val context: Context,
    private val overlay: FrameLayout,
    private val textView: TextView,
    private val speakerImage: ImageView,
    private val imageLoader: ImageLoader,
    private val questProgressLine: LinearLayout
) {
    private val textHandler = Handler(Looper.getMainLooper())
    private val spotlightView = SpotlightView(context)
    private var isTextAnimating = false

    private var textPages = listOf<String>()
    private var currentPageIndex = 0

    private var isTutorialActive = false
    private var currentTutorialStep = 0

    private val speakerNameView: TextView? by lazy {
        overlay.findViewById(R.id.tutorialSpeakerName)
    }
    private val questStageNameView: TextView? by lazy {
        overlay.findViewById(R.id.questStageName)
    }

    private val tutorialSpots = listOf(
        RectF(0f, 0f, 0f, 0f),
        RectF(0.40f, 0.05f, 0.60f, 0.20f),
        RectF(0.12f, 0.22f, 0.33f, 0.34f),
        RectF(0.64f, 0.22f, 0.85f, 0.34f),
        RectF(0.58f, 0.40f, 0.82f, 0.52f),
        RectF(0f, 0f, 0f, 0f),
        RectF(0f, 0f, 0f, 0f)
    )
    private val tutorialSteps = listOf(
        "Zzz... Oh? Ahoj! Já jsem Gudwin Oliver. Vítej v našem městě!",
        "Nahoře se rozprostírá Les. Tam se schovávají divocí Makromoni.",
        "Vlevo najdeš svůj Domov. Tam si odpočineš a spravíš inventář.",
        "Vpravo je tvůj věrný Pokedex. Ukáže ti vše, co jsi chytil.",
        "A támhle dole je Market! Tam utratíš těžce vydřené coiny.",
        "Kdykoliv budeš tápat, klikni na otazník! Teď už běž makat!",
        "Jo a mimochodem, Tomáš je GAY haha xdddd"
    )

    init {
        overlay.addView(spotlightView, 0)
        overlay.setOnClickListener { advance() }

        textView.typeface = Typeface.MONOSPACE
        textView.gravity = Gravity.TOP or Gravity.START

        val lp = textView.layoutParams
        lp.height = (110 * context.resources.displayMetrics.density).toInt()
        textView.layoutParams = lp
    }

    fun startTutorial() {
        isTutorialActive = true
        questProgressLine.visibility = View.GONE
        questStageNameView?.visibility = View.GONE
        speakerNameView?.text = "Gudwin Oliver"
        speakerNameView?.visibility = View.VISIBLE
        currentTutorialStep = 0
        speakerImage.load(R.drawable.makromon_30_gudwin, imageLoader)
        showOverlay()
        showCurrentTutorialStep()
    }

    private fun showCurrentTutorialStep() {
        val fullText = tutorialSteps[currentTutorialStep]
        preparePagesAndShow(fullText)
        val spot = if (currentTutorialStep < tutorialSpots.size) tutorialSpots[currentTutorialStep]
        else RectF(0f, 0f, 0f, 0f)
        spotlightView.setTargetSpotlight(spot)
    }

    fun showQuestDialog(
        speakerResource: Int,
        speakerName: String = "",
        stageName: String = "",
        text: String,
        totalSteps: Int,
        currentStepIndex: Int,
        spotlightRect: RectF? = null
    ) {
        isTutorialActive = false
        questProgressLine.visibility = View.VISIBLE
        speakerImage.load(speakerResource, imageLoader)

        if (speakerName.isNotBlank()) {
            speakerNameView?.text = speakerName.uppercase()
            speakerNameView?.visibility = View.VISIBLE
        } else {
            speakerNameView?.visibility = View.GONE
        }

        if (stageName.isNotBlank()) {
            questStageNameView?.text = stageName
            questStageNameView?.visibility = View.VISIBLE
        } else {
            questStageNameView?.visibility = View.GONE
        }

        // Centrovat NPC svisle na výšku dialog boxu
        centerNpcOnDialog()

        drawPixelQuestLine(totalSteps, currentStepIndex)
        showOverlay()
        spotlightView.setTargetSpotlight(spotlightRect ?: RectF(0f, 0f, 0f, 0f))
        preparePagesAndShow(text)
    }

    private fun centerNpcOnDialog() {
        val dialogBox = overlay.findViewById<View>(R.id.tutorialDialogBox) ?: return
        dialogBox.post {
            val dialogH = dialogBox.height
            val npcH = speakerImage.layoutParams.height
            val dialogMarginBottom = (20 * context.resources.displayMetrics.density).toInt()
            val newNpcMarginBottom = dialogMarginBottom + (dialogH - npcH) / 2
            val lp = speakerImage.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = newNpcMarginBottom.coerceAtLeast(dialogMarginBottom)
            speakerImage.layoutParams = lp
        }
    }

    private fun preparePagesAndShow(fullText: String) {
        textPages = fullText.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        currentPageIndex = 0
        showCurrentPage()
    }

    private fun showCurrentPage() {
        if (currentPageIndex < textPages.size) {
            animateText(textPages[currentPageIndex])
        } else {
            if (isTutorialActive && currentTutorialStep < tutorialSteps.size - 1) {
                currentTutorialStep++
                showCurrentTutorialStep()
            } else {
                hide()
            }
        }
    }

    private fun advance() {
        if (isTextAnimating) {
            textHandler.removeCallbacksAndMessages(null)
            textView.text = textPages[currentPageIndex]
            isTextAnimating = false
        } else {
            currentPageIndex++
            showCurrentPage()
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
                    textHandler.postDelayed(this, 30)
                } else {
                    isTextAnimating = false
                }
            }
        }
        textHandler.post(runnable)
    }

    private fun drawPixelQuestLine(total: Int, activeIndex: Int) {
        questProgressLine.removeAllViews()
        val dp = context.resources.displayMetrics.density

        val dotSize  = (10 * dp).toInt()
        val ringSize = (20 * dp).toInt()
        val lineH    = (2 * dp).toInt()

        val colorDone    = Color.parseColor("#606C38")
        val colorActive  = Color.parseColor("#BC6C25")
        val colorPending = Color.parseColor("#8D8D8D")

        for (i in 0 until total) {
            val isActive = i == activeIndex
            val isDone   = i < activeIndex

            if (isActive) {
                val ringWrapper = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(ringSize, ringSize)
                }
                ringWrapper.addView(View(context).apply {
                    layoutParams = FrameLayout.LayoutParams(ringSize, ringSize)
                    background = circleOutline(colorActive, 2.5f * dp)
                })
                ringWrapper.addView(View(context).apply {
                    layoutParams = FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER)
                    background = circleFill(colorActive)
                })
                questProgressLine.addView(ringWrapper)
            } else {
                questProgressLine.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                        topMargin    = (ringSize - dotSize) / 2
                        bottomMargin = (ringSize - dotSize) / 2
                    }
                    background = circleFill(if (isDone) colorDone else colorPending)
                })
            }

            if (i < total - 1) {
                val isDotted = (i == activeIndex - 1) || (i == activeIndex)
                val segColor = if (i < activeIndex) colorDone else colorPending

                val segWrapper = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ringSize, 1f)
                }

                if (isDotted) {
                    segWrapper.addView(object : View(context) {
                        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = segColor
                            strokeWidth = lineH.toFloat()
                            pathEffect = DashPathEffect(floatArrayOf(6f * dp, 5f * dp), 0f)
                            style = Paint.Style.STROKE
                        }
                        override fun onDraw(canvas: Canvas) {
                            canvas.drawLine(0f, height / 2f, width.toFloat(), height / 2f, paint)
                        }
                    }.apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    })
                } else {
                    segWrapper.addView(View(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT, lineH
                        ).apply { gravity = Gravity.CENTER_VERTICAL }
                        setBackgroundColor(segColor)
                    })
                }
                questProgressLine.addView(segWrapper)
            }
        }
    }

    private fun circleFill(color: Int): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        override fun draw(c: Canvas) {
            val b = bounds
            c.drawOval(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
        }
        override fun setAlpha(a: Int) { paint.alpha = a }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun circleOutline(color: Int, strokeWidth: Float): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        override fun draw(c: Canvas) {
            val b = bounds
            val inset = strokeWidth / 2f
            c.drawOval(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset, paint)
        }
        override fun setAlpha(a: Int) { paint.alpha = a }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun showOverlay() {
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(300).start()
    }

    fun hide() {
        spotlightView.setTargetSpotlight(RectF(0f, 0f, 0f, 0f))
        overlay.animate().alpha(0f).setDuration(300).withEndAction {
            overlay.visibility = View.GONE
            isTutorialActive = false
            questProgressLine.visibility = View.GONE
            speakerNameView?.visibility = View.GONE
            questStageNameView?.visibility = View.GONE
        }.start()
    }

    fun isVisible(): Boolean = overlay.visibility == View.VISIBLE

    fun showQuestUpdate(nextStage: QuestStage?) {
        if (nextStage == null) {
            // Quest je kompletně hotový
            showQuestDialog(
                speakerResource = R.drawable.gudwin_oliver,
                speakerName = "Gudwin Oliver",
                stageName = "Hotovo",
                text = "To je pro teď vše! Jsi připraven na velké dobrodružství.",
                totalSteps = 3,
                currentStepIndex = 3
            )
            return
        }

        // Automaticky zobrazíme dialog pro další krok
        showQuestDialog(
            speakerResource = nextStage.speakerResId,
            speakerName = "Gudwin Oliver",
            stageName = nextStage.title,
            text = nextStage.text,
            totalSteps = 3, // Ideálně brát z QuestRegistry.TOWN_INTRO_QUEST.stages.size
            currentStepIndex = 0 // Index se v dialogu stejně používá hlavně pro kreslení čáry
        )
    }

    private inner class SpotlightView(context: Context) : View(context) {
        init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
        private val backgroundPaint = Paint().apply { color = Color.parseColor("#CC283618") }
        private val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            isAntiAlias = false
        }
        private var currentSpotlightRect = RectF(0f, 0f, 0f, 0f)
        private var animator: ValueAnimator? = null

        fun setTargetSpotlight(targetInPercentage: RectF) {
            animator?.cancel()
            val target = RectF(
                targetInPercentage.left * width,  targetInPercentage.top * height,
                targetInPercentage.right * width, targetInPercentage.bottom * height
            )
            animator = ValueAnimator.ofObject(
                android.animation.TypeEvaluator<RectF> { f, s, e ->
                    RectF(
                        s.left + (e.left - s.left) * f,
                        s.top  + (e.top  - s.top)  * f,
                        s.right + (e.right - s.right) * f,
                        s.bottom + (e.bottom - s.bottom) * f
                    )
                }, currentSpotlightRect, target
            ).apply {
                duration = 500
                addUpdateListener { currentSpotlightRect.set(it.animatedValue as RectF); invalidate() }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
            if (!currentSpotlightRect.isEmpty) canvas.drawRect(currentSpotlightRect, clearPaint)
        }
    }
}