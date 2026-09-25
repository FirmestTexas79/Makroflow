# 0020 – Biologická projekce: trend vážení + energetická bilance

**Stav:** přijato · **Datum:** 2026-09-25

## Kontext
Karta „Biologic projekce“ v Historii ukazovala 7 dní vážení a 7 dní predikce. Po fázi B
(ADR 0003) už predikce stála na Kalmanově filtru, ale měla tyto slabiny:
* **Jen extrapolace trendu.** Tempo se bralo pouze z vážení, takže v prvních dnech (málo
  vážení, tempo neznámé) byla projekce skoro vodorovná s obřím pásmem, i když aplikace ví,
  kolik člověk jí a kolik vydává.
* **Graf kreslil syrová vážení** jako hlavní čáru (bezierova křivka přes výkyvy vody),
  vyhlazený trend nebyl vidět vůbec.
* **Pásmo se dělalo trikem**: horní mez vyplněná barvou, spodní překrytá krémovou maskou
  → maska přemazávala mřížku; pozadí historie bylo ruční poměr 7,5 : 6, který neseděl na osu.
* Štítek „STABILNÍ“ byl napevno v XML, u minulého dne nešlo porovnat projekci se skutečností.

## Rozhodnutí
* **Dva nezávislé zdroje tempa** (`energy/WeightProjection.kt`, čistý Kotlin):
  1. stav Kalmanova filtru k vybranému dni (jen data známá k tomu dni; když vybraný den leží
     za posledním vážením, filtr se posune predikčními kroky – `WeightTrend.predict`),
  2. energetická bilance `(příjem − výdej) / 7700 kcal/kg`. Příjem = průměr zapsaných dní
     z posledního týdne (≥ 4 dny; den pod 50 % výdeje se nepočítá), jinak cíl kalorií
     s nejistotou dodržení ±15 %. Výdej = model z rovnic na následující týden (±12 %).
* **Spojení Kalmanovou korekcí** – bilance je pozorování tempa s `H = [0, 1]`
  (`WeightTrend.observeSlope`). Váha obou zdrojů vychází z jejich rozptylů, přes kovarianci
  se jemně opraví i úroveň. Výsledný stav se promítne na 7 dní s 95% intervalem.
* **Výdej bez adaptivní korekce.** Adaptivní výdej (ADR 0003) je sám spočítaný z trendu vážení
  a příjmu; kdyby vstoupil do bilance, stejná informace z vážení by se započítala dvakrát a pásmo
  by bylo falešně úzké. Rovnice z fáze A jsou na vážení nezávislé, takže slouží jako čistý prior.
* **Hodnocení tempa vůči cíli**: redukce 0,5–1 % hmotnosti týdně (Helms, Aragon & Fitschen 2014),
  objem 0,25–0,5 % (Iraki et al. 2019), udržování ±0,25 %. Hranice mají toleranci ~0,1 %
  kolem cílových temp aplikace, aby šum nepřepínal hodnocení. Při < 5 váženích a tempu
  nerozlišitelném od nuly se nehodnotí („zatím málo vážení“).
* **Vlastní graf** `WeightProjectionView` (Canvas): vážení jako body, vyhlazený trend (RTS)
  jako čára, projekce čárkovaně, pásmo jako jedna uzavřená cesta, předěl „DNES“, hodnota na
  konci, klepnutí/tažení ukáže den (vážení, trend, výkyv vody, projekce s intervalem).
  U dne v minulosti se zobrazí i pozdější vážení a text „v pásmu / mimo pásmo“.
* **Karta**: tři čísla (teď podle trendu, tempo kg a % týdně, za 7 dní s intervalem),
  barevné hodnocení tempa a věta, z čeho se projekce skládá (podíl vážení vs. bilance).

## Validace (Monte Carlo, 2000 simulovaných lidí na řádek)
Skutečný výdej 2200–3400 kcal, rovnice se mýlí o σ = 10 %, příjem −700…+400 kcal od výdeje,
denní šum příjmu σ = 250 kcal, chyba zápisu 5 %, váha šumí σ = 0,7 kg. Chyba = |projekce za
7 dní − skutečná hmotnost bez šumu|.

| Dní vážení | Chyba – jen trend | Chyba – trend + bilance | Pokrytí 95% pásma (trend / + bilance) | Váha bilance |
|---|---|---|---|---|
| 2  | 0,51 kg | 0,43 kg | 100 % / 98 % | 0,69 |
| 4  | 0,47 kg | 0,37 kg | 100 % / 98 % | 0,78 |
| 7  | 0,51 kg | 0,35 kg | 99 % / 98 % | 0,73 |
| 14 | 0,45 kg | 0,34 kg | 97 % / 98 % | 0,46 |
| 28 | 0,31 kg | 0,29 kg | 98 % / 97 % | 0,19 |

Bilance zmenšuje chybu až o ~30 % v prvních dvou týdnech; s přibývajícími váženími její
váha přirozeně klesá a projekce se opírá o skutečný průběh. Pásmo je mírně konzervativní.

## Důsledky
* Projekce dává smysl od prvního vážení a zpřesňuje se s každým check-inem.
* 7700 kcal/kg je zjednodušení; na horizontu 7 dní je chyba malá, dlouhodobou adaptaci řeší
  adaptivní výdej. Rychlé přesuny vody/glykogenu po změně diety model nevysvětluje – spadnou do pásma.
* Graf už nepoužívá MPAndroidChart (knihovna zůstává v závislostech, jinde se nevyužívá).
