package cz.uhk.macroflow.pokemon

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Bundle
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
        rootView.findViewById<View>(R.id.tabZone).setOnClickListener { showTab(Tab.ZONE) }
        rootView.findViewById<View>(R.id.tabPocket).setOnClickListener { showTab(Tab.POCKET) }
        rootView.findViewById<View>(R.id.tabBag).setOnClickListener { showTab(Tab.BAG) }
        rootView.findViewById<View>(R.id.tabDex).setOnClickListener { showTab(Tab.DEX) }
        rootView.findViewById<View>(R.id.tabFiles).apply {
            setOnClickListener { showTab(Tab.FILES) }
            // Spisy jsou vidět až s prvním roztrženým listem nebo snem (docs/adr/0057)
            val flags = cz.uhk.macroflow.pokemon.story.StoryFlags.all(requireContext())
            visibility = if (cz.uhk.macroflow.pokemon.story.Insight.foundPages(flags).isNotEmpty() ||
                cz.uhk.macroflow.pokemon.story.Dreams.dreamed(flags).isNotEmpty()) View.VISIBLE else View.GONE
        }
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
            Tab.ZONE -> renderZone()
            Tab.FILES -> renderFiles()
            Tab.POCKET -> renderPocket()
            Tab.BAG -> renderBag()
            Tab.DEX -> renderDex()
            Tab.STORY -> {}
        }
    }

    // ── Denní úkoly ─────────────────────────────────────────────────────────

    private enum class Tab { CHARACTER, STORY, DAILY, RESOURCES, AWARDS, ZONE, FILES, POCKET, BAG, DEX }
    private var tab = Tab.CHARACTER
    /** Nastaví se před zobrazením, když má deník otevřít rovnou denní úkoly. */
    var showDailyFirst = false

    private fun showTab(t: Tab) {
        tab = t
        val story = t == Tab.STORY
        val storyViews = listOf(R.id.leftPage, R.id.rightPage, R.id.bindingShadowContainer, R.id.btnPrevPage, R.id.btnNextPage)
        storyViews.forEach { rootView.findViewById<View>(it)?.visibility = if (story) View.VISIBLE else View.GONE }
        if (!story) applyPageStyle(false) else if (unlockedQuests.isNotEmpty()) renderCurrentPage()
        rootView.findViewById<View>(R.id.dailyPage).visibility = if (t == Tab.DAILY) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.characterPage).visibility = if (t == Tab.CHARACTER) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.resourcesPage).visibility = if (t == Tab.RESOURCES) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.awardsPage).visibility = if (t == Tab.AWARDS) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.zonePage).visibility = if (t == Tab.ZONE) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.filesPage).visibility = if (t == Tab.FILES) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.pocketPage).visibility = if (t == Tab.POCKET) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.bagPage).visibility = if (t == Tab.BAG) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.dexPage).visibility = if (t == Tab.DEX) View.VISIBLE else View.GONE
        mapOf(Tab.CHARACTER to R.id.tabCharacter, Tab.STORY to R.id.tabStory, Tab.DAILY to R.id.tabDaily, Tab.RESOURCES to R.id.tabResources,
            Tab.AWARDS to R.id.tabAwards, Tab.ZONE to R.id.tabZone, Tab.FILES to R.id.tabFiles,
            Tab.POCKET to R.id.tabPocket, Tab.BAG to R.id.tabBag, Tab.DEX to R.id.tabDex)
            .forEach { (k, id) -> rootView.findViewById<View>(id).alpha = if (k == t) 1f else 0.55f }
        when (t) {
            Tab.DAILY -> renderDaily()
            Tab.CHARACTER -> renderCharacter()
            Tab.RESOURCES -> renderResources()
            Tab.AWARDS -> renderAwards()
            Tab.ZONE -> renderZone()
            Tab.FILES -> renderFiles()
            Tab.POCKET -> renderPocket()
            Tab.BAG -> renderBag()
            Tab.DEX -> renderDex()
            Tab.STORY -> {}
        }
    }

    // ── Kapsa, Batoh, Makrodex (docs/adr/0060) ───────────────────────────────

    private fun toast(msg: String) = android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show()

    private fun renderPocket() {
        val ctx = context?.applicationContext ?: return
        val PA = cz.uhk.macroflow.pokemon.bag.PocketActions
        val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
        viewLifecycleOwner.lifecycleScope.launch {
            data class P(val mons: List<CapturedMakromonEntity>, val team: List<Int>, val slots: Int, val active: Long)
            val d = withContext(Dispatchers.IO) {
                P(AppDatabase.getDatabase(ctx).capturedMakromonDao().getAllCaught(), SS.team(ctx), SS.state(ctx).teamSlots, PA.activeCaughtDate(ctx))
            }
            if (!isAdded) return@launch
            // akce běží mimo hlavní vlákno, pak se stránka překreslí
            fun act(block: () -> String?) = viewLifecycleOwner.lifecycleScope.launch {
                val msg = withContext(Dispatchers.IO) { block() }
                if (!isAdded) return@launch
                msg?.let { toast(it) }
                renderPocket()
            }
            cz.uhk.macroflow.pokemon.skills.ui.CollectionPages.pocket(rootView.findViewById(R.id.llPocket), rootView as FrameLayout,
                d.mons, d.team, d.slots, d.active, object : cz.uhk.macroflow.pokemon.skills.ui.CollectionPages.PocketCallbacks {
                    override fun toggleTeam(m: CapturedMakromonEntity) = act {
                        when (val r = PA.toggleTeam(ctx, m, d.mons)) {
                            is cz.uhk.macroflow.pokemon.bag.PocketActions.TeamResult.Added -> "${m.name} je v týmu (${r.size}/${r.slots})."
                            cz.uhk.macroflow.pokemon.bag.PocketActions.TeamResult.Removed -> "${m.name} odešel z týmu."
                            is cz.uhk.macroflow.pokemon.bag.PocketActions.TeamResult.Full ->
                                "Tým je plný (${r.slots}/${r.slots}). Další místo odemkneš ve stromu Chytání."
                        }
                    }.let { }
                    override fun makeActive(m: CapturedMakromonEntity) = act { PA.makeActive(ctx, m); "★ ${m.name} je parťák na liště." }.let { }
                    override fun toggleLock(m: CapturedMakromonEntity) = act { PA.toggleLock(ctx, m); if (m.isLocked) "🔒 ${m.name} zamčen." else "${m.name} odemčen." }.let { }
                    override fun release(m: CapturedMakromonEntity) = act { if (PA.release(ctx, m)) "${m.name} se vrátil do divočiny." else "Zamčeného Makromona pustit nejde." }.let { }
                })
        }
    }

    private fun renderBag() {
        val ctx = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val counts = withContext(Dispatchers.IO) { cz.uhk.macroflow.pokemon.skills.SkillStore.counts(ctx) }
            if (!isAdded) return@launch
            cz.uhk.macroflow.pokemon.skills.ui.CollectionPages.bag(rootView.findViewById(R.id.llBag), rootView as FrameLayout, counts,
                readText = { item -> bagReadText(item.id) },
                onUse = { item ->
                    if (item.id == cz.uhk.macroflow.pokemon.bag.BagItems.LURE_LAMP) viewLifecycleOwner.lifecycleScope.launch {
                        val ok = withContext(Dispatchers.IO) { cz.uhk.macroflow.pokemon.bag.PocketActions.useLureLamp(ctx) }
                        if (!isAdded) return@launch
                        toast(if (ok) "👻 Spooky Plate aktivován!" else "Spooky Plate už je aktivní.")
                        renderBag()
                    }
                })
        }
    }

    /** Text čitelných předmětů (stejný jako v inventáři). */
    private fun bagReadText(id: String): String {
        return when (id) {
            cz.uhk.macroflow.pokemon.story.SecretGrove.DIARY_ID ->
                cz.uhk.macroflow.pokemon.story.SecretGrove.DIARY_PAGES.joinToString("\n\n") { (title, body) -> "$title\n$body" }
            cz.uhk.macroflow.pokemon.story.Insight.PAGES_ITEM -> {
                val flags = cz.uhk.macroflow.pokemon.story.StoryFlags.all(requireContext())
                val insight = cz.uhk.macroflow.pokemon.story.Insight.level(flags)
                cz.uhk.macroflow.pokemon.story.Insight.foundPages(flags)
                    .joinToString("\n\n────────\n\n") { cz.uhk.macroflow.pokemon.story.Insight.render(it.text, insight) }
                    .ifEmpty { "Listy jsou prázdné." }
            }
            else -> ""
        }
    }

    private var dexMode = cz.uhk.macroflow.pokemon.skills.ui.CollectionPages.DexMode.DEX

    private fun renderDex() {
        val ctx = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val data = withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(ctx)
                val caught = db.capturedMakromonDao().getAllCaught()
                val defined = cz.uhk.macroflow.pokemon.dex.DexText.dexIds(SpawnManager.allEntries.map { it.id })
                val guardians = cz.uhk.macroflow.pokemon.dex.DexText.defeatedGuardians(cz.uhk.macroflow.pokemon.story.StoryFlags.all(ctx))
                val entries = db.makrodexEntryDao().getAllEntries().filter { it.makrodexId in defined }.sortedBy { it.makrodexId }
                val inv = caught.map { it.makromonId }.toSet()
                val shinyCaught = caught.filter { it.isShiny }.map { it.makromonId }.toSet()
                val seen = ctx.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
                    .getStringSet(cz.uhk.macroflow.pokemon.shiny.ShinyDex.SEEN_KEY, emptySet()).orEmpty() + shinyCaught
                cz.uhk.macroflow.pokemon.skills.ui.CollectionPages.DexData(entries,
                    (db.makrodexStatusDao().getUnlockedIds() + inv + guardians).toSet(),
                    caught.groupBy { it.makromonId }.mapValues { it.value.size }, seen, shinyCaught)
            }
            if (!isAdded) return@launch
            cz.uhk.macroflow.pokemon.skills.ui.CollectionPages.dex(rootView.findViewById(R.id.llDex), rootView as FrameLayout, data, dexMode,
                onMode = { dexMode = it; renderDex() },
                cb = object : cz.uhk.macroflow.pokemon.skills.ui.CollectionPages.DexCallbacks {
                    override fun dossier(e: MakrodexEntryEntity, caught: Int): String? {
                        val flags = cz.uhk.macroflow.pokemon.story.StoryFlags.all(requireContext())
                        val insight = cz.uhk.macroflow.pokemon.story.Insight.level(flags)
                        if (!cz.uhk.macroflow.pokemon.story.Dossiers.visible(insight)) return null
                        return cz.uhk.macroflow.pokemon.story.Insight.render(
                            cz.uhk.macroflow.pokemon.story.Dossiers.text(e.makrodexId, e.displayName, caught), insight)
                    }
                    override fun spirraPaths(show: (String) -> Unit) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            val text = withContext(Dispatchers.IO) {
                                val sp = cz.uhk.macroflow.pokemon.evolution.SpirraBond.activeSpirra(ctx)
                                cz.uhk.macroflow.pokemon.dex.DexText.spirraPaths(sp, sp?.let { cz.uhk.macroflow.pokemon.evolution.SpirraBond.days(ctx, it.id) })
                            }
                            if (isAdded) show(text)
                        }
                    }
                    override fun evoTest(e: MakrodexEntryEntity): (() -> Unit)? {
                        if (!cz.uhk.macroflow.BuildConfig.DEBUG) return null
                        val profile = MakromonGrowthManager.getProfile(e.makrodexId) ?: return null
                        if (profile.evolutionToId.isEmpty() || e.makrodexId == cz.uhk.macroflow.pokemon.evolution.SpirraEvolution.SPIRRA_ID) return null
                        return {
                            viewLifecycleOwner.lifecycleScope.launch {
                                val last = withContext(Dispatchers.IO) {
                                    AppDatabase.getDatabase(ctx).capturedMakromonDao().getAllCaught().filter { it.makromonId == e.makrodexId }.maxByOrNull { it.level }
                                } ?: return@launch
                                if (!isAdded) return@launch
                                val move = MakromonGrowthManager.getNewMoveForLevel(profile.evolutionToId, profile.evolutionLevel)
                                    ?: MakromonGrowthManager.getNewMoveForLevel(profile.evolutionToId, 1)
                                EvolutionDialog(requireContext(), last.id, e.makrodexId, profile.evolutionToId, move) { renderDex() }.show()
                            }
                        }
                    }
                })
        }
    }

    // ── Spisy: roztržené listy a hlášení o snech (docs/adr/0057) ──────────────

    private fun renderFiles() {
        // vzhled archivu: složky, razítka, začerněná místa (docs/adr/0062)
        cz.uhk.macroflow.pokemon.skills.ui.ArchivePages.files(rootView.findViewById(R.id.llFiles),
            cz.uhk.macroflow.pokemon.story.StoryFlags.all(requireContext()))
    }

    // ── Zóna 1: mapa lokací a teleport (docs/adr/0052) ───────────────────────

    private fun renderZone() {
        val act = activity as? MakromonMapActivity ?: return
        val page = rootView.findViewById<android.widget.FrameLayout>(R.id.zonePage)
        val map = page.findViewWithTag<cz.uhk.macroflow.pokemon.zone.ZoneMapView>("zone_map") ?: buildMapPage(page)
        val z = act.zoneState()
        map.current = z.current
        map.heroFrac = z.heroFrac
        map.heroHead = z.heroHead
        map.seen = z.seen
        map.onPick = { act.onZonePick(it) }
    }

    /**
     * Záložka Mapa: mapa vybrané zóny přes celou stránku, vlevo nahoře přes ni svislý sloupec
     * záložek zón (zatím jen Zóna 1).
     */
    private fun buildMapPage(page: android.widget.FrameLayout): cz.uhk.macroflow.pokemon.zone.ZoneMapView {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density
        val font = androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.jersey_15)
        val map = cz.uhk.macroflow.pokemon.zone.ZoneMapView(ctx).apply { tag = "zone_map" }
        page.addView(map, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val tabs = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; elevation = 2 * dp }
        cz.uhk.macroflow.pokemon.zone.ZoneOne.ZONES.forEach { (title, selected) ->
            tabs.addView(zoneTab(title, selected, font, dp), LinearLayout.LayoutParams(
                (72 * dp).toInt(), (32 * dp).toInt()).apply { bottomMargin = (5 * dp).toInt() })
        }
        page.addView(tabs, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START).apply { topMargin = (4 * dp).toInt(); marginStart = (4 * dp).toInt() })
        page.addView(TextView(ctx).apply {
            text = "Klepni na objevené místo a přenes se tam."
            textSize = 13f
            setTextColor(0xFFF6E8C4.toInt())
            setShadowLayer(2 * dp, 0f, 0f, 0xFF10140C.toInt())
            gravity = Gravity.CENTER
            typeface = font
        }, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = (4 * dp).toInt() })
        return map
    }

    /** Záložka zóny jako ve starých RPG: rámeček s obrysem, vybraná je zelená, písmo s tmavým obrysem. */
    private fun zoneTab(title: String, selected: Boolean, font: android.graphics.Typeface?, dp: Float) = TextView(requireContext()).apply {
        text = title.uppercase()
        typeface = font
        textSize = 17f
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(if (selected) 0xFFF4FFE8.toInt() else 0xFF9FD3FF.toInt())
        setShadowLayer(2.5f * dp, 0f, 0f, 0xFF10140C.toInt())
        background = android.graphics.drawable.LayerDrawable(arrayOf(
            android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 3 * dp
                setColor(if (selected) 0xFF5FB54A.toInt() else 0xFF2C3440.toInt())
                setStroke((2 * dp).toInt(), 0xFF1B1A14.toInt())
            },
            android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 2 * dp
                setColor(0)
                setStroke((1.5f * dp).toInt(), if (selected) 0xFFB9F09A.toInt() else 0xFF55657A.toInt())
            }
        )).apply { setLayerInset(1, (2.5f * dp).toInt(), (2.5f * dp).toInt(), (2.5f * dp).toInt(), (2.5f * dp).toInt()) }
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
                onTree = { openTree() },
                onGearTab = { gearTab = it; renderCharacter() },
                onSlot = { slot -> openSlot(slot) }
            )
        }
    }

    /** Grafický strom dovedností přes celý deník (docs/adr/0058). */
    private fun openTree() {
        val ctx = context?.applicationContext ?: return
        val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
        viewLifecycleOwner.lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { SS.state(ctx) }
            if (!isAdded) return@launch
            cz.uhk.macroflow.pokemon.skills.ui.SkillTreeOverlay.show(rootView as FrameLayout, selectedSkill, state,
                unlock = { node, done ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val (ok, fresh) = withContext(Dispatchers.IO) { SS.unlock(ctx, node.id).let { it to SS.state(ctx) } }
                        if (!isAdded) return@launch
                        if (ok) rootView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        done(if (ok) fresh else null)
                        renderCharacter()
                    }
                },
                reset = { skill, done ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val (r, fresh) = withContext(Dispatchers.IO) { SS.reset(ctx, skill).let { it to SS.state(ctx) } }
                        if (!isAdded) return@launch
                        when (r) {
                            is cz.uhk.macroflow.pokemon.skills.SkillStore.ResetResult.Done -> {
                                rootView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                done(fresh, "🔄 Strom ${skill.label} přeučen – body jsou zpět.")
                            }
                            is cz.uhk.macroflow.pokemon.skills.SkillStore.ResetResult.NoCoins ->
                                done(null, "Na přeučení potřebuješ ${cz.uhk.macroflow.pokemon.skills.SkillTree.RESET_COINS} 🪙 (máš ${r.have}).")
                            else -> done(null, "Není co přeučit.")
                        }
                        renderCharacter()
                    }
                },
                onSkill = { selectedSkill = it; renderCharacter() })
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
                    col.addView(ui.text(if (g.legendary) "✦ ${g.label}" else g.label, 19f, if (g.legendary) 0xFFB8860B.toInt() else ui.ink))
                    col.addView(ui.text(g.description, 15f, ui.inkSoft))
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
        // vzhled stránky (cedule, karty, razítka) je v DailyPage – docs/adr/0058
        cz.uhk.macroflow.pokemon.daily.DailyPage.render(rootView.findViewById(R.id.llDaily), quests, facts, claimed) { claimDaily(it) }
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
        val appCtx = requireContext().applicationContext
        val mapActivity = activity as? MakromonMapActivity
        val currentBiome = mapActivity?.getCurrentBiome() ?: BiomeType.TOWN
        // V biomu bez vlastního questu (hory) otevřeme stránku právě aktivního questu
        val targetQuestId = BiomeRegistry.definition(currentBiome)?.questId
            ?: mapActivity?.questManager?.getActiveQuestId()
            ?: QuestRegistry.TOWN_INTRO_QUEST.id

        lifecycleScope.launch(Dispatchers.IO) {
            // Kapitoly v pořadí příběhu (dřív podle poslední změny – stránky přeskakovaly), pak vedlejší, pak tajné
            val allProgress = db.questDao().getAllQuests()
                .filter { QuestRegistry.byId(it.questId) != null }
                .sortedBy { QuestRegistry.journalOrder(it.questId) }
            // zkouška havíře Vendelína: odpracované minuty zvlášť pro každou dovednost (docs/adr/0050)
            afkBySkill = allProgress.firstOrNull { p ->
                !p.isCompleted && QuestRegistry.byId(p.questId)?.stages?.getOrNull(p.currentStageIndex)?.requirementType == RequirementType.AFK_MINUTES
            }?.let { p ->
                val stage = QuestRegistry.byId(p.questId)!!.stages[p.currentStageIndex]
                p.questId to cz.uhk.macroflow.pokemon.skills.SkillStore.workedMinutesSince(appCtx,
                    QuestProgression.afkSkills(stage), p.stageStartedAt, System.currentTimeMillis() / 1000)
            }

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

    /** Odpracované minuty zkoušky (quest → Skill.id → minuty). */
    private var afkBySkill: Pair<String, Map<String, Int>>? = null

    private var goldLeaf: GoldLeafDrawable? = null

    /** Tajná linka má zlatolesklou stránku (docs/adr/0050), ostatní obyčejný papír. */
    private fun applyPageStyle(secret: Boolean) {
        val paper = rootView.findViewById<View>(R.id.journalPaperBody)
        if (secret) {
            paper.background?.mutate()?.colorFilter =
                android.graphics.PorterDuffColorFilter(0xFFF8DC8A.toInt(), android.graphics.PorterDuff.Mode.MULTIPLY)
            val g = goldLeaf ?: GoldLeafDrawable(resources.displayMetrics.density).also { goldLeaf = it }
            paper.foreground = g
            g.start()
            rootView.findViewById<TextView>(R.id.chapterLabel).setTextColor(Color.parseColor("#9A6A10"))
        } else {
            paper.background?.mutate()?.colorFilter = null
            goldLeaf?.stop()
            paper.foreground = null
            rootView.findViewById<TextView>(R.id.chapterLabel).setTextColor(requireContext().getColor(R.color.journal_chapter_title_ink))
        }
    }

    override fun onDestroyView() {
        goldLeaf?.stop()
        super.onDestroyView()
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
        rootView.findViewById<TextView>(R.id.chapterLabel).text = quest.chapter
        applyPageStyle(quest.secret && tab == Tab.STORY)
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
                    "Cíl: Výhry ${QuestProgression.biomeLabel(stageToDisplay)} ($value / ${stageToDisplay.targetValue})"
                }
                RequirementType.HIT_WATER -> {
                    if (viewingIndex < currentIndex || isAllDone) "Cíl: Splněno"
                    else "Cíl: Voda dnes ${QuestProgression.currentValue(stageToDisplay, progress.metadata)} % tvého cíle (potřeba 100 %)"
                }
                RequirementType.DELIVER_ITEMS -> {
                    if (viewingIndex < currentIndex || isAllDone) "Cíl: Odevzdáno"
                    else "Cíl: Přines ${QuestProgression.deliveryText(stageToDisplay, null)}"
                }
                RequirementType.STORY_FLAG -> {
                    if (viewingIndex < currentIndex || isAllDone) "Cíl: Splněno"
                    else "Cíl: " + (stageToDisplay.hint ?: "Dokonči, co po tobě chtějí")
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
                RequirementType.HAVE_ITEM -> if (viewingIndex < currentIndex || isAllDone) "Cíl: Splněno"
                    else "Cíl: Mít ${cz.uhk.macroflow.pokemon.skills.Gear.from(stageToDisplay.targetId)?.label ?: stageToDisplay.targetId}"
                RequirementType.AFK_MINUTES -> {
                    if (viewingIndex < currentIndex || isAllDone) "Cíl: Splněno"
                    else {
                        val bySkill = afkBySkill?.takeIf { it.first == quest.id }?.second
                        val need = stageToDisplay.targetValue
                        "Cíl: " + QuestProgression.afkSkills(stageToDisplay).joinToString(" · ") { id ->
                            val label = cz.uhk.macroflow.pokemon.skills.Skill.from(id)?.label ?: id
                            val m = (bySkill?.get(id) ?: 0).coerceAtMost(need)
                            "$label ${QuestProgression.clock(m)} / ${QuestProgression.clock(need)}" + if (m >= need) " ✓" else ""
                        }
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
            // dřevěný vzhled stránky (docs/adr/0060)
            cz.uhk.macroflow.pokemon.skills.ui.StoryPage.style(
                rootView.findViewById(R.id.npcPortrait), rootView.findViewById(R.id.taskListText), rootView.findViewById(R.id.storyScroll),
                rootView.findViewById(R.id.chapterLabel), rootView.findViewById(R.id.btnPrevPage), rootView.findViewById(R.id.btnNextPage),
                objectiveDone = viewingIndex < currentIndex || isAllDone, secret = quest.secret)
        }

        renderStagesList(quest, currentIndex, isAllDone)
        drawTracker(rootView.findViewById(R.id.journalQuestProgressLine), quest.stages.size, if (isAllDone) quest.stages.size else currentIndex)
    }

    private fun renderStagesList(quest: QuestDefinition, currentIdx: Int, allDone: Boolean) {
        cz.uhk.macroflow.pokemon.skills.ui.StoryPage.stages(rootView.findViewById(R.id.llQuestStages),
            quest.stages.map { it.title }, currentIdx, allDone, selectedStageIndex) { i ->
            selectedStageIndex = i
            renderCurrentPage()
        }
    }

    private fun drawTracker(container: LinearLayout, total: Int, activeIndex: Int) =
        cz.uhk.macroflow.pokemon.skills.ui.StoryPage.tracker(container, total, activeIndex)
}
