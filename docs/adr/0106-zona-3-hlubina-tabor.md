# 0106 – Zóna 3 (Akt III – Hlubina): Stanice 8

Stav: návrh (2026-11-25), první krok implementován. Celý návrh zóny: projekt `lore/akt3-hlubina-navrh.md`.

- Zóna 3 = region pod světem, kde visí Spící. 8 lokací se stupněm pohlcení 1/8 → 8/8: 1 Stanice 8 (hub) → 2 Pozorovatelna B
  → (3 Lanovka nad roklí | 4 Tábor na svahu) → 5 Svah bezejmenných → (6 Chrám Lumenců | 7 Les pod korunou) → 8 Kmen.
  Střed kořenů (finále) mimo rozsah.
- **Sestup schválen:** klec v Sektoru Hlubiny (−2) se otevře den po prvním výhledu na Spící (Septovo „už brzy“;
  den v GamePrefs `abyss_seen_day`, starší hry bez něj se berou jako včera). Poprvé okno „Sestup schválen“,
  pak klec sjede do tábora (`zona3_tabor`, synchronizovaný příběhový klíč). Debug volný výtah ji pustí hned.
- Pojetí (přepracováno 2026-11-25): dutina pod světem se Spící, kterou se Řád pokusil obsadit; kořeny ho pohlcují
  tím víc, čím blíž ke kmeni. Každá lokace je vlastní mapa ve stylu pater Věže (stěna, podlaha, z-řazené sprity se
  stíny, tůně světla, živé detaily). Díly v `tools/mapgen/hlubina/station_parts.py`.
- **Stanice 8** (`ROOT_CAMP`, `RootCamp.kt`, `gen_station8.py`, 176 × 352): skalní římsa, šachta s klecí, beton
  se značením S-8, ubikace s vysílačkou, nástěnka OBSAZENÍ HLUBINY, pozorovatelna A, sodíkové stožáry, ohňová hlídka
  se spálenými kořeny, terminál lanovky, závora ke Svahu (zatím neschváleno), Hunfreyho křída, ruina Lumenců ve
  skále. Bez setkání. Živé detaily InteriorFxView.ROOT_CAMP (blikající reflektory, popel, doutnání, houby, mlha).
- Dokud zóna 3 nemá druhou lokaci, počítá se v deníku pod zónu 2 (záložka Zóna 3 přibude s ní).
- **Stavebnice zóny** (`tools/mapgen/hlubina/`): `common` (palety), `terrain`, `order`, `lumen`, `roots`, `signs`
  (písmo 5×7 s diakritikou; `plate()` smaltované cedule s nýty a stínem písmen, `painted()` šablona s přestřikem,
  oděrem a stékanci), `scene` (z-buffer po pixelech – kořen nebo předmět blíž se tím za ním nepřekreslí; stíny,
  světlo, export, BFS). Lokace = krátký skript `gen_<lokace>.py`.
- **Pozorovatelna B** (`ROOT_WATCH`, `RootWatch.kt`, `gen_watch_b.py`), pohlcení 2/8 – přepracováno: bunkr Řádu
  zapuštěný do skály a betonová terasa nad propastí se zábradlím (jako balkon), sestup po schodech Lumenců; světlomet
  v půlkruhu pytlů, dálkoměr, agregát, stojan plamenometů, nástěnný graf KONTROLA, polní telefon. Pravidlo zóny:
  každá cedule má nápis a je připevněná (zeď, zábradlí, sloupky – `order.mount_plate`). Setkání DUCH a ZEMĚ lv 28–32,
  intro WatchIntro (světlomet přejede terasu a zastaví se na očích). Závora ze Stanice 8 zatím pouští jen debug
  volný výtah – podmínka přijde s fází
  „Výprava 8“ kapitoly VI. Východy k Lanovce nad roklí a k Táboru na svahu zatím nepřístupné.
