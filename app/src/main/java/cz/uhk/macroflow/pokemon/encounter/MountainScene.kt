package cz.uhk.macroflow.pokemon.encounter

import cz.uhk.macroflow.pokemon.encounter.MountainIntro.BACK_RISE_END
import cz.uhk.macroflow.pokemon.encounter.MountainIntro.BACK_RISE_START
import cz.uhk.macroflow.pokemon.encounter.MountainIntro.CRACK_START
import cz.uhk.macroflow.pokemon.encounter.MountainIntro.FRONT_RISE_END
import cz.uhk.macroflow.pokemon.encounter.MountainIntro.FRONT_RISE_START
import cz.uhk.macroflow.pokemon.encounter.MountainIntro.RUMBLE_START
import cz.uhk.macroflow.pokemon.encounter.MountainIntro.SKY_IN_END
import cz.uhk.macroflow.pokemon.encounter.MountainIntro.SPLIT_END
import cz.uhk.macroflow.pokemon.encounter.MountainIntro.SPLIT_START
import cz.uhk.macroflow.pokemon.encounter.MountainIntro.easeInQuad
import cz.uhk.macroflow.pokemon.encounter.MountainIntro.easeOutCubic
import cz.uhk.macroflow.pokemon.encounter.MountainIntro.progress
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Intro setkání v Horách jako pixel art v nízkém rozlišení (čistý Kotlin, bez Androidu).
 *
 * Každý snímek je čistá funkce času [render] – žádný stav mezi snímky. Díky tomu jde intro
 * přeskočit prostým skokem v čase, vyrenderovat mimo telefon do PNG (náhled) a testovat.
 * Paleta odpovídá mapě mountains.png (tools/mapgen/gen_mountains.py).
 */
class MountainScene(val w: Int, val h: Int, seed: Int = 42) {

    // ── Paleta ──
    private val sky = intArrayOf(rgb(46, 28, 38), rgb(78, 40, 46), rgb(126, 62, 56), rgb(186, 98, 66), rgb(228, 146, 86), rgb(246, 196, 128))
    private val sunCore = rgb(255, 234, 176); private val sunRing = rgb(250, 206, 132)
    private val backBody = rgb(140, 86, 74); private val backRim = rgb(180, 118, 98); private val backDark = rgb(118, 70, 62)
    private val frontRim = rgb(224, 184, 146); private val frontTop = rgb(212, 170, 132)
    private val frontBody = rgb(166, 108, 72); private val frontStrata = rgb(124, 76, 50)
    private val sand = intArrayOf(rgb(204, 160, 92), rgb(215, 172, 102), rgb(223, 183, 113))
    private val sandShadow = rgb(177, 137, 88)
    private val stone = intArrayOf(rgb(118, 114, 106), rgb(150, 146, 134), rgb(172, 168, 154), rgb(196, 192, 176))
    private val stoneOutline = rgb(74, 62, 56); private val crackColor = rgb(52, 38, 32)
    private val dust = rgb(238, 212, 162)

    // ── Rozvržení ──
    val horizon = (h * 0.50f).toInt()
    private val backBase = horizon + (h * 0.05f).toInt()
    private val frontBase = horizon + (h * 0.15f).toInt()
    val groundTop = frontBase + 4
    val groundY = groundTop + ((h - groundTop) * 0.52f).toInt()   // spodek balvanu
    val bw = (w * 0.50f).toInt().coerceAtLeast(16)
    val bh = (bw * 0.84f).toInt()

    // ── Předkreslené vrstvy (0 = průhledné) ──
    private val skyLayer = IntArray(w * h)
    private val backLayer = IntArray(w * h)
    private val frontLayer = IntArray(w * h)
    private val halfL = IntArray(bw * bh)
    private val halfR = IntArray(bw * bh)
    private val crackX = IntArray(bh) { y -> bw / 2 + MountainIntro.crackOffset(y) }

    // ── Částice (dané předem, poloha se počítá z času) ──
    private class Puff(val x: Float, val y: Float, val t0: Long, val life: Long, val r0: Float, val r1: Float, val vx: Float)
    private class Pebble(val x: Float, val t0: Long, val size: Int, val color: Int)
    private class Chunk(val vx: Float, val vy: Float, val size: Int, val color: Int, val ox: Float, val oy: Float)

    private val puffs = mutableListOf<Puff>()
    private val pebbles = mutableListOf<Pebble>()
    private val chunks = mutableListOf<Chunk>()

    private val frame = IntArray(w * h)

    init {
        val rnd = Random(seed)
        buildSky(); buildBack(); buildFront(rnd); buildBoulder(rnd)

        val cx = w / 2f
        // Dunění: obláčky prachu u paty balvanu
        for (k in 0 until 7) puffs += Puff(cx + (rnd.nextFloat() - 0.5f) * bw, groundY - 1f, RUMBLE_START + k * 105L, 520,
            1.5f, 4.5f + rnd.nextFloat() * 2f, (rnd.nextFloat() - 0.5f) * 10f)
        // Rozpůlení: velký mrak
        for (k in 0 until 12) {
            val a = rnd.nextFloat() * PI.toFloat()
            puffs += Puff(cx + cos(a) * bw * 0.2f, groundY - bh * 0.35f + (rnd.nextFloat() - 0.5f) * bh * 0.4f, SPLIT_START + k * 18L,
                620 + rnd.nextLong(200), 3f, 9f + rnd.nextFloat() * 6f, (rnd.nextFloat() - 0.5f) * 36f)
        }
        // Kamínky padající shora při otřesech
        for (k in 0 until 9) pebbles += Pebble(rnd.nextFloat() * w, RUMBLE_START + rnd.nextLong(SPLIT_START - RUMBLE_START),
            2 + rnd.nextInt(2), if (k % 2 == 0) frontStrata else stone[0])
        // Úlomky z praskliny
        for (k in 0 until 22) {
            val a = -PI / 2 + (rnd.nextDouble() - 0.5) * PI * 1.3
            val v = 40f + rnd.nextFloat() * 70f
            chunks += Chunk((cos(a) * v).toFloat(), (sin(a) * v).toFloat(), 1 + rnd.nextInt(3), stone[rnd.nextInt(4)],
                (rnd.nextFloat() - 0.5f) * 6f, (rnd.nextFloat() - 0.5f) * bh * 0.6f)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Vykreslí snímek v čase [t] (ms od startu) do [out] (w × h ARGB). */
    fun render(t: Long, out: IntArray) {
        val backOff = ((1f - easeOutCubic(progress(t, BACK_RISE_START, BACK_RISE_END))) * h * 0.6f).roundToInt()
        val frontOff = ((1f - easeOutCubic(progress(t, FRONT_RISE_START, FRONT_RISE_END))) * h * 0.6f).roundToInt()

        skyLayer.copyInto(frame)
        blit(backLayer, w, h, 0, backOff)
        blit(frontLayer, w, h, 0, frontOff)

        // Stín pod balvanem – po rozpůlení mizí
        val split = progress(t, SPLIT_START, SPLIT_END)
        val shadowA = (1f - split * 1.6f).coerceIn(0f, 1f)
        if (shadowA > 0f) ellipse(w / 2f, groundY + frontOff.toFloat(), bw * 0.46f, 2.5f, sandShadow, 0.8f * shadowA)

        // Balvan: dunění = třes o pixel, pak rozpůlení
        val jitter = if (t in RUMBLE_START until SPLIT_START) (if ((t / 55) % 2 == 0L) 1 else -1) else 0
        val bx = w / 2 - bw / 2 + jitter
        val by = groundY - bh + frontOff
        if (t < SPLIT_START) {
            blit(halfL, bw, bh, bx, by)
            blit(halfR, bw, bh, bx, by)
            val crackRows = (progress(t, CRACK_START, SPLIT_START) * bh).toInt()
            for (y in 0 until crackRows) {
                val x = crackX[y]
                if (halfL[y * bw + x.coerceIn(0, bw - 1)] != 0 || halfR[y * bw + x.coerceIn(0, bw - 1)] != 0) {
                    put(bx + x, by + y, crackColor)
                    if (y % 3 == 0) put(bx + x + 1, by + y, stone[3])   // světlá hrana lomu
                }
            }
        } else {
            val e = easeInQuad(split)
            val lift = MountainIntro.halfLift(split, bh * 0.35f)
            blitRotated(halfL, bw, bh, w / 2f - bw / 4f - e * w * 0.8f, by + bh / 2f + lift, -40f * split)
            blitRotated(halfR, bw, bh, w / 2f + bw / 4f + e * w * 0.8f, by + bh / 2f + lift, 40f * split)
        }

        // Kamínky padají od začátku dunění až na zem
        for (p in pebbles) {
            val tau = (t - p.t0) / 1000f
            if (tau < 0f) continue
            val y = -2f + 0.5f * 260f * tau * tau
            if (y > groundY + frontOff) continue
            rect(p.x.toInt(), y.toInt(), p.size, p.size, p.color)
        }

        // Prach
        for (pf in puffs) {
            val age = t - pf.t0
            if (age < 0 || age > pf.life) continue
            val q = age / pf.life.toFloat()
            val r = pf.r0 + (pf.r1 - pf.r0) * easeOutCubic(q)
            circle(pf.x + pf.vx * q, pf.y - q * 4f + frontOff, r, dust, 0.75f * (1f - q))
        }

        // Úlomky s gravitací a „kutálením“ (střídání tvaru)
        if (t >= SPLIT_START) {
            val tau = (t - SPLIT_START) / 1000f
            for (c in chunks) {
                val x = w / 2f + c.ox + c.vx * tau
                val y = groundY - bh * 0.45f + frontOff + c.oy + c.vy * tau + 0.5f * 220f * tau * tau
                if (y > h + 4) continue
                val flip = ((tau * 12).toInt() % 2 == 0)
                rect(x.toInt(), y.toInt(), if (flip) c.size else (c.size + 1), if (flip) (c.size + 1) else c.size, c.color)
            }
        }

        // Otřes celé scény + roztmívání z černé
        val amp = MountainIntro.shakeAmplitude(t)
        val sx = if (amp > 0f) ((((t / 45) * 7919) % 3) - 1).toInt() * amp.roundToInt() else 0
        val sy = if (amp > 0f) ((((t / 45) * 104729) % 3) - 1).toInt() * amp.roundToInt() else 0
        val fade = progress(t, 0, SKY_IN_END)
        for (y in 0 until h) {
            val srcY = (y - sy).coerceIn(0, h - 1)
            for (x in 0 until w) {
                val c = frame[srcY * w + (x - sx).coerceIn(0, w - 1)]
                out[y * w + x] = if (fade >= 1f) c else scale(c, fade)
            }
        }
    }

    // ── Stavba vrstev ────────────────────────────────────────────────────────

    private fun buildSky() {
        val bands = sky.size
        for (y in 0 until h) {
            val f = (y.toFloat() / horizon).coerceIn(0f, 0.9999f) * bands
            val i = f.toInt().coerceAtMost(bands - 1)
            val frac = f - i
            for (x in 0 until w) {
                // Pixelový dithering na přechodu pásů (šachovnice v poslední čtvrtině pásu)
                val next = (i + 1).coerceAtMost(bands - 1)
                val c = if (frac > 0.75f && (x + y) % 2 == 0) sky[next] else sky[i]
                skyLayer[y * w + x] = c
            }
        }
        // Slunce nízko nad obzorem
        val scx = w * 0.70f; val scy = horizon - h * 0.07f; val r = w * 0.10f
        for (y in 0 until h) for (x in 0 until w) {
            val d = (x - scx) * (x - scx) + (y - scy) * (y - scy)
            if (d <= r * r) skyLayer[y * w + x] = sunCore
            else if (d <= (r + 1.6f) * (r + 1.6f)) skyLayer[y * w + x] = sunRing
        }
    }

    private fun buildBack() {
        val ridge = MountainIntro.ridge(seed = 7, width = w, amplitude = (h * 0.20f).toInt(), roughness = 0.5f)
        for (x in 0 until w) {
            val top = backBase - ridge[x]
            for (y in top.coerceAtLeast(0) until h) {
                // Opar: dál od hřebene tmavší (dithering), ať hory nejsou ploché
                val depth = (y - top).toFloat() / (h * 0.25f)
                backLayer[y * w + x] = when {
                    y == top -> backRim
                    depth > 0.55f && (x + y) % 2 == 0 -> backDark
                    depth > 0.30f && (x + 2 * y) % 4 == 0 -> backDark
                    else -> backBody
                }
            }
        }
    }

    private fun buildFront(rnd: Random) {
        // Stolové hory (mesy) jako na mapě: hladší hřeben srovnaný do teras
        val raw = MountainIntro.ridge(seed = 13, width = w, amplitude = (h * 0.16f).toInt(), roughness = 0.42f)
        val terrace = (h * 0.025f).toInt().coerceAtLeast(3)
        val ridge = IntArray(w) { x -> raw[x] / terrace * terrace }
        for (x in 0 until w) {
            val top = frontBase - ridge[x]
            for (y in top.coerceAtLeast(0) until h) {
                val d = y - top
                frontLayer[y * w + x] = when {
                    y >= groundTop -> {
                        val band = ((y - groundTop) / 3 + (if ((x + y) % 2 == 0) 0 else 1)) % sand.size
                        sand[band]
                    }
                    d == 0 -> frontRim
                    d <= 2 -> frontTop
                    d % 7 == 5 && x % 4 != 0 -> frontStrata
                    else -> frontBody
                }
            }
        }
        // Kamínky a tmavší zrna v písku
        repeat(w * 2) {
            val x = rnd.nextInt(w); val y = groundTop + 2 + rnd.nextInt((h - groundTop - 2).coerceAtLeast(1))
            frontLayer[y * w + x] = if (rnd.nextInt(4) == 0) stone[1] else sandShadow
        }
    }

    private fun buildBoulder(rnd: Random) {
        val cx = bw / 2f; val cy = bh * 0.56f
        val a = bw / 2f - 1f; val b = bh * 0.56f
        val inside = BooleanArray(bw * bh)
        for (y in 0 until bh) for (x in 0 until bw) {
            if (y > bh - 2) continue                       // plochý spodek (leží na zemi)
            val nx = (x - cx) / a; val ny = (y - cy) / b
            val ang = atan2(ny, nx)
            val wobble = 0.07f * sin(ang * 5f + 1.3f) + 0.04f * sin(ang * 11f)
            inside[y * bw + x] = nx * nx + ny * ny <= 1f - wobble
        }
        for (y in 0 until bh) for (x in 0 until bw) {
            val i = y * bw + x
            if (!inside[i]) continue
            val edge = x == 0 || y == 0 || x == bw - 1 || y == bh - 1 ||
                !inside[i - 1] || !inside[i + 1] || !inside[i - bw] || !inside[i + bw]
            val nx = (x - cx) / a; val ny = (y - cy) / b
            val light = -(nx * 0.6f + ny * 0.8f)               // světlo zleva shora
            var c = when {
                edge -> stoneOutline
                light > 0.55f -> stone[3]
                light > 0.05f -> stone[2]
                light > -0.45f -> stone[1]
                else -> stone[0]
            }
            if (!edge && rnd.nextInt(17) == 0) c = stone[0]   // zrnitost kamene
            if (x < crackX[y]) halfL[i] = c else halfR[i] = c
        }
    }

    // ── Kreslení do snímku ───────────────────────────────────────────────────

    private fun put(x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) frame[y * w + x] = c }

    private fun rect(x: Int, y: Int, rw: Int, rh: Int, c: Int) {
        for (yy in y until y + rh) for (xx in x until x + rw) put(xx, yy, c)
    }

    private fun blit(src: IntArray, sw: Int, sh: Int, ox: Int, oy: Int) {
        for (y in 0 until sh) {
            val ty = y + oy
            if (ty < 0 || ty >= h) continue
            for (x in 0 until sw) {
                val c = src[y * sw + x]
                if (c != 0) { val tx = x + ox; if (tx in 0 until w) frame[ty * w + tx] = c }
            }
        }
    }

    /** Otočení spritu kolem jeho středu (nejbližší soused → zůstává pixel art). */
    private fun blitRotated(src: IntArray, sw: Int, sh: Int, cx: Float, cy: Float, deg: Float) {
        val r = Math.toRadians(deg.toDouble()); val cs = cos(r).toFloat(); val sn = sin(r).toFloat()
        val reach = (maxOf(sw, sh) * 0.75f).toInt()
        for (dy in -reach..reach) for (dx in -reach..reach) {
            val sx = (cs * dx + sn * dy + sw / 2f).toInt()
            val sy = (-sn * dx + cs * dy + sh / 2f).toInt()
            if (sx !in 0 until sw || sy !in 0 until sh) continue
            val c = src[sy * sw + sx]
            if (c != 0) put((cx + dx).toInt(), (cy + dy).toInt(), c)
        }
    }

    private fun circle(cx: Float, cy: Float, r: Float, c: Int, alpha: Float) {
        val ri = r.toInt() + 1
        for (y in (cy - ri).toInt()..(cy + ri).toInt()) for (x in (cx - ri).toInt()..(cx + ri).toInt()) {
            if (x !in 0 until w || y !in 0 until h) continue
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) frame[y * w + x] = mix(frame[y * w + x], c, alpha)
        }
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, c: Int, alpha: Float) {
        for (y in (cy - ry).toInt()..(cy + ry).toInt()) for (x in (cx - rx).toInt()..(cx + rx).toInt()) {
            if (x !in 0 until w || y !in 0 until h) continue
            val nx = (x - cx) / rx; val ny = (y - cy) / ry
            if (nx * nx + ny * ny <= 1f) frame[y * w + x] = mix(frame[y * w + x], c, alpha)
        }
    }

    companion object {
        fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(s: Int) = (((a shr s) and 0xFF) + (((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t).toInt()
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        fun scale(c: Int, f: Float): Int {
            fun ch(s: Int) = (((c shr s) and 0xFF) * f).toInt()
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
