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

// 💡 Sem můžeš v budoucnu jednoduše přidávat:
// val CALORIE_GOAL_MET = object : SpawnCondition { ... }
// val PROTEIN_GOAL_MET = object : SpawnCondition { ... }

// --- 📦 DATOVÁ TŘÍDA PRO POOL ---
data class SpawnPool(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val conditions: List<SpawnCondition>,
    val createMakromon: () -> Makromon
)

// --- 🧠 CENTRÁLNÍ MOZEK SPAWNOVÁNÍ ---
object SpawnManager {

    private val POOL: List<SpawnPool> = listOf(

        // ── COMMON ────────────────────────────────────────────────────
        // Základní starteři a jejich první evoluce jsou nejčastější
        SpawnPool("012", "SPIRRA",   Rarity.COMMON, listOf(Conditions.ALWAYS))      { BattleFactory.createSpirra() },
        SpawnPool("020", "FINLET",   Rarity.COMMON, listOf(Conditions.ALWAYS))      { BattleFactory.createFinlet() },
        SpawnPool("022", "MYCIT",    Rarity.COMMON, listOf(Conditions.ALWAYS))      { BattleFactory.createMycit() },
        SpawnPool("001", "IGNAR",    Rarity.COMMON, listOf(Conditions.ALWAYS))      { BattleFactory.createIgnar() },
        SpawnPool("004", "AQULIN",   Rarity.COMMON, listOf(Conditions.ALWAYS))      { BattleFactory.createAqulin() },
        SpawnPool("007", "FLORI",    Rarity.COMMON, listOf(Conditions.ALWAYS))      { BattleFactory.createFlori() },

        // ── RARE ──────────────────────────────────────────────────────
        SpawnPool("013", "FLAMIRRA",  Rarity.RARE, listOf(Conditions.ALWAYS))       { BattleFactory.createFlamirra() },
        SpawnPool("014", "AQUIRRA",   Rarity.RARE, listOf(Conditions.ALWAYS))       { BattleFactory.createAquirra() },
        SpawnPool("015", "VERDIRRA",  Rarity.RARE, listOf(Conditions.ALWAYS))       { BattleFactory.createVerdirra() },
        SpawnPool("017", "CHARMIRRA", Rarity.RARE, listOf(Conditions.ALWAYS))       { BattleFactory.createCharmirra() },
        SpawnPool("002", "IGNAROC",   Rarity.RARE, listOf(Conditions.ALWAYS))       { BattleFactory.createIgnaroc() },
        SpawnPool("005", "AQULIND",   Rarity.RARE, listOf(Conditions.ALWAYS))       { BattleFactory.createAqlind() },
        SpawnPool("008", "FLORIND",   Rarity.RARE, listOf(Conditions.ALWAYS))       { BattleFactory.createFlorind() },
        SpawnPool("021", "SERPFIN",   Rarity.RARE, listOf(Conditions.MinCheckInCount(3))) { BattleFactory.createSerpfin() },
        SpawnPool("027", "PHANTIL",   Rarity.RARE, listOf(Conditions.NIGHT_ONLY))   { BattleFactory.createPhantil() },
        SpawnPool("024", "SOULU",     Rarity.RARE, listOf(Conditions.NIGHT_ONLY))   { BattleFactory.createSoulu() },

        // ── EPIC ──────────────────────────────────────────────────────
        SpawnPool("016", "SHADIRRA",  Rarity.EPIC, listOf(Conditions.NIGHT_ONLY))   { BattleFactory.createShadirra() },
        SpawnPool("018", "GLACIRRA",  Rarity.EPIC, listOf(Conditions.ALWAYS))       { BattleFactory.createGlacirra() },
        SpawnPool("003", "IGNAROTH",  Rarity.EPIC, listOf(Conditions.MinCheckInCount(7)))  { BattleFactory.createIgnaroth() },
        SpawnPool("006", "AQULINOX",  Rarity.EPIC, listOf(Conditions.MinCheckInCount(7)))  { BattleFactory.createAqulinox() },
        SpawnPool("009", "FLORINDRA", Rarity.EPIC, listOf(Conditions.MinCheckInCount(7)))  { BattleFactory.createFlorindra() },
        SpawnPool("010", "UMBEX",     Rarity.EPIC, listOf(Conditions.NIGHT_ONLY))   { BattleFactory.createUmbex() },
        SpawnPool("023", "MYDRUS",    Rarity.EPIC, listOf(Conditions.MinCheckInCount(5)))  { BattleFactory.createMydrus() },
        SpawnPool("025", "SOULEX",    Rarity.EPIC, listOf(Conditions.NIGHT_ONLY))   { BattleFactory.createSoulex() },
        SpawnPool("028", "PHANTIUS",  Rarity.EPIC, listOf(Conditions.NIGHT_ONLY))   { BattleFactory.createPhantius() },
        SpawnPool("030", "GUDWIN",    Rarity.EPIC, listOf(Conditions.MinCheckInCount(7)))  { BattleFactory.createGudwin() },

        // ── LEGENDARY ─────────────────────────────────────────────────
        SpawnPool("011", "LUMEX",     Rarity.LEGENDARY, listOf(Conditions.NIGHT_ONLY))              { BattleFactory.createLumex() },
        SpawnPool("019", "DRAKIRRA",  Rarity.LEGENDARY, listOf(Conditions.MinCheckInCount(30)))     { BattleFactory.createDrakirra() },
        SpawnPool("026", "SOULORD",   Rarity.LEGENDARY, listOf(Conditions.MinCheckInCount(20), Conditions.NIGHT_ONLY)) { BattleFactory.createSoulord() },
        SpawnPool("029", "PHANTIAX",  Rarity.LEGENDARY, listOf(Conditions.MinCheckInCount(20)))     { BattleFactory.createPhantiax() },

        // ── MYTHIC ────────────────────────────────────────────────────
        // Axlu je tvář aplikace – extrémně vzácný, ale chytitelný
        SpawnPool("031", "AXLU",      Rarity.MYTHIC, listOf(Conditions.MinCheckInCount(50)))        { BattleFactory.createAxlu() }
    )

    fun getActivePool(context: Context): List<SpawnPool> =
        POOL.filter { spawn -> spawn.conditions.all { it.isMet(context) } }

    fun rollWildEncounter(context: Context): Makromon {
        val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

        // Ghost Plate aktivuje Shadirru (temná veverka) místo Gengara
        if (prefs.getBoolean("ghostPlateActive", false)) {
            prefs.edit().putBoolean("ghostPlateActive", false).apply()
            return BattleFactory.createShadirra()
        }

        val active = getActivePool(context)

        // Záchranný fallback – pokud je pool prázdný, spawnuje se Spirra
        if (active.isEmpty()) return BattleFactory.createSpirra()

        val totalWeight = active.sumOf { it.rarity.weight }
        var roll = kotlin.random.Random.nextInt(totalWeight)

        for (spawn in active) {
            roll -= spawn.rarity.weight
            if (roll < 0) return spawn.createMakromon()
        }

        // Druhý fallback – sem by se nemělo nikdy dojít
        return BattleFactory.createSpirra()
    }

    fun findById(id: String): SpawnPool? = POOL.find { it.id == id }

    val allEntries: List<SpawnPool> get() = POOL
}