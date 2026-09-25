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
        "TOWN" -> TOWN
        "MEADOW", "FOREST", "LAKE", "WATER" -> MEADOW
        "MOUNTAINS" -> MOUNTAINS
        "CAVE_OPEN", "CAVE_MAZE" -> CAVE
        else -> null
    }

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
