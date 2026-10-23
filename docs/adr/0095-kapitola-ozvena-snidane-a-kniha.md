# 0095 – Kapitola V · OZVĚNA: Dobré ráno a Pamětní kniha

Stav: přijato (2026-10-23), větev `MULTI`, Akt II (issues #26–#34)

- Nová hlavní kapitola `ozvena_chapter` („V · OZVĚNA“, `story/Ozvena.kt`), zatím první dvě fáze z bible:
  1. **Dobré ráno, Ozvěno** – pekař Brann (Ignar) pozve hráče na snídani. Nový požadavek `BREAKFAST_DAYS`:
     jídlo zapsané mezi 4:00 a 10:30 (stejné okno jako `MealRepeat.Slot.BREAKFAST`) ve 3 různých dnech od začátku
     fáze (`ConsumedSnackDao.getConsumedSinceSync`, `Ozvena.breakfastDays`). Odměňuje pravidelnost, ne množství.
  2. **Jméno na dveřích** – Brann si poprvé všimne, že říká pořád totéž, a pošle hráče za písařkou Vessou
     (Johnsova). Klepnutím na Vessu se nastaví příznak `ozvena_memory_book` a Vessa se rozloučí předáním
     **Pamětní knihy** („co do ní zapíšeš, Bezejmenní nesmažou“).
- Po dokončení Brann dál opakuje svůj pozdrav (smyčka); Vessa zopakuje rozloučení.
- Další fáze (Pět Mistrů, Kalibrace, Spis 212…) se připojí za tyto dvě; Pamětní kniha jako předmět a mizení
  obyvatel přijdou s nimi.
