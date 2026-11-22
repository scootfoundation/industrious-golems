"""Draws the wiki logo (site/assets/img/logo.svg): chunky block lettering in the style of the game's title screen,
a stone-grey "MINECRAFT" over a grassy "INDUSTRIOUS GOLEMS". Pure SVG, generated so the lettering can be tweaked in one place."""
import random

# 8 rows tall; 6 columns wide unless noted (M is 8). '#' is a block.
GLYPHS = {
    "I": ["######", "######", "..##..", "..##..", "..##..", "..##..", "######", "######"],
    "T": ["######", "######", "..##..", "..##..", "..##..", "..##..", "..##..", "..##.."],
    "E": ["######", "######", "##....", "####..", "####..", "##....", "######", "######"],
    "C": [".####.", "######", "##..##", "##....", "##....", "##..##", "######", ".####."],
    "N": ["##..##", "###.##", "######", "######", "##.###", "##..##", "##..##", "##..##"],
    "M": ["##....##", "###..###", "########", "########", "##.##.##", "##....##", "##....##", "##....##"],
    "R": ["#####.", "######", "##..##", "##..##", "#####.", "####..", "##.###", "##..##"],
    "A": [".####.", "######", "##..##", "##..##", "######", "######", "##..##", "##..##"],
    "F": ["######", "######", "##....", "####..", "####..", "##....", "##....", "##...."],
    "D": ["#####.", "######", "##..##", "##..##", "##..##", "##..##", "######", "#####."],
    "U": ["##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "######", ".####."],
    "S": [".#####", "######", "##....", ".####.", "..####", "....##", "######", "#####."],
    "O": [".####.", "######", "##..##", "##..##", "##..##", "##..##", "######", ".####."],
    "G": [".#####", "######", "##....", "##.###", "##.###", "##..##", "######", ".####."],
    "L": ["##....", "##....", "##....", "##....", "##....", "##....", "######", "######"],
}
GAP = 1      # blank columns between letters
SPACE = 3    # columns for a word space


def cells(text):
    """The set of (col, row) blocks of a line of text, and its width in columns."""
    out, x = set(), 0
    for ch in text:
        if ch == " ":
            x += SPACE
            continue
        g = GLYPHS[ch]
        for r, row in enumerate(g):
            for c, v in enumerate(row):
                if v == "#":
                    out.add((x + c, r))
        x += len(g[0]) + GAP
    return out, x - GAP


def runs(blocks):
    """Merge blocks into horizontal runs: [(col, row, length)]."""
    out = []
    for r in sorted({b[1] for b in blocks}):
        cols = sorted(c for c, rr in blocks if rr == r)
        start = prev = cols[0]
        for c in cols[1:] + [None]:
            if c is None or c != prev + 1:
                out.append((start, r, prev - start + 1))
                if c is not None:
                    start = c
            if c is not None:
                prev = c
    return out


def line(text, ox, oy, u, face, side, rng, depth=(0.55, 0.6)):
    """SVG for one line: black outline, extruded side, front face with bevel and stone speckle. `u` is the block size."""
    blocks, _ = cells(text)
    dx, dy = depth[0] * u, depth[1] * u
    steps = 4
    o = 0.22 * u  # outline thickness
    parts = []
    # 1. black silhouette (front plus everything swept to the extruded back)
    sil = []
    for (c, r, n) in runs(blocks):
        for s in range(steps + 1):
            t = s / steps
            sil.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f"/>' % (ox + c * u + dx * t - o, oy + r * u + dy * t - o, n * u + 2 * o, u + 2 * o))
    parts.append('<g fill="#000">%s</g>' % "".join(sil))
    # 2. the extruded side
    ext = []
    for (c, r, n) in runs(blocks):
        for s in range(1, steps + 1):
            t = s / steps
            ext.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f"/>' % (ox + c * u + dx * t, oy + r * u + dy * t, n * u + 0.4, u + 0.4))
    parts.append('<g fill="%s">%s</g>' % (side, "".join(ext)))
    # 3. the front face
    face_rects, hi, lo, spk = [], [], [], []
    for (c, r) in sorted(blocks):
        x, y = ox + c * u, oy + r * u
        b = max(u * 0.14, 1)
        if (c, r - 1) not in blocks:
            hi.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f"/>' % (x, y, u + 0.4, b))
        if (c - 1, r) not in blocks:
            hi.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f"/>' % (x, y, b, u + 0.4))
        if (c, r + 1) not in blocks:
            lo.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f"/>' % (x, y + u - b, u + 0.4, b))
        if (c + 1, r) not in blocks:
            lo.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f"/>' % (x + u - b, y, b, u + 0.4))
        # stone speckle: a couple of small darker/lighter pixels per block
        for _ in range(2):
            if rng.random() < 0.7:
                px = x + rng.randrange(1, 6) * u / 8
                py = y + rng.randrange(1, 6) * u / 8
                col = "#00000022" if rng.random() < 0.6 else "#ffffff26"
                spk.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" fill="%s"/>' % (px, py, u / 4, u / 4, col))
    face_rects = ['<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f"/>' % (ox + c * u, oy + r * u, n * u + 0.4, u + 0.4) for (c, r, n) in runs(blocks)]
    parts.append('<g fill="url(#%s)">%s</g>' % (face, "".join(face_rects)))
    parts.append('<g fill="#ffffff55">%s</g>' % "".join(hi))
    parts.append('<g fill="#00000040">%s</g>' % "".join(lo))
    parts.append("".join(spk))
    return "".join(parts)


def make(path):
    rng = random.Random(7)
    top, bottom = "MINECRAFT", "INDUSTRIOUS GOLEMS"
    _, w1 = cells(top)
    _, w2 = cells(bottom)
    u1 = 10.0
    width = w1 * u1
    u2 = width / w2           # the second line is as wide as the first, so its blocks are smaller
    pad = 12
    h1, h2 = 8 * u1, 8 * u2
    gap = 14
    H = pad + h1 + gap + h2 + pad + 8
    W = width + 2 * pad + 8
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %.0f %.0f" shape-rendering="crispEdges" role="img" aria-label="Minecraft: Industrious Golems">' % (W, H),
           '<title>Industrious Golems</title>',
           '<defs>'
           '<linearGradient id="stone" gradientUnits="userSpaceOnUse" x1="0" y1="%.1f" x2="0" y2="%.1f"><stop offset="0" stop-color="#d4d4d4"/><stop offset="0.55" stop-color="#b3b3b3"/><stop offset="1" stop-color="#8e8e8e"/></linearGradient>'
           '<linearGradient id="grass" gradientUnits="userSpaceOnUse" x1="0" y1="%.1f" x2="0" y2="%.1f"><stop offset="0" stop-color="#b6e86e"/><stop offset="0.5" stop-color="#7cc03e"/><stop offset="1" stop-color="#4c8a26"/></linearGradient>'
           '</defs>' % (pad, pad + h1, pad + h1 + gap + u1 * 0.6, pad + h1 + gap + u1 * 0.6 + h2)]
    svg.append(line(top, pad, pad, u1, "stone", "#4d4d4d", rng))
    svg.append(line(bottom, pad, pad + h1 + gap + u1 * 0.6, u2, "grass", "#2b5516", rng, depth=(0.55, 0.6)))
    svg.append("</svg>")
    with open(path, "w", encoding="utf-8") as f:
        f.write("".join(svg))


if __name__ == "__main__":
    make("logo.svg")
