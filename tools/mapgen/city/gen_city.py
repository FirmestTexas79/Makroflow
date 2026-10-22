"""
Návrh města Ozvěna (Akt II) – zatím jen náhled, do hry se nezapojuje.
Použití: python tools/mapgen/city/gen_city.py [verze] → tools/mapgen/city/ozvena_<verze>.png (3×)
         a ozvena_<verze>_view.png (výřez, jak ho uvidí hráč při přiblížení).

v4: širší město 384 × 656 a přiblížená kamera (hráč vidí ~128 art px na šířku). Ulice jsou propletené
křivky (hlavní třída do oblouku, okruh kolem náměstí, uličky mezi domy), víc domů, stromy ve stylu B/W
(patrové koruny), domy se stínováním a vrženým stínem, nahoře brána starého města z pláně rozšířená
o hradby a nárožní věže.
"""
import math, os, sys
import numpy as np
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
import city_parts as C, pix, tiles, objects  # noqa: E402
from pix import c, Spr  # noqa: E402
from pixart import STN, stones  # noqa: E402

VER = sys.argv[1] if len(sys.argv) > 1 else "v7"
AW, AH = 384, 656
T = 16
VOID_Y = 600
rng = np.random.default_rng(7)
LUSH = [c(0x34522C), c(0x426634), c(0x52783E), c(0x648C4C), c(0x92B478)]
img = np.zeros((AH, AW, 4), np.uint8)
for r in range(AH // T):
    for q in range(AW // T):
        img[r * T:(r + 1) * T, q * T:(q + 1) * T] = tiles.ground(int(rng.choice([0, 0, 0, 1])), LUSH).a

# za hradbou (nad ní) je Popelavá pláň: popel místo trávy
for r in range(0, 6):
    for q in range(AW // T):
        img[r * T:(r + 1) * T, q * T:(q + 1) * T] = tiles.ground(int(rng.choice([0, 1, 1, 2]))).a

# ── ulice: křivky (Catmull-Rom) s proměnnou šířkou ──
def cr(t, p0, p1, p2, p3):
    t2, t3 = t * t, t * t * t
    return tuple(0.5 * ((2 * p1[i]) + (-p0[i] + p2[i]) * t + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t2 +
                        (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t3) for i in range(2))


STREETS = [   # (body, poloměr)
    ([(192, 90), (192, 120), (176, 170), (186, 214), (192, 240)], 14),            # hlavní třída od brány
    ([(20, 178), (70, 172), (130, 182), (176, 170), (240, 166), (300, 176), (364, 170)], 9),   # severní ulička
    ([(110, 318), (64, 332), (40, 380), (52, 440), (96, 470), (150, 500), (192, 512)], 10),   # západní ulice
    ([(274, 318), (316, 340), (340, 380), (334, 420)], 10),                          # k Aréně
    ([(192, 400), (186, 450), (192, 500), (192, 560)], 13),                          # jižní třída k věži
    ([(10, 580), (100, 584), (192, 578), (290, 584), (374, 580)], 11),              # promenáda nad propastí
    ([(64, 332), (28, 300), (24, 250)], 7),                                          # ulička do parku
    ([(300, 176), (338, 220), (352, 270)], 7),                                       # ulička na východ
    ([(150, 500), (110, 540), (60, 556)], 7),
]
street = np.zeros((AH, AW), bool)
yy, xx = np.mgrid[0:AH, 0:AW]
for pts, rad in STREETS:
    P = [pts[0]] + pts + [pts[-1]]
    for i in range(1, len(P) - 2):
        for k in range(16):
            px, py = cr(k / 16, P[i - 1], P[i], P[i + 1], P[i + 2])
            m = (xx - px) ** 2 + (yy - py) ** 2 <= rad * rad
            street |= m
SQ = (192, 320)                                     # střed náměstí
plaza = ((xx - SQ[0]) / 82) ** 2 + ((yy - SQ[1]) / 66) ** 2 <= 1
ring = (((xx - SQ[0]) / 100) ** 2 + ((yy - SQ[1]) / 84) ** 2 <= 1) & ~plaza
street |= ring
street[VOID_Y:, :] = False
tiles.draw_cobbles(img, lambda x, y: street[y, x], (0, 0, AW, AH))
# náměstí: desky a soustředné kruhy
for y in range(SQ[1] - 70, SQ[1] + 70):
    for x in range(SQ[0] - 86, SQ[0] + 86):
        if not plaza[y, x]:
            continue
        d = math.hypot((x - SQ[0]) / 1.24, y - SQ[1])
        if 40 < d < 43 or 54 < d < 56:
            col = c(0x8E8478)
        else:
            slab = (x // 10 + (y // 7) % 2) % 3
            col = c(0x9A9286) if (x % 10 == 0 or y % 7 == 0) else (c(0xC8C0B0), c(0xD6CEBE), c(0xBEB6A6))[slab]
        img[y, x] = col
paved = street | plaza
for y in range(1, AH - 1):
    for x in range(1, AW - 1):
        if paved[y, x] and not paved[y - 1, x]:
            img[y, x] = STN[4]; img[y + 1, x] = STN[2]
        elif paved[y, x] and not paved[y + 1, x]:
            img[y, x] = STN[1]
        elif paved[y, x] and (not paved[y, x - 1] or not paved[y, x + 1]):
            img[y, x] = STN[2]

# ── propast dole ──
for y in range(VOID_Y, AH):
    for x in range(AW):
        t = (y - VOID_Y) / (AH - VOID_Y)
        img[y, x, :3] = (np.array([40, 36, 52]) * (1 - t) + np.array([8, 6, 14]) * t).astype(np.uint8)
        d = math.hypot((x - 240) / 140, (y - AH) / 50)
        if d < 1:
            img[y, x, :3] = np.minimum(255, img[y, x, :3] + np.array([30, 26, 48]) * (1 - d) ** 2).astype(np.uint8)


def branch(x, y, ang, ln, w, depth):
    for i in range(int(ln)):
        px, py = int(x + math.cos(ang) * i), int(y + math.sin(ang) * i)
        for k in range(-(w // 2), w // 2 + 1):
            if 0 <= px + k < AW and VOID_Y <= py < AH:
                f = 0.55 + 0.45 * (py - VOID_Y) / (AH - VOID_Y)
                base = np.array([214, 208, 224]) if k <= 0 else np.array([150, 144, 172])
                img[py, px + k, :3] = (base * (1 - f * 0.6)).astype(np.uint8)
    if depth:
        ex, ey = x + math.cos(ang) * ln, y + math.sin(ang) * ln
        for da in (-0.5, 0.45):
            branch(ex, ey, ang + da, ln * 0.62, max(1, w - 1), depth - 1)


branch(240, VOID_Y + 4, math.pi / 2, 22, 7, 5)
branch(240, VOID_Y + 20, math.pi / 2 - 0.9, 28, 4, 3)
branch(240, VOID_Y + 24, math.pi / 2 + 0.9, 32, 4, 3)
branch(70, VOID_Y + 10, math.pi / 2 + 0.25, 18, 3, 3)
for y in range(VOID_Y, AH):
    for x in range(AW):
        fog = (math.sin(x / 23 + y / 3.5) + 0.6 * math.sin(x / 11 - y / 9)) / 1.6
        band = math.exp(-((y - (VOID_Y + 36)) / 9) ** 2) + 0.6 * math.exp(-((y - (VOID_Y + 50)) / 5) ** 2)
        k = max(0, fog) * band * 0.55
        img[y, x, :3] = (img[y, x, :3] * (1 - k) + np.array([150, 144, 170]) * k).astype(np.uint8)

# ── objekty ──
placed = []


block = np.zeros((AH, AW), bool)


def place(spr, cx, by, z=None, cast=True, foot="auto"):
    placed.append((spr, cx, by, by if z is None else z, cast))
    if foot == "auto":
        foot = (max(4, spr.w - 6), max(3, int(spr.h * 0.42))) if spr.h > 30 else (max(4, spr.w - 2), 4)
    if foot:
        fw, fh = foot
        block[max(0, by - fh):by, max(0, int(cx - fw // 2)):min(AW, int(cx + (fw + 1) // 2))] = True


DOORS = []


def house(cx, by, **kw):
    place(C.shade_building(C.house(**kw)), cx, by)
    DOORS.append((cx + (6 if kw.get("windows", 2) == 1 else 0), by))


# brána nahoře: brána z pláně + hradby + nárožní věže
gate = objects.city_gate()
wall_l = Spr(120, 58); stones(wall_l, 0, 119, 8, 57, h=6, w=(8, 14), seed=81, tone=-1)
for x in range(0, 120, 8):
    wall_l.rect(x, 2, x + 4, 8, STN[3]); wall_l.hline(x, x + 4, 2, STN[4])
wall_r = Spr(120, 58); wall_r.a = wall_l.a[:, ::-1].copy()
place(wall_l, 60, 100, z=1, cast=False, foot=None); place(wall_r, 324, 100, z=1, cast=False, foot=None)
place(gate, 192, 100, z=2, cast=False, foot=None)
for tx in (16, 368):
    t = objects.city_gate()
    tower = Spr(34, 100); tower.a = t.a[:, 21:55].copy()
    place(tower, tx, 100, z=3, cast=False, foot=None)

# sever: řada domů nad uličkou a pod ní
for (x, kw) in ((48, dict(w=44, wall_h=24, roof_pal=C.ROOF_TEAL, wall="timber", shut="blue", seed=21)),
                (96, dict(w=40, wall_h=22, rows=5, roof_pal=C.ROOF_RED, wall="plaster", shut="green", windows=1, seed=22)),
                (140, dict(w=44, wall_h=34, roof_pal=C.ROOF_SLATE, wall="stone", shut="red", seed=23, storeys=2)),
                (246, dict(w=48, wall_h=34, roof_pal=C.ROOF_OCHRE, wall="plaster", shut="ochre", seed=24, storeys=2)),
                (296, dict(w=40, wall_h=22, rows=5, roof_pal=C.ROOF_TEAL, wall="plaster", shut="blue", windows=1, seed=25)),
                (342, dict(w=44, wall_h=24, roof_pal=C.ROOF_RED, wall="timber", shut="green", seed=26))):
    house(x, 164, **kw)
place(C.shade_building(C.venka_house()), 100, 246); DOORS.append((106, 246))
house(52, 246, w=44, wall_h=24, roof_pal=C.ROOF_RED, wall="plaster", shut="green", seed=27)
place(C.shade_building(C.bakery()), 280, 246); DOORS.append((282, 246))
house(340, 246, w=40, wall_h=22, rows=5, roof_pal=C.ROOF_SLATE, wall="stone", shut="red", windows=1, seed=28)
for (x, y) in ((314, 250), (322, 250)):
    place(C.barrel(), x, y, foot=(8, 4))
place(C.crate(), 246, 252)
# náměstí
place(C.fountain(), SQ[0], SQ[1] + 36, foot=(72, 34))
for (x, y, cl, g) in ((140, 300, "red", "apples"), (244, 300, "blue", "bread"), (140, 372, "green", "apples"), (244, 372, "red", "bread")):
    place(C.stall(cl, g), x, y)
for (x, y) in ((160, 386), (224, 386)):
    place(C.bench(), x, y)
place(C.notice_board(), 116, 272)
for a in range(0, 360, 45):
    place(C.city_lamp(), int(SQ[0] + 96 * math.cos(math.radians(a))), int(SQ[1] + 4 + 80 * math.sin(math.radians(a))))
place(C.bunting(150), SQ[0], 270, z=900, cast=False, foot=None)
# západ: park, domy u západní ulice
for (x, y, kw) in ((70, 316, dict(w=40, wall_h=22, rows=5, roof_pal=C.ROOF_OCHRE, wall="plaster", shut="green", windows=1, seed=31)),
                   (92, 420, dict(w=44, wall_h=34, roof_pal=C.ROOF_TEAL, wall="timber", shut="blue", seed=32, storeys=2)),
                   (24, 460, dict(w=40, wall_h=24, roof_pal=C.ROOF_RED, wall="plaster", shut="red", windows=1, seed=33)),
                   (110, 532, dict(w=44, wall_h=24, roof_pal=C.ROOF_SLATE, wall="stone", shut="green", seed=34)),
                   (40, 548, dict(w=36, wall_h=20, rows=5, roof_pal=C.ROOF_OCHRE, wall="plaster", shut="blue", windows=1, seed=35))):
    house(x, y, **kw)
place(C.flower_bed(36), 92, 432); place(C.flower_bed(30), 110, 544)
# východ: Aréna a domy
place(C.shade_building(C.arena()), 318, 486, foot=(110, 56))
house(352, 330, w=40, wall_h=24, roof_pal=C.ROOF_RED, wall="plaster", shut="ochre", windows=1, seed=41)
house(276, 420, w=40, wall_h=22, rows=5, roof_pal=C.ROOF_TEAL, wall="plaster", shut="green", windows=1, seed=42)
# jih: věž s náměstíčkem, sklad schovaný za stromy
place(C.shade_building(C.scale_tower()), 192, 562, foot=(44, 62))
house(258, 556, w=40, wall_h=24, roof_pal=C.ROOF_SLATE, wall="stone", shut="red", windows=1, seed=51)
place(C.shed(), 352, 548)
# okraj propasti
place(C.cliff_edge(AW), 192, VOID_Y + 34, z=0, cast=False, foot=None)
place(C.balustrade(152), 76, VOID_Y + 2, cast=False)       # zábradlí přes celou šířku, mezera jen u výtahu
place(C.balustrade(200), 288, VOID_Y + 2, cast=False)
place(C.elevator(), 170, VOID_Y + 14, cast=False, foot=(30, 18))
# stromy B/W: husté okraje, park, kolem Arény a věže
TREES = [(x, y) for y in range(130, 600, 22) for x in (6, 378)]
TREES += [(22, 214), (40, 226), (18, 260), (40, 270), (24, 296), (150, 440), (130, 470), (236, 470), (256, 500),
          (360, 400), (372, 470), (330, 556), (366, 540), (300, 578), (68, 600), (230, 140), (154, 140), (212, 260), (172, 262)]
for i, (x, y) in enumerate(TREES):
    place(C.tree_bw(i, 50 if i % 3 else 56), x, y, foot=(12, 7))

# chodníčky od dveří ke dlažbě (šlapáky) a květiny v trávě
for (dx, dy) in DOORS:
    y = dy
    while y < min(AH - 1, dy + 60) and not paved[y, dx]:
        if (y - dy) % 4 < 3:
            for x in range(dx - 3, dx + 3):
                img[y, x] = STN[3] if (y - dy) % 4 == 0 else STN[2]
        y += 1
FL = [c(0xE8546A), c(0xF4D060), c(0xF0F0F0), c(0xB070E0)]
for i in range(260):
    x, y = int(rng.integers(8, AW - 8)), int(rng.integers(120, VOID_Y - 10))
    if paved[y - 2:y + 3, x - 2:x + 3].any():
        continue
    col = FL[i % 4]
    for (ox, oy) in ((0, 0), (2, 1), (-1, 2)):
        img[y + oy, x + ox] = col; img[y + oy + 1, x + ox] = LUSH[1]

# vykreslení: nejdřív vržené stíny (doprava dolů), pak sprity podle paty
for (spr, cx, by, z, cast) in placed:
    if not cast:
        continue
    x0, y0 = int(cx - spr.w // 2), int(by - spr.h)
    m = spr.a[:, :, 3] > 0
    for (dx, dy) in ((5, 2),):
        for sy, sx in zip(*np.nonzero(m)):
            X, Y = x0 + sx + dx, y0 + sy + dy
            if sy > spr.h * 0.35 and 0 <= X < AW and 0 <= Y < VOID_Y:
                img[Y, X, :3] = (img[Y, X, :3] * 0.8).astype(np.uint8)
for (spr, cx, by, z, cast) in sorted(placed, key=lambda p: p[3]):
    pix.paste(img, spr, int(cx - spr.w // 2), int(by - spr.h))

out = Image.fromarray(img[:, :, :3])
out.resize((AW * 2, AH * 2), Image.NEAREST).save(os.path.join(HERE, f"ozvena_{VER}.png"))

# ── herní výstupy: obrázek, mapa chůze, uzly (ZDROJ PRAVDY i pro OzvenaCity.kt) ──
NODES = {
    "vstup_z_plane": (192, 108), "c_sever": (186, 182), "venka": (106, 258), "pekarna": (282, 258),
    "namesti": (124, 336), "vyveska": (116, 284), "kasna": (192, 368), "c_jih": (192, 410),
    "arena": (318, 494), "vez": (192, 572), "vytah": (170, 588), "sklad": (352, 560),
}
EDGES = [("vstup_z_plane", "c_sever"), ("c_sever", "venka"), ("c_sever", "pekarna"), ("c_sever", "namesti"),
         ("namesti", "vyveska"), ("namesti", "kasna"), ("namesti", "c_jih"), ("c_jih", "arena"), ("c_jih", "vez"),
         ("vez", "vytah"), ("vez", "sklad")]
block[:100, :] = True
block[:112, 180:205] = False                            # průjezd branou
block[VOID_Y - 10:, :] = True                           # zábradlí a propast
block[VOID_Y - 22:VOID_Y - 8, 154:186] = False          # mezera u výtahu
block[:, :6] = True; block[:, -6:] = True
walk = ~block
ROOT = os.path.join(HERE, "..", "..", "..")
Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "ozvena.png"))
CELL = 2
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    rr = list(rows[y // CELL]); rr[x // CELL] = "."; rows[y // CELL] = "".join(rr)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "ozvena.txt"), "w") as f:
    f.write(f"# mapa chůze ozvena: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/city/gen_city.py)\n")
    f.write(f"{AW} {AH} {CELL}\n")
    f.write("\n".join(rows) + "\n")
from PIL import ImageDraw
dbg = Image.fromarray(img[:, :, :3]).resize((AW * 2, AH * 2), Image.NEAREST).convert("RGBA")
ov = Image.new("RGBA", dbg.size, (0, 0, 0, 0)); d = ImageDraw.Draw(ov)
for gy, row in enumerate(rows):
    for gx, ch in enumerate(row):
        if ch == "#":
            d.rectangle([gx * 4, gy * 4, gx * 4 + 3, gy * 4 + 3], fill=(255, 0, 0, 50))
for (a, b) in EDGES:
    d.line([NODES[a][0] * 2, NODES[a][1] * 2, NODES[b][0] * 2, NODES[b][1] * 2], fill=(255, 255, 0, 200), width=2)
for n, (x, y) in NODES.items():
    d.ellipse([x * 2 - 5, y * 2 - 5, x * 2 + 5, y * 2 + 5], fill=(0, 120, 255, 230))
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "ozvena_debug.png"))
# výřez, jak ho uvidí hráč (128 art px na šířku, poměr telefonu ~9:20), střed na náměstí
vw, vh = 128, 284
v = out.crop((SQ[0] - vw // 2, SQ[1] - vh // 2 - 20, SQ[0] + vw // 2, SQ[1] + vh // 2 - 20))
v.resize((vw * 4, vh * 4), Image.NEAREST).save(os.path.join(HERE, f"ozvena_{VER}_view.png"))
print("ok", VER)
