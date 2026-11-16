package com.scottcaldwell.industriousvillagers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Consumer;

/**
 * Headless end-to-end tests, run with `./gradlew runGameTestServer`. Test instances live in
 * data/industriousvillagers/test_instance; each names one of the functions registered here.
 */
public class ModGameTests {

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, IndustriousVillagers.MODID);

    static {
        FUNCTIONS.register("farmer_chest_io", () -> ModGameTests::farmerChestIo);
        FUNCTIONS.register("farmer_chest_preference", () -> ModGameTests::farmerChestPreference);
        FUNCTIONS.register("farmer_shows_missing_hoe", () -> ModGameTests::farmerShowsMissingHoe);
        FUNCTIONS.register("farmer_inventory_menu", () -> ModGameTests::farmerInventoryMenu);
        FUNCTIONS.register("farmer_sleeps_at_night", () -> ModGameTests::farmerSleepsAtNight);
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

    private static Villager spawnFarmer(GameTestHelper helper, BlockPos at) {
        Villager villager = helper.spawn(EntityType.VILLAGER, at);
        villager.setVillagerData(villager.getVillagerData().withProfession(ModProfessions.FARMER.getDelegate()));
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(COMPOSTER)));
        return villager;
    }

    private static int count(Container c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        }
        return n;
    }

    /**
     * The farmer carries 5 wheat and 20 seeds (4 over the 16 it keeps) and has no hoe or food; the chest holds a hoe
     * and bread. After a visit the chest should hold the wheat and 4 seeds, and the farmer a hoe and bread.
     */
    private static void farmerChestIo(GameTestHelper helper) {
        buildFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.IRON_HOE));
        chest.setItem(1, new ItemStack(Items.BREAD, 10));

        Villager farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 5));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 20));

        boolean[] sawOpen = {false};
        helper.succeedWhen(() -> {
            if (!chest.getEntitiesWithContainerOpen().isEmpty()) sawOpen[0] = true;
            helper.assertTrue(sawOpen[0], "farmer should have opened the chest (lid animation)");
            helper.assertTrue(chest.getEntitiesWithContainerOpen().isEmpty(), "chest should be closed again");
            helper.assertTrue(count(chest, Items.WHEAT) == 5, "chest should hold the 5 harvested wheat, has " + count(chest, Items.WHEAT));
            helper.assertTrue(count(chest, Items.WHEAT_SEEDS) == 4, "chest should hold the 4 surplus seeds, has " + count(chest, Items.WHEAT_SEEDS));
            helper.assertTrue(count(farmer.getInventory(), Items.WHEAT_SEEDS) == 16, "farmer should keep 16 seeds, has " + count(farmer.getInventory(), Items.WHEAT_SEEDS));
            helper.assertTrue(count(farmer.getInventory(), Items.IRON_HOE) == 1, "farmer should have taken the hoe");
            helper.assertTrue(count(farmer.getInventory(), Items.BREAD) > 0, "farmer should have taken bread");
            helper.assertTrue(count(chest, Items.IRON_HOE) == 0, "hoe should have left the chest");
        });
    }

    private static final BlockPos BED_FOOT = new BlockPos(5, 1, 5);
    private static final BlockPos BED_HEAD = BED_FOOT.north();

    /** At night a farmer with a bed and a job site should walk to the bed and sleep. */
    private static void farmerSleepsAtNight(GameTestHelper helper) {
        buildFarm(helper);
        var bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        helper.setBlock(BED_FOOT, bed.setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(BED_HEAD, bed.setValue(BedBlock.PART, BedPart.HEAD));

        Villager farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(BED_HEAD)));

        helper.succeedWhen(() -> helper.assertTrue(farmer.isSleeping(), "farmer should be asleep at night"));
    }

    private static final BlockPos COPPER_CHEST = new BlockPos(4, 1, 4);

    /** Supplies are taken from the regular chest, and the harvest is deposited into the copper chest. */
    private static void farmerChestPreference(GameTestHelper helper) {
        buildFarm(helper);
        helper.setBlock(COPPER_CHEST, Blocks.COPPER_CHEST);
        Container regular = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        Container copper = helper.getBlockEntity(COPPER_CHEST, ChestBlockEntity.class);
        regular.setItem(0, new ItemStack(Items.IRON_HOE));
        regular.setItem(1, new ItemStack(Items.BREAD, 10));

        Villager farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 5));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 16));

        helper.succeedWhen(() -> {
            helper.assertTrue(count(farmer.getInventory(), Items.IRON_HOE) == 1, "farmer should take the hoe from the regular chest");
            helper.assertTrue(count(regular, Items.IRON_HOE) == 0, "hoe should have left the regular chest");
            helper.assertTrue(count(copper, Items.WHEAT) == 5, "wheat should go to the copper chest");
            helper.assertTrue(count(regular, Items.WHEAT) == 0, "wheat should not go to the regular chest");
        });
    }

    /** A farmer with no hoe (and nowhere to get one) shows a hoe over its head; it disappears once it has one. */
    private static void farmerShowsMissingHoe(GameTestHelper helper) {
        buildFarm(helper);
        Villager farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 16));

        helper.runAtTickTime(60, () -> helper.assertTrue(
                com.scottcaldwell.industriousvillagers.needs.NeedsIndicator.isIcon(farmer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)),
                "farmer without a hoe should show an indicator over its head"));
        helper.runAtTickTime(61, () -> farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE)));
        helper.succeedWhen(() -> helper.assertTrue(farmer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(),
                "indicator should clear once the hoe is in the inventory"));
    }

    /** Right-clicking a farmer opens a chest menu over its inventory instead of the trade screen. */
    private static void farmerInventoryMenu(GameTestHelper helper) {
        buildFarm(helper);
        Villager farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT, 5));
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(farmer.getX() + 1, farmer.getY(), farmer.getZ());

        boolean[] held = {false};
        helper.runAtTickTime(5, () -> player.interactOn(farmer, net.minecraft.world.InteractionHand.MAIN_HAND));
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu, "right-click should open a chest menu");
            helper.assertTrue(player.containerMenu.getSlot(0).getItem().is(Items.WHEAT), "menu slot 0 should show the farmer's wheat");
            helper.assertTrue(farmer.getTradingPlayer() == player, "farmer should be held for the player while open");
            held[0] = true;
            player.closeContainer();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(held[0], "menu should have been checked");
            helper.assertTrue(farmer.getTradingPlayer() == null, "farmer should be released when the menu closes");
        });
    }
}
