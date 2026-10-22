"""
Použití:  python tools/mapgen/gen_orchard.py   (vyžaduje Pillow + numpy)
Výstup:   app/src/main/res/drawable-nodpi/quiet_orchard.png (den) a quiet_orchard_night.png (noc), app/src/main/assets/walk/quiet_orchard.txt,
          tools/mapgen/quiet_orchard_debug.png, tools/mapgen/orchard/parts_objects.png

Ztichlý sad (Akt II, docs/adr/0089) – ve dne slunný, v noci měsíční (dvě mapy, stejné rozložení). Stejné skládání jako Popelavá pláň:
  1. pozadí z dlaždic (ashplain/tiles.py se zelenou paletou sadu),
  2. objekty (orchard/sad_objects.py + popelavé stromy z ashplain/objects.py na okraji),
  3. složení podle mřížky 10 × 20, pak NOC: celé přebarvení do měsíčního světla a nakonec
     zdroje světla (zlatá jablka Staré jabloně, lucerna na boudě, měsíc v jezírku).
Hráč přijde zprava branou mezi sloupky (z Popelavé pláně jde doleva) a pokračuje dolů ke Staré jabloni.

NODES / EDGES jsou ZDROJ PRAVDY i pro QuietOrchard.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "ashplain"))
sys.path.insert(0, os.path.join(HERE, "orchard"))
import tiles, objects, pix, sad_objects as S  # noqa: E402
from pix import c  # noqa: E402
from tiles import T  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
WALK = os.path.join(ROOT, "app", "src", "main", "assets", "walk")
COLS, ROWS = 10, 20
AW, AH = COLS * T, ROWS * T
CELL = 2
rng = np.random.default_rng(89)

NODES = {
    "vstup_ze_plane": (152, 80),
    "s1": (80, 80), "s2": (80, 160), "s3": (40, 160), "s4": (120, 160),
    "jezirko_sad": (64, 214), "ule": (120, 216), "studna": (96, 232),
    "s5": (80, 248), "stara_jablon": (52, 292), "trava_sad": (28, 270), "bouda": (124, 300),
}
EDGES = [
    ("vstup_ze_plane", "s1"), ("s1", "s2"), ("s2", "s3"), ("s2", "s4"), ("s3", "jezirko_sad"),
    ("s4", "ule"), ("s2", "studna"), ("s2", "s5"), ("s5", "stara_jablon"), ("s5", "trava_sad"), ("s5", "bouda"),
]

G = [c(0x3E5A2E), c(0x527438), c(0x648A44), c(0x7AA052), c(0xA6C67C)]
TG = [c(0x14200E), c(0x2A4220), c(0x406432), c(0x5E8A48), c(0x86B266)]

# ── 1) podklad ──
path = np.zeros((ROWS, COLS), bool)
for (q0, q1, r0, r1) in ((4, 9, 4, 5), (4, 5, 4, 15), (2, 7, 9, 10)):
    path[r0:r1 + 1, q0:q1 + 1] = True
img = np.zeros((AH, AW, 4), np.uint8)
block = np.zeros((AH, AW), bool)
for r in range(ROWS):
    for q in range(COLS):
        img[r * T:(r + 1) * T, q * T:(q + 1) * T] = tiles.ground(int(rng.choice([0, 0, 1, 2])), G).a
tiles.draw_path(img, path, seed=5)
tiles.draw_tall_grass(img, tiles.grass_patch(28, 272, 20, 14, 7), (4, 254, 52, 290), 7, TG)

# jezírko: tmavá voda, světlejší okraj, rákos a lekníny
PCX, PCY, PRX, PRY = 34, 210, 24, 13
for y in range(PCY - PRY - 2, PCY + PRY + 3):
    for x in range(PCX - PRX - 2, PCX + PRX + 3):
        d = ((x - PCX) / PRX) ** 2 + ((y - PCY) / PRY) ** 2
        if d <= 1:
            img[y, x] = c(0x2E5E7A) if d > 0.75 else c(0x24506C) if (x + y // 2) % 9 else c(0x3A7090)
            block[y, x] = True
        elif d <= 1.18:
            img[y, x] = c(0x8A7A5A) if y > PCY else c(0x6E6448)                 # břeh
for (x, y) in ((18, 204), (46, 214), (28, 218)):
    for (dx, dy) in ((0, 0), (1, 0), (0, 1), (-1, 0)):
        img[y + dy, x + dx] = c(0x4A8A3A)
    img[y, x] = c(0xE8A0C0)                                                  # květ leknínu
for (x, y) in ((12, 200), (14, 198), (56, 206), (58, 204), (54, 220)):
    for k in range(5):
        img[y - k, x] = c(0x5A7A3A) if k < 4 else c(0x6A4A2A)                 # rákos

# ── 2) objekty ──
placed = []


def place(spr, cx, by, foot=None, shade=True):
    placed.append((spr, cx, by, foot, shade))


# tmavý les za zídkou a zídka s vrátky
for i, x in enumerate(range(-4, AW + 16, 18)):
    place(objects.tree(100 + i), x, 30 + (i % 2) * 3, None)
place(S.stone_wall(160), 80, 46, None, False)
# brána vpravo: sloupky nad a pod cestou, vyklopená křídla
place(S.gate_pillar(), 150, 62, (12, 6), False)
place(S.gate_pillar(), 150, 108, (12, 6), False)
place(S.gate_leaf(True), 140, 62, (8, 3), False)
place(S.gate_leaf(True), 140, 108, (8, 3), False)
# ovocné stromy ve dvou řadách podél cesty
for i, (x, y) in enumerate(((26, 92), (52, 96), (26, 136), (52, 140), (108, 136), (134, 140))):
    place(S.fruit_tree(i), x, y, (8, 5))
place(S.ladder(), 40, 136, (4, 2))
place(S.basket(), 62, 146, (8, 3))
place(S.basket(), 116, 112, (8, 3))
# úly, studna
for i, (x, y) in enumerate(((112, 208), (126, 212), (140, 206))):
    place(S.skep(), x, y, (10, 3))
place(S.well(), 96, 228, (16, 6))
# Stará jabloň a bouda
place(S.old_apple_tree(), 80, 300, (12, 6))
place(S.cottage(), 126, 300, (46, 18), False)
# okrajové stromy
for i, y in enumerate(range(70, 340, 20)):
    place(objects.tree(i + 7), 4 + (i % 2) * 3, y, (12, 6))
    if not 54 <= y <= 120:                                      # vstup zprava (z pláně se jde doleva)
        place(objects.tree(i + 77), 156 - (i % 2) * 3, y, (12, 6))
for (x, y) in ((24, 322), (140, 330), (96, 334)):
    place(S.fruit_tree(x), x, y, (8, 5))

for (spr, cx, by, foot, shade) in sorted(placed, key=lambda p: p[2]):
    if shade:
        pix.shadow(img, cx, by - 1, spr.w * 0.38, 3)
    pix.paste(img, spr, int(cx - spr.w // 2), int(by - spr.h))
    if foot:
        fw, fh = foot
        block[max(0, by - fh):by, max(0, int(cx - fw // 2)):min(AW, int(cx + (fw + 1) // 2))] = True

# ── 3) DEN se uloží tak, jak je; NOC: přebarvení do měsíčního světla, pak zdroje světla ──
day = img.copy()
Image.fromarray(day[:, :, :3]).save(os.path.join(RES, "quiet_orchard.png"))
EMISSIVE = {tuple(int(v) for v in col[:3]) for col in S.GOLD + [c(0xFFD27A), c(0xFFF2B0)]}
rgb = img[:, :, :3].astype(np.float64)
lum = rgb @ np.array([0.3, 0.55, 0.15])
grey = rgb * 0.55 + lum[..., None] * 0.45                                 # nejdřív ubrat barvu, ať kámen nemodrá
night = grey * np.array([0.44, 0.52, 0.70]) + np.array([6, 10, 22]) - (lum[..., None] > 170) * np.array([18, 18, 14])
img[:, :, :3] = np.clip(night, 0, 255).astype(np.uint8)
lights = []
for y in range(AH):
    for x in range(AW):
        if tuple(int(v) for v in day[y, x, :3]) in EMISSIVE:
            img[y, x] = day[y, x]
            lights.append((x, y))


def glow(cx, cy, r, col, k):
    for y in range(int(cy - r), int(cy + r) + 1):
        for x in range(int(cx - r), int(cx + r) + 1):
            if 0 <= x < AW and 0 <= y < AH:
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / r
                if d < 1 and tuple(int(v) for v in day[y, x, :3]) not in EMISSIVE:
                    a = (1 - d) ** 2 * k
                    img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array(col) * a, 0, 255).astype(np.uint8)


for (x, y) in lights[::2]:
    glow(x, y, 4, (255, 210, 120), 0.35)
glow(144, 280, 20, (255, 200, 110), 0.5)                                    # lucerna boudy
# měsíc v jezírku
for (dx, dy, col) in ((0, 0, 0xEEF4FF), (1, 0, 0xEEF4FF), (-1, 0, 0xC8D8F0), (2, 0, 0xC8D8F0), (0, -1, 0xC8D8F0), (1, -1, 0xEEF4FF), (0, 1, 0xA8C0E0), (1, 1, 0xA8C0E0)):
    img[206 + dy, 38 + dx] = c(col)
for (x, y) in ((30, 212), (33, 212), (44, 210), (47, 210)):
    img[y, x] = c(0x7898C0)

# ── 4) mapa chůze ──
block[:46, :] = True
block[46:, :T] = True; block[46:, -T:] = True
block[64:96, -T:] = False                                     # vstup zprava mezi sloupky
walk = ~block
Image.fromarray(img[:, :, :3]).save(os.path.join(RES, "quiet_orchard_night.png"))
gw, gh = AW // CELL, AH // CELL
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(gw)) for gy in range(gh)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
os.makedirs(WALK, exist_ok=True)
with open(os.path.join(WALK, "quiet_orchard.txt"), "w") as f:
    f.write(f"# mapa chůze quiet_orchard: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_orchard.py)\n")
    f.write(f"{AW} {AH} {CELL}\n")
    f.write("\n".join(rows) + "\n")
dbg = Image.fromarray(img[:, :, :3]).resize((AW * 4, AH * 4), Image.NEAREST).convert("RGBA")
ov = Image.new("RGBA", dbg.size, (0, 0, 0, 0)); d = ImageDraw.Draw(ov)
for gy, row in enumerate(rows):
    for gx, ch in enumerate(row):
        if ch == "#":
            d.rectangle([gx * CELL * 4, gy * CELL * 4, (gx + 1) * CELL * 4 - 1, (gy + 1) * CELL * 4 - 1], fill=(255, 0, 0, 40))
for (a, b) in EDGES:
    (x0, y0), (x1, y1) = NODES[a], NODES[b]
    d.line([x0 * 4, y0 * 4, x1 * 4, y1 * 4], fill=(255, 255, 0, 200), width=2)
for n, (x, y) in NODES.items():
    d.ellipse([x * 4 - 6, y * 4 - 6, x * 4 + 6, y * 4 + 6], fill=(0, 120, 255, 220))
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "quiet_orchard_debug.png"))
pix.sheet(S.parts(), scale=4, cols=5).save(os.path.join(HERE, "orchard", "parts_objects.png"))
print("ok", AW, AH)
