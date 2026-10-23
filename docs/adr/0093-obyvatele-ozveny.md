# 0093 – Obyvatelé Ozvěny

Stav: přijato (2026-10-22), větev `MULTI`, Akt II (issues #26–#34)

- Ve městě nežijí lidé (bible příběhu): usadili se v něm **naši Makromoni**, kteří jednoho dne začali mluvit.
  Divili se tomu, a pak je něco táhlo do prázdného města s prostřenými stoly – cítili, že je jejich.
- Pět obyvatel z Makrodexu (`OzvenaCity.TOWNSFOLK`, druh = číslo Makrodexu): pekař **Brann** (Ignar, plamínek
  na ocase drží pec), písařka **Vessa** (Johnsova), babička **Nelle** (Flori), metař **Jarro** (Mysnic) a pan
  **Tobben** (Gemak). Obrázek Makromona (640 px = 64 px pixel artu po 10) se zmenší bez vyhlazení na 1 px = 1 dp,
  pata na art souřadnici, lehké dýchání (scaleY), každý jiným tempem.
- Brann říká každý den totéž (smyčka). Vessa hráče „zapíše“, její jméno na zdi chybí. Nelle, Jarro a Tobben mají
  tři repliky střídané po dnech (`OzvenaCity.residentLine`): jak poprvé promluvili, proč přišli do města, a drobné
  trhliny ve fasádě (popel je „pyl“, noviny jsou pořád z úterý třináctého).
- Nové uzly `lida`, `jarda`, `korinek` (gen_city.py i OzvenaCity.kt). Obrázek města ani mapa chůze se nemění.
- Zatím bez mizení obyvatel (pravidla v bibli příběhu): přijde s Pamětní knihou; postavy jsou proto samostatné
  překryvy, ne součást obrázku města.
- Klíčoví Makromoni příběhu (řada Spirry, Gudwin, Mydrus, Hunfrey) mezi obyvateli nejsou.
