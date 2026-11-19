package cz.uhk.macroflow.pokemon.audio

/**
 * Která skladba hraje ve které lokaci (čistý Kotlin, pokryto testy). Hudba je vlastní,
 * syntetizovaná skriptem tools/audiogen/gen_audio.py – docs/adr/0017.
 */
object MusicMap {
    const val TOWN = "music_town"
    const val MEADOW = "music_meadow"
    const val MOUNTAINS = "music_mountains"
    const val CAVE = "music_cave"

    /** Název res/raw skladby podle BiomeType.name; null = ticho. */
    fun trackFor(biome: String): String? = when (biome) {
        "TOWN", "OZVENA", "ARENA_HALL", "TOWER_LOBBY", "TOWER_ARCHIVE", "TOWER_MEASURE" -> TOWN
        "MEADOW", "FOREST", "LAKE", "WATER", "QUIET_ORCHARD", "TOWER_PLAIN", "TOWER_FOREST", "TOWER_WATER" -> MEADOW
        "MOUNTAINS", "SKY_PASS", "ASH_PLAIN", "ECHO_CANYON", "TOWER_ROCK", "TOWER_ICE", "TOWER_FIRE" -> MOUNTAINS
        "CAVE_OPEN", "CAVE_MAZE", "HIDDEN_GROVE", "MINES", "TOWER_CAVE", "TOWER_DORM" -> CAVE
        else -> null
    }

    /** Smyčka prostředí patra Věže (res/raw, ElevenLabs); null = žádná. */
    fun ambientFor(biome: String): String? = when (biome) {
        "TOWER_ARCHIVE" -> "amb_clock"
        "TOWER_MEASURE" -> "amb_metronome"
        "TOWER_FOREST" -> "amb_forest_indoor"
        "TOWER_CAVE" -> "amb_crystals"
        "TOWER_DORM" -> "amb_breathing"
        "TOWER_LOBBY" -> "amb_hall"
        "TOWER_PLAIN" -> "amb_meadow"
        "TOWER_ROCK" -> "amb_mountain_wind"
        "TOWER_WATER" -> "amb_pond"
        "TOWER_ICE" -> "amb_blizzard"
        "TOWER_FIRE" -> "amb_furnace"
        else -> null
    }

    const val AMBIENT_VOLUME = 0.35f

    /** Hlasitost hudby během souboje (zvuky souboje mají být slyšet). */
    const val BATTLE_DUCK = 0.3f
    const val FADE_OUT_MS = 600L
    const val FADE_IN_MS = 1200L

    /** Hlasitost v čase [elapsedMs] lineárního přechodu z [from] do [to] za [durationMs]. */
    fun fade(from: Float, to: Float, elapsedMs: Long, durationMs: Long): Float {
        if (durationMs <= 0) return to
        val p = (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        return from + (to - from) * p
    }
}
