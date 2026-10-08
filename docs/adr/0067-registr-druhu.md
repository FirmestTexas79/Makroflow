# 0067 – Registr druhů Makromonů

**Stav:** přijato · **Datum:** 2026-10-08

## Kontext
Nový Makromon se musel zapsat na 6 povinných míst (položka Makrodexu v AppDatabase, `createX()`,
`createById`, `makrodexId`, seznam spritů v `drawableName`, `SpawnManager`) a na několik dalších,
kde se jinak něco tiše rozbilo (`SpeciesIds.ALL` končil na #031, vývoj zapsaný dvakrát – Makrodex
a růstová křivka, rodina materiálů v `Drops`, šance chycení, velikost spritu v souboji).
Důsledky: Johnsova neměla v souboji obrázek (překlep v seznamu spritů), druhy od #032 chyběly
v MoveDexu, verze DB se zvedala zbytečně.

## Rozhodnutí
* `pokemon/species/Species.kt` – **jeden záznam = jeden druh**: číslo, vnitřní jméno, texty
  Makrodexu, statistiky, základní útoky, vývoj, spawny, rodina materiálů, šance chycení,
  výška spritu v souboji, u strážců `guardianOf`.
* Z registru se odvozuje: Makrodex (`fillMakrodexEntries`), `BattleFactory.createById / makrodexId /
  drawableName / catchMultiplier / GUARDIAN_DEX`, `SpawnManager` pool, `Drops.family / speciesFor`,
  `SpeciesIds.ALL`, vývoj v `MakromonGrowthManager.getProfile`, výška soupeře v `PokemonBattleView`.
* Sprite podle konvence `makromon_<2 číslice>_<jméno malými>.png`; chybějící soubor řeší volající
  (fallback ic_home) – žádný seznam.
* Zůstává zvlášť (volitelné, s výchozím chováním): učení útoků po levelech (`MakromonGrowthManager`),
  chování na mapě (`StandardWanderer`, `WandererFactory`), lore (`Kustodiat`), nápovědy v `DexText`.
* Vnitřní jméno (`name`) se nemění – je uložené u chycených Makromonů. Zobrazované jméno je `displayName`
  (Serpfin se v Makrodexu jmenuje Serfin).
* `SpeciesRegistryTest` hlídá jedinečnost čísel a jmen, vývoje, strážce a spawny.

## Nový Makromon
1. Záznam `Species(...)` v `Species.kt` (vzor: kterýkoli existující).
2. Obrázek `res/drawable/makromon_NN_jmeno.png`.
3. Volitelně růstová křivka a chování na mapě. Verzi databáze nezvedat.
