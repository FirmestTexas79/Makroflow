package cz.uhk.macroflow.pokemon

import android.graphics.PointF

enum class BiomeType { TOWN, MEADOW, MOUNTAINS, LAKE, WATER }

object BiomeRegistry {
    val TOWN_GRAPH = listOf(
        MovementEngine.Waypoint("les",        PointF(0.455f, 0.150f), listOf("spawn", "starter_bush")),
        MovementEngine.Waypoint("spawn",      PointF(0.480f, 0.275f), listOf("les", "krizovatka_hlavni")),
        MovementEngine.Waypoint("krizovatka_hlavni", PointF(0.480f, 0.340f), listOf("spawn", "rozbocka_zapad", "prah_pokedex")),
        MovementEngine.Waypoint("prah_pokedex", PointF(0.690f, 0.340f), listOf("krizovatka_hlavni", "pokedex")),
        MovementEngine.Waypoint("pokedex",      PointF(0.690f, 0.300f), listOf("prah_pokedex")),
        MovementEngine.Waypoint("rozbocka_zapad", PointF(0.370f, 0.340f), listOf("krizovatka_hlavni", "prah_domova", "roh_obchod")),
        MovementEngine.Waypoint("prah_domova",    PointF(0.200f, 0.340f), listOf("rozbocka_zapad", "domov")),
        MovementEngine.Waypoint("domov",          PointF(0.200f, 0.300f), listOf("prah_domova")),
        MovementEngine.Waypoint("roh_obchod",     PointF(0.370f, 0.505f), listOf("rozbocka_zapad", "prah_obchodu", "gudwin")),



        MovementEngine.Waypoint("gudwin",         PointF(0.120f, 0.520f), listOf("roh_obchod")),

        MovementEngine.Waypoint("starter_bush", PointF(0.200f, 0.170f), listOf("les")),

        MovementEngine.Waypoint("prah_obchodu",   PointF(0.700f, 0.505f), listOf("roh_obchod", "obchod")),
        MovementEngine.Waypoint("obchod",         PointF(0.700f, 0.480f), listOf("prah_obchodu"))
    )

    val MEADOW_GRAPH = listOf(
        MovementEngine.Waypoint("vstup_z_town", PointF(0.340f, 0.640f), listOf("rozcesti")),
        MovementEngine.Waypoint("rozcesti",      PointF(0.500f, 0.425f), listOf("vstup_z_town", "krovi1", "krovi2", "voda")),

        MovementEngine.Waypoint("meadow_npc",         PointF(0.630f, 0.350f), listOf("rozcesti")),

        MovementEngine.Waypoint("krovi1",        PointF(0.380f, 0.425f), listOf("rozcesti")),
        MovementEngine.Waypoint("krovi2",        PointF(0.630f, 0.270f), listOf("rozcesti")),
        MovementEngine.Waypoint("voda",        PointF(0.255f, 0.320f), listOf("rozcesti"))
    )
}