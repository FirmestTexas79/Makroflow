"""
Použití:  python tools/mapgen/gen_rootcamp.py
Výstup:   app/src/main/res/drawable-nodpi/root_camp.png, app/src/main/assets/walk/root_camp.txt,
          tools/mapgen/root_camp_debug.png

Zóna 3 (Akt III – Hlubina), lokace 1: Tábor výprav (176 × 352, pohled 3/4 shora, docs/adr/0106). Klec ze Sektoru
Hlubiny přistane na obřím bledém kořeni Spící. Na rozšířené vidlici kořene opuštěný tábor Řádu: tři stany výprav
s praporky, studené ohniště (shora padá popel), polní vysílačka s anténou, nástěnka, bedny S-0, modré houby
po okrajích, Hunfreyho křída. Kořen pokračuje dolů do mlhy – Svah bezejmenných. Kolem tma s mlhou a vzdálenými
kořeny, rudé nitě Řádu vedou přes propast ke kmeni.
Bez setkání. Živé detaily: InteriorFxView.ROOT_CAMP. NODES / EDGES jsou ZDROJ PRAVDY i pro RootCamp.kt (MAP).
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import light as L, figures as F  # noqa: E402
from pix import c  # noqa: E402
from hideout_parts import _vn, _h  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
NODES = {"kt_klec": (88, 66), "kt_radio": (64, 104), "kt_stred": (88, 140), "kt_stan1": (44, 168), "kt_stan2": (132, 168),
         "kt_ohniste": (88, 204), "kt_stan3": (46, 246), "kt_tabule": (126, 240), "kt_dole": (88, 284),
         "kt_krida": (100, 306), "kt_sestup": (88, 340)}
EDGES = [("kt_klec", "kt_stred"), ("kt_radio", "kt_stred"), ("kt_stred", "kt_stan1"), ("kt_stred", "kt_stan2"),
         ("kt_stred", "kt_ohniste"), ("kt_ohniste", "kt_stan3"), ("kt_ohniste", "kt_tabule"), ("kt_ohniste", "kt_dole"),
         ("kt_dole", "kt_krida"), ("kt_dole", "kt_sestup")]

BARK = [c(0x24221E), c(0x3E3A34), c(0x5E584E), c(0x827A6C), c(0xA49C8A), c(0xC8C0AC)]
CANVAS = [c(0x3C3A30), c(0x5E5A48), c(0x86806A), c(0xACA68C), c(0xCEC8AA)]
WOOD = [c(0x2A1A10), c(0x4A3020), c(0x6A4630), c(0x8C6444)]
STEEL = F.STEEL
GOLD = F.GOLD
BAYER = F.BAYER
RED = c(0xB0262A)

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.ones((AH, AW), bool)
rng = np.random.default_rng(306)


def px(x, y, col):
    if 0 <= x < AW and 0 <= y < AH:
        img[y, x, :3] = col[:3]


def pick(ramp, v, x, y):
    f = max(0.0, min(0.999, v)) * (len(ramp) - 1)
    i = int(f)
    return ramp[i + 1] if (f - i) > BAYER[y % 4, x % 4] and i + 1 < len(ramp) else ramp[i]


# ── propast: tma, mlha, vzdálené kořeny ─────────────────────────────────────
for y in range(AH):
    for x in range(AW):
        k = y / AH
        col = np.array([14, 18, 32], float) * (1 - k) + np.array([4, 5, 12], float) * k
        m = _vn(x, y * 1.5, 3, 16) * 0.7 + _vn(x, y, 5, 6) * 0.3
        if m > 0.58:
            col = col + np.array([18, 24, 42]) * (m - 0.58) * 2.2
        img[y, x, :3] = np.clip(col, 0, 255)


def far_root(x0, y0, x1, y1, w, shade):
    for t in np.linspace(0, 1, 300):
        x = x0 + (x1 - x0) * t + math.sin(t * 7 + x0) * 4
        y = y0 + (y1 - y0) * t
        for k in range(-int(w), int(w) + 1):
            X, Y = int(x + k), int(y)
            if 0 <= X < AW and 0 <= Y < AH:
                v = shade * (1 - abs(k) / (w + 1) * 0.6)
                img[Y, X, :3] = np.clip(img[Y, X, :3] * 0.3 + np.array([60, 66, 80]) * v, 0, 255)


for (x0, y0, x1, y1, w, s) in ((-10, 0, 30, 352, 4, 0.35), (186, 20, 150, 352, 5, 0.4), (140, -10, 176, 200, 2, 0.25),
                               (10, 120, -20, 352, 3, 0.3)):
    far_root(x0, y0, x1, y1, w, s)

# rudé nitě Řádu přes propast (shora ke kmeni, který je dole)
for i, (x0, x1) in enumerate(((4, 40), (12, 64), (170, 128), (162, 110))):
    for t in np.linspace(0, 1, 500):
        x = x0 + (x1 - x0) * t
        y = -4 + 360 * t + math.sin(t * math.pi) * (6 + i * 3)
        X, Y = int(x), int(y)
        if 0 <= X < AW and 0 <= Y < AH and (X + Y) % 3:
            img[Y, X, :3] = (np.array(RED[:3], float) * 0.6 + img[Y, X, :3] * 0.4).astype(np.uint8)

# ── kořen-plošina: tvar (poloviční šířka podle y) ───────────────────────────
def half_w(y):
    if y < 20:
        return 0
    if y < 80:
        return 30 + (y - 20) * 0.25
    if y < 120:
        return 45 + (y - 80) * 0.85
    if y < 262:
        return 79 - max(0, (y - 230)) * 0.6
    return max(16, 60 - (y - 262) * 0.55)


def center_x(y):
    return 88 + math.sin(y / 61) * 3


surf = np.zeros((AH, AW), bool)
for y in range(AH):
    hw = half_w(y)
    if hw <= 0:
        continue
    cx = center_x(y)
    hw_y = hw + (_vn(0, y, 11, 9) - 0.5) * 6
    for x in range(AW):
        d = abs(x - cx) / hw_y
        if d < 1:
            surf[y, x] = True
            # kůra: válcové stínování (světlo shora zleva), podélné rozpraskané pláty, jemné vlákno
            v = 0.62 - (d ** 1.8) * 0.5 - (x - cx) / max(1, hw_y) * 0.12
            v += (_vn(x * 2.2, y * 0.3, 4, 6) - 0.5) * 0.14
            fiss = abs(_vn(x * 1.6, y * 0.28, 9, 6) - 0.5)
            if fiss < 0.035:
                v = 0.12 + fiss * 3                                   # prasklina mezi pláty
            elif fiss < 0.06:
                v += 0.12                                             # světlá hrana plátu
            img[y, x, :3] = pick(BARK, v, x, y)[:3]
            block[y, x] = d > 0.88
        elif d < 1.12 and y > 30:                                   # boční hrana kořene (tloušťka ve 3/4)
            if x > cx:
                img[y, x, :3] = pick(BARK, 0.18, x, y)[:3]

# spodní hrana plošiny (tloušťka kořene pod povrchem, 3/4 pohled)
for x in range(AW):
    ys = [y for y in range(AH) if surf[y, x]]
    if not ys:
        continue
    for y in range(ys[-1] + 1, min(AH, ys[-1] + 7)):
        if not surf[y, x]:
            img[y, x, :3] = pick(BARK, 0.25 - (y - ys[-1]) * 0.03, x, y)[:3]

# ── klec a přistávací rám nahoře ────────────────────────────────────────────
for y in range(0, 52):                                             # řetězy do tmy
    for xc in (74, 102):
        if y % 4 != 3:
            px(xc, y, STEEL[3] if y % 4 == 0 else STEEL[2])
for y in range(22, 54):                                            # klec: mříž, otevřená dvířka
    for x in range(70, 107):
        if y in (22, 53) or x in (70, 106):
            px(x, y, STEEL[3])
        elif x % 5 == 0 and not (80 <= x <= 96 and y > 30):
            px(x, y, STEEL[1])
    block[y, 66:111] = True
for y in range(31, 53):
    px(97 + (y - 31) // 3, y, STEEL[4])                              # vyklopená dvířka
for x in range(66, 111):                                           # plošina pod klecí
    for y in range(52, 58):
        px(x, y, STEEL[2] if y > 52 else STEEL[3])
for (x, y) in ((76, 34), (77, 34)):
    px(x, y, c(0xA82A24))                                          # rudý zámek – odemčený
for (dx, dy) in ((0, 0), (1, 0), (0, 1)):
    px(78 + dx, 36 + dy, c(0xA82A24))

# ── polní vysílačka s anténou ───────────────────────────────────────────────
for y in range(90, 104):
    for x in range(48, 66):
        edge = y in (90, 103) or x in (48, 65)
        px(x, y, WOOD[0] if edge else pick(c(0x2E3A2A), 0, x, y) if False else c(0x3A4632) if (x + y) % 7 else c(0x2E3828))
for (x, y) in ((52, 94), (53, 94), (52, 95), (53, 95)):
    px(x, y, c(0xE8C050))                                          # kontrolka
for x in range(56, 63):
    px(x, 96, STEEL[3]); px(x, 99, STEEL[2])
for y in range(52, 92):
    px(46, y, STEEL[3])                                             # anténa
for x in range(40, 53):
    px(x, 52 + abs(x - 46), STEEL[2])
block[88:106, 46:68] = True

# ── stany výprav ────────────────────────────────────────────────────────────
def tent(cx, cy, num, tilt=1):
    """Stan 3/4 pohled: štít trojúhelník + střecha, praporek s číslem výpravy a pečetí vah."""
    W2, H = 18, 22
    for y in range(cy - H, cy + 1):
        t = (y - (cy - H)) / H
        hw = W2 * t
        for x in range(int(cx - hw), int(cx + hw) + 1):
            side = (x - cx) / max(1, hw)
            v = 0.62 - side * 0.25 * tilt + (_vn(x, y, 12, 3) - 0.5) * 0.15
            if abs(x - cx) < 4 and y > cy - 10:
                v = 0.08                                            # vchod
            px(x, y, pick(CANVAS, v, x, y))
    for y in range(cy - H, cy + 4):                                 # střecha dozadu
        for x in range(int(cx + W2 * (y - (cy - H)) / H), int(cx + W2 * (y - (cy - H)) / H) + 10):
            if y < cy + 1:
                px(x, y - 6 + (x - cx) // 4, pick(CANVAS, 0.35, x, y))
    for y in range(cy - H - 12, cy - H + 1):                        # tyč s praporkem
        px(cx, y, WOOD[2])
    for y in range(cy - H - 12, cy - H - 5):
        for x in range(cx + 1, cx + 9 - (y - (cy - H - 12)) // 2):
            px(x, y, RED if (x + y) % 5 else c(0x701818))
    for y in range(cy - 2, cy + 3):                                 # stín
        for x in range(cx - W2 - 2, cx + W2 + 6):
            if 0 <= x < AW and 0 <= y < AH and img[y, x, 0] > 60:
                img[y, x, :3] = (img[y, x, :3] * 0.7).astype(np.uint8)
    block[cy - H - 2:cy + 2, cx - W2 - 1:cx + W2 + 8] = True
    # číslo výpravy na plátně (malé, tmavé)
    digits = {"3": ["##.", ".##", "##."], "5": ["##", "#.", ".#"], "7": ["###", "..#", ".#."]}
    for dy, row in enumerate(digits[num]):
        for dx, ch in enumerate(row):
            if ch == "#":
                px(cx - 11 + dx, cy - 12 + dy, c(0x2A2620))


tent(44, 156, "3"); tent(130, 156, "5", -1); tent(46, 234, "7")

# ── ohniště ─────────────────────────────────────────────────────────────────
for a in range(0, 360, 30):
    x = int(88 + math.cos(math.radians(a)) * 9); y = int(198 + math.sin(math.radians(a)) * 5)
    for dx in range(2):
        px(x + dx, y, c(0x5A5650)); px(x + dx, y + 1, c(0x3A3632))
for y in range(195, 202):
    for x in range(81, 96):
        if ((x - 88) / 7) ** 2 + ((y - 198) / 3.5) ** 2 < 1:
            px(x, y, c(0xB8B2A8) if (x * 7 + y) % 3 else c(0x8A847A))   # popel
block[192:204, 78:99] = True

# ── nástěnka ────────────────────────────────────────────────────────────────
for y in range(216, 234):
    for x in range(112, 140):
        px(x, y, WOOD[2] if y in (216, 233) or x in (112, 139) else WOOD[1])
for (x0, y0, w, h) in ((115, 219, 7, 9), (124, 218, 6, 7), (131, 220, 6, 10), (124, 226, 7, 6)):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px(x, y, c(0xD8D2BE) if (y - y0) % 2 == 0 or x == x0 else c(0xB8B2A0))
for x in range(115, 122):
    px(x, 221, RED)                                                 # červeně přeškrtnuté
for y in range(234, 242):
    px(114, y, WOOD[1]); px(137, y, WOOD[1])
block[214:242, 110:142] = True

# ── bedny S-0 ───────────────────────────────────────────────────────────────
for (bx, by) in ((140, 112), (148, 120), (30, 196)):
    for y in range(by, by + 9):
        for x in range(bx, bx + 11):
            edge = y in (by, by + 8) or x in (bx, bx + 10)
            px(x, y, WOOD[0] if edge else WOOD[2] if (y - by) % 3 else WOOD[1])
    px(bx + 3, by + 4, c(0xD8D2BE)); px(bx + 4, by + 4, c(0xD8D2BE)); px(bx + 6, by + 4, c(0xD8D2BE))
    block[by - 1:by + 10, bx - 1:bx + 12] = True

# ── Hunfreyho křída: šipka dolů a 212 ───────────────────────────────────────
CH = c(0xEAE6DA)
for y in range(290, 304):
    px(108, y, CH)
for k in range(5):
    px(108 - k, 304 - k, CH); px(108 + k, 304 - k, CH)
for dy, row in enumerate(["###.#.###", "..#.#...#", "###.#.###", "#...#.#..", "###.#.###"]):
    for dx, ch in enumerate(row):
        if ch == "#":
            px(112 + dx, 292 + dy, CH)

# ── modré houby po okrajích kořene ──────────────────────────────────────────
glow = np.zeros((AH, AW))
fungi = []
for _ in range(60):
    y = int(rng.uniform(70, 330))
    cx, hw = center_x(y), half_w(y)
    if hw < 10:
        continue
    x = int(cx + rng.choice([-1, 1]) * hw * rng.uniform(0.78, 0.95))
    if not (0 <= x < AW) or not surf[y, x]:
        continue
    fungi.append((x, y))
    for (dx, dy, col) in ((0, 0, (90, 150, 230)), (1, 0, (150, 210, 255)), (0, -1, (190, 230, 255)), (-1, 1, (40, 70, 120))):
        px(x + dx, y + dy, col)
    for yy in range(y - 7, y + 8):
        for xx in range(x - 7, x + 8):
            if 0 <= xx < AW and 0 <= yy < AH:
                glow[yy, xx] = max(glow[yy, xx], max(0, 1 - math.hypot(xx - x, yy - y) / 7) * 0.7)
L.lighten(img, glow, 0.32, (120, 190, 255), steps=6)

# ── popel padající na kořen (drobné světlé tečky) ───────────────────────────
for _ in range(90):
    x, y = int(rng.integers(0, AW)), int(rng.integers(0, AH))
    if surf[y, x]:
        img[y, x, :3] = np.clip(img[y, x, :3] * 0.5 + np.array([200, 196, 188]) * 0.5, 0, 255)

# ── světlo: lucerna u klece, studené světlo ze tmy; viněta ──────────────────
warm = np.zeros((AH, AW))
for y in range(AH):
    for x in range(AW):
        warm[y, x] = max(0, 1 - math.hypot((x - 88) / 70, (y - 70) / 50))
L.lighten(img, warm, 0.18, (255, 214, 150), steps=6)
vig = np.zeros((AH, AW)); L.vignette(vig, AW / 2, AH * 0.5, AW * 0.62, AH * 0.6, k=0.6)
L.darken(img, vig, 0.65, (2, 3, 8), dither=True, steps=8)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "root_camp.png"))

CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "root_camp.txt"), "w") as f:
    f.write(f"# mapa chůze root_camp: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_rootcamp.py)\n{AW} {AH} {CELL}\n")
    f.write("\n".join(rows) + "\n")

# kontrola dosažitelnosti uzlů po mapě chůze (BFS)
from collections import deque
grid = [list(r) for r in rows]
start = (NODES["kt_klec"][1] // CELL, NODES["kt_klec"][0] // CELL)
seen = {start}; q = deque([start])
while q:
    gy, gx = q.popleft()
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        ny, nx = gy + dy, gx + dx
        if 0 <= ny < len(grid) and 0 <= nx < len(grid[0]) and grid[ny][nx] == "." and (ny, nx) not in seen:
            seen.add((ny, nx)); q.append((ny, nx))
bad = [n for n, (x, y) in NODES.items() if (y // CELL, x // CELL) not in seen]
dbg = Image.fromarray(img[:, :, :3]).resize((AW * 3, AH * 3), Image.NEAREST).convert("RGBA")
ov = Image.new("RGBA", dbg.size, (0, 0, 0, 0)); dr = ImageDraw.Draw(ov)
for gy, row in enumerate(rows):
    for gx, ch in enumerate(row):
        if ch == "#":
            dr.rectangle([gx * 6, gy * 6, gx * 6 + 5, gy * 6 + 5], fill=(255, 0, 0, 30))
for (a, b) in EDGES:
    dr.line([NODES[a][0] * 3, NODES[a][1] * 3, NODES[b][0] * 3, NODES[b][1] * 3], fill=(255, 255, 0, 200), width=2)
for n, (x, y) in NODES.items():
    dr.ellipse([x * 3 - 5, y * 3 - 5, x * 3 + 5, y * 3 + 5], fill=(0, 120, 255, 230))
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "root_camp_debug.png"))
print("ok", "NEDOSAŽITELNÉ: " + ", ".join(bad) if bad else "všechny uzly dosažitelné", "houby", len(fungi))
print("FUNGI", ", ".join(f"FxGlow({x}, {y}, 4, 4, 0x8AC8FF, {1.5 + (i % 5) * 0.6:.1f}f)" for i, (x, y) in enumerate(fungi[:12])))
