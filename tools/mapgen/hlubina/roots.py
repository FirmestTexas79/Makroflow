"""Zóna 3 – kořeny Spící: válcové stínování, hrboly, rýhy; zapisují se do z-bufferu scény."""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain")); sys.path.insert(0, os.path.join(HERE, "..", "tower")); sys.path.insert(0, HERE)
from common import *  # noqa: E402,F401,F403
from common import _shade, _pick, _h, _vn  # noqa: E402,F401


def root_path(img, depth, pts, w0, w1, AW, AH, block=None, lift=0.0, d=0.5, zbuf=None):
    """Kořen podél křivky bodů (šířka w0→w1) ve 3/4: válcové stínování, praskliny v kůře, stín pod ním.
    Kreslí přímo do img; block = kořen je překážka. lift = kořen vystupuje nad zem (víc stínu)."""
    n = len(pts)
    for i in range(n - 1):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        t = i / (n - 1)
        w = (w0 + (w1 - w0) * t) * (1 + 0.18 * math.sin(t * 23 + w0) + 0.1 * math.sin(t * 61))   # hrboly
        dx, dy = x1 - x0, y1 - y0
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln
        steps = max(1, int(ln * 1.6))
        for s_ in range(steps):
            cx, cy = x0 + dx * s_ / steps, y0 + dy * s_ / steps
            for k in np.linspace(-w / 2, w / 2, max(2, int(w * 2.2))):
                X, Y = int(round(cx + nx * k)), int(round(cy + ny * k))
                if not (0 <= X < AW and 0 <= Y < AH):
                    continue
                if depth[Y, X] and depth[Y, X] < d - 0.01:
                    continue
                u = k / (w / 2 + 0.01)
                v = 0.52 - 0.38 * u * (1 if nx >= 0 else -1) + 0.08 * math.cos(u * math.pi)
                v += (_vn(X * 1.4, Y * 1.4, 11, 3) - 0.5) * 0.3                  # kůra
                if abs(_vn(cx * 0.9 + k * 2, cy * 0.9, 12, 4) - 0.5) < 0.04:
                    v -= 0.3                                                  # podélné rýhy
                if abs(u) > 0.84:
                    v = 0.08
                if _h(int(cx * 0.6), int(cy * 0.6), 7) % 9 == 0 and abs(u) < 0.5:
                    v -= 0.3
                v = min(v, 0.82)
                img[Y, X] = _pick(BARK, max(0.0, min(1.0, v)), X, Y)
                depth[Y, X] = d
                if zbuf is not None:
                    zbuf[Y, X] = cy + w * 0.3                                # kořen leží na zemi v hloubce cy
                if block is not None:
                    block[Y, X] = True
            # stín pod kořenem (vpravo dole)
            for k in range(1, int(2 + lift * 3)):
                X, Y = int(round(cx + w / 2 * 0.3 + k)), int(round(cy + w / 2 + k))
                if 0 <= X < AW and 0 <= Y < AH and not depth[Y, X]:
                    img[Y, X, :3] = (img[Y, X, :3] * 0.55).astype(np.uint8)




def bez(p0, p1, p2, n=50):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]) for t in np.linspace(0, 1, n)]


def root(sc, pts, w0, w1, solid=True, lift=0.5, d=0.5):
    """Kořen do scény: kreslí, zapisuje z-buffer (předměty za ním ho nepřekreslí) a volitelně blokuje chůzi."""
    root_path(sc.img, sc.depth, pts, w0, w1, sc.W, sc.H, sc.block if solid else None, lift=lift, d=d, zbuf=sc.zbuf)
