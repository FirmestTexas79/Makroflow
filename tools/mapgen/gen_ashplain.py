"""
Použití:  python tools/mapgen/gen_ashplain.py   (vyžaduje Pillow + numpy)
Výstup:   app/src/main/res/drawable-nodpi/ash_plain.png   (1 px = 1 art pixel)
          app/src/main/assets/walk/ash_plain.txt           (mapa chůze, stejný formát jako gen_walkmask)
          tools/mapgen/ash_plain_debug.png                 (4x, s uzly a mapou chůze)

Popelavá pláň (Akt II, issue #26) – první kraj za Branou světů. 160 × 440 art px, kamera jezdí
svisle. Shora dolů:
  * skalní práh se zbořeným obloukem: druhá strana Brány světů, ze které hráč sestoupil,
  * stará dlážděná cesta zavátá popelem, lucerny, popelové duny,
  * spálený háj s rudými houbami (hniloba z Hvozdu se sem nedostala – tohle je jiný oheň),
  * vyhaslé ohniště, rozpadlé domy, vypálené pole se strašákem, mezník,
  * dole bílé hradby Ozvěny s bránou – čisté, jako by se jich popel bál.

NODES / EDGES jsou ZDROJ PRAVDY i pro AshPlain.kt (MAP).
"""
import math, os
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
WALK = os.path.join(ROOT, "app", "src", "main", "assets", "walk")
AW, AH = 160, 440
CELL = 2
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5
rng = np.random.default_rng(4848)

# ── Uzly a hrany (zdroj pravdy pro AshPlain.kt) ──
NODES = {
    "vstup_z_brany": (80, 40),
    "c1": (66, 66), "duny": (120, 82), "c2": (86, 104), "cedule": (48, 116),
    "c3": (70, 148), "hajek": (122, 168), "c4": (96, 198), "ohniste": (40, 212),
    "c5": (70, 244), "sutiny": (116, 268), "c6": (86, 298), "pole": (38, 318),
    "c7": (66, 348), "meznik": (112, 358), "c8": (80, 384), "brana_mesta": (80, 404),
}
EDGES = [
    ("vstup_z_brany", "c1"), ("c1", "c2"), ("c2", "duny"), ("c2", "cedule"), ("c2", "c3"),
    ("c3", "c4"), ("c4", "hajek"), ("c4", "ohniste"), ("c4", "c5"), ("c5", "c6"),
    ("c6", "sutiny"), ("c6", "pole"), ("c6", "c7"), ("c7", "meznik"), ("c7", "c8"),
    ("c8", "brana_mesta"),
]
TRAIL = ["vstup_z_brany", "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "brana_mesta"]

img = np.zeros((AH, AW, 3), dtype=np.float64)
blocks = np.zeros((AH, AW), dtype=bool)
yy, xx = np.mgrid[0:AH, 0:AW]


def rgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=np.float64)


def mix(a, b, t):
    return a + (b - a) * np.clip(t, 0, 1)


def put(x, y, c, block=False):
    x, y = int(x), int(y)
    if 0 <= x < AW and 0 <= y < AH:
        img[y, x] = c
        if block:
            blocks[y, x] = True


def noise2(x, y, s):
    xi, yi = int(math.floor(x)), int(math.floor(y))
    fx, fy = x - xi, y - yi
    def h(a, b):
        v = (a * 374761393 + b * 668265263 + s * 1442695041) & 0xFFFFFFFF
        v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
        return ((v ^ (v >> 16)) & 0xFFFFFF) / 16777216.0
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b, c, d = h(xi, yi), h(xi + 1, yi), h(xi, yi + 1), h(xi + 1, yi + 1)
    return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy


def fbm(x, y, s, oct=3):
    t, amp, f, norm = 0.0, 0.5, 1.0, 0.0
    for o in range(oct):
        t += amp * noise2(x * f, y * f, s + o * 17)
        norm += amp; amp *= 0.5; f *= 2.03
    return t / norm


def cr(t, p0, p1, p2, p3):
    t2, t3 = t * t, t * t * t
    return tuple(0.5 * ((2 * p1[i]) + (-p0[i] + p2[i]) * t + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t2 +
                        (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t3) for i in range(2))


def ellipse(cx, cy, rx, ry):
    return ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1


# ═════════════════════════════════════════════════════════════════════════════
# 1) PŮDA – šedobéžový popel, tmavší skvrny, vlnky od větru
# ═════════════════════════════════════════════════════════════════════════════
ASH_LO, ASH_HI = rgb(0x6E6660), rgb(0xB8AEA2)
for y in range(AH):
    for x in range(AW):
        n = fbm(x * 0.06, y * 0.06, 3)
        ripple = 0.5 + 0.5 * math.sin((x * 0.35 + y * 0.9) + fbm(x * 0.1, y * 0.1, 9) * 6)
        t = 0.25 + n * 0.55 + ripple * 0.08 + BAYER[y % 4, x % 4] * 0.08
        img[y, x] = mix(ASH_LO, ASH_HI, t)
        sc = fbm(x * 0.03 + 40, y * 0.03, 77)
        if sc < 0.38:
            img[y, x] = mix(img[y, x], rgb(0x3E3638), (0.38 - sc) * 3.2)   # spálené skvrny
        # teplejší nádech dole u města, chladnější nahoře
        img[y, x] = mix(img[y, x], rgb(0xC8B8A0), (y / AH) * 0.18)

# okraje mapy: rozpraskaná černá zem a zuhelnatělé pahorky (neprůchozí)
for y in range(AH):
    for x in range(AW):
        edge = min(x, AW - 1 - x)
        lim = 9 + fbm(y * 0.08, 1 if x < AW // 2 else 2, 21) * 9
        if edge < lim:
            d = edge / lim
            img[y, x] = mix(rgb(0x2A2426), img[y, x], d * d)
            if d < 0.75:
                blocks[y, x] = True

# ═════════════════════════════════════════════════════════════════════════════
# 2) CESTA – staré dláždění zaváté popelem
# ═════════════════════════════════════════════════════════════════════════════
pts = [NODES[n] for n in TRAIL]
pts = [(pts[0][0], pts[0][1] - 20)] + pts + [(pts[-1][0], pts[-1][1] + 20)]
trail = []
for i in range(1, len(pts) - 2):
    for s in range(20):
        trail.append(cr(s / 20, pts[i - 1], pts[i], pts[i + 1], pts[i + 2]))
trail.append(pts[-2])
path = np.zeros((AH, AW), dtype=bool)
for (px, py) in trail:
    path |= (xx - px) ** 2 + (yy - py) ** 2 <= 5.5 ** 2
# odbočky k místům (užší, jen vyšlapaná stopa v popelu)
spur = np.zeros((AH, AW), dtype=bool)
for (a, b) in EDGES:
    if a in TRAIL and b in TRAIL:
        continue
    (x0, y0), (x1, y1) = NODES[a], NODES[b]
    for s in range(40):
        t = s / 39
        spur |= (xx - (x0 + (x1 - x0) * t)) ** 2 + (yy - (y0 + (y1 - y0) * t)) ** 2 <= 3.2 ** 2
for y in range(AH):
    for x in range(AW):
        b = BAYER[y % 4, x % 4]
        if path[y, x]:
            buried = fbm(x * 0.12, y * 0.12, 33)
            if buried > 0.56:
                img[y, x] = mix(img[y, x], rgb(0xC4BAAE), min(1, (buried - 0.56) * 6) * 0.6)   # navátý popel
                if buried > 0.6:
                    continue
            off = (y // 5) % 2 * 3
            cx, cy = (x + off) // 6, y // 5
            if (x + off) % 6 == 0 or y % 5 == 0:
                c = rgb(0x6A6264)                                       # spáry
            else:
                k = (((cx * 73856093) ^ (cy * 19349663)) & 1023) / 1023
                c = mix(rgb(0x8E8682), rgb(0xB2AAA2), k * 0.8 + b * 0.15)
                if (x + off) % 6 == 1 or y % 5 == 1:
                    c = mix(c, rgb(0xD0C8C0), 0.35)                     # světlá hrana kamene
            img[y, x] = mix(img[y, x], c, 0.85)
        elif spur[y, x]:
            img[y, x] = mix(img[y, x], rgb(0x8A8076), 0.45 + b * 0.1)

# ═════════════════════════════════════════════════════════════════════════════
# 3) SKALNÍ PRÁH NAHOŘE + ZBOŘENÝ OBLOUK (druhá strana Brány světů)
# ═════════════════════════════════════════════════════════════════════════════
for y in range(0, 30):
    for x in range(AW):
        rim = 22 + fbm(x * 0.12, 4, 41) * 8
        if y < rim:
            t = y / rim
            img[y, x] = mix(rgb(0x3A3440), rgb(0x6A6070), t * 0.8 + BAYER[y % 4, x % 4] * 0.15)
            if abs(x - 80) > 13:
                blocks[y, x] = True
# schody dolů ze skály
for i, y in enumerate(range(14, 32, 3)):
    for x in range(70 - i, 91 + i):
        c = rgb(0x9A90A0) if (y % 3) else rgb(0x4A4252)
        for dy in range(3):
            put(x, y + dy, mix(c, rgb(0x5A5262), dy * 0.25))
# dva zlomené pilíře oblouku a ležící klenák
for sx, top in ((62, 6), (98, 12)):
    for y in range(top, 34):
        for x in range(sx - 3, sx + 4):
            c = mix(rgb(0x4A445C), rgb(0xA89EB8), 0.5 + (x - sx) * -0.13)
            if x in (sx - 3, sx + 3) or y == top:
                c = rgb(0x221E30)
            put(x, y, c, block=True)
    put(sx, top + 8, rgb(0x6AE8D8)); put(sx, top + 13, rgb(0x6AE8D8))      # runy dosud svítí
for x in range(102, 116):
    for y in range(30, 35):
        put(x, y, rgb(0x221E30) if y in (30, 34) or x in (102, 115) else rgb(0x8C8298), block=True)
# slabá zelená záře pod obloukem
for y in range(4, 40):
    for x in range(64, 97):
        d = math.hypot((x - 80) / 16, (y - 18) / 18)
        if d < 1:
            img[y, x] = mix(img[y, x], rgb(0x7EF0A0), (1 - d) ** 2 * 0.35)

# ═════════════════════════════════════════════════════════════════════════════
# 4) POPELOVÉ DUNY (vpravo nahoře)
# ═════════════════════════════════════════════════════════════════════════════
for (cx, cy, rx, ry) in ((128, 78, 20, 11), (110, 92, 13, 7), (140, 98, 12, 8)):
    m = ellipse(cx, cy, rx, ry)
    for y, x in zip(*np.nonzero(m)):
        if path[y, x]:
            continue
        lit = (cx - x) / rx * 0.4 + (cy - y) / ry * 0.6
        img[y, x] = mix(rgb(0x9A9084), rgb(0xDCD2C4), 0.45 + lit * 0.5 + BAYER[y % 4, x % 4] * 0.1)
        if (x + y * 2) % 7 == 0 and lit < 0:
            img[y, x] = mix(img[y, x], rgb(0x7A7066), 0.5)              # vlnky
# napůl zavátý vůz
for x in range(132, 148):
    for y in range(110, 116):
        put(x, y, rgb(0x3A2E28) if y in (110, 115) or x in (132, 147) else rgb(0x6A5444), block=True)
for (wx, wy) in ((135, 117), (144, 117)):
    for a in range(0, 360, 30):
        put(wx + round(2.5 * math.cos(math.radians(a))), wy + round(2.5 * math.sin(math.radians(a))), rgb(0x2A221E))

# ═════════════════════════════════════════════════════════════════════════════
# 5) LUCERNY PODÉL CESTY (pár ještě svítí)
# ═════════════════════════════════════════════════════════════════════════════
def lantern(x, y, lit):
    for dy in range(0, 10):
        put(x, y - dy, rgb(0x2A2426), block=dy < 3)
    for dx in (-1, 0, 1):
        for dy in (10, 11, 12):
            put(x + dx, y - dy, rgb(0xFFD27A) if lit and dx == 0 and dy == 11 else rgb(0x3A3234))
    if lit:
        for yy2 in range(y - 20, y - 2):
            for xx2 in range(x - 8, x + 9):
                d = math.hypot(xx2 - x, yy2 - (y - 11)) / 9
                if 0 <= xx2 < AW and 0 <= yy2 < AH and d < 1 and not (xx2 == x and y - 13 < yy2 < y - 9):
                    img[yy2, xx2] = mix(img[yy2, xx2], rgb(0xFFC870), (1 - d) ** 2 * 0.3)

for (x, y, lit) in ((57, 92, True), (98, 132, False), (58, 182, True), (84, 228, False), (98, 330, True), (60, 372, True)):
    lantern(x, y, lit)

# ═════════════════════════════════════════════════════════════════════════════
# 6) CEDULE (rozcestník)
# ═════════════════════════════════════════════════════════════════════════════
SX, SY = 40, 112
for dy in range(0, 12):
    put(SX, SY - dy, rgb(0x4A3A30), block=dy < 3)
for x in range(SX - 6, SX + 8):
    for y in range(SY - 12, SY - 7):
        c = rgb(0x2A221E) if y in (SY - 12, SY - 8) or x in (SX - 6, SX + 7) else rgb(0x9A8466)
        put(x, y, c)
for x in range(SX - 4, SX + 5, 2):
    put(x, SY - 10, rgb(0x3A2E26))                                       # nečitelný nápis

# ═════════════════════════════════════════════════════════════════════════════
# 7) SPÁLENÝ HÁJ (vpravo) s rudými houbami
# ═════════════════════════════════════════════════════════════════════════════
def dead_tree(x, y, h, seed):
    r = np.random.default_rng(seed)
    for dy in range(h):
        w = 1 if dy > h * 0.4 else 2
        for dx in range(-w + 1, w):
            put(x + dx, y - dy, rgb(0x1E1A1C) if dx <= 0 else rgb(0x3A3236), block=dy < 3)
    for _ in range(4):
        by = y - int(h * r.uniform(0.45, 0.9)); dirx = r.choice([-1, 1]); ln = int(r.integers(3, 7))
        for i in range(ln):
            put(x + dirx * (i + 1), by - i // 2, rgb(0x2A2428))
    for dx in range(-3, 4):
        put(x + dx, y + 1, mix(img[min(y + 1, AH - 1), x], rgb(0x1A1616), 0.4))   # stín

for i, (tx, ty, th) in enumerate(((112, 160, 16), (132, 156, 19), (146, 176, 14), (128, 186, 17), (104, 182, 12), (142, 148, 13))):
    dead_tree(tx, ty, th, i + 7)
for (mx, my) in ((118, 176), (136, 168), (125, 158), (108, 168)):
    put(mx, my, rgb(0xC83A3A)); put(mx + 1, my, rgb(0xE85A4A)); put(mx, my + 1, rgb(0xE8D8C8))

# ═════════════════════════════════════════════════════════════════════════════
# 8) VYHASLÉ OHNIŠTĚ (vlevo) – kruh kamenů, žhavé uhlíky
# ═════════════════════════════════════════════════════════════════════════════
OX, OY = 32, 206
for y in range(OY - 8, OY + 9):
    for x in range(OX - 9, OX + 10):
        d = math.hypot(x - OX, (y - OY) * 1.2)
        if d < 9:
            img[y, x] = mix(img[y, x], rgb(0x2A2224), (1 - d / 9) * 0.7)
for a in range(0, 360, 36):
    sx, sy = OX + round(6 * math.cos(math.radians(a))), OY + round(5 * math.sin(math.radians(a)))
    put(sx, sy, rgb(0x8A8088), block=True); put(sx + 1, sy, rgb(0x5A5258), block=True)
for (ex, ey) in ((OX, OY), (OX - 1, OY + 1), (OX + 2, OY - 1), (OX + 1, OY + 1)):
    put(ex, ey, rgb(0xFF8A3A))
put(OX, OY - 1, rgb(0xFFD27A))

# ═════════════════════════════════════════════════════════════════════════════
# 9) SUTINY DOMŮ (vpravo dole) – nízké zdi, komín
# ═════════════════════════════════════════════════════════════════════════════
def wall(x0, y0, x1, y1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            brick = (x + (y // 2) % 2 * 2) % 4 == 0 or y % 2 == 0
            c = rgb(0x6A5E5A) if brick else rgb(0xA49890)
            if y == y0:
                c = rgb(0xC4B8AE)
            put(x, y, c, block=True)

wall(104, 256, 132, 258); wall(104, 256, 106, 278); wall(130, 256, 132, 266)
wall(122, 280, 146, 282); wall(144, 268, 146, 282)
for y in range(244, 258):
    for x in range(126, 131):
        put(x, y, rgb(0x2A2224) if x in (126, 130) else rgb(0x7A6A62), block=True)
for (dx, dy) in ((110, 264), (114, 270), (120, 262), (138, 274)):
    for k in range(3):
        put(dx + k, dy, rgb(0x8A7E78)); put(dx + k, dy + 1, rgb(0x5A504C))   # suť

# ═════════════════════════════════════════════════════════════════════════════
# 10) VYPÁLENÉ POLE SE STRAŠÁKEM (vlevo dole)
# ═════════════════════════════════════════════════════════════════════════════
for y in range(304, 338):
    for x in range(14, 58):
        if path[y, x] or spur[y, x]:
            continue
        if (y - 304) % 4 == 0:
            img[y, x] = mix(img[y, x], rgb(0x3A3030), 0.55)                # brázdy
        elif (x * 3 + y) % 5 == 0:
            img[y, x] = mix(img[y, x], rgb(0x8A7A50), 0.6)                 # strniště
SCX, SCY = 26, 318
for dy in range(0, 14):
    put(SCX, SCY - dy, rgb(0x4A3A30), block=dy < 3)
for dx in range(-5, 6):
    put(SCX + dx, SCY - 10, rgb(0x4A3A30))
for dx in (-1, 0, 1):
    for dy in (14, 15, 16):
        put(SCX + dx, SCY - dy, rgb(0xB8A070))
put(SCX - 1, SCY - 15, rgb(0x2A2224)); put(SCX + 1, SCY - 15, rgb(0x2A2224))

# ═════════════════════════════════════════════════════════════════════════════
# 11) MEZNÍK (S-0)
# ═════════════════════════════════════════════════════════════════════════════
MX, MY = 122, 356
for y in range(MY - 10, MY + 1):
    for x in range(MX - 3, MX + 4):
        top = MY - 10 + (1 if abs(x - MX) == 3 else 0)
        if y < top:
            continue
        c = mix(rgb(0x7A7484), rgb(0xB0A8B8), 0.5 - (x - MX) * 0.12)
        if x in (MX - 3, MX + 3) or y == top:
            c = rgb(0x2A2632)
        put(x, y, c, block=True)
for (px, py) in ((MX - 1, MY - 7), (MX, MY - 7), (MX - 1, MY - 6), (MX, MY - 5), (MX - 1, MY - 4), (MX, MY - 4)):
    put(px, py, rgb(0x3A3444))                                           # vytesané „S“

# ═════════════════════════════════════════════════════════════════════════════
# 12) HRADBY OZVĚNY DOLE – bílé, čisté, brána
# ═════════════════════════════════════════════════════════════════════════════
WALL_Y = 408
for y in range(WALL_Y, AH):
    for x in range(AW):
        gate = abs(x - 80) <= 9
        if y >= WALL_Y + 22:
            img[y, x] = mix(rgb(0xD8D0C4), rgb(0xEEE6DA), BAYER[y % 4, x % 4] + 0.5)   # dlažba za branou
            continue
        brick = (x + (y // 3) % 2 * 3) % 6 == 0 or y % 3 == 0
        c = rgb(0xD4CCC0) if brick else rgb(0xF2ECE2)
        if y == WALL_Y:
            c = rgb(0xFFFFFF)
        if y < WALL_Y + 3 and x % 6 < 3:
            c = rgb(0x8A8278)                                              # cimbuří
        if gate:
            c = rgb(0x6A4A30) if y > WALL_Y + 4 else rgb(0xA89A88)        # vrata
            if x == 80 or y == WALL_Y + 12:
                c = rgb(0x3A2A1E)
        img[y, x] = c
        blocks[y, x] = True
# věže po stranách brány s rudými střechami
for tx in (62, 98):
    for y in range(WALL_Y - 12, WALL_Y + 20):
        for x in range(tx - 6, tx + 7):
            if y < WALL_Y - 4:
                w = (y - (WALL_Y - 12)) * 0.8
                if abs(x - tx) > w:
                    continue
                c = rgb(0xB84A3A) if abs(x - tx) < w - 1 else rgb(0x6A2A22)
            else:
                c = rgb(0xF2ECE2) if abs(x - tx) < 6 else rgb(0xA49A8E)
                if y == WALL_Y + 4 and abs(x - tx) < 2:
                    c = rgb(0x2A2632)                                       # střílna
            put(x, y, c, block=True)
# vlajky
for tx in (62, 98):
    for dy in range(12, 18):
        put(tx, WALL_Y - dy, rgb(0x2A2632))
    for dx in range(1, 5):
        put(tx + dx, WALL_Y - 17, rgb(0xF0C850)); put(tx + dx, WALL_Y - 16, rgb(0xD8A830))
# popel se hradbám vyhýbá: čistší pruh před nimi
for y in range(WALL_Y - 14, WALL_Y):
    for x in range(AW):
        if not blocks[y, x] and not path[y, x]:
            img[y, x] = mix(img[y, x], rgb(0xD0C8BA), (y - (WALL_Y - 14)) / 14 * 0.5)

# ═════════════════════════════════════════════════════════════════════════════
# 13) DROBNOSTI – suché trsy, kamínky, vločky popela
# ═════════════════════════════════════════════════════════════════════════════
for _ in range(220):
    x, y = int(rng.integers(12, AW - 12)), int(rng.integers(36, WALL_Y - 4))
    if path[y, x] or blocks[y, x]:
        continue
    k = rng.random()
    if k < 0.4:
        put(x, y, rgb(0x8A7A50)); put(x + 1, y - 1, rgb(0xA89660)); put(x - 1, y - 1, rgb(0x7A6A44))
    elif k < 0.7:
        put(x, y, rgb(0x5A5254)); put(x + 1, y, rgb(0x8A8286))
    else:
        put(x, y, rgb(0xEAE4DC))

# ═════════════════════════════════════════════════════════════════════════════
# 14) MAPA CHŮZE + VÝSTUPY
# ═════════════════════════════════════════════════════════════════════════════
walk = ~blocks
walk[:28, :] = False
walk[:40, 70:91] = True
walk[WALL_Y:, :] = False

os.makedirs(RES, exist_ok=True)
Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(os.path.join(RES, "ash_plain.png"))

gw, gh = math.ceil(AW / CELL), math.ceil(AH / CELL)
rows = []
for gy in range(gh):
    row = ""
    for gx in range(gw):
        cell = walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL]
        row += "." if cell.mean() >= 0.5 else "#"
    rows.append(row)
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
os.makedirs(WALK, exist_ok=True)
with open(os.path.join(WALK, "ash_plain.txt"), "w") as f:
    f.write(f"# mapa chůze ash_plain: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_ashplain.py)\n")
    f.write(f"{AW} {AH} {CELL}\n")
    f.write("\n".join(rows) + "\n")

dbg = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((AW * 4, AH * 4), Image.NEAREST).convert("RGBA")
ov = Image.new("RGBA", dbg.size, (0, 0, 0, 0))
d = ImageDraw.Draw(ov)
for gy, row in enumerate(rows):
    for gx, ch in enumerate(row):
        if ch == "#":
            d.rectangle([gx * CELL * 4, gy * CELL * 4, (gx + 1) * CELL * 4 - 1, (gy + 1) * CELL * 4 - 1], fill=(255, 0, 0, 35))
for (a, b) in EDGES:
    (x0, y0), (x1, y1) = NODES[a], NODES[b]
    d.line([x0 * 4, y0 * 4, x1 * 4, y1 * 4], fill=(255, 255, 0, 200), width=2)
for n, (x, y) in NODES.items():
    d.ellipse([x * 4 - 6, y * 4 - 6, x * 4 + 6, y * 4 + 6], fill=(0, 120, 255, 220))
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "ash_plain_debug.png"))
print("ok", AW, AH)
