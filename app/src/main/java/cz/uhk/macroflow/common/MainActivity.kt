package cz.uhk.macroflow.common

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import cz.uhk.macroflow.common.AppPreferences
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.profile.ProfileFragment
import cz.uhk.macroflow.R
import cz.uhk.macroflow.achievements.AchievementEngine
import cz.uhk.macroflow.achievements.AchievementUnlockQueue
import cz.uhk.macroflow.achievements.AchievementsFragment
import cz.uhk.macroflow.dashboard.DashboardFragment
import cz.uhk.macroflow.history.HistoryFragment
import cz.uhk.macroflow.nutrition.SnackFragment
import cz.uhk.macroflow.pokemon.EvolutionDialog
import cz.uhk.macroflow.pokemon.InventoryFragment
import cz.uhk.macroflow.pokemon.PokedexFragment
import cz.uhk.macroflow.pokemon.PokemonBattleFragment
import cz.uhk.macroflow.pokemon.PokemonBehavior
import cz.uhk.macroflow.pokemon.BattleFactory
import cz.uhk.macroflow.pokemon.Move
import cz.uhk.macroflow.pokemon.Pokemon
import cz.uhk.macroflow.pokemon.PokemonGrowthManager
import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import cz.uhk.macroflow.pokemon.WandererFactory
import cz.uhk.macroflow.training.PlanFragment
import cz.uhk.macroflow.data.StepsEntity
import cz.uhk.macroflow.pokemon.PokemonMapActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// 👣 ✅MainActivity teď spravuje kroky pro celou aplikaci
class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private var pokemonBehavior: PokemonBehavior? = null
    private var lastLoadedId: String = ""
    private var currentOnBarId: String = ""

    private var fabHoldStart = 0L
    private val FAB_HOLD_MS  = 2000L
    private var isLongPressTriggered = false
    private var holdRunnable: Runnable? = null


    companion object {
        private const val REQ_NOTIFICATION_PERMISSION = 100
    }

    private val lureSmokeHandler = Handler(Looper.getMainLooper())
    private var isLureActive = false

    // 👣 ✅ Senzorové proměnné celo-aplikace
    private var sensorManager: SensorManager? = null
    private var stepDetectorSensor: Sensor? = null
    private var todayStepsCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideStatusBar()

        // ☁️ 1. AUTOSYNC PŘI STARTU: Pojistka pro případ offline změn
        if (FirebaseRepository.isLoggedIn) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    FirebaseRepository.syncLocalDataToCloud(applicationContext)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        drawerLayout   = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        val bottomNav     = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val fabHome       = findViewById<FloatingActionButton>(R.id.fabHome)
        val btnOpenDrawer = findViewById<ImageButton>(R.id.btnOpenDrawer)

        bottomNav.itemActiveIndicatorColor = null

        if (savedInstanceState == null) replaceFragment(DashboardFragment())

        MakroflowNotifications.createChannels(this)
        requestPermissionsAndSchedule() // 👈 Upraveno, aby se zeptalo i na krokoměr

        window.decorView.post { updatePokemonVisibility() }

        // 👣 ✅ Inicializace HW senzoru z Pixelu
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        loadTodayStepsFromDb() // Načteme ranní stav do RAM

        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        // JEDEN JEDNOTNÝ CLICK LISTENER
        fabHome.setOnClickListener {
            // Klik se provede jen pokud NEPROBĚHL long press
            if (!isLongPressTriggered) {
                replaceFragment(DashboardFragment())
                bottomNav.selectedItemId = R.id.nav_placeholder
                resetBottomNavVisuals(bottomNav)
            }
        }

// JEDEN JEDNOTNÝ TOUCH LISTENER PRO LONG PRESS
        fabHome.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPressTriggered = false

                    // Vizuální odezva (smáčknutí tlačítka)
                    v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(150).start()

                    // Vytvoříme úkol pro otevření souboje
                    holdRunnable = Runnable {
                        isLongPressTriggered = true
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        // Vrátíme měřítko zpět
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                        openPokemonBattle()
                        holdRunnable = null
                    }

                    // Naplánujeme ho na 2 sekundy
                    v.postDelayed(holdRunnable!!, 2000L)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 🔥 KLÍČOVÁ OPRAVA: Pokud uživatel pustí prst, zrušíme naplánovaný úkol
                    holdRunnable?.let {
                        v.removeCallbacks(it)
                        holdRunnable = null
                    }

                    // Pokud to nebyl long press, vrátíme velikost tlačítka
                    if (!isLongPressTriggered) {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    }
                }
            }
            false // False zajistí, že event propadne do OnClickListeneru pro krátký klik
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_plan    -> replaceFragment(PlanFragment())
                R.id.nav_snack   -> replaceFragment(SnackFragment())
                R.id.nav_history -> replaceFragment(HistoryFragment())
                R.id.nav_profile -> replaceFragment(ProfileFragment())
            }

            bottomNav.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            for (i in 0 until bottomNav.menu.size()) {
                val menuItem = bottomNav.menu.getItem(i)
                val itemView = bottomNav.findViewById<View>(menuItem.itemId) ?: continue

                if (menuItem.itemId == item.itemId) {
                    itemView.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .translationY(-(4.dp))
                        .setDuration(250)
                        .start()
                } else {
                    itemView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationY(0f)
                        .setDuration(200)
                        .start()
                }
            }
            true
        }

        btnOpenDrawer.setOnClickListener { openDrawer() }

        navigationView.setNavigationItemSelectedListener { item ->
            // 1. Zavřeme menu hned po kliknutí
            drawerLayout.closeDrawer(GravityCompat.END)

            when (item.itemId) {
                // --- SEKCE: MOJE SBÍRKA ---
                R.id.nav_pokedex -> replaceFragment(PokedexFragment())

                R.id.nav_inventory -> replaceFragment(InventoryFragment())

                // --- SEKCE: HLAVNÍ (PROFIL & NASTAVENÍ) ---
                R.id.drawerProfile -> {
                    replaceFragment(ProfileFragment())
                    // Synchronizujeme spodní lištu, pokud tam profil je
                    val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
                    bottomNav.selectedItemId = R.id.nav_profile
                }

                // 🔥 TADY JE TA OPRAVA: Propojení na tvůj SettingsFragment
                R.id.drawerSettings -> replaceFragment(SettingsFragment())

                R.id.drawerAchievements -> replaceFragment(AchievementsFragment())

                // --- SEKCE: DEBUG / OSTATNÍ ---
                R.id.drawerResetAchievements -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(this@MainActivity)
                        db.achievementDao().deleteAll() // Předpokládám, že máš v DAO tuhle metodu
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Achievementy byly smazány", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                R.id.drawerDisclaimer -> {
                    // Tady můžeš zobrazit třeba AlertDialog s textem prohlášení
                    Toast.makeText(this, "Aplikace nenahrazuje lékařskou pomoc.", Toast.LENGTH_LONG).show()
                }

                // --- SEKCE: ÚČET ---
                R.id.drawerSignOut -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(this@MainActivity)

                        // ☁️ Sync před odhlášením
                        if (FirebaseRepository.isLoggedIn) {
                            try {
                                FirebaseRepository.syncLocalDataToCloud(applicationContext)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            FirebaseRepository.signOut()
                        }

                        // 🛑 Zastavení běžících procesů
                        withContext(Dispatchers.Main) {
                            pokemonBehavior?.stop()
                            // Pokud máš metodu stopLureSmoke() definovanou v MainActivity
                            try { stopLureSmoke() } catch (e: Exception) {}
                        }

                        // 🧹 Totální očista lokálních dat
                        db.stepsDao().deleteAll()
                        db.clearAllTables()

                        // Vymazání všech preferencí (DataStore)
                        AppPreferences.clearAll(this@MainActivity)

                        // 🚀 Návrat na Login
                        withContext(Dispatchers.Main) {
                            val intent = Intent(this@MainActivity, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
                }
            }
            true
        }

        checkAchievementsDelayed()

        runItemSpawner()
    }

    override fun onResume() {
        super.onResume()
        updatePokemonVisibility()
        awardDailyXp()
        runItemSpawner()

        // 👣 ✅ Senzor běží pořád, když je aplikace na popředí
        stepDetectorSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        // Senzor vypneme jen když se aplikace minimalizuje
        sensorManager?.unregisterListener(this)
    }

    // 👣 ✅ Živý poslech pro celou aplikaci!
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
            todayStepsCount++
            saveStepsToDb(todayStepsCount)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun loadTodayStepsFromDb() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val entity = db.stepsDao().getStepsForDateSync(todayStr)
            todayStepsCount = entity?.count ?: 0
        }
    }

    private fun saveStepsToDb(count: Int) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val db = AppDatabase.getDatabase(this@MainActivity)
        val entity = StepsEntity(date = todayStr, count = count)

        lifecycleScope.launch(Dispatchers.IO) {
            db.stepsDao().insertSteps(entity)

            if (FirebaseRepository.isLoggedIn) {
                try {
                    FirebaseRepository.uploadSteps(entity)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun awardDailyXp() {
        lifecycleScope.launch(Dispatchers.IO) {
            val barState = AppPreferences.getActiveBarStateSync(this@MainActivity)
            val activeCapturedId = barState.capturedId
            if (activeCapturedId == -1) return@launch

            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val lastDay = AppPreferences.getLastXpDaySync(this@MainActivity, activeCapturedId)

            if (today != lastDay) {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val pokemon = db.capturedPokemonDao().getPokemonById(activeCapturedId)

                if (pokemon != null) {
                    pokemon.xp += 20

                    val newLevel = PokemonLevelCalc.levelFromXp(pokemon.xp)
                    val oldLevel = pokemon.level
                    pokemon.level = newLevel

                    db.capturedPokemonDao().updatePokemon(pokemon)
                    AppPreferences.setLastXpDay(this@MainActivity, activeCapturedId, today)

                    if (FirebaseRepository.isLoggedIn) {
                        FirebaseRepository.uploadCapturedPokemon(pokemon)
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "🎉 ${pokemon.name} získal 20 XP!", Toast.LENGTH_SHORT).show()

                        val entry = withContext(Dispatchers.IO) { db.pokedexEntryDao().getEntry(pokemon.pokemonId) }
                        if (entry != null && entry.evolveLevel > 0 && newLevel >= entry.evolveLevel && newLevel > oldLevel) {
                            val nextMove = getEvolutionMove(entry.evolveToId)
                            val dialog = EvolutionDialog(
                                context = this@MainActivity,
                                capturedPokemonId = pokemon.id,
                                oldId = pokemon.pokemonId,
                                newId = entry.evolveToId,
                                newMoveToLearn = nextMove
                            ) {
                                updatePokemonVisibility()
                            }
                            dialog.show()
                        }
                    }
                }
            }
        }
    }

    fun addXpToActivePokemonRealTime(xpAmount: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val activeCapturedId = AppPreferences.getActiveBarStateSync(this@MainActivity).capturedId
            if (activeCapturedId == -1) return@launch
            val db = AppDatabase.getDatabase(this@MainActivity)
            val pokemon = db.capturedPokemonDao().getPokemonById(activeCapturedId)

            if (pokemon != null) {
                val oldLevel = pokemon.level

                pokemon.xp += xpAmount

                val newLevel = PokemonLevelCalc.levelFromXp(pokemon.xp)
                pokemon.level = newLevel

                db.capturedPokemonDao().updatePokemon(pokemon)

                if (FirebaseRepository.isLoggedIn) {
                    try {
                        FirebaseRepository.uploadCapturedPokemon(pokemon)
                    } catch (e: Exception) {
                        Log.e("FIREBASE_SYNC", "Chyba uploadu pokémona: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "🎉 ${pokemon.name} získal $xpAmount XP!", Toast.LENGTH_SHORT).show()

                    val entry = withContext(Dispatchers.IO) { db.pokedexEntryDao().getEntry(pokemon.pokemonId) }
                    if (entry != null && entry.evolveLevel > 0 && newLevel >= entry.evolveLevel && newLevel > oldLevel) {

                        val growthProfile = PokemonGrowthManager.getProfile(pokemon.pokemonId)
                        val targetEvolveId = growthProfile?.evolutionToId ?: ""

                        var nextMove = PokemonGrowthManager.getNewMoveForLevel(targetEvolveId, entry.evolveLevel)
                        if (nextMove == null) {
                            nextMove = PokemonGrowthManager.getNewMoveForLevel(targetEvolveId, 1)
                        }

                        val dialog = EvolutionDialog(
                            context = this@MainActivity,
                            capturedPokemonId = pokemon.id,
                            oldId = pokemon.pokemonId,
                            newId = targetEvolveId,
                            newMoveToLearn = nextMove
                        ) {
                            updatePokemonVisibility()
                        }
                        dialog.show()
                    }
                }
            }
        }
    }


    private fun resetBottomNavVisuals(bottomNav: BottomNavigationView) {
        for (i in 0 until bottomNav.menu.size()) {
            val menuItem = bottomNav.menu.getItem(i)
            val itemView = bottomNav.findViewById<View>(menuItem.itemId) ?: continue
            itemView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationY(0f)
                .setDuration(200)
                .start()
        }
    }

    private fun getEvolutionMove(targetId: String): Move? = when (targetId) {
        "011" -> BattleFactory.attackHarden()
        "012" -> BattleFactory.attackGust()
        else  -> null
    }

    fun openPokemonBattle() {
        stopLureSmoke() // Zastavíme částice, pokud běžely

        // Místo přímého vložení fragmentu spustíme Mapu jako novou aktivitu
        val intent = Intent(this, PokemonMapActivity::class.java)
        startActivity(intent)

        // Volitelně: Přidáme animaci přechodu, aby to vypadalo jako vstup do hry
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    fun updatePokemonVisibility() {
        val ivPokemon = findViewById<ImageView>(R.id.ivDiglettBottomBar) ?: return
        val barState = AppPreferences.getActiveBarStateSync(this)

        val acquired = barState.acquired
        val pId = barState.pokemonId
        val pName = barState.pokemonName

        currentOnBarId = pId

        if (!acquired) {
            pokemonBehavior?.stop()
            pokemonBehavior = null
            ivPokemon.visibility = View.GONE
            return
        }

        val dp = resources.displayMetrics.density
        ivPokemon.layoutParams.width = (52 * dp).toInt()
        ivPokemon.layoutParams.height = (52 * dp).toInt()
        ivPokemon.requestLayout()

        if (pId != lastLoadedId) {
            lastLoadedId = pId
            pokemonBehavior?.stop()
            pokemonBehavior = null

            ivPokemon.visibility = View.GONE
            ivPokemon.scaleX = 1f; ivPokemon.scaleY = 1f
            ivPokemon.alpha = 1f; ivPokemon.rotation = 0f

            val tempPokemon = Pokemon(
                name = pName.uppercase(),
                level = 1,
                maxHp = 1,
                attack = 1,
                defense = 1,
                speed = 1,
                moves = emptyList()
            )
            val webName = BattleFactory.webName(tempPokemon)

            val imageUrl = "https://img.pokemondb.net/sprites/lets-go-pikachu-eevee/normal/$webName.png"

            Log.d("POKEMON_IMAGE", "Načítám obrázek z: $imageUrl pro ID: $pId")

            ivPokemon.load(imageUrl) {
                crossfade(true)
                listener(
                    onSuccess = { _, _ ->
                        val wanderer = WandererFactory.create(this@MainActivity, ivPokemon, pId)
                        pokemonBehavior = wanderer
                        ivPokemon.visibility = View.VISIBLE
                        pokemonBehavior?.start()
                    },
                    onError = { _, _ ->
                        Log.e("POKEMON_IMAGE", "Nepodařilo se načíst obrázek z: $imageUrl")
                        ivPokemon.load("https://img.pokemondb.net/sprites/firered-leafgreen/normal/caterpie.png")
                        ivPokemon.visibility = View.VISIBLE
                    }
                )
            }

            ivPokemon.setOnClickListener { pokemonBehavior?.onSpriteClicked() }

        } else {
            ivPokemon.visibility = View.VISIBLE
            pokemonBehavior?.start()
        }
    }

    fun runItemSpawner() {
        val ghostActive = AppPreferences.isGhostPlateActiveSync(this)

        if (ghostActive) {
            if (!isLureActive) {
                isLureActive = true
                lureSmokeHandler.post(lureSmokeRunnable)
            }
        } else {
            stopLureSmoke()
        }
    }

    private val lureSmokeRunnable = object : Runnable {
        override fun run() {
            if (!isLureActive) return

            val ghostActive = AppPreferences.isGhostPlateActiveSync(this@MainActivity)

            if (ghostActive) {
                spawnItemParticle()
                lureSmokeHandler.postDelayed(this, Random.nextLong(250, 450))
            } else {
                stopLureSmoke()
            }
        }
    }

    private fun spawnItemParticle() {
        val fab = findViewById<FloatingActionButton>(R.id.fabHome) ?: return
        val overlayContainer = findViewById<ViewGroup>(R.id.smokeOverlayContainer) ?: return

        val dp = resources.displayMetrics.density
        val size = (Random.nextInt(50, 80) * dp).toInt()

        val colorStr = if (Random.nextBoolean()) "#CC9167AB" else "#CC703F8F"

        val particle = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(colorStr))
            }
            layoutParams = ViewGroup.LayoutParams(size, size)

            val location = IntArray(2)
            fab.getLocationInWindow(location)

            x = location[0] + (fab.width / 2f) - (size / 2f)
            y = location[1] + (fab.height / 2f) - (size / 2f)

            alpha = 0f

            isClickable = false
            isFocusable = false
            isEnabled = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }

        overlayContainer.addView(particle)

        particle.animate()
            .scaleX(1.4f)
            .scaleY(1.4f)
            .translationYBy(-100f * dp)
            .alpha(0.5f)
            .setDuration(1000)
            .withEndAction {
                particle.animate()
                    .alpha(0f)
                    .scaleX(1.8f)
                    .scaleY(1.8f)
                    .translationYBy(-40f * dp)
                    .setDuration(1000)
                    .withEndAction { overlayContainer.removeView(particle) }
                    .start()
            }
            .start()
    }

    fun stopLureSmoke() {
        isLureActive = false
        lureSmokeHandler.removeCallbacks(lureSmokeRunnable)
        val overlayContainer = findViewById<ViewGroup>(R.id.smokeOverlayContainer)
        overlayContainer?.removeAllViews()
    }

    fun replaceFragment(fragment: Fragment) {
        val ghostActive = AppPreferences.isGhostPlateActiveSync(this)

        if (fragment !is DashboardFragment && ghostActive) {
            stopLureSmoke()
        }

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    fun checkAchievementsDelayed(delayMs: Long = 1500L) {
        Handler(Looper.getMainLooper()).postDelayed({
            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val newlyUnlocked = withContext(Dispatchers.IO) {
                    AchievementEngine.checkAll(this@MainActivity)
                }
                if (newlyUnlocked.isNotEmpty()) {
                    AchievementUnlockQueue.enqueue(this@MainActivity, newlyUnlocked)
                }
            }
        }, delayMs)
    }

    fun resetAchievements() {
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(this@MainActivity).achievementDao().deleteAll()
        }
    }

    fun openDrawer() {
        updateDrawerHeader()
        drawerLayout.openDrawer(GravityCompat.END)
    }

    private fun updateDrawerHeader() {
        val header     = navigationView.getHeaderView(0)
        val tvName     = header.findViewById<TextView>(R.id.tvDrawerName)
        val tvEmail    = header.findViewById<TextView>(R.id.tvDrawerEmail)
        val tvInitials = header.findViewById<TextView>(R.id.tvAvatarInitials)
        val btnLogin   = header.findViewById<View>(R.id.btnDrawerLogin)
        val dotOnline  = header.findViewById<View>(R.id.viewOnlineDot)
        val signOutItem = navigationView.menu.findItem(R.id.drawerSignOut)

        val user = FirebaseRepository.currentUser
        if (user != null) {
            val name = user.displayName ?: "Sportovec"
            tvName.text     = name
            tvEmail.text    = user.email ?: ""
            tvInitials.text = name.firstOrNull()?.uppercase() ?: "S"
            btnLogin.visibility  = View.GONE
            dotOnline.visibility = View.VISIBLE
            signOutItem?.title   = "Odhlásit se"
        } else {
            tvName.text     = "Sportovec"
            tvEmail.text    = "Offline režim"
            tvInitials.text = "?"
            btnLogin.visibility  = View.VISIBLE
            dotOnline.visibility = View.GONE
            signOutItem?.title   = "Přihlásit se"
        }
        btnLogin.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun requestPermissionsAndSchedule() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perms = mutableListOf(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            requestPermissions(perms.toTypedArray(), REQ_NOTIFICATION_PERMISSION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), REQ_NOTIFICATION_PERMISSION)
        } else {
            MakroflowNotifications.scheduleAll(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATION_PERMISSION) {
            MakroflowNotifications.scheduleAll(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pokemonBehavior?.stop()
        stopLureSmoke()
    }

    val Int.dp: Float
        get() = this * android.content.res.Resources.getSystem().displayMetrics.density
}