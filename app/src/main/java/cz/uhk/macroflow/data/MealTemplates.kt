package cz.uhk.macroflow.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Uložené jídlo nebo celý den (docs/adr/0027). [kind] = „MEAL“ / „DAY“, [items] = položky
 * zakódované MealRepeat.encode, [createdAt] = klíč i ve Firestore.
 */
@Entity(tableName = "meal_templates")
data class MealTemplateEntity(
    @PrimaryKey val createdAt: Long,
    val name: String,
    val kind: String,
    val items: String,
    val lastUsedAt: Long = 0
)

@Dao
interface MealTemplateDao {
    @Query("SELECT * FROM meal_templates ORDER BY lastUsedAt DESC, createdAt DESC")
    fun all(): Flow<List<MealTemplateEntity>>

    @Query("SELECT * FROM meal_templates")
    fun allSync(): List<MealTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(t: MealTemplateEntity)

    @Query("UPDATE meal_templates SET lastUsedAt = :at WHERE createdAt = :id")
    fun markUsed(id: Long, at: Long)

    @Query("DELETE FROM meal_templates WHERE createdAt = :id")
    fun delete(id: Long)

    @Query("DELETE FROM meal_templates")
    fun deleteAllLocally()
}
