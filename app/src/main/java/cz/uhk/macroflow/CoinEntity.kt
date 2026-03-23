package cz.uhk.macroflow

import androidx.room.*

/**
 * Tabulka pro uložení celkového počtu coinů hráče.
 * Používáme jeden řádek s id = 1 (singleton row pattern).
 */
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

    /** Přidá coiny k aktuálnímu zůstatku. */
    @Transaction
    fun addCoins(amount: Int) {
        val current = getBalance()?.balance ?: 0
        setBalance(CoinEntity(balance = current + amount))
    }

    /** Odečte coiny — vrátí false pokud není dostatek. */
    @Transaction
    fun spendCoins(amount: Int): Boolean {
        val current = getBalance()?.balance ?: 0
        if (current < amount) return false
        setBalance(CoinEntity(balance = current - amount))
        return true
    }
}