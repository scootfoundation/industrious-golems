package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.block.GolemChestBlock;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.Face;
import com.scottcaldwell.industriousgolems.golem.Stand;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import com.scottcaldwell.industriousgolems.needs.Need;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import net.minecraft.core.BlockPos;
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
 * <p>The golem's own chest (see {@link GolemChestBlock}) comes first; otherwise containers next to the job site are used,
 * deposits preferring copper chests and supplies regular ones.
 */
public class ContainerInteraction extends TaskRunner<ContainerInteraction.Plan> {

    /** Containers within this many blocks (horizontally; +-2 vertically) of the job site count as "adjacent". */
    public static final int SEARCH_RADIUS = 3;
    private static final int DURATION = 1200;

    private enum Phase { APPROACH, PLAY, DONE }

    private BlockPos chestPos;
    private Plan visit;
    private Phase phase = Phase.DONE;
    private int timer;
    private int stalled;
    private Container opened;
    private Anim.Chest spec;
    private Vec3 spot;
    private boolean openedLid;
    private boolean taken;
    private boolean thrown;
    /** The stack let go of on its way into the chest. */
    private ItemStack stowed;

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
        if (golem.isCarrying()) return null;
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        String id = needs.work().wants().need(level, golem);
        Need need = id == null ? null : needs.need(id);
        if (need == null) return null;
        BlockPos pos = best(level, golem, chest -> slotWith(need, chest) >= 0, false);
        return pos == null ? null : new Plan(pos, need.id());
    }

    private static BlockPos best(ServerLevel level, WorkGolem golem, java.util.function.Predicate<Container> usable,
                                 boolean deposit) {
        BlockPos site = golem.jobSite(level);
        if (site == null) return null;
        BlockPos best = null;
        int bestPref = 0;
        double bestDist = 0;
        for (BlockPos p : BlockPos.betweenClosed(site.offset(-SEARCH_RADIUS, -2, -SEARCH_RADIUS),
                site.offset(SEARCH_RADIUS, 2, SEARCH_RADIUS))) {
            if (level.getBlockEntity(p) == null) continue;
            Container c = HopperBlockEntity.getContainerAt(level, p);
            if (c == null || !usable.test(c)) continue;
            BlockState state = level.getBlockState(p);
            int pref = p.equals(site) && state.getBlock() instanceof GolemChestBlock ? 3
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
        spec = Anim.chest(golem.profession().id());
        spot = null;
        timer = 0;
        stalled = 0;
        stowed = null;
        lookAtChest(golem);
    }

    @Override
    protected boolean allowedNow(ServerLevel level, WorkGolem golem) {
        return phase != Phase.DONE && chestPos != null && super.allowedNow(level, golem);
    }

    private void lookAtChest(WorkGolem golem) {
        golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(Vec3.atCenterOf(chestPos)));
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        if (chestPos == null) return;
        switch (phase) {
            case APPROACH -> {
                lookAtChest(golem);
                if (spot == null) {
                    spot = Stand.front(level, chestPos, golem, spec.dist());
                    if (spot == null) spot = golem.position(); // no room to stage it as the clip wants: do it where it can
                }
                if (!standAt(golem, spot, 1.0)) {
                    if (++stalled > 400) phase = Phase.DONE;
                    return;
                }
                Face.toward(golem, Vec3.atCenterOf(chestPos));
                golem.playClip(visit.supply() == null ? spec.put() : spec.fetch());
                phase = Phase.PLAY;
                timer = 0;
            }
            case PLAY -> {
                holdStill(golem);
                Face.toward(golem, Vec3.atCenterOf(chestPos));
                timer++;
                boolean put = visit.supply() == null;
                float t = timer / 20.0F;
                if (!openedLid && t >= (put ? spec.putOpen() : spec.fetchOpen())) {
                    Container chest = HopperBlockEntity.getContainerAt(level, chestPos);
                    if (chest == null) { phase = Phase.DONE; return; }
                    openedLid = true;
                    opened = chest;
                    chest.startOpen((ContainerUser) golem);
                    golem.setOpenedChestPos(chestPos);
                }
                if (put) {
                    if (stowed == null && t >= spec.putRelease()) { // the hand lets go of the stack
                        stowed = golem.carried().copy();
                        golem.setCarried(ItemStack.EMPTY);
                        golem.swing(InteractionHand.MAIN_HAND);
                    }
                    if (stowed != null && !stowed.isEmpty() && t >= spec.putDrop()) { // and it lands in the chest
                        ItemStack rest = opened == null ? stowed : insert(opened, stowed);
                        if (opened != null) opened.setChanged();
                        stowed = ItemStack.EMPTY;
                        if (!rest.isEmpty()) golem.setCarried(rest);
                    }
                } else {
                    if (!taken && t >= spec.fetchTake() && opened != null) {
                        taken = true;
                        take(ProfessionNeeds.of(golem).need(visit.supply()), golem, opened);
                        opened.setChanged();
                    }
                    if (!thrown && spec.fetchThrow() >= 0 && t >= spec.fetchThrow()) {
                        thrown = true;
                        throwOut(level, golem);
                    }
                }
                if (openedLid && t >= (put ? spec.putClose() : spec.fetchClose())) closeChest(golem);
                if (t >= (put ? spec.putLen() : spec.fetchLen())) phase = Phase.DONE;
            }
            case DONE -> { }
        }
    }

    /** The stack is flung out onto the grass beside the golem, where it will be picked up in the ordinary way. */
    private void throwOut(ServerLevel level, WorkGolem golem) {
        ItemStack stack = golem.carried();
        if (stack.isEmpty()) return;
        golem.setCarried(ItemStack.EMPTY);
        golem.swing(InteractionHand.MAIN_HAND);
        Vec3 from = Face.local(golem.position().add(0, 1.0, 0), golem.getYRot(), 0.3, 0.8);
        Vec3 land = Face.local(golem.position(), golem.getYRot(), spec.landX(), spec.landZ());
        ItemEntity drop = new ItemEntity(level, from.x, from.y, from.z, stack);
        double flight = 11.0;
        drop.setDeltaMovement((land.x - from.x) / flight * 0.95, 0.3, (land.z - from.z) / flight * 0.95);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        closeChest(golem); // also covers interruptions (panic, damage, timeout)
        if (stowed != null && !stowed.isEmpty()) { // let go of a stack the chest never received
            if (golem.carried().isEmpty()) golem.setCarried(stowed);
            stowed = null;
        }
        chestPos = null;
        phase = Phase.DONE;
        openedLid = false;
        taken = false;
        thrown = false;
    }

    private void closeChest(WorkGolem golem) {
        if (opened == null) return;
        opened.stopOpen((ContainerUser) golem);
        golem.setOpenedChestPos(null);
        opened = null;
    }

    // ---- moving the stack -------------------------------------------------------------------------------------

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
