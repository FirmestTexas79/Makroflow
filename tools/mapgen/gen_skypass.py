"""
Použití:  python tools/mapgen/gen_skypass.py   (vyžaduje Pillow + numpy)
Výstup:   app/src/main/res/drawable-nodpi/sky_pass.png   (1 px = 1 art pixel)
          app/src/main/assets/walk/sky_pass.txt           (mapa chůze, stejný formát jako gen_walkmask)
          tools/mapgen/sky_pass_debug.png                 (4x, s uzly a mapou chůze)

Nebeský průsmyk (docs/adr/0044) – za otevřenou branou svatyně. 160 × 440 art px, na šířku
je vidět celá mapa, kamera jezdí svisle. Zdola nahoru:
  * temný oblouk brány, kterou jsme přišli ze svatyně,
  * soutěska se serpentinou mezi skalními stěnami: vrstvy skály, sníh na římsách, kamenní
    mužíci, lana s modlitebními praporky, spálená skála a rýhy po drápech legendy,
  * vyhlídková plošina s Branou světů – kamenný prstenec s runami a prázdným lůžkem
    ve tvaru listu (sem patří Srdce Hvozdu),
  * VÝHLED do nového regionu: svítání nad mořem mraků, sopka s kouřem, létající ostrovy
    s vodopády, údolí s řekou, terasová pole a vzdálené město s arénou.

NODES / EDGES jsou ZDROJ PRAVDY i pro SkyPass.kt (MAP).
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
rng = np.random.default_rng(4242)

# ── Uzly a hrany (zdroj pravdy pro SkyPass.kt) ──
NODES = {
    "vstup_ze_svatyne": (80, 424),
    "p1": (54, 396), "p2": (98, 362), "drapy": (110, 344), "p3": (62, 326),
    "p4": (100, 290), "muzik": (54, 280), "p5": (78, 256),
    "vyhlidka": (40, 222), "brana_svetu": (80, 226), "plosina": (116, 222),
}
EDGES = [
    ("vstup_ze_svatyne", "p1"), ("p1", "p2"), ("p2", "drapy"), ("p2", "p3"), ("p3", "p4"),
    ("p4", "muzik"), ("muzik", "p5"), ("p4", "p5"), ("p5", "brana_svetu"),
    ("brana_svetu", "vyhlidka"), ("brana_svetu", "plosina"),
]
# serpentina (střed cesty) – kreslí se přes uzly v tomhle pořadí
TRAIL = ["vstup_ze_svatyne", "p1", "p2", "p3", "p4", "p5", "brana_svetu"]

GATE_C = (80, 190)          # střed prstence Brány světů
GATE_R_OUT, GATE_R_IN = 25, 19
LEDGE_Y0, LEDGE_Y1 = 204, 244   # vyhlídková plošina

img = np.zeros((AH, AW, 3), dtype=np.float64)


def rgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=np.float64)


def mix(a, b, t):
    return a + (b - a) * np.clip(t, 0, 1)


def put(x, y, c):
    if 0 <= x < AW and 0 <= y < AH:
        img[y, x] = c


def noise2(x, y, s):
    """Hladký hodnotový šum 0..1 (bilineární)."""
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


# ═════════════════════════════════════════════════════════════════════════════
# 1) OBLOHA – svítání (nahoře indigo, u obzoru růžová a oranžová), hvězdy, slunce
# ═════════════════════════════════════════════════════════════════════════════
HORIZON = 124
SKY = [(0, rgb(0x141030)), (40, rgb(0x2C2254)), (78, rgb(0x5E3A78)), (100, rgb(0xB0507A)),
       (114, rgb(0xE8845E)), (124, rgb(0xFFC46E))]
SUN = (100, 113)


def sky_color(y):
    for (y0, c0), (y1, c1) in zip(SKY, SKY[1:]):
        if y <= y1:
            return mix(c0, c1, (y - y0) / max(1, y1 - y0))
    return SKY[-1][1]


for y in range(0, HORIZON + 40):
    base = sky_color(min(y, HORIZON))
    for x in range(AW):
        b = BAYER[y % 4, x % 4]
        c = sky_color(min(HORIZON, max(0, y + b * 5)))
        # záře kolem slunce
        d = math.hypot(x - SUN[0], (y - SUN[1]) * 1.6)
        glow = max(0.0, 1 - d / 70.0) ** 2
        c = mix(c, rgb(0xFFD890), glow * 0.55)
        img[y, x] = c

# hvězdy nahoře (slábnou k obzoru)
for _ in range(70):
    x, y = int(rng.integers(0, AW)), int(rng.integers(0, 70))
    a = 1 - y / 70
    if rng.random() < a:
        img[y, x] = mix(img[y, x], rgb(0xFFF6E0), 0.5 + 0.5 * a)
        if rng.random() < 0.12 and y > 1 and x > 1:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                put(x + dx, y + dy, mix(img[y + dy, x + dx], rgb(0xFFF6E0), 0.35))

# sluneční paprsky (světlejší klíny z místa slunce)
for ang in np.linspace(-2.6, -0.5, 7):
    for r in range(10, 120):
        for w in (-1, 0, 1):
            x = int(SUN[0] + math.cos(ang) * r - math.sin(ang) * w * (r / 40))
            y = int(SUN[1] + math.sin(ang) * r + math.cos(ang) * w * (r / 40))
            if 0 <= x < AW and 0 <= y < HORIZON:
                img[y, x] = mix(img[y, x], rgb(0xFFE2B0), 0.10 * (1 - r / 120))

# ═════════════════════════════════════════════════════════════════════════════
# 2) DALEKÉ HORY A SOPKA
# ═════════════════════════════════════════════════════════════════════════════
def ridge(seed, base, amp, freq):
    return [base - amp * fbm(x * freq, 0.3, seed, 4) for x in range(AW)]

far = ridge(11, 116, 30, 0.045)
mid_r = ridge(23, 124, 20, 0.07)
for x in range(AW):
    for y in range(int(far[x]), HORIZON + 6):
        t = (y - far[x]) / 40
        c = mix(rgb(0x8A6A9A), rgb(0xB77E8E), t)
        # sluncem osvětlená hrana vlevo od slunce
        if y - far[x] < 2:
            c = mix(c, rgb(0xFFD2A0), 0.45 if x > 40 else 0.2)
        img[y, x] = mix(img[y, x], c, 0.85)
    for y in range(int(mid_r[x]), HORIZON + 10):
        c = mix(rgb(0x5E4878), rgb(0x7C5A84), (y - mid_r[x]) / 20)
        if y - mid_r[x] < 1:
            c = mix(c, rgb(0xFFB888), 0.35)
        img[y, x] = c

# sopka vpravo: kužel s rozžhaveným kráterem a lávovým pramínkem
VX, VTOP = 138, 92
for y in range(VTOP, HORIZON + 8):
    half = 2 + (y - VTOP) * 0.78
    for x in range(int(VX - half), int(VX + half) + 1):
        if not (0 <= x < AW):
            continue
        side = (x - VX) / max(1.0, half)
        c = mix(rgb(0x6A4A68), rgb(0x8A5E74), 0.5 - side * 0.5)
        if side < -0.55:
            c = mix(c, rgb(0xE8A080), 0.25)             # osvětlený levý svah
        c = mix(c, img[y, x], 0.25)                      # opar dálky
        if y - VTOP < 3:
            c = rgb(0xFF7A2A) if abs(x - VX) <= 2 else rgb(0x3A2030)   # kráter
        img[y, x] = c
lava = [(VX, VTOP + 2)]
for i in range(14):
    lx, ly = lava[-1]
    lava.append((lx + (1 if i % 4 == 1 else 0), ly + 1))
for i, (lx, ly) in enumerate(lava):
    put(lx, ly, mix(rgb(0xFFB04A), rgb(0xB04030), i / 14))
# kouř z kráteru – obláčky stoupající doleva nahoru
for i in range(10):
    cx = VX - i * 3.0 + math.sin(i * 1.3) * 2
    cy = VTOP - 3 - i * 3.6
    r = 1.8 + i * 0.5
    for y in range(int(cy - r), int(cy + r) + 1):
        for x in range(int(cx - r), int(cx + r) + 1):
            if 0 <= x < AW and 0 <= y < AH and math.hypot(x - cx, (y - cy) * 1.2) <= r:
                shade = 0.55 + 0.45 * (1 - (y - (cy - r)) / (2 * r))
                c = mix(rgb(0x6A5470), rgb(0xB89AB0), shade)
                img[y, x] = mix(img[y, x], c, 0.78 - i * 0.03)

# ═════════════════════════════════════════════════════════════════════════════
# 3) LÉTAJÍCÍ OSTROVY S VODOPÁDY (vlevo)
# ═════════════════════════════════════════════════════════════════════════════
def island(cx, cy, w, depth, seed):
    for x in range(int(cx - w), int(cx + w) + 1):
        u = (x - cx) / w
        if abs(u) > 1:
            continue
        top = cy - 2 + (1 - u * u) * -1.5
        bottom = cy + depth * (1 - abs(u) ** 1.4) + fbm(x * 0.4, 1, seed) * 2
        for y in range(int(top), int(bottom) + 1):
            if y < int(top) + 2:
                c = rgb(0x6EB050) if y == int(top) else rgb(0x4E8A3C)       # tráva
            else:
                t = (y - top) / max(1, bottom - top)
                c = mix(rgb(0x8C6A5A), rgb(0x4A3448), t)                     # skála
                if (x + y) % 5 == 0:
                    c = mix(c, rgb(0x2E2032), 0.4)
            put(x, y, c)
    # stromky na ostrově
    for k in range(int(w // 3)):
        tx = int(cx - w + 2 + k * 3 + (seed % 2))
        put(tx, int(cy - 4), rgb(0x2E6A34)); put(tx, int(cy - 5), rgb(0x4E9A44)); put(tx + 1, int(cy - 4), rgb(0x2E6A34))

island(24, 60, 13, 16, 3)
island(52, 88, 7, 9, 5)
island(8, 96, 5, 6, 7)
# vodopády padající do mraků
for (wx, wy0) in ((30, 60), (54, 88)):
    for y in range(wy0, HORIZON + 4):
        for dx in (0, 1):
            t = (y - wy0) / (HORIZON - wy0)
            c = mix(rgb(0xDDEEFF), rgb(0xFFD8C0), t)
            img[y, wx + dx] = mix(img[y, wx + dx], c, 0.85 - t * 0.35)
        if y % 3 == 0:
            put(wx + 2, y, mix(img[y, wx + 2] if wx + 2 < AW else rgb(0), rgb(0xFFFFFF), 0.3))

# ptáci nad obzorem
for (bx, by) in ((70, 48), (76, 44), (82, 50), (62, 56)):
    for dx, dy in ((-1, -1), (0, 0), (1, -1)):
        put(bx + dx, by + dy, rgb(0x2A1C3A))

# slunce (kotouč těsně nad obzorem, částečně za horami)
for y in range(SUN[1] - 9, SUN[1] + 1):
    for x in range(SUN[0] - 9, SUN[0] + 10):
        if math.hypot(x - SUN[0], y - SUN[1]) <= 8.5 and y < mid_r[x] + 2:
            img[y, x] = mix(rgb(0xFFF4D0), rgb(0xFFD070), math.hypot(x - SUN[0], y - SUN[1]) / 9)

# ═════════════════════════════════════════════════════════════════════════════
# 4) NOVÝ REGION POD MRAKY: údolí, řeka, pole, lesíky a město s arénou (y ~ 128 – 186)
# ═════════════════════════════════════════════════════════════════════════════
VALLEY_TOP, VALLEY_BOT = 126, 216
for y in range(VALLEY_TOP, VALLEY_BOT):
    for x in range(AW):
        t = (y - VALLEY_TOP) / (VALLEY_BOT - VALLEY_TOP)
        hills = fbm(x * 0.06, y * 0.12, 31)
        c = mix(rgb(0x5E7A58), rgb(0x3E6A3A), t)          # vzdálenější zelené kopce jsou do modra
        c = mix(c, rgb(0x8AA070), hills * 0.5 * (1 - t))
        c = mix(c, rgb(0xB48A7A), (1 - t) * 0.45)         # opar a narůžovělé světlo svítání
        b = BAYER[y % 4, x % 4]
        if hills + b * 0.1 > 0.62:
            c = mix(c, rgb(0xE8C890), 0.25 * (1 - t))     # sluncem ozářené hřbety
        img[y, x] = c

# terasová pole vlevo
for row in range(5):
    y0 = 150 + row * 5
    for x in range(8 + row * 2, 54 - row * 3):
        for y in range(y0, y0 + 4):
            base = [rgb(0xB8B060), rgb(0x8EA850), rgb(0xC8A458)][(row + x // 14) % 3]
            c = mix(base, rgb(0xB48A7A), 0.35 - row * 0.05)
            if y == y0:
                c = mix(c, rgb(0x5A6A3A), 0.5)              # mez
            img[y, x] = c

# lesíky (tmavé tečky)
for _ in range(90):
    x, y = int(rng.integers(0, AW)), int(rng.integers(VALLEY_TOP + 8, VALLEY_BOT))
    if 60 < x < 132 and y < 168:
        continue
    t = (y - VALLEY_TOP) / (VALLEY_BOT - VALLEY_TOP)
    c = mix(rgb(0x3A5238), rgb(0x24442A), t)
    put(x, y, c); put(x + 1, y, c); put(x, y - 1, mix(c, rgb(0x6E9A58), 0.5))

# řeka od slunce k nám – odráží oranžové světlo
river = []
x = 92.0
for y in range(VALLEY_TOP + 2, VALLEY_BOT + 2):
    x += math.sin(y * 0.19) * 1.2 - 0.55
    river.append((x, y))
for (rx, ry) in river:
    w = 1 + (ry - VALLEY_TOP) / 16
    for xx in range(int(rx - w), int(rx + w) + 1):
        t = (ry - VALLEY_TOP) / (VALLEY_BOT - VALLEY_TOP)
        c = mix(rgb(0xFFE0A0), rgb(0xE88C5A), t)
        if (xx + ry) % 4 == 0:
            c = mix(c, rgb(0xFFF6D8), 0.5)
        put(xx, ry, c)

# město s arénou vpravo uprostřed (jasné, na slunci)
CITY = (112, 150)
def rect(x0, y0, x1, y1, c):
    for yy in range(y0, y1 + 1):
        for xx in range(x0, x1 + 1):
            put(xx, yy, c)
# hradby
rect(92, 158, 132, 161, rgb(0xB89A86))
for xx in range(92, 133, 3):
    put(xx, 157, rgb(0xB89A86))
# aréna (ovál) – kolos se sloupořadím
ax, ay = 104, 152
for yy in range(ay - 5, ay + 6):
    for xx in range(ax - 10, ax + 11):
        e = ((xx - ax) / 10) ** 2 + ((yy - ay) / 5.5) ** 2
        if e <= 1:
            if e > 0.55:
                c = rgb(0xE8D2B0) if (xx % 2 == 0 or yy < ay) else rgb(0xA88A74)
            else:
                c = rgb(0xC8A270) if yy > ay - 2 else rgb(0x6E5446)  # písek arény / vnitřní stín
            put(xx, yy, c)
# věže a domy
for (tx, h, roof) in ((118, 14, 0xC0503A), (124, 10, 0x4A6AA8), (129, 12, 0xC0503A), (96, 8, 0x4A6AA8), (88, 6, 0xC0503A)):
    rect(tx, 158 - h, tx + 3, 158, rgb(0xD8C4AE))
    rect(tx, 158 - h, tx, 158, rgb(0xB09C88))
    put(tx + 1, 158 - h - 1, rgb(roof)); put(tx + 2, 158 - h - 1, rgb(roof))
    put(tx + 1, 158 - h - 2, rgb(roof))
    for wy in range(158 - h + 2, 157, 3):
        put(tx + 2, wy, rgb(0xFFE08A))            # rozsvícená okna
# vlajka na nejvyšší věži
put(119, 141, rgb(0x6A5A4A)); put(119, 140, rgb(0x6A5A4A)); put(120, 140, rgb(0xE0503A)); put(121, 140, rgb(0xE0503A)); put(120, 141, rgb(0xE0503A))

# ═════════════════════════════════════════════════════════════════════════════
# 5) MOŘE MRAKŮ – vrstva přes obzor a údolí, růžové vršky, fialové stíny, průhledy
# ═════════════════════════════════════════════════════════════════════════════
for y in range(HORIZON - 4, VALLEY_BOT):
    for x in range(AW):
        n = fbm(x * 0.07 + y * 0.01, y * 0.16, 77, 4)
        band = 1 - abs((y - 134) / 16.0)          # hustší kolem y 134, řidne k údolí
        dens = n * 1.25 + band * 0.55 - 0.72
        if y > 168:
            dens -= (y - 168) * 0.05
        if 88 < x < 136 and 140 < y < 164:
            dens -= 0.5                            # průhled na město
        if dens > 0:
            top = fbm(x * 0.07 + (y - 2) * 0.01, (y - 2) * 0.16, 77, 4)
            lit = n - top                          # horní hrana obláčku = světlejší
            c = mix(rgb(0x9C7AA8), rgb(0xFFD6C8), 0.35 + lit * 3 + (1 - (y - HORIZON) / 50) * 0.35)
            if dens < 0.06:
                c = mix(img[y, x], c, 0.55)        # jemný okraj
            img[y, x] = c

# ═════════════════════════════════════════════════════════════════════════════
# 6) SKALNÍ STĚNY PRŮSMYKU + VYHLÍDKOVÁ PLOŠINA + SOUTĚSKA
# ═════════════════════════════════════════════════════════════════════════════
def cr(t, p0, p1, p2, p3):
    t2, t3 = t * t, t * t * t
    return tuple(0.5 * ((2 * p1[i]) + (-p0[i] + p2[i]) * t + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t2 +
                        (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t3) for i in range(2))

pts = [NODES[n] for n in TRAIL]
pts = [(pts[0][0], pts[0][1] + 20)] + pts + [(pts[-1][0], pts[-1][1] - 10)]
trail = []
for i in range(1, len(pts) - 2):
    for s in range(24):
        trail.append(cr(s / 24, pts[i - 1], pts[i], pts[i + 1], pts[i + 2]))
trail.append(pts[-2])

walk = np.zeros((AH, AW), dtype=bool)
path_mask = np.zeros((AH, AW), dtype=bool)
yy, xx = np.mgrid[0:AH, 0:AW]
for (px, py) in trail:
    r = 9.5
    m = (xx - px) ** 2 + (yy - py) ** 2 <= r * r
    path_mask |= m
# plošina: nepravidelná elipsa pod Branou
ledge = (((xx - 80) / 62.0) ** 2 + ((yy - 224) / 20.0) ** 2) <= 1.0
floor_mask = path_mask | ledge
# rozšíření k odbočkám (drápy, mužík)
for n in ("drapy", "muzik", "vyhlidka", "plosina"):
    nx, ny = NODES[n]
    floor_mask |= (xx - nx) ** 2 + (yy - ny) ** 2 <= 8.5 ** 2
# odbočky mimo serpentinu: úzká stezka od uzlu k sousedovi na cestě (jinak by mužík ležel ve skále)
def spur(a, b, r=5.5):
    global floor_mask
    (ax, ay), (bx, by) = NODES[a], NODES[b]
    for k in range(41):
        t = k / 40
        cx, cy = ax + (bx - ax) * t, ay + (by - ay) * t
        floor_mask |= (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r
spur("muzik", "p5")
spur("drapy", "p2")
floor_mask[:LEDGE_Y0 - 6, :] = False


def voronoi(spacing, seed, y0, y1):
    """Pro každý pixel v řádcích y0..y1: (id buňky, d1, d2, dx, dy od středu)."""
    r = np.random.default_rng(seed)
    pts = []
    for gy in range(int(y0 / spacing) - 1, int(y1 / spacing) + 2):
        for gx in range(-1, int(AW / spacing) + 2):
            pts.append((gx * spacing + r.random() * spacing, gy * spacing + r.random() * spacing))
    pts = np.array(pts)
    H = y1 - y0
    ys, xs = np.mgrid[y0:y1, 0:AW]
    best = np.full((H, AW), 1e9); second = np.full((H, AW), 1e9); bid = np.zeros((H, AW), dtype=int)
    bdx = np.zeros((H, AW)); bdy = np.zeros((H, AW))
    for k, (px, py) in enumerate(pts):
        if py < y0 - spacing * 2 or py > y1 + spacing * 2:
            continue
        dx = xs - px; dy = ys - py
        d = np.sqrt(dx * dx + dy * dy)
        closer = d < best
        second = np.where(closer, best, np.minimum(second, d))
        bdx = np.where(closer, dx, bdx); bdy = np.where(closer, dy, bdy)
        bid = np.where(closer, k, bid)
        best = np.where(closer, d, best)
    return bid, best, second, bdx, bdy


def hash01(k, s=0):
    v = (int(k) * 2654435761 + s * 97) & 0xFFFFFFFF
    v = ((v ^ (v >> 15)) * 2246822519) & 0xFFFFFFFF
    return ((v ^ (v >> 13)) & 0xFFFF) / 65535.0


# vzdálenost od podlahy (pro výšku skal, stíny a hrany)
from collections import deque
dist_floor = np.full((AH, AW), 99.0)
q = deque()
fy, fx = np.nonzero(floor_mask)
for y, x in zip(fy, fx):
    dist_floor[y, x] = 0; q.append((y, x))
while q:
    y, x = q.popleft()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ny, nx = y + dy, x + dx
        if 0 <= ny < AH and 0 <= nx < AW and dist_floor[ny, nx] > dist_floor[y, x] + 1:
            dist_floor[ny, nx] = dist_floor[y, x] + 1
            q.append((ny, nx))

WALL_Y0 = 176
def left_cliff(y): return 6 + (y - WALL_Y0) * 0.55
def right_cliff(y): return AW - 6 - (y - WALL_Y0) * 0.55
def rim_top(x): return 203 + fbm(x * 0.15, 3, 61) * 7 - 3


def is_wall(x, y):
    if y < WALL_Y0 or floor_mask[y, x]:
        return False
    return y >= 214 or x < left_cliff(y) or x > right_cliff(y) or y >= rim_top(x)


vid, vd1, vd2, vdx, vdy = voronoi(13.0, 7, WALL_Y0, AH)
for y in range(WALL_Y0, AH):
    for x in range(AW):
        if not is_wall(x, y):
            continue
        yy2 = y - WALL_Y0
        d = dist_floor[y, x]
        b = BAYER[y % 4, x % 4]
        cell = vid[yy2, x]
        crack = vd2[yy2, x] - vd1[yy2, x] < 0.9
        lit = (-vdx[yy2, x] - vdy[yy2, x]) / 13.0           # horní levá část kamene chytá světlo
        strata = (y * 0.4 + fbm(x * 0.08, y * 0.04, 91) * 6) % 5
        tone = 0.30 + hash01(cell) * 0.20 + lit * 0.16 + b * 0.08 + (0.08 if strata < 1 else 0)
        height = min(d, 16) / 16.0                           # dál od cesty = výš (hřbet)
        c = mix(rgb(0x2E2A40), rgb(0x8C829C), tone + height * 0.10)
        c = mix(c, rgb(0x8A6A6A), 0.18 * fbm(x * 0.03, y * 0.03, 5))   # teplejší žíly skály
        if d <= 5:
            # skalní stěna u cesty: tmavší, svislé rýhy
            streak = fbm(x * 0.6, y * 0.07, 8)
            c = mix(rgb(0x241E34), rgb(0x5A5068), 0.35 + streak * 0.45 + b * 0.1)
            if d == 1:
                c = rgb(0x16121E)
        elif crack:
            c = mix(c, rgb(0x1E1A2A), 0.45)
        # sněhová pole na hřbetech
        if d > 9 and not crack:
            sn = fbm(x * 0.05, y * 0.06, 101) + height * 0.08
            if sn + b * 0.04 > 0.70:
                c = mix(rgb(0xBCC8DC), rgb(0xF4F8FF), min(1.0, (sn - 0.70) * 5 + lit * 0.3))
        # růžové světlo svítání na horních skalách
        c = mix(c, rgb(0xE8A090), 0.22 * max(0.0, 1 - (y - WALL_Y0) / 90))
        img[y, x] = c

# okraj plošiny: hrana skalního útesu proti výhledu
for x in range(AW):
    t = int(rim_top(x))
    if 0 <= t < AH and not floor_mask[t, x] and left_cliff(t) <= x <= right_cliff(t):
        img[t, x] = mix(rgb(0xFFD2B0), rgb(0xC8A0A8), 0.4)    # sluncem ozářená hrana

# podlaha: kamenné plotny cesty, opracované desky plošiny
pid, pd1, pd2, pdx, pdy = voronoi(5.0, 17, LEDGE_Y0 - 14, AH)
for y in range(LEDGE_Y0 - 14, AH):
    for x in range(AW):
        if not floor_mask[y, x]:
            continue
        b = BAYER[y % 4, x % 4]
        if ledge[y, x] and y < LEDGE_Y1:
            tile = ((x // 7) + (y // 5)) % 2
            c = mix(rgb(0x8C8298), rgb(0xA89EB0), 0.3 + tile * 0.25 + b * 0.2)
            if x % 7 == 0 or y % 5 == 0:
                c = rgb(0x5A5068)
            c = mix(c, rgb(0xE8A890), 0.15)
        else:
            yy2 = y - (LEDGE_Y0 - 14)
            cell = pid[yy2, x]
            if pd2[yy2, x] - pd1[yy2, x] < 0.9:
                c = rgb(0x4A4252)                              # spára mezi plotnami
            else:
                lit = (-pdx[yy2, x] - pdy[yy2, x]) / 5.0
                c = mix(rgb(0x6E6474), rgb(0xA89CA8), 0.35 + hash01(cell, 3) * 0.35 + lit * 0.2 + b * 0.1)
        img[y, x] = c

# stín od stěn na podlaze
for y in range(LEDGE_Y0 - 14, AH):
    for x in range(AW):
        if floor_mask[y, x]:
            if y > 0 and x > 0 and (not floor_mask[y - 1, x] or not floor_mask[y, x - 1]):
                img[y, x] = mix(img[y, x], rgb(0x2A2438), 0.45)
            elif y > 1 and not floor_mask[y - 2, x]:
                img[y, x] = mix(img[y, x], rgb(0x2A2438), 0.22)

# ═════════════════════════════════════════════════════════════════════════════
# 7) PŘEDMĚTY: oblouk ke svatyni, mužíci, praporky, drápy a spáleniny, šupina
# ═════════════════════════════════════════════════════════════════════════════
blocks = np.zeros((AH, AW), dtype=bool)

# temný oblouk brány dole (odkud jsme přišli) – stejný styl jako otevřená brána ve svatyni
gx0, gx1, gy0, gy1 = 70, 90, 426, AH - 1
for y in range(gy0 - 8, AH):
    for x in range(gx0 - 4, gx1 + 5):
        dx = x - 80
        top = gy0 - int(math.sqrt(max(0, 100 - dx * dx)))
        if gx0 - 4 <= x <= gx1 + 4 and y >= top - 3 and not (gx0 <= x <= gx1 and y >= top):
            img[y, x] = rgb(0x6A6078) if (x + y) % 3 else rgb(0x4A4258)      # kamenný rám
        if gx0 <= x <= gx1 and y >= top:
            t = (y - top) / 20
            img[y, x] = mix(rgb(0x2A1848), rgb(0x0A0614), t)
            if y == top:
                img[y, x] = rgb(0x8A64C8)
for (sx, sy) in ((76, 430), (84, 433), (80, 436)):
    put(sx, sy, rgb(0xC8A8FF))

# kamenní mužíci (hromádky kamenů) u cesty
def cairn(cx, by):
    stones = [(0, 0, 4), (0, -3, 3), (1, -5, 2), (0, -7, 1)]
    for (dx, dy, r) in stones:
        for y in range(by + dy - 1, by + dy + 2):
            for x in range(cx + dx - r, cx + dx + r + 1):
                e = abs(x - (cx + dx)) / (r + 0.5) + abs(y - (by + dy)) / 1.8
                if e <= 1:
                    c = rgb(0xB8B0C0) if y < by + dy else rgb(0x7A7088)
                    put(x, y, c); blocks[y, x] = True
    for x in range(cx - 5, cx + 6):
        put(x, by + 2, mix(img[by + 2, x], rgb(0x1E1A2A), 0.4))

cairn(49, 278); cairn(26, 236); cairn(134, 236)

# lana s modlitebními praporky přes soutěsku
FLAGS = [rgb(0xE05050), rgb(0xF0C040), rgb(0x50A0E0), rgb(0x60C070), rgb(0xF0F0F0)]
for (x0, y0, x1, y1) in ((14, 300, 146, 312), (18, 372, 150, 364)):
    n = 60
    for i in range(n + 1):
        t = i / n
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t + math.sin(t * math.pi) * 7
        put(int(x), int(y), rgb(0x3A2E26))
        if i % 3 == 0 and 0 < i < n:
            col = FLAGS[(i // 3) % len(FLAGS)]
            for fy2 in range(1, 5):
                for fx2 in range(0, 3 - fy2 // 3):
                    put(int(x) + fx2, int(y) + fy2, mix(col, rgb(0x2A2030), 0.15 if fx2 == 0 else 0))

# rýhy po drápech legendy + spálená skála (pravá stěna u „drapy“)
DX, DY = 128, 336
for k in range(3):
    for i in range(16):
        x = DX + k * 4 - i * 0.35
        y = DY - 8 + i
        put(int(x), int(y), rgb(0x100C14)); put(int(x) + 1, int(y), rgb(0x3A2A30))
        if i % 4 == 0:
            put(int(x) - 1, int(y), rgb(0xFF8A3A))     # doutnající okraj
for y in range(DY - 14, DY + 14):
    for x in range(DX - 12, DX + 18):
        if 0 <= x < AW and not floor_mask[y, x] and math.hypot(x - DX - 3, (y - DY) * 1.2) < 15:
            img[y, x] = mix(img[y, x], rgb(0x1E1418), 0.35)

# dračí šupina na cestě u drápů (třpytivá)
sx, sy = 104, 350
for y in range(sy - 3, sy + 3):
    for x in range(sx - 2, sx + 3):
        if abs(x - sx) / 2.6 + abs(y - sy) / 3.2 <= 1:
            put(x, y, rgb(0x3AAA8A) if (x + y) % 2 else rgb(0x9AF0D0))
put(sx, sy - 3, rgb(0xFFFFFF))

# větrem ohnuté keříky a rampouchy
for (bx, by) in ((20, 250), (140, 262), (26, 410), (138, 330), (14, 330)):
    if floor_mask[by, bx]:
        continue
    for i in range(5):
        put(bx + i // 2, by - i, rgb(0x5A7A4A) if i % 2 else rgb(0x3E5A36))
    put(bx + 3, by - 4, rgb(0x8AAA6A))
for x in range(4, AW - 4, 11):
    y = LEDGE_Y0 - 6
    while y < AH - 2 and not floor_mask[y + 1, x]:
        y += 1
    if y > LEDGE_Y0 and dist_floor[y, x] <= 2:
        for i in range(3):
            put(x, y - 2 - i, rgb(0xE4F0FF) if i == 0 else rgb(0xB8D0EC))

# ═════════════════════════════════════════════════════════════════════════════
# 8) BRÁNA SVĚTŮ – kamenný prstenec s runami, klenák s listem, podstavec s prázdným lůžkem
# ═════════════════════════════════════════════════════════════════════════════
CX, CY = GATE_C
# schodiště / podesta pod bránou
for y in range(CY + 18, CY + 36):
    w = 22 + (y - CY - 18) // 3 * 4
    for x in range(CX - w, CX + w + 1):
        if 0 <= x < AW:
            step = (y - CY - 18) // 3
            c = rgb(0x9A90A8) if (y - CY - 18) % 3 == 0 else rgb(0x6E6480)
            c = mix(c, rgb(0xE8B0A0), 0.12)
            img[y, x] = c
            if y < CY + 30:
                blocks[y, x] = True
# prstenec (nahoře přesahuje přes výhled)
for y in range(CY - GATE_R_OUT - 2, CY + GATE_R_OUT + 3):
    for x in range(CX - GATE_R_OUT - 2, CX + GATE_R_OUT + 3):
        d = math.hypot(x - CX, y - CY)
        if GATE_R_IN <= d <= GATE_R_OUT:
            ang = math.atan2(y - CY, x - CX)
            seg = int((ang + math.pi) / (math.pi / 8))
            joint = abs(((ang + math.pi) / (math.pi / 8)) - round((ang + math.pi) / (math.pi / 8))) < 0.07
            lit = -math.cos(ang + 0.6)                      # světlo zprava shora (slunce)
            c = mix(rgb(0x4A445C), rgb(0xB4AAC0), 0.45 + lit * 0.35 + BAYER[y % 4, x % 4] * 0.15)
            if d > GATE_R_OUT - 1 or d < GATE_R_IN + 1:
                c = rgb(0x221E30)
            elif joint:
                c = rgb(0x2E2A3E)
            elif abs(d - (GATE_R_IN + GATE_R_OUT) / 2) < 0.9 and seg % 2 == 0:
                c = rgb(0x6AE8D8)                           # runy – tyrkysová záře
            put(x, y, c)
            if y >= CY + 8:
                blocks[y, x] = True
        elif d < GATE_R_IN:
            # uvnitř prstence: výhled je za ním, ale zamlžený třpytivou blánou (brána je zapečetěná)
            sw = fbm(x * 0.25 + y * 0.05, y * 0.2, 55)
            img[y, x] = mix(img[y, x], rgb(0xB8A8E8), 0.28 + sw * 0.25)
            if sw > 0.7 and (x + y) % 3 == 0:
                img[y, x] = mix(img[y, x], rgb(0xF0E8FF), 0.5)
# klenák nahoře se znakem listu
for y in range(CY - GATE_R_OUT - 5, CY - GATE_R_OUT + 3):
    for x in range(CX - 4, CX + 5):
        c = rgb(0xA89EB8) if abs(x - CX) < 4 else rgb(0x221E30)
        put(x, y, c)
for (lx, ly) in ((0, -1), (-1, 0), (1, 0), (0, 0), (0, 1), (-1, 1), (1, -1), (0, -2)):
    put(CX + lx, CY - GATE_R_OUT - 1 + ly, rgb(0x4ECB6A))
# podstavec s prázdným lůžkem ve tvaru listu (tady bude Srdce Hvozdu)
for y in range(CY + 12, CY + 22):
    for x in range(CX - 6, CX + 7):
        c = rgb(0x7A7090) if y > CY + 13 else rgb(0xB0A6C0)
        if x in (CX - 6, CX + 6) or y == CY + 21:
            c = rgb(0x221E30)
        put(x, y, c); blocks[y, x] = True
for (lx, ly) in ((0, 0), (0, 1), (-1, 1), (1, 1), (-1, 2), (0, 2), (1, 2), (0, 3), (-2, 2), (2, 1), (0, -1)):
    put(CX + lx, CY + 14 + ly, rgb(0x14101C))                # prázdné lůžko
put(CX, CY + 18, rgb(0x2A5A3A))                              # slabý zelený svit ve dně
# vysoké menhiry po stranách brány
for sx in (CX - 36, CX + 36):
    for y in range(CY - 6, CY + 26):
        w = 3 if y > CY else 2
        for x in range(sx - w, sx + w + 1):
            c = mix(rgb(0x4A445C), rgb(0xA89EB8), 0.5 + (x - sx) * -0.15)
            if x in (sx - w, sx + w):
                c = rgb(0x221E30)
            put(x, y, c); blocks[y, x] = True
    put(sx, CY + 2, rgb(0x6AE8D8)); put(sx, CY + 6, rgb(0x6AE8D8))

# ═════════════════════════════════════════════════════════════════════════════
# 9) MAPA CHŮZE + VÝSTUPY
# ═════════════════════════════════════════════════════════════════════════════
walk = floor_mask & ~blocks
walk[:LEDGE_Y0 - 4, :] = False
# plošina před Branou musí zůstat průchozí k uzlu brana_svetu
bx, by = NODES["brana_svetu"]
walk[by - 3: by + 4, bx - 8: bx + 9] = True

os.makedirs(RES, exist_ok=True)
Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(os.path.join(RES, "sky_pass.png"))

gw, gh = math.ceil(AW / CELL), math.ceil(AH / CELL)
rows = []
for gy in range(gh):
    row = ""
    for gx in range(gw):
        cell = walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL]
        row += "." if cell.mean() >= 0.5 else "#"
    rows.append(row)
# uzly musí ležet na průchozí buňce
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
os.makedirs(WALK, exist_ok=True)
with open(os.path.join(WALK, "sky_pass.txt"), "w") as f:
    f.write(f"# mapa chůze sky_pass: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_skypass.py)\n")
    f.write(f"{AW} {AH} {CELL}\n")
    f.write("\n".join(rows) + "\n")

dbg = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((AW * 4, AH * 4), Image.NEAREST).convert("RGBA")
ov = Image.new("RGBA", dbg.size, (0, 0, 0, 0))
d = ImageDraw.Draw(ov)
for gy, row in enumerate(rows):
    for gx, ch in enumerate(row):
        if ch == ".":
            d.rectangle([gx * CELL * 4, gy * CELL * 4, (gx + 1) * CELL * 4 - 1, (gy + 1) * CELL * 4 - 1], fill=(0, 255, 0, 40))
for (a, b) in EDGES:
    (x0, y0), (x1, y1) = NODES[a], NODES[b]
    d.line([x0 * 4, y0 * 4, x1 * 4, y1 * 4], fill=(255, 255, 0, 200), width=2)
for n, (x, y) in NODES.items():
    d.ellipse([x * 4 - 6, y * 4 - 6, x * 4 + 6, y * 4 + 6], fill=(255, 0, 0, 220))
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "sky_pass_debug.png"))
print("ok", AW, AH)
