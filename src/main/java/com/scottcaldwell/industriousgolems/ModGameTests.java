package com.scottcaldwell.industriousgolems;

import net.minecraft.data.worldgen.features.TreeFeatures;
import com.scottcaldwell.industriousgolems.lumber.TreeFinder;
import com.scottcaldwell.industriousgolems.block.GolemChestBlock;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.ChestSpawn;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import com.scottcaldwell.industriousgolems.herd.HerdPen;
import com.scottcaldwell.industriousgolems.herd.HerdWork;
import com.scottcaldwell.industriousgolems.mine.Kit;
import com.scottcaldwell.industriousgolems.mine.MineFinder;
import com.scottcaldwell.industriousgolems.mine.Trunk;
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
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
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
        strictly("herder_breeds_without_bucket", ModGameTests::herderBreedsWithoutBucket);
        strictly("herder_crosses_over_at_capacity", ModGameTests::herderCrossesOverAtCapacity);
        herderMatrix("herder_matrix_fresh_wheat", Pen.FRESH, () -> new ItemStack[]{new ItemStack(Items.WHEAT, 16)}, false, true, null);
        herderMatrix("herder_matrix_fresh_bucket", Pen.FRESH, () -> new ItemStack[]{new ItemStack(Items.BUCKET, 1)}, true, false, null);
        herderMatrix("herder_matrix_fresh_bucket_and_wheat", Pen.FRESH, () -> new ItemStack[]{new ItemStack(Items.BUCKET, 1), new ItemStack(Items.WHEAT, 16)}, true, true, null);
        herderMatrix("herder_matrix_fresh_nothing", Pen.FRESH, () -> new ItemStack[]{}, false, false, null);
        herderMatrix("herder_matrix_fresh_carrots_only", Pen.FRESH, () -> new ItemStack[]{new ItemStack(Items.CARROT, 16)}, false, false, Items.CARROT);
        herderMatrix("herder_matrix_fresh_carrots_then_wheat", Pen.FRESH, () -> new ItemStack[]{new ItemStack(Items.CARROT, 16), new ItemStack(Items.WHEAT, 16)}, false, true, Items.CARROT);
        herderMatrix("herder_matrix_milked_wheat", Pen.MILKED, () -> new ItemStack[]{new ItemStack(Items.WHEAT, 16)}, false, true, null);
        herderMatrix("herder_matrix_milked_bucket", Pen.MILKED, () -> new ItemStack[]{new ItemStack(Items.BUCKET, 1)}, false, false, Items.BUCKET);
        strictly("lumberjack_sneezes_out_of_pit", ModGameTests::lumberjackSneezesOutOfPit);
        strictly("pen_detection", ModGameTests::penDetection);
        strictly("lumberjack_fells_and_replants", ModGameTests::lumberjackFellsAndReplants);
        strictly("lumberjack_ignores_log_building", ModGameTests::lumberjackIgnoresLogBuilding);
        strictly("golem_dormant_until_offered", ModGameTests::golemDormantUntilOffered);
        strictly("golem_offering_rules", ModGameTests::golemOfferingRules);
        strictly("golem_charge_runs_out", ModGameTests::golemChargeRunsOut);
        FUNCTIONS.register("hay_chest_pattern", () -> h -> chestPattern(h, ModProfessions.FARMER, ModBlocks.HAY_CHEST.get()));
        FUNCTIONS.register("mushroom_chest_pattern", () -> h -> chestPattern(h, ModProfessions.LUMBERJACK, ModBlocks.MUSHROOM_CHEST.get()));
        FUNCTIONS.register("terracotta_chest_pattern", () -> h -> chestPattern(h, ModProfessions.HERDER, ModBlocks.TERRACOTTA_CHEST.get()));
        strictly("chest_pattern_needs_matching_body", ModGameTests::chestPatternNeedsMatchingBody);
        strictly("chest_pattern_needs_whole_shape", ModGameTests::chestPatternNeedsWholeShape);
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
        FUNCTIONS.register("stone_chest_pattern", () -> h -> chestPattern(h, ModProfessions.MINER, ModBlocks.STONE_CHEST.get()));
        strictly("golem_sends_produce_to_copper_chest", ModGameTests::golemSendsProduceToCopperChest);
        strictly("golem_chests_join_into_doubles", ModGameTests::golemChestsJoinIntoDoubles);
        strictly("golem_climbs_to_raised_chest", ModGameTests::golemClimbsToRaisedChest);
        strictly("golem_sneezes_out_from_under_leaves", ModGameTests::golemSneezesOutFromUnderLeaves);
        strictly("golem_uses_gate_and_shuts_it", ModGameTests::golemUsesGate);
        strictly("golems_share_one_chest", ModGameTests::golemsShareOneChest);
        strictly("golem_does_not_trample_farmland", ModGameTests::golemDoesNotTrampleFarmland);
        strictly("herder_holds_the_animal_where_it_was", ModGameTests::herderHoldsTheAnimalWhereItWas);
        strictly("herder_spares_named_animals", ModGameTests::herderSparesNamedAnimals);
        strictly("herder_keeps_animals_in_at_gate", ModGameTests::herderKeepsAnimalsInAtGate);
        strictly("golem_shuts_gate_when_killed", ModGameTests::golemShutsGateWhenKilled);
        strictly("farmer_sows_pitcher_pods", ModGameTests::farmerSowsPitcherPods);
        strictly("farmer_returns_unsowable_seeds", ModGameTests::farmerReturnsUnsowableSeeds);
        strictly("golem_ignores_furnaces", ModGameTests::golemIgnoresFurnaces);
        strictly("miner_does_not_build_into_animals", ModGameTests::minerDoesNotBuildIntoAnimals);
        strictly("golem_deposits_into_double_chest", ModGameTests::golemDepositsIntoDoubleChest);
        strictly("golem_keeps_stack_in_flight", ModGameTests::golemKeepsStackInFlight);
        strictly("herder_has_no_pig_act_for_chickens", ModGameTests::herderHasNoPigActForChickens);
        strictly("lumberjack_leaves_tree_joined_to_a_build", ModGameTests::lumberjackLeavesTreeJoinedToABuild);
        strictly("natural_trees_are_fellable", ModGameTests::naturalTreesAreFellable);
        strictly("golems_leave_each_others_targets", ModGameTests::golemsLeaveEachOthersTargets);
        strictly("lumberjack_breaks_one_log_at_a_time", ModGameTests::lumberjackBreaksOneLogAtATime);
        strictly("miner_dig_rules", ModGameTests::minerDigRules);
        strictly("miner_trunk_plan", ModGameTests::minerTrunkPlan);
        strictly("miner_follows_veins", ModGameTests::minerFollowsVeins);
        strictly("miner_cowers_from_mobs", ModGameTests::minerCowersFromMobs);
        strictly("miner_digs_hub_and_arm", ModGameTests::minerDigsHubAndArm);
        strictly("miner_waits_for_kit", ModGameTests::minerWaitsForKit);
        strictly("miner_pack_slots", ModGameTests::minerPackSlots);
        strictly("miner_builds_support", ModGameTests::minerBuildsSupport);
        strictly("miner_path_through_brace", ModGameTests::minerPathThroughBrace);
        strictly("miner_takes_a_column_per_swing", ModGameTests::minerTakesAColumnPerSwing);
        strictly("miner_clears_a_solid_hub", ModGameTests::minerClearsASolidHub);
        strictly("golem_meets_chest_face_on_and_opens_it", ModGameTests::golemMeetsChestFaceOn);
        strictly("lumberjack_plants_spaced_saplings", ModGameTests::lumberjackPlantsSpacedSaplings);
        strictly("lumberjack_fells_branches", ModGameTests::lumberjackFellsBranchingTree);
        strictly("lumberjack_collects_before_felling", ModGameTests::lumberjackCollectsBeforeFelling);
        strictly("lumberjack_never_conjures_saplings", ModGameTests::lumberjackNeverConjuresSaplings);
        strictly("lumberjack_plants_through_grass", ModGameTests::lumberjackPlantsThroughGrass);
        strictly("lumberjack_sits_when_chest_full", ModGameTests::lumberjackSitsWhenChestFull);
        strictly("lumberjack_keeps_item_in_view", ModGameTests::lumberjackKeepsItemInView);
        strictly("lumberjack_clamber_stays_on_the_edge", ModGameTests::lumberjackClamberStaysOnTheEdge);
        strictly("lumberjack_grove_spacing", ModGameTests::lumberjackGroveSpacing);
        strictly("lumberjack_clears_around_chest", ModGameTests::lumberjackClearsAroundChest);
        strictly("lumberjack_fells_far_tree", ModGameTests::lumberjackFellsFarTree);
        strictly("lumberjack_fetches_far_drop", ModGameTests::lumberjackFetchesFarDrop);
        strictly("lumberjack_stands_up_when_chest_has_room", ModGameTests::lumberjackStandsUp);
        strictly("lumberjack_chest_broken_mid_put", ModGameTests::lumberjackChestBrokenMidPut);
        strictly("lumberjack_interrupted_in_tree", ModGameTests::lumberjackInterruptedInTree);
        strictly("lumberjack_ignores_stripped_logs", ModGameTests::lumberjackIgnoresStrippedLogs);
        strictly("lumberjack_leaves_dark_oak_saplings", ModGameTests::lumberjackLeavesDarkOakSaplings);
        strictly("lumberjack_tops_up_stack", ModGameTests::lumberjackTopsUpStack);
        strictly("mushroom_golem_takes_no_fall_damage", ModGameTests::mushroomTakesNoFallDamage);
        strictly("golem_chest_recounts_openers", ModGameTests::golemChestRecountsOpeners);
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
                        "farmer should have replanted at x=" + x + " (soil " + helper.getBlockState(new BlockPos(3, 1, 5)).getBlock() + helper.getBlockState(new BlockPos(4, 1, 5)).getBlock() + helper.getBlockState(new BlockPos(5, 1, 5)).getBlock() + ", plots " + helper.getBlockState(new BlockPos(3, 2, 5)) + helper.getBlockState(new BlockPos(4, 2, 5))
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
            helper.assertTrue(!sheep.isEmpty() && !sheep.get(0).readyForShearing(), "the sheep should have been sheared: " + sheep.size() + " sheep" + (sheep.isEmpty() ? "" : " at " + sheep.get(0).position().subtract(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)))) + describe(helper, herder, chest));
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

    /**
     * Two cows that could be milked, a chest with wheat but no bucket: the herder must still fetch the wheat and breed them
     * (a milking it cannot do must not hide the breeding it can).
     */
    private static void herderBreedsWithoutBucket(GameTestHelper helper) {
        buildHerdFarm(helper);
        Container chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.WHEAT, 16));
        helper.spawn(EntityTypes.COW, new BlockPos(5, 1, 5));
        helper.spawn(EntityTypes.COW, new BlockPos(7, 1, 6));
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        helper.assertTrue(suppliesWanted(helper, herder).contains("feed"), "the pen has room, so it wants feed (as well as a bucket, which the chest lacks)");
        helper.succeedWhen(() -> {
            helper.assertTrue(alive(helper, Cow.class).size() >= 3 || alive(helper, Cow.class).stream().anyMatch(c -> c.isInLove()), "the cows should have been fed");
            helper.assertTrue(total(herder, chest, Items.WHEAT) < 16, "the herder should have taken wheat");
        });
    }

    /**
     * What a bare-handed herder offers to do in the 16-cell pen (capacity four) at each head count: breed below capacity, cull above it, both at
     * one over (the crossover that keeps the pen producing), and never cull the last pair.
     */
    private static void herderCrossesOverAtCapacity(GameTestHelper helper) {
        buildHerdFarm(helper);
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        var pigs = new java.util.ArrayList<net.minecraft.world.entity.animal.pig.Pig>();
        helper.runAfterDelay(2, () -> {
            for (int count = 2; count <= 6; count++) {
                while (pigs.size() < count) pigs.add(helper.spawn(EntityTypes.PIG, new BlockPos(4 + pigs.size() % 4, 1, 4 + pigs.size() / 4)));
                boolean cull = false, breed = false;
                for (var job : HerdWork.jobs(helper.getLevel(), herder).toList()) {
                    if (job.plan() instanceof com.scottcaldwell.industriousgolems.herd.HerdPlan p && p.action() == com.scottcaldwell.industriousgolems.herd.HerdPlan.Action.CULL) cull = true;
                    if (job.needsSupply() && job.supply().equals("feed")) breed = true;
                }
                boolean wantCull = count > 4, wantBreed = count <= 5;
                helper.assertTrue(cull == wantCull, count + " pigs in 16 cells: cull should be " + wantCull);
                var pens = HerdPen.find(helper.getLevel(), herder.jobSite(helper.getLevel()));
                helper.assertTrue(breed == wantBreed, count + " pigs in 16 cells: breed should be " + wantBreed + " (pens: "
                        + pens.stream().map(p -> p.animals.size() + "/" + p.cells.size()).toList() + ")");
            }
            helper.succeed();
        });
    }

    /** The supplies the herder's jobs would need, as the dispatcher sees them. */
    private static List<String> suppliesWanted(GameTestHelper helper, WorkGolem golem) {
        return HerdWork.jobs(helper.getLevel(), golem).filter(com.scottcaldwell.industriousgolems.needs.Job::needsSupply)
                .map(com.scottcaldwell.industriousgolems.needs.Job::supply).toList();
    }

    private enum Pen { FRESH, MILKED }

    /** Registers one cell of the herder matrix. */
    private static void herderMatrix(String name, Pen pen, java.util.function.Supplier<ItemStack[]> stock, boolean milks, boolean feeds, net.minecraft.world.item.Item never) {
        strictly(name, helper -> herderMatrixCase(helper, pen, stock, milks, feeds, never));
    }

    /**
     * The herder's supply decisions, every combination of what the chest holds and what the two cows (room to breed in the pen) are fit for:
     * it must do exactly the jobs that are possible, and never walk off with something it cannot use ({@code never}). When nothing is possible
     * it must stay idle, which is checked once it has had time to do something foolish.
     */
    private static void herderMatrixCase(GameTestHelper helper, Pen pen, java.util.function.Supplier<ItemStack[]> stockOf, boolean milks, boolean feeds, net.minecraft.world.item.Item never) {
        buildHerdFarm(helper);
        Container chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        ItemStack[] stock = stockOf.get();
        for (int i = 0; i < stock.length; i++) chest.setItem(i, stock[i]);
        int wheat0 = count(chest, Items.WHEAT), bucket0 = count(chest, Items.BUCKET);
        var cows = List.of(helper.spawn(EntityTypes.COW, new BlockPos(5, 1, 5)), helper.spawn(EntityTypes.COW, new BlockPos(7, 1, 6)));
        if (pen == Pen.MILKED) for (var cow : cows) cow.setData(ModAttachments.MILKED_AT, helper.getLevel().getGameTime());
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        boolean[] carriedUseless = {false};
        helper.succeedWhen(() -> {
            if (never != null && herder.carried().is(never)) carriedUseless[0] = true;
            helper.assertTrue(!carriedUseless[0], "the herder took something nothing here can use");
            if (milks) helper.assertTrue(total(herder, chest, Items.MILK_BUCKET) > 0, "a cow should have been milked");
            if (feeds) helper.assertTrue(total(herder, chest, Items.WHEAT) < wheat0, "the cows should have been fed");
            if (!milks && !feeds) helper.assertTrue(helper.getTick() >= 600, "idle: waiting to see that it stays idle");
            if (!milks) helper.assertTrue(total(herder, chest, Items.MILK_BUCKET) == 0, "nothing to milk with, or the cows are not ready");
            if (!feeds) helper.assertTrue(total(herder, chest, Items.WHEAT) == wheat0 && cows.stream().noneMatch(c -> c.isInLove()), "no usable feed");
            helper.assertTrue(total(herder, chest, Items.BUCKET) + total(herder, chest, Items.MILK_BUCKET) >= bucket0, "buckets are not lost");
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
        helper.setBlock(new BlockPos(5, 1, 7), Blocks.OAK_FENCE);
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.HAY_BLOCK); // something inside to climb on does not let them out
        helper.assertTrue(HerdPen.cellsAround(level, helper.absolutePos(new BlockPos(8, 1, 8))) != null, "a hay bale inside the pen");
        for (int i = 5; i <= 10; i++) {
            for (int j = 5; j <= 10; j++) {
                if (i == 5 || i == 10 || j == 5 || j == 10) helper.setBlock(new BlockPos(i, 1, j), Blocks.GRASS_BLOCK);
            }
        }
        helper.assertTrue(HerdPen.cellsAround(level, helper.absolutePos(new BlockPos(8, 1, 8))) == null, "animals jump out of a ring one block high");
        for (int i = 5; i <= 10; i++) {
            for (int j = 5; j <= 10; j++) {
                if (i == 5 || i == 10 || j == 5 || j == 10) helper.setBlock(new BlockPos(i, 2, j), Blocks.GRASS_BLOCK);
            }
        }
        helper.assertTrue(HerdPen.cellsAround(level, helper.absolutePos(new BlockPos(8, 1, 8))) != null, "two high holds them");
        helper.succeed();
    }

    // ---- lumberjack -----------------------------------------------------------------------------------------

    /**
     * Boxed in by walls it cannot climb, with its chest out of reach: no job can even be planned, so it is idle with nowhere to walk, and it
     * must sneeze its way out toward the chest instead of staying put for good.
     */
    private static void lumberjackSneezesOutOfPit(GameTestHelper helper) {
        buildFarm(helper);
        for (int x = 5; x <= 7; x++) {
            for (int z = 5; z <= 7; z++) {
                if (x == 6 && z == 6) continue;
                for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        WorkGolem golem = spawnLumberjack(helper, new BlockPos(6, 1, 6));
        helper.succeedWhen(() -> {
            BlockPos at = golem.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
            helper.assertTrue(Math.abs(at.getX() - 6) > 1 || Math.abs(at.getZ() - 6) > 1, "still in the pit at " + at);
        });
    }

    private static WorkGolem spawnLumberjack(GameTestHelper helper, BlockPos at) {
        helper.setBlock(COMPOSTER, Blocks.FLETCHING_TABLE);
        return spawnWorker(helper, ModProfessions.LUMBERJACK, COMPOSTER, at);
    }

    /** A 4-log oak with natural leaves rooted in grass at (x, z). */
    private static void buildTree(GameTestHelper helper, int x, int z) {
        buildTree(helper, x, z, 0);
    }

    /** The same, {@code up} blocks higher (the sky platform). */
    private static void buildTree(GameTestHelper helper, int x, int z, int up) {
        helper.setBlock(new BlockPos(x, up, z), Blocks.GRASS_BLOCK);
        var leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int y = 4; y <= 5; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 && y == 4) continue;
                    helper.setBlock(new BlockPos(x + dx, up + y, z + dz), leaf);
                }
            }
        }
        for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(x, up + y, z), Blocks.OAK_LOG);
        for (int y = 6; y <= 7; y++) helper.setBlock(new BlockPos(x, up + y, z), Blocks.AIR); // the arena's low ceiling: room to stand on top of the crown
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
            helper.assertTrue(saplingInArena(helper), "a sapling should be planted in the grove" + describe(helper, jack, chest) + " saplings lying " + itemsOnGround(helper, Items.OAK_SAPLING) + " chest saplings " + count(chest, Items.OAK_SAPLING) + " logs lying " + itemsOnGround(helper, Items.OAK_LOG) + " plan " + jack.getData(com.scottcaldwell.industriousgolems.ModAttachments.TASK).plan);
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
        // walled in, so it cannot walk to a loom in a neighbouring arena and claim it as a job site
        for (int y = 1; y <= 2; y++) {
            for (BlockPos wall : new BlockPos[] {new BlockPos(4, y, 5), new BlockPos(6, y, 5), new BlockPos(5, y, 4), new BlockPos(5, y, 6)}) helper.setBlock(wall, Blocks.STONE);
        }
        WorkGolem homeless = helper.spawn(ModEntities.HERDER_GOLEM.get(), new BlockPos(5, 1, 5));
        homeless.setChargeTicks(60);
        helper.succeedWhen(() -> {
            helper.assertTrue(farmer.chargeTicks() == 0 && !farmer.isCharged(), "the farmer should have run out, has " + farmer.chargeTicks());
            helper.assertTrue(farmer.getBrain().isActive(Activity.REST), "a dormant golem rests");
            helper.assertTrue(homeless.chargeTicks() == 60, "a golem without a job site does not use charge, has " + homeless.chargeTicks());
        });
    }

    // ---- the chest spawn pattern ----------------------------------------------------------------------------------

    /**
     * Lays the golem's pattern below {@code top} (everything under the pumpkin, nothing else) and returns where its chest will be:
     * the hay golem is a hay bale under a fence, the mushroom golem a red mushroom block, the terracotta golem two terracotta, the stone
     * golem a T of smooth stone.
     */
    private static BlockPos layPattern(GameTestHelper helper, ModProfessions.Profession profession, BlockPos top) {
        if (profession == ModProfessions.FARMER) {
            helper.setBlock(top.below(), Blocks.OAK_FENCE);
            helper.setBlock(top.below(2), Blocks.HAY_BLOCK);
            return top.below(2);
        }
        if (profession == ModProfessions.LUMBERJACK) {
            helper.setBlock(top.below(), Blocks.RED_MUSHROOM_BLOCK);
            return top.below();
        }
        if (profession == ModProfessions.HERDER) {
            helper.setBlock(top.below(), Blocks.DYED_TERRACOTTA.red()); // (any kind, and they need not match)
            helper.setBlock(top.below(2), Blocks.TERRACOTTA);
            return top.below(2);
        }
        BlockPos middle = top.below();
        for (BlockPos at : List.of(middle, middle.east(), middle.west(), middle.below())) helper.setBlock(at, Blocks.SMOOTH_STONE);
        return middle.below();
    }

    /** The golem's pattern with a carved pumpkin on top becomes the golem standing on its chest, bound to it and ready to be woken. */
    private static void chestPattern(GameTestHelper helper, ModProfessions.Profession profession, Block chest) {
        BlockPos top = new BlockPos(3, 3, 3);
        BlockPos base = layPattern(helper, profession, top);
        var pumpkin = Blocks.CARVED_PUMPKIN.defaultBlockState()
                .setValue(CarvedPumpkinBlock.FACING, Direction.SOUTH);
        helper.setBlock(top, pumpkin);
        WorkGolem golem = ChestSpawn.build(helper.getLevel(), helper.absolutePos(top), pumpkin);
        helper.assertTrue(golem != null, "the pattern should spawn a golem");
        helper.assertTrue(golem.profession() == profession, "the right kind of golem");
        helper.assertTrue(helper.getBlockState(base).is(chest), "the lowest block should become the golem's chest");
        helper.assertTrue(helper.getBlockState(top).isAir(), "the pumpkin should be gone");
        for (BlockPos at : BlockPos.betweenClosed(top.offset(-1, -2, -1), top.offset(1, -1, 1))) {
            helper.assertTrue(at.equals(base) || helper.getBlockState(at).isAir(), "the rest of the pattern should be used up at " + at);
        }
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

    /** Two chests of the same kind side by side are one 54-slot double chest; a chest of another kind does not join them. */
    private static void golemChestsJoinIntoDoubles(GameTestHelper helper) {
        Block[] kinds = {ModBlocks.HAY_CHEST.get(), ModBlocks.MUSHROOM_CHEST.get(), ModBlocks.TERRACOTTA_CHEST.get(), ModBlocks.STONE_CHEST.get()};
        for (int i = 0; i < kinds.length; i++) {
            Block kind = kinds[i];
            BlockPos first = new BlockPos(1 + i * 2, 2, 1);
            BlockState right = kind.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.RIGHT);
            BlockPos second = first.relative(ChestBlock.getConnectedDirection(right));
            BlockState left = right.setValue(ChestBlock.TYPE, ChestType.LEFT);
            helper.setBlock(first, right);
            helper.setBlock(second, left);
            helper.assertTrue(((ChestBlock) kind).chestCanConnectTo(left), "a " + kind + " joins another of its kind");
            Container whole = ChestBlock.getContainer((ChestBlock) kind, helper.getBlockState(first), helper.getLevel(), helper.absolutePos(first), true);
            helper.assertTrue(whole != null && whole.getContainerSize() == 54, "the pair of " + kind + " should be a 54 slot double chest");
            for (Block other : kinds) {
                if (other != kind) helper.assertTrue(!((ChestBlock) kind).chestCanConnectTo(other.defaultBlockState()), kind + " does not join " + other);
            }
        }
        helper.succeed();
    }

    /** Part of a pattern is not enough: each golem's shape must be all there. */
    private static void chestPatternNeedsWholeShape(GameTestHelper helper) {
        BlockPos top = new BlockPos(3, 3, 3);
        BlockState pumpkin = Blocks.CARVED_PUMPKIN.defaultBlockState();
        helper.setBlock(top, pumpkin);
        // hay without its fence, fence without hay
        helper.setBlock(top.below(), Blocks.HAY_BLOCK);
        helper.assertTrue(ChestSpawn.build(helper.getLevel(), helper.absolutePos(top), pumpkin) == null, "hay alone builds nothing");
        helper.setBlock(top.below(), Blocks.OAK_FENCE);
        helper.assertTrue(ChestSpawn.build(helper.getLevel(), helper.absolutePos(top), pumpkin) == null, "a fence alone builds nothing");
        // one terracotta
        helper.setBlock(top.below(), Blocks.TERRACOTTA);
        helper.assertTrue(ChestSpawn.build(helper.getLevel(), helper.absolutePos(top), pumpkin) == null, "one terracotta builds nothing");
        // a T of stone missing an arm, then missing its foot, then a stack
        helper.setBlock(top.below(), Blocks.SMOOTH_STONE);
        helper.setBlock(top.below(2), Blocks.SMOOTH_STONE);
        helper.setBlock(top.below().east(), Blocks.SMOOTH_STONE);
        helper.assertTrue(ChestSpawn.build(helper.getLevel(), helper.absolutePos(top), pumpkin) == null, "a T with one arm builds nothing");
        helper.setBlock(top.below().east(), Blocks.AIR);
        helper.setBlock(top.below().east(), Blocks.AIR);
        helper.assertTrue(ChestSpawn.build(helper.getLevel(), helper.absolutePos(top), pumpkin) == null, "a stack of stone is not a T");
        helper.succeed();
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

    /** What the golem works with (seeds) goes in its own chest, not a copper chest, a plain chest or another golem's chest beside it. */
    /** While one golem holds a task on a crop or a cow, another is not offered the same one. */
    private static void golemsLeaveEachOthersTargets(GameTestHelper helper) {
        buildFarm(helper);
        BlockPos crop = new BlockPos(5, 2, 5);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        WorkGolem first = spawnFarmer(helper, new BlockPos(2, 1, 4));
        WorkGolem second = spawnFarmer(helper, new BlockPos(3, 1, 4));
        var level = helper.getLevel();
        helper.assertTrue(com.scottcaldwell.industriousgolems.farm.TendCrops.jobs(level, second).findAny().isPresent(), "the ripe wheat is work");
        first.getData(com.scottcaldwell.industriousgolems.ModAttachments.TASK).plan = new com.scottcaldwell.industriousgolems.farm.TendCrops.CropPlan(
                com.scottcaldwell.industriousgolems.farm.TendCrops.Kind.REAP, helper.absolutePos(crop));
        helper.assertTrue(com.scottcaldwell.industriousgolems.farm.TendCrops.jobs(level, second).findAny().isEmpty(), "but not while the other golem is reaping it");

        for (int i = 1; i <= 5; i++) {
            helper.setBlock(new BlockPos(i, 1, 1), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(1, 1, i), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(5, 1, i), Blocks.OAK_FENCE);
            helper.setBlock(new BlockPos(i, 1, 5), Blocks.OAK_FENCE);
        }
        helper.setBlock(crop, Blocks.AIR);
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(3, 1, 3));
        WorkGolem milker = spawnWorker(helper, ModProfessions.HERDER, COMPOSTER, new BlockPos(6, 1, 3));
        WorkGolem other = spawnWorker(helper, ModProfessions.HERDER, COMPOSTER, new BlockPos(6, 1, 4));
        other.setCarried(new ItemStack(Items.BUCKET));
        helper.assertTrue(HerdWork.jobs(level, other).findAny().isPresent(), "the cow can be milked");
        milker.getData(com.scottcaldwell.industriousgolems.ModAttachments.TASK).plan = new com.scottcaldwell.industriousgolems.herd.HerdPlan(
                com.scottcaldwell.industriousgolems.herd.HerdPlan.Action.MILK, cow.getId());
        helper.assertTrue(HerdWork.jobs(level, other).findAny().isEmpty(), "but not while the other golem is milking it");
        // arenas stand side by side and a pen is looked for 16 blocks about: leave nobody behind to work the neighbours'
        for (var made : List.of(first, second, milker, other, cow)) made.discard();
        helper.succeed();
    }

    /** A double chest is put into like any other. */
    private static void golemDepositsIntoDoubleChest(GameTestHelper helper) {
        buildFarm(helper);
        BlockState right = ModBlocks.HAY_CHEST.get().defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.RIGHT);
        BlockPos second = COMPOSTER.relative(ChestBlock.getConnectedDirection(right));
        helper.setBlock(CHEST, Blocks.AIR);
        helper.setBlock(COMPOSTER, right);
        helper.setBlock(second, right.setValue(ChestBlock.TYPE, ChestType.LEFT));
        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 1, 4));
        farmer.setCarried(new ItemStack(Items.WHEAT_SEEDS, 5));
        helper.succeedWhen(() -> {
            int in = count(helper.getBlockEntity(COMPOSTER, ChestBlockEntity.class), Items.WHEAT_SEEDS)
                    + count(helper.getBlockEntity(second, ChestBlockEntity.class), Items.WHEAT_SEEDS);
            helper.assertTrue(in == 5 && !farmer.isCarrying(), "the seeds go into the double chest, " + in + " there, carrying " + farmer.carried());
        });
    }

    /** Between the hand letting go and the stack landing in the chest it is still the golem's: saved with it, and dropped if it dies. */
    private static void golemKeepsStackInFlight(GameTestHelper helper) {
        buildFarm(helper);
        WorkGolem jack = spawnLumberjack(helper, AT);
        jack.setCarried(new ItemStack(Items.OAK_LOG, 3));
        boolean[] done = {false};
        int[] seen = {-1, -1};
        helper.onEachTick(() -> {
            if (done[0] || !jack.carried().isEmpty()) return;
            done[0] = true; // the hand has just let go
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            jack.saveWithoutId(out);
            WorkGolem copy = ModEntities.LUMBERJACK_GOLEM.get().create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
            copy.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), out.buildResult()));
            seen[0] = copy.carried().is(Items.OAK_LOG) ? copy.carried().getCount() : 0;
            jack.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000.0F);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(done[0] && jack.isRemoved(), "waiting for the golem to let go and die");
            if (seen[1] < 0) seen[1] = itemsOnGround(helper, Items.OAK_LOG) + count(helper.getBlockEntity(CHEST, ChestBlockEntity.class), Items.OAK_LOG);
            helper.assertTrue(seen[0] == 3, "a golem saved then has the stack when loaded, got " + seen[0]);
            helper.assertTrue(seen[1] == 3, "and one that dies then drops it, found " + seen[1]);
        });
    }

    /** There are no chicken animations: the herder does the job to a chicken without playing another animal's. */
    private static void herderHasNoPigActForChickens(GameTestHelper helper) {
        buildHerdFarm(helper);
        helper.getBlockEntity(CHEST, ChestBlockEntity.class).setItem(0, new ItemStack(Items.WHEAT_SEEDS, 16));
        var first = helper.spawn(EntityTypes.CHICKEN, new BlockPos(5, 1, 5));
        var second = helper.spawn(EntityTypes.CHICKEN, new BlockPos(6, 1, 5));
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        String[] wrong = {null};
        boolean[] fed = new boolean[2];
        helper.onEachTick(() -> {
            if (herder.clip().startsWith("feed-") || herder.clip().startsWith("cull-")) wrong[0] = herder.clip();
            fed[0] |= first.isInLove();
            fed[1] |= second.isInLove();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(fed[0] && fed[1], "both chickens get fed");
            helper.assertTrue(wrong[0] == null, "played " + wrong[0] + " for a chicken");
            for (var made : List.of(first, second, herder)) made.discard(); // none left to wander the neighbouring arenas' pens
        });
    }

    /** Logs joined to a tree that stand or rest on something away from its trunk are somebody's build: the whole lot is left alone. */
    private static void lumberjackLeavesTreeJoinedToABuild(GameTestHelper helper) {
        buildFarm(helper);
        buildTree(helper, 5, 5);
        var level = helper.getLevel();
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        helper.assertTrue(TreeFinder.find(level, foot, foot) != null, "the tree alone is a tree");
        for (int z = 2; z <= 4; z++) helper.setBlock(new BlockPos(5, 1, z), Blocks.OAK_LOG); // a log wall run up to the trunk
        helper.setBlock(new BlockPos(5, 2, 2), Blocks.OAK_LOG);
        var found = TreeFinder.find(level, foot, foot);
        // clear it all away first: the next arena's lumberjack can see this far
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(3, 1, 1), new BlockPos(7, 5, 7))) helper.setBlock(pos, Blocks.AIR);
        helper.assertTrue(found == null, "a tree with a wall against it must be left, found " + (found == null ? 0 : found.logs().size()) + " logs to fell");
        helper.succeed();
    }

    /** Trees as the world generates them (built high above the arenas, out of any golem's reach) are still recognised as trees. */
    private static void naturalTreesAreFellable(GameTestHelper helper) {
        buildFarm(helper);
        var level = helper.getLevel();
        var features = level.registryAccess().lookupOrThrow(Registries.FEATURE);
        BlockPos at = helper.absolutePos(new BlockPos(4, 80, 4));
        StringBuilder report = new StringBuilder();
        for (var key : List.of(TreeFeatures.OAK, TreeFeatures.FANCY_OAK, TreeFeatures.BIRCH, TreeFeatures.SPRUCE, TreeFeatures.PINE,
                TreeFeatures.ACACIA, TreeFeatures.CHERRY, TreeFeatures.JUNGLE_TREE, TreeFeatures.SWAMP_OAK,
                TreeFeatures.DARK_OAK, TreeFeatures.PALE_OAK, TreeFeatures.AZALEA_TREE, TreeFeatures.MEGA_SPRUCE, TreeFeatures.MEGA_JUNGLE_TREE)) {
            var feature = features.getOrThrow(key).value();
            int grown = 0, fellable = 0;
            for (int seed = 0; seed < 60; seed++) {
                for (BlockPos soil : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(2, -1, 2))) level.setBlock(soil, Blocks.DIRT.defaultBlockState(), 2);
                if (feature.place(level, level.getChunkSource().getGenerator(), net.minecraft.util.RandomSource.create(seed), at)) {
                    grown++;
                    if (TreeFinder.find(level, at, at) != null) fellable++;
                }
                for (BlockPos pos : BlockPos.betweenClosed(at.offset(-14, -2, -14), at.offset(14, 36, 14))) {
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
            report.append(key.identifier().getPath()).append(' ').append(fellable).append('/').append(grown).append("  ");
            helper.assertTrue(grown > 0, "no " + key.identifier() + " grew");
        }
        IndustriousGolems.LOGGER.info("Natural trees recognised: {}", report);
        helper.assertTrue(report.toString().strip().equals(NATURAL_TREES), "recognised trees changed: " + report);
        helper.succeed();
    }

    private static final String NATURAL_TREES = "oak 60/60  fancy_oak 60/60  birch 60/60  spruce 60/60  pine 60/60  acacia 60/60  cherry 60/60  jungle_tree 60/60  "
            + "swamp_oak 60/60  dark_oak 60/60  pale_oak 60/60  azalea_tree 60/60  mega_spruce 13/60  mega_jungle_tree 17/60"; // (the giants: over the log limit)

    /** A golem is heavy enough to trample farmland as a player does, but never does: dropping onto a field leaves it tilled. */
    private static void golemDoesNotTrampleFarmland(GameTestHelper helper) {
        buildFarm(helper);
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
        }
        WorkGolem farmer = spawnDormantWorker(helper, ModProfessions.FARMER, COMPOSTER, new BlockPos(4, 4, 4));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(farmer.onGround(), "the golem should have landed");
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 5; z++) helper.assertTrue(helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.FARMLAND), "trampled at " + x + ", " + z);
            }
            helper.succeed();
        });
    }

    /** The animal being worked on stays where it is, even if the golem is moved or slips partway through: it is never carried off. */
    private static void herderHoldsTheAnimalWhereItWas(GameTestHelper helper) {
        buildHerdFarm(helper);
        Sheep sheep = helper.spawn(EntityTypes.SHEEP, new BlockPos(5, 1, 5));
        sheep.setColor(DyeColor.WHITE);
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        Vec3[] held = {null};
        int[] ticks = {0};
        double[] strayed = {0};
        helper.onEachTick(() -> {
            if (!"shear".equals(herder.clip())) return;
            if (++ticks[0] == 2) {
                held[0] = sheep.position();
                herder.setPos(herder.getX() - 2.0, herder.getY() + 1.5, herder.getZ()); // up and away, as if it slid off something
            }
            if (held[0] != null) strayed[0] = Math.max(strayed[0], sheep.position().distanceTo(held[0]));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!sheep.readyForShearing(), "waiting for the sheep to be sheared");
            helper.assertTrue(held[0] != null, "the shearing should have been seen");
            helper.assertTrue(strayed[0] < 0.5, "the sheep was carried " + strayed[0] + " blocks");
        });
    }

    /** Six name-tagged cows crowd the pen: the herder has nothing it may cull. */
    private static void herderSparesNamedAnimals(GameTestHelper helper) {
        buildHerdFarm(helper);
        for (int i = 0; i < 6; i++) {
            helper.spawn(EntityTypes.COW, new BlockPos(4 + i % 4, 1, 4 + i / 2)).setCustomName(net.minecraft.network.chat.Component.literal("Daisy " + i));
        }
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        helper.runAtTickTime(600, () -> {
            helper.assertTrue(alive(helper, Cow.class).size() == 6, "named cows were culled, " + alive(helper, Cow.class).size() + " left");
            helper.assertTrue(HerdWork.jobs(helper.getLevel(), herder).noneMatch(job -> job.plan() instanceof com.scottcaldwell.industriousgolems.herd.HerdPlan p
                    && p.action() == com.scottcaldwell.industriousgolems.herd.HerdPlan.Action.CULL), "no cull is even planned");
            helper.succeed();
        });
    }

    /** Pitcher pods grow a crop of their own kind; the farmer sows them like any seed. */
    private static void farmerSowsPitcherPods(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(new BlockPos(3, 1, 5), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
        WorkGolem farmer = spawnFarmer(helper, AT);
        farmer.setCarried(new ItemStack(Items.PITCHER_POD));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 5)).is(Blocks.PITCHER_CROP), "the pod is sown");
            helper.assertTrue(!farmer.isCarrying(), "and used up");
        });
    }

    /** The whole room is dark: nothing sown here would grow, so the seeds go back to the chest without a try. */
    private static void farmerReturnsUnsowableSeeds(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(new BlockPos(3, 1, 5), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
        for (int x = -1; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
        }
        Container chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        WorkGolem farmer = spawnFarmer(helper, AT);
        boolean[] tried = {false};
        helper.onEachTick(() -> tried[0] |= Anim.H_REPLANT.equals(farmer.clip()));
        helper.runAtTickTime(20, () -> farmer.setCarried(new ItemStack(Items.WHEAT_SEEDS, 4))); // (once the light has caught up)
        helper.succeedWhen(() -> {
            helper.assertTrue(count(chest, Items.WHEAT_SEEDS) == 4, "the seeds are put away" + describe(helper, farmer, chest));
            helper.assertTrue(!tried[0], "it tried to sow in the dark");
        });
    }

    /** A furnace and a hopper by the job site are not storage: nothing is put in them or taken out. */
    private static void golemIgnoresFurnaces(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(CHEST, Blocks.AIR);
        BlockPos furnace = CHEST, hopper = new BlockPos(2, 1, 4);
        helper.setBlock(furnace, Blocks.FURNACE);
        helper.setBlock(hopper, Blocks.HOPPER);
        Container a = helper.getBlockEntity(furnace, net.minecraft.world.level.block.entity.FurnaceBlockEntity.class);
        Container b = helper.getBlockEntity(hopper, net.minecraft.world.level.block.entity.HopperBlockEntity.class);
        WorkGolem farmer = spawnFarmer(helper, AT);
        farmer.setCarried(new ItemStack(Items.WHEAT, 5));
        helper.runAtTickTime(300, () -> {
            helper.assertTrue(count(a, Items.WHEAT) == 0 && count(b, Items.WHEAT) == 0, "wheat went into a furnace or hopper");
            helper.assertTrue(farmer.carried().getCount() == 5, "it keeps hold of the wheat");
            helper.succeed();
        });
    }

    /** A cow stands where a support post goes: no fence is put inside it. */
    private static void minerDoesNotBuildIntoAnimals(GameTestHelper helper) {
        buildMine(helper);
        ChestBlockEntity chest = helper.getBlockEntity(HUB, ChestBlockEntity.class);
        stockKit(chest, 8);
        BlockPos post = new BlockPos(9, 1, 5);
        var cow = helper.spawn(EntityTypes.COW, post);
        cow.setNoAi(true);
        WorkGolem miner = spawnWorker(helper, ModProfessions.MINER, HUB, HUB.east());
        boolean[] built = {false};
        helper.onEachTick(() -> built[0] |= helper.getBlockState(post).is(net.minecraft.tags.BlockTags.FENCES));
        helper.runAtTickTime(900, () -> {
            helper.assertTrue(!built[0], "a post was put inside the cow" + describe(helper, miner, chest));
            helper.succeed();
        });
    }

    /** Sheep crowd the gate of the pen: the herder shoos them, goes in, shuts it and only then shears; none gets out. */
    private static void herderKeepsAnimalsInAtGate(GameTestHelper helper) {
        buildHerdFarm(helper);
        BlockPos gate = new BlockPos(3, 1, 5);
        helper.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST));
        List<Sheep> flock = new java.util.ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 1, 5), new BlockPos(4, 1, 4), new BlockPos(4, 1, 6))) {
            Sheep sheep = helper.spawn(EntityTypes.SHEEP, at);
            sheep.setColor(DyeColor.WHITE);
            flock.add(sheep);
        }
        WorkGolem herder = spawnHerder(helper, new BlockPos(1, 1, 5));
        double edge = helper.absolutePos(gate).getX() + 0.5;
        boolean[] shooed = {false}, opened = {false};
        String[] wrong = {""};
        helper.onEachTick(() -> {
            shooed[0] |= "shoo".equals(herder.clip());
            boolean open = helper.getBlockState(gate).getValue(FenceGateBlock.OPEN);
            opened[0] |= open;
            for (Sheep sheep : flock) if (sheep.getX() < edge) wrong[0] = "a sheep got out";
            if (open && "shear".equals(herder.clip())) wrong[0] = "shearing with the gate open";
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(wrong[0].isEmpty(), wrong[0]);
            helper.assertTrue(flock.stream().noneMatch(Sheep::readyForShearing), "waiting for all three to be shorn" + describe(helper, herder, helper.getBlockEntity(CHEST, ChestBlockEntity.class)));
            helper.assertTrue(shooed[0] && opened[0], "it shooed them and went by the gate: shooed " + shooed[0] + ", opened " + opened[0]);
            helper.assertTrue(!helper.getBlockState(gate).getValue(FenceGateBlock.OPEN), "the gate is shut");
        });
    }

    /** Killed in the gateway, it still shuts the gate. */
    private static void golemShutsGateWhenKilled(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(CHEST, Blocks.AIR);
        for (int z = 0; z <= 7; z++) helper.setBlock(new BlockPos(4, 1, z), Blocks.OAK_FENCE);
        BlockPos gate = new BlockPos(4, 1, 5);
        helper.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST));
        BlockPos site = new BlockPos(6, 1, 2);
        helper.setBlock(site, ModBlocks.HAY_CHEST.get());
        WorkGolem golem = spawnWorker(helper, ModProfessions.FARMER, site, new BlockPos(2, 1, 5));
        golem.setCarried(new ItemStack(Items.WHEAT, 5));
        boolean[] killed = {false};
        helper.succeedWhen(() -> {
            if (!killed[0]) {
                helper.assertTrue(helper.getBlockState(gate).getValue(FenceGateBlock.OPEN), "waiting for it to open the gate");
                golem.discard();
                killed[0] = true;
            }
            helper.assertTrue(!helper.getBlockState(gate).getValue(FenceGateBlock.OPEN), "the gate is shut behind it");
        });
    }

    private static void golemUsesItsOwnChest(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(COMPOSTER, ModBlocks.HAY_CHEST.get());
        helper.setBlock(COPPER_CHEST, Blocks.COPPER_CHEST.weathering().unaffected());
        helper.setBlock(new BlockPos(2, 1, 4), ModBlocks.MUSHROOM_CHEST.get());
        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.setCarried(new ItemStack(Items.WHEAT_SEEDS, 5));
        Container own = helper.getBlockEntity(COMPOSTER, ChestBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(count(own, Items.WHEAT_SEEDS) == 5, "seeds go into the golem's own chest, got " + count(own, Items.WHEAT_SEEDS));
            for (BlockPos other : List.of(CHEST, COPPER_CHEST, new BlockPos(2, 1, 4))) {
                helper.assertTrue(count(helper.getBlockEntity(other, ChestBlockEntity.class), Items.WHEAT_SEEDS) == 0, "no other chest gets any");
            }
        });
    }

    /** What the golem only produces (wheat) goes to a copper chest beside it for the copper golems to carry on, even with its own chest there; with no copper chest it keeps it. */
    private static void golemSendsProduceToCopperChest(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(COMPOSTER, ModBlocks.HAY_CHEST.get());
        helper.setBlock(COPPER_CHEST, Blocks.COPPER_CHEST.weathering().unaffected());
        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.setCarried(new ItemStack(Items.WHEAT, 5));
        Container own = helper.getBlockEntity(COMPOSTER, ChestBlockEntity.class);
        Container copper = helper.getBlockEntity(COPPER_CHEST, ChestBlockEntity.class);
        helper.succeedWhen(() -> {
            helper.assertTrue(count(copper, Items.WHEAT) == 5, "wheat goes to the copper chest, got " + count(copper, Items.WHEAT));
            helper.assertTrue(count(own, Items.WHEAT) == 0, "none in the golem's own chest");
        });
    }

    /** A chest up on a ledge with steps to it: a golem standing underneath climbs up and does not use it from below. */
    private static void golemClimbsToRaisedChest(GameTestHelper helper) {
        buildFarm(helper); // for its floor and walls
        for (int x = 3; x <= 5; x++) helper.setBlock(new BlockPos(x, 3, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(6, 2, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(7, 1, 3), Blocks.STONE);
        BlockPos site = new BlockPos(3, 4, 3);
        helper.setBlock(site, ModBlocks.MUSHROOM_CHEST.get().defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
        WorkGolem golem = spawnWorker(helper, ModProfessions.LUMBERJACK, site, new BlockPos(4, 1, 3)); // right under the spot in front
        golem.setCarried(new ItemStack(Items.OAK_LOG, 5));
        Container own = helper.getBlockEntity(site, ChestBlockEntity.class);
        double[] worst = {0}; // how far under the chest the golem ever was while holding it open (a failure inside succeedWhen is only retried)
        helper.succeedWhen(() -> {
            if (!helper.getBlockEntity(site, ChestBlockEntity.class).getEntitiesWithContainerOpen().isEmpty()) {
                worst[0] = Math.max(worst[0], helper.absolutePos(site).getY() - golem.getY());
            }
            helper.assertTrue(count(own, Items.OAK_LOG) == 5, "the logs go in the chest" + describe(helper, golem, own));
            helper.assertTrue(worst[0] < 0.75, "the chest was opened from " + worst[0] + " blocks below it");
        });
    }

    /** A mushroom golem with leaves at head height on every side cannot take a step: it sneezes out and carries on to its chest. */
    private static void golemSneezesOutFromUnderLeaves(GameTestHelper helper) {
        buildFarm(helper);
        BlockPos site = new BlockPos(6, 1, 6);
        helper.setBlock(site, ModBlocks.MUSHROOM_CHEST.get());
        var leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (Direction side : Direction.Plane.HORIZONTAL) helper.setBlock(new BlockPos(2, 2, 2).relative(side), leaf);
        helper.setBlock(COMPOSTER, Blocks.AIR);
        WorkGolem golem = spawnWorker(helper, ModProfessions.LUMBERJACK, site, new BlockPos(2, 1, 2));
        // handed its logs once it has landed: a golem still in the air takes any spot for reachable and sets off
        helper.runAtTickTime(20, () -> golem.setCarried(new ItemStack(Items.OAK_LOG, 5)));
        Container own = helper.getBlockEntity(site, ChestBlockEntity.class);
        helper.succeedWhen(() -> helper.assertTrue(count(own, Items.OAK_LOG) == 5, "it gets out and puts the logs away" + describe(helper, golem, own)));
    }

    /** The chest is outside the pen and the golem inside: it goes through the shut gate and leaves it shut. */
    private static void golemUsesGate(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(CHEST, Blocks.AIR);
        for (int z = 0; z <= 7; z++) helper.setBlock(new BlockPos(4, 1, z), Blocks.OAK_FENCE);
        BlockPos gate = new BlockPos(4, 1, 5);
        helper.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST));
        BlockPos site = new BlockPos(6, 1, 2);
        helper.setBlock(site, ModBlocks.HAY_CHEST.get().defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        WorkGolem golem = spawnWorker(helper, ModProfessions.FARMER, site, new BlockPos(2, 1, 2));
        golem.setCarried(new ItemStack(Items.WHEAT, 5));
        Container own = helper.getBlockEntity(site, ChestBlockEntity.class);
        boolean[] opened = {false};
        helper.succeedWhen(() -> {
            opened[0] |= helper.getBlockState(gate).getValue(FenceGateBlock.OPEN);
            helper.assertTrue(count(own, Items.WHEAT) == 5, "the wheat reaches the chest outside" + describe(helper, golem, own));
            helper.assertTrue(opened[0], "it went by the gate");
            helper.assertTrue(!helper.getBlockState(gate).getValue(FenceGateBlock.OPEN), "and shut it behind itself");
        });
    }

    /** Two golems with no chest of their own both take up the one chest there is, and both put their stacks in it. */
    private static void golemsShareOneChest(GameTestHelper helper) {
        buildFarm(helper);
        BlockPos site = new BlockPos(3, 1, 3);
        helper.setBlock(COMPOSTER, Blocks.AIR);
        helper.setBlock(CHEST, Blocks.AIR);
        helper.setBlock(site, ModBlocks.HAY_CHEST.get().defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
        List<WorkGolem> golems = new java.util.ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(2, 1, 5), new BlockPos(5, 1, 5))) {
            WorkGolem golem = helper.spawn(ModEntities.FARMER_GOLEM.get(), at);
            Offerings.fill(golem);
            golem.setCarried(new ItemStack(Items.WHEAT, 5));
            golems.add(golem);
        }
        Container own = helper.getBlockEntity(site, ChestBlockEntity.class);
        helper.succeedWhen(() -> {
            for (WorkGolem golem : golems) {
                helper.assertTrue(helper.absolutePos(site).equals(golem.jobSite(helper.getLevel())), "both work from the one chest");
            }
            helper.assertTrue(count(own, Items.WHEAT) == 10, "both stacks go in, got " + count(own, Items.WHEAT));
        });
    }

    /** A swing takes one log only, and the logs level with the golem all go before it breaks the one under its feet. */
    private static void lumberjackBreaksOneLogAtATime(GameTestHelper helper) {
        buildFarm(helper);
        // a two by two trunk
        List<BlockPos> logs = new java.util.ArrayList<>();
        var leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int x = 5; x <= 6; x++) {
            for (int z = 5; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
                for (int y = 1; y <= 3; y++) { logs.add(new BlockPos(x, y, z)); helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG); }
                helper.setBlock(new BlockPos(x, 4, z), leaf);
                for (int y = 5; y <= 7; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR); // the arena's low ceiling
            }
        }
        helper.setBlock(COMPOSTER, Blocks.FLETCHING_TABLE);
        WorkGolem golem = spawnWorker(helper, ModProfessions.LUMBERJACK, COMPOSTER, new BlockPos(3, 1, 3));
        int[] left = {logs.size()};
        String[] wrong = {""};
        helper.onEachTick(() -> {
            List<BlockPos> standing = logs.stream().filter(p -> helper.getBlockState(p).is(Blocks.OAK_LOG)).toList();
            if (left[0] - standing.size() > 1) wrong[0] = (left[0] - standing.size()) + " logs broke at once";
            left[0] = standing.size();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(left[0] == 0, "the whole trunk comes down, " + left[0] + " left" + describe(helper, golem, helper.getBlockEntity(CHEST, ChestBlockEntity.class)));
            helper.assertTrue(wrong[0].isEmpty(), wrong[0]);
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
            helper.assertTrue(saplingInArena(helper), "a sapling is planted");
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
        // the east wall is built up out of the way: a log that pops off the east branch must land on the floor, not rest on top of the wall where nothing can reach it
        for (int z = 0; z <= 7; z++) for (int y = 4; y <= 8; y++) helper.setBlock(new BlockPos(8, y, z), Blocks.STONE);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        helper.succeedWhen(() -> {
            int left = 0;
            StringBuilder where = new StringBuilder();
            for (int x = 4; x <= 7; x++) for (int y = 1; y <= 5; y++) if (helper.getBlockState(new BlockPos(x, y, 5)).is(Blocks.OAK_LOG)) { left++; where.append(x).append(',').append(y).append(' '); }
            helper.assertTrue(left == 0, "every log is felled, " + left + " left: " + where);
            // (this test has an environment of its own: arenas in one batch run side by side, and golems gather each other's logs)
            helper.assertTrue(count(chest, Items.OAK_LOG) == 11, "all eleven logs reach the chest, got " + count(chest, Items.OAK_LOG) + describe(helper, jack, chest));
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

    /** A chest whose open count gets out of step (a close with no open, an open nobody holds) is put right again, so the lid cannot stay inverted. */
    private static void golemChestRecountsOpeners(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(CHEST, ModBlocks.MUSHROOM_CHEST.get());
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        BlockPos pos = helper.absolutePos(CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        helper.runAtTickTime(5, () -> chest.stopOpen(jack)); // a close that was never opened: the count goes to -1
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(ChestBlockEntity.getOpenCount(helper.getLevel(), pos) == 0, "the count is back to zero after a stray close, got " + ChestBlockEntity.getOpenCount(helper.getLevel(), pos));
            chest.startOpen(jack); // an open nobody holds (the golem has no chest open): a leak
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(ChestBlockEntity.getOpenCount(helper.getLevel(), pos) == 0, "and after a leaked open, got " + ChestBlockEntity.getOpenCount(helper.getLevel(), pos));
            helper.succeed();
        });
    }

    /** With a stack in hand the lumberjack collects more of the same item (up to a full stack) before it goes to the chest. */
    private static void lumberjackTopsUpStack(GameTestHelper helper) {
        buildFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        jack.setCarried(new ItemStack(Items.OAK_LOG, 3));
        helper.spawnItem(Items.OAK_LOG, new Vec3(5.5, 1.5, 6.5)).setItem(new ItemStack(Items.OAK_LOG, 5));
        helper.spawnItem(Items.OAK_LOG, new Vec3(3.5, 1.5, 1.5)).setItem(new ItemStack(Items.OAK_LOG, 5));
        int[] first = {-1};
        helper.succeedWhen(() -> {
            int inChest = count(chest, Items.OAK_LOG);
            if (first[0] < 0 && inChest > 0) first[0] = inChest;
            helper.assertTrue(inChest == 13 && first[0] == 13, "all thirteen logs go to the chest in one trip, first deposit was " + first[0] + describe(helper, jack, chest));
        });
    }

    /** Mushroom golems land softly; the others do not. */
    private static void mushroomTakesNoFallDamage(GameTestHelper helper) {
        buildFarm(helper);
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        helper.runAtTickTime(5, () -> {
            jack.causeFallDamage(12, 1.0F, jack.damageSources().fall());
            farmer.causeFallDamage(12, 1.0F, farmer.damageSources().fall());
            helper.assertTrue(jack.getHealth() == jack.getMaxHealth(), "the mushroom golem is unhurt, health " + jack.getHealth());
            helper.assertTrue(farmer.getHealth() < farmer.getMaxHealth(), "other golems still take fall damage");
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
            helper.assertTrue(Anim.M_SIT_FULL.equals(jack.clip()), "it sits down, clip is '" + jack.clip() + "'");
            helper.assertTrue(jack.carried().is(Items.OAK_LOG) && jack.carried().getCount() == 3, "and keeps the stack");
        });
    }

    // ---- lumberjack: coverage of the review fixes ---------------------------------------------------------------

    /** The held item is drawn (in front of the body, never lost inside the cap) through every clip in which the golem carries it. */
    private static void lumberjackKeepsItemInView(GameTestHelper helper) {
        var kit = com.scottcaldwell.industriousgolems.client.anim.Animator.MUSHROOM;
        var pose = new com.scottcaldwell.industriousgolems.client.anim.Pose();
        var spec = Anim.chest("lumberjack");
        for (String item : List.of("log", "sapling")) {
            for (double t = 0; t < spec.putRelease() - 0.05; t += 0.05) {
                String hold = com.scottcaldwell.industriousgolems.client.anim.Animator.evaluate(kit, pose, Anim.M_PUT_IN, t, false, 0, t, false, false, item,
                        new com.scottcaldwell.industriousgolems.client.anim.AnimState());
                helper.assertTrue("front".equals(hold), item + " put-in at " + t + "s is held '" + hold + "'");
            }
            for (double t = spec.fetchTake() + 0.02; t < spec.fetchThrow() - 0.02; t += 0.05) {
                String hold = com.scottcaldwell.industriousgolems.client.anim.Animator.evaluate(kit, pose, Anim.M_FETCH, t, false, 0, t, false, false, item,
                        new com.scottcaldwell.industriousgolems.client.anim.AnimState());
                helper.assertTrue("front".equals(hold), item + " fetch at " + t + "s is held '" + hold + "'");
            }
            for (boolean moving : new boolean[]{true, false}) {
                String hold = com.scottcaldwell.industriousgolems.client.anim.Animator.evaluate(kit, pose, null, 1.3, moving, 6.0, 1.3, false, false, item,
                        new com.scottcaldwell.industriousgolems.client.anim.AnimState());
                helper.assertTrue("front".equals(hold), item + (moving ? " walking" : " standing") + " is held '" + hold + "'");
            }
        }
        // and it is drawn at the belly, not underground: the body pivot is 2 px above the ground (y is down), the item layer adds its own step
        float[] at = com.scottcaldwell.industriousgolems.client.anim.HeldItem.landing(com.scottcaldwell.industriousgolems.client.anim.HeldItem.MUSHROOM_FRONT);
        helper.assertTrue(at[1] <= 0F && at[1] >= -6F, "the item sits at body height, got " + at[1] + " px from the pivot");
        helper.assertTrue(at[2] < -3F && at[2] > -10F, "and just in front of the body, got " + at[2]);
        helper.succeed();
    }

    /** The climb ends on the front edge of the lid (z -8..-9 pixels toward the chest), never out over its middle (-13). */
    private static void lumberjackClamberStaysOnTheEdge(GameTestHelper helper) {
        var kit = com.scottcaldwell.industriousgolems.client.anim.Animator.MUSHROOM;
        var pose = new com.scottcaldwell.industriousgolems.client.anim.Pose();
        float deepest = 0;
        for (double t = 0; t < Anim.chest("lumberjack").fetchLen(); t += 0.05) {
            com.scottcaldwell.industriousgolems.client.anim.Animator.evaluate(kit, pose, Anim.M_FETCH, t, false, 0, t, false, false, null,
                    new com.scottcaldwell.industriousgolems.client.anim.AnimState());
            deepest = Math.min(deepest, pose.offZ);
        }
        helper.assertTrue(deepest <= -8.0F, "it does climb up onto the chest, got " + deepest);
        helper.assertTrue(deepest >= -9.0F, "but stays on the front edge, got " + deepest);
        helper.succeed();
    }

    private static final int SKY = 40;

    /** A big flat grass platform high above the arena (the real grove is far larger than the 8x8 arena), with the fletching table and chest on it. */
    private static void skyGrove(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) helper.getLevel().setChunkForced((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz, true);
        }
        // These platforms are far bigger than the arenas, so an earlier sky test's grove overlaps this one: clear away what it left
        // (tests using this must each have their own environment, so that none is running while another clears)
        for (int x = -45; x <= 55; x++) for (int z = -45; z <= 55; z++) for (int y = SKY + 1; y <= SKY + 10; y++) {
            BlockPos p = new BlockPos(x, y, z);
            if (!helper.getBlockState(p).isAir()) helper.setBlock(p, Blocks.AIR);
        }
        AABB above = new AABB(Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(-45, SKY, -45))), Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(55, SKY + 12, 55))));
        helper.getLevel().getEntities((net.minecraft.world.entity.Entity) null, above, e -> !(e instanceof net.minecraft.world.entity.player.Player)).forEach(net.minecraft.world.entity.Entity::discard);
        for (int x = -20; x <= 30; x++) for (int z = -20; z <= 30; z++) helper.setBlock(new BlockPos(x, SKY, z), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(2, SKY + 1, 2), Blocks.FLETCHING_TABLE);
        helper.setBlock(new BlockPos(4, SKY + 1, 2), Blocks.CHEST);
    }

    /** At its real size the grove keeps clear of the chest and spreads out: no two saplings near each other, none by the chest, and a cap. */
    private static void lumberjackGroveSpacing(GameTestHelper helper) {
        skyGrove(helper);
        WorkGolem jack = spawnWorker(helper, ModProfessions.LUMBERJACK, new BlockPos(2, SKY + 1, 2), new BlockPos(3, SKY + 1, 4));
        helper.runAtTickTime(10, () -> {
            var layout = com.scottcaldwell.industriousgolems.lumber.Planting.Layout.GROVE;
            BlockPos site = helper.absolutePos(new BlockPos(2, SKY + 1, 2));
            List<BlockPos> planted = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                BlockPos at = com.scottcaldwell.industriousgolems.lumber.Planting.find(helper.getLevel(), jack, site, p -> false, layout);
                if (at == null) break;
                helper.getLevel().setBlockAndUpdate(at, Blocks.OAK_SAPLING.defaultBlockState());
                planted.add(at);
            }
            helper.assertTrue(planted.size() >= 15 && planted.size() <= layout.cap(), "it keeps planting until the spacing leaves no good ground, got " + planted.size());
            for (BlockPos a : planted) {
                helper.assertTrue(!layout.clears(a, site), "clear of the chest: " + a.subtract(site));
                helper.assertTrue(Math.abs(a.getX() - site.getX()) <= layout.radius() && Math.abs(a.getZ() - site.getZ()) <= layout.radius(), "within reach: " + a.subtract(site));
                for (BlockPos b : planted) {
                    if (a != b) helper.assertTrue(Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ())) > layout.spacing(), "spaced apart: " + a + " " + b);
                }
            }
            helper.succeed();
        });
    }

    /** A stump inside the clear zone round the chest is not replanted (the tree would grow over the way to it). */
    private static void lumberjackClearsAroundChest(GameTestHelper helper) {
        buildFarm(helper);
        buildTree(helper, 3, 3); // one block diagonal from the job site at (2, 2): inside the clearing
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.OAK_SAPLING, 3));
        spawnLumberjack(helper, new BlockPos(2, 1, 5));
        helper.runAtTickTime(2600, () -> {
            helper.assertTrue(logsAt(helper, 3, 3) == 0, "the tree is felled");
            helper.assertTrue(!helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.OAK_SAPLING), "but nothing is planted on the stump by the chest");
            helper.succeed();
        });
    }

    private static boolean saplingInArena(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.OAK_SAPLING)) return true;
        return false;
    }

    private static WorkGolem reload(GameTestHelper helper, WorkGolem golem) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        golem.saveWithoutId(out);
        WorkGolem copy = helper.spawn(ModEntities.typeOf(golem.profession()), new BlockPos(5, 1, 5));
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), out.buildResult()));
        return copy;
    }

    /** A tree at the edge of the 16-block search, 14 blocks from the chest: every log it drops is still gathered. */
    private static void lumberjackFellsFarTree(GameTestHelper helper) {
        skyGrove(helper);
        buildTree(helper, 16, 4, SKY);
        ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(4, SKY + 1, 2), ChestBlockEntity.class);
        spawnWorker(helper, ModProfessions.LUMBERJACK, new BlockPos(2, SKY + 1, 2), new BlockPos(3, SKY + 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(16, SKY + 1, 4)).isAir() || helper.getBlockState(new BlockPos(16, SKY + 1, 4)).is(Blocks.OAK_SAPLING), "the tree is felled");
            helper.assertTrue(count(chest, Items.OAK_LOG) == 4, "all four logs reach the chest, got " + count(chest, Items.OAK_LOG));
        });
    }

    /** A stack lying 14 blocks out (where a far tree's drops land) is fetched: the pick-up reaches as far as the tree search does. */
    private static void lumberjackFetchesFarDrop(GameTestHelper helper) {
        skyGrove(helper);
        ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(4, SKY + 1, 2), ChestBlockEntity.class);
        spawnWorker(helper, ModProfessions.LUMBERJACK, new BlockPos(2, SKY + 1, 2), new BlockPos(3, SKY + 1, 4));
        helper.spawnItem(Items.OAK_LOG, new Vec3(16.5, SKY + 1.5, 4.5));
        helper.succeedWhen(() -> helper.assertTrue(count(chest, Items.OAK_LOG) == 1, "the far log reaches the chest, got " + count(chest, Items.OAK_LOG)));
    }

    /** Sat down with a stack and a full chest, it gets up and puts the stack away as soon as there is room. */
    private static void lumberjackStandsUp(GameTestHelper helper) {
        buildFarm(helper);
        Container chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        WorkGolem jack = spawnLumberjack(helper, AT);
        jack.setCarried(new ItemStack(Items.OAK_LOG, 3));
        boolean[] sat = {false};
        helper.succeedWhen(() -> {
            if (!sat[0]) {
                if (Anim.M_SIT_FULL.equals(jack.clip())) { sat[0] = true; chest.setItem(0, ItemStack.EMPTY); }
                helper.assertTrue(false, "waiting for it to sit down");
            }
            helper.assertTrue(!Anim.M_SIT_FULL.equals(jack.clip()), "it gets up");
            helper.assertTrue(!jack.isCarrying() && count(chest, Items.OAK_LOG) == 3, "and puts the logs away, got " + count(chest, Items.OAK_LOG));
        });
    }

    /** The chest breaks while the stack is in the air: the golem is left holding it, nothing lost or spilled. */
    private static void lumberjackChestBrokenMidPut(GameTestHelper helper) {
        buildFarm(helper);
        WorkGolem jack = spawnLumberjack(helper, AT);
        jack.setCarried(new ItemStack(Items.OAK_LOG, 3));
        boolean[] broken = {false};
        helper.succeedWhen(() -> {
            if (!broken[0]) {
                if (jack.carried().isEmpty()) { broken[0] = true; helper.setBlock(CHEST, Blocks.AIR); }
                helper.assertTrue(false, "waiting for the hand to let go");
            }
            helper.assertTrue(jack.carried().is(Items.OAK_LOG) && jack.carried().getCount() == 3, "the stack is back in hand, got " + jack.carried());
            helper.assertTrue(itemsOnGround(helper, Items.OAK_LOG) == 0, "and nothing is spilled");
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
            // (standing on top of the chest or composter is fine: it may have walked over one to the tree)
            helper.assertTrue(level.noCollision(jack, jack.getBoundingBox().deflate(0.05)), "not inside a block, at " + jack.position());
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
        helper.assertTrue(suppliesWanted(helper, herder).contains("bucket"), "a cow to milk and no bucket in hand: the herder wants one");
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
        helper.setBlock(base.above(), Blocks.BIRCH_FENCE);
        BlockState pumpkin = Blocks.JACK_O_LANTERN.defaultBlockState();
        helper.setBlock(base.above(2), pumpkin);
        var player = helper.makeMockServerPlayerInLevel();
        BlockSnapshot snapshot = BlockSnapshot.create(helper.getLevel().dimension(), helper.getLevel(), helper.absolutePos(base.above(2)));
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, Blocks.HAY_BLOCK.defaultBlockState(), player));
        helper.assertTrue(helper.getBlockState(base).is(ModBlocks.HAY_CHEST.get()), "the hay bale becomes the hay chest");
        helper.assertTrue(helper.getBlockState(base.above(2)).isAir() && helper.getBlockState(base.above()).isAir(), "the pumpkin and fence are used up");
        helper.assertEntityPresent(ModEntities.FARMER_GOLEM.get(), base.above());
        helper.succeed();
    }

    /** A golem needs room to stand: with a block over the pumpkin, nothing is built. */
    private static void chestPatternNeedsHeadroom(GameTestHelper helper) {
        BlockPos base = AT;
        helper.setBlock(base, Blocks.HAY_BLOCK);
        helper.setBlock(base.above(), Blocks.OAK_FENCE);
        BlockState pumpkin = Blocks.CARVED_PUMPKIN.defaultBlockState();
        helper.setBlock(base.above(2), pumpkin);
        helper.setBlock(base.above(3), Blocks.STONE);
        helper.assertTrue(ChestSpawn.build(helper.getLevel(), helper.absolutePos(base.above(2)), pumpkin) == null, "no room, no golem");
        helper.assertTrue(helper.getBlockState(base).is(Blocks.HAY_BLOCK) && helper.getBlockState(base.above(2)).is(Blocks.CARVED_PUMPKIN),
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
        // a pattern builds exactly one golem
        BlockPos top = new BlockPos(3, 3, 3);
        for (ModProfessions.Profession profession : ModProfessions.all()) {
            for (BlockPos at : BlockPos.betweenClosed(top.offset(-1, -2, -1), top.offset(1, -1, 1))) helper.setBlock(at, Blocks.AIR);
            layPattern(helper, profession, top);
            long matches = ModProfessions.all().stream().filter(p -> p.pattern().match(helper.getLevel(), helper.absolutePos(top)) != null).count();
            helper.assertTrue(matches == 1, profession.id() + "'s pattern should build exactly one golem, builds " + matches);
        }
        for (BlockPos at : BlockPos.betweenClosed(top.offset(-1, -2, -1), top.offset(1, -1, 1))) helper.setBlock(at, Blocks.AIR);
        helper.setBlock(top.below(), Blocks.STONE);
        helper.assertTrue(ModProfessions.all().stream().noneMatch(p -> p.pattern().match(helper.getLevel(), helper.absolutePos(top)) != null), "stone builds nothing");
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

        helper.succeed();
    }

    // ---- the mine ---------------------------------------------------------------------------------------------------

    /** The hub: the stone golem's chest, on a floor of stone. Arms run from it; the arena is 14 across. */
    private static final BlockPos HUB = new BlockPos(6, 1, 6);

    /**
     * A stone floor with open air above it and the golem's chest at the hub. The north, south and west arms are walled off at their first
     * slice so that only the east arm is in play, and the ground beyond the east end of the arena is cleared so the arm really ends there.
     */
    private static void buildMine(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) helper.getLevel().setChunkForced((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz, true);
        }
        for (int x = 0; x <= 24; x++) {
            for (int z = 0; z <= 13; z++) {
                helper.setBlock(new BlockPos(x, 0, z), x <= 13 ? Blocks.STONE : Blocks.AIR);
                for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }
        helper.setBlock(HUB, ModBlocks.STONE_CHEST.get());
        for (net.minecraft.core.Direction dir : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST}) {
            helper.setBlock(HUB.relative(dir, Trunk.HUB + 1).above(1), Blocks.OBSIDIAN);
        }
    }

    private static void stockKit(Container chest, int torches) {
        chest.setItem(0, new ItemStack(Items.OAK_FENCE, 16));
        chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 12));
        chest.setItem(2, new ItemStack(Items.COBBLESTONE, 16));
        if (torches > 0) chest.setItem(3, new ItemStack(Items.TORCH, torches));
    }

    private static Trunk.Step nextStep(GameTestHelper helper) {
        return Trunk.next(helper.getLevel(), helper.absolutePos(HUB), helper.absolutePos(HUB.above(2)), pos -> false);
    }

    private static boolean isTorch(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).is(Blocks.TORCH);
    }

    /** What the mine asks for: the brace on the arm's first slice (posts, beam, a torch hung on the middle plank facing the chest), and where it ends. */
    private static void minerTrunkPlan(GameTestHelper helper) {
        buildMine(helper);
        Trunk.Step step = nextStep(helper);
        helper.assertTrue(step != null && !step.digs() && step.slice() == 1 && step.builds().size() == 8, "the first slice of the east arm is a brace: four posts, three planks and a torch, got " + step);
        Trunk.Placement torch = step.builds().get(7);
        helper.assertTrue(torch.part() == Kit.TORCH && torch.facing() == net.minecraft.core.Direction.WEST && torch.pos().equals(helper.absolutePos(new BlockPos(8, 3, 6))),
                "the torch hangs on the middle plank, facing the chest, got " + torch);
        for (int z : new int[] {5, 7}) for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(9, y, z), Blocks.OAK_FENCE);
        for (int z = 5; z <= 7; z++) helper.setBlock(new BlockPos(9, 3, z), Blocks.OAK_PLANKS);
        step = nextStep(helper);
        helper.assertTrue(step != null && step.builds().size() == 1 && step.builds().get(0).part() == Kit.TORCH, "the torch comes last, got " + step);
        helper.setBlock(new BlockPos(8, 3, 6), Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, net.minecraft.core.Direction.WEST));
        helper.assertTrue(nextStep(helper) == null, "the arena ends after the fifth slice: no floor beyond it, so the arm ends and the mine is finished");
        helper.assertTrue(Trunk.isBrace(1) && Trunk.isBrace(6) && Trunk.isBrace(11) && !Trunk.isBrace(5), "a brace every fifth slice from the first");

        // a short gap in the floor is bridged with cobblestone, a long one ends the arm
        helper.setBlock(new BlockPos(11, 0, 6), Blocks.AIR);
        step = nextStep(helper);
        helper.assertTrue(step != null && step.builds().size() == 1 && step.builds().get(0).part() == Kit.COBBLE, "one missing floor block is bridged, got " + step);
        for (int x = 11; x <= 13; x++) helper.setBlock(new BlockPos(x, 0, 6), Blocks.AIR);
        helper.assertTrue(nextStep(helper) == null, "no floor for more than five slices ends the arm");
        for (int x = 11; x <= 13; x++) helper.setBlock(new BlockPos(x, 0, 6), Blocks.STONE);

        // liquid is plugged with cobblestone; where it is in the corridor itself the plug ends the arm
        helper.setBlock(new BlockPos(9, 2, 6), Blocks.LAVA);
        step = nextStep(helper);
        helper.assertTrue(step != null && !step.digs() && step.builds().size() == 1 && step.builds().get(0).pos().equals(helper.absolutePos(new BlockPos(9, 2, 6)))
                && step.builds().get(0).part() == Kit.COBBLE, "lava in the corridor is plugged, got " + step);
        helper.setBlock(new BlockPos(9, 2, 6), Blocks.COBBLESTONE);
        helper.assertTrue(nextStep(helper) == null, "and the arm ends at the plug");
        helper.setBlock(new BlockPos(9, 2, 6), Blocks.AIR);
        helper.setBlock(new BlockPos(9, 2, 4), Blocks.WATER);
        step = nextStep(helper);
        helper.assertTrue(step != null && step.builds().size() == 1 && step.builds().get(0).pos().equals(helper.absolutePos(new BlockPos(9, 2, 4))),
                "water in the wall beside the corridor is plugged before it can flow in, got " + step);
        helper.setBlock(new BlockPos(9, 2, 4), Blocks.AIR);

        // rock in the way is dug, and only where it is safe to
        helper.setBlock(new BlockPos(9, 2, 6), Blocks.STONE);
        step = nextStep(helper);
        helper.assertTrue(step != null && step.digs() && step.dig().equals(helper.absolutePos(new BlockPos(9, 2, 6))), "rock in a slice is dug, got " + step);
        helper.setBlock(new BlockPos(9, 2, 6), Blocks.GRAVEL);
        step = nextStep(helper);
        helper.assertTrue(step != null && step.digs() && step.dig().equals(helper.absolutePos(new BlockPos(9, 2, 6))), "gravel in the corridor is dug, got " + step);
        // a ceiling block with gravel over it: dug in the corridor (the column drops a block and is dug again), but not as a loose vein block
        helper.setBlock(new BlockPos(9, 2, 6), Blocks.AIR);
        helper.setBlock(new BlockPos(9, 3, 6), Blocks.STONE);
        helper.setBlock(new BlockPos(9, 4, 6), Blocks.GRAVEL);
        step = nextStep(helper);
        helper.assertTrue(step != null && step.digs() && step.dig().equals(helper.absolutePos(new BlockPos(9, 3, 6))), "rock under gravel in the corridor is dug, got " + step);
        helper.assertTrue(!MineFinder.isMineable(helper.getLevel(), helper.absolutePos(new BlockPos(9, 3, 6))), "though not outside it");
        helper.succeed();
    }

    /** Ore showing beside the corridor is dug before anything else, and the vein is followed as each block opens the next. The floor is left. */
    private static void minerFollowsVeins(GameTestHelper helper) {
        buildMine(helper);
        BlockPos[] vein = {new BlockPos(9, 1, 4), new BlockPos(9, 1, 3), new BlockPos(9, 1, 2)};
        for (BlockPos pos : vein) helper.setBlock(pos, Blocks.DIAMOND_ORE);
        for (int i = 0; i < vein.length; i++) {
            Trunk.Step step = nextStep(helper);
            helper.assertTrue(step != null && step.digs() && step.dig().equals(helper.absolutePos(vein[i])), "vein block " + i + " is dug next, got " + step);
            helper.setBlock(vein[i], Blocks.AIR);
        }
        helper.setBlock(new BlockPos(9, 0, 6), Blocks.DIAMOND_ORE);
        Trunk.Step step = nextStep(helper);
        helper.assertTrue(step != null && !(step.digs() && step.dig().equals(helper.absolutePos(new BlockPos(9, 0, 6)))), "ore in the floor is left, got " + step);
        helper.setBlock(new BlockPos(9, 0, 6), Blocks.STONE);
        helper.setBlock(new BlockPos(12, 1, 0), Blocks.DIAMOND_ORE);
        step = nextStep(helper);
        helper.assertTrue(step != null && !(step.digs() && step.dig().equals(helper.absolutePos(new BlockPos(12, 1, 0)))), "ore far from the mine's axes is not chased, got " + step);
        var level = helper.getLevel();
        for (var block : new net.minecraft.world.level.block.Block[] {Blocks.DIRT, Blocks.CLAY, Blocks.CALCITE, Blocks.AMETHYST_CLUSTER, Blocks.NETHER_QUARTZ_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.BASALT}) {
            helper.setBlock(new BlockPos(9, 1, 6), block);
            helper.assertTrue(MineFinder.isMineable(level, helper.absolutePos(new BlockPos(9, 1, 6))), block + " is dug");
        }
        helper.setBlock(new BlockPos(9, 1, 6), Blocks.ANCIENT_DEBRIS);
        helper.assertTrue(!MineFinder.isMineable(level, helper.absolutePos(new BlockPos(9, 1, 6))), "ancient debris is left for the player");
        helper.succeed();
    }

    /** A hostile mob near: the golem stops digging, runs to its chest and cowers; when the mob is gone it goes back to work. */
    private static void minerCowersFromMobs(GameTestHelper helper) {
        buildMine(helper);
        ChestBlockEntity chest = helper.getBlockEntity(HUB, ChestBlockEntity.class);
        stockKit(chest, 8);
        BlockPos rock = new BlockPos(7, 1, 6);
        helper.setBlock(rock, Blocks.STONE);
        for (int x : new int[] {8, 9, 10}) helper.setBlock(new BlockPos(x, 1, 8), Blocks.AIR);
        WorkGolem miner = spawnWorker(helper, ModProfessions.MINER, HUB, HUB.east());
        net.minecraft.world.entity.Mob spider = helper.spawn(EntityTypes.CREEPER, new BlockPos(11, 1, 11));
        spider.setNoAi(true);
        boolean[] cowered = {false};
        helper.onEachTick(() -> {
            if (cowered[0] || spider.isRemoved()) return;
            if (com.scottcaldwell.industriousgolems.golem.Anim.S_COWER.equals(miner.clip())) {
                helper.assertTrue(helper.getBlockState(rock).is(Blocks.STONE), "it digs nothing while frightened");
                cowered[0] = true;
                spider.discard();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(cowered[0], "the golem should cower when a hostile mob is near" + describe(helper, miner, chest));
            helper.assertTrue(helper.getBlockState(rock).isAir(), "and get back to work once it is gone" + describe(helper, miner, chest));
        });
    }

    /** The hub and the first slice of the east arm: the golem digs what is left, brings the drops home, and hangs the torches. */
    private static void minerDigsHubAndArm(GameTestHelper helper) {
        buildMine(helper);
        ChestBlockEntity chest = helper.getBlockEntity(HUB, ChestBlockEntity.class);
        stockKit(chest, 8);
        BlockPos[] rock = {new BlockPos(7, 1, 6), new BlockPos(7, 2, 6), new BlockPos(8, 1, 6), new BlockPos(9, 1, 6), new BlockPos(9, 2, 6)};
        for (BlockPos pos : rock) helper.setBlock(pos, Blocks.STONE);
        WorkGolem miner = spawnWorker(helper, ModProfessions.MINER, HUB, HUB.east());
        helper.succeedWhen(() -> {
            for (BlockPos pos : rock) helper.assertTrue(helper.getBlockState(pos).isAir(), "the rock at " + pos + " should be dug" + describe(helper, miner, chest));
            helper.assertTrue(helper.getBlockState(new BlockPos(8, 3, 6)).is(Blocks.WALL_TORCH), "the arm's first brace gets its torch" + describe(helper, miner, chest));
            helper.assertTrue(miner.pack().count(Kit.COBBLE.match()) >= 6, "the rock is picked up into its pack as cobblestone, got " + miner.pack().count(Kit.COBBLE.match()) + describe(helper, miner, chest));
            helper.assertTrue(helper.getBlockState(HUB.below()).is(Blocks.STONE) && helper.getBlockState(new BlockPos(6, 0, 5)).is(Blocks.STONE), "the floor stays");
        });
    }

    /** A column three high in the hub comes down in one swing, top first. */
    private static void minerTakesAColumnPerSwing(GameTestHelper helper) {
        buildMine(helper);
        ChestBlockEntity chest = helper.getBlockEntity(HUB, ChestBlockEntity.class);
        stockKit(chest, 8);
        BlockPos[] column = {new BlockPos(8, 1, 6), new BlockPos(8, 2, 6), new BlockPos(8, 3, 6)};
        for (BlockPos pos : column) helper.setBlock(pos, Blocks.STONE);
        WorkGolem miner = spawnWorker(helper, ModProfessions.MINER, HUB, HUB.east());
        long[] gone = new long[3];
        helper.onEachTick(() -> {
            for (int i = 0; i < 3; i++) {
                if (gone[i] == 0 && helper.getBlockState(column[i]).isAir()) gone[i] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gone[0] != 0 && gone[1] != 0 && gone[2] != 0, "the column should come down" + describe(helper, miner, chest));
            helper.assertTrue(gone[2] <= gone[1] && gone[1] <= gone[0] && gone[0] - gone[2] <= 8, "in one swing, top first: ticks " + gone[2] + ", " + gone[1] + ", " + gone[0]);
        });
    }

    /** A hub of solid rock, four high, is cleared completely: no block of the top row is left out of reach. */
    private static void minerClearsASolidHub(GameTestHelper helper) {
        buildMine(helper);
        ChestBlockEntity chest = helper.getBlockEntity(HUB, ChestBlockEntity.class);
        stockKit(chest, 8);
        List<BlockPos> rock = new java.util.ArrayList<>();
        for (int dx = -Trunk.HUB; dx <= Trunk.HUB; dx++) {
            for (int dz = -Trunk.HUB; dz <= Trunk.HUB; dz++) {
                for (int h = 0; h < Trunk.HUB_HEIGHT; h++) {
                    if (dx == 0 && dz == 0 && h < 3) continue; // the chest, and room for the golem standing on it
                    rock.add(HUB.offset(dx, h, dz));
                }
            }
        }
        for (BlockPos pos : rock) helper.setBlock(pos, Blocks.STONE);
        WorkGolem miner = spawnWorker(helper, ModProfessions.MINER, HUB, HUB.above());
        helper.succeedWhen(() -> {
            List<BlockPos> left = rock.stream().filter(p -> !helper.getBlockState(p).isAir()).toList();
            helper.assertTrue(left.isEmpty(), left.size() + " left, for one " + (left.isEmpty() ? "" : left.get(0).subtract(HUB)) + describe(helper, miner, chest));
        });
    }

    /** Out of torches, the golem digs and builds what it can, then fumes at the chest where its inventory is empty. */
    private static void minerWaitsForKit(GameTestHelper helper) {
        buildMine(helper);
        ChestBlockEntity chest = helper.getBlockEntity(HUB, ChestBlockEntity.class);
        stockKit(chest, 0);
        BlockPos rock = new BlockPos(7, 1, 6);
        helper.setBlock(rock, Blocks.STONE);
        WorkGolem miner = spawnWorker(helper, ModProfessions.MINER, HUB, HUB.east());
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(rock).isAir(), "the rock is dug without waiting for the kit" + describe(helper, miner, chest));
            helper.assertTrue(helper.getBlockState(new BlockPos(9, 1, 5)).is(net.minecraft.tags.BlockTags.FENCES) && helper.getBlockState(new BlockPos(9, 3, 6)).is(net.minecraft.tags.BlockTags.PLANKS),
                    "and the posts and beam go up" + describe(helper, miner, chest));
            helper.assertTrue(!helper.getBlockState(new BlockPos(8, 3, 6)).is(Blocks.WALL_TORCH), "no torch to hang");
            helper.assertTrue(com.scottcaldwell.industriousgolems.golem.Anim.S_FUME.equals(miner.clip()), "out of torches it fumes" + describe(helper, miner, chest));
        });
    }

    /** The miner's inventory: three slots of its own for fences, planks and torches, five general ones; each special slot takes only its kind. */
    private static void minerPackSlots(GameTestHelper helper) {
        WorkGolem miner = spawnWorker(helper, ModProfessions.MINER, HUB, HUB.east());
        miner.pack().add(new ItemStack(Items.OAK_FENCE, 5));
        miner.pack().add(new ItemStack(Items.TORCH, 3));
        miner.pack().add(new ItemStack(Items.OAK_FENCE, 2));
        miner.pack().add(new ItemStack(Items.COBBLESTONE, 30));
        miner.pack().add(new ItemStack(Items.DIRT, 10));
        helper.assertTrue(miner.pack().getItem(Kit.FENCE.ordinal()).getCount() == 7, "fences share the one slot");
        helper.assertTrue(miner.pack().getItem(Kit.TORCH.ordinal()).getCount() == 3, "the torches have a slot of their own");
        helper.assertTrue(miner.pack().getItem(Kit.PLANKS.ordinal()).isEmpty(), "planks have none yet");
        helper.assertTrue(miner.pack().count(Kit.COBBLE.match()) == 30 && miner.pack().fill() == 2, "cobblestone and dirt go in the general slots");
        helper.assertTrue(!miner.pack().takeOne(Kit.TORCH.match()).isEmpty() && miner.pack().count(Kit.TORCH.match()) == 2, "taking one leaves the rest");
        helper.assertTrue(miner.pack().hasLoot(), "dirt is loot to put away");
        for (int i = 0; i < 2; i++) miner.pack().add(new ItemStack(Items.STONE, 64));
        helper.assertTrue(miner.pack().fitsAll(java.util.List.of(new ItemStack(Items.STONE, 64))) && !miner.pack().fitsAll(java.util.List.of(new ItemStack(Items.STONE, 128))), "room for what a block might drop");
        var menu = new com.scottcaldwell.industriousgolems.mine.GolemPackMenu(1, helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL).getInventory(), miner);
        helper.assertTrue(menu.getSlot(Kit.TORCH.ordinal()).mayPlace(new ItemStack(Items.TORCH)) && !menu.getSlot(Kit.TORCH.ordinal()).mayPlace(new ItemStack(Items.DIRT)), "the torch slot takes only torches");
        helper.assertTrue(menu.getSlot(Kit.PLANKS.ordinal()).mayPlace(new ItemStack(Items.BIRCH_PLANKS)), "any planks go in the plank slot");
        helper.assertTrue(menu.getSlot(Kit.SPECIAL).mayPlace(new ItemStack(Items.DIRT)), "the general slots take anything");
        helper.succeed();
    }

    /** The open first slice of the arm gets its brace (fence posts, plank beam, torch), from the pack the golem fills at the chest. */
    private static void minerBuildsSupport(GameTestHelper helper) {
        buildMine(helper);
        ChestBlockEntity chest = helper.getBlockEntity(HUB, ChestBlockEntity.class);
        stockKit(chest, 8);
        WorkGolem miner = spawnWorker(helper, ModProfessions.MINER, HUB, HUB.east());
        helper.succeedWhen(() -> {
            for (int z : new int[] {5, 7}) {
                for (int y = 1; y <= 2; y++) helper.assertTrue(helper.getBlockState(new BlockPos(9, y, z)).is(net.minecraft.tags.BlockTags.FENCES), "a fence post at " + z + "," + y + describe(helper, miner, chest));
            }
            for (int z = 5; z <= 7; z++) helper.assertTrue(helper.getBlockState(new BlockPos(9, 3, z)).is(net.minecraft.tags.BlockTags.PLANKS), "a plank beam at " + z + describe(helper, miner, chest));
            var torch = helper.getBlockState(new BlockPos(8, 3, 6));
            helper.assertTrue(torch.is(Blocks.WALL_TORCH) && torch.getValue(net.minecraft.world.level.block.WallTorchBlock.FACING) == net.minecraft.core.Direction.WEST, "a torch on the middle plank facing the chest, found " + torch + " and planks " + helper.getBlockState(new BlockPos(9, 3, 6)) + describe(helper, miner, chest));
            helper.assertTrue(helper.getBlockState(new BlockPos(9, 1, 6)).isAir() && helper.getBlockState(new BlockPos(9, 2, 6)).isAir(), "the middle of the corridor stays open");
        });
    }

    /** With the first brace up, the golem goes on through it and digs the slice behind. */
    private static void minerPathThroughBrace(GameTestHelper helper) {
        buildMine(helper);
        ChestBlockEntity chest = helper.getBlockEntity(HUB, ChestBlockEntity.class);
        stockKit(chest, 8);
        for (int z : new int[] {5, 7}) {
            for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(9, y, z), Blocks.OAK_FENCE);
        }
        for (int z = 5; z <= 7; z++) helper.setBlock(new BlockPos(9, 3, z), Blocks.OAK_PLANKS);
        helper.setBlock(new BlockPos(8, 3, 6), Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, net.minecraft.core.Direction.WEST));
        for (int x = 10; x <= 12; x++) {
            for (int z = 5; z <= 7; z++) {
                for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        WorkGolem miner = spawnWorker(helper, ModProfessions.MINER, HUB, HUB.east());
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(10, 1, 6)).isAir() && helper.getBlockState(new BlockPos(11, 1, 6)).isAir(), "the golem walks between the brace's posts and digs the slice behind it" + describe(helper, miner, chest));
        });
    }
}
