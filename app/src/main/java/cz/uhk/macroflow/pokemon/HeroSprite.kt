package cz.uhk.macroflow.pokemon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import cz.uhk.macroflow.pokemon.walk.HeroAnims

/**
 * Snímky postavy hráče (docs/adr/0051) z assets/hero – pásy se načtou jednou a rozřežou,
 * každý snímek je BitmapDrawable bez vyhlazení (ostré pixely při zvětšení).
 */
class HeroSprite(private val context: Context) {
    val spec: HeroAnims.Spec = context.assets.open("hero/hero.json").bufferedReader().use { HeroAnims.parseSpec(it.readText()) }

    private val cache = HashMap<String, List<BitmapDrawable>>()

    /** Snímky pásu [key] („walk_se“); chybějící pás = idle dolů. */
    fun frames(key: String): List<BitmapDrawable> = cache.getOrPut(key) {
        val name = if (spec.anims.containsKey(key)) key else "idle_s"
        val strip = context.assets.open("hero/$name.png").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
        } ?: return@getOrPut emptyList()
        val n = strip.width / spec.frameW
        (0 until n).map { i ->
            BitmapDrawable(context.resources, Bitmap.createBitmap(strip, i * spec.frameW, 0, spec.frameW, spec.frameH))
                .apply { isFilterBitmap = false; setAntiAlias(false) }
        }
    }

    fun durations(key: String): List<Int> = spec.anims[key] ?: spec.anims["idle_s"].orEmpty()

    /** Portrét do deníku: první snímek stání dolů, oříznutý na postavu. */
    fun portrait(): Bitmap? {
        val src = frames("idle_s").firstOrNull()?.bitmap ?: return null
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        var x0 = src.width; var y0 = src.height; var x1 = -1; var y1 = -1
        for (y in 0 until src.height) for (x in 0 until src.width) if ((px[y * src.width + x] ushr 24) != 0) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        if (x1 < 0) return null
        return Bitmap.createBitmap(src, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }
}
