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
        FUNCTIONS.register("farmer_eats_when_hungry", () -> ModGameTests::farmerEatsWhenHungry);
        FUNCTIONS.register("hungry_farmer_without_food_stops_and_shows_food", () -> ModGameTests::hungryFarmerWithoutFood);
        FUNCTIONS.register("farmer_keeps_tool_in_tool_slot", () -> ModGameTests::farmerKeepsToolInToolSlot);
        FUNCTIONS.register("farmer_sleeps_at_night", () -> ModGameTests::farmerSleepsAtNight);
        FUNCTIONS.register("herder_shears_sheep", () -> ModGameTests::herderShearsSheep);
        FUNCTIONS.register("herder_milks_cow", () -> ModGameTests::herderMilksCow);
        FUNCTIONS.register("herder_culls_crowded_pen", () -> ModGameTests::herderCullsCrowdedPen);
        FUNCTIONS.register("herder_breeds_sparse_pen", () -> ModGameTests::herderBreedsSparsePen);
        FUNCTIONS.register("herder_inventory_menu", () -> ModGameTests::herderInventoryMenu);
        FUNCTIONS.register("herder_organizes_tools", () -> ModGameTests::herderOrganizesTools);
        FUNCTIONS.register("pen_detection", () -> ModGameTests::penDetection);
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

        helper.runAtTickTime(60, () -> {
            var status = farmer.getData(ModAttachments.STATUS);
            helper.assertTrue(status.enabled() && status.blocked(), "farmer without a hoe should be flagged as blocked");
            helper.assertTrue(status.tools() == 0, "no tool should be reported");
        });
        helper.runAtTickTime(61, () -> farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE)));
        helper.succeedWhen(() -> {
            var status = farmer.getData(ModAttachments.STATUS);
            helper.assertTrue(!status.blocked(), "blocked flag should clear once the hoe is in the inventory");
            helper.assertTrue(status.tools() > 0, "the carried tool should be reported");
            helper.assertTrue(status.hunger() == 10, "a freshly seen farmer should be full, was " + status.hunger());
        });
    }

    /** Right-clicking a farmer opens a chest menu over its inventory instead of the trade screen. */
    private static void farmerInventoryMenu(GameTestHelper helper) {
        buildFarm(helper);
        Villager farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.getInventory().setItem(0, new ItemStack(Items.IRON_HOE));
        farmer.getInventory().setItem(1, new ItemStack(Items.WHEAT, 5));
        var player = helper.makeMockServerPlayerInLevel();
        // Keep the mock player far away: it has no real connection, so it must never be a tracker of the farmer
        // (the farmer's status attachment syncs to trackers). interactOn does not check reach.
        player.setPos(farmer.getX() + 5000, farmer.getY(), farmer.getZ());

        boolean[] held = {false};
        // Everything in one tick: the far-away mock player would otherwise have the menu closed as out of reach.
        helper.runAtTickTime(5, () -> {
            player.interactOn(farmer, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(player.containerMenu instanceof com.scottcaldwell.industriousvillagers.inventory.VillagerMenu, "right-click should open the villager menu");
            var menu = (com.scottcaldwell.industriousvillagers.inventory.VillagerMenu) player.containerMenu;
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.IRON_HOE), "slot 0 is the tool slot and shows the hoe");
            helper.assertTrue(menu.getSlot(1).getItem().is(Items.WHEAT), "first general slot shows the wheat");
            helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_HOE)) && !menu.getSlot(0).mayPlace(new ItemStack(Items.WHEAT)),
                    "the tool slot accepts only hoes");
            helper.assertTrue(menu.generalSlots() == 7, "farmer allows 7 general slots, has " + menu.generalSlots());
            // 1 tool + 7 general + 36 player slots
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

    /** Wheat sitting in the tool slot and the hoe elsewhere: the farmer tidies its inventory so the hoe is in slot 0. */
    private static void farmerKeepsToolInToolSlot(GameTestHelper helper) {
        buildFarm(helper);
        Villager farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.getInventory().setItem(0, new ItemStack(Items.WHEAT, 3));
        farmer.getInventory().setItem(4, new ItemStack(Items.IRON_HOE));

        helper.succeedWhen(() -> {
            helper.assertTrue(farmer.getInventory().getItem(0).is(Items.IRON_HOE), "hoe should be in the tool slot");
            helper.assertTrue(count(farmer.getInventory(), Items.WHEAT) == 3, "wheat should still be carried");
        });
    }

    private static void makeHungry(Villager farmer) {
        farmer.setData(ModAttachments.LAST_MEAL, farmer.level().getGameTime() - com.scottcaldwell.industriousvillagers.needs.Hunger.INTERVAL - 1);
    }

    /** A hungry farmer eats one bread, is no longer hungry, and holds nothing afterwards. */
    private static void farmerEatsWhenHungry(GameTestHelper helper) {
        buildFarm(helper);
        Villager farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.getInventory().addItem(new ItemStack(Items.BREAD, 3));
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        makeHungry(farmer);

        helper.succeedWhen(() -> {
            helper.assertTrue(count(farmer.getInventory(), Items.BREAD) == 2, "farmer should have eaten one bread, has " + count(farmer.getInventory(), Items.BREAD));
            helper.assertTrue(!com.scottcaldwell.industriousvillagers.needs.Hunger.isHungry(farmer), "farmer should be fed");
            helper.assertTrue(farmer.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty(), "farmer should not still hold food");
        });
    }

    /** Hungry with nothing to eat: the farmer shows bread over its head and will not harvest. */
    private static void hungryFarmerWithoutFood(GameTestHelper helper) {
        buildFarm(helper);
        Villager farmer = spawnFarmer(helper, new BlockPos(3, 1, 3));
        farmer.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 16));
        makeHungry(farmer);

        helper.succeedWhen(() -> {
            var status = farmer.getData(ModAttachments.STATUS);
            helper.assertTrue(status.blocked() && status.hunger() == 0, "hungry farmer without food should be flagged blocked with an empty meter, hunger " + status.hunger());
            helper.assertTrue(com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds.of(farmer).workBlocked(farmer), "should be blocked from working");
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

    private static Villager spawnHerder(GameTestHelper helper, BlockPos at) {
        Villager villager = helper.spawn(EntityType.VILLAGER, at);
        villager.setVillagerData(villager.getVillagerData().withProfession(ModProfessions.HERDER.getDelegate()));
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(LOOM)));
        return villager;
    }

    private static <T extends net.minecraft.world.entity.animal.Animal> java.util.List<T> alive(GameTestHelper helper, Class<T> type) {
        var box = net.minecraft.world.phys.AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(3, 1, 3)), helper.absolutePos(new BlockPos(8, 3, 8)));
        return helper.getLevel().getEntitiesOfClass(type, box, e -> e.isAlive());
    }

    private static int total(Villager herder, Container chest, net.minecraft.world.item.Item item) {
        return count(herder.getInventory(), item) + count(chest, item);
    }

    /** A herder with shears shears a ready sheep in the pen, collects the wool and wears the shears. */
    private static void herderShearsSheep(GameTestHelper helper) {
        buildHerdFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        helper.spawn(EntityType.SHEEP, new BlockPos(5, 1, 5));
        Villager herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        herder.getInventory().addItem(new ItemStack(Items.SHEARS));
        helper.succeedWhen(() -> {
            var sheep = alive(helper, net.minecraft.world.entity.animal.sheep.Sheep.class);
            helper.assertTrue(!sheep.isEmpty() && !sheep.get(0).readyForShearing(), "the sheep should have been sheared");
            helper.assertTrue(total(herder, chest, Items.WHITE_WOOL) > 0, "the herder should hold or have deposited wool");
            int slot = -1;
            for (int i = 0; i < herder.getInventory().getContainerSize(); i++) {
                if (herder.getInventory().getItem(i).is(Items.SHEARS)) slot = i;
            }
            helper.assertTrue(slot >= 0 && herder.getInventory().getItem(slot).getDamageValue() > 0, "the shears should be worn");
        });
    }

    /** A herder with a bucket milks a cow: the bucket becomes milk. */
    private static void herderMilksCow(GameTestHelper helper) {
        buildHerdFarm(helper);
        ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        helper.spawn(EntityType.COW, new BlockPos(5, 1, 5));
        Villager herder = spawnHerder(helper, new BlockPos(2, 1, 4));
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
        Villager herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        herder.getInventory().addItem(new ItemStack(Items.IRON_SWORD));
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
        Villager herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        herder.getInventory().addItem(new ItemStack(Items.WHEAT, 8));
        helper.succeedWhen(() -> {
            helper.assertTrue(alive(helper, net.minecraft.world.entity.animal.cow.Cow.class).size() >= 3, "a calf should have been born");
            helper.assertTrue(count(herder.getInventory(), Items.WHEAT) < 8, "the herder should have fed wheat");
        });
    }

    /** The herder's menu has one dedicated slot per tool (shears, sword, bucket) and five general slots. */
    private static void herderInventoryMenu(GameTestHelper helper) {
        buildHerdFarm(helper);
        Villager herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(herder.getX() + 5000, herder.getY(), herder.getZ());
        boolean[] checked = {false};
        helper.runAtTickTime(5, () -> {
            player.interactOn(herder, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(player.containerMenu instanceof com.scottcaldwell.industriousvillagers.inventory.VillagerMenu, "right-click should open the villager menu");
            var menu = (com.scottcaldwell.industriousvillagers.inventory.VillagerMenu) player.containerMenu;
            helper.assertTrue(menu.toolSlots() == 3 && menu.generalSlots() == 5, "3 tool and 5 general slots, got " + menu.toolSlots() + "/" + menu.generalSlots());
            helper.assertTrue(menu.slots.size() == 3 + 5 + 36, "slot count should match, was " + menu.slots.size());
            helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(Items.SHEARS)) && !menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_SWORD)), "slot 0 takes shears");
            helper.assertTrue(menu.getSlot(1).mayPlace(new ItemStack(Items.IRON_SWORD)) && !menu.getSlot(1).mayPlace(new ItemStack(Items.BUCKET)), "slot 1 takes a sword");
            helper.assertTrue(menu.getSlot(2).mayPlace(new ItemStack(Items.BUCKET)) && !menu.getSlot(2).mayPlace(new ItemStack(Items.SHEARS)), "slot 2 takes a bucket");
            checked[0] = true;
            player.closeContainer();
        });
        helper.succeedWhen(() -> helper.assertTrue(checked[0], "menu should have been checked"));
    }

    /** Tools scattered through the inventory are tidied into their own slots, and the plate reports which are carried. */
    private static void herderOrganizesTools(GameTestHelper helper) {
        buildHerdFarm(helper);
        Villager herder = spawnHerder(helper, new BlockPos(2, 1, 4));
        herder.getInventory().setItem(0, new ItemStack(Items.BUCKET));
        herder.getInventory().setItem(5, new ItemStack(Items.SHEARS));
        herder.getInventory().setItem(6, new ItemStack(Items.IRON_SWORD));
        helper.succeedWhen(() -> {
            helper.assertTrue(herder.getInventory().getItem(0).is(Items.SHEARS), "shears belong in slot 0");
            helper.assertTrue(herder.getInventory().getItem(1).is(Items.IRON_SWORD), "sword belongs in slot 1");
            helper.assertTrue(herder.getInventory().getItem(2).is(Items.BUCKET), "bucket belongs in slot 2");
            helper.assertTrue(herder.getData(ModAttachments.STATUS).tools() == 0b111, "all three tools should be reported");
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
        var closed = com.scottcaldwell.industriousvillagers.herd.HerdPen.cellsAround(level, helper.absolutePos(new BlockPos(7, 1, 7)));
        helper.assertTrue(closed != null && closed.size() == 16, "closed fence should form a 16-cell pen, got " + (closed == null ? "none" : closed.size()));
        helper.setBlock(new BlockPos(5, 1, 7), Blocks.AIR); // open a gap
        var leaky = com.scottcaldwell.industriousvillagers.herd.HerdPen.cellsAround(level, helper.absolutePos(new BlockPos(7, 1, 7)));
        helper.assertTrue(leaky == null, "a gap in the fence should leak into open ground");
        helper.succeed();
    }
}