package cz.uhk.macroflow

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsumedSnackDao {
    @Query("SELECT * FROM consumed_snacks WHERE date = :date")
    fun getConsumedByDate(date: String): Flow<List<ConsumedSnackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertConsumed(snack: ConsumedSnackEntity)

    @Delete
    fun deleteConsumed(snack: ConsumedSnackEntity)
}