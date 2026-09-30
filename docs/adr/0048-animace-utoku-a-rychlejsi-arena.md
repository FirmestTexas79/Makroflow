# 0048 – Animace útoků, rychlejší aréna a třpytky u roztržených listů

**Stav:** návrh (větev `STORY`) · **Datum:** 2026-09-30

## Kontext
Připomínky z testování:
1. Roztržený list na mapě nebyl vidět, hráč nevěděl, kam klepnout.
2. Po intru souboje byla 2–3 s jen plochá barva, než se dopočítala 3D aréna.
3. Všechny útoky měly jen bílý záblesk přes obrazovku.

## Rozhodnutí
* **Třpytky u listů** (`placePageSparkles`):
  * Kde čeká nenalezený list, leží na mapě kousek papíru (stejná pixelová ikona jako v batohu), lehce se pohupuje, dýchá zlatou září a kolem něj se střídavě rozsvěcují čtyři hvězdičky.
  * U míst s oblastí klepnutí sedí přímo na objektu (dub, studánka, mužík), jinak kousek nad uzlem.
  * Po nalezení zmizí.
* **Rychlejší aréna** (`VoxelRenderer`):
  * Obraz se dělí na vodorovné pásy a každé vlákno (až 8) rasterizuje všechny polygony jen do svého pásu a hned ho stínuje. Pásy se nepřekrývají, takže to jde bez zámků. Stínová mapa stejně.
  * Výsledek je bit po bitu stejný jako v jednom vlákně (test `parallelRenderIsIdenticalToSingleThread`).
  * Po dopočítání se aréna 250 ms prolne přes náhradní barvy místo skoku.
* **Animace útoků** (`battlefx/MoveAnims.kt`, čistá logika, a kreslení v `PokemonBattleView`):
  * 19 stylů:

    | Styl | Útoky |
    |---|---|
    | výpad a náraz | TACKLE, BODY SLAM… |
    | zmizení a útok zespodu | DIG, PHANTOM FORCE |
    | čelisti | BITE, FIRE/ICE FANG |
    | škrábance | SLASH, DRAGON CLAW, LEAF BLADE |
    | střely obloukem | EMBER, SHADOW BALL, SEED BOMB… |
    | paprsky | FLAMETHROWER, HYDRO PUMP, SOLAR BEAM… |
    | blesk shora | THUNDERBOLT |
    | putující vír | GUST |
    | roj listů | RAZOR LEAF, PETAL DANCE |
    | vánice | BLIZZARD, HEAT WAVE |
    | otřes země | EARTHQUAKE |
    | šlahouny | VINE WHIP, STRING SHOT |
    | vlny | GROWL, PSYCHIC, DARK PULSE… |
    | oblak | SLEEP POWDER, POISON GAS… |
    | záře kolem útočníka | HARDEN, DRAGON DANCE… |
    | vysávání | SOUL DRAIN |
    | bludičky | HEX, WILL-O-WISP |
    | zamrzání | FREEZE-DRY |
    | srdíčka a noty | CHARM, LULLABY |

  * Každý útok má vlastní barvy, počet částic a délku.
  * Tvary částic:
    * kruh, čtverec, čára, prstenec;
    * hvězda, list, srdce, nota;
    * plamen, zub.
  * Neznámý útok dostane výchozí animaci podle typu a síly.
  * V okamžiku zásahu:
    * krátký slabý záblesk;
    * cíl ~0,4 s bliká a zatřese se;
    * silné útoky otřesou celou obrazovkou;
    * některé zabarví scénu (tma u DARK PULSE, bílá u BLIZZARDU).
  * Stavové útoky (bez zranění) mají taky animaci, efekt přijde po ní.
  * `MoveAnimsTest` hlídá:
    * každý útok ve hře má animaci;
    * snímky nevyjedou mimo obrazovku, alpha je v rozsahu;
    * kolem zásahu se vždy něco kreslí;
    * výpad se vrátí zpět a DIG je pod zemí neviditelný;
    * GUST putuje k cíli;
    * stejný seed dá stejný snímek.

## Důsledky
* Nový útok stačí přidat do `MoveAnims.SPECS`, jinak dostane výchozí animaci. Test ho připomene.
