"""
Mapa „Zóna 3“ (Akt III – Hlubina) do deníku – stejný generátor jako zóna 1 a 2 (gen_zone.py), jen jiné rozvržení.
Použití:  python tools/mapgen/gen_zone3.py [--preview out.png]
Výstup:   app/src/main/assets/zone3/<BIOME>.png, bg.png, zone3.json
Kolem lokací není moře, ale propast Hlubiny (tma se zelenou září zespodu); lokace sestupují shora dolů:
Stanice 8 → Pozorovatelna B → (Signální útes → gondolou Kořenový most | Tábor na svahu → Svah bezejmenných).
"""
import os, sys
import gen_zone as Z

Z.OUT = os.path.join(Z.ASSETS, "zone3")
Z.ZONE_JSON = "zone3.json"
Z.ZONE_TITLE = "zóna 3"
Z.W, Z.H = 900 + 2 * Z.MX, 1600 + 2 * Z.MY
Z.LOC = {
    "ROOT_CAMP":      dict(img="root_camp",      outdoor=False, x=60,  y=40,   h=380),
    "ROOT_WATCH":     dict(img="root_watch",     outdoor=False, x=80,  y=520,  h=380),
    "ROOT_SIGNAL":    dict(img="root_signal",    outdoor=False, x=420, y=300,  h=600),
    "ROOT_BRIDGE":    dict(img="root_bridge",    outdoor=False, x=680, y=60,   h=640),
    "ROOT_SLOPECAMP": dict(img="root_slopecamp", outdoor=False, x=90,  y=1000, h=380),
    "ROOT_NAMELESS":  dict(img="root_nameless",  outdoor=False, x=420, y=960,  h=640),
    "ROOT_TEMPLE":    dict(img="root_temple",    outdoor=False, x=660, y=1180, h=380),
}
Z.NODES = {
    ("ROOT_CAMP", "kt_sestup"): (36, 338), ("ROOT_WATCH", "pb_vstup"): (30, 128),
    ("ROOT_WATCH", "pb_k_rokli"): (168, 300), ("ROOT_SIGNAL", "su_vstup"): (150, 548),
    ("ROOT_SIGNAL", "su_lanovka"): (138, 252), ("ROOT_BRIDGE", "km_vstup"): (104, 546),
    ("ROOT_WATCH", "pb_dolu"): (24, 340), ("ROOT_SLOPECAMP", "tv_vstup"): (22, 86),
    ("ROOT_SLOPECAMP", "tv_dolu"): (92, 346), ("ROOT_NAMELESS", "sb_vstup"): (62, 24),
    ("ROOT_NAMELESS", "sb_chram"): (44, 546), ("ROOT_TEMPLE", "ch_vstup"): (88, 338),
}
Z.LINKS = [
    (("ROOT_CAMP", "kt_sestup", "s"), ("ROOT_WATCH", "pb_vstup", "n"), False),
    (("ROOT_WATCH", "pb_k_rokli", "e"), ("ROOT_SIGNAL", "su_vstup", "s"), False),
    (("ROOT_SIGNAL", "su_lanovka", "e"), ("ROOT_BRIDGE", "km_vstup", "s"), False),
    (("ROOT_WATCH", "pb_dolu", "s"), ("ROOT_SLOPECAMP", "tv_vstup", "n"), False),
    (("ROOT_SLOPECAMP", "tv_dolu", "s"), ("ROOT_NAMELESS", "sb_vstup", "n"), False),
    (("ROOT_NAMELESS", "sb_chram", "e"), ("ROOT_TEMPLE", "ch_vstup", "w"), False),
]
Z.LABEL = {"ROOT_CAMP": (330, 120), "ROOT_WATCH": (330, 600), "ROOT_SIGNAL": (520, 280), "ROOT_BRIDGE": (800, 46),
           "ROOT_SLOPECAMP": (250, 990), "ROOT_NAMELESS": (640, 1060), "ROOT_TEMPLE": (790, 1160)}
Z.LABEL_SIZE = 40
Z.TITLE = (170, 1480)
Z.TEX = {"ROOT_CAMP": (60, 0, 30, 24), "ROOT_WATCH": (60, 0, 30, 24), "ROOT_SLOPECAMP": (60, 0, 30, 24),   # skála stěny
         "ROOT_SIGNAL": (162, 300, 14, 200), "ROOT_BRIDGE": (0, 0, 60, 110), "ROOT_NAMELESS": (0, 120, 20, 90),
         "ROOT_TEMPLE": (0, 120, 10, 200)}
Z.TEX_GAIN = {b: (1.6, 10) for b in Z.LOC}                                  # tmavé lokace by v propasti zmizely
Z.NPC = {}
Z.HEADS = {}
Z.SECRET_BG = set()
Z.NAMES = {"ROOT_CAMP": "Stanice 8", "ROOT_WATCH": "Pozorovatelna B", "ROOT_SIGNAL": "Signální útes",
           "ROOT_BRIDGE": "Kořenový most", "ROOT_SLOPECAMP": "Tábor na svahu", "ROOT_NAMELESS": "Svah bezejmenných",
           "ROOT_TEMPLE": "Chrám Lumenců"}
# propast místo moře: tma, zelená záře z hloubky, hrany skal
Z.SEA = (12, 16, 22); Z.SEA_LIGHT = (26, 46, 44); Z.FOAM = (70, 130, 108)
Z.SAND = (92, 88, 96); Z.SAND_DARK = (60, 58, 66)

if __name__ == "__main__":
    a = sys.argv[1:]
    Z.main(a[a.index("--preview") + 1] if "--preview" in a else None)
