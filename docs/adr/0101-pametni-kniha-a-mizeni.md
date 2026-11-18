# 0101 – Pamětní kniha a mizení obyvatel Ozvěny

Stav: přijato (2026-11-18), Akt II, issue #28 · navazuje na ADR 0093 a 0095

## Kontext
Bible říká, že v Ozvěně mizí vedlejší obyvatelé (nejvýš 5 z asi 10) a Pamětní kniha drží, co Bezejmenní nesmažou.
Ve městě ale žili jen tři vedlejší obyvatelé a dva z nich patří lince sadu (Nelle, Tobben).

## Rozhodnutí
* **Šest nových obyvatel** (Makromoni z existujících sprajtů): pradlena **Corra** (Aqulin), hodinář **Oswin** (Axlu, hodiny stojí
  na 6:12), sourozenci **Pell a Pip** u kašny (Finlet, dokončují za sebe věty), pošťák **Lio** (Grifben, dopisy bez adres),
  **Sabeth** (Phantil, melodie, kterou nikdo nezná). Uzly a kolize jsou v `gen_city.py` i `OzvenaCity.kt`; obrázek mapy se nemění.
* **Aktivní den** = den, kdy hráč s knihou vstoupí do Ozvěny. Každý 3. aktivní den zmizí další v pevném pořadí
  **Corra → Oswin → Pip → Lio → Jarro** (`MemoryBook.goneAfter`). Brann, Vessa, Nelle, Pell a Sabeth nemizí nikdy;
  Tobben dál jen koncem A u Fenna.
* **Scéna zmizení** se přehraje při vstupu do města (první dosud neviděné zmizení), na místě zůstane stopa (prádlo, tikající hodiny,
  šupinka, brašna, koště). Klepnutí na místo stopu zopakuje. Pell po zmizení Pip mluví k prázdnému místu.
  Vessa při dalším oslovení zmizení okomentuje – jinak, když hráč jméno zapsal.
* **Pamětní kniha (varianta B – chrání vzpomínku, ne postavu)**: klíčový předmět v Batohu. První jméno dá Vessa, pak jedno za každý
  den s ranním rituálem od získání knihy. Zapsaný obyvatel zmizí taky, ale zůstane po něm jméno a jedna věta; nezapsaný nechá
  prázdný řádek. Zapsat jde i Vessu (její jméno na zdi chybí).
* Stav: `ozvena_gone_<id>` a `ozvena_book_<id>` jsou příznaky příběhu (synchronizují se); aktivní dny, datum knihy a viděné scény
  v `GamePrefs`. Hráč nikdy nepřijde o data ani Makromony; vynechaný rituál se netrestá.

## Testy
`MemoryBookTest` (pořadí a strop mizení, volná jména, stavy řádků, klíče příběhu), `OzvenaArenaHallTest` (11 obyvatel, repliky).
