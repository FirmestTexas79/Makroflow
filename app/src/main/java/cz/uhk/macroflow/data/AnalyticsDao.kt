package cz.uhk.macroflow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAnalytics(analytics: AnalyticsCacheEntity)

    // --- DOPLNĚNO PRO SYNC ---
    /**
     * Hromadné vložení dat stažených z cloudu.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(analyticsList: List<AnalyticsCacheEntity>)

    /**
     * Smaže lokální cache před tím, než se zapíšou čerstvá data z cloudu.
     * Tím zajistíme, že v mobilu nezůstanou "duchové" smazaných dat.
     */
    @Query("DELETE FROM analytics_cache")
    fun deleteAllLocally()
    // --------------------------

    @Query("SELECT * FROM analytics_cache ORDER BY date DESC LIMIT 1")
    fun getLatestCacheFlow(): Flow<AnalyticsCacheEntity?>

    @Query("SELECT * FROM analytics_cache ORDER BY date DESC LIMIT 1")
    fun getLatestCacheSync(): AnalyticsCacheEntity?

    @Query("SELECT * FROM analytics_cache WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getAnalyticsRangeSync(startDate: String, endDate: String): List<AnalyticsCacheEntity>

    @Query("DELETE FROM analytics_cache")
    fun clearCache()

    @Query("SELECT * FROM analytics_cache ORDER BY date ASC")
    fun getAllAnalyticsSync(): List<AnalyticsCacheEntity>

    @Query("SELECT * FROM analytics_cache WHERE date = :dateKey LIMIT 1")
    fun getAnalyticsForDateSync(dateKey: String): AnalyticsCacheEntity?
}