# 0066 – Ilustrace cviků (postava a stroj)

**Stav:** implementováno pro všech 67 cviků · **Datum:** 2026-10-07

## Kontext
Tester v posilovně nepoznal, o jaký cvik jde. Latinské názvy a postup v textu nestačí, chybí obrázek
„jak to vypadá“. Kreslení náčiní u zápisu série (docs/adr/0028) zůstává beze změny.

## Rozhodnutí
* V detailu cviku (sekce Provedení) karta se dvěma snímky vedle sebe: **začátek** a **konec** pohybu.
* Beztvářná postava z boku z jednoduchých tvarů (kapsle končetin, kruh hlavy, trup), stroj z pár
  primitiv (tyče, kotouče, lanko / páka do ruky). Pracující svaly zvýrazněné jako v atlasu:
  hlavní tmavě oranžově, pomocné světleji. Barvy aplikace, Canvas, bez animace a bez bitmap.
* Póza se zadává cíli (pánev, sklon trupu, kam míří zápěstí a kotník); loket a koleno dopočítá
  dvoukloubová IK. Jeden cvik = pár řádků dat v `ExerciseFigures.BY_ID`, žádné ruční kreslení.
* Cvik bez ilustrace kartu nemá.

## Implementace
* `ExerciseFigures` (kinematika, typy), `ExerciseFigureData` (vygenerovaná data), `ExerciseFigureView` (Canvas).
* Pózy se ladí v `tools/exercise_figures` (Python + Pillow): `engine.py` kreslí náhled stejně jako
  aplikace, `sheet.py` dělá kontaktní arch, `gen.py` z `figs.py` vygeneruje Kotlin. Data se ručně neupravují.
* Bokorys pro většinu cviků; **čelní pohled** pro pohyby do stran (upažování, rozpažky, pec deck,
  reverse pec deck, abdukce kyčle, dřevorubec, boční plank). Pohyb do hloubky v bokorysu zkracuje
  paži (`armScale`). Výdrže (plank, boční plank) mají jeden snímek „Výdrž“.
* Test hlídá, že každý cvik z knihovny má ilustraci a pózy drží délky článků.

## Omezení
Boční pohled je 2D: pohyb do hloubky (lokty od těla, rozpažení) se zobrazí zkráceně. U cviků, kde je
podstatný čelní pohled (upažování, pec deck), bude potřeba čelní varianta postavy.
