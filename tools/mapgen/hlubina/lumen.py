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


