package cz.uhk.macroflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snacks")
data class SnackEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val weight: String,
    val p: Float,
    val s: Float,
    val t: Float,
    val isPre: Boolean
)