# 0019 – Atlas svalů a knihovna cviků

**Stav:** přijato · **Datum:** 2026-09-25

## Kontext
Karta „Týden na těle“ v Plánu ukazuje, kolikrát týdně je která partie na řadě (ADR 0008),
ale nevysvětluje, *co s tím dělat*: když je partie pod doporučenými 2× týdně, uživatel neví,
jakými cviky ji doplnit, ani jak je správně provést. Cílem je interaktivní atlas: klepnutím
na sval se partie rozsvítí, vysune se její popis a seznam cviků; cvik se dá otevřít a přečíst
si techniku včetně latinských názvů zapojených svalů.

## Rozhodnutí
* **Data jako čistý Kotlin** (`training/exercises/ExerciseLibrary.kt`): 51 cviků, každý s
  pomůckou, obtížností 1–3, hlavními a vedlejšími partiemi (`Muscle`), latinskými názvy
  svalů (Terminologia Anatomica, „m.“/„mm.“), kroky provedení, tipy a častými chybami.
  `MuscleAnatomy` drží latinské složení a funkci všech 15 partií. Offline, bez sítě a bez
  databáze – obsah je statický a revidovatelný v jednom souboru; testy hlídají, že každá
  partie má aspoň 3 cviky a jeden, kde je hlavní, že každý cvik má ≥ 3 kroky a latinské názvy.
* **Rozložení a zásah jsou čistá logika** (`training/body/BodyLayout.kt`): `BodyLayout`
  počítá měřítko a posun postav (jedna nebo dvě) a převádí dotyk na souřadnice těla
  (viewBox 100 × 200) – kreslení i zásah tak používají tentýž výpočet. `MuscleHit` vybírá
  partii: přesný zásah (vyhrává partie kreslená navrch), jinak tolerance pro prst – vzorky
  na třech kružnicích do ~14 dp, vyhrává nejbližší zásah. View dodá jen test „bod leží
  v cestě“ přes `Region` (cesty ×4 kvůli celočíselným regionům).
* **`BodyMapView`** umí jednu velkou postavu (`sides`), obrys siluety, výběr partie
  (animované rozsvícení akcentovou barvou + obrys navrch) a `onMuscleTap`. Bez listeneru
  se chová jako dřív, takže malé postavy v kartách dnů se nemění.
* **UI:** `MuscleAtlasSheet` (panel přes celou výšku) – přepínač zepředu/zezadu, velká postava
  s týdenní intenzitou, čipy všech partií (partie vidět jen zezadu přepne stranu sama),
  tmavá karta partie (funkce, týdenní frekvence a doporučení, latinsky), seznam cviků
  (hlavní před vedlejšími, podle obtížnosti). `ExerciseDetailSheet` – zapojené partie na
  malé postavě a latinsky, číslované provedení, tipy a časté chyby. Vzhled podle ADR 0018.
* **Vstup:** celá karta „Týden na těle“ (odznak „ATLAS CVIKŮ ›“); klepnutí přímo na sval
  v kartě otevře atlas s tou partií už vybranou.

## Důsledky
* Uživatel se od „prsa pod 2× týdně“ dostane ke konkrétnímu cviku na dvě klepnutí.
* Obsah cviků je třeba udržovat ručně; rozšíření o obrázky/animace provedení je možné
  přidáním pole do `Exercise` bez změny UI struktury.
* Atlas zatím nezapisuje cviky do plánu – plán dál pracuje s typy tréninků (PUSH/PULL/LEGS).
