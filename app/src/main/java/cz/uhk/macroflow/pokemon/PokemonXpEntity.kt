package cz.uhk.macroflow.pokemon

import androidx.room.*

/**
 * XP systém pro pokémony na liště.
 *
 * XP zdroje (denně, každý jen 1× za den):
 *   +10 XP — splněné makro cíle (protein + sacharidy + tuky >= 90%)
 *   +8  XP — denní zápis / check-in
 *   +6  XP — splněný vodní cíl
 *   +5  XP — zalogováno alespoň 3 jídla
 *   +4  XP — zalogován trénink (PlanFragment)
 *   +2  XP — otevření aplikace (každý den)
 *
 * Level thresholdy (kumulativní XP):
 *   Lv1 → 0 XP
 *   Lv2 → 50 XP
 *   Lv3 → 150 XP
 *   Lv4 → 300 XP
 *   Lv5 → 500 XP
 *   Lv6 → 800 XP
 *   Lv7 → 1200 XP
 *   Lv8 → 1800 XP
 *   Lv9 → 2500 XP
 *   Lv10 → 3500 XP
 */
@Entity(tableName = "pokemon_xp")
data class PokemonXpEntity(
    @PrimaryKey val pokemonId: String,   // "050", "094", atd.
    val totalXp: Int = 0,
    val lastDailyRewardDate: String = "" // "yyyy-MM-dd" — zabraňuje více odměnám za den
)

@Dao
interface PokemonXpDao {

    @Query("SELECT * FROM pokemon_xp WHERE pokemonId = :pokemonId LIMIT 1")
    fun getXp(pokemonId: String): PokemonXpEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setXp(entity: PokemonXpEntity)

    @Transaction
    fun addXp(pokemonId: String, amount: Int) {
        val current = getXp(pokemonId) ?: PokemonXpEntity(pokemonId)
        setXp(current.copy(totalXp = current.totalXp + amount))
    }

    @Query("DELETE FROM pokemon_xp WHERE pokemonId = :pokemonId")
    fun deleteXp(pokemonId: String)
}

/** Pomocný objekt pro výpočty levelu z XP */
object PokemonLevelCalc {

    private val THRESHOLDS = listOf(0, 50, 150, 300, 500, 800, 1200, 1800, 2500, 3500)

    fun levelFromXp(xp: Int): Int {
        var level = 1
        for ((i, threshold) in THRESHOLDS.withIndex()) {
            if (xp >= threshold) level = i + 1
        }
        return level.coerceIn(1, THRESHOLDS.size)
    }

    fun xpForNextLevel(currentXp: Int): Int {
        val currentLevel = levelFromXp(currentXp)
        if (currentLevel >= THRESHOLDS.size) return 0
        return THRESHOLDS[currentLevel] - currentXp
    }

    fun progressToNextLevel(currentXp: Int): Float {
        val currentLevel = levelFromXp(currentXp)
        if (currentLevel >= THRESHOLDS.size) return 1f
        val levelStart = THRESHOLDS[currentLevel - 1]
        val levelEnd   = THRESHOLDS[currentLevel]
        return ((currentXp - levelStart).toFloat() / (levelEnd - levelStart).toFloat()).coerceIn(0f, 1f)
    }
}

/** XP zdroje s popisem */
object XpRewards {
    const val DAILY_OPEN      = 2   // otevření aplikace každý den
    const val LOGGED_FOOD     = 5   // zalogoval alespoň 3 jídla
    const val WATER_GOAL      = 6   // splněný vodní cíl
    const val TRAINING_LOGGED = 4   // zalogován trénink
    const val CHECK_IN        = 8   // denní check-in rituál
    const val MACROS_HIT      = 10  // splněna všechna makra (>= 90 %)
    const val STEPS_GOAL      = 6   // splněný denní krokový cíl
}