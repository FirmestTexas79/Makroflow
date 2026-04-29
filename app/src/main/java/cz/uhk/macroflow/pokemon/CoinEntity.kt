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

// --- 🎒 MAKRO-KAPSA (Chycení Makromoni) ---
@Entity(tableName = "captured_pokemon")  // název tabulky zachován pro zpětnou kompatibilitu DB
data class CapturedMakromonEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var makromonId: String,         // Např. "012" (Spirra)
    var name: String,               // Např. "SPIRRA"
    val isShiny: Boolean = false,   // Zakomentováno v logice, ale pole zachováno v DB
    var isLocked: Boolean = false,
    val caughtDate: Long = System.currentTimeMillis(),
    var moveListStr: String = "",
    var level: Int = 1,
    var xp: Int = 0
)

@Dao
interface CapturedMakromonDao {
    @Query("SELECT * FROM captured_pokemon ORDER BY caughtDate DESC")
    fun getAllCaught(): List<CapturedMakromonEntity>

    @Query("SELECT * FROM captured_pokemon WHERE id = :id LIMIT 1")
    fun getMakromonById(id: Int): CapturedMakromonEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM captured_pokemon WHERE makromonId = :makromonId LIMIT 1)")
    fun hasBeenCaught(makromonId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMakromon(makromon: CapturedMakromonEntity)

    @Update
    fun updateMakromon(makromon: CapturedMakromonEntity)

    @Delete
    fun deleteMakromon(makromon: CapturedMakromonEntity)

    @Query("DELETE FROM captured_pokemon WHERE makromonId = :id")
    fun deleteMakromonById(id: String)

    @Query("DELETE FROM captured_pokemon")
    fun deleteAllCapturedLocally()

    @Query("SELECT * FROM captured_pokemon WHERE caughtDate = :timestamp LIMIT 1")
    fun getMakromonByCaughtDate(timestamp: Long): CapturedMakromonEntity?

    @Transaction
    fun addExperience(timestamp: Long, amount: Int): Pair<Int, Int> {
        val m = getMakromonByCaughtDate(timestamp)
        if (m != null) {
            val oldLevel = m.level
            val newXp    = m.xp + amount
            val newLevel = PokemonLevelCalc.levelFromXp(newXp)
            val updated  = m.copy(xp = newXp, level = newLevel)
            updateMakromon(updated)
            return Pair(oldLevel, newLevel)
        }
        return Pair(0, 0)
    }
}

// --- 🎒 BATOH (Předměty a Bally) ---
@Entity(tableName = "user_items")
data class UserItemEntity(
    @PrimaryKey val itemId: String,
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

// --- 📖 STATICKÁ ENCYKLOPEDIE (Makrodex tabulka) ---
@Entity(tableName = "pokedex_entries")  // název tabulky zachován pro zpětnou kompatibilitu DB
data class MakrodexEntryEntity(
    @PrimaryKey val makrodexId: String,     // Např. "012"
    val drawableName: String,               // Např. "makromon_spirra" – název drawable zdroje
    val displayName: String,                // Např. "Spirra"
    val type: String,                       // Např. "NORMAL"
    val macroDesc: String,                  // Popis vázaný na fitness téma
    val unlockedHint: String,               // Nápověda jak ho najít
    val evolveLevel: Int = 0,               // 0 = nevyvíjí se
    val evolveToId: String = ""             // ID kam se vyvíjí
)

@Dao
interface MakrodexEntryDao {
    @Query("SELECT * FROM pokedex_entries ORDER BY makrodexId ASC")
    fun getAllEntries(): List<MakrodexEntryEntity>

    @Query("SELECT * FROM pokedex_entries WHERE makrodexId = :id LIMIT 1")
    fun getEntry(id: String): MakrodexEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entries: List<MakrodexEntryEntity>)

    @Query("SELECT COUNT(*) FROM pokedex_entries")
    fun getCount(): Int
}

// --- 📖 TRVALÝ ZÁZNAM MAKRODEXU (Jednou chycen, navždy objeven) ---
@Entity(tableName = "pokedex_status")   // název tabulky zachován pro zpětnou kompatibilitu DB
data class MakrodexStatusEntity(
    @PrimaryKey val makromonId: String,
    val unlocked: Boolean = true,
    val unlockedDate: Long = System.currentTimeMillis()
)

@Dao
interface MakrodexStatusDao {
    @Query("SELECT makromonId FROM pokedex_status")
    fun getUnlockedIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM pokedex_status WHERE makromonId = :makromonId)")
    fun isUnlocked(makromonId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun unlockMakromon(entity: MakrodexStatusEntity)
}