# 0045 – Příběh Hvozdu: druid Mydrus, Rudá hniloba a Srdce Hvozdu

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-27

## Kontext
Brána světů na Nebeském průsmyku (ADR 0044) čeká na Srdce Hvozdu. Hvozd měl zatím jen souboje, kácení a prázdnou mýtinu s hláškou „tady příběh teprve začne“. Chceme navázat na legendu (Drakirra odletěla nad Hvozd), přidat víc lore a quest, který propojí herní svět s funkční částí aplikace i se všemi dovednostmi.

## Lore
* **Drakirra** po probuzení přeletěla nad Hvozdem. Z jejích křídel padal žhavý popel a z něj vyrašila **Rudá hniloba** – rudé houby, které pijí z kořenů život. Jsou to ty houby na pařezu na mýtině.
* **Mydrus**, poslední druid z rodu Mycitů, se hnilobu snažil zastavit a nakazil se. Fialové skvrny na jeho srsti jsou hniloba.
* **Starý dub** pamatuje doby, kdy mezi světy vedly brány a hlídali je draci. V jeho dřeni zraje **Srdce Hvozdu**, klíč, kterým druidi kdysi zapečetili Bránu světů.
* V kořenech dubu se usadil **Soulord, pán hniloby**, a pije světlo Srdce.

## Rozhodnutí
* **Quest `forest_heart`** (kapitola „IV · HVOZD“ v deníku) vede Mydrus z mýtiny. Mýtina se ožije až po souboji s legendou (`legend_faced`), do té doby tam les jen šeptá.

  | # | Fáze | Úkol | Typ |
  |---|---|---|---|
  | 0 | Druid z mýtiny | prohlédnout tiché jezírko, houštinu a Starý dub | VISIT_NODE |
  | 1 | Živá voda | vypít dnes **osobní** cíl vody | HIT_WATER (nový) |
  | 2 | Zdivočelí | vyhrát 4 souboje ve Hvozdu | BATTLE_BIOME `FOREST` |
  | 3 | Léčivý odvar | přinést Mydrusovi 5 modrých bobulí, 5 březových polen, 5 suchých listů | DELIVER_ITEMS (nový) |
  | 4 | Po kořenech | ujít dnes 7000 kroků | WALK_STEPS |
  | 5 | Hlas Starého dubu | vyhnat Soulorda z kořenů dubu (boss, lvl 14) | STORY_FLAG (nový) |

  * **Mluvčí:** Mydrus mluví ve fázích 0–4, poslední fázi uvádí Starý dub (nový portrét). Po poslední fázi dub vydá Srdce Hvozdu (`QuestRewards`). Loučí se znovu Mydrus (`farewellSpeakerResId`).
  * **Suroviny odvaru** spojují tři dovednosti: pěstování (modrá bobule), kácení (bříza chce měděnou sekeru) a materiály z Makromonů.
* **Nové typy fází** (`QuestProgression`, `QuestManager`):
  * **HIT_WATER:** metadata = vypito v % osobního cíle vody (`MacroCalculator.water`).
    * Přepočítá se hned po zápisu vody.
    * K tomu slouží nový `WaterDao.observeTotalMlForDate` (jen dotaz, schéma DB se nemění).
  * **DELIVER_ITEMS:** `targetId = "itemId:počet,…"`.
    * Při oslovení NPC (až po úvodu fáze) se předměty odeberou, jen když má hráč všechny.
    * Jinak NPC vypíše, co chybí a kolik toho hráč má.
  * **STORY_FLAG:** fáze se splní, když je nastaven příznak příběhu (`StoryFlags`). Po návratu ze souboje mapa zavolá `questManager.recheck()`.
  * `QuestStage.hint` je vlastní krátká připomínka pro fázi.
  * `onBattleWon` bere i skutečnou lokaci (`location`), takže výhry ve Hvozdu se nepočítají jako louka.
* **Boss `SpecialBattle.FOREST_ROT`:**
  * Je to Soulord (026) na lvl 14 s hláškou „SOULORD ROSE FROM / THE ROTTEN ROOTS!“.
  * Výhra nastaví příznak `forest_rot_defeated` (nové pole `winKey`), který se synchronizuje.
  * Klepnutí na Starý dub ve fázi 5 spustí boss souboj místo divokého setkání.
* **Hvozd na mapě** (`placeForestStory`, data v `story/ForestHeart.kt`):
  * Mydrus u pařezu na mýtině se lehce pohupuje (oblast klepnutí mýtiny je větší, aby zahrnula i jeho).
  * Na šesti místech (jezírko, kmen v houštině, kořeny dubu, kruh hub, cesta) jsou fialové skvrny hniloby s rudými houbami a pulzující září.
  * Ve fázi 5 se u kořenů dubu vznáší průsvitný Soulord ve fialové auře.
  * Po vyhnání hniloby zmizí, u dubu se objeví zlatá záře a světlušky a na mýtinu se vrátí dva Mycité.
* **Srdce Hvozdu:**
  * Je to klíčový předmět v batohu (`srdce_hvozdu`) s vlastní pixelovou ikonou (jantarové semeno s lístkem) a popisem.
  * Když ho hráč u Brány světů má, spustí se obřad:
    1. semeno vyletí obloukem do lůžka;
    2. obrazovka zazáří zeleně;
    3. závoj a runy zezelenají a v lůžku zůstane jantar;
    4. nastaví se `forest_heart_placed`.
  * Tabule pak ukazuje otevřenou bránu (detail brány už obsahuje živý závoj a Srdce v lůžku, `SkyPassArt.gateDetail`).
* **Debug** (podržení deníku):
  * legenda odletěla, splnit fázi, dát Srdce Hvozdu, reset Hvozdu;
  * adb `--ei debug_quest_complete N`.

## Důsledky
* Region 2 začíná za otevřenou Branou světů. Tabule zatím říká, že cesta se teprve chystá.
* Hráč, který Srdce získal a přeinstaluje hru, ho má dál: předmět i příznaky se synchronizují.
* Testy:
  * `ForestQuestTest`: struktura questu, odvar, voda, připomínky, odměna a debug splnění pro každou fázi všech questů.
  * `ForestHeartTest`: pozice na mapě, skvrny hniloby, ikona a detail brány.
  * `SpecialBattleTest` pokrývá délku hlášek bosse.
