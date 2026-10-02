# 0061 – Ignileo a Mysnic v Makrodexu

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-10-02

## Kontext
* Ignileo, ohnivý lev a strážce rudého krystalu (docs/adr/0056), měl jen sprite a souboj se statistikami Ignarotha. V Makrodexu chyběl.
* Mysnic je nový Makromon od autora: myška s plochým kamenem na zádech.
* V Horách byli jen vzácní a silnější Makromoni, žádný běžný.

## Rozhodnutí
* **Ignileo = #032:**
  * záznam v Makrodexu „OHEŇ / STRÁŽCE“ se spritem `makromon_32_ignileo` (kopie bitvového `makromon_03_ignileo`; souboj zůstává beze změny);
  * v divočině ho nepotkáš a chytit nejde. V Makrodexu je jako „viděný“, jakmile ho hráč porazí (`boss_defeated_RED` nebo `crystal_RED`);
  * `DexText.dexIds` = divocí ze `SpawnManageru` + strážci (`EXTRA_IDS`), `DexText.defeatedGuardians` říká, koho hráč porazil;
  * platí pro deník i obrazovku Makrodexu.
* **Mysnic = #033:**
  * „ZEMĚ / OBRANA“, **běžný v Horách** (a v jeskyních, které berou druhy z Hor);
  * statistiky: málo útoku, hodně obrany (HP 42, ÚT 30, OBR 55, RYCH 32);
  * útoky: STONE TOSS (země), HARDEN a SAND ATTACK; učí se MUD-SLAP (5), SHELL SLAM (9), BOULDER GUARD (12, +obrana) a ROCK SLIDE (16);
  * nevyvíjí se. Kořist má normální rodina, takže má vyšší šanci na fragment energie;
  * spis S-7 má vlastní poznámku.

## Důsledky
* Další strážce do Makrodexu = číslo v `DexText.EXTRA_IDS`, podmínka v `defeatedGuardians` a sprite `makromon_NN_jmeno`.
* `NewSpeciesTest` hlídá zařazení Mysnica do Hor, jeho typ a sprite a zápis Ignilea po porážce.
