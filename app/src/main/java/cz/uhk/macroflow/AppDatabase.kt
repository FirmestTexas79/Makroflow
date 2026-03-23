package cz.uhk.macroflow

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Migrace 7 → 8: přidání mealContext do consumed_snacks
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE consumed_snacks ADD COLUMN mealContext TEXT NOT NULL DEFAULT 'NO_TRAINING'"
        )
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
        AchievementEntity::class
    ],
    version = 8,
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
                    .addMigrations(MIGRATION_7_8)
                    .allowMainThreadQueries()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}