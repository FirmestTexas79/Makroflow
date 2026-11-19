"""
Měřírna – 2. patro Věže Řádu Váhy. Studená úřední místnost, kde se váží spisy: na velkých mosazných vahách
leží na miskách složky (Řád neváží těla, váží záznamy – kolik toho z člověka zbylo na papíře).
Kachlíková stěna, linoleum, řady prázdných očíslovaných židlí, měřicí kabiny se závěsem, stojan se závažími,
stanice potrubní pošty, metronom na stole úředníka, který tu „právě není“.
Modely ve 3/4 (horní plocha + čelo, pravý bok ve stínu), objemově stínované, obrys selout.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402
from figures import (sphere, cylinder, selout, _pick, _grain, BRASS, WALNUT, PAPER, INK, STEEL, RED, GOLD,  # noqa: E402
                     NAVY, IRON_, GREEN_, shade_px, CABI)

TILE = [c(0x5C6A66), c(0x8A9A94), c(0xA8B8B0), c(0xC4D2CA), c(0xDCE6E0)]        # zelenošedé kachle
LINO = [c(0x2E3634), c(0x46504C), c(0x5E6A66), c(0x7A8682), c(0x98A49E)]         # linoleum
MARB = [c(0x4E5464), c(0x7A8292), c(0xA6ADBB), c(0xCCD1DB), c(0xE6E9EF), c(0xF7F8FA)]
CURT = [c(0x2A1416), c(0x4A2024), c(0x6E2E32), c(0x944044), c(0xB4585A)]         # vínový závěs
GLASS = [c(0x3A4A52), c(0x5E747E), c(0x8AA2AC), c(0xB8CCD4), c(0xE2EEF2)]
OAK = [c(0x3A2414), c(0x5C3A20), c(0x825630), c(0xA87444), c(0xCC9A60)]
FOLDER = [c(0x2A3456), c(0x3C4A78), c(0x5468A0)]


def bands(s, x0, x1, y0, y1, ramp):
    """Svislý válec bez ditheru: každý sloupec má jeden tón podle normály (světlo zleva) – čistý kov."""
    w = x1 - x0 + 1
    for x in range(x0, x1 + 1):
        nx = (x + .5 - x0) / w * 2 - 1
        v = 0.25 + 0.75 * max(0.0, -nx * 0.55 + math.sqrt(max(0.0, 1 - nx * nx)) * 0.6)
        if x == x0 + max(1, w // 4):
            v = 1.0                                                                 # odlesk
        col = ramp[min(len(ramp) - 1, int(v * (len(ramp) - 1) + 0.5))]
        for y in range(y0, y1 + 1):
            s.px(x, y, col)


def tiled_wall(W, H):
    """Kachlíková stěna 8 × 6 se spárami, spodní pás tmavších kachlí, nahoře římsa z kachlí se žlábkem."""
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            lx, ly = x % 8, (y - 6) % 6
            low = y > H - 22
            base = TILE[2] if not low else TILE[1]
            col = base
            if y < 6:
                col = TILE[4] if y == 0 else TILE[3] if y < 3 else TILE[1] if y == 5 else TILE[2]
            elif lx == 7 or ly == 5:
                col = TILE[0] if low else TILE[1]
            elif lx == 0 or ly == 0:
                col = TILE[3] if not low else TILE[2]
            elif (x * 7 + y * 3) % 23 == 0:
                col = TILE[3]                                                       # odlesk glazury
            if y == H - 22:
                col = TILE[4]
            s.px(x, y, col)
    return s


def linoleum(img, x0, y0, x1, y1):
    """Linoleum ve čtvercích 12 px (dva tóny), ošoupané stopy v ose místnosti, škrábance."""
    rng = np.random.default_rng(3)
    for y in range(y0, y1):
        for x in range(x0, x1):
            tx, ty = (x - x0) // 12, (y - y0) // 12
            lx, ly = (x - x0) % 12, (y - y0) % 12
            col = LINO[3] if (tx + ty) % 2 == 0 else LINO[2]
            if lx == 11 or ly == 11:
                col = LINO[1]
            elif lx == 0 or ly == 0:
                col = LINO[4] if (tx + ty) % 2 == 0 else LINO[3]
            img[y, x] = col
    for _ in range(60):                                                             # škrábance
        x, y = int(rng.integers(x0, x1 - 4)), int(rng.integers(y0, y1 - 2))
        for i in range(int(rng.integers(2, 5))):
            img[y + (i // 3), x + i] = LINO[1]


def balance_scale():
    """Velké rovnoramenné váhy: mramorový podstavec ve 3/4, mosazný sloup s hlavicí, vychýlené rameno,
    misky na řetízcích – na levé leží jedna tenká složka, na pravé štos (pravá níž). Ukazatel nad osou."""
    W, H = 62, 64
    s = Spr(W, H)
    cx = W // 2
    for y in range(H - 14, H):                                                      # podstavec: horní plocha + čelo
        for x in range(cx - 14, cx + 15):
            if y < H - 10:
                s.px(x, y, MARB[4] if y == H - 14 else MARB[3])
            else:
                s.px(x, y, MARB[2] if x < cx + 12 else MARB[1])
        for x in range(cx + 15, cx + 18):
            if y - (H - 14) >= x - cx - 15:
                s.px(x, y, MARB[0])
    s.hline(cx - 14, cx + 14, H - 10, MARB[5])
    for y in range(H - 6, H - 3):
        s.hline(cx - 12, cx + 11, y, MARB[1])
    bands(s, cx - 2, cx + 2, 14, H - 14, BRASS)                                       # sloup
    for y in (H - 16, H - 15, 20, 21):
        s.hline(cx - 3, cx + 3, y, BRASS[4] if y in (20, H - 16) else BRASS[1])
    sphere(s, cx + .5, 12, 3.5, 3.5, BRASS, amb=0.35)                              # hlavice (osa)
    tilt = 0.12                                                                     # rameno: pravá strana níž
    for i in range(-24, 25):
        x = cx + i
        y = 12 + round(i * tilt)
        s.px(x, y - 1, BRASS[4] if i < 0 else BRASS[3]); s.px(x, y, BRASS[2]); s.px(x, y + 1, BRASS[0])
    s.vline(cx, 2, 9, BRASS[3]); s.px(cx + 1, 3, BRASS[1])                          # jazýček (vychýlený)
    s.px(cx + 1, 2, RED[3])
    for side, n in ((-1, 1), (1, 4)):
        ex = cx + side * 23
        ey = 12 + round(side * 23 * tilt)
        py = ey + 18
        for k in range(3):                                                          # řetízky
            for yy in range(ey + 1, py):
                if (yy + k) % 2 == 0:
                    s.px(ex - 6 + k * 6, yy, STEEL[3] if k < 2 else STEEL[2])
        for x in range(ex - 9, ex + 10):                                            # miska (elipsa ve 3/4)
            d = abs(x - ex) / 9.5
            depth = round(3 * math.sqrt(max(0.0, 1 - d * d)))
            s.px(x, py, BRASS[4] if x < ex else BRASS[3])
            for yy in range(1, depth + 1):
                s.px(x, py + yy, BRASS[2] if yy < depth else BRASS[0])
        for j in range(n):                                                          # složky na misce
            yy = py - 1 - j * 2
            for x in range(ex - 6 + (j % 2), ex + 6 + (j % 2)):
                s.px(x, yy, FOLDER[2] if j % 2 else PAPER[3]); s.px(x, yy - 1, FOLDER[1] if j % 2 else PAPER[2])
        if n == 1:
            s.px(ex + 3, py - 2, RED[3]); s.px(ex + 4, py - 2, RED[2])               # razítko na tenké složce
    return selout(s, 0.5, 0.82)


def chair(seed=0):
    """Úřední židle zepředu ve 3/4: opěradlo (rámeček s výplní), sedák s hloubkou, čtyři nohy, číslo na opěradle."""
    s = Spr(14, 22)
    for y in range(0, 9):                                                           # opěradlo
        for x in range(2, 12):
            e = min(x - 2, 11 - x, y)
            s.px(x, y, OAK[4] if (e == 0 and x < 7) else OAK[2] if e == 0 else OAK[3])
    for x in range(5, 9):
        s.px(x, 4, PAPER[3])                                                        # štítek s číslem
    s.px(7, 4, INK)
    for y in (9, 10):
        s.px(3, y, OAK[2]); s.px(10, y, OAK[1])
    for y in range(11, 15):                                                         # sedák shora
        for x in range(1, 13):
            s.px(x, y, OAK[4] if y < 13 else OAK[3])
    s.hline(1, 12, 15, OAK[2]); s.hline(1, 12, 16, OAK[1])
    for lx in (1, 11):
        for y in range(17, 22):
            s.px(lx, y, OAK[2]); s.px(lx + 1, y, OAK[0])
    for lx in (3, 9):
        for y in range(17, 20):
            s.px(lx, y, OAK[1])
    return selout(s, 0.5, 0.82)


def booth(n=1):
    """Měřicí kabina: ocelový rám, matné sklo nahoře, vínový závěs dole (zatažený, s řasením), číslo nad vchodem,
    nahoře červená kontrolka „OBSAZENO“ (zhasnutá)."""
    W, H = 32, 54
    s = Spr(W + 3, H)
    for y in range(0, 5):                                                           # stříška
        for x in range(W):
            s.px(x, y, STEEL[4] if y == 0 else STEEL[3] if y < 3 else STEEL[1])
    for x in range(W, W + 3):                                                       # bok
        for y in range(H):
            if y >= x - W and y < H - 1:
                s.px(x, y, STEEL[1] if x == W else STEEL[0])
    for y in range(5, H):
        for x in (0, 1, W - 2, W - 1):
            s.px(x, y, STEEL[4] if x == 0 else STEEL[3] if x == 1 else STEEL[2] if x == W - 2 else STEEL[1])
    for y in range(5, 22):                                                          # matné sklo
        for x in range(2, W - 2):
            s.px(x, y, GLASS[3] if y < 12 else GLASS[2] if x > W // 2 or y > 17 else GLASS[3])
        if y < 14:
            s.px(4 + (y - 5), y, GLASS[4])                                          # šikmý odlesk
    s.hline(2, W - 3, 22, STEEL[2])
    for y in range(23, H - 2):                                                      # závěs se záhyby
        for x in range(2, W - 2):
            fold = (x - 2) % 6
            s.px(x, y, CURT[[4, 3, 2, 1, 2, 3][fold] - (1 if y > H - 10 and fold in (2, 3, 4) else 0)])
    for x in range(2, W - 2, 3):
        s.px(x, 23, STEEL[4])                                                       # kroužky na tyči
    for x in range(W // 2 - 4, W // 2 + 4):                                         # číslo
        s.px(x, 1, PAPER[3]); s.px(x, 2, PAPER[3]); s.px(x, 3, PAPER[1])
    for i in range(n):
        s.px(W // 2 - 2 + i * 2, 2, INK)
    sphere(s, W - 5, 2.5, 1.6, 1.6, [c(0x3A0A0A), c(0x5A1414), c(0x7A2020)], amb=0.4)   # kontrolka (zhasnutá)
    for x in range(0, W):
        s.px(x, H - 2, STEEL[2]); s.px(x, H - 1, STEEL[0])
    return selout(s, 0.5, 0.82)


def weights_rack():
    """Stojan se závažími: dubová police ve 3/4, na ní řada mosazných závaží od největšího po nejmenší (válce
    s knoflíkem), jedno místo prázdné (otisk v sametu)."""
    W, H = 40, 30
    s = Spr(W + 3, H)
    for y in range(10, 16):
        for x in range(W):
            s.px(x, y, OAK[4] if y == 10 else OAK[3] if y < 14 else OAK[2])
    for y in range(11, 14):                                                         # sametová výstelka
        for x in range(2, W - 2):
            s.px(x, y, NAVY[2] if y == 11 else NAVY[1])
    for x in range(W, W + 3):
        for y in range(10, H):
            if y - 10 >= x - W:
                s.px(x, y, OAK[0])
    for y in range(16, H):
        for x in range(W):
            s.px(x, y, OAK[2] if 16 < y < H - 2 else OAK[1])
        s.px(0, y, OAK[3])
    for x in range(3, W - 3, 9):
        s.px(x, 20, BRASS[3]); s.px(x + 1, 20, BRASS[1])                            # štítky gramáže
    x = 3
    for i, (r, h) in enumerate(((4.0, 10), (3.3, 8), (2.8, 7), (0, 6), (2.1, 5), (1.5, 4))):
        if r == 0:
            sphere(s, x + 2.5, 12, 2.6, 1.0, [NAVY[0], NAVY[0], NAVY[1]], amb=0.3)   # prázdné místo
            x += 7
            continue
        x0, x1 = round(x), round(x + r * 2)
        bands(s, x0, x1, 12 - h, 12, BRASS)
        for xx in range(x0, x1 + 1):
            s.px(xx, 12 - h, BRASS[4] if xx < (x0 + x1) / 2 else BRASS[3])
        sphere(s, (x0 + x1) / 2 + .5, 12 - h - 1, max(1.0, r * 0.35), 1.0, BRASS, amb=0.4)
        x += r * 2 + 2.5
    return selout(s, 0.5, 0.82)


def clerk_desk():
    """Stůl úředníka (úředník tu „právě není“): kovový stůl ve 3/4, psací stroj, štos formulářů, razítko,
    metronom (rameno kreslí InteriorFxView), prázdná židle za stolem."""
    W, H = 52, 40
    s = Spr(W + 3, H)
    for y in range(0, 10):                                                          # židle za stolem
        for x in range(W // 2 - 6, W // 2 + 6):
            e = min(x - (W // 2 - 6), W // 2 + 5 - x, y)
            s.px(x, y, OAK[3] if e > 0 else OAK[1])
    for y in range(10, 22):                                                         # deska (šedozelený plech)
        for x in range(W):
            s.px(x, y, TILE[2] if y < 13 else TILE[1])
    s.hline(0, W - 1, 10, TILE[4])
    for y in range(22, 26):
        s.hline(0, W - 1, y, TILE[3] if y == 22 else TILE[0])
    for x in range(W, W + 3):
        for y in range(10, H):
            if y - 10 >= x - W:
                s.px(x, y, CABI[0])
    for (lx, rx) in ((1, 14), (W - 15, W - 2)):                                     # boky se šuplíky
        for y in range(26, H):
            for x in range(lx, rx):
                s.px(x, y, CABI[3] if x > lx else CABI[4])
        for y in (29, 34):
            s.hline(lx + 2, rx - 2, y, CABI[1]); s.px((lx + rx) // 2, y + 2, BRASS[3])
    for y in range(13, 20):                                                         # psací stroj
        for x in range(8, 22):
            s.px(x, y, IRON_[2] if y > 15 else IRON_[3])
    s.hline(8, 21, 12, IRON_[4])
    for x in range(10, 20):
        s.px(x, 10, PAPER[3]); s.px(x, 11, PAPER[3])                                # papír ve stroji
    for x in range(9, 21, 2):
        s.px(x, 17, PAPER[1])                                                       # klávesy
    for j in range(3):                                                              # formuláře
        s.hline(26, 34, 18 - j, PAPER[3] if j % 2 else PAPER[2])
    s.px(37, 15, WALNUT[3]); s.px(37, 16, WALNUT[2]); s.px(36, 17, RED[2]); s.px(37, 17, RED[2]); s.px(38, 17, RED[2])
    for y in range(9, 19):                                                          # metronom (bez ramene)
        half = (y - 9) // 3 + 1
        for x in range(44 - half, 45 + half):
            s.px(x, y, WALNUT[4] if x < 44 else WALNUT[2])
    s.px(44, 9, BRASS[3])
    return selout(s, 0.5, 0.82)


def tube_station():
    """Stanice potrubní pošty: tři mosazné trubky shora ústí do skříňky se dvířky a kontrolkami."""
    W, H = 26, 40
    s = Spr(W, H)
    for k, x in enumerate((5, 11, 17)):
        for y in range(0, 20):
            s.px(x, y, BRASS[1]); s.px(x + 1, y, BRASS[4]); s.px(x + 2, y, BRASS[2]); s.px(x + 3, y, BRASS[0])
            if y % 7 == 0:
                s.hline(x, x + 3, y, BRASS[3])
    for y in range(20, H):
        for x in range(W):
            e = min(x, W - 1 - x, y - 20, H - 1 - y)
            s.px(x, y, BRASS[2] if e > 1 else BRASS[3] if (x < 3 or y < 23) else BRASS[0])
    for k, x in enumerate((4, 10, 16)):                                             # dvířka
        for y in range(24, 33):
            for xx in range(x, x + 6):
                s.px(xx, y, IRON_[2] if xx > x else IRON_[3])
        s.px(x + 4, 28, BRASS[4])
        s.px(x + 2, 35, [c(0x3A8A58), c(0x7A2020), c(0x7A2020)][k])               # kontrolky
    return selout(s, 0.5, 0.82)


def wall_chart():
    """Graf na zdi: milimetrový papír v rámu, klesající křivka „SPÁNEK OKRUHŮ“ a červená vodorovná čára normy."""
    W, H = 30, 36
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            e = min(x, y, W - 1 - x, H - 1 - y)
            if e < 2:
                s.px(x, y, OAK[3] if (x < 2 or y < 2) and e == 0 else OAK[2] if e == 0 else OAK[1])
            else:
                s.px(x, y, PAPER[3] if (x % 4 and y % 4) else PAPER[1])
    s.hline(4, W - 5, 6, INK)
    for x in range(4, 14):
        s.px(x, 4, INK if x % 2 else PAPER[3])
    norm = 14
    s.hline(3, W - 4, norm, RED[3])
    prev = None
    for i, x in enumerate(range(4, W - 4)):
        y = int(10 + i * 0.75 + 2 * math.sin(i * 0.9))                             # klesající křivka
        if prev is not None:
            s.line(x - 1, prev, x, y, NAVY[2])
        prev = y
    return s


def hourglass():
    """Velké přesýpací hodiny na stojanu: mosazné desky a sloupky, dvě skleněné baňky (odlesk), písek skoro
    celý dole – čas „měření“ téměř vypršel. Dubový podstavec ve 3/4."""
    W, H = 22, 40
    s = Spr(W + 2, H)
    cx = W // 2
    for y in range(H - 6, H):                                                       # podstavec
        for x in range(1, W - 1):
            s.px(x, y, OAK[4] if y == H - 6 else OAK[3] if y < H - 3 else OAK[1])
        for x in (W - 1, W):
            if y - (H - 6) >= x - (W - 1):
                s.px(x, y, OAK[0])
    for yy in (2, H - 9):                                                           # mosazné desky
        for x in range(2, W - 2):
            s.px(x, yy, BRASS[4] if x < cx else BRASS[3]); s.px(x, yy + 1, BRASS[2]); s.px(x, yy + 2, BRASS[0])
    for x in (3, W - 4):                                                            # sloupky
        for y in range(5, H - 9):
            s.px(x, y, BRASS[3] if x == 3 else BRASS[1])
    top, bot, mid = 5, H - 10, (5 + H - 10) // 2
    for y in range(top, bot + 1):                                                   # baňky
        t = abs(y - mid) / (mid - top)
        hw = round(1 + 5 * math.sin(min(1.0, t) * math.pi / 2) ** 1.2)
        for x in range(cx - hw, cx + hw + 1):
            col = GLASS[2] if x < cx else GLASS[1]
            if y > mid + (bot - mid) * 0.35 and y < bot:                           # písek dole
                col = c(0xD8B878) if x < cx else c(0xB09058)
            elif y < mid and y >= mid - 2 and abs(x - cx) < 1:
                col = c(0xD8B878)                                                   # zbytek nahoře
            s.px(x, y, col)
        s.px(cx - hw, y, GLASS[3])
    for y in range(mid, mid + 6):
        s.px(cx, y, c(0xE8CC88))                                                    # padající pramínek
    for y in range(top + 2, top + 6):
        s.px(cx - 3, y, GLASS[4])                                                   # odlesk
    return selout(s, 0.5, 0.82)
