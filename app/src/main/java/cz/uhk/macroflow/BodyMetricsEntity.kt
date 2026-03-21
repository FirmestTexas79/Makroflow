package cz.uhk.macroflow

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "body_metrics",
    indices = [Index(value = ["date"], unique = true)]
)
data class BodyMetricsEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // Formát yyyy-MM-dd
    val neck: Float = 0f,
    val chest: Float = 0f,
    val bicep: Float = 0f,
    val forearm: Float = 0f,
    val waist: Float = 0f,
    val abdomen: Float = 0f,
    val thigh: Float = 0f,
    val calf: Float = 0f
)