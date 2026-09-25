// Validace StrengthModel (docs/adr/0024) – Monte Carlo simulace cvičenců.
// Spuštění (kotlinc): zkompilovat spolu s training/log/{Progression,StrengthModel}.kt,
// training/body/Muscles.kt a training/exercises/ExerciseLibrary.kt, main = StrengthModelMonteCarloKt.
//
// Model cvičence: skutečné 1RM roste 0–2 % týdně, trénink co 3–4 dny, 3 pracovní série
// v rozsahu 6–12 opakování s rezervou 0–RIRmax (0 = vždy do selhání), denní forma ±3 %, únava −3 % na sérii,
// chyba Epleyho ±4 %, 30 % tréninků s pomalým spouštěním (skutečný efekt 1,1–1,3).
// Měří se chyba odhadu 1RM v den DALŠÍHO tréninku.
// Zápis RIR (ADR 0025): „výchozí“ = uživatel RIR nemění (zapíše se 2), „zapsané“ = uživatel RIR
// odhadne s chybou ±1 opakování (σ = 1, zaokrouhleno, 0–5) – lidé rezervu typicky trefí o 1 vedle.
import cz.uhk.macroflow.training.log.LoggedSet
import cz.uhk.macroflow.training.log.StrengthModel
import java.util.Random
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

fun main() {
    println("RIR | zápis RIR | tréninků | model | poslední trénink (Epley) | max. dosavadní (Epley) | pokrytí 95% intervalu")
    for (maxRir in listOf(0, 2, 3)) for (logged in listOf(false, true)) for (n in listOf(4, 8, 16)) {
        val rnd = Random(100L + n + maxRir)
        var eM = 0.0; var eL = 0.0; var eX = 0.0; var cov = 0; var runs = 0
        repeat(3000) {
            val start = 60 + 60 * rnd.nextDouble(); val weekly = rnd.nextDouble() * 0.02
            val sets = ArrayList<LoggedSet>(); var day = 0; val days = ArrayList<Int>()
            for (i in 0..n) { days += day; day += if (rnd.nextBoolean()) 3 else 4 }
            fun trueRm(d: Int) = start * (1 + weekly).pow(d / 7.0)
            for (i in 0 until n) {
                val d = days[i]; val rm = trueRm(d) * (1 + 0.03 * rnd.nextGaussian())
                val targetReps = 6 + rnd.nextInt(7); val rir = rnd.nextInt(maxRir + 1)
                val load = floor(rm / (1 + (targetReps + rir) / 30.0) / 2.5) * 2.5
                val slow = rnd.nextDouble() < 0.3; val tempo = 1.1 + 0.2 * rnd.nextDouble()
                repeat(3) { k ->
                    var reps = floor(30 * (rm * (1 - 0.03 * k) * (1 + 0.04 * rnd.nextGaussian()) / load - 1)).toInt() - rir
                    if (slow) reps = floor(reps / tempo).toInt()
                    val noted = if (logged) (rir + Math.round(rnd.nextGaussian()).toInt()).coerceIn(0, StrengthModel.MAX_RIR) else StrengthModel.DEFAULT_RIR
                    if (reps >= 1) sets += LoggedSet(day = d, exerciseId = "x", weightKg = load, reps = reps, slowEccentric = slow, rir = noted)
                }
            }
            val truth = trueRm(days[n])
            val e = StrengthModel.estimate(sets, days[n]) ?: return@repeat
            val perDay = sets.groupBy { it.day }.toSortedMap().values.map { ds -> ds.maxOf { s -> s.weightKg * (1 + s.reps / 30.0) } }
            eM += abs(e.e1rm - truth) / truth; eL += abs(perDay.last() - truth) / truth; eX += abs(perDay.max() - truth) / truth
            if (truth in e.lower..e.upper) cov++; runs++
        }
        println("0–%d | %s | %2d | %.2f %% | %.2f %% | %.2f %% | %.0f %%".format(maxRir, if (logged) "zapsané" else "výchozí", n, 100 * eM / runs, 100 * eL / runs, 100 * eX / runs, 100.0 * cov / runs))
    }
}
