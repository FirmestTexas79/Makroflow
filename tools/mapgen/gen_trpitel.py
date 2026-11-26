"""
Použití:  python tools/mapgen/gen_trpitel.py
Výstup:   app/src/main/res/drawable-nodpi/trpitel_sheet.png (pás FRAMES snímků vedle sebe, 1 px = 1 px mapy),
          tools/mapgen/trpitel_preview.png

TRPITEL (Kazimír) – bůh ve Stromu, NPC na mapě Kmene (gen_trunk.py, RootTrunk.kt). Obr s tělem kulturisty barvy
popela, kůže popraskaná jako vyschlé bahno (víc na pravé straně, kudy prochází bolest), hladká hlava bez tváře se
šesti svítícími očima ve dvou sloupcích. Šest paží: horní pár roztažený vzhůru, zápěstí v ocelových objímkách Řádu
přišroubovaných do kůry; prostřední pár rozpažený s otevřenými dlaněmi; dolní pár svírá ratiště kopí, které mu
prochází hrudí a trčí z rány ven. Od stehen ho pohltila pěna (Operace Pečeť). Snímky = jeden nádech a výdech:
hrudník se zvedne a rozšíří, rozpažené ruce se zvednou, dolní ruce zatáhnou za ratiště, oči pulzují a jeden pár
mrkne. Světlo: slabě shora, zespodu sodíkové světlomety, zezadu studená záře z rány (obrys).
"""
import math, os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
W, H = 160, 232
CX = 80
FRAMES = 10
# kde sprite leží v mapě Kmene (levý horní roh, art px) – musí sedět s RootTrunk.kt a gen_trunk.py
MAP_X0, MAP_Y0 = 8, 26
SPEAR_IN, SPEAR_OUT = (CX - 4, 68), (CX + 46, 226)
CLAMP_T = 0.62
CLAMP = (SPEAR_IN[0] + (SPEAR_OUT[0] - SPEAR_IN[0]) * CLAMP_T, SPEAR_IN[1] + (SPEAR_OUT[1] - SPEAR_IN[1]) * CLAMP_T)

SKIN = np.array([(18, 16, 18), (32, 29, 31), (50, 46, 47), (72, 67, 67), (96, 90, 88), (122, 116, 112), (150, 144, 138)], float)
FOAM = np.array([(48, 44, 32), (84, 78, 56), (120, 112, 82), (154, 144, 106), (186, 176, 134), (212, 204, 164)], float) * 0.62
WARM, CYAN = np.array((255, 168, 90.0)), np.array((120, 236, 224.0))
LIGHT = np.array((-0.45, -0.55, 0.7)); LIGHT /= np.linalg.norm(LIGHT)
UNDER = np.array((0.1, 0.85, 0.5)); UNDER /= np.linalg.norm(UNDER)


def h32(*a):
    v = 2166136261
    for x in a:
        v = ((v ^ (int(x) & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
    return v


def ramp(R, v, x, y):
    v = max(0.0, min(0.999, v)) * (len(R) - 1)
    i = int(v)
    return R[i + 1] if i + 1 < len(R) and (v - i) > ((x * 3 + y * 5) % 7) / 7 * 0.8 + 0.1 else R[i]


rs = np.random.default_rng(6)
SEEDS = np.column_stack([rs.uniform(0, W, 420), rs.uniform(0, H, 420)])


def crack(x, y):
    """Síť prasklin (Voronoi hrany); víc vpravo a na hrudi, hlava hladká."""
    d = np.hypot(SEEDS[:, 0] - x, (SEEDS[:, 1] - y) * 1.1)
    a, b = np.partition(d, 1)[:2]
    edge = (b - a) < 0.85
    density = 0.45 + 0.55 * max(0.0, min(1.0, (x - CX + 24) / 44))
    return edge and (h32(int(a * 3), int(b * 3), 7) % 100) / 100 < density


class Frame:
    """Výšková mapa těla: každý sval je elipsoid, z = nejvyšší povrch. Normály z gradientu → svaly do sebe plynule
    přecházejí (údolí mezi nimi jsou tmavší), překryv (ruka před trupem) dá tmavou spáru."""
    def __init__(self):
        self.z = np.full((H, W), -1e9); self.pid = np.full((H, W), -1)
        self.base = np.zeros((H, W)); self.n = 0
        self.rgb = np.zeros((H, W, 3)); self.a = np.zeros((H, W), bool); self.skin = np.zeros((H, W), bool)

    def _put(self, x, y, z, pid, base):
        if z > self.z[y, x]:
            self.z[y, x] = z; self.pid[y, x] = pid; self.base[y, x] = base

    def blob(self, cx, cy, rx, ry, base=0.0, z0=0.0, rz=None):
        pid = self.n; self.n += 1
        rz = rz if rz is not None else min(rx, ry) * 0.9
        for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
            for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
                if 0 <= x < W and 0 <= y < H:
                    d = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
                    if d <= 1:
                        self._put(x, y, z0 + rz * math.sqrt(1 - d), pid, base)
        return pid

    def capsule(self, pts, radii, base=0.0, z0=0.0):
        seg = []
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            k = max(1, int(math.hypot(x1 - x0, y1 - y0) * 1.5))
            seg += [(x0 + (x1 - x0) * i / k, y0 + (y1 - y0) * i / k) for i in range(k)]
        seg.append(pts[-1])
        pid = self.n; self.n += 1
        for i, (cx, cy) in enumerate(seg):
            r = np.interp(i / max(1, len(seg) - 1), np.linspace(0, 1, len(radii)), radii)
            for y in range(int(cy - r) - 1, int(cy + r) + 2):
                for x in range(int(cx - r) - 1, int(cx + r) + 2):
                    if 0 <= x < W and 0 <= y < H:
                        d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) / (r * r)
                        if d <= 1:
                            self._put(x, y, z0 + r * 0.9 * math.sqrt(1 - d), pid, base)
        return pid

    def bake(self):
        """Stínování z výškové mapy: světlo shora zleva, zespodu sodík, zezadu studený obrys."""
        m = self.z > -1e8
        z = np.where(m, self.z, np.nan)
        zp = np.pad(z, 1, mode="edge")
        gx = (zp[1:-1, 2:] - zp[1:-1, :-2]) / 2; gy = (zp[2:, 1:-1] - zp[:-2, 1:-1]) / 2
        gx = np.nan_to_num(gx, nan=0.0); gy = np.nan_to_num(gy, nan=0.0)
        gx = np.clip(gx, -3, 3); gy = np.clip(gy, -3, 3)
        nl = np.sqrt(gx * gx + gy * gy + 1)
        nx, ny, nz = -gx / nl, -gy / nl, 1 / nl
        edge = np.zeros_like(m)
        edge[1:, :] |= ~m[:-1, :]; edge[:-1, :] |= ~m[1:, :]; edge[:, 1:] |= ~m[:, :-1]; edge[:, :-1] |= ~m[:, 1:]
        edge &= m
        for y, x in zip(*np.nonzero(m)):
            n = np.array((nx[y, x], ny[y, x], nz[y, x]))
            dif = max(0.0, n @ LIGHT); und = max(0.0, n @ UNDER)
            col = ramp(SKIN, 0.1 + dif * 0.7 + self.base[y, x], x, y).copy()
            col += WARM * und * 0.12
            rim = 0.45 if edge[y, x] else (1 - nz[y, x]) ** 4 * 0.3
            col = col * (1 - rim) + CYAN * rim * 0.75 + col * rim * 0.25
            self.rgb[y, x] = np.clip(col * (0.86 if y > 120 else 0.94), 0, 255)
            self.a[y, x] = True; self.skin[y, x] = True
        rgb0 = self.rgb.copy()                                            # spára tam, kde něco leží před něčím
        for y in range(1, H - 1):
            for x in range(1, W - 1):
                if m[y, x]:
                    for dy, dx in ((-1, 0), (0, -1), (1, 0), (0, 1)):
                        if m[y + dy, x + dx] and self.z[y + dy, x + dx] - self.z[y, x] > 3.0:
                            self.rgb[y, x] = rgb0[y, x] * 0.45
                            break

    def px(self, x, y, col, a=1.0):
        X, Y = int(round(x)), int(round(y))
        if 0 <= X < W and 0 <= Y < H:
            self.rgb[Y, X] = self.rgb[Y, X] * (1 - a) + np.asarray(col, float) * a if self.a[Y, X] else np.asarray(col, float)
            self.a[Y, X] = True


def arm(f, sh, el, wr, big=1.0, z0=0.0):
    f.capsule([sh, el], [7.4 * big, 8.2 * big, 6.6 * big, 5.4 * big], z0=z0)   # rameno a biceps
    f.capsule([el, wr], [6.0 * big, 5.8 * big, 4.6 * big, 3.6 * big], z0=z0 + 0.5)   # předloktí


def palm(f, wx, wy, side, lift, z0):
    """Otevřená dlaň s roztaženými prsty (rozpažená ruka)."""
    f.blob(wx + side * 4, wy, 5.6, 4.4, z0=z0)
    for k, a in enumerate((-0.55, -0.18, 0.18, 0.5)):
        ang = a - 0.3 * lift
        ln = 7 - (k in (0, 3)) * 2
        x1 = wx + side * (8 + math.cos(ang) * ln); y1 = wy + math.sin(ang) * ln * 1.1
        f.capsule([(wx + side * 8, wy + (k - 1.5) * 1.6), (x1, y1 + (k - 1.5) * 1.2)], [1.5, 1.2], z0=z0)
    f.capsule([(wx + side * 3, wy - 3), (wx + side * 7, wy - 7)], [1.7, 1.3], z0=z0)   # palec


def fist(f, x, y, z0):
    f.blob(x, y, 5.4, 4.8, z0=z0, base=0.04)
    f.knuckles = getattr(f, "knuckles", []) + [(x, y)]


def cuff(f, x, y, z0):
    f.blob(x, y + 2, 5.0, 5.0, z0=z0)                                     # ruka v objímce
    for k in range(4):
        f.capsule([(x - 3.5 + k * 2.3, y - 1), (x - 4 + k * 2.6, y - 6)], [1.3, 1.1], z0=z0)   # prsty zaťaté do kůry
    f.cuffs = getattr(f, "cuffs", []) + [(x, y)]


def draw_cuffs(f):
    for (x, y) in getattr(f, "cuffs", []):
        for yy in range(int(y) + 3, int(y) + 9):
            for xx in range(int(x) - 6, int(x) + 7):
                v = 0.85 - (yy - y - 3) * 0.1
                f.px(xx, yy, (50 + v * 120, 54 + v * 120, 60 + v * 120) if yy < y + 8 else (20, 20, 24))
        for (bx, by) in ((x - 5, y + 4), (x + 5, y + 4), (x - 5, y + 7), (x + 5, y + 7)):
            f.px(bx, by, (24, 24, 28))
    for (x, y) in getattr(f, "knuckles", []):
        for k in range(4):
            f.px(x - 3 + k * 2, y - 3, SKIN[5] * 0.9 + CYAN * 0.1)


def render(fi):
    t = fi / FRAMES
    b = (1 - math.cos(2 * math.pi * t)) / 2                               # 0 = výdech, 1 = nádech
    up = -round(b * 1.6)                                                  # hrudník a ramena se zvednou
    f = Frame()
    # ── zadní vrstva: horní a prostřední paže (za trupem) ──
    for side in (-1, 1):
        sh = (CX + side * 26, 50 + up)
        el = (CX + side * (48 + b * 1.5), 30 + up * 0.5)
        wr = (CX + side * 62, 13)
        arm(f, sh, el, wr, big=1.0, z0=-8)
        cuff(f, wr[0], wr[1] - 4, z0=-8)
        sh2 = (CX + side * 26, 64 + up)
        el2 = (CX + side * 48, 72 - b * 2)
        wr2 = (CX + side * 64, 76 - b * 4.5)
        arm(f, sh2, el2, wr2, big=0.95, z0=-6)
        palm(f, wr2[0], wr2[1], side, b, z0=-6)
    # ── trup kulturisty ──
    f.blob(CX, 142, 22, 26, z0=-2, base=-0.06)                           # pánev
    for side in (-1, 1):
        f.blob(CX + side * 11, 154, 12, 24, z0=0, base=-0.04)             # stehna
        f.blob(CX + side * 20, 82 + up * 0.5, 10, 20, z0=-2, base=-0.04)  # široké zádové svaly (do V)
        f.blob(CX + side * 14, 116, 6, 12, z0=0, base=-0.03)              # šikmé svaly
    f.blob(CX, 96, 18, 34, z0=0, rz=10)                                   # hrudní koš a břicho
    for row, yy in enumerate((86, 98, 110)):                              # břišní svaly 3 × 2
        for side in (-1, 1):
            f.blob(CX + side * 5.4, yy - row * 0.4, 5.2, 5.6, z0=6, base=0.02 - row * 0.03, rz=4)
    for side in (-1, 1):
        f.blob(CX + side * 12.5, 64 + up, 13.5 + b * 0.9, 9.5 + b * 0.7, z0=6, base=0.06, rz=7)   # prsní svaly
        f.blob(CX + side * 29, 56 + up, 11.5, 12, z0=2, base=0.05, rz=9)  # deltové svaly
        f.blob(CX + side * 14, 46 + up, 13, 7, z0=0, base=0.0, rz=6)      # trapézy
    f.blob(CX, 56 + up, 22, 9, z0=2, base=0.02, rz=6)                     # horní hrudník mezi rameny
    f.blob(CX, 44 + up, 8, 9, z0=0, base=-0.04)                           # krk
    pid_head = f.blob(CX, 28 + up, 8.5, 11.5, z0=4, base=0.12, rz=8)      # hlava bez tváře
    # ── dolní pár paží svírá ratiště (před tělem) ──
    (x0, y0), (x1, y1) = SPEAR_IN, SPEAR_OUT
    pull = (1 - b) * 2.2                                                  # výdech: zatáhne za ratiště
    dirx, diry = (x1 - x0), (y1 - y0)
    Lsp = math.hypot(dirx, diry); dirx, diry = dirx / Lsp, diry / Lsp
    gl = (x0 + dirx * (36 + pull), y0 + diry * (36 + pull))
    gr = (x0 + dirx * (56 + pull), y0 + diry * (56 + pull))
    arm(f, (CX - 24, 76 + up), (CX - 38, 100), gl, big=0.95, z0=12)
    arm(f, (CX + 24, 78 + up), (CX + 40, 106), gr, big=0.95, z0=12)
    f.bake()
    # ── praskliny a světlé žilky na kůži ──
    shift = up
    for y in range(H):
        for x in range(W):
            if f.skin[y, x] and f.pid[y, x] != pid_head and crack(x, y - (shift if y < 92 else 0)):
                f.rgb[y, x] = f.rgb[y, x] * 0.3 + np.array((34, 14, 16)) * 0.7
    for (x0, y0, n) in ((CX - 14, 66, 50), (CX - 22, 80, 30), (CX - 6, 100, 40)):
        x, y = x0, y0 + up
        for i in range(n):
            x += math.sin(i * 0.35 + x0) * 0.6; y += 1
            if 0 <= int(y) < H and f.skin[int(y), int(x)]:
                f.rgb[int(y), int(x)] = f.rgb[int(y), int(x)] * 0.6 + np.array((196, 190, 182)) * 0.4
    # ── šest očí: dva sloupce po třech, pulzují; v jednom snímku spodní pár mrkne ──
    glow = 0.8 + 0.2 * math.sin(2 * math.pi * t + 1)
    for k, ey in enumerate((21, 27, 33)):
        for side in (-1, 1):
            ex = CX + side * 4.2
            shut = fi == 6 and k == 2
            for dx in (-2, -1, 0, 1):
                for dy in (0, 1):
                    edge_ = dx in (-2, 1)
                    if shut:
                        col = (26, 30, 32) if dy == 1 else (60, 66, 66)
                    elif edge_:
                        col = CYAN * 0.55 * glow + 10
                    else:
                        col = (226, 255, 250) if dy == 0 else CYAN * glow
                    f.px(ex + dx + 0.5, ey + up + dy, np.clip(col, 0, 255))
    # ── pěna pohltila stehna ──
    rf = np.random.default_rng(31)
    for _ in range(170):
        bx, by, r = rf.uniform(CX - 34, CX + 34), rf.uniform(150, 180) - (6 if rf.random() < 0.3 else 0), rf.uniform(2.5, 7)
        for y in range(int(by - r), int(by + r) + 1):
            for x in range(int(bx - r), int(bx + r) + 1):
                if 0 <= x < W and 0 <= y < H:
                    dd = ((x - bx) ** 2 + (y - by) ** 2) / (r * r)
                    if dd < 1 and (f.a[y, x] or y > 156) and ((x - CX) / 38) ** 2 + ((y - 156) / 28) ** 2 < 1:
                        f.rgb[y, x] = ramp(FOAM, 0.78 - dd * 0.42 - ((x - bx) - (y - by)) / r * 0.12, x, y)
                        f.a[y, x] = True; f.skin[y, x] = False; f.pid[y, x] = 950
    # ── kopí: rána v hrudi, ratiště k divákovi, objímka Řádu, ruce dolního páru ho svírají ──
    for y in range(y0 - 4, y0 + 5):
        for x in range(x0 - 5, x0 + 6):
            d = math.hypot(x - x0, (y - y0) * 1.2)
            if d < 4.6:
                f.px(x, y, (10, 8, 10) if d < 2.8 else (54, 24, 24))
    for k in range(int(y0 + 4), 140):                                     # studené světlo stéká z rány
        xx = x0 - 1 + math.sin(k * 0.3) * 0.8
        if f.skin[k, int(xx)]:
            f.px(xx, k, (170, 246, 232), 0.85 if (k + fi) % 4 else 0.5)
    n = 160
    for i in range(n):
        tt = i / n
        x, y = x0 + (x1 - x0) * tt, y0 + (y1 - y0) * tt
        r = 1.8 + tt * 2.2
        for dx in np.arange(-r, r + 0.5, 0.5):
            u = dx / r
            v = 0.55 - u * 0.4
            col = np.array((30, 22, 18)) * (1 - v) + np.array((128, 100, 72)) * v
            band = int(tt * 44)
            if band % 7 == 0:
                col = np.array((58, 60, 66)) * (1 - v) + np.array((176, 182, 188)) * v   # železný okov
            elif int(tt * 90) % 9 < 2:
                col = np.array((66, 54, 38)) * (1 - v) + np.array((156, 132, 92)) * v   # omotávka
            f.px(x + dx * 0.9, y - dx * 0.3, col)
    cx_, cy_ = CLAMP
    for y in range(int(cy_) - 4, int(cy_) + 5):                           # objímka Řádu
        for x in range(int(cx_) - 8, int(cx_) + 9):
            v = 0.85 - (y - cy_ + 4) / 8 * 0.6
            f.px(x, y, (40 + v * 140, 44 + v * 140, 50 + v * 140) if abs(y - cy_) < 4 else (24, 24, 28))
    for (bx, by) in ((cx_ - 6, cy_), (cx_ + 6, cy_)):
        f.px(bx, by, (220, 224, 228))
    for y in range(int(y1) - 1, int(y1) + 5):                             # pata ratiště
        for x in range(int(x1) - 4, int(x1) + 5):
            if abs(x - x1) + abs(y - y1 - 1) * 1.4 < 5:
                f.px(x, y, (60, 62, 68) if y > y1 else (150, 156, 162))
    g = Frame()                                                           # pěsti přes ratiště
    fist(g, *gl, z0=0); fist(g, *gr, z0=0)
    g.bake()
    for y, x in zip(*np.nonzero(g.a)):
        f.rgb[y, x] = g.rgb[y, x]; f.a[y, x] = True
    draw_cuffs(f); draw_cuffs(g)
    for (kx, ky) in g.knuckles:
        for k in range(4):
            f.px(kx - 3 + k * 2, ky - 3, SKIN[5])
    # ── tmavý obrys siluety ──
    out = np.zeros((H, W, 4), np.uint8)
    out[f.a, :3] = np.clip(f.rgb[f.a], 0, 255); out[f.a, 3] = 255
    a = f.a
    edge = np.zeros_like(a)
    edge[1:, :] |= a[:-1, :]; edge[:-1, :] |= a[1:, :]; edge[:, 1:] |= a[:, :-1]; edge[:, :-1] |= a[:, 1:]
    edge &= ~a
    out[edge] = (14, 10, 12, 255)
    return out


if __name__ == "__main__":
    frames = [render(i) for i in range(FRAMES)]
    sheet = np.concatenate(frames, axis=1)
    Image.fromarray(sheet, "RGBA").save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", "trpitel_sheet.png"))
    big = Image.fromarray(frames[5], "RGBA").resize((W * 3, H * 3), Image.NEAREST)
    prev = Image.new("RGBA", (W * 3, H * 3), (20, 22, 26, 255)); prev.alpha_composite(big)
    prev.save(os.path.join(HERE, "trpitel_preview.png"))
    print("trpitel", FRAMES, "snímků", W, "x", H, "clamp", tuple(round(v) for v in CLAMP))
