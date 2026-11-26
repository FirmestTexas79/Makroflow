"""
Použití:  python tools/mapgen/gen_threshold.py
Výstup:   app/src/main/res/drawable-nodpi/root_threshold.png, app/src/main/assets/walk/root_threshold.txt,
          tools/mapgen/root_threshold_debug.png

Zóna 3 (Akt III – Hlubina): PRÁH (176 × 560, kamera jede s hráčem, docs/adr/0106) – aréna posledního souboje
s Drakirrou, u paty koruny Spící. Z Lesa pod korunou se projde branou zkřížených větví a po plochém kořeni přes
mělké černé jezero. Vzadu se hlavní větve koruny spouštějí až do vody a splétají se v obrovskou bledou stěnu jako
žebra katedrály; uprostřed ní kamenný trůn Lumenců a na něm socha ženy v kápi se skloněnou hlavou – Lumira, jak si
ji pamatovali. Za trůnem stoupá jediná větev se stupy vzhůru ke kmeni. Shora z rány padá do středu sloup bledého
světla, jezero celou stěnu zrcadlí, nad hladinou leží mlha a pomalu padají světélkující šupinky kůry. V mělčině
klečí kamenné postavy Lumenců čelem k trůnu, některé bez hlav. Uprostřed jezera kamenný ostrov s kruhem znaků –
tam leží Drakirra. Inspirace: arény Elden Ringu (kořeny stromu, zatopená síň s trůnem). Bez divokých setkání.
Živé detaily: InteriorFxView.ROOT_THRESHOLD. NODES / EDGES jsou ZDROJ PRAVDY i pro RootThreshold.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import Spr, c, _pick, _h, _vn, BARK  # noqa: E402
from scene import Scene  # noqa: E402
from roots import bez  # noqa: E402
from tree import TreePainter  # noqa: E402
import lumen as LU  # noqa: E402

AW, AH = 176, 560
WB = 236                                                                  # pata stěny = hladina
CX = 88
DAIS = (88, 352, 42, 21)                                                  # ostrov: střed, poloosy
NODES = {
    "pr_vstup": (88, 550), "pr_hraz": (88, 476), "pr_socha_l": (40, 418), "pr_socha_r": (136, 414),
    "pr_drakirra": (88, 384), "pr_odraz": (34, 330), "pr_trun": (88, 268), "pr_vetev": (134, 262),
}
EDGES = [("pr_vstup", "pr_hraz"), ("pr_hraz", "pr_socha_l"), ("pr_hraz", "pr_socha_r"), ("pr_hraz", "pr_drakirra"),
         ("pr_socha_l", "pr_odraz"), ("pr_odraz", "pr_trun"), ("pr_socha_r", "pr_vetev"), ("pr_trun", "pr_vetev"),
         ("pr_drakirra", "pr_odraz")]
sc = Scene(AW, AH, 0, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(213)
F = lambda v: np.array(v, float)                                          # noqa: E731


# ── pozadí: tma dutiny, nahoře záře z rány, dole u paty stěny mlha ──────────
def void(x, y):
    g = max(0.0, 1 - math.hypot((x - CX) / 120, (y + 30) / 260)) ** 1.5
    col = F([6, 10, 14]) * (1 - g) + F([70, 104, 108]) * g
    m = max(0.0, 1 - abs(y - WB) / 50) ** 1.5                             # mlha u hladiny
    col = col * (1 - m * 0.6) + F([120, 150, 150]) * m * 0.6
    n = _vn(x * 0.6, y * 1.1, 3, 24) * 0.6 + _vn(x, y, 5, 8) * 0.4
    return col * (0.84 + 0.32 * n)


for y in range(WB + 6):
    for x in range(AW):
        img[y, x, :3] = np.clip(void(x, y), 0, 255)


def chain(*pts, n=30):
    out = []
    mids = [pts[0]] + [((a[0] + b[0]) / 2, (a[1] + b[1]) / 2) for a, b in zip(pts[1:-1], pts[2:])] + [pts[-1]]
    for i in range(len(pts) - 2):
        out += bez(mids[i], pts[i + 1], mids[i + 1], n)
    return out


def bough(tp, pts, r0, r1, twigs=0):
    """Větev podél bodů (tloušťka r0 → r1), s plynulým zúžením; twigs = drobné výhonky po stranách."""
    n = len(pts)
    tp.stroke([(x, y, r0 + (r1 - r0) * i / (n - 1)) for i, (x, y) in enumerate(pts)])
    for k in range(twigs):
        i = int(rng.integers(n // 5, n - 4))
        x, y = pts[i]
        a = math.atan2(pts[i + 1][1] - y, pts[i + 1][0] - x) + rng.choice([-1, 1]) * rng.uniform(0.5, 1.1)
        tp.branch(x, y, a, (r0 + (r1 - r0) * i / n) * 0.35, rng.uniform(14, 30), 4, math.pi / 2, maxdepth=6, pull=0.02)


# 1) vzdálená vrstva: hustá spleť větví v mlze
far = TreePainter(img, lambda x, y: (np.clip(void(x, y), 0, 255), 0.62), seed=1)
for i in range(16):
    x0 = -10 + i * 13 + rng.uniform(-5, 5)
    pts = chain((x0, -10), (x0 + rng.uniform(-30, 30), 70), (CX + (x0 - CX) * 0.4 + rng.uniform(-20, 20), 150), (x0 + rng.uniform(-20, 20), WB + 4))
    bough(far, pts, rng.uniform(3, 5), rng.uniform(2, 4), twigs=3)
far.composite(dark=(40, 46, 50), light=(150, 156, 152))

# 2) hlavní žebra: obrovské bledé větve sestupující do vody a křížící se jako klenba
mid = TreePainter(img, lambda x, y: (np.clip(void(x, y), 0, 255), 0.12 + max(0.0, 1 - y / 80) * 0.35), seed=2)
RIBS = [
    ((-20, 20), (30, 60), (40, 150), (22, WB + 6), 11, 8), ((196, 14), (146, 60), (136, 150), (154, WB + 6), 11, 8),
    ((10, -10), (70, 40), (112, 120), (128, WB + 4), 7, 6), ((166, -10), (106, 40), (64, 120), (48, WB + 4), 7, 6),
    ((-20, 110), (20, 140), (52, 200), (62, WB + 2), 6, 5), ((196, 104), (156, 140), (124, 200), (114, WB + 2), 6, 5),
    ((60, -10), (40, 60), (8, 120), (-10, 190), 5, 4), ((116, -10), (136, 60), (168, 120), (186, 190), 5, 4),
]
for (p0, p1, p2, p3, r0, r1) in RIBS:
    bough(mid, chain(p0, p1, p2, p3), r0, r1, twigs=4)
mid.composite(dark=(74, 70, 64), light=(232, 226, 210))
# kořeny se noří do vody: tmavší místo dotyku a kruhy na hladině kreslí sekce jezera

# 3) trůn Lumenců se sochou ženy v kápi (Lumira)
STONE = [F(v) for v in ((34, 36, 40), (66, 68, 72), (104, 104, 104), (150, 146, 138), (196, 190, 178), (230, 224, 210), (248, 244, 232))]


def st(v, x, y):
    v = max(0.0, min(0.999, v)) * (len(STONE) - 1)
    i = int(v)
    return STONE[i + 1] if (v - i) > (((x * 3 + y * 5) % 7) / 7) * 0.8 + 0.1 and i + 1 < len(STONE) else STONE[i]


TH = dict(x0=60, x1=116, top=118, seat=200, base=WB - 2)
for y in range(TH["top"], TH["base"]):
    for x in range(TH["x0"] - 14, TH["x1"] + 15):
        inside = False
        v = 0.5
        if TH["x0"] <= x < TH["x1"] and y < TH["seat"] + 8:                # opěradlo (lomený oblouk nahoře)
            arch = TH["top"] + (abs(x - CX) / 28) ** 2 * 18
            if y >= arch:
                inside = True
                v = 0.55 - (x - CX) / 56 * 0.25 + (0.12 if (x - TH["x0"]) in (2, 3) or (TH["x1"] - x) in (3, 4) else 0)
                if (y - TH["top"]) % 22 == 0:
                    v -= 0.2
        if TH["x0"] - 8 <= x < TH["x1"] + 8 and TH["seat"] - 12 <= y < TH["seat"] + 4 and (x < TH["x0"] + 4 or x >= TH["x1"] - 4):
            inside = True; v = 0.66 - (x - CX) / 80 * 0.3                  # područky
        step = y - TH["seat"] - 6
        if step >= 0:                                                     # stupně podstavce
            w = 34 + (step // 6) * 7
            if abs(x - CX) < w:
                inside = True
                v = 0.72 if step % 6 < 2 else 0.4 - (x - CX) / (w * 2) * 0.2
        if inside:
            img[y, x, :3] = st(v + (_vn(x, y, 7, 3) - 0.5) * 0.12, x, y)
for k in range(9):                                                        # obrácená koruna a znaky nad hlavou
    img[TH["top"] + 16, CX - 8 + 2 * k, :3] = (126, 196, 255)
    if k % 2 == 0:
        img[TH["top"] + 17, CX - 8 + 2 * k, :3] = (126, 196, 255); img[TH["top"] + 18, CX - 8 + 2 * k, :3] = (70, 120, 190)
for y in range(TH["top"] + 26, TH["seat"] - 10, 6):
    for x in (TH["x0"] + 5, TH["x1"] - 6):
        img[y, x, :3] = (110, 180, 240)
# socha: žena v kápi se skloněnou hlavou (Lumira), sedí – kápě, ramena v plášti, paže podél těla, dlaně na kolenou,
# kolena vpředu, plášť padá přes stupně. Části mají vlastní stínování a tmavé spáry mezi sebou, ať je tvar čitelný.
FIG = np.zeros((AH, AW), bool)


def fig_hw(y, u=0.0):
    """Poloviční šířka siluety sochy v řádku y: kápě se špičkou → ramena → paže → klín a kolena → plášť přes stupně."""
    if y < 124:
        return 0
    if y < 154:
        return 10.5 * math.sqrt(max(0.0, 1 - ((y - 140) / 16) ** 2)) * (0.55 + 0.45 * min(1.0, (y - 124) / 8))
    if y < 170:
        return 9.5 + math.sqrt((y - 154) / 16) * 12.5
    if y < 192:
        return 22.0
    if y < 206:
        return 22.0 + (y - 192) * 0.15
    return 24.0 + (y - 206) * 0.25 + math.sin(u * 0.5 + y * 0.3) * 1.2       # zvlněný lem pláště


for y in range(122, 234):
    for x in range(CX - 34, CX + 35):
        u = x - CX
        hw = fig_hw(y, u)
        if abs(u) > hw:
            continue
        FIG[y, x] = True
        q = u / max(1.0, hw)
        v = 0.76 - q * 0.5 - 0.16 * abs(q) ** 4                           # světlo zleva shora, pravý bok ve stínu
        if y < 154:                                                       # kápě
            v += 0.04
            if (u / 5.6) ** 2 + ((y - 146) / 6.8) ** 2 < 1:
                v = 0.04 + max(0.0, (y - 144) / 70)                        # tma v kápi – obličej nevytesaný
                if (u + 6) ** 2 / 2 + ((y - 145) / 6) ** 2 < 1:
                    v = 0.5                                                # osvětlený okraj kápě vlevo
        elif y < 192:                                                     # ramena a paže v rukávech
            ac = 16 - (y - 166) / 26 * 9 if y >= 166 else 16
            d = abs(abs(u) - ac)
            if y >= 166 and d < 1.0:
                v = 0.2                                                    # šev rukávu
            elif y >= 166 and abs(u) < ac and abs(u) > ac - 3:
                v -= 0.22                                                  # stín mezi paží a tělem
            elif y >= 166 and abs(u) > ac:
                v += 0.05 - (abs(u) - ac) * 0.015                          # rukáv
        elif y < 206:                                                     # klín a kolena
            for kx in (-9, 9):
                if ((u - kx) / 7.5) ** 2 + ((y - 197) / 4.5) ** 2 < 1:
                    v += 0.14
            if y >= 203:
                v -= 0.22                                                  # stín pod koleny
        else:                                                             # plášť padá přes stupně v záhybech
            f = math.sin(u * 0.95 + (y - 206) * 0.12)
            v += f * 0.12
            if abs(f) > 0.94:
                v -= 0.15
            if y > 228:
                v -= 0.2                                                   # lem ve stínu
        img[y, x, :3] = st(v + (_vn(x, y, 8, 3) - 0.5) * 0.06, x, y)
for (fx_, fy_) in ((CX - 9, 230), (CX + 9, 230)):                         # špičky chodidel zpod lemu
    for y in range(fy_, fy_ + 3):
        for x in range(fx_ - 3, fx_ + 3):
            img[y, x, :3] = st(0.78 - (x - fx_) * 0.06, x, y)
for y in range(189, 198):                                                 # sepjaté dlaně a v nich semínko (Srdce)
    for x in range(CX - 6, CX + 7):
        if ((x - CX) / 6) ** 2 + ((y - 193) / 3.6) ** 2 < 1:
            img[y, x, :3] = st(0.92 - (x - CX) * 0.035 - (y - 190) * 0.03, x, y)
for (x, y, col) in ((CX, 192, (170, 255, 210)), (CX - 1, 192, (120, 220, 170)), (CX, 191, (120, 220, 170)), (CX + 1, 192, (90, 180, 140))):
    img[y, x, :3] = col
for y in range(122, 234):                                                 # obrys sochy proti trůnu
    for x in range(CX - 35, CX + 36):
        if not FIG[y, x] and (FIG[y, x - 1] or FIG[y, x + 1] or FIG[y - 1, x]):
            img[y, x, :3] = st(0.1, x, y)
for y in range(200, 234):                                                 # mech v záhybech dole
    for x in range(CX - 34, CX + 34):
        if FIG[y, x] and _vn(x * 1.6, y * 1.6, 9, 3) > 0.8:
            img[y, x, :3] = np.clip(img[y, x, :3] * 0.55 + F([60, 96, 70]) * 0.45, 0, 255)
# koruna z větviček, která jí vyrostla kolem kápě (je to strom)
crown = TreePainter(img, lambda x, y: (np.clip(void(x, y), 0, 255), 0.0), seed=11)
for a_ in (-2.5, -2.05, -1.57, -1.1, -0.65):
    crown.branch(CX + math.cos(a_) * 9, 134 + math.sin(a_) * 8, a_, 1.1, 9, 3, a_, spread=(0.4, 0.8), maxdepth=6, pull=0.0)
crown.composite(dark=(120, 116, 106), light=(236, 230, 214))
over = TreePainter(img, lambda x, y: (np.clip(void(x, y), 0, 255), 0.05), seed=3)
bough(over, chain((CX - 40, 120), (CX - 24, 150), (CX - 14, 176), (CX - 30, WB)), 3.4, 2.4, twigs=2)
bough(over, chain((CX + 44, 160), (CX + 30, 200), (CX + 40, WB + 2)), 3.0, 2.2, twigs=1)
over.composite(dark=(74, 70, 64), light=(232, 226, 210))

# 4) větev se stupy za trůnem: stoupá vzhůru ke kmeni (cesta dál)
up = TreePainter(img, lambda x, y: (np.clip(void(x, y), 0, 255), 0.08 + max(0.0, 1 - y / 60) * 0.5), seed=4)
VET = chain((134, WB + 2), (128, 190), (142, 120), (124, 50), (132, -10))
bough(up, VET, 7.5, 5.0)
up.composite(dark=(80, 76, 70), light=(236, 230, 214))
for i, (x, y) in enumerate(VET[::5]):                                    # vyšlapané stupy se slabým světlem
    if 0 <= y < WB:
        img[int(y), int(x) - 3:int(x) + 3, :3] = (64, 60, 54)
        img[int(y), int(x), :3] = (150, 210, 200)

# ── jezero: hladina zrcadlí stěnu, vlnky, mlha ──────────────────────────────
WATER_TOP, WATER_BOT = WB, AH
src = img.copy()
water = np.zeros((AH, AW), bool)
for y in range(WATER_TOP, WATER_BOT):
    dist = y - WB
    ys = WB - dist - 2
    fade = max(0.0, 1 - dist / 170) ** 0.8
    for x in range(AW):
        dx = int(round(math.sin(y * 0.55 + x * 0.05) * (0.6 + dist / 90)))
        base = F([10, 28, 32]) * (1 - dist / 400) + F([4, 12, 16]) * (dist / 400)
        if 0 <= ys < WB:
            refl = src[ys, max(0, min(AW - 1, x + dx)), :3].astype(float)
            col = base * (1 - 0.7 * fade) + refl * 0.7 * fade * F([0.72, 0.92, 0.96])
        else:
            col = base
        if (y * 7 + x * 3) % 37 == 0 and _vn(x, y, 11, 6) > 0.5:
            col = col + F([40, 60, 60])                                   # odlesk vlnky
        img[y, x, :3] = np.clip(col, 0, 255)
        water[y, x] = True
for x in range(AW):                                                       # linie styku stěny a hladiny
    img[WB, x, :3] = np.clip(img[WB, x, :3] * 0.4 + F([200, 220, 215]) * 0.6, 0, 255)
    img[WB + 1, x, :3] = np.clip(img[WB + 1, x, :3] * 0.6, 0, 255)


def ring(cx, cy, r, k=0.4):
    for a in range(0, 360, 6):
        X, Y = int(cx + math.cos(math.radians(a)) * r), int(cy + math.sin(math.radians(a)) * r * 0.35)
        if 0 <= X < AW and 0 <= Y < AH:
            img[Y, X, :3] = np.clip(img[Y, X, :3] * (1 - k) + F([170, 200, 200]) * k, 0, 255)


for (x, y, r) in ((22, WB + 3, 9), (154, WB + 3, 9), (48, WB + 3, 6), (128, WB + 3, 6), (62, WB + 2, 5), (114, WB + 2, 5), (134, WB + 3, 7)):
    ring(x, y, r); ring(x, y, r + 5, 0.2)                                 # kruhy kolem kořenů ve vodě

# kamenný ostrov s kruhem znaků (tady leží Drakirra)
dx_, dy_, rx_, ry_ = DAIS
for y in range(dy_ - ry_, dy_ + ry_ + 6):
    for x in range(dx_ - rx_ - 1, dx_ + rx_ + 2):
        d = ((x - dx_) / rx_) ** 2 + ((y - dy_) / ry_) ** 2
        d2 = ((x - dx_) / rx_) ** 2 + ((y - dy_ - 5) / ry_) ** 2
        if d < 1:
            v = 0.62 - (x - dx_) / (rx_ * 2) * 0.25 + (_vn(x, y, 13, 3) - 0.5) * 0.12
            if 0.55 < d < 0.66:
                img[y, x, :3] = (120, 196, 255) if (x + y) % 3 else (60, 110, 180)   # kruh znaků
                continue
            if 0.86 < d < 0.9:
                v -= 0.3                                                    # stupeň u okraje
            if abs(_vn(x * 0.9, y * 0.9, 14, 6) - 0.5) < 0.025:
                v -= 0.3
            img[y, x, :3] = st(v, x, y)
        elif d2 < 1:
            img[y, x, :3] = st(0.22 - (y - dy_) / 40, x, y)                 # boční stěna ostrova
ring(dx_, dy_ + 8, rx_ + 4, 0.25); ring(dx_, dy_ + 8, rx_ + 10, 0.12)
for k in range(9):                                                        # obrácená koruna uprostřed ostrova
    img[dy_ - 2, dx_ - 8 + 2 * k, :3] = (110, 180, 240)
    if k % 2 == 0:
        img[dy_ - 1, dx_ - 8 + 2 * k, :3] = (110, 180, 240); img[dy_, dx_ - 8 + 2 * k, :3] = (60, 110, 180)

# procesní cesta: kamenné desky Lumenců od brány k ostrovu, napůl pod hladinou, místy chybí
for y in range(dy_ + ry_ + 2, AH):
    t = (y - dy_) / (AH - dy_)
    hw = 12 + t * 6
    row = (y - dy_ - ry_ - 2) // 15
    missing = _h(row, 3, 77) % 7 == 0
    for x in range(int(CX - hw - 2), int(CX + hw + 3)):
        u = (x - CX) / hw
        if abs(u) <= 1:
            seam = (y - dy_ - ry_ - 2) % 15 == 0 or (x - CX + (row % 2) * 9 + 30) % 18 == 0
            v = 0.4 - u * 0.2 + (_vn(x, y, 15, 3) - 0.5) * 0.16 + (0.14 if (y - dy_ - ry_ - 2) % 15 == 1 else 0) + (_h(row, (x - CX + (row % 2) * 9 + 30) // 18, 5) % 4) * 0.03
            if seam:
                v = 0.16
            col = st(v, x, y)
            wet = 0.6 if missing else (0.3 if abs(u) > 0.8 else 0.12)     # okraje a chybějící desky pod vodou
            img[y, x, :3] = np.clip(col * (1 - wet) + img[y, x, :3] * wet, 0, 255)
        elif abs(u) < 1 + 2 / hw:
            img[y, x, :3] = np.clip(img[y, x, :3] * 0.55, 0, 255)


def blit_sub(spr, cx_, by_, sub_from, refl=True):
    """Sprite postavený do mělčiny: spodek pod hladinou (od řádku sub_from), slabý odraz pod sebou."""
    x0, y0 = cx_ - spr.w // 2, by_ - spr.h
    for yy in range(spr.h):
        for xx in range(spr.w):
            if spr.a[yy, xx, 3]:
                X, Y = x0 + xx, y0 + yy
                if not (0 <= X < AW and 0 <= Y < AH):
                    continue
                sub = max(0.0, (yy - sub_from) / max(1, spr.h - sub_from))
                img[Y, X, :3] = np.clip(spr.a[yy, xx, :3] * (1 - sub * 0.7) + img[Y, X, :3] * sub * 0.7, 0, 255)
                Ym = by_ + (by_ - Y) - 2
                if refl and 0 <= Ym < AH and yy < sub_from:
                    img[Ym, X, :3] = np.clip(img[Ym, X, :3] * 0.72 + spr.a[yy, xx, :3] * 0.14, 0, 255)


# sloupy podél procesní cesty: některé ulomené, na celých hoří studený modrý plamen znaků
FLAMES = []
for i, (yy, br) in enumerate(((430, 0), (474, 14), (520, 0))):
    for sd in (-1, 1):
        bb = br if sd < 0 else (0 if br else 12)
        col_ = LU.column(40, broken=bb, seed=60 + i * 2 + (sd > 0))
        blit_sub(col_, CX + sd * 30, yy, col_.h - 8)
        if not bb:
            FLAMES.append((CX + sd * 30, yy - col_.h - 2))
        block[yy - 6:yy, CX + sd * 30 - 8:CX + sd * 30 + 8] = True
pool = np.zeros((AH, AW))
for (fx, fy) in FLAMES:
    Scene.pool(pool, fx, fy + 30, 26, 22, 0.7)                             # modrá záře plamene na hladině
    Scene.pool(pool, fx, fy + 4, 10, 8, 0.9)
__import__("light").lighten(img, pool, 0.28, (110, 180, 255), steps=6)
for (fx, fy) in FLAMES:
    for k in range(7):
        for dxx in range(-2 + k // 3, 3 - k // 3):
            img[fy - k, fx + dxx, :3] = (150, 210, 255) if k < 4 else (210, 240, 255)
# dva obří ulomené sloupy dál v jezeře (jako v zatopené síni)
for (gx, gy, br) in ((34, 372, 30), (142, 366, 0)):
    big = LU.column(84, broken=br, seed=90 + gx)
    blit_sub(big, gx, gy, big.h - 10)
    block[gy - 6:gy, gx - 9:gx + 9] = True
# světélkující šupinky plují na hladině
for _ in range(90):
    x, y = int(rng.integers(20, 156)), int(rng.integers(WB + 10, AH - 4))
    img[y, x, :3] = (236, 232, 200); img[y, x + 1, :3] = (150, 150, 130)
    if y + 2 < AH:
        img[y + 2, x, :3] = np.clip(img[y + 2, x, :3] * 0.6 + F([120, 130, 120]) * 0.4, 0, 255)

# ── klečící Lumenci v mělčině, čelem k trůnu (vidíme jejich záda) ────────────


def kneeler(headless=False, seed=0):
    """Klečící Lumenec zezadu (čelem k trůnu): hlava v kápi skloněná mezi zaoblená ramena, záda ohnutá v modlitbě,
    lokty do stran (ruce sepjaté vpředu), sedí na patách – zpod pláště koukají chodidla, plášť se rozlévá do vody."""
    s = Spr(22, 28)
    cx_ = 11
    E = [(cx_, 6.5, 3.0, 3.3, 'h'), (cx_, 13.5, 7.2, 6.2, 't'), (cx_, 20.5, 8.2, 4.6, 'p'), (cx_, 24.5, 10.2, 2.6, 'c'),
         (cx_ - 7.6, 14.5, 2.0, 2.4, 'e'), (cx_ + 7.6, 14.5, 2.0, 2.4, 'e')]
    for y in range(28):
        for x in range(22):
            part = None
            for (ex, ey, rx, ry, k) in E:
                if ((x - ex) / rx) ** 2 + ((y - ey) / ry) ** 2 < 1:
                    part = k
                    break
            if part is None or (headless and part == 'h'):
                continue
            ex, ey, rx, ry, _ = next(e for e in E if e[4] == part)
            q = (x - ex) / rx
            r_ = (y - ey) / ry
            v = 0.46 - q * 0.34 - r_ * 0.08 - 0.12 * max(0.0, (q * q + r_ * r_) - 0.6)   # objem: světlo zleva shora
            if part == 't' and abs(x - cx_) < 0.8 and y > 10:
                v -= 0.12                                                  # páteř pod látkou
            if part == 'h':
                v += 0.05
            if part in 'pc' and abs(math.sin((x - cx_) * 1.2 + y * 0.25)) > 0.9:
                v -= 0.1                                                   # záhyby pláště
            col = st(v + (_vn(x, y, seed, 3) - 0.5) * 0.07, x, y)
            s.px(x, y, (int(col[0]), int(col[1]), int(col[2]), 255))
    for (fx_, fy_) in ((cx_ - 4, 25), (cx_ + 4, 25)):                     # chodidla (paty nahoru) – klečí
        for y in range(fy_ - 1, fy_ + 2):
            for x in range(fx_ - 1, fx_ + 2):
                if abs(x - fx_) + abs(y - fy_) < 2:
                    col = st(0.66 if y < fy_ + 1 else 0.4, x, y)
                    s.px(x, y, (int(col[0]), int(col[1]), int(col[2]), 255))
    if headless:
        for x in range(cx_ - 2, cx_ + 3):                                  # hladký řez krku
            col = st(0.82, x, 8)
            s.px(x, 8, (int(col[0]), int(col[1]), int(col[2]), 255))
    for x in range(cx_ - 6, cx_ + 7):                                      # stín mezi hlavou a rameny
        if s.a[9, x, 3] and not headless and abs(x - cx_) > 2:
            s.a[9, x, :3] = (s.a[9, x, :3] * 0.7).astype(s.a.dtype)
    return selout_k(s)


def selout_k(s):
    """Tmavý obrys podle sousedství (sochy se nesmí slít s vodou)."""
    a = s.a.copy()
    for y in range(s.h):
        for x in range(s.w):
            if not a[y, x, 3] and any(0 <= y + dy < s.h and 0 <= x + dx < s.w and a[y + dy, x + dx, 3] for dy, dx in ((0, 1), (0, -1), (-1, 0))):
                s.a[y, x] = (24, 26, 30, 255)
    return s


KNEEL = [(26, 300, False), (150, 296, True), (52, 276, False), (124, 272, False), (18, 410, True),
         (158, 404, False), (26, 474, False), (152, 470, True), (70, 310, False), (106, 306, True)]
for i, (x, y, hl) in enumerate(KNEEL):
    k = kneeler(hl, 40 + i)
    for yy in range(k.h):
        for xx in range(k.w):
            if k.a[yy, xx, 3]:
                X, Y = x - 11 + xx, y - 28 + yy
                sub = max(0.0, (yy - 21) / 7)                              # spodek pod hladinou
                img[Y, X, :3] = np.clip(k.a[yy, xx, :3] * (1 - sub * 0.75) + img[Y, X, :3] * sub * 0.75, 0, 255)
                Ym = y + (y - Y) - 2                                       # odraz postavy ve vodě
                if 0 <= Ym < AH and yy < 21:
                    img[Ym, X, :3] = np.clip(img[Ym, X, :3] * 0.7 + k.a[yy, xx, :3] * 0.15, 0, 255)
    ring(x, y - 1, 9, 0.3)
    block[y - 4:y, x - 6:x + 6] = True

# ── popředí: obří větve po stranách (rám), převislé větvičky nahoře ──────────
front = TreePainter(img, lambda x, y: (F([4, 6, 8]), 0.0), seed=5)
bough(front, chain((-12, -10), (8, 120), (-4, 300), (12, 440), (-6, 570)), 12, 8, twigs=6)
bough(front, chain((188, -10), (166, 140), (180, 320), (164, 460), (182, 570)), 12, 8, twigs=6)
for (x0, a0) in ((0, 0.6), (176, math.pi - 0.6)):
    for k in range(3):
        front.branch(x0, 6 + k * 14, a0, 2.6, 34, 0, math.pi / 2, maxdepth=6, pull=0.04)
front.composite(dark=(8, 10, 12), light=(40, 46, 48))
for y in range(AH):                                                       # chladný okraj světla na vnitřní straně rámu
    for x in range(1, AW - 1):
        if front.cov[y, x] > 0.5 and front.cov[y, x + 1] < 0.5 and x < 60:
            img[y, x, :3] = (70, 96, 100)
        if front.cov[y, x] > 0.5 and front.cov[y, x - 1] < 0.5 and x > 116:
            img[y, x, :3] = (70, 96, 100)

# ── světlo: sloup z rány do středu, paprsky, mlha nad hladinou, šupinky ──────
L = __import__("light")
beam = np.zeros((AH, AW))
for y in range(0, dy_ + 20):
    t = y / (dy_ + 20)
    cxb, hw = CX + 4 - t * 4, 5 + t * 30
    for x in range(int(cxb - hw), int(cxb + hw) + 1):
        if 0 <= x < AW:
            beam[y, x] = max(beam[y, x], (1 - abs(x - cxb) / hw) ** 1.2 * (0.25 + 0.55 * t) * min(1.0, (dy_ + 20 - y) / 30))
for (x0, sl) in ((40, 0.25), (130, -0.22), (66, 0.1), (110, -0.12)):      # tenké paprsky mezerami ve stěně
    for y in range(0, WB):
        X = int(x0 + y * sl)
        for k in range(-2, 3):
            if 0 <= X + k < AW:
                beam[y, X + k] = max(beam[y, X + k], (1 - abs(k) / 3) * 0.28 * (y / WB))
beam[118:232, 54:124] *= 0.35                                            # socha nesmí přepálit
L.lighten(img, beam, 0.42, (220, 240, 232), steps=8)
for y in range(WB - 16, WB + 60):                                         # mlha nad hladinou (vrstvy)
    for x in range(AW):
        m = _vn(x * 0.5 + 3, y * 1.6, 21, 16)
        a = max(0.0, 1 - abs(y - WB - 6) / 40) * max(0.0, m - 0.35) * 1.1
        a = math.floor(a * 5) / 5 * 0.5
        if a > 0:
            img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + F([170, 196, 196]) * a, 0, 255)
vg = np.zeros((AH, AW)); L.vignette(vg, AW / 2, AH * 0.45, AW * 0.7, AH * 0.6, k=0.6)
L.darken(img, vg, 0.7, (2, 3, 6), dither=True, steps=10)
flakes = []
for _ in range(140):                                                      # padající světélkující šupinky kůry
    x, y = int(rng.integers(4, AW - 4)), int(rng.integers(20, AH - 10))
    flakes.append((x, y))
    img[y, x, :3] = (236, 232, 200) if rng.random() < 0.6 else (180, 230, 220)
    if rng.random() < 0.4:
        img[y + 1, x, :3] = (120, 140, 130)

# ── chůze: celé mělké jezero (brodí se), ne stěna, ne rám, ne sochy ─────────
block[:, :] = True
for y in range(WB + 18, AH):
    for x in range(AW):
        if not front.cov[y, x] > 0.3 and 18 < x < 158:
            block[y, x] = False
for (x, y, hl) in KNEEL:
    block[y - 5:y, x - 6:x + 6] = True
block[AH - 20:AH, 70:108] = False
sc.export("root_threshold", "gen_threshold.py")
print("FLAKES", flakes[:6])
