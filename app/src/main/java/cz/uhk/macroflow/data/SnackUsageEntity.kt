package cz.uhk.macroflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SnackUsageEntity — stínová tabulka pro sledování popularity potravin.
 * Propojeno se SnackEntity přes unikátní název (name).
 */
@Entity(tableName = "snack_usage_metadata")
data class SnackUsageEntity(
    @PrimaryKey val snackName: String,
    val usageCount: Int = 0,
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)