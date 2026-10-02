# 0060 – Příběh v dřevěném stylu, Kapsa, Batoh a Makrodex v deníku

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-10-02

## Kontext
* Postava, strom dovedností a Denní úkoly mají dřevěný pixelový vzhled (docs/adr/0058).
* Příběh byl pořád jen text na papíře a fáze byly odkazy v jednom odstavci.
* Kapsa (chycení Makromoni), Batoh (předměty) a Makrodex byly jen na samostatných obrazovkách mimo deník. Z mapy se k nim hráč dostával přes Domov.

## Rozhodnutí
* **Příběh** (`StoryPage`). Rozložení na dvě strany zůstává, mění se vzhled:
  * portrét postavy je v modrém rámu (u tajné linky se zlatým okrajem);
  * cíl je na dřevěné cedulce, splněný na zelené;
  * text kapitoly je na pergamenovém listu;
  * kapitola je na dřevěné stužce, tajná linka na zlaté;
  * postup úkolu je řada pixelových kamenů se spojkami (zelený = splněno, oranžový pulzující = teď, světlý = čeká) a vpravo „3/7“. U dlouhých úkolů se spojky zkrátí, aby se řada vešla;
  * fáze jsou klikací řádky s odznakem: ✓ zelená = splněná, číslo oranžová = aktuální, zámek a „???“ = budoucí. Vybraná fáze má zlatý rámeček;
  * listovací rohy jsou dřevěné kostky.
* **Nové záložky** v zadní řadě: Mapa, **Kapsa**, **Batoh**, **Makrodex**, Spisy.
* **Kapsa** (`CollectionPages.pocket` + `PocketActions`):
  * dřevěná cedule s počtem chycených;
  * řada šesti míst týmu: modré dlaždice, parťák se zlatým rámem a ★, volná místa „+“, zamčená místa se zámkem a odkazem na strom Chytání;
  * mřížka všech Makromonů po třech (nejdřív tým, pak podle levelu). Každý má sprite, jméno, level, pixelový XP pruh, číslo v týmu, zámek a ✦ u shiny.
  * Klepnutí otevře dřevěné menu s akcemi: přidat do týmu / odebrat, parťák na liště, zamknout, pustit. Puštění se potvrzuje druhým klepnutím a zamčeného pustit nejde.
  * Pravidla jsou stejná jako na obrazovce inventáře. Parťák na mapě se obnoví sám přes GamePrefs.
* **Batoh** (`BagItems`, bez Androidu):
  * přihrádky Makrobally, Lékárnička, Vybavení, Klíčové předměty (zlatá políčka) a Ostatní; pod každým políčkem je název;
  * suroviny do Batohu nepatří, mají vlastní záložku;
  * deník strážce a roztržené listy se čtou přímo v menu na pergamenu, Spooky Plate jde použít.
* **Makrodex:**
  * cedule „chyceno / celkem“;
  * dřevěné záložky Makrodex / ✦ Viděno / ✦ Chyceno;
  * mřížka po čtyřech s číslem: chycené na zeleném políčku s ballem, viděné na světlém, neobjevené jako černý stín na tmavém, chycené shiny na zlatém;
  * detail obsahuje sprite, stavový štítek, popis nebo nápovědu, spis S-7 (s Vhledem, docs/adr/0057), cesty vývoje Spirry a v ladicí verzi test evoluce.
  * Nápovědy a cesty Spirry jsou sdílené s obrazovkou Makrodexu (`DexText`).

## Důsledky
* Samostatné obrazovky inventáře a Makrodexu zůstávají (Domov). Deník používá stejná data i pravidla, jen jiný vzhled.
* Nový předmět, který má být v Batohu, potřebuje záznam v `BagItems.of`, jinak se ukáže jako „Neznámý předmět“. `BagItemsTest` hlídá přihrádky a velikost ikon.
