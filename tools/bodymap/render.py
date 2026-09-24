import sys; sys.path.insert(0,'.')
from shapes import *
hl = {"LATS","TRAPS","REAR_DELTS","BICEPS","FOREARMS"}
half = {"LOWER_BACK"}
def fig(muscles, x0):
    out=[f'<g transform="translate({x0},0)">']
    for side in ("", 'transform="matrix(-1 0 0 1 100 0)"'):
        out.append(f'<path d="{SIL}" fill="#E4DEC0" {side}/>')
    out.append(f'<path d="{HEAD}" fill="#E4DEC0"/><path d="{NECK}" fill="#E4DEC0"/>')
    for m,ps in muscles.items():
        col = "#BC6C25" if m in hl else ("#DDA15E" if m in half else "#C9C2A0")
        for p in ps:
            for side in ("", 'transform="matrix(-1 0 0 1 100 0)"'):
                out.append(f'<path d="{p}" fill="{col}" {side}/>')
    out.append('</g>'); return "\n".join(out)
svg=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 220 205" width="880" height="820" style="background:#FEFAE0">{fig(FRONT,5)}{fig(BACK,115)}</svg>'
open("body.svg","w").write(svg)
open("body.html","w").write(f"<html><body style='margin:0;background:#FEFAE0'>{svg}</body></html>")
