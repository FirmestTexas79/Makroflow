package cz.uhk.macroflow.wearable

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import cz.uhk.macroflow.data.AppDatabase
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Garmin Personal API: klic -> bearer (~24 h) -> GET. Jen pro vlastni data uzivatele (viz podminky Garminu). */
class GarminClient(private val apiKey: String, private val http: OkHttpClient = OkHttpClient()) {
    private var bearer: String? = null

    private fun token(): String = bearer ?: http.newCall(
        Request.Builder().url("$BASE/personalapitoken-service/personal-api/personal-api-key/token")
            .header("Authorization", "ApiKey $apiKey").post("".toRequestBody()).build()
    ).execute().use { r ->
        check(r.isSuccessful) { "Garmin token: HTTP ${r.code}" }
        r.body!!.string().trim().also { bearer = it }
    }

    private fun get(path: String): String {
        repeat(2) {
            http.newCall(Request.Builder().url(BASE + path).header("Authorization", "Bearer ${token()}").build()).execute().use { r ->
                if (r.code == 401) { bearer = null; return@repeat }
                check(r.isSuccessful) { "Garmin $path: HTTP ${r.code}" }
                return r.body!!.string()
            }
        }
        error("Garmin: klic odmitnut (401)")
    }

    fun summaries(from: LocalDate, to: LocalDate) = get("/usersummary-service/v2/dailysummaries?startDate=$from&endDate=$to")
    fun sleeps(from: LocalDate, to: LocalDate) = get("/sleep-service/sleep/dailySleepSummariesByDate?startDate=$from&endDate=$to")
    fun activities(from: LocalDate, to: LocalDate) =
        get("/activitylist-service/activities/search/activities?start=0&limit=100&startDate=$from&endDate=$to")

    companion object { const val BASE = "https://personal.connectapi.garmin.com" }
}

/** Klic jen v sifrovanem ulozisti zarizeni (AES-GCM, klic v Android Keystore) - nikdy v repu ani APK. */
object GarminKeyStore {
    private const val ALIAS = "makroflow_garmin"
    private const val PREFS = "WearablePrefs"
    private const val K = "garmin_key_enc"

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun save(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (key.isBlank()) { prefs.edit().remove(K).apply(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val out = c.iv + c.doFinal(key.trim().toByteArray())
        prefs.edit().putString(K, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    fun load(context: Context): String? = runCatching {
        val raw = Base64.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K, null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, raw.copyOfRange(0, 12))) }
        String(c.doFinal(raw.copyOfRange(12, raw.size)))
    }.getOrNull()

    fun hasKey(context: Context) = load(context) != null
}

object WearableSync {
    /**
     * Stahne [days] dni po 14denních blocích a ulozi je. Bez [days]: prvni synchronizace dotahne 90 dni historie, dalsi jen 14.
     * Volat mimo hlavni vlakno. Bez klice / bez hodinek nic nedela. Vraci pocet dni.
     */
    fun run(context: Context, days: Int? = null, today: LocalDate = LocalDate.now()): Int {
        val key = GarminKeyStore.load(context) ?: return 0
        val c = GarminClient(key)
        val dao = AppDatabase.getDatabase(context).wearableDao()
        var left = days ?: if (dao.countSync() < 45) 90 else 14
        var end = today
        var total = 0
        while (left > 0) {
            val span = minOf(left, 14)
            val from = end.minusDays(span - 1L)
            val sleeps = GarminParser.sleeps(c.sleeps(from, end)).associateBy { it.date }
            val merged = GarminParser.summaries(c.summaries(from, end)).map { GarminParser.merge(it, sleeps[it.date]) }
            dao.upsertDays(merged)
            dao.upsertActivities(GarminParser.activities(c.activities(from, end)))
            total += merged.size
            end = from.minusDays(1); left -= span
        }
        return total
    }
    /** Sync nejvyse jednou za [maxAgeMs]; chyby sit/klic resi volajici (runCatching). */
    fun runIfStale(context: Context, maxAgeMs: Long = 2 * 3600_000L): Int {
        val prefs = context.getSharedPreferences("WearablePrefs", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong("last_sync", 0L) < maxAgeMs) return 0
        return run(context).also { prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply() }
    }
}
