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
import coil.load
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
    private lateinit var staminaBar: cz.uhk.macroflow.pokemon.stamina.StaminaBar
    private val hudViews by lazy {
        listOf(R.id.btnExitMap, R.id.btnSound, R.id.btnArena, R.id.btnStartTutorial, R.id.btnOpenJournal,
            R.id.staminaBar, R.id.restPill, R.id.companionBox).mapNotNull { findViewById<View>(it) }
    }
    private var hudFocus: View? = null

    private fun hudMenuOpen() = supportFragmentManager.backStackEntryCount > 0 ||
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.isOpen(findViewById(R.id.mapRootContainer))

    /** HUD průsvitný; ťuknutý prvek svítí naplno, dokud je otevřené jeho menu, pak zase pomalu vybledne. */
    private fun syncHud() {
        val open = hudMenuOpen()
        if (!open) hudFocus = null
        hudViews.forEach { it.animate().alpha(if (open && it == hudFocus) 1f else HUD_ALPHA).setDuration(450).start() }
    }
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
        cz.uhk.macroflow.pokemon.cave.MinesMap.MAP.encounterNodes +
        cz.uhk.macroflow.pokemon.cave.AshPlain.MAP.encounterNodes +
        cz.uhk.macroflow.pokemon.cave.QuietOrchard.MAP.encounterNodes +
        cz.uhk.macroflow.pokemon.cave.EchoCanyon.MAP.encounterNodes +
        cz.uhk.macroflow.pokemon.cave.TowerPlain.MAP.encounterNodes +
        cz.uhk.macroflow.pokemon.cave.TowerForest.MAP.encounterNodes +
        cz.uhk.macroflow.pokemon.cave.TowerRock.MAP.encounterNodes +
        cz.uhk.macroflow.pokemon.cave.TowerCave.MAP.encounterNodes +
        cz.uhk.macroflow.pokemon.cave.TowerWater.MAP.encounterNodes +
        cz.uhk.macroflow.pokemon.cave.TowerDorm.MAP.encounterNodes +
            cz.uhk.macroflow.pokemon.cave.TowerIce.MAP.encounterNodes +
            cz.uhk.macroflow.pokemon.cave.TowerFire.MAP.encounterNodes +
            cz.uhk.macroflow.pokemon.cave.TowerFairy.MAP.encounterNodes +
            cz.uhk.macroflow.pokemon.cave.TowerPoison.MAP.encounterNodes

    /** Přechod mezi mapami. */
    private enum class MapTransition { NONE, FADE, CAVE_IN, CAVE_OUT,
        /** Sestup ze Starého dolu do Dolů (docs/adr/0049). */
        MINE_DESCENT }

    companion object {
        /** Průhlednost HUD na mapě – scéna musí být vidět (rozsvítí se jen prvek s otevřeným menu). */
        const val HUD_ALPHA = 0.3f
        /** Vstup s pixelovým přechodem (docs/adr/0070). */
        const val EXTRA_PORTAL = "portal"
        private const val DOUBLE_CLICK_TIME = 300L
        /** Lokace s vlastní scénou přechodu (docs/adr/0042). */
        private val LOCATION_SCENES = setOf("TOWN", "MEADOW", "FOREST", "MOUNTAINS", "SKY_PASS", "HIDDEN_GROVE", "ASH_PLAIN", "QUIET_ORCHARD", "ECHO_CANYON", "OZVENA")
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
            cz.uhk.macroflow.pokemon.cave.MinesMap.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.MinesMap.MAZE_NODE +
            cz.uhk.macroflow.pokemon.cave.TowerLobby.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.AshPlain.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.QuietOrchard.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.EchoCanyon.ACTION_NODES +
            cz.uhk.macroflow.pokemon.cave.OzvenaCity.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.ArenaHall.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.RootCamp.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.RootWatch.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.RootSlopeCamp.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.RootSignal.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.RootBridge.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.RootNameless.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.RootTemple.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.RootForest.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.RootThreshold.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.RootTrunk.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.RootHeart.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerArchive.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerMeasure.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerPlain.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerForest.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerRock.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerCave.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerWater.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerDorm.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerIce.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerFire.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerOffice.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerFairy.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerPoison.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerHideout.ACTION_NODES + cz.uhk.macroflow.pokemon.cave.TowerAbyss.ACTION_NODES
        private const val TAG_JOURNAL = "QUEST_JOURNAL"
        private const val DEBUG_BOOTS_KEY = "DEBUG_SEVEN_LEAGUE_BOOTS"
        /** Debug: výtah ve Věži jezdí do všech hotových pater bez kalibrace. */
        private const val DEBUG_LIFT_KEY = "DEBUG_FREE_ELEVATOR"
        private const val DEBUG_COUPON_KEY = "DEBUG_GOLDEN_COUPON"
        private const val DEBUG_KEYCARD_KEY = "DEBUG_KEYCARD"
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
        // první vstup: dlouhý úvod (docs/adr/0072), jinak krátké odkrytí (0070)
        val debugIntro = BuildConfig.DEBUG && intent.getBooleanExtra("debug_intro", false)
        if (savedInstanceState == null && (debugIntro || intent.getBooleanExtra(EXTRA_PORTAL, false))) {
            if (debugIntro || IntroFlyoverView.shouldPlay(this)) IntroFlyoverView.play(findViewById(android.R.id.content))
            else PortalTransitionView.reveal(findViewById(android.R.id.content))
        }

        mapBackground = findViewById(R.id.mapBackground)
        mapWorld = findViewById(R.id.mapWorld)
        stepProgressBar = findViewById(R.id.stepProgressBar)
        staminaBar = findViewById(R.id.staminaBar)
        run {
            val dp = resources.displayMetrics.density
            findViewById<View>(R.id.companionShadow).background = cz.uhk.macroflow.pokemon.skills.ui.BevelDrawable(
                2f * dp, 0xFF606C38.toInt(), 0xFF8C9C5B.toInt(), 0xFF283618.toInt(), 0xFF2E1B0E.toInt())
        }

        // Postava ze Sunnyside World (docs/adr/0051): snímek 64 × 40 px, 1 px spritu = 2 dp
        // (celé zařízení px, ať jsou pixely ostré); pata je u spodní hrany
        ashView = findViewById<ImageView>(R.id.ashView).also {
            val p = kotlin.math.max(1, kotlin.math.round(2 * resources.displayMetrics.density).toInt())
            it.layoutParams.width  = 64 * p
            it.layoutParams.height = 40 * p
            it.scaleType = ImageView.ScaleType.FIT_XY
            it.requestLayout()
        }

        imageLoader = ImageLoader.Builder(this).components {
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

        // Debug: náhled evoluce bez ukládání (adb … --es debug_evo 004:005) – docs/adr/0064
        if (BuildConfig.DEBUG) intent.getStringExtra("debug_evo")?.split(':')?.takeIf { it.size == 2 }?.let { (from, to) ->
            val move = MakromonGrowthManager.getNewMoveForLevel(to, 1) ?: BattleFactory.createById(to).moves.last()
            mapBackground.postDelayed({ EvolutionDialog(this, -1, from, to, move, preview = true) {}.show() }, 1500)
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
        questDialogManager.onHidden = { mapWorld.postDelayed({ if (!isFinishing) maybePlayStoryCutscene() }, 350) }

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
            crackProvider = { qid -> Insight.crack(qid, StoryFlags.insight(applicationContext), kotlin.random.Random.nextInt(100)) },
            // Hunfrey radí podle postupu hráče, jak získat Srdce Hvozdu (docs/adr/0085)
            hintProvider = { qid, idx -> if (qid == cz.uhk.macroflow.pokemon.story.Hunfrey.QUEST_ID && idx == cz.uhk.macroflow.pokemon.story.Hunfrey.ADVICE_STAGE) hunfreyAdvice() else null }
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
        hudViews.forEach { it.alpha = HUD_ALPHA }
        findViewById<FrameLayout>(R.id.mapRootContainer).setOnHierarchyChangeListener(object : android.view.ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) { syncHud() }
            override fun onChildViewRemoved(parent: View?, child: View?) { syncHud() }
        })
        // HUD: všechna tlačítka v dřevěném rámečku jako menu Makrosvěta (docs/adr/0080)
        listOf(R.id.btnExitMap, R.id.btnSound, R.id.btnArena, R.id.btnStartTutorial, R.id.btnOpenJournal).forEach {
            findViewById<View>(it).background = cz.uhk.macroflow.pokemon.skills.ui.WoodPanelDrawable(1.5f * resources.displayMetrics.density, parchment = false)
        }

        // Hudba a zvuky: přepínač v HUD, zvuky se načtou předem
        cz.uhk.macroflow.pokemon.audio.GameAudio.preload(this)
        val btnSound = findViewById<ImageButton>(R.id.btnSound)
        fun soundIcon() = btnSound.setImageResource(
            if (cz.uhk.macroflow.pokemon.audio.GameAudio.isEnabled(this)) R.drawable.ic_px_sound_on
            else R.drawable.ic_px_sound_off)
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
            syncHud()
            refreshStepBar()            // nad deníkem / soubojem ukazatel kroků nemá co dělat (a bral dotyky)
            if (supportFragmentManager.backStackEntryCount == 0) {
                companionManager.refresh()
                refreshTeamColumn()
                refreshStoryDecor()     // po souboji se strážcem / legendou
                // boss Hvozdu nastavil příznak → fáze questu se splní; po pádu Soulorda až po cutscéně (odměna od dubu pak navazuje)
                if (gamePrefs.getBoolean(cz.uhk.macroflow.pokemon.cutscene.StoryCutscenes.SOULORD_PENDING, false))
                    mapWorld.postDelayed({ if (!isFinishing && !maybePlayStoryCutscene { questManager.recheck() }) questManager.recheck() }, 300)
                else {
                    mapWorld.postDelayed({ if (!isFinishing) maybePlayStoryCutscene() }, 450)
                    questManager.recheck()
                }
                checkAwards()           // chycení, denní úkoly… (docs/adr/0037)
                maybeWhiteout()         // padl celý tým → smrt postavy (docs/adr/0052)
                // Spirra mohla v souboji dosáhnout levelu 12 → vývoj podle splněné cesty (docs/adr/0055)
                if (!gamePrefs.getBoolean(cz.uhk.macroflow.pokemon.zone.Whiteout.PENDING_KEY, false) && !cinematic)
                    mapWorld.postDelayed({
                        if (!isFinishing && !cinematic && !storyCutscenePlaying && supportFragmentManager.backStackEntryCount == 0)
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
            // Debug: energie (adb … --es debug_stamina 30+10)
            if (BuildConfig.DEBUG) intent.getStringExtra("debug_stamina")?.split("+")?.let { v ->
                val st = cz.uhk.macroflow.pokemon.stamina.StaminaStore
                st.debugSet(this, v[0].trim().toIntOrNull() ?: 100, v.getOrNull(1)?.trim()?.toIntOrNull() ?: 0)
                staminaBar.set(st.load(this), animate = false)
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
        // Aréna: souboje s trenéry a duchy hráčů, výměny (docs/adr/0078)
        findViewById<ImageButton>(R.id.btnArena).setOnClickListener {
            if (supportFragmentManager.backStackEntryCount == 0) { it.foreground = null; replaceMapContent(cz.uhk.macroflow.pokemon.trainer.ArenaFragment()) }
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
        refreshTeamColumn()
        cz.uhk.macroflow.pokemon.audio.GameAudio.resume(this)
        // Energie: nový den po aspoň 4 h pauze doplní bar (docs/adr/0065)
        staminaBar.set(cz.uhk.macroflow.pokemon.stamina.StaminaStore.onEnter(this), animate = false)
        collectStamina()
        refreshArenaBadge()
        startRestTimer()
        // AFK těžba / kácení: po návratu ukázat, co se za tu dobu udělalo (docs/adr/0035)
        mapWorld.postDelayed({ reportAfk() }, 700)
    }

    /** Červená tečka na tlačítku Arény, když někdo vyzval tvého ducha (docs/adr/0081). */
    private fun refreshArenaBadge() {
        val btn = findViewById<ImageButton>(R.id.btnArena)
        lifecycleScope.launch {
            val show = kotlinx.coroutines.withContext(Dispatchers.IO) { cz.uhk.macroflow.pokemon.trainer.Arena.hasNewReports(this@MakromonMapActivity) }
            btn.foreground = if (show) ArenaBadge(resources.displayMetrics.density) else null
        }
    }

    private class ArenaBadge(private val dp: Float) : android.graphics.drawable.Drawable() {
        private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5392F.toInt() }
        private val rim = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFEFAE0.toInt() }
        override fun draw(c: android.graphics.Canvas) {
            val x = bounds.right - 9 * dp; val y = bounds.top + 9 * dp
            c.drawCircle(x, y, 7 * dp, rim); c.drawCircle(x, y, 5.5f * dp, fill)
        }
        override fun setAlpha(a: Int) {}
        override fun setColorFilter(f: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** Energie za dnešní zápisy ve funkční části (tabulka A, docs/adr/0065); připíše jen nové. */
    private fun collectStamina() {
        val store = cz.uhk.macroflow.pokemon.stamina.StaminaStore
        lifecycleScope.launch {
            val day = kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { store.dayFacts(this@MakromonMapActivity) }.getOrNull() }
                ?: return@launch
            val before = store.load(this@MakromonMapActivity).total
            val s = store.collect(this@MakromonMapActivity, day)
            if (s.total > before) {
                staminaBar.set(s)
                showMapToast("⚡ +${s.total - before} energie za dnešní zápisy")
            }
        }
    }

    // ── Pauza mezi sériemi (docs/adr/0065, bod 5) ───────────────────────────
    private var lastSetAt = 0L
    private var lastSetExercise: String? = null
    private var restExtra = 0L
    private var restAlertedFor = 0L
    private val restPill by lazy { findViewById<View>(R.id.restPill) }
    private val restBgCount by lazy { cz.uhk.macroflow.pokemon.skills.ui.BevelDrawable(2f * resources.displayMetrics.density, 0xFF606C38.toInt(), 0xFF8C9C5B.toInt(), 0xFF283618.toInt(), 0xFF2E1B0E.toInt()) }
    private val restBgOver by lazy { cz.uhk.macroflow.pokemon.skills.ui.BevelDrawable(2f * resources.displayMetrics.density, 0xFFBC6C25.toInt(), 0xFFE9B072.toInt(), 0xFF7A4416.toInt(), 0xFF2E1B0E.toInt()) }
    private fun restBgMore() = cz.uhk.macroflow.pokemon.skills.ui.BevelDrawable(1.5f * resources.displayMetrics.density, 0xFFFEFAE0.toInt(), 0xFFFFFFFF.toInt(), 0xFFD7CFA8.toInt(), 0xFF2E1B0E.toInt())
    private val restTick = object : Runnable {
        override fun run() { renderRest(); restPill.postDelayed(this, 1000) }
    }

    private fun startRestTimer() {
        restPill.removeCallbacks(restTick)
        lifecycleScope.launch {
            val set = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { cz.uhk.macroflow.data.AppDatabase.getDatabase(this@MakromonMapActivity).workoutDao().lastSet() }.getOrNull()
            }
            val last = set?.createdAt ?: 0L
            lastSetExercise = set?.exerciseId
            // Debug: pauza, ze které zbývá N s (adb … --ei debug_rest_left 5)
            val dbg = if (BuildConfig.DEBUG) intent.getIntExtra("debug_rest_left", -1) else -1
            val use = if (dbg >= 0) System.currentTimeMillis() - cz.uhk.macroflow.common.AppSettings.restSecondsFor(this@MakromonMapActivity, lastSetExercise) * 1000L + dbg * 1000L else last
            if (use != lastSetAt) { lastSetAt = use; restExtra = 0 }
            restTick.run()
        }
    }

    /** Během pauzy odpočet; po jejím konci jantarová výzva „Zpět na trénink“ (+30 s jednou za pauzu). */
    private fun renderRest() {
        val left = cz.uhk.macroflow.training.QuickWorkout.restLeft(lastSetAt, System.currentTimeMillis(), restExtra,
            cz.uhk.macroflow.training.QuickWorkout.endedAt(this), cz.uhk.macroflow.common.AppSettings.restSecondsFor(this, lastSetExercise) * 1000L)
            ?.takeIf { cz.uhk.macroflow.common.AppSettings.restReminder(this) }
        if (left == null) { restPill.visibility = View.GONE; return }
        val tv = findViewById<TextView>(R.id.tvRest)
        val more = findViewById<TextView>(R.id.tvRestMore)
        restPill.visibility = View.VISIBLE
        if (left > 0) {
            val sec = (left + 999) / 1000
            tv.text = "⏱ Pauza ${sec / 60}:${String.format(java.util.Locale.US, "%02d", sec % 60)}"
            if (restPill.background !== restBgCount) restPill.background = restBgCount
            more.visibility = View.GONE
            restPill.setOnClickListener(null); restPill.isClickable = false
            return
        }
        tv.text = "Konec pauzy · Zpět ▸"
        if (restPill.background !== restBgOver) restPill.background = restBgOver
        more.visibility = if (restExtra == 0L) View.VISIBLE else View.GONE
        if (more.background == null) more.background = restBgMore()
        more.setOnClickListener { restExtra = 30_000; renderRest() }
        restPill.setOnClickListener {
            it.performHapticFeedback(cz.uhk.macroflow.common.Haptics.CONFIRM)
            finish()
        }
        if (restAlertedFor != lastSetAt + restExtra) {
            restAlertedFor = lastSetAt + restExtra
            restPill.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            restPill.scaleX = 0.7f; restPill.scaleY = 0.7f
            restPill.animate().scaleX(1f).scaleY(1f).setInterpolator(android.view.animation.OvershootInterpolator(3f)).setDuration(420).start()
        }
    }

    private var exiting = false

    /** Každý odchod z Makrosvěta (tlačítko, zpět, „Zpět na trénink“) jde přes scénu (docs/adr/0070). */
    override fun finish() {
        if (exiting) return            // scéna odchodu už běží
        if (isFinishing) return super.finish()
        exiting = true
        PortalTransitionView.cover(findViewById(android.R.id.content), exit = true) {
            PortalTransitionView.pendingExitReveal = true
            super.finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onPause() {
        restPill.removeCallbacks(restTick)
        cz.uhk.macroflow.pokemon.audio.GameAudio.pause()
        cz.uhk.macroflow.pokemon.stamina.StaminaStore.onExit(this)
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
                holdDone = false
                val root = findViewById<FrameLayout>(R.id.mapRootContainer)
                if (!touchDownOnUi && !questDialogManager.isVisible() && !cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.isOpen(root) &&
                    !cz.uhk.macroflow.pokemon.inspect.InspectPanel.isOpen(root))
                    inspectableAt(event.rawX, event.rawY)?.let { startHold(it, event.rawX, event.rawY) }
                if (!hudMenuOpen()) {
                    val r = android.graphics.Rect()
                    hudViews.firstOrNull { it.isShown && it.getGlobalVisibleRect(r) && r.contains(event.rawX.toInt(), event.rawY.toInt()) }
                        ?.let { hudFocus = it; it.animate().alpha(1f).setDuration(200).start() }
                }
            }
            MotionEvent.ACTION_MOVE -> if (holdRing != null || holdAnim != null) {
                val slop = android.view.ViewConfiguration.get(this).scaledTouchSlop
                if (kotlin.math.hypot(event.rawX - touchDownX, event.rawY - touchDownY) > slop * 2) cancelHold()
            }
            MotionEvent.ACTION_CANCEL -> cancelHold()
            MotionEvent.ACTION_UP -> {
                if (holdDone) { holdDone = false; return true }           // podržení otevřelo panel – žádná chůze
                cancelHold()
                if (hudFocus != null) mapWorld.postDelayed({ if (!isFinishing) syncHud() }, 700)   // nic se neotevřelo → zpět do průsvitu
                if (!touchDownOnUi && handleMapTap(event)) return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    /** Tlačítka, parťák, ukazatel kroků – klepnutí na ně nesmí poslat postavu na procházku. */
    private fun isOverUi(rawX: Float, rawY: Float): Boolean {
        val ui = listOf(
            findViewById<View>(R.id.btnExitMap)?.parent as? View,
            findViewById<View>(R.id.btnOpenJournal),
            staminaBar,
            restPill,
            findViewById<View>(R.id.companionBox),
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
        if (cz.uhk.macroflow.pokemon.inspect.InspectPanel.isOpen(findViewById(R.id.mapRootContainer))) return false
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
                if (payStamina(cz.uhk.macroflow.pokemon.stamina.Stamina.Action.GUARDIAN))
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
            // Cesta zpátky (docs/adr/0108): klepnutí na lucernu u uzlu ji zapálí místo běžné akce místa
            if (pendingCollect == nodeName) { pendingCollect = null
                cz.uhk.macroflow.pokemon.story.Collectibles.at(currentBiome.name, nodeName)?.let { onCollect(it); return@action } }
            if (currentBiome == BiomeType.OZVENA && (nodeName == cz.uhk.macroflow.pokemon.cave.OzvenaCity.LIO_NODE &&
                    !cz.uhk.macroflow.pokemon.story.MemoryBookStore.isGone(this, nodeName) || nodeName == cz.uhk.macroflow.pokemon.cave.OzvenaCity.BOARD_NODE &&
                    cz.uhk.macroflow.pokemon.story.MemoryBookStore.isGone(this, cz.uhk.macroflow.pokemon.cave.OzvenaCity.LIO_NODE)) &&
                onLetters(atBoard = nodeName == cz.uhk.macroflow.pokemon.cave.OzvenaCity.BOARD_NODE)) return@action
            if (pendingLantern == nodeName) { pendingLantern = null
                cz.uhk.macroflow.pokemon.story.Lanterns.at(currentBiome.name, nodeName)?.let { onLantern(it); return@action } }
            // Zahradník Fenn (docs/adr/0096): stopy ke jménům stromů – úly a jezírko mají přednost před setkáním
            if (currentBiome == BiomeType.QUIET_ORCHARD) fennClueHere(nodeName)?.let { onFennClue(it); return@action }
            // Nelle odpoví, kdo sbírá jablka – fáze „Teplý košík“ je splněná a dál mluví ona
            if (nodeName == cz.uhk.macroflow.pokemon.cave.OzvenaCity.LIDA_NODE &&
                StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Fenn.STARTED_KEY) && !StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Fenn.NELLE_KEY)) {
                questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Fenn.QUEST_ID)
                mapWorld.postDelayed({
                    if (isFinishing) return@postDelayed
                    StoryFlags.set(this, cz.uhk.macroflow.pokemon.story.Fenn.NELLE_KEY); questManager.recheck()
                }, 200)
                return@action
            }
            if (nodeName == cz.uhk.macroflow.pokemon.cave.OzvenaCity.LIDA_NODE && cz.uhk.macroflow.pokemon.story.Fenn.CLUE_KEYS.all { StoryFlags.isSet(this, it) } &&
                !StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Fenn.NELLE_TOLD_KEY)) { onNelleMaren(); return@action }
            // Stará jabloň po konci B/C v noci svítí a dává Zlaté jablko
            if (currentBiome == BiomeType.QUIET_ORCHARD && nodeName == "stara_jablon" && orchardNight() &&
                cz.uhk.macroflow.pokemon.story.Fenn.inTree { StoryFlags.isSet(this, it) }) { giveDailyApple(cz.uhk.macroflow.pokemon.story.Fenn.APPLE_FROM_TREE, cz.uhk.macroflow.pokemon.story.Fenn.TREE_AFTER); return@action }
            // Následky konců (docs/adr/0115): postavy s hotovým příběhem mluví po konci jinak
            aftermathNpc(nodeName)?.let { showMapToast(it); return@action }

            when (nodeName) {
                "gudwin", "meadow_npc", "kral_mlsak" -> questManager.checkNpcInteraction()
                "cave", "mine" -> BiomeRegistry.caveBehind(nodeName)?.let { cave ->
                    val entry = BiomeRegistry.definition(cave)?.cave?.exitNode ?: return@let
                    walkInto(cave, entry, MapTransition.CAVE_IN)
                }
                // Doly (docs/adr/0049): zabedněná štola ve Starém dole → sestup; zpět tunelem do dolu
                MinesMap.MAZE_NODE -> walkInto(BiomeType.MINES, MinesMap.EXIT_NODE, MapTransition.MINE_DESCENT)
                MinesMap.EXIT_NODE -> walkInto(BiomeType.CAVE_MAZE, MinesMap.MAZE_NODE, MapTransition.CAVE_IN)
                MinesMap.NET_NODE -> onOldNet()
                MinesMap.DOOR_NODE -> onInspectTap()
                Vendelin.NODE -> { questManager.loadQuest(Vendelin.QUEST_ID); mapWorld.postDelayed({ if (!isFinishing) questManager.checkNpcInteraction() }, 150) }
                Vendelin.BOOK_NODE -> onMinerBook()
                "vychod_jeskyne", "vychod_dolu", "vstup_z_louky", "vstup_ze_svatyne", "vstup_z_hvozdu", cz.uhk.macroflow.pokemon.cave.AshPlain.ENTRY_NODE, cz.uhk.macroflow.pokemon.cave.QuietOrchard.ENTRY_NODE, cz.uhk.macroflow.pokemon.cave.EchoCanyon.ENTRY_NODE, cz.uhk.macroflow.pokemon.cave.OzvenaCity.ENTRY_NODE, cz.uhk.macroflow.pokemon.cave.ArenaHall.ENTRY_NODE -> BiomeRegistry.definition(currentBiome)?.cave?.let {
                    walkInto(BiomeType.valueOf(it.parentBiome), it.mountainNode,
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
                SecretGrove.POOL_NODE -> onInspectTap()
                SecretGrove.ALTAR_NODE -> onGroveAltar()
                SecretGrove.GRAVE_NODE -> onGroveGrave()
                "krystal_modry", "krystal_cerveny" -> BiomeRegistry.definition(currentBiome)?.cave?.let { onCrystalNode(it) }
                "peak" -> onShrine()
                SkyPass.GATE_NODE -> onWorldGate()
                SkyPass.CLAWS_NODE, SkyPass.CAIRN_NODE -> onInspectTap()
                SkyPass.VISTA_NODE -> vistaShot()
                // Popelavá pláň (docs/adr/0088)
                cz.uhk.macroflow.pokemon.cave.AshPlain.SIGN_NODE, cz.uhk.macroflow.pokemon.cave.AshPlain.MILESTONE_NODE -> onInspectTap()
                cz.uhk.macroflow.pokemon.cave.AshPlain.CITY_GATE_NODE -> walkInto(BiomeType.OZVENA, cz.uhk.macroflow.pokemon.cave.OzvenaCity.ENTRY_NODE, MapTransition.FADE)
                // Město Ozvěna a hala Arény (docs/adr/0092)
                cz.uhk.macroflow.pokemon.cave.OzvenaCity.ARENA_NODE -> walkInto(BiomeType.ARENA_HALL, cz.uhk.macroflow.pokemon.cave.ArenaHall.ENTRY_NODE, MapTransition.FADE)
                // Kapitola V · OZVĚNA (docs/adr/0095): Brann ji začíná, Vessa dává Pamětní knihu
                cz.uhk.macroflow.pokemon.cave.OzvenaCity.BAKERY_NODE, cz.uhk.macroflow.pokemon.cave.OzvenaCity.VENKA_NODE -> {
                    questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Ozvena.QUEST_ID)
                    mapWorld.postDelayed({ if (!isFinishing) onOzvenaNpc(nodeName == cz.uhk.macroflow.pokemon.cave.OzvenaCity.VENKA_NODE) }, 150)
                }
                cz.uhk.macroflow.pokemon.cave.OzvenaCity.BOARD_NODE, cz.uhk.macroflow.pokemon.cave.OzvenaCity.FOUNTAIN_NODE -> onInspectTap()
                // Věž Řádu Váhy (docs/adr/0103): s Pozvánkou se otevře (poprvé cutscéna dveří)
                cz.uhk.macroflow.pokemon.cave.OzvenaCity.TOWER_NODE -> if (StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Masters.INVITE_KEY) || coupon()) enterTower()
                    else showMapToast(cz.uhk.macroflow.pokemon.cave.OzvenaCity.TOWER_TEXT)
                cz.uhk.macroflow.pokemon.cave.TowerLobby.SEPT_NODE -> onSept()
                cz.uhk.macroflow.pokemon.cave.TowerLobby.ELEVATOR_NODE -> onElevator()
                // Archiv okruhů – 1. patro Věže (Spis 212)
                cz.uhk.macroflow.pokemon.cave.TowerArchive.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerMeasure.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerPlain.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerForest.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerRock.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerCave.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerWater.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerDorm.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerIce.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerFire.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerOffice.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerFairy.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerPoison.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerHideout.ELEVATOR_NODE, cz.uhk.macroflow.pokemon.cave.TowerAbyss.ELEVATOR_NODE -> onElevator()
                // Kapitola V ve Věži (docs/adr/0104): stopy příběhu a útržky Hunfreyho mapy na patrech
                in cz.uhk.macroflow.pokemon.story.TowerStory.TAP_NODES -> onTowerClue(nodeName)
                in cz.uhk.macroflow.pokemon.cave.TowerHideout.TEXTS.keys -> onInspectTap()
                cz.uhk.macroflow.pokemon.cave.TowerAbyss.VIEW_NODE -> onAbyssView()
                cz.uhk.macroflow.pokemon.cave.TowerAbyss.CAGE_NODE -> onAbyssCage()
                // Zóna 3 – Tábor výprav (docs/adr/0106): klec zpět nahoru, jinak texty míst
                cz.uhk.macroflow.pokemon.cave.RootCamp.CAGE_NODE -> walkInto(BiomeType.TOWER_ABYSS, cz.uhk.macroflow.pokemon.cave.TowerAbyss.CAGE_NODE)
                cz.uhk.macroflow.pokemon.cave.RootCamp.DOWN_NODE -> onStationBarrier()
                cz.uhk.macroflow.pokemon.cave.RootWatch.ENTRY_NODE -> walkInto(BiomeType.ROOT_CAMP, cz.uhk.macroflow.pokemon.cave.RootCamp.DOWN_NODE)
                cz.uhk.macroflow.pokemon.cave.RootWatch.RAVINE_NODE -> {
                    StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.RootSignal.VISITED_KEY)
                    walkInto(BiomeType.ROOT_SIGNAL, cz.uhk.macroflow.pokemon.cave.RootSignal.ENTRY_NODE)
                }
                cz.uhk.macroflow.pokemon.cave.RootSignal.ENTRY_NODE -> walkInto(BiomeType.ROOT_WATCH, cz.uhk.macroflow.pokemon.cave.RootWatch.RAVINE_NODE)
                cz.uhk.macroflow.pokemon.story.Signal.NODE -> onKesh()
                "su_lanovka" -> onSignalCable()
                // Kořenový most (vedlejší, docs/adr/0106): gondola zpět, Orrinovy pásky, ventil
                cz.uhk.macroflow.pokemon.cave.RootBridge.ENTRY_NODE -> walkInto(BiomeType.ROOT_SIGNAL, "su_lanovka")
                cz.uhk.macroflow.pokemon.story.NightShift.TAPE_NODE -> onTapeRecorder()
                cz.uhk.macroflow.pokemon.story.NightShift.VALVE_NODE -> onFoamValve()
                cz.uhk.macroflow.pokemon.story.NightShift.SHRINE_NODE -> onHiddenReel(nodeName, cz.uhk.macroflow.pokemon.story.NightShift.REEL_SHRINE_KEY, cz.uhk.macroflow.pokemon.story.NightShift.REEL_SHRINE)
                cz.uhk.macroflow.pokemon.story.NightShift.SCAFFOLD_NODE -> onHiddenReel(nodeName, cz.uhk.macroflow.pokemon.story.NightShift.REEL_SCAFFOLD_KEY, cz.uhk.macroflow.pokemon.story.NightShift.REEL_SCAFFOLD)
                in cz.uhk.macroflow.pokemon.cave.RootBridge.TEXTS.keys -> onInspectTap()
                cz.uhk.macroflow.pokemon.story.Signal.LAMP_NODE -> onSignalLamp()
                in cz.uhk.macroflow.pokemon.cave.RootSignal.TEXTS.keys -> onInspectTap()
                cz.uhk.macroflow.pokemon.cave.RootWatch.DOWN_NODE -> {
                    StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.RootSlopeCamp.VISITED_KEY)
                    walkInto(BiomeType.ROOT_SLOPECAMP, cz.uhk.macroflow.pokemon.cave.RootSlopeCamp.ENTRY_NODE)
                }
                cz.uhk.macroflow.pokemon.cave.RootSlopeCamp.ENTRY_NODE -> walkInto(BiomeType.ROOT_WATCH, cz.uhk.macroflow.pokemon.cave.RootWatch.DOWN_NODE)
                cz.uhk.macroflow.pokemon.cave.RootSlopeCamp.DOWN_NODE -> {
                    StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.RootNameless.VISITED_KEY)
                    walkInto(BiomeType.ROOT_NAMELESS, cz.uhk.macroflow.pokemon.cave.RootNameless.ENTRY_NODE)
                }
                cz.uhk.macroflow.pokemon.cave.RootNameless.ENTRY_NODE -> walkInto(BiomeType.ROOT_SLOPECAMP, cz.uhk.macroflow.pokemon.cave.RootSlopeCamp.DOWN_NODE)
                "sb_les" -> {
                    StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.RootForest.VISITED_KEY)
                    walkInto(BiomeType.ROOT_FOREST, cz.uhk.macroflow.pokemon.cave.RootForest.ENTRY_NODE)
                }
                cz.uhk.macroflow.pokemon.cave.RootForest.ENTRY_NODE -> walkInto(BiomeType.ROOT_NAMELESS, "sb_les")
                cz.uhk.macroflow.pokemon.cave.RootForest.THRESHOLD_NODE -> {
                    StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.RootThreshold.VISITED_KEY)
                    walkInto(BiomeType.ROOT_THRESHOLD, cz.uhk.macroflow.pokemon.cave.RootThreshold.ENTRY_NODE)
                }
                // Práh (docs/adr/0106): aréna posledního souboje s Drakirrou
                cz.uhk.macroflow.pokemon.cave.RootThreshold.ENTRY_NODE -> walkInto(BiomeType.ROOT_FOREST, cz.uhk.macroflow.pokemon.cave.RootForest.THRESHOLD_NODE)
                cz.uhk.macroflow.pokemon.cave.RootThreshold.DRAKIRRA_NODE -> onDrakirraFinal()
                cz.uhk.macroflow.pokemon.cave.RootThreshold.BRANCH_NODE ->
                    if (StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.DrakirraFinal.DEFEATED_KEY) || devKeycard()) {
                        StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.RootTrunk.VISITED_KEY)
                        walkInto(BiomeType.ROOT_TRUNK, cz.uhk.macroflow.pokemon.cave.RootTrunk.ENTRY_NODE)
                    } else showMapToast(cz.uhk.macroflow.pokemon.story.DrakirraFinal.trunkText(false))
                // Kmen (docs/adr/0106): Trpitel ve Stromu, volba konce
                cz.uhk.macroflow.pokemon.cave.RootTrunk.ENTRY_NODE -> walkInto(BiomeType.ROOT_THRESHOLD, cz.uhk.macroflow.pokemon.cave.RootThreshold.BRANCH_NODE)
                cz.uhk.macroflow.pokemon.cave.RootTrunk.TRPITEL_NODE -> onTrpitel()
                cz.uhk.macroflow.pokemon.cave.RootTrunk.CRACK_NODE -> if (trpitelEnding() != null || devKeycard()) {
                    StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.RootHeart.VISITED_KEY)
                    walkInto(BiomeType.ROOT_HEART, cz.uhk.macroflow.pokemon.cave.RootHeart.ENTRY_NODE)
                } else showMapToast(cz.uhk.macroflow.pokemon.story.Trpitel.crack(false))
                // Střed kořenů (docs/adr/0110): poslední světlo, mechové lůžko, dohrání konce
                cz.uhk.macroflow.pokemon.cave.RootHeart.ENTRY_NODE -> walkInto(BiomeType.ROOT_TRUNK, cz.uhk.macroflow.pokemon.cave.RootTrunk.CRACK_NODE)
                cz.uhk.macroflow.pokemon.cave.RootHeart.LIGHT_NODE -> onLastLight()
                cz.uhk.macroflow.pokemon.cave.RootHeart.MOSS_NODE -> onMossBed()
                in cz.uhk.macroflow.pokemon.cave.RootHeart.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.RootTrunk.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.RootThreshold.TEXTS.keys -> onInspectTap()
                cz.uhk.macroflow.pokemon.cave.RootForest.GUDWIN_NODE -> onForestGudwin()
                in cz.uhk.macroflow.pokemon.cave.RootForest.TAG_NODES -> onForestTag(nodeName)
                in cz.uhk.macroflow.pokemon.cave.RootForest.TEXTS.keys -> onInspectTap()
                "sb_chram" -> {
                    StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.RootTemple.VISITED_KEY)
                    walkInto(BiomeType.ROOT_TEMPLE, cz.uhk.macroflow.pokemon.cave.RootTemple.ENTRY_NODE)
                }
                cz.uhk.macroflow.pokemon.cave.RootTemple.ENTRY_NODE -> walkInto(BiomeType.ROOT_NAMELESS, "sb_chram")
                in cz.uhk.macroflow.pokemon.cave.RootTemple.TEXTS.keys -> onInspectTap()
                // Kořeny, které pijí (docs/adr/0115): lať začíná úkol, u devíti z V7 se čtou jména
                cz.uhk.macroflow.pokemon.story.SlopeWater.WATER_NODE -> onSlopeWater()
                cz.uhk.macroflow.pokemon.story.SlopeWater.V7_NODE -> onSlopeV7()
                in cz.uhk.macroflow.pokemon.cave.RootNameless.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.RootSlopeCamp.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.RootWatch.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.RootCamp.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerAbyss.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerDorm.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerIce.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerFire.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerOffice.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerFairy.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerPoison.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerWater.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerCave.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerRock.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerForest.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerPlain.TEXTS.keys -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.TowerMeasure.TEXTS.keys -> onInspectTap()
                cz.uhk.macroflow.pokemon.cave.TowerArchive.FILE_NODE -> onFile212()
                in cz.uhk.macroflow.pokemon.cave.TowerArchive.TEXTS.keys -> onInspectTap()
                cz.uhk.macroflow.pokemon.cave.TowerLobby.WAITING_NODE, cz.uhk.macroflow.pokemon.cave.TowerLobby.FILES_NODE,
                cz.uhk.macroflow.pokemon.cave.TowerLobby.FILES2_NODE, cz.uhk.macroflow.pokemon.cave.TowerLobby.PORTRAITS_NODE -> onInspectTap()
                cz.uhk.macroflow.pokemon.cave.OzvenaCity.LIFT_NODE -> onCityLift()
                cz.uhk.macroflow.pokemon.cave.TowerAbyss.LIFT_NODE -> walkInto(BiomeType.OZVENA, cz.uhk.macroflow.pokemon.cave.OzvenaCity.LIFT_NODE)
                cz.uhk.macroflow.pokemon.cave.OzvenaCity.SHED_NODE -> onInspectTap()
                in cz.uhk.macroflow.pokemon.cave.OzvenaCity.RESIDENT_NODES -> when {
                    nodeName == cz.uhk.macroflow.pokemon.cave.OzvenaCity.KORINEK_NODE && StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Fenn.END_A_KEY) -> showMapToast(cz.uhk.macroflow.pokemon.story.Fenn.TOBBEN_GONE)
                    nodeName == cz.uhk.macroflow.pokemon.cave.OzvenaCity.LIDA_NODE && StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Fenn.NELLE_TRUTH_KEY) -> showMapToast(cz.uhk.macroflow.pokemon.story.Fenn.NELLE_AFTER_TRUTH)
                    cz.uhk.macroflow.pokemon.story.MemoryBookStore.isGone(this, nodeName) -> cz.uhk.macroflow.pokemon.story.MemoryBook.TRACES[nodeName]?.let { showMapToast(it) }
                    else -> cz.uhk.macroflow.pokemon.cave.OzvenaCity.residentLine(nodeName, java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR),
                        cz.uhk.macroflow.pokemon.story.MemoryBookStore.isGone(this, cz.uhk.macroflow.pokemon.cave.OzvenaCity.PIP_NODE))?.let { showMapToast(it) }
                }
                cz.uhk.macroflow.pokemon.cave.ArenaHall.GATE_NODE -> replaceMapContent(cz.uhk.macroflow.pokemon.trainer.ArenaFragment())
                cz.uhk.macroflow.pokemon.cave.ArenaHall.COMMANDER_NODE -> showCommander(0)
                cz.uhk.macroflow.pokemon.cave.ArenaHall.FAME_NODE -> showHallOfFame()
                cz.uhk.macroflow.pokemon.cave.ArenaHall.WANTED_NODE -> onInspectTap()
                // Pět Mistrů (docs/adr/0102) – až když má hráč Pamětní knihu od Vessy
                cz.uhk.macroflow.pokemon.cave.ArenaHall.TRAINING_NODE -> if (cz.uhk.macroflow.pokemon.story.MemoryBookStore.hasBook(this)) showMasters()
                    else showMapToast(cz.uhk.macroflow.pokemon.cave.ArenaHall.TRAINING_TEXT)
                // Ztichlý sad (docs/adr/0089) – jen v noci
                cz.uhk.macroflow.pokemon.cave.QuietOrchard.PLAIN_NODE -> walkInto(BiomeType.QUIET_ORCHARD, cz.uhk.macroflow.pokemon.cave.QuietOrchard.ENTRY_NODE, MapTransition.FADE)
                // Ozvěnový kaňon (docs/adr/0090)
                cz.uhk.macroflow.pokemon.cave.EchoCanyon.PLAIN_NODE -> walkInto(BiomeType.ECHO_CANYON, cz.uhk.macroflow.pokemon.cave.EchoCanyon.ENTRY_NODE, MapTransition.FADE)
                cz.uhk.macroflow.pokemon.cave.EchoCanyon.BRIDGE_NODE, cz.uhk.macroflow.pokemon.cave.EchoCanyon.BONES_NODE,
                cz.uhk.macroflow.pokemon.cave.EchoCanyon.WALL_NODE, cz.uhk.macroflow.pokemon.cave.QuietOrchard.WELL_NODE -> onInspectTap()
                cz.uhk.macroflow.pokemon.cave.QuietOrchard.HUT_NODE -> onOrchardHut()
                cz.uhk.macroflow.pokemon.story.Fenn.NODE -> if (orchardNight() && StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Fenn.MET_KEY) &&
                    !cz.uhk.macroflow.pokemon.story.Fenn.inTree { StoryFlags.isSet(this, it) }) onFennTalk() else showMapToast(cz.uhk.macroflow.pokemon.story.Fenn.DAY_SPOT)
                // Hunfrey na plošině vedle brány (docs/adr/0085)
                cz.uhk.macroflow.pokemon.story.Hunfrey.NODE -> {
                    questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Hunfrey.QUEST_ID)
                    mapWorld.postDelayed({ if (!isFinishing) questManager.checkNpcInteraction() }, 150)
                }
                "camp" -> restAtCamp()
                "rozcesti_hory" -> onInspectTap()
                "starter_bush" -> {
                    // Úvodní keřík je „vynucené“ setkání jen do konce úvodního úkolu; potom je to
                    // obyčejné městské křoví (se shiny šancí) – dřív zůstal vynucený navždy a shiny
                    // ve městě nikdy nepadl
                    val intro = !questManager.isIntroQuestFinished()
                    if (!intro && !payStamina(cz.uhk.macroflow.pokemon.stamina.Stamina.Action.WILD_BATTLE)) return@action
                    getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit()
                        .putString("LAST_BIOME", currentBiome.name)
                        .apply { if (intro) putString("FORCE_ENCOUNTER_ID", "starter_bush") else remove("FORCE_ENCOUNTER_ID") }
                        .apply()
                    replaceMapContent(PokemonBattleFragment())
                }
                "hory" -> tryEnterBiome(BiomeType.MOUNTAINS, entryNode = "vstup_z_meadow")
                "vstup_z_meadow" -> walkInto(BiomeType.MEADOW, "hory")
                "les" -> {
                    if (!questManager.isIntroQuestFinished()) {
                        movementEngine.cancel()
                        showBlockingDialog()
                    } else {
                        payMove(BiomeType.MEADOW) { changeBiome(BiomeType.MEADOW, PointF(0.340f, 0.640f)) }
                    }
                }
                "domov" -> replaceMapContent(InventoryFragment())
                "pokedex" -> replaceMapContent(MakrodexFragment())
                "obchod" -> replaceMapContent(PokemonShopFragment())
                "vstup_z_town" -> payMove(BiomeType.TOWN) { changeBiome(BiomeType.TOWN, PointF(0.46f, 0.15f)) }
                in encounterNodes -> {
                    if (!payStamina(cz.uhk.macroflow.pokemon.stamina.Stamina.Action.WILD_BATTLE)) return@action
                    if ((1..100).random() <= when (currentBiome) { BiomeType.TOWER_ICE -> cz.uhk.macroflow.pokemon.cave.TowerIce.ENCOUNTER_CHANCE; BiomeType.TOWER_POISON -> cz.uhk.macroflow.pokemon.cave.TowerPoison.ENCOUNTER_CHANCE; else -> 90 }) {
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

    private fun emptyEncounterText(node: String): String = cz.uhk.macroflow.pokemon.cave.AshPlain.emptyEncounterText(node) ?: cz.uhk.macroflow.pokemon.cave.QuietOrchard.emptyEncounterText(node) ?: cz.uhk.macroflow.pokemon.cave.EchoCanyon.emptyEncounterText(node) ?: when (node) {
        in cz.uhk.macroflow.pokemon.cave.TowerIce.DRIFT_NODES -> cz.uhk.macroflow.pokemon.cave.TowerIce.EMPTY_TEXT
        in cz.uhk.macroflow.pokemon.cave.TowerFire.ASH_NODES -> cz.uhk.macroflow.pokemon.cave.TowerFire.EMPTY_TEXT
        in cz.uhk.macroflow.pokemon.cave.TowerFairy.RING_NODES -> cz.uhk.macroflow.pokemon.cave.TowerFairy.EMPTY_TEXT
        in cz.uhk.macroflow.pokemon.cave.TowerPoison.PUDDLE_NODES -> cz.uhk.macroflow.pokemon.cave.TowerPoison.EMPTY_TEXT
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
        val boots = BuildConfig.DEBUG && gamePrefs.getBoolean(DEBUG_BOOTS_KEY, false) || coupon()
        if (boots || BiomeAccess.canEnter(target, currentDailySteps)) {
            walkInto(target, entryNode)
        } else {
            showStepWarningToast(BiomeAccess.missingSteps(target, currentDailySteps))
        }
    }

    private fun enterBiomeAtNode(target: BiomeType, nodeId: String, transition: MapTransition = MapTransition.FADE) {
        val graph = BiomeRegistry.definition(target)?.graph ?: return
        val pos = BiomeRegistry.nodePos(graph, nodeId) ?: return
        changeBiome(target, PointF(pos.x, pos.y), transition)
    }

    // ─── ENERGIE (docs/adr/0065) ─────────────────────────────────────────────

    /** Zaplatí [cost] energie; když nestačí, řekne kolik chybí a vrátí false. */
    private fun payStamina(cost: Int, what: String): Boolean {
        if (cost <= 0) return true
        val paid = cz.uhk.macroflow.pokemon.stamina.StaminaStore.pay(this, cost)
        if (paid == null) {
            val have = cz.uhk.macroflow.pokemon.stamina.StaminaStore.load(this).total
            showMapToast("⚡ Na $what ti chybí energie (stojí $cost, máš $have).\n" +
                "Doplníš ji zápisem jídla, vody nebo tréninku, nebo zítra po odpočinku.")
            return false
        }
        staminaBar.set(paid)
        return true
    }

    private fun payStamina(action: cz.uhk.macroflow.pokemon.stamina.Stamina.Action) =
        payStamina(action.cost, action.label.lowercase())

    /** Placený přechod hráče do [target]; návrat odkud přišel je do 5 minut zdarma. */
    private fun payMove(target: BiomeType, go: () -> Unit) {
        val st = cz.uhk.macroflow.pokemon.stamina.Stamina
        val store = cz.uhk.macroflow.pokemon.stamina.StaminaStore
        val now = System.currentTimeMillis()
        if (coupon()) { go(); return }                                       // Zlatý kupón: přechody bez výdrže
        val cost = st.transitionCost(store.load(this), target.name, now)
        if (!payStamina(cost, "přechod")) return
        store.save(this, st.moved(store.load(this), from = currentBiome.name, cost = cost, now = now))
        go()
    }

    private fun walkInto(target: BiomeType, nodeId: String, transition: MapTransition = MapTransition.FADE) =
        payMove(target) { enterBiomeAtNode(target, nodeId, transition) }

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
            if (newBiome == BiomeType.ASH_PLAIN) StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.AshPlain.VISITED_KEY)
            if (newBiome == BiomeType.QUIET_ORCHARD) StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.QuietOrchard.VISITED_KEY)
            if (newBiome == BiomeType.ECHO_CANYON) StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.EchoCanyon.VISITED_KEY)
            if (newBiome == BiomeType.OZVENA) StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.OzvenaCity.VISITED_KEY)
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
                val night = def.nightRes != null && cz.uhk.macroflow.pokemon.cave.QuietOrchard.isNight(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
                val burned = newBiome == BiomeType.TOWER_OFFICE && StoryFlags.isSet(this@MakromonMapActivity, cz.uhk.macroflow.pokemon.story.TowerStory.OBJ019_KEY)
                mapBackground.setImageResource(if (night) def.nightRes!! else if (burned) R.drawable.tower_office_burned else def.backgroundRes)
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
    /** Coil s podporou GIFů – portréty v dialozích a animovaní NPC na mapě (Hunfrey). */
    private lateinit var imageLoader: ImageLoader

    /** Postup příběhu krystalů (příznaky v GamePrefs + krystaly v inventáři). */
    private suspend fun loadLegend(): LegendProgress = kotlinx.coroutines.withContext(Dispatchers.IO) {
        LegendProgress.load({ gamePrefs.getBoolean(it, false) }, { db.userItemDao().getItemCount(it) ?: 0 })
    }

    /** Dekorace mapy podle stavu příběhu – po změně biomu i po návratu ze souboje. */
    /** Věta postavy po dohraném konci (story/Aftermath), nebo null. */
    private fun aftermathNpc(node: String): String? {
        val end = cz.uhk.macroflow.pokemon.story.Finale.played { StoryFlags.isSet(this, it) } ?: return null
        return cz.uhk.macroflow.pokemon.story.Aftermath.npc(end, currentBiome.name, node) { StoryFlags.isSet(this, it) }
    }

    /** Po dohraném konci: při prvním příchodu na místo jednou řekne, co je jinak. */
    private fun showAftermathArrival() {
        val A = cz.uhk.macroflow.pokemon.story.Aftermath
        val end = cz.uhk.macroflow.pokemon.story.Finale.played { StoryFlags.isSet(this, it) } ?: return
        val seen = gamePrefs.getStringSet(A.SEEN_KEY, emptySet()).orEmpty()
        val line = A.arrival(end, currentBiome.name, seen) ?: return
        gamePrefs.edit().putStringSet(A.SEEN_KEY, seen + currentBiome.name).apply()
        val biome = currentBiome
        mapWorld.postDelayed({ if (!isFinishing && currentBiome == biome) showMapToast(line) }, 900)
    }

    private fun refreshStoryDecor() {
        lifecycleScope.launch {
            showAftermathArrival()
            val progress = loadLegend()
            clearDecor()
            val cave = BiomeRegistry.definition(currentBiome)?.cave
            placeExitMarkers()
            placeInspectGlitter()
            when {
                currentBiome == BiomeType.SKY_PASS -> { placeSkyPassDecor(); placeHunfrey() }
                currentBiome == BiomeType.FOREST -> { placeGatherSpots(); placeForestStory(progress.legendFaced) }
                currentBiome == BiomeType.HIDDEN_GROVE -> placeGroveDecor()
                // místa setkání = vysoká tráva v mapě; přes ni padá popel a z ohniště jiskří
                currentBiome == BiomeType.ASH_PLAIN -> if (worldScale > 0) placeAshGateVeil().also { addGroundDecor(cz.uhk.macroflow.pokemon.cave.AshFxView(this@MakromonMapActivity, worldScale).apply {
                    layoutParams = FrameLayout.LayoutParams(cz.uhk.macroflow.pokemon.cave.AshPlain.MAP.artW * worldScale, cz.uhk.macroflow.pokemon.cave.AshPlain.MAP.artH * worldScale)
                }) }
                currentBiome == BiomeType.QUIET_ORCHARD -> if (worldScale > 0) placeFenn().also { addGroundDecor(cz.uhk.macroflow.pokemon.cave.OrchardFxView(this@MakromonMapActivity, worldScale,
                    cz.uhk.macroflow.pokemon.cave.QuietOrchard.isNight(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))).apply {
                    layoutParams = FrameLayout.LayoutParams(cz.uhk.macroflow.pokemon.cave.QuietOrchard.MAP.artW * worldScale, cz.uhk.macroflow.pokemon.cave.QuietOrchard.MAP.artH * worldScale)
                }) }
                currentBiome == BiomeType.ARENA_HALL -> if (worldScale > 0) { placeHallPortraits(); addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.HALL) }
                // živé interiéry Věže: lampy, hvězdy v oknech, kyvadlo, myš, potrubní pošta
                currentBiome == BiomeType.TOWER_LOBBY -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.LOBBY)
                currentBiome == BiomeType.TOWER_ARCHIVE -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ARCHIVE)
                currentBiome == BiomeType.TOWER_MEASURE -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.MEASURE)
                currentBiome == BiomeType.TOWER_PLAIN -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.PLAIN)
                currentBiome == BiomeType.TOWER_FOREST -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.FOREST)
                currentBiome == BiomeType.TOWER_ROCK -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ROCK)
                currentBiome == BiomeType.TOWER_CAVE -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.CAVE)
                currentBiome == BiomeType.TOWER_WATER -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.WATER)
                currentBiome == BiomeType.TOWER_DORM -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.DORM)
                currentBiome == BiomeType.TOWER_ICE -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ICE)
                currentBiome == BiomeType.TOWER_FIRE -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.FIRE)
                currentBiome == BiomeType.TOWER_OFFICE -> if (worldScale > 0) addInteriorFx(if (StoryFlags.isSet(this@MakromonMapActivity, cz.uhk.macroflow.pokemon.story.TowerStory.OBJ019_KEY)) cz.uhk.macroflow.pokemon.cave.InteriorFxView.OFFICE_BURNED else cz.uhk.macroflow.pokemon.cave.InteriorFxView.OFFICE)
                currentBiome == BiomeType.TOWER_FAIRY -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.FAIRY)
                currentBiome == BiomeType.TOWER_POISON -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.POISON)
                currentBiome == BiomeType.TOWER_HIDEOUT -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.HIDEOUT)
                currentBiome == BiomeType.TOWER_ABYSS -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ABYSS)
                currentBiome == BiomeType.ROOT_CAMP -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ROOT_CAMP)
                currentBiome == BiomeType.ROOT_WATCH -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ROOT_WATCH)
                currentBiome == BiomeType.ROOT_SLOPECAMP -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ROOT_SLOPECAMP)
                currentBiome == BiomeType.ROOT_SIGNAL -> if (worldScale > 0) {
                    addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ROOT_SIGNAL)
                    placeMakromonNpc(R.drawable.makromon_11_lumex, cz.uhk.macroflow.pokemon.story.Signal.X, cz.uhk.macroflow.pokemon.story.Signal.Y, cz.uhk.macroflow.pokemon.story.Signal.NAME)
                }
                currentBiome == BiomeType.ROOT_BRIDGE -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ROOT_BRIDGE)
                currentBiome == BiomeType.ROOT_NAMELESS -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ROOT_NAMELESS)
                currentBiome == BiomeType.ROOT_TEMPLE -> if (worldScale > 0) addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ROOT_TEMPLE)
                currentBiome == BiomeType.ROOT_FOREST -> if (worldScale > 0) {
                    addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ROOT_FOREST)
                    placeMakromonNpc(R.drawable.makromon_30_gudwin, cz.uhk.macroflow.pokemon.cave.RootForest.GUDWIN_X,
                        cz.uhk.macroflow.pokemon.cave.RootForest.GUDWIN_Y, "Gudwin")
                    placeForestTags()
                }
                currentBiome == BiomeType.ROOT_HEART -> if (worldScale > 0) {
                    addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ROOT_HEART)
                    val F = cz.uhk.macroflow.pokemon.story.Finale
                    if (!StoryFlags.isSet(this@MakromonMapActivity, F.ARRIVED_KEY)) mapWorld.postDelayed({
                        if (isFinishing) return@postDelayed
                        StoryFlags.set(this@MakromonMapActivity, F.ARRIVED_KEY)
                        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), F.TITLE, "Kapitola VI · Konec cesty") { ui, body, _ ->
                            body.addView(ui.text(F.ARRIVE, 15f))
                        }
                    }, 900)
                }
                currentBiome == BiomeType.ROOT_THRESHOLD -> if (worldScale > 0) {
                    addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ROOT_THRESHOLD)
                    val D = cz.uhk.macroflow.pokemon.story.DrakirraFinal
                    val beaten = StoryFlags.isSet(this@MakromonMapActivity, D.DEFEATED_KEY)
                    placeMakromonNpc(R.drawable.makromon_19_drakirra, cz.uhk.macroflow.pokemon.cave.RootThreshold.DRAKIRRA_X, cz.uhk.macroflow.pokemon.cave.RootThreshold.DRAKIRRA_Y, "Drakirra")
                        .apply { if (beaten) { rotation = -84f; translationX += layoutParams.width * 0.3f } }
                    if (beaten && !StoryFlags.isSet(this@MakromonMapActivity, D.AFTER_SEEN_KEY)) mapWorld.postDelayed({
                        if (isFinishing) return@postDelayed
                        StoryFlags.set(this@MakromonMapActivity, D.AFTER_SEEN_KEY)
                        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), D.TITLE, D.SUBTITLE) { ui, body, _ ->
                            body.addView(ui.text(D.AFTER, 15f))
                        }
                    }, 700)
                }
                currentBiome == BiomeType.ROOT_TRUNK -> if (worldScale > 0) {
                    addInteriorFx(cz.uhk.macroflow.pokemon.cave.InteriorFxView.ROOT_TRUNK)
                    if (trpitelEnding() != cz.uhk.macroflow.pokemon.story.Trpitel.Ending.BLOOM) placeTrpitel()
                }
                currentBiome == BiomeType.OZVENA -> if (worldScale > 0) placeTownsfolk()
                currentBiome == BiomeType.ECHO_CANYON -> if (worldScale > 0) addGroundDecor(cz.uhk.macroflow.pokemon.cave.CanyonFxView(this@MakromonMapActivity, worldScale).apply {
                    layoutParams = FrameLayout.LayoutParams(cz.uhk.macroflow.pokemon.cave.EchoCanyon.MAP.artW * worldScale, cz.uhk.macroflow.pokemon.cave.EchoCanyon.MAP.artH * worldScale)
                })
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
            if (currentBiome.name.startsWith("ROOT_") && worldScale > 0) placeLanterns()
            placeEncounterMarks()
            placeCollectibles()
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
    /** Hunfrey na plošině vpravo od brány – animovaný GIF (docs/adr/0085). */
    private fun placeHunfrey() {
        if (worldScale <= 0) return
        val s = worldScale.toFloat()
        val H = cz.uhk.macroflow.pokemon.story.Hunfrey
        val size = (H.HEIGHT * s).toInt()
        val npc = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.FIT_CENTER
            x = H.X * s - size / 2f; y = H.Y * s - size * 0.97f; elevation = 2f
            contentDescription = H.NAME
        }
        npc.load(R.drawable.npc_hunfrey, imageLoader)
        addDecor(npc)
    }

    /** Rada Hunfreye podle postupu: úkoly pro vstup do Hvozdu, quest Mydruse, Srdce v batohu. */
    private fun hunfreyAdvice(): String {
        val quests = db.questDao().getAllQuests()
        val done = cz.uhk.macroflow.pokemon.cave.ForestMap.completedTasks(quests.map { p ->
            Triple(cz.uhk.macroflow.pokemon.quests.QuestRegistry.byId(p.questId)?.stages?.size ?: 0, p.currentStageIndex, p.isCompleted)
        })
        val fp = quests.firstOrNull { it.questId == "forest_heart" }
        val forest = fp?.takeIf { !it.isCompleted }?.let { p ->
            cz.uhk.macroflow.pokemon.quests.QuestRegistry.byId("forest_heart")?.stages?.getOrNull(p.currentStageIndex)
                ?.let { cz.uhk.macroflow.pokemon.story.Hunfrey.ForestStage(p.currentStageIndex, it.title, it.hint) }
        }
        return cz.uhk.macroflow.pokemon.story.Hunfrey.advice(
            hasHeart = cz.uhk.macroflow.pokemon.skills.SkillStore.count(this, ForestHeart.ITEM_ID) > 0,
            heartPlaced = StoryFlags.isSet(this, SkyPass.HEART_PLACED_KEY),
            tasksDone = done, tasksNeeded = cz.uhk.macroflow.pokemon.cave.ForestMap.REQUIRED_TASKS,
            forest = forest, forestDone = fp?.isCompleted == true)
    }

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

    // ── Hala Arény v Ozvěně (docs/adr/0092) ─────────────────────────────────

    /** Tři nejlepší ze žebříčku (jméno, druh prvního Makromona, body); bez sítě legendy cechu. */
    private suspend fun hallChampions(): List<Triple<String, String, Int>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val live = if (cz.uhk.macroflow.data.FirebaseRepository.isLoggedIn) runCatching {
            cz.uhk.macroflow.data.FirebaseRepository.arenaLeaderboard(3).map { Triple(it.name, it.team.firstOrNull()?.speciesId ?: "012", it.points) }
        }.getOrNull().orEmpty() else emptyList()
        (live + cz.uhk.macroflow.pokemon.cave.ArenaHall.LEGENDS).take(3)
    }

    /** Portréty v Síni šampionů: Makromon šampionova týmu v zlaceném rámu. */
    private fun placeHallPortraits() {
        val s = worldScale.toFloat()
        lifecycleScope.launch {
            val champs = hallChampions()
            if (currentBiome != BiomeType.ARENA_HALL) return@launch
            champs.forEachIndexed { i, (_, species, _) ->
                val r = cz.uhk.macroflow.pokemon.cave.ArenaHall.PORTRAITS.getOrNull(i) ?: return@forEachIndexed
                val sp = cz.uhk.macroflow.pokemon.species.SpeciesRegistry.byId(species) ?: return@forEachIndexed
                val res = resources.getIdentifier("makromon_${species.takeLast(2)}_${sp.name.lowercase().trim().replace(" ", "_")}", "drawable", packageName)
                if (res == 0) return@forEachIndexed
                addDecor(ImageView(this@MakromonMapActivity).apply {
                    layoutParams = FrameLayout.LayoutParams((r[2] * s).toInt(), (r[3] * s).toInt())
                    x = r[0] * s; y = r[1] * s + r[3] * s * 0.1f; elevation = 1.2f
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageResource(res)
                })
            }
        }
    }

    private fun showHallOfFame() {
        lifecycleScope.launch {
            val champs = hallChampions()
            val medals = listOf("🥇", "🥈", "🥉")
            showMapToast("🏆 Síň šampionů Arény Ozvěny\n" + champs.mapIndexed { i, (n, sp, pts) ->
                val name = cz.uhk.macroflow.pokemon.species.SpeciesRegistry.byId(sp)?.displayName ?: ""
                "${medals[i]} $n – $pts bodů · $name"
            }.joinToString("\n"))
        }
    }

    /** Velitel bojového cechu vysvětluje arénu po stránkách; na konci pozve dál. */
    /** Věž Řádu Váhy (docs/adr/0103): poprvé cutscéna obřích dveří, pak rovnou dovnitř. */
    private fun enterTower() {
        val T = cz.uhk.macroflow.pokemon.cave.TowerLobby
        if (StoryFlags.isSet(this, T.VISITED_KEY)) { walkInto(BiomeType.TOWER_LOBBY, T.ENTRY_NODE); return }
        cz.uhk.macroflow.pokemon.cutscene.CutsceneView.play(findViewById(android.R.id.content), cz.uhk.macroflow.pokemon.cutscene.TowerDoorCutsceneView(this)) {
            StoryFlags.set(this, T.VISITED_KEY)
            enterBiomeAtNode(BiomeType.TOWER_LOBBY, T.ENTRY_NODE, MapTransition.FADE)
            mapWorld.postDelayed({
                if (!isFinishing) { questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Ozvena.QUEST_ID); questManager.recheck() }
            }, 700)
        }
    }

    /** Patra Věže v pořadí na panelu výtahu: (číslo patra, popisek, biom, uzel u výtahu). */
    private val towerFloors by lazy {
        listOf(
            Triple(0, "Přízemí · Vestibul", BiomeType.TOWER_LOBBY to cz.uhk.macroflow.pokemon.cave.TowerLobby.ELEVATOR_NODE),
            Triple(1, cz.uhk.macroflow.pokemon.cave.TowerArchive.ELEVATOR_UP, BiomeType.TOWER_ARCHIVE to cz.uhk.macroflow.pokemon.cave.TowerArchive.ELEVATOR_NODE),
            Triple(2, "Patro 2 · Měřírna", BiomeType.TOWER_MEASURE to cz.uhk.macroflow.pokemon.cave.TowerMeasure.ELEVATOR_NODE),
            Triple(3, "Patro 3 · Kalibrační louka", BiomeType.TOWER_PLAIN to cz.uhk.macroflow.pokemon.cave.TowerPlain.ELEVATOR_NODE),
            Triple(4, "Patro 4 · Zarostlé patro", BiomeType.TOWER_FOREST to cz.uhk.macroflow.pokemon.cave.TowerForest.ELEVATOR_NODE),
            Triple(5, "Patro 5 · Skalní patro", BiomeType.TOWER_ROCK to cz.uhk.macroflow.pokemon.cave.TowerRock.ELEVATOR_NODE),
            Triple(6, "Patro 6 · Krystalová jeskyně", BiomeType.TOWER_CAVE to cz.uhk.macroflow.pokemon.cave.TowerCave.ELEVATOR_NODE),
            Triple(7, "Patro 7 · Zatopené patro", BiomeType.TOWER_WATER to cz.uhk.macroflow.pokemon.cave.TowerWater.ELEVATOR_NODE),
            Triple(8, "Patro 8 · Dormitoř", BiomeType.TOWER_DORM to cz.uhk.macroflow.pokemon.cave.TowerDorm.ELEVATOR_NODE),
            Triple(9, "Patro 9 · Mrazírna", BiomeType.TOWER_ICE to cz.uhk.macroflow.pokemon.cave.TowerIce.ELEVATOR_NODE),
            Triple(10, "Patro 10 · Spalovna spisů", BiomeType.TOWER_FIRE to cz.uhk.macroflow.pokemon.cave.TowerFire.ELEVATOR_NODE),
            Triple(11, "Patro 11 · Duhová zahrada", BiomeType.TOWER_FAIRY to cz.uhk.macroflow.pokemon.cave.TowerFairy.ELEVATOR_NODE),
            Triple(12, "Patro 12 · Jedová laboratoř", BiomeType.TOWER_POISON to cz.uhk.macroflow.pokemon.cave.TowerPoison.ELEVATOR_NODE),
            Triple(13, "Patro 13 · Kancelář Řádu", BiomeType.TOWER_OFFICE to cz.uhk.macroflow.pokemon.cave.TowerOffice.ELEVATOR_NODE),
            Triple(-1, "Patro −1 · (bez tlačítka)", BiomeType.TOWER_HIDEOUT to cz.uhk.macroflow.pokemon.cave.TowerHideout.ELEVATOR_NODE),
            Triple(-2, "Patro −2 · Sektor Hlubiny", BiomeType.TOWER_ABYSS to cz.uhk.macroflow.pokemon.cave.TowerAbyss.ELEVATOR_NODE)
        )
    }

    /** Postup kapitoly V (Ozvěna + Věž), jen když je načtená: (index fáze, hotovo). */
    private fun ozvenaStage(): Pair<Int, Boolean>? =
        questManager.getCurrentProgress()?.takeIf { questManager.getActiveQuestId() == cz.uhk.macroflow.pokemon.story.Ozvena.QUEST_ID }
            ?.let { it.currentStageIndex to it.isCompleted }

    /** Výtah Věže: pustí do pater, která kapitola V už schválila (docs/adr/0104); debug volný výtah do všech. */
    private fun onElevator() {
        questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Ozvena.QUEST_ID)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            val free = BuildConfig.DEBUG && gamePrefs.getBoolean(DEBUG_LIFT_KEY, false) || coupon()
            val (idx, done) = ozvenaStage() ?: (0 to false)
            val open = towerFloors.filter { (f, _, _) -> free || cz.uhk.macroflow.pokemon.story.TowerStory.approved(f, idx, done) }
            val floors = open.map { (_, label, target) ->
                    val (biome, node) = target
                    val go: (() -> Unit)? = if (currentBiome == biome) null else ({ rideTo(biome, node) })
                    label to go
                }
            val at = towerFloors.firstOrNull { it.third.first == currentBiome }?.first ?: 0
            cz.uhk.macroflow.pokemon.story.TowerUi.showElevator(findViewById(R.id.mapRootContainer), StoryFlags.insight(this), floors, at,
                numbers = open.map { it.first })
        }, 150)
    }

    /** Jízda výtahem; poprvé na −2 nejdřív cutscéna „Sjezd do Hlubiny“. */
    private fun rideTo(biome: BiomeType, node: String) = payMove(biome) {
        val seen = cz.uhk.macroflow.pokemon.cutscene.StoryCutscenes.DESCENT_SEEN
        if (biome != BiomeType.TOWER_ABYSS || gamePrefs.getBoolean(seen, false)) { enterBiomeAtNode(biome, node, MapTransition.FADE); return@payMove }
        gamePrefs.edit().putBoolean(seen, true).apply()
        cz.uhk.macroflow.pokemon.cutscene.CutsceneView.play(findViewById(android.R.id.content), cz.uhk.macroflow.pokemon.cutscene.DescentCutsceneView(this)) {
            enterBiomeAtNode(biome, node, MapTransition.FADE)
        }
    }

    /** Stopa příběhu nebo útržek Hunfreyho mapy na patře Věže; jinak obyčejný text místa. */
    private fun onTowerClue(node: String) {
        questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Ozvena.QUEST_ID)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            val (idx, done) = ozvenaStage() ?: (0 to false)
            val tap = cz.uhk.macroflow.pokemon.story.TowerStory.tap(node, idx, done) { StoryFlags.isSet(this, it) }
            if (tap == null) { towerFloorText(node)?.let { showMapToast(it) }; return@postDelayed }
            tap.setKeys.forEach { StoryFlags.set(this, it) }
            val plain = towerFloorText(node)
            val fragment = tap.setKeys.any { it in cz.uhk.macroflow.pokemon.story.TowerStory.FRAGMENT_KEYS }
            val show = {
                cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), if (fragment) "Útržek mapy" else "Stopa", null) { ui, body, _ ->
                    plain?.let { body.addView(ui.text(it, 14f, ui.inkSoft)); body.addView(ui.spacer(10f)) }
                    body.addView(ui.text(tap.text, 15f))
                }
            }
            if (fragment) collectFx(node, "🗺️ Útržek mapy") { show() } else show()
            if (tap.setKeys.isNotEmpty()) questManager.recheck()
        }, 150)
    }

    private fun towerFloorText(node: String): String? = cz.uhk.macroflow.pokemon.cave.TowerOffice.textFor(node, StoryFlags.isSet(this@MakromonMapActivity, cz.uhk.macroflow.pokemon.story.TowerStory.OBJ019_KEY))?.takeIf { node in cz.uhk.macroflow.pokemon.cave.TowerOffice.TEXTS } ?: listOf(
        cz.uhk.macroflow.pokemon.cave.TowerMeasure.TEXTS, cz.uhk.macroflow.pokemon.cave.TowerForest.TEXTS, cz.uhk.macroflow.pokemon.cave.TowerCave.TEXTS, cz.uhk.macroflow.pokemon.cave.TowerWater.TEXTS, cz.uhk.macroflow.pokemon.cave.TowerIce.TEXTS,
        cz.uhk.macroflow.pokemon.cave.TowerFairy.TEXTS, cz.uhk.macroflow.pokemon.cave.TowerOffice.TEXTS, cz.uhk.macroflow.pokemon.cave.TowerHideout.TEXTS
    ).firstNotNullOfOrNull { it[node] }

    /** Balkon Sektoru Hlubiny: výhled na Spící (konec kapitoly V). */
    private fun onAbyssView() {
        val first = !StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.TowerStory.ABYSS_KEY)
        StoryFlags.set(this, cz.uhk.macroflow.pokemon.story.TowerStory.ABYSS_KEY)
        if (first) gamePrefs.edit().putInt(cz.uhk.macroflow.pokemon.cave.RootCamp.ABYSS_DAY_KEY, java.time.LocalDate.now().toEpochDay().toInt()).apply()
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Sektor Hlubiny",
            if (first) "Konec kapitoly V · Ozvěna" else null) { ui, body, _ ->
            body.addView(ui.text(cz.uhk.macroflow.pokemon.cave.TowerAbyss.TEXTS.getValue(cz.uhk.macroflow.pokemon.cave.TowerAbyss.VIEW_NODE), 15f))
        }
    }

    /** Železný výtah nad propastí v Ozvěně (story/AbyssLift): po výhledu z −2 sjede k lávce Sektoru Hlubiny. */
    private fun onCityLift() {
        val A = cz.uhk.macroflow.pokemon.story.AbyssLift
        if (!A.open { StoryFlags.isSet(this, it) } && !coupon()) { showMapToast(cz.uhk.macroflow.pokemon.cave.OzvenaCity.LIFT_TEXT); return }
        val ride = { walkInto(BiomeType.TOWER_ABYSS, cz.uhk.macroflow.pokemon.cave.TowerAbyss.LIFT_NODE, MapTransition.MINE_DESCENT) }
        if (gamePrefs.getBoolean(A.RIDDEN_KEY, false)) { ride(); return }
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Výtah nad propastí", "SUBJEKT PŘIPRAVEN: 213") { ui, body, close ->
            body.addView(ui.text(A.OPEN_TEXT, 15f))
            body.addView(ui.spacer(14f))
            body.addView(ui.button("🛗 Sjet dolů") {
                close()
                gamePrefs.edit().putBoolean(A.RIDDEN_KEY, true).apply()
                cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Sestup", "Ozvěna → Sektor Hlubiny") { u2, b2, c2 ->
                    b2.addView(u2.text(A.FIRST_RIDE, 15f))
                    b2.addView(u2.spacer(14f))
                    b2.addView(u2.button("Vystoupit") { c2(); ride() })
                }
            })
        }
    }

    /**
     * Klec v Sektoru Hlubiny (docs/adr/0106): den po prvním výhledu na Spící je sestup schválen a klec sjede do zóny 3
     * (Tábor výprav). Starší uložené hry bez dne výhledu se berou jako „už včera“. Debug volný výtah ji pustí hned.
     */
    private fun onAbyssCage() {
        val R3 = cz.uhk.macroflow.pokemon.cave.RootCamp
        val seen = StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.TowerStory.ABYSS_KEY)
        val today = java.time.LocalDate.now().toEpochDay().toInt()
        val free = BuildConfig.DEBUG && gamePrefs.getBoolean(DEBUG_LIFT_KEY, false) || devKeycard()
        val open = free || (seen && today > gamePrefs.getInt(R3.ABYSS_DAY_KEY, Int.MIN_VALUE))
        val locked = cz.uhk.macroflow.pokemon.cave.TowerAbyss.TEXTS.getValue(cz.uhk.macroflow.pokemon.cave.TowerAbyss.CAGE_NODE)
        when {
            !open -> showMapToast(locked + if (seen) R3.CAGE_WAIT_TEXT else "")
            StoryFlags.isSet(this, R3.VISITED_KEY) -> walkInto(BiomeType.ROOT_CAMP, R3.CAGE_NODE)
            else -> cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Sestup schválen", "Kapitola VI · Hlubina") { ui, body, close ->
                body.addView(ui.text(R3.CAGE_OPENED_TEXT, 15f))
                body.addView(ui.spacer(14f))
                body.addView(ui.button("⛓️ Sjet klecí dolů") {
                    close()
                    StoryFlags.set(this, R3.VISITED_KEY)
                    walkInto(BiomeType.ROOT_CAMP, R3.CAGE_NODE)
                })
            }
        }
    }

    /**
     * Závora UZÁVĚRA ve Stanici 8 (docs/adr/0106): sestup do Pozorovatelny B. ponytail: dokud nestojí kapitola VI
     * (fáze „Výprava 8“), pouští jen debug volný výtah – pak sem přijde podmínka fáze.
     */
    private fun stationBarrierOpen() = BuildConfig.DEBUG && gamePrefs.getBoolean(DEBUG_LIFT_KEY, false) || devKeycard() ||
        gamePrefs.getBoolean("inspect_read_ROOT_CAMP_${cz.uhk.macroflow.pokemon.cave.RootCamp.RADIO_NODE}", false)

    private fun onStationBarrier() {
        if (!stationBarrierOpen()) { showMapToast(cz.uhk.macroflow.pokemon.cave.RootCamp.TEXTS.getValue(cz.uhk.macroflow.pokemon.cave.RootCamp.DOWN_NODE) +
            cz.uhk.macroflow.pokemon.cave.RootCamp.BARRIER_HINT); return }
        StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.RootWatch.VISITED_KEY)
        walkInto(BiomeType.ROOT_WATCH, cz.uhk.macroflow.pokemon.cave.RootWatch.ENTRY_NODE)
    }

    /** Signalista Kesh (docs/adr/0106): první oslovení spustí vedlejší úkol, dál mluví podle fáze. */
    private fun onKesh() {
        lifecycleScope.launch {
            if (questProgress(cz.uhk.macroflow.pokemon.story.Signal.QUEST_ID)?.second == true) { onKeshLanterns(); return@launch }
            StoryFlags.set(this@MakromonMapActivity, cz.uhk.macroflow.pokemon.story.Signal.STARTED_KEY)
            questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Signal.QUEST_ID)
            mapWorld.postDelayed({ if (!isFinishing) questManager.checkNpcInteraction() }, 200)
        }
    }

    /** Kesh po odpovědi kmene: dá lucernu výpravy 7 (úkol Cesta zpátky), se všemi zapálenými ukáže rozsvícenou cestu. */
    private fun onKeshLanterns() {
        val L = cz.uhk.macroflow.pokemon.story.Lanterns
        StoryFlags.set(this, L.STARTED_KEY)
        questManager.loadQuest(L.QUEST_ID)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            questManager.recheck()
            mapWorld.postDelayed({
                if (isFinishing) return@postDelayed
                val p = questManager.getCurrentProgress()?.takeIf { questManager.getActiveQuestId() == L.QUEST_ID }
                val stage = p?.currentStageIndex ?: 0
                when {
                    p?.isCompleted == true -> showMapToast("🔦 " + L.FAREWELL)
                    stage == L.FINAL_STAGE && L.ALL.all { StoryFlags.isSet(this, it.key) } && !StoryFlags.isSet(this, L.FINAL_KEY) -> {
                        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Cesta zpátky", L.NAME) { ui, body, _ ->
                            body.addView(ui.text(L.FINAL, 15f))
                            body.addView(ui.spacer(10f))
                            body.addView(ui.text(L.LANTERN_GIFT, 14f, ui.inkSoft))
                        }
                        StoryFlags.set(this, L.FINAL_KEY)
                        lifecycleScope.launch(Dispatchers.IO) { giveSignalLantern() }
                        questManager.recheck()
                    }
                    else -> questManager.checkNpcInteraction()
                }
            }, 250)
        }, 150)
    }

    /** Signální lucerna za Cestu zpátky (jednou; dostanou ji i ti, kdo úkol dokončili dřív – viz deník). */
    private fun giveSignalLantern() {
        val id = cz.uhk.macroflow.pokemon.skills.Gear.SIGNAL_LANTERN.id
        if (cz.uhk.macroflow.pokemon.skills.SkillStore.count(this, id) <= 0) cz.uhk.macroflow.pokemon.skills.SkillStore.add(this, id, 1)
    }

    /** Signální lampa: ve fázi „Odpověď“ pošle hráč poslední signál a kmen odpoví šesti světly. */
    private fun onSignalLamp() {
        val S = cz.uhk.macroflow.pokemon.story.Signal
        if (!StoryFlags.isSet(this, S.STARTED_KEY)) { showMapToast(S.lampText(0, false)); return }
        questManager.loadQuest(S.QUEST_ID)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            val p = questManager.getCurrentProgress()?.takeIf { questManager.getActiveQuestId() == S.QUEST_ID }
            val done = p?.isCompleted == true
            val stage = p?.currentStageIndex ?: 0
            if (!done && stage == S.ANSWER_STAGE && !StoryFlags.isSet(this, S.ANSWER_KEY)) {
                cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Odpověď", "Signalista") { ui, body, _ ->
                    body.addView(ui.text(S.ANSWER, 15f))
                }
                StoryFlags.set(this, S.ANSWER_KEY)
                questManager.recheck()
            } else showMapToast(S.lampText(stage, done))
        }, 200)
    }

    /** Gondola na Signálním útesu: jede ke Kořenovému mostu až poté, co kmen odpověděl (úkol Signalista). */
    private fun onSignalCable() {
        if (!StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Signal.ANSWER_KEY) && !devKeycard()) {
            showMapToast(cz.uhk.macroflow.pokemon.cave.RootSignal.TEXTS.getValue("su_lanovka")); return
        }
        if (!StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.cave.RootBridge.VISITED_KEY)) showMapToast(cz.uhk.macroflow.pokemon.cave.RootSignal.CABLE_OPEN_TEXT)
        StoryFlags.set(this, cz.uhk.macroflow.pokemon.cave.RootBridge.VISITED_KEY)
        walkInto(BiomeType.ROOT_BRIDGE, cz.uhk.macroflow.pokemon.cave.RootBridge.ENTRY_NODE)
    }

    /** Fáze úkolu Noční směna (index, hotovo) – null, dokud nezačal. */
    private fun nightShiftStage(): Pair<Int, Boolean>? {
        val N = cz.uhk.macroflow.pokemon.story.NightShift
        if (!StoryFlags.isSet(this, N.STARTED_KEY)) return null
        val p = questManager.getCurrentProgress()?.takeIf { questManager.getActiveQuestId() == N.QUEST_ID } ?: return null
        return p.currentStageIndex to p.isCompleted
    }

    /** Magnetofon Orrina: první přiblížení spustí úkol; s páskou z ventilu přehraje poslední hlášení. */
    private fun onTapeRecorder() {
        val N = cz.uhk.macroflow.pokemon.story.NightShift
        StoryFlags.set(this, N.STARTED_KEY)
        questManager.loadQuest(N.QUEST_ID)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            val (stage, done) = nightShiftStage() ?: (0 to false)
            if (!done && stage == N.FINAL_STAGE && StoryFlags.isSet(this, N.VALVE_KEY) && !StoryFlags.isSet(this, N.FINAL_KEY)) {
                cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Hlášení 41", N.NAME) { ui, body, _ ->
                    body.addView(ui.text(N.FINAL, 15f))
                }
                StoryFlags.set(this, N.FINAL_KEY)
                questManager.recheck()
            } else if (done) showMapToast(N.TAPE_IDLE)
            else questManager.checkNpcInteraction()
        }, 200)
    }

    // ── Svah bezejmenných: Kořeny, které pijí (story/SlopeWater, docs/adr/0115) ──

    /** Vodočetná lať: začne a vede úkol (voda → jídlo → docházka); po dokončení jen text místa. */
    private fun onSlopeWater() {
        val W = cz.uhk.macroflow.pokemon.story.SlopeWater
        if (StoryFlags.isSet(this, W.NAMES_KEY)) { onInspectTap(); return }
        StoryFlags.set(this, W.STARTED_KEY)
        questManager.loadQuest(W.QUEST_ID)
        mapWorld.postDelayed({ if (!isFinishing) questManager.checkNpcInteraction() }, 200)
    }

    /** Devět z výpravy 7: ve fázi docházky hráč přečte jejich jména nahlas. */
    private fun onSlopeV7() {
        val W = cz.uhk.macroflow.pokemon.story.SlopeWater
        lifecycleScope.launch {
            val p = questProgress(W.QUEST_ID)
            if (p == null || p.second || p.first != W.NAMES_STAGE || StoryFlags.isSet(this@MakromonMapActivity, W.NAMES_KEY)) { onInspectTap(); return@launch }
            cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), W.READ_TITLE, "Docházka") { ui, body, close ->
                body.addView(ui.text(W.V7.joinToString("\n"), 16f))
                body.addView(ui.spacer(14f))
                body.addView(ui.button(W.READ_BUTTON) {
                    close()
                    StoryFlags.set(this@MakromonMapActivity, W.NAMES_KEY)
                    cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), W.READ_TITLE, W.NAME) { ui2, body2, _ ->
                        body2.addView(ui2.text(W.READ, 15f))
                    }
                    questManager.loadQuest(W.QUEST_ID)
                    mapWorld.postDelayed({ if (!isFinishing) questManager.recheck() }, 200)
                })
            }
        }
    }

    /** Schované pásky (svatyňka, lešení): dokud úkol běží, najde hráč cívku; jinak jen text místa. */
    private fun onHiddenReel(node: String, key: String, foundText: String) {
        val N = cz.uhk.macroflow.pokemon.story.NightShift
        if (StoryFlags.isSet(this, N.STARTED_KEY) && !StoryFlags.isSet(this, key)) {
            StoryFlags.set(this, key)
            collectFx(node, "📼 Cívka", 0xFFB8F070.toInt()) { showMapToast(foundText) }
            questManager.loadQuest(N.QUEST_ID)
            mapWorld.postDelayed({ if (!isFinishing) questManager.recheck() }, 200)
        } else showMapToast(cz.uhk.macroflow.pokemon.cave.RootBridge.TEXTS.getValue(node))
    }

    /** Ventil na potrubí s pěnou: povolí až ve fázi Hlášení 41 (po dvou dnech s tréninkem). */
    private fun onFoamValve() {
        val N = cz.uhk.macroflow.pokemon.story.NightShift
        if (StoryFlags.isSet(this, N.VALVE_KEY)) { showMapToast(N.VALVE_DONE); return }
        if (!StoryFlags.isSet(this, N.STARTED_KEY)) { showMapToast(N.VALVE_SHUT); return }
        questManager.loadQuest(N.QUEST_ID)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            val (stage, done) = nightShiftStage() ?: (0 to false)
            if (done || stage >= N.FINAL_STAGE) { StoryFlags.set(this, N.VALVE_KEY); showMapToast(N.VALVE_OPEN) }
            else showMapToast(N.VALVE_SHUT)
        }, 200)
    }

    /** Práh (docs/adr/0106): poslední souboj s Drakirrou před kmenem; po výhře jen text. */
    private fun onDrakirraFinal() {
        val D = cz.uhk.macroflow.pokemon.story.DrakirraFinal
        if (StoryFlags.isSet(this, D.DEFEATED_KEY)) { showMapToast(D.dragonText(true)); return }
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), D.TITLE, D.SUBTITLE) { ui, body, close ->
            body.addView(ui.text(D.BEFORE, 15f))
            body.addView(ui.spacer(14f))
            body.addView(ui.button(D.FIGHT) {
                close()
                lifecycleScope.launch {
                    val team = kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { db.capturedMakromonDao().getAllCaught().size }.getOrDefault(1) }
                    if (team == 0) showMapToast(D.NO_TEAM)
                    else startSpecialBattle(SpecialBattle.DRAKIRRA_FINAL, BiomeType.ROOT_THRESHOLD)
                }
            })
            body.addView(ui.button(D.LEAVE) { close(); showMapToast(D.LEAVE_TEXT) })
        }
    }

    private fun trpitelEnding() = cz.uhk.macroflow.pokemon.story.Trpitel.ending { StoryFlags.isSet(this, it) }

    /** Trpitel v ráně kmene: pás snímků (dech, ruce) přes mapu, 1 px spritu = 1 px mapy. */
    private fun placeTrpitel() {
        val K = cz.uhk.macroflow.pokemon.cave.RootTrunk
        val s = worldScale
        val sheet = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.trpitel_sheet, android.graphics.BitmapFactory.Options().apply { inScaled = false })
        val fw = sheet.width / K.TRPITEL_FRAMES
        val anim = android.graphics.drawable.AnimationDrawable().apply {
            for (i in 0 until K.TRPITEL_FRAMES) addFrame(android.graphics.drawable.BitmapDrawable(resources,
                android.graphics.Bitmap.createBitmap(sheet, i * fw, 0, fw, sheet.height)).apply { isFilterBitmap = false }, K.TRPITEL_FRAME_MS[i])
            isOneShot = false
        }
        val v = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(fw * s, sheet.height * s)
            scaleType = ImageView.ScaleType.FIT_XY
            setImageDrawable(anim)
            x = K.TRPITEL_X0 * s.toFloat(); y = K.TRPITEL_Y0 * s.toFloat(); elevation = 1f
            contentDescription = "Trpitel"
        }
        addDecor(v)
        anim.start()
        trpitelView = v
    }
    private var trpitelView: ImageView? = null

    /** Kmen: Trpitel se probudí jen nádobě v rovnováze (dnes 90–110 % cíle energie), pak volba konce – nevratně. */
    private fun onTrpitel() {
        val T = cz.uhk.macroflow.pokemon.story.Trpitel
        trpitelEnding()?.let { showMapToast(T.after(it)); return }
        lifecycleScope.launch {
            val pct = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching {
                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
                    val meals = AppDatabase.getDatabase(applicationContext).consumedSnackDao().getConsumedByDateSync(today)
                    val t = cz.uhk.macroflow.dashboard.MacroCalculator.calculate(applicationContext)
                    cz.uhk.macroflow.energy.Adherence.percent(cz.uhk.macroflow.energy.Adherence.Nutrient.KCAL,
                        cz.uhk.macroflow.energy.Adherence.Eaten(meals.sumOf { it.calories.toDouble() }, meals.sumOf { it.p.toDouble() },
                            meals.sumOf { it.s.toDouble() }, meals.sumOf { it.t.toDouble() }),
                        cz.uhk.macroflow.energy.Adherence.Targets(t.calories, t.protein, t.carbs, t.fat))
                }.getOrDefault(0)
            }
            if (isFinishing) return@launch
            val root = findViewById<FrameLayout>(R.id.mapRootContainer)
            if (!T.balanced(pct) && !devKeycard()) {
                cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(root, T.TITLE, T.SUBTITLE) { ui, body, _ -> body.addView(ui.text(T.asleep(pct), 15f)) }
                return@launch
            }
            StoryFlags.set(this@MakromonMapActivity, T.MET_KEY)
            cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(root, T.TITLE, T.SUBTITLE) { ui, body, close ->
                body.addView(ui.text(T.WAKE, 15f))
                body.addView(ui.spacer(10f))
                body.addView(ui.text(T.SEPT, 15f))
                body.addView(ui.spacer(14f))
                body.addView(ui.text(T.CHOOSE, 15f))
                for (e in cz.uhk.macroflow.pokemon.story.Trpitel.Ending.entries) body.addView(ui.button(T.label(e)) { close(); confirmEnding(e) })
                body.addView(ui.button(T.LEAVE) { close(); showMapToast(T.LEAVE_TEXT) })
            }
        }
    }

    private fun confirmEnding(e: cz.uhk.macroflow.pokemon.story.Trpitel.Ending) {
        val T = cz.uhk.macroflow.pokemon.story.Trpitel
        val root = findViewById<FrameLayout>(R.id.mapRootContainer)
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(root, T.label(e), T.SUBTITLE) { ui, body, close ->
            body.addView(ui.text(T.confirm(e), 15f))
            body.addView(ui.spacer(14f))
            body.addView(ui.button(T.CONFIRM) {
                close()
                StoryFlags.set(this, e.key)
                if (e == cz.uhk.macroflow.pokemon.story.Trpitel.Ending.BLOOM) trpitelView?.animate()?.alpha(0f)?.setDuration(2600)?.start()   // rozpadne se v popel
                cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(root, T.TITLE, T.SUBTITLE) { ui2, body2, _ -> body2.addView(ui2.text(T.chosen(e), 15f)) }
            })
            body.addView(ui.button(T.NOT_YET) { close(); showMapToast(T.LEAVE_TEXT) })
        }
    }

    // ── Střed kořenů a konce (story/Finale, docs/adr/0110) ──

    private fun finaleWritten() = cz.uhk.macroflow.pokemon.story.MemoryBook.RESIDENTS.count {
        StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.MemoryBook.writtenKey(it.id)) }

    /** Poslední světlo Lumiry: dohraje konec zvolený u Trpitele; po dohrání jen dovětek. */
    private fun onLastLight() {
        val F = cz.uhk.macroflow.pokemon.story.Finale
        F.played { StoryFlags.isSet(this, it) }?.let { showMapToast(F.after(it)); return }
        val chosen = trpitelEnding() ?: run { showMapToast(F.LIGHT_NONE); return }
        val end = F.pending(chosen)
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), F.TITLE, "Poslední světlo") { ui, body, close ->
            body.addView(ui.text(F.lightBefore(end), 15f))
            body.addView(ui.spacer(14f))
            body.addView(ui.button(F.go(end)) { close(); playEnding(end) })
        }
    }

    /** Mechové lůžko: s celou pamětí si hráč lehne a tajný konec přijde po skutečné noci spánku (≥ 7 h). */
    private fun onMossBed() {
        val F = cz.uhk.macroflow.pokemon.story.Finale
        if (F.played { StoryFlags.isSet(this, it) } != null) { showMapToast("🌿 Mech je měkký a teplý. Spirra v něm spí."); return }
        val missing = F.memoriesMissing({ StoryFlags.isSet(this, it) }, finaleWritten())
        if (missing.isNotEmpty() && !devKeycard()) { showMapToast(F.MOSS_PLAIN + missing.joinToString("\n")); return }
        val layDay = gamePrefs.getLong(F.LAY_DAY_KEY, -1L)
        if (layDay >= 0) {
            lifecycleScope.launch {
                val slept = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    runCatching {
                        val since = java.time.LocalDate.ofEpochDay(layDay + 1).toString()
                        val today = java.time.LocalDate.now().toString()
                        db.wearableDao().getDaysSync(since, today).any { (it.sleepSec ?: 0) >= F.SLEEP_HOURS * 3600 }
                    }.getOrDefault(false)
                }
                if (isFinishing) return@launch
                if (slept || devKeycard()) playEnding(cz.uhk.macroflow.pokemon.story.Finale.End.SECRET) else showMapToast(F.NOT_YET_SLEPT)
            }
            return
        }
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), F.TITLE, "Mechové lůžko") { ui, body, close ->
            body.addView(ui.text(F.MOSS_READY, 15f))
            body.addView(ui.spacer(14f))
            body.addView(ui.button(F.LIE_DOWN) {
                close()
                gamePrefs.edit().putLong(F.LAY_DAY_KEY, java.time.LocalDate.now().toEpochDay()).apply()
                showMapToast(F.LAY)
            })
        }
    }

    private fun endingVideo(end: cz.uhk.macroflow.pokemon.story.Finale.End) = when (end) {
        cz.uhk.macroflow.pokemon.story.Finale.End.SPEAR -> R.raw.ending_kopi
        cz.uhk.macroflow.pokemon.story.Finale.End.BLOOM -> R.raw.ending_kveteni
        cz.uhk.macroflow.pokemon.story.Finale.End.WISH -> R.raw.ending_prani
        cz.uhk.macroflow.pokemon.story.Finale.End.SECRET -> R.raw.ending_probuzeni
    }

    /** Debug: přehrát konec bez příznaků a bez návratu do Města. */
    private fun debugEnding(end: cz.uhk.macroflow.pokemon.story.Finale.End) {
        cz.uhk.macroflow.pokemon.audio.GameAudio.pause()
        cz.uhk.macroflow.pokemon.cutscene.EndingScene.show(findViewById(R.id.mapRootContainer),
            cz.uhk.macroflow.pokemon.story.Finale.ENDINGS.getValue(end), endingVideo(end)) { cz.uhk.macroflow.pokemon.audio.GameAudio.resume(this) }
    }

    /** Dohrání konce přes celou obrazovku, pak návrat do Města (Makrosvět jde dál). */
    private fun playEnding(end: cz.uhk.macroflow.pokemon.story.Finale.End) {
        val F = cz.uhk.macroflow.pokemon.story.Finale
        StoryFlags.set(this, end.key)
        val video = endingVideo(end)
        if (video != 0) cz.uhk.macroflow.pokemon.audio.GameAudio.pause()           // cutscéna má vlastní hudbu
        cz.uhk.macroflow.pokemon.cutscene.EndingScene.show(findViewById(R.id.mapRootContainer), F.ENDINGS.getValue(end), video) {
            if (isFinishing) return@show
            if (video != 0) cz.uhk.macroflow.pokemon.audio.GameAudio.resume(this)
            enterBiomeAtNode(BiomeType.TOWN, "spawn")
        }
    }

    /** Spis 212: stránky odkrývají noci spánku; po fázi Spis 212 je odkrytý celý. */
    private fun onFile212() {
        questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Ozvena.QUEST_ID)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            val (idx, done) = ozvenaStage() ?: (0 to false)
            cz.uhk.macroflow.pokemon.story.TowerUi.showFile212(findViewById(R.id.mapRootContainer), this,
                read = done || idx > cz.uhk.macroflow.pokemon.story.TowerStory.SPIS_212)
            questManager.recheck()
        }, 150)
    }

    /** Archivář Sept: stav kalibrace, Spis Subjektu. */
    private fun onSept() {
        questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Ozvena.QUEST_ID)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            val p = questManager.getCurrentProgress().takeIf { questManager.getActiveQuestId() == cz.uhk.macroflow.pokemon.story.Ozvena.QUEST_ID }
            val running = p != null && !p.isCompleted && p.currentStageIndex == cz.uhk.macroflow.pokemon.story.Calibration.STAGE
            val calibrated = p != null && (p.isCompleted || p.currentStageIndex > cz.uhk.macroflow.pokemon.story.Calibration.STAGE)
            val towerHint = p?.takeIf { !it.isCompleted && it.currentStageIndex > cz.uhk.macroflow.pokemon.story.Calibration.STAGE }
                ?.let { cz.uhk.macroflow.pokemon.quests.QuestRegistry.OZVENA_QUEST.stages.getOrNull(it.currentStageIndex)?.hint }
            cz.uhk.macroflow.pokemon.story.TowerUi.showSept(findViewById(R.id.mapRootContainer), this,
                hasInvite = StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Masters.INVITE_KEY),
                calibrated = calibrated, stageStartMs = if (running) p!!.stageStartedAt else null, towerHint = towerHint)
            questManager.recheck()
        }, 150)
    }

    /** Pět Mistrů (docs/adr/0102): pořadí, podmínka tréninků za 7 dní, výzva do souboje. */
    private fun showMasters() {
        val app = applicationContext
        val M = cz.uhk.macroflow.pokemon.story.Masters
        lifecycleScope.launch {
            val (trainings, levels) = kotlinx.coroutines.withContext(Dispatchers.IO) {
                cz.uhk.macroflow.pokemon.story.MastersStore.trainingsLast7(app) to cz.uhk.macroflow.pokemon.story.MastersStore.teamLevels(app)
            }
            val ozvena = kotlinx.coroutines.withContext(Dispatchers.IO) { db.questDao().getQuestById(cz.uhk.macroflow.pokemon.story.Ozvena.QUEST_ID) }
            if (isFinishing) return@launch
            questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Ozvena.QUEST_ID)
            val beaten = M.ALL.map { it.key }.filter { StoryFlags.isSet(this@MakromonMapActivity, M.flag(it)) }.toSet()
            cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Pět Mistrů",
                "Tréninky za posledních 7 dní: $trainings") { ui, body, close ->
                body.addView(ui.text(M.HALL_INTRO, 15f, ui.inkSoft))
                // Objekt 019 (docs/adr/0104): Řád tu předvádí spoutanou Drakirru
                val T = cz.uhk.macroflow.pokemon.story.TowerStory
                if (ozvena?.isCompleted == false && ozvena.currentStageIndex == T.OBJ019 && !StoryFlags.isSet(this@MakromonMapActivity, T.OBJ019_KEY)) {
                    body.addView(ui.spacer(10f))
                    body.addView(ui.text("⛓ Objekt 019\nUprostřed kouta leží v řetězech rudá dračice. Řetězy jí berou sílu i rychlost. Dívá se jen na tvou Spirru.", 15f))
                    body.addView(ui.button("Zahájit test síly nádoby") {
                        close()
                        if (levels.isEmpty()) showMapToast("Nejdřív si chyť Makromona.")
                        else startSpecialBattle(SpecialBattle.OBJEKT_019, BiomeType.ARENA_HALL)
                    })
                }
                body.addView(ui.spacer(10f))
                M.ALL.forEachIndexed { i, m ->
                    val st = M.status(i, trainings, beaten)
                    body.addView(ui.text((if (st == cz.uhk.macroflow.pokemon.story.Masters.Status.BEATEN) "✓ " else "") + "${m.name} · ${m.title}", 18f))
                    body.addView(ui.text(when (st) {
                        cz.uhk.macroflow.pokemon.story.Masters.Status.BEATEN -> m.win
                        cz.uhk.macroflow.pokemon.story.Masters.Status.OPEN -> m.intro
                        cz.uhk.macroflow.pokemon.story.Masters.Status.NEED_TRAINING -> M.needText(m.need, trainings)
                        cz.uhk.macroflow.pokemon.story.Masters.Status.NEED_PREVIOUS -> "Nejdřív poraz předchozího Mistra."
                    }, 15f, ui.inkSoft))
                    if (st == cz.uhk.macroflow.pokemon.story.Masters.Status.OPEN) body.addView(ui.button("Vyzvat") {
                        close()
                        if (levels.isEmpty()) showMapToast("Nejdřív si chyť Makromona.")
                        else replaceMapContent(PokemonBattleFragment.forTrainer(M.trainer(m, levels, java.time.LocalDate.now().toEpochDay())))
                    })
                    body.addView(ui.spacer(12f))
                }
            }
        }
    }

    private fun showCommander(page: Int) {
        val pages = cz.uhk.macroflow.pokemon.cave.ArenaHall.COMMANDER_PAGES
        val root = findViewById<FrameLayout>(R.id.mapRootContainer)
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(root, cz.uhk.macroflow.pokemon.cave.ArenaHall.COMMANDER_NAME, "Bojový cech Ozvěny · ${page + 1}/${pages.size}") { ui, body, close ->
            body.addView(ui.text(pages[page], 17f))
            body.addView(ui.spacer(12f))
            if (page < pages.size - 1) body.addView(ui.button("Dál") { close(); showCommander(page + 1) })
            else body.addView(ui.button("Do arény") { close(); replaceMapContent(cz.uhk.macroflow.pokemon.trainer.ArenaFragment()) })
        }
    }

    /** Popelavá pláň: otevřený závoj Brány světů se točí stejně jako v průsmyku (docs/adr/0088). */
    private fun placeAshGateVeil() {
        val s = worldScale.toFloat()
        val A = cz.uhk.macroflow.pokemon.cave.SkyPassArt
        val P = cz.uhk.macroflow.pokemon.cave.AshPlain
        val frames = (0 until A.FRAMES).map {
            android.graphics.Bitmap.createBitmap(A.veil(it, true), A.VEIL_SIZE, A.VEIL_SIZE, android.graphics.Bitmap.Config.ARGB_8888)
        }
        val veilSize = (A.VEIL_SIZE * s).toInt()
        val veil = pixelView(A.veil(0, true), A.VEIL_SIZE, A.VEIL_SIZE, veilSize, veilSize).apply {
            x = (P.GATE_X - A.VEIL_R) * s; y = (P.GATE_Y - A.VEIL_R) * s; elevation = 1.5f
        }
        val runeGlow = glowView((60 * s).toInt(), 0xFF7CFF9A.toInt(), 0x66).apply {
            x = (P.GATE_X - 30) * s; y = (P.GATE_Y - 30) * s; elevation = 1.4f
        }
        addDecor(runeGlow); addDecor(veil)
        val t0 = android.os.SystemClock.uptimeMillis()
        crystalAnimators += android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                val t = android.os.SystemClock.uptimeMillis() - t0
                veil.setImageDrawable(android.graphics.drawable.BitmapDrawable(resources, frames[((t / 110) % A.FRAMES).toInt()]).apply { isFilterBitmap = false })
                runeGlow.alpha = 0.4f + 0.35f * A.pulse(t + 700, 3400).toFloat()
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

    // ── Prozkoumávání míst (docs/adr/0107): třpyt, podržení prstu s pixelovým kruhem, panel „Prozkoumáno“ ──

    private val inspectMaps: Map<BiomeType, Map<String, String>> by lazy { mapOf(
            BiomeType.ROOT_TRUNK to cz.uhk.macroflow.pokemon.cave.RootTrunk.TEXTS,
            BiomeType.ROOT_HEART to cz.uhk.macroflow.pokemon.cave.RootHeart.TEXTS,
            BiomeType.ROOT_THRESHOLD to cz.uhk.macroflow.pokemon.cave.RootThreshold.TEXTS,
            BiomeType.ROOT_FOREST to cz.uhk.macroflow.pokemon.cave.RootForest.TEXTS,
            BiomeType.ROOT_TEMPLE to cz.uhk.macroflow.pokemon.cave.RootTemple.TEXTS,
            BiomeType.ROOT_NAMELESS to cz.uhk.macroflow.pokemon.cave.RootNameless.TEXTS,
            BiomeType.ROOT_SLOPECAMP to cz.uhk.macroflow.pokemon.cave.RootSlopeCamp.TEXTS,
            BiomeType.ROOT_WATCH to cz.uhk.macroflow.pokemon.cave.RootWatch.TEXTS,
            BiomeType.ROOT_CAMP to cz.uhk.macroflow.pokemon.cave.RootCamp.TEXTS,
            BiomeType.ROOT_BRIDGE to cz.uhk.macroflow.pokemon.cave.RootBridge.TEXTS,
            BiomeType.ROOT_SIGNAL to cz.uhk.macroflow.pokemon.cave.RootSignal.TEXTS,
            BiomeType.TOWER_HIDEOUT to cz.uhk.macroflow.pokemon.cave.TowerHideout.TEXTS,
            BiomeType.TOWER_ABYSS to cz.uhk.macroflow.pokemon.cave.TowerAbyss.TEXTS,
            BiomeType.TOWER_DORM to cz.uhk.macroflow.pokemon.cave.TowerDorm.TEXTS,
            BiomeType.TOWER_ICE to cz.uhk.macroflow.pokemon.cave.TowerIce.TEXTS,
            BiomeType.TOWER_FIRE to cz.uhk.macroflow.pokemon.cave.TowerFire.TEXTS,
            BiomeType.TOWER_OFFICE to cz.uhk.macroflow.pokemon.cave.TowerOffice.TEXTS,
            BiomeType.TOWER_FAIRY to cz.uhk.macroflow.pokemon.cave.TowerFairy.TEXTS,
            BiomeType.TOWER_POISON to cz.uhk.macroflow.pokemon.cave.TowerPoison.TEXTS,
            BiomeType.TOWER_WATER to cz.uhk.macroflow.pokemon.cave.TowerWater.TEXTS,
            BiomeType.TOWER_CAVE to cz.uhk.macroflow.pokemon.cave.TowerCave.TEXTS,
            BiomeType.TOWER_ROCK to cz.uhk.macroflow.pokemon.cave.TowerRock.TEXTS,
            BiomeType.TOWER_FOREST to cz.uhk.macroflow.pokemon.cave.TowerForest.TEXTS,
            BiomeType.TOWER_PLAIN to cz.uhk.macroflow.pokemon.cave.TowerPlain.TEXTS,
            BiomeType.TOWER_MEASURE to cz.uhk.macroflow.pokemon.cave.TowerMeasure.TEXTS,
            BiomeType.TOWER_ARCHIVE to cz.uhk.macroflow.pokemon.cave.TowerArchive.TEXTS
        ) }

    /** Místa zón 1 a 2 a vestibulu Věže: texty závislé na stavu (Vhled, den, fáze úkolu) – čtou se podržením jako v Hlubině. */
    private val inspectExtra: Map<BiomeType, Map<String, () -> String>> by lazy {
        val AP = cz.uhk.macroflow.pokemon.cave.AshPlain; val OC = cz.uhk.macroflow.pokemon.cave.OzvenaCity
        val TL = cz.uhk.macroflow.pokemon.cave.TowerLobby; val EC = cz.uhk.macroflow.pokemon.cave.EchoCanyon
        val insight = { Insight.level(StoryFlags.all(this)) >= 3 }
        mapOf(
            BiomeType.MOUNTAINS to mapOf("rozcesti_hory" to { "🪧 Rozcestník z ohlazených prken:\n↑ Socha krále Mlsáka · ↖ Důl a horní stezka\n← Tábor · ↓ Zpět na louku" }),
            BiomeType.SKY_PASS to mapOf(
                SkyPass.CLAWS_NODE to { "🐉 Do skály jsou vyryté hluboké rýhy po drápech a leží tu šupina rudá jako žhavé uhlí.\n" +
                    "Drakirra tudy odletěla – stopy míří dolů, nad korunami Hvozdu." },
                SkyPass.CAIRN_NODE to { "🪨 Mužík z plochých kamenů. Poutníci sem pokládají kámen pro štěstí na cestě mezi světy.\n" +
                    "Na tom nejvyšším je vyrytý list." }),
            BiomeType.MINES to mapOf(MinesMap.DOOR_NODE to { "🚪 " + MinesMap.DOOR_TEXT + (if (insight()) MinesMap.DOOR_TEXT_INSIGHT else "") +
                (if (StoryFlags.isSet(this, Vendelin.BOOK_SIGNED_KEY)) Vendelin.DOOR_HANDWRITING else "") }),
            BiomeType.HIDDEN_GROVE to mapOf(SecretGrove.POOL_NODE to { "✨ " + SecretGrove.POOL_TEXT }),
            BiomeType.ASH_PLAIN to mapOf(AP.SIGN_NODE to { AP.SIGN_TEXT }, AP.MILESTONE_NODE to { AP.MILESTONE_TEXT + if (insight()) AP.MILESTONE_TEXT_INSIGHT else "" }),
            BiomeType.OZVENA to mapOf(OC.BOARD_NODE to { OC.BOARD_TEXT }, OC.FOUNTAIN_NODE to { OC.FOUNTAIN_TEXT }, OC.SHED_NODE to { OC.SHED_TEXT }),
            BiomeType.TOWER_LOBBY to mapOf(TL.WAITING_NODE to { TL.WAITING_TEXT }, TL.FILES_NODE to { TL.FILES_TEXT }, TL.FILES2_NODE to { TL.FILES2_TEXT },
                TL.PORTRAITS_NODE to { TL.PORTRAITS_TEXT + if (StoryFlags.insight(this) >= 3) TL.PORTRAITS_INSIGHT else "" }),
            BiomeType.ARENA_HALL to mapOf(cz.uhk.macroflow.pokemon.cave.ArenaHall.WANTED_NODE to { cz.uhk.macroflow.pokemon.cave.ArenaHall.WANTED_TEXT }),
            BiomeType.ECHO_CANYON to mapOf(EC.BRIDGE_NODE to { EC.BRIDGE_TEXT }, EC.BONES_NODE to { EC.BONES_TEXT },
                EC.WALL_NODE to { EC.echo(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)) }),
            BiomeType.QUIET_ORCHARD to mapOf(cz.uhk.macroflow.pokemon.cave.QuietOrchard.WELL_NODE to {
                if (fennStage() == cz.uhk.macroflow.pokemon.story.Fenn.NAMES_STAGE && !orchardNight()) cz.uhk.macroflow.pokemon.story.Fenn.WELL_DAY
                else cz.uhk.macroflow.pokemon.cave.QuietOrchard.WELL_TEXT })
        )
    }

    private fun inspectText(node: String): String? = when (currentBiome) {
        BiomeType.TOWER_OFFICE -> cz.uhk.macroflow.pokemon.cave.TowerOffice.textFor(node, StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.TowerStory.OBJ019_KEY))
        BiomeType.ROOT_FOREST -> cz.uhk.macroflow.pokemon.cave.RootForest.textFor(node, forestRested)
        BiomeType.ROOT_NAMELESS -> cz.uhk.macroflow.pokemon.story.SlopeWater.textFor(node,
            StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.SlopeWater.NAMES_KEY)) ?: inspectMaps[currentBiome]?.get(node)
        else -> inspectMaps[currentBiome]?.get(node) ?: inspectExtra[currentBiome]?.get(node)?.invoke()
    }

    // ── Les pod korunou: vedlejší úkol Jména v mlze (docs/adr/0107) ──

    /** Hráč se vyspal (úkol je za fází spánku) – značky na kůře jdou přečíst a jmenovky jsou vidět. */
    private var forestRested = false
    private val forestTagViews = mutableMapOf<String, List<View>>()

    /** Fáze úkolu z databáze (nemusí být zrovna aktivní úkol): index, hotovo; null = nezačal. */
    private suspend fun questProgress(id: String): Pair<Int, Boolean>? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching { db.questDao().getAllQuests().firstOrNull { it.questId == id } }
            .getOrNull()?.let { it.currentStageIndex to it.isCompleted }
    }

    /** Jmenovky ve větvičkách: vidět jsou až po spánku, sebrané zmizí; plech se pohupuje a třpytí. */
    private fun placeForestTags() {
        val F = cz.uhk.macroflow.pokemon.story.ForestNames
        lifecycleScope.launch {
            val (stage, done) = questProgress(F.QUEST_ID) ?: (-1 to false)
            forestRested = done || stage >= F.TAGS_STAGE
            if (!forestRested || currentBiome != BiomeType.ROOT_FOREST || worldScale <= 0) return@launch
            val cave = cz.uhk.macroflow.pokemon.cave.RootForest.MAP
            val s = worldScale
            val rows = listOf("...b...", "...b...", "..ooo..", ".oSSSo.", ".oSdSo.", ".oSSSo.", ".oSdSo.", ".oSSSo.", "..ooo..")
            val px = IntArray(7 * 9) { i ->
                when (rows[i / 7][i % 7]) { 'b' -> 0xFF8C6A44.toInt(); 'o' -> 0xFF1C2026.toInt(); 'S' -> 0xFFC4CCD4.toInt(); 'd' -> 0xFF6A7280.toInt(); else -> 0 }
            }
            for (tag in F.TAGS) {
                if (StoryFlags.isSet(this@MakromonMapActivity, tag.key)) continue
                val n = cave.node(tag.node) ?: continue
                val glow = glowView(12 * s, 0xFFDFF8FF.toInt(), 0x88).apply { x = n.x * s - 6f * s; y = (n.y - 17.5f) * s - 6f * s; elevation = 2.9f }
                val v = pixelView(px, 7, 9, 7 * s, 9 * s).apply {
                    x = n.x * s - 3.5f * s; y = (n.y - 22) * s.toFloat(); elevation = 3f
                    contentDescription = "Jmenovka"
                    setOnClickListener { triggerHotspotAction(tag.node) }
                }
                addDecor(glow); addDecor(v)
                bob(v, 2f * s, 1900L + (n.x % 5) * 170)
                crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.2f, 0.85f, 0.2f).apply {
                    duration = 1500L + (n.y % 7) * 120; repeatCount = android.animation.ValueAnimator.INFINITE; start()
                }
                forestTagViews[tag.node] = listOf(glow, v)
            }
        }
    }

    /** Jmenovka: po spánku se dá sebrat (panel Prozkoumáno), před ním ji mlha rozmaže. */
    private fun onForestTag(node: String) {
        val F = cz.uhk.macroflow.pokemon.story.ForestNames
        val tag = F.tagAt(node) ?: return
        if (StoryFlags.isSet(this, tag.key)) return
        if (!forestRested || !StoryFlags.isSet(this, F.STARTED_KEY)) { showMapToast(F.TAG_TOO_EARLY); return }
        StoryFlags.set(this, tag.key)
        val views = forestTagViews.remove(node).orEmpty()
        views.firstOrNull()?.animate()?.alpha(0f)?.setDuration(300)?.start()           // záře zhasne, plíšek povyskočí
        val count = F.TAGS.count { StoryFlags.isSet(this, it.key) }
        val n = cz.uhk.macroflow.pokemon.cave.RootForest.MAP.node(node)
        val s = worldScale.toFloat()
        cz.uhk.macroflow.pokemon.inspect.PickupFx.play(mapWorld, (n?.x ?: 0) * s, ((n?.y ?: 0) - 17.5f) * s, s, 0xFFDFF8FF.toInt(),
            "🏷️ $count/${F.TAGS.size}", views.getOrNull(1)) {
            if (isFinishing) return@play
            cz.uhk.macroflow.pokemon.inspect.InspectPanel.show(findViewById(R.id.mapRootContainer), "Les pod korunou", F.tagFound(tag, count))
            questManager.loadQuest(F.QUEST_ID)
            mapWorld.postDelayed({ if (!isFinishing) questManager.recheck() }, 200)
        }
    }

    /** Gudwin u kruhu stop: první setkání spustí úkol, po spánku pošle pro jmenovky, se všemi je přečte nahlas. */
    private fun onForestGudwin() {
        val F = cz.uhk.macroflow.pokemon.story.ForestNames
        StoryFlags.set(this, F.STARTED_KEY)
        questManager.loadQuest(F.QUEST_ID)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            questManager.recheck()
            mapWorld.postDelayed({
                if (isFinishing) return@postDelayed
                val p = questManager.getCurrentProgress()?.takeIf { questManager.getActiveQuestId() == F.QUEST_ID }
                val stage = p?.currentStageIndex ?: 0; val done = p?.isCompleted ?: false
                val all = F.TAGS.all { StoryFlags.isSet(this, it.key) }
                when {
                    done -> showMapToast(F.IDLE)
                    stage == F.FINAL_STAGE && all && !StoryFlags.isSet(this, F.FINAL_KEY) -> {
                        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Jména nahlas", F.NAME) { ui, body, _ ->
                            body.addView(ui.text(F.FINAL, 15f))
                        }
                        StoryFlags.set(this, F.FINAL_KEY)
                        questManager.recheck()
                    }
                    else -> questManager.checkNpcInteraction()
                }
                if (!forestRested && stage >= F.TAGS_STAGE) refreshStoryDecor()        // po spánku se objeví jmenovky
            }, 250)
        }, 150)
    }

    // ── Cesta zpátky (docs/adr/0108): sedm zhaslých luceren výprav napříč Hlubinou ──

    /** Uzel, u kterého hráč klepl na lucernu – akce místa ji pak zapálí místo běžné akce. */
    private var pendingLantern: String? = null
    private val lanternViews = mutableMapOf<String, View>()

    private fun lanternPixels(lit: Boolean): IntArray {
        val rows = listOf("..mmm..", "...m...", ".ooooo.", ".oGGGo.", "oGgFgGo", "oGFfFGo", "oGgFgGo", ".oGGGo.", ".ooooo.", "...m...", "...m...", "...m...", "..mmm..")
        val pal = if (lit) mapOf('G' to 0xFFF2C94C, 'g' to 0xFFFFE08A, 'F' to 0xFFFFF4C0, 'f' to 0xFFFFFFFF)
            else mapOf('G' to 0xFF3A4048, 'g' to 0xFF2E343A, 'F' to 0xFF2A3036, 'f' to 0xFF262A30)
        return IntArray(7 * rows.size) { i ->
            when (val ch = rows[i / 7][i % 7]) { 'o' -> 0xFF1A1410.toInt(); 'm' -> 0xFF5A5A62.toInt(); '.' -> 0; else -> pal.getValue(ch).toInt() }
        }
    }

    /** Lucerny této lokace: zhaslé šedé, zapálené svítí a plamen se chvěje; klepnutí = dojít a zapálit. */
    private fun placeLanterns() {
        val cave = BiomeRegistry.definition(currentBiome)?.cave ?: return
        val L = cz.uhk.macroflow.pokemon.story.Lanterns
        val s = worldScale
        lanternViews.clear()
        for (l in L.inBiome(currentBiome.name)) {
            val n = cave.node(l.node) ?: continue
            val lit = StoryFlags.isSet(this, l.key)
            val cx = n.x + 8; val bottom = n.y
            if (lit) {
                val glow = glowView(22 * s, 0xFFFFC860.toInt(), 0xAA).apply { x = cx * s - 11f * s; y = (bottom - 8) * s - 11f * s; elevation = 2.9f }
                addDecor(glow)
                crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.75f, 1f, 0.6f, 0.95f, 0.75f).apply {
                    duration = 1300L + (n.x % 7) * 90; repeatCount = android.animation.ValueAnimator.INFINITE; start()
                }
            }
            val v = pixelView(lanternPixels(lit), 7, 13, 7 * s, 13 * s).apply {
                x = cx * s - 3.5f * s; y = (bottom - 13) * s.toFloat(); elevation = 3f
                contentDescription = if (lit) "Zapálená lucerna" else "Zhaslá lucerna"
                setOnClickListener { pendingLantern = l.node; triggerHotspotAction(l.node) }
            }
            addDecor(v)
            lanternViews[l.node] = v
        }
    }

    private fun onLantern(l: cz.uhk.macroflow.pokemon.story.Lanterns.Lantern) {
        val L = cz.uhk.macroflow.pokemon.story.Lanterns
        if (StoryFlags.isSet(this, l.key)) { showMapToast(L.ALREADY); return }
        if (!StoryFlags.isSet(this, L.STARTED_KEY)) { showMapToast(L.UNLIT); return }
        StoryFlags.set(this, l.key)
        val count = L.ALL.count { StoryFlags.isSet(this, it.key) }
        val n = BiomeRegistry.definition(currentBiome)?.cave?.node(l.node) ?: return
        val s = worldScale.toFloat()
        lanternViews[l.node]?.let { v -> (v as? ImageView)?.setImageBitmap(android.graphics.Bitmap.createBitmap(lanternPixels(true), 7, 13, android.graphics.Bitmap.Config.ARGB_8888)) }
        cz.uhk.macroflow.pokemon.inspect.PickupFx.play(mapWorld, (n.x + 8) * s, (n.y - 7) * s, s, 0xFFFFC860.toInt(), "🏮 $count/${L.ALL.size}") {
            if (isFinishing) return@play
            cz.uhk.macroflow.pokemon.inspect.InspectPanel.show(findViewById(R.id.mapRootContainer),
                cz.uhk.macroflow.pokemon.zone.ZoneOne.NAMES[currentBiome.name] ?: "", L.litText(l, count)) { refreshStoryDecor() }
            questManager.loadQuest(L.QUEST_ID)
            mapWorld.postDelayed({ if (!isFinishing) questManager.recheck() }, 200)
        }
    }

    /** Pixelová mřížka aktuální mapy: násobek, rozměr v art px a poloha uzlu (jeskyně i grafové mapy na celou obrazovku). */
    private class ArtFrame(val s: Int, val artW: Int, val artH: Int, val pos: (String) -> Pair<Int, Int>?)

    private fun artFrame(): ArtFrame? {
        val def = BiomeRegistry.definition(currentBiome) ?: return null
        val cave = def.cave
        if (cave != null) return if (worldScale > 0) ArtFrame(worldScale, cave.artW, cave.artH) { id -> cave.node(id)?.let { it.x to it.y } } else null
        if (mapWorld.width <= 0) return null
        val s = (mapWorld.width / 160).coerceAtLeast(2)
        val w = mapWorld.width / s; val h = mapWorld.height / s
        return ArtFrame(s, w, h) { id -> BiomeRegistry.nodePos(def.graph, id)?.let { (it.x * w).toInt() to (it.y * h).toInt() } }
    }

    /**
     * Setkání jsou vidět ve všech lokacích: ve tmě (Hlubina, Dormitoř, Krystalová jeskyně, Jedová laboratoř) se u místa
     * občas rozsvítí pár očí, venku a v zarostlých patrech se tráva či kamení „zašustí“ (dvě čárky a poskočení).
     * Jeskyně a doly mají vlastní záři (placeEncounterGlows), vodu je vidět sama.
     */
    private fun placeEncounterMarks() {
        val def = BiomeRegistry.definition(currentBiome) ?: return
        val cave = def.cave
        if (cave?.isCave == true || currentBiome == BiomeType.MINES) return
        val f = artFrame() ?: return
        val nodes = (cave?.encounterNodes ?: def.graph.map { it.id }.filter { it in encounterNodes })
            .filterNot { it == "voda" || it.startsWith("jezirko") }
        val dark = currentBiome.name.startsWith("ROOT_") || currentBiome in setOf(BiomeType.TOWER_DORM, BiomeType.TOWER_CAVE, BiomeType.TOWER_POISON)
        val s = f.s
        nodes.forEachIndexed { i, id ->
            val (nx, ny) = f.pos(id) ?: return@forEachIndexed
            if (dark) {
                val col = when (currentBiome) {
                    BiomeType.ROOT_FOREST -> 0xFFE8B84A.toInt(); BiomeType.ROOT_TEMPLE, BiomeType.TOWER_POISON -> 0xFFC8A8FF.toInt()
                    BiomeType.ROOT_BRIDGE -> 0xFFB8F070.toInt(); else -> 0xFFCFF2C0.toInt()
                }
                val v = pixelView(intArrayOf(col, 0, 0, col), 4, 1, 4 * s, s).apply {
                    x = (nx - 2 + (i % 3) - 1) * s.toFloat(); y = (ny - 12 - (i % 2) * 3) * s.toFloat(); elevation = 2.7f; alpha = 0f
                }
                addDecor(v)
                crystalAnimators += android.animation.ObjectAnimator.ofFloat(v, "alpha", 0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 0f, 0f, 0f).apply {
                    duration = 4200L + (i * 977L) % 2600; startDelay = (i * 1311L) % 3000
                    repeatCount = android.animation.ValueAnimator.INFINITE; start()
                }
            } else {
                val c = 0xFFF4F8E8.toInt()                                   // „šust“: dvě čárky nad místem, poskočí a zmizí
                val v = pixelView(intArrayOf(c, 0, 0, 0, 0, c, 0, c, 0, 0, c, 0), 6, 2, 6 * s, 2 * s).apply {
                    x = (nx - 3) * s.toFloat(); y = (ny - 10) * s.toFloat(); elevation = 2.7f; alpha = 0f
                }
                addDecor(v)
                val by = v.translationY
                crystalAnimators += android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 3600L + (i * 733L) % 2200; startDelay = (i * 911L) % 2500
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    addUpdateListener {
                        val t = it.animatedValue as Float
                        val on = t in 0.0f..0.18f || t in 0.26f..0.4f            // dvakrát krátce zašustí
                        v.alpha = if (on) 1f else 0f
                        v.translationY = by - (if (on && (t < 0.09f || t in 0.26f..0.33f)) s else 0)
                    }
                    start()
                }
            }
        }
    }

    // ── Sběratelské předměty zón 1, 2 a Věže (story/Collectibles, docs/adr/0109) ──

    private var pendingCollect: String? = null

    private fun collectiblePixels(kind: cz.uhk.macroflow.pokemon.story.Collectibles.Kind): Pair<IntArray, Int> {
        val (rows, pal) = when (kind) {
            cz.uhk.macroflow.pokemon.story.Collectibles.Kind.PAGE -> listOf("ooooooo.", "oPPPPPoo", "oPllllPo", "oPPPPPPo", "oPllllPo", "oPPPPPPo", "oPlllPPo", "oooooooo") to
                mapOf('P' to 0xFFF2E6C4, 'l' to 0xFFA08A60)
            cz.uhk.macroflow.pokemon.story.Collectibles.Kind.LETTER -> listOf("oooooooo", "oEEEEEEo", "oeEEEEeo", "oEeEEeEo", "oEEeeEEo", "oEEErEEo", "oEEEEEEo", "oooooooo") to
                mapOf('E' to 0xFFE8DCC0, 'e' to 0xFF9C8A6A, 'r' to 0xFFB8342C)
            cz.uhk.macroflow.pokemon.story.Collectibles.Kind.LOG -> listOf(".ooooooo", "obBBBBBo", "obBBmBBo", "obBmBBBo", "obBBmBBo", "obBBBBBo", "obBBBBBo", ".ooooooo") to
                mapOf('b' to 0xFF20305A, 'B' to 0xFF3A5A9A, 'm' to 0xFFF2E6A0)
        }
        return IntArray(8 * 8) { i -> when (val ch = rows[i / 8][i % 8]) { 'o' -> 0xFF1A1410.toInt(); '.' -> 0; else -> pal.getValue(ch).toInt() } } to 8
    }

    /** Nesebrané předměty v této lokaci: malý sprite se zářičkou, pomalu se pohupuje; klepnutí = dojít a sebrat. */
    private fun placeCollectibles() {
        val C = cz.uhk.macroflow.pokemon.story.Collectibles
        val items = C.inBiome(currentBiome.name).filterNot { StoryFlags.isSet(this, it.key) }
        if (items.isEmpty()) return
        val f = artFrame() ?: return
        val s = f.s
        collectViews.clear()
        items.forEachIndexed { i, item ->
            val (nx, ny) = f.pos(item.node) ?: return@forEachIndexed
            val set = C.set(item.set)
            val cx = nx + item.dx; val cy = ny + item.dy - 4                  // střed spritu 8 × 8
            val glow = glowView(14 * s, set.glow.toInt(), 0x99).apply { x = cx * s - 7f * s; y = cy * s - 7f * s; elevation = 2.9f }
            val (px, w) = collectiblePixels(set.kind)
            val v = pixelView(px, w, w, w * s, w * s).apply {
                x = cx * s - 4f * s; y = cy * s - 4f * s; elevation = 3f; rotation = if (set.kind == cz.uhk.macroflow.pokemon.story.Collectibles.Kind.PAGE) -8f else 0f
                contentDescription = set.label
                setOnClickListener { pendingCollect = item.node; triggerHotspotAction(item.node) }
            }
            addDecor(glow); addDecor(v)
            bob(v, s.toFloat(), 2100L + i * 170)
            crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.3f, 0.9f, 0.3f).apply {
                duration = 1700L + i * 130; repeatCount = android.animation.ValueAnimator.INFINITE; start()
            }
            collectViews[item.node] = listOf(glow, v)
        }
    }

    private val collectViews = mutableMapOf<String, List<View>>()

    /** Sebrání předmětu: animace, panel s textem a počtem; první předmět spustí úkol a ukáže jeho úvod. */
    private fun onCollect(item: cz.uhk.macroflow.pokemon.story.Collectibles.Item) {
        val C = cz.uhk.macroflow.pokemon.story.Collectibles
        if (StoryFlags.isSet(this, item.key)) return
        StoryFlags.set(this, item.key)
        val set = C.set(item.set)
        val count = set.items.count { StoryFlags.isSet(this, it.key) }
        val views = collectViews.remove(item.node).orEmpty()
        views.firstOrNull()?.animate()?.alpha(0f)?.setDuration(300)?.start()
        val f = artFrame()
        val (nx, ny) = f?.pos(item.node) ?: (0 to 0)
        val s = (f?.s ?: 1).toFloat()
        cz.uhk.macroflow.pokemon.inspect.PickupFx.play(mapWorld, (nx + item.dx) * s, (ny + item.dy - 4) * s, s, set.glow.toInt(),
            "${set.icon} $count/${set.items.size}", views.getOrNull(1)) {
            if (isFinishing) return@play
            cz.uhk.macroflow.pokemon.inspect.InspectPanel.show(findViewById(R.id.mapRootContainer), set.label,
                "${set.icon} ${item.text}\n\n${set.label} $count ze ${set.items.size}.") {
                questManager.loadQuest(set.questId)
                mapWorld.postDelayed({
                    if (isFinishing) return@postDelayed
                    if (count == 1) questManager.checkNpcInteraction() else questManager.recheck()   // první kus = úvod úkolu
                }, 250)
            }
        }
    }

    /** Pošťák Lio (úkol Nedoručené dopisy): vypráví o brašně, se všemi dopisy je doručí. Po úkolu běžné repliky. */
    private fun onLetters(atBoard: Boolean): Boolean {
        val C = cz.uhk.macroflow.pokemon.story.Collectibles
        if (StoryFlags.isSet(this, C.LETTERS_FINAL_KEY)) return false
        val all = C.LETTERS_SET.items.all { StoryFlags.isSet(this, it.key) }
        if (atBoard && !all) return false
        questManager.loadQuest(C.LETTERS)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            questManager.recheck()
            mapWorld.postDelayed({
                if (isFinishing) return@postDelayed
                val p = questManager.getCurrentProgress()?.takeIf { questManager.getActiveQuestId() == C.LETTERS }
                if (all && (p?.currentStageIndex ?: 0) >= C.LETTERS_FINAL_STAGE) {
                    cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Dopisy bez adres", if (atBoard) null else C.LETTERS_NAME) { ui, body, _ ->
                        body.addView(ui.text(if (atBoard) C.LETTERS_BOARD else C.LETTERS_DELIVER, 15f))
                    }
                    StoryFlags.set(this, C.LETTERS_FINAL_KEY)
                    questManager.recheck()
                } else questManager.checkNpcInteraction()
            }, 250)
        }, 150)
        return true
    }

    /** Střed místa na mapě ve světových souřadnicích (oblast klepnutí, uzel nebo bod grafu). */
    private fun nodeWorldPos(node: String): Pair<Float, Float>? {
        val def = BiomeRegistry.definition(currentBiome) ?: return null
        val cave = def.cave
        if (mapWorld.width <= 0) return null
        if (cave != null) {
            val fx = mapWorld.width.toFloat() / cave.artW; val fy = mapWorld.height.toFloat() / cave.artH
            cave.tapAreas[node]?.let { (x, y, _) -> return x * fx to y * fy }
            return cave.node(node)?.let { it.x * fx to (it.y - 10) * fy }
        }
        return BiomeRegistry.nodePos(def.graph, node)?.let { it.x * mapWorld.width to it.y * mapWorld.height - 18 * resources.displayMetrics.density }
    }

    /** Animace sebrání u místa [node], pak [then] (panel / list). */
    private fun collectFx(node: String, label: String?, color: Int = 0xFFFFF2B0.toInt(), then: () -> Unit) {
        val (x, y) = nodeWorldPos(node) ?: run { then(); return }
        val px = if (worldScale > 0) worldScale.toFloat() else resources.displayMetrics.density * 3
        cz.uhk.macroflow.pokemon.inspect.PickupFx.play(mapWorld, x, y, px, color, label) { if (!isFinishing) then() }
    }

    /** Místa s příběhovým textem v aktuální lokaci (bez východů – ty mají šipky). */
    private fun inspectNodes(): Set<String> {
        val cave = BiomeRegistry.definition(currentBiome)?.cave
            ?: return inspectExtra[currentBiome]?.keys.orEmpty() - cz.uhk.macroflow.pokemon.cave.ExitMarkers.EXITS[currentBiome.name].orEmpty().map { it.node }.toSet()
        val exits = cz.uhk.macroflow.pokemon.cave.ExitMarkers.EXITS[currentBiome.name].orEmpty().map { it.node }.toSet()
        // místo s čekajícím roztrženým listem má vlastní třpytky – druhý třpyt vedle by vypadal jako další předmět
        val flags = StoryFlags.all(this)
        val pages = Insight.PAGES.filter { Insight.pageAt(currentBiome.name, it.node, flags) == it }.map { it.node }.toSet()
        return (inspectMaps[currentBiome]?.keys.orEmpty() + inspectExtra[currentBiome]?.keys.orEmpty()) - exits - cave.exitNode - pages
    }

    private fun inspectReadKey(node: String) = "inspect_read_${currentBiome.name}_$node"

    /** Krátké klepnutí na místo s textem jen dojde; prvních pár krát poradí, jak ho prozkoumat. */
    private fun onInspectTap() {
        val n = gamePrefs.getInt("inspect_hint_count", 0)
        if (n < 3) {
            gamePrefs.edit().putInt("inspect_hint_count", n + 1).apply()
            showMapToast("✨ Místa, která se třpytí, prozkoumáš tak, že na nich podržíš prst.")
        }
    }

    private var glitterView: View? = null

    private fun placeInspectGlitter() {
        val f = artFrame() ?: return
        val cave = BiomeRegistry.definition(currentBiome)?.cave
        val spots = inspectNodes().mapNotNull { id ->
            val (x, y) = cave?.tapAreas?.get(id)?.let { (x, y, _) -> x.toFloat() to y.toFloat() }
                ?: f.pos(id)?.let { (x, y) -> x.toFloat() to y - 10f } ?: return@mapNotNull null
            cz.uhk.macroflow.pokemon.inspect.InspectGlitterView.Spot(x, y, gamePrefs.getBoolean(inspectReadKey(id), false))
        }
        if (spots.isEmpty()) return
        val v = cz.uhk.macroflow.pokemon.inspect.InspectGlitterView(this, f.s, spots).apply {
            layoutParams = FrameLayout.LayoutParams(f.artW * f.s, f.artH * f.s)
            elevation = 2.8f
        }
        glitterView = v
        addDecor(v)
    }

    /** Místo k prozkoumání pod prstem (oblast klepnutí nebo nejbližší uzel v dosahu). */
    private fun inspectableAt(rawX: Float, rawY: Float): String? {
        val cave = BiomeRegistry.definition(currentBiome)?.cave
        val nodes = inspectNodes().takeIf { it.isNotEmpty() } ?: return null
        if (mapWorld.width <= 0) return null
        val loc = IntArray(2); mapWorld.getLocationOnScreen(loc)
        val relX = (rawX - loc[0]) / mapWorld.width; val relY = (rawY - loc[1]) / mapWorld.height
        cave?.tapAreaAt(relX * cave.artW, relY * cave.artH)?.takeIf { it in nodes }?.let { return it }
        val viewport = findViewById<View>(R.id.mapMainContent)
        return movementEngine.navigationGraph.filter { it.id in nodes }
            .map { it to MapCamera.tapDistance(it.pos.x, it.pos.y, relX, relY, mapWorld.width, mapWorld.height, viewport.width, viewport.height) }
            .filter { it.second < (if (cave != null) CAVE_TAP_RADIUS else TAP_RADIUS) }.minByOrNull { it.second }?.first?.id
    }

    private var holdRing: cz.uhk.macroflow.pokemon.inspect.HoldRingView? = null
    private var holdAnim: android.animation.ValueAnimator? = null
    private var holdDone = false

    private fun startHold(node: String, rawX: Float, rawY: Float) {
        cancelHold()
        val root = findViewById<FrameLayout>(R.id.mapRootContainer)
        val ring = cz.uhk.macroflow.pokemon.inspect.HoldRingView(this)
        val size = ring.sizePx
        val loc = IntArray(2); root.getLocationOnScreen(loc)
        ring.layoutParams = FrameLayout.LayoutParams(size, size)
        ring.x = rawX - loc[0] - size / 2f
        ring.y = rawY - loc[1] - size * 1.25f                              // nad prstem, ať je vidět
        ring.elevation = 41f
        ring.alpha = 0f
        root.addView(ring)
        holdRing = ring
        holdAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = cz.uhk.macroflow.pokemon.inspect.Inspect.HOLD_MS
            interpolator = android.view.animation.LinearInterpolator()
            startDelay = 180                                               // krátké klepnutí kruh vůbec neukáže
            addUpdateListener { ring.alpha = 1f; ring.progress = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    root.removeView(ring); if (holdRing === ring) holdRing = null
                    if (cancelled || isFinishing) return
                    holdDone = true
                    ring.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    openInspect(node)
                }
            })
            start()
        }
    }

    private fun cancelHold() {
        holdAnim?.cancel(); holdAnim = null
        holdRing?.let { (it.parent as? ViewGroup)?.removeView(it) }; holdRing = null
    }

    private fun openInspect(node: String) {
        val text = inspectText(node) ?: return
        gamePrefs.edit().putBoolean(inspectReadKey(node), true).apply()
        val place = cz.uhk.macroflow.pokemon.zone.ZoneOne.NAMES[currentBiome.name] ?: ""
        cz.uhk.macroflow.pokemon.inspect.InspectPanel.show(findViewById(R.id.mapRootContainer), place, text) {
            refreshStoryDecor()                                            // přečtené místo třpytí slaběji (a vysílačka odjistí závoru)
        }
    }

    /** Je přechod z [node] zatím zavřený příběhem? (šedá šipka se zámkem) */
    private fun exitLocked(node: String): Boolean = when (node) {
        cz.uhk.macroflow.pokemon.cave.RootCamp.DOWN_NODE -> !stationBarrierOpen()
        "su_lanovka" -> !StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Signal.ANSWER_KEY) && !devKeycard()
        cz.uhk.macroflow.pokemon.cave.RootThreshold.BRANCH_NODE -> !StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.DrakirraFinal.DEFEATED_KEY) && !devKeycard()
        cz.uhk.macroflow.pokemon.cave.RootTrunk.CRACK_NODE -> trpitelEnding() == null && !devKeycard()   // puklina se otevře volbou u Trpitele
        cz.uhk.macroflow.pokemon.cave.OzvenaCity.TOWER_NODE -> !StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Masters.INVITE_KEY) && !coupon()
        cz.uhk.macroflow.pokemon.cave.OzvenaCity.LIFT_NODE -> !cz.uhk.macroflow.pokemon.story.AbyssLift.open { StoryFlags.isSet(this, it) } && !coupon()
        "hory" -> !(BuildConfig.DEBUG && gamePrefs.getBoolean(DEBUG_BOOTS_KEY, false) || coupon()) && !BiomeAccess.canEnter(BiomeType.MOUNTAINS, currentDailySteps)
        else -> false
    }

    /**
     * Šipky východů (cave/ExitMarkers): u každého přechodu v Hlubině se vznáší zlatá pixelová šipka natočená tam, kam se
     * jde, pohupuje se o 2 px (po celých pixelech) a pod ní dýchá slabá záře. Klepnutí na šipku nebo záři = na východ.
     */
    private fun placeExitMarkers() {
        val def = BiomeRegistry.definition(currentBiome) ?: return
        val cave = def.cave
        val exits = cz.uhk.macroflow.pokemon.cave.ExitMarkers.EXITS[currentBiome.name] ?: return
        // grafové mapy (Město, Louka, Hory) jsou na celou obrazovku – „art pixel“ je zlomek šířky světa
        val s = if (cave != null) worldScale else (mapWorld.width / 160).coerceAtLeast(2)
        if (s <= 0 || mapWorld.width <= 0) return
        val artW = cave?.artW ?: (mapWorld.width / s); val artH = cave?.artH ?: (mapWorld.height / s)
        val EM = cz.uhk.macroflow.pokemon.cave.ExitMarkers
        exits.forEachIndexed { i, ex ->
            val (nx, ny) = cave?.node(ex.node)?.let { it.x to it.y }
                ?: BiomeRegistry.nodePos(def.graph, ex.node)?.let { (it.x * artW).toInt() to (it.y * artH).toInt() } ?: return@forEachIndexed
            val d = ex.dir
            val (ax, ay) = EM.anchor(nx, ny, d, artW, artH)
            val locked = exitLocked(ex.node)
            val size = EM.SIZE * s
            val tap = View.OnClickListener { triggerHotspotAction(ex.node) }
            val glow = glowView(size * 3, if (locked) 0xFFB0B0B8.toInt() else 0xFFFFE08A.toInt(), if (locked) 0x55 else 0x99).apply {
                x = ax * s - size * 1.5f; y = ay * s - size * 1.5f; elevation = 3f
                setOnClickListener(tap)
            }
            val arrow = pixelView(EM.arrowPixels(d, locked), EM.SIZE, EM.SIZE, size, size).apply {
                x = ax * s - size / 2f; y = ay * s - size / 2f; elevation = 3.2f
                setOnClickListener(tap); contentDescription = "Východ: ${ex.label}"
            }
            addDecor(glow); addDecor(arrow)
            crystalAnimators += android.animation.ValueAnimator.ofFloat(0f, 1f, 0f).apply {   // pohupování po celých pixelech
                duration = 1400; startDelay = i * 230L; repeatCount = android.animation.ValueAnimator.INFINITE
                val ox = d == cz.uhk.macroflow.pokemon.cave.ExitMarkers.Dir.LEFT || d == cz.uhk.macroflow.pokemon.cave.ExitMarkers.Dir.RIGHT
                val sign = if (d == cz.uhk.macroflow.pokemon.cave.ExitMarkers.Dir.UP || d == cz.uhk.macroflow.pokemon.cave.ExitMarkers.Dir.LEFT) -1 else 1
                val bx = arrow.translationX; val by = arrow.translationY   // x/y už nastavily translaci – pohupuje se kolem ní
                addUpdateListener {
                    val off = Math.round((it.animatedValue as Float) * 2f) * s * sign.toFloat()
                    if (ox) arrow.translationX = bx + off else arrow.translationY = by + off
                }
                start()
            }
            if (!locked) crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.35f, 0.9f, 0.35f).apply {
                duration = 1800; repeatCount = android.animation.ValueAnimator.INFINITE; start()
            }
        }
    }

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

    /**
     * Obyvatelé Ozvěny (docs/adr/0093): Makromoni z Makrodexu (obrázek 640 px = 64 px pixel artu po 10),
     * zmenšení bez vyhlazení na 1 px = 1 dp, pata na art souřadnici; lehce dýchají, každý jiným tempem.
     */
    private fun placeTownsfolk() {
        for (t in cz.uhk.macroflow.pokemon.cave.OzvenaCity.TOWNSFOLK) {
            if (cz.uhk.macroflow.pokemon.story.MemoryBookStore.isGone(this, t.id)) continue   // Bezejmenní si ho vzali (Tobben i koncem A u Fenna, docs/adr/0101)
            val res = when (t.species) {
                1 -> R.drawable.makromon_01_ignar; 38 -> R.drawable.makromon_38_johnsova; 7 -> R.drawable.makromon_07_flori
                32 -> R.drawable.makromon_32_mysnic; 4 -> R.drawable.makromon_04_aqulin; 31 -> R.drawable.makromon_31_axlu
                20 -> R.drawable.makromon_20_finlet; 39 -> R.drawable.makromon_39_grifben; 27 -> R.drawable.makromon_27_phantil
                else -> R.drawable.makromon_44_gemak
            }
            placeMakromonNpc(res, t.x, t.y, t.id)
        }
        // Pamětní kniha (docs/adr/0101): aktivní den v Ozvěně, mizení a scéna prvního neviděného zmizení
        cz.uhk.macroflow.pokemon.story.MemoryBookStore.onEnter(this)?.let { scene -> mapWorld.postDelayed({ if (!isFinishing) showMapToast(scene) }, 700) }
    }

    /**
     * Makromon jako postava na mapě: obrázek z Makrodexu (640 px = 64 px pixel artu po 10) zmenšený bez vyhlazení
     * na 1 px = 1 dp, pata na art souřadnici [x], [y]; lehce dýchá, každý jiným tempem.
     */
    private fun placeMakromonNpc(res: Int, x: Int, y: Int, desc: String): ImageView {
        val s = worldScale.toFloat()
        val px = kotlin.math.max(1, kotlin.math.round(resources.displayMetrics.density).toInt())
        val big = android.graphics.BitmapFactory.decodeResource(resources, res, android.graphics.BitmapFactory.Options().apply { inScaled = false })
        val bmp = android.graphics.Bitmap.createScaledBitmap(big, big.width / 10, big.height / 10, false)
        var foot = bmp.height - 1
        while (foot > 0 && (0 until bmp.width).none { (bmp.getPixel(it, foot) ushr 24) != 0 }) foot--
        val w = bmp.width * px; val h = bmp.height * px
        val npc = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(w, h)
            setImageDrawable(android.graphics.drawable.BitmapDrawable(resources, bmp).apply { isFilterBitmap = false })
            scaleType = ImageView.ScaleType.FIT_XY
            this.x = x * s - w / 2f; this.y = y * s - (foot + 1) * px; elevation = 2f
            pivotX = w / 2f; pivotY = (foot + 1f) * px
            contentDescription = desc
        }
        addDecor(npc)
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(npc, "scaleY", 1f, 1.035f, 1f).apply {
            duration = 1600L + (x * 7 + y) % 700; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
        return npc
    }

    // ── Ztichlý sad: Zahradník, který sázel jména (docs/adr/0096) ──

    private fun orchardNight() = cz.uhk.macroflow.pokemon.cave.QuietOrchard.isNight(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    private var fennCutscenePlaying = false

    /** Aktuální fáze questu Fenna, je-li načtený a nedokončený; jinak -1. */
    private fun fennStage(): Int {
        val p = questManager.getCurrentProgress()
        return if (questManager.getActiveQuestId() == cz.uhk.macroflow.pokemon.story.Fenn.QUEST_ID && p != null && !p.isCompleted) p.currentStageIndex else -1
    }

    /** Stopa ke jménu stromu na tomhle uzlu, pokud ji teď (ve správnou denní dobu) jde najít. */
    private fun fennClueHere(node: String): cz.uhk.macroflow.pokemon.story.Fenn.Clue? {
        val clue = cz.uhk.macroflow.pokemon.story.Fenn.clueAt(node) ?: return null
        if (fennStage() != cz.uhk.macroflow.pokemon.story.Fenn.NAMES_STAGE || StoryFlags.isSet(this, clue.key) || clue.atNight != orchardNight()) return null
        return clue
    }

    /** Hráč najde jméno: zapíše ho do Pamětní knihy a rozhodne, jestli Fennovi řekne celou pravdu. */
    private fun onFennClue(clue: cz.uhk.macroflow.pokemon.story.Fenn.Clue) {
        if (!StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Ozvena.BOOK_KEY)) { showMapToast(cz.uhk.macroflow.pokemon.story.Fenn.NO_BOOK); return }
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), "Jméno: ${clue.treeName}",
            "Co řekneš Fennovi?") { ui, body, close ->
            body.addView(ui.text(clue.found, 15f).apply {
                setBackgroundColor(0x22101830)
                setPadding(ui.px(10f), ui.px(10f), ui.px(10f), ui.px(10f))
            })
            fun choice(label: String, truth: Boolean) = body.addView(ui.button(label) {
                close()
                StoryFlags.set(this@MakromonMapActivity, clue.key)
                if (truth) StoryFlags.set(this@MakromonMapActivity, clue.truthKey)
                showMapToast(cz.uhk.macroflow.pokemon.story.Fenn.WRITTEN + "\n\n" + if (truth) clue.truth else clue.nameOnly)
                mapWorld.postDelayed({ if (!isFinishing) { questManager.recheck(); refreshStoryDecor() } }, 600)
            }.apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = ui.px(8f) }
            })
            choice("Říct mu celou pravdu", true)
            choice("Říct mu jen jméno", false)
        }
    }

    /** Chalupa: ve dne začíná prolog (seznam se škrtnutými jmény), v noci je prázdná. */
    private fun onOrchardHut() {
        if (orchardNight()) { showMapToast(cz.uhk.macroflow.pokemon.story.Fenn.NIGHT_HUT); return }
        val started = StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Fenn.STARTED_KEY)
        if (!started) StoryFlags.set(this, cz.uhk.macroflow.pokemon.story.Fenn.STARTED_KEY)
        questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Fenn.QUEST_ID)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            if (!started || fennStage() == cz.uhk.macroflow.pokemon.story.Fenn.NELLE_STAGE) questManager.checkNpcInteraction()
            else showMapToast(cz.uhk.macroflow.pokemon.cave.QuietOrchard.HUT_TEXT)
        }, 200)
    }

    /**
     * Fenn stojí v noci u chalupy, jakmile ho hráč potkal. Při prvním nočním příchodu po rozhovoru s Nelle
     * se přehraje cutscéna „Světlo mezi stromy“ a Fenn pak sám promluví.
     */
    private fun placeFenn() {
        if (!StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Fenn.STARTED_KEY)) return
        questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Fenn.QUEST_ID)
        val has = { k: String -> StoryFlags.isSet(this, k) }
        if (cz.uhk.macroflow.pokemon.story.Fenn.fennStays(has)) pulseGlow(126f, 282f, 12f, 0xFFFFD27A.toInt())          // lucerna v chalupě svítí napořád
        if (!orchardNight()) return
        if (cz.uhk.macroflow.pokemon.story.Fenn.inTree(has)) { pulseGlow(80f, 258f, 30f, 0xFF9CC8FF.toInt()); return }  // Fenn ve Staré jabloni
        if (StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Fenn.MET_KEY)) {
            val npc = placeMakromonNpc(R.drawable.makromon_43_kendl, cz.uhk.macroflow.pokemon.story.Fenn.X, cz.uhk.macroflow.pokemon.story.Fenn.Y, cz.uhk.macroflow.pokemon.story.Fenn.NAME)
            val g = (14 * worldScale)
            val glow = glowView(g, 0xFF9CC8FF.toInt(), 0x88).apply {
                x = npc.x + npc.layoutParams.width / 2f - g / 2f; y = npc.y + npc.layoutParams.height * 0.12f - g / 2f; elevation = 1.9f
            }
            addDecor(glow)
            crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.5f, 1f, 0.7f, 0.95f, 0.5f).apply {
                duration = 1400; repeatCount = android.animation.ValueAnimator.INFINITE; start()
            }
        } else if (StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Fenn.NELLE_KEY) && !fennCutscenePlaying) playFennCutscene(meet = true)
    }

    private fun vessaText() = cz.uhk.macroflow.pokemon.cave.OzvenaCity.VENKA_TEXT + if (StoryFlags.isSet(this, cz.uhk.macroflow.pokemon.story.Fenn.END_A_KEY)) cz.uhk.macroflow.pokemon.story.Fenn.VESSA_AFTER_A else ""

    /** Pulzující záře na art souřadnici (lucerna, svítící jabloň). */
    private fun pulseGlow(x: Float, y: Float, size: Float, color: Int) {
        val g = (size * worldScale).toInt()
        val v = glowView(g, color, 0x88).apply { this.x = x * worldScale - g / 2f; this.y = y * worldScale - g / 2f; elevation = 1.9f }
        addDecor(v)
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(v, "alpha", 0.45f, 1f, 0.6f, 0.9f, 0.45f).apply {
            duration = 2400; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
    }

    /** Zlaté jablko jednou denně (konec A od Fenna, konec B/C z jabloně); jinak jen věta. */
    private fun giveDailyApple(given: String, otherwise: String) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        if (gamePrefs.getString(cz.uhk.macroflow.pokemon.story.Fenn.APPLE_DAY_PREF, null) == today) { showMapToast(otherwise); return }
        gamePrefs.edit().putString(cz.uhk.macroflow.pokemon.story.Fenn.APPLE_DAY_PREF, today).apply()
        lifecycleScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                cz.uhk.macroflow.pokemon.skills.SkillStore.add(applicationContext, cz.uhk.macroflow.pokemon.boost.RareBoost.GOLDEN_APPLE.id, 1)
            }
            showMapToast(given)
        }
    }

    /** Babička Nelle a jméno Maren (fáze „Co ví Nelle“): pravda o sadu, nebo fasáda. */
    private fun onNelleMaren() {
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), cz.uhk.macroflow.pokemon.story.Fenn.NELLE, "Maren") { ui, body, close ->
            body.addView(ui.text(cz.uhk.macroflow.pokemon.story.Fenn.NELLE_MAREN, 15f).apply {
                setBackgroundColor(0x22BC6C25); setPadding(ui.px(10f), ui.px(10f), ui.px(10f), ui.px(10f))
            })
            fun choice(label: String, truth: Boolean) = body.addView(ui.button(label) {
                close()
                StoryFlags.set(this@MakromonMapActivity, cz.uhk.macroflow.pokemon.story.Fenn.NELLE_TOLD_KEY)
                if (truth) StoryFlags.set(this@MakromonMapActivity, cz.uhk.macroflow.pokemon.story.Fenn.NELLE_TRUTH_KEY)
                showMapToast(if (truth) cz.uhk.macroflow.pokemon.story.Fenn.NELLE_TRUTH else cz.uhk.macroflow.pokemon.story.Fenn.NELLE_FACADE)
            }.apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = ui.px(8f) }
            })
            choice("Říct jí pravdu o sadu", true)
            choice("Nechat ji v klidu", false)
        }
    }

    /** Rozhovor s Fennem v noci: vyřízení od Nelle, finále, po konci A jablka. */
    private fun onFennTalk() {
        questManager.loadQuest(cz.uhk.macroflow.pokemon.story.Fenn.QUEST_ID)
        mapWorld.postDelayed({
            if (isFinishing) return@postDelayed
            val has = { k: String -> StoryFlags.isSet(this, k) }
            when {
                has(cz.uhk.macroflow.pokemon.story.Fenn.NELLE_TOLD_KEY) && !has(cz.uhk.macroflow.pokemon.story.Fenn.REPORTED_KEY) -> {
                    showMapToast(if (has(cz.uhk.macroflow.pokemon.story.Fenn.NELLE_TRUTH_KEY)) cz.uhk.macroflow.pokemon.story.Fenn.FENN_HEARS_TRUTH else cz.uhk.macroflow.pokemon.story.Fenn.FENN_HEARS_FACADE)
                    StoryFlags.set(this, cz.uhk.macroflow.pokemon.story.Fenn.REPORTED_KEY)
                    mapWorld.postDelayed({ if (!isFinishing) questManager.recheck() }, 1800)
                }
                fennStage() == cz.uhk.macroflow.pokemon.story.Fenn.FINALE_STAGE && !has(cz.uhk.macroflow.pokemon.story.Fenn.ENDING_KEY) && questIntroSeen() -> playFinale()
                cz.uhk.macroflow.pokemon.story.Fenn.fennStays(has) -> giveDailyApple(cz.uhk.macroflow.pokemon.story.Fenn.APPLE_FROM_FENN, cz.uhk.macroflow.pokemon.story.Fenn.FENN_AFTER)
                else -> questManager.checkNpcInteraction()
            }
        }, 150)
    }

    /** Úvod fáze Noc bez měsíce už zazněl (první oslovení ho přehraje, druhé spustí finále). */
    private fun questIntroSeen() =
        getSharedPreferences("QuestPrefs", Context.MODE_PRIVATE).getInt("intro_seen_" + cz.uhk.macroflow.pokemon.story.Fenn.QUEST_ID, -1) >= cz.uhk.macroflow.pokemon.story.Fenn.FINALE_STAGE

    /** Finále: cutscéna Bezejmenných, pak volba konce. */
    private fun playFinale() {
        cz.uhk.macroflow.pokemon.cutscene.CutsceneView.play(findViewById(android.R.id.content),
            cz.uhk.macroflow.pokemon.cutscene.NamelessCutsceneView(this)) { if (!isFinishing) chooseEnding() }
    }

    private fun chooseEnding() {
        val flags = StoryFlags.all(this)
        val secret = cz.uhk.macroflow.pokemon.story.Fenn.secretEndingOpen(cz.uhk.macroflow.pokemon.story.Fenn.truths { it in flags }, cz.uhk.macroflow.pokemon.story.Fenn.NELLE_TRUTH_KEY in flags, Insight.level(flags))
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), cz.uhk.macroflow.pokemon.story.Fenn.FINALE_TITLE,
            "Čí jméno si vezmou?") { ui, body, close ->
            body.addView(ui.text(cz.uhk.macroflow.pokemon.story.Fenn.FINALE, 15f).apply {
                setBackgroundColor(0x33101830); setPadding(ui.px(10f), ui.px(10f), ui.px(10f), ui.px(10f))
            })
            fun choice(label: String, e: cz.uhk.macroflow.pokemon.story.Fenn.Ending) = body.addView(ui.button(label) { close(); endFennQuest(e) }.apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = ui.px(8f) }
            })
            choice(cz.uhk.macroflow.pokemon.story.Fenn.CHOICE_A, cz.uhk.macroflow.pokemon.story.Fenn.Ending.A)
            choice(cz.uhk.macroflow.pokemon.story.Fenn.CHOICE_B, cz.uhk.macroflow.pokemon.story.Fenn.Ending.B)
            if (secret) choice(cz.uhk.macroflow.pokemon.story.Fenn.CHOICE_C, cz.uhk.macroflow.pokemon.story.Fenn.Ending.C)
        }
    }

    /** Konec linky: příznaky, Zlaté jablko a epilog; po zavření se quest uzavře a svět se změní. */
    private fun endFennQuest(e: cz.uhk.macroflow.pokemon.story.Fenn.Ending) {
        StoryFlags.set(this, cz.uhk.macroflow.pokemon.story.Fenn.endingKey(e)); StoryFlags.set(this, cz.uhk.macroflow.pokemon.story.Fenn.ENDING_KEY)
        gamePrefs.edit().putString(cz.uhk.macroflow.pokemon.story.Fenn.APPLE_DAY_PREF,
            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())).apply()
        lifecycleScope.launch(Dispatchers.IO) {
            cz.uhk.macroflow.pokemon.skills.SkillStore.add(applicationContext, cz.uhk.macroflow.pokemon.boost.RareBoost.GOLDEN_APPLE.id, 1)
        }
        cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(findViewById(R.id.mapRootContainer), cz.uhk.macroflow.pokemon.story.Fenn.FINALE_TITLE, "") { ui, body, close ->
            body.addView(ui.text(cz.uhk.macroflow.pokemon.story.Fenn.endingText(e), 15f).apply {
                setBackgroundColor(0x33101830); setPadding(ui.px(10f), ui.px(10f), ui.px(10f), ui.px(10f))
            })
            body.addView(ui.button("Pokračovat") {
                close()
                refreshStoryDecor()
                questManager.recheck()
            }.apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = ui.px(10f) }
            })
        }
    }

    /** Cutscéna „Světlo mezi stromy“; [meet] = poprvé ve hře (pak se Fenn objeví a promluví), jinak jen přehrání. */
    private fun playFennCutscene(meet: Boolean) {
        fennCutscenePlaying = true
        cz.uhk.macroflow.pokemon.cutscene.CutsceneView.play(findViewById(android.R.id.content),
            cz.uhk.macroflow.pokemon.cutscene.FennCutsceneView(this)) {
            fennCutscenePlaying = false
            if (meet && !isFinishing) {
                StoryFlags.set(this, cz.uhk.macroflow.pokemon.story.Fenn.MET_KEY)
                refreshStoryDecor()
                questManager.recheck()
            }
        }
    }

    /** Pekař Brann a písařka Vessa v kapitole Ozvěna (docs/adr/0095). */
    private fun onOzvenaNpc(venka: Boolean) {
        val O = cz.uhk.macroflow.pokemon.story.Ozvena
        val p = questManager.getCurrentProgress()
        val ours = questManager.getActiveQuestId() == O.QUEST_ID && p != null
        if (venka) cz.uhk.macroflow.pokemon.story.MemoryBookStore.takeVessaComment(this)?.let { showMapToast(it); return }
        when {
            !ours -> showMapToast(if (venka) vessaText() else cz.uhk.macroflow.pokemon.cave.OzvenaCity.BAKERY_TEXT)
            venka && !p!!.isCompleted && p.currentStageIndex == O.NAME_STAGE -> {
                StoryFlags.set(this, O.BOOK_KEY)          // Vessa hráče zapíše a dá mu knihu → fáze splněna, rozloučí se
                questManager.recheck()
            }
            venka -> if (p!!.isCompleted) questManager.checkNpcInteraction() else showMapToast(vessaText())
            p!!.isCompleted -> showMapToast(cz.uhk.macroflow.pokemon.cave.OzvenaCity.BAKERY_TEXT)   // smyčka: pořád stejný pozdrav
            else -> questManager.checkNpcInteraction()
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

    /** Živé detaily interiéru přes celý svět mapy (InteriorFxView). */
    private fun addInteriorFx(fx: cz.uhk.macroflow.pokemon.cave.InteriorFx) {
        val cave = BiomeRegistry.definition(currentBiome)?.cave ?: return
        addGroundDecor(cz.uhk.macroflow.pokemon.cave.InteriorFxView(this, worldScale, fx).apply {
            layoutParams = FrameLayout.LayoutParams(cave.artW * worldScale, cave.artH * worldScale)
        })
    }

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
                    val craftCost = cz.uhk.macroflow.pokemon.stamina.Stamina.Action.CRAFT.cost
                    if (cz.uhk.macroflow.pokemon.stamina.StaminaStore.load(ctx).total < craftCost) { payStamina(cz.uhk.macroflow.pokemon.stamina.Stamina.Action.CRAFT); return@craftMenu }
                    lifecycleScope.launch {
                        val res = kotlinx.coroutines.withContext(Dispatchers.IO) { SS.craft(ctx, ball, times) }
                        if (res == null) { showMapToast("Chybí suroviny."); return@launch }
                        payStamina(cz.uhk.macroflow.pokemon.stamina.Stamina.Action.CRAFT)
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
                    if (payStamina(cz.uhk.macroflow.pokemon.stamina.Stamina.Action.GATHER_START)) lifecycleScope.launch {
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
                    if (payStamina(cz.uhk.macroflow.pokemon.stamina.Stamina.Action.GUARDIAN))
                        startSpecialBattle(sp, battleBiome = currentBiome)
                }
                LegendProgress.Altar.CRYSTAL_READY -> takeCrystal(color)
                LegendProgress.Altar.EMPTY -> showMapToast("Oltář je prázdný – ${color.label} už je u tebe.")
            }
        }
    }

    // ── Ladění příběhu (jen debug build) ──

    /** Dev keycard (jen debug build): otevře zámky zóny 3, které jinak hlídá příběh. Zlatý kupón ji zahrnuje. */
    private fun devKeycard() = BuildConfig.DEBUG && gamePrefs.getBoolean(DEBUG_KEYCARD_KEY, false) || coupon()

    /** Zlatý kupón (jen debug build, zapínatelný): otevře každou lokaci zamčenou postupem, kroky i výdrží. */
    private fun coupon() = BuildConfig.DEBUG && gamePrefs.getBoolean(DEBUG_COUPON_KEY, false)

    private fun showDebugMenu() {
        val boots = gamePrefs.getBoolean(DEBUG_BOOTS_KEY, false)
        val lift = gamePrefs.getBoolean(DEBUG_LIFT_KEY, false)
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
            "🗝 Reset Zapomenutého háje",
            "🎬 Cutscény a videa ›",
            "🕯 Reset questu Fenna (Ztichlý sad)",
            "🕯 Fenn: skok na Noc bez měsíce",
            "🍫 +5× Proteinová tyčinka (+250 XP)",
            "💪 +5× Preworkout (+1 level)",
            "🥫 +5× Bílý Monster (+3 levely)",
            if (coupon()) "🎟️ Zlatý kupón: ZAPNUTO – vše odemčeno (vypnout)" else "🎟️ Zlatý kupón (odemkne všechny lokace)",
            if (lift) "🛗 Výtah volně: ZAPNUTO (vypnout)" else "🛗 Výtah volně (všechna patra)",
            if (gamePrefs.getBoolean(DEBUG_KEYCARD_KEY, false)) "🪪 Dev keycard: ZAPNUTO (vypnout)" else "🪪 Dev keycard (otevře závory a gondolu v Hlubině)",
            "🐉 Drakirra na Prahu poražena (otevře Kmen)"
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
                    12 -> showDebugCutscenes()
                    13 -> debugResetFenn()
                    14 -> debugFennFinale()
                    15, 16, 17 -> debugGiveBoost(cz.uhk.macroflow.pokemon.boost.RareBoost.entries[which - 15])
                    18 -> {
                        val on = coupon()
                        gamePrefs.edit().putBoolean(DEBUG_COUPON_KEY, !on).apply()
                        showMapToast(if (on) "🎟️ Zlatý kupón uschován – lokace zase podle postupu."
                            else "🎟️ Zlatý kupón! Otevře každou zamčenou lokaci: Věž, výtah, Hlubinu, gondolu, Kmen, kroky i výdrž.")
                        refreshStoryDecor()
                    }
                    19 -> {
                        gamePrefs.edit().putBoolean(DEBUG_LIFT_KEY, !lift).apply()
                        showMapToast(if (lift) "🛗 Výtah zase jen po kalibraci." else "🛗 Výtah jezdí volně do všech hotových pater.")
                    }
                    20 -> {
                        val on = gamePrefs.getBoolean(DEBUG_KEYCARD_KEY, false)
                        gamePrefs.edit().putBoolean(DEBUG_KEYCARD_KEY, !on).apply()
                        showMapToast(if (on) "🪪 Keycard odevzdána – Hlubina zase podle příběhu."
                            else "🪪 Keycard Řádu: klec na −2, závora UZÁVĚRA i gondola ke Kořenovému mostu se otevřou.")
                        refreshStoryDecor()
                    }
                    21 -> {
                        StoryFlags.set(this@MakromonMapActivity, cz.uhk.macroflow.pokemon.story.DrakirraFinal.DEFEATED_KEY)
                        showMapToast("🐉 Debug: Drakirra na Prahu poražena – větev ke Kmeni je volná.")
                        refreshStoryDecor()
                    }
                }
            }
            .show()
    }

    /** Podsložka debug menu: všechny cutscény v pořadí příběhu. */
    private fun showDebugCutscenes() {
        val root = findViewById<ViewGroup>(android.R.id.content)
        fun play(v: cz.uhk.macroflow.pokemon.cutscene.CutsceneView) = cz.uhk.macroflow.pokemon.cutscene.CutsceneView.play(root, v)
        val list: List<Pair<String, () -> Unit>> = listOf(
            "🎬 Úvod do Makrosvěta" to { IntroFlyoverView.play(root) },
            "🐉 Oči (Drakirra na vrcholu)" to { play(cz.uhk.macroflow.pokemon.cutscene.DrakirraCutsceneView(this)) },
            "🍄 Pád Soulorda (Hvozd)" to { play(cz.uhk.macroflow.pokemon.cutscene.SoulordCutsceneView(this)) },
            "💙 Modré světlo v trní (Hvozd)" to { play(cz.uhk.macroflow.pokemon.cutscene.GroveCutsceneView(this)) },
            "🌀 Brána se otevírá (průsmyk)" to { play(cz.uhk.macroflow.pokemon.cutscene.GateCutsceneView(this)) },
            "🕯 Světlo mezi stromy (Ztichlý sad)" to { play(cz.uhk.macroflow.pokemon.cutscene.FennCutsceneView(this)) },
            "🌑 Bezejmenní (finále sadu)" to { play(cz.uhk.macroflow.pokemon.cutscene.NamelessCutsceneView(this)) },
            "⚖️ Dveře Věže (Ozvěna)" to { play(cz.uhk.macroflow.pokemon.cutscene.TowerDoorCutsceneView(this)) },
            "🌳 Sjezd do Hlubiny (Věž −2)" to { play(cz.uhk.macroflow.pokemon.cutscene.DescentCutsceneView(this)) },
            "🗡 Konec I · Kopí" to { debugEnding(cz.uhk.macroflow.pokemon.story.Finale.End.SPEAR) },
            "🌸 Konec II · Den kvetení" to { debugEnding(cz.uhk.macroflow.pokemon.story.Finale.End.BLOOM) },
            "✨ Konec III · Poslední přání" to { debugEnding(cz.uhk.macroflow.pokemon.story.Finale.End.WISH) },
            "🌙 Konec IV · Probuzení" to { debugEnding(cz.uhk.macroflow.pokemon.story.Finale.End.SECRET) },
            "📚 Archiv okruhů – patro 1 (test)" to { enterBiomeAtNode(BiomeType.TOWER_ARCHIVE, cz.uhk.macroflow.pokemon.cave.TowerArchive.ELEVATOR_NODE, MapTransition.FADE) }
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("🎬 Cutscény a videa")
            .setItems(list.map { it.first }.toTypedArray()) { _, which -> list[which].second() }
            .setNegativeButton("Zpět") { _, _ -> showDebugMenu() }
            .show()
    }

    /** Quest Fenna rovnou ve fázi Noc bez měsíce (všechny stopy a Nelle jako pravda). */
    private fun debugFennFinale() {
        val F = cz.uhk.macroflow.pokemon.story.Fenn
        (listOf(F.STARTED_KEY, F.NELLE_KEY, F.MET_KEY, F.NELLE_TOLD_KEY, F.NELLE_TRUTH_KEY, F.REPORTED_KEY) + F.CLUE_KEYS)
            .forEach { StoryFlags.set(this, it) }
        listOf(F.ENDING_KEY, F.END_A_KEY, F.END_B_KEY, F.END_C_KEY).forEach { StoryFlags.clear(this, it) }
        lifecycleScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                db.questDao().saveQuestProgress(QuestProgressEntity(questId = F.QUEST_ID, currentStageIndex = F.FINALE_STAGE,
                    metadata = "0", stageStartedAt = System.currentTimeMillis()))
            }
            questManager.forget(F.QUEST_ID)
            questManager.loadQuest(F.QUEST_ID)
            refreshStoryDecor()
            showMapToast("🕯 Debug: Fenn čeká na Noc bez měsíce – v noci ho dvakrát oslov (úvod, pak finále)")
        }
    }

    /** Quest Fenna od začátku: příznaky pryč, postup v DB smazaný. */
    private fun debugResetFenn() {
        cz.uhk.macroflow.pokemon.story.Fenn.ALL_KEYS.forEach { StoryFlags.clear(this, it) }
        gamePrefs.edit().remove(cz.uhk.macroflow.pokemon.story.Fenn.APPLE_DAY_PREF).apply()
        lifecycleScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) { db.questDao().deleteById(cz.uhk.macroflow.pokemon.story.Fenn.QUEST_ID) }
            questManager.forget(cz.uhk.macroflow.pokemon.story.Fenn.QUEST_ID)
            refreshStoryDecor()
            showMapToast("🕯 Debug: quest Fenna začíná od začátku (chalupa ve dne)")
        }
    }

    /** Nakopávače do batohu (docs/adr/0086): 5 ks, použití v deníku → Batoh → Nakopávače. */
    private fun debugGiveBoost(boost: cz.uhk.macroflow.pokemon.boost.RareBoost) {
        lifecycleScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                cz.uhk.macroflow.pokemon.skills.SkillStore.add(applicationContext, boost.id, 5)
            }
            showMapToast("⚡ Debug: +5× ${boost.label} v batohu")
        }
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
            if (cz.uhk.macroflow.pokemon.cave.ForestMap.canEnter(done) || coupon()) {
                val entry = cz.uhk.macroflow.pokemon.cave.ForestMap.MAP.exitNode
                walkInto(BiomeType.FOREST, entry, MapTransition.FADE)
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
        // rozloučení s nápovědou k modrým houbám → po zavření dialogu cutscéna (docs/adr/0098)
        if (questManager.getCurrentProgress()?.isCompleted == true && StoryFlags.isSet(this, ForestHeart.ROT_DEFEATED_KEY) &&
            !gamePrefs.getBoolean(cz.uhk.macroflow.pokemon.cutscene.StoryCutscenes.GROVE_SEEN, false))
            gamePrefs.edit().putBoolean(cz.uhk.macroflow.pokemon.cutscene.StoryCutscenes.GROVE_ARMED, true).apply()
        questManager.checkNpcInteraction()
    }

    private var storyCutscenePlaying = false

    /**
     * Čekající příběhová cutscéna prvního regionu (po souboji s legendou, po pádu Soulorda, po Mydrusově nápovědě).
     * @return true, pokud se cutscéna spustila ([done] se zavolá po jejím konci).
     */
    private fun maybePlayStoryCutscene(done: () -> Unit = {}): Boolean {
        if (storyCutscenePlaying || cinematic || supportFragmentManager.backStackEntryCount > 0 || questDialogManager.isVisible()) return false
        val C = cz.uhk.macroflow.pokemon.cutscene.StoryCutscenes
        val view: cz.uhk.macroflow.pokemon.cutscene.CutsceneView = when {
            gamePrefs.getBoolean(C.SOULORD_PENDING, false) -> {
                gamePrefs.edit().remove(C.SOULORD_PENDING).apply()
                cz.uhk.macroflow.pokemon.cutscene.SoulordCutsceneView(this)
            }
            gamePrefs.getBoolean(C.DRAKIRRA_PENDING, false) -> {
                gamePrefs.edit().remove(C.DRAKIRRA_PENDING).apply()
                cz.uhk.macroflow.pokemon.cutscene.DrakirraCutsceneView(this)
            }
            gamePrefs.getBoolean(C.GROVE_ARMED, false) && !gamePrefs.getBoolean(C.GROVE_SEEN, false) -> {
                gamePrefs.edit().remove(C.GROVE_ARMED).putBoolean(C.GROVE_SEEN, true).apply()
                cz.uhk.macroflow.pokemon.cutscene.GroveCutsceneView(this)
            }
            else -> return false
        }
        storyCutscenePlaying = true
        cz.uhk.macroflow.pokemon.cutscene.CutsceneView.play(findViewById(android.R.id.content), view) { storyCutscenePlaying = false; done() }
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VHLED A ROZTRŽENÉ LISTY (docs/adr/0047)
    // ─────────────────────────────────────────────────────────────────────────

    private fun findTornPage(page: Insight.Page) {
        StoryFlags.set(this, page.key)
        refreshStoryDecor()     // třpytky zmizí
        val ctx = applicationContext
        lifecycleScope.launch(Dispatchers.IO) { cz.uhk.macroflow.pokemon.skills.SkillStore.add(ctx, Insight.PAGES_ITEM, 1) }
        collectFx(page.node, "📜 Roztržený list") { showTornPage(page, page.found) }
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
        val open = SecretGrove.canEnter(StoryFlags.isSet(this, ForestHeart.ROT_DEFEATED_KEY)) || coupon()
        showMapToast("🍄 " + SecretGrove.thornText(open))
        if (open) mapWorld.postDelayed({
            if (!isFinishing && currentBiome == BiomeType.FOREST)
                walkInto(BiomeType.HIDDEN_GROVE, SecretGrove.EXIT_NODE, MapTransition.FADE)
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
                            // cutscéna „Brána se otevírá“ (docs/adr/0099), pak tabule
                            cz.uhk.macroflow.pokemon.cutscene.CutsceneView.play(findViewById(android.R.id.content), cz.uhk.macroflow.pokemon.cutscene.GateCutsceneView(this@MakromonMapActivity)) {
                                transitionRunning = false
                                if (isFinishing) return@play
                                showMapToast("🍃 Srdce Hvozdu zapadlo do lůžka a runy se rozzářily zeleně. Brána světů je otevřená!")
                                mapWorld.postDelayed({ if (!isFinishing) showWorldGateBoard() }, 900)
                            }
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
            val open = StoryFlags.isSet(this, SkyPass.HEART_PLACED_KEY) || coupon()
            body.addView(ui.button(if (open) "Projít branou" else "Rozumím") {
                close()
                if (open) walkInto(BiomeType.ASH_PLAIN, cz.uhk.macroflow.pokemon.cave.AshPlain.ENTRY_NODE, MapTransition.FADE) else afterGateBoard()
            }.apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = ui.px(8f) }
            })
        }
    }

    /**
     * Po přečtení tabule u brány: čeká-li Hunfrey na runy (2. fáze, docs/adr/0085), fáze se splní hned
     * a Hunfrey runy vyloží – dřív se to stalo až při dalším oslovení a hráč myslel, že se nic nestalo.
     */
    private fun afterGateBoard() {
        if (currentBiome != BiomeType.SKY_PASS) return
        val H = cz.uhk.macroflow.pokemon.story.Hunfrey
        lifecycleScope.launch {
            val p = kotlinx.coroutines.withContext(Dispatchers.IO) { db.questDao().getQuestById(H.QUEST_ID) }
            if (p == null || p.isCompleted || p.currentStageIndex != H.RUNES_STAGE) return@launch
            if (questManager.getActiveQuestId() == H.QUEST_ID) questManager.recheck()   // splní fázi a Hunfrey promluví
            else {
                questManager.loadQuest(H.QUEST_ID)                                      // splní fázi potichu…
                mapWorld.postDelayed({ if (!isFinishing) questManager.checkNpcInteraction() }, 300)  // …a Hunfrey promluví
            }
        }
    }

    private fun onShrine() {
        lifecycleScope.launch {
            val p = loadLegend()
            if (coupon() && p.shrine !is LegendProgress.Shrine.GateOpen) {
                showMapToast("🎟️ Zlatý kupón: brána za svatyní se otevře i bez krystalů.")
                walkInto(BiomeType.SKY_PASS, SkyPass.MAP.exitNode, MapTransition.FADE); return@launch
            }
            when (val st = p.shrine) {
                is LegendProgress.Shrine.NeedCrystals -> showMapToast(
                    "⛩ Svatyně má dvě prázdná lůžka – modré a červené.\n" +
                        "Chybí: " + st.missing.joinToString(", ") { it.label } + ".\n" +
                        "Krystaly hlídají strážci v jeskyni a ve Starém dole.")
                LegendProgress.Shrine.ReadyToPlace -> placeCrystalsCeremony()
                LegendProgress.Shrine.LegendAwaits -> {
                    if (payStamina(cz.uhk.macroflow.pokemon.stamina.Stamina.Action.GUARDIAN)) {
                        showMapToast("Krystaly v lůžkách září… Drak se znovu probouzí!")
                        startSpecialBattle(SpecialBattle.LEGEND_PEAK, BiomeType.MOUNTAINS)
                    }
                }
                LegendProgress.Shrine.GateOpen -> {
                    if (!StoryFlags.isSet(this@MakromonMapActivity, SkyPass.VISITED_KEY))
                        showMapToast("Brána za svatyní zůstala otevřená. Za ní stoupá úzká stezka do mraků…")
                    walkInto(BiomeType.SKY_PASS, SkyPass.MAP.exitNode, MapTransition.FADE)
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
            val store = cz.uhk.macroflow.pokemon.stamina.StaminaStore
            val zone = cz.uhk.macroflow.pokemon.zone.ZoneOne
            val used = store.teleportsToday(this@MakromonMapActivity)
            val cost = zone.teleportCost(currentBiome.name, biome, used)
            val block = zone.teleportBlock(biome, currentBiome.name, zoneSeen,
                missing, forestDone, cz.uhk.macroflow.pokemon.cave.ForestMap.REQUIRED_TASKS,
                usedToday = used, energy = store.load(this@MakromonMapActivity).total)
            val root = findViewById<FrameLayout>(R.id.mapRootContainer)
            when (block) {
                cz.uhk.macroflow.pokemon.zone.ZoneOne.Block.Here -> showMapToast("📍 $name – tady právě stojíš.")
                cz.uhk.macroflow.pokemon.zone.ZoneOne.Block.Unknown -> showMapToast("🌫️ Tohle místo jsi ještě neobjevil. Dojdi tam nejdřív po svých.")
                cz.uhk.macroflow.pokemon.zone.ZoneOne.Block.Secret -> showMapToast("🌫️ Tahle cesta se na mapu zakreslit nedá.")
                is cz.uhk.macroflow.pokemon.zone.ZoneOne.Block.Steps ->
                    showMapToast("⛰️ $name leží za horami. Dnes ti na cestu chybí ještě ${block.missing} kroků.")
                is cz.uhk.macroflow.pokemon.zone.ZoneOne.Block.Forest ->
                    showMapToast("🌲 Hvozd tě pustí dál, až splníš ${block.need} úkolů (máš ${block.done}).")
                is cz.uhk.macroflow.pokemon.zone.ZoneOne.Block.Energy ->
                    showMapToast("⚡ Dnešních ${zone.FREE_TELEPORTS} teleportů zdarma je pryč. Cesta sem stojí ${block.need} energie, máš ${block.have}.")
                null -> cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.show(root, "Teleport", "${zone.title(zone.zoneOf(biome))} · $name") { ui, body, close ->
                    body.addView(ui.text("Vyskočíš vysoko nad Makrosvět a dopadneš rovnou na místo.", 16f, ui.inkSoft))
                    body.addView(ui.spacer(8f))
                    val free = zone.FREE_TELEPORTS - used
                    body.addView(ui.text(
                        if (cost == 0) "Zdarma · dnes ještě ${free} z ${zone.FREE_TELEPORTS} teleportů zdarma"
                        else "Stojí $cost ⚡ (jako cesta pěšky, ${cost / zone.HOP_COST}× přechod)", 15f, ui.inkSoft))
                    body.addView(ui.spacer(12f))
                    body.addView(ui.button(if (cost == 0) "Přenést se: $name" else "Přenést se za $cost ⚡") {
                        if (!payStamina(cost, "teleport")) return@button
                        store.recordTeleport(this@MakromonMapActivity)
                        close(); teleportTo(target)
                    })
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

    /** Tým nad parťákem vpravo dole: čtverce se sprity, klepnutí = nový parťák (docs/adr/0080). */
    private fun refreshTeamColumn() {
        val col = findViewById<android.widget.LinearLayout>(R.id.teamColumn) ?: return
        val ctx = applicationContext
        lifecycleScope.launch {
            val mons = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val dao = db.capturedMakromonDao()
                val active = cz.uhk.macroflow.pokemon.skills.SkillStore.activeId(ctx)
                cz.uhk.macroflow.pokemon.skills.SkillStore.team(ctx).filter { it != active }.mapNotNull { dao.getMakromonById(it) }
            }
            if (isFinishing) return@launch
            col.removeAllViews()
            val dp = resources.displayMetrics.density
            mons.forEach { m ->
                val res = cz.uhk.macroflow.pokemon.species.SpeciesRegistry.byId(m.makromonId)
                    ?.let { resources.getIdentifier(it.sprite, "drawable", packageName) } ?: 0
                col.addView(android.widget.ImageView(this@MakromonMapActivity).apply {
                    background = cz.uhk.macroflow.pokemon.skills.ui.BevelDrawable.slot(1.5f * dp)
                    val p = (5 * dp).toInt(); setPadding(p, p, p, p)
                    if (res != 0) {
                        // shiny Makromon i v týmu přebarvený jako jinde
                        val plain = (resources.getDrawable(res, null) as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (m.isShiny && plain != null) setImageBitmap(cz.uhk.macroflow.pokemon.shiny.ShinySprites.recolor(plain, m.makromonId))
                        else setImageResource(res)
                    }
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    contentDescription = "${m.name}, level ${m.level}"
                    setOnClickListener {
                        lifecycleScope.launch {
                            kotlinx.coroutines.withContext(Dispatchers.IO) { cz.uhk.macroflow.pokemon.bag.PocketActions.makeActive(ctx, m) }
                            companionManager.refresh(); refreshTeamColumn()
                        }
                    }
                }, android.widget.LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply { bottomMargin = (4 * dp).toInt() })
            }
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