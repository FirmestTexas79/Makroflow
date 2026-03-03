package cz.uhk.macroflow

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checkins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val weight: Double,
    val energyLevel: Int,
    val sleepQuality: Int,
    val hungerLevel: Int,
    val mood: String = ""
)