"""
Díl 2 – OBJEKTY Ztichlého sadu (Akt II): ovocné stromy, Stará jabloň, kamenná zídka s vrátky,
studna, úly, zahradníkova bouda, koš s jablky a žebřík. Kreslí se v denních barvách – noc
dodá až skládání (přebarvení do měsíčního světla) a světla se přidají nakonec.
"""
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ashplain"))
from pix import Spr, c, outline, blobs  # noqa: E402
from objects import OUT, BARK, STONE, WOOD  # noqa: E402

LEAF = [c(0x22381E), c(0x386030), c(0x528A42), c(0x74AE5A)]
APPLE = [c(0x8A1E1A), c(0xC8362A), c(0xF06A50)]
GOLD = [c(0x9A6A10), c(0xE0A830), c(0xFFE08A)]


def fruit_tree(seed=0, fruit=APPLE):
    """Ovocný strom 32 × 40: kulatá koruna z koulí, jablka se světlým odleskem."""
    r = np.random.default_rng(seed)
    s = Spr(32, 40)
    for y in range(27, 38):
        for x in range(14, 18):
            s.px(x, y, BARK[3] if x == 14 else BARK[2] if x < 17 else BARK[1])
    s.px(13, 37, BARK[1]); s.px(18, 37, BARK[1])
    j = lambda v: v + int(r.integers(-1, 2))
    blobs(s, [(j(9), 20, 7.5), (j(23), 20, 7.5), (16, j(21), 8), (j(11), 12, 7), (j(21), 12, 7), (16, j(9), 7)], LEAF)
    outline(s, OUT)
    spots = [(8, 18), (22, 14), (14, 24), (25, 22), (11, 10), (19, 19), (6, 23), (17, 7)]
    for (x, y) in spots[: 5 + seed % 4]:
        s.px(x, y, fruit[1]); s.px(x + 1, y, fruit[1]); s.px(x, y + 1, fruit[0]); s.px(x + 1, y + 1, fruit[0]); s.px(x, y, fruit[2])
    return s


def old_apple_tree():
    """Stará jabloň 56 × 60: sukovitý rozdvojený kmen, široká koruna, zlatá jablka."""
    s = Spr(56, 60)
    for y in range(34, 58):                                         # kmen se rozdvojuje
        w = 5 if y > 46 else 4
        for x in range(28 - w, 28 + w):
            k = 3 if x < 26 else 2 if x < 30 else 1
            if (x + y * 3) % 7 == 0:
                k = 0                                               # rýhy kůry
            s.px(x, y, BARK[k])
    s.line(26, 40, 16, 30, BARK[2], w=3); s.line(30, 40, 40, 30, BARK[1], w=3)
    for y in range(54, 60):                                         # kořeny
        w = 5 + (y - 53) * 2
        for x in range(28 - w, 28 + w):
            if (x - 28) % 4 != 1 or y < 56:
                s.px(x, y, BARK[2] if x < 28 else BARK[1])
    s.ellipse(27, 48, 2, 3, BARK[0])                                # dutina
    blobs(s, [(10, 30, 9), (46, 30, 9), (28, 32, 10), (16, 20, 10), (40, 20, 10), (28, 14, 11),
              (14, 10, 7), (42, 10, 7), (28, 22, 9)], LEAF)
    outline(s, OUT)
    for (x, y) in ((12, 26), (24, 12), (38, 18), (44, 30), (30, 28), (18, 34), (34, 8), (8, 16), (48, 22)):
        s.px(x, y, GOLD[1]); s.px(x + 1, y, GOLD[1]); s.px(x, y + 1, GOLD[0]); s.px(x + 1, y + 1, GOLD[0]); s.px(x, y, GOLD[2])
    return s


def stone_wall(w):
    """Suchá kamenná zídka w × 14: oblé kameny, mech na vršku."""
    s = Spr(w, 14)
    r = np.random.default_rng(w)
    x = 0
    row_y = [(2, 6), (7, 6)]
    for (y0, h) in row_y:
        x = -int(r.integers(0, 4))
        while x < w:
            bw = int(r.integers(5, 9))
            for yy in range(y0, y0 + h):
                for xx in range(x, x + bw):
                    e = (xx - x) in (0, bw - 1) or yy in (y0, y0 + h - 1)
                    k = 0 if e and yy == y0 + h - 1 else 1 if e else 3 if yy < y0 + 2 else 2
                    s.px(xx, yy, STONE[k])
            x += bw
    for xx in range(w):                                              # mech
        if (xx * 5) % 7 < 3:
            s.px(xx, 2, LEAF[2]); s.px(xx, 3, LEAF[1])
    return outline(s, OUT)


def gate_open():
    """Otevřená dřevěná vrátka 34 × 20 (dvě křídla vyklopená ke zdi) se sloupky."""
    s = Spr(34, 20)
    for px0 in (0, 29):
        s.rect(px0, 0, px0 + 4, 19, STONE[2]); s.rect(px0, 0, px0 + 4, 2, STONE[3]); s.px(px0 + 2, 0, STONE[4])
    for (x0, d) in ((5, 1), (28, -1)):
        for i in range(6):
            x = x0 + i * d
            s.vline(x, 6 - i // 2, 17 - i // 3, WOOD[2] if i % 2 else WOOD[3])
        s.line(x0, 8, x0 + 5 * d, 6, WOOD[1]); s.line(x0, 14, x0 + 5 * d, 13, WOOD[1])
    return outline(s, OUT)


def well():
    """Studna 22 × 26: kamenná obruba, dřevěná stříška, rumpál a vědro."""
    s = Spr(22, 26)
    s.ellipse(11, 20, 10, 5, STONE[2]); s.ellipse(11, 19, 7.5, 3.2, c(0x12161C))
    for a in range(0, 360, 30):
        x = 11 + 9.5 * math.cos(math.radians(a)); y = 20 + 4.6 * math.sin(math.radians(a))
        s.px(round(x), round(y), STONE[1])
    for x in range(1, 21):
        s.px(x, 20 + (1 if 3 < x < 18 else 0) + 3, STONE[1])
    s.rect(2, 6, 3, 19, WOOD[2]); s.rect(18, 6, 19, 19, WOOD[1])
    for y in range(0, 7):
        w = 4 + y * 2
        for x in range(11 - w // 2 - 1, 11 + w // 2 + 2):
            s.px(x, y, WOOD[3] if y % 2 else WOOD[2])
    s.hline(4, 17, 9, WOOD[1]); s.rect(9, 10, 12, 13, WOOD[2]); s.hline(9, 12, 10, STONE[2])
    return outline(s, OUT)


def hive():
    """Dřevěný úl 12 × 16 se stříškou a česnem."""
    s = Spr(12, 16)
    s.rect(1, 5, 10, 14, c(0xD8C080)); s.rect(1, 5, 2, 14, c(0xF0DCA0)); s.hline(1, 10, 9, c(0xA8904E))
    for y in range(0, 5):
        s.hline(y // 2, 11 - y // 2, 4 - y, c(0x8A5A3A) if y else c(0xA87050))
    s.rect(5, 12, 6, 13, c(0x2A1E14))
    s.rect(1, 15, 10, 15, WOOD[1])
    return outline(s, OUT)


def hut():
    """Zahradníkova bouda 40 × 38: prkenné stěny, šikmá střecha z šindele, dveře, lucerna."""
    s = Spr(40, 38)
    for y in range(0, 16):
        inset = max(0, (15 - y) // 2)
        for x in range(inset, 40 - inset):
            s.px(x, y, c(0x6E4A2E) if (y % 3) == 0 else c(0x8E6440) if (x + y // 3) % 5 else c(0x7A5436))
    s.hline(0, 39, 15, c(0x3A2414))
    for y in range(16, 37):
        for x in range(2, 38):
            s.px(x, y, WOOD[3] if x % 5 else WOOD[1])
    s.rect(16, 22, 24, 36, WOOD[1]); s.rect(17, 23, 23, 36, c(0x4A3220)); s.px(22, 30, c(0xC8A050))
    s.rect(5, 21, 12, 27, c(0x2A3240)); s.hline(5, 12, 24, WOOD[1]); s.vline(8, 21, 27, WOOD[1])
    s.rect(28, 20, 30, 24, c(0x2A2426)); s.px(29, 22, c(0xFFD27A))               # lucerna
    s.rect(1, 37, 38, 37, STONE[1])
    return outline(s, OUT)


def basket():
    s = Spr(12, 9)
    s.rect(1, 3, 10, 8, WOOD[2])
    for x in range(1, 11, 2):
        s.vline(x, 3, 8, WOOD[3])
    for x in range(2, 10, 2):
        s.px(x, 2, APPLE[1]); s.px(x + 1, 2, APPLE[0]); s.px(x, 1, APPLE[2] if x % 4 else APPLE[1])
    return outline(s, OUT)


def ladder():
    s = Spr(10, 22)
    s.line(1, 21, 3, 0, WOOD[2], w=2); s.line(7, 21, 9, 0, WOOD[2], w=2)
    for y in range(3, 21, 4):
        s.hline(2 + (21 - y) * 3 // 21, 7 + (21 - y) * 3 // 21, y, WOOD[3])
    return outline(s, OUT)


def parts():
    return [fruit_tree(0), fruit_tree(3), old_apple_tree(), stone_wall(48), gate_open(), well(), hive(), hut(), basket(), ladder()]
