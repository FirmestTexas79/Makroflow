package cz.uhk.macroflow.common

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import cz.uhk.macroflow.R
import cz.uhk.macroflow.achievements.AchievementEngine
import cz.uhk.macroflow.achievements.AchievementUnlockQueue
import cz.uhk.macroflow.achievements.AchievementsFragment
import cz.uhk.macroflow.dashboard.DashboardFragment
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.history.HistoryFragment
import cz.uhk.macroflow.nutrition.SnackFragment
import cz.uhk.macroflow.pokemon.InventoryFragment
import cz.uhk.macroflow.pokemon.PokedexFragment
import cz.uhk.macroflow.pokemon.PokemonMapActivity
import cz.uhk.macroflow.profile.ProfileFragment
import cz.uhk.macroflow.training.PlanFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // ── Drawer ────────────────────────────────────────────────────────
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    // ── FAB long-press stav ───────────────────────────────────────────
    // isLongPressTriggered zabraňuje tomu, aby se po long-pressu spustil i onClick
    private var isLongPressTriggered = false
    private var holdRunnable: Runnable? = null

    // ── Kroky (cache pro UI, živá data jsou v CompanionForegroundService) ──
    private var currentStepsForUI = 0

    // ── Controllers ───────────────────────────────────────────────────
    // Každý controller zapouzdřuje jednu logickou oblast a drží si vlastní stav

    /** Řídí zobrazení, načítání spritu a animaci Pokémona na spodní liště */
    private lateinit var pokemonBarController: PokemonBarController

    /** Řídí fialový částicový efekt kolem FAB (Ghost Plate / Lure) */
    private lateinit var lureSmokeController: LureSmokeController

    /** Řídí denní XP odměnu a real-time přidávání XP aktivnímu Pokémonovi */
    private lateinit var pokemonXpController: PokemonXpController

    companion object {
        private const val REQ_NOTIFICATION_PERMISSION = 100
    }

    // ═══════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideStatusBar()

        // ── Inicializace controllerů ──────────────────────────────────
        // Musí být hned po setContentView, aby měly přístup k views
        val ivPokemon = findViewById<ImageView>(R.id.ivDiglettBottomBar)
        pokemonBarController = PokemonBarController(this, ivPokemon, lifecycleScope)
        lureSmokeController  = LureSmokeController(this)
        pokemonXpController  = PokemonXpController(this, lifecycleScope)

        // ── Notifikační kanály (musí být před startem service) ────────
        MakroflowNotifications.createChannels(this)

        // ── Cloud sync při startu (pojistka pro offline změny) ────────
        if (FirebaseRepository.isLoggedIn) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    FirebaseRepository.syncLocalDataToCloud(applicationContext)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        // ── Views ─────────────────────────────────────────────────────
        drawerLayout   = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        val bottomNav     = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val fabHome       = findViewById<FloatingActionButton>(R.id.fabHome)
        val btnOpenDrawer = findViewById<ImageButton>(R.id.btnOpenDrawer)

        bottomNav.itemActiveIndicatorColor = null

        // ── Výchozí fragment ──────────────────────────────────────────
        if (savedInstanceState == null) replaceFragment(DashboardFragment())

        // ── Oprávnění + naplánování notifikací ────────────────────────
        requestPermissionsAndSchedule()

        // ── Pokemon bar (po prvním layoutu, aby měly views rozměry) ──
        window.decorView.post { updatePokemonVisibility() }

        // ── Kroky — načteme ranní stav z DB do RAM ────────────────────
        loadTodayStepsFromDb()

        // ── Foreground service pro krokoměr a notifikaci ─────────────
        val serviceIntent = Intent(this, CompanionForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // ── Back press — zavře drawer místo navigace zpět ─────────────
        onBackPressedDispatcher.addCallback(this) {
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        setupFabListeners(fabHome, bottomNav)
        setupBottomNav(bottomNav)
        setupDrawerNav(btnOpenDrawer)

        checkAchievementsDelayed()
        runItemSpawner()
        updatePokemonVisibility()
    }

    override fun onResume() {
        super.onResume()
        // Refresh Pokémona (mohl se změnit zatímco jsme byli v jiné aktivitě)
        updatePokemonVisibility()
        // Denní XP — controller si hlídá aby se nedalo vícekrát za den
        pokemonXpController.awardDailyXp()
        // Zkontrolujeme zda je Ghost Plate stále aktivní
        runItemSpawner()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Zastavíme animaci Pokémona aby nedošlo k memory leaku
        pokemonBarController.stop()
        // Zastavíme částicový efekt a vyčistíme overlay
        lureSmokeController.stop()
    }

    // ═══════════════════════════════════════════════════════════════════
    // VEŘEJNÉ METODY (volané z fragmentů)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Načte dnešní počet kroků z Room DB do RAM cache.
     * Volají to fragmenty, které potřebují aktuální hodnotu synchronně.
     */
    fun loadTodayStepsFromDb() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val entity = db.stepsDao().getStepsForDateSync(todayStr)
            withContext(Dispatchers.Main) {
                currentStepsForUI = entity?.count ?: 0
            }
        }
    }

    /** Vrátí počet dnešních kroků z RAM cache (bez DB dotazu) */
    fun getTodayStepsCount(): Int = currentStepsForUI

    /**
     * Přidá XP aktivnímu Pokémonovi v reálném čase.
     * Volají fragmenty přes: (activity as? MainActivity)?.addXpToActivePokemonRealTime(xp)
     */
    fun addXpToActivePokemonRealTime(xpAmount: Int) {
        pokemonXpController.addXpRealTime(xpAmount)
    }

    /**
     * Refreshne vizuál Pokémona na spodní liště.
     * Volají fragmenty po změně aktivního Pokémona (nasazení, evoluce, shiny).
     */
    fun updatePokemonVisibility() {
        pokemonBarController.refresh()
    }

    /**
     * Zkontroluje/spustí/zastaví Ghost Plate efekt podle aktuálního stavu prefs.
     * Volají fragmenty po aktivaci/deaktivaci Ghost Plate v inventáři.
     */
    fun runItemSpawner() {
        lureSmokeController.runIfActive()
    }

    /**
     * Zastaví Ghost Plate efekt okamžitě.
     * Volá se při navigaci pryč z Dashboardu nebo otevření souboje.
     */
    fun stopLureSmoke() {
        lureSmokeController.stop()
    }

    /**
     * Otevře mapu Pokémon soubojů jako novou aktivitu.
     * Spouští se long-pressem na FAB (2 sekundy).
     */
    fun openPokemonBattle() {
        lureSmokeController.stop()
        val intent = Intent(this, PokemonMapActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    /**
     * Přidá fragment do nav_host_fragment s fade animací.
     * Při odchodu z Dashboardu zastaví Ghost Plate efekt.
     */
    fun replaceFragment(fragment: Fragment) {
        // Ghost Plate efekt patří jen k Dashboardu
        if (fragment !is DashboardFragment) lureSmokeController.stop()

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }

    /**
     * Spustí kontrolu achievementů s volitelným zpožděním.
     * Zpoždění zajistí, že DB operace (uložení dat) proběhnou dřív než check.
     */
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

    /** Smaže všechny achievementy z lokální DB (debug funkce) */
    fun resetAchievements() {
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(this@MainActivity).achievementDao().deleteAll()
        }
    }

    /** Probudí CompanionForegroundService aby překreslila sticky notifikaci */
    fun refreshStickyNotification() {
        startService(Intent(this, CompanionForegroundService::class.java))
    }

    /** Otevře pravý drawer a aktualizuje hlavičku s aktuálními user daty */
    fun openDrawer() {
        updateDrawerHeader()
        drawerLayout.openDrawer(GravityCompat.END)
    }

    // ═══════════════════════════════════════════════════════════════════
    // PRIVÁTNÍ SETUP METODY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Nastaví click a touch listenery pro FAB tlačítko.
     * Krátký klik → Dashboard, Long press (2s) → Pokemon souboj.
     */
    private fun setupFabListeners(
        fabHome: FloatingActionButton,
        bottomNav: BottomNavigationView
    ) {
        // Krátký klik — jen pokud neproběhl long press
        fabHome.setOnClickListener {
            if (!isLongPressTriggered) {
                replaceFragment(DashboardFragment())
                bottomNav.selectedItemId = R.id.nav_placeholder
                resetBottomNavVisuals(bottomNav)
            }
        }

        // Touch listener pro detekci long pressu (2000ms)
        fabHome.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPressTriggered = false
                    v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(150).start()

                    holdRunnable = Runnable {
                        isLongPressTriggered = true
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                        openPokemonBattle()
                        holdRunnable = null
                    }
                    v.postDelayed(holdRunnable!!, 2000L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Zrušíme naplánovaný long press pokud uživatel pustil prst dřív
                    holdRunnable?.let { v.removeCallbacks(it); holdRunnable = null }
                    if (!isLongPressTriggered) {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    }
                }
            }
            false // false = event propadne do onClickListeneru
        }
    }

    /**
     * Nastaví listener pro spodní navigační lištu.
     * Při výběru položky animuje ikonu nahoru a ostatní vrátí na místo.
     */
    private fun setupBottomNav(bottomNav: BottomNavigationView) {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_plan    -> replaceFragment(PlanFragment())
                R.id.nav_snack   -> replaceFragment(SnackFragment())
                R.id.nav_history -> replaceFragment(HistoryFragment())
                R.id.nav_profile -> replaceFragment(ProfileFragment())
            }

            bottomNav.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            // Animace vybrané ikony nahoru, ostatní zpět na výchozí pozici
            for (i in 0 until bottomNav.menu.size()) {
                val menuItem = bottomNav.menu.getItem(i)
                val itemView = bottomNav.findViewById<View>(menuItem.itemId) ?: continue
                val isSelected = menuItem.itemId == item.itemId
                itemView.animate()
                    .scaleX(if (isSelected) 1.1f else 1.0f)
                    .scaleY(if (isSelected) 1.1f else 1.0f)
                    .translationY(if (isSelected) -(4.dp) else 0f)
                    .setDuration(if (isSelected) 250 else 200)
                    .start()
            }
            true
        }
    }

    /**
     * Nastaví tlačítko pro otevření draweru a listener pro položky v draweru.
     */
    private fun setupDrawerNav(btnOpenDrawer: ImageButton) {
        btnOpenDrawer.setOnClickListener { openDrawer() }

        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.END)

            when (item.itemId) {
                // ── Sbírka ────────────────────────────────────────────
                R.id.nav_pokedex       -> replaceFragment(PokedexFragment())
                R.id.nav_inventory     -> replaceFragment(InventoryFragment())
                R.id.nav_generate_report -> showReportSetupDialog()

                // ── Profil & Nastavení ────────────────────────────────
                R.id.drawerProfile -> {
                    replaceFragment(ProfileFragment())
                    findViewById<BottomNavigationView>(R.id.bottomNavigation)
                        .selectedItemId = R.id.nav_profile
                }
                R.id.drawerSettings      -> replaceFragment(SettingsFragment())
                R.id.drawerAchievements  -> replaceFragment(AchievementsFragment())

                // ── Debug ─────────────────────────────────────────────
                R.id.drawerResetAchievements -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        AppDatabase.getDatabase(this@MainActivity).achievementDao().deleteAll()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Achievementy byly smazány", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                R.id.drawerDisclaimer -> {
                    Toast.makeText(this, "Aplikace nenahrazuje lékařskou pomoc.", Toast.LENGTH_LONG).show()
                }

                // ── Účet ──────────────────────────────────────────────
                R.id.drawerSignOut -> handleSignOut()
            }
            true
        }
    }

    /**
     * Zpracuje odhlášení nebo přihlášení.
     * Pokud uživatel není prokazatelně přihlášen (Firebase lag), jen přesměruje na Login.
     * Pokud je přihlášen: sync → signOut → vyčištění DB a prefs → Login.
     */
    private fun handleSignOut() {
        val user = FirebaseRepository.currentUser

        if (user == null) {
            // Firebase ještě nenačetl uživatele — bezpečně přesměrujeme bez mazání dat
            startActivity(Intent(this, LoginActivity::class.java))
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)

            // Sync před odhlášením (aby se neuvedla data)
            if (FirebaseRepository.isLoggedIn) {
                try { FirebaseRepository.syncLocalDataToCloud(applicationContext) }
                catch (e: Exception) { e.printStackTrace() }
                FirebaseRepository.signOut()
            }

            // Zastavení animací (musí být na Main)
            withContext(Dispatchers.Main) {
                pokemonBarController.hide()
                lureSmokeController.stop()
            }

            // Totální vyčištění lokálních dat
            db.stepsDao().deleteAll()
            db.clearAllTables()

            // Vymazání všech SharedPreferences
            listOf("GamePrefs", "UserPrefs", "TrainingPrefs").forEach { name ->
                getSharedPreferences(name, MODE_PRIVATE).edit().clear().apply()
            }

            // Návrat na Login s vyčištěným zásobníkem aktivit
            withContext(Dispatchers.Main) {
                startActivity(
                    Intent(this@MainActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }
        }
    }

    /**
     * Aktualizuje hlavičku draweru podle stavu přihlášení.
     * Zobrazí jméno, email, iniciály a online/offline indikátor.
     */
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
        }
    }

    /**
     * Zobrazí dialog pro generování PDF reportu.
     * Uživatel zadá název, report se vygeneruje a nabídne ke sdílení.
     */
    private fun showReportSetupDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "Název reportu (např. Sam - Duben)"
            setPadding(60, 40, 60, 40)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Generovat PDF Report")
            .setMessage("Vytvoří profesionální PDF dokument s tvým plánem a historií pro trenéra.")
            .setView(input)
            .setPositiveButton("Generovat a Sdílet") { _, _ ->
                val name = input.text.toString().ifEmpty { "Report MakroFlow 2.0" }
                lifecycleScope.launch {
                    val pdfUri = ReportGenerator.generatePdfReport(this@MainActivity, name)
                    if (pdfUri != null) sharePdfReport(pdfUri)
                    else Toast.makeText(this@MainActivity, "Chyba při vytváření PDF!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    /** Otevře share sheet pro odeslání PDF souboru */
    private fun sharePdfReport(uri: android.net.Uri) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_SUBJECT, "MakroFlow 2.0 - Tréninkový Report")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Odeslat PDF trenérovi přes..."
            )
        )
    }

    /**
     * Vrátí všechny ikony spodní navigace do výchozí pozice.
     * Volá se po kliknutí na FAB (Home), aby se odznačil předchozí výběr.
     */
    private fun resetBottomNavVisuals(bottomNav: BottomNavigationView) {
        for (i in 0 until bottomNav.menu.size()) {
            val menuItem = bottomNav.menu.getItem(i)
            bottomNav.findViewById<View>(menuItem.itemId)?.animate()
                ?.scaleX(1.0f)?.scaleY(1.0f)?.translationY(0f)?.setDuration(200)?.start()
        }
    }

    /**
     * Požádá o potřebná oprávnění a naplánuje notifikace.
     * Na Android 13+ (Tiramisu) žádá o POST_NOTIFICATIONS + ACTIVITY_RECOGNITION.
     * Na Android 10+ jen ACTIVITY_RECOGNITION.
     * Na starších verzích rovnou naplánuje notifikace.
     */
    private fun requestPermissionsAndSchedule() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val perms = mutableListOf(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
                }
                requestPermissions(perms.toTypedArray(), REQ_NOTIFICATION_PERMISSION)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                requestPermissions(
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                    REQ_NOTIFICATION_PERMISSION
                )
            }
            else -> MakroflowNotifications.scheduleAll(this)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATION_PERMISSION) {
            MakroflowNotifications.scheduleAll(this)
        }
    }

    /**
     * Skryje status bar pro immersive full-screen zážitek.
     * Na Android 11+ používá WindowInsetsController, na starších systemUiVisibility.
     */
    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }
    }

    // Extension property pro převod Int dp na Float px
    val Int.dp: Float
        get() = this * resources.displayMetrics.density
}