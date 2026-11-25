"""
Použití:  python tools/mapgen/gen_station8.py
Výstup:   app/src/main/res/drawable-nodpi/root_camp.png, app/src/main/assets/walk/root_camp.txt,
          tools/mapgen/root_camp_debug.png

Zóna 3 (Akt III – Hlubina), lokace 1: STANICE 8 – předsunutá základna Řádu (176 × 352, pohled 3/4 shora,
docs/adr/0106). Skalní římsa pod stropem dutiny, kam ze stropu sjíždí ocelová šachta s klecí ze Sektoru Hlubiny.
Řád se tu zabydlel: betonová plocha se značením, plechová ubikace s vysílačkou a světlem v okně, nástěnka výprav,
pozorovatelna A s reflektorem do tmy, sodíkové stožáry, ohňová hlídka se sudy a spálenými pahýly kořenů,
spodní terminál lanovky ke kmeni, závora UZÁVĚRA na cestě dolů ke Svahu.
Starší vrstva: ve skále fasáda Lumenců se svítícími znaky. A kořeny Spící: lezou ze skály přes ruinu,
prolamují roh ubikace, zvedají beton a obtáčejí nohu pozorovatelny – tady teprve začínají.
Bez setkání. Živé detaily: InteriorFxView.ROOT_CAMP. NODES / EDGES jsou ZDROJ PRAVDY i pro RootCamp.kt (MAP).
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain")); sys.path.insert(0, os.path.join(HERE, "hlubina"))
import walkmask  # noqa: E402
import light as L, figures as F, archive_parts as A, hideout_parts as P  # noqa: E402
import station_parts as S  # noqa: E402
from pix import Spr, c  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 112
NODES = {
    "kt_klec": (88, 124), "kt_stred": (88, 168), "kt_ruina": (34, 124), "kt_radio": (28, 210), "kt_tabule": (78, 204),
    "kt_vez": (140, 246), "kt_hlidka": (108, 262), "kt_dole": (84, 290), "kt_lanovka": (130, 300),
    "kt_krida": (62, 312), "kt_sestup": (36, 338),
}
EDGES = [("kt_klec", "kt_stred"), ("kt_stred", "kt_ruina"), ("kt_stred", "kt_radio"), ("kt_stred", "kt_tabule"),
         ("kt_radio", "kt_tabule"), ("kt_stred", "kt_vez"), ("kt_vez", "kt_hlidka"), ("kt_stred", "kt_hlidka"),
         ("kt_hlidka", "kt_dole"), ("kt_dole", "kt_lanovka"), ("kt_dole", "kt_krida"), ("kt_krida", "kt_sestup")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
depth = np.zeros((AH, AW))
rng = np.random.default_rng(8)


# ── podlaha: skalní římsa, okraj nad propastí, betonová plocha Řádu ─────────
def edge_y(x):
    """Spodní okraj římsy (pod ním propast); vpravo dole terminál lanovky na výběžku."""
    base = 326 + math.sin(x / 23) * 6 + (S._vn(x, 0, 41, 6) - 0.5) * 6
    if x > 112:
        base = 334 + (S._vn(x, 0, 42, 5) - 0.5) * 4
    if 18 <= x <= 56:
        base = AH + 10                                                  # cesta dolů ke Svahu
    return base


for y in range(WALL_H, AH):
    for x in range(AW):
        ey = edge_y(x)
        if y < ey:
            v = 0.5 + (S._vn(x * 1.2, y, 13, 6) - 0.5) * 0.35 + (S._vn(x, y, 14, 2) - 0.5) * 0.12
            if abs(S._vn(x, y * 1.3, 15, 11) - 0.5) < 0.025:
                v = 0.12
            img[y, x] = S._pick(S.ROCK[1:], max(0.0, min(1.0, v)), x, y)
        elif y < ey + 8:                                                  # čelo římsy (tloušťka skály)
            img[y, x] = S._pick(S.ROCK, 0.35 - (y - ey) * 0.04, x, y)
            block[y, x] = True
        else:                                                             # propast s mlhou
            k = (y - ey) / 40
            col = np.array([12, 16, 30], float) * (1 - min(1, k)) + np.array([4, 6, 14], float) * min(1, k)
            m = S._vn(x, y * 1.4, 17, 9)
            if m > 0.55:
                col += np.array([22, 30, 50]) * (m - 0.55) * 2
            img[y, x, :3] = np.clip(col, 0, 255)
            block[y, x] = True

# betonová plocha (spáry, praskliny) se žlutým značením a kruhem S-8
AP = (14, WALL_H + 2, 162, 272)
P.concrete_floor(img, *AP)
for x in range(AP[0], AP[2]):                                             # okraj plochy, olámaný
    for y in range(AP[3], AP[3] + 3):
        if S._h(x, 1, 3) % 5:
            img[y, x] = P.CONC[1]
for y in range(AP[1] + 10, AP[3] - 6):                                    # žluté vodicí čáry
    for x in (58, 118):
        if (y // 6) % 2 == 0:
            img[y, x] = S.YELLOW[1]
for a in range(0, 360, 4):
    x = int(88 + math.cos(math.radians(a)) * 22); y = int(214 + math.sin(math.radians(a)) * 12)
    img[y, x] = S.YELLOW[1]
tmp = Spr(30, 8); S.stencil(tmp, 2, 1, "S-8", S.YELLOW[1], 2)
for yy in range(tmp.h):
    for xx in range(tmp.w):
        if tmp.a[yy, xx, 3]:
            img[208 + yy, 78 + xx] = tmp.a[yy, xx]

# ── kořeny v podlaze (kreslí se před sprity, jsou to překážky) ──────────────
def bez(p0, p1, p2, n=50):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in np.linspace(0, 1, n)]


# 1) od skály přes levý okraj k rohu ubikace
S.root_path(img, depth, bez((4, WALL_H - 4), (6, 160), (36, 196)), 12, 6, AW, AH, block, lift=0.6, d=0.4)
# 2) zprava pod pozorovatelnu a dál do betonu – konec spálený hlídkou
r2 = bez((180, 182), (150, 196), (118, 226))
S.root_path(img, depth, r2, 13, 7, AW, AH, block, lift=0.8, d=0.42)
# 3) zespodu z propasti zvedá okraj betonu
S.root_path(img, depth, bez((92, 348), (98, 300), (84, 276)), 10, 5, AW, AH, block, lift=0.7, d=0.45)
# tenké výhonky
for (p0, p1, p2, w) in (((30, 196), (52, 214), (50, 236), 3), ((118, 226), (100, 232), (94, 246), 2.5),
                        ((84, 276), (70, 270), (60, 280), 2.5), ((150, 196), (160, 220), (170, 228), 3)):
    S.root_path(img, depth, bez(p0, p1, p2, 30), w, 1.2, AW, AH, None, d=0.5)
# zvednutá deska betonu nad kořenem 3 (šikmá hrana, stín)
for x in range(70, 104):
    for k in range(3):
        y = 270 - k + int(abs(x - 88) * 0.08)
        img[y, x] = P.CONC[5 - k]
    img[273 + int(abs(x - 88) * 0.08), x] = P.CONC[0]

# spálené konce kořenů (černé uhlí a žhnoucí spáry)
for (x, y) in ((118, 226), (84, 276)):
    for yy in range(y - 5, y + 6):
        for xx in range(x - 6, x + 7):
            if (xx - x) ** 2 + ((yy - y) * 1.3) ** 2 < 30 and depth[yy, xx]:
                img[yy, xx] = S._pick(S.CHAR, 0.5 + (S._vn(xx, yy, 5, 2) - 0.5), xx, yy)
                if S._h(xx, yy, 4) % 17 == 0:
                    img[yy, xx] = c(0xE06020)

# ── sprity (z-řazené) ───────────────────────────────────────────────────────
placed = []


def place(spr, cx, by, collide=True, pad=1, shadow=True, foot=None, z=None):
    o = dict(spr=spr, x=int(cx - spr.w // 2), y=int(by - spr.h), z=by if z is None else z, shadow=shadow)
    placed.append(o)
    if collide:
        if foot:
            fw, fh = foot
            block[by - fh:by, int(cx - fw / 2):int(cx + fw / 2)] = True
        else:
            walkmask.silhouette(block, spr.a[:, :, 3], o["x"], o["y"], pad)
    return o


place(S.radio_mast(44), 22, 170, foot=(10, 4))
bar = place(S.barrack(56, 40), 42, 200, foot=(56, 22))
place(S.notice_board(), 78, 196, foot=(28, 5))
for (x, y, sd) in ((104, 178, 1), (122, 180, 2), (113, 188, 3), (150, 150, 4), (70, 142, 5)):
    place(S.army_crate(sd), x, y, pad=0)
place(S.flood_mast(56), 112, 150, foot=(10, 4))
tower = place(S.obs_tower(40, 104), 142, 238, foot=(34, 12))
place(S.sandbags(40, 2), 96, 254, pad=0)
place(S.fuel_drums(3), 126, 268, pad=0)
place(S.burnt_stump(18, 22, 1), 158, 292, pad=0)
place(S.burnt_stump(14, 16, 2), 106, 300, pad=0)
place(S.flood_mast(56), 22, 286, foot=(10, 4))
cab = place(S.cable_station(50, 36), 146, 326, foot=(50, 22))
place(S.barrier(52), 40, 326, foot=(10, 4), collide=False)
block[312:326, 14:20] = True; block[312:326, 60:66] = True                # patky závory

# lana lanovky přes okraj do tmy (k vzdálenému kmeni dole vpravo)
for (x0, y0) in ((136, 302), (140, 300)):
    for t in np.linspace(0, 1, 260):
        x = x0 + (182 - x0) * t; y = y0 + (360 - y0) * t + math.sin(t * math.pi) * 5
        X, Y = int(x), int(y)
        if 0 <= X < AW and 0 <= Y < AH:
            img[Y, X] = F.STEEL[2] if t < 0.5 else F.STEEL[1]

# Hunfreyho křída na betonové patce: šipka dolů a 212
CH = c(0xEAE6DA)
for y in range(300, 312):
    img[y, 70] = CH
for k in range(4):
    img[312 - k, 70 - k] = CH; img[312 - k, 70 + k] = CH
tmp = Spr(16, 6); S.stencil(tmp, 0, 0, "212", CH)
for yy in range(tmp.h):
    for xx in range(tmp.w):
        if tmp.a[yy, xx, 3]:
            img[301 + yy, 74 + xx] = tmp.a[yy, xx]

# ── stíny ───────────────────────────────────────────────────────────────────
floor_px = np.zeros((AH, AW), bool)
for x in range(AW):
    floor_px[WALL_H:int(edge_y(x)), x] = True
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=18, k=0.9)
for o in placed:
    s = o["spr"]
    if not o["shadow"]:
        continue
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.5, 0.3, 0.7)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.55, (4, 4, 8), dither=False, steps=4)

# ── zadní stěna dutiny: ruina, šachta, nápis, potrubí ───────────────────────
wall = S.cavern_wall(AW, WALL_H)
img[:WALL_H] = wall.a
block[:WALL_H + 4, :] = True


def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]


wall_put(S.ruin_facade(54, 70), 6, WALL_H - 70)
wall_put(S.shaft_lift(46, 112), 65, 0)
sg = Spr(56, 16)
S.stencil(sg, 0, 0, "STANICE 8", c(0xCFC8B0)); S.stencil(sg, 0, 8, "HLUBINA", c(0xA89E84))
wall_put(sg, 118, 46)
for y in range(0, WALL_H):                                                # svazek kabelů a potrubí ze stropu
    for (x, col) in ((154, F.STEEL[2]), (155, F.STEEL[1]), (158, S.RUST[2]), (159, S.RUST[1]), (160, S.RUST[0])):
        img[y, x] = col
    if y % 16 == 4:
        img[y, 152:162] = F.STEEL[3]
lamp = Spr(10, 6); lamp.rect(0, 0, 9, 4, F.STEEL[2]); lamp.rect(1, 3, 8, 4, S.SODIUM)
wall_put(lamp, 132, 24)
# kořeny lezou ze skály přes ruinu (kreslí se přes stěnu)
S.root_path(img, depth, bez((-6, 10), (20, 40), (14, WALL_H - 2)), 11, 12, AW, AH, None, d=0.3)
S.root_path(img, depth, bez((44, -4), (52, 40), (28, 70)), 6, 3, AW, AH, None, d=0.32)
S.root_path(img, depth, bez((176, 70), (166, 90), (178, 120)), 7, 7, AW, AH, None, d=0.3)

for o in sorted(placed, key=lambda o: o["z"]):
    s = o["spr"]
    for yy in range(s.h):
        for xx in range(s.w):
            if s.a[yy, xx, 3] and 0 <= o["y"] + yy < AH and 0 <= o["x"] + xx < AW:
                img[o["y"] + yy, o["x"] + xx] = s.a[yy, xx]

# modré houby podél kořenů (jen na kořenech, víc u ruiny)
fungi = []
for _ in range(6000):
    x, y = int(rng.integers(2, AW - 2)), int(rng.integers(20, 330))
    if depth[y, x] and not depth[y, min(AW - 1, x + 2)] and len(fungi) < 40:
        fungi.append((x, y))
        img[y, x, :3] = (150, 210, 255); img[y, x - 1, :3] = (80, 140, 220); img[y + 1, x, :3] = (40, 70, 120)

# ── světlo ──────────────────────────────────────────────────────────────────
def pool(mask, cx, cy, rx, ry, k=1.0):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if 0 <= x < AW and 0 <= y < AH:
                d = math.hypot((x + .5 - cx) / rx, (y + .5 - cy) / ry) + (0.04 if (x + y) % 2 else -0.04)
                v = max(0.0, 1 - d) ** 1.4
                mask[y, x] = max(mask[y, x], v * k)


warm = np.zeros((AH, AW))
pool(warm, 88, 128, 30, 20, 0.9)                                           # lampa nad klecí
pool(warm, 112, 178, 46, 32, 1.0)                                          # stožár u beden
pool(warm, 26, 302, 42, 28, 1.0)                                           # stožár u závory
pool(warm, 54, 196, 22, 12, 0.7)                                           # okno ubikace
pool(warm, 138, 46, 26, 22, 0.6)                                           # nástěnný reflektor na nápis
pool(warm, 150, 318, 30, 16, 0.6)                                          # světlo u lanovky
ember = np.zeros((AH, AW))
pool(ember, 118, 226, 10, 7, 0.8); pool(ember, 84, 276, 9, 6, 0.7); pool(ember, 158, 284, 9, 6, 0.6)
cold = np.zeros((AH, AW))
pool(cold, 33, 72, 40, 36, 1.0)                                            # znaky ruiny
pool(cold, 33, 96, 18, 10, 0.8)
for (x, y) in fungi:
    pool(cold, x, y, 6, 5, 0.6)

dark = np.clip(1.0 - np.maximum(np.maximum(warm, cold * 0.7), ember * 0.6), 0, 1)
L.darken(img, dark, 0.45, (3, 4, 10), dither=True, steps=12)
L.lighten(img, warm, 0.34, (255, 176, 84), steps=12)
L.lighten(img, ember, 0.3, (255, 110, 40), steps=6)
L.lighten(img, cold, 0.22, (110, 180, 255), steps=8)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.5, AW * 0.62, AH * 0.6, k=0.6)
L.darken(img, vg, 0.8, (2, 2, 6), dither=True, steps=10)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "root_camp.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "root_camp.txt"), "w") as f:
    f.write(f"# mapa chůze root_camp: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_station8.py)\n{AW} {AH} {CELL}\n")
    f.write("\n".join(rows) + "\n")

from collections import deque
grid = rows
st = (NODES["kt_klec"][1] // CELL, NODES["kt_klec"][0] // CELL)
seen = {st}; q = deque([st])
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
            dr.rectangle([gx * 6, gy * 6, gx * 6 + 5, gy * 6 + 5], fill=(255, 0, 0, 40))
for (a, b) in EDGES:
    dr.line([NODES[a][0] * 3, NODES[a][1] * 3, NODES[b][0] * 3, NODES[b][1] * 3], fill=(255, 255, 0, 200), width=2)
for n, (x, y) in NODES.items():
    dr.ellipse([x * 3 - 5, y * 3 - 5, x * 3 + 5, y * 3 + 5], fill=(0, 120, 255, 230))
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "root_camp_debug.png"))
print("ok", ("NEDOSAŽITELNÉ: " + ", ".join(bad)) if bad else "všechny uzly dosažitelné", "houby", len(fungi))
print("FUNGI", fungi[:14])
