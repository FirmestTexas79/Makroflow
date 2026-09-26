package cz.uhk.macroflow.pokemon.skills

import cz.uhk.macroflow.pokemon.balls.Makroball
import kotlin.math.hypot

/**
 * Ocenění Makrosvěta (docs/adr/0037): speciální úspěchy v deníku, každé má svůj obrázek.
 * Čistý Kotlin – definice, výpočet postupu i pixel art; pokryto testy.
 */
enum class AwardCategory(val label: String) {
    GENERAL("Všeobecné"), CATCHING("Chytání"), CRAFTING("Výroba"), HARVESTING("Pěstování"),
    MINING("Těžba"), LOGGING("Kácení")
}

/** Obtížnost = barva medaile. */
enum class AwardTier(val label: String) { BRONZE("Bronzové"), SILVER("Stříbrné"), GOLD("Zlaté"), PLATINUM("Platinové") }

/** Co hra ví o hráči – sbírá AwardStore, vyhodnocuje [Awards]. */
data class AwardFacts(
    val questTasks: Int = 0,
    val dailyClaimed: Int = 0,
    val skillLevels: Map<Skill, Int> = emptyMap(),
    val visitedForest: Boolean = false,
    val caught: Int = 0,
    val shinyCaught: Int = 0,
    val legendCaught: Int = 0,
    val speciesCaught: Int = 0,
    val speciesTotal: Int = 1,
    val teamSize: Int = 0,
    val crafted: Int = 0,
    val equipsFilled: Int = 0,
    val accessFilled: Int = 0,
    val harvested: Int = 0,
    val harvestedBlack: Int = 0,
    val plotsOpen: Int = 2,
    /** Celkem vytěžené / pokácené kusy podle suroviny (itemId). */
    val gathered: Map<String, Int> = emptyMap()
) {
    val totalLevel: Int get() = Skill.entries.sumOf { skillLevels[it] ?: 1 }
    fun level(s: Skill) = skillLevels[s] ?: 1
    fun got(r: Resource) = gathered[r.itemId] ?: 0
}

/** Symbol v medaili. */
enum class AwardSymbol { BALL, SHINY, CROWN, DEX, TEAM, SCROLL, TROPHY, FOREST, STAR, SEED, BERRY_GREEN, BERRY_BLACK, PLOT, SHIRT, RING, ENERGY,
    ORE_COPPER, ORE_SILVER, ORE_GOLD, LOG_OAK, LOG_BIRCH, LOG_MAPLE, BALL_KREATIN }

data class Award(
    val id: String,
    val category: AwardCategory,
    val tier: AwardTier,
    val title: String,
    val description: String,
    val target: Int,
    val symbol: AwardSymbol,
    val value: (AwardFacts) -> Int
) {
    /** user_items „award_<id>“ = den splnění (dny od 1970), 0 = zatím ne. */
    val itemId: String get() = "award_$id"
}

object Awards {
    private fun a(id: String, c: AwardCategory, t: AwardTier, title: String, desc: String, target: Int, s: AwardSymbol, v: (AwardFacts) -> Int) =
        Award(id, c, t, title, desc, target, s, v)

    private val G = AwardCategory.GENERAL
    private val C = AwardCategory.CATCHING
    private val CR = AwardCategory.CRAFTING
    private val H = AwardCategory.HARVESTING
    private val M = AwardCategory.MINING
    private val L = AwardCategory.LOGGING
    private val B = AwardTier.BRONZE
    private val S = AwardTier.SILVER
    private val Gd = AwardTier.GOLD
    private val P = AwardTier.PLATINUM

    val ALL: List<Award> = listOf(
        // ── Všeobecné ──
        a("tasks_10", G, B, "Pomocník", "Splň 10 fází příběhových úkolů.", 10, AwardSymbol.SCROLL) { it.questTasks },
        a("tasks_25", G, S, "Hrdina Makrosvěta", "Splň 25 fází příběhových úkolů.", 25, AwardSymbol.SCROLL) { it.questTasks },
        a("daily_10", G, B, "Každý den kousek", "Vyzvedni odměnu za 10 denních úkolů.", 10, AwardSymbol.TROPHY) { it.dailyClaimed },
        a("daily_100", G, Gd, "Neúnavný", "Vyzvedni odměnu za 100 denních úkolů.", 100, AwardSymbol.TROPHY) { it.dailyClaimed },
        a("forest", G, B, "Do Hvozdu", "Najdi cestu do Hvozdu nad loukou.", 1, AwardSymbol.FOREST) { if (it.visitedForest) 1 else 0 },
        a("level_25", G, S, "Všeuměl", "Dosáhni celkového levelu 25 ve všech dovednostech.", 25, AwardSymbol.STAR) { it.totalLevel },
        a("level_100", G, P, "Mistr Makrosvěta", "Dosáhni celkového levelu 100.", 100, AwardSymbol.STAR) { it.totalLevel },

        // ── Chytání ──
        a("catch_1", C, B, "První úlovek", "Chyť svého prvního Makromona.", 1, AwardSymbol.BALL) { it.caught },
        a("catch_50", C, S, "Sběratel", "Chyť 50 Makromonů.", 50, AwardSymbol.BALL) { it.caught },
        a("catch_500", C, Gd, "Lovec legend", "Chyť 500 Makromonů.", 500, AwardSymbol.BALL_KREATIN) { it.caught },
        a("species_all", C, P, "Celý Makrodex", "Chyť každý druh Makromona.", -1, AwardSymbol.DEX) { it.speciesCaught },
        a("legend_1", C, Gd, "Legenda v ballu", "Chyť svého prvního legendárního Makromona.", 1, AwardSymbol.CROWN) { it.legendCaught },
        a("shiny_1", C, S, "Třpytka", "Chyť svého prvního shiny Makromona.", 1, AwardSymbol.SHINY) { it.shinyCaught },
        a("shiny_100", C, P, "Shiny lovec", "Chyť 100 shiny Makromonů.", 100, AwardSymbol.SHINY) { it.shinyCaught },
        a("team_6", C, Gd, "Plná parta", "Měj v týmu šest Makromonů.", 6, AwardSymbol.TEAM) { it.teamSize },

        // ── Výroba ──
        a("craft_10", CR, B, "Učeň", "Vyrob 10 Makroballů.", 10, AwardSymbol.BALL) { it.crafted },
        a("craft_100", CR, S, "Tovaryš", "Vyrob 100 Makroballů.", 100, AwardSymbol.ENERGY) { it.crafted },
        a("outfit", CR, Gd, "Oblečený do posledního švu", "Měj nasazený celý set oblečení (přilba, hrudní plát, kalhoty, boty).", 4, AwardSymbol.SHIRT) { it.equipsFilled },
        a("accessories", CR, Gd, "Třpytivý", "Měj plné všechny sloty doplňků (talisman, přívěsek, oba prsteny).", 4, AwardSymbol.RING) { it.accessFilled },

        // ── Pěstování ──
        a("harvest_10", H, B, "Zahrádkář", "Sklízej na záhonech – nasbírej 10 bobulí.", 10, AwardSymbol.BERRY_GREEN) { it.harvested },
        a("harvest_250", H, S, "Sadař", "Nasbírej na záhonech 250 bobulí.", 250, AwardSymbol.BERRY_GREEN) { it.harvested },
        a("harvest_black", H, Gd, "Černé zlato", "Sklidíš 10 černozlatých bobulí.", 10, AwardSymbol.BERRY_BLACK) { it.harvestedBlack },
        a("plots_4", H, S, "Opravář", "Odemkni ve stromu Pěstování oba zbývající záhony.", 4, AwardSymbol.PLOT) { it.plotsOpen },

        // ── Těžba ──
        a("copper_100", M, B, "Měďák", "Vytěž 100 kusů měděné rudy.", 100, AwardSymbol.ORE_COPPER) { it.got(Resource.ORE_COPPER) },
        a("silver_50", M, S, "Stříbrná žíla", "Vytěž 50 kusů stříbrné rudy.", 50, AwardSymbol.ORE_SILVER) { it.got(Resource.ORE_SILVER) },
        a("gold_25", M, Gd, "Zlatokop", "Vytěž 25 kusů zlaté rudy.", 25, AwardSymbol.ORE_GOLD) { it.got(Resource.ORE_GOLD) },
        a("mining_20", M, P, "Mistr horník", "Dosáhni levelu 20 v Těžbě.", 20, AwardSymbol.STAR) { it.level(Skill.MINING) },

        // ── Kácení ──
        a("oak_100", L, B, "Dubový", "Pokácej 100 dubových polen.", 100, AwardSymbol.LOG_OAK) { it.got(Resource.LOG_OAK) },
        a("birch_50", L, S, "Březí", "Pokácej 50 březových polen.", 50, AwardSymbol.LOG_BIRCH) { it.got(Resource.LOG_BIRCH) },
        a("maple_25", L, Gd, "Javorník", "Pokácej 25 javorových polen.", 25, AwardSymbol.LOG_MAPLE) { it.got(Resource.LOG_MAPLE) },
        a("logging_20", L, P, "Mistr dřevorubec", "Dosáhni levelu 20 v Kácení.", 20, AwardSymbol.STAR) { it.level(Skill.LOGGING) }
    )

    /** Cíl – u „chyť každý druh“ podle počtu druhů ve hře. */
    fun target(a: Award, f: AwardFacts): Int = if (a.target < 0) f.speciesTotal.coerceAtLeast(1) else a.target

    fun value(a: Award, f: AwardFacts): Int = a.value(f).coerceAtLeast(0)

    fun isDone(a: Award, f: AwardFacts): Boolean = value(a, f) >= target(a, f)

    fun fraction(a: Award, f: AwardFacts): Float = (value(a, f).toFloat() / target(a, f)).coerceIn(0f, 1f)

    fun byCategory(c: AwardCategory) = ALL.filter { it.category == c }

    fun find(id: String) = ALL.firstOrNull { it.id == id }
}

/** Pixel art medailí: kruh v barvě obtížnosti a symbol uvnitř (16 × 16). */
object AwardArt {
    const val SIZE = 16
    const val FRAME = 24

    private fun c(hex: Long) = hex.toInt()

    /** (tmavý obrys, základ, světlo) medaile podle obtížnosti. */
    fun tierColors(t: AwardTier): Triple<Int, Int, Int> = when (t) {
        AwardTier.BRONZE -> Triple(c(0xFF5A2E12), c(0xFFB8733A), c(0xFFE7A76B))
        AwardTier.SILVER -> Triple(c(0xFF3C4450), c(0xFFA9B4C2), c(0xFFE6EDF4))
        AwardTier.GOLD -> Triple(c(0xFF6A4A08), c(0xFFE0A91E), c(0xFFFFE27A))
        AwardTier.PLATINUM -> Triple(c(0xFF1E3C4E), c(0xFF6CC7D8), c(0xFFD8FBFF))
    }

    private val GLYPHS: Map<AwardSymbol, List<String>> = mapOf(
        AwardSymbol.STAR to listOf(
            ".....KK.....", ".....YK.....", "....KYYK....", "KKKKYYYYKKKK", "KYYYYYYYYYYK", ".KYYYYYYYYK.",
            "..KYYYYYYK..", "..KYYYYYYK..", ".KYYYKKYYYK.", ".KYYK..KYYK.", "KYKK....KKYK", "KK........KK"),
        AwardSymbol.SHINY to listOf(
            "......K.....", ".....KWK....", "..K..KWK..K.", ".KWK.KWK.KWK", "..KKKWWWKKK.", "KKWWWWWWWWKK",
            "..KKKWWWKKK.", ".....KWK....", ".....KWK..K.", "..K..KWK.KWK", ".KWK..K...K.", "..K.........."),
        AwardSymbol.CROWN to listOf(
            "............", "K....K....K.", "KK..KYK..KK.", "KYK.KYK.KYK.", "KYYKYYYKYYK.", "KYYYYYYYYYK.",
            "KYRYYBYYRYK.", "KYYYYYYYYYK.", "KKKKKKKKKKK.", "KYYYYYYYYYK.", "KKKKKKKKKKK.", "............"),
        AwardSymbol.DEX to listOf(
            "............", ".KKKKKKKKKK.", ".KRRRRRRRRK.", ".KRRRRRRRRK.", ".KRRKKKKRRK.", ".KKKKWWKKKK.",
            ".KKKKWWKKKK.", ".KWWKKKKWWK.", ".KWWWWWWWWK.", ".KWWWWWWWWK.", ".KKKKKKKKKK.", "............"),
        AwardSymbol.TEAM to listOf(
            "............", "..KK....KK..", ".KWWK..KWWK.", ".KWWK..KWWK.", "..KK.KK.KK..", ".KGGKWWKGGK.",
            "KGGGKWWKGGGK", "KGGKGGGGKGGK", "KGGKGGGGKGGK", ".KKKGGGGKKK.", "....KKKK....", "............"),
        AwardSymbol.SCROLL to listOf(
            "............", "..KKKKKKKK..", ".KWWWWWWWWK.", ".KWKKKKKKWK.", "..KWWWWWWK..", "..KWKKKKWK..",
            "..KWWWWWWK..", "..KWKKKKWK..", "..KWWWWWWK..", ".KWWWWWWWWK.", "..KKKKKKKK..", "............"),
        AwardSymbol.TROPHY to listOf(
            "............", ".KKKKKKKKKK.", "KYKYYYYYYKYK", "KYKYYYYYYKYK", ".KKYYYYYYKK.", "..KYYYYYYK..",
            "...KYYYYK...", "....KYYK....", ".....KK.....", "...KKYYKK...", "..KYYYYYYK..", "..KKKKKKKK.."),
        AwardSymbol.FOREST to listOf(
            ".....KK.....", "....KGGK....", "...KGGGGK...", "..KGGLGGGK..", "..KKGGGGKK..", ".KGGGGLGGGK.",
            "KGGLGGGGGGGK", "KKKKGGGGKKKK", "...KGGGGK...", "KKKKKWWKKKKK", ".....WW.....", ".....KK....."),
        AwardSymbol.PLOT to listOf(
            "............", ".....GG.....", "....GLLG....", ".....GG.....", ".KKKKKKKKKK.", "KWWWWWWWWWWK",
            "KDDDDDDDDDDK", "KWWWWWWWWWWK", "KDDDDDDDDDDK", "KWWWWWWWWWWK", ".KKKKKKKKKK.", "............"),
        AwardSymbol.SHIRT to listOf(
            "............", "..KKK..KKK..", ".KBBBKKBBBK.", "KBBBBBBBBBBK", "KBBKBBBBKBBK", "KKK.KBBK.KKK",
            "....KBBK....", "...KBBBBK...", "...KBBBBK...", "...KBBBBK...", "...KKKKKK...", "............"),
        AwardSymbol.RING to listOf(
            "....KKKK....", "...KRRRRK...", "....KRRK....", "...KKKKKK...", "..KYYYYYYK..", ".KYK....KYK.",
            ".KYK....KYK.", ".KYK....KYK.", ".KYK....KYK.", "..KYYYYYYK..", "...KKKKKK...", "............")
    )

    private val GLYPH_COLORS = mapOf(
        'K' to c(0xFF1E140C), 'Y' to c(0xFFFFD54F), 'W' to c(0xFFFDFBF3), 'R' to c(0xFFD84838), 'B' to c(0xFF2E86DE),
        'G' to c(0xFF5F8A2A), 'L' to c(0xFF9CC45A), 'D' to c(0xFF6B4423)
    )

    /** Symbol 12 × 12: hotové ikony surovin a ballů, jinak vlastní glyfy. */
    fun symbol(s: AwardSymbol): IntArray = when (s) {
        AwardSymbol.BALL -> Makroball.MAKRO.pixels
        AwardSymbol.BALL_KREATIN -> Makroball.KREATIN.pixels
        AwardSymbol.ENERGY -> SkillArt.energyFragment()
        AwardSymbol.SEED -> SkillArt.seed(Berry.GREEN)
        AwardSymbol.BERRY_GREEN -> SkillArt.berry(Berry.GREEN)
        AwardSymbol.BERRY_BLACK -> SkillArt.berry(Berry.BLACK)
        AwardSymbol.ORE_COPPER -> GearArt.ore(Resource.ORE_COPPER)
        AwardSymbol.ORE_SILVER -> GearArt.ore(Resource.ORE_SILVER)
        AwardSymbol.ORE_GOLD -> GearArt.ore(Resource.ORE_GOLD)
        AwardSymbol.LOG_OAK -> GearArt.log(Resource.LOG_OAK)
        AwardSymbol.LOG_BIRCH -> GearArt.log(Resource.LOG_BIRCH)
        AwardSymbol.LOG_MAPLE -> GearArt.log(Resource.LOG_MAPLE)
        else -> {
            val rows = GLYPHS.getValue(s)
            IntArray(12 * 12) { i -> val ch = rows.getOrNull(i / 12)?.getOrNull(i % 12) ?: '.'; GLYPH_COLORS[ch] ?: 0 }
        }
    }

    /** Medaile 16 × 16; nezískaná je šedá a tmavší. */
    fun icon(a: Award, unlocked: Boolean): IntArray {
        val p = SkillArt.Px(SIZE, SIZE)
        val (dark, base, light) = tierColors(a.tier)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val d = hypot(x - 7.5f, y - 7.5f)
            if (d <= 7.9f) p[x, y] = when {
                d > 6.9f -> dark
                d > 5.9f -> if (x + y < 14) light else base
                else -> if (x + y < 12) mix(base, light, 0.35f) else base
            }
        }
        p.blit(symbol(a.symbol), 12, 12, 2, 2)
        return if (unlocked) p.data else IntArray(p.data.size) { i -> grey(p.data[i]) }
    }

    /** Dřevěný rámeček kolem medaile (stejný styl jako čtvereček s fajfkou) – 24 × 24. */
    fun framed(icon: IntArray): IntArray {
        val p = SkillArt.Px(FRAME, FRAME)
        val out = c(0xFF2E1B0E); val woodL = c(0xFFC48A4A); val wood = c(0xFF93602C); val woodD = c(0xFF6C4420); val well = c(0xFF2A1C11)
        for (y in 0 until FRAME) for (x in 0 until FRAME) {
            val edge = x == 0 || y == 0 || x == FRAME - 1 || y == FRAME - 1
            val woodBand = x <= 2 || y <= 2 || x >= FRAME - 3 || y >= FRAME - 3
            p[x, y] = when {
                edge -> out
                woodBand -> when { y == 1 -> woodL; y >= FRAME - 3 -> woodD; x == 1 -> woodL; x >= FRAME - 3 -> woodD; else -> wood }
                x == 3 || y == 3 || x == FRAME - 4 || y == FRAME - 4 -> out
                else -> well
            }
        }
        for ((cx, cy) in listOf(0 to 0, FRAME - 1 to 0, 0 to FRAME - 1, FRAME - 1 to FRAME - 1)) p[cx, cy] = 0
        p.blit(icon, SIZE, SIZE, 4, 4)
        return p.data
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + (((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun grey(c: Int): Int {
        if (c == 0) return 0
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        val l = ((r * 0.3 + g * 0.59 + b * 0.11) * 0.55 + 30).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (l shl 16) or (l shl 8) or l
    }
}
