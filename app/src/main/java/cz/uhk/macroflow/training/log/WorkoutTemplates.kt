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

    /** Výchozí cviky (id z ExerciseLibrary). PUSH A podle Samuelova tréninku, ostatní ve stejném stylu. */
    val DEFAULTS: Map<String, List<String>> = mapOf(
        "PUSH_A" to listOf("machine_chest_press", "incline_machine_press", "skull_crusher", "lateral_raise", "triceps_kickback", "pec_deck"),
        "PUSH_B" to listOf("incline_db_press", "machine_shoulder_press", "cable_lateral_raise", "overhead_extension", "triceps_pushdown", "cable_fly"),
        "PULL_A" to listOf("lat_pulldown", "machine_row", "straight_arm_pulldown", "face_pull", "barbell_curl", "hammer_curl"),
        "PULL_B" to listOf("pull_up", "seated_cable_row", "db_row", "reverse_pec_deck", "incline_db_curl", "preacher_curl"),
        "LEGS_A" to listOf("back_squat", "rdl", "leg_press", "lying_leg_curl", "standing_calf_raise", "hanging_leg_raise"),
        "LEGS_B" to listOf("hack_squat", "bulgarian_split_squat", "leg_extension", "seated_leg_curl", "hip_thrust", "seated_calf_raise")
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
