package cz.uhk.macroflow

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import cz.uhk.macroflow.pokemon.InventoryFragment
import cz.uhk.macroflow.pokemon.PokedexFragment
import cz.uhk.macroflow.pokemon.PokemonWanderer
import cz.uhk.macroflow.pokemon.DigTransitionEffect
import cz.uhk.macroflow.pokemon.SmokeTransitionEffect
import cz.uhk.macroflow.pokemon.DiglettKilledDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private var pokemonWanderer: PokemonWanderer? = null
    private var fabHoldStart = 0L
    private val FAB_HOLD_MS  = 5000L

    companion object {
        private const val REQ_NOTIFICATION_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideStatusBar()

        drawerLayout   = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        val bottomNav      = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val fabHome        = findViewById<FloatingActionButton>(R.id.fabHome)
        val btnOpenDrawer  = findViewById<ImageButton>(R.id.btnOpenDrawer)

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
                android.view.MotionEvent.ACTION_DOWN -> {
                    fabHoldStart = System.currentTimeMillis()
                    v.postDelayed({
                        if (fabHoldStart > 0L &&
                            System.currentTimeMillis() - fabHoldStart >= FAB_HOLD_MS) {
                            fabHoldStart = 0L
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            openPokemonBattle()
                        }
                    }, FAB_HOLD_MS)
                    false
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> { fabHoldStart = 0L; false }
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
                R.id.nav_pokedex -> replaceFragment(PokedexFragment())
                R.id.nav_inventory -> replaceFragment(InventoryFragment())

                R.id.drawerProfile -> {
                    replaceFragment(ProfileFragment())
                    bottomNav.selectedItemId = R.id.nav_profile
                }
                R.id.drawerAchievements -> replaceFragment(AchievementsFragment())
                R.id.drawerSettings     -> replaceFragment(SettingsFragment())
                R.id.drawerDisclaimer   -> replaceFragment(DisclaimerFragment())
                R.id.drawerResetAchievements -> {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Smazat achievementy?")
                        .setMessage("Všechny odemčené achievementy budou smazány. Tuto akci nelze vrátit.")
                        .setPositiveButton("Smazat") { _, _ ->
                            resetAchievements()
                            android.widget.Toast.makeText(this, "✓ Achievementy smazány",
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Zrušit", null)
                        .show()
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
    }

    override fun onResume() {
        super.onResume()
        updatePokemonVisibility()
    }

    private fun requestNotificationPermissionAndSchedule() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                MakroflowNotifications.scheduleAll(this)
            } else {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIFICATION_PERMISSION
                )
            }
        } else {
            MakroflowNotifications.scheduleAll(this)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATION_PERMISSION) {
            MakroflowNotifications.scheduleAll(this)
        }
    }

    fun openPokemonBattle() {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, cz.uhk.macroflow.pokemon.PokemonBattleFragment())
            .addToBackStack(null)
            .commit()
    }

    fun updatePokemonVisibility() {
        val ivPokemon = findViewById<ImageView>(R.id.ivDiglettBottomBar) ?: return
        val fabHome   = findViewById<View>(R.id.fabHome) ?: return
        val prefs     = getSharedPreferences("GamePrefs", MODE_PRIVATE)

        val acquired   = prefs.getBoolean("pokemonAcquired", false)
        val pokemonId = prefs.getString("currentOnBarId", "050") ?: "050"
        val pokemonName = prefs.getString("currentOnBarName", "DIGLETT") ?: "DIGLETT"

        if (acquired) {
            ivPokemon.visibility = View.VISIBLE

            val drawableName = when (pokemonName) {
                "GENGAR" -> "pokemon_gengar"
                else -> "pokemon_diglett"
            }
            val resId = resources.getIdentifier(drawableName, "drawable", packageName)
            if (resId != 0) ivPokemon.setImageResource(resId)

            if (pokemonName == "GENGAR") {
                ivPokemon.scaleX = 0.7f
                ivPokemon.scaleY = 0.7f
                val dp = resources.displayMetrics.density
                ivPokemon.translationY = 8f * dp
            } else {
                ivPokemon.translationY = 0f
            }

            val effect = if (pokemonName == "DIGLETT") DigTransitionEffect() else SmokeTransitionEffect()

            if (pokemonWanderer == null) {
                pokemonWanderer = PokemonWanderer(this, ivPokemon, pokemonId, effect)
                pokemonWanderer!!.onKilled = {
                    pokemonWanderer?.stop()
                    pokemonWanderer = null
                    prefs.edit().putBoolean("pokemonAcquired", false).apply()
                    DiglettKilledDialog().show(supportFragmentManager, "DiglettKilled")
                }
                ivPokemon.setOnClickListener { pokemonWanderer?.onSpriteClicked() }
            }
            if (ivPokemon.width > 0) pokemonWanderer?.start()
            else ivPokemon.post { pokemonWanderer?.start() }
        } else {
            ivPokemon.visibility = View.GONE
            pokemonWanderer?.stop()
            pokemonWanderer = null
        }
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
        android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed({ checkAchievements() }, delayMs)
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
        val header      = navigationView.getHeaderView(0)
        val tvName      = header.findViewById<TextView>(R.id.tvDrawerName)
        val tvEmail     = header.findViewById<TextView>(R.id.tvDrawerEmail)
        val tvInitials  = header.findViewById<TextView>(R.id.tvAvatarInitials)
        val btnLogin    = header.findViewById<View>(R.id.btnDrawerLogin)
        val dotOnline   = header.findViewById<View>(R.id.viewOnlineDot)
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
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onDestroy() {
        super.onDestroy()
        pokemonWanderer?.stop()
    }
}