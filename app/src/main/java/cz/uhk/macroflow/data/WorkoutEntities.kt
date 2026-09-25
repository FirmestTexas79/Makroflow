package cz.uhk.macroflow.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import cz.uhk.macroflow.training.log.LoggedSet
import kotlinx.coroutines.flow.Flow

/**
 * Zapsaná série z tréninkového deníku (docs/adr/0023). [exerciseId] = ExerciseLibrary id,
 * [weightKg] 0 = vlastní váha, u jednoruček váha jedné jednoručky.
 */
@Entity(tableName = "workout_sets", indices = [Index(value = ["exerciseId", "date"]), Index(value = ["date"])])
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,          // yyyy-MM-dd
    val createdAt: Long,       // epoch ms – pořadí sérií v rámci dne
    val exerciseId: String,
    val weightKg: Double,
    val reps: Int
) {
    fun toLogged(order: Int = 0) = LoggedSet(
        id = id, day = java.time.LocalDate.parse(date).toEpochDay().toInt(),
        exerciseId = exerciseId, weightKg = weightKg, reps = reps, order = order
    )
}

@Dao
interface WorkoutDao {
    @Insert
    fun insert(set: WorkoutSetEntity): Long

    @Delete
    fun delete(set: WorkoutSetEntity)

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId ORDER BY date ASC, createdAt ASC")
    fun forExercise(exerciseId: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE date >= :from AND date <= :to ORDER BY date ASC, createdAt ASC")
    fun between(from: String, to: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE date >= :from AND date <= :to")
    fun betweenSync(from: String, to: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets")
    fun getAllSync(): List<WorkoutSetEntity>

    @Query("DELETE FROM workout_sets")
    fun deleteAllLocally()
}
