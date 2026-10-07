# 0065 – Energie Makrosvěta, overstim a propojení s tréninkem

**Stav:** schváleno, fáze 1 (jádro, bar, ceny akcí) implementována · **Datum:** 2026-10-07

## Kontext
Makrosvět je teď volně přístupný a dá se projít naráz. Obsah se tak spotřebuje příliš rychle
a herní část jen slabě souvisí s funkční (kroky jako brána do Hor, coiny za streaky).

Cílové použití (Samuel + tester): hráč si Makrosvět otevírá **v pauzách mezi sériemi v posilovně**,
něco málo odkliká a energie mu vystačí na většinu pauz jednoho tréninku.

Tester navíc připomněl, že ne každý má pevný plán tréninků. Chybí rychlý zápis
„jdu na trénink“ jako jednorázová aktivita.

## Rozhodnutí

### 1. Energie hráče
* Bar nahoře v Makrosvětu: **základ 0–100** + **overstim až +50** (zobrazení „100 +32“, overstim zlatě).
* Utrácí se **nejdřív overstim**, pak základ. Overstim tedy nikdy nepropadne a je vidět, že se zápis vyplatil.
* Energie se **nikdy nedoplňuje během hraní** (žádná pasivní regenerace po minutách).
* V kódu balíček `pokemon/stamina` (ne `energy` – ten už patří k modelu kalorií, docs/adr k fázi A).
  V UI se to jmenuje **Energie**.

### 2. Doplnění přes noc
Základ se doplní na 100, když platí obojí:
1. je jiný kalendářní den než při posledním doplnění,
2. od posledního opuštění Makrosvěta (`onPause`) uběhly aspoň **4 hodiny**.

Kontrola běží v `onResume` mapy. Kdo hraje přes půlnoc, doplnění v 00:01 nedostane. Dostane ho
při prvním příchodu po čtyřhodinové pauze. Posun hodin dozadu (`now < lastExitAt`) se ignoruje.
Při doplnění se **overstim maže** (rozhodnuto 2026-10-07): každý den začíná na čistých 100 a overstim
se dá získat jen zápisy toho dne.

### 3. Odměny z funkční části (tabulka A)
Odměňuje se **zápis a trefení cíle, nikdy množství**. Každý zdroj má denní strop, aby se energie
nedala farmit.

| Zdroj | Energie | Denní strop | Poznámka |
|---|---|---|---|
| Ranní rituál | +15 | 1× | |
| Zapsané jídlo | +5 | 4× (20) | za jídlo, ne za kalorie |
| Voda 50 % cíle | +5 | 1× | |
| Voda 100 % cíle | +10 | 1× | |
| Makra v cílovém pásmu | +10 | 1× | bílkoviny ±10 %, kalorie v plánu; přebytek se neodměňuje |
| Kroky | +5 za každé 2 000 | 4× (20) | |
| Zapsaná série | +2 | 15× (30) | jen při aktivním tréninku (bod 5) |
| Dokončený trénink | +15 | 1× | ≥ 6 sérií nebo potvrzený rychlý zápis |
| Dokončené kardio | +10 | 1× | rychlý zápis kardia ≥ 15 min |
| Den volna („táborák“) | +10 | 1× | v plánovaný den volna, aby odpočinek nebyl trest |

Maximum za den je zhruba +145. Odměna jde nejdřív do základu do 100, zbytek do overstimu do +50.

### 4. Ceny akcí (tabulka B)
| Akce | Cena | Místo v kódu |
|---|---|---|
| Přechod mezi lokacemi | 3 | `MakromonMapActivity.enterBiomeAtNode` (viz pravidla níže) |
| Divoký souboj | 4 | `startWildEncounter` |
| Strážce / speciální souboj | 15 | `startSpecialBattle` |
| Spuštění sběru (AFK) | 2 | `openGatherSpot → onStart` (vybrání výnosu zdarma) |
| Výroba u stolu | 2 | `openCraftingTable` |
| NPC, deník, Makrodex, batoh, obchod, cinematiky | 0 | |

**Pravidla přechodů** (rozhodnuto 2026-10-07):
* **Překliknutí zdarma:** návrat do lokace, ze které hráč přišel před méně než **5 minutami**, nic nestojí
  (omylem otevřený přechod a hned zpátky). Po 5 minutách se platí normálně, postava tam opravdu musí dojít.
* **Teleport z deníku** (`ZoneOne`, docs/adr/0052): **10 teleportů za den zdarma**. Pak teleport stojí
  tolik, kolik by stála chůze: nejkratší cesta po spojnicích mapy zóny (`assets/zone/zone1.json`) × 3
  za každý přechod. Přes tři lokace tedy 9. Když energie nestačí, `ZoneOne.teleportBlock` vrátí nový
  důvod `Block.Energy(need, have)` a mapa zóny ukáže, kolik chybí. Výpočet cesty je čistá funkce
  v `ZoneOne`, pokrytá testy.

Cena se ukazuje přímo na tlačítku nebo v dialogu. Když energie nestačí, akce se neprovede a dialog
nabídne, co ji doplní (např. „Zapiš oběd +5“), s přímým odkazem do funkční části.

### 5. Propojení s posilovnou
* **Aktivní trénink** = uživatel spustil „Jdu na trénink“ nebo zapsal sérii za posledních 45 min.
* Po zápisu série se na pár sekund ukáže bublina **„+2 ⚡ · Skok do Makrosvěta“**. Neblokuje další zápis.
* V Makrosvětu během aktivního tréninku běží nahoře **odpočet pauzy** vedle baru energie.
* Po uplynutí pauzy se hra zapauzuje a ukáže výzvu **„Pauza skončila, jdi na to!“** s tlačítky
  **„Jdu cvičit“** (rovnou zápis další série) a **„Ještě chvilku“** (+30 s, jen jednou za pauzu).
  Rozehraný souboj se uloží.
* Délka pauzy: výchozí **3 min**, nastavitelná v nastavení, později per cvik v šabloně.
* V nastavení dva přepínače: bublina „Skok do Makrosvěta“ a připomínka konce pauzy.

### 6. Rychlý zápis tréninku („Jdu na trénink“)
* Plus tlačítko v Plánu: čas **Teď / Za 15 min / Vlastní**, typ (Push, Pull, Legs, kardio, jiné),
  volitelně šablona a varianta A/B.
* Ukládá se jako **jednorázová aktivita**, ne do týdenního plánu.
* „Teď“ rovnou spustí aktivní trénink (bod 5). Při ukončení se zeptá „Hotovo?“ a teprve potom dá
  odměnu za dokončený trénink nebo kardio.

### 7. Datový tok
Funkční a herní část nikdy neběží zároveň, takže se použije **stávající most `game_events`**
(`GameEventEntity`). Producenti jen zapisují události:

`CHECKIN_DONE, MEAL_LOGGED, WATER_HALF, WATER_FULL, MACROS_HIT, STEPS_2K, SET_LOGGED,
WORKOUT_DONE, CARDIO_DONE, REST_DAY`

Makrosvět při `onResume` přečte nové události (od posledního zpracovaného `id`), uplatní denní
stropy a připíše energii. Stav energie (základ, overstim, poslední zpracované id, datum doplnění,
`lastExitAt`) leží v jedné malé tabulce nebo v `GamePrefs`. Ledger je append-only, takže jde zpětně
auditovat i přepočítat, a do diplomky se dá vyhodnotit, které zvyky hráči plní.

Nová entita pro bod 6: `activity_log` (`date, startAt, kind, template?, variant?, durationMin?,
source = ADHOC|PLAN, completedAt?`).

### 8. Pravidla zdraví
* Žádná odměna za jedení navíc ani za trénink nad rámec; strop u sérií, makra jen v pásmu.
* Den volna má vlastní malou odměnu.
* Energie nikdy neblokuje funkční část aplikace.

## Simulace (`scratchpad/energy/sim.py`, průměrná akce 3,2 energie ≈ 40 s)
| Den | K dispozici | ≈ akcí | ≈ minut hraní |
|---|---|---|---|
| Nic nezapsáno | 100 | 31 | 21 |
| Běžný den bez tréninku (rituál, 3 jídla, voda 50 %, 6 000 kroků) | 150 | 47 | 31 |
| Push day, 20 sérií, vše splněno | ~210 během dne | ~65 | ~44 |

Posilovna: 20 pauz × 2 akce ≈ 128 energie. Před posilovnou přibude ~30 (rituál, 2 jídla, voda),
během tréninku ~45 (série + dokončení). Po tréninku zbude ~47 a večerní zápisy přidají ~35.
Disciplinovaný den tedy dá zhruba dvakrát víc hraní než den bez zápisů, ale ani prázdný den
nehru nezablokuje.

## Fáze implementace
1. `stamina` jádro: stav, doplnění přes noc, ceny akcí, bar v UI, zpracování `game_events` + unit testy.
2. Producenti událostí ve funkční části (rituál, jídlo, voda, makra, kroky, série).
3. Rychlý zápis tréninku + `activity_log` + aktivní trénink.
4. Smyčka posilovny: bublina po sérii, odpočet pauzy v Makrosvětu, výzva konce pauzy, nastavení.
5. Ladění hodnot na testerech (tabulky A a B jsou jediné místo, kde se čísla mění).

## Otevřené otázky
* AFK sběr už má vlastní strop (`afkCapHours`). Stačí cena za spuštění 2, nebo ho z energie vyjmout úplně?
