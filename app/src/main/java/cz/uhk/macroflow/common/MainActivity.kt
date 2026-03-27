package cz.uhk.macroflow.common

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import cz.uhk.macroflow.pokemon.PokemonXpEngine
import cz.uhk.macroflow.pokemon.BattleFactory
import cz.uhk.macroflow.pokemon.Move
import cz.uhk.macroflow.pokemon.Pokemon
import cz.uhk.macroflow.pokemon.PokemonGrowthManager
import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import cz.uhk.macroflow.pokemon.PokemonXpEntity
import cz.uhk.macroflow.pokemon.WandererFactory
import cz.uhk.macroflow.training.PlanFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private var pokemonBehavior: PokemonBehavior? = null
    private var lastLoadedId: String = ""
    private var currentOnBarId: String = ""

    private var fabHoldStart = 0L
    private val FAB_HOLD_MS  = 5000L

    companion object {
        private const val REQ_NOTIFICATION_PERMISSION = 100
    }

    private val lureSmokeHandler = Handler(Looper.getMainLooper())
    private var isLureActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideStatusBar()

        drawerLayout   = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        val bottomNav     = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val fabHome       = findViewById<FloatingActionButton>(R.id.fabHome)
        val btnOpenDrawer = findViewById<ImageButton>(R.id.btnOpenDrawer)

        bottomNav.itemActiveIndicatorColor = null

        if (savedInstanceState == null) replaceFragment(DashboardFragment())

        MakroflowNotifications.createChannels(this)
        requestNotificationPermissionAndSchedule()

        window.decorView.post { updatePokemonVisibility() }

        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        fabHome.setOnClickListener {
            replaceFragment(DashboardFragment())
            bottomNav.selectedItemId = R.id.nav_placeholder

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

        fabHome.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    fabHoldStart = System.currentTimeMillis()
                    v.postDelayed({
                        if (fabHoldStart > 0L &&
                            System.currentTimeMillis() - fabHoldStart >= FAB_HOLD_MS) {
                            fabHoldStart = 0L
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            openPokemonBattle()
                        }
                    }, FAB_HOLD_MS)
                    false
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> { fabHoldStart = 0L; false }
                else -> false
            }
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
            drawerLayout.closeDrawer(GravityCompat.END)
            when (item.itemId) {
                R.id.nav_pokedex    -> replaceFragment(PokedexFragment())
                R.id.nav_inventory  -> replaceFragment(InventoryFragment())
                R.id.drawerProfile  -> { replaceFragment(ProfileFragment()); bottomNav.selectedItemId = R.id.nav_profile }
                R.id.drawerAchievements -> replaceFragment(AchievementsFragment())
                R.id.drawerSettings     -> replaceFragment(SettingsFragment())
                R.id.drawerDisclaimer   -> replaceFragment(DisclaimerFragment())
                R.id.drawerResetAchievements -> {
                    AlertDialog.Builder(this)
                        .setTitle("Smazat achievementy?")
                        .setMessage("Všechny odemčené achievementy budou smazány. Tuto akci nelze vrátit.")
                        .setPositiveButton("Smazat") { _, _ ->
                            resetAchievements()
                            Toast.makeText(this, "✓ Achievementy smazány", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Zrušit", null).show()
                    return@setNavigationItemSelectedListener true
                }
                R.id.drawerSignOut -> {
                    if (FirebaseRepository.isLoggedIn) FirebaseRepository.signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
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
    }

    private fun awardDailyXp() {
        val prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE)
        val activeCapturedId = prefs.getInt("currentOnBarCapturedId", -1)
        if (activeCapturedId == -1) return

        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDay = prefs.getInt("lastXpDay_$activeCapturedId", -1)

        if (today != lastDay) {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val pokemon = db.capturedPokemonDao().getAllCaught().find { it.id == activeCapturedId }

                if (pokemon != null) {
                    pokemon.xp += 20

                    val newLevel = PokemonLevelCalc.levelFromXp(pokemon.xp)
                    val oldLevel = pokemon.level
                    pokemon.level = newLevel

                    db.capturedPokemonDao().updatePokemon(pokemon)
                    prefs.edit().putInt("lastXpDay_$activeCapturedId", today).apply()

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

    // V MainActivity.kt
    // V MainActivity.kt — úplně jedno kam do těla třídy

    fun addXpToActivePokemonRealTime(xpAmount: Int) {
        val prefs = getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val activeId = prefs.getString("currentOnBarId", "050") ?: "050"

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)

            // 1. Vytáhneme staré XP z tvé nové Room tabulky
            val currentXpEntity = db.pokemonXpDao().getXp(activeId) ?: PokemonXpEntity(activeId)
            val oldXp = currentXpEntity.totalXp
            val newXp = oldXp + xpAmount

            // 2. Vypočítáme levely pomocí tvého PokemonLevelCalc
            val oldLevel = PokemonLevelCalc.levelFromXp(oldXp)
            val newLevel = PokemonLevelCalc.levelFromXp(newXp)

            // 3. Uložíme nové XP do databáze
            db.pokemonXpDao().setXp(currentXpEntity.copy(totalXp = newXp))

            withContext(Dispatchers.Main) {
                // ✅ Okamžitá hláška o zisku XP na obrazovce!
                Toast.makeText(this@MainActivity, "🎉 Aktivní Pokémon získal $xpAmount XP!", Toast.LENGTH_SHORT).show()

                // 4. 🔥 Pokud došlo k Level UP a Pokémon má evoluci, spustíme EvolutionDialog!
                val entry = withContext(Dispatchers.IO) { db.pokedexEntryDao().getEntry(activeId) }

                if (entry != null && newLevel > oldLevel) {

                    val growthProfile = PokemonGrowthManager.getProfile(activeId)
                    val targetEvolveId = growthProfile?.evolutionToId ?: ""
                    val evolveLvl = growthProfile?.evolutionLevel ?: 1

                    // Zkontrolujeme, jestli vůbec má zapsanou evoluci a jestli na ni dosáhl
                    if (targetEvolveId.isNotEmpty() && newLevel >= evolveLvl) {

                        var nextMove = PokemonGrowthManager.getNewMoveForLevel(targetEvolveId, evolveLvl)
                        if (nextMove == null) {
                            nextMove = PokemonGrowthManager.getNewMoveForLevel(targetEvolveId, 1)
                        }

                        // Najdeme v inventáři reálnou instanci Pokémona k evoluci
                        val caughtPokemon = withContext(Dispatchers.IO) {
                            db.capturedPokemonDao().getAllCaught().find { it.pokemonId == activeId }
                        }

                        if (caughtPokemon != null) {
                            val dialog = EvolutionDialog(
                                context = this@MainActivity,
                                capturedPokemonId = caughtPokemon.id,
                                oldId = activeId,
                                newId = targetEvolveId,
                                newMoveToLearn = nextMove
                            ) {
                                updatePokemonVisibility() // Změní animaci a obrázek na liště po evoluci!
                            }
                            dialog.show()
                        }
                    }
                }
            }
        }
    }

    private fun getEvolutionMove(targetId: String): Move? = when (targetId) {
        "011" -> BattleFactory.attackHarden()
        "012" -> BattleFactory.attackGust()
        else  -> null
    }

    private fun showLevelUpToast(pokemonId: String, newLevel: Int) {
        val name = getSharedPreferences("GamePrefs", MODE_PRIVATE)
            .getString("currentOnBarName", "Pokémon") ?: "Pokémon"
        Toast.makeText(
            this,
            "🎉 $name dosáhl Level $newLevel!",
            Toast.LENGTH_LONG
        ).show()
    }

    fun openPokemonBattle() {
        stopLureSmoke()

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, PokemonBattleFragment())
            .addToBackStack(null)
            .commit()
    }

    fun updatePokemonVisibility() {
        val ivPokemon = findViewById<ImageView>(R.id.ivDiglettBottomBar) ?: return
        val prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE)

        val acquired = prefs.getBoolean("pokemonAcquired", false)
        val pId = prefs.getString("currentOnBarId", "050") ?: "050"
        val pName = prefs.getString("currentOnBarName", "DIGLETT") ?: "DIGLETT"

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

        // V MainActivity.kt najdi tuto část uvnitř updatePokemonVisibility()

        // V MainActivity.kt uvnitř updatePokemonVisibility()

        if (pId != lastLoadedId) {
            lastLoadedId = pId
            pokemonBehavior?.stop()
            pokemonBehavior = null

            ivPokemon.visibility = View.GONE
            ivPokemon.scaleX = 1f; ivPokemon.scaleY = 1f
            ivPokemon.alpha = 1f; ivPokemon.rotation = 0f

            // 🔍 1. Vytvoříme si dočasný fiktivní objekt Pokémona, abychom z BattleFactory vytáhli 100% správné webName!
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

            // ✅ 2. Použijeme spolehlivou firered-leafgreen sadu, kde Raichu i ostatní spolehlivě fungují
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
                        // Fallback pro jistotu na caterpie, aby tam nezůstalo prázdno
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
        val prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE)
        val ghostActive = prefs.getBoolean("ghostPlateActive", false)

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

            val prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE)
            val ghostActive = prefs.getBoolean("ghostPlateActive", false)

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
        val prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE)
        val ghostActive = prefs.getBoolean("ghostPlateActive", false)

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

    fun checkAchievements() {
        lifecycleScope.launch {
            val newlyUnlocked = withContext(Dispatchers.IO) {
                AchievementEngine.checkAll(this@MainActivity)
            }
            if (newlyUnlocked.isNotEmpty()) {
                AchievementUnlockQueue.enqueue(this@MainActivity, newlyUnlocked)
            }
        }
    }

    fun checkAchievementsDelayed(delayMs: Long = 1500L) {
        Handler(Looper.getMainLooper()).postDelayed({ checkAchievements() }, delayMs)
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

    private fun requestNotificationPermissionAndSchedule() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                MakroflowNotifications.scheduleAll(this)
            } else {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATION_PERMISSION)
            }
        } else {
            MakroflowNotifications.scheduleAll(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATION_PERMISSION) MakroflowNotifications.scheduleAll(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        pokemonBehavior?.stop()
        stopLureSmoke()
    }
}

// ✅ SPRÁVNÉ UMÍSTĚNÍ: Úplně mimo třídu MainActivity, na samotný konec souboru!
val Int.dp: Float
    get() = this * android.content.res.Resources.getSystem().displayMetrics.density