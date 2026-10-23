"""
Použití:  python tools/mapgen/gen_canyon.py   (vyžaduje Pillow + numpy)
Výstup:   app/src/main/res/drawable-nodpi/echo_canyon.png, app/src/main/assets/walk/echo_canyon.txt,
          tools/mapgen/echo_canyon_debug.png, tools/mapgen/canyon/parts.png

Ozvěnový kaňon (Akt II, docs/adr/0090) – odbočka vpravo z Popelavé pláně. Stejné skládání jako pláň a sad:
  1. podklad z dlaždic: dno kaňonu, plošiny podle VÝŠKOVÉ MŘÍŽKY H a skalní čela se slojemi pod nimi,
  2. objekty: visutý most přes průrvu, hnízdo, jeskyňka, obří kosti, kameny a suché keře,
  3. složení, na konci Ozvěnová stěna – skalní čelo s vyrytými dlaněmi a čárkami jmen.
Hráč přijde zleva (z pláně jde doprava) a sestupuje dolů do kotliny pod stěnou.

NODES / EDGES jsou ZDROJ PRAVDY i pro EchoCanyon.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import walkmask  # noqa: E402
sys.path.insert(0, os.path.join(HERE, "ashplain"))
sys.path.insert(0, os.path.join(HERE, "canyon"))
import pix, objects, canyon_parts as K  # noqa: E402
from pix import c  # noqa: E402

T = K.T
ROWS_H = [
    "1111111122", "1111111122", "0000000022", "0000000122", "2221000122", "2221000012",
    "2210000012", "2100000112", "2100001122", "2100001222", "2100000122", "2000000012",
    "2000000012", "2100000012", "2100000012", "2100222012", "2000222002", "2000222002",
    "2000000002", "2000000002", "2000000002", "2100000012", "2210000122", "2222222222",
]
H = np.array([[int(ch) for ch in row] for row in ROWS_H])
ROWS, COLS = H.shape
AW, AH = COLS * T, ROWS * T
CELL = 2

NODES = {
    "vstup_z_plane": (6, 58), "k1": (80, 58), "hnizdo": (72, 88), "k2": (88, 120),
    "jeskynka": (104, 186), "rimsa": (40, 168), "most": (80, 196), "k3": (80, 226), "koryto": (52, 232),
    "k4": (40, 264), "k5": (120, 264), "ozvenova_stena": (80, 328), "kosti": (44, 344),
}
EDGES = [
    ("vstup_z_plane", "k1"), ("k1", "hnizdo"), ("k1", "k2"), ("k2", "jeskynka"), ("k2", "rimsa"), ("k2", "most"),
    ("most", "k3"), ("k3", "koryto"), ("k3", "k4"), ("k3", "k5"), ("k4", "ozvenova_stena"), ("k5", "ozvenova_stena"),
    ("ozvenova_stena", "kosti"),
]

rng = np.random.default_rng(90)
img = np.zeros((AH, AW, 4), np.uint8)
block = np.zeros((AH, AW), bool)
# ── 1) dno a plošiny ──
for r in range(ROWS):
    for q in range(COLS):
        img[r * T:(r + 1) * T, q * T:(q + 1) * T] = K.floor_tile(int(rng.choice([0, 0, 1, 2]))).a
# suché koryto: pruh oblázků přes dno
for y in range(214, 246):
    for x in range(16, 144):
        if abs(y - (230 + 6 * np.sin(x / 14))) < 7 - abs(x - 80) / 30:
            img[y, x] = K.FLOOR[1] if (x + y) % 3 else K.FLOOR[4] if (x * y) % 7 == 0 else K.FLOOR[0]
K.draw_cliffs(img, H, block)
# Ozvěnová stěna: čelo plošiny uprostřed dole (řádky 18–19)
K.echo_marks(img, 66, 290, 44)
# průrva přes dno (řádky 11–12)
for y in range(188, 206):
    for x in range(AW):
        if block[y, x]:
            continue
        edge = abs(y - 197) - (2 * np.sin(x / 5) + 6)
        if edge < 0:
            img[y, x] = c(0x0E0806) if edge < -2 else K.FACE[1]
            block[y, x] = True
        elif edge < 1.5:
            img[y, x] = K.FACE[3]

# ── 2) objekty ──
placed = []


def place(spr, cx, by, foot=None, shade=True):
    placed.append((spr, cx, by, foot, shade))


place(K.rope_bridge(40, 30), 80, 212, None, False)
place(K.nest(), 54, 82, None, False)
place(K.cave_mouth(), 104, 174, None, False)
place(K.bones(), 46, 368, (40, 6), False)
for (x, y, big) in ((34, 160, True), (48, 164, False), (126, 128, False), (24, 300, True), (132, 312, False), (118, 352, True)):
    place(objects.rock(big), x, y, (10 if big else 8, 4))
for (x, y) in ((100, 56), (120, 238), (30, 330), (128, 290)):
    place(objects.bush(x + y), x, y, (10, 4))
for (x, y) in ((24, 26), (120, 24), (138, 362)):
    place(objects.dead_tree(x, mushrooms=False), x, y, None)

for (spr, cx, by, foot, shade) in sorted(placed, key=lambda p: p[2]):
    if shade:
        pix.shadow(img, cx, by - 1, spr.w * 0.38, 3)
    pix.paste(img, spr, int(cx - spr.w // 2), int(by - spr.h))
    if foot:                                            # silueta, ne jen pata (walkmask.py, ADR 0094)
        walkmask.silhouette(block, spr.a[:, :, 3], int(cx - spr.w // 2), int(by - spr.h))
block[186:210, 66:94] = False                       # most je průchozí

# ── 3) mapa chůze ──
walk = ~block
os.makedirs(os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi"), exist_ok=True)
Image.fromarray(img[:, :, :3]).save(os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi", "echo_canyon.png"))
gw, gh = AW // CELL, AH // CELL
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(gw)) for gy in range(gh)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "walk", "echo_canyon.txt"), "w") as f:
    f.write(f"# mapa chůze echo_canyon: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_canyon.py)\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "echo_canyon_debug.png"))
pix.sheet([K.floor_tile(0), K.floor_tile(1), K.top_tile(0), K.rope_bridge(40, 30), K.nest(), K.cave_mouth(), K.bones()], scale=4, cols=7).save(os.path.join(HERE, "canyon", "parts.png"))
print("ok", AW, AH)
