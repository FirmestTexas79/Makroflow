package cz.uhk.macroflow.pokemon

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.dashboard.MacroCalculator
import java.text.SimpleDateFormat
import java.util.*

object PokemonXpEngine {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ✅ REGISTR EVOLUČNÍCH ÚTOKŮ — sem budeš jen jednoduše připisovat další Pokémony!
    private val evolutionMoves = mapOf(
        "011" to { BattleFactory.attackHarden() }, // Metapod
        "012" to { BattleFactory.attackGust() },   // Butterfree
        // "002" to { BattleFactory.attackVineWhip() },// Ivysaur (příklad)
        // "005" to { BattleFactory.attackEmber() },   // Charmeleon (příklad)
        // "006" to { BattleFactory.attackFlamethrower() } // Charizard (příklad)
    )

    data class XpResult(
        val awardedXp: Int,
        val shouldEvolve: Boolean = false,
        val evolutionData: EvolutionRequest? = null
    )

    data class EvolutionRequest(
        val capturedId: Int,
        val oldId: String,
        val newId: String,
        val moveToLearn: Move?
    )

    fun checkAndAwardDailyXp(context: Context): XpResult {
        val prefs = context.getSharedPreferences("PokemonXpPrefs", Context.MODE_PRIVATE)
        val today = sdf.format(Date())
        val db = AppDatabase.getDatabase(context)

        val gamePrefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val acquired = gamePrefs.getBoolean("pokemonAcquired", false)
        val activePokId = gamePrefs.getString("currentOnBarId", null) ?: return XpResult(0)

        if (!acquired) return XpResult(0)

        var totalAwarded = 0

        // 1. Otevření aplikace
        val lastOpenKey = "last_open_$activePokId"
        if (prefs.getString(lastOpenKey, "") != today) {
            db.pokemonXpDao().addXp(activePokId, XpRewards.DAILY_OPEN)
            prefs.edit().putString(lastOpenKey, today).apply()
            totalAwarded += XpRewards.DAILY_OPEN
        }

        // 2. Check-in
        val lastCheckInKey = "last_checkin_$activePokId"
        if (prefs.getString(lastCheckInKey, "") != today) {
            val checkIn = db.checkInDao().getCheckInByDateSync(today)
            if (checkIn != null) {
                db.pokemonXpDao().addXp(activePokId, XpRewards.CHECK_IN)
                prefs.edit().putString(lastCheckInKey, today).apply()
                totalAwarded += XpRewards.CHECK_IN
            }
        }

        // 3. Jídla
        val lastFoodKey = "last_food_$activePokId"
        if (prefs.getString(lastFoodKey, "") != today) {
            val foodCount = db.consumedSnackDao().getAllConsumedSync().count { it.date == today }
            if (foodCount >= 3) {
                db.pokemonXpDao().addXp(activePokId, XpRewards.LOGGED_FOOD)
                prefs.edit().putString(lastFoodKey, today).apply()
                totalAwarded += XpRewards.LOGGED_FOOD
            }
        }

        val xpEntity = db.pokemonXpDao().getXp(activePokId) ?: return XpResult(totalAwarded)
        val currentLevel = PokemonLevelCalc.levelFromXp(xpEntity.totalXp)

        if (checkEvolutions(context, activePokId, currentLevel)) {
            val entry = db.pokedexEntryDao().getEntry(activePokId)
            val list = db.capturedPokemonDao().getAllCaught()
            val activeInInv = list.find { it.pokemonId == activePokId }

            if (entry != null && activeInInv != null) {
                // ✅ Získání útoku z registru (mapy)
                val nextMove = getEvolutionMove(entry.evolveToId)

                return XpResult(
                    awardedXp = totalAwarded,
                    shouldEvolve = true,
                    evolutionData = EvolutionRequest(
                        capturedId = activeInInv.id,
                        oldId = activePokId,
                        newId = entry.evolveToId,
                        moveToLearn = nextMove
                    )
                )
            }
        }

        return XpResult(totalAwarded)
    }

    fun checkEvolutions(context: Context, pokemonId: String, currentLevel: Int): Boolean {
        val db = AppDatabase.getDatabase(context)
        val entry = db.pokedexEntryDao().getEntry(pokemonId) ?: return false

        return entry.evolveLevel > 0 && currentLevel >= entry.evolveLevel
    }

    // ✅ Zjednodušená funkce, která se podívá do registru nahoře
    private fun getEvolutionMove(targetId: String): Move? {
        return evolutionMoves[targetId]?.invoke()
    }

    fun getActiveXpInfo(context: Context): Pair<Int, Int>? {
        val gamePrefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val activePokId = gamePrefs.getString("currentOnBarId", null) ?: return null
        val db = AppDatabase.getDatabase(context)
        val xpEntity = db.pokemonXpDao().getXp(activePokId) ?: return Pair(0, 1)
        val level = PokemonLevelCalc.levelFromXp(xpEntity.totalXp)
        return Pair(xpEntity.totalXp, level)
    }
}