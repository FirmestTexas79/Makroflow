"""Havíř Vendelín (docs/adr/0050, 0051) – kostlivý havíř ve stylu postav Sunnyside World.

Malý „chibi“ kostlivec (velká lebka, krátké tělo) s havířskou přilbou a lampičkou,
v modrozeleném pracovním kabátě s koženým opaskem. V ruce drží kahan, ve kterém místo oleje
svítí jiskřivky. V důlcích mu žhnou dvě jantarové tečky. Tmavý obrys 1 px a sytá paleta,
stejně jako postava hráče – na mapě 1 px spritu = 1 art px mapy.

Výstup (app/src/main/res/drawable-nodpi/):
  npc_vendelin.png       – portrét do dialogu a deníku (snímek 0, 16× bez vyhlazení, čtverec)
  npc_vendelin_map.png   – pás 4 snímků idle pro mapu (SPRITE_W × SPRITE_H na snímek)
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "drawable-nodpi")

PAL = {
    "K": (27, 26, 36), "k": (58, 52, 70),
    "W": (240, 234, 214), "B": (204, 194, 166), "D": (140, 128, 104),
    "E": (44, 30, 44), "G": (255, 176, 58), "g": (255, 230, 140),
    "Y": (236, 184, 58), "y": (178, 118, 32), "h": (255, 226, 120),
    "L": (255, 250, 200), "O": (255, 164, 52),
    "C": (70, 142, 154), "c": (44, 96, 110), "d": (30, 64, 76),
    "R": (140, 88, 50), "r": (90, 54, 30), "M": (214, 170, 80),
    "S": (164, 172, 184), "s": (92, 98, 112),
    "F": (255, 214, 90), "f": (255, 150, 40), "x": (255, 255, 230),
    "p": (168, 64, 60),                                    # vybledlá nášivka S-7
}

# 24 × 30, snímek 0 (kahan v jeho pravé ruce = vlevo na obrázku)
BASE = [
    "..........KKKKK.........",
    "........KKYYYYYKK.......",
    ".......KYYYKKKYYyK......",
    "......KYhYKLLLKYYyK.....",
    "......KYYYKLOLKYYyK.....",
    ".....KYYYYYKKKYYYYyK....",
    "....KKyyyyyyyyyyyyyyKK..",
    "....KyyyyyyyyyyyyyyyyK..",
    ".....KKWWWWWWWWWWWBBKK..",
    ".....KWWWWWWWWWWWWWBBK..",
    ".....KWWKKKWWWWKKKWBDK..",
    ".....KWKEEGKWWKEEGKBDK..",
    ".....KWKEEEKWWKEEEKBDK..",
    ".....KWWKKKWWKWKKKWBDK..",
    ".....KBWWWWWKEKWWWWBDK..",
    "......KBDWKWKWKWKDDK....",
    ".......KKDDDDDDDDKK.....",
    "...KK...KcCWWWWCcK......",
    "..KssK.KcCCCKKCCCcK.....",
    ".KsFfsKKCCCCCCCCCCcK....",
    ".KsfFsWBKCCCCCCCppcK....",
    ".KsFfsWBKCCRRMRRCCcDK...",
    ".KssssKKKrRRRRRRRrcBK...",
    "..KKKK..KcCCCCCCCcKWK...",
    "........KccCCKCCccKK....",
    "........KdcKK.KKcdK.....",
    "........KrrK...KrrK.....",
    ".......KrrrK...KrrrK....",
    ".......KKKK.....KKKK....",
    "........................",
]

W = max(len(r) for r in BASE)
H = len(BASE)


def frame(k):
    """Idle: lehké nadechnutí (tělo o 1 px dolů ve snímcích 1–2), kahan bliká, jiskřivka krouží."""
    rows = [r.ljust(W, ".") for r in BASE]
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = im.load()
    drop = 1 if k in (1, 2) else 0
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch == ".": continue
            yy = y
            if drop and 6 <= y <= 24: yy = y + 1          # hlava a trup klesnou, nohy stojí
            c = ch
            if ch in "Ff" and k % 2 == 1: c = "f" if ch == "F" else "F"   # plamen v kahanu
            if ch == "G" and k == 3: c = "g"               # žhnutí v důlcích
            if 0 <= yy < H: px[x, yy] = PAL[c] + (255,)
    # při poklesu dopočítat mezeru nad nohama obrysem trupu
    if drop:
        for x in range(W):
            if px[x, 6][3] == 0 and px[x, 7][3] and rows[6][x] != ".": px[x, 6] = PAL["K"] + (255,)
    # jiskřivka kolem kahanu
    sx, sy = [(0, 16), (5, 15), (6, 21), (0, 22)][k]
    if px[sx, sy][3] == 0 or True: px[sx, sy] = PAL["x"] + (255,)
    return im


frames = [frame(k) for k in range(4)]
strip = Image.new("RGBA", (W * 4, H))
for k, f in enumerate(frames):
    strip.paste(f, (k * W, 0))
os.makedirs(RES, exist_ok=True)
strip.save(os.path.join(RES, "npc_vendelin_map.png"))

# portrét: čtvercový, postava uprostřed, 16×
S = max(W, H) + 2
port = Image.new("RGBA", (S, S), (0, 0, 0, 0))
port.paste(frames[0], ((S - W) // 2, (S - H) // 2 + 1))
port.resize((S * 16, S * 16), Image.NEAREST).save(os.path.join(RES, "npc_vendelin.png"))
print("vendelin", W, H)
