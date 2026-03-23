package cz.uhk.macroflow

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
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,           // yyyy-MM-dd
    val time: String,           // HH:mm — čas záznamu
    val name: String,
    val p: Float,
    val s: Float,
    val t: Float,
    val calories: Int,
    val mealContext: String = "NO_TRAINING"  // TrainingTimeManager.MealContext.name()
)