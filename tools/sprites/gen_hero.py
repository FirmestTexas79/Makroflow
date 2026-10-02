"""
Postava hráče ze Sunnyside World (docs/adr/0051) – nahrazuje Reda.

Použití:  python tools/sprites/gen_hero.py [--hair "bowl hair"]
Vstup:    tools/sprites/sunnyside_human.aseprite (310 snímků 96×64, 34 animací)
Výstup:   app/src/main/assets/hero/<animace>_<směr>.png – vodorovný pás snímků FRAME_W × FRAME_H
          app/src/main/assets/hero/hero.json            – snímky, délky snímků (ms), rozměr, pata postavy

Směry: s, se, e, ne, n z Aseprite; sw, w, nw zrcadlením. Pohyb (idle, walk, run) má všech 8,
práce (axe = kácení, mining = těžba, casting = chytání hmyzu, doing, watering, dig) jen e/w.
Pozor na směry v souboru: boční chůze kouká doprava, práce doleva a tag „ne“ nahoru doleva.
Snímek se ořízne na 64 × 40 px kolem postavy (místo pro nástroje), pata je na řádku FOOT_Y.
"""
import json, os, sys
from PIL import Image, ImageOps

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ase_extract as A

ROOT = os.path.join(HERE, "..", "..")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "hero")
SRC = os.path.join(HERE, "sunnyside_human.aseprite")

CROP = (16, 0, 80, 40)          # x0, y0, x1, y1 v plátně 96 × 64 (postava má střed na x = 48)
FRAME_W, FRAME_H = CROP[2] - CROP[0], CROP[3] - CROP[1]
FOOT_Y = 38                      # poslední řádek chodidel (v ořezu)

# tag v Aseprite → (animace, směr); první „axe“ je pracovní šablona (jen hlavy), bere se druhý
MOVE = {"idle": "idle", "walk": "walk", "run": "run"}
# pozor: tag „ne“ v souboru ve skutečnosti kouká nahoru DOLEVA (je vidět levá tvář) → nw
DIRS = {"s": "s", "se": "se", "ne": "nw", "n": "n"}
# práce: (kolikátý tag toho jména, kam postava v souboru kouká, výřez snímků, výška snímku)
# Rybaření (reeling, caught) má vlasec a šplouchnutí pod nohama → vyšší snímek 64 px.
# „hammer“ = jen úder kladivem z tagu „hamering“ (snímky 15–21, náraz na 4. snímku) – docs/adr/0054.
ACTIONS = {
    "axe": (2, "w", None, FRAME_H), "mining": (1, "w", None, FRAME_H), "casting": (1, "w", None, FRAME_H),
    "doing": (1, "w", None, FRAME_H), "dig": (1, "w", None, FRAME_H),
    "watering": (1, "e", None, FRAME_H),
    "reeling": (1, "e", None, 64), "caught": (1, "e", None, 64),
    "hammer": (1, "e", (15, 21), FRAME_H),
}
TAG_FOR = {"hammer": "hamering"}
MIRROR = {"se": "sw", "e": "w", "nw": "ne", "w": "e"}
# jednorázové animace čelem k hráči (smrt, skok při teleportu, zásah) – jen směr „s“ (docs/adr/0052)
ONCE = {"death": 1, "jump": 1, "hurt": 1}


def main(hair=None):
    ase = A.parse(SRC)
    ase["hair"] = hair.lower() if hair else None
    frames = {}
    def render(i, h=FRAME_H):
        if (i, h) not in frames: frames[(i, h)] = A.render_frame(ase, i).crop((CROP[0], CROP[1], CROP[2], CROP[1] + h))
        return frames[(i, h)]

    seen = {}
    wanted = []                                         # (animace, směr, [snímky], [ms])
    for t in ase["tags"]:
        name = t["name"].strip().lower()
        seen[name] = seen.get(name, 0) + 1
        idx = list(range(t["start"], t["end"] + 1))
        durs = [ase["frames"][i]["duration"] for i in idx]
        if "-" in name:
            base, d = name.split("-", 1)
            if base in MOVE and d in DIRS: wanted.append((MOVE[base], DIRS[d], idx, durs, FRAME_H))
        elif name in MOVE:
            wanted.append((MOVE[name], "e", idx, durs, FRAME_H))     # boční pohled = doprava
        elif name in ONCE and seen[name] == ONCE[name]:
            wanted.append((name, "s", idx, durs, FRAME_H))           # nakreslené zepředu, bez zrcadlení
        else:
            for act, (nth, base, sub, h) in ACTIONS.items():
                if TAG_FOR.get(act, act) == name and seen[name] == nth:
                    ii, dd = (idx, durs) if sub is None else (idx[sub[0]:sub[1] + 1], durs[sub[0]:sub[1] + 1])
                    wanted.append((act, base, ii, dd, h))

    os.makedirs(OUT, exist_ok=True)
    index = dict(frameW=FRAME_W, frameH=FRAME_H, footY=FOOT_Y, anims={})
    for anim, d, idx, durs, fh in wanted:
        imgs = [render(i, fh) for i in idx]
        for dd, flip in ((d, False), (MIRROR.get(d), True)):
            if dd is None: continue
            strip = Image.new("RGBA", (FRAME_W * len(imgs), fh))
            for k, im in enumerate(imgs):
                strip.paste(ImageOps.mirror(im) if flip else im, (k * FRAME_W, 0))
            key = f"{anim}_{dd}"
            strip.save(os.path.join(OUT, key + ".png"), optimize=True)
            index["anims"][key] = dict(frames=len(imgs), durations=durs, **({"h": fh} if fh != FRAME_H else {}))
    json.dump(index, open(os.path.join(OUT, "hero.json"), "w"), indent=1)
    print(f"{len(index['anims'])} pásů do {OUT}")
    for k in sorted(index["anims"]): print(" ", k, index["anims"][k]["frames"])


if __name__ == "__main__":
    args = sys.argv[1:]
    main(args[args.index("--hair") + 1] if "--hair" in args else None)
