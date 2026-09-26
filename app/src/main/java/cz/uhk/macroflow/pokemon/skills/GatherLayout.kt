package cz.uhk.macroflow.pokemon.skills

/**
 * Kde na mapách stojí stromy ke kácení a rudné žíly (docs/adr/0035) – v pixelech obrázků
 * meadow.png a mountains.png (688 × 1536). Obdélníky kmenů a balvanů blokuje generátor
 * map chůze (MEADOW_FIX / MOUNTAIN_FIX); uzly v BiomeRegistry stojí těsně pod nimi.
 */
object GatherLayout {
    /** Střed spodní hrany dekorace (paty kmene / balvanu) a měřítko art pixelu. */
    data class Place(val baseX: Int, val baseY: Int, val artScale: Float)

    val PLACES: Map<GatherSpot, Place> = mapOf(
        GatherSpot.OAK to Place(127, 745, 3.6f),          // louka, nad pracovním stolem
        GatherSpot.COPPER to Place(227, 1075, 3.4f),      // hory, údolí
        // jeskyně: art pixely mapy (měřítko 1 = stejný pixel jako jeskyně)
        GatherSpot.SILVER to Place(100, 338, 1f),         // Starý důl – ve stěně nad štolou
        GatherSpot.GOLD to Place(118, 262, 1f),           // Mechová jeskyně – prostřední síň vpravo
        GatherSpot.BIRCH to Place(ForestSpots.BIRCH_X, ForestSpots.BIRCH_Y, 1f),
        GatherSpot.MAPLE to Place(ForestSpots.MAPLE_X, ForestSpots.MAPLE_Y, 1f)
    )

    /** Rozměr obrázku mapy lokace, ve kterém jsou souřadnice [PLACES]. */
    fun imageSize(biome: String): Pair<Int, Int> = when (biome) {
        "CAVE_MAZE" -> 150 to 480
        "CAVE_OPEN" -> 150 to 440
        "FOREST" -> ForestSpots.W to ForestSpots.H
        else -> 688 to 1536
    }
}

/** Místa kácení v Hvozdu – musí sedět s tools/mapgen/gen_forest.py (GATHER). */
object ForestSpots {
    const val W = 300
    const val H = 600
    const val BIRCH_X = 238
    const val BIRCH_Y = 470
    const val MAPLE_X = 232
    const val MAPLE_Y = 100
}
