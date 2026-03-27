package cz.uhk.macroflow.pokemon

import android.content.Context
import android.util.Log
import cz.uhk.macroflow.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object PokemonXpEngine {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    data class XpResult(
        val awardedXp: Int,
        val shouldEvolve: Boolean = false,
        val evolutionData: EvolutionRequest? = null
    )

    data class EvolutionRequest(
        val capturedId: Long, // 🔥 Opraveno na Long (protože caughtDate je Long)
        val oldId: String,
        val newId: String,
        val moveToLearn: Move?
    )

    // ✅ HLAVNÍ METODA: Zkontroluje denní aktivity a přidá XP aktivnímu Pokémonovi
    suspend fun checkAndAwardDailyXp(context: Context): XpResult = withContext(Dispatchers.IO) {
        val today = sdf.format(Date())
        val db = AppDatabase.getDatabase(context)

        val gamePrefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val activeCapturedId = gamePrefs.getLong("currentOnBarCapturedId", -1L) // 🔥 Opraveno na getLong

        if (!gamePrefs.getBoolean("pokemonAcquired", false) || activeCapturedId == -1L) {
            return@withContext XpResult(0)
        }

        // Vytáhneme konkrétního Pokémona z batohu pomocí caughtDate (activeCapturedId)
        val activePokemon = db.capturedPokemonDao().getAllCaught().find { it.caughtDate == activeCapturedId } // 🔥 Opraveno na caughtDate
            ?: return@withContext XpResult(0)

        val prefs = context.getSharedPreferences("PokemonXpPrefs", Context.MODE_PRIVATE)
        var totalAwarded = 0

        // 1. Odměna za otevření aplikace
        val lastOpenKey = "last_open_${activePokemon.caughtDate}" // 🔥 Opraveno na caughtDate
        if (prefs.getString(lastOpenKey, "") != today) {
            totalAwarded += XpRewards.DAILY_OPEN
            prefs.edit().putString(lastOpenKey, today).apply()
        }

        // 2. Odměna za ranní Check-in
        val lastCheckInKey = "last_checkin_${activePokemon.caughtDate}" // 🔥 Opraveno na caughtDate
        if (prefs.getString(lastCheckInKey, "") != today) {
            val checkIn = db.checkInDao().getCheckInByDateSync(today)
            if (checkIn != null) {
                totalAwarded += XpRewards.CHECK_IN
                prefs.edit().putString(lastCheckInKey, today).apply()
            }
        }

        // 3. Odměna za poctivé zapisování jídel (aspoň 3 jídla)
        val lastFoodKey = "last_food_${activePokemon.caughtDate}" // 🔥 Opraveno na caughtDate
        if (prefs.getString(lastFoodKey, "") != today) {
            val foodCount = db.consumedSnackDao().getAllConsumedSync().count { it.date == today }
            if (foodCount >= 3) {
                totalAwarded += XpRewards.LOGGED_FOOD
                prefs.edit().putString(lastFoodKey, today).apply()
            }
        }

        if (totalAwarded == 0) return@withContext XpResult(0)

        // ✅ Připíšeme XP do instance v batohu
        activePokemon.xp += totalAwarded

        // Přepočítáme nový level
        val newLevel = PokemonLevelCalc.levelFromXp(activePokemon.xp)
        val leveledUp = newLevel > activePokemon.level
        activePokemon.level = newLevel

        // Uložíme aktualizovaného Pokémona do DB
        db.capturedPokemonDao().updatePokemon(activePokemon)

        // 🔍 Zkontrolujeme pravidla evoluce ze statického Pokédexu
        val pokedexRule = db.pokedexEntryDao().getEntry(activePokemon.pokemonId)

        if (pokedexRule != null && pokedexRule.evolveLevel > 0 && activePokemon.level >= pokedexRule.evolveLevel && pokedexRule.evolveToId.isNotEmpty()) {

            val nextMove = PokemonGrowthManager.getNewMoveForLevel(pokedexRule.evolveToId, pokedexRule.evolveLevel)

            return@withContext XpResult(
                awardedXp = totalAwarded,
                shouldEvolve = leveledUp,
                evolutionData = EvolutionRequest(
                    capturedId = activePokemon.caughtDate, // 🔥 Opraveno na caughtDate
                    oldId = activePokemon.pokemonId,
                    newId = pokedexRule.evolveToId,
                    moveToLearn = nextMove
                )
            )
        }

        return@withContext XpResult(totalAwarded)
    }
}