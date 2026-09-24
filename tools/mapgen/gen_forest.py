"""
Použití:  python tools/mapgen/gen_forest.py   (vyžaduje Pillow + numpy)
Výstup:   app/src/main/res/drawable-nodpi/forest.png   (1 px = 1 art pixel)
          tools/mapgen/forest_debug.png                 (4x, s uzly)

Hvozd nad loukou (docs/adr/0015): bludiště palouků mezi hustými stromy – propletenější než
Starý důl, se smyčkami i slepými uličkami. Rozdíl oproti jeskyním: průchodnost dávají
PALOUKY (kapsle podél hran grafu), všechno ostatní zaroste stromy v pohledu 3/4 shora
(koruny se kreslí odzadu dopředu, takže přední stromy překrývají zadní).

Uzly (NODES / EDGES) jsou ZDROJ PRAVDY i pro ForestMap.kt – při změně upravit obojí.
Šířka 150 art px = celá šířka se vejde na obrazovku, kamera jezdí svisle.
"""
import math, os
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi")
AW, AH = 150, 520
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5

A, B, C, D = 22, 58, 94, 128          # sloupce
NODES = {
    "vstup_z_louky": (D, 508),
    "l_d1": (D, 462), "l_c1": (C, 462), "l_b1": (B, 462), "trava_1": (A, 462),
    "l_c2": (C, 420),
    "l_b3": (B, 378), "l_c3": (C, 378), "l_d3": (D, 378),
    "jezirko_1": (D, 336),
    "l_a4": (A, 336), "l_b4": (B, 336), "l_c4": (C, 336),
    "l_a5": (A, 294), "trava_2": (B, 294), "l_c5": (C, 294), "houstina": (D, 294),
    "l_a6": (A, 252), "l_b6": (B, 252), "l_c6": (C, 252), "l_d6": (D, 252),
    "trava_3": (A, 210), "l_b7": (B, 210), "l_c7": (C, 210), "l_d7": (D, 210),
    "l_b8": (B, 168), "l_c8": (C, 168),
    "l_b9": (B, 126), "l_c9": (C, 126), "jezirko_2": (D, 126),
    "l_a10": (A, 84), "l_b10": (B, 84), "l_c10": (C, 84),
    "stary_dub": (A, 42), "mytina": (C, 40),
}
EDGES = [
    ("vstup_z_louky", "l_d1"), ("l_d1", "l_c1"), ("l_c1", "l_b1"), ("l_b1", "trava_1"),
    ("l_c1", "l_c2"), ("l_c2", "l_c3"), ("l_c3", "l_b3"), ("l_c3", "l_d3"), ("l_d3", "jezirko_1"),
    ("l_b3", "l_b4"), ("l_b4", "l_a4"), ("l_b4", "l_c4"), ("l_a4", "l_a5"), ("l_a5", "trava_2"),
    ("l_c4", "l_c5"), ("l_c5", "houstina"), ("l_c5", "l_c6"),
    ("l_a5", "l_a6"), ("l_a6", "l_b6"), ("l_b6", "l_c6"), ("l_c6", "l_d6"), ("l_d6", "l_d7"),
    ("l_a6", "trava_3"), ("l_b6", "l_b7"), ("l_d7", "l_c7"), ("l_c7", "l_c8"),
    ("l_b7", "l_b8"), ("l_b8", "l_c8"), ("l_c8", "l_c9"), ("l_c9", "jezirko_2"), ("l_c9", "l_b9"),
    ("l_b9", "l_b10"), ("l_b10", "l_a10"), ("l_a10", "stary_dub"), ("l_b10", "l_c10"), ("l_c10", "mytina"),
]
ENCOUNTERS = ["trava_1", "jezirko_1", "trava_2", "houstina", "trava_3", "jezirko_2", "stary_dub"]

GRASS = [(150, 204, 110), (162, 214, 118), (174, 222, 128), (188, 230, 140)]
GRASS_DARK = (122, 178, 92)
TALL = [(46, 108, 60), (64, 138, 70), (92, 166, 82), (130, 196, 100)]
OUT = (30, 48, 30)
TREES = [   # tmavá … světlá, obrys
    [(34, 74, 44), (48, 98, 54), (70, 128, 64), (104, 160, 84), (22, 50, 30)],        # smrkově zelený
    [(60, 100, 40), (84, 128, 48), (112, 156, 60), (150, 184, 84), (38, 64, 26)],     # listnatý
    [(108, 96, 36), (140, 124, 48), (172, 152, 66), (206, 184, 96), (70, 60, 24)],    # olivový
    [(130, 86, 44), (168, 116, 60), (200, 148, 80), (226, 182, 110), (84, 52, 26)],   # podzimní
]
TRUNK = [(90, 60, 36), (120, 84, 50)]
WATER = [(40, 120, 180), (60, 150, 210), (110, 190, 232), (200, 236, 250)]
BANK = [(150, 104, 62), (116, 78, 44)]
FLOWERS = [(240, 150, 180), (160, 150, 240), (250, 250, 240), (250, 190, 90)]
BERRY = [(90, 110, 220), (220, 90, 120)]

rng = np.random.default_rng(15)
ys, xs = np.mgrid[0:AH, 0:AW]


def value_noise(sc, seed):
    r = np.random.default_rng(seed)
    g = r.random((AH // sc + 3, AW // sc + 3))
    fx, fy = xs / sc, ys / sc
    xi, yi = fx.astype(int), fy.astype(int)
    tx, ty = fx - xi, fy - yi
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a, b, c, d = g[yi, xi], g[yi, xi + 1], g[yi + 1, xi], g[yi + 1, xi + 1]
    return a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty


# ── průchodné palouky ──
walk = np.zeros((AH, AW), bool)
dist = np.full((AH, AW), 99.0)
for a, b in EDGES:
    (ax, ay), (bx, by) = NODES[a], NODES[b]
    dx, dy = bx - ax, by - ay; L = dx * dx + dy * dy or 1
    t = np.clip(((xs - ax) * dx + (ys - ay) * dy) / L, 0, 1)
    d = np.hypot(xs - ax - t * dx, ys - ay - t * dy)
    dist = np.minimum(dist, d)
wob = (value_noise(6, 3) - 0.5) * 3
walk = dist + wob * 0.6 < 7.5
for n, (x, y) in NODES.items():
    walk |= np.hypot(xs - x, ys - y) < (14 if n in ("mytina", "stary_dub") else 9.5)
walk[500:, D - 8:D + 9] = True                         # cesta z louky

# jezírka vedle uzlů setkání u vody (mimo palouk)
water = np.zeros((AH, AW), bool)
for (cx, cy, rx, ry) in ((140, 330, 14, 12), (140, 118, 13, 14), (104, 196, 10, 8)):
    ang = np.arctan2(ys - cy, xs - cx)
    rr = 1 + 0.12 * np.sin(3 * ang + cx) + 0.08 * np.sin(5 * ang)
    water |= ((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2 < rr * rr
water &= ~walk

# ── 1. tráva ──
img = np.zeros((AH, AW, 3))
n1, n2 = value_noise(5, 1), value_noise(14, 2)
v = np.clip(((n1 * 0.4 + n2 * 0.6) * 3.4 + BAYER[ys % 4, xs % 4] * 0.9).astype(int), 0, 3)
for i, c in enumerate(GRASS): img[v == i] = c
img[(n1 > 0.8) & ((xs + ys) % 3 == 0)] = GRASS_DARK            # stébla


def px(x, y, c):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < AW and 0 <= y < AH: img[y, x] = c


# ── 2. voda s hliněným břehem ──
for y, x in zip(*np.nonzero(water)):
    edge = any(not (0 <= y + dy < AH and 0 <= x + dx < AW and water[y + dy, x + dx]) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    img[y, x] = WATER[0] if edge else (WATER[2] if n1[y, x] > 0.7 else WATER[1])
    if not edge and n1[y, x] > 0.88 and (x + y) % 3 == 0: img[y, x] = WATER[3]
for y in range(AH):
    for x in range(AW):
        if water[y, x]: continue
        near = [water[y + dy, x + dx] for dx, dy in ((0, -1), (0, -2), (1, 0), (-1, 0), (0, 1))
                if 0 <= y + dy < AH and 0 <= x + dx < AW]
        if any(near): img[y, x] = BANK[0] if (0 <= y - 1 and water[y - 1, x]) else BANK[1]

# ── 3. vysoká tráva (místa setkání) ──
def tall_grass(cx, cy, rx=13, ry=8, count=26):
    r = np.random.default_rng(cx * 17 + cy)
    for _ in range(count):
        a = r.uniform(0, 6.283); d = math.sqrt(r.uniform(0, 1))
        x, y = int(cx + math.cos(a) * d * rx), int(cy + math.sin(a) * d * ry)
        if not (0 <= x < AW and 0 <= y < AH) or water[y, x]: continue
        # trs: tři listy do V
        for k in range(4):
            px(x, y - k, TALL[1] if k < 2 else TALL[2])
            if k >= 1: px(x - (k + 1) // 2, y - k, TALL[0] if k < 3 else TALL[2]); px(x + (k + 1) // 2, y - k, TALL[1])
        px(x, y - 4, TALL[3]); px(x, y + 1, OUT)

for n in ENCOUNTERS:
    if n.startswith("jezirko"): tall_grass(*NODES[n], rx=9, ry=6, count=14)
    else: tall_grass(*NODES[n])
# pár trsů jen tak pro okrasu
for _ in range(40):
    x, y = int(rng.integers(4, AW - 4)), int(rng.integers(20, AH - 10))
    if walk[y, x] and dist[y, x] > 5 and all(math.hypot(x - a, y - b) > 14 for a, b in NODES.values()):
        tall_grass(x, y, 4, 3, 4)

# ── 4. kvítí, pařezy, kamínky na paloucích ──
for _ in range(260):
    x, y = int(rng.integers(2, AW - 2)), int(rng.integers(2, AH - 2))
    if not walk[y, x] or water[y, x]: continue
    c = FLOWERS[int(rng.integers(0, len(FLOWERS)))]
    px(x, y, c); px(x + 1, y + 1, c); px(x, y + 1, GRASS_DARK)
for (x, y) in ((40, 470), (110, 250), (74, 176), (14, 300), (110, 90), (140, 440)):
    if walk[y, x]:
        for dx in range(-2, 3): px(x + dx, y, TRUNK[1]); px(x + dx, y + 1, TRUNK[0])
        for dx in range(-1, 2): px(x + dx, y - 1, (190, 160, 110))
        px(x - 3, y + 1, OUT); px(x + 3, y + 1, OUT)

# ukazatel u vstupu
sx, sy = D - 14, 494
for k in range(7): px(sx, sy - k, TRUNK[0])
for dx in range(-3, 5):
    for k in (7, 8): px(sx + dx, sy - k, TRUNK[1])
    px(sx + dx, sy - 9, OUT)

# ── 5. stromy (odzadu dopředu) ──
# Pata kmene musí být kus od palouku, jinak koruna zakryje cestu (dilatace masky o 5 px)
near_walk = walk.copy()
for r in range(1, 4):
    near_walk[r:, :] |= walk[:-r, :]; near_walk[:-r, :] |= walk[r:, :]
    near_walk[:, r:] |= walk[:, :-r]; near_walk[:, :-r] |= walk[:, r:]
trees = []
for gy in range(-6, AH + 8, 7):
    for gx in range(-6, AW + 8, 9):
        x = gx + (4 if (gy // 7) % 2 else 0) + rng.uniform(-1.5, 1.5)
        y = gy + rng.uniform(-2, 2)
        bx, by = int(x), int(y + 6)                       # pata kmene
        ok = True
        for ox, oy in ((0, 0), (-4, 0), (4, 0), (0, -3)):
            xx, yy = bx + ox, by + oy
            if 0 <= xx < AW and 0 <= yy < AH and (near_walk[yy, xx] or water[yy, xx]): ok = False
        # koruna nesmí přes uzel (postava musí být vidět)
        if ok and any(math.hypot(x - a, y - b) < 9 for a, b in NODES.values()): ok = False
        if ok: trees.append((y, x))
trees.sort()
noise_leaf = value_noise(2, 9)
for (y, x) in trees:
    kind = rng.choice(4, p=[0.34, 0.26, 0.16, 0.24])
    pal = TREES[kind]
    r = 7 + rng.uniform(-0.5, 1.0)
    cx, cy = x, y
    berries = rng.random() < 0.14
    for yy in range(int(cy - r - 1), int(cy + r + 5)):
        for xx in range(int(cx - r - 1), int(cx + r + 2)):
            if not (0 <= xx < AW and 0 <= yy < AH): continue
            ddx, ddy = (xx - cx) / r, (yy - cy) / (r * 0.95)
            # zubatý okraj koruny (listy)
            ang = math.atan2(ddy, ddx)
            rim = 1 + 0.07 * math.sin(ang * 7 + cx) + 0.05 * math.sin(ang * 11 + cy)
            d = math.hypot(ddx, ddy)
            if d < rim:
                light = -(ddx * 0.7 + ddy) + noise_leaf[yy, xx] * 0.8 + BAYER[yy % 4, xx % 4] * 0.5
                shade = 3 if light > 0.75 else (2 if light > 0.05 else (1 if light > -0.6 else 0))
                if d > rim - 0.12: shade = min(shade, 1)
                img[yy, xx] = pal[shade]
                # shluky listů – drobné tmavé tečky
                if noise_leaf[yy, xx] > 0.86 and shade > 0: img[yy, xx] = pal[shade - 1]
            elif d < rim + 0.13:
                img[yy, xx] = pal[4]
    # kmen pod korunou
    for k in range(3):
        for dx in (-1, 0, 1):
            px(cx + dx, cy + r * 0.9 + k, TRUNK[0] if dx == 1 else TRUNK[1])
    px(cx - 2, cy + r * 0.9 + 3, OUT); px(cx + 2, cy + r * 0.9 + 3, OUT)
    if berries:
        bc = BERRY[int(rng.integers(0, 2))]
        for _ in range(7):
            a = rng.uniform(0, 6.283); d = rng.uniform(0.2, 0.75) * r
            px(cx + math.cos(a) * d, cy + math.sin(a) * d, bc)

# stín korun na trávě u palouků (jemný tmavší lem)
shade_mask = np.zeros((AH, AW), bool)
for y in range(1, AH):
    for x in range(AW):
        if walk[y, x] and not water[y, x] and tuple(img[y - 1, x]) in [tuple(p[4]) for p in TREES]:
            shade_mask[y, x] = True
img[shade_mask] = img[shade_mask] * 0.8

# mýtina na konci: prastarý pařez-oltář s houbami (místo pro příští příběh)
mx, my = NODES["mytina"]
for yy in range(my - 12, my - 3):
    for xx in range(mx - 7, mx + 8):
        d = math.hypot((xx - mx) / 7, (yy - my + 8) / 4)
        if d < 1: px(xx, yy, (176, 140, 96) if (xx - mx) ** 2 + (yy - my + 8) ** 2 * 3 < 20 else TRUNK[1])
        elif d < 1.2: px(xx, yy, OUT)
for a in range(0, 360, 40):
    px(mx + 4 * math.cos(math.radians(a)), my - 8 + 2 * math.sin(math.radians(a)), TRUNK[0])
for (dx, c) in ((-9, (230, 90, 80)), (8, (230, 90, 80)), (-6, (250, 240, 220))):
    px(mx + dx, my - 3, (240, 230, 210)); px(mx + dx, my - 4, c); px(mx + dx - 1, my - 4, c); px(mx + dx + 1, my - 4, c)

os.makedirs(RES, exist_ok=True)
im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
im.save(os.path.join(RES, "forest.png"), optimize=True)
dbg = im.resize((AW * 4, AH * 4), Image.NEAREST)
dr = ImageDraw.Draw(dbg)
for a, b in EDGES:
    (ax, ay), (bx, by) = NODES[a], NODES[b]
    dr.line([(ax * 4, ay * 4), (bx * 4, by * 4)], fill=(255, 60, 60), width=2)
for n, (x, y) in NODES.items():
    dr.ellipse([x * 4 - 6, y * 4 - 6, x * 4 + 6, y * 4 + 6], outline=(255, 30, 30), width=3)
    dr.text((x * 4 + 8, y * 4 - 6), n, fill=(0, 0, 0))
dbg.save(os.path.join(HERE, "forest_debug.png"))
print("forest", im.size, len(trees), "stromů")
