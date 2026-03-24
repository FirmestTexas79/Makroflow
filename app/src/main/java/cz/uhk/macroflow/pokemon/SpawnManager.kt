package cz.uhk.macroflow.pokemon

// Definice rarity
enum class Rarity(val weight: Int) {
    COMMON(70),    // 70 % šance
    RARE(25),      // 25 % šance
    EPIC(4),       // 4 % šance
    LEGENDARY(1)   // 1 % šance
}

// Struktura pro zápis do Registru
data class WildPokemonSpawn(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val createPokemon: () -> Pokemon // Lambda funkce, která vytvoří instanci z BattleFactory
)

object SpawnManager {

    // 1. CENTRÁLNÍ REGISTR VŠECH DIVOKÝCH POKÉMONŮ
    private val wildRegistry = listOf(
        WildPokemonSpawn("050", "DIGLETT", Rarity.COMMON) { BattleFactory.createDiglett() },
        WildPokemonSpawn("094", "GENGAR", Rarity.EPIC) { BattleFactory.createGengar() }
        // Sem v budoucnu jednoduše připíšeš:
        // WildPokemonSpawn("066", "MACHOP", Rarity.RARE) { BattleFactory.createMachop() }
    )

    // 2. LOGIKA VÝBĚRU (Vylosování podle vah)
    // Potřebujeme kontext pro SharedPreferences, upravíme hlavičku metody:
    fun rollWildEncounter(context: android.content.Context): Pokemon {
        val prefs = context.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
        val isGhostPlateActive = prefs.getBoolean("ghostPlateActive", false)

        // 👻 PŘEDNOSTNÍ GENERATOR PŘEDMĚTU GHOST PLATE
        if (isGhostPlateActive) {
            // Okamžitě předmět deaktivujeme, aby se spotřeboval na 1 boj
            prefs.edit().putBoolean("ghostPlateActive", false).apply()

            val gengarSpawn = wildRegistry.find { it.name == "GENGAR" }
            if (gengarSpawn != null) {
                return gengarSpawn.createPokemon()
            }
        }

        // 🎲 KLASICKÝ NÁHODNÝ GENERÁTOR (Tvůj stávající kód)
        val totalWeight = wildRegistry.sumOf { it.rarity.weight }
        var randomNum = kotlin.random.Random.nextInt(totalWeight)

        for (spawn in wildRegistry) {
            randomNum -= spawn.rarity.weight
            if (randomNum < 0) {
                return spawn.createPokemon()
            }
        }
        return BattleFactory.createDiglett()
    }
}