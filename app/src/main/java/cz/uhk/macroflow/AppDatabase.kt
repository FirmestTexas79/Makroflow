package cz.uhk.macroflow

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Zvýšena verze na 3 kvůli ConsumedSnackEntity [cite: 2026-03-21]
@Database(
    entities = [SnackEntity::class, CheckInEntity::class, ConsumedSnackEntity::class],
    version = 3
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun snackDao(): SnackDao
    abstract fun checkInDao(): CheckInDao
    abstract fun consumedSnackDao(): ConsumedSnackDao // Nový přístup pro reálnou konzumaci

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
                    // POZOR: Při změně verze smaže stará data. Pro vývoj ideální.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}