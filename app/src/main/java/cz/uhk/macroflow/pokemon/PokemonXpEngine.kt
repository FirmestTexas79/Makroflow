package cz.uhk.macroflow.pokemon

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.dashboard.MacroCalculator
import java.text.SimpleDateFormat
import java.util.*

/**
 * PokemonXpEngine — každý den zkontroluje splněné cíle
 * a přidá XP aktivnímu pokémonovi na liště.
 *
 * Volej z MainActivity.onResume() nebo po check-inu.
 * Každý zdroj XP se udělí max 1× za kalendářní den.
 */
object PokemonXpEngine {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Zkontroluje dnešní plnění a přidá XP.
     * Vrátí celkový počet udělených XP (0 pokud vše již bylo uděleno dnes).
     * Volej na pozadí vlákně (je synchronní).
     */
    fun checkAndAwardDailyXp(context: Context): Int {
        val prefs   = context.getSharedPreferences("PokemonXpPrefs", Context.MODE_PRIVATE)
        val today   = sdf.format(Date())
        val db      = AppDatabase.getDatabase(context)

        // Zjisti aktivního pokémona na liště
        val gamePrefs   = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val acquired    = gamePrefs.getBoolean("pokemonAcquired", false)
        val activePokId = gamePrefs.getString("currentOnBarId", null)

        if (!acquired || activePokId == null) return 0

        var totalAwarded = 0

        // ── 1. Otevření aplikace (každý den) ──────────────────────────
        val lastOpenKey = "last_open_$activePokId"
        if (prefs.getString(lastOpenKey, "") != today) {
            db.pokemonXpDao().addXp(activePokId, XpRewards.DAILY_OPEN)
            prefs.edit().putString(lastOpenKey, today).apply()
            totalAwarded += XpRewards.DAILY_OPEN
        }

        // ── 2. Check-in / ranní rituál ────────────────────────────────
        val lastCheckInKey = "last_checkin_$activePokId"
        if (prefs.getString(lastCheckInKey, "") != today) {
            val checkIn = db.checkInDao().getCheckInByDateSync(today)
            if (checkIn != null) {
                db.pokemonXpDao().addXp(activePokId, XpRewards.CHECK_IN)
                prefs.edit().putString(lastCheckInKey, today).apply()
                totalAwarded += XpRewards.CHECK_IN
            }
        }

        // ── 3. Zalogovaná jídla (alespoň 3) ──────────────────────────
        val lastFoodKey = "last_food_$activePokId"
        if (prefs.getString(lastFoodKey, "") != today) {
            val foodCount = db.consumedSnackDao().getAllConsumedSync()
                .count { it.date == today }
            if (foodCount >= 3) {
                db.pokemonXpDao().addXp(activePokId, XpRewards.LOGGED_FOOD)
                prefs.edit().putString(lastFoodKey, today).apply()
                totalAwarded += XpRewards.LOGGED_FOOD
            }
        }

        // ── 4. Vodní cíl ──────────────────────────────────────────────
        val lastWaterKey = "last_water_$activePokId"
        if (prefs.getString(lastWaterKey, "") != today) {
            val target   = MacroCalculator.calculate(context)
            val goalMl   = (target.water * 1000).toInt()
            val actualMl = db.waterDao().getTotalMlForDateSync(today)
            if (actualMl >= goalMl && goalMl > 0) {
                db.pokemonXpDao().addXp(activePokId, XpRewards.WATER_GOAL)
                prefs.edit().putString(lastWaterKey, today).apply()
                totalAwarded += XpRewards.WATER_GOAL
            }
        }

        // ── 5. Makra splněna (>=90% všech tří) ───────────────────────
        val lastMacroKey = "last_macro_$activePokId"
        if (prefs.getString(lastMacroKey, "") != today) {
            val target  = MacroCalculator.calculate(context)
            val consumed = db.consumedSnackDao().getAllConsumedSync()
                .filter { it.date == today }
            val eatP = consumed.sumOf { it.p.toDouble() }
            val eatS = consumed.sumOf { it.s.toDouble() }
            val eatT = consumed.sumOf { it.t.toDouble() }
            val macrosOk = target.protein > 0 &&
                    eatP >= target.protein * 0.90 &&
                    eatS >= target.carbs   * 0.90 &&
                    eatT >= target.fat     * 0.90
            if (macrosOk) {
                db.pokemonXpDao().addXp(activePokId, XpRewards.MACROS_HIT)
                prefs.edit().putString(lastMacroKey, today).apply()
                totalAwarded += XpRewards.MACROS_HIT
            }
        }

        return totalAwarded
    }

    /** Vrátí aktuální XP a level aktivního pokémona */
    fun getActiveXpInfo(context: Context): Pair<Int, Int>? {
        val gamePrefs   = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val activePokId = gamePrefs.getString("currentOnBarId", null) ?: return null
        val db          = AppDatabase.getDatabase(context)
        val xpEntity    = db.pokemonXpDao().getXp(activePokId) ?: return Pair(0, 1)
        val level       = PokemonLevelCalc.levelFromXp(xpEntity.totalXp)
        return Pair(xpEntity.totalXp, level)
    }
}