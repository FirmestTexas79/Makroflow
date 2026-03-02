package cz.uhk.macroflow

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val fabHome = findViewById<FloatingActionButton>(R.id.fabHome)

        // Odstraní barevné pozadí za vybranou ikonkou (pro čistý vzhled dle tvého manuálu)
        bottomNav.itemActiveIndicatorColor = null

        // Při prvním spuštění aplikace zobrazíme Dashboard
        if (savedInstanceState == null) {
            replaceFragment(DashboardFragment())
        }

        // Akce pro středové FAB tlačítko (Home/Dashboard)
        fabHome.setOnClickListener {
            replaceFragment(DashboardFragment())
            // Nastaví placeholder jako vybraný, aby žádná z ostatních ikon nesvítila
            bottomNav.selectedItemId = R.id.nav_placeholder
        }

        // Logika přepínání oken přes spodní menu
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_plan -> replaceFragment(PlanFragment())
                R.id.nav_snack -> replaceFragment(SnackFragment())
                R.id.nav_history -> replaceFragment(HistoryFragment())
                R.id.nav_profile -> replaceFragment(ProfileFragment())
            }
            true
        }
    }

    /**
     * Pomocná funkce pro výměnu fragmentů v hlavním kontejneru
     */
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out) // Přidá plynulý přechod
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}