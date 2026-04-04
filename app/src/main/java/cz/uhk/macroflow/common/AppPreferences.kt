package cz.uhk.macroflow.common

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Centrální DataStore singleton — nahrazuje GamePrefs, UserPrefs, TrainingPrefs, PokemonXpPrefs.
 *
 * Pravidlo volání:
 *  - *Sync funkce: volej z non-suspend kontextů nebo z Dispatchers.IO (nikdy z Dispatchers.Main).
 *  - suspend funkce: volej z coroutine kontextů (viewModelScope, lifecycleScope, Dispatchers.IO).
 */

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "macroflow_prefs")

object AppPreferences {

    // ══ TRAINING ══════════════════════════════════════════════════════════════════

    private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    private fun trainingTypeKey(day: String) = stringPreferencesKey("training_type_$day")
    private fun trainingTimeKey(day: String) = stringPreferencesKey("training_time_$day")

    fun getTrainingTypeSync(context: Context, day: String): String =
        runBlocking { context.dataStore.data.map { it[trainingTypeKey(day)] ?: "rest" }.first() }

    suspend fun setTrainingType(context: Context, day: String, type: String) =
        context.dataStore.edit { it[trainingTypeKey(day)] = type }

    fun setTrainingTypeSync(context: Context, day: String, type: String) = runBlocking {
        setTrainingType(context, day, type)
    }

    fun getTrainingTimeSync(context: Context, day: String): String? =
        runBlocking { context.dataStore.data.map { it[trainingTimeKey(day)] }.first() }

    fun setTrainingTimeSync(context: Context, day: String, time: String?) = runBlocking {
        context.dataStore.edit { prefs ->
            if (time != null) prefs[trainingTimeKey(day)] = time
            else prefs.remove(trainingTimeKey(day))
        }
    }

    /** Vrátí všechny typy tréninků najednou — pro Firebase sync upload. */
    suspend fun getAllTrainingTypes(context: Context): Map<String, String> {
        val prefs = context.dataStore.data.first()
        return DAYS.associateWith { prefs[trainingTypeKey(it)] ?: "rest" }
    }

    /** Zapíše celý tréninkový plán najednou — pro Firebase sync download. */
    suspend fun setAllTrainingTypes(context: Context, plan: Map<String, String>) =
        context.dataStore.edit { prefs ->
            plan.forEach { (day, type) -> prefs[trainingTypeKey(day)] = type }
        }

    // ══ USER ══════════════════════════════════════════════════════════════════════

    private val KEY_WEIGHT_AKT = stringPreferencesKey("user_weight_akt")
    private val KEY_HEIGHT     = stringPreferencesKey("user_height")
    private val KEY_AGE        = stringPreferencesKey("user_age")
    private val KEY_GENDER     = stringPreferencesKey("user_gender")
    private val KEY_MULTIPLIER = floatPreferencesKey("user_multiplier")

    fun getWeightAktSync(context: Context): String =
        runBlocking { context.dataStore.data.map { it[KEY_WEIGHT_AKT] ?: "83.0" }.first() }

    suspend fun setWeightAkt(context: Context, weight: String) =
        context.dataStore.edit { it[KEY_WEIGHT_AKT] = weight }

    /** Čte celý UserPrefs snapshot najednou — pro ProfileFragment fallback. */
    suspend fun getUserPrefsSnapshot(context: Context): UserPrefsSnapshot {
        val prefs = context.dataStore.data.first()
        return UserPrefsSnapshot(
            weightAkt  = prefs[KEY_WEIGHT_AKT] ?: "83.0",
            height     = prefs[KEY_HEIGHT]     ?: "175",
            age        = prefs[KEY_AGE]        ?: "22",
            gender     = prefs[KEY_GENDER]     ?: "male",
            multiplier = prefs[KEY_MULTIPLIER] ?: 1.2f
        )
    }

    suspend fun setUserPrefsSnapshot(context: Context, snapshot: UserPrefsSnapshot) =
        context.dataStore.edit { prefs ->
            prefs[KEY_WEIGHT_AKT] = snapshot.weightAkt
            prefs[KEY_HEIGHT]     = snapshot.height
            prefs[KEY_AGE]        = snapshot.age
            prefs[KEY_GENDER]     = snapshot.gender
            prefs[KEY_MULTIPLIER] = snapshot.multiplier
        }

    // ══ GAME ══════════════════════════════════════════════════════════════════════

    private val KEY_GHOST_PLATE  = booleanPreferencesKey("game_ghost_plate_active")
    private val KEY_ACQUIRED     = booleanPreferencesKey("game_pokemon_acquired")
    private val KEY_BAR_CAPTURED = intPreferencesKey("game_current_on_bar_captured_id")
    private val KEY_BAR_ID       = stringPreferencesKey("game_current_on_bar_id")
    private val KEY_BAR_NAME     = stringPreferencesKey("game_current_on_bar_name")

    private fun lastXpDayKey(capturedId: Int) = intPreferencesKey("game_last_xp_day_$capturedId")

    /**
     * Vrátí celý stav herní lišty najednou — pro MainActivity, BattleView, SpawnManager.
     * Volej z Dispatchers.IO nebo non-coroutine kontextů.
     */
    fun getActiveBarStateSync(context: Context): ActiveBarState = runBlocking {
        val prefs = context.dataStore.data.first()
        ActiveBarState(
            capturedId  = prefs[KEY_BAR_CAPTURED] ?: -1,
            pokemonId   = prefs[KEY_BAR_ID]       ?: "050",
            pokemonName = prefs[KEY_BAR_NAME]      ?: "DIGLETT",
            acquired    = prefs[KEY_ACQUIRED]      ?: false,
            ghostActive = prefs[KEY_GHOST_PLATE]   ?: false
        )
    }

    fun isGhostPlateActiveSync(context: Context): Boolean =
        runBlocking { context.dataStore.data.map { it[KEY_GHOST_PLATE] ?: false }.first() }

    suspend fun setGhostPlateActive(context: Context, active: Boolean) =
        context.dataStore.edit { it[KEY_GHOST_PLATE] = active }

    suspend fun setPokemonAcquired(context: Context, acquired: Boolean) =
        context.dataStore.edit { it[KEY_ACQUIRED] = acquired }

    /** Připíchne pokémona na lištu. */
    suspend fun pinPokemonToBar(context: Context, capturedId: Int, pokemonId: String, name: String) =
        context.dataStore.edit { prefs ->
            prefs[KEY_ACQUIRED]     = true
            prefs[KEY_BAR_CAPTURED] = capturedId
            prefs[KEY_BAR_ID]       = pokemonId
            prefs[KEY_BAR_NAME]     = name.uppercase()
        }

    /** Odepne pokémona z lišty. */
    suspend fun unpinPokemonFromBar(context: Context) =
        context.dataStore.edit { prefs ->
            prefs[KEY_ACQUIRED]     = false
            prefs[KEY_BAR_CAPTURED] = -1
        }

    fun getLastXpDaySync(context: Context, capturedId: Int): Int =
        runBlocking { context.dataStore.data.map { it[lastXpDayKey(capturedId)] ?: -1 }.first() }

    suspend fun setLastXpDay(context: Context, capturedId: Int, day: Int) =
        context.dataStore.edit { it[lastXpDayKey(capturedId)] = day }

    /** Aktualizuje ID pokémona na liště po evoluci — pouze pokud je to aktivní pokémon. */
    suspend fun updateBarPokemonAfterEvolution(context: Context, oldId: String, newId: String, newName: String) =
        context.dataStore.edit { prefs ->
            if (prefs[KEY_BAR_ID] == oldId) {
                prefs[KEY_BAR_ID]   = newId
                prefs[KEY_BAR_NAME] = newName
            }
        }

    // ══ POKEMON XP ════════════════════════════════════════════════════════════════

    private fun xpKey(goalKey: String, capturedId: Int, date: String) =
        stringPreferencesKey("xp_${goalKey}_${capturedId}_${date}")

    /** Vrátí true pokud XP za daný cíl bylo pro tohoto pokémona dnes uděleno. */
    suspend fun isXpAwardedToday(context: Context, goalKey: String, capturedId: Int, date: String): Boolean =
        context.dataStore.data.map { it[xpKey(goalKey, capturedId, date)] == date }.first()

    /** Označí XP za daný cíl jako udělené pro tohoto pokémona dnes. */
    suspend fun markXpAwarded(context: Context, goalKey: String, capturedId: Int, date: String) =
        context.dataStore.edit { it[xpKey(goalKey, capturedId, date)] = date }

    // ══ CLEAR ALL (odhlášení) ════════════════════════════════════════════════════

    /** Smaže všechny preference — voláno při odhlášení. */
    suspend fun clearAll(context: Context) = context.dataStore.edit { it.clear() }
}

/** Snapshot UserPrefs pro fallback v ProfileFragmentu. */
data class UserPrefsSnapshot(
    val weightAkt: String,
    val height: String,
    val age: String,
    val gender: String,
    val multiplier: Float
)

/** Stav aktivního pokémona na dolní liště. */
data class ActiveBarState(
    val capturedId: Int,
    val pokemonId: String,
    val pokemonName: String,
    val acquired: Boolean,
    val ghostActive: Boolean
)
