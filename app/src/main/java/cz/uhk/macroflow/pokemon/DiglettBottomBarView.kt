package cz.uhk.macroflow.pokemon

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Malý pixel Diglett zobrazený v bottom navigation baru.
 * Přidej do activity_main.xml přímo do BottomNavigationView layoutu,
 * nebo jako overlay nad ním.
 *
 * Zobraz/skryj v MainActivity.onResume():
 *   val diglett = findViewById<DiglettBottomBarView>(R.id.diglettBottomBar)
 *   diglett.visibility = if (PokemonBattleFragment.isDiglettAcquired(this)) View.VISIBLE else View.GONE
 */
class DiglettBottomBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // Diglett mini sprite — 15×14 px z gridu
    private val px: IntArray by lazy { PokemonSprites.DIGLETT2 }

    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }

    override fun onMeasure(wms: Int, hms: Int) {
        // Zobrazíme ve 3× scale = 45×42 px
        setMeasuredDimension(45, 42)
    }

    override fun onDraw(canvas: Canvas) {
        val scale = 3f
        val w = PokemonSprites.DIGLETT_W
        val h = PokemonSprites.DIGLETT_H
        for (i in px.indices) {
            val color = px[i]
            if (color == Color.TRANSPARENT || color == 0) continue
            paint.color = color
            val col = i % w; val row = i / w
            canvas.drawRect(
                col * scale, row * scale,
                col * scale + scale, row * scale + scale,
                paint
            )
        }
    }
}