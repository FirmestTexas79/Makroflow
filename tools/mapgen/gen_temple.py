"""
Použití:  python tools/mapgen/gen_temple.py
Výstup:   app/src/main/res/drawable-nodpi/root_temple.png, app/src/main/assets/walk/root_temple.txt,
          tools/mapgen/root_temple_debug.png

Zóna 3 (Akt III – Hlubina), lokace 6 z 8: CHRÁM LUMENCŮ (176 × 352, pohled 3/4 shora, docs/adr/0106).
Ze Svahu bezejmenných se sestupuje po schodech Lumenců do lodi chrámu, který je skoro celý v kořenech (pohlcení 6/8):
kořeny se valí stropem a zdmi, obepínají sloupy, zvedají dlažbu. Na zadní stěně čtyři malby – příběh, který Řád
nikdy nezapsal: strom s hvězdou v koruně a lidé se světlem v dlaních; král, který šplhá a hvězdu utrhne; král, který
ji sní, a strom, který se obrací; žena ve stromě visící pod světem a lidé, kteří odcházejí do tmy. Před malbami oltář
přání – kamenná mísa plná drobností. V lodi bezhlavá socha jelena ve sloupu bledého světla z prasklé klenby. Jediný
průzkumný kolík Řádu se štítkem NEINVENTARIZOVAT (razítko S-0). Setkání PSYCHO a VÍLA.
Živé detaily: InteriorFxView.ROOT_TEMPLE. NODES / EDGES jsou ZDROJ PRAVDY i pro RootTemple.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import Spr, c, _pick, _h, _vn, ROCK, GLYPH, BARK  # noqa: E402
from scene import Scene  # noqa: E402
from roots import root, bez  # noqa: E402
import order as O, lumen as LU, signs as SG  # noqa: E402

AW, AH, WALL_H = 176, 352, 100
NODES = {
    "ch_vstup": (88, 338), "ch_lod": (88, 292), "ch_jelen": (58, 262), "ch_kolik": (118, 276), "ch_lod2": (88, 216),
    "ch_oltar": (88, 170), "ch_m1": (28, 116), "ch_m2": (64, 112), "ch_m3": (112, 112), "ch_m4": (148, 116),
}
EDGES = [("ch_vstup", "ch_lod"), ("ch_lod", "ch_jelen"), ("ch_lod", "ch_kolik"), ("ch_lod", "ch_lod2"), ("ch_jelen", "ch_lod2"),
         ("ch_lod2", "ch_oltar"), ("ch_oltar", "ch_m2"), ("ch_oltar", "ch_m3"), ("ch_m1", "ch_m2"), ("ch_m3", "ch_m4")]
sc = Scene(AW, AH, WALL_H, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(66)
TS = LU.TSTONE

# ── podlaha: kamenné dlaždice, uprostřed mozaikový pás se znaky, praskliny ───
for y in range(WALL_H, AH):
    for x in range(AW):
        tx, ty = (x - 10) % 26, (y - WALL_H) % 20
        v = 0.34 + (_vn(x * 1.3, y * 1.3, 11, 4) - 0.5) * 0.14 + (_h((x - 10) // 26, (y - WALL_H) // 20, 12) % 5) * 0.025
        if tx == 0 or ty == 0:
            v = 0.12
        elif ty == 1:
            v += 0.1
        if 70 <= x <= 106:                                                 # mozaikový pás lodí
            v += 0.06
            if x in (70, 71, 105, 106):
                v = 0.7
            q = ((x - 88) % 12, (y - WALL_H) % 20)
            if q in ((0, 9), (1, 10), (11, 10), (0, 11)) and y > WALL_H + 4:
                img[y, x, :3] = (78, 120, 180)
                continue
            if q in ((6, 0), (6, 1), (6, 19)):
                img[y, x, :3] = (150, 120, 70)
                continue
        if abs(_vn(x * 0.9, y * 0.9, 13, 9) - 0.5) < 0.02:
            v = 0.08                                                       # praskliny
        img[y, x] = _pick(TS, max(0.0, min(1.0, v)), x, y)
for y in range(WALL_H, AH):                                               # boční zdi lodi
    for x in list(range(0, 10)) + list(range(166, AW)):
        k = (10 - x) / 10 if x < 10 else (x - 165) / 10
        img[y, x] = _pick(TS, 0.34 - k * 0.2 + (_vn(x, y * 0.4, 14, 5) - 0.5) * 0.2, x, y)
        block[y, x] = True
LU.carved_stairs(img, 70, 330, 106, AH, steps=3, width=36)              # schody nahoru na Svah

# ── kořeny (6/8): stropem a zdmi do lodi, kolem sloupů, zvedají dlažbu ───────


def chain(*pts, n=24):
    out = []
    mids = [pts[0]] + [((a[0] + b[0]) / 2, (a[1] + b[1]) / 2) for a, b in zip(pts[1:-1], pts[2:])] + [pts[-1]]
    for i in range(len(pts) - 2):
        out += bez(mids[i], pts[i + 1], mids[i + 1], n)
    return out


root(sc, chain((-6, 110), (16, 150), (10, 200), (20, 250), (8, 300), (-6, 352)), 18, 12, solid=True, lift=0.8, d=0.4)
root(sc, chain((182, 100), (160, 150), (170, 210), (158, 260), (172, 352)), 16, 12, solid=True, lift=0.8, d=0.4)
root(sc, chain((30, 140), (46, 170), (40, 196), (52, 230)), 7, 3, solid=False, lift=0.4, d=0.45)
root(sc, chain((150, 140), (132, 180), (140, 214), (128, 236)), 6, 2, solid=False, lift=0.4, d=0.45)
root(sc, chain((10, 290), (40, 300), (60, 320), (62, 340)), 6, 2, solid=False, lift=0.4, d=0.45)
root(sc, chain((166, 300), (140, 312), (124, 330)), 5, 2, solid=False, lift=0.4, d=0.45)
for (x0, y0) in ((44, 300), (136, 222), (30, 176)):                       # zvednuté dlaždice
    for y in range(y0 - 3, y0 + 4):
        for x in range(x0 - 6, x0 + 7):
            if not sc.depth[y, x]:
                img[y, x, :3] = (img[y, x, :3] * (1.25 if y < y0 else 0.6)).clip(0, 255).astype(np.uint8)

# ── předměty ────────────────────────────────────────────────────────────────
COLS = [(26, 168, 0), (150, 168, 0), (26, 228, 24), (150, 228, 0), (26, 290, 0), (150, 290, 18), (26, 350, 0), (150, 350, 0)]
for i, (x, y, br) in enumerate(COLS):
    sc.place(LU.column(60, broken=br, seed=i), x, y, foot=(16, 8))
sc.place(LU.column_drum(1), 46, 240, foot=(20, 6))
sc.place(LU.column_drum(2), 132, 310, foot=(20, 6))
sc.place(LU.wish_altar(), 88, 158, foot=(30, 10))
sc.place(LU.deer_statue(), 58, 250, foot=(30, 10))
tag = Spr(40, 26)                                                         # kolík Řádu s papírovým štítkem
for y in range(4, 26):
    tag.px(19, y, O.RED[3] if (y // 3) % 2 == 0 else c(0xE6E0D0)); tag.px(20, y, O.RED[2] if (y // 3) % 2 == 0 else c(0xBEB8A8))
for y in range(0, 14):
    for x in range(1, 39):
        tag.px(x, y, O.PAPER[2] if 0 < y < 13 and 1 < x < 38 else O.PAPER[0])
SG.tiny(tag, 4, 1, "NEINVEN-", c(0x2A2418))
SG.tiny(tag, 2, 7, "TARIZOVAT", c(0x2A2418))
tag.px(34, 2, O.RED[3]); tag.px(35, 2, O.RED[3]); tag.px(34, 3, O.RED[3]); tag.px(35, 3, O.RED[3])
sc.place(tag, 124, 268, foot=(4, 3))

floor = np.zeros((AH, AW), bool); floor[WALL_H:, 10:166] = True
sc.shadows(floor)

# ── zadní stěna: kámen Lumenců s vlysem znaků a čtyřmi malbami ───────────────
for y in range(WALL_H):
    for x in range(AW):
        v = 0.4 + (_vn(x * 0.8, y * 1.6, 21, 6) - 0.5) * 0.16 - (1 - y / WALL_H) * 0.18
        if y % 16 == 0 or (x + (8 if (y // 16) % 2 else 0)) % 24 == 0:
            v = 0.2                                                        # kvádry
        if y > WALL_H - 6:
            v -= 0.12
        img[y, x] = _pick(TS, max(0.0, min(1.0, v)), x, y)
for x in range(4, AW - 4, 5):                                             # vlys svítících znaků nahoře
    img[12, x, :3] = (126, 192, 255); img[13, x + 1, :3] = (74, 128, 192)

OCH, RST, BLU, CRM, DRK, STAR = (176, 138, 74), (138, 58, 38), (74, 112, 160), (216, 200, 160), (42, 36, 32), (255, 226, 130)
PANELS = [(10, 26), (50, 26), (94, 26), (134, 26)]
PW, PH = 34, 56


def pp(px0, py0, x, y, col):
    X, Y = px0 + x, py0 + y
    if 0 <= x < PW and 0 <= y < PH:
        img[Y, X, :3] = col


def figure(px0, py0, x, y, col, crown=False, light=False, h=7):
    for k in range(h - 2):
        pp(px0, py0, x, y + 2 + k, col); pp(px0, py0, x + 1, y + 2 + k, col) if k < h - 4 else None
    pp(px0, py0, x, y, CRM); pp(px0, py0, x, y + 1, CRM)
    pp(px0, py0, x - 1, y + h - 1, col); pp(px0, py0, x + 1, y + h - 1, col)
    if crown:
        pp(px0, py0, x - 1, y - 1, STAR); pp(px0, py0, x, y - 2, STAR); pp(px0, py0, x + 1, y - 1, STAR)
    if light:
        pp(px0, py0, x + 1, y + 3, (190, 230, 255)); pp(px0, py0, x + 2, y + 3, (120, 170, 230))


def blob(px0, py0, cx, cy, rx, ry, col, hi, edge):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
            if d < 1:
                pp(px0, py0, x, y, edge if d > 0.78 else hi if (x - cx) + (y - cy) < -rx * 0.4 else col)


def tree(px0, py0, cx, top, h, inverted=False):
    if not inverted:                                                      # kmen a košatá koruna (tři laloky)
        for y in range(top + 12, top + h):
            pp(px0, py0, cx, y, DRK); pp(px0, py0, cx + 1, y, RST); pp(px0, py0, cx - 1, y, DRK)
        for k, (dx, dy, r) in enumerate(((-6, 10, 7), (6, 10, 7), (0, 6, 9))):
            blob(px0, py0, cx + dx, top + dy, r, r * 0.8, OCH, (214, 176, 100), (96, 70, 36))
    else:                                                                 # kořeny nahoru, koruna dolů do modré tmy
        for k in (-5, -2, 2, 5):
            for y in range(top, top + 8):
                pp(px0, py0, cx + k * (8 - (y - top)) // 8, y, DRK)
        for y in range(top + 6, top + h - 12):
            pp(px0, py0, cx, y, DRK); pp(px0, py0, cx + 1, y, RST); pp(px0, py0, cx - 1, y, DRK)
        for k, (dx, dy, r) in enumerate(((-6, -8, 7), (6, -8, 7), (0, -4, 9))):
            blob(px0, py0, cx + dx, top + h + dy, r, r * 0.8, (54, 70, 100), BLU, (24, 30, 46))


for i, (px0, py0) in enumerate(PANELS):
    for y in range(-2, PH + 2):                                           # rám a podklad
        for x in range(-2, PW + 2):
            frame = x < 0 or y < 0 or x >= PW or y >= PH
            img[py0 + y, px0 + x] = _pick(TS, 0.66 if frame and (x < 0 or y < 0) else 0.28 if frame else 0.0, px0 + x, py0 + y) if frame \
                else np.array([168, 150, 116, 255], np.uint8) * (0.82 + 0.18 * _vn(px0 + x, py0 + y, 22, 4))
    if i == 0:                                                            # strom s hvězdou, lidé se světlem
        tree(px0, py0, 16, 6, 40)
        for k in range(3):
            pp(px0, py0, 16 + k - 1, 10, STAR); pp(px0, py0, 17, 9 + k, STAR)
        for k in range(5):
            figure(px0, py0, 4 + k * 6, 44, BLU, light=True)
    elif i == 1:                                                          # král šplhá a utrhne hvězdu
        tree(px0, py0, 14, 6, 40)
        figure(px0, py0, 20, 14, RST, crown=True)
        pp(px0, py0, 21, 13, STAR); pp(px0, py0, 22, 12, STAR); pp(px0, py0, 22, 13, STAR)
        for k in range(4):
            figure(px0, py0, 4 + k * 7, 46, BLU)
    elif i == 2:                                                          # král hvězdu sní, strom se obrací
        figure(px0, py0, 8, 30, RST, crown=True, h=10)
        pp(px0, py0, 8, 30, STAR); pp(px0, py0, 9, 31, STAR)
        tree(px0, py0, 22, 6, 46, inverted=True)
        for y in range(10, 44, 3):
            pp(px0, py0, 18 + (y % 7), y, DRK)                            # stéká tma
    else:                                                                 # žena ve stromě pod světem, lidé odcházejí
        tree(px0, py0, 12, 0, 50, inverted=True)
        for y in range(12, 24):
            pp(px0, py0, 12, y, CRM); pp(px0, py0, 13, y, (230, 222, 200))
        pp(px0, py0, 11, 24, CRM); pp(px0, py0, 14, 24, CRM)               # ruce dolů – visí
        for k in range(4):
            figure(px0, py0, 22 + k * 3, 30 + k * 4, BLU, light=k < 3, h=6)
        for x in range(2, 32, 3):
            pp(px0, py0, x, 53, BLU)                                       # vyrytý řádek pod malbou
    for _ in range(40):                                                   # oprýskání a praskliny
        x, y = int(rng.integers(0, PW)), int(rng.integers(0, PH))
        img[py0 + y, px0 + x, :3] = (img[py0 + y, px0 + x, :3] * 0.55).astype(np.uint8)
    for k in range(18):
        x = int(PW * 0.3 + math.sin(k * 0.7 + i) * 6 + k * 0.6); y = int(k * 3)
        if 0 <= x < PW and 0 <= y < PH:
            img[py0 + y, px0 + x, :3] = (img[py0 + y, px0 + x, :3] * 0.4).astype(np.uint8)
# kořeny ze stropu přes stěnu a malby (čtvrtou skoro celou zakrývají)
root(sc, chain((120, -8), (132, 30), (128, 70), (140, WALL_H + 4)), 9, 7, solid=False, d=0.35)
root(sc, chain((176, 10), (160, 40), (168, 90), (158, WALL_H + 6)), 12, 10, solid=False, d=0.35)
root(sc, chain((20, -8), (6, 40), (14, 80), (4, WALL_H + 6)), 10, 9, solid=False, d=0.35)
root(sc, chain((60, -6), (52, 20), (46, 30)), 6, 2, solid=False, d=0.35)
block[:WALL_H + 4, :] = True
sc.zbuf[:WALL_H] = -1e9
sc.draw_sprites()

fungi = []
for _ in range(8000):
    x, y = int(rng.integers(4, AW - 4)), int(rng.integers(10, AH - 6))
    if sc.depth[y, x] and not sc.depth[y, x + 2] and len(fungi) < 24:
        fungi.append((x, y))
        img[y, x, :3] = (150, 210, 255); img[y, x - 1, :3] = (80, 140, 220); img[y + 1, x, :3] = (40, 70, 120)

# sloup bledého světla z prasklé klenby na sochu jelena
beam = np.zeros((AH, AW))
for y in range(0, 256):
    t = y / 256
    cx, hw = 64 - t * 6, 4 + t * 14
    for x in range(int(cx - hw), int(cx + hw) + 1):
        if 0 <= x < AW:
            beam[y, x] = max(beam[y, x], (1 - abs(x - cx) / hw) * (0.35 + 0.5 * t))
sc.light(
    cold=[(x, y, 7, 6, 0.6) for (x, y) in fungi] + [(88, 140, 34, 20, 0.9), (58, 236, 28, 18, 0.7)] +
         [(px0 + 17, py0 + 28, 26, 36, 0.45) for (px0, py0) in PANELS] + [(88, 330, 30, 18, 0.5), (118, 262, 16, 12, 0.4)],
    dark_k=0.4, warm_k=0.3, cold_k=0.2)
L = __import__("light")
L.lighten(img, beam, 0.22, (190, 230, 210), steps=6)
sc.export("root_temple", "gen_temple.py")
print("FUNGI", fungi[:8])
