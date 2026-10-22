# 0093 – Obyvatelé Ozvěny

Stav: přijato (2026-10-22), větev `MULTI`, Akt II (issues #26–#34)

- Ve městě stojí pět postav ze stejné Sunnyside předlohy jako hráč (`tools/mapgen/gen_npc_ozvena.py`: účes z aseprite,
  přebarvené vlasy a oblečení, doplněk kreslený podle polohy hlavy a trupu → `npc_<id>_map.png`, pás 4 snímků idle,
  1 px spritu = 2 dp jako hráč, každý dýchá jiným tempem): pekař **Bohoušek** u pekárny, písařka **Věnka** u svého domu,
  babička **Lída** u stánku, metař **Jarda** na severní ulici a pan **Kořínek** s novinami u lavičky.
- Bohoušek říká každý den totéž (smyčka). Věnka hráče „zapíše“, její jméno na zdi chybí. Lída, Jarda a Kořínek mají
  tři repliky střídané po dnech (`OzvenaCity.residentLine`) – drobné trhliny ve fasádě (popel je „pyl“, dlažba je
  „vždycky bílá“, noviny jsou pořád z „úterý třináctého“).
- Nové uzly `lida`, `jarda`, `korinek` (gen_city.py i OzvenaCity.kt). Obrázek města ani mapa chůze se nemění.
- Zatím bez mizení obyvatel (pravidla v bibli příběhu): přijde s Pamětní knihou; postavy jsou proto samostatné
  překryvy, ne součást obrázku města.
