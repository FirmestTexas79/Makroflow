package cz.uhk.macroflow

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Zapne Edge-To-Edge roztažení pro moderní Androidy (včetně průhledného status baru)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // Pojistka pro starší verze
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        // Inicializace UI prvků
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val fabHome = findViewById<FloatingActionButton>(R.id.fabHome)
        val btnOpenDrawer = findViewById<ImageButton>(R.id.btnOpenDrawer)
        val topBar = findViewById<View>(R.id.topBar)

        // Odsazení top baru pod hodiny
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
            }
            insets
        }

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
                R.id.nav_plan -> replaceFragment(PlanFragment())
                R.id.nav_snack -> replaceFragment(SnackFragment())
                R.id.nav_history -> replaceFragment(HistoryFragment())
                R.id.nav_profile -> replaceFragment(ProfileFragment())
            }
            true
        }

        btnOpenDrawer.setOnClickListener {
            openDrawer()
        }

        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.END)
            when (item.itemId) {
                R.id.drawerProfile -> {
                    replaceFragment(ProfileFragment())
                    bottomNav.selectedItemId = R.id.nav_profile
                }
                R.id.drawerAchievements -> replaceFragment(AchievementsFragment())
                R.id.drawerSettings -> replaceFragment(SettingsFragment())
                R.id.drawerDisclaimer -> replaceFragment(DisclaimerFragment())
                R.id.drawerSignOut -> {
                    if (FirebaseRepository.isLoggedIn) FirebaseRepository.signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
            true
        }
    }

    fun openDrawer() {
        updateDrawerHeader()
        drawerLayout.openDrawer(GravityCompat.END)
    }

    private fun updateDrawerHeader() {
        val header = navigationView.getHeaderView(0)
        val tvName = header.findViewById<TextView>(R.id.tvDrawerName)
        val tvEmail = header.findViewById<TextView>(R.id.tvDrawerEmail)
        val tvInitials = header.findViewById<TextView>(R.id.tvAvatarInitials)
        val btnLogin = header.findViewById<View>(R.id.btnDrawerLogin)
        val dotOnline = header.findViewById<View>(R.id.viewOnlineDot)
        val signOutItem = navigationView.menu.findItem(R.id.drawerSignOut)

        val user = FirebaseRepository.currentUser
        if (user != null) {
            val name = user.displayName ?: "Sportovec"
            tvName.text = name
            tvEmail.text = user.email ?: ""
            tvInitials.text = name.firstOrNull()?.uppercase() ?: "S"
            btnLogin.visibility = View.GONE
            dotOnline.visibility = View.VISIBLE
            signOutItem?.title = "Odhlásit se"
        } else {
            tvName.text = "Sportovec"
            tvEmail.text = "Offline režim"
            tvInitials.text = "?"
            btnLogin.visibility = View.VISIBLE
            dotOnline.visibility = View.GONE
            signOutItem?.title = "Přihlásit se"
        }

        btnLogin.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.END)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}