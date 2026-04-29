package cz.uhk.macroflow.pokemon

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CompanionManager(
    private val context: Context,
    private val ivCompanion: ImageView,
    private val tvCompanionLabel: TextView,
    private val companionShadow: View,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    fun refresh() {
        val prefs      = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        // OPRAVA: změněn klíč z "makromonAcquired" na "pokemonAcquired" (aby to sedělo s Inventory)
        val isAcquired = prefs.getBoolean("pokemonAcquired", false)
        val caughtDate = prefs.getLong("currentOnBarCaughtDate", -1L)

        if (!isAcquired || caughtDate == -1L) {
            hide()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val companion = AppDatabase.getDatabase(context)
                .capturedMakromonDao()
                .getMakromonByCaughtDate(caughtDate)

            withContext(Dispatchers.Main) {
                if (companion == null) { hide(); return@withContext }
                // OPRAVA: Posíláme ID i Jméno pro správné sestavení drawable
                show(companion.makromonId, companion.name)
            }
        }
    }

    private fun show(makromonId: String, name: String) {
        // --- KLÍČOVÁ OPRAVA: Dynamické sestavení názvu podle tvé nové konvence ---
        val shortId = if (makromonId.length >= 3) makromonId.takeLast(2) else makromonId
        val namePart = name.lowercase().trim().replace(" ", "_")

        // Sestavíme název: makromon_12_spirra
        val drawableName = "makromon_${shortId}_$namePart"

        val resId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
        val finalResId = if (resId != 0) resId else R.drawable.ic_home

        ivCompanion.setImageResource(finalResId)
        tvCompanionLabel.text = name
        tvCompanionLabel.setTextColor(android.graphics.Color.WHITE)

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