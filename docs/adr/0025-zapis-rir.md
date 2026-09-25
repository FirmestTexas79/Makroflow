# 0025 – Zápis rezervy opakování (RIR) u série

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-25

## Kontext
Model síly (ADR 0024) převádí sérii na odhad 1RM Epleyho vzorcem přes „ekvivalentní opakování
do selhání“. Rezervu (kolik opakování by ještě šlo) dosud jen předpokládal (1 opakování pro všechny
série). U cvičence, který jde pravidelně do selhání nebo naopak hodně v rezervě, je tak odhad
systematicky posunutý. Samuel chtěl RIR zapisovat, s výchozí hodnotou 2.

## Rozhodnutí
* `WorkoutSetEntity.rir` / `LoggedSet.rir` (Int, výchozí `StrengthModel.DEFAULT_RIR = 2`,
  rozsah 0–5, `MAX_RIR`). Sloupec `rir INTEGER NOT NULL` je součástí migrace 36→37 (v37 ještě
  nikde nebyla nainstalovaná), Firestore pole `rir` (chybějící = 2).
* Model: `effectiveReps = opakování (× 1,15 u pomalého spouštění) + RIR`. Předpoklad „rezerva 1“
  nahradila zapsaná hodnota; když ji uživatel nemění, platí 2.
* Panel „Zapsat sérii“: řádek **Opakování v rezervě (RIR)** s krokováním −/+ (0 = do selhání,
  5 = „5+“), převzato z poslední dnešní série, jinak 2. Živý odhad 1RM s RIR počítá.
* Zobrazení: u sérií se RIR píše jen když se liší od 2 („80 kg × 8 (RIR 0)“), v PDF `80×8@0`,
  legenda na stránce Silový deník.

## Validace (Monte Carlo, `tools/validation/StrengthModelMonteCarlo.kt`, 3000 cvičenců na řádek)
Stejný model cvičence jako v ADR 0024. „Výchozí“ = RIR se nemění (zapíše se 2), „zapsané“ =
uživatel rezervu odhadne s chybou σ = 1 opakování (zaokrouhleno, 0–5). Chyba odhadu 1RM v den
dalšího tréninku (sloupce Poslední/Maximum jsou naivní Epley bez korekcí):

| Skutečná rezerva | Zápis RIR | Tréninků | Model | Poslední trénink | Maximum dosavadní | Pokrytí 95% intervalu |
|---|---|---|---|---|---|---|
| vždy 0 (do selhání) | výchozí | 4 / 8 / 16 | 3,26 / 3,51 / 3,99 % | 4,4 % | 3,4–4,5 % | 97 / 95 / **88 %** |
| vždy 0 (do selhání) | zapsané | 4 / 8 / 16 | **2,27 / 2,37 / 2,14 %** | 4,4 % | 3,4–4,3 % | 98 % |
| 0–2 | výchozí | 4 / 8 / 16 | 2,30 / 2,51 / 2,42 % | 5,6–5,7 % | 3,1–3,3 % | 98–99 % |
| 0–2 | zapsané | 4 / 8 / 16 | 2,46 / 2,49 / **2,19 %** | 5,5–5,7 % | 3,0–3,2 % | 97–98 % |
| 0–3 | výchozí | 4 / 8 / 16 | 2,43 / 2,58 / 2,39 % | 6,5 % | 3,0–3,2 % | 97–98 % |
| 0–3 | zapsané | 4 / 8 / 16 | 2,43 / 2,45 / **2,19 %** | 6,4–6,6 % | 3,0–3,2 % | 98 % |

* Výchozí 2 místo dřívější 1 model nezhoršil (0–2: 2,3–2,5 % oproti 2,4–2,8 %; 0–3: 2,4–2,6 %
  oproti 3,1–3,6 %) – kulturistické série bývají spíš v rezervě než v selhání.
* Zápis RIR pomáhá hlavně tam, kde se skutečná rezerva od 2 výrazně liší: u tréninku do selhání
  chyba klesne ze ~3,5–4 % na ~2,2 % a 95% interval zase kryje skutečnost (88 % → 98 %).
  U cvičence kolem 2 je zisk malý (a jen při delší historii), protože nepřesný odhad RIR (±1)
  přidává šum.

## Důsledky
* RIR je nepovinný – kdo ho neřeší, má stejné chování jako s výchozí hodnotou 2.
* Připravenost (doporučená váha) dál míří na 2 opakování v rezervě (`StrengthModel.RIR`).
* RPE (Borgova stupnice 6–10) se nezapisuje; RIR je pro uživatele srozumitelnější a RPE ≈ 10 − RIR.
