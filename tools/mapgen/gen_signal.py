"""
Použití:  python tools/mapgen/gen_signal.py
Výstup:   app/src/main/res/drawable-nodpi/root_signal.png, app/src/main/assets/walk/root_signal.txt,
          tools/mapgen/root_signal_debug.png

Zóna 3 (Akt III – Hlubina), lokace 3 z 8 – vedlejší: SIGNÁLNÍ ÚTES (176 × 352, pohled 3/4 shora, docs/adr/0106).
Skalní ostroh na okraji obrovské propasti; přes celou horní polovinu mapy výhled na Spící: kořeny rozlezlé po stropě
dutiny, kmen visící do prázdna s ránou, ze které teče zelená mlha, a dole obrácená koruna, která se ztrácí v mlze.
V kmeni se blýská kopí a u něj šest drobných zelených světel (Trpitel – zatím jen náznak). Od ostrohu vedou ke kmeni
lana lanovky, uprostřed visí uvízlá gondola. Na ostrohu signální stanoviště Řádu: signální lampa s clonou,
bouda signalisty (kouř z komínku, světlo v okně), stojan s KNIHOU SIGNÁLŮ, lavička k propasti, horní stanice
lanovky. Vedlejší úkol „Signalista“ (Kesh). Vstup zprava po lávce z Pozorovatelny B.
Setkání DUCH, LÉTAVÝ. Živé detaily: InteriorFxView.ROOT_SIGNAL. NODES / EDGES jsou ZDROJ PRAVDY i pro RootSignal.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import Spr, c, _pick, _h, _vn, ROCK, STEEL, SCREE, BAYER  # noqa: E402
from scene import Scene  # noqa: E402
from roots import root, bez  # noqa: E402
import order as O, signs as SG  # noqa: E402

AW, AH = 176, 352
LEDGE = 214                                                               # odkud začíná ostroh (nad ním výhled)
NODES = {
    "su_vstup": (166, 326), "su_bouda": (138, 318), "su_kesh": (96, 266), "su_lampa": (70, 262),
    "su_kniha": (52, 284), "su_lavicka": (112, 250), "su_lanovka": (30, 300), "su_okraj": (88, 236),
}
EDGES = [("su_vstup", "su_bouda"), ("su_bouda", "su_kesh"), ("su_kesh", "su_lampa"), ("su_lampa", "su_kniha"),
         ("su_kesh", "su_lavicka"), ("su_lavicka", "su_okraj"), ("su_kniha", "su_lanovka"), ("su_lampa", "su_okraj")]
sc = Scene(AW, AH, 0, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(66)

# ── výhled: záře mlhy zpoza a zespodu, tmavý strop, vzdálené stěny dutiny ──
for y in range(AH):
    for x in range(AW):
        d = math.hypot((x - 92) / 120, (y - 200) / 150)
        g = max(0.0, 1 - d) ** 1.3
        col = np.array([5, 9, 10], float) * (1 - g) + np.array([58, 132, 106], float) * g
        n = _vn(x, y * 1.8, 3, 18) * 0.6 + _vn(x, y, 5, 6) * 0.4
        col = col * (0.86 + 0.28 * n)
        img[y, x, :3] = np.clip(col, 0, 255)
for y in range(0, 30):                                                    # strop dutiny
    for x in range(AW):
        edge = 12 + _vn(x, 0, 7, 9) * 14
        if y < edge:
            img[y, x] = _pick(ROCK, 0.18 + (_vn(x, y, 8, 4) - 0.5) * 0.25 + (y / edge) * 0.12, x, y)
for side in (0, 1):                                                       # vzdálené stěny dutiny
    for y in range(20, LEDGE + 20):
        wv = 8 + _vn(side * 50, y, 9, 20) * 18 + (y - 20) * 0.05
        for x in range(int(wv)):
            X = x if side == 0 else AW - 1 - x
            img[y, X, :3] = np.clip(img[y, X, :3] * 0.45 + np.array([8, 16, 15]) * 0.5, 0, 255)

# ── Spící: kořeny po stropě, kmen s ránou, obrácená koruna ──────────────────
TREE = [c(0x030504), c(0x080C0B), c(0x0E1614), c(0x16201D), c(0x22302B), c(0x324540)]
TX, T0, T1 = 92, 26, 118
mask = np.zeros((AH, AW), bool)


def limb(pts, w0, w1, haze=0.0):
    """Silueta větve/kořene: skoro černá kůra s jemnými rýhami, vzdušná perspektiva (haze) do barvy mlhy."""
    n = len(pts)
    for i in range(n - 1):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        t = i / (n - 1)
        w = (w0 + (w1 - w0) * t) * (1 + 0.1 * math.sin(t * 17 + w0))
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln
        steps = max(1, int(ln * 1.6))
        for s_ in range(steps):
            cx, cy = x0 + dx * s_ / steps, y0 + dy * s_ / steps
            for k in np.linspace(-w / 2, w / 2, max(2, int(w * 2.4))):
                X, Y = int(round(cx + nx * k)), int(round(cy + ny * k))
                if not (0 <= X < AW and 0 <= Y < AH) or mask[Y, X] and haze > 0.3:
                    continue
                u = k / (w / 2 + 0.01)
                v = 0.4 - 0.25 * u + (_vn(X * 1.6, Y * 0.7, 13, 3) - 0.5) * 0.3
                if abs(_vn(cx * 0.8 + k * 2.5, cy * 0.8, 14, 3) - 0.5) < 0.05:
                    v -= 0.2                                              # podélné rýhy kůry
                col = _pick(TREE, max(0.0, min(1.0, v)), X, Y)[:3].astype(float)
                img[Y, X, :3] = np.clip(col * (1 - haze) + img[Y, X, :3] * haze, 0, 255)
                mask[Y, X] = True


def bz(p0, p1, p2, n=40):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in np.linspace(0, 1, n)]


def branch(x, y, ang, ln, w, lvl, down=True):
    if lvl > 5 or ln < 3 or w < 0.5:
        return
    pts = []
    a = ang
    for _ in range(9):
        pts.append((x, y))
        a += rng.uniform(-0.2, 0.2)
        if not down and y < 16:
            a = a * 0.7 + (0 if math.cos(a) > 0 else math.pi) * 0.3       # po stropě se kořen stáčí do strany
        x += math.cos(a) * ln / 8; y += math.sin(a) * ln / 8
        y = max(y, 6) if not down else y
    haze = min(0.6, 0.06 * lvl + (max(0, y - 160) / 140 if down else 0.0))
    limb(pts, w, max(0.5, w * 0.6), haze)
    tips.append((x, y, lvl))
    for s_ in (-1, 1):
        if rng.random() < 0.88:
            branch(x, y, ang + s_ * rng.uniform(0.25, 0.6), ln * rng.uniform(0.6, 0.78), w * 0.62, lvl + 1, down)


tips = []
for ang in (math.pi / 2 - 1.3, math.pi / 2 - 0.7, math.pi / 2 - 0.2, math.pi / 2 + 0.25, math.pi / 2 + 0.75, math.pi / 2 + 1.3):
    branch(TX + math.cos(ang) * 5, T1, ang, 48, 10, 0, True)              # obrácená koruna dolů
for ang in (-math.pi / 2 - 1.2, -math.pi / 2 - 0.7, -math.pi / 2 - 0.25, -math.pi / 2 + 0.25, -math.pi / 2 + 0.7, -math.pi / 2 + 1.2):
    branch(TX + math.cos(ang) * 5, T0 + 4, ang, 30, 12, 0, False)          # kořeny nahoru a po stropě
limb([(TX + math.sin(i / 3) * 1.2, T0 + i * (T1 - T0) / 24) for i in range(25)], 22, 16, 0.0)              # kmen
# světelný lem: pixely stromu, které mají vlevo dole mlhu, chytnou její záři
for y in range(1, AH - 1):
    for x in range(1, AW - 1):
        if mask[y, x] and not mask[y + 1, x - 1] and T0 + 6 < y < LEDGE:
            img[y, x, :3] = np.clip(img[y, x, :3] * 0.65 + np.array([80, 170, 130]) * 0.35, 0, 255)
# rána v kmeni: úzká rozštěpená štěrbina se září
for y in range(66, 90):
    q = (y - 66) / 24
    half = 1.6 * math.sin(q * math.pi)
    for x in range(int(TX - 5 - half), int(TX - 5 + half) + 1):
        img[y, x, :3] = (170, 255, 200) if abs(x - (TX - 5)) < half * 0.5 else (70, 190, 130)
for y in range(56, 100):                                                  # halo kolem rány
    for x in range(TX - 20, TX + 10):
        d = math.hypot((x - (TX - 5)) / 14, (y - 78) / 20)
        if d < 1 and not mask[y, x]:
            a = round((1 - d) * 3) / 3 * 0.4
            img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([110, 230, 160]) * a, 0, 255)
# kopí a šest světel (Trpitel – jen náznak)
for i in range(16):
    img[54 + i, TX + 8 + i // 3, :3] = (210, 216, 224) if i < 3 else (120, 126, 136)
for (ex, ey) in ((TX + 3, 62), (TX + 6, 62), (TX + 2, 64), (TX + 7, 64), (TX + 3, 66), (TX + 6, 66)):
    img[ey, ex, :3] = (170, 255, 200)
for (x, y) in ((TX - 10, 40), (TX + 10, 52), (TX - 9, 104)):              # kotvy Řádu v kmeni
    img[y, x, :3] = (255, 200, 110)
for (x, y, lvl) in tips:                                                  # bioluminiscence na konečcích koruny
    if lvl >= 3 and 0 <= int(x) < AW and 120 <= int(y) < LEDGE and rng.random() < 0.6:
        img[int(y), int(x), :3] = (170, 230, 255)
# zelená mlha: stéká z rány a vrství se přes korunu (měkké pásy, 4 stupně průhlednosti)
for y in range(76, LEDGE + 24):
    for x in range(AW):
        stream = max(0.0, 1 - abs(x - (TX - 6 - (y - 76) * 0.12)) / (3 + (y - 76) * 0.3)) * max(0.0, 1 - (y - 76) / 170)
        m = _vn(x + 30, y * 2.2, 21, 14) * 0.7 + _vn(x, y * 1.5, 22, 6) * 0.3
        layer = max(0.0, (m - 0.6) * 2.0) * min(1.0, max(0.0, (y - 150) / 50))
        a = min(0.6, stream * 0.7 + layer * 0.45)
        a = math.floor(a * 4) / 4
        if a > 0:
            img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([92, 190, 150]) * a, 0, 255)

# lana lanovky z ostrohu ke kmeni a uvízlá gondola
for (x0, y0, x1, y1) in ((26, 262, TX - 9, 96), (32, 262, TX - 8, 100)):
    for t in np.linspace(0, 1, 400):
        X = int(x0 + (x1 - x0) * t); Y = int(y0 + (y1 - y0) * t + math.sin(t * math.pi) * 10)
        img[Y, X, :3] = (30, 36, 40) if t < 0.5 else (24, 38, 36)
gx, gy = 56, 180
for y in range(gy, gy + 9):
    for x in range(gx, gx + 8):
        img[y, x] = _pick(O.YELLOW, 0.5 - (x - gx) * 0.04, x, y) if not (y in (gy + 2, gy + 3) and 1 < x - gx < 7) else c(0x14181C)
for y in range(gy - 4, gy):
    img[y, gx + 4] = STEEL[2]

# ── ostroh (podlaha) ────────────────────────────────────────────────────────
def ledge_top(x):
    return LEDGE + 18 + math.sin(x / 13) * 6 + (_vn(x, 0, 33, 7) - 0.5) * 10 - max(0, 40 - x) * 0.4


block[:, :] = True
for x in range(AW):
    top = int(ledge_top(x))
    for y in range(top, AH):
        v = 0.48 + (_vn(x * 1.2, y, 23, 6) - 0.5) * 0.3 + (_vn(x, y, 24, 2) - 0.5) * 0.14
        if _h(x, y, 25) % 37 == 0:
            v += 0.22
        img[y, x] = _pick(SCREE, max(0.0, min(1.0, v)), x, y)
        if y > top + 4:
            block[y, x] = False
    for y in range(top, top + 3):                                         # hrana ostrohu světlejší
        img[y, x] = _pick(ROCK, 0.55 - (y - top) * 0.1, x, y)
    for y in range(top - 6, top):                                         # spodní stín hrany (tloušťka skály není vidět – jen tma)
        if y >= 0 and y < AH:
            img[y, x, :3] = (img[y, x, :3] * (0.7 + (top - y) * 0.05)).astype(np.uint8)

# kořeny (3/8): lezou přes hranu ostrohu z propasti
root(sc, bez((60, 220), (50, 260), (20, 290)), 10, 5, lift=0.6, d=0.45)
root(sc, bez((150, 226), (160, 262), (176, 280)), 11, 6, lift=0.6, d=0.45)
for (p0, p1, p2, w) in (((50, 260), (64, 276), (70, 300), 3), ((160, 262), (140, 272), (124, 270), 3)):
    root(sc, bez(p0, p1, p2, 30), w, 1.2, solid=False, d=0.5)

# ── předměty na ostrohu ─────────────────────────────────────────────────────
sc.place(O.signal_lamp(), 70, 256, foot=(24, 6))
sc.place(O.signal_hut(2), 140, 304, foot=(46, 18))
hut_sign = SG.plate("SIGNÁLNÍ STANOVIŠTĚ", c(0xE8E2CC), c(0x1E2A44), seed=31, pad=2, wear=0.0)
if hut_sign.w > 70:
    hut_sign = SG.plate("SIGNÁL", c(0xE8E2CC), c(0x1E2A44), seed=31, pad=2, wear=0.0)
sc.place(O.mount_plate(hut_sign, posts=8), 92, 318, foot=(4, 2))
sc.place(O.lectern_book(), 50, 290, foot=(8, 4))
sc.place(O.bench(), 112, 256, foot=(26, 4))
sc.place(O.cable_pylon(54), 28, 290, foot=(18, 5))
pl = SG.plate("LANOVKA", c(0x141414), c(0xE2BA34), seed=5, pad=3, wear=0.0)
sc.place(O.mount_plate(pl, posts=8), 30, 318, foot=(4, 2))
for (x, y, sd) in ((122, 334, 3), (108, 340, 5)):
    sc.place(O.army_crate(sd), x, y, pad=0)
sc.shadows(~block)
sc.draw_sprites()

fungi = []
for _ in range(6000):
    x, y = int(rng.integers(2, AW - 3)), int(rng.integers(LEDGE, 340))
    if sc.depth[y, x] and not sc.depth[y, x + 2] and len(fungi) < 30:
        fungi.append((x, y))
        img[y, x, :3] = (150, 210, 255); img[y, x - 1, :3] = (80, 140, 220); img[y + 1, x, :3] = (40, 70, 120)

block[:, :2] = True; block[:, AW - 2:] = True
block[318:334, AW - 2:] = False                                           # vstup zprava (lávka)
for y in range(318, 334):
    for x in range(160, AW):
        img[y, x] = STEEL[2] if (x + y) % 3 else STEEL[1]

vista = img[:LEDGE + 10].copy()
sc.light(
    warm=[(140, 300, 32, 20, 0.9), (70, 252, 24, 16, 0.8), (50, 286, 14, 10, 0.5)],
    cold=[(x, y, 7, 6, 0.6) for (x, y) in fungi] + [(TX - 4, 78, 16, 14, 0.6)],
    dark_k=0.3, warm_k=0.32, cold_k=0.15)
img[:LEDGE + 10, :, :3] = (vista[:, :, :3] * 0.8 + img[:LEDGE + 10, :, :3] * 0.2).astype(np.uint8)   # výhled se nestmívá
sc.export("root_signal", "gen_signal.py")
print("FUNGI", fungi[:10])
