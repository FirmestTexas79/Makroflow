package cz.uhk.macroflow.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Herní událost vzniklá ve funkční části aplikace (výživa, trénink, …),
 * kterou potřebuje Makrosvět (questy, achievementy, evoluce).
 *
 * Proč tabulka a ne callback: funkční část běží v [cz.uhk.macroflow.common.MainActivity],
 * herní svět v [cz.uhk.macroflow.pokemon.MakromonMapActivity]. Obě aktivity nikdy
 * nežijí zároveň, takže přímé volání (`activity as? MakromonMapActivity`) nefunguje.
 * Producent událost jen zapíše, konzument (QuestManager) si ji přečte, až bude potřebovat.
 * Log je append-only, takže se dá auditovat i zpětně přepočítat.
 */
@Entity(
    tableName = "game_events",
    indices = [Index(value = ["type", "timestamp"])]
)
data class GameEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,          // GameEventType.name
    val timestamp: Long,       // epoch ms
    val date: String,          // yyyy-MM-dd (lokální den, stejně jako ostatní tabulky)
    val payload: String? = null
)

enum class GameEventType {
    /** Úspěšně naskenovaný a v OpenFoodFacts dohledaný čárový kód. payload = kód. */
    BARCODE_SCANNED
}

@Dao
interface GameEventDao {
    @Insert
    fun insert(event: GameEventEntity): Long

    @Query("SELECT COUNT(*) FROM game_events WHERE type = :type AND timestamp >= :since")
    fun countSince(type: String, since: Long): Int

    /** Emituje při každé změně tabulky – slouží jako spouštěč přepočtu questů. */
    @Query("SELECT COUNT(*) FROM game_events WHERE type = :type")
    fun observeCount(type: String): Flow<Int>
}

/** Jediný vstupní bod pro zápis událostí z funkční části. */
object GameEvents {
    suspend fun record(db: AppDatabase, type: GameEventType, payload: String? = null) {
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            db.gameEventDao().insert(
                GameEventEntity(
                    type = type.name,
                    timestamp = now,
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now)),
                    payload = payload
                )
            )
        }
    }
}
