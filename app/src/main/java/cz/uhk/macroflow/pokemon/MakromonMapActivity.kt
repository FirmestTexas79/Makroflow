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

    private var lastClickTime = 0L
    private var lastClickedId = -1

    private val hotspotIds = listOf(
        R.id.hotspotForest,
        R.id.hotspotHome,
        R.id.hotspotPokedex,
        R.id.hotspotShop
    )

    companion object {
        private const val DOUBLE_CLICK_TIME = 300L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pokemon_map)

        mapBackground = findViewById(R.id.mapBackground)

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
            movementEngine.resetToPosition(PointF(0.480f, 0.275f)) // Reset na Spawn
            setupHotspots()

            when (intent.getStringExtra("TARGET_LOCATION")) {
                "POKEDEX"   -> findViewById<View>(R.id.hotspotPokedex).performClick()
                "INVENTORY" -> findViewById<View>(R.id.hotspotHome).performClick()
                "SHOP"      -> findViewById<View>(R.id.hotspotShop).performClick()
            }
        }
        companionManager.refresh()
    }

    private fun setupHotspots() {
        findViewById<View>(R.id.hotspotForest).setOnClickListener {
            handleHotspotClick(R.id.hotspotForest, "les") { replaceMapContent(PokemonBattleFragment()) }
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