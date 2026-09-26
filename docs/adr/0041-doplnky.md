# 0041 – Doplňky z materiálů Makromonů

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-26

## Rozhodnutí
* **Čtyři doplňky do slotů ACCESS**, vyrábějí se u pracovního stolu v sekci „Doplňky“:

  | Doplněk | Slot | Recept | Bonus | XP Výroby |
  |---|---|---|---|---|
  | Travní prsten | prsten | 3 suchý list + 1 živý list + 5 fragmentů energie | +15 % XP za chytání | 120 |
  | Ohnivý prsten | prsten | 3 chudý plamínek + 1 žhnoucí kámen + 5 fragmentů energie | +5 % XP za výrobu, pěstování, těžbu a kácení | 150 |
  | Dobrodruhův náhrdelník | přívěsek | 3 vodní perla + 3 malá dušička + 3 pixie prach | kořist z Makromonů padá o 10 % častěji | 180 |
  | Duše ohně | talisman | 3 černozlatá bobule + 2 koule magmatu + 20 fragmentů energie | −20 % šance na útěk z ballu | 250 |
* Recepty odemyká stejný uzel jako dobrodruhův set („Základní vybavení“ ve stromu Výroby). Každý kus jde vyrobit jednou. Do prázdného slotu se rovnou nasadí, prsten do prvního volného ze dvou.
* **„O 10 % častěji“** je násobitel šancí (×1,1): 40% šance na materiál se změní na 44 %. Platí pro všechnu kořist z Makromonů, i pro Gudwinovy artefakty.
* **Duše ohně** se sčítá s pasivním bonusem Chytání a počítá se jako IdleOn snížení ze základní šance: 10% šance na útěk × (1 − 0,2) = 8 %. Celkem jde nejvýš o 90 %. Deník (Postava → Chytání) ukazuje součet.
* Ocenění „Třpytivý“ (plné sloty doplňků) je teď splnitelné.
