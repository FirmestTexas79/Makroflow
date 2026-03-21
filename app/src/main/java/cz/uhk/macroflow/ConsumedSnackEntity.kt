package cz.uhk.macroflow

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.sql.Time

@Entity(tableName = "consumed_snacks")
data class ConsumedSnackEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // Formát yyyy-MM-dd
    val time: String,
    val name: String,
    val p: Float,
    val s: Float,
    val t: Float,
    val calories: Int
)