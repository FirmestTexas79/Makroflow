package cz.uhk.macroflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cz.uhk.macroflow.achievements.AchievementDao
import cz.uhk.macroflow.achievements.AchievementEntity
import cz.uhk.macroflow.pokemon.CapturedPokemonDao
import cz.uhk.macroflow.pokemon.CapturedPokemonEntity
import cz.uhk.macroflow.pokemon.CoinDao
import cz.uhk.macroflow.pokemon.CoinEntity
import cz.uhk.macroflow.pokemon.PokedexEntryDao
import cz.uhk.macroflow.pokemon.PokedexEntryEntity
import cz.uhk.macroflow.pokemon.UserItemDao
import cz.uhk.macroflow.pokemon.UserItemEntity

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE consumed_snacks ADD COLUMN mealContext TEXT NOT NULL DEFAULT 'NO_TRAINING'")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS coins (id INTEGER PRIMARY KEY NOT NULL DEFAULT 1, balance INTEGER NOT NULL DEFAULT 0)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS captured_pokemon (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, pokemonId TEXT NOT NULL, name TEXT NOT NULL, isShiny INTEGER NOT NULL DEFAULT 0, isLocked INTEGER NOT NULL DEFAULT 0, caughtDate INTEGER NOT NULL)")
        database.execSQL("CREATE TABLE IF NOT EXISTS user_items (itemId TEXT PRIMARY KEY NOT NULL, quantity INTEGER NOT NULL DEFAULT 0)")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS pokedex_entries (pokedexId TEXT PRIMARY KEY NOT NULL, webName TEXT NOT NULL, displayName TEXT NOT NULL, type TEXT NOT NULL, macroDesc TEXT NOT NULL)")
    }
}

@Database(
    entities = [
        SnackEntity::class,
        CheckInEntity::class,
        ConsumedSnackEntity::class,
        UserProfileEntity::class,
        BodyMetricsEntity::class,
        WaterEntity::class,
        AchievementEntity::class,
        CoinEntity::class,
        CapturedPokemonEntity::class,
        UserItemEntity::class,
        PokedexEntryEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun snackDao(): SnackDao
    abstract fun checkInDao(): CheckInDao
    abstract fun consumedSnackDao(): ConsumedSnackDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun bodyMetricsDao(): BodyMetricsDao
    abstract fun waterDao(): WaterDao
    abstract fun achievementDao(): AchievementDao
    abstract fun coinDao(): CoinDao
    abstract fun capturedPokemonDao(): CapturedPokemonDao
    abstract fun userItemDao(): UserItemDao
    abstract fun pokedexEntryDao(): PokedexEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "macroflow_database"
                )
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .allowMainThreadQueries()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)

                            db.execSQL("INSERT INTO coins (id, balance) VALUES (1, 100)")

                            // 🎒 Inicializace batohu o Great bally
                            db.execSQL("INSERT INTO user_items (itemId, quantity) VALUES ('poke_ball', 5)")
                            db.execSQL("INSERT INTO user_items (itemId, quantity) VALUES ('great_ball', 3)")

                            val kantoList = getKantoNames()

                            kantoList.forEachIndexed { index, pair ->
                                val idStr = String.format("%03d", index + 1)
                                val name = pair.first
                                val urlName = name.lowercase().replace(" ", "-").replace(".", "").replace("♀", "-f").replace("♂", "-m")
                                val type = pair.second
                                val desc = pair.third

                                db.execSQL(
                                    "INSERT INTO pokedex_entries (pokedexId, webName, displayName, type, macroDesc) VALUES ('$idStr', '$urlName', '$name', '$type', '$desc')"
                                )
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private fun getKantoNames(): List<Triple<String, String, String>> = listOf(
            Triple("Bulbasaur", "Kanto", "Zdravá strava a makra pro Bulbasaur budou brzy načtena."),
            Triple("Ivysaur", "Kanto", "Zdravá strava a makra pro Ivysaur budou brzy načtena."),
            Triple("Venusaur", "Kanto", "Zdravá strava a makra pro Venusaur budou brzy načtena."),
            Triple("Charmander", "Kanto", "Zdravá strava a makra pro Charmander budou brzy načtena."),
            Triple("Charmeleon", "Kanto", "Zdravá strava a makra pro Charmeleon budou brzy načtena."),
            Triple("Charizard", "Kanto", "Zdravá strava a makra pro Charizard budou brzy načtena."),
            Triple("Squirtle", "Kanto", "Zdravá strava a makra pro Squirtle budou brzy načtena."),
            Triple("Wartortle", "Kanto", "Zdravá strava a makra pro Wartortle budou brzy načtena."),
            Triple("Blastoise", "Kanto", "Zdravá strava a makra pro Blastoise budou brzy načtena."),
            Triple("Caterpie", "Kanto", "Zdravá strava a makra pro Caterpie budou brzy načtena."),
            Triple("Metapod", "Kanto", "Zdravá strava a makra pro Metapod budou brzy načtena."),
            Triple("Butterfree", "Kanto", "Zdravá strava a makra pro Butterfree budou brzy načtena."),
            Triple("Weedle", "Kanto", "Zdravá strava a makra pro Weedle budou brzy načtena."),
            Triple("Kakuna", "Kanto", "Zdravá strava a makra pro Kakuna budou brzy načtena."),
            Triple("Beedrill", "Kanto", "Zdravá strava a makra pro Beedrill budou brzy načtena."),
            Triple("Pidgey", "Kanto", "Zdravá strava a makra pro Pidgey budou brzy načtena."),
            Triple("Pidgeotto", "Kanto", "Zdravá strava a makra pro Pidgeotto budou brzy načtena."),
            Triple("Pidgeot", "Kanto", "Zdravá strava a makra pro Pidgeot budou brzy načtena."),
            Triple("Rattata", "Kanto", "Zdravá strava a makra pro Rattata budou brzy načtena."),
            Triple("Raticate", "Kanto", "Zdravá strava a makra pro Raticate budou brzy načtena."),
            Triple("Spearow", "Kanto", "Zdravá strava a makra pro Spearow budou brzy načtena."),
            Triple("Fearow", "Kanto", "Zdravá strava a makra pro Fearow budou brzy načtena."),
            Triple("Ekans", "Kanto", "Zdravá strava a makra pro Ekans budou brzy načtena."),
            Triple("Arbok", "Kanto", "Zdravá strava a makra pro Arbok budou brzy načtena."),
            Triple("Pikachu", "Kanto", "Zdravá strava a makra pro Pikachu budou brzy načtena."),
            Triple("Raichu", "Kanto", "Zdravá strava a makra pro Raichu budou brzy načtena."),
            Triple("Sandshrew", "Kanto", "Zdravá strava a makra pro Sandshrew budou brzy načtena."),
            Triple("Sandslash", "Kanto", "Zdravá strava a makra pro Sandslash budou brzy načtena."),
            Triple("Nidoran♀", "Kanto", "Zdravá strava a makra pro Nidoran♀ budou brzy načtena."),
            Triple("Nidorina", "Kanto", "Zdravá strava a makra pro Nidorina budou brzy načtena."),
            Triple("Nidoqueen", "Kanto", "Zdravá strava a makra pro Nidoqueen budou brzy načtena."),
            Triple("Nidoran♂", "Kanto", "Zdravá strava a makra pro Nidoran♂ budou brzy načtena."),
            Triple("Nidorino", "Kanto", "Zdravá strava a makra pro Nidorino budou brzy načtena."),
            Triple("Nidoking", "Kanto", "Zdravá strava a makra pro Nidoking budou brzy načtena."),
            Triple("Clefairy", "Kanto", "Zdravá strava a makra pro Clefairy budou brzy načtena."),
            Triple("Clefable", "Kanto", "Zdravá strava a makra pro Clefable budou brzy načtena."),
            Triple("Vulpix", "Kanto", "Zdravá strava a makra pro Vulpix budou brzy načtena."),
            Triple("Ninetales", "Kanto", "Zdravá strava a makra pro Ninetales budou brzy načtena."),
            Triple("Jigglypuff", "Kanto", "Zdravá strava a makra pro Jigglypuff budou brzy načtena."),
            Triple("Wigglytuff", "Kanto", "Zdravá strava a makra pro Wigglytuff budou brzy načtena."),
            Triple("Zubat", "Kanto", "Zdravá strava a makra pro Zubat budou brzy načtena."),
            Triple("Golbat", "Kanto", "Zdravá strava a makra pro Golbat budou brzy načtena."),
            Triple("Oddish", "Kanto", "Zdravá strava a makra pro Oddish budou brzy načtena."),
            Triple("Gloom", "Kanto", "Zdravá strava a makra pro Gloom budou brzy načtena."),
            Triple("Vileplume", "Kanto", "Zdravá strava a makra pro Vileplume budou brzy načtena."),
            Triple("Paras", "Kanto", "Zdravá strava a makra pro Paras budou brzy načtena."),
            Triple("Parasect", "Kanto", "Zdravá strava a makra pro Parasect budou brzy načtena."),
            Triple("Venonat", "Kanto", "Zdravá strava a makra pro Venonat budou brzy načtena."),
            Triple("Venomoth", "Kanto", "Zdravá strava a makra pro Venomoth budou brzy načtena."),

            Triple("Diglett", "ZEMĚ / VLÁKNINA", "Král ranního vyprazdňování! Tento podzemní tvor symbolizuje zdravou peristaltiku střev. Pokud tvůj trůnní rituál vázne, přidej rozpustnou vlákninu a dostatek vody."),

            Triple("Dugtrio", "Kanto", "Zdravá strava a makra pro Dugtrio budou brzy načtena."),
            Triple("Meowth", "Kanto", "Zdravá strava a makra pro Meowth budou brzy načtena."),
            Triple("Persian", "Kanto", "Zdravá strava a makra pro Persian budou brzy načtena."),
            Triple("Psyduck", "Kanto", "Zdravá strava a makra pro Psyduck budou brzy načtena."),
            Triple("Golduck", "Kanto", "Zdravá strava a makra pro Golduck budou brzy načtena."),
            Triple("Mankey", "Kanto", "Zdravá strava a makra pro Mankey budou brzy načtena."),
            Triple("Primeape", "Kanto", "Zdravá strava a makra pro Primeape budou brzy načtena."),
            Triple("Growlithe", "Kanto", "Zdravá strava a makra pro Growlithe budou brzy načtena."),
            Triple("Arcanine", "Kanto", "Zdravá strava a makra pro Arcanine budou brzy načtena."),
            Triple("Poliwag", "Kanto", "Zdravá strava a makra pro Poliwag budou brzy načtena."),
            Triple("Poliwhirl", "Kanto", "Zdravá strava a makra pro Poliwhirl budou brzy načtena."),
            Triple("Poliwrath", "Kanto", "Zdravá strava a makra pro Poliwrath budou brzy načtena."),
            Triple("Abra", "Kanto", "Zdravá strava a makra pro Abra budou brzy načtena."),
            Triple("Kadabra", "Kanto", "Zdravá strava a makra pro Kadabra budou brzy načtena."),
            Triple("Alakazam", "Kanto", "Zdravá strava a makra pro Alakazam budou brzy načtena."),
            Triple("Machop", "Kanto", "Zdravá strava a makra pro Machop budou brzy načtena."),
            Triple("Machoke", "Kanto", "Zdravá strava a makra pro Machoke budou brzy načtena."),
            Triple("Machamp", "Kanto", "Zdravá strava a makra pro Machamp budou brzy načtena."),
            Triple("Bellsprout", "Kanto", "Zdravá strava a makra pro Bellsprout budou brzy načtena."),
            Triple("Weepinbell", "Kanto", "Zdravá strava a makra pro Weepinbell budou brzy načtena."),
            Triple("Victreebel", "Kanto", "Zdravá strava a makra pro Victreebel budou brzy načtena."),
            Triple("Tentacool", "Kanto", "Zdravá strava a makra pro Tentacool budou brzy načtena."),
            Triple("Tentacruel", "Kanto", "Zdravá strava a makra pro Tentacruel budou brzy načtena."),
            Triple("Geodude", "Kanto", "Zdravá strava a makra pro Geodude budou brzy načtena."),
            Triple("Graveler", "Kanto", "Zdravá strava a makra pro Graveler budou brzy načtena."),
            Triple("Golem", "Kanto", "Zdravá strava a makra pro Golem budou brzy načtena."),
            Triple("Ponyta", "Kanto", "Zdravá strava a makra pro Ponyta budou brzy načtena."),
            Triple("Rapidash", "Kanto", "Zdravá strava a makra pro Rapidash budou brzy načtena."),
            Triple("Slowpoke", "Kanto", "Zdravá strava a makra pro Slowpoke budou brzy načtena."),
            Triple("Slowbro", "Kanto", "Zdravá strava a makra pro Slowbro budou brzy načtena."),
            Triple("Magnemite", "Kanto", "Zdravá strava a makra pro Magnemite budou brzy načtena."),
            Triple("Magneton", "Kanto", "Zdravá strava a makra pro Magneton budou brzy načtena."),
            Triple("Farfetchd", "Kanto", "Zdravá strava a makra pro Farfetchd budou brzy načtena."),
            Triple("Doduo", "Kanto", "Zdravá strava a makra pro Doduo budou brzy načtena."),
            Triple("Dodrio", "Kanto", "Zdravá strava a makra pro Dodrio budou brzy načtena."),
            Triple("Seel", "Kanto", "Zdravá strava a makra pro Seel budou brzy načtena."),
            Triple("Dewgong", "Kanto", "Zdravá strava a makra pro Dewgong budou brzy načtena."),
            Triple("Grimer", "Kanto", "Zdravá strava a makra pro Grimer budou brzy načtena."),
            Triple("Muk", "Kanto", "Zdravá strava a makra pro Muk budou brzy načtena."),
            Triple("Shellder", "Kanto", "Zdravá strava a makra pro Shellder budou brzy načtena."),
            Triple("Cloyster", "Kanto", "Zdravá strava a makra pro Cloyster budou brzy načtena."),
            Triple("Gastly", "Kanto", "Zdravá strava a makra pro Gastly budou brzy načtena."),
            Triple("Haunter", "Kanto", "Zdravá strava a makra pro Haunter budou brzy načtena."),
            Triple("Gengar", "Kanto", "Zdravá strava a makra pro Gengar budou brzy načtena."),
            Triple("Onix", "Kanto", "Zdravá strava a makra pro Onix budou brzy načtena."),
            Triple("Drowzee", "Kanto", "Zdravá strava a makra pro Drowzee budou brzy načtena."),
            Triple("Hypno", "Kanto", "Zdravá strava a makra pro Hypno budou brzy načtena."),
            Triple("Krabby", "Kanto", "Zdravá strava a makra pro Krabby budou brzy načtena."),
            Triple("Kingler", "Kanto", "Zdravá strava a makra pro Kingler budou brzy načtena."),
            Triple("Voltorb", "Kanto", "Zdravá strava a makra pro Voltorb budou brzy načtena."),
            Triple("Electrode", "Kanto", "Zdravá strava a makra pro Electrode budou brzy načtena."),
            Triple("Exeggcute", "Kanto", "Zdravá strava a makra pro Exeggcute budou brzy načtena."),
            Triple("Exeggutor", "Kanto", "Zdravá strava a makra pro Exeggutor budou brzy načtena."),
            Triple("Cubone", "Kanto", "Zdravá strava a makra pro Cubone budou brzy načtena."),
            Triple("Marowak", "Kanto", "Zdravá strava a makra pro Marowak budou brzy načtena."),
            Triple("Hitmonlee", "Kanto", "Zdravá strava a makra pro Hitmonlee budou brzy načtena."),
            Triple("Hitmonchan", "Kanto", "Zdravá strava a makra pro Hitmonchan budou brzy načtena."),
            Triple("Lickitung", "Kanto", "Zdravá strava a makra pro Lickitung budou brzy načtena."),
            Triple("Koffing", "Kanto", "Zdravá strava a makra pro Koffing budou brzy načtena."),
            Triple("Weezing", "Kanto", "Zdravá strava a makra pro Weezing budou brzy načtena."),
            Triple("Rhyhorn", "Kanto", "Zdravá strava a makra pro Rhyhorn budou brzy načtena."),
            Triple("Rhydon", "Kanto", "Zdravá strava a makra pro Rhydon budou brzy načtena."),
            Triple("Chansey", "Kanto", "Zdravá strava a makra pro Chansey budou brzy načtena."),
            Triple("Tangela", "Kanto", "Zdravá strava a makra pro Tangela budou brzy načtena."),
            Triple("Kangaskhan", "Kanto", "Zdravá strava a makra pro Kangaskhan budou brzy načtena."),
            Triple("Horsea", "Kanto", "Zdravá strava a makra pro Horsea budou brzy načtena."),
            Triple("Seadra", "Kanto", "Zdravá strava a makra pro Seadra budou brzy načtena."),
            Triple("Goldeen", "Kanto", "Zdravá strava a makra pro Goldeen budou brzy načtena."),
            Triple("Seaking", "Kanto", "Zdravá strava a makra pro Seaking budou brzy načtena."),
            Triple("Staryu", "Kanto", "Zdravá strava a makra pro Staryu budou brzy načtena."),
            Triple("Starmie", "Kanto", "Zdravá strava a makra pro Starmie budou brzy načtena."),
            Triple("Mr. Mime", "Kanto", "Zdravá strava a makra pro Mr. Mime budou brzy načtena."),
            Triple("Scyther", "Kanto", "Zdravá strava a makra pro Scyther budou brzy načtena."),
            Triple("Jynx", "Kanto", "Zdravá strava a makra pro Jynx budou brzy načtena."),
            Triple("Electabuzz", "Kanto", "Zdravá strava a makra pro Electabuzz budou brzy načtena."),
            Triple("Magmar", "Kanto", "Zdravá strava a makra pro Magmar budou brzy načtena."),
            Triple("Pinsir", "Kanto", "Zdravá strava a makra pro Pinsir budou brzy načtena."),
            Triple("Tauros", "Kanto", "Zdravá strava a makra pro Tauros budou brzy načtena."),
            Triple("Magikarp", "Kanto", "Zdravá strava a makra pro Magikarp budou brzy načtena."),
            Triple("Gyarados", "Kanto", "Zdravá strava a makra pro Gyarados budou brzy načtena."),
            Triple("Lapras", "Kanto", "Zdravá strava a makra pro Lapras budou brzy načtena."),
            Triple("Ditto", "Kanto", "Zdravá strava a makra pro Ditto budou brzy načtena."),
            Triple("Eevee", "Kanto", "Zdravá strava a makra pro Eevee budou brzy načtena."),
            Triple("Vaporeon", "Kanto", "Zdravá strava a makra pro Vaporeon budou brzy načtena."),
            Triple("Jolteon", "Kanto", "Zdravá strava a makra pro Jolteon budou brzy načtena."),
            Triple("Flareon", "Kanto", "Zdravá strava a makra pro Flareon budou brzy načtena."),
            Triple("Porygon", "Kanto", "Zdravá strava a makra pro Porygon budou brzy načtena."),
            Triple("Omanyte", "Kanto", "Zdravá strava a makra pro Omanyte budou brzy načtena."),
            Triple("Omastar", "Kanto", "Zdravá strava a makra pro Omastar budou brzy načtena."),
            Triple("Kabuto", "Kanto", "Zdravá strava a makra pro Kabuto budou brzy načtena."),
            Triple("Kabutops", "Kanto", "Zdravá strava a makra pro Kabutops budou brzy načtena."),
            Triple("Aerodactyl", "Kanto", "Zdravá strava a makra pro Aerodactyl budou brzy načtena."),
            Triple("Snorlax", "Kanto", "Zdravá strava a makra pro Snorlax budou brzy načtena."),
            Triple("Articuno", "Kanto", "Zdravá strava a makra pro Articuno budou brzy načtena."),
            Triple("Zapdos", "Kanto", "Zdravá strava a makra pro Zapdos budou brzy načtena."),
            Triple("Moltres", "Kanto", "Zdravá strava a makra pro Moltres budou brzy načtena."),
            Triple("Dratini", "Kanto", "Zdravá strava a makra pro Dratini budou brzy načtena."),
            Triple("Dragonair", "Kanto", "Zdravá strava a makra pro Dragonair budou brzy načtena."),
            Triple("Dragonite", "Kanto", "Zdravá strava a makra pro Dragonite budou brzy načtena."),
            Triple("Mewtwo", "Kanto", "Zdravá strava a makra pro Mewtwo budou brzy načtena."),
            Triple("Mew", "Kanto", "Zdravá strava a makra pro Mew budou brzy načtena.")
        )
    }
}