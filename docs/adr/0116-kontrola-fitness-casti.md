# 0116 – Kontrola fitness části (listopad 2026)

Prošlo 312 jednotkových testů funkční části (common, data, energy, history, learning, loop, nutrition, report,
settings, training, wearable, widget), lint bez chyb (jen varování). Opraveno:
- **Rychlý trénink v cíli dne.** „Jdu na trénink“ mimo týdenní plán se nepočítal do výdeje, takže cíl kcal a vody
  byl ten den nízký. Silový rychlý trénink se teď do dnešního modelu přičte (hotový skutečnou délkou 10–180 min,
  naplánovaný 60 min, „Jiný“ jako MET 3,5), jen když plán na dnešek silový trénink nemá. Kardio z rychlého
  zápisu zatím ne – nevíme typ; doplní ho adaptivní výdej.
- **Haptika na Androidu 8–10.** HapticFeedbackConstants.CONFIRM je až od API 30; starší telefony nedostaly nic.
  `common/Haptics.CONFIRM` dá na starších běžné cvaknutí.
Ponecháno vědomě: synchronní dotazy do Roomu (`allowMainThreadQueries`, `runBlocking` v MacroCalculator) – fungují,
ale při velké historii můžou zadrhávat UI; přesun na korutiny až při měřitelném problému.
