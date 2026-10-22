# 0089 – Ztichlý sad: noční lokace aktu II

Stav: přijato (2026-10-22), větev `MULTI`, issue #29

- Biom `QUIET_ORCHARD` (`cave/QuietOrchard.kt`, mapa 160 × 320 z `tools/mapgen/gen_orchard.py`).
  Vchod z Popelavé pláně odbočkou vlevo (`k_sadu` u ohniště); **otevřeno jen 19:00–5:00**, jinak hláška.
- Grafika stejným postupem jako pláň (ADR 0088): dlaždice z `ashplain/tiles.py` se zelenou paletou,
  objekty v `orchard/sad_objects.py` (ovocné stromy, Stará jabloň se zlatými jablky, kamenná zídka s vrátky,
  studna, úly, zahradníkova bouda, koše, žebřík). **Noc** = po složení se celá mapa přebarví do měsíčního
  světla a zdroje světla (zlatá jablka, lucerna, měsíc v jezírku) se vrátí a dostanou záři.
- Živá vrstva `OrchardFxView`: blikající světlušky, víc jich je u jezírka.
- Místa setkání: jezírko (rybaření, `jezirko_` = vodní setkání), úly, Stará jabloň, vlhká tráva.
  Divocí lv 12–18, druhy zatím z louky (`wildBiome = MEADOW`, noční druhy se tam objeví samy).
- Studna a bouda mají texty s náznaky (odraz se pohne později, škrtnutá jména zahradníků).
- Mimo rozsah: sběr plodů pro Bohouška (přijde s městem, #27/#28).
