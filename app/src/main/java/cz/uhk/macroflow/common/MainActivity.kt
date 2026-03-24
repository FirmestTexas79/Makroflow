package cz.uhk.macroflow.common

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import coil.load // ✅ Přidán import pro načítání z webu
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
import cz.uhk.macroflow.pokemon.InventoryFragment
import cz.uhk.macroflow.pokemon.PokedexFragment
import cz.uhk.macroflow.pokemon.PokemonBattleFragment
import cz.uhk.macroflow.pokemon.PokemonBehavior
import cz.uhk.macroflow.pokemon.WandererFactory
import cz.uhk.macroflow.training.PlanFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private var pokemonBehavior: PokemonBehavior? = null

    // ✅ PŘIDÁNO: Držení stavu pro plynulé načítání z webu a swapování
    private var lastLoadedId: String = ""
    private var currentOnBarId: String = ""

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
                    AlertDialog.Builder(this)
                        .setTitle("Smazat achievementy?")
                        .setMessage("Všechny odemčené achievementy budou smazány. Tuto akci nelze vrátit.")
                        .setPositiveButton("Smazat") { _, _ ->
                            resetAchievements()
                            Toast.makeText(this, "✓ Achievementy smazány",
                                Toast.LENGTH_SHORT).show()
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
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                MakroflowNotifications.scheduleAll(this)
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
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
            .replace(R.id.nav_host_fragment, PokemonBattleFragment())
            .addToBackStack(null)
            .commit()
    }

    // ✅ TVRDÝ SWAP + NAČÍTÁNÍ Z WEBU LETS GO PIKACHU/EEVEE
    // ... uvnitř MainActivity -> updatePokemonVisibility
    fun updatePokemonVisibility() {
        val ivPokemon = findViewById<ImageView>(R.id.ivDiglettBottomBar) ?: return
        val prefs     = getSharedPreferences("GamePrefs", MODE_PRIVATE)

        val acquired   = prefs.getBoolean("pokemonAcquired", false)
        val pId        = prefs.getString("currentOnBarId", "050") ?: "050"
        val pName      = prefs.getString("currentOnBarName", "DIGLETT") ?: "DIGLETT"

        currentOnBarId = pId

        if (!acquired) {
            pokemonBehavior?.stop()
            pokemonBehavior = null
            ivPokemon.visibility = View.GONE
            return
        }

        // 🔥 PERMANENTNÍ ZVĚTŠENÍ NA LIŠTĚ (Nastavíme natvrdo 52x52 dp)
        val dp = resources.displayMetrics.density
        ivPokemon.layoutParams.width = (52 * dp).toInt()
        ivPokemon.layoutParams.height = (52 * dp).toInt()
        ivPokemon.requestLayout()

        if (pId != lastLoadedId) {
            lastLoadedId = pId
            pokemonBehavior?.stop()

            ivPokemon.visibility = View.GONE
            ivPokemon.scaleX = 1f
            ivPokemon.scaleY = 1f
            ivPokemon.alpha = 1f
            ivPokemon.rotation = 0f

            val webName = pName.lowercase()
                .replace(" ", "-")
                .replace(".", "")
                .replace("♀", "-f")
                .replace("♂", "-m")

            val imageUrl = "https://img.pokemondb.net/sprites/lets-go-pikachu-eevee/normal/$webName.png"

            ivPokemon.load(imageUrl) {
                listener(onSuccess = { _, _ ->
                    pokemonBehavior = WandererFactory.create(this@MainActivity, ivPokemon, pId)
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
        Handler(Looper.getMainLooper())
            .postDelayed({ checkAchievements() }, delayMs)
    }

    fun resetAchievements() {
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.Companion.getDatabase(this@MainActivity).achievementDao().deleteAll()
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
        pokemonBehavior?.stop()
    }
}