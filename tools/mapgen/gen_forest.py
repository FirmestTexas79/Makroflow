"""
Použití:  python tools/mapgen/gen_forest.py   (vyžaduje Pillow + numpy)
Výstup:   app/src/main/res/drawable-nodpi/forest.png   (1 px = 1 art pixel)
          tools/mapgen/forest_walk.png                  (bílá = průchozí; čte tools/walkmask)
          tools/mapgen/forest_debug.png                 (3x, s uzly)

Hvozd nad loukou (docs/adr/0015, přepracováno v 0036): 300 × 600 art px, na obrazovce
je vidět 150 px na šířku, kamera tedy jezdí do stran i nahoru a dolů.

Místo rovných „kapslí“ po mřížce (cikcak) vedou mezi palouky vlnité hliněné stezky
(Catmull-Rom), palouky jsou nepravidelné, stromy mají stíny, vrstvené koruny a jehličnany,
na okrajích je podrost. Místa setkání mají vlastní poznávací objekty: kapradí, kruh
muchomůrek, dutý kmen, ostružiní, starý dub s dutinou a jezírka.

NODES / EDGES jsou ZDROJ PRAVDY i pro ForestMap.kt, GATHER pro GatherLayout.kt (ForestSpots).
"""
import json, math, os
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi")
AW, AH = 300, 600
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5

NODES = {
    "vstup_z_louky": (150, 588),
    "f_a": (150, 540), "f_w1": (104, 522), "trava_1": (62, 512), "f_e1": (196, 508), "strom_briza": (238, 482),
    "f_b": (134, 462), "f_c": (158, 414), "f_mid": (150, 372), "f_w2": (102, 382), "jezirko_1": (66, 386),
    "trava_2": (212, 338),
    "f_d": (138, 304), "f_e": (152, 262), "f_w3": (110, 258), "houstina": (68, 256),
    "f_e2": (200, 256), "jezirko_2": (240, 250),
    "f_f": (142, 214), "f_g": (162, 170), "f_w4": (106, 160), "trava_3": (60, 156),
    "f_nw": (118, 118), "stary_dub": (92, 92),
    "f_n": (156, 104), "mytina": (150, 54), "f_ne": (198, 110), "strom_javor": (232, 112),
}
EDGES = [
    ("vstup_z_louky", "f_a"), ("f_a", "f_w1"), ("f_w1", "trava_1"), ("f_a", "f_e1"), ("f_e1", "strom_briza"),
    ("f_a", "f_b"), ("f_b", "f_c"), ("f_c", "f_mid"), ("f_mid", "f_w2"), ("f_w2", "jezirko_1"), ("f_mid", "trava_2"),
    ("f_mid", "f_d"), ("f_d", "f_e"), ("f_e", "f_w3"), ("f_w3", "houstina"), ("f_e", "f_e2"), ("f_e2", "jezirko_2"),
    ("f_e", "f_f"), ("f_f", "f_g"), ("f_g", "f_w4"), ("f_w4", "trava_3"), ("f_w4", "f_nw"), ("f_nw", "stary_dub"),
    ("f_g", "f_n"), ("f_n", "mytina"), ("f_n", "f_ne"), ("f_ne", "strom_javor"),
    # smyčky
    ("f_w2", "f_w3"), ("trava_2", "f_e2"),
]
ENCOUNTERS = ["trava_1", "jezirko_1", "trava_2", "houstina", "jezirko_2", "trava_3", "stary_dub"]
# Stromy ke kácení (GatherLayout.kt / ForestSpots): pata kmene, uzel stojí 12 px pod ní
GATHER = {"strom_briza": (238, 470), "strom_javor": (232, 100)}
# Hlavní hliněná stezka (vstup → mýtina) a odbočky s užší stezkou
TRAIL = ["vstup_z_louky", "f_a", "f_b", "f_c", "f_mid", "f_d", "f_e", "f_f", "f_g", "f_n", "mytina"]

GRASS = [(96, 158, 78), (120, 180, 88), (142, 198, 98), (166, 214, 112), (190, 228, 132)]
DIRT = [(128, 94, 58), (156, 118, 74), (184, 146, 96), (208, 176, 124)]
OUT = (26, 40, 26)
TRUNK = [(74, 50, 32), (104, 72, 44), (134, 96, 60)]
LEAF = {     # tmavá … světlá, obrys
    "oak":    [(34, 78, 42), (50, 104, 52), (74, 132, 62), (108, 164, 80), (150, 196, 104), (20, 44, 26)],
    "linden": [(58, 98, 38), (82, 126, 46), (110, 154, 58), (146, 184, 78), (184, 212, 108), (36, 60, 24)],
    "autumn": [(122, 70, 34), (160, 98, 44), (196, 132, 58), (224, 168, 84), (244, 204, 124), (76, 42, 20)],
    "olive":  [(92, 88, 40), (122, 116, 50), (152, 144, 64), (186, 176, 90), (214, 204, 126), (58, 54, 24)],
    "pine":   [(22, 60, 46), (32, 82, 58), (48, 108, 70), (74, 138, 86), (104, 166, 104), (14, 36, 28)],
}
WATER = [(26, 84, 132), (40, 112, 168), (70, 150, 206), (150, 206, 236), (220, 244, 252)]
FLOWERS = [(246, 150, 184), (170, 160, 246), (252, 250, 236), (252, 206, 96), (236, 96, 96)]

rng = np.random.default_rng(36)
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


N1, N2, N3 = value_noise(5, 1), value_noise(17, 2), value_noise(40, 3)


def catmull(pts, steps=10):
    """Hladká křivka přes body (koncové body se zopakují)."""
    p = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(p) - 2):
        p0, p1, p2, p3 = p[i - 1], p[i], p[i + 1], p[i + 2]
        for s in range(steps):
            t = s / steps
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                                    + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3) for k in (0, 1)))
    out.append(pts[-1])
    return out


def wiggle(a, b, seed, amp=7):
    """Stezka mezi dvěma uzly jako mírně prohnutá křivka (dva vnitřní body posunuté do strany)."""
    (ax, ay), (bx, by) = a, b
    dx, dy = bx - ax, by - ay
    L = math.hypot(dx, dy) or 1
    nx, ny = -dy / L, dx / L
    r = np.random.default_rng(seed)
    k1, k2 = r.uniform(-1, 1) * amp, r.uniform(-1, 1) * amp
    pts = [a, (ax + dx / 3 + nx * k1, ay + dy / 3 + ny * k1), (ax + 2 * dx / 3 + nx * k2, ay + 2 * dy / 3 + ny * k2), b]
    return catmull(pts, 12)


def dist_to_curve(curve):
    d = np.full((AH, AW), 999.0)
    for (x0, y0), (x1, y1) in zip(curve, curve[1:]):
        dx, dy = x1 - x0, y1 - y0
        L = dx * dx + dy * dy or 1
        t = np.clip(((xs - x0) * dx + (ys - y0) * dy) / L, 0, 1)
        d = np.minimum(d, np.hypot(xs - x0 - t * dx, ys - y0 - t * dy))
    return d


# ── 1. palouky a stezky ────────────────────────────────────────────────────
curves = {}
dist = np.full((AH, AW), 999.0)
for i, (a, b) in enumerate(EDGES):
    c = wiggle(NODES[a], NODES[b], 100 + i, amp=6 if (a in TRAIL and b in TRAIL) else 4)
    curves[(a, b)] = c
    dist = np.minimum(dist, dist_to_curve(c))
wob = (N1 - 0.5) * 3.2 + (N2 - 0.5) * 2.0
walk = dist + wob < 9.5
GLADE = {"f_a": 28, "f_mid": 36, "f_e": 24, "f_g": 26, "mytina": 26, "vstup_z_louky": 14, "f_n": 20}
for n, (x, y) in NODES.items():
    r = GLADE.get(n, 22 if n in ENCOUNTERS or n in GATHER else 15)
    ang = np.arctan2(ys - y, xs - x)
    rr = r * (1 + 0.16 * np.sin(3 * ang + x * 0.1) + 0.1 * np.sin(5 * ang + y * 0.07))
    walk |= np.hypot(xs - x, ys - y) < rr
walk[572:, 138:163] = True                     # cesta dolů z lesa na louku


def dilate(m, r):
    o = m.copy()
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            if dx * dx + dy * dy > r * r: continue
            sh = np.zeros_like(m)
            sh[max(0, dy):AH + min(0, dy), max(0, dx):AW + min(0, dx)] = m[max(0, -dy):AH + min(0, -dy), max(0, -dx):AW + min(0, -dx)]
            o |= sh
    return o


# uzavření: úzké proužky lesa mezi stezkami se zaplní paloukem (dřív z nich zbyly „vrstevnice“)
walk = ~dilate(~dilate(walk, 5), 5)

# ── 2. jezírka ─────────────────────────────────────────────────────────────
water = np.zeros((AH, AW), bool)
PONDS = {"jezirko_1": (40, 366, 20, 14), "jezirko_2": (262, 230, 18, 15)}
for (cx, cy, rx, ry) in PONDS.values():
    ang = np.arctan2(ys - cy, xs - cx)
    rr = 1 + 0.12 * np.sin(3 * ang + cx) + 0.08 * np.sin(5 * ang + cy)
    water |= ((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2 < rr * rr
    bank = ((xs - cx) / (rx + 7)) ** 2 + ((ys - cy) / (ry + 6)) ** 2 < rr * rr
    walk |= bank & dilate(walk, 6)          # břeh jen tam, kde navazuje na palouk (ne do lesa)
walk &= ~water

# vzdálenost k okraji palouku se počítá až po jezírkách (břehy jsou taky palouk)
# vzdálenost k okraji palouku (pro stín a podrost)
edge_dist = np.zeros((AH, AW))
inside = walk.copy()
for k in range(1, 7):
    er = inside.copy()
    er[1:, :] &= inside[:-1, :]; er[:-1, :] &= inside[1:, :]; er[:, 1:] &= inside[:, :-1]; er[:, :-1] &= inside[:, 1:]
    edge_dist[inside & ~er] = k
    inside = er
edge_dist[inside] = 7

# ── 3. tráva ───────────────────────────────────────────────────────────────
img = np.zeros((AH, AW, 3))
light = N1 * 0.35 + N2 * 0.4 + N3 * 0.25
v = np.clip((light * 4.2 - 0.6 + BAYER[ys % 4, xs % 4] * 0.9).astype(int), 0, 4)
for i, c in enumerate(GRASS): img[v == i] = c
# mimo palouk: hustý podrost (lesní půda s keři), stromy ho většinou zakryjí
fl = np.clip((N1 * 0.6 + N2 * 0.4) * 3.2 + BAYER[ys % 4, xs % 4] * 0.8, 0, 2).astype(int)
for i in range(3): img[~walk & (fl == i)] = LEAF["oak"][i]
# okraj palouku ve stínu stromů
for k, f in ((1, 0.62), (2, 0.72), (3, 0.84), (4, 0.93)):
    img[walk & (edge_dist == k)] *= f
# stébla a jetel
tuft = (N1 > 0.74) & ((xs * 3 + ys) % 5 == 0)
img[walk & tuft] = GRASS[4]
img[walk & (N2 > 0.8) & ((xs + ys * 2) % 7 == 0)] = GRASS[1]
# sluneční skvrny (paprsky skrz koruny)
sun = (N3 > 0.62) & (edge_dist >= 4)
img[walk & sun] = np.minimum(img[walk & sun] * 1.1 + 8, 255)


def px(x, y, c):
    x, y = int(round(x)), int(round(y))
    if 0 <= x < AW and 0 <= y < AH: img[y, x] = c


# ── 4. hliněná stezka ──────────────────────────────────────────────────────
trail_d = np.full((AH, AW), 999.0)
for a, b in zip(TRAIL, TRAIL[1:]):
    key = (a, b) if (a, b) in curves else (b, a)
    trail_d = np.minimum(trail_d, dist_to_curve(curves[key]))
side_d = np.full((AH, AW), 999.0)
for (a, b), c in curves.items():
    if not (a in TRAIL and b in TRAIL):
        side_d = np.minimum(side_d, dist_to_curve(c))
tw = 2.6 + (N1 - 0.5) * 1.6
dirt_main = walk & (trail_d < tw)
dirt_side = np.zeros_like(walk)
for m, base in ((dirt_main, 2), (dirt_side, 1)):
    shade = np.clip((N1 * 2.6 + BAYER[ys % 4, xs % 4] * 0.8).astype(int), 0, 1) + base
    for i in range(4):
        img[m & (shade == i)] = DIRT[i]
# okraj stezky
edge = walk & ~dirt_main & (trail_d < tw + 1.1)
img[edge] = img[edge] * 0.86
# kamínky na stezce
for _ in range(220):
    x, y = int(rng.integers(0, AW)), int(rng.integers(0, AH))
    if dirt_main[y, x]: px(x, y, DIRT[3] if rng.random() < 0.6 else (118, 110, 100))

# ── 5. voda ────────────────────────────────────────────────────────────────
for y, x in zip(*np.nonzero(water)):
    nb = [(y + dy, x + dx) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
    rim = any(not (0 <= a < AH and 0 <= b < AW and water[a, b]) for a, b in nb)
    top = not (y - 2 >= 0 and water[y - 2, x])
    img[y, x] = WATER[0] if rim else (WATER[1] if top else (WATER[2] if N1[y, x] > 0.55 else WATER[1]))
    if not rim and N2[y, x] > 0.72 and (x + y) % 4 == 0: img[y, x] = WATER[3]
# hliněný břeh
for y in range(AH):
    for x in range(AW):
        if water[y, x]: continue
        if any(0 <= y + dy < AH and 0 <= x + dx < AW and water[y + dy, x + dx] for dx, dy in ((0, -1), (1, 0), (-1, 0), (0, 1))):
            img[y, x] = DIRT[0] if (y > 0 and water[y - 1, x]) else DIRT[1]
# lekníny a rákos
for (cx, cy, rx, ry) in PONDS.values():
    r = np.random.default_rng(cx)
    for _ in range(5):
        a = r.uniform(0, 6.28); d = r.uniform(0.2, 0.7)
        x, y = int(cx + math.cos(a) * rx * d), int(cy + math.sin(a) * ry * d)
        for dx in (-1, 0, 1):
            px(x + dx, y, (70, 140, 64)); px(x + dx, y - 1, (96, 170, 80))
        px(x, y - 1, (246, 176, 200)) if r.random() < 0.5 else None
    for _ in range(9):
        a = r.uniform(3.4, 6.0); x = int(cx + math.cos(a) * (rx + 1)); y = int(cy + math.sin(a) * (ry + 1))
        for k in range(r.integers(4, 8)): px(x, y - k, (54, 104, 50) if k < 3 else (84, 136, 60))
        px(x, y - 7, (118, 72, 40)); px(x, y - 8, (118, 72, 40))

# ── 6. objekty setkání (poznávací znaky) ───────────────────────────────────
objects = []          # obdélníky, které blokuje mapa chůze


def block(x0, y0, x1, y1):
    objects.append([int(x0), int(y0), int(x1), int(y1)])


def fern_thicket(cx, cy):
    """Velký trs kapradí – modrozelené vějíře listů s tmavým obrysem (poznávací znak setkání)."""
    FERN = [(18, 52, 40), (34, 96, 70), (56, 138, 88), (96, 180, 110), (150, 216, 140)]
    r = np.random.default_rng(cx + cy)
    pts = {}
    fronds = sorted([(r.uniform(-2.55, -0.6), 13 + r.uniform(-2, 4)) for _ in range(15)], key=lambda f: abs(f[0] + 1.57), reverse=True)
    for ang, ln in fronds:
        for s_ in range(int(ln)):
            t = s_ / ln
            x = cx + math.cos(ang) * s_ * 1.3
            y = cy + math.sin(ang) * s_ * 1.05 + (t * t) * 7
            pts[(round(x), round(y))] = FERN[1] if t < 0.3 else FERN[2] if t < 0.65 else FERN[3]
            if 0.15 < t < 0.9:
                w = 2 if t < 0.6 else 1
                for k in range(1, w + 1):
                    pts[(round(x - math.sin(ang) * k), round(y + math.cos(ang) * 0.6 * k - k))] = FERN[3] if t > 0.4 else FERN[2]
                    pts[(round(x + math.sin(ang) * k), round(y - math.cos(ang) * 0.6 * k - k))] = FERN[4] if t > 0.45 else FERN[3]
    for (x, y) in list(pts):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in pts: px(x + dx, y + dy, FERN[0])
    for (x, y), c in pts.items(): px(x, y, c)
    for dx in range(-4, 5): px(cx + dx, cy + 1, FERN[0])
    block(cx - 14, cy - 12, cx + 14, cy + 3)


def mushroom_ring(cx, cy):
    """Kruh muchomůrek."""
    for k in range(9):
        a = k / 9 * 6.283
        x = cx + math.cos(a) * 11; y = cy + math.sin(a) * 6
        big = k % 3 == 0
        for dx in range(-2 if big else -1, 3 if big else 2):
            px(x + dx, y - 3, (214, 52, 44)); px(x + dx, y - 4 if big else y - 3, (232, 70, 58))
        px(x - 1, y - 4, (250, 246, 236)); px(x + 1, y - 3, (250, 246, 236))
        px(x, y - 2, (240, 230, 206)); px(x, y - 1, (220, 206, 176))
    # tmavší tráva uvnitř kruhu (pohádkový kruh)
    for yy in range(int(cy - 5), int(cy + 6)):
        for xx in range(int(cx - 9), int(cx + 10)):
            if ((xx - cx) / 9) ** 2 + ((yy - cy) / 5) ** 2 < 1 and 0 <= xx < AW and 0 <= yy < AH:
                img[yy, xx] = img[yy, xx] * 0.8 + np.array([0, 10, 20])
    block(cx - 8, cy - 4, cx + 8, cy + 4)


def hollow_log(cx, cy):
    """Padlý dutý kmen s mechem."""
    for x in range(int(cx - 14), int(cx + 13)):
        for y in range(int(cy - 5), int(cy + 3)):
            t = (y - (cy - 5)) / 8
            px(x, y, TRUNK[2] if t < 0.25 else (TRUNK[1] if t < 0.7 else TRUNK[0]))
        if (x * 7) % 5 == 0: px(x, cy - 5, (110, 150, 70))           # mech
        px(x, cy - 6, OUT); px(x, cy + 3, OUT)
    # čelo s dutinou
    ex = cx + 13
    for y in range(int(cy - 6), int(cy + 4)):
        for x in range(int(ex - 3), int(ex + 3)):
            d = math.hypot((x - ex) / 3, (y - cy + 1) / 4.5)
            if d < 1: px(x, y, (22, 16, 12) if d < 0.55 else (176, 138, 92))
            elif d < 1.25: px(x, y, OUT)
    # houbičky na kmeni
    for x in (cx - 8, cx + 2):
        px(x, cy - 7, (226, 190, 120)); px(x + 1, cy - 7, (226, 190, 120)); px(x, cy - 6, (190, 150, 90))
    block(cx - 15, cy - 6, cx + 16, cy + 3)


def bramble(cx, cy):
    """Ostružiní – trnitý keř s bobulemi."""
    for y in range(int(cy - 11), int(cy + 2)):
        for x in range(int(cx - 12), int(cx + 13)):
            d = math.hypot((x - cx) / 12, (y - cy + 5) / 7)
            if d < 1:
                l = -(x - cx) / 12 * 0.6 - (y - cy + 5) / 7 + N1[min(y, AH - 1), min(max(x, 0), AW - 1)] * 0.9
                px(x, y, LEAF["oak"][3] if l > 0.7 else (LEAF["oak"][2] if l > 0 else LEAF["oak"][1]))
            elif d < 1.12: px(x, y, LEAF["oak"][5])
    r = np.random.default_rng(cx * 3 + cy)
    for _ in range(14):
        x = cx + r.uniform(-9, 9); y = cy - 5 + r.uniform(-5, 4)
        px(x, y, (60, 30, 80)); px(x + 1, y, (100, 50, 120)); px(x, y - 1, (170, 110, 190))
    block(cx - 11, cy - 10, cx + 11, cy + 2)


def old_oak(cx, cy):
    """Prastarý dub s dutinou a kořeny (stojí se pod ním)."""
    # kořeny a kmen
    for y in range(int(cy - 20), int(cy + 1)):
        w = 5 + max(0, (y - (cy - 6))) * 0.9
        for x in range(int(cx - w), int(cx + w) + 1):
            t = (x - (cx - w)) / (2 * w)
            px(x, y, TRUNK[2] if t < 0.3 else (TRUNK[1] if t < 0.75 else TRUNK[0]))
        px(cx - w - 1, y, OUT); px(cx + w + 1, y, OUT)
    for y in range(int(cy - 14), int(cy - 6)):            # dutina
        for x in range(int(cx - 2), int(cx + 3)):
            if math.hypot((x - cx) / 2.6, (y - cy + 10) / 4) < 1: px(x, y, (18, 12, 10))
    px(cx, cy - 11, (250, 220, 90)); px(cx + 1, cy - 11, (250, 220, 90))      # oči v dutině
    # obrovská koruna
    draw_canopy(cx, cy - 34, 19, LEAF["oak"], seed=cx)
    block(cx - 9, cy - 20, cx + 9, cy + 1)


# ── 7. stromy ──────────────────────────────────────────────────────────────
NL = value_noise(2, 9)


def draw_canopy(cx, cy, r, pal, seed=0):
    """Koruna složená z několika shluků listí, světlo zleva nahoře, tmavý obrys."""
    rr = np.random.default_rng(abs(int(seed)) + 1)
    blobs = [(cx, cy, r)] + [(cx + rr.uniform(-0.55, 0.55) * r, cy + rr.uniform(-0.5, 0.35) * r, r * rr.uniform(0.45, 0.62)) for _ in range(4)]
    x0, x1 = int(cx - r * 1.5), int(cx + r * 1.5) + 1
    y0, y1 = int(cy - r * 1.4), int(cy + r * 1.2) + 1
    for yy in range(max(0, y0), min(AH, y1)):
        for xx in range(max(0, x0), min(AW, x1)):
            best = 9.0; lit = 0.0
            for (bx, by, br) in blobs:
                ang = math.atan2(yy - by, xx - bx)
                rim = 1 + 0.08 * math.sin(ang * 7 + bx) + 0.05 * math.sin(ang * 13 + by)
                d = math.hypot(xx - bx, (yy - by) * 1.08) / br / rim
                if d < best: best = d; lit = -((xx - bx) * 0.7 + (yy - by)) / br
            if best < 1:
                l = lit * 0.9 + (NL[yy, xx] - 0.5) * 1.1 + BAYER[yy % 4, xx % 4] * 0.45 - best * 0.35
                shade = 4 if l > 0.95 else 3 if l > 0.45 else 2 if l > -0.05 else 1 if l > -0.55 else 0
                if best > 0.9: shade = min(shade, 1)
                img[yy, xx] = pal[shade]
            elif best < 1.1:
                img[yy, xx] = pal[5]


def draw_pine(cx, cy, h, pal):
    """Jehličnan – tři patra kuželů."""
    for tier in range(3):
        top = cy - h + tier * h * 0.28
        base = top + h * 0.45
        half = 4 + tier * 2.6
        for yy in range(int(top), int(base) + 1):
            f = (yy - top) / max(1, base - top)
            w = half * f
            for xx in range(int(cx - w - 1), int(cx + w + 2)):
                if not (0 <= xx < AW and 0 <= yy < AH): continue
                d = abs(xx - cx) / max(0.5, w)
                if d <= 1:
                    l = -(xx - cx) / max(1, half) + (NL[yy, xx] - 0.5) * 0.8 - f * 0.4
                    shade = 3 if l > 0.4 else 2 if l > -0.1 else 1 if l > -0.6 else 0
                    img[yy, xx] = pal[shade]
                elif d <= 1.2 or yy == int(base):
                    img[yy, xx] = pal[5]
    for k in range(3): px(cx, cy - k, TRUNK[0]); px(cx + 1, cy - k, TRUNK[1])


def shadow(cx, cy, rx, ry):
    for yy in range(int(cy - ry), int(cy + ry) + 1):
        for xx in range(int(cx - rx), int(cx + rx) + 1):
            if 0 <= xx < AW and 0 <= yy < AH and ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 < 1:
                img[yy, xx] = img[yy, xx] * 0.72
                pre[yy, xx] = pre[yy, xx] * 0.72          # stín není překážka


# kvítí na paloucích (pod stromy se kreslí dřív)
for _ in range(700):
    x, y = int(rng.integers(2, AW - 2)), int(rng.integers(2, AH - 2))
    if walk[y, x] and not dirt_main[y, x] and edge_dist[y, x] >= 2 and all(math.hypot(x - a, y - b) > 6 for a, b in NODES.values()):
        c = FLOWERS[int(rng.integers(0, len(FLOWERS)))]
        px(x, y, c); px(x, y + 1, GRASS[0])
        if rng.random() < 0.3: px(x + 1, y, c)

# pařezy, kameny s mechem a větve
for _ in range(26):
    x, y = int(rng.integers(6, AW - 6)), int(rng.integers(10, AH - 10))
    if not (walk[y, x] and edge_dist[y, x] >= 3 and trail_d[y, x] > 5 and all(math.hypot(x - a, y - b) > 14 for a, b in NODES.values())):
        continue
    kind = rng.integers(0, 3)
    if kind == 0:        # pařez
        for dx in range(-3, 4): px(x + dx, y, TRUNK[1]); px(x + dx, y + 1, TRUNK[0])
        for dx in range(-2, 3): px(x + dx, y - 1, (200, 166, 116))
        px(x, y - 1, TRUNK[1]); px(x - 4, y + 1, OUT); px(x + 4, y + 1, OUT)
        block(x - 3, y - 1, x + 4, y + 2)
    elif kind == 1:      # kámen s mechem
        for dx in range(-3, 4):
            for dy in range(-2, 2):
                if abs(dx) + abs(dy) < 4: px(x + dx, y + dy, (150, 146, 138) if dy < 0 else (112, 108, 102))
        px(x - 1, y - 2, (110, 160, 80)); px(x, y - 2, (110, 160, 80)); px(x + 1, y - 2, (130, 180, 90))
        block(x - 3, y - 2, x + 4, y + 2)
    else:                # spadlá větev
        for dx in range(-5, 6): px(x + dx, y + dx // 3, TRUNK[1])
        px(x + 2, y - 1, TRUNK[0]); px(x + 3, y - 2, TRUNK[0])

# snímek před stromy: co se pak změní (koruny, kmeny, keře), je překážka (docs/adr/0037)
pre = img.copy()
# stromy: pata kmene mimo palouk, koruna smí přesahovat okraj (měkčí hrana lesa)
trees = []
for gy in range(-8, AH + 14, 9):
    for gx in range(-8, AW + 12, 11):
        x = gx + (5 if (gy // 9) % 2 else 0) + rng.uniform(-2.5, 2.5)
        y = gy + rng.uniform(-2.5, 2.5)
        bx, by = int(round(x)), int(round(y))
        if 0 <= bx < AW and 0 <= by < AH and (walk[by, bx] or water[by, bx]): continue
        # kmen aspoň kus od palouku (koruna smí přes okraj, ne přes stezku)
        near = False
        for ox, oy in ((0, 0), (-4, 0), (4, 0), (0, -4), (0, 3)):
            xx, yy = bx + ox, by + oy
            if 0 <= xx < AW and 0 <= yy < AH and (walk[yy, xx] or water[yy, xx]): near = True
        if near: continue
        if any(math.hypot(x - a, y - 8 - b) < 14 for a, b in NODES.values()): continue
        # koruna smí přes okraj palouku jen trochu (vnitřek palouku a stezka zůstanou vidět)
        cr = 11; ccx, ccy = int(x), int(y - 4 - cr * 0.85)
        y0c, y1c, x0c, x1c = max(0, ccy - cr), min(AH, ccy + cr), max(0, ccx - cr), min(AW, ccx + cr)
        if y1c > y0c and x1c > x0c:
            sub = edge_dist[y0c:y1c, x0c:x1c]
            if (sub >= 3).sum() > 6 or (trail_d[y0c:y1c, x0c:x1c] < 5).any(): continue
        if any(math.hypot(x - a, y - b) < 16 for a, b in GATHER.values()): continue
        trees.append((y, x))
trees.sort()
kinds = ["oak", "linden", "autumn", "olive", "pine"]
for (y, x) in trees:
    kind = kinds[int(rng.choice(5, p=[0.34, 0.26, 0.08, 0.07, 0.25]))]
    if kind == "pine":
        h = 20 + rng.uniform(-2, 4)
        shadow(x + 3, y + 1, 6, 2.5)
        draw_pine(x, y, h, LEAF["pine"])
    else:
        r = 9 + rng.uniform(-1, 2.5)
        shadow(x + 4, y + 1, r * 0.8, 3)
        for k in range(4):
            for dx in (-1, 0, 1):
                px(x + dx, y - k, TRUNK[0] if dx == 1 else TRUNK[1] if dx == 0 else TRUNK[2])
        px(x - 2, y, OUT); px(x + 2, y, OUT)
        draw_canopy(x, y - 4 - r * 0.85, r, LEAF[kind], seed=int(x * 13 + y))
        if rng.random() < 0.12:        # bobule / květy v koruně
            bc = [(220, 70, 90), (250, 240, 200), (120, 90, 220)][int(rng.integers(0, 3))]
            for _ in range(6):
                a = rng.uniform(0, 6.28); d = rng.uniform(0.2, 0.7) * r
                px(x + math.cos(a) * d, y - 4 - r * 0.85 + math.sin(a) * d, bc)

# keře v lese tam, kde nezbyla koruna (dřív tmavé fleky)
floor_cols = {tuple(float(v) for v in LEAF["oak"][i]) for i in range(3)}
for _ in range(2600):
    x, y = int(rng.integers(0, AW)), int(rng.integers(0, AH))
    if walk[y, x] or tuple(img[y, x]) not in floor_cols: continue
    pal = LEAF["oak"] if rng.random() < 0.55 else (LEAF["linden"] if rng.random() < 0.7 else LEAF["pine"])
    rr_ = rng.uniform(3.5, 6)
    for yy in range(int(y - rr_), int(y + rr_ * 0.7) + 1):
        for xx in range(int(x - rr_), int(x + rr_) + 1):
            if not (0 <= xx < AW and 0 <= yy < AH) or walk[yy, xx]: continue
            d = math.hypot((xx - x) / rr_, (yy - y) / (rr_ * 0.75))
            if d < 1:
                l = -((xx - x) * 0.7 + (yy - y)) / rr_ + (NL[yy, xx] - 0.5) * 0.9
                img[yy, xx] = pal[3] if l > 0.6 else pal[2] if l > -0.1 else pal[1]
            elif d < 1.15:
                img[yy, xx] = pal[5]

# podrost na okraji palouku (malé keříky přes hranu)
for _ in range(420):
    x, y = int(rng.integers(3, AW - 3)), int(rng.integers(3, AH - 3))
    if walk[y, x] and edge_dist[y, x] == 1 and trail_d[y, x] > 6 and side_d[y, x] > 4 \
            and all(math.hypot(x - a, y - b) > 11 for a, b in NODES.values()):
        pal = LEAF["oak"] if rng.random() < 0.6 else LEAF["linden"]
        for yy in range(y - 3, y + 2):
            for xx in range(x - 4, x + 5):
                d = math.hypot((xx - x) / 4, (yy - y + 1) / 2.6)
                if d < 1: px(xx, yy, pal[3] if yy < y - 1 and xx < x else pal[2] if d < 0.7 else pal[1])
                elif d < 1.25: px(xx, yy, pal[5])

covered = np.any(np.abs(img - pre) > 0.5, axis=2)
# objekty setkání
fern_thicket(62, 500)
mushroom_ring(212, 324)
hollow_log(66, 243)
bramble(60, 142)
# mýtina: prastarý pařez-oltář s houbami (místo pro příští příběh)
mx, my = NODES["mytina"]
for yy in range(my - 14, my - 3):
    for xx in range(mx - 9, mx + 10):
        d = math.hypot((xx - mx) / 9, (yy - my + 9) / 5)
        if d < 1: px(xx, yy, (192, 156, 108) if (xx - mx) ** 2 + ((yy - my + 9) * 1.8) ** 2 < 30 else TRUNK[1])
        elif d < 1.18: px(xx, yy, OUT)
for a in range(0, 360, 30):
    px(mx + 5 * math.cos(math.radians(a)), my - 9 + 2.6 * math.sin(math.radians(a)), TRUNK[0])
for (dx, c) in ((-11, (230, 90, 80)), (10, (230, 90, 80)), (-7, (250, 240, 220)), (6, (250, 240, 220))):
    px(mx + dx, my - 3, (240, 230, 210)); px(mx + dx, my - 4, c); px(mx + dx - 1, my - 4, c); px(mx + dx + 1, my - 4, c)
block(mx - 9, my - 14, mx + 10, my - 3)


# starý dub (přes okolní stromy – je to dominanta)
old_oak(92, 80)

# ukazatel u vstupu
sx, sy = 132, 580
for k in range(8): px(sx, sy - k, TRUNK[0]); px(sx + 1, sy - k, TRUNK[1])
for dx in range(-4, 7):
    for k in (8, 9, 10): px(sx + dx, sy - k, TRUNK[2] if k == 8 else TRUNK[1])
    px(sx + dx, sy - 11, OUT)
px(sx + 7, sy - 9, OUT)

# ── výstupy ────────────────────────────────────────────────────────────────
os.makedirs(RES, exist_ok=True)
im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
im.save(os.path.join(RES, "forest.png"), optimize=True)

walk_out = walk & ~water & ~covered
for (x0, y0, x1, y1) in objects:
    walk_out[max(0, y0):max(0, y1), max(0, x0):max(0, x1)] = False
for n, (gx, gy) in GATHER.items():                   # kmen stromu ke kácení
    walk_out[gy - 8:gy + 1, gx - 5:gx + 6] = False
Image.fromarray((walk_out * 255).astype(np.uint8), "L").save(os.path.join(HERE, "forest_walk.png"))
with open(os.path.join(HERE, "forest_objects.json"), "w") as f:
    json.dump({"objects": objects}, f)

S = 3
dbg = im.resize((AW * S, AH * S), Image.NEAREST)
dr = ImageDraw.Draw(dbg)
for a, b in EDGES:
    (ax, ay), (bx, by) = NODES[a], NODES[b]
    dr.line([(ax * S, ay * S), (bx * S, by * S)], fill=(255, 60, 60), width=1)
for n, (x, y) in NODES.items():
    dr.ellipse([x * S - 4, y * S - 4, x * S + 4, y * S + 4], outline=(255, 30, 30), width=2)
    dr.text((x * S + 6, y * S - 6), n, fill=(0, 0, 0))
dbg.save(os.path.join(HERE, "forest_debug.png"))
print("forest", im.size, len(trees), "stromů")
