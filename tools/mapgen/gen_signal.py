"""
Použití:  python tools/mapgen/gen_signal.py
Výstup:   app/src/main/res/drawable-nodpi/root_signal.png, app/src/main/assets/walk/root_signal.txt,
          tools/mapgen/root_signal_debug.png

Zóna 3 (Akt III – Hlubina), lokace 3 z 8 – vedlejší: SIGNÁLNÍ ÚTES (176 × 560, pohled 3/4 shora, docs/adr/0106).
Vysoká mapa, kamera jede s hráčem: po skalní římse podél stěny dutiny se stoupá vzhůru – přes dřevěnou lávku
nad průrvou – až na ostroh, který vybíhá do propasti. Celou dobu vlevo v pozadí, daleko a ve stínu, visí Spící:
kořeny rozlezlé po stropě, krátký mohutný kmen a obrácená koruna, jejíž větve se dělí do jemné sítě a noří se
do zářící zelené mlhy. Dole je vidět jen koruna v mlze, výš kmen s ránou (kopí, šest světel – jen náznak), nahoře
kořeny ve stropě. Na ostrohu signální stanoviště Řádu: lampa s clonou, bouda signalisty, Kniha signálů, lavička,
horní stanice lanovky s lany ke kmeni a uvízlou gondolou. Podél stezky lampy na tyčích a lanové zábradlí.
Vedlejší úkol „Signalista“ (Kesh). Setkání DUCH a PSYCHO. Živé detaily: InteriorFxView.ROOT_SIGNAL.
NODES / EDGES jsou ZDROJ PRAVDY i pro RootSignal.kt. Strom: tools/mapgen/hlubina/tree.py.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import Spr, c, _pick, _h, _vn, ROCK, STEEL, SCREE  # noqa: E402
from scene import Scene  # noqa: E402
from roots import root, bez  # noqa: E402
from tree import TreePainter  # noqa: E402
import order as O, signs as SG  # noqa: E402

AW, AH = 176, 560
TX = 46                                                                   # osa stromu v pozadí
NODES = {
    "su_vstup": (150, 548), "su_cesta1": (138, 484), "su_lavka": (138, 388), "su_cesta2": (136, 306),
    "su_lanovka": (138, 252), "su_kniha": (116, 216), "su_kesh": (124, 192), "su_lampa": (112, 178),
    "su_lavicka": (136, 176), "su_bouda": (136, 152), "su_okraj": (108, 164),
}
EDGES = [("su_vstup", "su_cesta1"), ("su_cesta1", "su_lavka"), ("su_lavka", "su_cesta2"), ("su_cesta2", "su_lanovka"),
         ("su_lanovka", "su_kniha"), ("su_kniha", "su_kesh"), ("su_kesh", "su_lampa"), ("su_kesh", "su_lavicka"),
         ("su_lavicka", "su_bouda"), ("su_lampa", "su_okraj")]
sc = Scene(AW, AH, 0, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(66)


# ── pozadí: zářící mlha zespodu, závoj za kmenem, strop dutiny ───────────────
def mist(x, y):
    d = math.hypot((x - TX) / 150, (y - 470) / 330)
    g = max(0.0, 1 - d) ** 1.2
    col = np.array([4, 8, 9], float) * (1 - g) + np.array([62, 128, 106], float) * g
    veil = math.exp(-((x - TX) / 46) ** 2) * max(0.0, min(1.0, (y - 30) / 60)) * 0.5
    col = col + np.array([18, 40, 34]) * veil
    n = _vn(x * 0.6, y * 1.2, 3, 26) * 0.65 + _vn(x, y, 5, 8) * 0.35
    return col * (0.82 + 0.36 * n)


for y in range(AH):
    for x in range(AW):
        img[y, x, :3] = np.clip(mist(x, y), 0, 255)
for y in range(0, 44):                                                    # strop dutiny
    for x in range(AW):
        if y < 18 + _vn(x, 0, 7, 11) * 22:
            img[y, x] = _pick(ROCK, 0.15 + (_vn(x, y, 8, 4) - 0.5) * 0.25 + y / 44 * 0.12, x, y)

# ── Spící ───────────────────────────────────────────────────────────────────
def haze(x, y):
    return np.clip(mist(x, y), 0, 255), min(0.85, 0.22 + max(0.0, (y - 300) / 260) * 0.6)


tp = TreePainter(img, haze, seed=4)
trunk = []
for i in range(41):
    t = i / 40
    trunk.append((TX + math.sin(t * 2.2) * 3, 36 + t * 150, 8.5 - 2.0 * t + 4 * max(0, 0.08 - t) / 0.08 + 3 * max(0, t - 0.9) / 0.1))
tp.stroke(trunk)
bx, by = trunk[-1][0], trunk[-1][1]
tp.split3 = 0.45
for ang, r, L in ((math.pi / 2 - 1.25, 4.0, 62), (math.pi / 2 - 0.75, 4.6, 72), (math.pi / 2 - 0.25, 5.0, 78),
                  (math.pi / 2 + 0.25, 5.0, 78), (math.pi / 2 + 0.75, 4.6, 72), (math.pi / 2 + 1.25, 4.0, 62)):
    tp.branch(bx, by, ang, r, L, 0, math.pi / 2, spread=(0.38, 0.72), bend=0.05, maxdepth=8, pull=0.018)
tp.split3 = 0.3
tx_, ty_ = trunk[0][0], trunk[0][1]
for ang, r, L in ((-math.pi / 2 - 1.3, 4.5, 60), (-math.pi / 2 - 0.8, 4.0, 50), (-math.pi / 2 + 0.4, 3.8, 40),
                  (-math.pi / 2 + 0.95, 4.2, 55), (-math.pi / 2 + 1.35, 4.6, 70)):
    g = math.pi if math.cos(ang) < 0 else 0.0
    tp.branch(tx_, ty_ + 2, ang, r * 1.3, L * 1.2, 0, g, spread=(0.3, 0.6), bend=0.06, maxdepth=7, clamp_y=6, pull=0.04)
tp.composite()

# rána v kmeni (tlumeně – je to daleko), kopí a šest světel jen jako náznak
for y in range(104, 122):
    q = (y - 104) / 18
    half = 1.2 * math.sin(q * math.pi)
    for x in range(int(TX - 6 - half), int(TX - 6 + half) + 1):
        img[y, x, :3] = (96, 190, 140) if abs(x - (TX - 6)) <= half * 0.5 else (52, 120, 92)
for y in range(92, 140):
    for x in range(TX - 22, TX + 8):
        d = math.hypot((x - (TX - 6)) / 14, (y - 113) / 22)
        if d < 1 and tp.cov[y, x] < 0.5:
            a = round((1 - d) * 3) / 3 * 0.22
            img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([90, 200, 150]) * a, 0, 255)
for i in range(10):
    img[88 + i, TX + 7 + i // 3, :3] = (150, 156, 164) if i < 2 else (70, 78, 84)
for (ex, ey) in ((TX + 2, 96), (TX + 4, 96), (TX + 1, 98), (TX + 5, 98), (TX + 2, 100), (TX + 4, 100)):
    img[ey, ex, :3] = (110, 200, 150)
for (x, y, lvl) in [(x, y, 0) for (x, y) in tp.tips]:                     # světélka na konečcích (jen některá)
    if 0 <= int(x) < AW and 230 <= int(y) < AH and rng.random() < 0.25:
        img[int(y), int(x), :3] = np.clip(img[int(y), int(x), :3] * 0.4 + np.array([150, 220, 240]) * 0.6, 0, 255)
# zelená mlha z rány a vrstvy mlhy před korunou (měkké, 4 stupně)
for y in range(110, AH):
    for x in range(AW):
        stream = max(0.0, 1 - abs(x - (TX - 7 - (y - 110) * 0.1)) / (2.5 + (y - 110) * 0.22)) * max(0.0, 1 - (y - 110) / 200)
        m = _vn(x * 0.7 + 40, y * 1.6, 21, 22) * 0.7 + _vn(x, y * 1.2, 22, 8) * 0.3
        layer = max(0.0, (m - 0.62) * 1.8) * min(1.0, max(0.0, (y - 220) / 80))
        a = math.floor(min(0.5, stream * 0.45 + layer * 0.4) * 4) / 4
        if a > 0:
            img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([86, 170, 136]) * a, 0, 255)

# ── popředí: skalní římsa podél stěny a ostroh ──────────────────────────────
def brink(y):
    """Levá hrana pochozí plochy (vlevo od ní propast); None = v tomto řádku není podlaha."""
    if y < 104:
        return None
    if y < 240:                                                           # ostroh vybíhá doleva do propasti
        return 94 + ((y - 172) / 68) ** 2 * 26 + (_vn(0, y, 31, 9) - 0.5) * 6
    if 372 <= y <= 404:                                                   # průrva – jen lávka
        return None
    return 112 + math.sin(y / 40) * 9 + (_vn(0, y, 32, 11) - 0.5) * 8


def wall_x(y):
    return 160 + math.sin(y / 31) * 3 + (_vn(1, y, 33, 9) - 0.5) * 5


block[:, :] = True
floor = np.zeros((AH, AW), bool)
for y in range(AH):
    b = brink(y)
    wx = int(wall_x(y))
    for x in range(wx, AW):                                               # stěna dutiny vpravo (svislá skála)
        v = 0.34 + (_vn(x * 2, y * 0.5, 41, 5) - 0.5) * 0.3 - (x - wx) * 0.01
        if abs(_vn(x, y * 0.6, 42, 7) - 0.5) < 0.03:
            v = 0.1
        img[y, x] = _pick(ROCK, max(0.0, min(1.0, v)), x, y)
        if x == wx:
            img[y, x] = ROCK[4]
    if b is None:
        continue
    b = int(b)
    for x in range(max(0, b), wx):
        v = 0.46 + (_vn(x * 1.2, y, 23, 6) - 0.5) * 0.3 + (_vn(x, y, 24, 2) - 0.5) * 0.14
        if _h(x, y, 25) % 37 == 0:
            v += 0.22
        img[y, x] = _pick(SCREE, max(0.0, min(1.0, v)), x, y)
        floor[y, x] = True
        if x > b + 3 and x < wx - 2:
            block[y, x] = False
    for x in range(max(0, b), min(wx, b + 2)):                            # hrana nad propastí
        img[y, x] = _pick(ROCK, 0.6, x, y)
    for x in range(max(0, b - 3), max(0, b)):                             # stinná boční hrana skály
        img[y, x] = ROCK[1]
# spodní (jižní) hrana ostrohu: tloušťka skály pod ním
for x in range(AW):
    for y in range(236, 250):
        if not floor[y, x] and floor[y - 1, x] if y > 0 else False:
            for k in range(10):
                if y + k < AH and not floor[y + k, x]:
                    img[y + k, x] = _pick(ROCK, 0.3 - k * 0.03, x, y + k)

# lávka přes průrvu
for y in range(368, 408):
    for x in range(118, 160):
        edge = x in (118, 119, 158, 159)
        seam = (y - 368) % 5 == 0
        img[y, x] = _pick(O.WOOD_POLE, 0.2 if edge or seam else 0.62 + (_vn(x, y, 81, 2) - 0.5) * 0.3, x, y)
        floor[y, x] = True
        if 121 < x < 157:
            block[y, x] = False
    for xr in (117, 160):
        img[y, xr] = c(0xC8BE9C) if y % 2 else c(0x8A8268)                 # lanové zábradlí
for y in range(408, 412):                                                 # stín lávky do průrvy
    for x in range(118, 160):
        img[y, x, :3] = (img[y, x, :3] * 0.55).astype(np.uint8)
# lanové zábradlí podél hrany stezky: sloupky po 22 px a prověšené lano
prev = None
for y in range(250, AH, 22):
    b = brink(y)
    if b is None:
        prev = None
        continue
    x = int(b) + 4
    for k in range(7):
        img[y - k, x] = O.WOOD_POLE[2]; img[y - k, x + 1] = O.WOOD_POLE[0]
    if prev:
        for t in np.linspace(0, 1, 30):
            X = int(prev[0] + (x - prev[0]) * t); Y = int(prev[1] - 6 + (y - prev[1]) * t + math.sin(t * math.pi) * 3)
            img[Y, X] = c(0xB8AE8C)
    prev = (x, y)

# kořeny (3/8): z propasti přes hranu na stezku a ostroh
root(sc, bez((96, 214), (106, 226), (112, 238)), 8, 4, lift=0.6, d=0.45)          # kořen přes hranu ostrohu
root(sc, bez((106, 470), (114, 480), (122, 500)), 7, 3, lift=0.5, d=0.45)          # a přes hranu stezky

# ── předměty ────────────────────────────────────────────────────────────────
sc.place(O.signal_lamp(), 108, 172, foot=(24, 6))
sc.place(O.signal_hut(2), 136, 144, foot=(46, 18))
hut_sign = SG.plate("SIGNÁL", c(0xE8E2CC), c(0x1E2A44), seed=31, pad=2, wear=0.0)
sc.place(O.mount_plate(hut_sign, posts=8), 150, 236, foot=(4, 2))
sc.place(O.lectern_book(), 108, 210, foot=(8, 4))
sc.place(O.bench(), 140, 184, foot=(26, 4))
sc.place(O.cable_pylon(54), 126, 244, foot=(18, 5))
pl = SG.plate("LANOVKA", c(0x141414), c(0xE2BA34), seed=5, pad=3, wear=0.0)
sc.place(O.mount_plate(pl, posts=8), 146, 262, foot=(4, 2))
for (y,) in ((290,), (440,), (520,)):                                    # lampy na tyčích podél stezky
    lamp = Spr(8, 26)
    for yy in range(4, 26):
        lamp.px(3, yy, O.WOOD_POLE[3]); lamp.px(4, yy, O.WOOD_POLE[1])
    lamp.rect(1, 0, 6, 4, STEEL[2]); lamp.rect(2, 2, 5, 4, O.SODIUM)
    sc.place(lamp, int(wall_x(y)) - 8, y, foot=(4, 2))
for (x, y, sd) in ((150, 200, 3), (152, 212, 5)):
    sc.place(O.army_crate(sd), x, y, pad=0)
# lana lanovky z ostrohu ke kmeni a uvízlá gondola
for (y0, y1) in ((192, 132), (196, 138)):
    for t in np.linspace(0, 1, 300):
        X = int(124 + (TX + 9 - 124) * t); Y = int(y0 + (y1 - y0) * t + math.sin(t * math.pi) * 10)
        img[Y, X, :3] = (28, 34, 38) if t < 0.5 else (34, 52, 48)
gx, gy = 80, 168
for y in range(gy, gy + 8):
    for x in range(gx, gx + 7):
        img[y, x] = _pick(O.YELLOW, 0.45 - (x - gx) * 0.04, x, y) if not (y in (gy + 2, gy + 3) and 0 < x - gx < 6) else c(0x14181C)
for y in range(gy - 5, gy):
    img[y, gx + 3] = STEEL[2]

sc.shadows(floor)
sc.draw_sprites()

fungi = []
for _ in range(9000):
    x, y = int(rng.integers(2, AW - 3)), int(rng.integers(100, AH - 4))
    if sc.depth[y, x] and not sc.depth[y, x + 2] and len(fungi) < 30:
        fungi.append((x, y))
        img[y, x, :3] = (150, 210, 255); img[y, x - 1, :3] = (80, 140, 220); img[y + 1, x, :3] = (40, 70, 120)

# vstup dole vpravo (lávka z Pozorovatelny B)
block[536:AH, 140:160] = False
vista = img.copy()
lamps_xy = [(int(wall_x(y)) - 8, y - 22) for y in (290, 440, 520)]
sc.light(
    warm=[(136, 140, 30, 20, 0.9), (108, 166, 22, 14, 0.8), (108, 206, 14, 10, 0.5)] + [(x, y + 18, 26, 16, 0.8) for (x, y) in lamps_xy],
    cold=[(x, y, 7, 6, 0.6) for (x, y) in fungi] + [(TX - 6, 113, 14, 16, 0.5)],
    dark_k=0.32, warm_k=0.32, cold_k=0.15)
keep = ~floor                                                             # výhled (vše mimo podlahu) se nestmívá
img[keep, :3] = (vista[keep, :3] * 0.85 + img[keep, :3] * 0.15).astype(np.uint8)
sc.export("root_signal", "gen_signal.py")
print("LAMPS", lamps_xy, "FUNGI", fungi[:8])
