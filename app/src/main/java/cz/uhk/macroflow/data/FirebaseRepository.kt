package cz.uhk.macroflow.data

import android.content.Context
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
    private val db = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val isLoggedIn: Boolean get() = currentUser != null

    private fun userDoc() = db.collection("users").document(currentUser!!.uid)

    // ========== 👤 PROFIL PROFIL ==========

    suspend fun uploadProfile(profile: UserProfileEntity) {
        val data = mapOf(
            "weight" to profile.weight,
            "height" to profile.height,
            "age" to profile.age,
            "gender" to profile.gender,
            "activityMultiplier" to profile.activityMultiplier,
            "stepGoal" to profile.stepGoal
        )
        userDoc().collection("data").document("profile")
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadProfile(): UserProfileEntity? {
        val snap = userDoc().collection("data").document("profile").get().await()
        if (!snap.exists()) return null
        return UserProfileEntity(
            id = 1,
            weight = snap.getDouble("weight") ?: 83.0,
            height = snap.getDouble("height") ?: 175.0,
            age = (snap.getLong("age") ?: 22L).toInt(),
            gender = snap.getString("gender") ?: "male",
            activityMultiplier = (snap.getDouble("activityMultiplier") ?: 1.2).toFloat(),
            stepGoal = (snap.getLong("stepGoal") ?: 6000L).toInt()
        )
    }

    // ========== 📋 TRÉNINKOVÝ PLÁN ==========

    suspend fun uploadTrainingPlan(plan: Map<String, String>) {
        userDoc().collection("data").document("training_plan")
            .set(plan, SetOptions.merge()).await()
    }

    suspend fun downloadTrainingPlan(): Map<String, String> {
        val snap = userDoc().collection("data").document("training_plan").get().await()
        if (!snap.exists()) return emptyMap()
        @Suppress("UNCHECKED_CAST")
        return snap.data as? Map<String, String> ?: emptyMap()
    }

    // ========== 📈 CHECK-INY ==========

    suspend fun uploadCheckIn(checkIn: CheckInEntity) {
        val data = mapOf(
            "weight" to checkIn.weight,
            "energyLevel" to checkIn.energyLevel,
            "sleepQuality" to checkIn.sleepQuality,
            "hungerLevel" to checkIn.hungerLevel,
            "trainingReps" to checkIn.trainingReps,
            "trainingIntensity" to checkIn.trainingIntensity,
            "mood" to checkIn.mood
        )
        userDoc().collection("checkins").document(checkIn.date)
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllCheckIns(): List<CheckInEntity> {
        val snaps = userDoc().collection("checkins").get().await()
        return snaps.documents.mapNotNull { doc ->
            CheckInEntity(
                date = doc.id,
                weight = doc.getDouble("weight") ?: 83.0,
                energyLevel = (doc.getLong("energyLevel") ?: 3L).toInt(),
                sleepQuality = (doc.getLong("sleepQuality") ?: 3L).toInt(),
                hungerLevel = (doc.getLong("hungerLevel") ?: 3L).toInt(),
                trainingReps = (doc.getLong("trainingReps") ?: 0L).toInt(),
                trainingIntensity = (doc.getDouble("trainingIntensity") ?: 0.0).toFloat(),
                mood = doc.getString("mood") ?: ""
            )
        }
    }

    // ========== 📏 TĚLESNÉ MÍRY ==========

    suspend fun uploadBodyMetrics(metrics: BodyMetricsEntity) {
        val data = mapOf(
            "neck" to metrics.neck,
            "chest" to metrics.chest,
            "bicep" to metrics.bicep,
            "forearm" to metrics.forearm,
            "waist" to metrics.waist,
            "abdomen" to metrics.abdomen,
            "thigh" to metrics.thigh,
            "calf" to metrics.calf
        )
        userDoc().collection("body_metrics").document(metrics.date)
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllBodyMetrics(): List<BodyMetricsEntity> {
        val snaps = userDoc().collection("body_metrics").get().await()
        return snaps.documents.mapNotNull { doc ->
            BodyMetricsEntity(
                date = doc.id,
                neck = (doc.getDouble("neck") ?: 0.0).toFloat(),
                chest = (doc.getDouble("chest") ?: 0.0).toFloat(),
                bicep = (doc.getDouble("bicep") ?: 0.0).toFloat(),
                forearm = (doc.getDouble("forearm") ?: 0.0).toFloat(),
                waist = (doc.getDouble("waist") ?: 0.0).toFloat(),
                abdomen = (doc.getDouble("abdomen") ?: 0.0).toFloat(),
                thigh = (doc.getDouble("thigh") ?: 0.0).toFloat(),
                calf = (doc.getDouble("calf") ?: 0.0).toFloat()
            )
        }
    }

    // ========== 💧 VODA ==========

    suspend fun uploadWater(water: WaterEntity) {
        val data = mapOf(
            "date" to water.date,
            "amountMl" to water.amountMl,
            "timestamp" to water.timestamp
        )
        userDoc().collection("water").document(water.timestamp.toString())
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllWater(): List<WaterEntity> {
        val snaps = userDoc().collection("water").get().await()
        return snaps.documents.mapNotNull { doc ->
            WaterEntity(
                date = doc.getString("date") ?: "",
                amountMl = (doc.getLong("amountMl") ?: 0L).toInt(),
                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
            )
        }
    }

    // ========== 🍕 VLASTNÍ POTRAVINY ==========

    suspend fun uploadCustomSnack(snack: SnackEntity) {
        val data = mapOf(
            "name" to snack.name,
            "weight" to snack.weight,
            "p" to snack.p,
            "s" to snack.s,
            "t" to snack.t,
            "isPre" to snack.isPre
        )
        userDoc().collection("custom_snacks").document(snack.id.toString())
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllCustomSnacks(): List<SnackEntity> {
        val snap = userDoc().collection("custom_snacks").get().await()
        return snap.documents.mapNotNull { doc ->
            SnackEntity(
                id = doc.id.toIntOrNull() ?: 0,
                name = doc.getString("name") ?: "",
                weight = doc.getString("weight") ?: "",
                p = (doc.getDouble("p") ?: 0.0).toFloat(),
                s = (doc.getDouble("s") ?: 0.0).toFloat(),
                t = (doc.getDouble("t") ?: 0.0).toFloat(),
                isPre = doc.getBoolean("isPre") ?: false
            )
        }
    }

    // ========== 🍕 SNĚDENÉ POTRAVINY ==========

    suspend fun uploadConsumedSnack(consumed: ConsumedSnackEntity) {
        val data = mapOf(
            "date" to consumed.date,
            "time" to consumed.time,
            "name" to consumed.name,
            "p" to consumed.p,
            "s" to consumed.s,
            "t" to consumed.t,
            "calories" to consumed.calories,
            "mealContext" to consumed.mealContext,
            "timestamp" to consumed.timestamp
        )
        userDoc().collection("consumed_history").document(consumed.timestamp.toString())
            .set(data, SetOptions.merge()).await()
    }

    // 📂 ✅ NOVÉ: Stáhne kompletně celou historii snězených jídel pro trenéra (všechny dny)
    suspend fun downloadAllConsumedHistory(): List<ConsumedSnackEntity> {
        val snaps = userDoc().collection("consumed_history").get().await()

        return snaps.documents.mapNotNull { doc ->
            ConsumedSnackEntity(
                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                date = doc.getString("date") ?: "",
                time = doc.getString("time") ?: "",
                name = doc.getString("name") ?: "",
                p = (doc.getDouble("p") ?: 0.0).toFloat(),
                s = (doc.getDouble("s") ?: 0.0).toFloat(),
                t = (doc.getDouble("t") ?: 0.0).toFloat(),
                calories = (doc.getLong("calories") ?: 0L).toInt(),
                mealContext = doc.getString("mealContext") ?: "NO_TRAINING"
            )
        }
    }

    suspend fun deleteConsumedSnack(timestamp: Long) {
        userDoc().collection("consumed_history").document(timestamp.toString()).delete().await()
    }

    // ========== 🦖 INVENTÁŘ (Zlatý rámeček v kapse) ==========

    suspend fun uploadCapturedPokemon(pokemon: CapturedPokemonEntity) {
        val data = mapOf(
            "pokemonId" to pokemon.pokemonId,
            "name" to pokemon.name,
            "isShiny" to pokemon.isShiny,
            "isLocked" to pokemon.isLocked,
            "caughtDate" to pokemon.caughtDate,
            "moveListStr" to pokemon.moveListStr,
            "level" to pokemon.level,
            "xp" to pokemon.xp
        )

        userDoc().collection("captured_pokemon").document(pokemon.caughtDate.toString())
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllCapturedPokemon(): List<CapturedPokemonEntity> {
        val snaps = userDoc().collection("captured_pokemon").get().await()
        return snaps.documents.mapNotNull { doc ->
            CapturedPokemonEntity(
                pokemonId = doc.getString("pokemonId") ?: "",
                name = doc.getString("name") ?: "",
                isShiny = doc.getBoolean("isShiny") ?: false,
                isLocked = doc.getBoolean("isLocked") ?: false,
                caughtDate = doc.getLong("caughtDate") ?: System.currentTimeMillis(),
                moveListStr = doc.getString("moveListStr") ?: "",
                level = (doc.getLong("level") ?: 1L).toInt(),
                xp = (doc.getLong("xp") ?: 0L).toInt()
            )
        }
    }

    suspend fun deleteCapturedPokemon(caughtDate: Long) {
        userDoc().collection("captured_pokemon").document(caughtDate.toString()).delete().await()
    }

    // ========== 📖 POKÉDEX STATUS (Trvale barevný = aspoň jednou chycený) ==========

    suspend fun uploadPokedexStatus(pokemonId: String) {
        val data = mapOf(
            "unlocked" to true,
            "unlockedDate" to System.currentTimeMillis()
        )

        userDoc().collection("pokedex_status").document(pokemonId)
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllPokedexStatus(): List<PokedexStatusEntity> {
        val snaps = userDoc().collection("pokedex_status").get().await()
        return snaps.documents.mapNotNull { doc ->
            PokedexStatusEntity(
                pokemonId = doc.id,
                unlocked = true,
                unlockedDate = doc.getLong("unlockedDate") ?: System.currentTimeMillis()
            )
        }
    }

    // ========== 🎒 BATOH (Předměty a Bally) ==========

    suspend fun uploadUserItem(item: UserItemEntity) {
        val data = mapOf("quantity" to item.quantity)
        userDoc().collection("user_items").document(item.itemId)
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllUserItems(): List<UserItemEntity> {
        val snaps = userDoc().collection("user_items").get().await()
        return snaps.documents.mapNotNull { doc ->
            UserItemEntity(
                itemId = doc.id,
                quantity = (doc.getLong("quantity") ?: 0L).toInt()
            )
        }
    }

    // ========== 🧪 POKEMON XP ==========

    suspend fun uploadPokemonXp(xp: PokemonXpEntity) {
        val data = mapOf(
            "totalXp" to xp.totalXp,
            "lastDailyRewardDate" to xp.lastDailyRewardDate
        )
        userDoc().collection("pokemon_xp").document(xp.pokemonId)
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllPokemonXp(): List<PokemonXpEntity> {
        val snaps = userDoc().collection("pokemon_xp").get().await()
        return snaps.documents.mapNotNull { doc ->
            PokemonXpEntity(
                pokemonId = doc.id,
                totalXp = (doc.getLong("totalXp") ?: 0L).toInt(),
                lastDailyRewardDate = doc.getString("lastDailyRewardDate") ?: ""
            )
        }
    }

    // ========== 💰 COINY ==========

    suspend fun uploadCoins(coins: CoinEntity) {
        val data = mapOf("balance" to coins.balance)
        userDoc().collection("wallet").document("coins")
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadCoins(): CoinEntity {
        val snap = userDoc().collection("wallet").document("coins").get().await()
        if (!snap.exists()) return CoinEntity(balance = 100)
        return CoinEntity(
            id = 1,
            balance = (snap.getLong("balance") ?: 100L).toInt()
        )
    }

    // ========== 🏆 ACHIEVEMENTY ==========

    suspend fun uploadAchievement(achievement: AchievementEntity) {
        val data = mapOf("unlockedAt" to achievement.unlockedAt)
        userDoc().collection("unlocked_achievements").document(achievement.id)
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllAchievements(): List<AchievementEntity> {
        val snaps = userDoc().collection("unlocked_achievements").get().await()
        return snaps.documents.mapNotNull { doc ->
            AchievementEntity(
                id = doc.id,
                unlockedAt = doc.getLong("unlockedAt") ?: System.currentTimeMillis()
            )
        }
    }

    // ========== 👣 KROKY ==========

    suspend fun uploadSteps(steps: StepsEntity) {
        val data = mapOf(
            "count" to steps.count
        )
        userDoc().collection("steps").document(steps.date)
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllSteps(): List<StepsEntity> {
        val snaps = userDoc().collection("steps").get().await()
        return snaps.documents.mapNotNull { doc ->
            StepsEntity(
                date = doc.id,
                count = (doc.getLong("count") ?: 0L).toInt()
            )
        }
    }

    // ========== 🔄 KOMPLETNÍ SYNC ==========

    suspend fun syncLocalDataToCloud(context: Context) {
        val localDb = AppDatabase.getDatabase(context)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        localDb.userProfileDao().getProfileSync()?.let { uploadProfile(it) }

        val prefs = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val plan = days.associateWith { prefs.getString("type_$it", "rest") ?: "rest" }
        uploadTrainingPlan(plan)

        localDb.checkInDao().getAllCheckInsSync().forEach { uploadCheckIn(it) }
        localDb.bodyMetricsDao().getAllSync().forEach { uploadBodyMetrics(it) }

        localDb.snackDao().getAllSnacks().first().forEach { uploadCustomSnack(it) }

        // 🍕 ✅ Celá historie se odešle na server pro trenérské PDF přehledy
        localDb.consumedSnackDao().getAllConsumedSync().forEach { uploadConsumedSnack(it) }

        localDb.waterDao().getAllWaterSync().forEach { uploadWater(it) }

        localDb.coinDao().getBalance()?.let { uploadCoins(it) }
        localDb.userItemDao().getAllItems().forEach { uploadUserItem(it) }

        localDb.stepsDao().getStepsForDateSync(today)?.let { uploadSteps(it) }

        val localCaught = localDb.capturedPokemonDao().getAllCaught()
        localCaught.forEach { uploadCapturedPokemon(it) }

        val localStatusIds = localDb.pokedexStatusDao().getUnlockedIds()
        val localCaughtIds = localCaught.map { it.pokemonId }
        val allUnlockedIds = (localStatusIds + localCaughtIds).distinct()

        allUnlockedIds.forEach { id ->
            uploadPokedexStatus(pokemonId = id)
        }

        localDb.achievementDao().getAllUnlocked().forEach { uploadAchievement(it) }
        localCaught.forEach { pokemon ->
            val xpEntity = localDb.pokemonXpDao().getXp(pokemon.pokemonId)
            if (xpEntity != null) uploadPokemonXp(xpEntity)
        }
    }

    // ========== 🔄 KOMPLETNÍ DOWNLOAD ==========

    suspend fun syncCloudDataToLocal(context: Context) {
        val localDb = AppDatabase.getDatabase(context)

        downloadProfile()?.let { localDb.userProfileDao().saveProfile(it) }

        val plan = downloadTrainingPlan()
        if (plan.isNotEmpty()) {
            val prefs = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            plan.forEach { (day, type) -> editor.putString("type_$day", type) }
            editor.apply()
        }

        downloadAllCheckIns().forEach { localDb.checkInDao().insertCheckIn(it) }
        downloadAllBodyMetrics().forEach { localDb.bodyMetricsDao().save(it) }
        downloadAllCustomSnacks().forEach { localDb.snackDao().insertSnack(it) }

        // 🍕 🔥 OPRAVA ZDE: Smaže duplikáty a čistě stáhne kompletní historii snězených jídel z Cloudu
        localDb.consumedSnackDao().deleteAllConsumedLocally()
        downloadAllConsumedHistory().forEach { localDb.consumedSnackDao().insertConsumed(it) }

        downloadAllWater().forEach { localDb.waterDao().insertWater(it) }

        localDb.coinDao().setBalance(downloadCoins())
        downloadAllUserItems().forEach { localDb.userItemDao().insertOrUpdateItem(it) }

        localDb.capturedPokemonDao().deleteAllCapturedLocally()
        downloadAllCapturedPokemon().forEach { localDb.capturedPokemonDao().insertPokemon(it) }

        downloadAllAchievements().forEach { localDb.achievementDao().unlock(it) }
        downloadAllPokedexStatus().forEach { localDb.pokedexStatusDao().unlockPokemon(it) }
        downloadAllPokemonXp().forEach { localDb.pokemonXpDao().setXp(it) }
        downloadAllSteps().forEach { localDb.stepsDao().insertSteps(it) }
    }

    fun signOut() = auth.signOut()
}