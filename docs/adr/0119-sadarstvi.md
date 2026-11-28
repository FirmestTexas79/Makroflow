# 0119 – Sadařství: ovocné stromy ve Ztichlém sadu (region 2, povolání 1/3)

**Stav:** přijato · **Datum:** 2026-11-28

## Kontext
Každý region má tři sběrná povolání a jednu minihru. Region 1: Kácení, Těžba, Chytání hmyzu + Výheň (ADR 0118).
Region 2 (Popelavá pláň, Ztichlý sad, Ozvěnový kaňon, Ozvěna): **Sadařství**, **Rybaření**, **Archeologie**
(část aktivní, část AFK) a minihra **Alchymie**. Tohle ADR je první povolání.

## Rozhodnutí
- `Skill.ORCHARD` („Sadařství“) je sběrná dovednost jako těžba – AFK, efektivita podle nástroje, levelu a stromu,
  pasivní bonus = šance na dvojitý kus, strom ze šablony `gathering(...)` (Sadař I–III, Jistá ruka, Plný koš, Zralé do rána,
  Mistr sadař; synergie „Ovocná šťáva“ → XP za Pěstování).
- **Ovoce** (`Resource.FRUIT_*`): jablko, hruška, pomeranč, banán. Ikony 12 × 12 stínované z barevných ramp
  (`tools/skillart/gen_fruit_art.py` → `skills/FruitArt.kt`, generovaný soubor).
- **Stromy** ve Ztichlém sadu (`GatherSpot` 10–13): Jabloň 10/180 s/10 XP, Hrušeň 30/360/25, Pomerančovník 70/720/60,
  Banánovník 120/1080/110. Čtyři stromy z řad podél cesty se v mapě kreslí bez ovoce a ovoce na ně dokresluje
  `OrchardFruitView`: na stromě, kde se češe, každé místo v koruně prochází stadii květ → malé zelené → nalévá se → zralé
  (posunutě a podle postupu rozpracovaného kusu), jinak na stromě jen kvete. Uzly `sad_*` stojí pod korunou.
- **Česák** (`GearSlot.PICKER`) v nové záložce vybavení **TOOLS 2** (česák; prut, lopatka a štětec jsou zatím zamčené
  sloty pro Rybaření a Archeologii). Starý česák (síla 10) visí na žebříku u jabloní – dostaneš ho u prvního ovocného
  stromu. Měděný / stříbrný / zlatý jako ostatní nástroje: kov + dřevo + ovoce o stupeň horší + bobule.
- **Sadařův set** (EQUIPS): Slaměný klobouk, Sadařova zástěra, Plátěné kalhoty, Sadařovy holínky – každý kus z jednoho
  druhu ovoce a surovin z regionu 1; celá sada +10 % XP, +5 % dvojitý kus, +2 h AFK pro Sadařství.
- Deník: sekce „Ze sadu“, statistiky dovednosti; dílna: sekce „Sadařův set“, česáky mezi nástroji.
