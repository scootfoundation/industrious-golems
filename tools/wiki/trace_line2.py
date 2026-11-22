"""Second line (INDUSTRIOUS GOLEMS) in the traced MINECRAFT style: block letters sheared like the game's logo, with a
black outline, a dark front band under the strokes that stand on the baseline, and a gradient face. Rasterised, then
vectorised with the same routines as trace_vectorise.py; output line2_trace.json."""
import json, numpy as np
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

src = Path(__file__).with_name("trace_vectorise.py").read_text()
ns = {"np": np, "Image": Image}
exec(src[src.index("def trace"):src.index("sil=(S<32)")], ns)   # trace, rdp, area, vec
X0 = Y0 = 0
ns["X0"] = ns["Y0"] = 0

R = lambda *a: a
G = {  # (x0,y0,x1,y1) on a 5 x 8 grid (M is 6 wide)
 "I": [(0,0,5,2),(1.5,0,3.5,8),(0,6,5,8)],
 "N": [(0,0,2,8),(3,0,5,8),(2,1.5,3,5)],
 "D": [(0,0,2,8),(2,0,4,2),(2,6,4,8),(3,1.2,5,6.8)],
 "U": [(0,0,2,8),(3,0,5,8),(2,6,3,8)],
 "S": [(0,0,5,2),(0,1.5,2,4),(0,3,5,5),(3,4,5,6.5),(0,6,5,8)],
 "T": [(0,0,5,2),(1.5,1,3.5,8)],
 "R": [(0,0,2,8),(2,0,5,2),(3,1.5,5,4),(2,3.5,5,5.5),(3,5,5,8)],
 "O": [(0,0,5,2),(0,1.5,2,6.5),(3,1.5,5,6.5),(0,6,5,8)],
 "G": [(0,0,5,2),(0,1.5,2,6.5),(0,6,5,8),(3,4,5,6.5),(2.5,3.5,5,5)],
 "L": [(0,0,2,8),(2,6,5,8)],
 "E": [(0,0,2,8),(2,0,5,2),(2,3,4,5),(2,6,5,8)],
 "M": [(0,0,2,8),(4,0,6,8),(2,1,4,4)],
}
CELL, BH, LEAN, GAP = 13.0, 2.4, 0.14, 1.6
words = ["INDUSTRIOUS", "GOLEMS"]
PADX, PADY = 40, 40

def layout():
    out = []; x = 0.0
    for wi, w in enumerate(words):
        for ch in w:
            wd = 6 if ch == "M" else 5
            out.append((wi, ch, x)); x += wd + GAP
        x += 2.2
    return out, x - GAP - 2.2

def shear(px, py):  # cell coords -> pixels; top leans right
    return PADX + (px + (8 - py) * LEAN) * CELL, PADY + (py) * CELL

def poly(x0, y0, x1, y1, ox):
    return [shear(ox + x, y) for x, y in ((x0, y0), (x1, y0), (x1, y1), (x0, y1))]

lay, total = layout()
W = int(PADX * 2 + (total + 8 * LEAN) * CELL); H = int(PADY * 2 + (8 + BH) * CELL)
masks = {}
for wi in (0, 1):
    for name in ("face", "band"):
        masks[(wi, name)] = Image.new("L", (W, H), 0)
for wi, ch, ox in lay:
    fd = ImageDraw.Draw(masks[(wi, "face")]); bd = ImageDraw.Draw(masks[(wi, "band")])
    for (a, b, c, d) in G[ch]:
        fd.polygon(poly(a, b, c, d, ox), fill=255)
        if d >= 7.99:
            bd.polygon(poly(a, d, c, d + BH, ox), fill=255)
    # thin separator at the letter's left edge of the band is implicit in the gap
face = {wi: np.asarray(masks[(wi, "face")]) > 127 for wi in (0, 1)}
band = {wi: (np.asarray(masks[(wi, "band")]) > 127) & ~face[wi] for wi in (0, 1)}
allm = face[0] | face[1] | band[0] | band[1]
dil = lambda m, n: np.asarray(Image.fromarray((m * 255).astype("uint8")).filter(ImageFilter.MaxFilter(n))) > 0
res = {"size": [W, H],
       "sil": [p.round(1).tolist() for p in ns["vec"](dil(allm, int(CELL * 0.62) | 1), 1.2, 200, 1e9)]}
for wi in (0, 1):
    res[f"face{wi}"] = [p.round(1).tolist() for p in ns["vec"](face[wi], 0.8, 30, 30)]
    res[f"band{wi}"] = [p.round(1).tolist() for p in ns["vec"](band[wi], 0.8, 30, 30)]
Path(__file__).with_name("line2_trace.json").write_text(json.dumps(res, separators=(",", ":")))
print(W, H, {k: len(v) for k, v in res.items() if k != "size"})
