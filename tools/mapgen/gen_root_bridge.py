"""
Použití:  python tools/mapgen/gen_root_bridge.py
Výstup:   app/src/main/res/drawable-nodpi/root_bridge.png, app/src/main/assets/walk/root_bridge.txt,
          app/src/main/res/drawable-nodpi/npc_paska.png, tools/mapgen/root_bridge_debug.png

Zóna 3 (Akt III – Hlubina), vedlejší lokace: KOŘENOVÝ MOST (176 × 560, pohled 3/4 shora, docs/adr/0106).
Gondola ze Signálního útesu doveze hráče na konec lana: ocelová plošina přišroubovaná na kořen, který se klene
přes propast až ke kmeni Spící. Kamera jede s hráčem vzhůru po hřbetu kořene; nahoře celou šířku mapy zabírá
kmen – kůra jako kost s hlubokými rýhami, ve kterých prosvítá zelené světlo. Pohlcení 6/8: předsunutý sklad
„Operace Pečeť“ (nádrže s pěnou, potrubí, které mizí přes hranu dolů k ráně, ventil, lešení přišroubované do kmene)
je už napůl v kůře. Magnetofon technika z výpravy 8 (hlášení o snech). Svatyňka Lumenců: trn, na kterém je
nabodnutá drobná světélkující bytost, a nápis AŤ NIKDO NENÍ SÁM. U paty kmene v kůře vrostlý obrys člověka.
V propasti pod mostem další kořeny v různých hloubkách a mlha. Vedlejší úkol „Noční směna“ (story/RootBridgeTape).
Setkání PŘÍRODA a PSYCHO. Živé detaily: InteriorFxView.ROOT_BRIDGE. NODES / EDGES jsou ZDROJ PRAVDY i pro RootBridge.kt.
"""
import math, os, sys
import numpy as np
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "hlubina")); sys.path.insert(0, os.path.join(HERE, "tower"))
from common import Spr, c, _pick, _h, _vn, STEEL, BARK, OLIVE  # noqa: E402
from scene import Scene, ROOT  # noqa: E402
from roots import root, bez  # noqa: E402
from tree import TreePainter  # noqa: E402
import order as O, signs as SG  # noqa: E402

AW, AH, WALL_H = 176, 560, 132
NODES = {
    "km_vstup": (104, 546), "km_kotva": (70, 516), "km_cesta1": (88, 470), "km_paska": (76, 442),
    "km_cesta2": (90, 392), "km_svatyne": (70, 346), "km_nadrze": (124, 282), "km_cesta3": (100, 236),
    "km_ventil": (136, 214), "km_leseni": (132, 152), "km_obrys": (58, 148),
}
EDGES = [("km_vstup", "km_kotva"), ("km_vstup", "km_cesta1"), ("km_cesta1", "km_paska"), ("km_cesta1", "km_cesta2"),
         ("km_cesta2", "km_svatyne"), ("km_cesta2", "km_nadrze"), ("km_nadrze", "km_cesta3"), ("km_cesta3", "km_ventil"),
         ("km_cesta3", "km_leseni"), ("km_cesta3", "km_obrys"), ("km_obrys", "km_leseni")]
sc = Scene(AW, AH, WALL_H, NODES, EDGES)
img, block = sc.img, sc.block
rng = np.random.default_rng(68)

# ── tvar kořene: osa a poloviční šířka (u kmene se rozšiřuje), boule pro svatyňku, magnetofon a nádrže ──
CY = [WALL_H, 190, 250, 320, 400, 470, 530, AH]
CX = [92, 98, 110, 100, 84, 86, 98, 104]
HY = [WALL_H, 150, 175, 215, AH]
HW = [82, 56, 40, 33, 31]


def bump(y, y0, r, k):
    return k * max(0.0, 1 - ((y - y0) / r) ** 2)


def edges(y):
    cx, hw = np.interp(y, CY, CX), np.interp(y, HY, HW)
    wob = (_vn(0, y, 51, 13) - 0.5) * 6
    L = cx - hw - bump(y, 338, 30, 20) - bump(y, 440, 22, 14) - bump(y, 512, 18, 10) + wob
    R = cx + hw + bump(y, 276, 30, 22) + bump(y, 212, 20, 10) - wob * 0.5
    return L, R


# ── propast: tma, zelená záře zdola, vzdálené kořeny v různých hloubkách ────
def mist(x, y):
    g = max(0.0, 1 - math.hypot((x - 60) / 220, (y - 640) / 420)) ** 1.6
    col = np.array([3, 6, 8], float) * (1 - g) + np.array([40, 92, 78], float) * g
    n = _vn(x * 0.5, y * 1.1, 3, 30) * 0.6 + _vn(x, y, 5, 9) * 0.4
    return col * (0.78 + 0.44 * n)


for y in range(AH):
    for x in range(AW):
        img[y, x, :3] = np.clip(mist(x, y), 0, 255)
for depth_k, seed, specs in ((0.75, 7, [((-20, 300), (80, 250), (200, 340), 5.5), ((-20, 520), (90, 470), (200, 560), 4.5)]),
                             (0.5, 8, [((200, 200), (120, 300), (-20, 380), 8), ((-10, 160), (40, 210), (120, 230), 4)])):
    tp = TreePainter(img, lambda x, y, k=depth_k: (np.clip(mist(x, y), 0, 255), k), seed=seed)
    for (p0, p1, p2, r) in specs:
        tp.stroke([(x, y, r * (0.8 + 0.3 * math.sin(i / 7))) for i, (x, y) in enumerate(bez(p0, p1, p2, 80))])
        for _ in range(3):                                                  # odbočky
            t = rng.uniform(0.2, 0.8); bx, by = bez(p0, p1, p2, 80)[int(t * 79)]
            tp.branch(bx, by, rng.uniform(0.6, 2.5), r * 0.45, 40, 4, math.pi / 2, maxdepth=6, pull=0.03)
    tp.composite(dark=(6, 10, 10), light=(22, 34, 32))
for y in range(WALL_H, AH):                                                # vrstvy mlhy (měkké, 4 stupně)
    for x in range(AW):
        m = _vn(x * 0.7 + 11, y * 1.5, 61, 24) * 0.7 + _vn(x, y * 1.2, 62, 7) * 0.3
        a = math.floor(max(0.0, (m - 0.6) * 1.6) * 4) / 4 * 0.5
        if a > 0:
            img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([64, 128, 108]) * a, 0, 255)
# ── kmen: kůra jako kost, hluboké svislé rýhy se zeleným světlem (stěna nahoře i pokračování dolů do tmy) ──
TRUNK = [c(0x14130F), c(0x2A2722), c(0x433F37), c(0x5E594E), c(0x7C7668), c(0x9C9584), c(0xBDB6A2)]
FUR = []                                                                  # svislé rýhy kůry
for k in range(-1, 16):
    FUR.append((k * 12.5 + rng.uniform(-3, 3), rng.uniform(0, 6), rng.uniform(26, 44), k))


def fur_x(f, y):
    b, ph, per, k = f
    return b + 3.5 * math.sin(y / per + ph) + (_vn(k * 31, y, 71, 14) - 0.5) * 5


def trunk_v(x, y, xs):
    """Světlost kůry v bodě (0–1) a zda svítí rýha; xs = seřazené x rýh v řádku y."""
    i = int(np.searchsorted(xs, x + 0.5))
    lft, rgt = xs[max(0, i - 1)], xs[min(len(xs) - 1, i)]
    t = (x + 0.5 - lft) / max(1.0, rgt - lft)                             # 0 u levé rýhy … 1 u pravé
    dist = min(abs(x + 0.5 - lft), abs(rgt - x - 0.5))
    v = 0.6 - abs((x - 88) / 110) ** 2 * 0.45                             # obrovský válec, světlo uprostřed
    v += 0.12 * (0.5 - t) + (_vn(x * 1.4, y * 0.5, 74, 3) - 0.5) * 0.16
    if t < 0.18:
        v += 0.12                                                         # levá hrana plátu ve světle
    if (y + int(lft * 7)) % 23 == 0 and _h(int(lft), y // 23, 78) % 3 == 0:
        v -= 0.3                                                          # příčná prasklina plátu
    g = 0.0
    if dist < 1.3:
        v = 0.04
        if _vn(int(lft) * 13, y * 0.05, 75, 1) > 0.8:
            g = 1.0
    elif dist < 2.4:
        v = min(v, 0.2)
    return v, g


# kmen pokračuje pod kořenem dolů k obrácené koruně: zužuje se do hloubky, tmavne a mizí v mlze
TRUNK_DEPTH = 240
trunk_ext = np.zeros((AH, AW))
for y in range(WALL_H, WALL_H + TRUNK_DEPTH):
    d = (y - WALL_H) / TRUNK_DEPTH
    half = 110 - (y - WALL_H) * 0.26 + (_vn(0, y, 81, 12) - 0.5) * 4
    xs = sorted(fur_x(f, y) for f in FUR)
    L, R = edges(y)
    for x in range(AW):
        off = abs(x + 0.5 - 88)
        if off > half or L - 3 <= x <= R + 3:
            continue
        v, g = trunk_v(x, y, xs)
        v *= 1 - 0.55 * d
        if off > half - 2.5:
            v = 0.03                                                      # obrys válce, který se stáčí pryč
        near = min(abs(x - (L - 3)), abs(x - (R + 3)))                     # kořen vrhá stín na kmen za sebou
        if near < 10:
            v *= 0.45 + 0.55 * near / 10
        col = np.array(_pick(TRUNK, max(0.0, min(1.0, v)), x, y)[:3], float)
        if g and d < 0.75:
            col = col * d + np.array([62, 150, 108], float) * (1 - d) * 0.9
        haze = min(1.0, d ** 1.25 * 1.05)                                  # do hloubky ho pohlcuje mlha a tma
        mcol = np.clip(mist(x, y), 0, 255) * (0.75 + 0.25 * d)
        img[y, x, :3] = np.clip(col * (1 - haze) + mcol * haze, 0, 255)
        trunk_ext[y, x] = 1 - haze
# rozhraní: pás mlhy přes pokračování kmene (měkké vrstvy, aby nebyl vidět řez)
for y in range(WALL_H + 60, WALL_H + TRUNK_DEPTH + 20):
    for x in range(AW):
        m = _vn(x * 0.6 + 5, y * 1.3, 82, 20)
        a = math.floor(max(0.0, (m - 0.55) * 1.4) * min(1.0, (y - WALL_H - 60) / 70) * 4) / 4 * 0.45
        L, R = edges(y)
        if a > 0 and not (L - 3 <= x <= R + 3):
            img[y, x, :3] = np.clip(img[y, x, :3] * (1 - a) + np.array([58, 118, 98]) * a, 0, 255)
vista = img.copy()

# ── kořenový most ───────────────────────────────────────────────────────────
GROOVES = [-0.78, -0.52, -0.3, -0.08, 0.16, 0.4, 0.62, 0.84]
block[:, :] = True
floor = np.zeros((AH, AW), bool)
for y in range(WALL_H - 4, AH):
    L, R = edges(y)
    cx = (L + R) / 2; hw = (R - L) / 2
    for x in range(max(0, int(L) - 3), min(AW, int(R) + 4)):
        u = (x + 0.5 - cx) / hw                                           # −1 vlevo … 1 vpravo
        if abs(u) > 1.0:
            if abs(u) < 1.0 + 3 / hw:                                     # boční stěna kořene do tmy
                img[y, x] = BARK[0] if abs(u) > 1.0 + 1.5 / hw else BARK[1]
            continue
        v = 0.5 + 0.18 * math.cos(u * math.pi / 2) - 0.14 * u              # válec, světlo zleva
        v += (_vn(x * 1.3, y * 1.3, 52, 3) - 0.5) * 0.16
        for gk in GROOVES:                                                # podélné rýhy podél kořene
            dd = abs(u - gk - (_vn(gk * 50, y, 53, 23) - 0.5) * 0.16) * hw
            if dd < 0.8:
                v -= 0.3
            elif dd < 1.6:
                v += 0.08 if u - gk < 0 else -0.12                        # hrana rýhy: světlo zleva
        if _h(x // 2, y // 9, 54) % 41 == 0:                              # příčné praskliny
            v -= 0.2
        if abs(u) > 0.92:
            v = 0.62 if u < 0 else 0.1                                    # hrana: levý okraj ve světle
        img[y, x] = _pick(BARK, max(0.0, min(0.85, v * 0.82)), x, y)
        floor[y, x] = True
        if abs(u) < 1.0 - 5 / hw:
            block[y, x] = False
# dva krátké kořínky z hřbetu, které se noří do tmy
for (p0, p1, p2, w) in (((140, 336), (152, 350), (160, 372), 5),):
    root(sc, bez(p0, p1, p2, 40), w, 2.5, solid=False, lift=0.3, d=0.5)
    for (x, y) in bez(p0, p1, p2, 40)[20:]:
        for yy in range(int(y) - 4, int(y) + 5):
            for xx in range(int(x) - 4, int(x) + 5):
                if 0 <= xx < AW and 0 <= yy < AH and sc.depth[yy, xx] == 0.5 and not floor[yy, xx]:
                    img[yy, xx, :3] = (img[yy, xx, :3] * 0.7).astype(np.uint8)

# plošina konečné stanice (ocelový rošt přišroubovaný na kořen) a lana ke Signálnímu útesu
PL0, PL1 = 60, 160
for y in range(500, AH):
    for x in range(PL0, PL1):
        q = (x + y) % 4 == 0 or (x - y) % 4 == 0
        img[y, x] = STEEL[1] if q else STEEL[3] if y % 12 else STEEL[4]
        floor[y, x] = True
        if PL0 + 3 < x < PL1 - 3:
            block[y, x] = False
    for xe in (PL0, PL1 - 1):
        img[y, xe] = O.YELLOW[2] if (y // 3) % 2 else c(0x16161C)
    img[y, PL1] = STEEL[0]; img[y, PL1 + 1] = STEEL[0]
for x in range(PL0, PL1):                                                 # horní hrana plošiny
    img[499, x] = STEEL[4]; img[498, x] = STEEL[2]
for (bx0, by0) in ((150, 506), (150, 534)):                               # konzoly zespodu ke kořeni
    for t in range(14):
        img[by0 + t // 3, bx0 - t, :3] = (52, 56, 62)
for (y0, dy) in ((520, 0), (526, 3)):                                     # lana z kladky dolů doprava (k útesu)
    for t in np.linspace(0, 1, 160):
        X = int(130 + (AW + 4 - 130) * t); Y = int(y0 + (AH + 12 - y0) * t + math.sin(t * math.pi) * 4 + dy)
        if 0 <= Y < AH and 0 <= X < AW:
            img[Y, X, :3] = (30, 34, 38)
# druhé (přeseknuté) lano visí z kotvy přes hranu do tmy
for t in np.linspace(0, 1, 120):
    X = int(48 - t * 26 + math.sin(t * 5) * 2); Y = int(512 + t * 40)
    if 0 <= X < AW and Y < AH:
        img[Y, X, :3] = (26, 30, 34)

# potrubí s pěnou: od nádrží vpravo vzhůru podél hrany, u kmene přes hranu dolů (k ráně)
pipe = [(140, 270), (146, 240), (148, 214), (146, 186), (150, 168), (160, 160), (166, 168)]
pts = bez(pipe[0], pipe[1], pipe[2], 30) + bez(pipe[2], pipe[3], pipe[4], 30) + bez(pipe[4], pipe[5], pipe[6], 30)
for i, (x, y) in enumerate(pts):
    for k in range(-3, 4):
        X, Y = int(x + k), int(y)
        if 0 <= X < AW and 0 <= Y < AH:
            img[Y, X] = [STEEL[0], STEEL[2], STEEL[4], STEEL[5], STEEL[3], STEEL[1], STEEL[0]][k + 3]
            block[Y, X] = True; sc.depth[Y, X] = sc.depth[Y, X] or 0.01
    if i % 14 == 0:                                                       # objímky přišroubované do kůry
        for k in range(-3, 4):
            if 0 <= int(x + k) < AW:
                img[int(y), int(x + k)] = O.RUST[2]
for y in range(168, 262):                                                 # dolů po kmeni, ztrácí se ve tmě
    d = (y - 168) / 94
    x0 = 166 + int(math.sin(y / 30) * 1.5)
    for k in range(-3, 4):
        col = np.array([STEEL[0], STEEL[2], STEEL[4], STEEL[5], STEEL[3], STEEL[1], STEEL[0]][k + 3][:3], float)
        if (y - 168) % 18 == 0:
            col = np.array(O.RUST[2][:3], float)
        img[y, x0 + k, :3] = np.clip(col * (1 - d) * (1 - 0.4 * d) + img[y, x0 + k, :3] * d, 0, 255)
        sc.depth[y, x0 + k] = 0.01
# kůra přerůstá potrubí (pohlcení): úseky trubky pod kůrou
for (i0, i1) in ((18, 26), (44, 52), (64, 72)):
    for (x, y) in pts[i0:i1]:
        for k in range(-4, 5):
            X = int(x + k)
            if 0 <= X < AW:
                img[int(y), X] = _pick(BARK, 0.5 - k * 0.05 - (0.3 if abs(k) == 4 else 0), X, int(y))

# ── předměty ────────────────────────────────────────────────────────────────
sc.place(O.cable_station(), 130, 520, foot=(46, 12))
st = SG.plate("KONEČNÁ", c(0xE8E2CC), c(0x1E2A44), seed=41, pad=2, wear=0.0)
sc.place(O.mount_plate(st, posts=10), 78, 502, foot=(4, 2))
sc.place(O.cable_anchor(), 46, 512, foot=(12, 6))
sc.place(O.tape_station(), 62, 432, foot=(34, 10))
sc.place(O.lumen_thorn(), 54, 336, foot=(26, 6))
nb = SG.plate("NESAHAT", c(0x141414), c(0xE2BA34), seed=44, pad=3, wear=0.0)
sc.place(O.mount_plate(nb, posts=7), 98, 318, foot=(4, 2))
sc.place(O.foam_tanks(), 142, 270, foot=(36, 8))
pp = SG.plate("PĚNA", c(0x141414), c(0xE2BA34), seed=42, pad=2, wear=0.0)
sc.place(O.mount_plate(pp, posts=9), 104, 264, foot=(4, 2))
sc.place(O.valve_wheel(), 148, 222, foot=(10, 4))
for (x, y, sd) in ((112, 300, 2), (130, 304, 6)):
    sc.place(O.army_crate(sd), x, y, pad=0)
sc.place(O.helmet(), 70, 160, collide=False)
sc.place(O.army_crate(4), 106, 196, pad=0)
for (x, y) in ((122, 488), (100, 296), (104, 176)):                       # pracovní lampy na tyčích
    lamp = Spr(8, 26)
    for yy in range(4, 26):
        lamp.px(3, yy, O.WOOD_POLE[3]); lamp.px(4, yy, O.WOOD_POLE[1])
    lamp.rect(1, 0, 6, 4, STEEL[2]); lamp.rect(2, 2, 5, 4, O.SODIUM)
    sc.place(lamp, x, y, foot=(4, 2))

# zaschlá pěna kolem nádrží a podél potrubí (žlutobílé krusty)
for (fx0, fy0, r) in ((132, 286, 9), (150, 252, 5), (146, 228, 4), (120, 292, 5)):
    for y in range(fy0 - r, fy0 + r):
        for x in range(fx0 - 2 * r, fx0 + 2 * r):
            m = ((x - fx0) / (2 * r)) ** 2 + ((y - fy0) / r) ** 2
            if m < 1 and _vn(x, y, 91, 3) > 0.42 + m * 0.3 and floor[y, x]:
                img[y, x] = c(0xD8D2B0) if _vn(x, y, 92, 2) > 0.5 else c(0xB0A884)
# rozsypané kazety u magnetofonu
for (x, y) in ((84, 446), (90, 452), (80, 456)):
    img[y:y + 2, x:x + 4] = c(0x2A2A30); img[y, x + 1] = c(0xC8C0A0)
# svitek kabelu na plošině
for a_ in range(0, 360, 8):
    for r in (5, 6, 7):
        img[int(516 + math.sin(math.radians(a_)) * r * 0.5), int(96 + math.cos(math.radians(a_)) * r)] = c(0x1C1E22) if r == 6 else c(0x34383E)
# znaky Lumenců vyryté do hřbetu kolem svatyňky (studeně svítí)
for (x, y) in ((70, 350), (74, 356), (62, 358), (80, 344), (58, 312), (86, 360)):
    for (dx, dy) in ((0, 0), (1, 0), (0, 1), (2, 1)):
        img[y + dy, x + dx] = O.GLYPH if (dx + dy) % 2 == 0 else c(0x3E6A98)
sc.shadows(floor)

# ── kmen (zadní stěna): kůra jako kost, hluboké rýhy, zelené světlo v nich ──
glow_mask = np.zeros((AH, AW))
for y in range(WALL_H):
    xs = sorted(fur_x(f, y) for f in FUR)
    for x in range(AW):
        v, g = trunk_v(x, y, xs)
        glow_mask[y, x] = g
        img[y, x] = _pick(TRUNK, max(0.0, min(1.0, v)), x, y)
for y in range(WALL_H):                                                   # zelené světlo z rýh (+ jemná záře okolo)
    for x in range(AW):
        if glow_mask[y, x]:
            img[y, x, :3] = (62, 150, 108)
        elif glow_mask[max(0, y - 1):y + 2, max(0, x - 2):x + 3].any():
            img[y, x, :3] = np.clip(img[y, x, :3] * 0.6 + np.array([34, 90, 66]) * 0.3, 0, 255)
# pata kmene přechází do kořene (stín v koutě)
for y in range(WALL_H - 6, WALL_H + 10):
    for x in range(AW):
        if floor[y, x] or (y < WALL_H and floor[WALL_H + 2, x]):
            k = max(0.0, 1 - abs(y - WALL_H) / 10) * 0.45
            img[y, x, :3] = (img[y, x, :3] * (1 - k)).astype(np.uint8)

# ocelové kotvy v kmeni s rezavými stékanci
for (ax, ay) in ((24, 56), (152, 40), (96, 22), (118, 92)):
    for y in range(ay, ay + 7):
        for x in range(ax, ax + 7):
            img[y, x] = STEEL[3] if y == ay else STEEL[2]
    img[ay + 3, ax + 3] = STEEL[5]
    for y in range(ay + 7, ay + 7 + 10 + (ax % 7)):
        if img[y, ax + 3, 0] > 30:
            img[y, ax + 3, :3] = (img[y, ax + 3, :3] * 0.5 + np.array([100, 52, 28]) * 0.5).astype(np.uint8)
    for t in np.linspace(0, 1, 40):                                       # utržený řetěz
        X = int(ax + 3 + math.sin(t * 6) * 1.5); Y = int(ay + 7 + t * 8)
        if (int(t * 40)) % 3:
            img[Y, X, :3] = (60, 62, 66)

# obrys člověka vrostlý do kůry (vlevo u paty kmene): silueta jako reliéf – světlo zleva, stín vpravo a dole
OX, OY = 56, 100
fig = np.zeros((AH, AW), bool)
for y in range(OY, OY + 30):
    for x in range(OX - 8, OX + 9):
        dy = y - OY
        if dy < 6:
            ok = math.hypot(x - OX, dy - 3) < 3.2                         # hlava
        elif dy < 18:
            ok = abs(x - OX) <= 4 or (abs(x - OX) <= 6 and dy < 17)       # trup a paže podél těla
        else:
            ok = 1 <= abs(x - OX) <= 3                                    # nohy
        fig[y, x] = ok
for y in range(OY - 1, OY + 32):
    for x in range(OX - 9, OX + 10):
        col = img[y, x, :3].astype(float)
        if fig[y, x]:
            col = np.array([96, 90, 78], float) * 0.7 + col * 0.3
            if not fig[y, x - 1]:
                col = np.array([150, 142, 126], float)
            elif not fig[y, x + 1] or not fig[y + 1, x]:
                col = np.array([40, 37, 32], float)
        elif fig[y, x - 1] or fig[y - 1, x]:
            col = col * 0.45                                              # vržený stín do kůry
        img[y, x, :3] = col.astype(np.uint8)
for (x, y) in ((OX - 2, OY + 9), (OX - 1, OY + 9), (OX, OY + 9), (OX - 1, OY + 10), (OX - 2, OY + 10)):
    img[y, x] = O.OLIVE[3]                                                # cár kombinézy s nášivkou
img[OY + 9, OX + 1] = O.YELLOW[2]
for y in range(OY + 4, OY + 26):                                          # kůra přes obrys (zarůstá zprava)
    for x in range(OX + 2, OX + 9):
        if fig[y, x] and _vn(x, y * 0.5, 79, 3) > 0.55:
            img[y, x] = _pick(TRUNK, 0.35, x, y)

# lešení Řádu přišroubované do kmene, kůra ho z levé strany pohlcuje
sc.zbuf[:WALL_H] = -1e9
scf = O.scaffold(56, 78)
pl = SG.plate("PEČEŤ", c(0xE8E2CC), c(0x1E2A44), seed=43, pad=2, wear=0.0)
scf_s = Spr(scf.w, scf.h); scf_s.a[:] = scf.a
for yy in range(pl.h):
    for xx in range(pl.w):
        if pl.a[yy, xx, 3]:
            scf_s.a[24 + yy, scf.w - pl.w - 3 + xx] = pl.a[yy, xx]
sc.place(scf_s, 132, WALL_H + 8, foot=(56, 10), shadow=False)
sc.draw_sprites()
for y in range(WALL_H - 70, WALL_H):                                      # jazyky kůry přes levé sloupky lešení
    w = 6 + int(_vn(0, y, 76, 8) * 10)
    for x in range(104, 104 + w):
        if 0 <= x < AW:
            img[y, x] = _pick(TRUNK, 0.55 - (x - 104) / max(1, w) * 0.4 + (_vn(x, y, 77, 3) - 0.5) * 0.2, x, y)
    img[y, 104 + w] = TRUNK[1]
block[:WALL_H + 2, :] = True
block[WALL_H + 2:WALL_H + 10, 108:158] = True                             # pata lešení

fungi = []                                                                # trsy hub u hran kořene
for (fx0, fy0) in ((44, 300), (150, 352), (128, 470), (70, 214)):
    for k in range(4):
        x, y = fx0 + int(rng.integers(-4, 5)), fy0 + int(rng.integers(-3, 4))
        fungi.append((x, y))
        img[y, x, :3] = (150, 210, 255); img[y, x - 1, :3] = (80, 140, 220); img[y + 1, x, :3] = (40, 70, 120)

block[540:AH, 64:156] = False                                             # nástup z gondoly
lamps = [(122, 466), (100, 274), (104, 154)]
sc.light(
    warm=[(x, y + 18, 26, 16, 0.85) for (x, y) in lamps] + [(130, 506, 30, 18, 0.8), (62, 420, 16, 10, 0.4)],
    cold=[(x, y, 7, 6, 0.6) for (x, y) in fungi] + [(48, 300, 26, 22, 1.0), (56, 114, 22, 26, 0.5), (68, 352, 18, 10, 0.5)],
    dark_k=0.36, warm_k=0.32, cold_k=0.2)
keep = ~floor & (sc.zbuf < -1e8) & (sc.depth == 0)
keep[:WALL_H] = False
wv = (1 - trunk_ext[keep])[:, None]                                       # kmen do hloubky plynule přechází do výhledu
img[keep, :3] = ((vista[keep, :3] * 0.8 + img[keep, :3] * 0.2) * wv + img[keep, :3] * (1 - wv)).astype(np.uint8)
sc.export("root_bridge", "gen_root_bridge.py")

# portrét magnetofonu pro dialogy úkolu (8× bez vyhlazení)
ts = O.tape_station().a
Image.fromarray(ts, "RGBA").resize((ts.shape[1] * 8, ts.shape[0] * 8), Image.NEAREST).save(
    os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "npc_paska.png"))
print("LAMPS", lamps, "FUNGI", fungi[:8])
gy, gx = np.nonzero(glow_mask)
print("GLOWS", [(int(gx[i]), int(gy[i])) for i in np.linspace(0, len(gx) - 1, 6).astype(int)])
