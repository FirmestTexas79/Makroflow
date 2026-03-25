package cz.uhk.macroflow.pokemon

import androidx.room.*

// --- 💰 CELKOVÝ POČET COINŮ (Singleton pattern) ---
@Entity(tableName = "coins")
data class CoinEntity(
    @PrimaryKey val id: Int = 1,
    val balance: Int = 0
)

@Dao
interface CoinDao {
    @Query("SELECT * FROM coins WHERE id = 1 LIMIT 1")
    fun getBalance(): CoinEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun setBalance(entity: CoinEntity)

    @Transaction
    fun addCoins(amount: Int) {
        val current = getBalance()?.balance ?: 0
        setBalance(CoinEntity(balance = current + amount))
    }

    @Transaction
    fun spendCoins(amount: Int): Boolean {
        val current = getBalance()?.balance ?: 0
        if (current < amount) return false
        setBalance(CoinEntity(balance = current - amount))
        return true
    }
}

// --- 🎒 POKÉ-KAPSA (Chycení Pokémoni) ---
@Entity(tableName = "captured_pokemon")
data class CapturedPokemonEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pokemonId: String,          // např. "094" pro Gengara
    val name: String,
    val isShiny: Boolean = false,
    val isLocked: Boolean = false, // Manuální zámek proti smazání 🔒
    val caughtDate: Long = System.currentTimeMillis()
)

@Dao
interface CapturedPokemonDao {
    @Query("SELECT * FROM captured_pokemon ORDER BY caughtDate DESC")
    fun getAllCaught(): List<CapturedPokemonEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM captured_pokemon WHERE pokemonId = :pokemonId LIMIT 1)")
    fun hasBeenCaught(pokemonId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPokemon(pokemon: CapturedPokemonEntity)

    @Update
    fun updatePokemon(pokemon: CapturedPokemonEntity)

    @Delete
    fun deletePokemon(pokemon: CapturedPokemonEntity)

    @Query("DELETE FROM captured_pokemon WHERE pokemonId = :id")
    fun deletePokemonById(id: String)
}

// --- 🎒 BATOH (Předměty a Bally) ---
@Entity(tableName = "user_items")
data class UserItemEntity(
    @PrimaryKey val itemId: String, // "poke_ball", "great_ball"
    val quantity: Int = 0
)

@Dao
interface UserItemDao {
    @Query("SELECT * FROM user_items")
    fun getAllItems(): List<UserItemEntity>

    @Query("SELECT * FROM user_items WHERE itemId = :itemId LIMIT 1")
    fun getItem(itemId: String): UserItemEntity?

    @Query("SELECT quantity FROM user_items WHERE itemId = :itemId LIMIT 1")
    fun getItemCount(itemId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateItem(item: UserItemEntity)

    @Transaction
    fun addItem(itemId: String, amount: Int) {
        val current = getItem(itemId)?.quantity ?: 0
        insertOrUpdateItem(UserItemEntity(itemId, current + amount))
    }

    @Transaction
    fun consumeItem(itemId: String, amount: Int): Boolean {
        val current = getItem(itemId)?.quantity ?: 0
        if (current < amount) return false
        insertOrUpdateItem(UserItemEntity(itemId, current - amount))
        return true
    }
}

// --- 📖 STATICKÁ ENCYKLOPEDIE (Nutriční informace do Pokédexu) ---
@Entity(tableName = "pokedex_entries")
data class PokedexEntryEntity(
    @PrimaryKey val pokedexId: String, // "001", "002"
    val webName: String,               // "bulbasaur"
    val displayName: String,           // "Bulbasaur"
    val type: String,                  // "GRASS / POISON"
    val macroDesc: String              // "Vyvážená volba plná vlákniny a sacharidů pro svaly."
)

@Dao
interface PokedexEntryDao {
    @Query("SELECT * FROM pokedex_entries ORDER BY pokedexId ASC")
    fun getAllEntries(): List<PokedexEntryEntity>

    @Query("SELECT * FROM pokedex_entries WHERE pokedexId = :id LIMIT 1")
    fun getEntry(id: String): PokedexEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entries: List<PokedexEntryEntity>)

    @Query("SELECT COUNT(*) FROM pokedex_entries")
    fun getCount(): Int
}

// --- 📖 TRVALÝ ZÁZNAM POKÉDEXU (Jednou chycen, navždy objeven) ---
@Entity(tableName = "pokedex_status")
data class PokedexStatusEntity(
    @PrimaryKey val pokemonId: String, // "050", "094"
    val unlocked: Boolean = true,
    val unlockedDate: Long = System.currentTimeMillis()
)

@Dao
interface PokedexStatusDao {
    @Query("SELECT pokemonId FROM pokedex_status")
    fun getUnlockedIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM pokedex_status WHERE pokemonId = :pokemonId)")
    fun isUnlocked(pokemonId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun unlockPokemon(entity: PokedexStatusEntity)
}