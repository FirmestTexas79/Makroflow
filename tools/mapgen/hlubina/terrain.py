"""Zóna 3 – skála dutiny, římsa a propast."""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain")); sys.path.insert(0, os.path.join(HERE, "..", "tower")); sys.path.insert(0, HERE)
from common import *  # noqa: E402,F401,F403
from common import _shade, _pick, _h, _vn  # noqa: E402,F401


def cavern_wall(W, H):
    """Svislá čedičová stěna (vrstvy, praskliny), nahoře ztrácející se ve tmě stropu."""
    s = Spr(W, H)
    for y in range(H):
        for x in range(W):
            strata = math.sin(y / 5.5 + _vn(x, y, 3, 18) * 3) * 0.12
            col = _vn(x * 0.6, y * 2.4, 4, 7)
            v = 0.42 + strata + (col - 0.5) * 0.3 - (1 - y / H) * 0.25
            if abs(_vn(x * 1.3, y * 0.5, 6, 9) - 0.5) < 0.03:
                v = 0.06                                                  # svislé pukliny
            if y > H - 6:
                v -= 0.18 * (y - (H - 6)) / 6                             # pata stěny ve stínu
            _shade(s, x, y, v, ROCK)
    return s


