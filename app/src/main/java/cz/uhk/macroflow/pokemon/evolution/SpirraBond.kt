package cz.uhk.macroflow.pokemon.evolution

import android.content.Context
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.pokemon.CapturedMakromonEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Pouto se Spirrou (docs/adr/0031): které dny byla aktivním parťákem a co se ten den stalo.
 * Dny se ukládají pro každou chycenou Spirru zvlášť; uzavřené dny se spočítají jednou a uloží,
 * dnešek se počítá pokaždé znovu. Vše mimo hlavní vlákno.
 */
object SpirraBond {

    private const val PREFS = "SpirraBond"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun daysKey(id: Int) = "days_$id"
    private fun cacheKey(id: Int, date: LocalDate) = "day_${id}_$date"

    /** Aktivní parťák, pokud je to Spirra. */
    fun activeSpirra(ctx: Context): CapturedMakromonEntity? {
        val id = ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).getInt("currentOnBarCapturedId", -1)
        if (id == -1) return null
        return AppDatabase.getDatabase(ctx).capturedMakromonDao().getMakromonById(id)
            ?.takeIf { it.makromonId == SpirraEvolution.SPIRRA_ID }
    }

    /** Zapíše dnešek jako den se Spirrou. */
    fun markToday(ctx: Context, capturedId: Int) {
        val p = prefs(ctx)
        val set = p.getStringSet(daysKey(capturedId), emptySet()).orEmpty()
        val today = LocalDate.now().toString()
        if (today !in set) p.edit().putStringSet(daysKey(capturedId), set + today).apply()
    }

    fun days(ctx: Context, capturedId: Int): List<SpirraEvolution.Day> {
        val p = prefs(ctx)
        val today = LocalDate.now()
        val dates = p.getStringSet(daysKey(capturedId), emptySet()).orEmpty()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        val edit = p.edit()
        val out = dates.map { d ->
            val cached = if (d < today) p.getString(cacheKey(capturedId, d), null)?.let { SpirraEvolution.decode(it) } else null
            cached ?: computeDay(ctx, d).also { if (d < today) edit.putString(cacheKey(capturedId, d), SpirraEvolution.encode(it)) }
        }
        edit.apply()
        return out
    }

    fun progress(ctx: Context, capturedId: Int) = SpirraEvolution.progress(days(ctx, capturedId))

    /** Po vývoji se pouto smaže (nová forma už se nevyvíjí). */
    fun clear(ctx: Context, capturedId: Int) {
        val p = prefs(ctx)
        p.edit().apply {
            remove(daysKey(capturedId))
            p.all.keys.filter { it.startsWith("day_${capturedId}_") }.forEach { remove(it) }
        }.apply()
    }

    private fun computeDay(ctx: Context, date: LocalDate): SpirraEvolution.Day {
        val db = AppDatabase.getDatabase(ctx)
        val d = date.toString()
        val food = runCatching { db.consumedSnackDao().getConsumedByDateSync(d) }.getOrDefault(emptyList())
        val steps = runCatching { db.stepsDao().getStepsForDateSync(d)?.count ?: 0 }.getOrDefault(0)
        val targets = runCatching {
            MacroCalculator.calculateForDate(ctx, Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()))
        }.getOrNull()
        val burned = targets?.expenditure?.let { it.walking + it.exercise }?.toInt() ?: 0
        return SpirraEvolution.Day(
            date = date,
            burnedKcal = burned,
            waterMl = runCatching { db.waterDao().getTotalMlForDateSync(d) ?: 0 }.getOrDefault(0),
            fiberG = food.sumOf { it.fiber.toDouble() },
            fiberTargetG = targets?.fiber ?: 0.0,
            nightMeals = food.count { SpirraEvolution.isNight(it.time) },
            steps = steps,
            workoutSets = runCatching { db.workoutDao().betweenSync(d, d).size }.getOrDefault(0)
        )
    }
}
