package cz.uhk.macroflow.pokemon.cave

/**
 * Zarostlé patro – 4. patro Věže Řádu Váhy, patro listových Makromonů. Les prorostl úřadem: kořeny zvedly
 * dlaždice, kartotéky obrostl břečťan, výtah je zelený od mechu. V kapradí se potkávají divocí Makromoni
 * typu GRASS (SpawnManager.TYPED_FLOORS). Uzly kopírují tools/mapgen/gen_forest.py – ZDROJ PRAVDY je v obou místech.
 * Živé detaily: InteriorFxView.FOREST.
 */
object TowerForest {
    const val ELEVATOR_NODE = "les_vytah"
    val FERN_NODES = setOf("les_krovi_1", "les_krovi_2", "les_krovi_3")

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 98), CaveNode("les_stred", 88, 172), CaveNode("les_krovi_1", 66, 222),
            CaveNode("les_krovi_2", 112, 280), CaveNode("les_krovi_3", 70, 336), CaveNode("les_dutina", 130, 226),
            CaveNode("les_kmen", 100, 318), CaveNode("les_kartoteka", 70, 142), CaveNode("les_houby", 106, 182),
            CaveNode("les_cedulka", 62, 300)
        ),
        edges = listOf(ELEVATOR_NODE to "les_stred", ELEVATOR_NODE to "les_kartoteka", "les_stred" to "les_houby",
            "les_stred" to "les_krovi_1", "les_stred" to "les_dutina", "les_dutina" to "les_krovi_2",
            "les_krovi_1" to "les_krovi_2", "les_krovi_2" to "les_kmen", "les_kmen" to "les_cedulka", "les_kmen" to "les_krovi_3"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = FERN_NODES,
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 62, 22), "les_krovi_1" to Triple(66, 200, 18), "les_krovi_2" to Triple(112, 258, 18),
            "les_krovi_3" to Triple(70, 314, 18), "les_dutina" to Triple(143, 186, 10), "les_kmen" to Triple(100, 298, 22),
            "les_kartoteka" to Triple(64, 112, 16), "les_houby" to Triple(106, 170, 8), "les_cedulka" to Triple(64, 282, 8)
        )
    )

    val TEXTS = mapOf(
        "les_dutina" to "🌳 Dutina ve starém stromě. Uvnitř je tma – a chvílemi se v ní rozsvítí dvě oči. Než se stihneš podívat pořádně, jsou pryč.",
        "les_kmen" to "🪵 Padlý kmen porostlý mechem. Podél něj se táhne lesklá stopa. Někdo tu leze hodně, hodně pomalu.",
        "les_kartoteka" to "🗄️ Kartotéka obrostlá břečťanem. Šuplíky nejdou otevřít, kořeny je drží zevnitř. Na štítku: „ROZRŮST – SLEDOVÁNO“.",
        "les_houby" to "🍄 Trs červených hub s bílými tečkami. Rostou v kruhu kolem dlaždice s číslem 4.",
        "les_cedulka" to "🪧 Cedulka zapíchnutá v mechu: „POZOR – POMALÝ PROVOZ.“ Úřad tu evidoval i slimáky."
    )

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}
