# ADR 0004 – Herní odměny za zdravé chování (fáze C)

- **Stav:** přijato (2026-09)
- **Kód:** `energy/Adherence.kt` (jediné pravidlo „splněno“), napojení v questech a achievementech

## Kontext

Herní vrstva může chování posilovat i špatným směrem. Audit našel:

| Kde | Problém |
|---|---|
| Quest Krále Mlsáka | pevné cíle 1500 kcal / 80 g B / 200 g S / 50 g T bez ohledu na člověka. Pro 55kg ženu na dietě jde o nevhodný pokyn. |
| Achievementy maker a „perfektní týden“ | „aspoň 90 % cíle“ bez horní hranice, takže odměňovaly i přejídání. Počítal se i rozjedený dnešek. |
| Achievementy váhy | libovolná změna o 2 / 5 kg (i opačným směrem nebo nezdravě rychle), navíc ze zašuměného vážení |
| Notifikace po tréninku | „nejbližší 2 hodiny jsou kritické“: mýtus úzkého anabolického okna (Schoenfeld et al. 2013) |
| Popisy achievementů | neodpovídaly kódu (např. „Používej 7 dní“ vs. kód 3 dny, „1 různých jídel“ vs. 5) |

## Rozhodnutí

**Hra odměňuje trefení OSOBNÍHO cíle v pásmu, ne „čím víc/míň, tím líp“.**
Cíle už obsahují dietu nebo bulk (fáze A) i adaptivní výdej (fáze B).

| Živina | Pásmo „splněno“ | Důvod |
|---|---|---|
| Kalorie | 90–110 % | dole by hra odměňovala hladovění, nahoře přejídání |
| Bílkoviny | 90–150 % | minimum je důležité, strop jen jako rozumná mez |
| Sacharidy, tuky | 85–115 % | přirozená denní variabilita |

- Achievementy hodnotí jen **uzavřené dny**, protože dnešek se ještě může přehoupnout přes pásmo.
- Questy (`HIT_TARGET`) vyhodnocují dnešek průběžně, aby hra reagovala hned. Jde o vědomý kompromis.
- **Váha:** pokrok se měří z trendu (Kalman) a jen ve směru cíle. Pokud bylo průměrné tempo rychlejší
  než 1 % hmotnosti za týden, odměna nepadne. Při udržování se odměňuje stabilita (±1 kg po 4 týdnech,
  diamant po 3 měsících).
- Texty questů krále přepsané na „traf svůj cíl“. Příběh zůstává.

## Důsledky

- (+) Hra a výživová doporučení táhnou za jeden provaz, žádná odměna nepodporuje extrémy.
- (+) Jedno pravidlo (`Adherence`) místo tří různých výkladů „splněno“ v kódu.
- (−) Dřív odemčené achievementy zůstávají odemčené (nemažeme uživatelům historii).
- (−) Pásma jsou expertní volba bez přímé studie „správné tolerance“. Vycházejí z přesnosti
  zápisu jídla (etikety ±20 %, odhad porcí) a denní variability. Parametry jsou na jednom místě a jdou snadno upravit.
