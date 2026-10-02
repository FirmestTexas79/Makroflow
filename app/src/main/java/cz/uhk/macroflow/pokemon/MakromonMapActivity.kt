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
        // Mydrus zapomíná (docs/adr/0057)
        private const val MYDRUS_REMEMBERED = "mydrus_remembered_day"
        private const val MYDRUS_TIMES = "mydrus_forgot_times"
        private const val MYDRUS_FORGOT = "mydrus_forgot"
        private const val MYDRUS_RECALLED = "mydrus_recalled"
        private const val TAG_GROVE_GHOST = "grove_ghost"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // kdy byl poutník naposledy na mapě – pro ranní sny (docs/adr/0057); onPause to přepíše
        launchLastSeen = getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).getLong("map_last_seen", 0L)
        setContentView(R.layout.activity_pokemon_map)

        mapBackground = findViewById(R.id.mapBackground)
        mapWorld = findViewById(R.id.mapWorld)
        stepProgressBar = findViewById(R.id.stepProgressBar)

        // Postava ze Sunnyside World (docs/adr/0051): snímek 64 × 40 px, 1 px spritu = 2 dp
        // (celé zařízení px, ať jsou pixely ostré); pata je u spodní hrany
        ashView = findViewById<ImageView>(R.id.ashView).also {
            val p = kotlin.math.max(1, kotlin.math.round(2 * resources.displayMetrics.density).toInt())
            it.layoutParams.width  = 64 * p
            it.layoutParams.height = 40 * p
            it.scaleType = ImageView.ScaleType.FIT_XY
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
                (cz.uhk.macroflow.pokemon.skills.GearCrafting.SET + cz.uhk.macroflow.pokemon.skills.GearCrafting.SPARK_SET + cz.uhk.macroflow.pokemon.skills.GearCrafting.PRISM_SET +
                    cz.uhk.macroflow.pokemon.skills.GearCrafting.ACCESSORIES + cz.uhk.macroflow.pokemon.skills.GearCrafting.TOOLS).forEach { g ->
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
                maybeWhiteout()         // padl celý tým → smrt postavy (docs/adr/0052)
                // Spirra mohla v souboji dosáhnout levelu 12 → vývoj podle splněné cesty (docs/adr/0055)
                if (!gamePrefs.getBoolean(cz.uhk.macroflow.pokemon.zone.Whiteout.PENDING_KEY, false) && !cinematic)
                    mapWorld.postDelayed({
                        if (!isFinishing && !cinematic && supportFragmentManager.backStackEntryCount == 0)
                            cz.uhk.macroflow.pokemon.evolution.SpirraEvolutionFlow.check(this) { companionManager.refresh() }
                    }, 600)
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
            loadZoneSeen()
            mapWorld.postDelayed({ maybeDream(0) }, 2500)          // ranní hlášení o snu (docs/adr/0057)
            // aplikace se zavřela po prohraném souboji dřív, než mapa smrt přehrála
            mapWorld.postDelayed({ if (!isFinishing) maybeWhiteout() }, 900)
            // Debug: rovnou souboj se strážcem / legendou (adb … --es debug_special boss_red)
            if (BuildConfig.DEBUG) intent.getStringExtra("debug_special")?.let { SpecialBattle.from(it) }?.let { sp ->
                mapWorld.postDelayed({ if (!isFinishing) startSpecialBattle(sp, currentBiome) }, 1200)
            }
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
        if (::movementEngine.isInitialized) movementEngine.release()
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
            // Mydrus zapomněl – poutník mu u jezírka, v houštině a u dubu připomíná, co spolu zažili (docs/adr/0057)
            if (currentBiome == BiomeType.FOREST && nodeName in cz.uhk.macroflow.pokemon.story.MydrusMemory.PLACES && gamePrefs.getBoolean(MYDRUS_FORGOT, false)) {
                val recalled = gamePrefs.getStringSet(MYDRUS_RECALLED, emptySet()).orEmpty()
                if (nodeName !in recalled) {
                    val now = recalled + nodeName
                    gamePrefs.edit().putStringSet(MYDRUS_RECALLED, now).apply()
                    showMapToast("🍂 " + cz.uhk.macroflow.pokemon.story.MydrusMemory.fragment(nodeName) +
                        if (now.containsAll(cz.uhk.macroflow.pokemon.story.MydrusMemory.PLACES)) "\n\nVrať se s ním na mýtinu." else "")
                    return@action
                }
            }
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
                        // U vody se nejdřív rybaří: nahození, „!“, záběr a pak vodní souboj (docs/adr/0054)
                        if (encounterBiome == BiomeType.WATER) fishForEncounter(nodeName, bite = true)
                        else startWildEncounter(encounterBiome)
                    } else if (nodeName == "voda" || nodeName.startsWith("jezirko_")) {
                        fishForEncounter(nodeName, bite = false)
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
            markZoneSeen(newBiome)      // mapa Zóna 1 v deníku (docs/adr/0052)
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
            val size = (6 * s).toInt()
            val glow = glowView((14 * s).toInt(), 0xFFFFF2B0.toInt(), 0x77).apply {
                x = hx * s - 7 * s; y = hy * s - 7 * s; elevation = 2.5f
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
        // kostlivý havíř ve stylu postav Sunnyside (docs/adr/0051): 1 px spritu = 1 art px mapy,
        // 4 snímky idle (nadechnutí, plamen v kahanu, žhnutí v důlcích, jiskřivka kolem kahanu)
        val sheet = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.npc_vendelin_map,
            android.graphics.BitmapFactory.Options().apply { inScaled = false })
        val fw = sheet.width / 4; val fh = sheet.height
        val frames = (0 until 4).map { k ->
            android.graphics.drawable.BitmapDrawable(resources, android.graphics.Bitmap.createBitmap(sheet, k * fw, 0, fw, fh)).apply { isFilterBitmap = false }
        }
        // o čtvrtinu menší než art px mapy (celé px zařízení, ať jsou pixely ostré)
        val px = kotlin.math.max(1, (worldScale * 0.72f).toInt()).toFloat()
        val left = Vendelin.X * s - fw * px / 2f; val top = (Vendelin.Y + 1) * s - fh * px
        val glow = glowView((18 * s).toInt(), 0xFFFFC04A.toInt(), 0x77).apply {
            x = left + 3 * px - 9 * s; y = top + 20 * px - 9 * s; elevation = 1.9f
        }
        val npc = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams((fw * px).toInt(), (fh * px).toInt())
            setImageDrawable(frames[0])
            scaleType = ImageView.ScaleType.FIT_XY
            x = left; y = top; elevation = 2f
        }
        addDecor(glow); addDecor(npc)
        crystalAnimators += android.animation.ValueAnimator.ofInt(0, 4).apply {
            duration = 1000; repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { npc.setImageDrawable(frames[(it.animatedValue as Int).coerceIn(0, 3)]) }
            start()
        }
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.5f, 1f, 0.6f, 0.9f, 0.5f).apply {
            duration = 1900; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
        // šichtovní kniha na bedně
        val book = pixelView(cz.uhk.macroflow.pokemon.skills.GearArt.ledger(), 12, 12, (5 * s).toInt(), (5 * s).toInt()).apply {
            x = (Vendelin.BOOK_X - 2.5f) * s; y = (Vendelin.BOOK_Y - 3.5f) * s; elevation = 2.1f
        }
        addDecor(book)
        val p = questManager.getCurrentProgress()
        val waiting = questManager.getActiveQuestId() == Vendelin.QUEST_ID && p != null && !p.isCompleted &&
            p.currentStageIndex == Vendelin.SIGN_STAGE && !StoryFlags.isSet(this, Vendelin.BOOK_SIGNED_KEY)
        if (waiting) {
            val bg = glowView((14 * s).toInt(), 0xFFFFF2B0.toInt(), 0x88).apply {
                x = Vendelin.BOOK_X * s - 7 * s; y = (Vendelin.BOOK_Y - 1) * s - 7 * s; elevation = 2.05f
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
    /** Pracovní stůl na louce – z něj vyskočí vykovaný předmět (docs/adr/0054). */
    private var craftTableView: View? = null

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
        craftTableView = table
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
            val waters = kotlinx.coroutines.withContext(Dispatchers.IO) { cz.uhk.macroflow.pokemon.skills.SkillStore.waterTimes(ctx) }
            gv.plots = plots; gv.plotsOpen = state.plotsOpen; gv.speedup = state.growthSpeedup; gv.waterTimes = waters
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
                else -> {
                    // Zalévání (docs/adr/0054): žíznivý záhon zalij, jinak ukaž, kdy to půjde
                    val last = kotlinx.coroutines.withContext(Dispatchers.IO) { SS.waterTimes(ctx) }[i]
                    val wait = G.waterIn(plot, last, now, state.growthSpeedup)
                    if (wait == 0L) waterPlot(i)
                    else showMapToast("⏳ Roste ${plot.berry!!.label} – zbývá ${G.clock(G.remaining(plot, now, state.growthSpeedup))}." +
                        (wait?.let { "\n💧 Zalít půjde za ${G.clock(it)}." } ?: ""))
                }
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
                        forgeAnimation(ball.pixels, cz.uhk.macroflow.pokemon.balls.Makroball.SIZE) {
                            showMapToast("🔨 Vyrobeno: ${made}× ${ball.label}" + (if (made > times) " (dvojitá výroba!)" else "") +
                                "\n+${xp.gained} XP Výroba" + skillLevelText(xp))
                            checkAwards()
                        }
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
            forgeAnimation(cz.uhk.macroflow.pokemon.skills.GearArt.gearIcon(g), cz.uhk.macroflow.pokemon.skills.SkillArt.ICON) {
                showMapToast("🧵 Vyrobeno: ${g.label}! Nasadíš ho v deníku → Postava (do prázdného slotu už je nasazený).\n+${xp.gained} XP Výroba" + skillLevelText(xp))
                checkAwards()
            }
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
            // stojí u místa, kde pracuje → seká / kope / chytá čelem k němu (docs/adr/0051)
            var working = false
            if (info != null && info.second != null && info.first.spot.biome == currentBiome.name) {
                val spot = info.first.spot
                val node = BiomeRegistry.definition(currentBiome)?.graph?.find { it.id == spot.node }
                val pos = movementEngine.getCurrentPosition()
                if (node != null && kotlin.math.hypot(node.pos.x - pos.x, node.pos.y - pos.y) < 0.05f) {
                    val place = cz.uhk.macroflow.pokemon.skills.GatherLayout.PLACES[spot]
                    val (iw, ih) = cz.uhk.macroflow.pokemon.skills.GatherLayout.imageSize(currentBiome.name)
                    val spotX = place?.let {
                        cz.uhk.macroflow.pokemon.walk.MapGeometry(iw, ih, mapWorld.width, mapWorld.height)
                            .toWorld(cz.uhk.macroflow.pokemon.walk.Pt(it.baseX.toFloat(), it.baseY.toFloat())).x
                    } ?: (ashView.x + ashView.width / 2f)
                    val faceRight = spotX >= ashView.x + ashView.width / 2f
                    movementEngine.playAction(cz.uhk.macroflow.pokemon.walk.HeroAnims.actionFor(spot.skill.id), faceRight)
                    working = true
                }
            }
            if (!working) movementEngine.stopAction()
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
        craftTableView = null
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
        if (mydrusForgets()) return
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

    // ─────────────────────────────────────────────────────────────────────────
    // SMRT POSTAVY, MAPA ZÓNA 1 A TELEPORT (docs/adr/0052)
    // ─────────────────────────────────────────────────────────────────────────

    /** Běží scéna (smrt / teleport) – mapa nebere dotyky. */
    private var cinematic = false
    private var cinemaCover: View? = null

    /** Objevené lokace (StoryFlags zone_seen_*, u starých uložených her odvozené). */
    private val zoneSeen = HashSet<String>().apply { add("TOWN") }

    /** Černá opona přes celou obrazovku; zároveň polyká dotyky, dokud scéna běží. */
    private fun cover(): View = cinemaCover ?: View(this).apply {
        setBackgroundColor(Color.BLACK); alpha = 0f; isClickable = true; isFocusable = true
        elevation = 400f
        // pozor: uvnitř apply je `this` nová View – kořen hledat na aktivitě
        this@MakromonMapActivity.findViewById<FrameLayout>(R.id.mapRootContainer).addView(this,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }.also { cinemaCover = it }

    private fun startCinematic() {
        cinematic = true
        movementEngine.cancel()
        cover().apply { visibility = View.VISIBLE; alpha = 0f }
    }

    private fun endCinematic() {
        cinematic = false
        cinemaCover?.visibility = View.GONE
    }

    /** Přiblížení kamery na postavu (svět se zvětší kolem hráče a hráč sjede doprostřed). */
    private fun zoomOnHero(scale: Float, ms: Long, end: () -> Unit) {
        val vp = findViewById<View>(R.id.mapMainContent)
        val hx = ashView.x + ashView.width / 2f
        val hy = ashView.y + ashView.height * 0.75f
        mapWorld.animate().cancel()
        cameraOverride = true
        // při měřítku 1 posun pivotu nic nepohne; poloha hráče na obrazovce = translace + pivot
        mapWorld.pivotX = hx; mapWorld.pivotY = hy
        val (tx, ty) = clampZoom(scale, vp.width / 2f - hx, vp.height * 0.55f - hy)
        mapWorld.animate().scaleX(scale).scaleY(scale)
            .translationX(tx).translationY(ty)
            .setDuration(ms).setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
            .withEndAction { if (!isFinishing) end() }.start()
    }

    /**
     * Translace přiblíženého světa omezená tak, aby u okrajů obrazovky nezůstal černý pruh
     * (svět zvětšený [scale]× kolem aktuálního pivotu musí pořád pokrýt celý výřez).
     */
    private fun clampZoom(scale: Float, tx: Float, ty: Float): Pair<Float, Float> {
        val vp = findViewById<View>(R.id.mapMainContent)
        fun clamp(t: Float, pivot: Float, size: Int, view: Int): Float {
            val hi = -pivot * (1 - scale)                       // levý / horní okraj světa na 0
            val lo = view - pivot * (1 - scale) - scale * size  // pravý / dolní okraj na kraji výřezu
            return if (lo > hi) (lo + hi) / 2f else t.coerceIn(lo, hi)
        }
        return clamp(tx, mapWorld.pivotX, mapWorld.width, vp.width) to clamp(ty, mapWorld.pivotY, mapWorld.height, vp.height)
    }

    /** Zpět na běžnou kameru (translaci srovná layoutWorld / kamera při změně lokace). */
    private fun resetZoom() {
        mapWorld.animate().cancel()
        mapWorld.scaleX = 1f; mapWorld.scaleY = 1f
        cameraOverride = false
    }

    /** Prach od nohou (pixelové obláčky do stran). */
    private fun dustPuff(count: Int = 10) {
        val unit = maxOf(1, Math.round(2 * resources.displayMetrics.density)).toFloat()     // 1 px spritu
        val footY = ashView.y + ashView.height * (38.5f / 40f)
        val cx = ashView.x + ashView.width / 2f
        repeat(count) { i ->
            val size = (unit * (2 + i % 3)).toInt()
            val side = if (i % 2 == 0) -1 else 1
            val puff = View(this).apply {
                setBackgroundColor(if (i % 3 == 0) 0xFFC8B48E.toInt() else 0xFFEADFC6.toInt())
                elevation = ashView.elevation + 1f
            }
            mapWorld.addView(puff, FrameLayout.LayoutParams(size, size))
            puff.x = cx - size / 2f + side * unit * (2 + i % 4)
            puff.y = footY - size / 2f - unit * (i % 2)
            puff.animate()
                .x(puff.x + side * unit * (7 + (i * 5) % 11)).y(puff.y - unit * (2 + (i * 3) % 7))
                .scaleX(1.9f).scaleY(1.9f).alpha(0f)
                .setDuration(480L + (i % 3) * 90L).setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction { mapWorld.removeView(puff) }.start()
        }
    }

    /**
     * Padl celý tým: souboj se zavřel → přiblížení na postavu, animace smrti, tma
     * a probuzení na prahu domova ve městě.
     */
    private fun maybeWhiteout() {
        if (cinematic || isFinishing || supportFragmentManager.backStackEntryCount > 0) return
        if (!gamePrefs.getBoolean(cz.uhk.macroflow.pokemon.zone.Whiteout.PENDING_KEY, false)) return
        gamePrefs.edit().remove(cz.uhk.macroflow.pokemon.zone.Whiteout.PENDING_KEY).apply()
        startCinematic()
        val curtain = cover()
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            zoomOnHero(2.4f, 900) {
                val total = movementEngine.playOnce("death", slow = 1.7f)
                mapWorld.postDelayed({
                    curtain.animate().alpha(1f).setDuration(700).withEndAction {
                        resetZoom()
                        enterBiomeAtNode(BiomeType.TOWN, cz.uhk.macroflow.pokemon.zone.Whiteout.RESPAWN_NODE, MapTransition.NONE)
                        movementEngine.resetIdle()
                        mapWorld.postDelayed({
                            curtain.animate().alpha(0f).setDuration(800).withEndAction {
                                endCinematic()
                                // Gudwin vítá poutníka na prahu – a počítá to (docs/adr/0057)
                                val n = gamePrefs.getInt(cz.uhk.macroflow.pokemon.story.Wakeups.COUNT_KEY, 0) + 1
                                gamePrefs.edit().putInt(cz.uhk.macroflow.pokemon.story.Wakeups.COUNT_KEY, n).apply()
                                questDialogManager.showQuestDialog(
                                    speakerResource = R.drawable.gudwin_oliver, speakerName = "Gudwin Oliver", stageName = "Probuzení",
                                    text = cz.uhk.macroflow.pokemon.story.Wakeups.gudwin(n, StoryFlags.insight(this), (0..99).random()),
                                    totalSteps = 1, currentStepIndex = 0)
                            }.start()
                        }, 450)
                    }.start()
                }, total + 900)
            }
        }, 350)
    }

    /** Zapíše objevenou lokaci (synchronizuje se jako příběhový příznak). */
    private fun markZoneSeen(biome: BiomeType) {
        if (biome.name !in cz.uhk.macroflow.pokemon.zone.ZoneOne.LOCATIONS) return
        if (zoneSeen.add(biome.name) || !StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.zone.ZoneOne.seenKey(biome.name)))
            StoryFlags.set(this, cz.uhk.macroflow.pokemon.zone.ZoneOne.seenKey(biome.name))
    }

    /** Objevené lokace ze StoryFlags; u starších her doplní odvozené (a rovnou je zapíše). */
    private fun loadZoneSeen() {
        val ctx = applicationContext
        lifecycleScope.launch {
            val questBiomes = runCatching {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val ids = db.questDao().getAllQuests().map { it.questId }.toSet()
                    BiomeRegistry.DEFINITIONS.values.filter { it.questId != null && it.questId in ids }.map { it.type.name }.toSet()
                }
            }.getOrDefault(emptySet())
            val inferred = cz.uhk.macroflow.pokemon.zone.ZoneOne.inferSeen(StoryFlags.all(ctx), questBiomes) + currentBiome.name
            inferred.filter { it in cz.uhk.macroflow.pokemon.zone.ZoneOne.LOCATIONS }.forEach { b ->
                zoneSeen.add(b)
                if (!StoryFlags.isSet(ctx, cz.uhk.macroflow.pokemon.zone.ZoneOne.seenKey(b)))
                    StoryFlags.set(ctx, cz.uhk.macroflow.pokemon.zone.ZoneOne.seenKey(b))
            }
        }
    }

    /** Co ukáže mapa v deníku. */
    data class ZoneState(val current: String, val heroFrac: PointF, val seen: Set<String>, val heroHead: android.graphics.Bitmap?)

    private var heroHeadCache: android.graphics.Bitmap? = null

    fun zoneState(): ZoneState {
        val head = heroHeadCache ?: movementEngine.hero.portrait()?.let { p ->
            // hlava = horní čtverec postavy, zvětšená bez vyhlazení
            val side = minOf(p.width, p.height)
            val sq = android.graphics.Bitmap.createBitmap(p, 0, 0, side, side)
            android.graphics.Bitmap.createScaledBitmap(sq, side * 8, side * 8, false)
        }.also { heroHeadCache = it }
        val pos = movementEngine.getCurrentPosition()
        return ZoneState(currentBiome.name, PointF(pos.x, pos.y), zoneSeen.toSet(), head)
    }

    /** Klepnutí na lokaci v mapě deníku: důvod, proč to nejde, nebo dřevěná nabídka teleportu. */
    fun onZonePick(biome: String) {
        if (cinematic) return
        val target = runCatching { BiomeType.valueOf(biome) }.getOrNull() ?: return
        val name = cz.uhk.macroflow.pokemon.zone.ZoneOne.NAMES[biome] ?: biome
        lifecycleScope.launch {
            val forestDone = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching {
                    cz.uhk.macroflow.pokemon.cave.ForestMap.completedTasks(db.questDao().getAllQuests().map { p ->
                        val total = cz.uhk.macroflow.pokemon.quests.QuestRegistry.byId(p.questId)?.stages?.size ?: 0
                        Triple(total, p.currentStageIndex, p.isCompleted)
                    })
                }.getOrDefault(0)
            }
            val boots = BuildConfig.DEBUG && gamePrefs.getBoolean(DEBUG_BOOTS_KEY, false)
            val missing = if (boots) 0 else BiomeAccess.missingSteps(BiomeType.MOUNTAINS, currentDailySteps)
            val block = cz.uhk.macroflow.pokemon.zone.ZoneOne.teleportBlock(biome, currentBiome.name, zoneSeen,
                missing, forestDone, cz.uhk.macroflow.pokemon.cave.ForestMap.REQUIRED_TASKS)
            val root = findViewById<FrameLayout>(R.id.mapRootContainer)
            when (block) {
                cz.uhk.macroflow.pokemon.zone.ZoneOne.Block.Here -> showMapToast("📍 $name – tady právě stojíš.")
                cz.uhk.macroflow.pokemon.zone.ZoneOne.Block.Unknown -> showMapToast("🌫️ Tohle místo jsi ještě neobjevil. Dojdi tam nejdřív po svých.")
                cz.uhk.macroflow.pokemon.zone.ZoneOne.Block.Secret -> showMapToast("🌫️ Tahle cesta se na mapu zakreslit nedá.")
                is cz.uhk.macroflow.pokemon.zone.ZoneOne.Block.Steps ->
                    showMapToast("⛰️ $name leží za horami. Dnes ti na cestu chybí ještě ${block.missing} kroků.")
                is cz.uhk.macroflow.pokemon.zone.ZoneOne.Block.Forest ->
                    showMapToast("🌲 Hvozd tě pustí dál, až splníš ${block.need} úkolů (máš ${block.done}).")
                null -> cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(root, "Teleport", "Zóna 1 · $name") { ui, body, close ->
                    body.addView(ui.text("Vyskočíš vysoko nad Makrosvět a dopadneš rovnou na místo.", 16f, ui.inkSoft))
                    body.addView(ui.spacer(12f))
                    body.addView(ui.button("Přenést se: $name") { close(); teleportTo(target) })
                }
            }
        }
    }

    /** Teleport: deník se zavře, přiblížení na postavu, výskok z obrazovky s prachem a dopad v cíli. */
    private fun teleportTo(target: BiomeType) {
        if (cinematic) return
        val node = cz.uhk.macroflow.pokemon.zone.ZoneOne.ARRIVAL[target.name] ?: return
        if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
        startCinematic()
        val curtain = cover()
        val vp = findViewById<View>(R.id.mapMainContent)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            zoomOnHero(2.2f, 650) {
                val total = movementEngine.playOnce("jump", slow = 1.3f)
                // odraz: po přikrčení vyletí nahoru z obrazovky, od nohou se zvedne prach
                mapWorld.postDelayed({
                    dustPuff()
                    ashView.animate().y(ashView.y - vp.height / mapWorld.scaleY - ashView.height * 2f)
                        .setDuration(480).setInterpolator(android.view.animation.AccelerateInterpolator(1.4f))
                        .withEndAction {
                            curtain.animate().alpha(1f).setDuration(260).withEndAction {
                                resetZoom()
                                enterBiomeAtNode(target, node, MapTransition.NONE)
                                movementEngine.resetIdle()
                                // až resetToPosition postaví postavu: seskok shora a prach při dopadu
                                mapBackground.post { mapBackground.post { landFromSky(curtain) } }
                            }.start()
                        }.start()
                }, (total * 0.3f).toLong())
            }
        }, 380)
    }

    private fun landFromSky(curtain: View) {
        if (isFinishing) return
        val vp = findViewById<View>(R.id.mapMainContent)
        val finalY = ashView.y
        ashView.y = finalY - vp.height
        curtain.animate().alpha(0f).setDuration(350).start()
        ashView.animate().y(finalY).setStartDelay(200).setDuration(520)
            .setInterpolator(android.view.animation.AccelerateInterpolator(1.6f))
            .withEndAction {
                ashView.animate().setStartDelay(0)
                dustPuff(12)
                ashView.pivotX = ashView.width / 2f; ashView.pivotY = ashView.height.toFloat()
                ashView.scaleY = 0.82f
                ashView.animate().scaleY(1f).setDuration(220).withEndAction { endCinematic() }.start()
            }.start()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RYBAŘENÍ, ZALÉVÁNÍ A KOVÁNÍ (docs/adr/0054)
    // ─────────────────────────────────────────────────────────────────────────

    /** Divoké setkání: uloží biom pro souboj a otevře ho (vodní má vlastní úvodní scénu). */
    private fun startWildEncounter(biome: BiomeType) {
        getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit()
            .putString("LAST_BIOME", biome.name)
            .remove("FORCE_ENCOUNTER_ID")
            .apply()
        replaceMapContent(PokemonBattleFragment())
    }

    /** Jeden pixel spritu postavy v px světa. */
    private fun spritePx(): Float = ashView.width / movementEngine.hero.spec.frameW.toFloat()

    /**
     * Leží voda vpravo od postavy? Spočítá modré pixely obrázku mapy vlevo a vpravo od nohou
     * (u jezírek ve Hvozdu i u louky, bez ručně zadaných stran).
     */
    private fun waterOnRight(): Boolean {
        val d = mapBackground.drawable as? android.graphics.drawable.BitmapDrawable ?: return true
        val bmp = d.bitmap ?: return true
        if (mapBackground.width == 0 || d.intrinsicWidth <= 0) return true
        val inv = android.graphics.Matrix()
        val fit = mapBackground.scaleType == ImageView.ScaleType.FIT_XY
        if (!fit && !mapBackground.imageMatrix.invert(inv)) return true
        val pt = FloatArray(2)
        fun blue(x: Float, y: Float): Boolean {
            val bx: Int; val by: Int
            if (fit) { bx = (x / mapBackground.width * bmp.width).toInt(); by = (y / mapBackground.height * bmp.height).toInt() }
            else {
                pt[0] = x; pt[1] = y; inv.mapPoints(pt)
                bx = (pt[0] * bmp.width / d.intrinsicWidth).toInt(); by = (pt[1] * bmp.height / d.intrinsicHeight).toInt()
            }
            if (bx !in 0 until bmp.width || by !in 0 until bmp.height) return false
            val c = bmp.getPixel(bx, by)
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            return b > r + 40 && b > 110 && b >= g - 10
        }
        val p = spritePx()
        val cx = ashView.x + ashView.width / 2f
        val foot = ashView.y + 38.5f * p
        var left = 0; var right = 0
        val step = maxOf(1f, 2 * p)
        var dy = -12 * p
        while (dy <= 14 * p) {
            var dx = 4 * p
            while (dx <= 40 * p) {
                if (blue(cx + dx, foot + dy)) right++
                if (blue(cx - dx, foot + dy)) left++
                dx += step
            }
            dy += step
        }
        return right >= left
    }

    /** Bublina s vykřičníkem nad hlavou – záběr! */
    private fun exclaim(then: () -> Unit) {
        val p = spritePx()
        val rows = listOf(
            ".KKKKKKK.",
            "KWWWWWWWK",
            "KWWWRWWWK",
            "KWWWRWWWK",
            "KWWWRWWWK",
            "KWWWRWWWK",
            "KWWWWWWWK",
            "KWWWRWWWK",
            "KWWWWWWWK",
            ".KKKWKKK.",
            "...KWK...",
            "....K....")
        val col = mapOf('K' to 0xFF1A1410.toInt(), 'W' to 0xFFFFFBEA.toInt(), 'R' to 0xFFE0402A.toInt())
        val px = IntArray(9 * 12) { i -> col[rows[i / 9][i % 9]] ?: 0 }
        val w = (9 * p).toInt(); val h = (12 * p).toInt()
        val bubble = pixelView(px, 9, 12, w, h).apply {
            x = ashView.x + ashView.width / 2f - w / 2f
            y = ashView.y + 13 * p - h
            pivotX = w / 2f; pivotY = h.toFloat()
            scaleX = 0.2f; scaleY = 0.2f
            elevation = ashView.elevation + 2f
        }
        mapWorld.addView(bubble)
        mapWorld.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        bubble.animate().scaleX(1.15f).scaleY(1.15f).setDuration(140).withEndAction {
            bubble.animate().scaleX(1f).scaleY(1f).setDuration(90).withEndAction {
                mapWorld.postDelayed({
                    bubble.animate().alpha(0f).setDuration(150).withEndAction { mapWorld.removeView(bubble) }.start()
                    if (!isFinishing) then()
                }, 520)
            }.start()
        }.start()
    }

    /**
     * Setkání u vody: postava nahodí a párkrát zatáhne za prut. Při záběru vyskočí „!“,
     * postava zabere a teprve pak naskočí vodní souboj. Bez záběru se jen nic nechytí.
     */
    private fun fishForEncounter(nodeName: String, bite: Boolean) {
        if (cinematic) return
        startCinematic()
        val right = waterOnRight()
        val dir = if (right) "e" else "w"
        movementEngine.playAction("reeling", right)
        val loops = if (bite) 2 + (0..1).random() else 3
        val reel = movementEngine.durationOf("reeling", dir).coerceAtLeast(300)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            if (!bite) {
                movementEngine.stopAction(); endCinematic()
                showMapToast("🎣 Nic nezabralo. " + emptyEncounterText(nodeName))
                return@postDelayed
            }
            exclaim {
                val t = movementEngine.playOnce("caught", dir = dir)
                mapWorld.postDelayed({
                    if (isFinishing) return@postDelayed
                    endCinematic()
                    startWildEncounter(BiomeType.WATER)
                    mapWorld.postDelayed({ movementEngine.resetIdle() }, 500)
                }, t + 120)
            }
        }, reel * loops)
    }

    /** Zalití záhonu: postava zalévá konvičkou, pak se růst posune dopředu. */
    private fun waterPlot(i: Int) {
        if (cinematic || mapWorld.width == 0) return
        val r = cz.uhk.macroflow.pokemon.skills.MeadowLayout.PLOTS[i]
        val c = meadowGeometry().toWorld(cz.uhk.macroflow.pokemon.walk.Pt((r.x0 + r.x1) / 2f, (r.y0 + r.y1) / 2f))
        startCinematic()
        movementEngine.playAction("watering", c.x >= ashView.x + ashView.width / 2f)
        val ctx = applicationContext
        val G = cz.uhk.macroflow.pokemon.skills.Garden
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            lifecycleScope.launch {
                val now = System.currentTimeMillis() / 1000
                val (boost, st, plot) = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
                    Triple(SS.water(ctx, i, now), SS.state(ctx), SS.plots(ctx)[i])
                }
                movementEngine.stopAction(); endCinematic()
                refreshGarden()
                val berry = plot.berry
                if (boost == null || berry == null) return@launch
                val left = G.remaining(plot, now, st.growthSpeedup)
                showMapToast("💧 Zalito! ${berry.label} poroste o ${G.clock(boost)} rychleji" +
                    (if (left > 0) " – zbývá ${G.clock(left)}. Znovu zalít půjde za ${G.clock(G.waterInterval(berry, st.growthSpeedup))}." else " a je hotová!"))
            }
        }, maxOf(1100L, movementEngine.durationOf("watering") * 3))
    }

    /** Jiskry od úderu kladivem (žluté a oranžové pixely do stran a nahoru). */
    private fun sparkBurst(x: Float, y: Float) {
        val p = spritePx()
        repeat(9) { k ->
            val size = (p * (1 + k % 2)).toInt().coerceAtLeast(1)
            val spark = View(this).apply {
                setBackgroundColor(if (k % 3 == 0) 0xFFFFF4B0.toInt() else if (k % 3 == 1) 0xFFFFC23A.toInt() else 0xFFFF7A1C.toInt())
                elevation = ashView.elevation + 2f
            }
            mapWorld.addView(spark, FrameLayout.LayoutParams(size, size))
            spark.x = x; spark.y = y
            val a = Math.toRadians(200.0 + k * 17.0)
            val dist = p * (6 + (k * 7) % 9)
            spark.animate().x(x + (Math.cos(a) * dist).toFloat()).y(y + (Math.sin(a) * dist).toFloat() - p * 2)
                .alpha(0f).setDuration(320L + (k % 3) * 70L).setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction { mapWorld.removeView(spark) }.start()
        }
    }

    /** Záře za vykovaným předmětem: paprsky, které se pomalu točí. */
    private fun raysView(size: Int): View = object : View(this) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: android.graphics.Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = width / 2f
            paint.shader = android.graphics.RadialGradient(cx, cy, r, intArrayOf(0xEEFFF6C8.toInt(), 0x88FFD45A.toInt(), 0x00FFB020),
                floatArrayOf(0f, 0.45f, 1f), android.graphics.Shader.TileMode.CLAMP)
            val path = android.graphics.Path()
            for (k in 0 until 10) {
                val a0 = Math.PI * 2 * k / 10; val a1 = a0 + Math.PI / 18
                path.moveTo(cx, cy)
                path.lineTo(cx + (Math.cos(a0) * r).toFloat(), cy + (Math.sin(a0) * r).toFloat())
                path.lineTo(cx + (Math.cos(a1) * r).toFloat(), cy + (Math.sin(a1) * r).toFloat())
                path.close()
            }
            c.drawPath(path, paint)
            c.drawCircle(cx, cy, r * 0.42f, paint)
        }
    }.apply { layoutParams = FrameLayout.LayoutParams(size, size) }

    /**
     * Kování u pracovního stolu: postava třikrát udeří kladivem, po každém úderu se kamera
     * přiblíží, pak ze stolu vyskočí vyrobený předmět se září za sebou a kamera se vrátí.
     */
    private fun forgeAnimation(icon: IntArray, iconSize: Int, then: () -> Unit) {
        val table = craftTableView
        if (cinematic || table == null || mapWorld.width == 0) { then(); return }
        startCinematic()
        val vp = findViewById<View>(R.id.mapMainContent)
        val tableCx = table.x + table.width / 2f
        val tableTop = table.y + table.height * 0.25f
        val faceRight = tableCx >= ashView.x + ashView.width / 2f
        val dir = if (faceRight) "e" else "w"
        // střed záběru mezi postavou a stolem
        val cx = (tableCx + ashView.x + ashView.width / 2f) / 2f
        val cy = (tableTop + ashView.y + ashView.height * 0.7f) / 2f
        val tx0 = mapWorld.translationX; val ty0 = mapWorld.translationY
        mapWorld.animate().cancel()
        cameraOverride = true
        mapWorld.pivotX = cx; mapWorld.pivotY = cy
        fun zoom(scale: Float, ms: Long, end: (() -> Unit)? = null) {
            // bod (cx, cy) má na obrazovce polohu tx + pivot → doprostřed výřezu
            val (tx, ty) = if (scale == 1f) tx0 to ty0 else clampZoom(scale, vp.width / 2f - cx, vp.height * 0.5f - cy)
            mapWorld.animate().scaleX(scale).scaleY(scale)
                .translationX(tx).translationY(ty)
                .setDuration(ms).setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                .withEndAction { if (!isFinishing) end?.invoke() }.start()
        }
        val reveal: () -> Unit = {
            val p = spritePx()
            val itemSize = (iconSize * p * 0.9f).toInt()
            val raySize = itemSize * 3
            val rays = raysView(raySize).apply {
                x = tableCx - raySize / 2f; y = tableTop - raySize / 2f
                alpha = 0f; scaleX = 0.3f; scaleY = 0.3f; elevation = ashView.elevation + 3f
            }
            val item = pixelView(icon, iconSize, iconSize, itemSize, itemSize).apply {
                x = tableCx - itemSize / 2f; y = tableTop - itemSize / 2f
                scaleX = 0.2f; scaleY = 0.2f; elevation = ashView.elevation + 4f
            }
            mapWorld.addView(rays); mapWorld.addView(item)
            movementEngine.resetIdle()
            val lift = itemSize * 1.1f
            rays.animate().alpha(1f).scaleX(1f).scaleY(1f).translationYBy(-lift).setDuration(420).start()
            android.animation.ObjectAnimator.ofFloat(rays, View.ROTATION, 0f, 120f).apply {
                duration = 2000; interpolator = android.view.animation.LinearInterpolator()
            }.start()
            item.animate().scaleX(1.2f).scaleY(1.2f).translationYBy(-lift).setDuration(380)
                .setInterpolator(android.view.animation.OvershootInterpolator(2f)).withEndAction {
                    item.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }.start()
            mapWorld.postDelayed({
                if (isFinishing) return@postDelayed
                zoom(1f, 650) {
                    mapWorld.pivotX = mapWorld.width / 2f; mapWorld.pivotY = mapWorld.height / 2f
                    cameraOverride = false
                    endCinematic()
                    then()
                }
                // předmět odletí k postavě a zmizí, záře pohasne
                item.animate().x(ashView.x + ashView.width / 2f - itemSize / 2f).y(ashView.y + ashView.height * 0.4f)
                    .scaleX(0.3f).scaleY(0.3f).alpha(0f).setDuration(520).withEndAction { mapWorld.removeView(item) }.start()
                rays.animate().alpha(0f).setDuration(420).withEndAction { mapWorld.removeView(rays) }.start()
            }, 1500)
        }
        val hammerMs = movementEngine.durationOf("hammer", dir).coerceAtLeast(300)
        val impactMs = 3 * 75L                                    // náraz je 4. snímek úderu
        val zooms = listOf(1.35f, 1.75f, 2.2f)
        fun strike(n: Int) {
            movementEngine.playOnce("hammer", dir = dir)
            mapWorld.postDelayed({
                if (isFinishing) return@postDelayed
                sparkBurst(tableCx - (if (faceRight) table.width * 0.2f else -table.width * 0.2f), tableTop)
                mapWorld.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                zoom(zooms[n], 260)
            }, impactMs)
            mapWorld.postDelayed({
                if (isFinishing) return@postDelayed
                if (n < zooms.lastIndex) strike(n + 1) else reveal()
            }, hammerMs + 140)
        }
        strike(0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // KUSTODIÁT: SNY, MYDRUS ZAPOMÍNÁ, PROBUZENÍ (docs/adr/0057)
    // ─────────────────────────────────────────────────────────────────────────

    private var launchLastSeen = 0L

    /**
     * Mydrus po vyhnání hniloby čas od času zapomene i poutníka. Vrací true, když se o mýtinu
     * postaral (nepoznal / znovu poznal) a běžný dialog už se nemá ukázat.
     */
    private fun mydrusForgets(): Boolean {
        val M = cz.uhk.macroflow.pokemon.story.MydrusMemory
        if (!StoryFlags.isSet(this, ForestHeart.ROT_DEFEATED_KEY)) return false
        val today = java.time.LocalDate.now().toEpochDay()
        var remembered = gamePrefs.getLong(MYDRUS_REMEMBERED, 0L)
        if (remembered == 0L) { remembered = today; gamePrefs.edit().putLong(MYDRUS_REMEMBERED, today).apply() }
        val times = gamePrefs.getInt(MYDRUS_TIMES, 0)
        var forgot = gamePrefs.getBoolean(MYDRUS_FORGOT, false)
        if (!forgot && M.forgets(true, today, remembered, times)) {
            forgot = true
            gamePrefs.edit().putBoolean(MYDRUS_FORGOT, true).putStringSet(MYDRUS_RECALLED, emptySet()).apply()
        }
        if (!forgot) return false
        val recalled = gamePrefs.getStringSet(MYDRUS_RECALLED, emptySet()).orEmpty()
        val (stage, text) = if (recalled.containsAll(M.PLACES)) {
            gamePrefs.edit().putBoolean(MYDRUS_FORGOT, false).putLong(MYDRUS_REMEMBERED, today).putInt(MYDRUS_TIMES, times + 1)
                .putStringSet(MYDRUS_RECALLED, emptySet()).apply()
            "Vzpomínka" to M.remembered(times)
        } else "Zapomnění" to M.stranger(times)
        questDialogManager.showQuestDialog(
            speakerResource = R.drawable.makromon_23_mydrus, speakerName = "Mydrus", stageName = stage,
            text = text, totalSteps = 1, currentStepIndex = 0)
        return true
    }

    /** Ráno po spánku se občas objeví hlášení o snu personálu – pak je k přečtení v deníku (Spisy). */
    private fun maybeDream(attempt: Int) {
        if (isFinishing || attempt > 8) return
        val root = findViewById<FrameLayout>(R.id.mapRootContainer)
        // nepřekrývat cedulku „Vítej zpět“, souboj ani scénu
        if (cinematic || supportFragmentManager.backStackEntryCount > 0 || cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.isOpen(root)) {
            mapWorld.postDelayed({ maybeDream(attempt + 1) }, 3000); return
        }
        val D = cz.uhk.macroflow.pokemon.story.Dreams
        val flags = StoryFlags.all(this)
        val insight = Insight.level(flags)
        val now = System.currentTimeMillis() / 1000
        val away = if (launchLastSeen > 0) now - launchLastSeen else 0L
        val hour = java.time.LocalTime.now().hour
        val today = java.time.LocalDate.now().toEpochDay()
        val already = gamePrefs.getLong("dream_last_day", -1L) == today
        if (!D.eligible(insight, hour, away, already)) return
        gamePrefs.edit().putLong("dream_last_day", today).apply()        // jedna šance za ráno
        val dream = D.next(flags, insight, hour, away, false, (0..99).random()) ?: return
        StoryFlags.set(this, dream.key)
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(root, "Hlášení o snu", "Ráno ti pod dveřmi někdo nechal přeložený list.") { ui, body, close ->
            body.addView(ui.text(dream.who + ":\n„" + Insight.render(dream.text, insight) + "“", 16f).apply {
                typeface = android.graphics.Typeface.MONOSPACE
                setBackgroundColor(0x22BC6C25)
                setPadding(ui.px(10f), ui.px(10f), ui.px(10f), ui.px(10f))
            })
            body.addView(ui.spacer(10f))
            body.addView(ui.text("List najdeš v deníku pod záložkou Spisy.", 14f, ui.inkSoft))
            body.addView(ui.spacer(8f))
            body.addView(ui.button("Odložit") { close() })
        }
    }

    private fun replaceMapContent(fragment: Fragment, tag: String? = null) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.mapFragmentContainer, fragment, tag)
            .addToBackStack(null)
            .commit()
    }
}