"""Zóna 3 – ruiny Lumenců (starší než Řád), které kořeny stahují do skály."""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain")); sys.path.insert(0, os.path.join(HERE, "..", "tower")); sys.path.insert(0, HERE)
from common import *  # noqa: E402,F401,F403
from common import _shade, _pick, _h, _vn  # noqa: E402,F401


def ruin_facade(w=54, h=70):
    """Fasáda Lumenců vytesaná do skály: lomený oblouk vchodu (zasypaný), dva štíhlé sloupy, římsa s řadou
    drobných svítících znaků (hvězdy a vlny), nad obloukem vytesaná obrácená koruna. Starší než Řád."""
    s = Spr(w, h)
    STONE = [c(0x1A1C24), c(0x2E3240), c(0x444A5A), c(0x5E6676), c(0x7E8696), c(0xA4ACBA)]
    cx = w // 2
    for y in range(h):
        for x in range(w):
            v = 0.5 + (_vn(x, y, 31, 4) - 0.5) * 0.25
            if y < 8:
                v = 0.62 if y % 4 else 0.3                               # římsa
            if y > 18 and abs(x - cx) < 14:                               # oblouk (lomený)
                top = 18 + (abs(x - cx) / 14) ** 2 * 10
                if y > top:
                    v = 0.0                                               # tma za vchodem
                elif y > top - 2:
                    v = 0.75
            for px_ in (5, w - 9):                                        # sloupy
                if px_ <= x < px_ + 4 and y >= 8:
                    v = 0.4 + (x - px_) * 0.12
            _shade(s, x, y, v, STONE)
    for i, x in enumerate(range(4, w - 4, 4)):                            # znaky na římse
        if i % 2:
            s.px(x, 3, GLYPH); s.px(x + 1, 4, GLYPH); s.px(x - 1, 4, GLYPH); s.px(x, 5, GLYPH)
        else:
            s.px(x, 4, GLYPH); s.px(x + 1, 3, GLYPH); s.px(x + 2, 4, GLYPH)
    for k in range(9):                                                    # obrácená koruna nad obloukem
        s.px(cx - 4 + k, 11, GLYPH)
        if k % 2 == 0:
            s.px(cx - 4 + k, 12, GLYPH); s.px(cx - 4 + k, 13, c(0x4A80C0))
    s.px(cx, 10, GLYPH); s.px(cx, 9, GLYPH); s.px(cx, 8, c(0x4A80C0))
    for (x, y) in ((cx - 6, h - 6), (cx + 3, h - 9), (cx - 1, h - 4)):    # sutina ve vchodu
        sphere(s, x, y, 3, 2, STONE, amb=0.25)
    return selout(s, 0.5, 0.8)




def carved_stairs(img, x0, y0, x1, y1, steps=12, width=14):
    """Schody Lumenců vytesané diagonálně do skalní stěny (světlá stupnice, tmavá podstupnice, ohlazené hrany)."""
    STONE = [c(0x1A1C24), c(0x2E3240), c(0x444A5A), c(0x5E6676), c(0x7E8696)]
    for i in range(steps):
        t0, t1 = i / steps, (i + 1) / steps
        sx, sy = x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0
        ex, ey = x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1
        for y in range(int(sy), int(ey) + 1):
            for x in range(int(sx), int(sx) + width):
                if 0 <= y < img.shape[0] and 0 <= x < img.shape[1]:
                    q = (y - sy) / max(1, ey - sy)
                    v = 0.75 if q < 0.45 else 0.3
                    if x in (int(sx), int(sx) + width - 1):
                        v = 0.15
                    img[y, x] = _pick(STONE, v + (_vn(x, y, 51, 3) - 0.5) * 0.15, x, y)


def longhouse_ribs(w=60, h=34, sink=0.4, seed=0):
    """Pozůstatky dlouhého domu Lumenců: řada kamenných žeber (oblouků) zapadajících do země; sink = jak hluboko
    je kořeny stáhly (zadní žebra víc)."""
    STONE = [c(0x1A1C24), c(0x2E3240), c(0x444A5A), c(0x5E6676), c(0x7E8696), c(0xA4ACBA)]
    s = Spr(w, h)
    n = 5
    for i in range(n):
        cx = 6 + i * (w - 12) / (n - 1)
        r = 10 - i * 0.6
        base = h - 2 - i * 2
        depth_in = (sink * (i + 1) / n) * r
        for a in range(0, 181, 3):
            x = cx + math.cos(math.radians(a)) * r * 0.55
            y = base - math.sin(math.radians(a)) * r + depth_in
            if y > base:
                continue
            for k in range(3):
                v = 0.7 - k * 0.2 - i * 0.06 + (_vn(x, y, seed + i, 2) - 0.5) * 0.2
                s.px(x + k * 0.6, y, _pick(STONE, max(0.0, min(1.0, v)), int(x), int(y)))
        if i % 2 == 0:
            s.px(cx, base - r + depth_in - 1, GLYPH)                       # znak na vrcholu oblouku
    return selout(s, 0.5, 0.8)


# ── Chrám Lumenců (pohlcení 6/8) ────────────────────────────────────────────
TSTONE = [c(0x15171E), c(0x262A36), c(0x3A4050), c(0x525A6C), c(0x6E778A), c(0x8E98AA), c(0xB2BACA)]


def column(h=58, broken=0, seed=0):
    """Sloup Lumenců (pohled 3/4): patka, dřík s kanelurami a pásem svítících znaků, hlavice s obrácenou korunou.
    broken > 0 = ulomený (zůstane jen spodek o výšce broken, nahoře zubatý lom)."""
    rng = np.random.default_rng(seed)
    w = 14
    s = Spr(w + 4, h + 6)
    top = 0 if not broken else h - broken
    cx = (w + 4) / 2
    for y in range(top, h):
        for x in range(2, w + 2):
            u = (x + 0.5 - cx) / (w / 2)
            v = 0.62 - u * 0.32 + 0.08 * math.cos(u * math.pi)
            if (x - 2) % 3 == 0:
                v -= 0.14                                                  # kanelury
            if y < top + 6 and not broken:
                v += 0.1
            s.px(x, y, _pick(TSTONE, max(0.0, min(1.0, v + (_vn(x, y, seed + 3, 3) - 0.5) * 0.12)), x, y))
        if not broken and y in (h // 2, h // 2 + 1):
            for x in range(4, w, 3):
                s.px(x, y, GLYPH)                                          # pás znaků
    if broken:
        for x in range(2, w + 2):                                          # zubatý lom
            d = int(abs(math.sin(x * 1.7 + seed)) * 4)
            for y in range(top, top + d):
                s.a[y, x] = 0
            s.px(x, top + d, TSTONE[5])
    else:
        for y in range(0, 6):                                              # hlavice
            for x in range(0, w + 4):
                s.px(x, y, _pick(TSTONE, 0.7 - y * 0.08 - (x - cx) / (w + 4) * 0.3, x, y))
        for k in range(5):
            s.px(int(cx) - 2 + k, 2, GLYPH if k % 2 == 0 else c(0x4A80C0))  # obrácená koruna
    for y in range(h, h + 6):                                              # patka
        for x in range(0, w + 4):
            s.px(x, y, _pick(TSTONE, 0.55 - (y - h) * 0.07 - (x - cx) / (w + 4) * 0.3, x, y))
    return selout(s, 0.5, 0.8)


def column_drum(seed=0):
    """Spadlý kus dříku ležící na podlaze (válec na boku)."""
    s = Spr(22, 12)
    for y in range(1, 11):
        for x in range(2, 20):
            u = (y - 6) / 5
            s.px(x, y, _pick(TSTONE, 0.6 - u * 0.3 - (0.1 if x % 3 == 0 else 0), x, y))
    for y in range(1, 11):                                                 # čelo válce
        for x in range(0, 5):
            if ((x - 3) / 3) ** 2 + ((y - 6) / 5) ** 2 < 1:
                s.px(x, y, _pick(TSTONE, 0.75 - (_vn(x, y, seed, 2)) * 0.2, x, y))
    return selout(s, 0.5, 0.8)


MARBLE = [c(0x2A2C34), c(0x4A4E5A), c(0x707684), c(0x9AA0AC), c(0xC4C8D0), c(0xE2E4EA), c(0xF4F4F8)]


def deer_statue():
    """Bezhlavá socha jelena na podstavci: štíhlé nohy, hruď, krk useknutý hladce; na podstavci znaky."""
    s = Spr(34, 46)
    for y in range(30, 46):                                                # podstavec
        for x in range(2, 32):
            v = 0.55 - (y - 30) * 0.02 - (x - 17) / 30 * 0.3 + (0.15 if y == 30 else 0)
            s.px(x, y, _pick(TSTONE, v, x, y))
    for x in range(6, 30, 4):
        s.px(x, 37, GLYPH); s.px(x + 1, 38, c(0x4A80C0))
    for (lx, ln) in ((9, 0), (13, 1), (21, 0), (25, 1)):                    # nohy
        for y in range(20, 30):
            s.px(lx + (1 if y > 26 and ln else 0), y, MARBLE[5] if lx < 17 else MARBLE[3])
            s.px(lx + 1 + (1 if y > 26 and ln else 0), y, MARBLE[4] if lx < 17 else MARBLE[2])
            s.px(lx + 2 + (1 if y > 26 and ln else 0), y, MARBLE[2])
    for y in range(10, 21):                                                # tělo
        for x in range(7, 29):
            if ((x - 17.5) / 11) ** 2 + ((y - 15.5) / 5.5) ** 2 < 1:
                s.px(x, y, _pick(MARBLE, 0.8 - (x - 17) / 22 * 0.4 - (y - 10) * 0.025, x, y))
    for y in range(3, 12):                                                 # krk šikmo vzhůru
        for x in range(23 + (11 - y) // 3, 29 + (11 - y) // 3):
            s.px(x, y, _pick(MARBLE, 0.78 - (x - 24) * 0.07, x, y))
    for x in range(26, 31):                                                # hladký řez krku
        s.px(x, 3, MARBLE[6]); s.px(x, 4, MARBLE[5])
    s.px(8, 11, TSTONE[4]); s.px(7, 10, TSTONE[4])                          # ocásek
    return selout(s, 0.5, 0.8)


def wish_altar():
    """Oltář přání: kamenná mísa na nízkém podstavci, plná drobností (knoflíky, hřebínky, květy, píšťalka),
    nad ní slabě svítí modré jiskry. Okraj mísy pokrytý znaky."""
    s = Spr(44, 30)
    for y in range(16, 30):                                                # podstavec
        for x in range(12, 32):
            s.px(x, y, _pick(TSTONE, 0.5 - (x - 22) / 20 * 0.3 - (y - 16) * 0.015, x, y))
    for y in range(4, 18):                                                 # mísa (elipsa)
        for x in range(2, 42):
            d = ((x - 22) / 20) ** 2 + ((y - 10) / 7) ** 2
            if d < 1:
                inner = ((x - 22) / 17) ** 2 + ((y - 9) / 5) ** 2 < 1
                v = (0.3 + (y - 4) * 0.02) if inner else 0.8 - (x - 22) / 40 * 0.35
                s.px(x, y, _pick(MARBLE if not inner else TSTONE, v, x, y))
    rng = np.random.default_rng(6)
    OFF = [c(0xC8A050), c(0xB8B0A0), c(0x8E4A3A), c(0xD8D0B8), c(0x6A8A50), c(0xE0B060), c(0x9AB0D0)]
    for _ in range(46):                                                    # drobnosti v míse
        x, y = int(rng.integers(8, 37)), int(rng.integers(6, 14))
        if ((x - 22) / 16) ** 2 + ((y - 9) / 4.6) ** 2 < 1:
            s.px(x, y, OFF[int(rng.integers(0, len(OFF)))])
    for a in range(0, 360, 30):                                            # znaky na okraji
        x = 22 + math.cos(math.radians(a)) * 19; y = 10 + math.sin(math.radians(a)) * 6.4
        s.px(x, y, GLYPH)
    for (x, y) in ((16, 2), (25, 0), (30, 3), (20, 1)):                     # jiskry
        s.px(x, y, c(0xBFE6FF))
    return selout(s, 0.5, 0.8)
