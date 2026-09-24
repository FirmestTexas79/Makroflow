package cz.uhk.macroflow.pokemon.status

/**
 * Léčivé předměty na stavy (čistý Kotlin, pokryto testy).
 * Použití v souboji stojí tah; bez účinku se nespotřebuje a tah nepropadne.
 * Sprite 12 × 12 se kreslí ze stejné mřížky znaků jako Makrobally.
 */
enum class MedItem(
    val id: String,
    /** Krátký název do menu souboje (max. 7 znaků, pixelový font bez diakritiky). */
    val short: String,
    val label: String,
    val price: Int,
    val description: String,
    private val cures: Set<StatusKind>,
    private val curesConfusion: Boolean,
    private val grid: List<String>,
    private val colors: Map<Char, Long>
) {
    CAFFEINE(
        "item_caffeine", "KOFEIN", "Kofein", 30, "Probudí ze spánku.",
        setOf(StatusKind.SLEEP), false,
        listOf(
            "...S.S......",
            "....S.S.....",
            "..KKKKKKK...",
            "..KWWWWWKKK.",
            "..KWCCCWK.K.",
            "..KWCCCWK.K.",
            "..KWWWWWKKK.",
            "..KWWWWWK...",
            "...KWWWK....",
            "..KKKKKKKK..",
            "..KwwwwwwK..",
            "..KKKKKKKK.."
        ),
        mapOf('K' to 0xFF2A1A12, 'W' to 0xFFF6F2EA, 'w' to 0xFFD8CFC0, 'C' to 0xFF6B3E22, 'S' to 0xFFB8B8B8)
    ),
    ELECTROLYTE(
        "item_electrolyte", "ELEKTRO", "Elektrolyt", 30, "Zruší paralýzu.",
        setOf(StatusKind.PARALYSIS), false,
        listOf(
            ".....KK.....",
            "....KWWK....",
            "....KKKK....",
            "...KBBBBK...",
            "...KBBYBK...",
            "...KBYYBK...",
            "...KYYBBK...",
            "...KBYYBK...",
            "...KBYBBK...",
            "...KbBBBK...",
            "...KbbBBK...",
            "...KKKKKK..."
        ),
        mapOf('K' to 0xFF0E1A26, 'W' to 0xFFF4F7FA, 'B' to 0xFF2E86DE, 'b' to 0xFF1B5FA6, 'Y' to 0xFFFFD54F)
    ),
    CHARCOAL(
        "item_charcoal", "UHLI", "Aktivní uhlí", 30, "Vyčistí otravu.",
        setOf(StatusKind.POISON), false,
        listOf(
            "............",
            "............",
            "....KKKK....",
            "..KKDDDDKK..",
            ".KDDGDDDDDK.",
            ".KDDDDDGDDK.",
            ".KDGDDDDDDK.",
            ".KDDDDDDGDK.",
            ".KDDDGDDDDK.",
            "..KDDDDDDK..",
            "...KKKKKK...",
            "............"
        ),
        mapOf('K' to 0xFF0A0A0A, 'D' to 0xFF2E2E30, 'G' to 0xFF6A6A70)
    ),
    ALOE(
        "item_aloe", "ALOE", "Aloe gel", 30, "Zklidní popáleniny.",
        setOf(StatusKind.BURN), false,
        listOf(
            ".....KK.....",
            "....KGGK....",
            "....KGgK....",
            "...KGGgGK...",
            "...KGGgGK...",
            "..KGGGgGGK..",
            "..KGGgGGGK..",
            "..KGGgGGGK..",
            "...KGgGGK...",
            "...KGgGGK...",
            "....KGGK....",
            ".....KK....."
        ),
        mapOf('K' to 0xFF1C3A12, 'G' to 0xFF5FA83A, 'g' to 0xFFB6E08E)
    ),
    COLD_SHOWER(
        "item_cold_shower", "SPRCHA", "Studená sprcha", 25, "Probere ze zmatení.",
        emptySet(), true,
        listOf(
            ".....KK.....",
            "....KBBK....",
            "....KBBK....",
            "...KBBBBK...",
            "...KWBBBK...",
            "..KBWBBBBK..",
            "..KBWBBBBK..",
            "..KBBBBBBK..",
            "..KBBBBBbK..",
            "...KBBBbK...",
            "....KKKK....",
            "............"
        ),
        mapOf('K' to 0xFF0E2A44, 'B' to 0xFF6EC6FF, 'b' to 0xFF3A96D6, 'W' to 0xFFE8F6FF)
    ),
    MULTIVITAMIN(
        "item_multivitamin", "MULTIVI", "Multivitamín", 80, "Vyléčí jakýkoli stav.",
        StatusKind.entries.toSet(), true,
        listOf(
            "............",
            ".......KKK..",
            "......KRRRK.",
            ".....KRRrRK.",
            "....KRRRRK..",
            "...KYKRRK...",
            "..KYYYKK....",
            ".KYyYYK.....",
            ".KYYYK......",
            "..KKK.......",
            "............",
            "............"
        ),
        mapOf('K' to 0xFF3A1010, 'R' to 0xFFE53935, 'r' to 0xFFFF8A80, 'Y' to 0xFFFFC107, 'y' to 0xFFFFE9A0)
    );

    /** Pomůže to na aktuální stav? */
    fun helps(c: Condition): Boolean = (c.major != null && c.major in cures) || (curesConfusion && c.confused)

    /** Vyléčí, co umí; vrací true, když se něco změnilo. */
    fun apply(c: Condition): Boolean {
        if (!helps(c)) return false
        if (c.major != null && c.major in cures) { c.major = null; c.sleepTurns = 0 }
        if (curesConfusion) c.confusedTurns = 0
        return true
    }

    val pixels: IntArray by lazy {
        IntArray(SIZE * SIZE) { i ->
            val ch = grid[i / SIZE][i % SIZE]
            (colors[ch] ?: 0L).toInt()
        }
    }

    companion object {
        const val SIZE = 12
        fun from(id: String?): MedItem? = entries.firstOrNull { it.id == id }
    }
}
