# ADR 0107 – Prozkoumávání míst podržením, rozlehlý Les pod korunou a úkol Jména v mlze

Datum: 2026-11-26 · Stav: přijato

## Kontext
Místa s příběhovým textem (cedule, malby, předměty) se otevírala klepnutím. Hráč, který chtěl jen dojít kousek dál,
je často otevřel omylem, a vyskakovací text byl nevýrazný. Les pod korunou (zóna 3) byl úzký koridor 176 × 560.

## Rozhodnutí
- **Prozkoumávání (pokemon/inspect/InspectUi.kt):** místa s textem (TEXTS lokací Hlubiny a pater Věže) se klepnutím
  neotevírají – postava jen dojde (prvních pár krát poradí, jak na to). Nad místem je třpyt: bílé a azurové čtyřcípé
  pixelové hvězdičky (jinak než zlaté šipky východů), přečtená místa třpytí slaběji. Hráč podrží prst 2,2 s: nad prstem
  se vykresluje pixelový kruh (24 dílků, naplňuje se po směru hodin) a po naplnění s vibrací vyjede panel
  „✦ PROZKOUMÁNO“ – tmavá deska s pulzujícím azurovým lemem, ikona v rámečku, název lokace a text po písmenech.
  Pohyb prstu nebo puštění kruh zruší a klepnutí se počítá jako chůze.
- **Les pod korunou 704 × 1120** (4× širší, 2× vyšší, tools/mapgen/gen_crown_forest.py): síť stezek v obráceném lese,
  kruh stop s Gudwinem, strom značek, sonda, opuštěný tábor výpravy 6, tůň, mýtina ticha, lucerny, brána na Práh.
  Šest mechových palouků jsou místa setkání (PŘÍRODA, JED). Větve nad stezkou jsou průhledné (visí vysoko).
- **Vedlejší úkol Jména v mlze (story/ForestNames.kt):** Gudwin (kletba Paměti) říká jména těm, kdo v mlze zapomněli.
  Fáze: 3 noci spánku ≥ 7 h (mlha se živí únavou; pak jdou přečíst značky na kůře a jsou vidět jmenovky) → 7 jmenovek
  ve větvičkách (sběratelské předměty) → Gudwin je přečte nahlas a dá hráči jmenovku s číslem 213.
- **Kmen:** Trpitel drží kopí vpředu v levých rukou (zápěstí vytočená ven, kopí neprochází hrudí), kopí má listovou
  čepel; mrknutí je plynulé (víčka sjedou přes 3 rychlé snímky); kmen pokračuje i pod lávkou do tmy.

## Důsledky
Klepnutí na textová místa už nic neotevře – kdo nečte nápovědu, uvidí aspoň třpyt. Akční místa (východy, NPC,
předměty, souboje) fungují dál klepnutím.
