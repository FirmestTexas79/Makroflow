"""
Použití:  python tools/mapgen/gen_nameless.py
Výstup:   app/src/main/res/drawable-nodpi/root_nameless.png, app/src/main/assets/walk/root_nameless.txt,
          tools/mapgen/root_nameless_debug.png

Zóna 3 (Akt III – Hlubina), lokace 5 z 8: SVAH BEZEJMENNÝCH (176 × 560, kamera jede s hráčem, docs/adr/0106).
Dvě perspektivy a dvě úrovně jako na Signálním útesu: hráč sestupuje po levém okraji svahu (zhruba pětina šířky,
pohled 3/4 shora), zbytek je výhled přes údolí s mlhou na PROTĚJŠÍ KOPEC – z boku, daleko a v oparu. Po celém
kopci se nahoru plahočí zástupy těl ve stopách k vrcholu, kde pořád svítí sodíková lampa Řádu (lezli ke světlu
tábora, pryč od něčeho dole). Čím výš, tím menší a mlhavější; u vrcholu jen siluety proti světlu.
Na našem svahu leží těla zblízka – různá: plazí se, natahují ruku, na zádech, schoulení na boku, zabalení do deky,
sedící, s batohy a přilbami, v kombinézách Řádu, vlněných kabátech, bundách i modrém technickém; všichni hlavou
vzhůru. Devět se štítky V7 (Keshova výprava). Trosky Řádu: kolíky s přetrženým lanem, zlámaný stativ s baterkou,
která pořád svítí, sčítací tabule na sloupcích, vodočetná lať VODA ve vypité tůňce, nástřik NEPOČÍTEJTE JE na
balvanu. Kořeny tečou dolů oběma svahy a pijí pramínky vody. Dole schody Lumenců (Chrám) a kolíky do mlhy (Les).
Setkání DUCH a ZEMĚ. Živé detaily: InteriorFxView.ROOT_NAMELESS. NODES / EDGES jsou ZDROJ PRAVDY i pro RootNameless.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import Spr, c, _pick, _h, _vn, ROCK, SCREE, BARK, STEEL  # noqa: E402
from scene import Scene  # noqa: E402
from roots import root, bez  # noqa: E402
import order as O, lumen as LU, signs as SG  # noqa: E402

AW, AH = 176, 560
NODES = {
    "sb_vstup": (24, 26), "sb_kolik": (28, 92), "sb_teodolit": (46, 150), "sb_rada": (26, 204), "sb_tabule": (52, 262),
    "sb_cesta": (26, 304), "sb_voda": (48, 390), "sb_v7": (46, 452), "sb_vyhled": (70, 488), "sb_cesta2": (24, 506),
    "sb_chram": (16, 546), "sb_les": (54, 548),
}
EDGES = [("sb_vstup", "sb_kolik"), ("sb_kolik", "sb_teodolit"), ("sb_kolik", "sb_rada"), ("sb_teodolit", "sb_rada"),
         ("sb_rada", "sb_tabule"), ("sb_rada", "sb_cesta"), ("sb_tabule", "sb_cesta"), ("sb_cesta", "sb_voda"),
         ("sb_voda", "sb_v7"), ("sb_cesta", "sb_cesta2"), ("sb_v7", "sb_vyhled"), ("sb_v7", "sb_cesta2"),
         ("sb_vyhled", "sb_cesta2"), ("sb_cesta2", "sb_chram"), ("sb_cesta2", "sb_les")]
sc = Scene(AW, AH, 0, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(55)

LAMP = (128, 108)                                                          # sodíková lampa na protějším vrcholu


# ── výhled: tma dutiny, světlo lampy na vrcholu, mlha z údolí ───────────────
def void(x, y):
    g = max(0.0, 1 - math.hypot((x - LAMP[0]) / 110, (y - LAMP[1]) / 90)) ** 1.6   # teplá záře lampy
    m = max(0.0, (y - 330) / 230) ** 1.3                                        # zelená mlha zespodu
    col = np.array([5, 6, 9], float) + np.array([150, 100, 46], float) * g * 0.6
    col = col * (1 - m) + np.array([40, 92, 78], float) * m
    n = _vn(x * 0.5, y * 1.1, 3, 30) * 0.6 + _vn(x, y, 5, 9) * 0.4
    return col * (0.82 + 0.36 * n)


for y in range(AH):
    for x in range(AW):
        img[y, x, :3] = np.clip(void(x, y), 0, 255)
for (x0, ln) in ((96, 60), (120, 34), (168, 46), (70, 26)):               # kořeny visící ze stropu (silueta)
    for t in range(ln):
        X = int(x0 + math.sin(t / 9 + x0) * 3); w = max(1, int((1 - t / ln) * 3))
        for k in range(w):
            if 0 <= X + k < AW:
                img[t, X + k, :3] = np.clip(img[t, X + k, :3] * 0.4 + np.array([10, 10, 12]) * 0.6, 0, 255)


# ── protější kopec (z boku): silueta, terasy, kořeny, zástupy těl ─────────────
def H(x):
    """Horní obrys protějšího kopce (y) – vrchol vpravo nahoře, svah klesá doleva dolů do údolí."""
    n = (_vn(x, 0, 61, 9) - 0.5) * 4
    if x >= LAMP[0]:
        return LAMP[1] + 10 + (max(0.0, x - LAMP[0] - 8) / 52) ** 1.8 * 60 + n
    return LAMP[1] + 10 + (max(0.0, LAMP[0] - x - 10) / 74) ** 1.7 * 330 + n


def haze_of(y):
    """Vzdušná perspektiva: vrchol je daleko (opar), dole se zvedá mlha z údolí."""
    return min(0.85, 0.3 * max(0.0, 1 - (y - 110) / 160) + 0.7 * max(0.0, (y - 380) / 180) ** 1.2)


HILL = [c(0x16140F), c(0x221F19), c(0x2F2A22), c(0x3E372D), c(0x4E463A), c(0x625849)]
hill = np.zeros((AH, AW), bool)
for x in range(AW):
    top = H(x)
    for y in range(max(0, int(top)), AH):
        hill[y, x] = True
        d = y - top
        v = 0.5 + (_vn(x * 1.1, y * 0.7, 62, 6) - 0.5) * 0.3 + (x - 60) / 116 * 0.12
        sp = 8 + y / AH * 22                                              # terasy: blíž (níž) řidší
        if (d + _vn(x, 0, 63, 12) * 6) % sp < 1.1:
            v = 0.12
        elif (d + _vn(x, 0, 63, 12) * 6) % sp < 2.2:
            v += 0.2
        if d < 2:
            v = 0.62                                                      # hřbet ve světle lampy
        glow = max(0.0, 1 - math.hypot((x - LAMP[0]) / 90, (y - LAMP[1]) / 110)) ** 1.4
        col = np.array(_pick(HILL, max(0.0, min(1.0, v)), x, y)[:3], float)
        col = col + np.array([130, 84, 36], float) * glow * 0.45
        hz = haze_of(y)
        img[y, x, :3] = np.clip(col * (1 - hz) + np.clip(void(x, y), 0, 255) * hz, 0, 255)
# kořeny tekoucí dolů po protějším svahu (do údolí, ke kmeni)
for (x0, x1, w) in ((120, 50, 3.5), (100, 40, 2.5), (140, 110, 2.0), (112, 76, 1.5)):
    y0, y1 = H(x0) + 3, 560
    for t in np.linspace(0, 1, 500):
        x = x0 + (x1 - x0) * t + math.sin(t * 9 + x0) * 4; y = y0 + (y1 - y0) * t
        ww = w * (0.6 + 0.8 * t)
        for k in range(-int(ww), int(ww) + 1):
            X, Y = int(x + k), int(y)
            if 0 <= X < AW and 0 <= Y < AH and hill[Y, X]:
                col = np.array([20, 18, 14], float) if k > 0 else np.array([62, 56, 46], float)
                hz = haze_of(Y)
                img[Y, X, :3] = np.clip(col * (1 - hz) + img[Y, X, :3] * hz, 0, 255)
# lampa a stožár na vrcholu
for y in range(LAMP[1], LAMP[1] + 12):
    img[y, LAMP[0], :3] = (30, 24, 18)
img[LAMP[1] - 1:LAMP[1] + 1, LAMP[0] - 1:LAMP[0] + 2, :3] = (255, 214, 140)


def far_figure(x, y, s, ang):
    """Drobná postava plazící se do kopce: tělo podél svahu, světlejší hlava nahoře, ruka natažená dál.
    s = měřítko (1 u vrcholu … 4 dole), ang = směr vzhůru po svahu."""
    ang += rng.uniform(-0.35, 0.35)
    if rng.random() < 0.15:
        ang += rng.choice([-1.3, 1.3])                                    # někdo se svalil na bok
    ux, uy = math.cos(ang), math.sin(ang)
    hz = haze_of(y)
    sil = max(0.0, 1 - math.hypot((x - LAMP[0]) / 60, (y - LAMP[1]) / 46))  # proti světlu jen silueta
    body = np.array([16, 15, 12], float) * (1 - sil) + np.array([6, 4, 2], float) * sil
    head = np.array([150, 140, 118], float) * (1 - sil) + np.array([10, 7, 4], float) * sil
    L = 2.2 * s
    for i in range(int(L * 2) + 1):
        t = i / 2
        for k in ((0,) if s < 2 else (0, 1)):
            X, Y = int(round(x + ux * t - uy * k)), int(round(y + uy * t + ux * k))
            if 0 <= X < AW and 0 <= Y < AH:
                col = head if t >= L - 0.5 else body
                img[Y, X, :3] = np.clip(col * (1 - hz) + img[Y, X, :3] * hz, 0, 255)
    if s >= 1.6:                                                          # natažená ruka
        for t in np.linspace(L, L + 1.2 * s, 4):
            X, Y = int(round(x + ux * t + uy * 1)), int(round(y + uy * t - ux * 1))
            if 0 <= X < AW and 0 <= Y < AH:
                img[Y, X, :3] = np.clip(body * (1 - hz) + img[Y, X, :3] * hz, 0, 255)


figures = []
for tr in range(8):                                                       # stopy z údolí k vrcholu
    xb = 60 + tr * 15 + rng.uniform(-4, 4)
    pts = []
    x = xb
    for yy in np.arange(552, LAMP[1] + 12, -1.0):
        frac = (552 - yy) / (552 - LAMP[1])
        x += (LAMP[0] - 6 + tr * 1.4 - x) * 0.004 * (1 + frac * 3) + math.sin(yy / 23 + tr) * 0.25
        if hill[int(yy), int(min(AW - 1, max(0, x)))]:
            pts.append((x, yy))
    gap = 0
    for (x, y) in pts:
        gap -= 1
        frac = (552 - y) / (552 - LAMP[1])
        if gap <= 0 and rng.random() < 0.95 - frac * 0.5:
            s = 1.3 + ((y - LAMP[1]) / (AH - LAMP[1])) ** 1.2 * 3.4
            figures.append((x + rng.uniform(-2, 2) * s, y, s))
            gap = int(3 + s * 3.2 + rng.integers(0, 4))
for _ in range(110):                                                      # roztroušení mimo stopy
    x, y = rng.uniform(44, AW), rng.uniform(80, 540)
    if hill[int(y), int(x)] and y > H(x) + 4:
        figures.append((x, y, 1.3 + ((y - LAMP[1]) / (AH - LAMP[1])) ** 1.2 * 3.4))
for (x, y, s) in sorted(figures, key=lambda f: f[1]):
    dx = 1.0
    slope = (H(x + 1) - H(x - 1)) / 2                                     # sklon terénu pod postavou
    ang = math.atan2(min(-0.6, slope * 0.9 - 0.3), dx)
    far_figure(x, y, s, ang)
vista = img.copy()

# ── náš svah (vlevo, pohled 3/4 shora) ──────────────────────────────────────
def bump(y, y0, r, k):
    return k * max(0.0, 1 - ((y - y0) / r) ** 2)


def edge(y):
    """Pravý okraj našeho svahu; vpravo od něj sráz do údolí."""
    return (44 + math.sin(y / 37) * 5 + (_vn(0, y, 64, 11) - 0.5) * 6 + bump(y, 150, 26, 20) + bump(y, 262, 30, 30)
            + bump(y, 304, 18, 26) + bump(y, 372, 26, 26) + bump(y, 436, 30, 42) + bump(y, 488, 22, 32) + bump(y, 548, 30, 30))


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
    E = edge(y)
    for x in range(0, min(AW, int(E) + 14)):
        if x < E:
            TRAIL[y, x] = seg_dist(x, y)
            down = y / AH
            v = 0.52 - down * 0.16 + (_vn(x * 1.2, y, 23, 6) - 0.5) * 0.3 + (_vn(x, y, 24, 2) - 0.5) * 0.14
            if _h(x, y, 25) % 31 == 0:
                v += 0.25
            ledge = (y + _vn(x, 0, 26, 30) * 18) % 26
            if ledge < 1.2 and _vn(x, y, 27, 14) > 0.4:
                v = 0.08
            elif ledge < 2.4 and _vn(x, y, 27, 14) > 0.4:
                v += 0.2
            if TRAIL[y, x] < 4:
                v = 0.66 - down * 0.16 + (_vn(x, y, 28, 3) - 0.5) * 0.1 - TRAIL[y, x] * 0.03
            if x > E - 2:
                v = 0.7                                                   # hrana srázu ve světle
            img[y, x] = _pick(SCREE, max(0.0, min(1.0, v)), x, y)
            floor[y, x] = True
            if 3 < x < E - 4:
                block[y, x] = False
        else:                                                             # stěna srázu – tmavne do mlhy
            k = (x - E) / 14
            col = np.array(_pick(ROCK, 0.45 - k * 0.3 + (_vn(x, y * 0.5, 65, 4) - 0.5) * 0.2, x, y)[:3], float)
            if abs(_vn(x * 2, y * 0.3, 66, 5) - 0.5) < 0.04:
                col *= 0.4
            img[y, x, :3] = np.clip(col * (1 - k) + img[y, x, :3] * k, 0, 255)
# mlha z údolí přes úpatí našeho srázu
for y in range(AH):
    for x in range(AW):
        if not floor[y, x]:
            m = _vn(x * 0.7 + 11, y * 1.5, 67, 22) * 0.7 + _vn(x, y * 1.2, 68, 7) * 0.3
            a = math.floor(max(0.0, (m - 0.58) * 1.6) * 4) / 4 * 0.45 * min(1.0, max(0.0, (y - 140) / 160))
            if a > 0:
                img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([60, 120, 100]) * a, 0, 255)
vista = np.where(floor[:, :, None], vista, img)

# pramínky vody po našem svahu – mizí v kořeni
for (rx, ry0, ry1) in ((10, 0, 230), (34, 300, 520)):
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


root(sc, chain((-8, 200), (14, 236), (4, 268), (20, 300)), 9, 4, solid=False, lift=0.6, d=0.42)      # pije pramínek
root(sc, chain((-8, 470), (30, 500), (60, 520), (90, 540)), 10, 5, solid=False, lift=0.6, d=0.42)
rmask = (sc.depth > 0.3) & (sc.depth < 0.5)
img[rmask, :3] = (img[rmask, :3] * 0.7).astype(np.uint8)
LU.carved_stairs(img, 0, 530, 30, AH, steps=5, width=30)

# tůňka u vodočetné latě (skoro vypitá)
for y in range(364, 380):
    for x in range(36, 76):
        m = ((x - 56) / 18) ** 2 + ((y - 372) / 6) ** 2
        if m < 1 and floor[y, x]:
            img[y, x, :3] = (22, 34, 44) if m < 0.55 else (40, 52, 58)
            if m < 0.55 and (x + y) % 9 == 0:
                img[y, x, :3] = (70, 100, 120)
        elif m < 1.3 and floor[y, x]:
            img[y, x, :3] = (img[y, x, :3] * 0.7).astype(np.uint8)
block[366:378, 40:72] = True

# ── trosky Řádu ─────────────────────────────────────────────────────────────
sc.place(O.theodolite(), 58, 146, foot=(18, 6))
lantern = Spr(7, 5)
lantern.rect(0, 1, 4, 4, O.OLIVE[3]); lantern.rect(5, 0, 6, 4, c(0xFFE8B0))
sc.place(lantern, 44, 164, collide=False)
sc.place(O.count_board("SČÍTÁNÍ"), 48, 256, foot=(40, 4))
sc.place(O.water_gauge(), 66, 374, foot=(6, 3))
# plochá skalní deska přes stezku s nástřikem NEPOČÍTEJTE JE (stojí se přímo na nápisu)
for y in range(290, 316):
    for x in range(2, 76):
        d = ((x - 38) / 37) ** 2 + ((y - 303) / 13) ** 2
        if d < 1 and floor[y, x]:
            v = 0.78 - (y - 290) * 0.012 + (_vn(x, y, 69, 4) - 0.5) * 0.16 - (0.35 if d > 0.86 else 0)
            if abs(_vn(x * 0.8, y * 0.8, 70, 7) - 0.5) < 0.025:
                v -= 0.3                                                  # prasklina v desce
            img[y, x] = _pick(SCREE, max(0.0, min(1.0, v)), x, y)
SG.painted(img, 6, 293, "NEPOČÍTEJTE", c(0x9A2620), seed=12, wear=0.1, drips=False)
SG.painted(img, 28, 304, "JE.", c(0x9A2620), seed=13, wear=0.1, drips=False)
STAKES = [(36, 50), (40, 74), (14, 120), (40, 176), (14, 230), (38, 286), (14, 330), (40, 380), (36, 436), (44, 516), (70, 540)]
for (x, y) in STAKES:
    sc.place(O.stake(18), x, y, foot=(2, 2))
for (a, b) in zip(STAKES, STAKES[1:]):
    if _h(a[0], a[1], 33) % 3 == 0:
        continue
    for t in np.linspace(0, 1, 50):
        X = int(a[0] + (b[0] - a[0]) * t); Y = int(a[1] - 12 + (b[1] - a[1]) * t + math.sin(t * math.pi) * 4)
        if 0 <= Y < AH and 0 <= X < AW:
            img[Y, X, :3] = (150, 140, 116)
sc.place(LU.longhouse_ribs(40, 28, sink=0.8, seed=5), 50, 528, collide=False)

# ── těla na našem svahu: různá, všechna hlavou vzhůru ───────────────────────
FADE = 0.12


def lying(spr, x, y):
    """Leží: zkrácené na výšku, lehce zaprášené, nohy napůl v suti, přitisknutý stín kolem."""
    h2 = int(spr.h * 0.66)
    out = Spr(spr.w, h2)
    for yy in range(h2):
        sy = min(spr.h - 1, int(yy / 0.66))
        for xx in range(spr.w):
            px = spr.a[sy, xx]
            if px[3]:
                ground = img[min(AH - 1, y - h2 + yy), min(AW - 1, max(0, x - spr.w // 2 + xx)), :3].astype(float)
                dust = FADE + 0.4 * max(0.0, (yy / h2) - 0.6) / 0.4
                out.a[yy, xx, :3] = np.clip(px[:3].astype(float) * (1 - dust) + ground * dust, 0, 255); out.a[yy, xx, 3] = 255
    for yy in range(h2 + 2):
        for xx in range(-1, spr.w + 1):
            X, Y = x - spr.w // 2 + xx, y - h2 + yy
            inside = 0 <= yy < h2 and 0 <= xx < spr.w and out.a[yy, xx, 3]
            near = any(0 <= yy + dy < h2 and 0 <= xx + dx < spr.w and out.a[yy + dy, xx + dx, 3] for dy in (-1, -2) for dx in (-1, 0, 1))
            if not inside and near and 0 <= X < AW and 0 <= Y < AH and floor[Y, X]:
                img[Y, X, :3] = (img[Y, X, :3] * 0.6).astype(np.uint8)
    return out


POSES = ["crawl", "reach", "crawl", "back", "curl", "blanket", "reach", "crawl", "sit"]
OUTS = ["olive", "olive", "wool", "jacket", "tech", "olive"]
bodies = []
V7 = [(6 + i * 9 + (i % 2) * 2, 404 + (i % 3) * 4 + (i // 5) * 18 - (i % 5) * 0) for i in range(9)]
V7 = [(8, 430), (16, 434), (24, 431), (32, 435), (40, 432), (56, 436), (64, 433), (72, 437), (80, 434)]  # mezera 5–6
for i, (x, y) in enumerate(V7):
    bodies.append((x, y, dict(seed=70 + i, pose="crawl" if i % 3 else "reach", outfit="olive", pack=i % 4 == 0, helmet=i == 0, overgrown=0.3)))
tries = 0
while len(bodies) < 34 and tries < 8000:
    tries += 1
    y = int(rng.integers(40, 520)); x = int(rng.integers(8, max(9, int(edge(y)) - 6)))
    if not floor[y, x] or TRAIL[y, x] < 8 or TRAIL[y - 8, x] < 7:
        continue
    if any(abs(x - bx) < 14 and abs(y - by) < 16 for (bx, by, _) in bodies):
        continue
    if (362 < y < 384 and 34 < x < 78) or 286 < y < 322:
        continue
    k = len(bodies)
    bodies.append((x, y, dict(seed=int(rng.integers(0, 9999)), pose=POSES[k % len(POSES)], outfit=OUTS[(k * 7) % len(OUTS)],
                              pack=rng.random() < 0.3, helmet=rng.random() < 0.2, overgrown=min(1.0, y / 520 * rng.uniform(0.3, 1.2)))))
for (x, y, kw) in bodies:
    spr = O.victim(**kw)
    if kw["pose"] == "sit":
        sc.place(spr, x, y, collide=True, pad=0, z=y)
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
    if sc.depth[y, x] and not sc.depth[y, x + 2] and len(fungi) < 10:
        fungi.append((x, y))
        img[y, x, :3] = (150, 210, 255); img[y, x - 1, :3] = (80, 140, 220); img[y + 1, x, :3] = (40, 70, 120)

sc.light(
    warm=[(46, 164, 20, 12, 0.7)],
    cold=[(x, y, 7, 6, 0.6) for (x, y) in fungi] + [(16, 540, 22, 14, 0.6)] +
         [(30, y, 44, 40, 0.3) for y in range(40, 540, 70)],
    dark_k=0.26, warm_k=0.3, cold_k=0.14)
keep = ~floor & (sc.zbuf < -1e8) & (sc.depth == 0)
img[keep, :3] = (vista[keep, :3] * 0.85 + img[keep, :3] * 0.15).astype(np.uint8)
sc.export("root_nameless", "gen_nameless.py")
print("BODIES", len(bodies), "FIGURES", len(figures), "FUNGI", fungi[:6])
