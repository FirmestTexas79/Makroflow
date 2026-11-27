"""
Použití:  python tools/mapgen/gen_smithy.py
Výstup:   app/src/main/res/drawable-nodpi/smithy.png, app/src/main/assets/walk/smithy.txt, tools/mapgen/smithy_debug.png

Stará kovárna v Horách – region 1, domov minihry Výheň (docs/adr/0118). 176 × 240, pohled 3/4 shora.
Zeď z lomového kamene s trámem, vlevo zděná výheň s komínovým pláštěm a žhavým uhlím, vedle velký měch na kozlíku,
nářadí na liště, štít se sekerami, okno s denním světlem, regál s lahvičkami. Uprostřed kovadlina na pařezu a kalicí
káď, u ní kovář Magmor (Makromon), vpravo brus a stojan se zbraněmi, vlevo dole ponk, u dveří uhlí, ruda, ingoty,
sudy, bedny a polena. Světlo: žár výhně, šikmý pruh z okna, denní světlo ze dveří.
NODES / EDGES jsou ZDROJ PRAVDY i pro cave/Smithy.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "smithy")); sys.path.insert(0, os.path.join(HERE, "tower"))
sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import smithy_parts as P  # noqa: E402
import light as L  # noqa: E402
from pix import Spr  # noqa: E402
from figures import selout  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 240
WALL_H = 92
SIDE = 8
FRONT_Y = AH - 9
NODES = {
    "kv_vstup": (88, 230), "kv_stred": (88, 190), "kv_magmor": (106, 164), "kv_vyhen": (46, 126),
    "kv_kovadlina": (66, 166), "kv_brus": (146, 182), "kv_stojan": (138, 210), "kv_ponk": (40, 208), "kv_police": (150, 128),
}
EDGES = [("kv_vstup", "kv_stred"), ("kv_stred", "kv_magmor"), ("kv_stred", "kv_kovadlina"), ("kv_kovadlina", "kv_vyhen"),
         ("kv_stred", "kv_brus"), ("kv_stred", "kv_stojan"), ("kv_stred", "kv_ponk"), ("kv_magmor", "kv_police"),
         ("kv_brus", "kv_police"), ("kv_magmor", "kv_vyhen")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:FRONT_Y, SIDE:AW - SIDE] = True

P.floor(img, 0, WALL_H, AW, AH)
P.flagstones(img, SIDE, WALL_H, 92, 142)

placed = []
def place(spr, cx, by, collide=True, pad=2, foot=None):
    o = dict(spr=spr, x=int(cx - spr.w // 2), y=int(by - spr.h), z=by)
    placed.append(o)
    if collide:
        if foot:
            fw, fh = foot
            block[by - fh:by, int(cx - fw / 2):int(cx + fw / 2)] = True
        else:
            walkmask.silhouette(block, spr.a[:, :, 3], o["x"], o["y"], pad)
    return o

forge = place(P.forge(60, 100), 46, 116, foot=(60, 26))
place(P.bellows(36, 30), 94, 114, foot=(34, 10))
place(P.potion_shelf(34, 40), 152, 112, foot=(34, 10))
anvil = place(P.anvil_stump(30, 30), 68, 156, foot=(24, 10))
place(P.quench_tub(24, 18), 36, 156, foot=(22, 8))


def magmor_sprite(h=30):
    im = Image.open(os.path.join(ROOT, "app", "src", "main", "res", "drawable", "makromon_47_magmor.png")).convert("RGBA")
    im = im.crop(im.getbbox()).transpose(Image.FLIP_LEFT_RIGHT)            # dívá se doleva ke kovadlině
    w = round(im.width * h / im.height)
    a = np.array(im.resize((w, h), Image.LANCZOS)).astype(np.float32)
    a[:, :, 3] = np.where(a[:, :, 3] > 120, 255, 0)
    s = Spr(w, h); s.a = a.astype(np.uint8)
    return selout(s, 0.55, 0.85)


magmor = place(magmor_sprite(30), 104, 156, foot=(20, 8))
place(P.grindstone(26, 28), 150, 172, foot=(22, 8))
place(P.weapon_rack(42, 34), 146, 204, foot=(42, 8))
place(P.workbench(50, 24), 38, 198, foot=(50, 10))
place(P.heap(30, 14, P.COALR, 3), 24, 226, foot=(28, 8))
place(P.heap(24, 13, P.ORE, 5), 122, 226, foot=(22, 7))
place(P.ingots(18, 10), 140, 228, foot=(16, 5))
place(P.barrel(14, 18), 160, 226, foot=(14, 6))
place(P.crate(16, 16), 52, 228, foot=(16, 6))
place(P.log_pile(30, 16), 152, 146, foot=(28, 6))

# ── světlo ─────────────────────────────────────────────────────────────────
def ring_pool(mask, cx, cy, rx, ry, k=1.0):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if 0 <= x < AW and 0 <= y < AH:
                d = (((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2) ** 0.5 + (0.04 if (x + y) % 2 else -0.04)
                v = 1.0 if d < 0.35 else 0.7 if d < 0.6 else 0.4 if d < 0.85 else 0.15 if d < 1 else 0
                mask[y, x] = max(mask[y, x], round(v * k, 2))


warm = np.zeros((AH, AW))
ring_pool(warm, 46, 104, 62, 44, 1.0)
ring_pool(warm, 46, 60, 30, 40, 0.5)
day = np.zeros((AH, AW))
L.light_shaft(day, (140, WALL_H), (160, WALL_H), -0.55, 110)
for y in range(FRONT_Y - 40, AH):                                            # světlo ze dveří
    for x in range(70, 107):
        t = (AH - y) / 40
        day[y, x] = max(day[y, x], max(0.0, 1 - t) * (1 - abs(x - 88) / 20) * 0.9)
day *= floor_px | (np.arange(AH)[:, None] >= FRONT_Y)

sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=12, k=0.9)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=8, k=0.6, y0=WALL_H)
for o in placed:
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.45, 0.25, 0.7)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
sh *= floor_px
L.darken(img, sh, 0.55, (10, 4, 6), dither=False, steps=4)

img[:WALL_H] = P.stone_wall(AW, WALL_H, soot_x=46).a
block[:WALL_H + 3, :] = True


def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]


wall_put(P.window(22, 28), 139, 16)
wall_put(P.tool_rack(40, 24), 92, 40)
wall_put(P.shield_axes(26, 24), 99, 13)
for y in range(WALL_H - 4, AH):                                              # boční stěny
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            col = (P.DWOOD[1] if inner in (0, SIDE - 1) else P.WOOD[2] if inner == 1 else P.DWOOD[3] if (y // 14) % 2 else P.DWOOD[2]).copy()
            if X > AW // 2:
                col[:3] = (col[:3] * 0.72).astype(np.uint8)
            img[y, X] = col
            block[y, X] = True

for o in sorted(placed, key=lambda o: o["z"]):
    s = o["spr"]
    for yy in range(s.h):
        for xx in range(s.w):
            if s.a[yy, xx, 3] and 0 <= o["y"] + yy < AH and 0 <= o["x"] + xx < AW:
                img[o["y"] + yy, o["x"] + xx] = s.a[yy, xx]

# přední stěna (řez) se dveřmi
for y in range(FRONT_Y, AH):
    for x in range(AW):
        if 76 <= x <= 100:
            img[y, x] = P.c(0xC8B48A) if y > FRONT_Y + 1 else P.WOOD[2]     # práh a denní světlo
        else:
            img[y, x] = P.WOOD[3] if y == FRONT_Y else P.WOOD[1] if (x // 9) % 2 else P.DWOOD[2]
block[FRONT_Y - 1:, :] = True
block[FRONT_Y - 1:, 80:97] = False
wall_put(P.sign_hammer(22, 14), 77, FRONT_Y - 18) if False else None

dark = np.clip(1.0 - np.maximum(warm, day * 0.8), 0, 1); dark[:WALL_H] *= 0.6
L.darken(img, dark, 0.42, (10, 6, 10), dither=True, steps=10)
L.lighten(img, warm, 0.24, (255, 150, 70), steps=10)
L.lighten(img, day, 0.16, (255, 240, 210), steps=8)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.62, AH * 0.62, k=0.45)
L.darken(img, vg, 0.7, (8, 4, 8), dither=True, steps=10)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "smithy.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "smithy.txt"), "w") as f:
    f.write(f"# mapa chůze smithy: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_smithy.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "smithy_debug.png"))
print("ok forge", (forge["x"], forge["y"]), "anvil", (anvil["x"], anvil["y"]), "magmor", (magmor["x"], magmor["y"], magmor["spr"].w))
