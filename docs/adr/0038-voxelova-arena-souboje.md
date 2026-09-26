# 0038 – Voxelová 3D aréna souboje podle lokace

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-26

## Kontext
Souboj vypadal od první verze jako Pokémon Yellow: bílé pozadí, šedé pruhy a dvě ploché plošinky. Ke zbytku Makrosvěta (pixelové mapy, intra setkání, dřevěné UI) to nesedělo. Navíc byl souboj všude stejný, ať se bojovalo ve městě, nebo v jeskyni.

## Rozhodnutí
* **Softwarový voxelový renderer v čistém Kotlinu** (`pokemon/arena/VoxelRenderer.kt`):
  * scéna je seznam kvádrů, kamera je perspektivní a viditelnost řeší z-buffer;
  * stíny ze slunce počítá ortogonální shadow mapa; dál umí mlhu, bodová světla a svítící materiály;
  * textura je procedurální, 6 × 6 texelů na blok. Texel se počítá ze světových souřadnic, takže i velký kvádr vypadá jako řada samostatných bloků.
  * Nepotřebuje OpenGL ani knihovny a dá se testovat na JVM.
* **Scény podle lokace** (`ArenaScenes.kt`, `ArenaTheme.fromBiome`):
  * **Město**: dlážděné náměstí, domy s okny a stupňovitými střechami, kostel s věží, kašna a lampy.
  * **Louka**: květinová tráva, cestička, živý plot, stromy a vzdálené hory.
  * **Hvozd**: mechová zem, klenba korun s průhledy, borovice, houby, padlý kmen, světlušky a zelená mlha.
  * **Hory**: suť, rozeklané skály se sněhem a štíty v oparu.
  * **Starý důl**: koleje, výdřeva, lucerny a stříbrné žíly.
  * **Mechová jeskyně**: svítící krystaly, podzemní jezírko a zlatý balvan.
  * **Voda** (jezírko, jezero): písečný břeh, soupeř na kamenném ostrůvku, lekníny a rákosí.
* **Kamera je pro všechny lokace stejná.** Soupeř stojí na stejném místě obrazovky jako dřív a všechny efekty (ball, třpytky, stavy) zůstaly. Scéna se staví kolem pozice soupeře: podstavec leží přesně pod jeho nohama. Test hlídá, aby mezi kamerou a Makromony nestál žádný kvádr.
* **Hráčův Makromon** stojí blíž kameře: je větší (46 herních px) a nohy má pod okrajem scény.
* **Aréna vyplní celou výšku displeje nad herním plátnem.** Obraz se prodlouží nahoru se stejným středem pohledu. Nadpis setkání leží na obloze.
* **HUD** je průsvitný tmavý panel s bílým písmem se stínem a „závorkou“ ve stylu Game Boye. Spodní menu zůstalo bílé.
* **Výkon**: aréna se kreslí jednou za souboj na pozadí, během intra (zhruba 0,1–0,4 s). Do té doby je vidět jednobarevná náhrada.
* Debug: `adb shell am start … --es debug_battle WATER` spustí rovnou souboj v aréně zvolené lokace.

## Důsledky
* Nová lokace = nová funkce ve `Builder` a řádek v `ArenaTheme.fromBiome`.
* Aréna je statická. Vlnění vody nebo pohyb světlušek by znamenalo překreslovat každý snímek, případně přidat samostatné animované vrstvy.
