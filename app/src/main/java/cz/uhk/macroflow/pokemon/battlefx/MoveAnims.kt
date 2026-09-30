package cz.uhk.macroflow.pokemon.battlefx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Animace útoků v souboji (docs/adr/0048). Čistý Kotlin, pokryto testy.
 *
 * Každý útok má [Spec] – styl pohybu, barvy, počet částic a délku. [frame] pro čas t (0..1)
 * vrátí, co se má nakreslit: částice v herních pixelech (plátno 160 × 144), posun útočníka
 * (výpad, zmizení), otřes cíle a obrazovky. View jen kreslí – žádná logika tam není.
 * Zásah (záblesk, zranění) přijde v čase [Spec.hitAt].
 */
object MoveAnims {

    enum class Style {
        CONTACT,     // výpad k soupeři a náraz (TACKLE, BODY SLAM…)
        VANISH,      // útočník zmizí a zaútočí zespodu / ze stínu (DIG, PHANTOM FORCE)
        BITE,        // čelisti se sevřou na soupeři
        CLAW,        // šikmé škrábance přes soupeře
        PROJECTILE,  // koule / kusy letí obloukem a na soupeři vybuchnou
        BEAM,        // paprsek od útočníka k soupeři
        BOLT,        // blesk shora
        WHIRL,       // vír putuje k soupeři (GUST)
        LEAVES,      // roj listů / okvětních lístků
        STORM,       // sněhová / ohnivá vánice padá na soupeře
        QUAKE,       // otřes země, prach
        VINE,        // šlahouny švihnou po soupeři
        RINGS,       // soustředné vlny (zvuk, puls, hypnóza)
        CLOUD,       // oblak prášku / plynu doplave k soupeři
        SELF_AURA,   // záře kolem útočníka (posílení)
        DRAIN,       // světélka ze soupeře do útočníka
        WISPS,       // bludičky kroužící kolem soupeře
        SHARDS,      // krystaly se sbíhají do soupeře a zamrznou
        HEARTS       // srdíčka / noty doplují k soupeři
    }

    enum class Shape { CIRCLE, SQUARE, LINE, RING, STAR, LEAF, HEART, NOTE, FLAME, TOOTH }

    data class Spec(
        val style: Style,
        val main: Int,
        val accent: Int,
        val durationMs: Int = 900,
        val count: Int = 6,
        val size: Float = 3f,
        val hitAt: Float = 0.75f,
        val shake: Float = 0f,
        val shape: Shape = Shape.CIRCLE
    )

    /** Částice v herních pixelech; u LINE je druhý konec (x2, y2). alpha 0..1. */
    data class Particle(
        val x: Float, val y: Float, val r: Float, val color: Int, val alpha: Float, val shape: Shape,
        val x2: Float = x, val y2: Float = y, val rot: Float = 0f
    )

    data class Frame(
        val particles: List<Particle>,
        val attackerDx: Float = 0f, val attackerDy: Float = 0f, val attackerAlpha: Float = 1f,
        val targetDx: Float = 0f,
        val screenDx: Float = 0f, val screenDy: Float = 0f,
        /** Zabarvení celé scény (např. tma u DARK PULSE), alpha 0..1. */
        val tint: Int = 0, val tintAlpha: Float = 0f
    )

    // ── Barvy ────────────────────────────────────────────────────────────────
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val FIRE = 0xFFFF7A1A.toInt(); private const val FIRE_L = 0xFFFFD23A.toInt()
    private const val WATER = 0xFF3A9AFF.toInt(); private const val WATER_L = 0xFFBFE6FF.toInt()
    private const val ICE = 0xFFA8ECFF.toInt(); private const val ICE_L = 0xFFFFFFFF.toInt()
    private const val GRASS = 0xFF48C23A.toInt(); private const val GRASS_L = 0xFFB6F07A.toInt()
    private const val ELEC = 0xFFFFE23A.toInt(); private const val ELEC_L = 0xFFFFFFB0.toInt()
    private const val GHOST = 0xFF7A4AC8.toInt(); private const val GHOST_L = 0xFFC8A8FF.toInt()
    private const val DARK = 0xFF2A1A3A.toInt()
    private const val POISON = 0xFFB048D0.toInt(); private const val POISON_L = 0xFFE0A0F0.toInt()
    private const val GROUND = 0xFFA87A40.toInt(); private const val GROUND_L = 0xFFE0C088.toInt()
    private const val DRAGON = 0xFF6A5AF0.toInt(); private const val DRAGON_L = 0xFFFF6A5A.toInt()
    private const val FAIRY = 0xFFFF8AC8.toInt(); private const val FAIRY_L = 0xFFFFE0F0.toInt()
    private const val PSY = 0xFFFF5AB0.toInt(); private const val PSY_L = 0xFFFFC0E8.toInt()
    private const val NORMAL = 0xFFFFFFFF.toInt(); private const val NORMAL_L = 0xFFFFE890.toInt()
    private const val FLYING = 0xFFD8F0FF.toInt(); private const val FLYING_L = 0xFFFFFFFF.toInt()

    private fun typeColors(type: String): Pair<Int, Int> = when (type) {
        "FIRE" -> FIRE to FIRE_L
        "WATER" -> WATER to WATER_L
        "GRASS", "BUG" -> GRASS to GRASS_L
        "ELECTRIC" -> ELEC to ELEC_L
        "GHOST" -> GHOST to GHOST_L
        "POISON" -> POISON to POISON_L
        "GROUND" -> GROUND to GROUND_L
        "DRAGON" -> DRAGON to DRAGON_L
        "FAIRY" -> FAIRY to FAIRY_L
        "PSYCHIC" -> PSY to PSY_L
        "FLYING" -> FLYING to FLYING_L
        else -> NORMAL to NORMAL_L
    }

    /** Animace podle jména útoku; neznámý útok dostane výchozí animaci podle typu a síly. */
    fun spec(name: String, type: String, power: Int): Spec = SPECS[name] ?: fallback(type, power)

    private fun fallback(type: String, power: Int): Spec {
        val (m, a) = typeColors(type)
        return when {
            power <= 0 -> Spec(Style.RINGS, m, a, 800, 3, 3f, 0.8f)
            type == "NORMAL" -> Spec(Style.CONTACT, m, a, 700, 6, 3f, 0.45f)
            else -> Spec(Style.PROJECTILE, m, a, 850, 1, 4f, 0.75f)
        }
    }

    private val SPECS: Map<String, Spec> = mapOf(
        // ── dotykové ──
        "TACKLE" to Spec(Style.CONTACT, NORMAL, NORMAL_L, 650, 6, 3f, 0.45f),
        "QUICK ATTACK" to Spec(Style.CONTACT, WHITE, 0xFFB0D8FF.toInt(), 500, 6, 3f, 0.4f),
        "BODY SLAM" to Spec(Style.CONTACT, NORMAL, NORMAL_L, 800, 8, 4f, 0.45f, shake = 2f),
        "SLAM" to Spec(Style.CONTACT, NORMAL, NORMAL_L, 750, 8, 4f, 0.45f, shake = 1.5f),
        "HEAVY SLAM" to Spec(Style.CONTACT, 0xFFB8B8C8.toInt(), NORMAL_L, 950, 10, 5f, 0.5f, shake = 3.5f),
        "SPIRAL SPIN" to Spec(Style.WHIRL, NORMAL, NORMAL_L, 850, 10, 2.5f, 0.8f),
        "AQUA TAIL" to Spec(Style.CONTACT, WATER, WATER_L, 800, 10, 3f, 0.45f),
        "WING ATTACK" to Spec(Style.CONTACT, FLYING, FLYING_L, 700, 8, 3f, 0.45f, shape = Shape.LEAF),
        "SHADOW PUNCH" to Spec(Style.CONTACT, GHOST, DARK, 750, 8, 4f, 0.45f),
        "PLAY ROUGH" to Spec(Style.CONTACT, FAIRY, FAIRY_L, 900, 12, 3f, 0.45f, shape = Shape.STAR),
        "WOOD HAMMER" to Spec(Style.CONTACT, 0xFF8A5A2A.toInt(), GRASS_L, 950, 10, 5f, 0.5f, shake = 3f),
        "OUTRAGE" to Spec(Style.CONTACT, DRAGON_L, DRAGON, 1000, 12, 4f, 0.5f, shake = 2.5f),
        "DIG" to Spec(Style.VANISH, GROUND, GROUND_L, 1100, 10, 3f, 0.7f, shake = 2f),
        "PHANTOM FORCE" to Spec(Style.VANISH, GHOST, GHOST_L, 1100, 10, 3f, 0.7f),
        // ── kousnutí a škrábance ──
        "BITE" to Spec(Style.BITE, WHITE, NORMAL_L, 750, 5, 3f, 0.6f, shape = Shape.TOOTH),
        "CRUNCH" to Spec(Style.BITE, WHITE, 0xFFD0D0D0.toInt(), 800, 6, 3.5f, 0.6f, shake = 1.5f, shape = Shape.TOOTH),
        "HYPER FANG" to Spec(Style.BITE, WHITE, NORMAL_L, 750, 4, 4.5f, 0.6f, shape = Shape.TOOTH),
        "FIRE FANG" to Spec(Style.BITE, FIRE_L, FIRE, 800, 5, 3.5f, 0.6f, shape = Shape.TOOTH),
        "ICE FANG" to Spec(Style.BITE, ICE, ICE_L, 800, 5, 3.5f, 0.6f, shape = Shape.TOOTH),
        "LICK" to Spec(Style.BITE, 0xFFFF8AA8.toInt(), GHOST_L, 700, 3, 3f, 0.6f),
        "SCRATCH" to Spec(Style.CLAW, WHITE, NORMAL_L, 600, 3, 1f, 0.55f),
        "SLASH" to Spec(Style.CLAW, WHITE, NORMAL_L, 650, 3, 1.5f, 0.55f),
        "FURY ATTACK" to Spec(Style.CLAW, WHITE, NORMAL_L, 850, 5, 1f, 0.55f),
        "DRAGON CLAW" to Spec(Style.CLAW, DRAGON, DRAGON_L, 700, 3, 2f, 0.55f),
        "LEAF BLADE" to Spec(Style.CLAW, GRASS, GRASS_L, 700, 2, 2.5f, 0.55f),
        // ── střely ──
        "EMBER" to Spec(Style.PROJECTILE, FIRE, FIRE_L, 800, 3, 2.5f, 0.75f, shape = Shape.FLAME),
        "FIRE BLAST" to Spec(Style.PROJECTILE, FIRE, FIRE_L, 1150, 1, 7f, 0.7f, shake = 2.5f, shape = Shape.STAR),
        "SHADOW BALL" to Spec(Style.PROJECTILE, GHOST, DARK, 900, 1, 6f, 0.75f),
        "SEED BOMB" to Spec(Style.PROJECTILE, 0xFF8A6A2A.toInt(), GRASS, 900, 4, 2.5f, 0.75f),
        "SLUDGE BOMB" to Spec(Style.PROJECTILE, POISON, POISON_L, 900, 2, 5f, 0.75f),
        "MUD-SLAP" to Spec(Style.PROJECTILE, GROUND, GROUND_L, 750, 4, 2.5f, 0.75f, shape = Shape.SQUARE),
        "MOONBLAST" to Spec(Style.PROJECTILE, FAIRY_L, FAIRY, 1000, 1, 7f, 0.75f),
        "POISON STING" to Spec(Style.PROJECTILE, POISON_L, POISON, 600, 1, 2f, 0.8f, shape = Shape.LINE),
        "ACID SPRAY" to Spec(Style.PROJECTILE, 0xFFB8F048.toInt(), POISON, 800, 6, 2f, 0.75f),
        "BELCH" to Spec(Style.PROJECTILE, 0xFF9AC838.toInt(), POISON, 1000, 1, 8f, 0.75f, shake = 1.5f),
        "ICE SHARD" to Spec(Style.PROJECTILE, ICE, ICE_L, 650, 2, 2.5f, 0.8f, shape = Shape.SQUARE),
        "CRYSTAL SHARD" to Spec(Style.PROJECTILE, 0xFFB8A0FF.toInt(), WHITE, 700, 3, 2.5f, 0.8f, shape = Shape.SQUARE),
        "WATER GUN" to Spec(Style.BEAM, WATER, WATER_L, 750, 10, 2f, 0.45f),
        // ── paprsky ──
        "BUBBLE BEAM" to Spec(Style.BEAM, WATER_L, WATER, 900, 14, 2.5f, 0.45f, shape = Shape.RING),
        "HYDRO PUMP" to Spec(Style.BEAM, WATER, WATER_L, 1000, 18, 4f, 0.4f, shake = 1.5f),
        "HYDRO CANNON" to Spec(Style.BEAM, WATER, WHITE, 1150, 22, 6f, 0.4f, shake = 3f),
        "ORIGIN PULSE" to Spec(Style.RINGS, WATER, WATER_L, 1100, 5, 4f, 0.7f, shake = 2f),
        "AURORA BEAM" to Spec(Style.BEAM, 0xFFFF7AE0.toInt(), 0xFF7AFFE0.toInt(), 950, 16, 3f, 0.45f),
        "SOLAR BEAM" to Spec(Style.BEAM, 0xFFFFF6A0.toInt(), WHITE, 1150, 20, 6f, 0.45f, shake = 2f),
        "FLAMETHROWER" to Spec(Style.BEAM, FIRE, FIRE_L, 1000, 18, 3.5f, 0.45f, shape = Shape.FLAME),
        "DRAGONBREATH" to Spec(Style.BEAM, DRAGON, DRAGON_L, 900, 14, 3f, 0.45f, shape = Shape.FLAME),
        "DRAGON PULSE" to Spec(Style.RINGS, DRAGON, DRAGON_L, 950, 5, 4f, 0.7f),
        "DARK PULSE" to Spec(Style.RINGS, DARK, GHOST, 950, 5, 4f, 0.7f),
        "PSYCHIC" to Spec(Style.RINGS, PSY, PSY_L, 1000, 6, 4f, 0.7f, shake = 1.5f),
        "WATER PULSE" to Spec(Style.RINGS, WATER, WATER_L, 900, 4, 4f, 0.7f),
        "NIGHT SHADE" to Spec(Style.RINGS, DARK, GHOST_L, 950, 4, 4f, 0.7f),
        "SPECTRAL TIDE" to Spec(Style.WHIRL, GHOST, GHOST_L, 1100, 14, 3f, 0.8f),
        "THUNDER SHOCK" to Spec(Style.BOLT, ELEC, ELEC_L, 650, 1, 1.5f, 0.35f),
        "THUNDERBOLT" to Spec(Style.BOLT, ELEC, ELEC_L, 850, 3, 2f, 0.35f, shake = 2f),
        // ── vír, listí, vánice, země ──
        "GUST" to Spec(Style.WHIRL, FLYING, FLYING_L, 1000, 10, 2.5f, 0.8f),
        "RAZOR LEAF" to Spec(Style.LEAVES, GRASS, GRASS_L, 900, 8, 3f, 0.8f, shape = Shape.LEAF),
        "PETAL DANCE" to Spec(Style.LEAVES, FAIRY, FAIRY_L, 1150, 16, 3f, 0.8f, shape = Shape.LEAF),
        "BLIZZARD" to Spec(Style.STORM, ICE, ICE_L, 1100, 26, 2f, 0.6f, shape = Shape.STAR),
        "HEAT WAVE" to Spec(Style.STORM, FIRE, FIRE_L, 1000, 22, 2.5f, 0.6f, shape = Shape.FLAME),
        "FREEZE-DRY" to Spec(Style.SHARDS, ICE, ICE_L, 950, 8, 3f, 0.8f, shape = Shape.SQUARE),
        "EARTHQUAKE" to Spec(Style.QUAKE, GROUND, GROUND_L, 1100, 14, 3f, 0.5f, shake = 4f),
        "VINE WHIP" to Spec(Style.VINE, GRASS, GRASS_L, 750, 2, 1.5f, 0.55f),
        // ── duchové a jedy ──
        "HEX" to Spec(Style.WISPS, GHOST, GHOST_L, 950, 5, 3f, 0.7f, shape = Shape.FLAME),
        "WILL-O-WISP" to Spec(Style.WISPS, 0xFF5A8AFF.toInt(), 0xFFB0D0FF.toInt(), 1000, 4, 3f, 0.8f, shape = Shape.FLAME),
        "TOXIC AURA" to Spec(Style.WISPS, POISON, POISON_L, 950, 8, 3f, 0.7f),
        "SOUL DRAIN" to Spec(Style.DRAIN, GHOST_L, GHOST, 1100, 8, 2.5f, 0.3f),
        "PAIN SPLIT" to Spec(Style.DRAIN, 0xFFFF6A6A.toInt(), GHOST_L, 1000, 6, 2.5f, 0.3f),
        "CONFUSE RAY" to Spec(Style.PROJECTILE, 0xFFFFE87A.toInt(), GHOST_L, 900, 1, 4f, 0.8f, shape = Shape.STAR),
        "TOXIC" to Spec(Style.CLOUD, POISON, POISON_L, 950, 8, 4f, 0.8f),
        "POISON GAS" to Spec(Style.CLOUD, POISON, POISON_L, 950, 10, 4f, 0.8f),
        "SLEEP POWDER" to Spec(Style.CLOUD, 0xFF7AB8FF.toInt(), WHITE, 1000, 14, 1.5f, 0.8f, shape = Shape.STAR),
        "SMOKESCREEN" to Spec(Style.CLOUD, 0xFF505058.toInt(), 0xFF8A8A92.toInt(), 950, 10, 5f, 0.8f),
        "SAND ATTACK" to Spec(Style.CLOUD, GROUND_L, GROUND, 800, 14, 1.5f, 0.8f, shape = Shape.SQUARE),
        "STRING SHOT" to Spec(Style.VINE, WHITE, 0xFFE0E0E0.toInt(), 800, 3, 1f, 0.6f),
        // ── zvuk, pohled, hypnóza ──
        "GROWL" to Spec(Style.RINGS, WHITE, NORMAL_L, 800, 3, 3f, 0.8f),
        "SNORE" to Spec(Style.RINGS, WHITE, 0xFFB0C8FF.toInt(), 850, 3, 3f, 0.7f),
        "LULLABY" to Spec(Style.HEARTS, 0xFF7AB8FF.toInt(), WHITE, 1000, 5, 3f, 0.85f, shape = Shape.NOTE),
        "HYPNOSIS" to Spec(Style.RINGS, PSY, PSY_L, 1000, 4, 3f, 0.8f),
        "LEER" to Spec(Style.SELF_AURA, 0xFFFF4A4A.toInt(), WHITE, 700, 2, 3f, 0.9f, shape = Shape.STAR),
        "TAIL WHIP" to Spec(Style.SELF_AURA, NORMAL_L, WHITE, 700, 4, 2f, 0.9f),
        "WISE WORDS" to Spec(Style.HEARTS, NORMAL_L, WHITE, 900, 4, 3f, 0.85f, shape = Shape.NOTE),
        "CHARM" to Spec(Style.HEARTS, FAIRY, FAIRY_L, 950, 5, 3f, 0.85f, shape = Shape.HEART),
        "BABY-DOLL" to Spec(Style.HEARTS, FAIRY_L, FAIRY, 950, 6, 3f, 0.85f, shape = Shape.HEART),
        "SPITE" to Spec(Style.RINGS, DARK, GHOST, 850, 3, 3f, 0.8f),
        "MEMENTO" to Spec(Style.DRAIN, DARK, GHOST_L, 1000, 8, 2.5f, 0.8f),
        // ── posílení ──
        "HARDEN" to Spec(Style.SELF_AURA, 0xFFC8C8D8.toInt(), WHITE, 800, 8, 3f, 0.9f, shape = Shape.SQUARE),
        "DRAGON DANCE" to Spec(Style.SELF_AURA, DRAGON_L, DRAGON, 1000, 12, 3f, 0.9f, shape = Shape.FLAME),
        "AQUA RING" to Spec(Style.SELF_AURA, WATER, WATER_L, 950, 10, 2.5f, 0.9f, shape = Shape.RING),
        "REGENERATE" to Spec(Style.SELF_AURA, GRASS_L, WHITE, 950, 10, 2.5f, 0.9f, shape = Shape.STAR)
    )

    val KNOWN: Set<String> get() = SPECS.keys

    // ── Průběh ───────────────────────────────────────────────────────────────

    private fun ease(t: Float) = if (t < 0.5f) 2 * t * t else 1 - (-2 * t + 2).let { it * it } / 2
    private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun fade(p: Float) = if (p < 0.15f) p / 0.15f else 1f - ((p - 0.15f) / 0.85f)
    private fun rnd(i: Int, k: Int, seed: Int): Float {
        var v = i * 374761393 + k * 668265263 + seed * 1442695041
        v = (v xor (v ushr 13)) * 1274126177
        return ((v xor (v ushr 16)) and 0xFFFF) / 65535f
    }

    /** Hvězdicový výbuch při zásahu kolem (x, y), [p] = 0..1 průběh výbuchu. */
    private fun burst(out: MutableList<Particle>, x: Float, y: Float, s: Spec, p: Float, n: Int, seed: Int, reach: Float = 14f) {
        if (p <= 0f || p >= 1f) return
        for (i in 0 until n) {
            val ang = 2 * PI.toFloat() * i / n + rnd(i, 1, seed) * 0.5f
            val d = reach * (0.3f + 0.7f * p) * (0.7f + 0.3f * rnd(i, 2, seed))
            val px = x + cos(ang) * d; val py = y + sin(ang) * d
            out += Particle(x + cos(ang) * d * 0.4f, y + sin(ang) * d * 0.4f, s.size * (1f - p * 0.6f),
                if (i % 2 == 0) s.main else s.accent, 1f - p, Shape.LINE, px, py)
        }
        out += Particle(x, y, s.size * 2.2f * (1f - p), s.accent, (1f - p) * 0.9f, Shape.STAR, rot = p * 90f)
    }

    /**
     * Snímek animace. (ax, ay) = střed útočníka, (tx, ty) = střed cíle v herních pixelech;
     * [t] = 0..1 průběh; [seed] rozhodí náhodné detaily (stejný seed = stejná animace).
     */
    fun frame(s: Spec, t: Float, ax: Float, ay: Float, tx: Float, ty: Float, seed: Int = 0): Frame {
        val out = ArrayList<Particle>()
        val dx = tx - ax; val dy = ty - ay
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val nx = -dy / len; val ny = dx / len          // kolmice k dráze
        val hitP = seg(t, s.hitAt, (s.hitAt + 0.35f).coerceAtMost(1f))
        var adx = 0f; var ady = 0f; var aAlpha = 1f; var tdx = 0f
        var sdx = 0f; var sdy = 0f; var tint = 0; var tintA = 0f

        // otřes cíle po zásahu, otřes obrazovky u silných útoků
        if (t >= s.hitAt && hitP < 1f) {
            tdx = sin(hitP * 40f) * 3f * (1f - hitP)
            if (s.shake > 0f) { sdx = sin(hitP * 55f) * s.shake * (1f - hitP); sdy = cos(hitP * 47f) * s.shake * 0.6f * (1f - hitP) }
        }

        when (s.style) {
            Style.CONTACT -> {
                val go = ease(seg(t, 0f, s.hitAt)); val back = ease(seg(t, s.hitAt, s.hitAt + 0.3f))
                val k = go * 0.62f * (1f - back)
                adx = dx * k; ady = dy * k
                // stopa za útočníkem (rychlé čáry)
                if (go in 0.05f..0.99f) for (i in 0 until 3) {
                    val o = (i + 1) * 5f
                    out += Particle(ax + adx - dx / len * o + nx * (i - 1) * 4f, ay + ady - dy / len * o + ny * (i - 1) * 4f, 1f,
                        s.accent, 0.6f * (1f - i / 3f), Shape.LINE,
                        ax + adx - dx / len * (o + 8f) + nx * (i - 1) * 4f, ay + ady - dy / len * (o + 8f) + ny * (i - 1) * 4f)
                }
                burst(out, tx, ty, s, hitP, s.count, seed)
                if (s.shape != Shape.CIRCLE) for (i in 0 until s.count) {        // hvězdičky / peří kolem cíle
                    val q = seg(t, s.hitAt, 1f)
                    if (q <= 0f || q >= 1f) continue
                    val ang = rnd(i, 3, seed) * 6.28f
                    out += Particle(tx + cos(ang) * 16f * q, ty + sin(ang) * 12f * q - 6f * q, s.size, if (i % 2 == 0) s.main else s.accent, 1f - q, s.shape, rot = q * 180f)
                }
            }
            Style.VANISH -> {
                // útočník zmizí, na cíli se zvedne prach / stín a udeří zespodu
                aAlpha = when {
                    t < 0.25f -> 1f - seg(t, 0f, 0.25f)
                    t < s.hitAt + 0.15f -> 0f
                    else -> seg(t, s.hitAt + 0.15f, 1f)
                }
                val rise = seg(t, 0.35f, s.hitAt)
                for (i in 0 until s.count) {
                    val u = rnd(i, 4, seed) * 2 - 1
                    val px = tx + u * 14f
                    val py = ty + 18f - rise * (10f + rnd(i, 5, seed) * 18f)
                    if (rise > 0f) out += Particle(px, py, s.size * (0.6f + rnd(i, 6, seed)), if (i % 2 == 0) s.main else s.accent, fade(rise) * 0.9f, Shape.SQUARE)
                }
                burst(out, tx, ty + 6f, s, hitP, s.count, seed)
            }
            Style.BITE -> {
                val close = ease(seg(t, 0.1f, s.hitAt))
                val open = 14f * (1f - close)
                val a = if (t < s.hitAt + 0.25f) 1f else 1f - seg(t, s.hitAt + 0.25f, 1f)
                for (i in 0 until s.count) {
                    val x = tx - (s.count - 1) * 3.5f + i * 7f
                    out += Particle(x, ty - 4f - open, s.size, s.main, a, Shape.TOOTH, rot = 180f)
                    out += Particle(x, ty + 4f + open, s.size, s.main, a, Shape.TOOTH, rot = 0f)
                }
                burst(out, tx, ty, s, hitP, 6, seed, 10f)
            }
            Style.CLAW -> {
                for (i in 0 until s.count) {
                    val st = 0.1f + i * 0.08f
                    val p = seg(t, st, st + 0.3f)
                    if (p <= 0f) continue
                    val off = (i - (s.count - 1) / 2f) * 6f
                    val x0 = tx - 12f + off; val y0 = ty - 12f
                    val a = if (t < s.hitAt + 0.2f) 1f else 1f - seg(t, s.hitAt + 0.2f, 1f)
                    out += Particle(x0, y0, s.size, s.main, a, Shape.LINE, x0 + 22f * p, y0 + 22f * p)
                    out += Particle(x0 + 1f, y0, s.size * 0.5f, s.accent, a * 0.8f, Shape.LINE, x0 + 1f + 20f * p, y0 + 20f * p)
                }
                burst(out, tx, ty, s, hitP, 5, seed, 9f)
            }
            Style.PROJECTILE -> {
                for (i in 0 until s.count) {
                    val st = i * 0.08f
                    val p = seg(t, st, s.hitAt)
                    if (p <= 0f || p >= 1f) continue
                    val arc = sin(p * PI.toFloat()) * (8f + i * 3f)
                    val x = lerp(ax, tx, p) + nx * (i - (s.count - 1) / 2f) * 3f
                    val y = lerp(ay, ty, p) - arc
                    val r = s.size * (0.7f + 0.3f * p)
                    if (s.shape == Shape.LINE) {
                        out += Particle(x, y, 1.5f, s.main, 1f, Shape.LINE, x - dx / len * 7f, y - dy / len * 7f)
                    } else {
                        out += Particle(x, y, r * 1.5f, s.accent, 0.35f, Shape.CIRCLE)       // záře
                        out += Particle(x, y, r, s.main, 1f, s.shape, rot = p * 360f)
                    }
                    // ohon
                    for (k in 1..3) {
                        val q = (p - k * 0.04f).coerceAtLeast(0f)
                        out += Particle(lerp(ax, tx, q), lerp(ay, ty, q) - sin(q * PI.toFloat()) * (8f + i * 3f), r * (1f - k * 0.25f),
                            s.accent, 0.5f - k * 0.12f, Shape.CIRCLE)
                    }
                }
                burst(out, tx, ty, s, hitP, 6 + s.count, seed, 10f + s.size * 1.5f)
            }
            Style.BEAM -> {
                val grow = seg(t, 0.05f, s.hitAt)
                val hold = if (t < s.hitAt + 0.3f) 1f else 1f - seg(t, s.hitAt + 0.3f, 1f)
                val ex = lerp(ax, tx, grow); val ey = lerp(ay, ty, grow)
                if (grow > 0f && hold > 0f) {
                    out += Particle(ax, ay, s.size * 1.6f, s.accent, 0.45f * hold, Shape.LINE, ex, ey)
                    out += Particle(ax, ay, s.size * 0.8f, s.main, hold, Shape.LINE, ex, ey)
                    for (i in 0 until s.count) {
                        val f = ((i / s.count.toFloat()) + t * 2.5f) % 1f
                        if (f > grow) continue
                        val w = sin((f * 9f + t * 20f + i)) * s.size
                        out += Particle(lerp(ax, tx, f) + nx * w, lerp(ay, ty, f) + ny * w, s.size * 0.6f,
                            if (i % 2 == 0) s.main else s.accent, hold * 0.9f, if (s.shape == Shape.CIRCLE) Shape.CIRCLE else s.shape, rot = f * 360f)
                    }
                }
                burst(out, tx, ty, s, hitP, 8, seed, 12f)
            }
            Style.BOLT -> {
                val on = t in 0.15f..(s.hitAt + 0.3f)
                if (on) for (b in 0 until s.count) {
                    var px = tx + (b - (s.count - 1) / 2f) * 6f; var py = -4f
                    val steps = 6
                    val flick = ((t * 30f).toInt() + b) % 3 != 0
                    for (k in 1..steps) {
                        val qx = tx + (b - (s.count - 1) / 2f) * 2f + (rnd(k + (t * 12f).toInt(), b, seed) - 0.5f) * 14f * (1f - k / steps.toFloat())
                        val qy = ty * k / steps
                        out += Particle(px, py, s.size + 1f, s.accent, if (flick) 1f else 0.5f, Shape.LINE, qx, qy)
                        out += Particle(px, py, s.size * 0.5f, WHITE, if (flick) 1f else 0.4f, Shape.LINE, qx, qy)
                        px = qx; py = qy
                    }
                }
                if (t > 0.15f && t < s.hitAt + 0.1f) { tint = s.main; tintA = 0.18f }
                burst(out, tx, ty, s, hitP, 10, seed, 14f)
            }
            Style.WHIRL -> {
                val p = ease(seg(t, 0f, s.hitAt))
                val linger = seg(t, s.hitAt, 1f)
                val cx = lerp(ax, tx, p); val cy = lerp(ay, ty, p)
                val a = if (linger > 0f) 1f - linger else 1f
                for (i in 0 until s.count) {
                    val f = i / s.count.toFloat()
                    val ang = f * 6.28f * 2 + t * 18f
                    val r = 3f + f * 11f
                    out += Particle(cx + cos(ang) * r, cy + sin(ang) * r * 0.55f - f * 10f, s.size * (1f - f * 0.4f),
                        if (i % 2 == 0) s.main else s.accent, a * (0.5f + 0.5f * (1f - f)), Shape.CIRCLE)
                }
                if (s.shape == Shape.CIRCLE) for (i in 0 until 3) {    // svislé šmouhy větru
                    val ang = t * 18f + i * 2.1f
                    out += Particle(cx + cos(ang) * 10f, cy - 8f, 1f, s.accent, a * 0.6f, Shape.LINE, cx + cos(ang + 0.6f) * 12f, cy + 6f)
                }
                burst(out, tx, ty, s, hitP, 6, seed, 10f)
            }
            Style.LEAVES -> {
                for (i in 0 until s.count) {
                    val st = rnd(i, 7, seed) * 0.25f
                    val p = seg(t, st, s.hitAt + 0.05f)
                    if (p <= 0f || p >= 1f) continue
                    val wav = sin(p * 9f + i) * 8f
                    out += Particle(lerp(ax, tx, p) + nx * wav, lerp(ay, ty, p) + ny * wav - sin(p * PI.toFloat()) * 10f,
                        s.size, if (i % 2 == 0) s.main else s.accent, 1f, Shape.LEAF, rot = p * 720f + i * 40f)
                }
                burst(out, tx, ty, s, hitP, 6, seed, 10f)
            }
            Style.STORM -> {
                for (i in 0 until s.count) {
                    val st = rnd(i, 8, seed) * 0.4f
                    val p = seg(t, st, st + 0.5f)
                    if (p <= 0f || p >= 1f) continue
                    val x0 = tx - 26f + rnd(i, 9, seed) * 52f
                    out += Particle(x0 + 16f * p, ty - 40f + 58f * p, s.size, if (i % 3 == 0) s.accent else s.main, fade(p), s.shape, rot = p * 360f)
                }
                if (t in 0.1f..0.85f) { tint = s.main; tintA = 0.12f }
                burst(out, tx, ty, s, hitP, 6, seed, 10f)
            }
            Style.QUAKE -> {
                val q = seg(t, 0.1f, 0.85f)
                if (q > 0f && q < 1f) { sdx += sin(t * 70f) * s.shake * (1f - q * 0.5f); sdy += cos(t * 61f) * s.shake * 0.5f }
                for (i in 0 until s.count) {
                    val p = seg(t, 0.15f + rnd(i, 10, seed) * 0.3f, 0.95f)
                    if (p <= 0f || p >= 1f) continue
                    val x0 = tx - 24f + rnd(i, 11, seed) * 48f
                    out += Particle(x0, ty + 16f - 16f * sin(p * PI.toFloat()) * (0.5f + rnd(i, 12, seed)), s.size,
                        if (i % 2 == 0) s.main else s.accent, fade(p), Shape.SQUARE)
                }
                // trhlina v zemi pod cílem
                val c = seg(t, 0.2f, s.hitAt)
                if (c > 0f && t < 0.95f) out += Particle(tx - 14f * c, ty + 17f, 1.5f, 0xFF3A2A1A.toInt(), 1f - seg(t, 0.8f, 1f), Shape.LINE, tx + 14f * c, ty + 19f)
            }
            Style.VINE -> {
                val reach = if (t < s.hitAt) ease(seg(t, 0f, s.hitAt)) else 1f - ease(seg(t, s.hitAt + 0.1f, 1f))
                for (v in 0 until s.count) {
                    val side = (v - (s.count - 1) / 2f) * 8f
                    var px = ax; var py = ay
                    val n = 8
                    for (k in 1..n) {
                        val f = k / n.toFloat() * reach
                        val bend = sin(f * PI.toFloat()) * (side + 10f)
                        val qx = lerp(ax, tx, f) + nx * bend; val qy = lerp(ay, ty, f) + ny * bend
                        out += Particle(px, py, s.size, if (k % 2 == 0) s.main else s.accent, 1f, Shape.LINE, qx, qy)
                        px = qx; py = qy
                    }
                }
                burst(out, tx, ty, s, hitP, 5, seed, 9f)
            }
            Style.RINGS -> {
                for (i in 0 until s.count) {
                    val p = seg(t, i * 0.12f, i * 0.12f + 0.55f)
                    if (p <= 0f || p >= 1f) continue
                    val cx = lerp(ax, tx, p); val cy = lerp(ay, ty, p)
                    out += Particle(cx, cy, 3f + p * 9f, if (i % 2 == 0) s.main else s.accent, 1f - p * 0.6f, Shape.RING)
                }
                if (s.main == DARK) { tint = DARK; tintA = 0.25f * sin(t * PI.toFloat()) }
                burst(out, tx, ty, s, hitP, 5, seed, 9f)
            }
            Style.CLOUD -> {
                for (i in 0 until s.count) {
                    val st = rnd(i, 13, seed) * 0.3f
                    val p = seg(t, st, 0.75f)
                    if (p <= 0f) continue
                    val u = rnd(i, 14, seed) * 2 - 1; val v = rnd(i, 15, seed) * 2 - 1
                    val x = lerp(ax, tx + u * 14f, ease(p)) + sin(t * 8f + i) * 2f
                    val y = lerp(ay, ty + v * 10f, ease(p)) - sin(p * PI.toFloat()) * 8f
                    val a = if (t < 0.75f) 0.85f else 0.85f * (1f - seg(t, 0.75f, 1f))
                    out += Particle(x, y, s.size * (0.6f + p * 0.8f), if (i % 3 == 0) s.accent else s.main, a, s.shape, rot = t * 200f + i * 30f)
                }
            }
            Style.SELF_AURA -> {
                for (i in 0 until s.count) {
                    val st = i / s.count.toFloat() * 0.5f
                    val p = seg(t, st, st + 0.5f)
                    if (p <= 0f || p >= 1f) continue
                    val ang = rnd(i, 16, seed) * 6.28f
                    out += Particle(ax + cos(ang) * 14f, ay + 12f - p * 30f, s.size, if (i % 2 == 0) s.main else s.accent, fade(p), s.shape, rot = p * 180f)
                }
                val glow = sin(seg(t, 0f, 0.9f) * PI.toFloat())
                out += Particle(ax, ay, 20f, s.main, glow * 0.25f, Shape.CIRCLE)
                if (s.shape == Shape.STAR && s.main == 0xFFFF4A4A.toInt()) {        // LEER: zablýsknutí očí
                    out += Particle(ax + 4f, ay - 8f, 4f * glow, WHITE, glow, Shape.STAR, rot = t * 90f)
                }
            }
            Style.DRAIN -> {
                burst(out, tx, ty, s, seg(t, 0.05f, 0.4f), 6, seed, 8f)
                for (i in 0 until s.count) {
                    val st = 0.2f + i * 0.05f
                    val p = seg(t, st, st + 0.5f)
                    if (p <= 0f || p >= 1f) continue
                    val wav = sin(p * 7f + i) * 6f
                    out += Particle(lerp(tx, ax, ease(p)) + nx * wav, lerp(ty, ay, ease(p)) + ny * wav, s.size,
                        if (i % 2 == 0) s.main else s.accent, 1f, Shape.CIRCLE)
                }
                val g = seg(t, 0.6f, 1f)
                if (g > 0f) out += Particle(ax, ay, 16f, s.main, sin(g * PI.toFloat()) * 0.35f, Shape.CIRCLE)
            }
            Style.WISPS -> {
                val appear = seg(t, 0f, 0.3f); val gone = seg(t, 0.85f, 1f)
                for (i in 0 until s.count) {
                    val ang = i * 6.28f / s.count + t * 9f
                    val r = 14f - seg(t, 0.3f, s.hitAt) * 8f
                    out += Particle(tx + cos(ang) * r, ty + sin(ang) * r * 0.6f, s.size, if (i % 2 == 0) s.main else s.accent,
                        appear * (1f - gone), s.shape)
                }
                burst(out, tx, ty, s, hitP, 6, seed, 9f)
            }
            Style.SHARDS -> {
                for (i in 0 until s.count) {
                    val ang = i * 6.28f / s.count
                    val p = ease(seg(t, 0.05f, s.hitAt))
                    val r = 28f * (1f - p) + 5f
                    val a = if (t < s.hitAt + 0.25f) 1f else 1f - seg(t, s.hitAt + 0.25f, 1f)
                    out += Particle(tx + cos(ang) * r, ty + sin(ang) * r * 0.7f, s.size, if (i % 2 == 0) s.main else s.accent, a, s.shape, rot = 45f)
                }
                if (t > s.hitAt) { tint = s.main; tintA = 0.15f * (1f - hitP) }
                burst(out, tx, ty, s, hitP, 8, seed, 12f)
            }
            Style.HEARTS -> {
                for (i in 0 until s.count) {
                    val st = i * 0.1f
                    val p = seg(t, st, st + 0.6f)
                    if (p <= 0f || p >= 1f) continue
                    val wav = sin(p * 8f + i) * 5f
                    out += Particle(lerp(ax, tx, p) + nx * wav, lerp(ay, ty, p) - sin(p * PI.toFloat()) * 12f, s.size * (0.8f + 0.4f * sin(t * 20f + i)),
                        if (i % 2 == 0) s.main else s.accent, fade(p), s.shape)
                }
            }
        }
        val visible = out.mapNotNull { p ->
            if (p.alpha <= 0.001f) null else p.copy(alpha = p.alpha.coerceAtMost(1f), r = p.r.coerceAtLeast(0f))
        }
        return Frame(visible, adx, ady, aAlpha.coerceIn(0f, 1f), tdx, sdx, sdy, tint, tintA.coerceIn(0f, 1f))
    }

    /** Pomocné pro testy: je průběh v rozumných mezích? */
    fun inBounds(p: Particle, pad: Float = 80f): Boolean =
        p.x in -pad..(160f + pad) && p.y in -pad..(144f + pad) && abs(p.alpha) <= 1.001f
}
