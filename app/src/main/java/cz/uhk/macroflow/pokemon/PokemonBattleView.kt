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

class PokemonBattleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    /** Divoký Makromon je shiny – o šanci rozhoduje fragment před vytvořením view (kvůli intru). */
    private val enemyShiny: Boolean = false
) : View(context, attrs) {

    var onCaught: (() -> Unit)? = null

    private val GBW = 160
    private val GBH = 144

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
    private val fp    = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val sp    = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val spSmooth = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
    private val srcR  = Rect(0, 0, GBW, GBH)
    private var dstR  = RectF()

    private lateinit var gs: BattleState
    private val handler = Handler(Looper.getMainLooper())
    // ── Makroball: poloha a stav animace (souřadnice herního plátna 160 × 144) ──
    private var ball: cz.uhk.macroflow.pokemon.balls.Makroball = cz.uhk.macroflow.pokemon.balls.Makroball.MAKRO
    private var ballShown = false
    private var ballCx = 0f; private var ballCy = 0f
    private var ballRot = 0f; private var ballOpen = 0f
    /** 0 = Makromon normálně, 1 = celý vtažený do ballu (světlo). */
    private var enemyAbsorb = 0f
    private var captureBeam = 0f
    private var clickStars = -1f
    private val ballCounts = HashMap<cz.uhk.macroflow.pokemon.balls.Makroball, Int>()
    private val absorbPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var flashOn = false; private var cursorOn = true
    private var busy = false
    private var pendingAction: (() -> Unit)? = null

    private var enemyBitmap: Bitmap? = null
    private var playerBitmap: Bitmap? = null
    private var introStarted = false

    private val db = AppDatabase.getDatabase(context)

    private val cursorTick = object : Runnable {
        override fun run() { cursorOn = !cursorOn; invalidate(); handler.postDelayed(this, 480) }
    }

    private data class Zone(val r: Rect, val fn: () -> Unit)
    private val zones = mutableListOf<Zone>()

    init {
        PokemonSprites.init(context)

        val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val activeCapturedId = prefs.getInt("currentOnBarCapturedId", -1)
        val backupMakromonId = prefs.getString("currentOnBarId", "012") ?: "012"

        // --- 🌍 NAČTENÍ AKTUÁLNÍHO BIOMU ---
        val biomeStr = prefs.getString("LAST_BIOME", BiomeType.TOWN.name)
        val currentBiome = BiomeType.valueOf(biomeStr!!)

        Thread {
            val db = AppDatabase.getDatabase(context)

            val caughtEntity = if (activeCapturedId != -1) {
                db.capturedMakromonDao().getMakromonById(activeCapturedId)
            } else null

            val mId = caughtEntity?.makromonId ?: backupMakromonId
            val playerLevel = caughtEntity?.level ?: 1
            val playerIsShiny = caughtEntity?.isShiny ?: false

            val playerWithStats = createPlayerMakromon(mId, playerLevel)

            // --- 🎲 OPRAVENÝ ROLL S BIOMEM ---
            val baseEnemy = SpawnManager.rollWildEncounter(context, currentBiome)

            val randomEnemyLevel = (playerLevel + Random.nextInt(-2, 3)).coerceAtLeast(1)
            val enemyWithStats = BattleEngine.initializeStatsForLevel(baseEnemy, randomEnemyLevel)
            val enemyIsShiny = enemyShiny

            val counts = cz.uhk.macroflow.pokemon.balls.Makroball.entries.associateWith { db.userItemDao().getItemCount(it.id) ?: 0 }
            val currentPokeballs = counts.values.sum()

            handler.post {
                gs = BattleState(
                    player = playerWithStats,
                    enemy  = enemyWithStats,
                    ballCount = currentPokeballs
                )
                ballCounts.putAll(counts)
                gs.isEnemyShiny  = enemyIsShiny
                gs.isPlayerShiny = playerIsShiny

                handler.post(cursorTick)

                loadMakromonSprite(enemyWithStats, isPlayer = false)
                loadMakromonSprite(playerWithStats, isPlayer = true)

                invalidate()
            }
        }.start()
    }

    /**
     * Načte sprite Makromona z lokálního drawable zdroje.
     * Konvence: makromon_spirra, makromon_ignar, atd.
     *
     * Shiny logika zakomentována – odkomentuj až budou hotové sprity:
     * val drawableName = if (isShiny) "makromon_${name}_shiny" else "makromon_$name"
     */
    private fun loadMakromonSprite(makromon: Makromon, isPlayer: Boolean) {
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

    private fun maybeStartIntro() {
        handler.post {
            if (!introStarted) {
                introStarted = true
                startIntro()
            }
            invalidate()
        }
    }

    override fun onMeasure(wms: Int, hms: Int) {
        val w  = MeasureSpec.getSize(wms)
        val sc = maxOf(1, w / GBW)
        setMeasuredDimension(GBW * sc, GBH * sc)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val sc = minOf(w.toFloat() / GBW, h.toFloat() / GBH)
        val sw = GBW * sc; val sh = GBH * sc
        dstR = RectF((w - sw) / 2f, (h - sh) / 2f, (w + sw) / 2f, (h + sh) / 2f)
    }

    private val scale get() = if (dstR.width() > 0) dstR.width() / GBW.toFloat() else 1f
    private fun gbX(x: Float) = dstR.left + x * scale
    private fun gbY(y: Float) = dstR.top  + y * scale

    override fun onDraw(canvas: Canvas) {
        if (!::gs.isInitialized) { canvas.drawColor(C_BG); return }
        renderFrame()
        canvas.drawBitmap(gbBmp, srcR, dstR, sp)
        drawSpritesOverlay(canvas)
    }

    private fun drawSpritesOverlay(canvas: Canvas) {
        val sc = scale
        if (sc <= 0f || !::gs.isInitialized) return
        val animOffset = (gs.introOffset * 100f) * sc

        if (gs.enemyVisible) {
            enemyBitmap?.let { bmp ->
                val targetH = enemySpriteHeight() * sc
                val targetW = targetH * bmp.width.toFloat() / bmp.height.toFloat()
                val sx = gbX(112f) - targetW / 2f + animOffset
                val sy = gbY(54f) - targetH
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

        playerBitmap?.let { bmp ->
            val targetH = 36f * sc
            val targetW = targetH * bmp.width.toFloat() / bmp.height.toFloat()
            val cx = gbX(48f) - animOffset
            val sy = gbY(82f) - targetH
            canvas.save()
            canvas.scale(-1f, 1f, cx, 0f)
            canvas.drawBitmap(bmp, null, RectF(cx - targetW / 2f, sy, cx + targetW / 2f, sy + targetH), spSmooth)
            canvas.restore()
        }
    }

    private fun enemySpriteHeight(): Float = when (gs.enemy.name) {
        "SERPFIN"   -> 42f
        "SOULORD"   -> 38f
        "PHANTIAX"  -> 36f
        "IGNAROTH"  -> 40f
        "AQULINOX"  -> 38f
        "FLORINDRA" -> 36f
        "DRAKIRRA"  -> 34f
        "GUDWIN"    -> 42f
        "MYDRUS"    -> 32f
        "AXLU"      -> 30f
        "UMBEX"     -> 28f
        "LUMEX"     -> 28f
        else        -> 28f
    }

    private fun renderFrame() {
        val c = gbCvs
        fp.color = C_BG; c.drawRect(0f, 0f, 160f, 96f, fp)
        fp.color = C_STRIPE
        for (y in 0 until 56 step 6)
            c.drawRect(0f, (y + 4).toFloat(), 160f, (y + 6).toFloat(), fp)
        drawPlatform(c, 80, 44, 64, 10)
        drawPlatform(c, 16, 72, 64, 10)
        drawEnemyHUD(c)
        drawPlayerHUD(c)
        drawBottomUI(c)
        if (flashOn) { fp.color = 0xBBFFFFFF.toInt(); c.drawRect(0f, 0f, 160f, 144f, fp) }
    }

    private fun drawPlatform(c: Canvas, x: Int, y: Int, w: Int, h: Int) {
        fp.color = C_PLAT_L; c.drawRect(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+3).toFloat(), fp)
        fp.color = C_PLAT_D; c.drawRect(x.toFloat(), (y+3).toFloat(), (x+w).toFloat(), (y+h-2).toFloat(), fp)
        fp.color = C_PLAT_E; c.drawRect(x.toFloat(), (y+h-2).toFloat(), (x+w).toFloat(), (y+h).toFloat(), fp)
    }

    private fun drawEnemyHUD(c: Canvas) {
        val x = 1; val y = 1; val w = 76; val h = 28; drawUIBox(c, x, y, w, h)
        PokemonSprites.drawText(c, gs.enemy.name.take(7), x+3, y+3, C_TEXT, fp)
        PokemonSprites.drawText(c, ":L${gs.enemy.level}", x+48, y+3, C_TEXT, fp)
        PokemonSprites.drawText(c, "HP", x+3, y+13, C_TEXT, fp)
        drawHPBar(c, x+16, y+13, 54, 5, gs.enemy.currentHp, gs.enemy.maxHp)
        if (gs.isEnemyShiny) PokemonSprites.drawText(c, "*SHINY", x+3, y+20, 0xFFC08A00.toInt(), fp)
    }

    private fun drawPlayerHUD(c: Canvas) {
        val x = 84; val y = 58; val w = 75; val h = 36; drawUIBox(c, x, y, w, h)
        PokemonSprites.drawText(c, gs.player.name.take(7), x+3, y+3, C_TEXT, fp)
        PokemonSprites.drawText(c, ":L${gs.player.level}", x+42, y+3, C_TEXT, fp)
        PokemonSprites.drawText(c, "HP", x+3, y+13, C_TEXT, fp)
        drawHPBar(c, x+16, y+13, 54, 5, gs.player.currentHp, gs.player.maxHp)
        val hp = "${gs.player.currentHp}/${gs.player.maxHp}"
        PokemonSprites.drawText(c, hp, x+w-3-hp.length*6, y+22, C_TEXT, fp)
    }

    private fun drawHPBar(c: Canvas, x: Int, y: Int, w: Int, h: Int, cur: Int, max: Int) {
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

    private fun drawUIBox(c: Canvas, x: Int, y: Int, w: Int, h: Int) {
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

    private fun drawFightMenu(c: Canvas) {
        val bx = 2; val by = 97; val bw = 104; val bh = 46; drawUIBox(c, bx, by, bw, bh)
        val ys = listOf(by+5, by+16, by+27, by+38)
        gs.player.moves.forEachIndexed { i, mv ->
            PokemonSprites.drawText(c, mv.name.take(10), bx+8, ys[i], C_TEXT, fp)
            val pp = "${mv.pp}/${mv.maxPp}"
            PokemonSprites.drawText(c, pp, bx+bw-pp.length*6-4, ys[i], C_TEXT, fp)
        }
        val tx = 108; val ty = 97; val tw = 50; val th = 46; drawUIBox(c, tx, ty, tw, th)
        PokemonSprites.drawText(c, "TYPE/", tx+3, ty+5, C_TEXT, fp)
        PokemonSprites.drawText(c, gs.player.moves[0].type.name.take(7), tx+3, ty+15, C_TEXT, fp)
        PokemonSprites.drawText(c, "BACK", tx+3, ty+38, C_TEXT, fp)
        zones.clear()
        gs.player.moves.forEachIndexed { i, _ ->
            val zy = by + i * 11
            zones.add(Zone(Rect(bx, zy, bx+bw, zy+11)) { if (!busy) playerMove(i) })
        }
        zones.add(Zone(Rect(tx, ty+32, tx+tw, ty+th)) { if (!busy) showMain() })
    }

    private fun drawItemMenu(c: Canvas) {
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
        zones.add(Zone(Rect(bx+bw-32, by+33, bx+bw, by+bh)) { if (!busy) showMain() })
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

    private data class Sparkle(
        val x0: Float, val y0: Float,       // start (GB px od středu soupeře)
        val vx: Float, val vy: Float,       // rychlost (GB px / s)
        val born: Long, val life: Long,
        val size: Float,                    // poloměr cípu (GB px)
        val spin: Float,                    // otočení (° / s)
        val color: Int
    )

    private val sparkles = mutableListOf<Sparkle>()
    private var sparkleLoop = false
    private var nextTwinkleAt = 0L
    private var glowUntil = 0L
    private val starPath = Path()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gold = 0xFFFFD54F.toInt()
    private val paleGold = 0xFFFFF3C4.toInt()

    private fun now() = android.os.SystemClock.uptimeMillis()

    /** Střed soupeře v GB souřadnicích (po doběhnutí intra). */
    private fun enemyCenterGb(): PointF = PointF(112f + gs.introOffset * 100f, 54f - enemySpriteHeight() / 2f)

    private val sparkleTick = object : Runnable {
        override fun run() {
            val t = now()
            sparkles.removeAll { t - it.born > it.life }
            // Jemné třpytky dokola, dokud je shiny soupeř na scéně
            if (gs.isEnemyShiny && gs.enemyVisible && t >= nextTwinkleAt && gs.phase != BattlePhase.CAUGHT) {
                spawnTwinkle(t)
                nextTwinkleAt = t + 1800 + Random.nextLong(900)
            }
            invalidate()
            if (sparkles.isNotEmpty() || gs.isEnemyShiny) handler.postDelayed(this, 16) else sparkleLoop = false
        }
    }

    private fun ensureSparkleLoop() {
        if (!sparkleLoop) { sparkleLoop = true; handler.post(sparkleTick) }
    }

    /** Výbuch hvězd při odhalení shiny soupeře: dvě vlny + záblesk. Po doběhnutí se uvolní ovládání. */
    private fun startShinyAnim() {
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

    private fun spawnTwinkle(t: Long) {
        val half = enemySpriteHeight() / 2f
        repeat(2 + Random.nextInt(2)) { k ->
            sparkles += Sparkle(
                (Random.nextFloat() - 0.5f) * half * 1.6f, (Random.nextFloat() - 0.5f) * half * 1.8f,
                0f, 0f, t + k * 140L, 520, 2.4f + Random.nextFloat() * 1.2f, 90f, if (k == 0) gold else paleGold
            )
        }
    }

    /** Měkká zlatá záře za soupeřem – krátce při odhalení, pak jen jemně pulzuje. */
    private fun drawShinyGlow(canvas: Canvas, cx: Float, cy: Float, h: Float) {
        val t = now()
        val burst = ((glowUntil - t).coerceAtLeast(0) / 700f)
        val pulse = 0.5f + 0.5f * sin(t / 420.0).toFloat()
        val alpha = (40 + 25 * pulse + 150 * burst).toInt().coerceIn(0, 255)
        val r = h * (0.75f + 0.35f * burst)
        glowPaint.shader = RadialGradient(cx, cy, r,
            intArrayOf(Color.argb(alpha, 255, 224, 130), Color.argb(0, 255, 224, 130)), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, glowPaint)
    }

    private fun drawSparkles(canvas: Canvas) {
        if (sparkles.isEmpty() || !::gs.isInitialized) return
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
    private fun drawStar(canvas: Canvas, x: Float, y: Float, r: Float, rotDeg: Float, color: Int, alpha: Int) {
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

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true

        if (gs.phase == BattlePhase.CAUGHT || gs.phase == BattlePhase.ESCAPED ||
            gs.phase == BattlePhase.ENEMY_FAINTED || gs.phase == BattlePhase.PLAYER_FAINTED) {
            onCaught?.invoke(); return true
        }
        if (gs.phase == BattlePhase.TEXT_WAIT && !busy) { advText(); return true }
        if (busy) return true

        val sx = GBW.toFloat() / dstR.width()
        val sy = GBH.toFloat() / dstR.height()
        val gx = ((e.x - dstR.left) * sx).toInt()
        val gy = ((e.y - dstR.top)  * sy).toInt()

        zones.forEach { z -> if (z.r.contains(gx, gy)) { z.fn(); return true } }
        return true
    }

    private fun setText(l1: String, l2: String) {
        gs.textLine1 = l1; gs.textLine2 = l2
        if (gs.phase != BattlePhase.CAUGHT && gs.phase != BattlePhase.ESCAPED &&
            gs.phase != BattlePhase.ENEMY_FAINTED && gs.phase != BattlePhase.PLAYER_FAINTED) {
            gs.phase = BattlePhase.TEXT_WAIT
        }
        invalidate()
    }

    private fun startIntro() {
        busy = true
        if (gs.isEnemyShiny) setText("*SHINY* ${gs.enemy.name}", "APPEARED!")
        else setText("WILD ${gs.enemy.name}", "APPEARED!")
        val startTime = System.currentTimeMillis()
        val duration  = 1000L

        val introLoop = object : Runnable {
            override fun run() {
                val elapsed  = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                gs.introOffset = 1.0f - progress
                invalidate()
                if (progress < 1f) handler.postDelayed(this, 16)
                else { if (gs.isEnemyShiny) startShinyAnim() else busy = false }
            }
        }
        handler.post(introLoop)
    }

    private fun showMain()    { busy = false; gs.phase = BattlePhase.MAIN_MENU; zones.clear(); invalidate() }
    private fun startFight()  { if (gs.player.moves.all { it.pp <= 0 }) { setText("NO PP LEFT!", ""); return }; gs.phase = BattlePhase.FIGHT_MENU; invalidate() }
    private fun showPkmn()    { setText("ONLY ${gs.player.name}", "IN PARTY!") }
    private fun startItem()   { gs.phase = BattlePhase.ITEM_MENU; zones.clear(); invalidate() }

    private fun doRun() {
        busy = true
        if (BattleEngine.tryEscape(gs.player.speed, gs.enemy.speed)) {
            gs.phase = BattlePhase.ESCAPED
            setText("GOT AWAY", "SAFELY!")
        } else {
            setText("CANT ESCAPE!", "")
            scheduleAfterText { enemyTurn() }
        }
        invalidate()
    }

    private fun playerMove(idx: Int) {
        val mv = gs.player.moves[idx]
        if (mv.pp <= 0) { setText("NO PP LEFT!", ""); return }
        mv.pp--; busy = true; gs.phase = BattlePhase.ANIMATING
        setText("${gs.player.name}", "USED ${mv.name}!"); invalidate()
        handler.postDelayed({
            if (Random.nextInt(100) >= mv.accuracy) {
                setText("${gs.player.name}", "MISSED!"); scheduleAfterText { enemyTurn() }; return@postDelayed
            }
            if (mv.power > 0) {
                val enemyType = gs.enemy.moves.firstOrNull()?.type ?: MakromonType.NORMAL
                val dmg = BattleEngine.calcDamage(gs.player.level, mv.power, gs.player.attack, gs.enemy.defense, mv.type, enemyType)
                doFlash {
                    gs.enemy.currentHp = maxOf(0, gs.enemy.currentHp - dmg)
                    invalidate()

                    handler.postDelayed({
                        if (gs.enemy.currentHp <= 0) {
                            gs.enemyVisible = false
                            gs.phase = BattlePhase.ENEMY_FAINTED
                            setText("${gs.enemy.name}", "FAINTED!")

                            // --- QUEST SYSTÉM: OZNÁMENÍ VÝHRY ---
                            // Zjistíme typ nepřítele (bereme typ prvního útoku, jak to máš v dmg výpočtu)
                            val enemyType = gs.enemy.moves.firstOrNull()?.type ?: MakromonType.NORMAL

                            // Informujeme QuestManager o výhře nad konkrétním typem
                            (context as? MakromonMapActivity)?.let { map ->
                                map.questManager.onBattleWon(enemyType.name, biome = map.getCurrentBiome().name)
                            }

                            Thread {
                                // Logika pro XP a Makrodex
                                val mId = BattleFactory.makrodexId(gs.enemy)
                                val spawnEntry = SpawnManager.allEntries.find { it.id == mId }
                                val rarity = spawnEntry?.rarity ?: Rarity.COMMON

                                val baseScore = when (rarity) {
                                    Rarity.COMMON    -> 15
                                    Rarity.RARE      -> 30
                                    Rarity.EPIC      -> 60
                                    Rarity.LEGENDARY -> 120
                                    Rarity.MYTHIC    -> 250
                                }
                                val totalBattleXp = baseScore + (gs.enemy.level * 3)
                                awardXpToActiveMakromon(totalBattleXp)

                                handler.post {
                                    // Počkej 2 sekundy, než se vrátíš na mapu/ukončíš souboj
                                    handler.postDelayed({
                                        onCaught?.invoke()
                                    }, 2000)
                                }
                            }.start()
                        } else {
                            // Nepřítel přežil, pokračujeme v boji
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
                doFlash { setText("ENEMY STAT", "FELL!"); scheduleAfterText { enemyTurn() } }
            }
        }, 1200)
    }

    /**
     * Vytvoří hráčova Makromona podle ID a levelu.
     * Používá nový BattleFactory s Makromony.
     */
    fun createPlayerMakromon(id: String, level: Int): Makromon {
        val base = when (id) {
            "001" -> BattleFactory.createIgnar()
            "002" -> BattleFactory.createIgnaroc()
            "003" -> BattleFactory.createIgnaroth()
            "004" -> BattleFactory.createAqulin()
            "005" -> BattleFactory.createAqlind()
            "006" -> BattleFactory.createAqulinox()
            "007" -> BattleFactory.createFlori()
            "008" -> BattleFactory.createFlorind()
            "009" -> BattleFactory.createFlorindra()
            "010" -> BattleFactory.createUmbex()
            "011" -> BattleFactory.createLumex()
            "012" -> BattleFactory.createSpirra()
            "013" -> BattleFactory.createFlamirra()
            "014" -> BattleFactory.createAquirra()
            "015" -> BattleFactory.createVerdirra()
            "016" -> BattleFactory.createShadirra()
            "017" -> BattleFactory.createCharmirra()
            "018" -> BattleFactory.createGlacirra()
            "019" -> BattleFactory.createDrakirra()
            "020" -> BattleFactory.createFinlet()
            "021" -> BattleFactory.createSerpfin()
            "022" -> BattleFactory.createMycit()
            "023" -> BattleFactory.createMydrus()
            "024" -> BattleFactory.createSoulu()
            "025" -> BattleFactory.createSoulex()
            "026" -> BattleFactory.createSoulord()
            "027" -> BattleFactory.createPhantil()
            "028" -> BattleFactory.createPhantius()
            "029" -> BattleFactory.createPhantiax()
            "030" -> BattleFactory.createGudwin()
            "031" -> BattleFactory.createAxlu()
            else  -> BattleFactory.createSpirra() // Spirra jako bezpečný fallback
        }
        return BattleEngine.initializeStatsForLevel(base, level)
    }

    private fun scheduleAfterText(action: () -> Unit) {
        busy = false; gs.phase = BattlePhase.TEXT_WAIT; pendingAction = action; invalidate()
    }

    private fun advText() {
        val action = pendingAction
        pendingAction = null
        when (gs.phase) {
            BattlePhase.ESCAPED, BattlePhase.CAUGHT,
            BattlePhase.ENEMY_FAINTED, BattlePhase.PLAYER_FAINTED -> onCaught?.invoke()
            BattlePhase.TEXT_WAIT -> if (action != null) action() else showMain()
            else -> if (action != null) action() else showMain()
        }
    }

    private fun enemyTurn() {
        if (gs.enemy.currentHp <= 0 || gs.player.currentHp <= 0) { busy = false; showMain(); return }
        busy = true
        val mv = BattleEngine.enemyChooseMove(gs.enemy); mv.pp = maxOf(0, mv.pp - 1)
        setText("${gs.enemy.name}", "USED ${mv.name}!"); invalidate()
        handler.postDelayed({
            if (mv.power > 0) {
                val atkE = (gs.enemy.attack   * gs.enemyAtkMod).toInt()
                val defE = (gs.player.defense * gs.enemyDefMod).toInt()
                val playerType = gs.player.moves.firstOrNull()?.type ?: MakromonType.NORMAL
                val dmg = BattleEngine.calcDamage(gs.enemy.level, mv.power, atkE, defE, mv.type, playerType)
                doFlash {
                    gs.player.currentHp = maxOf(0, gs.player.currentHp - dmg); invalidate()
                    handler.postDelayed({
                        if (gs.player.currentHp <= 0) {
                            gs.phase = BattlePhase.PLAYER_FAINTED
                            setText("${gs.player.name}", "FAINTED!")
                            pendingAction = { onCaught?.invoke() }
                        } else {
                            setText("IT DEALT", "$dmg DAMAGE!")
                            busy = false; gs.phase = BattlePhase.TEXT_WAIT
                            pendingAction = { showMain() }; invalidate()
                        }
                    }, 400)
                }
            } else {
                setText("${gs.player.name}", "STAT FELL!")
                busy = false; gs.phase = BattlePhase.TEXT_WAIT
                pendingAction = { showMain() }; invalidate()
            }
        }, 1200)
    }

    // ── Hod Makroballem ──────────────────────────────────────────────────────

    /** Jednoduchý časovaný průběh 0..1 (snímky po 16 ms). */
    private fun tween(ms: Long, update: (Float) -> Unit, end: () -> Unit) {
        val start = android.os.SystemClock.uptimeMillis()
        handler.post(object : Runnable {
            override fun run() {
                val p = ((android.os.SystemClock.uptimeMillis() - start) / ms.toFloat()).coerceIn(0f, 1f)
                update(p); invalidate()
                if (p < 1f) handler.postDelayed(this, 16) else end()
            }
        })
    }

    private fun bounce(p: Float): Float {
        val n = 7.5625f; val d = 2.75f
        return when {
            p < 1f / d -> n * p * p
            p < 2f / d -> { val q = p - 1.5f / d; n * q * q + 0.75f }
            p < 2.5f / d -> { val q = p - 2.25f / d; n * q * q + 0.9375f }
            else -> { val q = p - 2.625f / d; n * q * q + 0.984375f }
        }
    }

    private fun throwBall(b: cz.uhk.macroflow.pokemon.balls.Makroball) {
        if (gs.enemy.currentHp <= 0) return
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
    private fun onBallHit() {
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

    private fun startWobbleBall() {
        val baseMultiplier  = BattleFactory.catchMultiplier(gs.enemy)
        val finalMultiplier = baseMultiplier * ball.catchMultiplier
        val (success, wobbles) = BattleEngine.calcCaptureResult(gs.enemy, finalMultiplier)
        gs.captureSuccess = success; gs.wobbleCount = wobbles; gs.wobbleDone = 0
        gs.phase = BattlePhase.BALL_WOBBLE
        setText("...", "")
        handler.postDelayed({ doWobble() }, 350)
    }

    /** Kolébání ballu na zemi (pivot ve spodku) – počet podle šance na chycení. */
    private fun doWobble() {
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

    private fun breakFree() {
        // Ball se otevře a Makromon z něj vyskočí zpátky
        tween(150, { p -> ballOpen = 75f * p; captureBeam = p }) {
            gs.enemyVisible = true; enemyAbsorb = 1f
            tween(320, { p -> enemyAbsorb = 1f - p; captureBeam = 1f - p }) {
                ballShown = false; ballOpen = 0f; enemyAbsorb = 0f; captureBeam = 0f
                afterBreakFree()
            }
        }
    }

    private fun afterBreakFree() {
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

        if (Random.nextFloat() < runAwayChance) {
            gs.phase = BattlePhase.ESCAPED
            setText("${gs.enemy.name}", "RAN AWAY!")
            pendingAction = { onCaught?.invoke() }
        } else {
            setText("${gs.enemy.name}", "BROKE FREE!")
            gs.phase = BattlePhase.TEXT_WAIT
            pendingAction = { showMain() }
        }
    }

    /** Ball + záře při otevření + hvězdičky po chycení; kreslí se nad soupeřem. */
    private fun drawBallOverlay(canvas: Canvas) {
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

    private fun caught() {
        gs.phase = BattlePhase.CAUGHT
        tween(650, { p -> clickStars = p }) { clickStars = -1f }

        val mId = BattleFactory.makrodexId(gs.enemy)

        val entity = CapturedMakromonEntity(
            makromonId = mId,
            name       = gs.enemy.name,
            level      = gs.enemy.level,
            isShiny    = gs.isEnemyShiny,
            caughtDate = System.currentTimeMillis()
        )

        Thread {
            db.capturedMakromonDao().insertMakromon(entity)

            if (FirebaseRepository.isLoggedIn) {
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    FirebaseRepository.uploadCapturedMakromon(entity)
                    FirebaseRepository.uploadMakrodexStatus(mId)
                }
            }

            val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
            val isAcquired = prefs.getBoolean("makromonAcquired", false)

            handler.post {
                setText((if (gs.isEnemyShiny) "CAUGHT *" else "CAUGHT ") + "${gs.enemy.name.take(7)}!",
                    if (gs.isEnemyShiny) "SHINY!" else "")
                busy = false

                if (isAcquired) {
                    // --- 🏆 XP ODMĚNA PODLE RARITY Z NOVÉHO POOLU ---
                    val spawnEntry = SpawnManager.allEntries.find { it.id == mId }
                    val rarity = spawnEntry?.rarity ?: Rarity.COMMON

                    val xpReward = when (rarity) {
                        Rarity.COMMON    -> 20
                        Rarity.RARE      -> 50
                        Rarity.EPIC      -> 100
                        Rarity.LEGENDARY -> 250
                        Rarity.MYTHIC    -> 500
                    } * (if (gs.isEnemyShiny) 2 else 1)   // shiny = dvojnásobná odměna

                    awardXpToActiveMakromon(xpReward)
                }
                pendingAction = { onCaught?.invoke() }
            }
        }.start()
    }

    /**
     * Udělí XP aktivnímu Makromonovi přímo z BattleView.
     * Funguje v jakémkoli Activity kontextu (MakromonMapActivity i MainActivity).
     */
    private fun awardXpToActiveMakromon(xpAmount: Int) {
        val prefs = context.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
        val activeCapturedId = prefs.getInt("currentOnBarCapturedId", -1)
        if (activeCapturedId == -1) return

        Thread {
            val localDb  = AppDatabase.getDatabase(context)
            val makromon = localDb.capturedMakromonDao().getMakromonById(activeCapturedId)
                ?: return@Thread

            val oldLevel = makromon.level
            makromon.xp += xpAmount
            makromon.level = PokemonLevelCalc.levelFromXp(makromon.xp)

            // 1. Uložíme do lokální DB
            localDb.capturedMakromonDao().updateMakromon(makromon)

            // 2. Synchronizace na pozadí
            if (FirebaseRepository.isLoggedIn) {
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    try {
                        FirebaseRepository.uploadCapturedMakromon(makromon)
                    } catch (e: Exception) {
                        android.util.Log.e("XP_AWARD", "Firebase upload failed: ${e.message}")
                    }
                }
            }

            // 3. Aktualizace UI na hlavním vlákně
            handler.post {
                // Zobrazení toastu
                val levelMsg = if (makromon.level > oldLevel) " 🎊 Level up! Lv.${makromon.level}!" else ""
                android.widget.Toast.makeText(
                    context,
                    "⭐ ${makromon.name} získal $xpAmount XP!$levelMsg",
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                // --- KLÍČOVÁ ZMĚNA ---
                // Zavoláme pouze refresh lišty v MainActivity,
                // což aktualizuje texty a progress bar na spodním baru,
                // ale nepřepne fragment (nevyhodí tě na Dashboard).
                (context as? MainActivity)?.updateMakromonVisibility()
            }
        }.start()
    }

    private fun doFlash(after: () -> Unit) {
        flashOn = true; invalidate()
        handler.postDelayed({ flashOn = false; invalidate(); after() }, 180)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }
}