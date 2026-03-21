package cz.uhk.macroflow

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "consumed_snacks")
data class ConsumedSnackEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // Formát yyyy-MM-dd
    val name: String,
    val p: Float,
    val s: Float,
    val t: Float,
    val calories: Int
)