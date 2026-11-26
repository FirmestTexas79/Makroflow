"""
Použití:  python tools/mapgen/gen_crown_forest.py
Výstup:   app/src/main/res/drawable-nodpi/root_forest.png, app/src/main/assets/walk/root_forest.txt,
          tools/mapgen/root_forest_debug.png

Zóna 3 (Akt III – Hlubina), lokace 7 z 8: LES POD KORUNOU (704 × 1120 – rozlehlý les, kamera jede s hráčem oběma směry,
docs/adr/0106 a 0107). Dno dutiny přímo pod obrácenou korunou Spící. „Les, který nejde projít“: stromy tu nerostou ze
země – visí shora. Holé větve koruny sestupují z tmy jako kmeny obráceného lesa a dole se rozpadají na záclony
větviček, které na konečcích svítí. Z rány v kmeni stéká zelená mlha zapomnění a leží tu ve vrstvách; stezky se v ní
větví a vracejí. Místa: kruh stop (tady chodí v kruhu ti, kdo zapomněli kam – a u něj Gudwin, který zapomenout nemůže),
strom značek, ztracená sonda Řádu, opuštěný tábor výpravy 6, tůň, mýtina ticha (nejhustší mlha), lucerny na větvích,
dole brána ze zkřížených větví na Práh. Mechové palouky = divoká setkání (PŘÍRODA, JED). Sedm jmenovek (sběratelské
předměty vedlejšího úkolu Jména v mlze) se kreslí ve hře, ne do mapy. Živé detaily: InteriorFxView.ROOT_FOREST.
NODES / EDGES jsou ZDROJ PRAVDY i pro RootForest.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import Spr, c, _pick, _h, _vn, STEEL, BARK  # noqa: E402
from scene import Scene  # noqa: E402
import order as O, signs as SG  # noqa: E402

AW, AH = 704, 1120
NODES = {
    "lp_vstup": (352, 24), "lp_cesta1": (330, 140), "lp_znacky1": (440, 196), "lp_mech1": (246, 214), "lp_kruh": (168, 300),
    "lp_gudwin": (214, 318), "lp_jm1": (86, 236), "lp_sonda": (560, 300), "lp_jm2": (646, 226), "lp_mech2": (470, 340),
    "lp_cesta2": (360, 430), "lp_mech3": (500, 560), "lp_tabor": (592, 520), "lp_jm3": (660, 610), "lp_mech4": (232, 600),
    "lp_znacky2": (118, 520), "lp_jm4": (58, 612), "lp_tun": (176, 760), "lp_jm5": (84, 840), "lp_ticho": (420, 720),
    "lp_jm6": (480, 792), "lp_mech5": (300, 826), "lp_lucerny": (604, 816), "lp_jm7": (652, 904), "lp_mech6": (530, 920),
    "lp_cesta3": (342, 930), "lp_brana": (352, 1012), "lp_prah": (352, 1100),
}
EDGES = [("lp_vstup", "lp_cesta1"), ("lp_cesta1", "lp_znacky1"), ("lp_cesta1", "lp_mech1"), ("lp_mech1", "lp_kruh"),
         ("lp_kruh", "lp_gudwin"), ("lp_kruh", "lp_jm1"), ("lp_gudwin", "lp_mech1"), ("lp_znacky1", "lp_sonda"),
         ("lp_sonda", "lp_jm2"), ("lp_sonda", "lp_mech2"), ("lp_mech2", "lp_cesta2"), ("lp_cesta1", "lp_cesta2"),
         ("lp_cesta2", "lp_mech4"), ("lp_mech4", "lp_znacky2"), ("lp_znacky2", "lp_jm4"), ("lp_gudwin", "lp_mech4"),
         ("lp_cesta2", "lp_mech3"), ("lp_mech2", "lp_mech3"), ("lp_mech3", "lp_tabor"), ("lp_tabor", "lp_jm3"),
         ("lp_mech4", "lp_tun"), ("lp_tun", "lp_jm5"), ("lp_tun", "lp_mech5"), ("lp_cesta2", "lp_ticho"),
         ("lp_ticho", "lp_jm6"), ("lp_ticho", "lp_mech5"), ("lp_ticho", "lp_lucerny"), ("lp_lucerny", "lp_jm7"),
         ("lp_lucerny", "lp_mech6"), ("lp_mech3", "lp_lucerny"), ("lp_mech5", "lp_cesta3"), ("lp_mech6", "lp_cesta3"),
         ("lp_ticho", "lp_cesta3"), ("lp_cesta3", "lp_brana"), ("lp_brana", "lp_prah")]
CLEARINGS = {"lp_kruh": 40, "lp_ticho": 46, "lp_tabor": 34, "lp_tun": 30, "lp_lucerny": 26, "lp_sonda": 22,
             "lp_brana": 30, "lp_vstup": 18, "lp_prah": 18}
MOSS = ["lp_mech1", "lp_mech2", "lp_mech3", "lp_mech4", "lp_mech5", "lp_mech6"]
sc = Scene(AW, AH, 0, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(77)
YY, XX = np.mgrid[0:AH, 0:AW].astype(float)


def hashf(ix, iy, s):
    v = (np.asarray(ix).astype(np.int64) * 374761393 + np.asarray(iy).astype(np.int64) * 668265263 + s * 1442695041) & 0xFFFFFFFF
    v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((v ^ (v >> 16)) & 0xFFFF) / 65535.0


def vn(X, Y, s, cell):
    """Hodnotový šum po celých polích (numpy)."""
    gx, gy = X / cell, Y / cell
    ix, iy = np.floor(gx), np.floor(gy)
    fx, fy = gx - ix, gy - iy
    u, w = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = hashf(ix, iy, s), hashf(ix + 1, iy, s)
    c_, d = hashf(ix, iy + 1, s), hashf(ix + 1, iy + 1, s)
    return (a * (1 - u) + b * u) * (1 - w) + (c_ * (1 - u) + d * u) * w


# ── stezky: vzdálenost k síti cest (zvlněná šumem) a palouky ───────────────────
TRAIL = np.full((AH, AW), 1e9)
for a, b in EDGES:
    (x0, y0), (x1, y1) = NODES[a], NODES[b]
    dx, dy = x1 - x0, y1 - y0
    t = np.clip(((XX - x0) * dx + (YY - y0) * dy) / (dx * dx + dy * dy), 0, 1)
    TRAIL = np.minimum(TRAIL, np.hypot(XX - x0 - dx * t, YY - y0 - dy * t))
TRAIL += (vn(XX, YY, 5, 26) - 0.5) * 10
OPEN = np.full((AH, AW), 1e9)                                             # palouky: vzdálenost od okraje (záporné = uvnitř)
for n, r in list(CLEARINGS.items()) + [(m, 24) for m in MOSS]:
    x0, y0 = NODES[n]
    OPEN = np.minimum(OPEN, np.hypot(XX - x0, (YY - y0) * 1.25) - r - (vn(XX, YY, 6, 9) - 0.5) * 10)

# ── zem: popel kůry, mech, šupinky; vyšlapané stezky; mechové palouky ─────────
GROUND = np.array([(12, 18, 16), (20, 28, 25), (28, 40, 35), (38, 52, 45), (50, 66, 56), (64, 82, 70), (84, 98, 84), (104, 114, 98)], float)
v = 0.38 + (vn(XX, YY, 11, 9) - 0.5) * 0.3 + (vn(XX, YY, 12, 3) - 0.5) * 0.14
v += (hashf(XX, YY, 13) < 0.05) * 0.22                                    # šupinky kůry
path = TRAIL < 7
v = np.where(path, 0.74 + (vn(XX, YY, 14, 4) - 0.5) * 0.12 - np.clip(TRAIL, 0, 7) * 0.03, v)
v = np.where((TRAIL >= 7) & (TRAIL < 9), v - 0.12, v)                    # okraj stezky
v = np.where((OPEN < 0) & ~path, 0.5 + (vn(XX, YY, 15, 6) - 0.5) * 0.16, v)
bayer = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0
q = np.clip(v, 0, 0.999) * (len(GROUND) - 1)
qi = np.floor(q + (bayer[(YY % 4).astype(int), (XX % 4).astype(int)] - 0.5) * 0.9).clip(0, len(GROUND) - 1).astype(int)
img[:, :, :3] = GROUND[qi]
MOSSC = np.array([(26, 60, 40), (40, 92, 58), (66, 130, 80), (120, 200, 140)], float)
for m in MOSS:                                                            # mechové palouky: zelené, s drobnými svítícími kapradinami
    x0, y0 = NODES[m]
    d = np.hypot(XX - x0, (YY - y0) * 1.3) / (26 + (vn(XX, YY, 16, 7) - 0.5) * 10)
    mm = d < 1
    tone = np.clip(0.75 - d * 0.5 + (vn(XX, YY, 17, 2) - 0.5) * 0.3, 0, 0.99) * 3
    img[mm, :3] = MOSSC[tone[mm].astype(int)]
    fr = mm & (hashf(XX, YY, 18) < 0.03)
    img[fr, :3] = (150, 240, 190)
POOLS = [(110, 736, 26, 11), (80, 410, 18, 8), (640, 700, 22, 9), (300, 520, 14, 6), (520, 1040, 20, 8), (110, 1000, 22, 9)]
for (cx, cy, rx, ry) in POOLS:                                            # tůně
    d = ((XX - cx) / rx) ** 2 + ((YY - cy) / ry) ** 2
    pm = d < 1
    img[pm, :3] = np.where((d[pm] < 0.7)[:, None], np.array([12, 28, 26]), np.array([22, 42, 38]))
    sh = pm & (((XX + 2 * YY) % 11) == 0) & (d < 0.6)
    img[sh, :3] = (70, 140, 120)
    block[pm] = True
kx, ky = NODES["lp_kruh"]                                                 # kruh stop: chodily tu dokola
for a in range(0, 360, 4):
    for rr in (26, 30):
        x, y = kx + math.cos(math.radians(a)) * rr, ky + math.sin(math.radians(a)) * rr * 0.55
        if (a // 4) % 2:
            img[int(y), int(x), :3] = (10, 16, 14)
            img[int(y), int(x) + 1, :3] = (10, 16, 14)


def put(x, y, col, a=1.0):
    X, Y = int(round(x)), int(round(y))
    if 0 <= X < AW and 0 <= Y < AH and a > 0:
        if HIGH[0] is not None and (TRAIL[Y, X] < 10 or OPEN[Y, X] < 0):
            a *= HIGH[0]                                                  # nad stezkou je větev vysoko – jen tušená

        img[Y, X, :3] = np.clip(img[Y, X, :3] * (1 - a) + np.asarray(col[:3], float) * a, 0, 255)


HIGH = [None]                                                             # průhlednost větví nad stezkou (při kreslení větví)


# ── visící větve koruny (obrácený les) ──────────────────────────────────────
def hanging(fx, fy, h, w, seed, glyphs=False):
    """Větev visící shora až k zemi (pata fx, fy): nahoře tlustá a mizí do tmy, dolů se tenčí, kroutí, má boční
    výhonky a dole se rozpadá na záclonu větviček, které se dotknou země; na konečcích svítí."""
    r = np.random.default_rng(seed)
    tips = []
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
        if y < -2 or y >= AH:
            continue
        ww = w * (1 - t) ** 0.7 + 0.8
        up = fy - y                                                       # výška nad zemí: dole plná, výš se ztrácí ve tmě
        alpha = min(1.0, (t * h) / 46) * (1.0 if up < 60 else max(0.14, 1 - (up - 60) / 70))
        HIGH[0] = 0.15 if up > 34 else 1.0
        for k in np.arange(-ww, ww + 0.5, 0.5):
            u = k / max(1.0, ww)
            v_ = 0.52 - u * 0.32 + (_vn(x + k, y * 0.4, seed, 3) - 0.5) * 0.2
            if abs(u) > 0.82:
                v_ = 0.1
            if abs(_vn((x + k) * 0.7, y * 0.1, seed + 1, 2) - 0.5) < 0.04:
                v_ -= 0.25
            put(x + k, y, _pick(BARK, max(0.0, min(1.0, v_ * 0.7)), int(x + k), int(y)), alpha)
    for k in range(int(r.integers(1, 3))):                                # boční výhonky
        i0 = int(r.integers(len(spine) // 3, len(spine) - 20))
        x, y, t = spine[i0]
        side = r.choice([-1, 1])
        a = math.pi / 2 + side * r.uniform(0.6, 1.1)
        ww = w * (1 - t) * 0.45 + 0.6
        for i in range(int(r.integers(14, 26))):
            a += (math.pi / 2 - a) * 0.06
            x += math.cos(a); y += math.sin(a)
            HIGH[0] = 0.25 if fy - y > 40 else 1.0
            for kk in np.arange(-ww, ww + 0.5, 0.5):
                put(x + kk, y, BARK[2] if kk < 0 else BARK[1], min(1.0, max(0.0, (y - top) / 46)) * (1.0 if fy - y < 70 else 0.45))
            ww = max(0.5, ww * 0.95)
        tips.append((x, y))
    HIGH[0] = None
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
    FEET.append((int(fx), int(fy)))
    return tips


FEET = []
# ── rekvizity ───────────────────────────────────────────────────────────────
probe = Spr(22, 26)                                                       # ztracená sonda Řádu zapletená ve větvičkách
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
sx_, sy_ = NODES["lp_sonda"]
sc.place(probe, sx_ + 4, sy_ - 8, foot=(12, 4))
post = Spr(30, 30)                                                        # kolík se směrovkami, které všechny ukazují VEN
for y in range(4, 30):
    post.px(14, y, O.WOOD_POLE[3]); post.px(15, y, O.WOOD_POLE[1])
for (yy, dx) in ((6, 1), (13, -1), (20, 1)):
    for x in range(0, 13):
        X = 16 + x if dx > 0 else 13 - x
        for k in range(4):
            post.px(X, yy + k, O.PAPER[1] if k in (0, 3) else O.PAPER[2])
SG.tiny(post, 17, 7, "VEN", c(0x2A2418)); SG.tiny(post, 1, 14, "VEN", c(0x2A2418)); SG.tiny(post, 17, 21, "VEN", c(0x2A2418))
sc.place(post, kx + 40, ky - 6, foot=(4, 3))
tx_, ty_ = NODES["lp_tabor"]                                              # opuštěný tábor výpravy 6: stany obrostlé větvičkami
sc.place(O.ridge_tent(6, overgrowth=0.7, seed=3), tx_ + 30, ty_ - 8)
sc.place(O.ridge_tent(6, overgrowth=0.9, seed=4, hw=14, L=16), tx_ - 30, ty_ - 16)
sc.place(O.army_crate(seed=2), tx_ + 4, ty_ + 22, foot=(12, 4))
sc.place(O.army_crate(seed=5, w=14, h=12), tx_ + 22, ty_ + 26, foot=(10, 4))
LANTERNS = []
lx_, ly_ = NODES["lp_lucerny"]
for k, (dx, dy) in enumerate(((-30, -40), (-6, -52), (20, -38), (36, -56), (-44, -16), (44, -12))):   # lucerny na větvičkách
    x, y = lx_ + dx, ly_ + dy
    for yy in range(int(y) - 18, int(y)):
        put(x, yy, BARK[1], 0.8)
    for (ddx, ddy) in ((-1, 0), (0, 0), (1, 0), (-1, 1), (0, 1), (1, 1), (0, 2)):
        put(x + ddx, y + ddy, (255, 214, 140) if k % 2 else (190, 240, 220))
    put(x, y - 1, (60, 50, 40))
    LANTERNS.append((x, y))
sc.shadows(np.ones((AH, AW), bool), k=0.35)

# ── visící větve: všude mimo stezky a palouky; značené větve u značek ─────────
tips_all = []
placed = []
for i in range(900):
    x, y = float(rng.uniform(8, AW - 8)), float(rng.uniform(60, AH - 10))
    if TRAIL[int(y), int(x)] < 16 or OPEN[int(y), int(x)] < 8 or any(math.hypot(x - px, (y - py) * 0.6) < 44 for (px, py) in placed):
        continue
    placed.append((x, y))
for i, (x, y) in enumerate(sorted(placed, key=lambda p: p[1])):
    tips_all += hanging(x, y, int(rng.uniform(150, 230)), float(rng.uniform(4, 10)), i * 7 + 3)
for n in ("lp_znacky1", "lp_znacky2"):                                    # značené větve
    x, y = NODES[n]
    tips_all += hanging(x + (26 if n == "lp_znacky1" else -30), y - 2, 180, 9, 500 + len(n), glyphs=True)
sc.draw_sprites()
bx_, by_ = NODES["lp_brana"]                                              # brána: dvě nejtlustší větve kříží cestu, mezi nimi světlo
hanging(bx_ - 34, AH + 4, 200, 17, 998)
hanging(bx_ + 34, AH + 4, 200, 17, 999)
glow = np.clip(1 - np.abs(XX - bx_) / 22, 0, 1) * np.clip((YY - (by_ - 30)) / 110, 0, 1) * 0.35
img[:, :, :3] = np.clip(img[:, :, :3] * (1 - glow[..., None]) + np.array([200, 230, 220]) * glow[..., None], 0, 255)

# ── chůze: stezky a palouky ───────────────────────────────────────────────────
walk = ((TRAIL < 9) | (OPEN < 0)) & (XX > 4) & (XX < AW - 4)
pooled = block.copy()
block[:, :] = ~walk
block |= pooled
for (fx, fy) in FEET:
    block[max(0, fy - 5):fy, max(0, fx - 5):fx + 5] = True
for o in sc.placed:                                                       # paty předmětů
    s_ = o["spr"]; fx = o["x"] + s_.w // 2; fy = o["y"] + s_.h
    block[max(0, fy - 4):fy, max(0, fx - 6):fx + 6] = True

# ── vrstvy zelené mlhy zapomnění (nejhustší na mýtině ticha) ───────────────────
m = vn(XX * 0.6 + 7, YY * 1.4, 41, 22) * 0.7 + vn(XX, YY * 1.1, 42, 7) * 0.3
tx2, ty2 = NODES["lp_ticho"]
dense = 0.2 + 0.45 * np.clip(1 - np.hypot((XX - tx2) / 120, (YY - ty2) / 90), 0, 1)
a = np.floor(np.clip((m - 0.5) * 1.6, 0, 1) * dense * 16) / 16 * 2
img[:, :, :3] = np.clip(img[:, :, :3] * (1 - a[..., None]) + np.array([70, 150, 120]) * a[..., None], 0, 255)

cold = [(x, y, 9, 7, 0.55) for (x, y) in tips_all if 0 <= y < AH][::2][:260]
sc.light(
    warm=[(sx_ + 4, sy_ - 14, 10, 8, 0.3)] + [(x, y, 14, 12, 0.45) for (x, y) in LANTERNS[::2]],
    ember=[],
    cold=cold + [(tx2, ty2, 90, 60, 0.6), (NODES["lp_vstup"][0], 22, 40, 20, 0.5), (bx_, AH - 30, 40, 50, 0.8),
                 (kx, ky, 50, 30, 0.35)] + [(NODES[mm][0], NODES[mm][1], 30, 22, 0.3) for mm in MOSS],
    dark_k=0.42, warm_k=0.25, cold_k=0.22)
sc.export("root_forest", "gen_crown_forest.py")
print("TIPS", len(tips_all), "větví", len(placed), "LANTERNS", [(int(x), int(y)) for (x, y) in LANTERNS])
