# 0033 – Volná chůze po mapě (mapy chůze místo teček)

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-26

## Kontext
Postava chodila jen po hranách grafu mezi body. Body byly v debug buildu vidět jako červené
a zelené čtverečky a klepnout šlo jen na ně. Ve finální hře na zemi žádné tečky být nemají.
Chceme, aby šlo dojít kamkoli, kde se dá chodit, a nikdy ne do domů, stromů, vody nebo skal.

## Rozhodnutí
* **Mapa chůze** pro každý biom: `app/src/main/assets/walk/<biom>.txt`. Obsahuje hlavičku
  „šířka výška buňka“ obrázku a mřížku `.` (průchozí) / `#` (zeď). Generuje ji
  `tools/walkmask/gen_walkmask.py` z obrázků map:
  * rozpozná barvy trávy, cest, skal a podlahy jeskyní;
  * kolem hran grafu přidá chodby, aby cesty k aktivním místům zůstaly průchozí;
  * přidá ruční opravy (např. socha v horách je zeď);
  * ponechá jen plochu souvislou s uzly.

  Buňky: město 6 px, louka a hory 12 px, jeskyně a les 3 art px.
  Kontrolní překryvy se ukládají do `tools/walkmask/out`.
* **Za běhu** (`pokemon/walk/WalkGrid.kt`, čistý Kotlin, testy `WalkGridTest`):
  * `MapGeometry` převádí svět mapy na pixely obrázku. Pro běžné mapy platí CENTER_CROP, jeskyně a les jsou celočíselným násobkem, takže stačí stejný vzorec.
  * A* po buňkách: 8 směrů, bez řezání rohů.
  * Vyhlazení přímkami. Přímka musí být průchozí i o čtvrt buňky do stran, aby cesta nevedla po hraně zdi.
  * Cíl ve zdi se přichytí na nejbližší průchozí místo.
* **Klepnutí** (`MakromonMapActivity.handleMapTap`):
  * Klepnutí blízko aktivního místa (NPC, vchod, křoví, jezírko…) → postava dojde až k němu a spustí se akce jako dřív. V jeskyních a lese mají aktivní místa menší dosah a patří k nim jen místa, která něco dělají.
  * Klepnutí jinam → postava volně dojde na klepnuté místo.
  * Tažení prstem a klepnutí na tlačítka nebo parťáka postavu nepošlou.
  * Dvojklik zrychlí chůzi.
* **Chůze** (`MovementEngine.walkToPoint`):
  * Postava jde úsek po úseku a animace kroků se řídí převažujícím směrem úseku.
  * Na konci se postava vždy otočí dolů, k hráči.
  * Bez mapy chůze zůstává původní chůze po grafu.
* Ladicí tečky grafu jsou z mapy pryč úplně. Graf dál slouží jako seznam aktivních míst a startovních pozic.

## Důsledky
* Uzly jsou v podílech obrazovky vyladěné na telefon 1280 × 2856, takže na jiném poměru
  stran se proti obrázku mírně posunou (stejně jako dřív). Maska je v pixelech obrázku, a proto
  na poměru stran nezávisí.
* Po úpravě obrázku mapy je potřeba znovu spustit generátor.
