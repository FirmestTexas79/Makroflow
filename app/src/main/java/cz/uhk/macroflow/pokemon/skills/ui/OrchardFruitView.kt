package cz.uhk.macroflow.pokemon.skills.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import cz.uhk.macroflow.pokemon.skills.FruitArt
import cz.uhk.macroflow.pokemon.skills.Gathering
import cz.uhk.macroflow.pokemon.skills.Resource

/**
 * Ovoce na stromě ve Ztichlém sadu (Sadařství, docs/adr/0119). Překryv přes korunu stromu 32 × 40 art px (strom je v mapě
 * bez ovoce). Když se na stromě češe, každé místo v koruně prochází stadii květ → malé zelené → nalévá se → zralé
 * (posunutě, aby strom „žil“, a s postupem rozpracovaného kusu); bez česání na stromě kvetou jen květy.
 *
 * @param k velikost art pixelu ve světě mapy (px)
 */
class OrchardFruitView(ctx: Context, private val k: Float, private val resource: Resource, night: Boolean) : View(ctx) {
    var activity: Gathering.Activity? = null
        set(v) { field = v; invalidate() }
    var secPerUnit: Long = 0

    /** Místa v koruně (art px od levého horního rohu stromu) – banánovník má trsy blíž kmeni. */
    private val spots: List<Pair<Int, Int>> = if (resource == Resource.FRUIT_BANANA)
        listOf(10 to 17, 18 to 14, 14 to 21, 21 to 20)
    else listOf(7 to 17, 21 to 13, 13 to 23, 24 to 21, 10 to 9, 18 to 18, 5 to 22, 16 to 6)

    private val sprites: List<Bitmap> = (0..3).map { st ->
        Bitmap.createBitmap(FruitArt.tiny(resource, st), FruitArt.TINY_W, FruitArt.TINY_H, Bitmap.Config.ARGB_8888)
    }
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; alpha = if (night) 190 else 255 }
    private val src = Rect(0, 0, FruitArt.TINY_W, FruitArt.TINY_H)
    private val dst = RectF()

    val wantedWidth: Int get() = (32 * k).toInt()
    val wantedHeight: Int get() = (40 * k).toInt()

    /** Stadium podle postupu 0–1: první čtvrtina květ, pak malé zelené, nalévá se, poslední část zralé. */
    private fun stage(p: Float) = when {
        p < 0.22f -> 0
        p < 0.48f -> 1
        p < 0.78f -> 2
        else -> 3
    }

    override fun onDraw(c: Canvas) {
        val a = activity
        val p = if (a != null && secPerUnit > 0) Gathering.partial(a, System.currentTimeMillis() / 1000, secPerUnit).first else -1f
        spots.forEachIndexed { i, (x, y) ->
            val st = if (p < 0f) (if (i % 2 == 0) 0 else -1) else stage((p + i * 0.09f) % 1f)
            if (st < 0) return@forEachIndexed
            val bx = (x - FruitArt.TINY_W / 2f) * k; val by = (y - 1f) * k
            dst.set(bx, by, bx + FruitArt.TINY_W * k, by + FruitArt.TINY_H * k)
            c.drawBitmap(sprites[st], src, dst, paint)
        }
        if (p >= 0f) postInvalidateDelayed(1000)
    }
}
