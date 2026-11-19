package cz.uhk.macroflow.pokemon.cave

/**
 * Kalibrační louka – 3. patro Věže Řádu Váhy, patro normálních Makromonů. Umělá louka pod lampou místo slunce,
 * namalované nebe s odlupující se malbou, měřicí čtverce z provázků, strašák v úřednickém plášti.
 * Ve vysokých trávách se potkávají divocí Makromoni typu NORMAL (SpawnManager, TYPED_FLOORS).
 * Uzly kopírují tools/mapgen/gen_plain.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.PLAIN.
 */
object TowerPlain {
    const val ELEVATOR_NODE = "plan_vytah"
    val GRASS_NODES = setOf("plan_trava_1", "plan_trava_2", "plan_trava_3")

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 104), CaveNode("plan_stred", 88, 180), CaveNode("plan_trava_1", 36, 206),
            CaveNode("plan_posed", 146, 212), CaveNode("plan_strasak", 42, 272), CaveNode("plan_trava_2", 140, 278),
            CaveNode("plan_trava_3", 38, 332), CaveNode("plan_seno", 142, 334), CaveNode("plan_kolik", 60, 160),
            CaveNode("plan_malba", 150, 104), CaveNode("plan_lavicka", 34, 138), CaveNode("plan_stromek", 130, 140)
        ),
        edges = listOf(ELEVATOR_NODE to "plan_stred", ELEVATOR_NODE to "plan_malba", "plan_stred" to "plan_kolik",
            "plan_stred" to "plan_trava_1", "plan_stred" to "plan_posed", "plan_stred" to "plan_strasak",
            "plan_stred" to "plan_trava_2", "plan_strasak" to "plan_trava_3", "plan_trava_2" to "plan_seno",
            "plan_stred" to "plan_lavicka", "plan_stred" to "plan_stromek"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = GRASS_NODES,
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 66, 22), "plan_trava_1" to Triple(36, 186, 18), "plan_trava_2" to Triple(140, 258, 18),
            "plan_trava_3" to Triple(38, 312, 18), "plan_posed" to Triple(146, 172, 16), "plan_strasak" to Triple(42, 236, 16),
            "plan_seno" to Triple(142, 314, 14), "plan_kolik" to Triple(53, 146, 8), "plan_malba" to Triple(160, 20, 14),
            "plan_lavicka" to Triple(34, 122, 14), "plan_stromek" to Triple(146, 116, 14)
        )
    )

    val TEXTS = mapOf(
        "plan_posed" to "🪜 Pozorovatelna. Nahoře na sedátku leží desky a tužka. Do tabulky někdo zapisoval, kolik Makromonů v kterém čtverci spí.\n" +
            "Poslední řádek: „Čtverec B2 – nespí nikdo.“",
        "plan_strasak" to "🧥 Strašák v úřednickém plášti a buřince. Místo tváře pytel s natištěným číslem 213.\nV ruce desky. Na nich tvoje jméno, zatím bez kolonek.",
        "plan_seno" to "🌾 Balík sena. Voní jako skutečné léto. Je to jediná věc na tomhle patře, která není z úřadu.",
        "plan_kolik" to "📍 Kolík s cedulkou A1. Provázky dělí louku na čtverce: A1, A2, B1, B2… Každý čtverec má svůj počet Makromonů a svou hodinu spánku.",
        "plan_malba" to "🎨 Nebe je jen malba. V rohu se odlupuje a pod ní jsou kachle. Na jedné kachli je tužkou: „Okno bylo tady.“",
        "plan_lavicka" to "🪑 Lavička čelem k namalovanému nebi. Sedí se na ní dobře – jen slunce nehřeje. Je to lampa.",
        "plan_stromek" to "🌳 Stromek v květináči. Listy jsou z plastu, na větvi visí cedulka s inventárním číslem. Ptáci na něm stejně sedají."
    )

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}
