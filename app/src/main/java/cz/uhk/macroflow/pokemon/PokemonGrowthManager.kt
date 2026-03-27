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

        "025" to PokemonGrowthProfile(
            pokedexId = "025",
            evolutionLevel = 6, // Přirozená evoluce
            evolutionToId = "026",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackTackle()),
                LearnableMove(6, BattleFactory.attackThunderShock()),
                LearnableMove(11, BattleFactory.attackQuickAttack())
            )
        ),

        "026" to PokemonGrowthProfile(
            pokedexId = "026",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackThunderShock()), // 👈 Naučí se hned při evoluci!
                LearnableMove(8, BattleFactory.attackSlam()),
                LearnableMove(10, BattleFactory.attackThunderbolt()),
                LearnableMove(11, BattleFactory.attackThunder())
            )
        ),

        "115" to PokemonGrowthProfile(
            pokedexId = "115",
            evolutionLevel = 0,    // ❌ Nevyvíjí se
            evolutionToId = "",    // ❌ Žádné další ID
            movesLearnedAt = listOf(
                LearnableMove(1, Move("TACKLE", PokemonType.NORMAL, 40, 100, 35)),
                LearnableMove(1, Move("TAIL WHIP", PokemonType.NORMAL, 0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)),
                LearnableMove(10, Move("MEGA PUNCH", PokemonType.NORMAL, 80, 85, 20)) // Naučí se na lvl 10
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