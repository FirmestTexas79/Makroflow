# 0050 – Havíř Vendelín, šichtovní kniha a oprava deníku

**Stav:** návrh (větev `STORY`) · **Datum:** 2026-09-30

## Kontext
* Doly (0049) neměly žádný příběh. Hráč chtěl postavu, ne Makromona:
  * měla by ho naučit chytat hmyz,
  * a pak vyzkoušet, jestli opravdu zná všechny sběrné mechaniky světa: vrátit se až po 3 hodinách AFK kácení, těžby i chytání hmyzu.
* Deník úkolů řadil kapitoly podle poslední změny, takže stránky přeskakovaly.
* Záložka vpravo nahoře zakrývala text.
* Tajný quest nebyl na pohled ničím zvláštní.

## Rozhodnutí
* **Havíř Vendelín** (`story/Vendelin.kt`, portrét `tools/mapgen/gen_npc_vendelin.py`):
  * Člověk, poslední havíř ze šichty „Sektoru Hlubiny“.
  * Na kabátě má vybledlou nášivku S-7. V kahanu mu místo oleje svítí jiskřivky. Jedno oko má zakalené.
  * Stojí u lucerny na nakládací rampě a dýchá. Na bedně vlevo dole leží šichtovní kniha.
* **Quest „⚒ Doly“** (`QuestRegistry.MINES_QUEST`, vedlejší linka):

  | # | Fáze | Požadavek | Poznámka |
  |---|---|---|---|
  | 1 | Světlo v hlubině | sebrat starou síťku (`mines_net_taken`) | Síťka je na háku přivázaná drátem, dokud se hráč s Vendelínem nepozná. Na násadě je 212 zářezů. |
  | 2 | Naplň kahan | odevzdat 10 jiskřivek | Vendelín vysvětlí AFK chytání a efektivitu. Náznak Lumenců: mušky „čekají, až jim řekneš jméno“. |
  | 3 | Lepší síťka | mít měděnou síťku (nový typ `HAVE_ITEM`) | |
  | 4 | Pláč krystalů | odevzdat 5 krystalových mušek | Krystaly „dorůstají, jako by nad námi pořád někdo plakal“ – Drakiřiny oči. |
  | 5 | Šichtovní kniha | 3 h kácení + 3 h těžby + 3 h chytání hmyzu od začátku fáze (nový typ `AFK_MINUTES`) | |
  | 6 | Podpis | podepsat se do knihy (`mines_book_signed`) | Odměna: **Havířský kahan** (talisman: +10 % XP a +2 h AFK pro těžbu, kácení a chytání hmyzu). |

* **Šichtovní kniha** je kalibrační protokol Kustodiátu:
  * Předchozí zápisy „Okruh 211“ a „212“ mají stejné hodiny a jsou přeškrtnuté.
  * Hráč je **Okruh 213** (navazuje na roztržený list č. 5) a všechny podpisy jsou psané jeho rukou.
  * Podpis přidá 1 bod Vhledu.
  * Na železných dveřích pak hráč pozná, že „NEKOPEJTE HLOUBĚJ“ vyryl Vendelín.
  * Po dokončení Vendelín hráče zdraví jako nováčka, protože zapomíná, a diví se, proč má hráč jeho kahan.
* **Odpracovaný čas**:
  * Každé vybrání kusů přičte do `stat_worked_<dovednost>` čas spotřebovaných kusů.
  * `SkillStore.workedSeconds` k tomu připočte i právě běžící, ještě nevybranou činnost, nejvýš do stropu AFK. Hráč se tedy může rovnou vrátit za Vendelínem.
  * Na začátku fáze se uloží výchozí stav (`quest_afk_*`, skryté v inventáři) a počítá se jen rozdíl.
  * Postup fáze = minuty nejslabší dovednosti. Deník ukazuje každou dovednost zvlášť, např. „Kácení 2 h 10 min / 3 h ✓“.
* **Deník**:
  * Kapitoly jsou seřazené podle příběhu (Město, Louka, Hory, Hvozd), pak vedlejší (Doly), pak tajné (Háj).
  * Názvy kapitol jsou v `QuestDefinition.chapter`.
  * Záložka je jen stužka nad hranou desek a do textu nezasahuje.
  * Tajná linka (`secret = true`) má **zlatolesklou stránku**: papír dozlatova, dvojitý zlatý rámeček s rohovými kosočtverci, přes stránku přejíždí lesk a třpytí se hvězdičky (`GoldLeafDrawable`).

## Důsledky
* Nové typy fází `HAVE_ITEM` a `AFK_MINUTES` jdou použít i v dalších světech.
* Kdo sebral starou síťku ve verzi 0049 ještě bez Vendelína, má první fázi splněnou hned.
