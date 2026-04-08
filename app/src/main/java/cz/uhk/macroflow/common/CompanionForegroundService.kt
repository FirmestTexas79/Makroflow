package cz.uhk.macroflow.common

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import coil.Coil
import coil.request.ImageRequest
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.data.StepsEntity
import cz.uhk.macroflow.training.TrainingTimeManager
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class CompanionForegroundService : Service(), SensorEventListener {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var sensorManager: SensorManager? = null
    private var todayStepsCount = 0
    private var lastDateString: String = "" // Klíč pro detekci změny dne

    // Pomocné proměnné pro držení stavu, aby to neblikalo
    private var cachedPokemonBitmap: Bitmap? = null
    private var lastPokemonId: Int? = null

    override fun onCreate() {
        super.onCreate()
        // Inicializujeme datum hned při startu
        lastDateString = getTodayString()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        loadStepsAndPokemon()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Vynutíme refresh dat z DB (kdyby se změnil pokemon v aplikaci)
        loadStepsAndPokemon()
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
            // 1. Nejdřív zkontrolujeme, zda není nový den
            checkDateChange()

            // 2. Přičteme krok
            todayStepsCount++

            // 3. Uložíme a aktualizujeme
            saveStepsToDb(todayStepsCount)
            updateNotification(todayStepsCount)
        }
    }

    /**
     * Pokud se aktuální datum liší od naposledy zaznamenaného,
     * znamená to, že uživatel přešel do nového dne a musíme začít od nuly.
     */
    private fun checkDateChange() {
        val currentDate = getTodayString()
        if (currentDate != lastDateString) {
            Log.d("MacroFlowService", "Detekována změna dne ($lastDateString -> $currentDate). Resetuji kroky.")
            lastDateString = currentDate
            todayStepsCount = 0
        }
    }

    private fun getTodayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun loadStepsAndPokemon() {
        val todayStr = getTodayString()
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val entity = db.stepsDao().getStepsForDateSync(todayStr)

            withContext(Dispatchers.Main) {
                todayStepsCount = entity?.count ?: 0
                lastDateString = todayStr // Ujistíme se, že máme aktuální datum
                updateNotification(todayStepsCount)
            }
        }
    }

    private fun saveStepsToDb(count: Int) {
        val todayStr = getTodayString()
        val db = AppDatabase.getDatabase(applicationContext)
        val entity = StepsEntity(date = todayStr, count = count)
        serviceScope.launch(Dispatchers.IO) {
            db.stepsDao().insertSteps(entity)
            if (FirebaseRepository.isLoggedIn) {
                try { FirebaseRepository.uploadSteps(entity) } catch (e: Exception) {
                    Log.e("MacroFlowService", "Chyba uploadu kroků: ${e.message}")
                }
            }
        }
    }

    private fun updateNotification(currentSteps: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MakroflowNotifications.CHANNEL_STICKY,
                "Aktivní parťák",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }

        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val gamePrefs = getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
            val trainingPrefs = getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)

            val caughtDate = gamePrefs.getLong("currentOnBarCaughtDate", -1L)
            val activePokemon = withContext(Dispatchers.IO) {
                if (caughtDate != -1L) db.capturedPokemonDao().getPokemonByCaughtDate(caughtDate) else null
            }
            val profile = withContext(Dispatchers.IO) { db.userProfileDao().getProfileSync() }

            val rvSmall = RemoteViews(packageName, R.layout.notification_sticky)
            val rvLarge = RemoteViews(packageName, R.layout.notification_sticky_large)

            val stepGoal = profile?.stepGoal ?: 6000
            val timeStr = TrainingTimeManager.getTrainingTimeForToday(applicationContext) ?: "--:--"

            // Jméno dne v EN pro klíče v TrainingPrefs
            val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date())
            val workoutType = trainingPrefs.getString("type_$dayName", "REST")?.uppercase() ?: "REST"

            val hasPokemon = activePokemon != null
            if (hasPokemon) {
                val pName = activePokemon!!.name.uppercase()
                rvSmall.setViewVisibility(R.id.tvNotificationTitle, View.VISIBLE)
                rvSmall.setViewVisibility(R.id.ivNotificationPokemon, View.VISIBLE)
                rvSmall.setTextViewText(R.id.tvNotificationTitle, pName)

                rvLarge.setViewVisibility(R.id.tvNotificationTitle, View.VISIBLE)
                rvLarge.setViewVisibility(R.id.tvNotificationLevelLarge, View.VISIBLE)
                rvLarge.setViewVisibility(R.id.ivNotificationPokemon, View.VISIBLE)
                rvLarge.setTextViewText(R.id.tvNotificationTitle, pName)
                rvLarge.setTextViewText(R.id.tvNotificationLevelLarge, "LEVEL: ${activePokemon.level}")

                // Pokud už máme bitmapu v RAM, hned ji tam dáme
                if (activePokemon.id == lastPokemonId && cachedPokemonBitmap != null) {
                    rvSmall.setImageViewBitmap(R.id.ivNotificationPokemon, cachedPokemonBitmap)
                    rvLarge.setImageViewBitmap(R.id.ivNotificationPokemon, cachedPokemonBitmap)
                }
            } else {
                rvSmall.setViewVisibility(R.id.tvNotificationTitle, View.GONE)
                rvSmall.setViewVisibility(R.id.ivNotificationPokemon, View.GONE)
                rvLarge.setViewVisibility(R.id.tvNotificationTitle, View.GONE)
                rvLarge.setViewVisibility(R.id.tvNotificationLevelLarge, View.GONE)
                rvLarge.setViewVisibility(R.id.ivNotificationPokemon, View.GONE)
            }

            rvSmall.setTextViewText(R.id.tvNotificationSteps, "Kroky: $currentSteps / $stepGoal")
            rvSmall.setTextViewText(R.id.tvNotificationStatus, timeStr)
            rvLarge.setTextViewText(R.id.tvNotificationSteps, "Kroky: $currentSteps / $stepGoal")
            rvLarge.setTextViewText(R.id.tvNotificationStatus, timeStr)
            rvLarge.setTextViewText(R.id.tvNotificationWorkoutType, workoutType)

            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(applicationContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val builder = NotificationCompat.Builder(applicationContext, MakroflowNotifications.CHANNEL_STICKY)
                .setSmallIcon(R.drawable.ic_logo_white)
                .setCustomContentView(rvSmall)
                .setCustomBigContentView(rvLarge)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true)
                .setColor(0xFEFAE0)
                .setColorized(true)

            startForeground(MakroflowNotifications.ID_STICKY_SERVICE, builder.build())

            // Načítání obrázku - spustí se jen pokud se změnil pokemon nebo bitmapa chybí
            if (hasPokemon && (activePokemon?.id != lastPokemonId || cachedPokemonBitmap == null)) {
                activePokemon?.let { pokemon ->
                    val webName = pokemon.name.lowercase().trim().replace(" ", "-").replace(".", "").replace("♀", "-f").replace("♂", "-m")
                    val imageData: Any = if (pokemon.isShiny) {
                        val resId = resources.getIdentifier("shiny_${webName.replace("-", "_")}", "drawable", packageName)
                        if (resId != 0) resId else "https://img.pokemondb.net/sprites/ruby-sapphire/shiny/$webName.png"
                    } else {
                        "https://img.pokemondb.net/sprites/lets-go-pikachu-eevee/normal/$webName.png"
                    }

                    val request = ImageRequest.Builder(applicationContext)
                        .data(imageData)
                        .allowHardware(false)
                        .target { drawable ->
                            val bitmap = (drawable as? BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                val finalBitmap = getCroppedAndScaledBitmap(bitmap)
                                cachedPokemonBitmap = finalBitmap
                                lastPokemonId = pokemon.id

                                rvSmall.setImageViewBitmap(R.id.ivNotificationPokemon, finalBitmap)
                                rvLarge.setImageViewBitmap(R.id.ivNotificationPokemon, finalBitmap)
                                nm.notify(MakroflowNotifications.ID_STICKY_SERVICE, builder.build())
                            }
                        }.build()
                    Coil.imageLoader(applicationContext).enqueue(request)
                }
            }
        }
    }

    private fun getCroppedAndScaledBitmap(src: Bitmap): Bitmap {
        var minX = src.width; var maxX = -1; var minY = src.height; var maxY = -1
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                if (((src.getPixel(x, y) shr 24) and 0xFF) > 30) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return src
        val cropped = Bitmap.createBitmap(src, minX, minY, (maxX - minX) + 1, (maxY - minY) + 1)
        val targetHeight = 120
        val targetWidth = (targetHeight * (cropped.width.toFloat() / cropped.height.toFloat())).toInt()
        return Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager?.unregisterListener(this)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}