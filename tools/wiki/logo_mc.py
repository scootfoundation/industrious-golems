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


L2 = json.loads(Path(__file__).with_name("line2_trace.json").read_text())


def make_full(path):
    """MINECRAFT with INDUSTRIOUS GOLEMS tucked under it (second line drawn first so the first line overlaps it)."""
    w1 = X1 - X0
    sc = w1 / L2["size"][0] * 1.0
    ty = 585 - 40 * sc          # local face top (y=40) lands just under MINECRAFT's band
    h = ty + L2["size"][1] * sc - Y0 - 20 * sc
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{X0} {Y0} {w1} {h:.0f}" role="img" aria-label="Industrious Golems">'
           '<defs>'
           '<linearGradient id="mcf" gradientUnits="userSpaceOnUse" x1="0" y1="350" x2="0" y2="535">'
           '<stop offset="0" stop-color="#b3aea9"/><stop offset="1" stop-color="#dcd8d4"/></linearGradient>'
           '<linearGradient id="mcb" gradientUnits="userSpaceOnUse" x1="0" y1="520" x2="0" y2="580">'
           '<stop offset="0" stop-color="#4c4c4c"/><stop offset="1" stop-color="#2f2f2f"/></linearGradient>'
           f'<linearGradient id="g0" gradientUnits="userSpaceOnUse" x1="0" y1="{ty+40*sc:.0f}" x2="0" y2="{ty+144*sc:.0f}">'
           '<stop offset="0" stop-color="#b86a2c"/><stop offset=".55" stop-color="#e8a23c"/><stop offset="1" stop-color="#f7dc72"/></linearGradient>'
           f'<linearGradient id="g1" gradientUnits="userSpaceOnUse" x1="0" y1="{ty+40*sc:.0f}" x2="0" y2="{ty+144*sc:.0f}">'
           '<stop offset="0" stop-color="#8fb8a4"/><stop offset="1" stop-color="#c4e0e6"/></linearGradient>'
           '</defs>')

    def dd(polys):
        return " ".join("M" + " L".join(f"{x * sc + (X0):g},{y * sc + ty:g}" for x, y in p) + "Z" for p in polys)
    svg += f'<path d="{dd(L2["sil"])}" fill="#000"/>'
    for i, bandc in enumerate(("#4a2c26", "#474a2c")):
        svg += f'<path d="{dd(L2[f"band{i}"])}" fill="{bandc}"/>'
    for i in (0, 1):
        svg += f'<path d="{dd(L2[f"face{i}"])}" fill="url(#g{i})"/>'
    for key, fill in (("sil", "#000"), ("band", "url(#mcb)"), ("face", "url(#mcf)"), ("crack", "#86817b"), ("fleck", "#fff")):
        svg += f'<path d="{_d(DATA[key])}" fill="{fill}" fill-rule="evenodd"/>'
    Path(path).write_text(svg + "</svg>", encoding="utf-8")


if __name__ == "__main__":
    make(Path(__file__).resolve().parents[2] / "site/assets/img/minecraft.svg")
    make_full(Path(__file__).resolve().parents[2] / "site/assets/img/logo.svg")
