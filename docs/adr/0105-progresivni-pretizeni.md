# 0105 – Progresivní přetížení a druh série

## Kontext
Doporučená váha v tréninku se týdny neměnila. Opakování šla z dvojité progrese (osa 6–10, jednoručky 8–12,
kladky 10–15), ale váha z Kalmanova modelu síly s rezervou 2 opakování a zaokrouhlením dolů – model vždy
navrhl stejnou nebo nižší váhu, než jakou uživatel zvedl, a nahoru se posouval jen pomalu. Rozehřívací série
se poznávaly jen podle toho, že byly lehčí.

## Rozhodnutí
- `ProgressiveOverload` – samostatný model s laditelnými parametry (`Params`): pracovní série 6–8 opakování,
  2–3 série; při 10 opakováních se stejnou vahou přidat. Nová váha z Epleyho vzorce tak, aby vycházela na
  ~7 opakování, aspoň o kotouč, nejvýš +10 %. Skóre tréninku = mix nejslabší a nejsilnější pracovní série
  (`minWeight`) + RIR nad 2 (`rirWeight`). Dvakrát po sobě pod 6 se stejnou vahou → odlehčit o 10 %.
- Druh série (`SetKind`: pracovní / rozehřívací / dojížděcí), sloupec `workout_sets.kind` (DB v43).
  Rozehřívací (~60 %) a dojížděcí (~80 % pracovní váhy) se do progrese nepočítají, rozehřívací ani do
  týdenního objemu a modelu síly. Formulář je předvyplní z pracovní váhy.
- Model síly (`StrengthModel`) zůstává na odhad 1RM a trend, váhu už nenavrhuje.

## Důsledky
Doporučení se posune hned po tréninku s 10 opakováními. Parametry se ladí na jednom místě (`Params`);
starší zápisy bez druhu se berou jako pracovní (nejtěžší série dne).
