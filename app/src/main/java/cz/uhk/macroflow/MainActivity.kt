package cz.uhk.macroflow

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideStatusBar()

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val fabHome = findViewById<FloatingActionButton>(R.id.fabHome)
        val btnOpenDrawer = findViewById<ImageButton>(R.id.btnOpenDrawer)

        bottomNav.itemActiveIndicatorColor = null

        if (savedInstanceState == null) {
            replaceFragment(DashboardFragment())
        }

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
                            android.widget.Toast.makeText(
                                this,
                                "✓ Achievementy smazány",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
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

        // ── Achievement check po startu aplikace ─────────────────────
        // Počkáme 1.5s aby se UI stihlo načíst, pak zkontrolujeme
        checkAchievementsDelayed()
    }

    /**
     * Zkontroluje achievementy na pozadí a zobrazí animaci
     * pro nově získané. Volej kdekoliv po dokončení důležité
     * akce (zápis jídla, check-in, ...).
     */
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

    /**
     * Zavolá checkAchievements s krátkým zpožděním —
     * použij při startu nebo po otevření fragmentu.
     */
    fun checkAchievementsDelayed(delayMs: Long = 1500L) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            checkAchievements()
        }, delayMs)
    }

    // ── DEBUG / TEST: reset všech achievementů ───────────────────────
    /**
     * Smaže všechny odemčené achievementy z DB.
     * Zavolej z SettingsFragment nebo přes App Inspection.
     *
     * Příklad použití v SettingsFragment:
     *   (activity as? MainActivity)?.resetAchievements()
     */
    fun resetAchievements() {
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(this@MainActivity)
                .achievementDao()
                .deleteAll()
        }
    }

    fun openDrawer() {
        updateDrawerHeader()
        drawerLayout.openDrawer(GravityCompat.END)
    }

    private fun updateDrawerHeader() {
        val header    = navigationView.getHeaderView(0)
        val tvName    = header.findViewById<TextView>(R.id.tvDrawerName)
        val tvEmail   = header.findViewById<TextView>(R.id.tvDrawerEmail)
        val tvInitials = header.findViewById<TextView>(R.id.tvAvatarInitials)
        val btnLogin  = header.findViewById<View>(R.id.btnDrawerLogin)
        val dotOnline = header.findViewById<View>(R.id.viewOnlineDot)
        val signOutItem = navigationView.menu.findItem(R.id.drawerSignOut)

        val user = FirebaseRepository.currentUser
        if (user != null) {
            val name = user.displayName ?: "Sportovec"
            tvName.text    = name
            tvEmail.text   = user.email ?: ""
            tvInitials.text = name.firstOrNull()?.uppercase() ?: "S"
            btnLogin.visibility  = View.GONE
            dotOnline.visibility = View.VISIBLE
            signOutItem?.title   = "Odhlásit se"
        } else {
            tvName.text    = "Sportovec"
            tvEmail.text   = "Offline režim"
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
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
}