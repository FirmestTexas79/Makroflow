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
