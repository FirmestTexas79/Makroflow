package cz.uhk.macroflow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_log WHERE date = :date")
    fun getTotalMlForDateSync(date: String): Int

    /** Vypitá voda dne jako Flow – quest Hvozdu se přepočítá hned po zápisu (docs/adr/0045). */
    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_log WHERE date = :date")
    fun observeTotalMlForDate(date: String): Flow<Int>

    @Query("SELECT MAX(timestamp) FROM water_log WHERE date = :date")
    fun getLastDrinkTimestamp(date: String): Long?

    @Query("SELECT * FROM water_log")
    fun getAllWaterSync(): List<WaterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWater(entry: WaterEntity)

    @Query("DELETE FROM water_log")
    fun deleteAllWaterLocally()
}