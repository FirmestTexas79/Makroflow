"""
Návrh města Ozvěna (Akt II) – zatím jen náhled, do hry se nezapojuje.
Použití: python tools/mapgen/city/gen_city.py [verze] → tools/mapgen/city/ozvena_<verze>.png (3×)
Skládání stejné jako u sadu: podklad (tráva, dlažba ulic s obrubníkem, kamenné desky náměstí),
domy a objekty z city_parts.py po prvcích, řazení podle paty, dole okraj propasti s korunou Lumiry.
"""
import math, os, sys
import numpy as np
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
import city_parts as C, pix, tiles  # noqa: E402
from pix import c  # noqa: E402
from pixart import STN  # noqa: E402

VER = sys.argv[1] if len(sys.argv) > 1 else "v3"
AW, AH = 240, 576
T = 16
rng = np.random.default_rng(7)
LUSH = [c(0x3A5A2C), c(0x4A6E36), c(0x5A8240), c(0x6E9650), c(0x9EBE80)]
img = np.zeros((AH, AW, 4), np.uint8)
for r in range(AH // T):
    for q in range(AW // T):
        img[r * T:(r + 1) * T, q * T:(q + 1) * T] = tiles.ground(int(rng.choice([0, 0, 0, 1])), LUSH).a

SQUARE = (24, 146, 216, 266)
FOUNT = (120, 214)


def street(x, y):
    return ((96 <= x < 144) and (56 <= y < 480)) or ((0 <= x < AW) and (338 <= y < 372)) or \
        ((0 <= x < AW) and (456 <= y < 480))


def square(x, y):
    return SQUARE[0] <= x < SQUARE[2] and SQUARE[1] <= y < SQUARE[3]


tiles.draw_cobbles(img, lambda x, y: street(x, y) and not square(x, y), (0, 0, AW, AH))
# náměstí: velké kamenné desky a soustředné kruhy kolem kašny
for y in range(SQUARE[1], SQUARE[3]):
    for x in range(SQUARE[0], SQUARE[2]):
        d = math.hypot((x - FOUNT[0]) / 1.4, y - FOUNT[1])
        if 46 < d < 50 or 58 < d < 60:
            col = c(0x8E8478) if int(math.degrees(math.atan2(y - FOUNT[1], x - FOUNT[0]))) % 12 else c(0x6E665C)
        else:
            slab = (x // 10 + (y // 7) % 2 * 5 // 5) % 3
            joint = x % 10 == 0 or y % 7 == 0
            col = c(0x9A9286) if joint else (c(0xC8C0B0), c(0xD6CEBE), c(0xBEB6A6))[slab]
            if (x * 13 + y * 7) % 41 == 0:
                col = c(0xE6DECE)
        img[y, x] = col
# obrubník: světlá horní hrana, stín dole, kolem ulic i náměstí
paved = np.zeros((AH, AW), bool)
for y in range(AH):
    for x in range(AW):
        paved[y, x] = street(x, y) or square(x, y)
for y in range(1, AH - 1):
    for x in range(1, AW - 1):
        if paved[y, x] and not paved[y - 1, x]:
            img[y, x] = STN[4]; img[y + 1, x] = STN[2]
        elif paved[y, x] and not paved[y + 1, x]:
            img[y, x] = STN[1]
        elif paved[y, x] and (not paved[y, x - 1] or not paved[y, x + 1]):
            img[y, x] = STN[2]

# propast: skalní hrana, tma a mlha, koruna Lumiry vzhůru nohama
VOID_Y = 504
for y in range(VOID_Y, AH):
    for x in range(AW):
        t = (y - VOID_Y) / (AH - VOID_Y)
        img[y, x, :3] = (np.array([40, 36, 52]) * (1 - t) + np.array([8, 6, 14]) * t).astype(np.uint8)


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


# slabý svit zdola (mrtvá bohyně ještě trochu světélkuje)
for y in range(VOID_Y, AH):
    for x in range(AW):
        d = math.hypot((x - 150) / 90, (y - AH) / 50)
        if d < 1:
            img[y, x, :3] = np.minimum(255, img[y, x, :3] + np.array([30, 26, 48]) * (1 - d) ** 2).astype(np.uint8)
branch(150, VOID_Y + 4, math.pi / 2, 22, 7, 5)
branch(150, VOID_Y + 20, math.pi / 2 - 0.9, 26, 4, 3)
branch(150, VOID_Y + 24, math.pi / 2 + 0.9, 30, 4, 3)
branch(46, VOID_Y + 10, math.pi / 2 + 0.25, 18, 3, 3)
for y in range(VOID_Y, AH):
    for x in range(AW):
        fog = (math.sin(x / 23 + y / 3.5) + 0.6 * math.sin(x / 11 - y / 9)) / 1.6
        band = math.exp(-((y - (VOID_Y + 40)) / 10) ** 2) + 0.6 * math.exp(-((y - (VOID_Y + 62)) / 6) ** 2)
        k = max(0, fog) * band * 0.55
        img[y, x, :3] = (img[y, x, :3] * (1 - k) + np.array([150, 144, 170]) * k).astype(np.uint8)

placed = []


def place(spr, cx, by, z=None):
    placed.append((spr, cx, by, by if z is None else z))


place(C.inner_gate(AW), 120, 60)
# blok A nad náměstím
place(C.venka_house(), 26, 146)
place(C.house(48, 34, 6, C.ROOF_RED, "plaster", "green", seed=2, storeys=2), 70, 146)
place(C.bakery(), 176, 146)
place(C.house(40, 22, 5, C.ROOF_SLATE, "stone", "red", windows=1, seed=5), 222, 146)
for (x, y) in ((146, 150), (156, 150)):
    place(C.barrel(), x, y)
place(C.crate(), 206, 152)
# náměstí
place(C.fountain(), FOUNT[0], FOUNT[1] + 24)
place(C.stall("red", "apples"), 52, 216); place(C.stall("blue", "bread"), 188, 216)
place(C.crate(), 34, 222); place(C.barrel(), 206, 222)
place(C.bench(), 66, 262); place(C.bench(), 174, 262)
place(C.notice_board(), 40, 184)
for (x, y) in ((28, 150), (212, 150), (28, 268), (212, 268)):
    place(C.city_lamp(), x, y)
for (x, y) in ((86, 178), (154, 178)):
    place(C.planter_tree(int(x)), x, y)
place(C.bunting(176), 120, 164, z=600)                        # girlanda nad náměstím, vždy navrchu
# blok B pod náměstím
place(C.hedge(96), 48, 282)
place(C.house(48, 34, 6, C.ROOF_TEAL, "timber", "blue", seed=3, storeys=2), 26, 338)
place(C.house(44, 24, 6, C.ROOF_RED, "plaster", "ochre", seed=8), 72, 338)
place(C.arena(), 184, 338)
place(C.flower_bed(36), 26, 350); place(C.flower_bed(30), 72, 350)
# blok C: věž, domky, sklad
place(C.scale_tower(), 120, 456)
place(C.house(40, 22, 5, C.ROOF_SLATE, "stone", "red", windows=1, seed=11), 32, 452)
place(C.house(36, 20, 5, C.ROOF_OCHRE, "plaster", "green", windows=1, seed=12), 70, 452)
place(C.shed(), 214, 446)
place(C.planter_tree(3), 178, 448)
# okraj propasti
place(C.cliff_edge(AW), 120, VOID_Y + 34, z=0)
place(C.balustrade(148), 74, VOID_Y - 4)
place(C.balustrade(32), 224, VOID_Y - 4)
place(C.elevator(), 182, VOID_Y + 10)

# zeleň: stromy u brány, mezi domy a dole u věže
for (x, y) in ((20, 100), (52, 96), (196, 96), (226, 104), (176, 300), (10, 420), (226, 424), (160, 438)):
    place(C.round_tree(x + y), x, y)
place(C.hedge(48), 40, 116); place(C.hedge(48), 200, 120)
place(C.flower_bed(30), 168, 386); place(C.flower_bed(30), 216, 386)
for (spr, cx, by, z) in sorted(placed, key=lambda p: p[3]):
    pix.paste(img, spr, int(cx - spr.w // 2), int(by - spr.h))
out = Image.fromarray(img[:, :, :3])
out.resize((AW * 3, AH * 3), Image.NEAREST).save(os.path.join(HERE, f"ozvena_{VER}.png"))
print("ok", VER)
