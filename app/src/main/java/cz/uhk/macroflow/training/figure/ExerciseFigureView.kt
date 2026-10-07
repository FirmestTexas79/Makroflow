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
 * Výchozí a koncová poloha cviku vedle sebe (docs/adr/0066): beztvářná postava, stroj a zvýrazněné
 * pracující svaly (hlavní tmavě oranžově, pomocné světleji). Výdrž (plank) = jeden snímek.
 * Čistě Canvas, bez animace. Pořadí kreslení odpovídá náhledovému skriptu, ze kterého jsou data.
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
        Ink.PLATE to Color.parseColor("#283618"),
        Ink.SKIN to cSkin
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
        contentDescription = if (illustration.end == null) "Ilustrace cviku: výdrž" else "Ilustrace cviku: výchozí a koncová poloha"
        invalidate()
    }

    // převod jednotek na pixely pro právě kreslený snímek
    private var s = 1f; private var ox = 0f; private var oy = 0f; private var minX = 0f; private var maxY = 0f
    private var frame = 0
    private fun X(p: P) = ox + (p.x - minX) * s
    private fun Y(p: P) = oy + (maxY - p.y) * s

    private fun propPoints(pr: Prop, js: List<Joints>): List<P> = when (pr) {
        is Prop.Only -> propPoints(pr.prop, js)
        is Prop.Bar -> listOf(pr.a, pr.b)
        is Prop.Disc -> listOf(pr.c + P(-pr.r, -pr.r), pr.c + P(pr.r, pr.r))
        is Prop.ToJoint -> listOf(pr.from)
        is Prop.At -> js.flatMap { val c = ExerciseFigures.joint(it, pr.joint); listOf(c + P(-pr.r, -pr.r), c + P(pr.r, pr.r)) }
        is Prop.Pad -> js.flatMap { val c = ExerciseFigures.joint(it, pr.joint); listOf(c + pr.d1, c + pr.d2) }
    }

    override fun onDraw(canvas: Canvas) {
        val il = ill ?: return
        val js = listOfNotNull(il.start, il.end).map(ExerciseFigures::solve)
        val pts = js.flatMap {
            listOf(it.pelvis, it.shoulder, it.elbow, it.wrist, it.knee, it.ankle, it.toe, it.elbow2, it.wrist2, it.knee2,
                it.ankle2, it.toe2, it.shoulderR, it.shoulderL, it.hipR, it.hipL, it.head + P(-7f, -7f), it.head + P(7f, 7f))
        } + il.props.flatMap { propPoints(it, js) }
        val bx0 = pts.minOf { it.x } - 3; val bx1 = pts.maxOf { it.x } + 3
        val by0 = pts.minOf { it.y } - 3; val by1 = pts.maxOf { it.y } + 3
        val labelH = height * 0.12f
        val gap = width * 0.04f
        val frameW = (width - gap) / 2f
        s = minOf(frameW / (bx1 - bx0), (height - labelH) / (by1 - by0))
        minX = bx0; maxY = by1
        label.textSize = labelH * 0.55f
        val single = js.size == 1
        val labels = if (single) listOf("VÝDRŽ") else listOf("ZAČÁTEK", "KONEC")

        js.forEachIndexed { i, j ->
            frame = i
            val left = if (single) (width - frameW) / 2f else i * (frameW + gap)
            ox = left + (frameW - (bx1 - bx0) * s) / 2f
            oy = (height - labelH - (by1 - by0) * s) / 2f
            if (j.view == ExerciseFigures.View.SIDE) drawSide(canvas, il, j) else drawFront(canvas, il, j)
            canvas.drawText(labels[i], left + frameW / 2f, height - labelH * 0.25f, label)
        }
    }

    private fun props(c: Canvas, il: ExerciseFigures.Illustration, j: Joints, layer: Layer) =
        il.props.forEach { if (it.layer == layer) prop(c, it, j) }

    private fun drawSide(c: Canvas, il: ExerciseFigures.Illustration, j: Joints) {
        val ol = 1.1f * s
        props(c, il, j, Layer.BACK)
        // vzdálenější končetiny tmavší a trochu posunuté, ať je vidět hloubka
        val far = P(-1.2f, 1.2f)
        limb(c, j.pelvis + far, j.knee2 + far, 7.5f, cSkinFar, ol); limb(c, j.knee2 + far, j.ankle2 + far, 5.5f, cSkinFar, ol)
        limb(c, j.ankle2 + far, j.toe2 + far, 3f, cSkinFar, ol)
        limb(c, j.shoulder + far, j.elbow2 + far, 5f, cSkinFar, ol); limb(c, j.elbow2 + far, j.wrist2 + far, 4.2f, cSkinFar, ol)
        hand(c, j.wrist2 + far, cSkinFar)
        torsoSide(c, j, ol)
        head(c, j, ol)
        props(c, il, j, Layer.MID)
        limb(c, j.pelvis, j.knee, 8f, cSkin, ol, hl(Muscle.QUADS, Muscle.HAMSTRINGS))
        limb(c, j.knee, j.ankle, 6f, cSkin, ol, hl(Muscle.CALVES))
        limb(c, j.ankle, j.toe, 3.2f, cSkin, ol)
        limb(c, j.shoulder, j.elbow, 5.6f, cSkin, ol, hl(Muscle.TRICEPS, Muscle.BICEPS))
        hl(Muscle.FRONT_DELTS, Muscle.REAR_DELTS)?.let { fill.color = it; c.drawCircle(X(j.shoulder), Y(j.shoulder), 3.6f * s, fill) }
        limb(c, j.elbow, j.wrist, 4.6f, cSkin, ol, hl(Muscle.FOREARMS))
        hand(c, j.wrist, cSkin)
        props(c, il, j, Layer.FRONT)
    }

    private fun drawFront(c: Canvas, il: ExerciseFigures.Illustration, j: Joints) {
        val ol = 1.1f * s
        props(c, il, j, Layer.BACK)
        listOf(Triple(j.hipR, j.knee, j.ankle) to j.toe, Triple(j.hipL, j.knee2, j.ankle2) to j.toe2).forEach { (leg, toe) ->
            limb(c, leg.first, leg.second, 8f, cSkin, ol, hl(Muscle.QUADS, Muscle.HAMSTRINGS))
            limb(c, leg.second, leg.third, 6f, cSkin, ol, hl(Muscle.CALVES))
            limb(c, leg.third, toe, 3.2f, cSkin, ol)
        }
        torsoFront(c, j, ol)
        hl(Muscle.GLUTES)?.let { fill.color = it; c.drawCircle(X(j.hipR), Y(j.hipR), 3.8f * s, fill); c.drawCircle(X(j.hipL), Y(j.hipL), 3.8f * s, fill) }
        head(c, j, ol)
        props(c, il, j, Layer.MID)
        listOf(Triple(j.shoulderR, j.elbow, j.wrist), Triple(j.shoulderL, j.elbow2, j.wrist2)).forEach { (sh, el, wr) ->
            limb(c, sh, el, 5.6f, cSkin, ol, hl(Muscle.TRICEPS, Muscle.BICEPS))
            hl(Muscle.FRONT_DELTS, Muscle.REAR_DELTS)?.let { fill.color = it; c.drawCircle(X(sh), Y(sh), 3.8f * s, fill) }
            limb(c, el, wr, 4.6f, cSkin, ol, hl(Muscle.FOREARMS))
            hand(c, wr, cSkin)
        }
        props(c, il, j, Layer.FRONT)
    }

    /** Barva zvýraznění partie: hlavní > pomocná > nic. */
    private fun hl(vararg m: Muscle): Int? = when {
        m.any { it in primary } -> cPrimary
        m.any { it in secondary } -> cSecondary
        else -> null
    }

    private fun head(c: Canvas, j: Joints, ol: Float) {
        limb(c, j.shoulder, j.head, 4f, cSkin, ol)
        fill.color = cOutline; c.drawCircle(X(j.head), Y(j.head), (ExerciseFigures.HEAD_R + 1.1f) * s, fill)
        fill.color = cSkin; c.drawCircle(X(j.head), Y(j.head), ExerciseFigures.HEAD_R * s, fill)
    }

    private fun hand(c: Canvas, w: P, color: Int) {
        fill.color = cOutline; c.drawCircle(X(w), Y(w), 3.4f * s, fill)
        fill.color = color; c.drawCircle(X(w), Y(w), 2.4f * s, fill)
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

    /** Pás trupu mezi [t0]..[t1] (0 pánev … 1 ramena) a stranami [s0]..[s1]. */
    private fun band(at: (Float, Float) -> P, t0: Float, t1: Float, s0: Float, s1: Float) {
        path.reset()
        val n = 6
        for (k in 0..n) { val q = at(t0 + (t1 - t0) * k / n, s1); if (k == 0) path.moveTo(X(q), Y(q)) else path.lineTo(X(q), Y(q)) }
        for (k in n downTo 0) { val q = at(t0 + (t1 - t0) * k / n, s0); path.lineTo(X(q), Y(q)) }
        path.close()
    }

    private fun torso(c: Canvas, at: (Float, Float) -> P, ol: Float, outline: FloatArray, regions: List<Pair<Muscle, FloatArray>>) {
        band(at, outline[0], outline[1], outline[2], outline[3])
        line.color = cOutline; line.strokeWidth = 2 * ol
        c.drawPath(path, line)
        fill.color = cSkin; c.drawPath(path, fill)
        regions.forEach { (m, r) ->
            val col = hl(m) ?: return@forEach
            band(at, r[0], r[1], r[2], r[3]); fill.color = col; c.drawPath(path, fill)
        }
    }

    private fun torsoSide(c: Canvas, j: Joints, ol: Float) {
        // side = −1 záda … +1 hrudník
        val at = { t: Float, side: Float -> j.pelvis + (j.shoulder - j.pelvis) * t + j.front * (side * (6f + 1.8f * t)) }
        torso(c, at, ol, floatArrayOf(-0.08f, 1.04f, -1f, 1f), listOf(
            Muscle.CHEST to floatArrayOf(0.58f, 0.95f, 0.05f, 0.92f),
            Muscle.ABS to floatArrayOf(0.12f, 0.55f, 0.1f, 0.85f),
            Muscle.OBLIQUES to floatArrayOf(0.1f, 0.5f, -0.3f, 0.4f),
            Muscle.LATS to floatArrayOf(0.38f, 0.88f, -0.92f, -0.1f),
            Muscle.TRAPS to floatArrayOf(0.86f, 1.02f, -0.9f, -0.05f),
            Muscle.LOWER_BACK to floatArrayOf(0.05f, 0.36f, -0.85f, -0.15f),
            Muscle.GLUTES to floatArrayOf(-0.06f, 0.16f, -0.95f, -0.2f)
        ))
    }

    private fun torsoFront(c: Canvas, j: Joints, ol: Float) {
        // u = −1 levý okraj … +1 pravý okraj (z pohledu diváka)
        val hw = ExerciseFigures.HIP_HALF + 1f; val sw = ExerciseFigures.SHOULDER_HALF + 1f
        val at = { t: Float, u: Float -> j.pelvis + (j.shoulder - j.pelvis) * t + j.front * (u * (hw + (sw - hw) * t)) }
        torso(c, at, ol, floatArrayOf(-0.1f, 1.03f, -1f, 1f), listOf(
            Muscle.CHEST to floatArrayOf(0.62f, 0.92f, -0.9f, -0.08f),
            Muscle.CHEST to floatArrayOf(0.62f, 0.92f, 0.08f, 0.9f),
            Muscle.ABS to floatArrayOf(0.12f, 0.58f, -0.35f, 0.35f),
            Muscle.OBLIQUES to floatArrayOf(0.1f, 0.5f, -0.95f, -0.5f),
            Muscle.OBLIQUES to floatArrayOf(0.1f, 0.5f, 0.5f, 0.95f),
            Muscle.LATS to floatArrayOf(0.45f, 0.8f, -1f, -0.75f),
            Muscle.LATS to floatArrayOf(0.45f, 0.8f, 0.75f, 1f),
            Muscle.TRAPS to floatArrayOf(0.9f, 1.03f, -0.55f, 0.55f)
        ))
    }

    private fun prop(c: Canvas, pr: Prop, j: Joints) {
        when (pr) {
            is Prop.Only -> if (pr.frame == frame) prop(c, pr.prop, j)
            is Prop.Bar -> { line.color = ink.getValue(pr.ink); line.strokeWidth = pr.w * s; c.drawLine(X(pr.a), Y(pr.a), X(pr.b), Y(pr.b), line) }
            is Prop.Disc -> { fill.color = ink.getValue(pr.ink); c.drawCircle(X(pr.c), Y(pr.c), pr.r * s, fill) }
            is Prop.ToJoint -> {
                val to = ExerciseFigures.joint(j, pr.joint)
                line.color = ink.getValue(pr.ink); line.strokeWidth = pr.w * s; c.drawLine(X(pr.from), Y(pr.from), X(to), Y(to), line)
            }
            is Prop.Pad -> {
                val o = ExerciseFigures.joint(j, pr.joint); val a = o + pr.d1; val b = o + pr.d2
                line.color = ink.getValue(pr.ink); line.strokeWidth = pr.w * s; c.drawLine(X(a), Y(a), X(b), Y(b), line)
            }
            is Prop.At -> {
                val o = ExerciseFigures.joint(j, pr.joint)
                fill.color = ink.getValue(pr.ink); c.drawCircle(X(o), Y(o), pr.r * s, fill)
                if (pr.ink == Ink.PLATE) {
                    line.color = ink.getValue(Ink.STEEL); line.strokeWidth = 0.8f * s
                    c.drawCircle(X(o), Y(o), pr.r * 0.62f * s, line)
                }
            }
        }
    }
}
