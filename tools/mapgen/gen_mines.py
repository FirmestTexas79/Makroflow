"""
Použití:  python tools/mapgen/gen_mines.py   (vyžaduje Pillow + numpy)
Výstup:   app/src/main/res/drawable-nodpi/mines.png      (1 px = 1 art pixel)
          app/src/main/assets/walk/mines.txt              (mapa chůze, buňka 3 px)
          tools/mapgen/mines_debug.png, mines_walk.png

Doly (docs/adr/0049) – opuštěné hlubinné doly za Starým dolem. Tři patra:
  0  nakládací rampa: koleje, vozíky, rumpál, lucerna s jiskřivkami, stará síťka na háku
  1  puklina: výdřeva končí, stěny se lámou do přirozené jeskyně s krystaly (krystalové mušky)
  2  lávový sál: lávový vodopád z pukliny ve skále, jezírko, stoka a lávová řeka přes sál;
     přes řeku vede kolejový most. Nahoře komora se zamčenými železnými dveřmi.

Uzly, hrany a tvary lávy jsou ZDROJ PRAVDY i pro cave/MinesMap.kt – při změně upravit obojí.
Lávu i mušky v aplikaci rozpohybuje MinesFxView (MinesArt.kt) přesně nad těmito pixely.
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_caves as G
from gen_caves import Cave, BAYER, WALL, WALL_FH, STEP_FH, OUT, STONE, WOOD, RAIL, LANTERN, ORE, CRY_R, value_noise

ROOT = os.path.join(HERE, "..", "..")
WALK = os.path.join(ROOT, "app", "src", "main", "assets", "walk")
CELL = 3

AW, AH = 150, 540

NODES = {
    "zpet_do_stoly":  (75, 530),
    "nakladiste":     (75, 478),
    "hmyz_jiskrivky": (38, 470),
    "stara_sitka":    (112, 490),
    "rumpal":         (114, 448),
    "prasklina":      (75, 368),
    "hmyz_krystal":   (108, 352),
    "puklina":        (36, 352),
    "pata_mostu":     (75, 278),
    "lavovy_sal":     (75, 232),
    "hmyz_magma":     (54, 214),
    "popel":          (114, 222),
    "zelezne_dvere":  (75, 130),
}
LVL = {"zpet_do_stoly": 0, "nakladiste": 0, "hmyz_jiskrivky": 0, "stara_sitka": 0, "rumpal": 0,
       "prasklina": 1, "hmyz_krystal": 1, "puklina": 1,
       "pata_mostu": 2, "lavovy_sal": 2, "hmyz_magma": 2, "popel": 2, "zelezne_dvere": 2}
EDGES = [("zpet_do_stoly", "nakladiste"), ("nakladiste", "hmyz_jiskrivky"), ("nakladiste", "stara_sitka"),
         ("nakladiste", "rumpal"), ("nakladiste", "prasklina"),
         ("prasklina", "hmyz_krystal"), ("prasklina", "puklina"), ("prasklina", "pata_mostu"),
         ("pata_mostu", "lavovy_sal"), ("lavovy_sal", "hmyz_magma"), ("lavovy_sal", "popel"),
         ("lavovy_sal", "zelezne_dvere")]

# ── láva: stejné tvary jako MinesMap.kt ──
FALL = (29, 37, 156, 204)                  # x0, x1, y0, y1 (včetně)
POOL = (33.0, 206.0, 11.0, 6.0)            # cx, cy, rx, ry
CHANNEL = (29, 37, 210, 251)
RIVER = (26, 127, 252, 261)
BRIDGE = (67, 83)
SWARMS = {"SPARK": (30, 452), "CRYSTAL": (118, 336), "MAGMA": (40, 192)}

LAVA = [(42, 8, 4), (94, 19, 8), (160, 38, 12), (224, 82, 14), (255, 138, 30), (255, 192, 74), (255, 240, 168)]
CRY_V = [(70, 40, 120), (130, 80, 210), (190, 150, 255), (245, 230, 255)]      # fialové krystaly
EMBER_FERN = [(96, 34, 18), (190, 84, 36), (255, 178, 96)]                     # žhnoucí mech = místa setkání
STEEL = [(40, 44, 52), (70, 76, 86), (104, 112, 124), (150, 158, 170)]
RUSTC = [(92, 44, 24), (140, 70, 34)]
COOL = (140, 170, 230)


def floor_lava(x, y):
    cx, cy, rx, ry = POOL
    dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
    if dx * dx + dy * dy < 1: return True
    if CHANNEL[0] <= x <= CHANNEL[1] and CHANNEL[2] <= y <= CHANNEL[3]: return True
    if RIVER[2] <= y <= RIVER[3] and RIVER[0] <= x <= RIVER[1] and not (BRIDGE[0] <= x <= BRIDGE[1]): return True
    return False


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] * (1 - t) + b[i] * t for i in range(3))


def lava_color(x, y):
    """Statická láva (v aplikaci ji přepíše animace; bez ní musí vypadat stejně dobře)."""
    b = BAYER[y % 4, x % 4]
    if FALL[0] <= x <= FALL[1] and FALL[2] <= y <= FALL[3]:
        edge = x in (FALL[0], FALL[1])
        l = 1.6 if edge else 4.0 + ((x * 7 + y // 2 * 3) % 5) * 0.35 + b * 0.6 - (1.2 if x in (FALL[0] + 1, FALL[1] - 1) else 0)
    elif RIVER[2] <= y <= RIVER[3] and not (CHANNEL[0] <= x <= CHANNEL[1] and y < RIVER[2]):
        l = 3.6 + ((x // 3 * 5 + y) % 4) * 0.4 + b * 0.6
        if y in (RIVER[2], RIVER[3]): l = 1.4 + b
        elif y in (RIVER[2] + 1, RIVER[3] - 1): l -= 0.9
    else:
        l = 3.8 + b * 0.6
        if CHANNEL[0] <= x <= CHANNEL[1] and y > POOL[1] + POOL[3] and x in (CHANNEL[0], CHANNEL[1]): l -= 1.6
    return LAVA[int(max(0, min(6, l)))]


def main():
    c = Cave("mines", AW, AH, NODES, EDGES, LVL, seed=37)

    # ── tvar ──
    c.blob(0, 75, 472, 66, 42, rough=0.10, k=1)             # nakládací rampa
    c.rect(0, 58, 500, 93, AH)                              # štola dolů do Starého dolu
    c.rect(0, 62, 398, 89, 440)                             # rampa k puklině (dolní půlka)
    c.blob(1, 75, 360, 62, 40, rough=0.14, k=2)             # puklina
    c.blob(1, 118, 340, 24, 22, rough=0.22, k=3)            # krystalová jeskyně vpravo
    c.blob(1, 30, 352, 22, 18, rough=0.22, k=4)             # výklenek vlevo
    c.rect(1, 62, 282, 89, 330)                             # rampa k lávovému sálu
    c.blob(2, 75, 232, 66, 54, rough=0.08, k=5)             # lávový sál
    c.rect(2, 66, 110, 85, 190)                             # chodba ke dveřím
    c.blob(2, 75, 124, 30, 18, rough=0.10, k=6)             # komora se železnými dveřmi
    c.carve_nodes()

    lava = np.zeros((AH, AW), bool)
    for y in range(AH):
        for x in range(AW):
            if floor_lava(x, y): lava[y, x] = True
    c.water = lava & (c.H == 2)
    assert (c.water == lava).all(), "láva musí ležet celá na podlaze sálu"

    c.faces()
    c.paint_base()
    c.rock_mass()
    c.outline()
    c.moss(0.3)

    ys, xs = c.ys, c.xs
    floor = c.is_floor()

    # ── koleje: od štoly nahoru přes rampy a most až do sálu ──
    for y in range(236, AH):
        if lava[y, 75] and not (BRIDGE[0] <= 75 <= BRIDGE[1]): continue
        for rx in (71, 79):
            if c.face[y, rx] < 0 or c.H[y, rx] < WALL: c.px(rx, y, RAIL)
        if y % 3 == 0:
            for x in range(70, 81):
                if x not in (71, 79): c.px(x, y, G.WOOD[1])
    # vedlejší kolej doprava k vozíkům
    for i in range(0, 34):
        x = 80 + i; y = 470 + i // 4
        c.px(x, y - 2, RAIL); c.px(x, y + 2, RAIL)
        if i % 3 == 0:
            for k in (-1, 0, 1): c.px(x, y + k, G.WOOD[1])

    # ── schody (rampy) mezi patry ──
    stairs_walk = []
    for lo, hi in (("nakladiste", "prasklina"), ("prasklina", "pata_mostu")):
        p = c.crossing(lo, hi, LVL[lo], LVL[hi])
        x, y = p
        yy = y
        while yy + 1 < AH and c.face[yy + 1, x] < 0 and c.H[yy + 1, x] == LVL[hi]: yy += 1
        c.stairs(x, yy - 2, yy + STEP_FH + 2, w=9)
        stairs_walk.append((x - 6, yy - 4, x + 7, yy + STEP_FH + 5))
        # koleje přes rampu (šikmá kolej)
        for sy in range(yy - 2, yy + STEP_FH + 3):
            c.px(71, sy, RAIL); c.px(79, sy, RAIL)

    # ── výdřeva a lucerny (patra 0 a 1) ──
    def face_top(x, y):
        """Nejvyšší řádek čela nad podlahou v bodě (x, y)."""
        yy = y
        while yy > 0 and c.face[yy, x] < 0 and c.H[yy, x] < WALL: yy -= 1
        while yy > 0 and c.face[yy - 1, x] >= 0: yy -= 1
        return yy if c.face[yy, x] >= 0 else None

    for (x, y) in ((40, 440), (112, 440), (44, 330), (100, 326)):
        t = face_top(x, y)
        if t is not None and c.faceH[t, x] >= WALL_FH: c.support(x, t)
    # polámaná výztuž u pukliny: sloupek vlevo stojí, trám spadl šikmo
    t = face_top(60, 340)
    if t is not None:
        for k in range(WALL_FH): c.px(54, t + k, G.WOOD[0]); c.px(53, t + k, G.WOOD[1])
        for k in range(12): c.px(55 + k, t + 2 + k // 2, G.WOOD[2]); c.px(55 + k, t + 3 + k // 2, G.WOOD[1])

    # lucerna na sloupu u jiskřivek (mušky krouží kolem ní)
    sx, sy = SWARMS["SPARK"]
    post_x = sx - 7
    for y in range(sy - 14, sy + 12):
        c.px(post_x, y, G.WOOD[0]); c.px(post_x + 1, y, G.WOOD[1])
    for x in range(post_x, sx + 1): c.px(x, sy - 14, G.WOOD[2])
    c.px(post_x - 1, sy + 12, OUT); c.px(post_x + 2, sy + 12, OUT)
    c.blocks.append((post_x - 1, sy + 8, post_x + 3, sy + 13))
    G.Cave.lantern(c, sx, sy - 10)
    for (x, y) in ((62, 424), (98, 424), (100, 390), (46, 392)):
        t = face_top(x, y)
        if t is not None: c.lantern(x, t + 3)

    # vozíky: na vedlejší koleji plný rudy, u stěny převrácený (na háku nad ním visí síťka)
    def cart(cx, cy, tipped=False):
        c.blocks.append((cx - 8, cy - 12, cx + 9, cy + 1))
        if not tipped:
            for y in range(cy - 11, cy - 3):
                for x in range(cx - 7, cx + 8):
                    e = x in (cx - 7, cx + 7) or y in (cy - 11, cy - 4)
                    c.px(x, y, OUT if e else (STEEL[1] if y > cy - 9 else (ORE[0] if (x + y) % 3 == 0 else STEEL[2])))
            for x in (cx - 4, cx + 4):
                c.px(x, cy - 3, OUT); c.px(x, cy - 2, STEEL[0])
        else:
            for y in range(cy - 9, cy - 1):
                for x in range(cx - 6, cx + 6):
                    e = x in (cx - 6, cx + 5) or y in (cy - 9, cy - 2)
                    c.px(x, y, OUT if e else (RUSTC[1] if (x * 3 + y) % 5 == 0 else STEEL[1]))
            for (dx, dy) in ((-7, -6), (-7, -3)):                  # kola ve vzduchu
                c.px(cx + dx, cy + dy, STEEL[0]); c.px(cx + dx - 1, cy + dy, OUT)
            for (dx, dy) in ((7, -1), (9, 0), (10, -1), (-8, 0)):  # vysypaná ruda
                c.px(cx + dx, cy + dy, ORE[0])
    cart(100, 478)
    cart(122, 482, tipped=True)
    # rezavý hák ve stěně nad převráceným vozíkem (síťku na něj kreslí aplikace)
    t = face_top(120, 470)
    if t is not None:
        for k in range(4): c.px(120, t + 1 + k, RUSTC[0])
        c.px(121, t + 4, RUSTC[1]); c.px(122, t + 3, RUSTC[1])

    # rumpál (dřevěný buben s lanem) u pravé stěny
    rx_, ry_ = 128, 440
    c.blocks.append((rx_ - 6, ry_ - 8, rx_ + 6, ry_ + 3))
    for y in range(ry_ - 6, ry_ + 1):
        c.px(rx_ - 5, y, G.WOOD[1]); c.px(rx_ + 5, y, G.WOOD[1])
    for y in range(ry_ - 5, ry_ - 1):
        for x in range(rx_ - 4, rx_ + 5):
            c.px(x, y, G.WOOD[2] if (x + y) % 2 else (180, 160, 120))
    for k in range(6): c.px(rx_ + 6 + k // 2, ry_ - 3 - k, (180, 160, 120))
    # bedny
    for (bx, by) in ((20, 492), (26, 496), (132, 500)):
        c.blocks.append((bx - 3, by - 5, bx + 4, by + 1))
        for y in range(by - 5, by + 1):
            for x in range(bx - 3, bx + 4):
                c.px(x, y, OUT if x in (bx - 3, bx + 3) or y in (by - 5, by) else (G.WOOD[2] if (x - y) % 4 else G.WOOD[0]))

    # ── praskliny ve stěnách: výdřeva končí, skála puká do přirozené jeskyně, zevnitř žhne ──
    def crack(x, y, n, seed, glow=True):
        r = np.random.default_rng(seed)
        for i in range(n):
            c.px(x, y, OUT)
            if glow and i % 2 == 0: c.px(x + 1, y, (190, 70, 30))
            x += int(r.integers(-1, 2)); y += 1 if r.random() < 0.8 else 0
            if glow: c.lights.append((x, y, 6, 0.08, (255, 110, 40)))

    for (x, y, n, s) in ((22, 330, 16, 1), (132, 318, 14, 2), (60, 300, 10, 3), (96, 394, 9, 4), (20, 420, 12, 5),
                         (134, 424, 10, 6), (56, 190, 10, 7), (120, 186, 12, 8)):
        crack(x, y, n, s)

    # krystaly: v puklině fialové a rudé – náznak, že Doly sahají k Rudému krystalu
    for (x, y) in ((122, 326), (132, 334), (110, 324), (128, 350)):
        c.crystal_cluster(x, y, CRY_V, 3)
        c.lights.append((x, y - 3, 16, 0.34, CRY_V[2]))
    for (x, y) in ((22, 340), (14, 360), (46, 336)):
        c.crystal_cluster(x, y, CRY_R, 2)
        c.lights.append((x, y - 3, 14, 0.28, CRY_R[2]))
    for (x, y) in ((126, 198), (18, 270)):
        c.crystal_cluster(x, y, CRY_R, 2)
        c.lights.append((x, y - 3, 12, 0.22, CRY_R[2]))
    for (x, y, h) in ((136, 356, 5), (104, 372, 4), (18, 376, 5), (132, 250, 5), (18, 230, 4), (100, 150, 4)):
        c.stalagmite(x, y, h)
    for (x, y, r) in ((50, 390, 3), (132, 470, 3)):
        c.boulder(x, y, r)

    # místa setkání: trsy žhnoucího mechu (jeskynní kapradiny v barvě žáru)
    fern = G.FERN
    G.FERN = EMBER_FERN
    for n in ("rumpal", "puklina", "popel"):
        c.encounter_patch(*NODES[n])
    G.FERN = fern

    # ── lávový sál ──
    # puklina ve skále, ze které vodopád padá: rozeklaná štěrbina se žhnoucím okrajem
    r = np.random.default_rng(5)
    cx = (FALL[0] + FALL[1]) / 2
    for y in range(FALL[2] - 10, FALL[2] + 1):
        half = 2 + (y - FALL[2] + 10) * 0.55 + r.uniform(-0.8, 0.8)
        for x in range(int(cx - half - 2), int(cx + half + 3)):
            d = abs(x + 0.5 - cx)
            if d < half - 1: c.px(x, y, (255, 150, 50) if y > FALL[2] - 3 else (40, 8, 4))
            elif d < half + 0.5: c.px(x, y, (200, 70, 24))
            elif d < half + 1.8: c.px(x, y, OUT)
    # žár prosvítá puklinami ve skále kolem vodopádu
    for (x, y, n, sd) in ((22, 150, 8, 11), (44, 146, 9, 12), (18, 176, 7, 13)):
        crack(x, y, n, sd)
    # most přes řeku: prkna, kolej a zábradlí z lana
    for y in range(RIVER[2] - 1, RIVER[3] + 2):
        for x in range(BRIDGE[0], BRIDGE[1] + 1):
            edge = x in (BRIDGE[0], BRIDGE[1])
            c.px(x, y, OUT if edge else (G.WOOD[2] if y % 3 else G.WOOD[0]))
        c.px(71, y, RAIL); c.px(79, y, RAIL)
    for x in (BRIDGE[0] - 1, BRIDGE[1] + 1):
        for y in (RIVER[2] - 3, RIVER[3] + 1):
            c.px(x, y, G.WOOD[0]); c.px(x, y + 1, G.WOOD[0])
    # ožehnuté okraje u lávy (zčernalá podlaha)
    near = np.zeros((AH, AW), bool)
    for r in (1, 2):
        sh = np.zeros_like(lava)
        sh[r:, :] |= lava[:-r, :]; sh[:-r, :] |= lava[r:, :]; sh[:, r:] |= lava[:, :-r]; sh[:, :-r] |= lava[:, r:]
        near |= sh
    for y, x in zip(*np.nonzero(near & ~lava & floor)):
        if BRIDGE[0] <= x <= BRIDGE[1] and RIVER[2] - 1 <= y <= RIVER[3] + 1: continue
        c.img[y, x] = np.array(c.img[y, x]) * 0.55 + np.array((70, 24, 12)) * 0.45

    # železné dveře v čele komory
    dx0, dx1 = 68, 82
    t = face_top(75, 130)
    if t is not None:
        for y in range(t, t + WALL_FH):
            for x in range(dx0, dx1 + 1):
                e = x in (dx0, dx1) or y == t
                col = STEEL[0] if e else (STEEL[2] if y == t + 1 else STEEL[1])
                if not e and (x * 5 + y * 3) % 7 == 0: col = RUSTC[0]
                c.px(x, y, col)
            c.px(dx0 + 2, y, STEEL[0])                      # svislý šev
        for x in (dx0 + 1, dx1 - 1):
            for y in (t + 2, t + WALL_FH - 2): c.px(x, y, STEEL[3])   # nýty
        for x in range(72, 79): c.px(x, t + 3, OUT)         # zavařený průzor
        for x in range(72, 79): c.px(x, t + 4, STEEL[3])

    # podlaha lávového sálu je ožehlá do rezava, puklina napůl (teplo stoupá z hloubky)
    warm = np.array([1.08, 0.86, 0.74])
    for y, x in zip(*np.nonzero(c.is_floor())):
        k = 1.0 if c.H[y, x] == 2 else (0.5 if c.H[y, x] == 1 else 0.0)
        if k: c.img[y, x] = np.array(c.img[y, x]) * (1 + (warm - 1) * k)
    # světla
    c.lights.append((FALL[0] + 4, (FALL[2] + FALL[3]) // 2, 32, 0.7, LAVA[4]))
    c.lights.append((int(POOL[0]), int(POOL[1]), 32, 0.75, LAVA[4]))
    for x in range(RIVER[0], RIVER[1] + 1, 12):
        c.lights.append((x, (RIVER[2] + RIVER[3]) // 2, 26, 0.55, LAVA[3]))
    c.lights.append((75, 300, 40, 0.18, LAVA[3]))                # rudá záře ze sálu dolů na rampu
    c.lights.append((75, AH + 4, 30, 0.5, COOL))                 # chladné světlo ze Starého dolu
    c.lighting(ambient=0.48)

    # láva až po osvětlení – svítí sama
    for y in range(AH):
        for x in range(AW):
            if lava[y, x] or (FALL[0] <= x <= FALL[1] and FALL[2] <= y <= FALL[3]):
                c.img[y, x] = lava_color(x, y)

    # ── výstupy ──
    c.save()
    walk = c.is_floor() & ~lava
    for (x0, y0, x1, y1) in c.blocks:
        walk[max(0, y0):max(0, y1), max(0, x0):max(0, x1)] = False
    for (x0, y0, x1, y1) in stairs_walk:
        walk[y0:y1, x0:x1] = True
    # most je průchozí
    walk[RIVER[2] - 1:RIVER[3] + 2, BRIDGE[0] + 1:BRIDGE[1]] = True

    gw, gh = math.ceil(AW / CELL), math.ceil(AH / CELL)
    rows = []
    for gy in range(gh):
        row = ""
        for gx in range(gw):
            cell = walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL]
            row += "." if cell.mean() >= 0.5 else "#"
        rows.append(row)
    for n, (x, y) in NODES.items():
        r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
    os.makedirs(WALK, exist_ok=True)
    with open(os.path.join(WALK, "mines.txt"), "w") as f:
        f.write(f"# mapa chůze mines: obrázek {AW}x{AH}, buňka {CELL} px (tools/mapgen/gen_mines.py)\n")
        f.write(f"{AW} {AH} {CELL}\n")
        f.write("\n".join(rows) + "\n")

    # náhled s mapou chůze a hejny mušek
    dbg = Image.open(os.path.join(HERE, "mines_debug.png")).convert("RGBA")
    ov = Image.new("RGBA", dbg.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    for gy, row in enumerate(rows):
        for gx, ch in enumerate(row):
            if ch == ".":
                d.rectangle([gx * CELL * 4, gy * CELL * 4, (gx + 1) * CELL * 4 - 1, (gy + 1) * CELL * 4 - 1], fill=(0, 255, 0, 36))
    for k, (x, y) in SWARMS.items():
        d.ellipse([x * 4 - 30, y * 4 - 20, x * 4 + 30, y * 4 + 20], outline=(255, 255, 0, 220), width=2)
    Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "mines_debug.png"))
    print("ok", AW, AH)


if __name__ == "__main__":
    main()
