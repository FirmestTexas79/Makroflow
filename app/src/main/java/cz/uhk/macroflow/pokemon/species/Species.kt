package cz.uhk.macroflow.pokemon.species

import cz.uhk.macroflow.pokemon.BattleFactory
import cz.uhk.macroflow.pokemon.BiomeType
import cz.uhk.macroflow.pokemon.BiomeType.*
import cz.uhk.macroflow.pokemon.Conditions
import cz.uhk.macroflow.pokemon.Makromon
import cz.uhk.macroflow.pokemon.MakromonType
import cz.uhk.macroflow.pokemon.Move
import cz.uhk.macroflow.pokemon.Rarity
import cz.uhk.macroflow.pokemon.Rarity.*
import cz.uhk.macroflow.pokemon.SpawnCondition
import cz.uhk.macroflow.pokemon.StatEffect
import cz.uhk.macroflow.pokemon.skills.DropFamily

/**
 * Registr všech Makromonů (docs/adr/0067): **jeden záznam = jeden druh**. Odsud se berou
 * Makrodex (AppDatabase), souboje (BattleFactory), spawny (SpawnManager), vývoj, materiály
 * z kořisti (Drops), šance chycení i velikost spritu v souboji.
 *
 * Nový Makromon = nový záznam níže + obrázek `res/drawable/makromon_<2 číslice>_<jméno malými>.png`
 * (např. `makromon_36_tynafi.png`). Nic dalšího není potřeba; volitelně růstová křivka
 * v MakromonGrowthManager a vlastní chování na mapě ve StandardWanderer (jinak výchozí).
 * Verzi databáze kvůli novému druhu zvedat netřeba – Makrodex se zapisuje při každém spuštění.
 */
data class Stats(val hp: Int, val atk: Int, val def: Int, val spd: Int, val level: Int = 1)

/** Vývoj na druh [to] od levelu [level]. */
data class Evolve(val to: String, val level: Int)

/** Kde a jak často se druh objevuje v divočině. Víc spawnů = víc řádků (např. jinde jiná rarita). */
data class Spawn(
    val rarity: Rarity,
    val biomes: List<BiomeType>,
    val conditions: List<SpawnCondition> = listOf(Conditions.ALWAYS)
)

data class Species(
    /** Číslo Makrodexu, např. "036". */
    val id: String,
    /** Vnitřní jméno v soubojích a v uložených datech (VELKÝMI), např. "TYNAFI". Neměnit – je v DB. */
    val name: String,
    /** Jméno v Makrodexu. */
    val displayName: String,
    /** Typ v Makrodexu (text), např. "OHEŇ / SÍLA". */
    val dexType: String,
    val desc: String,
    /** Nápověda, kde druh hledat. */
    val hint: String,
    val stats: Stats = Stats(1, 1, 1, 1),
    /** Základní útoky (level 1). Lambda, protože Move má měnitelné PP – každý souboj dostane nové. */
    val moves: () -> List<Move> = { emptyList() },
    /** Typ pro účinnost útoků; null = typ prvního útoku. */
    val type: MakromonType? = null,
    val evolves: Evolve? = null,
    val spawns: List<Spawn> = emptyList(),
    /** Materiál, který padá z kořisti (docs/adr/0040). */
    val family: DropFamily = DropFamily.NORMAL,
    /** Násobič šance chycení (nižší = těžší). */
    val catchRate: Float = 1f,
    /** Výška spritu soupeře v souboji (GB pixely). */
    val battleHeight: Float = 28f,
    /** Strážce: vlastní číslo, jméno a sprite, ale statistiky a útoky druhu [guardianOf]. */
    val guardianOf: String? = null,
    /** Výměnou se vyvine v tento druh (docs/adr/0077); null = výměna nic nemění. */
    val tradeEvolvesTo: String? = null
) {
    /** Název obrázku: makromon_36_tynafi. */
    val sprite: String get() = "makromon_${id.takeLast(2)}_${name.lowercase()}"

    /** Základní (level 1) Makromon tohoto druhu. */
    fun create(): Makromon = guardianOf?.let { SpeciesRegistry.create(it).copy(name = name) }
        ?: Makromon(name = name, level = stats.level, maxHp = stats.hp, attack = stats.atk,
            defense = stats.def, speed = stats.spd, moves = moves(), type = type)
}

object SpeciesRegistry {

    /** Divočina = vše kromě města a vody. */
    val ALL_WILD: List<BiomeType> = BiomeType.values().filter { it != TOWN && it != WATER }

    val ALL: List<Species> = with(BattleFactory) { listOf(

        // 01 - Ignar (ohnivá ještěrka)
        Species("001", "IGNAR", "Ignar", "OHEŇ / STARTER",
            desc = "Ohnivá ještěrka plná energie. Nastartuj svůj metabolismus jako Ignar rozdmýchává svůj ocas!",
            hint = "Ignar tě čeká od samého začátku. Vyber si ho jako svého startéra!",
            stats = Stats(hp = 39, atk = 52, def = 43, spd = 65),
            moves = { listOf(attackScratch(), attackGrowl()) },
            evolves = Evolve("002", 4),
            spawns = listOf(Spawn(COMMON, listOf(TOWN))),
            family = DropFamily.FIRE
        ),

        // 02 - Ignaroc (střední evoluce)
        Species("002", "IGNAROC", "Ignaroc", "OHEŇ / PROGRES",
            desc = "Střední evoluce Ignara. Oheň uvnitř roste spolu s tvojí silou.",
            hint = "Ignar se vyvine na levelu 4. Cvič poctivě!",
            stats = Stats(hp = 58, atk = 64, def = 58, spd = 80),
            moves = { listOf(attackScratch(), attackGrowl(), attackEmber()) },
            evolves = Evolve("003", 10),
            spawns = listOf(Spawn(RARE, listOf(MOUNTAINS))),
            family = DropFamily.FIRE
        ),

        // 03 - Ignaroth (finální dračí forma)
        Species("003", "IGNAROTH", "Ignaroth", "OHEŇ / DRAK",
            desc = "Finální forma. Dračí oheň a síla. Ultimátní spalovač kalorií.",
            hint = "Ignaroc se vyvine na levelu 10. Dlouhodobá disciplína přináší ovoce.",
            stats = Stats(hp = 78, atk = 84, def = 78, spd = 100),
            moves = { listOf(attackEmber(), attackDragonClaw(), attackFlamethrower()) },
            spawns = listOf(Spawn(EPIC, listOf(MOUNTAINS), listOf(Conditions.MinCheckInCount(7)))),
            family = DropFamily.FIRE,
            battleHeight = 40f
        ),

        // 04 - Aqulin (malý vydří s ploutví)
        Species("004", "AQULIN", "Aqulin", "VODA / STARTER",
            desc = "Malý vydří s ploutví. Hydratace je základ každého výkonu!",
            hint = "Aqulin tě čeká od samého začátku. Vyber si ho jako svého startéra!",
            stats = Stats(hp = 44, atk = 48, def = 65, spd = 55),
            moves = { listOf(attackTackle(), attackWaterGun()) },
            evolves = Evolve("005", 4),
            spawns = listOf(Spawn(COMMON, listOf(TOWN))),
            family = DropFamily.WATER
        ),

        // 05 - Aqulind (střední evoluce)
        Species("005", "AQULIND", "Aqulind", "VODA / REGENERACE",
            desc = "Střední evoluce Aqulina. Voda léčí a regeneruje.",
            hint = "Aqulin se vyvine na levelu 4. Plň svůj vodní cíl každý den!",
            stats = Stats(hp = 59, atk = 63, def = 80, spd = 68),
            moves = { listOf(attackWaterGun(), attackBubbleBeam(), attackBite()) },
            evolves = Evolve("006", 10),
            spawns = listOf(Spawn(RARE, listOf(WATER))),
            family = DropFamily.WATER
        ),

        // 06 - Aqulinox (bojovná vodní forma)
        Species("006", "AQULINOX", "Aqulinox", "VODA / SÍLA",
            desc = "Finální forma. Hydro pumpa na maximum. Svaly nabyté vodou a silou.",
            hint = "Aqulind se vyvine na levelu 10. Vytrvalost a hydratace jsou klíčem.",
            stats = Stats(hp = 79, atk = 83, def = 100, spd = 78),
            moves = { listOf(attackWaterPulse(), attackAquaTail(), attackHydroPump()) },
            spawns = listOf(Spawn(EPIC, listOf(WATER), listOf(Conditions.MinCheckInCount(7)))),
            family = DropFamily.WATER,
            battleHeight = 38f
        ),

        // 07 - Flori (jelínek s listovou korunou)
        Species("007", "FLORI", "Flori", "PŘÍRODA / STARTER",
            desc = "Jelínek s listovou korunou. Zelenina a vláknina jsou tvoji přátelé!",
            hint = "Flori tě čeká od samého začátku. Vyber si ho jako svého startéra!",
            stats = Stats(hp = 45, atk = 49, def = 49, spd = 45),
            moves = { listOf(attackTackle(), attackGrowl()) },
            evolves = Evolve("008", 4),
            spawns = listOf(Spawn(COMMON, listOf(TOWN))),
            family = DropFamily.GRASS
        ),

        // 08 - Florind (střední evoluce)
        Species("008", "FLORIND", "Florind", "PŘÍRODA / RŮST",
            desc = "Střední evoluce Floriho. Rostlinná energie pro každodenní výkon.",
            hint = "Flori se vyvine na levelu 4. Jez více zeleniny a ovoce!",
            stats = Stats(hp = 60, atk = 62, def = 63, spd = 60),
            moves = { listOf(attackTackle(), attackVineWhip(), attackRazorLeaf()) },
            evolves = Evolve("009", 10),
            spawns = listOf(Spawn(RARE, listOf(MEADOW))),
            family = DropFamily.GRASS
        ),

        // 09 - Florindra (finální stromová forma)
        Species("009", "FLORINDRA", "Florindra", "PŘÍRODA / MOUDROST",
            desc = "Finální forma. Stromový duch harmonie. Dlouhodobé zdraví těla i mysli.",
            hint = "Florind se vyvine na levelu 10. Konzistentnost je tvoje největší zbraň.",
            stats = Stats(hp = 80, atk = 82, def = 83, spd = 80),
            moves = { listOf(attackRazorLeaf(), attackLeafBlade(), attackSolarBeam()) },
            spawns = listOf(Spawn(EPIC, listOf(MEADOW), listOf(Conditions.MinCheckInCount(7)))),
            family = DropFamily.GRASS,
            battleHeight = 36f
        ),

        // 10 - Umbex (temná kulička – smutná ale dobrá)
        Species("010", "UMBEX", "Umbex", "DUCH / TEMNÝ",
            desc = "Shluk špatných vzpomínek a smutku. Vznikne jen z lásky a ztráty. Uvnitř dobrý.",
            hint = "Umbex se toulá pouze v noci. Zkus večerní trénink po 19:00.",
            stats = Stats(hp = 30, atk = 14, def = 12, spd = 16, level = 8),
            moves = { listOf(
                attackShadowBall(),
                attackLick(),
                attackNightShade(),
                Move("SPITE", MakromonType.GHOST, 0, 100, 10, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_ATK))
            ) },
            spawns = listOf(Spawn(EPIC, listOf(MOUNTAINS), listOf(Conditions.NIGHT_ONLY))),
            family = DropFamily.GHOST,
            catchRate = 0.7f
        ),

        // 11 - Lumex (světlá kulička – zářivá ale zlá)
        Species("011", "LUMEX", "Lumex", "DUCH / SVĚTLO",
            desc = "Protějšek Umbexe. Zářivý zvenku, ale uvnitř skrývá temné záměry.",
            hint = "Lumex je velmi vzácný a toulá se pouze v noci.",
            stats = Stats(hp = 28, atk = 16, def = 10, spd = 20, level = 8),
            moves = { listOf(
                attackDazzlingGleam(),
                attackHex(),
                attackCharm(),
                Move("DARK PULSE", MakromonType.GHOST, 80, 100, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.FLINCH, 20))
            ) },
            spawns = listOf(Spawn(LEGENDARY, listOf(MOUNTAINS), listOf(Conditions.NIGHT_ONLY))),
            family = DropFamily.GHOST,
            catchRate = 0.7f
        ),

        // 12 - Spirra (béžová veverka – základ)
        Species("012", "SPIRRA", "Spirra", "NORMÁLNÍ / ZÁKLAD",
            desc = "Béžová veverka se spirálovým ocasem. Základní forma plná potenciálu.",
            hint = "Spirra je nejčastější Makromon. Hledej ji všude kolem sebe!",
            stats = Stats(hp = 35, atk = 40, def = 35, spd = 55),
            moves = { listOf(attackTackle(), attackGrowl()) },
            spawns = listOf(Spawn(COMMON, listOf(MEADOW)))
        ),

        // 13 - Flamirra (ohnivá veverka)
        Species("013", "FLAMIRRA", "Flamirra", "OHEŇ / EVOLUCE",
            desc = "Ohnivá evoluce Spirry. Zlatooranžová spirála spaluje tuky jako šílená. Má ráda, když se pořádně zapotíš – Spirra se v ni promění, když spolu spálíte hodně kalorií pohybem.",
            hint = "Vyvine se ze Spirry, která s tebou pořádně zapotí.",
            stats = Stats(hp = 38, atk = 55, def = 35, spd = 65),
            moves = { listOf(attackTackle(), attackEmber(), attackFireFang()) },
            spawns = listOf(Spawn(RARE, listOf(MEADOW))),
            family = DropFamily.FIRE
        ),

        // 14 - Aquirra (vodní veverka)
        Species("014", "AQUIRRA", "Aquirra", "VODA / EVOLUCE",
            desc = "Vodní evoluce Spirry. Teal modrá spirála pro dokonalou hydrataci. Má ráda, když se pořádně napiješ – Spirra se v ni promění, když spolu vypijete spoustu vody.",
            hint = "Vyvine se ze Spirry, se kterou se pořádně napiješ.",
            stats = Stats(hp = 42, atk = 45, def = 50, spd = 55),
            moves = { listOf(attackWaterGun(), attackTackle(), attackBubbleBeam()) },
            spawns = listOf(Spawn(RARE, listOf(WATER))),
            family = DropFamily.WATER
        ),

        // 15 - Verdirra (grass veverka)
        Species("015", "VERDIRRA", "Verdirra", "PŘÍRODA / EVOLUCE",
            desc = "Travní evoluce Spirry. Zelená spirála pro sílu ze země. Má ráda, když má vláknina svou míru – Spirra se v ni promění po pár dnech v řadě se správnou dávkou vlákniny.",
            hint = "Vyvine se ze Spirry, se kterou jíš zeleninu den co den.",
            stats = Stats(hp = 40, atk = 48, def = 45, spd = 50),
            moves = { listOf(attackTackle(), attackVineWhip(), attackRazorLeaf()) },
            spawns = listOf(Spawn(RARE, listOf(MEADOW))),
            family = DropFamily.GRASS
        ),

        // 16 - Shadirra (dark/ghost veverka)
        Species("016", "SHADIRRA", "Shadirra", "DUCH / EVOLUCE",
            desc = "Temná evoluce Spirry. Fialová spirála skrývající tajemství noci. Má ráda, když si večer zdravě zamlsáš – Spirra se v ni promění po řadě nočních svačinek.",
            hint = "Vyvine se ze Spirry, se kterou si večer zdravě zamlsáš.",
            stats = Stats(hp = 35, atk = 50, def = 30, spd = 65),
            moves = { listOf(attackTackle(), attackShadowBall(), attackLick(), attackWillOWisp()) },
            spawns = listOf(Spawn(EPIC, ALL_WILD, listOf(Conditions.NIGHT_ONLY))),
            family = DropFamily.GHOST
        ),

        // 17 - Charmirra (fairy veverka)
        Species("017", "CHARMIRRA", "Charmirra", "VÍLA / EVOLUCE",
            desc = "Fairy evoluce Spirry. Růžová pastelová spirála plná šarmu. Má ráda dlouhé procházky – Spirra se v ni promění, když spolu nachodíte spoustu kroků.",
            hint = "Vyvine se ze Spirry, se kterou nachodíš spoustu kilometrů.",
            stats = Stats(hp = 38, atk = 42, def = 42, spd = 55),
            moves = { listOf(attackTackle(), attackCharm(), attackDazzlingGleam()) },
            spawns = listOf(Spawn(RARE, listOf(MEADOW))),
            family = DropFamily.FAIRY
        ),

        // 18 - Glacirra (ledová veverka)
        Species("018", "GLACIRRA", "Glacirra", "LED / EVOLUCE",
            desc = "Ledová evoluce Spirry. Chladná a precizní jako tvůj tréninkový plán. Má ráda, když poctivě dřeš v posilovně – Spirra se v ni promění po spoustě zapsaných sérií.",
            hint = "Vyvine se ze Spirry, se kterou poctivě dřeš v posilovně.",
            stats = Stats(hp = 40, atk = 45, def = 48, spd = 50),
            moves = { listOf(
                attackTackle(),
                Move("ICE SHARD", MakromonType.NORMAL, 40, 100, 30),
                Move("BLIZZARD",  MakromonType.WATER,  110, 70, 5)
            ) },
            spawns = listOf(Spawn(EPIC, ALL_WILD)),
            family = DropFamily.WATER
        ),

        // 19 - Drakirra (dragon veverka – skrytá evoluce)
        Species("019", "DRAKIRRA", "Drakirra", "DRAK / SKRYTÁ EVOLUCE",
            desc = "Tajemná dračí evoluce Spirry. Zlatá a teal spirála plná prastaré síly.",
            hint = "Tajná evoluce Spirry. Ani Spirra neví, jak se jí stát – zatím ji jde jen ulovit.",
            stats = Stats(hp = 45, atk = 60, def = 45, spd = 60),
            moves = { listOf(attackDragonBreath(), attackDragonClaw(), attackOutrage()) },
            spawns = listOf(Spawn(LEGENDARY, ALL_WILD, listOf(Conditions.MinCheckInCount(30)))),
            family = DropFamily.DRAGON,
            catchRate = 0.55f,
            battleHeight = 34f
        ),

        // 20 - Finlet (malá akvarijní rybka)
        Species("020", "FINLET", "Finlet", "VODA / SLABÝ",
            desc = "Malá průhledná rybka. Každý začátek je malý, ale i Finlet může být mocný.",
            hint = "Finlet je velmi běžný. Hledej ho u vodních zdrojů.",
            stats = Stats(hp = 20, atk = 8, def = 8, spd = 40),
            moves = { listOf(attackWaterGun(), attackTackle()) },
            evolves = Evolve("021", 8),
            spawns = listOf(Spawn(COMMON, listOf(WATER))),
            family = DropFamily.WATER
        ),

        // 21 - Serpfin (obří rybohadí monstrum)
        Species("021", "SERPFIN", "Serfin", "VODA / SÍLA",
            desc = "Obří rybohadí monstrum. Důkaz že konzistence přináší brutální transformaci.",
            hint = "Finlet se vyvine na levelu 8. Věř procesu!",
            stats = Stats(hp = 65, atk = 25, def = 20, spd = 15),
            moves = { listOf(attackAquaTail(), attackHydroPump(), attackSandAttack()) },
            spawns = listOf(Spawn(RARE, listOf(WATER), listOf(Conditions.MinCheckInCount(3)))),
            family = DropFamily.WATER,
            catchRate = 0.65f,
            battleHeight = 42f
        ),

        // 22 - Mycit (malá koloniální myška s krystalky)
        Species("022", "MYCIT", "Mycit", "NORMÁLNÍ / KOLONIE",
            desc = "Malá myška s krystalky. Žijí v koloniích a staví primitivní domečky.",
            hint = "Mycit je velmi běžný. Hledej ho na okrajích lesů a luk.",
            stats = Stats(hp = 28, atk = 35, def = 30, spd = 45),
            moves = { listOf(
                attackTackle(), attackSleepPowder(),
                Move("CRYSTAL SHARD", MakromonType.NORMAL, 35, 100, 30)
            ) },
            evolves = Evolve("023", 7),
            spawns = listOf(Spawn(COMMON, listOf(MEADOW)))
        ),

        // 23 - Mydrus (druidský vůdce, pozřený jedem)
        Species("023", "MYDRUS", "Mydrus", "JED / DRUID",
            desc = "Druidský vůdce kolonie. Pozřen jedem, ale imunní. Chrání své bratry.",
            hint = "Mydrus se vyvine z Mycita na levelu 7. Po 5 check-inech ho najdeš.",
            stats = Stats(hp = 55, atk = 60, def = 50, spd = 35),
            moves = { listOf(
                attackPoisonSting(),
                attackSludgeBomb(),
                Move("TOXIC AURA", MakromonType.POISON, 70, 90, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.POISON, 30)),
                attackToxic()
            ) },
            spawns = listOf(Spawn(EPIC, listOf(MEADOW), listOf(Conditions.MinCheckInCount(5)))),
            catchRate = 0.65f,
            battleHeight = 32f
        ),

        // 24 - Soulu
        Species("024", "SOULU", "Soulu", "DUCH / ZÁRODEK",
            desc = "Malá roztomilá duše. Ještě se učí ovládat svůj průhledný obleček.",
            hint = "Soulu se toulá v noci. Cvič po 19:00 a třeba ho potkáš!",
            stats = Stats(hp = 25, atk = 12, def = 8, spd = 18),
            moves = { listOf(attackLick(), attackNightShade(), attackConfuseRay()) },
            evolves = Evolve("025", 5),
            spawns = listOf(Spawn(RARE, ALL_WILD, listOf(Conditions.NIGHT_ONLY))),
            family = DropFamily.GHOST
        ),

        // 25 - Soulex
        Species("025", "SOULEX", "Soulex", "DUCH / STŘEDNÍ",
            desc = "Obleček začíná sedět, ale stále uniká kontrole. Roste v síle.",
            hint = "Soulu se vyvine na levelu 5. Noční trénink urychlí jeho růst.",
            stats = Stats(hp = 35, atk = 16, def = 12, spd = 22),
            moves = { listOf(attackShadowPunch(), attackLick(), attackHypnosis()) },
            evolves = Evolve("026", 10),
            spawns = listOf(Spawn(EPIC, ALL_WILD, listOf(Conditions.NIGHT_ONLY))),
            family = DropFamily.GHOST
        ),

        // 26 - Soulord
        Species("026", "SOULORD", "Soulord", "DUCH / MISTR",
            desc = "Finální forma. Duše v dokonalém obleku. Ovládá prostor i čas.",
            hint = "Soulex se vyvine na levelu 10. Po 20 nočních check-inech!",
            stats = Stats(hp = 50, atk = 22, def = 18, spd = 28),
            moves = { listOf(attackShadowBall(), attackShadowPunch(), attackHex(), attackPsychic()) },
            spawns = listOf(Spawn(LEGENDARY, ALL_WILD, listOf(Conditions.MinCheckInCount(20), Conditions.NIGHT_ONLY))),
            family = DropFamily.GHOST,
            catchRate = 0.6f,
            battleHeight = 38f
        ),

        // 27 - Phantil
        Species("027", "PHANTIL", "Phantil", "VODA / DUCH",
            desc = "Malá průsvitná duch-ryba. Lehká jako pára nad hladinou.",
            hint = "Phantil se toulá v noci u vodních ploch.",
            stats = Stats(hp = 22, atk = 10, def = 8, spd = 20),
            moves = { listOf(attackTackle(), attackWaterGun()) },
            evolves = Evolve("028", 6),
            spawns = listOf(Spawn(RARE, ALL_WILD, listOf(Conditions.NIGHT_ONLY))),
            family = DropFamily.GHOST
        ),

        // 28 - Phantius
        Species("028", "PHANTIUS", "Phantius", "VODA / DUCH",
            desc = "Větší a temnější. Duchový opar kolem jeho těla houstne.",
            hint = "Phantil se vyvine na levelu 6. Noční trénink je klíčem.",
            stats = Stats(hp = 38, atk = 16, def = 14, spd = 25),
            moves = { listOf(attackWaterPulse(), attackShadowBall(), attackBite()) },
            evolves = Evolve("029", 12),
            spawns = listOf(Spawn(EPIC, listOf(WATER), listOf(Conditions.NIGHT_ONLY))),
            family = DropFamily.GHOST
        ),

        // 29 - Phantiax
        Species("029", "PHANTIAX", "Phantiax", "VODA / DUCH",
            desc = "Obří duch-mořský drak. Vznáší se nad hladinou a ovládá přílivy.",
            hint = "Phantius se vyvine na levelu 12. Po 20 check-inech!",
            stats = Stats(hp = 60, atk = 25, def = 20, spd = 30),
            moves = { listOf(attackHydroPump(), attackShadowBall(), attackDragonPulse(), attackHex()) },
            spawns = listOf(Spawn(LEGENDARY, ALL_WILD, listOf(Conditions.MinCheckInCount(20)))),
            family = DropFamily.GHOST,
            catchRate = 0.6f,
            battleHeight = 36f
        ),

        // 30 - Gudwin (tlustý medvěd s váčkem – moudrý rádce, ale i bojovník)
        Species("030", "GUDWIN", "Gudwin", "NORMÁLNÍ / MOUDROST",
            desc = "Tlustý medvěd s váčkem přes rameno. Moudrý rádce a zkušený bojovník.",
            hint = "Gudwin vychází ven až po 7 poctivých check-inech. Ranní rituál je klíčem.",
            stats = Stats(hp = 55, atk = 14, def = 18, spd = 8, level = 10),
            moves = { listOf(
                Move("BODY SLAM",  MakromonType.NORMAL, 85, 85, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.PARALYZE, 30)),
                attackTackle(),
                // Ukolébavka uspí (dřív snižovala útok)
                Move("LULLABY",    MakromonType.NORMAL,  0, 80, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.SLEEP)),
                Move("WISE WORDS", MakromonType.NORMAL,  0, 100, 20, statEffect = StatEffect.LOWER_ENEMY_DEF)
            ) },
            spawns = listOf(
                Spawn(EPIC, listOf(MEADOW), listOf(Conditions.MinCheckInCount(7))),
                // TEST (docs/adr/0040): Gudwin dočasně i v městském křoví – zkouška dropu Makromonovy sekery a krumpáče. Pak smazat!
                Spawn(COMMON, listOf(TOWN))
            ),
            catchRate = 0.75f,
            battleHeight = 42f
        ),

        // 31 - Axlu (růžový axolotl – tvář aplikace)
        Species("031", "AXLU", "Axlu", "VODA / VÍLA",
            desc = "Růžový axolotl, tvář Makroflow. Vzácný, roztomilý a neuvěřitelně odolný.",
            hint = "Axlu je extrémně vzácný. Říká se, že se zjeví jen těm nejdisciplinovanějším – 50 check-inů!",
            stats = Stats(hp = 35, atk = 12, def = 12, spd = 15, level = 5),
            moves = { listOf(
                attackWaterGun(),
                attackTackle(),
                attackCharm(),
                Move("REGENERATE", MakromonType.NORMAL, 0, 100, 10, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.RAISE_DEF))
            ) },
            spawns = listOf(Spawn(MYTHIC, ALL_WILD, listOf(Conditions.MinCheckInCount(50)))),
            catchRate = 0.3f,
            battleHeight = 30f
        ),

        // 32 - Mysnic (horská myška s kamenným štítem na zádech, docs/adr/0061)
        Species("032", "MYSNIC", "Mysnic", "ZEMĚ / OBRANA",
            desc = "Horská myška, která na zádech nosí plochý kámen jako štít. Když se lekne, schová se pod něj. Vytrvalost nad rychlost.",
            hint = "Mysnic pobíhá po kamenitých stezkách v Horách. Je tam nejčastější.",
            stats = Stats(hp = 42, atk = 30, def = 55, spd = 32),
            moves = { listOf(
                Move("STONE TOSS", MakromonType.GROUND, 40, 95, 30),
                attackHarden(),
                attackSandAttack()
            ) },
            evolves = Evolve("033", 10),
            spawns = listOf(Spawn(COMMON, listOf(MOUNTAINS)))
        ),

        // 33 - Mysnor (vývoj Mysnica: kamenný štít mu srostl s hřbetem, docs/adr/0063)
        Species("033", "MYSNOR", "Mysnor", "ZEMĚ / PEVNOST",
            desc = "Kamenný štít mu srostl s hřbetem. Nehne se z místa, ani když do něj narazí lavina. Trpělivost je taky síla.",
            hint = "Mysnor se vyvine z Mysnica na levelu 10. Vzácně ho potkáš i v Horách.",
            stats = Stats(hp = 70, atk = 50, def = 85, spd = 36),
            moves = { listOf(
                Move("SHELL SLAM", MakromonType.GROUND, 60, 95, 20),
                Move("BOULDER GUARD", MakromonType.GROUND, 0, 100, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.RAISE_DEF)),
                Move("ROCK SLIDE", MakromonType.GROUND, 75, 90, 10)
            ) },
            spawns = listOf(Spawn(RARE, listOf(MOUNTAINS)))
        ),

        // strážce krystalu (docs/adr/0056, 0063): statistiky a útoky bere od #003
        Species("034", "IGNILEO", "Ignileo", "OHEŇ / STRÁŽCE",
            desc = "Ohnivý lev s korunou z plamenů. Strážce rudého krystalu v hlubinách Starého dolu. Nevzdává se a chytit nejde.",
            hint = "Hlídá rudý krystal v hlubinách Starého dolu. Do Makrodexu se zapíše, až ho porazíš.",
            guardianOf = "003",
            family = DropFamily.FIRE,
            battleHeight = 42f
        ),

        // strážce krystalu (docs/adr/0056, 0063): statistiky a útoky bere od #021
        Species("035", "AQUAVULP", "Aquavulp", "VODA / STRÁŽCE",
            desc = "Liška z vodního víru. Hlídá modrý krystal u podzemního jezírka a voda kolem ní nikdy nepřestane kroužit. Chytit nejde.",
            hint = "Hlídá modrý krystal u podzemního jezírka v Mechové jeskyni. Do Makrodexu se zapíše, až ji porazíš.",
            guardianOf = "021",
            family = DropFamily.WATER,
            battleHeight = 36f
        ),

        Species("036", "TYNAFI", "Tynafi", "OHEŇ / KRASAVICE",
            desc = "Malý ohnivý tvor plný energie, který bydlí někde v Příbrami i privilegovaný rodiny zrzků.",
            hint = "Potkáš ho v divočině.",
            stats = Stats(hp = 32, atk = 14, def = 8, spd = 15),
            moves = { listOf(
                attackTackle(),
                attackFireFang(),
                attackFuryAttack(),
                attackCharm()
            ) },
            evolves = Evolve("037", 8),
            spawns = listOf(Spawn(COMMON, ALL_WILD))
        ),

        Species("037", "TYNAFIOR", "Tynafior", "OHEŇ / SÍLA",
            desc = "Vyvinutá forma Tynafiho chrlící žár.",
            hint = "Vyvine se z Tynafiho na levelu 8.",
            stats = Stats(hp = 60, atk = 20, def = 30, spd = 20, level = 8),
            moves = { listOf(
                attackTackle(),
                attackFlamethrower(),
                attackFuryAttack(),
                attackHeatWave()
            ) },
            spawns = listOf(Spawn(COMMON, ALL_WILD))
        ),

        Species("038", "JOHNSOVA", "Johnsova", "BLESK / HORY",
            desc = "Horská blesková sovička. Jakmile uslyšíš houknutí, můžeš očekávat, že tě brzy trefí blesk.",
            hint = "Hledej v Horách.",
            stats = Stats(hp = 35, atk = 12, def = 12, spd = 15),
            moves = { listOf(
                attackSandAttack(),
                attackThunderbolt(),
                attackScratch(),
                attackConfuseRay()
            ) },
            evolves = Evolve("039", 10),
            spawns = listOf(Spawn(COMMON, listOf(MOUNTAINS)))
        ),

        // 39 - Grifben (vývoj Johnsovy: sovička dorostla v gryfa)
        Species("039", "GRIFBEN", "Grifben", "BLESK / LÉTAJÍCÍ",
            desc = "Z malé sovičky vyrostl horský gryf s vějířem bleskových per. Než udeří, peří se mu naježí a vzduch zapraská.",
            hint = "Grifben se vyvine z Johnsovy na levelu 10. Vzácně krouží i nad Horami.",
            stats = Stats(hp = 62, atk = 24, def = 22, spd = 26, level = 10),
            moves = { listOf(
                attackWingAttack(),
                attackThunderbolt(),
                attackSlash(),
                attackLeer()
            ) },
            type = MakromonType.ELECTRIC,
            spawns = listOf(Spawn(RARE, listOf(MOUNTAINS))),
            catchRate = 0.6f,
            battleHeight = 40f
        ),

        Species("040", "LUMIVIX", "Lumivix", "VÍLA / KRYSTAL",
            desc = "Liščátko s křídly z růžového krystalu a kamínkem na hrudi. Kde proběhne, zůstanou ve vzduchu jiskřičky. Prý má i temnou podobu.",
            hint = "Potkáš ho v divočině.",
            stats = Stats(hp = 35, atk = 12, def = 12, spd = 15),
            moves = { listOf(
                attackTackle(),
                attackCharm(),
                attackBite()
            ) },
            spawns = listOf(Spawn(COMMON, ALL_WILD)),
            type = MakromonType.FAIRY,
            family = DropFamily.FAIRY
        ),

        // 41 - Psychirra (psychická veverka – evoluce Spirry, docs/adr/0083)
        Species("041", "PSYCHIRRA", "Psychirra", "PSYCHO / EVOLUCE",
            desc = "Psychická evoluce Spirry. Ocas se jí stočil do hvězdné spirály a třetí oko na čele vidí, jak ses vyspal. Má ráda klidná rána – Spirra se v ni promění, když spolu každé ráno uděláte check-in.",
            hint = "Vyvine se ze Spirry, se kterou každé ráno uděláš check-in.",
            stats = Stats(hp = 38, atk = 52, def = 36, spd = 62),
            moves = { listOf(attackTackle(), attackPsychic(), attackHypnosis()) },
            type = MakromonType.PSYCHIC,
            spawns = listOf(Spawn(EPIC, ALL_WILD, listOf(Conditions.NIGHT_ONLY))),
            family = DropFamily.FAIRY
        )

    ) }

    private val byId: Map<String, Species> = ALL.associateBy { it.id }
    private val byName: Map<String, Species> = ALL.associateBy { it.name }

    fun byId(id: String): Species? = byId[id]
    fun byName(name: String): Species? = byName[name]

    /** Druhy, které se dají mít (bez strážců). */
    val PLAYABLE: List<Species> get() = ALL.filter { it.guardianOf == null }

    /** Neznámé číslo → Spirra (bezpečný fallback jako dřív). */
    fun create(id: String): Makromon = (byId[id] ?: byId.getValue("012")).create()
}
