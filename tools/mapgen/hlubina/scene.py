"""
Zóna 3 – skládání mapy lokace (stejně pro všech 8): podlaha a zadní stěna, kořeny, z-řazené sprity se stíny,
světlo (teplé sodíkové tůně, žhnutí, studená modř Lumenců a hub), viněta, export PNG + mapy chůze + ladicí obrázek
a kontrola dosažitelnosti uzlů (BFS). Pořadí kreslení řeší z-buffer po pixelech: kořen nebo předmět, který leží
blíž (větší y paty), se tím, co je za ním, nepřekreslí – žádné useknuté kořeny pod bednami.
"""
import math, os, sys
from collections import deque
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..")); sys.path.insert(0, os.path.join(HERE, "..", "tower")); sys.path.insert(0, HERE)
import walkmask  # noqa: E402
import light as L  # noqa: E402

ROOT = os.path.join(HERE, "..", "..", "..")


class Scene:
    def __init__(self, W, H, wall_h, nodes, edges):
        self.W, self.H, self.wall_h = W, H, wall_h
        self.nodes, self.edges = nodes, edges
        self.img = np.zeros((H, W, 4), np.uint8); self.img[:, :, 3] = 255
        self.block = np.zeros((H, W), bool)
        self.depth = np.zeros((H, W))
        self.zbuf = np.full((H, W), -1e9)
        self.placed = []

    def put(self, spr, x, y):
        """Přímo do mapy (stěna, nápisy) – bez z-testu."""
        for yy in range(spr.h):
            for xx in range(spr.w):
                if spr.a[yy, xx, 3] and 0 <= y + yy < self.H and 0 <= x + xx < self.W:
                    self.img[y + yy, x + xx] = spr.a[yy, xx]

    def place(self, spr, cx, by, collide=True, pad=1, shadow=True, foot=None, z=None):
        o = dict(spr=spr, x=int(cx - spr.w // 2), y=int(by - spr.h), z=by if z is None else z, shadow=shadow)
        self.placed.append(o)
        if collide:
            if foot:
                fw, fh = foot
                self.block[by - fh:by, int(cx - fw / 2):int(cx + fw / 2)] = True
            else:
                walkmask.silhouette(self.block, spr.a[:, :, 3], o["x"], o["y"], pad)
        return o

    def shadows(self, floor, k=0.55):
        sh = np.zeros((self.H, self.W))
        L.wall_base_shadow(sh, self.wall_h, depth=18, k=0.9)
        for o in self.placed:
            if o["shadow"]:
                s = o["spr"]
                L.hard_shadow(sh, s.a[:, :, 3], o["x"], o["y"], 0.5, 0.3, 0.7)
                L.contact_shadow(sh, o["x"] + s.w / 2, o["y"] + s.h - 1, s.w / 2 + 1, 2.4, 1.0)
        sh *= floor
        L.darken(self.img, sh, k, (4, 4, 8), dither=False, steps=4)

    def draw_sprites(self):
        for o in sorted(self.placed, key=lambda o: o["z"]):
            s, z = o["spr"], o["z"]
            for yy in range(s.h):
                Y = o["y"] + yy
                if not 0 <= Y < self.H:
                    continue
                for xx in range(s.w):
                    X = o["x"] + xx
                    if s.a[yy, xx, 3] and 0 <= X < self.W and z >= self.zbuf[Y, X]:
                        self.img[Y, X] = s.a[yy, xx]
                        self.zbuf[Y, X] = z

    @staticmethod
    def pool(mask, cx, cy, rx, ry, k=1.0):
        H, W = mask.shape
        for y in range(int(cy - ry) - 1, int(cy + ry) + 2):
            for x in range(int(cx - rx) - 1, int(cx + rx) + 2):
                if 0 <= x < W and 0 <= y < H:
                    d = math.hypot((x + .5 - cx) / rx, (y + .5 - cy) / ry) + (0.04 if (x + y) % 2 else -0.04)
                    mask[y, x] = max(mask[y, x], max(0.0, 1 - d) ** 1.4 * k)

    def light(self, warm=(), ember=(), cold=(), dark_k=0.45, warm_k=0.34, cold_k=0.22):
        """Pooly jako (cx, cy, rx, ry, k). Tma všude, kam nesvítí nic."""
        m = {n: np.zeros((self.H, self.W)) for n in ("w", "e", "c")}
        for n, lst in (("w", warm), ("e", ember), ("c", cold)):
            for p in lst:
                self.pool(m[n], *p)
        dark = np.clip(1.0 - np.maximum(np.maximum(m["w"], m["c"] * 0.7), m["e"] * 0.6), 0, 1)
        L.darken(self.img, dark, dark_k, (3, 4, 10), dither=True, steps=12)
        L.lighten(self.img, m["w"], warm_k, (255, 176, 84), steps=12)
        L.lighten(self.img, m["e"], 0.3, (255, 110, 40), steps=6)
        L.lighten(self.img, m["c"], cold_k, (110, 180, 255), steps=8)
        vg = np.zeros((self.H, self.W)); L.vignette(vg, self.W / 2, self.H * 0.5, self.W * 0.62, self.H * 0.6, k=0.6)
        L.darken(self.img, vg, 0.8, (2, 2, 6), dither=True, steps=10)

    def export(self, name, gen):
        Image.fromarray(self.img[:, :, :3]).save(os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi", f"{name}.png"))
        CELL = 2
        walk = ~self.block
        rows = ["".join("." if walk[gy * CELL:(gy + 1) * CELL, gx * CELL:(gx + 1) * CELL].mean() >= 0.5 else "#"
                        for gx in range(self.W // CELL)) for gy in range(self.H // CELL)]
        for n, (x, y) in self.nodes.items():
            r = list(rows[y // CELL]); r[x // CELL] = "."; rows[y // CELL] = "".join(r)
        with open(os.path.join(ROOT, "app", "src", "main", "assets", "walk", f"{name}.txt"), "w") as f:
            f.write(f"# mapa chůze {name}: obrázek {self.W}x{self.H}, buňka {CELL} px (tools/mapgen/{gen})\n{self.W} {self.H} {CELL}\n")
            f.write("\n".join(rows) + "\n")
        first = next(iter(self.nodes.values()))
        st = (first[1] // CELL, first[0] // CELL)
        seen = {st}; q = deque([st])
        while q:
            gy, gx = q.popleft()
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = gy + dy, gx + dx
                if 0 <= ny < len(rows) and 0 <= nx < len(rows[0]) and rows[ny][nx] == "." and (ny, nx) not in seen:
                    seen.add((ny, nx)); q.append((ny, nx))
        bad = [n for n, (x, y) in self.nodes.items() if (y // CELL, x // CELL) not in seen]
        dbg = Image.fromarray(self.img[:, :, :3]).resize((self.W * 3, self.H * 3), Image.NEAREST).convert("RGBA")
        ov = Image.new("RGBA", dbg.size, (0, 0, 0, 0)); dr = ImageDraw.Draw(ov)
        for gy, row in enumerate(rows):
            for gx, ch in enumerate(row):
                if ch == "#":
                    dr.rectangle([gx * 6, gy * 6, gx * 6 + 5, gy * 6 + 5], fill=(255, 0, 0, 40))
        for (a, b) in self.edges:
            A, B = self.nodes[a], self.nodes[b]
            dr.line([A[0] * 3, A[1] * 3, B[0] * 3, B[1] * 3], fill=(255, 255, 0, 200), width=2)
        for n, (x, y) in self.nodes.items():
            dr.ellipse([x * 3 - 5, y * 3 - 5, x * 3 + 5, y * 3 + 5], fill=(0, 120, 255, 230))
        Image.alpha_composite(dbg, ov).save(os.path.join(HERE, "..", f"{name}_debug.png"))
        print(name, "ok", ("NEDOSAŽITELNÉ: " + ", ".join(bad)) if bad else "všechny uzly dosažitelné")
        return bad
