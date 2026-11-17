# Industrious Golems — Product Requirements Document (Draft v2)

## Overview

*Industrious Golems* adds work golems: golems that hold a job and produce resources directly, while the player builds the surrounding infrastructure (chests, hoppers, furnaces, crafters, minecarts) using vanilla blocks. Villagers are left exactly as in vanilla (trading and all); the golems do the labour.

Two value propositions:

1. **Adds productive automation that doesn't exist in vanilla** — auto-farming, auto-logging, herding, and (planned) auto-mining — replacing the fragile-redstone-contraption meta with a clean role-based answer.
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

- **Built like a copper golem:** place the golem's body block and put a carved pumpkin or jack o'lantern on top. The pumpkin goes, the body block becomes the golem's **chest**, and the golem stands on it. No chest is needed to start; the pattern creates one. The golem is bound to that chest as its job site and starts dormant, ready to work as soon as it is given what it craves. Spawn eggs stay in the creative tab.
- **The chest** is a 27-slot chest of the golem's own design. It is the golem's job site and the place it deposits output and restocks supplies, and it is preferred over any other nearby chest. It is craftable (vanilla chest + the old job site block + the body block) so a chest can be re-placed or moved. Chests never join into double chests.
- Claim the nearest unclaimed job site of their kind (within 48 blocks) and stay near it. A golem's own chest is its job site; the vanilla block it used to use (composter, fletching table, loom) still counts as a fallback.
- Work bare-handed, day and night, but only while **charged**. A golem starts dormant (head hanging, standing still) and does nothing until a player gives it what it currently craves.
- Each golem kind has eight **offerings**: uncommon finds from different biomes, structures and mobs, deliberately unrelated to the golem's trade and never something it produces itself. It craves one at a time (shown on its status plate and in its screen); one offering is worth 45 minutes of work, and after it is accepted the golem craves something different, so no single item is enough. It stores at most two offerings' worth (90 minutes). The clock runs only while the golem is loaded and has a job site.
- Give an offering by right-clicking the golem with it. A dormant golem holds out what it craves in its off hand; there is no inventory screen and no status plate.
- **Carry one stack at a time.** A golem has no inventory. It breaks a block (or shears, milks, culls) and the result drops in the world as it would for a player. The golem picks up one stack in its hand, takes it to its chest, and goes back for the next. Supplies (seeds, saplings, buckets, feed) come from the chest the same way, a stack at a time, and are used from the hand.

### Hay golem (farmer)

- **Job site:** Hay chest. **Built from:** hay bale + pumpkin.
- **Consumes:** Seeds.
- **Wants:** Slimeball, glow ink sac, pufferfish, cobweb, rabbit's foot, cactus flower, pointed dripstone, glow berries.
- **Produces:** Crops the player has planted on pre-tilled soil within range.
- **Behaviour:** Stands on each plot it works: walks onto a ripe crop and breaks it, then onto bare farmland to sow the seeds it carries. Works pre-tilled blocks only. Deliberately slow (it walks at less than half the others' pace). Player provides the first generation of seeds.

### Mushroom golem (lumberjack)

- **Job site:** Mushroom chest. **Built from:** red mushroom block + pumpkin.
- **Consumes:** Saplings.
- **Wants:** Amethyst shard, resin clump, spore blossom, pink petals, goat horn, armadillo scute, honey bottle, open eyeblossom.
- **Produces:** Logs, saplings, sticks, apples.
- **Behaviour:** Works a grove around the job site. Fells natural trees log by log and replants saplings from output. Ignores player-built log structures.

### Terracotta golem (herder)

- **Job site:** Terracotta chest. **Built from:** terracotta (plain or coloured) + pumpkin.
- **Consumes:** Buckets (turned into milk), animal feed.
- **Wants:** Turtle scute, sea pickle, cocoa beans, mangrove propagule, powder snow bucket, any pottery sherd, shroomlight, big dripleaf.
- **Produces:** Wool, milk, meat and hides, kept to a target herd size.
- **Behaviour:** Shears sheep, milks cows, breeds sparse pens and culls crowded ones.

### Stone golem (miner) — planned

- **Job site:** Stone chest (art done, mossy stone bricks with an iron latch), placed at the target Y level by the player. **Built from:** stone bricks + pumpkin.
- **Consumes:** Wood (tunnel supports), torches, optionally rails.
- **Wants:** Eight offerings still to be chosen (candidates: ender pearl, honeycomb, calcite, packed ice, moss block, sweet berries, phantom membrane, nether quartz).
- **Produces:** Cobblestone and the ore distribution at the chosen Y level.
- **Behaviour:** Excavates a 3×3 trunk-and-branch mine in the four cardinal directions; seals off lava and caves.

## Villagers

Unchanged from vanilla, including trading, professions, schedules, breeding, iron golems and raids.

## Open Questions

- **Acquisition.** Spawn eggs only for now; a build-by-pattern or craftable recipe is undecided.
- **Behaviour when a miner exhausts their claim.** Idle with a depleted indicator? Particle alert? To be confirmed.
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