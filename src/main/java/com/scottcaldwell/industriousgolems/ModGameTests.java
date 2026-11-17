package com.scottcaldwell.industriousgolems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

/**
 * Headless end-to-end tests, run with `./gradlew runGameTestServer`. Test instances live in
 * data/industriousgolems/test_instance; each names one of the functions registered here.
 */
public class ModGameTests {

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, IndustriousGolems.MODID);

    static {
        FUNCTIONS.register("farmer_harvests_and_replants", () -> ModGameTests::farmerHarvestsAndReplants);
        FUNCTIONS.register("farmer_chest_io", () -> ModGameTests::farmerChestIo);
        FUNCTIONS.register("farmer_chest_preference", () -> ModGameTests::farmerChestPreference);
        FUNCTIONS.register("farmer_inventory_menu", () -> ModGameTests::farmerInventoryMenu);
        FUNCTIONS.register("herder_shears_sheep", () -> ModGameTests::herderShearsSheep);
        FUNCTIONS.register("herder_milks_cow", () -> ModGameTests::herderMilksCow);
        FUNCTIONS.register("herder_culls_crowded_pen", () -> ModGameTests::herderCullsCrowdedPen);
        FUNCTIONS.register("herder_breeds_sparse_pen", () -> ModGameTests::herderBreedsSparsePen);
        FUNCTIONS.register("herder_inventory_menu", () -> ModGameTests::herderInventoryMenu);
        FUNCTIONS.register("pen_detection", () -> ModGameTests::penDetection);
        FUNCTIONS.register("lumberjack_fells_and_replants", () -> ModGameTests::lumberjackFellsAndReplants);
        FUNCTIONS.register("lumberjack_ignores_log_building", () -> ModGameTests::lumberjackIgnoresLogBuilding);
        FUNCTIONS.register("lumberjack_inventory_menu", () -> ModGameTests::lumberjackInventoryMenu);
        FUNCTIONS.register("golem_dormant_until_offered", () -> ModGameTests::golemDormantUntilOffered);
        FUNCTIONS.register("golem_offering_rules", () -> ModGameTests::golemOfferingRules);
        FUNCTIONS.register("golem_charge_runs_out", () -> ModGameTests::golemChargeRunsOut);
        FUNCTIONS.register("golem_menu_offering_slot", () -> ModGameTests::golemMenuOfferingSlot);
        FUNCTIONS.register("hay_chest_pattern", () -> h -> chestPattern(h, ModProfessions.FARMER, Blocks.HAY_BLOCK.defaultBlockState(), ModBlocks.HAY_CHEST.get()));
        FUNCTIONS.register("mushroom_chest_pattern", () -> h -> chestPattern(h, ModProfessions.LUMBERJACK, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState(), ModBlocks.MUSHROOM_CHEST.get()));
        FUNCTIONS.register("terracotta_chest_pattern", () -> h -> chestPattern(h, ModProfessions.HERDER, Blocks.TERRACOTTA.defaultBlockState(), ModBlocks.TERRACOTTA_CHEST.get()));
        FUNCTIONS.register("chest_pattern_needs_matching_body", () -> ModGameTests::chestPatternNeedsMatchingBody);
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
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
        com.scottcaldwell.industriousgolems.offering.Offerings.fill(golem);
        return golem;
    }

    /** Spawns a golem as a spawn egg would: out of charge, waiting for an offering. */
    private static WorkGolem spawnDormantWorker(GameTestHelper helper, ModProfessions.Profession profession,
                                                BlockPos site, BlockPos at) {
        var type = profession == ModProfessions.FARMER ? ModEntities.FARMER_GOLEM
                : profession == ModProfessions.HERDER ? ModEntities.HERDER_GOLEM : ModEntities.LUMBERJACK_GOLEM;
        WorkGolem golem = helper.spawn(type.get(), at);
        golem.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(site)));
        return golem;
    }

    private static WorkGolem spawnFarmer(GameTestHelper helper, BlockPos at) {
        return spawnWorker(helper, ModProfessions.FARMER, COMPOSTER, at);
    }

    private static int count(Container c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        }
        return n;
    }

    /**
     * The farmer carries 5 wheat and 20 seeds (4 over the 16 it keeps). After a visit the chest should hold the wheat
     * and the 4 surplus seeds.
     */
    private static void farmerChestIo(GameTestHelper helper) {
        buildFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);

        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 5));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 20));

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
            helper.assertTrue(count(chest, Items.WHEAT) == 5, "chest should hold the 5 harvested wheat, has " + count(chest, Items.WHEAT));
            helper.assertTrue(count(chest, Items.WHEAT_SEEDS) == 4, "chest should hold the 4 surplus seeds, has " + count(chest, Items.WHEAT_SEEDS));
            helper.assertTrue(count(farmer.getInventory(), Items.WHEAT_SEEDS) == 16, "farmer should keep 16 seeds, has " + count(farmer.getInventory(), Items.WHEAT_SEEDS));
        });
    }

    /** The farmer reaps ripe wheat bare-handed and sows the bare soil again from the seeds it carries. */
    private static void farmerHarvestsAndReplants(GameTestHelper helper) {
        buildFarm(helper);
        for (int x = 3; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 5), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
            helper.setBlock(new BlockPos(x, 2, 5), Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        }
        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 2, 3));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
        helper.succeedWhen(() -> {
            helper.assertTrue(count(farmer.getInventory(), Items.WHEAT) >= 3, "farmer should have reaped 3 wheat, has " + count(farmer.getInventory(), Items.WHEAT));
            for (int x = 3; x <= 5; x++) {
                var state = helper.getBlockState(new BlockPos(x, 2, 5));
                helper.assertTrue(state.is(Blocks.WHEAT) && state.getValue(net.minecraft.world.level.block.CropBlock.AGE) < 7,
                        "farmer should have replanted at x=" + x);
            }
        });
    }

    private static final BlockPos COPPER_CHEST = new BlockPos(4, 1, 4);

    /** Supplies are taken from the regular chest (seeds here), and the harvest is deposited into the copper chest. */
    private static void farmerChestPreference(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(COPPER_CHEST, Blocks.COPPER_CHEST);
        Container regular = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        Container copper = helper.getBlockEntity(COPPER_CHEST, ChestBlockEntity.class);
        regular.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 10));

        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 5));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 2));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(farmer.getInventory(), Items.WHEAT_SEEDS) == 12, "farmer should top up on seeds from the regular chest, has " + count(farmer.getInventory(), Items.WHEAT_SEEDS));
            helper.assertTrue(count(regular, Items.WHEAT_SEEDS) == 0, "seeds should have left the regular chest");
            helper.assertTrue(count(copper, Items.WHEAT) == 5, "wheat should go to the copper chest");
            helper.assertTrue(count(regular, Items.WHEAT) == 0, "wheat should not go to the regular chest");
        });
    }

    /** Right-clicking a farmer opens a chest menu over its inventory instead of the trade screen. */
    private static void farmerInventoryMenu(GameTestHelper helper) {
        buildFarm(helper);
        WorkGolem farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.getInventory().setItem(0, new ItemStack(Items.WHEAT, 5));
        var player = helper.makeMockServerPlayerInLevel();
        // Keep the mock player far away: it has no real connection, so it must never be a tracker of the farmer
        // (the farmer's status attachment syncs to trackers). interactOn does not check reach.
        player.setPos(farmer.getX() + 5000, farmer.getY(), farmer.getZ());

        boolean[] held = {false};
        // Everything in one tick: the far-away mock player would otherwise have the menu closed as out of reach.
        helper.runAtTickTime(5, () -> {
            player.interactOn(farmer, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(player.containerMenu instanceof com.scottcaldwell.industriousgolems.inventory.GolemMenu, "right-click should open the golem menu");
            var menu = (com.scottcaldwell.industriousgolems.inventory.GolemMenu) player.containerMenu;
            helper.assertTrue(menu.getSlot(1).getItem().is(Items.WHEAT), "first general slot shows the wheat");
            helper.assertTrue(menu.generalSlots() == 7, "farmer allows 7 general slots, has " + menu.generalSlots());
            // 1 offering + 7 general + 36 player slots
            helper.assertTrue(menu.slots.size() == 1 + 7 + 36, "slot count should match the allowance, was " + menu.slots.size());
            helper.assertTrue(farmer.getTradingPlayer() == player, "farmer should be held for the player while open");
            held[0] = true;
            player.closeContainer();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(held[0], "menu should have been checked");
            helper.assertTrue(farmer.getTradingPlayer() == null, "farmer should be released when the menu closes");
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

    private static <T extends net.minecraft.world.entity.animal.Animal> java.util.List<T> alive(GameTestHelper helper, Class<T> type) {
        var box = net.minecraft.world.phys.AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(3, 1, 3)), helper.absolutePos(new BlockPos(8, 3, 8)));
        return helper.getLevel().getEntitiesOfClass(type, box, e -> e.isAlive());
    }

    private static int total(WorkGolem herder, Container chest, net.minecraft.world.item.Item item) {
        return count(herder.getInventory(), item) + count(chest, item);
    }

    /** A herder shears a ready sheep in the pen bare-handed and collects the wool. */
    private static void herderShearsSheep(GameTestHelper helper) {
        buildHerdFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        helper.spawn(EntityType.SHEEP, new BlockPos(5, 1, 5));
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        helper.succeedWhen(() -> {
            var sheep = alive(helper, net.minecraft.world.entity.animal.sheep.Sheep.class);
            helper.assertTrue(!sheep.isEmpty() && !sheep.get(0).readyForShearing(), "the sheep should have been sheared");
            helper.assertTrue(total(herder, chest, Items.WHITE_WOOL) > 0, "the herder should hold or have deposited wool");
            helper.assertTrue(herder.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty(), "the herder holds nothing: it uses its hands");
        });
    }

    /** A herder with a spare bucket milks a cow: the bucket becomes milk. */
    private static void herderMilksCow(GameTestHelper helper) {
        buildHerdFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        helper.spawn(EntityType.COW, new BlockPos(5, 1, 5));
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        herder.getInventory().addItem(new ItemStack(Items.BUCKET));
        helper.succeedWhen(() -> {
            helper.assertTrue(total(herder, chest, Items.MILK_BUCKET) == 1, "the cow should have been milked once");
            helper.assertTrue(count(herder.getInventory(), Items.BUCKET) == 0, "the empty bucket was used up");
        });
    }

    /** Five pigs in a 16-cell pen is over the density band: the herder culls down to three, collecting the meat. */
    private static void herderCullsCrowdedPen(GameTestHelper helper) {
        buildHerdFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        for (int i = 0; i < 5; i++) helper.spawn(EntityType.PIG, new BlockPos(4 + i % 4, 1, 4 + i / 2));
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(alive(helper, net.minecraft.world.entity.animal.pig.Pig.class).size() == 3,
                    "pen should be culled to 3 pigs, has " + alive(helper, net.minecraft.world.entity.animal.pig.Pig.class).size());
            helper.assertTrue(total(herder, chest, Items.PORKCHOP) > 0, "the meat should be collected");
        });
    }

    /** Two cows in a 16-cell pen is below the density band: the herder feeds wheat and a calf is born. */
    private static void herderBreedsSparsePen(GameTestHelper helper) {
        buildHerdFarm(helper);
        helper.spawn(EntityType.COW, new BlockPos(5, 1, 5));
        helper.spawn(EntityType.COW, new BlockPos(6, 1, 6));
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        herder.getInventory().addItem(new ItemStack(Items.WHEAT, 8));
        helper.succeedWhen(() -> {
            helper.assertTrue(alive(helper, net.minecraft.world.entity.animal.cow.Cow.class).size() >= 3, "a calf should have been born");
            helper.assertTrue(count(herder.getInventory(), Items.WHEAT) < 8, "the herder should have fed wheat");
        });
    }

    /** The herder's menu is the offering slot plus seven general slots, like every golem's. */
    private static void herderInventoryMenu(GameTestHelper helper) {
        buildHerdFarm(helper);
        WorkGolem herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(herder.getX() + 5000, herder.getY(), herder.getZ());
        boolean[] checked = {false};
        helper.runAtTickTime(5, () -> {
            player.interactOn(herder, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(player.containerMenu instanceof com.scottcaldwell.industriousgolems.inventory.GolemMenu, "right-click should open the golem menu");
            var menu = (com.scottcaldwell.industriousgolems.inventory.GolemMenu) player.containerMenu;
            helper.assertTrue(menu.generalSlots() == 7, "7 general slots, got " + menu.generalSlots());
            helper.assertTrue(menu.slots.size() == 1 + 7 + 36, "slot count should match, was " + menu.slots.size());
            checked[0] = true;
            player.closeContainer();
        });
        helper.succeedWhen(() -> helper.assertTrue(checked[0], "menu should have been checked"));
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
        var closed = com.scottcaldwell.industriousgolems.herd.HerdPen.cellsAround(level, helper.absolutePos(new BlockPos(7, 1, 7)));
        helper.assertTrue(closed != null && closed.size() == 16, "closed fence should form a 16-cell pen, got " + (closed == null ? "none" : closed.size()));
        helper.setBlock(new BlockPos(5, 1, 7), Blocks.AIR); // open a gap
        var leaky = com.scottcaldwell.industriousgolems.herd.HerdPen.cellsAround(level, helper.absolutePos(new BlockPos(7, 1, 7)));
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
        var leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false);
        for (int y = 4; y <= 5; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 && y == 4) continue;
                    helper.setBlock(new BlockPos(x + dx, y, z + dz), leaf);
                }
            }
        }
        for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
    }

    private static int logsAt(GameTestHelper helper, int x, int z) {
        int n = 0;
        for (int y = 1; y <= 4; y++) if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_LOG)) n++;
        return n;
    }

    /** The lumberjack fells a natural tree bare-handed, keeps the logs for the chest and plants a sapling. */
    private static void lumberjackFellsAndReplants(GameTestHelper helper) {
        buildFarm(helper);
        buildTree(helper, 5, 5);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        jack.getInventory().addItem(new ItemStack(Items.OAK_SAPLING, 2));
        helper.succeedWhen(() -> {
            helper.assertTrue(logsAt(helper, 5, 5) == 0, "all four logs should be felled, " + logsAt(helper, 5, 5) + " left");
            helper.assertTrue(total(jack, chest, Items.OAK_LOG) == 4, "the four logs should be collected, got " + total(jack, chest, Items.OAK_LOG));
            helper.assertTrue(helper.getBlockState(new BlockPos(5, 1, 5)).is(Blocks.OAK_SAPLING), "a sapling should be planted where the trunk stood");
            helper.assertTrue(jack.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty(), "nothing is held afterwards");
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

    /** The lumberjack's menu: the offering slot plus seven general slots. */
    private static void lumberjackInventoryMenu(GameTestHelper helper) {
        buildFarm(helper);
        WorkGolem jack = spawnLumberjack(helper, new BlockPos(2, 1, 4));
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(jack.getX() + 5000, jack.getY(), jack.getZ());
        boolean[] checked = {false};
        helper.runAtTickTime(5, () -> {
            player.interactOn(jack, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(player.containerMenu instanceof com.scottcaldwell.industriousgolems.inventory.GolemMenu, "right-click should open the golem menu");
            var menu = (com.scottcaldwell.industriousgolems.inventory.GolemMenu) player.containerMenu;
            helper.assertTrue(menu.generalSlots() == 7, "7 general slots");
            checked[0] = true;
            player.closeContainer();
        });
        helper.succeedWhen(() -> helper.assertTrue(checked[0], "menu should have been checked"));
    }

    // ---- offerings ------------------------------------------------------------------------------------------------

    private static net.minecraft.world.item.Item craved(WorkGolem golem) {
        return golem.profession().offerings().get(golem.cravingIndex()).icon().getItem();
    }

    private static net.minecraft.world.item.Item notCraved(WorkGolem golem) {
        for (var offering : golem.profession().offerings()) {
            if (offering != golem.profession().offerings().get(golem.cravingIndex())) return offering.icon().getItem();
        }
        throw new IllegalStateException("a profession needs more than one offering");
    }

    /** A golem as a spawn egg makes it does nothing and shows what it craves; the right item wakes it and it works. */
    private static void golemDormantUntilOffered(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(new BlockPos(3, 1, 5), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
        helper.setBlock(new BlockPos(3, 2, 5), Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        WorkGolem farmer = spawnDormantWorker(helper, ModProfessions.FARMER, COMPOSTER, new BlockPos(3, 2, 3));
        net.minecraft.world.item.Item[] given = {null};
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(!farmer.isCharged(), "a new golem starts dormant");
            helper.assertTrue(farmer.cravingIndex() >= 0, "a dormant golem has chosen what it craves");
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 5)).getValue(net.minecraft.world.level.block.CropBlock.AGE) == 7, "a dormant golem leaves the crop alone");
            var status = farmer.getData(ModAttachments.STATUS);
            helper.assertTrue(status.enabled() && status.blocked(), "a dormant golem is flagged as blocked for the plate");
            given[0] = craved(farmer);
            var player = helper.makeMockServerPlayerInLevel();
            player.setPos(farmer.getX() + 5000, farmer.getY(), farmer.getZ());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(given[0], 2));
            player.interactOn(farmer, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(farmer.isCharged() && farmer.chargeTicks() == com.scottcaldwell.industriousgolems.offering.Offerings.PER_OFFERING, "45 minutes of charge");
            helper.assertTrue(craved(farmer) != given[0], "it now craves something different");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(given[0] != null, "the offering was made");
            helper.assertTrue(count(farmer.getInventory(), Items.WHEAT) >= 1, "the woken golem should have reaped the wheat");
        });
    }

    /** Only the craved item is taken; charge is capped at two offerings; the craving never repeats the last item. */
    private static void golemOfferingRules(GameTestHelper helper) {
        buildFarm(helper);
        WorkGolem farmer = spawnDormantWorker(helper, ModProfessions.FARMER, COMPOSTER, new BlockPos(3, 1, 3));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(farmer.profession().offerings().size() == 8, "each golem kind has eight offerings");
            ItemStack wrong = new ItemStack(notCraved(farmer), 3);
            helper.assertTrue(!com.scottcaldwell.industriousgolems.offering.Offerings.offer(farmer, wrong) && wrong.getCount() == 3, "an item it does not crave is refused");
            helper.assertTrue(!com.scottcaldwell.industriousgolems.offering.Offerings.offer(farmer, new ItemStack(Items.BREAD)), "food is no offering");

            int first = farmer.cravingIndex();
            ItemStack one = new ItemStack(craved(farmer), 4);
            helper.assertTrue(com.scottcaldwell.industriousgolems.offering.Offerings.offer(farmer, one) && one.getCount() == 3, "the craved item is taken");
            helper.assertTrue(farmer.cravingIndex() != first, "the next craving differs from the last");
            ItemStack two = new ItemStack(craved(farmer), 4);
            helper.assertTrue(com.scottcaldwell.industriousgolems.offering.Offerings.offer(farmer, two), "a second offering fits");
            helper.assertTrue(farmer.chargeTicks() == com.scottcaldwell.industriousgolems.offering.Offerings.MAX, "charge is two offerings");
            ItemStack three = new ItemStack(craved(farmer), 4);
            helper.assertTrue(!com.scottcaldwell.industriousgolems.offering.Offerings.offer(farmer, three) && three.getCount() == 4, "a third does not fit");
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
            helper.assertTrue(farmer.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.REST), "a dormant golem rests");
            helper.assertTrue(homeless.chargeTicks() == 60, "a golem without a job site does not use charge, has " + homeless.chargeTicks());
        });
    }

    /** The menu's offering slot takes only the craved item, and the golem consumes it. */
    private static void golemMenuOfferingSlot(GameTestHelper helper) {
        buildFarm(helper);
        WorkGolem farmer = spawnDormantWorker(helper, ModProfessions.FARMER, COMPOSTER, new BlockPos(3, 1, 3));
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(farmer.getX() + 5000, farmer.getY(), farmer.getZ());
        boolean[] done = {false};
        helper.runAtTickTime(5, () -> {
            player.interactOn(farmer, net.minecraft.world.InteractionHand.MAIN_HAND);
            var menu = (com.scottcaldwell.industriousgolems.inventory.GolemMenu) player.containerMenu;
            menu.broadcastChanges();
            helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(craved(farmer))) && !menu.getSlot(0).mayPlace(new ItemStack(notCraved(farmer))),
                    "the offering slot accepts only the craved item");
            menu.getSlot(0).set(new ItemStack(craved(farmer)));
            menu.broadcastChanges();
            helper.assertTrue(farmer.isCharged(), "the golem takes what is placed in the offering slot");
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "the slot is empty again");
            done[0] = true;
            player.closeContainer();
        });
        helper.succeedWhen(() -> helper.assertTrue(done[0], "menu should have been checked"));
    }

    // ---- the chest spawn pattern ----------------------------------------------------------------------------------

    /** A body block with a carved pumpkin on top becomes the golem standing on its chest, bound to it and ready to be woken. */
    private static void chestPattern(GameTestHelper helper, ModProfessions.Profession profession,
                                     net.minecraft.world.level.block.state.BlockState body,
                                     net.minecraft.world.level.block.Block chest) {
        BlockPos base = new BlockPos(3, 1, 3);
        BlockPos top = base.above();
        helper.setBlock(base, body);
        var pumpkin = Blocks.CARVED_PUMPKIN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CarvedPumpkinBlock.FACING, net.minecraft.core.Direction.SOUTH);
        helper.setBlock(top, pumpkin);
        WorkGolem golem = com.scottcaldwell.industriousgolems.golem.ChestSpawn.build(helper.getLevel(), helper.absolutePos(top), pumpkin);
        helper.assertTrue(golem != null, "the pattern should spawn a golem");
        helper.assertTrue(golem.profession() == profession, "the right kind of golem");
        helper.assertTrue(helper.getBlockState(base).is(chest), "the body block should become the golem's chest");
        helper.assertTrue(helper.getBlockState(top).isAir(), "the pumpkin should be gone");
        helper.assertTrue(golem.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.pos().equals(helper.absolutePos(base))).isPresent(), "the chest should be its job site");
        helper.assertTrue(!golem.isCharged(), "it starts dormant, waiting for what it craves");
        helper.runAtTickTime(5, () -> {
            net.minecraft.world.item.Item wanted = craved(golem);
            helper.assertTrue(com.scottcaldwell.industriousgolems.offering.Offerings.wants(golem, new ItemStack(wanted)), "it should want its craving");
            com.scottcaldwell.industriousgolems.offering.Offerings.offer(golem, new ItemStack(wanted));
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
        helper.assertTrue(com.scottcaldwell.industriousgolems.golem.ChestSpawn.build(helper.getLevel(),
                helper.absolutePos(base.above()), pumpkin) == null, "stone is not a body block");
        helper.assertTrue(helper.getBlockState(base).is(Blocks.STONE), "nothing should change");
        helper.succeed();
    }
}
