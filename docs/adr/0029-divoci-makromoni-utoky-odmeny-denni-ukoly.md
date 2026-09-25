# 0029 – Levely podle lokality, útoky z poolu, penízky za výhru a denní úkoly

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-25

## Kontext
S více lokalitami (Město, Louka, Hvozd, Hory, jeskyně) měl divoký Makromon pořád level hráčova
Makromona ±2 – lokalita neznamenala obtížnost. Chycený Makromon se sice uložil s levelem
z divočiny, ale s XP 0, takže ho první odměna XP shodila zpět na level 1–2. Každý druh měl
vždy stejné útoky a za výhru nebyla žádná odměna kromě XP.

## Rozhodnutí
### Level podle lokality (`WildLevels`)
| Lokalita | Level |
|---|---|
| Město, Louka | 2 nebo 3 (po 45 %), občas 4 (10 %) |
| Hvozd | 3–6 (Samuel nezadal, mezi Loukou a Horami) |
| Hory | 4–8 |
| Jeskyně | 6–12 |
Druhy Makromonů se dál berou podle `wildBiome` (jeskyně = Hory), level podle skutečné lokality.
Strážci a legendy mají dál pevný level.

### Chycení
Chycený Makromon si nese level **a útoky** z divočiny; XP začíná na hranici svého levelu
(`PokemonLevelCalc.xpForLevel`). Přičítání XP (`gain`) level nikdy nesníží – opraví i dřív
chycené Makromony (level z divočiny, XP 0). Křivka XP je prodloužená do levelu 30
(do levelu 10 beze změny, dál krok +200 XP).

### Útoky z poolu (`MovePool`, `MoveDex`)
* Pool druhu = jeho základní útoky + útoky z růstové křivky jeho i předchozích vývojových stupňů
  do aktuálního levelu. Silné základní útoky až od levelu: síla ≥ 80 od 6, síla ≥ 95 od 9.
* Divoký Makromon dostane náhodně až 4 útoky: podpisový (první základní – drží typ druhu),
  aspoň jeden útočný, zbytek náhodně s větší šancí pro později učené útoky.
* Typ druhu je nové pole `Makromon.type` – dřív se bral typ prvního útoku, což by s náhodnými
  útoky měnilo účinnost i počítání úkolů „poraz vodní Makromony“.
* Útoky chyceného Makromona se ukládají jako jména (`moveListStr`) a v souboji se použijí;
  prázdná sada = základní útoky druhu (staré záznamy fungují jako dřív). Učení útoku při
  vývoji vychází ze skutečné sady, ne z prázdného seznamu.

### Odměny
* Výhra nad divokým Makromonem: **1–5 makro penízků** (strážci a legendy mají vlastní odměny).

### Denní úkoly (`DailyQuests`, deník → záložka „Denní úkoly“)
* 3 úkoly denně – jeden z Makrosvěta (poraz 2–3 Makromony daného typu, vyhraj 5 soubojů,
  chyť 2, vyhraj 2 souboje v lokalitě), jeden z jídla a pití (vypij osobní cíl vody, sněz 90 %
  cíle bílkovin, 25 g vlákniny, zapiš 4 jídla) a jeden z pohybu či zapisování (kroky, 12 sérií
  v tréninkovém deníku, ranní check-in).
* Výběr je deterministický podle data – stejné úkoly celý den i po restartu. Úkol v lokalitě
  jen tam, kde hráč už někdy vyhrál. Typy jen běžných Makromonů (vodní, ohnivé, travní, normální).
* Odměna 10–50 penízků podle náročnosti, vyzvedává se tlačítkem (jednou za den a úkol).
* Počítadla soubojů jsou v SharedPreferences s datem v klíči (staré dny se mažou); jídlo, voda,
  kroky, série a check-in se čtou z databáze.

## Důsledky
* Jeskyně jsou na začátku těžké – to je záměr (lokalita = obtížnost).
* Počítadla denních úkolů nejsou v cloudu (jsou jen na den); penízky ano (zůstatek se nahraje).
