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

/**
 * Načte sprite Makromona z lokálního drawable zdroje.
 * Konvence: makromon_spirra, makromon_ignar, atd.
 *
 * Shiny logika zakomentována – odkomentuj až budou hotové sprity:
 * val drawableName = if (isShiny) "makromon_${name}_shiny" else "makromon_$name"
 */
internal fun PokemonBattleView.loadMakromonSprite(makromon: Makromon, isPlayer: Boolean) {
    // TADY VOLÁME TVOJI FUNKCI Z BATTLE FACTORY
    val resourceName = BattleFactory.drawableName(makromon)

    val resId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    val finalResId = if (resId != 0) resId else cz.uhk.macroflow.R.drawable.ic_home

    // Zbytek zůstává stejný...
    val drawable = context.resources.getDrawable(finalResId, null)
    val plain = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        ?: Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
    // Shiny = stejný sprite přebarvený filtrem (rotace odstínu podle druhu)
    val shiny = if (isPlayer) gs.isPlayerShiny else gs.isEnemyShiny
    val bmp = if (shiny && resId != 0) cz.uhk.macroflow.pokemon.shiny.ShinySprites.recolor(plain, BattleFactory.makrodexId(makromon))
        else plain

    if (isPlayer) playerBitmap = bmp else enemyBitmap = bmp
    maybeStartIntro()
}

/** Vykreslí 3D arénu lokace na pozadí (trvá desítky až stovky ms – běží během intra). */
internal fun PokemonBattleView.startArena(biome: BiomeType) = startArena(cz.uhk.macroflow.pokemon.arena.ArenaTheme.fromBiome(biome.name))

internal fun PokemonBattleView.startArena(theme: cz.uhk.macroflow.pokemon.arena.ArenaTheme) {
    arenaTheme = theme
    arenaSeed = Random.nextInt(1_000_000)
    maybeRenderArena()
}

/** Aréna se kreslí, až je známá lokace i velikost pohledu (kolik se má prodloužit nahoru). */
internal fun PokemonBattleView.maybeRenderArena() {
    if (dstR.width() <= 0f) return
    val A = cz.uhk.macroflow.pokemon.arena.Arenas
    // kolik řádků arény (3 na herní pixel) zabere místo nad herním plátnem
    val extra = kotlin.math.ceil(dstR.top * 3f / scale).toInt().coerceIn(0, 2000)
    if (extra == arenaExtra) return
    arenaExtra = extra
    val theme = arenaTheme; val seed = arenaSeed
    Thread {
        // 1) hrubý náhled (desetina času) → intro může hned skončit, 2) plná aréna se prolne přes něj
        val t0 = android.os.SystemClock.uptimeMillis()
        val (pw, ph) = A.previewSize(extra)
        val pre = runCatching { A.renderPreview(theme, seed, extra) }.getOrNull()
        if (pre != null) {
            val pbmp = Bitmap.createBitmap(pre, pw, ph, Bitmap.Config.ARGB_8888)
            handler.post { if (extra == arenaExtra && arenaBmp == null) showArena(pbmp) }
        }
        val t1 = android.os.SystemClock.uptimeMillis()
        val px = runCatching { A.render(theme, seed, extra) }.getOrNull() ?: return@Thread
        android.util.Log.d("Arena", "$theme extra=$extra threads=${cz.uhk.macroflow.pokemon.arena.VoxelRenderer.threads} náhled ${t1 - t0} ms, plná ${android.os.SystemClock.uptimeMillis() - t1} ms")
        val bmp = Bitmap.createBitmap(px, A.W, A.H + extra, Bitmap.Config.ARGB_8888)
        handler.post { if (extra == arenaExtra) showArena(bmp) }
    }.start()
}

internal fun PokemonBattleView.showArena(bmp: Bitmap) {
    arenaPrevBmp = arenaBmp
    arenaBmp = bmp; arenaShownAt = now(); invalidate()
    if (!arenaReady) {
        arenaReady = true
        onArenaReady?.invoke(); onArenaReady = null
    }
}

/** Barva pozadí, než se aréna dopočítá. */
internal fun PokemonBattleView.arenaFallback(): Pair<Int, Int> = when (arenaTheme) {
    cz.uhk.macroflow.pokemon.arena.ArenaTheme.TOWN -> 0xFFB8DCF0.toInt() to 0xFFC9CDD2.toInt()
    cz.uhk.macroflow.pokemon.arena.ArenaTheme.FOREST -> 0xFF7E9E78.toInt() to 0xFF3B6E2C.toInt()
    cz.uhk.macroflow.pokemon.arena.ArenaTheme.MOUNTAINS -> 0xFFC4D6E6.toInt() to 0xFF858075.toInt()
    cz.uhk.macroflow.pokemon.arena.ArenaTheme.CAVE_OPEN, cz.uhk.macroflow.pokemon.arena.ArenaTheme.CAVE_MAZE -> 0xFF0A080C.toInt() to 0xFF2E2B2C.toInt()
    cz.uhk.macroflow.pokemon.arena.ArenaTheme.WATER -> 0xFFB8DCF0.toInt() to 0xFF2A74AA.toInt()
    cz.uhk.macroflow.pokemon.arena.ArenaTheme.MEADOW -> 0xFFB8DCF0.toInt() to 0xFF58A03A.toInt()
    cz.uhk.macroflow.pokemon.arena.ArenaTheme.COLOSSEUM -> 0xFFF2D6A8.toInt() to 0xFFD2AE74.toInt()
}

internal fun PokemonBattleView.maybeStartIntro() {
    handler.post {
        if (!introStarted) {
            introStarted = true
            startIntro()
        }
        invalidate()
    }
}

internal fun PokemonBattleView.gbX(x: Float) = dstR.left + x * scale

internal fun PokemonBattleView.gbY(y: Float) = dstR.top  + y * scale

internal fun PokemonBattleView.drawSpritesOverlay(canvas: Canvas) {
    val sc = scale
    if (sc <= 0f || !gsReady) return
    val animOffset = (gs.introOffset * 100f) * sc

    val mf = moveFrame; val ma = moveAnim
    // výpad / zmizení útočníka a otřes cíle (docs/adr/0048)
    val enemyAtk = ma != null && !ma.attackerIsPlayer
    val eDx = if (mf == null) 0f else if (enemyAtk) mf.attackerDx else mf.targetDx
    val eDy = if (mf == null || !enemyAtk) 0f else mf.attackerDy
    val eAlpha = if (mf != null && enemyAtk) mf.attackerAlpha else 1f
    if (gs.enemyVisible && !hiddenByBlink(false) && eAlpha > 0.02f) {
        enemyBitmap?.let { bmp ->
            val targetH = enemySpriteHeight() * sc
            val targetW = targetH * bmp.width.toFloat() / bmp.height.toFloat()
            val sx = gbX(112f) - targetW / 2f + animOffset + eDx * sc
            val sy = gbY(54f) - targetH + eDy * sc
            spSmooth.alpha = (eAlpha * 255).toInt()
            if (enemyAbsorb <= 0f) {
                val sw = targetW * 0.42f
                canvas.drawOval(sx + targetW / 2f - sw, gbY(52.6f), sx + targetW / 2f + sw, gbY(55.4f), shadowPaint)
            }
            if (gs.isEnemyShiny && enemyAbsorb == 0f) drawShinyGlow(canvas, sx + targetW / 2f, sy + targetH / 2f, targetH)
            if (enemyAbsorb <= 0f) {
                canvas.drawBitmap(bmp, null, RectF(sx, sy, sx + targetW, sy + targetH), spSmooth)
            } else {
                // Makromon se promění ve světlo, zmenší se a vletí do ballu
                val a = enemyAbsorb
                val ecx = sx + targetW / 2f; val ecy = sy + targetH / 2f
                val cx = ecx + (gbX(ballCx) - ecx) * a
                val cy = ecy + (gbY(ballCy) - ecy) * a
                val w = targetW * (1f - a); val h = targetH * (1f - a)
                val r = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                absorbPaint.colorFilter = null
                absorbPaint.alpha = ((1f - (a * 2f).coerceAtMost(1f)) * 255).toInt()
                canvas.drawBitmap(bmp, null, r, absorbPaint)
                absorbPaint.colorFilter = PorterDuffColorFilter(0xFFFFF6D8.toInt(), PorterDuff.Mode.SRC_ATOP)
                absorbPaint.alpha = ((a * 2f).coerceAtMost(1f) * 255).toInt()
                canvas.drawBitmap(bmp, null, r, absorbPaint)
            }
        }
    }
    drawSparkles(canvas)
    drawBallOverlay(canvas)
    drawStatusFx(canvas)

    spSmooth.alpha = 255
    val playerAtk = ma != null && ma.attackerIsPlayer
    val pDx = if (mf == null) 0f else if (playerAtk) mf.attackerDx else mf.targetDx
    val pDy = if (mf == null || !playerAtk) 0f else mf.attackerDy
    val pAlpha = if (mf != null && playerAtk) mf.attackerAlpha else 1f
    if (!hiddenByBlink(true) && pAlpha > 0.02f) playerBitmap?.let { bmp ->
        spSmooth.alpha = (pAlpha * 255).toInt()
        val targetH = PLAYER_H * sc
        val targetW = targetH * bmp.width.toFloat() / bmp.height.toFloat()
        val cx = gbX(48f) - animOffset + pDx * sc
        val sy = gbY(PLAYER_FOOT_Y) - targetH + pDy * sc
        canvas.save()
        canvas.scale(-1f, 1f, cx, 0f)
        canvas.drawBitmap(bmp, null, RectF(cx - targetW / 2f, sy, cx + targetW / 2f, sy + targetH), spSmooth)
        canvas.restore()
        spSmooth.alpha = 255
    }
}

internal fun PokemonBattleView.enemySpriteHeight(): Float =
    cz.uhk.macroflow.pokemon.species.SpeciesRegistry.byName(gs.enemy.name)?.battleHeight ?: 28f

internal fun PokemonBattleView.renderFrame() {
    val c = gbCvs
    c.drawColor(0, PorterDuff.Mode.CLEAR)
    drawEnemyHUD(c)
    drawPlayerHUD(c)
    drawBottomUI(c)
}

internal fun PokemonBattleView.drawEnemyHUD(c: Canvas) {
    val x = 1; val y = 1; val w = 76; val h = 28; drawHudBox(c, x, y, w, h, rightBracket = true)
    hudText(c, gs.enemy.name.take(7), x+3, y+3)
    hudText(c, ":L${gs.enemy.level}", x+48, y+3)
    hudText(c, "HP", x+3, y+13)
    drawHPBar(c, x+16, y+13, 54, 5, gs.enemy.currentHp, gs.enemy.maxHp)
    // Třetí řádek: stupně statistik (A = útok, D = obrana), jinak štítek shiny
    val stages = enemyCond.stagesLabel
    if (stages != null) hudText(c, stages, x+3, y+20, 0xFF9EC0FF.toInt())
    else if (gs.isEnemyShiny) hudText(c, "*SHINY", x+3, y+20, 0xFFFFD54F.toInt())
    enemyCond.tag?.let { drawStatusTag(c, it, x + 51, y + 19) }
}

/** Štítek stavu (SLP, PAR, PSN, BRN, CNF) v barevném rámečku. */
internal fun PokemonBattleView.drawStatusTag(c: Canvas, tag: String, x: Int, y: Int) {
    fp.color = when (tag) {
        "SLP" -> 0xFF8A8A8A.toInt(); "PAR" -> 0xFFC8960E.toInt(); "PSN" -> 0xFF8E44AD.toInt()
        "BRN" -> 0xFFE0592A.toInt(); else -> 0xFFE84F9A.toInt()
    }
    c.drawRect(x.toFloat(), y.toFloat(), (x + 21).toFloat(), (y + 9).toFloat(), fp)
    PokemonSprites.drawText(c, tag, x + 2, y + 1, 0xFFFFFFFF.toInt(), fp)
}

internal fun PokemonBattleView.drawPlayerHUD(c: Canvas) {
    val x = 84; val y = 58; val w = 75; val h = 37; drawHudBox(c, x, y, w, h, rightBracket = false)
    hudText(c, gs.player.name.take(7), x+3, y+3)
    hudText(c, ":L${gs.player.level}", x+42, y+3)
    hudText(c, "HP", x+3, y+13)
    drawHPBar(c, x+16, y+13, 54, 5, gs.player.currentHp, gs.player.maxHp)
    val hp = "${gs.player.currentHp}/${gs.player.maxHp}"
    hudText(c, hp, x+w-3-hp.length*6, y+22)
    playerCond.tag?.let { drawStatusTag(c, it, x + 3, y + 21) }
    playerCond.stagesLabel?.let { hudText(c, it, x + 3, y + 29, 0xFF9EC0FF.toInt()) }
}

/** Text HUDu nad arénou: bílý s tmavým stínem, aby byl čitelný na každém pozadí. */
internal fun PokemonBattleView.hudText(c: Canvas, s: String, x: Int, y: Int, color: Int = C_HUD_TEXT) {
    PokemonSprites.drawText(c, s, x + 1, y + 1, C_HUD_SHADOW, fp)
    PokemonSprites.drawText(c, s, x, y, color, fp)
}

/** Průsvitný panel HUDu s „hranatou závorkou“ ve stylu Game Boye (spodní hrana + jedna svislá). */
internal fun PokemonBattleView.drawHudBox(c: Canvas, x: Int, y: Int, w: Int, h: Int, rightBracket: Boolean) {
    fp.color = C_HUD_BG; c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), fp)
    fp.color = C_HUD_LINE
    c.drawRect((x + 2).toFloat(), (y + h - 2).toFloat(), (x + w - 2).toFloat(), (y + h - 1).toFloat(), fp)
    val vx = if (rightBracket) x + w - 3 else x + 2
    c.drawRect(vx.toFloat(), (y + h / 2).toFloat(), (vx + 1).toFloat(), (y + h - 1).toFloat(), fp)
    // šipka na konci závorky
    val tip = if (rightBracket) x + 2 else x + w - 3
    val dir = if (rightBracket) 1 else -1
    c.drawRect(tip.toFloat(), (y + h - 3).toFloat(), (tip + 1).toFloat(), (y + h - 2).toFloat(), fp)
    c.drawRect((tip + dir).toFloat(), (y + h - 4).toFloat(), (tip + dir + 1).toFloat(), (y + h - 3).toFloat(), fp)
}

internal fun PokemonBattleView.drawHPBar(c: Canvas, x: Int, y: Int, w: Int, h: Int, cur: Int, max: Int) {
    fp.color = C_HP_BG; c.drawRect(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat(), fp)
    val frac = (cur.toFloat()/max).coerceIn(0f,1f); val fw = (w*frac).toInt()
    if (fw > 0) {
        fp.color = if (frac > 0.5f) C_HP_G else if (frac > 0.25f) C_HP_Y else C_HP_R
        c.drawRect(x.toFloat(), y.toFloat(), (x+fw).toFloat(), (y+h).toFloat(), fp)
    }
    fp.color = C_BORDER
    c.drawRect(x.toFloat(), y.toFloat(), (x+w).toFloat(), y+1f, fp)
    c.drawRect(x.toFloat(), (y+h-1).toFloat(), (x+w).toFloat(), (y+h).toFloat(), fp)
    c.drawRect(x.toFloat(), y.toFloat(), x+1f, (y+h).toFloat(), fp)
    c.drawRect((x+w-1).toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat(), fp)
}

internal fun PokemonBattleView.drawUIBox(c: Canvas, x: Int, y: Int, w: Int, h: Int) {
    fp.color = C_UI_BG; c.drawRect(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat(), fp)
    fp.color = C_BORDER
    c.drawRect(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+1).toFloat(), fp)
    c.drawRect(x.toFloat(), (y+h-1).toFloat(), (x+w).toFloat(), (y+h).toFloat(), fp)
    c.drawRect(x.toFloat(), y.toFloat(), (x+1).toFloat(), (y+h).toFloat(), fp)
    c.drawRect((x+w-1).toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat(), fp)
    fp.color = C_UI_BG
    c.drawRect(x.toFloat(), y.toFloat(), (x+1).toFloat(), (y+1).toFloat(), fp)
    c.drawRect((x+w-1).toFloat(), y.toFloat(), (x+w).toFloat(), (y+1).toFloat(), fp)
    c.drawRect(x.toFloat(), (y+h-1).toFloat(), (x+1).toFloat(), (y+h).toFloat(), fp)
    c.drawRect((x+w-1).toFloat(), (y+h-1).toFloat(), (x+w).toFloat(), (y+h).toFloat(), fp)
}

internal fun PokemonBattleView.drawBottomUI(c: Canvas) {
    fp.color = C_UI_BG; c.drawRect(0f, 96f, 160f, 144f, fp)
    fp.color = C_BORDER; c.drawRect(0f, 96f, 160f, 97f, fp)
    if (partyMenu) { drawPartyMenu(c); return }
    when (gs.phase) {
        BattlePhase.MAIN_MENU, BattlePhase.INTRO -> drawMainMenu(c)
        BattlePhase.FIGHT_MENU                   -> drawFightMenu(c)
        BattlePhase.ITEM_MENU                    -> drawItemMenu(c)
        else                                     -> drawTextPanel(c)
    }
}

/** Tým: 2 sloupce × 3 řádky, bojující má šipku, omdlelí šedě. */
internal fun PokemonBattleView.drawPartyMenu(c: Canvas) {
    val bx = 2; val by = 97; val bw = 156; val bh = 46; drawUIBox(c, bx, by, bw, bh)
    zones.clear()
    party.forEachIndexed { i, m ->
        val col = i / 3; val row = i % 3
        val x = bx + 3 + col * 76; val y = by + 4 + row * 11
        val alive = m.mon.currentHp > 0
        val color = if (alive) C_TEXT else 0xFF9A9A9A.toInt()
        if (i == partyIdx) PokemonSprites.drawText(c, ">", x, y, C_TEXT, fp)
        PokemonSprites.drawText(c, "${m.mon.name.take(7)} L${m.mon.level}", x + 7, y, color, fp)
        // mini ukazatel HP
        val frac = m.mon.currentHp.toFloat() / m.mon.maxHp.coerceAtLeast(1)
        fp.color = C_HP_BG; c.drawRect((x + 7).toFloat(), (y + 7).toFloat(), (x + 67).toFloat(), (y + 8).toFloat(), fp)
        fp.color = if (frac > 0.5f) C_HP_G else if (frac > 0.2f) C_HP_Y else C_HP_R
        c.drawRect((x + 7).toFloat(), (y + 7).toFloat(), x + 7 + 60 * frac, (y + 8).toFloat(), fp)
        zones.add(Zone(Rect(x - 1, y - 2, x + 74, y + 9)) { if (!busy) switchTo(i) })
    }
    if (!partyForced) {
        PokemonSprites.drawText(c, "BACK", bx+bw-28, by+37, C_TEXT, fp)
        zones.add(Zone(Rect(bx+bw-32, by+33, bx+bw, by+bh)) { if (!busy) { partyMenu = false; showMain() } })
    } else {
        PokemonSprites.drawText(c, "CHOOSE NEXT!", bx + 4, by + 37, C_TEXT, fp)
    }
}

internal fun PokemonBattleView.drawMainMenu(c: Canvas) {
    PokemonSprites.drawText(c, "WHAT WILL", 4, 101, C_TEXT, fp)
    PokemonSprites.drawText(c, "${gs.player.name} DO?", 4, 112, C_TEXT, fp)
    val bx = 88; val by = 97; val bw = 70; val bh = 46; drawUIBox(c, bx, by, bw, bh)
    if (cursorOn) {
        fp.color = C_TEXT
        c.drawRect(bx+3f, by+11f, bx+5f, by+13f, fp)
        c.drawRect(bx+5f, by+10f, bx+7f, by+14f, fp)
    }
    PokemonSprites.drawText(c, "FIGHT", bx+8,  by+9,  C_TEXT, fp)
    PokemonSprites.drawText(c, "MKRM",  bx+44, by+9,  C_TEXT, fp)
    PokemonSprites.drawText(c, "ITEM",  bx+8,  by+26, C_TEXT, fp)
    PokemonSprites.drawText(c, "RUN",   bx+44, by+26, C_TEXT, fp)
    fp.color = C_BORDER
    c.drawRect((bx+40).toFloat(), (by+2).toFloat(), (bx+41).toFloat(), (by+bh-2).toFloat(), fp)
    c.drawRect((bx+2).toFloat(), (by+22).toFloat(), (bx+bw-2).toFloat(), (by+23).toFloat(), fp)
    zones.clear()
    zones.add(Zone(Rect(bx,    by,    bx+40, by+22)) { if (!busy) startFight() })
    zones.add(Zone(Rect(bx+40, by,    bx+bw, by+22)) { if (!busy) showPkmn()  })
    zones.add(Zone(Rect(bx,    by+22, bx+40, by+bh)) { if (!busy) startItem() })
    zones.add(Zone(Rect(bx+40, by+22, bx+bw, by+bh)) { if (!busy) doRun()    })
}

internal fun PokemonBattleView.drawFightMenu(c: Canvas) {
    val bx = 2; val by = 97; val bw = 104; val bh = 46; drawUIBox(c, bx, by, bw, bh)
    val ys = listOf(by+5, by+16, by+27, by+38)
    gs.player.moves.forEachIndexed { i, mv ->
        PokemonSprites.drawText(c, mv.name.take(10), bx+8, ys[i], C_TEXT, fp)
        val pp = "${mv.pp}/${mv.maxPp}"
        PokemonSprites.drawText(c, pp, bx+bw-pp.length*6-4, ys[i], C_TEXT, fp)
    }
    val tx = 108; val ty = 97; val tw = 50; val th = 46; drawUIBox(c, tx, ty, tw, th)
    PokemonSprites.drawText(c, "TYPE/", tx+3, ty+5, C_TEXT, fp)
    PokemonSprites.drawText(c, gs.player.speciesType.name.take(7), tx+3, ty+15, C_TEXT, fp)
    PokemonSprites.drawText(c, "BACK", tx+3, ty+38, C_TEXT, fp)
    zones.clear()
    gs.player.moves.forEachIndexed { i, _ ->
        val zy = by + i * 11
        zones.add(Zone(Rect(bx, zy, bx+bw, zy+11)) { if (!busy) playerMove(i) })
    }
    zones.add(Zone(Rect(tx, ty+32, tx+tw, ty+th)) { if (!busy) showMain() })
}

internal fun PokemonBattleView.drawItemMenu(c: Canvas) {
    when (itemPage) {
        1 -> drawBallMenu(c)
        2 -> drawMedMenu(c)
        else -> drawItemCategories(c)
    }
}

internal fun PokemonBattleView.drawItemCategories(c: Canvas) {
    val bx = 2; val by = 97; val bw = 156; val bh = 46; drawUIBox(c, bx, by, bw, bh)
    zones.clear()
    c.save(); c.translate((bx + 3).toFloat(), (by + 3).toFloat()); c.scale(8f / 12f, 8f / 12f)
    cz.uhk.macroflow.pokemon.balls.BallSprites.draw(c, cz.uhk.macroflow.pokemon.balls.Makroball.MAKRO, 6f, 6f)
    c.restore()
    PokemonSprites.drawText(c, "MAKROBALLY", bx + 14, by + 4, C_TEXT, fp)
    c.save(); c.translate((bx + 3).toFloat(), (by + 14).toFloat()); c.scale(8f / 12f, 8f / 12f)
    cz.uhk.macroflow.pokemon.balls.BallSprites.drawPixels(c, cz.uhk.macroflow.pokemon.status.MedItem.MULTIVITAMIN.pixels, 12, 0f, 0f)
    c.restore()
    PokemonSprites.drawText(c, "LEKARNICKA", bx + 14, by + 15, C_TEXT, fp)
    PokemonSprites.drawText(c, "BACK", bx+bw-28, by+37, C_TEXT, fp)
    zones.add(Zone(Rect(bx, by + 1, bx + bw, by + 12)) { if (!busy) { itemPage = 1; invalidate() } })
    zones.add(Zone(Rect(bx, by + 12, bx + bw, by + 23)) { if (!busy) { itemPage = 2; invalidate() } })
    zones.add(Zone(Rect(bx+bw-32, by+33, bx+bw, by+bh)) { if (!busy) showMain() })
}

internal fun PokemonBattleView.drawBallMenu(c: Canvas) {
    val bx = 2; val by = 97; val bw = 156; val bh = 46; drawUIBox(c, bx, by, bw, bh)
    zones.clear()
    // Počty se čtou z mezipaměti – dřív se sahalo do DB při každém překreslení
    cz.uhk.macroflow.pokemon.balls.Makroball.entries.forEachIndexed { i, b ->
        val y = by + 4 + i * 11
        val n = ballCounts[b] ?: 0
        c.save(); c.translate((bx + 3).toFloat(), (y - 1).toFloat()); c.scale(8f / 12f, 8f / 12f)
        cz.uhk.macroflow.pokemon.balls.BallSprites.draw(c, b, 6f, 6f)
        c.restore()
        PokemonSprites.drawText(c, "${b.label.uppercase()} X$n", bx + 14, y, if (n > 0) C_TEXT else 0xFF9A9A9A.toInt(), fp)
        zones.add(Zone(Rect(bx, y - 2, bx + bw - 34, y + 9)) { if (!busy && n > 0) throwBall(b) })
    }
    PokemonSprites.drawText(c, "BACK", bx+bw-28, by+37, C_TEXT, fp)
    zones.add(Zone(Rect(bx+bw-32, by+33, bx+bw, by+bh)) { if (!busy) { itemPage = 0; invalidate() } })
}

/** Lékárnička: 2 sloupce × 3 řádky, šedě co nemáš. */
internal fun PokemonBattleView.drawMedMenu(c: Canvas) {
    val bx = 2; val by = 97; val bw = 156; val bh = 46; drawUIBox(c, bx, by, bw, bh)
    zones.clear()
    cz.uhk.macroflow.pokemon.status.MedItem.entries.forEachIndexed { i, m ->
        val col = i / 3; val row = i % 3
        val x = bx + 3 + col * 76; val y = by + 4 + row * 11
        val n = medCounts[m] ?: 0
        c.save(); c.translate(x.toFloat(), (y - 1).toFloat()); c.scale(8f / 12f, 8f / 12f)
        cz.uhk.macroflow.pokemon.balls.BallSprites.drawPixels(c, m.pixels, 12, 0f, 0f)
        c.restore()
        PokemonSprites.drawText(c, "${m.short} X$n", x + 10, y, if (n > 0) C_TEXT else 0xFF9A9A9A.toInt(), fp)
        zones.add(Zone(Rect(x - 1, y - 2, x + 74, y + 9)) { if (!busy && n > 0) useMed(m) })
    }
    PokemonSprites.drawText(c, "BACK", bx+bw-28, by+37, C_TEXT, fp)
    zones.add(Zone(Rect(bx+bw-32, by+33, bx+bw, by+bh)) { if (!busy) { itemPage = 0; invalidate() } })
}

internal fun PokemonBattleView.drawTextPanel(c: Canvas) {
    PokemonSprites.drawText(c, gs.textLine1, 6, 104, C_TEXT, fp)
    if (gs.textLine2.isNotEmpty())
        PokemonSprites.drawText(c, gs.textLine2, 6, 118, C_TEXT, fp)
    if (gs.phase == BattlePhase.TEXT_WAIT && cursorOn) {
        fp.color = C_TEXT
        c.drawRect(150f, 136f, 154f, 137f, fp)
        c.drawRect(151f, 137f, 153f, 138f, fp)
        c.drawRect(152f, 138f, 153f, 139f, fp)
    }
}

internal fun PokemonBattleView.drawSprite(c: Canvas, px: IntArray, w: Int, h: Int, ox: Int, oy: Int) {
    if (px.isEmpty()) return
    for (i in 0 until minOf(px.size, w * h)) {
        val color = px[i]; if (color == Color.TRANSPARENT || color == 0) continue
        fp.color = color
        val col = i % w; val row = i / w
        c.drawRect((ox+col).toFloat(), (oy+row).toFloat(), (ox+col+1).toFloat(), (oy+row+1).toFloat(), fp)
    }
}

// ── Shiny: hvězdičkový efekt ─────────────────────────────────────────────
//
// Částice žijí v souřadnicích GB plátna relativně ke středu soupeře, kreslí se ale
// v rozlišení displeje (hladké hvězdy přes pixelové pozadí).

internal fun PokemonBattleView.now() = android.os.SystemClock.uptimeMillis()

/** Střed soupeře v GB souřadnicích (po doběhnutí intra). */
internal fun PokemonBattleView.enemyCenterGb(): PointF = PointF(112f + gs.introOffset * 100f, 54f - enemySpriteHeight() / 2f)

internal fun PokemonBattleView.ensureSparkleLoop() {
    if (!sparkleLoop) { sparkleLoop = true; handler.post(sparkleTick) }
}

/** Výbuch hvězd při odhalení shiny soupeře: dvě vlny + záblesk. Po doběhnutí se uvolní ovládání. */
internal fun PokemonBattleView.startShinyAnim() {
    val t = now()
    glowUntil = t + 700
    repeat(14) { i ->
        val a = Math.toRadians(i * (360.0 / 14) + Random.nextDouble(-8.0, 8.0))
        val v = 40f + Random.nextFloat() * 18f
        sparkles += Sparkle(0f, 0f, (cos(a) * v).toFloat(), (sin(a) * v).toFloat(), t, 900, 3.6f + Random.nextFloat() * 1.6f,
            if (i % 2 == 0) 220f else -220f, gold)
    }
    handler.postDelayed({
        val t2 = now()
        repeat(10) { i ->
            val a = Math.toRadians(i * 36.0 + 18.0)
            val v = 22f + Random.nextFloat() * 10f
            sparkles += Sparkle(0f, 0f, (cos(a) * v).toFloat(), (sin(a) * v).toFloat(), t2, 800, 2.6f, 160f, paleGold)
        }
    }, 260)
    nextTwinkleAt = t + 1500
    ensureSparkleLoop()
    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    handler.postDelayed({ busy = false; invalidate() }, 1100)
}

internal fun PokemonBattleView.spawnTwinkle(t: Long) {
    val half = enemySpriteHeight() / 2f
    repeat(2 + Random.nextInt(2)) { k ->
        sparkles += Sparkle(
            (Random.nextFloat() - 0.5f) * half * 1.6f, (Random.nextFloat() - 0.5f) * half * 1.8f,
            0f, 0f, t + k * 140L, 520, 2.4f + Random.nextFloat() * 1.2f, 90f, if (k == 0) gold else paleGold
        )
    }
}

/** Měkká zlatá záře za soupeřem – krátce při odhalení, pak jen jemně pulzuje. */
internal fun PokemonBattleView.drawShinyGlow(canvas: Canvas, cx: Float, cy: Float, h: Float) {
    val t = now()
    val burst = ((glowUntil - t).coerceAtLeast(0) / 700f)
    val pulse = 0.5f + 0.5f * sin(t / 420.0).toFloat()
    val alpha = (40 + 25 * pulse + 150 * burst).toInt().coerceIn(0, 255)
    val r = h * (0.75f + 0.35f * burst)
    glowPaint.shader = RadialGradient(cx, cy, r,
        intArrayOf(Color.argb(alpha, 255, 224, 130), Color.argb(0, 255, 224, 130)), null, Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, cy, r, glowPaint)
}

internal fun PokemonBattleView.drawSparkles(canvas: Canvas) {
    if (sparkles.isEmpty() || !gsReady) return
    val sc = scale
    val t = now()
    val c = enemyCenterGb()
    for (sp in sparkles.toList()) {
        val age = t - sp.born
        if (age < 0) continue
        val p = (age / sp.life.toFloat()).coerceIn(0f, 1f)
        val sec = age / 1000f
        // Zpomalování letu (ease-out) a „nádech“ velikosti
        val travel = sec * (1f - 0.45f * p)
        val x = gbX(c.x + sp.x0 + sp.vx * travel)
        val y = gbY(c.y + sp.y0 + sp.vy * travel)
        val grow = sin(PI * p).toFloat()
        val r = sp.size * sc * (0.35f + 0.9f * grow)
        val a = (255 * (if (p < 0.15f) p / 0.15f else 1f - (p - 0.15f) / 0.85f)).toInt().coerceIn(0, 255)
        drawStar(canvas, x, y, r, sp.spin * sec, sp.color, a)
    }
}

/** Čtyřcípá hvězda s bílým středem. */
internal fun PokemonBattleView.drawStar(canvas: Canvas, x: Float, y: Float, r: Float, rotDeg: Float, color: Int, alpha: Int) {
    val k = 0.24f * r
    starPath.reset()
    starPath.moveTo(0f, -r); starPath.lineTo(k, -k); starPath.lineTo(r, 0f); starPath.lineTo(k, k)
    starPath.lineTo(0f, r); starPath.lineTo(-k, k); starPath.lineTo(-r, 0f); starPath.lineTo(-k, -k); starPath.close()
    canvas.save()
    canvas.translate(x, y); canvas.rotate(rotDeg)
    starPaint.color = color; starPaint.alpha = alpha
    canvas.drawPath(starPath, starPaint)
    starPaint.color = Color.WHITE; starPaint.alpha = alpha
    canvas.drawCircle(0f, 0f, k * 0.9f, starPaint)
    canvas.restore()
}

/** Ball + záře při otevření + hvězdičky po chycení; kreslí se nad soupeřem. */
internal fun PokemonBattleView.drawBallOverlay(canvas: Canvas) {
    if (!ballShown) return
    val sc = scale
    if (captureBeam > 0f) {
        val cx = gbX(ballCx); val cy = gbY(ballCy); val r = 22f * sc * (0.6f + 0.6f * captureBeam)
        beamPaint.shader = RadialGradient(cx, cy, r,
            intArrayOf(Color.argb((200 * captureBeam).toInt(), 255, 246, 216), Color.argb(0, 255, 246, 216)),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, beamPaint)
    }
    canvas.save()
    canvas.translate(dstR.left, dstR.top)
    canvas.scale(sc, sc)
    cz.uhk.macroflow.pokemon.balls.BallSprites.draw(canvas, ball, ballCx, ballCy, ballRot, ballOpen, ballOpen / 15f)
    if (clickStars in 0f..1f) {
        // Tři hvězdičky vyletí z ballu = chyceno
        fp.color = 0xFFFFD54F.toInt()
        fp.alpha = ((1f - clickStars) * 255).toInt()
        for (i in -1..1) {
            val x = ballCx + i * 8f * (0.4f + clickStars)
            val y = ballCy - 8f - clickStars * 12f + kotlin.math.abs(i) * 3f
            canvas.drawRect(x - 2f, y - 0.5f, x + 2f, y + 0.5f, fp)
            canvas.drawRect(x - 0.5f, y - 2f, x + 0.5f, y + 2f, fp)
        }
        fp.alpha = 255
    }
    canvas.restore()
}

// ── Dovednosti a kořist (docs/adr/0034) ──
