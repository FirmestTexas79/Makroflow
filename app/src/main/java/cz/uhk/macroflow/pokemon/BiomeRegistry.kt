package cz.uhk.macroflow.pokemon

import android.graphics.PointF

enum class BiomeType { TOWN, MEADOW, MOUNTAINS, LAKE }

data class HotspotData(
    val id: Int,
    val nodeName: String,
    val type: HotspotType,
    val targetBiome: BiomeType? = null
)

enum class HotspotType { ACTION, TRANSITION, BUSH }

object BiomeRegistry {
    // Definice biomu LOUKA
    val TOWN_GRAPH = listOf(
        MovementEngine.Waypoint("les",        PointF(0.460f, 0.120f), listOf("spawn")),
        MovementEngine.Waypoint("spawn",      PointF(0.480f, 0.275f), listOf("les", "krizovatka_hlavni")),
        MovementEngine.Waypoint("krizovatka_hlavni", PointF(0.480f, 0.340f), listOf("spawn", "rozbocka_zapad", "prah_pokedex")),
        MovementEngine.Waypoint("prah_pokedex", PointF(0.650f, 0.340f), listOf("krizovatka_hlavni", "pokedex")),
        MovementEngine.Waypoint("pokedex",      PointF(0.650f, 0.300f), listOf("prah_pokedex")),
        MovementEngine.Waypoint("rozbocka_zapad", PointF(0.370f, 0.340f), listOf("krizovatka_hlavni", "prah_domova", "roh_obchod")),
        MovementEngine.Waypoint("prah_domova",    PointF(0.255f, 0.340f), listOf("rozbocka_zapad", "domov")),
        MovementEngine.Waypoint("domov",          PointF(0.255f, 0.300f), listOf("prah_domova")),
        MovementEngine.Waypoint("roh_obchod",     PointF(0.370f, 0.505f), listOf("rozbocka_zapad", "prah_obchodu")),
        MovementEngine.Waypoint("prah_obchodu",   PointF(0.640f, 0.505f), listOf("roh_obchod", "obchod")),
        MovementEngine.Waypoint("obchod",         PointF(0.640f, 0.480f), listOf("prah_obchodu"))
    )

    // Kompletní graf pro louku (MEADOW)
    val MEADOW_GRAPH = listOf(
        MovementEngine.Waypoint("vstup_z_town", PointF(0.500f, 0.900f), listOf("rozcesti")),
        MovementEngine.Waypoint("rozcesti",      PointF(0.500f, 0.500f), listOf("vstup_z_town", "krovi1", "krovi2", "krovi3")),
        MovementEngine.Waypoint("krovi1",        PointF(0.200f, 0.450f), listOf("rozcesti")),
        MovementEngine.Waypoint("krovi2",        PointF(0.800f, 0.450f), listOf("rozcesti")),
        MovementEngine.Waypoint("krovi3",        PointF(0.500f, 0.250f), listOf("rozcesti"))
    )
}