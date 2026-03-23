package cz.uhk.macroflow.pokemon

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

class PokemonBattleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onCaught: (() -> Unit)? = null

    private val GBW = 160
    private val GBH = 144

    // Barvy
    private val C_BG     = 0xFFF8F8F8.toInt()
    private val C_STRIPE = 0xFFE0E0E0.toInt()
    private val C_PLAT_L = 0xFFB8B8B8.toInt()
    private val C_PLAT_D = 0xFF787878.toInt()
    private val C_PLAT_E = 0xFF383838.toInt()
    private val C_UI_BG  = 0xFFF8F8F8.toInt()
    private val C_BORDER = 0xFF181818.toInt()
    private val C_TEXT   = 0xFF181818.toInt()
    private val C_HP_G   = 0xFF00B000.toInt()
    private val C_HP_Y   = 0xFFB8B000.toInt()
    private val C_HP_R   = 0xFFB80000.toInt()
    private val C_HP_BG  = 0xFF282828.toInt()

    private val gbBmp = Bitmap.createBitmap(GBW, GBH, Bitmap.Config.ARGB_8888)
    private val gbCvs = Canvas(gbBmp)
    private val fp = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val sp = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val srcR = Rect(0, 0, GBW, GBH)
    private var dstR = RectF()

    private lateinit var gs: BattleState
    private val handler = Handler(Looper.getMainLooper())
    private var ballX = 0f; private var ballY = 0f
    private var flashOn = false; private var cursorOn = true
    private var busy = false

    // Pokéball viditelnost — tři stavy:
    // 0 = skrytý, 1 = letí/třese se, 2 = zachycen (černý) na zemi
    private var ballVisible = 0

    private val cursorTick = object : Runnable {
        override fun run() { cursorOn = !cursorOn; invalidate(); handler.postDelayed(this, 480) }
    }

    private data class Zone(val r: Rect, val fn: () -> Unit)
    private val zones = mutableListOf<Zone>()

    init {
        gs = BattleState(BattleFactory.createMew(), BattleFactory.createDiglett())
        handler.post(cursorTick)
        startIntro()
    }

    override fun onMeasure(wms: Int, hms: Int) {
        val w = MeasureSpec.getSize(wms)
        val sc = maxOf(1, w / GBW)
        setMeasuredDimension(GBW * sc, GBH * sc)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val sc = minOf(w.toFloat() / GBW, h.toFloat() / GBH)
        val sw = GBW * sc; val sh = GBH * sc
        dstR = RectF((w - sw) / 2f, (h - sh) / 2f, (w + sw) / 2f, (h + sh) / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        renderFrame()
        canvas.drawBitmap(gbBmp, srcR, dstR, sp)
    }

    // ═══════════════════════════════════════════════════════════════
    // RENDER
    // ═══════════════════════════════════════════════════════════════
    private fun renderFrame() {
        val c = gbCvs

        // Scéna — bílá s pruhy
        fp.color = C_BG; c.drawRect(0f, 0f, 160f, 96f, fp)
        fp.color = C_STRIPE
        for (y in 0 until 56 step 6) c.drawRect(0f, (y + 4).toFloat(), 160f, (y + 6).toFloat(), fp)

        drawPlatform(c, 80, 44, 64, 10)
        drawPlatform(c, 16, 72, 64, 10)

        drawEnemyHUD(c)
        drawPlayerHUD(c)

        // Diglett — skrytý v průběhu hodu (dokud se nezjistí výsledek)
        if (gs.enemyVisible)
            drawSprite(c, PokemonSprites.DIGLETT2, PokemonSprites.DIGLETT_W, PokemonSprites.DIGLETT_H, 94, 28)

        // Mew
        drawSprite(c, PokemonSprites.MEW, PokemonSprites.MEW_W, PokemonSprites.MEW_H, 2, 26)

        // ── Pokéball — zobraz POUZE když letí nebo po chycení ────
        when (ballVisible) {
            1 -> { // letí nebo se třese
                drawSprite(c, PokemonSprites.POKEBALL,
                    PokemonSprites.POKEBALL_W, PokemonSprites.POKEBALL_H,
                    ballX.toInt(), ballY.toInt())
            }
            2 -> { // chycen — černý pokéball zůstane na místě
                drawSprite(c, PokemonSprites.POKEBALL_CLOSED,
                    PokemonSprites.POKEBALL_W, PokemonSprites.POKEBALL_H,
                    ballX.toInt(), ballY.toInt())
            }
            // 0 = skrytý — nic nekresli
        }

        drawBottomUI(c)

        if (flashOn) {
            fp.color = 0xBBFFFFFF.toInt()
            c.drawRect(0f, 0f, 160f, 144f, fp)
        }
    }

    private fun drawPlatform(c: Canvas, x: Int, y: Int, w: Int, h: Int) {
        fp.color = C_PLAT_L; c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + 3).toFloat(), fp)
        fp.color = C_PLAT_D; c.drawRect(x.toFloat(), (y + 3).toFloat(), (x + w).toFloat(), (y + h - 2).toFloat(), fp)
        fp.color = C_PLAT_E; c.drawRect(x.toFloat(), (y + h - 2).toFloat(), (x + w).toFloat(), (y + h).toFloat(), fp)
    }

    private fun drawEnemyHUD(c: Canvas) {
        val x = 1; val y = 1; val w = 76; val h = 28; drawUIBox(c, x, y, w, h)
        PokemonSprites.drawText(c, gs.enemy.name, x + 3, y + 3, C_TEXT, fp)
        PokemonSprites.drawText(c, ":L${gs.enemy.level}", x + 48, y + 3, C_TEXT, fp)
        PokemonSprites.drawText(c, "HP", x + 3, y + 13, C_TEXT, fp)
        drawHPBar(c, x + 16, y + 13, 54, 5, gs.enemy.currentHp, gs.enemy.maxHp)
    }

    private fun drawPlayerHUD(c: Canvas) {
        val x = 84; val y = 58; val w = 75; val h = 36; drawUIBox(c, x, y, w, h)
        PokemonSprites.drawText(c, gs.player.name, x + 3, y + 3, C_TEXT, fp)
        PokemonSprites.drawText(c, ":L${gs.player.level}", x + 42, y + 3, C_TEXT, fp)
        PokemonSprites.drawText(c, "HP", x + 3, y + 13, C_TEXT, fp)
        drawHPBar(c, x + 16, y + 13, 54, 5, gs.player.currentHp, gs.player.maxHp)
        val hp = "${gs.player.currentHp}/${gs.player.maxHp}"
        PokemonSprites.drawText(c, hp, x + w - 3 - hp.length * 6, y + 22, C_TEXT, fp)
    }

    private fun drawHPBar(c: Canvas, x: Int, y: Int, w: Int, h: Int, cur: Int, max: Int) {
        fp.color = C_HP_BG; c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), fp)
        val frac = (cur.toFloat() / max).coerceIn(0f, 1f); val fw = (w * frac).toInt()
        if (fw > 0) {
            fp.color = if (frac > 0.5f) C_HP_G else if (frac > 0.25f) C_HP_Y else C_HP_R
            c.drawRect(x.toFloat(), y.toFloat(), (x + fw).toFloat(), (y + h).toFloat(), fp)
        }
        fp.color = C_BORDER
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), y + 1f, fp)
        c.drawRect(x.toFloat(), (y + h - 1).toFloat(), (x + w).toFloat(), (y + h).toFloat(), fp)
        c.drawRect(x.toFloat(), y.toFloat(), x + 1f, (y + h).toFloat(), fp)
        c.drawRect((x + w - 1).toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), fp)
    }

    private fun drawUIBox(c: Canvas, x: Int, y: Int, w: Int, h: Int) {
        fp.color = C_UI_BG; c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), fp)
        fp.color = C_BORDER
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + 1).toFloat(), fp)
        c.drawRect(x.toFloat(), (y + h - 1).toFloat(), (x + w).toFloat(), (y + h).toFloat(), fp)
        c.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + h).toFloat(), fp)
        c.drawRect((x + w - 1).toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), fp)
        fp.color = C_UI_BG  // rohové tečky
        c.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat(), fp)
        c.drawRect((x + w - 1).toFloat(), y.toFloat(), (x + w).toFloat(), (y + 1).toFloat(), fp)
        c.drawRect(x.toFloat(), (y + h - 1).toFloat(), (x + 1).toFloat(), (y + h).toFloat(), fp)
        c.drawRect((x + w - 1).toFloat(), (y + h - 1).toFloat(), (x + w).toFloat(), (y + h).toFloat(), fp)
    }

    private fun drawBottomUI(c: Canvas) {
        fp.color = C_UI_BG; c.drawRect(0f, 96f, 160f, 144f, fp)
        fp.color = C_BORDER; c.drawRect(0f, 96f, 160f, 97f, fp)
        when (gs.phase) {
            BattlePhase.MAIN_MENU, BattlePhase.INTRO -> drawMainMenu(c)
            BattlePhase.FIGHT_MENU                   -> drawFightMenu(c)
            BattlePhase.ITEM_MENU                    -> drawItemMenu(c)
            else                                     -> drawTextPanel(c)
        }
    }

    private fun drawMainMenu(c: Canvas) {
        PokemonSprites.drawText(c, "WHAT WILL", 4, 101, C_TEXT, fp)
        PokemonSprites.drawText(c, "${gs.player.name} DO?", 4, 112, C_TEXT, fp)
        val bx = 88; val by = 97; val bw = 70; val bh = 46; drawUIBox(c, bx, by, bw, bh)
        if (cursorOn) {
            fp.color = C_TEXT
            c.drawRect(bx + 3f, by + 11f, bx + 5f, by + 13f, fp)
            c.drawRect(bx + 5f, by + 10f, bx + 7f, by + 14f, fp)
        }
        PokemonSprites.drawText(c, "FIGHT", bx + 8, by + 9, C_TEXT, fp)
        PokemonSprites.drawText(c, "PKMN",  bx + 44, by + 9, C_TEXT, fp)
        PokemonSprites.drawText(c, "ITEM",  bx + 8, by + 26, C_TEXT, fp)
        PokemonSprites.drawText(c, "RUN",   bx + 44, by + 26, C_TEXT, fp)
        fp.color = C_BORDER
        c.drawRect((bx + 40).toFloat(), (by + 2).toFloat(), (bx + 41).toFloat(), (by + bh - 2).toFloat(), fp)
        c.drawRect((bx + 2).toFloat(), (by + 22).toFloat(), (bx + bw - 2).toFloat(), (by + 23).toFloat(), fp)
        zones.clear()
        zones.add(Zone(Rect(bx, by, bx + 40, by + 22))     { if (!busy) startFight() })
        zones.add(Zone(Rect(bx + 40, by, bx + bw, by + 22)){ if (!busy) showPkmn() })
        zones.add(Zone(Rect(bx, by + 22, bx + 40, by + bh)){ if (!busy) startItem() })
        zones.add(Zone(Rect(bx + 40, by + 22, bx + bw, by + bh)) { if (!busy) doRun() })
    }

    private fun drawFightMenu(c: Canvas) {
        val bx = 2; val by = 97; val bw = 104; val bh = 46; drawUIBox(c, bx, by, bw, bh)
        val ys = listOf(by + 5, by + 16, by + 27, by + 38)
        gs.player.moves.forEachIndexed { i, mv ->
            PokemonSprites.drawText(c, mv.name.take(10), bx + 8, ys[i], C_TEXT, fp)
            val pp = "${mv.pp}/${mv.maxPp}"
            PokemonSprites.drawText(c, pp, bx + bw - pp.length * 6 - 4, ys[i], C_TEXT, fp)
        }
        val tx = 108; val ty = 97; val tw = 50; val th = 46; drawUIBox(c, tx, ty, tw, th)
        PokemonSprites.drawText(c, "TYPE/", tx + 3, ty + 5, C_TEXT, fp)
        PokemonSprites.drawText(c, gs.player.moves[0].type.take(7), tx + 3, ty + 15, C_TEXT, fp)
        PokemonSprites.drawText(c, "BACK", tx + 3, ty + 38, C_TEXT, fp)
        zones.clear()
        gs.player.moves.forEachIndexed { i, _ ->
            val zy = by + i * 11
            zones.add(Zone(Rect(bx, zy, bx + bw, zy + 11)) { if (!busy) playerMove(i) })
        }
        zones.add(Zone(Rect(tx, ty + 32, tx + tw, ty + th)) { if (!busy) showMain() })
    }

    private fun drawItemMenu(c: Canvas) {
        val bx = 2; val by = 97; val bw = 156; val bh = 46; drawUIBox(c, bx, by, bw, bh)
        PokemonSprites.drawText(c, "POKE BALL  X${gs.ballCount}", bx + 4, by + 5, C_TEXT, fp)
        if (gs.ballCount > 0)
            PokemonSprites.drawText(c, "> THROW", bx + 4, by + 18, C_TEXT, fp)
        else
            PokemonSprites.drawText(c, "NO BALLS!", bx + 4, by + 18, C_TEXT, fp)
        PokemonSprites.drawText(c, "BACK", bx + bw - 28, by + 38, C_TEXT, fp)
        zones.clear()
        if (gs.ballCount > 0) zones.add(Zone(Rect(bx, by, bx + bw, by + 30)) { if (!busy) throwBall() })
        zones.add(Zone(Rect(bx + bw - 32, by + 32, bx + bw, by + bh)) { if (!busy) showMain() })
    }

    private fun drawTextPanel(c: Canvas) {
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

    private fun drawSprite(c: Canvas, px: IntArray, w: Int, h: Int, ox: Int, oy: Int) {
        val total = minOf(px.size, w * h)
        for (i in 0 until total) {
            val color = px[i]; if (color == Color.TRANSPARENT || color == 0) continue
            fp.color = color
            c.drawRect((ox + i % w).toFloat(), (oy + i / w).toFloat(),
                (ox + i % w + 1).toFloat(), (oy + i / w + 1).toFloat(), fp)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TOUCH
    // ═══════════════════════════════════════════════════════════════
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true
        if (busy) return true
        if (gs.phase == BattlePhase.TEXT_WAIT) { advText(); return true }
        val sx = GBW.toFloat() / dstR.width()
        val sy = GBH.toFloat() / dstR.height()
        val gx = ((e.x - dstR.left) * sx).toInt()
        val gy = ((e.y - dstR.top)  * sy).toInt()
        zones.forEach { z -> if (z.r.contains(gx, gy)) { z.fn(); return true } }
        return true
    }

    // ═══════════════════════════════════════════════════════════════
    // HERNÍ LOGIKA
    // ═══════════════════════════════════════════════════════════════
    private fun startIntro() {
        setText("WILD ${gs.enemy.name}", "APPEARED!")
    }

    private fun showMain() {
        busy = false; gs.phase = BattlePhase.MAIN_MENU; zones.clear(); invalidate()
    }

    private fun startFight() {
        if (gs.player.moves.all { it.pp <= 0 }) { setText("NO PP LEFT!", ""); return }
        gs.phase = BattlePhase.FIGHT_MENU; invalidate()
    }

    private fun showPkmn() { setText("ONLY ${gs.player.name}", "IN YOUR PARTY!") }

    private fun startItem() { gs.phase = BattlePhase.ITEM_MENU; zones.clear(); invalidate() }

    private fun doRun() {
        if (BattleEngine.tryEscape(gs.player.speed, gs.enemy.speed)) {
            gs.phase = BattlePhase.ESCAPED
            setText("GOT AWAY", "SAFELY!")
        } else {
            setText("CANT ESCAPE!", "")
            scheduleAfterText { enemyTurn() }
        }
        invalidate()
    }

    // ── Hráč útočí ───────────────────────────────────────────────────
    private fun playerMove(idx: Int) {
        val mv = gs.player.moves[idx]; if (mv.pp <= 0) { setText("NO PP LEFT!", ""); return }
        mv.pp--; busy = true
        gs.phase = BattlePhase.ANIMATING
        setText("${gs.player.name}", "USED ${mv.name}!"); invalidate()

        handler.postDelayed({
            if (Random.nextInt(100) >= mv.accuracy) {
                setText("${gs.player.name}", "MISSED!")
                scheduleAfterText { enemyTurn() }
                return@postDelayed
            }
            if (mv.power > 0) {
                val dmg = BattleEngine.calcDamage(gs.player.level, mv.power, gs.player.attack, gs.enemy.defense)
                doFlash {
                    gs.enemy.currentHp = maxOf(0, gs.enemy.currentHp - dmg); invalidate()
                    handler.postDelayed({
                        if (gs.enemy.currentHp <= 0) {
                            gs.enemyVisible = false
                            gs.phase = BattlePhase.ENEMY_FAINTED
                            setText("${gs.enemy.name}", "FAINTED!")
                            busy = false; invalidate()
                        } else {
                            setText("IT DEALT", "$dmg DAMAGE!")
                            scheduleAfterText { enemyTurn() }
                        }
                    }, 400)
                }
            } else {
                when (mv.statEffect) {
                    StatEffect.LOWER_ENEMY_ATK -> gs.enemyAtkMod *= 0.85f
                    StatEffect.LOWER_ENEMY_DEF -> gs.enemyDefMod *= 0.85f
                    else -> {}
                }
                doFlash {
                    setText("ENEMY STAT", "FELL!")
                    scheduleAfterText { enemyTurn() }
                }
            }
        }, 1200)
    }

    // Naplánuje akci PO kliknutí na text
    private fun scheduleAfterText(action: () -> Unit) {
        busy = false
        gs.phase = BattlePhase.TEXT_WAIT
        pendingAction = action
        invalidate()
    }

    private var pendingAction: (() -> Unit)? = null

    private fun advText() {
        val action = pendingAction
        pendingAction = null
        when (gs.phase) {
            BattlePhase.ESCAPED,
            BattlePhase.ENEMY_FAINTED,
            BattlePhase.PLAYER_FAINTED,
            BattlePhase.CAUGHT -> { /* konec */ }
            BattlePhase.TEXT_WAIT -> {
                if (action != null) {
                    action()
                } else if (!busy && gs.player.currentHp > 0 && gs.enemy.currentHp > 0) {
                    enemyTurn()
                } else {
                    showMain()
                }
            }
            else -> showMain()
        }
    }

    // ── Tah nepřítele ─────────────────────────────────────────────────
    private fun enemyTurn() {
        if (gs.enemy.currentHp <= 0 || gs.player.currentHp <= 0) { showMain(); return }
        busy = true
        val mv = BattleEngine.enemyChooseMove(gs.enemy); mv.pp = maxOf(0, mv.pp - 1)
        setText("${gs.enemy.name}", "USED ${mv.name}!"); invalidate()
        handler.postDelayed({
            if (mv.power > 0) {
                val atkE = (gs.enemy.attack  * gs.enemyAtkMod).toInt()
                val defE = (gs.player.defense * gs.enemyDefMod).toInt()
                val dmg  = BattleEngine.calcDamage(gs.enemy.level, mv.power, atkE, defE)
                doFlash {
                    gs.player.currentHp = maxOf(0, gs.player.currentHp - dmg); invalidate()
                    handler.postDelayed({
                        if (gs.player.currentHp <= 0) {
                            gs.phase = BattlePhase.PLAYER_FAINTED
                            setText("${gs.player.name}", "FAINTED!")
                            busy = false; invalidate()
                        } else {
                            setText("IT DEALT", "$dmg DAMAGE!")
                            busy = false
                            gs.phase = BattlePhase.TEXT_WAIT
                            pendingAction = { showMain() }
                            invalidate()
                        }
                    }, 400)
                }
            } else {
                setText("${gs.player.name}", "STAT FELL!")
                busy = false
                gs.phase = BattlePhase.TEXT_WAIT
                pendingAction = { showMain() }
                invalidate()
            }
        }, 1200)
    }

    // ── Pokéball hod ──────────────────────────────────────────────────
    private fun throwBall() {
        if (gs.ballCount <= 0 || gs.enemy.currentHp <= 0) return
        gs.ballCount--; busy = true
        gs.phase = BattlePhase.BALL_THROW
        setText("THREW A", "POKE BALL!")

        val sx = 28f; val sy = 58f; val tx = 100f; val ty = 28f
        ballX = sx; ballY = sy; var t = 0f
        ballVisible = 1  // ← pokéball viditelný při letu

        fun fly() {
            if (t >= 1f) {
                // Diglett zmizí do pokébalu
                gs.enemyVisible = false
                ballX = 100f; ballY = 36f; invalidate()
                handler.postDelayed({
                    // Pokéball dopadl na zem
                    ballY = 46f; invalidate()
                    handler.postDelayed({ startWobble() }, 300)
                }, 350)
                return
            }
            ballX = sx + (tx - sx) * t
            ballY = sy + (ty - sy) * t + (-sin(t * PI.toFloat()) * 26f)
            t += 0.035f; invalidate()
            handler.postDelayed({ fly() }, 20)
        }
        fly()
    }

    private fun startWobble() {
        val (success, wobbles) = BattleEngine.calcCaptureResult(gs.enemy)
        gs.captureSuccess = success; gs.wobbleCount = wobbles; gs.wobbleDone = 0
        gs.phase = BattlePhase.BALL_WOBBLE
        setText("...", ""); nextWobble()
    }

    private fun nextWobble() {
        if (gs.wobbleDone >= gs.wobbleCount) {
            handler.postDelayed({
                if (gs.captureSuccess) caught() else breakFree()
            }, 600)
            return
        }
        var wt = 0
        fun step() {
            ballX = 100f + sin(wt * 0.65f) * 4.5f; invalidate(); wt++
            if (wt < 30) handler.postDelayed({ step() }, 45)
            else {
                ballX = 100f; gs.wobbleDone++; invalidate()
                handler.postDelayed({ nextWobble() }, 700)
            }
        }
        step()
    }

    private fun caught() {
        gs.caught = true
        gs.phase = BattlePhase.CAUGHT
        ballVisible = 2  // ← pokéball zůstane viditelný jako černý (chycen)

        // Uložit do SharedPrefs
        context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
            .edit().putBoolean("diglettAcquired", true).apply()

        // Blikání
        var fl = 0
        fun blink() {
            flashOn = !flashOn; invalidate(); fl++
            if (fl < 10) handler.postDelayed({ blink() }, 180)
            else {
                flashOn = false
                // DŮLEŽITÉ: nastavíme phase CAUGHT před setText
                // aby advText() NIKDY nespustil enemyTurn()
                gs.phase = BattlePhase.CAUGHT
                pendingAction = null  // ← vyčistit pendingAction!
                gs.textLine1 = "GOTCHA!"
                gs.textLine2 = "${gs.enemy.name} CAUGHT!"
                busy = false; invalidate()
                onCaught?.invoke()
            }
        }
        blink()
    }

    private fun breakFree() {
        // Pokéball zmizel — Diglett se vrátí
        ballVisible = 0  // ← pokéball skrytý po úniku
        gs.enemyVisible = true
        setText("${gs.enemy.name}", "BROKE FREE!"); invalidate()
        busy = false
        if (gs.ballCount <= 0) {
            gs.phase = BattlePhase.TEXT_WAIT
            pendingAction = null
        } else {
            // Kliknutí → tah nepřítele
            gs.phase = BattlePhase.TEXT_WAIT
            pendingAction = { enemyTurn() }
        }
    }

    private fun doFlash(done: () -> Unit) {
        flashOn = true; invalidate()
        handler.postDelayed({ flashOn = false; done(); invalidate() }, 100)
    }

    private fun setText(l1: String, l2: String) {
        gs.textLine1 = l1; gs.textLine2 = l2
        // Nepřepisuj terminální fáze!
        if (gs.phase != BattlePhase.CAUGHT &&
            gs.phase != BattlePhase.ESCAPED &&
            gs.phase != BattlePhase.ENEMY_FAINTED &&
            gs.phase != BattlePhase.PLAYER_FAINTED) {
            gs.phase = BattlePhase.TEXT_WAIT
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }
}