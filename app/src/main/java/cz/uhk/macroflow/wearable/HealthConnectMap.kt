package cz.uhk.macroflow.wearable

import kotlin.math.roundToInt

/** Jedna fáze spánku z Health Connect. Časy jsou v ms, typ je číslo STAGE_TYPE_* z Health Connect. */
data class HcStage(val type: Int, val startMs: Long, val endMs: Long)

/** Jedna spánková relace. Časy jsou „lokální“ ms (jako Garmin *Local), aby se den probuzení dal spočítat bez časových pásem. */
data class HcSleepSession(val startMs: Long, val endMs: Long, val stages: List<HcStage>)

data class SleepFields(
    val sleepSec: Int, val deepSec: Int?, val lightSec: Int?, val remSec: Int?, val awakeSec: Int?,
    val awakeCount: Int?, val score: Int?, val startMs: Long, val endMs: Long
)

/** Čistá logika mapování Health Connect → naše tabulky. Bez Androidu, aby šla testovat. */
object HealthConnectMap {
    const val SOURCE = "health_connect"

    // Hodnoty SleepSessionRecord.STAGE_TYPE_*
    private const val AWAKE = 1
    private const val SLEEPING = 2
    private const val LIGHT = 4
    private const val DEEP = 5
    private const val REM = 6
    private const val AWAKE_IN_BED = 7

    /** Noc kratší než tohle je šlofík, ne noc. */
    const val MIN_NIGHT_SEC = 2 * 3600

    private fun sec(a: Long, b: Long) = ((b - a) / 1000L).coerceAtLeast(0L).toInt()

    fun sleep(s: HcSleepSession): SleepFields {
        if (s.stages.isEmpty()) {
            val total = sec(s.startMs, s.endMs)
            return SleepFields(total, null, null, null, null, null, null, s.startMs, s.endMs)
        }
        fun sum(vararg t: Int) = s.stages.filter { it.type in t }.sumOf { sec(it.startMs, it.endMs) }
        val deep = sum(DEEP); val light = sum(LIGHT); val rem = sum(REM); val generic = sum(SLEEPING)
        val awake = sum(AWAKE, AWAKE_IN_BED)
        val awakeCount = s.stages.count { it.type == AWAKE }
        val asleep = deep + light + rem + generic
        // Fáze hodinky rozlišily jen když je aspoň něco hluboké, lehké nebo REM
        val staged = deep + light + rem > 0
        val score = if (staged) score(asleep, deep, rem, awake) else null
        return SleepFields(asleep, if (staged) deep else null, if (staged) light else null, if (staged) rem else null,
            awake, awakeCount, score, s.startMs, s.endMs)
    }

    /**
     * Health Connect skóre spánku nemá, tak se odvodí: délka 50 b (8 h = plno), hluboký spánek 25 b (16 % = plno),
     * REM 15 b (20 % = plno), bdění 10 b (0 % = plno, 15 % a víc = nic). Bez fází se skóre nepočítá.
     */
    fun score(sleepSec: Int, deepSec: Int, remSec: Int, awakeSec: Int): Int? {
        if (sleepSec <= 0) return null
        val total = sleepSec + awakeSec
        val dur = (sleepSec / 28800.0).coerceIn(0.0, 1.0) * 50
        val deep = (deepSec.toDouble() / sleepSec / 0.16).coerceIn(0.0, 1.0) * 25
        val rem = (remSec.toDouble() / sleepSec / 0.20).coerceIn(0.0, 1.0) * 15
        val awake = (1 - (awakeSec.toDouble() / total / 0.15).coerceIn(0.0, 1.0)) * 10
        return (dur + deep + rem + awake).roundToInt()
    }

    /** Z několika relací jedné noci vezme tu nejdelší. Šlofíky pod [MIN_NIGHT_SEC] ignoruje. */
    fun nightOf(sessions: List<HcSleepSession>): HcSleepSession? =
        sessions.filter { sec(it.startMs, it.endMs) >= MIN_NIGHT_SEC }.maxByOrNull { it.endMs - it.startMs }

    /** Nošené hodinky: kroky a celkový výdej. Stres z Health Connect nepřichází, proto jiné pravidlo než u Garminu. */
    fun worn(d: WearableDayEntity) = (d.steps ?: 0) >= 500 && (d.totalKcal ?: 0) > 0

    /** Nejdůležitější: pokud aplikace nepíše spánek, kroky a cvičení, nemá smysl dál zkoušet. */
    data class Summary(val nights: Int, val stepDays: Int, val restHrDays: Int, val workouts: Int) {
        val any get() = nights + stepDays + restHrDays + workouts > 0
    }

    fun summary(days: List<WearableDayEntity>, activities: Int): Summary {
        val hc = days.filter { it.source == SOURCE }
        return Summary(hc.count { (it.sleepSec ?: 0) > 0 }, hc.count { (it.steps ?: 0) > 0 },
            hc.count { (it.restingHr ?: 0) > 0 }, activities)
    }
}
