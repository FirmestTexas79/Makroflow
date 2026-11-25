"""
Použití:  python tools/mapgen/gen_station8.py
Výstup:   app/src/main/res/drawable-nodpi/root_camp.png, app/src/main/assets/walk/root_camp.txt,
          tools/mapgen/root_camp_debug.png

Zóna 3 (Akt III – Hlubina), lokace 1 z 8: STANICE 8 – předsunutá základna Řádu (176 × 352, pohled 3/4 shora,
docs/adr/0106). Skalní římsa pod stropem dutiny, kam ze stropu sjíždí ocelová šachta s klecí ze Sektoru Hlubiny.
Řád se tu zabydlel: betonová plocha se značením, plechová ubikace s vysílačkou a světlem v okně, nástěnka výprav,
pozorovatelna A s reflektorem do tmy, sodíkové stožáry, ohňová hlídka se sudy a spálenými pahýly kořenů,
spodní terminál lanovky ke kmeni, závora UZÁVĚRA na cestě dolů ke Svahu. Starší vrstva: ve skále fasáda Lumenců.
Kořeny Spící tu teprve začínají (stupeň pohlcení 1/8): lezou ze skály přes ruinu, obtáčí roh ubikace, zvedají
beton a ovíjejí nohu pozorovatelny. Díly: tools/mapgen/hlubina/. Bez setkání. Živé detaily: InteriorFxView.ROOT_CAMP.
NODES / EDGES jsou ZDROJ PRAVDY i pro RootCamp.kt (MAP).
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
import hideout_parts as P  # noqa: E402
from common import Spr, c, _pick, _h, _vn, ROCK, YELLOW, CHAR, STEEL, RUST  # noqa: E402
from scene import Scene  # noqa: E402
from roots import root, bez  # noqa: E402
import order as O, lumen as LU, terrain as T, signs as SG  # noqa: E402

AW, AH, WALL_H = 176, 352, 112
NODES = {
    "kt_klec": (88, 124), "kt_stred": (88, 168), "kt_ruina": (34, 124), "kt_radio": (28, 212), "kt_tabule": (78, 204),
    "kt_vez": (140, 246), "kt_hlidka": (108, 262), "kt_dole": (84, 290), "kt_lanovka": (130, 300),
    "kt_krida": (62, 312), "kt_sestup": (36, 338),
}
EDGES = [("kt_klec", "kt_stred"), ("kt_stred", "kt_ruina"), ("kt_stred", "kt_radio"), ("kt_stred", "kt_tabule"),
         ("kt_radio", "kt_tabule"), ("kt_stred", "kt_vez"), ("kt_vez", "kt_hlidka"), ("kt_stred", "kt_hlidka"),
         ("kt_hlidka", "kt_dole"), ("kt_dole", "kt_lanovka"), ("kt_dole", "kt_krida"), ("kt_krida", "kt_sestup")]
sc = Scene(AW, AH, WALL_H, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(8)


# ── podlaha: skalní římsa, okraj nad propastí, cesta dolů vlevo ─────────────
def edge_y(x):
    if 18 <= x <= 56:
        return AH + 10                                                    # cesta dolů ke Svahu
    if x > 112:
        return 334 + (_vn(x, 0, 42, 5) - 0.5) * 4
    return 326 + math.sin(x / 23) * 6 + (_vn(x, 0, 41, 6) - 0.5) * 6


for y in range(WALL_H, AH):
    for x in range(AW):
        ey = edge_y(x)
        if y < ey:
            v = 0.5 + (_vn(x * 1.2, y, 13, 6) - 0.5) * 0.35 + (_vn(x, y, 14, 2) - 0.5) * 0.12
            if abs(_vn(x, y * 1.3, 15, 11) - 0.5) < 0.025:
                v = 0.12
            img[y, x] = _pick(ROCK[1:], max(0.0, min(1.0, v)), x, y)
        elif y < ey + 8:                                                  # čelo římsy
            img[y, x] = _pick(ROCK, max(0.0, 0.35 - (y - ey) * 0.04), x, y); block[y, x] = True
        else:                                                             # propast s mlhou
            k = min(1.0, (y - ey) / 40)
            col = np.array([12, 16, 30], float) * (1 - k) + np.array([4, 6, 14], float) * k
            m = _vn(x, y * 1.4, 17, 9)
            if m > 0.55:
                col += np.array([22, 30, 50]) * (m - 0.55) * 2
            img[y, x, :3] = np.clip(col, 0, 255); block[y, x] = True

# betonová plocha se žlutým značením a kruhem S-8
AP = (14, WALL_H + 2, 162, 272)
P.concrete_floor(img, *AP)
for x in range(AP[0], AP[2]):
    for y in range(AP[3], AP[3] + 3):
        if _h(x, 1, 3) % 5:
            img[y, x] = P.CONC[1]
for y in range(AP[1] + 10, AP[3] - 6):
    for x in (58, 118):
        if (y // 6) % 2 == 0:
            img[y, x] = YELLOW[1]
for a in range(0, 360, 3):
    x = int(88 + math.cos(math.radians(a)) * 24); y = int(216 + math.sin(math.radians(a)) * 13)
    img[y, x] = YELLOW[1]
SG.painted(img, 72, 210, "S-8", YELLOW[2], scale=1, seed=3, drips=False)

# ── kořeny (z-buffer: co leží za nimi, je nepřekreslí) ──────────────────────
root(sc, bez((4, WALL_H - 4), (2, 176), (30, 222)), 12, 6, lift=0.6, d=0.4)         # ze skály kolem rohu ubikace
root(sc, bez((180, 182), (150, 196), (118, 226)), 13, 7, lift=0.8, d=0.42)          # zprava pod pozorovatelnou
root(sc, bez((92, 348), (98, 300), (84, 276)), 10, 5, lift=0.7, d=0.45)            # z propasti zvedá beton
for (p0, p1, p2, w) in (((30, 222), (52, 226), (56, 244), 3), ((118, 226), (100, 232), (94, 246), 2.5),
                        ((84, 276), (70, 270), (60, 280), 2.5), ((150, 196), (160, 220), (170, 228), 3)):
    root(sc, bez(p0, p1, p2, 30), w, 1.2, solid=False, d=0.5)
for x in range(70, 104):                                                  # zvednutá deska betonu nad kořenem
    for k in range(3):
        img[270 - k + int(abs(x - 88) * 0.08), x] = P.CONC[5 - k]
    img[273 + int(abs(x - 88) * 0.08), x] = P.CONC[0]
for (x, y) in ((118, 226), (84, 276)):                                    # spálené konce (hlídka)
    for yy in range(y - 5, y + 6):
        for xx in range(x - 6, x + 7):
            if (xx - x) ** 2 + ((yy - y) * 1.3) ** 2 < 30 and sc.depth[yy, xx]:
                img[yy, xx] = _pick(CHAR, max(0.0, min(1.0, 0.5 + (_vn(xx, yy, 5, 2) - 0.5))), xx, yy)
                if _h(xx, yy, 4) % 17 == 0:
                    img[yy, xx] = c(0xE06020)

# ── stavby a předměty ───────────────────────────────────────────────────────
sc.place(O.radio_mast(44), 22, 170, foot=(10, 4))
sc.place(O.barrack(56, 40), 44, 200, foot=(56, 22))
sc.place(O.notice_board(), 80, 196, foot=(28, 5))
for (x, y, sd) in ((101, 178, 1), (119, 176, 2), (110, 194, 3), (150, 150, 4), (70, 142, 5)):
    sc.place(O.army_crate(sd), x, y, pad=0)
sc.place(O.flood_mast(56), 112, 150, foot=(10, 4))
sc.place(O.obs_tower(40, 104), 142, 238, foot=(34, 12))
sc.place(O.sandbags(40, 2), 96, 254, pad=0)
sc.place(O.fuel_drums(3), 126, 268, pad=0)
sc.place(O.burnt_stump(18, 22, 1), 158, 292, pad=0)
sc.place(O.burnt_stump(14, 16, 2), 106, 300, pad=0)
sc.place(O.flood_mast(56), 22, 286, foot=(10, 4))
cab = O.cable_station(50, 36)
pl = SG.plate("> KMEN", c(0x1A1A1A), c(0xD8B030), seed=5, pad=2)
sc.place(cab, 146, 326, foot=(50, 22))
sc.place(O.barrier(64), 40, 330, collide=False)
block[314:330, 8:17] = True; block[314:330, 63:72] = True                # patky závory

# lana lanovky přes okraj do tmy
for (x0, y0) in ((136, 302), (140, 300)):
    for t in np.linspace(0, 1, 260):
        X, Y = int(x0 + (182 - x0) * t), int(y0 + (360 - y0) * t + math.sin(t * math.pi) * 5)
        if 0 <= X < AW and 0 <= Y < AH:
            img[Y, X] = STEEL[2] if t < 0.5 else STEEL[1]

# Hunfreyho křída na betonu u závory: šipka dolů a 212
CH = c(0xEAE6DA)
for y in range(296, 308):
    img[y, 76] = CH
for k in range(4):
    img[308 - k, 76 - k] = CH; img[308 - k, 76 + k] = CH
SG.painted(img, 82, 298, "212", CH, seed=6, drips=False, spray=False, wear=0.25)

# ── stíny, zadní stěna, sprity ──────────────────────────────────────────────
floor = np.zeros((AH, AW), bool)
for x in range(AW):
    floor[WALL_H:int(min(AH, edge_y(x))), x] = True
sc.shadows(floor)

img[:WALL_H] = T.cavern_wall(AW, WALL_H).a
block[:WALL_H + 4, :] = True
sc.zbuf[:WALL_H] = -1e9
sc.put(LU.ruin_facade(54, 70), 6, WALL_H - 70)
sc.put(O.shaft_lift(46, 112), 65, 0)
for y in range(0, WALL_H):                                                # kabely a potrubí ze stropu
    for (x, col) in ((169, STEEL[2]), (170, STEEL[1]), (172, RUST[2]), (173, RUST[1]), (174, RUST[0])):
        img[y, x] = col
    if y % 16 == 4:
        img[y, 168:176] = STEEL[3]
SG.painted(img, 112, 32, "STANICE 8", c(0xE8E0C4), seed=1, wear=0.1)                 # šablona na skále
SG.painted(img, 118, 44, "HLUBINA", c(0xC8BEA0), seed=2, wear=0.12)
info = SG.plate("ŘÁD VÁHY", c(0xE8E2CC), c(0x1E2A44), seed=7, pad=3)                  # smaltovaná cedule
sc.put(info, 138 - info.w // 2, 60)
lamp = Spr(10, 6); lamp.rect(0, 0, 9, 4, STEEL[2]); lamp.rect(1, 3, 8, 4, O.SODIUM)
sc.put(lamp, 132, 22)
root(sc, bez((-6, 10), (20, 40), (14, WALL_H - 2)), 11, 12, solid=False, d=0.3)      # kořeny ze skály přes ruinu
root(sc, bez((44, -4), (52, 40), (28, 70)), 6, 3, solid=False, d=0.32)
root(sc, bez((176, 70), (160, 92), (178, 120)), 7, 7, solid=False, d=0.3)
sc.zbuf[:WALL_H] = -1e9                                                   # stěna je za vším na podlaze

# terminál lanovky dostane ceduli až teď (je to sprite, kreslí se se z-testem)
for yy in range(pl.h):
    for xx in range(pl.w):
        if pl.a[yy, xx, 3]:
            cab.a[1 + yy, 1 + xx] = pl.a[yy, xx]
sc.draw_sprites()

# modré houby na hranách kořenů
fungi = []
for _ in range(6000):
    x, y = int(rng.integers(2, AW - 3)), int(rng.integers(20, 330))
    if sc.depth[y, x] and not sc.depth[y, x + 2] and len(fungi) < 40:
        fungi.append((x, y))
        img[y, x, :3] = (150, 210, 255); img[y, x - 1, :3] = (80, 140, 220); img[y + 1, x, :3] = (40, 70, 120)

# ── světlo a export ─────────────────────────────────────────────────────────
sc.light(
    warm=[(88, 128, 30, 20, 0.9), (112, 178, 46, 32, 1.0), (26, 302, 42, 28, 1.0), (56, 196, 22, 12, 0.7),
          (138, 44, 34, 28, 0.9), (150, 318, 30, 16, 0.6)],
    ember=[(118, 226, 10, 7, 0.8), (84, 276, 9, 6, 0.7), (158, 284, 9, 6, 0.6)],
    cold=[(33, 72, 40, 36, 1.0), (33, 96, 18, 10, 0.8)] + [(x, y, 6, 5, 0.6) for (x, y) in fungi])
sc.export("root_camp", "gen_station8.py")
print("FUNGI", fungi[:12])
