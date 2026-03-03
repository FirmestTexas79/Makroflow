package cz.uhk.macroflow

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SnackDao {
    @Query("SELECT * FROM snacks")
    fun getAllSnacks(): Flow<List<SnackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSnack(snack: SnackEntity)

    @Delete
    fun deleteSnack(snack: SnackEntity)
}