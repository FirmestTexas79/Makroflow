"""
Díl 2 – OBJEKTY Popelavé pláně: samostatné sprity s obrysem a stínováním ze shora zleva.
Každá funkce vrací Spr; [FOOT] říká, kolik px od spodku spritu je „pata“ (kolizní plocha).
"""
import math
import numpy as np
from pix import Spr, c, outline, blobs

OUT = c(0x1E1C1A)

# popelem zaprášené listí (desaturovaná zelená), kůra, ohořelé dřevo
LEAF = [c(0x2C3428), c(0x48543E), c(0x66735A), c(0x8A967A)]
ASH_DUST = c(0xCDD0C2)
BARK = [c(0x2E241C), c(0x4E3C2E), c(0x6E5644), c(0x8C725A)]
CHAR = [c(0x1E1A1C), c(0x342E32), c(0x4E464C), c(0x6E666C)]
STONE = [c(0x3A3632), c(0x5E5850), c(0x847C72), c(0xA8A094), c(0xCAC2B4)]
WOOD = [c(0x3A2818), c(0x5E4228), c(0x86603C), c(0xAC8456), c(0xCCA878)]


def tree(seed=0):
    """Popelavý strom 32 × 40 – zaoblená koruna z koulí (šupiny), kmen s kořeny."""
    r = np.random.default_rng(seed)
    s = Spr(32, 40)
    j = lambda v: v + r.integers(-1, 2)
    # kmen
    for y in range(26, 38):
        for x in range(13, 19):
            k = 0 if x == 13 else 3 if x == 14 else 2 if x < 17 else 1
            s.px(x, y, BARK[k])
    for (x, y) in ((12, 37), (19, 37), (11, 38), (20, 38), (12, 38), (19, 38)):
        s.px(x, y, BARK[1])
    s.px(15, 31, BARK[0]); s.px(16, 34, BARK[0])
    blobs(s, [(j(8), j(22), 7.5), (j(24), j(22), 7.5), (16, j(23), 8),
              (j(10), j(14), 7), (j(22), j(14), 7), (16, j(9), 7.5), (16, 17, 6)],
          LEAF, top_tint=ASH_DUST)
    return outline(s, OUT)


def dead_tree(seed=0, mushrooms=True):
    """Ohořelý strom 28 × 40: holé větve, žhavá puklina, u paty rudé houby."""
    r = np.random.default_rng(seed)
    s = Spr(28, 40)
    # kmen (širší dole)
    for y in range(12, 38):
        w = 2 if y < 22 else 3
        for x in range(14 - w, 14 + w):
            k = 3 if x == 14 - w + 1 else 2 if x < 14 else 1
            s.px(x, y, CHAR[k])
    s.px(12, 38, CHAR[1]); s.px(16, 38, CHAR[1]); s.px(11, 38, CHAR[1]); s.px(17, 38, CHAR[1])
    # větve
    br = [((14, 20), (5, 9)), ((14, 16), (23, 6)), ((13, 13), (8, 2)), ((15, 12), (19, 1)),
          ((8, 11), (3, 6)), ((20, 9), (25, 11)), ((14, 24), (21, 17))]
    for (a, b) in br:
        s.line(a[0], a[1], b[0], b[1], CHAR[2], w=2)
        s.line(a[0], a[1] - 1, b[0], b[1] - 1, CHAR[3])
    # žhavá puklina
    for y in (27, 28, 29):
        s.px(13, y, c(0xE8682E))
    s.px(13, 28, c(0xFFC66A))
    outline(s, OUT)
    if mushrooms:
        for (x, y) in ((8, 37), (19, 36), (21, 38)):
            s.px(x, y, c(0xC63A32)); s.px(x + 1, y, c(0xE8604A)); s.px(x - 1, y, c(0x8A2420))
            s.px(x, y + 1, c(0xEAD8C4))
    return s


def bush(seed=0):
    s = Spr(16, 14)
    blobs(s, [(5, 8, 4.5), (11, 8, 4.5), (8, 5, 5)], [c(0x3A3424), c(0x5E5636), c(0x847A50), c(0xA89C6C)], top_tint=ASH_DUST)
    return outline(s, OUT)


def rock(big=False):
    s = Spr(20 if big else 14, 14 if big else 10)
    if big:
        blobs(s, [(7, 8, 6), (13, 9, 5.5), (10, 6, 5)], STONE[:4])
    else:
        blobs(s, [(5, 6, 4), (9, 6, 3.6)], STONE[:4])
    return outline(s, OUT)


def sign():
    """Dřevěná cedule na sloupku (16 × 18)."""
    s = Spr(16, 18)
    s.rect(7, 9, 8, 17, WOOD[1]); s.vline(7, 9, 17, WOOD[2])
    s.rect(1, 1, 14, 10, WOOD[3])
    s.hline(1, 14, 1, WOOD[4]); s.hline(1, 14, 10, WOOD[1])
    for y in (4, 7):
        s.hline(3, 12, y, WOOD[1])
    s.px(3, 5, WOOD[2]); s.px(12, 8, WOOD[2])
    return outline(s, OUT)


def lamp(lit=True):
    """Kovová lucerna 10 × 28; svítící má teplé sklo."""
    s = Spr(10, 28)
    s.rect(4, 8, 5, 26, CHAR[2]); s.vline(4, 8, 26, CHAR[3])
    s.rect(3, 26, 6, 27, CHAR[1])
    s.rect(2, 1, 7, 8, CHAR[1])
    glass = [c(0xFFE6A0), c(0xFFC860), c(0xE89A3A)] if lit else [c(0x5A5E64), c(0x44484E), c(0x34363A)]
    s.rect(3, 3, 6, 6, glass[1]); s.px(3, 3, glass[0]); s.px(4, 3, glass[0]); s.hline(3, 6, 6, glass[2])
    s.hline(1, 8, 1, CHAR[2]); s.px(4, 0, CHAR[2]); s.px(5, 0, CHAR[2])
    return outline(s, OUT)


def campfire():
    """Vyhaslé ohniště 18 × 14: kruh kamenů, zuhelnatělá polena, žhavé uhlíky."""
    s = Spr(18, 14)
    s.ellipse(9, 8, 7, 4.5, c(0x2A2224))
    s.line(4, 10, 14, 5, BARK[1], w=2); s.line(4, 5, 14, 10, BARK[2], w=2)
    for (x, y, col) in ((8, 7, 0xFF8A3A), (10, 8, 0xFFC66A), (9, 9, 0xE8682E), (7, 8, 0xFF8A3A), (11, 6, 0xE8682E)):
        s.px(x, y, c(col))
    for a in range(0, 360, 40):
        x = 9 + 7.5 * math.cos(math.radians(a)); y = 8 + 4.8 * math.sin(math.radians(a))
        s.rect(round(x) - 1, round(y) - 1, round(x), round(y), STONE[2])
        s.px(round(x) - 1, round(y) - 1, STONE[3])
    return outline(s, OUT)


def scarecrow():
    """Strašák 18 × 28 s potrhaným kabátem."""
    s = Spr(18, 28)
    s.rect(8, 6, 9, 27, WOOD[1]); s.vline(8, 6, 27, WOOD[2])
    s.rect(1, 11, 16, 12, WOOD[2])
    coat = [c(0x3E4038), c(0x5C5E50), c(0x7A7C68)]
    for y in range(10, 21):
        w = 4 + (y - 10) // 3
        for x in range(9 - w, 9 + w):
            s.px(x, y, coat[2] if x < 8 else coat[1])
    for x in (4, 7, 11, 13):
        s.px(x, 21, coat[0]); s.px(x, 22, coat[0])
    s.ellipse(8.5, 6, 3.5, 3.5, c(0xC8B080))
    s.px(7, 6, OUT); s.px(10, 6, OUT); s.hline(7, 10, 8, c(0x8A7448))
    s.rect(4, 1, 13, 3, c(0x6A5034)); s.rect(6, 0, 11, 1, c(0x6A5034)); s.hline(4, 13, 3, c(0x4A3622))
    return outline(s, OUT)


def cart():
    """Převrácený vůz 30 × 20 napůl v popelu."""
    s = Spr(30, 20)
    s.rect(2, 4, 25, 12, WOOD[2]); s.hline(2, 25, 4, WOOD[3]); s.hline(2, 25, 12, WOOD[1])
    for x in (8, 14, 20):
        s.vline(x, 5, 11, WOOD[1])
    s.line(25, 8, 29, 6, WOOD[1], w=2)
    for (cx, cy) in ((7, 14), (21, 14)):
        s.ellipse(cx, cy, 4.5, 4.5, WOOD[1]); s.ellipse(cx, cy, 2.5, 2.5, WOOD[3]); s.px(cx, cy, WOOD[0])
    outline(s, OUT)
    for x in range(0, 30):          # popel navátý přes spodek
        for y in range(16 + (x % 5 == 0), 20):
            s.px(x, y, c(0xBAB49C) if y == 16 + (x % 5 == 0) else c(0xA6A088))
    return s


def milestone():
    """Mezník 12 × 18 s vytesaným S."""
    s = Spr(12, 18)
    for y in range(2, 17):
        for x in range(1, 11):
            if y < 4 and (x in (1, 10) or (y == 2 and x in (2, 9))):
                continue
            s.px(x, y, STONE[3] if x < 5 else STONE[2])
    s.hline(2, 9, 2, STONE[4])
    for (x, y) in ((4, 6), (5, 6), (6, 6), (4, 7), (4, 8), (5, 8), (6, 8), (6, 9), (4, 10), (5, 10), (6, 10)):
        s.px(x, y, STONE[0])
    s.hline(0, 11, 17, STONE[1])
    return outline(s, OUT)


def ruined_house():
    """Zřícený dům 48 × 50: propadlá střecha, začouzené zdi, zabedněné dveře, rozbité okno."""
    s = Spr(48, 50)
    roof = [c(0x3E1A16), c(0x5E2620), c(0x80362A), c(0xA44C38), c(0xC46A4E)]
    for y in range(2, 24):
        inset = max(0, (24 - y) // 3)
        for x in range(1 + inset, 47 - inset):
            row = (y - 2) % 4
            col = roof[3] if row == 0 else roof[2] if row < 3 else roof[1]
            if (x + (y // 4) * 3) % 8 == 0 and row:
                col = roof[1]
            s.px(x, y, col)
    s.hline(9, 38, 2, roof[4]); s.hline(0, 47, 23, roof[0])
    # propadlá část: tma a trámy
    hole = [(x, y) for y in range(5, 20) for x in range(26, 41) if (x - 33) ** 2 / 64 + (y - 12) ** 2 / 49 < 1 + 0.2 * math.sin(x * 1.7 + y)]
    for (x, y) in hole:
        s.px(x, y, c(0x161214))
    s.line(26, 18, 39, 7, BARK[2], w=2); s.line(30, 5, 34, 19, BARK[1])
    # zeď
    wall = [c(0x8E8270), c(0xB4A890), c(0xD6CCB4), c(0xE6DECA)]
    for y in range(24, 47):
        for x in range(3, 45):
            col = wall[2] if x > 6 else wall[1]
            if y < 27:
                col = wall[0]
            if (x + y) % 9 == 0 and y > 28:
                col = wall[1]
            s.px(x, y, col)
    # saze nad okny a u díry
    for x in range(26, 44):
        for y in range(24, 24 + int(6 + 3 * math.sin(x))):
            if (x + y) % 2 == 0:
                s.px(x, y, wall[0])
    # okna
    s.rect(7, 30, 16, 38, c(0x26282C)); s.rect(8, 31, 15, 34, c(0x5A6A74)); s.hline(7, 16, 34, c(0x1A1A1E)); s.vline(11, 30, 38, c(0x1A1A1E))
    s.line(8, 35, 15, 31, c(0x8A9AA4))            # prasklina
    s.rect(32, 30, 41, 38, c(0x161416)); s.line(32, 30, 41, 38, c(0x3A3A40))
    # zabedněné dveře
    s.rect(20, 34, 27, 46, WOOD[1])
    s.line(20, 37, 27, 41, WOOD[3], w=2); s.line(20, 43, 27, 38, WOOD[2], w=2)
    # podezdívka a suť
    s.rect(2, 46, 45, 47, STONE[1]); s.hline(2, 45, 46, STONE[2])
    outline(s, OUT)
    for (x, y) in ((4, 48), (5, 48), (40, 48), (41, 49), (44, 48), (12, 49)):
        s.px(x, y, STONE[2])
    return s


def gatehouse():
    """Strážnice Ozvěny 64 × 64: bílá fasáda, tyrkysová plochá střecha, prosklený vchod, zlatý štítek."""
    s = Spr(64, 64)
    roof = [c(0x163432), c(0x245250), c(0x34726C), c(0x4A948C), c(0x78C2B6)]
    for y in range(0, 20):
        for x in range(1, 63):
            col = roof[3] if y < 3 else roof[2] if y < 15 else roof[1]
            if y in (6, 11) and 4 < x < 59:
                col = roof[1]
            if y == 1:
                col = roof[4]
            s.px(x, y, col)
    s.hline(0, 63, 19, roof[0])
    for x in range(8, 57, 8):                    # žebrování střechy
        s.vline(x, 4, 14, roof[1])
    white = [c(0x8E887C), c(0xC8C2B6), c(0xE6E2D8), c(0xF8F6F0)]
    for y in range(20, 62):
        for x in range(2, 62):
            col = white[3] if 6 < x < 57 else white[2]
            if y < 23:
                col = white[1]
            s.px(x, y, col)
    for px0 in (2, 57):                           # pilíře
        s.rect(px0, 20, px0 + 4, 61, white[2]); s.vline(px0, 20, 61, white[1]); s.vline(px0 + 4, 20, 61, white[1])
    # okna
    for wx in (9, 43):
        s.rect(wx, 30, wx + 11, 40, c(0x2E5058)); s.rect(wx + 1, 31, wx + 10, 34, c(0x6AA0A8))
        s.hline(wx, wx + 11, 41, white[0]); s.vline(wx + 6, 30, 40, white[1])
    # vchod
    s.rect(22, 40, 41, 61, white[0])
    s.rect(24, 42, 39, 61, c(0x1E3036)); s.rect(25, 43, 31, 61, c(0x2E4A52)); s.rect(32, 43, 38, 61, c(0x2A424A))
    s.line(26, 44, 29, 50, c(0x7AB4BC)); s.line(33, 44, 36, 50, c(0x6AA0A8))
    s.vline(31, 42, 61, c(0x14222A))
    # štítek nad vchodem
    s.rect(24, 31, 39, 37, c(0xB8902E)); s.rect(25, 32, 38, 36, c(0xE6C258)); s.hline(27, 36, 34, c(0x8A6820))
    # truhlíky s květinami
    for fx in (9, 43):
        s.rect(fx, 55, fx + 11, 60, WOOD[2]); s.hline(fx, fx + 11, 55, WOOD[3])
        for i, x in enumerate(range(fx + 1, fx + 11, 2)):
            s.px(x, 54, c(0xE85A6A) if i % 2 else c(0xF4D060)); s.px(x, 53, c(0x5A8A4A))
    s.rect(0, 62, 63, 63, STONE[2]); s.hline(0, 63, 62, STONE[3])
    return outline(s, OUT)


def city_wall(w=48):
    """Hradba Ozvěny w × 56: bílé kvádry, cimbuří, za ní červené střechy a věž."""
    s = Spr(w, 56)
    roof = [c(0x5A1E1A), c(0x8A3028), c(0xB44A3A), c(0xD86A52)]
    for i, hx in enumerate(range(2, w - 8, 18)):     # střechy domů za hradbou
        top = 4 + (i % 2) * 5
        for y in range(top, 22):
            inset = max(0, 8 - (y - top))
            for x in range(hx + inset, hx + 18 - inset):
                s.px(x, y, roof[2] if (y - top) % 3 else roof[3])
        s.hline(hx, hx + 17, 21, roof[0])
    stone = [c(0x8E887C), c(0xC4BEB2), c(0xE6E2D8), c(0xF6F4EE)]
    for y in range(20, 56):
        for x in range(w):
            course = (y - 20) // 5
            joint = (y - 20) % 5 == 0 or (x + course % 2 * 6) % 12 == 0
            s.px(x, y, stone[1] if joint else stone[3] if (y - 20) % 5 == 1 else stone[2])
    for x in range(0, w, 8):                          # cimbuří
        s.rect(x, 16, x + 4, 20, stone[2]); s.hline(x, x + 4, 16, stone[3])
    s.rect(0, 53, w - 1, 55, stone[0])
    return outline(s, OUT)


def world_arch():
    """Druhá strana Brány světů 64 × 52: dva pilíře s tyrkysovými runami, puklý klenák, kamenná plotna."""
    s = Spr(64, 52)
    rune = c(0x6AE8D8)
    # plotna mezi pilíři (vzadu)
    for y in range(38, 50):
        for x in range(12, 52):
            s.px(x, y, STONE[3] if (x // 6 + y // 4) % 2 else STONE[2])
    s.hline(12, 51, 38, STONE[4])
    for px0 in (4, 50):
        for y in range(6, 50):
            for x in range(px0, px0 + 10):
                col = STONE[3] if x < px0 + 4 else STONE[2]
                if (y - 6) % 11 == 0:
                    col = STONE[1]
                s.px(x, y, col)
        s.rect(px0 - 1, 46, px0 + 10, 49, STONE[1]); s.hline(px0 - 1, px0 + 10, 46, STONE[2])
        for ry in (16, 24, 32):
            s.px(px0 + 4, ry, rune); s.px(px0 + 5, ry, rune); s.px(px0 + 4, ry + 1, rune)
    # klenák přes celou šířku, vpravo puklý a sesunutý
    for x in range(1, 63):
        drop = 0 if x < 40 else 1 if x < 46 else 2
        for y in range(0 + drop, 7 + drop):
            s.px(x, y, STONE[4] if y == drop else STONE[3] if y < 4 + drop else STONE[2])
    s.line(40, 0, 38, 8, STONE[0]); s.line(46, 1, 45, 8, STONE[0])
    for x in (20, 21, 30, 31):
        s.px(x, 3, rune)
    s.px(25, 2, c(0x4ECB6A)); s.px(26, 3, c(0x4ECB6A)); s.px(25, 4, c(0x4ECB6A))
    return outline(s, OUT)


def parts():
    return [tree(1), tree(2), dead_tree(3), bush(4), rock(), rock(True), sign(), lamp(True), lamp(False),
            campfire(), scarecrow(), cart(), milestone(), ruined_house(), gatehouse(), city_wall(), world_arch()]
