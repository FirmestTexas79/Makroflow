package cz.uhk.macroflow

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Zvýšena verze na 2, přidána CheckInEntity [cite: 2026-03-01]
@Database(entities = [SnackEntity::class, CheckInEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun snackDao(): SnackDao
    abstract fun checkInDao(): CheckInDao // Nový přístup k check-inům [cite: 2026-03-01]

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "macroflow_database"
                )
                    .fallbackToDestructiveMigration() // Důležité pro hladký přechod na verzi 2 [cite: 2026-03-01]
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}