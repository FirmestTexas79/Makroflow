# 0079 – Hodnocené zápasy a žebříček

Stav: přijato (2026-10-09), větev `MULTI`

- Dva režimy v Aréně: **⚡ Rychlý** (náhodný soupeř z trenérů arény a duchů, o body se nehraje,
  penízky jako dřív) a **🏆 Hodnocený** (body a rank; jen s přihlášeným účtem kvůli žebříčku).
- **Ranky**: Bronz 0, Stříbro 100, Zlato 250, Platina 450, Diamant 700, Mistr 1000 bodů.
  Výhra +20, prohra −12; ze začátku ranku se nespadne (jen v Bronzu jde až na 0).
  Odchod z hodnoceného zápasu před koncem = prohra.
- **Soupeři sílí s rankem** (`Ranked.opponent`): počet Makromonů = pořadí ranku (Bronz 1 … Mistr 6),
  level = průměr tvého týmu −2 … +3, od Zlata vzácní, od Diamantu epičtí. Soupeř je daný počtem
  odehraných zápasů – v menu vidíš, koho potkáš; po zápase je další. Jména ze světa posilovny.
- **Odměny**: 3 + 2 × rank penízků za každou hodnocenou výhru, jednorázově 40 × rank za nový rank.
- **Žebříček**: body v `arena/{uid}.points` (+ `rankedAt` serverový čas). Top 50 v dřevěném okně,
  vlastní řádek zvýrazněný, pořadí přes agregační dotaz `count()` („#12 v žebříčku“).
- **Ochrana**: pravidla Firestore pustí změnu bodů nejvýš o +60 a ne častěji než po 15 s,
  nový dokument začíná na 0. Body z jiného telefonu se při otevření Arény převezmou (vyšší vyhrává).
