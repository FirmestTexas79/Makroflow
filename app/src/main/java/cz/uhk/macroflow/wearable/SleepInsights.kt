package cz.uhk.macroflow.wearable

import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Dlouhodobé vyhodnocení spánku z uložených nocí. Čisté funkce: žádný Android, snadno se testují. */
object SleepInsights {
    const val NEED_SEC = 8 * 3600
    const val MIN_NIGHTS = 3

    data class Summary(
        val nights: Int,               // noci s daty v okně 30 dnů
        val avg7Sec: Int?, val avg30Sec: Int?,
        val trendSec: Int?,            // průměr posledních 7 nocí minus předchozích 7
        val debt7Sec: Int,             // součet chybějícího spánku proti 8 h za posledních 7 nocí
        val bedtimeSdMin: Int?, val avgBedtimeMin: Int?,   // minuty od půlnoci, po půlnoci se přičítá 1440
        val avgScore7: Int?, val deepPct: Int?, val remPct: Int?
    )

    private fun nightsUpTo(days: List<WearableDayEntity>, asOf: String) =
        days.filter { it.sleepSec != null && it.date <= asOf }.sortedBy { it.date }

    /** Minuty od 12:00 předchozího dne přepočtené tak, aby 23:30 a 00:30 ležely vedle sebe. */
    fun bedtimeMin(d: WearableDayEntity): Int? = d.sleepStartMs?.let { ms ->
        val m = ((ms / 60000) % 1440).toInt()
        if (m < 720) m + 1440 else m
    }

    fun summarize(days: List<WearableDayEntity>, asOf: String): Summary {
        val all = nightsUpTo(days, asOf)
        val from30 = LocalDate.parse(asOf).minusDays(29).toString()
        val last30 = all.filter { it.date >= from30 }
        val last7 = last30.takeLast(7)
        val prev7 = last30.dropLast(7).takeLast(7)
        fun avg(l: List<WearableDayEntity>) = l.mapNotNull { it.sleepSec }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val bed = last30.takeLast(14).mapNotNull { bedtimeMin(it) }
        val mean = bed.takeIf { it.size >= 3 }?.average()
        val sd = mean?.let { m -> sqrt(bed.sumOf { (it - m) * (it - m) } / bed.size) }
        fun share(f: (WearableDayEntity) -> Int?) = last30.filter { (it.sleepSec ?: 0) > 0 && f(it) != null }
            .takeIf { it.isNotEmpty() }?.map { f(it)!!.toDouble() / it.sleepSec!! * 100 }?.average()?.roundToInt()
        return Summary(
            nights = last30.size, avg7Sec = avg(last7), avg30Sec = avg(last30),
            trendSec = if (last7.size >= 4 && prev7.size >= 4) avg(last7)!! - avg(prev7)!! else null,
            debt7Sec = last7.sumOf { (NEED_SEC - (it.sleepSec ?: NEED_SEC)).coerceAtLeast(0) },
            bedtimeSdMin = sd?.roundToInt(), avgBedtimeMin = mean?.roundToInt(),
            avgScore7 = last7.mapNotNull { it.sleepScore }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            deepPct = share { it.deepSec }, remPct = share { it.remSec }
        )
    }

    /** Skóre po náročných dnech (stres předchozího dne nad mediánem) vs. po klidných; null při nedostatku dat. */
    fun stressEffect(days: List<WearableDayEntity>, asOf: String): Pair<Int, Int>? {
        val byDate = days.associateBy { it.date }
        val rows = nightsUpTo(days, asOf).mapNotNull { n ->
            val prev = byDate[LocalDate.parse(n.date).minusDays(1).toString()]
            val stress = prev?.stressAvg?.takeIf { it >= 0 }
            if (stress != null && n.sleepScore != null) stress to n.sleepScore else null
        }
        if (rows.size < 10) return null
        val median = rows.map { it.first }.sorted()[rows.size / 2]
        val hi = rows.filter { it.first >= median }.map { it.second }
        val lo = rows.filter { it.first < median }.map { it.second }
        if (hi.size < 4 || lo.size < 4) return null
        val a = hi.average().roundToInt(); val b = lo.average().roundToInt()
        return if (b - a >= 8) a to b else null
    }

    private fun hm(min: Int) = "%02d:%02d".format((min / 60) % 24, min % 60)
    private fun hours(sec: Int) = "%.1f".format(sec / 3600.0).replace('.', ',')

    /** Nejvýš 3 doporučení, seřazená podle dopadu. Prázdný seznam = nic zvláštního. */
    fun recommendations(s: Summary, stress: Pair<Int, Int>? = null): List<String> {
        if (s.nights < MIN_NIGHTS) return listOf("Na vyhodnocení potřebuju aspoň $MIN_NIGHTS noci z hodinek (zatím ${s.nights}).")
        val out = mutableListOf<String>()
        if (s.debt7Sec >= 4 * 3600) {
            val target = s.avgBedtimeMin?.let { " Dnes zkus být v posteli do ${hm(it - 30)}." } ?: ""
            out += "Za posledních 7 nocí ti chybí ${hours(s.debt7Sec)} h oproti 8 h.$target"
        }
        if ((s.bedtimeSdMin ?: 0) >= 60)
            out += "Chodíš spát nepravidelně (±${s.bedtimeSdMin} min kolem ${hm(s.avgBedtimeMin ?: 0)}). Pevný čas usínání pomáhá víc než jedna dlouhá noc."
        if ((s.trendSec ?: 0) <= -30 * 60)
            out += "Poslední týden spíš v průměru o ${-(s.trendSec!!) / 60} min méně než týden předtím."
        if (s.nights >= 5 && (s.remPct ?: 100) < 18)
            out += "REM spánku máš málo (${s.remPct} %, doporučeno 21–31 %). Může ho zkracovat pozdní jídlo, alkohol nebo stres večer."
        stress?.let { (hi, lo) -> out += "Po náročných dnech spíš hůř: skóre $hi místo $lo. Večer po těžkém dni plánuj klidnější dojezd." }
        if (out.isEmpty() && (s.avg7Sec ?: 0) >= 7 * 3600 + 1800 && (s.bedtimeSdMin ?: 99) < 45)
            out += "Spánek máš v pořádku a pravidelný. Drž stávající rytmus."
        return out.take(3)
    }
}
