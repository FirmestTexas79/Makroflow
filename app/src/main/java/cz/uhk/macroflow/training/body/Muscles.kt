package cz.uhk.macroflow.training.body

/**
 * Svalové partie a to, které z nich trénink daného typu zatěžuje (čistý Kotlin, pokryto testy).
 * Podklady v docs/adr/0008.
 */
enum class Muscle(val label: String) {
    CHEST("prsa"),
    FRONT_DELTS("přední ramena"),
    REAR_DELTS("zadní ramena"),
    BICEPS("biceps"),
    TRICEPS("triceps"),
    FOREARMS("předloktí"),
    ABS("břicho"),
    OBLIQUES("šikmé břišní"),
    TRAPS("trapézy"),
    LATS("široký zádový"),
    LOWER_BACK("spodní záda"),
    GLUTES("hýždě"),
    QUADS("kvadricepsy"),
    HAMSTRINGS("hamstringy"),
    CALVES("lýtka");
}

object TrainingMuscles {

    const val PRIMARY = 1.0
    const val SECONDARY = 0.5

    /** Partie, u kterých má smysl hlídat frekvenci pro růst (předloktí a šikmé břišní jdou „s sebou“). */
    val HYPERTROPHY_TARGETS = listOf(
        Muscle.CHEST, Muscle.FRONT_DELTS, Muscle.REAR_DELTS, Muscle.BICEPS, Muscle.TRICEPS,
        Muscle.TRAPS, Muscle.LATS, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.ABS
    )

    /** Doporučená frekvence pro růst svalu: 2× týdně (Schoenfeld, Ogborn & Krieger 2016). */
    const val TARGET_PER_WEEK = 2.0

    private fun map(primary: List<Muscle>, secondary: List<Muscle> = emptyList()) =
        primary.associateWith { PRIMARY } + secondary.associateWith { SECONDARY }

    /** Zapojení partií pro typ tréninku z plánu (klíče jako v TrainingPrefs). Neznámý typ / rest = prázdné. */
    fun of(type: String?): Map<Muscle, Double> = when (type?.lowercase()) {
        "push" -> map(listOf(Muscle.CHEST, Muscle.FRONT_DELTS, Muscle.TRICEPS))
        "pull" -> map(listOf(Muscle.LATS, Muscle.TRAPS, Muscle.REAR_DELTS, Muscle.BICEPS),
                      listOf(Muscle.FOREARMS, Muscle.LOWER_BACK))
        "legs" -> map(listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES),
                      listOf(Muscle.LOWER_BACK, Muscle.ABS))
        "full" -> map(listOf(Muscle.CHEST, Muscle.LATS, Muscle.FRONT_DELTS, Muscle.QUADS, Muscle.GLUTES, Muscle.HAMSTRINGS),
                      listOf(Muscle.TRAPS, Muscle.REAR_DELTS, Muscle.BICEPS, Muscle.TRICEPS, Muscle.ABS, Muscle.LOWER_BACK, Muscle.CALVES))
        // Kardio
        "run"    -> map(listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES), listOf(Muscle.ABS, Muscle.OBLIQUES))
        "bike"   -> map(listOf(Muscle.QUADS, Muscle.GLUTES), listOf(Muscle.HAMSTRINGS, Muscle.CALVES))
        "stairs" -> map(listOf(Muscle.GLUTES, Muscle.QUADS), listOf(Muscle.HAMSTRINGS, Muscle.CALVES))
        "rope"   -> map(listOf(Muscle.CALVES), listOf(Muscle.QUADS, Muscle.FRONT_DELTS, Muscle.FOREARMS, Muscle.ABS))
        else -> emptyMap()
    }

    /** Efektivní počet tréninků partie za týden: hlavní zapojení 1, vedlejší 0,5. */
    fun weeklyFrequency(types: List<String?>): Map<Muscle, Double> {
        val out = Muscle.entries.associateWith { 0.0 }.toMutableMap()
        types.forEach { t -> of(t).forEach { (m, w) -> out[m] = out.getValue(m) + w } }
        return out
    }

    /** Partie pod doporučenou frekvencí; prázdné, když v týdnu není žádný trénink. */
    fun belowTarget(freq: Map<Muscle, Double>): List<Muscle> {
        if (freq.values.all { it == 0.0 }) return emptyList()
        return HYPERTROPHY_TARGETS.filter { (freq[it] ?: 0.0) < TARGET_PER_WEEK }
    }

    /** „Hlavně: prsa, přední ramena, triceps · Vedlejší: …“ */
    fun describe(type: String?): String {
        val m = of(type)
        if (m.isEmpty()) return ""
        val main = m.filterValues { it >= PRIMARY }.keys.joinToString(", ") { it.label }
        val side = m.filterValues { it < PRIMARY }.keys.joinToString(", ") { it.label }
        return if (side.isEmpty()) main.replaceFirstChar { it.uppercase() }
        else "${main.replaceFirstChar { it.uppercase() }} · vedlejší: $side"
    }
}
