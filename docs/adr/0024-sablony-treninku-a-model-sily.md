# 0024 – Šablony tréninků PUSH/PULL/LEGS A/B a predikční model síly

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-25

## Kontext
Samuel si trénink vedl v Excelu: na každý typ dne (2× push, 2× pull, 2× legs v rytmu 1/1/1)
předvyplněné cviky, ke každé sérii váha, opakování a poznámka o pomalém spouštění. Deník z ADR 0023
zapisuje série jen u jednotlivých cviků z atlasu. Cílem je (1) nahradit Excel – šablony dnů
a rychlý zápis během tréninku, (2) z dat predikovat 1RM, na jakou váhu je člověk připraven teď
a jaký bude příště, (3) ukázat to trenérovi ve výpisu.

## Rozhodnutí
### Šablony
* `WorkoutTemplates`: klíče `PUSH_A … LEGS_B`, výchozí cviky podle Samuelova tréninku (A i B zatím
  stejné: push 6, pull 8, legs 5 cviků),
  úpravy v tabulce `workout_templates` (DB v37) + Firestore `workout_templates/{klíč}`.
* **Střídání A/B**: pro dnešní typ dne z Plánu se vezme opak varianty, se kterou se typ cvičil
  naposledy (série nesou `template`); když se dnes už podle šablony cvičilo, zůstává. Ručně jde přepnout.
* Do knihovny přibylo 11 strojových/kladkových cviků (hrudní a šikmý tlak na stroji, peck deck,
  kickbacky, tlak na ramena na stroji, upažování na kladce, přítahy, Scottova lavice, hack dřep,
  reverzní motýlek) a později dalších 5 pro Samuelův pull/legs den (stahování V-úchopem, přítah
  širokým úchopem, jednoruční zdvih s oporou, EZ osa, jednonožný legpress) – s technikou a latinskými názvy.
* Série nese příznak **pomalé spouštění** (`slowEccentric`).

### Model síly (`StrengthModel`, čistý Kotlin, testy)
1. **Série → odhad 1RM**: Epley `w · (1 + r/30)`, kde r = „ekvivalentní opakování do selhání“ =
   opakování × 1,15 u pomalého spouštění (se stejnou vahou jich člověk udělá méně) + 1 opakování
   předpokládané rezervy. Nejistota σ = 3 % + 0,2 % za opakování, nad 20 opakování se nepočítá.
2. **Trénink → jedno pozorování**: nejlepší série dne (další série ovlivňuje únava).
3. **Kalmanův filtr v log-prostoru** (úroveň + tempo růstu za den): nezávislý na velikosti vah,
   dává 95% interval i odhad pro den bez tréninku.
4. **Výstupy** (`ExerciseInsight`): 1RM ± interval, trend v % týdně, předpověď na další trénink
   (medián odstupu tréninků), **připravenost** = váha pro cílová opakování z dvojité progrese
   se 2 v rezervě, zaokrouhlená dolů na kotouče a nejvýš poslední pracovní váha + 2 přírůstky.

### Validace (Monte Carlo, `tools/validation/StrengthModelMonteCarlo.kt`, 3000 cvičenců na řádek)
Skutečné 1RM roste 0–2 % týdně, trénink co 3–4 dny, 3 série v rozsahu 6–12 opakování, denní forma
±3 %, únava −3 %/sérii, chyba Epleyho ±4 %, 30 % tréninků s pomalým spouštěním (skutečný efekt
1,1–1,3). Chyba odhadu 1RM v den dalšího tréninku:

| Rezerva cvičence | Tréninků | Model | Poslední trénink | Maximum dosavadní | Pokrytí 95% intervalu |
|---|---|---|---|---|---|
| 0–2 | 4  | **2,83 %** | 5,74 % | 3,17 % | 95 % |
| 0–2 | 8  | **2,77 %** | 5,57 % | 3,10 % | 96 % |
| 0–2 | 16 | **2,42 %** | 5,69 % | 3,28 % | 96 % |
| 0–3 | 4  | 3,61 % | 6,46 % | 3,21 % | 89 % |
| 0–3 | 8  | 3,55 % | 6,45 % | 2,96 % | 89 % |
| 0–3 | 16 | 3,08 % | 6,47 % | 3,07 % | 90 % |

První verze bez předpokládané rezervy byla **horší než naivní odhady** (5,5–6,5 %) – série
obvykle nejdou do selhání, takže Epley sílu podhodnocuje. Rezerva 1 opakování je zvolena podle
typického kulturistického tréninku (0–2 do selhání), ne vyladěná na simulaci (ta by vybrala 1,5).
U velmi konzervativního cvičence (0–3) je model srovnatelný s „maximem dosavadním“; na rozdíl od něj
ale zachytí i pokles síly (pauza, dieta) a dává interval.

### UI a výpis
* Plán → karta **Dnešní trénink** (PUSH B …, „Začít / Pokračovat“, „Jiný“ trénink).
* Obrazovka tréninku: u cviku 1RM ± interval a trend, **Dnes připraven: 62,5 kg × 12**, předpověď
  na příště, minulý trénink, dnešní série, „Zapsat sérii“ (předvyplněno připraveností, přepínač
  pomalého spouštění, živý odhad 1RM a rekord). Úprava šablony: pořadí, přidat z knihovny, odebrat.
* PDF pro trenéra: stránka **Silový deník** – po cvicích (v pořadí šablon) model, mini graf
  (body = tréninky, čára = model) a poslední 4 tréninky se sériemi.

## Důsledky
* Tempový faktor 1,15 je modelový předpoklad. Předpokládanou rezervu 1 nahradil zapsaný RIR
  s výchozí hodnotou 2 – viz ADR 0025. Deload/přestávky model zvládá jen přes rostoucí nejistotu.
* Šablony jsou vázané na typy dne z Plánu (push/pull/legs); FULL body šablonu zatím nemá.
