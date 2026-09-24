package cz.uhk.macroflow.pokemon.shiny

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView

/**
 * Shiny sprity: normální sprite Makromona projde filtrem [ShinyPalette] a výsledek se drží v paměti.
 * Jedno místo pro souboj, inventář, parťáka, lištu, notifikaci i evoluci.
 */
object ShinySprites {

    /** Klíč = resId; sprity jsou malé (desítky kB), 32 druhů se vejde bez problému. */
    private val cache = LruCache<Int, Bitmap>(48)

    private val opts = BitmapFactory.Options().apply {
        inScaled = false                       // pixel art v původním rozlišení, bez rozmazání
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    /** Přebarvená kopie spritu [resId] pro druh [makrodexId]; null, když zdroj nejde načíst. */
    fun bitmap(context: Context, resId: Int, makrodexId: String): Bitmap? {
        cache.get(resId)?.let { return it }
        val src = BitmapFactory.decodeResource(context.resources, resId, opts) ?: return null
        val out = recolor(src, makrodexId)
        cache.put(resId, out)
        return out
    }

    /** Přebarví libovolnou bitmapu (např. už načtenou v souboji nebo v notifikaci). */
    fun recolor(src: Bitmap, makrodexId: String): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        ShinyPalette.transformAll(px, ShinyPalette.hueShiftFor(makrodexId))
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Nastaví do [view] normální nebo shiny sprite. */
    fun into(view: ImageView, resId: Int, makrodexId: String, shiny: Boolean) {
        val bmp = if (shiny) bitmap(view.context, resId, makrodexId) else null
        if (bmp != null) view.setImageBitmap(bmp) else view.setImageResource(resId)
    }
}
