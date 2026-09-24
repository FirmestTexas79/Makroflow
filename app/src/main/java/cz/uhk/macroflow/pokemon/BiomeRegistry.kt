package cz.uhk.macroflow.pokemon

import android.graphics.PointF
import androidx.annotation.DrawableRes
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.quests.QuestRegistry

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
        MovementEngine.Waypoint("rozcesti",      PointF(0.500f, 0.425f), listOf("vstup_z_town", "krovi1", "krovi2", "voda", "meadow_npc", "hory")),

        MovementEngine.Waypoint("meadow_npc",         PointF(0.630f, 0.410f), listOf("rozcesti")),

        MovementEngine.Waypoint("krovi1",        PointF(0.380f, 0.425f), listOf("rozcesti")),
        MovementEngine.Waypoint("krovi2",        PointF(0.630f, 0.270f), listOf("rozcesti")),
        MovementEngine.Waypoint("voda",        PointF(0.255f, 0.320f), listOf("rozcesti")),

        // Můstek na pravém okraji louky = vstup do hor (zamčený denním krokovým cílem)
        MovementEngine.Waypoint("hory",          PointF(0.765f, 0.432f), listOf("rozcesti"))
    )

    // ⚠️ PROVIZORNÍ souřadnice – sedí na placeholder pozadí `mountains.png`.
    // Až bude hotová pixel-art mapa hor, přepočítat podle ní (stejně jako Meadow: x/y = zlomek šířky/výšky obrázku).
    val MOUNTAINS_GRAPH = listOf(
        MovementEngine.Waypoint("vstup_z_meadow", PointF(0.500f, 0.850f), listOf("horska_stezka")),
        MovementEngine.Waypoint("horska_stezka",  PointF(0.500f, 0.620f), listOf("vstup_z_meadow", "skaly1", "skaly2", "vrchol")),
        MovementEngine.Waypoint("skaly1",         PointF(0.280f, 0.550f), listOf("horska_stezka")),
        MovementEngine.Waypoint("skaly2",         PointF(0.720f, 0.480f), listOf("horska_stezka")),
        // Místo pro budoucího NPC a quest hor (zatím neklikatelné)
        MovementEngine.Waypoint("vrchol",         PointF(0.500f, 0.300f), listOf("horska_stezka"))
    )

    /** Vše, co mapa potřebuje o biomu vědět, na jednom místě. */
    data class BiomeDefinition(
        val type: BiomeType,
        @DrawableRes val backgroundRes: Int,
        val graph: List<MovementEngine.Waypoint>,
        /** Quest, který se v biomu načte; null = ponechat aktuálně aktivní quest. */
        val questId: String?,
        /** Zobrazit ukazatel denních kroků (cíl = zámek dalšího biomu). */
        val stepGoalFor: BiomeType? = null
    )

    val DEFINITIONS: Map<BiomeType, BiomeDefinition> by lazy {
        listOf(
            BiomeDefinition(BiomeType.TOWN, R.drawable.poketown, TOWN_GRAPH, QuestRegistry.TOWN_INTRO_QUEST.id),
            BiomeDefinition(BiomeType.MEADOW, R.drawable.meadow, MEADOW_GRAPH, QuestRegistry.MEADOW_QUEST.id,
                stepGoalFor = BiomeType.MOUNTAINS),
            BiomeDefinition(BiomeType.MOUNTAINS, R.drawable.mountains, MOUNTAINS_GRAPH, questId = null)
        ).associateBy { it.type }
    }

    fun definition(type: BiomeType): BiomeDefinition? = DEFINITIONS[type]

    fun nodePos(graph: List<MovementEngine.Waypoint>, id: String): PointF? = graph.find { it.id == id }?.pos
}