package cz.uhk.macroflow.common

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.pokemon.BattleFactory
import cz.uhk.macroflow.pokemon.EvolutionDialog
import cz.uhk.macroflow.pokemon.Move
import cz.uhk.macroflow.pokemon.PokemonGrowthManager
import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * PokemonXpController — řídí veškerou XP logiku aktivního Pokémona.
 *
 * Zodpovědnosti:
 *   - Denní XP odměna (20 XP jednou za den při spuštění aplikace)
 *   - Real-time přidání XP (po check-inu, tréninku, splnění cíle)
 *   - Detekce level-upu a zobrazení EvolutionDialogu pokud je splněna podmínka
 *   - Upload změn na Firebase
 *
 * Obě metody jsou volány z MainActivity:
 *   - awardDailyXp() → voláme v onResume()
 *   - addXpRealTime(amount) → voláme z fragmentů přes (activity as MainActivity)
 */
class PokemonXpController(
    private val activity: MainActivity,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    /**
     * Udělí 20 XP aktivnímu Pokémonovi jednou za kalendářní den.
     * Použije SharedPrefs klíč "lastXpDay_{capturedId}" jako pojistku.
     * Pokud Pokémon dosáhne úrovně evoluce, automaticky zobrazí EvolutionDialog.
     */
    fun awardDailyXp() {
        val prefs = activity.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
        val activeCapturedId = prefs.getInt("currentOnBarCapturedId", -1)
        if (activeCapturedId == -1) return // Žádný aktivní Pokémon

        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDay = prefs.getInt("lastXpDay_$activeCapturedId", -1)

        // Pokud jsme XP dnes už udělili, přeskočíme
        if (today == lastDay) return

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(activity)
            val pokemon = db.capturedPokemonDao().getPokemonById(activeCapturedId) ?: return@launch

            val oldLevel = pokemon.level
            pokemon.xp += 20
            val newLevel = PokemonLevelCalc.levelFromXp(pokemon.xp)
            pokemon.level = newLevel

            db.capturedPokemonDao().updatePokemon(pokemon)
            // Uložíme dnešní den jako poslední den odměny
            prefs.edit().putInt("lastXpDay_$activeCapturedId", today).apply()

            if (FirebaseRepository.isLoggedIn) {
                FirebaseRepository.uploadCapturedPokemon(pokemon)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(activity, "🎉 ${pokemon.name} získal 20 XP!", Toast.LENGTH_SHORT).show()

                // Zkontrolujeme evolveLevel z Pokédexu a případně zobrazíme dialog
                val entry = withContext(Dispatchers.IO) {
                    db.pokedexEntryDao().getEntry(pokemon.pokemonId)
                }
                if (entry != null && entry.evolveLevel > 0
                    && newLevel >= entry.evolveLevel && newLevel > oldLevel) {

                    val nextMove = getEvolutionMove(entry.evolveToId)
                    showEvolutionDialog(pokemon.id, pokemon.pokemonId, entry.evolveToId, nextMove)
                }
            }
        }
    }

    /**
     * Okamžitě přidá zadané množství XP aktivnímu Pokémonovi.
     * Volá se z fragmentů po splnění cíle (check-in, trénink, splnění maker).
     *
     * @param xpAmount Počet XP k přidání (závisí na typu akce, viz PokemonXpEngine)
     */
    fun addXpRealTime(xpAmount: Int) {
        val prefs = activity.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
        val activeCapturedId = prefs.getInt("currentOnBarCapturedId", -1)
        if (activeCapturedId == -1) return

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(activity)
            val pokemon = db.capturedPokemonDao().getPokemonById(activeCapturedId) ?: return@launch

            val oldLevel = pokemon.level
            pokemon.xp += xpAmount
            val newLevel = PokemonLevelCalc.levelFromXp(pokemon.xp)
            pokemon.level = newLevel

            db.capturedPokemonDao().updatePokemon(pokemon)

            if (FirebaseRepository.isLoggedIn) {
                try {
                    FirebaseRepository.uploadCapturedPokemon(pokemon)
                } catch (e: Exception) {
                    Log.e("XP_CONTROLLER", "Chyba uploadu: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(activity, "🎉 ${pokemon.name} získal $xpAmount XP!", Toast.LENGTH_SHORT).show()

                // Zkontrolujeme evolveLevel — tentokrát přes GrowthManager (pro evoluční řetězce)
                val entry = withContext(Dispatchers.IO) {
                    db.pokedexEntryDao().getEntry(pokemon.pokemonId)
                }
                if (entry != null && entry.evolveLevel > 0
                    && newLevel >= entry.evolveLevel && newLevel > oldLevel) {

                    val growthProfile = PokemonGrowthManager.getProfile(pokemon.pokemonId)
                    val targetEvolveId = growthProfile?.evolutionToId ?: ""

                    // Pokusíme se najít útok pro novou evoluci, fallback na level 1 útok
                    val nextMove = PokemonGrowthManager.getNewMoveForLevel(targetEvolveId, entry.evolveLevel)
                        ?: PokemonGrowthManager.getNewMoveForLevel(targetEvolveId, 1)

                    showEvolutionDialog(pokemon.id, pokemon.pokemonId, targetEvolveId, nextMove)
                }
            }
        }
    }

    /**
     * Zobrazí EvolutionDialog a po jeho zavření refreshne Pokémona na liště.
     * Voláno z Main vlákna po detekci level-upu.
     */
    private fun showEvolutionDialog(
        capturedId: Int,
        oldId: String,
        newId: String,
        newMove: Move?
    ) {
        EvolutionDialog(
            context = activity,
            capturedPokemonId = capturedId,
            oldId = oldId,
            newId = newId,
            newMoveToLearn = newMove
        ) {
            // Po zavření dialogu refreshneme vizuál Pokémona na spodní liště
            activity.updatePokemonVisibility()
        }.show()
    }

    /**
     * Vrátí útok pro danou evoluci (hardcoded pro Caterpie → Metapod → Butterfree).
     * Pro ostatní evoluce vrátí null (BattleFactory použije výchozí útok).
     */
    private fun getEvolutionMove(targetId: String): Move? = when (targetId) {
        "011" -> BattleFactory.attackHarden()
        "012" -> BattleFactory.attackGust()
        else -> null
    }
}