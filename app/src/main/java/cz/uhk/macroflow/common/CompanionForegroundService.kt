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
import androidx.core.content.ContextCompat
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
    private var lastDateString: String = ""

    // Cache pro bitmapu, aby se při každém kroku nemusela znovu generovat
    private var cachedPokemonBitmap: Bitmap? = null
    private var lastPokemonId: Int? = null

    override fun onCreate() {
        super.onCreate()
        lastDateString = getTodayString()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        loadStepsAndPokemon()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        loadStepsAndPokemon()
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
            checkDateChange()
            todayStepsCount++
            saveStepsToDb(todayStepsCount)
            updateNotification(todayStepsCount)
        }
    }

    private fun checkDateChange() {
        val currentDate = getTodayString()
        if (currentDate != lastDateString) {
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
                lastDateString = todayStr
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
                    Log.e("MacroFlowService", "Upload failed: ${e.message}")
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
                if (caughtDate != -1L) db.capturedMakromonDao().getMakromonByCaughtDate(caughtDate) else null
            }
            val profile = withContext(Dispatchers.IO) { db.userProfileDao().getProfileSync() }

            val rvSmall = RemoteViews(packageName, R.layout.notification_sticky)
            val rvLarge = RemoteViews(packageName, R.layout.notification_sticky_large)

            val stepGoal = profile?.stepGoal ?: 6000
            val timeStr = TrainingTimeManager.getTrainingTimeForToday(applicationContext) ?: "--:--"
            val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date())
            val workoutType = trainingPrefs.getString("type_$dayName", "REST")?.uppercase() ?: "REST"

            val hasPokemon = activePokemon != null
            if (hasPokemon) {
                val pokemon = activePokemon!!
                val pName = pokemon.name.uppercase()

                rvSmall.setViewVisibility(R.id.tvNotificationTitle, View.VISIBLE)
                rvSmall.setViewVisibility(R.id.ivNotificationPokemon, View.VISIBLE)
                rvSmall.setTextViewText(R.id.tvNotificationTitle, pName)

                rvLarge.setViewVisibility(R.id.tvNotificationTitle, View.VISIBLE)
                rvLarge.setViewVisibility(R.id.tvNotificationLevelLarge, View.VISIBLE)
                rvLarge.setViewVisibility(R.id.ivNotificationPokemon, View.VISIBLE)
                rvLarge.setTextViewText(R.id.tvNotificationTitle, pName)
                rvLarge.setTextViewText(R.id.tvNotificationLevelLarge, "LEVEL: ${pokemon.level}")

                // LOGIKA NAČTENÍ LOKÁLNÍHO OBRÁZKU
                if (pokemon.id != lastPokemonId || cachedPokemonBitmap == null) {
                    val rawBitmap = getLocalMakromonBitmap(applicationContext, pokemon.makromonId, pokemon.name)
                    if (rawBitmap != null) {
                        cachedPokemonBitmap = getCroppedAndScaledBitmap(rawBitmap)
                        lastPokemonId = pokemon.id
                    }
                }

                if (cachedPokemonBitmap != null) {
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
        }
    }

    /**
     * Sestaví název resource a vrátí Bitmapu z lokálních drawable.
     */
    private fun getLocalMakromonBitmap(context: Context, makromonId: String, name: String): Bitmap? {
        val shortId = if (makromonId.length >= 3) makromonId.takeLast(2) else makromonId
        val namePart = name.lowercase().trim().replace(" ", "_")
        val drawableName = "makromon_${shortId}_$namePart"

        val resId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
        if (resId == 0) return null

        val drawable = ContextCompat.getDrawable(context, resId)
        return (drawable as? BitmapDrawable)?.bitmap
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