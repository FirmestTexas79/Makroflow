package cz.uhk.macroflow

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1, // Singleton – vždy jen jeden řádek
    val weight: Double = 83.0,
    val height: Double = 175.0,
    val age: Int = 22,
    val gender: String = "male",
    val activityMultiplier: Float = 1.2f
)