package cz.uhk.macroflow.pokemon

import android.content.Context
import cz.uhk.macroflow.common.AppPreferences
import cz.uhk.macroflow.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * PokemonXpEngine — pomocník pro udělení XP aktivnímu pokémonovi za splněné denní cíle.
 *
 * ⚠️ DŮLEŽITÉ: Základní denní XP (+20 za otevření aplikace každý den) řeší přímo
 * MainActivity.awardDailyXp(). Tato třída slouží pro DODATEČNÉ odměny z různých fragmentů
 * (makra, voda, kroky, jídla, check-in).
 *
 * Typické použití:
 *   val xp = PokemonXpEngine.tryAwardGoalXp(context, XpGoal.MACROS_HIT)
 *   if (xp > 0) (activity as? MainActivity)?.addXpToActivePokemonRealTime(xp)
 */
object PokemonXpEngine {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** Typy odměn, které lze udělit z různých míst v aplikaci */
    enum class XpGoal(val key: String, val xp: Int, val label: String) {
        MACROS_HIT    ("macros",   XpRewards.MACROS_HIT,      "splněná makra"),
        CHECK_IN      ("checkin",  XpRewards.CHECK_IN,        "ranní check-in"),
        WATER_GOAL    ("water",    XpRewards.WATER_GOAL,      "vodní cíl"),
        TRAINING_DONE ("training", XpRewards.TRAINING_LOGGED, "trénink"),
        FOOD_LOGGED   ("food",     XpRewards.LOGGED_FOOD,     "zalogovaná jídla"),
        STEPS_GOAL    ("steps",    XpRewards.STEPS_GOAL,      "denní kroky")
    }

    /**
     * Udělí XP za splněný cíl — ale jen jednou za den (ochrana proti duplicitám).
     *
     * @return Množství XP k přidání. Vrátí 0 pokud:
     *  - pokémon není aktivní na liště
     *  - tato odměna už byla dnes udělena
     *
     * Po obdržení nenulové hodnoty zavolej:
     *   (activity as? MainActivity)?.addXpToActivePokemonRealTime(xp)
     */
    suspend fun tryAwardGoalXp(context: Context, goal: XpGoal): Int = withContext(Dispatchers.IO) {
        val today = sdf.format(Date())
        val barState = AppPreferences.getActiveBarStateSync(context)

        // Pokud není aktivní pokémon, nevyplácíme nic
        if (!barState.acquired || barState.capturedId == -1) return@withContext 0

        val activeCapturedId = barState.capturedId

        // Klíč je unikátní per-pokémon per-den, aby se různí pokémoni nepřebíjeli
        if (AppPreferences.isXpAwardedToday(context, goal.key, activeCapturedId, today)) {
            return@withContext 0  // Už bylo uděleno dnes
        }

        // Označíme jako uděleno
        AppPreferences.markXpAwarded(context, goal.key, activeCapturedId, today)
        return@withContext goal.xp
    }

    /**
     * Zkontroluje splněné cíle a udělí XP (používá se při refresh DashboardFragmentu).
     * Obsahuje vlastní podmínky splnění (počítá jídla, check-in atd. z DB).
     *
     * @return Celkové XP přidané v tomto volání (0 = nic nového)
     */
    suspend fun checkAndAwardDailyGoals(context: Context): Int = withContext(Dispatchers.IO) {
        val today = sdf.format(Date())
        val db = AppDatabase.getDatabase(context)
        val barState = AppPreferences.getActiveBarStateSync(context)

        if (!barState.acquired || barState.capturedId == -1) return@withContext 0

        val activeCapturedId = barState.capturedId
        var totalAwarded = 0

        suspend fun alreadyAwarded(goal: XpGoal): Boolean =
            AppPreferences.isXpAwardedToday(context, goal.key, activeCapturedId, today)

        suspend fun markAwarded(goal: XpGoal) =
            AppPreferences.markXpAwarded(context, goal.key, activeCapturedId, today)

        // 1. Check-in
        if (!alreadyAwarded(XpGoal.CHECK_IN)) {
            val checkIn = db.checkInDao().getCheckInByDateSync(today)
            if (checkIn != null) {
                totalAwarded += XpRewards.CHECK_IN
                markAwarded(XpGoal.CHECK_IN)
            }
        }

        // 2. Zalogovaná jídla (aspoň 3)
        if (!alreadyAwarded(XpGoal.FOOD_LOGGED)) {
            val foodCount = db.consumedSnackDao().getAllConsumedSync().count { it.date == today }
            if (foodCount >= 3) {
                totalAwarded += XpRewards.LOGGED_FOOD
                markAwarded(XpGoal.FOOD_LOGGED)
            }
        }

        return@withContext totalAwarded
    }
}
