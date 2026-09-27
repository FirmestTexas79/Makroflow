package cz.uhk.macroflow.pokemon.story

/**
 * Příběh Hvozdu (docs/adr/0045) – čistá data bez Androidu, pokryto testy.
 *
 * Po souboji s legendou odletěla Drakirra nad Hvozd; z popela z jejích křídel vyrašila Rudá
 * hniloba. Druid Mydrus hráče provede questem „forest_heart“ a Starý dub nakonec vydá
 * Srdce Hvozdu – klíč, který otevře Bránu světů na Nebeském průsmyku.
 */
object ForestHeart {
    const val QUEST_ID = "forest_heart"

    /** Poražený Soulord v kořenech Starého dubu (StoryFlags). */
    const val ROT_DEFEATED_KEY = "forest_rot_defeated"

    /** Klíčový předmět v batohu (synchronizuje se jako každý předmět). */
    const val ITEM_ID = "srdce_hvozdu"
    const val LABEL = "Srdce Hvozdu"
    const val DESCRIPTION = "Jantarové semeno, které tisíc let zrálo v dřeni Starého dubu. Je teplé a tluče jako srdce. " +
        "Druidi jím kdysi zapečetili Bránu světů – vlož ho do lůžka na Nebeském průsmyku za svatyní."

    /** Uzly Hvozdu, které patří k příběhu. */
    const val MYDRUS_NODE = "mytina"
    const val OAK_NODE = "stary_dub"

    /** Mydrus se na mýtině objeví až po souboji s legendou (Drakirra odletěla nad Hvozd). */
    fun questAvailable(legendFaced: Boolean): Boolean = legendFaced

    /** Fáze questu, ve které Soulord čeká v kořenech dubu (STORY_FLAG). */
    const val BOSS_STAGE = 5

    /** Místa zasažená hnilobou (střed x, y a poloměr v art px Hvozdu) – dokud Soulord nepadne. */
    val ROT_SPOTS = listOf(
        Triple(62, 379, 8),      // břeh tichého jezírka
        Triple(70, 246, 8),      // padlý kmen v houštině
        Triple(104, 82, 10),     // kořeny Starého dubu
        Triple(80, 84, 7),
        Triple(214, 324, 7),     // kruh hub u louky
        Triple(142, 206, 5)      // u cesty
    )

    /** Kde stojí na mapě Mydrus, Soulord a Mycité (art px, střed spodního okraje). */
    val MYDRUS_POS = 165 to 58
    val SOULORD_POS = 112 to 94
    val MYCIT_POS = listOf(136 to 62, 180 to 66)

    /**
     * Skvrna hniloby (2r+1)×(r+1) px: fialová plíseň (zploštělá elipsa), rudé kloboučky hub
     * a světlé spory. [seed] mění rozmístění, aby každá skvrna vypadala jinak.
     */
    fun rotPatch(r: Int, seed: Int): IntArray {
        val w = 2 * r + 1; val h = r + 1
        val out = IntArray(w * h)
        val cx = r.toDouble(); val cy = h / 2.0
        fun hash(x: Int, y: Int) = ((x * 374761393 + y * 668265263 + seed * 1442695041) xor (seed shl 7)).let { (it xor (it ushr 13)) * 1274126177 }.ushr(1) % 1000
        for (y in 0 until h) for (x in 0 until w) {
            val dx = (x - cx) / (r + 0.5); val dy = (y - cy) / (h / 2.0 + 0.3)
            val d = dx * dx + dy * dy
            val n = hash(x, y)
            if (d > 1.0 || (d > 0.7 && n < 450)) continue
            out[y * w + x] = when {
                n < 60 -> 0xFFE8B8F8.toInt()                 // spora
                d < 0.35 && n < 380 -> 0xFF7A2A8A.toInt()    // tmavé jádro
                n < 520 -> 0xFFA848B8.toInt()
                else -> 0xCC8A3AA0.toInt()
            }
        }
        // kloboučky hub: 2–3 podle velikosti
        val caps = 1 + r / 4
        for (i in 0 until caps) {
            val mx = 1 + (hash(i, 99) % (w - 3)); val my = (hash(99, i) % maxOf(1, h - 2))
            for ((ox, oy, c) in listOf(Triple(0, 0, 0xFFC8282A.toInt()), Triple(1, 0, 0xFFE84A3A.toInt()), Triple(2, 0, 0xFFC8282A.toInt()),
                    Triple(1, 1, 0xFFF0E0C8.toInt()))) {
                val x = mx + ox; val y = my + oy
                if (x in 0 until w && y in 0 until h) out[y * w + x] = c
            }
        }
        return out
    }

    /** Text na mýtině, dokud se Mydrus neukázal (legenda ještě spí). */
    const val MYTINA_BEFORE =
        "🌳 Mýtina v srdci Hvozdu. Na prastarém pařezu rostou rudé houby a v korunách je slyšet šepot…\n" +
            "Jako by les na něco čekal. Možná se něco musí probudit – nahoře na vrcholu hor."

    /** Ikona 16×16: jantarové semeno s lístkem a zlatou září uvnitř. */
    const val ICON = 16

    fun iconPixels(): IntArray {
        val o = 0xFF2A1A0A.toInt()        // obrys
        val d = 0xFF8A4A10.toInt()        // tmavý jantar
        val m = 0xFFD8841C.toInt()        // jantar
        val l = 0xFFF4B840.toInt()        // světlý jantar
        val g = 0xFFFFF0A0.toInt()        // záře
        val leafD = 0xFF2E6A2A.toInt()
        val leaf = 0xFF4EB84A.toInt()
        val leafL = 0xFF9BE07A.toInt()
        val rows = listOf(
            "........oo......",
            ".......oLlo.....",
            "......oLLlo.....",
            "......olLo......",
            ".....oo.oo......",
            "....odmmmdo.....",
            "...odmllmmdo....",
            "..odmlggllmdo...",
            "..odmlgglmmdo...",
            "..odmllllmmdo...",
            "..oddmllmmddo...",
            "...oddmmmddo....",
            "....oddddddo....",
            ".....oddddo.....",
            "......oooo......",
            "................"
        )
        val pal = mapOf('o' to o, 'd' to d, 'm' to m, 'l' to l, 'g' to g, 'L' to leaf, 'D' to leafD)
        val out = IntArray(ICON * ICON)
        rows.forEachIndexed { y, r ->
            r.forEachIndexed { x, ch ->
                out[y * ICON + x] = when (ch) {
                    'l' -> if (y <= 3) leafL else l
                    else -> pal[ch] ?: 0
                }
            }
        }
        return out
    }
}
