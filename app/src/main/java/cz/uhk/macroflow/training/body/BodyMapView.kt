package cz.uhk.macroflow.training.body

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser

/**
 * Postava zepředu a zezadu vedle sebe; partie se podbarví podle intenzity 0..1
 * (0 = nezapojená, 0,5 = vedlejší / 1× týdně, 1 = hlavní / 2× týdně).
 * Změna se plynule přebarví, takže výběr PUSH/PULL/LEGS „rozsvítí“ tělo.
 */
class BodyMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var baseColor = Color.parseColor("#E4DEC0")
    var idleMuscleColor = Color.parseColor("#CFC8A6")
    /** Popisky „zepředu / zezadu“ pod postavami (u malých postav v kartě dne vypnuté). */
    var showCaptions = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80283618"); textAlign = Paint.Align.CENTER
        textSize = 10f * resources.displayMetrics.density
    }

    private var from: Map<Muscle, Int> = emptyMap()
    private var target: Map<Muscle, Int> = emptyMap()
    private var progress = 1f
    private var animator: ValueAnimator? = null

    /** Nastaví intenzity; [animate] = plynulé přebarvení z aktuálního stavu. */
    fun setIntensities(intensities: Map<Muscle, Double>, color: Int, animate: Boolean = true) {
        val newTarget = Muscle.entries.associateWith { m ->
            val i = (intensities[m] ?: 0.0).coerceIn(0.0, 1.0).toFloat()
            if (i <= 0f) idleMuscleColor else ColorUtils.blendARGB(idleMuscleColor, color, 0.35f + 0.65f * i)
        }
        from = current()
        target = newTarget
        animator?.cancel()
        if (!animate || !isLaidOut) { progress = 1f; invalidate(); return }
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun current(): Map<Muscle, Int> = Muscle.entries.associateWith { colorOf(it) }

    private fun colorOf(m: Muscle): Int {
        val t = target[m] ?: idleMuscleColor
        val f = from[m] ?: idleMuscleColor
        return if (progress >= 1f) t else ColorUtils.blendARGB(f, t, progress)
    }

    override fun onDraw(canvas: Canvas) {
        val captionH = if (showCaptions) captionPaint.textSize * 1.6f else 0f
        val gap = BodyShapes.WIDTH * 0.12f
        val totalW = BodyShapes.WIDTH * 2 + gap
        val availW = (width - paddingLeft - paddingRight).toFloat()
        val availH = (height - paddingTop - paddingBottom) - captionH
        if (availW <= 0 || availH <= 0) return
        val scale = minOf(availW / totalW, availH / BodyShapes.HEIGHT)
        val offX = paddingLeft + (availW - totalW * scale) / 2
        val offY = paddingTop.toFloat()

        listOf(0f to Shapes.front, BodyShapes.WIDTH + gap to Shapes.back).forEachIndexed { i, (dx, muscles) ->
            canvas.save()
            canvas.translate(offX + dx * scale, offY)
            canvas.scale(scale, scale)
            paint.color = baseColor
            Shapes.base.forEach { canvas.drawPath(it, paint) }
            muscles.forEach { (m, paths) ->
                paint.color = colorOf(m)
                paths.forEach { canvas.drawPath(it, paint) }
            }
            canvas.restore()
            if (showCaptions) {
                val cx = offX + (dx + BodyShapes.WIDTH / 2) * scale
                canvas.drawText(if (i == 0) "zepředu" else "zezadu", cx, offY + BodyShapes.HEIGHT * scale + captionH * 0.8f, captionPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    /** Cesty se parsují jednou pro celou aplikaci; obě poloviny (levá + zrcadlená). */
    private object Shapes {
        private val mirror = Matrix().apply { setScale(-1f, 1f); postTranslate(BodyShapes.WIDTH, 0f) }

        private fun both(d: String): List<Path> {
            val left = PathParser.createPathFromPathData(d)
            val right = Path(left).apply { transform(mirror) }
            return listOf(left, right)
        }

        val base: List<Path> = both(BodyShapes.SILHOUETTE_HALF) +
            PathParser.createPathFromPathData(BodyShapes.HEAD) +
            PathParser.createPathFromPathData(BodyShapes.NECK)

        val front: Map<Muscle, List<Path>> = BodyShapes.FRONT.mapValues { (_, ds) -> ds.flatMap(::both) }
        val back: Map<Muscle, List<Path>> = BodyShapes.BACK.mapValues { (_, ds) -> ds.flatMap(::both) }
    }
}
