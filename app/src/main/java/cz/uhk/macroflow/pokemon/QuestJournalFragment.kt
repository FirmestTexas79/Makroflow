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
import cz.uhk.macroflow.pokemon.quests.QuestProgression
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

        // Listování kapitolami šipkami v hlavičce (dřív neviditelné klepání na okraje stránky)
        rootView.findViewById<View>(R.id.btnPrevPage).setOnClickListener { flipPage(-1) }
        rootView.findViewById<View>(R.id.btnNextPage).setOnClickListener { flipPage(1) }

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
        val mapActivity = activity as? MakromonMapActivity
        val currentBiome = mapActivity?.getCurrentBiome() ?: BiomeType.TOWN
        // V biomu bez vlastního questu (hory) otevřeme stránku právě aktivního questu
        val targetQuestId = BiomeRegistry.definition(currentBiome)?.questId
            ?: mapActivity?.questManager?.getActiveQuestId()
            ?: QuestRegistry.TOWN_INTRO_QUEST.id

        lifecycleScope.launch(Dispatchers.IO) {
            val allProgress = db.questDao().getAllQuests().sortedBy { it.lastUpdated }

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    unlockedQuests = allProgress

                    // Pokud otevíráme deník poprvé (ne při refresh), najdeme správnou stranu podle biomu
                    if (selectedStageIndex == null && unlockedQuests.isNotEmpty()) {
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
            rootView.findViewById<TextView>(R.id.chapterLabel).text = "DENÍK"
            rootView.findViewById<TextView>(R.id.chapterTitle).text = "Prázdný deník"
            rootView.findViewById<TextView>(R.id.storyText).text = "Zatím jsi nezačal žádné dobrodružství."
            rootView.findViewById<View>(R.id.taskListText).visibility = View.GONE
            listOf(R.id.btnPrevPage, R.id.btnNextPage).forEach { rootView.findViewById<View>(it).alpha = 0.2f }
            return
        }
        rootView.findViewById<View>(R.id.taskListText).visibility = View.VISIBLE

        val progress = unlockedQuests.getOrNull(currentPageIndex) ?: return
        val quest = QuestRegistry.byId(progress.questId) ?: QuestRegistry.TOWN_INTRO_QUEST

        val currentIndex = progress.currentStageIndex
        val isAllDone = progress.isCompleted
        val viewingIndex = selectedStageIndex ?: if (isAllDone) quest.stages.size - 1 else currentIndex

        // STRÁNKOVÁNÍ
        rootView.findViewById<TextView>(R.id.chapterLabel).text = chapterName(quest.id)
        val done = if (isAllDone) quest.stages.size else currentIndex
        rootView.findViewById<TextView>(R.id.dateText).text =
            "Kapitola ${currentPageIndex + 1} z ${unlockedQuests.size} · splněno $done/${quest.stages.size}"
        rootView.findViewById<View>(R.id.btnPrevPage).alpha = if (currentPageIndex > 0) 1f else 0.2f
        rootView.findViewById<View>(R.id.btnNextPage).alpha = if (currentPageIndex < unlockedQuests.size - 1) 1f else 0.2f

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
                        QuestProgression.currentValue(stageToDisplay, progress.metadata)
                    }
                    "Cíl: Průzkum ($visited / ${stageToDisplay.targetValue})"
                }
                RequirementType.BATTLE_TYPE -> "Cíl: Souboj (${stageToDisplay.targetId})"
                RequirementType.BATTLE_BIOME -> {
                    val value = if (viewingIndex < currentIndex || isAllDone) stageToDisplay.targetValue
                        else QuestProgression.currentValue(stageToDisplay, progress.metadata)
                    "Cíl: Výhry v horách ($value / ${stageToDisplay.targetValue})"
                }
                RequirementType.HIT_TARGET -> {
                    val n = cz.uhk.macroflow.energy.Adherence.Nutrient.from(stageToDisplay.targetId)
                    if (n == null || viewingIndex < currentIndex || isAllDone) "Cíl: Splněno"
                    else {
                        val pct = QuestProgression.currentValue(stageToDisplay, progress.metadata)
                        "Cíl: ${n.label} dnes $pct % tvého cíle (potřeba ${n.minPct}–${n.maxPct} %)"
                    }
                }
                RequirementType.SCAN_BARCODE -> {
                    val scanned = if (viewingIndex < currentIndex || isAllDone) {
                        stageToDisplay.targetValue
                    } else {
                        QuestProgression.currentValue(stageToDisplay, progress.metadata)
                    }
                    "Cíl: Naskenuj kód jídla v sekci Jídlo ($scanned / ${stageToDisplay.targetValue})"
                }
                else -> if (viewingIndex < currentIndex || isAllDone) "Cíl: Splněno" else "Cíl: Aktivní"
            }
            rootView.findViewById<TextView>(R.id.taskListText).text = brief.removePrefix("Cíl: ").let { "🎯 $it" }
            capStoryHeight()
        }

        renderStagesList(quest, currentIndex, isAllDone)
        drawTracker(rootView.findViewById(R.id.journalQuestProgressLine), quest.stages.size, if (isAllDone) quest.stages.size else currentIndex)
    }

    /** Příběh může být dlouhý – karta ale nemá přerůst obrazovku, text se pak posouvá. */
    private fun capStoryHeight() {
        val scroll = rootView.findViewById<View>(R.id.storyScroll)
        val max = (170 * resources.displayMetrics.density).toInt()
        scroll.layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        scroll.requestLayout()
        scroll.post {
            if (scroll.height > max) { scroll.layoutParams.height = max; scroll.requestLayout() }
        }
    }

    private fun chapterName(questId: String): String = when (questId) {
        QuestRegistry.TOWN_INTRO_QUEST.id -> "MĚSTO"
        QuestRegistry.MEADOW_QUEST.id -> "LOUKA"
        QuestRegistry.MOUNTAINS_QUEST.id -> "HORY"
        else -> "DOBRODRUŽSTVÍ"
    }

    private fun renderStagesList(quest: QuestDefinition, currentIdx: Int, allDone: Boolean) {
        val listTextView = rootView.findViewById<TextView>(R.id.tvQuestStagesList)
        val builder = SpannableStringBuilder()
        val colorDone = Color.parseColor("#606C38")
        val colorActive = Color.parseColor("#BC6C25")
        val colorLocked = Color.parseColor("#99283618")

        quest.stages.forEachIndexed { index, stage ->
            val done = allDone || index < currentIdx
            val active = !allDone && index == currentIdx
            val start = builder.length
            if (done || active) {
                builder.append(if (done) "✓  " else "▸  ").append(stage.title)
                val end = builder.length
                val selected = selectedStageIndex == index
                builder.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        selectedStageIndex = index
                        renderCurrentPage()
                    }
                    override fun updateDrawState(ds: TextPaint) {
                        ds.isUnderlineText = selected
                        ds.color = if (active || selected) colorActive else colorDone
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                builder.append("·  ???")
                builder.setSpan(android.text.style.ForegroundColorSpan(colorLocked), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (index < quest.stages.size - 1) builder.append("\n")
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