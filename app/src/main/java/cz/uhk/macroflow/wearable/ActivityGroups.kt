package cz.uhk.macroflow.wearable

/** Sporty z hodinek -> 4 skupiny aplikace. */
enum class SportGroup(val label: String) { STRENGTH("Síla"), CARDIO("Kardio"), WALK("Chůze"), OTHER("Ostatní") }

object ActivityGroups {
    private val cardio = setOf("running", "treadmill_running", "trail_running", "cycling", "indoor_cycling", "virtual_ride", "jump_rope",
        "indoor_cardio", "cardio", "hiit", "elliptical", "rowing", "indoor_rowing", "swimming", "lap_swimming", "stair_climbing", "stair_stepper")
    private val walk = setOf("walking", "casual_walking", "speed_walking", "hiking")

    fun of(typeKey: String): SportGroup = when {
        typeKey.contains("strength") -> SportGroup.STRENGTH
        typeKey in cardio -> SportGroup.CARDIO
        typeKey in walk -> SportGroup.WALK
        else -> SportGroup.OTHER
    }

    data class Summary(val group: SportGroup, val count: Int, val minutes: Int, val kcal: Int)

    fun summarize(acts: List<WearableActivityEntity>): List<Summary> =
        acts.groupBy { of(it.typeKey) }.map { (g, l) -> Summary(g, l.size, l.sumOf { it.durationSec } / 60, l.sumOf { it.kcal ?: 0 }) }
            .sortedBy { it.group.ordinal }

    /** Porovnani s planem dne (TrainingPrefs): plan sily / kardia vs. co hodinky skutecne videly. */
    fun planNote(plannedStrength: String?, plannedCardio: Boolean, acts: List<WearableActivityEntity>): String? {
        val s = summarize(acts).associateBy { it.group }
        val doneStrength = (s[SportGroup.STRENGTH]?.minutes ?: 0) >= 20
        val doneCardio = (s[SportGroup.CARDIO]?.minutes ?: 0) >= 10
        val plan = plannedStrength?.takeIf { it.isNotBlank() && !it.equals("rest", true) }
        return when {
            plan != null && doneStrength -> "Plán ${plan.uppercase()} splněn podle hodinek"
            plan != null -> "V plánu ${plan.uppercase()}, hodinky zatím žádnou sílu nezaznamenaly"
            plannedCardio && !doneCardio -> "V plánu kardio, hodinky zatím nic nezaznamenaly"
            plan == null && doneStrength -> "Silový trénink navíc mimo plán"
            else -> null
        }
    }
}