"""
Použití:  python tools/mapgen/gen_watch_b.py
Výstup:   app/src/main/res/drawable-nodpi/root_watch.png, app/src/main/assets/walk/root_watch.txt,
          tools/mapgen/root_watch_debug.png

Zóna 3 (Akt III – Hlubina), lokace 2 z 8: POZOROVATELNA B (176 × 352, pohled 3/4 shora, docs/adr/0106).
Nižší terasa na stěně dutiny. Ze Stanice 8 se sem sestupuje po schodech Lumenců vytesaných ve skále. Řád tu měl
oplocenou pozorovatelnu: nakloněná věž B (okny kabiny prorůstají kořeny, po nohách šplhají), rozlámaná betonová
plocha, bouda s generátorem, opuštěný stojan plamenometů s helmou na zemi, sloupek KONTROLA s čárkami po dnech.
Pohlcení 2/8: tlustý kořen ze stěny zmáčkl plot a rozlomil beton, další leze z propasti k věži.
Východy: schody nahoru (Stanice 8), vpravo stožár lanovky (Lanovka nad roklí), vlevo dole cesta (Tábor na svahu).
Setkání DUCH a ZEMĚ. Živé detaily: InteriorFxView.ROOT_WATCH. NODES / EDGES jsou ZDROJ PRAVDY i pro RootWatch.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
import hideout_parts as P  # noqa: E402
from common import Spr, c, _pick, _h, _vn, ROCK, CHAR, STEEL, SCREE  # noqa: E402
from scene import Scene  # noqa: E402
from roots import root, bez  # noqa: E402
import order as O, lumen as LU, terrain as T, signs as SG  # noqa: E402

AW, AH, WALL_H = 176, 352, 96
NODES = {
    "pb_vstup": (36, 110), "pb_brana": (66, 134), "pb_stred": (84, 186), "pb_generator": (42, 236),
    "pb_vez": (122, 274), "pb_hlidka": (64, 290), "pb_kontrola": (98, 296), "pb_k_rokli": (168, 306),
    "pb_dolu": (34, 342),
}
EDGES = [("pb_vstup", "pb_brana"), ("pb_brana", "pb_stred"), ("pb_stred", "pb_generator"), ("pb_stred", "pb_vez"),
         ("pb_stred", "pb_hlidka"), ("pb_generator", "pb_hlidka"), ("pb_vez", "pb_kontrola"),
         ("pb_kontrola", "pb_k_rokli"), ("pb_hlidka", "pb_dolu")]
sc = Scene(AW, AH, WALL_H, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(22)


def edge_y(x):
    """Okraj terasy: dole propast; vlevo dole cesta dál, vpravo římsa k lanovce."""
    if 16 <= x <= 54:
        return AH + 10
    base = 322 + math.sin(x / 19) * 7 + (_vn(x, 0, 61, 6) - 0.5) * 8
    if x > 130:
        base = 318 + (_vn(x, 0, 62, 5) - 0.5) * 4
    return base


for y in range(WALL_H, AH):
    for x in range(AW):
        ey = edge_y(x)
        if y < ey:
            v = 0.48 + (_vn(x * 1.2, y, 23, 6) - 0.5) * 0.35 + (_vn(x, y, 24, 2) - 0.5) * 0.14
            if _h(x, y, 25) % 41 == 0:
                v += 0.25                                                 # štěrk
            if abs(_vn(x, y * 1.3, 26, 11) - 0.5) < 0.025:
                v = 0.12
            img[y, x] = _pick(SCREE[1:], max(0.0, min(1.0, v)), x, y)
        elif y < ey + 8:
            img[y, x] = _pick(ROCK, max(0.0, 0.35 - (y - ey) * 0.04), x, y); block[y, x] = True
        else:
            k = min(1.0, (y - ey) / 40)
            col = np.array([12, 16, 30], float) * (1 - k) + np.array([4, 6, 14], float) * k
            m = _vn(x, y * 1.4, 27, 9)
            if m > 0.55:
                col += np.array([22, 30, 50]) * (m - 0.55) * 2
            img[y, x, :3] = np.clip(col, 0, 255); block[y, x] = True

# rozlámaná betonová plocha (desky posunuté a nadzvednuté kořenem)
P.concrete_floor(img, 52, 150, 160, 262)
for (x0, y0, x1, y1) in ((52, 150, 160, 152), (52, 260, 160, 262)):
    for y in range(y0, y1):
        for x in range(x0, x1):
            if _h(x, y, 5) % 3:
                img[y, x] = P.CONC[1]
for (x0, y0, w, h, dy) in ((76, 170, 24, 20, -2), (100, 190, 24, 20, -3), (124, 210, 24, 20, -2)):  # nadzvednuté desky
    for x in range(x0, x0 + w):
        for k in range(2):
            img[y0 + dy - k, x] = P.CONC[5 - k]
        img[y0 + h + 1, x] = P.CONC[0]

# Hunfreyho křída? Ne – tady jen značky Řádu: nastříkaná šipka a B na betonu
SG.painted(img, 60, 244, "B", c(0xD8AC30), seed=21, drips=False)

# ── kořeny (pohlcení 2/8) ───────────────────────────────────────────────────
root(sc, bez((70, WALL_H - 6), (96, 170), (150, 214)), 17, 9, lift=0.9, d=0.4)           # ze stěny přes plot a beton
root(sc, bez((150, 214), (168, 232), (178, 250)), 9, 6, lift=0.6, d=0.42)
root(sc, bez((120, 352), (128, 300), (118, 262)), 12, 6, lift=0.8, d=0.44)              # z propasti k věži
root(sc, bez((-6, 160), (20, 190), (26, 214)), 9, 4, lift=0.5, d=0.45)                 # zleva pod boudu
for (p0, p1, p2, w) in (((96, 170), (80, 192), (70, 214), 4), ((130, 200), (138, 180), (150, 168), 3.5),
                        ((118, 262), (100, 270), (92, 296), 3), ((26, 214), (40, 222), (52, 216), 2.5),
                        ((150, 214), (146, 236), (152, 252), 3)):
    root(sc, bez(p0, p1, p2, 30), w, 1.2, solid=False, d=0.5)

# ── stavby a předměty ───────────────────────────────────────────────────────
sc.place(O.fence(112, 22, 3, gap=(22, 44)), 108, 150, foot=None, collide=False)
block[146:150, 52:74] = True; block[146:150, 98:164] = True              # plot (průchod brankou a zmáčknutým úsekem)
warn = SG.plate("POZOR", c(0x141414), c(0xE2BA34), seed=17, pad=2, wear=0.0)
sc.place(warn, 140, 146, collide=False, shadow=False, z=152)
sc.place(O.gen_shed(4), 40, 226, foot=(52, 16))
sc.place(O.leaning_tower(40, 112), 130, 266, foot=(36, 12))
sc.place(O.flamer_rack(), 52, 284, foot=(24, 6))
sc.place(O.helmet(), 76, 298, collide=False)
sc.place(O.check_post(), 96, 290, foot=(6, 3))
sc.place(O.cable_pylon(70), 162, 304, foot=(18, 5))
sc.place(O.army_crate(7), 70, 170, pad=0)
sc.place(O.army_crate(8), 62, 182, pad=0)
sc.place(O.burnt_stump(14, 16, 9), 30, 312, pad=0)

# kabely od generátoru k věži (po zemi)
for t in np.linspace(0, 1, 140):
    X = int(66 + (112 - 66) * t); Y = int(222 + (258 - 222) * t + math.sin(t * 6) * 2)
    img[Y, X] = c(0x141418)
# lana od stožáru doprava do tmy
for (x0, y0) in ((151, 240), (170, 240)):
    for t in np.linspace(0, 1, 120):
        X, Y = int(x0 + (182 - x0) * t), int(y0 + (250 - y0) * t + math.sin(t * math.pi) * 4)
        if 0 <= X < AW and 0 <= Y < AH:
            img[Y, X] = STEEL[2]

# ── stíny, stěna se schody Lumenců, sprity ──────────────────────────────────
floor = np.zeros((AH, AW), bool)
for x in range(AW):
    floor[WALL_H:int(min(AH, edge_y(x))), x] = True
sc.shadows(floor)
img[:WALL_H] = T.cavern_wall(AW, WALL_H).a
block[:WALL_H + 4, :] = True
LU.carved_stairs(img, 4, 0, 26, WALL_H + 4, steps=12, width=16)
block[:WALL_H + 4, 4:42] = False                                          # po schodech se dá jít nahoru
block[:WALL_H - 10, :] = True; block[WALL_H - 10:WALL_H + 4, 4:44] = False
root(sc, bez((150, -6), (138, 40), (120, WALL_H - 6)), 9, 11, solid=False, d=0.3)      # kořen po stěně
SG.painted(img, 32, 34, "POZOROVATELNA B", c(0xE0D6B8), seed=31, wear=0.04)
SG.painted(img, 52, 50, "ŘÁD VÁHY", c(0xB8AE92), seed=32, wear=0.2)
sc.zbuf[:WALL_H] = -1e9
sc.draw_sprites()

fungi = []
for _ in range(8000):
    x, y = int(rng.integers(2, AW - 3)), int(rng.integers(10, 330))
    if sc.depth[y, x] and not sc.depth[y, x + 2] and len(fungi) < 60:
        fungi.append((x, y))
        img[y, x, :3] = (150, 210, 255); img[y, x - 1, :3] = (80, 140, 220); img[y + 1, x, :3] = (40, 70, 120)

sc.light(
    warm=[(40, 214, 30, 18, 0.9), (132, 172, 30, 20, 0.8), (80, 150, 30, 16, 0.7), (162, 240, 20, 14, 0.6),
          (90, 40, 46, 20, 0.6), (100, 286, 26, 14, 0.6), (52, 288, 26, 14, 0.6)],
    ember=[(96, 298, 8, 6, 0.6)],
    cold=[(24, 40, 22, 30, 0.6)] + [(x, y, 7, 6, 0.7) for (x, y) in fungi],
    dark_k=0.4)
sc.export("root_watch", "gen_watch_b.py")
print("FUNGI", fungi[:14])
