package cz.uhk.macroflow.pokemon.skills

/**
 * Drobné pixelové ikony 12 × 12 pro strom dovedností a denní úkoly (docs/adr/0058).
 * Kreslí se z mřížek znaků – vygenerováno z tools/skillart/icons.py, úpravy dělej tam.
 */
object TreeArt {
    const val SIZE = 12

    private val PALETTE: Map<Char, Int> = mapOf(
        'B' to 0xFFC48A4A.toInt(),
        'C' to 0xFFB7E4FF.toInt(),
        'L' to 0xFF9CC45A.toInt(),
        'P' to 0xFFD7C4F0.toInt(),
        'R' to 0xFFF19C8F.toInt(),
        'S' to 0xFFE3E8EC.toInt(),
        'W' to 0xFFFDFBF3.toInt(),
        'Y' to 0xFFFFF3B0.toInt(),
        'b' to 0xFF93602C.toInt(),
        'c' to 0xFF4FA3E0.toInt(),
        'd' to 0xFFC9961A.toInt(),
        'g' to 0xFF3E5A1E.toInt(),
        'h' to 0xFFB89F78.toInt(),
        'k' to 0xFF1F5F99.toInt(),
        'l' to 0xFF5F8A2A.toInt(),
        'm' to 0xFF8E2C2A.toInt(),
        'n' to 0xFF6C4420.toInt(),
        'o' to 0xFF1E140C.toInt(),
        'p' to 0xFF8E6CC8.toInt(),
        'q' to 0xFF5A3F8A.toInt(),
        'r' to 0xFFD9534F.toInt(),
        's' to 0xFFA7B1BA.toInt(),
        't' to 0xFF6E7880.toInt(),
        'w' to 0xFFE9DCC4.toInt(),
        'y' to 0xFFFFD54F.toInt()
    )

    private fun grid(vararg rows: String): IntArray {
        require(rows.size == SIZE && rows.all { it.length == SIZE })
        return IntArray(SIZE * SIZE) { i -> PALETTE[rows[i / SIZE][i % SIZE]] ?: 0 }
    }

    val STAR: IntArray by lazy { grid(
        ".....oo.....",
        "....oYyo....",
        "....oYyo....",
        "ooooYyyyoooo",
        "oYYYyyyyyyyo",
        ".oyyyyyyyyo.",
        "..oyyyyyyo..",
        "..oyyyyyyo..",
        ".oyyydoyyyo.",
        ".oydo..odyo.",
        "oyoo....ooyo",
        "oo........oo"
    ) }

    val DOUBLE: IntArray by lazy { grid(
        "...o........",
        "..oCo.......",
        ".oCcko......",
        "oCcckko.o...",
        ".okkko.oCo..",
        "..oko.oCcko.",
        "...o.oCcckko",
        ".....okkkko.",
        "......okko..",
        ".......oo...",
        "............",
        "............"
    ) }

    val MOON: IntArray by lazy { grid(
        "....oooo....",
        "..ooPPpo....",
        ".oPPpo....o.",
        ".oPpo....oWo",
        "oPPpo.....o.",
        "oPpo........",
        "oPpo........",
        "oPppo.....oo",
        ".oPppoo.ooqo",
        ".oqpppppppo.",
        "..ooqqqqoo..",
        "....oooo...."
    ) }

    val LOCK: IntArray by lazy { grid(
        "....oooo....",
        "...osSSso...",
        "..oso..oso..",
        "..oso..oso..",
        ".oooooooooo.",
        ".oyYYyyyyyo.",
        ".oyyyooyydo.",
        ".oyyyooyydo.",
        ".oyyyyoyydo.",
        ".oyyyyyyddo.",
        ".oddddddddo.",
        ".oooooooooo."
    ) }

    val COIN: IntArray by lazy { grid(
        "...oooooo...",
        "..oyYYYyyo..",
        ".oyYyyyyydo.",
        "oyYyyddyyydo",
        "oyYydyyyyydo",
        "oyyydyyyyydo",
        "oyyydyyyyydo",
        "oyyyyddyyydo",
        "oyyyyyyyyddo",
        ".oyyyyyyddo.",
        "..odddddoo..",
        "...oooooo..."
    ) }

    val SWORD: IntArray by lazy { grid(
        "..........oo",
        ".........oSo",
        "........oSso",
        ".......oSso.",
        "......oSso..",
        ".oo..oSso...",
        ".oyooSso....",
        "..oyysso....",
        "..onyyo.....",
        ".onbooyo....",
        "onbo..oo....",
        "ooo........."
    ) }

    val TREE: IntArray by lazy { grid(
        ".....oo.....",
        "....oLlo....",
        "...oLlllo...",
        "...oLllgo...",
        "..oLlllllo..",
        "..oLllllgo..",
        ".oLlllllllo.",
        ".oLllllllgo.",
        "oollllllggoo",
        "...oobnoo...",
        "....obno....",
        "....oooo...."
    ) }

    val DROP: IntArray by lazy { grid(
        ".....oo.....",
        ".....oco....",
        "....occo....",
        "...occcco...",
        "...oCccco...",
        "..oCccccko..",
        "..oCccccko..",
        ".oCWcccccko.",
        ".oCWcccccko.",
        ".occcccckko.",
        "..okkkkkko..",
        "...oooooo..."
    ) }

    val MEAT: IntArray by lazy { grid(
        "....oooo....",
        "...oRrrro...",
        "..oRrrrrmo..",
        ".oRrrrrrrmo.",
        ".oRrrrrrrmo.",
        ".orrrrrrmmo.",
        "..ommrrmmo..",
        "...oommoo...",
        "....oWWo....",
        "...oWWWWo...",
        "...oWooWo...",
        "....o..o...."
    ) }

    val LEAF: IntArray by lazy { grid(
        "........oooo",
        "......ooLLlo",
        "....ooLLlllo",
        "...oLLlllglo",
        "..oLlllglllo",
        "..oLllglllgo",
        ".oLllglllgo.",
        ".oLlglllgo..",
        ".olglllgo...",
        ".ogoggoo....",
        "og.ooo......",
        "o..........."
    ) }

    val BOWL: IntArray by lazy { grid(
        "....b.b.....",
        "....b.b.....",
        "...o..b.o...",
        "..oLrlLyro..",
        "oooooooooooo",
        "owwwwwwwwwho",
        ".owwwwwwwho.",
        ".owwwwwwhho.",
        "..owwwwhho..",
        "...oohhoo...",
        "...oooooo...",
        "............"
    ) }

    val BOOT: IntArray by lazy { grid(
        "..oooooo....",
        "..oBbbbo....",
        "..oBbbbo....",
        "..oBbbno....",
        "..oBbbno....",
        "..oBbbno....",
        "..oBbbbnooo.",
        "..oBbbbbbbno",
        "..oBbbbbbbno",
        "..onnnnnnnno",
        "..oooooooooo",
        "............"
    ) }

    val DUMBBELL: IntArray by lazy { grid(
        "............",
        "oo........oo",
        "oso......oso",
        "oSso....oSso",
        "oSsoooooSsso",
        "oSsoSSSSoSso",
        "oSsottttosso",
        "oSsoooooSsso",
        "oSso....oSso",
        "oso......oso",
        "oo........oo",
        "............"
    ) }

    val SUN: IntArray by lazy { grid(
        ".....yy.....",
        "..y..yy..y..",
        "...yooooyy..",
        "..oYYyyyyo..",
        "yyoYyyyyydoy",
        "y.oYyyyyydo.",
        "..oyyyyyydo.",
        ".yoyyyyyddoy",
        "..oddddddo..",
        "..y.oooo.y..",
        ".....yy.....",
        "............"
    ) }

}
