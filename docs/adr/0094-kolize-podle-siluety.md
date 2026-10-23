# 0094 – Kolize podle siluety objektů

Stav: přijato (2026-10-23), větev `MULTI`

- Mapy jsou jedna vrstva a hráč se kreslí přes všechno. Kolize jen u paty (spodní třetina domu, kmen stromu) proto
  dovolila jít „za“ dům nebo strom – na obrazovce to vypadalo jako chůze skrz.
- Nově se v generátorech Aktu II (město, pláň, sad, kaňon, hala Arény) blokuje **celá silueta** objektu
  (`tools/mapgen/walkmask.py`): díry v řádcích se zaplní (větve, ramena vah) a silueta se rozšíří o 2–3 px
  do stran (půl šířky postavy). Zábradlí nad propastí blokuje jen spodek – hráč stojí před ním.
- Obyvatelé Ozvěny mají v mapě chůze malý obdélník u paty, skrz Makromony se taky nechodí.
- Posunuté uzly, které skončily uvnitř siluety: pláň `hajek` (124, 244), sad `s5` (80, 238) a ovocný strom
  v sadu o kus níž (průchod k boudě), město `venka` (96, 256) a Vessa (78, 256); vlajka cechu v hale stojí
  za stolem (bez kolize), aby zůstala volná cesta po koberci k bráně.
