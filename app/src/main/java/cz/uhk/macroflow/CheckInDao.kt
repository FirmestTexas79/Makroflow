package cz.uhk.macroflow

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Query("SELECT * FROM checkins ORDER BY date DESC")
    fun getAllCheckIns(): Flow<List<CheckInEntity>> // Styl Flow jako u SnackDao [cite: 2026-03-01]

    @Query("SELECT * FROM checkins WHERE date = :date LIMIT 1")
    fun getCheckInByDate(date: String): CheckInEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCheckIn(checkIn: CheckInEntity)

    @Delete
    fun deleteCheckIn(checkIn: CheckInEntity)
}