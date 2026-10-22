"""
Pixel-art pomůcky pro budovy a kamenné prvky: jednotlivě stínované kameny (světlo shora zleva,
stín dole vpravo, spára), šindelová střecha s posunutými řadami, obrys po prvcích, sprite z ASCII.
"""
import os, sys
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ashplain"))
from pix import Spr, c  # noqa: E402

K = c(0x24181A)                     # obrys
STN = [c(0x3C3840), c(0x5E5A64), c(0x827C86), c(0xA8A2AA), c(0xCCC6C8)]
WD = [c(0x3E2614), c(0x5E3C22), c(0x82583A), c(0xA67852), c(0xC89A6E)]
ROOF = [c(0x4A1C18), c(0x76302A), c(0xA24636), c(0xC8644A), c(0xE68A66)]
PL = [c(0x9C8C74), c(0xC4B496), c(0xE2D4B6), c(0xF4EAD2)]
GLASS = [c(0x1E2A40), c(0x34507A), c(0x6E98C0), c(0xB8D8EC)]
MOSS = [c(0x2E4A22), c(0x4A7032), c(0x6E9A48)]


def stones(s, x0, x1, y0, y1, h=5, w=(5, 9), seed=1, tone=0, mortar=True):
    """Řady kamenů různé šířky; každý kámen má světlou horní a levou hranu a stín dole/vpravo."""
    r = np.random.default_rng(seed)
    y = y0
    row = 0
    while y <= y1:
        x = x0 - int(r.integers(0, w[0]))
        hh = min(h, y1 - y + 1)
        while x <= x1:
            bw = int(r.integers(w[0], w[1] + 1))
            t = tone + int(r.integers(-1, 2))
            for yy in range(y, y + hh):
                for xx in range(max(x, x0), min(x + bw, x1 + 1)):
                    e_top, e_bot = yy == y, yy == y + hh - 1
                    e_l, e_r = xx == x, xx == x + bw - 1
                    if mortar and (e_bot or e_r):
                        col = STN[0]
                    elif e_top or e_l:
                        col = STN[min(4, 3 + t)]
                    elif yy == y + hh - 2 or xx == x + bw - 2:
                        col = STN[max(1, 1 + t)]
                    else:
                        col = STN[max(1, min(4, 2 + t))]
                    s.px(xx, yy, col)
            x += bw
        y += hh
        row += 1


def shingles(s, rows_spec, seed=2):
    """
    Šindelová střecha: rows_spec = [(y, x0, x1)] pro každou řadu (výška řady 3 px, shora dolů).
    Šindele 4 px, posun v každé druhé řadě, světlá horní hrana, tmavá spodní a mezery.
    """
    for i, (y, x0, x1) in enumerate(rows_spec):
        off = (i % 2) * 2
        for x in range(x0, x1 + 1):
            gap = (x + off) % 4 == 0
            s.px(x, y, ROOF[1] if gap else ROOF[4] if (x + off) % 4 == 1 else ROOF[3])
            s.px(x, y + 1, ROOF[1] if gap else ROOF[2])
            s.px(x, y + 2, ROOF[0] if gap else ROOF[1])


def box_outline(s, x0, y0, x1, y1, col=K):
    s.hline(x0, x1, y0, col); s.hline(x0, x1, y1, col); s.vline(x0, y0, y1, col); s.vline(x1, y0, y1, col)


def from_ascii(rows, pal):
    w = max(len(r) for r in rows)
    s = Spr(w, len(rows))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                s.px(x, y, pal[ch])
    return s
