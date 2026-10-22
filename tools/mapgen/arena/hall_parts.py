"""
Díly vstupní haly Arény v Ozvěně (Akt II): leštěná dlažba, zadní stěna (kámen nad dřevěným obložením)
s velkou branou do arény, prapory bojového cechu, nástěnné pochodně, Síň šampionů (tři zlacené rámy, poháry,
sametový provaz), pult velitele cechu a velitel sám, nástěnka s plakátem HLEDÁ SE (Drakirra),
tréninkové figuríny a stojan se zbraněmi, lavice a květiny. Vše po prvcích s vlastním obrysem a stínem.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain")); sys.path.insert(0, os.path.join(HERE, "..", "orchard"))
sys.path.insert(0, os.path.join(HERE, "..", "city"))
from pix import Spr, c, outline, blobs  # noqa: E402
from pixart import K, STN, WD, stones, box_outline, from_ascii  # noqa: E402

GOLD = [c(0x6A4A12), c(0x9C7424), c(0xD0A440), c(0xF0D070), c(0xFFF4B8)]
RED = [c(0x3E0E10), c(0x6A1A1C), c(0x9A2A26), c(0xC4423A), c(0xE06A58)]
STEEL = [c(0x2A2E38), c(0x464C5A), c(0x6A7284), c(0x9AA2B4), c(0xD0D6E2)]
WALLST = [c(0x3A3236), c(0x584C4E), c(0x766668), c(0x928080), c(0xB4A09A)]
OAK = [c(0x2E1A0E), c(0x4A2C18), c(0x6E4426), c(0x92603A), c(0xB88252), c(0xD8A676)]
SKIN = [c(0x8A5A3A), c(0xC88A62), c(0xE8B48A)]
PARCH = [c(0x8A7448), c(0xBCA470), c(0xE2CE98), c(0xF4E6BC)]


def floor(img, x0, y0, x1, y1):
    """Leštěná dlažba: velké desky ve dvou tónech, světlá hrana nahoře vlevo, spára, občas odlesk."""
    A, B = [c(0x9C8462), c(0xB89E78), c(0xCCB48C), c(0xE0CCA6)], [c(0x8A7252), c(0xA68C66), c(0xBCA27A), c(0xD2BC94)]
    for y in range(y0, y1):
        for x in range(x0, x1):
            tx, ty = (x - x0) // 16, (y - y0) // 16
            P = A if (tx + ty) % 2 == 0 else B
            lx, ly = (x - x0) % 16, (y - y0) % 16
            col = P[2]
            if lx == 15 or ly == 15:
                col = P[0]
            elif lx == 0 or ly == 0:
                col = P[3]
            elif (lx + ly) in (5, 6) and lx < 9:
                col = P[3]                                               # odlesk
            img[y, x] = col


def carpet(img, x0, x1, y0, y1):
    for y in range(y0, y1):
        for x in range(x0, x1):
            e = min(x - x0, x1 - 1 - x)
            if e == 0:
                col = RED[0]
            elif e in (1, 2):
                col = GOLD[3] if e == 1 else GOLD[1]
            else:
                col = RED[2] if ((y // 4) + (x // 4)) % 2 else RED[3]
                if (x - x0 - 3) % 8 == 4 and (y % 8) == 4:
                    col = GOLD[2]                                        # vzor
            img[y, x] = col


def back_wall(W, H=76):
    """Zadní stěna W × H: římsa, kamenné kvádry, dřevěné obložení s panely, sokl."""
    s = Spr(W, H)
    for y in range(0, 6):
        for x in range(W):
            s.px(x, y, WALLST[3] if y == 1 else WALLST[1] if y > 3 else WALLST[2])
    for y in range(6, 44):
        row = (y - 6) // 6
        for x in range(W):
            bx = (x + row % 2 * 7) % 14
            by = (y - 6) % 6
            k = 2 + ((x + row % 2 * 7) // 14 * 7 + row * 3) % 3 // 2
            col = WALLST[1] if by == 5 or bx == 13 else WALLST[min(4, k + 1)] if by == 0 or bx == 0 else WALLST[k]
            s.px(x, y, col)
    for y in range(44, 68):
        for x in range(W):
            px = x % 22
            if y in (44, 45):
                col = OAK[4] if y == 44 else OAK[2]                   # horní lišta
            elif px in (0, 21):
                col = OAK[1]
            elif px in (2, 19) or y in (48, 64):
                col = OAK[2] if not (px in (2,) or y == 48) else OAK[4]
            else:
                col = OAK[3] if y < 56 else OAK[2]
            s.px(x, y, col)
    for y in range(68, H):
        for x in range(W):
            s.px(x, y, OAK[1] if y < 71 else OAK[0])
    return s


def arena_door():
    """Brána do arény 56 × 72: zlatý oblouk se svorníkem a znakem, kamenná ostění, těžká křídla s kováním,
    pod dveřmi prosvítá světlo z písku arény, dva schody."""
    W, H = 56, 72
    s = Spr(W, H)
    cx = W / 2 - 0.5
    for y in range(H - 8):
        for x in range(W):
            # ostění (kámen) – oblouk
            r = math.hypot(x - cx, (y - 24) * 1.0)
            inside_outer = (y >= 24 and 2 <= x <= W - 3) or (y < 24 and r <= 26)
            inside_inner = (y >= 24 and 8 <= x <= W - 9) or (y < 24 and r <= 20)
            if inside_outer and not inside_inner:
                band = (r if y < 24 else 26 - min(x - 2, W - 3 - x))
                ang = math.degrees(math.atan2(24 - y, x - cx)) if y < 24 else 0
                joint = y < 24 and abs((ang + 7.5) % 15 - 7.5) < 1.5
                col = GOLD[2] if (y < 24 and 20 < r < 22) else WALLST[4] if x < cx else WALLST[3]
                if joint:
                    col = WALLST[1]
                if y >= 24 and (y - 24) % 8 == 0:
                    col = WALLST[1]
                s.px(x, y, col)
            elif inside_inner:
                # křídla dveří
                left = x < cx
                col = OAK[3] if (x % 4) else OAK[2]
                if abs(x - cx) < 1:
                    col = OAK[0]
                if y in (30, 46, 58):
                    col = STEEL[2] if (x % 2) else STEEL[1]            # kované pásy
                if y in (31, 47, 59):
                    col = STEEL[0]
                if (y in (30, 46, 58)) and x % 4 == 1:
                    col = STEEL[4]                                     # nýty
                s.px(x, y, col)
    # kruhy klepadel
    for kx in (int(cx) - 4, int(cx) + 5):
        s.ellipse(kx, 40, 2.2, 2.2, GOLD[2]); s.px(kx, 40, OAK[2])
    # svorník se znakem: zkřížené meče ve vavřínu
    s.rect(cx - 5, 0, cx + 6, 9, GOLD[3]); s.rect(cx - 4, 1, cx + 5, 8, GOLD[2])
    s.line(cx - 3, 7, cx + 4, 2, STEEL[4]); s.line(cx - 3, 2, cx + 4, 7, STEEL[4])
    s.px(cx - 4, 8, GOLD[0]); s.px(cx + 5, 8, GOLD[0])
    # světlo pod dveřmi a schody
    s.hline(9, W - 10, H - 9, c(0xFFE6A0))
    for i in range(2):
        y = H - 8 + i * 4
        s.rect(4 - i * 2, y, W - 5 + i * 2, y + 3, STN[3]); s.hline(4 - i * 2, W - 5 + i * 2, y, STN[4])
    return outline(s, K)


def banner(emblem=True):
    """Prapor cechu 16 × 34: tyč, rudé sukno se zlatým lemem a zkříženými meči, vlaštovčí konec."""
    s = Spr(16, 34)
    s.rect(0, 0, 15, 1, OAK[2]); s.px(0, 0, GOLD[3]); s.px(15, 0, GOLD[3])
    for y in range(2, 34):
        for x in range(1, 15):
            tail = y > 28 and abs(x - 7.5) < (y - 28) * 1.3
            if tail:
                continue
            col = RED[3] if x < 6 else RED[2] if x < 11 else RED[1]
            if x in (1, 14):
                col = GOLD[2]
            if (y + x) % 9 == 0 and 2 < x < 13:
                col = RED[2] if x < 6 else col                      # záhyby
            s.px(x, y, col)
    if emblem:
        s.line(4, 18, 11, 9, STEEL[4]); s.line(4, 9, 11, 18, STEEL[3])
        s.rect(6, 15, 9, 16, GOLD[3])
        s.px(7, 7, GOLD[3]); s.px(8, 7, GOLD[3])
    return outline(s, K)


def torch():
    """Nástěnná pochodeň 10 × 20: železný držák, plamen se světlým jádrem (kreslí se až po obrysu)."""
    s = Spr(10, 20)
    s.rect(3, 10, 6, 12, STEEL[1]); s.rect(4, 12, 5, 19, STEEL[1]); s.hline(2, 7, 10, STEEL[3])
    outline(s, K)
    flame = ["...o....", "..oyo...", "..oyyo..", ".oyYyo..", ".oyYYyo.", ".ooyYyo.", "..ooyo..", "...oo..."]
    pal = {"o": c(0xE8682E), "y": c(0xFFB040), "Y": c(0xFFF2C0)}
    for y, row in enumerate(flame):
        for x, ch in enumerate(row):
            if ch != ".":
                s.px(1 + x, 2 + y, pal[ch])
    return s


def hall_of_fame():
    """Síň šampionů 80 × 60: tmavý dřevěný panel, zlatý štítek, tři zlacené rámy (1., 2., 3. místo)
    s pozadím portrétu, pod nimi poháry na polici. Rámy jsou na [FRAMES] (relativně ke spritu)."""
    W, H = 80, 60
    s = Spr(W, H)
    for y in range(4, H):
        for x in range(W):
            col = OAK[2] if (x // 10) % 2 else OAK[1]
            if y in (4, 5):
                col = OAK[4] if y == 4 else OAK[3]
            s.px(x, y, col)
    for x in range(W):                                               # stříška
        s.px(x, 0, OAK[5]); s.px(x, 1, OAK[4]); s.px(x, 2, OAK[3]); s.px(x, 3, OAK[0])
    s.rect(26, 7, 53, 12, GOLD[2]); s.hline(26, 53, 7, GOLD[4]); box_outline(s, 26, 7, 53, 12, GOLD[0])
    for x in range(29, 51, 2):
        s.px(x, 9 + (x % 3 == 0), GOLD[0])                           # „SÍŇ ŠAMPIONŮ“ (drobné písmo)
    ranks = [(GOLD, 31, 15, 1), (STEEL, 6, 19, 2), ([c(0x5A2E14), c(0x8A4A22), c(0xB8723A), c(0xD89A5A), c(0xF0C08A)], 56, 19, 3)]
    for (P, fx, fy, n) in ranks:
        fw, fh = 18, 22
        s.rect(fx, fy, fx + fw - 1, fy + fh - 1, P[2])
        s.hline(fx, fx + fw - 1, fy, P[4]); s.vline(fx, fy, fy + fh - 1, P[3]); s.hline(fx, fx + fw - 1, fy + fh - 1, P[0]); s.vline(fx + fw - 1, fy, fy + fh - 1, P[1])
        for y in range(fy + 2, fy + fh - 2):                           # pozadí portrétu (nebe → tráva)
            for x in range(fx + 2, fx + fw - 2):
                t = (y - fy) / fh
                s.px(x, y, c(0x6A9AC8) if t < 0.55 else c(0x5A8A4A) if t < 0.62 else c(0x4A7A3A))
        box_outline(s, fx + 1, fy + 1, fx + fw - 2, fy + fh - 2, P[0])
        # cedulka s číslem místa
        s.rect(fx + 5, fy + fh + 1, fx + 12, fy + fh + 4, P[2]); s.hline(fx + 5, fx + 12, fy + fh + 1, P[4])
        for k in range(n):
            s.vline(fx + 7 + k * 2, fy + fh + 2, fy + fh + 3, P[0])
    # police s poháry
    s.rect(2, 50, W - 3, 52, OAK[4]); s.hline(2, W - 3, 53, OAK[0])
    for (P, cx) in ((STEEL, 15), (GOLD, 40), ([c(0x5A2E14), c(0x8A4A22), c(0xB8723A), c(0xD89A5A), c(0xF0C08A)], 65)):
        s.rect(cx - 3, 42, cx + 3, 46, P[2]); s.hline(cx - 3, cx + 3, 42, P[4]); s.px(cx - 4, 43, P[2]); s.px(cx + 4, 43, P[2])
        s.vline(cx, 46, 48, P[1]); s.rect(cx - 2, 48, cx + 2, 49, P[3])
    return outline(s, K)


FRAMES = [(31, 15), (6, 19), (56, 19)]           # levé horní rohy rámů (1., 2., 3.) v hall_of_fame, 18 × 22


def rope_barrier(w):
    s = Spr(w, 14)
    posts = list(range(1, w - 2, (w - 4) // 2))
    for px0 in posts:
        s.rect(px0, 3, px0 + 1, 13, GOLD[2]); s.vline(px0, 3, 13, GOLD[3]); s.ellipse(px0 + 0.5, 2, 1.6, 1.6, GOLD[3])
        s.hline(px0 - 1, px0 + 2, 13, GOLD[1])
    for i in range(len(posts) - 1):
        a, b = posts[i] + 1, posts[i + 1]
        for x in range(a, b + 1):
            y = 5 + int(3 * math.sin(math.pi * (x - a) / max(1, b - a)))
            s.px(x, y, RED[3]); s.px(x, y + 1, RED[1])
    return outline(s, K)


def desk():
    """Pult velitele 64 × 32: dřevěná deska se světlou hranou, čelo s panely a znakem cechu,
    na desce kniha, kalamář s brkem, zvonek a štos papírů."""
    W, H = 64, 32
    s = Spr(W, H)
    for y in range(0, 10):                                           # deska shora
        for x in range(W):
            s.px(x, y, OAK[5] if y == 0 else OAK[4] if y < 3 else OAK[3])
    s.hline(0, W - 1, 10, OAK[1])
    for y in range(11, H):                                           # čelo
        for x in range(W):
            px = x % 16
            col = OAK[2] if 2 < px < 14 and 13 < y < H - 3 else OAK[1] if px in (0, 15) else OAK[3]
            if y >= H - 2:
                col = OAK[0]
            s.px(x, y, col)
    s.rect(26, 14, 37, 25, RED[2]); box_outline(s, 26, 14, 37, 25, GOLD[2])
    s.line(28, 23, 35, 16, STEEL[4]); s.line(28, 16, 35, 23, STEEL[3])
    # na desce
    s.rect(6, 2, 19, 7, c(0xF4ECD4)); s.vline(12, 2, 7, c(0x8A7448)); s.rect(6, 2, 19, 2, RED[2])   # otevřená kniha
    for y in (4, 5, 6):
        s.hline(7, 10, y, c(0x6A6458)); s.hline(14, 18, y, c(0x6A6458))
    s.rect(24, 3, 26, 6, c(0x1A1A24)); s.line(25, 3, 28, -1 + 1, c(0xF4F0E8))           # kalamář, brk
    s.ellipse(46, 4, 2.6, 2.2, GOLD[2]); s.px(46, 1, GOLD[3]); s.hline(43, 49, 6, GOLD[1])  # zvonek
    s.rect(52, 2, 60, 7, c(0xE8DEC4)); s.rect(53, 1, 61, 6, c(0xF4ECD8))                 # papíry
    return outline(s, K)


def commander():
    """Velitel bojového cechu 22 × 34: krátké šedivé vlasy a plnovous, jizva, ocelové náramenníky,
    rudý plášť se zlatým lemem, kožený pás. Ve hře je vidět nad pultem (spodek kryje deska)."""
    rows = [
        "........kkkkk.........",
        "......kkhhhhhkk.......",
        ".....khhHhhhhhhk......",
        ".....khHsssssshk......",
        ".....khsssksskhk......",
        ".....kssssssssk.......",
        "......kbbsssbbk.......",
        "......kbbbbbbbk.......",
        ".......kbbbbbk........",
        "...kkkkkkbbbkkkkkk....",
        "..kAAPPPkkkkkPPPAAk...",
        ".kAAPPPPRRRRRPPPPAAk..",
        ".kAPPPPRRGGGRRPPPPAk..",
        ".kAkPPPRRGrGRRPPPkAk..",
        ".kRkPPPRRGGGRRPPPkRk..",
        ".kRkPPPPRRRRRPPPPkRk..",
        ".kRkkPPPPPPPPPPPkkRk..",
        ".kRksskLLLLLLLkksRk...",
        ".kRkssklllglllkssRk...",
        ".kRRkkkLLLLLLLkkRRk...",
        ".kRRRkPPPPPPPPPkRRk...",
        ".kRRRkPPPPPPPPPkRRk...",
        ".kRRRkPPPkkPPPPkRRk...",
        ".kRRRkPPPkkPPPPkRRk...",
        ".kRRRkWWWkkWWWWkRRk...",
        ".kRRRkWWWkkWWWWkRRk...",
        ".kRRRkWWWkkWWWWkRRk...",
        "..kRRkWWWkkWWWWkRk....",
        "..kRRkWWWkkWWWWkRk....",
        "...kkkWWWkkWWWWkk.....",
        ".....kWWWkkWWWWk......",
        ".....kKKKkkKKKKk......",
        ".....kKKKKkKKKKKk.....",
        ".....kkkkkkkkkkkk.....",
    ]
    pal = {"k": K, "h": c(0x8E8A88), "H": c(0xC8C4C0), "s": SKIN[1], "b": c(0x9A9690), "A": STEEL[3], "P": STEEL[2],
           "R": RED[2], "G": GOLD[3], "r": RED[3], "L": OAK[1], "l": OAK[2], "g": GOLD[2], "W": OAK[2], "K": OAK[0]}
    s = from_ascii(rows, pal)
    s.px(10, 4, c(0x2A2A30)); s.px(13, 4, c(0x2A2A30))              # oči
    s.px(8, 5, c(0xA06A4A)); s.px(8, 6, c(0xA06A4A))                  # jizva
    s.px(7, 3, SKIN[2]); s.px(8, 3, SKIN[2])
    return s


def wanted_board(drakirra_px):
    """Nástěnka 52 × 50: dřevěný rám na nožkách, velký plakát HLEDÁ SE s Drakirrou a odměnou, dva menší lístky."""
    W, H = 60, 56
    s = Spr(W, H)
    s.rect(3, 40, 5, 55, OAK[1]); s.rect(54, 40, 56, 55, OAK[1])
    for y in range(0, 42):
        for x in range(W):
            col = OAK[3] if (y in (0, 1) or y in (40, 41) or x in (0, 1, W - 2, W - 1)) else c(0x7A5A3A) if (x + y) % 3 else c(0x6E4E30)
            if y == 0 or x == 0:
                col = OAK[4]
            s.px(x, y, col)
    # plakát
    px0, py0, pw, ph = 5, 3, 34, 36
    for y in range(py0, py0 + ph):
        for x in range(px0, px0 + pw):
            torn = (x == px0 + pw - 1 and y > py0 + ph - 5) or (y == py0 + ph - 1 and x > px0 + pw - 6)
            if torn:
                continue
            col = PARCH[2] if (x * 3 + y) % 11 else PARCH[1]
            s.px(x, y, col)
    s.rect(px0 + 2, py0 + 2, px0 + pw - 3, py0 + 5, RED[2])                        # HLEDÁ SE
    for x in range(px0 + 4, px0 + pw - 4, 2):
        s.vline(x, py0 + 3, py0 + 4, PARCH[3])
    dh, dw = drakirra_px.shape[:2]
    ox, oy = px0 + (pw - dw) // 2, py0 + 7
    s.rect(ox - 1, oy - 1, ox + dw, oy + dh, PARCH[1])
    for y in range(dh):
        for x in range(dw):
            if drakirra_px[y, x, 3] > 128:
                r_, g_, b_ = drakirra_px[y, x, :3].astype(int)
                # tisk: barvy dračice ztlumené do papíru, ať je poznat a přitom vypadá jako plakát
                s.px(ox + x, oy + y, (int(r_ * 0.8 + 40), int(g_ * 0.62 + 30), int(b_ * 0.5 + 18), 255))
    s.rect(px0 + 3, py0 + ph - 6, px0 + pw - 4, py0 + ph - 5, c(0x5A4A30))       # odměna
    s.ellipse(px0 + 6, py0 + ph - 3, 1.6, 1.6, GOLD[3]); s.hline(px0 + 9, px0 + 20, py0 + ph - 3, c(0x5A4A30))
    s.px(px0 + 1, py0 + 1, STEEL[3]); s.px(px0 + pw - 2, py0 + 1, STEEL[3])          # hřebíky
    # menší lístky
    for (lx, ly, lw, lh) in ((43, 5, 12, 13), (44, 22, 11, 12)):
        s.rect(lx, ly, lx + lw, ly + lh, c(0xF0E8D0))
        for k in range(ly + 3, ly + lh - 1, 2):
            s.hline(lx + 2, lx + lw - 2, k, c(0x8A8274))
        s.px(lx + lw // 2, ly, RED[3])
    return outline(s, K)


def dummy():
    """Tréninková figurína 20 × 32: kůl s příčným ramenem, pytel se slámou (hlava a trup), terč, provaz."""
    s = Spr(20, 32)
    s.rect(9, 12, 10, 31, OAK[2]); s.vline(9, 12, 31, OAK[3]); s.hline(6, 13, 31, OAK[1])
    s.rect(1, 13, 18, 14, OAK[3]); s.hline(1, 18, 13, OAK[4])                       # rameno
    s.ellipse(9.5, 5, 4.2, 4.2, c(0xC8A858)); s.px(8, 4, c(0x6A5030)); s.px(11, 4, c(0x6A5030))
    for k in range(-4, 5):
        s.px(9 + k, 1 + abs(k) // 2, c(0xE0C470))                                    # sláma nahoře
    for y in range(10, 26):
        half = 5 if y < 22 else 5 - (y - 21)
        for x in range(10 - half, 10 + half):
            s.px(x, y, c(0xD8B860) if (x * 2 + y) % 5 else c(0xB0904A))
    s.hline(4, 15, 15, c(0x8A6A3A)); s.hline(5, 14, 21, c(0x8A6A3A))                 # provazy
    s.ellipse(9.5, 18, 3.2, 3.2, RED[3]); s.ellipse(9.5, 18, 2, 2, c(0xF4ECD8)); s.px(9, 18, RED[2]); s.px(10, 18, RED[2])
    return outline(s, K)


def armor_statue():
    """Rytířská zbroj na podstavci 20 × 40 (u vchodu jako sochy v pokémonních tělocvičnách)."""
    s = Spr(20, 40)
    stones(s, 2, 17, 30, 39, h=5, w=(4, 6), seed=3)
    s.rect(1, 29, 18, 30, STN[4])
    s.ellipse(9.5, 5, 4, 4.5, STEEL[3]); s.hline(6, 13, 5, STEEL[0]); s.vline(9, 1, 3, RED[3]); s.vline(10, 0, 3, RED[3])
    for y in range(10, 29):
        half = 6 if y < 14 else 5 if y < 22 else 4
        for x in range(10 - half, 10 + half):
            s.px(x, y, STEEL[4] if x < 8 else STEEL[3] if x < 11 else STEEL[2])
    s.rect(2, 10, 5, 13, STEEL[3]); s.rect(14, 10, 17, 13, STEEL[2])                 # náramenníky
    s.hline(5, 14, 20, STEEL[1]); s.rect(8, 19, 11, 21, GOLD[2])                       # pás
    s.vline(17, 4, 28, STEEL[4]); s.vline(18, 4, 28, STEEL[2]); s.hline(15, 20, 14, GOLD[2])   # meč
    return outline(s, K)


def floor_emblem(r=14):
    """Mozaika cechu v podlaze: zlatý kruh, rudé pole, zkřížené meče."""
    s = Spr(2 * r + 1, int(1.4 * r) + 1)
    for y in range(s.h):
        for x in range(s.w):
            d = math.hypot((x - r) / r, (y - s.h / 2) / (s.h / 2))
            if d <= 1:
                s.px(x, y, GOLD[3] if d > 0.86 else GOLD[1] if d > 0.78 else RED[2] if (x + y) % 2 else RED[3])
    s.line(r - 7, s.h // 2 + 4, r + 7, s.h // 2 - 4, STEEL[4]); s.line(r - 7, s.h // 2 - 4, r + 7, s.h // 2 + 4, STEEL[3])
    return s


def guild_flag():
    s = Spr(14, 44)
    s.rect(1, 2, 2, 43, OAK[2]); s.ellipse(1.5, 1, 1.6, 1.6, GOLD[3]); s.hline(0, 4, 43, OAK[1])
    for y in range(4, 20):
        for x in range(3, 14 - (y - 4) // 4):
            s.px(x, y, RED[3] if y < 12 else RED[2])
    s.line(5, 14, 10, 8, STEEL[4]); s.line(5, 8, 10, 14, STEEL[3])
    return outline(s, K)


def weapon_rack():
    s = Spr(30, 30)
    s.rect(1, 6, 28, 8, OAK[3]); s.hline(1, 28, 6, OAK[4]); s.rect(1, 24, 28, 26, OAK[2])
    s.rect(2, 6, 3, 29, OAK[1]); s.rect(26, 6, 27, 29, OAK[1])
    for i, x in enumerate(range(6, 25, 5)):
        s.vline(x, 1, 25, STEEL[3] if i % 2 else OAK[4]); s.vline(x + 1, 1, 25, STEEL[1] if i % 2 else OAK[2])
        s.hline(x - 1, x + 2, 9, GOLD[2] if i % 2 else OAK[1])
        s.px(x, 0, STEEL[4] if i % 2 else OAK[5])
    return outline(s, K)


def bench_long():
    s = Spr(34, 12)
    s.rect(0, 2, 33, 5, OAK[4]); s.hline(0, 33, 2, OAK[5]); s.hline(0, 33, 5, OAK[1])
    for x in (3, 30):
        s.rect(x - 1, 6, x + 1, 11, OAK[1])
    return outline(s, K)


def plant():
    s = Spr(16, 22)
    blobs(s, [(5, 7, 4.5), (11, 7, 4.5), (8, 4, 4.5)], [c(0x1C4020), c(0x2C6030), c(0x448844), c(0x66B05A)])
    s.rect(4, 12, 11, 20, c(0xA8623A)); s.hline(3, 12, 12, c(0xC8804E)); s.hline(4, 11, 20, c(0x6A3A1E))
    return outline(s, K)
