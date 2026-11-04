package cz.uhk.macroflow.learning

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Oblasti, ve kterých se aplikace přizpůsobuje konkrétnímu člověku. */
enum class Area(val label: String, val icon: String) {
    ENERGY("Výdej energie", "🔥"), STRENGTH("Síla", "💪"), RITUAL("Ranní rituál", "🌅"), SLEEP("Spánek", "🌙"), WATCH("Hodinky", "⌚")
}

/** Jeden poznatek: věta s číslem, rozdíl proti výchozímu stavu a jistota 0–3 (tečky). */
data class Learning(
    val area: Area, val headline: String, val detail: String,
    val from: String?, val to: String?, val dots: Int, val differs: Boolean
)

data class EnergyIn(val ok: Boolean, val factor: Double, val modelTdee: Double, val adaptiveTdee: Double,
                    val confidence: Double, val weighIns: Int, val loggedDays: Int)
data class LiftIn(val name: String, val start: Double, val now: Double, val sd: Double, val sessions: Int)
data class RitualIn(val n: Int, val mae: Double)
data class SleepIn(val nights: Int, val tip: String?)
data class WatchIn(val days: Int, val modelErrPct: Double, val garminErrPct: Double, val garminWeight: Double)

data class Inputs(
    val daysUsed: Int, val energy: EnergyIn? = null, val lifts: List<LiftIn> = emptyList(),
    val ritual: RitualIn? = null, val sleep: SleepIn? = null, val watch: WatchIn? = null
)

data class Report(val daysUsed: Int, val unlocked: Boolean, val items: List<Learning>) {
    val differing get() = items.count { it.differs }
    val weeks get() = ((daysUsed + 6) / 7).coerceAtLeast(1)
}

data class TriggerState(val unlockedSeen: Boolean = false, val lastFactor: Double? = null,
                        val weekKey: String? = null, val dismissed: String? = null)
data class Notice(val key: String, val text: String)

/** Čistá logika bez Androidu: z dat udělá poznatky a rozhodne, kdy upozornit. */
object LearningModel {
    const val UNLOCK_DAYS = 7

    private fun kg(v: Double) = String.format(Locale.US, "%.1f", v).replace('.', ',').removeSuffix(",0")
    private fun signed(p: Int) = (if (p > 0) "+" else if (p < 0) "−" else "") + abs(p)

    fun build(i: Inputs): Report {
        val items = buildList {
            i.energy?.let { add(energy(it)) }
            i.lifts.filter { it.sessions >= 3 && it.start > 0 }
                .sortedByDescending { abs(it.now - it.start) / it.start }.take(2).forEach { add(lift(it)) }
            i.ritual?.takeIf { it.n >= 3 }?.let { add(ritual(it)) }
            i.sleep?.takeIf { it.nights >= 3 && it.tip != null }?.let { add(sleep(it)) }
            i.watch?.takeIf { it.days >= 10 }?.let { add(watch(it)) }
        }
        return Report(i.daysUsed, i.daysUsed >= UNLOCK_DAYS, items)
    }

    private fun energy(e: EnergyIn): Learning {
        if (!e.ok) return Learning(Area.ENERGY, "Zatím se učím tvůj výdej",
            "Chybí ještě ${maxOf(0, 7 - e.weighIns)} vážení a ${maxOf(0, 10 - e.loggedDays)} zapsaných dnů. Do té doby počítám podle rovnice.",
            null, null, 0, false)
        val pct = ((e.factor - 1) * 100).roundToInt()
        val differs = abs(pct) >= 3
        val head = if (!differs) "Rovnice sedí, tvůj výdej se od ní liší o méně než 3 %"
        else "Tvůj výdej je o ${abs(pct)} % ${if (pct < 0) "nižší" else "vyšší"} než podle rovnice"
        val dots = when { e.confidence < 0.25 -> 1; e.confidence < 0.5 -> 2; else -> 3 }
        return Learning(Area.ENERGY, head,
            "Z ${e.weighIns} vážení a ${e.loggedDays} zapsaných dnů. Pozorování se na odhadu podílí ${(e.confidence * 100).roundToInt()} %.",
            "${e.modelTdee.roundToInt()} kcal", "${e.adaptiveTdee.roundToInt()} kcal", dots, differs)
    }

    private fun lift(l: LiftIn): Learning {
        val d = l.now - l.start
        val head = if (abs(d) < 1) "${l.name}: 1RM ≈ ${kg(l.now)} kg, zatím beze změny"
        else "${l.name}: 1RM ≈ ${kg(l.now)} kg, o ${kg(abs(d))} kg ${if (d > 0) "víc" else "méně"} než na začátku"
        val dots = when { l.sessions < 5 -> 1; l.sessions < 10 -> 2; else -> 3 }
        return Learning(Area.STRENGTH, head, "Odhad ± ${kg(l.sd)} kg z ${l.sessions} tréninků.",
            "${kg(l.start)} kg", "${kg(l.now)} kg", dots, abs(d) / l.start >= 0.025)
    }

    private fun ritual(r: RitualIn): Learning {
        val dots = when { r.n < 5 -> 1; r.n < 15 -> 2; else -> 3 }
        return Learning(Area.RITUAL, "Ranní rituál předvyplňuju s odchylkou ${String.format(Locale.US, "%.1f", r.mae).replace('.', ',')} bodu",
            "Z ${r.n} odhadů porovnaných s tím, co jsi nakonec zadal. Čím nižší odchylka, tím líp.", null, null, dots, r.n >= 5)
    }

    private fun sleep(s: SleepIn): Learning {
        val dots = when { s.nights < 7 -> 1; s.nights < 14 -> 2; else -> 3 }
        return Learning(Area.SLEEP, s.tip!!, "Z ${s.nights} nocí z hodinek.", null, null, dots, true)
    }

    private fun watch(w: WatchIn): Learning {
        val pct = (w.garminWeight * 100).roundToInt()
        val dots = when { w.days < 14 -> 1; w.days < 21 -> 2; else -> 3 }
        return Learning(Area.WATCH,
            "Hodinky se proti váze mýlí o ${w.garminErrPct.roundToInt()} %, rovnice o ${w.modelErrPct.roundToInt()} %",
            "Z ${w.days} dnů. Odhad výdeje se proto z $pct % opírá o hodinky.", "bez hodinek", "hodinky $pct %", dots, true)
    }

    // ── Kdy upozornit ───────────────────────────────────────────────────────

    fun weekKey(d: LocalDate) = "${d.get(IsoFields.WEEK_BASED_YEAR)}-W${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

    /**
     * Jedno upozornění na Dashboardu, nebo null. Pořadí: odemčení po 7 dnech, výrazný posun odhadu výdeje (≥ 5 %),
     * týdenní shrnutí v neděli. Už zavřené upozornění ([TriggerState.dismissed]) se neopakuje.
     */
    fun notice(daysUsed: Int, factor: Double?, s: TriggerState, today: LocalDate): Notice? {
        if (daysUsed < UNLOCK_DAYS) return null
        val n = when {
            !s.unlockedSeen -> Notice("unlock", "Odemkla se nová obrazovka Co se aplikace naučila. Najdeš ji v Profilu.")
            factor != null && abs(factor - (s.lastFactor ?: 1.0)) >= 0.05 -> {
                val pct = ((factor - 1) * 100).roundToInt()
                Notice("energy:$pct", "Odhad tvého výdeje se posunul na ${signed(pct)} % oproti rovnici.")
            }
            today.dayOfWeek == DayOfWeek.SUNDAY && s.weekKey != weekKey(today) ->
                Notice("week:${weekKey(today)}", "Týdenní shrnutí je hotové. Podívej se, co se aplikace tenhle týden naučila.")
            else -> null
        }
        return n?.takeIf { it.key != s.dismissed }
    }

    /** „za 1 den“, „za 3 dny“, „za 6 dní“. */
    fun days(n: Int) = "$n ${when { n == 1 -> "den"; n in 2..4 -> "dny"; else -> "dní" }}"
}
