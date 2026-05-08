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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_quest_journal, container, false)

        // Zavření deníku
        view.findViewById<ImageButton>(R.id.btnCloseJournal).setOnClickListener {
            selectedStageIndex = null
            parentFragmentManager.popBackStack()
        }

        // --- ÚPRAVA 1: PŘEPÍNÁNÍ STRÁNEK KLIKEM NA OKRAJE ---
        val paperBody = view.findViewById<View>(R.id.journalPaperBody)
        paperBody.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val width = v.width
                val x = event.x

                if (x < width * 0.25f) { // Klik vlevo (předchozí strana)
                    flipPage(-1, view)
                } else if (x > width * 0.75f) { // Klik vpravo (další strana)
                    flipPage(1, view)
                } else { // Klik uprostřed (reset výběru fáze)
                    selectedStageIndex = null
                    renderCurrentPage(view)
                }
            }
            true
        }

        loadDataAndSetup(view)
        return view
    }

    private fun flipPage(direction: Int, view: View) {
        if (unlockedQuests.isEmpty()) return

        val newIndex = currentPageIndex + direction
        if (newIndex in unlockedQuests.indices) {
            currentPageIndex = newIndex
            selectedStageIndex = null
            renderCurrentPage(view)
        }
    }

    private fun loadDataAndSetup(view: View) {
        val db = AppDatabase.getDatabase(requireContext())

        // Získání aktuální lokace z aktivity přes novou metodu
        val currentBiome = (activity as? MakromonMapActivity)?.getCurrentBiome() ?: BiomeType.TOWN

        lifecycleScope.launch(Dispatchers.IO) {
            // Načteme všechny questy a seřadíme je
            val allProgress = db.questDao().getAllQuests().sortedBy { it.lastUpdated }

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    unlockedQuests = allProgress

                    // Logika: Chceme otevřít deník na questu, který odpovídá lokaci
                    val targetQuestId = when (currentBiome) {
                        BiomeType.TOWN -> QuestRegistry.TOWN_INTRO_QUEST.id
                        BiomeType.MEADOW -> QuestRegistry.MEADOW_QUEST.id
                        else -> QuestRegistry.TOWN_INTRO_QUEST.id
                    }

                    // Najdeme index stránky v našem seznamu progresů
                    val locationPageIndex = unlockedQuests.indexOfFirst { it.questId == targetQuestId }

                    currentPageIndex = if (locationPageIndex != -1) {
                        locationPageIndex
                    } else {
                        // Pokud v dané lokaci nemáme rozjetý quest, skočíme na poslední stránku
                        (unlockedQuests.size - 1).coerceAtLeast(0)
                    }

                    renderCurrentPage(view)
                }
            }
        }
    }

    private fun renderCurrentPage(view: View) {
        if (unlockedQuests.isEmpty()) {
            view.findViewById<TextView>(R.id.chapterTitle).text = "Prázdný deník"
            view.findViewById<TextView>(R.id.storyText).text = "Zatím jsi nezačal žádné dobrodružství."
            return
        }

        val progress = unlockedQuests.getOrNull(currentPageIndex) ?: return

        // Mapování ID na definici (Gudwin vs Křovník)
        val quest = when (progress.questId) {
            QuestRegistry.TOWN_INTRO_QUEST.id -> QuestRegistry.TOWN_INTRO_QUEST
            QuestRegistry.MEADOW_QUEST.id -> QuestRegistry.MEADOW_QUEST
            else -> QuestRegistry.TOWN_INTRO_QUEST
        }

        val currentIndex = progress.currentStageIndex
        val isAllDone = progress.isCompleted
        val viewingIndex = selectedStageIndex ?: if (isAllDone) quest.stages.size - 1 else currentIndex

        // --- ZOBRAZENÍ STRÁNKOVÁNÍ ---
        val dateTextView = view.findViewById<TextView>(R.id.dateText)
        dateTextView.text = "Strana ${currentPageIndex + 1} / ${unlockedQuests.size}"
        // Malý vizuální hint, že se dá listovat
        dateTextView.append(if (currentPageIndex < unlockedQuests.size - 1) "  →" else "")

        // --- OBSAH (NPC a TEXT) ---
        val stageToDisplay = quest.stages.getOrNull(viewingIndex)
        if (stageToDisplay != null) {
            view.findViewById<TextView>(R.id.chapterTitle).text = stageToDisplay.title
            view.findViewById<ImageView>(R.id.npcPortrait).setImageResource(stageToDisplay.speakerResId)
            view.findViewById<TextView>(R.id.storyText).text = stageToDisplay.text

            val brief = when (stageToDisplay.requirementType) {
                RequirementType.WALK_STEPS -> "Cíl: ${stageToDisplay.targetValue} kroků"
                RequirementType.LOG_MEAL -> {
                    val currentVal = if (viewingIndex < currentIndex) stageToDisplay.targetValue else progress.metadata
                    "Cíl: Zapsat jídla ($currentVal/${stageToDisplay.targetValue})"
                }
                RequirementType.BATTLE_TYPE -> "Cíl: Souboj (${stageToDisplay.targetId})"
                RequirementType.SCAN_BARCODE -> "Cíl: Skenování kódu"
                RequirementType.VISIT_NODE -> "Cíl: Průzkum oblasti"
                else -> "Cíl: Hotovo"
            }
            view.findViewById<TextView>(R.id.taskListText).text = "• $brief"
        }

        renderStagesList(view, quest, currentIndex, isAllDone)
        drawTracker(view.findViewById(R.id.journalQuestProgressLine), quest.stages.size, if (isAllDone) quest.stages.size else currentIndex)
    }

    private fun indexPast(viewIdx: Int, currentIdx: Int): Boolean = viewIdx < currentIdx

    private fun renderStagesList(view: View, quest: QuestDefinition, currentIdx: Int, allDone: Boolean) {
        val listTextView = view.findViewById<TextView>(R.id.tvQuestStagesList)
        val builder = SpannableStringBuilder()

        quest.stages.forEachIndexed { index, stage ->
            // Tajemství: Hráč vidí jen to, co už potkal (aktuální index nebo historii)
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
                        renderCurrentPage(view)
                    }
                    override fun updateDrawState(ds: TextPaint) {
                        ds.isUnderlineText = false
                        ds.color = if (selectedStageIndex == index) Color.parseColor("#BC6C25") else Color.parseColor("#283618")
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                // Neobjevené části příběhu jsou skryté
                builder.append("[ ] ???\n")
            }
        }

        listTextView.text = builder
        listTextView.movementMethod = LinkMovementMethod.getInstance()
        listTextView.highlightColor = Color.TRANSPARENT
    }

    // --- POMOCNÉ METODY PRO KRESLENÍ (Zůstávají stejné) ---

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