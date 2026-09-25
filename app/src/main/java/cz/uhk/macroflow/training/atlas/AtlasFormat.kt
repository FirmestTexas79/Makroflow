package cz.uhk.macroflow.training.atlas

import cz.uhk.macroflow.training.body.Muscle
import cz.uhk.macroflow.training.body.TrainingMuscles
import cz.uhk.macroflow.training.exercises.Exercise

/** Texty a převody pro atlas svalů (čistý Kotlin, pokryto testy). */
object AtlasFormat {

    /** „1,5× týdně“ – česká desetinná čárka, celá čísla bez „,0“. */
    fun frequency(perWeek: Double): String {
        val v = Math.round(perWeek * 2) / 2.0
        val num = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ',')
        return "$num× týdně"
    }

    /** Stav partie v týdenním plánu vzhledem k doporučení 2× týdně. */
    fun frequencyNote(m: Muscle, perWeek: Double): String = when {
        perWeek <= 0.0 -> "V plánu na tento týden chybí."
        m !in TrainingMuscles.HYPERTROPHY_TARGETS -> "Zapojuje se při jiných cvicích."
        perWeek < TrainingMuscles.TARGET_PER_WEEK -> "Pod doporučenými 2× týdně – přidej cvik níže."
        else -> "Splňuje doporučené 2× týdně."
    }

    /** Intenzita pro postavu: 2× týdně = plná barva. */
    fun intensities(freq: Map<Muscle, Double>): Map<Muscle, Double> =
        freq.mapValues { (it.value / TrainingMuscles.TARGET_PER_WEEK).coerceIn(0.0, 1.0) }

    /** Intenzity pro postavu v detailu cviku: hlavní partie naplno, pomocné napůl. */
    fun exerciseIntensities(e: Exercise): Map<Muscle, Double> =
        e.secondary.associateWith { TrainingMuscles.SECONDARY } + e.primary.associateWith { TrainingMuscles.PRIMARY }

    fun capitalized(m: Muscle) = m.label.replaceFirstChar { it.uppercase() }
}
