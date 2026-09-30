# 0047 – Vhled, roztržené listy a praskliny ve fasádě (větev STORY)

**Stav:** návrh (větev `STORY`) · **Datum:** 2026-09-30

## Kontext
Příběh se od aktu II stáčí k temnému hororu ve stylu SCP, Dark Souls a Bloodborne. Bible příběhu je v projektu jako `lore/pribeh-bible.md`. Akt I má zůstat pohádkou, ale fasáda se musí začít nenápadně lámat.

Pravidla:
* náznaky jsou drobné a vzácné;
* pravda o Makromonech zazní až na konci;
* prokletí (Gudwin, Mlsák, Drakirra) si drží veselou fasádu;
* Kustodiát je v aktu I jen anonymní a jeho centrála přijde až za branou.

## Rozhodnutí
* **Vhled** (`story/Insight.kt`) je skrytá hodnota, hráč ji nikde nevidí.
  * Počítá se ze synchronizovaných příznaků příběhu:

    | Příznak | Vhled |
    |---|---|
    | legenda | +1 |
    | průsmyk | +1 |
    | vyhnaná hniloba | +1 |
    | nalezený háj | +1 |
    | vysvobozený Elderan | +2 |
    | každý nalezený list | +1 |

  * `StoryFlags.insight(ctx)` ho počítá.
* **Roztržené listy Kustodiátu:** pět anonymních klinických záznamů s razítkem S-7. Každý se najde jednou, místo běžné akce místa.

  | # | Kde | Kdy | O čem |
  |---|---|---|---|
  | 1 | táborák v horách | vždy | kalibrace subjektu, zdroj „G. O.“ |
  | 2 | mužík na průsmyku | vždy | sny personálu: strom kořeny nahoru, šest očí, kopí |
  | 3 | Starý dub | po vyhnání hniloby | hniloba maže paměť, „zapomnění je milosrdné“ |
  | 4 | studánka v háji | po nalezení háje | Elderanův deník ponechán jako návnada |
  | 5 | obchod ve Městě | po vysvobození Elderana | „Stanice 7“, okruh č. 213, přesun do Věže |

  * Začerněná místa `{n|text}` se odkryjí až při Vhledu alespoň n, jinak se ukážou jako █.
  * Listy se čtou v batohu (předmět „Roztržené listy“ s vlastní ikonou).
  * Díky Vhledu se staré listy s dalšími tajemstvími odkrývají zpětně.
* **Praskliny ve fasádě:**
  * **Postavy:** při Vhledu alespoň 2 a jen při dalším oslovení (asi 12 %) postava na zlomek věty zaváhá (`QuestManager.crackProvider`):
    * Gudwin: „Tohle jsem ti už… ne, promiň. Jsi tu přece poprvé.“
    * Křoví: „někdy si pamatuju, jak jsem měl ruce“
    * Mlsák: „…mám hlad. Ne, to nic.“
  * **Město:** při Vhledu alespoň 4 na zlomek vteřiny problikne jako „STANICE 7“ (1 z 6 příchodů).
  * **Makrodex:** jediná nevinná věta navíc jen u tří druhů (Flori, Mycit, Soulu).
  * **Fragment energie** je „v dlani zvláštně teplý“.
  * **Obchod:** každý nákup má účtenku „S-7/####“.
* Na všechno je dost jen jedna věta nebo zlomek vteřiny. Nic hráči neříká, co to znamená.

## Důsledky
* Akt II může navázat centrálou Kustodiátu (Věž) a plnými Spisy. Vhled je připravený jako měřítko, jak moc se fasáda smí lámat.
* `InsightTest` hlídá:
  * výpočet Vhledu a synchronizaci listů;
  * že se začerněná místa odkrývají postupně a na začátku není žádný list celý čitelný;
  * že listy leží na skutečných uzlech map a objeví se ve správný čas;
  * že praskliny jsou vzácné a jen u prokletých;
  * že náznaky v Makrodexu zůstanou nanejvýš u zhruba každého desátého druhu.
