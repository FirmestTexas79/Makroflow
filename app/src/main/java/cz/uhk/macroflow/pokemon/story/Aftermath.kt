package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.pokemon.cave.OzvenaCity
import cz.uhk.macroflow.pokemon.cave.RootForest
import cz.uhk.macroflow.pokemon.cave.TowerLobby

/**
 * Následky konců (docs/adr/0115): po dohrání se Makrosvět změní podle toho, jak hráč skončil.
 *  - při prvním příchodu na místo po konci se jednou ukáže, co je jinak (příchozí věta),
 *  - postavy, jejichž příběh je hotový, mluví jinak (přednost před běžným pozdravem; úkoly se nikdy neblokují –
 *    postava přepne až po splnění svého vedlejšího úkolu, viz [Npc.requires]).
 * Jen konec III (Poslední přání) smí prozradit, že Makromoni byli lidé.
 */
object Aftermath {
    /** GamePrefs: místa, kde už hráč příchozí větu po konci viděl (názvy BiomeType). */
    const val SEEN_KEY = "nasledky_videno"

    data class Npc(val biome: String, val node: String, val requires: String?)

    val BRANN = Npc("OZVENA", OzvenaCity.BAKERY_NODE, null)
    val LIO = Npc("OZVENA", OzvenaCity.LIO_NODE, Collectibles.LETTERS_FINAL_KEY)
    val SEPT = Npc("TOWER_LOBBY", TowerLobby.SEPT_NODE, null)
    val KESH = Npc("ROOT_SIGNAL", Signal.NODE, Lanterns.FINAL_KEY)
    val GUDWIN = Npc("ROOT_FOREST", RootForest.GUDWIN_NODE, ForestNames.FINAL_KEY)
    val NPCS = listOf(BRANN, LIO, SEPT, KESH, GUDWIN)

    /** Věta postavy po konci, nebo null (pak běží její běžná akce). */
    fun npc(end: Finale.End, biome: String, node: String, isSet: (String) -> Boolean): String? {
        val n = NPCS.firstOrNull { it.biome == biome && it.node == node } ?: return null
        if (n.requires != null && !isSet(n.requires)) return null
        return NPC_LINES[end]?.get(n)
    }

    /** Příchozí věta na místo po konci (jednou na místo), nebo null. */
    fun arrival(end: Finale.End, biome: String, seen: Set<String>): String? =
        if (biome in seen) null else ARRIVALS[end]?.get(biome)

    private val NPC_LINES: Map<Finale.End, Map<Npc, String>> = mapOf(
        Finale.End.SPEAR to mapOf(
            BRANN to "🍞 Brann ti podá chleba a usměje se jako na cizího. „Vítej v Ozvěně, poutníku. Jsi tu poprvé, viď?“ " +
                "Ruce má od mouky a oči úplně klidné.",
            LIO to "✉️ Lio ti podá dopis. Má adresu – ale ne tvoji. „Pro toho, kdo přijde po tobě,“ řekne. „Nevím, proč to vím.“",
            SEPT to "📁 Sept listuje novou složkou, na hřbetě 214. „Subjekt 213?“ zvedne oči. „Takový spis nevedeme. Nikdy jsme nevedli. " +
                "Přejete si lístek?“",
            KESH to "🏮 Kesh rozsvěcí lucerny a tebe si nevšimne. Když ho pozdravíš, zamrká: „Signál? Ne. Tady dole se nikdy nic neozvalo.“",
            GUDWIN to "🧓 Gudwin sedí na kraji kruhu a opakuje jména. Když tě uvidí, otevře pusu – a zarazí se. Poprvé za celou dobu " +
                "si nemůže vzpomenout, jak se jmenuješ. „Promiň,“ šeptne. „Ty jsi… ten, co drží kopí?“"
        ),
        Finale.End.BLOOM to mapOf(
            BRANN to "🍞 Brann stojí před pekárnou a dívá se na řeku. „Dneska je jiné ráno než včera,“ řekne. " +
                "„Pamatuju si včerejšek. To se mi dlouho nestalo.“",
            LIO to "✉️ Lio suší dopisy na šňůře u kašny. Inkoust se rozpil a adresy zmizely. „Stejně je nikdo nečetl,“ pokrčí rameny. " +
                "„Aspoň je teď vidět, jak jsou krásné.“",
            SEPT to "☂️ Sept má kalhoty mokré po kolena a deštník pořád otevřený, i uvnitř. Spisy se suší po lavicích. " +
                "„Potopa,“ řekne. „Tak tomu říká Řád. Já už nevím, jak tomu říkat.“",
            KESH to "🏮 Kesh zhasíná lucerny. „Už nejsou potřeba,“ ukáže dolů: obrácený strom kvete. " +
                "„Celý život jsem hlídal tmu. A ona rozkvetla.“",
            GUDWIN to "🧓 Gudwin sedí v trávě. Vyšlapaný kruh zarostl a mlha je pryč. „Už jim nemusím říkat jména,“ usměje se. " +
                "„Les si je říká sám. Slyšíš?“"
        ),
        Finale.End.WISH to mapOf(
            BRANN to "🍞 Brann peče dva bochníky místo jednoho. „Jeden je pro Bohouška,“ kývne k Ignarovi u pece. " +
                "„Vzpomněl si, že kdysi taky pekl. Prý líp než já.“",
            LIO to "✉️ Lio rozdává dopisy. Najednou mají adresy, psané drobným písmem Lumenců. „Vědí, kam patří,“ řekne. " +
                "„Konečně vědí, kam patří.“",
            SEPT to "📁 Sept sedí nad otevřenými spisy a čte jména, ne čísla. „Lumenci,“ šeptá. „Řád je inventarizoval jako zvířata. " +
                "Já jsem je inventarizoval jako zvířata.“ Ruce se mu třesou.",
            KESH to "🏮 „Asko,“ řekne Kesh, sotva tě uvidí. „Jmenuju se Asko. Pořád si to opakuju, abych to zase nezapomněl.“ " +
                "Lucernu drží oběma rukama, pevně.",
            GUDWIN to "🧓 Gudwin se směje a pláče zároveň. „Pamatují si!“ volá do lesa. „Všichni si pamatují. Už nejsem jediný.“"
        ),
        Finale.End.SECRET to mapOf(
            BRANN to "🍞 Brann zavírá pekárnu dřív. „Jdu spát,“ zívne. „Divný, co? Celé roky jsem nemohl, a teď se mi chce.“",
            LIO to "✉️ Lio ti podá dopis. Konečně má adresu: červený domek ve Městě. Uvnitř je jediná věta: „Vítej doma.“",
            SEPT to "📁 Sept zavírá poslední složku a podá ti ji. Na hřbetě je místo čísla prázdné místo. " +
                "„Napište si tam, co chcete,“ řekne. „Já končím směnu.“",
            KESH to "🏮 Kesh spí v boudě signalisty, hlavu na Knize signálů. Lucerny svítí samy. Necháš ho spát.",
            GUDWIN to "🧓 Kruh je prázdný. Na pařezu, kde Gudwin sedával, leží vzkaz: „Šel jsem domů. Vyspat se. Ty to znáš.“"
        )
    )

    private val ARRIVALS: Map<Finale.End, Map<String, String>> = mapOf(
        Finale.End.SPEAR to mapOf(
            "TOWN" to "🏘️ Město je stejné jako vždycky. Jen jmenovka na tvé poštovní schránce je prázdná – někdo ji seškrábal, nebo nikdy nebyla.",
            "OZVENA" to "🔔 Ozvěna jede dál jako hodinky. Na nástěnce visí oznámení: PŘÍCHOD SUBJEKTU 214 – PROSÍME O VSTŘÍCNOST.",
            "TOWER_LOBBY" to "🔴 Tabule v čekárně svítí: 214. V lavici sedí někdo nový a drží lístek.",
            "ROOT_FOREST" to "🌫️ Mlha v lese je řidší. Ale znaky na kůře jsou smyté – všechny, i ty, které ukazovaly na tebe.",
            "ROOT_TRUNK" to "🗡️ Kopí drží ránu. Popel už nepadá. Na lávce čeká Spirra stočená do klubíčka – ona jediná ví, kdo jsi."
        ),
        Finale.End.BLOOM to mapOf(
            "TOWN" to "🌸 Ve Městě kvetou stromy, i když na to není doba. Voda v jezírku je teplá jako v létě.",
            "OZVENA" to "🌊 Ozvěnou teče řeka – od brány přes náměstí až ke kašně. Popel z ulic odplaval a dlažba je čistá.",
            "TOWER_LOBBY" to "💧 Podlaha čekárny je mokrá, na lavicích se suší spisy. Tabule nesvítí – automat na lístky utopila voda.",
            "ROOT_NAMELESS" to "🌼 Na Svahu bezejmenných kvetou bílé květy, jeden u každého, kdo nedošel. Vodočetná lať stojí ve vodě – hladina už neklesá.",
            "ROOT_FOREST" to "🍃 Les pod korunou je zelený odspodu až nahoru. Mlha se zvedla a mezi větvemi je vidět daleko.",
            "ROOT_TRUNK" to "🌿 Rána v kmeni zarostla listím. Kopí leží na lávce a pomalu ho obrůstá břečťan."
        ),
        Finale.End.WISH to mapOf(
            "TOWN" to "✨ Ve Městě je slyšet tiché broukání. Makromoni v trávě se občas zastaví a podívají se na tebe, jako by tě znali.",
            "MEADOW" to "🌾 Na louce sedí malý Makromon a skládá z kamínků znaky. Když přijdeš blíž, nepřikryje je. Chce, abys je viděl.",
            "OZVENA" to "🕯️ V oknech Ozvěny svítí malá světla. Někdo na kašnu napsal křídou znaky Lumenců a nikdo je nesmazal.",
            "TOWER_LOBBY" to "📁 Na tabuli v čekárně není číslo. Svítí tam jméno, které nikdo nezná. Automat stojí.",
            "ROOT_TEMPLE" to "🖼️ Malby v chrámu svítí: lidé se světlem v dlaních. Někdo k nim přinesl čerstvý květ."
        ),
        Finale.End.SECRET to mapOf(
            "TOWN" to "🏡 V červeném domku je ustlaná postel a Spirra spí na dece. Město je potichu – jako ráno, než se kdokoli probudí.",
            "OZVENA" to "🌙 Ozvěna spí. Okenice jsou zavřené a na pekárně visí cedulka: ZAVŘENO – VYSPÁVÁME.",
            "TOWER_LOBBY" to "🎫 Tabule nesvítí. Na podlaze leží prázdný lístek. Nikdo další už není na řadě.",
            "ROOT_FOREST" to "🌲 Vyšlapaný kruh je prázdný a pomalu zarůstá mechem. Nikdo už v něm nechodí dokola."
        )
    )
}
