package cz.uhk.macroflow.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Denní snapshot adaptivního odhadu výdeje (fáze B).
 * Historie odhadů je záměrně uložená – dá se z ní vyhodnotit, jak rychle
 * a jak stabilně odhad konverguje (podklad pro vyhodnocení v diplomové práci).
 *
 * Data jsou odvozená (z check-inů, jídel a kroků), proto se nesynchronizují
 * do Firestore – na jiném zařízení se přepočítají ze synchronizovaných vstupů.
 */
@Entity(tableName = "adaptive_tdee")
data class AdaptiveTdeeEntity(
    @PrimaryKey val date: String,           // yyyy-MM-dd
    val status: String,                     // AdaptiveExpenditure.Status.name
    val factor: Double,                     // k, násobí výdej modelu
    val modelTdee: Double,
    val observedTdee: Double?,
    val adaptiveTdee: Double,
    val confidence: Double,
    val trendWeightKg: Double?,
    val weightChangeKgPerWeek: Double?,
    val weighIns: Int,
    val loggedDays: Int
)

@Dao
interface AdaptiveTdeeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: AdaptiveTdeeEntity)

    @Query("SELECT * FROM adaptive_tdee ORDER BY date DESC LIMIT 1")
    fun getLatestSync(): AdaptiveTdeeEntity?

    /** Poslední odhad platný k danému dni (pro historii nepoužívat „budoucí“ odhady). */
    @Query("SELECT * FROM adaptive_tdee WHERE date <= :date ORDER BY date DESC LIMIT 1")
    fun getLatestOnOrBeforeSync(date: String): AdaptiveTdeeEntity?

    @Query("SELECT * FROM adaptive_tdee ORDER BY date ASC")
    fun getAllSync(): List<AdaptiveTdeeEntity>
}
