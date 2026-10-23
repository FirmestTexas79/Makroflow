package cz.uhk.macroflow.pokemon.cave

/**
 * Vstupní hala Arény v Ozvěně (docs/adr/0092): velitel bojového cechu arénu vysvětlí, v Síni šampionů visí
 * portréty tří nejlepších hráčů žebříčku (Makromon jejich týmu), na nástěnce plakát HLEDÁ SE s Drakirrou.
 * Teprve branou na konci haly se jde do soubojů. Uzly kopírují tools/mapgen/gen_arena_hall.py.
 */
object ArenaHall {
    const val ENTRY_NODE = "vychod"
    const val GATE_NODE = "brana_areny"
    const val COMMANDER_NODE = "velitel"
    const val FAME_NODE = "sin_slavy"
    const val WANTED_NODE = "hledani"
    const val TRAINING_NODE = "trenink"
    const val COMMANDER_NAME = "Velitel Kassar"

    val MAP = CaveMap(
        artW = 176, artH = 288,
        nodes = listOf(
            CaveNode(ENTRY_NODE, 88, 278), CaveNode("stred", 88, 200), CaveNode(GATE_NODE, 88, 92),
            CaveNode(COMMANDER_NODE, 132, 182), CaveNode(FAME_NODE, 46, 166), CaveNode(WANTED_NODE, 36, 262),
            CaveNode(TRAINING_NODE, 136, 270)
        ),
        edges = listOf(ENTRY_NODE to "stred", "stred" to GATE_NODE, "stred" to COMMANDER_NODE, "stred" to FAME_NODE,
            "stred" to WANTED_NODE, "stred" to TRAINING_NODE),
        exitNode = ENTRY_NODE,
        mountainNode = OzvenaCity.ARENA_NODE,
        crystalNode = null,
        crystal = null,
        encounterNodes = emptySet(),
        parentBiome = "OZVENA",
        isCave = false,
        tapAreas = mapOf(
            ENTRY_NODE to Triple(88, 280, 16), GATE_NODE to Triple(88, 44, 26),
            COMMANDER_NODE to Triple(132, 150, 22), FAME_NODE to Triple(46, 128, 32),
            WANTED_NODE to Triple(38, 234, 22), TRAINING_NODE to Triple(140, 244, 22)
        )
    )

    val ACTION_NODES = setOf(ENTRY_NODE, GATE_NODE, COMMANDER_NODE, FAME_NODE, WANTED_NODE, TRAINING_NODE)

    /** Vnitřek rámů portrétů v Síni šampionů (art px mapy, x, y, šířka, výška) – 1., 2., 3. místo. */
    val PORTRAITS = listOf(intArrayOf(39, 107, 14, 18), intArrayOf(14, 111, 14, 18), intArrayOf(64, 111, 14, 18))

    /** Když žebříček není k dispozici (offline, nepřihlášený hráč), visí tu legendy cechu. */
    val LEGENDS = listOf(Triple("Kaia", "003", 1980), Triple("Orvo", "026", 1840), Triple("Ilvi", "021", 1715))

    /** Výklad velitele po stránkách. */
    val COMMANDER_PAGES = listOf(
        "Vítej v Aréně Ozvěny, poutníku. Jsem $COMMANDER_NAME, velitel bojového cechu. Kdo tu chce bojovat, projde nejdřív kolem mě.",
        "Branou za mnou se jde na písek. Přátelský zápas tě nic nestojí – zkusíš si tým a nic neztratíš. " +
            "Hodnocený zápas mění body v ranku: výhra přidá, prohra ubere. Čím výš jsi, tím silnější soupeři.",
        "Soupeři nejsou jen naši zápasníci. Aréna si pamatuje i ostatní poutníky – jejich týmy tu bojují jako ozvěny, " +
            "i když oni sami sedí doma. A tvůj tým tu bude bránit tebe, až budeš pryč. Obranu si nastavíš uvnitř.",
        "Kdo se drží v kondici, ten v aréně bije silněji – trénink nebo kardio za poslední den ti dá navrch. " +
            "A kdo má dnes splněná makra, toho první jed, spánek ani ochromení od soupeře nezasáhne. Tak to u nás chodí.",
        "Tři nejlepší visí v Síni šampionů vlevo. A ten plakát u dveří… Řád Váhy vypsal odměnu za dračici. " +
            "Prý ji viděli nad Hvozdem. Kdo ji přivede, toho čeká sláva, jakou tahle síň ještě nezažila."
    )

    const val WANTED_TEXT = "📜 HLEDÁ SE – DRAKIRRA\n„Rudá dračice z vrcholu Hor. Nebezpečná, na lidi neútočí, pokud není vyprovokována. " +
        "Kdo ji živou přivede do Ozvěny, obdrží odměnu 5 000 mincí a čestné místo v Síni šampionů.“\n" +
        "Dole je razítko se znakem vah a drobným písmem: „Objekt 019 – pro účely kalibrace.“"
    const val TRAINING_TEXT = "🥋 Tréninkový kout cechu. Figuríny jsou rozbité od tisíců úderů.\n" +
        "Trénink nebo kardio za posledních 24 hodin ti v aréně přidá +10 % útoku."
}
