"""Kolize podle siluety spritu (docs/adr/0094).

Mapy jsou jedna vrstva a hráč se kreslí přes všechno – kdyby šel „za“ dům nebo strom, prošel by jím.
Proto se blokuje celá silueta objektu: díry v řádcích (mezi větvemi, rameny vah) se zaplní a silueta se
rozšíří o `pad` px do stran (půl šířky postavy), ať hráč nezajede bokem do zdi.
"""
import numpy as np


def silhouette(block, alpha, x0, y0, pad=3):
    m = np.asarray(alpha) > 0
    m = m.copy()
    for sy in range(m.shape[0]):
        row = np.nonzero(m[sy])[0]
        if len(row): m[sy, row[0]:row[-1] + 1] = True
    m = np.pad(m, ((0, 0), (pad, pad)))                 # rozšíření může sahat za okraj spritu
    wide = m.copy()
    for d in range(1, pad + 1):
        wide[:, d:] |= m[:, :-d]; wide[:, :-d] |= m[:, d:]
    H, W = block.shape
    for sy, sx in zip(*np.nonzero(wide)):
        X, Y = x0 + sx - pad, y0 + sy
        if 0 <= X < W and 0 <= Y < H: block[Y, X] = True


if __name__ == "__main__":
    b = np.zeros((6, 10), bool)
    a = np.zeros((2, 4)); a[0, 0] = a[0, 3] = 1; a[1, 1] = 1
    silhouette(b, a, 3, 2, pad=1)
    assert b[2, 2:8].all() and not b[2, 8] and b[3, 3:6].all() and not b[3, 6] and not b[4].any(), b.astype(int)
    print("ok")
