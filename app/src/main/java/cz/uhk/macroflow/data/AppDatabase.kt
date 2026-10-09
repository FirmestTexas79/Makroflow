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
import kotlin.concurrent.thread

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
        CapturedMakromonEntity::class,
        UserItemEntity::class,
        MakrodexEntryEntity::class,
        MakrodexStatusEntity::class,
        SeenPokemonEntity::class,
        MakromonXpEntity::class,
        StepsEntity::class,
        AnalyticsCacheEntity::class,
        SnackUsageEntity::class,
        QuestProgressEntity::class,
        GameEventEntity::class,
        AdaptiveTdeeEntity::class,
        BarbellSetEntity::class,
        BarbellRepEntity::class,
        WorkoutSetEntity::class,
        WorkoutTemplateEntity::class,
        MealTemplateEntity::class
    ],
    version = 41,
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
    abstract fun capturedMakromonDao(): CapturedMakromonDao
    abstract fun userItemDao(): UserItemDao
    abstract fun makrodexEntryDao(): MakrodexEntryDao
    abstract fun makrodexStatusDao(): MakrodexStatusDao
    abstract fun makromonXpDao(): MakromonXpDao
    abstract fun stepsDao(): StepsDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun questDao(): QuestDao
    abstract fun gameEventDao(): GameEventDao
    abstract fun adaptiveTdeeDao(): AdaptiveTdeeDao
    abstract fun barbellDao(): BarbellDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun mealTemplateDao(): MealTemplateDao

    companion object {



        /** v39-40: přechod z verze 38 na 40. */
        val MIGRATION_38_40 = object : Migration(38, 40) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Pokud přibyly sloupce nebo tabulky, sem patří db.execSQL(...)
                // Pokud se schéma DB neměnilo, může zůstat prázdné
            }
        }


        /** v41: stálé ID chyceného Makromona (docs/adr/0076, stávající dostanou náhodné) a původní trenér (0077). */
        val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `captured_pokemon` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `captured_pokemon` ADD COLUMN `otName` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE `captured_pokemon` SET `uid` = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                        "substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(2)) || '-' || hex(randomblob(6))) WHERE `uid` = ''"
                )
            }
        }

        /** v34: log herních událostí + začátek fáze questu. */
        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `quest_progress` ADD COLUMN `stageStartedAt` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `game_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`payload` TEXT)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_game_events_type_timestamp` " +
                        "ON `game_events` (`type`, `timestamp`)"
                )
            }
        }

        /** v35: historie adaptivního odhadu výdeje (fáze B). */
        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `adaptive_tdee` (" +
                        "`date` TEXT NOT NULL, `status` TEXT NOT NULL, `factor` REAL NOT NULL, " +
                        "`modelTdee` REAL NOT NULL, `observedTdee` REAL, `adaptiveTdee` REAL NOT NULL, " +
                        "`confidence` REAL NOT NULL, `trendWeightKg` REAL, `weightChangeKgPerWeek` REAL, " +
                        "`weighIns` INTEGER NOT NULL, `loggedDays` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }

        /** v36: sledování činky kamerou – série a opakování. */
        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `barbell_sets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `exercise` TEXT NOT NULL, `setIndex` INTEGER NOT NULL, " +
                        "`loadKg` REAL, `plateDiameterCm` REAL NOT NULL, `repCount` INTEGER NOT NULL, " +
                        "`avgRomCm` REAL NOT NULL, `weightedRomCm` REAL NOT NULL, `romCvPct` REAL NOT NULL, " +
                        "`avgEccentricMs` INTEGER NOT NULL, `avgConcentricMs` INTEGER NOT NULL, " +
                        "`avgTotalMs` INTEGER NOT NULL, `bestMcv` REAL NOT NULL, `lastMcv` REAL NOT NULL, " +
                        "`velocityLossPct` REAL NOT NULL, `avgDeviationCm` REAL NOT NULL, `quality` REAL NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_barbell_sets_date` ON `barbell_sets` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `barbell_reps` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `setId` INTEGER NOT NULL, " +
                        "`repIndex` INTEGER NOT NULL, `startCm` REAL NOT NULL, `turnCm` REAL NOT NULL, " +
                        "`endCm` REAL NOT NULL, `romCm` REAL NOT NULL, `eccentricMs` INTEGER NOT NULL, " +
                        "`concentricMs` INTEGER NOT NULL, `pauseMs` INTEGER NOT NULL, `totalMs` INTEGER NOT NULL, " +
                        "`meanVelocity` REAL NOT NULL, `peakVelocity` REAL NOT NULL, `deviationCm` REAL NOT NULL, " +
                        "`quality` REAL NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_barbell_reps_setId` ON `barbell_reps` (`setId`)")
            }
        }

        /** v37: tréninkový deník – série (s tempem a šablonou) a šablony dnů PUSH/PULL/LEGS A/B (docs/adr/0023, 0024). */
        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_sets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, " +
                        "`weightKg` REAL NOT NULL, `reps` INTEGER NOT NULL, " +
                        "`slowEccentric` INTEGER NOT NULL, `template` TEXT, `rir` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_exerciseId_date` ON `workout_sets` (`exerciseId`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sets_date` ON `workout_sets` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_templates` (" +
                        "`templateKey` TEXT NOT NULL, `position` INTEGER NOT NULL, `exerciseId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`templateKey`, `position`))"
                )
            }
        }

        /** v38: šablony jídel a celých dnů (docs/adr/0027). */
        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `meal_templates` (" +
                        "`createdAt` INTEGER NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`items` TEXT NOT NULL, `lastUsedAt` INTEGER NOT NULL, PRIMARY KEY(`createdAt`))"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "macroflow_database"
                )
                    .addMigrations(MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_40, MIGRATION_40_41)
                    // Destruktivní fallback jen pro verze PŘED zavedením migrací.
                    // Od v33 se lokální data uživatelů už nikdy nesmažou potichu:
                    // chybějící migrace = pád při vývoji, ne ztráta dat v produkci.
                    .fallbackToDestructiveMigrationFrom(*(1..32).toList().toIntArray())
                    .allowMainThreadQueries()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL("INSERT INTO coins (id, balance) VALUES (1, 100)")
                            db.execSQL("INSERT INTO user_items (itemId, quantity) VALUES ('poke_ball', 5)")
                            db.execSQL("INSERT INTO user_items (itemId, quantity) VALUES ('great_ball', 3)")
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            thread { fillMakrodexEntries(db); renumberSpecies(db) }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * Přečíslování druhů (docs/adr/0063): Mysnic byl chvíli #033, teď je #032 (#033 je jeho vývoj).
         * Podle jména, takže to jde pouštět pořád dokola – opraví i Mysnica obnoveného ze staré zálohy v cloudu.
         */
        private fun renumberSpecies(db: SupportSQLiteDatabase) {
            runCatching { db.execSQL("UPDATE captured_pokemon SET makromonId = '032' WHERE name = 'MYSNIC' AND makromonId <> '032'") }
            // Happiny → Lumivix (2026-10-08): původní jméno patřilo cizí značce
            runCatching { db.execSQL("UPDATE captured_pokemon SET name = 'LUMIVIX' WHERE name = 'HAPPINY'") }
        }

        /** Makrodex z registru druhů (docs/adr/0067); zapisuje se při každém otevření, verzi DB netřeba zvedat. */
        private fun fillMakrodexEntries(db: SupportSQLiteDatabase) {
            fun q(v: String) = "'" + v.replace("'", "''") + "'"
            cz.uhk.macroflow.pokemon.species.SpeciesRegistry.ALL.forEach { s ->
                db.execSQL(
                    "INSERT OR REPLACE INTO pokedex_entries " +
                        "(makrodexId, drawableName, displayName, type, macroDesc, unlockedHint, evolveLevel, evolveToId) " +
                        "VALUES (${q(s.id)}, ${q(s.sprite)}, ${q(s.displayName)}, ${q(s.dexType)}, ${q(s.desc)}, ${q(s.hint)}, " +
                        "${s.evolves?.level ?: 0}, ${q(s.evolves?.to ?: "")})"
                )
            }
        }
    }
}