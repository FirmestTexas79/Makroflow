"""
Pixelové postavy NPC v interiérech (archivář Sept ve Věži, velitel Kassar v hale Arény) – docs/adr/0103.

Místo plochých tvarů se postavy stínují jako objemy: hlava, ramena a paže jsou koule a válce osvětlené shora zleva
(4–5 tónů s ditherem na hranách tónů), vlasy a vousy jsou shluky stínovaných chomáčů, látka má záhyby, kov odlesk.
Obrys je „selout“: okrajové pixely jsou tmavší verzí vlastní barvy (na osvětlené straně méně), ne černá čára.
Postavy jsou poprsí – ve hře je kryje pult, ruce leží na desce.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain"))
from pix import Spr, c  # noqa: E402

LIGHT = np.array([-0.55, -0.72, 0.45]); LIGHT /= np.linalg.norm(LIGHT)
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0


class Canvas(Spr):
    """Sprite s horním okrajem [oy] px: kreslí se v logických souřadnicích, nic nad hlavou se neořízne."""
    def __init__(self, w, h, oy=3):
        super().__init__(w, h + oy); self.oy = oy

    def px(self, x, y, col):
        super().px(x, y + self.oy, col)

    def opaque(self, x, y):
        return super().opaque(x, y + self.oy)


def _pick(ramp, v, x, y):
    """v 0..1 → tón rampy, s ditherem mezi sousedními tóny (pixelový přechod)."""
    f = v * (len(ramp) - 1)
    i = int(f)
    if i >= len(ramp) - 1:
        return ramp[-1]
    return ramp[i + 1] if (f - i) > BAYER[y % 4, x % 4] * 0.8 + 0.1 else ramp[i]


def sphere(s, cx, cy, rx, ry, ramp, amb=0.18, mask=None):
    for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            dx, dy = (x + .5 - cx) / rx, (y + .5 - cy) / ry
            d2 = dx * dx + dy * dy
            if d2 > 1 or (mask is not None and not mask(x, y)):
                continue
            n = np.array([dx, dy, math.sqrt(1 - d2)])
            v = amb + (1 - amb) * max(0.0, float(n @ LIGHT))
            s.px(x, y, _pick(ramp, min(1.0, v), x, y))


def cylinder(s, x0, x1, y0, y1, ramp, amb=0.22, top_lit=True, shape=None):
    """Svislý válec (trup, rukáv): tón podle vodorovné normály, horní hrana mírně světlejší. shape(x, y) → uvnitř?"""
    w = x1 - x0 + 1
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if shape is not None and not shape(x, y):
                continue
            nx = ((x + .5 - x0) / w) * 2 - 1
            n = np.array([nx, -0.25 if top_lit else 0.0, math.sqrt(max(0.0, 1 - nx * nx))])
            v = amb + (1 - amb) * max(0.0, float(n @ LIGHT / np.linalg.norm(n)))
            s.px(x, y, _pick(ramp, min(1.0, v), x, y))


def blobs(s, lst, ramp, amb=0.2):
    """Chomáče (vlasy, vousy): každá koule se stínuje zvlášť, spáry mezi nimi tmavé → objemná textura."""
    owner = -np.ones((s.h, s.w), int)
    for i, (cx, cy, r) in enumerate(lst):
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                dx, dy = (x + .5 - cx) / r, (y + .5 - cy) / r
                d2 = dx * dx + dy * dy
                oy = getattr(s, "oy", 0)
                if d2 > 1 or not (0 <= x < s.w and 0 <= y + oy < s.h):
                    continue
                n = np.array([dx, dy, math.sqrt(1 - d2)])
                v = amb + (1 - amb) * max(0.0, float(n @ LIGHT))
                s.px(x, y, _pick(ramp, min(1.0, v), x, y)); owner[y + oy, x] = i
    oy = getattr(s, "oy", 0)
    for y in range(s.h - 1):
        for x in range(s.w - 1):
            o = owner[y, x]
            if o >= 0 and owner[y + 1, x] >= 0 and owner[y + 1, x] < o:
                s.px(x, y + 1 - oy, ramp[0])


def selout(s, dark=0.42, lit=0.68):
    """Okrajové pixely ztmaví podle vlastní barvy: na stinné straně (dole/vpravo) víc, na osvětlené méně."""
    a = s.a
    op = a[:, :, 3] > 0
    p = np.pad(op, 1)
    up, down, left, right = ~p[:-2, 1:-1], ~p[2:, 1:-1], ~p[1:-1, :-2], ~p[1:-1, 2:]
    edge = op & (up | down | left | right)
    out = a.copy()
    for y, x in zip(*np.nonzero(edge)):
        k = lit if (up[y, x] or left[y, x]) and not (down[y, x] or right[y, x]) else dark
        out[y, x, :3] = (a[y, x, :3] * k).astype(np.uint8)
    s.a[:] = out
    return s


def shade_px(s, x, y, k):
    if s.opaque(x, y):
        yy = y + getattr(s, "oy", 0)
        s.a[yy, x, :3] = np.clip(s.a[yy, x, :3] * k, 0, 255).astype(np.uint8)


# ── palety ─────────────────────────────────────────────────────────────────
SKIN_PALE = [c(0x6E5450), c(0x9C7E74), c(0xC6A898), c(0xE2C8B8), c(0xF4E2D6)]
SKIN_WARM = [c(0x5E3426), c(0x8C5638), c(0xB87C56), c(0xDCA47A), c(0xF2C89E)]
HAIR_GREY = [c(0x34343E), c(0x5A5A66), c(0x8A8A96), c(0xB6B6C2), c(0xE0E0E8)]
NAVY = [c(0x0A0E1A), c(0x141C30), c(0x223052), c(0x34497A), c(0x5874AA)]
WHITE = [c(0x8A8E9C), c(0xB4B8C4), c(0xD8DBE4), c(0xF4F5F8)]
BRASS = [c(0x5A3E12), c(0x8A6424), c(0xBC9238), c(0xE0BC5C), c(0xFFE9A0)]
STEEL = [c(0x22262E), c(0x3E4452), c(0x646C7E), c(0x98A0B2), c(0xD4DAE6), c(0xFFFFFF)]
RED = [c(0x320A0C), c(0x581416), c(0x8A2220), c(0xB83A30), c(0xDC6450)]
GOLD = [c(0x6A4A12), c(0x9C7424), c(0xD0A440), c(0xF0D070)]
MAIL = [c(0x2A2E36), c(0x4A505E), c(0x7A8292)]
LEATHER = [c(0x24140C), c(0x3E2416), c(0x5E3A24), c(0x845436)]


def sept():
    """Archivář Sept 26 × 36: uhlazené šedé vlasy s pěšinkou, kulaté brýle, ve kterých se odráží jen světlo (oči nejsou
    vidět), bledá úzká tvář, vysoký bílý límec s tmavou kravatou a mosaznou jehlicí, tmavomodrý kabát s klopami,
    páska se znakem vah na rukávu. Levá ruka na spisu, pravá drží pero."""
    s = Canvas(26, 36)
    # kabát: ramena (koule) + trup (válec se zúženými rameny)
    def torso(x, y):
        if y < 21:
            return abs(x + .5 - 13) < 6 + (y - 17) * 2.2
        return 1 <= x <= 24
    cylinder(s, 1, 24, 18, 35, NAVY, amb=0.25, shape=torso)
    sphere(s, 6, 23, 5.5, 5.2, NAVY, amb=0.25)
    sphere(s, 20, 23, 5.5, 5.2, NAVY, amb=0.25)
    # záhyby na rukávech a klopy
    for (x0, y0, x1, y1) in ((4, 27, 7, 33), (21, 27, 18, 33)):
        for i in range(5):
            shade_px(s, x0 + (x1 - x0) * i // 4, y0 + (y1 - y0) * i // 4, 0.6)
    for i in range(9):                                                      # klopy do V
        s.px(10 - i // 3, 19 + i, NAVY[3]); s.px(16 + i // 3, 19 + i, NAVY[1])
        s.px(11 - i // 3, 19 + i, NAVY[4] if i < 3 else NAVY[3])
    # vysoký bílý límec + kravata s jehlicí
    cylinder(s, 9, 17, 15, 19, WHITE, amb=0.35)
    for y in range(19, 30):
        s.px(12, y, NAVY[0]); s.px(13, y, NAVY[1]); s.px(14, y, NAVY[0] if y > 20 else NAVY[1])
    s.px(13, 23, BRASS[4]); s.px(12, 23, BRASS[2]); s.px(14, 23, BRASS[2])
    for y in (25, 29, 33):                                                  # knoflíky
        s.px(17, y, BRASS[3]); s.px(18, y, BRASS[1])
    # páska na rukávu (vlevo na obrázku)
    for y in range(28, 31):
        for x in range(1, 7):
            if s.opaque(x, y):
                s.px(x, y, WHITE[2] if y == 28 else WHITE[1])
    s.px(3, 29, BRASS[2]); s.px(4, 29, BRASS[3])
    # krk
    cylinder(s, 11, 15, 13, 15, SKIN_PALE, amb=0.2)
    # hlava a uši
    sphere(s, 7.8, 9.5, 1.6, 2.2, SKIN_PALE); sphere(s, 18.3, 9.5, 1.6, 2.2, SKIN_PALE)
    sphere(s, 13, 8.6, 5.4, 6.4, SKIN_PALE, amb=0.22)
    # vlasy: uhlazené dozadu pomádou – hladká čepice (koule nad čelem), pěšinka vlevo, pruh lesku
    sphere(s, 13, 7.4, 6.0, 7.0, HAIR_GREY, amb=0.25, mask=lambda x, y: y < 6 - (1 if 10 < x < 16 else 0) or (y < 9 and (x < 9 or x > 17)))
    for y in range(0, 5):
        s.px(10, y, HAIR_GREY[0]); s.px(9, y, HAIR_GREY[3] if y > 0 else HAIR_GREY[2])
    for x in range(11, 17):
        s.px(x, 1 if x < 15 else 2, HAIR_GREY[4])
    for x in range(12, 18, 2):
        s.px(x, 3, HAIR_GREY[1])                                            # stopy hřebenu
    # brýle: kulaté, jen odlesk (žádné oči)
    for gx in (10.9, 15.3):
        for y in range(7, 12):
            for x in range(8, 19):
                d = math.hypot(x + .5 - gx, y + .5 - 9.3)
                if d <= 1.9:
                    s.px(x, y, c(0x2A2A34) if d > 1.25 else c(0xD2DCEC))
        s.px(int(gx) - 1, 8, c(0xFFFFFF))
    s.px(13, 9, c(0x2A2A34))                                                # můstek
    s.px(8, 9, c(0x2A2A34)); s.px(18, 9, c(0x2A2A34))                      # stranice k uším
    s.px(10, 12, SKIN_PALE[2]); s.px(16, 12, SKIN_PALE[1])                 # lícní kosti
    # nos, ústa, stín pod bradou
    s.px(13, 11, SKIN_PALE[4]); s.px(14, 11, SKIN_PALE[1]); s.px(14, 12, SKIN_PALE[1])
    for x in range(11, 16):
        s.px(x, 13, c(0x7A4E46))
    s.px(10, 13, SKIN_PALE[1]); s.px(16, 13, SKIN_PALE[1])
    for x in range(11, 16):
        shade_px(s, x, 15, 0.7)
    # ruce na desce: levá na spisu, pravá s perem
    sphere(s, 6, 33.5, 2.6, 1.8, SKIN_PALE); sphere(s, 20, 33, 2.4, 1.8, SKIN_PALE)
    s.px(5, 34, SKIN_PALE[1]); s.px(7, 34, SKIN_PALE[1])
    for i in range(6):
        s.px(21 + i // 2, 33 - i, c(0x14141C))
    s.px(24, 28, BRASS[3])
    return selout(s)


def kassar():
    """Velitel Kassar 28 × 38: krátce střižené šedé vlasy, hustý šedý plnovous s knírem, husté obočí, jizva přes levé
    oko, ocelové náramenníky s nýty, kroužková košile, rudá tabarda se zlatým lemem a zkříženými meči, rudý plášť
    za rameny, kožené rukavice na pultu."""
    s = Canvas(28, 38)
    # plášť za rameny
    cylinder(s, 0, 27, 20, 37, RED[:4], amb=0.2, shape=lambda x, y: y > 20 + max(0, 6 - min(x, 27 - x)))
    # kroužková košile + tabarda
    def body(x, y):
        return 4 <= x <= 23 and y >= 19
    cylinder(s, 4, 23, 19, 37, MAIL, amb=0.3, shape=body)
    for y in range(19, 38):
        for x in range(4, 24):
            if s.opaque(x, y) and (x + y * 2) % 3 == 0:
                shade_px(s, x, y, 0.75)                                     # kroužky
    cylinder(s, 8, 19, 22, 37, RED, amb=0.25, shape=lambda x, y: y > 21 + abs(x - 13.5) * 0.25)
    for y in range(22, 38):
        for x in (8, 19):
            if s.opaque(x, y):
                s.px(x, y, GOLD[2] if x == 8 else GOLD[1])
    # zkřížené meče na tabardě
    for i in range(7):
        s.px(11 + i, 26 + i, STEEL[4]); s.px(17 - i, 26 + i, STEEL[3])
    s.px(13, 31, GOLD[3]); s.px(14, 31, GOLD[3]); s.px(10, 25, GOLD[2]); s.px(18, 25, GOLD[2])
    # krk (pod vousy) a náramenníky
    cylinder(s, 10, 17, 14, 18, SKIN_WARM, amb=0.2)
    sphere(s, 5.5, 21.5, 6.2, 5.0, STEEL, amb=0.3)
    sphere(s, 22.5, 21.5, 6.2, 5.0, STEEL, amb=0.3)
    for (x, y) in ((3, 20), (6, 18), (21, 18), (24, 20)):
        s.px(x, y, STEEL[5]); s.px(x + 1, y + 1, STEEL[1])
    for x in range(1, 11):
        shade_px(s, x, 25, 0.55)
    for x in range(17, 27):
        shade_px(s, x, 25, 0.55)
    # hlava
    sphere(s, 8.3, 9.8, 1.6, 2.3, SKIN_WARM); sphere(s, 19.7, 9.8, 1.6, 2.3, SKIN_WARM)
    sphere(s, 14, 9.2, 5.6, 6.6, SKIN_WARM, amb=0.22)
    # krátké šedé vlasy
    blobs(s, [(10.5, 3.6, 2.8), (14, 2.6, 3.4), (17.6, 3.6, 2.8), (9, 5.6, 1.8), (19, 5.6, 1.8)], HAIR_GREY, amb=0.3)
    # obočí, oči, jizva
    for x in range(10, 13):
        s.px(x, 7, HAIR_GREY[1])
    for x in range(15, 18):
        s.px(x, 7, HAIR_GREY[1])
    for (ex, ey) in ((11, 9), (16, 9)):
        s.px(ex - 1, ey, c(0xE8E2DA)); s.px(ex, ey, c(0x1E1A18)); s.px(ex + 1, ey, SKIN_WARM[1])
    for i in range(6):
        s.px(10 + i // 2, 5 + i, c(0xC88A7A) if i % 2 else c(0xB0705E))     # jizva přes levé oko
    s.px(14, 10, SKIN_WARM[4]); s.px(14, 11, SKIN_WARM[2]); s.px(15, 11, SKIN_WARM[1])   # nos
    # plnovous s knírem (chomáče)
    blobs(s, [(10.5, 13.2, 2.4), (17.5, 13.2, 2.4), (14, 14.8, 3.2), (12, 16.4, 2.4), (16, 16.4, 2.4), (14, 18, 2.2)],
          HAIR_GREY, amb=0.3)
    for x in range(12, 17):
        s.px(x, 12, HAIR_GREY[3] if x in (13, 15) else HAIR_GREY[2])         # knír
    s.px(14, 13, c(0x4A2A22))                                               # ústa ve vousech
    # kožené rukavice na pultu
    sphere(s, 7, 35.5, 3.0, 2.0, LEATHER, amb=0.3); sphere(s, 21, 35.5, 3.0, 2.0, LEATHER, amb=0.3)
    return selout(s)


# ── pulty (3/4 pohled): horní deska s hloubkou, přední hrana, stín pod přesahem, vystouplé panely, boční křídla ──

MARBLE = [c(0x0C0E14), c(0x1A1E28), c(0x262B38), c(0x323848), c(0x485062), c(0xC8CCD6)]   # černý mramor (Nero)
WALNUT = [c(0x1C0E08), c(0x2E1A0E), c(0x4A2C18), c(0x6A4026), c(0x8C5836), c(0xB07A50)]
OAK = [c(0x2E1A0E), c(0x4A2C18), c(0x6E4426), c(0x92603A), c(0xB88252), c(0xD8A676)]
PAPER = [c(0x9E9682), c(0xC8C0A8), c(0xE8E2CE), c(0xFAF6EA)]
INK = c(0x16161E)


def _grain(x, y, seed):
    """Léta dřeva: dlouhé vodorovné vlnky (ne cihly)."""
    v = y + 1.8 * math.sin(x * 0.06 + seed) + 0.7 * math.sin(x * 0.23 + seed * 2.1)
    return (v % 3.4) < 0.6 and (x * 7 + int(y) * 3) % 11 != 0


def counter(w, style="tower"):
    """Recepční pult w × 44 ve 3/4 pohledu. style "tower" = mramorová deska, ořech, mosaz, váhy;
    "arena" = dubová deska i čelo, rudo-zlatý cechovní štít. Řádky 0–11 jsou pro předměty a boční křídla,
    deska 12–21 (hloubka), čelo 22–41, sokl 42–43."""
    H = 44
    s = Spr(w, H)
    TOP, FRONT, PL = 12, 22, 42
    wood = WALNUT if style == "tower" else OAK
    topm = MARBLE if style == "tower" else OAK
    trim = BRASS if style == "tower" else GOLD
    W2 = 7                                                                # šířka bočních křídel
    # boční křídla (deska jde dozadu → nahoru), u zadního konce tmavší
    for x in list(range(0, W2)) + list(range(w - W2, w)):
        for y in range(2, TOP):
            t = (y - 2) / (TOP - 2)
            k = 1 + int(t * 2.4)
            col = topm[min(len(topm) - 2, k + (1 if x < W2 else 0))]
            if x in (0, w - 1):
                col = topm[0]
            elif x in (W2 - 1, w - W2):
                col = topm[1] if x == W2 - 1 else topm[3]                    # vnitřní hrana křídla
            if y == 2:
                col = topm[1]
            s.px(x, y, col)
    # horní deska: vzadu tmavší, vpředu světlá hrana (okosení), lesk/kresba
    for y in range(TOP, FRONT):
        t = (y - TOP) / (FRONT - TOP)
        for x in range(w):
            if y == TOP:
                col = topm[1]                                                 # zadní hrana
            elif y == FRONT - 2:
                col = topm[-1] if style != "tower" else BRASS[4]               # okosení přední hrany
            elif y == FRONT - 1:
                col = trim[2] if style == "tower" else topm[2]
            else:
                k = 2 + (1 if t > 0.35 else 0) + (1 if x < w * 0.45 and t > 0.2 else 0)
                col = topm[min(len(topm) - 2, k)]
                if style == "tower":
                    v = (y - TOP) - (1.5 + 0.12 * x + 1.4 * math.sin(x * 0.31))
                    if abs(v % 7 - 3.5) < 0.45 and (x + y) % 3:
                        col = c(0x8A90A0)                                     # šedé žilky v černém mramoru
                    elif abs(v % 7 - 3.5) < 0.9 and (x + y) % 4 == 0:
                        col = topm[4]
                elif _grain(x, y, 1.0):
                    col = topm[2]
            s.px(x, y, col)
    # čelo pultu
    for y in range(FRONT, PL):
        for x in range(w):
            col = wood[3]
            if y in (FRONT, FRONT + 1):
                col = wood[0] if y == FRONT else wood[1]                     # stín pod přesahem desky
            elif _grain(x, y, 2.3):
                col = wood[2]
            s.px(x, y, col)
    # vystouplé panely
    pn = max(3, w // 18)
    pw = (w - 4) // pn
    for i in range(pn):
        x0, x1 = 3 + i * pw, 3 + (i + 1) * pw - 3
        y0, y1 = FRONT + 4, PL - 3
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                e = min(x - x0, x1 - x, y - y0, y1 - y)
                if e == 0:
                    col = wood[5] if (x == x0 or y == y0) else wood[0]
                elif e == 1:
                    col = wood[4] if (x == x0 + 1 or y == y0 + 1) else wood[1]
                else:
                    col = wood[4] if _grain(x, y, i * 1.7) else wood[3]
                    if x - x0 < 4 and y - y0 < 4 and (x + y) % 2 == 0:
                        col = wood[5]                                         # lesk v rohu panelu
                s.px(x, y, col)
        # lišta mezi panely (pilastr)
        px = x1 + 1
        for y in range(FRONT + 2, PL):
            s.px(px, y, wood[1]); s.px(px + 1, y, wood[4]); s.px(px + 2, y, wood[2])
    # sokl
    for y in range(PL, H):
        for x in range(w):
            s.px(x, y, (NAVY if style == "tower" else LEATHER)[2 if y == PL else 0])
    s.hline(0, w - 1, PL, trim[2] if style == "tower" else wood[1])
    # znak uprostřed čela
    mx, my = w // 2, (FRONT + PL) // 2 + 1
    if style == "tower":
        sphere(s, mx, my, 7, 7, BRASS, amb=0.35)
        sphere(s, mx, my, 5.2, 5.2, NAVY, amb=0.3)
        s.vline(mx, my - 4, my + 3, BRASS[4]); s.hline(mx - 4, mx + 4, my - 2, BRASS[3])
        for sx in (mx - 4, mx + 4):
            s.px(sx, my, BRASS[3]); s.px(sx - 1, my + 1, BRASS[2]); s.px(sx + 1, my + 1, BRASS[2])
        s.hline(mx - 2, mx + 2, my + 3, BRASS[2])
    else:
        for y in range(my - 7, my + 8):                                       # štít
            half = 7 if y < my + 2 else 7 - (y - my - 2) * 1.2
            for x in range(int(mx - half), int(mx + half) + 1):
                e = min(x - (mx - half), (mx + half) - x)
                s.px(x, y, GOLD[3] if e < 1 else RED[3] if x < mx else RED[2])
        for i in range(-4, 5):
            s.px(mx + i, my + i * 0.9 // 1 - 1, STEEL[4]); s.px(mx - i, my + i * 0.9 // 1 - 1, STEEL[3])
    # boční hrany čela (plastičnost)
    for y in range(FRONT, PL):
        s.px(0, y, wood[4]); s.px(1, y, wood[3]); s.px(w - 1, y, wood[0]); s.px(w - 2, y, wood[1])
    return s


def _shadow_on(s, x0, y0, x1, y1, k=0.72):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            shade_px(s, x, y, k)


def tower_desk_items(s):
    """Předměty na Septově pultu (kreslí se do řádků 0–20 spritu counter): psací stroj s papírem, stohy spisů,
    razítko s poduškou, mosazný zvonek, bankéřská lampa se zeleným stínidlem a kruhem světla, jmenovka."""
    w = s.w
    # psací stroj
    _shadow_on(s, 10, 17, 25, 18)
    for y in range(9, 18):
        for x in range(8, 24):
            t = (y - 9) / 9
            s.px(x, y, IRON_[3] if y == 9 else IRON_[2] if t < 0.4 else IRON_[1])
    for row, y in enumerate((13, 15)):
        for x in range(9 + row, 23, 2):
            s.px(x, y, PAPER[3]); s.px(x, y + 1, IRON_[0])
    for y in range(3, 10):
        for x in range(11, 21):
            s.px(x, y, PAPER[3] if y < 8 else PAPER[2])
    for x in range(12, 20):
        s.px(x, 5, PAPER[0] if x % 3 else PAPER[3])
    s.hline(9, 22, 9, IRON_[4]); s.px(23, 10, IRON_[4])
    # stohy spisů
    for (x0, hh) in ((27, 8), (34, 5)):
        _shadow_on(s, x0 + 1, 19, x0 + 7, 20)
        for k in range(hh):
            y = 18 - k
            for x in range(x0, x0 + 7):
                s.px(x, y, PAPER[2] if k % 2 else c(0xD8C8A0))
            s.px(x0 + 6, y, PAPER[0])
        for x in range(x0, x0 + 7):
            s.px(x, 18 - hh, NAVY[3])
    # razítko
    _shadow_on(s, 43, 18, 47, 19)
    for x in range(42, 47):
        s.px(x, 17, NAVY[2]); s.px(x, 18, NAVY[1])
    sphere(s, 44.5, 13, 1.6, 2.6, WALNUT[3:], amb=0.3)
    s.hline(43, 46, 15, WALNUT[4])
    # zvonek
    _shadow_on(s, 50, 18, 54, 19)
    sphere(s, 51.5, 16.5, 2.6, 2.0, BRASS, amb=0.35); s.px(51, 13, BRASS[4])
    # lampa
    if w > 60:
        lx = w - 9
        for y in range(10, 22):
            for x in range(lx - 9, lx + 10):
                d = math.hypot((x - lx) / 1.6, y - 19) / 6
                if d < 1 and s.opaque(x, y):
                    a = (1 - d) * 0.35
                    yy = y
                    s.a[yy, x, :3] = np.clip(s.a[yy, x, :3] * (1 - a) + np.array([190, 255, 200]) * a, 0, 255).astype(np.uint8)
        s.hline(lx - 2, lx + 2, 19, BRASS[1]); s.hline(lx - 2, lx + 2, 18, BRASS[3])
        s.vline(lx, 11, 17, BRASS[2])
        for y in range(7, 11):
            for x in range(lx - 4 + (10 - y) // 2, lx + 5 - (10 - y) // 2):
                s.px(x, y, GREEN_[3] if y == 7 else GREEN_[2] if x < lx else GREEN_[1])
        s.hline(lx - 3, lx + 3, 11, c(0xFFF6C8))
    # jmenovka na hraně
    for x in range(26, 37):
        s.px(x, 20, BRASS[3]); s.px(x, 21, BRASS[1])
    for x in range(28, 35, 2):
        s.px(x, 20, INK)


def arena_desk_items(s):
    """Předměty na pultu velitele: otevřená kniha zápasů s řádky, kalamář s brkem, sloupek mincí, zvonek, svitky."""
    # kniha
    _shadow_on(s, 9, 19, 26, 20)
    for y in range(13, 19):
        for x in range(8, 25):
            col = PAPER[3] if x < 16 else PAPER[2]
            if x in (16, 17):
                col = PAPER[0]
            if y in (15, 17) and x not in (16, 17) and (x % 2 == 0):
                col = PAPER[0]
            s.px(x, y, col)
    for x in range(8, 25):
        s.px(x, 19, RED[2])
    # kalamář s brkem
    _shadow_on(s, 29, 18, 33, 19)
    sphere(s, 30.5, 17, 2.2, 1.8, [c(0x0C0C14), c(0x1C1C2A), c(0x34344A)], amb=0.3)
    for i in range(7):
        s.px(31 + i // 2, 15 - i, PAPER[3] if i > 2 else PAPER[1])
    # mince
    _shadow_on(s, 37, 18, 42, 19)
    for k in range(4):
        for x in range(36, 41):
            s.px(x, 17 - k, GOLD[3] if x < 38 else GOLD[2])
        s.px(40, 17 - k, GOLD[1])
    # zvonek
    _shadow_on(s, 45, 18, 49, 19)
    sphere(s, 46.5, 16.5, 2.6, 2.0, BRASS, amb=0.35); s.px(46, 13, BRASS[4])
    # svitky
    for (x0, y0) in ((52, 15), (53, 17)):
        _shadow_on(s, x0 + 1, y0 + 2, x0 + 8, y0 + 2)
        for x in range(x0, x0 + 8):
            s.px(x, y0, PAPER[3]); s.px(x, y0 + 1, PAPER[1])
        s.px(x0 + 3, y0, RED[3]); s.px(x0 + 3, y0 + 1, RED[2])


IRON_ = [c(0x16181E), c(0x2A2E38), c(0x464C58), c(0x6A7280), c(0xA0A8B6)]
GREEN_ = [c(0x0E3020), c(0x1C5034), c(0x2E7A4E), c(0x6ED096)]
