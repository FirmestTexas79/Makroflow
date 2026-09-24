# 0016 – Shiny část Makrodexu (viděno / chyceno)

**Stav:** přijato · **Datum:** 2026-09-25 · navazuje na [0009](0009-shiny-makromoni.md)

## Rozhodnutí
* Makrodex má tři záložky: **MAKRODEX** (jako dosud), **✦ SHINY VIDĚNO**, **✦ SHINY CHYCENO**.
  Ve shiny záložkách se sprity kreslí v barvách shiny (`ShinySprites`, stejný posun odstínu
  jako v souboji), chycení mají zlatý rámeček, detail ukazuje „✦ SHINY • VIDĚNO/CHYCENO“.
* **Chyceno** = `captured_makromon.isShiny` (už existuje a synchronizuje se přes Firebase).
* **Viděno** = nová množina čísel Makrodexu v GamePrefs (`shinySeenIds`), zapisuje se
  v `PokemonBattleView`, jakmile se objeví shiny soupeř. Chycení se počítají i jako viděná,
  takže shiny chycení před zavedením evidence se neztratí.
* Logika (stav, filtr záložky, počty) je čistý Kotlin `ShinyDex` s testy.

## Důsledky
* − Viděné shiny jsou jen lokálně (GamePrefs), ne ve Firebase – po přeinstalaci zůstanou
  jen chycené. Až se bude synchronizovat herní stav, přidat i tuto množinu.
