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
    private var crystalGlow: View? = null
    private val crystalAnimators = mutableListOf<android.animation.Animator>()
    /** Pulzující světlo nad místy setkání v jeskyni (svítící kapradiny). */
    private val encounterGlows = mutableListOf<View>()
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
        "meadow_npc",
        "vstup_z_meadow", "rozcesti_hory", "kral_mlsak", "camp", "mine", "cave", "peak", "skaly1", "skaly2"
    )

    /** Uzly, kde může vyskočit divoký Makromon (90 % šance). Jeskyně mají vlastní (CaveMap.encounterNodes). */
    private val encounterNodes = setOf("krovi1", "krovi2", "voda", "skaly1", "skaly2", "peak") +
        cz.uhk.macroflow.pokemon.cave.CaveMaps.ALL.flatMap { it.encounterNodes }

    /** Přechod mezi mapami. */
    private enum class MapTransition { NONE, FADE, CAVE_IN, CAVE_OUT }

    companion object {
        private const val DOUBLE_CLICK_TIME = 300L
        /** Dosah klepnutí na uzel v podílu obrazovky. */
        private const val TAP_RADIUS = 0.1f
        private const val TAG_JOURNAL = "QUEST_JOURNAL"
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

        onBackPressedDispatcher.addCallback(this) {
            when {
                questDialogManager.isVisible() -> questDialogManager.hide()   // zpět zavře tutoriál/dialog
                supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
                else -> finish()
            }
        }

        // Parťák vlevo nahoře: dřív se načetl jen při otevření mapy – změna v Domově se neprojevila.
        // Obnoví se při změně aktivního Makromona i po návratu z Domova / souboje (level, evoluce).
        gamePrefs.registerOnSharedPreferenceChangeListener(companionPrefsListener)
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) companionManager.refresh()
        }

        mapBackground.post {
            changeBiome(BiomeType.TOWN, PointF(0.480f, 0.275f), MapTransition.NONE)
            intent.getStringExtra("TARGET_LOCATION")?.let { triggerHotspotAction(it.lowercase()) }
        }

        findViewById<ImageButton>(R.id.btnOpenJournal).setOnClickListener {
            replaceMapContent(QuestJournalFragment(), TAG_JOURNAL)
        }
        // Ladění shiny (jen debug build): podržením deníku bude příští setkání shiny
        if (BuildConfig.DEBUG) findViewById<ImageButton>(R.id.btnOpenJournal).setOnLongClickListener {
            getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit().putBoolean("DEBUG_FORCE_SHINY", true).apply()
            showMapToast("✦ Debug: příští setkání bude shiny")
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
    }

    override fun onDestroy() {
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (transitionRunning) return true
        if (event.action == MotionEvent.ACTION_UP) {
            // Souřadnice ve světě mapy (getLocationOnScreen zahrnuje i posun kamery)
            val viewport = findViewById<View>(R.id.mapMainContent)
            val location = IntArray(2)
            mapWorld.getLocationOnScreen(location)
            val relX = (event.rawX - location[0]) / mapWorld.width
            val relY = (event.rawY - location[1]) / mapWorld.height

            val cave = BiomeRegistry.definition(currentBiome)?.cave
            // Nejbližší uzel v dosahu (dřív první nalezený – v hustém bludišti by se trefil vedlejší)
            val clickedWaypoint = movementEngine.navigationGraph
                .filter { cave != null || clickableNodes.contains(it.id) }
                .map { it to MapCamera.tapDistance(it.pos.x, it.pos.y, relX, relY,
                    mapWorld.width, mapWorld.height, viewport.width, viewport.height) }
                .filter { it.second < TAP_RADIUS }
                .minByOrNull { it.second }?.first

            if (clickedWaypoint != null && !questDialogManager.isVisible() && supportFragmentManager.backStackEntryCount == 0) {
                triggerHotspotAction(clickedWaypoint.id)
                return true
            }
        }
        return super.dispatchTouchEvent(event)
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
                "vychod_jeskyne", "vychod_dolu" -> BiomeRegistry.definition(currentBiome)?.cave?.let {
                    enterBiomeAtNode(BiomeType.MOUNTAINS, it.mountainNode, MapTransition.CAVE_OUT)
                }
                "tezba" -> scanInMine()
                "krystal_modry", "krystal_cerveny" -> BiomeRegistry.definition(currentBiome)?.cave?.let { collectCrystal(it) }
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
                        val encounterBiome = if (nodeName == "voda") BiomeType.WATER else currentBiome
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
        "peak" -> "Na vrcholu fouká, ale nikdo tu není. Výhled na celý Makrosvět ale stojí za to."
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
        if (BiomeAccess.canEnter(target, currentDailySteps)) {
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
        val transitionAction = {
            currentBiome = newBiome
            refreshStepBar()
            // Úvodní tutoriál (otazník) patří zatím jen k městu
            findViewById<View>(R.id.btnStartTutorial).visibility = if (newBiome == BiomeType.TOWN) View.VISIBLE else View.GONE

            val def = BiomeRegistry.definition(newBiome)
            def?.questId?.let { questManager.loadQuest(it) }
            gudwinNPC.visibility = if (newBiome == BiomeType.TOWN) View.VISIBLE else View.GONE
            starterBush.visibility = if (newBiome == BiomeType.TOWN) View.VISIBLE else View.GONE
            meadowBushNPC.visibility = if (newBiome == BiomeType.MEADOW) View.VISIBLE else View.GONE
            removeCrystal()

            if (def != null) {
                mapBackground.setImageResource(def.backgroundRes)
                layoutWorld(def.cave)
                movementEngine.updateBiome(def.graph, startPos)
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
                else -> def?.cave?.let { cave -> mapWorld.post { placeCrystal(cave); placeEncounterGlows(cave) } }
            }
            // Ladicí body grafu jen v debug buildu – dřív byly vidět i v produkční verzi.
            if (BuildConfig.DEBUG) mapWorld.post { drawDebugNodes(mapWorld) }
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
        if (cave == null) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            mapBackground.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            val scale = MapCamera.pixelScale(cave.artW, cave.artH, viewport.width, viewport.height, cave.artPixelsAcross)
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
    // KRYSTALY na konci jeskyní (později otevřou legendární souboj)
    // ─────────────────────────────────────────────────────────────────────────

    private fun isCollected(c: CrystalColor) = gamePrefs.getBoolean(c.prefKey, false)

    private fun placeCrystal(cave: CaveMap) {
        removeCrystal()
        if (isCollected(cave.crystal) || mapWorld.width == 0) return
        val scale = mapWorld.width / cave.artW.toFloat()
        val (bx, by) = cave.crystalBase
        val w = (Crystals.W * scale).toInt()
        val h = (Crystals.H * scale).toInt()
        val left = bx * scale - w / 2f
        val top = by * scale - h

        val glowSize = (w * 3.2f).toInt()
        val glow = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(glowSize, glowSize)
            background = android.graphics.drawable.GradientDrawable().apply {
                gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
                gradientRadius = glowSize / 2f
                colors = intArrayOf((cave.crystal.glow and 0x00FFFFFF) or 0x99000000.toInt(), Color.TRANSPARENT)
            }
            x = left + w / 2f - glowSize / 2f
            y = top + h / 2f - glowSize / 2f
            elevation = 1f
        }
        val bmp = android.graphics.Bitmap.createBitmap(Crystals.pixels(cave.crystal), Crystals.W, Crystals.H, android.graphics.Bitmap.Config.ARGB_8888)
        val crystal = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(w, h)
            setImageDrawable(android.graphics.drawable.BitmapDrawable(resources, bmp).apply { isFilterBitmap = false })
            scaleType = ImageView.ScaleType.FIT_XY
            x = left; y = top
            elevation = 1.5f
        }
        mapWorld.addView(glow)
        mapWorld.addView(crystal)
        crystalGlow = glow
        crystalView = crystal

        // Krystal se vznáší (po celých art pixelech) a záře pulzuje
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(crystal, "translationY", 0f, -2 * scale, 0f).apply {
            duration = 2200; repeatCount = android.animation.ValueAnimator.INFINITE
            // skoky po celých art pixelech, ne plynule (pixel art)
            setEvaluator(android.animation.TypeEvaluator<Float> { f, _, _ ->
                -Math.round(2 * kotlin.math.sin(f * Math.PI).toFloat()) * scale
            })
            start()
        }
        crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.45f, 1f, 0.45f).apply {
            duration = 1800; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
    }

    /** Místa setkání v jeskyni: kapradiny jsou v mapě, tady jen pomalu dýchající záře nad nimi. */
    private fun placeEncounterGlows(cave: CaveMap) {
        if (mapWorld.width == 0) return
        val scale = mapWorld.width / cave.artW.toFloat()
        val size = (30 * scale).toInt()
        cave.encounterNodes.forEachIndexed { i, id ->
            val n = cave.node(id) ?: return@forEachIndexed
            val glow = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(size, (size * 0.6f).toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = size / 2f
                    colors = intArrayOf(0x668CF0D2, Color.TRANSPARENT)
                }
                x = n.x * scale - size / 2f
                y = (n.y - 3) * scale - size * 0.3f
                alpha = 0.3f
                elevation = 1f
            }
            mapWorld.addView(glow)
            encounterGlows += glow
            crystalAnimators += android.animation.ObjectAnimator.ofFloat(glow, "alpha", 0.25f, 0.9f, 0.25f).apply {
                duration = 2600; startDelay = i * 450L
                repeatCount = android.animation.ValueAnimator.INFINITE; start()
            }
        }
    }

    private fun removeCrystal() {
        crystalAnimators.forEach { it.cancel() }
        crystalAnimators.clear()
        encounterGlows.forEach { mapWorld.removeView(it) }
        encounterGlows.clear()
        crystalView?.let { mapWorld.removeView(it) }
        crystalGlow?.let { mapWorld.removeView(it) }
        crystalView = null; crystalGlow = null
    }

    private fun collectCrystal(cave: CaveMap) {
        val color = cave.crystal
        if (isCollected(color)) {
            showMapToast("Oltář je prázdný – ${color.label} už máš u sebe.")
            return
        }
        gamePrefs.edit().putBoolean(color.prefKey, true).apply()
        val crystal = crystalView
        val glow = crystalGlow
        // zastavit jen vznášení a pulz krystalu, záře nad kapradinami běží dál
        crystalAnimators.filter { (it as? android.animation.ObjectAnimator)?.target.let { t -> t === crystal || t === glow } }
            .forEach { it.cancel(); crystalAnimators.remove(it) }
        crystal?.animate()?.translationYBy(-crystal.height * 0.6f)?.scaleX(1.5f)?.scaleY(1.5f)?.alpha(0f)
            ?.setDuration(900)?.withEndAction {
                crystal.visibility = View.GONE; glow?.visibility = View.GONE
            }?.start()
        glow?.animate()?.scaleX(3f)?.scaleY(3f)?.alpha(0f)?.setDuration(900)?.start()

        val all = CrystalColor.entries.filter { isCollected(it) }.toSet()
        val tail = if (Crystals.legendaryUnlocked(all))
            "Oba krystaly se rozzářily současně… Někde v Makrosvětě se probudila legenda."
        else "Pulzuje v ruce. Druhý krystal prý leží v jiné jeskyni."
        showMapToast("💎 Získal jsi ${color.label}!\n$tail")
    }

    private fun drawDebugNodes(container: FrameLayout) {
        container.findViewWithTag<View>("debug_layer")?.let { container.removeView(it) }
        val debugLayer = FrameLayout(this).apply {
            tag = "debug_layer"
            layoutParams = FrameLayout.LayoutParams(container.width, container.height)
        }
        movementEngine.navigationGraph.forEach { waypoint ->
            debugLayer.addView(View(this).apply {
                background = ColorDrawable(if (clickableNodes.contains(waypoint.id)) Color.GREEN else Color.RED)
                alpha = 0.4f
                layoutParams = FrameLayout.LayoutParams(40, 40)
                x = waypoint.pos.x * container.width - 20
                y = waypoint.pos.y * container.height - 20
            })
        }
        container.addView(debugLayer)
    }

    private fun replaceMapContent(fragment: Fragment, tag: String? = null) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.mapFragmentContainer, fragment, tag)
            .addToBackStack(null)
            .commit()
    }
}