package cz.uhk.macroflow.pokemon

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.quests.QuestDefinition
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.RequirementType
import java.text.SimpleDateFormat
import java.util.*

class QuestJournalFragment : Fragment() {

    // Držíme informaci o tom, na co uživatel kliknul (mimo fragment kvůli zachování stavu při rebuildování)
    private var selectedStageIndex: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_quest_journal, container, false)

        view.findViewById<ImageButton>(R.id.btnCloseJournal).setOnClickListener {
            selectedStageIndex = null // Reset při zavření
            parentFragmentManager.popBackStack()
        }

        setupJournalData(view)
        return view
    }

    private fun setupJournalData(view: View) {
        val activity = requireActivity() as MakromonMapActivity
        val progress = activity.questManager.getCurrentProgress()
        val quest = QuestRegistry.TOWN_INTRO_QUEST
        val currentIndex = progress?.currentStageIndex ?: 0
        val isAllDone = progress?.isCompleted == true

        // Pokud není nic vybráno, zobrazujeme buď aktuální progres, nebo finální stav
        val viewingIndex = selectedStageIndex ?: currentIndex

        view.findViewById<TextView>(R.id.dateText).text =
            SimpleDateFormat("dd. MM. yyyy", Locale.getDefault()).format(Date())

        // RESET: Kliknutí na pozadí papíru vrátí výchozí stav
        view.findViewById<View>(R.id.journalPaperBody).setOnClickListener {
            selectedStageIndex = null
            setupJournalData(view)
        }

        // 1. LEVÁ STRANA: Dynamický obsah
        val stageToDisplay = quest.stages.getOrNull(viewingIndex)

        if (isAllDone && selectedStageIndex == null) {
            // Zobrazení po dokončení celého questu
            view.findViewById<TextView>(R.id.chapterTitle).text = "Dobrodružství dokončeno"
            view.findViewById<ImageView>(R.id.npcPortrait).setImageResource(R.drawable.gudwin_oliver)
            view.findViewById<TextView>(R.id.taskListText).text = "Všechny cíle splněny!"
            view.findViewById<TextView>(R.id.storyText).text =
                "Dokázal jsi to! Town už pro tebe nemá žádná tajemství. " +
                        "Meadow je nyní přístupné a tvá cesta za poznáním Makromonů může skutečně začít. " +
                        "Hodně štěstí, trenére!"
        } else if (stageToDisplay != null) {
            // Zobrazení konkrétní fáze (buď aktuální nebo vybrané z historie)
            view.findViewById<TextView>(R.id.chapterTitle).text = stageToDisplay.title
            view.findViewById<ImageView>(R.id.npcPortrait).setImageResource(stageToDisplay.speakerResId)
            view.findViewById<TextView>(R.id.storyText).text = stageToDisplay.text

            val brief = when (stageToDisplay.requirementType) {
                RequirementType.WALK_STEPS -> "Cíl: ${stageToDisplay.targetValue} kroků"
                RequirementType.VISIT_NODE -> "Cíl: Průzkum místa"
                RequirementType.CAPTURE_SPECIFIC -> "Cíl: Chytit Makromona"
                RequirementType.LOG_MEAL -> "Cíl: Zapsat jídla (${progress?.metadata ?: 0}/${stageToDisplay.targetValue})"
                RequirementType.BATTLE_TYPE -> "Cíl: Porazit ${stageToDisplay.targetValue}x typ ${stageToDisplay.targetId}"
                RequirementType.SCAN_BARCODE -> "Cíl: Naskenovat čárový kód"
                else -> "Pokračuj v příběhu"
            }
            view.findViewById<TextView>(R.id.taskListText).text = "• $brief"
        }

        // 2. PRAVÁ STRANA: Seznam fází
        renderStagesList(view, quest, currentIndex, isAllDone)

        // 3. TRACKER
        val progressContainer = view.findViewById<LinearLayout>(R.id.journalQuestProgressLine)
        drawTracker(progressContainer, quest.stages.size, if (isAllDone) quest.stages.size else currentIndex)
    }

    private fun renderStagesList(view: View, quest: QuestDefinition, currentIdx: Int, allDone: Boolean) {
        val listTextView = view.findViewById<TextView>(R.id.tvQuestStagesList)
        val builder = SpannableStringBuilder()

        quest.stages.forEachIndexed { index, stage ->
            val prefix = when {
                allDone || index < currentIdx -> "[X] "
                index == currentIdx -> "[>] "
                else -> "[ ] "
            }

            val start = builder.length
            builder.append("$prefix${index + 1}. ${stage.title}\n")
            val end = builder.length

            // Klikací zóna pro každou fázi
            builder.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    selectedStageIndex = index
                    setupJournalData(view)
                }
                override fun updateDrawState(ds: TextPaint) {
                    ds.isUnderlineText = false
                    // Zvýraznění vybrané fáze hnědou, ostatní černou/tmavě zelenou
                    ds.color = if (selectedStageIndex == index) Color.parseColor("#BC6C25") else Color.parseColor("#283618")
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        listTextView.text = builder
        listTextView.movementMethod = LinkMovementMethod.getInstance()
        listTextView.highlightColor = Color.TRANSPARENT // Odstraní ošklivý šedý obdélník při kliku
    }

    private fun drawTracker(container: LinearLayout, total: Int, activeIndex: Int) {
        container.removeAllViews()
        val dp = resources.displayMetrics.density
        val dotSize = (8 * dp).toInt()
        val ringSize = (16 * dp).toInt()

        val colorDone = Color.parseColor("#606C38")
        val colorActive = Color.parseColor("#BC6C25")
        val colorPending = Color.parseColor("#DDA15E")

        for (i in 0 until total) {
            val isActive = i == activeIndex
            val isCompleted = i < activeIndex

            if (isActive) {
                val ringWrapper = FrameLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(ringSize, ringSize)
                }
                ringWrapper.addView(View(requireContext()).apply {
                    layoutParams = FrameLayout.LayoutParams(ringSize, ringSize)
                    background = circleOutline(colorActive, 2f * dp)
                })
                ringWrapper.addView(View(requireContext()).apply {
                    layoutParams = FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER)
                    background = circleFill(colorActive)
                })
                container.addView(ringWrapper)
            } else {
                container.addView(View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                        setMargins(0, (ringSize - dotSize) / 2, 0, (ringSize - dotSize) / 2)
                    }
                    background = circleFill(if (isCompleted) colorDone else colorPending)
                    alpha = if (isCompleted) 1.0f else 0.5f
                })
            }

            if (i < total - 1) {
                container.addView(View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams((15 * dp).toInt(), (2 * dp).toInt()).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    setBackgroundColor(if (i < activeIndex) colorDone else colorPending)
                    alpha = if (i < activeIndex) 1.0f else 0.5f
                })
            }
        }
    }

    private fun circleFill(color: Int): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        override fun draw(c: Canvas) { c.drawOval(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), paint) }
        override fun setAlpha(a: Int) { paint.alpha = a }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun circleOutline(color: Int, strokeWidth: Float): Drawable = object : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; this.strokeWidth = strokeWidth }
        override fun draw(c: Canvas) {
            val inset = strokeWidth / 2f
            c.drawOval(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset, paint)
        }
        override fun setAlpha(a: Int) { paint.alpha = a }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}