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
        val data = mapOf(
            "name" to snack.name,
            "weight" to snack.weight,
            "p" to snack.p,
            "s" to snack.s,
            "t" to snack.t,
            "isPre" to snack.isPre,
            "energyKj" to snack.energyKj,    // PŘIDÁNO
            "fiber" to snack.fiber      // PŘIDÁNO
        )
        userDoc().collection("custom_snacks").document(snack.id.toString()).set(data, SetOptions.merge()).await()
    }

    suspend fun deleteCustomSnack(id: Int) {
        if (!isLoggedIn) return
        userDoc().collection("custom_snacks").document(id.toString()).delete().await()
    }

    suspend fun downloadAllCustomSnacks(): List<SnackEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("custom_snacks").get().await()
        return snaps.documents.mapNotNull { doc ->
            SnackEntity(
                id = doc.id.toIntOrNull() ?: 0,
                name = doc.getString("name") ?: "",
                weight = doc.getString("weight") ?: "",
                p = (doc.getDouble("p") ?: 0.0).toFloat(),
                s = (doc.getDouble("s") ?: 0.0).toFloat(),
                t = (doc.getDouble("t") ?: 0.0).toFloat(),
                isPre = doc.getBoolean("isPre") ?: false,
                energyKj = (doc.getDouble("energyKj") ?: 0.0).toFloat(),    // PŘIDÁNO
                fiber = (doc.getDouble("fiber") ?: 0.0).toFloat()        // PŘIDÁNO
            )
        }
    }

    // ========== TRÉNINKOVÝ DENÍK (docs/adr/0023) ==========
    // Dokument = createdAt (ms) – stejně jako u konzumace je jedinečný a stejný na všech zařízeních.

    suspend fun uploadWorkoutSet(set: WorkoutSetEntity) {
        if (!isLoggedIn) return
        val data = mapOf(
            "date" to set.date, "createdAt" to set.createdAt, "exerciseId" to set.exerciseId,
            "weightKg" to set.weightKg, "reps" to set.reps,
            "slowEccentric" to set.slowEccentric, "template" to set.template, "rir" to set.rir
        )
        userDoc().collection("workout_sets").document(set.createdAt.toString()).set(data, SetOptions.merge()).await()
    }

    suspend fun deleteWorkoutSet(createdAt: Long) {
        if (!isLoggedIn) return
        userDoc().collection("workout_sets").document(createdAt.toString()).delete().await()
    }

    suspend fun downloadAllWorkoutSets(): List<WorkoutSetEntity> {
        if (!isLoggedIn) return emptyList()
        return userDoc().collection("workout_sets").get().await().documents.mapNotNull { doc ->
            WorkoutSetEntity(
                date = doc.getString("date") ?: return@mapNotNull null,
                createdAt = doc.getLong("createdAt") ?: return@mapNotNull null,
                exerciseId = doc.getString("exerciseId") ?: return@mapNotNull null,
                weightKg = doc.getDouble("weightKg") ?: 0.0,
                reps = (doc.getLong("reps") ?: 0L).toInt(),
                slowEccentric = doc.getBoolean("slowEccentric") ?: false,
                template = doc.getString("template"),
                rir = (doc.getLong("rir") ?: 2L).toInt()
            )
        }
    }

    /** Šablona dne = jeden dokument (PUSH_A …) se seznamem cviků v pořadí. */
    suspend fun uploadWorkoutTemplate(key: String, exerciseIds: List<String>) {
        if (!isLoggedIn) return
        userDoc().collection("workout_templates").document(key).set(mapOf("exercises" to exerciseIds)).await()
    }

    suspend fun downloadAllWorkoutTemplates(): Map<String, List<String>> {
        if (!isLoggedIn) return emptyMap()
        return userDoc().collection("workout_templates").get().await().documents.associate { doc ->
            @Suppress("UNCHECKED_CAST")
            doc.id to ((doc.get("exercises") as? List<String>) ?: emptyList())
        }
    }

    // ========== ŠABLONY JÍDEL (docs/adr/0027) ==========

    suspend fun uploadMealTemplate(t: MealTemplateEntity) {
        if (!isLoggedIn) return
        val data = mapOf("name" to t.name, "kind" to t.kind, "items" to t.items, "lastUsedAt" to t.lastUsedAt)
        userDoc().collection("meal_templates").document(t.createdAt.toString()).set(data, SetOptions.merge()).await()
    }

    suspend fun deleteMealTemplate(createdAt: Long) {
        if (!isLoggedIn) return
        userDoc().collection("meal_templates").document(createdAt.toString()).delete().await()
    }

    suspend fun downloadAllMealTemplates(): List<MealTemplateEntity> {
        if (!isLoggedIn) return emptyList()
        return userDoc().collection("meal_templates").get().await().documents.mapNotNull { doc ->
            MealTemplateEntity(
                createdAt = doc.id.toLongOrNull() ?: return@mapNotNull null,
                name = doc.getString("name") ?: return@mapNotNull null,
                kind = doc.getString("kind") ?: "MEAL",
                items = doc.getString("items") ?: return@mapNotNull null,
                lastUsedAt = doc.getLong("lastUsedAt") ?: 0L
            )
        }
    }

    // ========== KONZUMACE ==========

    suspend fun uploadConsumedSnack(consumed: ConsumedSnackEntity) {
        if (!isLoggedIn) return
        val data = mapOf(
            "date" to consumed.date,
            "time" to consumed.time,
            "name" to consumed.name,
            "p" to consumed.p,
            "s" to consumed.s,
            "t" to consumed.t,
            "calories" to consumed.calories,
            "energyKj" to consumed.energyKj,    // PŘIDÁNO
            "fiber" to consumed.fiber,          // PŘIDÁNO
            "mealContext" to consumed.mealContext,
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
                date        = doc.getString("date") ?: "",
                time        = doc.getString("time") ?: "",
                name        = doc.getString("name") ?: "",
                p = (doc.getDouble("p") ?: 0.0).toFloat(),
                s = (doc.getDouble("s") ?: 0.0).toFloat(),
                t = (doc.getDouble("t") ?: 0.0).toFloat(),
                calories    = (doc.getLong("calories") ?: 0L).toInt(),
                energyKj    = (doc.getDouble("energyKj") ?: 0.0).toFloat(),    // PŘIDÁNO
                fiber       = (doc.getDouble("fiber") ?: 0.0).toFloat(),       // PŘIDÁNO
                mealContext = doc.getString("mealContext") ?: "NO_TRAINING"
            )
        }
    }

    suspend fun deleteConsumedSnack(timestamp: Long) {
        if (!isLoggedIn) return
        userDoc().collection("consumed_history").document(timestamp.toString()).delete().await()
    }

    // ========== POKÉMONI ==========

    suspend fun uploadCapturedMakromon(makromon: CapturedMakromonEntity) {
        if (!isLoggedIn) return
        val data = mapOf(
            "makromonId" to makromon.makromonId,
            "name" to makromon.name,
            "isShiny" to makromon.isShiny,
            "isLocked" to makromon.isLocked,
            "caughtDate" to makromon.caughtDate,
            "level" to makromon.level,
            "xp" to makromon.xp,
            "moves" to makromon.moveListStr,
            "uid" to makromon.uid,
            "otName" to makromon.otName
        )
        userDoc().collection("captured_makromons").document(makromon.caughtDate.toString())
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllCapturedMakromons(): List<CapturedMakromonEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("captured_makromons").get().await()
        return snaps.documents.mapNotNull { doc ->
            CapturedMakromonEntity(
                id = 0,
                makromonId  = doc.getString("makromonId") ?: "",
                name        = doc.getString("name") ?: "",
                isShiny     = doc.getBoolean("isShiny") ?: false,
                isLocked    = doc.getBoolean("isLocked") ?: false,
                caughtDate  = doc.getLong("caughtDate") ?: System.currentTimeMillis(),
                level       = (doc.getLong("level") ?: 1L).toInt(),
                xp          = (doc.getLong("xp") ?: 0L).toInt(),
                moveListStr = doc.getString("moves") ?: "",
                uid         = doc.getString("uid") ?: java.util.UUID.randomUUID().toString(),
                otName      = doc.getString("otName") ?: ""
            )
        }
    }

    suspend fun deleteCapturedMakromon(caughtDate: Long) {
        if (!isLoggedIn) return
        userDoc().collection("captured_makromons").document(caughtDate.toString()).delete().await()   // dřív „captured_makromon“ – mazalo se jinde, než se ukládá
    }

    // ========== POKÉDEX, ITEMS, XP ==========

    suspend fun uploadMakrodexStatus(makromonId: String) {
        if (!isLoggedIn) return
        val data = mapOf("unlocked" to true, "unlockedDate" to System.currentTimeMillis())
        userDoc().collection("makrodex_status").document(makromonId).set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllMakrodexStatus(): List<MakrodexStatusEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("makrodex_status").get().await()
        return snaps.documents.mapNotNull { doc ->
            MakrodexStatusEntity(makromonId = doc.id, unlocked = true,
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

    suspend fun uploadMakromonXp(xp: MakromonXpEntity) {
        if (!isLoggedIn) return
        val data = mapOf("totalXp" to xp.totalXp, "lastDailyRewardDate" to xp.lastDailyRewardDate)
        userDoc().collection("makromon_xp").document(xp.makromonId).set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllMakromonXp(): List<MakromonXpEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("makromon_xp").get().await()
        return snaps.documents.mapNotNull { doc ->
            MakromonXpEntity(makromonId = doc.id,
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


    // ========== QUESTY ==========

    suspend fun uploadQuestProgress(progress: QuestProgressEntity) {
        if (!isLoggedIn) return
        val data = mapOf(
            "currentStageIndex" to progress.currentStageIndex,
            "isCompleted"      to progress.isCompleted,
            "metadata"         to progress.metadata,
            "lastUpdated"      to progress.lastUpdated,
            "stageStartedAt"   to progress.stageStartedAt
        )
        userDoc().collection("quest_progress").document(progress.questId)
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadAllQuestProgress(): List<QuestProgressEntity> {
        if (!isLoggedIn) return emptyList()
        val snaps = userDoc().collection("quest_progress").get().await()
        return snaps.documents.mapNotNull { doc ->
            QuestProgressEntity(
                questId           = doc.id,
                currentStageIndex = (doc.getLong("currentStageIndex") ?: 0L).toInt(),
                isCompleted       = doc.getBoolean("isCompleted") ?: false,
                metadata          = doc.getString("metadata") ?: "",
                lastUpdated       = doc.getLong("lastUpdated") ?: System.currentTimeMillis(),
                stageStartedAt    = doc.getLong("stageStartedAt") ?: 0L
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
            prefs.getString("kardio_jumps_$day", null)?.let    { planMap["kardio_jumps_$day"] = it }

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

        localDb.capturedMakromonDao().getAllCaught().forEach { makromon ->
            uploadCapturedMakromon(makromon)
            uploadMakrodexStatus(makromon.makromonId)
            localDb.makromonXpDao().getXp(makromon.makromonId)?.let { uploadMakromonXp(it) }
        }
        localDb.makrodexStatusDao().getUnlockedIds().forEach { uploadMakrodexStatus(it) }
        localDb.achievementDao().getAllUnlocked().forEach   { uploadAchievement(it) }

        localDb.questDao().getAllQuests().forEach { uploadQuestProgress(it) }
        localDb.workoutDao().getAllSync().forEach { uploadWorkoutSet(it) }
        localDb.workoutDao().allTemplatesSync().groupBy { it.templateKey }.forEach { (key, rows) ->
            uploadWorkoutTemplate(key, rows.sortedBy { it.position }.map { it.exerciseId })
        }
        localDb.mealTemplateDao().allSync().forEach { uploadMealTemplate(it) }

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
            val makromon      = downloadAllCapturedMakromons()
            val achievements = downloadAllAchievements()
            val makrodex      = downloadAllMakrodexStatus()
            val makromonXp    = downloadAllMakromonXp()
            val steps        = downloadAllSteps()
            val analytics    = downloadAllAnalytics()

            val questProgress = downloadAllQuestProgress()
            val workoutSets   = downloadAllWorkoutSets()
            val workoutTemplates = downloadAllWorkoutTemplates()
            val mealTemplates = downloadAllMealTemplates()

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
            // postup příběhu (story_* předměty) zpět do GamePrefs – docs/adr/0044
            runCatching { cz.uhk.macroflow.pokemon.story.StoryFlags.sync(context) }

            localDb.capturedMakromonDao().deleteAllCapturedLocally()
            makromon.forEach { localDb.capturedMakromonDao().insertMakromon(it) }

            achievements.forEach { localDb.achievementDao().unlock(it) }
            makrodex.forEach      { localDb.makrodexStatusDao().unlockMakromon(it) }
            makromonXp.forEach    { localDb.makromonXpDao().setXp(it) }
            steps.forEach        { localDb.stepsDao().insertSteps(it) }

            questProgress.forEach { localDb.questDao().saveQuestProgress(it) }

            if (workoutSets.isNotEmpty()) {
                localDb.workoutDao().deleteAllLocally()
                workoutSets.forEach { localDb.workoutDao().insert(it) }
            }
            workoutTemplates.forEach { (key, ids) -> localDb.workoutDao().replaceTemplate(key, ids) }
            mealTemplates.forEach { localDb.mealTemplateDao().upsert(it) }

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

    /** Všechny podkolekce users/{uid}. Při přidání nové kolekce ji doplň sem (hlídá FirebaseCollectionsTest). */
    val USER_COLLECTIONS = listOf(
        "data", "checkins", "body_metrics", "analytics", "custom_snacks", "consumed_history",
        "water", "wallet", "user_items", "captured_makromons", "makrodex_status", "makromon_xp",
        "unlocked_achievements", "steps", "quest_progress", "workout_sets", "workout_templates",
        "meal_templates"
    )

    // ========== ARÉNA (docs/adr/0076) ==========

    private const val ARENA = "arena"

    /** Nahraje tým přihlášeného hráče jako jeho ducha v aréně. */
    suspend fun publishArenaGhost(trainer: cz.uhk.macroflow.pokemon.trainer.Trainer) {
        val uid = auth.currentUser?.uid ?: return
        // merge: body v ranku (points, rankedAt) zůstanou, jak jsou
        db.collection(ARENA).document(uid).set(cz.uhk.macroflow.pokemon.trainer.Trainers.toMap(trainer), SetOptions.merge()).await()
    }

    /** Body v ranku do cloudu (docs/adr/0079); pravidla pustí nejvýš +25 a ne častěji než po 20 s. */
    suspend fun updateArenaPoints(points: Int) {
        val uid = auth.currentUser?.uid ?: return
        db.collection(ARENA).document(uid).update(mapOf(
            "points" to points, "rankedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )).await()
    }

    /** Moje body v cloudu (null = ještě nehrál hodnocený zápas / dokument neexistuje). */
    suspend fun myArenaPoints(): Int? {
        val uid = auth.currentUser?.uid ?: return null
        return db.collection(ARENA).document(uid).get().await().getLong("points")?.toInt()
    }

    /** Žebříček: nejlepší hráči podle bodů. */
    suspend fun arenaLeaderboard(limit: Long = 50): List<cz.uhk.macroflow.pokemon.trainer.Trainer> =
        db.collection(ARENA).orderBy("points", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(limit)
            .get().await().documents.mapNotNull { cz.uhk.macroflow.pokemon.trainer.Trainers.fromMap(it.id, it.data) }
            .mapNotNull { t -> cz.uhk.macroflow.pokemon.trainer.Trainers.sanitize(t) }

    /** Moje pořadí (1 = první): kolik hráčů má víc bodů + 1. */
    suspend fun arenaPlace(points: Int): Long =
        db.collection(ARENA).whereGreaterThan("points", points).count()
            .get(com.google.firebase.firestore.AggregateSource.SERVER).await().count + 1

    /** Naposledy aktivní duchové (už ověření – neplatná data vypadnou). */
    suspend fun fetchArenaGhosts(limit: Long = 40): List<cz.uhk.macroflow.pokemon.trainer.Trainer> {
        if (!isLoggedIn) return emptyList()
        return db.collection(ARENA).orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit).get().await().documents
            .mapNotNull { cz.uhk.macroflow.pokemon.trainer.Trainers.fromMap(it.id, it.data) }
            .map { it.copy(kind = cz.uhk.macroflow.pokemon.trainer.Trainer.Kind.GHOST) }
            .mapNotNull(cz.uhk.macroflow.pokemon.trainer.Trainers::sanitize)
    }

    // ========== VÝMĚNY (docs/adr/0077) ==========

    private const val TRADES = "trades"

    private fun permissionDenied(e: Exception) =
        (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED

    /** Založí výměnu pod kódem; false = kód už je obsazený (pravidla nedovolí přepsat cizí výměnu). */
    suspend fun createTrade(code: String, data: Map<String, Any?>): Boolean = try {
        db.collection(TRADES).document(code).set(data).await(); true
    } catch (e: Exception) { if (permissionDenied(e)) false else throw e }

    /** Výměna podle kódu; null = neexistuje nebo k ní hráč nemá přístup. */
    suspend fun getTrade(code: String): cz.uhk.macroflow.pokemon.trade.Trade? = try {
        db.collection(TRADES).document(code).get().await().let { cz.uhk.macroflow.pokemon.trade.Trading.fromMap(code, it.data) }
    } catch (e: Exception) { if (permissionDenied(e)) null else throw e }

    suspend fun joinTrade(code: String, name: String, offer: cz.uhk.macroflow.pokemon.trade.TradeOffer) {
        val uid = auth.currentUser?.uid ?: return
        db.collection(TRADES).document(code).update(mapOf(
            "b" to uid, "bName" to name.take(16),
            "offerB" to cz.uhk.macroflow.pokemon.trade.Trading.offerToMap(offer),
            "state" to cz.uhk.macroflow.pokemon.trade.Trading.OFFERED
        )).await()
    }

    suspend fun confirmTrade(code: String, role: cz.uhk.macroflow.pokemon.trade.Trading.Role) {
        db.collection(TRADES).document(code).update(if (role == cz.uhk.macroflow.pokemon.trade.Trading.Role.A) "confirmA" else "confirmB", true).await()
    }

    suspend fun markTradeApplied(code: String, role: cz.uhk.macroflow.pokemon.trade.Trading.Role) {
        db.collection(TRADES).document(code).update(if (role == cz.uhk.macroflow.pokemon.trade.Trading.Role.A) "appliedA" else "appliedB", true).await()
    }

    suspend fun cancelTrade(code: String) {
        db.collection(TRADES).document(code).update("state", cz.uhk.macroflow.pokemon.trade.Trading.CANCELLED).await()
    }

    /** Živé změny výměny (druhý hráč se připojil, potvrdil…). */
    fun listenTrade(code: String, onChange: (cz.uhk.macroflow.pokemon.trade.Trade?) -> Unit) =
        db.collection(TRADES).document(code).addSnapshotListener { snap, _ ->
            onChange(snap?.let { cz.uhk.macroflow.pokemon.trade.Trading.fromMap(code, it.data) })
        }

    suspend fun deleteAllUserData() {
        val uid = auth.currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Smazání všech kolekcí uživatele – názvy MUSÍ odpovídat těm, do kterých se ukládá
        // (dřív tu byly jiné názvy – checkIns, consumedSnacks… – a většina dat v cloudu zůstala).
        USER_COLLECTIONS.forEach { collection ->
            try {
                val docs = db.collection("users").document(uid)
                    .collection(collection).get().await()
                docs.documents.forEach { it.reference.delete().await() }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // Duch v aréně (docs/adr/0076)
        try { db.collection(ARENA).document(uid).delete().await() } catch (e: Exception) { e.printStackTrace() }

        // Smazání hlavního dokumentu uživatele
        try {
            db.collection("users").document(uid).delete().await()
        } catch (e: Exception) { e.printStackTrace() }
    }
}