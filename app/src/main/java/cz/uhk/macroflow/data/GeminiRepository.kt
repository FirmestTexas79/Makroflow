package cz.uhk.macroflow.data

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import cz.uhk.macroflow.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject


object GeminiRepository {

    private const val TAG = "Gemini API"
    // Tvůj API klíč
    private const val API_KEY = BuildConfig.GEMINI_API_KEY

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = API_KEY,
        generationConfig = generationConfig {
            temperature = 0.1f
            responseMimeType = "application/json"
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
        )
    )

    suspend fun analyzeFood(bitmap: Bitmap): FoodAIResult? = withContext(Dispatchers.IO) {
        try {
            // Optimalizace pro Pixel 10 Pro (zmenšení pro úsporu tokenů a rychlost)
            val optimizedBitmap = scaleBitmap(bitmap, 1024)

            val prompt = """
                Analyze the food in this image. 
                Return a JSON object with this exact structure:
                {
                  "name": "Czech name of the food",
                  "weight": "estimated weight with unit (e.g. 200g)",
                  "p": protein grams per 100g (float),
                  "s": carbs grams per 100g (float),
                  "t": fat grams per 100g (float),
                  "fiber": fiber grams per 100g (float),
                  "kj": energy in kJ per 100g (float)
                }
                If it is not food, return an empty object. Respond ONLY with JSON.
            """.trimIndent()

            val inputContent = content {
                image(optimizedBitmap)
                text(prompt)
            }

            val response = try {
                generativeModel.generateContent(inputContent)
            } catch (t: Throwable) {
                val errorMsg = t.message ?: "Unknown error"
                Log.e(TAG, "Chyba Gemini API: $errorMsg")

                // Specifické varování pro kvóty z tvého logu
                if (errorMsg.contains("quota", ignoreCase = true) || errorMsg.contains("429")) {
                    Log.e(TAG, "KRITICKÉ: Kvóta vyčerpána. Zkontroluj Google AI Studio.")
                }
                return@withContext null
            }

            val jsonText = response.text ?: return@withContext null

            // Vyčištění případných markdown značek
            val cleanJson = if (jsonText.contains("```json")) {
                jsonText.substringAfter("```json").substringBefore("```").trim()
            } else if (jsonText.contains("```")) {
                jsonText.substringAfter("```").substringBeforeLast("```").trim()
            } else {
                jsonText.trim()
            }

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
            Log.e(TAG, "Chyba při zpracování: ${e.localizedMessage}")
            null
        }
    }

    private fun scaleBitmap(bm: Bitmap, maxSize: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val ratio = width.toFloat() / height.toFloat()
        val (finalWidth, finalHeight) = if (ratio > 1) {
            maxSize to (maxSize / ratio).toInt()
        } else {
            (maxSize * ratio).toInt() to maxSize
        }
        return Bitmap.createScaledBitmap(bm, finalWidth, finalHeight, true)
    }
}