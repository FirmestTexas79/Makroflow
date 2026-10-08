package cz.uhk.macroflow.training.exercises

/**
 * Doporučená pauza mezi sériemi podle cviku (docs/adr/0069).
 *
 * Těžké vícekloubové cviky s osou potřebují nejdelší zotavení (nervová soustava, celé tělo),
 * vícekloubové na stroji / s jednoručkami střed, izolované cviky a core krátce.
 * Opora: Schoenfeld a kol. 2016 (3 min > 1 min pro sílu i objem), ACSM 2009 (2–3 min u základních
 * cviků, 1–2 min u doplňkových). Jednostranné cviky = pauza po odcvičení obou stran.
 */
object RestTimes {

    const val HEAVY = 240     // dřep, mrtvý tah
    const val COMPOUND = 180  // těžké vícekloubové s osou / vlastní vahou
    const val MEDIUM = 120    // vícekloubové na stroji, s jednoručkami, kladky
    const val ISOLATION = 90  // izolované cviky na velké partie
    const val SMALL = 60      // malé svaly, core

    private val BY_ID: Map<String, Int> = buildMap {
        fun tier(s: Int, vararg ids: String) = ids.forEach { put(it, s) }
        tier(HEAVY, "back_squat", "deadlift")
        tier(COMPOUND, "bench_press", "overhead_press", "barbell_row", "rdl", "hack_squat", "leg_press",
            "hip_thrust", "pull_up", "chin_up", "close_grip_bench", "chest_dip")
        tier(MEDIUM, "incline_db_press", "db_shoulder_press", "lat_pulldown", "db_row", "machine_chest_press",
            "incline_machine_press", "machine_shoulder_press", "machine_row", "seated_cable_row",
            "close_grip_pulldown", "wide_cable_row", "walking_lunge", "bulgarian_split_squat",
            "single_leg_press", "nordic_curl", "farmers_walk", "back_extension")
        tier(ISOLATION, "cable_fly", "pec_deck", "push_up", "leg_extension", "seated_leg_curl", "lying_leg_curl",
            "skull_crusher", "barbell_curl", "ez_bar_curl", "preacher_curl", "overhead_extension",
            "straight_arm_pulldown", "hanging_leg_raise", "ab_wheel", "shrug", "hip_abduction", "standing_calf_raise")
        tier(SMALL, "lateral_raise", "cable_lateral_raise", "front_raise", "face_pull", "reverse_fly",
            "reverse_pec_deck", "incline_db_curl", "hammer_curl", "single_arm_supported_curl", "triceps_pushdown",
            "triceps_kickback", "wrist_curl", "reverse_wrist_curl", "plank", "side_plank", "cable_crunch",
            "pallof_press", "woodchop", "bird_dog", "seated_calf_raise")
    }

    /** Pauza v sekundách; neznámý (vlastní) cvik = [MEDIUM]. */
    fun seconds(exerciseId: String?): Int = BY_ID[exerciseId] ?: MEDIUM

    fun known(exerciseId: String) = exerciseId in BY_ID

    /** „2 min“, „1:30 min“ – do detailu cviku. */
    fun label(seconds: Int): String =
        if (seconds % 60 == 0) "${seconds / 60} min" else "${seconds / 60}:${String.format(java.util.Locale.US, "%02d", seconds % 60)} min"
}
