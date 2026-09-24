package cz.uhk.macroflow.pokemon.cave

/**
 * Jeskyně v Horách jako čistá data (bez Androidu, pokryto testy). Podklady v docs/adr/0013.
 *
 * Souřadnice uzlů jsou v „art pixelech“ mapy vygenerované skriptem tools/mapgen/gen_caves.py
 * (slovník `nodes` tamtéž) – ZDROJ PRAVDY je v obou místech, při změně upravit obojí.
 * BiomeRegistry z nich staví navigační graf (zlomky šířky/výšky).
 */
data class CaveNode(val id: String, val x: Int, val y: Int)

data class CaveMap(
    val artW: Int,
    val artH: Int,
    val nodes: List<CaveNode>,
    val edges: List<Pair<String, String>>,
    /** Uzel u ústí – odtud se vychází zpět do Hor. */
    val exitNode: String,
    /** Uzel v Horách, kam se hráč po odchodu vrátí. */
    val mountainNode: String,
    /** Uzel před oltářem s krystalem na konci jeskyně. */
    val crystalNode: String,
    val crystal: CrystalColor,
    /** Místa, kde může vyskočit divoký Makromon. */
    val encounterNodes: Set<String>,
    /** Kolik art pixelů je vidět na šířku obrazovky (přiblížení kamery). */
    val artPixelsAcross: Int = 120
) {
    private val byId = nodes.associateBy { it.id }

    fun node(id: String): CaveNode? = byId[id]

    fun neighbors(id: String): List<String> =
        edges.mapNotNull { (a, b) -> if (a == id) b else if (b == id) a else null }

    /** Všechny uzly dosažitelné z [start] (BFS). */
    fun reachableFrom(start: String): Set<String> {
        val seen = linkedSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            for (n in neighbors(queue.removeFirst())) if (seen.add(n)) queue.addLast(n)
        }
        return seen
    }

    /** Relativní pozice uzlu (zlomek šířky/výšky mapy). */
    fun relative(id: String): Pair<Float, Float>? = node(id)?.let { it.x.toFloat() / artW to it.y.toFloat() / artH }

    /** Pata krystalu na oltáři v art pixelech (oltář je [ALTAR_ABOVE] px nad uzlem). */
    val crystalBase: Pair<Int, Int> get() = node(crystalNode)!!.let { it.x to it.y - ALTAR_ABOVE - 2 }

    companion object {
        const val ALTAR_ABOVE = 16
    }
}

object CaveMaps {

    /** Mechová jeskyně – otevřená síň ve třech patrech, modrý krystal. Vchod: „cave“ v Horách. */
    val OPEN = CaveMap(
        artW = 240, artH = 400,
        nodes = listOf(
            CaveNode("vychod_jeskyne", 120, 388),
            CaveNode("sal", 120, 318),
            CaveNode("jezirko", 62, 296),
            CaveNode("balvany_j", 192, 300),
            CaveNode("pata_schodu", 120, 270),
            CaveNode("terasa", 120, 206),
            CaveNode("houby", 46, 176),
            CaveNode("krystaly_j", 194, 162),
            CaveNode("pata_schodu2", 128, 124),
            CaveNode("krystal_modry", 120, 58)
        ),
        edges = listOf(
            "vychod_jeskyne" to "sal", "sal" to "jezirko", "sal" to "balvany_j", "sal" to "pata_schodu",
            "pata_schodu" to "terasa", "terasa" to "houby", "terasa" to "krystaly_j",
            "terasa" to "pata_schodu2", "pata_schodu2" to "krystal_modry"
        ),
        exitNode = "vychod_jeskyne",
        mountainNode = "cave",
        crystalNode = "krystal_modry",
        crystal = CrystalColor.BLUE,
        encounterNodes = setOf("jezirko", "balvany_j", "houby", "krystaly_j")
    )

    /** Starý důl – uzavřené bludiště štol se žebříky, červený krystal. Vchod: „mine“ v Horách. */
    val MAZE = CaveMap(
        artW = 240, artH = 440,
        nodes = listOf(
            CaveNode("vychod_dolu", 40, 424),
            CaveNode("stola_vstup", 40, 372),
            CaveNode("stola_kriz", 120, 372),
            CaveNode("vozik", 204, 372),
            CaveNode("zebrik1", 120, 300),
            CaveNode("tezba", 204, 300),
            CaveNode("chodba_zapad", 40, 300),
            CaveNode("chodba_sever", 40, 232),
            CaveNode("rozcesti_dul", 120, 232),
            CaveNode("netopyri", 204, 232),
            CaveNode("zebrik2", 120, 162),
            CaveNode("slepa_chodba", 40, 162),
            CaveNode("horni_stola", 204, 162),
            CaveNode("hlubina", 204, 94),
            CaveNode("sin_krystalu", 120, 94),
            CaveNode("krystal_cerveny", 120, 56)
        ),
        edges = listOf(
            "vychod_dolu" to "stola_vstup", "stola_vstup" to "stola_kriz", "stola_kriz" to "vozik",
            "stola_kriz" to "zebrik1",
            "zebrik1" to "tezba", "zebrik1" to "chodba_zapad", "chodba_zapad" to "chodba_sever",
            "chodba_sever" to "rozcesti_dul", "rozcesti_dul" to "netopyri",
            "rozcesti_dul" to "zebrik2",
            "zebrik2" to "slepa_chodba", "zebrik2" to "horni_stola", "horni_stola" to "hlubina",
            "hlubina" to "sin_krystalu", "sin_krystalu" to "krystal_cerveny"
        ),
        exitNode = "vychod_dolu",
        mountainNode = "mine",
        crystalNode = "krystal_cerveny",
        crystal = CrystalColor.RED,
        encounterNodes = setOf("vozik", "netopyri", "slepa_chodba", "hlubina")
    )

    val ALL = listOf(OPEN, MAZE)
}
