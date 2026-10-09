package cz.uhk.macroflow.pokemon

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import cz.uhk.macroflow.common.MainActivity
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random
import cz.uhk.macroflow.pokemon.PokemonBattleView.*

// Část souboje PokemonBattleView (docs/adr/0080) – rozšiřující funkce nad stavem pohledu.

internal fun PokemonBattleView.rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

internal fun PokemonBattleView.statusFx(onPlayer: Boolean, kind: cz.uhk.macroflow.pokemon.status.EffectKind?, ambient: Boolean = false) {
    val t = now()
    val shape = when (kind) {
        cz.uhk.macroflow.pokemon.status.EffectKind.SLEEP -> FxShape.Z
        cz.uhk.macroflow.pokemon.status.EffectKind.PARALYZE -> FxShape.SPARK
        cz.uhk.macroflow.pokemon.status.EffectKind.POISON -> FxShape.BUBBLE
        cz.uhk.macroflow.pokemon.status.EffectKind.BURN -> FxShape.FLAME
        cz.uhk.macroflow.pokemon.status.EffectKind.CONFUSE -> FxShape.STAR
        cz.uhk.macroflow.pokemon.status.EffectKind.FLINCH -> return
        cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_ATK, cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_DEF -> { statFx(onPlayer, false); return }
        cz.uhk.macroflow.pokemon.status.EffectKind.RAISE_ATK, cz.uhk.macroflow.pokemon.status.EffectKind.RAISE_DEF -> { statFx(onPlayer, true); return }
        null -> FxShape.HEAL
    }
    val n = when (shape) {
        FxShape.Z -> if (ambient) 1 else 3
        FxShape.STAR -> 3
        else -> if (ambient) 2 else 8
    }
    repeat(n) { i ->
        fxList += when (shape) {
            FxShape.Z -> Fx(onPlayer, shape, 5f, -8f, 9f, -13f, t + i * 260L, 1100, 3.2f + i * 1.1f, 0f)
            FxShape.SPARK -> Fx(onPlayer, shape, rnd(-13f, 13f), rnd(-12f, 12f), 0f, 0f, t + (rnd(0f, 320f)).toLong(), 240, rnd(3f, 5f), rnd(0f, 360f))
            FxShape.BUBBLE -> Fx(onPlayer, shape, rnd(-10f, 10f), rnd(2f, 12f), rnd(-3f, 3f), rnd(-26f, -16f), t + i * 70L, 900, rnd(1.2f, 2.6f), 0f)
            FxShape.FLAME -> Fx(onPlayer, shape, rnd(-11f, 11f), rnd(4f, 12f), rnd(-2f, 2f), -18f, t + i * 60L, 700, rnd(2f, 3.4f), 0f)
            FxShape.STAR -> Fx(onPlayer, shape, 0f, -15f, 0f, 0f, t, if (ambient) 900 else 1500, 2.4f, i * 120f)
            FxShape.HEAL -> Fx(onPlayer, shape, rnd(-12f, 12f), rnd(-4f, 12f), 0f, -20f, t + i * 50L, 800, 1.6f, 0f)
            FxShape.ARROW_DOWN, FxShape.ARROW_UP -> return   // šipky řeší statFx
        }
    }
    ensureFxLoop()
}

/** Šipky přes Makromona: modré dolů (statistika klesla), červené nahoru (stoupla). */
internal fun PokemonBattleView.statFx(onPlayer: Boolean, up: Boolean) {
    val t = now()
    repeat(6) { i ->
        fxList += Fx(onPlayer, if (up) FxShape.ARROW_UP else FxShape.ARROW_DOWN,
            -12f + (i % 3) * 12f, if (up) 10f else -14f, 0f, if (up) -26f else 26f,
            t + i * 90L, 650, 3.2f, 0f)
    }
    ensureFxLoop()
}

internal fun PokemonBattleView.ambientFor(onPlayer: Boolean) {
    val c = condOf(onPlayer)
    if (monOf(onPlayer).currentHp <= 0) return
    val kind = when (c.major) {
        cz.uhk.macroflow.pokemon.status.StatusKind.SLEEP -> cz.uhk.macroflow.pokemon.status.EffectKind.SLEEP
        cz.uhk.macroflow.pokemon.status.StatusKind.PARALYSIS -> cz.uhk.macroflow.pokemon.status.EffectKind.PARALYZE
        cz.uhk.macroflow.pokemon.status.StatusKind.POISON -> cz.uhk.macroflow.pokemon.status.EffectKind.POISON
        cz.uhk.macroflow.pokemon.status.StatusKind.BURN -> cz.uhk.macroflow.pokemon.status.EffectKind.BURN
        null -> if (c.confused) cz.uhk.macroflow.pokemon.status.EffectKind.CONFUSE else return
    }
    statusFx(onPlayer, kind, ambient = true)
}

internal fun PokemonBattleView.ensureFxLoop() { if (!fxLoop) { fxLoop = true; nextAmbientAt = now() + 1700; handler.post(fxTick) } }

/** Střed Makromona v herních souřadnicích (hráč vlevo dole, soupeř vpravo nahoře). */
internal fun PokemonBattleView.centerGb(onPlayer: Boolean): PointF =
    if (onPlayer) PointF(48f - gs.introOffset * 100f, PLAYER_FOOT_Y - PLAYER_H * 0.5f)
    else PointF(112f + gs.introOffset * 100f, 54f - enemySpriteHeight() / 2f)

internal fun PokemonBattleView.drawStatusFx(canvas: Canvas) {
    if (fxList.isEmpty() || !::gs.isInitialized) return
    val sc = scale; val t = now()
    for (f in fxList.toList()) {
        val age = t - f.born
        if (age < 0) continue
        if (!f.onPlayer && !gs.enemyVisible) continue
        val q = (age / f.life.toFloat()).coerceIn(0f, 1f)
        val tau = age / 1000f
        val c = centerGb(f.onPlayer)
        val x = gbX(c.x + f.x0 + f.vx * tau); val y = gbY(c.y + f.y0 + f.vy * tau)
        val a = ((if (q < 0.15f) q / 0.15f else 1f - (q - 0.15f) / 0.85f) * 255).toInt().coerceIn(0, 255)
        when (f.shape) {
            FxShape.Z -> {
                fxPaint.style = Paint.Style.FILL; fxPaint.typeface = Typeface.DEFAULT_BOLD
                fxPaint.textSize = f.size * sc * 1.7f
                fxPaint.color = Color.WHITE; fxPaint.alpha = a
                canvas.drawText("Z", x + sc * 0.6f, y + sc * 0.6f, fxPaint)
                fxPaint.color = 0xFF34467E.toInt(); fxPaint.alpha = a
                canvas.drawText("Z", x, y, fxPaint)
            }
            FxShape.SPARK -> {
                val flicker = if ((age / 40) % 2 == 0L) a else a / 3
                fxPaint.style = Paint.Style.STROKE; fxPaint.strokeWidth = 1.1f * sc
                fxPaint.color = 0xFFFFE066.toInt(); fxPaint.alpha = flicker
                val r = Math.toRadians(f.phase.toDouble()); val dx = cos(r).toFloat() * f.size * sc; val dy = sin(r).toFloat() * f.size * sc
                fxPath.reset(); fxPath.moveTo(x - dx, y - dy)
                fxPath.lineTo(x - dx * 0.2f + dy * 0.35f, y - dy * 0.2f - dx * 0.35f)
                fxPath.lineTo(x + dx * 0.2f - dy * 0.35f, y + dy * 0.2f + dx * 0.35f)
                fxPath.lineTo(x + dx, y + dy)
                canvas.drawPath(fxPath, fxPaint)
                fxPaint.style = Paint.Style.FILL
            }
            FxShape.BUBBLE -> {
                fxPaint.style = Paint.Style.FILL
                fxPaint.color = 0xFFA35BD1.toInt(); fxPaint.alpha = (a * 0.85f).toInt()
                canvas.drawCircle(x, y, f.size * sc, fxPaint)
                fxPaint.color = 0xFFE7C8F7.toInt(); fxPaint.alpha = a
                canvas.drawCircle(x - f.size * sc * 0.35f, y - f.size * sc * 0.35f, f.size * sc * 0.3f, fxPaint)
            }
            FxShape.FLAME -> {
                val r = f.size * sc * (1f - 0.6f * q)
                fxPaint.style = Paint.Style.FILL
                fxPaint.color = 0xFFFF6A1A.toInt(); fxPaint.alpha = a
                canvas.drawCircle(x, y, r, fxPaint)
                fxPath.reset(); fxPath.moveTo(x - r, y); fxPath.lineTo(x, y - r * 2.2f); fxPath.lineTo(x + r, y); fxPath.close()
                canvas.drawPath(fxPath, fxPaint)
                fxPaint.color = 0xFFFFD54F.toInt(); fxPaint.alpha = a
                canvas.drawCircle(x, y + r * 0.2f, r * 0.5f, fxPaint)
            }
            FxShape.STAR -> {
                // Hvězdičky krouží nad hlavou zmateného Makromona
                val ang = Math.toRadians((f.phase + tau * 260f).toDouble())
                val sx = gbX(c.x + cos(ang).toFloat() * 10f); val sy = gbY(c.y + f.y0 + sin(ang).toFloat() * 3f)
                drawStar(canvas, sx, sy, f.size * sc, tau * 180f, 0xFFFFE066.toInt(), a)
            }
            FxShape.ARROW_DOWN, FxShape.ARROW_UP -> {
                val r = f.size * sc
                val dir = if (f.shape == FxShape.ARROW_UP) -1f else 1f
                fxPaint.style = Paint.Style.STROKE; fxPaint.strokeWidth = 1.4f * sc; fxPaint.strokeCap = Paint.Cap.ROUND
                fxPaint.color = if (f.shape == FxShape.ARROW_UP) 0xFFE5533D.toInt() else 0xFF3F6FD8.toInt()
                fxPaint.alpha = a
                // Dvojitá šipka (chevron) ve směru pohybu
                for (k in 0..1) {
                    val yy = y + k * r * 0.9f * dir
                    fxPath.reset(); fxPath.moveTo(x - r, yy - r * 0.6f * dir); fxPath.lineTo(x, yy + r * 0.4f * dir); fxPath.lineTo(x + r, yy - r * 0.6f * dir)
                    canvas.drawPath(fxPath, fxPaint)
                }
                fxPaint.style = Paint.Style.FILL
            }
            FxShape.HEAL -> {
                fxPaint.style = Paint.Style.FILL
                fxPaint.color = 0xFF7CD957.toInt(); fxPaint.alpha = a
                val r = f.size * sc
                canvas.drawRect(x - r * 1.5f, y - r * 0.5f, x + r * 1.5f, y + r * 0.5f, fxPaint)
                canvas.drawRect(x - r * 0.5f, y - r * 1.5f, x + r * 0.5f, y + r * 1.5f, fxPaint)
            }
        }
    }
    fxPaint.alpha = 255
}

/** Přehraje animaci útoku; [onHit] přijde v okamžiku zásahu (záblesk, zranění). */
internal fun PokemonBattleView.playMoveAnim(isPlayer: Boolean, mv: Move, onHit: () -> Unit) {
    val spec = cz.uhk.macroflow.pokemon.battlefx.MoveAnims.spec(mv.name, mv.type.name, mv.power)
    moveAnim = MoveAnim(spec, isPlayer, now(), Random.nextInt(1000), onHit)
    handler.post(moveTick)
}

/** Je Makromon právě „bliknutý“ (po zásahu se na chvíli schová)? */
internal fun PokemonBattleView.hiddenByBlink(onPlayer: Boolean): Boolean =
    onPlayer == hitBlinkOnPlayer && now() < hitBlinkUntil && ((hitBlinkUntil - now()) / 70) % 2 == 0L

internal fun PokemonBattleView.drawMoveParticles(canvas: Canvas) {
    val f = moveFrame ?: return
    val sc = scale
    for (p in f.particles) {
        val x = gbX(p.x); val y = gbY(p.y); val r = (p.r * sc).coerceAtLeast(1f)
        moveFxPaint.color = p.color; moveFxPaint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
        moveFxPaint.style = Paint.Style.FILL
        when (p.shape) {
            cz.uhk.macroflow.pokemon.battlefx.MoveAnims.Shape.CIRCLE -> {
                if (p.r > 8f) {
                    // velká záře: měkký kruhový přechod
                    moveFxPaint.shader = android.graphics.RadialGradient(x, y, r, p.color and 0x00FFFFFF or (moveFxPaint.alpha shl 24),
                        p.color and 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP)
                    canvas.drawCircle(x, y, r, moveFxPaint); moveFxPaint.shader = null
                } else canvas.drawCircle(x, y, r, moveFxPaint)
            }
            cz.uhk.macroflow.pokemon.battlefx.MoveAnims.Shape.SQUARE -> canvas.drawRect(x - r, y - r, x + r, y + r, moveFxPaint)
            cz.uhk.macroflow.pokemon.battlefx.MoveAnims.Shape.LINE -> {
                moveFxPaint.style = Paint.Style.STROKE; moveFxPaint.strokeWidth = r; moveFxPaint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(x, y, gbX(p.x2), gbY(p.y2), moveFxPaint)
            }
            cz.uhk.macroflow.pokemon.battlefx.MoveAnims.Shape.RING -> {
                moveFxPaint.style = Paint.Style.STROKE; moveFxPaint.strokeWidth = 1.2f * sc
                canvas.drawCircle(x, y, r, moveFxPaint)
            }
            cz.uhk.macroflow.pokemon.battlefx.MoveAnims.Shape.STAR -> drawStar(canvas, x, y, r, p.rot, p.color, moveFxPaint.alpha)
            cz.uhk.macroflow.pokemon.battlefx.MoveAnims.Shape.LEAF -> {
                canvas.save(); canvas.rotate(p.rot, x, y)
                canvas.drawOval(x - r, y - r * 0.45f, x + r, y + r * 0.45f, moveFxPaint)
                canvas.restore()
            }
            cz.uhk.macroflow.pokemon.battlefx.MoveAnims.Shape.HEART -> {
                canvas.drawCircle(x - r * 0.45f, y - r * 0.2f, r * 0.55f, moveFxPaint)
                canvas.drawCircle(x + r * 0.45f, y - r * 0.2f, r * 0.55f, moveFxPaint)
                moveFxPath.reset(); moveFxPath.moveTo(x - r, y); moveFxPath.lineTo(x + r, y); moveFxPath.lineTo(x, y + r * 1.1f); moveFxPath.close()
                canvas.drawPath(moveFxPath, moveFxPaint)
            }
            cz.uhk.macroflow.pokemon.battlefx.MoveAnims.Shape.NOTE -> {
                canvas.drawOval(x - r * 0.7f, y - r * 0.45f, x + r * 0.5f, y + r * 0.45f, moveFxPaint)
                moveFxPaint.style = Paint.Style.STROKE; moveFxPaint.strokeWidth = 0.8f * sc
                canvas.drawLine(x + r * 0.45f, y, x + r * 0.45f, y - r * 2f, moveFxPaint)
                canvas.drawLine(x + r * 0.45f, y - r * 2f, x + r * 1.2f, y - r * 1.4f, moveFxPaint)
            }
            cz.uhk.macroflow.pokemon.battlefx.MoveAnims.Shape.FLAME -> {
                moveFxPath.reset(); moveFxPath.moveTo(x, y - r * 1.8f)
                moveFxPath.quadTo(x + r * 1.1f, y - r * 0.2f, x, y + r); moveFxPath.quadTo(x - r * 1.1f, y - r * 0.2f, x, y - r * 1.8f)
                canvas.drawPath(moveFxPath, moveFxPaint)
                moveFxPaint.color = 0xFFFFF4B0.toInt(); moveFxPaint.alpha = (p.alpha * 200).toInt()
                canvas.drawCircle(x, y + r * 0.2f, r * 0.4f, moveFxPaint)
            }
            cz.uhk.macroflow.pokemon.battlefx.MoveAnims.Shape.TOOTH -> {
                val dir = if (p.rot > 90f) 1f else -1f        // 180° = zub dolů (horní čelist)
                moveFxPath.reset(); moveFxPath.moveTo(x - r, y - dir * r); moveFxPath.lineTo(x + r, y - dir * r); moveFxPath.lineTo(x, y + dir * r * 1.2f); moveFxPath.close()
                canvas.drawPath(moveFxPath, moveFxPaint)
            }
        }
    }
}

internal fun PokemonBattleView.doFlash(after: () -> Unit) {
    flashOn = true; invalidate()
    handler.postDelayed({ flashOn = false; invalidate(); after() }, 180)
}
