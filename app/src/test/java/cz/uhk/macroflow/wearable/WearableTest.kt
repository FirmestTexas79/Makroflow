package cz.uhk.macroflow.wearable

import org.junit.Assert.*
import org.junit.Test

class WearableTest {
    private val summary = """{"data":[{"calendarDate":"2026-11-03","calories":{"burnedResting":2111,"burnedActive":1268,"burnedTotal":3379},
      "movement":{"steps":{"goal":7440,"value":13615}},"heartRate":{"minValue":46,"maxValue":156,"restingValue":50},
      "stress":{"avgLevel":42},"bodyBattery":{"minValue":9,"maxValue":63,"activityEvents":[
        {"eventType":"SLEEP","bodyBatteryImpact":35},{"eventType":"ACTIVITY","bodyBatteryImpact":-20}]}},
      {"calendarDate":"2026-10-31","calories":{"burnedTotal":2100},"movement":{"steps":{"value":120}},"stress":{"avgLevel":-1}}]}"""

    private val sleep = """{"calendarDate":"2026-11-03","sleepTimeSeconds":20700,"deepSleepSeconds":1980,"lightSleepSeconds":15960,
      "remSleepSeconds":2760,"awakeSleepSeconds":8700,"sleepStartTimestampLocal":1793659945000,"sleepEndTimestampLocal":1793689345000,
      "averageSpO2Value":97.0,"lowestSpO2Value":90,"averageRespirationValue":17.0,"awakeCount":4,"avgSleepStress":24.0,"avgHeartRate":56.0,
      "sleepScores":{"overall":{"value":49,"qualifierKey":"POOR"}}}"""

    private val emptyNight = """[{"calendarDate":"2026-11-04","sleepTimeSeconds":null,"deepSleepSeconds":null},""" + sleep + "]"

    @Test fun summaryFields() {
        val d = GarminParser.summaries(summary).first()
        assertEquals(3379, d.totalKcal); assertEquals(1268, d.activeKcal); assertEquals(13615, d.steps)
        assertEquals(50, d.restingHr); assertEquals(42, d.stressAvg); assertEquals(35, d.bbSleepGain)
        assertTrue(GarminParser.isWorn(d))
    }

    @Test fun notWornDayIsDetected() {
        assertFalse(GarminParser.isWorn(GarminParser.summaries(summary)[1]))
    }

    @Test fun sleepFieldsAndMerge() {
        val s = GarminParser.sleeps(sleep).single()
        assertEquals(20700, s.sleepSec); assertEquals(49, s.sleepScore); assertEquals(97.0, s.avgSpo2!!, 0.01)
        val m = GarminParser.merge(GarminParser.summaries(summary).first(), s)
        assertEquals(3379, m.totalKcal); assertEquals(1980, m.deepSec)
    }

    @Test fun nightsWithoutDataAreSkipped() {
        assertEquals(listOf("2026-11-03"), GarminParser.sleeps(emptyNight).map { it.date })
    }

    @Test fun activities() {
        val a = GarminParser.activities("""[{"activityId":24952083994,"activityName":"Strength","startTimeLocal":"2026-11-03 14:42:17",
          "activityType":{"typeKey":"strength_training"},"duration":2844.99,"calories":330.0,"averageHR":113.0,"maxHR":163.0}]""").single()
        assertEquals("2026-11-03", a.date); assertEquals("strength_training", a.typeKey)
        assertEquals(330, a.kcal); assertEquals(2844, a.durationSec)
    }

    private fun days(model: Double, garmin: Double, n: Int = 14) = List(n) { WearableCompare.Day(model, garmin) }

    @Test fun compareNeedsEnoughData() {
        assertNull(WearableCompare.compare(days(3000.0, 3200.0, 5), 3100.0))
        assertNull(WearableCompare.compare(days(3000.0, 3200.0), null))
    }

    @Test fun closerSourceGetsMoreWeight() {
        val r = WearableCompare.compare(days(2800.0, 3350.0), 3300.0)!!
        assertTrue(r.garminErrPct < r.modelErrPct); assertTrue(r.garminWeight > 0.5)
        val r2 = WearableCompare.compare(days(3300.0, 2800.0), 3300.0)!!
        assertTrue(r2.garminWeight < 0.5)
        assertTrue(r.blended in r.modelMean..r.garminMean)
    }

    @Test fun equalErrorsGiveEqualWeight() {
        assertEquals(0.5, WearableCompare.compare(days(3000.0, 3200.0), 3100.0)!!.garminWeight, 0.01)
    }
}