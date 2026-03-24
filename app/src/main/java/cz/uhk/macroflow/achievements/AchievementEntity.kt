package cz.uhk.macroflow.achievements

import androidx.room.*

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Long = System.currentTimeMillis()
)

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements")
    fun getAllUnlocked(): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE id = :id LIMIT 1")
    fun getById(id: String): AchievementEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun unlock(achievement: AchievementEntity): Long  // vrátí -1 pokud už existuje

    @Query("DELETE FROM achievements")
    fun deleteAll()
}