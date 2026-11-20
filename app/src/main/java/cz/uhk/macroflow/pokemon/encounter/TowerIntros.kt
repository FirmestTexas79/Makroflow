package cz.uhk.macroflow.pokemon.encounter

import cz.uhk.macroflow.pokemon.encounter.Kit.CLIMAX
import cz.uhk.macroflow.pokemon.encounter.Kit.PEAK
import cz.uhk.macroflow.pokemon.encounter.Kit.TELL1
import cz.uhk.macroflow.pokemon.encounter.Kit.TELL2
import cz.uhk.macroflow.pokemon.encounter.Kit.easeOut
import cz.uhk.macroflow.pokemon.encounter.Kit.hex
import cz.uhk.macroflow.pokemon.encounter.Kit.mix
import cz.uhk.macroflow.pokemon.encounter.Kit.p
import cz.uhk.macroflow.pokemon.encounter.Kit.pulse
import cz.uhk.macroflow.pokemon.encounter.Kit.since
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * Intra setkání pro patra Věže Řádu Váhy – každé patro má vlastní znamení a vyvrcholení.
 */

private fun burstDirs(rnd: kotlin.random.Random, n: Int, vMin: Float, vMax: Float, up: Float = 0f) = List(n) {
    val a = rnd.nextDouble() * PI * 2
    val v = vMin + rnd.nextFloat() * (vMax - vMin)
    floatArrayOf((cos(a) * v).toFloat(), (sin(a) * v).toFloat() - up, rnd.nextFloat(), rnd.nextFloat() * 6.28f)
}

/** Kalibrační louka: pravítko a měřidlo na stěně, kolíky v trávě; pod trávou se něco blíží a vyrazí ven. */
class TowerPlainScene(w: Int, h: Int) : KitScene(w, h, 3) {
    private val wall = hex(0xE8DCC0); private val wallD = hex(0xB8A888); private val ink = hex(0x3A3028); private val brass = hex(0xC8A040)
    private val grass = hex(0x5E9A48); private val grassL = hex(0x9ACC66); private val grassD = hex(0x386A30); private val grid = hex(0xD8F0A0)
    private val flag = hex(0xE04838); private val stakeC = hex(0xF4ECD8)
    private val wallY = (h * 0.4f).toInt()
    private val cx = w / 2f; private val cy = h * 0.66f
    private val blades = List(260) { floatArrayOf(rnd.nextFloat() * w, wallY + 4 + rnd.nextFloat() * (h - wallY), rnd.nextFloat() * 6.28f) }.sortedBy { it[1] }
    private val stakes = listOf(floatArrayOf(w * 0.18f, h * 0.55f), floatArrayOf(w * 0.82f, h * 0.58f), floatArrayOf(w * 0.3f, h * 0.84f), floatArrayOf(w * 0.75f, h * 0.9f))
    private val bits = burstDirs(rnd, 70, 40f, 140f, 60f)

    override fun paintBase() {
        vgrad(0, wallY, wall, wallD)
        for (x in 0 until w step 16) rect(x, 0, 1, wallY, wallD)                         // obložení
        rect(0, (h * 0.3f).toInt(), w, 7, hex(0xF6EED8)); rect(0, (h * 0.3f).toInt() + 7, w, 1, ink)   // pravítko
        for (x in 0 until w step 3) rect(x, (h * 0.3f).toInt(), 1, if (x % 15 == 0) 5 else 2, ink)
        circle(cx, h * 0.15f, 16f, brass); circle(cx, h * 0.15f, 14f, hex(0xFAF4E4))
        for (i in 0..10) { val a = PI * (1.1 + 0.08 * i); line(cx + (cos(a) * 11).toFloat(), h * 0.15f + (sin(a) * 11).toFloat(), cx + (cos(a) * 13).toFloat(), h * 0.15f + (sin(a) * 13).toFloat(), ink) }
        vgrad((h * 0.3f).toInt() + 8, wallY, hex(0x8A7458), hex(0x5A4A38)); rect(0, (h * 0.3f).toInt() + 8, w, 1, hex(0xC8B490))
        vgrad(wallY, h, grass, grassD)
        for (i in -6..6) line(cx + i * 6f, wallY.toFloat(), cx + i * 34f, h.toFloat(), grid, 0.25f)   // měřicí síť v trávě
        var y = wallY + 3f; var s = 3f; while (y < h) { line(0f, y, w.toFloat(), y, grid, 0.2f); s *= 1.35f; y += s }
        for (l in listOf(w * 0.25f, w * 0.75f)) glow(l, wallY + 10f, 30f, hex(0xFFF6D0), 0.25f, 0.4f)
    }

    override fun draw(t: Long) {
        // ručička měřidla – při znameních se rozkmitá
        val jit = (pulse(t, TELL1, 500) + pulse(t, TELL2, 500) * 1.5f + p(t, CLIMAX - 200, CLIMAX)) * sinf(t * 0.05f)
        val a = PI * 1.5 + 0.5 * jit + 0.3 * p(t, TELL1, CLIMAX)
        line(cx, h * 0.15f, cx + (cos(a) * 12).toFloat(), h * 0.15f + (sin(a) * 12).toFloat(), flag)
        // vlna v trávě: z levého dolního rohu ke středu
        val k = p(t, TELL1, CLIMAX)
        val wx = w * 0.1f + (cx - w * 0.1f) * k; val wy = h * 0.95f + (cy - h * 0.95f) * k
        val burst = since(t)
        for (b in blades) {
            val d = hypot(b[0] - wx, (b[1] - wy) * 1.5f)
            val bend = if (k in 0.01f..0.99f && d < 14f) (1f - d / 14f) * 3f else 0f
            val sway = sinf(t * 0.003f + b[2]) * 0.8f + bend
            val depth = (b[1] - wallY) / (h - wallY); val len = 2f + 5f * depth
            if (burst > 0f && hypot(b[0] - cx, b[1] - cy) < 22f) continue
            line(b[0], b[1], b[0] + sway, b[1] - len, if (bend > 0.5f) grassL else if (b[2] > 3f) grassL else grassD)
        }
        if (k in 0.01f..0.99f) ellipse(wx, wy, 8f, 3f, grassL, 0.35f)
        eyes(cx, cy - 2f, 4f, hex(0xFFF070), p(t, TELL2, TELL2 + 200) * (1f - p(t, CLIMAX, CLIMAX + 80)), 1f)
        // kolíky s praporky (po výbuchu se skácí)
        for ((i, st) in stakes.withIndex()) {
            val near = hypot(st[0] - wx, st[1] - wy) < 18f && k < 0.99f
            val tilt = (if (near) sinf(t * 0.05f) * 2f else 0f) + easeOut(p(t, CLIMAX, CLIMAX + 300)) * (if (st[0] < cx) -7f else 7f)
            val sc = 0.6f + 0.6f * (st[1] - wallY) / (h - wallY)
            val top = st[1] - 18f * sc
            line(st[0], st[1], st[0] + tilt, top, stakeC); line(st[0] + 1, st[1], st[0] + tilt + 1, top, wallD)
            rect((st[0] + tilt).toInt() + 1, top.toInt(), (6 * sc).toInt() + 1, (4 * sc).toInt() + 1, flag)
            if (i == 0) Unit
        }
        if (burst > 0f) {
            glow(cx, cy, 30f * easeOut(minOf(1f, burst * 2f)), grassL, 0.5f)
            ellipse(cx, cy, 20f, 6f, hex(0x2A2016))
            for (b in bits) { val (x, y) = ballistic(cx, cy, b[0], b[1], 160f, burst); line(x, y, x + b[0] / 60f, y - 3, if (b[2] > 0.5f) grassL else grass); if (b[2] > 0.8f) rect(x.toInt(), y.toInt(), 2, 2, hex(0x6A4A30)) }
        }
    }
}

/** Zarostlé patro: kartotéka obrostlá břečťanem; šuplík se pootevře, v něm oči – vystřelí ven s listím. */
class TowerForestScene(w: Int, h: Int) : KitScene(w, h, 4) {
    private val wall = hex(0x2E3A34); private val wallL = hex(0x44564A); private val win = hex(0xC8E8B0)
    private val floor = hex(0x3A3026); private val floorL = hex(0x524234)
    private val metal = hex(0x7A8478); private val metalL = hex(0xA4AE9E); private val metalD = hex(0x4A524A)
    private val leaf = hex(0x4E9A44); private val leafL = hex(0x8ACC5E); private val leafD = hex(0x2A5A2E); private val paper = hex(0xF2ECD8)
    private val floorY = (h * 0.62f).toInt()
    private val cx = w / 2f; private val top = h * 0.36f; private val cw = 38f; private val drawerH = (floorY - top) / 3f
    private val ivy = List(9) { floatArrayOf(rnd.nextFloat() * w, 20f + rnd.nextFloat() * 60f, rnd.nextFloat() * 6.28f) }
    private val bits = burstDirs(rnd, 70, 40f, 150f, 50f)
    private val drift = List(20) { floatArrayOf(rnd.nextFloat() * w, rnd.nextFloat(), 0.5f + rnd.nextFloat()) }

    override fun paintBase() {
        vgrad(0, floorY, wall, wallL)
        for (x in 0 until w step 20) rect(x, 0, 1, floorY, hex(0x26302A))
        rect((w * 0.12f).toInt(), (h * 0.08f).toInt(), 30, 40, win); rect((w * 0.62f).toInt(), (h * 0.08f).toInt(), 30, 40, win)
        for (wx in listOf(w * 0.12f, w * 0.62f)) { rect(wx.toInt() + 14, (h * 0.08f).toInt(), 2, 40, wall); rect(wx.toInt(), (h * 0.08f).toInt() + 19, 30, 2, wall) }
        for (y in 0 until floorY) for (x in 0 until w) {
            val edge = minOf(x, w - 1 - x) / (w * 0.5f) + y / (floorY * 1.6f)
            val n = fbm(x / 7f, y / 7f, 2) - edge * 0.55f
            if (n > 0.32f) put(x, y, if (n > 0.42f && hf(x, y) > 0.4f) leafL else if (n > 0.37f) leaf else leafD)
        }
        vgrad(floorY, h, floorL, floor)
        for (y in floorY until h step 7) rect(0, y, w, 1, floor)
        glow(w * 0.3f, h * 0.2f, 40f, win, 0.2f)
        // kartotéka
        rect((cx - cw / 2).toInt(), top.toInt(), cw.toInt(), (floorY + 4 - top).toInt(), metal)
        rect((cx - cw / 2).toInt(), top.toInt(), cw.toInt(), 2, metalL); rect((cx + cw / 2).toInt() - 3, top.toInt(), 3, (floorY + 4 - top).toInt(), metalD)
        for (i in 0 until 3) { val y = top + 3 + i * drawerH; rect((cx - cw / 2 + 3).toInt(), y.toInt(), (cw - 7).toInt(), (drawerH - 3).toInt(), metalL); rect((cx - 5).toInt(), (y + drawerH / 2 - 2).toInt(), 10, 2, metalD) }
        for (y in top.toInt() until floorY) for (x in (cx - cw / 2).toInt() until (cx + cw / 2).toInt()) if (fbm(x / 4f, y / 4f, 8) > 0.74f) put(x, y, hex(0x5E7A50))
    }

    override fun draw(t: Long) {
        // břečťan visí ze stropu a houpe se
        for (v in ivy) for (s in 0 until v[1].toInt()) {
            val x = v[0] + sinf(t * 0.002f + v[2] + s * 0.05f) * s * 0.06f; val y = s.toFloat()
            put(x.toInt(), y.toInt(), leafD); if (s % 5 == 2) { put(x.toInt() - 1, y.toInt(), leaf); put(x.toInt() + 1, y.toInt() + 1, leafL) }
        }
        // úponky kolem kartotéky se stahují
        val tight = pulse(t, TELL1, 450) + pulse(t, TELL2, 450)
        for (i in 0 until 4) {
            val y = top + 6 + i * drawerH * 0.8f
            for (x in (cx - cw / 2 - 3).toInt()..(cx + cw / 2 + 2).toInt()) {
                val yy = y + 2f * sinf(x * 0.4f + i + t * 0.003f) * (1f - tight * 0.7f)
                put(x, yy.toInt(), if ((x + i) % 6 == 0) leafL else leaf)
            }
        }
        // prostřední šuplík: pootevře se, pak vystřelí
        val y = top + 3 + drawerH
        val open = 2f * p(t, TELL1 + 200, TELL1 + 500) + 2f * p(t, TELL2, TELL2 + 200)
        val fly = since(t)
        if (fly <= 0f) {
            rect((cx - cw / 2 + 3).toInt(), y.toInt() - 1, (cw - 7).toInt(), open.toInt() + 1, BLACK)
            eyes(cx, y + 1.5f, 5f, hex(0xFFE060), p(t, TELL2 + 100, TELL2 + 300), 0.8f)
        } else {
            val k = easeOut(minOf(1f, fly * 2.5f))
            rect((cx - cw / 2 + 3).toInt(), y.toInt(), (cw - 7).toInt(), (drawerH - 3).toInt(), BLACK)
            // šuplík vyjíždí k divákovi: bok v perspektivě + čelo
            val dw = (cw - 7) * (1f + k * 1.1f); val dh = (drawerH - 3) * (1f + k * 1.1f); val dy = y + k * 46f
            for (yy in y.toInt() until dy.toInt()) { val q = (yy - y) / (dy - y + 0.01f); val hw = (cw - 7) / 2 + (dw - cw + 7) / 2 * q; rect((cx - hw).toInt(), yy, (hw * 2).toInt(), 1, metalD); rect((cx - hw + 2).toInt(), yy, (hw * 2 - 4).toInt(), 1, paper) }
            rect((cx - dw / 2).toInt(), dy.toInt(), dw.toInt(), dh.toInt(), metalL); rect((cx - dw / 2).toInt(), (dy + dh - 2).toInt(), dw.toInt(), 2, metalD)
            rect((cx - 8 * (1 + k)).toInt(), (dy + dh / 2 - 1).toInt(), (16 * (1 + k)).toInt(), 3, metalD)
            for (b in bits) { val (x, yy) = ballistic(cx, y + 4, b[0], b[1], 120f, fly); rect(x.toInt(), yy.toInt(), 2, if (b[2] > 0.6f) 1 else 2, if (b[2] > 0.7f) paper else if (b[2] > 0.35f) leafL else leaf) }
        }
        for (d in drift) put(((d[0] + sinf(t * 0.002f + d[2] * 4f) * 5f) % w).toInt(), (((d[1] + t * 0.00005f * d[2]) % 1f) * h).toInt(), leafL)
    }
}

/** Skalní patro: kamenné dlaždice, louče; od středu běží praskliny, pak se podlaha vzdme balvanem. */
class TowerRockScene(w: Int, h: Int) : KitScene(w, h, 5) {
    private val stone = hex(0x6A5E54); private val stoneL = hex(0x8E8274); private val stoneD = hex(0x3E3630); private val mortar = hex(0x2A2420)
    private val tile = hex(0x7A6E60); private val tileL = hex(0x9A8E7C); private val fire = hex(0xFFA040); private val glowC = hex(0xFFD080)
    private val floorY = (h * 0.45f).toInt()
    private val cx = w / 2f; private val cy = h * 0.7f
    private val cracks = List(7) { i -> floatArrayOf((i * 0.9f + rnd.nextFloat() * 0.5f), 18f + rnd.nextFloat() * 30f, rnd.nextInt(100).toFloat()) }
    private val shards = burstDirs(rnd, 40, 50f, 150f, 80f)
    private val dust = List(30) { floatArrayOf(rnd.nextFloat() * w, rnd.nextFloat() * h, rnd.nextFloat() * 6f) }

    override fun paintBase() {
        for (y in 0 until floorY) for (x in 0 until w) {                 // kvádrové zdivo
            val row = y / 9; val off = if (row % 2 == 0) 0 else 7
            val mx = (x + off) % 14 == 0 || y % 9 == 0
            put(x, y, if (mx) mortar else if (hf((x + off) / 14, row, 2) > 0.6f) stoneL else if (hf(x, y) > 0.92f) stoneD else stone)
        }
        for (y in floorY until h) {                                          // dlaždice v perspektivě
            val k = (y - floorY).toFloat() / (h - floorY)
            val v = kotlin.math.sqrt(k) * 7f; val vi = v.toInt()
            val hEdge = (v - vi) < 0.9f / (h - floorY) * 7f / (0.5f / kotlin.math.sqrt(k + 0.02f)) * 2f
            for (x in 0 until w) {
                val u = (x - cx) / (8f + 34f * k); val ui = kotlin.math.floor(u + 0.5f).toInt()
                val vEdge = abs(u - ui) > 0.5f - 0.5f / (8f + 34f * k)
                val base = if (hf(ui, vi, 4) > 0.5f) tile else tileL
                put(x, y, if (hEdge || vEdge) mortar else mix(mix(base, stoneD, hf(x / 3, y / 2, 9) * 0.25f), stoneD, (1f - k) * 0.55f))
            }
        }
        for (tx in listOf(w * 0.15f, w * 0.85f)) { rect(tx.toInt() - 1, (h * 0.18f).toInt(), 3, 10, stoneD); rect(tx.toInt() - 2, (h * 0.18f).toInt(), 5, 2, stoneD) }
    }

    override fun draw(t: Long) {
        for (tx in listOf(w * 0.15f, w * 0.85f)) {
            val f = 0.8f + 0.2f * sinf(t * 0.02f + tx)
            glow(tx, h * 0.16f, 28f * f, fire, 0.35f); ellipse(tx, h * 0.16f, 2f, 4f * f, fire); put(tx.toInt(), (h * 0.155f).toInt(), glowC)
        }
        // praskliny rostou ze středu (po dvou znameních)
        val g = 0.5f * p(t, TELL1, TELL1 + 300) + 0.5f * p(t, TELL2, TELL2 + 300)
        for (c in cracks) {
            val a = c[0]; val len = c[1] * g
            if (len < 1f) continue
            zigzag(cx, cy, cx + cos(a) * len, cy + sin(a) * len * 0.45f, 3f, c[2].toInt(), mortar, 1f, 5)
        }
        val heat = p(t, TELL2, CLIMAX)
        if (heat > 0f) { glow(cx, cy, 10f + 10f * heat, glowC, 0.4f * heat, 0.5f); eyes(cx, cy - 1, 4f, hex(0xFFC040), p(t, TELL2 + 150, TELL2 + 350) * (1f - p(t, CLIMAX, CLIMAX + 60)), 0.9f) }
        // poskakující kamínky
        for (i in 0 until 6) { val hop = pulse(t, TELL1 + i * 50L, 250) + pulse(t, TELL2 + i * 40L, 250); if (hop > 0f) rect((cx + (i - 2.5f) * 9).toInt(), (cy + (i % 3) * 4 - hop * 4).toInt(), 2, 2, stoneL) }
        // balvan se vzdme
        val s = since(t)
        if (s > 0f) {
            val r = 16f * easeOut(minOf(1f, s * 3f))
            ellipse(cx, cy + 4, r * 1.4f, r * 0.4f, BLACK, 0.6f)
            ellipse(cx, cy - r * 0.4f, r, r * 0.8f, stone); ellipse(cx - r * 0.3f, cy - r * 0.7f, r * 0.5f, r * 0.35f, stoneL)
            ellipse(cx + r * 0.4f, cy - r * 0.2f, r * 0.4f, r * 0.5f, stoneD)
            for (b in shards) { val (x, y) = ballistic(cx, cy, b[0], b[1], 200f, s); rect(x.toInt(), y.toInt(), 3, 2, if (b[2] > 0.5f) tileL else tile) }
            for (y in (cy - 40).toInt() until (cy + 20).toInt()) for (x in 0 until w) {
                val k = fbm(x / 7f, y / 5f + s, 6) - 0.95f + easeOut(minOf(1f, s * 1.5f)) * 0.7f - abs(x - cx) / w
                if (k > 0f && dither(x, y, k * 3f)) blend(x, y, stoneL, 0.6f)
            }
        }
        for (d in dust) { val y = (d[1] - t * 0.004f) % h; blend(((d[0] + 4f * sinf(t * 0.001f + d[2])) % w).toInt(), ((y + h) % h).toInt(), glowC, 0.35f) }
    }
}

/** Krystalová jeskyně: pulzující krystaly, mezi nimi přeskakují výboje; středový krystal se roztříští. */
class TowerCaveScene(w: Int, h: Int) : KitScene(w, h, 6) {
    private val dark = hex(0x120A20); private val rock = hex(0x2A1C3E); private val rockL = hex(0x40305A)
    private val vio = hex(0x9A5AE8); private val vioL = hex(0xD8B0FF); private val cyan = hex(0x5AE8F0); private val cyanL = hex(0xC8FFFF)
    private val cx = w / 2f; private val cy = h * 0.6f
    private data class Cr(val x: Float, val y: Float, val hgt: Float, val wid: Float, val tilt: Float, val c: Int)
    private val crystals = listOf(Cr(w * 0.14f, h * 0.7f, 40f, 7f, -0.25f, 0), Cr(w * 0.24f, h * 0.74f, 26f, 5f, 0.15f, 1), Cr(w * 0.82f, h * 0.68f, 46f, 8f, 0.2f, 1),
        Cr(w * 0.92f, h * 0.76f, 24f, 5f, -0.1f, 0), Cr(w * 0.3f, h * 0.9f, 20f, 5f, -0.3f, 1), Cr(w * 0.72f, h * 0.92f, 22f, 5f, 0.3f, 0))
    private val shards = burstDirs(rnd, 36, 50f, 170f, 40f)

    override fun paintBase() {
        for (y in 0 until h) for (x in 0 until w) {
            val n = fbm(x / 10f, y / 10f, 2)
            val edge = abs(x - cx) / (w * 0.5f) + (if (y < h * 0.3f) (h * 0.3f - y) / (h * 0.3f) else 0f)
            put(x, y, if (n + edge * 0.6f > 0.95f) (if (n > 0.6f) rockL else rock) else mix(dark, rock, n * 0.5f))
        }
        ellipse(cx, h * 0.93f, w * 0.6f, h * 0.12f, rock)
    }

    private fun crystal(x: Float, y: Float, hgt: Float, wid: Float, tilt: Float, c0: Int, c1: Int, a: Float = 1f) {
        for (s in 0 until hgt.toInt()) {
            val k = s / hgt; val half = wid * (1f - k * k) * 0.9f
            val px = x + tilt * s
            for (xx in (px - half).toInt()..(px + half).toInt()) {
                val c = if (xx < px - half * 0.2f) c1 else if (xx > px + half * 0.5f) mix(c0, BLACK, 0.35f) else c0
                if (a >= 1f) put(xx, (y - s).toInt(), c) else blend(xx, (y - s).toInt(), c, a)
            }
        }
    }

    override fun draw(t: Long) {
        val pulseAll = 0.5f + 0.5f * sinf(t * 0.004f)
        val s = since(t)
        for (c in crystals) {
            val (c0, c1) = if (c.c == 0) vio to vioL else cyan to cyanL
            glow(c.x + c.tilt * c.hgt * 0.5f, c.y - c.hgt * 0.5f, c.hgt * 0.8f, c0, 0.25f + 0.15f * pulseAll)
            crystal(c.x, c.y, c.hgt, c.wid, c.tilt, c0, c1)
        }
        // výboje mezi krystaly
        for ((i, at) in listOf(TELL1, TELL1 + 180, TELL2, TELL2 + 140, TELL2 + 300, CLIMAX - 120).withIndex()) {
            val a = pulse(t, at, 160); if (a <= 0f) continue
            val c1 = crystals[i % crystals.size]; val c2 = crystals[(i + 2) % crystals.size]
            zigzag(c1.x + c1.tilt * c1.hgt, c1.y - c1.hgt, cx, cy - 30, 5f, i * 7 + (t / 50).toInt(), cyanL, a, 8)
            zigzag(cx, cy - 30, c2.x + c2.tilt * c2.hgt, c2.y - c2.hgt, 5f, i * 5 + (t / 50).toInt(), cyanL, a, 8)
        }
        // psychické kruhy
        for (at in listOf(TELL1 + 100, TELL2 + 100)) { val k = p(t, at, at + 700); if (k in 0.01f..0.99f) ring(cx, cy - 30, 6f + k * 60f, 0.7f, vioL, 0.5f * (1f - k)) }
        // středový krystal: oči v něm, pak se roztříští
        if (s <= 0f) {
            val q = p(t, TELL2, CLIMAX)
            glow(cx, cy - 30, 30f + 10f * q, vio, 0.4f + 0.4f * q)
            crystal(cx, cy, 62f, 12f, 0f, vio, vioL)
            eyes(cx, cy - 32, 4f, cyanL, p(t, TELL2 + 100, TELL2 + 300), 1f)
        } else {
            glow(cx, cy - 30, 70f * easeOut(minOf(1f, s * 2f)), vioL, 0.7f * (1f - minOf(1f, s * 1.2f)))
            for (b in shards) { val (x, y) = ballistic(cx, cy - 30, b[0], b[1], 60f, s); crystal(x, y, 6f + b[2] * 6f, 2f, b[0] / 200f, if (b[2] > 0.5f) vio else cyan, vioL) }
        }
    }
}

/** Zatopené patro: voda až po kolena kanceláře, kape ze stropu, plavou papíry; pod hladinou krouží stín a vyrazí gejzír. */
class TowerWaterScene(w: Int, h: Int) : KitScene(w, h, 7) {
    private val tileC = hex(0xC8DCD8); private val tileD = hex(0x8AA8A8); private val stain = hex(0x6A8A84)
    private val water = hex(0x1E5A6A); private val waterD = hex(0x0E2E3E); private val waterL = hex(0x5AAAB8); private val foam = hex(0xE0FAFF)
    private val lampC = hex(0xFFF2C0); private val paper = hex(0xF0ECDC); private val door = hex(0x5A4636)
    private val waterY = (h * 0.5f).toInt()
    private val cx = w / 2f; private val cy = h * 0.72f
    private val drops = List(5) { floatArrayOf(w * (0.15f + rnd.nextFloat() * 0.7f), rnd.nextFloat() * 900f) }
    private val papers = List(5) { floatArrayOf(rnd.nextFloat() * w, waterY + 10f + rnd.nextFloat() * (h - waterY - 20), rnd.nextFloat() * 6f) }
    private val spray = burstDirs(rnd, 80, 30f, 130f, 120f)

    override fun paintBase() {
        for (y in 0 until waterY) for (x in 0 until w) put(x, y, if (x % 10 == 0 || y % 10 == 0) tileD else tileC)
        for (y in (waterY - 14) until waterY) for (x in 0 until w) blend(x, y, stain, 0.4f + 0.3f * hf(x, 3))
        rect((w * 0.68f).toInt(), (h * 0.24f).toInt(), 24, waterY - (h * 0.24f).toInt(), door); rect((w * 0.68f).toInt() + 19, (h * 0.36f).toInt(), 2, 2, hex(0xC8A040))
        for (y in waterY until h) { val k = (y - waterY).toFloat() / (h - waterY); for (x in 0 until w) put(x, y, mix(water, waterD, k)) }
        line(cx, 0f, cx, h * 0.08f, hex(0x303030))
    }

    override fun draw(t: Long) {
        // blikající lampa a její odraz
        val flick = if ((pulse(t, TELL1, 300) > 0.2f || pulse(t, TELL2, 400) > 0.2f) && (t / 70) % 3 == 0L) 0.15f else 1f
        glow(cx, h * 0.1f, 34f, lampC, 0.45f * flick); ellipse(cx, h * 0.1f, 6f, 3f, lampC, flick); rect((cx - 7).toInt(), (h * 0.085f).toInt(), 14, 2, hex(0x405050))
        for (y in waterY until (waterY + 40)) { val off = 3f * sinf(y * 0.8f + t * 0.006f); rect((cx - 5 + off).toInt(), y, 10 - (y - waterY) / 5, 1, lampC, 0.4f * flick * (1f - (y - waterY) / 40f)) }
        // vlnky na hladině
        for (y in waterY until h step 3) for (x in 0 until w) if (sinf(x * 0.25f + y * 0.9f + t * 0.004f) > 0.92f) blend(x, y, waterL, 0.5f)
        rect(0, waterY, w, 1, waterL)
        // kapky ze stropu a kruhy
        for (d in drops) {
            val ph = ((t + d[1].toLong()) % 900) / 900f
            if (ph < 0.4f) put(d[0].toInt(), (ph / 0.4f * waterY).toInt(), foam)
            else { val k = (ph - 0.4f) / 0.6f; ring(d[0], waterY + 6f + d[1] / 60f, 1f + k * 10f, 0.5f, foam, 0.5f * (1f - k), 0.35f) }
        }
        // stín krouží pod hladinou, bubliny, oči
        val circ = p(t, TELL1, CLIMAX)
        if (t < CLIMAX + 100) {
            val a = t * 0.004f; val r = 26f * (1f - circ * 0.8f)
            ellipse(cx + cosf(a) * r, cy + sinf(a) * r * 0.3f, 10f, 4f, waterD, 0.7f)
            for (i in 0 until 6) { val by = cy - ((t * 0.04f + i * 13) % 30); if (circ > 0.2f) ring(cx + sinf(i * 2f + t * 0.01f) * 4f, by, 1.2f, 0.5f, foam, 0.8f) }
            eyes(cx + cosf(a) * r, cy + sinf(a) * r * 0.3f, 3.5f, hex(0xFFE480), p(t, TELL2, TELL2 + 200), 0.9f)
        }
        // plovoucí papíry
        val s = since(t)
        for (pp in papers) {
            val bob = sinf(t * 0.003f + pp[2]) * 1.5f
            if (s > 0f) { val (x, y) = ballistic(pp[0], pp[1], (pp[0] - cx) * 1.5f, -110f, 160f, s); rect(x.toInt(), y.toInt(), 5, 3, paper); continue }
            rect(pp[0].toInt(), (pp[1] + bob).toInt(), 6, 2, paper); rect(pp[0].toInt(), (pp[1] + bob).toInt() + 2, 6, 1, tileD)
        }
        // gejzír
        if (s > 0f) {
            val hh = 120f * easeOut(minOf(1f, s * 2.5f)) * (1f - p(t, PEAK + 300, Kit.END) * 0.5f)
            for (y in (cy - hh).toInt() until cy.toInt()) {
                val k = (cy - y) / hh; val half = 7f + 4f * sinf(y * 0.5f + t * 0.03f) * k
                for (x in (cx - half).toInt()..(cx + half).toInt()) put(x, y, if (abs(x - cx) < half * 0.4f) foam else waterL)
            }
            for (b in spray) { val (x, y) = ballistic(cx, cy - hh * 0.8f, b[0], b[1], 180f, s); put(x.toInt(), y.toInt(), if (b[2] > 0.5f) foam else waterL) }
            ring(cx, cy, 8f + s * 40f, 1f, foam, 0.6f * (1f - minOf(1f, s * 1.5f)), 0.35f)
        }
    }
}

/** Dormitoř: postele, svíčky, okno s měsícem; svíčky zhasínají, prostěradlo se zvedne a odletí. */
class TowerDormScene(w: Int, h: Int) : KitScene(w, h, 8) {
    private val wall = hex(0x1A1E3A); private val wallL = hex(0x2A3058); private val floor = hex(0x2A2030); private val floorL = hex(0x3A2E40)
    private val moon = hex(0xC8D4FF); private val bedF = hex(0x3A2A2A); private val sheet = hex(0xB8BEDA); private val sheetD = hex(0x7880A8)
    private val flame = hex(0xFFC860); private val wax = hex(0xE8E0D0); private val ghost = hex(0xE6E8FF)
    private val floorY = (h * 0.45f).toInt()
    private val cx = w / 2f; private val cy = h * 0.74f
    private val candles = listOf(floatArrayOf(w * 0.15f, h * 0.55f, 600f), floatArrayOf(w * 0.85f, h * 0.55f, 900f), floatArrayOf(w * 0.08f, h * 0.82f, 1250f), floatArrayOf(w * 0.92f, h * 0.84f, 1500f))
    private val wisps = List(14) { floatArrayOf(rnd.nextFloat() * 6.28f, 10f + rnd.nextFloat() * 40f, rnd.nextFloat()) }

    override fun paintBase() {
        vgrad(0, floorY, wall, wallL)
        rect((cx - 14).toInt(), (h * 0.08f).toInt(), 28, 36, hex(0x0A0C1C)); circle(cx + 4, h * 0.13f, 5f, moon)
        for (i in 0..3) rect((cx - 14 + i * 9).toInt(), (h * 0.08f).toInt(), 1, 36, wallL)
        for (y in floorY until h) for (x in 0 until w) put(x, y, if ((x + (y - floorY) / 2) % 12 == 0) floor else mix(floorL, floor, (y - floorY).toFloat() / (h - floorY)))
        for (y in floorY until h) { val k = (y - floorY).toFloat() / (h - floorY); for (x in (cx - 6 - k * 30).toInt()..(cx + 6 + k * 30).toInt()) blend(x, y, moon, 0.12f * (1f - k)) }
        // řady postelí po stranách
        for (side in intArrayOf(-1, 1)) for (i in 0 until 3) {
            val k = 0.3f + i * 0.3f; val by = floorY + (h - floorY) * k * 0.7f; val bx = cx + side * (20f + k * 34f)
            val bw = 16f + 14f * k; val bh = 8f + 8f * k
            bed(bx, by, bw, bh)
        }
        rect((cx - 24).toInt(), (cy - 22).toInt(), 48, 6, bedF); rect((cx - 24).toInt(), (cy - 22).toInt(), 48, 1, hex(0x5A4040))   // čelo prostřední postele
        rect((cx - 24).toInt(), (cy - 4).toInt(), 48, 14, bedF); rect((cx - 24).toInt(), (cy + 10).toInt(), 3, 4, bedF); rect((cx + 21).toInt(), (cy + 10).toInt(), 3, 4, bedF)
        ellipse(cx, cy - 13f, 10f, 3f, hex(0xE8EAF4))
    }

    private fun bed(bx: Float, by: Float, bw: Float, bh: Float) {
        val x0 = (bx - bw / 2).toInt(); val y0 = (by - bh).toInt()
        rect(x0, y0 - 3, bw.toInt(), 3, bedF)                                              // čelo
        rect(x0, y0, bw.toInt(), bh.toInt(), sheetD); rect(x0, y0, bw.toInt(), (bh * 0.3f).toInt(), hex(0xD8DCEC))   // polštář
        rect(x0, (y0 + bh * 0.35f).toInt(), bw.toInt(), 1, sheet); rect(x0, by.toInt(), bw.toInt(), 2, bedF)
    }

    override fun draw(t: Long) {
        // svíčky – každá v jiném okamžiku zhasne (kouř)
        for (c in candles) {
            val out = t > c[2]
            rect(c[0].toInt() - 1, c[1].toInt(), 3, 6, wax)
            if (!out) { val f = 0.85f + 0.15f * sinf(t * 0.03f + c[0]); glow(c[0], c[1] - 3, 18f * f, flame, 0.4f); ellipse(c[0], c[1] - 2, 1f, 2.5f * f, flame) }
            else { val s = (t - c[2]) / 1000f; for (i in 0 until 6) blend((c[0] + sinf(s * 4f + i) * 2f).toInt(), (c[1] - 2 - i * 2 - s * 10f).toInt(), sheetD, 0.6f * (1f - minOf(1f, s * 1.5f))) }
        }
        // prostěradlo na prostřední posteli: zvedá se do tvaru ducha
        val rise = 0.35f * pulse(t, TELL1, 600) + p(t, TELL2, CLIMAX) * 0.8f
        val fly = since(t)
        if (fly <= 0f) {
            val bump = 4f + 12f * rise
            for (x in (cx - 22).toInt() until (cx + 22).toInt()) {
                val k = 1f - abs(x - cx) / 22f; val top = cy - 6 - bump * k * k
                for (y in top.toInt() until (cy + 6).toInt()) put(x, y, if (x < cx - 6 && y < top + 3) ghost else if (x > cx + 10) sheetD else sheet)
            }
            eyes(cx, cy - 6 - bump * 0.7f, 4f, hex(0x9AB0FF), p(t, TELL2 + 150, TELL2 + 350), 0.9f)
        } else {
            val k = easeOut(minOf(1f, fly * 1.8f))
            val gy = cy - 10 - k * 120f; val gx = cx + sinf(fly * 6f) * 8f
            val fa = 0.95f * (1f - minOf(1f, fly * 0.7f))
            for (y in (gy - 20).toInt() until (gy + 20).toInt()) {
                val q = (y - gy + 20) / 40f
                val half = if (q < 0.4f) 13f * kotlin.math.sqrt(1f - ((0.4f - q) / 0.4f).let { it * it }) else 13f + (q - 0.4f) * 14f
                for (x in (gx - half).toInt()..(gx + half).toInt()) {
                    val hem = q > 0.86f && sinf((x - gx) * 0.9f + t * 0.02f) < (q - 0.86f) * 14f - 1f
                    if (!hem) blend(x, y, if (x < gx - half * 0.3f) ghost else if (x > gx + half * 0.5f) sheetD else sheet, fa)
                }
            }
            for (s in intArrayOf(-1, 1)) { ellipse(gx + s * 4.5f, gy - 9f, 2f, 3f, hex(0x1A1E3A), fa); }
            for (wsp in wisps) { val a = wsp[0] + fly * 5f; blend((cx + cosf(a) * wsp[1] * (1 + fly)).toInt(), (cy - 10 + sinf(a) * wsp[1] * 0.5f - fly * 40f).toInt(), ghost, 0.8f) }
            glow(gx, gy, 40f, ghost, 0.4f * (1f - minOf(1f, fly)))
        }
    }
}

/** Mrazírna: ocelové stěny, rampouchy, vánice; od okrajů leze jinovatka, v ledovém kvádru se rozsvítí oči a kvádr praskne. */
class TowerIceScene(w: Int, h: Int) : KitScene(w, h, 9) {
    private val steel = hex(0x56687A); private val steelL = hex(0x7E92A6); private val steelD = hex(0x3A4858); private val rivet = hex(0xA8BAD0)
    private val ice = hex(0x9ADCF0); private val iceL = hex(0xDAF6FF); private val iceD = hex(0x5AA8C8); private val frost = hex(0xF0FAFF)
    private val floorC = hex(0x8AB0C8); private val inner = hex(0x2A4A6A)
    private val floorY = (h * 0.5f).toInt()
    private val cx = w / 2f; private val cy = h * 0.68f
    private val bw = 40f; private val bh = 44f
    private val snow = List(70) { floatArrayOf(rnd.nextFloat() * w, rnd.nextFloat() * h, 0.5f + rnd.nextFloat()) }
    private val shards = burstDirs(rnd, 40, 50f, 160f, 60f)
    private val frostN = FloatArray(w * h)

    override fun paintBase() {
        for (y in 0 until h) for (x in 0 until w) {
            val e = minOf(minOf(x, w - 1 - x).toFloat() / w, minOf(y, h - 1 - y).toFloat() / h * 0.5f)
            frostN[y * w + x] = if (hf(x, y, 1) > 0.25f && e < 0.2f) fbm(x / 5f, y / 5f, 7) * 0.5f - 0.62f else -9f
        }
        for (y in 0 until floorY) for (x in 0 until w) {
            val panel = x % 32 == 0 || y % 40 == 0
            put(x, y, if (panel) steelD else if ((x % 32 == 3 || x % 32 == 29) && y % 40 in setOf(4, 36)) rivet else mix(steel, steelL, fbm(x / 20f, y / 30f, 2) * 0.6f))
        }
        rect(0, 6, w, 4, steelD); rect(0, 6, w, 1, rivet)                                // trubka pod stropem
        for (x in 3 until w step 5) { val l = 3 + (hf(x, 1) * 9).toInt(); for (i in 0 until l) for (xx in 0 until (2 - i * 2 / l)) put(x + xx, 10 + i, if (i < l / 2) iceL else ice) }
        for (y in floorY until h) for (x in 0 until w) put(x, y, mix(floorC, iceL, fbm(x / 9f, y / 4f, 5) * 0.5f))
        ellipse(cx, cy + 2, bw * 0.7f, 5f, iceD)
    }

    override fun draw(t: Long) {
        val s = since(t)
        // ledový kvádr s tvarem uvnitř
        if (s <= 0f) {
            val x0 = (cx - bw / 2).toInt(); val y0 = (cy - bh).toInt()
            for (y in y0 until cy.toInt()) for (x in x0 until x0 + bw.toInt()) {
                val e = x == x0 || y == y0 || x == x0 + bw.toInt() - 1
                put(x, y, if (e) iceL else if (x - x0 < 4 || y - y0 < 3) iceL else if (x - x0 > bw - 6) iceD else ice)
            }
            ellipse(cx, cy - bh * 0.5f, 10f, 12f, inner, 0.7f); ellipse(cx - 6, cy - bh * 0.75f, 3f, 4f, inner, 0.7f); ellipse(cx + 6, cy - bh * 0.75f, 3f, 4f, inner, 0.7f)
            val crack = 0.5f * p(t, TELL1, TELL1 + 250) + 0.5f * p(t, TELL2, TELL2 + 300)
            if (crack > 0f) { zigzag(cx - 4, cy - bh, cx + 6 * crack, cy - bh + bh * crack, 3f, 3, frost, 1f, 6); zigzag(cx + bw / 2, cy - bh * 0.6f, cx + bw / 2 - 16 * crack, cy - bh * 0.6f + 10 * crack, 2f, 8, frost, 1f, 4) }
            eyes(cx, cy - bh * 0.52f, 4.5f, hex(0x80F0FF), p(t, TELL2 + 100, TELL2 + 300), 1f)
        } else {
            glow(cx, cy - bh / 2, 60f * easeOut(minOf(1f, s * 2f)), iceL, 0.6f * (1f - minOf(1f, s)))
            for (b in shards) {
                val (x, y) = ballistic(cx, cy - bh / 2, b[0], b[1], 150f, s)
                val l = 3f + b[2] * 5f
                line(x, y, x + cosf(b[3] + s * 8f) * l, y + sinf(b[3] + s * 8f) * l, if (b[2] > 0.5f) iceL else ice)
            }
        }
        // jinovatka leze od okrajů
        val fr = 0.15f + 0.35f * p(t, TELL1, CLIMAX) + 0.3f * p(t, CLIMAX, PEAK)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; if (frostN[i] < -5f) continue
            val e = minOf(minOf(x, w - 1 - x).toFloat() / w, minOf(y, h - 1 - y).toFloat() / h * 0.5f)
            if (frostN[i] + (0.5f - e * 4f) * fr * 2f > 0f) blend(x, y, frost, 0.75f)
        }
        // vánice
        val gust = 1f + 1.5f * (pulse(t, TELL1, 500) + pulse(t, TELL2, 500)) + 3f * p(t, CLIMAX, CLIMAX + 200)
        for (f in snow) {
            val x = ((f[0] - t * 0.03f * f[2] * gust) % w + w) % w
            val y = (f[1] + t * 0.01f * f[2] + sinf(t * 0.003f + f[0]) * 3f) % h
            put(x.toInt(), y.toInt(), frost); if (gust > 2f) put(x.toInt() + 1, y.toInt(), frost)
        }
    }
}

/** Spalovna spisů: kamna se mřížkou, létají hořící papíry; dvířka se rozrachtají, v mřížce oči, pak vyšlehne plamen. */
class TowerFireScene(w: Int, h: Int) : KitScene(w, h, 10) {
    private val brick = hex(0x5A2A20); private val brickL = hex(0x7A3A2A); private val mortar = hex(0x2A1410)
    private val iron = hex(0x2E2A2C); private val ironL = hex(0x58504E); private val ironD = hex(0x161214)
    private val fireC = hex(0xFF7A20); private val fireL = hex(0xFFD050); private val fireW = hex(0xFFF4C0); private val paper = hex(0xE8DCC0)
    private val floorY = (h * 0.7f).toInt()
    private val cx = w / 2f; private val doorY = h * 0.48f; private val dw = 30f; private val dh = 34f
    private val embers = List(50) { floatArrayOf(rnd.nextFloat() * w, rnd.nextFloat() * h, 0.5f + rnd.nextFloat(), rnd.nextFloat() * 6f) }
    private val scraps = List(10) { floatArrayOf((rnd.nextFloat() - 0.5f) * 100f, -30f - rnd.nextFloat() * 60f, rnd.nextFloat() * 400f, rnd.nextFloat() * 6f) }
    private val sparks = burstDirs(rnd, 70, 50f, 180f, 30f)

    override fun paintBase() {
        for (y in 0 until floorY) for (x in 0 until w) { val row = y / 6; val off = if (row % 2 == 0) 0 else 6; put(x, y, if (y % 6 == 0 || (x + off) % 12 == 0) mortar else if (hf((x + off) / 12, row, 3) > 0.6f) brickL else brick) }
        vgrad(floorY, h, hex(0x2A1A16), hex(0x120A08))
        rect((cx - 5).toInt(), 0, 10, (doorY - dh).toInt(), iron); rect((cx - 5).toInt(), 0, 2, (doorY - dh).toInt(), ironL)   // komín
        for (y in (doorY - dh - 10).toInt() until (floorY + 6)) {                                     // tělo kamen
            val k = ((y - (doorY - dh - 10)) / 12f).coerceAtMost(1f); val half = 22f + 8f * k
            for (x in (cx - half).toInt()..(cx + half).toInt()) put(x, y, if (x < cx - half + 3) ironL else if (x > cx + half - 5) ironD else iron)
        }
        rect((cx - 34).toInt(), floorY + 4, 68, 4, ironD)
    }

    override fun draw(t: Long) {
        val g = 0.5f + 0.15f * sinf(t * 0.01f) + 0.35f * pulse(t, TELL1, 500) + 0.5f * p(t, TELL2, CLIMAX)
        glow(cx, doorY - dh / 2, 50f, fireC, 0.3f * g)
        for (y in floorY until h) for (x in 0 until w) if (dither(x, y, 0.25f * g * (1f - abs(x - cx) / w * 2f).coerceAtLeast(0f))) blend(x, y, fireC, 0.3f)
        val s = since(t)
        val rattle = (pulse(t, TELL1, 400) + pulse(t, TELL2, 500) * 1.5f) * sinf(t * 0.11f) * 1.5f
        // otvor s ohněm
        for (y in (doorY - dh).toInt() until doorY.toInt()) for (x in (cx - dw / 2).toInt() until (cx + dw / 2).toInt()) {
            val n = fbm(x / 5f, (y + t * 0.05f) / 5f, 4) + (y - (doorY - dh)) / dh * 0.6f
            put(x, y, if (n > 1.05f) fireW else if (n > 0.85f) fireL else if (n > 0.6f) fireC else hex(0x601808))
        }
        if (s <= 0f) {
            // dvířka s mřížkou (chrastí)
            val x0 = cx - dw / 2 + rattle
            for (y in (doorY - dh).toInt() until doorY.toInt()) for (x in x0.toInt() until (x0 + dw).toInt()) {
                val bar = (x - x0.toInt()) % 5 < 3 || y < doorY - dh + 3 || y > doorY - 4
                if (bar) put(x, y, if (x < x0 + 2) ironL else iron)
            }
            eyes(cx + rattle, doorY - dh * 0.55f, 5f, fireW, p(t, TELL2 + 100, TELL2 + 300), 1f)
        } else {
            // dvířka se rozletí a vyšlehne plamen
            val k = easeOut(minOf(1f, s * 4f))
            rect((cx - dw / 2 - dw * k).toInt(), (doorY - dh).toInt(), (dw * (1f - k * 0.7f)).toInt().coerceAtLeast(2), dh.toInt(), ironL)
            val fl = easeOut(minOf(1f, s * 2.5f)) * (1f - p(t, PEAK + 300, Kit.END) * 0.6f)
            for (y in (doorY - dh - 30 * fl).toInt() until (doorY + 70 * fl).toInt()) for (x in (cx - 50 * fl).toInt()..(cx + 50 * fl).toInt()) {
                val d = hypot((x - cx) / (50f * fl + 1f), (y - doorY + dh / 2 - 20 * fl) / (60f * fl + 1f))
                val n = fbm(x / 6f, (y + t * 0.12f) / 6f, 9)
                val k2 = (1f - d) * 1.8f + n - 0.9f
                if (k2 > 0f) put(x, y, if (k2 > 0.8f) fireW else if (k2 > 0.4f) fireL else fireC)
            }
            for (b in sparks) { val (x, y) = ballistic(cx, doorY - dh / 2, b[0], b[1], 80f, s); put(x.toInt(), y.toInt(), if (b[2] > 0.5f) fireL else fireW) }
        }
        // hořící útržky spisů z mřížky
        for (sc in scraps) {
            val start = TELL1 + sc[2].toLong(); if (t < start) continue
            val q = (t - start) / 1000f; if (q > 1.5f) continue
            val x = cx + sc[0] * q + sinf(q * 6f + sc[3]) * 4f; val y = doorY - dh / 2 + sc[1] * q
            rect(x.toInt(), y.toInt(), 3, 2, mix(paper, BLACK, q * 0.5f)); put(x.toInt() + 3, y.toInt(), fireL)
        }
        for (e in embers) { val y = ((e[1] - t * 0.025f * e[2]) % h + h) % h; val x = e[0] + sinf(t * 0.003f + e[3]) * 4f; if ((t / 90 + e[3].toLong()) % 4 != 0L) put(x.toInt(), y.toInt(), if (e[2] > 1f) fireL else fireC) }
    }
}

/** Duhová zahrada: duha, jetel, kruh hub a víly; houby se rozsvítí jedna po druhé a kruh vybuchne jiskrami. */
class TowerFairyScene(w: Int, h: Int) : KitScene(w, h, 11) {
    private val sky0 = hex(0xF8C8E0); private val sky1 = hex(0xC8E4FF)
    private val bow = intArrayOf(hex(0xF04848), hex(0xF8A030), hex(0xF8E048), hex(0x60C860), hex(0x4890F0), hex(0x8860D8))
    private val lawn = hex(0x5ABA5A); private val lawnL = hex(0x8AD87A); private val lawnD = hex(0x3A8A44); private val clover = hex(0x2E7A3A)
    private val cap = hex(0xE84848); private val stem = hex(0xF4ECDC); private val pix = hex(0xFFF6A0); private val pink = hex(0xFFB0E0)
    private val groundY = (h * 0.5f).toInt()
    private val cx = w / 2f; private val cy = h * 0.72f
    private val pixies = List(7) { floatArrayOf(rnd.nextFloat() * 6.28f, 20f + rnd.nextFloat() * 30f, 0.6f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 20f) }
    private val stars = burstDirs(rnd, 60, 30f, 140f, 60f)

    override fun paintBase() {
        vgrad(0, groundY, sky0, sky1)
        for ((i, c) in bow.withIndex()) ring(cx, groundY + 10f, 70f - i * 3f, 1.4f, c, 0.85f, 0.9f)
        for (y in 0 until groundY) for (x in 0 until w) if (fbm(x / 18f, y / 6f, 2) > 0.66f) blend(x, y, WHITE, 0.7f)
        for (y in groundY until h) for (x in 0 until w) put(x, y, if (fbm(x / 14f, y / 5f, 3) > 0.62f) lawnL else if (hf(x, y) > 0.96f) lawnD else lawn)
        for (i in 0 until 26) {                                                   // jetel (čtyřlístky)
            val x = hf(i, 1) * w; val y = groundY + 6 + hf(i, 2) * (h - groundY - 8)
            for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) ellipse(x + dx * 1.5f, y + dy * 1.2f, 1.3f, 1f, clover)
        }
        ellipse(cx, cy, 44f, 13f, lawnD, 0.5f)
    }

    override fun draw(t: Long) {
        val s = since(t)
        // kruh hub – rozsvěcují se dokola
        val lit = p(t, TELL1, CLIMAX) * 10f
        for (i in 0 until 10) {
            val a = PI * 2 * i / 10 - PI / 2
            val x = cx + (cos(a) * 42).toFloat(); val y = cy + (sin(a) * 12).toFloat()
            val on = (lit - i).coerceIn(0f, 1f)
            if (on > 0f) glow(x, y - 5, 11f, pix, 0.7f * on)
            rect(x.toInt() - 1, y.toInt() - 4, 3, 5, stem); ellipse(x, y - 5, 5f, 3f, mix(cap, pink, on)); put(x.toInt() - 2, (y - 6).toInt(), WHITE); put(x.toInt() + 2, (y - 5).toInt(), WHITE); put(x.toInt(), (y - 7).toInt(), WHITE)
        }
        // víly kroužící ke středu
        val pull = p(t, TELL1, CLIMAX)
        for (pz in pixies) {
            val a = pz[0] + t * 0.003f * pz[2] * (1f + pull * 2f)
            val r = (pz[1] + 14f) * (1f - pull * 0.6f) + s * 120f
            val x = cx + cosf(a) * r; val y = cy - 20f - pz[3] + sinf(a) * r * 0.4f - pull * 10f
            for (k in 1..4) blend((cx + cosf(a - k * 0.12f) * r).toInt(), (cy - 20f - pz[3] + sinf(a - k * 0.12f) * r * 0.4f - pull * 10f).toInt(), pix, 0.5f - k * 0.1f)
            glow(x, y, 7f, pix, 0.7f); ellipse(x, y, 1.5f, 1.5f, WHITE)
            if ((t / 120 + pz[0].toLong()) % 2 == 0L) { put(x.toInt() - 1, y.toInt() - 1, pink); put(x.toInt() + 1, y.toInt() - 1, pink) }
        }
        eyes(cx, cy - 4, 4f, hex(0xFF80D0), p(t, TELL2, TELL2 + 200) * (1f - p(t, CLIMAX, CLIMAX + 80)), 1f)
        // výbuch jisker a sloup světla
        if (s > 0f) {
            val k = easeOut(minOf(1f, s * 2f))
            for (y in 0 until cy.toInt()) for (x in (cx - 10 * k).toInt()..(cx + 10 * k).toInt()) blend(x, y, WHITE, 0.4f * k * (1f - abs(x - cx) / (10 * k + 1)) * (1f - minOf(1f, s)))
            for ((i, b) in stars.withIndex()) {
                val (x, y) = ballistic(cx, cy - 4, b[0], b[1], 50f, s)
                val c = if (i % 3 == 0) pix else if (i % 3 == 1) pink else bow[i % bow.size]
                put(x.toInt(), y.toInt(), c); if (b[2] > 0.6f) { put(x.toInt() - 1, y.toInt(), c); put(x.toInt() + 1, y.toInt(), c); put(x.toInt(), y.toInt() - 1, c); put(x.toInt(), y.toInt() + 1, c) }
            }
        }
    }
}

/** Jedová laboratoř: kádě a baňky, nad zemí zelená mlha; louže bublá, v ní oči – vybuchne sliz a plyn. */
class TowerPoisonScene(w: Int, h: Int) : KitScene(w, h, 12) {
    private val wall = hex(0x1A2420); private val wallL = hex(0x26342E); private val shelf = hex(0x3A2A20)
    private val tox = hex(0x7AE83A); private val toxL = hex(0xCAFF80); private val toxD = hex(0x2E6A1A); private val vio = hex(0xB060E0)
    private val vatC = hex(0x4A5A50); private val vatL = hex(0x6E8276); private val floorC = hex(0x24201C); private val floorL = hex(0x34302A)
    private val floorY = (h * 0.55f).toInt()
    private val cx = w / 2f; private val cy = h * 0.76f
    private val drops = burstDirs(rnd, 70, 40f, 150f, 110f)
    private val drips = List(3) { floatArrayOf(w * (0.25f + it * 0.25f), rnd.nextFloat() * 700f) }

    override fun paintBase() {
        vgrad(0, floorY, wall, wallL)
        for (y in floorY until h) for (x in 0 until w) put(x, y, if ((x / 10 + y / 10) % 2 == 0) floorC else floorL)
        for (sy in listOf(h * 0.18f, h * 0.32f)) {                               // police s baňkami
            rect(4, sy.toInt(), w - 8, 2, shelf)
            for (i in 0 until 9) { val x = 8 + i * 13; val c = if (i % 3 == 0) vio else tox; rect(x, sy.toInt() - 6, 4, 6, mix(c, BLACK, 0.3f)); rect(x + 1, sy.toInt() - 8, 2, 2, vatL); rect(x, sy.toInt() - 3, 4, 3, c) }
        }
        for (side in listOf(w * 0.14f, w * 0.86f)) {                               // kádě
            for (y in (floorY - 14) until (floorY + 22)) { val k = (y - floorY + 14) / 36f; val half = 14f - 3f * k * k; for (x in (side - half).toInt()..(side + half).toInt()) put(x, y, if (x < side - half + 3) vatL else vatC) }
            ellipse(side, floorY - 14f, 13f, 3f, tox)
        }
        for (x in 0 until w) rect(x, 2, 1, 3, if (x % 20 < 2) vatL else vatC)       // potrubí
    }

    override fun draw(t: Long) {
        // bublání v kádích
        for (side in listOf(w * 0.14f, w * 0.86f)) for (i in 0 until 3) { val ph = ((t + i * 300 + side.toLong() * 7) % 900) / 900f; ring(side + (i - 1) * 5f, floorY - 15f - ph * 6, 1f + ph, 0.5f, toxL, 1f - ph) }
        // kapky z potrubí
        for (d in drips) { val ph = ((t + d[1].toLong()) % 700) / 700f; put(d[0].toInt(), (5 + ph * (cy - 10)).toInt(), toxL) }
        val s = since(t)
        val swell = 0.4f * pulse(t, TELL1, 500) + p(t, TELL2, CLIMAX) * 0.6f
        if (s <= 0f) {
            val rx = 22f + 6f * swell; val ry = 7f + 2f * swell
            glow(cx, cy, rx * 1.4f, tox, 0.35f + 0.3f * swell, 0.4f)
            ellipse(cx, cy, rx, ry, toxD); ellipse(cx, cy - 1, rx - 2, ry - 2, tox); ellipse(cx - 6, cy - 2, rx * 0.3f, 1.5f, toxL)
            for (i in 0 until 4) { val ph = ((t * (1f + swell * 2f)).toLong() + i * 230) % 700 / 700f; if (ph < 0.7f) ring(cx + (i - 1.5f) * 9f, cy - ph * 3f, 1f + ph * 2.5f * (1 + swell), 0.5f, toxL, 0.9f) }
            eyes(cx, cy - 1, 5f, hex(0xF0FF60), p(t, TELL2 + 100, TELL2 + 300), 1f, 0f)
        } else {
            val k = easeOut(minOf(1f, s * 2f))
            ellipse(cx, cy, 26f + 10f * k, 8f + 2f * k, toxD)
            for (y in (cy - 70 * k).toInt() until cy.toInt()) { val q = (cy - y) / (70 * k + 1); val half = 9f * (1f - q) + 3f; for (x in (cx - half).toInt()..(cx + half).toInt()) put(x, y, if (abs(x - cx) < half * 0.4f) toxL else tox) }
            for (b in drops) { val (x, y) = ballistic(cx, cy - 20, b[0], b[1], 200f, s); circle(x, y, if (b[2] > 0.7f) 1.5f else 1f, if (b[2] > 0.4f) tox else toxL) }
        }
        // jedovatá mlha nad podlahou (při výbuchu se nafoukne)
        val fogH = 18f + 10f * swell + 60f * easeOut(minOf(1f, s * 1.3f))
        for (y in (cy + 12 - fogH).toInt() until h) for (x in 0 until w) {
            val n = fbm((x + t * 0.01f) / 14f, y / 6f, 9)
            val k = n - 0.45f - abs(y - (cy + 12 - fogH * 0.4f)) / fogH * 0.4f
            if (k > 0f && dither(x, y, k * 3f)) blend(x, y, toxL, 0.35f)
        }
    }
}
