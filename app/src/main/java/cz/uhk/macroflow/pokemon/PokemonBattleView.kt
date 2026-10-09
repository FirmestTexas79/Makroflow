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
    internal val enemyShiny: Boolean = false,
    /** Strážce jeskyně nebo legenda z vrcholu (docs/adr/0014); null = divoké setkání. */
    internal val special: cz.uhk.macroflow.pokemon.legend.SpecialBattle? = null,
    /** Souboj s trenérem v aréně – duch hráče nebo AI (docs/adr/0076); null = jiný souboj. */
    internal val trainer: cz.uhk.macroflow.pokemon.trainer.Trainer? = null
) : View(context, attrs) {

    var onCaught: (() -> Unit)? = null

    internal val GBW = 160
    internal val GBH = 144

    internal val C_BG     = 0xFFF8F8F8.toInt()
    // HUD nad 3D arénou: průsvitný tmavý panel, bílé písmo se stínem (docs/adr/0038)
    internal val C_HUD_BG   = 0x9E0C1420.toInt()
    internal val C_HUD_LINE = 0xFFF0F0F0.toInt()
    internal val C_HUD_TEXT = 0xFFF8F8F8.toInt()
    internal val C_HUD_SHADOW = 0xFF101418.toInt()

    /** Hráčův Makromon: stojí blíž kameře, nohy má pod dolním okrajem scény. */
    internal val PLAYER_FOOT_Y = cz.uhk.macroflow.pokemon.arena.Arenas.PLAYER_Y / 3f
    internal val PLAYER_H = 46f

    // ── Voxelová aréna podle lokace ──
    internal var arenaBmp: Bitmap? = null
    internal var arenaTheme = cz.uhk.macroflow.pokemon.arena.ArenaTheme.MEADOW
    internal val arenaDst = RectF()
    internal val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x5A000000 }
    internal val C_UI_BG  = 0xFFF8F8F8.toInt()
    internal val C_BORDER = 0xFF181818.toInt()
    internal val C_TEXT   = 0xFF181818.toInt()
    internal val C_HP_G   = 0xFF00B000.toInt()
    internal val C_HP_Y   = 0xFFB8B000.toInt()
    internal val C_HP_R   = 0xFFB80000.toInt()
    internal val C_HP_BG  = 0xFF282828.toInt()

    internal val gbBmp = Bitmap.createBitmap(GBW, GBH, Bitmap.Config.ARGB_8888)
    internal val gbCvs = Canvas(gbBmp)
    internal val fp    = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    internal val sp    = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    internal val spSmooth = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
    internal val srcR  = Rect(0, 0, GBW, GBH)
    internal var dstR  = RectF()

    internal lateinit var gs: BattleState
    internal val handler = Handler(Looper.getMainLooper())
    // ── Makroball: poloha a stav animace (souřadnice herního plátna 160 × 144) ──
    internal var ball: cz.uhk.macroflow.pokemon.balls.Makroball = cz.uhk.macroflow.pokemon.balls.Makroball.MAKRO
    internal var ballShown = false
    internal var ballCx = 0f; private var ballCy = 0f
    internal var ballRot = 0f; private var ballOpen = 0f
    /** 0 = Makromon normálně, 1 = celý vtažený do ballu (světlo). */
    internal var enemyAbsorb = 0f
    internal var captureBeam = 0f
    internal var clickStars = -1f
    internal val ballCounts = HashMap<cz.uhk.macroflow.pokemon.balls.Makroball, Int>()
    internal val medCounts = HashMap<cz.uhk.macroflow.pokemon.status.MedItem, Int>()
    /** Menu předmětů: 0 = výběr kategorie, 1 = Makrobally, 2 = lékárnička. */
    internal var itemPage = 0

    // ── Stavy (spánek, paralýza…) – platí jen po dobu souboje ──
    internal var playerCond = cz.uhk.macroflow.pokemon.status.Condition()

    // ── Tým (docs/adr/0034): až 6 Makromonů, v souboji se dají střídat ──
    internal class PartyMember(
        val capturedId: Int,
        val mon: Makromon,
        val shiny: Boolean,
        val cond: cz.uhk.macroflow.pokemon.status.Condition = cz.uhk.macroflow.pokemon.status.Condition()
    )
    internal val party = mutableListOf<PartyMember>()
    internal var partyIdx = 0
    /** Menu týmu je vynucené (bojující Makromon omdlel) – nejde zavřít. */
    internal var partyForced = false
    internal var partyMenu = false
    /** Pasivní bonus Chytání (snižuje šanci na útěk po vyskočení z ballu). */
    internal var catchingPassive = 0.0
    internal var enemyCond = cz.uhk.macroflow.pokemon.status.Condition()
    /** Tým trenéra (Makromon, shiny) a kdo z něj právě bojuje. */
    internal val enemyTeam = mutableListOf<Pair<Makromon, Boolean>>()
    internal var enemyIdx = 0

    internal val absorbPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
    internal val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal var flashOn = false; private var cursorOn = true
    internal var busy = false
    internal var pendingAction: (() -> Unit)? = null

    internal var enemyBitmap: Bitmap? = null
    internal var playerBitmap: Bitmap? = null
    internal var introStarted = false

    internal val db = AppDatabase.getDatabase(context)

    internal val cursorTick = object : Runnable {
        override fun run() { cursorOn = !cursorOn; invalidate(); handler.postDelayed(this, 480) }
    }

    internal data class Zone(val r: Rect, val fn: () -> Unit)
    internal val zones = mutableListOf<Zone>()

    init {
        PokemonSprites.init(context)

        val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val activeCapturedId = prefs.getInt("currentOnBarCapturedId", -1)
        val backupMakromonId = prefs.getString("currentOnBarId", "012") ?: "012"

        // --- 🌍 NAČTENÍ AKTUÁLNÍHO BIOMU ---
        val biomeStr = prefs.getString("LAST_BIOME", BiomeType.TOWN.name)
        val currentBiome = BiomeType.valueOf(biomeStr!!)
        if (trainer != null) startArena(cz.uhk.macroflow.pokemon.arena.ArenaTheme.COLOSSEUM) else startArena(currentBiome)

        Thread {
            val db = AppDatabase.getDatabase(context)

            val caughtEntity = if (activeCapturedId != -1) {
                db.capturedMakromonDao().getMakromonById(activeCapturedId)
            } else null
            catchingPassive = runCatching {
                cz.uhk.macroflow.pokemon.skills.SkillStore.state(context).catchReduction
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

            // Trenér: celý tým najednou, statistiky z druhu a levelu (docs/adr/0076)
            val trainerTeam = trainer?.team?.map { cz.uhk.macroflow.pokemon.trainer.Trainers.toBattle(it) to it.shiny }.orEmpty()

            // --- 🎲 OPRAVENÝ ROLL S BIOMEM ---
            val enemyWithStats = if (trainerTeam.isNotEmpty()) {
                trainerTeam[0].first
            } else if (special != null) {
                // Strážce / legenda: pevný Makromon s pevným levelem
                createPlayerMakromon(special.statsId, special.level)
                    .let { m -> special.displayName?.let { m.copy(name = it) } ?: m }
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
            val enemyIsShiny = if (trainerTeam.isNotEmpty()) trainerTeam[0].second else enemyShiny

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
                enemyTeam.clear(); enemyTeam += trainerTeam; enemyIdx = 0
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
                if (enemyIsShiny && trainer == null) {
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

    internal var arenaSeed = 0
    /** Aréna je dopočítaná (intro na ni čeká, docs/adr/0048). */
    var arenaReady = false
        internal set
    var onArenaReady: (() -> Unit)? = null
    /** Kdy se aréna dopočítala – 250 ms se prolíná přes náhradní barvy. */
    internal var arenaShownAt = 0L
    internal val arenaFadePaint = Paint().apply { isFilterBitmap = false }
    internal var arenaExtra = -1

    /** Pod novou arénou se během prolínání kreslí ta předchozí (náhled). */
    internal var arenaPrevBmp: Bitmap? = null

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

    internal val scale get() = if (dstR.width() > 0) dstR.width() / GBW.toFloat() else 1f
    override fun onDraw(canvas: Canvas) {
        if (!::gs.isInitialized) { canvas.drawColor(C_BG); return }
        renderFrame()
        // aréna pod herním plátnem (horních 96 řádků), plátno má nahoře průhledno
        arenaDst.set(dstR.left, 0f, dstR.right, gbY(96f))
        val mf = moveFrame
        val shaking = mf != null && (mf.screenDx != 0f || mf.screenDy != 0f)
        if (shaking) { canvas.save(); canvas.translate(mf!!.screenDx * scale, mf.screenDy * scale) }
        val arena = arenaBmp
        val fadeIn = if (arena == null) 0f else ((now() - arenaShownAt) / 250f).coerceIn(0f, 1f)
        val prev = arenaPrevBmp
        if (fadeIn < 1f && prev != null) canvas.drawBitmap(prev, null, arenaDst, sp)
        else if (fadeIn < 1f) {
            val (sky, ground) = arenaFallback()
            fp.color = sky; canvas.drawRect(arenaDst.left, arenaDst.top, arenaDst.right, gbY(40f), fp)
            // (dopočítává se na pozadí během intra)
            fp.color = ground; canvas.drawRect(arenaDst.left, gbY(40f), arenaDst.right, arenaDst.bottom, fp)
        }
        if (arena != null) {
            arenaFadePaint.alpha = (fadeIn * 255).toInt()
            canvas.drawBitmap(arena, null, arenaDst, if (fadeIn >= 1f) sp else arenaFadePaint)
            if (fadeIn < 1f) postInvalidateOnAnimation() else if (prev != null) arenaPrevBmp = null
        }
        canvas.save()
        canvas.clipRect(arenaDst)
        drawSpritesOverlay(canvas)
        mf?.let { f -> if (f.tintAlpha > 0f) { fp.color = f.tint; fp.alpha = (f.tintAlpha * 255).toInt(); canvas.drawRect(arenaDst, fp); fp.alpha = 255 } }
        drawMoveParticles(canvas)
        canvas.restore()
        if (shaking) canvas.restore()
        canvas.drawBitmap(gbBmp, srcR, dstR, sp)
        // záblesk útoku přes celou arénu i herní plátno
        if (flashOn) canvas.drawColor(if (moveAnim != null) 0x66FFFFFF else 0xBBFFFFFF.toInt())
    }

    internal data class Sparkle(
        val x0: Float, val y0: Float,       // start (GB px od středu soupeře)
        val vx: Float, val vy: Float,       // rychlost (GB px / s)
        val born: Long, val life: Long,
        val size: Float,                    // poloměr cípu (GB px)
        val spin: Float,                    // otočení (° / s)
        val color: Int
    )

    internal val sparkles = mutableListOf<Sparkle>()
    internal var sparkleLoop = false
    internal var nextTwinkleAt = 0L
    internal var glowUntil = 0L
    internal val starPath = Path()
    internal val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    internal val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val gold = 0xFFFFD54F.toInt()
    internal val paleGold = 0xFFFFF3C4.toInt()

    internal val sparkleTick = object : Runnable {
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

    internal val rng = Random.Default

    /** Souboj s trenérem doběhl do konce (výsledek zapsaný). */
    var trainerFinished = false
        internal set

    internal enum class FxShape { Z, SPARK, BUBBLE, FLAME, STAR, HEAL, ARROW_DOWN, ARROW_UP }
    internal class Fx(
        val onPlayer: Boolean, val shape: FxShape,
        val x0: Float, val y0: Float, val vx: Float, val vy: Float,
        val born: Long, val life: Long, val size: Float, val phase: Float
    )
    internal val fxList = mutableListOf<Fx>()
    internal var fxLoop = false
    internal var nextAmbientAt = 0L
    internal val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    internal val fxPath = Path()

    internal val fxTick = object : Runnable {
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

    /**
     * Vytvoří hráčova Makromona podle ID a levelu.
     * Používá nový BattleFactory s Makromony.
     */
    fun createPlayerMakromon(id: String, level: Int): Makromon =
        BattleEngine.initializeStatsForLevel(BattleFactory.createById(id), level)

    internal class MoveAnim(val spec: cz.uhk.macroflow.pokemon.battlefx.MoveAnims.Spec, val attackerIsPlayer: Boolean,
                           val start: Long, val seed: Int, val onHit: () -> Unit) {
        var hitFired = false
    }
    internal var moveAnim: MoveAnim? = null
    internal var moveFrame: cz.uhk.macroflow.pokemon.battlefx.MoveAnims.Frame? = null
    /** Cíl po zásahu krátce bliká (do tohoto času). */
    internal var hitBlinkUntil = 0L
    internal var hitBlinkOnPlayer = false
    internal val moveFxPaint = Paint().apply { isAntiAlias = true }
    internal val moveFxPath = android.graphics.Path()

    internal val moveTick = object : Runnable {
        override fun run() {
            val a = moveAnim ?: return
            val t = ((now() - a.start) / a.spec.durationMs.toFloat()).coerceIn(0f, 1f)
            if (!::gs.isInitialized) return
            val from = centerGb(a.attackerIsPlayer); val to = centerGb(!a.attackerIsPlayer)
            moveFrame = cz.uhk.macroflow.pokemon.battlefx.MoveAnims.frame(a.spec, t, from.x, from.y, to.x, to.y, a.seed)
            if (!a.hitFired && t >= a.spec.hitAt) {
                a.hitFired = true
                // záblesk při zásahu a cíl chvíli bliká
                flashOn = true
                hitBlinkUntil = now() + 420; hitBlinkOnPlayer = !a.attackerIsPlayer
                handler.postDelayed({ flashOn = false; invalidate() }, 90)
                a.onHit()
            }
            invalidate()
            if (t < 1f) handler.postDelayed(this, 16) else { moveAnim = null; moveFrame = null; invalidate() }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
    }
}