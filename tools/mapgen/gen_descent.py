"""
Použití:  python tools/mapgen/gen_descent.py
Výstup:   app/src/main/res/drawable-nodpi/tower_descent.png

Pozadí cutscény „Sjezd do Hlubiny“ (DescentCutsceneView, docs/adr/0104): šachta výtahu Věže zepředu (176 × 760).
Nahoře cihly přízemí, pak beton patra −1 se štěrbinou (Hunfreyho lampa), pak čedič, do kterého prorůstají bledé
kořeny. Rudé nitě Řádu jedou po stěnách dolů. Pod y = SHAFT_END šachta končí ve stropě obří jeskyně: kořeny se
sbíhají do kmene Spící (Lumira) a pod ním se v mlze rozevírá obrácená koruna s modrými světly. Na kmeni kopí.
Obrázek je schválně tmavý – šachtu osvětluje až lampa kabiny (kreslí ji cutscéna).
Na konci vypíše souřadnice světel na konečcích větví a kopí → DescentCutsceneView (TIPS, SPEAR).
"""
import math, os, sys
import numpy as np
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import light as L, figures as F  # noqa: E402
from pix import c  # noqa: E402
from hideout_parts import _vn, _h  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 760
SHAFT_END = 400
RAIL_L, RAIL_R = 64, 112
BARK = [c(0x2A2824), c(0x4A4640), c(0x75706A), c(0xA29C92), c(0xCCC6BA), c(0xEEE8DC)]
BRICK = [c(0x141A2C), c(0x1E2840), c(0x2A3656), c(0x38466A)]
CONC = [c(0x1C1C20), c(0x2A2A30), c(0x3A3A42), c(0x4C4C56)]
BASALT = [c(0x0A0A0E), c(0x121218), c(0x1C1C24), c(0x282832), c(0x363642)]
STEEL = F.STEEL
GOLD = F.GOLD
BAYER = F.BAYER

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
depth = np.zeros((AH, AW))
rng = np.random.default_rng(213)


def px(x, y, col):
    if 0 <= x < AW and 0 <= y < AH:
        img[y, x, :3] = col[:3]


def pick(ramp, v, x, y):
    f = max(0.0, min(0.999, v)) * (len(ramp) - 1)
    i = int(f)
    return ramp[i + 1] if (f - i) > BAYER[y % 4, x % 4] and i + 1 < len(ramp) else ramp[i]


# ── šachta: zadní stěna po pásech ───────────────────────────────────────────
for y in range(SHAFT_END):
    for x in range(AW):
        side = abs(x - 88) / 88                                   # okraje šachty tmavší (boční stěny)
        if y < 120:                                               # cihly přízemí
            row = y // 6
            bx = (x + (row % 2) * 6) % 12
            mortar = y % 6 == 5 or bx == 11
            v = 0.15 if mortar else 0.45 + (_h(x // 12 + row * 7, row, 4) % 100) / 400 + (_vn(x, y, 2, 4) - 0.5) * 0.3
            col = pick(BRICK, v - side * 0.35, x, y)
        elif y < 200:                                             # beton patra −1: panely s bedněním
            seam = y % 26 == 0 or (x + 6) % 44 == 0
            v = 0.2 if seam else 0.5 + (_vn(x, y, 3, 6) - 0.5) * 0.5 - ((_h(x, y, 5) % 23 == 0) * 0.3)
            col = pick(CONC, v - side * 0.35, x, y)
        else:                                                     # čedič: svislé sloupce, mokré pruhy
            colw = 9 + (_h(x // 9, 0, 6) % 4)
            edge = (x % colw) in (0, colw - 1)
            wet = _vn(x * 3, y * 0.25, 7, 6) > 0.7
            v = 0.15 if edge else 0.45 + (_vn(x, y, 8, 5) - 0.5) * 0.45 + (0.2 if wet else 0)
            col = pick(BASALT, v - side * 0.4 - (y - 200) / 900, x, y)
        img[y, x, :3] = col[:3]

# přechodové lišty mezi pásy
for yb in (120, 200):
    for x in range(AW):
        for k in range(3):
            px(x, yb - 1 + k, STEEL[1] if k != 1 else STEEL[2])

# vodicí kolejnice kabiny s konzolami
for x0 in (RAIL_L - 3, RAIL_R):
    for y in range(SHAFT_END + 4):
        for k in range(3):
            px(x0 + k, y, STEEL[[1, 3, 2][k]] if y < 200 else STEEL[[0, 2, 1][k]])
        if y % 32 == 8:
            for k in range(-3, 6):
                px(x0 + k, y, STEEL[3]); px(x0 + k, y + 1, STEEL[1])

# šachtové dveře přízemí (0) a patra −1 – zavřená křídla, mosazný štítek s číslem
def shaft_door(y0, label_rows, glow=False):
    for y in range(y0, y0 + 50):
        for x in range(RAIL_L + 2, RAIL_R - 2):
            if y in (y0, y0 + 49) or x in (RAIL_L + 2, RAIL_R - 3):
                px(x, y, STEEL[3])
            elif x == 88:
                px(x, y, STEEL[0])
            else:
                px(x, y, pick(STEEL, 0.25 + 0.15 * ((x // 6) % 2), x, y))
    for (dy, row) in enumerate(label_rows):                       # štítek
        for (dx, ch) in enumerate(row):
            px(80 + dx, y0 - 8 + dy, GOLD[3] if ch == "#" else GOLD[0])


shaft_door(34, ["################", "#..............#", "#......##......#", "#.....#..#.....#", "#.....#..#.....#",
                "#......##......#", "################"])
shaft_door(138, ["################", "#..............#", "#.........#....#", "#..####..##....#", "#.........#....#",
                 "#........###...#", "################"])
# štěrbina vedle dveří −1: mřížka a za ní teplá lampa (Hunfreyho skrýš)
for y in range(158, 168):
    for x in range(124, 150):
        px(x, y, c(0x6A4A20) if (x % 3) else STEEL[1])
for y in range(156, 170):
    for x in (123, 150):
        px(x, y, STEEL[3])
for y in range(154, 172):
    for x in range(122, 152):
        if y in (154, 155) or y in (170, 171):
            px(x, y, STEEL[2])
# lampa za mřížkou
for (x, y) in ((136, 162), (137, 162), (136, 163), (137, 163)):
    px(x, y, c(0xFFE8A0))
L_slot = np.zeros((AH, AW))
for y in range(146, 190):
    for x in range(110, 170):
        L_slot[y, x] = max(0, 1 - math.hypot((x - 137) / 26, (y - 163) / 16))
L.lighten(img, L_slot, 0.35, (255, 190, 110), steps=5)

# šablonou nastříkané −1 na beton vlevo
for (dy, row) in enumerate([".......#.", "......##.", "#####..#.", ".......#.", "......###"]):
    for (dx, ch) in enumerate(row):
        if ch == "#":
            for k in range(2):
                px(20 + dx * 2 + k, 150 + dy * 2, c(0x8A8060)); px(20 + dx * 2 + k, 151 + dy * 2, c(0x8A8060))


# ── kořeny ──────────────────────────────────────────────────────────────────
def shade_bark(v, x, y):
    return pick(BARK, v, x, y)


def fade(col, d, tgt=(10, 16, 34)):
    t = np.array(tgt, float)
    return np.clip(col[:3].astype(float) * (1 - d) + t * d, 0, 255).astype(np.uint8)


def limb(pts, w0, w1, d0, d1, tgt=(10, 16, 34)):
    n = len(pts)
    for i in range(n - 1):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        t = i / (n - 1)
        w = w0 + (w1 - w0) * t
        d = d0 + (d1 - d0) * t
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln
        steps = max(1, int(ln * 1.5))
        for s_ in range(steps):
            cx, cy = x0 + dx * s_ / steps, y0 + dy * s_ / steps
            for k in np.linspace(-w / 2, w / 2, max(2, int(w * 2))):
                X, Y = int(round(cx + nx * k)), int(round(cy + ny * k))
                if not (0 <= X < AW and 0 <= Y < AH):
                    continue
                if depth[Y, X] and depth[Y, X] < d - 0.02:
                    continue
                u = k / (w / 2 + 0.01)
                v = 0.6 - 0.36 * u * (1 if nx >= 0 else -1) + 0.1 * math.cos(u * math.pi)
                if abs(u) > 0.82:
                    v = 0.08
                if _h(int(cx * 0.7), int(cy * 0.5), 3) % 11 == 0 and abs(u) < 0.5:
                    v -= 0.3
                img[Y, X, :3] = fade(shade_bark(v, X, Y), d, tgt)
                depth[Y, X] = d


def bezier(p0, p1, p2, n=40):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in np.linspace(0, 1, n)]


def wall_root(x, y, ang, ln, w, lvl=0):
    """Kořen prorůstající zdí šachty: klikatí se dolů, větví se, tenčí."""
    if lvl > 3 or ln < 6 or w < 0.8:
        return
    pts = []
    a = ang
    for _ in range(10):
        pts.append((x, y))
        a += rng.uniform(-0.25, 0.25)
        a = min(max(a, 0.5), math.pi - 0.5)                        # roste převážně dolů
        x += math.cos(a) * ln / 9; y += math.sin(a) * ln / 9
    limb(pts, w, max(0.8, w * 0.6), 0.5, 0.55, (6, 6, 10))
    for s_ in (-1, 1):
        if rng.random() < 0.6:
            wall_root(x, y, a + s_ * rng.uniform(0.3, 0.7), ln * 0.7, w * 0.62, lvl + 1)


# pukliny v čediči, z nich kořeny – čím níž, tím víc a silnější
for i in range(34):
    y = int(212 + (i / 34) ** 0.8 * 180)
    x = int(rng.choice([rng.uniform(4, RAIL_L - 8), rng.uniform(RAIL_R + 8, AW - 4)]))
    w = 2 + (y - 212) / 180 * 5
    for k in range(-3, 4):                                         # puklina
        px(x + k, y + (k * k) // 4, BASALT[0])
    wall_root(x, y, math.pi / 2 + rng.uniform(-0.5, 0.5), 26 + (y - 212) / 180 * 50, w)

# rudé nitě Řádu po stěnách šachty (dvě vlevo, dvě vpravo)
THREAD = c(0xB0262A)
for x0 in (12, 50, 157, 168):
    for y in range(0, SHAFT_END):
        x = int(x0 + math.sin(y / 37 + x0) * 2.5)
        if (x + y) % 3:
            px(x, y, fade(THREAD, 0.35 + 0.3 * (abs(x - 88) / 88)))

# ── jeskyně pod šachtou ─────────────────────────────────────────────────────
VOID_TOP = np.array([12, 16, 30], float); VOID_BOT = np.array([2, 3, 8], float)
for y in range(SHAFT_END, AH):
    k = (y - SHAFT_END) / (AH - SHAFT_END)
    for x in range(AW):
        col = VOID_TOP * (1 - k) + VOID_BOT * k
        m = _vn(x, y * 1.6, 3, 14) * 0.7 + _vn(x, y, 5, 5) * 0.3
        if m > 0.58:
            col = col + np.array([18, 24, 40]) * (m - 0.58) * 2.2
        img[y, x, :3] = np.clip(col, 0, 255).astype(np.uint8)

TX, TY0, TY1 = 88, 560, 624                                         # kmen Spící
tips = []


def branch(x, y, ang, ln, w, d, lvl):
    if lvl > 4 or ln < 5 or y > AH + 10:
        return
    pts = []
    a = ang
    for _ in range(10):
        pts.append((x, y))
        a += rng.uniform(-0.12, 0.12)
        x += math.cos(a) * ln / 9; y += math.sin(a) * ln / 9
    limb(pts, w, max(1.0, w * 0.55), d, min(0.95, d + 0.1))
    tips.append((x, y, d))
    for s_ in (-1, 1):
        if rng.random() < 0.92:
            branch(x, y, ang + s_ * rng.uniform(0.3, 0.6), ln * rng.uniform(0.62, 0.78), max(1.0, w * 0.6), min(0.95, d + 0.1), lvl + 1)


for ang in (math.pi / 2 - 1.2, math.pi / 2 - 0.6, math.pi / 2, math.pi / 2 + 0.6, math.pi / 2 + 1.2):
    branch(TX + math.cos(ang) * 6, TY1, ang, 62, 10, 0.45, 0)
limb([(TX, TY0 + i * (TY1 - TY0) / 14) for i in range(15)], 26, 18, 0.3, 0.42)

# kořeny ze stropu jeskyně: nepravidelné, vlnité, vzdálenější tmavší a modřejší; některé visí naprázdno
def wavy(p0, p1, p2, amp, n=48):
    pts = bezier(p0, p1, p2, n)
    ph = rng.uniform(0, 6)
    return [(x + math.sin(i / n * math.pi * 3 + ph) * amp * math.sin(i / n * math.pi), y) for i, (x, y) in enumerate(pts)]


roots = []
for i in range(16):
    sx = -10 + i * 12.9 + rng.uniform(-5, 5)
    d = rng.uniform(0.12, 0.55)
    w = 4 + (1 - d) * 9 + rng.uniform(-1.5, 1.5)
    roots.append((d, sx, w))
for (d, sx, w) in sorted(roots, key=lambda r: -r[0]):                # vzdálené první
    mx = sx + (88 - sx) * rng.uniform(0.15, 0.45) + rng.uniform(-14, 14)
    my = rng.uniform(450, 520)
    limb(wavy((sx, SHAFT_END + 8), (mx, my), (TX + (sx - 88) * 0.09, TY0 + 8), rng.uniform(2, 6)), w, 6.5, d, 0.3)
for i in range(9):                                                   # visící kořínky, nedosáhnou na kmen
    sx = rng.uniform(4, AW - 4)
    if abs(sx - 88) < 34:
        continue
    ln = rng.uniform(30, 90)
    d = rng.uniform(0.3, 0.6)
    limb(wavy((sx, SHAFT_END + 10), (sx + rng.uniform(-10, 10), SHAFT_END + ln / 2), (sx + rng.uniform(-16, 16), SHAFT_END + ln), 3),
         rng.uniform(2.5, 4.5), 0.8, d, d + 0.1)

# strop jeskyně: zubatý čedič s krápníky kolem ústí šachty
for x in range(AW):
    edge = SHAFT_END + 6 + int(_vn(x, 0, 9, 9) * 20) + (_h(x, 1, 2) % 3)
    if (_h(x // 5, 3, 2) % 6) == 0:
        edge += 6 + (_h(x // 5, 4, 2) % 10) - abs(x % 5 - 2) * 3      # krápník
    if abs(x - 88) < 30:
        edge = SHAFT_END + 3 + (_h(x, 2, 2) % 2)                       # ústí šachty
    for y in range(SHAFT_END, edge):
        if depth[y, x] == 0 or abs(x - 88) >= 30:
            img[y, x, :3] = pick(BASALT, 0.42 - (y - SHAFT_END) / (edge - SHAFT_END + 1) * 0.35, x, y)[:3]
    if abs(x - 88) >= 30:
        px(x, edge, BASALT[0])

# rudé nitě: ze šachty dolů ke kmeni
for (i, x0) in enumerate((12, 50, 157, 168)):
    for t in np.linspace(0, 1, 320):
        x = x0 + (TX + (i - 1.5) * 3 - x0) * t
        y = SHAFT_END + (TY0 + 12 - SHAFT_END) * t + math.sin(t * math.pi) * 14
        X, Y = int(x), int(y)
        if 0 <= X < AW and 0 <= Y < AH and (X + Y) % 3:
            img[Y, X, :3] = fade(THREAD, 0.25 + 0.25 * t)

# kopí v kmeni (Akt III)
SPEAR = (80, 598)
for i in range(12):
    px(SPEAR[0] + i // 2, SPEAR[1] - i, (190, 196, 210) if i < 3 else (90, 92, 104))
px(SPEAR[0] - 1, SPEAR[1] + 1, (255, 255, 255))

# bioluminiscence na konečcích větví
glow = np.zeros((AH, AW))
lit = []
for (x, y, d) in tips:
    if 0 <= int(x) < AW and 0 <= int(y) < AH and rng.random() < 0.6:
        px(int(x), int(y), (150, 210, 255) if d < 0.8 else (90, 140, 200))
        lit.append((int(x), int(y)))
        for yy in range(int(y) - 6, int(y) + 7):
            for xx in range(int(x) - 6, int(x) + 7):
                if 0 <= xx < AW and 0 <= yy < AH:
                    glow[yy, xx] = max(glow[yy, xx], max(0, 1 - math.hypot(xx - x, yy - y) / 6) * 0.6)
for _ in range(90):
    x, y = int(rng.integers(0, AW)), int(rng.integers(SHAFT_END + 20, AH))
    px(x, y, (120, 170, 230) if rng.random() < 0.5 else (70, 100, 150))
L.lighten(img, glow, 0.3, (130, 200, 255), steps=6)

# mlha v hloubce
for y in range(600, AH):
    for x in range(AW):
        m = _vn(x + 40, y * 2.2, 21, 11)
        k = (y - 600) / (AH - 600)
        if m > 0.55 - k * 0.15:
            a = min(0.45, (m - 0.55 + k * 0.15) * 1.6) * (0.5 + 0.5 * k)
            if BAYER[y % 4, x % 4] < a * 2:
                img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([40, 54, 84]) * a, 0, 255).astype(np.uint8)

# celé ztmavit: šachtu osvětlí až lampa kabiny
dark = np.zeros((AH, AW)); dark[:SHAFT_END] = 1
for y in range(SHAFT_END - 30, SHAFT_END):
    dark[y] = (SHAFT_END - y) / 30
L.darken(img, dark, 0.45, (2, 2, 6), dither=True, steps=6)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_descent.png"))
lit.sort(key=lambda p: p[1])
print("TIPS", ", ".join(f"{x}f, {y}f" for (x, y) in lit[::max(1, len(lit) // 18)][:18]))
print("SPEAR", SPEAR)
