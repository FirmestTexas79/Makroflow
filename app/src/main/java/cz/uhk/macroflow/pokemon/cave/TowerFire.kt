package cz.uhk.macroflow.pokemon.cave

/**
 * Spalovna spisů – 10. patro Věže Řádu Váhy, ohnivé patro. Sem skluzem padají spisy, které Řád „uzavřel“, a končí
 * ve velké peci. Uhlí jsou slisované stránky, parní potrubí topí pamětí. V kupách popela se potkávají divocí
 * Makromoni typu OHEŇ (SpawnManager.TYPED_FLOORS). Navazuje na fázi Ohořelé stránky (Prosévání popela).
 * Uzly kopírují tools/mapgen/gen_fire.py – ZDROJ PRAVDY je v obou místech. Živé detaily: InteriorFxView.FIRE.
 */
object TowerFire {
    const val ELEVATOR_NODE = "ohen_vytah"
    val ASH_NODES = setOf("ohen_popel_1", "ohen_popel_2", "ohen_popel_3")

    val MAP = CaveMap(
        artW = 176, artH = 352,
        nodes = listOf(
            CaveNode(ELEVATOR_NODE, 88, 104), CaveNode("ohen_stred", 88, 196), CaveNode("ohen_pec", 88, 320),
            CaveNode("ohen_vozik", 138, 150), CaveNode("ohen_ventil", 30, 106), CaveNode("ohen_uhli", 40, 238),
            CaveNode("ohen_popel_1", 34, 292), CaveNode("ohen_popel_2", 142, 292), CaveNode("ohen_popel_3", 100, 212)
        ),
        edges = listOf(ELEVATOR_NODE to "ohen_stred", ELEVATOR_NODE to "ohen_ventil", ELEVATOR_NODE to "ohen_vozik",
            "ohen_stred" to "ohen_uhli", "ohen_stred" to "ohen_popel_3", "ohen_popel_3" to "ohen_popel_2",
            "ohen_uhli" to "ohen_popel_1", "ohen_popel_1" to "ohen_pec", "ohen_popel_2" to "ohen_pec",
            "ohen_popel_3" to "ohen_pec"),
        exitNode = ELEVATOR_NODE,
        mountainNode = TowerLobby.ELEVATOR_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = ASH_NODES,
        parentBiome = "TOWER_LOBBY",
        isCave = false,
        tapAreas = mapOf(
            ELEVATOR_NODE to Triple(88, 66, 22), "ohen_pec" to Triple(88, 262, 36), "ohen_vozik" to Triple(148, 110, 22),
            "ohen_ventil" to Triple(30, 60, 14), "ohen_uhli" to Triple(40, 216, 18)
        )
    )

    val TEXTS = mapOf(
        "ohen_pec" to "🔥 Pec. Na mosazném štítku: ZPRACOVÁNÍ ZÁZNAMŮ. V plamenech se kroutí složky. Na jedné stihneš přečíst OKRUH 187 – UZAVŘENO, pak zčerná.\nŽár ti pálí oči, ale nemůžeš se přestat dívat.",
        "ohen_vozik" to "🗂️ Vozík pod skluzem. Složky s čísly okruhů, na každé razítko UZAVŘENO. Navrchu leží ta nejnovější: bez čísla, jen s prázdným řádkem na jméno.",
        "ohen_ventil" to "♨️ Parní potrubí. Ručička manometru se třese v červeném poli. Někdo na trubku napsal křídou: TOPÍ SE PAMĚTÍ.",
        "ohen_uhli" to "⛏️ Hromada uhlí s lopatou. Hrudky jsou divně lehké. Když jednu rozlomíš, uvnitř jsou slisované stránky – řádky písma stlačené do černé."
    )

    const val EMPTY_TEXT = "Z popela vyletělo pár jisker. Zkus to znovu."

    val ACTION_NODES = TEXTS.keys + ELEVATOR_NODE
}
