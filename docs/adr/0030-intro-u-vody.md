# 0030 – Intro setkání u vody

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-25

## Kontext
Hory (ADR 0010) a jeskyně mají vlastní intro souboje v pixel artu. Vodní místa (tůň na Louce,
jezírka ve Hvozdu) měla jen obecné intro s keři na modrém pozadí.

## Rozhodnutí
* **Scéna** (`WaterScene`, čistý Kotlin, stejný princip jako `MountainScene` – snímek je čistá
  funkce času): soumrak nad rybníkem, slunce za lesem a jeho odraz, třpyt na hladině, lekníny,
  rákosí s orobincem, vlasec a splávek.
* **Časová osa** (`WaterIntro`, testy): roztmívání (0,35 s) → kruhy od splávku → pod hladinou se
  po spirále blíží stín → splávek dvakrát cukne → zmizí pod hladinou → vytryskne vodní sloup
  s tříští a pěnou, obraz se otřese → studený záblesk a souboj (2 s), kapky ještě dopadají.
  Klepnutí přeskočí na stažení splávku.
* **Přehrávač** `PixelEncounterView` je obecný (scéna, čas odhalení, konec, kam skočit) – další
  lokality už nepotřebují vlastní View.
* Náhled mimo telefon: `tools/encounter/WaterPreview.kt` vyrenderuje snímky do PPM.
* Týká se setkání s biomem WATER (tůň „voda“ na Louce, „jezirko_*“ ve Hvozdu).
