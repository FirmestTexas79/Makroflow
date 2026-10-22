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
from pixart import K, STN, WD, ROOF, PL, GLASS, MOSS, stones, shingles, box_outline, from_ascii  # noqa: E402

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
    """Kamenná zídka w × 18: stříška z plochých kamenů, pod ní řady jednotlivě stínovaných kamenů, mech."""
    s = Spr(w, 18)
    stones(s, 0, w - 1, 5, 16, h=4, w=(5, 9), seed=w)
    for x in range(w):                                             # plochá stříška zídky
        s.px(x, 2, STN[4]); s.px(x, 3, STN[3]); s.px(x, 4, STN[1])
        if x % 7 == 0:
            s.px(x, 3, STN[1])
    s.hline(0, w - 1, 1, K); s.hline(0, w - 1, 17, K); s.vline(0, 1, 17, K); s.vline(w - 1, 1, 17, K)
    for x in range(2, w - 2):                                      # mech přes stříšku
        if (x * 5) % 11 < 4:
            s.px(x, 1, MOSS[1]); s.px(x, 2, MOSS[2])
            if (x * 3) % 7 == 0:
                s.px(x, 0, MOSS[0])
        if (x * 7) % 13 == 0:
            s.px(x, 5, MOSS[1]); s.px(x, 6, MOSS[0])               # mech stékající do spár
    return s


def gate_pillar(cap_moss=True):
    """Kamenný sloupek brány 12 × 30 s jehlancovou hlavicí."""
    s = Spr(12, 30)
    stones(s, 1, 10, 9, 28, h=4, w=(4, 6), seed=31)
    for y in range(2, 9):                                          # hlavice
        half = (y - 1) // 1.4
        for x in range(int(6 - half), int(6 + half) + 1):
            if 0 <= x < 12:
                s.px(x, y, STN[4] if x < 6 else STN[2])
    s.hline(0, 11, 8, STN[3]); s.hline(0, 11, 9, STN[1])
    s.px(5, 1, STN[4]); s.px(6, 1, STN[3])
    from pix import outline as _o
    _o(s, K)
    if cap_moss:
        s.px(3, 8, MOSS[2]); s.px(4, 8, MOSS[1]); s.px(8, 8, MOSS[1])
    return s


def gate_leaf(flip=False):
    """Otevřené křídlo vrátek 10 × 18 (prkna, příčky, kování) – natočené ke sloupku."""
    rows = [
        "kkk.......",
        "kvkk......",
        "kVvWkk....",
        "kVvWWwkk..",
        "kVvWWwWwk.",
        "kVvWWwWwk.",
        "kNNNNNNNk.",
        "kVvWWwWwk.",
        "kVvWWwWwk.",
        "kVvWWwWwk.",
        "kVvWWwWwk.",
        "kNNNNNNNk.",
        "kVvWWwWwk.",
        "kVvWWwWwk.",
        "kVvWWwWwk.",
        "kVvWWwWwk.",
        "kkkkkkkkk.",
        "..........",
    ]
    pal = {"k": K, "v": WD[3], "V": WD[4], "W": WD[2], "w": WD[1], "N": c(0x3A3A44)}
    sp = from_ascii(rows, pal)
    if flip:
        sp.a = sp.a[:, ::-1].copy()
    return sp


def well():
    """Studna 26 × 32: válcová kamenná obruba, tmavá voda s odleskem, sloupky, šindelová stříška, rumpál s vědrem."""
    s = Spr(26, 32)
    # obruba – válec: přední stěna z kamenů, horní hrana jako elipsa
    stones(s, 2, 23, 20, 30, h=4, w=(4, 6), seed=7)
    for y in range(20, 31):                                        # stín válce vpravo, světlo vlevo
        for x in range(2, 24):
            t = (x - 13) / 11
            if t > 0.45 and s.a[y, x, 3]:
                s.a[y, x, :3] = (s.a[y, x, :3] * 0.72).astype(np.uint8)
            elif t < -0.55 and s.a[y, x, 3]:
                s.a[y, x, :3] = np.minimum(255, s.a[y, x, :3] * 1.12).astype(np.uint8)
    for y in range(16, 22):
        for x in range(2, 24):
            d = ((x - 12.5) / 11) ** 2 + ((y - 19.5) / 3.6) ** 2
            if d <= 1:
                s.px(x, y, STN[4] if d > 0.62 and y < 19 else STN[3] if d > 0.62 else c(0x101820))
    s.px(9, 19, GLASS[2]); s.px(10, 19, GLASS[3]); s.px(15, 20, GLASS[1])          # odlesk vody
    # sloupky a příčka s rumpálem
    for px0 in (3, 21):
        s.rect(px0, 6, px0 + 1, 18, WD[2]); s.vline(px0, 6, 18, WD[3])
    s.rect(4, 9, 21, 10, WD[2]); s.hline(4, 21, 9, WD[3])
    s.rect(11, 8, 14, 11, WD[1]); s.vline(12, 11, 14, c(0xC8B08A))                 # rumpál a lano
    s.rect(10, 15, 14, 18, WD[2]); s.hline(10, 14, 15, STN[3]); s.px(10, 16, WD[3]) # vědro
    shingles(s, [(0, 10, 15), (2, 6, 19), (4, 2, 23)])
    s.hline(1, 24, 7, ROOF[0])
    from pix import outline as _o
    return _o(s, K)


def skep():
    """Slaměný úl (koš) 14 × 16 na dřevěném podstavci – vodorovné prstence slámy, česno."""
    rows = [
        "....kkkkkk....",
        "...kOYYOOOk...",
        "..kOYOOOOOok..",
        "..knoooooonk..",
        ".kOYOOOOOOOok.",
        ".knoooooooonk.",
        "kOYOOOOOOOOOok",
        "knoooooooonnnk",
        "kOYOOOOOOOOOok",
        "knoooookkonnnk",
        "kOOOOOkddkOOok",
        "kkkkkkkkkkkkkk",
        ".kVWWWWWWWWwk.",
        ".kwwwwwwwwwwk.",
        "..kw......wk..",
        "..kk......kk..",
    ]
    pal = {"k": K, "O": c(0xD8B058), "Y": c(0xF4DC8C), "o": c(0xAE8636), "n": c(0x86621E), "d": c(0x140C06),
           "V": WD[4], "W": WD[3], "w": WD[1]}
    return from_ascii(rows, pal)


def cottage():
    """
    Zahradníkova chalupa 50 × 50 (hrázděná): šindelová střecha s hřebenáčem a kamenným komínem,
    okapový stín, bílá omítka v dřevěném rámu se vzpěrami, okno s truhlíkem, klenuté dveře,
    lucerna, kamenná podezdívka a schod. Každý prvek má vlastní obrys.
    """
    s = Spr(50, 50)
    # střecha: řady šindelů, každá o kus širší
    rows = []
    for i, y in enumerate(range(4, 22, 3)):
        rows.append((y, 9 - i * 1 - (i > 2), 40 + i * 1 + (i > 2)))
    shingles(s, rows)
    s.hline(9, 40, 3, STN[4]); s.hline(9, 40, 2, K)                       # hřebenáč
    for (y, x0, x1) in rows:
        s.px(x0 - 1, y, K); s.px(x0 - 1, y + 1, K); s.px(x0 - 1, y + 2, K)
        s.px(x1 + 1, y, K); s.px(x1 + 1, y + 1, K); s.px(x1 + 1, y + 2, K)
    s.hline(1, 48, 22, ROOF[0]); s.hline(1, 48, 23, K)                    # okap
    # komín (přes střechu)
    stones(s, 35, 40, 0, 9, h=3, w=(3, 3), seed=5)
    box_outline(s, 34, 0, 41, 10); s.hline(35, 40, 1, STN[4]); s.hline(34, 41, 10, ROOF[0])
    # stěna: omítka + hrázdění
    for y in range(24, 45):
        for x in range(3, 47):
            s.px(x, y, PL[2] if y > 26 else PL[0] if y == 24 else PL[1])   # okapový stín nahoře
    for bx in (3, 4, 24, 25, 45, 46):
        s.vline(bx, 24, 44, WD[1] if bx % 2 else WD[2])
    s.hline(3, 46, 34, WD[2]); s.hline(3, 46, 35, WD[1])
    s.line(5, 44, 12, 36, WD[2], w=2); s.line(44, 33, 38, 26, WD[2], w=2)   # vzpěry
    # okno vlevo
    s.rect(8, 26, 20, 33, WD[3]); s.rect(9, 27, 19, 32, GLASS[1])
    s.rect(9, 27, 13, 29, GLASS[2]); s.px(10, 27, GLASS[3]); s.px(9, 28, GLASS[3])
    s.vline(14, 27, 32, WD[1]); s.hline(9, 19, 30, WD[1])
    box_outline(s, 8, 26, 20, 33)
    s.rect(7, 34, 21, 37, WD[2]); s.hline(7, 21, 34, WD[4]); box_outline(s, 7, 34, 21, 37)   # truhlík
    for i, x in enumerate(range(8, 21, 2)):
        s.px(x, 33, c(0xE8546A) if i % 2 else c(0xF4D060)); s.px(x + 1, 33, MOSS[2])
    # dveře s obloukem
    for y in range(28, 45):
        for x in range(29, 40):
            arch = y < 31 and ((x - 34) ** 2 / 30 + (31 - y) ** 2 / 9) > 1
            if arch:
                continue
            s.px(x, y, WD[2] if (x - 29) % 3 else WD[1])
            if x in (29, 39) or y == 44:
                s.px(x, y, K)
    for x in range(29, 40):
        for y in range(27, 31):
            if abs(((x - 34) ** 2 / 30 + (31 - y) ** 2 / 9) - 1) < 0.35:
                s.px(x, y, K)
    s.hline(30, 38, 37, WD[0]); s.px(37, 38, c(0xE0B040))                # kování, klika
    # lucerna
    s.rect(42, 28, 44, 32, c(0x2A2426)); s.px(43, 29, c(0xFFF2B0)); s.px(43, 30, c(0xFFD27A)); s.px(43, 31, c(0xE89A3A))
    s.px(43, 27, K)
    # podezdívka a schod
    stones(s, 2, 47, 45, 48, h=4, w=(4, 7), seed=9)
    s.rect(29, 45, 40, 47, STN[3]); s.hline(29, 40, 45, STN[4])
    box_outline(s, 2, 24, 47, 49)
    for y in range(24, 50):
        s.px(1, y, (0, 0, 0, 0)); s.px(48, y, (0, 0, 0, 0))
    return s


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
    return [fruit_tree(0), fruit_tree(3), old_apple_tree(), stone_wall(48), gate_pillar(), gate_leaf(), gate_leaf(True), well(), skep(), cottage(), basket(), ladder()]
