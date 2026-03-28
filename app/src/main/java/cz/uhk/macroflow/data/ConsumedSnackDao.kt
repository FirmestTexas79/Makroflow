package cz.uhk.macroflow.data

import androidx.room.*
import cz.uhk.macroflow.data.ConsumedSnackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsumedSnackDao {

    @Query("SELECT * FROM consumed_snacks WHERE date = :date ORDER BY time DESC")
    fun getConsumedByDate(date: String): Flow<List<ConsumedSnackEntity>>

    // Pro AchievementEngine a export pro trenéra — vrátí vše synchronně na Dispatchers.IO
    @Query("SELECT * FROM consumed_snacks")
    fun getAllConsumedSync(): List<ConsumedSnackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertConsumed(snack: ConsumedSnackEntity)

    @Query("DELETE FROM consumed_snacks WHERE timestamp = :timestamp")
    fun deleteConsumedByTimestamp(timestamp: Long)

    @Query("DELETE FROM consumed_snacks") // Toto tam musí být!
    fun deleteAllConsumedLocally()
}