package cz.uhk.macroflow

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMetricsDao {

    @Query("SELECT * FROM body_metrics WHERE date = :date LIMIT 1")
    fun getByDateSync(date: String): BodyMetricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(metrics: BodyMetricsEntity)

    @Query("SELECT * FROM body_metrics ORDER BY date DESC")
    fun getAllFlow(): Flow<List<BodyMetricsEntity>>
}