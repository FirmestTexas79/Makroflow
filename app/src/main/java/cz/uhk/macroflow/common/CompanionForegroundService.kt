package cz.uhk.macroflow.common

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import coil.Coil
import coil.request.ImageRequest
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.training.TrainingTimeManager
import kotlinx.coroutines.*
import java.util.*

class CompanionForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val steps = intent?.getIntExtra("steps", 0) ?: 0
        updateNotification(steps)
        return START_STICKY
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

            // 🔥 Pokud je v prefs -1, ani se nepokoušej tahat z DB
            val activePokemon = withContext(Dispatchers.IO) {
                if (caughtDate != -1L) db.capturedPokemonDao().getPokemonByCaughtDate(caughtDate) else null
            }
            val profile = withContext(Dispatchers.IO) { db.userProfileDao().getProfileSync() }

            val rvSmall = RemoteViews(packageName, R.layout.notification_sticky)
            val rvLarge = RemoteViews(packageName, R.layout.notification_sticky_large)

            val stepGoal = profile?.stepGoal ?: 6000
            val timeStr = TrainingTimeManager.getTrainingTimeForToday(applicationContext) ?: "--:--"
            val dayName = java.text.SimpleDateFormat("EEEE", java.util.Locale.ENGLISH).format(java.util.Date())
            val workoutType = trainingPrefs.getString("type_$dayName", "REST")?.uppercase() ?: "REST"

            // ─── LOGIKA VIDITELNOSTI POKÉMONA ───
            val hasPokemon = activePokemon != null

            if (hasPokemon) {
                val pName = activePokemon!!.name.uppercase()
                // Viditelné prvky
                rvSmall.setViewVisibility(R.id.tvNotificationTitle, View.VISIBLE)
                rvSmall.setViewVisibility(R.id.ivNotificationPokemon, View.VISIBLE)
                rvSmall.setTextViewText(R.id.tvNotificationTitle, pName)

                rvLarge.setViewVisibility(R.id.tvNotificationTitle, View.VISIBLE)
                rvLarge.setViewVisibility(R.id.tvNotificationLevelLarge, View.VISIBLE)
                rvLarge.setViewVisibility(R.id.ivNotificationPokemon, View.VISIBLE)
                rvLarge.setTextViewText(R.id.tvNotificationTitle, pName)
                rvLarge.setTextViewText(R.id.tvNotificationLevelLarge, "LEVEL: ${activePokemon.level}")
            } else {
                // 🔥 TOTÁLNÍ SMAZÁNÍ POKÉMON STUPIDIT (GONE nezabírá místo)
                rvSmall.setViewVisibility(R.id.tvNotificationTitle, View.GONE)
                rvSmall.setViewVisibility(R.id.ivNotificationPokemon, View.GONE)

                rvLarge.setViewVisibility(R.id.tvNotificationTitle, View.GONE)
                rvLarge.setViewVisibility(R.id.tvNotificationLevelLarge, View.GONE)
                rvLarge.setViewVisibility(R.id.ivNotificationPokemon, View.GONE)
            }

            // Společná fitness data (vždy viditelná)
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

            // Spustíme service hned se základními daty
            startForeground(MakroflowNotifications.ID_STICKY_SERVICE, builder.build())

            // Načtení obrázku pouze pokud Pokémon existuje
            if (hasPokemon) {
                activePokemon?.let { pokemon ->
                    val webName = pokemon.name.lowercase().trim()
                        .replace(" ", "-").replace(".", "")
                        .replace("♀", "-f").replace("♂", "-m")

                    val url = "https://img.pokemondb.net/sprites/lets-go-pikachu-eevee/normal/$webName.png"

                    val request = ImageRequest.Builder(applicationContext)
                        .data(url)
                        .allowHardware(false)
                        .target { drawable ->
                            val bitmap = (drawable as? BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                rvSmall.setImageViewBitmap(R.id.ivNotificationPokemon, bitmap)
                                val giantPokemon = getCroppedAndScaledBitmap(bitmap)
                                rvLarge.setImageViewBitmap(R.id.ivNotificationPokemon, giantPokemon)

                                // Refresh notifikace s obrázkem
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
                val alpha = (src.getPixel(x, y) shr 24) and 0xFF
                if (alpha > 30) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x
                    if (y < minY) minY = y; if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return src
        val cropped = Bitmap.createBitmap(src, minX, minY, (maxX - minX) + 1, (maxY - minY) + 1)

        // Upscale na 120px výšku pro ostrost na Pixelu
        val targetHeight = 120
        val aspectRatio = cropped.width.toFloat() / cropped.height.toFloat()
        val targetWidth = (targetHeight * aspectRatio).toInt()

        val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        scaled.density = src.density
        return scaled
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}