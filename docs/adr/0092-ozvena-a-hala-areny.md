# 0092 – Město Ozvěna a vstupní hala Arény

Stav: přijato (2026-10-22), větev `MULTI`, issue #27 a #30

- **Ozvěna** (`cave/OzvenaCity.kt`, `res/drawable-nodpi/ozvena.png` z `tools/mapgen/city/gen_city.py`): město 384 × 656
  s **přiblíženou kamerou** (`artPixelsAcross = 128`), hráč nikdy nevidí celé město. Vstup severní branou z pláně
  (dřív zavřená mříž). Propletené ulice s okruhem kolem náměstí, ~20 domů po prvcích (omítka se skvrnami,
  nároží, cihly pod opadanou omítkou), stromy ve stylu B/W, vržené stíny, kašna se sochou s velkými vahami,
  Věž Řádu Váhy se zlatými vahami, zábradlí nad propastí s výtahem, koruna Lumiry v mlze.
  Klepnutelná místa: pekárna, dům Vessy, vývěska, kašna, věž, výtah, sklad (zatím texty), Aréna.
- **Hala Arény** (`cave/ArenaHall.kt`, `arena_hall.png` z `tools/mapgen/gen_arena_hall.py` + `arena/hall_parts.py`):
  vstup z Arény ve městě už nevede rovnou do souboje. V hale je velitel bojového cechu (výklad po stránkách,
  na konci „Do arény“), Síň šampionů se třemi zlacenými rámy – aplikace do nich vloží Makromona prvních
  tří hráčů žebříčku (offline legendy cechu), plakát HLEDÁ SE s Drakirrou (razítko „Objekt 019“ – předzvěst
  souboje v aréně), tréninkový kout s tipem na tréninkový bonus, sochy rytířů u vchodu, mozaika cechu v podlaze.
  Do soubojů se jde branou na konci haly (`ArenaFragment`); tlačítko Arény v HUD zůstává.
- Zóna 2 v deníku obsahuje i Ozvěnu (`gen_zone2.py`), hala patří do zóny 2 jako interiér.
