package cz.uhk.macroflow.nutrition

import android.util.Log
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.GameEventType
import cz.uhk.macroflow.data.GameEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Nutriční hodnoty produktu na 100 g (tak, jak je vrací OpenFoodFacts). */
data class ScannedProduct(
    val barcode: String,
    val name: String,
    val proteins100g: Float,
    val carbs100g: Float,
    val fat100g: Float,
    val fiber100g: Float,
    val energyKj100g: Float
)

/**
 * Jediné místo, kde se čárový kód převádí na produkt.
 * Dřív byla stejná logika zkopírovaná v SnackFragment i MealBuilderSheet.
 *
 * Zároveň je to **most do Makrosvěta**: každé úspěšné dohledání zapíše
 * [GameEventType.BARCODE_SCANNED], ze kterého quest „Moderní lovec“ počítá postup.
 */
object BarcodeProductLookup {

    private const val TAG = "BarcodeLookup"
    private const val TIMEOUT_MS = 8_000
    // OpenFoodFacts vyžaduje identifikaci klienta, jinak může požadavky omezovat.
    private const val USER_AGENT = "Macroflow-Android/1.0 (https://macroflow-web-six.vercel.app)"

    suspend fun lookup(db: AppDatabase, barcode: String): ScannedProduct? {
        val product = withContext(Dispatchers.IO) {
            try {
                parse(fetch(barcode), barcode)
            } catch (e: Exception) {
                Log.w(TAG, "Dohledání $barcode selhalo", e)
                null
            }
        } ?: return null

        GameEvents.record(db, GameEventType.BARCODE_SCANNED, payload = barcode)
        return product
    }

    private fun fetch(barcode: String): String {
        val conn = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json")
            .openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Čistá funkce – testovaná bez sítě. Vrací null, pokud produkt neexistuje nebo nemá nutriční údaje. */
    fun parse(json: String, barcode: String): ScannedProduct? {
        val root = JSONObject(json)
        if (root.optInt("status") != 1) return null
        val product = root.optJSONObject("product") ?: return null
        val n = product.optJSONObject("nutriments") ?: return null

        val name = product.optString("product_name_cs")
            .ifBlank { product.optString("product_name") }
            .ifBlank { "Neznámý" }

        val kj = n.optDouble("energy-kj_100g", 0.0).takeIf { it > 0.0 }
            ?: (n.optDouble("energy-kcal_100g", 0.0) * 4.184)

        return ScannedProduct(
            barcode = barcode,
            name = name,
            proteins100g = n.optDouble("proteins_100g", 0.0).toFloat(),
            carbs100g = n.optDouble("carbohydrates_100g", 0.0).toFloat(),
            fat100g = n.optDouble("fat_100g", 0.0).toFloat(),
            fiber100g = n.optDouble("fiber_100g", 0.0).toFloat(),
            energyKj100g = kj.toFloat()
        )
    }
}
