package cz.uhk.macroflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cz.uhk.macroflow.achievements.AchievementDao
import cz.uhk.macroflow.achievements.AchievementEntity
import cz.uhk.macroflow.pokemon.*

// ── Migrace ───────────────────────────────────────────────────────────

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE consumed_snacks ADD COLUMN mealContext TEXT NOT NULL DEFAULT 'NO_TRAINING'"
        )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS coins (id INTEGER PRIMARY KEY NOT NULL DEFAULT 1, balance INTEGER NOT NULL DEFAULT 0)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS captured_pokemon (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, pokemonId TEXT NOT NULL, name TEXT NOT NULL, isShiny INTEGER NOT NULL DEFAULT 0, isLocked INTEGER NOT NULL DEFAULT 0, caughtDate INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS user_items (itemId TEXT PRIMARY KEY NOT NULL, quantity INTEGER NOT NULL DEFAULT 0)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS pokedex_entries (pokedexId TEXT PRIMARY KEY NOT NULL, webName TEXT NOT NULL, displayName TEXT NOT NULL, type TEXT NOT NULL, macroDesc TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS pokedex_status (pokemonId TEXT PRIMARY KEY NOT NULL, unlocked INTEGER NOT NULL DEFAULT 1, unlockedDate INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS seen_pokemon (pokemonId TEXT PRIMARY KEY NOT NULL, seenAt INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // XP tabulka pro pokémony
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS pokemon_xp (pokemonId TEXT PRIMARY KEY NOT NULL, totalXp INTEGER NOT NULL DEFAULT 0, lastDailyRewardDate TEXT NOT NULL DEFAULT '')"
        )
    }
}

// ── Database ──────────────────────────────────────────────────────────

@Database(
    entities = [
        // Core
        SnackEntity::class,
        CheckInEntity::class,
        ConsumedSnackEntity::class,
        UserProfileEntity::class,
        BodyMetricsEntity::class,
        WaterEntity::class,
        // Achievements
        AchievementEntity::class,
        // Pokémon systém
        CoinEntity::class,
        CapturedPokemonEntity::class,
        UserItemEntity::class,
        PokedexEntryEntity::class,
        PokedexStatusEntity::class,
        SeenPokemonEntity::class,
        PokemonXpEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // Core DAOs
    abstract fun snackDao(): SnackDao
    abstract fun checkInDao(): CheckInDao
    abstract fun consumedSnackDao(): ConsumedSnackDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun bodyMetricsDao(): BodyMetricsDao
    abstract fun waterDao(): WaterDao

    // Achievement DAO
    abstract fun achievementDao(): AchievementDao

    // Pokémon DAOs
    abstract fun coinDao(): CoinDao
    abstract fun capturedPokemonDao(): CapturedPokemonDao
    abstract fun userItemDao(): UserItemDao
    abstract fun pokedexEntryDao(): PokedexEntryDao
    abstract fun pokedexStatusDao(): PokedexStatusDao
    abstract fun seenPokemonDao(): SeenPokemonDao
    abstract fun pokemonXpDao(): PokemonXpDao

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
                    .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .allowMainThreadQueries()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}