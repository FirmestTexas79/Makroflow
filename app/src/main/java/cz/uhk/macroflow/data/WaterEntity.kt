package cz.uhk.macroflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_log")
data class WaterEntity(
    @PrimaryKey val timestamp: Long = System.currentTimeMillis(), // 👈 ✅ Čas jako primární klíč
    val date: String,
    val amountMl: Int
)