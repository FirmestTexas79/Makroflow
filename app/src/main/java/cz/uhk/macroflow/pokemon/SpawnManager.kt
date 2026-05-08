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

    // Definujeme biomy, které jsou považovány za "divočinu" (vše kromě města)
    private val ALL_WILD_BIOMES = BiomeType.values().filter { it != BiomeType.TOWN && it != BiomeType.WATER }

    // Ponecháno, pokud byste někdy potřebovali skutečně úplně všechny
    private val ALL_BIOMES = BiomeType.values().toList()

    private val POOL: List<SpawnPool> = listOf(

        // ── TOWN (Město) ──────────────────────────────────────────────
        // Zde zůstávají jen ti, kteří mají BiomeType.TOWN explicitně
        SpawnPool("012", "SPIRRA",   Rarity.COMMON, listOf(BiomeType.MEADOW), listOf(Conditions.ALWAYS))      { BattleFactory.createSpirra() },
        SpawnPool("001", "IGNAR",    Rarity.COMMON, listOf(BiomeType.TOWN), listOf(Conditions.ALWAYS))      { BattleFactory.createIgnar() },
        SpawnPool("013", "FLAMIRRA",  Rarity.RARE,   listOf(BiomeType.MEADOW), listOf(Conditions.ALWAYS))       { BattleFactory.createFlamirra() },
        SpawnPool("017", "CHARMIRRA", Rarity.RARE,   listOf(BiomeType.MEADOW), listOf(Conditions.ALWAYS))       { BattleFactory.createCharmirra() },
        SpawnPool("002", "IGNAROC",   Rarity.RARE,   listOf(BiomeType.MOUNTAINS), listOf(Conditions.ALWAYS))       { BattleFactory.createIgnaroc() },
        SpawnPool("010", "UMBEX",     Rarity.EPIC,   listOf(BiomeType.MOUNTAINS), listOf(Conditions.NIGHT_ONLY))   { BattleFactory.createUmbex() },
        SpawnPool("011", "LUMEX",     Rarity.LEGENDARY, listOf(BiomeType.MOUNTAINS), listOf(Conditions.NIGHT_ONLY)) { BattleFactory.createLumex() },

        // ── MEADOW (Les/Louka) ─────────────────────────────────────────
        SpawnPool("022", "MYCIT",    Rarity.COMMON, listOf(BiomeType.MEADOW), listOf(Conditions.ALWAYS))      { BattleFactory.createMycit() },
        SpawnPool("007", "FLORI",    Rarity.COMMON, listOf(BiomeType.TOWN), listOf(Conditions.ALWAYS))      { BattleFactory.createFlori() },
        SpawnPool("015", "VERDIRRA",  Rarity.RARE,   listOf(BiomeType.MEADOW), listOf(Conditions.ALWAYS))       { BattleFactory.createVerdirra() },
        SpawnPool("008", "FLORIND",   Rarity.RARE,   listOf(BiomeType.MEADOW), listOf(Conditions.ALWAYS))       { BattleFactory.createFlorind() },
        SpawnPool("023", "MYDRUS",    Rarity.EPIC,   listOf(BiomeType.MEADOW), listOf(Conditions.MinCheckInCount(5)))  { BattleFactory.createMydrus() },
        SpawnPool("009", "FLORINDRA", Rarity.EPIC,   listOf(BiomeType.MEADOW), listOf(Conditions.MinCheckInCount(7)))  { BattleFactory.createFlorindra() },
        SpawnPool("030", "GUDWIN",    Rarity.EPIC,   listOf(BiomeType.MEADOW), listOf(Conditions.MinCheckInCount(7)))  { BattleFactory.createGudwin() },

        // ── WATER (Voda) ──────────────────────────────────────────────
        SpawnPool("020", "FINLET",   Rarity.COMMON, listOf(BiomeType.WATER), listOf(Conditions.ALWAYS))      { BattleFactory.createFinlet() },
        SpawnPool("004", "AQULIN",   Rarity.COMMON, listOf(BiomeType.TOWN), listOf(Conditions.ALWAYS))      { BattleFactory.createAqulin() },
        SpawnPool("014", "AQUIRRA",   Rarity.RARE,   listOf(BiomeType.WATER), listOf(Conditions.ALWAYS))       { BattleFactory.createAquirra() },
        SpawnPool("005", "AQULIND",   Rarity.RARE,   listOf(BiomeType.WATER), listOf(Conditions.ALWAYS))       { BattleFactory.createAqlind() },
        SpawnPool("021", "SERPFIN",   Rarity.RARE,   listOf(BiomeType.WATER), listOf(Conditions.MinCheckInCount(3))) { BattleFactory.createSerpfin() },
        SpawnPool("006", "AQULINOX",  Rarity.EPIC,   listOf(BiomeType.WATER), listOf(Conditions.MinCheckInCount(7)))  { BattleFactory.createAqulinox() },

        // ── NIGHT / GHOST (Všude kromě TOWN v noci) ────────────────────
        SpawnPool("027", "PHANTIL",   Rarity.RARE,   ALL_WILD_BIOMES, listOf(Conditions.NIGHT_ONLY))   { BattleFactory.createPhantil() },
        SpawnPool("024", "SOULU",     Rarity.RARE,   ALL_WILD_BIOMES, listOf(Conditions.NIGHT_ONLY))   { BattleFactory.createSoulu() },
        SpawnPool("016", "SHADIRRA",  Rarity.EPIC,   ALL_WILD_BIOMES, listOf(Conditions.NIGHT_ONLY))   { BattleFactory.createShadirra() },
        SpawnPool("025", "SOULEX",    Rarity.EPIC,   ALL_WILD_BIOMES, listOf(Conditions.NIGHT_ONLY))   { BattleFactory.createSoulex() },
        SpawnPool("028", "PHANTIUS",  Rarity.EPIC,   listOf(BiomeType.WATER), listOf(Conditions.NIGHT_ONLY))   { BattleFactory.createPhantius() },
        SpawnPool("026", "SOULORD",   Rarity.LEGENDARY, ALL_WILD_BIOMES, listOf(Conditions.MinCheckInCount(20), Conditions.NIGHT_ONLY)) { BattleFactory.createSoulord() },

        // ── LEGENDARY & MYTHIC (Všude kromě TOWN) ──────────────────────
        SpawnPool("018", "GLACIRRA",  Rarity.EPIC,   ALL_WILD_BIOMES, listOf(Conditions.ALWAYS))       { BattleFactory.createGlacirra() },
        SpawnPool("003", "IGNAROTH",  Rarity.EPIC,   listOf(BiomeType.TOWN), listOf(Conditions.MinCheckInCount(7)))  { BattleFactory.createIgnaroth() },
        SpawnPool("019", "DRAKIRRA",  Rarity.LEGENDARY, ALL_WILD_BIOMES, listOf(Conditions.MinCheckInCount(30)))     { BattleFactory.createDrakirra() },
        SpawnPool("029", "PHANTIAX",  Rarity.LEGENDARY, ALL_WILD_BIOMES, listOf(Conditions.MinCheckInCount(20)))     { BattleFactory.createPhantiax() },
        SpawnPool("031", "AXLU",      Rarity.MYTHIC, ALL_WILD_BIOMES, listOf(Conditions.MinCheckInCount(50)))        { BattleFactory.createAxlu() }
    )

    fun rollWildEncounter(context: Context, currentBiome: BiomeType): Makromon {
        val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

        if (prefs.getBoolean("ghostPlateActive", false)) {
            prefs.edit().putBoolean("ghostPlateActive", false).apply()
            return BattleFactory.createShadirra()
        }

        // Filtrování podle biomu a podmínek
        val active = POOL.filter { spawn ->
            spawn.biomes.contains(currentBiome) && spawn.conditions.all { it.isMet(context) }
        }

        if (active.isEmpty()) return BattleFactory.createSpirra()

        val totalWeight = active.sumOf { it.rarity.weight }
        var roll = kotlin.random.Random.nextInt(totalWeight)

        for (spawn in active) {
            roll -= spawn.rarity.weight
            if (roll < 0) return spawn.createMakromon()
        }

        return BattleFactory.createSpirra()
    }

    fun findById(id: String): SpawnPool? = POOL.find { it.id == id }
    val allEntries: List<SpawnPool> get() = POOL
}