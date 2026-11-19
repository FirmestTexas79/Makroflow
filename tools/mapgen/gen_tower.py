"""
Použití:  python tools/mapgen/gen_tower.py
Výstup:   app/src/main/res/drawable-nodpi/tower_lobby.png, app/src/main/assets/walk/tower_lobby.txt,
          app/src/main/res/drawable-nodpi/tower_gate.png (průčelí pro cutscénu),
          tools/mapgen/tower_lobby_debug.png, tools/mapgen/tower/parts.png

Vestibul Věže Řádu Váhy (Akt II, docs/adr/0103) – interiér 176 × 320 v pohledu 3/4 shora:
  1. podklad: mramorová podlaha, modrý koberec s mosazným lemem, mozaika vah,
  2. světlo: kužely z oken, stín u paty stěny a bočních stěn, vržené a kontaktní stíny objektů, odlesky v mramoru,
  3. zadní stěna (mramor, okna s popelem, portréty bez tváří, pilastry, potrubní pošta), výtah s ukazatelem pater,
  4. objekty (tower/tower_parts.py): Septův pult a Sept, fronta se sloupky, čekárna, kartotéky, sloupy, rostliny,
  5. viněta a tónování (studené světlo nahoře, teplejší u vchodu).
NODES / EDGES jsou ZDROJ PRAVDY i pro TowerLobby.kt (MAP).
"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import walkmask  # noqa: E402
import tower_parts as P, light as L  # noqa: E402
from pix import c, Spr, outline  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 320
WALL_H = 90
SIDE = 10                                                        # boční stěna (vrch + vnitřní čelo)
NODES = {
    "vychod": (88, 312), "stred": (88, 236), "vytah_veze": (88, 98), "sept": (110, 210),
    "cekarna": (44, 252), "kartoteka": (54, 304), "portrety": (54, 100), "kartoteka_p": (122, 304),
}
EDGES = [("vychod", "stred"), ("stred", "vytah_veze"), ("stred", "sept"), ("stred", "cekarna"), ("stred", "kartoteka"),
         ("vytah_veze", "portrety"), ("stred", "kartoteka_p")]

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.zeros((AH, AW), bool)
floor_px = np.zeros((AH, AW), bool); floor_px[WALL_H:, SIDE:AW - SIDE] = True

# ── 1. podlaha ───────────────────────────────────────────────────────────────
P.floor(img, 0, WALL_H, AW, AH)
P.carpet(img, 78, 99, WALL_H + 2, AH)
em = P.floor_emblem(18)
EMX, EMY = 88, 150
for y in range(em.h):
    for x in range(em.w):
        if em.a[y, x, 3]:
            img[EMY - em.h // 2 + y, EMX - em.w // 2 + x] = em.a[y, x]

# ── objekty (sprite, střed x, pata y, z, silueta do kolizí, stín) ────────────
placed = []
def place(spr, cx, by, z=None, collide=True, pad=2, shadow=True, foot=None):
    placed.append(dict(spr=spr, x=int(cx - spr.w // 2), y=int(by - spr.h), z=by if z is None else z, shadow=shadow, foot=foot))
    if collide:
        if foot:                                                 # jen obdélník u paty (věci, za které se nechodí)
            fw, fh = foot
            block[by - fh:by, int(cx - fw / 2):int(cx + fw / 2)] = True
        else:
            walkmask.silhouette(block, spr.a[:, :, 3], int(cx - spr.w // 2), int(by - spr.h), pad)

sept = P.sept(); desk = P.desk(66, 30)
DESK_X, DESK_Y = 132, 198
place(sept, DESK_X + 4, DESK_Y - 17, z=DESK_Y - 40, collide=False, shadow=False)
place(desk, DESK_X, DESK_Y, foot=(66, 22))
place(P.stanchion_line(52), DESK_X + 6, 228, pad=1)
place(P.queue_board(), 26, 186)
place(P.calibration_scale(), 46, 140)
place(P.bench(36), 54, 206); place(P.bench(36), 54, 234)
place(P.ticket_machine(), 18, 232)
for i, x in enumerate((24, 48)):
    place(P.cabinet(i), x, 296)
for i, x in enumerate((128, 152)):
    place(P.cabinet(i + 2), x, 296)
place(P.dead_plant(), 20, 112); place(P.dead_plant(), 156, 112)
place(P.dead_plant(), 68, 300); place(P.dead_plant(), 108, 300)
for y in (150, 262):
    place(P.side_column(42, 0), 6, y, foot=(14, 10)); place(P.side_column(42, 1), AW - 7, y, foot=(14, 10))

# ── 2. světlo a stíny podlahy ────────────────────────────────────────────────
lit = np.zeros((AH, AW))
WIN = [(22, 14, 20, 50), (154, 14, 20, 50)]                      # okna (střed x, horní y, š, v)
for (wx, wy, ww, wh) in WIN:
    L.light_shaft(lit, (wx - ww // 2 + 1, WALL_H), (wx + ww // 2 - 1, WALL_H), 0.46 if wx < 88 else -0.34, 150)
lit *= floor_px
L.lighten(img, lit, 0.42, (240, 244, 255))
rng = np.random.default_rng(11)                                    # prach a popel v kuželech světla
for _ in range(70):
    x, y = int(rng.integers(SIDE, AW - SIDE)), int(rng.integers(WALL_H + 2, AH - 10))
    if lit[y, x] > 0.35:
        img[y, x, :3] = (250, 250, 255) if rng.random() < 0.6 else (210, 214, 224)
# teplé světlo od vchodu
warm = np.zeros((AH, AW)); L.contact_shadow(warm, 88, AH, 46, 34, 1.0); warm *= floor_px
L.lighten(img, warm, 0.22, (255, 236, 196))
# zelená záře bankéřské lampy na desce a světlo kabiny výtahu
glow = np.zeros((AH, AW)); L.contact_shadow(glow, DESK_X + 23, DESK_Y - 26, 14, 9, 1.0)
L.lighten(img, glow, 0.25, (170, 240, 190))
eg = np.zeros((AH, AW)); L.contact_shadow(eg, 88, WALL_H + 6, 24, 12, 1.0); eg *= floor_px
L.lighten(img, eg, 0.25, (255, 226, 150))

sh = np.zeros((AH, AW))
L.wall_base_shadow(sh, WALL_H, depth=16, k=0.95)
L.side_wall_shadow(sh, SIDE, AW - SIDE, depth=12, k=0.8, y0=WALL_H)
for o in placed:
    if not o["shadow"]:
        continue
    s = o["spr"]
    L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.50, 0.28, 0.75)
    L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.2, 1.0)
sh *= floor_px
L.darken(img, sh, 0.50, dither=False, steps=4)

# ── 3. zadní a boční stěny ───────────────────────────────────────────────────
wall = P.back_wall(AW, WALL_H)
wa = wall.a.copy()
for y in range(WALL_H):                                          # stěna nahoře tmavne (strop je vysoko)
    k = round(0.40 * (1 - y / WALL_H) ** 1.6 * 6) / 6
    if k > 0:
        wa[y, :, :3] = (wa[y, :, :3] * (1 - k) + np.array([14, 18, 32]) * k).astype(np.uint8)
img[:WALL_H] = wa
block[:WALL_H + 3, :] = True

def wall_put(spr, x, y):
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3] and 0 <= y + yy < AH and 0 <= x + xx < AW:
                img[y + yy, x + xx] = spr.a[yy, xx]

for px0 in (34, 132):
    wall_put(P.pilaster(WALL_H - 14), px0, 9)
for (wx, wy, ww, wh) in WIN:
    wall_put(P.window(ww, wh, seed=wx), wx - ww // 2, wy)
for i, x in enumerate((54, 122)):
    wall_put(P.portrait(i), x - 8, 26)
for x in (62, 112):
    wall_put(P.tube_pipe(WALL_H - 12), x, 9)
elev = P.elevator(40, 58)
EX0, EY0 = 88 - 20, WALL_H + 2 - 58
wall_put(elev, EX0, EY0)
dial = P.floor_dial(15)
wall_put(dial, 88 - dial.w // 2, 8)
# odlesky v leštěném mramoru: okna a mosazná klec výtahu
for (wx, wy, ww, wh) in WIN:
    L.reflect(img, (wx - ww // 2 + 1, wy, wx + ww // 2 - 1), WALL_H + 1, 14, k=0.20, floor_mask=floor_px)
L.reflect(img, (EX0 + 4, EY0 + 8, EX0 + 36), WALL_H + 3, 16, k=0.28, floor_mask=floor_px)

# boční stěny: vrch (světlý mramor) + vnitřní čelo (stínované, levé světlejší – světlo z oken)
for y in range(WALL_H - 6, AH):
    for x in range(SIDE):
        for (X, inner) in ((x, x), (AW - 1 - x, x)):
            left = X < AW // 2
            if inner < 3:
                col = P.MARB[4] if inner == 1 else P.MARB[2] if inner == 2 else P.MARB[1]
            else:
                t = (inner - 3) / (SIDE - 3)
                base = P.MARB[2] if left else P.MARB[1]
                col = base.copy()
                k = (0.15 + 0.45 * t) if left else (0.35 + 0.4 * t)
                if (y // 9) % 2 == 0 and inner == SIDE - 1:
                    col = P.MARB[0]
                col[:3] = (col[:3] * (1 - k) + np.array([14, 18, 32]) * k).astype(np.uint8)
            img[y, X] = col
            block[y, X] = True

# ── 4. objekty podle hloubky ─────────────────────────────────────────────────
for o in sorted(placed, key=lambda o: o["z"]):
    s = o["spr"]
    for yy in range(s.h):
        for xx in range(s.w):
            if s.a[yy, xx, 3] and 0 <= o["y"] + yy < AH and 0 <= o["x"] + xx < AW:
                img[o["y"] + yy, o["x"] + xx] = s.a[yy, xx]

# vchod dole: práh a šedé denní světlo zvenku (popel)
for y in range(AH - 5, AH):
    for x in range(AW):
        if 70 <= x < 107:
            img[y, x, :3] = P.ASH[3][:3] if y > AH - 3 else P.MARB[4][:3]
        elif x < SIDE or x >= AW - SIDE or True:
            img[y, x, :3] = P.MARB[1][:3] if y > AH - 4 else P.MARB[2][:3]
block[AH - 6:, 70:107] = False

# ── 5. viněta a tónování ─────────────────────────────────────────────────────
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.55, AW * 0.62, AH * 0.62, k=0.45)
L.darken(img, vg, 1.0, (10, 12, 24), dither=False, steps=6)
L.tone(img, (150, 170, 220), (230, 210, 180), k=0.08)
# lampa na pultu svítí (jádro světla se kreslí až po vinětě)
for (x, y) in ((DESK_X + 22, DESK_Y - 27), (DESK_X + 23, DESK_Y - 27), (DESK_X + 24, DESK_Y - 27)):
    img[y, x, :3] = (220, 255, 200)

# ── výstupy ──────────────────────────────────────────────────────────────────
out_png = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_lobby.png")
Image.fromarray(img[:, :, :3]).save(out_png)
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_lobby.txt"), "w") as f:
    f.write(f"# mapa chůze tower_lobby: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_tower.py)\n{AW} {AH} {CELL}\n")
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
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_lobby_debug.png"))

# průčelí pro cutscénu
fac, portal = P.tower_facade()
Image.fromarray(fac[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_gate.png"))
leaf = P.door_leaf(portal[2], portal[3])
Image.fromarray(leaf, "RGBA").save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_door_leaf.png"))
# portrét archiváře Septa pro deník a dialogy (10× bez vyhlazení, jako Makromoni)
sp = P.sept().a
Image.fromarray(sp, "RGBA").resize((sp.shape[1] * 10, sp.shape[0] * 10), Image.NEAREST).save(
    os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "npc_sept.png"))
print("ok", AW, AH, "portal", portal, "desk", (DESK_X, DESK_Y))
