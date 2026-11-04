package cz.uhk.macroflow.loop

import android.content.Context
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.energy.Goal
import cz.uhk.macroflow.dashboard.MacroResult
import cz.uhk.macroflow.wearable.SleepInsights
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Android strana uzavřeného kruhu: sbírá data z databáze, drží rozhodnutí uživatele a záznam úprav. Volat mimo hlavní vlákno. */
object LoopData {
    private const val PREFS = "LoopPrefs"
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Rozhodnutí k dnešnímu návrhu: null = ještě nerozhodnuto, "applied" = použito, "kept" = nechat podle plánu. */
    class Snapshot(val situation: Situation, val readiness: Readiness?, val advice: Advice?, val decision: String?)

    fun snapshot(ctx: Context, today: LocalDate = LocalDate.now()): Snapshot {
        val app = ctx.applicationContext
        val db = AppDatabase.getDatabase(app)
        val fromKey = today.minusDays(LoopModel.BASELINE_DAYS.toLong()).toString()
        val wear = db.wearableDao().getDaysSync(fromKey, today.toString()).associateBy { it.date }
        val checks = db.checkInDao().getAllCheckInsSync().filter { it.date >= fromKey }.associateBy { it.date }
        val water = db.waterDao().getWaterSinceSync(fromKey).groupBy { it.date }.mapValues { e -> e.value.sumOf { it.amountMl } }
        val steps = db.stepsDao().getAllStepsSync().associate { it.date to it.count }
        val vol = db.workoutDao().getAllSync().filter { it.date >= fromKey }.groupBy { it.date }.mapValues { e -> e.value.sumOf { it.weightKg * it.reps } }

        fun model(d: LocalDate) = runCatching {
            MacroCalculator.calculateForDate(app, Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant()), applyAdaptive = false)
        }.getOrNull()

        val days = (0..LoopModel.BASELINE_DAYS).map { back ->
            val d = today.minusDays(back.toLong()); val k = d.toString(); val w = wear[k]; val c = checks[k]
            val recent = back in 1..3
            val consumed = if (recent) db.consumedSnackDao().getConsumedByDateSync(k).takeIf { it.isNotEmpty() }?.sumOf { it.calories.toDouble() } else null
            DayIn(
                day = d.toEpochDay().toInt(),
                sleepH = w?.sleepSec?.takeIf { it > 0 }?.let { it / 3600.0 }, sleepScore = w?.sleepScore, restHr = w?.restingHr,
                stress = w?.stressAvg, battery = w?.bbMax,
                energy = c?.energyLevel, sleepQ = c?.sleepQuality, hunger = c?.hungerLevel,
                steps = steps[k] ?: w?.steps, waterMl = water[k], volume = vol[k] ?: 0.0,
                intakeKcal = consumed, targetKcal = if (recent && consumed != null) model(d)?.calories else null
            )
        }.reversed()

        val profile = runBlocking { db.userProfileDao().getProfileSync() }
        val adaptive = db.adaptiveTdeeDao().getLatestSync()?.takeIf { it.status == "OK" }
        val loss = adaptive?.let { a -> a.weightChangeKgPerWeek?.let { w -> a.trendWeightKg?.takeIf { it > 0 }?.let { t -> -w / t * 100 } } }
        val bedtimes = wear.values.sortedBy { it.date }.takeLast(14).mapNotNull { SleepInsights.bedtimeMin(it) }.sorted()
        val situation = Situation(
            today = today.toEpochDay().toInt(), days = days,
            trainingToday = model(today)?.trainingType?.let { it != "REST" } ?: false,
            cutting = profile?.let { Goal.from(it.goal) == Goal.CUT },
            usualBedtimeMin = bedtimes.takeIf { it.size >= 3 }?.let { it[it.size / 2] },
            weeklyLossPct = loss
        )
        val r = LoopModel.readiness(situation)
        // Trénink si návrh přečte z preferencí (bez nového přepočtu)
        LoopModel.advice(situation, r ?: return Snapshot(situation, null, null, prefs(app).getString("dec:$today", null))).let { a ->
            prefs(app).edit().putString("train:$today", "${a.volumeFactor}|${a.rirDelta}|${a.score}").apply()
        }
        return Snapshot(situation, r, r?.let { LoopModel.advice(situation, it) }, prefs(app).getString("dec:$today", null))
    }

    /** Dnešní úprava tréninku: jen zmírnění (slabší forma). Null, když je forma v pořádku nebo uživatel zvolil „nechat podle plánu“. */
    class Training(val factor: Double, val rir: Int, val score: Int)

    fun training(ctx: Context, today: LocalDate = LocalDate.now()): Training? {
        val p = prefs(ctx)
        if (p.getString("dec:$today", null) == "kept") return null
        val s = p.getString("train:$today", null)?.split("|") ?: return null
        val t = Training(s[0].toDouble(), s[1].toInt(), s[2].toInt())
        return t.takeIf { it.factor < 0.97 || it.rir > 0 }
    }

    // ── Rozhodnutí a záznam ─────────────────────────────────────────────────

    /** Použít návrh (kalorie a voda se promítnou do dnešních cílů) nebo ho nechat být. Obojí je vratné. */
    fun decide(ctx: Context, a: Advice, apply: Boolean, today: LocalDate = LocalDate.now()) {
        val e = prefs(ctx).edit().putString("dec:$today", if (apply) "applied" else "kept")
        if (apply) e.putFloat("kcal:$today", a.kcalPct.toFloat()).putInt("water:$today", a.waterDeltaMl)
        else e.remove("kcal:$today").remove("water:$today")
        e.apply()
        log(ctx, today, if (apply) "Použito: ${describe(a)}" else "Nechán plán beze změny", apply)
    }

    fun undo(ctx: Context, today: LocalDate = LocalDate.now()) {
        prefs(ctx).edit().remove("dec:$today").remove("kcal:$today").remove("water:$today").apply()
        log(ctx, today, "Úprava vrácena", false)
    }

    fun describe(a: Advice) = listOfNotNull(
        a.kcalPct.takeIf { it != 0.0 }?.let { "${if (it > 0) "+" else ""}${Math.round(it * 100)} % kcal" },
        a.waterDeltaMl.takeIf { it != 0 }?.let { "+$it ml vody" }
    ).joinToString(", ").ifEmpty { "bez změny cílů" }

    /** Použitá úprava pro daný den. Jen čte preference, takže je levná a volá se z [MacroCalculator]. */
    fun applyTo(ctx: Context, dateKey: String, r: MacroResult): MacroResult {
        val p = prefs(ctx)
        if (p.getString("dec:$dateKey", null) != "applied") return r
        val pct = p.getFloat("kcal:$dateKey", 0f).toDouble(); val ml = p.getInt("water:$dateKey", 0)
        if (pct == 0.0 && ml == 0) return r
        val extra = r.calories * pct
        return r.copy(calories = r.calories + extra, carbs = r.carbs + extra / 4, water = r.water + ml / 1000.0)
    }

    class Entry(val date: String, val text: String, val applied: Boolean)

    private fun log(ctx: Context, d: LocalDate, text: String, applied: Boolean) {
        val p = prefs(ctx)
        val arr = runCatching { JSONArray(p.getString("log", "[]")) }.getOrDefault(JSONArray())
        val out = JSONArray().apply { put(JSONObject().put("d", d.toString()).put("t", text).put("a", applied)) }
        for (i in 0 until minOf(arr.length(), 59)) out.put(arr.getJSONObject(i))
        p.edit().putString("log", out.toString()).apply()
    }

    fun entries(ctx: Context): List<Entry> = runCatching {
        val arr = JSONArray(prefs(ctx).getString("log", "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Entry(o.getString("d"), o.getString("t"), o.optBoolean("a")) } }
    }.getOrDefault(emptyList())
}
