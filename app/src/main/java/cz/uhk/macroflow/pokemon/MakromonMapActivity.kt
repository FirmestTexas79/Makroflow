package cz.uhk.macroflow.pokemon

import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import cz.uhk.macroflow.R

class MakromonMapActivity : AppCompatActivity() {

    private lateinit var mapBackground:    ImageView
    private lateinit var movementEngine:   MovementEngine
    private lateinit var tutorialManager:  TutorialManager
    private lateinit var companionManager: CompanionManager

    private lateinit var townHotspots:     View
    private lateinit var meadowHotspots:   View

    private var lastClickTime = 0L
    private var lastClickedId = -1
    private var currentBiome = BiomeType.TOWN

    private val hotspotIds = listOf(
        R.id.hotspotForest, R.id.hotspotHome, R.id.hotspotPokedex, R.id.hotspotShop,
        R.id.hotspotMeadowToTown, R.id.hotspotBush1, R.id.hotspotBush2, R.id.hotspotBush3
    )

    companion object {
        private const val DOUBLE_CLICK_TIME = 300L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pokemon_map)

        mapBackground  = findViewById(R.id.mapBackground)
        townHotspots   = findViewById(R.id.townHotspots)
        meadowHotspots = findViewById(R.id.meadowHotspots)

        val ashView = findViewById<ImageView>(R.id.ashView).also {
            it.layoutParams.width  = (28 * resources.displayMetrics.density).toInt()
            it.layoutParams.height = (42 * resources.displayMetrics.density).toInt()
            it.requestLayout()
        }

        val imageLoader = ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()

        movementEngine = MovementEngine(this, ashView, mapBackground)
        tutorialManager = TutorialManager(this, findViewById(R.id.tutorialOverlay), findViewById(R.id.tutorialText), findViewById(R.id.tutorialTeacher), imageLoader)
        companionManager = CompanionManager(this, findViewById(R.id.ivCompanion), findViewById(R.id.tvCompanionLabel), findViewById(R.id.companionShadow), lifecycleScope)

        findViewById<ImageButton>(R.id.btnStartTutorial).setOnClickListener { tutorialManager.start() }
        findViewById<View>(R.id.btnExitMap).setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this) {
            if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack()
            else finish()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            val fragmentOpen = supportFragmentManager.backStackEntryCount > 0
            setHotspotsEnabled(!fragmentOpen)
            if (!fragmentOpen) companionManager.refresh()
        }

        mapBackground.post {
            // Výchozí stav: TOWN
            changeBiome(BiomeType.TOWN, PointF(0.480f, 0.275f), animate = false)

            when (intent.getStringExtra("TARGET_LOCATION")) {
                "POKEDEX"   -> findViewById<View>(R.id.hotspotPokedex).performClick()
                "INVENTORY" -> findViewById<View>(R.id.hotspotHome).performClick()
                "SHOP"      -> findViewById<View>(R.id.hotspotShop).performClick()
            }
        }
        companionManager.refresh()
    }

    /**
     * Hlavní metoda pro změnu lokace.
     * Implementuje vizuální přechod a aktualizaci navigačního enginu.
     */
    private fun changeBiome(newBiome: BiomeType, startPos: PointF, animate: Boolean = true) {
        val transitionAction = {
            currentBiome = newBiome

            when (newBiome) {
                BiomeType.TOWN -> {
                    // Opraveno na poketown (odpovídá poketown.webp)
                    mapBackground.setImageResource(R.drawable.poketown)
                    townHotspots.visibility = View.VISIBLE
                    meadowHotspots.visibility = View.GONE
                    movementEngine.updateBiome(BiomeRegistry.TOWN_GRAPH, startPos)
                    setupTownHotspots()
                }
                BiomeType.MEADOW -> {
                    // Opraveno na meadow (odpovídá meadow.png)
                    mapBackground.setImageResource(R.drawable.meadow)
                    townHotspots.visibility = View.GONE
                    meadowHotspots.visibility = View.VISIBLE
                    movementEngine.updateBiome(BiomeRegistry.MEADOW_GRAPH, startPos)
                    setupMeadowHotspots()
                }
                else -> {}
            }
        }

        if (animate) {
            // Animujeme celý RelativeLayout, aby zmizela mapa i hotspoty najednou
            findViewById<View>(R.id.mapMainContent).animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction {
                    transitionAction()
                    findViewById<View>(R.id.mapMainContent).animate().alpha(1f).setDuration(400).start()
                }.start()
        } else {
            transitionAction()
        }
    }

    private fun setupTownHotspots() {
        findViewById<View>(R.id.hotspotForest).setOnClickListener {
            handleHotspotClick(R.id.hotspotForest, "les") {
                changeBiome(BiomeType.MEADOW, PointF(0.500f, 0.850f))
            }
        }
        findViewById<View>(R.id.hotspotHome).setOnClickListener {
            handleHotspotClick(R.id.hotspotHome, "domov") { replaceMapContent(InventoryFragment()) }
        }
        findViewById<View>(R.id.hotspotPokedex).setOnClickListener {
            handleHotspotClick(R.id.hotspotPokedex, "pokedex") { replaceMapContent(MakrodexFragment()) }
        }
        findViewById<View>(R.id.hotspotShop).setOnClickListener {
            handleHotspotClick(R.id.hotspotShop, "obchod") { replaceMapContent(PokemonShopFragment()) }
        }
    }

    private fun setupMeadowHotspots() {
        findViewById<View>(R.id.hotspotMeadowToTown).setOnClickListener {
            handleHotspotClick(R.id.hotspotMeadowToTown, "vstup_z_town") {
                changeBiome(BiomeType.TOWN, PointF(0.460f, 0.150f))
            }
        }

        // Encounter logiky pro křoví
        val bushIds = listOf(R.id.hotspotBush1 to "krovi1", R.id.hotspotBush2 to "krovi2", R.id.hotspotBush3 to "krovi3")
        bushIds.forEach { (viewId, nodeName) ->
            findViewById<View>(viewId).setOnClickListener {
                handleHotspotClick(viewId, nodeName) {
                    // 75% šance na spawn Makromona
                    if ((1..100).random() <= 75) {
                        replaceMapContent(PokemonBattleFragment())
                    }
                }
            }
        }
    }

    private fun handleHotspotClick(id: Int, targetNode: String, onFinished: () -> Unit) {
        if (tutorialManager.isVisible()) return
        val now = System.currentTimeMillis()
        if (id == lastClickedId && now - lastClickTime < DOUBLE_CLICK_TIME) {
            movementEngine.cancel()
            movementEngine.currentSpeed = MovementEngine.FAST_SPEED
            onFinished()
            movementEngine.walkToNode(targetNode)
        } else {
            movementEngine.currentSpeed = MovementEngine.NORMAL_SPEED
            movementEngine.walkToNode(targetNode, onFinished)
        }
        lastClickTime = now
        lastClickedId = id
    }

    private fun setHotspotsEnabled(enabled: Boolean) {
        hotspotIds.forEach { id -> findViewById<View>(id)?.apply { isClickable = enabled; isFocusable = enabled } }
    }

    private fun replaceMapContent(fragment: Fragment) {
        setHotspotsEnabled(false)
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.mapFragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }
}