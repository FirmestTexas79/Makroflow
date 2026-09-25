# 0027 – Série z kamery do deníku a opakovaná jídla

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-25

## Kontext
1. Sledování činky kamerou (ADR 0005) měří opakování, tempo a rychlost, ale do tréninkového
   deníku (ADR 0023–0025) se série musela zapsat znovu ručně.
2. Jídla se často opakují (stejná snídaně, stejný tréninkový den). Zapisovat je po položkách je
   zbytečná práce – Samuel chtěl „dej mi znovu včerejší snídani“ a šablony celého dne.

## Rozhodnutí
### Kamera → deník (`CameraToDiary`, čistý Kotlin, testy)
* Dřep → `back_squat`, bench → `bench_press`, tlaky nad hlavu → `overhead_press`, mrtvý tah → `deadlift`.
* Zapíše se jen série se zadanou zátěží (bez váhy nedává deník odhad síly) – jinak nápověda v souhrnu.
* Opakování = rozpoznaná opakování; **pomalé spouštění** = naměřená průměrná excentrická fáze ≥ 2,5 s.
* **RIR**: když poslední opakování kleslo na rychlost do 0,05 m/s nad MVT cviku (rychlost při 1RM,
  ADR 0006), série šla prakticky do selhání → RIR 0. Jinak výchozí 2 – rychlost sama rezervu
  přesně neurčí (závisí na cviku i člověku), proto jde v souhrnu opravit jedním klepnutím (0–4+).
* `createdAt` = začátek série (spojení se sérií kamery). Souhrn ukazuje, co se zapsalo, výběr RIR
  a „Nezapisovat tuto sérii“. Vypínač v Nastavení → Zobrazení („Série z kamery do deníku“, výchozí zapnuto).

### Opakovaná jídla (`MealRepeat`, čistý Kotlin, testy)
* **Jídla dne**: záznamy seřazené podle času, mezera > 60 min = nové jídlo. Název podle začátku:
  snídaně 4:00–10:29, dopolední svačina do 11:29, oběd do 14:29, odpolední svačina do 17:29,
  večeře do 21:29, jinak pozdní jídlo; dvě jídla v jedné době „Oběd 2“.
* **Snacky → Přidat → Zopakovat jídlo**: šablony a jídla posledních 7 dní. Klepnutí přidá jídlo
  na dnešek s aktuálním časem, „Celý den“ přidá všechno s původními časy (aby se dal zase rozdělit
  na jídla). Snackbar se „Zpět“ vrátí celé přidání.
* **Šablony**: záložka u jídla nebo dne → název → tabulka `meal_templates` (DB v38, migrace 37→38)
  + Firestore `meal_templates/{createdAt}`; položky jako text (`MealRepeat.encode`, oddělovače
  U+001E/U+001F – bez org.json, aby šlo testovat na JVM). Naposledy použité jsou nahoře, dlouhý
  stisk šablonu smaže. Kolekce je v `USER_COLLECTIONS` (smazání účtu).
* Přidané položky jsou kopie (makra za porci), ne odkazy na potraviny – šablona funguje, i když
  potravinu ze seznamu smažeš.

## Důsledky
* Celý den ze šablony se zapíše hned celý (i večeře ráno) – jde o plánovaný den; kdo chce jen
  část, přidá jednotlivá jídla.
* Série z kamery bez zátěže do deníku nejdou; zátěž se v trackeru pamatuje mezi sériemi.
