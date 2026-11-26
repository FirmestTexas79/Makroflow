"""
Použití:  python tools/mapgen/gen_abyss.py
Výstup:   app/src/main/res/drawable-nodpi/tower_abyss.png, app/src/main/assets/walk/tower_abyss.txt,
          tools/mapgen/tower_abyss_debug.png

Patro −2 Věže Řádu Váhy – Sektor Hlubiny (176 × 352, pohled 3/4 shora). Výtah „Subjekt připraven“ ústí do
čedičové stěny nad propastí; ocelová lávka vede nad prázdnotu na pozorovací balkon. Pod světem visí Lumira –
mrtvý strom hlavou dolů: bledé kořeny vyrůstají ze skal nahoře a sbíhají se do kmene, pod ním se v hloubce
rozprostírá obrácená koruna. Rudé nitě Řádu (stanice 0–7) vedou shora až ke kmeni. Na kmeni v hloubce drobný
záblesk kovu – kopí (Akt III). U balkonu visí klec na řetězech: SESTUP ZATÍM NESCHVÁLEN.
Bez setkání. Živé detaily: InteriorFxView.ABYSS. NODES / EDGES jsou ZDROJ PRAVDY i pro TowerAbyss.kt (MAP).
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "tower")); sys.path.insert(0, os.path.join(HERE, "ashplain"))
import light as L, figures as F  # noqa: E402
from pix import c  # noqa: E402
from hideout_parts import _vn, _h  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
AW, AH = 176, 352
WALL_H = 92
NODES = {"hlub_vytah": (88, 102), "hlub_most": (88, 140), "hlub_cedule": (76, 186), "hlub_vyhled": (88, 196),
         "hlub_klec": (114, 184), "hlub_venku": (56, 196)}
EDGES = [("hlub_vytah", "hlub_most"), ("hlub_most", "hlub_vyhled"), ("hlub_most", "hlub_cedule"), ("hlub_vyhled", "hlub_klec"),
         ("hlub_cedule", "hlub_vyhled"), ("hlub_cedule", "hlub_venku"), ("hlub_venku", "hlub_vyhled")]

BARK = [c(0x2A2824), c(0x4A4640), c(0x75706A), c(0xA29C92), c(0xCCC6BA), c(0xEEE8DC)]
BASALT = [c(0x0C0C10), c(0x16161C), c(0x202028), c(0x2C2C36), c(0x3A3A46), c(0x4E4E5C)]
STEEL = F.STEEL
RUSTC = c(0x5A3420)
VOID_TOP = np.array([16, 22, 38], float); VOID_BOT = np.array([2, 3, 8], float)

img = np.zeros((AH, AW, 4), np.uint8); img[:, :, 3] = 255
block = np.ones((AH, AW), bool)
depth = np.zeros((AH, AW))                                    # 0 = blízko (lávka), 1 = nejhlouběji

# ── propast: tma s mlhou ─────────────────────────────────────────────────────
for y in range(AH):
    k = y / AH
    for x in range(AW):
        col = VOID_TOP * (1 - k) + VOID_BOT * k
        m = _vn(x, y * 1.6, 3, 14) * 0.7 + _vn(x, y, 5, 5) * 0.3
        if m > 0.58:
            col = col + np.array([18, 24, 40]) * (m - 0.58) * 2.2
        img[y, x, :3] = np.clip(col, 0, 255).astype(np.uint8)


def shade_bark(v, x, y):
    f = max(0.0, min(0.999, v)) * (len(BARK) - 1)
    i = int(f)
    return BARK[i + 1] if (f - i) > F.BAYER[y % 4, x % 4] * 0.8 + 0.1 and i + 1 < len(BARK) else BARK[i]


def fade(col, d):
    """Hloubková mlha: čím hlouběji, tím tmavší a modřejší."""
    tgt = np.array([10, 16, 34], float)
    return np.clip(col[:3].astype(float) * (1 - d) + tgt * d, 0, 255).astype(np.uint8)


def limb(pts, w0, w1, d0, d1):
    """Stínovaný válcový kořen/větev podél křivky bodů, šířka w0→w1, hloubka d0→d1 (zastíní vzdálenější)."""
    n = len(pts)
    for i in range(n - 1):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        t = i / (n - 1)
        w = w0 + (w1 - w0) * t
        d = d0 + (d1 - d0) * t
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln
        steps = max(1, int(ln * 1.5))
        for s_ in range(steps):
            cx, cy = x0 + dx * s_ / steps, y0 + dy * s_ / steps
            for k in np.linspace(-w / 2, w / 2, max(2, int(w * 2))):
                X, Y = int(round(cx + nx * k)), int(round(cy + ny * k))
                if not (0 <= X < AW and 0 <= Y < AH):
                    continue
                if depth[Y, X] and depth[Y, X] < d - 0.02:
                    continue
                u = k / (w / 2 + 0.01)
                v = 0.6 - 0.45 * u * (1 if nx >= 0 else -1) * 0.8 + 0.1 * math.cos(u * math.pi)
                if abs(u) > 0.82:
                    v = 0.08
                if _h(int(cx * 0.7), int(cy * 0.5), 3) % 11 == 0 and abs(u) < 0.5:
                    v -= 0.3                                          # praskliny v kůře
                col = shade_bark(v, X, Y)
                img[Y, X, :3] = fade(col, d)
                depth[Y, X] = d


def bezier(p0, p1, p2, n=40):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in np.linspace(0, 1, n)]


TX, TY0, TY1 = 88, 214, 262                                    # kmen

# obrácená koruna: souvislé větve s plynulým ohybem (pravidlo trubek), do hloubky tmavnou a modrají
sys.path.insert(0, os.path.join(HERE, "hlubina"))
from tree import TreePainter  # noqa: E402
rng = np.random.default_rng(9)
tp = TreePainter(img, lambda x, y: (VOID_TOP * (1 - y / AH) + VOID_BOT * (y / AH) + np.array([8, 12, 24]),
                                    min(0.85, 0.2 + max(0.0, (y - TY1) / (AH - TY1)) * 0.65)), seed=9)
tp.split3 = 0.4
for ang, r in ((math.pi / 2 - 1.1, 3.2), (math.pi / 2 - 0.5, 3.6), (math.pi / 2, 3.9), (math.pi / 2 + 0.5, 3.6), (math.pi / 2 + 1.1, 3.2)):
    tp.branch(TX + math.cos(ang) * 3, TY1 - 2, ang, r, 42, 0, math.pi / 2, spread=(0.38, 0.7), bend=0.05, maxdepth=7, pull=0.02)
tp.composite(dark=(84, 80, 72), light=(206, 200, 188))
tips = [(x, y, 0.55 + (y - TY1) / (AH - TY1) * 0.4) for (x, y) in tp.tips]

# kmen

# obrácená koruna: větve rostou z dolního konce kmene dolů a do stran (vzdálenější, kreslí se první)
rng = np.random.default_rng(9)
def branch(x, y, ang, ln, w, d, lvl):
    if lvl > 4 or ln < 5 or y > AH + 10:
        return
    pts = []
    a = ang
    for i in range(10):
        pts.append((x, y))
        a += rng.uniform(-0.12, 0.12)
        x += math.cos(a) * ln / 9; y += math.sin(a) * ln / 9
    limb(pts, w, max(1.0, w * 0.55), d, min(0.95, d + 0.12))
    tips.append((x, y, d))
    for s_ in (-1, 1):
        if rng.random() < 0.92:
            branch(x, y, ang + s_ * rng.uniform(0.3, 0.6), ln * rng.uniform(0.62, 0.78), max(1.0, w * 0.6), min(0.95, d + 0.12), lvl + 1)
tips = []
for ang in (math.pi / 2 - 1.15, math.pi / 2 - 0.55, math.pi / 2, math.pi / 2 + 0.55, math.pi / 2 + 1.15):
    branch(TX + math.cos(ang) * 4, TY1, ang, 46, 7, 0.55, 0)

# kmen
limb([(TX, TY0 + i * (TY1 - TY0) / 12) for i in range(13)], 18, 13, 0.35, 0.5)

# kořeny: ze skal nahoře a ze stran se sbíhají do kmene (blíž = světlejší, kreslí se naposledy)
for (sx, sy, mx, my, w, d) in ((-6, 70, 30, 170, 9, 0.25), (182, 78, 150, 168, 9, 0.25), (-6, 150, 40, 200, 7, 0.3),
                              (182, 140, 140, 200, 7, 0.3), (30, 60, 56, 150, 6, 0.2), (150, 64, 124, 150, 6, 0.2),
                              (-6, 112, 20, 196, 5, 0.32), (182, 108, 160, 196, 5, 0.32)):
    limb(bezier((sx, sy), (mx, my), (TX + (sx - 88) * 0.06, TY0 + 6)), w, 6, d, 0.35)

for (sx, mx, w) in ((40, 60, 4), (128, 116, 4), (16, 44, 3), (160, 136, 3), (112, 104, 3)):
    limb(bezier((sx, WALL_H - 2), (mx, WALL_H + 50), (TX + (sx - 88) * 0.05, TY0 + 4)), w, 3, 0.18, 0.34)

# rudé nitě Řádu (stanice 0–7) shora ke kmeni
for i in range(8):
    x0 = 8 + i * 22
    for t in np.linspace(0, 1, 260):
        x = x0 + (TX + (i - 3.5) * 1.2 - x0) * t
        y = WALL_H - 4 + (TY0 + 10 - WALL_H + 4) * t + math.sin(t * math.pi) * 10
        X, Y = int(x), int(y)
        if 0 <= X < AW and 0 <= Y < AH and (X + Y) % 3:
            img[Y, X, :3] = fade(c(0xB0262A), 0.25 + 0.2 * t)

# kopí v kmeni (Akt III): drobný kovový záblesk a temná skvrna
for i in range(9):
    img[240 - i, 82 + i // 2, :3] = (190, 196, 210) if i < 2 else (90, 92, 104)
img[238, 81, :3] = (255, 255, 255)

# bioluminiscence na koncích větví a v mlze
glow = np.zeros((AH, AW))
for (x, y, d) in tips:
    if 0 <= int(x) < AW and 0 <= int(y) < AH and rng.random() < 0.55:
        img[int(y), int(x), :3] = (150, 210, 255) if d < 0.8 else (90, 140, 200)
        for yy in range(int(y) - 6, int(y) + 7):
            for xx in range(int(x) - 6, int(x) + 7):
                if 0 <= xx < AW and 0 <= yy < AH:
                    glow[yy, xx] = max(glow[yy, xx], max(0, 1 - math.hypot(xx - x, yy - y) / 6) * 0.6)
for _ in range(70):
    x, y = int(rng.integers(0, AW)), int(rng.integers(WALL_H, AH))
    img[y, x, :3] = (120, 170, 230) if rng.random() < 0.5 else (70, 100, 150)
L.lighten(img, glow, 0.3, (130, 200, 255), steps=6)

# mlha přes hlubinu (vrstvy)
for y in range(170, AH):
    for x in range(AW):
        m = _vn(x + 40, y * 2.2, 21, 11)
        k = (y - 170) / (AH - 170)
        if m > 0.55 - k * 0.15:
            a = min(0.45, (m - 0.55 + k * 0.15) * 1.6) * (0.5 + 0.5 * k)
            if F.BAYER[y % 4, x % 4] < a * 2:
                img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([40, 54, 84]) * a, 0, 255).astype(np.uint8)

# ── čedičová stěna s výtahem ─────────────────────────────────────────────────
for y in range(WALL_H + 6):
    for x in range(AW):
        edge = WALL_H + 2 + int(4 * _vn(x, 0, 7, 8))                         # rozervaná spodní hrana skály
        if y > edge:
            continue
        col_i = (x // 9 + (y // 13) * 3) % 4                                  # sloupcová odlučnost čediče
        v = 2 + (1 if (x % 9) < 2 else 0) - (1 if (x % 9) > 7 else 0)
        if y % 13 == 0:
            v = 1
        if y > edge - 3:
            v = 0
        img[y, x] = BASALT[max(0, min(5, v + (1 if col_i == 0 else 0)))]
for yy in range(20, WALL_H):                                                  # nýtovaný ocelový rám
    for xx in range(60, 117):
        if xx < 67 or xx > 109 or yy < 28:
            col = STEEL[3] if xx in (60, 61) or yy == 20 else STEEL[1] if xx in (115, 116) else STEEL[2]
            if (xx - 60) % 6 == 3 and (yy < 28 or xx < 67 or xx > 109) and yy % 6 == 3:
                col = STEEL[4]
            img[yy, xx] = col
el = F.elevator3d(40, 58)
for yy in range(el.h):
    for xx in range(el.w):
        if el.a[yy, xx, 3]:
            img[WALL_H + 2 - 58 + yy, 68 + xx] = el.a[yy, xx]
dial = F.floor_dial3d(10, at=-2)
for yy in range(dial.h):
    for xx in range(dial.w):
        if dial.a[yy, xx, 3]:
            img[2 + yy, 76 + xx] = dial.a[yy, xx]
import signs as SG, order as O  # noqa: E402
sign = O.mount_plate(SG.plate("HLUBINA", c(0xE8E2CC), c(0x1E2A44), seed=17, pad=2, wear=0.0))
for yy in range(sign.h):                                                      # cedule přišroubovaná do čediče
    for xx in range(sign.w):
        if sign.a[yy, xx, 3]:
            img[8 + yy, 144 - sign.w // 2 + xx] = sign.a[yy, xx]

# ── lávka a balkon ───────────────────────────────────────────────────────────
GR = [c(0x22242A), c(0x34363E), c(0x4A4C56), c(0x666874)]
def grate(x0, x1, y0, y1):
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = 2 if ((x // 2) + (y // 2)) % 2 else 1
            if x in (x0, x1 - 1):
                k = 3
            if y - y0 < 30 and k == 1 and (x + y) % 3 == 0:
                k = 2
            img[y, x] = GR[k]
            block[y, x] = False
# tloušťka lávky a balkonu (spodní hrana v pohledu 3/4) + stín na kořenech pod nimi
for y in range(WALL_H, 190):
    for x in range(105, 109):
        img[y, x] = STEEL[1] if x < 107 else STEEL[0]
for a in range(0, 181):
    t = math.radians(a)
    x, y = 88 + math.cos(t) * 40, 186 + math.sin(t) * 30
    for d in range(1, 5):
        X, Y = int(x), int(y + d)
        if 0 <= X < AW and 0 <= Y < AH:
            img[Y, X] = STEEL[1] if d < 3 else STEEL[0]
for y in range(WALL_H, 230):
    for x in range(40, 140):
        inside = (72 <= x < 112 and y < 192) or math.hypot((x + 3.5 - 88) / 41, (y - 5 + .5 - 186) / 31) <= 1
        if inside and y > WALL_H + 2:
            img[y, x, :3] = (img[y, x, :3] * 0.55).astype(np.uint8)
grate(72, 105, WALL_H, 186)
for y in range(176, 218):                                                     # půlkruhový balkon
    for x in range(48, 129):
        if math.hypot((x + .5 - 88) / 40, (y + .5 - 186) / 30) <= 1 and y >= 176:
            img[y, x] = GR[2 if ((x // 2) + (y // 2)) % 2 else 1]
            block[y, x] = False
for y in range(WALL_H, 220):                                                  # stín lávky pod sebou
    for x in range(AW):
        pass
def rail(points):
    for (x, y) in points:
        X, Y = int(x), int(y)
        if 0 <= X < AW and 0 <= Y < AH:
            img[Y, X] = STEEL[4]; img[min(AH - 1, Y + 1), X] = STEEL[1]
            block[Y, X] = True
            if (X + Y) % 7 == 0:
                img[Y - 1, X] = STEEL[5]
for y in range(WALL_H, 178):
    rail([(71, y), (105, y)])
for a in range(0, 181):
    t = math.radians(a)
    rail([(88 + math.cos(t) * 40, 186 + math.sin(t) * 30)])
for (x, y) in ((71, 178), (105, 178)):
    for i in range(10):
        block[y - 1, x - 5 + i] = block[y - 1, x - 5 + i] or (abs(i - 5) > 2 and False)
for a in range(0, 181, 15):                                                   # sloupky zábradlí
    t = math.radians(a)
    x, y = int(88 + math.cos(t) * 40), int(186 + math.sin(t) * 30)
    img[y - 2:y + 1, x] = STEEL[5]
# pult s mosaznou deskou na balkoně: ocelový sloupek, šikmá deska s rytým písmem, nýty, odlesk
BR = [c(0x3A2410), c(0x5E3C1A), c(0x8A5E2A), c(0xB4843E), c(0xD8AC5C), c(0xF2D38A)]
lect = np.zeros((22, 30, 4), np.uint8)
def lp(x, y, col):
    if 0 <= x < 30 and 0 <= y < 22:
        lect[y, x, :3] = col[:3]; lect[y, x, 3] = 255
for y in range(12, 22):                                                       # sloupek a patka
    lp(14, y, STEEL[3]); lp(15, y, STEEL[1])
for x in range(10, 20):
    lp(x, 21, STEEL[2]); lp(x, 20, STEEL[3] if x < 15 else STEEL[1])
for y in range(0, 12):                                                        # deska (šikmá – horní hrana dál)
    for x in range(1 + (11 - y) // 4, 29 - (11 - y) // 4):
        v = 2 + (1 if y < 3 else 0) + (1 if (x + y) % 9 == 0 else 0)
        if y in (0, 11) or x in (1 + (11 - y) // 4, 28 - (11 - y) // 4):
            v = 1
        lp(x, y, BR[min(5, v)])
for y in range(10, 12):
    for x in range(3, 27):
        lp(x, y + 1, BR[0])                                                   # tloušťka desky
for (x, y) in ((4, 2), (25, 2), (3, 9), (26, 9)):
    lp(x, y, BR[5])                                                           # nýty
_t = O.Spr(28, 6); SG.tiny(_t, 0, 0, "SEKTOR", BR[0])
for yy in range(5):
    for xx in range(24):
        if _t.a[yy, xx, 3]:
            lp(4 + xx, 3 + yy, BR[1])                                         # rytý nápis
for x in range(6, 24, 2):
    lp(x, 9, BR[2])                                                           # drobné řádky pod tím
for x in range(5, 12):
    lp(x, 1, BR[5])                                                           # odlesk
for yy in range(22):
    for xx in range(30):
        if lect[yy, xx, 3]:
            img[160 + yy, 52 + xx] = lect[yy, xx]
block[172:182, 60:72] = True
# klec na řetězech vpravo od balkonu
for yy in range(150, 200):
    img[yy, 128 if yy % 2 else 129] = STEEL[3]
    img[yy, 140 if yy % 2 else 139] = STEEL[3]
for yy in range(196, 222):
    for xx in range(124, 146):
        bar = (xx - 124) % 4 == 0 or yy in (196, 221)
        if bar:
            img[yy, xx] = STEEL[4] if xx < 136 else STEEL[2]
        elif yy == 208:
            img[yy, xx] = STEEL[1]
img[208, 121:126] = STEEL[3]                                                  # závora se zámkem
img[207:210, 121:123] = c(0xA82A24)
block[194:224, 122:148] = True

# železný výtah z Ozvěny vlevo od balkonu: dlouhé řetězy shora z propasti, nýtovaná klec s padací mříží,
# lávka z plechu k balkonu, nahoře kladka a žlutočerná výstražná páska (výtah „nad propastí“ z náměstí)
for yy in range(0, 196):
    for (xx, ph) in ((30, 0), (46, 1)):
        img[yy, xx] = STEEL[3] if (yy + ph) % 2 else STEEL[1]
        if (yy + ph) % 4 == 0:
            img[yy, xx + 1] = STEEL[2]
for xx in range(27, 51):                                                      # kladka nad klecí
    img[188, xx] = STEEL[2]; img[189, xx] = STEEL[1]
for yy in range(190, 224):
    for xx in range(26, 50):
        edge = xx in (26, 49) or yy in (190, 223)
        bar = (xx - 26) % 3 == 0
        if edge:
            img[yy, xx] = STEEL[4] if xx < 38 else STEEL[2]
        elif bar:
            img[yy, xx] = STEEL[3] if xx < 38 else STEEL[1]
        elif yy in (200, 213):
            img[yy, xx] = STEEL[1]
        elif yy > 214:
            img[yy, xx, :3] = (img[yy, xx, :3] * 0.6).astype(np.uint8)
for xx in range(26, 50):                                                      # výstražná páska na podlaze klece
    img[222, xx] = c(0xE0B030) if (xx // 3) % 2 else c(0x1A1A1A)
for (xx, yy) in ((27, 191), (48, 191), (27, 222), (48, 222)):
    img[yy, xx] = STEEL[5]                                                    # nýty
for yy in range(194, 202):                                                    # plechová lávka ke balkonu
    for xx in range(49, 58):
        img[yy, xx] = GR[2 if (xx + yy) % 2 else 1]
        block[yy, xx] = False
lamp_y = 186
img[lamp_y:lamp_y + 2, 36:40] = c(0xF2C94C)                                   # signálka „PŘIPRAVEN“
block[188:226, 24:50] = True

L_v = np.zeros((AH, AW)); L.vignette(L_v, AW / 2, AH * 0.45, AW * 0.62, AH * 0.6, k=0.55)
L.darken(img, L_v, 0.7, (2, 2, 6), dither=True, steps=10)
cold = np.zeros((AH, AW))
L.light_shaft(cold, (70, WALL_H), (106, WALL_H), 0.0, 120)
L.lighten(img, cold, 0.12, (170, 190, 230), steps=8)

Image.fromarray(img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "tower_abyss.png"))
CELL = 2
walk = ~block
rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#" for gx in range(AW // CELL)) for gy in range(AH // CELL)]
for n, (x, y) in NODES.items():
    r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", "tower_abyss.txt"), "w") as f:
    f.write(f"# mapa chůze tower_abyss: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_abyss.py)\n{AW} {AH} {CELL}\n")
    f.write("\n".join(rows) + "\n")
dbg = Image.fromarray(img[:, :, :3]).resize((AW * 3, AH * 3), Image.NEAREST).convert("RGBA")
ov = Image.new("RGBA", dbg.size, (0, 0, 0, 0)); dr = ImageDraw.Draw(ov)
for gy, row in enumerate(rows):
    for gx, ch in enumerate(row):
        if ch == "#":
            dr.rectangle([gx * 6, gy * 6, gx * 6 + 5, gy * 6 + 5], fill=(255, 0, 0, 30))
for (a, b) in EDGES:
    dr.line([NODES[a][0] * 3, NODES[a][1] * 3, NODES[b][0] * 3, NODES[b][1] * 3], fill=(255, 255, 0, 200), width=2)
for n, (x, y) in NODES.items():
    dr.ellipse([x * 3 - 5, y * 3 - 5, x * 3 + 5, y * 3 + 5], fill=(0, 120, 255, 230))
Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "tower_abyss_debug.png"))
print("ok tips", len(tips))
