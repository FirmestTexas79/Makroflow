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

/** Jednoduchý časovaný průběh 0..1 (snímky po 16 ms). */
internal fun PokemonBattleView.tween(ms: Long, update: (Float) -> Unit, end: () -> Unit) {
    val start = android.os.SystemClock.uptimeMillis()
    handler.post(object : Runnable {
        override fun run() {
            val p = ((android.os.SystemClock.uptimeMillis() - start) / ms.toFloat()).coerceIn(0f, 1f)
            update(p); invalidate()
            if (p < 1f) handler.postDelayed(this, 16) else end()
        }
    })
}

internal fun PokemonBattleView.bounce(p: Float): Float {
    val n = 7.5625f; val d = 2.75f
    return when {
        p < 1f / d -> n * p * p
        p < 2f / d -> { val q = p - 1.5f / d; n * q * q + 0.75f }
        p < 2.5f / d -> { val q = p - 2.25f / d; n * q * q + 0.9375f }
        else -> { val q = p - 2.625f / d; n * q * q + 0.984375f }
    }
}

internal fun PokemonBattleView.throwBall(b: cz.uhk.macroflow.pokemon.balls.Makroball) {
    if (gs.enemy.currentHp <= 0) return
    // Strážce ani legendu chytit nejde – ball se nespotřebuje
    special?.let { sp -> val (l1, l2) = sp.noCatchLines; say(l1, l2) { showMain() }; return }
    if (trainer != null) { say("THE TRAINER", "BLOCKED THE BALL!") { showMain() }; return }
    ball = b; busy = true; gs.phase = BattlePhase.BALL_THROW
    ballCounts[b] = ((ballCounts[b] ?: 1) - 1).coerceAtLeast(0)
    Thread {
        db.userItemDao().consumeItem(b.id, 1)
        val total = cz.uhk.macroflow.pokemon.balls.Makroball.entries.sumOf { db.userItemDao().getItemCount(it.id) ?: 0 }
        handler.post { gs.ballCount = total }
    }.start()

    setText("THREW A", "${b.label.uppercase()}!")

    // Let obloukem od hráče ke středu soupeře; ball se při letu točí
    val sx = 30f; val sy = 64f
    val tx = 112f; val ty = 54f - enemySpriteHeight() / 2f
    val speedStep = when (gs.enemy.name) {
        "FINLET", "SPIRRA", "MYCIT"   -> 0.055f
        "AXLU", "DRAKIRRA"            -> 0.025f
        "SERPFIN", "GUDWIN"           -> 0.030f
        "SOULORD", "PHANTIAX"         -> 0.030f
        else                          -> 0.040f
    }
    val flightMs = (20f / speedStep).toLong()
    ballShown = true; ballOpen = 0f; ballRot = 0f; enemyAbsorb = 0f; captureBeam = 0f
    tween(flightMs, { p ->
        ballCx = sx + (tx - sx) * p
        ballCy = sy + (ty - sy) * p - sin(p * PI.toFloat()) * 26f
        ballRot = p * 720f
    }) { onBallHit() }
}

/** Dopad: víčko se odklopí, Makromon se změní ve světlo a vletí dovnitř, ball dopadne na zem. */
internal fun PokemonBattleView.onBallHit() {
    ballRot = 0f
    tween(170, { p -> ballOpen = 60f * p; captureBeam = p }) {
        tween(430, { p -> enemyAbsorb = p }) {
            gs.enemyVisible = false; enemyAbsorb = 0f
            tween(170, { p -> ballOpen = 60f * (1f - p); captureBeam = 1f - p }) {
                val y0 = ballCy
                val ground = 54f - cz.uhk.macroflow.pokemon.balls.Makroball.SIZE / 2f
                tween(360, { p -> ballCy = y0 + (ground - y0) * bounce(p) }) { startWobbleBall() }
            }
        }
    }
}

internal fun PokemonBattleView.startWobbleBall() {
    val baseMultiplier  = BattleFactory.catchMultiplier(gs.enemy)
    // Spící / paralyzovaný / otrávený soupeř se chytá snáz
    val finalMultiplier = baseMultiplier * ball.catchMultiplier * cz.uhk.macroflow.pokemon.status.StatusRules.catchBonus(enemyCond)
    val (success, wobbles) = BattleEngine.calcCaptureResult(gs.enemy, finalMultiplier)
    gs.captureSuccess = success; gs.wobbleCount = wobbles; gs.wobbleDone = 0
    gs.phase = BattlePhase.BALL_WOBBLE
    setText("...", "")
    handler.postDelayed({ doWobble() }, 350)
}

/** Kolébání ballu na zemi (pivot ve spodku) – počet podle šance na chycení. */
internal fun PokemonBattleView.doWobble() {
    if (gs.wobbleDone >= gs.wobbleCount) {
        handler.postDelayed({ if (gs.captureSuccess) caught() else breakFree() }, 380)
        return
    }
    gs.wobbleDone++
    tween(540, { p -> ballRot = 24f * sin(p * 2f * PI.toFloat()) * (1f - 0.35f * p) }) {
        ballRot = 0f
        handler.postDelayed({ doWobble() }, 420)
    }
}

internal fun PokemonBattleView.breakFree() {
    // Ball se otevře a Makromon z něj vyskočí zpátky
    tween(150, { p -> ballOpen = 75f * p; captureBeam = p }) {
        gs.enemyVisible = true; enemyAbsorb = 1f
        tween(320, { p -> enemyAbsorb = 1f - p; captureBeam = 1f - p }) {
            ballShown = false; ballOpen = 0f; enemyAbsorb = 0f; captureBeam = 0f
            afterBreakFree()
        }
    }
}

internal fun PokemonBattleView.afterBreakFree() {
    invalidate()

    val runAwayChance = when (gs.enemy.name) {
        "AXLU"              -> 0.40f
        "DRAKIRRA"          -> 0.30f
        "SOULORD", "PHANTIAX" -> 0.25f
        "SERPFIN", "GUDWIN" -> 0.20f
        "MYDRUS", "LUMEX"   -> 0.15f
        else                -> 0.08f
    }

    busy = false
    val flee = cz.uhk.macroflow.pokemon.skills.CatchRules.fleeChance(runAwayChance.toDouble(), catchingPassive)

    if (Random.nextDouble() < flee) {
        gs.phase = BattlePhase.ESCAPED
        setText("${gs.enemy.name}", "RAN AWAY!")
        pendingAction = { onCaught?.invoke() }
    } else {
        setText("${gs.enemy.name}", "BROKE FREE!")
        gs.phase = BattlePhase.TEXT_WAIT
        pendingAction = { showMain() }
    }
}
