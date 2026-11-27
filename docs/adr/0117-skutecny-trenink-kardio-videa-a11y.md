# 0117 – Skutečný trénink přebíjí plán, kardio v rychlém zápisu, menší videa, přístupnost

**Skutečný trénink (`energy/ActualTraining`).** Cíl dne se počítal z týdenního plánu. Teď plán přebije, co se opravdu stalo:
- aktivity z hodinek (`wearable_activity`): síla → MET model se skutečnou délkou, kardio a ostatní sporty → kcal z hodinek
  minus klidová část (`Exercise.Measured`), bez kcal MET 7 / 4,5; chůze se nepočítá (je v krocích);
- deník sérií (≥ 3 série → délka od první do poslední + 3 min, 15–180 min);
- rychlý zápis „Jdu na trénink“ (síla i kardio);
- uzavřený den bez skutečnosti: plán vypadne jen tam, kde bychom trénink viděli (hodinky ten den synchronizovaly; u síly
  i pravidelné zapisování sérií – aspoň 2 dny z posledních 14). Kdo nic nezapisuje, tomu plán zůstává.
Silný důsledek: adaptivní výdej i historie dostávají přesnější modelový výdej.

**Kardio v rychlém zápisu.** Druh kardia (běh, kolo, švihadlo, schody, plavání, jiné; MET z Compendia) se ukládá místo
varianty A/B; naplánované 30 min, hotové skutečnou délkou.

**Videa konců.** H.264 `-preset slow -crf 32`, AAC 128 kb/s: 35 MB → 22 MB v APK bez viditelné ztráty u pixel artu.

**Hlavní vlákno.** Dashboard a dialog vody počítají cíle mimo hlavní vlákno; `runBlocking` kolem synchronních DAO
zmizel (nic nedělal); medián kroků čte jen 14 dní místo celé tabulky (historie volá výpočet pro každý den).
`allowMainThreadQueries` zůstává – Makrosvět na něm stojí; odstranit až po přesunu jeho čtení.

**Přístupnost a úklid.** Ikonová tlačítka mají popis pro čtečku, dekorace `importantForAccessibility="no"`.
Smazáno 52 nepoužitých obrázků (mj. staré sprity s cizími postavami), 2 rozložení, 96 textů a 17 barev; obrázky
skládané jménem za běhu (`achievement_`, `recipe_`, `food_`, `makromon_`, zvuky v `raw`) zůstaly.
