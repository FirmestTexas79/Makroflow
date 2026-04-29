package cz.uhk.macroflow.common

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LifecycleCoroutineScope
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.pokemon.PokemonBehavior
import cz.uhk.macroflow.pokemon.WandererFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MakromonBarController — řídí zobrazení Makromona na spodní liště MainActivity.
 *
 * Zodpovědnosti:
 * - Načtení aktivního Makromona z DB (podle caughtDate v GamePrefs)
 * - Načtení správného spritu z lokálního drawable (offline)
 * - Spuštění/zastavení WandererFactory animace
 * - Nastavení click listeneru pro tap reakce Makromona
 * - Retry logika pokud Makromon v DB ještě není (čeká 3s a zkusí znovu)
 *
 * Shiny logika zakomentována – odkomentuj až budou hotové shiny sprity:
 * val spriteType = if (caught.isShiny) "shiny" else "regular"
 */
class MakromonBarController(
    private val activity: MainActivity,
    private val ivPokemon: ImageView,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    private var behavior: PokemonBehavior? = null

    // Klíč pro detekci změny Makromona (caughtDate)
    // Shiny zatím není součástí klíče – odkomentuj až budou shiny sprity:
    // private var lastLoadedKey: String = "" // "${caught.caughtDate}_${caught.isShiny}"
    private var lastLoadedKey: String = ""

    fun refresh() {
        val prefs = activity.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
        val acquired = prefs.getBoolean("pokemonAcquired", false)
        val activeCaughtDate = prefs.getLong("currentOnBarCaughtDate", -1L)

        if (!acquired || activeCaughtDate == -1L) {
            hide()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(activity)
            val caught = db.capturedMakromonDao().getMakromonByCaughtDate(activeCaughtDate)

            withContext(Dispatchers.Main) {
                if (caught == null) {
                    ivPokemon.visibility = View.GONE
                    Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 3000)
                    return@withContext
                }

                // Shiny zatím není součástí klíče
                // Odkomentuj až budou shiny sprity: "${caught.caughtDate}_${caught.isShiny}"
                val uniqueKey = "${caught.caughtDate}"

                if (uniqueKey == lastLoadedKey) {
                    ivPokemon.visibility = View.VISIBLE
                    if (behavior == null) startBehavior(caught.makromonId)
                    setupClickListener()
                    return@withContext
                }

                // Nový Makromon – přenačteme sprite z lokálního drawable
                lastLoadedKey = uniqueKey
                stop()
                ivPokemon.visibility = View.INVISIBLE

                // Sestavení názvu drawable z názvu Makromona
                // Konvence: makromon_spirra, makromon_ignar, atd.
                // Shiny verze zakomentována – odkomentuj až budou hotové sprity:
                // val drawableName = if (caught.isShiny) "makromon_${name}_shiny" else "makromon_$name"
                val name = caught.name.lowercase().trim().replace(" ", "_")
                val drawableName = "makromon_$name"
                val resId = activity.resources.getIdentifier(
                    drawableName, "drawable", activity.packageName
                )
                val finalResId = if (resId != 0) resId else R.drawable.ic_home

                ivPokemon.setImageResource(finalResId)
                ivPokemon.visibility = View.VISIBLE
                startBehavior(caught.makromonId)
                setupClickListener()
            }
        }
    }

    fun hide() {
        stop()
        ivPokemon.visibility = View.GONE
        lastLoadedKey = ""
    }

    fun stop() {
        behavior?.stop()
        behavior = null
    }

    private fun setupClickListener() {
        ivPokemon.setOnClickListener {
            behavior?.onSpriteClicked()
        }
    }

    private fun startBehavior(makromonId: String) {
        behavior = WandererFactory.create(activity, ivPokemon, makromonId)
        behavior?.start()
    }
}