# 0014 – Příběh krystalů: strážci, inventář, svatyně a legenda

**Stav:** přijato · **Datum:** 2026-09-24 · navazuje na [0013](0013-jeskyne-kamera-krystaly.md)

## Kontext
Krystaly na konci jeskyní (0013) šly sebrat hned. Nově má být:
1. krystal hlídaný strážcem (lvl 12, Makromon s hotovým spritem) – vzít jde až po jeho porážce,
2. krystal jako předmět v inventáři s popiskem,
3. na vrcholu Hor oba vložit do svatyně (animace) → souboj s legendou lvl 80, která hráče
   porazí a uletí, a tím otevře průchod dál (další lokace zatím není součástí).

## Rozhodnutí
* **Zvláštní souboje** `SpecialBattle` (čistý Kotlin): `BOSS_BLUE` = Serpfin (had podzemního
  jezírka v Mechové jeskyni), `BOSS_RED` = Ignaroth (oheň v hlubinách Starého dolu),
  `LEGEND_PEAK` = Drakirra lvl 80. Mapa zapíše `SPECIAL_BATTLE` do GamePrefs, fragment
  souboje ho přečte a hned smaže (platí jen pro jeden souboj) a předá ho `PokemonBattleView`.
  Souboj pak: pevný Makromon a level, vlastní úvodní hláška (GUARDIAN / LEGENDARY),
  **nejde chytit** (ball se nespotřebuje) ani **utéct**, žádná shiny kostka.
* **Legenda je neporazitelná** (`clampEnemyHp`: HP neklesne pod 1, platí pro útoky, zmatení
  i otravu). Když hráčův Makromon padne: „řev“ → legenda zmizí → „odletěla“; zapíše se
  `legend_faced`. Zavření souboje tlačítkem CLOSE nic nezapíše – legendu lze vyvolat znovu.
* **Stav příběhu** `LegendProgress` = odvozený z příznaků v GamePrefs
  (`boss_defeated_*`, `crystal_*` = sebráno, `crystals_placed`, `legend_faced`) a z počtu
  předmětů `crystal_blue` / `crystal_red` v tabulce `user_items` (ta se už synchronizuje
  přes Firebase). Stav oltáře: `GUARDED → CRYSTAL_READY → EMPTY`; svatyně:
  `NeedCrystals → ReadyToPlace → LegendAwaits → GateOpen`. Mapa se podle něj překreslí po
  změně biomu i po návratu ze souboje.
* **Na mapě:** strážce stojí před oltářem (sprite, stín, pomalé „dýchání“), krystal se vznáší
  **1 art pixel nad podstavcem** (dřív výš + dekorace se počítaly ze šířky světa, která
  mohla být v tu chvíli ještě stará → posun; teď se bere stejný celočíselný násobek jako
  kamera). Po sebrání krystal vletí do hráče a přibude do inventáře (ikona + popis v dialogu).
* **Svatyně na vrcholu** je vykreslená přímo v `mountains.png` (generátor): podesta, tělo
  se dvěma prázdnými lůžky a runou, za ní zapečetěná brána ve skále. Uzel `peak` se posunul
  před svatyni a přestal být místem setkání. Dynamické části (krystaly v lůžkách, otevřená
  brána) jsou pixel-art vrstvy v aplikaci; souřadnice v `PeakShrine` = zdroj pravdy spolu
  s generátorem.
* **Obřad vložení:** oba krystaly vyletí od hráče obloukem s rotací do lůžek, rozzáří se,
  země se otřese, ze svatyně vyšlehne paprsek k nebi, bílý záblesk → souboj s legendou.
  Předměty se spotřebují až tady; během obřadu se na mapu neklepe.

## Důsledky
* + Celá logika příběhu je testovaná bez Androidu (stavy, klíče, hlášky se vejdou do okna).
* + Když aplikace spadne uprostřed, nic se neztratí: vložené krystaly = `LegendAwaits` →
  klepnutím na svatyni se legenda probudí znovu.
* − Příznaky postupu jsou v GamePrefs (lokálně); předměty se synchronizují, příznaky ne –
  stejné jako ostatní herní příznaky, řešit spolu se synchronizací herního stavu.
* − Legenda používá sprite Drakirry, kterou lze (vzácně) potkat i divoce; vlastní sprite
  legendy je snadná výměna v `SpecialBattle.LEGEND_PEAK`.
