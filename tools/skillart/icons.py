# 12x12 pixel ikony – zdroj pravdy pro TreeArt.kt (generuje se odsud)
PAL = {
 '.':None,'o':0xFF1E140C,
 'Y':0xFFFFF3B0,'y':0xFFFFD54F,'d':0xFFC9961A,          # zlatá
 'W':0xFFFDFBF3,'s':0xFFA7B1BA,'S':0xFFE3E8EC,'t':0xFF6E7880,  # ocel
 'b':0xFF93602C,'B':0xFFC48A4A,'n':0xFF6C4420,           # dřevo / kůže
 'C':0xFFB7E4FF,'c':0xFF4FA3E0,'k':0xFF1F5F99,           # voda / modrá
 'r':0xFFD9534F,'R':0xFFF19C8F,'m':0xFF8E2C2A,           # maso / červená
 'L':0xFF9CC45A,'l':0xFF5F8A2A,'g':0xFF3E5A1E,           # list
 'P':0xFFD7C4F0,'p':0xFF8E6CC8,'q':0xFF5A3F8A,           # fialová (měsíc)
 'w':0xFFE9DCC4,'h':0xFFB89F78,                           # miska
}
ICONS = {
'star': [
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
"oo........oo"],
'double': [
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
"............"],
'moon': [
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
"....oooo...."],
'lock': [
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
".oooooooooo."],
'coin': [
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
"...oooooo..."],
'sword': [
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
"ooo........."],
'tree': [
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
"....oooo...."],
'drop': [
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
"...oooooo..."],
'meat': [
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
"....o..o...."],
'leaf': [
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
"o..........."],
'bowl': [
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
"............"],
'boot': [
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
"............"],
'dumbbell': [
"............",
"oo........oo",
"oso......oso",
"oSso....oSso",
"oSsoooooSsso",
"oSsoSSSSoSso",
"oSsotttto sso".replace(' ',''),
"oSsoooooSsso",
"oSso....oSso",
"oso......oso",
"oo........oo",
"............"],
'sun': [
".....yy.....",
"..y..yy..y..",
"...yoooo y..".replace(' ','y'),
"..oYYyyyyo..",
"yyoYyyyyydoy",
"y.oYyyyyydo.",
"..oyyyyyydo.",
".yoyyyyyddoy",
"..oddddddo..",
"..y.oooo.y..",
".....yy.....",
"............"],
}

# ── generátor: python3 tools/skillart/icons.py → TreeArt.kt ────────────────
if __name__ == '__main__':
    import os
    assert all(len(v) == 12 and all(len(r) == 12 for r in v) for v in ICONS.values())
    pal = sorted(set(ch for v in ICONS.values() for r in v for ch in r) - {'.'})
    out = ["package cz.uhk.macroflow.pokemon.skills", "",
           "/**", " * Drobné pixelové ikony 12 × 12 pro strom dovedností a denní úkoly (docs/adr/0058).",
           " * Kreslí se z mřížek znaků – vygenerováno z tools/skillart/icons.py, úpravy dělej tam.", " */",
           "object TreeArt {", "    const val SIZE = 12", "",
           "    private val PALETTE: Map<Char, Int> = mapOf("]
    out.append(",\n".join("        '%s' to 0x%08X.toInt()" % (ch, PAL[ch]) for ch in pal))
    out.append("    )\n")
    out.append("    private fun grid(vararg rows: String): IntArray {")
    out.append("        require(rows.size == SIZE && rows.all { it.length == SIZE })")
    out.append("        return IntArray(SIZE * SIZE) { i -> PALETTE[rows[i / SIZE][i % SIZE]] ?: 0 }")
    out.append("    }\n")
    for k, v in ICONS.items():
        out.append("    val %s: IntArray by lazy { grid(\n%s\n    ) }\n" % (k.upper(), ",\n".join('        "%s"' % r for r in v)))
    out.append("}")
    here = os.path.dirname(os.path.abspath(__file__))
    dst = os.path.join(here, '..', '..', 'app/src/main/java/cz/uhk/macroflow/pokemon/skills/TreeArt.kt')
    open(dst, 'w').write("\n".join(out) + "\n")
