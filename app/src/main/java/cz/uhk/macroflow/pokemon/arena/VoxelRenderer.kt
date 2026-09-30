package cz.uhk.macroflow.pokemon.arena

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Voxelová aréna souboje (docs/adr/0038): malý softwarový 3D renderer v čistém Kotlinu.
 * Scéna = kvádry s procedurální pixelovou texturou (6 texelů na blok), perspektivní kamera,
 * z-buffer, stíny ze slunce (shadow mapa), mlha a bodová světla. Kreslí se jednou na začátku
 * souboje do bitmapy; Makromoni se pak kreslí přes ni.
 */
data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V3(x * s, y * s, z * s)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun len() = sqrt(dot(this))
    fun norm(): V3 { val l = len(); return if (l == 0f) this else this * (1f / l) }
}

/** Vzor textury – určuje, jak se z palety skládají texely. */
enum class Pattern { GRASS, FLOWERS, DIRT, SAND, STONE, GRAVEL, SNOW, MOSS, TILES, COBBLE, BRICK, PLASTER, PLANK, LEAF, BARK, ROOF, WATER, CRYSTAL, LAMP, WINDOW, METAL, MUSHROOM, LILY }

/** Materiál: vzor + paleta (tmavá, základ, světlá, akcent). Svítící se nestínuje a nemlží tolik. */
class Mat(val pattern: Pattern, val c0: Int, val c1: Int, val c2: Int, val c3: Int = c2, val emissive: Boolean = false)

class Box(val x0: Float, val y0: Float, val z0: Float, val x1: Float, val y1: Float, val z1: Float, val mat: Mat, val castsShadow: Boolean = true)

class PointLight(val pos: V3, val color: Int, val radius: Float, val strength: Float = 1f)

class Camera(val pos: V3, yawDeg: Float, pitchDeg: Float, fovDeg: Float) {
    val fwd: V3
    val right: V3
    val up: V3
    val fovTan = tan(Math.toRadians(fovDeg / 2.0)).toFloat()

    init {
        val yaw = Math.toRadians(yawDeg.toDouble()); val pitch = Math.toRadians(pitchDeg.toDouble())
        fwd = V3((sin(yaw) * cos(pitch)).toFloat(), (-sin(pitch)).toFloat(), (cos(yaw) * cos(pitch)).toFloat()).norm()
        right = V3(0f, 1f, 0f).cross(fwd).norm()
        up = fwd.cross(right).norm()
    }

    /** Světový bod → (x, y) obrazovky a hloubka; null za kamerou. */
    fun project(p: V3, w: Int, h: Int): Triple<Float, Float, Float>? {
        val d = p - pos
        val zv = d.dot(fwd); if (zv <= 0.05f) return null
        val f = (w / 2f) / fovTan
        return Triple(w / 2f + d.dot(right) / zv * f, h / 2f - d.dot(up) / zv * f, zv)
    }

    /** Paprsek pod pixelem protnutý s vodorovnou rovinou y = [planeY]. */
    fun unproject(sx: Float, sy: Float, w: Int, h: Int, planeY: Float): V3? {
        val dir = ray(sx, sy, w, h)
        if (abs(dir.y) < 1e-4f) return null
        val t = (planeY - pos.y) / dir.y
        return if (t <= 0f) null else pos + dir * t
    }

    fun ray(sx: Float, sy: Float, w: Int, h: Int, cy: Float = h / 2f): V3 {
        val f = (w / 2f) / fovTan
        return (fwd + right * ((sx - w / 2f) / f) + up * ((cy - sy) / f)).norm()
    }
}

/** Nálada lokace: obloha, mlha, světlo. */
class Atmosphere(
    val skyTop: Int, val skyHorizon: Int,
    val fog: Int, val fogStart: Float, val fogEnd: Float, val fogMax: Float = 1f,
    val light: Int = 0xFFFFFFFF.toInt(),      // barva a síla slunce (násobí texturu)
    val ambient: Float = 0.6f,                // jas ve stínu
    val sun: V3 = V3(-0.45f, 1f, -0.35f),
    val lights: List<PointLight> = emptyList(),
    val clouds: Boolean = true,
    val stars: Boolean = false
)

class Scene(val boxes: List<Box>, val atmosphere: Atmosphere, val seed: Int = 0)

object VoxelRenderer {
    const val TEX = 6

    private class Face(val v: Array<V3>, val n: Int, val box: Box)

    // normály: 0 +y, 1 -y, 2 +x, 3 -x, 4 +z, 5 -z
    private val NORMALS = arrayOf(V3(0f, 1f, 0f), V3(0f, -1f, 0f), V3(1f, 0f, 0f), V3(-1f, 0f, 0f), V3(0f, 0f, 1f), V3(0f, 0f, -1f))

    private fun faces(b: Box): List<Face> {
        val x0 = b.x0; val y0 = b.y0; val z0 = b.z0; val x1 = b.x1; val y1 = b.y1; val z1 = b.z1
        return listOf(
            Face(arrayOf(V3(x0, y1, z0), V3(x1, y1, z0), V3(x1, y1, z1), V3(x0, y1, z1)), 0, b),
            Face(arrayOf(V3(x0, y0, z0), V3(x0, y0, z1), V3(x1, y0, z1), V3(x1, y0, z0)), 1, b),
            Face(arrayOf(V3(x1, y0, z0), V3(x1, y0, z1), V3(x1, y1, z1), V3(x1, y1, z0)), 2, b),
            Face(arrayOf(V3(x0, y0, z0), V3(x0, y1, z0), V3(x0, y1, z1), V3(x0, y0, z1)), 3, b),
            Face(arrayOf(V3(x0, y0, z1), V3(x0, y1, z1), V3(x1, y1, z1), V3(x1, y0, z1)), 4, b),
            Face(arrayOf(V3(x0, y0, z0), V3(x1, y0, z0), V3(x1, y1, z0), V3(x0, y1, z0)), 5, b)
        )
    }

    /**
     * Rasterizace konvexního polygonu. Vrcholy: obrazovka (sx, sy), q = 1/w pro perspektivní
     * interpolaci, atributy (x, y, z, hloubka). Volá [px] pro každý pokrytý pixel.
     */
    private inline fun raster(
        w: Int, h: Int, n: Int, sx: FloatArray, sy: FloatArray, q: FloatArray, attr: Array<FloatArray>,
        yLo: Int = 0, yHi: Int = h - 1,
        px: (Int, Float, Float, Float, Float) -> Unit
    ) {
        for (t in 1 until n - 1) {
            val a = 0; val b = t; val c = t + 1
            val area = (sx[b] - sx[a]) * (sy[c] - sy[a]) - (sy[b] - sy[a]) * (sx[c] - sx[a])
            if (abs(area) < 1e-6f) continue
            val minX = max(0, floor(min(sx[a], min(sx[b], sx[c]))).toInt())
            val maxX = min(w - 1, floor(max(sx[a], max(sx[b], sx[c]))).toInt())
            val minY = max(yLo, floor(min(sy[a], min(sy[b], sy[c]))).toInt())
            val maxY = min(yHi, floor(max(sy[a], max(sy[b], sy[c]))).toInt())
            if (minX > maxX || minY > maxY) continue
            val inv = 1f / area
            for (y in minY..maxY) {
                val py = y + 0.5f
                for (x in minX..maxX) {
                    val pxf = x + 0.5f
                    val w0 = ((sx[b] - pxf) * (sy[c] - py) - (sy[b] - py) * (sx[c] - pxf)) * inv
                    val w1 = ((sx[c] - pxf) * (sy[a] - py) - (sy[c] - py) * (sx[a] - pxf)) * inv
                    val w2 = 1f - w0 - w1
                    if (w0 < -1e-5f || w1 < -1e-5f || w2 < -1e-5f) continue
                    val qq = w0 * q[a] + w1 * q[b] + w2 * q[c]
                    val iq = 1f / qq
                    px(y * w + x,
                        (w0 * attr[a][0] + w1 * attr[b][0] + w2 * attr[c][0]) * iq,
                        (w0 * attr[a][1] + w1 * attr[b][1] + w2 * attr[c][1]) * iq,
                        (w0 * attr[a][2] + w1 * attr[b][2] + w2 * attr[c][2]) * iq,
                        (w0 * attr[a][3] + w1 * attr[b][3] + w2 * attr[c][3]) * iq)
                }
            }
        }
    }

    /** Promítnutý polygon (až 8 vrcholů po ořezu) připravený k rasterizaci. */
    private class Poly(val n: Int, val sx: FloatArray, val sy: FloatArray, val q: FloatArray, val attr: Array<FloatArray>, val face: Int)

    /** Kolik vláken použít – obraz se dělí na vodorovné pásy, každé vlákno kreslí svůj pás. */
    var threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)

    /** Spustí [band] pro pásy řádků 0 until [h] paralelně (na jednom jádře přímo). */
    private fun parallelRows(h: Int, band: (Int, Int) -> Unit) {
        val n = threads.coerceAtMost(h).coerceAtLeast(1)
        if (n == 1) { band(0, h - 1); return }
        val step = (h + n - 1) / n
        val workers = (0 until n).mapNotNull { i ->
            val y0 = i * step; val y1 = min(h - 1, y0 + step - 1)
            if (y0 > y1) null else Thread { band(y0, y1) }.apply { start() }
        }
        workers.forEach { it.join() }
    }

    // ── Stínová mapa (ortogonální pohled ze slunce) ──

    private class ShadowMap(val sun: V3, boxes: List<Box>, val size: Int = 768) {
        val r: V3 = V3(0f, 1f, 0f).cross(sun).norm().let { if (it.len() < 0.5f) V3(1f, 0f, 0f) else it }
        val u: V3 = sun.cross(r).norm()
        var minA = Float.MAX_VALUE; var maxA = -Float.MAX_VALUE; var minB = Float.MAX_VALUE; var maxB = -Float.MAX_VALUE
        val depth = FloatArray(size * size) { Float.MAX_VALUE }
        // rozbalené složky pro horkou smyčku (bez alokací na pixel)
        private val rx = r.x; private val ry = r.y; private val rz = r.z
        private val ux = u.x; private val uy = u.y; private val uz = u.z
        private val sx0 = sun.x; private val sy0 = sun.y; private val sz0 = sun.z
        private var spanA = 1f; private var spanB = 1f

        init {
            val casters = boxes.filter { it.castsShadow }
            for (b in casters) for (f in faces(b)) for (v in f.v) {
                minA = min(minA, v.dot(r)); maxA = max(maxA, v.dot(r)); minB = min(minB, v.dot(u)); maxB = max(maxB, v.dot(u))
            }
            spanA = maxA - minA + 1e-3f; spanB = maxB - minB + 1e-3f
            val q = FloatArray(4) { 1f }
            val polys = ArrayList<Poly>()
            for (b in casters) for (f in faces(b)) {
                val sx = FloatArray(4); val sy = FloatArray(4); val attr = Array(4) { FloatArray(4) }
                for (i in 0 until 4) {
                    val v = f.v[i]
                    sx[i] = mapX(v.x, v.y, v.z); sy[i] = mapY(v.x, v.y, v.z)
                    attr[i][3] = -v.dot(sun)
                }
                polys.add(Poly(4, sx, sy, q, attr, 0))
            }
            parallelRows(size) { y0, y1 ->
                for (p in polys) raster(size, size, 4, p.sx, p.sy, p.q, p.attr, y0, y1) { idx, _, _, _, d -> if (d < depth[idx]) depth[idx] = d }
            }
        }

        // stejné pořadí operací jako V3.dot → stejný výsledek
        private fun mapX(x: Float, y: Float, z: Float) = (x * rx + y * ry + z * rz - minA) / spanA * size
        private fun mapY(x: Float, y: Float, z: Float) = (x * ux + y * uy + z * uz - minB) / spanB * size

        fun lit(x: Float, y: Float, z: Float): Boolean {
            val ix = mapX(x, y, z).toInt(); val iy = mapY(x, y, z).toInt()
            if (ix < 0 || ix >= size || iy < 0 || iy >= size) return true
            return -(x * sx0 + y * sy0 + z * sz0) <= depth[iy * size + ix] + 0.04f
        }
    }

    /**
     * Vykreslí scénu do pole ARGB (w × h). [cy] = řádek středu pohledu – když je obraz nahoře
     * prodloužený (aréna až k hornímu okraji displeje), střed zůstává na stejném místě scény.
     */
    fun render(scene: Scene, cam: Camera, w: Int, h: Int, cy: Float = h / 2f, shadowSize: Int = 768): IntArray {
        val atm = scene.atmosphere
        val sun = atm.sun.norm()
        val shadow = ShadowMap(sun, scene.boxes, shadowSize)
        val ctx = ShadeCtx(scene, sun, shadow)
        val depth = FloatArray(w * h) { Float.MAX_VALUE }
        val faceAt = IntArray(w * h) { -1 }
        val wx = FloatArray(w * h); val wy = FloatArray(w * h); val wz = FloatArray(w * h)
        val allFaces = ArrayList<Face>()
        val near = 0.1f
        val f = (w / 2f) / cam.fovTan

        val polys = ArrayList<Poly>()
        for (b in scene.boxes) for (face in faces(b)) {
            val n = NORMALS[face.n]
            val center = (face.v[0] + face.v[2]) * 0.5f
            if (n.dot(cam.pos - center) <= 0f) continue        // odvrácená stěna
            // ořez blízkou rovinou (Sutherland–Hodgman) ve view prostoru
            val inV = face.v.map { p -> val d = p - cam.pos; Pair(p, d.dot(cam.fwd)) }
            val out = ArrayList<Pair<V3, Float>>(8)
            for (i in inV.indices) {
                val a = inV[i]; val c = inV[(i + 1) % inV.size]
                val ain = a.second >= near; val cin = c.second >= near
                if (ain) out.add(a)
                if (ain != cin) {
                    val t = (near - a.second) / (c.second - a.second)
                    out.add(Pair(a.first + (c.first - a.first) * t, near))
                }
            }
            if (out.size < 3) continue
            val fi = allFaces.size; allFaces.add(face)
            val sx = FloatArray(out.size); val sy = FloatArray(out.size); val q = FloatArray(out.size)
            val attr = Array(out.size) { FloatArray(4) }
            for (i in out.indices) {
                val (p, zv) = out[i]; val d = p - cam.pos
                sx[i] = w / 2f + d.dot(cam.right) / zv * f
                sy[i] = cy - d.dot(cam.up) / zv * f
                q[i] = 1f / zv
                attr[i][0] = p.x * q[i]; attr[i][1] = p.y * q[i]; attr[i][2] = p.z * q[i]; attr[i][3] = 1f
            }
            polys.add(Poly(out.size, sx, sy, q, attr, fi))
        }

        // Každé vlákno rasterizuje všechny polygony jen do svého pásu řádků a hned ho vystínuje
        // (pásy se nepřekrývají → bez zámků; výsledek je stejný jako v jednom vlákně)
        val img = IntArray(w * h)
        parallelRows(h) { y0, y1 ->
            for (pl in polys) {
                val fi = pl.face
                raster(w, h, pl.n, pl.sx, pl.sy, pl.q, pl.attr, y0, y1) { idx, x, y, z, _ ->
                    val dx = x - cam.pos.x; val dy = y - cam.pos.y; val dz = z - cam.pos.z
                    val dd = dx * dx + dy * dy + dz * dz
                    if (dd < depth[idx]) { depth[idx] = dd; faceAt[idx] = fi; wx[idx] = x; wy[idx] = y; wz[idx] = z }
                }
            }
            // paprsek oblohy rozepsaný do složek (stejné pořadí operací jako Camera.ray)
            val fx = cam.fwd.x; val fy = cam.fwd.y; val fz = cam.fwd.z
            val rx = cam.right.x; val ry = cam.right.y; val rz = cam.right.z
            val ux = cam.up.x; val uy = cam.up.y; val uz = cam.up.z
            val fr = (w / 2f) / cam.fovTan
            for (py in y0..y1) {
                val b = (cy - (py + 0.5f)) / fr
                for (pxi in 0 until w) {
                    val idx = py * w + pxi
                    val fi = faceAt[idx]
                    if (fi < 0) {
                        val a = ((pxi + 0.5f) - w / 2f) / fr
                        var dx = fx + rx * a; var dy = fy + ry * a; var dz = fz + rz * a
                        dx += ux * b; dy += uy * b; dz += uz * b
                        val l = sqrt(dx * dx + dy * dy + dz * dz)
                        if (l != 0f) { val k = 1f / l; dx *= k; dy *= k; dz *= k }
                        img[idx] = sky(atm, dx, dy, dz, pxi, py, scene.seed); continue
                    }
                    img[idx] = shade(ctx, allFaces[fi], wx[idx], wy[idx], wz[idx], sqrt(depth[idx]))
                }
            }
        }
        return img
    }

    /** Předpočítané konstanty stínování (barvy světel jako floaty, pole místo seznamů). */
    private class ShadeCtx(val scene: Scene, val sun: V3, val shadow: ShadowMap) {
        val atm = scene.atmosphere
        val lr = ((atm.light shr 16) and 0xFF) / 255f; val lg = ((atm.light shr 8) and 0xFF) / 255f; val lb = (atm.light and 0xFF) / 255f
        val fr = ((atm.fog shr 16) and 0xFF) / 255f; val fg = ((atm.fog shr 8) and 0xFF) / 255f; val fb = (atm.fog and 0xFF) / 255f
        val nl = atm.lights.size
        val lx = FloatArray(nl) { atm.lights[it].pos.x }; val ly = FloatArray(nl) { atm.lights[it].pos.y }; val lz = FloatArray(nl) { atm.lights[it].pos.z }
        val lrad = FloatArray(nl) { atm.lights[it].radius }; val lstr = FloatArray(nl) { atm.lights[it].strength }
        val lcol = IntArray(nl) { atm.lights[it].color }
        val sunDot = FloatArray(6) { NORMALS[it].dot(sun) }
    }

    private fun shade(c: ShadeCtx, face: Face, px: Float, py: Float, pz: Float, dist: Float): Int {
        val atm = c.atm
        val mat = face.box.mat
        val base = Textures.texel(mat, face.box, face.n, px, py, pz, c.scene.seed)
        var r = ((base shr 16) and 0xFF) / 255f; var g = ((base shr 8) and 0xFF) / 255f; var b = (base and 0xFF) / 255f
        if (!mat.emissive) {
            val n = NORMALS[face.n]
            val faceShade = when (face.n) { 0 -> 1f; 5 -> 0.84f; 4 -> 0.62f; 1 -> 0.62f; else -> 0.72f }
            val lit = c.sunDot[face.n] > 0f && c.shadow.lit(px + n.x * 0.02f, py + n.y * 0.02f, pz + n.z * 0.02f)
            val sunAmt = if (lit) 1f else atm.ambient
            var kr = faceShade * sunAmt * c.lr; var kg = faceShade * sunAmt * c.lg; var kb = faceShade * sunAmt * c.lb
            for (i in 0 until c.nl) {
                val dx = px - c.lx[i]; val dy = py - c.ly[i]; val dz = pz - c.lz[i]
                val d = sqrt(dx * dx + dy * dy + dz * dz)
                val rad = c.lrad[i]
                if (d >= rad) continue
                val t = 1f - d / rad
                val fall = t * t * c.lstr[i]
                val col = c.lcol[i]
                kr += fall * ((col shr 16) and 0xFF) / 255f
                kg += fall * ((col shr 8) and 0xFF) / 255f
                kb += fall * (col and 0xFF) / 255f
            }
            r *= kr; g *= kg; b *= kb
        }
        // mlha
        val fogT = ((dist - atm.fogStart) / (atm.fogEnd - atm.fogStart)).coerceIn(0f, 1f) * atm.fogMax * (if (mat.emissive) 0.4f else 1f)
        r += (c.fr - r) * fogT; g += (c.fg - g) * fogT; b += (c.fb - b) * fogT
        return rgb(r, g, b)
    }

    private fun sky(atm: Atmosphere, dirX: Float, dirY: Float, dirZ: Float, x: Int, y: Int, seed: Int): Int {
        val t = (dirY * 2.2f).coerceIn(0f, 1f)
        var c = mix(atm.skyHorizon, atm.skyTop, t)
        if (atm.clouds && dirY > 0.02f) {
            // pixelové obláčky: šum na mřížce nad obzorem
            // hranaté obláčky: mřížka v úhlech (azimut × výška), bez perspektivního zkosení
            val az = Math.toDegrees(kotlin.math.atan2(dirX, dirZ).toDouble()).toFloat()
            val el = Math.toDegrees(kotlin.math.asin(dirY.coerceIn(-1f, 1f)).toDouble()).toFloat()
            if (el in 3f..24f) {
                val cw = 3.2f; val ch = 1.6f
                val gx = floor(az / cw).toInt(); val gy = floor(el / ch).toInt()
                val band = Textures.hash(gy / 3, 0, 79, seed) > 0.45f
                val a = Textures.hash(gx / 4, gy / 2, 77, seed)
                val b = Textures.hash(gx, gy, 78, seed)
                if (band && a > 0.55f && b > 0.25f) {
                    val under = Textures.hash(gx, gy - 1, 78, seed) <= 0.25f || Textures.hash(gx / 4, (gy - 1) / 2, 77, seed) <= 0.55f
                    c = if (under) mix(c, 0xFFDDE6F0.toInt(), 0.85f) else mix(c, 0xFFFFFFFF.toInt(), 0.9f)
                }
            }
        }
        if (atm.stars && Textures.hash(x, y, 5, seed) > 0.996f) c = 0xFFE8E8FF.toInt()
        return c
    }

    fun rgb(r: Float, g: Float, b: Float): Int =
        (0xFF shl 24) or ((r.coerceIn(0f, 1f) * 255).toInt() shl 16) or ((g.coerceIn(0f, 1f) * 255).toInt() shl 8) or (b.coerceIn(0f, 1f) * 255).toInt()

    fun mix(a: Int, b: Int, t: Float): Int =
        (0xFF shl 24) or (mixCh(a, b, 16, t) shl 16) or (mixCh(a, b, 8, t) shl 8) or mixCh(a, b, 0, t)

    private fun mixCh(a: Int, b: Int, s: Int, t: Float): Int =
        (((a shr s) and 0xFF) + (((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t).toInt().coerceIn(0, 255)
}
