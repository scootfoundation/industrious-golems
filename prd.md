# Industrious Golems — Product Requirements Document (Draft v3)

## Overview

*Industrious Golems* adds work golems: golems that hold a job and produce resources directly, while the player builds the surrounding infrastructure (chests, hoppers, furnaces, crafters, minecarts) using vanilla blocks. Villagers are left exactly as in vanilla (trading and all); the golems do the labour.

Two value propositions:

1. **Adds productive automation that doesn't exist in vanilla** — auto-farming, auto-logging, herding and mining — replacing the fragile-redstone-contraption meta with a clean role-based answer.
2. **Leaves the rest of the game alone** — no changes to villager trading, professions or schedules.

Each new role retires a category of menial player labour, lifting the player up the abstraction ladder (à la Factorio). Player work shifts from manual labour to systems-thinking: layout, logistics, defense, expansion.

**Target loader:** NeoForge.

## Goals

- Provide automation that doesn't exist in vanilla (no role overlaps with what crafters, hoppers, and furnaces already enable).
- Create cross-chain resource dependencies that produce scaling pressure.
- Leverage existing Minecraft systems for everything that isn't the role behaviour itself.
- Reuse existing job site blocks, items and sounds; the golems' own models and textures are the only new art.

## Player Experience

After a vanilla bootstrap (gather wood, craft stone tools), the player places a composter, spawns a hay golem next to it and wakes it with an offering. They till a plot; the golem takes over planting and harvesting. The player adds a mushroom golem (fletching table within a grove) and a terracotta golem (loom beside a pen), sets up vanilla auto-smelters and a recipe-locked crafter, and the loop closes. The player's last hand-crafted tool is the **graduation moment**: from here they design infrastructure rather than swinging tools.

Scaling generates the gameplay. More golems need more offerings, and offerings are uncommon finds from all over the world, so running a bigger operation means going out and exploring: new biomes, structures and mobs.

## Golems

All golems share:

- **Built like a vanilla golem:** each golem has its own pattern of blocks, with a carved pumpkin or jack o'lantern placed on top last. The pumpkin and all but the lowest block go, the lowest block becomes the golem's **chest**, and the golem stands on it. No chest is needed to start; the pattern creates one. The golem is bound to that chest as its job site and starts dormant, ready to work as soon as it is given what it craves. Spawn eggs stay in the creative tab.
- **The chest** is a 27-slot chest of the golem's own design. It is the golem's job site and the place it deposits output and restocks supplies, and it is preferred over any other nearby chest. It is craftable (vanilla chest + the old job site block + the body block) so a chest can be re-placed or moved. Chests never join into double chests.
- Claim the nearest unclaimed job site of their kind (within 48 blocks) and stay near it. A golem's own chest is its job site; the vanilla block it used to use (composter, fletching table, loom) still counts as a fallback.
- Work bare-handed, day and night, but only while **charged**. A golem starts dormant (head hanging, standing still) and does nothing until a player gives it what it currently craves.
- Each golem kind has eight **offerings**: uncommon finds from different biomes, structures and mobs, deliberately unrelated to the golem's trade and never something it produces itself. It craves one at a time (shown on its status plate and in its screen); one offering is worth 45 minutes of work, and after it is accepted the golem craves something different, so no single item is enough. It stores at most two offerings' worth (90 minutes). The clock runs only while the golem is loaded and has a job site.
- Give an offering by right-clicking the golem with it. A dormant golem holds out what it craves in its off hand; there is no inventory screen and no status plate.
- **Carry one stack at a time.** A golem has no inventory. It breaks a block (or shears, milks, culls) and the result drops in the world as it would for a player. The golem picks up one stack in its hand, takes it to its chest, and goes back for the next. Supplies (seeds, saplings, buckets, feed) come from the chest the same way, a stack at a time, and are used from the hand.

### Hay golem (farmer)

- **Job site:** Hay chest. **Built from:** pumpkin on a fence (any kind) on a hay bale.
- **Consumes:** Villager-plantable seeds and crops (wheat seeds, carrots, potatoes, beetroot seeds, ...).
- **Wants:** Slimeball, glow ink sac, pufferfish, cobweb, rabbit's foot, cactus flower, pointed dripstone, glow berries.
- **Produces:** Wheat, carrots, potatoes, beetroot, melon, pumpkins and sweet berries from crops the player has planted on pre-tilled soil within range.
- **Behaviour:** Works the pre-tilled farmland within 10 blocks of its chest (2 blocks up and down): walks onto a ripe crop and breaks it, then onto bare farmland to sow the seeds it carries. Never tills, and never sows anything that is not a seed. Deliberately slow: it plods. Player provides the first generation of seeds.

### Mushroom golem (lumberjack)

- **Job site:** Mushroom chest. **Built from:** red mushroom block + pumpkin.
- **Consumes:** Saplings (handed over a few at a time; what it does not plant is carried back).
- **Wants:** Amethyst shard, resin clump, spore blossom, pink petals, goat horn, armadillo scute, honey bottle, open eyeblossom.
- **Produces:** Logs, saplings, sticks, apples.
- **Behaviour:** Works a grove around the job site. Finds natural trees within 16 blocks and fells them log by log (branches and extra trunks included). Replants within 14 blocks of the chest with saplings at least 5 blocks apart and a 6-block clearing round the chest, so the chest is never walled in. Only plants saplings it has been given or collected, and leaves lone dark oak saplings alone. Ignores player-built and stripped logs. When its chest is full it sits down holding its stack, and stands up when there is room.

### Terracotta golem (herder)

- **Job site:** Terracotta chest. **Built from:** pumpkin on two terracotta (plain or coloured, need not match).
- **Consumes:** Buckets (turned into milk), animal feed.
- **Wants:** Turtle scute, sea pickle, cocoa beans, mangrove propagule, powder snow bucket, any pottery sherd, shroomlight, big dripleaf.
- **Produces:** Wool, milk, meat and hides, kept to a target herd size.
- **Behaviour:** Manages enclosed pens (up to 64 floor blocks) within 16 blocks of its chest. Shears sheep, milks cows (each cow at most every 5 minutes), culls a pen below 3.5 floor blocks per animal (never below two adults of a species) and keeps breeding with feed from the chest until one animal fewer would not be crowded, so a pen at capacity is bred and culled continuously; it always breeds a pen of fewer than three animals.

### Stone golem (miner)

- **Job site:** Stone chest (mossy stone bricks with an iron latch), placed at the level the player wants to mine. **Built from:** pumpkin on a T of smooth stone, like an iron golem: a row of three with one more under the middle (the chest is craftable from a chest, a stonecutter and smooth stone).
- **Consumes:** Fences, planks (any wood), torches and cobblestone, kept in a pack of up to 16 fences, 12 planks, 8 torches and 16 cobblestone, topped up from the chest in one visit. It digs bare-handed.
- **Wants:** Nautilus shell, prismarine crystals, echo shard, disc fragment 5, chorus fruit, phantom membrane, ghast tear, breeze rod.
- **Produces:** Cobblestone, cobbled deepslate, granite, diorite, andesite, tuff and the ores (coal, raw iron, raw copper, raw gold, redstone, lapis, diamond, emerald), as an iron pickaxe would drop them.
- **Behaviour:** The chest sits on the floor of the mine; everything is cut from its level up. The golem digs a 5 x 5 hub room, 4 high, around the chest, then a 3 x 3 trunk from the middle of each wall in each cardinal direction, up to 64 slices each (four chunks, so a whole mine stays inside what a nearby player keeps loaded). Arms are worked a section of five slices at a time, least advanced first, so the mine grows as a plus. Every fifth slice is a support (a fence post each side two high, a plank beam across) and the slice two on from it has a torch on the floor; the hub's corners are torched. Missing floor is bridged with cobblestone when the gap is at most five slices. It never starts a slice whose kit it cannot carry, and waits (idles) when the chest cannot supply it. A trunk ends at liquid, a falling block over or in the way, a long drop, or a block it does not dig; it leaves caves it opens into alone. Where it is now: planned from the world, nothing remembered. It digs a batch of up to 12 blocks, then carries the drops to the chest.

- **Veins and minerals:** Ore showing in a wall, ceiling or cave beside the mine (within 5 blocks of one of its axes, never the floor) is dug before plain rock; each block dug exposes the next, so a vein is followed as far as the golem can reach it. It also digs nether quartz and gold ore, amethyst and calcite, clay, dirt, deepslate, basalt, blackstone, netherrack and dripstone. It leaves ancient debris and obsidian to the player.

- **Planned, not built yet:** sealing liquids with cobblestone; clearing sand and gravel carefully and propping the ceiling; and fleeing mobs to the chest, covering its head and shaking, resuming when they are gone.

## Villagers

Unchanged from vanilla, including trading, professions, schedules, breeding, iron golems and raids.

## Open Questions

- **Behaviour when a miner runs out of reachable rock.** It idles; whether it should signal that (particles, an indicator) is undecided.
- **Consumption and production rates.** Deferred to playtest.
- **Exact claim dimensions.** Subject to revision.

## Future Ideas

An unordered bucket; not commitments, not phased:

- More production roles: toolsmith, armorer, weaponsmith, shearer, butcher, librarian (auto-enchanting), cartographer (auto-mapping). New job site blocks and villager textures become reasonable at this stage.
- Construction roles: carpenters, pavers, builders â€” automating expansion rather than production.
- Warrior villagers replacing iron golems; consume armour and weapons in combat, creating real sinks for armorer/weaponsmith chains.
- Per-role configuration via right-clicking the villager (whitelists for crops, tree types, kept materials).
- Cobble overflow handling: filtering at source, or a void toggle on the miner.
- Villager progression / tiering: production milestones unlock claim size and output rate.
- Repurposed Hero of Village and cured villager benefits as productivity buffs or reduced food consumption.
- Inter-village trade and supply chains.
- Wandering trader inventory expansion.
- 24-hour miner work cycle (they're underground; daylight is irrelevant).

## Out of PRD Scope

- Target Minecraft version.
- License.