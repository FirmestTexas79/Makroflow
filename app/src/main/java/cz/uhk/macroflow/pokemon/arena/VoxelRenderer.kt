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
        px: (Int, Float, Float, Float, Float) -> Unit
    ) {
        for (t in 1 until n - 1) {
            val a = 0; val b = t; val c = t + 1
            val area = (sx[b] - sx[a]) * (sy[c] - sy[a]) - (sy[b] - sy[a]) * (sx[c] - sx[a])
            if (abs(area) < 1e-6f) continue
            val minX = max(0, floor(min(sx[a], min(sx[b], sx[c]))).toInt())
            val maxX = min(w - 1, floor(max(sx[a], max(sx[b], sx[c]))).toInt())
            val minY = max(0, floor(min(sy[a], min(sy[b], sy[c]))).toInt())
            val maxY = min(h - 1, floor(max(sy[a], max(sy[b], sy[c]))).toInt())
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

    // ── Stínová mapa (ortogonální pohled ze slunce) ──

    private class ShadowMap(val sun: V3, boxes: List<Box>) {
        val size = 768
        val r: V3 = V3(0f, 1f, 0f).cross(sun).norm().let { if (it.len() < 0.5f) V3(1f, 0f, 0f) else it }
        val u: V3 = sun.cross(r).norm()
        var minA = Float.MAX_VALUE; var maxA = -Float.MAX_VALUE; var minB = Float.MAX_VALUE; var maxB = -Float.MAX_VALUE
        val depth = FloatArray(size * size) { Float.MAX_VALUE }

        init {
            val casters = boxes.filter { it.castsShadow }
            for (b in casters) for (f in faces(b)) for (v in f.v) {
                minA = min(minA, v.dot(r)); maxA = max(maxA, v.dot(r)); minB = min(minB, v.dot(u)); maxB = max(maxB, v.dot(u))
            }
            val sx = FloatArray(8); val sy = FloatArray(8); val q = FloatArray(8) { 1f }
            val attr = Array(8) { FloatArray(4) }
            for (b in casters) for (f in faces(b)) {
                for (i in 0 until 4) {
                    val (x, y) = toMap(f.v[i]); sx[i] = x; sy[i] = y
                    attr[i][3] = -f.v[i].dot(sun)
                }
                raster(size, size, 4, sx, sy, q, attr) { idx, _, _, _, d -> if (d < depth[idx]) depth[idx] = d }
            }
        }

        fun toMap(p: V3): Pair<Float, Float> =
            (p.dot(r) - minA) / (maxA - minA + 1e-3f) * size to (p.dot(u) - minB) / (maxB - minB + 1e-3f) * size

        fun lit(p: V3): Boolean {
            val (x, y) = toMap(p)
            val ix = x.toInt(); val iy = y.toInt()
            if (ix !in 0 until size || iy !in 0 until size) return true
            return -p.dot(sun) <= depth[iy * size + ix] + 0.04f
        }
    }

    /**
     * Vykreslí scénu do pole ARGB (w × h). [cy] = řádek středu pohledu – když je obraz nahoře
     * prodloužený (aréna až k hornímu okraji displeje), střed zůstává na stejném místě scény.
     */
    fun render(scene: Scene, cam: Camera, w: Int, h: Int, cy: Float = h / 2f): IntArray {
        val atm = scene.atmosphere
        val sun = atm.sun.norm()
        val shadow = ShadowMap(sun, scene.boxes)
        val depth = FloatArray(w * h) { Float.MAX_VALUE }
        val faceAt = IntArray(w * h) { -1 }
        val wx = FloatArray(w * h); val wy = FloatArray(w * h); val wz = FloatArray(w * h)
        val allFaces = ArrayList<Face>()
        val near = 0.1f
        val f = (w / 2f) / cam.fovTan

        val sx = FloatArray(8); val sy = FloatArray(8); val q = FloatArray(8)
        val attr = Array(8) { FloatArray(4) }
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
            for (i in out.indices) {
                val (p, zv) = out[i]; val d = p - cam.pos
                sx[i] = w / 2f + d.dot(cam.right) / zv * f
                sy[i] = cy - d.dot(cam.up) / zv * f
                q[i] = 1f / zv
                attr[i][0] = p.x * q[i]; attr[i][1] = p.y * q[i]; attr[i][2] = p.z * q[i]; attr[i][3] = 1f
            }
            raster(w, h, out.size, sx, sy, q, attr) { idx, x, y, z, _ ->
                val dx = x - cam.pos.x; val dy = y - cam.pos.y; val dz = z - cam.pos.z
                val dd = dx * dx + dy * dy + dz * dz
                if (dd < depth[idx]) { depth[idx] = dd; faceAt[idx] = fi; wx[idx] = x; wy[idx] = y; wz[idx] = z }
            }
        }

        val out = IntArray(w * h)
        for (py in 0 until h) for (pxi in 0 until w) {
            val idx = py * w + pxi
            val fi = faceAt[idx]
            if (fi < 0) { out[idx] = sky(atm, cam.ray(pxi + 0.5f, py + 0.5f, w, h, cy), pxi, py, scene.seed); continue }
            val face = allFaces[fi]
            val p = V3(wx[idx], wy[idx], wz[idx])
            out[idx] = shade(scene, face, p, sqrt(depth[idx]), sun, shadow)
        }
        return out
    }

    private fun shade(scene: Scene, face: Face, p: V3, dist: Float, sun: V3, shadow: ShadowMap): Int {
        val atm = scene.atmosphere
        val mat = face.box.mat
        val base = Textures.texel(mat, face.box, face.n, p, scene.seed)
        var r = ((base shr 16) and 0xFF) / 255f; var g = ((base shr 8) and 0xFF) / 255f; var b = (base and 0xFF) / 255f
        if (!mat.emissive) {
            val n = NORMALS[face.n]
            val faceShade = when (face.n) { 0 -> 1f; 5 -> 0.84f; 4 -> 0.62f; 1 -> 0.5f; else -> 0.72f }
            val sunDot = n.dot(sun)
            val lit = sunDot > 0f && shadow.lit(p + n * 0.02f)
            val sunAmt = if (lit) 1f else atm.ambient
            val lr = ((atm.light shr 16) and 0xFF) / 255f; val lg = ((atm.light shr 8) and 0xFF) / 255f; val lb = (atm.light and 0xFF) / 255f
            var kr = faceShade * sunAmt * lr; var kg = faceShade * sunAmt * lg; var kb = faceShade * sunAmt * lb
            for (l in atm.lights) {
                val d = (p - l.pos).len()
                if (d >= l.radius) continue
                val fall = (1f - d / l.radius).let { it * it } * l.strength
                kr += fall * ((l.color shr 16) and 0xFF) / 255f
                kg += fall * ((l.color shr 8) and 0xFF) / 255f
                kb += fall * (l.color and 0xFF) / 255f
            }
            r *= kr; g *= kg; b *= kb
        }
        // mlha
        val fogT = ((dist - atm.fogStart) / (atm.fogEnd - atm.fogStart)).coerceIn(0f, 1f) * atm.fogMax * (if (mat.emissive) 0.4f else 1f)
        val fr = ((atm.fog shr 16) and 0xFF) / 255f; val fg = ((atm.fog shr 8) and 0xFF) / 255f; val fb = (atm.fog and 0xFF) / 255f
        r += (fr - r) * fogT; g += (fg - g) * fogT; b += (fb - b) * fogT
        return rgb(r, g, b)
    }

    private fun sky(atm: Atmosphere, dir: V3, x: Int, y: Int, seed: Int): Int {
        val t = (dir.y * 2.2f).coerceIn(0f, 1f)
        var c = mix(atm.skyHorizon, atm.skyTop, t)
        if (atm.clouds && dir.y > 0.02f) {
            // pixelové obláčky: šum na mřížce nad obzorem
            val u = dir.x / dir.y * 1.1f; val v = dir.z / dir.y * 0.9f
            val a = Textures.hash(floor(u).toInt(), floor(v).toInt(), 77, seed)
            val b = Textures.hash(floor(u * 2f).toInt(), floor(v * 2f).toInt(), 78, seed)
            if (a > 0.72f && b > 0.3f) c = mix(c, 0xFFFFFFFF.toInt(), 0.6f * (1f - t * 0.5f))
        }
        if (atm.stars && Textures.hash(x, y, 5, seed) > 0.996f) c = 0xFFE8E8FF.toInt()
        return c
    }

    fun rgb(r: Float, g: Float, b: Float): Int =
        (0xFF shl 24) or ((r.coerceIn(0f, 1f) * 255).toInt() shl 16) or ((g.coerceIn(0f, 1f) * 255).toInt() shl 8) or (b.coerceIn(0f, 1f) * 255).toInt()

    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + (((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
