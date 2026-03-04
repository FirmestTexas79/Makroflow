package cz.uhk.macroflow

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Query("SELECT * FROM checkins ORDER BY date DESC")
    fun getAllCheckInsSync(): List<CheckInEntity> // Přidali jsme Sync verzi pro thread

    @Query("SELECT * FROM checkins WHERE date = :date LIMIT 1")
    fun getCheckInByDateSync(date: String): CheckInEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCheckIn(checkIn: CheckInEntity)
}