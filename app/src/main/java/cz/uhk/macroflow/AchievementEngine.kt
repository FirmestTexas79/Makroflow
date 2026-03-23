package cz.uhk.macroflow

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.exp

/**
 * AchievementEngine — precizní kontrola podmínek achievementů.
 *
 * Každé kritérium je explicitně definované — žádné mezery:
 *
 * VODA: den splněn = SUM(ml za den) >= cíl z MacroCalculator
 *       (ne jen libovolný součet záznamů)
 *
 * MAKRA: den splněn = sněděné makro >= 90% cíle z MacroCalculator
 *        pro DANÝ den (zohledňuje typ tréninku ten den)
 *
 * STREAK: přísně po sobě jdoucí kalendářní dny — žádná mezera
 *
 * CHECK-IN: počítáme záznamy kde datum existuje v tabulce checkins
 *           (ne duplicity — CheckInDao používá REPLACE, takže max 1/den)
 *
 * COINY: při každém novém odemčení se automaticky přidají coiny dle tieru:
 *   BRONZE  = 1 coin
 *   SILVER  = 3 coins
 *   GOLD    = 10 coins
 *   DIAMOND = 50 coins
 */
object AchievementEngine {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Hodnoty coinů za každý tier
    fun coinsForTier(tier: AchievementTier): Int = when (tier) {
        AchievementTier.BRONZE  -> 1
        AchievementTier.SILVER  -> 3
        AchievementTier.GOLD    -> 10
        AchievementTier.DIAMOND -> 50
    }

    // ── Veřejné API ───────────────────────────────────────────────────
    fun checkAll(context: Context): List<AchievementDef> {
        val db = AppDatabase.getDatabase(context)
        val result = mutableListOf<AchievementDef>()

        result += checkStreaks(db)
        result += checkMacros(context, db)
        result += checkWater(context, db)
        result += checkCheckIns(db)
        result += checkSymmetry(db)
        result += checkWeight(db)
        result += checkVariety(db)
        result += checkMilestones(context, db)

        return result
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Pokus o odemčení — vrátí def pouze pokud bylo nově odemčeno.
     * Při odemčení automaticky přidá coiny dle tieru achievementu.
     */
    private fun tryUnlock(db: AppDatabase, id: String): AchievementDef? {
        val rowId = db.achievementDao().unlock(AchievementEntity(id))
        if (rowId == -1L) return null  // už bylo odemčeno dříve

        val def = AchievementRegistry.findById(id) ?: return null

        // Přidej coiny za nově odemčený achievement
        val coins = coinsForTier(def.tier)
        db.coinDao().addCoins(coins)
        android.util.Log.d("AchievementEngine", "Unlocked ${def.titleCs} → +$coins coins")

        return def
    }

    /**
     * Vypočítá kalendářní streak — přísně po sobě jdoucí dny.
     * Přijme seznam datumů (yyyy-MM-dd), vrátí délku aktuálního streaku.
     */
    private fun calcStreak(dates: List<String>): Int {
        if (dates.isEmpty()) return 0

        val sorted = dates.distinct().sorted()  // unikátní dny, vzestupně
        val cal = Calendar.getInstance()

        // Začni od dneška nebo včerejška (pokud dnes ještě není záznam)
        val today = sdf.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = sdf.format(cal.time)

        val startDate = when {
            sorted.last() == today     -> today
            sorted.last() == yesterday -> yesterday
            else                       -> return 0  // poslední záznam starší než včera = streak přerušen
        }

        // Počítej zpětně
        var streak = 0
        val checkCal = Calendar.getInstance()
        checkCal.time = sdf.parse(startDate) ?: return 0

        while (true) {
            val expected = sdf.format(checkCal.time)
            if (expected in sorted) {
                streak++
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
            } else break
        }
        return streak
    }

    // ── ZÁPISOVÝ STREAK ───────────────────────────────────────────────
    private fun checkStreaks(db: AppDatabase): List<AchievementDef> {
        val result = mutableListOf<AchievementDef>()

        // CheckIn má max 1 záznam/den díky PrimaryKey = date
        val checkInDates = db.checkInDao().getAllCheckInsSync().map { it.date }
        val streak = calcStreak(checkInDates)

        if (streak >= 3)   tryUnlock(db, "streak_3")?.let   { result += it }
        if (streak >= 10)  tryUnlock(db, "streak_10")?.let  { result += it }
        if (streak >= 40)  tryUnlock(db, "streak_40")?.let  { result += it }
        if (streak >= 100) tryUnlock(db, "streak_100")?.let { result += it }

        return result
    }

    // ── MAKRA ─────────────────────────────────────────────────────────
    private fun checkMacros(context: Context, db: AppDatabase): List<AchievementDef> {
        val result = mutableListOf<AchievementDef>()

        val allConsumed = db.consumedSnackDao().getAllConsumedSync()
        if (allConsumed.isEmpty()) return result

        val byDate = allConsumed.groupBy { it.date }

        data class DayResult(
            val date: String,
            val proteinOk: Boolean,
            val carbsOk: Boolean,
            val fatOk: Boolean
        )

        val dayResults = byDate.map { (dateStr, items) ->
            val date = sdf.parse(dateStr) ?: return@map null
            val target = MacroCalculator.calculateForDate(context, date)

            if (target.protein <= 0 || target.carbs <= 0 || target.fat <= 0)
                return@map null

            val eatP = items.sumOf { it.p.toDouble() }
            val eatS = items.sumOf { it.s.toDouble() }
            val eatT = items.sumOf { it.t.toDouble() }

            DayResult(
                date      = dateStr,
                proteinOk = eatP >= target.protein * 0.90,
                carbsOk   = eatS >= target.carbs   * 0.90,
                fatOk     = eatT >= target.fat     * 0.90
            )
        }.filterNotNull()

        val proteinTotal = dayResults.count { it.proteinOk }
        val carbsTotal   = dayResults.count { it.carbsOk }
        val fatTotal     = dayResults.count { it.fatOk }

        val proteinStreak = calcStreak(dayResults.filter { it.proteinOk }.map { it.date })
        val carbsStreak   = calcStreak(dayResults.filter { it.carbsOk }.map { it.date })
        val fatStreak     = calcStreak(dayResults.filter { it.fatOk }.map { it.date })

        if (proteinStreak >= 3)   tryUnlock(db, "protein_bronze")?.let  { result += it }
        if (proteinStreak >= 10)  tryUnlock(db, "protein_silver")?.let  { result += it }
        if (proteinTotal  >= 40)  tryUnlock(db, "protein_gold")?.let    { result += it }
        if (proteinTotal  >= 100) tryUnlock(db, "protein_diamond")?.let { result += it }

        if (carbsStreak >= 3)   tryUnlock(db, "carbs_bronze")?.let  { result += it }
        if (carbsStreak >= 10)  tryUnlock(db, "carbs_silver")?.let  { result += it }
        if (carbsTotal  >= 40)  tryUnlock(db, "carbs_gold")?.let    { result += it }
        if (carbsTotal  >= 100) tryUnlock(db, "carbs_diamond")?.let { result += it }

        if (fatStreak >= 3)   tryUnlock(db, "fat_bronze")?.let  { result += it }
        if (fatStreak >= 10)  tryUnlock(db, "fat_silver")?.let  { result += it }
        if (fatTotal  >= 40)  tryUnlock(db, "fat_gold")?.let    { result += it }
        if (fatTotal  >= 100) tryUnlock(db, "fat_diamond")?.let { result += it }

        return result
    }

    // ── VODA ──────────────────────────────────────────────────────────
    private fun checkWater(context: Context, db: AppDatabase): List<AchievementDef> {
        val result = mutableListOf<AchievementDef>()

        val allWater = db.waterDao().getAllWaterSync()
        if (allWater.isEmpty()) return result

        val byDate = allWater.groupBy { it.date }
        val splneneDny  = mutableListOf<String>()
        var totalSplneno = 0

        for ((dateStr, items) in byDate) {
            val date   = sdf.parse(dateStr) ?: continue
            val target = MacroCalculator.calculateForDate(context, date)
            val goalMl = (target.water * 1000).toInt()
            if (goalMl <= 0) continue

            val actualMl = items.sumOf { it.amountMl }

            if (actualMl >= goalMl) {
                splneneDny += dateStr
                totalSplneno++
            }
        }

        val streak = calcStreak(splneneDny)

        if (streak >= 3)         tryUnlock(db, "water_bronze")?.let  { result += it }
        if (streak >= 10)        tryUnlock(db, "water_silver")?.let  { result += it }
        if (totalSplneno >= 40)  tryUnlock(db, "water_gold")?.let    { result += it }
        if (totalSplneno >= 100) tryUnlock(db, "water_diamond")?.let { result += it }

        return result
    }

    // ── CHECK-INY ────────────────────────────────────────────────────
    private fun checkCheckIns(db: AppDatabase): List<AchievementDef> {
        val result = mutableListOf<AchievementDef>()
        val all = db.checkInDao().getAllCheckInsSync()
        val total = all.size

        if (total >= 1)   tryUnlock(db, "checkin_bronze")?.let  { result += it }
        if (total >= 10)  tryUnlock(db, "checkin_silver")?.let  { result += it }
        if (total >= 40)  tryUnlock(db, "checkin_gold")?.let    { result += it }
        if (total >= 100) tryUnlock(db, "checkin_diamond")?.let { result += it }

        return result
    }

    // ── SYMETRIE ─────────────────────────────────────────────────────
    private fun checkSymmetry(db: AppDatabase): List<AchievementDef> {
        val result = mutableListOf<AchievementDef>()
        val allMetrics = db.bodyMetricsDao().getAllSync()

        if (allMetrics.isEmpty()) return result
        tryUnlock(db, "symmetry_bronze")?.let { result += it }

        if (allMetrics.size < 2) return result

        val first  = allMetrics.minByOrNull { it.date }!!
        val latest = allMetrics.maxByOrNull { it.date }!!

        val firstScore  = calcSymmetryScore(first)
        val latestScore = calcSymmetryScore(latest)

        if (latestScore > 0f && (latestScore - firstScore) >= 0.05f)
            tryUnlock(db, "symmetry_silver")?.let { result += it }
        if (latestScore >= 0.80f)
            tryUnlock(db, "symmetry_gold")?.let { result += it }
        if (latestScore >= 0.90f)
            tryUnlock(db, "symmetry_diamond")?.let { result += it }

        return result
    }

    private fun calcSymmetryScore(m: BodyMetricsEntity): Float {
        if (m.neck <= 0f) return 0f
        val neck = m.neck
        val w = neck * 0.406f
        val scores = listOfNotNull(
            if (m.chest  > 0f) asymScore(m.chest,  neck * 2.87f, 0.09f, 0.15f) else null,
            if (m.bicep  > 0f) asymScore(m.bicep,  w * 2.50f,    0.10f, 0.18f) else null,
            if (m.thigh  > 0f) asymScore(m.thigh,  neck * 1.70f, 0.10f, 0.16f) else null,
            if (m.calf   > 0f) asymScore(m.calf,   w * 2.50f,    0.10f, 0.16f) else null
        )
        return if (scores.isEmpty()) 0f else scores.average().toFloat()
    }

    private fun asymScore(actual: Float, ideal: Float, tL: Float, tH: Float): Float {
        if (ideal <= 0f) return 0.5f
        val r = actual / ideal
        val t = if (r < 1f) tL else tH
        return exp(-((r - 1.0) * (r - 1.0)) / (2.0 * t * t)).toFloat().coerceIn(0.1f, 1f)
    }

    // ── VÁHA ──────────────────────────────────────────────────────────
    private fun checkWeight(db: AppDatabase): List<AchievementDef> {
        val result = mutableListOf<AchievementDef>()
        val all = db.checkInDao().getAllCheckInsSync().sortedBy { it.date }
        if (all.isEmpty()) return result

        tryUnlock(db, "weight_bronze")?.let { result += it }

        if (all.size >= 10) tryUnlock(db, "weight_silver")?.let { result += it }

        val firstWeight  = all.first().weight
        val latestWeight = all.last().weight
        val change       = abs(firstWeight - latestWeight)

        if (change >= 2.0)  tryUnlock(db, "weight_gold")?.let    { result += it }
        if (change >= 5.0)  tryUnlock(db, "weight_diamond")?.let { result += it }

        return result
    }

    // ── ROZMANITOST ───────────────────────────────────────────────────
    private fun checkVariety(db: AppDatabase): List<AchievementDef> {
        val result = mutableListOf<AchievementDef>()
        val uniqueFoods = db.consumedSnackDao().getAllConsumedSync()
            .map { it.name.trim().lowercase() }
            .toSet().size

        if (uniqueFoods >= 5)   tryUnlock(db, "variety_bronze")?.let  { result += it }
        if (uniqueFoods >= 15)  tryUnlock(db, "variety_silver")?.let  { result += it }
        if (uniqueFoods >= 40)  tryUnlock(db, "variety_gold")?.let    { result += it }
        if (uniqueFoods >= 100) tryUnlock(db, "variety_diamond")?.let { result += it }

        return result
    }

    // ── MILNÍKY ───────────────────────────────────────────────────────
    private fun checkMilestones(context: Context, db: AppDatabase): List<AchievementDef> {
        val result = mutableListOf<AchievementDef>()

        tryUnlock(db, "milestone_first")?.let { result += it }

        val checkInDates = db.checkInDao().getAllCheckInsSync().map { it.date }.toSet()
        val daysWithData = checkInDates.size

        if (daysWithData >= 3)  tryUnlock(db, "milestone_week")?.let  { result += it }
        if (daysWithData >= 10) tryUnlock(db, "milestone_month")?.let { result += it }

        checkPerfectWeek(context, db)?.let { result += it }

        return result
    }

    private fun checkPerfectWeek(context: Context, db: AppDatabase): AchievementDef? {
        val cal = Calendar.getInstance()
        val allConsumed = db.consumedSnackDao().getAllConsumedSync().groupBy { it.date }
        val allWater    = db.waterDao().getAllWaterSync().groupBy { it.date }

        for (i in 0..6) {
            val dateStr = sdf.format(cal.time)
            val date    = cal.time

            val checkIn = db.checkInDao().getCheckInByDateSync(dateStr)
                ?: return null

            val target = MacroCalculator.calculateForDate(context, date)
            val items  = allConsumed[dateStr]
            if (items == null || target.protein <= 0) return null

            val eatP = items.sumOf { it.p.toDouble() }
            val eatS = items.sumOf { it.s.toDouble() }
            val eatT = items.sumOf { it.t.toDouble() }

            val macrosOk = eatP >= target.protein * 0.90 &&
                    eatS >= target.carbs   * 0.90 &&
                    eatT >= target.fat     * 0.90
            if (!macrosOk) return null

            val goalMl   = (target.water * 1000).toInt()
            val actualMl = allWater[dateStr]?.sumOf { it.amountMl } ?: 0
            if (actualMl < goalMl) return null

            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        return tryUnlock(db, "milestone_perfect")
    }
}