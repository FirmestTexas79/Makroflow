package cz.uhk.macroflow.pokemon.walk

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Bod v pixelech obrázku mapy (nebo světa – podle kontextu). */
data class Pt(val x: Float, val y: Float) {
    fun dist(o: Pt): Float = hypot(x - o.x, y - o.y)
}

/**
 * Mapa chůze (docs/adr/0033): obrázek mapy rozdělený na čtvercové buňky, každá je
 * průchozí ('.') nebo zeď ('#' – domy, stromy, voda, skály). Generuje ji
 * tools/walkmask/gen_walkmask.py do assets/walk/<biom>.txt.
 *
 * Čistý Kotlin bez Androidu – hledání cesty je pokryté testy.
 */
class WalkGrid(
    /** Rozměr obrázku mapy v pixelech. */
    val imgW: Int,
    val imgH: Int,
    /** Hrana buňky v pixelech obrázku. */
    val cell: Int,
    val cols: Int,
    val rows: Int,
    private val walk: BooleanArray
) {
    init { require(walk.size == cols * rows) { "maska ${cols}x$rows má ${walk.size} buněk" } }

    fun isWalkableCell(cx: Int, cy: Int): Boolean =
        cx in 0 until cols && cy in 0 until rows && walk[cy * cols + cx]

    fun cellOf(p: Pt): Pair<Int, Int> =
        (floor(p.x / cell).toInt()).coerceIn(0, cols - 1) to (floor(p.y / cell).toInt()).coerceIn(0, rows - 1)

    fun isWalkable(p: Pt): Boolean {
        if (p.x < 0 || p.y < 0 || p.x >= imgW || p.y >= imgH) return false
        val (cx, cy) = cellOf(p)
        return isWalkableCell(cx, cy)
    }

    fun center(cx: Int, cy: Int) = Pt((cx + 0.5f) * cell, (cy + 0.5f) * cell)

    val walkableCount: Int get() = walk.count { it }

    /** Nejbližší průchozí buňka k bodu (po čtvercových prstencích, pak skutečná vzdálenost). */
    fun nearestWalkable(p: Pt): Pair<Int, Int>? {
        val (sx, sy) = cellOf(p)
        if (isWalkableCell(sx, sy)) return sx to sy
        val maxR = max(cols, rows)
        for (r in 1..maxR) {
            var best: Pair<Int, Int>? = null
            var bestD = Float.MAX_VALUE
            for (cy in sy - r..sy + r) for (cx in sx - r..sx + r) {
                if (max(abs(cx - sx), abs(cy - sy)) != r || !isWalkableCell(cx, cy)) continue
                val d = center(cx, cy).dist(p)
                if (d < bestD) { bestD = d; best = cx to cy }
            }
            // Ještě o prstenec dál může ležet blíž (roh vs. hrana) – stačí jeden navíc
            if (best != null) {
                for (cy in sy - r - 1..sy + r + 1) for (cx in sx - r - 1..sx + r + 1) {
                    if (max(abs(cx - sx), abs(cy - sy)) != r + 1 || !isWalkableCell(cx, cy)) continue
                    val d = center(cx, cy).dist(p)
                    if (d < bestD) { bestD = d; best = cx to cy }
                }
                return best
            }
        }
        return null
    }

    /** Nejbližší průchozí bod: bod sám, jinak střed nejbližší průchozí buňky. */
    fun snap(p: Pt): Pt? = if (isWalkable(p)) p else nearestWalkable(p)?.let { center(it.first, it.second) }

    /**
     * Cesta z [from] do [to] v pixelech obrázku: A* po buňkách (8 směrů, bez řezání rohů),
     * pak vyhlazení – vynechá body, mezi kterými je přímá průchozí čára.
     * Nepochozí start/cíl se přichytí na nejbližší průchozí místo. Vrací body BEZ startu,
     * poslední je cíl; prázdný seznam = už jsi tam; null = nedá se dojít.
     */
    fun findPath(from: Pt, to: Pt): List<Pt>? {
        val start = snap(from) ?: return null
        val goal = snap(to) ?: return null
        val (sx, sy) = cellOf(start)
        val (gx, gy) = cellOf(goal)
        val cells = aStar(sx, sy, gx, gy) ?: return null

        // Uzlové body: start, středy buněk, cíl – a pak vyhladit
        val raw = ArrayList<Pt>(cells.size + 2)
        raw += start
        for (i in 1 until cells.size - 1) raw += center(cells[i] % cols, cells[i] / cols)
        raw += goal
        val out = smooth(raw).drop(1).toMutableList()
        // Start mimo průchozí plochu (postava stála na uzlu u zdi) – nejdřív na přichycený bod
        if (start.dist(from) > cell * 0.5f) out.add(0, start)
        return out.filterIndexed { i, p -> p.dist(if (i == 0) from else out[i - 1]) > 0.01f }
    }

    private fun aStar(sx: Int, sy: Int, gx: Int, gy: Int): List<Int>? {
        val n = cols * rows
        val start = sy * cols + sx
        val goal = gy * cols + gx
        if (start == goal) return listOf(start)
        val g = FloatArray(n) { Float.MAX_VALUE }
        val came = IntArray(n) { -1 }
        val closed = BooleanArray(n)
        fun h(i: Int): Float {
            val dx = abs(i % cols - gx).toFloat(); val dy = abs(i / cols - gy).toFloat()
            return max(dx, dy) + (SQRT2 - 1f) * min(dx, dy)          // oktilová vzdálenost
        }
        val open = PriorityQueue<Pair<Int, Float>>(compareBy { it.second })
        g[start] = 0f
        open += start to h(start)
        while (open.isNotEmpty()) {
            val (cur, _) = open.poll()!!
            if (closed[cur]) continue
            if (cur == goal) break
            closed[cur] = true
            val cx = cur % cols; val cy = cur / cols
            for (k in 0 until 8) {
                val dx = DX[k]; val dy = DY[k]
                val nx = cx + dx; val ny = cy + dy
                if (!isWalkableCell(nx, ny)) continue
                // Úhlopříčně jen když jsou volné obě sousední buňky (žádné řezání rohů)
                if (dx != 0 && dy != 0 && (!isWalkableCell(cx + dx, cy) || !isWalkableCell(cx, cy + dy))) continue
                val ni = ny * cols + nx
                if (closed[ni]) continue
                val alt = g[cur] + if (dx != 0 && dy != 0) SQRT2 else 1f
                if (alt < g[ni]) {
                    g[ni] = alt; came[ni] = cur
                    open += ni to alt + h(ni)
                }
            }
        }
        if (came[goal] == -1) return null
        val path = ArrayList<Int>()
        var c = goal
        while (c != -1) { path += c; c = came[c] }
        path.reverse()
        return path
    }

    /** Vynechá mezilehlé body, na které je z předchozího vidět (přímka celá po průchozí ploše). */
    fun smooth(points: List<Pt>): List<Pt> {
        if (points.size <= 2) return points
        val out = mutableListOf(points.first())
        var anchor = 0
        while (anchor < points.size - 1) {
            var next = anchor + 1
            for (j in points.size - 1 downTo anchor + 2) {
                if (lineWalkable(points[anchor], points[j])) { next = j; break }
            }
            out += points[next]
            anchor = next
        }
        return out
    }

    /**
     * Je celá úsečka průchozí? Kontroluje se i šířka postavy: úsečka posunutá o čtvrt buňky
     * na obě strany, aby cesta nešla těsně po hraně zdi.
     */
    fun lineWalkable(a: Pt, b: Pt): Boolean {
        val len = a.dist(b)
        if (len < 0.001f) return isWalkable(a)
        val nx = -(b.y - a.y) / len * cell * 0.25f
        val ny = (b.x - a.x) / len * cell * 0.25f
        val steps = max(1, (len / (cell * 0.25f)).toInt())
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            val x = a.x + (b.x - a.x) * t; val y = a.y + (b.y - a.y) * t
            if (!isWalkable(Pt(x, y))) return false
            // Konce úsečky smí ležet u zdi (přichycené body), boky se kontrolují jen uvnitř
            if (s in 1 until steps && (!isWalkable(Pt(x + nx, y + ny)) || !isWalkable(Pt(x - nx, y - ny)))) return false
        }
        return true
    }

    companion object {
        private val SQRT2 = sqrt(2f)
        private val DX = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        private val DY = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

        /**
         * Formát assets/walk/<biom>.txt: řádky s '#' na začátku jsou komentáře, pak
         * „šířka výška buňka“ obrázku a řádky mřížky ('.' průchozí, cokoli jiného zeď).
         */
        fun parse(text: String): WalkGrid {
            val lines = text.lineSequence().map { it.trimEnd('\r') }
                .filter { it.isNotEmpty() }
                .dropWhile { it.startsWith("# ") }
                .toList()
            val head = lines.first().trim().split(Regex("\\s+")).map { it.toInt() }
            require(head.size == 3) { "hlavička masky: šířka výška buňka" }
            val (w, h, cell) = head
            val rows = lines.drop(1)
            val cols = (w + cell - 1) / cell
            val rowCount = (h + cell - 1) / cell
            require(rows.size == rowCount) { "maska má ${rows.size} řádků, čekáno $rowCount" }
            val walk = BooleanArray(cols * rowCount)
            rows.forEachIndexed { y, r ->
                require(r.length == cols) { "řádek $y má ${r.length} znaků, čekáno $cols" }
                r.forEachIndexed { x, ch -> walk[y * cols + x] = ch == '.' }
            }
            return WalkGrid(w, h, cell, cols, rowCount, walk)
        }
    }
}

/**
 * Převod mezi světem mapy (pixely View) a pixely obrázku mapy. Běžné mapy jsou CENTER_CROP
 * přes obrazovku; jeskyně a les mají svět přesně celočíselný násobek obrázku – stejný vzorec
 * pak dá měřítko = násobek a nulový posun.
 */
class MapGeometry(val imgW: Int, val imgH: Int, val viewW: Int, val viewH: Int) {
    val scale: Float = max(viewW.toFloat() / imgW, viewH.toFloat() / imgH)
    val offsetX: Float = (viewW - imgW * scale) / 2f
    val offsetY: Float = (viewH - imgH * scale) / 2f

    fun toImage(world: Pt) = Pt((world.x - offsetX) / scale, (world.y - offsetY) / scale)
    fun toWorld(img: Pt) = Pt(img.x * scale + offsetX, img.y * scale + offsetY)
}

/** Směr sprite postavy pro úsek chůze: 0 dolů, 1 nahoru, 2 doleva, 3 doprava (jako MovementEngine). */
object WalkDirection {
    const val DOWN = 0
    const val UP = 1
    const val LEFT = 2
    const val RIGHT = 3

    /** Převažující osa; u úhlopříčky mírně preferuje svislý směr (postava se víc „dívá“ po cestě). */
    fun of(dx: Float, dy: Float, previous: Int = DOWN): Int = when {
        abs(dx) < 0.5f && abs(dy) < 0.5f -> previous
        abs(dx) > abs(dy) * 1.1f -> if (dx > 0) RIGHT else LEFT
        else -> if (dy > 0) DOWN else UP
    }
}
