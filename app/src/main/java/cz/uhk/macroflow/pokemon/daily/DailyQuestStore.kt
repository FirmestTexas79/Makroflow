package cz.uhk.macroflow.pokemon.daily

import android.content.Context
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.data.AppDatabase
import java.time.LocalDate

/**
 * Denní úkoly – počítadla soubojů (SharedPreferences, klíče s datem), fakta z databáze
 * a vyzvednutí odměny (docs/adr/0029).
 */
object DailyQuestStore {

    private const val PREFS = "DailyQuests"
    private const val K_BIOMES = "won_biomes"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun day() = LocalDate.now().toString()

    /** Výhra nad divokým Makromonem ([type] = typ druhu, [biome] = skutečná lokalita). */
    fun recordWin(ctx: Context, type: String, biome: String) {
        val p = prefs(ctx); val d = day()
        val biomes = p.getStringSet(K_BIOMES, emptySet()).orEmpty() + (if (biome.startsWith("CAVE")) "CAVE" else biome)
        p.edit()
            .putInt("$d:win:$type", p.getInt("$d:win:$type", 0) + 1)
            .putInt("$d:biome:$biome", p.getInt("$d:biome:$biome", 0) + 1)
            .putStringSet(K_BIOMES, biomes)
            .apply()
    }

    fun recordCatch(ctx: Context) {
        val p = prefs(ctx); val d = day()
        p.edit().putInt("$d:catch", p.getInt("$d:catch", 0) + 1).apply()
    }

    /** Lokality, kde hráč už někdy vyhrál (úkoly jen tam, kam se umí dostat). */
    fun unlockedBiomes(ctx: Context): Set<String> = setOf("MEADOW") + prefs(ctx).getStringSet(K_BIOMES, emptySet()).orEmpty()

    fun targets(ctx: Context): DailyQuests.Targets = runCatching {
        val t = MacroCalculator.calculate(ctx)
        DailyQuests.Targets(waterMl = (t.water * 1000).toInt().takeIf { it > 0 }, proteinG = t.protein.toInt().takeIf { it > 0 })
    }.getOrDefault(DailyQuests.Targets())

    fun questsToday(ctx: Context): List<DailyQuests.Quest> =
        DailyQuests.forDay(LocalDate.now().toEpochDay(), targets(ctx), unlockedBiomes(ctx))

    /** Musí běžet mimo hlavní vlákno (čte databázi). */
    fun facts(ctx: Context): DailyQuests.Facts {
        val p = prefs(ctx); val d = day()
        val db = AppDatabase.getDatabase(ctx)
        val food = db.consumedSnackDao().getConsumedByDateSync(d)
        val all = p.all
        val winsByType = all.filterKeys { it.startsWith("$d:win:") }.mapKeys { it.key.removePrefix("$d:win:") }.mapValues { (it.value as? Int) ?: 0 }
        val winsByBiome = all.filterKeys { it.startsWith("$d:biome:") }.mapKeys { it.key.removePrefix("$d:biome:") }.mapValues { (it.value as? Int) ?: 0 }
        return DailyQuests.Facts(
            winsByType = winsByType,
            winsByBiome = winsByBiome,
            catches = p.getInt("$d:catch", 0),
            waterMl = runCatching { db.waterDao().getTotalMlForDateSync(d) ?: 0 }.getOrDefault(0),
            proteinG = food.sumOf { it.p.toDouble() }.toInt(),
            fiberG = food.sumOf { it.fiber.toDouble() }.toInt(),
            meals = cz.uhk.macroflow.nutrition.MealRepeat.meals(food.map { cz.uhk.macroflow.nutrition.MealRepeatRepository.toItem(it) }).size,
            steps = runCatching { db.stepsDao().getStepsForDateSync(d)?.count ?: 0 }.getOrDefault(0),
            workoutSets = runCatching { db.workoutDao().betweenSync(d, d).size }.getOrDefault(0),
            checkIn = runCatching { db.checkInDao().getCheckInByDateSync(d) != null }.getOrDefault(false)
        )
    }

    fun isClaimed(ctx: Context, index: Int) = prefs(ctx).getBoolean("${day()}:claimed:$index", false)

    /** Vyzvedne odměnu (jen jednou za den a úkol); vrací počet připsaných penízků. Mimo hlavní vlákno. */
    fun claim(ctx: Context, q: DailyQuests.Quest): Int {
        val p = prefs(ctx); val key = "${day()}:claimed:${q.index}"
        synchronized(this) {
            if (p.getBoolean(key, false)) return 0
            p.edit().putBoolean(key, true).commit()
        }
        AppDatabase.getDatabase(ctx).coinDao().addCoins(q.reward)
        return q.reward
    }

    /** Úklid počítadel starších dnů (volá se při otevření deníku). */
    fun prune(ctx: Context) {
        val p = prefs(ctx); val d = day()
        val old = p.all.keys.filter { it.contains(':') && !it.startsWith("$d:") }
        if (old.isNotEmpty()) p.edit().apply { old.forEach { remove(it) } }.apply()
    }
}
