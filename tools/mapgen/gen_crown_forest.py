"""
Použití:  python tools/mapgen/gen_crown_forest.py
Výstup:   app/src/main/res/drawable-nodpi/root_forest.png, app/src/main/assets/walk/root_forest.txt,
          tools/mapgen/root_forest_debug.png

Zóna 3 (Akt III – Hlubina), lokace 7 z 8: LES POD KORUNOU (176 × 560, kamera jede s hráčem, docs/adr/0106).
Dno dutiny přímo pod obrácenou korunou Spící. „Les, který nejde projít“: stromy tu nerostou ze země – visí shora.
Holé větve koruny sestupují z tmy jako kmeny obráceného lesa, dole se dělí do jemných větviček, které se dotýkají
země jako záclony, a na konečcích svítí. Z rány v kmeni (vysoko nad námi) stéká zelená mlha zapomnění a leží tu
ve vrstvách. Pohlcení 7/8: z Řádu zbyla jen ztracená sonda zapletená ve větvičkách, která pořád pípá. Na kůře
visících větví vyryté značky, které se rozmazávají, když se na ně díváš. Stopy chodí v kruhu. Dole, kde se jedna
větev skloní až k zemi a dá se po ní vylézt ke kmeni, leží přes cestu Drakirra (NPC, souboj přijde s kapitolou VI).
Setkání PŘÍRODA a JED. Živé detaily: InteriorFxView.ROOT_FOREST. NODES / EDGES jsou ZDROJ PRAVDY i pro RootForest.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import Spr, c, _pick, _h, _vn, STEEL, BARK  # noqa: E402
from scene import Scene  # noqa: E402
import order as O, signs as SG  # noqa: E402

AW, AH = 176, 560
NODES = {
    "lp_vstup": (90, 22), "lp_cesta1": (70, 96), "lp_znacky1": (124, 140), "lp_kruh": (56, 200), "lp_sonda": (120, 250),
    "lp_cesta2": (82, 300), "lp_znacky2": (38, 352), "lp_ticho": (112, 392), "lp_cesta3": (70, 440), "lp_drakirra": (88, 492),
    "lp_kmen": (88, 548),
}
EDGES = [("lp_vstup", "lp_cesta1"), ("lp_cesta1", "lp_znacky1"), ("lp_cesta1", "lp_kruh"), ("lp_kruh", "lp_sonda"),
         ("lp_znacky1", "lp_sonda"), ("lp_kruh", "lp_cesta2"), ("lp_sonda", "lp_cesta2"), ("lp_cesta2", "lp_znacky2"),
         ("lp_cesta2", "lp_ticho"), ("lp_znacky2", "lp_cesta3"), ("lp_ticho", "lp_cesta3"), ("lp_cesta3", "lp_drakirra"),
         ("lp_drakirra", "lp_kmen")]
sc = Scene(AW, AH, 0, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(77)


def seg_dist(x, y):
    best = 1e9
    for a, b in EDGES:
        (x0, y0), (x1, y1) = NODES[a], NODES[b]
        dx, dy = x1 - x0, y1 - y0
        t = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy)))
        best = min(best, math.hypot(x - x0 - dx * t, y - y0 - dy * t))
    return best


# ── zem: popel kůry, mech, tůňky; vyšlapaná stezka ──────────────────────────
GROUND = [c(0x0C1210), c(0x141C19), c(0x1C2823), c(0x26342D), c(0x324238), c(0x405246)]
TRAIL = np.zeros((AH, AW))
for y in range(AH):
    for x in range(AW):
        TRAIL[y, x] = seg_dist(x, y)
        v = 0.4 + (_vn(x * 1.2, y * 1.2, 11, 7) - 0.5) * 0.3 + (_vn(x, y, 12, 2) - 0.5) * 0.14
        if _h(x, y, 13) % 23 == 0:
            v += 0.25                                                     # šupinky kůry
        if TRAIL[y, x] < 4:
            v = 0.56 + (_vn(x, y, 14, 3) - 0.5) * 0.1 - TRAIL[y, x] * 0.03
        img[y, x] = _pick(GROUND, max(0.0, min(1.0, v)), x, y)
for (cx, cy, rx, ry) in ((30, 120, 14, 6), (150, 330, 16, 7), (40, 470, 18, 6), (140, 186, 10, 5)):   # tůňky
    for y in range(cy - ry, cy + ry + 1):
        for x in range(cx - rx, cx + rx + 1):
            d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
            if d < 1:
                img[y, x, :3] = (14, 30, 28) if d < 0.7 else (24, 44, 40)
                if (x + 2 * y) % 11 == 0 and d < 0.6:
                    img[y, x, :3] = (70, 140, 120)                        # odraz světel
                block[y, x] = True
# kruh stop: chodily tu dokola
for a in range(0, 360, 9):
    x, y = 56 + math.cos(math.radians(a)) * 20, 200 + math.sin(math.radians(a)) * 11
    for (dx, dy) in ((0, 0), (1, 0)):
        img[int(y) + dy, int(x) + dx + (1 if (a // 9) % 2 else 0), :3] = (14, 20, 18)


# ── visící větve koruny (obrácený les) ──────────────────────────────────────
def hanging(fx, fy, h, w, seed, glyphs=False):
    """Větev visící shora až k zemi (pata fx, fy): nahoře tlustá a mizí do tmy, dolů se tenčí, kroutí, má boční
    výhonky a dole se rozpadá na záclonu větviček, které se dotknou země; na konečcích svítí. Kreslí se rovnou do
    mapy s průhledností (vrchol plynule zmizí). glyphs = vyryté značky na kůře nad zemí."""
    r = np.random.default_rng(seed)
    tips = []

    def put(x, y, col, a=1.0):
        X, Y = int(round(x)), int(round(y))
        if 0 <= X < AW and 0 <= Y < AH and a > 0:
            img[Y, X, :3] = np.clip(img[Y, X, :3] * (1 - a) + np.asarray(col[:3], float) * a, 0, 255)

    top = fy - h
    x = fx + r.uniform(-10, 10)
    ph = r.uniform(0, 6)
    spine = []
    for i in range(h - 14):
        y = top + i
        t = i / h
        x += math.sin(i / 21 + ph) * 0.45 + (fx - x) * 0.02
        spine.append((x, y, t))
    for (x, y, t) in spine:
        ww = w * (1 - t) ** 0.7 + 0.8
        alpha = min(1.0, (t * h) / 46)                                    # vrchol mizí ve tmě
        for k in np.arange(-ww, ww + 0.5, 0.5):
            u = k / max(1.0, ww)
            v = 0.52 - u * 0.32 + (_vn(x + k, y * 0.4, seed, 3) - 0.5) * 0.2
            if abs(u) > 0.82:
                v = 0.1
            if abs(_vn((x + k) * 0.7, y * 0.1, seed + 1, 2) - 0.5) < 0.04:
                v -= 0.25
            put(x + k, y, _pick(BARK, max(0.0, min(1.0, v * 0.7)), int(x + k), int(y)), alpha)
    for k in range(int(r.integers(1, 3))):                                # boční výhonky
        i0 = int(r.integers(len(spine) // 3, len(spine) - 20))
        x, y, t = spine[i0]
        side = r.choice([-1, 1])
        a = math.pi / 2 + side * r.uniform(0.6, 1.1)
        ww = w * (1 - t) * 0.45 + 0.6
        for i in range(int(r.integers(14, 26))):
            a += (math.pi / 2 - a) * 0.06
            x += math.cos(a); y += math.sin(a)
            for kk in np.arange(-ww, ww + 0.5, 0.5):
                put(x + kk, y, BARK[2] if kk < 0 else BARK[1], min(1.0, max(0.0, (y - top) / 46)))
            ww = max(0.5, ww * 0.95)
        tips.append((x, y))
    bx, by, _ = spine[-1]
    for k in range(int(r.integers(6, 11))):                               # záclona větviček k zemi
        x, y = bx + r.uniform(-1.5, 1.5), by
        a = math.pi / 2 + r.uniform(-1.0, 1.0)
        for i in range(int(r.integers(10, 20))):
            a += (math.pi / 2 - a) * 0.12 + r.uniform(-0.2, 0.2)
            x += math.cos(a); y += math.sin(a) * 1.1
            put(x, y, BARK[2] if i < 5 else BARK[1], 0.9 if i < 8 else 0.6)
        tips.append((x, y))
    for (tx, ty) in tips:
        put(tx, ty, (170, 240, 220))
    if glyphs:
        for k in range(5):
            gx, gy, _ = spine[max(0, len(spine) - 44 + k * 7)]
            for (dx, dy) in ((0, 0), (1, 1), (2, 0), (1, 2)):
                put(gx - 1 + dx, gy + dy, (120, 210, 170) if k % 2 else (80, 150, 120))
    return tips


BR = [(28, 60, 150, 7), (150, 52, 140, 8), (110, 88, 120, 5), (18, 170, 160, 9), (100, 150, 110, 6), (158, 196, 150, 8),
      (24, 262, 140, 7), (152, 270, 130, 6), (56, 252, 100, 4), (130, 342, 140, 9), (14, 330, 120, 6), (62, 356, 110, 5),
      (150, 420, 150, 8), (20, 420, 140, 8), (110, 470, 120, 6), (40, 520, 130, 7), (146, 530, 140, 9), (90, 410, 90, 3)]
# ztracená sonda Řádu zapletená ve větvičkách
probe = Spr(22, 26)
for y in range(8, 24):
    for x in range(5, 17):
        if ((x - 11) / 6) ** 2 + ((y - 16) / 8) ** 2 < 1:
            probe.px(x, y, _pick(STEEL, 0.75 - (x - 11) / 12 * 0.5 - (y - 8) * 0.01, x, y))
probe.rect(8, 13, 13, 16, c(0x101418)); probe.px(10, 14, (90, 220, 140, 255)); probe.px(11, 14, (90, 220, 140, 255))
for y in range(0, 9):
    probe.px(11, y, STEEL[3])
probe.px(11, 0, O.RED[3])
for k in range(4):
    probe.line(2 + k * 5, 4 + k, 14 + k, 24, BARK[1])
SG.tiny(probe, 6, 18, "S8", c(0x2A2418))
sc.place(probe, 124, 244, foot=(12, 4))
# drobná ceduľka Řádu na kolíku: směrovky, které ukazují všude
post = Spr(30, 30)
for y in range(4, 30):
    post.px(14, y, O.WOOD_POLE[3]); post.px(15, y, O.WOOD_POLE[1])
for (yy, dx) in ((6, 1), (13, -1), (20, 1)):
    for x in range(0, 13):
        X = 16 + x if dx > 0 else 13 - x
        for k in range(4):
            post.px(X, yy + k, O.PAPER[1] if k in (0, 3) else O.PAPER[2])
SG.tiny(post, 17, 7, "VEN", c(0x2A2418)); SG.tiny(post, 1, 14, "VEN", c(0x2A2418)); SG.tiny(post, 17, 21, "VEN", c(0x2A2418))
sc.place(post, 86, 206, foot=(4, 3))

sc.shadows(np.ones((AH, AW), bool), k=0.35)
tips_all = []
for i, (x, y, h, w) in sorted(enumerate(BR), key=lambda e: e[1][1]):
    if TRAIL[min(AH - 1, y), x] < 9:
        continue
    tips_all += hanging(x, y, h + 40, w, i * 7 + 3, glyphs=i in (1, 10))
sc.draw_sprites()

# větev, po které se dá vylézt ke kmeni: nejtlustší ze všech, sklání se až k zemi dole uprostřed
hanging(92, AH + 6, 150, 15, 999)
for y in range(AH - 40, AH):                                              # zářezy po stoupání (stupy)
    if y % 7 == 0:
        for x in range(84, 98):
            img[y, x, :3] = (img[y, x, :3] * 0.55).astype(np.uint8)

block[:, :] = True
for y in range(AH):
    for x in range(AW):
        if TRAIL[y, x] < 9 and 4 < x < AW - 4:
            block[y, x] = False
for o in sc.placed:                                                       # paty předmětů
    s_ = o["spr"]; fx = o["x"] + s_.w // 2; fy = o["y"] + s_.h
    block[max(0, fy - 4):fy, max(0, fx - 4):fx + 4] = True
for (x, y, h, w) in BR:                                                   # paty větví
    if TRAIL[min(AH - 1, y), x] >= 9:
        block[max(0, y - 4):y, max(0, x - 4):x + 4] = True

# vrstvy zelené mlhy zapomnění (nejhustší na mýtině ticha)
for y in range(AH):
    for x in range(AW):
        m = _vn(x * 0.6 + 7, y * 1.4, 41, 22) * 0.7 + _vn(x, y * 1.1, 42, 7) * 0.3
        dense = 0.18 + 0.4 * max(0.0, 1 - math.hypot((x - 112) / 60, (y - 392) / 50))
        a = math.floor(max(0.0, (m - 0.5) * 1.6) * dense * 4 * 4) / 4 / 4 * 4 * 0.5
        if a > 0:
            img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([70, 150, 120]) * a, 0, 255)

sc.light(
    warm=[(124, 238, 10, 8, 0.3)],
    ember=[(88, 476, 34, 22, 0.8)],
    cold=[(x, y, 9, 7, 0.55) for (x, y) in tips_all if 0 <= y < AH][:90] + [(112, 392, 50, 36, 0.6), (88, 22, 30, 16, 0.5)],
    dark_k=0.42, warm_k=0.25, cold_k=0.22)
sc.export("root_forest", "gen_crown_forest.py")
print("TIPS", len(tips_all), [(int(x), int(y)) for (x, y) in tips_all if 0 <= y < AH][::14][:12])
