# 0028 – Obrázek náčiní a kalkulačka kotoučů u zápisu série

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-25

## Kontext
U zápisu série je jen číslo váhy. Samuel chtěl kalkulačku kotoučů a k tomu obrázek náčiní,
který se mění s vahou: jednoručky rostou, na osu a stroj se nakládají kotouče, na kladce
přibývá závaží. U jednoruček volba 1 / 2, u činky rovná / EZ, u stroje obě / jedna strana.

## Rozhodnutí
* **2D Canvas s animací** (`EquipmentView`), ne 3D – plynulé na každém telefonu, bez knihoven,
  styl odpovídá zbytku aplikace (plochá grafika s přechody a stínem).
* **Náčiní** (`Rig`, čistý Kotlin, testy) a co znamená zapsaná váha:
  | Náčiní | Zapsaná váha | Obrázek |
  |---|---|---|
  | 2 / 1 jednoručka | jedna jednoručka | hlavy rostou (logaritmicky 1–60 kg) |
  | osa + kotouče | celkem včetně osy 20 kg | kotouče na každé straně |
  | rovná / EZ činka | pevná činka celkem | konce rostou (5–60 kg) |
  | stroj – obě / jedna strana | na jednu stranu | kotouče na rameni stroje |
  | blok závaží (kladka, stroj s kolíkem) | celkem | bloky po 5 kg na konci lanka + přídavné závaží |
* **Kotouče**: 20, 10, 5, 2,5 a 1,25 kg, nakládá se od největšího (25 kg na stranu = 20 + 5,
  jak Samuel popsal). Co nejde poskládat, popisek ukáže („chybí 0,5 kg na stranu“).
* **Výchozí náčiní podle cviku** (bench, dřep, mrtvý tah … = osa s kotouči; skull crusher, EZ
  bicepsový zdvih = EZ činka; stroje na kotouče vs. stroje s blokem; jednoruční cviky = 1 jednoručka).
  Volba se pamatuje pro každý cvik zvlášť.
* **Animace**: nové kotouče najedou z kraje, odebrané odjedou, zbytek se plynule posune; hlavy
  jednoruček a bloky závaží rostou plynule; po změně váhy obrázek krátce „žuchne“.
* Panel zápisu série je teď posouvatelný (s obrázkem je vyšší).
* Debug: `EquipmentPreviewActivity` ukáže všechna náčiní se třemi sadami vah (`--es w 0|1|2`,
  `--ez cycle true` pro animace).

## Důsledky
* U strojů na kotouče se váha zapisuje na jednu stranu – dosavadní zápisy téhož cviku by měly
  být stejně (model síly porovnává jen v rámci cviku).
* Sada kotoučů je pevná; vlastní sada (např. s 25 a 15 kg) by šla přidat do Nastavení.
