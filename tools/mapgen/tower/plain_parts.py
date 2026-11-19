"""
Kalibrační louka – 3. patro Věže Řádu Váhy, patro normálních Makromonů. Umělá louka uvnitř věže: zadní stěna
je namalované nebe (s prasklinou a odlupující se omítkou, pod kterou jsou kachle), tráva rozdělená
provázky a očíslovanými kolíky na měřicí čtverce, vysoké trávy (setkání), strašák v úřednickém plášti
s cedulkou 213 místo tváře, prázdná pozorovatelna s deskami, postřikovače, balíky sena, lavička.
Modely ve 3/4 (světlo shora zleva, „slunce“ je velká lampa nad loukou), obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import (sphere, selout, _pick, _grain, BRASS, WALNUT, PAPER, INK, STEEL, RED, NAVY, IRON_,  # noqa: E402
                     shade_px)

GRASS = [c(0x1E3A1C), c(0x2E5426), c(0x3E6E30), c(0x58883C), c(0x78A44A), c(0xA0C462)]
SKY = [c(0x5E86B8), c(0x7AA0CC), c(0x9CBCDC), c(0xBED6EA), c(0xDCEAF4)]
CLOUD = [c(0xB8C8DA), c(0xD6E2EE), c(0xF2F6FA)]
HILL = [c(0x4E7A5A), c(0x6A9670), c(0x88B08A)]
TILE = [c(0x5C6A66), c(0x8A9A94), c(0xA8B8B0), c(0xC4D2CA)]
OAK = [c(0x3A2414), c(0x5C3A20), c(0x825630), c(0xA87444), c(0xCC9A60)]
HAY = [c(0x6A5226), c(0x9A7A38), c(0xC4A04C), c(0xE2C46E), c(0xF2DC96)]
COAT = [c(0x1A1E2A), c(0x262C3E), c(0x343C54), c(0x4A546E)]
DIRT = [c(0x4A3826), c(0x6A5036), c(0x8A6C4A)]
STONE = [c(0x5E5A56), c(0x807A74), c(0xA49C94), c(0xC4BCB2)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def meadow(img, x0, y0, x1, y1):
    """Travnatá zem: tři tóny trávy v drobných chomáčcích, tu a tam květ (bílý, žlutý, fialový) a holá hlína."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            h = _h(x, y)
            k = (h % 7)
            col = GRASS[3] if k < 3 else GRASS[2] if k < 6 else GRASS[4]
            if (x + (y // 3) * 2) % 5 == 0 and h % 3 == 0:
                col = GRASS[4] if y % 2 else GRASS[2]                              # stébla
            img[y, x] = col
    for y in range(y0 + 2, y1 - 2, 3):
        for x in range(x0 + 2, x1 - 2, 3):
            h = _h(x, y, 5)
            if h % 97 == 0:
                img[y, x] = (c(0xF4F4EC), c(0xF2D850), c(0xB48AD8))[h % 3]
                img[y + 1, x] = GRASS[1]
            elif h % 211 == 0:
                for dx in range(-2, 3):
                    for dy in range(-1, 2):
                        if abs(dx) + abs(dy) < 3:
                            img[y + dy, x + dx] = DIRT[1] if (dx + dy) % 2 else DIRT[2]


def sky_mural(W, H):
    """Namalované nebe na zadní stěně: přechod modré, malované mraky, kopce na obzoru, namalované slunce.
    Svislá spára mezi panely a v pravém rohu odlupující se malba, pod kterou jsou kachle. Nahoře římsa."""
    s = Spr(W, H)
    rng = np.random.default_rng(31)
    for y in range(H):
        t = y / H
        f = (0.95 - t * 0.85) * (len(SKY) - 1)
        i = int(f)
        for x in range(W):
            edge = (f - i) > 0.9 and (x + y) % 2 == 0                              # jen řádek přechodu
            s.px(x, y, SKY[min(len(SKY) - 1, i + (1 if edge else 0))])
    for (cx, cy, rx, ry) in ((30, 22, 16, 5), (128, 30, 20, 6), (70, 44, 12, 4), (160, 52, 10, 3)):   # mraky
        for y in range(int(cy - ry * 2), int(cy + ry + 1)):
            for x in range(int(cx - rx), int(cx + rx + 1)):
                d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
                bump = 0.35 * math.sin(x * 0.7) + 0.25 * math.sin(x * 1.9 + cy)
                if d < 1 + bump and y > cy - ry * (1.6 + bump):
                    s.px(x, y, CLOUD[2] if y < cy - ry * 0.3 else CLOUD[1] if y < cy + ry * 0.5 else CLOUD[0])
    sx, sy = 140, 14                                                                # namalované slunce
    for y in range(sy - 6, sy + 7):
        for x in range(sx - 6, sx + 7):
            d = math.hypot(x - sx, y - sy)
            if d < 5:
                s.px(x, y, c(0xFFF4C8) if d < 3.5 else c(0xFFE08A))
            elif d < 6.5 and (x + y) % 2 == 0:
                s.px(x, y, c(0xEADCA8))
    for x in range(W):                                                              # kopce na obzoru
        h1 = H - 18 + int(4 * math.sin(x * 0.05) + 3 * math.sin(x * 0.13 + 1))
        h2 = H - 10 + int(3 * math.sin(x * 0.08 + 2))
        for y in range(h1, H):
            s.px(x, y, HILL[2] if y < h2 else HILL[1] if y < H - 4 else HILL[0])
    for y in range(4, H):                                                           # spára mezi panely
        s.px(56, y, SKY[0] if y < H - 18 else HILL[0])
        s.px(120, y, SKY[0] if y < H - 18 else HILL[0])
    for y in range(6, 34):                                                          # odlupující se malba (pravý roh)
        for x in range(W - 22 + (34 - y) // 3, W):
            if (x + y) % 9 == 0 and y > 28:
                continue
            ly, lx = (y - 6) % 6, (x - (W - 22)) % 8
            s.px(x, y, TILE[0] if (lx == 7 or ly == 5) else TILE[2])
    for y in range(6, 34):                                                          # okraj odloupnutí (zvlněný šupinek)
        x = W - 23 + (34 - y) // 3
        s.px(x, y, CLOUD[2]); s.px(x - 1, y, SKY[1])
    for y in range(0, 6):                                                           # římsa
        for x in range(W):
            s.px(x, y, c(0xE6E9EF) if y == 0 else c(0xCCD1DB) if y < 3 else c(0x7A8292) if y == 5 else c(0xA6ADBB))
    return s


def tall_grass(w=34, h=22, seed=0):
    """Vysoká tráva (místo setkání): tmavá báze, stébla ve třech vrstvách, světlé konce, pár klasů."""
    s = Spr(w, h)
    rng = np.random.default_rng(seed)
    cx = w / 2
    for layer, (tone, n) in enumerate(((1, 26), (2, 30), (3, 26))):
        for _ in range(n):
            bx = rng.uniform(1, w - 2)
            if abs(bx - cx) / cx > 0.95:
                continue
            top = int(h * (0.15 + 0.35 * abs(bx - cx) / cx) + rng.uniform(-2, 3)) + layer * 2
            lean = rng.uniform(-0.25, 0.25)
            for y in range(max(0, top), h - layer):
                t = (y - top) / max(1, h - top)
                x = int(bx + lean * (h - y))
                col = GRASS[min(5, tone + (2 if t < 0.2 else 1 if t < 0.5 else 0))]
                if y > h - 4:
                    col = GRASS[0]
                s.px(x, y, col)
            if rng.random() < 0.12:                                                 # klas
                s.px(int(bx + lean * (h - top)), top - 1, HAY[3]); s.px(int(bx + lean * (h - top)), top - 2, HAY[4])
    return selout(s, 0.55, 0.85)


def stake(n=0):
    """Kolík s papírovou cedulkou čtverce (A1, B2…): dřevo ve 3/4, cedulka na hřebíku, provázek."""
    s = Spr(9, 16)
    for y in range(2, 16):
        s.px(3, y, OAK[3]); s.px(4, y, OAK[2]); s.px(5, y, OAK[1])
    s.px(3, 1, OAK[4]); s.px(4, 1, OAK[3]); s.px(4, 0, OAK[3])
    for y in range(4, 8):
        for x in range(1, 8):
            s.px(x, y, PAPER[3] if y < 7 else PAPER[1])
    s.px(2, 5, INK); s.px(3, 5, INK); s.px(5, 5, INK if n % 2 else PAPER[3]); s.px(6, 5, INK)
    s.px(4, 4, STEEL[3])
    return selout(s, 0.5, 0.82)


def scarecrow():
    """Strašák v úřednickém plášti: dřevěný kříž, tmavý kabát se zapnutými knoflíky, buřinka, místo tváře
    pytel s natištěným číslem 213, z rukávů trčí sláma, v ruce desky s klipem."""
    W, H = 32, 52
    s = Spr(W, H)
    cx = W // 2
    for y in range(10, H):                                                          # kůl
        s.px(cx, y, OAK[3]); s.px(cx + 1, y, OAK[1])
    for x in range(2, W - 2):                                                       # rameno kříže
        s.px(x, 20, OAK[3]); s.px(x, 21, OAK[1])
    for y in range(18, 40):                                                         # kabát
        w = 7 if y < 24 else 8 if y < 32 else 9
        for x in range(cx - w, cx + w + 2):
            nx = (x - cx) / (w + 1)
            s.px(x, y, COAT[3] if nx < -0.55 else COAT[2] if nx < 0.2 else COAT[1] if nx < 0.75 else COAT[0])
        s.px(cx, y, COAT[0])
    for y in (24, 28, 32, 36):
        s.px(cx - 1, y, BRASS[3])
    for x in range(3, cx - 6):                                                      # rukávy
        for y in range(19, 24):
            s.px(x, y, COAT[2] if y < 21 else COAT[1])
    for x in range(cx + 8, W - 3):
        for y in range(19, 24):
            s.px(x, y, COAT[1] if y < 21 else COAT[0])
    for (x, y) in ((2, 21), (1, 22), (2, 23), (1, 20), (W - 3, 21), (W - 2, 22), (W - 2, 20)):   # sláma
        s.px(x, y, HAY[3] if (x + y) % 2 else HAY[2])
    for y in range(7, 18):                                                          # hlava – pytel
        for x in range(cx - 5, cx + 7):
            d = ((x - cx - .5) / 6) ** 2 + ((y - 12.5) / 5.6) ** 2
            if d < 1:
                s.px(x, y, c(0xD8C49A) if x < cx + 2 else c(0xB8A47A))
    for x in (cx - 2, cx, cx + 2):                                                  # „213“
        s.px(x, 12, INK); s.px(x, 13, INK if x != cx else c(0xD8C49A))
    s.px(cx - 3, 14, RED[2]); s.px(cx + 4, 14, RED[2])
    for y in range(3, 8):                                                           # buřinka
        for x in range(cx - 5 + (y < 6) * 2, cx + 7 - (y < 6) * 2):
            s.px(x, y, COAT[3] if x < cx else COAT[1])
    s.hline(cx - 6, cx + 7, 7, COAT[0])
    for y in range(26, 34):                                                         # desky s klipem
        for x in range(W - 10, W - 3):
            s.px(x, y, OAK[3] if y > 27 else PAPER[3])
    s.px(W - 7, 26, STEEL[4])
    return selout(s, 0.5, 0.82)


def observer_chair():
    """Vysoká pozorovatelna (jako u plavčíka): dvě nohy s příčlemi, sedátko s opěradlem, na sedátku desky
    s tužkou. Prázdná."""
    W, H = 30, 58
    s = Spr(W + 2, H)
    for y in range(20, H):                                                          # nohy (rozšiřují se dolů)
        off = (y - 20) // 7
        for x in (6 - off, W - 8 + off):
            s.px(x, y, OAK[3]); s.px(x + 1, y, OAK[1])
    for y in range(28, H - 2, 8):                                                   # příčle
        off = (y - 20) // 7
        for x in range(7 - off, W - 8 + off):
            s.px(x, y, OAK[4]); s.px(x, y + 1, OAK[1])
    for y in range(14, 21):                                                         # sedátko
        for x in range(4, W - 4):
            s.px(x, y, OAK[4] if y < 17 else OAK[3])
    s.hline(4, W - 5, 21, OAK[1])
    for y in range(0, 14):                                                          # opěradlo
        for x in range(5, W - 5):
            e = min(x - 5, W - 6 - x, y)
            s.px(x, y, OAK[3] if e > 1 else OAK[4] if x < W // 2 else OAK[1])
    for x in range(7, W - 7):
        s.px(x, 6, OAK[1])
    for y in range(15, 19):                                                         # desky na sedátku
        for x in range(10, 19):
            s.px(x, y, PAPER[3] if y > 15 else OAK[2])
    s.px(17, 16, NAVY[2]); s.px(18, 15, NAVY[2])
    return selout(s, 0.5, 0.82)


def sprinkler():
    """Mosazný postřikovač na trubce (kropí InteriorFxView)."""
    s = Spr(9, 12)
    for y in range(4, 12):
        s.px(3, y, BRASS[3]); s.px(4, y, BRASS[2]); s.px(5, y, BRASS[0])
    sphere(s, 4.5, 3, 3, 2.2, BRASS, amb=0.35)
    s.px(4, 0, BRASS[4])
    for x in range(1, 8):
        s.px(x, 11, STONE[1])
    return selout(s, 0.5, 0.82)


def hay_bale():
    """Balík sena ve 3/4: válec ležící na boku – čelo se spirálou, horní plocha se stébly, provázky."""
    W, H = 28, 20
    s = Spr(W, H)
    for y in range(2, H):
        for x in range(8, W):
            col = HAY[4] if y < 5 else HAY[3] if y < 10 else HAY[2] if y < 16 else HAY[1]
            if (x * 3 + y) % 7 == 0:
                col = HAY[1]
            s.px(x, y, col)
    for x in (14, 22):
        for y in range(2, H):
            s.px(x, y, OAK[1])
    for y in range(0, H):                                                           # čelo (kruh se spirálou)
        for x in range(0, 16):
            d = math.hypot((x - 8) / 8, (y - 10) / 10)
            if d < 1:
                a = math.atan2(y - 10, x - 8)
                spiral = (d * 6 + a / math.pi) % 1
                s.px(x, y, HAY[3] if spiral < 0.5 else HAY[2] if d < 0.9 else HAY[1])
    return selout(s, 0.5, 0.82)


def stepping_stone(seed=0):
    s = Spr(10, 6)
    for y in range(6):
        for x in range(10):
            d = ((x - 4.5) / 5) ** 2 + ((y - 2.5) / 3) ** 2
            if d < 1:
                s.px(x, y, STONE[3] if y < 2 else STONE[2] if y < 4 else STONE[1])
    return selout(s, 0.55, 0.85)


def picket(w):
    """Bílý laťkový plůtek ve 3/4: laťky se špičkou, dvě vodorovné příčky, stín pod laťkami."""
    s = Spr(w, 14)
    for x in range(w):
        s.px(x, 6, c(0xC8C4BA)); s.px(x, 10, c(0xC8C4BA)); s.px(x, 11, c(0x8A867E))
    for x0 in range(1, w - 2, 4):
        for y in range(1, 14):
            s.px(x0, y, c(0xF4F2EA)); s.px(x0 + 1, y, c(0xC8C4BA))
        s.px(x0, 0, c(0xF4F2EA))
    return selout(s, 0.55, 0.85)


def potted_tree():
    """Umělý stromek v květináči: koruna ze stínovaných chomáčů, kmínek, hranatý květináč; na větvi visí
    papírová cedulka s inventárním číslem (je z plastu)."""
    from figures import blobs
    W, H = 30, 42
    s = Spr(W, H)
    for y in range(30, H):                                                          # květináč
        for x in range(8, 22):
            s.px(x, y, STONE[3] if y == 30 else STONE[2] if x < 18 else STONE[1])
    s.hline(8, 21, 31, STONE[0])
    for y in range(18, 31):
        s.px(14, y, OAK[3]); s.px(15, y, OAK[1])
    blobs(s, [(9, 13, 6), (20, 12, 6.5), (14, 7, 7), (15, 16, 6), (8, 18, 4.5), (22, 18, 4.5)], GRASS[1:], amb=0.25)
    for y in range(17, 22):                                                         # cedulka
        s.px(24, y, PAPER[3] if y > 18 else STEEL[3])
    s.px(25, 19, PAPER[3]); s.px(25, 20, INK)
    return selout(s, 0.5, 0.82)
