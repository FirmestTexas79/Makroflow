# 0103 – Věž Řádu Váhy, Kalibrace a světlo v interiérech

Stav: přijato (2026-11-19), Akt II, kapitola V · issue #31 · navazuje na ADR 0102

## Rozhodnutí
* **Vstup:** s Pozvánkou Řádu Váhy se dveře Věže otevřou. Poprvé se přehraje cutscéna **„Dveře Věže“**
  (`TowerDoorCutsceneView`, ~27 s): kamera dojede k Věži, sjede po jejích patrech k portálu, pečeť vah se rozsvítí, po křídlech
  se rozběhnou zlaté linky, sedm závor povolí a pak osmá, hlubší; bronzová křídla se otevřou do studeného bílého světla,
  střih do vestibulu k pultu. Příznak `tower_entered` splní fázi **Pozvánka**.
* **Vestibul** (`TOWER_LOBBY`, `TowerLobby.kt`, `tools/mapgen/gen_tower.py` + `tower/tower_parts.py`): mramor, tmavá modř
  a mosaz. Pult archiváře **Septa**, výtah s ukazatelem pater **0–7** (pod nulou dvě zaslepené pozice – skrytá patra, s Vhledem ≥ 3
  „tlačítko chybí“), čekárna s tabulí **SUBJEKT 213** a hodinami na 6:12 (jako u Oswina), automat vydá lístek 214, kartotéky
  (OKRUH 198–212, šuplík 213 prázdný), portréty „kalibrovaných“ bez tváří, velké kalibrační váhy. Patra 1–7 jsou zatím
  „zatím neschváleno“ a přibudou s dalšími fázemi.
* **Kalibrace** (nový požadavek `MEAL_STREAK`): sedm dnů se zápisem jídla v řadě od začátku fáze. Počítá se zápis, nikdy kalorie,
  váha ani tvar těla. **Jeden vynechaný den se odpouští, druhý sérii vynuluje**; dnešek bez zápisu se za vynechaný nepočítá.
  Pravidlo je čistá funkce `Calibration.streak` (testy). Kapitola V zatím končí razítkem KALIBROVÁN.
* **Spis Subjektu** (u Septa): jen pravidelnost za 30 dní – dny se zápisem jídla, ranní hlášení, dny se splněnou normou vody,
  průměrné kroky, tréninky za 7 dní, stav kalibrace a tolerance. Řádek „Tělesné míry: neevidováno“ – s Vhledem ≥ 3
  „(pro nádobu nerelevantní)“; „Předchozí subjekt: OKRUH 212“ se odkryje s Vhledem ≥ 4.
* **Světlo v interiérech** (`tools/mapgen/tower/light.py`): stěna nahoře tmavne, stín u paty zadní i bočních zdí, boční zdi
  s vnitřním čelem, tvrdé pixelové vržené stíny objektů doprava dolů, kontaktní stíny, světelné kužely z oken s prachem,
  odlesky v leštěné podlaze, viněta a tónování (ditherované, bez pruhů). Stejnou technikou je **přegenerovaná hala Arény**
  (rozložení a uzly beze změny).

## Testy
`CalibrationTest` (série, tolerance, reset, dnešek, spis bez váhy a začernění podle Vhledu), `OzvenaQuestTest`.
