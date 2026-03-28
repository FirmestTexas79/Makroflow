package cz.uhk.macroflow.data

import androidx.room.*

@Dao
interface WaterDao {

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_log WHERE date = :date")
    fun getTotalMlForDateSync(date: String): Int

    @Query("SELECT MAX(timestamp) FROM water_log WHERE date = :date")
    fun getLastDrinkTimestamp(date: String): Long?

    @Query("SELECT * FROM water_log")
    fun getAllWaterSync(): List<WaterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWater(entry: WaterEntity)

    @Query("DELETE FROM water_log")
    fun deleteAllWaterLocally()
}