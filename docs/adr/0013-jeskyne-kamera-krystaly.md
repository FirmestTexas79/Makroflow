# 0013 – Jeskyně v Horách: kamera, krystaly a mechový přechod

**Stav:** přijato · **Datum:** 2026-09-24

## Kontext
Hory mají dva vchody do skály – jeskyni vpravo nahoře (`cave`) a důl s výdřevou vlevo (`mine`).
Doteď byl `cave` jen místo setkání a `mine` sken čárového kódu. Nové lokace mají být:
otevřená jeskyně a uzavřené bludiště, obě větší než obrazovka s kamerou, která jede za postavou
jen po hranu obrázku, na konci každé krystal (modrý / červený) pro budoucí legendární souboj
a vlastní přechod „do tmavých jeskyní“ v tmavě modrém stylu s mechem.

## Rozhodnutí
1. **Dvě mapy z generátoru** `tools/mapgen/gen_caves.py` (vlastní pixel art, žádné cizí assety):
   * `cave_open` – *Mechová jeskyně* (240×400 art px): tři patra teras propojená schody,
     podzemní jezírko, svítící houby, modré krystalky. Vchod z uzlu `cave`.
   * `cave_maze` – *Starý důl* (240×440): štoly v bludišti, patra spojená žebříky, výdřeva,
     lucerny, koleje, vozík. Vchod z uzlu `mine` (výdřeva u vchodu v Horách sedí k dolu).
   Pohled 3/4 shora: výšková mapa (patra 0–2, masiv 9) → čela stěn/teras pod jižními hranami,
   hroudy masivu jako Voronoi buňky, mech u stěn a pramínky z hran, bodová světla s ditheringem.
2. **PNG v nativním rozlišení v `drawable-nodpi`.** Běžné `drawable/` by Android přepočítal
   podle hustoty (u xxhdpi 3× → desítky MB). Zvětšuje se až ve view celočíselným násobkem
   bez vyhlazení (`MapCamera.pixelScale`, cíl ~120 art px na šířku) → ostré, stejné pixely.
3. **Kamera = posun „světa“.** `mapWorld` (pozadí, postava, NPC, krystal) má v jeskyni velikost
   obrázku × násobek a kamera nastavuje `translationX/Y` z pozice postavy
   (`MapCamera.offset`: postava uprostřed, omezeno hranami, menší svět se vycentruje).
   `MovementEngine` dál pracuje v souřadnicích světa a jen hlásí posun (`onMoved`, i v každém
   snímku animace) – chůze, dvojklik ani hledání cesty se nemění. Běžné mapy mají svět =
   obrazovka a posun 0, chovají se přesně jako dřív.
4. **Klepnutí ve světě.** Pozice se přepočítá přes `mapWorld.getLocationOnScreen` (zahrnuje
   posun kamery) a dosah uzlu se měří v jednotkách obrazovky (`MapCamera.tapDistance`), takže
   zóna je všude stejně velká. Vybírá se *nejbližší* uzel v dosahu (v bludišti jsou uzly blízko).
   V jeskyních jsou klikatelné všechny uzly (rozcestí slouží k chůzi).
5. **Data jeskyní v čistém Kotlinu** (`CaveMaps`): uzly v art px, hrany, východ, uzel krystalu,
   místa setkání. `BiomeRegistry.graphOf` z nich staví navigační graf. Souřadnice jsou zdrojem
   pravdy v generátoru i v Kotlinu (stejně jako u Hor); test hlídá propojenost a že krystal je
   nejvzdálenější uzel od východu.
6. **Souboje v jeskyni jsou „horské“** (`battleBiome = MOUNTAINS`): intro s kameny, horští
   Makromoni a questy s výhrami v Horách počítají i jeskyně. Sken čárového kódu se přestěhoval
   na rudnou žílu (`tezba`) uvnitř dolu.
7. **Krystaly:** sprite z pixelů (`Crystals.pixels`), vznáší se po celých art pixelech, pulzující
   záře. Sebrání = `GamePrefs` `crystal_BLUE` / `crystal_RED`; `Crystals.legendaryUnlocked`
   (oba) je připravené pro legendární souboj – ten zatím není součástí.
8. **Přechod** `CaveTransitionScene` (čistý Kotlin, ~120 px na šířku): ústí v modrém kameni
   s mechovým lemem a visícím mechem roste exponenciálně, dokud nepokryje obrazovku
   (`COVERED_AT`, ověřeno testem pro různé poměry stran); pak let tunelem ze soustředných prstenců
   se sporami a ztmavení. Mapa se mění pod plným překryvem, překryv se pak rozplyne.
   Výstup je zrcadlový: ze tmy k teplému dennímu světlu, které vše přezáří. Během přechodu se
   klepnutí ignorují.

## Důsledky
* + Kamera nezasahuje do chůze ani do starých map; jeden mechanismus pro libovolné velké mapy.
* + Mapy mají ~110 kB a v paměti ~0,4 MB.
* − Souřadnice uzlů jsou ve dvou souborech (generátor a `CaveMaps`) – při úpravě změnit obojí.
* − Krystaly jsou jen v `SharedPreferences` (nesynchronizují se přes Firebase); stejně jako
  ostatní herní příznaky – synchronizace až s legendárním soubojem.
