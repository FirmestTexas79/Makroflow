package cz.uhk.macroflow.pokemon

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.dashboard.MacroCalculator
import java.text.SimpleDateFormat
import java.util.*

// --- 📊 DEFINICE RARITY ---
enum class Rarity(val weight: Int, val label: String) {
    COMMON   (60, "Common"),
    RARE     (28, "Rare"),
    EPIC     ( 9, "Epic"),
    LEGENDARY( 2, "Legendary"),
    MYTHIC   ( 1, "Mythic")
}

// --- 🛠️ ROZHRANÍ PRO PODMÍNKY ---
interface SpawnCondition {
    fun isMet(context: Context): Boolean
}

// --- 📋 PODMÍNKY SPAWNU ---
object Conditions {
    val ALWAYS = object : SpawnCondition {
        override fun isMet(context: Context): Boolean = true
    }

    val NIGHT_ONLY = object : SpawnCondition {
        override fun isMet(context: Context): Boolean {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return hour !in 5..18
        }
    }

    val WATER_GOAL_REACHED = object : SpawnCondition {
        override fun isMet(context: Context): Boolean {
            val db = AppDatabase.getDatabase(context)
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val target = MacroCalculator.calculate(context)
            val targetMl = (target.water * 1000).toInt()
            val currentMl = db.waterDao().getTotalMlForDateSync(todayStr)
            return currentMl >= targetMl
        }
    }

    class MinCheckInCount(private val requiredDays: Int) : SpawnCondition {
        override fun isMet(context: Context): Boolean {
            val db = AppDatabase.getDatabase(context)
            val totalCheckIns = db.checkInDao().getAllCheckInsSync().size
            return totalCheckIns >= requiredDays
        }
    }
}

// --- 📦 DATOVÁ TŘÍDA PRO POOL S BIOMY ---
data class SpawnPool(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val biomes: List<BiomeType>,
    val conditions: List<SpawnCondition>,
    val createMakromon: () -> Makromon
)

// --- 🧠 CENTRÁLNÍ MOZEK SPAWNOVÁNÍ ---
object SpawnManager {

    /** Spawny všech druhů z registru (docs/adr/0067) – nový druh se sem přidává v species/Species.kt. */
    private val POOL: List<SpawnPool> by lazy {
        cz.uhk.macroflow.pokemon.species.SpeciesRegistry.ALL.flatMap { sp ->
            sp.spawns.map { SpawnPool(sp.id, sp.name, it.rarity, it.biomes, it.conditions) { sp.create() } }
        }
    }

    fun rollWildEncounter(context: Context, currentBiome: BiomeType): Makromon {
        val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

        if (prefs.getBoolean("ghostPlateActive", false)) {
            prefs.edit().putBoolean("ghostPlateActive", false).apply()
            return BattleFactory.createById("016")
        }

        // Filtrování podle biomu a podmínek
        val active = POOL.filter { spawn ->
            spawn.biomes.contains(currentBiome) && spawn.conditions.all { it.isMet(context) }
        }

        if (active.isEmpty()) return BattleFactory.createById("012")

        val totalWeight = active.sumOf { it.rarity.weight }
        var roll = kotlin.random.Random.nextInt(totalWeight)

        for (spawn in active) {
            roll -= spawn.rarity.weight
            if (roll < 0) return spawn.createMakromon()
        }

        return BattleFactory.createById("012")
    }

    fun findById(id: String): SpawnPool? = POOL.find { it.id == id }
    val allEntries: List<SpawnPool> get() = POOL
}