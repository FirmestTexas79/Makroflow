package cz.uhk.macroflow.pokemon.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Hudba a zvuky Makrosvěta (docs/adr/0017).
 *
 * * Hudba: jedna smyčka na lokaci (MediaPlayer, setLooping), při změně lokace se stará
 *   skladba ztiší a nová náběhem zesílí. Stejná skladba při přechodu (louka ↔ hvozd) hraje dál.
 * * Během souboje se hudba ztiší na [MusicMap.BATTLE_DUCK].
 * * Zvuky: SoundPool (začátek souboje, chycení, poražení).
 * * Vypínač zvuku v Makrosvětě: GamePrefs „soundEnabled“ (výchozí zapnuto).
 */
object GameAudio {
    enum class Sfx(val res: String) { BATTLE_START("sfx_battle_start"), CATCH("sfx_catch"), VICTORY("sfx_victory") }

    private const val PREF = "soundEnabled"
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var track: String? = null
    private var ducked = false
    private var paused = false
    private var soundPool: SoundPool? = null
    private val sfxIds = HashMap<Sfx, Int>()

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    fun isEnabled(ctx: Context) = ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).getBoolean(PREF, true)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit().putBoolean(PREF, on).apply()
        if (on) { val t = track; track = null; t?.let { playTrack(ctx, it) } } else stopMusic(fade = true)
    }

    private fun target() = if (ducked) MusicMap.BATTLE_DUCK else 1f

    /** Hudba pro lokaci (BiomeType.name). */
    fun playFor(ctx: Context, biome: String) {
        val t = MusicMap.trackFor(biome) ?: return stopMusic(fade = true)
        playTrack(ctx, t)
    }

    private fun playTrack(ctx: Context, name: String) {
        if (name == track && player != null) return
        track = name
        if (!isEnabled(ctx)) return
        val old = player
        player = null
        if (old != null) fadeOutAndRelease(old)
        val res = ctx.resources.getIdentifier(name, "raw", ctx.packageName)
        if (res == 0) return
        val mp = MediaPlayer.create(ctx.applicationContext, res) ?: return
        mp.setAudioAttributes(attrs)
        mp.isLooping = true
        mp.setVolume(0f, 0f)
        player = mp
        if (!paused) mp.start()
        ramp(mp, 0f, target(), MusicMap.FADE_IN_MS)
    }

    private fun ramp(mp: MediaPlayer, from: Float, to: Float, ms: Long, then: (() -> Unit)? = null) {
        val start = SystemClock.uptimeMillis()
        handler.post(object : Runnable {
            override fun run() {
                val el = SystemClock.uptimeMillis() - start
                val v = MusicMap.fade(from, to, el, ms)
                runCatching { mp.setVolume(v, v) }
                if (el < ms) handler.postDelayed(this, 40) else then?.invoke()
            }
        })
    }

    private fun fadeOutAndRelease(mp: MediaPlayer) {
        ramp(mp, target(), 0f, MusicMap.FADE_OUT_MS) { runCatching { mp.stop(); mp.release() } }
    }

    fun stopMusic(fade: Boolean) {
        val mp = player ?: return
        player = null
        if (fade) fadeOutAndRelease(mp) else runCatching { mp.stop(); mp.release() }
    }

    /** Ztišení hudby během souboje. */
    fun duck(on: Boolean) {
        if (ducked == on) return
        ducked = on
        val mp = player ?: return
        ramp(mp, if (on) 1f else MusicMap.BATTLE_DUCK, target(), 400)
    }

    fun pause() {
        paused = true
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    fun resume(ctx: Context) {
        paused = false
        if (!isEnabled(ctx)) return
        runCatching { player?.start() }
    }

    /** Uvolnit vše (odchod z Makrosvěta). */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.release() }
        player = null; track = null; ducked = false
        soundPool?.release(); soundPool = null; sfxIds.clear()
    }

    private fun pool(ctx: Context): SoundPool {
        soundPool?.let { return it }
        val sp = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        Sfx.entries.forEach { s ->
            val res = ctx.resources.getIdentifier(s.res, "raw", ctx.packageName)
            if (res != 0) sfxIds[s] = sp.load(ctx.applicationContext, res, 1)
        }
        soundPool = sp
        return sp
    }

    /** Připraví zvuky předem (načtení trvá chvilku – první přehrání by jinak mohlo vypadnout). */
    fun preload(ctx: Context) { pool(ctx) }

    fun sfx(ctx: Context, s: Sfx) {
        if (!isEnabled(ctx)) return
        val id = sfxIds[s] ?: run { pool(ctx); sfxIds[s] } ?: return
        pool(ctx).play(id, 1f, 1f, 1, 0, 1f)
    }
}
