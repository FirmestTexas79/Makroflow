package cz.uhk.macroflow.pokemon

// 📋 1. Třída pro jeden záznam učení útoku
data class LearnableMove(
    val level: Int,
    val move: Move
)

// 📋 2. Třída pro růstovou křivku konkrétního Pokémona
data class PokemonGrowthProfile(
    val pokedexId: String,
    val evolutionLevel: Int = 0,    // 0 = nevyvíjí se
    val evolutionToId: String = "", // Kam se vyvíjí
    val movesLearnedAt: List<LearnableMove> = emptyList() // Kdy a jaké útoky se učí
)

// 🧠 3. Centrální registr pro všechny Pokémony ve hře
object PokemonGrowthManager {

    private val GROWTH_DATABASE: Map<String, PokemonGrowthProfile> = mapOf(

        // --- 🐛 CATERPIE RODINA ---
        "010" to PokemonGrowthProfile(
            pokedexId = "010",
            evolutionLevel = 7,
            evolutionToId = "011",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackTackle()),
                LearnableMove(1, BattleFactory.attackStringShot())
            )
        ),
        "011" to PokemonGrowthProfile(
            pokedexId = "011",
            evolutionLevel = 10,
            evolutionToId = "012",
            movesLearnedAt = listOf(
                LearnableMove(7, BattleFactory.attackHarden()) // Naučí se při evoluci na Metapoda
            )
        ),
        "012" to PokemonGrowthProfile(
            pokedexId = "012",
            movesLearnedAt = listOf(
                LearnableMove(10, BattleFactory.attackGust()) // Naučí se při evoluci na Butterfree
            )
        ),

        // --- ⚡ PIKACHU ---
        "025" to PokemonGrowthProfile(
            pokedexId = "025",
            evolutionLevel = 20, // Jen jako příklad
            evolutionToId = "026", // Raichu
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackTackle()),
                LearnableMove(6, Move("Thunder Shock", PokemonType.ELECTRIC, 40, 100, 30)),
                LearnableMove(11, Move("Quick Attack", PokemonType.NORMAL, 40, 100, 30))
            )
        )
        // ➕ Sem budeš jednoduše pod sebe dopisovat všechny ostatní Pokémony a jejich křivky!
    )

    fun getProfile(pokedexId: String): PokemonGrowthProfile? {
        return GROWTH_DATABASE[pokedexId]
    }

    // Zjistí, jestli se Pokémon na daném levelu má naučit nový útok
    fun getNewMoveForLevel(pokedexId: String, level: Int): Move? {
        val profile = getProfile(pokedexId) ?: return null
        return profile.movesLearnedAt.find { it.level == level }?.move
    }
}