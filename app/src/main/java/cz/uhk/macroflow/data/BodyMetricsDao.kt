package cz.uhk.macroflow.data

import androidx.room.*
import cz.uhk.macroflow.data.BodyMetricsEntity

@Dao
interface BodyMetricsDao {

    @Query("SELECT * FROM body_metrics WHERE date = :date LIMIT 1")
    fun getByDateSync(date: String): BodyMetricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(metrics: BodyMetricsEntity)


    @Query("SELECT * FROM body_metrics")
    fun getAllSync(): List<BodyMetricsEntity>

    @Query("SELECT * FROM body_metrics ORDER BY id DESC LIMIT 1")
    fun getLastMetricsSync(): BodyMetricsEntity?

    @Query("DELETE FROM body_metrics")
    fun deleteAllLocally()
}