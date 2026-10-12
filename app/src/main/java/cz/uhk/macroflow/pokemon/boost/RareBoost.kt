package cz.uhk.macroflow.pokemon.boost

import cz.uhk.macroflow.pokemon.PokemonLevelCalc

/** Kolik XP dá Rare Oatmeal. */
private const val OATMEAL_XP = 250

/**
 * Nakopávače (docs/adr/0074): vzácné předměty, kterými hráč rovnou zvedne Makromonovi XP nebo levely
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
    OATMEAL(
        "item_rare_oatmeal", "Rare Oatmeal",
        "Vydatná snídaně šampionů. Makromon z ní dostane $OATMEAL_XP XP.",
        Effect.Xp(OATMEAL_XP),
        listOf(
            "..Y......Y..",
            "...Y....Y...",
            "..OOOOOOOO..",
            ".OOoOOOoOOO.",
            "KOOOOoOOOOOK",
            "KKKKKKKKKKKK",
            "KBBBBBBBBBBK",
            ".KBBBBBBBBK.",
            "..KBBBBBBK..",
            "...KKKKKK...",
            "............",
            "............"
        ),
        mapOf('K' to 0xFF3A2A1A, 'O' to 0xFFE8D2A0, 'o' to 0xFFC9A66B, 'B' to 0xFF8E5A3C, 'Y' to 0xFFFFD54F)
    ),
    PREWORKOUT(
        "item_rare_preworkout", "Rare Preworkout",
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
        "item_rare_white_monster", "Rare White Monster",
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
    );

    /** Co nakopávač udělá. */
    sealed class Effect {
        /** Přidá zkušenosti (level se dopočítá z křivky). */
        data class Xp(val amount: Int) : Effect()
        /** Zvedne level rovnou; postup v novém levelu začíná od nuly (jako Rare Candy). */
        data class Levels(val levels: Int) : Effect()
    }

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
