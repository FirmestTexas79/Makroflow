package cz.uhk.macroflow.pokemon

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

// ✅ 1. ENTITA PRO VIDĚNÉ POKÉMONY
@Entity(tableName = "seen_pokemon")
data class SeenPokemonEntity(
    @PrimaryKey val pokemonId: String, // "050", "094"
    val seenAt: Long = System.currentTimeMillis()
)

// ✅ 2. DAO ROZHRANÍ PRO VIDĚNÉ POKÉMONY
@Dao
interface SeenPokemonDao {
    @Query("SELECT * FROM seen_pokemon")
    fun getAllSeen(): List<SeenPokemonEntity>

    // Oprava názvu parametru z :id na :pokemonId pro Room KSP kompilátor
    @Query("SELECT EXISTS(SELECT 1 FROM seen_pokemon WHERE pokemonId = :pokemonId)")
    fun hasSeen(pokemonId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun markSeen(entity: SeenPokemonEntity)
}