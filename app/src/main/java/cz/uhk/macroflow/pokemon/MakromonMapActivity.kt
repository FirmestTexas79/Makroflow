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
import cz.uhk.macroflow.pokemon.ui.StepProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sqrt

class MakromonMapActivity : AppCompatActivity() {

    private lateinit var mapBackground:    ImageView
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
        "vstup_z_meadow", "kral_mlsak", "camp", "mine", "cave", "peak", "skaly1", "skaly2"
    )

    /** Uzly, kde může vyskočit divoký Makromon (90 % šance). */
    private val encounterNodes = setOf("krovi1", "krovi2", "voda", "skaly1", "skaly2", "cave", "peak")

    companion object {
        private const val DOUBLE_CLICK_TIME = 300L
        private const val TAG_JOURNAL = "QUEST_JOURNAL"
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pokemon_map)

        mapBackground = findViewById(R.id.mapBackground)
        stepProgressBar = findViewById(R.id.stepProgressBar)

        val ashView = findViewById<ImageView>(R.id.ashView).also {
            it.layoutParams.width  = (28 * resources.displayMetrics.density).toInt()
            it.layoutParams.height = (42 * resources.displayMetrics.density).toInt()
            it.requestLayout()
        }

        val imageLoader = ImageLoader.Builder(this).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }.build()

        movementEngine = MovementEngine(this, ashView, mapBackground)

        questDialogManager = QuestDialogManager(
            this,
            findViewById(R.id.tutorialOverlay),
            findViewById(R.id.tutorialText),
            findViewById(R.id.tutorialTeacher),
            imageLoader,
            findViewById(R.id.questProgressLine)
        )

        questManager = QuestManager(AppDatabase.getDatabase(this), questDialogManager, lifecycleScope)

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
            if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack() else finish()
        }

        mapBackground.post {
            changeBiome(BiomeType.TOWN, PointF(0.480f, 0.275f), animate = false)
            intent.getStringExtra("TARGET_LOCATION")?.let { triggerHotspotAction(it.lowercase()) }
        }

        findViewById<ImageButton>(R.id.btnOpenJournal).setOnClickListener {
            replaceMapContent(QuestJournalFragment(), TAG_JOURNAL)
        }

        companionManager.refresh()
        observeGameData()
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
        findViewById<ViewGroup>(R.id.mapMainContent).addView(gudwinNPC)
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
        findViewById<ViewGroup>(R.id.mapMainContent).addView(starterBush)
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
        findViewById<ViewGroup>(R.id.mapMainContent).addView(meadowBushNPC)
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
        if (event.action == MotionEvent.ACTION_UP) {
            val mapContent = findViewById<View>(R.id.mapMainContent)
            val location = IntArray(2)
            mapContent.getLocationOnScreen(location)

            val relX = (event.rawX - location[0]) / mapContent.width
            val relY = (event.rawY - location[1]) / mapContent.height

            val clickedWaypoint = movementEngine.navigationGraph.find { waypoint ->
                clickableNodes.contains(waypoint.id) &&
                        sqrt(Math.pow((waypoint.pos.x - relX).toDouble(), 2.0) +
                                Math.pow((waypoint.pos.y - relY).toDouble(), 2.0)) < 0.1
            }

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
                "mine" -> scanInMine()
                "camp" -> {} // zatím jen cíl průzkumu v questu krále
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
                        val encounterBiome = if (nodeName == "voda") BiomeType.WATER else currentBiome
                        getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit()
                            .putString("LAST_BIOME", encounterBiome.name)
                            .remove("FORCE_ENCOUNTER_ID")
                            .apply()
                        replaceMapContent(PokemonBattleFragment())
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

    /** Vstup do biomu se zámkem (dnes nachozené kroky). Kontroluje se jen při vstupu. */
    private fun tryEnterBiome(target: BiomeType, entryNode: String) {
        if (BiomeAccess.canEnter(target, currentDailySteps)) {
            enterBiomeAtNode(target, entryNode)
        } else {
            showStepWarningToast(BiomeAccess.missingSteps(target, currentDailySteps))
        }
    }

    private fun enterBiomeAtNode(target: BiomeType, nodeId: String) {
        val graph = BiomeRegistry.definition(target)?.graph ?: return
        val pos = BiomeRegistry.nodePos(graph, nodeId) ?: return
        changeBiome(target, PointF(pos.x, pos.y))
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

    private fun changeBiome(newBiome: BiomeType, startPos: PointF, animate: Boolean = true) {
        val container = findViewById<ViewGroup>(R.id.mapMainContent)
        val transitionAction = {
            currentBiome = newBiome
            refreshStepBar()

            BiomeRegistry.definition(newBiome)?.questId?.let { questManager.loadQuest(it) }

            when (newBiome) {
                BiomeType.TOWN -> {
                    mapBackground.setImageResource(R.drawable.poketown)
                    movementEngine.updateBiome(BiomeRegistry.TOWN_GRAPH, startPos)
                    gudwinNPC.visibility = View.VISIBLE
                    starterBush.visibility = View.VISIBLE
                    meadowBushNPC.visibility = View.GONE

                    val gudwinPos = BiomeRegistry.TOWN_GRAPH.find { it.id == "gudwin" }?.pos ?: PointF(0.120f, 0.520f)
                    val bushPos = BiomeRegistry.TOWN_GRAPH.find { it.id == "starter_bush" }?.pos ?: PointF(0.200f, 0.170f)

                    gudwinNPC.post {
                        gudwinNPC.x = gudwinPos.x * container.width - (gudwinNPC.width / 2)
                        gudwinNPC.y = gudwinPos.y * container.height - gudwinNPC.height
                    }
                    starterBush.post {
                        starterBush.x = bushPos.x * container.width - (starterBush.width / 2)
                        starterBush.y = bushPos.y * container.height - starterBush.height
                    }
                }
                BiomeType.MEADOW -> {
                    mapBackground.setImageResource(R.drawable.meadow)
                    movementEngine.updateBiome(BiomeRegistry.MEADOW_GRAPH, startPos)
                    gudwinNPC.visibility = View.GONE
                    starterBush.visibility = View.GONE
                    meadowBushNPC.visibility = View.VISIBLE

                    val bushNpcPos = BiomeRegistry.MEADOW_GRAPH.find { it.id == "meadow_npc" }?.pos ?: PointF(0.630f, 0.430f)
                    meadowBushNPC.post {
                        meadowBushNPC.x = bushNpcPos.x * container.width - (meadowBushNPC.width / 2)
                        meadowBushNPC.y = bushNpcPos.y * container.height - (meadowBushNPC.height / 0.8f)
                    }
                }
                BiomeType.MOUNTAINS -> {
                    mapBackground.setImageResource(R.drawable.mountains)
                    movementEngine.updateBiome(BiomeRegistry.MOUNTAINS_GRAPH, startPos)
                    gudwinNPC.visibility = View.GONE
                    starterBush.visibility = View.GONE
                    meadowBushNPC.visibility = View.GONE
                }
                else -> {}
            }
            // Ladicí body grafu jen v debug buildu – dřív byly vidět i v produkční verzi.
            if (BuildConfig.DEBUG) drawDebugNodes(container)
        }

        if (animate) {
            container.animate().alpha(0f).setDuration(400).withEndAction {
                transitionAction()
                container.animate().alpha(1f).setDuration(400).start()
            }.start()
        } else {
            transitionAction()
        }
    }

    private fun drawDebugNodes(container: ViewGroup) {
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