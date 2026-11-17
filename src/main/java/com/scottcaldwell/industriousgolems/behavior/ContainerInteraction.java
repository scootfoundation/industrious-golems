package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.block.GolemChestBlock;
import com.scottcaldwell.industriousgolems.needs.Need;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A visit to a chest, carrying one stack at a time. A golem carrying something takes it to the chest and puts it in;
 * an empty-handed golem whose work needs a supply takes one stack of it out. It behaves like a player: walks over,
 * looks at the chest, pauses, opens it (lid animation and sound), moves the stack, closes it, looks away and leaves.
 *
 * <p>Its own chest (see {@link GolemChestBlock}) comes first; otherwise containers next to the job site are used,
 * deposits preferring copper chests and supplies regular ones.
 */
public class ContainerInteraction extends TaskRunner<ContainerInteraction.Plan> {

    /** Containers within this many blocks (horizontally; +-2 vertically) of the job site count as "adjacent". */
    public static final int SEARCH_RADIUS = 3;
    private static final int DURATION = 1200;
    private static final double INTERACT_DIST_SQR = 2.5 * 2.5;

    private static final int PAUSE_LOOK = 15;
    private static final int PAUSE_OPEN = 12;
    private static final int PAUSE_STEP = 14;
    private static final int PAUSE_CLOSE = 12;
    private static final int PAUSE_AWAY = 15;

    private enum Phase { APPROACH, LOOK, TRANSFER, CLOSING, LOOK_AWAY, DONE }

    private BlockPos chestPos;
    private Plan visit;
    private Phase phase = Phase.DONE;
    private int timer;
    private Container opened;

    public ContainerInteraction() {
        super(Plan.class, DURATION);
    }

    // ---- choosing a container ---------------------------------------------------------------------------------

    /** Which container to visit; {@code supply} is the need to take from it, or null to put the carried stack in. */
    public record Plan(BlockPos pos, String supply) { }

    /** Puts what the golem carries away: always the first thing to do with a full hand. */
    public static Plan planDeposit(ServerLevel level, WorkGolem golem) {
        ItemStack carried = golem.carried();
        if (carried.isEmpty()) return null;
        BlockPos pos = best(level, golem, chest -> room(chest, carried), true);
        return pos == null ? null : new Plan(pos, null);
    }

    /** Fetches a stack of the supply the work is waiting for, when a container nearby holds some. */
    public static Plan planSupply(ServerLevel level, WorkGolem golem) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        if (needs == null || needs.work() == null || golem.isCarrying()) return null;
        String id = needs.work().wants().need(level, golem);
        Need need = id == null ? null : needs.need(id);
        if (need == null) return null;
        BlockPos pos = best(level, golem, chest -> slotWith(need, chest) >= 0, false);
        return pos == null ? null : new Plan(pos, need.id());
    }

    private static BlockPos best(ServerLevel level, WorkGolem golem, java.util.function.Predicate<Container> usable,
                                 boolean deposit) {
        GlobalPos site = golem.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (site == null || !site.dimension().equals(level.dimension())) return null;
        BlockPos best = null;
        int bestPref = 0;
        double bestDist = 0;
        for (BlockPos p : BlockPos.betweenClosed(site.pos().offset(-SEARCH_RADIUS, -2, -SEARCH_RADIUS),
                site.pos().offset(SEARCH_RADIUS, 2, SEARCH_RADIUS))) {
            if (level.getBlockEntity(p) == null) continue;
            Container c = HopperBlockEntity.getContainerAt(level, p);
            if (c == null || !usable.test(c)) continue;
            BlockState state = level.getBlockState(p);
            int pref = state.getBlock() instanceof GolemChestBlock ? 3
                    : state.is(BlockTags.COPPER_CHESTS) == deposit ? 2 : 1;
            double dist = p.distSqr(golem.blockPosition());
            if (pref > bestPref || (pref == bestPref && dist < bestDist)) {
                best = p.immutable();
                bestPref = pref;
                bestDist = dist;
            }
        }
        return best;
    }

    private static int slotWith(Need need, Container chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (!s.isEmpty() && need.match().test(s)) return i;
        }
        return -1;
    }

    /** Whether the whole stack fits. */
    private static boolean room(Container chest, ItemStack stack) {
        int left = stack.getCount();
        for (int i = 0; i < chest.getContainerSize() && left > 0; i++) {
            if (!chest.canPlaceItem(i, stack)) continue;
            ItemStack there = chest.getItem(i);
            if (there.isEmpty()) left -= Math.min(stack.getMaxStackSize(), chest.getMaxStackSize());
            else if (ItemStack.isSameItemSameComponents(there, stack)) {
                left -= Math.min(there.getMaxStackSize(), chest.getMaxStackSize()) - there.getCount();
            }
        }
        return left <= 0;
    }

    // ---- the visit (runs in the exclusive task activity) --------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        chestPos = plan.pos();
        visit = plan;
        phase = Phase.APPROACH;
        lookAtChest(golem);
        walkToChest(golem);
    }

    @Override
    protected boolean allowedNow(ServerLevel level, WorkGolem golem) {
        return phase != Phase.DONE && chestPos != null && super.allowedNow(level, golem);
    }

    private void lookAtChest(WorkGolem golem) {
        golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(Vec3.atCenterOf(chestPos)));
    }

    private void walkToChest(WorkGolem golem) {
        golem.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(chestPos), 0.5F, 1));
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        if (chestPos == null) return;
        switch (phase) {
            case APPROACH -> {
                lookAtChest(golem);
                if (golem.distanceToSqr(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5) > INTERACT_DIST_SQR) {
                    if (!golem.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) walkToChest(golem);
                    return;
                }
                holdStill(golem);
                phase = Phase.LOOK;
                timer = PAUSE_LOOK;
            }
            case LOOK -> {
                holdStill(golem);
                lookAtChest(golem);
                if (--timer > 0) return;
                Container chest = HopperBlockEntity.getContainerAt(level, chestPos);
                if (chest == null) { phase = Phase.DONE; return; }
                opened = chest;
                chest.startOpen((ContainerUser) golem);
                golem.setOpenedChestPos(chestPos);
                golem.swing(InteractionHand.MAIN_HAND);
                phase = Phase.TRANSFER;
                timer = PAUSE_OPEN;
            }
            case TRANSFER -> {
                holdStill(golem);
                lookAtChest(golem);
                if (--timer > 0) return;
                if (opened != null) {
                    if (visit.supply() == null) deposit(golem, opened);
                    else take(ProfessionNeeds.of(golem).need(visit.supply()), golem, opened);
                    opened.setChanged();
                }
                phase = Phase.CLOSING;
                timer = PAUSE_CLOSE;
            }
            case CLOSING -> {
                holdStill(golem);
                lookAtChest(golem);
                if (--timer > 0) return;
                closeChest(golem);
                Vec3 away = golem.position().subtract(Vec3.atCenterOf(chestPos)).multiply(1, 0, 1).normalize().scale(4);
                golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
                        new BlockPosTracker(BlockPos.containing(golem.position().add(away))));
                phase = Phase.LOOK_AWAY;
                timer = PAUSE_AWAY;
            }
            case LOOK_AWAY -> {
                holdStill(golem);
                if (--timer > 0) return;
                phase = Phase.DONE;
            }
            case DONE -> { }
        }
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        closeChest(golem); // also covers interruptions (panic, damage, timeout)
        chestPos = null;
        phase = Phase.DONE;
    }

    private void closeChest(WorkGolem golem) {
        if (opened == null) return;
        opened.stopOpen((ContainerUser) golem);
        golem.setOpenedChestPos(null);
        opened = null;
    }

    // ---- moving the stack -------------------------------------------------------------------------------------

    /** Puts the carried stack into the chest; whatever does not fit stays in the hand. */
    private static void deposit(WorkGolem golem, Container chest) {
        ItemStack rest = insert(chest, golem.carried().copy());
        golem.setCarried(rest);
    }

    /** Takes one stack of the supply (at most what the need allows) from the chest into the hand. */
    private static void take(Need need, WorkGolem golem, Container chest) {
        if (need == null || golem.isCarrying()) return;
        int slot = slotWith(need, chest);
        if (slot < 0) return;
        ItemStack in = chest.getItem(slot);
        int n = Math.min(need.carry(), in.getCount());
        golem.setCarried(in.copyWithCount(n));
        in.shrink(n);
        if (in.isEmpty()) chest.setItem(slot, ItemStack.EMPTY);
    }

    /** Inserts into the container, merging into matching stacks first; returns whatever did not fit. */
    private static ItemStack insert(Container c, ItemStack stack) {
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
                if (!c.canPlaceItem(i, stack)) continue;
                ItemStack there = c.getItem(i);
                if (pass == 0) {
                    if (there.isEmpty() || !ItemStack.isSameItemSameComponents(there, stack)) continue;
                    int room = Math.min(there.getMaxStackSize(), c.getMaxStackSize()) - there.getCount();
                    int n = Math.min(room, stack.getCount());
                    if (n > 0) { there.grow(n); stack.shrink(n); }
                } else if (there.isEmpty()) {
                    int n = Math.min(Math.min(stack.getMaxStackSize(), c.getMaxStackSize()), stack.getCount());
                    c.setItem(i, stack.copyWithCount(n));
                    stack.shrink(n);
                }
            }
        }
        return stack;
    }
}
