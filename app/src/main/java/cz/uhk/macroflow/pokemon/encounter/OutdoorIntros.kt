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
import kotlin.math.hypot
import kotlin.math.sin

/*
 * Intra setkání pro venkovní lokace bez vlastní animace. Každá scéna: klidný záběr,
 * dvě znamení (TELL1, TELL2), vyvrcholení (CLIMAX) a záblesk (Kit.REVEAL).
 */

/** Nebeský průsmyk: mraky pod stezkou, praporky ve větru; v mračnu se rýsuje stín, mrak se roztrhne. */
class SkyPassScene(w: Int, h: Int) : KitScene(w, h, 51) {
    private val skyTop = hex(0x2A3A70); private val skyMid = hex(0x8296CC); private val skyLow = hex(0xF2CCAA)
    private val far = hex(0x7886B0); private val farSnow = hex(0xE4EAF6); private val near = hex(0x46527C); private val nearSnow = hex(0xC8D2EA)
    private val rock = hex(0x5E5864); private val rockL = hex(0x8A8090); private val rockD = hex(0x3A3440); private val path = hex(0xA89A8C)
    private val cloud = hex(0xF6F8FF); private val cloudS = hex(0xB8C2DE); private val shadow = hex(0x2A2E48)
    private val flags = intArrayOf(hex(0x3A6AD0), hex(0xF4F4F0), hex(0xD04040), hex(0x40A060), hex(0xF0C838))
    private val horizon = (h * 0.62f).toInt()
    private val ledge = (h * 0.76f).toInt()
    private val streaks = List(26) { floatArrayOf(rnd.nextFloat() * w, h * (0.15f + rnd.nextFloat() * 0.7f), 5f + rnd.nextFloat() * 10f, 0.6f + rnd.nextFloat()) }
    private val snow = List(40) { floatArrayOf(rnd.nextFloat() * w, rnd.nextFloat() * h, 0.4f + rnd.nextFloat()) }

    override fun paintBase() {
        vgrad(0, (h * 0.4f).toInt(), skyTop, skyMid); vgrad((h * 0.4f).toInt(), horizon, skyMid, skyLow)
        vgrad(horizon, h, hex(0xD8D4EC), hex(0xA8AACC))
        for (x in 0 until w) {
            val fr = h * 0.47f - 26f * fbm(x / 22f, 1f, 3) - 10f * abs(sin(x / 9f))
            val nr = h * 0.58f - 22f * fbm(x / 30f + 5f, 2f, 4)
            for (y in fr.toInt() until horizon) put(x, y, if (y < fr + 3 + 4 * hf(x, 1)) farSnow else mix(far, skyLow, (y - fr) / (horizon - fr) * 0.5f))
            for (y in nr.toInt() until horizon + 6) put(x, y, if (y < nr + 2 + 3 * hf(x, 2)) nearSnow else near)
        }
        // skalní římsa se stezkou
        for (x in 0 until w) {
            val top = ledge - 6 * fbm(x / 14f, 9f, 5) - (if (x > w * 0.7f) (x - w * 0.7f) * 0.35f else 0f)
            for (y in top.toInt() until h) {
                val n = fbm(x / 18f, y / 4f, 6); val crack = hf(x / 5, (y + x / 3) / 7, 2) > 0.93f
                put(x, y, when { y < top + 2 -> rockL; crack -> rockD; n > 0.6f -> rockL; n < 0.36f -> rockD; else -> rock })
            }
        }
        for (y in ledge + 6 until h) {                                  // stezka do dálky
            val k = (y - ledge).toFloat() / (h - ledge)
            val cx = w * (0.62f - 0.25f * k); val hw = 6f + 30f * k
            for (x in (cx - hw).toInt()..(cx + hw).toInt()) if (hf(x, y, 7) > 0.12f) put(x, y, mix(path, rockL, hf(x / 3, y / 2, 8) * 0.5f))
        }
    }

    private fun gust(t: Long) = pulse(t, TELL1, 500) + pulse(t, TELL2, 500) + if (t >= CLIMAX) 1.4f else 0f

    override fun draw(t: Long) {
        val bank = easeOut(p(t, TELL1, CLIMAX))
        val tear = easeOut(p(t, CLIMAX, PEAK + 350))
        val cx = w / 2f; val cy = h * 0.55f
        // oblačná vrstva pod průsmykem + hustnoucí mračno uprostřed
        for (y in (h * 0.36f).toInt() until (h * 0.72f).toInt()) for (x in 0 until w) {
            val d = hypot((x - cx) / (w * 0.5f), (y - cy) / (h * 0.13f))
            var dens = fbm((x + t * 0.014f) / 20f, y / 8f, 11) - 0.12f + 0.35f * (1f - abs(y - h * 0.6f) / (h * 0.14f)).coerceAtLeast(0f)
            dens += bank * 0.6f * (1f - d).coerceAtLeast(0f)
            if (tear > 0f) dens -= 1.6f * (tear * 1.3f - d).coerceAtLeast(0f)
            if (dens > 0.58f && dither(x, y, (dens - 0.58f) * 5f)) blend(x, y, if (dens > 0.78f) cloud else cloudS, 0.9f)
        }
        // stín v mračnu a oči
        val sh = p(t, TELL2 - 150, TELL2 + 250) * (1f - tear)
        if (sh > 0f) {
            val flap = sinf(t * 0.012f) * 4f
            ellipse(cx, cy, 7f + 2f * sh, 9f + 2f * sh, shadow, 0.7f * sh)                       // tělo
            for (s in intArrayOf(-1, 1)) for (i in 0 until 22) {                                  // křídla
                val k = i / 22f; val x = cx + s * (5f + i * 1.3f); val y = cy - 2f - k * 6f + k * k * 10f + flap * k
                for (yy in 0 until (5f * (1f - k) + 1f).toInt()) blend(x.toInt(), (y + yy).toInt(), shadow, 0.65f * sh)
            }
            eyes(cx, cy - 2f, 4.5f, hex(0xA8E4FF), p(t, TELL2, TELL2 + 200) * (1f - tear), 1.2f)
        }
        // praporky na laně přes záběr
        val g = gust(t)
        val y0 = h * 0.70f; val y1 = h * 0.64f
        for (x in 0 until w) { val k = x.toFloat() / w; put(x, (y0 + (y1 - y0) * k + 9f * sin(k * PI).toFloat()).toInt(), rockD) }
        for (i in 0 until 9) {
            val k = (i + 0.5f) / 9f; val fx = k * w
            val fy = y0 + (y1 - y0) * k + 9f * sin(k * PI).toFloat()
            val flap = sin(t * 0.012 + i).toFloat() * (1f + 2.2f * g)
            val c = flags[i % flags.size]
            for (yy in 1..8) for (xx in -3..3) {
                val sx = fx + xx + flap * yy / 8f - g * yy * 0.9f
                put(sx.toInt(), (fy + yy).toInt(), if (xx == -3 || yy == 8) mix(c, BLACK, 0.3f) else c)
            }
        }
        // poryvy větru a sníh
        for (s in streaks) {
            val a = (g * 0.6f).coerceAtMost(0.9f); if (a <= 0.05f) continue
            val x = ((s[0] - t * 0.14f * s[3]) % (w + 30) + w + 30) % (w + 30) - 15
            line(x, s[1], x + s[2], s[1], WHITE, a * 0.7f)
        }
        for (s in snow) {
            val x = ((s[0] - t * 0.02f * s[2] * (1f + g * 3f)) % w + w) % w
            val y = (s[1] + t * 0.012f * s[2]) % h
            blend(x.toInt(), y.toInt(), WHITE, 0.8f)
        }
    }
}

/** Zapomenutý háj: trní kolem kamenného oltáře, spory září; trní se rozestoupí. */
class HiddenGroveScene(w: Int, h: Int) : KitScene(w, h, 46) {
    private val dark = hex(0x0E1E1C); private val mid = hex(0x1C3A32); private val moss = hex(0x3E7A4E); private val mossL = hex(0x7EB86A)
    private val stone = hex(0x6A7470); private val stoneL = hex(0x9AA6A0); private val stoneD = hex(0x3C4642)
    private val thorn = hex(0x2A1A20); private val thornL = hex(0x4E3438); private val berry = hex(0xC03A5A)
    private val rune = hex(0x7CF2D2); private val spore = hex(0xBFFFE8)
    private val floorY = (h * 0.6f).toInt()
    private val spores = List(36) { floatArrayOf(rnd.nextFloat() * w, h * (0.25f + rnd.nextFloat() * 0.7f), rnd.nextFloat() * 6.28f, 3f + rnd.nextFloat() * 8f) }
    private val vines = List(22) { i -> floatArrayOf(if (i % 2 == 0) -4f else w + 4f, h * (0.45f + rnd.nextFloat() * 0.55f), rnd.nextFloat() * 6.28f, 0.45f + rnd.nextFloat() * 0.45f) }

    override fun paintBase() {
        vgrad(0, floorY, dark, mid)
        vgrad(floorY, h, hex(0x1E3A2C), dark)
        for (y in 0 until floorY) for (x in 0 until w) {                // staré kmeny v pozadí
            val n = fbm(x / 9f, y / 40f, 2)
            if (n > 0.6f) put(x, y, mix(hex(0x14282A), mid, (n - 0.6f) * 2f))
        }
        for (y in floorY until h) for (x in 0 until w) if (fbm(x / 7f, y / 4f, 3) > 0.58f) put(x, y, if (hf(x, y) > 0.7f) mossL else moss)
        // kamenný oltář s runou
        val cx = w / 2; val top = (h * 0.5f).toInt()
        for (y in top until floorY + 14) for (x in cx - 16..cx + 16) {
            val e = y < top + 4 && abs(x - cx) > 14 - (y - top)
            if (e) continue
            put(x, y, when { y < top + 3 -> stoneL; x > cx + 11 -> stoneD; x < cx - 13 -> stoneL; hf(x / 4, y / 3, 5) > 0.88f -> stoneD; else -> stone })
        }
        for (x in cx - 16..cx + 16) if (hf(x, 1, 9) > 0.4f) put(x, top + (hf(x, 2) * 3).toInt(), moss)
    }

    override fun draw(t: Long) {
        val cx = w / 2f
        val part = easeOut(p(t, CLIMAX, PEAK + 300))
        // runy na oltáři se rozsvěcují se znameními
        val ra = 0.25f + 0.5f * pulse(t, TELL1, 500) + 0.8f * p(t, TELL2, CLIMAX) - 0.6f * part
        for ((i, dx) in listOf(-9, -3, 3, 9).withIndex()) {
            val x = cx + dx; val y = h * 0.53f + (i % 2) * 2
            glow(x, y + 2, 5f, rune, ra * 0.6f)
            line(x, y, x, y + 4, rune, ra); line(x - 1, y + 2, x + 1, y + 2, rune, ra)
        }
        // spory kroužící k oltáři
        val pull = p(t, TELL1, CLIMAX)
        for (s in spores) {
            val ang = s[2] + t * 0.0012f * (1f + pull * 2f)
            val bx = s[0] + (cx - s[0]) * pull * 0.6f; val by = s[1] + (h * 0.55f - s[1]) * pull * 0.5f
            var x = bx + cosf(ang) * s[3]; var y = by + sinf(ang) * s[3] * 0.6f - t * 0.004f
            if (part > 0f) { val a = s[2] * 3f; x += cosf(a) * part * 70f; y += sinf(a) * part * 70f }
            val a = 0.5f + 0.5f * sinf(t * 0.006f + s[2] * 3f)
            glow(x, y, 2.5f, spore, a * 0.4f); blend(x.toInt(), y.toInt(), spore, a)
        }
        // oči za trním před oltářem
        val ea = p(t, TELL2, TELL2 + 200) * (1f - part)
        if (ea > 0f) ellipse(cx, h * 0.72f, 16f, 8f, BLACK, 0.6f * ea)
        eyes(cx, h * 0.72f, 6f, hex(0xFFE27A), ea, 1.5f)
        // trní z obou stran: přirůstá k sobě, při vyvrcholení se rozestoupí
        val grow = 0.55f + 0.3f * p(t, TELL1, CLIMAX) - 0.9f * part
        for ((i, v) in vines.withIndex()) {
            val dir = if (v[0] < 0) 1f else -1f
            val len = w * 0.62f * grow * v[3] + 2f * sinf(t * 0.004f + v[2])
            var px = v[0]; var py = v[1]
            for (s in 0 until len.toInt().coerceAtLeast(0)) {
                val nx = px + dir; val ny = v[1] + 6f * sinf(s / 9f + v[2]) + 0.06f * s * (if (i % 3 == 0) -1f else 1f)
                line(px, py, nx, ny, thorn); line(px, py + 1, nx, ny + 1, thorn); put(nx.toInt(), ny.toInt() - 1, thornL)
                if (s % 7 == 3) { put(nx.toInt(), ny.toInt() + 1, thornL); put(nx.toInt() + dir.toInt(), ny.toInt() + 2, thorn) }
                if (s % 23 == 11) { put(nx.toInt(), ny.toInt() - 2, berry); put(nx.toInt() + 1, ny.toInt() - 2, berry) }
                px = nx; py = ny
            }
        }
        if (part > 0f) {
            glow(cx, h * 0.6f, 60f * part, spore, 0.7f * (1f - part * 0.5f))
            for (i in 0 until 40) { val a = i * 0.157f * 4f; val r = 10f + part * (40f + 30f * hf(i, 4)); circle(cx + cosf(a) * r, h * 0.62f + sinf(a) * r * 0.7f, 1.2f, spore, 1f - part * 0.6f) }
        }
    }
}

/** Doly: koleje do tmy, kývající se lucerna, padá suť; z tunelu vyrazí vozík s jiskrami. */
class MinesScene(w: Int, h: Int) : KitScene(w, h, 49) {
    private val rockD = hex(0x1A1416); private val rock = hex(0x3A2E2C); private val rockL = hex(0x5E4A42)
    private val wood = hex(0x6A4428); private val woodD = hex(0x3E2616); private val woodL = hex(0x9A6A3E)
    private val rail = hex(0x8A8E96); private val railD = hex(0x4A4C52); private val sleeper = hex(0x4A3020)
    private val lava = hex(0xFF7A20); private val lavaL = hex(0xFFD060); private val lamp = hex(0xFFC860)
    private val vx = w / 2f; private val vy = h * 0.42f            // úběžník tunelu
    private val rocks = List(14) { floatArrayOf(w * (0.25f + rnd.nextFloat() * 0.5f), rnd.nextFloat() * 400f, 1f + rnd.nextFloat() * 2f) }
    private val sparks = List(40) { floatArrayOf((rnd.nextFloat() - 0.5f) * 120f, -20f - rnd.nextFloat() * 90f, rnd.nextFloat()) }

    override fun paintBase() {
        for (y in 0 until h) for (x in 0 until w) {
            val d = hypot((x - vx) / w, (y - vy) / h)
            val n = fbm(x / 8f, y / 8f, 4)
            val brick = fbm(x / 14f, y / 9f, 4); val c = if (brick > 0.62f) rockL else if (brick < 0.4f) rockD else rock
            put(x, y, mix(rockD, c, ((d - 0.08f) * 2.6f).coerceIn(0f, 1f) * (0.55f + n * 0.3f)))
        }
        ellipse(vx, vy, 10f, 12f, BLACK)                                  // ústí tunelu
        // koleje k úběžníku
        for (y in vy.toInt() + 10 until h) {
            val k = (y - vy) / (h - vy)
            val half = 4f + 40f * k
            if (((y - vy) * (1.6f - k)).toInt() % 8 == 0) for (x in (vx - half * 1.4f).toInt()..(vx + half * 1.4f).toInt()) put(x, y, sleeper)
            for (s in intArrayOf(-1, 1)) { val x = vx + s * half; rect(x.toInt(), y, (1 + 2 * k).toInt().coerceAtLeast(1), 1, if (s < 0) rail else railD) }
        }
        // výdřeva: tři rámy do hloubky
        for ((i, k) in listOf(1f, 0.55f, 0.3f).withIndex()) {
            val hw = w * 0.5f * k + 6; val top = vy - h * 0.38f * k; val bot = vy + (h - vy) * k
            val th = (6 * k).toInt().coerceAtLeast(1)
            rect((vx - hw).toInt(), top.toInt(), th, (bot - top).toInt(), if (i == 0) woodL else wood)
            rect((vx + hw).toInt() - th, top.toInt(), th, (bot - top).toInt(), woodD)
            rect((vx - hw).toInt(), top.toInt(), (hw * 2).toInt(), th, wood)
        }
        // lávové pukliny vpravo dole
        for (i in 0 until 60) { val y = (h * 0.8f + i * 0.6f).toInt(); put((w * 0.86f + 6 * sin(i * 0.4)).toInt(), y, lava) }
    }

    override fun draw(t: Long) {
        // dýchající záře lávy
        glow(w * 0.9f, h * 0.88f, 36f, lava, 0.35f + 0.1f * sinf(t * 0.004f))
        // lucerna na řetězu: kývá se a bliká
        val sw = sinf(t * 0.004f) * (0.3f + 0.6f * p(t, TELL1, CLIMAX))
        val lx = w * 0.3f + sw * 14f; val ly = h * 0.22f
        line(w * 0.3f, 0f, lx, ly, railD)
        val flick = if (pulse(t, TELL2, 300) > 0.3f && (t / 60) % 2 == 0L) 0.2f else 1f
        glow(lx, ly + 4, 26f, lamp, 0.5f * flick); rect(lx.toInt() - 2, ly.toInt(), 5, 7, woodD); rect(lx.toInt() - 1, ly.toInt() + 2, 3, 3, lamp)
        // padající suť
        for (r in rocks) {
            val start = TELL1 + r[1].toLong()
            if (t < start) continue
            val s = (t - start) / 1000f
            val y = 10f + 260f * s * s; if (y > h * 0.9f) continue
            rect(r[0].toInt(), y.toInt(), r[2].toInt(), r[2].toInt(), rockL)
            blend(r[0].toInt(), y.toInt() - 2, rockL, 0.3f)
        }
        // oči v tunelu
        eyes(vx, vy, 3f, hex(0xFF5040), p(t, TELL2, TELL2 + 200) * (1f - p(t, CLIMAX, CLIMAX + 120)), 0.8f)
        // vozík: vyrazí z úběžníku a roste
        val c = p(t, CLIMAX - 100, PEAK + 250)
        if (c > 0f) {
            val k = c * c
            val cy = vy + (h * 0.75f - vy) * k; val cw = 8f + 90f * k; val ch = 6f + 50f * k
            rect((vx - cw / 2).toInt(), (cy - ch).toInt(), cw.toInt(), ch.toInt(), hex(0x5A5A62))
            rect((vx - cw / 2).toInt(), (cy - ch).toInt(), cw.toInt(), (ch * 0.18f).toInt().coerceAtLeast(1), hex(0x9A9AA6))
            rect((vx - cw / 2).toInt(), (cy - ch * 0.2f).toInt(), cw.toInt(), (ch * 0.2f).toInt().coerceAtLeast(1), hex(0x34343A))
            ellipse(vx, cy - ch * 0.95f, cw * 0.42f, ch * 0.22f, hex(0x2A2020))          // ruda
            for (i in 0 until 5) circle(vx + (i - 2) * cw * 0.15f, cy - ch * 1.02f - (i % 2) * ch * 0.06f, ch * 0.06f, if (i % 2 == 0) hex(0xC8A040) else hex(0x5A4A40))
            for (s in intArrayOf(-1, 1)) { circle(vx + s * cw * 0.3f, cy, ch * 0.18f, hex(0x1A1A1E)); circle(vx + s * cw * 0.3f, cy, ch * 0.07f, rail) }
            glow(vx, cy - ch * 0.5f, cw * 0.4f, lamp, 0.25f)
            for (s in sparks) {
                if (s[2] > c * 1.4f) continue
                val st = since(t, CLIMAX)
                val (x, y) = ballistic(vx + s[0] * 0.3f * k, cy, s[0], s[1], 140f, st)
                put(x.toInt(), y.toInt(), if (s[2] > 0.5f) lavaL else lava)
            }
        }
    }
}

/** Popelavá pláň: padá popel, mrtvé stromy; kupa popela se chvěje, žhne a vybuchne v mračno. */
class AshPlainScene(w: Int, h: Int) : KitScene(w, h, 88) {
    private val sky0 = hex(0x3A3440); private val sky1 = hex(0x8A7A80); private val sun = hex(0xF0D6C0)
    private val ash = hex(0x8E8884); private val ashL = hex(0xB8B2AC); private val ashD = hex(0x5A5450)
    private val tree = hex(0x24201E); private val ember = hex(0xFF7A3A); private val emberL = hex(0xFFD08A)
    private val horizon = (h * 0.5f).toInt()
    private val mx = w / 2f; private val my = h * 0.68f
    private val flakes = List(70) { floatArrayOf(rnd.nextFloat() * w, rnd.nextFloat() * h, 0.4f + rnd.nextFloat(), rnd.nextFloat() * 6f) }
    private val bits = List(60) { val a = -PI / 2 + (rnd.nextDouble() - 0.5) * 2.4; floatArrayOf((kotlin.math.cos(a) * (30 + rnd.nextFloat() * 90)).toFloat(), (sin(a) * (40 + rnd.nextFloat() * 110)).toFloat(), rnd.nextFloat()) }

    override fun paintBase() {
        vgrad(0, horizon, sky0, sky1)
        for (y in 0 until horizon) for (x in 0 until w) if (fbm(x / 30f, y / 8f, 2) > 0.6f) blend(x, y, ashL, 0.25f)
        glow(w * 0.7f, h * 0.22f, 22f, sun, 0.5f); circle(w * 0.7f, h * 0.22f, 7f, mix(sun, sky1, 0.3f))
        for (y in horizon until h) for (x in 0 until w) {
            val n = fbm(x / 26f, (y - horizon) / 6f + y * 0.02f, 4); val k = (y - horizon).toFloat() / (h - horizon)
            put(x, y, mix(if (n > 0.64f) ashL else if (n < 0.36f) ashD else ash, ashD, (1f - k) * 0.35f))
        }
        for (x in 0 until w) { val hh = 3f * fbm(x / 12f, 0f, 7); for (y in (horizon - hh).toInt() until horizon) put(x, y, ashD) }
        // mrtvé stromy na obzoru a jeden blíž
        for ((tx, th, s) in listOf(Triple(w * 0.12f, 34f, 1), Triple(w * 0.28f, 18f, 2), Triple(w * 0.85f, 28f, 3), Triple(w * 0.95f, 60f, 4))) {
            val by = if (s == 4) h * 0.72f else horizon.toFloat()
            line(tx, by, tx + 1, by - th, tree); line(tx + 1, by, tx + 2, by - th * 0.9f, tree)
            for (b in 0 until 4) {
                val y = by - th * (0.4f + b * 0.15f); val d = if (b % 2 == 0) 1f else -1f
                line(tx + 1, y, tx + 1 + d * th * 0.25f, y - th * 0.18f, tree); line(tx + 1 + d * th * 0.25f, y - th * 0.18f, tx + 1 + d * th * 0.3f, y - th * 0.26f, tree)
            }
        }
    }

    override fun draw(t: Long) {
        val boom = since(t)
        val tremble = (pulse(t, TELL1, 400) + pulse(t, TELL2, 400)) * sinf(t * 0.08f) * 1.4f
        // kupa popela (zmizí v explozi)
        val gone = p(t, CLIMAX, CLIMAX + 250)
        if (gone < 1f) {
            val rx = 34f * (1f - gone * 0.6f); val ry = 16f * (1f - gone)
            ellipse(mx + tremble, my, rx, ry, ash)
            ellipse(mx + tremble - 4, my - ry * 0.3f, rx * 0.6f, ry * 0.5f, ashL)
            ellipse(mx + tremble, my + ry * 0.6f, rx, ry * 0.4f, ashD)
            // žhnoucí prasklina
            val crack = p(t, TELL2, CLIMAX)
            if (crack > 0f) {
                zigzag(mx - 10f * crack, my - 3, mx + 10f * crack, my + 2, 2.5f, 4, ember, 0.9f, 5)
                glow(mx, my, 14f * crack, ember, 0.5f * crack)
                eyes(mx, my - 2, 4.5f, emberL, p(t, TELL2 + 150, TELL2 + 350), 1f)
            }
            // stékající popel po bocích
            for (i in 0 until 6) {
                val a = pulse(t, TELL1 + i * 40L, 500) + pulse(t, TELL2 + i * 30L, 500)
                if (a > 0f) put((mx + (i - 2.5f) * 7f).toInt(), (my - 6 + (t / 30 + i * 3) % 12).toInt(), ashL)
            }
        }
        // mračno popela po výbuchu
        if (boom > 0f) {
            val r = 10f + 70f * easeOut(minOf(1f, boom * 1.6f))
            for (y in (my - r * 1.4f).toInt()..(my + r * 0.3f).toInt()) for (x in (mx - r).toInt()..(mx + r).toInt()) {
                val d = hypot((x - mx) / r, (y - my + r * 0.4f) / (r * 0.9f))
                val n = fbm(x / 7f, (y + boom * 40f) / 7f, 12)
                val k = (1f - d) * 2f + n - 0.9f
                if (k > 0f && dither(x, y, k * 2f)) blend(x, y, if (n > 0.55f) ashL else ash, 0.85f)
            }
            for (b in bits) {
                val (x, y) = ballistic(mx, my - 4, b[0], b[1], 90f, boom)
                put(x.toInt(), y.toInt(), if (b[2] > 0.4f) ember else emberL)
            }
        }
        // padající popel (po výbuchu víří víc)
        for (f in flakes) {
            val x = ((f[0] + t * 0.008f * f[2] + 3f * sinf(t * 0.002f + f[3]) + boom * 30f * (f[0] - mx) / w) % w + w) % w
            val y = (f[1] + t * 0.014f * f[2]) % h
            put(x.toInt(), y.toInt(), if (f[2] > 1f) ashL else ash)
        }
    }
}

/** Ztichlý sad v noci: měsíc, rozkvetlé stromy; spadne jablko, v koruně se rozsvítí oči a vyletí vrány. */
class QuietOrchardScene(w: Int, h: Int) : KitScene(w, h, 89) {
    private val sky0 = hex(0x0C1028); private val sky1 = hex(0x2A2E5A); private val moon = hex(0xF4F0DC)
    private val grass = hex(0x1C3020); private val grassL = hex(0x34503A); private val trunk = hex(0x1A1416)
    private val crown = hex(0x1E3424); private val crownL = hex(0x3A5A44); private val bloom = hex(0xE8D8F0); private val apple = hex(0xD03A3A)
    private val crow = hex(0x08080C)
    private val ground = (h * 0.62f).toInt()
    private val tx = w / 2f; private val ty = h * 0.40f
    private val stars = List(40) { floatArrayOf(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.5f, rnd.nextFloat() * 6f) }
    private val petals = List(30) { floatArrayOf(rnd.nextFloat() * w, rnd.nextFloat(), 0.5f + rnd.nextFloat(), rnd.nextFloat() * 6f) }
    private val crows = List(6) { floatArrayOf((rnd.nextFloat() - 0.5f) * 140f, -60f - rnd.nextFloat() * 70f, rnd.nextFloat() * 6f) }

    override fun paintBase() {
        vgrad(0, ground, sky0, sky1)
        glow(w * 0.24f, h * 0.14f, 30f, moon, 0.35f); circle(w * 0.24f, h * 0.14f, 9f, moon); circle(w * 0.24f + 3, h * 0.14f - 2, 2f, hex(0xD8D2BC))
        vgrad(ground, h, grass, hex(0x0E1A12))
        for (y in ground until h) for (x in 0 until w) if (hf(x, y) > 0.97f || (hf(x, y / 2, 4) > 0.9f && y > ground + 20)) put(x, y, grassL)
        ellipse(tx, ground + 14f, 30f, 5f, hex(0x0A120C), 0.6f)
        // řady stromů do dálky
        for (row in 0 until 3) {
            val y = ground - 2 + row * 0; val sc = 0.35f + row * 0.15f
            for (i in 0 until 5) {
                val x = (i + 0.5f) * w / 5f + (row - 1) * 9f
                if (row == 2 && abs(x - tx) < 30) continue
                crownAt(x, y - 30f * sc, 18f * sc, row)
                rect(x.toInt() - 1, (y - 30f * sc).toInt(), 2, (30 * sc).toInt() + 2, trunk)
            }
        }
        rect(tx.toInt() - 3, ty.toInt(), 6, (ground + 14 - ty).toInt(), trunk)       // hlavní strom
    }

    private fun crownAt(x: Float, y: Float, r: Float, seed: Int, dx: Float = 0f) {
        for (yy in (y - r).toInt()..(y + r * 0.7f).toInt()) for (xx in (x - r).toInt()..(x + r).toInt()) {
            val d = hypot(xx - x, (yy - y) * 1.2f) / (r * (0.85f + 0.2f * noise(xx / 3f, yy / 3f, seed)))
            if (d < 1f) {
                val cl = noise(xx / 2.5f, yy / 2.5f, seed + 5)
                put(xx + dx.toInt(), yy, if (cl > 0.78f) bloom else if (cl > 0.72f) mix(bloom, crownL, 0.5f) else if ((xx - x) + (yy - y) < -r * 0.3f) crownL else if (d > 0.85f) mix(crown, BLACK, 0.3f) else crown)
            }
        }
    }

    override fun draw(t: Long) {
        for (s in stars) if (sinf(t * 0.004f + s[2]) > 0.2f) put(s[0].toInt(), s[1].toInt(), moon)
        val shake = (pulse(t, TELL1, 350) + pulse(t, TELL2, 450)) * sinf(t * 0.07f) * 2f + if (t >= CLIMAX) sinf(t * 0.09f) * 2f * (1f - p(t, CLIMAX, PEAK + 400)) else 0f
        crownAt(tx, ty, 30f, 9, shake)
        // jablko spadne po prvním zachvění a odskočí
        val fall = (t - TELL1 - 150) / 1000f
        if (fall > 0f) {
            val gy = ground + 8f
            var y = ty + 10f + 300f * fall * fall
            if (y > gy) { val b = fall - kotlin.math.sqrt((gy - ty - 10f) / 300f); y = gy - (40f * b - 160f * b * b).coerceAtLeast(0f) }
            ellipse(tx + 12f + fall * 6f, y, 2f, 2f, apple); put((tx + 12f + fall * 6f).toInt(), (y - 2).toInt(), crownL)
        }
        // oči v koruně
        val ea = p(t, TELL2, TELL2 + 200) * (1f - p(t, CLIMAX, CLIMAX + 100))
        eyes(tx - 8f + shake, ty + 2f, 3f, hex(0xFFE070), ea, 0.9f)
        eyes(tx + 10f + shake, ty - 8f, 3f, hex(0xFFE070), ea * p(t, TELL2 + 200, TELL2 + 400), 0.8f)
        // okvětní lístky (při vyvrcholení se vysypou z koruny)
        val burst = since(t)
        for (pt in petals) {
            val x: Float; val y: Float
            if (burst > 0f) { val r = ballistic(tx, ty, (pt[0] - w / 2f) * 1.2f, -40f * pt[2], 60f, burst); x = r.first; y = r.second }
            else { x = (pt[0] + 6f * sinf(t * 0.002f + pt[3])) % w; y = ((pt[1] + t * 0.00004f * pt[2]) % 1f) * h }
            put(x.toInt(), y.toInt(), bloom)
        }
        // vrány
        if (burst > 0f) for (c in crows) {
            val x = tx + c[0] * burst; val y = ty + c[1] * burst
            val up = sinf(t * 0.04f + c[2]) > 0
            rect(x.toInt() - 1, y.toInt(), 3, 2, crow)
            line(x - 1, y, x - 4, y + if (up) -3 else 2, crow); line(x + 1, y, x + 4, y + if (up) -3 else 2, crow)
        }
    }
}

/** Ozvěnový kaňon: vrstvené stěny, kruhy ozvěny, sypou se kamínky; stín na římse spustí sesuv. */
class EchoCanyonScene(w: Int, h: Int) : KitScene(w, h, 90) {
    private val sky0 = hex(0x6A9AD0); private val sky1 = hex(0xF4D8B0)
    private val strata = intArrayOf(hex(0xB4583A), hex(0xC8744A), hex(0xA04A32), hex(0xD89A62), hex(0x8E4030))
    private val sand = hex(0xD8A878); private val sandD = hex(0xB07E52); private val shadowC = hex(0x5A2A20)
    private val ledgeX = w * 0.72f; private val ledgeY = h * 0.34f
    private fun wallL(y: Int) = w * (0.36f - 0.25f * y / h) + 4f * noise(0f, y / 9f, 2)
    private fun wallR(y: Int) = w * (0.66f + 0.25f * y / h) - 4f * noise(1f, y / 9f, 3)
    private val pebbles = List(18) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 1000f) }
    private val boulders = List(9) { floatArrayOf(-20f - rnd.nextFloat() * 60f, -10f - rnd.nextFloat() * 30f, 2f + rnd.nextFloat() * 4f, rnd.nextFloat() * 120f) }

    override fun paintBase() {
        vgrad(0, (h * 0.5f).toInt(), sky0, sky1)
        val floorY = (h * 0.8f).toInt()
        for (y in 0 until h) for (x in 0 until w) {
            val l = wallL(y); val r = wallR(y)
            if (y >= floorY && x > l && x < r) { put(x, y, if (fbm(x / 9f, y / 3f, 5) > 0.55f) sandD else sand); continue }
            if (x <= l || x >= r) {
                val band = ((y + 9f * noise(x / 14f, y / 60f, 7) + 5f * sin(y / 23f)) / (5f + 4f * noise(0f, y / 30f, 8))).toInt()
                var c = strata[Math.floorMod(band, strata.size)]
                val edge = if (x <= l) l - x else x - r
                if (edge < 3) c = mix(c, WHITE, 0.15f)
                if (x >= r) c = mix(c, shadowC, 0.35f)
                if (hf(x / 2, y / 2, 6) > 0.9f) c = mix(c, BLACK, 0.2f)
                put(x, y, c)
            } else if (y < floorY) {
                val k = (y - h * 0.5f) / (h * 0.3f)
                if (k >= 0) put(x, y, mix(sky1, sandD, k * 0.5f))
            }
        }
        rect((ledgeX - 10).toInt(), ledgeY.toInt(), 22, 3, strata[3])                    // římsa
    }

    override fun draw(t: Long) {
        // kruhy ozvěny z římsy
        for ((i, s) in listOf(TELL1, TELL1 + 250, TELL2, TELL2 + 250).withIndex()) {
            val k = p(t, s, s + 900)
            if (k > 0f && k < 1f) ring(ledgeX, ledgeY, 4f + k * 70f, 0.6f, hex(0xFFF0D0), 0.6f * (1f - k), 0.6f + 0.1f * i)
        }
        // kamínky sypající se ze stěn
        for (pb in pebbles) {
            val start = TELL1 + pb[1].toLong(); if (t < start) continue
            val s = (t - start) / 1000f
            val left = pb[0] < 0.5f; val y = h * (0.1f + pb[0] * 0.3f) + 220f * s * s
            val x = if (left) wallL(y.toInt()) + 2 else wallR(y.toInt()) - 2
            if (y < h * 0.8f) put(x.toInt(), y.toInt(), strata[1])
        }
        // stín na římse
        val sa = p(t, TELL2 - 200, TELL2 + 200) * (1f - p(t, CLIMAX + 100, PEAK))
        if (sa > 0f) {
            ellipse(ledgeX, ledgeY - 6, 6f, 7f, shadowC, sa); ellipse(ledgeX - 3, ledgeY - 13, 2f, 3f, shadowC, sa); ellipse(ledgeX + 3, ledgeY - 13, 2f, 3f, shadowC, sa)
            eyes(ledgeX, ledgeY - 7, 2.4f, hex(0xFFE890), p(t, TELL2 + 100, TELL2 + 300), 0.8f)
        }
        // sesuv: balvany z římsy, prach u dna
        val s = since(t)
        if (s > 0f) {
            for (b in boulders) {
                val (x, y0) = ballistic(ledgeX, ledgeY, b[0], b[1], 220f, s)
                val floorY = h * 0.82f
                val y = minOf(y0, floorY - b[2])
                circle(x, y, b[2], strata[2]); blend(x.toInt() - 1, (y - b[2] * 0.5f).toInt(), strata[3], 0.8f)
            }
            val d = easeOut(minOf(1f, s * 1.4f))
            for (y in (h * 0.6f).toInt() until (h * 0.86f).toInt()) for (x in 0 until w) {
                val n = fbm(x / 8f + s, y / 6f, 9); val k = n - 0.85f + d * 0.6f - abs(x - w * 0.55f) / w
                if (k > 0f && dither(x, y, k * 3f)) blend(x, y, sky1, 0.7f)
            }
        }
    }
}
