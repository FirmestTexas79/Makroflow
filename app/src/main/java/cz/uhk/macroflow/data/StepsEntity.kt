package cz.uhk.macroflow.data

import androidx.room.*

@Entity(tableName = "steps")
data class StepsEntity(
    @PrimaryKey val date: String,
    val count: Int
)

@Dao
interface StepsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSteps(steps: StepsEntity) // 👈 Bez suspend! Stejně jako SnackDao

    @Query("SELECT * FROM steps WHERE date = :date")
    fun getStepsForDateSync(date: String): StepsEntity? // 👈 Bez suspend!

    @Query("SELECT SUM(count) FROM steps")
    fun getTotalStepsAllTimeFlow(): kotlinx.coroutines.flow.Flow<Int?>

    @Query("SELECT * FROM steps WHERE date = :date")
    fun getStepsForDateFlow(date: String): kotlinx.coroutines.flow.Flow<StepsEntity?>

    @Query("DELETE FROM steps")
    fun deleteAll(): Int // 👈 Bez suspend!

    @Query("SELECT * FROM steps")
    fun getAllStepsSync(): List<StepsEntity>
}