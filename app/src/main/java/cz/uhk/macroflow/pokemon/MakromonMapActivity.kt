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
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.pokemon.ui.StepProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class MakromonMapActivity : AppCompatActivity() {

    private lateinit var mapBackground:    ImageView
    private lateinit var movementEngine:   MovementEngine
    private lateinit var questDialogManager: QuestDialogManager
    private lateinit var companionManager: CompanionManager
    private lateinit var stepProgressBar:  StepProgressBar
    public lateinit var questManager:     QuestManager
    private lateinit var gudwinNPC:        ImageView
    private lateinit var starterBush:      ImageView
    private lateinit var meadowBushNPC:    ImageView // <--- NOVÉ

    private var lastClickTime = 0L
    private var lastClickedNode = ""
    private var currentBiome = BiomeType.TOWN
    private var currentDailySteps = 0
    private val STEP_GOAL_FOR_MOUNTAINS = 5000

    private val clickableNodes = listOf(
        "les", "domov", "pokedex", "obchod", "hory",
        "vstup_z_town", "krovi1", "krovi2", "voda", "gudwin", "starter_bush",
        "meadow_npc"
    )

    companion object {
        private const val DOUBLE_CLICK_TIME = 300L
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
        questManager.loadQuest("town_intro_oliver")

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
            replaceMapContent(QuestJournalFragment())
        }

        companionManager.refresh()
        startStepSyncLoop()
    }

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
            elevation = 5f // Zvýšeno na úroveň Gudwina
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

    private fun startStepSyncLoop() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            while (true) {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val steps = withContext(Dispatchers.IO) {
                    db.stepsDao().getStepsForDateSync(todayStr)?.count ?: 0
                }
                currentDailySteps = steps

                // Spustíme na hlavním vlákně
                runOnUiThread {
                    stepProgressBar.setProgress(currentDailySteps, STEP_GOAL_FOR_MOUNTAINS)

                    // OPRAVA: Voláme novou metodu onStepsChanged
                    // Tímto propojíme senzory s Quest systémem
                    questManager.onStepsChanged(currentDailySteps)
                }

                delay(2000)
            }
        }
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

        // Definice toho, co se stane, když Ash DOSTOUPÍ na políčko
        val action = {
            // 1. Oznámíme manažerovi, že jsme na místě (důležité pro VISIT_NODE i CAPTURE_SPECIFIC)
            questManager.onNodeVisited(nodeName)

            when (nodeName) {
                "gudwin" -> questManager.checkNpcInteraction()

                "meadow_npc" -> questManager.checkNpcInteraction()

                "starter_bush" -> {
                    // TADY JE ZMĚNA: Nastavíme příznak, že jde o starter bush souboj
                    getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit()
                        .putString("LAST_BIOME", currentBiome.name)
                        .putString("FORCE_ENCOUNTER_ID", "starter_bush") // Přidáno pro BattleFragment
                        .apply()
                    replaceMapContent(PokemonBattleFragment())
                }

                "hory" -> if (currentDailySteps >= STEP_GOAL_FOR_MOUNTAINS) changeBiome(BiomeType.MOUNTAINS, PointF(0.450f, 0.350f)) else showStepWarningToast()


                "les" -> {
                    // Kontrola, zda je hotový úvodní quest pro Olivera
                    if (!questManager.isIntroQuestFinished()) {
                        // Zastavíme pohyb, aby Ash nedošel až ke vstupu
                        movementEngine.cancel()

                        // Zobrazíme blokující dialog
                        questDialogManager.showQuestDialog(
                            speakerResource = R.drawable.gudwin_oliver,
                            speakerName = "Gudwin Oliver",
                            stageName = "Cesta uzavřena",
                            text = "Zadrž! Ještě jsi nesplnil vše, co jsem tě učil. Dokonči mé úkoly v Town, než se vydáš do nebezpečného Meadow!",
                            totalSteps = 1,
                            currentStepIndex = 0
                        )
                    } else {
                        // Pokud je splněno, normálně tě pustíme dál
                        changeBiome(BiomeType.MEADOW, PointF(0.340f, 0.640f))
                    }
                }

                "domov" -> replaceMapContent(InventoryFragment())
                "pokedex" -> replaceMapContent(MakrodexFragment())
                "obchod" -> replaceMapContent(PokemonShopFragment())
                "vstup_z_town" -> changeBiome(BiomeType.TOWN, PointF(0.46f, 0.15f))
                "krovi1", "krovi2", "voda" -> {
                    if ((1..100).random() <= 90) {
                        val encounterBiome = if (nodeName == "voda") BiomeType.WATER else currentBiome
                        getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit()
                            .putString("LAST_BIOME", encounterBiome.name)
                            .remove("FORCE_ENCOUNTER_ID") // Jistota, že u wild není force
                            .apply()
                        replaceMapContent(PokemonBattleFragment())
                    }
                }
            }
        }

        // Pohyb k uzlu
        if (isDoubleClick) {
            movementEngine.cancel()
            movementEngine.currentSpeed = MovementEngine.FAST_SPEED
            movementEngine.walkToNode(nodeName)
            // I u double clicku počkáme na dojítí, nebo akci spustíme hned,
            // ale sjednoceně přes action()
            action()
        } else {
            movementEngine.currentSpeed = MovementEngine.NORMAL_SPEED
            movementEngine.walkToNode(nodeName) {
                // Tohle se spustí, až postavička dojde k cíli
                runOnUiThread { action() }
            }
        }
    }

    private fun showStepWarningToast() {
        val layout = layoutInflater.inflate(R.layout.layout_custom_toast, null)
        val text: TextView = layout.findViewById(R.id.toastText)
        text.text = "Tohle by jsi na jeden zátah neušel, zkus se trochu víc ještě projít!"
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
            stageName = "Zákaz vstupu",
            text = "Zadrž, hrdino! Meadow je nebezpečné místo pro někoho, kdo ještě neprokázal své základní dovednosti. Dokonči prosím mé úkoly v Town, než se vydáš dál!",
            totalSteps = 1,
            currentStepIndex = 0
        )
    }

    private fun changeBiome(newBiome: BiomeType, startPos: PointF, animate: Boolean = true) {
                val container = findViewById<ViewGroup>(R.id.mapMainContent)
                val transitionAction = {
                    currentBiome = newBiome
                    stepProgressBar.visibility = if (newBiome == BiomeType.MEADOW) View.VISIBLE else View.GONE

                    // --- SPRÁVA NPC PODLE BIOMU ---
                    if (newBiome == BiomeType.TOWN) {
                        gudwinNPC.visibility = View.VISIBLE
                        starterBush.visibility = View.VISIBLE
                        meadowBushNPC.visibility = View.GONE

                        // OPRAVA: Musíme Gudwina a keř znovu umístit na správné souřadnice v kontejneru
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
                    } else if (newBiome == BiomeType.MEADOW) {
                gudwinNPC.visibility = View.GONE
                starterBush.visibility = View.GONE
                meadowBushNPC.visibility = View.VISIBLE // Zobrazit v Meadow

                // Pozicování NPC z křoví v Meadow (např. na pozici uzlu 'krovi1')
                val bushNpcPos = BiomeRegistry.MEADOW_GRAPH.find { it.id == "npc_bush" }?.pos ?: PointF(0.630f, 0.350f)
                meadowBushNPC.post {
                    meadowBushNPC.x = bushNpcPos.x * container.width - (meadowBushNPC.width / 2)
                    meadowBushNPC.y = bushNpcPos.y * container.height - (meadowBushNPC.height / 0.8f) // Trochu výš, aby to vypadalo přirozeně
                }
            } else {
                gudwinNPC.visibility = View.GONE
                starterBush.visibility = View.GONE
                meadowBushNPC.visibility = View.GONE
            }

            when (newBiome) {
                BiomeType.TOWN -> {
                    mapBackground.setImageResource(R.drawable.poketown)
                    movementEngine.updateBiome(BiomeRegistry.TOWN_GRAPH, startPos)
                }
                BiomeType.MEADOW -> {
                    mapBackground.setImageResource(R.drawable.meadow)
                    movementEngine.updateBiome(BiomeRegistry.MEADOW_GRAPH, startPos)
                }
                BiomeType.MOUNTAINS -> {
                    mapBackground.setImageResource(R.drawable.poketown)
                    movementEngine.updateBiome(BiomeRegistry.TOWN_GRAPH, startPos)
                }
                else -> {}
            }
            drawDebugNodes(container)
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

    private fun replaceMapContent(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.mapFragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }
}