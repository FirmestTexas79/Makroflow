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

        // Kniha nemá přerůst obrazovku: nejvýš ~68 % výšky (a ne víc než 600 dp)
        val dm = resources.displayMetrics
        rootView.findViewById<View>(R.id.journalBook).layoutParams.height =
            minOf((dm.heightPixels * 0.68f).toInt(), (600 * dm.density).toInt())

        // LISTOVÁNÍ: šipky v rozích, klepnutí na okraj stránky, tah prstem
        rootView.findViewById<View>(R.id.btnPrevPage).setOnClickListener { flipPage(-1) }
        rootView.findViewById<View>(R.id.btnNextPage).setOnClickListener { flipPage(1) }
        val swipe = android.view.GestureDetector(requireContext(), object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent) = true
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (kotlin.math.abs(dx) < 60 * dm.density || kotlin.math.abs(vx) < kotlin.math.abs(vy)) return false
                flipPage(if (dx < 0) 1 else -1)
                return true
            }
            override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                val w = rootView.findViewById<View>(R.id.journalPaperBody).width
                when {
                    e.x < w * 0.12f -> flipPage(-1)
                    e.x > w * 0.88f -> flipPage(1)
                    else -> { selectedStageIndex = null; renderCurrentPage() }
                }
                return true
            }
        })
        rootView.findViewById<View>(R.id.journalPaperBody).setOnTouchListener { _, event -> swipe.onTouchEvent(event) }
        // Posuvné texty si dotyk berou samy – tah do strany jim proto „odposloucháme“ zvlášť
        val flingOnly = android.view.GestureDetector(requireContext(), object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (kotlin.math.abs(dx) < 60 * dm.density || kotlin.math.abs(vx) < kotlin.math.abs(vy) * 1.5f) return false
                flipPage(if (dx < 0) 1 else -1)
                return true
            }
        })
        listOf(R.id.storyScroll, R.id.stagesScroll).forEach { id ->
            rootView.findViewById<View>(id).setOnTouchListener { _, e -> flingOnly.onTouchEvent(e); false }
        }

        loadDataAndSetup()
        // Záložky: Postava / Příběh / Denní úkoly / Suroviny (docs/adr/0029, 0034)
        rootView.findViewById<View>(R.id.tabCharacter).setOnClickListener { showTab(Tab.CHARACTER) }
        rootView.findViewById<View>(R.id.tabStory).setOnClickListener { showTab(Tab.STORY) }
        rootView.findViewById<View>(R.id.tabDaily).setOnClickListener { showTab(Tab.DAILY) }
        rootView.findViewById<View>(R.id.tabResources).setOnClickListener { showTab(Tab.RESOURCES) }
        rootView.findViewById<View>(R.id.tabAwards).setOnClickListener { showTab(Tab.AWARDS) }
        showTab(if (showDailyFirst) Tab.DAILY else Tab.CHARACTER)
        return rootView
    }

    // Metoda pro refresh zvenčí (z MakromonMapActivity)
    fun refreshData() {
        loadDataAndSetup()
        when (tab) {
            Tab.DAILY -> renderDaily()
            Tab.CHARACTER -> renderCharacter()
            Tab.RESOURCES -> renderResources()
            Tab.AWARDS -> renderAwards()
            Tab.STORY -> {}
        }
    }

    // ── Denní úkoly ─────────────────────────────────────────────────────────

    private enum class Tab { CHARACTER, STORY, DAILY, RESOURCES, AWARDS }
    private var tab = Tab.CHARACTER
    /** Nastaví se před zobrazením, když má deník otevřít rovnou denní úkoly. */
    var showDailyFirst = false

    private fun showTab(t: Tab) {
        tab = t
        val story = t == Tab.STORY
        val storyViews = listOf(R.id.leftPage, R.id.rightPage, R.id.bindingShadowContainer, R.id.btnPrevPage, R.id.btnNextPage)
        storyViews.forEach { rootView.findViewById<View>(it)?.visibility = if (story) View.VISIBLE else View.GONE }
        rootView.findViewById<View>(R.id.dailyPage).visibility = if (t == Tab.DAILY) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.characterPage).visibility = if (t == Tab.CHARACTER) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.resourcesPage).visibility = if (t == Tab.RESOURCES) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.awardsPage).visibility = if (t == Tab.AWARDS) View.VISIBLE else View.GONE
        mapOf(Tab.CHARACTER to R.id.tabCharacter, Tab.STORY to R.id.tabStory, Tab.DAILY to R.id.tabDaily, Tab.RESOURCES to R.id.tabResources,
            Tab.AWARDS to R.id.tabAwards)
            .forEach { (k, id) -> rootView.findViewById<View>(id).alpha = if (k == t) 1f else 0.55f }
        when (t) {
            Tab.DAILY -> renderDaily()
            Tab.CHARACTER -> renderCharacter()
            Tab.RESOURCES -> renderResources()
            Tab.AWARDS -> renderAwards()
            Tab.STORY -> {}
        }
    }

    // ── Postava a suroviny (docs/adr/0034) ──────────────────────────────────

    private var selectedSkill = cz.uhk.macroflow.pokemon.skills.Skill.CATCHING

    private var gearTab = cz.uhk.macroflow.pokemon.skills.GearTab.EQUIPS

    private fun renderCharacter() {
        val ctx = context?.applicationContext ?: return
        val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
        viewLifecycleOwner.lifecycleScope.launch {
            val (state, team, equipped) = withContext(Dispatchers.IO) {
                Triple(SS.state(ctx), SS.team(ctx), SS.equippedAll(ctx))
            }
            if (!isAdded) return@launch
            cz.uhk.macroflow.pokemon.skills.ui.JournalPages.character(
                rootView.findViewById(R.id.characterPage), state, team.size, selectedSkill, equipped, gearTab,
                onSelect = { selectedSkill = it; renderCharacter() },
                onUnlock = { node ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) { SS.unlock(ctx, node.id) }
                        if (ok) {
                            rootView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                            android.widget.Toast.makeText(ctx, "✨ Odemčeno: ${node.title}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        renderCharacter()
                    }
                },
                onGearTab = { gearTab = it; renderCharacter() },
                onSlot = { slot -> openSlot(slot) }
            )
        }
    }

    /** Výběr vybavení do slotu (dřevěné menu). */
    private fun openSlot(slot: cz.uhk.macroflow.pokemon.skills.GearSlot) {
        val ctx = context?.applicationContext ?: return
        val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
        if (slot.locked) {
            android.widget.Toast.makeText(ctx, "❔ Tenhle nástroj přijde do hry později.", android.widget.Toast.LENGTH_SHORT).show(); return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val (current, owned) = withContext(Dispatchers.IO) {
                SS.equipped(ctx, slot) to cz.uhk.macroflow.pokemon.skills.Gear.fitting(slot).filter { SS.count(ctx, it.id) > 0 }
            }
            if (!isAdded) return@launch
            val root = rootView as FrameLayout
            cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(root, slot.label, current?.let { "Nasazeno: ${it.label}" } ?: "Slot je prázdný.") { ui, body, close ->
                if (owned.isEmpty()) body.addView(ui.text("Zatím nemáš nic, co sem patří. Vybavení přinese výroba a úkoly.", 16f, ui.inkSoft))
                owned.forEach { g ->
                    val c = cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.card(ui)
                    c.addView(ui.icon(cz.uhk.macroflow.pokemon.skills.GearArt.gearIcon(g), 16, 16, 40f))
                    val col = ui.column().apply { setPadding(ui.px(10f), 0, ui.px(6f), 0) }
                    col.addView(ui.text(g.label, 19f)); col.addView(ui.text(g.description, 15f, ui.inkSoft))
                    c.addView(col, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    val on = current == g
                    c.addView(ui.button(if (on) "Sundat" else "Nasadit") {
                        close()
                        viewLifecycleOwner.lifecycleScope.launch {
                            withContext(Dispatchers.IO) { SS.equip(ctx, slot, if (on) null else g) }
                            renderCharacter()
                        }
                    })
                    body.addView(c)
                }
            }
        }
    }

    private fun renderResources() {
        val ctx = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val counts = withContext(Dispatchers.IO) { cz.uhk.macroflow.pokemon.skills.SkillStore.counts(ctx) }
            if (!isAdded) return@launch
            cz.uhk.macroflow.pokemon.skills.ui.JournalPages.resources(rootView.findViewById(R.id.llResources), counts, rootView as FrameLayout)
        }
    }

    private fun renderAwards() {
        val ctx = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val (facts, unlocked) = withContext(Dispatchers.IO) {
                val S = cz.uhk.macroflow.pokemon.skills.AwardStore
                val f = S.facts(ctx)
                S.check(ctx, f)
                f to S.unlockedDays(ctx)
            }
            if (!isAdded) return@launch
            cz.uhk.macroflow.pokemon.skills.ui.JournalPages.awards(rootView.findViewById(R.id.llAwards), facts, unlocked, rootView as FrameLayout)
        }
    }

    private fun renderDaily() {
        val ctx = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val (quests, facts, claimed) = withContext(Dispatchers.IO) {
                cz.uhk.macroflow.pokemon.daily.DailyQuestStore.prune(ctx)
                val q = cz.uhk.macroflow.pokemon.daily.DailyQuestStore.questsToday(ctx)
                Triple(q, cz.uhk.macroflow.pokemon.daily.DailyQuestStore.facts(ctx),
                    q.map { cz.uhk.macroflow.pokemon.daily.DailyQuestStore.isClaimed(ctx, it.index) })
            }
            if (!isAdded) return@launch
            buildDaily(quests, facts, claimed)
        }
    }

    private fun buildDaily(
        quests: List<cz.uhk.macroflow.pokemon.daily.DailyQuests.Quest>,
        facts: cz.uhk.macroflow.pokemon.daily.DailyQuests.Facts,
        claimed: List<Boolean>
    ) {
        val DQ = cz.uhk.macroflow.pokemon.daily.DailyQuests
        val box = rootView.findViewById<LinearLayout>(R.id.llDaily)
        box.removeAllViews()
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        val font = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.jersey_15)
        val ink = ctx.getColor(R.color.journal_ink)
        val deep = Color.parseColor("#9A5518")
        fun tv(text: String, size: Float, color: Int, gravity: Int = Gravity.START) = TextView(ctx).apply {
            this.text = text; textSize = size; setTextColor(color); typeface = font; this.gravity = gravity
        }

        box.addView(tv("Denní úkoly", 27f, ctx.getColor(R.color.journal_chapter_title_ink), Gravity.CENTER).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val now = java.time.LocalDateTime.now()
        val left = java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay())
        box.addView(tv("Nové úkoly za ${left.toHours()} h ${left.toMinutes() % 60} min · 3 úkoly denně",
            15f, ink, Gravity.CENTER).apply {
            alpha = 0.7f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = (10 * dp).toInt() }
        })

        quests.forEachIndexed { i, q ->
            val done = DQ.isDone(q, facts)
            val isClaimed = claimed.getOrElse(i) { false }
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 14 * dp
                    setColor(if (isClaimed) Color.parseColor("#1F606C38") else Color.parseColor("#2EBC6C25"))
                    setStroke((1.5f * dp).toInt(), if (done) Color.parseColor("#606C38") else Color.parseColor("#55BC6C25"))
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = (10 * dp).toInt() }
            }
            val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(tv(q.title, 21f, deep).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            head.addView(tv("+${q.reward} ${getString(R.string.coin_emoji)}", 21f, deep))
            card.addView(head)
            card.addView(tv(q.description, 17f, ink))
            // postup
            val bar = FrameLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (10 * dp).toInt())
                    .apply { topMargin = (8 * dp).toInt() }
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 5 * dp; setColor(Color.parseColor("#33283618")) }
            }
            val fill = View(ctx).apply {
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 5 * dp; setColor(if (done) Color.parseColor("#606C38") else Color.parseColor("#BC6C25")) }
            }
            bar.addView(fill, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))
            bar.post {
                val target = (bar.width * DQ.fraction(q, facts)).toInt()
                fill.layoutParams = fill.layoutParams.apply { width = target.coerceAtLeast(if (DQ.fraction(q, facts) > 0f) (10 * dp).toInt() else 0) }
                fill.scaleX = 0f; fill.pivotX = 0f
                fill.animate().scaleX(1f).setDuration(500).setStartDelay(i * 120L).start()
            }
            card.addView(bar)
            val foot = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = (6 * dp).toInt() }
            }
            foot.addView(tv(DQ.progressText(q, facts), 16f, ink).apply {
                alpha = 0.8f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            when {
                isClaimed -> foot.addView(tv("Vyzvednuto ✓", 17f, Color.parseColor("#606C38")))
                done -> foot.addView(tv("Vyzvednout", 18f, Color.parseColor("#FEFAE0"), Gravity.CENTER).apply {
                    setPadding((14 * dp).toInt(), (4 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
                    background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 12 * dp; setColor(Color.parseColor("#606C38")) }
                    setOnClickListener { claimDaily(q) }
                })
                else -> {}
            }
            card.addView(foot)
            box.addView(card)
        }
    }

    private fun claimDaily(q: cz.uhk.macroflow.pokemon.daily.DailyQuests.Quest) {
        val ctx = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val got = withContext(Dispatchers.IO) { cz.uhk.macroflow.pokemon.daily.DailyQuestStore.claim(ctx, q) }
            if (got > 0) {
                rootView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                android.widget.Toast.makeText(ctx, "+$got makro penízků", android.widget.Toast.LENGTH_SHORT).show()
                // zůstatek i do cloudu
                if (cz.uhk.macroflow.data.FirebaseRepository.isLoggedIn) withContext(Dispatchers.IO) {
                    runCatching {
                        cz.uhk.macroflow.data.AppDatabase.getDatabase(ctx).coinDao().getBalance()
                            ?.let { cz.uhk.macroflow.data.FirebaseRepository.uploadCoins(it) }
                    }
                }
            }
            renderDaily()
        }
    }

    private fun flipPage(direction: Int) {
        if (tab != Tab.STORY) return
        if (unlockedQuests.isEmpty()) return
        val newIndex = currentPageIndex + direction
        if (newIndex !in unlockedQuests.indices) return
        // Krátké „otočení listu“: stránky uhnou do strany, vymění se a vrátí se
        val pages = listOf(R.id.leftPage, R.id.rightPage).map { rootView.findViewById<View>(it) }
        val shift = 24 * resources.displayMetrics.density * -direction
        pages.forEach { it.animate().alpha(0f).translationX(shift).setDuration(110).start() }
        pages.first().postDelayed({
            if (!isAdded) return@postDelayed
            currentPageIndex = newIndex
            selectedStageIndex = null
            renderCurrentPage()
            pages.forEach { it.translationX = -shift; it.animate().alpha(1f).translationX(0f).setDuration(160).start() }
        }, 115)
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
            rootView.findViewById<TextView>(R.id.chapterTitle).text = "Prázdný deník"
            rootView.findViewById<TextView>(R.id.storyText).text = "Zatím jsi nezačal žádné dobrodružství."
            return
        }

        val progress = unlockedQuests.getOrNull(currentPageIndex) ?: return
        val quest = QuestRegistry.byId(progress.questId) ?: QuestRegistry.TOWN_INTRO_QUEST

        val currentIndex = progress.currentStageIndex
        val isAllDone = progress.isCompleted
        val viewingIndex = selectedStageIndex ?: if (isAllDone) quest.stages.size - 1 else currentIndex

        // STRÁNKOVÁNÍ
        rootView.findViewById<TextView>(R.id.chapterLabel).text = chapterName(quest.id)
        rootView.findViewById<TextView>(R.id.dateText).text = "Strana ${currentPageIndex + 1} / ${unlockedQuests.size}"
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
                    val allMacros = stageToDisplay.targetId == QuestProgression.ALL_MACROS
                    if (allMacros && !(viewingIndex < currentIndex || isAllDone))
                        "Cíl: B/S/T dnes (${QuestProgression.currentValue(stageToDisplay, progress.metadata)} / 3 trefeno)"
                    else if (n == null || viewingIndex < currentIndex || isAllDone) "Cíl: Splněno"
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
            rootView.findViewById<TextView>(R.id.taskListText).text = brief
        }

        renderStagesList(quest, currentIndex, isAllDone)
        drawTracker(rootView.findViewById(R.id.journalQuestProgressLine), quest.stages.size, if (isAllDone) quest.stages.size else currentIndex)
    }

    private fun chapterName(questId: String): String = when (questId) {
        QuestRegistry.TOWN_INTRO_QUEST.id -> "I · MĚSTO"
        QuestRegistry.MEADOW_QUEST.id -> "II · LOUKA"
        QuestRegistry.MOUNTAINS_QUEST.id -> "III · HORY"
        else -> "DOBRODRUŽSTVÍ"
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
                        // splněné zeleně, aktuální a vybraná oranžově
                        ds.color = when {
                            selectedStageIndex == index || (!allDone && index == currentIdx) -> Color.parseColor("#BC6C25")
                            else -> Color.parseColor("#606C38")
                        }
                        ds.isUnderlineText = selectedStageIndex == index
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                val st = builder.length
                builder.append("[ ] ???\n")
                builder.setSpan(android.text.style.ForegroundColorSpan(Color.parseColor("#80283618")), st, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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