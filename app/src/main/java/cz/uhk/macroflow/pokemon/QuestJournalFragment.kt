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
import androidx.lifecycle.lifecycleScope
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.pokemon.quests.QuestDefinition
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.RequirementType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuestJournalFragment : Fragment() {

    private var selectedStageIndex: Int? = null
    private var currentPageIndex = 0
    private var unlockedQuests: List<QuestProgressEntity> = emptyList()
    private lateinit var rootView: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        rootView = inflater.inflate(R.layout.fragment_quest_journal, container, false)

        // Zavření deníku
        rootView.findViewById<ImageButton>(R.id.btnCloseJournal).setOnClickListener {
            selectedStageIndex = null
            parentFragmentManager.popBackStack()
        }

        // PŘEPÍNÁNÍ STRÁNEK KLIKEM NA OKRAJE
        val paperBody = rootView.findViewById<View>(R.id.journalPaperBody)
        paperBody.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val width = v.width
                val x = event.x

                if (x < width * 0.25f) { // Klik vlevo
                    flipPage(-1)
                } else if (x > width * 0.75f) { // Klik vpravo
                    flipPage(1)
                } else { // Klik uprostřed
                    selectedStageIndex = null
                    renderCurrentPage()
                }
            }
            true
        }

        loadDataAndSetup()
        return rootView
    }

    // Metoda pro refresh zvenčí (z MakromonMapActivity)
    fun refreshData() {
        loadDataAndSetup()
    }

    private fun flipPage(direction: Int) {
        if (unlockedQuests.isEmpty()) return
        val newIndex = currentPageIndex + direction
        if (newIndex in unlockedQuests.indices) {
            currentPageIndex = newIndex
            selectedStageIndex = null
            renderCurrentPage()
        }
    }

    private fun loadDataAndSetup() {
        val db = AppDatabase.getDatabase(requireContext())
        val currentBiome = (activity as? MakromonMapActivity)?.getCurrentBiome() ?: BiomeType.TOWN

        lifecycleScope.launch(Dispatchers.IO) {
            val allProgress = db.questDao().getAllQuests().sortedBy { it.lastUpdated }

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    unlockedQuests = allProgress

                    // Pokud otevíráme deník poprvé (ne při refresh), najdeme správnou stranu podle biomu
                    if (selectedStageIndex == null && unlockedQuests.isNotEmpty()) {
                        val targetQuestId = when (currentBiome) {
                            BiomeType.TOWN -> QuestRegistry.TOWN_INTRO_QUEST.id
                            BiomeType.MEADOW -> QuestRegistry.MEADOW_QUEST.id
                            else -> QuestRegistry.TOWN_INTRO_QUEST.id
                        }
                        val locationIndex = unlockedQuests.indexOfFirst { it.questId == targetQuestId }
                        if (locationIndex != -1) currentPageIndex = locationIndex
                    }

                    renderCurrentPage()
                }
            }
        }
    }

    private fun renderCurrentPage() {
        if (!::rootView.isInitialized) return

        if (unlockedQuests.isEmpty()) {
            rootView.findViewById<TextView>(R.id.chapterTitle).text = "Prázdný deník"
            rootView.findViewById<TextView>(R.id.storyText).text = "Zatím jsi nezačal žádné dobrodružství."
            return
        }

        val progress = unlockedQuests.getOrNull(currentPageIndex) ?: return
        val quest = when (progress.questId) {
            QuestRegistry.TOWN_INTRO_QUEST.id -> QuestRegistry.TOWN_INTRO_QUEST
            QuestRegistry.MEADOW_QUEST.id -> QuestRegistry.MEADOW_QUEST
            else -> QuestRegistry.TOWN_INTRO_QUEST
        }

        val currentIndex = progress.currentStageIndex
        val isAllDone = progress.isCompleted
        val viewingIndex = selectedStageIndex ?: if (isAllDone) quest.stages.size - 1 else currentIndex

        // STRÁNKOVÁNÍ
        val dateTextView = rootView.findViewById<TextView>(R.id.dateText)
        dateTextView.text = "Strana ${currentPageIndex + 1} / ${unlockedQuests.size}"
        if (currentPageIndex < unlockedQuests.size - 1) dateTextView.append("  →")

        // OBSAH
        val stageToDisplay = quest.stages.getOrNull(viewingIndex)
        if (stageToDisplay != null) {
            rootView.findViewById<TextView>(R.id.chapterTitle).text = stageToDisplay.title
            rootView.findViewById<ImageView>(R.id.npcPortrait).setImageResource(stageToDisplay.speakerResId)
            rootView.findViewById<TextView>(R.id.storyText).text = stageToDisplay.text

            // Dynamický výpočet cíle (bere data z progressu)
            val brief = when (stageToDisplay.requirementType) {
                RequirementType.LOG_MEAL -> {
                    val currentVal = if (viewingIndex < currentIndex || isAllDone) {
                        stageToDisplay.targetValue
                    } else {
                        progress.metadata.toIntOrNull() ?: 0
                    }
                    "Cíl: Zapsat jídla ($currentVal / ${stageToDisplay.targetValue})"
                }
                RequirementType.WALK_STEPS -> {
                    val currentSteps = if (viewingIndex < currentIndex || isAllDone) {
                        stageToDisplay.targetValue
                    } else {
                        progress.metadata.toIntOrNull() ?: 0
                    }
                    "Cíl: Kroky ($currentSteps / ${stageToDisplay.targetValue})"
                }
                RequirementType.VISIT_NODE -> {
                    val visited = if (viewingIndex < currentIndex || isAllDone) {
                        stageToDisplay.targetValue
                    } else {
                        progress.metadata.split(",").filter { it.isNotBlank() }.size
                    }
                    "Cíl: Průzkum ($visited / ${stageToDisplay.targetValue})"
                }
                RequirementType.BATTLE_TYPE -> "Cíl: Souboj (${stageToDisplay.targetId})"
                RequirementType.SCAN_BARCODE -> "Cíl: Skenování kódu"
                else -> if (viewingIndex < currentIndex || isAllDone) "Cíl: Splněno" else "Cíl: Aktivní"
            }
            rootView.findViewById<TextView>(R.id.taskListText).text = "• $brief"
        }

        renderStagesList(quest, currentIndex, isAllDone)
        drawTracker(rootView.findViewById(R.id.journalQuestProgressLine), quest.stages.size, if (isAllDone) quest.stages.size else currentIndex)
    }

    private fun renderStagesList(quest: QuestDefinition, currentIdx: Int, allDone: Boolean) {
        val listTextView = rootView.findViewById<TextView>(R.id.tvQuestStagesList)
        val builder = SpannableStringBuilder()

        quest.stages.forEachIndexed { index, stage ->
            val isKnown = allDone || index <= currentIdx
            if (isKnown) {
                val prefix = when {
                    allDone || index < currentIdx -> "[X] "
                    index == currentIdx -> "[>] "
                    else -> "[ ] "
                }
                val start = builder.length
                builder.append("$prefix${index + 1}. ${stage.title}\n")
                val end = builder.length

                builder.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        selectedStageIndex = index
                        renderCurrentPage()
                    }
                    override fun updateDrawState(ds: TextPaint) {
                        ds.isUnderlineText = false
                        ds.color = if (selectedStageIndex == index) Color.parseColor("#BC6C25") else Color.parseColor("#283618")
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                builder.append("[ ] ???\n")
            }
        }

        listTextView.text = builder
        listTextView.movementMethod = LinkMovementMethod.getInstance()
        listTextView.highlightColor = Color.TRANSPARENT
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