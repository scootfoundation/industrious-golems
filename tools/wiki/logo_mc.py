"""MINECRAFT lettering for the wiki, vectorised from a reference image of the game's logo.

trace_vectorise.py (needs the reference JPEG, which is not in the repo) turned that image into polygon layers; the layers live in
minecraft_trace.json as pixel coordinates. make() draws them back as an SVG: black silhouette, dark front band, stone face with a
vertical gradient, then the cracks and white flecks.
"""
import json
from pathlib import Path

DATA = json.loads((Path(__file__).with_name("minecraft_trace.json")).read_text())
X0, Y0, X1, Y1 = 215, 335, 1690, 592


def _d(polys):
    return " ".join("M" + " L".join(f"{x:g},{y:g}" for x, y in p) + "Z" for p in polys)


def make(path):
    w, h = X1 - X0, Y1 - Y0
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{X0} {Y0} {w} {h}" role="img" aria-label="Minecraft">'
           '<defs>'
           '<linearGradient id="mcf" gradientUnits="userSpaceOnUse" x1="0" y1="350" x2="0" y2="535">'
           '<stop offset="0" stop-color="#b3aea9"/><stop offset="1" stop-color="#dcd8d4"/></linearGradient>'
           '<linearGradient id="mcb" gradientUnits="userSpaceOnUse" x1="0" y1="520" x2="0" y2="580">'
           '<stop offset="0" stop-color="#4c4c4c"/><stop offset="1" stop-color="#2f2f2f"/></linearGradient>'
           '</defs>')
    for key, fill in (("sil", "#000"), ("band", "url(#mcb)"), ("face", "url(#mcf)"), ("crack", "#86817b"), ("fleck", "#fff")):
        svg += f'<path d="{_d(DATA[key])}" fill="{fill}" fill-rule="evenodd"/>'
    Path(path).write_text(svg + "</svg>", encoding="utf-8")


if __name__ == "__main__":
    make(Path(__file__).resolve().parents[2] / "site/assets/img/minecraft.svg")
