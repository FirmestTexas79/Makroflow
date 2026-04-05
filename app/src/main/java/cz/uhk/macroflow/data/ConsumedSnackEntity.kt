package cz.uhk.macroflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ConsumedSnackEntity — záznam o snědené potravině.
 *
 * mealContext: ukládá kontext jídla v době záznamu, např. "PRE_WORKOUT", "POST_WORKOUT_EARLY"
 * Slouží pro:
 *   - AchievementEngine (achievement za jídlo v ideálním okně)
 *   - HistoryFragment (zobrazení kontextu v historii)
 *   - Budoucí analýzy výkonu vs. načasování jídla
 */
@Entity(tableName = "consumed_snacks")
data class ConsumedSnackEntity(
    @PrimaryKey val timestamp: Long = System.currentTimeMillis(), // 👈 ✅ NOVÝ UNIKÁTNÍ KLÍČ
    val date: String,           // yyyy-MM-dd
    val time: String,           // HH:mm
    val name: String,
    val p: Float,
    val s: Float,
    val t: Float,
    val calories: Int,
    val mealContext: String = "NO_TRAINING",
    val energyKj: Float,
    val fiber: Float
)