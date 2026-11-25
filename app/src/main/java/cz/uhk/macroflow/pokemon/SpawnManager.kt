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

        // Patra Věže zaměřená na typ: všechny divoké druhy daného typu, každý s nejčastější raritou
        TYPED_FLOORS[currentBiome]?.let { type ->
            val typed = POOL.filter { sp -> sp.conditions.all { it.isMet(context) } }.groupBy { it.id }
                .mapNotNull { (_, l) -> l.maxByOrNull { it.rarity.weight }?.takeIf { DEX_TYPE[it.id] in type } }
            if (typed.isNotEmpty()) {
                var r = kotlin.random.Random.nextInt(typed.sumOf { it.rarity.weight })
                for (spawn in typed) { r -= spawn.rarity.weight; if (r < 0) return spawn.createMakromon() }
            }
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

    /** Patra Věže Řádu Váhy, kde žijí jen Makromoni jednoho typu (od 3. patra). */
    val TYPED_FLOORS: Map<BiomeType, Set<String>> = mapOf(
        BiomeType.TOWER_PLAIN to setOf("NORMÁLNÍ"), BiomeType.TOWER_FOREST to setOf("PŘÍRODA"),
        BiomeType.TOWER_ROCK to setOf("ZEMĚ"), BiomeType.TOWER_CAVE to setOf("PSYCHO", "BLESK"),
        BiomeType.TOWER_WATER to setOf("VODA"),
        BiomeType.TOWER_DORM to setOf("DUCH"),
        BiomeType.TOWER_ICE to setOf("LED"),
        BiomeType.TOWER_FIRE to setOf("OHEŇ"),
        BiomeType.TOWER_FAIRY to setOf("VÍLA"),
        BiomeType.TOWER_POISON to setOf("JED"),
        // Zóna 3 – Hlubina (docs/adr/0106)
        BiomeType.ROOT_WATCH to setOf("DUCH", "ZEMĚ"),
        BiomeType.ROOT_SLOPECAMP to setOf("DUCH"),
        BiomeType.ROOT_SIGNAL to setOf("DUCH", "PSYCHO"),
        BiomeType.ROOT_BRIDGE to setOf("PŘÍRODA", "PSYCHO"))

    /** Typ druhu tak, jak ho ukazuje Makrodex (první slovo dexType: „ZEMĚ / KÁMEN“ → ZEMĚ). */
    private val DEX_TYPE: Map<String, String> by lazy {
        cz.uhk.macroflow.pokemon.species.SpeciesRegistry.ALL.associate { it.id to it.dexType.substringBefore('/').trim() }
    }

    fun findById(id: String): SpawnPool? = POOL.find { it.id == id }
    val allEntries: List<SpawnPool> get() = POOL
}