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
        GatherSpot.OAK to Place(205, 630, 3.6f),
        GatherSpot.BIRCH to Place(490, 520, 3.6f),
        GatherSpot.MAPLE to Place(69, 676, 3.6f),
        GatherSpot.COPPER to Place(227, 1075, 3.4f),
        GatherSpot.SILVER to Place(450, 1213, 3.4f),
        GatherSpot.GOLD to Place(440, 645, 3.4f)
    )
}
