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
        "vyrobna", "zahon_1", "zahon_2", "zahon_3", "zahon_4"
    )

    /** Uzly, kde může vyskočit divoký Makromon (90 % šance). Jeskyně mají vlastní (CaveMap.encounterNodes). */
    private val encounterNodes = setOf("krovi1", "krovi2", "voda", "skaly1", "skaly2") +
        cz.uhk.macroflow.pokemon.cave.CaveMaps.ALL.flatMap { it.encounterNodes } +
        cz.uhk.macroflow.pokemon.cave.ForestMap.MAP.encounterNodes

    /** Přechod mezi mapami. */
    private enum class MapTransition { NONE, FADE, CAVE_IN, CAVE_OUT }

    companion object {
        private const val DOUBLE_CLICK_TIME = 300L
        /** Dosah klepnutí na uzel v podílu obrazovky. */
        private const val TAP_RADIUS = 0.1f
        /** V jeskyních a lese jsou body husté a mezi nimi se chodí volně – menší dosah. */
        private const val CAVE_TAP_RADIUS = 0.06f
        /** Uzly jeskyní a lesa, které něco dělají (ostatní jsou jen cesta). */
        private val CAVE_ACTION_NODES = setOf(
            "vychod_jeskyne", "vychod_dolu", "vstup_z_louky", "tezba", "les_sever", "mytina",
            "krystal_modry", "krystal_cerveny"
        )
        private const val TAG_JOURNAL = "QUEST_JOURNAL"
        private const val DEBUG_BOOTS_KEY = "DEBUG_SEVEN_LEAGUE_BOOTS"
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
            introPrefs = getSharedPreferences("QuestPrefs", Context.MODE_PRIVATE)
        )

        // PROPOJENÍ: Když se v manageru změní progres (např. onMealLogged), refreshneme UI
        questManager.onProgressChanged = { progress ->
            // lifecycleScope zajistí, že nebudeme sahat do UI, pokud aktivita umírá
            lifecycleScope.launch(Dispatchers.Main) {
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
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                companionManager.refresh()
                refreshStoryDecor()     // po souboji se strážcem / legendou
            }
        }

        mapBackground.post {
            // Debug: rovnou do jiné lokace (adb … --es debug_biome MEADOW)
            val debugBiome = if (BuildConfig.DEBUG) intent.getStringExtra("debug_biome")?.let { runCatching { BiomeType.valueOf(it) }.getOrNull() } else null
            if (debugBiome != null) enterBiomeAtNode(debugBiome, BiomeRegistry.definition(debugBiome)?.graph?.first()?.id ?: "", MapTransition.NONE)
            else changeBiome(BiomeType.TOWN, PointF(0.480f, 0.275f), MapTransition.NONE)
            intent.getStringExtra("TARGET_LOCATION")?.let { triggerHotspotAction(it.lowercase()) }
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
    }

    override fun onPause() {
        cz.uhk.macroflow.pokemon.audio.GameAudio.pause()
        super.onPause()
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
        if (gatedBiome == null) {
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
        val now = System.currentTimeMillis()
        val isDoubleClick = (nodeName == lastClickedNode && now - lastClickTime < DOUBLE_CLICK_TIME)
        lastClickTime = now
        lastClickedNode = nodeName

        val action = {
            questManager.onNodeVisited(nodeName)

            when (nodeName) {
                "gudwin", "meadow_npc", "kral_mlsak" -> questManager.checkNpcInteraction()
                "cave", "mine" -> BiomeRegistry.caveBehind(nodeName)?.let { cave ->
                    val entry = BiomeRegistry.definition(cave)?.cave?.exitNode ?: return@let
                    enterBiomeAtNode(cave, entry, MapTransition.CAVE_IN)
                }
                "vychod_jeskyne", "vychod_dolu", "vstup_z_louky" -> BiomeRegistry.definition(currentBiome)?.cave?.let {
                    enterBiomeAtNode(BiomeType.valueOf(it.parentBiome), it.mountainNode,
                        if (it.isCave) MapTransition.CAVE_OUT else MapTransition.FADE)
                }
                "tezba" -> scanInMine()
                cz.uhk.macroflow.pokemon.skills.MeadowLayout.TABLE_NODE -> openCraftingTable()
                "zahon_1", "zahon_2", "zahon_3", "zahon_4" ->
                    cz.uhk.macroflow.pokemon.skills.MeadowLayout.plotIndex(nodeName)?.let { onPlot(it) }
                "les_sever" -> tryEnterForest()
                "mytina" -> showMapToast("🌳 Mýtina v srdci Hvozdu. Na prastarém pařezu rostou rudé houby a v korunách je slyšet šepot…\n(Tady příběh teprve začne.)")
                "krystal_modry", "krystal_cerveny" -> BiomeRegistry.definition(currentBiome)?.cave?.let { onCrystalNode(it) }
                "peak" -> onShrine()
                "camp" -> restAtCamp()
                "rozcesti_hory" -> showMapToast("🪧 ↑ Socha krále Mlsáka · ↖ Důl a horní stezka\n← Tábor · ↓ Zpět na louku")
                "starter_bush" -> {
                    getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit()
                        .putString("LAST_BIOME", currentBiome.name)
                        .putString("FORCE_ENCOUNTER_ID", "starter_bush")
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

    private fun showStepWarningToast(missingSteps: Int) =
        showMapToast("Tohle bys na jeden zátah neušel! Dnes se ještě projdi – chybí ti $missingSteps kroků.")

    private fun showMapToast(message: String) {
        val layout = layoutInflater.inflate(R.layout.layout_custom_toast, null)
        layout.findViewById<TextView>(R.id.toastText).text = message
        with (Toast(applicationContext)) {
            setGravity(Gravity.CENTER, 0, 0)
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
            refreshStepBar()
            // Úvodní tutoriál (otazník) patří zatím jen k městu
            findViewById<View>(R.id.btnStartTutorial).visibility = if (newBiome == BiomeType.TOWN) View.VISIBLE else View.GONE

            val def = BiomeRegistry.definition(newBiome)
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
        }

        when (transition) {
            MapTransition.NONE -> transitionAction()
            MapTransition.FADE -> container.animate().alpha(0f).setDuration(400).withEndAction {
                transitionAction()
                container.animate().alpha(1f).setDuration(400).start()
            }.start()
            MapTransition.CAVE_IN, MapTransition.CAVE_OUT ->
                playCaveTransition(exiting = transition == MapTransition.CAVE_OUT, onCovered = transitionAction)
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

    /** Postava uprostřed obrazovky, ale kamera nevyjede za okraj pozadí. */
    private fun updateCamera() {
        if (BiomeRegistry.definition(currentBiome)?.cave == null) {
            mapWorld.translationX = 0f; mapWorld.translationY = 0f
            return
        }
        val viewport = findViewById<View>(R.id.mapMainContent)
        mapWorld.translationX = MapCamera.offset(ashView.x + ashView.width / 2f, viewport.width, mapWorld.width)
        mapWorld.translationY = MapCamera.offset(ashView.y + ashView.height / 2f, viewport.height, mapWorld.height)
    }

    /** Tmavě modrý mechový přechod: pod plně zakrytou obrazovkou se vymění mapa, pak se překryv rozplyne. */
    private fun playCaveTransition(exiting: Boolean, onCovered: () -> Unit) {
        val root = findViewById<FrameLayout>(R.id.mapRootContainer)
        movementEngine.cancel()
        transitionRunning = true
        val overlay = CaveTransitionView(this, exiting).apply {
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
                cave != null -> { placeCrystal(cave, progress); if (cave.isCave) placeEncounterGlows(cave) }
                currentBiome == BiomeType.MOUNTAINS -> placeShrineDecor(progress)
                currentBiome == BiomeType.MEADOW -> placeMeadowWorkshop()
                else -> {}
            }
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
            val glow = glowView(size, 0xFF8CF0D2.toInt(), 0x66).apply {
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
            cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus.craftMenu(findViewById(R.id.mapRootContainer), owned, state) { ball, times ->
                lifecycleScope.launch {
                    val res = kotlinx.coroutines.withContext(Dispatchers.IO) { SS.craft(ctx, ball, times) }
                    if (res == null) { showMapToast("Chybí suroviny."); return@launch }
                    val (made, xp) = res
                    showMapToast("🔨 Vyrobeno: ${made}× ${ball.label}" + (if (made > times) " (dvojitá výroba!)" else "") +
                        "\n+${xp.gained} XP Výroba" + skillLevelText(xp))
                }
            }
        }
    }

    private fun clearDecor() {
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
            "🎒 Dát oba krystaly do inventáře"
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
                        gamePrefs.edit().apply {
                            CrystalColor.entries.forEach { putBoolean(LegendProgress.bossKey(it), true) }
                        }.apply()
                        showMapToast("⚔ Debug: strážci poraženi – krystaly jdou vzít")
                        refreshStoryDecor()
                    }
                    4 -> lifecycleScope.launch {
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            CrystalColor.entries.forEach { c ->
                                if ((db.userItemDao().getItemCount(c.itemId) ?: 0) == 0) db.userItemDao().addItem(c.itemId, 1)
                            }
                        }
                        gamePrefs.edit().apply {
                            CrystalColor.entries.forEach {
                                putBoolean(LegendProgress.bossKey(it), true); putBoolean(LegendProgress.takenKey(it), true)
                            }
                        }.apply()
                        showMapToast("🎒 Debug: oba krystaly jsou v inventáři")
                        refreshStoryDecor()
                    }
                }
            }
            .show()
    }

    /** Vrátí celý příběh krystalů na začátek: strážci zpět, krystaly na oltářích, svatyně prázdná. */
    private fun debugResetLegend() {
        gamePrefs.edit().apply {
            CrystalColor.entries.forEach { remove(LegendProgress.bossKey(it)); remove(LegendProgress.takenKey(it)) }
            remove(LegendProgress.PLACED_KEY); remove(LegendProgress.LEGEND_KEY)
        }.apply()
        lifecycleScope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
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
        gamePrefs.edit().putBoolean(LegendProgress.takenKey(color), true).apply()
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
                LegendProgress.Shrine.GateOpen -> showMapToast(
                    "Brána za svatyní zůstala otevřená. Z temnoty za ní táhne studený vítr…\n(Cesta dál se teprve chystá.)")
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
        gamePrefs.edit().putBoolean(LegendProgress.PLACED_KEY, true).apply()

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