package cz.uhk.macroflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analytics_cache")
data class AnalyticsCacheEntity(
    @PrimaryKey val date: String,    // Formát yyyy-MM-dd
    val smoothedWeight: Double,      // Očištěná váha (Weighted Moving Average)
    val trendSlope: Double,          // Směrnice (kg/den) - základ pro predikci
    val standardDeviation: Double,   // "Sigma" - šířka stínu v grafu
    val confidenceScore: Float,      // 0.0 - 1.0 (podle počtu a konzistence dat)
    val metabolicEfficiency: Double  // Poměr přijatá vs. spálená energie (korekce TDEE)
)