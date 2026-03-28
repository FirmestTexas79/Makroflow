package cz.uhk.macroflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import cz.uhk.macroflow.achievements.AchievementDao
import cz.uhk.macroflow.achievements.AchievementEntity
import cz.uhk.macroflow.pokemon.*
import kotlin.concurrent.thread

@Database(
    entities = [
        SnackEntity::class,
        CheckInEntity::class,
        ConsumedSnackEntity::class,
        UserProfileEntity::class,
        BodyMetricsEntity::class,
        WaterEntity::class,
        AchievementEntity::class,
        CoinEntity::class,
        CapturedPokemonEntity::class,
        UserItemEntity::class,
        PokedexEntryEntity::class,
        PokedexStatusEntity::class,
        SeenPokemonEntity::class,
        PokemonXpEntity::class,
        StepsEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun snackDao(): SnackDao
    abstract fun checkInDao(): CheckInDao
    abstract fun consumedSnackDao(): ConsumedSnackDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun bodyMetricsDao(): BodyMetricsDao
    abstract fun waterDao(): WaterDao
    abstract fun achievementDao(): AchievementDao
    abstract fun coinDao(): CoinDao
    abstract fun capturedPokemonDao(): CapturedPokemonDao
    abstract fun userItemDao(): UserItemDao
    abstract fun pokedexEntryDao(): PokedexEntryDao
    abstract fun pokedexStatusDao(): PokedexStatusDao
    abstract fun seenPokemonDao(): SeenPokemonDao
    abstract fun pokemonXpDao(): PokemonXpDao
    abstract fun stepsDao(): StepsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "macroflow_database"
                )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Základní vklady při prvním spuštění
                            db.execSQL("INSERT INTO coins (id, balance) VALUES (1, 100)")
                            db.execSQL("INSERT INTO user_items (itemId, quantity) VALUES ('poke_ball', 5)")
                            db.execSQL("INSERT INTO user_items (itemId, quantity) VALUES ('great_ball', 3)")
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            // Naplníme Pokédex asynchronně, aby nezamrzlo UI (opraví nefunkční menu)
                            thread {
                                fillPokedexEntries(db)
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private fun fillPokedexEntries(db: SupportSQLiteDatabase) {
            val kantoList = getKantoNames()
            kantoList.forEachIndexed { index, triple ->
                val idStr = String.format("%03d", index + 1)
                val name = triple.first
                val urlName = name.lowercase()
                    .replace(" ", "-")
                    .replace(".", "")
                    .replace("♀", "-f")
                    .replace("♂", "-m")
                val type = triple.second
                val desc = triple.third
                val hint = getHintForPokemon(idStr)

                // ✅ Definice evolučních pravidel (Pikachu na Raichu dle tvého zadání)
                val evolveLvl = when(idStr) {
                    "010" -> 3   // Caterpie -> Metapod
                    "011" -> 5   // Metapod -> Butterfree
                    "025" -> 8   // Pikachu -> Raichu (level-up na 8)
                    else -> 0
                }
                val evolveTo = when(idStr) {
                    "010" -> "011"
                    "011" -> "012"
                    "025" -> "026"
                    else -> ""
                }

                // Používáme REPLACE, aby se při každém spuštění aktualizovaly texty/evoluce,
                // ale Pokédex zůstal dynamický podle getKantoNames
                db.execSQL(
                    "INSERT OR REPLACE INTO pokedex_entries (pokedexId, webName, displayName, type, macroDesc, unlockedHint, evolveLevel, evolveToId) " +
                            "VALUES ('$idStr', '$urlName', '$name', '$type', '$desc', '$hint', $evolveLvl, '$evolveTo')"
                )
            }
        }

        private fun getHintForId(id: String): String = when (id) {
            "010" -> "Hledej v trávě u Dashboardu. Caterpie je nenápadná, ale s poctivým zapisováním Maker ji jistě objevíš!"
            "007" -> "Cítíš sucho v krku? Vodní Pokémoni se k tvému Dashboardu nepřiblíží, dokud pořádně nehydratuješ a nesplníš dnešní cíl vody!"
            "092", "093", "094" -> "Někteří Pokémoni nesnáší slunce. Zkusil jsi někdy večerní trénink? Říká se, že po 19:00 můžeš narazit na staré duchy."
            "143" -> "Tento Pokémon tvrdě spí. Probudit ho dokáže jen poctivá ranní rutina. Zkus zapsat svůj spánek a váhu 7 dní v kuse!"
            "006" -> "Žhnoucí plameny Charizarda spatří jen opravdoví dříči. Pokračuj v konzistentním zapisování tréninků a budování návyků aspoň 14 dní."
            "150", "151" -> "Tajemná psychická energie pulzuje kdesi v nedohlednu. Získá ji jen ten, kdo se stane mistrem dlouhodobé disciplíny v MakroFlow."
            else -> "Zapiš trénink, udržuj disciplínu a vyraz ho hledat!"
        }

        private fun getHintForPokemon(id: String) = getHintForId(id)

        private fun getKantoNames(): List<Triple<String, String, String>> = listOf(
            Triple("Bulbasaur", "TRÁVA / ELEMENT", "Základní stavební kámen jídelníčku. Nezapomínej na listovou zeleninu pro dostatek mikroživin!"),
            Triple("Ivysaur", "TRÁVA / PROGRES", "Jak roste poupě na zádech, rostou i tvoje svaly. Nepodceňuj progresivní přetížení."),
            Triple("Venusaur", "TRÁVA / OBJEM", "Absolutní král objemové fáze. Květina na zádech potřebuje pořádnou fotosyntézu a hromadu komplexních sacharidů."),
            Triple("Charmander", "OHEŇ / METABOLISMUS", "Nastartuj svůj termogenní spalovač! Kapsaicín z chilli rozdmýchá tvůj vnitřní plamen."),
            Triple("Charmeleon", "OHEŇ / TERMOGENEZE", "Teplota stoupá. Tvůj bazální metabolismus (BMR) pracuje na plné obrátky."),
            Triple("Charizard", "OHEŇ / SPALOVÁNÍ", "Ultimátní definice a rýsování. Spálí tuky rychleji než ty zvládneš zapsat kardio do MakroFlow."),
            Triple("Squirtle", "VODA / HYDRATACE", "Základ každého tréninku! Vypij aspoň 35 ml vody na 1 kg tělesné hmotnosti."),
            Triple("Wartortle", "VODA / REGENERACE", "Krunýř tě ochrání před svalovou horečkou. Ledová vana po těžkém legdayi dělá divy."),
            Triple("Blastoise", "VODA / HYDRO-PUMPA", "Hydratace dohnaná do extrému. Pořádný svalový pump s citrulinem a dostatkem tekutin."),
            Triple("Caterpie", "HMYZ / PROTEIN", "Nenápadný začátek, ale i malý červík v sobě skrývá zárodek budoucích bílkovin. Začni zapisovat jídla!"),
            Triple("Metapod", "HMYZ / STATIKA", "Tuhá schránka značí statickou kontrakci. Zpevni střed těla a drž plank! Odemčeno na Lvl. 3."),
            Triple("Butterfree", "HMYZ / LEHKOST", "Lehká jídla před tréninkem. Poletuj po fitku plný energie. Odemčeno na Lvl. 5 a učí se nový útok!"),
            Triple("Weedle", "HMYZ / MIKRO", "Malé detaily dělají velké výsledky. Nezapomínej na vitamíny a minerály."),
            Triple("Kakuna", "HMYZ / STATIKA", "Čas na statický strečink a hlubokou regeneraci fascií."),
            Triple("Beedrill", "HMYZ / RYCHLOST", "HIIT trénink v plném proudu. Bodavé tempo, které nenechá tvůj tep v klidu."),
            Triple("Pidgey", "LÉTACÍ / MOBILITA", "Zahřátí ramen a rotátorů před benchpressem. Rozpažování s lehkou vahou."),
            Triple("Pidgeotto", "LÉTACÍ / AEROBIK", "Pravidelné kardio pro zdravé srdce. Rozpřáhni křídla na běžeckém páse."),
            Triple("Pidgeot", "LÉTACÍ / KARDIO-KRÁL", "Tepovka v zóně 2. Tuky mizí rychlostí zvuku."),
            Triple("Rattata", "NORMÁLNÍ / REPETICE", "Základní cviky s vlastní vahou. Malý, ale vytrvalý dříč."),
            Triple("Raticate", "NORMÁLNÍ / KRADENÉ KALORIE", "Pozor na noční uzobávání ze spíže! Každý kousek sýra se v MakroFlow počítá."),
            Triple("Spearow", "LÉTACÍ / AGRESIVITA", "Předtréninkový stimulant tě nakopne k agresivnímu výkonu na čince."),
            Triple("Fearow", "LÉTACÍ / VYTRVALOST", "Dlouhé vytrvalostní tréninky. Rozlož si energii na celý den."),
            Triple("Ekans", "JED / DETOX", "Zapomeň na drahé detox čaje. Tvoje játra a ledviny jsou nejlepší čističkou těla."),
            Triple("Arbok", "JED / KONTRAKCE", "Zaškrť svaly pořádným napětím. Mind-muscle connection v praxi."),
            Triple("Pikachu", "ELEKTRO / STIMULANT", "Pořádná dávka kofeinu před tréninkem. Blesková energie do žil!"),
            Triple("Raichu", "ELEKTRO / VÝBOJ", "Centrální nervová soustava (CNS) dostává zabrat. Maximálky na mrtvý tah vyžadují pořádný výboj."),
            Triple("Sandshrew", "ZEMĚ / STABILITA", "Pevný postoj u dřepů je základ. Zapusť nohy do země."),
            Triple("Sandslash", "ZEMĚ / SVALOVÁ DEFINICE", "Vyrýsovaný jako ostny na zádech. Odvodnění před soutěží zvládnuté na jedničku."),
            Triple("Nidoran♀", "JED / BALANC", "Rovnováha mezi silovým tréninkem a regenerací pro něžné pohlaví."),
            Triple("Nidorina", "JED / SÍLA", "Ženská síla v plné kráse. Progres se v aplikaci zapisuje sám."),
            Triple("Nidoqueen", "JED / ŠAMPIONKA", "Královna fitness centra. Estetika spojená s funkční silou."),
            Triple("Nidoran♂", "JED / EGO-LIFTING", "Pozor na příliš velké váhy na úkor techniky! Ego nech v šatně."),
            Triple("Nidorino", "JED / DRIVE", "Tlak na pilu. Každý trénink tě posouvá blíž k cíli."),
            Triple("Nidoking", "JED / ALFA", "Monstrózní síla a dominance na legpressu. Král těžkých vah."),
            Triple("Clefairy", "VÍLA / SPÁNEK", "Hluboký spánek a cirkadiánní rytmus. Svaly nerostou ve fitku, ale v posteli!"),
            Triple("Clefable", "VÍLA / REGENERACE", "Noční obnova buněk. Kasein před spaním dodá tělu potřebné aminokyselins."),
            Triple("Vulpix", "OHEŇ / TERMOGENNÍ JÍDLO", "Pálivé papričky a zázvor. Přírodní podpora spalování tuků."),
            Triple("Ninetales", "OHEŇ / ESTETIKA", "Dokonalá symetrie těla a elegance pohybu. Flexibilita i svaly."),
            Triple("Jigglypuff", "NORMÁLNÍ / DECH", "Brániční dýchání u těžkých dřepů. Nafoukni se a zpevni core."),
            Triple("Wigglytuff", "NORMÁLNÍ / OBJEMOVKA", "Trocha podkožního tuku k objemové fázi patří. Hlavně hlídej kalorie."),
            Triple("Zubat", "JED / NOČNÍ TRÉNINK", "Pro ty, co raději cvičí v prázdném fitku pozdě v noci."),
            Triple("Golbat", "JED / UPÍR ENERGIE", "Pozor na energetické upíry a stres. Kortizol ti pálí těžce vydřené svaly!"),
            Triple("Oddish", "TRÁVA / ALKALIZACE", "Vybalancuj pH těla. Zelená zelenina pomáhá vyrovnat kyselé prostředí z masa."),
            Triple("Gloom", "TRÁVA / FERMENTACE", "Kysané zelí a kimchi. Tvoje střevní mikrobiota ti poděkuje."),
            Triple("Vileplume", "TRÁVA / FITOESTROGENY", "Rostlinné tuky a ořechy. Zdravé tuky pro hormonální optimál."),
            Triple("Paras", "HMYZ / SYMBIOZA", "Kombinace jídla a suplementace. Všechno do sebe musí zapadat."),
            Triple("Parasect", "HMYZ / ADAPTOGENY", "Medicinální houby (Cordyceps, Reishi) pro lepší zvládání stresu a imunitu."),
            Triple("Venonat", "HMYZ / FOCUS", "Maximální soustředění na každé jedno opakování. Tunelové vidění."),
            Triple("Venomoth", "HMYZ / PLYNULOST", "Plynulý pohyb v plném rozsahu (ROM). Žádné trhané poloviční opakování."),
            Triple("Diglett", "ZEMĚ / VLÁKNINA", "Král ranního vyprazdňování! Tento podzemní tvor symbolizuje zdravou peristaltiku střev. Pokud tvůj trůnní rituál vázne, přidej rozpustnou vlákninu a dostatek vody."),
            Triple("Dugtrio", "ZEMĚ / SUPLEMENTY", "Svatá trojice: Protein, Kreatin, Omega-3. Tři pilíře úspěšné suplementace."),
            Triple("Meowth", "NORMÁLNÍ / CHEAT MEAL", "Zlaťáky na utrácení za cheat mealy. Jedonce za čas si dej pizzu a zapiš ji jako radost."),
            Triple("Persian", "NORMÁLNÍ / LEAN BULK", "Čisté svaly bez zbytečného tuku. Kočičí mrštnost a vyrýsovanost."),
            Triple("Psyduck", "VODA / SVALOVÁ KŘEČ", "Bolí tě hlava a chytají tě křeče? Chybí ti hořčík a draslík ve stravě."),
            Triple("Golduck", "VODA / FLOW STATUS", "Stav absolutního soustředění. Jsi ve zóně, kde čas ve fitku neexistuje."),
            Triple("Mankey", "BOJOVÝ / HYPERTROFIE", "Zuřivost na tréninku! Využij agresi k překonání osobních rekordů."),
            Triple("Primeape", "BOJOVÝ / OVERTRAINING", "Pozor na přetrénování a syndrom vyhoření. I vztek má své limity, odpočívej."),
            Triple("Growlithe", "OHEŇ / VĚRNOST REŽIMU", "Konzistentnost je klíč. Věrně následuj svůj plán v MakroFlow den co den."),
            Triple("Arcanine", "OHEŇ / EXPLOZIVITA", "Rychlá svalová vlákna typu II. Plyometrie a výskoky na bednu."),
            Triple("Poliwag", "VODA / HYDRATAČNÍ REŽIM", "Vlnka po vlnce. Pij průběžně celý den, ne až když máš žízeň."),
            Triple("Poliwhirl", "VODA / CORE TRÉNINK", "Spirála na břiše značí silný střed těla. Cvič břišáky komplexně."),
            Triple("Poliwrath", "BOJOVÝ / ŽELEZNÁ SÍLA", "Kombinace plavání a těžkého zvedání železa. Ultimátní kondice."),
            Triple("Abra", "PSYCHICKÝ / MINDSET", "Mentální příprava před sérií. Vizualizuj si úspěšné zvednutí váhy."),
            Triple("Kadabra", "PSYCHICKÝ / SOUSTŘEDĚNÍ", "Lžička v ruce? Použij ji k míchání vloček s proteinem, ne k ohýbání myslí."),
            Triple("Alakazam", "PSYCHICKÝ / BIOMECHANIKA", "IQ 5000 ve fitku! Chápeš biomechaniku těla a pákové poměry u cviků."),
            Triple("Machop", "BOJOVÝ / ZAČÁTEČNÍK", "Nováček ve fitku. Správná technika je teď důležitější než váha na čince."),
            Triple("Machoke", "BOJOVÝ / STREČINK", "Pás si utáhni, váhy jdou nahoru. Tady začíná pořádná dřina."),
            Triple("Machamp", "BOJOVÝ / KULTURISTA", "Čtyři ruce by se hodily na zvedání všech těch kotoučů. Vrchol naturální kulturistiky."),
            Triple("Bellsprout", "TRÁVA / ŠTÍHLÁ LINIE", "Ektomorfní somatotyp. Těžko nabírá svaly, potřebuje kalorický nadbytek."),
            Triple("Weepinbell", "TRÁVA / KYSELÉ PH", "Pozor na překyselení žaludku po těžkých předtréninkovkách."),
            Triple("Victreebel", "TRÁVA / ABSORPCE", "Trávicí enzymy v akci! Vstřebej z jídla maximum živin."),
            Triple("Tentacool", "VODA / ELEKTROLYTY", "Sodík a chlór ztracené potem. Doplň ionťák po těžkém kardiu."),
            Triple("Tentacruel", "VODA / HYDRO-PUMPA", "Svalová pumpa, která trhá kůži. Napumpuj paže k prasknutí."),
            Triple("Geodude", "KAMENNÝ / ZÁKLADNÍ SÍLA", "Tvrdý jako skála. Základní trojboj: Dřep, Bench, Mrtvola."),
            Triple("Graveler", "KAMENNÝ / ŽELEZNÁ HUSTOTA", "Zahušťování svalových vláken těžkými vahami o nízkém počtu opakování."),
            Triple("Golem", "KAMENNÝ / ABSOLUTNÍ SÍLA", "Nezastavitelný kolos. Hustota svalů, kterou nepropíchneš prstem."),
            Triple("Ponyta", "OHEŇ / RYCHLÝ METABOLISMUS", "Spaluje kalorie za běhu. Ideální stav pro obří porce jídla."),
            Triple("Rapidash", "OHEŇ / CARDIO MASTERY", "Sprinty do vrchu. Vyždímej z nohou maximum ohnivé energie."),
            Triple("Slowpoke", "PSYCHICKÝ / DELOAD WEEK", "Čas zpomalit. Zařaď deload týden pro klouby a centrální nervovou soustavu."),
            Triple("Slowbro", "VODA / AKTIVNÍ REGENERACE", "Procházka v přírodě nebo lehké protažení. Žádné těžké váhy dnes."),
            Triple("Magnemite", "ELEKTRO / MINERÁLY", "Nezapomínej na železo! Železo v krvi přenáší kyslík do pracujících svalů."),
            Triple("Magneton", "ELEKTRO / NEUROMUSKULÁRNÍ PROPOJENÍ", "Zesílený signál z mozku do svalů. Aktivuj více motorických jednotek."),
            Triple("Farfetchd", "LÉTACÍ / PŘÍPRAVA JÍDLA", "Král meal-prepu. Vždy má u sebe svůj pórek a krabičku s rýží."),
            Triple("Doduo", "LÉTACÍ / ROZDĚLENÝ TRÉNINK", "Split trénink (vršek / spodek). Rozděl si tělo chytře."),
            Triple("Dodrio", "LÉTACÍ / PUSH-PULL-LEGS", "Třídenní split zvládnutý levou zadní. Komplexní procvičení."),
            Triple("Seel", "VODA / OMEGA TUKY", "Rybí tuk je poklad pro klouby a mozek. Nezapomínej na EPA a DHA."),
            Triple("Dewgong", "VODA / TERMOPLÁŠŤ", "Zdravý podkožní tuk je důležitý pro izolaci a tvorbu hormonů."),
            Triple("Grimer", "JED / NEZPRACOVANÉ POTRAVINY", "Vyhni se vysoce průmyslově zpracovaným potravinám (UPP). Jez opravdové jídlo."),
            Triple("Muk", "JED / ŠPINAVÝ OBJEM", "Dirty bulk tě vytrestá. Tuk se shazuje hůř než se nabírá sval."),
            Triple("Shellder", "VODA / VÁPNÍK", "Pevné kosti díky vápníku a vitamínu D3. Krunýř musí držet."),
            Triple("Cloyster", "VODA / KALCIT", "Tuhá ochrana kloubního aparátu. Kloubní výživa s kolagenem je nutnost."),
            Triple("Gastly", "DUCH / LEHKÁ VÁHA", "Váha na čince je jen iluze. Důležité je procítění sval."),
            Triple("Haunter", "DUCH / CHEATING", "Negativní opakování a cheating. Používej ho jen jako pokročilou techniku."),
            Triple("Gengar", "DUCH / SVALOVÝ STÍN", "V zrcadle vypadáš vždy menší, než jsi doopravdy (tzv. Bigorexie). Jsi borec!"),
            Triple("Onix", "KAMENNÝ / POSTURA", "Rovná páteř je základ. Neprohýbej se v bedrech u mrtvého tahu."),
            Triple("Drowzee", "PSYCHICKÝ / REM SPÁNEK", "Hluboký spánek a cirkadiánní rytmus. Svaly nerostou ve fitku, ale v posteli!"),
            Triple("Hypno", "PSYCHICKÝ / MEDITACE", "Ztišení mysli po tréninku. Sniž kortizol dechovým cvičením."),
            Triple("Krabby", "VODA / MOŘSKÉ PLODY", "Skvělý zdroj bílkovin s minimem tuku. Krevety a krabi do jídelníčku patří."),
            Triple("Kingler", "VODA / ASYMETRIE", "Pozor na svalové dysbalance! Cvič obě strany těla rovnoměrně."),
            Triple("Voltorb", "ELEKTRO / PLYOMETRIE", "Výbušná síla z nuly na sto. Odrazová cvičení pro dynamiku."),
            Triple("Electrode", "ELEKTRO / KREVNÍ TLAK", "Hlídej si krevní tlak při těžkých dřepech. Správně dýchej, ať nebouchneš."),
            Triple("Exeggcute", "TRÁVA / VAJEČNÝ BÍLEK", "Základní fitness potravina. Dokonalé spektrum aminokyselin."),
            Triple("Exeggutor", "TRÁVA / KOKOSOVÝ TUK", "MCT oleje pro rychlou energii. Tuky, co se neukládají, ale pálí."),
            Triple("Cubone", "ZEMĚ / ZDRAVÉ KOSTI", "Kolagen typu II pro pevnost tvého skeletu."),
            Triple("Marowak", "ZEMĚ / TVRDÉ ŠLACHY", "Úpony a šlachy adaptované na těžkou zátěž."),
            Triple("Hitmonlee", "BOJOVÝ / STREČINK", "Dynamický strečink a kopy. Rozsah pohybu je stejně důležitý jako síla."),
            Triple("Hitmonchan", "BOJOVÝ / RYCHLÉ PESTI", "Stínový box jako perfektní kardio na závěr tréninku."),
            Triple("Lickitung", "NORMÁLNÍ / CHUŤOVÉ BUŇKY", "Když tě honí mlsná. Zkus proteinový pudink místo čokolády."),
            Triple("Koffing", "JED / ŠPATNÉ DÝCHÁNÍ", "Nezadržuj dech při zvedání činky (Valsalvův manévr má svá pravidla)."),
            Triple("Weezing", "JED / PLICNÍ KAPACITA", "Zlepšuj své VO2 max pro lepší kyslíkovou kapacitu."),
            Triple("Rehyhorn", "ZEMĚ / HRUBÁ SÍLA", "Probourat se přes stagnaci. Někdy to chce jen zvednout těžší váhu."),
            Triple("Rhydon", "ZEMĚ / PEVNÉ ŠLACHY", "Šlachy z ocele. Trpělivost při budování síly přináší ocele."),
            Triple("Chansey", "NORMÁLNÍ / ZDRAVÍ NA PRVNÍM MÍSTĚ", "Zdravotní krevní testy a prevence. Zdravé tělo podává nejlepší výkony."),
            Triple("Tangela", "TRÁVA / KOMPLEXNÍ SACHARIDY", "Zamotané klubko špaget? Dej si celozrnné těstoviny pro stabilní energii."),
            Triple("Kangaskhan", "NORMÁLNÍ / SPARING PARTNER", "Najdi si parťáka, co tě nenechá pod činkou umřít. Společně dál dojdete."),
            Triple("Horsea", "VODA / MOŘSKÉ ŘASY", "Jód pro správnou funkci štítné žlázy. Nastartuj metabolismus."),
            Triple("Seadra", "VODA / SPALOVÁNÍ TUKŮ", "Kardio ve vodě šetří tvoje klouby. Plavání je skvělý nástroj."),
            Triple("Goldeen", "VODA / ESTETICKÁ KULTURISTIKA", "Pózing před zrcadlem. Nauč se prodat svou formu symetrií."),
            Triple("Seaking", "VODA / SVALOVÁ VYTRVALOST", "Plav proti proudu. Překonávání odporu buduje charakter i svaly."),
            Triple("Staryu", "VODA / REGENERACE TKÁNÍ", "Mikrotrhliny ve svalech se léčí jídlem a spánkem. Regeneruj jako hvězdice."),
            Triple("Starmie", "PSYCHICKÝ / HORMONÁLNÍ OPTIMÁL", "Pěticípá rovnováha: Strava, Trénink, Spánek, Suplementace, Psychika."),
            Triple("Mr. Mime", "PSYCHICKÝ / IZOLOVANÉ CVIKY", "Předstírej, že tlačíš neviditelnou zeď. Dokonalá izolace prsních svalů na peck-decku."),
            Triple("Scyther", "HMYZ / DEFINICE REZŮ", "Svalové separace ostré jako břitva. Žádná vrstva tuku."),
            Triple("Jynx", "PSYCHICKÝ / TEPLOTNÍ ŠOK", "Saunování a otužování. Zvyšuj odolnost organismu."),
            Triple("Electabuzz", "ELEKTRO / INTENZITA", "Vysokofrekvenční trénink (HFT). Cvič svalové partie častěji."),
            Triple("Magmar", "OHEŇ / POT", "Zpocené tričko po tréninku není ukazatel kvality, ale termoregulace."),
            Triple("Pinsir", "HMYZ / ÚCHOP", "Pevný stisk ruky. Trénuj předloktí, ať ti činka nevypadne z rukou."),
            Triple("Tauros", "NORMÁLNÍ / TESTOSTERON", "Přirozený anabolismus. Zdravý tuk a dostatek zinku pro mužské zdraví."),
            Triple("Magikarp", "VODA / SLABÁ CHVÍLE", "Každý nějak začínal. I ta nejmenší ryba se může proměnit v monstrum."),
            Triple("Gyarados", "VODA / TRANSFORMACE", "Důkaz, že konzistence přináší brutální výsledky. Věř procesu!"),
            Triple("Lapras", "VODA / STABILNÍ KARDIO", "Dlouhé a plynulé kardio v tempu, u kterého zvládneš mluvit."),
            Triple("Ditto", "NORMÁLNÍ / VARIABILITA CVIKŮ", "Dokáže se přizpůsobit jakémukoliv tréninkovému plánu. Full-body i Split."),
            Triple("Eevee", "NORMÁLNÍ / POTENCIÁL", "Nepopsaný list. Máš genetický potenciál vydat se jakoukoliv fitness cestou."),
            Triple("Vaporeon", "VODA / HYDRATACE SVALŮ", "Sval tvoří ze 70 % voda. Bez hydratace není objem."),
            Triple("Jolteon", "ELEKTRO / ATP ENERGIE", "Okamžitá obnova buněčné energie svalů pomocí kreatinu."),
            Triple("Flareon", "OHEŇ / SPALOVÁNÍ TUKŮ", "Zvýšená tělesná teplota pálí kalorie i v klidovém stavu."),
            Triple("Porygon", "NORMÁLNÍ / MATEMATIKA MAKER", "Čistá data a výpočty. Kalorie dovnitř vs. kalorie ven."),
            Triple("Omanyte", "VODA / STARÁ ŠKOLA", "Old-school tréninkové metody z dob Arnolda. Základní těžké váhy."),
            Triple("Omastar", "VODA / TVRDÁ KONSTRUKCE", "Krunýř tě ochrání před svalovou horečkou. Ledová vana po těžkém legdayi dělá divy."),
            Triple("Kabuto", "ZEMĚ / HISTORIE FORMY", "Podívej se na své staré fotky. Srovnej progres v čase."),
            Triple("Kabutops", "ZEMĚ / FUNKČNÍ TRÉNINK", "Atletická postava stvořená pro pohyb, rychlost a sílu."),
            Triple("Aerodactyl", "LÉTACÍ / EXPLOSIVNÍ START", "Vyběhni schody s lehkostí. Rychlostní vytrvalost."),
            Triple("Snorlax", "NORMÁLNÍ / REFEED DAY", "Den plný jídla k doplnění glykogenu. Sněz všechno a pak si dej šlofíka."),
            Triple("Articuno", "LED / KRYOTERAPIE", "Léčba chladem pro snížení zánětlivosti svalů."),
            Triple("Zapdos", "ELEKTRO / SPORTOVNÍ VÝKON", "Elektrický kopanec do tréninku. Maximální úsilí v každé sérii."),
            Triple("Moltres", "OHEŇ / DOPEČENÍ FORMY", "Finální rýsování před létem. Forma do plavek je upečena."),
            Triple("Dratini", "DRAČÍ / GENETIKA", "Máš v sobě dračí krev, stačí ji probudit správným tréninkem."),
            Triple("Dragonair", "DRAČÍ / ESTETICKÝ RŮST", "Elegantní a čisté svalové přírůstky bez zbytečného tuku."),
            Triple("Dragonite", "DRAČÍ / KOMPLEXNÍ ŠAMPION", "Kombinace obrovské síly a neuvěřitelné kondice. Vrchol MakroFlow."),
            Triple("Mewtwo", "PSYCHICKÝ / NEUROLOGICKÁ SÍLA", "Propojení mysli a těla na 100 %. Ovládni své svaly nervovou soustavou."),
            Triple("Mew", "PSYCHICKÝ / VŠESTRANNOST", "Dokážeš cokoliv. Umíš zvedat těžké váhy, běhat maratony i stát na rukou.")
        )
    }
}