# ADR 0001 – Propojení funkční části a Makrosvěta přes log herních událostí

- **Stav:** přijato (2026-09)
- **Kontext:** větev `feature/quest-bridge-mountains`

## Kontext

Questy v Makrosvětě potřebují reagovat na akce ve funkční části aplikace
(zápis jídla, sken čárového kódu, kroky). Původní řešení volalo
`(activity as? MakromonMapActivity)?.questManager` přímo z `SnackFragment`.
`SnackFragment` ale žije v `MainActivity`, takže přetypování vždy vrátilo `null`
a události se ztrácely. `QuestManager` navíc existuje jen po dobu, kdy je otevřená mapa.

## Rozhodnutí

1. Zavádíme append-only tabulku `game_events` (Room, DB v34).
   Producent (funkční část) událost jen zapíše přes `GameEvents.record(...)`.
2. `QuestManager` postup fází typu LOG_MEAL, WALK_STEPS a SCAN_BARCODE **nepočítá**,
   ale **odvozuje** z DB:
   - jídla: počet dnešních záznamů v `consumed_snacks`,
   - kroky: dnešní záznam v `steps`,
   - skeny: `COUNT(game_events) WHERE timestamp >= quest_progress.stageStartedAt`.
3. Odvození se spouští při načtení questu (dohnání změn mimo mapu) a reaktivně
   přes Flow z Room, ale jen ve stavu STARTED (`repeatOnLifecycle`).
4. Pravidla vyhodnocení jsou v čisté třídě `QuestProgression` a pokrývají je unit testy.

## Zvažované alternativy

| Varianta | Proč ne |
|---|---|
| Globální singleton `QuestManager` / event bus v paměti | Události ze zabité aplikace se ztratí. Musel by existovat i bez UI mapy. |
| Zápis přímo do `quest_progress` z funkční části | Duplikovala by se logika fází a funkční část by musela znát questy. |
| Počítadlo ve SharedPreferences | Není transakční, špatně se testuje a nesynchronizuje se s Room. |

## Důsledky

- (+) Funkční část o questech nic neví. Stačí jí zapsat fakt, že se něco stalo.
- (+) Postup se započte i tehdy, když hráč sken udělal bez otevřené mapy.
- (+) Log jde auditovat a nad stejnými událostmi půjdou stavět další systémy (achievementy, Spirra).
- (−) Schéma DB se změnilo, proto je potřeba migrace `MIGRATION_33_34`.
  Destruktivní fallback nyní platí jen pro verze < 33.
- (−) `game_events` se zatím nesynchronizuje do Firestore. Po reinstalaci se
  rozpracovaná fáze se skenem musí zopakovat (zbytek postupu se obnoví z Firestore).
