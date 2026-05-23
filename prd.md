# Industrious Villagers — Product Requirements Document (Draft v1)

## Overview

*Industrious Villagers* replaces villager trading with productive labour. Villagers perform jobs that produce resources directly; the player builds the surrounding infrastructure (chests, hoppers, furnaces, crafters, minecarts) using vanilla blocks.

Two value propositions:

1. **Adds productive automation that doesn't exist in vanilla** — auto-farming, auto-logging, auto-mining — replacing the fragile-redstone-contraption meta with a clean role-based answer.
2. **Removes broken trading** — the anti-fun re-roll-the-librarian-for-mending mechanic is gone. The wandering trader is the only NPC offering trades.

Each new role retires a category of menial player labour, lifting the player up the abstraction ladder (à la Factorio). Player work shifts from manual labour to systems-thinking: village layout, logistics, defense, expansion.

**Target loader:** NeoForge.

## Goals

- Provide automation that doesn't exist in vanilla (no role overlaps with what crafters, hoppers, and furnaces already enable).
- Create cross-chain resource dependencies that produce scaling pressure.
- Leverage existing Minecraft systems for everything that isn't the role behaviour itself.
- Minimise new assets in V1: reuse existing job site blocks and villager skins.

## Player Experience

After a vanilla bootstrap (gather wood, craft stone tools, find a village), the player places a composter and a villager becomes a farmer. They supply a hoe and till a plot; the farmer takes over planting and harvesting. The player adds a lumberjack (Fletching Table within a grove) and a miner (Stonecutter at the chosen Y level), sets up vanilla auto-smelters and a recipe-locked crafter, and the loop closes — the village produces everything it consumes via three role villagers and vanilla automation blocks. The player's last hand-crafted tool is the **graduation moment**: from here they design infrastructure rather than swinging tools.

Scaling generates the gameplay. More farmers need more hoes need more iron need more mining need more wood for supports. Outpost placement, rail routing, storage organisation, and raid defense become the real decisions.

## Roles

All roles share:

- Assigned by the vanilla mechanism (unemployed villager auto-claims a nearby unowned job site).
- Walk the vanilla daily schedule.
- Consume food (vanilla definition); show hungry particles when blocked by lack of food.
- Pull inputs from and deposit outputs to adjacent chests.
- Tool wear matches vanilla.
- Right-clicking the villager does nothing.

### Farmer

- **Job site:** Composter (reused).
- **Skin:** Vanilla Farmer (reused).
- **Consumes:** Food, hoes.
- **Produces:** Crops the player has planted on pre-tilled soil within range.
- **Behaviour:** Harvests mature crops and replants the same type from harvest yields. Works pre-tilled blocks only. Player provides the first generation of seeds.

### Lumberjack

- **Job site:** Fletching Table (repurposed — vanilla Fletcher profession removed).
- **Skin:** Vanilla Fletcher (reused).
- **Consumes:** Food, axes, saplings when leaf-decay yields are insufficient.
- **Produces:** Logs, saplings, sticks, apples.
- **Behaviour:** Works a 16×16 grove around the job site. Fells supported 1×1 trees (oak, birch, spruce, jungle, acacia) and replants saplings from output. Does not exit the claim. Player provides the first generation of saplings.

### Miner

- **Job site:** Stonecutter (repurposed — vanilla Mason profession removed). Placed at the target Y level by the player.
- **Skin:** Vanilla Mason (reused).
- **Consumes:** Food, pickaxes, wood (tunnel supports), torches, optionally rails.
- **Produces:** Cobblestone and the ore distribution at the chosen Y level. All output goes to the chest unfiltered.
- **Behaviour:** Excavates a 3×3 trunk-and-branch mine in all four cardinal directions. Trunks ~64m, branches ~16m every ~5m of trunk. Wood supports every ~4m, torches every ~6m, rails laid as the miner advances if supplied.
- **Edge cases:** Lava → seal with cobble, abandon branch. Caves → seal opening, abandon branch. Bedrock → branch terminus.

## Trading and Vanilla Professions

All vanilla villager professions are removed entirely. Villagers do not claim cartography tables, lecterns, brewing stands, smithing tables, looms, blast furnaces, grindstones, cauldrons, smokers, or barrels for vanilla professions. The only professions in the game are farmer, lumberjack, and miner. The wandering trader is the sole remaining trading NPC.

Items previously trade-locked are redistributed to loot tables. Mending in particular needs deliberate placement (likely stronghold libraries and ancient cities). A loot table audit pass before shipping ensures nothing becomes unobtainable.

## Preserved Vanilla Mechanics

Unchanged from vanilla: villager food and breeding, beds and population caps, daily sleep schedule, iron golems, pillager raids, zombification. The mod's role villagers participate in all of these. Curing flag remains on cured villagers; its post-trade benefit is to be repurposed in a future version.

## Open Questions

- **Behaviour when a miner exhausts their claim.** Idle with a depleted indicator on the job site? Particle alert? Auto-relocate? **To be confirmed.**
- **Consumption and production rates.** Deferred to playtest.
- **Exact claim dimensions.** 16×16 grove, ~64m trunks, ~16m branches — subject to revision.

## Future Ideas

An unordered bucket; not commitments, not phased:

- More production roles: toolsmith, armorer, weaponsmith, shearer, butcher, librarian (auto-enchanting), cartographer (auto-mapping). New job site blocks and villager textures become reasonable at this stage.
- Construction roles: carpenters, pavers, builders — automating expansion rather than production.
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