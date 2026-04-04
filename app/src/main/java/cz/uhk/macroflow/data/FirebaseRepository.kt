package cz.uhk.macroflow.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import cz.uhk.macroflow.achievements.AchievementEntity
import cz.uhk.macroflow.pokemon.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val isLoggedIn: Boolean        get() = auth.currentUser != null

    /**
     * Vrátí cestu k dokumentu přihlášeného uživatele.
     * NIKDY nepoužívá "anonymous" fallback — pokud není UID, hodí výjimku.
     * Všechny suspend funkce volají isLoggedIn check PŘED voláním userDoc().
     */
    private fun userDoc(): com.google.firebase.firestore.DocumentReference {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Firebase: uživatel není přihlášen")
        return db.collection("users").document(uid)
    }

    // ========== PROFIL ==========

    suspend fun uploadProfile(profile: UserProfileEntity) {
        if (!isLoggedIn) { Log.w("FB", "uploadProfile: přeskočeno, nepřihlášen"); return }
        val data = mapOf(
            "weight"               to profile.weight,
            "height"               to profile.height,
            "age"                  to profile.age,
            "gender"               to profile.gender,
            "goal"                 to profile.goal,
            "activityMultiplier"   to profile.activityMultiplier,
            "stepGoal"             to profile.stepGoal,
            "isEliteMode"          to profile.isEliteMode,
            "bodyFatPercentage"    to profile.bodyFatPercentage,
            "dietType"             to profile.dietType,
            "lastWristMeasurement" to profile.lastWristMeasurement
        )
        userDoc().collection("data").document("profile").set(data, SetOptions.merge()).await()
    }

    suspend fun downloadProfile(): UserProfileEntity? {
        if (!isLoggedIn) return null
        val snap = userDoc().collection("data").document("profile").get().await()
        if (!snap.exists()) return null
        return UserProfileEntity(
            id                   = 1,
            weight               = snap.getDouble("weight") ?: 83.0,
            height               = snap.getDouble("height") ?: 175.0,
            age                  = (snap.getLong("age") ?: 22L).toInt(),
            gender               = snap.getString("gender") ?: "male",
            goal                 = snap.getString("goal") ?: "MAINTAIN",
            activityMultiplier   = (snap.getDouble("activityMultiplier") ?: 1.2).toFloat(),
            stepGoal             = (snap.getLong("stepGoal") ?: 6000L).toInt(),
            isEliteMode          = snap.getBoolean("isEliteMode") ?: false,
            bodyFatPercentage    = snap.getDouble("bodyFatPercentage") ?: 15.0,
            dietType             = snap.getString("dietType") ?: "BALANCED",
            lastWristMeasurement = snap.getDouble("lastWristMeasurement") ?: 17.5
        )
    }

    // ========== TRÉNINKOVÝ PLÁN ==========

    suspend fun uploadTrainingPlan(plan: Map<String, String>) {
        if (!isLoggedIn) return
        userDoc().collection("data").document("training_plan").set(plan).await()
    }

    suspend fun downloadTrainingPlan(): Map<String, String> {
        if (!isLoggedIn) return emptyMap()
        val snap = userDoc().collection("data").document("training_plan").get().await()
        if (!snap.exists()) return emptyMap()
        @Suppress("UNCHECKED_CAST")
        return snap.data as? Map<String, String> ?: emptyMap()
    }

    // ========== CHECK-INY ==========

    suspend fun uploadCheckIn(checkIn: CheckInEntity) {
        if (!isLoggedIn) { Log.w("FB", "uploadCheckIn: přeskočeno, nepřihlášen"); return }
        val data = mapOf(
            "weight"            to checkIn.weight,
            "energyLevel"       to checkIn.energyLevel,
            "sleepQuality"      to checkIn.sleepQuality,
            "hungerLevel"       to checkIn.hungerLevel,
            "trainingReps"      to checkIn.trainingReps,
            "trainingIntensity" to checkIn.trainingIntensity,
            "mood"              to checkIn.mood
        )
        userDoc().collection("checkins").document(checkIn.date).set(data, SetOptions.merge()).await()
        Log.d("FB", "uploadCheckIn OK: ${checkIn.date}, uid=${auth.currentUser?.uid}")
    }

    suspend fun downloadAllCheckIns(): List<CheckInEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("checkins").get().await()
        return snaps.documents.mapNotNull { doc ->
            CheckInEntity(
                date               = doc.id,
                weight             = doc.getDouble("weight") ?: 83.0,
                energyLevel        = (doc.getLong("energyLevel") ?: 3L).toInt(),
                sleepQuality       = (doc.getLong("sleepQuality") ?: 3L).toInt(),
                hungerLevel        = (doc.getLong("hungerLevel") ?: 3L).toInt(),
                trainingReps       = (doc.getLong("trainingReps") ?: 0L).toInt(),
                trainingIntensity  = (doc.getDouble("trainingIntensity") ?: 0.0).toFloat(),
                mood               = doc.getString("mood") ?: ""
            )
        }
    }

    // ========== ANALYTIKA ==========
    // Kolekce: users/{uid}/analytics/{yyyy-MM-dd}
    // Voláno z CheckInFragment po každém uloženém rituálu.

    suspend fun uploadAnalytics(analytics: AnalyticsCacheEntity) {
        if (!isLoggedIn) return

        // Použijeme withTimeout, aby nás GMS nezablokovalo na věky
        kotlin.runCatching {
            kotlinx.coroutines.withTimeout(5000) { // Max 5 sekund čekání
                val data = mapOf(
                    "smoothedWeight"      to analytics.smoothedWeight,
                    "trendSlope"          to analytics.trendSlope,
                    "standardDeviation"   to analytics.standardDeviation,
                    "confidenceScore"     to analytics.confidenceScore.toDouble(),
                    "metabolicEfficiency" to analytics.metabolicEfficiency
                )
                userDoc().collection("analytics").document(analytics.date)
                    .set(data, com.google.firebase.firestore.SetOptions.merge()).await()
                Log.d("FB", "uploadAnalytics OK")
            }
        }.onFailure { e ->
            // I když to selže (třeba na tu SecurityException), aplikace pojede dál
            Log.e("FB", "uploadAnalytics SELHAL nebo VYPRŠEL: ${e.message}")
        }
    }

    suspend fun downloadAllAnalytics(): List<AnalyticsCacheEntity> {
        if (!isLoggedIn) return emptyList()
        return try {
            val snaps = userDoc().collection("analytics").get().await()
            snaps.documents.mapNotNull { doc ->
                AnalyticsCacheEntity(
                    date                 = doc.id,
                    smoothedWeight       = doc.getDouble("smoothedWeight") ?: 0.0,
                    trendSlope           = doc.getDouble("trendSlope") ?: 0.0,
                    standardDeviation    = doc.getDouble("standardDeviation") ?: 0.0,
                    confidenceScore      = (doc.getDouble("confidenceScore") ?: 0.0).toFloat(),
                    metabolicEfficiency  = doc.getDouble("metabolicEfficiency") ?: 0.0
                )
            }
        } catch (e: Exception) {
            Log.e("FB", "downloadAllAnalytics selhal: ${e.message}")
            emptyList()
        }
    }

    // ========== VODA ==========

    suspend fun uploadWater(water: WaterEntity) {
        if (!isLoggedIn) return
        val data = mapOf("date" to water.date, "amountMl" to water.amountMl, "timestamp" to water.timestamp)
        userDoc().collection("water").document(water.timestamp.toString()).set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllWater(): List<WaterEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("water").get().await()
        return snaps.documents.mapNotNull { doc ->
            WaterEntity(
                date      = doc.getString("date") ?: "",
                amountMl  = (doc.getLong("amountMl") ?: 0L).toInt(),
                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
            )
        }
    }

    // ========== VLASTNÍ POTRAVINY ==========

    suspend fun uploadCustomSnack(snack: SnackEntity) {
        if (!isLoggedIn) return
        val data = mapOf("name" to snack.name, "weight" to snack.weight,
            "p" to snack.p, "s" to snack.s, "t" to snack.t, "isPre" to snack.isPre)
        userDoc().collection("custom_snacks").document(snack.id.toString()).set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllCustomSnacks(): List<SnackEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("custom_snacks").get().await()
        return snaps.documents.mapNotNull { doc ->
            SnackEntity(
                id = doc.id.toIntOrNull() ?: 0, name = doc.getString("name") ?: "",
                weight = doc.getString("weight") ?: "",
                p = (doc.getDouble("p") ?: 0.0).toFloat(), s = (doc.getDouble("s") ?: 0.0).toFloat(),
                t = (doc.getDouble("t") ?: 0.0).toFloat(), isPre = doc.getBoolean("isPre") ?: false
            )
        }
    }

    // ========== KONZUMACE ==========

    suspend fun uploadConsumedSnack(consumed: ConsumedSnackEntity) {
        if (!isLoggedIn) return
        val data = mapOf(
            "date" to consumed.date, "time" to consumed.time, "name" to consumed.name,
            "p" to consumed.p, "s" to consumed.s, "t" to consumed.t,
            "calories" to consumed.calories, "mealContext" to consumed.mealContext,
            "timestamp" to consumed.timestamp
        )
        userDoc().collection("consumed_history").document(consumed.timestamp.toString())
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllConsumedHistory(): List<ConsumedSnackEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("consumed_history").get().await()
        return snaps.documents.mapNotNull { doc ->
            ConsumedSnackEntity(
                timestamp   = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                date        = doc.getString("date") ?: "", time = doc.getString("time") ?: "",
                name        = doc.getString("name") ?: "",
                p = (doc.getDouble("p") ?: 0.0).toFloat(), s = (doc.getDouble("s") ?: 0.0).toFloat(),
                t = (doc.getDouble("t") ?: 0.0).toFloat(),
                calories    = (doc.getLong("calories") ?: 0L).toInt(),
                mealContext = doc.getString("mealContext") ?: "NO_TRAINING"
            )
        }
    }

    suspend fun deleteConsumedSnack(timestamp: Long) {
        if (!isLoggedIn) return
        userDoc().collection("consumed_history").document(timestamp.toString()).delete().await()
    }

    // ========== POKÉMONI ==========

    suspend fun uploadCapturedPokemon(pokemon: CapturedPokemonEntity) {
        if (!isLoggedIn) return
        val data = mapOf(
            "pokemonId" to pokemon.pokemonId, "name" to pokemon.name,
            "isShiny" to pokemon.isShiny, "isLocked" to pokemon.isLocked,
            "caughtDate" to pokemon.caughtDate, "moveListStr" to pokemon.moveListStr,
            "level" to pokemon.level, "xp" to pokemon.xp
        )
        userDoc().collection("captured_pokemon").document(pokemon.caughtDate.toString())
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllCapturedPokemon(): List<CapturedPokemonEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("captured_pokemon").get().await()
        return snaps.documents.mapNotNull { doc ->
            CapturedPokemonEntity(
                pokemonId   = doc.getString("pokemonId") ?: "",
                name        = doc.getString("name") ?: "",
                isShiny     = doc.getBoolean("isShiny") ?: false,
                isLocked    = doc.getBoolean("isLocked") ?: false,
                caughtDate  = doc.getLong("caughtDate") ?: System.currentTimeMillis(),
                moveListStr = doc.getString("moveListStr") ?: "",
                level       = (doc.getLong("level") ?: 1L).toInt(),
                xp          = (doc.getLong("xp") ?: 0L).toInt()
            )
        }
    }

    suspend fun deleteCapturedPokemon(caughtDate: Long) {
        if (!isLoggedIn) return
        userDoc().collection("captured_pokemon").document(caughtDate.toString()).delete().await()
    }

    // ========== POKÉDEX, ITEMS, XP ==========

    suspend fun uploadPokedexStatus(pokemonId: String) {
        if (!isLoggedIn) return
        val data = mapOf("unlocked" to true, "unlockedDate" to System.currentTimeMillis())
        userDoc().collection("pokedex_status").document(pokemonId).set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllPokedexStatus(): List<PokedexStatusEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("pokedex_status").get().await()
        return snaps.documents.mapNotNull { doc ->
            PokedexStatusEntity(pokemonId = doc.id, unlocked = true,
                unlockedDate = doc.getLong("unlockedDate") ?: System.currentTimeMillis())
        }
    }

    suspend fun uploadUserItem(item: UserItemEntity) {
        if (!isLoggedIn) return
        userDoc().collection("user_items").document(item.itemId)
            .set(mapOf("quantity" to item.quantity), SetOptions.merge()).await()
    }

    suspend fun downloadAllUserItems(): List<UserItemEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("user_items").get().await()
        return snaps.documents.mapNotNull { doc ->
            UserItemEntity(itemId = doc.id, quantity = (doc.getLong("quantity") ?: 0L).toInt())
        }
    }

    suspend fun uploadPokemonXp(xp: PokemonXpEntity) {
        if (!isLoggedIn) return
        val data = mapOf("totalXp" to xp.totalXp, "lastDailyRewardDate" to xp.lastDailyRewardDate)
        userDoc().collection("pokemon_xp").document(xp.pokemonId).set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllPokemonXp(): List<PokemonXpEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("pokemon_xp").get().await()
        return snaps.documents.mapNotNull { doc ->
            PokemonXpEntity(pokemonId = doc.id,
                totalXp = (doc.getLong("totalXp") ?: 0L).toInt(),
                lastDailyRewardDate = doc.getString("lastDailyRewardDate") ?: "")
        }
    }

    // ========== COINY & KROKY ==========

    suspend fun uploadCoins(coins: CoinEntity) {
        if (!isLoggedIn) return
        userDoc().collection("wallet").document("coins")
            .set(mapOf("balance" to coins.balance), SetOptions.merge()).await()
    }

    suspend fun downloadCoins(): CoinEntity {
        if (!isLoggedIn) return CoinEntity(balance = 100)
        val snap = userDoc().collection("wallet").document("coins").get().await()
        return CoinEntity(id = 1, balance = (snap.getLong("balance") ?: 100L).toInt())
    }

    suspend fun uploadSteps(steps: StepsEntity) {
        if (!isLoggedIn) return
        userDoc().collection("steps").document(steps.date)
            .set(mapOf("count" to steps.count), SetOptions.merge()).await()
    }

    suspend fun downloadAllSteps(): List<StepsEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("steps").get().await()
        return snaps.documents.mapNotNull { doc ->
            StepsEntity(date = doc.id, count = (doc.getLong("count") ?: 0L).toInt())
        }
    }

    // ========== ACHIEVEMENTY ==========

    suspend fun uploadAchievement(achievement: AchievementEntity) {
        if (!isLoggedIn) return
        userDoc().collection("unlocked_achievements").document(achievement.id)
            .set(mapOf("unlockedAt" to achievement.unlockedAt), SetOptions.merge()).await()
    }

    suspend fun downloadAllAchievements(): List<AchievementEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("unlocked_achievements").get().await()
        return snaps.documents.mapNotNull { doc ->
            AchievementEntity(id = doc.id, unlockedAt = doc.getLong("unlockedAt") ?: System.currentTimeMillis())
        }
    }

    // ========== TĚLESNÉ MÍRY ==========

    suspend fun uploadBodyMetrics(metrics: BodyMetricsEntity) {
        if (!isLoggedIn) return
        val data = mapOf(
            "neck" to metrics.neck, "chest" to metrics.chest, "bicep" to metrics.bicep,
            "forearm" to metrics.forearm, "waist" to metrics.waist, "abdomen" to metrics.abdomen,
            "thigh" to metrics.thigh, "calf" to metrics.calf
        )
        userDoc().collection("body_metrics").document(metrics.date).set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllBodyMetrics(): List<BodyMetricsEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("body_metrics").get().await()
        return snaps.documents.mapNotNull { doc ->
            BodyMetricsEntity(
                date    = doc.id,
                neck    = (doc.getDouble("neck")    ?: 0.0).toFloat(),
                chest   = (doc.getDouble("chest")   ?: 0.0).toFloat(),
                bicep   = (doc.getDouble("bicep")   ?: 0.0).toFloat(),
                forearm = (doc.getDouble("forearm") ?: 0.0).toFloat(),
                waist   = (doc.getDouble("waist")   ?: 0.0).toFloat(),
                abdomen = (doc.getDouble("abdomen") ?: 0.0).toFloat(),
                thigh   = (doc.getDouble("thigh")   ?: 0.0).toFloat(),
                calf    = (doc.getDouble("calf")    ?: 0.0).toFloat()
            )
        }
    }

    // ========== SYNC: LOCAL → CLOUD ==========

    suspend fun syncLocalDataToCloud(context: Context) {
        if (!isLoggedIn) { Log.w("FB_SYNC", "syncLocalDataToCloud: přeskočeno, nepřihlášen"); return }
        Log.d("FB_SYNC", "Začínám upload, uid=${auth.currentUser?.uid}")
        val localDb = AppDatabase.getDatabase(context)
        val today   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        localDb.userProfileDao().getProfileSync()?.let { uploadProfile(it) }

        val prefs = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
        val days  = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
        val planMap = mutableMapOf<String, String>()
        days.forEach { day ->
            planMap["type_$day"]         = prefs.getString("type_$day", "rest") ?: "rest"
            prefs.getString("time_$day", null)?.let          { planMap["time_$day"] = it }
            planMap["kardio_type_$day"]  = prefs.getString("kardio_type_$day", "rest") ?: "rest"
            prefs.getString("time_kardio_$day", null)?.let   { planMap["time_kardio_$day"] = it }
            prefs.getString("kardio_duration_$day", null)?.let { planMap["kardio_duration_$day"] = it }
            prefs.getString("kardio_speed_$day", null)?.let    { planMap["kardio_speed_$day"] = it }
        }
        uploadTrainingPlan(planMap)

        localDb.checkInDao().getAllCheckInsSync().forEach      { uploadCheckIn(it) }
        localDb.bodyMetricsDao().getAllSync().forEach          { uploadBodyMetrics(it) }
        localDb.snackDao().getAllSnacks().first().forEach      { uploadCustomSnack(it) }
        localDb.consumedSnackDao().getAllConsumedSync().forEach { uploadConsumedSnack(it) }
        localDb.waterDao().getAllWaterSync().forEach           { uploadWater(it) }
        localDb.coinDao().getBalance()?.let                   { uploadCoins(it) }
        localDb.userItemDao().getAllItems().forEach            { uploadUserItem(it) }
        localDb.stepsDao().getStepsForDateSync(today)?.let    { uploadSteps(it) }
        localDb.analyticsDao().getAllAnalyticsSync().forEach  { uploadAnalytics(it) }

        localDb.capturedPokemonDao().getAllCaught().forEach { pokemon ->
            uploadCapturedPokemon(pokemon)
            uploadPokedexStatus(pokemon.pokemonId)
            localDb.pokemonXpDao().getXp(pokemon.pokemonId)?.let { uploadPokemonXp(it) }
        }
        localDb.pokedexStatusDao().getUnlockedIds().forEach { uploadPokedexStatus(it) }
        localDb.achievementDao().getAllUnlocked().forEach   { uploadAchievement(it) }

        Log.d("FB_SYNC", "Upload dokončen")
    }

    // ========== SYNC: CLOUD → LOCAL ==========

    suspend fun syncCloudDataToLocal(context: Context) {
        if (!isLoggedIn) { Log.w("FB_SYNC", "syncCloudDataToLocal: přeskočeno, nepřihlášen"); return }
        Log.d("FB_SYNC", "Začínám download, uid=${auth.currentUser?.uid}")
        val localDb = AppDatabase.getDatabase(context)

        try {
            val profile      = downloadProfile()
            val plan         = downloadTrainingPlan()
            val checkIns     = downloadAllCheckIns()
            val metrics      = downloadAllBodyMetrics()
            val snacks       = downloadAllCustomSnacks()
            val history      = downloadAllConsumedHistory()
            val water        = downloadAllWater()
            val coins        = downloadCoins()
            val items        = downloadAllUserItems()
            val pokemon      = downloadAllCapturedPokemon()
            val achievements = downloadAllAchievements()
            val pokedex      = downloadAllPokedexStatus()
            val pokemonXp    = downloadAllPokemonXp()
            val steps        = downloadAllSteps()
            val analytics    = downloadAllAnalytics()

            profile?.let { localDb.userProfileDao().saveProfile(it) }
            if (plan.isNotEmpty()) {
                context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE).edit().apply {
                    plan.forEach { (key, value) ->
                        // Nový formát: type_*, time_*, kardio_*
                        // Starý formát (zpětná kompatibilita): plain day name
                        if (key.startsWith("type_") || key.startsWith("time_") || key.startsWith("kardio_")) {
                            putString(key, value)
                        } else {
                            putString("type_$key", value)
                        }
                    }
                    apply()
                }
            }

            localDb.checkInDao().deleteAllCheckInsLocally()
            checkIns.forEach { localDb.checkInDao().insertCheckIn(it) }

            localDb.bodyMetricsDao().deleteAllLocally()
            metrics.forEach { localDb.bodyMetricsDao().save(it) }

            snacks.forEach { localDb.snackDao().insertSnack(it) }

            localDb.consumedSnackDao().deleteAllConsumedLocally()
            history.forEach { localDb.consumedSnackDao().insertConsumed(it) }

            localDb.waterDao().deleteAllWaterLocally()
            water.forEach { localDb.waterDao().insertWater(it) }

            localDb.coinDao().setBalance(coins)
            items.forEach { localDb.userItemDao().insertOrUpdateItem(it) }

            localDb.capturedPokemonDao().deleteAllCapturedLocally()
            pokemon.forEach { localDb.capturedPokemonDao().insertPokemon(it) }

            achievements.forEach { localDb.achievementDao().unlock(it) }
            pokedex.forEach      { localDb.pokedexStatusDao().unlockPokemon(it) }
            pokemonXp.forEach    { localDb.pokemonXpDao().setXp(it) }
            steps.forEach        { localDb.stepsDao().insertSteps(it) }

            if (analytics.isNotEmpty()) {
                localDb.analyticsDao().deleteAllLocally()
                localDb.analyticsDao().insertAll(analytics)
            }

            Log.d("FB_SYNC", "Download dokončen: ${checkIns.size} checkinů, ${analytics.size} analytics")

        } catch (e: Exception) {
            Log.e("FB_SYNC", "Chyba při downloadu z cloudu: ${e.message}", e)
        }
        // Zde záměrně NEVOLÁME žádné upload funkce
    }

    fun signOut() = auth.signOut()
}