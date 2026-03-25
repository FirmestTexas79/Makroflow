package cz.uhk.macroflow.pokemon

import android.content.Context

enum class Rarity(val weight: Int, val label: String) {
    COMMON   (60, "Common"),
    RARE     (28, "Rare"),
    EPIC     ( 9, "Epic"),
    LEGENDARY( 2, "Legendary"),
    MYTHIC   ( 1, "Mythic")
}

interface SpawnCondition {
    fun isMet(context: Context): Boolean
}

object AlwaysCondition : SpawnCondition {
    override fun isMet(context: Context): Boolean = true
}

object NightCondition : SpawnCondition {
    override fun isMet(context: Context): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour >= 19 || hour < 5
    }
}

class MinStreakCondition(private val requiredStreak: Int) : SpawnCondition {
    override fun isMet(context: Context): Boolean {
        val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val currentStreak = prefs.getInt("currentStreak", 0)
        return currentStreak >= requiredStreak
    }
}

data class SpawnPool(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val conditions: List<SpawnCondition>,
    val createPokemon: () -> Pokemon
)

object SpawnManager {

    private val POOL: List<SpawnPool> = listOf(

        SpawnPool("050", "DIGLETT",   Rarity.COMMON, listOf(AlwaysCondition))        { BattleFactory.createDiglett() },
        SpawnPool("025", "PIKACHU",   Rarity.COMMON, listOf(AlwaysCondition))        { BattleFactory.createPikachu() },
        SpawnPool("133", "EEVEE",     Rarity.COMMON, listOf(AlwaysCondition))        { BattleFactory.createEevee() },

        SpawnPool("001", "BULBASAUR", Rarity.RARE, listOf(AlwaysCondition))        { BattleFactory.createBulbasaur() },
        SpawnPool("007", "SQUIRTLE",  Rarity.RARE, listOf(AlwaysCondition))        { BattleFactory.createSquirtle() },
        SpawnPool("004", "CHARMANDER",Rarity.RARE, listOf(AlwaysCondition))        { BattleFactory.createCharmander() },
        SpawnPool("092", "GASTLY",    Rarity.RARE, listOf(NightCondition))         { BattleFactory.createGastly() },

        SpawnPool("093", "HAUNTER",   Rarity.EPIC, listOf(NightCondition))         { BattleFactory.createHaunter() },
        SpawnPool("094", "GENGAR",    Rarity.EPIC, listOf(NightCondition))         { BattleFactory.createGengar() },
        SpawnPool("143", "SNORLAX",   Rarity.EPIC, listOf(MinStreakCondition(7)))   { BattleFactory.createSnorlax() },

        SpawnPool("006", "CHARIZARD", Rarity.LEGENDARY, listOf(MinStreakCondition(30)))  { BattleFactory.createCharizard() },
        SpawnPool("150", "MEWTWO",    Rarity.LEGENDARY, listOf(MinStreakCondition(60)))  { BattleFactory.createMewtwo() },

        SpawnPool("151", "MEW",       Rarity.MYTHIC, listOf(MinStreakCondition(100))) { BattleFactory.createWildMew() }
    )

    fun getActivePool(context: Context): List<SpawnPool> =
        POOL.filter { spawn -> spawn.conditions.all { it.isMet(context) } }

    fun rollWildEncounter(context: Context): Pokemon {
        val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

        if (prefs.getBoolean("ghostPlateActive", false)) {
            prefs.edit().putBoolean("ghostPlateActive", false).apply()
            return BattleFactory.createGengar()
        }

        val active = getActivePool(context)
        if (active.isEmpty()) return BattleFactory.createDiglett()

        val totalWeight = active.sumOf { it.rarity.weight }
        var roll = kotlin.random.Random.nextInt(totalWeight)
        for (spawn in active) {
            roll -= spawn.rarity.weight
            if (roll < 0) return spawn.createPokemon()
        }
        return BattleFactory.createDiglett()
    }

    fun findById(id: String): SpawnPool? = POOL.find { it.id == id }

    val allEntries: List<SpawnPool> get() = POOL
}