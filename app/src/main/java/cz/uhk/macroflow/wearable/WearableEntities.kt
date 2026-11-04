package cz.uhk.macroflow.wearable

import androidx.room.*

/** Jeden den z hodinek (Garmin Personal API nebo Health Connect). Vse nullable = bez hodinek zadna data. */
@Entity(tableName = "wearable_day")
data class WearableDayEntity(
    @PrimaryKey val date: String,           // yyyy-MM-dd (u spanku = den probuzeni)
    val source: String = "garmin",
    val totalKcal: Int? = null,
    val activeKcal: Int? = null,
    val restingKcal: Int? = null,
    val steps: Int? = null,
    val restingHr: Int? = null,
    val stressAvg: Int? = null,             // -1 od Garminu = hodinky nenoseny
    val bbMin: Int? = null,
    val bbMax: Int? = null,
    val bbSleepGain: Int? = null,           // o kolik spanek nabil Body Battery
    val sleepSec: Int? = null,
    val deepSec: Int? = null,
    val lightSec: Int? = null,
    val remSec: Int? = null,
    val awakeSec: Int? = null,
    val sleepStartMs: Long? = null,         // lokalni cas v ms (jako Garmin *Local)
    val sleepEndMs: Long? = null,
    val sleepScore: Int? = null,
    val awakeCount: Int? = null,
    val avgSpo2: Double? = null,
    val lowSpo2: Int? = null,
    val avgResp: Double? = null,
    val sleepStress: Double? = null,
    val sleepAvgHr: Double? = null,
    val syncedAt: Long = 0L
)

@Entity(tableName = "wearable_activity")
data class WearableActivityEntity(
    @PrimaryKey val id: Long,
    val date: String,
    val startLocal: String,
    val typeKey: String,
    val name: String,
    val kcal: Int?,
    val durationSec: Int,
    val avgHr: Int?,
    val maxHr: Int?
)

@Dao
interface WearableDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertDays(days: List<WearableDayEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertActivities(a: List<WearableActivityEntity>)
    @Query("SELECT * FROM wearable_day WHERE date = :date") fun getDaySync(date: String): WearableDayEntity?
    @Query("SELECT * FROM wearable_day WHERE date = :date") fun dayFlow(date: String): kotlinx.coroutines.flow.Flow<WearableDayEntity?>
    @Query("SELECT * FROM wearable_day WHERE date BETWEEN :from AND :to ORDER BY date") fun getDaysSync(from: String, to: String): List<WearableDayEntity>
    @Query("SELECT * FROM wearable_activity WHERE date = :date ORDER BY startLocal") fun getActivitiesSync(date: String): List<WearableActivityEntity>
}