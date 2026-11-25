# 0106 – Zóna 3 (Akt III – Hlubina): Stanice 8

Stav: návrh (2026-11-25), první krok implementován. Celý návrh zóny: projekt `lore/akt3-hlubina-navrh.md`.

- Zóna 3 = region pod světem, kde visí Spící. Lokace shora dolů: Stanice 8 (hub) → Pozorovatelna B → Svah bezejmenných
  → Les pod korunou → Kmen (Trpitel) → Střed kořenů (finále, mimo rozsah).
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
