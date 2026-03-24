package cz.uhk.macroflow.data

import androidx.room.*
import cz.uhk.macroflow.data.WaterEntity

@Dao
interface WaterDao {

    // Sync verze — volat z Dispatchers.IO
    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_log WHERE date = :date")
    fun getTotalMlForDateSync(date: String): Int

    @Query("SELECT MAX(timestamp) FROM water_log WHERE date = :date")
    fun getLastDrinkTimestamp(date: String): Long?

    // Pro AchievementEngine — vrátí všechny záznamy synchronně
    @Query("SELECT * FROM water_log")
    fun getAllWaterSync(): List<WaterEntity>

    @Insert
    fun insertWater(entry: WaterEntity)
}