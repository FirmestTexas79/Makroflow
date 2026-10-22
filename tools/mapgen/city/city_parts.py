"""
Díly města Ozvěna (Akt II, návrh): domy skládané po prvcích (šindel po šindeli, kámen po kameni,
vlastní obrys každého prvku, stín pod okapem), pekárna, dům písařky Věnky popsaný jmény, kašna,
stánky, Aréna, Věž Řádu Váhy, sklad, zábradlí nad propastí, výtah a vnitřní strana městské brány.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
sys.path.insert(0, os.path.join(HERE, "..", "orchard"))
from pix import Spr, c, outline, blobs  # noqa: E402
from pixart import K, STN, WD, PL, GLASS, MOSS, stones, shingles, box_outline, from_ascii  # noqa: E402

ROOF_RED = [c(0x4A1C18), c(0x76302A), c(0xA24636), c(0xC8644A), c(0xE68A66)]
ROOF_TEAL = [c(0x14302E), c(0x22504C), c(0x2E726A), c(0x46968A), c(0x72BEB0)]
ROOF_SLATE = [c(0x1A1E2A), c(0x2A3242), c(0x3C485C), c(0x56647C), c(0x7C8AA4)]
ROOF_OCHRE = [c(0x4A3014), c(0x7A5020), c(0xA6702E), c(0xCC9440), c(0xEAB860)]
SHUT = {"green": [c(0x24482A), c(0x3A6E3E), c(0x5A9458)], "blue": [c(0x1E3A5A), c(0x2E5A86), c(0x4C82B2)],
        "red": [c(0x5A1E1E), c(0x8A2E2A), c(0xB4483E)], "ochre": [c(0x5A4018), c(0x8A6428), c(0xB48A3E)]}
FLOWERS = [c(0xE8546A), c(0xF4D060), c(0xF0F0F0), c(0xB070E0)]


def roof(s, x0, x1, top, rows, pal, ridge=True):
    """Šindelová střecha: [rows] řad po 3 px, každá o 1 px širší na obě strany; obrys a hřebenáč."""
    spec = [(top + 2 + i * 3, x0 + rows - i, x1 - rows + i) for i in range(rows)]
    shingles(s, spec, pal=pal)
    for (y, a, b) in spec:
        for yy in (y, y + 1, y + 2):
            s.px(a - 1, yy, K); s.px(b + 1, yy, K)
    if ridge:
        a, b = spec[0][1], spec[0][2]
        s.hline(a, b, top + 1, STN[4]); s.hline(a, b, top, K)
    yb = spec[-1][0] + 3
    s.hline(x0 - 1, x1 + 1, yb, pal[0]); s.hline(x0 - 1, x1 + 1, yb + 1, K)
    return yb + 2


def window(s, x, y, w=8, h=8, shut="green", box=True, lit=False):
    g1, g2, g3 = (c(0xE8A040), c(0xFFD27A), c(0xFFF2B0)) if lit else (GLASS[1], GLASS[2], GLASS[3])
    s.rect(x, y, x + w, y + h, WD[3]); s.rect(x + 1, y + 1, x + w - 1, y + h - 1, g1)
    s.rect(x + 1, y + 1, x + w // 2 - 1, y + h // 2 - 1, g2); s.px(x + 2, y + 1, g3); s.px(x + 1, y + 2, g3)
    s.vline(x + w // 2, y + 1, y + h - 1, WD[1]); s.hline(x + 1, x + w - 1, y + h // 2, WD[1])
    box_outline(s, x, y, x + w, y + h)
    if shut:
        S = SHUT[shut]
        for sx in (x - 3, x + w + 1):
            s.rect(sx, y, sx + 2, y + h, S[1]); s.vline(sx, y, y + h, S[2]); s.hline(sx, sx + 2, y + h // 2, S[0])
            box_outline(s, sx, y, sx + 2, y + h)
    if box:
        s.rect(x - 1, y + h + 1, x + w + 1, y + h + 3, WD[2]); s.hline(x - 1, x + w + 1, y + h + 1, WD[4])
        box_outline(s, x - 1, y + h + 1, x + w + 1, y + h + 3)
        for i, fx in enumerate(range(x, x + w + 1, 2)):
            s.px(fx, y + h, FLOWERS[(i + x) % 4]); s.px(fx + 1, y + h, MOSS[2])


def door(s, x, y, w=8, h=12, col=WD, arch=True, knob=c(0xE0B040)):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if arch and yy < y + 2 and (xx in (x, x + w - 1)):
                continue
            s.px(xx, yy, col[2] if (xx - x) % 3 else col[1])
    s.px(x + w - 2, y + h // 2 + 1, knob)
    for xx in range(x, x + w):
        top = y + (1 if xx in (x, x + w - 1) else 0) - (1 if arch and x + 2 <= xx <= x + w - 3 else 0)
        s.px(xx, top, K)
    s.vline(x - 1, y + 1, y + h - 1, K); s.vline(x + w, y + 1, y + h - 1, K)


def house(w=48, wall_h=24, rows=6, roof_pal=ROOF_RED, wall="plaster", shut="green", door_col=None,
          windows=2, chimney=True, seed=1, lit=False, storeys=1):
    """Městský dům: šindelová střecha, komín, omítka / hrázdění / kámen, okenice a truhlíky, dveře se schodem."""
    roof_h = rows * 3 + 4
    H = roof_h + wall_h + 4
    s = Spr(w, H)
    y_wall = roof(s, 1, w - 2, 0, rows, roof_pal)
    if chimney:
        cx = w - 14 - seed % 6
        stones(s, cx, cx + 5, 0, 8, h=3, w=(3, 3), seed=seed)
        box_outline(s, cx - 1, 0, cx + 6, 9); s.hline(cx, cx + 5, 1, STN[4])
    # stěna
    y0, y1 = y_wall, y_wall + wall_h - 1
    if wall == "stone":
        stones(s, 2, w - 3, y0, y1, h=4, w=(5, 8), seed=seed, tone=1)
    else:
        base = PL if wall != "ochre" else [c(0xA88A5C), c(0xCCAA72), c(0xE6C890), c(0xF4DEAA)]
        rr = np.random.default_rng(seed * 7 + 1)
        for yy in range(y0, y1 + 1):
            v = (yy - y0) / max(1, wall_h)
            for xx in range(2, w - 2):
                col = base[0] if yy <= y0 + 1 else base[1] if yy <= y0 + 3 else base[2]
                n = ((xx * 73 + yy * 151 + seed * 31) % 17)
                if yy > y0 + 3 and n == 0:
                    col = base[1]                                    # skvrny v omítce
                elif yy > y0 + 3 and n == 1 and v < 0.6:
                    col = base[3]
                if v > 0.82 and (xx + yy) % 2 == 0:
                    col = base[1]                                    # zašpinění u země
                s.px(xx, yy, col)
        # odpadlá omítka – cihly
        for _ in range(2):
            bx = int(rr.integers(4, max(5, w - 14))); by = int(rr.integers(y0 + 5, max(y0 + 6, y1 - 6)))
            for yy in range(by, by + 4):
                for xx in range(bx, bx + 6 - abs(yy - by - 1)):
                    if 2 < xx < w - 3:
                        brick = (xx + (yy % 2) * 2) % 4 == 0 or yy % 2 == 0
                        s.px(xx, yy, c(0x8A4A36) if brick else c(0xB4644A))
        # nároží z kvádrů
        for qx in (2, w - 6):
            for i, yy in enumerate(range(y0 + 2, y1 - 1, 4)):
                ww = 4 if i % 2 == 0 else 3
                x0q = qx if qx == 2 else qx + (4 - ww)
                s.rect(x0q, yy, x0q + ww - 1, yy + 2, STN[3]); s.hline(x0q, x0q + ww - 1, yy, STN[4])
                s.hline(x0q, x0q + ww - 1, yy + 3, STN[1])
        if wall == "timber":
            for bx in (2, w // 2, w - 3):
                s.vline(bx, y0, y1, WD[1])
            s.hline(2, w - 3, y0 + wall_h // 2 - 2, WD[1])
    box_outline(s, 1, y0, w - 2, y1 + 1)
    # okna a dveře
    dw = 8
    dx = w // 2 - dw // 2 + (6 if windows == 1 else 0)
    door(s, dx, y1 - 11, dw, 12, col=door_col or WD)
    slots = [w // 4 - 4, 3 * w // 4 - 4] if windows == 2 else [w // 4 - 4]
    for wx in slots:
        if abs(wx + 4 - (dx + 4)) > 9:
            window(s, wx, y1 - 18 if storeys == 2 else y0 + 4, 8, 7, shut=shut, lit=lit)
    if storeys == 2:                                                # horní patro: okna nad dveřmi i po stranách
        s.hline(2, w - 3, y0 + wall_h // 2 - 3, WD[1] if wall != "stone" else STN[1])
        for wx in ([w // 4 - 4, w // 2 - 4, 3 * w // 4 - 4] if w >= 48 else [w // 2 - 4]):
            window(s, wx, y0 + 3, 8, 7, shut=shut, box=wx != w // 2 - 4, lit=lit)
    # podezdívka a schod
    stones(s, 1, w - 2, y1 + 2, y1 + 3, h=2, w=(4, 6), seed=seed + 3)
    s.rect(dx - 1, y1 + 1, dx + dw, y1 + 3, STN[3]); s.hline(dx - 1, dx + dw, y1 + 1, STN[4])
    s.hline(1, w - 2, H - 1, K)
    return s


def bakery():
    """Pekárna Bohouška: okrová omítka, výloha s chleby, pruhovaná markýza, vývěsní preclík, komín."""
    s = house(64, 28, rows=7, roof_pal=ROOF_OCHRE, wall="ochre", shut=None, windows=0, chimney=True, seed=4)
    y0 = 7 * 3 + 4 + 2
    # výloha
    s.rect(6, y0 + 9, 30, y0 + 22, WD[3]); s.rect(7, y0 + 10, 29, y0 + 21, GLASS[1])
    s.rect(7, y0 + 10, 14, y0 + 13, GLASS[2]); box_outline(s, 6, y0 + 9, 30, y0 + 22)
    for i, bx in enumerate(range(9, 28, 5)):                     # chleby ve výloze
        s.rect(bx, y0 + 17, bx + 3, y0 + 19, c(0xC8823A)); s.hline(bx, bx + 3, y0 + 17, c(0xE8B060)); s.px(bx + 1, y0 + 18, c(0x8A5420))
        s.ellipse(bx + 2, y0 + 14, 1.6, 1.2, c(0xE0A050))
    # markýza
    for yy in range(y0 + 2, y0 + 8):
        for xx in range(4, 33):
            stripe = ((xx - 4) // 3) % 2
            s.px(xx, yy, (c(0xC83A32) if stripe else c(0xF4EEE0)) if yy < y0 + 7 else K)
    for xx in range(4, 33, 3):
        s.px(xx, y0 + 8, c(0xC83A32) if ((xx - 4) // 3) % 2 else c(0xF4EEE0))
    box_outline(s, 3, y0 + 1, 33, y0 + 7)
    # vývěsní štít s preclíkem
    s.hline(50, 58, y0 + 2, K); s.vline(58, y0 + 2, y0 + 4, K)
    pretzel = ["..ooo.ooo..", ".o...o...o.", "o...o.o...o", "o..o...o..o", ".oo.....oo.", "...ooooo..."]
    for yy, row in enumerate(pretzel):
        for xx, ch in enumerate(row):
            if ch == "o":
                s.px(47 + xx, y0 + 5 + yy, c(0xC8823A))
    return s


def venka_house():
    """Dům písařky Věnky: bílá omítka hustě popsaná jmény (řádky drobných čárek), břidlicová střecha."""
    s = house(44, 26, rows=6, roof_pal=ROOF_SLATE, wall="plaster", shut="blue", windows=1, chimney=False, seed=7)
    r = np.random.default_rng(3)
    y0 = 6 * 3 + 4 + 2
    for yy in range(y0 + 2, y0 + 24, 2):
        x = 3 + int(r.integers(0, 3))
        while x < 40:
            ln = int(r.integers(2, 6))
            for k in range(ln):
                if s.a[yy, x + k, :3].tolist() == list(PL[2][:3]):
                    s.a[yy, x + k] = c(0x3A3438) if r.random() > 0.15 else c(0x8A2A2A)
            x += ln + int(r.integers(1, 3))
    return s


def fountain():
    """
    Kašna 80 × 64: osmiboká kamenná nádrž s reliéfními panely, na podstavci bílá socha zahalené postavy,
    která drží nad hlavou VELKÉ zlaté váhy – z obou misek teče voda do nádrže.
    """
    W, H = 80, 70
    s = Spr(W, H)
    WATER = [c(0x24608E), c(0x3480B2), c(0x5EA6D2), c(0xCDEBFA)]
    GOLD = [c(0x7A5A16), c(0xB8902E), c(0xE6C258), c(0xFFF0A8)]
    MARB = [c(0x8E8A92), c(0xB8B4BC), c(0xDCD8DE), c(0xF6F4F6)]
    cx, cy = 39.5, 46
    def octa(x, y, rx, ry):
        dx, dy = abs(x - cx) / rx, abs(y - cy) / ry
        return max(dx, dy, (dx + dy) / 1.38) <= 1
    for y in range(28, H):
        for x in range(W):
            if not octa(x, y, 39, 15):
                continue
            if octa(x, y, 33, 11):
                k = 1 if (x + y * 3) % 11 else 2
                if ((x - cx) ** 2 / 900 + (y - cy) ** 2 / 100) < 0.25 and (x + y) % 5 == 0:
                    k = 2
                s.px(x, y, WATER[k])
                if (x * 5 + y * 11) % 89 == 0:
                    s.px(x, y, WATER[3])
            elif y < cy:
                s.px(x, y, MARB[3] if not octa(x, y, 37.5, 14) else MARB[2])       # horní hrana obruby
            else:
                s.px(x, y, MARB[2] if not octa(x, y, 37.5, 14) else MARB[1])
    # přední stěna nádrže s reliéfními panely (pod spodní hranou obruby)
    for x in range(W):
        ys = [y for y in range(28, H - 7) if octa(x, y, 39, 15)]
        if not ys:
            continue
        yb = max(ys)
        if yb < cy + 4:
            continue
        for k in range(1, 7):
            y = yb + k
            panel = (x // 10) % 2
            col = MARB[1] if panel else MARB[2]
            if x % 10 == 0 or k == 6:
                col = MARB[0]
            if 3 < x % 10 < 7 and k == 3:
                col = GOLD[1]
            s.px(x, y, col)
    # podstavec
    for y in range(24, 50):
        for x in range(33, 47):
            k = 3 if x < 36 else 2 if x < 43 else 1
            s.px(x, y, MARB[k])
    s.hline(31, 48, 24, MARB[3]); s.hline(31, 48, 25, MARB[1]); s.hline(32, 47, 49, MARB[0])
    # socha: zahalená postava – hlava s páskou, ramena, roucho se záhyby, ruce vzhůru
    for y in range(11, 24):
        half = 3 + (y - 11) // 4
        for x in range(int(cx - half), int(cx + half) + 1):
            fold = (x - int(cx - half)) % 3 == 0 and y > 14
            s.px(x, y, MARB[1] if fold else MARB[3] if x < cx - 1 else MARB[2] if x < cx + 2 else MARB[1])
    s.rect(35, 10, 44, 12, MARB[2]); s.hline(35, 44, 10, MARB[3])            # ramena
    s.ellipse(cx, 7.5, 2.8, 2.8, MARB[3]); s.hline(37, 42, 7, c(0x4A4650))  # hlava, páska přes oči
    s.line(35, 10, 33, 4, MARB[2], w=2); s.line(44, 10, 46, 4, MARB[1], w=2) # paže drží vahadlo
    # VELKÉ váhy nad hlavou: sloupek, vahadlo, řetízky, misky
    s.vline(39, 0, 5, GOLD[2]); s.vline(40, 0, 5, GOLD[1]); s.rect(38, 0, 41, 1, GOLD[3])
    s.rect(17, 3, 62, 6, GOLD[1]); s.hline(17, 62, 3, GOLD[3]); s.hline(17, 62, 4, GOLD[2])
    for px0 in (19, 60):
        s.line(px0, 6, px0 - 5, 13, GOLD[0]); s.line(px0, 6, px0 + 5, 13, GOLD[0])
        for k in range(4):
            s.hline(px0 - 6 + k, px0 + 6 - k, 14 + k, GOLD[2] if k == 0 else GOLD[1] if k < 3 else GOLD[0])
        s.hline(px0 - 5, px0 + 5, 14, GOLD[3])
    from pix import outline as _o
    _o(s, K)
    for px0 in (19, 60):
        for i in range(20):
            x = px0 + (1 if px0 > 39 else -1) * (i // 6)
            s.px(x, 18 + i, WATER[3] if i % 3 else WATER[2]); s.px(x + (1 if px0 > 39 else -1), 18 + i, WATER[2])
        for (dx, dy) in ((-3, 37), (3, 37), (-1, 38), (2, 38)):
            s.px(px0 + (2 if px0 > 39 else -2) + dx, dy, WATER[3])     # čeření pod proudem
    return s


def bunting(w):
    """Girlanda praporků w × 8: prověšená šňůra s barevnými trojúhelníky."""
    s = Spr(w, 8)
    cols = [c(0xC83A32), c(0xF4D060), c(0x2E6AA8), c(0x3A8A4A), c(0xF4EEE0)]
    for x in range(w):
        y = int(1 + 3 * math.sin(math.pi * x / (w - 1)))
        s.px(x, y, c(0x3A3438))
        if x % 6 == 1:
            col = cols[(x // 6) % len(cols)]
            for k in range(3):
                for dx in range(-1 + k // 2, 2 - k // 2):
                    s.px(x + 1 + dx, y + 1 + k, col)
    return s


def barrel():
    s = from_ascii([
        "..kkkkkk..",
        ".kVWWWWwk.",
        "kNNNNNNNNk",
        "kVWWWWWwwk",
        "kVWWWWWwwk",
        "kNNNNNNNNk",
        "kVWWWWWwwk",
        ".kVWWWWwk.",
        "..kkkkkk..",
    ], {"k": K, "V": WD[4], "W": WD[3], "w": WD[2], "N": c(0x4A4A56)})
    return s


def crate(goods=None):
    s = from_ascii([
        "kkkkkkkkkk",
        "kVVVVVVVVk",
        "kVWwWWwWVk",
        "kVwWWWWwVk",
        "kVWWwwWWVk",
        "kVwWWWWwVk",
        "kVVVVVVVVk",
        "kkkkkkkkkk",
    ], {"k": K, "V": WD[4], "W": WD[3], "w": WD[2]})
    return s


def round_tree(seed=0):
    """Městský listnatý strom 32 × 40 (sytě zelený, na rozdíl od popelavých za hradbou)."""
    r = np.random.default_rng(seed)
    s = Spr(32, 40)
    for y in range(27, 38):
        for x in range(14, 18):
            s.px(x, y, WD[3] if x == 14 else WD[2] if x < 17 else WD[1])
    j = lambda v: v + int(r.integers(-1, 2))
    blobs(s, [(j(9), 20, 7.5), (j(23), 20, 7.5), (16, j(21), 8), (j(11), 12, 7), (j(21), 12, 7), (16, j(9), 7)],
          [c(0x1C4020), c(0x2C6030), c(0x448844), c(0x66B05A)])
    return outline(s, K)


def hedge(w):
    s = Spr(w, 12)
    blobs(s, [(x, 6, 5.5) for x in range(5, w - 3, 6)], [c(0x1E4220), c(0x2E6230), c(0x468A42), c(0x64AE56)])
    return outline(s, K)


def cliff_edge(w, h=34):
    """Skalní hrana nad propastí w × h: horní okraj, čelo se slojemi a puklinami mizící do tmy."""
    s = Spr(w, h)
    for y in range(h):
        for x in range(w):
            band = (y + (x // 6) % 2) % 4
            k = 3 if band == 0 else 2 if band < 3 else 1
            if (x * 7 + y * 3) % 19 == 0:
                k = 0
            col = STN[k]
            f = (y / h) ** 0.8
            if y > h - 6 and (x * 5 + y) % 7 < y - (h - 6):
                continue                                             # rozeklaný spodek mizí do tmy
            s.px(x, y, (int(col[0] * (1 - f * 0.8)), int(col[1] * (1 - f * 0.8)), int(col[2] * (1 - f * 0.7)), 255))
    s.hline(0, w - 1, 0, STN[4])
    return s


def stall(cloth="red", goods="apples"):
    """Tržní stánek 30 × 30: pruhovaná plachta, pult se zbožím, sloupky."""
    s = Spr(30, 30)
    C = {"red": c(0xC83A32), "blue": c(0x2E6AA8), "green": c(0x3A8A4A)}[cloth]
    for yy in range(0, 9):
        inset = max(0, 3 - yy)
        for xx in range(inset, 30 - inset):
            s.px(xx, yy, (C if ((xx // 4) % 2) else c(0xF4EEE0)) if yy < 8 else K)
    for xx in range(1, 29, 4):
        s.px(xx, 9, C); s.px(xx + 1, 9, C)
    s.rect(2, 9, 3, 28, WD[1]); s.rect(26, 9, 27, 28, WD[1])
    s.rect(1, 18, 28, 27, WD[2]); s.hline(1, 28, 18, WD[4]); s.hline(1, 28, 22, WD[1])
    for i, gx in enumerate(range(3, 27, 3)):
        col = (c(0xC8362A), c(0xF06A50)) if goods == "apples" else (c(0xC8823A), c(0xE8B060))
        s.px(gx, 16, col[0]); s.px(gx + 1, 16, col[0]); s.px(gx, 15, col[1]); s.px(gx + 1, 17, col[0])
    return outline(s, K)


def bench():
    s = from_ascii([
        "kkkkkkkkkkkkkkkkkk",
        "kVVVVVVVVVVVVVVVVk",
        "kWWWWWWWWWWWWWWWWk",
        "kkkkkkkkkkkkkkkkkk",
        "kVVVVVVVVVVVVVVVVk",
        "kwwwwwwwwwwwwwwwwk",
        "kkkkkkkkkkkkkkkkkk",
        ".kNk..........kNk.",
        ".kNk..........kNk.",
        ".kkk..........kkk.",
    ], {"k": K, "V": WD[4], "W": WD[3], "w": WD[2], "N": c(0x3A3A44)})
    return s


def city_lamp():
    s = Spr(12, 30)
    s.rect(5, 8, 6, 28, c(0x2A2A32)); s.vline(5, 8, 28, c(0x4A4A56))
    s.rect(3, 27, 8, 29, c(0x2A2A32))
    s.rect(2, 1, 9, 8, c(0x2A2A32)); s.rect(3, 3, 8, 6, c(0xFFD27A)); s.hline(3, 8, 3, c(0xFFF2B0)); s.hline(3, 8, 6, c(0xE89A3A))
    s.hline(1, 10, 1, c(0x4A4A56)); s.px(5, 0, c(0x4A4A56)); s.px(6, 0, c(0x4A4A56))
    return outline(s, K)


def planter_tree(seed=0):
    s = Spr(28, 40)
    blobs(s, [(8, 16, 7), (20, 16, 7), (14, 12, 8), (14, 20, 7)], [c(0x1E4A22), c(0x2E6E30), c(0x48964A), c(0x6EBE60)])
    s.rect(13, 24, 15, 31, WD[2])
    stones(s, 6, 21, 31, 38, h=4, w=(4, 5), seed=seed)
    for x in range(7, 21, 2):
        s.px(x, 31, FLOWERS[x % 4])
    return outline(s, K)


def flower_bed(w=32):
    """Záhon w × 12: hlína, keříky květin ve dvou řadách, kamenná obruba."""
    s = Spr(w, 12)
    s.rect(1, 3, w - 2, 8, c(0x5A3E26))
    for i, x in enumerate(range(2, w - 2, 4)):
        for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            s.px(x + dx, 3 + dy, MOSS[1]); s.px(x + 2 + dx, 6 + dy, MOSS[1])
        s.px(x, 3, FLOWERS[i % 4]); s.px(x + 1, 2, FLOWERS[i % 4]); s.px(x + 2, 6, FLOWERS[(i + 2) % 4]); s.px(x + 3, 5, FLOWERS[(i + 2) % 4])
    stones(s, 0, w - 1, 9, 11, h=3, w=(4, 6), seed=w)
    return outline(s, K)


def notice_board():
    s = Spr(22, 24)
    s.rect(2, 10, 3, 23, WD[1]); s.rect(18, 10, 19, 23, WD[1])
    s.rect(0, 2, 21, 14, WD[2]); s.rect(2, 4, 19, 12, c(0xC8A870))
    for (x, y, w, h) in ((3, 5, 5, 4), (9, 5, 6, 6), (15, 6, 4, 5), (4, 9, 4, 3)):
        s.rect(x, y, x + w, y + h, c(0xF4EEE0)); s.hline(x + 1, x + w - 1, y + 2, c(0x8A8A8A)); s.px(x + w // 2, y, c(0xC83A32))
    s.rect(-1, 0, 22, 2, ROOF_RED[2]); s.hline(0, 21, 0, ROOF_RED[4])
    return outline(s, K)


def arena():
    """Aréna 112 × 80 (pohled shora zepředu): vnější kamenný prstenec s oblouky, hlediště ve stupních,
    písečná plocha, prapory nahoře, vstupní brána dole uprostřed."""
    W, H = 112, 80
    s = Spr(W, H)
    cx, cy = 55.5, 34
    for y in range(H):
        for x in range(W):
            d = ((x - cx) / 54) ** 2 + ((y - cy) / 30) ** 2
            if d <= 1:
                if d < 0.30:
                    s.px(x, y, c(0xD8C08A) if (x + y) % 5 else c(0xC4AA72))            # písek
                elif d < 0.36:
                    s.px(x, y, STN[1])                                               # nízká zídka
                elif d < 0.92:
                    tier = int((d - 0.36) / 0.08)
                    s.px(x, y, (c(0x8E3A30) if tier % 2 else c(0xB4503E)) if (x // 3) % 7 else STN[2])
                else:
                    s.px(x, y, STN[4])
    # přední vnější stěna s oblouky (pod horní hranou elipsy)
    for y in range(cy, H - 2):
        for x in range(W):
            outer = ((x - cx) / 54) ** 2 + ((y - 14 - cy) / 30) ** 2 <= 1 and ((x - cx) / 54) ** 2 + ((y - cy) / 30) ** 2 > 1
            if outer:
                arch_x = (x + 3) % 10
                ay = y - (cy + 14 + 30 * math.sqrt(max(0, 1 - ((x - cx) / 54) ** 2)) - 16)
                col = STN[3] if (y // 4) % 2 else STN[2]
                if 2 <= arch_x <= 7 and 4 <= ay <= 12 and not (ay < 6 and arch_x in (2, 7)):
                    col = c(0x1E1A1C)
                s.px(x, y, col)
    # hlavní brána dole
    s.rect(cx - 8, H - 18, cx + 8, H - 3, c(0x141012)); s.rect(cx - 10, H - 20, cx + 10, H - 17, STN[4])
    s.rect(cx - 6, H - 26, cx + 6, H - 21, c(0xB8902E)); s.rect(cx - 5, H - 25, cx + 5, H - 22, c(0xE6C258))
    # prapory
    for i, bx in enumerate(range(8, 104, 16)):
        by = int(cy - 30 * math.sqrt(max(0, 1 - ((bx - cx) / 54) ** 2)))
        s.vline(bx, by - 10, by, c(0x3A3A44))
        for k in range(5):
            s.hline(bx + 1, bx + 5, by - 10 + k, c(0x2E6AA8) if i % 2 else c(0xC83A32))
    return outline(s, K)


def scale_tower():
    """Věž Řádu Váhy 60 × 132: čtyřhranná věž z tmavého kamene s bílým lemováním, břidlicová jehla,
    místo hodin znak vah, úzká okna, velké dveře se schody, černobílé prapory."""
    W, H = 60, 132
    s = Spr(W, H)
    # jehla
    for y in range(0, 40):
        half = y * 22 / 40
        for x in range(int(30 - half), int(30 + half) + 1):
            t = (x - 30) / max(half, 1)
            k = 4 if t < -0.4 else 3 if t < 0.1 else 2 if t < 0.6 else 1
            if y % 4 == 0:
                k = max(0, k - 1)
            s.px(x, y, ROOF_SLATE[k])
    s.vline(30, 0, 3, c(0xE6C258))
    # ochoz
    stones(s, 6, 53, 40, 45, h=3, w=(3, 5), seed=21, tone=1)
    for x in range(6, 54, 5):
        s.rect(x, 36, x + 2, 39, STN[4])
    # tělo věže
    dark = [c(0x24222A), c(0x34323C), c(0x4A4652), c(0x625E6A), c(0x807A86)]
    for y in range(46, 118):
        row = (y - 46) // 5
        for x in range(10, 50):
            joint = (y - 46) % 5 == 0 or (x + row % 2 * 5) % 10 == 0
            k = 1 if joint else 3 if (y - 46) % 5 == 1 else 2
            if x > 40:
                k = max(0, k - 1)
            s.px(x, y, dark[k])
    for x in (10, 11, 48, 49):
        s.vline(x, 46, 117, c(0xE6E2D8) if x in (10, 48) else c(0xC8C2B6))   # bílé nároží
    # znak vah: velké zlaté váhy přímo na tmavém kameni (bez rámu), pod nimi bílá římsa
    G = [c(0x7A5A16), c(0xB8902E), c(0xE6C258), c(0xFFF0A8)]
    s.rect(28, 50, 31, 74, G[1]); s.vline(28, 50, 74, G[3]); s.vline(31, 50, 74, G[0])     # sloupek
    s.rect(25, 74, 34, 76, G[2]); s.hline(25, 34, 74, G[3])                               # podstavec
    s.rect(14, 53, 45, 55, G[2]); s.hline(14, 45, 53, G[3]); s.hline(14, 45, 55, G[0])     # vahadlo
    s.rect(27, 48, 32, 51, G[2]); s.px(29, 47, G[3]); s.px(30, 47, G[3])                  # hlavice
    for px0 in (16, 43):
        s.line(px0, 56, px0 - 5, 64, G[1]); s.line(px0, 56, px0 + 5, 64, G[1])            # řetízky
        for k in range(4):
            s.hline(px0 - 7 + k, px0 + 7 - k, 65 + k, G[3] if k == 0 else G[2] if k < 2 else G[1])
        s.hline(px0 - 3, px0 + 3, 69, G[0])
    s.hline(12, 47, 78, c(0xE6E2D8)); s.hline(12, 47, 79, c(0xA8A2A0))
    # úzká okna
    for (x, y) in ((16, 80), (41, 80), (16, 96), (41, 96)):
        s.rect(x, y, x + 2, y + 8, c(0xFFD27A) if y == 80 else c(0x14121A)); s.px(x + 1, y - 1, c(0xE6E2D8))
    # dveře se schody
    s.rect(22, 98, 37, 117, c(0xE6E2D8)); s.rect(24, 100, 35, 117, c(0x2A1C14))
    for xx in range(24, 36, 3):
        s.vline(xx, 101, 117, c(0x4A3220))
    s.px(33, 109, c(0xE0B040))
    for i in range(4):
        s.rect(18 - i * 2, 118 + i * 3, 41 + i * 2, 120 + i * 3, STN[3 if i % 2 else 2]); s.hline(18 - i * 2, 41 + i * 2, 118 + i * 3, STN[4])
    return outline(s, K)


def shed():
    s = Spr(28, 28)
    for y in range(0, 9):
        for x in range(0, 28):
            s.px(x, y, c(0x3A3238) if y % 3 == 0 else c(0x4E4650))
    for y in range(9, 27):
        for x in range(2, 26):
            s.px(x, y, c(0x4A3826) if x % 4 else c(0x34261A))
    s.rect(9, 13, 18, 26, c(0x2A1E14)); s.rect(12, 18, 15, 21, c(0x8A8A96)); s.px(13, 21, c(0x3A3A44))
    s.hline(0, 27, 9, K)
    return outline(s, K)


def balustrade(w, lamps=True):
    """Kamenné zábradlí w × 24: madlo se světlou hranou, kuželkové balustry, každých 24 px sloupek
    s hlavicí (na každém druhém lucerna), sokl."""
    s = Spr(w, 24)
    top = 8
    for x in range(w):                                             # madlo
        s.px(x, top, STN[4]); s.px(x, top + 1, STN[3]); s.px(x, top + 2, STN[2]); s.px(x, top + 3, STN[1])
    for x in range(2, w - 2, 4):                                   # balustry (kuželky)
        prof = [1, 2, 2, 1, 1, 2, 3, 3, 2, 1]
        for i, half in enumerate(prof):
            y = top + 4 + i
            for dx in range(-half + 1, half + 1):
                s.px(x + dx, y, STN[3] if dx <= 0 else STN[1])
    for x in range(w):                                             # sokl
        s.px(x, top + 14, STN[3]); s.px(x, top + 15, STN[2])
    for i, px0 in enumerate(range(0, w, 24)):                      # sloupky
        for y in range(top - 3, top + 16):
            for x in range(px0, min(w, px0 + 5)):
                s.px(x, y, STN[4] if x == px0 else STN[2] if x < px0 + 4 else STN[1])
        s.rect(px0 - 1, top - 4, px0 + 5, top - 3, STN[4]); s.hline(px0 - 1, px0 + 5, top - 2, STN[1])
        if lamps and i % 2 == 0:
            s.rect(px0 + 1, top - 9, px0 + 3, top - 5, c(0x2A2426)); s.px(px0 + 2, top - 7, c(0xFFD27A)); s.px(px0 + 2, top - 8, c(0xFFF2B0))
            s.px(px0 + 2, top - 10, c(0x2A2426))
    from pix import outline as _o
    return _o(s, K)


def elevator():
    """Výtah dolů 34 × 60: železná věžička s kladkou, klec, cedulka „Subjekt připraven“ (zatím prázdná)."""
    s = Spr(34, 60)
    iron = [c(0x22222A), c(0x3A3A46), c(0x5A5A68)]
    for x in (3, 30):
        s.rect(x, 4, x + 1, 59, iron[1]); s.vline(x, 4, 59, iron[2])
    for y in range(8, 58, 8):
        s.line(4, y, 30, y + 8, iron[1])
    s.rect(1, 2, 32, 5, iron[1]); s.hline(1, 32, 2, iron[2])
    s.ellipse(17, 3, 4, 4, iron[2]); s.ellipse(17, 3, 2, 2, iron[0])
    s.vline(17, 7, 30, c(0xC8B08A))
    s.rect(7, 30, 27, 54, iron[0])
    for x in range(8, 27, 3):
        s.vline(x, 31, 53, iron[2])
    s.hline(7, 27, 30, iron[2]); s.hline(7, 27, 54, iron[2])
    s.rect(10, 22, 24, 27, c(0xE6E2D8)); s.hline(11, 23, 24, c(0x3A3438))
    return outline(s, K)


def inner_gate(W=240):
    """Vnitřní strana severní hradby s průjezdem (vidíme její jižní líc – správná perspektiva)."""
    s = Spr(W, 60)
    stones(s, 0, W - 1, 14, 57, h=5, w=(8, 14), seed=55, tone=1)
    for x in range(0, W, 10):
        s.rect(x, 6, x + 6, 13, STN[3]); s.hline(x, x + 6, 6, STN[4])
    s.hline(0, W - 1, 13, STN[1])
    cx = W // 2
    for y in range(26, 58):
        for x in range(cx - 14, cx + 15):
            inside = (math.hypot(x - cx, y - 40) < 13) if y < 40 else abs(x - cx) < 13
            ring = (13 <= math.hypot(x - cx, y - 40) < 17) if y < 40 else 13 <= abs(x - cx) < 16
            if inside:
                s.px(x, y, c(0x16120E) if y < 50 else c(0x3A3226))
            elif ring:
                s.px(x, y, STN[4] if x < cx else STN[3])
    for bx in (cx - 30, cx + 26):
        for y in range(16, 40):
            for x in range(bx, bx + 6):
                s.px(x, y, c(0x2E6AA8) if (x - bx) in (0, 5) else c(0xC83A32))
        s.px(bx + 2, 28, c(0xE6C258)); s.px(bx + 3, 28, c(0xE6C258))
    for lx in (cx - 20, cx + 19):
        s.rect(lx - 1, 34, lx + 1, 39, c(0x2A2426)); s.px(lx, 36, c(0xFFD27A))
    return outline(s, K)


BW_LEAF = [c(0x10262A), c(0x183C3A), c(0x22584A), c(0x2E7656), c(0x48946A), c(0x78B884)]


def tree_bw(seed=0, h=50):
    """
    Strom ve stylu B/W 34 × h: tři patra koruny nad sebou, každé patro je elipsa se světlým lemem nahoře,
    tmavým zubatým spodkem (stín dalšího patra) a jemnými lístky – plochý, ale působí prostorově.
    """
    r = np.random.default_rng(seed)
    W = 34
    s = Spr(W, h)
    # kmen
    for y in range(h - 15, h - 1):
        for x in range(14, 20):
            s.px(x, y, WD[3] if x == 14 else WD[2] if x < 18 else WD[1])
    s.hline(13, 20, h - 1, WD[0])
    tiers = [(17, h - 22, 16.5, 10), (17, h - 33, 13, 9), (17, h - 42, 9, 7.5)]
    for ti, (cx, cy, rx, ry) in enumerate(tiers):
        cx += int(r.integers(-1, 2))
        for y in range(int(cy - ry) - 1, int(cy + ry) + 3):
            for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
                # zubatý spodní okraj (lalůčky)
                lob = 1.0 + 0.12 * math.sin((x - cx) * 1.3 + ti)
                d = ((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / (ry * lob)) ** 2
                if d > 1:
                    continue
                v = (y - (cy - ry)) / (2 * ry)                       # 0 nahoře → 1 dole
                side = (x - cx) / rx                                 # světlo zleva
                k = 5 if v < 0.16 else 4 if v < 0.38 else 3 if v < 0.62 else 2 if v < 0.82 else 1
                if side > 0.55:
                    k = max(1, k - 1)
                if side < -0.6 and v < 0.6:
                    k = min(5, k + 1)
                if 0.25 < v < 0.75 and (x * 3 + y * 5 + ti) % 7 == 0:
                    k = min(5, k + 1)                                # lístky
                s.px(x, y, BW_LEAF[k])
        # stín pod patrem na nižším patře
        if ti > 0:
            for x in range(int(cx - rx), int(cx + rx) + 1):
                yb = int(cy + ry * math.sqrt(max(0, 1 - ((x - cx) / rx) ** 2))) + 1
                if 0 <= yb < h and s.a[yb, x, 3]:
                    s.px(x, yb, BW_LEAF[0]); s.px(x, yb + 1, BW_LEAF[1])
    return outline(s, c(0x0A1416))


def shade_building(s, roof_rows=None):
    """Víc prostoru: pravá třetina stěn a střechy tmavší, levý okraj lehce světlejší."""
    a = s.a
    h, w = a.shape[:2]
    for x in range(w):
        t = x / (w - 1)
        if t > 0.66:
            f = 1 - (t - 0.66) * 0.55
            m = a[:, x, 3] > 0
            a[m, x, :3] = (a[m, x, :3] * f).astype(np.uint8)
        elif t < 0.12:
            m = a[:, x, 3] > 0
            a[m, x, :3] = np.minimum(255, a[m, x, :3] * 1.06).astype(np.uint8)
    return s
