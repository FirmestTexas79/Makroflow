package cz.uhk.macroflow.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import cz.uhk.macroflow.training.log.LoggedSet
import kotlinx.coroutines.flow.Flow

/**
 * Zapsaná série z tréninkového deníku (docs/adr/0023, 0024). [exerciseId] = ExerciseLibrary id,
 * [weightKg] 0 = vlastní váha, u jednoruček váha jedné jednoručky, [slowEccentric] = pomalé
 * spouštění, [template] = šablona dne („PUSH_A“), null = zápis mimo šablonu (z atlasu).
 */
@Entity(tableName = "workout_sets", indices = [Index(value = ["exerciseId", "date"]), Index(value = ["date"])])
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,          // yyyy-MM-dd
    val createdAt: Long,       // epoch ms – pořadí sérií v rámci dne
    val exerciseId: String,
    val weightKg: Double,
    val reps: Int,
    val slowEccentric: Boolean = false,
    val template: String? = null
) {
    fun toLogged(order: Int = 0) = LoggedSet(
        id = id, day = java.time.LocalDate.parse(date).toEpochDay().toInt(),
        exerciseId = exerciseId, weightKg = weightKg, reps = reps, order = order,
        slowEccentric = slowEccentric, template = template
    )
}

/** Cvik v šabloně tréninkového dne (PUSH_A …) na pozici [position]. */
@Entity(tableName = "workout_templates", primaryKeys = ["templateKey", "position"])
data class WorkoutTemplateEntity(
    val templateKey: String,
    val position: Int,
    val exerciseId: String
)

@Dao
abstract class WorkoutDao {
    @Insert
    abstract fun insert(set: WorkoutSetEntity): Long

    @Delete
    abstract fun delete(set: WorkoutSetEntity)

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId ORDER BY date ASC, createdAt ASC")
    abstract fun forExercise(exerciseId: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE date >= :from AND date <= :to ORDER BY date ASC, createdAt ASC")
    abstract fun between(from: String, to: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE date >= :from AND date <= :to")
    abstract fun betweenSync(from: String, to: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets ORDER BY date ASC, createdAt ASC")
    abstract fun all(): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets")
    abstract fun getAllSync(): List<WorkoutSetEntity>

    @Query("DELETE FROM workout_sets")
    abstract fun deleteAllLocally()

    // ── Šablony ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM workout_templates WHERE templateKey = :key ORDER BY position ASC")
    abstract fun template(key: String): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates WHERE templateKey = :key ORDER BY position ASC")
    abstract fun templateSync(key: String): List<WorkoutTemplateEntity>

    @Query("SELECT * FROM workout_templates")
    abstract fun allTemplatesSync(): List<WorkoutTemplateEntity>

    @Query("DELETE FROM workout_templates WHERE templateKey = :key")
    abstract fun deleteTemplate(key: String)

    @Insert
    abstract fun insertTemplateRows(rows: List<WorkoutTemplateEntity>)

    @Query("DELETE FROM workout_templates")
    abstract fun deleteAllTemplatesLocally()

    /** Uloží celé pořadí cviků šablony najednou. */
    @Transaction
    open fun replaceTemplate(key: String, exerciseIds: List<String>) {
        deleteTemplate(key)
        insertTemplateRows(exerciseIds.mapIndexed { i, id -> WorkoutTemplateEntity(key, i, id) })
    }
}
