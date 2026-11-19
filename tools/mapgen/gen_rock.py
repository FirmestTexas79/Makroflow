"""
Použití:  python tools/mapgen/gen_rock.py
Výstup:   app/src/main/res/drawable-nodpi/tower_rock.png, app/src/main/assets/walk/tower_rock.txt,
          tools/mapgen/tower_rock_debug.png

Skalní patro – 5. patro Věže Řádu Váhy (patro zemních Makromonů), 176 × 352 v pohledu 3/4 shora.
Věž tu prorazila horu: zadní stěna je holá skála ve vrstvách s římsami, podlaha prašná kamenná plotna.
Balvany, sutě (setkání), skalní věž z balvanů, trhlina za provazovým zábradlím, teodolit a vytyčovací
praporky (Řád zaměřuje i horu), suché keře, napůl zasypaná lebka.
Živé detaily (padající kamínky ze sutí a ze skály, štír, prachový vír, prach): InteriorFxView.ROCK.
NODES / EDGES jsou ZDROJ PRAVDY i pro TowerRock.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import tower_parts as P, light as L, figures as F, archive_parts as A, rock_parts as K  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 96
SIDE = 10
NODES = {
    "hora_vytah": (88, 104), "hora_stred": (88, 160), "hora_sut_1": (40, 196), "hora_sut_2": (134, 290),
    "hora_sut_3": (44, 330), "hora_teodolit": (112, 186), "hora_lebka": (60, 268), "hora_trhlina": (124, 210),
    "hora_pilir": (34, 142), "hora_balvan": (142, 150),
}
EDGES = [("hora_vytah", "hora_stred"), ("hora_stred", "hora_pilir"), ("hora_stred", "hora_balvan"),
         ("hora_stred", "hora_sut_1"), ("hora_stred", "hora_teodolit"), ("hora_teodolit", "hora_trhlina"),
         ("hora_sut_1", "hora_lebka"), ("hora_lebka", "hora_sut_3"), ("hora_lebka", "hora_sut_2")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
BAL_Y = AH - 12
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:BAL_Y, SIDE:AW - SIDE] = True

K.rock_floor(img, 0, WALL_H, AW, AH)
K.chasm(img, 96, 222, AW - SIDE, 252)                                    # trhlina vpravo uprostřed
block[220:256, 94:AW] = True

placed = []
def place(spr, cx, by, z=None, collide=True, pad=2, shadow=True, foot=None):
    o = dict(spr=spr, x=int(cx - spr.w // 2), y=int(by - spr.h), z=by if z is None else z, shadow=shadow)
    placed.append(o)
    if collide:
        if foot:
            fw, fh = foot
            block[by - fh:by, int(cx - fw / 2):int(cx + fw / 2)] = True
        else:
            walkmask.silhouette(block, spr.a[:, :, 3], o["x"], o["y"], pad)
    return o

place(K.pillar(1), 30, 136, foot=(24, 10))
place(K.boulder(2, 34, 26), 144, 140, foot=(30, 10))
place(K.boulder(5, 22, 18), 160, 176, foot=(18, 8))
s1 = place(K.scree(1, 52, 28), 40, 186, collide=False)
th = place(K.theodolite(), 112, 178, foot=(14, 6))
fence = place(K.rope_fence(70), 131, 222, foot=(70, 4))
place(K.skull(), 62, 258, foot=(28, 6))
place(K.boulder(7, 28, 22), 24, 238, foot=(24, 8))
s2 = place(K.scree(2, 52, 28), 134, 280, collide=False)
s3 = place(K.scree(3, 52, 28), 44, 320, collide=False)
place(K.boulder(9, 26, 20), 104, 326, foot=(22, 8))
place(K.boulder(11, 18, 14), 156, 322, foot=(14, 6))
for i, (x, y) in enumerate(((70, 214), (150, 262), (88, 286), (122, 336))):
    place(K.flag(i), x, y, foot=(3, 3))
for i, (x, y) in enumerate(((60, 130), (120, 118), (24, 280), (96, 252), (150, 344), (70, 340))):
    place(K.dry_bush(i), x, y, collide=False, shadow=False)

# ── světlo: ostré horské, z levého horního rohu; tvrdé stíny ────────────────
sun = np.zeros((AH, AW)); L.contact_shadow(sun, 70, 170, 110, 150, 1.0); sun *= floor_px
L.lighten(img, sun, 0.12, (255, 236, 200), steps=10)
sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=14, k=0.9)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=10, k=0.7, y0=WALL_H)
for o in placed:
    if not o["shadow"]:
        continue
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.50, 0.28, 0.85)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.55, (24, 14, 8), dither=False, steps=4)

# ── zadní stěna: skála ──────────────────────────────────────────────────────
img[:WALL_H] = K.cliff_wall(AW, WALL_H).a
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

for yy in range(22, WALL_H):                                              # rám výtahu: mramor + železné nosníky
    for xx in range(62, 115):
        if xx < 68 or xx > 108 or yy < 30:
            img[yy, xx] = P.MARB[4] if xx in (64, 65) or yy == 22 else P.MARB[1] if xx in (111, 112) else P.MARB[3]
            if xx in (62, 63, 113, 114):
                img[yy, xx] = F.IRON_[3] if xx in (62, 113) else F.IRON_[1]
for xx in range(58, 119):
    img[20, xx] = F.IRON_[4]; img[21, xx] = F.IRON_[2]
    if xx % 8 == 2:
        img[21, xx] = F.BRASS[3]
elev = F.elevator3d(40, 58)
EX0, EY0 = 88 - 20, WALL_H + 2 - 58
wall_put(elev, EX0, EY0)
wall_put(F.floor_dial3d(10, at=5), 88 - 12, 2)

for y in range(WALL_H - 6, AH):                                           # boční stěny: skála
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            left = X < AW // 2
            band = ((y + int(2 * np.sin(X * 0.3))) // 9) % 4
            col = K.STRATA[[1, 2, 3, 2][band]].copy()
            if inner >= 7:
                col = K.ROCK[1].copy()
            k = 0.15 if left else 0.4
            col[:3] = (col[:3] * (1 - k) + np.array([30, 20, 12]) * k).astype(np.uint8)
            img[y, X] = col
            block[y, X] = True

for o in sorted(placed, key=lambda o: o["z"]):
    s = o["spr"]
    for yy in range(s.h):
        for xx in range(s.w):
            if s.a[yy, xx, 3] and 0 <= o["y"] + yy < AH and 0 <= o["x"] + xx < AW:
                img[o["y"] + yy, o["x"] + xx] = s.a[yy, xx]

wall_put(A.balustrade(AW - 2 * SIDE, AH - BAL_Y), SIDE, BAL_Y)
block[BAL_Y - 2:, :] = True

vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.64, AH * 0.62, k=0.45)
L.darken(img, vg, 0.75, (20, 12, 8), dither=True, steps=10)
L.tone(img, (190, 180, 170), (220, 190, 150), k=0.06)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_rock.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_rock.txt"), "w") as f:
    f.write(f"# mapa chůze tower_rock: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_rock.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_rock_debug.png"))
print("ok", "scree", [(o["x"], o["y"], o["spr"].w) for o in (s1, s2, s3)], "fence", (fence["x"], fence["y"]))
