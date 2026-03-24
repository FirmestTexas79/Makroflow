package cz.uhk.macroflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_log")
data class WaterEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,           // "yyyy-MM-dd"
    val amountMl: Int,          // ml přidané v tomto záznamu
    val timestamp: Long = System.currentTimeMillis()
)