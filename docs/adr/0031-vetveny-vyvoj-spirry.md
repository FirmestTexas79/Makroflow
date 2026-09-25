# 0031 – Větvený vývoj Spirry podle činností

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-25

## Kontext
Veverky Flamirra, Aquirra, Verdirra, Shadirra, Charmirra, Glacirra a Drakirra jsou vývojové
formy Spirry, ale Spirra se nevyvíjela vůbec. Samuel chtěl, aby o vývoji rozhodovalo, co
s ní uživatel dělá – vývoj jako odměna za návyk.

## Rozhodnutí
| Forma | Podmínka (jen dny, kdy je Spirra aktivním parťákem) |
|---|---|
| Flamirra | spálit 5 000 kcal pohybem (chůze + trénink z energetického modelu) |
| Aquirra | vypít celkem 25 l vody |
| Verdirra | 3 dny za sebou vláknina v rozmezí 90–140 % osobního cíle |
| Shadirra | zapsat 10 jídel v noci (21:00–4:59) |
| Charmirra | nachodit 100 000 kroků |
| Glacirra | zapsat 200 sérií do tréninkového deníku |
| Drakirra | tajná – zatím jen k ulovení |

* **Pouto** (`SpirraBond`): při každém návratu do aplikace se dnešek zapíše jako den s aktivní
  Spirrou (pro každou chycenou Spirru zvlášť). Uzavřené dny se spočítají jednou a uloží,
  dnešek se počítá znovu. Den bez Spirry na liště přeruší řadu vlákniny.
* **Vývoj**: jakmile je některý cíl splněný, spustí se `EvolutionDialog` (stejná animace jako
  u vývoje levelem, naučí se první útok nové formy); při více splněných cílech ten s největší
  rezervou. Po vývoji se pouto smaže.
* **Přehled**: Makrodex → Spirra (v inventáři) → „Cesty vývoje“ – úkoly a postup aktivní Spirry.
* Logika (`SpirraEvolution`) je čistý Kotlin s testy.

## Důsledky
* Počítají se jen dny od zavedení (dřívější dny se Spirrou nejsou zaznamenané).
* Formy se dál dají i ulovit v divočině (vzácné); Drakirra jen tak.
