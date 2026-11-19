package cz.uhk.macroflow.pokemon.cave

/**
 * Měřírna – 2. patro Věže Řádu Váhy. Studená úřední místnost, kde se na velkých vahách váží spisy (ne těla),
 * měřicí kabiny „Měření spánku. Nerušit.“, prázdné očíslované židle, přesýpací hodiny, graf SPÁNEK OKRUHŮ.
 * Uzly kopírují tools/mapgen/gen_measure.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.MEASURE.
 */
object TowerMeasure {
    const val ELEVATOR_NODE = "meri_vytah"

    val MAP = CaveMap(
        artW = 176, artH = 320,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 100), CaveNode("meri_stred", 88, 140), CaveNode("meri_kabina_l", 30, 160),
            CaveNode("meri_kabina_p", 146, 160), CaveNode("meri_vahy", 88, 232), CaveNode("meri_zidle", 40, 298),
            CaveNode("meri_zavazi", 144, 262), CaveNode("meri_stul", 100, 300), CaveNode("meri_graf", 48, 100),
            CaveNode("meri_posta", 130, 100), CaveNode("meri_hodiny", 148, 212), CaveNode("meri_kartoteka", 34, 232)
        ),
        edges = listOf(ELEVATOR_NODE to "meri_stred", ELEVATOR_NODE to "meri_graf", ELEVATOR_NODE to "meri_posta",
            "meri_stred" to "meri_kabina_l", "meri_stred" to "meri_kabina_p", "meri_stred" to "meri_vahy",
            "meri_vahy" to "meri_zidle", "meri_vahy" to "meri_zavazi", "meri_vahy" to "meri_stul",
            "meri_vahy" to "meri_hodiny", "meri_vahy" to "meri_kartoteka"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 62, 22), "meri_kabina_l" to Triple(30, 124, 18), "meri_kabina_p" to Triple(146, 124, 18),
            "meri_vahy" to Triple(88, 190, 26), "meri_zidle" to Triple(38, 260, 22), "meri_zavazi" to Triple(144, 236, 18),
            "meri_stul" to Triple(136, 276, 24), "meri_graf" to Triple(48, 42, 14), "meri_posta" to Triple(131, 66, 14),
            "meri_hodiny" to Triple(148, 184, 12), "meri_kartoteka" to Triple(34, 206, 20)
        )
    )

    val TEXTS = mapOf(
        "meri_vahy" to "⚖️ Velké váhy. Na misky se tu nepokládají lidé, ale jejich spisy. Na levé leží jedna tenká složka s razítkem, na pravé štos.\n" +
            "Rameno se naklání ke štosu. Tenká složka má na hřbetu číslo 212.",
        "meri_kabina_l" to "🚪 Měřicí kabina č. 1. Závěs je zatažený. Uvnitř jen stolička a nad ní hodiny bez ručiček.\nCedulka: „Měření spánku. Nerušit.“",
        "meri_kabina_p" to "🚪 Kabina č. 2. Kontrolka OBSAZENO se občas rozsvítí, i když uvnitř nikdo není.",
        "meri_zidle" to "🪑 Řady očíslovaných židlí. Na každé leží pořadový lístek: 205, 206, 207… Nikdo si pro ně nepřišel.",
        "meri_zavazi" to "⚖️ Stojan se závažími. Jedno místo v sametu je prázdné – otisk je ještě čerstvý.",
        "meri_stul" to "🗒️ Stůl úředníka, který tu „právě není“. Ve stroji je rozepsaný formulář: SUBJEKT 213 – POČET NOCÍ: ___.\nMetronom tiká dál.",
        "meri_graf" to "📉 Graf na zdi: SPÁNEK OKRUHŮ. Křivka klesá okruh za okruhem – a pod červenou čárou normy už nepokračuje.",
        "meri_posta" to "📮 Stanice potrubní pošty. Zelená kontrolka svítí jen u trubky, která vede dolů. Pod přízemí.",
        "meri_hodiny" to "⏳ Přesýpací hodiny. Písek je skoro celý dole. Kdo je otočí, až doteče?",
        "meri_kartoteka" to "🗄️ Kartotéka měření. Na šuplíku 213 je nový štítek a pod ním tužkou: „Spánek – sledovat.“"
    )

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}
