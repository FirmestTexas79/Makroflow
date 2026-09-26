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
    /** Uzel u ústí – odtud se vychází zpět do nadřazené lokace. */
    val exitNode: String,
    /** Uzel v nadřazené lokaci ([parentBiome]), kam se hráč po odchodu vrátí. */
    val mountainNode: String,
    /** Uzel před oltářem s krystalem (jen jeskyně; les žádný krystal nemá). */
    val crystalNode: String?,
    val crystal: CrystalColor?,
    /** Místa, kde může vyskočit divoký Makromon. */
    val encounterNodes: Set<String>,
    /**
     * Kolik art pixelů je vidět na šířku obrazovky. Výchozí = celá šířka mapy: všechny body
     * jsou vodorovně vždy na obrazovce a dají se naklikat, kamera jezdí svisle.
     */
    val artPixelsAcross: Int = artW,
    /** Název BiomeType, do kterého vede východ (bez závislosti na Androidu). */
    val parentBiome: String = "MOUNTAINS",
    /** Jeskyně = tmavý mechový přechod a jeskynní intro; les = běžné prolnutí a křoví. */
    val isCave: Boolean = true
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

    /**
     * Pata krystalu v art pixelech: jeden pixel nad podstavcem oltáře (podstavec má horní řádek
     * 2 px nad středem oltáře, oltář je [ALTAR_ABOVE] px nad uzlem).
     */
    val crystalBase: Pair<Int, Int> get() = node(crystalNode!!)!!.let { it.x to it.y - ALTAR_ABOVE - 3 }

    companion object {
        const val ALTAR_ABOVE = 16
    }
}

object CaveMaps {

    /** Mechová jeskyně – otevřená síň ve třech patrech, modrý krystal. Vchod: „cave“ v Horách. */
    val OPEN = CaveMap(
        artW = 150, artH = 440,
        nodes = listOf(
            CaveNode("vychod_jeskyne", 75, 428),
            CaveNode("sal", 75, 370),
            CaveNode("jezirko", 38, 346),
            CaveNode("balvany_j", 116, 352),
            CaveNode("pata_schodu", 75, 318),
            CaveNode("terasa", 75, 256),
            CaveNode("houby", 34, 230),
            CaveNode("krystaly_j", 116, 214),
            CaveNode("pata_schodu2", 82, 170),
            CaveNode("krystal_modry", 75, 86),
            // Zlatá žíla (docs/adr/0035) – balvan vpravo v prostřední síni, stojí se pod ním
            CaveNode("zila_zlato", 118, 272)
        ),
        edges = listOf(
            "vychod_jeskyne" to "sal", "sal" to "jezirko", "sal" to "balvany_j", "sal" to "pata_schodu",
            "pata_schodu" to "terasa", "terasa" to "houby", "terasa" to "krystaly_j",
            "terasa" to "pata_schodu2", "pata_schodu2" to "krystal_modry", "terasa" to "zila_zlato"
        ),
        exitNode = "vychod_jeskyne",
        mountainNode = "cave",
        crystalNode = "krystal_modry",
        crystal = CrystalColor.BLUE,
        encounterNodes = setOf("jezirko", "balvany_j", "houby", "krystaly_j")
    )

    /** Starý důl – uzavřené bludiště štol se žebříky, červený krystal. Vchod: „mine“ v Horách. */
    val MAZE = CaveMap(
        artW = 150, artH = 480,
        nodes = listOf(
            CaveNode("vychod_dolu", 26, 466),
            CaveNode("stola_vstup", 26, 412),
            CaveNode("stola_kriz", 75, 412),
            CaveNode("vozik", 124, 412),
            CaveNode("zebrik1", 75, 344),
            CaveNode("tezba", 124, 344),
            CaveNode("chodba_zapad", 26, 344),
            CaveNode("chodba_sever", 26, 276),
            CaveNode("rozcesti_dul", 75, 276),
            CaveNode("netopyri", 124, 276),
            CaveNode("zebrik2", 75, 208),
            CaveNode("slepa_chodba", 26, 208),
            CaveNode("horni_stola", 124, 208),
            CaveNode("hlubina", 124, 140),
            CaveNode("sin_krystalu", 75, 140),
            CaveNode("krystal_cerveny", 75, 76),
            // Stříbrná žíla (docs/adr/0035) – ve stěně nad štolou mezi žebříkem a těžbou
            CaveNode("zila_stribro", 100, 346)
        ),
        edges = listOf(
            "vychod_dolu" to "stola_vstup", "stola_vstup" to "stola_kriz", "stola_kriz" to "vozik",
            "stola_kriz" to "zebrik1",
            "zebrik1" to "tezba", "zebrik1" to "chodba_zapad", "chodba_zapad" to "chodba_sever",
            "chodba_sever" to "rozcesti_dul", "rozcesti_dul" to "netopyri",
            "rozcesti_dul" to "zebrik2",
            "zebrik2" to "slepa_chodba", "zebrik2" to "horni_stola", "horni_stola" to "hlubina",
            "hlubina" to "sin_krystalu", "sin_krystalu" to "krystal_cerveny", "zebrik1" to "zila_stribro"
        ),
        exitNode = "vychod_dolu",
        mountainNode = "mine",
        crystalNode = "krystal_cerveny",
        crystal = CrystalColor.RED,
        encounterNodes = setOf("vozik", "netopyri", "slepa_chodba", "hlubina")
    )

    val ALL = listOf(OPEN, MAZE)
}
