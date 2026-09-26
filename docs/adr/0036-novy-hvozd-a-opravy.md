# 0036 – Nový Hvozd, intro setkání v lese a drobné opravy

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-26

## Rozhodnutí
* **Hvozd přepracovaný** (`tools/mapgen/gen_forest.py`):
  * 300 × 600 art px, na obrazovce je vidět 150 px na šířku (`artPixelsAcross`), kamera jezdí do všech stran;
  * místo rovných kapslí po mřížce (cikcak) jsou nepravidelné palouky spojené vlnitými stezkami (Catmull-Rom); hlavní stezka od vstupu k mýtině je hliněná;
  * stromy mají stíny, koruny z několika shluků listí a mezi nimi jsou jehličnany; koruna smí přes okraj palouku jen trochu;
  * na okrajích je podrost, na paloucích sluneční skvrny, kvítí, pařezy a kameny s mechem.
* **Poznávací znaky setkání**: kapradí, kruh muchomůrek, dutý kmen, ostružiní, starý dub s dutinou (a očima v ní) a dvě jezírka s lekníny. Objekty i kmeny stromů ke kácení jsou v mapě chůze neprůchozí.
* **Mapa chůze Hvozdu**: kreslí ji rovnou generátor (`forest_walk.png`). Rovné koridory mezi uzly by vedly přes stromy.
* **Intro setkání v lese** (`ForestIntro` + `ForestScene`) ve stejné stylizaci jako voda:
  * soumrak mezi kmeny, paprsky světla, světlušky a padající listí;
  * keř se dvakrát zachvěje a v jeho stínu se rozsvítí oči (poprvé mrknou);
  * pak se keř rozlétne do listí, z korun vyletí ptáci – záblesk a souboj;
  * jezírka dál používají intro u vody.
* **Místa těžby a kácení**:

  | Surovina | Kde |
  |---|---|
  | Bříza | první (spodní) polovina Hvozdu |
  | Javor | konec Hvozdu u mýtiny |
  | Dub | louka, nad pracovním stolem (dál od křoví) |
  | Stříbro | Starý důl (levá jeskyně), ve stěně nad štolou |
  | Zlato | Mechová jeskyně (pravá), balvan v prostřední síni |
* **Město**: jen Ignar, Aqulin a Flori; Ignaroth patří do hor.
* **Keřík pod Gudwinem**: „vynucené“ setkání je jen do konce úvodního úkolu. Dřív příznak zůstal navždy, takže ve městě nikdy nepadl shiny.
* **Makromon ve funkční části**:
  * **Skok na výchozí místo**: třesení (klepnutí i některá idle) animovalo `translationX` od nuly, takže Makromon po klepnutí přeskočil na výchozí místo. Teď se třese kolem aktuální polohy.
  * **Otáčení na špatnou stranu**: dýchací idle si drželo původní směr. Otočení teď idle restartuje a příchod na scénu už nepřepisuje směr.
* Oznámení na mapě se ukazují dole, semínka stojí 10 / 20 / 50.
* Generátor map chůze čte uzly jeskyně jen z její vlastní definice (dřív 6000 znaků přes sousední mapu).
