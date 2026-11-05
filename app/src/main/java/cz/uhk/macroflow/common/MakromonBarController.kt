package cz.uhk.macroflow.common

import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.LifecycleCoroutineScope
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.pokemon.PokemonBehavior
import cz.uhk.macroflow.pokemon.WandererFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MakromonBarController — řídí zobrazení Makromona na spodní liště MainActivity.
 *
 * Zodpovědnosti:
 * - Načtení aktivního Makromona z DB (podle caughtDate v GamePrefs)
 * - Načtení správného spritu z lokálního drawable (offline)
 * - Spuštění/zastavení WandererFactory animace
 * - Nastavení click listeneru pro tap reakce Makromona
 * - Retry logika pokud Makromon v DB ještě není (čeká 3s a zkusí znovu)
 *
 * Klíč pro detekci změny: "${caughtDate}_${makromonId}"
 * — caughtDate identifikuje konkrétního jedince, makromonId detekuje evoluci.
 *
 * Drawable konvence: makromon_${shortId}_${name}  např. makromon_02_ignaroc
 * — stejný formát jako MakrodexFragment, MakrodexAdapter a WandererFactory.
 *
 * Shiny logika zakomentována – odkomentuj až budou hotové shiny sprity:
 * val spriteType = if (caught.isShiny) "shiny" else "regular"
 */
class MakromonBarController(
    private val activity: MainActivity,
    private val ivPokemon: ImageView,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    private var behavior: PokemonBehavior? = null

    // Klíč pro detekci změny Makromona.
    // Obsahuje caughtDate (identifikuje jedince) + makromonId (detekuje evoluci).
    // Shiny zatím není součástí klíče – odkomentuj až budou shiny sprity:
    // private var lastLoadedKey: String = "" // "${caught.caughtDate}_${caught.makromonId}_${caught.isShiny}"
    private var lastLoadedKey: String = ""

    fun refresh() {
        val prefs = activity.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
        val acquired = prefs.getBoolean("pokemonAcquired", false)
        val activeCaughtDate = prefs.getLong("currentOnBarCaughtDate", -1L)

        // Parťák jde v Nastavení schovat (zakrývá spodní část obrazovek) – docs/adr/0022
        if (!acquired || activeCaughtDate == -1L || !AppSettings.companionOnScreen(activity)) {
            hide()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(activity)
            val caught = db.capturedMakromonDao().getMakromonByCaughtDate(activeCaughtDate)

            withContext(Dispatchers.Main) {
                if (caught == null) {
                    ivPokemon.visibility = View.GONE
                    Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 3000)
                    return@withContext
                }

                // Klíč obsahuje makromonId aby se detekovala evoluce
                val uniqueKey = "${caught.caughtDate}_${caught.makromonId}_${caught.isShiny}"

                if (uniqueKey == lastLoadedKey) {
                    // Stejný Makromon ve stejné formě — jen zajistíme viditelnost
                    ivPokemon.visibility = View.VISIBLE
                    if (behavior == null) startBehavior(caught.makromonId, caught.isShiny)
                    setupClickListener()
                    return@withContext
                }

                // Nový Makromon nebo evoluce — přenačteme sprite z lokálního drawable
                lastLoadedKey = uniqueKey
                stop()
                ivPokemon.visibility = View.INVISIBLE

                // Sestavení názvu drawable — stejná konvence jako MakrodexFragment a WandererFactory
                // Formát: makromon_${shortId}_${name}  např. makromon_02_ignaroc
                val shortId = if (caught.makromonId.length >= 3) caught.makromonId.takeLast(2) else caught.makromonId
                val name = caught.name.lowercase().trim().replace(" ", "_")
                val drawableName = "makromon_${shortId}_$name"

                val resId = activity.resources.getIdentifier(
                    drawableName, "drawable", activity.packageName
                )
                val finalResId = if (resId != 0) resId else R.drawable.ic_home

                cz.uhk.macroflow.pokemon.shiny.ShinySprites.into(ivPokemon, finalResId, caught.makromonId, caught.isShiny && resId != 0)
                ivPokemon.visibility = View.VISIBLE
                startBehavior(caught.makromonId, caught.isShiny)
                setupClickListener()
            }
        }
    }

    fun hide() {
        stop()
        ivPokemon.visibility = View.GONE
        lastLoadedKey = ""
    }

    fun stop() {
        behavior?.stop()
        behavior = null
    }

    private var touchReady = false
    private var dragging = false

    /** Klepnutí = reakce Makromona, dlouhé podržení = uchopí ho a jde s ním hýbat (pustíš = spadne zpátky na lištu). */
    private fun setupClickListener() {
        if (touchReady) return
        touchReady = true
        fun point(e: MotionEvent): Pair<Float, Float> {
            val loc = IntArray(2)
            (ivPokemon.parent as android.view.View).getLocationOnScreen(loc)
            return (e.rawX - loc[0]) to (e.rawY - loc[1])
        }
        val gestures = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { behavior?.onSpriteClicked(); return true }
            override fun onLongPress(e: MotionEvent) {
                val (x, y) = point(e)
                if (behavior?.grab(x, y) == true) {
                    dragging = true
                    ivPokemon.parent.requestDisallowInterceptTouchEvent(true)
                    ivPokemon.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        })
        ivPokemon.setOnTouchListener { _, e ->
            if (dragging && behavior == null) dragging = false
            if (dragging) {
                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> { val (x, y) = point(e); behavior?.carry(x, y) }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; behavior?.release() }
                }
            } else gestures.onTouchEvent(e)
            true
        }
    }

    private fun startBehavior(makromonId: String, shiny: Boolean) {
        behavior = WandererFactory.create(activity, ivPokemon, makromonId, shiny)
        behavior?.start()
    }
}