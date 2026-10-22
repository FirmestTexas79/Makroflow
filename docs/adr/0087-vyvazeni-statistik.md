# 0087 – Vyvážení statistik: všechny druhy v jedné stupnici

Stav: přijato (2026-10-21), větev `MULTI`

- Statistiky druhu jsou „základní“ (přepočet na level v `BattleEngine.initializeStatsForLevel`).
  17 druhů mělo součet 60–135, zbytek 160–340, takže duchové, Finlet/Serpfin, Gudwin, Axlu a nové
  druhy byly v soubojích i v Aréně výrazně slabší.
- Úprava: součet podle role, **poměr mezi HP/útokem/obranou/rychlostí zůstal** (zaokrouhlení dorovná HP).
  Běžný první stupeň 170–190, druhý 240–260, třetí / legendární 320, vzácný samostatný 190–250.

| Druh | Původně | Nově | Součet |
|---|---|---|---|
| Umbex | 30/14/12/16 | 87/41/35/47 | 72 → 210 |
| Lumex | 28/16/10/20 | 94/54/34/68 | 74 → 250 |
| Finlet | 20/8/8/40 | 31/13/13/63 | 76 → 120 (záměrně slabý) |
| Serpfin | 65/25/20/15 | 145/56/45/34 | 125 → 280 |
| Soulu → Soulex → Soulord | 63 / 85 / 118 | 67/32/22/49 · 99/45/34/62 · 135/60/49/76 | → 170 / 240 / 320 |
| Phantil → Phantius → Phantiax | 60 / 93 / 135 | 62/28/23/57 · 98/41/36/65 · 143/59/47/71 | → 170 / 240 / 320 |
| Gudwin | 55/14/18/8 | 140/35/45/20 | 95 → 240 |
| Axlu | 35/12/12/15 | 109/37/37/47 | 74 → 230 |
| Tynafi → Tynafior | 69 / 130 | 83/37/21/39 · 120/40/60/40 | → 180 / 260 |
| Johnsova → Grifben | 74 / 134 | 86/29/29/36 · 120/47/43/50 | → 180 / 260 |
| Lumivix | 35/12/12/15 | 89/31/31/39 | 74 → 190 |

- Dopad: chycení Makromoni se přepočítají sami (statistiky se nikde neukládají). **Bossové Aquavulp
  (statistiky Serpfina, lv 12) a Soulord (lv 14) zesílí** – dřív byli výrazně slabší než Ignileo
  (statistiky Ignarotha, součet 340, lv 12); teď jsou srovnatelní.
- Test `StatsBalanceTest`: každý druh 110–360, vývoj je vždy silnější než předchozí stupeň.

## Doladění bossů (2026-10-22, issue #18)
Simulace soubojů (tým 2–3 Makromonů druhého stupně, součet ~240, útoky 40/65 bez výhody typu, 4000 bitev):

| Boss | Kde (divocí) | Výhra týmu 3 na lv 10 / 12 / 14 |
|---|---|---|
| Aquavulp lv 12 | jeskyně (6–12) | 99 % / 100 % / 100 % |
| Ignileo lv 12 | doly (8–14) | 1 % / 75 % / 100 % |
| Soulord lv 14 | kořeny Hvozdu | 2 % / 62 % / 100 % |
| **Soulord lv 13** | | 10 % / 95 % / 100 % |

- Aquavulp a Ignileo beze změny: křivka jeskyně → doly roste, jak má.
- **Soulord 14 → 13.** Je duch, takže normální útoky (Tackle, Scratch…) ho nezasáhnou a v praxi je těžší,
  než ukazuje simulace. S lv 13 zůstává nejtěžším bossem aktu I, ale tým na lv 12 ho dá.
