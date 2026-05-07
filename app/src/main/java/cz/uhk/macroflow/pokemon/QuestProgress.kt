package cz.uhk.macroflow.pokemon

import androidx.room.*

@Entity(tableName = "quest_progress")
data class QuestProgressEntity(
    @PrimaryKey val questId: String,       // Unikátní ID (např. "town_intro_oliver")
    val currentStageIndex: Int = 0,        // Aktuální krok v poli questu
    val isCompleted: Boolean = false,
    val metadata: String = "",             // Pro flexibilní data (např. "visited:domov,obchod")
    val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface QuestDao {
    @Query("SELECT * FROM quest_progress")
    fun getAllQuests(): List<QuestProgressEntity> // Odstraněn suspend

    @Query("SELECT * FROM quest_progress WHERE questId = :id LIMIT 1")
    fun getQuestById(id: String): QuestProgressEntity? // Odstraněn suspend

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveQuestProgress(progress: QuestProgressEntity) // Odstraněn suspend i Unit

    @Query("DELETE FROM quest_progress")
    fun deleteAllLocally() // Odstraněn suspend i Unit
}