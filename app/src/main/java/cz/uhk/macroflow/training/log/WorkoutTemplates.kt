package cz.uhk.macroflow.training.log

/**
 * Šablony tréninkových dnů PUSH/PULL/LEGS ve dvou variantách A a B, které se střídají
 * (rytmus 1/1/1 a pak znovu) – čistý Kotlin, pokryto testy. docs/adr/0024.
 */
object WorkoutTemplates {

    enum class Kind(val label: String, val planType: String) {
        PUSH("PUSH", "push"), PULL("PULL", "pull"), LEGS("LEGS", "legs");

        companion object {
            fun fromPlanType(type: String?): Kind? = entries.firstOrNull { it.planType == type?.lowercase() }
        }
    }

    val VARIANTS = listOf('A', 'B')

    fun key(kind: Kind, variant: Char) = "${kind.name}_$variant"
    fun parse(key: String?): Pair<Kind, Char>? {
        val parts = key?.split('_') ?: return null
        val kind = Kind.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return null
        val v = parts.getOrNull(1)?.singleOrNull()?.takeIf { it in VARIANTS } ?: return null
        return kind to v
    }
    fun label(key: String) = parse(key)?.let { (k, v) -> "${k.label} $v" } ?: key

    // Výchozí cviky (id z ExerciseLibrary) podle Samuelova tréninku.
    private val PUSH = listOf("machine_chest_press", "incline_machine_press", "skull_crusher", "lateral_raise", "triceps_kickback", "pec_deck")
    private val PULL = listOf("lat_pulldown", "close_grip_pulldown", "wide_cable_row", "seated_cable_row", "single_arm_supported_curl", "hammer_curl", "ez_bar_curl", "reverse_pec_deck")
    private val LEGS = listOf("seated_leg_curl", "rdl", "single_leg_press", "standing_calf_raise", "leg_extension")

    /** Výchozí šablony = Samuelův trénink; A i B zatím stejné, liší se úpravou v editoru šablony. */
    val DEFAULTS: Map<String, List<String>> = mapOf(
        "PUSH_A" to PUSH, "PUSH_B" to PUSH,
        "PULL_A" to PULL, "PULL_B" to PULL,
        "LEGS_A" to LEGS, "LEGS_B" to LEGS
    )

    /**
     * Varianta pro dnešek: opak té, se kterou se tenhle typ dne cvičil naposledy (A → B → A…).
     * Když se dnes už podle šablony cvičilo, zůstane dnešní. Bez historie A.
     */
    fun variantFor(kind: Kind, history: List<LoggedSet>, today: Int): Char {
        val mine = history.mapNotNull { s -> parse(s.template)?.takeIf { it.first == kind }?.let { s.day to it.second } }
        mine.filter { it.first == today }.maxByOrNull { it.first }?.let { return it.second }
        val last = mine.filter { it.first < today }.maxByOrNull { it.first }?.second ?: return 'A'
        return if (last == 'A') 'B' else 'A'
    }
}
