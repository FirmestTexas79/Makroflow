# 0089 – Ztichlý sad: lokace aktu II se dnem a nocí

Stav: přijato (2026-10-22), větev `MULTI`, issue #29

- Biom `QUIET_ORCHARD` (`cave/QuietOrchard.kt`, mapa 160 × 320 z `tools/mapgen/gen_orchard.py`).
  Vchod z Popelavé pláně odbočkou vlevo (`k_sadu` u ohniště) – v sadu hráč vejde **zprava** branou mezi
  kamennými sloupky. **Otevřeno pořád**: ve dne slunná mapa `quiet_orchard.png` a motýlci, v noci (19:00–5:00)
  měsíční `quiet_orchard_night.png` (stejné rozložení, `BiomeDefinition.nightRes`) a světlušky.
- Grafika stejným postupem jako pláň (ADR 0088): dlaždice z `ashplain/tiles.py` se zelenou paletou,
  objekty v `orchard/sad_objects.py` (ovocné stromy, Stará jabloň se zlatými jablky, kamenná zídka, sloupky brány
  s křídly, studna, slaměné úly, hrázděná chalupa, koše, žebřík). Budovy a kamenné prvky se kreslí po prvcích
  (`orchard/pixart.py`): každý kámen a šindel má světlou hranu, stín a spáru, prvky mají vlastní obrys,
  pod okapem je stín; drobnosti (úl, křídlo vrátek) jsou ručně kreslené z ASCII předlohy. **Noc** = po složení se celá mapa přebarví do měsíčního
  světla a zdroje světla (zlatá jablka, lucerna, měsíc v jezírku) se vrátí a dostanou záři.
- Živá vrstva `OrchardFxView`: blikající světlušky, víc jich je u jezírka.
- Místa setkání: jezírko (rybaření, `jezirko_` = vodní setkání), úly, Stará jabloň, vlhká tráva.
  Divocí lv 12–18, druhy zatím z louky (`wildBiome = MEADOW`, noční druhy se tam objeví samy).
- Studna a bouda mají texty s náznaky (odraz se pohne později, škrtnutá jména zahradníků).
- Mimo rozsah: sběr plodů pro Branna (přijde s městem, #27/#28).
