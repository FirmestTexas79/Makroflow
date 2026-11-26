"""
Použití:  python tools/mapgen/gen_root_heart.py
Výstup:   app/src/main/res/drawable-nodpi/root_heart.png, app/src/main/assets/walk/root_heart.txt,
          tools/mapgen/root_heart_debug.png

STŘED KOŘENŮ (176 × 448) – finální lokace (docs/adr/0110). Pod puklinou u paty kmene se otevře dutina uvnitř kořenů:
žádný popel, žádný Řád, jen světlo a klid. Kořeny se sbíhají do kokonu jako žebra kopule, jsou teplé a bledé jako
kost na slunci, z klenby visí tenká vlákna se světélky na koncích. Dno je mělká, úplně klidná voda, která zrcadlí
klenbu. Po plochých kamenech s mechem se jde dolů k ostrůvku, kde v kolébce z jemných kořínků leží poslední světlo
Lumiry (živě: InteriorFxView.ROOT_HEART). Vlevo pod obloukem kořene mechové lůžko. Shora z pukliny padá úzký sloup
světla. Barvy: z modré tmy Hlubiny do teplé zlatavé – jako svítání. Bez setkání, bez nápisů.
NODES / EDGES jsou ZDROJ PRAVDY i pro RootHeart.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import _h, _vn  # noqa: E402
from scene import Scene  # noqa: E402
from roots import bez  # noqa: E402
from tree import TreePainter  # noqa: E402
import light as L  # noqa: E402

AW, AH = 176, 448
CX = 88
ISLE = (88, 318, 30, 13)                                                   # ostrůvek se světlem: střed, poloosy
WATER = 196                                                               # od této výšky dno zalévá voda
NODES = {
    "st_vstup": (88, 40), "st_schody": (88, 116), "st_koreny": (42, 168), "st_odraz": (134, 206),
    "st_brod": (90, 252), "st_mech": (36, 300), "st_svetlo": (88, 312), "st_ticho": (116, 380),
}
EDGES = [("st_vstup", "st_schody"), ("st_schody", "st_koreny"), ("st_schody", "st_odraz"), ("st_koreny", "st_brod"),
         ("st_odraz", "st_brod"), ("st_brod", "st_mech"), ("st_brod", "st_svetlo"), ("st_mech", "st_svetlo"),
         ("st_svetlo", "st_ticho")]
sc = Scene(AW, AH, 0, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(213)
F = lambda v: np.array(v, float)                                          # noqa: E731
LX, LY = ISLE[0], ISLE[1] - 8                                             # poslední světlo


def glow(x, y):
    return max(0.0, 1 - math.hypot((x - LX) / 150, (y - LY) / 250)) ** 1.25


def void(x, y):
    """Pozadí dutiny: kolem světla teplé zlatavé, ke krajům modrá tma Hlubiny; nahoře slabě studené z pukliny."""
    g = glow(x, y)
    col = F([14, 20, 34]) * (1 - g) + F([246, 220, 168]) * g
    top = max(0.0, 1 - y / 90) * max(0.0, 1 - abs(x - CX) / 40)
    col = col * (1 - top * 0.5) + F([150, 190, 200]) * top * 0.5
    n = _vn(x * 0.7, y * 0.9, 3, 22) * 0.6 + _vn(x, y, 5, 7) * 0.4
    return col * (0.88 + 0.24 * n)


for y in range(AH):
    for x in range(AW):
        img[y, x, :3] = np.clip(void(x, y), 0, 255)


def chain(*pts, n=30):
    out = []
    mids = [pts[0]] + [((a[0] + b[0]) / 2, (a[1] + b[1]) / 2) for a, b in zip(pts[1:-1], pts[2:])] + [pts[-1]]
    for i in range(len(pts) - 2):
        out += bez(mids[i], pts[i + 1], mids[i + 1], n)
    return out


def bough(tp, pts, r0, r1, twigs=0):
    n = len(pts)
    tp.stroke([(x, y, r0 + (r1 - r0) * i / (n - 1)) for i, (x, y) in enumerate(pts)])
    for _ in range(twigs):
        i = int(rng.integers(n // 5, n - 4))
        x, y = pts[i]
        a = math.atan2(pts[i + 1][1] - y, pts[i + 1][0] - x) + rng.choice([-1, 1]) * rng.uniform(0.5, 1.1)
        tp.branch(x, y, a, (r0 + (r1 - r0) * i / n) * 0.35, rng.uniform(12, 26), 4, math.pi / 2, maxdepth=6, pull=0.02)


def lit(x, y):
    """Kořeny k světlu teplé a světlé, odvrácené chladně tmavé."""
    return (np.clip(void(x, y), 0, 255), 0.1 + 0.3 * (1 - glow(x, y)))


# 1) vzdálená vrstva: hustá síť kořenů ve stěně kokonu
far = TreePainter(img, lambda x, y: (np.clip(void(x, y), 0, 255), 0.55), seed=1)
for i in range(18):
    a = i / 18 * math.pi * 2
    x0, y0 = LX + math.cos(a) * 150, LY + math.sin(a) * 230
    pts = chain((x0, y0), (LX + math.cos(a + 0.5) * 110, LY + math.sin(a + 0.5) * 170), (LX + math.cos(a + 0.9) * 96, LY + math.sin(a + 0.9) * 150))
    bough(far, pts, rng.uniform(2.5, 4), rng.uniform(1.5, 3), twigs=3)
far.composite(dark=(60, 56, 52), light=(200, 186, 160))

# 2) žebra kopule: od pukliny nahoře se rozbíhají a dole se zase stáčejí k sobě (kokon)
mid = TreePainter(img, lit, seed=2)
for sd in (-1, 1):
    for (w0, w1, w2, r0, r1) in ((8, 84, 74, 9, 7), (4, 70, 50, 6, 5), (16, 92, 88, 6, 4)):
        pts = chain((CX + sd * w0, -6), (CX + sd * w1, 90), (CX + sd * (w1 + 6), 230), (CX + sd * w2, 360), (CX + sd * (w2 * 0.4), AH + 10))
        bough(mid, pts, r0, r1, twigs=5)
mid.composite(dark=(96, 84, 70), light=(250, 238, 210))

# 3) vlákna z klenby se světélky na konci (jako kořínky trávy, jen naruby)
threads = TreePainter(img, lambda x, y: (np.clip(void(x, y), 0, 255), 0.3), seed=3)
TIPS = []
for k in range(22):
    x0 = 24 + k * 6.2 + rng.uniform(-3, 3)
    top = 10 + abs(x0 - CX) * 0.9
    ln = rng.uniform(30, 120) * (1 - abs(x0 - CX) / 140)
    pts = [(x0 + math.sin(t * 2.2 + k) * 2.5 * t, top + ln * t, 0.9 - 0.4 * t) for t in np.linspace(0, 1, 30)]
    threads.stroke(pts)
    TIPS.append((int(pts[-1][0]), int(pts[-1][1])))
threads.composite(dark=(120, 108, 90), light=(240, 226, 196))
for (x, y) in TIPS:                                                        # světélka
    if 0 <= y < AH - 1:
        img[y, x, :3] = (255, 244, 200); img[y + 1, x, :3] = (220, 190, 130)
        for (dx, dy) in ((1, 0), (-1, 0), (0, -1)):
            img[y + dy, x + dx, :3] = np.clip(img[y + dy, x + dx, :3] * 0.5 + F([255, 230, 170]) * 0.5, 0, 255)

# 4) puklina nahoře: úzká štěrbina v kůře, kudy se přišlo, a schody z kořenů dolů
for y in range(0, 34):
    hw = 5 + y * 0.12
    for x in range(int(CX - hw - 6), int(CX + hw + 7)):
        d = abs(x - CX) - hw
        if d < 0:
            img[y, x, :3] = np.clip(F([200, 226, 230]) * (0.6 + 0.4 * (1 - y / 34)), 0, 255)   # světlo shora
        elif d < 6:
            img[y, x, :3] = np.clip(F([60, 52, 44]) * (0.6 + 0.4 * (d / 6)) + _vn(x, y, 4, 3) * 20, 0, 255)  # okraj kůry
steps = TreePainter(img, lit, seed=4)
for i in range(7):                                                         # kořenové stupně (ploché, přes sebe)
    y = 46 + i * 11
    w = 20 - abs(i - 3) * 1.5
    steps.stroke([(CX - w + (i % 2) * 3, y, 2.6), (CX + w - (i % 2) * 3, y + 2, 2.4)])
steps.composite(dark=(110, 96, 78), light=(246, 232, 204))

# ── voda: dno kokonu je mělká, úplně klidná voda (sklovitá, odráží jen světlo) ──
def floor_in(x, y):
    return ((x - CX) / 74) ** 2 + ((y - 272) / 168) ** 2 < 1 and y > 128


FLOOR = np.zeros((AH, AW), bool)
for y in range(AH):
    for x in range(AW):
        if floor_in(x, y):
            FLOOR[y, x] = True
            g = glow(x, y)
            deep = F([22, 30, 40]) * (1 - g) + F([150, 128, 92]) * g
            refl = max(0.0, 1 - abs(x - LX) / (10 + abs(y - LY) * 0.25)) * max(0.0, 1 - abs(y - LY) / 140)   # sloupek odlesku světla
            col = img[y, x, :3] * 0.35 + deep * 0.65 + F([255, 236, 190]) * refl * 0.35
            edge = 1 - (((x - CX) / 74) ** 2 + ((y - 272) / 168) ** 2)
            if edge < 0.06:
                col = col * 0.7 + F([255, 240, 210]) * 0.12                 # tenký světlý lem u kořenů
            if (y * 5 + x * 7) % 61 == 0 and _vn(x, y, 11, 9) > 0.6:
                col = col + F([40, 36, 26])
            img[y, x, :3] = np.clip(col, 0, 255)


def ring(cx, cy, r, k=0.35):
    for a in range(0, 360, 5):
        X, Y = int(cx + math.cos(math.radians(a)) * r), int(cy + math.sin(math.radians(a)) * r * 0.32)
        if 0 <= X < AW and 0 <= Y < AH:
            img[Y, X, :3] = np.clip(img[Y, X, :3] * (1 - k) + F([255, 236, 190]) * k, 0, 255)


STONE = [F(v) for v in ((34, 34, 32), (62, 60, 54), (96, 92, 80), (136, 130, 108), (178, 170, 138), (222, 212, 176))]
MOSS = [F(v) for v in ((44, 66, 40), (70, 98, 54), (104, 132, 70), (150, 170, 96))]


def st(v, x, y):
    v = max(0.0, min(0.999, v)) * (len(STONE) - 1)
    i = int(v)
    return STONE[i + 1] if (v - i) > (((x * 3 + y * 5) % 7) / 7) * 0.8 + 0.1 and i + 1 < len(STONE) else STONE[i]


def stone(cx, cy, rx, ry, moss=0.6):
    """Plochý kámen v klidné vodě: mech navrchu, mokrý okraj, kruh na hladině."""
    for y in range(int(cy - ry) - 1, int(cy + ry) + 4):
        for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
            if not (0 <= x < AW and 0 <= y < AH):
                continue
            wob = 1 + (_vn(x * 0.9 + cx, y * 0.9, 31, 4) - 0.5) * 0.5          # nepravidelný obrys
            d = (((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy) / ry) ** 2) * wob
            d2 = (((x + .5 - cx) / rx) ** 2 + ((y + .5 - cy - 2.5) / ry) ** 2) * wob
            if d < 1:
                v = 0.6 - (x - cx) / (rx * 2) * 0.35 - (y - cy) / (ry * 2) * 0.25 + (_vn(x, y, 7, 3) - 0.5) * 0.14
                col = st(v, x, y)
                m = _vn(x * 1.6, y * 1.6, 8, 3)
                if m < moss and d < 0.85:
                    mi = min(3, max(0, int((v + (moss - m)) * 3.2)))
                    col = MOSS[mi]                                         # mech jen v chomáčích
                img[y, x, :3] = col
            elif d2 < 1:
                img[y, x, :3] = st(0.12, x, y)
    ring(cx, cy + ry + 1, rx + 3, 0.25)


# dno je brodivé (mělká voda), kameny jen ukazují cestu; stěny kokonu a kořeny jsou neprůchozí
block[:, :] = True
block[FLOOR] = False
block[(mid.cov > 0.5) & (np.arange(AH)[:, None] > 128)] = True
PATH = [(88, 124, 13, 6), (66, 146, 11, 5), (44, 170, 12, 6), (64, 196, 11, 5), (90, 216, 12, 5), (118, 204, 11, 5),
        (134, 212, 12, 6), (108, 236, 11, 5), (90, 256, 13, 6), (62, 274, 11, 5), (38, 298, 14, 7), (112, 344, 11, 5),
        (124, 360, 12, 6), (116, 382, 13, 6)]
for p in PATH:
    stone(p[0], p[1], p[2] * 0.8, p[3] * 0.85, moss=0.5)

# ostrůvek: mech, kolébka z jemných kořínků a v ní poslední světlo
ix, iy, irx, iry = ISLE
stone(ix, iy, irx, iry, moss=0.85)
cradle = TreePainter(img, lambda x, y: (F([255, 236, 190]), 0.15), seed=7)
for k in range(9):
    a = math.pi + k / 8 * math.pi
    x0, y0 = LX + math.cos(a) * 14, iy - 2 + math.sin(a) * 4
    cradle.stroke([(x0, y0, 1.1), (LX + math.cos(a) * 8, LY + 2 + math.sin(a) * 3, 0.8), (LX + math.cos(a) * 3, LY - 4, 0.4)])
cradle.composite(dark=(150, 130, 96), light=(255, 240, 200))
for y in range(LY - 6, LY + 5):                                            # samotné světlo (živé ve FX)
    for x in range(LX - 5, LX + 6):
        d = math.hypot(x - LX, (y - LY) * 1.1)
        if d < 4.5:
            img[y, x, :3] = np.clip(F([255, 250, 230]) * (1 - d / 9) + img[y, x, :3] * (d / 9), 0, 255)

# mechové lůžko vlevo pod obloukem kořene
for y in range(284, 312):
    for x in range(18, 60):
        d = ((x - 38) / 20) ** 2 + ((y - 299) / 11) ** 2
        if d < 1:
            v = 0.35 + (1 - d) * 0.35 - (y - 299) / 40 + (_vn(x * 1.5, y * 1.5, 12, 3) - 0.5) * 0.3
            img[y, x, :3] = MOSS[min(3, max(0, int(v * 4)))]
            if d < 0.7 and _h(x, y, 5) % 41 == 0:
                img[y, x, :3] = (236, 240, 210)                             # drobné bílé kvítky
            if d > 0.86:
                img[y, x, :3] = (img[y, x, :3] * 0.6).astype(np.uint8)       # měkký okraj do vody
            block[y, x] = False
arch = TreePainter(img, lit, seed=8)
bough(arch, chain((-4, 252), (30, 258), (62, 286), (60, 322), (40, 340)), 5.5, 4, twigs=2)
arch.composite(dark=(96, 84, 70), light=(250, 238, 210))

# chůze: schody nahoře
for y in range(34, 130):
    hw = 10 if y < 50 else 16
    block[y, CX - hw:CX + hw] = False

# ── světlo: sloup z pukliny, záře kolem posledního světla, okraj tmy ────────
beam = np.zeros((AH, AW))
for y in range(0, LY):
    t = y / LY
    hw = 4 + t * 16
    for x in range(int(CX - hw), int(CX + hw) + 1):
        beam[y, x] = max(beam[y, x], (1 - abs(x - CX) / hw) ** 1.3 * (0.3 + 0.4 * t))
L.lighten(img, beam, 0.32, (255, 246, 220), steps=8)
pool = np.zeros((AH, AW))
Scene.pool(pool, LX, LY, 70, 60, 1.0)
Scene.pool(pool, LX, iy + 10, 60, 20, 0.8)
L.lighten(img, pool, 0.3, (255, 214, 150), steps=10)
vg = np.zeros((AH, AW)); L.vignette(vg, LX, LY, AW * 0.75, AH * 0.62, k=0.55)
L.darken(img, vg, 0.55, (6, 8, 14), dither=True, steps=10)
for _ in range(110):                                                       # plující světélka (pyl světla)
    x, y = int(rng.integers(6, AW - 6)), int(rng.integers(30, AH - 6))
    img[y, x, :3] = (255, 244, 210) if rng.random() < 0.7 else (200, 236, 230)

sc.export("root_heart", "gen_root_heart.py")
