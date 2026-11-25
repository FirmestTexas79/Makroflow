"""
Zóna 3 – Spící jako vzdálený strom v pozadí (obrácený, holý): souvislá silueta z větví generovaných rekurzivně
s plynulým ohybem a tloušťkou podle pravidla trubek (průřez rodiče = součet průřezů dětí), rasterizovaná jako řada
disků (bez mezer mezi segmenty). Tenké větvičky pod 1 px se kreslí průhledně (pokrytí), takže koruna končí jemnou
sítí, ne useknutými kusy. Barva = tma stromu smíchaná s barvou mlhy za ním podle vzdálenosti (vzdušná perspektiva).
"""
import math
import numpy as np


class TreePainter:
    def __init__(self, img, haze_fn, seed=0):
        """img = RGBA mapa; haze_fn(x, y) → (barva mlhy za stromem jako np.array, zamlžení 0–1) v daném místě."""
        self.img, self.haze_fn = img, haze_fn
        self.H, self.W = img.shape[:2]
        self.cov = np.zeros((self.H, self.W))                              # pokrytí (0–1) – kvůli tenkým větvím
        self.shade = np.zeros((self.H, self.W))                            # světlost kůry (strana k mlze)
        self.rng = np.random.default_rng(seed)
        self.tips = []
        self.split3 = 0.3

    def disk(self, cx, cy, r, side_light=0.0):
        if r < 0.75:                                                       # pod pixel: jen průhledné pokrytí
            X, Y = int(round(cx)), int(round(cy))
            if 0 <= X < self.W and 0 <= Y < self.H:
                self.cov[Y, X] = max(self.cov[Y, X], min(1.0, r / 0.75) * 0.85)
            return
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                if 0 <= x < self.W and 0 <= y < self.H:
                    d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                    if d <= r:
                        self.cov[y, x] = 1.0
                        u = (x + 0.5 - cx) / r                              # −1 vlevo … 1 vpravo
                        self.shade[y, x] = max(self.shade[y, x], side_light * max(0.0, -u))

    def stroke(self, pts):
        """pts = [(x, y, r)], plynule: disky po 0,5 px."""
        for (x0, y0, r0), (x1, y1, r1) in zip(pts, pts[1:]):
            n = max(1, int(math.hypot(x1 - x0, y1 - y0) * 2))
            for i in range(n):
                t = i / n
                self.disk(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, r0 + (r1 - r0) * t, 0.5)

    def branch(self, x, y, ang, r, length, depth, gravity, spread=(0.35, 0.7), bend=0.08, maxdepth=8, clamp_y=None, pull=0.025):
        """Větev s plynulým ohybem (ang se mění po malých krocích), na konci se rozdělí na 2–3 děti (pravidlo trubek)."""
        pts = [(x, y, r)]
        steps = max(4, int(length / 2.5))
        a = ang
        drift = self.rng.uniform(-bend, bend)
        for i in range(steps):
            a += drift + self.rng.uniform(-0.05, 0.05)
            a += (gravity - a) * pull                                       # tah k směru „gravitace“ (dolů / po stropě)
            x += math.cos(a) * length / steps; y += math.sin(a) * length / steps
            if clamp_y is not None and y < clamp_y:
                y = clamp_y + self.rng.uniform(0, 1.5)
            rr = r * (1 - 0.25 * (i + 1) / steps)
            pts.append((x, y, rr))
        self.stroke(pts)
        r_end = pts[-1][2]
        if depth >= maxdepth or r_end < 0.35:
            self.tips.append((x, y))
            return
        n = 3 if self.rng.random() < self.split3 else 2
        shares = self.rng.dirichlet(np.ones(n) * 3)
        for k in range(n):
            rc = r_end * math.sqrt(shares[k]) * 1.05
            off = (k - (n - 1) / 2) * self.rng.uniform(*spread) + self.rng.uniform(-0.12, 0.12)
            self.branch(x, y, a + off, rc, length * self.rng.uniform(0.68, 0.84), depth + 1, gravity,
                        spread, bend, maxdepth, clamp_y, pull)

    def composite(self, dark=(8, 13, 12), light=(30, 46, 42)):
        """Silueta do mapy: tma stromu (strana k mlze o kus světlejší) smíchaná s mlhou podle vzdálenosti, pak pokrytí."""
        dark, light = np.array(dark, float), np.array(light, float)
        ys, xs = np.nonzero(self.cov > 0)
        for y, x in zip(ys, xs):
            mist, haze = self.haze_fn(x, y)
            col = dark + (light - dark) * self.shade[y, x]
            col = col * (1 - haze) + mist * haze
            a = self.cov[y, x]
            self.img[y, x, :3] = np.clip(self.img[y, x, :3] * (1 - a) + col * a, 0, 255).astype(np.uint8)
