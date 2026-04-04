package cz.uhk.macroflow.training

import androidx.room.*

/**
 * Tréninkový plán — jeden řádek na den.
 *
 * Lokální úložiště: Room DB (tabulka training_plan_entries)
 * Cloud:            Firestore users/{uid}/data/training_plan
 *                   → { Monday: { type: "push", time: "07:30" }, ... }
 *
 * Pole type je rozšiřitelné — posilování: "push"/"pull"/"legs"/"rest",
 * v budoucnu kardio: "cardio_run"/"cardio_bike" atd.
 */
@Entity(tableName = "training_plan_entries")
data class TrainingPlanEntity(
    @PrimaryKey val day: String,       // "Monday" … "Sunday"
    val type: String  = "rest",        // "push" | "pull" | "legs" | "rest"
    val time: String? = null           // "HH:mm" nebo null = nenastaveno
)

@Dao
interface TrainingPlanDao {

    @Query("SELECT * FROM training_plan_entries")
    fun getAll(): List<TrainingPlanEntity>

    @Query("SELECT * FROM training_plan_entries WHERE day = :day LIMIT 1")
    fun getDay(day: String): TrainingPlanEntity?

    /** Vytvoří nebo přepíše záznam pro daný den (REPLACE). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entry: TrainingPlanEntity)

    /** Nahradí celý plán najednou — používá sync z Firebase. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entries: List<TrainingPlanEntity>)

    @Query("DELETE FROM training_plan_entries")
    fun deleteAll()
}
