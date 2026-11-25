"""
Použití:  python tools/mapgen/gen_slope_camp.py
Výstup:   app/src/main/res/drawable-nodpi/root_slopecamp.png, app/src/main/assets/walk/root_slopecamp.txt,
          tools/mapgen/root_slopecamp_debug.png

Zóna 3 (Akt III – Hlubina), lokace 4 z 8: TÁBOR NA SVAHU (176 × 352, pohled 3/4 shora, docs/adr/0106).
Fáze 3 obsazení Hlubiny – OPUŠTĚNO. Odsud výpravy 1–7 sestupovaly na Svah bezejmenných a žádná se nevrátila.
Dolů se sem sestupuje po schodech z terasy Pozorovatelny B. U vstupu registrační stolek s knihou zápisů (jména
přeškrtaná, místo nich čísla), polní kuchyně pod stříškou, stany výprav 1, 3, 5 a 7 s praporky. Pohlcení 4/8:
středem tábora prošel kořen a co stálo v cestě – stany výprav 2, 4 a 6 – spolkl; z kořenů trčí jen tyče
s praporky. Řád přes kořen položil prkennou lávku. Dole nad svahem sčítací tabule (NEPOČÍTEJTE), naviják s lanem,
řada měřicích kolíků do mlhy a žebra dlouhého domu Lumenců, které kořeny stahují do země.
Setkání DUCH. Živé detaily: InteriorFxView.ROOT_SLOPECAMP. NODES / EDGES jsou ZDROJ PRAVDY i pro RootSlopeCamp.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import Spr, c, _pick, _h, _vn, ROCK, STEEL, SCREE  # noqa: E402
from scene import Scene  # noqa: E402
from roots import root, bez  # noqa: E402
import order as O, lumen as LU, terrain as T, signs as SG  # noqa: E402

AW, AH, WALL_H = 176, 352, 72
NODES = {
    "tv_vstup": (22, 86), "tv_ruina": (140, 84), "tv_registr": (62, 124), "tv_kuchyne": (122, 146), "tv_stan1": (34, 196),
    "tv_most": (88, 209), "tv_stan5": (146, 240), "tv_stan3": (46, 262), "tv_tabule": (58, 314),
    "tv_stan7": (132, 302), "tv_navijak": (154, 330), "tv_dolu": (92, 346),
}
EDGES = [("tv_vstup", "tv_registr"), ("tv_registr", "tv_ruina"), ("tv_registr", "tv_kuchyne"), ("tv_registr", "tv_stan1"), ("tv_stan1", "tv_most"),
         ("tv_most", "tv_kuchyne"), ("tv_most", "tv_stan5"), ("tv_stan1", "tv_stan3"), ("tv_stan3", "tv_tabule"),
         ("tv_stan5", "tv_stan7"), ("tv_stan7", "tv_navijak"), ("tv_tabule", "tv_dolu"), ("tv_stan7", "tv_dolu")]
sc = Scene(AW, AH, WALL_H, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(44)
PLANK = [c(0x24180E), c(0x3C2818), c(0x5A3E26), c(0x7A5836), c(0x9A7448)]

# ── země: udusaná suť, níž do svahu tmavší, dole mlha nad Svahem ────────────
for y in range(WALL_H, AH):
    for x in range(AW):
        k = max(0.0, (y - 280) / 72)
        v = 0.5 + (_vn(x * 1.2, y, 23, 6) - 0.5) * 0.3 + (_vn(x, y, 24, 2) - 0.5) * 0.14 - k * 0.35
        if _h(x, y, 25) % 37 == 0:
            v += 0.22
        if abs(_vn(x, y * 1.3, 26, 11) - 0.5) < 0.02:
            v = 0.12
        img[y, x] = _pick(SCREE, max(0.0, min(1.0, v)), x, y)

# prkenné chodníky Řádu (kladou se před kořeny, lávka přes kořen až po nich)
def boards(x0, y0, x1, y1, w=10):
    n = int(math.hypot(x1 - x0, y1 - y0))
    ux, uy = (x1 - x0) / max(1, n), (y1 - y0) / max(1, n)
    px_, py_ = -uy, ux
    for i in range(n):
        for k in np.linspace(-w / 2, w / 2, w * 2):
            X, Y = int(round(x0 + ux * i + px_ * k)), int(round(y0 + uy * i + py_ * k))
            if 0 <= X < AW and 0 <= Y < AH:
                seam = (i % 5 == 0)
                edge = abs(k) > w / 2 - 1
                v = 0.15 if seam else 0.3 if edge else 0.55 + (_vn(X, Y, 81, 2) - 0.5) * 0.3
                img[Y, X] = _pick(PLANK, max(0.0, min(1.0, v)), X, Y)


for (a, b) in ((NODES["tv_vstup"], NODES["tv_registr"]), (NODES["tv_registr"], (40, 180)), ((40, 180), (46, 300)),
               ((46, 300), (88, 340)), (NODES["tv_registr"], (110, 140)), ((128, 214), (140, 290)), ((140, 290), (100, 340))):
    boards(a[0], a[1], b[0], b[1])

# ── kořen středem tábora (pohlcení 4/8) a výhonky ───────────────────────────
root(sc, bez((182, 40), (150, 120), (76, 266)), 24, 13, lift=1.0, d=0.38)
root(sc, bez((76, 266), (60, 300), (70, 360)), 13, 9, lift=0.8, d=0.4)
root(sc, bez((-6, 120), (6, 150), (14, 170)), 9, 5, lift=0.5, d=0.45)
for (p0, p1, p2, w) in (((130, 150), (160, 160), (178, 176), 6), ((106, 206), (130, 214), (150, 206), 4),
                        ((92, 236), (70, 230), (60, 214), 4), ((150, 110), (126, 100), (104, 104), 4.5),
                        ((70, 290), (100, 296), (118, 312), 3)):
    root(sc, bez(p0, p1, p2, 30), w, 1.5, solid=False, d=0.5)
# lávka přes kořen: prkna z levého chodníku na pravý, leží nad kořenem (z-buffer), po okrajích lanové zábradlí
bx0, by0, bx1, by1 = 44, 204, 130, 214
n = int(math.hypot(bx1 - bx0, by1 - by0)); ux, uy = (bx1 - bx0) / n, (by1 - by0) / n
for i in range(n):
    for k in np.linspace(-6, 6, 26):
        X, Y = int(round(bx0 + ux * i - uy * k)), int(round(by0 + uy * i + ux * k))
        seam = i % 5 == 0
        edge = abs(k) > 5
        img[Y, X] = _pick(PLANK, 0.15 if seam else 0.32 if edge else 0.6 + (_vn(X, Y, 81, 2) - 0.5) * 0.25, X, Y)
        sc.zbuf[Y, X] = 1e9; block[Y, X] = False
    for side in (-7, 7):                                                  # lano zábradlí a sloupky
        X, Y = int(round(bx0 + ux * i - uy * side)), int(round(by0 + uy * i + ux * side)) - 3
        img[Y, X] = c(0xC8BE9C); sc.zbuf[Y, X] = 1e9
        if i % 14 == 0:
            for q in range(4):
                img[Y + q, X] = PLANK[1]; sc.zbuf[Y + q, X] = 1e9
    X, Y = int(round(bx0 + ux * i)), int(round(by0 + uy * i)) + 8           # stín lávky
    if sc.depth[Y, X]:
        img[Y, X, :3] = (img[Y, X, :3] * 0.5).astype(np.uint8)

# ── stavby a předměty ───────────────────────────────────────────────────────
sign = SG.plate("TÁBOR VÝPRAV", c(0xE8E2CC), c(0x1E2A44), seed=3, pad=2, wear=0.0)
sc.place(O.mount_plate(sign, posts=10), 58, 98, foot=(sign.w, 3), collide=False)
block[95:98, 58 - sign.w // 2 + 2:58 - sign.w // 2 + 5] = True
sc.place(O.reg_desk(), 64, 118, foot=(32, 8))
sc.place(O.mess_canopy(58, 40), 124, 138, foot=(10, 4))
block[124:138, 98:104] = True; block[124:138, 146:152] = True             # tyče stříšky
sc.place(O.ridge_tent(1, 0.1, seed=1), 34, 192, foot=(46, 20))
sc.place(O.ridge_tent(3, 0.3, seed=3), 40, 258, foot=(46, 20))
sc.place(O.ridge_tent(5, 0.35, seed=5), 150, 242, foot=(46, 20))
sc.place(O.ridge_tent(7, 0.15, seed=7), 138, 300, foot=(46, 20))
for (num, x, y) in (("2", 154, 104), ("4", 118, 172), ("6", 80, 266)):     # spolknuté stany: jen tyče s praporky
    sc.place(O.pennant(num, 26), x, y, collide=False, z=y + 40)
    for k in range(10):                                                    # cáry plachty v kořenech
        X, Y = x - 8 + int(rng.integers(0, 16)), y - 2 + int(rng.integers(0, 6))
        img[Y, X] = O.CANVAS[int(rng.integers(1, 4))]
sc.place(O.count_board("SČÍTÁNÍ"), 56, 306, foot=(10, 3))
sc.place(O.winch(), 156, 324, foot=(26, 8))
for i, (x, y) in enumerate(((96, 304), (100, 318), (104, 332), (108, 346))):   # kolíky dolů do mlhy
    sc.place(O.stake(18 - i * 2), x, y, collide=False)
for i in range(3):                                                         # lano mezi kolíky
    for t in np.linspace(0, 1, 20):
        X = int(96 + i * 4 + 4 * t); Y = int(290 + i * 14 + 14 * t + math.sin(t * math.pi) * 2)
        img[Y, X] = c(0xC8BE9C)
for t in np.linspace(0, 1, 120):                                           # lano z navijáku dolů po svahu
    X, Y = int(160 + 14 * t), int(322 + 34 * t)
    if Y < AH:
        img[Y, X] = c(0x3A3428)

floor = np.zeros((AH, AW), bool); floor[WALL_H:, :] = True
sc.shadows(floor)

# ── stěna: skalní převis, schody shora, nástřik ─────────────────────────────
img[:WALL_H] = T.cavern_wall(AW, WALL_H).a
block[:WALL_H + 4, :] = True
LU.carved_stairs(img, 4, 0, 14, WALL_H + 6, steps=9, width=18)
block[WALL_H - 8:WALL_H + 6, 10:34] = False
SG.painted(img, 62, 30, "FÁZE 3", c(0xE0D6B8), seed=11, wear=0.05)
SG.painted(img, 62, 42, "OPUŠTĚNO", c(0xC8402E), seed=12, wear=0.05)
sc.put(LU.longhouse_ribs(56, 34, 0.6, seed=2), 116, WALL_H - 36)                 # žebra dlouhého domu ve skále
sc.zbuf[:WALL_H] = -1e9
sc.draw_sprites()

# mlha nad Svahem (dole) – řídký závoj přes spodek mapy
for y in range(300, AH):
    a = (y - 300) / 52 * 0.55
    for x in range(AW):
        if (x + y) % 4 == 0 or (y > 330 and (x * 3 + y) % 3 == 0):
            img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([46, 58, 86]) * a, 0, 255).astype(np.uint8)

fungi = []
for _ in range(9000):
    x, y = int(rng.integers(2, AW - 3)), int(rng.integers(40, 330))
    if sc.depth[y, x] and not sc.depth[y, x + 2] and len(fungi) < 70:
        fungi.append((x, y))
        img[y, x, :3] = (150, 210, 255); img[y, x - 1, :3] = (80, 140, 220); img[y + 1, x, :3] = (40, 70, 120)

sc.light(
    warm=[(64, 112, 30, 20, 1.0), (140, 290, 24, 16, 0.7), (58, 300, 22, 14, 0.5), (22, 84, 20, 14, 0.4)],
    cold=[(x, y, 8, 7, 0.75) for (x, y) in fungi] + [(110, 190, 50, 60, 0.35), (144, 54, 34, 20, 0.7)],
    dark_k=0.4)
sc.export("root_slopecamp", "gen_slope_camp.py")
print("FUNGI", fungi[:14])
