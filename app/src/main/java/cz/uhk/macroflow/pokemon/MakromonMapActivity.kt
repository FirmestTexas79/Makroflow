package cz.uhk.macroflow.pokemon

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import kotlin.math.sqrt

class MakromonMapActivity : AppCompatActivity() {

    private lateinit var mapBackground:    ImageView
    private lateinit var movementEngine:   MovementEngine
    private lateinit var tutorialManager:  TutorialManager
    private lateinit var companionManager: CompanionManager

    private var lastClickTime = 0L
    private var lastClickedNode = ""
    private var currentBiome = BiomeType.TOWN

    private val clickableNodes = listOf(
        "les", "domov", "pokedex", "obchod",
        "vstup_z_town", "krovi1", "krovi2", "voda"
    )

    companion object {
        private const val DOUBLE_CLICK_TIME = 300L
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pokemon_map)

        mapBackground = findViewById(R.id.mapBackground)
        val ashView = findViewById<ImageView>(R.id.ashView).also {
            it.layoutParams.width  = (28 * resources.displayMetrics.density).toInt()
            it.layoutParams.height = (42 * resources.displayMetrics.density).toInt()
            it.requestLayout()
        }

        val imageLoader = ImageLoader.Builder(this).components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }.build()

        movementEngine = MovementEngine(this, ashView, mapBackground)
        tutorialManager = TutorialManager(this, findViewById(R.id.tutorialOverlay), findViewById(R.id.tutorialText), findViewById(R.id.tutorialTeacher), imageLoader)
        companionManager = CompanionManager(this, findViewById(R.id.ivCompanion), findViewById(R.id.tvCompanionLabel), findViewById(R.id.companionShadow), lifecycleScope)

        findViewById<ImageButton>(R.id.btnStartTutorial).setOnClickListener { tutorialManager.start() }
        findViewById<View>(R.id.btnExitMap).setOnClickListener { finish() }

        onBackPressedDispatcher.addCallback(this) {
            if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack() else finish()
        }

        mapBackground.post {
            changeBiome(BiomeType.TOWN, PointF(0.480f, 0.275f), animate = false)
            intent.getStringExtra("TARGET_LOCATION")?.let { triggerHotspotAction(it.lowercase()) }
        }
        companionManager.refresh()
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

            if (clickedWaypoint != null && !tutorialManager.isVisible() && supportFragmentManager.backStackEntryCount == 0) {
                triggerHotspotAction(clickedWaypoint.id)
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
            when (nodeName) {
                "les" -> changeBiome(BiomeType.MEADOW, PointF(0.340f, 0.640f))
                "domov" -> replaceMapContent(InventoryFragment())
                "pokedex" -> replaceMapContent(MakrodexFragment())
                "obchod" -> replaceMapContent(PokemonShopFragment())
                "vstup_z_town" -> changeBiome(BiomeType.TOWN, PointF(0.46f, 0.15f))
                "krovi1", "krovi2", "voda" -> {
                    if ((1..100).random() <= 75) {
                        // Určení biomu pro souboj
                        val encounterBiome = if (nodeName == "voda") BiomeType.WATER else currentBiome

                        // Uložení biomu pro BattleView
                        getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("LAST_BIOME", encounterBiome.name)
                            .apply()

                        replaceMapContent(PokemonBattleFragment())
                    }
                }
            }
        }

        if (isDoubleClick) {
            movementEngine.cancel()
            action()
            movementEngine.currentSpeed = MovementEngine.FAST_SPEED
            movementEngine.walkToNode(nodeName)
        } else {
            val targetNode = movementEngine.navigationGraph.find { it.id == nodeName }
            val currentPos = movementEngine.getCurrentPosition()
            val dist = if (targetNode != null) {
                sqrt(Math.pow((targetNode.pos.x - currentPos.x).toDouble(), 2.0) +
                        Math.pow((targetNode.pos.y - currentPos.y).toDouble(), 2.0))
            } else 1.0

            if (dist < 0.08) {
                action()
            } else {
                movementEngine.currentSpeed = MovementEngine.NORMAL_SPEED
                movementEngine.walkToNode(nodeName) { action() }
            }
        }
    }

    private fun changeBiome(newBiome: BiomeType, startPos: PointF, animate: Boolean = true) {
        val container = findViewById<ViewGroup>(R.id.mapMainContent)
        val transitionAction = {
            currentBiome = newBiome
            when (newBiome) {
                BiomeType.TOWN -> {
                    mapBackground.setImageResource(R.drawable.poketown)
                    movementEngine.updateBiome(BiomeRegistry.TOWN_GRAPH, startPos)
                }
                BiomeType.MEADOW -> {
                    mapBackground.setImageResource(R.drawable.meadow)
                    movementEngine.updateBiome(BiomeRegistry.MEADOW_GRAPH, startPos)
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