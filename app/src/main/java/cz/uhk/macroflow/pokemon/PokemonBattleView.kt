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
    private val enemyShiny: Boolean = false,
    /** Strážce jeskyně nebo legenda z vrcholu (docs/adr/0014); null = divoké setkání. */
    private val special: cz.uhk.macroflow.pokemon.legend.SpecialBattle? = null
) : View(context, attrs) {

    var onCaught: (() -> Unit)? = null

    private val GBW = 160
    private val GBH = 144

    private val C_BG     = 0xFFF8F8F8.toInt()
    // HUD nad 3D arénou: průsvitný tmavý panel, bílé písmo se stínem (docs/adr/0038)
    private val C_HUD_BG   = 0x9E0C1420.toInt()
    private val C_HUD_LINE = 0xFFF0F0F0.toInt()
    private val C_HUD_TEXT = 0xFFF8F8F8.toInt()
    private val C_HUD_SHADOW = 0xFF101418.toInt()

    /** Hráčův Makromon: stojí blíž kameře, nohy má pod dolním okrajem scény. */
    private val PLAYER_FOOT_Y = cz.uhk.macroflow.pokemon.arena.Arenas.PLAYER_Y / 3f
    private val PLAYER_H = 46f

    // ── Voxelová aréna podle lokace ──
    private var arenaBmp: Bitmap? = null
    private var arenaTheme = cz.uhk.macroflow.pokemon.arena.ArenaTheme.MEADOW
    private val arenaDst = RectF()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x5A000000 }
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
    private val medCounts = HashMap<cz.uhk.macroflow.pokemon.status.MedItem, Int>()
    /** Menu předmětů: 0 = výběr kategorie, 1 = Makrobally, 2 = lékárnička. */
    private var itemPage = 0

    // ── Stavy (spánek, paralýza…) – platí jen po dobu souboje ──
    private var playerCond = cz.uhk.macroflow.pokemon.status.Condition()

    // ── Tým (docs/adr/0034): až 6 Makromonů, v souboji se dají střídat ──
    private class PartyMember(
        val capturedId: Int,
        val mon: Makromon,
        val shiny: Boolean,
        val cond: cz.uhk.macroflow.pokemon.status.Condition = cz.uhk.macroflow.pokemon.status.Condition()
    )
    private val party = mutableListOf<PartyMember>()
    private var partyIdx = 0
    /** Menu týmu je vynucené (bojující Makromon omdlel) – nejde zavřít. */
    private var partyForced = false
    private var partyMenu = false
    /** Pasivní bonus Chytání (snižuje šanci na útěk po vyskočení z ballu). */
    private var catchingPassive = 0.0
    private val enemyCond = cz.uhk.macroflow.pokemon.status.Condition()

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
        startArena(currentBiome)

        Thread {
            val db = AppDatabase.getDatabase(context)

            val caughtEntity = if (activeCapturedId != -1) {
                db.capturedMakromonDao().getMakromonById(activeCapturedId)
            } else null
            catchingPassive = runCatching {
                cz.uhk.macroflow.pokemon.skills.SkillStore.state(context).passive(cz.uhk.macroflow.pokemon.skills.Skill.CATCHING)
            }.getOrDefault(0.0)
            // Ostatní členové týmu (první = aktivní parťák, ten je už načtený výš)
            val teamRest = runCatching { cz.uhk.macroflow.pokemon.skills.SkillStore.team(context) }.getOrDefault(emptyList())
                .filter { it != caughtEntity?.id }
                .mapNotNull { db.capturedMakromonDao().getMakromonById(it) }

            val mId = caughtEntity?.makromonId ?: backupMakromonId
            val playerLevel = caughtEntity?.level ?: 1
            val playerIsShiny = caughtEntity?.isShiny ?: false

            // Útoky chyceného Makromona (uložená sada), jinak základní útoky druhu; typ = typ druhu
            val playerBase = BattleFactory.createById(mId)
            val playerWithStats = createPlayerMakromon(mId, playerLevel).copy(
                moves = cz.uhk.macroflow.pokemon.wild.MovePool.resolve(caughtEntity?.moveListStr.orEmpty(), playerBase.moves),
                type = playerBase.speciesType
            )

            // --- 🎲 OPRAVENÝ ROLL S BIOMEM ---
            val enemyWithStats = if (special != null) {
                // Strážce / legenda: pevný Makromon s pevným levelem
                createPlayerMakromon(special.makromonId, special.level)
            } else {
                // Level podle lokality (docs/adr/0029) – jeskyně mají vlastní rozpětí, i když
                // druhy Makromonů sdílí s Horami; útoky náhodně z poolu druhu podle levelu
                val baseEnemy = SpawnManager.rollWildEncounter(context, currentBiome.wildBiome)
                val wildLevel = cz.uhk.macroflow.pokemon.wild.WildLevels.roll(currentBiome)
                val enemyId = BattleFactory.makrodexId(baseEnemy)
                BattleEngine.initializeStatsForLevel(
                    baseEnemy.copy(
                        moves = cz.uhk.macroflow.pokemon.wild.MovePool.wildMoveset(enemyId, wildLevel),
                        type = baseEnemy.speciesType
                    ),
                    wildLevel
                )
            }
            val enemyIsShiny = enemyShiny

            val counts = cz.uhk.macroflow.pokemon.balls.Makroball.entries.associateWith { db.userItemDao().getItemCount(it.id) ?: 0 }
            val currentPokeballs = counts.values.sum()
            val meds = cz.uhk.macroflow.pokemon.status.MedItem.entries.associateWith { db.userItemDao().getItemCount(it.id) ?: 0 }

            val restMembers = teamRest.map { e ->
                val base = BattleFactory.createById(e.makromonId)
                PartyMember(e.id, createPlayerMakromon(e.makromonId, e.level).copy(
                    moves = cz.uhk.macroflow.pokemon.wild.MovePool.resolve(e.moveListStr, base.moves),
                    type = base.speciesType
                ), e.isShiny)
            }

            handler.post {
                party.clear()
                party += PartyMember(caughtEntity?.id ?: -1, playerWithStats, playerIsShiny, playerCond)
                party += restMembers
                partyIdx = 0
                gs = BattleState(
                    player = playerWithStats,
                    enemy  = enemyWithStats,
                    ballCount = currentPokeballs
                )
                ballCounts.putAll(counts)
                medCounts.putAll(meds)
                gs.isEnemyShiny  = enemyIsShiny
                gs.isPlayerShiny = playerIsShiny
                // Shiny Makrodex: zapsat, že hráč tuhle shiny verzi viděl (docs/adr/0016)
                if (enemyIsShiny) {
                    val id = BattleFactory.makrodexId(enemyWithStats)
                    val seen = prefs.getStringSet(cz.uhk.macroflow.pokemon.shiny.ShinyDex.SEEN_KEY, emptySet()).orEmpty()
                    if (id !in seen) prefs.edit().putStringSet(cz.uhk.macroflow.pokemon.shiny.ShinyDex.SEEN_KEY, seen + id).apply()
                }

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

    /** Vykreslí 3D arénu lokace na pozadí (trvá desítky až stovky ms – běží během intra). */
    private fun startArena(biome: BiomeType) {
        arenaTheme = cz.uhk.macroflow.pokemon.arena.ArenaTheme.fromBiome(biome.name)
        arenaSeed = Random.nextInt(1_000_000)
        maybeRenderArena()
    }

    private var arenaSeed = 0
    private var arenaExtra = -1

    /** Aréna se kreslí, až je známá lokace i velikost pohledu (kolik se má prodloužit nahoru). */
    private fun maybeRenderArena() {
        if (dstR.width() <= 0f) return
        val A = cz.uhk.macroflow.pokemon.arena.Arenas
        // kolik řádků arény (3 na herní pixel) zabere místo nad herním plátnem
        val extra = kotlin.math.ceil(dstR.top * 3f / scale).toInt().coerceIn(0, 2000)
        if (extra == arenaExtra) return
        arenaExtra = extra
        val theme = arenaTheme; val seed = arenaSeed
        Thread {
            val px = runCatching { A.render(theme, seed, extra) }.getOrNull() ?: return@Thread
            val bmp = Bitmap.createBitmap(px, A.W, A.H + extra, Bitmap.Config.ARGB_8888)
            handler.post { if (extra == arenaExtra) { arenaBmp = bmp; invalidate() } }
        }.start()
    }

    /** Barva pozadí, než se aréna dopočítá. */
    private fun arenaFallback(): Pair<Int, Int> = when (arenaTheme) {
        cz.uhk.macroflow.pokemon.arena.ArenaTheme.TOWN -> 0xFFB8DCF0.toInt() to 0xFFC9CDD2.toInt()
        cz.uhk.macroflow.pokemon.arena.ArenaTheme.FOREST -> 0xFF7E9E78.toInt() to 0xFF3B6E2C.toInt()
        cz.uhk.macroflow.pokemon.arena.ArenaTheme.MOUNTAINS -> 0xFFC4D6E6.toInt() to 0xFF858075.toInt()
        cz.uhk.macroflow.pokemon.arena.ArenaTheme.CAVE_OPEN, cz.uhk.macroflow.pokemon.arena.ArenaTheme.CAVE_MAZE -> 0xFF0A080C.toInt() to 0xFF2E2B2C.toInt()
        cz.uhk.macroflow.pokemon.arena.ArenaTheme.WATER -> 0xFFB8DCF0.toInt() to 0xFF2A74AA.toInt()
        cz.uhk.macroflow.pokemon.arena.ArenaTheme.MEADOW -> 0xFFB8DCF0.toInt() to 0xFF58A03A.toInt()
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
        val gbH = w * GBH / GBW
        // Na výšku zabere, co dostane: nad herním plátnem pokračuje 3D aréna (docs/adr/0038)
        val h = if (MeasureSpec.getMode(hms) == MeasureSpec.UNSPECIFIED) gbH else maxOf(gbH, MeasureSpec.getSize(hms))
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val sc = minOf(w.toFloat() / GBW, h.toFloat() / GBH)
        val sw = GBW * sc; val sh = GBH * sc
        // herní plátno dole, nad ním aréna až k hornímu okraji
        dstR = RectF((w - sw) / 2f, h - sh, (w + sw) / 2f, h.toFloat())
        maybeRenderArena()
    }

    private val scale get() = if (dstR.width() > 0) dstR.width() / GBW.toFloat() else 1f
    private fun gbX(x: Float) = dstR.left + x * scale
    private fun gbY(y: Float) = dstR.top  + y * scale

    override fun onDraw(canvas: Canvas) {
        if (!::gs.isInitialized) { canvas.drawColor(C_BG); return }
        renderFrame()
        // aréna pod herním plátnem (horních 96 řádků), plátno má nahoře průhledno
        arenaDst.set(dstR.left, 0f, dstR.right, gbY(96f))
        val arena = arenaBmp
        if (arena != null) canvas.drawBitmap(arena, null, arenaDst, sp)
        else {
            val (sky, ground) = arenaFallback()
            fp.color = sky; canvas.drawRect(arenaDst.left, arenaDst.top, arenaDst.right, gbY(40f), fp)
            // (dopočítává se na pozadí během intra)
            fp.color = ground; canvas.drawRect(arenaDst.left, gbY(40f), arenaDst.right, arenaDst.bottom, fp)
        }
        canvas.save()
        canvas.clipRect(arenaDst)
        drawSpritesOverlay(canvas)
        canvas.restore()
        canvas.drawBitmap(gbBmp, srcR, dstR, sp)
        // záblesk útoku přes celou arénu i herní plátno
        if (flashOn) canvas.drawColor(0xBBFFFFFF.toInt())
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

        playerBitmap?.let { bmp ->
            val targetH = PLAYER_H * sc
            val targetW = targetH * bmp.width.toFloat() / bmp.height.toFloat()
            val cx = gbX(48f) - animOffset
            val sy = gbY(PLAYER_FOOT_Y) - targetH
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
        c.drawColor(0, PorterDuff.Mode.CLEAR)
        drawEnemyHUD(c)
        drawPlayerHUD(c)
        drawBottomUI(c)
    }

    private fun drawEnemyHUD(c: Canvas) {
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
    private fun drawStatusTag(c: Canvas, tag: String, x: Int, y: Int) {
        fp.color = when (tag) {
            "SLP" -> 0xFF8A8A8A.toInt(); "PAR" -> 0xFFC8960E.toInt(); "PSN" -> 0xFF8E44AD.toInt()
            "BRN" -> 0xFFE0592A.toInt(); else -> 0xFFE84F9A.toInt()
        }
        c.drawRect(x.toFloat(), y.toFloat(), (x + 21).toFloat(), (y + 9).toFloat(), fp)
        PokemonSprites.drawText(c, tag, x + 2, y + 1, 0xFFFFFFFF.toInt(), fp)
    }

    private fun drawPlayerHUD(c: Canvas) {
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
    private fun hudText(c: Canvas, s: String, x: Int, y: Int, color: Int = C_HUD_TEXT) {
        PokemonSprites.drawText(c, s, x + 1, y + 1, C_HUD_SHADOW, fp)
        PokemonSprites.drawText(c, s, x, y, color, fp)
    }

    /** Průsvitný panel HUDu s „hranatou závorkou“ ve stylu Game Boye (spodní hrana + jedna svislá). */
    private fun drawHudBox(c: Canvas, x: Int, y: Int, w: Int, h: Int, rightBracket: Boolean) {
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
        if (partyMenu) { drawPartyMenu(c); return }
        when (gs.phase) {
            BattlePhase.MAIN_MENU, BattlePhase.INTRO -> drawMainMenu(c)
            BattlePhase.FIGHT_MENU                   -> drawFightMenu(c)
            BattlePhase.ITEM_MENU                    -> drawItemMenu(c)
            else                                     -> drawTextPanel(c)
        }
    }

    /** Tým: 2 sloupce × 3 řádky, bojující má šipku, omdlelí šedě. */
    private fun drawPartyMenu(c: Canvas) {
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
        PokemonSprites.drawText(c, gs.player.speciesType.name.take(7), tx+3, ty+15, C_TEXT, fp)
        PokemonSprites.drawText(c, "BACK", tx+3, ty+38, C_TEXT, fp)
        zones.clear()
        gs.player.moves.forEachIndexed { i, _ ->
            val zy = by + i * 11
            zones.add(Zone(Rect(bx, zy, bx+bw, zy+11)) { if (!busy) playerMove(i) })
        }
        zones.add(Zone(Rect(tx, ty+32, tx+tw, ty+th)) { if (!busy) showMain() })
    }

    private fun drawItemMenu(c: Canvas) {
        when (itemPage) {
            1 -> drawBallMenu(c)
            2 -> drawMedMenu(c)
            else -> drawItemCategories(c)
        }
    }

    private fun drawItemCategories(c: Canvas) {
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

    private fun drawBallMenu(c: Canvas) {
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
    private fun drawMedMenu(c: Canvas) {
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
        if (special != null) special.appearLines(gs.enemy.name).let { (a, b) -> setText(a, b) }
        else if (gs.isEnemyShiny) setText("*SHINY* ${gs.enemy.name}", "APPEARED!")
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
    private fun showPkmn() {
        if (party.size <= 1) { setText("ONLY ${gs.player.name}", "IN PARTY!"); return }
        partyForced = false; partyMenu = true; gs.phase = BattlePhase.MAIN_MENU; invalidate()
    }

    /** Výměna Makromona: dobrovolná stojí tah, po omdlení je zdarma. */
    private fun switchTo(i: Int) {
        val m = party.getOrNull(i) ?: return
        if (i == partyIdx) { if (!partyForced) { partyMenu = false; showMain() }; return }
        if (m.mon.currentHp <= 0) { return }
        val forced = partyForced
        val old = gs.player.name
        partyMenu = false; partyForced = false
        partyIdx = i
        gs.player = m.mon
        gs.isPlayerShiny = m.shiny
        playerCond = m.cond
        loadMakromonSprite(m.mon, isPlayer = true)
        busy = true
        if (forced) say("GO ${m.mon.name}!", "") { showMain() }
        else say("COME BACK $old!", "GO ${m.mon.name}!") { enemyTurn() }
    }
    private fun startItem()   { itemPage = 0; gs.phase = BattlePhase.ITEM_MENU; zones.clear(); invalidate() }

    private fun doRun() {
        special?.let { sp -> val (a, b) = sp.noRunLines; say(a, b) { showMain() }; return }
        busy = true
        // Paralýza půlí rychlost i při útěku
        val speed = (gs.player.speed * cz.uhk.macroflow.pokemon.status.StatusRules.speedMultiplier(playerCond)).toInt()
        if (BattleEngine.tryEscape(speed, gs.enemy.speed)) {
            gs.phase = BattlePhase.ESCAPED
            setText("GOT AWAY", "SAFELY!")
        } else {
            setText("CANT ESCAPE!", "")
            scheduleAfterText { enemyTurn() }
        }
        invalidate()
    }

    // ── Tahy se stavy ────────────────────────────────────────────────────────
    //
    // Kolo: hráč → soupeř → zranění na konci kola (otrava, popálení). Každá hláška čeká na ťuknutí.

    private val rng = Random.Default

    private fun typeOf(m: Makromon) = m.speciesType

    private fun say(l1: String, l2: String, then: () -> Unit) { setText(l1, l2); scheduleAfterText(then) }

    private fun condOf(isPlayer: Boolean) = if (isPlayer) playerCond else enemyCond
    private fun monOf(isPlayer: Boolean) = if (isPlayer) gs.player else gs.enemy

    /** Zmatený Makromon zasáhne sám sebe útokem bez typu o síle 40. */
    private fun selfHitDamage(isPlayer: Boolean): Int {
        val m = monOf(isPlayer)
        val atk = (m.attack * cz.uhk.macroflow.pokemon.status.StatusRules.attackMultiplier(condOf(isPlayer))).toInt().coerceAtLeast(1)
        return BattleEngine.calcDamage(m.level, cz.uhk.macroflow.pokemon.status.StatusRules.CONFUSION_SELF_POWER, atk, m.defense,
            MakromonType.NORMAL, MakromonType.NORMAL)
    }

    private fun playerMove(idx: Int) {
        val mv = gs.player.moves[idx]
        if (mv.pp <= 0) { setText("NO PP LEFT!", ""); return }
        busy = true; gs.phase = BattlePhase.ANIMATING
        val pre = cz.uhk.macroflow.pokemon.status.StatusRules.beforeMove(playerCond, rng) { selfHitDamage(true) }
        handleBeforeMove(true, pre, act = { useMove(true, mv) }, skip = { enemyTurn() })
    }

    private fun enemyTurn() {
        if (gs.enemy.currentHp <= 0 || gs.player.currentHp <= 0) { busy = false; showMain(); return }
        busy = true
        val mv = BattleEngine.enemyChooseMove(gs.enemy, playerCond, enemyCond)
        val pre = cz.uhk.macroflow.pokemon.status.StatusRules.beforeMove(enemyCond, rng) { selfHitDamage(false) }
        handleBeforeMove(false, pre, act = { useMove(false, mv) }, skip = { endOfRound() })
    }

    /** Reakce na stav před tahem: spánek, paralýza, zmatení, omráčení. */
    private fun handleBeforeMove(isPlayer: Boolean, pre: cz.uhk.macroflow.pokemon.status.BeforeMove, act: () -> Unit, skip: () -> Unit) {
        val name = monOf(isPlayer).name
        when (pre) {
            is cz.uhk.macroflow.pokemon.status.BeforeMove.CanAct -> act()
            is cz.uhk.macroflow.pokemon.status.BeforeMove.Flinched -> say(name, "FLINCHED!", skip)
            is cz.uhk.macroflow.pokemon.status.BeforeMove.StillAsleep -> { statusFx(isPlayer, cz.uhk.macroflow.pokemon.status.EffectKind.SLEEP); say(name, "IS FAST ASLEEP.", skip) }
            is cz.uhk.macroflow.pokemon.status.BeforeMove.WokeUp -> say(name, "WOKE UP!", act)
            is cz.uhk.macroflow.pokemon.status.BeforeMove.FullyParalyzed -> { statusFx(isPlayer, cz.uhk.macroflow.pokemon.status.EffectKind.PARALYZE); say(name, "IS PARALYZED!", skip) }
            is cz.uhk.macroflow.pokemon.status.BeforeMove.SnappedOut -> say(name, "SNAPPED OUT OF IT!", act)
            is cz.uhk.macroflow.pokemon.status.BeforeMove.ConfusedButActs -> { statusFx(isPlayer, cz.uhk.macroflow.pokemon.status.EffectKind.CONFUSE); say(name, "IS CONFUSED!", act) }
            is cz.uhk.macroflow.pokemon.status.BeforeMove.HurtItself -> {
                statusFx(isPlayer, cz.uhk.macroflow.pokemon.status.EffectKind.CONFUSE)
                say(name, "IS CONFUSED!") {
                    doFlash {
                        val m = monOf(isPlayer)
                        hurt(isPlayer, pre.damage); invalidate()
                        say("IT HURT ITSELF", "IN CONFUSION!") {
                            if (m.currentHp <= 0) { if (isPlayer) playerFainted() else enemyFainted() } else skip()
                        }
                    }
                }
            }
        }
    }

    /** Provedení útoku: přesnost → zranění → vedlejší efekt (stav, snížení statistiky). */
    private fun useMove(isPlayer: Boolean, mv: Move) {
        mv.pp = maxOf(0, mv.pp - 1)
        val atk = monOf(isPlayer); val def = monOf(!isPlayer)
        val defCond = condOf(!isPlayer)
        busy = true; gs.phase = BattlePhase.ANIMATING
        setText(atk.name, "USED ${mv.name}!"); invalidate()
        handler.postDelayed({
            if (Random.nextInt(100) >= mv.accuracy) {
                say(atk.name, "MISSED!") { afterAction(isPlayer) }; return@postDelayed
            }
            when {
                mv.power > 0 -> {
                    // HEX má dvojnásobnou sílu proti cíli se stavem
                    val power = if (mv.name == "HEX" && defCond.major != null) mv.power * 2 else mv.power
                    // Stupně útoku a obrany (−6 … +6) + popálení půlí útok
                    val atkStat = (atk.attack * cz.uhk.macroflow.pokemon.status.StatStages.multiplier(condOf(isPlayer).atkStage) *
                        cz.uhk.macroflow.pokemon.status.StatusRules.attackMultiplier(condOf(isPlayer))).toInt().coerceAtLeast(1)
                    val defStat = (def.defense * cz.uhk.macroflow.pokemon.status.StatStages.multiplier(defCond.defStage)).toInt().coerceAtLeast(1)
                    val dmg = BattleEngine.calcDamage(atk.level, power, atkStat, defStat, mv.type, typeOf(def))
                    doFlash {
                        hurt(!isPlayer, dmg); invalidate()
                        handler.postDelayed({
                            if (def.currentHp <= 0) { if (isPlayer) enemyFainted() else playerFainted() }
                            else say("IT DEALT", "$dmg DAMAGE!") { applyMoveEffect(isPlayer, mv, statusOnly = false) }
                        }, 400)
                    }
                }
                else -> applyMoveEffect(isPlayer, mv, statusOnly = true)
            }
        }, 1200)
    }

    private fun applyMoveEffect(isPlayer: Boolean, mv: Move, statusOnly: Boolean) {
        val eff = mv.fullEffect
        if (eff == null) {
            if (statusOnly) say("BUT NOTHING", "HAPPENED!") { afterAction(isPlayer) } else afterAction(isPlayer)
            return
        }
        if (eff.kind.isStatChange) { applyStatChange(isPlayer, eff, statusOnly); return }
        val def = monOf(!isPlayer)
        when (cz.uhk.macroflow.pokemon.status.StatusRules.tryInflict(condOf(!isPlayer), typeOf(def), eff, rng)) {
            cz.uhk.macroflow.pokemon.status.InflictResult.APPLIED -> {
                if (eff.kind == cz.uhk.macroflow.pokemon.status.EffectKind.FLINCH) { afterAction(isPlayer); return }
                statusFx(!isPlayer, eff.kind)
                val text = when (eff.kind) {
                    cz.uhk.macroflow.pokemon.status.EffectKind.SLEEP -> "FELL ASLEEP!"
                    cz.uhk.macroflow.pokemon.status.EffectKind.PARALYZE -> "IS PARALYZED!"
                    cz.uhk.macroflow.pokemon.status.EffectKind.POISON -> "WAS POISONED!"
                    cz.uhk.macroflow.pokemon.status.EffectKind.BURN -> "WAS BURNED!"
                    else -> "BECAME CONFUSED!"
                }
                invalidate()
                say(def.name, text) { afterAction(isPlayer) }
            }
            cz.uhk.macroflow.pokemon.status.InflictResult.IMMUNE ->
                if (statusOnly) say("IT DOESNT AFFECT", def.name) { afterAction(isPlayer) } else afterAction(isPlayer)
            cz.uhk.macroflow.pokemon.status.InflictResult.ALREADY ->
                if (statusOnly) say("BUT IT", "FAILED!") { afterAction(isPlayer) } else afterAction(isPlayer)
            cz.uhk.macroflow.pokemon.status.InflictResult.FAILED_CHANCE -> afterAction(isPlayer)
        }
    }

    /**
     * Snížení / zvýšení útoku či obrany o stupně. RAISE_* míří na útočníka (HARDEN, DRAGON DANCE),
     * ostatní na soupeře. Na −6 / +6 už to nejde – u čistě stavového útoku to hráč uvidí.
     */
    private fun applyStatChange(isPlayer: Boolean, eff: cz.uhk.macroflow.pokemon.status.MoveEffect, statusOnly: Boolean) {
        val onSelf = eff.kind.targetsSelf
        val targetIsPlayer = if (onSelf) isPlayer else !isPlayer
        val target = monOf(targetIsPlayer)
        val stat = if (eff.kind == cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_ATK || eff.kind == cz.uhk.macroflow.pokemon.status.EffectKind.RAISE_ATK) "ATTACK" else "DEFENSE"
        val up = onSelf
        when (cz.uhk.macroflow.pokemon.status.StatusRules.tryInflict(condOf(targetIsPlayer), typeOf(target), eff, rng)) {
            cz.uhk.macroflow.pokemon.status.InflictResult.APPLIED -> {
                statFx(targetIsPlayer, up)
                val text = when {
                    up && eff.stages >= 2 -> "$stat SHARPLY ROSE!"
                    up -> "$stat ROSE!"
                    eff.stages >= 2 -> "$stat HARSHLY FELL!"
                    else -> "$stat FELL!"
                }
                invalidate()
                say(target.name, text.take(24)) { afterAction(isPlayer) }
            }
            else ->
                if (statusOnly) say(target.name, if (up) "$stat WONT GO HIGHER!" else "$stat WONT GO LOWER!") { afterAction(isPlayer) }
                else afterAction(isPlayer)
        }
    }

    private fun afterAction(isPlayer: Boolean) { if (isPlayer) enemyTurn() else endOfRound() }

    /** Konec kola: omráčení vyprší, otrava a popálení zraní (nejdřív hráče, pak soupeře). */
    private fun endOfRound() {
        playerCond.flinched = false; enemyCond.flinched = false
        residual(true) { residual(false) { showMain() } }
    }

    private fun residual(isPlayer: Boolean, next: () -> Unit) {
        val m = monOf(isPlayer); val c = condOf(isPlayer)
        val d = cz.uhk.macroflow.pokemon.status.StatusRules.endOfTurnDamage(c, m.maxHp)
        if (d == 0 || m.currentHp <= 0) { next(); return }
        hurt(isPlayer, d)
        statusFx(isPlayer, if (c.major == cz.uhk.macroflow.pokemon.status.StatusKind.POISON) cz.uhk.macroflow.pokemon.status.EffectKind.POISON else cz.uhk.macroflow.pokemon.status.EffectKind.BURN)
        invalidate()
        say(m.name, if (c.major == cz.uhk.macroflow.pokemon.status.StatusKind.POISON) "IS HURT BY POISON!" else "IS HURT BY ITS BURN!") {
            if (m.currentHp <= 0) { if (isPlayer) playerFainted() else enemyFainted() } else next()
        }
    }

    /** Zranění; legenda z vrcholu neklesne pod 1 HP (nedá se porazit). */
    private fun hurt(isPlayer: Boolean, dmg: Int) {
        val m = monOf(isPlayer)
        m.currentHp = maxOf(0, m.currentHp - dmg)
        if (!isPlayer && special != null) m.currentHp = special.clampEnemyHp(m.currentHp)
    }

    private fun gamePrefs() = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

    private fun playerFainted() {
        if (special?.kind == cz.uhk.macroflow.pokemon.legend.SpecialBattle.Kind.LEGEND) {
            // Legenda hráče porazí a uletí → brána na vrcholu se otevře
            gamePrefs().edit().putBoolean(cz.uhk.macroflow.pokemon.legend.LegendProgress.LEGEND_KEY, true).apply()
            val (roar, flee) = special.fleeLines(gs.enemy.name)
            say(gs.player.name, "FAINTED!") {
                say(roar.first, roar.second) {
                    gs.enemyVisible = false; invalidate()
                    gs.phase = BattlePhase.PLAYER_FAINTED
                    setText(flee.first, flee.second)
                    busy = false
                    pendingAction = { onCaught?.invoke() }
                }
            }
            return
        }
        if (party.any { it.mon.currentHp > 0 }) {
            // Další člen týmu nastoupí
            say("${gs.player.name}", "FAINTED!") {
                partyForced = true; partyMenu = true; busy = false
                gs.phase = BattlePhase.MAIN_MENU; invalidate()
            }
            return
        }
        gs.phase = BattlePhase.PLAYER_FAINTED
        setText("${gs.player.name}", "FAINTED!")
        busy = false
        pendingAction = { onCaught?.invoke() }
    }

    private fun enemyFainted() {
        busy = false
        cz.uhk.macroflow.pokemon.audio.GameAudio.sfx(context, cz.uhk.macroflow.pokemon.audio.GameAudio.Sfx.VICTORY)
        // Poražený strážce jeskyně uvolní svůj krystal
        special?.crystal?.let { c ->
            gamePrefs().edit().putBoolean(cz.uhk.macroflow.pokemon.legend.LegendProgress.bossKey(c), true).apply()
        }
        gs.enemyVisible = false
        gs.phase = BattlePhase.ENEMY_FAINTED
        setText("${gs.enemy.name}", "FAINTED!")

        // --- QUEST SYSTÉM: OZNÁMENÍ VÝHRY ---
        // Zjistíme typ nepřítele (bereme typ prvního útoku, jak to máš v dmg výpočtu)
        val enemyType = gs.enemy.speciesType
        val coins = if (special == null) cz.uhk.macroflow.pokemon.wild.BattleRewards.coinsForWin() else 0

        // Informujeme QuestManager o výhře nad konkrétním typem
        (context as? MakromonMapActivity)?.let { map ->
            map.questManager.onBattleWon(enemyType.name, biome = map.getCurrentBiome().wildBiome.name)
            cz.uhk.macroflow.pokemon.daily.DailyQuestStore.recordWin(context, enemyType.name, map.getCurrentBiome().name)
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
            if (coins > 0) db.coinDao().addCoins(coins)
            // Kořist (docs/adr/0034): fragment energie, z travních i semínko
            val drops = if (special == null) grantDrops(caught = false) else emptyList()

            handler.post {
                // Makro penízky za výhru (1–5) a kořist postupně, pak zpět na mapu
                val lines = mutableListOf<Pair<String, String>>()
                if (coins > 0) lines += "YOU GOT" to (if (coins == 1) "1 MAKRO COIN!" else "$coins MAKRO COINS!")
                lines += dropLines(drops)
                lines.forEachIndexed { i, (a, b) -> handler.postDelayed({ setText(a, b) }, 900L + i * 1500L) }
                handler.postDelayed({
                    onCaught?.invoke()
                }, if (lines.isEmpty()) 2000L else 900L + lines.size * 1500L + 400L)
            }
        }.start()
    }

    // ── Efekty stavů ─────────────────────────────────────────────────────────
    //
    // Částice žijí v souřadnicích herního plátna relativně ke středu Makromona a kreslí se
    // v rozlišení displeje. Dokud stav trvá, občas se krátce zopakují (Zzz, jiskry, bublinky…).

    private enum class FxShape { Z, SPARK, BUBBLE, FLAME, STAR, HEAL, ARROW_DOWN, ARROW_UP }
    private class Fx(
        val onPlayer: Boolean, val shape: FxShape,
        val x0: Float, val y0: Float, val vx: Float, val vy: Float,
        val born: Long, val life: Long, val size: Float, val phase: Float
    )
    private val fxList = mutableListOf<Fx>()
    private var fxLoop = false
    private var nextAmbientAt = 0L
    private val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fxPath = Path()

    private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    private fun statusFx(onPlayer: Boolean, kind: cz.uhk.macroflow.pokemon.status.EffectKind?, ambient: Boolean = false) {
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
    private fun statFx(onPlayer: Boolean, up: Boolean) {
        val t = now()
        repeat(6) { i ->
            fxList += Fx(onPlayer, if (up) FxShape.ARROW_UP else FxShape.ARROW_DOWN,
                -12f + (i % 3) * 12f, if (up) 10f else -14f, 0f, if (up) -26f else 26f,
                t + i * 90L, 650, 3.2f, 0f)
        }
        ensureFxLoop()
    }

    private val fxTick = object : Runnable {
        override fun run() {
            val t = now()
            fxList.removeAll { t - it.born > it.life }
            if (t >= nextAmbientAt && ::gs.isInitialized && gs.phase != BattlePhase.CAUGHT) {
                nextAmbientAt = t + 1700
                ambientFor(true); if (gs.enemyVisible) ambientFor(false)
            }
            invalidate()
            val anyStatus = playerCond.tag != null || enemyCond.tag != null
            if (fxList.isNotEmpty() || anyStatus) handler.postDelayed(this, 16) else fxLoop = false
        }
    }

    private fun ambientFor(onPlayer: Boolean) {
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

    private fun ensureFxLoop() { if (!fxLoop) { fxLoop = true; nextAmbientAt = now() + 1700; handler.post(fxTick) } }

    /** Střed Makromona v herních souřadnicích (hráč vlevo dole, soupeř vpravo nahoře). */
    private fun centerGb(onPlayer: Boolean): PointF =
        if (onPlayer) PointF(48f - gs.introOffset * 100f, PLAYER_FOOT_Y - PLAYER_H * 0.5f)
        else PointF(112f + gs.introOffset * 100f, 54f - enemySpriteHeight() / 2f)

    private fun drawStatusFx(canvas: Canvas) {
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

    /** Lék z lékárničky: bez účinku se nespotřebuje a tah nepropadne; jinak stojí tah. */
    private fun useMed(m: cz.uhk.macroflow.pokemon.status.MedItem) {
        if (!m.helps(playerCond)) {
            say("IT WONT HAVE", "ANY EFFECT.") { itemPage = 2; gs.phase = BattlePhase.ITEM_MENU; busy = false; invalidate() }
            return
        }
        busy = true
        medCounts[m] = ((medCounts[m] ?: 1) - 1).coerceAtLeast(0)
        Thread { db.userItemDao().consumeItem(m.id, 1) }.start()
        m.apply(playerCond)
        statusFx(true, null)
        val cure = when (m) {
            cz.uhk.macroflow.pokemon.status.MedItem.CAFFEINE -> "WOKE UP!"
            cz.uhk.macroflow.pokemon.status.MedItem.ELECTROLYTE -> "CAN MOVE AGAIN!"
            cz.uhk.macroflow.pokemon.status.MedItem.CHARCOAL -> "POISON IS GONE!"
            cz.uhk.macroflow.pokemon.status.MedItem.ALOE -> "BURN IS HEALED!"
            cz.uhk.macroflow.pokemon.status.MedItem.COLD_SHOWER -> "SNAPPED OUT OF IT!"
            cz.uhk.macroflow.pokemon.status.MedItem.MULTIVITAMIN -> "IS FULLY CURED!"
        }
        invalidate()
        say("USED ${m.short}!", "${gs.player.name} ${cure}".take(24)) { enemyTurn() }
    }

    /**
     * Vytvoří hráčova Makromona podle ID a levelu.
     * Používá nový BattleFactory s Makromony.
     */
    fun createPlayerMakromon(id: String, level: Int): Makromon =
        BattleEngine.initializeStatsForLevel(BattleFactory.createById(id), level)

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
        // Strážce ani legendu chytit nejde – ball se nespotřebuje
        special?.let { sp -> val (l1, l2) = sp.noCatchLines; say(l1, l2) { showMain() }; return }
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
        // Spící / paralyzovaný / otrávený soupeř se chytá snáz
        val finalMultiplier = baseMultiplier * ball.catchMultiplier * cz.uhk.macroflow.pokemon.status.StatusRules.catchBonus(enemyCond)
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

    // ── Dovednosti a kořist (docs/adr/0034) ──

    private fun grantDrops(caught: Boolean): List<cz.uhk.macroflow.pokemon.skills.Drops.Drop> {
        val drops = cz.uhk.macroflow.pokemon.skills.Drops.roll(gs.enemy.level, gs.enemy.speciesType == MakromonType.GRASS, caught)
        drops.forEach { runCatching { cz.uhk.macroflow.pokemon.skills.SkillStore.add(context, it.itemId, it.amount) } }
        return drops
    }

    private fun dropLabel(id: String) = when (id) {
        "energy_fragment" -> "ENERGY FRAGMENT"
        "seed_green" -> "OLIVE SEED"
        "seed_blue" -> "BLUE SEED"
        "seed_black" -> "BLACKGOLD SEED"
        else -> id.uppercase().replace('_', ' ')
    }

    private fun dropLines(drops: List<cz.uhk.macroflow.pokemon.skills.Drops.Drop>) =
        drops.map { d -> "FOUND" to ((if (d.amount > 1) "${d.amount}X " else "") + dropLabel(d.itemId) + "!") }

    private fun skillLines(r: cz.uhk.macroflow.pokemon.skills.SkillStore.XpResult?): List<Pair<String, String>> {
        if (r == null || r.gained <= 0) return emptyList()
        val name = r.skill.name
        val out = mutableListOf("$name SKILL" to "GAINED ${r.gained} XP!")
        if (r.leveledUp) out += "$name LV ${r.newLevel}!" to (if (r.newPoints > 0) "GOT A SKILL POINT!" else "")
        return out
    }

    /** Postupně ukáže hlášky (každá čeká na ťuknutí), pak [end]. */
    private fun sayChain(lines: List<Pair<String, String>>, end: () -> Unit) {
        if (lines.isEmpty()) { end(); return }
        gs.phase = BattlePhase.TEXT_WAIT
        val (a, b) = lines.first()
        setText(a, b)
        busy = false
        pendingAction = { sayChain(lines.drop(1), end) }
    }

    private fun caught() {
        gs.phase = BattlePhase.ANIMATING
        busy = true
        cz.uhk.macroflow.pokemon.audio.GameAudio.sfx(context, cz.uhk.macroflow.pokemon.audio.GameAudio.Sfx.CATCH)
        tween(650, { p -> clickStars = p }) { clickStars = -1f }

        val mId = BattleFactory.makrodexId(gs.enemy)

        // Chycený Makromon si nese level i útoky z divočiny (docs/adr/0029)
        val entity = CapturedMakromonEntity(
            makromonId = mId,
            name       = gs.enemy.name,
            level      = gs.enemy.level,
            xp         = PokemonLevelCalc.xpForLevel(gs.enemy.level),
            moveListStr = cz.uhk.macroflow.pokemon.wild.MovePool.namesOf(gs.enemy.moves),
            isShiny    = gs.isEnemyShiny,
            caughtDate = System.currentTimeMillis()
        )

        Thread {
            db.capturedMakromonDao().insertMakromon(entity)
            cz.uhk.macroflow.pokemon.daily.DailyQuestStore.recordCatch(context)
            runCatching { cz.uhk.macroflow.pokemon.skills.AwardStore.recordCatch(context, mId, gs.isEnemyShiny) }

            if (FirebaseRepository.isLoggedIn) {
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    FirebaseRepository.uploadCapturedMakromon(entity)
                    FirebaseRepository.uploadMakrodexStatus(mId)
                }
            }

            val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
            val isAcquired = prefs.getBoolean("makromonAcquired", false)

            // Chytání: XP podle levelu chyceného (shiny ×2) + kořist
            val skillXp = runCatching {
                val st = cz.uhk.macroflow.pokemon.skills.SkillStore.state(context)
                val gain = st.gain(cz.uhk.macroflow.pokemon.skills.Skill.CATCHING,
                    cz.uhk.macroflow.pokemon.skills.CatchRules.baseXp(gs.enemy.level),
                    cz.uhk.macroflow.pokemon.skills.CatchRules.multipliers(gs.isEnemyShiny))
                cz.uhk.macroflow.pokemon.skills.SkillStore.addXp(context, cz.uhk.macroflow.pokemon.skills.Skill.CATCHING, gain)
            }.getOrNull()
            val drops = grantDrops(caught = true)

            handler.post {
                val caughtLine = (if (gs.isEnemyShiny) "CAUGHT *" else "CAUGHT ") + "${gs.enemy.name.take(7)}!" to
                    (if (gs.isEnemyShiny) "SHINY!" else "")
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
                sayChain(listOf(caughtLine) + skillLines(skillXp) + dropLines(drops)) {
                    gs.phase = BattlePhase.CAUGHT
                    onCaught?.invoke()
                }
            }
        }.start()
    }

    /**
     * Udělí XP aktivnímu Makromonovi přímo z BattleView.
     * Funguje v jakémkoli Activity kontextu (MakromonMapActivity i MainActivity).
     */
    private fun awardXpToActiveMakromon(xpAmount: Int) {
        val prefs = context.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
        val activeCapturedId = party.getOrNull(partyIdx)?.capturedId?.takeIf { it > 0 }
            ?: prefs.getInt("currentOnBarCapturedId", -1)
        if (activeCapturedId == -1) return

        Thread {
            val localDb  = AppDatabase.getDatabase(context)
            val makromon = localDb.capturedMakromonDao().getMakromonById(activeCapturedId)
                ?: return@Thread

            val oldLevel = makromon.level
            val (gainedXp, gainedLevel) = PokemonLevelCalc.gain(makromon.level, makromon.xp, xpAmount)
            makromon.xp = gainedXp
            makromon.level = gainedLevel

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