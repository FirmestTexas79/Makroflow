import cz.uhk.macroflow.energy.*

// Monte Carlo validace adaptivního výdeje (fáze B) – výsledky viz docs/adr/0003.
// Spuštění: zkompilovat spolu s app/src/main/java/cz/uhk/macroflow/energy/*.kt (kotlinc) a spustit main.
import java.util.Random
import kotlin.math.abs
fun main() {
    val rnd = Random(2026); val runs = 1000
    for (every in listOf(1, 2, 3)) {
        var m = 0.0; var a = 0.0; var ok = 0; var within150m = 0; var within150a = 0
        repeat(runs) { i ->
            val t = 2200.0 + rnd.nextDouble() * 1200.0
            val model = t * (1.0 + rnd.nextGaussian() * 0.10)
            val intake = t + (rnd.nextDouble() - 0.5) * 1000.0
            val r = Random(5000L + i); var w = 83.0
            val days = (0 until 28).map { d ->
                val inTake = intake + r.nextGaussian() * 250
                val z = w + r.nextGaussian() * 0.7
                w += (inTake - t) / 7700.0
                AdaptiveExpenditure.Day(20000 + d, if (d % every == 0) z else null, inTake, model)
            }
            val e = AdaptiveExpenditure.estimate(days)
            if (e.status == AdaptiveExpenditure.Status.OK) ok++
            m += abs(model - t); a += abs(e.adaptiveTdee - t)
            if (abs(model - t) < 150) within150m++; if (abs(e.adaptiveTdee - t) < 150) within150a++
        }
        println("vážení každý $every. den: MAE model ${(m/runs).toInt()} kcal, adaptivní ${(a/runs).toInt()} kcal; do ±150 kcal: model ${within150m*100/runs} %, adaptivní ${within150a*100/runs} %; OK ${ok*100/runs} %")
    }
}
