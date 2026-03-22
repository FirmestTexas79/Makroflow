package cz.uhk.macroflow

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

object FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val isLoggedIn: Boolean get() = currentUser != null

    private fun userDoc() = db.collection("users").document(currentUser!!.uid)

    // ========== PROFIL ==========

    suspend fun uploadProfile(profile: UserProfileEntity) {
        val data = mapOf(
            "weight" to profile.weight,
            "height" to profile.height,
            "age" to profile.age,
            "gender" to profile.gender,
            "activityMultiplier" to profile.activityMultiplier
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
            activityMultiplier = (snap.getDouble("activityMultiplier") ?: 1.2).toFloat()
        )
    }

    // ========== TRÉNINKOVÝ PLÁN ==========

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

    // ========== CHECK-INY ==========

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

    // ========== TĚLESNÉ MÍRY ==========

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

    // --- VLASTNÍ POTRAVINY (Číselník) ---
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

    // --- SNĚDENÉ POTRAVINY (Historie konzumace) ---
    suspend fun uploadConsumedSnack(consumed: ConsumedSnackEntity) {
        val data = mapOf(
            "date" to consumed.date,
            "time" to consumed.time,
            "name" to consumed.name,
            "p" to consumed.p,
            "s" to consumed.s,
            "t" to consumed.t,
            "calories" to consumed.calories
        )
        userDoc().collection("consumed_history").document(consumed.id.toString())
            .set(data, SetOptions.merge()).await()
    }

    suspend fun downloadConsumedByDate(date: String): List<ConsumedSnackEntity> {
        val snaps = userDoc().collection("consumed_history")
            .whereEqualTo("date", date).get().await()

        return snaps.documents.mapNotNull { doc ->
            ConsumedSnackEntity(
                id = doc.id.toIntOrNull() ?: 0,
                date = doc.getString("date") ?: date,
                time = doc.getString("time") ?: "",
                name = doc.getString("name") ?: "",
                p = (doc.getDouble("p") ?: 0.0).toFloat(),
                s = (doc.getDouble("s") ?: 0.0).toFloat(),
                t = (doc.getDouble("t") ?: 0.0).toFloat(),
                calories = (doc.getLong("calories") ?: 0L).toInt()
            )
        }
    }

    // ========== SYNC (Room → Cloud po přihlášení) ==========

    suspend fun syncLocalDataToCloud(context: android.content.Context) {
        val db = AppDatabase.getDatabase(context)

        // 1. Profil
        db.userProfileDao().getProfileSync()?.let { uploadProfile(it) }

        // 2. Tréninkový plán
        val prefs = context.getSharedPreferences("TrainingPrefs", android.content.Context.MODE_PRIVATE)
        val days = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
        val plan = days.associateWith { prefs.getString("type_$it", "rest") ?: "rest" }
        uploadTrainingPlan(plan)

        // 3. Check-iny
        db.checkInDao().getAllCheckInsSync().forEach { uploadCheckIn(it) }

        // 4. Tělesné míry
        db.bodyMetricsDao().getAllSync().forEach { uploadBodyMetrics(it) }

        // 5. Vlastní snacky (využívá tvé Flow a vytáhne z něj aktuální snapshot)
        db.snackDao().getAllSnacks().first().forEach { uploadCustomSnack(it) }

        // 6. Historie konzumace (prochází dnešní datum přes tvé Flow)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        db.consumedSnackDao().getConsumedByDate(today).first().forEach { uploadConsumedSnack(it) }
    }

    suspend fun syncCloudDataToLocal(context: android.content.Context) {
        val localDb = AppDatabase.getDatabase(context)

        // 1. Profil
        downloadProfile()?.let { localDb.userProfileDao().saveProfile(it) }

        // 2. Tréninkový plán
        val plan = downloadTrainingPlan()
        if (plan.isNotEmpty()) {
            val prefs = context.getSharedPreferences("TrainingPrefs", android.content.Context.MODE_PRIVATE)
            val editor = prefs.edit()
            plan.forEach { (day, type) -> editor.putString("type_$day", type) }
            editor.apply()
        }

        // 3. Check-iny
        downloadAllCheckIns().forEach { localDb.checkInDao().insertCheckIn(it) }

        // 4. Tělesné míry
        downloadAllBodyMetrics().forEach { localDb.bodyMetricsDao().save(it) }

        // 5. Vlastní snacky
        downloadAllCustomSnacks().forEach { localDb.snackDao().insertSnack(it) }

        // 6. Historie konzumace
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        downloadConsumedByDate(today).forEach { localDb.consumedSnackDao().insertConsumed(it) }
    }

    fun signOut() = auth.signOut()
}