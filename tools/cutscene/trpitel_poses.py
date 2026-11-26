"""
Použití:  python tools/cutscene/trpitel_poses.py
Výstup:   tools/cutscene/build/trpitel_rel_<r>.png + .npy (materiály), trpitel_face_<s>.png

Pózy Trpitele pro cutscénu konce I (Kopí): střední záběr (rámec mapy Kmene, K = 1.75) s rukama, které postupně pouští
(release 0 → 3), a detail hlavy (K = 4.6, 360 × 300), ve kterém se šest očí usměje (smile 0 → 1). Renderuje gen_trpitel.
"""
import os, sys
import numpy as np
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "mapgen"))
import gen_trpitel as T  # noqa: E402

OUT = os.path.join(HERE, "build"); os.makedirs(OUT, exist_ok=True)
JOBS = [("rel", r) for r in (0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0)] + [("face", s) for s in (0.0, 0.5, 1.0)]


def job(j):
    kind, v = j
    T.POSE.update(breath=0.5, smile=0.0, release=0.0)
    if kind == "rel":
        T.POSE["release"] = v
    else:
        T.POSE["smile"] = v
        T.K = 4.6; T.W, T.H = 360, 300
        hc = T.build(0)[1]["head_c"]
        k = 460.0 / (460.0 - hc[2])
        T.OFFX = 180 - T.K * (T.CX + (hc[0] - T.CX) * k)
        T.OFFY = 150 - T.K * (150 + (hc[1] - 150) * k)
    img, info = T.render(0)
    name = f"trpitel_{kind}_{v:.1f}"
    Image.fromarray(img, "RGBA").save(os.path.join(OUT, name + ".png"))
    np.save(os.path.join(OUT, name + "_mat.npy"), info["mat"].astype(np.int8))
    return name


if __name__ == "__main__":
    from multiprocessing import Pool
    with Pool(2) as p:
        for n in p.imap_unordered(job, JOBS):
            print("hotovo", n, flush=True)
