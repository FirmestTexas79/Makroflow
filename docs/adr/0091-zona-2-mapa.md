# 0091 – Mapa Zóna 2 v deníku

Stav: přijato (2026-10-22), větev `MULTI`

- Stránka Mapa v deníku má záložky **Zóna 1** a **Zóna 2**; otevře se zóna, ve které hráč stojí, klepnutím
  na záložku se přepne.
- Zóna 2 = Akt II: Popelavá pláň uprostřed, Ztichlý sad vlevo, Ozvěnový kaňon vpravo (město Ozvěna přibude,
  až bude ve hře). Rozvržení generuje `tools/mapgen/gen_zone2.py` stejným algoritmem jako zónu 1
  (`gen_zone.py` dostal přepínatelné výstupy) do `assets/zone2/`; kolem lokací je místo moře popelavá pustina.
- `ZoneOne` drží pravidla pro obě zóny (`ZONE2`, `zoneOf`, `jsonPath`); spoje pro teleport obsahují i průchod
  Branou světů (průsmyk ↔ pláň), takže cena teleportu počítá přechody napříč zónami. Lokace zóny 2 jsou za
  horami (stejný krokový zámek). Objevení se odvodí i z příznaků návštěv u starších uložených her.
- `ZoneTwoTest` hlídá, že vchody a východy v `zone2.json` sedí s uzly map lokací.
