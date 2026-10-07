# 0066 – Ilustrace cviků (postava a stroj)

**Stav:** prototyp na 3 cvicích · **Datum:** 2026-10-07

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

## Prototyp
`skull_crusher`, `incline_machine_press`, `lat_pulldown`. Po schválení stylu doplnit zbytek knihovny
(67 cviků); každou pózu je potřeba zkontrolovat okem.

## Omezení
Boční pohled je 2D: pohyb do hloubky (lokty od těla, rozpažení) se zobrazí zkráceně. U cviků, kde je
podstatný čelní pohled (upažování, pec deck), bude potřeba čelní varianta postavy.
