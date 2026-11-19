# 0102 – Pět Mistrů Arény

Stav: přijato (2026-11-19), Akt II, kapitola V · issue #30 · navazuje na ADR 0080 (Aréna) a 0101

## Rozhodnutí
* Nová (3.) fáze kapitoly V **„Pět Mistrů“**: Vessa při předání Pamětní knihy pošle hráče do Arény. Požadavek `FLAGS_COUNT`
  přes příznaky `master_beaten_<klíč>`. Hotová kapitola se u starších uložených her sama znovu otevře (`reopenIfExtended`).
* Mistři se vyzývají v **Tréninkovém koutě** haly Arény (dokud hráč nemá knihu, zůstává tam původní text). Jdou po sobě:
  **Rhenna** (rozcvička, 1 trénink) → **Odran** (objem, 2) → **Misk** (tempo, 3) → **Taddeo** (technika, 4) →
  **Kalibrátor** (maska, 4). Mistr N chce N tréninků za posledních 7 dní, nejvýš 4.
* **Trénink** = den se sérií v deníku, s dokončeným „Jdu na trénink“ (`WORKOUT_DONE` / `CARDIO_DONE`) nebo s aktivitou z hodinek
  (síla ≥ 20 min, kardio ≥ 10 min) – stejné pravidlo jako týdenní report. Okno se posouvá, prohra nic nestojí.
* **Síla** podle hráčova týmu jako AI v Aréně: level = průměr týmu + 2 až +4 podle Mistra, počet Makromonů o jednoho víc než hráč
  (aspoň 2, nejvýš kolik má Mistr druhů). Druhy jsou tematické a z hratelných (ne strážci).
* Souboj je obyčejný AI souboj Arény (`PokemonBattleFragment.forTrainer`, id `ai:master_<klíč>`), výhru zapíše `Arena.recordResult`
  jako příznak příběhu; coiny jako za AI trenéra.
* **Kalibrátor** po prohře: „Okruh dvě stě třináct. Výkon v toleranci.“ – klíčový předmět **Pozvánka Řádu Váhy** v Batohu
  a dveře Věže: „Žádost předběžně schválena. Čeká se na kalibraci.“ (vstup do další fáze Kalibrace).

## Testy
`MastersTest` (pořadí a podmínky, hratelné druhy, škálování týmu, id trenéra, klíče příběhu), `OzvenaQuestTest`.
