package cz.uhk.macroflow.data

import androidx.room.*
import cz.uhk.macroflow.data.SnackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SnackDao {
    @Query("SELECT * FROM snacks")
    fun getAllSnacks(): Flow<List<SnackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSnack(snack: SnackEntity)

    @Delete
    fun deleteSnack(snack: SnackEntity)

    // Shadow tabulka - SMART SORTING
    @Query("""
        SELECT s.* FROM snacks s
        LEFT JOIN snack_usage_metadata m ON s.name = m.snackName
        ORDER BY (CAST(m.usageCount AS REAL) * 1000000000 / (:now - m.lastUsedTimestamp + 86400000)) DESC, s.name ASC
    """)
    fun getAllSnacksSmart(now: Long): Flow<List<SnackEntity>>

    // TADY BYLA CHYBA: Aby to KSP skouslo bez suspend,
    // musí to vracet Long (ID vloženého řádku), ne Unit.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun updateUsageMetadata(usage: SnackUsageEntity): Long

    @Query("SELECT * FROM snack_usage_metadata WHERE snackName = :name LIMIT 1")
    fun getUsageStats(name: String): SnackUsageEntity?
}