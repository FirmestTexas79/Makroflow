package cz.uhk.macroflow.pokemon

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import cz.uhk.macroflow.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Krátký přechod do Makrosvěta (docs/adr/0070). Dvě poloviny, každá v jiné aktivitě:
 *  - [cover] (aplikace): pixelové dlaždice se od středu „kosočtvercem“ poskládají přes obrazovku,
 *    na konci naskočí nápis MAKROSVĚT,
 *  - [reveal] (mapa): chvíli drží, pak se dlaždice od středu rozpadnou a odhalí svět.
 * Mřížka se počítá ze šířky obrazovky, takže obě poloviny na sebe navazují.
 */
class PortalTransitionView(context: Context, private val covering: Boolean) : View(context) {

    var progress = 0f
        set(v) { field = v; invalidate() }

    private val dark = Paint().apply { color = 0xFF283618.toInt(); isAntiAlias = false }
    private val darker = Paint().apply { color = 0xFF222E14.toInt(); isAntiAlias = false }
    private val font = runCatching { ResourcesCompat.getFont(context, R.font.jersey_15) }.getOrNull()
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFEFAE0.toInt(); typeface = font; textAlign = Paint.Align.CENTER
    }
    private val shadow = Paint(title).apply { color = 0xFF0F1508.toInt() }
    private val accent = Paint().apply { color = 0xFFE9B072.toInt(); isAntiAlias = false }

    init {
        isClickable = true   // během přechodu nic neprokliknout
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val cell = w / COLS
        val rows = ceil(h / cell).toInt()
        val cx = (COLS - 1) / 2f; val cy = (rows - 1) / 2f
        val maxD = cx + cy
        for (r in 0 until rows) for (c in 0 until COLS) {
            val d = (abs(c - cx) + abs(r - cy)) / maxD                // 0 střed … 1 roh
            val t = ((progress - d * SPREAD) / (1f - SPREAD)).coerceIn(0f, 1f)
            val size = if (covering) ease(t) else 1f - ease(t)
            if (size <= 0f) continue
            val half = cell * size / 2f + if (size >= 1f) 0.5f else 0f   // bez švů mezi plnými dlaždicemi
            val x = c * cell + cell / 2f; val y = r * cell + cell / 2f
            canvas.drawRect(x - half, y - half, x + half, y + half, if ((c + r) % 2 == 0) dark else darker)
        }

        val a = if (covering) ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f) else 1f - (progress / 0.35f).coerceIn(0f, 1f)
        if (a <= 0f) return
        val size = w / 6.2f
        title.textSize = size; shadow.textSize = size
        val lift = (1f - a) * size * 0.25f * (if (covering) 1f else -1f)
        val y = h / 2f + size * 0.3f + lift
        val px = max(2f, size / 14f)
        val alpha = (a * 255).toInt()
        title.alpha = alpha; shadow.alpha = alpha; accent.alpha = alpha
        canvas.drawText(TITLE, w / 2f + px, y + px, shadow)
        canvas.drawText(TITLE, w / 2f, y, title)
        // pixelová linka pod nápisem
        val lw = title.measureText(TITLE) * 0.6f * a
        canvas.drawRect(w / 2f - lw / 2f, y + px * 3, w / 2f + lw / 2f, y + px * 5, accent)
    }

    private fun ease(t: Float) = 1f - (1f - t) * (1f - t)

    companion object {
        private const val COLS = 9
        private const val SPREAD = 0.55f
        private const val TITLE = "MAKROSVĚT"

        /** Překryje [root] a po dokončení zavolá [then]; overlay zůstává, odstraní ho [remove]. */
        fun cover(root: ViewGroup, then: () -> Unit): PortalTransitionView {
            val v = PortalTransitionView(root.context, covering = true)
            root.addView(v, ViewGroup.LayoutParams(-1, -1))
            run(v, 480, 0) { then() }
            return v
        }

        /** Odhalí [root] (mapa), overlay se na konci sám odstraní. */
        fun reveal(root: ViewGroup) {
            val v = PortalTransitionView(root.context, covering = false)
            root.addView(v, ViewGroup.LayoutParams(-1, -1))
            v.post { run(v, 620, 160) { root.removeView(v) } }
        }

        private fun run(v: PortalTransitionView, ms: Long, delay: Long, end: () -> Unit) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ms; startDelay = delay; interpolator = LinearInterpolator()
                addUpdateListener { v.progress = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = end()
                })
            }.start()
        }
    }
}
