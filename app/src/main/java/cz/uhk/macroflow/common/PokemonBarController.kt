package cz.uhk.macroflow.common

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LifecycleCoroutineScope
import coil.load
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.pokemon.PokemonBehavior
import cz.uhk.macroflow.pokemon.WandererFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PokemonBarController — řídí zobrazení Pokémona na spodní liště MainActivity.
 *
 * Zodpovědnosti:
 * - Načtení aktivního Pokémona z DB (podle caughtDate v GamePrefs)
 * - Stažení správného spritu (normální / shiny) z pokesprite CDN
 * - Spuštění/zastavení WandererFactory animace
 * - Nastavení click listeneru pro tap reakce Pokémona
 * - Retry logika pokud Pokémon v DB ještě není (čeká 3s a zkusí znovu)
 */
class PokemonBarController(
    private val activity: MainActivity,
    private val ivPokemon: ImageView,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    // Aktuálně běžící animační chování (Wanderer, Gengar, Diglett...)
    private var behavior: PokemonBehavior? = null

    // Klíč pro detekci změny Pokémona (caughtDate + isShiny)
    // Pokud se klíč nezměnil, nepřenačítáme obrázek zbytečně
    private var lastLoadedKey: String = ""

    /**
     * Hlavní vstupní bod — volej z MainActivity.updatePokemonVisibility().
     * Zkontroluje SharedPrefs, načte Pokémona z DB a spustí animaci.
     */
    fun refresh() {
        val prefs = activity.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
        val acquired = prefs.getBoolean("pokemonAcquired", false)
        val activeCaughtDate = prefs.getLong("currentOnBarCaughtDate", -1L)

        // Pokud žádný Pokémon není nasazen, schováme ImageView a zastavíme animaci
        if (!acquired || activeCaughtDate == -1L) {
            hide()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(activity)
            val caught = db.capturedPokemonDao().getPokemonByCaughtDate(activeCaughtDate)

            withContext(Dispatchers.Main) {
                if (caught == null) {
                    // Pokémon v DB zatím není (třeba Firebase sync ještě běží)
                    // Schováme a za 3s zkusíme znovu
                    ivPokemon.visibility = View.GONE
                    Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 3000)
                    return@withContext
                }

                val uniqueKey = "${caught.caughtDate}_${caught.isShiny}"

                // Pokud se Pokémon nezměnil, jen zajistíme že je viditelný a animace běží
                if (uniqueKey == lastLoadedKey) {
                    ivPokemon.visibility = View.VISIBLE
                    if (behavior == null) startBehavior(caught.pokemonId)
                    // Pojistka — listener musí být vždy nastaven i bez přenačtení spritu
                    setupClickListener()
                    return@withContext
                }

                // Nový Pokémon nebo změna shiny stavu — přenačteme sprite
                lastLoadedKey = uniqueKey
                stop()
                ivPokemon.visibility = View.INVISIBLE

                // Sestavení URL podle shiny stavu
                // Zdroj: github.com/msikma/pokesprite (gen8 sprity, průhledné pozadí)
                val spriteType = if (caught.isShiny) "shiny" else "regular"
                val formattedName = caught.name.lowercase().trim()
                    .replace(" ", "-")
                    .replace(".", "")
                    .replace("♀", "-f")
                    .replace("♂", "-m")

                val url = "https://raw.githubusercontent.com/msikma/pokesprite/master/pokemon-gen8/$spriteType/$formattedName.png"

                ivPokemon.load(url) {
                    crossfade(true)
                    placeholder(R.drawable.ic_home)
                    error(R.drawable.ic_home)
                    listener(onSuccess = { _, _ ->
                        // Obrázek načten — spustíme animaci pohybu
                        startBehavior(caught.pokemonId)
                        ivPokemon.visibility = View.VISIBLE
                        // Click listener nastavíme až po načtení spritu
                        // aby byl behavior již inicializován
                        setupClickListener()
                    })
                }
            }
        }
    }

    /**
     * Schová Pokémona a zastaví animaci.
     * Volá se při odhlášení nebo když žádný Pokémon není nasazen.
     */
    fun hide() {
        stop()
        ivPokemon.visibility = View.GONE
        lastLoadedKey = ""
    }

    /**
     * Zastaví aktuální animační chování bez schování ImageView.
     * Volá se před načtením nového Pokémona.
     */
    fun stop() {
        behavior?.stop()
        behavior = null
    }

    /**
     * Nastaví click listener na ImageView.
     * Deleguje tap na behavior.onSpriteClicked() — každý Pokémon
     * má vlastní reakci (Pikachu blesky, Snorlax kývání, Mew blikání...).
     * Voláme po každém startBehavior() aby listener vždy odkazoval
     * na aktuální instanci behavior.
     */
    private fun setupClickListener() {
        ivPokemon.setOnClickListener {
            behavior?.onSpriteClicked()
        }
    }

    // Vytvoří a spustí WandererFactory animaci pro daný pokemonId
    private fun startBehavior(pokemonId: String) {
        behavior = WandererFactory.create(activity, ivPokemon, pokemonId)
        behavior?.start()
    }
}