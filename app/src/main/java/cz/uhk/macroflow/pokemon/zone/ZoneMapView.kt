package cz.uhk.macroflow.pokemon.zone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Mapa „Zóna 1“ na stránce deníku (docs/adr/0052). Kreslí výřezy lokací ve tvaru chozeného území,
 * spoje mezi východy a vchody, hlavy NPC a hráče tam, kde právě stojí. Klepnutí na lokaci → [onPick].
 * Rozvržení je v assets/zone/zone1.json (tools/mapgen/gen_zone.py).
 */
class ZoneMapView(context: Context) : View(context) {

    private class Loc(val id: String, val rect: RectF, val map: FloatArray, val label: PointF, val bmp: Bitmap?)
    private class Link(val a: String, val aDir: String, val aPos: PointF, val b: String, val bDir: String, val bPos: PointF)
    private class Npc(val biome: String, val pos: PointF, val head: Bitmap?)

    private val cw: Float
    private val ch: Float
    private val labelSize: Float
    private val locs = LinkedHashMap<String, Loc>()
    private val links = ArrayList<Link>()
    private val npcs = ArrayList<Npc>()

    /** Lokace, ve které hráč stojí, a jeho pozice v ní (podíl světa / obrazovky jako v MovementEngine). */
    var current = "TOWN"
    var heroFrac: PointF? = null
    var heroHead: Bitmap? = null
    var seen: Set<String> = setOf("TOWN")
        set(v) { field = v; invalidate() }
    var onPick: ((String) -> Unit)? = null

    private val ink = 0xFF4A3520.toInt()
    private val rust = 0xFF7A2E1E.toInt()
    private val paper = 0xFFF1E3C0.toInt()
    private val bmpPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val fogPaint = Paint().apply { isFilterBitmap = false; colorFilter = PorterDuffColorFilter(0xFFDAD6CC.toInt(), PorterDuff.Mode.SRC_IN); alpha = 215 }
    /** Cesta: tmavý podklad a světlé čárky navrch – je vidět na lese, skále i v jeskyni. */
    private val linkUnder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 9f; color = 0xFF281A0E.toInt(); strokeCap = Paint.Cap.ROUND
    }
    private val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = 0xFFF6E8C4.toInt(); strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
    }
    private val seaColor = 0xFF3D7DB5.toInt()
    /** Souvislý terén celé zóny (gen_zone.py → zone/bg.png). */
    private val bg: Bitmap? = bitmap("zone/bg.png")
    private var titlePos = PointF(795f, 1150f)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; strokeWidth = 8f; color = paper; strokeJoin = Paint.Join.ROUND }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    // převod plátno → obrazovka
    private var s = 1f
    private var ox = 0f
    private var oy = 0f

    init {
        val json = JSONObject(context.assets.open("zone/zone1.json").bufferedReader().use { it.readText() })
        cw = json.getDouble("w").toFloat(); ch = json.getDouble("h").toFloat()
        labelSize = json.optDouble("labelSize", 30.0).toFloat()
        json.optJSONArray("title")?.let { titlePos = it.pt() }
        val l = json.getJSONObject("locations")
        ZoneOne.LOCATIONS.filter { l.has(it) }.forEach { id ->
            val o = l.getJSONObject(id)
            val r = o.getJSONArray("rect"); val m = o.getJSONArray("map"); val lb = o.getJSONArray("label")
            locs[id] = Loc(id,
                RectF(r.f(0), r.f(1), r.f(0) + r.f(2), r.f(1) + r.f(3)),
                floatArrayOf(m.f(0), m.f(1), m.f(2), m.f(3)),
                PointF(lb.f(0), lb.f(1)),
                bitmap("zone/$id.png"))
        }
        val ls = json.getJSONArray("links")
        for (i in 0 until ls.length()) {
            val o = ls.getJSONObject(i)
            links += Link(o.getString("a"), o.getString("aDir"), o.getJSONArray("aPos").pt(),
                o.getString("b"), o.getString("bDir"), o.getJSONArray("bPos").pt())
        }
        val ns = json.getJSONObject("npcs")
        ns.keys().forEach { b -> npcs += Npc(b, ns.getJSONObject(b).getJSONArray("pos").pt(), bitmap("zone/head_$b.png")) }
        text.typeface = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, cz.uhk.macroflow.R.font.jersey_15) }.getOrNull()
        halo.typeface = text.typeface
    }

    private fun org.json.JSONArray.f(i: Int) = getDouble(i).toFloat()
    private fun org.json.JSONArray.pt() = PointF(f(0), f(1))

    private fun bitmap(path: String): Bitmap? = runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false }) }
    }.getOrNull()

    override fun onDraw(canvas: Canvas) {
        s = min(width / cw, height / ch)
        ox = (width - cw * s) / 2f; oy = (height - ch * s) / 2f
        canvas.drawColor(seaColor)                                   // moře i mimo plátno
        canvas.save()
        canvas.translate(ox, oy); canvas.scale(s, s)
        bg?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, cw, ch), bmpPaint) }

        compass(canvas, titlePos.x, titlePos.y)
        val state = locs.keys.associateWith { ZoneOne.shown(it, current, seen) }
        // spoje (pod lokacemi – vchod a východ označí kolečko na okraji tvaru)
        for (k in links) {
            if (!ZoneOne.linkShown(k.a, k.b, current, seen)) continue
            val fog = state[k.a] == ZoneOne.Shown.FOG || state[k.b] == ZoneOne.Shown.FOG
            val path = curve(k)
            linkUnder.alpha = if (fog) 90 else 170
            linkPaint.alpha = if (fog) 120 else 255
            canvas.drawPath(path, linkUnder)
            canvas.drawPath(path, linkPaint)
        }
        // lokace
        for ((id, loc) in locs) {
            val st = state[id] ?: continue
            if (st == ZoneOne.Shown.HIDDEN) continue
            loc.bmp?.let { canvas.drawBitmap(it, null, loc.rect, if (st == ZoneOne.Shown.FOG) fogPaint else bmpPaint) }
            if (st == ZoneOne.Shown.FOG) {
                text.textSize = 64f; text.color = 0xCC5A5448.toInt()
                canvas.drawText("?", loc.rect.centerX(), loc.rect.centerY() + 22f, text)
            }
        }
        // vchody a východy
        for (k in links) {
            if (!ZoneOne.linkShown(k.a, k.b, current, seen)) continue
            listOf(k.a to k.aPos, k.b to k.bPos).forEach { (b, p) -> if (state[b] != ZoneOne.Shown.FOG) door(canvas, p) }
        }
        // jména
        for ((id, loc) in locs) {
            val st = state[id] ?: continue
            if (st == ZoneOne.Shown.HIDDEN) continue
            val name = if (st == ZoneOne.Shown.FOG) "???" else ZoneOne.NAMES[id] ?: id
            text.textSize = labelSize; halo.textSize = labelSize
            text.color = if (st == ZoneOne.Shown.HERE) rust else ink
            canvas.drawText(name, loc.label.x, loc.label.y, halo)
            canvas.drawText(name, loc.label.x, loc.label.y, text)
        }
        // hlavy NPC
        for (n in npcs) {
            val st = state[n.biome]
            if (st != ZoneOne.Shown.KNOWN && st != ZoneOne.Shown.HERE) continue
            badge(canvas, n.pos.x, n.pos.y, 27f, n.head, ink)
        }
        // hráč
        val here = locs[current]
        val f = heroFrac
        if (here != null && f != null) {
            val hx = here.map[0] + f.x * here.map[1]
            val hy = here.map[2] + f.y * here.map[3]
            val t = (SystemClock.uptimeMillis() % 1400L) / 1400f
            stroke.color = rust; stroke.strokeWidth = 4f; stroke.alpha = (255 * (1f - t)).toInt()
            canvas.drawCircle(hx, hy, 22f + 26f * t, stroke)
            stroke.alpha = 255
            badge(canvas, hx, hy, 24f, heroHead, rust)
            postInvalidateDelayed(40)
        }
        canvas.restore()
    }

    /** Křivka spoje: z každého konce vyjede ve směru, kterým se z lokace odchází. */
    private fun curve(k: Link): Path {
        val d = hypot(k.bPos.x - k.aPos.x, k.bPos.y - k.aPos.y)
        val len = max(60f, 0.45f * d)
        fun dv(dir: String) = when (dir) { "n" -> 0f to -1f; "s" -> 0f to 1f; "e" -> 1f to 0f; else -> -1f to 0f }
        val (ax, ay) = dv(k.aDir); val (bx, by) = dv(k.bDir)
        return Path().apply {
            moveTo(k.aPos.x, k.aPos.y)
            cubicTo(k.aPos.x + ax * len, k.aPos.y + ay * len, k.bPos.x + bx * len, k.bPos.y + by * len, k.bPos.x, k.bPos.y)
        }
    }

    /** Název zóny a větrná růžice v prázdném rohu mapy. */
    private fun compass(canvas: Canvas, cx: Float, cy: Float) {
        // název zóny na pergamenové stuze (jako cedule mapy)
        text.textSize = 64f; text.color = ink
        val tw = text.measureText(ZoneOne.TITLE)
        val banner = RectF(cx - tw / 2f - 30f, cy - 150f, cx + tw / 2f + 30f, cy - 76f)
        fill.color = 0xFFB08A58.toInt()
        canvas.drawRect(banner.left - 18f, banner.top + 14f, banner.left + 8f, banner.bottom + 10f, fill)     // konce stuhy
        canvas.drawRect(banner.right - 8f, banner.top + 14f, banner.right + 18f, banner.bottom + 10f, fill)
        fill.color = paper; canvas.drawRoundRect(banner, 8f, 8f, fill)
        stroke.color = ink; stroke.strokeWidth = 4f; canvas.drawRoundRect(banner, 8f, 8f, stroke)
        canvas.drawText(ZoneOne.TITLE, cx, cy - 92f, text)
        val r = 52f
        val star = Path()
        for (i in 0 until 8) {
            val a = Math.PI / 4 * i - Math.PI / 2
            val rr = if (i % 2 == 0) r else r * 0.32f
            val x = cx + (rr * Math.cos(a)).toFloat(); val y = cy + (rr * Math.sin(a)).toFloat()
            if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        fill.color = paper; canvas.drawPath(star, fill)
        stroke.color = ink; stroke.strokeWidth = 3f; canvas.drawPath(star, stroke)
        stroke.strokeWidth = 2f; canvas.drawCircle(cx, cy, r * 0.62f, stroke)
        text.textSize = 28f; halo.textSize = 28f
        listOf(Triple("S", cx, cy - r - 8f), Triple("J", cx, cy + r + 28f), Triple("V", cx + r + 18f, cy + 10f), Triple("Z", cx - r - 18f, cy + 10f))
            .forEach { (t, x, y) -> text.color = if (t == "S") rust else ink; canvas.drawText(t, x, y, halo); canvas.drawText(t, x, y, text) }
    }

    private fun door(canvas: Canvas, p: PointF) {
        fill.color = paper; canvas.drawCircle(p.x, p.y, 8f, fill)
        stroke.color = rust; stroke.strokeWidth = 3.5f; canvas.drawCircle(p.x, p.y, 8f, stroke)
    }

    /** Kulatý odznak s hlavou (NPC nebo hráč). */
    private fun badge(canvas: Canvas, x: Float, y: Float, r: Float, head: Bitmap?, ring: Int) {
        fill.color = paper; canvas.drawCircle(x, y, r, fill)
        if (head != null) {
            val shader = BitmapShader(head, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val sc = (2 * r) / max(head.width, head.height)
            shader.setLocalMatrix(Matrix().apply {
                postScale(sc, sc)
                postTranslate(x - head.width * sc / 2f, y - head.height * sc / 2f)
            })
            headPaint.shader = shader
            canvas.drawCircle(x, y, r - 2f, headPaint)
            headPaint.shader = null
        }
        stroke.color = ring; stroke.strokeWidth = 4f; canvas.drawCircle(x, y, r, stroke)
    }

    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; return true }
            MotionEvent.ACTION_UP -> {
                if (hypot(e.x - downX, e.y - downY) > 24 * resources.displayMetrics.density) return true
                pick((e.x - ox) / s, (e.y - oy) / s)?.let { performClick(); onPick?.invoke(it) }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    /** Lokace pod bodem plátna: nejdřív přesně podle tvaru (neprůhledný pixel), pak nejbližší obdélník. */
    private fun pick(x: Float, y: Float): String? {
        val visible = locs.values.filter { ZoneOne.shown(it.id, current, seen) != ZoneOne.Shown.HIDDEN }
        visible.firstOrNull { l ->
            val b = l.bmp ?: return@firstOrNull false
            if (!l.rect.contains(x, y)) return@firstOrNull false
            val px = ((x - l.rect.left) / l.rect.width() * b.width).toInt().coerceIn(0, b.width - 1)
            val py = ((y - l.rect.top) / l.rect.height() * b.height).toInt().coerceIn(0, b.height - 1)
            (b.getPixel(px, py) ushr 24) > 0
        }?.let { return it.id }
        val pad = 24f
        return visible.filter { RectF(it.rect).apply { inset(-pad, -pad) }.contains(x, y) }
            .minByOrNull { hypot(it.rect.centerX() - x, it.rect.centerY() - y) }?.id
    }
}
