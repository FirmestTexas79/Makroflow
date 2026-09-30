# 0053 – Jiskřivý a Duhový set

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-09-30

## Kontext
* Jediná sada brnění byl Dobrodruhův set (docs/adr/0039) z bobulí, dřeva a rudy.
* Doly (docs/adr/0049) přinesly tři druhy hmyzu – jiskřivky, krystalové a magmové mušky – a zlato se dá těžit v Mechové jeskyni. Chybělo, na co je dlouhodobě utrácet.

## Rozhodnutí
* **Jiskřivý set** (stříbro + jiskřivky + kořist z Makromonů):

  | Kus | Recept | Bonus |
  |---|---|---|
  | Jiskřivá přilba | stříbro 20, jiskřivky 12, chudý plamínek 4 | +15 % XP těžba, +40 efektivita těžby |
  | Jiskřivý kyrys | stříbro 30, jiskřivky 16, dušičky 5 | +15 % XP hmyz, +5 % dvojitý úlovek |
  | Jiskřivé nohavice | stříbro 25, jiskřivky 12, vodní perly 5, březová polena 10 | +15 % XP kácení, +40 efektivita kácení |
  | Jiskřivé boty | stříbro 20, jiskřivky 10, **krystalové mušky 8**, suché listy 6 | +80 efektivita a +10 % XP těžba / kácení / hmyz |

  **Celá sada:** těžba, kácení a chytání hmyzu běží o 1 h déle bez hráče.
* **Duhový set** (zlato + krystalové mušky):

  | Kus | Recept | Bonus |
  |---|---|---|
  | Duhová přilba | zlato 25, krystalové mušky 14, pixie prach 4 | +25 % XP těžba, +6 % dvojitá ruda, +2 h AFK |
  | Duhový kyrys | zlato 35, krystalové mušky 18, dušičky 6 | +25 % XP hmyz, +8 % dvojitý úlovek, +2 h AFK |
  | Duhové nohavice | zlato 30, krystalové mušky 14, vodní perly 6, javorová polena 15 | +25 % XP kácení, +6 % dvojitá polena, +2 h AFK |
  | Magmové boty | zlato 20, **magmové mušky 12**, koule magmatu 2, dračí šupiny 2 | +150 efektivita a +15 % XP těžba / kácení / hmyz, kořist +10 % |

  **Celá sada:** +10 % XP a +5 % dvojitý kus za těžbu, kácení i chytání hmyzu.
* **Bonus sady** (`GearSet`) platí jen s nasazenými všemi čtyřmi kusy a přičítá se v `SkillState` k XP, dvojitému kusu a AFK hodinám. Dobrodruhův set bonus sady nemá.
* **Výroba** – u pracovního stolu na louce, pod stejným uzlem stromu Výroby („Základní vybavení“). Každá sada má v dílně vlastní oddíl a pod názvem popis bonusu celé sady.
* **Ikony** 16 × 16 px (`GearArt.ARMOR`), inspirované sadou pixelových brnění od uživatele:
  * stříbrné kusy mají jiskřivky v hledí, na prsou a na kolenou;
  * zlaté kusy mají duhové krystaly;
  * magmové boty jsou z černého krunýře s ohnivými prasklinami a zlatým lemem.
* Na mapě Zóna 1 jsou názvy lokací větší (46 místo 30 jednotek plátna).
