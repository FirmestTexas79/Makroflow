"""
Mapa „Zóna 2“ (Akt II) do deníku – stejný generátor jako zóna 1 (gen_zone.py), jen jiné rozvržení.
Použití:  python tools/mapgen/gen_zone2.py [--preview out.png]
Výstup:   app/src/main/assets/zone2/<BIOME>.png, bg.png, zone2.json
Kolem lokací není moře, ale popelavá pustina (stejný algoritmus pobřeží, jiné barvy).
Město Ozvěna přibude, až bude ve hře.
"""
import os, sys
import gen_zone as Z

Z.OUT = os.path.join(Z.ASSETS, "zone2")
Z.ZONE_JSON = "zone2.json"
Z.ZONE_TITLE = "zóna 2"
Z.W, Z.H = 900 + 2 * Z.MX, 800 + 2 * Z.MY
Z.LOC = {
    "QUIET_ORCHARD": dict(img="quiet_orchard", outdoor=False, x=40, y=230, h=420),
    "ASH_PLAIN":     dict(img="ash_plain",     outdoor=False, x=330, y=40, h=720),
    "ECHO_CANYON":   dict(img="echo_canyon",   outdoor=False, x=640, y=200, h=520),
}
Z.NODES = {
    ("ASH_PLAIN", "k_sadu"): (10, 192), ("ASH_PLAIN", "ke_kanonu"): (150, 192),
    ("QUIET_ORCHARD", "vstup_ze_plane"): (152, 80), ("ECHO_CANYON", "vstup_z_plane"): (6, 58),
}
Z.LINKS = [
    (("ASH_PLAIN", "k_sadu", "w"), ("QUIET_ORCHARD", "vstup_ze_plane", "e"), False),
    (("ASH_PLAIN", "ke_kanonu", "e"), ("ECHO_CANYON", "vstup_z_plane", "w"), False),
]
Z.LABEL = {"ASH_PLAIN": (470, 30), "QUIET_ORCHARD": (150, 214), "ECHO_CANYON": (760, 184)}
Z.TITLE = (160, 770)
Z.TEX = {}
Z.TEX_GAIN = {}
Z.NPC = {}
Z.HEADS = {}
Z.SECRET_BG = set()
Z.NAMES = {"ASH_PLAIN": "Popelavá pláň", "QUIET_ORCHARD": "Ztichlý sad", "ECHO_CANYON": "Ozvěnový kaňon"}
# popelavá pustina místo moře
Z.SEA = (58, 54, 60); Z.SEA_LIGHT = (84, 80, 88); Z.FOAM = (150, 146, 150)
Z.SAND = (168, 158, 138); Z.SAND_DARK = (128, 120, 102)

if __name__ == "__main__":
    a = sys.argv[1:]
    Z.main(a[a.index("--preview") + 1] if "--preview" in a else None)
