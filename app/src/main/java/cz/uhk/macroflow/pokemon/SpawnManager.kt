package cz.uhk.macroflow.pokemon

import android.content.Context
import cz.uhk.macroflow.common.AppPreferences
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.dashboard.MacroCalculator
import kotlinx.coroutines.runBlocking
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

// --- 📋 REGISTR KROKŮ A PODMÍNEK (Jednoduché přidávání) ---
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

    // 💡 Sem můžeš v budoucnu jednoduše přidávat:
    // val CALORIE_GOAL_MET = object : SpawnCondition { ... }
    // val PROTEIN_GOAL_MET = object : SpawnCondition { ... }
}

// --- 📦 DATOVÁ TŘÍDA PRO POOL ---
data class SpawnPool(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val conditions: List<SpawnCondition>,
    val createPokemon: () -> Pokemon
)

// --- 🧠 CENTRÁLNÍ MOZEK SPAWNOVÁNÍ ---
object SpawnManager {

    // 📜 Tady budeš mít časem všech 151 Pokémonů. Přidat nového znamená jen přidat JEDEN řádek!
    private val POOL: List<SpawnPool> = listOf(
        // ID, Jméno, Rarita, Seznam podmínek, Tovární funkce

        SpawnPool("001", "BULBASAUR",  Rarity.RARE,      listOf(Conditions.ALWAYS)) { BattleFactory.createBulbasaur() },
        SpawnPool("002", "IVYSAUR",    Rarity.EPIC,      listOf(Conditions.ALWAYS)) { BattleFactory.createIvysaur() },
        SpawnPool("003", "VENUSAUR",   Rarity.LEGENDARY, listOf(Conditions.ALWAYS)) { BattleFactory.createVenusaur() },
        SpawnPool("004", "CHARMANDER",Rarity.RARE,   listOf(Conditions.ALWAYS)) { BattleFactory.createCharmander() },
        SpawnPool("005", "CHARMELEON",Rarity.EPIC,   listOf(Conditions.ALWAYS)) { BattleFactory.createCharmeleon() },
        SpawnPool("006", "CHARIZARD", Rarity.LEGENDARY, listOf(Conditions.MinCheckInCount(14))) { BattleFactory.createCharizard() },
        SpawnPool("007", "SQUIRTLE",   Rarity.RARE,      listOf(Conditions.ALWAYS)) { BattleFactory.createSquirtle() },
        SpawnPool("008", "WARTORTLE",  Rarity.EPIC,      listOf(Conditions.ALWAYS)) { BattleFactory.createWartortle() },
        SpawnPool("009", "BLASTOISE",  Rarity.LEGENDARY, listOf(Conditions.ALWAYS)) { BattleFactory.createBlastoise() },
        SpawnPool("010", "CATERPIE",   Rarity.COMMON, listOf(Conditions.ALWAYS)) { BattleFactory.createCaterpie() },
        SpawnPool("011", "METAPOD",   Rarity.EPIC, listOf(Conditions.ALWAYS)) { BattleFactory.createCaterpie() },
        SpawnPool("012", "BUTTERFREE",   Rarity.LEGENDARY, listOf(Conditions.ALWAYS)) { BattleFactory.createCaterpie() },
        SpawnPool("013", "WEEDLE",     Rarity.COMMON,    listOf(Conditions.ALWAYS)) { BattleFactory.createWeedle() },
        SpawnPool("014", "KAKUNA",     Rarity.RARE,  listOf(Conditions.ALWAYS)) { BattleFactory.createKakuna() },
        SpawnPool("016", "PIDGEY",    Rarity.COMMON,    listOf(Conditions.ALWAYS)) { BattleFactory.createPidgey() },
        SpawnPool("017", "PIDGEOTTO", Rarity.RARE,  listOf(Conditions.ALWAYS)) { BattleFactory.createPidgeotto() },
        SpawnPool("018", "PIDGEOT",   Rarity.EPIC,      listOf(Conditions.ALWAYS)) { BattleFactory.createPidgeot() },SpawnPool("015", "BEEDRILL",   Rarity.LEGENDARY,      listOf(Conditions.ALWAYS)) { BattleFactory.createBeedrill() },
        SpawnPool("019", "RATTATA",   Rarity.COMMON,   listOf(Conditions.ALWAYS)) { BattleFactory.createRattata() },
        SpawnPool("020", "RATICATE",  Rarity.RARE, listOf(Conditions.ALWAYS)) { BattleFactory.createRaticate() },
        SpawnPool("021", "SPEAROW",   Rarity.COMMON,   listOf(Conditions.ALWAYS)) { BattleFactory.createSpearow() },
        SpawnPool("022", "FEAROW",    Rarity.RARE,     listOf(Conditions.ALWAYS)) { BattleFactory.createFearow() },
        SpawnPool("023", "EKANS",     Rarity.COMMON,   listOf(Conditions.ALWAYS)) { BattleFactory.createEkans() },
        SpawnPool("024", "ARBOK",     Rarity.RARE,     listOf(Conditions.ALWAYS)) { BattleFactory.createArbok() },

        SpawnPool("050", "DIGLETT",   Rarity.COMMON, listOf(Conditions.ALWAYS)) { BattleFactory.createDiglett() },
        SpawnPool("051", "DUGTRIO",   Rarity.EPIC, listOf(Conditions.ALWAYS)) { BattleFactory.createDugtrio() },

        SpawnPool("025", "PIKACHU",   Rarity.RARE, listOf(Conditions.ALWAYS)) { BattleFactory.createPikachu() },
        SpawnPool("133", "EEVEE",     Rarity.COMMON, listOf(Conditions.ALWAYS)) { BattleFactory.createEevee() },

        SpawnPool("092", "GASTLY",    Rarity.RARE,   listOf(Conditions.NIGHT_ONLY)) { BattleFactory.createGastly() },

        // 🌊 Squirtle se odemkne jen při splnění vody v Dashboardu!



        SpawnPool("137", "PORYGON", Rarity.EPIC, listOf(Conditions.MinCheckInCount(3))) { BattleFactory.createPorygon() },

        SpawnPool("093", "HAUNTER",   Rarity.EPIC,   listOf(Conditions.NIGHT_ONLY)) { BattleFactory.createHaunter() },
        SpawnPool("094", "GENGAR",    Rarity.EPIC,   listOf(Conditions.NIGHT_ONLY)) { BattleFactory.createGengar() },


        SpawnPool("132", "DITTO", Rarity.LEGENDARY, listOf(Conditions.ALWAYS)) { BattleFactory.createDitto() },


        // Přidej do POOL v SpawnManager.kt (třeba pod Kangaskhana)
        SpawnPool("131", "LAPRAS", Rarity.EPIC, listOf(Conditions.ALWAYS)) { BattleFactory.createLapras() },
        SpawnPool("115", "KANGASKHAN",Rarity.EPIC,   listOf(Conditions.ALWAYS)) { BattleFactory.createKangaskhan() },
        // 💤 Snorlax se odemkne až po 7 splněných ranních check-inech
        SpawnPool("143", "SNORLAX",   Rarity.EPIC,   listOf(Conditions.MinCheckInCount(7))) { BattleFactory.createSnorlax() },

        SpawnPool("150", "MEWTWO",    Rarity.LEGENDARY, listOf(Conditions.MinCheckInCount(30))) { BattleFactory.createMewtwo() },

        SpawnPool("151", "MEW",       Rarity.MYTHIC, listOf(Conditions.MinCheckInCount(50))) { BattleFactory.createWildMew() }
    )

    fun getActivePool(context: Context): List<SpawnPool> =
        POOL.filter { spawn -> spawn.conditions.all { it.isMet(context) } }

    fun rollWildEncounter(context: Context): Pokemon {
        if (AppPreferences.isGhostPlateActiveSync(context)) {
            runBlocking { AppPreferences.setGhostPlateActive(context, false) }
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