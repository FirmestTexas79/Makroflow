# 0080 – Rozdělení souboje, účinnost typů, herní oznámení, HUD Makrosvěta

Stav: přijato (2026-10-09), větev `MULTI`

- **Rozdělení `PokemonBattleView`**: z ~2000 řádků na jádro (~420 ř.) a rozšiřující funkce
  v `BattleRender.kt` (kreslení), `BattleTurns.kt` (tahy, hlášky účinnosti), `BattleOutcomes.kt`
  (výsledky, trenéři, rank, XP), `BattleFx.kt` (efekty), `BattleCatch.kt` (chytání).
  Členy jsou `internal`, aby k nim rozšíření dosáhla; `gsReady` nahrazuje `::gs.isInitialized`
  (z rozšíření nejde). Důvod: menší soubory = levnější úpravy a čitelnější diffy.
- **Tabulka typů** (`BattleEngine.CHART`): úplná pro 13 typů včetně imunit (×0).
  Při imunitě je poškození 0; hlášky „Je to super účinné!“, „Není to moc účinné…“, „Nemá to účinek…“.
- **`GameToast`**: dřevěná karta nahoře s ikonou, nadpisem a u XP s ukazatelem levelu; fronta,
  takže víc oznámení jde po sobě. Nahrazuje obyčejné `Toast` u XP, levelu, mincí a předmětů.
- **HUD Makrosvěta**: pixelové ikony (`ic_px_*`) na dřevěných tlačítkách (styl `MapHudButton`),
  sloupec týmu vpravo (čtverce, shiny přebarvení, klepnutí = aktivní Makromon).
- **Obrazovka Arény** (`ArenaUi`): erb ranku, barevné dlaždice režimů, stuhy sekcí, nástupová animace.
