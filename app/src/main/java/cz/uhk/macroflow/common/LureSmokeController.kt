package cz.uhk.macroflow.common

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import cz.uhk.macroflow.R
import kotlin.random.Random

/**
 * LureSmokeController — řídí fialové částicové efekty kolem FAB tlačítka.
 *
 * Aktivuje se když je v GamePrefs nastaven "ghostPlateActive" = true.
 * Každých 250–450ms spawne jednu fialovou bublinu, která stoupá a mizí.
 *
 * Volání z MainActivity:
 *   - runItemSpawner() — zkontroluje stav a spustí/zastaví efekt
 *   - stop() — okamžité zastavení a vyčištění všech částic z overlay
 */
class LureSmokeController(private val activity: MainActivity) {

    // Handler pro plánování spawnu dalších částic na hlavním vlákně
    private val handler = Handler(Looper.getMainLooper())

    // Příznak zda efekt aktuálně běží (brání dvojitému spuštění)
    private var isActive = false

    // Runnable který se rekurzivně plánuje dokud je efekt aktivní
    private val smokeRunnable = object : Runnable {
        override fun run() {
            if (!isActive) return

            val prefs = activity.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
            val ghostActive = prefs.getBoolean("ghostPlateActive", false)

            if (ghostActive) {
                // Spawneme částici a naplánujeme další za náhodný interval
                spawnParticle()
                handler.postDelayed(this, Random.nextLong(250, 450))
            } else {
                // Deska byla deaktivována — zastavíme efekt
                stop()
            }
        }
    }

    /**
     * Zkontroluje stav ghostPlate a podle toho spustí nebo zastaví efekt.
     * Voláme z MainActivity.runItemSpawner() a onResume().
     */
    fun runIfActive() {
        val prefs = activity.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
        val ghostActive = prefs.getBoolean("ghostPlateActive", false)

        if (ghostActive) {
            if (!isActive) {
                isActive = true
                handler.post(smokeRunnable)
            }
        } else {
            stop()
        }
    }

    /**
     * Zastaví efekt, odstraní všechny existující částice z overlay kontejneru.
     * Voláme při navigaci pryč z Dashboardu nebo při odhlášení.
     */
    fun stop() {
        isActive = false
        handler.removeCallbacks(smokeRunnable)
        // Vyčistíme overlay — odstraníme všechny zbývající bubliny z obrazovky
        activity.findViewById<ViewGroup>(R.id.smokeOverlayContainer)?.removeAllViews()
    }

    /**
     * Vytvoří jednu fialovou bublinu u středu FAB a animuje ji nahoru.
     * Bublina se zvětší, zprůhlední a po dokončení se odstraní z view hierarchy.
     */
    private fun spawnParticle() {
        val fab = activity.findViewById<FloatingActionButton>(R.id.fabHome) ?: return
        val overlayContainer = activity.findViewById<ViewGroup>(R.id.smokeOverlayContainer) ?: return

        val dp = activity.resources.displayMetrics.density
        val size = (Random.nextInt(50, 80) * dp).toInt()

        // Náhodně vybereme světlejší nebo tmavší fialovou
        val colorStr = if (Random.nextBoolean()) "#CC9167AB" else "#CC703F8F"

        val particle = View(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(colorStr))
            }
            layoutParams = ViewGroup.LayoutParams(size, size)

            // Umístíme částici na střed FAB tlačítka
            val location = IntArray(2)
            fab.getLocationInWindow(location)
            x = location[0] + (fab.width / 2f) - (size / 2f)
            y = location[1] + (fab.height / 2f) - (size / 2f)

            alpha = 0f

            // Zabráníme interakci s částicí (je jen dekorativní)
            isClickable = false
            isFocusable = false
            isEnabled = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        }

        overlayContainer.addView(particle)

        // Fáze 1: Zvětšení a stoupání nahoru
        particle.animate()
            .scaleX(1.4f).scaleY(1.4f)
            .translationYBy(-100f * dp)
            .alpha(0.5f)
            .setDuration(1000)
            .withEndAction {
                // Fáze 2: Dofade a zmizení — po skončení odstraníme z hierarchy
                particle.animate()
                    .alpha(0f)
                    .scaleX(1.8f).scaleY(1.8f)
                    .translationYBy(-40f * dp)
                    .setDuration(1000)
                    .withEndAction { overlayContainer.removeView(particle) }
                    .start()
            }
            .start()
    }
}