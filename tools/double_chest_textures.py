"""Makes the double-chest halves (<name>_left.png, <name>_right.png) from each golem chest's single texture.

A double half is 15 pixels wide instead of 14, so every face that runs along the chest's width gets one extra column (the
middle column repeated); the faces on the ends keep their size. Layout follows the vanilla chest_left/chest_right textures.
Run from the repo root: python tools/double_chest_textures.py
"""
from pathlib import Path
from PIL import Image

DIR = Path("src/main/resources/assets/industriousgolems/textures/entity/chest")
D, H_LID, H_BASE = 14, 5, 10


def widen(src, box):
    """Crops a 14-wide face out of src and returns it 15 wide, repeating the middle column."""
    x, y, w, h = box
    face = src.crop((x, y, x + w, y + h))
    out = Image.new("RGBA", (w + 1, h))
    out.paste(face.crop((0, 0, w // 2, h)), (0, 0))
    out.paste(face.crop((w // 2 - 1, 0, w // 2, h)), (w // 2, 0))
    out.paste(face.crop((w // 2, 0, w, h)), (w // 2 + 1, 0))
    return out


def make(name):
    src = Image.open(DIR / f"{name}.png").convert("RGBA")
    out = Image.new("RGBA", (64, 64))
    for v, h in ((0, H_LID), (19, H_BASE)):
        # single layout: top (14,v) bottom (28,v) | west (0,v+14) front (14,v+14) east (28,v+14) back (42,v+14)
        out.paste(widen(src, (14, v, 14, 14)), (14, v))
        out.paste(widen(src, (28, v, 14, 14)), (29, v))
        out.paste(src.crop((0, v + 14, 14, v + 14 + h)), (0, v + 14))
        out.paste(widen(src, (14, v + 14, 14, h)), (14, v + 14))
        out.paste(src.crop((28, v + 14, 42, v + 14 + h)), (29, v + 14))
        out.paste(widen(src, (42, v + 14, 14, h)), (43, v + 14))
    # the latch: one pixel wide on each half (single latch is 2 wide: top (1,0) bottom (3,0) | west (0,1) front (1,1) east (3,1) back (4,1))
    lock = Image.new("RGBA", (64, 64))
    lock.paste(src.crop((1, 0, 2, 1)), (1, 0))
    lock.paste(src.crop((3, 0, 4, 1)), (2, 0))
    lock.paste(src.crop((0, 1, 1, 5)), (0, 1))
    lock.paste(src.crop((1, 1, 2, 5)), (1, 1))
    lock.paste(src.crop((3, 1, 4, 5)), (2, 1))
    lock.paste(src.crop((4, 1, 5, 5)), (3, 1))
    out.paste(lock.crop((0, 0, 5, 5)), (0, 0))
    out.save(DIR / f"{name}_left.png")
    out.save(DIR / f"{name}_right.png")


for n in ("hay", "mushroom", "terracotta", "stone"):
    make(n)
    print("made", n)
