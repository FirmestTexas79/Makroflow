"""
Použití:  python tools/mapgen/gen_trunk.py
Výstup:   app/src/main/res/drawable-nodpi/root_trunk.png, app/src/main/assets/walk/root_trunk.txt,
          tools/mapgen/root_trunk_debug.png

Zóna 3 (Akt III – Hlubina): KMEN (176 × 560, kamera jede s hráčem, docs/adr/0106) – 8/8, bůh ve Stromu. Z Prahu se
vystoupá po obří větvi se stupy nad propastí na ocelovou lávku Řádu u kmene Spící. Celou horní půlku mapy vyplní
kmen – kůra se svislými brázdami, vyrytými znaky Lumenců a šablonovými nápisy Řádu. Uprostřed se kmen rozevírá
v obrovskou ránu, kterou Řád zalil žlutošedou pěnou (Operace Pečeť) a sešil ocelovými pásy přišroubovanými
k šesti kotvám. V pěně je přibitý Trpitel (Kazimír): vyhublé tělo barvy popela, šest paží roztažených jako vějíř,
zápěstí v ocelových objímkách, šest očí, hlava skloněná. Hrudí mu prochází kopí – ratiště trčí z rány ven a Řád ho
sevřel ocelovou objímkou s řetězy ke kotvám, aby ho nikdo nevytáhl. Z kůže se mu odlupuje popel a padá na lávku.
Prasklinami v pěně prosvítá studené světlo (to poslední, co z ní zbylo); zespodu na ránu míří sodíkové světlomety.
Na lávce nádrže s pěnou a hadice do rány, sonda Řádu (Sept), cedule OPERACE PEČEŤ, hromady popela; vpravo u paty
kmene puklina dolů – cesta do Středu kořenů. Bez divokých setkání. Živé detaily: InteriorFxView.ROOT_TRUNK.
NODES / EDGES jsou ZDROJ PRAVDY i pro RootTrunk.kt.
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import Spr, c, _pick, _h, _vn, BARK, STEEL, RUST, YELLOW, GLYPH  # noqa: E402
from scene import Scene  # noqa: E402
from roots import bez  # noqa: E402
from tree import TreePainter  # noqa: E402
from signs import plate, painted, tiny  # noqa: E402
import order as O  # noqa: E402

AW, AH = 176, 560
TB = 300                                                                  # pata kmene = horní hrana lávky
CX = 88
NODES = {
    "kn_vstup": (96, 550), "kn_vetev": (84, 468), "kn_lavka": (88, 404), "kn_sonda": (40, 372),
    "kn_kotva": (30, 318), "kn_trpitel": (88, 320), "kn_popel": (114, 334), "kn_hadice": (140, 372),
    "kn_stred": (156, 318),
}
EDGES = [("kn_vstup", "kn_vetev"), ("kn_vetev", "kn_lavka"), ("kn_lavka", "kn_sonda"), ("kn_lavka", "kn_hadice"),
         ("kn_lavka", "kn_trpitel"), ("kn_sonda", "kn_kotva"), ("kn_kotva", "kn_trpitel"), ("kn_trpitel", "kn_popel"),
         ("kn_popel", "kn_hadice"), ("kn_hadice", "kn_stred"), ("kn_popel", "kn_stred")]
sc = Scene(AW, AH, 0, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(8)
F = lambda v: np.array(v, float)                                          # noqa: E731


def put(x, y, col, a=1.0):
    X, Y = int(round(x)), int(round(y))
    if 0 <= X < AW and 0 <= Y < AH and a > 0:
        img[Y, X, :3] = np.clip(img[Y, X, :3] * (1 - a) + np.asarray(col[:3], float) * a, 0, 255)


class _Img:
    """Adaptér: drobné písmo (signs.tiny) rovnou do mapy."""
    def px(self, x, y, col):
        put(x, y, col)


IMG = _Img()


def ramp(R, v, x, y):
    """Dithered výběr z palety R (seznam RGB) podle v 0–1."""
    v = max(0.0, min(0.999, v)) * (len(R) - 1)
    i = int(v)
    return R[i + 1] if i + 1 < len(R) and (v - i) > ((x * 3 + y * 5) % 7) / 7 * 0.8 + 0.1 else R[i]


# ── propast: tma, dole slabá záře koruny ─────────────────────────────────────
def void(x, y):
    g = max(0.0, 1 - math.hypot((x - CX) / 150, (y - AH - 40) / 220)) ** 1.6
    col = F([4, 7, 11]) * (1 - g) + F([46, 72, 80]) * g
    return col * (0.85 + 0.3 * _vn(x * 0.7, y * 0.9, 3, 20))


for y in range(AH):
    for x in range(AW):
        img[y, x, :3] = np.clip(void(x, y), 0, 255)

far = TreePainter(img, lambda x, y: (np.clip(void(x, y), 0, 255), 0.7), seed=1)
for i in range(9):                                                        # vzdálené větve koruny v mlze pod námi
    x0 = -20 + i * 26 + rng.uniform(-8, 8)
    far.branch(x0, TB + 10, math.pi / 2 + rng.uniform(-0.5, 0.5), rng.uniform(4, 7), rng.uniform(60, 120), 0, math.pi / 2,
               maxdepth=7, pull=0.03)
far.composite(dark=(20, 26, 30), light=(90, 110, 110))

# ── kmen: kůra se svislými brázdami, válcové stínování, znaky Lumenců ─────────
BARKF = [F(v[:3]) for v in BARK]
WOUND = dict(top=16, bot=262, hw=48)


def wound_hw(y):
    if not WOUND["top"] <= y <= WOUND["bot"]:
        return -1.0
    t = (y - WOUND["top"]) / (WOUND["bot"] - WOUND["top"])
    return WOUND["hw"] * math.sin(math.pi * t) ** 0.75 + (_vn(0, y * 0.4, 31, 4) - 0.5) * 5


for y in range(TB):
    for x in range(AW):
        u = (x - CX) / 110                                                # válec: kraje do tmy
        warp = (_vn(x, y * 0.25, 60, 14) - 0.5) * 14
        n = _vn(x + warp, y * 0.07, 62, 7)
        ridge = 1 - abs(2 * n - 1)                                        # hřbety desek kůry
        plate_ = _vn(x + warp, y * 0.45, 63, 9)                           # příčné zlomy desek
        v = 0.16 + ridge * 0.42 - abs(u) ** 2 * 0.32 - u * 0.08
        v += (_vn(x * 2, y * 0.5, 64, 3) - 0.5) * 0.12
        if ridge < 0.22:
            v = 0.08 + ridge * 0.4                                        # hluboká brázda
        elif abs(plate_ - 0.5) < 0.035:
            v -= 0.22
        if _vn(x * 0.6, y * 0.3, 65, 8) > 0.78 and ridge > 0.5:
            v += 0.08                                                     # světlejší šupiny
        hw = wound_hw(y)
        d = abs(x - CX) - hw
        if hw > 0 and 0 <= d < 10:                                        # okolí rány: kůra se kroutí ven
            v += (1 - d / 10) * 0.2
        img[y, x, :3] = ramp(BARKF, v, x, y)
        if _vn(x * 0.5, y * 0.2, 66, 10) > 0.8 and ridge > 0.35:
            img[y, x, :3] = np.clip(img[y, x, :3] * 0.6 + F((54, 72, 58)) * 0.4, 0, 255)   # mech v brázdách
for (x0, y0) in ((30, 20), (142, 40), (26, 180), (150, 190), (40, 112), (136, 120)):   # svítící jizvy znaků Lumenců
    x, y = x0, y0
    for i in range(26):
        y += 1; x += math.sin(i * 0.5 + x0) * 0.6
        if i % 5 != 4:
            put(x, y, (110, 190, 230), 0.8); put(x + 1, y, (40, 80, 110), 0.6)
for (gx, gy) in ((14, 30), (160, 54), (20, 250), (156, 236), (8, 140), (168, 160)):   # staré vyryté znaky
    for k in range(6):
        put(gx + (k % 2), gy + k * 3, (90, 150, 190) if k % 2 else (60, 100, 140))
painted(img, 6, 282, "PEČEŤ-04", (200, 168, 60), seed=3, wear=0.3, drips=True)
painted(img, 116, 282, "NEOTVÍRAT", (180, 60, 40), seed=4, wear=0.3, drips=True)

# ── rána: roztržené rty kůry, syrové dřevo, uvnitř pěna ───────────────────────
FOAM = [F(v) for v in ((50, 46, 34), (86, 80, 58), (122, 114, 84), (156, 146, 108), (188, 178, 136), (214, 206, 166))]
for y in range(WOUND["top"], WOUND["bot"] + 1):
    hw = wound_hw(y)
    for x in range(int(CX - hw - 4), int(CX + hw + 5)):
        d = abs(x - CX) - hw
        if -5 < d < 0:
            img[y, x, :3] = ramp([F((80, 62, 44)), F((132, 108, 80)), F((176, 150, 112)), F((206, 184, 146))],
                                 0.75 + d / 7 - (0.2 if x > CX else 0), x, y)   # syrové dřevo na lemu
        elif 0 <= d < 2:
            img[y, x, :3] = (22, 18, 14)                                  # tmavá hrana kůry
foam = np.zeros((AH, AW), bool)
for y in range(WOUND["top"] + 2, WOUND["bot"] - 1):
    hw = wound_hw(y) - 5
    for x in range(int(CX - hw), int(CX + hw) + 1):
        foam[y, x] = True
        img[y, x, :3] = FOAM[1]
blobs = []
for _ in range(1500):                                                     # bublinatá pěna: stínované hroudy
    y = rng.uniform(WOUND["top"] + 4, WOUND["bot"] - 3)
    hw = wound_hw(int(y)) - 6
    if hw <= 1:
        continue
    blobs.append((rng.uniform(CX - hw, CX + hw), y, rng.uniform(1.6, 5.5)))
for (bx, by, r) in sorted(blobs, key=lambda b: b[1]):
    for y in range(int(by - r), int(by + r) + 1):
        for x in range(int(bx - r), int(bx + r) + 1):
            if 0 <= y < AH and 0 <= x < AW and foam[y, x]:
                dd = ((x - bx) ** 2 + (y - by) ** 2) / (r * r)
                if dd < 1:
                    v = 0.78 - dd * 0.42 - ((x - bx) - (y - by)) / r * 0.12
                    img[y, x, :3] = ramp(FOAM, v, x, y)
                elif dd < 1.25 and img[y, x, 0] > FOAM[2][0]:
                    img[y, x, :3] = FOAM[1]                               # spára mezi hroudami
CRACKS = []
for (x0, y0, n, a0) in ((70, 40, 30, 1.2), (108, 60, 26, 1.9), (66, 196, 30, 1.4), (110, 214, 28, 1.7), (88, 236, 18, 1.57),
                        (60, 120, 16, 1.0), (118, 140, 18, 2.1)):
    x, y, a = x0, y0, a0
    for i in range(n):                                                     # praskliny se studeným světlem zevnitř
        a += rng.uniform(-0.5, 0.5)
        x += math.cos(a); y += math.sin(a)
        if 0 <= int(y) < AH and 0 <= int(x) < AW and foam[int(y), int(x)]:
            put(x, y, (190, 250, 236)); put(x + 1, y, (110, 200, 190), 0.7); put(x - 1, y, (60, 120, 120), 0.5)
            CRACKS.append((int(x), int(y)))
# ── Trpitel je NPC (gen_trpitel.py → trpitel_sheet.png, dýchá a hýbe rukama), sem jen kotevní body ─────────
import gen_trpitel as TP  # noqa: E402
TX, TY = TP.MAP_X0 + TP.SPEAR_IN[0], TP.MAP_Y0 + TP.SPEAR_IN[1]       # rána od kopí v hrudi (mapa)
CLAMP = (TP.MAP_X0 + TP.CLAMP[0], TP.MAP_Y0 + TP.CLAMP[1])            # objímka Řádu na ratišti
ANCHORS = [(16, 70), (12, 150), (18, 230), (160, 70), (164, 150), (158, 230)]


def chain(p0, p1, sag=8):
    """Řetěz s průvěsem: střídavá očka."""
    n = int(math.hypot(p1[0] - p0[0], p1[1] - p0[1]) / 1.5)
    mid = ((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2 + sag)
    for i, (x, y) in enumerate(bez(p0, mid, p1, n)):
        put(x, y, (150, 152, 156) if i % 3 else (60, 62, 66))
        put(x, y + 1, (30, 30, 34), 0.8)


chain(CLAMP, (ANCHORS[2][0] + 6, ANCHORS[2][1] + 4), 10)
chain(CLAMP, (ANCHORS[5][0] - 6, ANCHORS[5][1] + 4), 6)
for side, k in ((-1, 0), (1, 3)):                                         # řetězy od objímek horních zápěstí ke kotvám
    chain((TP.MAP_X0 + TP.CX + side * 62, TP.MAP_Y0 + 14), (ANCHORS[k][0], ANCHORS[k][1] - 8), 3)
    chain((TP.MAP_X0 + TP.CX + side * 66, TP.MAP_Y0 + 80), (ANCHORS[k + 1][0], ANCHORS[k + 1][1] - 8), 5)
# ocelové pásy přes ránu (stehy Řádu) nad a pod Trpitelem
for (yb, x0, x1) in ((240, ANCHORS[2][0], ANCHORS[5][0]),):
    for x in range(x0, x1 + 1):
        dip = math.sin((x - x0) / (x1 - x0) * math.pi) * 3                # pás se prověsil přes pěnu
        for k in range(4):
            y = int(yb + dip + k)
            img[y, x, :3] = STEEL[(4, 3, 2, 1)[k]][:3]
        if (x - x0) % 16 == 0:
            put(x, yb + dip + 1, (30, 30, 34)); put(x, yb + dip + 2, (30, 30, 34))
    painted(img, CX - 18, yb + 1, "PEČEŤ", (200, 168, 60), seed=yb, wear=0.25, drips=False, spray=False)
# šest kotev: ocelové desky se šrouby, rez, čísla
for i, (ax, ay) in enumerate(ANCHORS):
    for y in range(ay - 9, ay + 10):
        for x in range(ax - 7, ax + 8):
            if 0 <= x < AW:
                v = 0.62 - (x - ax) / 30 - (y - ay) / 40
                img[y, x, :3] = F(STEEL[min(5, max(1, int(v * 6)))][:3])
                if abs(x - ax) == 7 or abs(y - ay) == 9:
                    img[y, x, :3] = STEEL[1][:3]
    for (bx, by) in ((ax - 5, ay - 7), (ax + 5, ay - 7), (ax - 5, ay + 7), (ax + 5, ay + 7)):
        put(bx, by, STEEL[5]); put(bx + 1, by + 1, (20, 20, 24))
    for y in range(ay + 10, ay + 26):                                     # rezavé stékance
        for x in (ax - 3, ax + 2):
            if _h(x, y, i) % 4:
                put(x, y, RUST[2], 0.55 - (y - ay - 10) * 0.03)
    tiny(IMG, ax - 4, ay - 3, "K" + str(i + 1), (200, 168, 60))


# ── lávka Řádu u paty kmene: ocelový rošt nad propastí, zábradlí, nosníky ─────
DECK_B = 404


def deck_x(y):
    """Okraje lávky v řádku y (lichoběžník, dole se zužuje k větvi)."""
    t = (y - TB) / (DECK_B - TB)
    return 10 + t * 20, 166 - t * 20


deck = np.zeros((AH, AW), bool)
for y in range(TB, DECK_B):
    x0, x1 = deck_x(y)
    for x in range(int(x0), int(x1) + 1):
        deck[y, x] = True
        gx, gy = (x - 2) % 4, (y - TB) % 4
        v = (36, 40, 46)
        if gy == 0:
            v = (60, 64, 72)                                              # mřížka roštu
        elif gx == 0:
            v = (50, 54, 60)
        elif gx == 2 and gy == 2:
            v = (10, 12, 16)                                              # skrz rošt prosvítá propast
        if (y - TB) % 30 == 0:
            v = (30, 32, 36)                                              # nosník
        if _vn(x, y, 41, 5) > 0.8:
            v = tuple(int(a * 0.6 + b * 0.4) for a, b in zip(v, RUST[2][:3]))   # rez
        img[y, x, :3] = v
for y in range(TB, TB + 6):                                               # stín kmene na lávce
    for x in range(AW):
        if deck[y, x]:
            img[y, x, :3] = (img[y, x, :3] * (0.45 + (y - TB) * 0.09)).astype(np.uint8)
for y in range(DECK_B, DECK_B + 8):                                       # tloušťka lávky a nosníky pod ní
    x0, x1 = deck_x(DECK_B - 1)
    for x in range(int(x0), int(x1) + 1):
        img[y, x, :3] = STEEL[1][:3] if y < DECK_B + 3 else (16, 18, 22)
for (x0, a) in ((32, 2.3), (144, 0.84), (60, 2.0), (116, 1.15)):          # vzpěry do tmy
    for i in range(60):
        put(x0 + math.cos(a) * i * 0.5, DECK_B + 4 + math.sin(a) * i * 0.5 + i * 0.4, STEEL[2], max(0.0, 1 - i / 60))
for y in range(TB + 4, DECK_B):                                           # zábradlí po bocích
    for side in (0, 1):
        x = deck_x(y)[side]
        put(x, y, STEEL[4]); put(x + (1 if side == 0 else -1), y, STEEL[2])
        if (y - TB) % 12 == 0:
            for k in range(1, 9):
                put(x, y - k, STEEL[3])
        put(x, y - 8, STEEL[4])

# kořenové náběhy kmene se rozlévají na lávku (kmen nekončí rovnou čárou)
for (x0, dirx, ln, w0) in ((4, 1, 40, 14), (20, 1, 26, 9), (172, -1, 40, 14), (150, -1, 22, 8), (46, 1, 14, 6), (128, -1, 14, 6)):
    for i in range(ln):
        t = i / ln
        y = TB - 6 + i
        cxr = x0 + dirx * (i * 0.55 + t * t * 10)
        w = w0 * (1 - t) ** 0.7 + 0.6
        for x in range(int(cxr - w), int(cxr + w) + 1):
            if 0 <= x < AW:
                uu = (x - cxr) / w
                img[y, x, :3] = ramp(BARKF, 0.42 - uu * 0.22 - abs(uu) ** 4 * 0.2 - t * 0.12, x, y)
                deck[y, x] = False
        for x in (int(cxr - w) - 1, int(cxr + w) + 1):
            put(x, y + 1, (10, 10, 12), 0.7)

# ── větev z Prahu: obří kůra se stupy, lano na kolících ──────────────────────
BR = bez((98, AH + 20), (70, 480), (88, DECK_B - 6), 400)


def br_x(y):
    return min(BR, key=lambda p: abs(p[1] - y))[0]


bw = np.zeros((AH, AW), bool)
for y in range(DECK_B, AH):
    bx = br_x(y)
    hw = 22 + (y - DECK_B) / (AH - DECK_B) * 10
    for x in range(int(bx - hw - 1), int(bx + hw + 7)):
        if not 0 <= x < AW:
            continue
        u = (x - bx) / hw
        if abs(u) <= 1:
            fur = math.sin(u * 9 + _vn(u * 3, y * 0.05, 45, 2) * 3)          # brázdy po délce větve
            v = 0.5 - u * 0.26 - abs(u) ** 5 * 0.3 + fur * 0.06 + (_vn(x * 0.5, y * 0.12, 44, 3) - 0.5) * 0.16
            if fur > 0.9:
                v -= 0.14
            img[y, x, :3] = ramp(BARKF, v, x, y)
            bw[y, x] = abs(u) < 0.8
        elif 1 < u < 1 + 6 / hw:
            img[y, x, :3] = (12, 12, 14)                                  # stinná strana větve
for y in range(DECK_B + 10, AH, 11):                                      # vyšlapané stupy
    bx = br_x(y)
    for x in range(int(bx - 13), int(bx + 12)):
        put(x, y, (40, 36, 30), 0.8); put(x, y + 1, (190, 178, 150), 0.35)
BR = [(br_x(y), y) for y in range(DECK_B, AH)]
POSTS = [BR[i] for i in range(8, len(BR) - 4, 24)]
for (bx, by) in POSTS:                                                    # kolíky s lanem po pravé straně
    for k in range(9):
        put(bx + 18, by - k, (90, 70, 46) if k else (40, 30, 20))
for (p, q) in zip(POSTS, POSTS[1:]):
    for (x, y) in bez((p[0] + 18, p[1] - 8), ((p[0] + q[0]) / 2 + 18, (p[1] + q[1]) / 2 - 4), (q[0] + 18, q[1] - 8), 30):
        put(x, y, (150, 130, 96))

# ── pata kmene: puklina dolů (Střed kořenů), popel, hadice ───────────────────
CRX = 156
for y in range(250, TB + 2):                                              # puklina v kůře u paty vpravo
    t = (y - 250) / (TB - 250)
    hw = 2 + t * 7
    for x in range(int(CRX - hw - 2), int(CRX + hw + 3)):
        d = abs(x - CRX) - hw
        if d < 0:
            img[y, x, :3] = (6, 6, 8) if d < -1.5 else (30, 26, 22)
        elif d < 2:
            img[y, x, :3] = ramp(BARKF, 0.7 - d * 0.2, x, y)              # odštípnutý okraj
for k in range(4):                                                        # uvnitř hluboko slabě svítí znaky
    put(CRX + (k % 2) - 1, 270 + k * 7, (70, 120, 160))
for (ax, ay, rx, ry) in ((76, TB + 10, 16, 6), (104, TB + 14, 14, 6), (90, TB + 22, 10, 4), (122, TB + 28, 8, 3)):
    for y in range(ay - ry, ay + ry + 1):                                 # hromady popela pod Trpitelem
        for x in range(ax - rx, ax + rx + 1):
            d = ((x - ax) / rx) ** 2 + ((y - ay) / ry) ** 2
            if d < 1:
                v = 0.72 - d * 0.4 - (y - ay) / ry * 0.12 + (_vn(x, y, 50, 2) - 0.5) * 0.12
                img[y, x, :3] = ramp([F((40, 38, 36)), F((74, 70, 66)), F((112, 108, 102)), F((150, 146, 140)), F((184, 180, 172))], v, x, y)
ASHFALL = []
for _ in range(220):                                                      # popel padá z těla dolů na lávku
    x = CX + rng.normal(0, 26)
    y = rng.uniform(150, TB + 30)
    if 0 <= x < AW:
        put(x, y, (150, 146, 140) if rng.random() < 0.6 else (96, 92, 88), 0.8)
        ASHFALL.append((int(x), int(y)))

tanks = O.foam_tanks()
sc.place(tanks, 144, 388)
HOSE = bez((134, 362), (150, 300), (112, 248), 120)                       # hadice z nádrží do rány
for i, (x, y) in enumerate(HOSE):
    for k in (-1, 0, 1):
        put(x + k, y, (20, 20, 22) if k else (56, 56, 60))
    if i % 12 == 0:
        put(x, y, YELLOW[2])
for (x, y) in ((110, 250), (111, 251), (109, 249)):
    put(x, y, (200, 190, 150))


def probe():
    """Sonda Řádu: skříňka na trojnožce, trychtýřový reproduktor, rudá kontrolka, štítek se znakem vah."""
    sp_ = Spr(22, 34)
    for (x0, x1) in ((11, 3), (11, 19), (11, 11)):
        sp_.line(x0, 16, x1, 33, STEEL[2])
    for y in range(6, 18):
        for x in range(4, 18):
            sp_.px(x, y, STEEL[3] if x < 6 else (STEEL[2] if x < 15 else STEEL[1]))
    sp_.hline(4, 17, 6, STEEL[4])
    for y in range(0, 8):                                                 # trychtýř
        for x in range(13 - y // 2, 21):
            if x - 13 + y // 2 < 8:
                sp_.px(x, y, STEEL[2] if (x + y) % 3 else STEEL[3])
    sp_.px(6, 9, c(0xFF4030)); sp_.px(7, 9, c(0xA02018))
    sp_.rect(8, 12, 13, 15, YELLOW[1]); sp_.px(10, 13, c(0x201A10)); sp_.px(11, 13, c(0x201A10))
    return sp_


sc.place(probe(), 38, 362)
sc.place(O.flood_mast(44), 30, 340)
sc.place(O.flood_mast(44), 146, 336)
pl = plate("OPERACE PEČEŤ", c(0xDCBE50), c(0x28282C), seed=5, border=c(0xA0281E))
sc.place(O.mount_plate(pl, posts=7, post_col=[c(0x1C1E22), c(0x2E3238), c(0x4A5058), c(0x6A7078), c(0x8A9098)]), 52, 402)
sc.draw_sprites()

# ── světlo: tma, studená záře z prasklin a z rány v hrudi, sodíkové světlomety, oči ──
cold = [(TX, TY + 20, 40, 70, 0.9)] + [(x, y, 10, 10, 0.35) for (x, y) in CRACKS[::6]]
warm = [(78, 236, 30, 40, 0.8), (104, 236, 30, 40, 0.8), (30, 300, 14, 10, 0.5), (146, 296, 14, 10, 0.5)]
sc.light(warm=warm, cold=cold + [(CX, 360, 70, 40, 0.5), (90, 480, 50, 90, 0.35)], dark_k=0.62, warm_k=0.3, cold_k=0.26)
L = __import__("light")
beam = np.zeros((AH, AW))
for (lx, ly, tx_) in ((28, 298, 74), (148, 294, 102)):                     # kužely světlometů nahoru na ránu
    for i in range(110):
        t = i / 110
        cx_, cy_ = lx + (tx_ - lx) * t, ly - t * 110
        hw = 3 + t * 22
        for x in range(int(cx_ - hw), int(cx_ + hw) + 1):
            if 0 <= x < AW and 0 <= int(cy_) < AH:
                beam[int(cy_), x] = max(beam[int(cy_), x], (1 - abs(x - cx_) / hw) * (1 - t) * 0.6)
L.lighten(img, beam, 0.35, (255, 200, 120), steps=8)

# ── chůze: rošt lávky (ne u zábradlí) a hřbet větve ───────────────────────────
block[:, :] = True
for y in range(TB + 8, DECK_B + 1):
    x0, x1 = deck_x(y)
    block[y, int(x0) + 4:int(x1) - 3] = False
block[TB + 8:TB + 34, 60:120] = False
for y in range(DECK_B - 4, AH):
    for x in range(AW):
        if bw[y, x]:
            block[y, x] = False
for o in sc.placed:
    s_ = o["spr"]
    block[o["y"] + s_.h - 6:o["y"] + s_.h, o["x"] + 2:o["x"] + s_.w - 2] = True

sc.export("root_trunk", "gen_trunk.py")
from PIL import Image  # noqa: E402
_m = Image.open(os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi", "root_trunk.png")).convert("RGBA")
_s = Image.open(os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi", "trpitel_sheet.png"))
_m.alpha_composite(_s.crop((0, 0, TP.W, TP.H)), (TP.MAP_X0, TP.MAP_Y0))
_m.save(os.path.join(HERE, "root_trunk_preview.png"))
