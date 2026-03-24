package cz.uhk.macroflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checkins")
data class CheckInEntity(
    @PrimaryKey val date: String, // Datum je teď unikátní klíč
    val weight: Double,
    val energyLevel: Int,
    val sleepQuality: Int,
    val hungerLevel: Int,
    val trainingReps: Int = 0,
    val trainingIntensity: Float = 0f,
    val mood: String = ""
)