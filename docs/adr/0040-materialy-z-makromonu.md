# 0040 – Materiály z Makromonů a legendární artefakty z Gudwina

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-26

## Rozhodnutí
* **Kořist podle rodiny Makromona** (`DropFamily` ve `World1.kt`). Typ v souboji se bere z prvního útoku, takže Ignar by vyšel jako Normal. Rodinu proto určuje druh:

  | Rodina | Druhy | Materiál |
  |---|---|---|
  | Listoví | Flori, Florind, Florindra, Verdirra | Suchý list (+ semínka jako dřív) |
  | Ohniví | Ignar, Ignaroc, Ignaroth, Flamirra | Chudý plamínek |
  | Vodní | Aqulin…, Aquirra, Finlet, Serpfin, Glacirra | Vodní perla |
  | Duchové | Umbex, Lumex, Soulu…, Phantil…, Shadirra | Malá dušička |
  | Draci | Drakirra | Dračí šupina |
  | Víly | Charmirra | Pixie prach |
  | Normální | Spirra, Mycit, Mydrus, Gudwin, Axlu | fragment energie s vyšší šancí (+20 %, max 80 %) |
* **Šance na materiál**: po výhře 40 % + 1 % za level (max 65 %), po chycení 25 %. Od Lv 10 občas padnou 2 kusy. Fragment energie padá dál ze všech.
* **Vylepšené materiály z evolucí** (po výhře 35 %, po chycení 20 %):
  * Ignaroc: žhnoucí kámen (kámen, ze kterého vyšlehává plamínek);
  * Ignaroth: koule magmatu;
  * Florind a Florindra: živý list.

  Evoluce dávají i základní materiál své rodiny. Ostatní evoluce zatím vylepšenou verzi nemají.
* **Gudwin**: po výhře má každý artefakt zvlášť 5% šanci. Padne jen tomu, kdo ho ještě nemá.
  * Makromonova sekera: síla 500, +50 % XP za kácení, +10 % šance na dvojité poleno;
  * Makromonův krumpáč: síla 500, +50 % XP za těžbu, +10 % šance na dvojitou rudu.
  * Grafika je zkamenělá kostěná násada se zlatými obručemi a čepel z nefritového krystalu se zářícími runami a zlatým jádrem, jako památka z dob, kdy na světě byli jen Makromoni. V souboji se ohlásí jako „★ LEGENDARY ★“, ve výběru do slotu mají zlatý nápis.
* **Deník**: nové materiály jsou v Surovinách v sekci „Z Makromonů“. Cedule u každého ukazuje, kdo ho dává a s jakou šancí.
* Názvy kořisti v souboji se berou z názvů surovin (bez diakritiky, velkými písmeny).
