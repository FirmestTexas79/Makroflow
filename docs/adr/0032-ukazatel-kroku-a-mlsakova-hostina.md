# 0032 – Pixelový ukazatel kroků, Mlsákova hostina a nápovědy vývoje Spirry

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-25

## Rozhodnutí
* **Ukazatel kroků na mapě** (`StepProgressBar` + čistý `StepBarArt`, testy): pixel art 70 × 16 –
  dřevěný rámeček se zaoblenými rohy (světlo na horní hraně, léta dřeva), zapuštěná tmavá drážka
  se stínem rámu a zelená náplň s hlavním odleskem a druhou slabší linkou. Zvětšuje se celočíselně
  bez vyhlazení, postup se plynule animuje. Po splnění denního cíle se změní na čtvereček
  16 × 16 ve stejném stylu se zelenou pixelovou fajfkou.
* **Král Mlsák**: poslední fáze je nově „Královská hostina“ – trefit v jednom dni bílkoviny,
  sacharidy i tuky (HIT_TARGET s cílem `macros`, metadata = počet trefených maker 0–3).
  Fáze se přidala na konec, aby rozehraný postup (index fáze) zůstal platný.
* **Makrodex**: formy Spirry mají v popisu i nápovědě, co mají rády („Aquirra má ráda, když se
  pořádně napiješ“) – přesná čísla jsou v „Cestách vývoje“ (ADR 0031).
