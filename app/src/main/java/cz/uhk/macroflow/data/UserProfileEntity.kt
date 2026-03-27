package cz.uhk.macroflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val weight: Double = 83.0,
    val height: Double = 175.0,
    val age: Int = 22,
    val gender: String = "male",
    val activityMultiplier: Float = 1.2f,
    val stepGoal: Int = 6000 // 👈 ✅ Nové: Uložený cíl kroků v DB!
)