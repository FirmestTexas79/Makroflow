package cz.uhk.macroflow.wearable

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import cz.uhk.macroflow.data.AppDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Health Connect jako zdroj dat z hodinek: funguje s čímkoli, co do něj píše (Garmin, Samsung, Fitbit, Polar, Withings, Zepp…).
 * Řádky mají source = "health_connect" a nikdy nepřepíšou den, který už má Garmin (ten je přesnější: stres, Body Battery).
 */
object HealthConnectSource {
    const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    private const val PREFS = "WearablePrefs"
    private const val K_ON = "hc_enabled"
    private const val K_FIRST = "hc_first_sync"

    /** Nejdřív přístupná historie bez zvláštního povolení je 30 dní před prvním povolením. */
    private const val FIRST_DAYS = 30
    private const val NEXT_DAYS = 7

    enum class Status { AVAILABLE, NEEDS_INSTALL, NEEDS_UPDATE }

    private val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )
    val permissions: Set<String> get() = readPermissions

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isEnabled(ctx: Context) = prefs(ctx).getBoolean(K_ON, false)
    fun setEnabled(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(K_ON, on).apply() }

    fun status(ctx: Context): Status = when (HealthConnectClient.getSdkStatus(ctx, PROVIDER_PACKAGE)) {
        HealthConnectClient.SDK_AVAILABLE -> Status.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.NEEDS_UPDATE
        else -> Status.NEEDS_INSTALL
    }

    suspend fun granted(ctx: Context): Set<String> = runCatching {
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()
    }.getOrDefault(emptySet())

    // ── Čas ─────────────────────────────────────────────────────────────────
    private fun offsetAt(i: Instant): ZoneOffset = ZoneId.systemDefault().rules.getOffset(i)
    /** „Lokální“ ms: čas na hodinkách v okamžiku zápisu, vyjádřený jako kdyby byl UTC (stejně jako Garmin *Local). */
    private fun localMs(i: Instant, off: ZoneOffset? = null) = i.toEpochMilli() + (off ?: offsetAt(i)).totalSeconds * 1000L
    private fun localDate(ms: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(ms, 86_400_000L))

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> daily(c: HealthConnectClient, m: AggregateMetric<T>, from: LocalDateTime, to: LocalDateTime): Map<LocalDate, T?> =
        c.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(m) as Set<AggregateMetric<Any>>,
            TimeRangeFilter.between(from, to), Period.ofDays(1))).associate { it.startTime.toLocalDate() to it.result[m] }

    /**
     * Stáhne posledních [days] dní (poprvé 30, pak 7) a uloží je. Volat mimo hlavní vlákno.
     * Vrací počet uložených dní. Bez povolení nebo bez Health Connect nic nedělá.
     */
    suspend fun sync(ctx: Context, days: Int? = null, today: LocalDate = LocalDate.now()): Int {
        if (status(ctx) != Status.AVAILABLE) return 0
        val client = HealthConnectClient.getOrCreate(ctx)
        val ok = client.permissionController.getGrantedPermissions()
        fun can(p: String) = p in ok
        if (ok.none { it in readPermissions }) return 0

        val p = prefs(ctx)
        val span = days ?: if (p.getLong(K_FIRST, 0L) == 0L) FIRST_DAYS else NEXT_DAYS
        val from = today.minusDays(span - 1L)
        val start = from.atStartOfDay(); val end = today.plusDays(1).atStartOfDay()
        val dao = AppDatabase.getDatabase(ctx.applicationContext).wearableDao()
        val garmin = dao.getDaysSync(from.toString(), today.toString()).filter { it.source != HealthConnectMap.SOURCE }.map { it.date }.toSet()

        fun perm(c: kotlin.reflect.KClass<out androidx.health.connect.client.records.Record>) = can(HealthPermission.getReadPermission(c))
        val steps = if (perm(StepsRecord::class)) daily(client, StepsRecord.COUNT_TOTAL, start, end) else emptyMap()
        val active = if (perm(ActiveCaloriesBurnedRecord::class)) daily(client, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, start, end) else emptyMap()
        val total = if (perm(TotalCaloriesBurnedRecord::class)) daily(client, TotalCaloriesBurnedRecord.ENERGY_TOTAL, start, end) else emptyMap()
        val rest = if (perm(RestingHeartRateRecord::class)) daily(client, RestingHeartRateRecord.BPM_AVG, start, end) else emptyMap()

        // Spánek: relace, které skončily v rozsahu; noc patří dni probuzení
        val nights = HashMap<LocalDate, HcSleepSession>()
        if (perm(SleepSessionRecord::class)) {
            val bySleepDay = HashMap<LocalDate, MutableList<HcSleepSession>>()
            var token: String? = null
            do {
                val r = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class,
                    TimeRangeFilter.between(start.minusHours(12), end), pageSize = 500, pageToken = token))
                r.records.forEach { rec ->
                    val s = HcSleepSession(localMs(rec.startTime, rec.startZoneOffset), localMs(rec.endTime, rec.endZoneOffset),
                        rec.stages.map { HcStage(it.stage, localMs(it.startTime, rec.startZoneOffset), localMs(it.endTime, rec.startZoneOffset)) })
                    bySleepDay.getOrPut(localDate(s.endMs)) { mutableListOf() }.add(s)
                }
                token = r.pageToken?.takeIf { it.isNotEmpty() }
            } while (token != null)
            bySleepDay.forEach { (d, l) -> if (d in from..today) HealthConnectMap.nightOf(l)?.let { nights[d] = it } }
        }

        val now = System.currentTimeMillis()
        val dates = (steps.keys + active.keys + total.keys + rest.keys + nights.keys).filter { it in from..today && it.toString() !in garmin }.sorted()
        val rows = dates.map { d ->
            val night = nights[d]?.let { HealthConnectMap.sleep(it) }
            val sleepHr = if (night != null && can(HealthPermission.getReadPermission(HeartRateRecord::class))) runCatching {
                // Tep ve spánku: průměr přes okno noci (lokální ms zpět na okamžiky v systémové zóně)
                val s = Instant.ofEpochMilli(night.startMs - offsetAt(Instant.ofEpochMilli(night.startMs)).totalSeconds * 1000L)
                val e = Instant.ofEpochMilli(night.endMs - offsetAt(Instant.ofEpochMilli(night.endMs)).totalSeconds * 1000L)
                client.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG), TimeRangeFilter.between(s, e)))[HeartRateRecord.BPM_AVG]?.toDouble()
            }.getOrNull() else null
            WearableDayEntity(
                date = d.toString(), source = HealthConnectMap.SOURCE,
                totalKcal = total[d]?.inKilocalories?.toInt(), activeKcal = active[d]?.inKilocalories?.toInt(),
                restingKcal = total[d]?.inKilocalories?.let { t -> active[d]?.inKilocalories?.let { a -> (t - a).toInt() } },
                steps = steps[d]?.toInt(), restingHr = rest[d]?.toInt(),
                sleepSec = night?.sleepSec, deepSec = night?.deepSec, lightSec = night?.lightSec, remSec = night?.remSec,
                awakeSec = night?.awakeSec, sleepStartMs = night?.startMs, sleepEndMs = night?.endMs,
                sleepScore = night?.score, awakeCount = night?.awakeCount, sleepAvgHr = sleepHr, syncedAt = now)
        }
        dao.upsertDays(rows)

        if (perm(ExerciseSessionRecord::class)) {
            val acts = ArrayList<WearableActivityEntity>()
            var token: String? = null
            do {
                val r = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(start, end), pageSize = 200, pageToken = token))
                r.records.forEach { rec ->
                    val local = LocalDateTime.ofEpochSecond(localMs(rec.startTime, rec.startZoneOffset) / 1000, 0, ZoneOffset.UTC)
                    if (local.toLocalDate().toString() in garmin) return@forEach
                    val win = TimeRangeFilter.between(rec.startTime, rec.endTime)
                    val kcal = runCatching { client.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), win))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toInt() }.getOrNull()
                    val hr = if (can(HealthPermission.getReadPermission(HeartRateRecord::class))) runCatching {
                        client.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX), win))
                    }.getOrNull() else null
                    acts += WearableActivityEntity(
                        id = (1L shl 50) or ("hc:" + rec.metadata.id).hashCode().toLong().and(0xFFFFFFFFL),
                        date = local.toLocalDate().toString(), startLocal = local.toString().replace('T', ' '),
                        typeKey = typeKey(rec.exerciseType), name = rec.title.orEmpty(), kcal = kcal,
                        durationSec = (rec.endTime.epochSecond - rec.startTime.epochSecond).toInt(),
                        avgHr = hr?.get(HeartRateRecord.BPM_AVG)?.toInt(), maxHr = hr?.get(HeartRateRecord.BPM_MAX)?.toInt())
                }
                token = r.pageToken?.takeIf { it.isNotEmpty() }
            } while (token != null)
            dao.upsertActivities(acts)
        }

        p.edit().putLong(K_FIRST, now).apply()
        return rows.size
    }

    /** Typ cvičení do klíče, který používá zbytek aplikace (shodný s Garminem). */
    private fun typeKey(t: Int) = when (t) {
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "strength_training"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "running"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "walking"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "cycling"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "hiking"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "hiit"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "swimming"
        else -> "other"
    }

    /** Co z hodinek skutečně dorazilo za posledních 14 dní. Čte jen naši databázi. */
    fun summary(ctx: Context, today: LocalDate = LocalDate.now()): HealthConnectMap.Summary {
        val dao = AppDatabase.getDatabase(ctx.applicationContext).wearableDao()
        val from = today.minusDays(13).toString()
        val acts = dao.getActivitiesBetweenSync(from, today.toString()).count { it.id and (1L shl 50) != 0L }
        return HealthConnectMap.summary(dao.getDaysSync(from, today.toString()), acts)
    }
}
