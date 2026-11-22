"""Builds the wiki in site/ (the GitHub Pages site): renders, item icons and the HTML pages.

    python tools/wiki/build.py [--jar path/to/minecraft client.jar]

The pages are generated from the data in this file (golem facts) plus the mod's own files (textures, GolemModels.java), so the
renders cannot drift from the game; the facts below are written by hand and must be kept in step with the code (CLAUDE.md).
The jar is the vanilla Minecraft client the dev environment already downloaded (found in the Gradle cache); its item sprites
are only used to draw the small item icons.
"""
import argparse
import html
import json
import sys
from pathlib import Path

import render as r
from PIL import Image

SITE = r.ROOT / "site"
REPO = "https://github.com/scootfoundation/industrious-golems"
MCWIKI = "https://minecraft.wiki/w/"
LANG_NAMES = {}


def find_jar():
    base = Path.home() / ".gradle/caches/ng_execute"
    jars = sorted((j for j in base.glob("*/client.jar")), key=lambda j: j.stat().st_size, reverse=True)
    for j in jars:
        import zipfile
        names = zipfile.ZipFile(j).namelist()
        if "assets/minecraft/items/slime_ball.json" in names and "assets/minecraft/items/cactus_flower.json" in names:
            return j
    sys.exit("no Minecraft client jar found: pass --jar")


# ------------------------------------------------------------------ item catalogue

# id -> (display name, minecraft.wiki page). Only what differs from the jar's name needs a page; the name comes from the jar's lang file.
PAGE = {
    "oak_log": "Log", "oak_sapling": "Sapling", "white_wool": "Wool", "snort_pottery_sherd": "Pottery_Sherd",
    "red_mushroom_block": "Mushroom_Block", "terracotta": "Terracotta", "disc_fragment_5": "Disc_Fragment_5",
    "carved_pumpkin": "Carved_Pumpkin", "jack_o_lantern": "Jack_o'Lantern", "hay_block": "Hay_Bale",
    "cobblestone": "Cobblestone", "granite": "Granite", "pumpkin": "Pumpkin", "powder_snow_bucket": "Bucket",
    "milk_bucket": "Milk_Bucket", "goat_horn": "Goat_Horn", "wheat_seeds": "Wheat_Seeds",
    "cobbled_deepslate": "Cobbled_Deepslate",
}
LABEL = {"oak_log": "Any log", "oak_sapling": "Sapling", "white_wool": "Wool", "snort_pottery_sherd": "Any pottery sherd",
         "red_mushroom_block": "Red Mushroom Block", "wheat_seeds": "Seeds", "terracotta": "Terracotta (any colour)"}


def name(item):
    if item in LABEL:
        return LABEL[item]
    return LANG_NAMES.get("item.minecraft." + item) or LANG_NAMES.get("block.minecraft." + item) or item.replace("_", " ").title()


def page(item):
    return PAGE.get(item) or name(item).replace(" ", "_")


def slot(item, label=False, hint=None):
    """An inventory slot linking to the Minecraft wiki, with the game's tooltip; optionally with the name (and a hint) beside it."""
    n = html.escape(name(item))
    tip = '<span class="tip"><b>%s</b>%s</span>' % (n, '<i>%s</i>' % html.escape(hint) if hint else "")
    s = ('<a class="invslot" href="%s%s"><img src="{root}assets/items/%s.png" alt="%s">%s</a>' % (MCWIKI, page(item), item, n, tip))
    if not label:
        return s
    extra = '<span class="lore">%s</span>' % html.escape(hint) if hint else ""
    return ('<span class="entry">%s<span class="entry-text"><a href="%s%s">%s</a>%s</span></span>'
            % (s, MCWIKI, page(item), n, extra))


def wiki(text, item=None, page_name=None):
    return '<a href="%s%s">%s</a>' % (MCWIKI, page_name or page(item), text)


# ------------------------------------------------------------------ the golems

GOLEMS = [
    dict(
        id="hay", slug="hay-golem", title="Hay Golem", role="Farmer", tagline="A slow, patient scarecrow that tends your crops.",
        texture="hay_golem", chest="hay", job="Hay Chest", body="hay_block", job_vanilla="composter",
        recipe=["chest", "composter", "hay_block"],
        consumes=[("wheat_seeds", "Seeds, carrots, potatoes and the other plantable crops")],
        produces=["wheat", "carrot", "potato", "beetroot", "melon_slice", "pumpkin", "sweet_berries"],
        offerings=[("slime_ball", "slimes"), ("glow_ink_sac", "glow squid"), ("pufferfish", "warm oceans"), ("cobweb", "mineshafts"),
                   ("rabbit_foot", "rabbits"), ("cactus_flower", "cacti"), ("pointed_dripstone", "dripstone caves"),
                   ("glow_berries", "lush caves")],
        summary="The Hay Golem is the farmer. It harvests ripe crops from soil you have tilled and sows the next generation, "
                "carrying the harvest to its Hay Chest one stack at a time.",
        behaviour=[
            "It works the tilled farmland within <strong>10 blocks</strong> of its chest (and two blocks up and down). It never tills soil itself: "
            "you prepare the plots and plant the first generation.",
            "It walks onto a ripe crop and breaks it, then onto the bare farmland to sow whatever seeds it is holding. It will not sow things that are not seeds.",
            "It is deliberately slow: it plods about at well under the pace of the other golems.",
            "Seeds come from the chest. When it runs out it waits, so keep some seeds in the Hay Chest.",
        ],
        notes=["Hoppers and water streams work fine around it: it only ever picks up the crops it works with.",
               "The vanilla composter still counts as its job site if you have no Hay Chest."],
        tips=[],
    ),
    dict(
        id="mushroom", slug="mushroom-golem", title="Mushroom Golem", role="Lumberjack", tagline="Fells trees and replants the grove as it goes.",
        texture="mushroom_golem", chest="mushroom", job="Mushroom Chest", body="red_mushroom_block", job_vanilla="fletching_table",
        recipe=["chest", "fletching_table", "red_mushroom_block"],
        consumes=[("oak_sapling", "Saplings, a few at a time")],
        produces=["oak_log", "oak_sapling", "stick", "apple"],
        offerings=[("amethyst_shard", "geodes"), ("resin_clump", "pale gardens"), ("spore_blossom", "lush caves"), ("pink_petals", "cherry groves"),
                   ("goat_horn", "goats"), ("armadillo_scute", "armadillos"), ("honey_bottle", "bee nests"), ("open_eyeblossom", "pale gardens")],
        summary="The Mushroom Golem is the lumberjack. It fells the natural trees around its chest, log by log, and plants saplings "
                "so the grove keeps growing.",
        behaviour=[
            "It looks for trees within <strong>16 blocks</strong> of its chest and fells them log by log, including branches and extra trunks. "
            "Logs, sticks, apples and saplings are carried to the chest.",
            "It <strong>ignores logs you have built with</strong> (and stripped logs), so your house is safe.",
            "It replants within <strong>14 blocks</strong> of the chest, keeping saplings at least <strong>5 blocks apart</strong> so trees have room, "
            "and leaves a <strong>6-block clearing</strong> around the chest so the chest never gets walled in.",
            "It only plants saplings it has been given or has collected: it never conjures them. A lone dark oak sapling is left alone, because it cannot grow without room for four.",
            "If the chest is full it sits down with the stack it is holding, and gets up again once there is room.",
        ],
        notes=["To start a grove, put a handful of saplings in the chest. The golem tops up its supply from there."],
        tips=[],
    ),
    dict(
        id="terracotta", slug="terracotta-golem", title="Terracotta Golem", role="Herder", tagline="Shears, milks and keeps your pens at a sensible size.",
        texture="terracotta_golem", chest="terracotta", job="Terracotta Chest", body="terracotta", job_vanilla="loom",
        recipe=["chest", "loom", "terracotta"],
        consumes=[("bucket", "Buckets (each milking turns one into milk)"), ("wheat", "Animal feed")],
        produces=["white_wool", "milk_bucket", "beef", "porkchop", "chicken", "mutton", "leather", "feather", "egg"],
        offerings=[("turtle_scute", "baby turtles"), ("sea_pickle", "warm oceans"), ("cocoa_beans", "jungles"), ("mangrove_propagule", "mangrove swamps"),
                   ("powder_snow_bucket", "powder snow"), ("snort_pottery_sherd", "archaeology: any sherd works"), ("shroomlight", "the Nether"),
                   ("big_dripleaf", "lush caves")],
        summary="The Terracotta Golem is the herder. It shears sheep, milks cows and looks after the animals in the pens near its chest.",
        behaviour=[
            "It works the <strong>enclosed pens</strong> (up to 64 floor blocks) with animals in them within <strong>16 blocks</strong> of its chest. "
            "A pen with a gap in the fence is not a pen.",
            "It shears sheep and milks cows (a cow can be milked about every five minutes), turning each bucket from the chest into a bucket of milk.",
            "It keeps herds to a sensible size: it <strong>breeds</strong> a pen that has more than four floor blocks per animal, using feed from the chest, "
            "and <strong>culls</strong> one that is crowded below 3.5. It never culls a species below two adults.",
            "Wool, milk, meat, leather, feathers and eggs go to the chest.",
        ],
        notes=["Give it plenty of feed and buckets: without feed it cannot breed, without buckets it cannot milk."],
        tips=[],
    ),
    dict(
        id="stone", slug="stone-golem", title="Stone Golem", role="Miner", tagline="Quarries stone and ore, one block at a time.",
        texture="stone_golem", chest="stone", job="Stone Chest", body="smooth_stone", job_vanilla="stonecutter",
        recipe=["chest", "stonecutter", "smooth_stone"],
        consumes=[],
        produces=["cobblestone", "granite", "diorite", "andesite", "tuff", "cobbled_deepslate", "coal", "raw_iron", "raw_copper", "raw_gold", "redstone", "lapis_lazuli", "diamond", "emerald"],
        offerings=[("nautilus_shell", "drowned, fishing"), ("prismarine_crystals", "ocean monuments"), ("echo_shard", "ancient cities"), ("disc_fragment_5", "ancient cities"),
                   ("chorus_fruit", "the End"), ("phantom_membrane", "phantoms"), ("ghast_tear", "ghasts"), ("breeze_rod", "breezes")],
        summary="The Stone Golem is the miner. Put its chest where you want a quarry and it will cut the rock and ore around it.",
        behaviour=[
            "It digs rock that is <strong>already exposed to air</strong> within <strong>7 blocks</strong> of its chest, working inward from the faces it can see "
            "rather than boring through the ground.",
            "It never digs <strong>below the chest's level</strong> and only up to two blocks above it: it cuts a tunnel or a quarry face, not a pit it could fall into.",
            "It will not break a block that would let a liquid in or bring sand or gravel down on it.",
            "Ore is taken before plain stone, and drops what an <strong>iron pickaxe</strong> would give: ore it cannot harvest properly is left alone.",
            "It digs a dozen blocks, then collects the drops and takes them to the chest. It needs no supplies.",
        ],
        notes=["Place the chest at the height you want to mine."],
        tips=[],
    ),
]

BUILD_HOWTO = ("Place the golem's body block, then put a <strong>carved pumpkin</strong> or <strong>jack o'lantern</strong> on top. "
               "The pumpkin is consumed, the body block becomes the golem's chest, and the golem stands on it. "
               "You do not need a chest first: the pattern makes one. The golem is bound to that chest and starts <em>dormant</em>.")


# ------------------------------------------------------------------ rendering

def crop(im, pad=8):
    """Trim transparent margins (the scale is untouched, so golems keep their relative sizes)."""
    l, t, rr, b = im.getbbox()
    return im.crop((max(l - pad, 0), max(t - pad, 0), min(rr + pad, im.width), min(b + pad, im.height)))


def build_images(vanilla, items):
    img = SITE / "assets/img"
    img.mkdir(parents=True, exist_ok=True)
    (SITE / "assets/items").mkdir(parents=True, exist_ok=True)
    s = 7.5  # one scale for every golem so they stand at their true relative sizes
    for g in GOLEMS:
        sc = r.golem_scene(g["texture"].replace("_golem", ""), r.MOD_ASSETS / ("textures/entity/%s.png" % g["texture"]))
        crop(sc.render(400, yaw=-30, pitch=18, scale=s)[0]).save(img / ("%s.png" % g["slug"]))
        ch = r.chest_scene(r.MOD_ASSETS / ("textures/entity/chest/%s.png" % g["chest"]))
        crop(ch.render(320, yaw=-30, pitch=25, scale=11)[0]).save(img / ("%s-chest.png" % g["slug"]))
    for item in sorted(items):
        r.slot_icon(vanilla, item).save(SITE / "assets/items" / ("%s.png" % item))




def build_extras(vanilla):
    """Images that are not items: the crafting-grid pieces, each chest as an inventory item, the page background."""
    img = SITE / "assets/img"
    r.slot_image().save(SITE / "assets/items/_empty.png")
    for g in GOLEMS:
        ch = r.chest_scene(r.MOD_ASSETS / ("textures/entity/chest/%s.png" % g["chest"]))
        r.slot_image(r.scene_icon(ch)).save(SITE / "assets/items" / ("%s_chest.png" % g["id"]))
    # the crafting arrow, as a 22x15 pixel image
    a = Image.new("RGBA", (22, 15), (0, 0, 0, 0))
    px = a.load()
    for x in range(0, 14):
        for y in range(5, 10):
            px[x, y] = (139, 139, 139, 255)
    for k in range(8):
        for y in range(k, 15 - k):
            px[13 + k, y] = (139, 139, 139, 255)
    a.resize((22 * 3, 15 * 3), Image.NEAREST).save(img / "arrow.png")
    # the page background: the dirt block texture, darkened and tiled like the game's option screens
    dirt = vanilla.texture("block/dirt").resize((64, 64), Image.NEAREST)
    dark = Image.new("RGBA", dirt.size, (0, 0, 0, 150))
    dirt.alpha_composite(dark)
    dirt.save(img / "bg.png")


# ------------------------------------------------------------------ pages

NAV_FONT = "https://fonts.googleapis.com/css2?family=Pixelify+Sans:wght@400;600;700&family=Press+Start+2P&display=swap"


def css_version():
    """A short hash of the stylesheet, so a changed stylesheet is never served from a browser's cache against new pages."""
    import hashlib
    return hashlib.md5((SITE / "assets/style.css").read_bytes()).hexdigest()[:8]


def layout(title, body, root, active, description, splash=None):
    nav = "".join('<a class="mcbtn%s" href="%sgolems/%s.html">%s</a>' % (" on" if active == g["slug"] else "", root, g["slug"], g["title"].replace(" Golem", ""))
                  for g in GOLEMS)
    return """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>%(title)s</title>
<meta name="description" content="%(description)s">
<link rel="icon" href="%(root)sassets/img/favicon.png">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="%(font)s">
<link rel="stylesheet" href="%(root)sassets/style.css?v=%(ver)s">
</head>
<body>
<header class="title">
  <a class="logo" href="%(root)sindex.html"><span>Industrious</span><span>Golems</span></a>
  <nav class="menu"><a class="mcbtn%(home)s" href="%(root)sindex.html">Home</a>%(nav)s<a class="mcbtn" href="%(repo)s">GitHub</a></nav>
</header>
<main class="gui">
%(body)s
</main>
<footer class="foot">Industrious Golems is a fan-made <a href="https://neoforged.net/">NeoForge</a> mod and is not affiliated with Mojang or Microsoft.
Item icons are drawn from Minecraft's own textures; item links go to the <a href="https://minecraft.wiki/">Minecraft Wiki</a>.
This wiki is generated by <code>tools/wiki</code> in the <a href="%(repo)s">repository</a>.</footer>
</body>
</html>
""" % dict(title=html.escape(title), description=html.escape(description), root=root, nav=nav, body=body.replace("{root}", root), repo=REPO,
           home=" on" if active == "home" else "", font=NAV_FONT, ver=css_version())


def crafting(g):
    """The chest recipe as a crafting table shows it (shapeless, so the ingredients fill the first cells)."""
    cells = [slot(i) for i in g["recipe"]] + ['<span class="invslot blank"><img src="{root}assets/items/_empty.png" alt=""></span>'] * (9 - len(g["recipe"]))
    result = ('<span class="invslot"><img src="{root}assets/items/%s_chest.png" alt="%s">'
              '<span class="tip"><b>%s</b><i>Shapeless</i></span></span>' % (g["id"], g["job"], g["job"]))
    return ('<div class="crafting"><div class="craft-title">Crafting</div><div class="craft-body">'
            '<div class="grid3">%s</div><img class="arrow" src="{root}assets/img/arrow.png" alt="makes">%s</div></div>' % ("".join(cells), result))


def golem_page(g):
    offer = "".join("<li>%s</li>" % slot(i, True, "Found: " + h) for i, h in g["offerings"])
    made = "".join("<li>%s</li>" % slot(i, True) for i in g["produces"])
    uses = "".join("<li>%s</li>" % slot(i, True, t) for i, t in g["consumes"])
    uses_text = "; ".join(t for _, t in g["consumes"]) or "Nothing: it digs bare-handed."
    behaviour = "".join("<li>%s</li>" % t for t in g["behaviour"])
    notes = "".join("<li>%s</li>" % t for t in g["notes"])
    others = "".join('<a class="card small" href="%s.html"><span class="view"><img src="{root}assets/img/%s.png" alt=""></span><span class="cardname">%s</span></a>'
                     % (o["slug"], o["slug"], o["title"]) for o in GOLEMS if o is not g)
    summary = g["summary"].replace("The " + g["title"], "The <strong>%s</strong>" % g["title"], 1)
    body = """
<article>
<h1>%(title)s</h1>
<p class="role">%(role)s</p>
<aside class="infobox">
  <div class="ib-title">%(title)s</div>
  <div class="view"><img src="{root}assets/img/%(slug)s.png" alt="Render of the %(title)s"></div>
  <div class="view chest"><img src="{root}assets/img/%(slug)s-chest.png" alt="Render of the %(job)s"></div>
  <div class="ib-cap">%(job)s</div>
  <table>
    <tr><th>Role</th><td>%(role)s</td></tr>
    <tr><th>Job site</th><td>%(job)s (or a vanilla %(jobv)s)</td></tr>
    <tr><th>Built from</th><td>%(bodylink)s + %(pumpkin)s</td></tr>
    <tr><th>Uses</th><td>%(uses_text)s</td></tr>
    <tr><th>Charge</th><td>45 minutes per offering</td></tr>
  </table>
</aside>
<p class="lead">%(summary)s</p>

<h2 id="building">Building a %(title)s</h2>
<p>%(howto)s</p>
<p>The chest is also craftable, so you can move or replace it. It has 27 slots and never joins into a double chest.</p>
%(crafting)s

<h2 id="offerings">Waking it up</h2>
<p>A golem starts <em>dormant</em>: head hanging, standing still, holding out the thing it wants in its off hand. Right-click it with that item
to give it an <strong>offering</strong>. Each offering is worth <strong>45 minutes</strong> of work and it stores at most two (90 minutes).
It then asks for something different, so no single item will do. The clock runs only while the golem is loaded and has a job site.</p>
<p>These are the items it can ask for. They are deliberately unrelated to its job, so keeping a large operation going means going out exploring.</p>
<ul class="entries">%(offer)s</ul>

<h2 id="behaviour">What it does</h2>
<ul>%(behaviour)s</ul>
<p>Like every golem it <strong>carries one stack at a time</strong>: it has no inventory, picks things up in its hand, takes them to its chest and goes back for the next.
Supplies come out of the chest the same way.</p>

<h3>It produces</h3>
<ul class="entries">%(made)s</ul>
%(usesblock)s
<h2 id="notes">Notes</h2>
<ul>%(notes)s<li>A golem prefers its own chest to any other chest nearby.</li></ul>

<h2>Other golems</h2>
<div class="cards">%(others)s</div>
</article>
""" % dict(
        title=g["title"], role=g["role"], slug=g["slug"], job=g["job"], jobv=wiki(name(g["job_vanilla"]).lower(), g["job_vanilla"]),
        bodylink=wiki(name(g["body"]).lower(), g["body"]), pumpkin=wiki("carved pumpkin", "carved_pumpkin"),
        uses_text=uses_text, summary=summary, howto=BUILD_HOWTO, crafting=crafting(g),
        offer=offer, behaviour=behaviour, made=made,
        usesblock=("<h3>It uses</h3><ul class=\"entries\">%s</ul>" % uses) if uses else "", notes=notes, others=others)
    return layout("%s - Industrious Golems Wiki" % g["title"], body, "../", g["slug"], g["tagline"] + " " + g["summary"])


def home_page():
    cards = "".join('<a class="card" href="golems/%s.html"><span class="view"><img src="assets/img/%s.png" alt="%s"></span><span class="cardname">%s</span><span class="lore">%s &middot; %s</span></a>'
                    % (g["slug"], g["slug"], g["title"], g["title"], g["role"], g["tagline"]) for g in GOLEMS)
    rows = "".join('<div class="row"><a class="rowname" href="golems/%s.html">%s<span class="lore">%s</span></a>'
                   '<div class="cell"><span class="label">Body</span>%s</div>'
                   '<div class="cell"><span class="label">Uses</span>%s</div>'
                   '<div class="cell"><span class="label">Produces</span>%s</div></div>' % (
                       g["slug"], g["title"], g["role"], slot(g["body"]),
                       "".join(slot(i) for i, _ in g["consumes"]) or '<span class="lore">nothing</span>',
                       "".join(slot(i) for i in g["produces"][:7])) for g in GOLEMS)
    body = """
<article>
<p class="lead"><strong>Industrious Golems</strong> is a NeoForge mod that adds <strong>work golems</strong>: golems that hold a job and produce resources directly,
while you build the infrastructure around them (chests, hoppers, furnaces, crafters, minecarts) from ordinary vanilla blocks.
Villagers, trading and iron golems are left exactly as they are.</p>
<div class="cards hero">%(cards)s</div>

<h2 id="how">How golems work</h2>
<h3>Building one</h3>
<p>%(howto)s</p>
<h3>The chest</h3>
<p>Each golem has its own <strong>27-slot chest</strong>, which is its job site. It deposits what it produces there and restocks its supplies from it. The chest is craftable
(a %(chest)s, the golem's old job block and its body block) so you can move one, and it never joins into a double chest.</p>
<h3>Charge and offerings</h3>
<p>Golems work bare-handed, day and night, but only while <strong>charged</strong>. A new golem is dormant until you give it something it craves: right-click it with the item.
Each kind has <strong>eight offerings</strong>, uncommon finds from different biomes, structures and mobs, and it craves one at a time. One offering is worth <strong>45 minutes</strong>
of work; a golem stores two. The more golems you run, the more offerings you need, which means going out exploring.</p>
<h3>One stack at a time</h3>
<p>A golem has no inventory. It breaks a block, shears or milks, and the result drops in the world as it would for a player. It picks up <strong>one stack in its hand</strong>,
carries it to its chest and goes back for the next. Supplies (seeds, saplings, buckets, feed) come out of the chest the same way.</p>

<h2 id="golems">The golems</h2>
<div class="rows">%(rows)s</div>

<h2 id="about">About this wiki</h2>
<p>The source and issue tracker are on <a href="%(repo)s">GitHub</a>. Item links go to the <a href="https://minecraft.wiki/">Minecraft Wiki</a>.</p>
</article>
""" % dict(cards=cards, howto=BUILD_HOWTO, chest=wiki("chest", "chest"), repo=REPO, rows=rows)
    return layout("Industrious Golems Wiki", body, "", "home",
                  "Work golems for Minecraft (NeoForge): a farmer, a lumberjack, a herder and a miner.")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar")
    args = ap.parse_args()
    jar = Path(args.jar) if args.jar else find_jar()
    vanilla = r.Vanilla(jar)
    LANG_NAMES.update(json.loads(vanilla.zip.read("assets/minecraft/lang/en_us.json")))
    items = {"chest", "carved_pumpkin"}
    for g in GOLEMS:
        items.update([g["body"], g["job_vanilla"]] + g["recipe"] + g["produces"] + [i for i, _ in g["consumes"]] + [i for i, _ in g["offerings"]])
    build_images(vanilla, items)
    build_extras(vanilla)
    fav = Image.open(r.MOD_ASSETS / "textures/item/hay_golem_spawn_egg.png").convert("RGBA")
    fav.save(SITE / "assets/img/favicon.png")
    (SITE / "golems").mkdir(exist_ok=True)
    (SITE / "index.html").write_text(home_page(), encoding="utf-8")
    for g in GOLEMS:
        (SITE / "golems" / ("%s.html" % g["slug"])).write_text(golem_page(g), encoding="utf-8")
    (SITE / ".nojekyll").write_text("")
    print("built", len(GOLEMS), "golem pages and", len(items), "icons into", SITE)


if __name__ == "__main__":
    main()
