"""Zapomenutý háj – skrytá lokace ve Hvozdu (docs/adr/0046).

Měsícem zalitá mýtina hluboko v trní: kamenný kruh s menhiry a třemi kameny s vytesanými
obrazy, uprostřed studánka, ve které se odráží hvězdy, nahoře prastarý tis s modrými houbami,
oltář druidů a hrob posledního Strážce brány.

Výstupy:
  app/src/main/res/drawable-nodpi/hidden_grove.png   (160×300)
  app/src/main/assets/walk/hidden_grove.txt          (buňka 2 px)
  tools/mapgen/hidden_grove_debug.png                (uzly a průchozí plocha)

NODES / EDGES jsou ZDROJ PRAVDY i pro cave/GroveMap.kt.
"""
import math, os
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
WALK = os.path.join(ROOT, "app", "src", "main", "assets", "walk")
AW, AH = 160, 300
CELL = 2
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5
rng = np.random.default_rng(1717)

NODES = {
    "vstup_z_hvozdu": (80, 290), "g1": (80, 250), "kruh_jih": (80, 214), "studanka": (80, 194),
    "mural_1": (42, 170), "mural_2": (118, 170), "mural_3": (60, 132), "g2": (80, 120),
    "oltar": (80, 100), "hrob": (122, 104),
}
EDGES = [
    ("vstup_z_hvozdu", "g1"), ("g1", "kruh_jih"), ("kruh_jih", "studanka"), ("kruh_jih", "mural_1"),
    ("kruh_jih", "mural_2"), ("mural_1", "mural_3"), ("mural_3", "g2"), ("mural_2", "g2"),
    ("g2", "oltar"), ("oltar", "hrob"),
]
RING_C = (80, 165)          # střed kamenného kruhu
POOL = (80, 165, 21, 16)    # studánka: střed, poloosy
MURALS = {"mural_1": (24, 160), "mural_2": (136, 160), "mural_3": (46, 116)}   # vytesané kameny (střed paty)
ALTAR = (80, 86)            # oltářní kámen
GRAVE = (124, 94)           # hrobový kámen
TREE = (80, 30)             # prastarý tis

img = np.zeros((AH, AW, 3))
walk = np.zeros((AH, AW), dtype=bool)
block = np.zeros((AH, AW), dtype=bool)


def rgb(h):
    return np.array([(h >> 16) & 255, (h >> 8) & 255, h & 255], dtype=float)


def mix(a, b, t):
    return a + (b - a) * float(np.clip(t, 0, 1))


def put(x, y, c):
    if 0 <= x < AW and 0 <= y < AH:
        img[y, x] = c


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


def hash01(k, s=0):
    v = (k * 2654435761 + s * 97531) & 0xFFFFFFFF
    v = ((v ^ (v >> 15)) * 2246822519) & 0xFFFFFFFF
    return ((v ^ (v >> 13)) & 0xFFFF) / 65535.0


def ell(x, y, cx, cy, rx, ry):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2


# ── tvar mýtiny: kruh + plošina u oltáře + ulička trním k východu ─────────────
def clearing(x, y):
    wob = (fbm(x * 0.12, y * 0.12, 3) - 0.5) * 0.35
    if ell(x, y, RING_C[0], RING_C[1], 66, 55) < 1 + wob:
        return True
    if ell(x, y, 80, 80, 58, 40) < 1 + wob:
        return True
    if 215 <= y and abs(x - 80 - math.sin(y * 0.07) * 3) < 15 + wob * 10:
        return True
    return False


# ═════════════════════════════════════════════════════════════════════════════
# 1) PŮDA mýtiny – tmavý mech ve svitu měsíce, 2) KORUNY kolem
# ═════════════════════════════════════════════════════════════════════════════
moss_d, moss_m, moss_l = rgb(0x0E2A22), rgb(0x1A4436), rgb(0x2E6A4E)
leaf_d, leaf_m, leaf_l, leaf_rim = rgb(0x06141A), rgb(0x0C2A2E), rgb(0x16444A), rgb(0x3E8A86)

# koruny okolního lesa: překrývající se kupole listí (nižší = blíž, kreslí se přes vyšší)
blobs = []
for _ in range(900):
    bx, by = rng.random() * (AW + 16) - 8, rng.random() * (AH + 16) - 8
    blobs.append((by, bx, 5 + rng.random() * 5))
blobs.sort()
barr = np.array(blobs)
for y in range(AH):
    for x in range(AW):
        b = BAYER[y % 4, x % 4]
        if clearing(x, y):
            moon = max(0.0, 1 - ell(x, y, 72, 150, 70, 110)) * 0.55
            n = fbm(x * 0.18, y * 0.18, 11)
            c = mix(moss_d, moss_m, 0.3 + n * 0.6 + b * 0.25)
            c = mix(c, moss_l, moon * (0.6 + n * 0.5))
            if hash01(x * 991 + y, 5) < 0.04:
                c = mix(c, rgb(0x5AA07A), 0.5)
            img[y, x] = c
            continue
        block[y, x] = True
        d = np.hypot(barr[:, 1] - x, barr[:, 0] - y)
        inside = np.nonzero(d <= barr[:, 2])[0]
        if len(inside) == 0:
            img[y, x] = leaf_d * 0.6
            continue
        k = inside[-1]                               # nejnižší (nejbližší) kupole
        cy, cx, r = barr[k]
        lx, ly = (x - cx) / r, (y - cy) / r
        t = d[k] / r
        light = 0.62 - (lx * 0.45 + ly * 0.55) - t * 0.25 + b * 0.35
        leaf = hash01(x * 57 + y * 131, 3)
        if leaf < 0.1:
            light += 0.25
        c = mix(leaf_d, leaf_m, light)
        if light > 0.78:
            c = mix(leaf_m, leaf_l, (light - 0.78) * 2.6)
        if t > 0.9:
            c = leaf_d * 0.75                        # tmavý okraj kupole
        near = any(clearing(x + dx, y + dy) for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2)))
        if near and light > 0.5:
            c = mix(c, leaf_rim, 0.45)
        img[y, x] = c

# stín pod okrajem korun (koruny vrhají stín dovnitř mýtiny)
for y in range(AH):
    for x in range(AW):
        if not block[y, x]:
            for k in (1, 2, 3):
                if y - k >= 0 and block[y - k, x]:
                    img[y, x] = img[y, x] * (0.55 + 0.12 * k)
                    break

# ═════════════════════════════════════════════════════════════════════════════
# 3) STEZKA z plochých kamenů a kruhová cesta kolem studánky
# ═════════════════════════════════════════════════════════════════════════════
stone_d, stone_m, stone_l = rgb(0x2A3844), rgb(0x46586A), rgb(0x6E8496)


def flagstone(x, y, spacing=6, seed=3):
    """Ploché kameny (Voronoi) prorostlé mechem."""
    gx, gy = x / spacing, y / spacing
    best, second, bid = 9e9, 9e9, 0
    for oy in (-1, 0, 1):
        for ox in (-1, 0, 1):
            cx, cy = math.floor(gx) + ox, math.floor(gy) + oy
            px = (cx + hash01(cx * 131 + cy * 71, seed)) * spacing
            py = (cy + hash01(cx * 17 + cy * 313, seed + 1)) * spacing
            d = math.hypot(x - px, y - py)
            if d < best:
                second, best, bid = best, d, cx * 1000 + cy
            elif d < second:
                second = d
    if second - best < 0.9:
        return mix(moss_d, moss_m, 0.5)          # spára zarostlá mechem
    t = hash01(bid, seed + 7)
    c = mix(stone_d, stone_m, 0.45 + t * 0.4)
    moon = max(0.0, 1 - ell(x, y, 72, 150, 70, 110))
    return mix(c, stone_l, moon * 0.45 + (0.15 if best < 1.5 else 0))


for y in range(AH):
    for x in range(AW):
        if block[y, x]:
            continue
        onpath = False
        # stezka od východu ke kruhu a od kruhu k oltáři
        if y >= 205 and abs(x - 80 - math.sin(y * 0.07) * 3) < 7:
            onpath = True
        if 96 <= y <= 125 and abs(x - 80) < 7:
            onpath = True
        # kruhová cesta
        e = ell(x, y, RING_C[0], RING_C[1], 44, 38)
        if 0.72 < e < 1.28:
            onpath = True
        # plošina před oltářem
        if ell(x, y, 80, 98, 20, 9) < 1:
            onpath = True
        if onpath and fbm(x * 0.3, y * 0.3, 21) > 0.28:
            img[y, x] = flagstone(x, y)

# ═════════════════════════════════════════════════════════════════════════════
# 4) STUDÁNKA HVĚZD
# ═════════════════════════════════════════════════════════════════════════════
px_, py_, prx, pry = POOL
water_d, water_m = rgb(0x06122E), rgb(0x10285A)
for y in range(py_ - pry - 3, py_ + pry + 4):
    for x in range(px_ - prx - 3, px_ + prx + 4):
        e = ell(x, y, px_, py_, prx, pry)
        if e < 1:
            t = (y - (py_ - pry)) / (2 * pry)
            c = mix(water_m, water_d, t * 0.8 + BAYER[y % 4, x % 4] * 0.2)
            img[y, x] = c
            block[y, x] = True
        elif e < 1.35:
            # kamenný lem studánky
            ang = math.atan2(y - py_, x - px_)
            seg = int((ang + math.pi) / (math.pi / 9))
            c = mix(stone_m, stone_l, 0.3 + hash01(seg, 4) * 0.5)
            if abs(((ang + math.pi) / (math.pi / 9)) - round((ang + math.pi) / (math.pi / 9))) < 0.08 or e > 1.3:
                c = stone_d
            img[y, x] = c
            block[y, x] = True
# hvězdy a měsíc v odrazu
for i in range(26):
    a = rng.random() * 2 * math.pi; r = math.sqrt(rng.random()) * 0.85
    x = int(px_ + math.cos(a) * r * prx); y = int(py_ + math.sin(a) * r * pry)
    put(x, y, mix(rgb(0xBFD8FF), rgb(0xFFFFFF), rng.random()))
for y in range(py_ - 7, py_ - 1):
    for x in range(px_ + 3, px_ + 11):
        d = math.hypot(x - (px_ + 7), (y - (py_ - 4)) * 1.2)
        if d < 3.4:
            put(x, y, rgb(0xF4F0D8) if d < 2.6 else rgb(0xB8C8E0))
# souhvězdí „parohy“ – náznak, že hvězdy tu tvoří obraz
for (x, y) in [(66, 160), (69, 157), (72, 159), (71, 154), (74, 152), (64, 155), (62, 152)]:
    put(x, y, rgb(0x9FF0FF))

# ═════════════════════════════════════════════════════════════════════════════
# 5) MENHIRY kamenného kruhu (3 z nich s vytesanými obrazy)
# ═════════════════════════════════════════════════════════════════════════════
rune = rgb(0x6AF0E0)


def menhir(cx, by, w, h, carved=False, seed=0):
    for y in range(by - h, by + 1):
        t = (by - y) / h
        half = w / 2 * (1 - max(0, t - 0.75) * 1.6)       # zúžení nahoře
        for x in range(int(cx - half - 1), int(cx + half + 2)):
            u = (x - cx) / max(0.5, half)
            if abs(u) > 1.05:
                continue
            light = 0.55 - u * 0.4 + BAYER[y % 4, x % 4] * 0.2
            c = mix(stone_d, stone_m, light)
            if light > 0.75:
                c = mix(stone_m, stone_l, (light - 0.75) * 2)
            if abs(u) > 0.9:
                c = rgb(0x121A22)
            # mech na vrcholu a na návětrné straně
            if (t > 0.8 and hash01(x * 7 + y, seed) < 0.6) or (u < -0.4 and hash01(x + y * 13, seed + 1) < 0.18):
                c = mix(moss_m, moss_l, hash01(x * 3 + y, seed + 2))
            put(x, y, c)
            block[y, x] = True
    # stín u paty
    for x in range(int(cx - w / 2 - 2), int(cx + w / 2 + 4)):
        for y in (by + 1, by + 2):
            if 0 <= x < AW and y < AH and not block[y, x]:
                img[y, x] = img[y, x] * 0.6
    if carved:
        # vytesaný obraz: rámeček a svítící znaky
        top = by - h + 5
        for y in range(top, top + h // 2):
            for x in (int(cx - w / 2 + 2), int(cx + w / 2 - 2)):
                put(x, y, rgb(0x1E2A34))
        for k in range(4):
            yy = top + 2 + k * 3
            for x in range(int(cx - w / 2 + 3), int(cx + w / 2 - 2)):
                if hash01(x * 5 + yy + seed * 31, 9) < 0.55:
                    put(x, yy, rune)
        # záře kolem kamene
        for y in range(by - h - 4, by + 4):
            for x in range(int(cx - w), int(cx + w + 1)):
                if 0 <= x < AW and 0 <= y < AH and not block[y, x]:
                    d = math.hypot((x - cx) / w, (y - (by - h / 2)) / h)
                    if d < 1.1:
                        img[y, x] = mix(img[y, x], rgb(0x2A8A84), (1.1 - d) * 0.35)


ring_stones = []
for k in range(10):
    a = -math.pi / 2 + k * (2 * math.pi / 10) + 0.12
    cx = RING_C[0] + math.cos(a) * 58
    by = RING_C[1] + math.sin(a) * 50 + 8
    # vynechat místa pro stezky (jih, sever) a vytesané kameny
    if abs(cx - 80) < 16 and (by > 200 or by < 130):
        continue
    if any(math.hypot(cx - mx, by - my) < 16 for mx, my in MURALS.values()):
        continue
    ring_stones.append((cx, by))
for i, (cx, by) in enumerate(ring_stones):
    menhir(int(cx), int(by), 7, 15, seed=i)
for i, (name, (cx, by)) in enumerate(MURALS.items()):
    menhir(cx, by, 12, 24, carved=True, seed=40 + i)

# ═════════════════════════════════════════════════════════════════════════════
# 6) PRASTARÝ TIS, OLTÁŘ A HROB STRÁŽCE
# ═════════════════════════════════════════════════════════════════════════════
bark_d, bark_m, bark_l = rgb(0x1E2226), rgb(0x3E464E), rgb(0x7A8690)
tx, ty = TREE
# kmen – široký, rozvětvený do kořenů
for y in range(0, 72):
    hw = 13 + max(0, y - 40) * 0.9 + math.sin(y * 0.3) * 1.2
    for x in range(int(tx - hw - 1), int(tx + hw + 2)):
        u = (x - tx) / hw
        if abs(u) > 1:
            continue
        groove = math.sin((x - tx) * 1.1 + math.sin(y * 0.2) * 2.5)
        c = mix(bark_d, bark_m, 0.55 - u * 0.4 + groove * 0.2)
        if groove > 0.8 and u < 0.2:
            c = mix(c, bark_l, 0.5)
        if abs(u) > 0.94:
            c = rgb(0x0A0C10)
        put(x, y, c)
        block[y, x] = True
# větve do koruny nad kmenem
for side in (-1, 1):
    for k in range(30):
        x = int(tx + side * (4 + k * 1.3)); y = int(14 - k * 0.45 + math.sin(k * 0.4) * 1.5)
        for w in range(max(1, 3 - k // 10)):
            if 0 <= y - w < AH:
                put(x, y - w, mix(bark_d, bark_m, 0.5))
# dutina v kmeni se slabou modrou září
for y in range(22, 42):
    for x in range(tx - 5, tx + 6):
        d = ell(x, y, tx, 32, 5, 10)
        if d < 1:
            put(x, y, mix(rgb(0x0A1C2A), rgb(0x2E7AA0), (1 - d) * 0.7))
# kořeny do stran
for side in (-1, 1):
    for r in range(3):
        x0, y0 = tx + side * (10 + r * 5), 62 + r * 3
        for k in range(26):
            x = int(x0 + side * k * 1.4); y = int(y0 + k * 0.35 + math.sin(k * 0.5 + r) * 1.2)
            for w in range(3 - k // 10):
                put(x, y + w, mix(bark_d, bark_m, 0.4 + (1 - w) * 0.2))
                if 0 <= y + w < AH and 0 <= x < AW:
                    block[y + w, x] = True
# modré svítící houby na kořenech a kolem mýtiny
glow_c = rgb(0x7ADCFF)
mush = [(58, 70), (104, 72), (46, 76), (116, 66), (36, 110), (130, 128), (22, 196), (140, 204),
        (64, 262), (98, 272), (70, 228), (92, 236)]
for (mx, my) in mush:
    for y in range(my - 5, my + 6):
        for x in range(mx - 5, mx + 6):
            d = math.hypot(x - mx, y - my)
            if d < 5.5 and 0 <= x < AW and 0 <= y < AH:
                img[y, x] = mix(img[y, x], rgb(0x2A7A9A), (5.5 - d) / 5.5 * 0.45)
    for (ox, oy) in ((0, 0), (2, 1), (-2, 1)):
        put(mx + ox, my + oy - 1, glow_c); put(mx + ox - 1, my + oy - 1, rgb(0x3AA8E0))
        put(mx + ox + 1, my + oy - 1, rgb(0x3AA8E0)); put(mx + ox, my + oy, rgb(0xC8E8F0))
# oltář – plochý kámen na dvou podstavcích, vytesané parohy
ax, ay = ALTAR
for y in range(ay - 5, ay + 6):
    for x in range(ax - 14, ax + 15):
        top = y < ay - 1
        c = mix(stone_m, stone_l, 0.5) if top else mix(stone_d, stone_m, 0.5 + BAYER[y % 4, x % 4] * 0.3)
        if x in (ax - 14, ax + 14) or y in (ay - 5, ay + 5):
            c = rgb(0x121A22)
        put(x, y, c); block[y, x] = True
for (lx, ly) in [(-6, -3), (-5, -4), (-4, -3), (-3, -4), (-5, -2), (6, -3), (5, -4), (4, -3), (3, -4), (5, -2),
                 (-1, -3), (0, -3), (1, -3), (0, -2)]:
    put(ax + lx, ay + ly, rune)
# hrob strážce – ležící deska se znakem parohů a mechem
gx, gy = GRAVE
for y in range(gy - 5, gy + 6):
    for x in range(gx - 7, gx + 8):
        c = mix(stone_d, stone_m, 0.55 - (x - gx) * 0.03 + BAYER[y % 4, x % 4] * 0.3)
        if abs(x - gx) == 7 or abs(y - gy) == 5:
            c = rgb(0x121A22)
        if hash01(x * 11 + y * 3, 77) < 0.22:
            c = mix(moss_m, moss_l, 0.4)
        put(x, y, c); block[y, x] = True
for (lx, ly) in [(-3, -2), (-2, -3), (-1, -2), (1, -2), (2, -3), (3, -2), (0, -1), (0, 0), (0, 1)]:
    put(gx + lx, gy + ly, rgb(0x9AD8E8))
# lucerny z kamenů po stranách oltáře
for sx in (ax - 24, ax + 24):
    for y in range(ay - 2, ay + 8):
        for x in range(sx - 2, sx + 3):
            put(x, y, mix(stone_d, stone_m, 0.5)); block[y, x] = True
    put(sx, ay + 1, rgb(0xFFE89A)); put(sx, ay + 2, rgb(0xFFC860))
    for y in range(ay - 8, ay + 12):
        for x in range(sx - 8, sx + 9):
            d = math.hypot(x - sx, y - (ay + 1))
            if d < 9 and 0 <= x < AW and 0 <= y < AH and not block[y, x]:
                img[y, x] = mix(img[y, x], rgb(0xC8A060), (9 - d) / 9 * 0.35)

# ═════════════════════════════════════════════════════════════════════════════
# 7) VSTUP – tunel z trní, 8) SVĚTLUŠKY a paprsky měsíce
# ═════════════════════════════════════════════════════════════════════════════
thorn = rgb(0x1A1016)
for y in range(262, AH):
    for x in range(AW):
        dx = abs(x - 80)
        if 13 <= dx <= 22 and hash01(x * 31 + y * 7, 5) < 0.55:
            put(x, y, thorn if hash01(x + y, 6) < 0.6 else rgb(0x3A2030))
        if dx < 13 and y > 285 and hash01(x * 13 + y, 8) < 0.12:
            put(x, y, rgb(0x2A1822))
# trny (šikmé čárky) a červené bobule trní
for i in range(40):
    x = int(80 + (15 + rng.random() * 8) * (1 if i % 2 else -1)); y = int(262 + rng.random() * 38)
    put(x, y, rgb(0x5A2A3A)); put(x + (1 if i % 2 else -1), y - 1, rgb(0x5A2A3A))
    if i % 5 == 0:
        put(x, y + 1, rgb(0xC03040))
# paprsky měsíce – šikmé pruhy světla přes mýtinu
for y in range(AH):
    for x in range(AW):
        if block[y, x]:
            continue
        s = (x * 0.6 + y) % 46
        if s < 7 and 60 < y < 240:
            img[y, x] = mix(img[y, x], rgb(0xB8E0F0), 0.07 * (1 - abs(s - 3.5) / 3.5))
# světlušky
for i in range(34):
    x, y = int(rng.random() * AW), int(40 + rng.random() * 240)
    if block[y, x]:
        continue
    put(x, y, rgb(0xE8FF9A))
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if 0 <= x + dx < AW and 0 <= y + dy < AH:
            img[y + dy, x + dx] = mix(img[y + dy, x + dx], rgb(0xC8F070), 0.35)

# ═════════════════════════════════════════════════════════════════════════════
# 9) MAPA CHŮZE + VÝSTUPY
# ═════════════════════════════════════════════════════════════════════════════
for y in range(AH):
    for x in range(AW):
        walk[y, x] = clearing(x, y) and not block[y, x]
# okraje trní u vstupu neprůchozí
walk[262:, :] &= (np.abs(np.arange(AW) - 80) < 12)[None, :]

out = np.clip(img, 0, 255).astype(np.uint8)
Image.fromarray(out, "RGB").save(os.path.join(RES, "hidden_grove.png"))

cols, rows = AW // CELL, AH // CELL
lines = ["# mapa chůze hidden_grove: obrázek 160x300, buňka 2 px (tools/mapgen/gen_grove.py)", f"{AW} {AH} {CELL}"]
for r in range(rows):
    row = ""
    for c in range(cols):
        cellw = walk[r * CELL:(r + 1) * CELL, c * CELL:(c + 1) * CELL]
        row += "." if cellw.mean() >= 0.5 else "#"
    lines.append(row)
open(os.path.join(WALK, "hidden_grove.txt"), "w").write("\n".join(lines) + "\n")

dbg = Image.fromarray(out, "RGB").resize((AW * 3, AH * 3), Image.NEAREST)
d = ImageDraw.Draw(dbg)
for (a, b) in EDGES:
    (x0, y0), (x1, y1) = NODES[a], NODES[b]
    d.line((x0 * 3, y0 * 3, x1 * 3, y1 * 3), fill=(255, 0, 255), width=1)
for n, (x, y) in NODES.items():
    ok = walk[y, x]
    d.ellipse((x * 3 - 4, y * 3 - 4, x * 3 + 4, y * 3 + 4), outline=(0, 255, 0) if ok else (255, 0, 0), width=2)
dbg.save(os.path.join(HERE, "hidden_grove_debug.png"))
print("ok", AW, AH, "nodes on walk:", {n: bool(walk[y, x]) for n, (x, y) in NODES.items()})
