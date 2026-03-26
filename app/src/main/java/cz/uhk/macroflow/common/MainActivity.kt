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
import cz.uhk.macroflow.pokemon.WandererFactory
import cz.uhk.macroflow.training.PlanFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // ── 🔮 Handler pro částice z Ghost Plate ───────────────────────────
    private val particleHandler = Handler(Looper.getMainLooper())
    private val particleRunnable = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE)
            val ghostActive = prefs.getBoolean("spookyPlateActive", false)
            val cursedActive = prefs.getBoolean("cursedPlateActive", false) // ✅ Nová kontrola pro Cursed Plate

            if (ghostActive || cursedActive) {
                spawnItemParticle(cursedActive) // ✅ Pošleme informaci, zda jde o Cursed
            }
            particleHandler.postDelayed(this, 300)
        }
    }

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

        // 🌟 Spuštění generátoru kouře pro Ghost Plate
        particleHandler.post(particleRunnable)
    }

    override fun onResume() {
        super.onResume()
        updatePokemonVisibility()
        awardDailyXp()
    }

    // ── XP & Univerzální Evoluční Engine ──────────────────────────────

    private fun awardDailyXp() {
        lifecycleScope.launch {
            val awarded = withContext(Dispatchers.IO) {
                PokemonXpEngine.checkAndAwardDailyXp(this@MainActivity)
            }
            if (awarded > 0) {
                val (totalXp, newLevel) = withContext(Dispatchers.IO) {
                    PokemonXpEngine.getActiveXpInfo(this@MainActivity) ?: return@withContext null
                } ?: return@launch

                val prefs       = getSharedPreferences("PokemonXpPrefs", MODE_PRIVATE)
                val pId         = getSharedPreferences("GamePrefs", MODE_PRIVATE)
                    .getString("currentOnBarId", null) ?: return@launch
                val lastLevelKey = "last_known_level_$pId"
                val lastLevel    = prefs.getInt(lastLevelKey, 1)

                if (newLevel > lastLevel) {
                    prefs.edit().putInt(lastLevelKey, newLevel).apply()

                    val db = AppDatabase.getDatabase(this@MainActivity)
                    val entry = withContext(Dispatchers.IO) { db.pokedexEntryDao().getEntry(pId) }

                    // ✅ UNIVERZÁLNÍ KONTROLA: Načte evoluční data libovolného Pokémona ze statické DB tabulky
                    if (entry != null && entry.evolveLevel > 0 && newLevel >= entry.evolveLevel) {
                        val list = withContext(Dispatchers.IO) { db.capturedPokemonDao().getAllCaught() }
                        val activeInInv = list.find { it.pokemonId == pId }

                        if (activeInInv != null) {
                            // Načteme nový útok příslušné vývojové fáze z továrny na základě ID
                            val nextMove = getEvolutionMove(entry.evolveToId)

                            val dialog = EvolutionDialog(
                                context = this@MainActivity,
                                capturedPokemonId = activeInInv.id,
                                oldId = pId,
                                newId = entry.evolveToId,
                                newMoveToLearn = nextMove
                            ) {
                                updatePokemonVisibility() // Refreshne lištu až po úspěšné evoluci
                            }
                            dialog.show()
                        }
                    } else {
                        // Obyčejný level bez evoluce
                        showLevelUpToast(pId, newLevel)
                    }
                }
            }
        }
    }

    // Pomocná metoda pro získání nového útoku na základě cílového ID evoluce
    private fun getEvolutionMove(targetId: String): Move? = when (targetId) {
        "011" -> BattleFactory.attackHarden() // Metapod
        "012" -> BattleFactory.attackGust()   // Butterfree
        // 💡 Zde se bude jen jednoduše v budoucnu doplňovat: např. "002" -> BattleFactory.attackVineWhip() atd.
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

    // ── Pokémon na liště ──────────────────────────────────────────────

    fun openPokemonBattle() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, PokemonBattleFragment())
            .addToBackStack(null)
            .commit()
    }

    fun updatePokemonVisibility() {
        val ivPokemon = findViewById<ImageView>(R.id.ivDiglettBottomBar) ?: return
        val prefs     = getSharedPreferences("GamePrefs", MODE_PRIVATE)

        val acquired = prefs.getBoolean("pokemonAcquired", false)
        val pId      = prefs.getString("currentOnBarId", "050") ?: "050"
        val pName    = prefs.getString("currentOnBarName", "DIGLETT") ?: "DIGLETT"

        currentOnBarId = pId

        if (!acquired) {
            pokemonBehavior?.stop()
            pokemonBehavior = null
            ivPokemon.visibility = View.GONE
            return
        }

        val dp = resources.displayMetrics.density
        ivPokemon.layoutParams.width  = (52 * dp).toInt()
        ivPokemon.layoutParams.height = (52 * dp).toInt()
        ivPokemon.requestLayout()

        if (pId != lastLoadedId) {
            lastLoadedId = pId
            pokemonBehavior?.stop()
            pokemonBehavior = null

            ivPokemon.visibility = View.GONE
            ivPokemon.scaleX = 1f; ivPokemon.scaleY = 1f
            ivPokemon.alpha  = 1f; ivPokemon.rotation = 0f

            val webName  = pName.lowercase()
                .replace(" ", "-").replace(".", "")
                .replace("♀", "-f").replace("♂", "-m")
            val imageUrl = "https://img.pokemondb.net/sprites/lets-go-pikachu-eevee/normal/$webName.png"

            ivPokemon.load(imageUrl) {
                listener(onSuccess = { _, _ ->
                    val wanderer = WandererFactory.create(this@MainActivity, ivPokemon, pId)
                    pokemonBehavior = wanderer
                    ivPokemon.visibility = View.VISIBLE
                    pokemonBehavior?.start()
                })
            }

            ivPokemon.setOnClickListener { pokemonBehavior?.onSpriteClicked() }

        } else {
            ivPokemon.visibility = View.VISIBLE
            pokemonBehavior?.start()
        }
    }

    // ── 💨 Částicový generátor pro Lure (Spooky/Cursed Plate) ──────────────

    // ✅ NOVÉ: Ukládáme si odkaz na Handler, abychom mohli kouř vypnout
    private val lureSmokeHandler = Handler(Looper.getMainLooper())
    private var isLureActive = false

    // Přepsaná metoda pro cyklické generování kouře
    private fun runItemSpawner() {
        val prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE)
        val spookyUsed = prefs.getBoolean("spooky_plate_used", false)
        val cursedUsed = prefs.getBoolean("cursed_plate_used", false)

        val isActiveNow = spookyUsed || cursedUsed

        if (isActiveNow) {
            // ✅ Pokud je Lure aktivní a kouř neběží, zapneme ho plynule
            if (!isLureActive) {
                isLureActive = true
                lureSmokeHandler.post(lureSmokeRunnable)
            }
        } else {
            // ✅ Pokud Lure skončil, vypneme kouř
            isLureActive = false
            lureSmokeHandler.removeCallbacks(lureSmokeRunnable)
        }
    }

    // ✅ NOVÉ: Cyklus, který plynule generuje kouřová kolečka každých 150-250ms
    private val lureSmokeRunnable = object : Runnable {
        override fun run() {
            if (!isLureActive) return

            // Zkontrolujeme, zda Lure stále trvá v prefs
            val prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE)
            val isCursed = prefs.getBoolean("cursed_plate_used", false)
            val isSpooky = prefs.getBoolean("spooky_plate_used", false)

            if (isCursed || isSpooky) {
                spawnItemParticle(isCursed) // Vygeneruje jedno kouřové kolečko
                val nextDelay = Random.nextLong(150, 250) // Náhodné zpoždění pro plynulost
                lureSmokeHandler.postDelayed(this, nextDelay)
            } else {
                isLureActive = false
            }
        }
    }

    // ✅ PŘEPSANÁ: Generuje kouřový kruh, který se plynule zvětší a zmizí kolem tlačítka
    private fun spawnItemParticle(isCursed: Boolean) {
        val fab = findViewById<FloatingActionButton>(R.id.fabHome) ?: return
        // ✅ Změna: Částice dáváme do nového celoobrazovkového kontejneru
        val overlayContainer = findViewById<ViewGroup>(R.id.smokeOverlayContainer) ?: return

        val dp = resources.displayMetrics.density
        // Kolečka zvětšíme, ať udělají pořádnou mlhu
        val size = (Random.nextInt(50, 80) * dp).toInt()

        val colorStr = if (isCursed) {
            if (Random.nextBoolean()) "#CC4A148C" else "#CC000000" // Cursed (Temná/Černá)
        } else {
            if (Random.nextBoolean()) "#CC9167AB" else "#CC703F8F" // Spooky (Fialová)
        }

        val particle = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(colorStr))
            }
            layoutParams = ViewGroup.LayoutParams(size, size)

            // ✅ Výpočet pozice FAB tlačítka v rámci celé obrazovky
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

        // ✅ Animace kouřového kolečka: rozplynutí do stran a nahoru
        particle.animate()
            .scaleX(1.4f)
            .scaleY(1.4f)
            .translationYBy(-100f * dp) // Letí vysoko nad lištu!
            .alpha(0.5f)
            .setDuration(1000)
            .withEndAction {
                particle.animate()
                    .alpha(0f)
                    .scaleX(1.8f)
                    .scaleY(1.8f)
                    .translationYBy(-50f * dp)
                    .setDuration(1000)
                    .withEndAction { overlayContainer.removeView(particle) }
                    .start()
            }
            .start()
    }

    // ── 🔄 Navigace a Spodní UI prvky ───────────────────────────────────

    fun replaceFragment(fragment: Fragment) {
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

    // ── 🏆 Achievements & Ostatní ──────────────────────────────────────────

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
        particleHandler.removeCallbacks(particleRunnable)
    }
}