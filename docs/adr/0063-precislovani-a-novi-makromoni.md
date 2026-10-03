# 0063 – Přečíslování Makrodexu, Mysnor, Aquavulp, sprity Aqulinda a Florindry

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-10-03

## Kontext
* Autor nakreslil tři nové sprity: Aqulind (#005), Florindra (#009) a druhého strážce (#035).
* Autor určil nové pořadí: Mysnic #032, jeho vývoj #033, lev Ignileo #034.
* Modrý krystal dosud hlídal obyčejný Serpfin (#021). Strážce s vlastním číslem, ale cizími statistikami, kód neuměl: sprite se skládal z čísla druhu, odkud se brala síla. Proto vznikla kopie `makromon_03_ignileo`.

## Rozhodnutí
* **Čísla:**
  * #032 Mysnic;
  * #033 **Mysnor** (vývoj Mysnica na Lv 10, „ZEMĚ / PEVNOST“, vzácný v Horách, zatím bez spritu);
  * #034 Ignileo;
  * #035 **Aquavulp**, vodní liška a strážkyně modrého krystalu (místo Serpfina).
  
  Jména Mysnor a Aquavulp jsou návrh, autor je může přejmenovat.
* **Strážci:** `SpecialBattle.makromonId` je číslo Makrodexu a `statsFrom` druh, odkud se berou statistiky a útoky (Ignileo ← Ignaroth #003, Aquavulp ← Serpfin #021).
  * `BattleFactory.GUARDIAN_DEX` říká, jaký sprite má strážce v souboji, takže kopie spritu `makromon_03_ignileo` je pryč.
  * V Makrodexu se strážce zapíše po porážce (`boss_defeated_BLUE` / `crystal_BLUE` → #035).
* **Uložené hry:** chycený Mysnic měl číslo `033`. Při každém otevření databáze se `MYSNIC` přečísluje na `032` podle jména, takže se opraví i Mysnic obnovený ze staré zálohy v cloudu.
* Sprity Aqulinda (`makromon_05_aqulind`) a Florindry (`makromon_09_florindra`, zmenšená na 640 px) jsou zapsané v seznamu existujících spritů souboje.
* Mysnor zatím nemá sprite. V souboji se zobrazí výchozí ikona a v Makrodexu stín Makroballu, dokud ho autor nenakreslí jako `makromon_33_mysnor` a nepřidá do seznamu spritů.

## Důsledky
* Hlídají to testy:
  * `NewSpeciesTest`: čísla, vývoj, sprity, zápis strážců;
  * `SpecialBattleTest`: souboj i mapa najdou stejný sprite strážce.
