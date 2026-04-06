package cz.uhk.macroflow.data

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Repository pro komunikaci s Gemini 1.5 Flash.
 * Opraveno pro chybu 404 (Model not found).
 */
object GeminiRepository {

    private const val TAG = "GeminiAI"
    private const val API_KEY = "AIzaSyA2-GWmBoa8HHXsYz6aQRGIH-ji-QarX5w"

    // Konfigurace generování
    private val config = generationConfig {
        temperature = 0.1f
        topK = 16
        topP = 0.95f
        // Odstraňujeme responseMimeType, pokud tvoje verze SDK v1beta
        // tento parametr ještě neumí správně zpracovat a hází 404
    }

    // Bezpečnostní nastavení (aby neodmítal jídlo, které vypadá "divně")
    private val safetySettings = listOf(
        SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
        SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
        SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
    )

    private val model = GenerativeModel(
        // ZKUSÍME: "gemini-1.5-flash-latest" nebo jen "gemini-1.5-flash"
        // Některé verze SDK vyžadují "models/gemini-1.5-flash",
        // ale knihovna si to většinou přidává sama.
        modelName = "gemini-1.5-flash",
        apiKey = API_KEY,
        generationConfig = config,
        safetySettings = safetySettings
    )

    suspend fun analyzeFood(bitmap: Bitmap): FoodAIResult? = withContext(Dispatchers.IO) {
        try {
            // Zmenšení pro stabilitu
            val resizedBitmap = scaleBitmap(bitmap, 768)
            Log.d(TAG, "Odesílám analýzu... (Velikost: ${resizedBitmap.width}x${resizedBitmap.height})")

            val prompt = """
                Identify the food in the image. 
                Return ONLY valid JSON in this format:
                {"name":"Czech Name","weight":"100g","p":1.0,"s":10.0,"t":0.5,"fiber":2.0,"kj":200}
                
                No extra talk, no markdown code blocks. Just the JSON.
            """.trimIndent()

            val response = model.generateContent(content {
                image(resizedBitmap)
                text(prompt)
            })

            val responseText = response.text?.trim() ?: return@withContext null
            Log.d(TAG, "Response: $responseText")

            // Robustní čištění textu (AI občas přidá ```json i když nechceme)
            val jsonStart = responseText.indexOf("{")
            val jsonEnd = responseText.lastIndexOf("}")

            if (jsonStart == -1 || jsonEnd == -1) {
                Log.e(TAG, "Nebyl nalezen platný JSON v odpovědi")
                return@withContext null
            }

            val cleanJson = responseText.substring(jsonStart, jsonEnd + 1)
            val obj = JSONObject(cleanJson)

            return@withContext FoodAIResult(
                name = obj.optString("name", "Neznámé jídlo"),
                weight = obj.optString("weight", "100g"),
                p = obj.optDouble("p", 0.0).toFloat(),
                s = obj.optDouble("s", 0.0).toFloat(),
                t = obj.optDouble("t", 0.0).toFloat(),
                fiber = obj.optDouble("fiber", 0.0).toFloat(),
                kj = obj.optDouble("kj", 0.0).toFloat()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Chyba Gemini: ${e.message}")
            // Pokud stále dostáváš 404, vypiš přesně co přišlo v e.cause
            e.printStackTrace()
            null
        }
    }

    private fun scaleBitmap(bm: Bitmap, maxSize: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (ratio > 1) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bm, newWidth, newHeight, true)
    }
}