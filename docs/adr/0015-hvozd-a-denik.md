# 0015 – Hvozd nad loukou a kompaktní deník úkolů

**Stav:** přijato · **Datum:** 2026-09-24 · navazuje na [0013](0013-jeskyne-kamera-krystaly.md)

## Hvozd
* **Mapa** `forest.png` z generátoru `tools/mapgen/gen_forest.py` (vlastní pixel art):
  bludiště palouků mezi hustými stromy ve čtyřech barvách (smrková, listnatá, olivová,
  podzimní, některé s bobulemi), jezírka s hliněným břehem, vysoká tráva, kvítí, pařezy.
  Průchodnost dávají palouky (kapsle podél hran grafu), zbytek zaroste stromy; koruny se
  kreslí odzadu dopředu a jejich kmeny musí být ≥ 3 px od palouku, aby nezakryly cestu.
* **Propletenější než Starý důl** (hlídá test): 36 uzlů, smyčky (hran ≥ uzlů), ≥ 5 slepých
  uliček, jen rovné vodorovné/svislé chodby. Na konci mýtina s prastarým pařezem – místo
  pro příští příběh.
* **Stejná kamera jako jeskyně** – `CaveMap` dostal `parentBiome` a `isCave`; krystal je
  volitelný. Les má běžné prolnutí a intro s křovím; divocí Makromoni jsou z louky
  (`wildBiome`), u jezírek vodní.
* **Vstup** z horního okraje louky (cesta tam už v mapě vede): `les_sever`. Zámek:
  **5 splněných fází úkolů celkem** – „úkol“ v deníku je fáze (questy jsou zatím jen tři),
  dokončený quest = všechny jeho fáze (`ForestMap.completedTasks`).

## Deník
Dvoustránková kniha přes celou výšku displeje (úzké sloupce, spousta prázdna) → kompaktní
karta uprostřed: hlavička s kapitolou (MĚSTO / LOUKA / HORY), šipkami a počtem splněných
fází; portrét zadavatele, název fáze a cíl jako štítek; příběh (nejvýš ~170 dp, pak se
posouvá); tečky postupu a seznam fází se značkami ✓ / ▸ / ???. Listování šipkami místo
neviditelného klepání na okraje.
