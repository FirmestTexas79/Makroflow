"""
Použití:  python tools/mapgen/gen_caves.py   (vyžaduje Pillow + numpy)
Výstup:   app/src/main/res/drawable-nodpi/cave_open.png, cave_maze.png   (1 px = 1 art pixel)
          tools/mapgen/cave_open_debug.png, cave_maze_debug.png          (4x, s uzly)

Dvě jeskyně v Horách, tmavě modrý kámen s mechem:
  * cave_open  – Mechová jeskyně: otevřená síň ve třech výškových patrech se schody,
                 jezírkem a svítícími houbami. Na nejvyšším patře oltář s MODRÝM krystalem.
  * cave_maze  – Starý důl: uzavřené bludiště chodeb, patra propojená žebříky, výdřeva,
                 lucerny, koleje a rudná žíla (sken čárového kódu). Na konci ČERVENÝ krystal.

Mapy jsou větší než obrazovka – v aplikaci po nich jezdí kamera (docs/adr/0013).
PNG se ukládá v nativním rozlišení do drawable-nodpi (žádné přepočty hustoty, ~100 kB RAM)
a zvětšuje se až v aplikaci celočíselným násobkem bez vyhlazení.

Souřadnice uzlů (NODES) jsou ZDROJ PRAVDY i pro BiomeRegistry.CAVE_OPEN_GRAPH / CAVE_MAZE_GRAPH
(zlomky šířky/výšky). Při změně upravit obojí – test CaveGraphTest hlídá propojení grafu.
"""
import math, os
import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi")

WALL = 9                       # „výška“ skalního masivu
WALL_FH, STEP_FH = 9, 6        # výška čela: stěna / jeden schod patra
ALTAR_ABOVE = 16               # oltář je 16 art px nad uzlem krystalu (CaveMap.ALTAR_ABOVE v Kotlinu)
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5

# ── Paleta: tmavě modrý kámen, mech, voda, světla ────────────────────────────────
WALL_TOP = [(16, 22, 40), (21, 29, 50), (27, 36, 61)]
ROCK = [(14, 19, 34), (22, 30, 52), (31, 41, 70), (42, 55, 92), (56, 72, 116)]   # hroudy masivu
FACE = [(50, 64, 104), (41, 53, 89), (33, 43, 73), (25, 33, 57)]
FLOOR = {0: [(46, 58, 90), (52, 66, 100), (58, 74, 110)],
         1: [(56, 70, 106), (63, 79, 117), (71, 88, 129)],
         2: [(66, 82, 120), (75, 93, 133), (85, 105, 147)]}
OUT = (9, 12, 24); RIM = (98, 120, 170)
MOSS = [(30, 72, 62), (42, 98, 76), (62, 128, 88), (104, 170, 104)]
WATER = [(24, 56, 104), (34, 84, 144), (70, 150, 196), (170, 226, 244)]
FERN = [(34, 110, 84), (70, 176, 122), (170, 250, 200)]    # svítící kapradiny = místa setkání
CAP = [(40, 150, 164), (86, 216, 206), (190, 250, 240)]; STEM = (176, 186, 204)
CRY_B = [(34, 70, 160), (60, 130, 230), (150, 210, 255), (240, 250, 255)]
CRY_R = [(120, 24, 40), (200, 50, 60), (255, 130, 120), (255, 230, 220)]
STONE = [(88, 96, 122), (112, 120, 146), (138, 146, 170), (62, 68, 92)]
WOOD = [(112, 76, 46), (84, 56, 34), (140, 98, 60)]
RAIL = (118, 126, 150); LANTERN = (255, 206, 120); ORE = [(220, 170, 70), (250, 220, 120)]
WARM = (232, 196, 140)


def value_noise(w, h, sc, seed):
    rng = np.random.default_rng(seed)
    g = rng.random((h // sc + 3, w // sc + 3))
    ys, xs = np.mgrid[0:h, 0:w].astype(float)
    xs /= sc; ys /= sc
    xi, yi = xs.astype(int), ys.astype(int)
    fx, fy = xs - xi, ys - yi
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = g[yi, xi], g[yi, xi + 1]
    c, d = g[yi + 1, xi], g[yi + 1, xi + 1]
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy


class Cave:
    def __init__(self, name, aw, ah, nodes, edges, levels, seed):
        self.name, self.AW, self.AH = name, aw, ah
        self.nodes, self.edges, self.lvl = nodes, edges, levels     # nodes: id -> (x, y) v art px
        self.rng = np.random.default_rng(seed)
        self.seed = seed
        self.H = np.full((ah, aw), WALL, dtype=int)
        self.water = np.zeros((ah, aw), bool)
        self.img = np.zeros((ah, aw, 3), dtype=float)
        self.lights = []            # (x, y, poloměr, síla, barva)
        self.props = []             # funkce kreslící rekvizity až po osvětlení (svítí samy)
        self.blocks = []            # obdélníky rekvizit, přes které se nechodí (mapa chůze)
        self.ys, self.xs = np.mgrid[0:ah, 0:aw]

    # ── tvary ──
    def blob(self, level, cx, cy, rx, ry, rough=0.18, k=0):
        r = np.random.default_rng(self.seed * 31 + k)
        ph = r.uniform(0, 6.28, 3)
        ang = np.arctan2((self.ys - cy) / ry, (self.xs - cx) / rx)
        rr = 1 + rough * (0.5 * np.sin(3 * ang + ph[0]) + 0.3 * np.sin(5 * ang + ph[1]) + 0.2 * np.sin(9 * ang + ph[2]))
        m = ((self.xs - cx) / rx) ** 2 + ((self.ys - cy) / ry) ** 2 < rr * rr
        self.H[m] = level
        return m

    def rect(self, level, x0, y0, x1, y1):
        self.H[max(0, y0):min(self.AH, y1), max(0, x0):min(self.AW, x1)] = level

    def capsule(self, level, a, b, r):
        (ax, ay), (bx, by) = a, b
        dx, dy = bx - ax, by - ay; L = dx * dx + dy * dy or 1
        t = np.clip(((self.xs - ax) * dx + (self.ys - ay) * dy) / L, 0, 1)
        d = np.hypot(self.xs - ax - t * dx, self.ys - ay - t * dy)
        self.H[d < r] = level

    def carve_nodes(self, r=11):
        for n, (x, y) in self.nodes.items():
            self.capsule(self.lvl[n], (x, y), (x, y), r)
        for a, b in self.edges:
            if self.lvl[a] == self.lvl[b]:
                self.capsule(self.lvl[a], self.nodes[a], self.nodes[b], 9)

    # ── čela stěn a teras (pohled 3/4 shora) ──
    def faces(self):
        AW, AH, H = self.AW, self.AH, self.H
        self.face = np.full((AH, AW), -1); self.faceH = np.zeros((AH, AW), int); self.faceUp = np.zeros((AH, AW), int)
        for x in range(AW):
            for y in range(AH - 1):
                hu, hl = H[y, x], H[y + 1, x]
                if hu <= hl: continue
                fh = WALL_FH if hu == WALL else STEP_FH * (hu - hl)
                for k in range(fh):
                    yy = y + 1 + k
                    if yy >= AH or H[yy, x] >= hu: break
                    self.face[yy, x] = k; self.faceH[yy, x] = fh; self.faceUp[yy, x] = hu

    def is_floor(self):
        return (self.H < WALL) & (self.face < 0)

    # ── základní textury ──
    def paint_base(self):
        AW, AH = self.AW, self.AH
        n1 = value_noise(AW, AH, 6, self.seed + 1); n2 = value_noise(AW, AH, 17, self.seed + 2)
        dither = BAYER[self.ys % 4, self.xs % 4]
        v = np.clip(((n1 * 0.4 + n2 * 0.6) * 3 + dither * 0.9).astype(int), 0, 2)
        img = self.img
        for y in range(AH):
            for x in range(AW):
                h = self.H[y, x]; k = self.face[y, x]
                if k >= 0:
                    fh = self.faceH[y, x]
                    band = 0 if k < fh * 0.25 else (1 if k < fh * 0.55 else (2 if k < fh * 0.8 else 3))
                    if n1[y, x * 3 % AW] > 0.66: band = min(3, band + 1)
                    if (x + y // 4) % 9 == 0: band = min(3, band + 1)          # svislé praskliny
                    img[y, x] = FACE[band]
                elif h == WALL:
                    img[y, x] = WALL_TOP[v[y, x]]            # přepíše rock_mass()
                else:
                    img[y, x] = FLOOR[h][v[y, x]]
                    if n2[y, x] > 0.72 and (x * 7 + y * 3) % 11 == 0: img[y, x] = FLOOR[h][0]  # oblázky

    def rock_mass(self, cell=9):
        """Skalní masiv jako shluk hrud (Voronoi): světlá hrana vlevo nahoře, stín vpravo dole."""
        AW, AH = self.AW, self.AH
        r = np.random.default_rng(self.seed + 40)
        pts = []
        for gy in range(-1, AH // cell + 2):
            for gx in range(-1, AW // cell + 2):
                pts.append((gx * cell + r.uniform(0, cell), gy * cell + r.uniform(0, cell), r.uniform(0.8, 1.25)))
        pts = np.array(pts)
        wall = (self.H == WALL) & (self.face < 0)
        for y, x in zip(*np.nonzero(wall)):
            gx, gy = int(x // cell), int(y // cell)
            best, second, bi = 1e9, 1e9, 0
            for oy in (-1, 0, 1, 2):
                for ox in (-1, 0, 1, 2):
                    i = (gy + oy) * (AW // cell + 3) + (gx + ox)
                    if 0 <= i < len(pts):
                        px_, py_, w = pts[i]
                        d = math.hypot(x - px_, y - py_) / w
                        if d < best: second, best, bi = best, d, i
                        elif d < second: second = d
            px_, py_, _ = pts[bi]
            edge = second - best
            if edge < 1.0: c = ROCK[0]                                   # spára mezi hroudami
            else:
                lx, ly = (x - px_), (y - py_)
                shade = -(lx + ly) / cell                                # světlo zleva shora
                v = 2 + shade * 1.6 + BAYER[y % 4, x % 4] * 0.8
                c = ROCK[int(max(1, min(4, v)))]
                if edge < 2.0 and c != ROCK[1]: c = ROCK[1]
            self.img[y, x] = c

    def warm_exit(self, x0, x1, y0):
        """Denní světlo zvenku prosvítá do ústí (plynulý přechod, žádná šachovnice)."""
        for y in range(y0, self.AH):
            a = min(1.0, (y - y0) / (self.AH - y0)) * 0.6
            for x in range(x0, x1):
                if not self.is_floor()[y, x]: continue
                q = round((a + BAYER[y % 4, x % 4] * 0.2) * 4) / 4
                if q <= 0: continue
                self.img[y, x] = np.array(self.img[y, x]) * (1 - q) + np.array(WARM) * q

    def outline(self):
        AW, AH = self.AW, self.AH
        cls = np.where(self.face >= 0, 100 + self.faceUp, self.H)        # třídy povrchů
        for y in range(AH):
            for x in range(AW):
                c = cls[y, x]
                if c == WALL:
                    # hrana masivu proti podlaze (ne proti vlastnímu čelu)
                    for dx, dy in ((1, 0), (-1, 0), (0, -1)):
                        xx, yy = x + dx, y + dy
                        if 0 <= xx < AW and 0 <= yy < AH and cls[yy, xx] < WALL:
                            self.img[y, x] = OUT; break
                    else:
                        if y + 1 < AH and self.face[y + 1, x] >= 0: self.img[y, x] = RIM if (x // 2) % 5 else OUT
                elif c < WALL:
                    if y > 0 and self.face[y - 1, x] >= 0 and self.face[y - 1, x] == self.faceH[y - 1, x] - 1:
                        self.img[y, x] = OUT                          # pata čela = stín
                    elif any(0 <= x + dx < AW and cls[y, x + dx] == WALL for dx in (-1, 1)):
                        self.img[y, x] = OUT
                    elif any(0 <= x + dx < AW and self.H[y, x + dx] > c and self.face[y, x + dx] < 0 and self.H[y, x + dx] < WALL for dx in (-1, 1)):
                        self.img[y, x] = OUT                          # boční hrana terasy
                    elif y + 1 < AH and self.face[y + 1, x] >= 0 and self.faceUp[y + 1, x] == c:
                        self.img[y, x] = RIM                          # světlá hrana terasy nad čelem

    # ── mech ──
    def moss(self, amount=0.55):
        AW, AH = self.AW, self.AH
        n = value_noise(AW, AH, 9, self.seed + 5); n3 = value_noise(AW, AH, 3, self.seed + 6)
        near = np.zeros((AH, AW), float)                                # blízkost stěny
        wallish = (self.H == WALL) | (self.face >= 0)
        for r in range(1, 9):
            sh = np.zeros_like(wallish)
            sh[r:, :] |= wallish[:-r, :]; sh[:-r, :] |= wallish[r:, :]
            sh[:, r:] |= wallish[:, :-r]; sh[:, :-r] |= wallish[:, r:]
            near = np.maximum(near, sh * (1 - r / 9))
        score = n * 0.7 + near * 0.55 + n3 * 0.15
        floor = self.is_floor() & ~self.water
        dither = BAYER[self.ys % 4, self.xs % 4]
        m = floor & (score + dither * 0.12 > 1.02 - amount * 0.3)
        lvl = np.clip(((score - 0.8) * 5 + dither).astype(int), 0, 2)
        for y, x in zip(*np.nonzero(m)):
            self.img[y, x] = MOSS[lvl[y, x]]
        # mech visící z hran čel (pramínky)
        r = np.random.default_rng(self.seed + 7)
        for y in range(1, AH):
            for x in range(AW):
                if self.face[y, x] == 0 and r.random() < 0.42 * amount + 0.1:
                    L = int(r.integers(1, max(2, self.faceH[y, x] - 1)))
                    for k in range(L):
                        if y + k < AH and self.face[y + k, x] >= 0:
                            self.img[y + k, x] = MOSS[3] if k == L - 1 else MOSS[1 + (k % 2)]
                    if r.random() < 0.5 and y - 1 >= 0: self.img[y - 1, x] = MOSS[2]

    # ── světla ──
    def lighting(self, ambient=0.56):
        AW, AH = self.AW, self.AH
        b = np.full((AH, AW), ambient); tint = np.zeros((AH, AW, 3))
        for (lx, ly, rad, s, col) in self.lights:
            g = s * np.exp(-((self.xs - lx) ** 2 + ((self.ys - ly) * 1.15) ** 2) / (rad * rad))
            b += g
            tint += g[..., None] * np.array(col, float)[None, None, :] * 0.22
        dither = BAYER[self.ys % 4, self.xs % 4] * 0.12
        q = np.clip(np.round((b + dither) / 0.12) * 0.12, 0.4, 1.18)
        self.img = np.clip(self.img * q[..., None] + tint * (q[..., None] > 0.6), 0, 255)

    def px(self, x, y, c):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < self.AW and 0 <= y < self.AH: self.img[y, x] = c

    # ── rekvizity ──
    def boulder(self, cx, cy, r):
        self.blocks.append((cx - r - 1, cy - r - 1, cx + r + 2, cy + r + 2))
        for y in range(cy - r - 1, cy + r + 2):
            for x in range(cx - r - 1, cx + r + 2):
                d = math.hypot((x - cx) / 1.15, y - cy)
                if d < r:
                    s = (x - cx) + (y - cy)
                    self.px(x, y, STONE[2] if s < -r * 0.4 else (STONE[1] if s < r * 0.3 else STONE[3]))
                elif d < r + 1: self.px(x, y, OUT)
        for k in range(-r + 1, r): self.px(cx + k, cy + r + 1, OUT)

    def stalagmite(self, cx, cy, h):
        self.blocks.append((cx - 2, cy - h, cx + 3, cy + 2))
        for k in range(h):
            w = max(0, (k * 2) // h)
            for dx in range(-w, w + 1):
                self.px(cx + dx, cy - h + k, STONE[1] if dx < 0 else (STONE[0] if dx == 0 else STONE[3]))
        self.px(cx, cy - h, STONE[2])
        for dx in (-2, -1, 0, 1, 2): self.px(cx + dx, cy + 1, OUT)

    def mushroom(self, cx, cy, big=False):
        self.px(cx, cy, STEM); self.px(cx, cy - 1, STEM)
        w = 2 if big else 1
        for dx in range(-w, w + 1): self.px(cx + dx, cy - 2, CAP[1])
        for dx in range(-w + 1, w): self.px(cx + dx, cy - 3, CAP[2] if dx == 0 else CAP[1])
        self.px(cx - w - 1, cy - 2, CAP[0]); self.px(cx + w + 1, cy - 2, CAP[0])

    def crystal_cluster(self, cx, cy, pal, n=3):
        r = np.random.default_rng(cx * 97 + cy)
        for i in range(n):
            ox = int(r.integers(-4, 5)); h = int(r.integers(3, 8)); lean = int(r.integers(-1, 2))
            for k in range(h):
                x = cx + ox + (lean * k) // 3; y = cy - k
                self.px(x, y, pal[1]); self.px(x + 1, y, pal[0])
                if k == h - 1: self.px(x, y - 1, pal[3])
                elif k > h // 2: self.px(x, y, pal[2])

    def altar(self, cx, cy, pal):
        self.blocks.append((cx - 13, cy - 7, cx + 14, cy + 3))
        # kamenný oltář: kruhová deska s runami, krystal kreslí aplikace (dá se sebrat)
        for y in range(cy - 7, cy + 5):
            for x in range(cx - 13, cx + 14):
                d = math.hypot((x - cx) / 13, (y - cy + 1) / 6)
                if d < 0.82: self.px(x, y, STONE[2] if (x + y) % 5 else STONE[1])
                elif d < 1.0: self.px(x, y, STONE[0])
                elif d < 1.12: self.px(x, y, OUT)
        for a in range(0, 360, 45):                                   # svítící runy po obvodu
            self.px(cx + 10 * math.cos(math.radians(a)), cy - 1 + 4.6 * math.sin(math.radians(a)), pal[2])
        for dx in range(-4, 5):                                       # podstavec
            self.px(cx + dx, cy - 1, STONE[3]); self.px(cx + dx, cy - 2, STONE[0])

    def encounter_patch(self, cx, cy):
        """Místo setkání = jeskynní obdoba vysoké trávy: hustý trs svítících kapradin kolem uzlu."""
        r = np.random.default_rng(cx * 131 + cy)
        floor = self.is_floor()
        for _ in range(16):
            ang = r.uniform(0, 6.283); d = math.sqrt(r.uniform(0.05, 1.0))
            x = int(cx + math.cos(ang) * d * 13); y = int(cy - 2 + math.sin(ang) * d * 6)
            if not (0 <= x < self.AW and 0 <= y < self.AH and floor[y, x]): continue
            h = int(r.integers(3, 6))
            for k in range(h):                                   # vějíř listů
                for side in (-1, 0, 1):
                    xx = x + side * ((k + 1) // 2) if side else x
                    if side and k == 0: continue
                    col = FERN[2] if k == h - 1 else (FERN[1] if k > h // 2 else FERN[0])
                    self.px(xx, y - k, col)
            self.px(x, y + 1, OUT)
        self.lights.append((cx, cy - 3, 20, 0.42, FERN[2]))

    def ladder(self, x, y0, y1):
        for y in range(y0, y1 + 1):
            self.px(x - 4, y, WOOD[1]); self.px(x + 4, y, WOOD[1])
            self.px(x - 3, y, WOOD[0]); self.px(x + 3, y, WOOD[0])
            if (y - y0) % 3 == 0:
                for dx in range(-3, 4): self.px(x + dx, y, WOOD[2])
        self.px(x - 4, y0 - 1, OUT); self.px(x + 4, y0 - 1, OUT)

    def stairs(self, x, y0, y1, w=8):
        for y in range(y0, y1 + 1):
            step = (y - y0) % 3
            for dx in range(-w, w + 1):
                c = STONE[2] if step == 0 else (STONE[1] if step == 1 else STONE[3])
                if abs(dx) == w: c = OUT
                self.px(x + dx, y, c)

    def support(self, x, y, horizontal=True):
        """Dřevěná výztuž štoly: dva sloupky v čele a trám."""
        if horizontal:
            for k in range(WALL_FH):
                self.px(x - 5, y + k, WOOD[0]); self.px(x + 5, y + k, WOOD[0])
                self.px(x - 6, y + k, WOOD[1]); self.px(x + 6, y + k, WOOD[1])
            for dx in range(-7, 8): self.px(x + dx, y, WOOD[2]); self.px(x + dx, y + 1, WOOD[1])

    def lantern(self, x, y):
        self.px(x, y - 2, WOOD[1]); self.px(x, y - 1, OUT)
        self.px(x - 1, y, OUT); self.px(x + 1, y, OUT); self.px(x, y, LANTERN); self.px(x, y + 1, (255, 240, 190))
        self.px(x - 1, y + 1, OUT); self.px(x + 1, y + 1, OUT); self.px(x, y + 2, OUT)
        self.lights.append((x, y + 6, 22, 0.42, LANTERN))

    # ── výstup ──
    def save(self):
        os.makedirs(RES, exist_ok=True)
        im = Image.fromarray(self.img.astype(np.uint8), "RGB")
        im.save(os.path.join(RES, self.name + ".png"), optimize=True)
        dbg = im.resize((self.AW * 4, self.AH * 4), Image.NEAREST)
        d = ImageDraw.Draw(dbg)
        for a, b in self.edges:
            (ax, ay), (bx, by) = self.nodes[a], self.nodes[b]
            d.line([(ax * 4, ay * 4), (bx * 4, by * 4)], fill=(255, 80, 80), width=2)
        for n, (x, y) in self.nodes.items():
            d.ellipse([x * 4 - 7, y * 4 - 7, x * 4 + 7, y * 4 + 7], outline=(255, 40, 40), width=3)
            d.text((x * 4 + 10, y * 4 - 6), n, fill=(255, 255, 255))
        dbg.save(os.path.join(HERE, self.name + "_debug.png"))
        # mapa chůze (docs/adr/0037): jen skutečná podlaha – ne čela stěn a teras, voda ani rekvizity.
        # Dřív se hádalo z barev a světlé čelo stěny se trámy vypadalo jako podlaha.
        walk = self.is_floor() & ~self.water
        for (x0, y0, x1, y1) in self.blocks:
            walk[max(0, y0):max(0, y1), max(0, x0):max(0, x1)] = False
        Image.fromarray((walk * 255).astype(np.uint8), "L").save(os.path.join(HERE, self.name + "_walk.png"))
        print(self.name, im.size)
        for n, (x, y) in self.nodes.items():
            print(f'  "{n}": ({x / self.AW:.4f}, {y / self.AH:.4f})  lvl {self.lvl[n]}')

    def crossing(self, a, b, level_from, level_to):
        """Bod úsečky a→b, kde se mění patro (hranice podlahy vyššího patra)."""
        (ax, ay), (bx, by) = self.nodes[a], self.nodes[b]
        steps = int(math.hypot(bx - ax, by - ay))
        for i in range(steps + 1):
            t = i / steps
            x, y = ax + (bx - ax) * t, ay + (by - ay) * t
            if self.H[int(y), int(x)] == max(level_from, level_to) and self.face[int(y), int(x)] < 0:
                return int(x), int(y)
        return None


# ═══════════════════════════════════════════════════════════════════════════════
# 1) MECHOVÁ JESKYNĚ (otevřená, 3 patra, modrý krystal)
#    Šířka 150 art px = celá šířka se vejde na obrazovku, kamera jezdí hlavně svisle.
# ═══════════════════════════════════════════════════════════════════════════════
def gen_open():
    AW, AH = 150, 440
    nodes = {
        "vychod_jeskyne": (75, 428),
        "sal":            (75, 370),
        "jezirko":        (38, 346),
        "balvany_j":      (116, 352),
        "pata_schodu":    (75, 318),
        "terasa":         (75, 256),
        "houby":          (34, 230),
        "krystaly_j":     (116, 214),
        "pata_schodu2":   (82, 170),
        "krystal_modry":  (75, 86),
    }
    lvl = {"vychod_jeskyne": 0, "sal": 0, "jezirko": 0, "balvany_j": 0, "pata_schodu": 0,
           "terasa": 1, "houby": 1, "krystaly_j": 1, "pata_schodu2": 1, "krystal_modry": 2}
    edges = [("vychod_jeskyne", "sal"), ("sal", "jezirko"), ("sal", "balvany_j"), ("sal", "pata_schodu"),
             ("pata_schodu", "terasa"), ("terasa", "houby"), ("terasa", "krystaly_j"),
             ("terasa", "pata_schodu2"), ("pata_schodu2", "krystal_modry")]
    c = Cave("cave_open", AW, AH, nodes, edges, lvl, seed=11)

    c.blob(0, 75, 372, 70, 56, k=1)                   # hlavní síň
    c.blob(0, 24, 388, 20, 16, k=2)                   # záliv s jezírkem
    c.blob(0, 72, 318, 66, 16, k=11)                  # síň sahá až pod terasu
    c.blob(1, 75, 236, 70, 66, k=3)                   # střední terasa
    c.blob(1, 78, 170, 58, 20, k=12)                  # terasa sahá až pod horní patro
    c.blob(2, 75, 100, 52, 50, k=5)                   # horní patro s oltářem
    c.blob(9, 10, 268, 6, 12, k=7)                    # skalní pilíř
    c.carve_nodes()
    c.rect(0, 58, 400, 93, AH)                        # chodba ven
    wm = ((c.xs - 24) / 15.0) ** 2 + ((c.ys - 394) / 9.0) ** 2 < 1
    c.water |= wm & (c.H == 0)
    c.faces()
    c.paint_base()
    c.rock_mass()

    # voda
    wn = value_noise(AW, AH, 4, 3)
    for y, x in zip(*np.nonzero(c.water)):
        edge = any(not c.water[y + dy, x + dx] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        c.img[y, x] = WATER[0] if edge else (WATER[2] if wn[y, x] > 0.78 else WATER[1])
        if not edge and wn[y, x] > 0.9 and (x + y) % 3 == 0: c.img[y, x] = WATER[3]
    c.lights.append((24, 394, 26, 0.35, WATER[2]))

    c.outline()
    c.moss(1.0)

    # vyšlapaná stezka (světlejší tečkování podél hran grafu)
    for a, b in edges:
        (ax, ay), (bx, by) = nodes[a], nodes[b]
        n = int(math.hypot(bx - ax, by - ay))
        for i in range(0, n, 3):
            x, y = ax + (bx - ax) * i / n, ay + (by - ay) * i / n
            xi, yi = int(x), int(y)
            if c.is_floor()[yi, xi] and not c.water[yi, xi]:
                c.px(xi + (i % 2), yi, FLOOR[min(2, c.H[yi, xi] + 1)][2])

    # schody na hranách mezi patry
    for a, b in edges:
        if lvl[a] != lvl[b]:
            lo, hi = (a, b) if lvl[a] < lvl[b] else (b, a)
            p = c.crossing(lo, hi, lvl[lo], lvl[hi])
            if p:
                x, y = p
                yy = y
                while yy + 1 < AH and c.face[yy + 1, x] < 0 and c.H[yy + 1, x] == lvl[hi]: yy += 1
                c.stairs(x, yy - 2, yy + STEP_FH * (lvl[hi] - lvl[lo]) + 2)

    for (x, y, big) in ((22, 214, True), (48, 212, False), (18, 244, True), (52, 244, False),
                        (60, 196, False), (128, 186, False), (134, 330, False), (12, 366, True), (52, 402, False)):
        c.mushroom(x, y, big)
        c.lights.append((x, y - 3, 14, 0.30, CAP[1]))
    for (x, y) in ((130, 200), (136, 226), (104, 196), (40, 112), (114, 116), (108, 82), (44, 80)):
        c.crystal_cluster(x, y, CRY_B, 3)
        c.lights.append((x, y - 3, 16, 0.34, CRY_B[2]))
    for (x, y, r) in ((130, 340, 5), (104, 338, 4), (138, 362, 3), (98, 410, 3), (30, 322, 3)):
        c.boulder(x, y, r)
    for (x, y, h) in ((138, 388, 6), (56, 410, 5), (106, 282, 4), (120, 134, 4), (30, 128, 5)):
        c.stalagmite(x, y, h)
    for n in ("jezirko", "balvany_j", "houby", "krystaly_j"):
        c.encounter_patch(*nodes[n])

    # oltář s modrým krystalem
    ax, ay = nodes["krystal_modry"]
    c.altar(ax, ay - ALTAR_ABOVE, CRY_B)          # hráč stojí před oltářem, krystal kreslí aplikace
    c.lights.append((ax, ay - ALTAR_ABOVE - 6, 32, 0.55, CRY_B[2]))
    c.warm_exit(56, 95, 392)
    c.lights.append((75, AH + 4, 40, 0.7, WARM))
    c.lighting()
    c.save()
    return c


# ═══════════════════════════════════════════════════════════════════════════════
# 2) STARÝ DŮL (uzavřené bludiště, žebříky, červený krystal)
# ═══════════════════════════════════════════════════════════════════════════════
def gen_maze():
    AW, AH = 150, 480
    L, M, R = 26, 75, 124                     # sloupce štol
    nodes = {
        "vychod_dolu":     (L, 466),
        "stola_vstup":     (L, 412),
        "stola_kriz":      (M, 412),
        "vozik":           (R, 412),
        "zebrik1":         (M, 344),
        "tezba":           (R, 344),
        "chodba_zapad":    (L, 344),
        "chodba_sever":    (L, 276),
        "rozcesti_dul":    (M, 276),
        "netopyri":        (R, 276),
        "zebrik2":         (M, 208),
        "slepa_chodba":    (L, 208),
        "horni_stola":     (R, 208),
        "hlubina":         (R, 140),
        "sin_krystalu":    (M, 140),
        "krystal_cerveny": (M, 76),
    }
    lvl = {"vychod_dolu": 0, "stola_vstup": 0, "stola_kriz": 0, "vozik": 0,
           "zebrik1": 1, "tezba": 1, "chodba_zapad": 1, "chodba_sever": 1, "rozcesti_dul": 1, "netopyri": 1,
           "zebrik2": 2, "slepa_chodba": 2, "horni_stola": 2, "hlubina": 2, "sin_krystalu": 2, "krystal_cerveny": 2}
    edges = [("vychod_dolu", "stola_vstup"), ("stola_vstup", "stola_kriz"), ("stola_kriz", "vozik"),
             ("stola_kriz", "zebrik1"),                                     # žebřík 0→1
             ("zebrik1", "tezba"), ("zebrik1", "chodba_zapad"), ("chodba_zapad", "chodba_sever"),
             ("chodba_sever", "rozcesti_dul"), ("rozcesti_dul", "netopyri"),
             ("rozcesti_dul", "zebrik2"),                                   # žebřík 1→2
             ("zebrik2", "slepa_chodba"), ("zebrik2", "horni_stola"), ("horni_stola", "hlubina"),
             ("hlubina", "sin_krystalu"), ("sin_krystalu", "krystal_cerveny")]
    c = Cave("cave_maze", AW, AH, nodes, edges, lvl, seed=23)

    # chodby: vodorovné mají místo navíc na severu (čelo stěny), svislé jsou užší
    def corridor(a, b, level):
        (ax, ay), (bx, by) = nodes[a], nodes[b]
        if ay == by:
            c.rect(level, min(ax, bx) - 9, ay - 9 - WALL_FH, max(ax, bx) + 10, ay + 8)
        else:
            c.rect(level, ax - 9, min(ay, by) - 9 - WALL_FH, ax + 10, max(ay, by) + 8)

    for a, b in edges:
        if lvl[a] == lvl[b]: corridor(a, b, lvl[a])
    # žebříkové šachty: spodní polovina patro níž, horní patro výš
    ladders = (("stola_kriz", "zebrik1"), ("rozcesti_dul", "zebrik2"))
    for lo, hi in ladders:
        (x, y0), (_, y1) = nodes[lo], nodes[hi]
        mid = (y0 + y1) // 2
        c.rect(lvl[hi], x - 9, y1 - 9 - WALL_FH, x + 10, mid)
        c.rect(lvl[lo], x - 9, mid, x + 10, y0 + 8)
    c.rect(0, L - 9, 420, L + 10, AH)                                       # štola ven
    c.blob(2, M, 64, 28, 24, rough=0.12, k=9)                               # síň krystalu
    c.rect(1, R - 9, 276 - 9 - WALL_FH, R + 18, 284)                        # výklenek netopýrů
    c.faces()
    c.paint_base()
    c.rock_mass()
    c.outline()
    c.moss(0.45)

    for lo, hi in ladders:
        x, y0 = nodes[lo]; _, y1 = nodes[hi]
        mid = (y0 + y1) // 2
        c.ladder(x, mid - 4, mid + STEP_FH + 3)

    # koleje ve spodní štole, výztuže a lucerny
    y = nodes["stola_kriz"][1] + 2
    for x in range(L - 6, R + 9):
        if c.is_floor()[y, x]:
            c.px(x, y - 2, RAIL); c.px(x, y + 2, RAIL)
            if x % 4 == 0:
                for k in (-1, 0, 1): c.px(x, y + k, WOOD[1])
    for (x, y) in ((50, 412), (100, 412), (50, 344), (100, 344), (50, 276), (100, 276), (50, 208), (100, 208), (100, 140)):
        c.support(x, y - 9 - WALL_FH + 1)
    for (x, y) in ((62, 412), (112, 344), (38, 276), (112, 208), (88, 140), (38, 208)):
        c.lantern(x, y - 9 - WALL_FH + 4)

    # opuštěný vozík
    vx, vy = nodes["vozik"]
    vx -= 2
    for y in range(vy - 12, vy - 4):
        for x in range(vx - 7, vx + 8):
            e = x in (vx - 7, vx + 7) or y in (vy - 12, vy - 5)
            c.px(x, y, OUT if e else (STONE[1] if y > vy - 10 else ORE[0] if (x + y) % 3 == 0 else STONE[2]))
    for x in (vx - 4, vx + 4): c.px(x, vy - 4, OUT); c.px(x, vy - 3, OUT)

    # rudná žíla v čele u těžby + krumpáč
    tx, ty = nodes["tezba"]
    for (dx, dy) in ((-6, -14), (-3, -12), (2, -15), (5, -12), (0, -10), (-5, -10), (7, -14)):
        c.px(tx + dx, ty + dy, ORE[1]); c.px(tx + dx + 1, ty + dy, ORE[0])
    c.lights.append((tx, ty - 12, 16, 0.26, ORE[1]))
    for k in range(6): c.px(tx + 8 - k, ty - 2 - k, WOOD[0])
    for k in range(-2, 3): c.px(tx + 3 + k, ty - 7 + abs(k), STONE[2])

    # netopýři (tmavá komora) a slepá chodba: kosti a krápníky
    nx, ny = nodes["netopyri"]
    for (dx, dy) in ((-6, -14), (3, -16), (10, -13)):
        c.px(nx + dx, ny + dy, OUT); c.px(nx + dx - 1, ny + dy - 1, OUT); c.px(nx + dx + 1, ny + dy - 1, OUT)
        c.px(nx + dx, ny + dy - 1, (200, 60, 70))
    sx, sy = nodes["slepa_chodba"]
    c.stalagmite(sx - 6, sy + 5, 5)
    for dx in range(-3, 4): c.px(sx + dx + 4, sy + 6, (214, 214, 196))

    for (x, y) in ((58, 52), (94, 54), (60, 74), (92, 72), (118, 132)):
        c.crystal_cluster(x, y, CRY_R, 2)
        c.lights.append((x, y - 3, 14, 0.28, CRY_R[2]))
    for (x, y) in ((33, 310), (130, 176), (40, 214), (130, 420), (32, 440)):
        c.mushroom(x, y, False)
        c.lights.append((x, y - 3, 12, 0.24, CAP[1]))
    for n in ("vozik", "netopyri", "slepa_chodba", "hlubina"):
        c.encounter_patch(*nodes[n])

    ax, ay = nodes["krystal_cerveny"]
    c.altar(ax, ay - ALTAR_ABOVE, CRY_R)
    c.lights.append((ax, ay - ALTAR_ABOVE - 6, 30, 0.55, CRY_R[2]))
    c.warm_exit(L - 12, L + 14, 440)
    c.lights.append((L, AH + 4, 30, 0.7, WARM))
    c.lighting(ambient=0.5)
    c.save()
    return c


if __name__ == "__main__":
    gen_open()
    gen_maze()
