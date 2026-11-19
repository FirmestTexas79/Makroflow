"""
Kancelář Řádu – 11. patro: palety a mapa Makrosvěta se stanicemi 0–7. Ostatní díly jsou v office2.py.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, cylinder, selout, _pick, BRASS, PAPER, INK, STEEL, RED, IRON_, shade_px  # noqa: E402

NAVY = [c(0x070A16), c(0x0E1428), c(0x18203E), c(0x243058), c(0x364678), c(0x5468A0)]
GOLDL = [c(0x6A4A18), c(0x9C7428), c(0xCCA040), c(0xF0CC6C)]
EBONY = [c(0x100A0A), c(0x1C1212), c(0x2A1C1A), c(0x3C2A24), c(0x56403A)]
MARBLE = [c(0x101218), c(0x181C26), c(0x222836), c(0x2E3646), c(0x404A5E), c(0x5E6A82)]
CARPET = [c(0x2A0A10), c(0x44121A), c(0x5E1C24), c(0x7A2A30), c(0x9A3E40)]
LEATHERG = [c(0x0E2418), c(0x163626), c(0x204A34), c(0x2E6046)]
SKY = [c(0x020308), c(0x060A18), c(0x0C1430), c(0x162248)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def station_map(w=44, h=40):
    """Mapa Makrosvěta na stěně: pergamen v rámu, obrysy krajů, osm připínáčků (stanice 0–7), od každého
    vede rudá nit dolů přes okraj rámu."""
    s = Spr(w, h + 18)
    rng = np.random.default_rng(5)
    for y in range(h):
        for x in range(w):
            e = min(x, y, w - 1 - x, h - 1 - y)
            col = EBONY[3] if e == 0 else EBONY[2] if e < 2 else PAPER[2] if (x + y * 3) % 11 else PAPER[1]
            s.px(x, y, col)
    for k in range(5):                                                               # obrysy krajů
        cx_, cy_ = rng.uniform(8, w - 8), rng.uniform(8, h - 8)
        for a10 in range(0, 63):
            a = a10 / 10
            r = 5 + math.sin(a * 3 + k) * 2
            s.px(cx_ + math.cos(a) * r, cy_ + math.sin(a) * r * 0.8, PAPER[0])
    pins = [(8, 8), (20, 6), (34, 9), (12, 20), (28, 18), (38, 26), (16, 31), (26, 33)]
    for i, (px_, py_) in enumerate(pins):
        s.line(px_, py_ + 1, px_ + (i % 3 - 1), h + 6 + (i * 5) % 12, RED[1])         # rudá nit dolů přes okraj
        s.px(px_, py_, RED[4] if i else c(0xF0F0F0)); s.px(px_ + 1, py_, RED[2])
    return selout(s, 0.5, 0.82)
