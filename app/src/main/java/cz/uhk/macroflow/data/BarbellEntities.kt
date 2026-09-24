package cz.uhk.macroflow.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/** Jedna série sledovaná kamerou (souhrn z RepAnalyzer.SetSummary). */
@Entity(tableName = "barbell_sets", indices = [Index(value = ["date"])])
data class BarbellSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,               // yyyy-MM-dd
    val startedAt: Long,            // epoch ms
    val exercise: String,           // Lift.name
    val setIndex: Int,              // pořadí série daného cviku v daném dni (1..)
    val loadKg: Double?,            // zátěž, pokud ji uživatel zadal
    val plateDiameterCm: Double,
    val repCount: Int,
    val avgRomCm: Double,
    val weightedRomCm: Double,
    val romCvPct: Double,
    val avgEccentricMs: Long,
    val avgConcentricMs: Long,
    val avgTotalMs: Long,
    val bestMcv: Double,
    val lastMcv: Double,
    val velocityLossPct: Double,
    val avgDeviationCm: Double,
    val quality: Double
)

@Entity(tableName = "barbell_reps", indices = [Index(value = ["setId"])])
data class BarbellRepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val setId: Long,
    val repIndex: Int,
    val startCm: Double,
    val turnCm: Double,
    val endCm: Double,
    val romCm: Double,
    val eccentricMs: Long,
    val concentricMs: Long,
    val pauseMs: Long,
    val totalMs: Long,
    val meanVelocity: Double,
    val peakVelocity: Double,
    val deviationCm: Double,
    val quality: Double
)

@Dao
abstract class BarbellDao {
    @Insert
    abstract fun insertSet(set: BarbellSetEntity): Long

    @Insert
    abstract fun insertReps(reps: List<BarbellRepEntity>)

    @Transaction
    open fun insertSetWithReps(set: BarbellSetEntity, reps: List<BarbellRepEntity>): Long {
        val id = insertSet(set)
        insertReps(reps.map { it.copy(setId = id) })
        return id
    }

    @Query("SELECT COUNT(*) FROM barbell_sets WHERE date = :date AND exercise = :exercise")
    abstract fun countSets(date: String, exercise: String): Int

    @Query("SELECT * FROM barbell_sets WHERE date >= :fromDate ORDER BY startedAt ASC")
    abstract fun getSetsSince(fromDate: String): List<BarbellSetEntity>

    @Query("SELECT * FROM barbell_sets WHERE date = :date ORDER BY startedAt ASC")
    abstract fun getSetsForDate(date: String): List<BarbellSetEntity>

    @Query("SELECT * FROM barbell_reps WHERE setId = :setId ORDER BY repIndex ASC")
    abstract fun getReps(setId: Long): List<BarbellRepEntity>

    @Query("DELETE FROM barbell_reps WHERE setId = :setId")
    abstract fun deleteReps(setId: Long)

    @Query("DELETE FROM barbell_sets WHERE id = :setId")
    abstract fun deleteSetOnly(setId: Long)

    @Transaction
    open fun deleteSet(setId: Long) { deleteReps(setId); deleteSetOnly(setId) }
}
