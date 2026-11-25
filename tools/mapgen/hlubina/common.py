"""
Zóna 3 (Akt III – Hlubina), docs/adr/0106 – společné palety a nástroje pro všech 8 lokací. Řád Váhy se pokusil
Hlubinu obsadit (beton, plech, sodíkové světlo, ohňové hlídky, lanovka); kořeny Spící ho pohlcují tím víc, čím
blíž ke kmeni. Pod tím starší vrstva: ruiny Lumenců. Moduly: terrain (skála, římsa, propast), order (stavby Řádu),
lumen (ruiny), roots (kořeny), signs (písmo a cedule), scene (skládání mapy, z-buffer, světlo, export).
"""
import math, os, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "ashplain")); sys.path.insert(0, os.path.join(HERE, "..", "tower")); sys.path.insert(0, HERE)
from pix import Spr, c  # noqa: E402,F401
from figures import sphere, cylinder, selout, _pick, BRASS, STEEL, RED, BAYER  # noqa: E402,F401
from hideout_parts import _h, _vn  # noqa: E402,F401

ROCK = [c(0x0E0E12), c(0x17171D), c(0x212129), c(0x2C2C36), c(0x3A3A46), c(0x4C4C5A)]
SCREE = [c(0x16130F), c(0x221E19), c(0x2F2A23), c(0x3E372E), c(0x4E463A), c(0x625849)]   # suť a štěrk terasy
CONC = [c(0x1C1E20), c(0x282B2E), c(0x34383C), c(0x42474C), c(0x52585E), c(0x666D74)]
OLIVE = [c(0x161A12), c(0x232A1C), c(0x323C28), c(0x445236), c(0x5A6A48), c(0x748660)]
RUST = [c(0x24140C), c(0x42261A), c(0x623A24), c(0x86522E)]
BARK = [c(0x24221E), c(0x3E3A34), c(0x5E584E), c(0x827A6C), c(0xA49C8A), c(0xC8C0AC), c(0xE4DECC)]
CHAR = [c(0x0C0A08), c(0x1A1612), c(0x2A241E), c(0x3C342A)]
SAND = [c(0x2E2618), c(0x4A3E28), c(0x6A5C3C), c(0x8C7C54), c(0xAC9C70)]
PAPER = [c(0x8A7E62), c(0xB4A882), c(0xD8CCA4), c(0xF0E6C4)]
SODIUM = c(0xFFC870)
GLYPH = c(0x7EC0FF)
YELLOW = [c(0x5A4410), c(0x9C7A1E), c(0xD8AC30), c(0xF4D060)]


def _shade(s, x, y, v, ramp):
    s.px(x, y, _pick(ramp, max(0.0, min(1.0, v)), x, y))


