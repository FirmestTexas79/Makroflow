# 0057 – Spisy Kustodiátu, sny, Mydrus zapomíná a Gudwin počítá

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-10-02 · navazuje na [0047](0047-vhled-a-roztrzene-listy.md)

## Kontext
Bible příběhu popisuje mechaniky, které ve hře chyběly: klinické spisy u Makrodexu, hlášení o snech personálu, Mydrusovo zapomínání a Gudwina, který si pamatuje smrt každého poutníka. Všechno je v duchu zásady z bible: náznaky jsou drobné a pravda o Makromonech zazní až na konci.

## Rozhodnutí
Pravidla a texty jsou v `story/Kustodiat.kt` (čistý Kotlin, `KustodiatTest`).

* **Spisy S-7 (Makrodex)**
  * U chyceného druhu se objeví tmavé tlačítko „Spis S-7/NNN“, ale až od Vhledu 2.
  * Spis je klinický záznam Řádu Váhy: stanice, původ, kolikrát druh poutník odchytil, pozorování a poznámka.
  * Začerněná místa se odkrývají s Vhledem stejně jako u roztržených listů.
  * Místa označená `{99|…}` zůstanou černá celý akt I: původ „lumenský národ, před Přáním“ a lumenská architektura.
  * Spirra je „NÁDOBA, pokus č. 2“, Drakirra má uzavřený spis „pokus č. 1“ a odebrané oči.
* **Sny**
  * Ráno (4–12 h) po aspoň 5 hodinách mimo hru, s Vhledem 3+, padne jednou za ráno 40% šance na hlášení o snu.
  * Sny chodí v pevném pořadí (7 snů: hlad, strom s kopím, veverka u kořenů, medvídek počítá mrtvé, šest očí, zhasnuté město, sen poutníka).
  * Na mapě se ukáže papírová cedule a sen se uloží jako příběhový příznak `dream_N`, takže se synchronizuje.
* **Záložka Spisy v deníku**
  * Je skrytá, dokud poutník nenajde první list nebo se mu nezdá první sen.
  * Obsahuje roztržené listy a hlášení o snech, vykreslené podle aktuálního Vhledu.
* **Mydrus zapomíná**
  * Po vyhnání hniloby Mydrus po čase poutníka nepozná. Poprvé za 2 dny, pak za 4, 3 a dál každé 2 dny.
  * Na mýtině začne stejnou větou jako při prvním setkání.
  * Poutník mu musí připomenout, co spolu zažili, u jezírka, v houštině a u Starého dubu. Každé místo vrátí kousek vzpomínky.
  * Pak ho Mydrus na mýtině zase pozná. Repliky jsou každým kolem smutnější.
  * Stav je jen v GamePrefs (`mydrus_*`).
* **Probuzení po smrti**
  * Místo hlášky po smrti postavy (0052) vítá poutníka na prahu Gudwin.
  * Počítá probuzení (`whiteout_count`). S Vhledem 3+ přizná, že počítá všechny, a s Vhledem 5+ občas zmíní 212 poutníků před tebou.
