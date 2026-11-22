package com.scottcaldwell.industriousgolems;

import com.scottcaldwell.industriousgolems.block.GolemChestBlock;
import com.scottcaldwell.industriousgolems.golem.ChestSpawn;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import com.scottcaldwell.industriousgolems.herd.HerdPen;
import com.scottcaldwell.industriousgolems.herd.HerdWork;
import com.scottcaldwell.industriousgolems.mine.MineFinder;
import com.scottcaldwell.industriousgolems.needs.NeedsIndicator;
import com.scottcaldwell.industriousgolems.offering.Offering;
import com.scottcaldwell.industriousgolems.offering.Offerings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Headless end-to-end tests, run with `./gradlew runGameTestServer`. Test instances live in
 * data/industriousgolems/test_instance; each names one of the functions registered here.
 */
public class ModGameTests {

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, IndustriousGolems.MODID);

    static {
        strictly("farmer_harvests_and_replants", ModGameTests::farmerHarvestsAndReplants);
        strictly("farmer_chest_io", ModGameTests::farmerChestIo);
        strictly("farmer_chest_preference", ModGameTests::farmerChestPreference);
        strictly("herder_shears_sheep", ModGameTests::herderShearsSheep);
        strictly("herder_milks_cow", ModGameTests::herderMilksCow);
        strictly("herder_culls_crowded_pen", ModGameTests::herderCullsCrowdedPen);
        strictly("herder_breeds_sparse_pen", ModGameTests::herderBreedsSparsePen);
        strictly("pen_detection", ModGameTests::penDetection);
        strictly("lumberjack_fells_and_replants", ModGameTests::lumberjackFellsAndReplants);
        strictly("lumberjack_ignores_log_building", ModGameTests::lumberjackIgnoresLogBuilding);
        strictly("golem_dormant_until_offered", ModGameTests::golemDormantUntilOffered);
        strictly("golem_offering_rules", ModGameTests::golemOfferingRules);
        strictly("golem_charge_runs_out", ModGameTests::golemChargeRunsOut);
        FUNCTIONS.register("hay_chest_pattern", () -> h -> chestPattern(h, ModProfessions.FARMER, Blocks.HAY_BLOCK.defaultBlockState(), ModBlocks.HAY_CHEST.get()));
        FUNCTIONS.register("mushroom_chest_pattern", () -> h -> chestPattern(h, ModProfessions.LUMBERJACK, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(), ModBlocks.MUSHROOM_CHEST.get()));
        FUNCTIONS.register("terracotta_chest_pattern", () -> h -> chestPattern(h, ModProfessions.HERDER, Blocks.TERRACOTTA.defaultBlockState(), ModBlocks.TERRACOTTA_CHEST.get()));
        strictly("chest_pattern_needs_matching_body", ModGameTests::chestPatternNeedsMatchingBody);
        strictly("golem_death_drops_carried_stack", ModGameTests::golemDeathDropsCarriedStack);
        strictly("golem_state_survives_save_and_load", ModGameTests::golemStateSurvivesSaveAndLoad);
        strictly("golem_picks_up_only_what_it_works_with", ModGameTests::golemPicksUpOnlyWhatItWorksWith);
        strictly("golem_uses_its_own_chest", ModGameTests::golemUsesItsOwnChest);
        strictly("golem_keeps_stack_when_chest_full", ModGameTests::golemKeepsStackWhenChestFull);
        strictly("farmer_does_not_sow_non_seeds", ModGameTests::farmerDoesNotSowNonSeeds);
        strictly("lumberjack_fells_tall_tree", ModGameTests::lumberjackFellsTallTree);
        strictly("lumberjack_fetches_saplings", ModGameTests::lumberjackFetchesSaplings);
        strictly("herder_fetches_one_bucket", ModGameTests::herderFetchesOneBucket);
        strictly("herder_accepts_any_sherd", ModGameTests::herderAcceptsAnySherd);
        strictly("chest_pattern_via_place_event", ModGameTests::chestPatternViaPlaceEvent);
        strictly("chest_pattern_needs_headroom", ModGameTests::chestPatternNeedsHeadroom);
        FUNCTIONS.register("stone_chest_pattern", () -> h -> chestPattern(h, ModProfessions.MINER, Blocks.SMOOTH_STONE.defaultBlockState(), ModBlocks.STONE_CHEST.get()));
        strictly("miner_digs_and_delivers", ModGameTests::minerDigsAndDelivers);
        strictly("miner_dig_rules", ModGameTests::minerDigRules);
        strictly("miner_ignores_unreachable_rock", ModGameTests::minerIgnoresUnreachableRock);
        strictly("golem_meets_chest_face_on_and_opens_it", ModGameTests::golemMeetsChestFaceOn);
        strictly("lumberjack_plants_spaced_saplings", ModGameTests::lumberjackPlantsSpacedSaplings);
        strictly("lumberjack_fells_branches", ModGameTests::lumberjackFellsBranchingTree);
        strictly("lumberjack_collects_before_felling", ModGameTests::lumberjackCollectsBeforeFelling);
        strictly("lumberjack_never_conjures_saplings", ModGameTests::lumberjackNeverConjuresSaplings);
        strictly("lumberjack_plants_through_grass", ModGameTests::lumberjackPlantsThroughGrass);
        strictly("lumberjack_sits_when_chest_full", ModGameTests::lumberjackSitsWhenChestFull);
        strictly("lumberjack_interrupted_in_tree", ModGameTests::lumberjackInterruptedInTree);
        strictly("lumberjack_ignores_stripped_logs", ModGameTests::lumberjackIgnoresStrippedLogs);
        strictly("lumberjack_leaves_dark_oak_saplings", ModGameTests::lumberjackLeavesDarkOakSaplings);
        strictly("registry_consistency", ModGameTests::registryConsistency);
        strictly("state_graphs", ModGameTests::stateGraphs);
        strictly("golem_leaves_chest_top_before_animating", ModGameTests::golemLeavesChestTopBeforeAnimating);
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
    }

    /** Registers a test that runs with the state machines strict: a transition nobody declared fails it. */
    private static void strictly(String name, Consumer<GameTestHelper> test) {
        FUNCTIONS.register(name, () -> helper -> {
            com.scottcaldwell.industriousgolems.behavior.Machine.strict = true;
            test.accept(helper);
        });
    }

    /** Every state machine is fully described; also refreshes docs/state-machines.json for the diagram page. */
    private static void stateGraphs(GameTestHelper helper) {
        for (var graph : com.scottcaldwell.industriousgolems.behavior.Graphs.all()) {
            helper.assertTrue(graph.problems().isEmpty(), String.join("; ", graph.problems()));
        }
        try {
            java.nio.file.Path docs = java.nio.file.Path.of("..", "..", "docs");
            if (java.nio.file.Files.isDirectory(docs)) {
                String template = java.nio.file.Files.readString(docs.resolve("state-machines.template.html"));
                java.nio.file.Files.writeString(docs.resolve("state-machines.artifact.html"), com.scottcaldwell.industriousgolems.behavior.Graphs.page(template));
            }
        } catch (java.io.IOException e) {
            IndustriousGolems.LOGGER.warn("could not write the state machine page", e);
        }
        helper.succeed();
    }

    private static final BlockPos COMPOSTER = new BlockPos(2, 1, 2);
    private static final BlockPos CHEST = new BlockPos(4, 1, 2);

    /** Builds a small flat work area with a composter job site and a chest beside it. */
    private static void buildFarm(GameTestHelper helper) {
        // Tests are placed at arbitrary far-away coordinates; make sure the chunks around the test are fully
        // ticking so entities (and their brains) actually run.
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.getLevel().setChunkForced((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz, true);
            }
        }
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        // Walls so the farmer can't stroll off the platform and fall out of the test area.
        for (int i = -1; i <= 8; i++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(i, y, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(i, y, 8), Blocks.STONE);
                helper.setBlock(new BlockPos(-1, y, i), Blocks.STONE);
                helper.setBlock(new BlockPos(8, y, i), Blocks.STONE);
            }
        }
        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        helper.setBlock(CHEST, Blocks.CHEST);
    }

    /** Spawns a charged golem of the given profession with its job site at {@code site}. */
    private static WorkGolem spawnWorker(GameTestHelper helper, ModProfessions.Profession profession,
                                         BlockPos site, BlockPos at) {
        WorkGolem golem = spawnDormantWorker(helper, profession, site, at);
        Offerings.fill(golem);
        return golem;
    }

    /** Spawns a golem as a spawn egg would: out of charge, waiting for an offering. */
    private static WorkGolem spawnDormantWorker(GameTestHelper helper, ModProfessions.Profession profession,
                                                BlockPos site, BlockPos at) {
        WorkGolem golem = helper.spawn(ModEntities.typeOf(profession), at);
        golem.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(site)));
        return golem;
    }

    private static WorkGolem spawnFarmer(GameTestHelper helper, BlockPos at) {
        return spawnWorker(helper, ModProfessions.FARMER, COMPOSTER, at);
    }


    /** Where a golem is and what it holds, for failure messages. */
    private static String describe(GameTestHelper helper, WorkGolem golem, Container chest) {
        StringBuilder items = new StringBuilder();
        AABB box = AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(8, 4, 8)));
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            items.append(entity.getItem()).append('@').append(entity.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO))).append(' ');
        }
        int inChest = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) inChest += chest.getItem(i).getCount();
        return " [golem at " + golem.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + ", carrying " + golem.carried()
                + ", charge " + golem.chargeTicks() + ", active " + golem.getBrain().getActiveActivities() + ", dead " + golem.isDeadOrDying() + ", " + inChest + " in chest, on ground: " + items + "]";
    }

    private static int count(Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        }
        return n;
    }

    /** The farmer carries a stack of wheat: it walks to the chest, opens it and puts the stack in. */
    private static void farmerChestIo(GameTestHelper helper) {
        buildFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);

        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.setCarried(new ItemStack(Items.WHEAT, 5));

        boolean[] sawOpen = {false};
        boolean[] strayed = {false};
        BlockPos chestAbs = helper.absolutePos(CHEST);
        helper.succeedWhen(() -> {
            if (!chest.getEntitiesWithContainerOpen().isEmpty()) {
                sawOpen[0] = true;
                // exclusive task state: while the chest is open the farmer must stay put beside it
                if (farmer.distanceToSqr(chestAbs.getX() + 0.5, chestAbs.getY() + 0.5, chestAbs.getZ() + 0.5) > 3.0 * 3.0) strayed[0] = true;
            }
            helper.assertTrue(!strayed[0], "farmer wandered away from the open chest");
            helper.assertTrue(sawOpen[0], "farmer should have opened the chest (lid animation)");
            helper.assertTrue(chest.getEntitiesWithContainerOpen().isEmpty(), "chest should be closed again");
            helper.assertTrue(count(chest, Items.WHEAT) == 5, "chest should hold the 5 wheat, has " + count(chest, Items.WHEAT));
            helper.assertTrue(!farmer.isCarrying(), "the farmer's hand is free again");
        });
    }

    /**
     * The farmer stands on each ripe crop and breaks it (the yield drops as for a player), carries one stack at a time to
     * the chest, and sows the seeds it picked up on the bare soil again.
     */
    private static void farmerHarvestsAndReplants(GameTestHelper helper) {
        buildFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        for (int x = 3; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 5), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
            helper.setBlock(new BlockPos(x, 2, 5), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        }
        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 2, 3));
        helper.succeedWhen(() -> {
            helper.assertTrue(total(farmer, chest, Items.WHEAT) >= 3, "farmer should have reaped 3 wheat, has " + total(farmer, chest, Items.WHEAT) + " (carried " + farmer.carried() + ", ground wheat " + itemsOnGround(helper, Items.WHEAT) + ", seeds " + itemsOnGround(helper, Items.WHEAT_SEEDS) + ", chest seeds " + count(chest, Items.WHEAT_SEEDS) + ")" + describe(helper, farmer, chest));
            for (int x = 3; x <= 5; x++) {
                var state = helper.getBlockState(new BlockPos(x, 2, 5));
                helper.assertTrue(state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) < 7,
                        "farmer should have replanted at x=" + x + " (plots " + helper.getBlockState(new BlockPos(3, 2, 5)) + helper.getBlockState(new BlockPos(4, 2, 5))
                                + helper.getBlockState(new BlockPos(5, 2, 5)) + ", carried " + farmer.carried() + ", ground seeds " + itemsOnGround(helper, Items.WHEAT_SEEDS) + describe(helper, farmer, chest)
                                + ", chest seeds " + count(chest, Items.WHEAT_SEEDS) + ", pos " + farmer.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)) + ")");
            }
        });
    }

    private static final BlockPos COPPER_CHEST = new BlockPos(4, 1, 4);

    /** Seeds are fetched from the regular chest and sown; what it carries to a chest goes to the copper chest. */
    private static void farmerChestPreference(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(COPPER_CHEST, Blocks.COPPER_CHEST.weathering().unaffected());
        Container regular = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        Container copper = helper.getBlockEntity(COPPER_CHEST, ChestBlockEntity.class);
        regular.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 10));
        helper.setBlock(new BlockPos(3, 1, 5), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));

        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.setCarried(new ItemStack(Items.WHEAT, 5));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(copper, Items.WHEAT) == 5, "wheat should go to the copper chest");
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 5)).is(Blocks.WHEAT), "the farmer should have fetched seeds from the regular chest and sown them");
            helper.assertTrue(count(regular, Items.WHEAT_SEEDS) == 0, "the seed stack should have left the regular chest");
            helper.assertTrue(count(regular, Items.WHEAT) == 0, "wheat should not go to the regular chest");
        });
    }

    // ---- herder ---------------------------------------------------------------------------------------------

    private static final BlockPos LOOM = COMPOSTER;

    /** The farm platform with a loom job site and a 4x4 pen (x 4..7, z 4..7) fenced on its west and north sides. */
    private static void buildHerdFarm(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(LOOM, Blocks.LOOM);
        for (int i = 3; i <= 7; i++) {
            helper.setBlock(new BlockPos(3, 1, i), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(i, 1, 3), Blocks.OAK_FENCE);
        }
    }

    private static WorkGolem spawnHerder(GameTestHelper helper, BlockPos at) {
        return spawnWorker(helper, ModProfessions.HERDER, LOOM, at);
    }

    private static <T extends Animal> List<T> alive(GameTestHelper helper, Class<T> type) {
        var box = AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(3, 1, 3)), helper.absolutePos(new BlockPos(8, 3, 8)));
        return helper.getLevel().getEntitiesOfClass(type, box, e -> e.isAlive());
    }

    private static int total(WorkGolem herder, Container chest, Item item) {
        return (herder.carried().is(item) ? herder.carried().getCount() : 0) + count(chest, item);
    }

    /** A herder shears a ready sheep in the pen bare-handed and collects the wool. */
    private static void herderShearsSheep(GameTestHelper helper) {
        buildHerdFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        helper.spawn(EntityTypes.SHEEP, new BlockPos(5, 1, 5)).setColor(DyeColor.WHITE); // spawned sheep are random colours
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        helper.succeedWhen(() -> {
            var sheep = alive(helper, Sheep.class);
            helper.assertTrue(!sheep.isEmpty() && !sheep.get(0).readyForShearing(), "the sheep should have been sheared");
            helper.assertTrue(total(herder, chest, Items.WOOL.white()) > 0, "the herder should hold or have deposited wool" + describe(helper, herder, chest));
        });
    }

    /** A herder with a spare bucket milks a cow: the bucket becomes milk. */
    private static void herderMilksCow(GameTestHelper helper) {
        buildHerdFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        helper.spawn(EntityTypes.COW, new BlockPos(5, 1, 5));
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        herder.setCarried(new ItemStack(Items.BUCKET));
        helper.succeedWhen(() -> {
            helper.assertTrue(total(herder, chest, Items.MILK_BUCKET) == 1, "the cow should have been milked once");
            helper.assertTrue(total(herder, chest, Items.BUCKET) == 0, "the empty bucket was used up");
        });
    }

    /** Six pigs in a 16-cell pen is over the density band: the herder culls down to four, collecting the meat. */
    private static void herderCullsCrowdedPen(GameTestHelper helper) {
        buildHerdFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        for (int i = 0; i < 6; i++) helper.spawn(EntityTypes.PIG, new BlockPos(4 + i % 4, 1, 4 + i / 2));
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(alive(helper, Pig.class).size() == 4,
                    "pen should be culled to 4 pigs, has " + alive(helper, Pig.class).size());
            helper.assertTrue(total(herder, chest, Items.PORKCHOP) > 0, "the meat should be collected" + describe(helper, herder, chest));
        });
    }

    /** Three cows in a 16-cell pen is below the density band: the herder feeds wheat and a calf is born. */
    private static void herderBreedsSparsePen(GameTestHelper helper) {
        buildHerdFarm(helper);
        helper.spawn(EntityTypes.COW, new BlockPos(5, 1, 5));
        helper.spawn(EntityTypes.COW, new BlockPos(6, 1, 6));
        helper.spawn(EntityTypes.COW, new BlockPos(7, 1, 5));
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        herder.setCarried(new ItemStack(Items.WHEAT, 8));
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(alive(helper, Cow.class).size() >= 4, "a calf should have been born");
            helper.assertTrue(total(herder, chest, Items.WHEAT) < 8, "the herder should have fed wheat");
        });
    }

    /** A fenced 4x4 area is a pen; the same fence with a gap leaks into open ground and is not. */
    private static void penDetection(GameTestHelper helper) {
        for (int x = 0; x < 14; x++) {
            for (int z = 0; z < 14; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        for (int i = 5; i <= 10; i++) { // fence ring around the 4x4 interior x 6..9, z 6..9
            for (int j = 5; j <= 10; j++) {
                if (i == 5 || i == 10 || j == 5 || j == 10) helper.setBlock(new BlockPos(i, 1, j), Blocks.OAK_FENCE);
            }
        }
        var level = helper.getLevel();
        var closed = HerdPen.cellsAround(level, helper.absolutePos(new BlockPos(7, 1, 7)));
        helper.assertTrue(closed != null && closed.size() == 16, "closed fence should form a 16-cell pen, got " + (closed == null ? "none" : closed.size()));
        helper.setBlock(new BlockPos(5, 1, 7), Blocks.AIR); // open a gap
        var leaky = HerdPen.cellsAround(level, helper.absolutePos(new BlockPos(7, 1, 7)));
        helper.assertTrue(leaky == null, "a gap in the fence should leak into open ground");
        helper.succeed();
    }

    // ---- lumberjack -----------------------------------------------------------------------------------------

    private static WorkGolem spawnLumberjack(GameTestHelper helper, BlockPos at) {
        helper.setBlock(COMPOSTER, Blocks.FLETCHING_TABLE);
        return spawnWorker(helper, ModProfessions.LUMBERJACK, COMPOSTER, at);
    }

    /** A 4-log oak with natural leaves rooted in grass at (x, z). */
    private static void buildTree(GameTestHelper helper, int x, int z) {
        helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
        var leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int y = 4; y <= 5; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 && y == 4) continue;
                    helper.setBlock(new BlockPos(x + dx, y, z + dz), leaf);
                }
            }
        }
        for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
        for (int y = 6; y <= 7; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR); // the arena's low ceiling: room to stand on top of the crown
    }

    private static int logsAt(GameTestHelper helper, int x, int z) {
        int n = 0;
        for (int y = 1; y <= 4; y++) if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_LOG)) n++;
        return n;
    }

    /** The lumberjack fells a natural tree bare-handed, carries the logs to the chest a stack at a time and plants a sapling from its chest. */
    private static void lumberjackFellsAndReplants(GameTestHelper helper) {
        buildFarm(helper);
        buildTree(helper, 5, 5);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        chest.setItem(0, new ItemStack(Items.OAK_SAPLING, 2));
        helper.succeedWhen(() -> {
            helper.assertTrue(logsAt(helper, 5, 5) == 0, "all four logs should be felled, " + logsAt(helper, 5, 5) + " left");
            helper.assertTrue(helper.getBlockState(new BlockPos(5, 1, 5)).is(Blocks.OAK_SAPLING), "a sapling should be planted where the trunk stood");
            helper.assertTrue(count(chest, Items.OAK_LOG) == 4, "the four logs should be carried to the chest, got " + count(chest, Items.OAK_LOG));
            helper.assertTrue(!jack.isCarrying(), "nothing is carried afterwards");
        });
    }

    /** Logs without natural leaves are a player's building: left alone. */
    private static void lumberjackIgnoresLogBuilding(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(new BlockPos(5, 0, 5), Blocks.GRASS_BLOCK);
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(5, y, 5), Blocks.OAK_LOG);
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        helper.runAtTickTime(500, () -> {
            helper.assertTrue(logsAt(helper, 5, 5) == 3, "a log structure must not be felled");
            helper.succeed();
        });
    }

    // ---- offerings ------------------------------------------------------------------------------------------------

    private static Item craved(WorkGolem golem) {
        return golem.profession().offerings().get(golem.cravingIndex()).icon().getItem();
    }

    private static Item notCraved(WorkGolem golem) {
        for (var offering : golem.profession().offerings()) {
            if (offering != golem.profession().offerings().get(golem.cravingIndex())) return offering.icon().getItem();
        }
        throw new IllegalStateException("a profession needs more than one offering");
    }

    /** A golem as a spawn egg makes it does nothing and shows what it craves; the right item wakes it and it works. */
    private static void golemDormantUntilOffered(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(new BlockPos(3, 1, 5), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
        helper.setBlock(new BlockPos(3, 2, 5), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        WorkGolem farmer = spawnDormantWorker(helper, ModProfessions.FARMER, COMPOSTER, new BlockPos(3, 2, 3));
        Item[] given = {null};
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(!farmer.isCharged(), "a new golem starts dormant");
            helper.assertTrue(farmer.cravingIndex() >= 0, "a dormant golem has chosen what it craves");
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 5)).getValue(CropBlock.AGE) == 7, "a dormant golem leaves the crop alone");
            helper.assertTrue(farmer.getItemBySlot(EquipmentSlot.OFFHAND).is(craved(farmer)), "a dormant golem holds out what it craves");
            given[0] = craved(farmer);
            var player = helper.makeMockServerPlayerInLevel();
            player.setPos(farmer.getX() + 5000, farmer.getY(), farmer.getZ());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(given[0], 2));
            player.interactOn(farmer, InteractionHand.MAIN_HAND, farmer.position());
            helper.assertTrue(farmer.isCharged() && farmer.chargeTicks() == Offerings.PER_OFFERING, "45 minutes of charge");
            helper.assertTrue(craved(farmer) != given[0], "it now craves something different");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(given[0] != null, "the offering was made");
            helper.assertTrue(helper.getBlockEntity(CHEST, ChestBlockEntity.class).getItem(0).is(Items.WHEAT) || farmer.carried().is(Items.WHEAT)
                    || !helper.getBlockState(new BlockPos(3, 2, 5)).is(Blocks.WHEAT) || helper.getBlockState(new BlockPos(3, 2, 5)).getValue(CropBlock.AGE) < 7,
                    "the woken golem should have reaped the wheat");
        });
    }

    /** Only the craved item is taken; charge is capped at two offerings; the craving never repeats the last item. */
    private static void golemOfferingRules(GameTestHelper helper) {
        buildFarm(helper);
        WorkGolem farmer = spawnDormantWorker(helper, ModProfessions.FARMER, COMPOSTER, new BlockPos(3, 1, 3));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(farmer.profession().offerings().size() == 8, "each golem kind has eight offerings");
            ItemStack wrong = new ItemStack(notCraved(farmer), 3);
            helper.assertTrue(!Offerings.offer(farmer, wrong) && wrong.getCount() == 3, "an item it does not crave is refused");
            helper.assertTrue(!Offerings.offer(farmer, new ItemStack(Items.BREAD)), "food is no offering");

            int first = farmer.cravingIndex();
            ItemStack one = new ItemStack(craved(farmer), 4);
            helper.assertTrue(Offerings.offer(farmer, one) && one.getCount() == 3, "the craved item is taken");
            helper.assertTrue(farmer.cravingIndex() != first, "the next craving differs from the last");
            ItemStack two = new ItemStack(craved(farmer), 4);
            helper.assertTrue(Offerings.offer(farmer, two), "a second offering fits");
            helper.assertTrue(farmer.chargeTicks() == Offerings.MAX, "charge is two offerings");
            ItemStack three = new ItemStack(craved(farmer), 4);
            helper.assertTrue(!Offerings.offer(farmer, three) && three.getCount() == 4, "a third does not fit");
            helper.succeed();
        });
    }

    /** The charge runs down only while the golem has a job site; at zero it goes dormant and stops working. */
    private static void golemChargeRunsOut(GameTestHelper helper) {
        buildFarm(helper);
        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.setChargeTicks(60);
        // a herder has no loom here to claim as a job site
        WorkGolem homeless = helper.spawn(ModEntities.HERDER_GOLEM.get(), new BlockPos(5, 1, 5));
        homeless.setChargeTicks(60);
        helper.succeedWhen(() -> {
            helper.assertTrue(farmer.chargeTicks() == 0 && !farmer.isCharged(), "the farmer should have run out, has " + farmer.chargeTicks());
            helper.assertTrue(farmer.getBrain().isActive(Activity.REST), "a dormant golem rests");
            helper.assertTrue(homeless.chargeTicks() == 60, "a golem without a job site does not use charge, has " + homeless.chargeTicks());
        });
    }

    // ---- the chest spawn pattern ----------------------------------------------------------------------------------

    /** A body block with a carved pumpkin on top becomes the golem standing on its chest, bound to it and ready to be woken. */
    private static void chestPattern(GameTestHelper helper, ModProfessions.Profession profession,
                                     BlockState body,
                                     Block chest) {
        BlockPos base = new BlockPos(3, 1, 3);
        BlockPos top = base.above();
        helper.setBlock(base, body);
        var pumpkin = Blocks.CARVED_PUMPKIN.defaultBlockState()
                .setValue(CarvedPumpkinBlock.FACING, Direction.SOUTH);
        helper.setBlock(top, pumpkin);
        WorkGolem golem = ChestSpawn.build(helper.getLevel(), helper.absolutePos(top), pumpkin);
        helper.assertTrue(golem != null, "the pattern should spawn a golem");
        helper.assertTrue(golem.profession() == profession, "the right kind of golem");
        helper.assertTrue(helper.getBlockState(base).is(chest), "the body block should become the golem's chest");
        helper.assertTrue(helper.getBlockState(top).isAir(), "the pumpkin should be gone");
        helper.assertTrue(golem.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.pos().equals(helper.absolutePos(base))).isPresent(), "the chest should be its job site");
        helper.assertTrue(!golem.isCharged(), "it starts dormant, waiting for what it craves");
        helper.runAtTickTime(5, () -> {
            Item wanted = craved(golem);
            helper.assertTrue(Offerings.wants(golem, new ItemStack(wanted)), "it should want its craving");
            Offerings.offer(golem, new ItemStack(wanted));
            helper.assertTrue(golem.isCharged(), "the craving should wake it");
            helper.assertTrue(golem.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE), "it keeps its chest as job site");
        });
        helper.succeedWhen(() -> helper.assertTrue(golem.isCharged(), "charged"));
    }

    /** A pumpkin on any other block, or a body block of another golem under the wrong pumpkin, builds nothing. */
    private static void chestPatternNeedsMatchingBody(GameTestHelper helper) {
        BlockPos base = new BlockPos(3, 1, 3);
        helper.setBlock(base, Blocks.STONE);
        var pumpkin = Blocks.JACK_O_LANTERN.defaultBlockState();
        helper.setBlock(base.above(), pumpkin);
        helper.assertTrue(ChestSpawn.build(helper.getLevel(),
                helper.absolutePos(base.above()), pumpkin) == null, "stone is not a body block");
        helper.assertTrue(helper.getBlockState(base).is(Blocks.STONE), "nothing should change");
        helper.succeed();
    }

    // ---- carrying, picking up and chests ----------------------------------------------------------------------------

    private static final BlockPos AT = new BlockPos(3, 1, 3);

    private static int itemsOnGround(GameTestHelper helper, Item item) {
        AABB box = AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(8, 4, 8)));
        int n = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
            if (entity.getItem().is(item)) n += entity.getItem().getCount();
        }
        return n;
    }

    /** What a golem carries drops with it however it dies, but the craving it shows in its off hand never does. */
    private static void golemDeathDropsCarriedStack(GameTestHelper helper) {
        buildFarm(helper);
        WorkGolem golem = spawnDormantWorker(helper, ModProfessions.FARMER, COMPOSTER, AT);
        golem.setCarried(new ItemStack(Items.WHEAT, 7));
        Item[] shown = {null};
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(NeedsIndicator.isIcon(golem.getItemBySlot(EquipmentSlot.OFFHAND)), "the craving is shown in the off hand");
            shown[0] = golem.getItemBySlot(EquipmentSlot.OFFHAND).getItem();
            golem.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000.0F);
        });
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(golem.isDeadOrDying(), "the golem should be dead");
            helper.assertTrue(itemsOnGround(helper, Items.WHEAT) == 7, "the carried stack should drop, found " + itemsOnGround(helper, Items.WHEAT));
            helper.assertTrue(itemsOnGround(helper, shown[0]) == 0, "the craving icon is not loot");
            helper.succeed();
        });
    }

    /** Charge, craving, last offering and the carried stack all survive a save and load. */
    private static void golemStateSurvivesSaveAndLoad(GameTestHelper helper) {
        buildFarm(helper);
        WorkGolem golem = spawnDormantWorker(helper, ModProfessions.FARMER, COMPOSTER, AT);
        golem.setChargeTicks(12345);
        golem.setCravingIndex(3);
        golem.setLastOffering(5);
        golem.setCarried(new ItemStack(Items.WHEAT, 9));

        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        golem.saveWithoutId(out);
        CompoundTag saved = out.buildResult();

        WorkGolem copy = helper.spawn(ModEntities.FARMER_GOLEM.get(), new BlockPos(5, 1, 5));
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        helper.assertTrue(copy.chargeTicks() == 12345, "charge, got " + copy.chargeTicks());
        helper.assertTrue(copy.isCharged(), "the synced charge is restored too");
        helper.assertTrue(copy.cravingIndex() == 3, "craving, got " + copy.cravingIndex());
        helper.assertTrue(copy.lastOffering() == 5, "last offering, got " + copy.lastOffering());
        helper.assertTrue(copy.carried().is(Items.WHEAT) && copy.carried().getCount() == 9, "carried stack");
        helper.succeed();
    }

    /** A golem takes a whole stack of what its work produces or uses, and nothing else; a dormant one takes nothing. */
    private static void golemPicksUpOnlyWhatItWorksWith(GameTestHelper helper) {
        buildFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        WorkGolem farmer = spawnDormantWorker(helper, ModProfessions.FARMER, COMPOSTER, new BlockPos(2, 1, 5));
        helper.spawnItem(Items.WHEAT, new Vec3(3.5, 1.0, 5.5)).setItem(new ItemStack(Items.WHEAT, 12));
        helper.spawnItem(Items.STONE, new Vec3(3.5, 1.0, 6.5));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(!farmer.isCarrying() && itemsOnGround(helper, Items.WHEAT) == 12, "a dormant golem leaves things lying");
            Offerings.fill(farmer);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(count(chest, Items.WHEAT) == 12, "the whole stack of wheat reaches the chest, got " + count(chest, Items.WHEAT));
            helper.assertTrue(count(chest, Items.STONE) == 0 && itemsOnGround(helper, Items.STONE) == 1, "stone is left alone");
        });
    }

    /** The golem's own chest takes its deliveries, not a copper chest, a plain chest or another golem's chest beside it. */
    private static void golemUsesItsOwnChest(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(COMPOSTER, ModBlocks.HAY_CHEST.get());
        helper.setBlock(COPPER_CHEST, Blocks.COPPER_CHEST.weathering().unaffected());
        helper.setBlock(new BlockPos(2, 1, 4), ModBlocks.MUSHROOM_CHEST.get());
        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.setCarried(new ItemStack(Items.WHEAT, 5));
        Container own = helper.getBlockEntity(COMPOSTER, ChestBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(count(own, Items.WHEAT) == 5, "wheat goes into the golem's own chest, got " + count(own, Items.WHEAT));
            for (BlockPos other : List.of(CHEST, COPPER_CHEST, new BlockPos(2, 1, 4))) {
                helper.assertTrue(count(helper.getBlockEntity(other, ChestBlockEntity.class), Items.WHEAT) == 0, "no other chest gets any");
            }
        });
    }

    /** A golem comes to the front of its chest, even from behind it, and the lid is up while it puts its stack away. */
    private static void golemMeetsChestFaceOn(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(COMPOSTER, ModBlocks.HAY_CHEST.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(CHEST, Blocks.AIR); // the front is clear
        WorkGolem farmer = spawnFarmer(helper, new BlockPos(1, 1, 2)); // behind it
        farmer.setCarried(new ItemStack(Items.WHEAT, 5));
        Container own = helper.getBlockEntity(COMPOSTER, ChestBlockEntity.class);
        boolean[] seen = new boolean[2];
        String[] where = {""};
        helper.succeedWhen(() -> {
            BlockPos chestAbs = helper.absolutePos(COMPOSTER);
            if (helper.getBlockEntity(COMPOSTER, ChestBlockEntity.class).getEntitiesWithContainerOpen().isEmpty() == false) {
                seen[0] = true;
                where[0] = where[0].isEmpty() || !seen[1] ? farmer.position() + " chest " + chestAbs + " " + helper.getLevel().getBlockState(chestAbs) : where[0];
                net.minecraft.core.Direction f = helper.getLevel().getBlockState(chestAbs)
                        .getValue(net.minecraft.world.level.block.ChestBlock.FACING);
                double dx = farmer.getX() - (chestAbs.getX() + 0.5), dz = farmer.getZ() - (chestAbs.getZ() + 0.5);
                seen[1] = dx * f.getStepX() + dz * f.getStepZ() > 1.0 && Math.abs(dx * f.getStepZ() - dz * f.getStepX()) < 0.2;
            }
            helper.assertTrue(count(own, Items.WHEAT) == 5, "the wheat goes in the chest");
            helper.assertTrue(seen[0], "the lid was raised during the visit");
            helper.assertTrue(seen[1], "the golem stood squarely in front of the chest while it was open: " + where[0]);
        });
    }

    /** A golem found standing on top of its chest climbs down and only starts the clip at ground level in front. */
    private static void golemLeavesChestTopBeforeAnimating(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(COMPOSTER, ModBlocks.HAY_CHEST.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(CHEST, Blocks.AIR);
        WorkGolem farmer = spawnFarmer(helper, COMPOSTER.above());
        farmer.setCarried(new ItemStack(Items.WHEAT, 5));
        Container own = helper.getBlockEntity(COMPOSTER, ChestBlockEntity.class);
        boolean[] high = {false};
        helper.succeedWhen(() -> {
            if (!helper.getBlockEntity(COMPOSTER, ChestBlockEntity.class).getEntitiesWithContainerOpen().isEmpty()
                    && farmer.getY() > helper.absolutePos(COMPOSTER).getY() + 0.4) high[0] = true;
            helper.assertTrue(count(own, Items.WHEAT) == 5, "the wheat goes in the chest");
            helper.assertTrue(!high[0], "the golem was up on the chest while it was open");
        });
    }

    /** With no tree to fell, the lumberjack fetches saplings from its chest and plants them on open ground, well apart from each other. */
    private static void lumberjackPlantsSpacedSaplings(GameTestHelper helper) {
        buildFarm(helper);
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
                for (int y = 6; y <= 7; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.OAK_SAPLING, 6));
        helper.succeedWhen(() -> {
            List<BlockPos> planted = new java.util.ArrayList<>();
            for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) {
                if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.OAK_SAPLING)) planted.add(new BlockPos(x, 1, z));
            }
            helper.assertTrue(planted.size() >= 2, "it plants saplings on its own, got " + planted.size());
            for (BlockPos a : planted) for (BlockPos b : planted) {
                if (a != b) helper.assertTrue(Math.abs(a.getX() - b.getX()) > 3 || Math.abs(a.getZ() - b.getZ()) > 3, "saplings are spaced apart: " + a + " " + b);
            }
        });
    }

    /** With nowhere to put it, the golem keeps hold of its stack: nothing is lost or spilled. */
    private static void golemKeepsStackWhenChestFull(GameTestHelper helper) {
        buildFarm(helper);
        Container chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        WorkGolem farmer = spawnFarmer(helper, AT);
        farmer.setCarried(new ItemStack(Items.WHEAT, 5));
        helper.runAtTickTime(400, () -> {
            helper.assertTrue(farmer.carried().is(Items.WHEAT) && farmer.carried().getCount() == 5, "the stack stays in hand");
            helper.assertTrue(count(chest, Items.WHEAT) == 0 && itemsOnGround(helper, Items.WHEAT) == 0, "nothing is lost or spilled");
            helper.succeed();
        });
    }

    /** Wheat in hand is not seed: bare farmland stays bare, and the wheat goes to the chest. */
    private static void farmerDoesNotSowNonSeeds(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(new BlockPos(3, 1, 5), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
        Container chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        WorkGolem farmer = spawnFarmer(helper, AT);
        farmer.setCarried(new ItemStack(Items.WHEAT, 5));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 5)).isAir(), "nothing is sown");
            helper.assertTrue(count(chest, Items.WHEAT) == 5, "the wheat is put away");
        });
    }

    /** A trunk taller than a golem could reach from the ground is no problem: it works down from the top. */
    private static void lumberjackFellsTallTree(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(new BlockPos(5, 0, 5), Blocks.GRASS_BLOCK);
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int y = 1; y <= 8; y++) helper.setBlock(new BlockPos(5, y, 5), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(4, 7, 5), leaf);
        helper.setBlock(new BlockPos(6, 7, 5), leaf);
        helper.setBlock(new BlockPos(5, 7, 4), leaf);
        helper.setBlock(new BlockPos(5, 7, 6), leaf);
        helper.setBlock(new BlockPos(5, 9, 5), leaf);
        spawnLumberjack(helper, new BlockPos(2, 1, 4));
        helper.succeedWhen(() -> {
            int logs = 0;
            for (int y = 1; y <= 8; y++) if (helper.getBlockState(new BlockPos(5, y, 5)).is(Blocks.OAK_LOG)) logs++;
            helper.assertTrue(logs == 0, "the tall tree is felled from the top, " + logs + " of 8 logs left");
        });
    }

    /** With saplings only in the chest, the lumberjack fetches them, fells the tree, plants one and puts the rest back. */
    private static void lumberjackFetchesSaplings(GameTestHelper helper) {
        buildFarm(helper);
        buildTree(helper, 5, 5);
        Container chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.OAK_SAPLING, 3));
        spawnLumberjack(helper, new BlockPos(2, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(5, 1, 5)).is(Blocks.OAK_SAPLING), "a sapling is planted at the stump");
            helper.assertTrue(count(chest, Items.OAK_LOG) == 4, "the logs are in the chest, got " + count(chest, Items.OAK_LOG));
            // the leaves that wither once the trunk is gone may drop a sapling or two as well, and they are gathered up too
            helper.assertTrue(count(chest, Items.OAK_SAPLING) >= 2, "the two spare saplings are back in the chest, got " + count(chest, Items.OAK_SAPLING));
        });
    }

    /** A branch off the side and a second trunk are felled too, not just the column under the highest log. */
    private static void lumberjackFellsBranchingTree(GameTestHelper helper) {
        buildFarm(helper);
        buildTree(helper, 5, 5);
        helper.setBlock(new BlockPos(4, 0, 5), Blocks.GRASS_BLOCK);
        for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(4, y, 5), Blocks.OAK_LOG);
        for (int x = 6; x <= 7; x++) helper.setBlock(new BlockPos(x, 4, 5), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(7, 5, 5), Blocks.OAK_LOG);
        for (int x : new int[]{4, 6, 7}) for (int y = 6; y <= 7; y++) helper.setBlock(new BlockPos(x, y, 5), Blocks.AIR); // room above each column (the arena ceiling is low)
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        spawnLumberjack(helper, new BlockPos(2, 1, 4));
        helper.succeedWhen(() -> {
            int left = 0;
            StringBuilder where = new StringBuilder();
            for (int x = 4; x <= 7; x++) for (int y = 1; y <= 5; y++) if (helper.getBlockState(new BlockPos(x, y, 5)).is(Blocks.OAK_LOG)) { left++; where.append(x).append(',').append(y).append(' '); }
            helper.assertTrue(left == 0, "every log is felled, " + left + " left: " + where);
            helper.assertTrue(count(chest, Items.OAK_LOG) == 11, "all eleven logs reach the chest, got " + count(chest, Items.OAK_LOG));
        });
    }

    /** Stacks lying near the chest are carried off before another tree is started. */
    private static void lumberjackCollectsBeforeFelling(GameTestHelper helper) {
        buildFarm(helper);
        buildTree(helper, 5, 5);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        helper.spawnItem(Items.OAK_LOG, new net.minecraft.world.phys.Vec3(3.5, 1.5, 6.5));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(logsAt(helper, 5, 5) == 4 || count(chest, Items.OAK_LOG) >= 1, "the lying log is dealt with before the tree is touched");
            helper.succeed();
        });
    }

    /** Open grass, no trees and nothing in the chest: no sapling can appear, in the ground or in the hand. */
    private static void lumberjackNeverConjuresSaplings(GameTestHelper helper) {
        buildFarm(helper);
        grassField(helper);
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        helper.runAtTickTime(400, () -> {
            for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) {
                helper.assertTrue(!helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.OAK_SAPLING), "no sapling without one to plant");
            }
            helper.assertTrue(!jack.isCarrying(), "the hand stays empty");
            helper.succeed();
        });
    }

    private static void grassField(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
                for (int y = 6; y <= 7; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }
    }

    /** With a stack in hand and a full chest the lumberjack sits down with it (and keeps it); it stands up again once there is room. */
    private static void lumberjackSitsWhenChestFull(GameTestHelper helper) {
        buildFarm(helper);
        Container chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        WorkGolem jack = spawnLumberjack(helper, AT);
        jack.setCarried(new ItemStack(Items.OAK_LOG, 3));
        helper.succeedWhen(() -> {
            helper.assertTrue(com.scottcaldwell.industriousgolems.golem.Anim.M_SIT_FULL.equals(jack.clip()), "it sits down, clip is '" + jack.clip() + "'");
            helper.assertTrue(jack.carried().is(Items.OAK_LOG) && jack.carried().getCount() == 3, "and keeps the stack");
        });
    }

    /** Short grass covering the ground is no obstacle: the sapling replaces it. */
    private static void lumberjackPlantsThroughGrass(GameTestHelper helper) {
        buildFarm(helper);
        grassField(helper);
        for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) {
            BlockPos p = new BlockPos(x, 1, z);
            if (helper.getBlockState(p).isAir()) helper.setBlock(p, Blocks.SHORT_GRASS);
        }
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.OAK_SAPLING, 3));
        spawnLumberjack(helper, new BlockPos(2, 1, 4));
        helper.succeedWhen(() -> {
            boolean any = false;
            for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) any |= helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.OAK_SAPLING);
            helper.assertTrue(any, "a sapling goes in where the grass was");
        });
    }

    /** Cut short up in the tree (here: out of charge), the golem is put back on the ground, never inside the standing trunk. */
    private static void lumberjackInterruptedInTree(GameTestHelper helper) {
        buildFarm(helper);
        buildTree(helper, 5, 5);
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        double groundY = helper.absolutePos(new BlockPos(5, 1, 5)).getY();
        boolean[] cut = {false};
        helper.succeedWhen(() -> {
            if (!cut[0]) {
                if (jack.getY() > groundY + 3) { cut[0] = true; jack.setChargeTicks(0); }
                helper.assertTrue(false, "waiting for the golem to be up in the tree");
            }
            var level = helper.getLevel();
            helper.assertTrue(jack.getY() <= groundY + 1.5, "back on the ground");
            helper.assertTrue(level.getBlockState(jack.blockPosition()).getCollisionShape(level, jack.blockPosition()).isEmpty(), "not inside a block");
        });
    }

    /** A column of stripped logs with leaves beside it is somebody's building, not a tree. */
    private static void lumberjackIgnoresStrippedLogs(GameTestHelper helper) {
        buildFarm(helper);
        buildTree(helper, 5, 5);
        for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(5, y, 5), Blocks.STRIPPED_OAK_LOG);
        spawnLumberjack(helper, new BlockPos(2, 1, 4));
        helper.runAtTickTime(500, () -> {
            for (int y = 1; y <= 4; y++) helper.assertTrue(helper.getBlockState(new BlockPos(5, y, 5)).is(Blocks.STRIPPED_OAK_LOG), "stripped logs are left alone");
            helper.succeed();
        });
    }

    /** Dark oak only grows from a 2x2 planting: the golem never plants a single one, nor takes them out of the chest. */
    private static void lumberjackLeavesDarkOakSaplings(GameTestHelper helper) {
        buildFarm(helper);
        grassField(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.DARK_OAK_SAPLING, 4));
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        helper.runAtTickTime(400, () -> {
            for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) {
                helper.assertTrue(!helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.DARK_OAK_SAPLING), "no lone dark oak sapling");
            }
            helper.assertTrue(count(chest, Items.DARK_OAK_SAPLING) + (jack.carried().is(Items.DARK_OAK_SAPLING) ? jack.carried().getCount() : 0) == 4, "none is lost");
            helper.succeed();
        });
    }

    /** A herder takes one bucket at a time from the chest, however many there are, and milks with it. */
    private static void herderFetchesOneBucket(GameTestHelper helper) {
        buildHerdFarm(helper);
        Container chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.BUCKET, 3));
        helper.spawn(EntityTypes.COW, new BlockPos(5, 1, 5));
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        helper.assertTrue("bucket".equals(HerdWork.wants(helper.getLevel(), herder)), "a cow to milk and no bucket in hand: the herder wants one");
        helper.succeedWhen(() -> {
            helper.assertTrue(total(herder, chest, Items.MILK_BUCKET) == 1, "one cow, one milking");
            helper.assertTrue(count(chest, Items.BUCKET) == 2, "only one bucket was taken, " + count(chest, Items.BUCKET) + " left");
        });
    }

    /** The herder's sherd craving is satisfied by any decorated pot sherd. */
    private static void herderAcceptsAnySherd(GameTestHelper helper) {
        buildFarm(helper);
        WorkGolem herder = spawnDormantWorker(helper, ModProfessions.HERDER, COMPOSTER, AT);
        int sherd = -1;
        List<Offering> offerings = herder.profession().offerings();
        for (int i = 0; i < offerings.size(); i++) if (offerings.get(i).name() != null) sherd = i;
        helper.assertTrue(sherd >= 0, "the herder has a group offering");
        herder.setCravingIndex(sherd);
        helper.assertTrue(!Offerings.wants(herder, new ItemStack(Items.BRICK)), "not just anything");
        helper.assertTrue(Offerings.offer(herder, new ItemStack(Items.ARCHER_POTTERY_SHERD)), "one sherd");
        herder.setCravingIndex(sherd);
        herder.setLastOffering(-1);
        helper.assertTrue(Offerings.offer(herder, new ItemStack(Items.HEARTBREAK_POTTERY_SHERD)), "any other sherd too");
        helper.succeed();
    }

    // ---- more on the chest pattern ----------------------------------------------------------------------------------

    /** Placing the pumpkin as a player does (the placement event) builds the golem, not only the direct call. */
    private static void chestPatternViaPlaceEvent(GameTestHelper helper) {
        BlockPos base = AT;
        helper.setBlock(base, Blocks.HAY_BLOCK);
        BlockState pumpkin = Blocks.JACK_O_LANTERN.defaultBlockState();
        helper.setBlock(base.above(), pumpkin);
        var player = helper.makeMockServerPlayerInLevel();
        BlockSnapshot snapshot = BlockSnapshot.create(helper.getLevel().dimension(), helper.getLevel(), helper.absolutePos(base.above()));
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, Blocks.HAY_BLOCK.defaultBlockState(), player));
        helper.assertTrue(helper.getBlockState(base).is(ModBlocks.HAY_CHEST.get()), "the hay bale becomes the hay chest");
        helper.assertTrue(helper.getBlockState(base.above()).isAir(), "the pumpkin is used up");
        helper.assertEntityPresent(ModEntities.FARMER_GOLEM.get(), base.above());
        helper.succeed();
    }

    /** A golem needs room to stand: with a block over the pumpkin, nothing is built. */
    private static void chestPatternNeedsHeadroom(GameTestHelper helper) {
        BlockPos base = AT;
        helper.setBlock(base, Blocks.HAY_BLOCK);
        BlockState pumpkin = Blocks.CARVED_PUMPKIN.defaultBlockState();
        helper.setBlock(base.above(), pumpkin);
        helper.setBlock(base.above(2), Blocks.STONE);
        helper.assertTrue(ChestSpawn.build(helper.getLevel(), helper.absolutePos(base.above()), pumpkin) == null, "no room, no golem");
        helper.assertTrue(helper.getBlockState(base).is(Blocks.HAY_BLOCK) && helper.getBlockState(base.above()).is(Blocks.CARVED_PUMPKIN),
                "the blocks are left as they were");
        helper.succeed();
    }

    // ---- registrations ----------------------------------------------------------------------------------------------

    /** Every profession is wired to its own entity, chest, job-site type and body block, with a full set of offerings. */
    private static void registryConsistency(GameTestHelper helper) {
        List<Block> bodies = new ArrayList<>();
        for (ModProfessions.Profession profession : ModProfessions.all()) {
            String id = profession.id();
            helper.assertTrue(profession.offerings().size() == 8, id + " has eight offerings");
            for (Offering offering : profession.offerings()) {
                helper.assertTrue(!offering.icon().isEmpty() && offering.match().test(offering.icon()), id + ": an offering accepts its own icon");
            }
            helper.assertTrue(ModEntities.professionOf(ModEntities.typeOf(profession)) == profession, id + " has its own entity type");
            helper.assertTrue(profession.speed() > 0, id + " moves");
            helper.assertTrue(profession.needs().work() != null, id + " has work");

            BlockState chest = profession.chest().get().defaultBlockState();
            var poi = PoiTypes.forState(chest);
            helper.assertTrue(poi.isPresent() && profession.jobSite().test(poi.get()), id + "'s chest is its job site");
            for (ModProfessions.Profession other : ModProfessions.all()) {
                if (other != profession) {
                    helper.assertTrue(!other.jobSite().test(poi.get()), id + "'s chest is nobody else's job site");
                }
            }
            helper.assertTrue(profession.chest().get() instanceof GolemChestBlock, id + " chest is a golem chest");
            bodies.add(profession.chest().get());
        }
        // a body block builds exactly one golem
        for (BlockState body : List.of(Blocks.HAY_BLOCK.defaultBlockState(), Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(),
                Blocks.TERRACOTTA.defaultBlockState(), Blocks.DYED_TERRACOTTA.red().defaultBlockState(), Blocks.SMOOTH_STONE.defaultBlockState())) {
            long matches = ModProfessions.all().stream().filter(p -> p.bodyBlock().test(body)).count();
            helper.assertTrue(matches == 1, body.getBlock() + " should build exactly one golem, builds " + matches);
        }
        helper.assertTrue(ModProfessions.all().stream().noneMatch(p -> p.bodyBlock().test(Blocks.STONE.defaultBlockState())), "stone builds nothing");
        helper.assertTrue(bodies.stream().distinct().count() == ModProfessions.all().size(), "each golem has a chest of its own");
        helper.succeed();
    }

    // ---- miner ----------------------------------------------------------------------------------------------------

    /** Like {@link #buildFarm}, but floor and walls are smooth stone: the stone golem digs plain stone, so it must not be what the arena is made of. */
    private static void buildQuarry(GameTestHelper helper) {
        buildFarm(helper);
        for (int x = -1; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) {
                boolean edge = x == -1 || x == 8 || z == -1 || z == 8;
                for (int y = 0; y <= 3; y++) {
                    if (y == 0 || edge) helper.setBlock(new BlockPos(x, y, z), Blocks.SMOOTH_STONE);
                }
            }
        }
        helper.setBlock(COMPOSTER, Blocks.STONECUTTER);
    }

    private static WorkGolem spawnMiner(GameTestHelper helper, BlockPos at) {
        return spawnWorker(helper, ModProfessions.MINER, COMPOSTER, at);
    }

    private static int blocksOf(GameTestHelper helper, Block block, BlockPos from, BlockPos to) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) if (helper.getBlockState(pos).is(block)) n++;
        return n;
    }

    /**
     * The miner digs a rock face block by block, bare-handed, leaves the drops until a batch is done, then carries them
     * to the chest a stack at a time. Ore yields ore, and the floor it walks on (a stone block set into it) stays.
     */
    private static void minerDigsAndDelivers(GameTestHelper helper) {
        buildQuarry(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        BlockPos rockFrom = new BlockPos(5, 1, 4);
        BlockPos rockTo = new BlockPos(7, 2, 6);
        for (BlockPos pos : BlockPos.betweenClosed(rockFrom, rockTo)) helper.setBlock(pos, Blocks.STONE);
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.COAL_ORE);
        helper.setBlock(new BlockPos(6, 0, 5), Blocks.STONE); // part of the floor, below the chest's level: not to be dug
        WorkGolem miner = spawnMiner(helper, new BlockPos(3, 1, 4));

        boolean[] grabbedEarly = {false};
        helper.succeedWhen(() -> {
            int rock = blocksOf(helper, Blocks.STONE, rockFrom, rockTo) + blocksOf(helper, Blocks.COAL_ORE, rockFrom, rockTo);
            if (rock >= 7 && miner.isCarrying()) grabbedEarly[0] = true; // fewer than a batch dug: the drops are still lying about
            helper.assertTrue(!grabbedEarly[0], "the miner picked drops up in the middle of a batch");
            StringBuilder left = new StringBuilder();
            for (BlockPos pos : BlockPos.betweenClosed(rockFrom, rockTo)) {
                if (!helper.getBlockState(pos).isAir()) left.append(pos.subtract(rockFrom)).append(miner.getData(ModAttachments.TASK).ignoresBlock(helper.absolutePos(pos), helper.getLevel().getGameTime()) ? "(ignored) " : " ");
            }
            helper.assertTrue(rock == 0, rock + " blocks of the rock face are still standing: " + left + describe(helper, miner, chest));
            helper.assertTrue(count(chest, Items.COBBLESTONE) == 17, "17 cobblestone should be in the chest, got " + count(chest, Items.COBBLESTONE) + describe(helper, miner, chest));
            helper.assertTrue(count(chest, Items.COAL) >= 1, "the coal ore should have yielded coal" + describe(helper, miner, chest));
            helper.assertTrue(!miner.isCarrying(), "nothing is carried afterwards");
            helper.assertTrue(helper.getBlockState(new BlockPos(6, 0, 5)).is(Blocks.STONE), "the floor must not be dug");
        });
    }

    /** What the miner will and will not dig, and where it will not look. */
    private static void minerDigRules(GameTestHelper helper) {
        buildQuarry(helper);
        var level = helper.getLevel();
        BlockPos chest = helper.absolutePos(CHEST);
        BlockPos from = helper.absolutePos(new BlockPos(3, 1, 4));

        BlockPos open = helper.absolutePos(new BlockPos(5, 1, 4));
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.STONE);
        helper.assertTrue(MineFinder.isMineable(level, open), "stone with air beside it is dug");

        helper.setBlock(new BlockPos(5, 1, 4), Blocks.COBBLESTONE);
        helper.assertTrue(!MineFinder.isMineable(level, open), "cobblestone is somebody's building, not rock");
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.OBSIDIAN);
        helper.assertTrue(!MineFinder.isMineable(level, open), "obsidian is beyond an iron pick");
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.DIAMOND_ORE);
        helper.assertTrue(MineFinder.isMineable(level, open), "diamond ore is dug");

        // buried: stone on every side is left in the ground, the faces of the same lump are not
        BlockPos lumpFrom = new BlockPos(5, 1, 5);
        BlockPos lumpTo = new BlockPos(7, 3, 7);
        for (BlockPos pos : BlockPos.betweenClosed(lumpFrom, lumpTo)) helper.setBlock(pos, Blocks.STONE);
        helper.assertTrue(!MineFinder.isMineable(level, helper.absolutePos(new BlockPos(6, 2, 6))), "stone with stone on every side is left in the ground");
        helper.assertTrue(MineFinder.isMineable(level, helper.absolutePos(new BlockPos(6, 3, 6))), "the top of the lump faces the air");
        for (BlockPos pos : BlockPos.betweenClosed(lumpFrom, lumpTo)) helper.setBlock(pos, Blocks.AIR);

        // liquid and falling blocks make a cut unsafe
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 1, 3), Blocks.WATER);
        helper.assertTrue(!MineFinder.isMineable(level, open), "stone beside water would flood the tunnel");
        helper.setBlock(new BlockPos(5, 1, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.GRAVEL);
        helper.assertTrue(!MineFinder.isMineable(level, open), "stone under gravel would bring it down");
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.AIR);
        helper.assertTrue(MineFinder.isMineable(level, open), "and is fine again once that is gone");

        // finds it, but only it; the ignore list is honoured; nothing below the chest's own level is ever a target
        MineFinder.Target target = MineFinder.find(level, chest, from, pos -> false);
        helper.assertTrue(target != null && target.block().equals(open), "the exposed stone is the target, got " + target);
        helper.assertTrue(MineFinder.find(level, chest, from, open::equals) == null, "an ignored block is not a target");
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.AIR);
        helper.setBlock(new BlockPos(6, 0, 5), Blocks.STONE);
        helper.assertTrue(MineFinder.find(level, chest, from, pos -> false) == null, "the floor below the chest's level is never dug");
        helper.setBlock(new BlockPos(6, 0, 5), Blocks.SMOOTH_STONE);
        helper.setBlock(new BlockPos(5, 1, 4 + MineFinder.RADIUS + 2), Blocks.STONE);
        helper.assertTrue(MineFinder.find(level, chest, from, pos -> false) == null, "rock beyond the dig radius is left alone");
        helper.succeed();
    }

    /** Rock the miner cannot get within reach of is given up on, so the rest of the work goes on. */
    private static void minerIgnoresUnreachableRock(GameTestHelper helper) {
        buildQuarry(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        // a stone block walled in by smooth stone two high, open only from above where nothing can climb to
        BlockPos sealed = new BlockPos(7, 1, 7);
        helper.setBlock(sealed, Blocks.STONE);
        for (int y = 1; y <= 2; y++) {
            helper.setBlock(new BlockPos(6, y, 7), Blocks.SMOOTH_STONE);
            helper.setBlock(new BlockPos(7, y, 6), Blocks.SMOOTH_STONE);
            helper.setBlock(new BlockPos(6, y, 6), Blocks.SMOOTH_STONE);
        }
        BlockPos reachable = new BlockPos(5, 1, 3);
        helper.setBlock(reachable, Blocks.STONE);
        WorkGolem miner = spawnMiner(helper, new BlockPos(3, 1, 4));

        BlockPos sealedAbs = helper.absolutePos(sealed);
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(reachable).isAir(), "the reachable stone should be dug" + describe(helper, miner, chest));
            helper.assertTrue(count(chest, Items.COBBLESTONE) == 1, "and carried to the chest" + describe(helper, miner, chest));
            helper.assertTrue(helper.getBlockState(sealed).is(Blocks.STONE), "the sealed stone cannot be dug");
            helper.assertTrue(miner.getData(ModAttachments.TASK).ignoresBlock(sealedAbs, helper.getLevel().getGameTime()), "the golem should have given up on the sealed stone");
        });
    }
}
