package cz.uhk.macroflow.pokemon

import android.graphics.PointF
import androidx.annotation.DrawableRes
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.cave.CaveMap
import cz.uhk.macroflow.pokemon.cave.CaveMaps
import cz.uhk.macroflow.pokemon.quests.QuestRegistry

enum class BiomeType {
    TOWN, MEADOW, MOUNTAINS, LAKE, WATER,
    /** Jeskyně v Horách (docs/adr/0013) – mapy větší než obrazovka s pohyblivou kamerou. */
    CAVE_OPEN, CAVE_MAZE,
    /** Hvozd nad loukou (docs/adr/0015) – bludiště palouků, stejná kamera jako jeskyně. */
    FOREST
}

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
        MovementEngine.Waypoint("rozcesti",      PointF(0.500f, 0.425f), listOf("vstup_z_town", "krovi1", "krovi2", "voda", "meadow_npc", "hory", "cesta_sever")),
        // Cesta nahoru do Hvozdu (zamčeno: 5 splněných fází úkolů)
        MovementEngine.Waypoint("cesta_sever",   PointF(0.500f, 0.280f), listOf("rozcesti", "les_sever")),
        MovementEngine.Waypoint("les_sever",     PointF(0.470f, 0.090f), listOf("cesta_sever")),

        MovementEngine.Waypoint("meadow_npc",         PointF(0.630f, 0.410f), listOf("rozcesti")),

        MovementEngine.Waypoint("krovi1",        PointF(0.380f, 0.425f), listOf("rozcesti")),
        MovementEngine.Waypoint("krovi2",        PointF(0.630f, 0.270f), listOf("rozcesti")),
        MovementEngine.Waypoint("voda",        PointF(0.255f, 0.320f), listOf("rozcesti")),

        // Můstek na pravém okraji louky = vstup do hor (zamčený denním krokovým cílem)
        MovementEngine.Waypoint("hory",          PointF(0.765f, 0.432f), listOf("rozcesti"))
    )

    // Mapa `mountains.png` je generovaná skriptem tools/mapgen/gen_mountains.py –
    // souřadnice níže jsou ZDROJ PRAVDY i pro skript (slovník N). Při změně upravit obojí.
    val MOUNTAINS_GRAPH = listOf(
        MovementEngine.Waypoint("vstup_z_meadow", PointF(0.500f, 0.960f), listOf("rozcesti_hory")),
        MovementEngine.Waypoint("rozcesti_hory",  PointF(0.500f, 0.820f), listOf("vstup_z_meadow", "camp", "kral_mlsak")),
        MovementEngine.Waypoint("camp",           PointF(0.300f, 0.780f), listOf("rozcesti_hory")),
        // Hráč stojí na plošině před sochou krále
        MovementEngine.Waypoint("kral_mlsak",     PointF(0.500f, 0.625f), listOf("rozcesti_hory", "zapadni_stezka", "skaly2")),
        MovementEngine.Waypoint("skaly2",         PointF(0.735f, 0.655f), listOf("kral_mlsak")),
        MovementEngine.Waypoint("zapadni_stezka", PointF(0.300f, 0.505f), listOf("kral_mlsak", "mine", "horni_stezka")),
        MovementEngine.Waypoint("mine",           PointF(0.130f, 0.470f), listOf("zapadni_stezka")),
        MovementEngine.Waypoint("horni_stezka",   PointF(0.500f, 0.335f), listOf("zapadni_stezka", "skaly1", "cave", "peak")),
        MovementEngine.Waypoint("skaly1",         PointF(0.270f, 0.300f), listOf("horni_stezka")),
        MovementEngine.Waypoint("cave",           PointF(0.790f, 0.345f), listOf("horni_stezka")),
        MovementEngine.Waypoint("peak",           PointF(0.500f, 0.150f), listOf("horni_stezka"))
    )

    /** Vše, co mapa potřebuje o biomu vědět, na jednom místě. */
    data class BiomeDefinition(
        val type: BiomeType,
        @DrawableRes val backgroundRes: Int,
        val graph: List<MovementEngine.Waypoint>,
        /** Quest, který se v biomu načte; null = ponechat aktuálně aktivní quest. */
        val questId: String?,
        /** Zobrazit ukazatel denních kroků (cíl = zámek dalšího biomu). */
        val stepGoalFor: BiomeType? = null,
        /** Jeskyně: mapa větší než obrazovka, kamera jede za postavou. null = mapa přes celou obrazovku. */
        val cave: CaveMap? = null
    )

    val DEFINITIONS: Map<BiomeType, BiomeDefinition> by lazy {
        listOf(
            BiomeDefinition(BiomeType.TOWN, R.drawable.poketown, TOWN_GRAPH, QuestRegistry.TOWN_INTRO_QUEST.id),
            BiomeDefinition(BiomeType.MEADOW, R.drawable.meadow, MEADOW_GRAPH, QuestRegistry.MEADOW_QUEST.id,
                stepGoalFor = BiomeType.MOUNTAINS),
            BiomeDefinition(BiomeType.MOUNTAINS, R.drawable.mountains, MOUNTAINS_GRAPH, QuestRegistry.MOUNTAINS_QUEST.id),
            BiomeDefinition(BiomeType.CAVE_OPEN, R.drawable.cave_open, graphOf(CaveMaps.OPEN), questId = null,
                cave = CaveMaps.OPEN),
            BiomeDefinition(BiomeType.CAVE_MAZE, R.drawable.cave_maze, graphOf(CaveMaps.MAZE), questId = null,
                cave = CaveMaps.MAZE),
            BiomeDefinition(BiomeType.FOREST, R.drawable.forest, graphOf(cz.uhk.macroflow.pokemon.cave.ForestMap.MAP), questId = null,
                cave = cz.uhk.macroflow.pokemon.cave.ForestMap.MAP)
        ).associateBy { it.type }
    }

    /** Navigační graf jeskyně z art souřadnic (sousedé obousměrně podle hran). */
    fun graphOf(cave: CaveMap): List<MovementEngine.Waypoint> = cave.nodes.map { n ->
        MovementEngine.Waypoint(n.id, PointF(n.x.toFloat() / cave.artW, n.y.toFloat() / cave.artH), cave.neighbors(n.id))
    }

    /** Jeskyně, do které vede uzel v Horách („cave“, „mine“), nebo null. */
    fun caveBehind(mountainNode: String): BiomeType? =
        DEFINITIONS.values.firstOrNull { it.cave?.isCave == true && it.cave.mountainNode == mountainNode }?.type

    fun definition(type: BiomeType): BiomeDefinition? = DEFINITIONS[type]

    fun nodePos(graph: List<MovementEngine.Waypoint>, id: String): PointF? = graph.find { it.id == id }?.pos
}