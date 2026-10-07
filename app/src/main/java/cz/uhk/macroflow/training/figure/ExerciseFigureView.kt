package cz.uhk.macroflow.training.figure

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import cz.uhk.macroflow.training.body.Muscle
import cz.uhk.macroflow.training.figure.ExerciseFigures.Ink
import cz.uhk.macroflow.training.figure.ExerciseFigures.Joints
import cz.uhk.macroflow.training.figure.ExerciseFigures.Layer
import cz.uhk.macroflow.training.figure.ExerciseFigures.P
import cz.uhk.macroflow.training.figure.ExerciseFigures.Prop

/**
 * Výchozí a koncová poloha cviku vedle sebe (docs/adr/0066): beztvářná postava z boku, stroj
 * a zvýrazněné pracující svaly (hlavní tmavě oranžově, pomocné světleji). Čistě Canvas, bez animace.
 */
class ExerciseFigureView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private val cOutline = Color.parseColor("#283618")
    private val cSkin = Color.parseColor("#DCD3B4")
    private val cSkinFar = Color.parseColor("#B9AF8F")
    private val cPrimary = Color.parseColor("#BC6C25")
    private val cSecondary = Color.parseColor("#E9B072")
    private val ink = mapOf(
        Ink.FRAME to Color.parseColor("#283618"),
        Ink.STEEL to Color.parseColor("#9EA294"),
        Ink.CUSHION to Color.parseColor("#606C38"),
        Ink.PLATE to Color.parseColor("#283618")
    )

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cOutline; alpha = 150; textAlign = Paint.Align.CENTER; letterSpacing = 0.14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val path = Path()

    private var ill: ExerciseFigures.Illustration? = null
    private var primary: Set<Muscle> = emptySet()
    private var secondary: Set<Muscle> = emptySet()

    fun set(illustration: ExerciseFigures.Illustration, primary: Set<Muscle>, secondary: Set<Muscle>) {
        ill = illustration; this.primary = primary; this.secondary = secondary
        contentDescription = "Ilustrace cviku: výchozí a koncová poloha"
        invalidate()
    }

    // převod jednotek na pixely pro právě kreslený snímek
    private var s = 1f; private var ox = 0f; private var oy = 0f; private var minX = 0f; private var maxY = 0f
    private fun X(p: P) = ox + (p.x - minX) * s
    private fun Y(p: P) = oy + (maxY - p.y) * s

    override fun onDraw(canvas: Canvas) {
        val il = ill ?: return
        val a = ExerciseFigures.solve(il.start)
        val b = ExerciseFigures.solve(il.end)
        val pts = listOf(a, b).flatMap { listOf(it.pelvis, it.shoulder, it.elbow, it.wrist, it.knee, it.ankle, it.toe) } +
            listOf(a.head, b.head).flatMap { listOf(it + P(-7f, -7f), it + P(7f, 7f)) } +
            il.props.flatMap { pr -> when (pr) {
                is Prop.Bar -> listOf(pr.a, pr.b)
                is Prop.Disc -> listOf(pr.c + P(-pr.r, -pr.r), pr.c + P(pr.r, pr.r))
                is Prop.ToHand -> listOf(pr.from)
                is Prop.HandDisc -> listOf(a.wrist, b.wrist).flatMap { listOf(it + P(-pr.r, -pr.r), it + P(pr.r, pr.r)) }
            } }
        val bx0 = pts.minOf { it.x } - 3; val bx1 = pts.maxOf { it.x } + 3
        val by0 = pts.minOf { it.y } - 3; val by1 = pts.maxOf { it.y } + 3
        val labelH = height * 0.12f
        val gap = width * 0.04f
        val frameW = (width - gap) / 2f
        s = minOf(frameW / (bx1 - bx0), (height - labelH) / (by1 - by0))
        minX = bx0; maxY = by1
        label.textSize = labelH * 0.55f

        listOf(a to "ZAČÁTEK", b to "KONEC").forEachIndexed { i, (j, text) ->
            val left = i * (frameW + gap)
            ox = left + (frameW - (bx1 - bx0) * s) / 2f
            oy = (height - labelH - (by1 - by0) * s) / 2f
            drawFrame(canvas, il, j)
            canvas.drawText(text, left + frameW / 2f, height - labelH * 0.25f, label)
        }
    }

    private fun drawFrame(c: Canvas, il: ExerciseFigures.Illustration, j: Joints) {
        val ol = 1.1f * s
        il.props.filter { it.layer == Layer.BACK }.forEach { prop(c, it, j) }
        // vzdálenější končetiny tmavší a trochu posunuté, ať je vidět hloubka
        val far = P(-1.2f, 1.2f)
        limb(c, j.pelvis + far, j.knee + far, 7.5f, cSkinFar, ol); limb(c, j.knee + far, j.ankle + far, 5.5f, cSkinFar, ol)
        limb(c, j.ankle + far, j.toe + far, 3f, cSkinFar, ol)
        limb(c, j.shoulder + far, j.elbow + far, 5f, cSkinFar, ol); limb(c, j.elbow + far, j.wrist + far, 4.2f, cSkinFar, ol)
        torso(c, j, ol)
        // hlava a krk
        limb(c, j.shoulder, j.head, 4f, cSkin, ol)
        fill.color = cOutline; c.drawCircle(X(j.head), Y(j.head), (ExerciseFigures.HEAD_R + 1.1f) * s, fill)
        fill.color = cSkin; c.drawCircle(X(j.head), Y(j.head), ExerciseFigures.HEAD_R * s, fill)
        il.props.filter { it.layer == Layer.MID }.forEach { prop(c, it, j) }
        // bližší noha
        limb(c, j.pelvis, j.knee, 8f, cSkin, ol, hl(Muscle.QUADS, Muscle.HAMSTRINGS))
        limb(c, j.knee, j.ankle, 6f, cSkin, ol, hl(Muscle.CALVES))
        limb(c, j.ankle, j.toe, 3.2f, cSkin, ol)
        // bližší paže
        limb(c, j.shoulder, j.elbow, 5.6f, cSkin, ol, hl(Muscle.TRICEPS, Muscle.BICEPS))
        hl(Muscle.FRONT_DELTS, Muscle.REAR_DELTS)?.let { fill.color = it; c.drawCircle(X(j.shoulder), Y(j.shoulder), 3.6f * s, fill) }
        limb(c, j.elbow, j.wrist, 4.6f, cSkin, ol, hl(Muscle.FOREARMS))
        fill.color = cOutline; c.drawCircle(X(j.wrist), Y(j.wrist), 3.4f * s, fill)
        fill.color = cSkin; c.drawCircle(X(j.wrist), Y(j.wrist), 2.4f * s, fill)
        il.props.filter { it.layer == Layer.FRONT }.forEach { prop(c, it, j) }
    }

    /** Barva zvýraznění partie: hlavní > pomocná > nic. */
    private fun hl(vararg m: Muscle): Int? = when {
        m.any { it in primary } -> cPrimary
        m.any { it in secondary } -> cSecondary
        else -> null
    }

    private fun limb(c: Canvas, a: P, b: P, w: Float, color: Int, ol: Float, highlight: Int? = null) {
        line.color = cOutline; line.strokeWidth = w * s + 2 * ol
        c.drawLine(X(a), Y(a), X(b), Y(b), line)
        line.color = color; line.strokeWidth = w * s
        c.drawLine(X(a), Y(a), X(b), Y(b), line)
        if (highlight != null) {
            // sval jako vřeteno uprostřed článku
            val m1 = a + (b - a) * 0.18f; val m2 = a + (b - a) * 0.82f
            line.color = highlight; line.strokeWidth = w * s * 0.72f
            c.drawLine(X(m1), Y(m1), X(m2), Y(m2), line)
        }
    }

    private fun torso(c: Canvas, j: Joints, ol: Float) {
        // bod na trupu: t = 0 pánev … 1 ramena, side = −1 záda … +1 hrudník
        fun at(t: Float, side: Float) = j.pelvis + (j.shoulder - j.pelvis) * t + j.front * (side * (6f + 1.8f * t))
        fun poly(t0: Float, t1: Float, s0: Float, s1: Float) {
            path.reset()
            val n = 6
            for (k in 0..n) { val q = at(t0 + (t1 - t0) * k / n, s1); if (k == 0) path.moveTo(X(q), Y(q)) else path.lineTo(X(q), Y(q)) }
            for (k in n downTo 0) { val q = at(t0 + (t1 - t0) * k / n, s0); path.lineTo(X(q), Y(q)) }
            path.close()
        }
        poly(-0.08f, 1.04f, -1f, 1f)
        line.color = cOutline; line.strokeWidth = 2 * ol
        c.drawPath(path, line)
        fill.color = cSkin; c.drawPath(path, fill)
        fun region(m: Muscle, t0: Float, t1: Float, s0: Float, s1: Float) {
            val col = hl(m) ?: return
            poly(t0, t1, s0, s1); fill.color = col; c.drawPath(path, fill)
        }
        region(Muscle.CHEST, 0.58f, 0.95f, 0.05f, 0.92f)
        region(Muscle.ABS, 0.12f, 0.55f, 0.1f, 0.85f)
        region(Muscle.LATS, 0.38f, 0.88f, -0.92f, -0.1f)
        region(Muscle.TRAPS, 0.86f, 1.02f, -0.9f, -0.05f)
        region(Muscle.LOWER_BACK, 0.05f, 0.36f, -0.85f, -0.15f)
        region(Muscle.GLUTES, -0.06f, 0.16f, -0.95f, -0.2f)
    }

    private fun prop(c: Canvas, pr: Prop, j: Joints) {
        when (pr) {
            is Prop.Bar -> { line.color = ink.getValue(pr.ink); line.strokeWidth = pr.w * s; c.drawLine(X(pr.a), Y(pr.a), X(pr.b), Y(pr.b), line) }
            is Prop.Disc -> { fill.color = ink.getValue(pr.ink); c.drawCircle(X(pr.c), Y(pr.c), pr.r * s, fill) }
            is Prop.ToHand -> { line.color = ink.getValue(pr.ink); line.strokeWidth = pr.w * s; c.drawLine(X(pr.from), Y(pr.from), X(j.wrist), Y(j.wrist), line) }
            is Prop.HandDisc -> {
                fill.color = ink.getValue(pr.ink); c.drawCircle(X(j.wrist), Y(j.wrist), pr.r * s, fill)
                if (pr.ink == Ink.PLATE) {
                    line.color = ink.getValue(Ink.STEEL); line.strokeWidth = 0.8f * s
                    c.drawCircle(X(j.wrist), Y(j.wrist), pr.r * 0.62f * s, line)
                }
            }
        }
    }
}
