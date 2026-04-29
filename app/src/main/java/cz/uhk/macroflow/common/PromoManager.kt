package cz.uhk.macroflow.common

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.pokemon.CapturedMakromonEntity
import cz.uhk.macroflow.pokemon.MakrodexStatusEntity
import cz.uhk.macroflow.pokemon.PokemonBattleView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object PromoManager {

    // Tvůj Admin UID pro nekonečné testování
    private const val DEV_UID = "eSsB0mtAsFcxwp12C8unz1X1kqx1"

    fun redeemCode(context: Context, code: String, scope: CoroutineScope, onSuccess: () -> Unit) {
        val normalizedCode = code.trim().uppercase()
        val firestore = Firebase.firestore
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser


        if (currentUser == null) {
            Toast.makeText(context, "Pro uplatnění kódu musíš být přihlášen!", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                // 1. Sáhneme si do Firestore pro data o kódu
                val codeDoc = firestore.collection("promo_codes").document(normalizedCode).get().await()

                if (!codeDoc.exists()) {
                    showToast(context, "Kód neexistuje! 🧐")
                    return@launch
                }

                val isActive = codeDoc.getBoolean("isActive") ?: false
                if (!isActive) {
                    showToast(context, "Tento kód už vypršel! 🛑")
                    return@launch
                }

                // 2. Kontrola, zda uživatel kód už nepoužil (Admina DEV_UID ignorujeme)
                val userRef = firestore.collection("users").document(currentUser.uid)
                val userSnapshot = userRef.get().await()

                if (currentUser.uid != DEV_UID) {
                    val usedCodes = userSnapshot.get("usedPromoCodes") as? List<String> ?: emptyList()
                    if (usedCodes.contains(normalizedCode)) {
                        showToast(context, "Tento kód už jsi jednou použil! ❌")
                        return@launch
                    }
                }

                // 3. Vytáhneme data o odměně z dokumentu
                val rewardType = codeDoc.getString("rewardType") ?: ""
                val rewardValue = codeDoc.getLong("rewardValue") ?: 0L
                val itemId = codeDoc.getString("itemId") ?: "poke_ball"

                // 4. APLIKACE ODMĚNY
                val success = applyReward(context, rewardType, rewardValue, itemId)

                if (success) {
                    // 5. Zápis do Firebase, že kód byl použit (pokud to není Admin)
                    if (currentUser.uid != DEV_UID) {
                        userRef.update("usedPromoCodes", FieldValue.arrayUnion(normalizedCode)).await()
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "✅ Kód uplatněn!", Toast.LENGTH_LONG).show()
                        onSuccess()
                    }
                }

            } catch (e: Exception) {
                showToast(context, "Chyba při ověřování: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun applyReward(context: Context, type: String, value: Long, itemId: String): Boolean {
        val db = AppDatabase.getDatabase(context)
        var success = false

        when (type) {
            "shiny" -> {
                val gamePrefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                val caughtDate = gamePrefs.getLong("currentOnBarCaughtDate", -1L)
                if (caughtDate != -1L) {
                    val p = db.capturedMakromonDao().getMakromonByCaughtDate(caughtDate)
                    if (p != null) {
                        val updated = p.copy(isShiny = true)
                        db.capturedMakromonDao().updateMakromon(updated)
                        if (FirebaseRepository.isLoggedIn) FirebaseRepository.uploadCapturedMakromon(updated)

                        withContext(Dispatchers.Main) {
                            (context as? MainActivity)?.updateMakromonVisibility()
                            val serviceIntent = Intent(context, CompanionForegroundService::class.java)
                            context.startService(serviceIntent)
                        }
                        success = true
                    }
                } else {
                    showToast(context, "Musíš mít nasazeného parťáka!")
                }
            }

            "coins" -> {
                db.coinDao().addCoins(value.toInt())
                success = true
            }

            "item" -> {
                db.userItemDao().addItem(itemId, value.toInt())
                success = true
            }

            "items_bundle" -> {
                // Startovací balíček: 20x obyč, 10x skvělý
                db.userItemDao().addItem("poke_ball", 20)
                db.userItemDao().addItem("great_ball", 10)
                success = true
            }

            "xp_boost" -> {
                val gamePrefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                val caughtDate = gamePrefs.getLong("currentOnBarCaughtDate", -1L)
                if (caughtDate != -1L) {
                    val levels = db.capturedMakromonDao().addExperience(caughtDate, value.toInt())

                    withContext(Dispatchers.Main) {
                        val msg = if (levels.second > levels.first) " 🎊 LEVEL UP! Lv.${levels.second}" else ""
                        Toast.makeText(context, "⭐ Parťák získal $value XP!$msg", Toast.LENGTH_SHORT).show()
                    }
                    success = true
                } else {
                    showToast(context, "Musíš mít nasazeného parťáka!")
                }
            }

            "give_makromon" -> {
                val makromonId = String.format("%03d", value)
                val targetLevel = 5

                // 1. Musíme vytvořit dočasnou instanci View, abychom se dostali k té metodě
                // (Jelikož createPlayerPokemon není v companion objectu)
                val dummyView = PokemonBattleView(context)
                val basePoke = dummyView.createPlayerMakromon(makromonId, targetLevel)

                // 2. Získáme displayName z Pokédexu, aby se jmenoval správně (ne MYSTERY)
                val makrodexEntry = db.makrodexEntryDao().getEntry(makromonId)
                val finalName = makrodexEntry?.displayName ?: basePoke.name

                // 3. Převedeme útoky na string
                // Tohle je klíčové: basePoke už má útoky vygenerované z BattleFactory/GrowthManageru
                val movesString = basePoke.moves.joinToString(",") { it.name }

                val newCapture = CapturedMakromonEntity(
                    makromonId = makromonId,
                    name = finalName.uppercase(),
                    isShiny = false,
                    level = targetLevel,
                    xp = 0,
                    moveListStr = movesString,
                    caughtDate = System.currentTimeMillis()
                )

                // 4. Uložení do lokální DB
                db.capturedMakromonDao().insertMakromon(newCapture)

                // 5. Odemčení v Pokédexu (aby uživatel viděl kartu v seznamu)
                db.makrodexStatusDao().unlockMakromon(MakrodexStatusEntity(makromonId))

                // 6. Synchronizace
                if (FirebaseRepository.isLoggedIn) {
                    FirebaseRepository.uploadCapturedMakromon(newCapture)
                }
                success = true
            }
        }
        return success
    }

    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}