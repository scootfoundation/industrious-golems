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

After a vanilla bootstrap (gather wood, craft stone tools), the player places a composter, spawns a hay golem next to it and gives it a hoe. They till a plot; the golem takes over planting and harvesting. The player adds a mushroom golem (fletching table within a grove) and a terracotta golem (loom beside a pen), sets up vanilla auto-smelters and a recipe-locked crafter, and the loop closes. The player's last hand-crafted tool is the **graduation moment**: from here they design infrastructure rather than swinging tools.

Scaling generates the gameplay. More golems need more tools need more iron need more mining need more wood for supports.

## Golems

All golems share:

- **Obtained** from spawn eggs for now (creative tab); a crafting or building recipe is future work.
- Claim the nearest unclaimed job site of their kind (within 48 blocks) and stay near it.
- Work during the daytime work hours of the villager schedule; they never sleep, they idle at night.
- Consume food (vanilla definition) and show a hunger bar; they refuse to work when too hungry and starve down to half a heart.
- Pull inputs from and deposit outputs to adjacent chests.
- Tool wear matches vanilla.
- Right-clicking a golem opens its inventory (tool slots, supplies, food).
- A status plate shows hunger, health and missing tools.

### Hay golem (farmer)

- **Job site:** Composter.
- **Consumes:** Food, hoes, seeds.
- **Produces:** Crops the player has planted on pre-tilled soil within range.
- **Behaviour:** Harvests mature crops and replants from harvest yields. Works pre-tilled blocks only. Player provides the first generation of seeds.

### Mushroom golem (lumberjack)

- **Job site:** Fletching Table.
- **Consumes:** Food, axes, saplings.
- **Produces:** Logs, saplings, sticks, apples.
- **Behaviour:** Works a grove around the job site. Fells natural trees log by log and replants saplings from output. Ignores player-built log structures.

### Terracotta golem (herder)

- **Job site:** Loom.
- **Consumes:** Food, shears, a sword, buckets, animal feed.
- **Produces:** Wool, milk, meat and hides, kept to a target herd size.
- **Behaviour:** Shears sheep, milks cows, breeds sparse pens and culls crowded ones.

### Stone golem (miner) — planned

- **Job site:** Stonecutter, placed at the target Y level by the player.
- **Consumes:** Food, pickaxes, wood (tunnel supports), torches, optionally rails.
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