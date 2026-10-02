package cz.uhk.macroflow.pokemon.skills.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.res.ResourcesCompat
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.balls.Makroball
import cz.uhk.macroflow.pokemon.skills.Skill
import cz.uhk.macroflow.pokemon.skills.SkillArt
import cz.uhk.macroflow.pokemon.skills.SkillState
import cz.uhk.macroflow.pokemon.skills.SkillTree
import cz.uhk.macroflow.pokemon.skills.SkillTreeLayout
import cz.uhk.macroflow.pokemon.skills.TreeArt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Grafický strom dovedností (docs/adr/0058): pixelové „drahokamové“ uzly na tmavém pozadí,
 * spoje rodič → dítě jako lomené pixelové čáry. Odemčené uzly a jejich cesta svítí zlatě,
 * uzel, který jde odemknout, pulzuje. Při otevření uzly naskakují po řádcích, po odemčení
 * z uzlu vyletí jiskry.
 */
@SuppressLint("ViewConstructor")
class SkillTreeView(ctx: Context) : View(ctx) {

    var onSelect: (SkillTree.Node) -> Unit = {}

    private val dp = resources.displayMetrics.density
    private val font = ResourcesCompat.getFont(ctx, R.font.jersey_15)
    private val p = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font; textAlign = Paint.Align.CENTER; color = Color.parseColor("#FEFAE0")
        setShadowLayer(3f, 0f, 1.5f, Color.parseColor("#05080A"))
    }

    private var nodes: List<SkillTree.Node> = emptyList()
    private var layout = SkillTreeLayout.Layout(emptyMap(), 1, 1)
    private var state = SkillState()
    var selectedId: String? = null
        set(v) { field = v; invalidate() }

    private val icons = HashMap<String, Bitmap>()

    private val rowH = 138 * dp
    /** Pod uzlem je úroveň a název (až na dva řádky) – spoj začíná až pod nimi. */
    private val labelSpace = 50 * dp
    private val topPad = 26 * dp
    /** Velikost uzlu: 58 dp, u širokých stromů (víc sloupců) menší, aby se vešly názvy. */
    private var node = 58 * dp
    private val u get() = node / 19.33f            // art pixel uzlu

    // animace
    private var appear = 0f
    private var appearAnim: ValueAnimator? = null
    private val started = System.currentTimeMillis()
    private data class Spark(val x: Float, val y: Float, val vx: Float, val vy: Float, val color: Int, val size: Float)
    private var sparks: List<Spark> = emptyList()
    private var burstAt = 0L
    private var burstCenter = 0f to 0f

    fun bind(list: List<SkillTree.Node>, st: SkillState, animate: Boolean) {
        nodes = list
        layout = SkillTreeLayout.of(list)
        state = st
        if (selectedId == null || list.none { it.id == selectedId }) selectedId = list.firstOrNull { st.canUnlock(it) }?.id ?: list.firstOrNull()?.id
        requestLayout()
        if (animate) {
            appearAnim?.cancel()
            appear = 0f
            appearAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260L + 140L * layout.rows
                addUpdateListener { appear = it.animatedValue as Float; invalidate() }
                start()
            }
        } else { appear = 1f; invalidate() }
    }

    fun updateState(st: SkillState) { state = st; invalidate() }

    /** Jiskry a kruh z právě odemčeného uzlu. */
    fun celebrate(id: String) {
        val c = center(id) ?: return
        burstCenter = c
        burstAt = System.currentTimeMillis()
        val rnd = java.util.Random(id.hashCode().toLong() xor burstAt)
        sparks = List(28) {
            val a = rnd.nextFloat() * 2 * PI
            val v = (90 + rnd.nextFloat() * 140) * dp
            val col = listOf("#FFF3B0", "#FFD54F", "#FFFFFF", "#9CC45A")[rnd.nextInt(4)]
            Spark(c.first, c.second, (cos(a) * v).toFloat(), (sin(a) * v).toFloat() - 40 * dp, Color.parseColor(col), (2 + rnd.nextInt(3)) * dp)
        }
        invalidate()
    }

    // ── rozměry ─────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        node = minOf(58 * dp, minOf(w / layout.columns.toFloat(), 150 * dp) * 0.62f)
        val content = contentH().toInt()
        // ScrollView s fillViewport dá nízkému stromu celou výšku desky – strom se pak vycentruje
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) maxOf(content, MeasureSpec.getSize(heightMeasureSpec)) else content
        setMeasuredDimension(w, h)
    }

    private fun contentH(): Float = topPad + layout.rows * rowH - (rowH - node - labelSpace) + 10 * dp

    /** Svislý posun, aby nízký strom stál uprostřed desky. */
    private fun offsetY(): Float = maxOf(0f, (height - contentH()) / 2f)

    private fun colW(): Float = minOf(width / layout.columns.toFloat(), 150 * dp)

    private fun center(id: String): Pair<Float, Float>? {
        val pos = layout.pos[id] ?: return null
        val cw = colW()
        val left = (width - cw * layout.columns) / 2f
        return (left + cw * (pos.col + 0.5f)) to (offsetY() + topPad + pos.row * rowH + node / 2f)
    }

    // ── kreslení ────────────────────────────────────────────────────────────

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, col: Int) { p.color = col; c.drawRect(l, t, r, b, p) }

    override fun onDraw(c: Canvas) {
        val now = System.currentTimeMillis()
        val pulse = ((sin((now - started) / 260.0) + 1) / 2).toFloat()   // 0..1
        drawSpores(c, now)
        layout.links(nodes).forEach { (a, b) -> drawLink(c, a, b, pulse) }
        nodes.forEach { n -> drawNode(c, n, pulse) }
        drawBurst(c, now)
        // pulz a výtrusy běží, dokud je strom na obrazovce
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    /** Pomalu blikající pixelové výtrusy v pozadí. */
    private fun drawSpores(c: Canvas, now: Long) {
        val rnd = java.util.Random(7)
        repeat(46) {
            val x = rnd.nextFloat() * width; val y = rnd.nextFloat() * height
            val phase = rnd.nextFloat() * 6.28f; val s = (if (rnd.nextInt(5) == 0) 3 else 2) * dp
            val a = ((sin(now / 900.0 + phase) + 1) / 2 * 120 + 20).toInt()
            rect(c, x, y, x + s, y + s, Color.argb(a, 190, 230, 150))
        }
    }

    private fun scaleFor(row: Int): Float {
        val t = (appear * (layout.rows + 1) - row).coerceIn(0f, 1f)
        return if (t >= 1f) 1f else OvershootInterpolator(2.2f).getInterpolation(t)
    }

    private fun drawLink(c: Canvas, from: String, to: String, pulse: Float) {
        val a = center(from) ?: return; val b = center(to) ?: return
        val toRow = layout.pos[to]?.row ?: 0
        val vis = ((appear * (layout.rows + 1) - toRow + 0.3f)).coerceIn(0f, 1f)
        if (vis <= 0f) return
        val fromNode = SkillTree.node(from); val toNode = SkillTree.node(to)
        // cesta je „průchozí“, když má předchozí uzel potřebnou úroveň
        val through = toNode != null && fromNode != null && state.rank(fromNode.id) >= state.needed(toNode)
        val bothOn = through && toNode != null && state.rank(toNode.id) > 0
        val open = through && toNode != null && state.canUnlock(toNode)
        val inner = when {
            bothOn -> Color.parseColor("#FFD54F")
            open -> blend(Color.parseColor("#8A6A3A"), Color.parseColor("#FFF3B0"), pulse)
            through -> Color.parseColor("#8A6A3A")
            else -> Color.parseColor("#3A3328")
        }
        val startY = a.second + node / 2f + labelSpace
        val endY = b.second - node / 2f - 4 * dp
        val midY = startY + (endY - startY) / 2f
        val w = 3 * dp; val o = 5 * dp
        fun seg(x0: Float, y0: Float, x1: Float, y1: Float) {
            val l = minOf(x0, x1); val r = maxOf(x0, x1); val t = minOf(y0, y1); val bt = maxOf(y0, y1)
            rect(c, l - o / 2 - w / 2, t - o / 2 - w / 2, r + o / 2 + w / 2, bt + o / 2 + w / 2, Color.parseColor("#0B0805"))
        }
        fun segIn(x0: Float, y0: Float, x1: Float, y1: Float) {
            val l = minOf(x0, x1); val r = maxOf(x0, x1); val t = minOf(y0, y1); val bt = maxOf(y0, y1)
            rect(c, l - w / 2, t - w / 2, r + w / 2, bt + w / 2, inner)
        }
        val y0 = startY
        val y1 = y0 + (endY - y0) * vis
        // lomená čára: dolů, do strany, dolů
        val pts = if (abs(a.first - b.first) < 1f) listOf(a.first to y0, b.first to y1)
                  else if (y1 < midY) listOf(a.first to y0, a.first to y1)
                  else listOf(a.first to y0, a.first to midY, b.first to midY, b.first to y1)
        for (i in 0 until pts.size - 1) seg(pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second)
        for (i in 0 until pts.size - 1) segIn(pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second)
        if (bothOn) {   // jiskra, která běží po zlaté cestě
            val t = ((System.currentTimeMillis() - started) % 1600L) / 1600f
            val total = pts.zipWithNext().sumOf { (p0, p1) -> (abs(p1.first - p0.first) + abs(p1.second - p0.second)).toDouble() }.toFloat()
            var d = t * total
            for ((p0, p1) in pts.zipWithNext()) {
                val len = abs(p1.first - p0.first) + abs(p1.second - p0.second)
                if (d <= len && len > 0f) {
                    val f = d / len
                    val x = p0.first + (p1.first - p0.first) * f; val y = p0.second + (p1.second - p0.second) * f
                    rect(c, x - w, y - w, x + w, y + w, Color.WHITE); break
                }
                d -= len
            }
        }
    }

    private fun drawNode(c: Canvas, n: SkillTree.Node, pulse: Float) {
        val (cx, cy) = center(n.id) ?: return
        val row = layout.pos[n.id]?.row ?: 0
        val s = scaleFor(row)
        if (s <= 0.01f) return
        val st = state.status(n)
        c.save()
        c.scale(s, s, cx, cy)
        val half = node / 2f
        val l = cx - half; val t = cy - half; val r = cx + half; val b = cy + half

        val rank = state.rank(n.id).coerceAtMost(n.maxRank)
        val S = SkillState.NodeStatus

        // záře za uzlem
        val glow = when (st) {
            S.MAXED -> Color.argb(70, 255, 213, 79)
            S.AVAILABLE -> Color.argb((60 + 110 * pulse).toInt(), 255, 236, 160)
            else -> 0
        }
        if (glow != 0) {
            val g = (if (st == S.AVAILABLE) 8 + 5 * pulse else 7f) * dp * (node / (58 * dp))
            rect(c, l - g + u, t - g, r + g - u, b + g, glow)
            rect(c, l - g, t - g + u, r + g, b + g - u, glow)
        }

        // barvy: zelená = má úrovně (zlatý obrys, když je na maximu), dřevo = jde vylepšit,
        // šedomodrá = chybí body nebo level, tmavá = zamčeno
        val green = listOf("#3E6B3A", "#7FB069", "#24401F")
        val (base, light, dark, outline) = when {
            st == S.MAXED -> green + "#FFD54F"
            rank > 0 && st == S.AVAILABLE -> green + (if (pulse > 0.5f) "#FFF3B0" else "#FFD54F")
            rank > 0 -> green + "#0B0E14"
            st == S.AVAILABLE -> listOf("#7A5428", "#C48A4A", "#4F3016", if (pulse > 0.5f) "#FFF3B0" else "#FFD54F")
            st == S.LOCKED -> listOf("#25272C", "#34373E", "#17181C", "#0B0C0F")
            else -> listOf("#3A4150", "#5C6578", "#232833", "#0B0E14")
        }.map { Color.parseColor(it) }
        // osmiúhelník po art pixelech: obrys, vnitřní rámeček, vybroušená plocha
        rect(c, l + 2 * u, t, r - 2 * u, b, outline)
        rect(c, l + u, t + u, r - u, b - u, outline)
        rect(c, l, t + 2 * u, r, b - 2 * u, outline)
        val i = u
        rect(c, l + i + 2 * u, t + i, r - i - 2 * u, b - i, dark)
        rect(c, l + i + u, t + i + u, r - i - u, b - i - u, dark)
        rect(c, l + i, t + i + 2 * u, r - i, b - i - 2 * u, dark)
        rect(c, l + 2 * i + u, t + 2 * i, r - 2 * i - u, b - 2 * i - u, base)
        rect(c, l + 2 * i, t + 2 * i + u, r - 2 * i, b - 2 * i - u, base)
        rect(c, l + 2 * i + u, t + 2 * i, r - 2 * i - u, t + 2 * i + u, light)       // odlesk nahoře
        rect(c, l + 2 * i, t + 2 * i + u, l + 2 * i + u, cy, light)                  // odlesk vlevo
        rect(c, l + 3 * u, t + 3 * u, l + 4 * u, t + 4 * u, Color.argb(160, 255, 255, 255))
        // vrcholný uzel: zlatá korunka nad uzlem
        if (n.effect is SkillTree.Effect.Many) {
            val cw = 3 * u; val ch = 2 * u
            rect(c, cx - 2 * cw, t - ch - u, cx + 2 * cw, t - u, Color.parseColor("#0B0805"))
            rect(c, cx - 2 * cw + u, t - ch, cx + 2 * cw - u, t - u, Color.parseColor("#FFD54F"))
            for (k in -1..1) rect(c, cx + k * cw * 1.5f - u, t - ch - 2 * u, cx + k * cw * 1.5f + u, t - ch, Color.parseColor("#FFD54F"))
        }

        // ikona efektu
        val bmp = icon(n)
        val box = node * 0.52f
        p.alpha = when (st) { S.LOCKED -> 90; S.NO_POINTS, S.LOW_LEVEL -> if (rank > 0) 255 else 170; else -> 255 }
        val ratio = bmp.height.toFloat() / bmp.width
        val iw = if (ratio <= 1f) box else box / ratio; val ih = iw * ratio
        c.drawBitmap(bmp, null, RectF(cx - iw / 2, cy - ih / 2, cx + iw / 2, cy + ih / 2), p)
        p.alpha = 255
        if (st == S.LOCKED || (st == S.LOW_LEVEL && rank == 0)) {
            val lk = icon("lock") { TreeArt.LOCK }
            val ls = node * 0.31f
            c.drawBitmap(lk, null, RectF(r - ls + 2 * dp, b - ls + 2 * dp, r + 2 * dp, b + 2 * dp), p)
        }

        // výběr: bílé rohy kolem uzlu
        if (n.id == selectedId) {
            val m = 6 * dp + u * pulse; val k = 9 * dp; val w = 3 * dp
            val col = Color.WHITE
            listOf(l - m to t - m, r + m to t - m, l - m to b + m, r + m to b + m).forEachIndexed { idx, (x, y) ->
                val sx = if (idx % 2 == 0) 1 else -1; val sy = if (idx < 2) 1 else -1
                rect(c, minOf(x, x + sx * k), minOf(y, y + sy * w), maxOf(x, x + sx * k), maxOf(y, y + sy * w), col)
                rect(c, minOf(x, x + sx * w), minOf(y, y + sy * k), maxOf(x, x + sx * w), maxOf(y, y + sy * k), col)
            }
        }

        // úroveň: dílky pod uzlem (zlaté = koupené), u jednorázových uzlů jen jeden
        val pips = n.maxRank
        val pip = 5 * dp; val gap = 2 * dp
        val totalW = pips * pip + (pips - 1) * gap
        var px = cx - totalW / 2
        val py = b + 5 * dp
        repeat(pips) { k ->
            rect(c, px - 1 * dp, py - 1 * dp, px + pip + 1 * dp, py + pip + 1 * dp, Color.parseColor("#0B0805"))
            rect(c, px, py, px + pip, py + pip, if (k < rank) Color.parseColor("#FFD54F") else Color.parseColor("#5A5040"))
            px += pip + gap
        }

        // název pod uzlem: zmenší se a případně zalomí na dva řádky, aby se vešel do sloupce
        val maxW = colW() - 6 * dp
        text.textSize = 14f * dp * resources.configuration.fontScale
        val words = n.title.split(" ")
        fun lines(): List<String> {
            if (text.measureText(n.title) <= maxW || words.size == 1) return listOf(n.title)
            // nejlepší zlom: co nejvyrovnanější řádky
            return (1 until words.size).map { k -> listOf(words.take(k).joinToString(" "), words.drop(k).joinToString(" ")) }
                .minByOrNull { ls -> ls.maxOf { text.measureText(it) } }!!
        }
        var ls = lines()
        while (ls.maxOf { text.measureText(it) } > maxW && text.textSize > 9 * dp) { text.textSize -= 1 * dp; ls = lines() }
        text.color = when {
            st == S.MAXED -> Color.parseColor("#FFE9A0")
            st == S.AVAILABLE || rank > 0 -> Color.parseColor("#FEFAE0")
            else -> Color.parseColor("#A9A396")
        }
        ls.forEachIndexed { k, line -> c.drawText(line, cx, py + pip + 15 * dp + k * (text.textSize + 1 * dp), text) }
        c.restore()
    }

    private fun drawBurst(c: Canvas, now: Long) {
        if (sparks.isEmpty()) return
        val t = (now - burstAt) / 1000f
        if (t > 0.9f) { sparks = emptyList(); return }
        // rozpínající se kruh z pixelů
        val ring = node / 2 + t * 90 * dp
        val a = ((1f - t / 0.9f) * 255).toInt().coerceIn(0, 255)
        for (k in 0 until 24) {
            val ang = k / 24.0 * 2 * PI
            val x = burstCenter.first + (cos(ang) * ring).toFloat(); val y = burstCenter.second + (sin(ang) * ring).toFloat()
            rect(c, x - 2 * dp, y - 2 * dp, x + 2 * dp, y + 2 * dp, Color.argb(a, 255, 243, 176))
        }
        sparks.forEach { s ->
            val x = s.x + s.vx * t; val y = s.y + s.vy * t + 260 * dp * t * t
            p.color = s.color; p.alpha = a
            c.drawRect(x, y, x + s.size, y + s.size, p)
        }
        p.alpha = 255
    }

    // ── ikony ───────────────────────────────────────────────────────────────

    private fun icon(key: String, w: Int = TreeArt.SIZE, h: Int = TreeArt.SIZE, pixels: () -> IntArray): Bitmap =
        icons.getOrPut(key) { Bitmap.createBitmap(pixels(), w, h, Bitmap.Config.ARGB_8888) }

    private fun icon(n: SkillTree.Node): Bitmap = icon(n, n.effect)

    /** Ikona podle efektu; vrcholné uzly mají ikonu prvního efektu. */
    private fun icon(n: SkillTree.Node, e: SkillTree.Effect): Bitmap = when (e) {
        is SkillTree.Effect.TeamSlot -> icon("ball") { Makroball.entries.first().pixels }
        is SkillTree.Effect.XpBonus -> icon("star") { TreeArt.STAR }
        is SkillTree.Effect.XpOthers -> icon("star") { TreeArt.STAR }
        is SkillTree.Effect.XpFor -> icon("skill_${e.skill.id}", SkillArt.ICON, SkillArt.ICON) { SkillArt.skillIcon(e.skill) }
        is SkillTree.Effect.MorePlots -> icon("plot", SkillArt.PLOT_W, SkillArt.PLOT_H) { SkillArt.plot(false) }
        is SkillTree.Effect.BasicEquipment -> icon("table", SkillArt.TABLE_W, SkillArt.TABLE_H) { SkillArt.craftingTable() }
        is SkillTree.Effect.FasterGrowth -> icon("leaf") { TreeArt.LEAF }
        is SkillTree.Effect.Efficiency -> icon("skill_${n.skill.id}", SkillArt.ICON, SkillArt.ICON) { SkillArt.skillIcon(n.skill) }
        is SkillTree.Effect.EfficiencyAll -> icon("skill_${Skill.MINING.id}", SkillArt.ICON, SkillArt.ICON) { SkillArt.skillIcon(Skill.MINING) }
        is SkillTree.Effect.AfkHours -> icon("moon") { TreeArt.MOON }
        is SkillTree.Effect.MultiChance -> icon("double") { TreeArt.DOUBLE }
        is SkillTree.Effect.DropRate -> icon("coin") { TreeArt.COIN }
        is SkillTree.Effect.CatchBonus -> icon("ball2") { Makroball.entries.last().pixels }
        is SkillTree.Effect.Many -> icon(n, e.list.first())
    }

    // ── dotyk ───────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action != MotionEvent.ACTION_UP) return super.onTouchEvent(e)
        val hit = nodes.minByOrNull { n -> center(n.id)?.let { (x, y) -> abs(x - e.x) + abs(y - e.y) } ?: Float.MAX_VALUE }
        val c = hit?.let { center(it.id) }
        if (hit != null && c != null && abs(c.first - e.x) < node && abs(c.second - e.y) < node) {
            selectedId = hit.id
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            onSelect(hit)
        }
        return true
    }

    override fun onDetachedFromWindow() { appearAnim?.cancel(); super.onDetachedFromWindow() }

    companion object {
        fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())
    }
}

/** Tmavé pozadí stromu: zelenočerný přechod v pixelových pruzích se ztmavenými okraji. */
class TreeBackdropDrawable(private val u: Float) : android.graphics.drawable.Drawable() {
    private val p = Paint().apply { isAntiAlias = false }
    override fun draw(c: Canvas) {
        val b: Rect = bounds
        val top = Color.parseColor("#1C2B24"); val bottom = Color.parseColor("#0C1310")
        val bands = 14
        for (i in 0 until bands) {
            p.color = SkillTreeView.blend(top, bottom, i / (bands - 1f))
            c.drawRect(b.left.toFloat(), b.top + b.height() * i / bands.toFloat(), b.right.toFloat(), b.top + b.height() * (i + 1) / bands.toFloat() + 1, p)
        }
        // jemná mřížka teček
        p.color = Color.argb(28, 160, 210, 140)
        var y = b.top + 6 * u
        while (y < b.bottom) {
            var x = b.left + 6 * u
            while (x < b.right) { c.drawRect(x, y, x + u, y + u, p); x += 12 * u }
            y += 12 * u
        }
        // ztmavené okraje (vinětace po pixelech)
        for (k in 0 until 4) {
            p.color = Color.argb(60 - k * 14, 0, 0, 0)
            val o = k * 2 * u
            c.drawRect(b.left + o, b.top + o, b.right - o, b.top + o + 2 * u, p)
            c.drawRect(b.left + o, b.bottom - o - 2 * u, b.right - o, b.bottom - o, p)
            c.drawRect(b.left + o, b.top + o, b.left + o + 2 * u, b.bottom - o, p)
            c.drawRect(b.right - o - 2 * u, b.top + o, b.right - o, b.bottom - o, p)
        }
    }
    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
}
