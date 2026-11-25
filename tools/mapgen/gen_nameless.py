"""
Použití:  python tools/mapgen/gen_nameless.py
Výstup:   app/src/main/res/drawable-nodpi/root_nameless.png, app/src/main/assets/walk/root_nameless.txt,
          tools/mapgen/root_nameless_debug.png

Zóna 3 (Akt III – Hlubina), lokace 5 z 8: SVAH BEZEJMENNÝCH (176 × 352, pohled 3/4 shora, docs/adr/0106).
Strmý suťový svah pod Táborem, kam sestupovaly výpravy 1–7. Pohlcení 5/8: z Řádu zbyly jen trosky – zlámaný
stativ měřiče, řada červenobílých kolíků s přetrženým lanem, převrácená sčítací tabule, vodočetná lať v tůňce
a na skalní stěně nastříkané NEPOČÍTEJTE JE. Po celém svahu leží členové výprav: vyhublí, v kombinézách, tváří
dolů a všichni hlavou vzhůru – lezli nahoru, pryč od něčeho dole. U kotníků papírové štítky s číslem výpravy;
devět štítků V7 (Keshova výprava). Kořeny se svahem valí dolů ke kmeni a pijí: pramínky vody ze skály mizí v nich.
Ve stěně fasáda Lumenců, kterou skála a kořeny stahují dovnitř, dole na svahu žebra dlouhého domu. Žádné lampy
Řádu – jen jedna spadlá baterka, studené znaky, houby a zelená záře zespodu. Dolů vlevo schody Lumenců
(Chrám Lumenců), vpravo kolíky do mlhy (Les pod korunou). Setkání DUCH a ZEMĚ.
Živé detaily: InteriorFxView.ROOT_NAMELESS. NODES / EDGES jsou ZDROJ PRAVDY i pro RootNameless.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import Spr, c, _pick, _h, _vn, ROCK, SCREE, BARK  # noqa: E402
from scene import Scene  # noqa: E402
from roots import root, bez  # noqa: E402
import order as O, lumen as LU, terrain as T, signs as SG  # noqa: E402

AW, AH, WALL_H = 176, 352, 54
NODES = {
    "sb_vstup": (88, 64), "sb_ruina": (140, 66), "sb_kolik": (50, 110), "sb_teodolit": (104, 138), "sb_rada": (140, 172),
    "sb_tabule": (62, 194), "sb_cesta": (96, 222), "sb_v7": (34, 250), "sb_voda": (130, 248), "sb_cesta2": (88, 286),
    "sb_zebra": (150, 292), "sb_chram": (22, 336), "sb_les": (152, 338),
}
EDGES = [("sb_vstup", "sb_ruina"), ("sb_vstup", "sb_kolik"), ("sb_kolik", "sb_teodolit"), ("sb_teodolit", "sb_rada"),
         ("sb_teodolit", "sb_tabule"), ("sb_tabule", "sb_cesta"), ("sb_rada", "sb_cesta"), ("sb_cesta", "sb_v7"),
         ("sb_cesta", "sb_voda"), ("sb_cesta", "sb_cesta2"), ("sb_voda", "sb_zebra"), ("sb_cesta2", "sb_chram"),
         ("sb_cesta2", "sb_les"), ("sb_zebra", "sb_les"), ("sb_v7", "sb_chram")]
sc = Scene(AW, AH, WALL_H, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(55)


def seg_dist(x, y):
    """Vzdálenost bodu od nejbližší stezky (hrany grafu)."""
    best = 1e9
    for a, b in EDGES:
        (x0, y0), (x1, y1) = NODES[a], NODES[b]
        dx, dy = x1 - x0, y1 - y0
        t = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy)))
        best = min(best, math.hypot(x - x0 - dx * t, y - y0 - dy * t))
    return best


# ── svah: suť, hrany teras (vrstevnice), vyšlapaná stezka, dole mlha ke koruně ──
TRAIL = np.zeros((AH, AW))
for y in range(WALL_H, AH):
    for x in range(AW):
        TRAIL[y, x] = seg_dist(x, y)
for y in range(WALL_H, AH):
    for x in range(AW):
        down = (y - WALL_H) / (AH - WALL_H)
        v = 0.5 - down * 0.22 + (_vn(x * 1.2, y, 23, 6) - 0.5) * 0.3 + (_vn(x, y, 24, 2) - 0.5) * 0.14
        if _h(x, y, 25) % 31 == 0:
            v += 0.25                                                     # oblázky
        ledge = (y + _vn(x, 0, 26, 30) * 18) % 30
        if ledge < 1.2 and _vn(x, y, 27, 14) > 0.35:
            v = 0.08                                                      # hrana terasy (stín pod schodem)
        elif ledge < 2.4 and _vn(x, y, 27, 14) > 0.35:
            v += 0.2                                                      # osvětlená horní hrana
        if TRAIL[y, x] < 4:
            v = 0.7 - down * 0.2 + (_vn(x, y, 28, 3) - 0.5) * 0.1 - TRAIL[y, x] * 0.03   # vyšlapaná stezka (prach)
        img[y, x] = _pick(SCREE, max(0.0, min(1.0, v)), x, y)
for y in range(270, AH):                                                  # mlha zespodu (koruna, les)
    for x in range(AW):
        k = ((y - 270) / 82) ** 1.6
        m = _vn(x * 0.6, y * 1.3, 29, 18)
        a = math.floor(min(0.85, k * (0.6 + 0.6 * m)) * 5) / 5
        if a > 0:
            img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([40, 92, 76]) * a, 0, 255)

# schody Lumenců dolů vlevo (k Chrámu) a kolíky do mlhy vpravo (k Lesu)
LU.carved_stairs(img, 6, 300, 40, AH, steps=8, width=30)

# ── pramínky vody ze skály: tečou dolů a mizí v kořenech ────────────────────
RIVULETS = [(26, WALL_H, 300), (118, WALL_H, 240), (160, WALL_H, 150)]
wet_ends = []
for (rx, ry, ry1) in RIVULETS:
    x = float(rx)
    for y in range(ry, ry1):
        x += math.sin(y / 11 + rx) * 0.35 + (_vn(rx, y, 30, 9) - 0.5) * 0.6
        X = int(x)
        if 0 < X < AW - 1:
            img[y, X, :3] = (54, 78, 96); img[y, X + 1, :3] = (30, 44, 56)
            if y % 5 == 0:
                img[y, X, :3] = (120, 160, 190)                           # odlesk
    wet_ends.append((int(x), ry1))

# ── kořeny (5/8): ze stěny vpravo dolů přes svah ke kmeni (dolů vlevo); pijí vodu ──
def chain(*pts, n=24):
    """Plynulá křivka přes víc bodů (navazující kvadratické úseky přes středy)."""
    out = []
    mids = [pts[0]] + [((a[0] + b[0]) / 2, (a[1] + b[1]) / 2) for a, b in zip(pts[1:-1], pts[2:])] + [pts[-1]]
    for i in range(len(pts) - 2):
        out += bez(mids[i], pts[i + 1], mids[i + 1], n)
    return out


root(sc, chain((182, 36), (132, 84), (154, 150), (112, 222), (66, 286), (44, 360)), 15, 8, solid=False, lift=0.7, d=0.4)
root(sc, chain((-6, 146), (30, 178), (14, 214), (26, 252)), 8, 3, solid=False, lift=0.5, d=0.45)
root(sc, chain((128, WALL_H - 4), (104, 92), (78, 104), (64, 128)), 6, 2, solid=False, lift=0.4, d=0.45)
root(sc, chain((150, 160), (172, 196), (160, 250)), 6, 2, solid=False, lift=0.4, d=0.45)
for (ex, ey) in wet_ends:                                                 # kořínek, který pije z pramínku
    root(sc, bez((ex - 10, ey + 10), (ex - 2, ey + 4), (ex, ey)), 4, 1.2, solid=False, lift=0.2, d=0.5)
    for y in range(ey - 3, ey + 6):
        for x in range(ex - 6, ex + 7):
            if 0 <= x < AW and 0 <= y < AH and ((x - ex) / 6) ** 2 + ((y - ey - 1) / 4) ** 2 < 1:
                img[y, x, :3] = (img[y, x, :3] * 0.6 + np.array([30, 44, 56]) * 0.4).astype(np.uint8)

rmask = (sc.depth > 0.3) & (sc.depth < 0.5)
img[rmask, :3] = (img[rmask, :3] * 0.66).astype(np.uint8)
for _ in range(900):                                                      # suť přisypaná přes okraje kořenů
    x, y = int(rng.integers(2, AW - 2)), int(rng.integers(WALL_H, AH - 2))
    if rmask[y, x] and not rmask[y - 2:y + 3, x - 2:x + 3].all():
        img[y, x] = _pick(SCREE, 0.4 + rng.random() * 0.3, x, y)

# tůňka u vodočetné latě (skoro vypitá – hladina pod nulou)
for y in range(236, 252):
    for x in range(112, 150):
        m = ((x - 131) / 18) ** 2 + ((y - 244) / 7) ** 2
        if m < 1:
            img[y, x, :3] = (22, 34, 44) if m < 0.55 else (40, 52, 58)
            if m < 0.55 and (x + y) % 9 == 0:
                img[y, x, :3] = (70, 100, 120)
        elif m < 1.25:
            img[y, x, :3] = (img[y, x, :3] * 0.7).astype(np.uint8)        # mokrý okraj – kdysi výš

# ── trosky Řádu ─────────────────────────────────────────────────────────────
sc.place(O.theodolite(), 116, 132, foot=(18, 6))
sc.place(O.lean(O.count_board("SČÍTÁNÍ"), 0.3), 70, 186, foot=(40, 6))
sc.place(O.water_gauge(), 140, 240, foot=(6, 3))
sc.place(LU.longhouse_ribs(56, 34, sink=0.8, seed=5), 148, 318, foot=(50, 10))
STAKES = [(70, 84), (58, 100), (44, 124), (60, 152), (84, 176), (104, 204), (110, 236), (100, 262), (118, 290), (140, 314), (158, 330)]
for (x, y) in STAKES:
    x += 9
    sc.place(O.stake(18), x, y, foot=(2, 2))
for (a, b) in zip(STAKES, STAKES[1:]):                                     # lano mezi kolíky – místy přetržené
    if _h(a[0], a[1], 33) % 3 == 0:
        continue
    for t in np.linspace(0, 1, 40):
        X = int(a[0] + 9 + (b[0] - a[0]) * t); Y = int(a[1] - 12 + (b[1] - a[1]) * t + math.sin(t * math.pi) * 4)
        if 0 <= Y < AH and 0 <= X < AW:
            img[Y, X, :3] = (150, 140, 116)
lantern = Spr(7, 5)                                                       # spadlá baterka Řádu – pořád svítí
lantern.rect(0, 1, 4, 4, O.OLIVE[3]); lantern.rect(5, 0, 6, 4, c(0xFFE8B0))
sc.place(lantern, 96, 150, collide=False)

# ── bezejmenní: všichni hlavou vzhůru ──────────────────────────────────────
bodies = []
V7 = [(12 + i * 8 + (i % 2) * 2, 266 + (i % 3) * 4 - i) for i in range(9)]  # devět V7 vedle sebe
for i, (x, y) in enumerate(V7):
    bodies.append((x, y, dict(seed=70 + i, reach=i % 3 != 1, overgrown=0.3)))
tries = 0
while len(bodies) < 30 and tries < 6000:
    tries += 1
    x, y = int(rng.integers(8, AW - 8)), int(rng.integers(WALL_H + 30, 320))
    if TRAIL[y - 10, x] < 10 or TRAIL[y, x] < 10:
        continue
    if any(abs(x - bx) < 12 and abs(y - by) < 16 for (bx, by, _) in bodies):
        continue
    if 110 < x < 152 and 228 < y < 262:                                   # tůňka
        continue
    bodies.append((x, y, dict(seed=int(rng.integers(0, 9999)), reach=rng.random() < 0.5,
                              overgrown=min(1.0, (y - WALL_H) / 260 * rng.uniform(0.4, 1.4)))))
FADED = [c(0x1E1E18), c(0x3A3A2E), c(0x5C5A48), c(0x7E7A62), c(0x9E987C), c(0xBAB496)]


def lying(spr, x, y):
    """Ležící tělo: zkrácené na výšku (leží na svahu), vybledlá kombinéza, nohy napůl v suti."""
    h2 = int(spr.h * 0.66)
    out = Spr(spr.w, h2)
    for yy in range(h2):
        sy = min(spr.h - 1, int(yy / 0.66))
        for xx in range(spr.w):
            px = spr.a[sy, xx]
            if px[3]:
                lum = (int(px[0]) * 3 + int(px[1]) * 4 + int(px[2])) / 8 / 255
                col = np.array(_pick(FADED, min(1.0, lum * 2.6), xx, yy)[:3], float)
                if px[0] > px[1] + 20 or (px[0] > 180 and px[1] > 170):
                    col = px[:3].astype(float)                            # štítek a ruce nechat
                ground = img[min(AH - 1, y - h2 + yy), min(AW - 1, max(0, x - spr.w // 2 + xx)), :3].astype(float)
                dust = 0.05 + 0.45 * max(0.0, (yy / h2) - 0.55) / 0.45     # nohy v suti
                out.a[yy, xx, :3] = np.clip(col * (1 - dust) + ground * dust, 0, 255); out.a[yy, xx, 3] = 255
    for yy in range(h2):                                                  # spodní obrys – leží, nevrhá stín
        for xx in range(spr.w):
            if out.a[yy, xx, 3] and (yy + 1 >= h2 or not out.a[yy + 1, xx, 3]):
                out.a[yy, xx, :3] = (out.a[yy, xx, :3] * 0.55).astype(np.uint8)
    for yy in range(h2 + 2):                                              # přitisknutý stín kolem těla
        for xx in range(-1, spr.w + 1):
            X, Y = x - spr.w // 2 + xx, y - h2 + yy
            inside = 0 <= yy < h2 and 0 <= xx < spr.w and out.a[yy, xx, 3]
            near = any(0 <= yy + dy < h2 and 0 <= xx + dx < spr.w and out.a[yy + dy, xx + dx, 3] for dy in (-1, -2) for dx in (-1, 0, 1))
            if not inside and near and 0 <= X < AW and 0 <= Y < AH:
                img[Y, X, :3] = (img[Y, X, :3] * 0.6).astype(np.uint8)
    return out


for (x, y, kw) in bodies:
    sc.place(lying(O.fallen(**kw), x, y), x, y, collide=False, shadow=False, z=y - 30)

floor = np.zeros((AH, AW), bool); floor[WALL_H:AH, :] = True
sc.shadows(floor)

# ── skalní stěna: fasáda Lumenců stahovaná do skály, nástřik NEPOČÍTEJTE JE ──
img[:WALL_H] = T.cavern_wall(AW, WALL_H).a
fac = O.lean(LU.ruin_facade(46, 64), 0.12)
for yy in range(fac.h):                                                   # spodek fasády už je pod sutí
    for xx in range(fac.w):
        Y, X = WALL_H + 8 - fac.h + yy, 114 + xx
        if fac.a[yy, xx, 3] and 0 <= Y < WALL_H + 2 and Y < WALL_H - 2 + int(_vn(X, 0, 34, 6) * 6):
            img[Y, X] = fac.a[yy, xx]
SG.painted(img, 12, 16, "NEPOČÍTEJTE JE.", c(0xB8342A), scale=1, seed=12, wear=0.1)
root(sc, bez((150, -10), (128, 20), (126, WALL_H + 10)), 10, 9, solid=False, d=0.35)      # kořen přes fasádu
for y in range(0, WALL_H + 12):
    for x in range(100, 160):
        if sc.depth[y, x] == 0.35:
            img[y, x, :3] = (img[y, x, :3] * 0.66).astype(np.uint8)
block[:WALL_H + 4, :] = True
sc.zbuf[:WALL_H] = -1e9
sc.draw_sprites()

# chůze: celý svah volný kromě stěny, okrajů a předmětů; dolů jen po schodech a ke kolíkům
block[WALL_H + 4:, :] = False
for o in sc.placed:
    pass
block[:, :3] = True; block[:, AW - 3:] = True
for (x, y) in STAKES:
    block[y - 2:y, x + 8:x + 10] = True
block[300:AH, 42:136] = True                                              # dole prudký sráz do mlhy
block[300:AH, 6:42] = False; block[300:AH, 136:170] = False
block[176:188, 50:92] = True                                              # převrácená tabule
block[126:132, 107:125] = True                                            # stativ
block[296:318, 124:172] = True                                            # žebra dlouhého domu
block[300:318, 140:160] = False

fungi = []
for _ in range(9000):
    x, y = int(rng.integers(4, AW - 4)), int(rng.integers(WALL_H + 6, AH - 6))
    if sc.depth[y, x] and not sc.depth[y, x + 2] and len(fungi) < 12:
        fungi.append((x, y))
        img[y, x, :3] = (150, 210, 255); img[y, x - 1, :3] = (80, 140, 220); img[y + 1, x, :3] = (40, 70, 120)

sc.light(
    warm=[(100, 150, 18, 10, 0.7)],
    cold=[(x, y, 7, 6, 0.6) for (x, y) in fungi] + [(136, 28, 26, 22, 0.6), (24, 330, 22, 16, 0.6),
          (60, 120, 50, 40, 0.35), (120, 200, 50, 40, 0.3), (50, 260, 40, 30, 0.3)],
    ember=[],
    dark_k=0.24, warm_k=0.3, cold_k=0.16)
for y in range(290, AH):                                                  # zelená záře zespodu přes tmu
    for x in range(AW):
        k = ((y - 290) / 62) ** 1.3 * 0.35
        img[y, x, :3] = np.clip(img[y, x, :3] * (1 - k) + np.array([52, 120, 96]) * k, 0, 255)
sc.export("root_nameless", "gen_nameless.py")
print("BODIES", len(bodies), "V7", V7[:3], "FUNGI", fungi[:6])
