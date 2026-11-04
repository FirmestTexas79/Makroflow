package cz.uhk.macroflow.wearable

import org.json.JSONArray
import org.json.JSONObject

/** Cista cast: JSON z Garmin Personal API -> entity. Zadna sit, zadny Android (kromě org.json). */
object GarminParser {

    private fun JSONObject.int(k: String): Int? = if (isNull(k) || !has(k)) null else optDouble(k).let { if (it.isNaN()) null else it.toInt() }
    private fun JSONObject.dbl(k: String): Double? = if (isNull(k) || !has(k)) null else optDouble(k).takeIf { !it.isNaN() }
    private fun JSONObject.lng(k: String): Long? = if (isNull(k) || !has(k)) null else optLong(k)
    private fun JSONObject.obj(k: String): JSONObject? = optJSONObject(k)

    /** /usersummary-service/v2/dailysummaries -> {"data":[...]} */
    fun summaries(json: String, now: Long = System.currentTimeMillis()): List<WearableDayEntity> {
        val arr = JSONObject(json).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val date = o.optString("calendarDate").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val cal = o.obj("calories")
            val hr = o.obj("heartRate")
            val bb = o.obj("bodyBattery")
            val sleepEvent = bb?.optJSONArray("activityEvents")?.let { ev ->
                (0 until ev.length()).map { ev.getJSONObject(it) }.firstOrNull { it.optString("eventType") == "SLEEP" }
            }
            WearableDayEntity(
                date = date,
                totalKcal = cal?.int("burnedTotal"),
                activeKcal = cal?.int("burnedActive"),
                restingKcal = cal?.int("burnedResting"),
                steps = o.obj("movement")?.obj("steps")?.int("value"),
                restingHr = hr?.int("restingValue"),
                stressAvg = o.obj("stress")?.int("avgLevel"),
                bbMin = bb?.int("minValue"),
                bbMax = bb?.int("maxValue"),
                bbSleepGain = sleepEvent?.int("bodyBatteryImpact"),
                syncedAt = now
            )
        }
    }

    /** dailySleep (objekt) i dailySleepSummariesByDate (pole); noci bez dat (sleepTimeSeconds=null) se preskoci. */
    fun sleeps(json: String): List<WearableDayEntity> {
        val t = json.trim()
        val items = if (t.startsWith("[")) JSONArray(t).let { a -> (0 until a.length()).map { a.getJSONObject(it) } } else listOf(JSONObject(t))
        return items.mapNotNull { o ->
            val date = o.optString("calendarDate").takeIf { it.isNotEmpty() && o.int("sleepTimeSeconds") != null } ?: return@mapNotNull null
            WearableDayEntity(
                date = date,
                sleepSec = o.int("sleepTimeSeconds"), deepSec = o.int("deepSleepSeconds"),
                lightSec = o.int("lightSleepSeconds"), remSec = o.int("remSleepSeconds"), awakeSec = o.int("awakeSleepSeconds"),
                sleepStartMs = o.lng("sleepStartTimestampLocal"), sleepEndMs = o.lng("sleepEndTimestampLocal"),
                sleepScore = o.obj("sleepScores")?.obj("overall")?.int("value"),
                awakeCount = o.int("awakeCount"),
                avgSpo2 = o.dbl("averageSpO2Value"), lowSpo2 = o.int("lowestSpO2Value"),
                avgResp = o.dbl("averageRespirationValue"), sleepStress = o.dbl("avgSleepStress"), sleepAvgHr = o.dbl("avgHeartRate")
            )
        }
    }

    /** /activitylist-service/activities/search/activities -> pole aktivit */
    fun activities(json: String): List<WearableActivityEntity> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            val start = o.optString("startTimeLocal")
            WearableActivityEntity(
                id = o.getLong("activityId"), date = start.take(10), startLocal = start,
                typeKey = o.obj("activityType")?.optString("typeKey") ?: "other",
                name = o.optString("activityName"), kcal = o.int("calories"),
                durationSec = o.optDouble("duration", 0.0).toInt(), avgHr = o.int("averageHR"), maxHr = o.int("maxHR")
            )
        }
    }

    /** Spojí den ze souhrnu a spanek (spanek ma prednost jen ve svých polich). */
    fun merge(base: WearableDayEntity, sleep: WearableDayEntity?): WearableDayEntity =
        if (sleep == null) base else base.copy(
            sleepSec = sleep.sleepSec, deepSec = sleep.deepSec, lightSec = sleep.lightSec, remSec = sleep.remSec,
            awakeSec = sleep.awakeSec, sleepStartMs = sleep.sleepStartMs, sleepEndMs = sleep.sleepEndMs,
            sleepScore = sleep.sleepScore, awakeCount = sleep.awakeCount, avgSpo2 = sleep.avgSpo2, lowSpo2 = sleep.lowSpo2,
            avgResp = sleep.avgResp, sleepStress = sleep.sleepStress, sleepAvgHr = sleep.sleepAvgHr
        )

    /** Den, kdy hodinky skutecne byly na ruce (Garmin dava stres -1 a minimum kroku jinak). */
    fun isWorn(d: WearableDayEntity): Boolean = (d.stressAvg ?: -1) >= 0 && (d.steps ?: 0) >= 500 && (d.totalKcal ?: 0) > 0
}