package cz.uhk.macroflow.pokemon

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import coil.ImageLoader
import coil.request.ImageRequest
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

class PokemonBattleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
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
    private var ballX = 0f; private var ballY = 0f
    private var flashOn = false; private var cursorOn = true
    private var busy = false
    private var pendingAction: (() -> Unit)? = null
    private var ballVisible = 0
    private var selectedBallId = "poke_ball"

    private var enemyBitmap: Bitmap? = null
    private var playerBitmap: Bitmap? = null
    private var introStarted = false

    private val db = AppDatabase.getDatabase(context)

    private val cursorTick = object : Runnable {
        override fun run() { cursorOn = !cursorOn; invalidate(); handler.postDelayed(this, 480) }
    }

    private data class Zone(val r: Rect, val fn: () -> Unit)
    private val zones = mutableListOf<Zone>()

    private fun spriteUrl(webName: String) =
        "https://img.pokemondb.net/sprites/firered-leafgreen/normal/$webName.png"

    init {
        PokemonSprites.init(context)

        val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

        // Získáme unikátní ID řádku z DB a záložní ID druhu
        val activeCapturedId = prefs.getInt("currentOnBarCapturedId", -1)
        val backupPokemonId = prefs.getString("currentOnBarId", "050") ?: "050"

        Thread {
            val db = AppDatabase.getDatabase(context)

            // 1. Pokusíme se načíst reálného pokémona z DB
            val caughtEntity = if (activeCapturedId != -1) {
                db.capturedPokemonDao().getPokemonById(activeCapturedId)
            } else null

            // 2. Definujeme parametry hráče na základě dat z DB nebo zálohy
            val pId = caughtEntity?.pokemonId ?: backupPokemonId
            val playerLevel = caughtEntity?.level ?: 1

            // 3. Vytvoření objektu hráče se správnými staty
            val playerWithStats = createPlayerPokemon(pId, playerLevel)

            // 4. Inicializace nepřítele (level odvozen od hráče)
            val baseEnemy = SpawnManager.rollWildEncounter(context)
            val randomEnemyLevel = (playerLevel + Random.nextInt(-2, 3)).coerceAtLeast(1)
            val enemyWithStats = BattleEngine.initializeStatsForLevel(baseEnemy, randomEnemyLevel)

            // 5. Načtení Pokéballů z inventáře
            val currentPokeballs = db.userItemDao().getItemCount("poke_ball") ?: 0

            // 6. Sestavení herního stavu a aktualizace UI
            handler.post {
                gs = BattleState(
                    player = playerWithStats,
                    enemy = enemyWithStats,
                    ballCount = currentPokeballs
                )

                handler.post(cursorTick)

                // Načtení spritů pro oba bojovníky
                loadSprite(spriteUrl(BattleFactory.webName(enemyWithStats)), isPlayer = false)
                loadSprite(spriteUrl(BattleFactory.webName(playerWithStats)), isPlayer = true)

                invalidate()
            }
        }.start()
    }


    private fun loadSprite(url: String, isPlayer: Boolean) {
        val loader  = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .target(
                onSuccess = { drawable ->
                    val bmp = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        if (isPlayer) playerBitmap = bmp else enemyBitmap = bmp
                    }
                    maybeStartIntro()
                },
                onError = { maybeStartIntro() }
            )
            .build()
        loader.enqueue(request)
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
        // ✅ Pokud ještě vlákno nenačetlo data z DB do 'gs', přeskočíme kreslení a nespadneme!
        if (!::gs.isInitialized) {
            // Volitelně sem můžeš hodit černé pozadí, ať to nebliká bíle
            canvas.drawColor(C_BG)
            return
        }

        renderFrame()
        canvas.drawBitmap(gbBmp, srcR, dstR, sp)
        drawSpritesOverlay(canvas)
    }

    private fun drawSpritesOverlay(canvas: Canvas) {
        val sc = scale
        if (sc <= 0f) return

        if (gs.enemyVisible) {
            enemyBitmap?.let { bmp ->
                val targetH = enemySpriteHeight() * sc
                val targetW = targetH * bmp.width.toFloat() / bmp.height.toFloat()
                val sx = gbX(112f) - targetW / 2f
                val sy = gbY(54f)  - targetH
                canvas.drawBitmap(bmp, null, RectF(sx, sy, sx + targetW, sy + targetH), spSmooth)
            }
        }

        playerBitmap?.let { bmp ->
            val targetH = 36f * sc
            val targetW = targetH * bmp.width.toFloat() / bmp.height.toFloat()
            val cx = gbX(48f)
            val sy = gbY(82f) - targetH
            canvas.save()
            canvas.scale(-1f, 1f, cx, 0f)
            canvas.drawBitmap(bmp, null, RectF(cx - targetW / 2f, sy, cx + targetW / 2f, sy + targetH), spSmooth)
            canvas.restore()
        }
    }

    private fun enemySpriteHeight(): Float = when (gs.enemy.name) {
        "CATERPIE"   -> 24f
        "METAPOD"    -> 24f
        "BUTTERFREE" -> 32f
        "SNORLAX"    -> 42f
        "CHARIZARD"  -> 40f
        "MEWTWO"     -> 38f
        "MEW"        -> 36f
        "GENGAR"     -> 34f
        "HAUNTER"    -> 32f
        "GASTLY"     -> 28f
        "KANGASKHAN" -> 34f
        "BULBASAUR"  -> 28f
        "SQUIRTLE"   -> 26f
        "CHARMANDER" -> 28f
        "CHARMELEON" -> 30f
        "PIKACHU"    -> 24f
        "RAICHU"     -> 30f
        "DITTO"      -> 36f
        "EEVEE"      -> 24f
        "DIGLETT"    -> 22f
        "DUGTRIO"    -> 30f
        else         -> 28f
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
        when (ballVisible) {
            1 -> drawSprite(c, PokemonSprites.POKEBALL,
                PokemonSprites.POKEBALL_W, PokemonSprites.POKEBALL_H,
                ballX.toInt(), ballY.toInt())
            2 -> drawSprite(c, PokemonSprites.POKEBALL_CLOSED,
                PokemonSprites.POKEBALL_W, PokemonSprites.POKEBALL_H,
                ballX.toInt(), ballY.toInt())
        }
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

        val enemyName = gs.enemy.name.take(7)
        PokemonSprites.drawText(c, enemyName, x+3, y+3, C_TEXT, fp)

        PokemonSprites.drawText(c, ":L${gs.enemy.level}", x+48, y+3, C_TEXT, fp)
        PokemonSprites.drawText(c, "HP", x+3, y+13, C_TEXT, fp)
        drawHPBar(c, x+16, y+13, 54, 5, gs.enemy.currentHp, gs.enemy.maxHp)
    }

    private fun drawPlayerHUD(c: Canvas) {
        val x = 84; val y = 58; val w = 75; val h = 36; drawUIBox(c, x, y, w, h)

        // ✅ Zkrátíme jméno na 7 znaků i u tvého panelu
        val playerName = gs.player.name.take(7)
        PokemonSprites.drawText(c, playerName, x+3, y+3, C_TEXT, fp)

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
        PokemonSprites.drawText(c, "PKMN",  bx+44, by+9,  C_TEXT, fp)
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
        val pokeCount  = db.userItemDao().getItemCount("poke_ball")  ?: 0
        val greatCount = db.userItemDao().getItemCount("great_ball") ?: 0
        PokemonSprites.drawText(c, "1.POKE BALL X$pokeCount",  bx+4, by+5,  C_TEXT, fp)
        PokemonSprites.drawText(c, "2.GREAT BALL X$greatCount", bx+4, by+18, C_TEXT, fp)
        PokemonSprites.drawText(c, "BACK", bx+bw-28, by+34, C_TEXT, fp)
        zones.clear()
        zones.add(Zone(Rect(bx, by,    bx+bw, by+15)) { if (!busy && pokeCount  > 0) throwBall("poke_ball")  })
        zones.add(Zone(Rect(bx, by+16, bx+bw, by+31)) { if (!busy && greatCount > 0) throwBall("great_ball") })
        zones.add(Zone(Rect(bx+bw-32, by+32, bx+bw, by+bh)) { if (!busy) showMain() })
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

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_DOWN) return true

        if (gs.phase == BattlePhase.CAUGHT || gs.phase == BattlePhase.ESCAPED ||
            gs.phase == BattlePhase.ENEMY_FAINTED || gs.phase == BattlePhase.PLAYER_FAINTED) {
            onCaught?.invoke()
            return true
        }

        if (gs.phase == BattlePhase.TEXT_WAIT && !busy) {
            advText()
            return true
        }

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

    private fun startIntro()  { setText("WILD ${gs.enemy.name}", "APPEARED!") }
    private fun showMain()    { busy = false; gs.phase = BattlePhase.MAIN_MENU; zones.clear(); invalidate() }
    private fun startFight()  { if (gs.player.moves.all { it.pp <= 0 }) { setText("NO PP LEFT!",""); return }; gs.phase = BattlePhase.FIGHT_MENU; invalidate() }
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

                // ✅ Pro zjednodušení vezmeme typ nepřítele podle jeho prvního útoku v seznamu
                val enemyType = gs.enemy.moves.firstOrNull()?.type ?: PokemonType.NORMAL

                // Přidali jsme typy do výpočtu damage!
                val dmg = BattleEngine.calcDamage(gs.player.level, mv.power, gs.player.attack, gs.enemy.defense, mv.type, enemyType)

                doFlash {
                    gs.enemy.currentHp = maxOf(0, gs.enemy.currentHp - dmg); invalidate()
                    handler.postDelayed({
                        if (gs.enemy.currentHp <= 0) {
                            gs.enemyVisible = false; gs.phase = BattlePhase.ENEMY_FAINTED
                            setText("${gs.enemy.name}", "FAINTED!")

                            // ⚔️ ODMĚNA ZA PORAŽENÍ NEPŘÍTELE (RPG XP)
                            Thread {
                                val pId = BattleFactory.pokedexId(gs.enemy)
                                val spawnEntry = SpawnManager.allEntries.find { it.id == pId }
                                val rarity = spawnEntry?.rarity ?: Rarity.COMMON

                                // 1. Základní XP podle rarity
                                val baseScore = when (rarity) {
                                    Rarity.COMMON -> 15
                                    Rarity.RARE -> 30
                                    Rarity.EPIC -> 60
                                    Rarity.LEGENDARY -> 120
                                    Rarity.MYTHIC -> 250
                                }

                                // 2. Modifikátor za level nepřítele
                                val levelBonus = gs.enemy.level * 3
                                val totalBattleXp = baseScore + levelBonus

                                // ✅ Uložíme XP přímo do DB — nepotřebujeme MainActivity
                                // BattleView běží v PokemonMapActivity, ne v MainActivity!
                                awardXpToActivePokemon(totalBattleXp)

                                handler.post {
                                    // Počkáme 2 vteřiny než se dočte text o porážce
                                    handler.postDelayed({
                                        onCaught?.invoke()
                                    }, 2000)
                                }
                            }.start()

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
                doFlash { setText("ENEMY STAT", "FELL!"); scheduleAfterText { enemyTurn() } }
            }
        }, 1200)
    }

    // ✅ TATO METODA TI CHYBĚLA A VYŘEŠÍ ČERVENOU CHYBU ZE SCREENSHOTU!
    private fun createPlayerPokemon(id: String, level: Int): Pokemon {
        val base = when (id) {
            "010" -> BattleFactory.createCaterpie()
            "011" -> BattleFactory.createMetapod()
            "012" -> BattleFactory.createButterfree()
            "050" -> BattleFactory.createDiglett()
            "051" -> BattleFactory.createDugtrio()
            "025" -> BattleFactory.createPikachu()
            "026" -> BattleFactory.createRaichu()
            "133" -> BattleFactory.createEevee()
            "132" -> BattleFactory.createDitto()
            "131" -> BattleFactory.createLapras()
            "001" -> BattleFactory.createBulbasaur()
            "007" -> BattleFactory.createSquirtle()
            "004" -> BattleFactory.createCharmander()
            "005" -> BattleFactory.createCharmeleon()
            "092" -> BattleFactory.createGastly()
            "093" -> BattleFactory.createHaunter()
            "094" -> BattleFactory.createGengar()
            "115" -> BattleFactory.createKangaskhan()
            "143" -> BattleFactory.createSnorlax()
            "006" -> BattleFactory.createCharizard()
            "150" -> BattleFactory.createMewtwo()
            else -> BattleFactory.createCaterpie() // ✅ Změněno z Mew na Caterpie jako bezpečný fallback!
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
            BattlePhase.ENEMY_FAINTED, BattlePhase.PLAYER_FAINTED -> {
                onCaught?.invoke()
            }
            BattlePhase.TEXT_WAIT -> {
                if (action != null) action() else showMain()
            }
            else -> {
                if (action != null) action() else showMain()
            }
        }
    }

    private fun enemyTurn() {
        if (gs.enemy.currentHp <= 0 || gs.player.currentHp <= 0) {
            busy = false
            showMain()
            return
        }
        busy = true
        val mv = BattleEngine.enemyChooseMove(gs.enemy); mv.pp = maxOf(0, mv.pp - 1)
        setText("${gs.enemy.name}", "USED ${mv.name}!"); invalidate()
        handler.postDelayed({
            if (mv.power > 0) {
                val atkE = (gs.enemy.attack   * gs.enemyAtkMod).toInt()
                val defE = (gs.player.defense * gs.enemyDefMod).toInt()

                // ✅ OPRAVA: Pro zjednodušení vezmeme typ hráče podle jeho prvního útoku v seznamu
                val playerType = gs.player.moves.firstOrNull()?.type ?: PokemonType.NORMAL

                // Přidali jsme typy do výpočtu damage!
                val dmg  = BattleEngine.calcDamage(gs.enemy.level, mv.power, atkE, defE, mv.type, playerType)

                doFlash {
                    gs.player.currentHp = maxOf(0, gs.player.currentHp - dmg); invalidate()
                    handler.postDelayed({
                        if (gs.player.currentHp <= 0) {
                            gs.phase = BattlePhase.PLAYER_FAINTED
                            setText("${gs.player.name}", "FAINTED!")
                            pendingAction = { onCaught?.invoke() }
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

    private fun throwBall(ballType: String) {
        if (gs.enemy.currentHp <= 0) return
        selectedBallId = ballType
        busy = true
        gs.phase = BattlePhase.BALL_THROW

        Thread {
            db.userItemDao().consumeItem(ballType, 1)
            val newCount = db.userItemDao().getItemCount("poke_ball") ?: 0
            handler.post { gs.ballCount = newCount }
        }.start()

        setText("THREW A", "${ballType.uppercase().replace("_", " ")}!")

        val sx = 28f; val sy = 58f; val tx = 100f; val ty = 28f
        ballX = sx; ballY = sy; var t = 0f; ballVisible = 1

        val speedStep = when (gs.enemy.name) {
            "DIGLETT", "PIKACHU", "EEVEE", "CATERPIE" -> 0.055f
            "MEWTWO", "MEW"                           -> 0.025f
            "CHARIZARD", "SNORLAX"                    -> 0.030f
            "KANGASKHAN"                              -> 0.030f
            else                                      -> 0.040f
        }

        fun fly() {
            if (t >= 1f) {
                gs.enemyVisible = false; ballX = 100f; ballY = 36f; invalidate()
                handler.postDelayed({
                    ballY = 46f; invalidate()
                    handler.postDelayed({ startWobbleBall() }, 300)
                }, 350)
                return
            }
            ballX = sx + (tx - sx) * t
            ballY = sy + (ty - sy) * t + (-sin(t * PI.toFloat()) * 26f)
            t += speedStep; invalidate()
            handler.postDelayed({ fly() }, 20)
        }
        fly()
    }

    private fun startWobbleBall() {
        val baseMultiplier  = BattleFactory.catchMultiplier(gs.enemy)
        val ballBonus       = if (selectedBallId == "great_ball") 1.5f else 1.0f
        val finalMultiplier = baseMultiplier * ballBonus
        val (success, wobbles) = BattleEngine.calcCaptureResult(gs.enemy, finalMultiplier)
        gs.captureSuccess = success; gs.wobbleCount = wobbles; gs.wobbleDone = 0
        gs.phase = BattlePhase.BALL_WOBBLE
        doWobble()
    }

    private fun doWobble() {
        if (gs.wobbleDone >= gs.wobbleCount) {
            handler.postDelayed({ if (gs.captureSuccess) caught() else breakFree() }, 600)
            return
        }
        gs.wobbleDone++
        setText("...", "")
        invalidate()
        handler.postDelayed({
            setText("", "")
            invalidate()
            handler.postDelayed({ doWobble() }, 500)
        }, 700)
    }

    private fun breakFree() {
        ballVisible = 0
        gs.enemyVisible = true
        invalidate()

        val runAwayChance = when (gs.enemy.name) {
            "MEWTWO", "MEW"                               -> 0.40f
            "CHARIZARD"                                   -> 0.25f
            "SNORLAX", "HAUNTER", "GENGAR", "CHARMELEON"  -> 0.15f
            "KANGASKHAN"                                  -> 0.20f
            "LAPRAS"                                      -> 0.30f
            else                                          -> 0.08f
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

    private fun caught() {
        gs.phase = BattlePhase.CAUGHT
        ballVisible = 2
        invalidate()

        val pId = BattleFactory.pokedexId(gs.enemy)

        val entity = CapturedPokemonEntity(
            pokemonId = pId,
            name = gs.enemy.name,
            level = gs.enemy.level,
            caughtDate = System.currentTimeMillis() // Přidá unikátní čas chycení
        )

        Thread {
            db.capturedPokemonDao().insertPokemon(entity)

            if (FirebaseRepository.isLoggedIn) {
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    // ✅ Uloží do inventáře s unikátním ID (nepřepíše se!)
                    FirebaseRepository.uploadCapturedPokemon(entity)

                    // ✅ Označí v Pokédexu jako viděného i chyceného
                    FirebaseRepository.uploadPokedexStatus(pId)
                }
            }

            val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
            val isAcquired = prefs.getBoolean("pokemonAcquired", false)

            handler.post {
                setText("CAUGHT ${gs.enemy.name.take(7)}!", "")
                busy = false

                // ✅ XP za chycení — přímo do DB, bez závislosti na MainActivity
                if (isAcquired) {
                    val xpReward = when (SpawnManager.allEntries.find { it.id == pId }?.rarity) {
                        Rarity.COMMON    -> 20
                        Rarity.RARE      -> 50
                        Rarity.EPIC      -> 100
                        Rarity.LEGENDARY -> 250
                        Rarity.MYTHIC    -> 250
                        else             -> 20
                    }
                    awardXpToActivePokemon(xpReward)
                }

                pendingAction = { onCaught?.invoke() }
            }
        }.start()
    }

    /**
     * Udělí XP aktivnímu pokémonovi přímo z BattleView.
     * Funguje v jakémkoli Activity kontextu (PokemonMapActivity i MainActivity).
     * Zobrazí Toast s výsledkem a nahraje do Firebase pokud je uživatel přihlášen.
     */
    private fun awardXpToActivePokemon(xpAmount: Int) {
        val prefs = context.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
        val activeCapturedId = prefs.getInt("currentOnBarCapturedId", -1)
        if (activeCapturedId == -1) return

        Thread {
            val localDb = AppDatabase.getDatabase(context)
            val pokemon = localDb.capturedPokemonDao().getPokemonById(activeCapturedId)
                ?: return@Thread

            val oldLevel = pokemon.level
            pokemon.xp += xpAmount
            pokemon.level = PokemonLevelCalc.levelFromXp(pokemon.xp)

            localDb.capturedPokemonDao().updatePokemon(pokemon)

            if (FirebaseRepository.isLoggedIn) {
                try {
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                        FirebaseRepository.uploadCapturedPokemon(pokemon)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("XP_AWARD", "Firebase upload failed: ${e.message}")
                }
            }

            handler.post {
                val levelMsg = if (pokemon.level > oldLevel) " 🎊 Level up! Lv.${pokemon.level}!" else ""
                android.widget.Toast.makeText(
                    context,
                    "⭐ ${pokemon.name} získal $xpAmount XP!$levelMsg",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
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