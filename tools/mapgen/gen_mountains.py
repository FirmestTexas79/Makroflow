"""
Použití:  python tools/mapgen/gen_mountains.py   (vyžaduje Pillow)
Výstup:   app/src/main/res/drawable/mountains.png  +  tools/mapgen/mountains_debug.png (s uzly)
statue.png = Král Mlsák zmenšený z mlsak.png na mřížku mapy (22x42, posterizovaný, s obrysem).

Procedurální pixel-art mapa hor pro Makroflow.
Kreslí se v nízkém rozlišení 172x384 ("art pixely") a zvětšuje 4x nearest -> 688x1536,
stejně jako meadow.png. Souřadnice uzlů jsou zlomky šířky/výšky -> přímo do BiomeRegistry.
"""
import math, os, random
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_PNG = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable", "mountains.png")

AW, AH, S = 172, 384, 4
rnd = random.Random(42)

# ── Uzly (zlomky) – musí sedět s BiomeRegistry.MOUNTAINS_GRAPH ──────────────────
N = {
    "vstup_z_meadow": (0.500, 0.960),
    "rozcesti_hory":  (0.500, 0.820),
    "camp":           (0.300, 0.780),
    "kral_mlsak":     (0.500, 0.625),
    "skaly2":         (0.735, 0.655),
    "zapadni_stezka": (0.300, 0.505),
    "mine":           (0.130, 0.470),
    "horni_stezka":   (0.500, 0.335),
    "skaly1":         (0.270, 0.300),
    "cave":           (0.790, 0.345),
    "peak":           (0.500, 0.125),
}
EDGES = [("vstup_z_meadow", "rozcesti_hory"), ("rozcesti_hory", "camp"), ("rozcesti_hory", "kral_mlsak"),
         ("kral_mlsak", "zapadni_stezka"), ("zapadni_stezka", "mine"), ("zapadni_stezka", "horni_stezka"),
         ("kral_mlsak", "skaly2"), ("horni_stezka", "skaly1"), ("horni_stezka", "cave"), ("horni_stezka", "peak")]

def A(n):  # uzel -> art pixel
    x, y = N[n]; return (x * AW, y * AH)

# ── Paleta ──────────────────────────────────────────────────────────────────────
SAND = [(204, 160, 92), (215, 172, 102), (223, 183, 113), (231, 194, 126)]
PATH = (238, 212, 162); PATH_EDGE = (222, 192, 138); PATH_DOT = (214, 182, 128)
SHADOW = (177, 137, 88)
TOP = [(212, 170, 132), (224, 184, 146), (235, 199, 162)]
FACE = [(166, 108, 72), (146, 92, 60), (124, 76, 50), (102, 60, 40)]
OUT = (70, 42, 30); RIM = (246, 214, 180)
STONE = [(150, 146, 134), (172, 168, 154), (196, 192, 176), (118, 114, 106)]

img = Image.new("RGB", (AW, AH))
px = img.load()

def noise2(x, y, sc):
    # levný hodnotový šum
    def h(i, j):
        r = random.Random(i * 73856093 ^ j * 19349663 ^ 7); return r.random()
    xi, yi = int(x // sc), int(y // sc); fx, fy = (x % sc) / sc, (y % sc) / sc
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b, c, d = h(xi, yi), h(xi + 1, yi), h(xi, yi + 1), h(xi + 1, yi + 1)
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy

# ── 1. Písek ────────────────────────────────────────────────────────────────────
BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]
for y in range(AH):
    for x in range(AW):
        v = noise2(x, y, 18) * 0.7 + noise2(x, y, 6) * 0.3
        v = v * 3.0 + (BAYER[y % 4][x % 4] / 16 - 0.5) * 0.9
        px[x, y] = SAND[max(0, min(3, int(v)))]

# závěje navátého písku (vlnité tmavší/světlejší linky)
for y in range(AH):
    for x in range(AW):
        w = noise2(x, y * 2.2, 22) * 10 + y * 0.18 + math.sin(x * 0.09) * 1.5
        f = w - math.floor(w)
        if f < 0.07: px[x, y] = SAND[0]
        elif f < 0.13: px[x, y] = SAND[3]

# ── 2. Cesty (maska) ────────────────────────────────────────────────────────────
def seg_dist(p, a, b):
    ax, ay = a; bx, by = b; x, y = p
    dx, dy = bx - ax, by - ay; L = dx * dx + dy * dy
    t = 0 if L == 0 else max(0, min(1, ((x - ax) * dx + (y - ay) * dy) / L))
    return math.hypot(x - ax - t * dx, y - ay - t * dy)

path = [[False] * AW for _ in range(AH)]
pathd = [[99.0] * AW for _ in range(AH)]
PLAZA_C = (0.5 * AW, 0.575 * AH); PLAZA_R = 19
for y in range(AH):
    for x in range(AW):
        d = min(seg_dist((x, y), A(a), A(b)) for a, b in EDGES)
        wob = (noise2(x, y, 7) - 0.5) * 2.2
        pathd[y][x] = d - wob
        path[y][x] = d - wob < 4.2

def plaza_d(x, y):  # osmiúhelník
    dx, dy = abs(x - PLAZA_C[0]), abs(y - PLAZA_C[1]) * 1.25
    return max(dx, dy, (dx + dy) * 0.72)

# ── 3. Skály: půdorysné masky + vyvýšení ────────────────────────────────────────
def blob_mask(cx, cy, rx, ry, rough=0.35, seed=0):
    r = random.Random(seed); k = [r.uniform(0, 6.28) for _ in range(4)]
    def inside(x, y):
        ang = math.atan2((y - cy) / ry, (x - cx) / rx)
        rr = 1 + rough * (0.5 * math.sin(3 * ang + k[0]) + 0.3 * math.sin(5 * ang + k[1]) + 0.2 * math.sin(9 * ang + k[2]))
        return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 < rr * rr
    return inside

# tier1 = nižší terasa, tier2 = vyšší (leží na tier1)
tier1_blobs, tier2_blobs = [], []
# levá stěna
for i, yy in enumerate(range(-10, AH + 20, 34)):
    tier1_blobs.append(blob_mask(4 + rnd.uniform(-4, 6), yy, 24 + rnd.uniform(0, 10), 26, 0.3, 100 + i))
    tier2_blobs.append(blob_mask(-6 + rnd.uniform(-3, 4), yy + 8, 16 + rnd.uniform(0, 6), 22, 0.3, 200 + i))
# pravá stěna
for i, yy in enumerate(range(-20, AH + 20, 32)):
    tier1_blobs.append(blob_mask(AW - 4 + rnd.uniform(-6, 4), yy, 26 + rnd.uniform(0, 10), 26, 0.3, 300 + i))
    tier2_blobs.append(blob_mask(AW + 6 + rnd.uniform(-4, 3), yy + 6, 18 + rnd.uniform(0, 6), 22, 0.3, 400 + i))
# horní hřeben (vrchol)
for i, xx in enumerate(range(-10, AW + 20, 26)):
    tier1_blobs.append(blob_mask(xx, 10 + rnd.uniform(-3, 5), 22, 30, 0.3, 500 + i))
    tier2_blobs.append(blob_mask(xx + 6, -2 + rnd.uniform(-2, 4), 18, 22, 0.3, 600 + i))
# vnitřní výchozy
tier1_blobs += [blob_mask(118, 128, 22, 13, 0.35, 701), blob_mask(52, 196, 17, 11, 0.35, 702),
                blob_mask(128, 206, 12, 8, 0.4, 703), blob_mask(36, 262, 13, 9, 0.35, 704),
                blob_mask(140, 268, 16, 13, 0.35, 705), blob_mask(86, 64, 10, 7, 0.4, 706)]
tier2_blobs += [blob_mask(122, 124, 12, 7, 0.35, 801)]
# spodní okraj (les skal kolem vstupu)
tier1_blobs += [blob_mask(22, AH - 6, 50, 22, 0.3, 901), blob_mask(AW - 18, AH - 4, 52, 22, 0.3, 902)]

H1, H2 = 12, 9  # výška stěn v art px

# průchody: cesty + plošina + okolí uzlů
FACE_NODES = {"cave": 0, "mine": 0}  # uzly u stěny – volno jen pod nimi
def clearance(x, y):
    if pathd[y][x] < 8.5 or plaza_d(x, y) < PLAZA_R + 5: return True
    for n, (fx, fy) in N.items():
        nx, ny = fx * AW, fy * AH
        if n in FACE_NODES:
            if abs(x - nx) < 10 and ny - 3 < y < ny + 10: return True
        elif math.hypot(x - nx, y - ny) < 11: return True
    return False

C = [[clearance(x, y) for x in range(AW)] for y in range(AH)]

def footprint(blobs, H):
    M = [[any(b(x, y) for b in blobs) for x in range(AW)] for y in range(AH)]
    # vyřízni: pixel půdorysu (x,y) se kreslí v řádcích y-H..y
    for y in range(AH):
        for x in range(AW):
            if M[y][x] and any(0 <= y - k < AH and C[y - k][x] for k in range(0, H + 1)):
                M[y][x] = False
    return M

# ruční výplně stěn nad vstupy do jeskyně a dolu (aby byl vchod ve skále)
def add_face_block(M, n, w, h):
    nx, ny = int(N[n][0] * AW), int(N[n][1] * AH)
    for y in range(ny - h, ny - 2):
        for x in range(nx - w, nx + w + 1):
            if 0 <= x < AW and 0 <= y < AH: M[y][x] = True

M1 = footprint(tier1_blobs, H1)
add_face_block(M1, "cave", 14, 30); add_face_block(M1, "mine", 14, 30)
M2 = footprint(tier2_blobs, H1 + H2)
M2 = [[M2[y][x] and M1[y][x] for x in range(AW)] for y in range(AH)]  # tier2 leží na tier1
# tier2 ustoupí od hrany tier1 (terasa)
M2 = [[M2[y][x] and all(0 <= y + k < AH and M1[y + k][x] for k in range(1, 6)) for x in range(AW)] for y in range(AH)]

rock = [[None] * AW for _ in range(AH)]  # co je v pixelu nakresleno: ('top',t) / ('face',t,dy)

def render_tier(M, base_h, h, tier):
    # stěna: pro každý spodní okraj půdorysu kresli sloupec výšky h pod horní plochou
    for y in range(AH):
        for x in range(AW):
            if not M[y][x]: continue
            top_y = y - base_h - h
            if 0 <= top_y < AH: rock[top_y][x] = ("top", tier)
            if y + 1 >= AH or not M[y + 1][x]:  # jižní hrana -> čelo skály
                for k in range(h):
                    yy = y - base_h - k
                    if 0 <= yy < AH:
                        cur = rock[yy][x]
                        if cur is None or cur[0] == "face" or cur[1] < tier or True:
                            rock[yy][x] = ("face", tier, k)

render_tier(M1, 0, H1, 1)
# horní plocha tier1 musí překrýt čela tier1 uvnitř (sloupce výše) – druhý průchod jen tops
for y in range(AH):
    for x in range(AW):
        if M1[y][x] and (y + 1 < AH and M1[y + 1][x]):
            top_y = y - H1
            if 0 <= top_y < AH: rock[top_y][x] = ("top", 1)
render_tier(M2, H1, H2, 2)
for y in range(AH):
    for x in range(AW):
        if M2[y][x] and (y + 1 < AH and M2[y + 1][x]):
            top_y = y - H1 - H2
            if 0 <= top_y < AH: rock[top_y][x] = ("top", 2)

# odstranění drobných útržků skal (artefakty po vyříznutí cest)
seen=[[False]*AW for _ in range(AH)]
for y0 in range(AH):
    for x0 in range(AW):
        if rock[y0][x0] is None or seen[y0][x0]: continue
        comp=[]; st=[(x0,y0)]; seen[y0][x0]=True
        while st:
            x,y=st.pop(); comp.append((x,y))
            for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
                xx,yy=x+dx,y+dy
                if 0<=xx<AW and 0<=yy<AH and not seen[yy][xx] and rock[yy][xx] is not None:
                    seen[yy][xx]=True; st.append((xx,yy))
        if len(comp)<60:
            for x,y in comp: rock[y][x]=None

# ── 4. Stín skal na písku (vpravo dole) ─────────────────────────────────────────
is_rock = [[rock[y][x] is not None for x in range(AW)] for y in range(AH)]
for y in range(AH):
    for x in range(AW):
        if not is_rock[y][x] and any(0 <= y - k < AH and 0 <= x - k < AW and is_rock[y - k][x - k] for k in (1, 2, 3)):
            if not (0 <= y - 1 and is_rock[y - 1][x] and False):
                px[x, y] = SHADOW

# ── 5. Cesty a plošina ──────────────────────────────────────────────────────────
for y in range(AH):
    for x in range(AW):
        if is_rock[y][x]: continue
        d = pathd[y][x]
        if d < 3.0: px[x, y] = PATH
        elif d < 4.2: px[x, y] = PATH_EDGE
        if d < 3.0 and rnd.random() < 0.035: px[x, y] = PATH_DOT

# osmiboká dlážděná plošina krále
for y in range(AH):
    for x in range(AW):
        d = plaza_d(x, y)
        if d < PLAZA_R:
            tile = ((x // 5) + (y // 4)) % 2
            c = STONE[1] if tile else STONE[2]
            if x % 5 == 0 or y % 4 == 0: c = STONE[0]
            px[x, y] = c
        elif d < PLAZA_R + 1.2: px[x, y] = STONE[3]
        elif d < PLAZA_R + 2.2: px[x, y] = SHADOW

# ── 6. Textura skal ────────────────────────────────────────────────────────────
for y in range(AH):
    for x in range(AW):
        r = rock[y][x]
        if r is None: continue
        if r[0] == "top":
            v = noise2(x + r[1] * 50, y, 5) * 2.6 + (BAYER[y % 4][x % 4] / 16 - 0.5) * 0.8
            px[x, y] = TOP[max(0, min(2, int(v)))]
        else:
            k = r[2]; h = H1 if r[1] == 1 else H2
            band = 0 if k > h * 0.66 else (1 if k > h * 0.33 else 2)
            if noise2(x * 3.0, y * 0.4, 3) > 0.62: band = min(3, band + 1)
            if (x + (y // 3)) % 7 == 0: band = min(3, band + 1)  # svislé praskliny
            px[x, y] = FACE[band]

# obrys + světlý lem hran
for y in range(AH):
    for x in range(AW):
        r = rock[y][x]
        if r is None: continue
        nb = lambda xx, yy: rock[yy][xx] if 0 <= xx < AW and 0 <= yy < AH else ("top", 9)
        if any(nb(x + dx, y + dy) is None for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px[x, y] = OUT
        elif r[0] == "top":
            below = nb(x, y + 1)
            above = nb(x, y - 1)
            if below and below[0] == "face": px[x, y] = RIM
            elif above and above[0] == "face": px[x, y] = OUT  # hrana spodní terasy za horní stěnou
        elif r[0] == "face":
            below = nb(x, y + 1)
            if below and below[0] == "top": px[x, y] = OUT

# ── 7. Rekvizity ────────────────────────────────────────────────────────────────
d = ImageDraw.Draw(img)

def P(n): x, y = A(n); return int(round(x)), int(round(y))

# balvany
def boulder(cx, cy, r):
    for y in range(cy - r - 1, cy + r + 2):
        for x in range(cx - r - 1, cx + r + 2):
            dd = math.hypot((x - cx) / 1.15, y - cy)
            if dd < r:
                c = TOP[2] if (x - cx) + (y - cy) < -r * 0.4 else (TOP[1] if (x - cx) + (y - cy) < r * 0.3 else FACE[1])
                px[x, y] = c
            elif dd < r + 1: px[x, y] = OUT
    px[cx + r, cy + r // 2 + 1] = SHADOW

def tuft(cx, cy):
    for dx, h in ((-1, 2), (0, 3), (1, 2), (2, 1)):
        for k in range(h): px[cx + dx, cy - k] = (132, 140, 70) if k else (98, 104, 52)

# encounter místa: kupa balvanů + suchý keř
for n in ("skaly1", "skaly2"):
    x, y = P(n)
    boulder(x - 7, y - 9, 5); boulder(x + 5, y - 11, 6); boulder(x - 1, y - 16, 4)
    tuft(x + 10, y - 4); tuft(x - 12, y - 3)

# tábor: stan + ohniště
cx, cy = P("camp")
tx, ty = cx - 10, cy - 6
for yy in range(12):
    half = yy // 1.4
    for xx in range(int(-half), int(half) + 1):
        col = (176, 70, 52) if (xx // 2) % 2 == 0 else (206, 196, 170)
        if abs(xx) >= half - 0.5: col = OUT
        px[tx + xx, ty - 12 + yy] = col
for xx in range(-9, 10): px[tx + xx, ty] = OUT
for yy in range(4): px[tx, ty - 1 - yy] = (40, 28, 20)          # vchod
fx, fy = cx + 8, cy - 5
for a in range(0, 360, 45):
    px[fx + round(3 * math.cos(math.radians(a))), fy + round(2 * math.sin(math.radians(a)))] = STONE[3]
px[fx, fy] = (250, 170, 60); px[fx, fy - 1] = (240, 110, 40); px[fx - 1, fy] = (230, 90, 40); px[fx + 1, fy] = (250, 200, 90)
for xx in range(-4, 5): px[cx + xx - 2, cy + 3] = (122, 82, 50)  # kláda
px[cx - 7, cy + 3] = OUT; px[cx + 3, cy + 3] = OUT

# jeskyně: tmavý oblouk ve skále
def arch(cx, top, w, h, color, frame=None):
    for yy in range(h):
        for xx in range(-w, w + 1):
            if yy < w and xx * xx + (w - yy) ** 2 > w * w: continue
            px[cx + xx, top + yy] = color
    if frame:
        for yy in range(h): px[cx - w - 1, top + yy] = frame; px[cx + w + 1, top + yy] = frame
        for xx in range(-w - 1, w + 2): px[cx + xx, top - 1] = frame
cx, cy = P("cave"); arch(cx, cy - 15, 6, 13, (34, 22, 18))
for yy in range(6, 13): px[cx - 2, cy - 15 + yy] = (52, 34, 26)
# důl: dřevěná výztuž + koleje
cx, cy = P("mine")
arch(cx, cy - 14, 5, 12, (30, 20, 16))
WOOD, WOOD_D = (150, 98, 56), (104, 66, 38)
for yy in range(12): px[cx - 6, cy - 14 + yy] = WOOD; px[cx + 6, cy - 14 + yy] = WOOD; px[cx - 7, cy - 14 + yy] = WOOD_D; px[cx + 7, cy - 14 + yy] = WOOD_D
for xx in range(-8, 9): px[cx + xx, cy - 15] = WOOD; px[cx + xx, cy - 16] = WOOD_D
for k in range(0, 22):
    rx, ry = cx + k, cy - 2 + k // 5
    if 0 <= rx < AW:
        px[rx, ry - 1] = (96, 96, 100); px[rx, ry + 2] = (96, 96, 100)
        if k % 3 == 0: px[rx, ry] = WOOD_D; px[rx, ry + 1] = WOOD_D

# vrchol: mužík z kamenů + praporek
cx, cy = P("peak")
for i, (w, yy) in enumerate(((4, 0), (3, -3), (2, -5), (1, -7))):
    for xx in range(-w, w + 1): px[cx + xx, cy - 8 + yy] = STONE[1] if xx < 0 else STONE[0]
    px[cx - w - 1, cy - 8 + yy] = OUT; px[cx + w + 1, cy - 8 + yy] = OUT
for yy in range(10): px[cx + 1, cy - 16 - yy] = WOOD_D
for yy in range(4):
    for xx in range(2, 8 - yy): px[cx + xx, cy - 25 + yy] = (196, 60, 48)

# rozcestník u vstupu
cx, cy = P("rozcesti_hory")
sx, sy = cx + 11, cy + 2
for yy in range(8): px[sx, sy - yy] = WOOD_D
for xx in range(-1, 6): px[sx + xx, sy - 8] = WOOD; px[sx + xx, sy - 7] = WOOD; px[sx + xx, sy - 9] = OUT
px[sx + 6, sy - 8] = WOOD; px[sx + 6, sy - 7] = OUT

# podstavec + socha krále (sprite zmenšený z mlsak.png)
statue = Image.open(os.path.join(HERE, "statue.png")).convert("RGBA")
statue = statue.resize((16, 31), Image.NEAREST)
sw, sh = statue.size
pcx, pcy = int(PLAZA_C[0]), int(PLAZA_C[1]) + 2   # pata podstavce
for yy in range(7):                                # kamenný podstavec
    for xx in range(-9, 10):
        c = STONE[2] if yy < 2 else (STONE[1] if xx < 0 else STONE[0])
        if abs(xx) == 9 or yy == 6: c = OUT
        px[pcx + xx, pcy - yy] = c
for xx in range(-10, 11): px[pcx + xx, pcy + 1] = SHADOW
img.paste(statue.convert("RGB"), (pcx - sw // 2, pcy - 7 - sh), statue)

# balvany u paty skal
placed = 0
for _ in range(4000):
    if placed >= 16: break
    x, y = rnd.randrange(6, AW - 6), rnd.randrange(20, AH - 6)
    if is_rock[y][x] or not is_rock[y - 4][x] or pathd[y][x] < 8 or plaza_d(x, y) < PLAZA_R + 6: continue
    if any(math.hypot(x - A(n)[0], y - A(n)[1]) < 14 for n in N): continue
    boulder(x, y, rnd.choice((2, 3, 3, 4))); placed += 1

# detaily na horních plochách skal: praskliny, kamínky, trsy
for _ in range(260):
    x, y = rnd.randrange(3, AW - 4), rnd.randrange(3, AH - 4)
    r = rock[y][x]
    if not r or r[0] != "top": continue
    if not all(rock[y + dy][x + dx] and rock[y + dy][x + dx][0] == "top" for dx in (-2, 0, 2) for dy in (-2, 0, 2)): continue
    k = rnd.random()
    if k < 0.45:
        for i in range(rnd.choice((2, 3, 4))): px[x + i, y + (i // 2)] = TOP[0] if i else FACE[0]
    elif k < 0.8:
        px[x, y] = FACE[0]; px[x + 1, y] = TOP[2]; px[x, y + 1] = TOP[0]
    else:
        tuft(x, y)

# drobnosti: trsy trávy, kamínky
for _ in range(150):
    x, y = rnd.randrange(3, AW - 3), rnd.randrange(8, AH - 3)
    if is_rock[y][x] or pathd[y][x] < 6 or plaza_d(x, y) < PLAZA_R + 3: continue
    if any(math.hypot(x - A(n)[0], y - A(n)[1]) < 12 for n in N): continue
    if rnd.random() < 0.45: tuft(x, y)
    else:
        px[x, y] = FACE[2]; px[x + 1, y] = FACE[1]; px[x + 1, y + 1] = SHADOW

big = img.resize((AW * S, AH * S), Image.NEAREST)
big.save(OUT_PNG, optimize=True)

# debug náhled s uzly
dbg = big.copy(); dd = ImageDraw.Draw(dbg)
for n, (fx, fy) in N.items():
    x, y = fx * AW * S, fy * AH * S
    dd.ellipse([x - 8, y - 8, x + 8, y + 8], outline=(255, 0, 0), width=3)
    dd.text((x + 10, y - 6), n, fill=(255, 0, 0))
dbg.save(os.path.join(HERE, "mountains_debug.png"))
print("ok")
