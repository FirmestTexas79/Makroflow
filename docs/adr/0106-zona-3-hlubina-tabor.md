# 0106 – Zóna 3 (Akt III – Hlubina): Tábor výprav

Stav: návrh (2026-11-25), první krok implementován. Celý návrh zóny: projekt `lore/akt3-hlubina-navrh.md`.

- Zóna 3 = region pod světem, kde visí Spící. Lokace shora dolů: Tábor výprav (hub) → Svah bezejmenných → Les,
  který nejde projít → Kmen (Trpitel) → Střed kořenů (finále, mimo rozsah).
- **Sestup schválen:** klec v Sektoru Hlubiny (−2) se otevře den po prvním výhledu na Spící (Septovo „už brzy“;
  den v GamePrefs `abyss_seen_day`, starší hry bez něj se berou jako včera). Poprvé okno „Sestup schválen“,
  pak klec sjede do tábora (`zona3_tabor`, synchronizovaný příběhový klíč). Debug volný výtah ji pustí hned.
- **Tábor výprav** (`ROOT_CAMP`, `RootCamp.kt`, `gen_rootcamp.py`, 176 × 352): bez setkání, stany výprav 3/5/7
  s deníky, vysílačka se Septem, nástěnka „VÝPRAVA 1–7 NEVRÁCENA · VÝPRAVA 8 – SUBJEKT 213 – PROBÍHÁ“, ohniště
  s padajícím teplým popelem, Hunfreyho křída, sestup na Svah zatím neschválen. Živé detaily InteriorFxView.ROOT_CAMP.
- Dokud zóna 3 nemá druhou lokaci, počítá se v deníku pod zónu 2 (záložka Zóna 3 přibude s ní).
