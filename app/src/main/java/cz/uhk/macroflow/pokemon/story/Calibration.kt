package cz.uhk.macroflow.pokemon.story

import java.time.LocalDate

/**
 * Kapitola V · Kalibrace ve Věži Řádu Váhy (bible příběhu, docs/adr/0103). Čistá pravidla a texty, pokryto testy.
 *
 * Sedm dnů se zápisem jídla v řadě; počítá se zápis, nikdy kalorie, váha ani tvar těla. Jeden vynechaný den
 * se toleruje, druhý sérii vynuluje. Dnešek bez zápisu se za vynechaný nepočítá (den ještě neskončil).
 */
object Calibration {
    const val DAYS = 7
    const val TOLERANCE = 1
    /** Index fáze „Kalibrace“ v kapitole V (Dobré ráno, Jméno na dveřích, Pět Mistrů, Pozvánka, Kalibrace). */
    const val STAGE = 4

    data class Streak(val days: Int, val missed: Int)

    fun streak(logged: Set<LocalDate>, since: LocalDate, today: LocalDate): Streak {
        var days = 0; var missed = 0
        var d = since
        while (!d.isAfter(today)) {
            when {
                d in logged -> days++
                d == today -> {}
                else -> { missed++; if (missed > TOLERANCE) { days = 0; missed = 0 } }
            }
            d = d.plusDays(1)
        }
        return Streak(days, missed)
    }

    /** Pravidelnost za posledních 30 dní pro Spis Subjektu – nikdy váha ani míry. */
    data class Stats(
        val mealDays: Int, val ritualDays: Int, val waterDays: Int, val avgSteps: Int, val trainings7: Int,
        val streak: Streak?, val calibrated: Boolean
    )

    const val STAGE_INVITE_HINT = "Věž Řádu Váhy stojí na jižním konci města. S Pozvánkou se ti dveře otevřou."

    const val SEPT_START = "„Pozvánka platná. Subjekt Okruhu 213, vítejte ve Věži.“\n\n" +
        "Archivář Sept nezvedne hlavu od psacího stroje. „Kalibrace vyžaduje sedm dnů se záznamem výživy. " +
        "Na kalorie nehledíme, jen na pravidelnost. Jeden vynechaný den tolerujeme – subjekty jsou křehké. Druhý znamená začít znovu.“\n\n" +
        "„Posaďte se. Čekání je součástí měření.“"
    const val SEPT_HINT = "Zapisuj si jídlo každý den. Sept počítá dny v řadě a jeden vynechaný den odpustí."

    fun septProgress(s: Streak): String = "„Měření probíhá: ${s.days} ze $DAYS dnů.“\n\n" + when {
        s.days == 0 -> "„Záznam přerušen. Měření začíná znovu. Subjekty se přerušují běžně.“"
        s.missed >= TOLERANCE -> "„Tolerance je vyčerpána. Další vynechaný den a začneme znovu.“"
        else -> "„Tolerance nevyužita. Výborně.“ Pero se ani na okamžik nezastaví."
    }

    const val STAMPED = "Sept poprvé zvedne hlavu. V brýlích se odráží jen světlo.\n\n" +
        "Razítko dopadne na spis tak tvrdě, že se otřese celý pult: KALIBROVÁN.\n\n" +
        "„Subjekt kalibrován. Archiv je… zatím neschválen. Ale už brzy.“"
    const val SEPT_DONE = "„Subjekt kalibrován. Archiv je zatím neschválen. Ale už brzy.“ Pero se ani na okamžik nezastaví."
    const val SEPT_BEFORE = "„Subjekt bez pozvánky. Žádost zatím neschválena.“"

    /** Ukazatel výtahu: 7 pater nahoru zamčených, nula je vestibul, dvě skrytá dolů jen s Vhledem. */
    /** [panel] = vypsat i stupnici (před kalibrací); s odemčenými patry ji nahradí tlačítka. */
    fun elevatorText(insight: Int, panel: Boolean = true): String = buildString {
        if (panel) {
            for (f in 7 downTo 1) append("  $f   ─  zatím neschváleno\n")
            append("▶ 0   ●  vestibul (jste zde)\n")
            if (insight >= 3) append("  ▒   ─  tlačítko chybí\n  ▒   ─  tlačítko chybí\n")
            append('\n')
        }
        append(if (insight >= 3) "Pod nulou jsou na panelu dva zaslepené otvory. Někdo je zalepil zevnitř." else "Pod nulou je panel hladký.")
    }

    private fun dots(label: String, value: String, w: Int = 30): String =
        label + " " + ".".repeat((w - label.length - value.length - 2).coerceAtLeast(1)) + " " + value

    /** Spis Subjektu: jen pravidelnost; váha a míry se nikdy neevidují. Začerněná místa podle Vhledu. */
    fun fileText(s: Stats, insight: Int): String = buildString {
        append("ŘÁD VÁHY · STANICE 0 · ARCHIV\n")
        append("SPIS SUBJEKTU\n\n")
        append("Subjekt: OKRUH 213\n")
        append("Stav: ").append(
            if (s.calibrated) "KALIBROVÁN" else s.streak?.let { "KALIBRACE PROBÍHÁ (${it.days}/$DAYS)" } ?: "ČEKÁ NA KALIBRACI").append('\n')
        s.streak?.takeIf { !s.calibrated }?.let { append("Tolerance: ").append(if (it.missed >= TOLERANCE) "využita" else "nevyužita").append('\n') }
        append("──────────────────────────────\n")
        append("Pravidelnost za 30 dní\n")
        append(dots("Záznam výživy", "${s.mealDays}/30")).append('\n')
        append(dots("Ranní hlášení", "${s.ritualDays}/30")).append('\n')
        append(dots("Hydratační norma", "${s.waterDays}/30")).append('\n')
        append(dots("Pohyb (průměr)", "${s.avgSteps} kr.")).append('\n')
        append(dots("Výcvik (7 dní)", "${s.trainings7} d")).append('\n')
        append("──────────────────────────────\n")
        append("Tělesné míry: neevidováno")
        append(if (insight >= 3) " (pro nádobu nerelevantní)\n" else " (███████████)\n")
        append("Předchozí subjekt: OKRUH 212 – ")
        append(if (insight >= 4) "kalibrace nedokončena\n" else "████████████\n")
        append("\nPoznámka archiváře:\n")
        append(when {
            s.mealDays >= 24 -> "„Subjekt vykazuje nadprůměrnou pravidelnost. Vhodný.“"
            s.mealDays >= 15 -> "„Pravidelnost uspokojivá. Pokračovat v měření.“"
            else -> "„Pravidelnost kolísavá. Subjekty se přerušují běžně. Pokračovat.“"
        })
    }
}
