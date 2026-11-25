"""
Použití:  python tools/mapgen/gen_watch_b.py
Výstup:   app/src/main/res/drawable-nodpi/root_watch.png, app/src/main/assets/walk/root_watch.txt,
          tools/mapgen/root_watch_debug.png

Zóna 3 (Akt III – Hlubina), lokace 2 z 8: POZOROVATELNA B (176 × 352, pohled 3/4 shora, docs/adr/0106).
Bunkr Řádu zapuštěný do skalní stěny a před ním betonová terasa nad propastí se zábradlím – jako balkon.
Ze Stanice 8 se sem sestupuje po schodech Lumenců vytesaných ve skále (vlevo nahoře). Na čele bunkru: smaltovaná
cedule POZOROVATELNA B, nástřik B, ocelové dveře s pečetí, dvě střílny se světlem, polní telefon, nástěnný graf
KONTROLA s křivkou kořenů. Na terase: velký světlomet na točně v půlkruhu pytlů, dálkoměr u zábradlí, agregát,
stojan plamenometů u zdi, bedny. Pohlcení 2/8: tlustý kořen se přes skálu valí po čele bunkru (praskliny), přes
terasu a ohýbá zábradlí, než spadne do tmy. Každá cedule je přišroubovaná ke zdi, zábradlí nebo stojí na sloupcích.
Východy: schody nahoru (Stanice 8), vpravo průchod v zábradlí k lanovce, vlevo dole schody dolů (Tábor na svahu).
Setkání DUCH a ZEMĚ. Živé detaily: InteriorFxView.ROOT_WATCH. NODES / EDGES jsou ZDROJ PRAVDY i pro RootWatch.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
import hideout_parts as P  # noqa: E402
from common import Spr, c, _pick, _h, _vn, ROCK, STEEL, CONC  # noqa: E402
from scene import Scene  # noqa: E402
from roots import root, bez  # noqa: E402
import order as O, lumen as LU, terrain as T, signs as SG  # noqa: E402

AW, AH, WALL_H = 176, 352, 118
EDGE = 312                                                                # hrana terasy (zábradlí)
NODES = {
    "pb_vstup": (30, 128), "pb_dvere": (100, 132), "pb_telefon": (128, 134), "pb_tabule": (66, 132),
    "pb_stred": (100, 196), "pb_generator": (40, 182), "pb_svetlomet": (74, 222), "pb_hlidka": (136, 156),
    "pb_dalkomer": (100, 290), "pb_k_rokli": (168, 300), "pb_dolu": (24, 340),
}
EDGES = [("pb_vstup", "pb_tabule"), ("pb_tabule", "pb_dvere"), ("pb_dvere", "pb_telefon"), ("pb_telefon", "pb_hlidka"),
         ("pb_tabule", "pb_stred"), ("pb_vstup", "pb_generator"), ("pb_generator", "pb_stred"), ("pb_stred", "pb_svetlomet"),
         ("pb_stred", "pb_dalkomer"), ("pb_svetlomet", "pb_k_rokli"), ("pb_dalkomer", "pb_dolu")]
sc = Scene(AW, AH, WALL_H, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(22)

# ── terasa: betonové desky; pod hranou propast; vlevo dole schody dolů ──────
P.concrete_floor(img, 0, WALL_H, AW, EDGE)
for y in range(EDGE, AH):
    for x in range(AW):
        if 12 <= x <= 36:                                                 # schody dolů z terasy
            q = (y - EDGE) % 7
            img[y, x] = CONC[4] if q < 2 else CONC[2] if q < 5 else CONC[0]
            if x in (12, 36):
                img[y, x] = CONC[0]
            continue
        if y < EDGE + 7:                                                  # čelo terasy
            img[y, x] = _pick(CONC, max(0.0, 0.4 - (y - EDGE) * 0.05), x, y); block[y, x] = True
            continue
        k = min(1.0, (y - EDGE) / 30)
        col = np.array([12, 16, 30], float) * (1 - k) + np.array([4, 6, 14], float) * k
        m = _vn(x, y * 1.4, 27, 9)
        if m > 0.55:
            col += np.array([22, 30, 50]) * (m - 0.55) * 2
        img[y, x, :3] = np.clip(col, 0, 255); block[y, x] = True
block[EDGE - 2:EDGE, :] = True
block[EDGE - 2:EDGE, 12:37] = False; block[EDGE - 2:EDGE, 158:AW] = False   # průchody zábradlím
block[EDGE:EDGE + 8, 158:AW] = False                                      # lávka k lanovce
for y in range(EDGE, EDGE + 8):
    for x in range(158, AW):
        img[y, x] = STEEL[2] if (x + y) % 3 else STEEL[1]

# značení na terase: žlutý kruh kolem světlometu a nástřik B
for a in range(0, 360, 3):
    img[int(242 + math.sin(math.radians(a)) * 20), int(74 + math.cos(math.radians(a)) * 34)] = O.YELLOW[1]
SG.painted(img, 112, 200, "B", O.YELLOW[2], scale=2, seed=21, drips=False)
# život na terase: olejové skvrny u agregátu, kanálový poklop, nábojnice a papíry, žluté hrany schodů
for (cx_, cy_, r) in ((52, 186, 7), (60, 192, 4), (30, 196, 5)):
    for y in range(cy_ - r, cy_ + r):
        for x in range(cx_ - 2 * r, cx_ + 2 * r):
            if ((x - cx_) / (2 * r)) ** 2 + ((y - cy_) / r) ** 2 < 1 and _vn(x, y, 71, 3) > 0.35:
                img[y, x, :3] = (img[y, x, :3] * 0.55).astype(np.uint8)
for y in range(262, 274):
    for x in range(136, 152):
        if ((x - 144) / 8) ** 2 + ((y - 268) / 6) ** 2 < 1:
            img[y, x] = STEEL[1] if (x + y) % 3 else STEEL[2]
for i in range(18):
    x, y = int(rng.integers(90, 170)), int(rng.integers(150, 300))
    img[y, x] = O.BRASS[3]; img[y, x + 1] = O.BRASS[1]
import archive_parts as A  # noqa: E402
A.loose_papers(img, [(120, 214), (132, 226), (126, 240), (96, 250)], np.random.default_rng(3))
for x in range(12, 37):
    img[EDGE, x] = O.YELLOW[2] if (x // 3) % 2 else c(0x16161C)

# ── kořen (pohlcení 2/8): přes stěnu, terasu a zábradlí do tmy ──────────────
root(sc, bez((166, 150), (146, 236), (130, 330)), 16, 10, lift=0.9, d=0.4)            # po terase k zábradlí
root(sc, bez((130, 330), (128, 342), (136, 356)), 10, 7, solid=False, d=0.42)         # přes hranu dolů
for (p0, p1, p2, w) in (((150, 210), (170, 226), (176, 250), 4), ((142, 260), (118, 270), (112, 286), 3.5),
                        ((160, 170), (140, 176), (124, 172), 3)):
    root(sc, bez(p0, p1, p2, 30), w, 1.2, solid=False, d=0.5)

# ── předměty na terase ──────────────────────────────────────────────────────
sc.place(O.sandbag_ring(64, 30), 74, 264, foot=None, pad=0)
sc.place(O.searchlight(), 74, 248, foot=(30, 8))
sc.place(O.gen_shed(4), 38, 166, foot=(52, 14))
sc.place(O.flamer_rack(), 140, 146, foot=(24, 6))
sc.place(O.helmet(), 118, 168, collide=False)
for (x, y, sd) in ((72, 168, 7), (80, 180, 8)):
    sc.place(O.army_crate(sd), x, y, pad=0)
sc.place(O.rangefinder(), 100, 300, foot=(24, 6))
rail = O.railing(AW, 12, bend=(118, 142), gaps=((12, 36), (158, AW)))
sc.place(rail, AW // 2, EDGE + 2, collide=False, shadow=False)
pl = SG.plate("LANOVKA", c(0x141414), c(0xE2BA34), seed=5, pad=3, wear=0.0)
sc.place(O.mount_plate(pl, posts=10), 150, EDGE - 2, foot=(4, 2))
pl2 = SG.plate("DOLŮ: TÁBOR", c(0xE8E2CC), c(0x1E2A44), seed=6, pad=2)
sc.place(O.mount_plate(pl2, posts=10), 44, EDGE - 4, foot=(4, 2))
for t in np.linspace(0, 1, 160):                                          # kabel od agregátu ke dveřím bunkru
    X = int(64 + (98 - 64) * t); Y = int(160 + (WALL_H + 6 - 160) * t + math.sin(t * 5) * 2)
    img[Y, X] = c(0x141418)

floor = np.zeros((AH, AW), bool); floor[WALL_H:EDGE, :] = True
sc.shadows(floor)

# ── stěna: skála, schody Lumenců, čelo bunkru s cedulemi ────────────────────
img[:WALL_H] = T.cavern_wall(AW, WALL_H).a
block[:WALL_H + 4, :] = True
LU.carved_stairs(img, 2, 0, 18, WALL_H + 4, steps=14, width=18)
block[WALL_H - 10:WALL_H + 4, 14:44] = False
sc.put(O.bunker_facade(AW, WALL_H, 46, 89, seed=3), 0, 0)
title = SG.plate("POZOROVATELNA B", c(0xE8E2CC), c(0x1E2A44), seed=17, pad=3, wear=0.0)
sc.put(O.mount_plate(title), 100 - title.w // 2, 34)
warn = SG.plate("VSTUP ZAKÁZÁN", c(0x141414), c(0xE2BA34), seed=9, pad=2, wear=0.0)
sc.put(O.mount_plate(warn), 100 - warn.w // 2, 56)
sc.put(O.field_phone(), 124, WALL_H - 26)
sc.put(O.wall_chart("KONTROLA"), 50, WALL_H - 42)
SG.painted(img, 139, 74, "B", O.YELLOW[2], scale=3, seed=4, wear=0.06)
for lx in (95, 122):                                                      # nástěnné lampy
    lamp = Spr(10, 6); lamp.rect(0, 0, 9, 4, STEEL[2]); lamp.rect(1, 3, 8, 4, O.SODIUM)
    sc.put(lamp, lx, 70)
root(sc, bez((186, -6), (160, 60), (166, 150)), 15, 16, solid=False, d=0.35)          # ze skály po pravém konci bunkru
for (x0, y0, n) in ((140, 40, 12), (146, 96, 10), (138, 108, 8)):                       # praskliny v betonu
    x, y = x0, y0
    for i in range(n):
        x += int(rng.integers(0, 3)); y += int(rng.integers(-1, 2))
        if 46 < x < AW and 26 < y < WALL_H and not sc.depth[y, x]:
            img[y, x] = CONC[0]
sc.zbuf[:WALL_H] = -1e9
sc.draw_sprites()

fungi = []
for _ in range(8000):
    x, y = int(rng.integers(2, AW - 3)), int(rng.integers(10, 340))
    if sc.depth[y, x] and not sc.depth[y, x + 2] and len(fungi) < 50:
        fungi.append((x, y))
        img[y, x, :3] = (150, 210, 255); img[y, x - 1, :3] = (80, 140, 220); img[y + 1, x, :3] = (40, 70, 120)

sc.light(
    warm=[(100, 124, 36, 22, 0.9), (127, 120, 26, 18, 0.7), (100, 88, 34, 18, 0.6), (127, 88, 24, 14, 0.5),
          (40, 176, 28, 16, 0.6), (74, 244, 34, 22, 0.8), (150, 300, 20, 12, 0.6), (44, 300, 20, 12, 0.5)],
    cold=[(16, 60, 20, 40, 0.5)] + [(x, y, 7, 6, 0.7) for (x, y) in fungi],
    dark_k=0.38)
sc.export("root_watch", "gen_watch_b.py")
print("FUNGI", fungi[:14])
