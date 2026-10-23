"""
Použití:  python tools/mapgen/gen_arena_hall.py
Výstup:   app/src/main/res/drawable-nodpi/arena_hall.png, app/src/main/assets/walk/arena_hall.txt,
          tools/mapgen/arena_hall_debug.png, tools/mapgen/arena/parts.png

Vstupní hala Arény v Ozvěně (Akt II, docs/adr/0092) – interiér 176 × 288 skládaný jako ostatní lokace:
  1. podklad: leštěná dlažba, rudý koberec od vchodu k bráně do arény, světelné kruhy pod pochodněmi,
  2. zadní stěna (kámen nad dřevěným obložením) s branou do arény, prapory, pochodně; boční zdi se sloupy,
  3. objekty (arena/hall_parts.py): Síň šampionů se třemi rámy (portréty doplní aplikace podle žebříčku),
     pult velitele cechu s velitelem, nástěnka s plakátem HLEDÁ SE (Drakirra z jejího spritu),
     tréninkový kout, lavice, květiny, sametový provaz.
NODES / EDGES jsou ZDROJ PRAVDY i pro ArenaHall.kt (MAP).
"""
import glob, math, os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import walkmask  # noqa: E402
sys.path.insert(0, os.path.join(HERE, "arena")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
sys.path.insert(0, os.path.join(HERE, "orchard"))
import hall_parts as P, pix  # noqa: E402
from pix import c, Spr  # noqa: E402
from pixart import K, STN, stones  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 288
WALL_H = 76
NODES = {
    "vychod": (88, 278), "stred": (88, 200), "brana_areny": (88, 92), "velitel": (132, 182),
    "sin_slavy": (46, 166), "hledani": (36, 262), "trenink": (136, 270),
}
EDGES = [("vychod", "stred"), ("stred", "brana_areny"), ("stred", "velitel"), ("stred", "sin_slavy"),
         ("stred", "hledani"), ("stred", "trenink")]

img = np.zeros((AH, AW, 4), np.uint8)
block = np.zeros((AH, AW), bool)
P.floor(img, 0, 0, AW, AH)
P.carpet(img, 76, 100, WALL_H, AH)
em = P.floor_emblem(16)
pix.paste(img, em, 88 - em.w // 2, 200 - em.h // 2)
# vchod dole: práh a denní světlo zvenku
for y in range(AH - 5, AH):
    for x in range(AW):
        if 70 <= x < 106:
            img[y, x] = c(0xF4E6C0) if y > AH - 3 else c(0xD8C49A)
        else:
            img[y, x] = P.WALLST[2] if y > AH - 4 else P.WALLST[4]
# světelné kruhy pod pochodněmi a zlatavé světlo od brány
def light(cx, cy, r, col, k):
    for y in range(max(0, int(cy - r)), min(AH, int(cy + r))):
        for x in range(max(0, int(cx - r)), min(AW, int(cx + r))):
            d = math.hypot((x - cx) / 1.3, y - cy) / r
            if d < 1:
                a = (1 - d) ** 2 * k
                img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array(col) * a, 0, 255).astype(np.uint8)
for tx in (54, 122):
    light(tx, WALL_H + 10, 26, (255, 200, 120), 0.35)
light(88, WALL_H + 6, 30, (255, 230, 160), 0.3)
light(88, AH, 34, (255, 244, 210), 0.35)

# zadní stěna
wall = P.back_wall(AW, WALL_H)
pix.paste(img, wall, 0, 0)
for y in range(WALL_H, WALL_H + 4):                                  # stín stěny na podlaze
    img[y, :, :3] = (img[y, :, :3] * (0.7 + (y - WALL_H) * 0.07)).astype(np.uint8)
block[:WALL_H + 2, :] = True

placed = []
def place(spr, cx, by, foot="auto", z=None):
    placed.append((spr, cx, by, by if z is None else z))
    if foot:                                            # silueta, ne jen pata (walkmask.py, ADR 0094)
        walkmask.silhouette(block, spr.a[:, :, 3], int(cx - spr.w // 2), int(by - spr.h), 2)

place(P.arena_door(), 88, WALL_H + 6, foot=None, z=1)
for bx in (28, 148):
    place(P.banner(), bx, 52, foot=None, z=1)
for tx in (54, 122):
    place(P.torch(), tx, 40, foot=None, z=1)
# boční zdi se sloupy (pohled shora: hrana zdi, mezi sloupy výklenky)
for y in range(WALL_H, AH):
    for x in list(range(0, 8)) + list(range(AW - 8, AW)):
        e = x if x < 8 else AW - 1 - x
        img[y, x] = P.WALLST[1] if e < 2 else P.WALLST[3] if e < 6 else P.WALLST[2]
        block[y, x] = True
for py in range(WALL_H + 40, AH, 56):
    for side in (0, 1):
        col = Spr(14, 40)
        stones(col, 1, 12, 6, 37, h=5, w=(4, 6), seed=py + side)
        col.rect(0, 0, 13, 5, STN[4]); col.hline(0, 13, 5, STN[1]); col.rect(0, 37, 13, 39, STN[2])
        place(pix.outline(col, K), 7 if side == 0 else AW - 8, py, foot=(14, 10))
# Síň šampionů vlevo, pult velitele vpravo
hof = P.hall_of_fame()
place(hof, 46, 150, foot=(80, 24))
place(P.rope_barrier(76), 46, 162, foot=(76, 3))
place(P.commander(), 132, 152, foot=None, z=150)
place(P.desk(), 132, 168, foot=(64, 16))
# nástěnka HLEDÁ SE u vchodu vlevo
src = Image.open(glob.glob(os.path.join(ROOT, "app", "src", "main", "res", "drawable*", "makromon_19_drakirra.png"))[0]).convert("RGBA")
d = src.crop(src.getbbox()); d.thumbnail((30, 24), Image.LANCZOS)
place(P.wanted_board(np.array(d)), 38, 262, foot=(56, 6))
# tréninkový kout vpravo dole
place(P.weapon_rack(), 154, 234, foot=(28, 6))
place(P.dummy(), 124, 254, foot=(8, 4)); place(P.dummy(), 150, 264, foot=(8, 4))
# lavice a květiny
place(P.bench_long(), 46, 196, foot=(34, 5)); place(P.bench_long(), 124, 196, foot=(34, 5))
for (x, y) in ((16, 100), (160, 100)):
    place(P.plant(), x, y, foot=(10, 5))
place(P.armor_statue(), 62, 286, foot=(16, 8)); place(P.armor_statue(), 114, 286, foot=(16, 8))
place(P.guild_flag(), 104, 162, foot=None)                       # vlajka za levým koncem stolu
for (spr, cx, by, z) in sorted(placed, key=lambda p: p[3]):
    pix.paste(img, spr, int(cx - spr.w // 2), int(by - spr.h))
block[WALL_H - 4:WALL_H + 8, 70:107] = False                          # schody k bráně
block[AH - 6:, 70:107] = False

# výstupy
Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "arena_hall.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "arena_hall.txt"), "w") as f:
    f.write(f"# mapa chůze arena_hall: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_arena_hall.py)\n{AW} {AH} {CELL}\n")
    f.write("\n".join(rows) + "\n")
dbg = Image.fromarray(img[:, :, :3]).resize((AW * 3, AH * 3), Image.NEAREST).convert("RGBA")
ov = Image.new("RGBA", dbg.size, (0, 0, 0, 0)); dr = ImageDraw.Draw(ov)
for gy, row in enumerate(rows):
    for gx, ch in enumerate(row):
        if ch == "#":
            dr.rectangle([gx * 6, gy * 6, gx * 6 + 5, gy * 6 + 5], fill=(255, 0, 0, 45))
for (a, b) in EDGES:
    dr.line([NODES[a][0] * 3, NODES[a][1] * 3, NODES[b][0] * 3, NODES[b][1] * 3], fill=(255, 255, 0, 200), width=2)
for n, (x, y) in NODES.items():
    dr.ellipse([x * 3 - 5, y * 3 - 5, x * 3 + 5, y * 3 + 5], fill=(0, 120, 255, 230))
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "arena_hall_debug.png"))
# rámy portrétů v art px mapy (pro ArenaHall.kt)
hx0, hy0 = 46 - hof.w // 2, 150 - hof.h
print("ok", AW, AH, "rámy:", [(hx0 + fx, hy0 + fy) for (fx, fy) in P.FRAMES])
