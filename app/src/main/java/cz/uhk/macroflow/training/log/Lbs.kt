package cz.uhk.macroflow.training.log

import kotlin.math.roundToLong

/** Libry ↔ kilogramy pro „Automatický převod“ při zápisu série. Ukládá se vždy v kg na dvě desetinná místa. */
object Lbs {
    const val KG_PER_LB = 0.45359237
    /** Krok tlačítek ± v librách (běžný nejmenší kotouč 2,5 lb na každé straně). */
    const val STEP = 5.0

    private fun round2(v: Double) = (v * 100).roundToLong() / 100.0
    fun toKg(lb: Double) = round2(lb * KG_PER_LB)
    /** Zpět na libry po desetinách – kg jsou uložené na setiny, chyba je nejvýš 0,01 lb, takže se vrátí, co hráč napsal. */
    fun fromKg(kg: Double) = (kg / KG_PER_LB * 10).roundToLong() / 10.0
}
