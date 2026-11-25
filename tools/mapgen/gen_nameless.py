"""
Použití:  python tools/mapgen/gen_nameless.py
Výstup:   app/src/main/res/drawable-nodpi/root_nameless.png, app/src/main/assets/walk/root_nameless.txt,
          tools/mapgen/root_nameless_debug.png

Zóna 3 (Akt III – Hlubina), lokace 5 z 8: SVAH BEZEJMENNÝCH (176 × 560, kamera jede s hráčem, docs/adr/0106).
Dvě perspektivy a dvě úrovně: hráč sestupuje po širokém suťovém svahu (zhruba 80 % šířky, pohled 3/4 shora), vpravo
za hranou srázu je úzký výhled (20 %) na Spící – kmen visící do prázdna, rána, kopí, šest světel jen náznakem –
a pod korunou v mlze protější kopec, ze kterého se těla rozbíhají na všechny strany, pryč od stromu.
Výtah nebyl jediná cesta ven: vlevo je stěna dutiny s puklinami a jeskyněmi, kudy chtěli výpravy utéct. U každého
ústí leží hromady těl hlavou dovnitř; kořeny pukliny zarostly a zavřely. Těla na našem svahu jsou různá (plazí se,
natažená ruka, na zádech, na boku, v dece, sedící; kombinéza Řádu, kabát, bunda, technická; batohy, přilby) a míří
pryč od stromu – ke stěně a nahoru. Devět se štítky V7 (Keshova výprava). Trosky Řádu: kolíky s přetrženým lanem,
zlámaný stativ s baterkou, která pořád svítí, sčítací tabule na sloupcích, NEPOČÍTEJTE JE přes skalní desku na
stezce, vodočetná lať VODA ve vypité tůňce. Kořeny pijí pramínky. Dole schody Lumenců (Chrám) a kolíky do mlhy (Les).
Setkání DUCH a ZEMĚ. Živé detaily: InteriorFxView.ROOT_NAMELESS. NODES / EDGES jsou ZDROJ PRAVDY i pro RootNameless.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import Spr, c, _pick, _h, _vn, ROCK, SCREE, BARK, STEEL  # noqa: E402
from scene import Scene  # noqa: E402
from roots import root, bez  # noqa: E402
from tree import TreePainter  # noqa: E402
import order as O, lumen as LU, signs as SG  # noqa: E402

AW, AH = 176, 560
TX = 164                                                                  # osa kmene ve výhledu
CROWN = (158, 390)                                                        # střed koruny (odtud utíkali)
NODES = {
    "sb_vstup": (62, 24), "sb_kolik": (94, 78), "sb_pukl1": (44, 100), "sb_teodolit": (114, 136), "sb_rada": (70, 186),
    "sb_pukl2": (44, 238), "sb_tabule": (112, 250), "sb_cesta": (72, 300), "sb_voda": (112, 372), "sb_pukl3": (44, 386),
    "sb_v7": (80, 420), "sb_vyhled": (130, 470), "sb_cesta2": (70, 492), "sb_chram": (44, 546), "sb_les": (110, 548),
}
EDGES = [("sb_vstup", "sb_kolik"), ("sb_vstup", "sb_pukl1"), ("sb_kolik", "sb_teodolit"), ("sb_kolik", "sb_rada"),
         ("sb_pukl1", "sb_rada"), ("sb_teodolit", "sb_rada"), ("sb_rada", "sb_pukl2"), ("sb_rada", "sb_tabule"),
         ("sb_rada", "sb_cesta"), ("sb_tabule", "sb_cesta"), ("sb_pukl2", "sb_cesta"), ("sb_cesta", "sb_voda"),
         ("sb_cesta", "sb_pukl3"), ("sb_cesta", "sb_v7"), ("sb_voda", "sb_v7"), ("sb_pukl3", "sb_v7"),
         ("sb_v7", "sb_vyhled"), ("sb_v7", "sb_cesta2"), ("sb_vyhled", "sb_cesta2"), ("sb_cesta2", "sb_chram"),
         ("sb_cesta2", "sb_les")]
sc = Scene(AW, AH, 0, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(55)


# ── výhled vpravo: mlha, Spící, protější kopec pod korunou ──────────────────
def mist(x, y):
    g = max(0.0, 1 - math.hypot((x - CROWN[0]) / 120, (y - 470) / 260)) ** 1.3
    col = np.array([4, 8, 9], float) * (1 - g) + np.array([58, 122, 102], float) * g
    veil = math.exp(-((x - TX) / 16) ** 2) * (0.5 + 0.5 * min(1.0, y / 200))   # závoj mlhy za kmenem (silueta)
    col = col + np.array([34, 74, 62]) * veil
    n = _vn(x * 0.6, y * 1.2, 3, 26) * 0.65 + _vn(x, y, 5, 8) * 0.35
    return col * (0.82 + 0.36 * n)


for y in range(AH):
    for x in range(AW):
        img[y, x, :3] = np.clip(mist(x, y), 0, 255)


def haze(x, y):
    return np.clip(mist(x, y), 0, 255), min(0.82, 0.2 + max(0.0, (y - 260) / 200) * 0.55)


tp = TreePainter(img, haze, seed=9)
trunk = [(TX + math.sin(t * 2.4) * 2, -10 + t * 330, 9.5 - 1.8 * t + 3.5 * max(0, t - 0.9) / 0.1) for t in np.linspace(0, 1, 50)]
tp.stroke(trunk)
bx, by = trunk[-1][0], trunk[-1][1]
tp.split3 = 0.45
for ang, r, L in ((math.pi / 2 - 1.2, 4.2, 70), (math.pi / 2 - 0.6, 4.8, 80), (math.pi / 2, 5.0, 86),
                  (math.pi / 2 + 0.6, 4.8, 80), (math.pi / 2 + 1.2, 4.2, 70)):
    tp.branch(bx, by, ang, r, L, 0, math.pi / 2, spread=(0.38, 0.72), bend=0.05, maxdepth=8, pull=0.018)
tp.composite()
for y in range(150, 172):                                                 # rána, kopí, šest světel – jen náznak
    half = 1.4 * math.sin((y - 150) / 22 * math.pi)
    for x in range(int(TX - 6 - half), int(TX - 6 + half) + 1):
        img[y, x, :3] = (96, 190, 140) if abs(x - (TX - 6)) <= half * 0.5 else (52, 120, 92)
for i in range(12):
    img[136 + i, TX + 6 + i // 3, :3] = (150, 156, 164) if i < 2 else (70, 78, 84)
for (ex, ey) in ((TX + 1, 142), (TX + 3, 142), (TX, 144), (TX + 4, 144), (TX + 1, 146), (TX + 3, 146)):
    img[ey, ex, :3] = (110, 200, 150)
for y in range(160, AH):                                                  # zelená mlha z rány dolů
    for x in range(120, AW):
        stream = max(0.0, 1 - abs(x - (TX - 7 - (y - 160) * 0.06)) / (2.5 + (y - 160) * 0.12)) * max(0.0, 1 - (y - 160) / 220)
        a = math.floor(min(0.5, stream * 0.45) * 4) / 4
        if a > 0:
            img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([86, 170, 136]) * a, 0, 255)


# protější kopec pod korunou (z boku) – těla se z něj rozbíhají pryč od stromu
def Hfar(x):
    return 440 - (x - 120) * 0.9 + (_vn(x, 0, 61, 9) - 0.5) * 5


HILL = [c(0x16140F), c(0x221F19), c(0x2F2A22), c(0x3E372D), c(0x4E463A), c(0x625849)]
for x in range(110, AW):
    top = Hfar(x)
    for y in range(max(0, int(top)), AH):
        d = y - top
        v = 0.44 + (_vn(x * 1.1, y * 0.7, 62, 6) - 0.5) * 0.3 + (0.2 if d < 2 else 0)
        if (d + _vn(x, 0, 63, 12) * 6) % 12 < 1.1:
            v = 0.14
        col = np.array(_pick(HILL, max(0.0, min(1.0, v)), x, y)[:3], float) + np.array([30, 70, 56]) * 0.25
        hz = 0.45 + 0.3 * max(0.0, (y - 480) / 80)
        img[y, x, :3] = np.clip(col * (1 - hz) + np.clip(mist(x, y), 0, 255) * hz, 0, 255)


def far_figure(x, y, s, ang):
    """Drobná postava: tělo podél směru útěku, světlejší hlava vpředu, natažená ruka."""
    ux, uy = math.cos(ang), math.sin(ang)
    hz = 0.4
    body, head = np.array([14, 14, 12], float), np.array([140, 132, 112], float)
    L = 2.0 * s
    for i in range(int(L * 2) + 1):
        t = i / 2
        X, Y = int(round(x + ux * t)), int(round(y + uy * t))
        if 0 <= X < AW and 0 <= Y < AH:
            col = head if t >= L - 0.5 else body
            img[Y, X, :3] = np.clip(col * (1 - hz) + img[Y, X, :3] * hz, 0, 255)
    for t in np.linspace(L, L + s, 3):
        X, Y = int(round(x + ux * t + uy)), int(round(y + uy * t - ux))
        if 0 <= X < AW and 0 <= Y < AH:
            img[Y, X, :3] = np.clip(body * (1 - hz) + img[Y, X, :3] * hz, 0, 255)


far = 0
for _ in range(600):
    x, y = rng.uniform(112, AW), rng.uniform(380, AH)
    if y < Hfar(x) + 3:
        continue
    ang = math.atan2(y - CROWN[1], x - CROWN[0]) + rng.uniform(-0.4, 0.4)      # pryč od koruny
    far_figure(x, y, 1.0 + (y - 380) / 180 * 1.4, ang)
    far += 1
    if far > 120:
        break
vista = img.copy()

# ── náš svah (80 %): stěna dutiny vlevo s puklinami a jeskyněmi, sráz vpravo ──
MOUTHS = [(100, 26), (238, 30), (386, 28), (500, 20)]                    # (y středu, výška ústí)


def wall_x(y):
    return 24 + math.sin(y / 29) * 4 + (_vn(0, y, 71, 13) - 0.5) * 6


def bump(y, y0, r, k):
    return k * max(0.0, 1 - ((y - y0) / r) ** 2)


def edge(y):
    return 138 + math.sin(y / 41) * 5 + (_vn(0, y, 64, 11) - 0.5) * 6 + bump(y, 470, 26, 12) - bump(y, 300, 40, 8)


def seg_dist(x, y):
    best = 1e9
    for a, b in EDGES:
        (x0, y0), (x1, y1) = NODES[a], NODES[b]
        dx, dy = x1 - x0, y1 - y0
        t = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy)))
        best = min(best, math.hypot(x - x0 - dx * t, y - y0 - dy * t))
    return best


block[:, :] = True
floor = np.zeros((AH, AW), bool)
TRAIL = np.full((AH, AW), 99.0)
for y in range(AH):
    W, E = wall_x(y), edge(y)
    for x in range(0, min(AW, int(E) + 12)):
        if x < W:                                                         # stěna dutiny (svislá skála)
            v = 0.5 + (_vn(x * 2, y * 0.5, 41, 5) - 0.5) * 0.3 + (x - W) * 0.012
            if abs(_vn(x, y * 0.6, 42, 7) - 0.5) < 0.03:
                v = 0.08
            img[y, x] = _pick(ROCK, max(0.0, min(1.0, v)), x, y)
            if x > W - 2:
                img[y, x] = ROCK[4]
            continue
        if x < E:
            TRAIL[y, x] = seg_dist(x, y)
            down = y / AH
            v = 0.52 - down * 0.14 + (_vn(x * 1.2, y, 23, 6) - 0.5) * 0.3 + (_vn(x, y, 24, 2) - 0.5) * 0.14
            v += (x - W) / 120 * 0.06                                     # k srázu (ke stromu) se svah zvedá
            if _h(x, y, 25) % 31 == 0:
                v += 0.25
            ledge = (y + _vn(x, 0, 26, 30) * 18) % 26
            if ledge < 1.2 and _vn(x, y, 27, 14) > 0.4:
                v = 0.08
            elif ledge < 2.4 and _vn(x, y, 27, 14) > 0.4:
                v += 0.2
            if TRAIL[y, x] < 4:
                v = 0.66 - down * 0.14 + (_vn(x, y, 28, 3) - 0.5) * 0.1 - TRAIL[y, x] * 0.03
            if x < W + 6:
                v -= (W + 6 - x) * 0.05                                   # stín u paty stěny
            if x > E - 2:
                v = 0.7
            img[y, x] = _pick(SCREE, max(0.0, min(1.0, v)), x, y)
            floor[y, x] = True
            if W + 3 < x < E - 4:
                block[y, x] = False
        else:                                                             # sráz – tmavne do mlhy
            k = (x - E) / 12
            col = np.array(_pick(ROCK, 0.45 - k * 0.3 + (_vn(x, y * 0.5, 65, 4) - 0.5) * 0.2, x, y)[:3], float)
            img[y, x, :3] = np.clip(col * (1 - k) + img[y, x, :3] * k, 0, 255)
vista = np.where(floor[:, :, None], vista, img)

# pukliny a jeskyně ve stěně: tma uvnitř, okraj, kořeny je zarůstají
for i, (my, mh) in enumerate(MOUTHS):
    W = wall_x(my)
    for y in range(my - mh // 2, my + mh // 2):
        q = abs(y - my) / (mh / 2)
        depth_in = int((W + 3) * (1 - q ** 2) ** 0.5)
        for x in range(0, depth_in):
            img[y, x, :3] = (4, 4, 6) if x < depth_in - 3 else (16, 16, 20)
        for x in range(depth_in, depth_in + 2):                           # světlý okraj skály kolem ústí
            img[y, x] = ROCK[5] if abs(y - my) < mh // 2 - 1 else ROCK[3]
    for x in range(0, int(W) + 4):                                        # překlad nad ústím
        img[my - mh // 2 - 1, x] = ROCK[5]; img[my - mh // 2, x] = ROCK[1]
    for k in range(2 + i % 2):                                            # kořeny ústí zavírají
        y0 = my - mh // 2 + 3 + k * (mh // 3)
        root(sc, bez((-6, y0 - 6), (W - 2, y0 + 2), (W + 10, y0 + 8 + k * 3)), 4.5, 2, solid=False, lift=0.2, d=0.45)
for (y0, n) in ((160, 14), (318, 16), (452, 12)):                         # tenké praskliny (nedalo se projít)
    x, y = wall_x(y0), y0
    for _ in range(n):
        x -= rng.uniform(0.2, 1.0); y += rng.uniform(-1.5, 1.5)
        if 0 <= int(x) < AW:
            img[int(y), int(x), :3] = (6, 6, 9); img[int(y) + 1, int(x), :3] = (6, 6, 9)

# pramínky vody po svahu – mizí v kořenech
for (rx, ry0, ry1) in ((40, 120, 220), (120, 300, 420)):
    x = float(rx)
    for y in range(ry0, ry1):
        x += math.sin(y / 11 + rx) * 0.35 + (_vn(rx, y, 30, 9) - 0.5) * 0.6
        X = int(x)
        if 0 < X < AW - 1 and floor[y, X]:
            img[y, X, :3] = (54, 78, 96); img[y, X + 1, :3] = (30, 44, 56)
            if y % 5 == 0:
                img[y, X, :3] = (120, 160, 190)


def chain(*pts, n=24):
    out = []
    mids = [pts[0]] + [((a[0] + b[0]) / 2, (a[1] + b[1]) / 2) for a, b in zip(pts[1:-1], pts[2:])] + [pts[-1]]
    for i in range(len(pts) - 2):
        out += bez(mids[i], pts[i + 1], mids[i + 1], n)
    return out


root(sc, chain((180, 196), (140, 210), (100, 214), (56, 230), (36, 238)), 9, 4, solid=False, lift=0.6, d=0.42)   # do jeskyně 2
root(sc, chain((180, 410), (150, 420), (126, 424), (118, 422)), 8, 3, solid=False, lift=0.6, d=0.42)               # pije pramínek
root(sc, chain((180, 520), (150, 528), (120, 540), (96, 560)), 10, 5, solid=False, lift=0.6, d=0.42)
rmask = (sc.depth > 0.3) & (sc.depth < 0.5)
img[rmask, :3] = (img[rmask, :3] * 0.55).astype(np.uint8)
LU.carved_stairs(img, 30, 530, 58, AH, steps=5, width=28)
O.tiny  # noqa
_w = Spr(16, 6); SG.tiny(_w, 0, 0, "VEN", c(0xC8C4B4))                    # vyškrábáno nehty vedle ústí
for yy in range(6):
    for xx in range(16):
        if _w.a[yy, xx, 3]:
            for (X0, Y0) in ((6, 220), (7, 368)):
                img[Y0 + yy, X0 + xx, :3] = (150, 146, 134)

for y in range(348, 364):                                                 # tůňka u vodočetné latě
    for x in range(92, 132):
        m = ((x - 112) / 18) ** 2 + ((y - 356) / 6) ** 2
        if m < 1:
            img[y, x, :3] = (22, 34, 44) if m < 0.55 else (40, 52, 58)
            if m < 0.55 and (x + y) % 9 == 0:
                img[y, x, :3] = (70, 100, 120)
        elif m < 1.3 and floor[y, x]:
            img[y, x, :3] = (img[y, x, :3] * 0.7).astype(np.uint8)
block[350:362, 96:128] = True

# plochá skalní deska přes stezku s nástřikem NEPOČÍTEJTE JE
for y in range(288, 314):
    for x in range(36, 110):
        d = ((x - 72) / 37) ** 2 + ((y - 301) / 13) ** 2
        if d < 1:
            v = 0.78 - (y - 288) * 0.012 + (_vn(x, y, 69, 4) - 0.5) * 0.16 - (0.35 if d > 0.86 else 0)
            if abs(_vn(x * 0.8, y * 0.8, 70, 7) - 0.5) < 0.025:
                v -= 0.3
            img[y, x] = _pick(SCREE, max(0.0, min(1.0, v)), x, y)
BLOOD = [np.array(v, float) for v in ((70, 10, 8), (110, 18, 12), (140, 28, 18), (168, 42, 26))]


def blood(x0, y0, text, seed):
    """Napsáno prstem krví: čitelná písmena, tah nestejně sytý, ke konci slábne a písmena poskočí, z dolních
    hran stékají kapky, na začátku rozmazaný otisk dlaně."""
    r = np.random.default_rng(seed)
    m = SG.text_mask(text)
    H_, W_ = m.shape
    ys, xs = np.nonzero(m.any(1))[0], None
    for xx in range(W_):
        fade = xx / W_
        dy = 1 if fade > 0.75 and (xx // 6) % 2 else 0
        for yy in range(H_):
            if not m[yy, xx]:
                continue
            X, Y = x0 + xx, y0 + yy + dy
            if r.random() < fade * 0.08:
                continue
            col = BLOOD[2] if r.random() < 0.6 else BLOOD[3] if r.random() < 0.5 else BLOOD[1]
            img[Y, X, :3] = col
            if yy + 1 < H_ and not m[yy + 1, xx] and r.random() < 0.3 - fade * 0.15:
                n = int(r.integers(2, 7))
                for k in range(1, n):
                    img[Y + k, X, :3] = BLOOD[1] if k < n - 1 else BLOOD[0]
    for k in range(12):
        X, Y = x0 - 7 + int(r.integers(0, 6)), y0 + 1 + int(r.integers(0, 7))
        img[Y, X, :3] = np.clip(img[Y, X, :3] * 0.4 + BLOOD[1] * 0.6, 0, 255)


blood(40, 290, "NEPOČÍTEJTE", 12)
blood(60, 302, "JE.", 13)

# ── trosky Řádu ─────────────────────────────────────────────────────────────
sc.place(O.theodolite(), 128, 132, foot=(18, 6))
lantern = Spr(7, 5)
lantern.rect(0, 1, 4, 4, O.OLIVE[3]); lantern.rect(5, 0, 6, 4, c(0xFFE8B0))
sc.place(lantern, 112, 152, collide=False)
sc.place(O.count_board("SČÍTÁNÍ"), 116, 242, foot=(40, 4))
sc.place(O.water_gauge(), 124, 358, foot=(6, 3))
STAKES = [(76, 46), (100, 104), (92, 160), (88, 214), (96, 270), (90, 330), (96, 396), (90, 452), (88, 510), (112, 534)]
for (x, y) in STAKES:
    sc.place(O.stake(18), x, y, foot=(2, 2))
for (a, b) in zip(STAKES, STAKES[1:]):
    if _h(a[0], a[1], 33) % 3 == 0:
        continue
    for t in np.linspace(0, 1, 50):
        X = int(a[0] + (b[0] - a[0]) * t); Y = int(a[1] - 12 + (b[1] - a[1]) * t + math.sin(t * math.pi) * 4)
        if 0 <= Y < AH and 0 <= X < AW:
            img[Y, X, :3] = (150, 140, 116)
sc.place(LU.longhouse_ribs(40, 28, sink=0.8, seed=5), 70, 528, collide=False)


# ── těla: různá, všechna utíkají pryč od stromu (ke stěně a nahoru) ─────────
def rot_left(spr):
    out = Spr(spr.h, spr.w); out.a = np.ascontiguousarray(np.rot90(spr.a, 1)); return out


def lying(spr, x, y, legs_right=False):
    """Leží: zkrácené na výšku (3/4 shora), lehce zaprášené, nohy napůl v suti, přitisknutý stín kolem."""
    h2 = max(1, int(spr.h * 0.66))
    out = Spr(spr.w, h2)
    for yy in range(h2):
        sy = min(spr.h - 1, int(yy / 0.66))
        for xx in range(spr.w):
            px = spr.a[sy, xx]
            if px[3]:
                ground = img[min(AH - 1, y - h2 + yy), min(AW - 1, max(0, x - spr.w // 2 + xx)), :3].astype(float)
                f = (xx / spr.w) if legs_right else (yy / h2)
                dust = 0.12 + 0.4 * max(0.0, f - 0.6) / 0.4
                out.a[yy, xx, :3] = np.clip(px[:3].astype(float) * (1 - dust) + ground * dust, 0, 255); out.a[yy, xx, 3] = 255
    for yy in range(h2 + 2):
        for xx in range(-1, spr.w + 1):
            X, Y = x - spr.w // 2 + xx, y - h2 + yy
            inside = 0 <= yy < h2 and 0 <= xx < spr.w and out.a[yy, xx, 3]
            near = any(0 <= yy + dy < h2 and 0 <= xx + dx < spr.w and out.a[yy + dy, xx + dx, 3] for dy in (-1, -2) for dx in (-1, 0, 1))
            if not inside and near and 0 <= X < AW and 0 <= Y < AH and floor[Y, X]:
                img[Y, X, :3] = (img[Y, X, :3] * 0.6).astype(np.uint8)
    return out


POSES = ["crawl", "reach", "crawl", "back", "curl", "blanket", "reach", "crawl", "sit", "reach"]
OUTS = ["olive", "olive", "wool", "jacket", "tech", "olive", "wool"]
bodies = []


def add(x, y, kw, left):
    bodies.append((x, y, kw, left))


for i, (my, mh) in enumerate(MOUTHS):                                     # hromady u ústí – hlavou dovnitř
    W = wall_x(my)
    for k in range(4 + (i % 2) * 2):
        x = int(W + 14 + k * 9 + rng.integers(-2, 3)); y = int(my - mh // 3 + (k % 3) * 8 + rng.integers(-2, 3))
        add(x, y, dict(seed=200 + i * 10 + k, pose=["crawl", "reach", "crawl", "back"][k % 4], outfit=OUTS[(i + k) % len(OUTS)],
                       pack=k % 3 == 0, helmet=k % 5 == 1, overgrown=0.4), True)
V7 = [(52, 414), (62, 418), (72, 415), (82, 419), (92, 416), (110, 420), (120, 417), (130, 421), (140, 418)]
V7 = [(x - 8, y) for (x, y) in V7]
for i, (x, y) in enumerate(V7):
    add(x, y, dict(seed=70 + i, pose="crawl" if i % 3 else "reach", outfit="olive", pack=i % 4 == 0, helmet=i == 0, overgrown=0.3), False)
tries = 0
while len(bodies) < 72 and tries < 12000:
    tries += 1
    y = int(rng.integers(36, 526)); x = int(rng.integers(int(wall_x(y)) + 10, int(edge(y)) - 8))
    if not floor[y, x] or TRAIL[y, x] < 9 or TRAIL[min(AH - 1, y - 8), x] < 8:
        continue
    if any(abs(x - bx) < 15 and abs(y - by) < 15 for (bx, by, _, _) in bodies):
        continue
    if (346 < y < 368 and 90 < x < 134) or (286 < y < 318 and 34 < x < 112):
        continue
    k = len(bodies)
    left = x < 110 and rng.random() < 0.7                                 # blíž ke stěně míří ke stěně
    add(x, y, dict(seed=int(rng.integers(0, 9999)), pose=POSES[k % len(POSES)], outfit=OUTS[(k * 3) % len(OUTS)],
                   pack=rng.random() < 0.3, helmet=rng.random() < 0.2, overgrown=min(1.0, y / 520 * rng.uniform(0.3, 1.2))), left)
for (x, y, kw, left) in bodies:
    spr = O.victim(**kw)
    if kw["pose"] == "sit":
        sc.place(spr, x, y, collide=True, pad=0, z=y)
    elif left:
        sc.place(lying(rot_left(spr), x, y, legs_right=True), x, y, collide=False, shadow=False, z=y - 30)
    else:
        sc.place(lying(spr, x, y), x, y, collide=False, shadow=False, z=y - 30)

sc.shadows(floor)
sc.draw_sprites()
block[:, :3] = True
for (x, y) in STAKES:
    block[y - 2:y, x - 1:x + 1] = True

fungi = []
for _ in range(9000):
    x, y = int(rng.integers(4, AW - 4)), int(rng.integers(10, AH - 6))
    if sc.depth[y, x] and not sc.depth[y, x + 2] and len(fungi) < 12:
        fungi.append((x, y))
        img[y, x, :3] = (150, 210, 255); img[y, x - 1, :3] = (80, 140, 220); img[y + 1, x, :3] = (40, 70, 120)

sc.light(
    warm=[(112, 154, 22, 14, 0.75)],
    cold=[(x, y, 7, 6, 0.6) for (x, y) in fungi] + [(44, 546, 22, 14, 0.6)] + [(30, my, 26, 20, 0.55) for (my, _) in MOUTHS] +
         [(80, y, 70, 46, 0.32) for y in range(40, 540, 70)] + [(140, y, 20, 40, 0.4) for y in range(60, 540, 80)],
    dark_k=0.26, warm_k=0.3, cold_k=0.14)
keep = ~floor & (sc.zbuf < -1e8) & (sc.depth == 0)
keep[:, :32] = False                                                       # stěna se svítí normálně
img[keep, :3] = (vista[keep, :3] * 0.85 + img[keep, :3] * 0.15).astype(np.uint8)
sc.export("root_nameless", "gen_nameless.py")
print("BODIES", len(bodies), "FAR", far, "FUNGI", fungi[:6], "VODA", NODES["sb_voda"])
