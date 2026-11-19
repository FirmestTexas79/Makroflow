"""
Dormitoř – 8. patro Věže Řádu Váhy, patro duchů. Věčná noc. Dlouhá ložnice „kalibrovaných“: řady železných
postelí, pod některými přikrývkami se rýsuje tvar spáče, který se nikdy neprobudí. Svíčky na nočních stolcích,
vysoká okna s průsvitnými záclonami a měsícem, cedule TICHO – PROBÍHÁ SPÁNEK, stolek noční sestry s knihou
spánku, plátěná zástěna. Druhý vrchol tématu spánku: spánek tu není odpočinek, ale měření, které nekončí.
Ve 3/4, objemově, obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import sphere, selout, blobs, _pick, BRASS, PAPER, INK, STEEL, RED, IRON_, shade_px  # noqa: E402

PAPERW = [c(0x14142A), c(0x1E1E3C), c(0x2A2A50), c(0x3A3A66)]                 # tapeta
WAINS = [c(0x1A1012), c(0x2C1C1E), c(0x40282A), c(0x58383A)]
BOARD = [c(0x140E0C), c(0x221814), c(0x30221C), c(0x403026)]
RUNNER = [c(0x2A0C10), c(0x441418), c(0x5E1E22), c(0x7A2E30)]
SHEET = [c(0x5E6478), c(0x8A90A4), c(0xB4BACC), c(0xD8DCE8)]
BLANKET = [c(0x262A3A), c(0x363C52), c(0x4A526C), c(0x646E8A)]
NIGHT = [c(0x060914), c(0x0C1226), c(0x142040), c(0x22325C)]
MOON = [c(0x9AA4C0), c(0xC8D0E4), c(0xEEF2FA)]


def _h(x, y, s=0):
    return ((x * 73856093) ^ (y * 19349663) ^ (s * 83492791)) & 0xFFFF


def floorboards(img, x0, y0, x1, y1, runner=(80, 97)):
    """Dlouhá prkna podlahy (svisle, spáry po 6 px, posunuté konce), uprostřed vínový běhoun s lemem."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            col_i = (x - x0) // 6
            off = (col_i * 37) % 40
            seam = (x - x0) % 6 == 0 or (y + off) % 40 == 0
            k = 2 if col_i % 3 else 1
            if _h(col_i, (y + off) // 40) % 5 == 0:
                k = 3
            col = BOARD[0] if seam else BOARD[k]
            if runner[0] <= x < runner[1]:
                e = min(x - runner[0], runner[1] - 1 - x)
                col = RUNNER[0] if e == 0 else BRASS[1] if e == 1 else RUNNER[2] if ((x + y) // 3) % 4 else RUNNER[1]
            img[y, x] = col


def dorm_wall(W, H):
    """Zadní stěna: tmavá tapeta s pruhy a drobným vzorem, nahoře lišta, dole dřevěný obklad."""
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            if y < 4:
                col = WAINS[3] if y == 0 else WAINS[2] if y < 3 else WAINS[0]
            elif y > H - 24:
                ly = y - (H - 24)
                col = WAINS[3] if ly == 0 else WAINS[2] if (x % 22) not in (0, 21) else WAINS[0]
                if ly in (4, 18) and (x % 22) not in (0, 21):
                    col = WAINS[1]
                if ly == 23:
                    col = WAINS[0]
            else:
                col = PAPERW[1] if (x // 8) % 2 else PAPERW[2]
                if (x % 8 == 4) and ((y // 6) % 2 == 0) and (y % 6) in (2, 3):
                    col = PAPERW[3]                                                 # drobný vzor
            s.px(x, y, col)
    return s


def curtained_window(w=24, h=56, moon=False):
    """Vysoké okno do noci s průsvitnou záclonou: rám, hvězdy a měsíc, záclony po stranách v záhybech."""
    s = Spr(w, h)
    rng = np.random.default_rng(w + h)
    for y in range(h):
        for x in range(w):
            if y < w / 2 and math.hypot(x + .5 - w / 2, y + .5 - w / 2) > w / 2:
                continue
            s.px(x, y, WAINS[2])
    for y in range(2, h - 3):
        for x in range(2, w - 2):
            if y < w / 2 and math.hypot(x + .5 - w / 2, y + .5 - w / 2) > w / 2 - 2:
                continue
            s.px(x, y, NIGHT[1] if y < h * 0.5 else NIGHT[2])
    for _ in range(8):
        x, y = int(rng.integers(4, w - 4)), int(rng.integers(5, h // 2))
        if s.opaque(x, y):
            s.px(x, y, MOON[1])
    if moon:
        sphere(s, w * 0.55, h * 0.24, 3.2, 3.2, MOON, amb=0.6)
    for side in (0, 1):                                                             # záclony
        for y in range(2, h - 2):
            cw = 6 + int(2 * math.sin(y * 0.15 + side))
            for k in range(cw):
                x = 2 + k if side == 0 else w - 3 - k
                if not s.opaque(x, y):
                    continue
                fold = (k + y // 9) % 3
                col = SHEET[2] if fold == 0 else SHEET[1]
                base = s.a[y, x, :3].astype(int)
                s.a[y, x, :3] = (base * 0.35 + np.array(col[:3], int) * 0.65).astype(np.uint8)   # průsvitné
    for x in range(w):
        s.px(x, h - 3, WAINS[3]); s.px(x, h - 2, WAINS[1]); s.px(x, h - 1, WAINS[0])
    return selout(s, 0.55, 0.85)


def iron_bed(seed=0, sleeper=True):
    """Železná postel ve 3/4 (hlavou ke zdi): čelo s příčkami, polštář, přikrývka přes matraci; když [sleeper],
    pod přikrývkou se rýsuje tvar spáče (hlava v polštáři, ramena, kolena), čelo nohou s cedulkou čísla."""
    W, H = 26, 44
    s = Spr(W, H)
    rng = np.random.default_rng(seed)
    for y in range(0, 10):                                                          # čelo hlavy
        for x in (2, W - 3):
            s.px(x, y, IRON_[4] if x == 2 else IRON_[2])
        if y in (1, 8):
            s.hline(2, W - 3, y, IRON_[3])
    for x in range(5, W - 5, 4):
        s.vline(x, 2, 8, IRON_[2])
    sphere(s, 2.5, 0.5, 1.6, 1.4, BRASS, amb=0.4); sphere(s, W - 2.5, 0.5, 1.6, 1.4, BRASS, amb=0.4)
    for y in range(8, H - 6):                                                       # matrace + prostěradlo
        for x in range(3, W - 3):
            s.px(x, y, SHEET[2] if x < W - 5 else SHEET[1])
    for y in range(10, 17):                                                         # polštář
        for x in range(5, W - 5):
            e = min(x - 5, W - 6 - x, y - 10, 16 - y)
            s.px(x, y, SHEET[3] if e > 0 and y < 14 else SHEET[2])
    top = 16 if sleeper else 22
    for y in range(top, H - 6):                                                     # přikrývka
        for x in range(3, W - 3):
            nx = (x - W / 2) / (W / 2 - 3)
            lump = 0.0
            if sleeper:                                                             # tvar těla pod dekou
                lump = math.exp(-((y - 24) / 6) ** 2) * 0.8 + math.exp(-((y - 33) / 5) ** 2) * 0.5
                lump *= math.exp(-(nx / 0.55) ** 2)
            v = 0.45 + lump * 0.5 - nx * 0.25 + (0.15 if y == top else 0)
            if (x + y * 2) % 23 == 0 and not lump > 0.3:
                v -= 0.12                                                           # záhyby
            s.px(x, y, BLANKET[max(0, min(3, int(v * 4)))])
    if sleeper:                                                                     # temeno hlavy v polštáři
        for y in range(13, 17):
            for x in range(W // 2 - 3, W // 2 + 3):
                if ((x - W / 2 + .5) / 3) ** 2 + ((y - 16) / 3) ** 2 < 1:
                    s.px(x, y, c(0x3A3040) if y < 15 else c(0x2A2230))
    for y in range(H - 6, H):                                                       # čelo nohou
        for x in range(2, W - 2):
            s.px(x, y, IRON_[3] if y == H - 6 else IRON_[1] if (x - 2) % 4 else IRON_[2])
    for x in range(9, 17):
        s.px(x, H - 4, PAPER[3]); s.px(x, H - 3, PAPER[2])
    s.px(11, H - 4, INK); s.px(13, H - 4, INK); s.px(14, H - 3, INK)
    for lx in (2, W - 3):
        s.px(lx, H - 1, IRON_[0])
    return selout(s, 0.5, 0.82)


def candle_stand():
    """Noční stolek se svíčkou: dřevo ve 3/4, mosazný svícen, svíčka (plamen kreslí InteriorFxView)."""
    s = Spr(12, 24)
    for y in range(10, 13):
        for x in range(11):
            s.px(x, y, WAINS[3] if y == 10 else WAINS[2])
    for y in range(13, 24):
        for x in range(11):
            s.px(x, y, WAINS[1] if x else WAINS[3])
        s.px(11, y, WAINS[0])
    s.hline(2, 8, 17, WAINS[0]); s.px(5, 15, BRASS[3])
    for x in range(3, 8):
        s.px(x, 9, BRASS[3] if x < 5 else BRASS[1])
    for y in range(3, 9):                                                           # svíčka
        s.px(5, y, c(0xF4ECD8)); s.px(4, y, c(0xFAF6EA)); s.px(6, y, c(0xC8C0A8))
    s.px(5, 2, c(0x2A2420))
    s.px(6, 6, c(0xFAF6EA)); s.px(6, 7, c(0xF4ECD8))                                # stékající vosk
    return selout(s, 0.5, 0.82)


def nurse_desk():
    """Stolek noční sestry: deska ve 3/4, otevřená kniha spánku s tabulkou, petrolejka, zvonek, prázdná židle."""
    W, H = 40, 34
    s = Spr(W + 3, H)
    for y in range(0, 9):                                                           # opěradlo židle za stolem
        for x in range(W // 2 - 5, W // 2 + 6):
            e = min(x - (W // 2 - 5), W // 2 + 5 - x, y)
            s.px(x, y, WAINS[3] if e > 0 else WAINS[1])
    for y in range(9, 19):
        for x in range(W):
            s.px(x, y, WAINS[3] if y < 11 else WAINS[2])
    for y in range(19, H):
        for x in range(W):
            s.px(x, y, WAINS[1] if 19 < y < H - 2 else WAINS[0])
        s.px(0, y, WAINS[3])
    for x in range(W, W + 3):
        for y in range(9, H):
            if y - 9 >= x - W:
                s.px(x, y, WAINS[0])
    for x in range(8, 24):                                                          # kniha spánku
        for y in range(11, 17):
            mid = x == 16
            s.px(x, y, PAPER[1] if mid else PAPER[3])
            if not mid and y % 2 == 0 and 9 < x < 23:
                s.px(x, y, PAPER[0])
    for y in range(4, 11):                                                          # petrolejka
        for x in range(28, 33):
            s.px(x, y, c(0xFFE0A0) if 5 < y < 10 and 28 < x < 32 else BRASS[2])
    s.px(30, 3, BRASS[3]); s.hline(27, 33, 10, BRASS[1])
    sphere(s, 36.5, 11, 2, 1.6, BRASS, amb=0.4)                                     # zvonek
    return selout(s, 0.5, 0.82)


def screen():
    """Plátěná zástěna (paraván): tři rámy, mezi nimi bledé plátno, na jednom stín postavy za ním."""
    W, H = 40, 40
    s = Spr(W, H)
    for p in range(3):
        x0 = p * 13
        for y in range(0, H - 4):
            for x in range(x0, x0 + 13):
                edge = x in (x0, x0 + 12) or y in (0, H - 5)
                col = IRON_[3] if edge and x == x0 else IRON_[1] if edge else SHEET[2] if p != 1 else SHEET[1]
                s.px(x, y, col)
        for y in range(H - 4, H):
            s.px(x0 + 1, y, IRON_[2]); s.px(x0 + 11, y, IRON_[2])
    for y in range(8, 34):                                                          # stín postavy za plátnem
        for x in range(15, 24):
            d = ((x - 19.5) / (3 if y < 15 else 4.5)) ** 2 + ((y - (11 if y < 15 else 26)) / (4 if y < 15 else 9)) ** 2
            if d < 1:
                base = s.a[y, x, :3].astype(int)
                s.a[y, x, :3] = (base * 0.55).astype(np.uint8)
    return selout(s, 0.5, 0.82)


def silence_sign():
    """Cedule „TICHO – PROBÍHÁ SPÁNEK“: smaltovaná tabulka s rámečkem (drobné písmo naznačené tečkami)."""
    s = Spr(30, 12)
    for y in range(12):
        for x in range(30):
            e = min(x, y, 29 - x, 11 - y)
            s.px(x, y, c(0x1A2440) if e == 0 else c(0xE8E2D2) if e > 1 else c(0x8A8678))
    for x in range(4, 26, 2):
        s.px(x, 4, c(0x1A2440))
    for x in range(5, 25, 2):
        s.px(x, 7, c(0x6A2A2A))
    return s
