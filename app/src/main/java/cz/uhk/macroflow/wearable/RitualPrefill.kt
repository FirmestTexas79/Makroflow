package cz.uhk.macroflow.wearable

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.dashboard.MacroCalculator
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/** Navrh tri rannich hodnot (1-5) z hodinek. Pravidla, ne ML: dost dat na uceni zatim neni; opravy uzivatele se meri. */
object RitualPrefill {
    data class Prefill(val sleep: Int, val energy: Int, val hunger: Int)

    /** Garmin: 90+ vyborne, 80+ dobre, 60+ prijatelne, pod 60 slabe. Kratky spanek skore zastropuje. */
    fun sleepRating(d: WearableDayEntity): Int? {
        val sec = d.sleepSec ?: return null
        val byScore = d.sleepScore?.let { s -> when { s >= 90 -> 5; s >= 80 -> 4; s >= 60 -> 3; s >= 40 -> 2; else -> 1 } }
        val h = sec / 3600.0
        val byDuration = when { h >= 7.5 -> 4; h >= 6.5 -> 3; h >= 5.5 -> 2; else -> 1 }
        val cap = when { h < 5.0 -> 2; h < 6.0 -> 3; else -> 5 }
        return minOf(byScore ?: byDuration, cap)
    }

    /** Body Battery rano (denni maximum = hodnota po probuzeni) -> 1-5 */
    fun batteryRating(bb: Int?): Int? = bb?.let { when { it >= 75 -> 5; it >= 55 -> 4; it >= 40 -> 3; it >= 25 -> 2; else -> 1 } }

    /**
     * @param today        den s dnesnim probuzenim (spanek + Body Battery)
     * @param yesterday    vcerejsek (aktivni kcal) a [recentActiveAvg] = prumer aktivnich kcal predchozich dnu
     * @param yesterdayBalanceKcal snedeno - cil za vcerejsek; null = nezapsano
     */
    fun predict(today: WearableDayEntity?, yesterday: WearableDayEntity?, recentActiveAvg: Double?, yesterdayBalanceKcal: Double?): Prefill? {
        val t = today ?: return null
        val sleep = sleepRating(t) ?: return null
        val bb = batteryRating(t.bbMax)
        val energy = if (bb != null) (0.6 * bb + 0.4 * sleep).roundToInt() else sleep

        var hunger = 3
        if ((t.sleepSec ?: Int.MAX_VALUE) < 6 * 3600) hunger++                  // kratky spanek zvysuje chut k jidlu
        if (yesterdayBalanceKcal != null && yesterdayBalanceKcal < -300) hunger++      // vcera deficit
        if (yesterdayBalanceKcal != null && yesterdayBalanceKcal > 300) hunger--       // vcera prebytek
        val act = yesterday?.activeKcal
        if (act != null && recentActiveAvg != null && recentActiveAvg > 0 && act >= 1.25 * recentActiveAvg) hunger++ // vcera velky vydej
        return Prefill(sleep.coerceIn(1, 5), energy.coerceIn(1, 5), hunger.coerceIn(1, 5))
    }

    /** Prumerna absolutni chyba odhadu vs. to, co uzivatel nakonec zadal (radky "pS,pE,pH,aS,aE,aH"). */
    fun meanAbsError(rows: List<List<Int>>): Double? =
        rows.takeIf { it.isNotEmpty() }?.map { r -> (0..2).sumOf { abs(r[it] - r[it + 3]) } / 3.0 }?.average()

    // ── Android cast ─────────────────────────────────────────────────────────
    private const val PREFS = "WearablePrefs"

    /** Spocita odhad pro dnesek (volat mimo hlavni vlakno) a zapamatuje si ho kvuli mereni presnosti. */
    fun forToday(context: Context, today: LocalDate = LocalDate.now()): Prefill? {
        val db = AppDatabase.getDatabase(context)
        val days = db.wearableDao().getDaysSync(today.minusDays(15).toString(), today.toString()).associateBy { it.date }
        val y = today.minusDays(1)
        val recent = (2L..8L).mapNotNull { days[today.minusDays(it).toString()]?.activeKcal }.takeIf { it.isNotEmpty() }?.average()
        val eaten = db.consumedSnackDao().getConsumedByDateSync(y.toString()).sumOf { it.calories.toDouble() }
        val balance = if (eaten <= 0.0) null
            else eaten - MacroCalculator.calculateForDate(context, Date.from(y.atStartOfDay(ZoneId.systemDefault()).toInstant())).calories
        val p = predict(days[today.toString()], days[y.toString()], recent, balance) ?: return null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("pred_$today", "${p.sleep},${p.energy},${p.hunger}").apply()
        return p
    }

    /** Po ulozeni rituálu: pokud byl dnes odhad, zapise dvojici (odhad, skutecnost). */
    fun record(context: Context, date: String, sleep: Int, energy: Int, hunger: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pred = prefs.getString("pred_$date", null) ?: return
        val log = (prefs.getString("pred_log", "") ?: "").split(';').filter { it.isNotBlank() && !it.startsWith("$date|") }
        prefs.edit().putString("pred_log", (log + "$date|$pred,$sleep,$energy,$hunger").takeLast(60).joinToString(";")).apply()
    }

    fun accuracy(context: Context): Pair<Int, Double>? {
        val rows = (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("pred_log", "") ?: "").split(';')
            .filter { it.contains('|') }.map { r -> r.substringAfter('|').split(',').map { it.toInt() } }
        return meanAbsError(rows)?.let { rows.size to it }
    }
}