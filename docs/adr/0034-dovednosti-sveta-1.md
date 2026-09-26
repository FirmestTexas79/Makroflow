# 0034 – Dovednosti světa 1: Chytání, Výroba, Pěstování (po vzoru Legends of IdleOn)

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-26

## Kontext
Makrosvět má být komplexnější hra. Každý svět dostane vlastní dovednosti. Svět 1, tedy město, louka a hory, má tři dovednosti:
* každá je samostatná mechanika a sbírá vlastní XP;
* levely dávají pasivní bonus;
* dovednostní body se utrácejí ve stromu dovedností.

## Rozhodnutí
* **Level a XP** (`skills/Skills.kt`, čistý Kotlin, testy `SkillsTest`):
  * Level začíná na 1.
  * XP na další level počítá vzorec profesí z IdleOn: ⌊15 + 4L + (1,5L)^2,2 + 5L·1,45^max(0,(L−20)/7)⌋.
  * Zisk XP = ⌊Základ × (1 + ΣAᵢ) × ΠMⱼ × Koeficient času⌋. Aditivní bonusy (strom +15 %) se nejdřív sečtou, samostatné násobitele (shiny ×2) pak násobí celek. Koeficient času je zatím 1.
* **Dovednostní body** přibývají na levelech 3, 6, 10 a pak každých 5 levelů. Počítají se pro každou dovednost zvlášť.
* **Pasivní bonus**: +1 % za každý level nad první, nejvýš 50 %. Jako v IdleOn se neodečítá od celkové hodnoty, ale bere se z původní šance: šance − šance × bonus.
  * Chytání: nižší šance, že Makromon po vyskočení z ballu uteče.
  * Výroba: šance na dvojitou výrobu.
  * Pěstování: šance na dvojitou sklizeň.
* **Stromy dovedností**:

  | Dovednost | Uzly |
  |---|---|
  | Chytání | „Parťák navíc“ (tým až 2), pak místa až do 6; +15 % XP |
  | Výroba | „Základní vybavení“ (zatím jen příznak, recepty přibudou); +15 % XP |
  | Pěstování | „Nové záhony“ (opraví 2 zničené); +15 % XP; hnojivo −15 % doby růstu |
* **Chytání XP**: 10 + 3 × level chyceného Makromona, shiny ×2.
* **Kořist** z divokých Makromonů:
  * fragment energie: 35 % po výhře (+1 % za level, nejvýš 60 %), 25 % po chycení;
  * semínko jen z travních Makromonů, 25 %. Vzácnost: olivové 75 %, modré 20 %, černozlaté 5 %.
* **Bobule** mají barvy Makroballů: olivová → Makroball, modrá → Proteinball, černozlatá → Kreatinball.

  | Bobule | Růst | XP za sklizeň | XP za výrobu | Semínko v obchodě |
  |---|---|---|---|---|
  | Olivová | 15 min | 15 | 10 | 15 |
  | Modrá | 1 h | 40 | 25 | 45 |
  | Černozlatá | 4 h | 100 | 60 | 120 |

  Semínka se prodávají v obchodě na nové záložce „Semínka“.
* **Pracovní stůl** stojí na louce vlevo dole, na světlé trávě u cedule. Recept: 1 fragment energie + 1 bobule příslušné barvy = ball. Základní recepty jdou od začátku.
* **Záhony** jsou na louce vpravo pod mostem:
  * čtyři v rozích, mezi nimi hliněná cesta ve tvaru plus;
  * záhony 1 a 2 jsou otevřené, 3 a 4 zničené až do uzlu „Nové záhony“.
  * Klepnutí na prázdný záhon otevře dřevěné menu se semínky.
  * Nad rostoucím záhonem je cedulka s přesýpacími hodinami a odpočtem.
  * Hotový záhon má nad sebou dřevěný čtvereček s fajfkou; klepnutí sklidí.

  Záhony a stůl jsou v mapě chůze neprůchozí (`MEADOW_FIX`) a jejich uzly vedou postavu k okraji.
* **Tým**:
  * seznam až 6 chycených Makromonů; první je aktivní parťák na liště;
  * v inventáři tlačítko „+ / ×“ přidává a odebírá a odznak ukazuje pořadí;
  * v souboji tlačítko MKRM otevře tým a výměna stojí tah;
  * po omdlení nastoupí další člen, výběr je vynucený;
  * XP ze souboje dostane ten, kdo zrovna bojuje.
* **Deník**:
  * nová první záložka **Postava**, okno ve stylu IdleOn:
    * portrét, celkový level a velikost týmu;
    * tři dovednosti pod sebou;
    * po klepnutí rozpis: XP pruh, pasivní bonus, XP multiplikátor, body a strom s odemykáním;
  * nová záložka **Suroviny**: sklad v políčkách s počty – fragmenty, bobule, semínka a vyrobené Makrobally.
* **Úložiště**:
  * vše kromě týmu je v `user_items`, takže se samo zálohuje do Firebase:
    * `skill_xp_*` = celkové XP;
    * `skill_node_*` = odemčený uzel;
    * suroviny a semínka;
    * `garden_i` = čas zasazení × 4 + kód bobule;
  * interní položky inventář skrývá;
  * tým je v GamePrefs, stejně jako aktivní parťák.
* Keřík se starterem se přesunul ve městě na trávu pod Gudwina, protože nahoře ho zakrývala ikona parťáka.

## Důsledky
* Nové dovednosti dalších světů stačí přidat do `Skill` a `SkillTree.NODES`. Stránka Postava je vykreslí sama.
* Čas zasazení se kóduje do Int s epochou 2026-01-01 a vystačí zhruba do roku 2042.
