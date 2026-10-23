package cz.uhk.macroflow.pokemon.boost

import cz.uhk.macroflow.pokemon.PokemonLevelCalc

/** Kolik XP dá proteinová tyčinka. */
private const val PROTEIN_BAR_XP = 250
/** Kolik XP dá Zlaté jablko ze Ztichlého sadu. */
private const val GOLDEN_APPLE_XP = 600

/**
 * Nakopávače (docs/adr/0086): vzácné předměty, kterými hráč rovnou zvedne Makromonovi XP nebo levely
 * (obdoba Rare Candy). Nakopávač se dá z Batohu v deníku, vybere se Makromon z Kapsy.
 *
 * Ladění: síla každého nakopávače je jen tady v [effect] – nikde jinde se čísla nepočítají.
 * Čistý Kotlin (pixelová ikona 12 × 12 ze stejné mřížky znaků jako Makrobally a léčiva), testy v RareBoostTest.
 */
enum class RareBoost(
    val id: String,
    val label: String,
    val description: String,
    val effect: Effect,
    private val grid: List<String>,
    private val colors: Map<Char, Long>
) {
    PROTEIN_BAR(
        "item_rare_protein_bar", "Proteinová tyčinka",
        "Poctivá tyčinka do kapsy. Makromon z ní dostane $PROTEIN_BAR_XP XP.",
        Effect.Xp(PROTEIN_BAR_XP),
        listOf(
            "............",
            "............",
            "............",
            "KK.KKKKKK.KK",
            "KRKRWWWWRKRK",
            ".RRRWbbWRRR.",
            "KrKRWWWWRKrK",
            "KK.KKKKKK.KK",
            "............",
            "............",
            "............",
            "............"
        ),
        mapOf('K' to 0xFF3A2A1A, 'R' to 0xFFE53935, 'r' to 0xFFB71C1C, 'W' to 0xFFF4EBDD, 'b' to 0xFF6D4C41)
    ),
    PREWORKOUT(
        "item_rare_preworkout", "Preworkout",
        "Kdo si ho dá, nezastaví se. Makromon rovnou získá +1 level.",
        Effect.Levels(1),
        listOf(
            "....KKKK....",
            "....KRRK....",
            "...KKKKKK...",
            "..KWWWWWWK..",
            "..KPPPYPPK..",
            "..KPPYYPPK..",
            "..KPYYPPPK..",
            "..KPPYPPPK..",
            "..KPYPPPPK..",
            "..KpPPPPpK..",
            "..KKKKKKKK..",
            "............"
        ),
        mapOf('K' to 0xFF1E1030, 'W' to 0xFFF4F7FA, 'R' to 0xFFE53935, 'P' to 0xFF8E44AD, 'p' to 0xFF5E2B76, 'Y' to 0xFFFFE066)
    ),
    WHITE_MONSTER(
        "item_rare_white_monster", "Bílý Monster",
        "Plechovka, po které se svět zrychlí. Makromon rovnou získá +3 levely.",
        Effect.Levels(3),
        listOf(
            "..KKKKKKKK..",
            "..KSSSSSSK..",
            "..KWWWWWWK..",
            "..KWGGWGGK..",
            "..KWGGWGGK..",
            "..KWGGWGGK..",
            "..KWGGWGGK..",
            "..KWWWWWWK..",
            "..KWWWWWWK..",
            "..KsSSSSsK..",
            "..KKKKKKKK..",
            "............"
        ),
        mapOf('K' to 0xFF1E2A1E, 'S' to 0xFFB8C0C8, 's' to 0xFF8A939B, 'W' to 0xFFF6F6F2, 'G' to 0xFF3DDC4A)
    ),
    /** Odměna ze Ztichlého sadu (docs/adr/0097) – v obchodě se neprodává. */
    GOLDEN_APPLE(
        "item_rare_golden_apple", "Zlaté jablko",
        "Jablko ze Ztichlého sadu. Chutná jako vzpomínka, kterou nemůžeš zařadit. Makromon dostane $GOLDEN_APPLE_XP XP.",
        Effect.Xp(GOLDEN_APPLE_XP),
        listOf(
            "......K.....",
            ".....KbK....",
            "...KKKbLLK..",
            "..KYYYKKLLK.",
            ".KYWYYYYKK..",
            ".KYWYYYYYyK.",
            ".KYYYYYYYyK.",
            ".KYYYYYYYyK.",
            "..KYYYYYyK..",
            "..KyYYYyyK..",
            "...KKyyKK...",
            "....KKKK...."
        ),
        mapOf('K' to 0xFF3A2A12, 'Y' to 0xFFF2C744, 'y' to 0xFFC28A1E, 'W' to 0xFFFFF6C8, 'b' to 0xFF6D4C2A, 'L' to 0xFF6FBF4A)
    );

    /** Co nakopávač udělá. */
    sealed class Effect {
        /** Přidá zkušenosti (level se dopočítá z křivky). */
        data class Xp(val amount: Int) : Effect()
        /** Zvedne level rovnou; postup v novém levelu začíná od nuly (jako Rare Candy). */
        data class Levels(val levels: Int) : Effect()
    }

    /** Cena v obchodě (Makro penízky). */
    val price: Int get() = when (this) { PROTEIN_BAR -> 120; PREWORKOUT -> 300; WHITE_MONSTER -> 800; GOLDEN_APPLE -> 0 }

    /** Prodává se v obchodě (Zlaté jablko jen ze sadu). */
    val sold: Boolean get() = price > 0

    /** Výsledek použití: nový level, nové XP a o kolik levelů to Makromona posunulo. */
    data class Outcome(val level: Int, val xp: Int, val levelsGained: Int)

    /** Skutečný level Makromona; starší chycení mají level z divočiny a XP 0, proto větší z obou. */
    private fun effectiveLevel(level: Int, xp: Int): Int = maxOf(level, PokemonLevelCalc.levelFromXp(xp))

    /** Dá se použít? Na Makromona s maximálním levelem ne (předmět se pak nespotřebuje). */
    fun canUse(level: Int, xp: Int): Boolean = effectiveLevel(level, xp) < PokemonLevelCalc.MAX_LEVEL

    /** Spočítá nový stav Makromona. Volat jen když [canUse] platí. */
    fun apply(level: Int, xp: Int): Outcome {
        val before = effectiveLevel(level, xp)
        return when (val e = effect) {
            is Effect.Xp -> {
                val (newXp, newLevel) = PokemonLevelCalc.gain(level, xp, e.amount)
                val after = minOf(newLevel, PokemonLevelCalc.MAX_LEVEL)
                Outcome(after, newXp, after - before)
            }
            is Effect.Levels -> {
                val after = minOf(before + e.levels, PokemonLevelCalc.MAX_LEVEL)
                Outcome(after, PokemonLevelCalc.xpForLevel(after), after - before)
            }
        }
    }

    val pixels: IntArray by lazy {
        IntArray(SIZE * SIZE) { i ->
            val ch = grid[i / SIZE][i % SIZE]
            (colors[ch] ?: 0L).toInt()
        }
    }

    companion object {
        const val SIZE = 12
        fun from(id: String?): RareBoost? = entries.firstOrNull { it.id == id }

        /**
         * Má se po zvednutí levelu spustit evoluce? [evolveLevel] je level evoluce druhu (0 = nevyvíjí se).
         * Stejné pravidlo jako při XP za denní cíle: level musí vývojovou hranici dosáhnout nebo přeskočit.
         */
        fun evolutionDue(oldLevel: Int, newLevel: Int, evolveLevel: Int): Boolean =
            evolveLevel > 0 && newLevel >= evolveLevel && newLevel > oldLevel
    }
}
