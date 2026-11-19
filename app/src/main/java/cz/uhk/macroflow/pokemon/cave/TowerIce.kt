package cz.uhk.macroflow.pokemon.cave

/**
 * Mrazírna – 9. patro Věže Řádu Váhy, ledové patro. Rozbitým oknem dovnitř sněží, za ním polární záře. Hibernační
 * komory, ve kterých pod námrazou spí „kalibrovaní“ – spánek, který nekončí. V závějích se potkává Glacirra
 * (SpawnManager.TYPED_FLOORS, typ LED), jen vzácně (MakromonMapActivity: nižší šance setkání).
 * Uzly kopírují tools/mapgen/gen_ice.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.ICE.
 */
object TowerIce {
    const val ELEVATOR_NODE = "led_vytah"
    val DRIFT_NODES = setOf("led_zavej_1", "led_zavej_2", "led_zavej_3")

    /** Šance (v %), že ze závěje něco vyleze – Glacirra má být vzácná. */
    const val ENCOUNTER_CHANCE = 35

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 104), CaveNode("led_stred", 88, 214), CaveNode("led_komory_l", 46, 206),
            CaveNode("led_komory_p", 130, 206), CaveNode("led_zavej_1", 34, 292), CaveNode("led_zavej_2", 142, 280),
            CaveNode("led_zavej_3", 88, 300), CaveNode("led_okno", 36, 108), CaveNode("led_teplomer", 146, 106),
            CaveNode("led_blok", 136, 332), CaveNode("led_skrine", 88, 266)
        ),
        edges = listOf(ELEVATOR_NODE to "led_stred", ELEVATOR_NODE to "led_okno", ELEVATOR_NODE to "led_teplomer",
            "led_stred" to "led_komory_l", "led_stred" to "led_komory_p", "led_stred" to "led_zavej_3",
            "led_zavej_3" to "led_zavej_1", "led_zavej_3" to "led_zavej_2", "led_zavej_3" to "led_blok",
            "led_stred" to "led_skrine"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = DRIFT_NODES,
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 66, 22), "led_komory_l" to Triple(46, 156, 30), "led_komory_p" to Triple(130, 156, 30),
            "led_okno" to Triple(36, 40, 14), "led_teplomer" to Triple(146, 45, 10), "led_blok" to Triple(136, 309, 18),
            "led_skrine" to Triple(88, 236, 22)
        )
    )

    val TEXTS = mapOf(
        "led_komory_l" to "❄️ Hibernační komory 206–208. Pod námrazou na skle se rýsují spáči. Kontrolky svítí zeleně: STAV – SPÁNEK.\nPod každou komorou je vyraženo: KALIBRACE DOKONČENA.",
        "led_komory_p" to "❄️ Komory 209–211 spí stejně. Krajní komora je prázdná, kontrolka bliká červeně. Na štítku: 212.\nNa skle jsou zevnitř otisky dlaní.",
        "led_okno" to "🌌 Rozbitá tabule. Dovnitř fouká sníh a venku se vlní polární záře.\nVěž přece stojí uprostřed města. Odkud je tenhle sníh?",
        "led_teplomer" to "🌡️ Teploměr ukazuje −21 °C. Rtuť se nehne, ani když na baňku dýchneš. Pod ním vyrytý nápis: CHLAD ZACHOVÁVÁ.",
        "led_blok" to "🧊 V bloku ledu je uvězněný tvor se svítícím okem. Když se pohneš, oko tě sleduje.",
        "led_skrine" to "🗄️ Kartotéky přimrzlé k podlaze. Šuplík SPÁNEK – DLOUHODOBÝ jde pootevřít. Karty zamrzly v jeden blok, čitelná je jen poslední: SUBJEKT 213 – KOMORA PŘIPRAVENA."
    )

    val EMPTY_TEXT = "Ze závěje se zvedl jen poprašek sněhu. Zkus to znovu."

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}
