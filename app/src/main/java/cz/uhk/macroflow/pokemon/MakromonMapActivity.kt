package cz.uhk.macroflow.pokemon

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cz.uhk.macroflow.BuildConfig
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import cz.uhk.macroflow.R
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.nutrition.BarcodeProductLookup
import cz.uhk.macroflow.pokemon.cave.CaveMap
import cz.uhk.macroflow.pokemon.cave.CaveTransitionView
import cz.uhk.macroflow.pokemon.cave.CrystalColor
import cz.uhk.macroflow.pokemon.cave.Crystals
import cz.uhk.macroflow.pokemon.cave.MapCamera
import cz.uhk.macroflow.pokemon.legend.LegendProgress
import cz.uhk.macroflow.pokemon.legend.PeakShrine
import cz.uhk.macroflow.pokemon.legend.SpecialBattle
import cz.uhk.macroflow.pokemon.ui.StepProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import cz.uhk.macroflow.pokemon.cave.SkyPass
import cz.uhk.macroflow.pokemon.cave.MinesMap
import cz.uhk.macroflow.pokemon.story.StoryFlags
import cz.uhk.macroflow.pokemon.story.ForestHeart
import cz.uhk.macroflow.pokemon.story.SecretGrove
import cz.uhk.macroflow.pokemon.story.Insight
import cz.uhk.macroflow.pokemon.story.Vendelin
import java.util.Locale
import kotlin.math.sqrt

class MakromonMapActivity : AppCompatActivity() {

    private lateinit var mapBackground:    ImageView
    /** Pozadí + postava + NPC; v jeskyních větší než obrazovka a posouvaný kamerou. */
    private lateinit var mapWorld:         FrameLayout
    private lateinit var ashView:          ImageView
    private var crystalView: ImageView? = null
    /** Zvětšení art pixelu v jeskyni (celé číslo z MapCamera.pixelScale); 0 mimo jeskyně. */
    private var worldScale = 0
    /** Krystaly, strážci, záře a svatyně – vše, co se překresluje podle stavu příběhu. */
    private val decorViews = mutableListOf<View>()
    private var crystalGlow: View? = null
    private val crystalAnimators = mutableListOf<android.animation.Animator>()
    /** Během přechodu do/z jeskyně se na mapu neklepe. */
    private var transitionRunning = false
    private lateinit var movementEngine:   MovementEngine
    private lateinit var questDialogManager: QuestDialogManager
    private lateinit var companionManager: CompanionManager
    private lateinit var stepProgressBar:  StepProgressBar
    lateinit var questManager:             QuestManager
    private lateinit var gudwinNPC:        ImageView
    private lateinit var starterBush:      ImageView
    private lateinit var meadowBushNPC:    ImageView

    private var lastClickTime = 0L
    private var lastClickedNode = ""
    private var currentBiome = BiomeType.TOWN
    private var currentDailySteps = 0

    private val clickableNodes = listOf(
        "les", "domov", "pokedex", "obchod", "hory",
        "vstup_z_town", "krovi1", "krovi2", "voda", "gudwin", "starter_bush",
        "meadow_npc", "les_sever",
        "vstup_z_meadow", "rozcesti_hory", "kral_mlsak", "camp", "mine", "cave", "peak", "skaly1", "skaly2",
        // Dílna na louce (docs/adr/0034)
        "vyrobna", "zahon_1", "zahon_2", "zahon_3", "zahon_4",
        // Těžba a kácení (docs/adr/0035)
        "strom_dub", "zila_med"
    )

    /** Uzly, kde může vyskočit divoký Makromon (90 % šance). Jeskyně mají vlastní (CaveMap.encounterNodes). */
    private val encounterNodes = setOf("krovi1", "krovi2", "voda", "skaly1", "skaly2") +
        cz.uhk.macroflow.pokemon.cave.CaveMaps.ALL.flatMap { it.encounterNodes } +
        cz.uhk.macroflow.pokemon.cave.ForestMap.MAP.encounterNodes +
        cz.uhk.macroflow.pokemon.cave.MinesMap.MAP.encounterNodes

    /** Přechod mezi mapami. */
    private enum class MapTransition { NONE, FADE, CAVE_IN, CAVE_OUT,
        /** Sestup ze Starého dolu do Dolů (docs/adr/0049). */
        MINE_DESCENT }

    companion object {
        private const val DOUBLE_CLICK_TIME = 300L
        /** Lokace s vlastní scénou přechodu (docs/adr/0042). */
        private val LOCATION_SCENES = setOf("TOWN", "MEADOW", "FOREST", "MOUNTAINS", "SKY_PASS", "HIDDEN_GROVE")
        /** Dosah klepnutí na uzel v podílu obrazovky. */
        private const val TAP_RADIUS = 0.1f
        /** V jeskyních a lese jsou body husté a mezi nimi se chodí volně – menší dosah. */
        private const val CAVE_TAP_RADIUS = 0.06f
        /** Uzly jeskyní a lesa, které něco dělají (ostatní jsou jen cesta). */
        private val CAVE_ACTION_NODES = setOf(
            "vychod_jeskyne", "vychod_dolu", "vstup_z_louky", "tezba", "les_sever", "mytina",
            "krystal_modry", "krystal_cerveny",
            // těžba a kácení v jeskyních a Hvozdu (docs/adr/0035)
            "zila_stribro", "zila_zlato", "strom_briza", "strom_javor"
        ) + cz.uhk.macroflow.pokemon.cave.SkyPass.ACTION_NODES +
            cz.uhk.macroflow.pokemon.cave.GroveMap.ACTION_NODES + "skryta_stezka" +
            cz.uhk.macroflow.pokemon.cave.MinesMap.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.MinesMap.MAZE_NODE
        private const val TAG_JOURNAL = "QUEST_JOURNAL"
        private const val DEBUG_BOOTS_KEY = "DEBUG_SEVEN_LEAGUE_BOOTS"
        private const val TAG_GROVE_GHOST = "grove_ghost"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pokemon_map)

        mapBackground = findViewById(R.id.mapBackground)
        mapWorld = findViewById(R.id.mapWorld)
        stepProgressBar = findViewById(R.id.stepProgressBar)

        ashView = findViewById<ImageView>(R.id.ashView).also {
            it.layoutParams.width  = (28 * resources.displayMetrics.density).toInt()
            it.layoutParams.height = (42 * resources.displayMetrics.density).toInt()
            it.requestLayout()
        }

        val imageLoader = ImageLoader.Builder(this).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }.build()

        // Stará sekera a Starý krumpáč jsou odměnou za první fáze questů u Křoví a krále Mlsáka
        // (docs/adr/0043) – kdo je dostal dřív automaticky, o nic nepřijde.

        // Debug: suroviny a XP pro vyzkoušení dílny (adb … --ez seed_skills true)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("seed_skills", false)) {
            val ctx = applicationContext
            lifecycleScope.launch(Dispatchers.IO) {
                val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
                SS.add(ctx, "energy_fragment", 6)
                cz.uhk.macroflow.pokemon.skills.Berry.entries.forEach { SS.add(ctx, it.seedItemId, 2); SS.add(ctx, it.berryItemId, 2) }
                SS.addXp(ctx, cz.uhk.macroflow.pokemon.skills.Skill.CATCHING, 80)
                SS.addXp(ctx, cz.uhk.macroflow.pokemon.skills.Skill.HARVESTING, 80)
            }
        }

        // Debug: suroviny na dobrodruhův set + odemčené recepty (adb … --ez seed_gear true)
        if (BuildConfig.DEBUG && intent.getBooleanExtra("seed_gear", false)) {
            val ctx = applicationContext
            lifecycleScope.launch(Dispatchers.IO) {
                val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
                (cz.uhk.macroflow.pokemon.skills.GearCrafting.SET + cz.uhk.macroflow.pokemon.skills.GearCrafting.ACCESSORIES + cz.uhk.macroflow.pokemon.skills.GearCrafting.TOOLS).forEach { g ->
                    cz.uhk.macroflow.pokemon.skills.GearCrafting.recipe(g)?.forEach { (id, n) -> SS.add(ctx, id, n) }
                }
                cz.uhk.macroflow.pokemon.skills.SkillTree.node("basic_gear")?.let { SS.add(ctx, it.itemId, 1) }
                // artefakty z Gudwina + materiály z Makromonů na ukázku
                listOf(cz.uhk.macroflow.pokemon.skills.Gear.MAKRO_AXE, cz.uhk.macroflow.pokemon.skills.Gear.MAKRO_PICKAXE)
                    .forEach { if (SS.count(ctx, it.id) == 0) SS.add(ctx, it.id, 1) }
                cz.uhk.macroflow.pokemon.skills.Resource.entries.filter { it.isMonsterMaterial }.forEach { SS.add(ctx, it.itemId, 3) }
            }
        }

        // Debug: přidá jeden předmět podle ID (adb … --es give_item tool_axe_makro)
        if (BuildConfig.DEBUG) intent.getStringExtra("give_item")?.let { id ->
            val ctx = applicationContext
            lifecycleScope.launch(Dispatchers.IO) {
                val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
                val gear = cz.uhk.macroflow.pokemon.skills.Gear.from(id)
                if (gear == null || SS.count(ctx, id) == 0) SS.add(ctx, id, 1)
                kotlinx.coroutines.withContext(Dispatchers.Main) { showMapToast("🎁 Přidáno: ${gear?.label ?: id}") }
            }
        }

        movementEngine = MovementEngine(this, ashView, mapBackground)
        movementEngine.onMoved = { updateCamera() }

        questDialogManager = QuestDialogManager(
            this,
            findViewById(R.id.tutorialOverlay),
            findViewById(R.id.tutorialText),
            findViewById(R.id.tutorialTeacher),
            imageLoader,
            findViewById(R.id.questProgressLine)
        )

        questManager = QuestManager(
            db = AppDatabase.getDatabase(this),
            dialogManager = questDialogManager,
            scope = lifecycleScope,
            // Osobní cíle dne (fáze A+B) – questy krále odměňují jejich trefení, ne pevná čísla
            targetsProvider = {
                val t = cz.uhk.macroflow.dashboard.MacroCalculator.calculate(applicationContext)
                cz.uhk.macroflow.energy.Adherence.Targets(t.calories, t.protein, t.carbs, t.fat)
            },
            introPrefs = getSharedPreferences("QuestPrefs", Context.MODE_PRIVATE),
            // Hvozd (docs/adr/0045): voda proti osobnímu cíli, příznaky příběhu, odevzdání surovin
            waterProvider = {
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
                val drank = AppDatabase.getDatabase(applicationContext).waterDao().getTotalMlForDateSync(today)
                val target = (cz.uhk.macroflow.dashboard.MacroCalculator.calculate(applicationContext).water * 1000).toInt()
                drank to target
            },
            storyFlagProvider = { key -> StoryFlags.isSet(applicationContext, key) },
            itemCounter = { ids -> ids.associateWith { cz.uhk.macroflow.pokemon.skills.SkillStore.count(applicationContext, it) } },
            // zkouška havíře Vendelína (docs/adr/0050): odpracované minuty od začátku fáze
            afkMinutesProvider = { skills, startedAt ->
                cz.uhk.macroflow.pokemon.skills.SkillStore.workedMinutesSince(applicationContext, skills, startedAt, System.currentTimeMillis() / 1000)
            },
            itemConsumer = { items ->
                val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
                if (items.any { (id, n) -> SS.count(applicationContext, id) < n }) false
                else { items.forEach { (id, n) -> SS.consume(applicationContext, id, n) }; true }
            },
            // Praskliny ve fasádě (docs/adr/0047): postava občas na zlomek věty zaváhá
            crackProvider = { qid -> Insight.crack(qid, StoryFlags.insight(applicationContext), kotlin.random.Random.nextInt(100)) }
        )

        // PROPOJENÍ: Když se v manageru změní progres (např. onMealLogged), refreshneme UI
        questManager.onStageReward = { r -> cz.uhk.macroflow.pokemon.skills.SkillStore.grantItemOnce(applicationContext, r.itemId) }
        questManager.onProgressChanged = { progress ->
            // lifecycleScope zajistí, že nebudeme sahat do UI, pokud aktivita umírá
            lifecycleScope.launch(Dispatchers.Main) {
                // Hvozd: nová fáze mění mapu (Soulord u dubu, uzdravený les) – docs/adr/0045
                val forestStage = progress.currentStageIndex to progress.isCompleted
                if (progress.questId == ForestHeart.QUEST_ID && forestStage != lastForestStage) {
                    val first = lastForestStage == null
                    lastForestStage = forestStage
                    if (!first || currentBiome == BiomeType.FOREST) refreshStoryDecor()
                }
                // Doly: kniha se rozzáří ve fázi podpisu (jen při změně fáze, ne při každé minutě zkoušky)
                val minesStage = progress.currentStageIndex to progress.isCompleted
                if (progress.questId == Vendelin.QUEST_ID && minesStage != lastMinesStage) {
                    val first = lastMinesStage == null
                    lastMinesStage = minesStage
                    if (!first && currentBiome == BiomeType.MINES) refreshStoryDecor()
                }
                val journalFragment = supportFragmentManager.findFragmentByTag(TAG_JOURNAL) as? QuestJournalFragment
                // Kontrolujeme isAdded(), aby fragment nespadl při pokusu o refresh
                if (journalFragment != null && journalFragment.isAdded) {
                    journalFragment.refreshData()
                }
            }
        }

        setupGudwinView()
        setupStarterBush()
        setupMeadowBush()

        companionManager = CompanionManager(this, findViewById(R.id.ivCompanion), findViewById(R.id.tvCompanionLabel), findViewById(R.id.companionShadow), lifecycleScope)

        findViewById<ImageButton>(R.id.btnStartTutorial).setOnClickListener { questDialogManager.startTutorial() }
        findViewById<View>(R.id.btnExitMap).setOnClickListener { finish() }

        // Hudba a zvuky: přepínač v HUD, zvuky se načtou předem
        cz.uhk.macroflow.pokemon.audio.GameAudio.preload(this)
        val btnSound = findViewById<ImageButton>(R.id.btnSound)
        fun soundIcon() = btnSound.setImageResource(
            if (cz.uhk.macroflow.pokemon.audio.GameAudio.isEnabled(this)) android.R.drawable.ic_lock_silent_mode_off
            else android.R.drawable.ic_lock_silent_mode)
        soundIcon()
        btnSound.setOnClickListener {
            val on = !cz.uhk.macroflow.pokemon.audio.GameAudio.isEnabled(this)
            cz.uhk.macroflow.pokemon.audio.GameAudio.setEnabled(this, on)
            soundIcon()
            showMapToast(if (on) "🔊 Hudba a zvuky zapnuty" else "🔇 Ticho v Makrosvětě")
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                questDialogManager.isVisible() -> questDialogManager.hide()   // zpět zavře tutoriál/dialog
                cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.isOpen(findViewById(R.id.mapRootContainer)) ->
                    cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.close(findViewById(R.id.mapRootContainer))
                supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
                else -> finish()
            }
        }

        // Parťák vlevo nahoře: dřív se načetl jen při otevření mapy – změna v Domově se neprojevila.
        // Obnoví se při změně aktivního Makromona i po návratu z Domova / souboje (level, evoluce).
        gamePrefs.registerOnSharedPreferenceChangeListener(companionPrefsListener)

        // Postup příběhu: sloučení GamePrefs se synchronizovanými story_* předměty (docs/adr/0044)
        lifecycleScope.launch {
            val restored = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) { StoryFlags.sync(applicationContext) }
            }.getOrDefault(0)
            if (restored > 0 && !isFinishing) refreshStoryDecor()
        }
        supportFragmentManager.addOnBackStackChangedListener {
            refreshStepBar()            // nad deníkem / soubojem ukazatel kroků nemá co dělat (a bral dotyky)
            if (supportFragmentManager.backStackEntryCount == 0) {
                companionManager.refresh()
                refreshStoryDecor()     // po souboji se strážcem / legendou
                questManager.recheck()  // boss Hvozdu nastavil příznak → fáze questu se splní
                checkAwards()           // chycení, denní úkoly… (docs/adr/0037)
            }
        }

        mapBackground.post {
            // Debug: rovnou do jiné lokace (adb … --es debug_biome MEADOW)
            val debugBiome = if (BuildConfig.DEBUG) intent.getStringExtra("debug_biome")?.let { runCatching { BiomeType.valueOf(it) }.getOrNull() } else null
            val last = if (intent.getStringExtra("TARGET_LOCATION") == null) lastLocation() else null
            when {
                debugBiome != null -> enterBiomeAtNode(debugBiome, BiomeRegistry.definition(debugBiome)?.graph?.first()?.id ?: "", MapTransition.NONE)
                // Makrosvět si pamatuje, kde jsi byl naposledy (AFK těžba tam pokračuje)
                last != null -> changeBiome(last.first, last.second, MapTransition.NONE)
                else -> changeBiome(BiomeType.TOWN, PointF(0.480f, 0.275f), MapTransition.NONE)
            }
            intent.getStringExtra("TARGET_LOCATION")?.let { triggerHotspotAction(it.lowercase()) }
            // Debug: splnit N fází aktivního questu (adb … --ei debug_quest_complete 5)
            if (BuildConfig.DEBUG) intent.getIntExtra("debug_quest_complete", 0).takeIf { it > 0 }?.let { n ->
                (1..n).forEach { i -> mapWorld.postDelayed({ if (!isFinishing) questManager.debugCompleteStage() }, 1500L + i * 600L) }
            }
            // Debug: rovnou souboj v aréně dané lokace (adb … --es debug_battle WATER)
            if (BuildConfig.DEBUG) intent.getStringExtra("debug_battle")?.let { b ->
                if (runCatching { BiomeType.valueOf(b) }.isSuccess) {
                    gamePrefs.edit().putString("LAST_BIOME", b).apply()
                    replaceMapContent(PokemonBattleFragment())
                }
            }
        }

        findViewById<ImageButton>(R.id.btnOpenJournal).setOnClickListener {
            replaceMapContent(QuestJournalFragment(), TAG_JOURNAL)
        }
        // Ladění shiny (jen debug build): podržením deníku bude příští setkání shiny
        // Ladicí menu (jen debug build): podržení deníku
        if (BuildConfig.DEBUG) findViewById<ImageButton>(R.id.btnOpenJournal).setOnLongClickListener {
            showDebugMenu()
            true
        }

        companionManager.refresh()
        observeGameData()
    }

    private val gamePrefs by lazy { getSharedPreferences("GamePrefs", Context.MODE_PRIVATE) }

    /** Silná reference – SharedPreferences drží posluchače jen slabě. */
    private val companionPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "currentOnBarCaughtDate" || key == "currentOnBarCapturedId" || key == "pokemonAcquired") {
            runOnUiThread { companionManager.refresh() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::companionManager.isInitialized) companionManager.refresh()
        cz.uhk.macroflow.pokemon.audio.GameAudio.resume(this)
        // AFK těžba / kácení: po návratu ukázat, co se za tu dobu udělalo (docs/adr/0035)
        mapWorld.postDelayed({ reportAfk() }, 700)
    }

    override fun onPause() {
        cz.uhk.macroflow.pokemon.audio.GameAudio.pause()
        saveLastLocation()
        super.onPause()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POSLEDNÍ MÍSTO A AFK (docs/adr/0035)
    // ─────────────────────────────────────────────────────────────────────────

    private fun saveLastLocation() {
        if (!::movementEngine.isInitialized) return
        val p = movementEngine.getCurrentPosition()
        gamePrefs.edit()
            .putString("map_last_biome", currentBiome.name)
            .putFloat("map_last_x", p.x).putFloat("map_last_y", p.y)
            .putLong("map_last_seen", System.currentTimeMillis() / 1000)
            .apply()
    }

    /** Uložené poslední místo na mapě (lokace + pozice), nebo null. */
    private fun lastLocation(): Pair<BiomeType, PointF>? {
        val b = gamePrefs.getString("map_last_biome", null)?.let { runCatching { BiomeType.valueOf(it) }.getOrNull() } ?: return null
        if (BiomeRegistry.definition(b) == null) return null
        val x = gamePrefs.getFloat("map_last_x", -1f); val y = gamePrefs.getFloat("map_last_y", -1f)
        if (x !in 0f..1f || y !in 0f..1f) return null
        return b to PointF(x, y)
    }

    private var afkChecking = false

    /** Vybere AFK kusy a ukáže dřevěnou ceduli „Vítej zpět“ (jen po delší nepřítomnosti). */
    private fun reportAfk() {
        if (afkChecking || isFinishing) return
        val lastSeen = gamePrefs.getLong("map_last_seen", 0L)
        val now = System.currentTimeMillis() / 1000
        val away = if (lastSeen > 0) now - lastSeen else 0L
        afkChecking = true
        val ctx = applicationContext
        lifecycleScope.launch {
            val res = kotlinx.coroutines.withContext(Dispatchers.IO) {
                cz.uhk.macroflow.pokemon.skills.SkillStore.claimActivity(ctx, now)
            }
            afkChecking = false
            refreshGatherPlaque()
            checkAwards()
            if (res == null || away < 60 || res.claim.units == 0) return@launch
            if (supportFragmentManager.backStackEntryCount > 0) return@launch
            cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.afkReport(findViewById(R.id.mapRootContainer), away, res)
        }
    }

    override fun onDestroy() {
        cz.uhk.macroflow.pokemon.audio.GameAudio.release()
        gamePrefs.unregisterOnSharedPreferenceChangeListener(companionPrefsListener)
        super.onDestroy()
    }

    fun getCurrentBiome(): BiomeType = currentBiome

    private fun setupGudwinView() {
        gudwinNPC = ImageView(this).apply {
            setImageResource(R.drawable.gudwin_oliver)
            layoutParams = FrameLayout.LayoutParams(
                (45 * resources.displayMetrics.density).toInt(),
                (45 * resources.displayMetrics.density).toInt()
            )
            visibility = View.GONE
            elevation = 5f
        }
        mapWorld.addView(gudwinNPC)
    }

    private fun setupStarterBush() {
        starterBush = ImageView(this).apply {
            setImageResource(R.drawable.bush)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                (50 * resources.displayMetrics.density).toInt(),
                (50 * resources.displayMetrics.density).toInt()
            )
            visibility = View.GONE
            elevation = 5f
        }
        mapWorld.addView(starterBush)
    }

    private fun setupMeadowBush() {
        meadowBushNPC = ImageView(this).apply {
            setImageResource(R.drawable.npc_bush)
            layoutParams = FrameLayout.LayoutParams(
                (55 * resources.displayMetrics.density).toInt(),
                (55 * resources.displayMetrics.density).toInt()
            )
            visibility = View.GONE
            elevation = 5f
        }
        mapWorld.addView(meadowBushNPC)
    }

    /**
     * Reaktivní napojení na data funkční části (kroky, jídla, skeny).
     * Nahrazuje dřívější polling každé 2 s, který běžel i na pozadí.
     * Běží jen ve stavu STARTED; po návratu na mapu se vše přepočítá.
     */
    private fun observeGameData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                questManager.observeGameData { steps ->
                    currentDailySteps = steps
                    refreshStepBar()
                }
            }
        }
    }

    private fun refreshStepBar() {
        val gatedBiome = BiomeRegistry.definition(currentBiome)?.stepGoalFor
        if (gatedBiome == null || supportFragmentManager.backStackEntryCount > 0) {
            stepProgressBar.visibility = View.GONE
            return
        }
        stepProgressBar.visibility = View.VISIBLE
        stepProgressBar.setProgress(currentDailySteps, BiomeAccess.requiredSteps(gatedBiome))
    }

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownOnUi = false
    private var lastFreeTapTime = 0L

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (transitionRunning) return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.rawX; touchDownY = event.rawY
                touchDownOnUi = isOverUi(event.rawX, event.rawY)
            }
            MotionEvent.ACTION_UP -> if (!touchDownOnUi && handleMapTap(event)) return true
        }
        return super.dispatchTouchEvent(event)
    }

    /** Tlačítka, parťák, ukazatel kroků – klepnutí na ně nesmí poslat postavu na procházku. */
    private fun isOverUi(rawX: Float, rawY: Float): Boolean {
        val ui = listOf(
            findViewById<View>(R.id.btnExitMap)?.parent as? View,
            findViewById<View>(R.id.btnOpenJournal),
            findViewById<View>(R.id.tvCompanionLabel)?.parent as? View,
            stepProgressBar
        )
        val r = android.graphics.Rect()
        return ui.any { v -> v != null && v.isShown && v.getGlobalVisibleRect(r) && r.contains(rawX.toInt(), rawY.toInt()) }
    }

    /**
     * Klepnutí do mapy (docs/adr/0033): blízko aktivního místa → dojít k němu a spustit akci;
     * jinak volná chůze na místo klepnutí po průchozí ploše. Tažení prstem se nepočítá.
     */
    private fun handleMapTap(event: MotionEvent): Boolean {
        if (questDialogManager.isVisible() || supportFragmentManager.backStackEntryCount != 0) return false
        if (cameraOverride) return true          // kamera se kochá výhledem
        if (cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.isOpen(findViewById(R.id.mapRootContainer))) return false
        val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        if (kotlin.math.hypot(event.rawX - touchDownX, event.rawY - touchDownY) > slop * 2) return false

        // Souřadnice ve světě mapy (getLocationOnScreen zahrnuje i posun kamery)
        val viewport = findViewById<View>(R.id.mapMainContent)
        val location = IntArray(2)
        mapWorld.getLocationOnScreen(location)
        val worldX = event.rawX - location[0]
        val worldY = event.rawY - location[1]
        if (mapWorld.width <= 0 || mapWorld.height <= 0) return false
        val relX = worldX / mapWorld.width
        val relY = worldY / mapWorld.height

        val cave = BiomeRegistry.definition(currentBiome)?.cave
        val hotspots = if (cave == null) clickableNodes.toSet()
            else cave.encounterNodes + CAVE_ACTION_NODES + listOfNotNull(cave.exitNode, cave.crystalNode)
        val radius = if (cave == null) TAP_RADIUS else CAVE_TAP_RADIUS
        // Klepnutí přímo na objekt (jezírko, keř, balvan) – docs/adr/0037
        cave?.tapAreaAt(relX * cave.artW, relY * cave.artH)?.takeIf { it in hotspots }?.let {
            triggerHotspotAction(it); return true
        }
        // Nejbližší aktivní uzel v dosahu (ne první nalezený)
        val clickedWaypoint = movementEngine.navigationGraph
            .filter { it.id in hotspots }
            .map { it to MapCamera.tapDistance(it.pos.x, it.pos.y, relX, relY,
                mapWorld.width, mapWorld.height, viewport.width, viewport.height) }
            .filter { it.second < radius }
            .minByOrNull { it.second }?.first

        if (clickedWaypoint != null) {
            triggerHotspotAction(clickedWaypoint.id)
            return true
        }
        if (movementEngine.walkGrid == null) {
            // Bez mapy chůze postaru: v jeskyni k nejbližšímu uzlu cesty
            val nearest = if (cave == null) null else movementEngine.navigationGraph
                .map { it to MapCamera.tapDistance(it.pos.x, it.pos.y, relX, relY,
                    mapWorld.width, mapWorld.height, viewport.width, viewport.height) }
                .filter { it.second < TAP_RADIUS }
                .minByOrNull { it.second }?.first
            if (nearest != null) { triggerHotspotAction(nearest.id); return true }
            return false
        }
        val now = System.currentTimeMillis()
        val isDoubleTap = now - lastFreeTapTime < DOUBLE_CLICK_TIME
        lastFreeTapTime = now
        lastClickedNode = ""
        movementEngine.currentSpeed = if (isDoubleTap) MovementEngine.FAST_SPEED else MovementEngine.NORMAL_SPEED
        leaveGatherSpot(null)
        movementEngine.walkToPoint(worldX, worldY)
        return true
    }

    /** Mapa chůze biomu z assets/walk (načte se jednou; chybí-li, chodí se po grafu). */
    private val walkGrids = HashMap<BiomeType, cz.uhk.macroflow.pokemon.walk.WalkGrid?>()

    private fun walkGridFor(biome: BiomeType): cz.uhk.macroflow.pokemon.walk.WalkGrid? = walkGrids.getOrPut(biome) {
        runCatching {
            assets.open("walk/${biome.name.lowercase(Locale.ROOT)}.txt").bufferedReader().use {
                cz.uhk.macroflow.pokemon.walk.WalkGrid.parse(it.readText())
            }
        }.getOrNull()
    }

    private fun triggerHotspotAction(nodeName: String) {
        leaveGatherSpot(nodeName)
        val now = System.currentTimeMillis()
        val isDoubleClick = (nodeName == lastClickedNode && now - lastClickTime < DOUBLE_CLICK_TIME)
        lastClickTime = now
        lastClickedNode = nodeName

        val action: () -> Unit = action@{
            questManager.onNodeVisited(nodeName)
            // Starý dub: ve fázi s bossem se z kořenů vynoří Soulord místo divokého setkání
            if (nodeName == ForestHeart.OAK_NODE && rotBossWaiting()) {
                showMapToast("🍂 Kořeny Starého dubu se zachvějí a z hniloby stoupá fialová mlha…")
                startSpecialBattle(SpecialBattle.FOREST_ROT, BiomeType.FOREST)
                return@action
            }
            // Roztržený list Kustodiátu (docs/adr/0047) – najde se jednou, místo běžné akce místa
            Insight.pageAt(currentBiome.name, nodeName, StoryFlags.all(this))?.let { findTornPage(it); return@action }

            when (nodeName) {
                "gudwin", "meadow_npc", "kral_mlsak" -> questManager.checkNpcInteraction()
                "cave", "mine" -> BiomeRegistry.caveBehind(nodeName)?.let { cave ->
                    val entry = BiomeRegistry.definition(cave)?.cave?.exitNode ?: return@let
                    enterBiomeAtNode(cave, entry, MapTransition.CAVE_IN)
                }
                // Doly (docs/adr/0049): zabedněná štola ve Starém dole → sestup; zpět tunelem do dolu
                MinesMap.MAZE_NODE -> enterBiomeAtNode(BiomeType.MINES, MinesMap.EXIT_NODE, MapTransition.MINE_DESCENT)
                MinesMap.EXIT_NODE -> enterBiomeAtNode(BiomeType.CAVE_MAZE, MinesMap.MAZE_NODE, MapTransition.CAVE_IN)
                MinesMap.NET_NODE -> onOldNet()
                MinesMap.DOOR_NODE -> showMapToast("🚪 " + MinesMap.DOOR_TEXT +
                    (if (Insight.level(StoryFlags.all(this)) >= 3) MinesMap.DOOR_TEXT_INSIGHT else "") +
                    (if (StoryFlags.isSet(this, Vendelin.BOOK_SIGNED_KEY)) Vendelin.DOOR_HANDWRITING else ""))
                Vendelin.NODE -> { questManager.loadQuest(Vendelin.QUEST_ID); mapWorld.postDelayed({ if (!isFinishing) questManager.checkNpcInteraction() }, 150) }
                Vendelin.BOOK_NODE -> onMinerBook()
                "vychod_jeskyne", "vychod_dolu", "vstup_z_louky", "vstup_ze_svatyne", "vstup_z_hvozdu" -> BiomeRegistry.definition(currentBiome)?.cave?.let {
                    enterBiomeAtNode(BiomeType.valueOf(it.parentBiome), it.mountainNode,
                        if (it.isCave) MapTransition.CAVE_OUT else MapTransition.FADE)
                }
                "tezba" -> scanInMine()
                cz.uhk.macroflow.pokemon.skills.MeadowLayout.TABLE_NODE -> openCraftingTable()
                "strom_dub", "strom_briza", "strom_javor", "zila_med", "zila_stribro", "zila_zlato",
                MinesMap.SPARK_NODE, MinesMap.CRYSTAL_NODE, MinesMap.MAGMA_NODE ->
                    cz.uhk.macroflow.pokemon.skills.GatherSpot.fromNode(nodeName)?.let { openGatherSpot(it) }
                "zahon_1", "zahon_2", "zahon_3", "zahon_4" ->
                    cz.uhk.macroflow.pokemon.skills.MeadowLayout.plotIndex(nodeName)?.let { onPlot(it) }
                "les_sever" -> tryEnterForest()
                ForestHeart.MYDRUS_NODE -> onMytina()
                // Zapomenutý háj (docs/adr/0046)
                SecretGrove.FOREST_NODE -> onHiddenThorns()
                "mural_1", "mural_2", "mural_3" -> SecretGrove.mural(nodeName)?.let { showMural(it) }
                SecretGrove.POOL_NODE -> showMapToast("✨ " + SecretGrove.POOL_TEXT)
                SecretGrove.ALTAR_NODE -> onGroveAltar()
                SecretGrove.GRAVE_NODE -> onGroveGrave()
                "krystal_modry", "krystal_cerveny" -> BiomeRegistry.definition(currentBiome)?.cave?.let { onCrystalNode(it) }
                "peak" -> onShrine()
                SkyPass.GATE_NODE -> onWorldGate()
                SkyPass.CLAWS_NODE -> showMapToast("🐉 Do skály jsou vyryté hluboké rýhy po drápech a leží tu šupina rudá jako žhavé uhlí.\n" +
                    "Drakirra tudy odletěla – stopy míří dolů, nad korunami Hvozdu.")
                SkyPass.CAIRN_NODE -> showMapToast("🪨 Mužík z plochých kamenů. Poutníci sem pokládají kámen pro štěstí na cestě mezi světy.\n" +
                    "Na tom nejvyšším je vyrytý list.")
                SkyPass.VISTA_NODE, SkyPass.LEDGE_NODE -> vistaShot()
                "camp" -> restAtCamp()
                "rozcesti_hory" -> showMapToast("🪧 ↑ Socha krále Mlsáka · ↖ Důl a horní stezka\n← Tábor · ↓ Zpět na louku")
                "starter_bush" -> {
                    // Úvodní keřík je „vynucené“ setkání jen do konce úvodního úkolu; potom je to
                    // obyčejné městské křoví (se shiny šancí) – dřív zůstal vynucený navždy a shiny
                    // ve městě nikdy nepadl
                    val intro = !questManager.isIntroQuestFinished()
                    getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit()
                        .putString("LAST_BIOME", currentBiome.name)
                        .apply { if (intro) putString("FORCE_ENCOUNTER_ID", "starter_bush") else remove("FORCE_ENCOUNTER_ID") }
                        .apply()
                    replaceMapContent(PokemonBattleFragment())
                }
                "hory" -> tryEnterBiome(BiomeType.MOUNTAINS, entryNode = "vstup_z_meadow")
                "vstup_z_meadow" -> enterBiomeAtNode(BiomeType.MEADOW, "hory")
                "les" -> {
                    if (!questManager.isIntroQuestFinished()) {
                        movementEngine.cancel()
                        showBlockingDialog()
                    } else {
                        changeBiome(BiomeType.MEADOW, PointF(0.340f, 0.640f))
                    }
                }
                "domov" -> replaceMapContent(InventoryFragment())
                "pokedex" -> replaceMapContent(MakrodexFragment())
                "obchod" -> replaceMapContent(PokemonShopFragment())
                "vstup_z_town" -> changeBiome(BiomeType.TOWN, PointF(0.46f, 0.15f))
                in encounterNodes -> {
                    if ((1..100).random() <= 90) {
                        // Jeskyně ukládají svůj biom (vlastní intro); Makromoni a questy jsou horské (wildBiome)
                        // Jezírka ve Hvozdu jsou vodní setkání
                        val encounterBiome = if (nodeName == "voda" || nodeName.startsWith("jezirko_")) BiomeType.WATER else currentBiome
                        getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit()
                            .putString("LAST_BIOME", encounterBiome.name)
                            .remove("FORCE_ENCOUNTER_ID")
                            .apply()
                        replaceMapContent(PokemonBattleFragment())
                    } else {
                        // Dřív se v 10 % nestalo nic a bod působil rozbitě
                        showMapToast(emptyEncounterText(nodeName))
                    }
                }
            }
        }

        movementEngine.currentSpeed = if (isDoubleClick) MovementEngine.FAST_SPEED else MovementEngine.NORMAL_SPEED
        movementEngine.walkToNode(nodeName) {
            runOnUiThread { action() }
        }
    }

    /**
     * Důl v horách: sken čárového kódu přímo z mapy. Jde přes stejný
     * BarcodeProductLookup jako funkční část, takže se zapíše i herní událost.
     */
    private fun scanInMine() {
        GmsBarcodeScanning.getClient(this).startScan().addOnSuccessListener { barcode ->
            val code = barcode.rawValue ?: return@addOnSuccessListener
            lifecycleScope.launch {
                val product = BarcodeProductLookup.lookup(AppDatabase.getDatabase(this@MakromonMapActivity), code)
                val text = if (product == null) {
                    "V dole jsi nic nevytěžil – produkt $code neznám."
                } else {
                    "Vytěžil jsi: ${product.name}\n" +
                        "B %.1f g · S %.1f g · T %.1f g (na 100 g)".format(
                            Locale.US, product.proteins100g, product.carbs100g, product.fat100g
                        )
                }
                showMapToast(text)
            }
        }
    }

    private fun emptyEncounterText(node: String): String = when (node) {
        "jezirko" -> "Na hladině podzemního jezírka se jen zavlnil odraz hub. Zkus to znovu."
        "houby" -> "Svítící houby pomalu pulzují… nikdo tu není. Zkus to znovu."
        "krystaly_j", "balvany_j" -> "Mezi kameny to jen zapraskalo. Zkus to znovu."
        "vozik" -> "Ve starém vozíku je jen hlušina. Zkus to znovu."
        "netopyri" -> "Netopýři se rozletěli, ale nic dalšího se nehnulo. Zkus to znovu."
        "slepa_chodba" -> "Slepá chodba… jen kape voda. Zkus to znovu."
        "hlubina" -> "Z hlubiny zafoukal studený vzduch. Zkus to znovu."
        "rumpal" -> "Rumpál zaskřípal, lano se zhouplo… nic. Zkus to znovu."
        "puklina" -> "V puklině jen zacinkaly krystaly. Zkus to znovu."
        "popel" -> "Žhnoucí mech se rozpadl na popel. Zkus to znovu."
        "trava_1", "trava_2", "trava_3", "houstina" -> "Vysoká tráva se jen zavlnila ve větru. Zkus to znovu."
        "jezirko_1", "jezirko_2" -> "Po hladině přeběhla vážka, víc nic. Zkus to znovu."
        "stary_dub" -> "Ve starém dubu to zašustilo… jen veverka. Zkus to znovu."
        "skaly1", "skaly2" -> "Mezi skalami se nic nehnulo. Zkus to znovu."
        "voda" -> "Hladina je klidná. Zkus to znovu."
        else -> "Křoví se ani nehnulo. Zkus to znovu."
    }

    /**
     * Tábor v horách: odpočinek u ohně = přehled dne z funkční části
     * (kroky a co zbývá z dnešních cílů) – most mezi Makrosvětem a aplikací.
     */
    private fun restAtCamp() {
        lifecycleScope.launch {
            val status = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val ctx = applicationContext
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
                val eaten = AppDatabase.getDatabase(ctx).consumedSnackDao().getConsumedByDateSync(today)
                cz.uhk.macroflow.dashboard.MacroFlowEngine.calculateDailyStatusForDate(ctx, java.util.Date(), eaten)
            }
            fun left(v: Double, unit: String) = if (v > 0) "${v.toInt()} $unit" else "splněno ✓"
            showMapToast(
                "🔥 Odpočíváš u táboráku.\n" +
                    "Dnes ${currentDailySteps} kroků.\n" +
                    "Zbývá: ${left(status.caloriesLeft, "kcal")} · bílkoviny ${left(status.proteinLeft, "g")}"
            )
        }
    }

    /** Vstup do biomu se zámkem (dnes nachozené kroky). Kontroluje se jen při vstupu. */
    private fun tryEnterBiome(target: BiomeType, entryNode: String) {
        // Debug: sedmimílové boty přeskočí denní krokový zámek (jen v debug buildu)
        val boots = BuildConfig.DEBUG && gamePrefs.getBoolean(DEBUG_BOOTS_KEY, false)
        if (boots || BiomeAccess.canEnter(target, currentDailySteps)) {
            enterBiomeAtNode(target, entryNode)
        } else {
            showStepWarningToast(BiomeAccess.missingSteps(target, currentDailySteps))
        }
    }

    private fun enterBiomeAtNode(target: BiomeType, nodeId: String, transition: MapTransition = MapTransition.FADE) {
        val graph = BiomeRegistry.definition(target)?.graph ?: return
        val pos = BiomeRegistry.nodePos(graph, nodeId) ?: return
        changeBiome(target, PointF(pos.x, pos.y), transition)
    }

    private var awardsChecking = false

    /** Zapíše nově splněná ocenění a oznámí je (docs/adr/0037). */
    private fun checkAwards() {
        if (awardsChecking || isFinishing) return
        awardsChecking = true
        val ctx = applicationContext
        lifecycleScope.launch {
            val fresh = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) { cz.uhk.macroflow.pokemon.skills.AwardStore.check(ctx) }
            }.getOrDefault(emptyList())
            awardsChecking = false
            if (fresh.isEmpty()) return@launch
            val text = fresh.joinToString("\n") { "🏅 Nové ocenění: ${it.title}" } + "\nNajdeš ho v deníku pod záložkou Ocenění."
            mapWorld.postDelayed({ if (!isFinishing) showMapToast(text) }, 1200)
        }
    }

    private fun showStepWarningToast(missingSteps: Int) =
        showMapToast("Tohle bys na jeden zátah neušel! Dnes se ještě projdi – chybí ti $missingSteps kroků.")

    private fun showMapToast(message: String) {
        val layout = layoutInflater.inflate(R.layout.layout_custom_toast, null)
        layout.findViewById<TextView>(R.id.toastText).text = message
        with (Toast(applicationContext)) {
            // dole na obrazovce – uprostřed zakrývala mapu
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, (96 * resources.displayMetrics.density).toInt())
            duration = Toast.LENGTH_LONG
            view = layout
            show()
        }
    }

    private fun showBlockingDialog() {
        questDialogManager.showQuestDialog(
            speakerResource = R.drawable.gudwin_oliver,
            speakerName = "Gudwin Oliver",
            stageName = "Cesta uzavřena",
            text = "Zadrž! Ještě jsi nesplnil vše, co jsem tě učil. Dokonči mé úkoly v Town, než se vydáš do nebezpečného Meadow!",
            totalSteps = 1,
            currentStepIndex = 0
        )
    }

    private fun changeBiome(newBiome: BiomeType, startPos: PointF, transition: MapTransition = MapTransition.FADE) {
        val container = findViewById<ViewGroup>(R.id.mapMainContent)
        val transitionAction: () -> Unit = {
            currentBiome = newBiome
            // první návštěva průsmyku: kamera začne nahoře na výhledu a sjede k hráči
            mapWorld.animate().cancel(); cameraOverride = false
            val firstSkyVisit = newBiome == BiomeType.SKY_PASS && !StoryFlags.isSet(this, SkyPass.VISITED_KEY)
            if (newBiome == BiomeType.SKY_PASS) StoryFlags.set(this, SkyPass.VISITED_KEY)
            if (newBiome == BiomeType.FOREST) {
                val ctx = applicationContext
                lifecycleScope.launch {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        cz.uhk.macroflow.pokemon.skills.AwardStore.recordOnce(ctx, cz.uhk.macroflow.pokemon.skills.AwardStore.VISIT_FOREST)
                    }
                    checkAwards()
                }
            }
            refreshStepBar()
            // Úvodní tutoriál (otazník) patří zatím jen k městu
            findViewById<View>(R.id.btnStartTutorial).visibility = if (newBiome == BiomeType.TOWN) View.VISIBLE else View.GONE

            val def = BiomeRegistry.definition(newBiome)
            if (newBiome == BiomeType.FOREST) lastForestStage = null   // po načtení questu se dekorace obnoví
            if (newBiome == BiomeType.TOWN) maybeStationFlicker()
            if (newBiome == BiomeType.MINES && !StoryFlags.isSet(this, MinesMap.VISITED_KEY)) {
                StoryFlags.set(this, MinesMap.VISITED_KEY)
                mapWorld.postDelayed({ if (!isFinishing) showMapToast("🔥 " + MinesMap.FIRST_VISIT_TEXT) }, 1900)
            }
            // První příchod do háje: ozve se šepot (úvod tajného questu)
            if (newBiome == BiomeType.HIDDEN_GROVE && !StoryFlags.isSet(this, SecretGrove.FOUND_KEY)) {
                StoryFlags.set(this, SecretGrove.FOUND_KEY)
                mapWorld.postDelayed({
                    if (!isFinishing && currentBiome == BiomeType.HIDDEN_GROVE && questManager.getActiveQuestId() == SecretGrove.QUEST_ID)
                        questManager.checkNpcInteraction()
                }, 2200)
            }
            // Quest Hvozdu začne až po souboji s legendou – do té doby je mýtina prázdná
            if (newBiome != BiomeType.FOREST || ForestHeart.questAvailable(StoryFlags.isSet(this, LegendProgress.LEGEND_KEY)))
                def?.questId?.let { questManager.loadQuest(it) }
            cz.uhk.macroflow.pokemon.audio.GameAudio.playFor(this, newBiome.name)
            gudwinNPC.visibility = if (newBiome == BiomeType.TOWN) View.VISIBLE else View.GONE
            starterBush.visibility = if (newBiome == BiomeType.TOWN) View.VISIBLE else View.GONE
            meadowBushNPC.visibility = if (newBiome == BiomeType.MEADOW) View.VISIBLE else View.GONE
            clearDecor()

            if (def != null) {
                mapBackground.setImageResource(def.backgroundRes)
                layoutWorld(def.cave)
                movementEngine.updateBiome(def.graph, startPos, walkGridFor(newBiome))
            }

            when (newBiome) {
                BiomeType.TOWN -> {
                    val gudwinPos = BiomeRegistry.TOWN_GRAPH.find { it.id == "gudwin" }?.pos ?: PointF(0.120f, 0.520f)
                    val bushPos = BiomeRegistry.TOWN_GRAPH.find { it.id == "starter_bush" }?.pos ?: PointF(0.200f, 0.170f)
                    gudwinNPC.post {
                        gudwinNPC.x = gudwinPos.x * mapWorld.width - (gudwinNPC.width / 2)
                        gudwinNPC.y = gudwinPos.y * mapWorld.height - gudwinNPC.height
                    }
                    starterBush.post {
                        starterBush.x = bushPos.x * mapWorld.width - (starterBush.width / 2)
                        starterBush.y = bushPos.y * mapWorld.height - starterBush.height
                    }
                }
                BiomeType.MEADOW -> {
                    val bushNpcPos = BiomeRegistry.MEADOW_GRAPH.find { it.id == "meadow_npc" }?.pos ?: PointF(0.630f, 0.430f)
                    meadowBushNPC.post {
                        meadowBushNPC.x = bushNpcPos.x * mapWorld.width - (meadowBushNPC.width / 2)
                        meadowBushNPC.y = bushNpcPos.y * mapWorld.height - (meadowBushNPC.height / 0.8f)
                    }
                }
                else -> {}
            }
            mapWorld.post { refreshStoryDecor() }
            if (firstSkyVisit) mapWorld.post { mapWorld.post { vistaShot(establishing = true) } }   // až po rozložení mapy a postavy
        }

        when (transition) {
            MapTransition.NONE -> transitionAction()
            // Město, louka, Hvozd a hory mají vlastní scénu přechodu (docs/adr/0042), jinak prolnutí
            MapTransition.FADE -> if (newBiome.name in LOCATION_SCENES) playLocationTransition(newBiome, transitionAction)
                else container.animate().alpha(0f).setDuration(400).withEndAction {
                    transitionAction()
                    container.animate().alpha(1f).setDuration(400).start()
                }.start()
            MapTransition.CAVE_IN, MapTransition.CAVE_OUT ->
                playCaveTransition(exiting = transition == MapTransition.CAVE_OUT, onCovered = transitionAction)
            MapTransition.MINE_DESCENT -> playCaveTransition(exiting = false, onCovered = transitionAction, descent = true)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KAMERA (jeskyně): svět je větší než obrazovka a jede za postavou
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Velikost světa: běžné mapy = obrazovka (centerCrop jako dřív). Jeskyně = obrázek zvětšený
     * celočíselným násobkem bez vyhlazení; kamera pak posouvá celý svět.
     */
    private fun layoutWorld(cave: CaveMap?) {
        val viewport = findViewById<View>(R.id.mapMainContent)
        val lp = mapWorld.layoutParams
        mapWorld.translationX = 0f
        mapWorld.translationY = 0f
        worldScale = 0
        if (cave == null) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            mapBackground.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            val scale = MapCamera.pixelScale(cave.artW, cave.artH, viewport.width, viewport.height, cave.artPixelsAcross)
            worldScale = scale      // dekorace počítají se stejným násobkem, ne se (možná ještě starou) šířkou světa
            lp.width = cave.artW * scale
            lp.height = cave.artH * scale
            mapBackground.scaleType = ImageView.ScaleType.FIT_XY
            (mapBackground.drawable as? android.graphics.drawable.BitmapDrawable)?.apply {
                isFilterBitmap = false; setAntiAlias(false)
            }
        }
        mapWorld.layoutParams = lp
    }

    /** Kamera právě jede po vlastní dráze (výhled v průsmyku) – nesleduje postavu. */
    private var cameraOverride = false

    /**
     * Pohled na nový kraj z Nebeského průsmyku (docs/adr/0044): kamera vyjede nahoru k výhledu,
     * chvíli se kochá a vrátí se k hráči. [establishing] = první příchod: začne rovnou nahoře
     * (ještě pod otevírajícím se přechodem) a pomalu sjede dolů k hráči.
     */
    private fun vistaShot(establishing: Boolean = false) {
        if (currentBiome != BiomeType.SKY_PASS || cameraOverride) return
        val viewport = findViewById<View>(R.id.mapMainContent)
        if (viewport.height <= 0 || mapWorld.height <= viewport.height) return
        val playerTy = MapCamera.offset(ashView.y + ashView.height / 2f, viewport.height, mapWorld.height)
        val topTy = MapCamera.offset(SkyPass.VISTA_FOCUS_Y * mapWorld.height.toFloat() / SkyPass.MAP.artH, viewport.height, mapWorld.height)
        cameraOverride = true
        movementEngine.cancel()
        fun pan(from: Float, to: Float, ms: Long, delay: Long, end: () -> Unit) {
            mapWorld.translationY = from
            mapWorld.animate().translationY(to).setStartDelay(delay).setDuration(ms)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction { if (!isFinishing) end() }.start()
        }
        val done = { cameraOverride = false; updateCamera() }
        if (establishing) {
            mapWorld.postDelayed({ if (!isFinishing) showMapToast("🌄 Za Branou světů leží cizí kraj – řeka, terasová pole a město s arénou…") }, 1400)
            pan(topTy, playerTy, 3600, 2600) { done() }
        } else {
            pan(playerTy, topTy, 2400, 0) {
                showMapToast("🌄 Pod mořem mraků se v údolí rozkládá cizí kraj: terasová pole, řeka " +
                    "a město s arénou. Za ním kouří sopka a nad vším plují ostrovy s vodopády.\nTam vede Brána světů.")
                pan(topTy, playerTy, 2400, 3200) { done() }
            }
        }
    }

    /** Postava uprostřed obrazovky, ale kamera nevyjede za okraj pozadí. */
    private fun updateCamera() {
        if (cameraOverride) return
        if (BiomeRegistry.definition(currentBiome)?.cave == null) {
            mapWorld.translationX = 0f; mapWorld.translationY = 0f
            return
        }
        val viewport = findViewById<View>(R.id.mapMainContent)
        mapWorld.translationX = MapCamera.offset(ashView.x + ashView.width / 2f, viewport.width, mapWorld.width)
        mapWorld.translationY = MapCamera.offset(ashView.y + ashView.height / 2f, viewport.height, mapWorld.height)
    }

    /** Přechod do lokace: scéna zakryje obrazovku, vymění se mapa a scéna se sama otevře. */
    private fun playLocationTransition(target: BiomeType, onCovered: () -> Unit) {
        val root = findViewById<FrameLayout>(R.id.mapRootContainer)
        movementEngine.cancel()
        transitionRunning = true
        val overlay = cz.uhk.macroflow.pokemon.transition.LocationTransitionView(this, target.name).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            elevation = 200f
        }
        overlay.onCovered = {
            onCovered()
            // otevřít až po vykreslení nové mapy (velký obrázek se dekóduje a nahrává do GPU)
            mapWorld.post { mapWorld.postOnAnimation { mapWorld.postDelayed({ overlay.release() }, 180) } }
        }
        overlay.onFinished = {
            root.removeView(overlay)
            transitionRunning = false
        }
        root.addView(overlay)
        overlay.post { overlay.start() }
    }

    /** Tmavě modrý mechový přechod: pod plně zakrytou obrazovkou se vymění mapa, pak se překryv rozplyne. */
    private fun playCaveTransition(exiting: Boolean, onCovered: () -> Unit, descent: Boolean = false) {
        val root = findViewById<FrameLayout>(R.id.mapRootContainer)
        movementEngine.cancel()
        transitionRunning = true
        val overlay = CaveTransitionView(this, exiting, descent).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            elevation = 200f
        }
        overlay.onCovered = onCovered
        overlay.onFinished = {
            overlay.animate().alpha(0f).setDuration(if (exiting) 420 else 520).withEndAction {
                root.removeView(overlay)
                transitionRunning = false
            }.start()
        }
        root.addView(overlay)
        overlay.post { overlay.start() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KRYSTALY, STRÁŽCI A SVATYNĚ NA VRCHOLU (docs/adr/0014)
    // ─────────────────────────────────────────────────────────────────────────

    private val db by lazy { AppDatabase.getDatabase(this) }

    /** Postup příběhu krystalů (příznaky v GamePrefs + krystaly v inventáři). */
    private suspend fun loadLegend(): LegendProgress = kotlinx.coroutines.withContext(Dispatchers.IO) {
        LegendProgress.load({ gamePrefs.getBoolean(it, false) }, { db.userItemDao().getItemCount(it) ?: 0 })
    }

    /** Dekorace mapy podle stavu příběhu – po změně biomu i po návratu ze souboje. */
    private fun refreshStoryDecor() {
        lifecycleScope.launch {
            val progress = loadLegend()
            clearDecor()
            val cave = BiomeRegistry.definition(currentBiome)?.cave
            when {
                currentBiome == BiomeType.SKY_PASS -> placeSkyPassDecor()
                currentBiome == BiomeType.FOREST -> { placeGatherSpots(); placeForestStory(progress.legendFaced) }
                currentBiome == BiomeType.HIDDEN_GROVE -> placeGroveDecor()
                currentBiome == BiomeType.MINES -> { placeMinesDecor(); placeEncounterGlows(cave!!); placeGatherSpots(); placeVendelin() }
                cave != null -> {
                    placeCrystal(cave, progress); if (cave.isCave) placeEncounterGlows(cave); placeGatherSpots()
                    if (currentBiome == BiomeType.CAVE_MAZE) placeMineDoorGlow()
                }
                currentBiome == BiomeType.MOUNTAINS -> { placeShrineDecor(progress); placeGatherSpots() }
                currentBiome == BiomeType.MEADOW -> { placeMeadowWorkshop(); placeGatherSpots() }
                else -> {}
            }
            placePageSparkles()
        }
    }

    /**
     * Roztržený list, který tu čeká, je vidět: kousek papíru s třpytkami a občasným zábleskem
     * (docs/adr/0048). U místa s oblastí klepnutí sedí na objektu, jinak kousek nad uzlem.
     */
    private fun placePageSparkles() {
        if (mapWorld.width <= 0) return
        val flags = StoryFlags.all(this)
        val def = BiomeRegistry.definition(currentBiome) ?: return
        val d = resources.displayMetrics.density
        Insight.PAGES.filter { Insight.pageAt(currentBiome.name, it.node, flags) == it }.forEachIndexed { i, page ->
            val cave = def.cave
            val (cx, cy) = cave?.tapAreas?.get(page.node)?.let { (x, y, _) ->
                x * mapWorld.width.toFloat() / cave.artW to y * mapWorld.height.toFloat() / cave.artH
            } ?: BiomeRegistry.nodePos(def.graph, page.node)?.let { it.x * mapWorld.width to it.y * mapWorld.height - 18 * d }
            ?: return@forEachIndexed
            val paperSize = (14 * d).toInt()
            val glow = glowView((46 * d).toInt(), 0xFFFFF2B0.toInt(), 0xAA).apply {
                x = cx - 23 * d; y = cy - 23 * d; elevation = 2.5f
            }
            val paper = pixelView(Insight.pageIcon(), Insight.ICON, Insight.ICON, paperSize, paperSize).apply {
                x = cx - paperSize / 2f; y = cy - paperSize / 2f; elevation = 2.6f; rotation = -12f
            }
            addDecor(glow); addDecor(paper)
            bob(paper, 3 * d, 2400)
            crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.25f, 0.9f, 0.25f).apply {
                duration = 1800; repeatCount = android.animation.ValueAnimator.INFINITE; startDelay = i * 300L; start()
            }
            // třpytky: malé hvězdičky, které se střídavě rozsvěcují kolem papíru
            repeat(4) { k ->
                val ang = k * 1.57f + 0.4f
                val star = pixelView(intArrayOf(0, 0xFFFFFFFF.toInt(), 0, 0xFFFFFFFF.toInt(), 0xFFFFF6C0.toInt(), 0xFFFFFFFF.toInt(),
                    0, 0xFFFFFFFF.toInt(), 0), 3, 3, (7 * d).toInt(), (7 * d).toInt()).apply {
                    x = cx + kotlin.math.cos(ang) * 14 * d - 3.5f * d; y = cy + kotlin.math.sin(ang) * 11 * d - 3.5f * d
                    elevation = 2.7f; alpha = 0f
                }
                addDecor(star)
                crystalAnimators += android.animation.ObjectAnimator.ofFloat(star, "alpha", 0f, 1f, 0f, 0f).apply {
                    duration = 1600; repeatCount = android.animation.ValueAnimator.INFINITE; startDelay = k * 400L; start()
                }
            }
        }
    }

    /**
     * Živý Nebeský průsmyk (docs/adr/0044): třpytivý závoj v Bráně světů, pulzující lůžko
     * pro Srdce Hvozdu a obláčky plující přes moře mraků nad údolím.
     */
    private fun placeSkyPassDecor() {
        if (worldScale <= 0) return
        val s = worldScale.toFloat()
        val A = cz.uhk.macroflow.pokemon.cave.SkyPassArt
        val open = StoryFlags.isSet(this, SkyPass.HEART_PLACED_KEY)

        // závoj v prstenci – smyčka snímků
        val frames = (0 until A.FRAMES).map {
            android.graphics.Bitmap.createBitmap(A.veil(it, open), A.VEIL_SIZE, A.VEIL_SIZE, android.graphics.Bitmap.Config.ARGB_8888)
        }
        val veilSize = (A.VEIL_SIZE * s).toInt()
        val veil = pixelView(A.veil(0, open), A.VEIL_SIZE, A.VEIL_SIZE, veilSize, veilSize).apply {
            x = (A.GATE_X - A.VEIL_R) * s; y = (A.GATE_Y - A.VEIL_R) * s; elevation = 1.5f
        }
        val runeGlow = glowView((60 * s).toInt(), if (open) 0xFF7CFF9A.toInt() else 0xFF6AE8D8.toInt(), if (open) 0x77 else 0x55).apply {
            x = (A.GATE_X - 30) * s; y = (A.GATE_Y - 30) * s; elevation = 1.4f
        }
        val socketGlow = glowView((12 * s).toInt(), if (open) 0xFF7CFF9A.toInt() else 0xFF4ECB6A.toInt(), 0xCC).apply {
            x = (A.SOCKET_X - 6) * s; y = (A.SOCKET_Y - 6) * s; elevation = 1.6f
        }
        // obláčky v moři mraků (různá velikost, rychlost a výška)
        data class Cloud(val view: ImageView, val w: Int, val speed: Double, val start: Double)
        val band = A.CLOUD_BAND
        val clouds = listOf(Triple(22, 7, 3.0), Triple(16, 6, 4.5), Triple(28, 8, 2.2)).mapIndexed { i, (w, h, speed) ->
            val v = pixelView(A.cloud(w, h, i + 1), w, h, (w * s).toInt(), (h * s).toInt()).apply {
                y = (band.first + (band.last - band.first - h) * i / 2f) * s; elevation = 1.2f; alpha = 0.9f
            }
            Cloud(v, w, speed, 55.0 * i)
        }
        addDecor(runeGlow); clouds.forEach { addDecor(it.view) }; addDecor(veil); addDecor(socketGlow)
        if (open) addDecor(pixelView(A.HEART_IN_SOCKET, 5, 5, (5 * s).toInt(), (5 * s).toInt()).apply {
            x = (A.SOCKET_X - 2) * s; y = (A.SOCKET_Y - 2) * s; elevation = 1.7f
        })

        val artW = SkyPass.MAP.artW
        val t0 = android.os.SystemClock.uptimeMillis()
        crystalAnimators += android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                val t = android.os.SystemClock.uptimeMillis() - t0
                (veil.drawable as? android.graphics.drawable.BitmapDrawable)?.let { d ->
                    val f = frames[((t / 110) % A.FRAMES).toInt()]
                    if (d.bitmap !== f) veil.setImageDrawable(android.graphics.drawable.BitmapDrawable(resources, f).apply { isFilterBitmap = false })
                }
                val p = A.pulse(t).toFloat()
                socketGlow.alpha = 0.25f + 0.75f * p
                runeGlow.alpha = 0.45f + 0.35f * A.pulse(t + 700, 3400).toFloat()
                clouds.forEach { c -> c.view.x = kotlin.math.floor(A.cloudX(t, c.speed, c.start, artW, c.w)).toFloat() * s }
            }
            start()
        }
    }

    private fun pixelView(pixels: IntArray, w: Int, h: Int, viewW: Int, viewH: Int): ImageView {
        val bmp = android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888)
        return ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(viewW, viewH)
            setImageDrawable(android.graphics.drawable.BitmapDrawable(resources, bmp).apply { isFilterBitmap = false })
            scaleType = ImageView.ScaleType.FIT_XY
        }
    }

    private fun glowView(size: Int, color: Int, alphaHex: Int = 0x99): View = View(this).apply {
        layoutParams = FrameLayout.LayoutParams(size, size)
        background = android.graphics.drawable.GradientDrawable().apply {
            gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
            gradientRadius = size / 2f
            colors = intArrayOf((color and 0x00FFFFFF) or (alphaHex shl 24), Color.TRANSPARENT)
        }
    }

    private fun addDecor(v: View) { mapWorld.addView(v); decorViews += v }

    /** Krystal lehce nad oltářem; dokud stojí strážce, je vidět, ale nejde vzít. */
    private fun placeCrystal(cave: CaveMap, progress: LegendProgress) {
        val color = cave.crystal ?: return          // les žádný krystal nemá
        val altar = progress.altar(color)
        if (altar == LegendProgress.Altar.EMPTY || worldScale <= 0) return
        val scale = worldScale.toFloat()
        val (bx, by) = cave.crystalBase
        val w = (Crystals.W * scale).toInt()
        val h = (Crystals.H * scale).toInt()
        val left = bx * scale - w / 2f
        val top = by * scale - h

        val glowSize = (w * 2.4f).toInt()
        val glow = glowView(glowSize, color.glow).apply {
            x = left + w / 2f - glowSize / 2f
            y = top + h / 2f - glowSize / 2f
            elevation = 1f
        }
        val crystal = pixelView(Crystals.pixels(color), Crystals.W, Crystals.H, w, h).apply {
            x = left; y = top; elevation = 1.5f
        }
        addDecor(glow); addDecor(crystal)
        crystalGlow = glow; crystalView = crystal

        // Vznáší se jen o jeden art pixel (po celých pixelech) a záře pulzuje
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(crystal, "translationY", 0f, -scale, 0f).apply {
            duration = 2200; repeatCount = android.animation.ValueAnimator.INFINITE
            setEvaluator(android.animation.TypeEvaluator<Float> { f, _, _ ->
                -Math.round(kotlin.math.sin(f * Math.PI).toFloat()) * scale
            })
            start()
        }
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.45f, 1f, 0.45f).apply {
            duration = 1800; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }

        if (altar == LegendProgress.Altar.GUARDED) placeGuardian(cave, color, scale)
    }

    /** Strážce stojí na cestě před oltářem a pomalu dýchá. */
    private fun placeGuardian(cave: CaveMap, color: CrystalColor, scale: Float) {
        val sp = SpecialBattle.guardianOf(color)
        val res = resources.getIdentifier(sp.spriteName, "drawable", packageName)
        if (res == 0) return
        val node = cave.node(cave.crystalNode ?: return) ?: return
        val size = (26 * scale).toInt()
        val shadow = View(this).apply {
            layoutParams = FrameLayout.LayoutParams((size * 0.7f).toInt(), (size * 0.18f).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0x66000000)
            }
            x = node.x * scale - size * 0.35f
            y = (node.y - 5) * scale - size * 0.09f
            elevation = 1.6f
        }
        val guardian = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            setImageResource(res)
            scaleType = ImageView.ScaleType.FIT_CENTER
            x = node.x * scale - size / 2f
            y = (node.y - 5) * scale - size
            elevation = 1.8f
            pivotY = size.toFloat()
        }
        addDecor(shadow); addDecor(guardian)
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(guardian, "scaleY", 1f, 1.05f, 1f).apply {
            duration = 1600; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
    }

    /** Místa setkání v jeskyni: kapradiny jsou v mapě, tady jen pomalu dýchající záře nad nimi. */
    private fun placeEncounterGlows(cave: CaveMap) {
        if (worldScale <= 0) return
        val scale = worldScale.toFloat()
        val size = (30 * scale).toInt()
        cave.encounterNodes.forEachIndexed { i, id ->
            val n = cave.node(id) ?: return@forEachIndexed
            // Doly: žhnoucí mech (oranžová záře), jeskyně: svítící kapradiny
            val glow = glowView(size, if (currentBiome == BiomeType.MINES) 0xFFFF8A3A.toInt() else 0xFF8CF0D2.toInt(), 0x66).apply {
                layoutParams = FrameLayout.LayoutParams(size, (size * 0.6f).toInt())
                x = n.x * scale - size / 2f
                y = (n.y - 3) * scale - size * 0.3f
                alpha = 0.3f
                elevation = 1f
            }
            addDecor(glow)
            crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.25f, 0.9f, 0.25f).apply {
                duration = 2600; startDelay = i * 450L
                repeatCount = android.animation.ValueAnimator.INFINITE; start()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DOLY (docs/adr/0049): láva, mušky, stará síťka, vchod ze Starého dolu
    // ─────────────────────────────────────────────────────────────────────────

    /** Tekoucí láva a mušky přes celou mapu + stará síťka na háku (dokud ji hráč nesebere). */
    private fun placeMinesDecor() {
        if (worldScale <= 0) return
        val s = worldScale.toFloat()
        addGroundDecor(cz.uhk.macroflow.pokemon.cave.MinesFxView(this, worldScale).apply {
            layoutParams = FrameLayout.LayoutParams(MinesMap.W * worldScale, MinesMap.H * worldScale)
        })
        if (!StoryFlags.isSet(this, MinesMap.NET_TAKEN_KEY)) {
            val (hx, hy, _) = MinesMap.MAP.tapAreas.getValue(MinesMap.NET_NODE)
            val size = (8 * s).toInt()
            val glow = glowView((18 * s).toInt(), 0xFFFFF2B0.toInt(), 0x77).apply {
                x = hx * s - 9 * s; y = hy * s - 9 * s; elevation = 2.5f
            }
            val net = pixelView(cz.uhk.macroflow.pokemon.skills.GearArt.gearIcon(cz.uhk.macroflow.pokemon.skills.Gear.OLD_NET),
                16, 16, size, size).apply {
                x = hx * s - size / 2f; y = hy * s - size / 2f; elevation = 2.6f; rotation = 18f; pivotY = 0f
            }
            addDecor(glow); addDecor(net)
            // síťka se na háku lehce houpe
            crystalAnimators += android.animation.ObjectAnimator.ofFloat(net, "rotation", 10f, 26f, 10f).apply {
                duration = 2600; repeatCount = android.animation.ValueAnimator.INFINITE; start()
            }
            crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.3f, 0.9f, 0.3f).apply {
                duration = 1800; repeatCount = android.animation.ValueAnimator.INFINITE; start()
            }
        }
    }

    /**
     * Havíř Vendelín u lucerny (docs/adr/0050): postava se záři kahanu, a šichtovní kniha na bedně.
     * Kniha se rozzáří, když na ni čeká podpis.
     */
    private fun placeVendelin() {
        if (worldScale <= 0) return
        val s = worldScale.toFloat()
        val size = (24 * s).toInt()
        val glow = glowView((30 * s).toInt(), 0xFFFFC04A.toInt(), 0x66).apply {
            x = (Vendelin.X - 7) * s - 15 * s; y = (Vendelin.Y - 15) * s - 15 * s; elevation = 1.9f
        }
        val npc = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            setImageResource(R.drawable.npc_vendelin)
            (drawable as? android.graphics.drawable.BitmapDrawable)?.isFilterBitmap = false
            scaleType = ImageView.ScaleType.FIT_XY
            x = Vendelin.X * s - size / 2f; y = Vendelin.Y * s - size; elevation = 2f
            pivotY = size.toFloat()
        }
        addDecor(glow); addDecor(npc)
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(npc, "scaleY", 1f, 1.03f, 1f).apply {
            duration = 2200; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.5f, 1f, 0.6f, 0.9f, 0.5f).apply {
            duration = 1900; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
        // šichtovní kniha na bedně
        val book = pixelView(cz.uhk.macroflow.pokemon.skills.GearArt.ledger(), 12, 12, (8 * s).toInt(), (8 * s).toInt()).apply {
            x = (Vendelin.BOOK_X - 4) * s; y = (Vendelin.BOOK_Y - 5) * s; elevation = 2.1f
        }
        addDecor(book)
        val p = questManager.getCurrentProgress()
        val waiting = questManager.getActiveQuestId() == Vendelin.QUEST_ID && p != null && !p.isCompleted &&
            p.currentStageIndex == Vendelin.SIGN_STAGE && !StoryFlags.isSet(this, Vendelin.BOOK_SIGNED_KEY)
        if (waiting) {
            val bg = glowView((20 * s).toInt(), 0xFFFFF2B0.toInt(), 0x88).apply {
                x = Vendelin.BOOK_X * s - 10 * s; y = (Vendelin.BOOK_Y - 1) * s - 10 * s; elevation = 2.05f
            }
            addDecor(bg)
            crystalAnimators += android.animation.ObjectAnimator.ofFloat(bg, "alpha", 0.3f, 1f, 0.3f).apply {
                duration = 1500; repeatCount = android.animation.ValueAnimator.INFINITE; start()
            }
        }
    }

    /** Šichtovní kniha: ve fázi podpisu se hráč podepíše, jinak je zavřená / jen se čte. */
    private fun onMinerBook() {
        val signed = StoryFlags.isSet(this, Vendelin.BOOK_SIGNED_KEY)
        val p = questManager.getCurrentProgress()
        val canSign = !signed && questManager.getActiveQuestId() == Vendelin.QUEST_ID && p != null && !p.isCompleted &&
            p.currentStageIndex == Vendelin.SIGN_STAGE
        if (!signed && !canSign) { showMapToast("📕 " + Vendelin.BOOK_CLOSED); return }
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Šichtovní kniha",
            if (signed) "Tvůj podpis je pořád na posledním řádku." else "Poslední řádek je volný.") { ui, body, close ->
            body.addView(ui.text(Vendelin.bookText(signed), 16f).apply {
                typeface = android.graphics.Typeface.MONOSPACE
                setBackgroundColor(0x22BC6C25)
                setPadding(ui.px(10f), ui.px(10f), ui.px(10f), ui.px(10f))
            })
            body.addView(ui.button(if (signed) "Zavřít" else "Podepsat se") {
                close()
                if (!signed) {
                    StoryFlags.set(this@MakromonMapActivity, Vendelin.BOOK_SIGNED_KEY)
                    mapWorld.postDelayed({
                        if (isFinishing) return@postDelayed
                        onMinerBook()          // kniha s podpisem – rukopis je pořád stejný
                        questManager.recheck()
                        refreshStoryDecor()
                    }, 250)
                }
            }.apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = ui.px(10f) }
            })
        }
    }

    /** Ve Starém dole u zabedněné štoly do Dolů zdola prosvítá rudý žár. */
    private fun placeMineDoorGlow() {
        if (worldScale <= 0) return
        val s = worldScale.toFloat()
        val (x0, y0, _) = MinesMap.MAZE_DOOR
        val glow = glowView((22 * s).toInt(), 0xFFFF6A1E.toInt(), 0x88).apply {
            x = x0 * s - 11 * s; y = (y0 + 3) * s - 11 * s; elevation = 1.2f
        }
        addDecor(glow)
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.35f, 0.95f, 0.5f, 0.85f, 0.35f).apply {
            duration = 2400; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
    }

    /** Stará síťka na háku: poprvé se sebere a nasadí, potom je hák prázdný. */
    private fun onOldNet() {
        if (StoryFlags.isSet(this, MinesMap.NET_TAKEN_KEY)) { showMapToast(MinesMap.NET_GONE_TEXT); return }
        // síťka je Vendelínova – dokud se s ním hráč nepozná, je na háku přivázaná drátem (docs/adr/0050)
        if (!questManager.hasSeenIntro(Vendelin.QUEST_ID, 0)) { showMapToast("🪝 " + MinesMap.NET_TIED_TEXT); return }
        val ctx = applicationContext
        lifecycleScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                cz.uhk.macroflow.pokemon.skills.SkillStore.grantItemOnce(ctx, cz.uhk.macroflow.pokemon.skills.Gear.OLD_NET.id)
            }
            StoryFlags.set(this@MakromonMapActivity, MinesMap.NET_TAKEN_KEY)
            questManager.recheck()           // první fáze Vendelína: síťka je sebraná
            showMapToast("🪰 " + MinesMap.NET_TEXT + "\n\nZískal jsi: Stará síťka (nasazená v deníku → Postava → TOOLS).")
            refreshStoryDecor()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DÍLNA NA LOUCE (docs/adr/0034): pracovní stůl a záhony
    // ─────────────────────────────────────────────────────────────────────────

    private var gardenView: cz.uhk.macroflow.pokemon.skills.ui.GardenView? = null

    /** Dekorace „na zemi“ – pod postavou (hned za pozadím mapy). */
    private fun addGroundDecor(v: View) { mapWorld.addView(v, 1); decorViews += v }

    private fun meadowGeometry() = cz.uhk.macroflow.pokemon.walk.MapGeometry(688, 1536, mapWorld.width, mapWorld.height)

    private fun placeMeadowWorkshop() {
        if (mapWorld.width == 0) return
        val geo = meadowGeometry()
        val ML = cz.uhk.macroflow.pokemon.skills.MeadowLayout
        val SA = cz.uhk.macroflow.pokemon.skills.SkillArt
        // pracovní stůl
        val t = ML.TABLE
        val tl = geo.toWorld(cz.uhk.macroflow.pokemon.walk.Pt(t.x0.toFloat(), t.y0.toFloat()))
        val table = pixelView(SA.craftingTable(), SA.TABLE_W, SA.TABLE_H, (t.w * geo.scale).toInt(), (t.h * geo.scale).toInt()).apply {
            x = tl.x; y = tl.y
        }
        addGroundDecor(table)
        // zahrada
        val garden = cz.uhk.macroflow.pokemon.skills.ui.GardenView(this, geo.scale)
        val g = ML.GARDEN
        val gl = geo.toWorld(cz.uhk.macroflow.pokemon.walk.Pt(g.x0.toFloat(), (g.y0 - cz.uhk.macroflow.pokemon.skills.ui.GardenView.TOP_MARGIN).toFloat()))
        garden.layoutParams = FrameLayout.LayoutParams(garden.viewWidth, garden.viewHeight)
        garden.x = gl.x; garden.y = gl.y
        addGroundDecor(garden)
        gardenView = garden
        refreshGarden()
    }

    private fun refreshGarden() {
        val gv = gardenView ?: return
        val ctx = applicationContext
        lifecycleScope.launch {
            val (plots, state) = kotlinx.coroutines.withContext(Dispatchers.IO) {
                cz.uhk.macroflow.pokemon.skills.SkillStore.plots(ctx) to cz.uhk.macroflow.pokemon.skills.SkillStore.state(ctx)
            }
            gv.plots = plots; gv.plotsOpen = state.plotsOpen; gv.speedup = state.growthSpeedup
        }
    }

    private fun skillLevelText(r: cz.uhk.macroflow.pokemon.skills.SkillStore.XpResult): String =
        if (!r.leveledUp) "" else "\n⭐ ${r.skill.label} Lv ${r.newLevel}!" +
            (if (r.newPoints > 0) " Nový dovednostní bod – utrať ho v deníku (Postava)." else "")

    private fun onPlot(i: Int) {
        val ctx = applicationContext
        val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
        val G = cz.uhk.macroflow.pokemon.skills.Garden
        lifecycleScope.launch {
            val (plots, state, owned) = kotlinx.coroutines.withContext(Dispatchers.IO) {
                Triple(SS.plots(ctx), SS.state(ctx), SS.counts(ctx))
            }
            val now = System.currentTimeMillis() / 1000
            val plot = plots[i]
            when {
                !G.isOpen(i, state.plotsOpen) -> showMapToast("🪵 Zničený záhon. Opravíš ho uzlem „Nové záhony“ ve stromu Pěstování (deník → Postava).")
                plot.isEmpty -> cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.seedMenu(findViewById(R.id.mapRootContainer), i, owned, state) { berry ->
                    lifecycleScope.launch {
                        val ok = kotlinx.coroutines.withContext(Dispatchers.IO) { SS.plant(ctx, i, berry, System.currentTimeMillis() / 1000) }
                        if (ok) showMapToast("🌱 Zasazeno: ${berry.seedLabel}. Sklizeň za ${G.clock(G.growSeconds(berry, state.growthSpeedup))}.")
                        refreshGarden()
                    }
                }
                G.isReady(plot, now, state.growthSpeedup) -> {
                    val res = kotlinx.coroutines.withContext(Dispatchers.IO) { SS.harvest(ctx, i, now) }
                    if (res != null) {
                        val (n, xp) = res
                        val berry = plot.berry!!
                        showMapToast("🧺 Sklizeno: ${n}× ${berry.label}" + (if (n > 1) " (dvojitá sklizeň!)" else "") +
                            "\n+${xp.gained} XP Pěstování" + skillLevelText(xp))
                    }
                    refreshGarden()
                    checkAwards()
                }
                else -> showMapToast("⏳ Roste ${plot.berry!!.label} – zbývá ${G.clock(G.remaining(plot, now, state.growthSpeedup))}.")
            }
        }
    }

    private fun openCraftingTable() {
        val ctx = applicationContext
        val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
        lifecycleScope.launch {
            val (owned, state) = kotlinx.coroutines.withContext(Dispatchers.IO) { SS.counts(ctx) to SS.state(ctx) }
            cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.craftMenu(findViewById(R.id.mapRootContainer), owned, state,
                onCraft = { ball, times ->
                    lifecycleScope.launch {
                        val res = kotlinx.coroutines.withContext(Dispatchers.IO) { SS.craft(ctx, ball, times) }
                        if (res == null) { showMapToast("Chybí suroviny."); return@launch }
                        val (made, xp) = res
                        showMapToast("🔨 Vyrobeno: ${made}× ${ball.label}" + (if (made > times) " (dvojitá výroba!)" else "") +
                            "\n+${xp.gained} XP Výroba" + skillLevelText(xp))
                        checkAwards()
                    }
                },
                onCraftGear = { g -> craftGear(g) })
        }
    }

    /** Výroba kusu dobrodruhova setu (docs/adr/0039). */
    private fun craftGear(g: cz.uhk.macroflow.pokemon.skills.Gear) {
        val ctx = applicationContext
        val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
        lifecycleScope.launch {
            val xp = kotlinx.coroutines.withContext(Dispatchers.IO) { SS.craftGear(ctx, g) }
            if (xp == null) { showMapToast("Tohle teď vyrobit nejde – chybí suroviny."); return@launch }
            showMapToast("🧵 Vyrobeno: ${g.label}! Nasadíš ho v deníku → Postava (do prázdného slotu už je nasazený).\n+${xp.gained} XP Výroba" + skillLevelText(xp))
            checkAwards()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TĚŽBA A KÁCENÍ (docs/adr/0035): stromy, žíly, cedulka a dřevěná tabule
    // ─────────────────────────────────────────────────────────────────────────

    private val gatherPlaques = HashMap<cz.uhk.macroflow.pokemon.skills.GatherSpot, cz.uhk.macroflow.pokemon.skills.ui.GatherPlaqueView>()

    private fun placeGatherSpots() {
        if (mapWorld.width == 0) return
        val (iw, ih) = cz.uhk.macroflow.pokemon.skills.GatherLayout.imageSize(currentBiome.name)
        val geo = cz.uhk.macroflow.pokemon.walk.MapGeometry(iw, ih, mapWorld.width, mapWorld.height)
        cz.uhk.macroflow.pokemon.skills.GatherSpot.entries.filter { it.biome == currentBiome.name }.forEach { spot ->
            val place = cz.uhk.macroflow.pokemon.skills.GatherLayout.PLACES[spot] ?: return@forEach
            val art = cz.uhk.macroflow.pokemon.skills.GearArt.spot(spot)
            val base = geo.toWorld(cz.uhk.macroflow.pokemon.walk.Pt(place.baseX.toFloat(), place.baseY.toFloat()))
            // mušky (Chytání) nemají obrázek – poletují v MinesFxView; cedulka visí nad hejnem
            val vh = if (art == null) (10 * geo.scale).toInt() else (art.third * place.artScale * geo.scale).toInt()
            if (art != null) {
                val (px, w, h) = art
                val vw = (w * place.artScale * geo.scale).toInt()
                addGroundDecor(pixelView(px, w, h, vw, vh).apply { x = base.x - vw / 2f; y = base.y - vh })
            }
            // cedulka nad místem (vidět jen když se tu zrovna těží)
            val plaque = cz.uhk.macroflow.pokemon.skills.ui.GatherPlaqueView(this, place.artScale * geo.scale * 0.55f).apply {
                layoutParams = FrameLayout.LayoutParams(wantedWidth, wantedHeight)
                x = base.x - wantedWidth / 2f; y = base.y - vh - wantedHeight - 6
                visibility = View.GONE
                elevation = 6f
                icon = cz.uhk.macroflow.pokemon.skills.SkillArt.resourceIcon(spot.resource)
            }
            addDecor(plaque)
            gatherPlaques[spot] = plaque
        }
        refreshGatherPlaque()
    }

    private fun refreshGatherPlaque() {
        val ctx = applicationContext
        lifecycleScope.launch {
            val info = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
                val a = SS.activity(ctx) ?: return@withContext null
                val st = SS.state(ctx)
                Triple(a, cz.uhk.macroflow.pokemon.skills.Gathering.secondsPerUnit(a.spot, SS.efficiency(ctx, a.spot, st)), st.afkCapHours(a.spot.skill))
            }
            gatherPlaques.forEach { (spot, v) ->
                val on = info != null && info.first.spot == spot && info.second != null
                v.visibility = if (on) View.VISIBLE else View.GONE
                if (on) { v.secPerUnit = info!!.second!!; v.capHours = info.third; v.activity = info.first }
            }
            // stojí u místa, kde těží → čelem k němu
            if (info != null && info.first.spot.biome == currentBiome.name) {
                val node = BiomeRegistry.definition(currentBiome)?.graph?.find { it.id == info.first.spot.node }
                val pos = movementEngine.getCurrentPosition()
                if (node != null && kotlin.math.hypot(node.pos.x - pos.x, node.pos.y - pos.y) < 0.05f) movementEngine.face(1)
            }
        }
    }

    private fun openGatherSpot(spot: cz.uhk.macroflow.pokemon.skills.GatherSpot) {
        val ctx = applicationContext
        val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
        lifecycleScope.launch {
            val info = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val st = SS.state(ctx)
                val eff = SS.efficiency(ctx, spot, st)
                val sec = cz.uhk.macroflow.pokemon.skills.Gathering.secondsPerUnit(spot, eff)
                val active = SS.activity(ctx)
                val pending = if (active?.spot == spot && sec != null)
                    cz.uhk.macroflow.pokemon.skills.Gathering.pending(active, System.currentTimeMillis() / 1000, sec, st.afkCapHours(spot.skill)) else 0
                cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.GatherInfo(
                    spot, SS.equipped(ctx, spot.toolSlot), eff, sec, st.gain(spot.skill, spot.xp.toDouble()),
                    st.multiChance(spot.skill), st.afkCapHours(spot.skill), active, pending)
            }
            movementEngine.face(1)
            cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.gatherMenu(findViewById(R.id.mapRootContainer), info,
                onStart = {
                    lifecycleScope.launch {
                        val prev = kotlinx.coroutines.withContext(Dispatchers.IO) {
                            val now = System.currentTimeMillis() / 1000
                            val r = SS.claimActivity(ctx, now)
                            SS.startActivity(ctx, spot, now)
                            r
                        }
                        val got = prev?.takeIf { it.claim.amount > 0 }?.let { "\nZ ${it.spot.label}: ${it.claim.amount}× ${it.spot.resource.label}." } ?: ""
                        showMapToast("${spot.emoji} ${spot.doing.replaceFirstChar { it.uppercase() }}: ${spot.label}. Běží dál, i když Makrosvět zavřeš.$got")
                        refreshGatherPlaque()
                    }
                },
                onCollect = { collectGathering(stop = false) },
                onStop = { collectGathering(stop = true) })
        }
    }

    private fun collectGathering(stop: Boolean) {
        val ctx = applicationContext
        val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
        lifecycleScope.launch {
            val r = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val res = SS.claimActivity(ctx, System.currentTimeMillis() / 1000)
                if (stop) SS.stopActivity(ctx)
                res
            }
            val msg = when {
                r == null -> null
                r.claim.amount > 0 -> "Vybráno: ${r.claim.amount}× ${r.spot.resource.label}" +
                    (if (r.claim.amount > r.claim.units) " (dvojité kusy!)" else "") +
                    (r.xp?.let { "\n+${it.gained} XP ${it.skill.label}" + skillLevelText(it) } ?: "")
                else -> null
            }
            showMapToast((msg ?: "Zatím nic hotového.") + if (stop) "\nPřestal jsi." else "")
            refreshGatherPlaque()
            questManager.recheck()
            checkAwards()
        }
    }

    /**
     * Odchod od stromu / žíly ukončí těžbu (postava tam musí stát, jako v IdleOn) –
     * hotové kusy se vyberou. Zavření Makrosvěta ji NEukončí, to je AFK.
     */
    private fun leaveGatherSpot(targetNode: String?) {
        val ctx = applicationContext
        lifecycleScope.launch {
            val r = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
                val a = SS.activity(ctx) ?: return@withContext null
                if (a.spot.node == targetNode) return@withContext null
                val res = SS.claimActivity(ctx, System.currentTimeMillis() / 1000)
                SS.stopActivity(ctx)
                res
            } ?: return@launch
            showMapToast("Odešel jsi od: ${r.spot.label}." + if (r.claim.amount > 0) "\nVybráno ${r.claim.amount}× ${r.spot.resource.label}" +
                (r.xp?.let { ", +${it.gained} XP ${it.skill.label}" } ?: "") else "")
            refreshGatherPlaque()
            questManager.recheck()
        }
    }

    private fun clearDecor() {
        gatherPlaques.clear()
        gardenView = null
        crystalAnimators.forEach { it.cancel() }
        crystalAnimators.clear()
        decorViews.forEach { mapWorld.removeView(it) }
        decorViews.clear()
        crystalView = null; crystalGlow = null
    }

    /** Uzel krystalu: strážce → souboj; volný krystal → do inventáře; prázdný oltář → hláška. */
    private fun onCrystalNode(cave: CaveMap) {
        lifecycleScope.launch {
            val progress = loadLegend()
            val color = cave.crystal ?: return@launch
            when (progress.altar(color)) {
                LegendProgress.Altar.GUARDED -> {
                    val sp = SpecialBattle.guardianOf(color)
                    startSpecialBattle(sp, battleBiome = currentBiome)
                }
                LegendProgress.Altar.CRYSTAL_READY -> takeCrystal(color)
                LegendProgress.Altar.EMPTY -> showMapToast("Oltář je prázdný – ${color.label} už je u tebe.")
            }
        }
    }

    // ── Ladění příběhu (jen debug build) ──

    private fun showDebugMenu() {
        val boots = gamePrefs.getBoolean(DEBUG_BOOTS_KEY, false)
        val items = arrayOf(
            if (boots) "👢 Sedmimílové boty: ZAPNUTO (sundat)" else "👢 Obout sedmimílové boty (hory bez kroků)",
            "✦ Příští setkání bude shiny",
            "💎 Reset krystalů, strážců a legendy",
            "⚔ Porazit oba strážce",
            "🎒 Dát oba krystaly do inventáře",
            "🐉 Legenda odletěla nad Hvozd (odemkne Mydruse)",
            "🌳 Splnit aktuální fázi questu",
            "🍃 Dát Srdce Hvozdu do inventáře",
            "🍂 Reset příběhu Hvozdu",
            "🍄 Soulord poražen (otevře háj)",
            "🌕 Bdění u oltáře splněno (noc)",
            "🗝 Reset Zapomenutého háje"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("Debug – Makrosvět")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        gamePrefs.edit().putBoolean(DEBUG_BOOTS_KEY, !boots).apply()
                        showMapToast(if (boots) "👢 Boty sundány – do hor zase jen po svých."
                            else "👢 Sedmimílové boty obuty! Do hor se dostaneš i bez dnešních kroků.")
                    }
                    1 -> {
                        gamePrefs.edit().putBoolean("DEBUG_FORCE_SHINY", true).apply()
                        showMapToast("✦ Debug: příští setkání bude shiny")
                    }
                    2 -> debugResetLegend()
                    3 -> {
                        CrystalColor.entries.forEach { StoryFlags.set(this@MakromonMapActivity, LegendProgress.bossKey(it)) }
                        showMapToast("⚔ Debug: strážci poraženi – krystaly jdou vzít")
                        refreshStoryDecor()
                    }
                    4 -> lifecycleScope.launch {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            CrystalColor.entries.forEach { c ->
                                if ((db.userItemDao().getItemCount(c.itemId) ?: 0) == 0) db.userItemDao().addItem(c.itemId, 1)
                            }
                        }
                        CrystalColor.entries.forEach {
                            StoryFlags.set(this@MakromonMapActivity, LegendProgress.bossKey(it))
                            StoryFlags.set(this@MakromonMapActivity, LegendProgress.takenKey(it))
                        }
                        showMapToast("🎒 Debug: oba krystaly jsou v inventáři")
                        refreshStoryDecor()
                    }
                    5 -> {
                        CrystalColor.entries.forEach {
                            StoryFlags.set(this@MakromonMapActivity, LegendProgress.bossKey(it))
                            StoryFlags.set(this@MakromonMapActivity, LegendProgress.takenKey(it))
                        }
                        StoryFlags.set(this@MakromonMapActivity, LegendProgress.PLACED_KEY)
                        StoryFlags.set(this@MakromonMapActivity, LegendProgress.LEGEND_KEY)
                        showMapToast("🐉 Debug: Drakirra odletěla nad Hvozd – Mydrus čeká na mýtině")
                        if (currentBiome == BiomeType.FOREST) questManager.loadQuest(ForestHeart.QUEST_ID)
                        refreshStoryDecor()
                    }
                    6 -> { questManager.debugCompleteStage(); showMapToast("🌳 Debug: fáze splněna") }
                    7 -> lifecycleScope.launch {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            cz.uhk.macroflow.pokemon.skills.SkillStore.grantItemOnce(applicationContext, ForestHeart.ITEM_ID)
                        }
                        showMapToast("🍃 Debug: Srdce Hvozdu je v inventáři")
                    }
                    8 -> debugResetForest()
                    9 -> { StoryFlags.set(this@MakromonMapActivity, ForestHeart.ROT_DEFEATED_KEY); showMapToast("🍄 Debug: kletba padla, v trní svítí houby"); refreshStoryDecor() }
                    10 -> {
                        StoryFlags.set(this@MakromonMapActivity, SecretGrove.VIGIL_KEY)
                        questManager.recheck(); refreshStoryDecor()
                        showMapToast("🌕 Debug: bdění splněno")
                    }
                    11 -> debugResetGrove()
                }
            }
            .show()
    }

    /** Tajný háj od začátku (quest, příznaky, deník). */
    private fun debugResetGrove() {
        val ctx = applicationContext
        gamePrefs.edit().apply { SecretGrove.KEYS.forEach { remove(it) } }.apply()
        questManager.forget(SecretGrove.QUEST_ID)
        lifecycleScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                SecretGrove.KEYS.forEach { StoryFlags.clear(ctx, it) }
                val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
                SS.count(ctx, SecretGrove.DIARY_ID).takeIf { it > 0 }?.let { SS.consume(ctx, SecretGrove.DIARY_ID, it) }
                db.questDao().deleteById(SecretGrove.QUEST_ID)
            }
            if (currentBiome == BiomeType.HIDDEN_GROVE) questManager.loadQuest(SecretGrove.QUEST_ID)
            showMapToast("🗝 Debug: Zapomenutý háj je zase zapomenutý")
            refreshStoryDecor()
        }
    }

    /** Hvozd od začátku: quest, hniloba, Srdce (i vložené do brány). */
    private fun debugResetForest() {
        val ctx = applicationContext
        gamePrefs.edit().remove(ForestHeart.ROT_DEFEATED_KEY).remove(SkyPass.HEART_PLACED_KEY).apply()
        questManager.forget(ForestHeart.QUEST_ID)
        lifecycleScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                StoryFlags.clear(ctx, ForestHeart.ROT_DEFEATED_KEY)
                StoryFlags.clear(ctx, SkyPass.HEART_PLACED_KEY)
                val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
                SS.count(ctx, ForestHeart.ITEM_ID).takeIf { it > 0 }?.let { SS.consume(ctx, ForestHeart.ITEM_ID, it) }
                db.questDao().deleteById(ForestHeart.QUEST_ID)
            }
            if (currentBiome == BiomeType.FOREST && StoryFlags.isSet(ctx, LegendProgress.LEGEND_KEY)) questManager.loadQuest(ForestHeart.QUEST_ID)
            showMapToast("🍂 Debug: Hvozd je zase nemocný a quest začíná od začátku")
            refreshStoryDecor()
        }
    }

    /** Vrátí celý příběh krystalů na začátek: strážci zpět, krystaly na oltářích, svatyně prázdná. */
    private fun debugResetLegend() {
        val keys = CrystalColor.entries.flatMap { listOf(LegendProgress.bossKey(it), LegendProgress.takenKey(it)) } +
            listOf(LegendProgress.PLACED_KEY, LegendProgress.LEGEND_KEY,
                SkyPass.VISITED_KEY, SkyPass.GATE_SEEN_KEY, SkyPass.HEART_PLACED_KEY)
        // GamePrefs hned (decor se čte z nich), předměty story_* na pozadí
        gamePrefs.edit().apply { keys.forEach { remove(it) } }.apply()
        lifecycleScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                keys.forEach { StoryFlags.clear(applicationContext, it) }
                CrystalColor.entries.forEach { c ->
                    db.userItemDao().insertOrUpdateItem(UserItemEntity(c.itemId, 0))
                    if (cz.uhk.macroflow.data.FirebaseRepository.isLoggedIn) {
                        runCatching { cz.uhk.macroflow.data.FirebaseRepository.uploadUserItem(UserItemEntity(c.itemId, 0)) }
                    }
                }
            }
            showMapToast("💎 Debug: krystaly, strážci i legenda jsou zpět na začátku")
            refreshStoryDecor()
        }
    }

    /** Hvozd: vstup až po [ForestMap.REQUIRED_TASKS] splněných fázích úkolů (celkem ze všech questů). */
    private fun tryEnterForest() {
        lifecycleScope.launch {
            val done = kotlinx.coroutines.withContext(Dispatchers.IO) {
                cz.uhk.macroflow.pokemon.cave.ForestMap.completedTasks(db.questDao().getAllQuests().map { p ->
                    val total = cz.uhk.macroflow.pokemon.quests.QuestRegistry.byId(p.questId)?.stages?.size ?: 0
                    Triple(total, p.currentStageIndex, p.isCompleted)
                })
            }
            if (cz.uhk.macroflow.pokemon.cave.ForestMap.canEnter(done)) {
                val entry = cz.uhk.macroflow.pokemon.cave.ForestMap.MAP.exitNode
                enterBiomeAtNode(BiomeType.FOREST, entry, MapTransition.FADE)
            } else {
                val need = cz.uhk.macroflow.pokemon.cave.ForestMap.REQUIRED_TASKS
                showMapToast("🌲 Hvozd je hustý a cesta se v něm snadno ztratí.\n" +
                    "Pustí tě dál, až splníš $need úkolů (máš $done z $need). Mrkni do deníku!")
            }
        }
    }

    private fun startSpecialBattle(sp: SpecialBattle, battleBiome: BiomeType) {
        gamePrefs.edit()
            .putString("LAST_BIOME", battleBiome.name)
            .putString(SpecialBattle.PREF, sp.id)
            .remove("FORCE_ENCOUNTER_ID")
            .apply()
        replaceMapContent(PokemonBattleFragment())
    }

    private fun takeCrystal(color: CrystalColor) {
        StoryFlags.set(this, LegendProgress.takenKey(color))
        lifecycleScope.launch(Dispatchers.IO) {
            db.userItemDao().addItem(color.itemId, 1)
            if (cz.uhk.macroflow.data.FirebaseRepository.isLoggedIn) {
                db.userItemDao().getItem(color.itemId)?.let { runCatching { cz.uhk.macroflow.data.FirebaseRepository.uploadUserItem(it) } }
            }
        }
        val crystal = crystalView
        val glow = crystalGlow
        crystalAnimators.filter { (it as? android.animation.ObjectAnimator)?.target.let { t -> t === crystal || t === glow } }
            .forEach { it.cancel(); crystalAnimators.remove(it) }
        // Krystal vyletí k hráči a zmizí v batohu
        crystal?.animate()?.x(ashView.x + ashView.width / 2f - crystal.width / 2f)?.y(ashView.y)
            ?.scaleX(0.3f)?.scaleY(0.3f)?.alpha(0f)?.setDuration(900)
            ?.withEndAction { crystal.visibility = View.GONE; glow?.visibility = View.GONE }?.start()
        glow?.animate()?.scaleX(2.5f)?.scaleY(2.5f)?.alpha(0f)?.setDuration(700)?.start()
        showMapToast("💎 Získal jsi ${color.label}!\nNajdeš ho v inventáři. Patří do svatyně na vrcholu Hor.")
    }

    // ── Svatyně na vrcholu Hor ──

    /** Mapa hor je přes celou obrazovku: art pixel → pixel světa zvlášť pro x a y (jako uzly grafu). */
    private fun peakX(artX: Float) = artX / PeakShrine.ART_W * mapWorld.width
    private fun peakY(artY: Float) = artY / PeakShrine.ART_H * mapWorld.height

    private fun placeShrineDecor(progress: LegendProgress) {
        if (mapWorld.width == 0) return
        progress.socketsFilled.forEach { c -> addSocketCrystal(c, animateIn = false) }
        if (progress.shrine == LegendProgress.Shrine.GateOpen) addOpenGate()
    }

    private fun addSocketCrystal(c: CrystalColor, animateIn: Boolean): View {
        val sx = PeakShrine.SOCKETS.getValue(c).toFloat()
        val w = (peakX(sx + 1.5f) - peakX(sx - 1.5f)).toInt()
        val h = (peakY(PeakShrine.SOCKET_BOTTOM.toFloat()) - peakY(PeakShrine.SOCKET_BOTTOM - 6f)).toInt()
        val glowSize = w * 4
        val glow = glowView(glowSize, c.glow).apply {
            x = peakX(sx) - glowSize / 2f
            y = peakY(PeakShrine.SOCKET_BOTTOM - 3f) - glowSize / 2f
            elevation = 3f
        }
        val v = pixelView(Crystals.smallPixels(c), Crystals.SMALL_W, Crystals.SMALL_H, w, h).apply {
            x = peakX(sx - 1.5f); y = peakY(PeakShrine.SOCKET_BOTTOM - 6f); elevation = 3.5f
        }
        addDecor(glow); addDecor(v)
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.5f, 1f, 0.5f).apply {
            duration = 1500; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
        if (animateIn) { v.alpha = 0f; glow.scaleX = 0.2f; glow.scaleY = 0.2f
            v.animate().alpha(1f).setDuration(250).start()
            glow.animate().scaleX(1f).scaleY(1f).setDuration(400).start() }
        return v
    }

    /** Otevřená brána ve skále za svatyní (vede do další lokace – zatím zavřené dál). */
    private fun addOpenGate() {
        val gw = PeakShrine.GATE_RIGHT - PeakShrine.GATE_LEFT + 1
        val gh = PeakShrine.GATE_BOTTOM - PeakShrine.GATE_TOP + 1
        val left = peakX(PeakShrine.GATE_LEFT.toFloat()); val top = peakY(PeakShrine.GATE_TOP.toFloat())
        val w = (peakX(PeakShrine.GATE_RIGHT + 1f) - left).toInt()
        val h = (peakY(PeakShrine.GATE_BOTTOM + 1f) - top).toInt()
        val glow = glowView((w * 2.2f).toInt(), 0xFFB488FF.toInt(), 0x77).apply {
            x = left + w / 2f - w * 1.1f; y = top + h / 2f - w * 1.1f; elevation = 2f
        }
        val gate = pixelView(PeakShrine.openGatePixels(), gw, gh, w, h).apply { x = left; y = top; elevation = 2.2f }
        addDecor(glow); addDecor(gate)
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.35f, 0.85f, 0.35f).apply {
            duration = 2400; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HVOZD: druid Mydrus, Rudá hniloba a Srdce Hvozdu (docs/adr/0045)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Pomalé pohupování nahoru a dolů. Pozice dekorace je uložená v translationY (View.y),
     * takže se animuje kolem ní – animace od 0 by postavu přesunula k hornímu okraji mapy.
     */
    private fun bob(v: View, amplitude: Float, periodMs: Long) {
        val base = v.translationY
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(v, "translationY", base, base - amplitude, base).apply {
            duration = periodMs; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
    }

    /** Poslední známá fáze questu Dolů (docs/adr/0050). */
    private var lastMinesStage: Pair<Int, Boolean>? = null

    /** Poslední známá fáze questu Hvozdu (index, dokončeno) – dekorace se obnoví jen při změně. */
    private var lastForestStage: Pair<Int, Boolean>? = null

    /** Je aktivní quest Hvozdu ve fázi, kdy v kořenech Starého dubu čeká Soulord? */
    private fun rotBossWaiting(): Boolean =
        currentBiome == BiomeType.FOREST && questManager.getActiveQuestId() == ForestHeart.QUEST_ID &&
            questManager.getCurrentProgress()?.let { !it.isCompleted && it.currentStageIndex == ForestHeart.BOSS_STAGE } == true &&
            !StoryFlags.isSet(this, ForestHeart.ROT_DEFEATED_KEY)

    /** Mýtina: dokud legenda spí, jen šepot lesa; potom tu čeká Mydrus s questem. */
    private fun onMytina() {
        if (!ForestHeart.questAvailable(StoryFlags.isSet(this, LegendProgress.LEGEND_KEY))) {
            showMapToast(ForestHeart.MYTINA_BEFORE); return
        }
        // Po vysvobození Elderana se Mydrus jednou dozví pravdu o svém učiteli
        if (StoryFlags.isSet(this, SecretGrove.RELEASED_KEY) && !StoryFlags.isSet(this, SecretGrove.MYDRUS_TOLD_KEY)) {
            StoryFlags.set(this, SecretGrove.MYDRUS_TOLD_KEY)
            questDialogManager.showQuestDialog(
                speakerResource = R.drawable.makromon_23_mydrus, speakerName = "Mydrus", stageName = "Mistr Elderan",
                text = SecretGrove.MYDRUS_CLOSURE, totalSteps = 1, currentStepIndex = 0)
            return
        }
        if (questManager.getActiveQuestId() != ForestHeart.QUEST_ID) {
            // quest se právě odemkl (legenda porazila hráče, když byl Hvozd už načtený)
            questManager.loadQuest(ForestHeart.QUEST_ID)
            mapWorld.postDelayed({ if (!isFinishing) questManager.checkNpcInteraction() }, 300)
            return
        }
        questManager.checkNpcInteraction()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VHLED A ROZTRŽENÉ LISTY (docs/adr/0047)
    // ─────────────────────────────────────────────────────────────────────────

    private fun findTornPage(page: Insight.Page) {
        StoryFlags.set(this, page.key)
        refreshStoryDecor()     // třpytky zmizí
        val ctx = applicationContext
        lifecycleScope.launch(Dispatchers.IO) { cz.uhk.macroflow.pokemon.skills.SkillStore.add(ctx, Insight.PAGES_ITEM, 1) }
        showTornPage(page, page.found)
    }

    /** List na papírové tabuli; začerněná místa podle aktuálního Vhledu. */
    private fun showTornPage(page: Insight.Page, subtitle: String) {
        val insight = StoryFlags.insight(this)
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Roztržený list", subtitle) { ui, body, close ->
            body.addView(ui.text(Insight.render(page.text, insight), 16f).apply {
                typeface = android.graphics.Typeface.MONOSPACE
                setBackgroundColor(0x22BC6C25)
                setPadding(ui.px(10f), ui.px(10f), ui.px(10f), ui.px(10f))
            })
            body.addView(ui.text("Uloženo v batohu (Roztržené listy).", 14f, ui.inkSoft).apply { setPadding(0, ui.px(8f), 0, 0) })
            body.addView(ui.button("Schovat") { close() }.apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = ui.px(10f) }
            })
        }
    }

    /** Město na zlomek vteřiny problikne jako Stanice 7 (jen při vyšším Vhledu a vzácně). */
    private fun maybeStationFlicker() {
        if (!Insight.stationFlicker(StoryFlags.insight(this), kotlin.random.Random.nextInt(600))) return
        val root = findViewById<FrameLayout>(R.id.mapRootContainer)
        val stamp = TextView(this).apply {
            text = "STANICE 7"
            textSize = 54f
            setTextColor(0xFFB02020.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setBackgroundColor(0x66000000)
            elevation = 60f
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        root.addView(stamp)
        mapWorld.postDelayed({
            stamp.alpha = 0.9f
            stamp.postDelayed({ stamp.alpha = 0f; root.removeView(stamp) }, 110)
        }, 1800)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ZAPOMENUTÝ HÁJ: duch Elderana a tajemství Pána popela (docs/adr/0046)
    // ─────────────────────────────────────────────────────────────────────────

    private fun groveStage(): Int? = if (questManager.getActiveQuestId() == SecretGrove.QUEST_ID)
        questManager.getCurrentProgress()?.takeIf { !it.isCompleted }?.currentStageIndex else null

    private fun isNightNow(): Boolean = SecretGrove.isNight(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))

    /** Trní na západě Hvozdu: po vyhnání Soulorda vede mezerou do Zapomenutého háje. */
    private fun onHiddenThorns() {
        val open = SecretGrove.canEnter(StoryFlags.isSet(this, ForestHeart.ROT_DEFEATED_KEY))
        showMapToast("🍄 " + SecretGrove.thornText(open))
        if (open) mapWorld.postDelayed({
            if (!isFinishing && currentBiome == BiomeType.FOREST)
                enterBiomeAtNode(BiomeType.HIDDEN_GROVE, SecretGrove.EXIT_NODE, MapTransition.FADE)
        }, 1600)
    }

    /** Vytesaný kámen: obraz z kamene na dřevěné tabuli a runy pod ním. */
    private fun showMural(m: SecretGrove.Mural) {
        val res = resources.getIdentifier(m.drawable, "drawable", packageName)
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), m.title, "Vytesáno do kamene kruhu") { ui, body, close ->
            if (res != 0) body.addView(ImageView(this).apply {
                setImageDrawable(androidx.core.content.ContextCompat.getDrawable(this@MakromonMapActivity, res)?.apply {
                    (this as? android.graphics.drawable.BitmapDrawable)?.isFilterBitmap = false
                })
                adjustViewBounds = true
                layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = ui.px(10f) }
            })
            body.addView(ui.text(m.text, 17f))
            body.addView(ui.button("Pokračovat") { close() }.apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = ui.px(10f) }
            })
        }
    }

    /** Oltář: v noci bdění (fáze 1), jinak rozhovor s Elderanem (i odevzdání darů). */
    private fun onGroveAltar() {
        val stage = groveStage()
        if (stage == SecretGrove.VIGIL_STAGE && !StoryFlags.isSet(this, SecretGrove.VIGIL_KEY)) {
            if (!isNightNow()) { showMapToast("🌑 " + SecretGrove.ALTAR_DAY_TEXT); return }
            StoryFlags.set(this, SecretGrove.VIGIL_KEY)
            showMapToast("🌕 Posadil ses k oltáři. Měsíc vystoupal nad koruny, runy parohů se rozzářily a ze tmy se vynořila průsvitná postava…")
            refreshStoryDecor()
            mapWorld.postDelayed({ if (!isFinishing) questManager.recheck() }, 2500)
            return
        }
        questManager.checkNpcInteraction()
    }

    /** Hrob strážce: v poslední fázi vysvobození duše, jinak nápis na kameni. */
    private fun onGroveGrave() {
        if (groveStage() != SecretGrove.RELEASE_STAGE || StoryFlags.isSet(this, SecretGrove.RELEASED_KEY)) {
            showMapToast("🪦 " + SecretGrove.GRAVE_TEXT); return
        }
        releaseElderan()
    }

    /** Vysvobození: duch se zvedne nad hrob, rozplyne se ve sloup světla a háj zazáří. */
    private fun releaseElderan() {
        if (worldScale <= 0) return
        transitionRunning = true
        movementEngine.cancel()
        val s = worldScale.toFloat()
        val ghost = decorViews.firstOrNull { it.tag == TAG_GROVE_GHOST }
        val (gx, gy) = 124 to 94
        val pillar = glowView((36 * s).toInt(), 0xFF9AF8FF.toInt(), 0xDD).apply {
            x = gx * s - 18 * s; y = gy * s - 30 * s; elevation = 3f; alpha = 0f; scaleY = 2.4f
        }
        addDecor(pillar)
        ghost?.let { g -> g.animate().x(gx * s - g.width / 2f).y(gy * s - g.height - 6 * s).setDuration(1400).start() }
        pillar.animate().alpha(1f).setStartDelay(900).setDuration(1100).withEndAction {
            ghost?.animate()?.translationYBy(-40 * s)?.alpha(0f)?.setDuration(1800)?.start()
            val flash = View(this).apply {
                setBackgroundColor(0xFFD8FFFF.toInt()); alpha = 0f; elevation = 50f
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            val root = findViewById<FrameLayout>(R.id.mapRootContainer)
            root.addView(flash)
            flash.animate().alpha(0.8f).setStartDelay(1200).setDuration(500).withEndAction {
                StoryFlags.set(this, SecretGrove.RELEASED_KEY)
                refreshStoryDecor()
                flash.animate().alpha(0f).setDuration(1200).withEndAction {
                    root.removeView(flash)
                    transitionRunning = false
                    questManager.recheck()          // fáze se splní → Elderan zanechá deník
                }.start()
            }.start()
        }.start()
    }

    /**
     * Háj na mapě: duch Elderana za oltářem (v noci nebo po bdění), dýchající světlo kamenů s obrazy,
     * světlušky; po vysvobození jen klidná záře nad hrobem.
     */
    private fun placeGroveDecor() {
        if (worldScale <= 0) return
        val s = worldScale.toFloat()
        val released = StoryFlags.isSet(this, SecretGrove.RELEASED_KEY)
        val t0 = android.os.SystemClock.uptimeMillis()
        val pulsing = mutableListOf<Pair<View, Long>>()
        // záře tří kamenů s obrazy
        listOf(24 to 148, 136 to 148, 46 to 104).forEachIndexed { i, (cx, cy) ->
            val g = glowView((34 * s).toInt(), 0xFF6AF0E0.toInt(), 0x66).apply {
                x = cx * s - 17 * s; y = cy * s - 17 * s; elevation = 1.1f
            }
            addDecor(g); pulsing += g to (i * 700L)
        }
        // světlušky nad studánkou a u oltáře
        listOf(60 to 150, 104 to 176, 70 to 120, 98 to 90, 40 to 200, 126 to 210, 84 to 240).forEachIndexed { i, (fx, fy) ->
            val fly = glowView((5 * s).toInt(), 0xFFE8FF9A.toInt(), 0xEE).apply { x = fx * s; y = fy * s; elevation = 2.2f }
            addDecor(fly); pulsing += fly to (i * 380L); bob(fly, 4f * s, 2800L + i * 300)
        }
        if (released) {
            val calm = glowView((30 * s).toInt(), 0xFF9AF8FF.toInt(), 0x55).apply {
                x = 124 * s - 15 * s; y = 94 * s - 20 * s; elevation = 1.2f
            }
            addDecor(calm); pulsing += calm to 0L
        } else if (StoryFlags.isSet(this, SecretGrove.VIGIL_KEY) || isNightNow()) {
            // Elderan: průsvitný duch za oltářem (před bděním jen slabě v noci)
            val size = (30 * s).toInt()
            val ghost = ImageView(this).apply {
                tag = TAG_GROVE_GHOST
                layoutParams = FrameLayout.LayoutParams(size, size)
                setImageResource(R.drawable.npc_elderan)
                (drawable as? android.graphics.drawable.BitmapDrawable)?.isFilterBitmap = false
                x = SecretGrove.GHOST_POS.first * s - size / 2f; y = SecretGrove.GHOST_POS.second * s - size
                elevation = 2.4f
                alpha = if (StoryFlags.isSet(this@MakromonMapActivity, SecretGrove.VIGIL_KEY)) 0.85f else 0.35f
            }
            val aura = glowView((44 * s).toInt(), 0xFF7AF0E8.toInt(), 0x77).apply {
                x = SecretGrove.GHOST_POS.first * s - 22 * s; y = SecretGrove.GHOST_POS.second * s - 34 * s; elevation = 2.3f
            }
            addDecor(aura); addDecor(ghost)
            pulsing += aura to 0L
            bob(ghost, 2f * s, 3200)
        }
        if (pulsing.isNotEmpty()) crystalAnimators += android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener {
                val t = android.os.SystemClock.uptimeMillis() - t0
                pulsing.forEach { (v, phase) -> v.alpha = 0.3f + 0.7f * cz.uhk.macroflow.pokemon.cave.SkyPassArt.pulse(t + phase, 3000).toFloat() }
            }
            start()
        }
    }

    /**
     * Příběh na mapě Hvozdu: Mydrus u pařezu na mýtině, fialové skvrny hniloby s rudými houbami,
     * Soulord u kořenů dubu (ve fázi s bossem) a po uzdravení Mycité na mýtině a zlaté světlušky.
     */
    private fun placeForestStory(legendFaced: Boolean) {
        if (worldScale <= 0 || !ForestHeart.questAvailable(legendFaced)) return
        val s = worldScale.toFloat()
        val cured = StoryFlags.isSet(this, ForestHeart.ROT_DEFEATED_KEY)
        val t0 = android.os.SystemClock.uptimeMillis()
        val pulsing = mutableListOf<Pair<View, Long>>()     // pohled + fáze pulzu

        fun sprite(res: Int, artSize: Int, pos: Pair<Int, Int>, elev: Float): ImageView = ImageView(this).apply {
            val px = (artSize * s).toInt()
            layoutParams = FrameLayout.LayoutParams(px, px)
            setImageResource(res)
            scaleType = ImageView.ScaleType.FIT_CENTER
            x = pos.first * s - px / 2f; y = pos.second * s - px; elevation = elev
        }

        if (!cured) {
            ForestHeart.ROT_SPOTS.forEachIndexed { i, (cx, cy, r) ->
                val glow = glowView((r * 3.2f * s).toInt(), 0xFFB050D0.toInt(), 0x88).apply {
                    x = cx * s - r * 1.6f * s; y = cy * s - r * 1.6f * s; elevation = 1.1f
                }
                val w = 2 * r + 1; val h = r + 1
                val patch = pixelView(ForestHeart.rotPatch(r, i + 1), w, h, (w * s).toInt(), (h * s).toInt()).apply {
                    x = (cx - r) * s; y = (cy - h / 2f) * s; elevation = 1.2f
                }
                addDecor(glow); addDecor(patch)
                pulsing += glow to (i * 530L)
            }
            if (rotBossWaiting()) {
                val aura = glowView((40 * s).toInt(), 0xFF8A3AFF.toInt(), 0xAA).apply {
                    x = ForestHeart.SOULORD_POS.first * s - 20 * s; y = ForestHeart.SOULORD_POS.second * s - 30 * s; elevation = 1.9f
                }
                val ghost = sprite(R.drawable.makromon_26_soulord, 26, ForestHeart.SOULORD_POS, 2f).apply { alpha = 0.8f }
                addDecor(aura); addDecor(ghost)
                pulsing += aura to 0L
                bob(ghost, 2.5f * s, 2600)
            }
        } else {
            // les dýchá: zlatá záře u dubu a světlušky
            val oak = glowView((46 * s).toInt(), 0xFFFFD86A.toInt(), 0x66).apply {
                x = 92 * s - 23 * s; y = 72 * s - 23 * s; elevation = 1.1f
            }
            addDecor(oak); pulsing += oak to 0L
            listOf(80 to 96, 110 to 64, 132 to 110, 60 to 120, 176 to 40, 146 to 84).forEachIndexed { i, (fx, fy) ->
                val fly = glowView((5 * s).toInt(), 0xFFFFF2A0.toInt(), 0xEE).apply {
                    x = fx * s; y = fy * s; elevation = 2.2f
                }
                addDecor(fly); pulsing += fly to (i * 410L)
                bob(fly, 4f * s, 3000L + i * 350)
            }
            // modré houby v trní – jediná viditelná stopa k Zapomenutému háji
            SecretGrove.FOREST_MUSHROOMS.forEachIndexed { i, (mx, my) ->
                val g = glowView((8 * s).toInt(), 0xFF7ADCFF.toInt(), 0xCC).apply { x = mx * s - 4 * s; y = my * s - 4 * s; elevation = 1.3f }
                val cap = pixelView(intArrayOf(0, 0xFF3AA8E0.toInt(), 0, 0xFF3AA8E0.toInt(), 0xFF7ADCFF.toInt(), 0xFF3AA8E0.toInt(),
                    0, 0xFFC8E8F0.toInt(), 0), 3, 3, (3 * s).toInt(), (3 * s).toInt()).apply { x = (mx - 1) * s; y = (my - 1) * s; elevation = 1.4f }
                addDecor(g); addDecor(cap); pulsing += g to (i * 450L)
            }
            if (questManager.getCurrentProgress()?.isCompleted == true || cured) {
                ForestHeart.MYCIT_POS.forEachIndexed { i, p ->
                    val m = sprite(R.drawable.makromon_22_mycit, 14, p, 2f).apply { if (i == 0) scaleX = -1f }
                    addDecor(m)
                }
            }
        }

        // Mydrus u pařezu (lehce se pohupuje)
        val mydrus = sprite(R.drawable.makromon_23_mydrus, 22, ForestHeart.MYDRUS_POS, 2.1f)
        addDecor(mydrus)
        bob(mydrus, 1.2f * s, 1800)

        if (pulsing.isNotEmpty()) crystalAnimators += android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener {
                val t = android.os.SystemClock.uptimeMillis() - t0
                pulsing.forEach { (v, phase) -> v.alpha = 0.35f + 0.65f * cz.uhk.macroflow.pokemon.cave.SkyPassArt.pulse(t + phase, 2600).toFloat() }
            }
            start()
        }
    }

    /** Brána světů na konci Nebeského průsmyku (docs/adr/0044): zatím zapečetěná, chybí Srdce Hvozdu. */
    private fun onWorldGate() {
        StoryFlags.set(this, SkyPass.GATE_SEEN_KEY)
        if (!StoryFlags.isSet(this, SkyPass.HEART_PLACED_KEY)) {
            val ctx = applicationContext
            lifecycleScope.launch {
                val hasHeart = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    cz.uhk.macroflow.pokemon.skills.SkillStore.count(ctx, ForestHeart.ITEM_ID) > 0
                }
                if (hasHeart) placeHeartCeremony() else showWorldGateBoard()
            }
            return
        }
        showWorldGateBoard()
    }

    /**
     * Vložení Srdce Hvozdu (docs/adr/0045): jantarové semeno vyletí od hráče obloukem do lůžka,
     * prstenec se rozzáří zeleně, závoj se promění v otevřenou bránu a zazní tabule.
     */
    private fun placeHeartCeremony() {
        if (worldScale <= 0) { showWorldGateBoard(); return }
        transitionRunning = true
        movementEngine.cancel()
        val ctx = applicationContext
        lifecycleScope.launch(Dispatchers.IO) { cz.uhk.macroflow.pokemon.skills.SkillStore.consume(ctx, ForestHeart.ITEM_ID, 1) }
        StoryFlags.set(this, SkyPass.HEART_PLACED_KEY)

        val s = worldScale.toFloat()
        val A = cz.uhk.macroflow.pokemon.cave.SkyPassArt
        val size = (10 * s).toInt()
        val seed = pixelView(ForestHeart.iconPixels(), ForestHeart.ICON, ForestHeart.ICON, size, size).apply {
            x = ashView.x + ashView.width / 2f - size / 2f; y = ashView.y; elevation = 6f
        }
        val halo = glowView(size * 3, 0xFFFFD86A.toInt(), 0xCC).apply { elevation = 5.9f }
        addDecor(halo); addDecor(seed)
        val tx = A.SOCKET_X * s - size / 2f; val ty = A.SOCKET_Y * s - size / 2f
        val sx = seed.x; val sy = seed.y
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1700; interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener {
                val p = it.animatedValue as Float
                seed.x = sx + (tx - sx) * p
                seed.y = sy + (ty - sy) * p - kotlin.math.sin(p * Math.PI).toFloat() * 40 * s
                seed.rotation = p * 360f
                halo.x = seed.x + size / 2f - halo.layoutParams.width / 2f; halo.y = seed.y + size / 2f - halo.layoutParams.height / 2f
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // záblesk: zelené světlo z brány přes celou obrazovku
                    val flash = View(this@MakromonMapActivity).apply {
                        setBackgroundColor(0xFFB8FFC8.toInt()); alpha = 0f; elevation = 50f
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    }
                    val root = findViewById<FrameLayout>(R.id.mapRootContainer)
                    root.addView(flash)
                    flash.animate().alpha(0.85f).setDuration(260).withEndAction {
                        refreshStoryDecor()          // závoj se přebarví na otevřenou bránu
                        flash.animate().alpha(0f).setDuration(900).withEndAction {
                            root.removeView(flash)
                            transitionRunning = false
                            showMapToast("🍃 Srdce Hvozdu zapadlo do lůžka a runy se rozzářily zeleně. Brána světů je otevřená!")
                            mapWorld.postDelayed({ if (!isFinishing) showWorldGateBoard() }, 900)
                        }.start()
                    }.start()
                }
            })
            start()
        }
    }

    private fun showWorldGateBoard() {
        val board = SkyPass.gateBoard(StoryFlags.isSet(this, SkyPass.HEART_PLACED_KEY))
        val (cx, cy, cw, ch) = SkyPass.GATE_CROP.toList()
        val art = runCatching {
            val full = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.sky_pass,
                android.graphics.BitmapFactory.Options().apply { inScaled = false })
            val px = IntArray(cw * ch).also { full.getPixels(it, 0, cw, cx, cy, cw, ch); full.recycle() }
            cz.uhk.macroflow.pokemon.cave.SkyPassArt.gateDetail(px, cx, cy, cw, ch, StoryFlags.isSet(this, SkyPass.HEART_PLACED_KEY))
        }.getOrNull()
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Brána světů", board.subtitle) { ui, body, close ->
            art?.let { px ->
                body.addView(ui.icon(px, cw, ch, 200f).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = ui.px(8f) }
                })
            }
            board.lines.forEach { body.addView(ui.text(it, 17f).apply { setPadding(0, 0, 0, ui.px(6f)) }) }
            if (StoryFlags.isSet(this, SecretGrove.RELEASED_KEY))
                body.addView(ui.text(SecretGrove.GATE_RUNES_LINE, 17f, ui.olive).apply { setPadding(0, ui.px(2f), 0, ui.px(6f)) })
            board.missing?.let { body.addView(ui.text("Chybí: $it 🍃", 19f, ui.rust).apply { setPadding(0, ui.px(4f), 0, ui.px(6f)) }) }
            body.addView(ui.button("Rozumím") { close() }.apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = ui.px(8f) }
            })
        }
    }

    private fun onShrine() {
        lifecycleScope.launch {
            val p = loadLegend()
            when (val st = p.shrine) {
                is LegendProgress.Shrine.NeedCrystals -> showMapToast(
                    "⛩ Svatyně má dvě prázdná lůžka – modré a červené.\n" +
                        "Chybí: " + st.missing.joinToString(", ") { it.label } + ".\n" +
                        "Krystaly hlídají strážci v jeskyni a ve Starém dole.")
                LegendProgress.Shrine.ReadyToPlace -> placeCrystalsCeremony()
                LegendProgress.Shrine.LegendAwaits -> {
                    showMapToast("Krystaly v lůžkách září… Drak se znovu probouzí!")
                    startSpecialBattle(SpecialBattle.LEGEND_PEAK, BiomeType.MOUNTAINS)
                }
                LegendProgress.Shrine.GateOpen -> {
                    if (!StoryFlags.isSet(this@MakromonMapActivity, SkyPass.VISITED_KEY))
                        showMapToast("Brána za svatyní zůstala otevřená. Za ní stoupá úzká stezka do mraků…")
                    enterBiomeAtNode(BiomeType.SKY_PASS, SkyPass.MAP.exitNode, MapTransition.FADE)
                }
            }
        }
    }

    /**
     * Vložení krystalů: oba vyletí od hráče obloukem do lůžek, svatyně se rozzáří,
     * zemi rozechvěje, ze svatyně vyšlehne paprsek světla a probudí se legenda.
     */
    private fun placeCrystalsCeremony() {
        transitionRunning = true
        lifecycleScope.launch(Dispatchers.IO) {
            CrystalColor.entries.forEach { db.userItemDao().consumeItem(it.itemId, 1) }
            if (cz.uhk.macroflow.data.FirebaseRepository.isLoggedIn) CrystalColor.entries.forEach { c ->
                db.userItemDao().getItem(c.itemId)?.let { runCatching { cz.uhk.macroflow.data.FirebaseRepository.uploadUserItem(it) } }
            }
        }
        StoryFlags.set(this, LegendProgress.PLACED_KEY)

        val startX = ashView.x + ashView.width / 2f
        val startY = ashView.y + ashView.height * 0.2f
        val size = (peakX(Crystals.W.toFloat()) - peakX(0f)).toInt().coerceAtLeast(24)
        CrystalColor.entries.forEachIndexed { i, c ->
            val flying = pixelView(Crystals.pixels(c), Crystals.W, Crystals.H, size, size * Crystals.H / Crystals.W).apply {
                x = startX - size / 2f; y = startY; elevation = 6f; alpha = 0f
            }
            addDecor(flying)
            val tx = peakX(PeakShrine.SOCKETS.getValue(c).toFloat()) - size / 2f
            val ty = peakY(PeakShrine.SOCKET_BOTTOM - 6f) - flying.layoutParams.height * 0.5f
            android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1100; startDelay = 200L + i * 250L
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener { a ->
                    val t = a.animatedValue as Float
                    flying.alpha = (t * 4f).coerceAtMost(1f)
                    flying.x = startX - size / 2f + (tx - startX + size / 2f) * t
                    // oblouk nahoru
                    flying.y = startY + (ty - startY) * t - kotlin.math.sin(t * Math.PI).toFloat() * size * 2.5f
                    flying.rotation = t * 360f
                    val s = 1f - 0.7f * t; flying.scaleX = s; flying.scaleY = s
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: android.animation.Animator) {
                        flying.visibility = View.GONE
                        addSocketCrystal(c, animateIn = true)
                        if (i == CrystalColor.entries.lastIndex) shrineAwakens()
                    }
                })
                start()
            }
        }
    }

    private fun shrineAwakens() {
        val content = findViewById<View>(R.id.mapMainContent)
        // otřesy
        android.animation.ObjectAnimator.ofFloat(content, "translationX", 0f, -14f, 12f, -10f, 9f, -6f, 4f, 0f).apply {
            duration = 900; startDelay = 250; start()
        }
        // paprsek světla ze svatyně k nebi
        val cx = peakX(86f)
        val beamW = (peakX(10f) - peakX(0f)).toInt()
        val beamBottom = peakY(PeakShrine.SOCKET_BOTTOM - 3f)
        val beam = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(beamW, beamBottom.toInt().coerceAtLeast(1))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xFFFFF6D0.toInt(), 0xCCB488FF.toInt(), 0x00B488FF)
            )
            x = cx - beamW / 2f; y = 0f; elevation = 7f
            pivotY = beamBottom; scaleY = 0f; alpha = 0.9f
        }
        addDecor(beam)
        beam.animate().scaleY(1f).setStartDelay(500).setDuration(700).start()
        // bílý záblesk přes celou obrazovku → souboj s legendou
        val root = findViewById<FrameLayout>(R.id.mapRootContainer)
        val flash = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0xFFFFF8E6.toInt()); alpha = 0f; elevation = 250f
        }
        root.addView(flash)
        flash.animate().alpha(1f).setStartDelay(1500).setDuration(450).withEndAction {
            startSpecialBattle(SpecialBattle.LEGEND_PEAK, BiomeType.MOUNTAINS)
            flash.animate().alpha(0f).setStartDelay(350).setDuration(500).withEndAction {
                root.removeView(flash); transitionRunning = false
            }.start()
        }.start()
    }

    private fun replaceMapContent(fragment: Fragment, tag: String? = null) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.mapFragmentContainer, fragment, tag)
            .addToBackStack(null)
            .commit()
    }
}