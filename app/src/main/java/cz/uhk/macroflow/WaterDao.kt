package cz.uhk.macroflow

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {

    // Flow verze — pro live observování z DashboardFragment
    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_log WHERE date = :date")
    fun getTotalMlForDate(date: String): Flow<Int>

    // Sync verze bez suspend — volat pouze z Dispatchers.IO pomocí withContext
    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_log WHERE date = :date")
    fun getTotalMlForDateSync(date: String): Int

    @Query("SELECT MAX(timestamp) FROM water_log WHERE date = :date")
    fun getLastDrinkTimestamp(date: String): Long?

    // Insert bez suspend — volat pouze z Dispatchers.IO
    @Insert
    fun insertWater(entry: WaterEntity)
}