package cz.uhk.macroflow.pokemon.cave

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Živé vrstvy Nebeského průsmyku (docs/adr/0044), bez Androidu – pokryto testy.
 * Obrázek mapy je statický; přes něj se kreslí třpytivý závoj v Bráně světů (smyčka snímků),
 * pulzující lůžko pro Srdce Hvozdu a obláčky, které plují přes moře mraků.
 */
object SkyPassArt {
    /** Střed prstence a vnitřní poloměr – shodné s gen_skypass.py (GATE_C, GATE_R_IN). */
    const val GATE_X = 80
    const val GATE_Y = 190
    const val VEIL_R = 18
    const val VEIL_SIZE = VEIL_R * 2 + 1
    const val FRAMES = 12

    /** Lůžko pro Srdce Hvozdu na podstavci (art px). */
    const val SOCKET_X = 80
    const val SOCKET_Y = 206

    /** Pás moře mraků nad údolím, kudy plují obláčky (art px, svisle). */
    val CLOUD_BAND = 118..140

    /**
     * Snímek závoje: spirála světla, která se pomalu točí (fáze [frame] / [FRAMES]), a jiskřičky.
     * Průhledné mimo kruh; [open] = brána otevřená (jasnější, zelenavý).
     */
    fun veil(frame: Int, open: Boolean = false): IntArray {
        val out = IntArray(VEIL_SIZE * VEIL_SIZE)
        val phase = 2 * PI * (frame % FRAMES) / FRAMES
        for (y in 0 until VEIL_SIZE) for (x in 0 until VEIL_SIZE) {
            val dx = (x - VEIL_R).toDouble(); val dy = (y - VEIL_R).toDouble()
            val d = hypot(dx, dy)
            if (d > VEIL_R + 0.3) continue
            val ang = atan2(dy, dx)
            // dvě ramena spirály, stáčí se ke středu
            val swirl = (sin(2 * ang + d * 0.45 - phase) + 1) / 2
            val edge = (d / VEIL_R).coerceIn(0.0, 1.0)
            var a = 0.10 + swirl * 0.30 + edge * edge * 0.25
            val (r, g, b) = if (open) Triple(150, 240, 190) else Triple(190, 170, 240)
            var cr = r; var cg = g; var cb = b
            // jiskřičky: hash polohy × fáze, drží se ~2 snímky
            val h = ((x * 73856093) xor (y * 19349663) xor (((frame % FRAMES) / 2) * 83492791)) and 0x7fffffff
            if (h % 23 == 0 && d < VEIL_R - 1) { cr = 250; cg = 245; cb = 255; a = 0.85 }
            // tmavý okraj u kamene, ať závoj nepřetéká přes prstenec
            if (d > VEIL_R - 0.6) a *= 0.5
            val alpha = (a * 255).toInt().coerceIn(0, 255)
            out[y * VEIL_SIZE + x] = (alpha shl 24) or (cr shl 16) or (cg shl 8) or cb
        }
        return out
    }

    /** Obláček z pixelů (šířka [w]), měkký okraj, bílo-růžový od svítání. */
    fun cloud(w: Int, h: Int, seed: Int): IntArray {
        val out = IntArray(w * h)
        val bumps = (0 until 3).map { i ->
            val cx = w * (0.25 + 0.25 * i) + ((seed * (i + 3)) % 5 - 2)
            Triple(cx, h * 0.62, h * (0.45 + ((seed + i) % 3) * 0.1))
        }
        for (y in 0 until h) for (x in 0 until w) {
            val inside = bumps.any { (cx, cy, r) -> hypot(x - cx, (y - cy) * 1.4) <= r } &&
                y >= h * 0.25 && y < h
            if (!inside) continue
            val shade = if (y > h * 0.75) 0xFFE8D8E8.toInt() else 0xFFFFF4F4.toInt()
            val a = if (x == 0 || x == w - 1) 0x90 else 0xE0
            out[y * w + x] = (a shl 24) or (shade and 0x00FFFFFF)
        }
        return out
    }

    /** Vodorovná poloha obláčku v čase: pomalý posun zleva doprava a znovu (art px). */
    fun cloudX(tMs: Long, speedPxPerSec: Double, startX: Double, span: Int, w: Int): Double {
        val total = span + w
        val p = ((startX + tMs / 1000.0 * speedPxPerSec) % total + total) % total
        return p - w
    }

    /** Srdce Hvozdu v lůžku (5×5 px jantar s lístkem), kreslí se na mapu i do detailu brány. */
    val HEART_IN_SOCKET: IntArray = run {
        val a = 0xFFD8841C.toInt(); val l = 0xFFF4B840.toInt(); val g = 0xFF4EB84A.toInt(); val o = 0xFF5A300C.toInt()
        intArrayOf(
            0, 0, g, 0, 0,
            0, o, a, o, 0,
            o, a, l, a, o,
            o, a, a, a, o,
            0, o, o, o, 0
        )
    }

    /** Průhledné [top] přes neprůhledné [base] (ARGB, na stejném místě). */
    fun blend(base: Int, top: Int): Int {
        val ta = top ushr 24
        if (ta == 0) return base
        if (ta == 255) return top
        fun ch(sh: Int) = (((top ushr sh) and 0xFF) * ta + ((base ushr sh) and 0xFF) * (255 - ta)) / 255
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * Detail brány pro tabuli: výřez mapy [base] (od [cropX], [cropY], [w]×[h]) se závojem
     * a – když je brána otevřená – se Srdcem Hvozdu v lůžku (statický obrázek mapy ani jedno nemá).
     */
    fun gateDetail(base: IntArray, cropX: Int, cropY: Int, w: Int, h: Int, open: Boolean): IntArray {
        val out = base.copyOf()
        val v = veil(0, open)
        val ox = GATE_X - VEIL_R - cropX; val oy = GATE_Y - VEIL_R - cropY
        for (y in 0 until VEIL_SIZE) for (x in 0 until VEIL_SIZE) {
            val tx = ox + x; val ty = oy + y
            if (tx in 0 until w && ty in 0 until h) out[ty * w + tx] = blend(out[ty * w + tx], v[y * VEIL_SIZE + x])
        }
        if (open) {
            val hx = SOCKET_X - 2 - cropX; val hy = SOCKET_Y - 2 - cropY
            for (y in 0 until 5) for (x in 0 until 5) {
                val c = HEART_IN_SOCKET[y * 5 + x]
                if (c != 0 && hx + x in 0 until w && hy + y in 0 until h) out[(hy + y) * w + hx + x] = c
            }
        }
        return out
    }

    /** Pulz lůžka 0..1 (sinus s periodou [periodMs]). */
    fun pulse(tMs: Long, periodMs: Long = 2200): Double = (1 - cos(2 * PI * (tMs % periodMs) / periodMs)) / 2
}
