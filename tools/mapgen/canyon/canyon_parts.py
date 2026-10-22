"""
Díly Ozvěnového kaňonu (Akt II): dlaždice dna a plošin, skalní čela se slojemi (podle výškové mřížky),
a objekty – visutý most, hnízdo, ústí jeskyňky, obří kosti, ozvěnová stěna s vyrytými dlaněmi.
"""
import math, os, sys
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "ashplain"))
from pix import Spr, c, outline, blobs  # noqa: E402
from objects import OUT, BARK, STONE, WOOD  # noqa: E402

T = 16
FLOOR = [c(0x8A6E4E), c(0xAC9070), c(0xC8AE88), c(0xDCC6A2), c(0xEEDCBC)]
TOP = [c(0x5E3424), c(0x7E4632), c(0x9A5A3E), c(0xB4704C), c(0xCC8A62)]
FACE = [c(0x3A2218), c(0x5A3626), c(0x7A4A32), c(0x9A6040), c(0xB87A52), c(0xD49A6E)]
DRY = [c(0x6A5A2E), c(0x8A7A40), c(0xAEA060)]
FACE_H = 18            # výška skalního čela na jeden stupeň


def floor_tile(v=0):
    s = Spr(T, T); s.a[:] = FLOOR[2]
    for y in range(T):
        for x in range(T):
            if (x * 5 + y * 3 + v) % 17 == 0:
                s.px(x, y, FLOOR[1])
            elif (x * 3 + y * 7 + v) % 19 == 0:
                s.px(x, y, FLOOR[3])
    if v == 1:                                     # prasklina
        for (x, y) in ((3, 4), (4, 5), (5, 5), (6, 6), (6, 7), (7, 8)):
            s.px(x, y, FLOOR[0])
    if v == 2:                                     # oblázky
        for (x, y) in ((10, 11), (4, 12)):
            s.px(x, y, FLOOR[1]); s.px(x + 1, y, FLOOR[0]); s.px(x, y - 1, FLOOR[4])
    return s


def top_tile(v=0):
    s = Spr(T, T); s.a[:] = TOP[2]
    for y in range(T):
        for x in range(T):
            if (x * 7 + y * 5 + v) % 13 == 0:
                s.px(x, y, TOP[1])
            elif (x * 2 + y * 9 + v) % 23 == 0:
                s.px(x, y, TOP[3])
    for (x, y) in ((4, 6), (11, 12)) if v % 2 == 0 else ((9, 4),):
        s.px(x, y, DRY[0]); s.px(x - 1, y - 1, DRY[1]); s.px(x + 1, y - 1, DRY[1]); s.px(x - 1, y - 2, DRY[2]); s.px(x + 1, y - 2, DRY[2])
    return s


def draw_cliffs(img, H, block):
    """
    Výšková mřížka H (řádky × sloupce, 0 = dno). Buňka s výškou L > 0 je plošina; kde je jižní soused níž,
    spadá pod hranou skalní čelo (FACE_H px na stupeň) se slojemi, světlou hranou a stínem dole.
    Boční hrany plošin dostanou tmavší okraj. Vše na plošině a v čele je neprůchozí.
    """
    R, C = H.shape
    for r in range(R):
        for q in range(C):
            L = H[r, q]
            if L == 0:
                continue
            x0, y0 = q * T, r * T
            t = top_tile((r * 3 + q) % 4)
            for yy in range(T):
                for xx in range(T):
                    y = y0 + yy
                    if 0 <= y < img.shape[0]:
                        img[y, x0 + xx] = t.a[yy, xx]
                        block[y, x0 + xx] = True
            if r > 0 and H[r - 1, q] < L:
                for xx in range(T):
                    img[y0, x0 + xx] = TOP[4]; img[y0 + 1, x0 + xx] = TOP[3]
            # boční hrany
            for (dq, ex) in ((-1, 0), (1, T - 1)):
                if 0 <= q + dq < C and H[r, q + dq] < L:
                    for yy in range(T):
                        y = y0 + yy
                        if 0 <= y < img.shape[0]:
                            img[y, x0 + ex] = TOP[0]; img[y, x0 + ex + (1 if ex == 0 else -1)] = TOP[1]
            # čelo dolů
            below = H[r + 1, q] if r + 1 < R else L
            if below < L:
                fh = (L - below) * FACE_H
                fy0 = y0 + T
                for yy in range(fh):
                    y = fy0 + yy
                    if not 0 <= y < img.shape[0]:
                        continue
                    for xx in range(T):
                        x = x0 + xx
                        band = (yy + (x // 5) % 2) % 5
                        k = 4 if band == 0 else 3 if band < 3 else 2
                        if yy == 0:
                            k = 5
                        if yy >= fh - 2:
                            k = 1
                        if (x * 7 + yy * 3) % 23 == 0:
                            k = 1                               # pukliny
                        img[y, x] = FACE[k]
                        block[y, x] = True
                # stín na dně pod čelem
                for yy in (fh, fh + 1, fh + 2):
                    y = fy0 + yy
                    if y < img.shape[0]:
                        for xx in range(T):
                            if not block[y, x0 + xx]:
                                img[y, x0 + xx, :3] = (img[y, x0 + xx, :3] * (0.65 if yy == fh else 0.82)).astype(np.uint8)


def rope_bridge(w=40, h=40):
    """Visutý most přes průrvu: prkna, lana, sloupky na obou koncích (svisle, shora dolů)."""
    s = Spr(w, h)
    for y in range(4, h - 4):
        sag = int(2 * math.sin(math.pi * (y - 4) / (h - 8)))
        for x in range(10 + sag, w - 10 + sag):
            s.px(x, y, WOOD[3] if (y % 4) else WOOD[1])
            if (y % 4) == 1:
                s.px(x, y, WOOD[4])
        s.px(8 + sag, y, c(0xC8B08A)); s.px(w - 9 + sag, y, c(0xC8B08A))       # lana
    for (px0, py0) in ((7, 0), (w - 10, 0), (7, h - 5), (w - 10, h - 5)):
        s.rect(px0, py0, px0 + 2, py0 + 4, WOOD[1]); s.px(px0, py0, WOOD[3])
    return outline(s, OUT)


def nest():
    s = Spr(20, 10)
    s.ellipse(10, 6, 9, 3.6, c(0x6A5034))
    for i in range(14):
        a = i * 0.45
        s.line(10 + 8 * math.cos(a), 6 + 3 * math.sin(a), 10 + 5 * math.cos(a + 1.3), 5 + 2 * math.sin(a + 1.3), c(0x8E6E46))
    s.ellipse(10, 4, 5, 2, c(0x3A2A1A))
    s.ellipse(8, 4, 1.6, 1.3, c(0xE8E0CC)); s.ellipse(12, 4, 1.6, 1.3, c(0xD8CCAE)); s.px(8, 3, c(0xFFFFFF))
    return outline(s, OUT)


def cave_mouth():
    s = Spr(22, 16)
    for y in range(16):
        for x in range(22):
            d = ((x - 11) / 10.5) ** 2 + ((y - 15) / 14) ** 2
            if d < 1:
                s.px(x, y, c(0x140C0A) if d < 0.75 else FACE[1])
    return s


def bones():
    """Obří kosti napůl v písku: páteř a žebra."""
    s = Spr(44, 26)
    for i in range(7):
        x = 4 + i * 6
        s.line(x, 18, x - 3, 4 + (i % 2) * 2, c(0xE6DCC6), w=2)
        s.px(x - 3, 4 + (i % 2) * 2, c(0xFFFFFF))
    for x in range(2, 44):
        s.px(x, 18 + (x % 6 == 0), c(0xD2C6AC)); s.px(x, 19, c(0xBEB094))
    s.ellipse(40, 16, 4, 3, c(0xE6DCC6)); s.px(41, 15, OUT); s.px(39, 15, OUT)            # lebka
    outline(s, OUT)
    for x in range(0, 44):
        for y in range(21 + (x % 5 == 0), 26):
            s.px(x, y, FLOOR[3] if y == 21 + (x % 5 == 0) else FLOOR[2])                 # písek přes spodek
    return s


def echo_marks(img, x0, y0, w, rows=2):
    """Vyryté dlaně a čárky (jména) do skalního čela ozvěnové stěny."""
    rnd = np.random.default_rng(12)
    for i in range(10):
        x = x0 + 4 + int(rnd.integers(0, w - 10)); y = y0 + 3 + int(rnd.integers(0, rows * 8))
        palm = [".o.o.", "oo.oo", "ooooo", ".ooo.", ".ooo."]
        for yy, row in enumerate(palm):
            for xx, ch in enumerate(row):
                if ch == "o":
                    img[y + yy, x + xx] = FACE[1]
    for i in range(16):
        x = x0 + 2 + int(rnd.integers(0, w - 8)); y = y0 + 2 + int(rnd.integers(0, rows * 10))
        for k in range(int(rnd.integers(3, 7))):
            img[y, x + k] = FACE[0] if k % 3 else FACE[1]
