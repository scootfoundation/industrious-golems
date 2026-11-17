package com.scottcaldwell.industriousvillagers.farm;

import com.scottcaldwell.industriousvillagers.behavior.Drops;
import com.scottcaldwell.industriousvillagers.behavior.HeldItem;
import com.scottcaldwell.industriousvillagers.behavior.TaskRunner;
import com.scottcaldwell.industriousvillagers.golem.WorkGolem;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import com.scottcaldwell.industriousvillagers.needs.ToolWear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Works the pre-tilled soil around the job site: reaps each ripe crop with the hoe and sows seeds on bare farmland (so
 * the same plot is replanted from the harvest). Like a player, the farmer walks up, stops and looks at the spot for a
 * moment, then swings. Runs in the exclusive task activity.
 */
public class TendCrops extends TaskRunner<TendCrops.CropPlan> {

    private static final int RADIUS = 10;
    private static final int HEIGHT = 2;
    private static final int MAX_TARGETS = 12;
    private static final double REACH = 4.5;
    private static final int LOOK_TICKS = 15;
    private static final int PAUSE_TICKS = 8;
    private static final int STALL_TICKS = 200;

    private enum Phase { WALK, LOOK, PAUSE }

    /** The spots to tend, nearest first: ripe crops to reap and bare farmland to sow. */
    public record CropPlan(List<BlockPos> targets) { }

    private final List<BlockPos> remaining = new ArrayList<>();
    private BlockPos current;
    private Phase phase = Phase.WALK;
    private int timer;
    private int stalled;

    public TendCrops() {
        super(CropPlan.class, 1800);
    }

    // ---- choosing work ----------------------------------------------------------------------------------------

    public static Object plan(ServerLevel level, WorkGolem golem) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        GlobalPos site = golem.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (site == null || !site.dimension().equals(level.dimension())) return null;
        if (!net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, golem)) return null;
        boolean hasHoe = needs.has(golem, "hoe");
        boolean hasSeeds = needs.has(golem, "seeds");
        List<BlockPos> found = new ArrayList<>();
        BlockPos.betweenClosedStream(site.pos().offset(-RADIUS, -HEIGHT, -RADIUS), site.pos().offset(RADIUS, HEIGHT, RADIUS))
                .forEach(p -> {
                    if ((hasHoe && isRipe(level.getBlockState(p))) || (hasSeeds && isBareFarmland(level, p))) found.add(p.immutable());
                });
        if (found.isEmpty()) return null;
        BlockPos from = golem.blockPosition();
        found.sort(Comparator.comparingDouble(p -> p.distSqr(from)));
        return new CropPlan(List.copyOf(found.subList(0, Math.min(MAX_TARGETS, found.size()))));
    }

    private static boolean isRipe(BlockState state) {
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    private static boolean isBareFarmland(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND);
    }

    // ---- the job ------------------------------------------------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        remaining.clear();
        remaining.addAll(plan.targets());
        current = null;
        phase = Phase.WALK;
        stalled = 0;
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        if (phase == Phase.PAUSE) {
            holdStill(golem);
            if (--timer <= 0) { phase = Phase.WALK; current = null; }
            return;
        }
        if (current == null && !nextTarget(level, golem)) { finish(); return; }

        lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(current)));
        if (golem.getEyePosition().distanceTo(Vec3.atCenterOf(current)) > REACH) {
            phase = Phase.WALK;
            if (++stalled > STALL_TICKS) { remaining.remove(current); current = null; stalled = 0; return; }
            walkTo(golem, new BlockPosTracker(current), 2);
            return;
        }

        holdStill(golem);
        stalled = 0;
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        if (phase == Phase.WALK) { // arrived: take up the hoe to reap, seeds to sow
            phase = Phase.LOOK;
            timer = LOOK_TICKS;
            SimpleContainer inventory = golem.getInventory();
            int slot = level.getBlockState(current).isAir() ? needs.need("seeds").slot(inventory) : needs.need("hoe").slot(inventory);
            if (slot >= 0) HeldItem.hold(golem, inventory.getItem(slot));
        }
        if (--timer > 0) return;
        golem.swing(InteractionHand.MAIN_HAND);
        if (isRipe(level.getBlockState(current))) {
            reap(level, golem, current);
            phase = Phase.LOOK; // look again, then sow the same spot from the harvest
            timer = LOOK_TICKS / 2;
            int seeds = needs.need("seeds").slot(golem.getInventory());
            if (seeds >= 0) HeldItem.hold(golem, golem.getInventory().getItem(seeds));
        } else {
            if (isBareFarmland(level, current)) sow(level, golem, current);
            remaining.remove(current);
            phase = Phase.PAUSE;
            timer = PAUSE_TICKS;
        }
    }

    private boolean nextTarget(ServerLevel level, WorkGolem golem) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        while (!remaining.isEmpty()) {
            BlockPos pos = remaining.get(0);
            BlockState state = level.getBlockState(pos);
            if ((isRipe(state) && needs.has(golem, "hoe")) || (isBareFarmland(level, pos) && needs.has(golem, "seeds"))) {
                current = pos;
                return true;
            }
            remaining.remove(0);
        }
        return false;
    }

    private void reap(ServerLevel level, WorkGolem golem, BlockPos pos) {
        level.destroyBlock(pos, true, golem);
        ToolWear.use(level, golem, "hoe");
        Drops.collectFresh(level, golem, Vec3.atCenterOf(pos));
    }

    private void sow(ServerLevel level, WorkGolem golem, BlockPos pos) {
        SimpleContainer inventory = golem.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) || !(stack.getItem() instanceof BlockItem item)) continue;
            Block block = item.getBlock();
            if (!(block instanceof CropBlock)) continue;
            BlockState crop = block.defaultBlockState();
            if (!crop.canSurvive(level, pos)) continue;
            level.setBlockAndUpdate(pos, crop);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
            inventory.removeItem(i, 1);
            return;
        }
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        current = null;
        remaining.clear();
        phase = Phase.WALK;
    }
}
