package cz.uhk.macroflow.training.body

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.PathParser

/**
 * Postava zepředu a zezadu (nebo jen jedna velká, viz [sides]); partie se podbarví podle
 * intenzity 0..1 (0 = nezapojená, 0,5 = vedlejší / 1× týdně, 1 = hlavní / 2× týdně).
 * Změna se plynule přebarví, takže výběr PUSH/PULL/LEGS „rozsvítí“ tělo.
 *
 * V atlasu svalů se navíc dá na partii klepnout ([onMuscleTap]); vybraná partie ([selected])
 * se rozsvítí akcentovou barvou a dostane obrys. Rozložení a zásah řeší čistý [BodyLayout]
 * a [MuscleHit] (pokryté testy), view jen kreslí a staví Regiony z cest.
 */
class BodyMapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var baseColor = Color.parseColor("#E4DEC0")
    var idleMuscleColor = Color.parseColor("#CFC8A6")
    var selectedColor = Color.parseColor("#BC6C25")
    var outlineColor = Color.parseColor("#283618")
    /** Popisky „zepředu / zezadu“ pod postavami (u malých postav v kartě dne vypnuté). */
    var showCaptions = true
    /** Které pohledy kreslit – výchozí oba vedle sebe, v atlasu jen jeden velký. */
    var sides: List<BodySide> = listOf(BodySide.FRONT, BodySide.BACK)
        set(value) { field = value; layout = null; invalidate() }
    /** Barva popisků pod postavami (na tmavé kartě světlá). */
    var captionColor: Int
        get() = captionPaint.color
        set(value) { captionPaint.color = value; invalidate() }
    /** Obrys siluety (atlas: „outline těla“). */
    var showSilhouetteOutline = false
        set(value) { field = value; invalidate() }

    /** Klepnutí na partii; když je null, view se chová jako obyčejné (klik jde do rodiče / listeneru). */
    var onMuscleTap: ((Muscle) -> Unit)? = null

    var selected: Muscle? = null
        private set

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80283618"); textAlign = Paint.Align.CENTER
        textSize = 10f * density
    }

    private var from: Map<Muscle, Int> = emptyMap()
    private var target: Map<Muscle, Int> = emptyMap()
    private var progress = 1f
    private var animator: ValueAnimator? = null

    /** 0..1 – rozsvícení vybrané partie (animuje se při výběru). */
    private var glow = 1f
    private var glowAnimator: ValueAnimator? = null

    private var layout: BodyLayout? = null

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

    /** Vybere (rozsvítí) partii; null = bez výběru. */
    fun select(muscle: Muscle?, animate: Boolean = true) {
        if (muscle == selected) return
        selected = muscle
        glowAnimator?.cancel()
        if (muscle == null || !animate || !isLaidOut) { glow = 1f; invalidate(); return }
        glow = 0f
        glowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            interpolator = DecelerateInterpolator()
            addUpdateListener { glow = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** Je partie vidět na některém z kreslených pohledů? */
    fun isVisible(m: Muscle): Boolean = sides.any { Shapes.of(it).containsKey(m) }

    private fun current(): Map<Muscle, Int> = Muscle.entries.associateWith { colorOf(it) }

    private fun colorOf(m: Muscle): Int {
        val t = target[m] ?: idleMuscleColor
        val f = from[m] ?: idleMuscleColor
        return if (progress >= 1f) t else ColorUtils.blendARGB(f, t, progress)
    }

    private fun captionHeight() = if (showCaptions) captionPaint.textSize * 1.6f else 0f

    private fun layoutOrNull(): BodyLayout? {
        layout?.let { return it }
        return BodyLayout.compute(sides, width, height, paddingLeft, paddingTop, paddingRight, paddingBottom, captionHeight())
            .also { layout = it }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layout = null
    }

    override fun onDraw(canvas: Canvas) {
        val l = layoutOrNull() ?: return
        val captionH = captionHeight()
        l.sides.forEachIndexed { i, side ->
            val muscles = Shapes.of(side)
            canvas.save()
            canvas.translate(l.originX(i), l.offY)
            canvas.scale(l.scale, l.scale)
            paint.color = baseColor
            Shapes.base.forEach { canvas.drawPath(it, paint) }
            if (showSilhouetteOutline) {
                strokePaint.color = ColorUtils.setAlphaComponent(outlineColor, 0x40)
                strokePaint.strokeWidth = 1.2f * density / l.scale
                Shapes.base.forEach { canvas.drawPath(it, strokePaint) }
            }
            val sel = selected
            muscles.forEach { (m, paths) ->
                paint.color = if (m == sel) ColorUtils.blendARGB(colorOf(m), selectedColor, glow) else colorOf(m)
                paths.forEach { canvas.drawPath(it, paint) }
            }
            // Obrys vybrané partie navrch, ať je vidět i přes sousední svaly.
            sel?.let { muscles[it] }?.let { paths ->
                strokePaint.color = ColorUtils.setAlphaComponent(outlineColor, (0xD0 * glow).toInt())
                strokePaint.strokeWidth = 1.8f * density / l.scale
                paths.forEach { canvas.drawPath(it, strokePaint) }
            }
            canvas.restore()
            if (showCaptions) {
                val cx = l.originX(i) + BodyShapes.WIDTH / 2 * l.scale
                canvas.drawText(side.label, cx, l.offY + BodyShapes.HEIGHT * l.scale + captionH * 0.8f, captionPaint)
            }
        }
    }

    // ── Klepnutí na partii ──────────────────────────────────────────────────

    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onMuscleTap == null) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; return true }
            MotionEvent.ACTION_UP -> {
                val slop = 12 * density
                if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) return true
                val hit = muscleAt(event.x, event.y)
                if (hit != null) {
                    select(hit)
                    onMuscleTap?.invoke(hit)
                } else performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** Partie pod prstem (s tolerancí ~14 dp), nebo null. */
    fun muscleAt(x: Float, y: Float): Muscle? {
        val l = layoutOrNull() ?: return null
        val (side, bx, by) = l.toBody(x, y) ?: return null
        val regions = Shapes.regions(side)
        val order = regions.map { it.first }
        val bySide = regions.toMap()
        val radius = (14 * density / l.scale).coerceAtMost(8f)
        return MuscleHit.pick(bx, by, radius, order) { m, px, py ->
            bySide[m]?.contains((px * Shapes.REGION_SCALE).toInt(), (py * Shapes.REGION_SCALE).toInt()) == true
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        glowAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    /** Cesty se parsují jednou pro celou aplikaci; obě poloviny (levá + zrcadlená). */
    private object Shapes {
        /** Regiony jsou celočíselné – souřadnice těla se násobí, aby měl zásah jemnost 0,25 jednotky. */
        const val REGION_SCALE = 4f

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

        fun of(side: BodySide) = if (side == BodySide.FRONT) front else back

        private val regionCache = HashMap<BodySide, List<Pair<Muscle, Region>>>()

        /** Regiony partií v pořadí kreslení (poslední je navrchu). */
        fun regions(side: BodySide): List<Pair<Muscle, Region>> = regionCache.getOrPut(side) {
            val scale = Matrix().apply { setScale(REGION_SCALE, REGION_SCALE) }
            val clip = Region(0, 0, (BodyShapes.WIDTH * REGION_SCALE).toInt() + 1, (BodyShapes.HEIGHT * REGION_SCALE).toInt() + 1)
            of(side).map { (m, paths) ->
                val union = Region()
                paths.forEach { p ->
                    val scaled = Path(p).apply { transform(scale) }
                    val r = Region().apply { setPath(scaled, clip) }
                    union.op(r, Region.Op.UNION)
                }
                m to union
            }
        }
    }
}
