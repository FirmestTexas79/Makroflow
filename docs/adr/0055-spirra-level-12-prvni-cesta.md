# 0055 – Spirra se vyvíjí od levelu 12 do cesty, kterou splní první

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-10-02 · navazuje na [0031](0031-vetveny-vyvoj-spirry.md)

## Kontext
Cesty vývoje Spirry (0031) se počítaly jen ve dnech, kdy byla aktivním parťákem. Vývoj ale nastal hned po splnění kteréhokoli cíle bez ohledu na level. Při více splněných cílech vyhrál ten s největší rezervou, ne ten splněný jako první.

## Rozhodnutí
* **Všechny cesty běží v pozadí najednou** – stejně jako dřív, jen ve dnech, kdy je Spirra aktivní:

  | Forma | Cíl |
  |---|---|
  | Flamirra | spálit 5 000 kcal pohybem |
  | Aquirra | vypít 25 l vody |
  | Verdirra | 3 dny za sebou vláknina v rozmezí 90–140 % |
  | Shadirra | 10 jídel v noci |
  | Charmirra | 100 000 kroků |
  | Glacirra | 200 sérií v tréninkovém deníku |

* **Level 12** – dřív se Spirra nevyvine (`SpirraEvolution.LEVEL`), i když má cíle splněné.
* **První splněná cesta vyhrává** (`SpirraEvolution.reachedOn`, `evolveInto`):
  * dny se projdou v pořadí a u každé cesty se zapíše den, kdy byla splněná;
  * vyhraje nejdřívější den, při shodě ta s větší rezervou;
  * cesta splněná před levelem 12 si své pořadí podrží.
* **Kdy se kontroluje** – `SpirraEvolutionFlow`: po návratu do aplikace (jako dřív) a nově i po souboji na mapě, protože tam Spirra levelem nejčastěji dosáhne 12.
* **Přehled v Makrodexu** („Cesty vývoje“) ukazuje:
  * level a kolik levelů do vývoje chybí;
  * první splněnou cestu s datem (★);
  * ostatní splněné cesty (✓).
* **Drakirra** zůstává tajná a dá se jen ulovit. Podle příběhu je to prokletá legenda, ne cesta Spirry.
