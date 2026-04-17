package cz.uhk.macroflow.pokemon

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import coil.load
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CompanionManager — řídí zobrazení společníka (Pokémona) na mapě vedle Ashe.
 *
 * Zodpovědnosti:
 *  - Načtení aktuálního společníka z DB podle caughtDate uloženého v SharedPrefs
 *  - Výběr správného URL / lokálního drawable (normal vs shiny)
 *  - Zobrazení / skrytí všech tří view (ivCompanion, tvCompanionLabel, companionShadow)
 *  - Normalizace jména Pokémona pro URL (mezery, tečky, ♀♂)
 */
class CompanionManager(
    private val context: Context,
    private val ivCompanion: ImageView,
    private val tvCompanionLabel: TextView,
    private val companionShadow: View,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    // ─────────────────────────────────────────────────────────────────────────
    //  PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Načte a zobrazí aktuálního společníka.
     * Bezpečné volat opakovaně — např. při návratu z fragmentu.
     */
    fun refresh() {
        val prefs      = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val isAcquired = prefs.getBoolean("pokemonAcquired", false)
        val caughtDate = prefs.getLong("currentOnBarCaughtDate", -1L)

        if (!isAcquired || caughtDate == -1L) {
            hide()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val companion = AppDatabase.getDatabase(context)
                .capturedPokemonDao()
                .getPokemonByCaughtDate(caughtDate)

            withContext(Dispatchers.Main) {
                if (companion == null) { hide(); return@withContext }
                show(companion.name, companion.isShiny)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INTERNÍ
    // ─────────────────────────────────────────────────────────────────────────

    private fun show(name: String, isShiny: Boolean) {
        val webName = name.lowercase().trim()
            .replace(" ", "-")
            .replace(".", "")
            .replace("♀", "-f")
            .replace("♂", "-m")

        val imageData: Any = if (isShiny) {
            val resName = "shiny_${webName.replace("-", "_")}"
            val resId   = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) resId
            else "https://img.pokemondb.net/sprites/ruby-sapphire/shiny/$webName.png"
        } else {
            "https://img.pokemondb.net/sprites/lets-go-pikachu-eevee/normal/$webName.png"
        }

        ivCompanion.load(imageData) { crossfade(true); placeholder(R.drawable.ic_home) }
        tvCompanionLabel.text = if (isShiny) "⭐ $name" else name
        tvCompanionLabel.setTextColor(
            if (isShiny) Color.parseColor("#DDA15E") else Color.WHITE
        )

        ivCompanion.visibility      = View.VISIBLE
        tvCompanionLabel.visibility = View.VISIBLE
        companionShadow.visibility  = View.VISIBLE
    }

    private fun hide() {
        ivCompanion.visibility      = View.GONE
        tvCompanionLabel.visibility = View.GONE
        companionShadow.visibility  = View.GONE
    }
}