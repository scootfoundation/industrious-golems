package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.block.GolemChestBlock;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.Face;
import com.scottcaldwell.industriousgolems.golem.Stand;
import com.scottcaldwell.industriousgolems.mine.Kit;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import com.scottcaldwell.industriousgolems.needs.Need;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import com.scottcaldwell.industriousgolems.needs.Job;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
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


    private BlockPos chestPos;
    private Plan visit;
    private int timer;
    private int stalled;
    private Container opened;
    private Anim.Chest spec;
    private Vec3 spot;
    /** The stack let go of on its way into the chest. */
    private ItemStack stowed;

    public ContainerInteraction() {
        super(Plan.class, DURATION);
    }

    // ---- choosing a container ---------------------------------------------------------------------------------

    /** Which container to visit; {@code supply} is the need to take from it, or null to put the carried stack in. */
    public record Plan(BlockPos pos, String supply, java.util.function.Predicate<ItemStack> accepts) {
        public Plan(BlockPos pos, String supply) {
            this(pos, supply, s -> true);
        }
    }

    /** Puts what the golem carries away: always the first thing to do with a full hand. */
    public static Plan planDeposit(ServerLevel level, WorkGolem golem) {
        ItemStack carried = golem.carried();
        if (carried.isEmpty()) return null;
        BlockPos pos = best(level, golem, chest -> room(chest, carried), true);
        return pos == null ? null : new Plan(pos, null);
    }

    /**
     * Fetches a stack of a supply for the best job that needs one and that a container nearby can supply. A job whose supply nothing nearby
     * holds (or holds only in a form the job cannot use) is passed over, so it cannot hold up the jobs after it.
     */
    public static Plan planSupply(ServerLevel level, WorkGolem golem, JobList jobs) {
        if (golem.isCarrying()) return null;
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        for (Job job : jobs) {
            if (!job.needsSupply()) continue;
            Need need = needs.need(job.supply());
            if (need == null) continue;
            BlockPos pos = best(level, golem, chest -> need.isKit() ? packVisitUseful(need, golem, chest) : slotWith(need, job.accepts(), chest) >= 0, false);
            if (pos != null) return new Plan(pos, need.id(), job.accepts());
        }
        return null;
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

    /** A slot of the chest holding something of the kit that the golem's pack is still short of, or -1. */
    private static int kitSlot(Need need, WorkGolem golem, Container chest) {
        for (Need.Part part : need.parts()) {
            if (golem.packCount(part.match()) >= part.target()) continue;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack s = chest.getItem(i);
                if (!s.isEmpty() && part.match().test(s)) return i;
            }
        }
        return -1;
    }

    private static int slotWith(Need need, Predicate<ItemStack> accepts, Container chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack s = chest.getItem(i);
            if (!s.isEmpty() && need.match().test(s) && accepts.test(s)) return i;
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

    /** The steps of a visit. The put and the fetch share them; the transfer step is what differs. */
    private enum State { APPROACH, REACH, OPEN, LET_GO, TRANSFERRED, THROWN, CLOSED, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("chest-visit", "Visit the chest (put away / fetch a supply)",
            "Any golem. Walks to the spot in front of the chest, faces it, then plays the profession's clip; the lid, the hand-over and "
                    + "the close are each tied to a moment in that clip.", State.class)
            .start(State.APPROACH)
            .state(State.APPROACH, "Walking to the spot in front of the chest (the side it faces). Never starts the clip anywhere else, and "
                    + "not while up on top of the chest.")
            .state(State.REACH, "Standing in front, clip playing, lid still shut.")
            .state(State.OPEN, "The lid is up (the chest is opened for the golem: lid animation and sound). Waiting for the hand to let go or take.")
            .state(State.LET_GO, "Put only: the hand has let go of the stack and it is on its way in.")
            .state(State.TRANSFERRED, "The stack is in the chest (put) or in the hand (fetch).")
            .state(State.THROWN, "Fetch, for golems that fling the stack out onto the grass to be picked up in the usual way.")
            .state(State.CLOSED, "Lid down; the clip is finishing.")
            .state(State.DONE, "(end) Task over. Interruptions close the lid and give back any stack the chest never took.")
            .edge(State.APPROACH, State.REACH, "settled on the spot, at ground level, facing the chest")
            .edge(State.APPROACH, State.DONE, "no standing room in front, no way there for 20 s, or the chest is gone")
            .edge(State.REACH, State.OPEN, "clip reaches the lid-open moment")
            .edge(State.OPEN, State.LET_GO, "put: clip reaches the release moment")
            .edge(State.OPEN, State.TRANSFERRED, "fetch: clip reaches the take moment, a stack moves chest -> hand")
            .edge(State.LET_GO, State.TRANSFERRED, "put: clip reaches the drop moment, the stack lands in the chest")
            .edge(State.TRANSFERRED, State.THROWN, "fetch, thrower kinds: the clip reaches the throw moment")
            .edge(State.TRANSFERRED, State.CLOSED, "clip reaches the lid-close moment")
            .edge(State.THROWN, State.CLOSED, "clip reaches the lid-close moment")
            .anyTo(State.DONE, "the clip ends, or the visit is interrupted or the chest vanishes (the lid is shut and any stack handed back)", State.APPROACH, State.DONE);

    private final Machine.Run<State> machine = GRAPH.run();

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        chestPos = plan.pos();
        visit = plan;
        machine.reset();
        spec = Anim.chest(golem.profession().id());
        spot = null;
        timer = 0;
        stalled = 0;
        stowed = null;
        golem.setPickupSuspended(true); // a free hand while the stack is in the air must not grab something else
        lookAtChest(golem);
    }

    private void lookAtChest(WorkGolem golem) {
        golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(Vec3.atCenterOf(chestPos)));
    }

    private void done() {
        machine.to(State.DONE);
        finish();
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        if (chestPos == null) return;
        if (machine.is(State.APPROACH)) approach(level, golem);
        else play(level, golem);
    }

    private void approach(ServerLevel level, WorkGolem golem) {
        lookAtChest(golem);
        if (spot == null) {
            spot = Stand.front(level, chestPos, golem, spec.dist());
            // no spot in front of or beside it: only where the golem already stands on the ground, never up on the chest
            if (spot == null && golem.onGround() && Math.abs(golem.getY() - chestPos.getY()) < 0.5) spot = golem.position();
            if (spot == null) { done(); return; }
        }
        // up on top of the chest (its lid is under a block high, so the golem's block position is the chest's own, not the one above):
        // climb down first, the clip needs level ground
        if (golem.getBlockX() == chestPos.getX() && golem.getBlockZ() == chestPos.getZ() && golem.getY() > chestPos.getY() + 0.3) {
            if (++stalled > 400) { done(); return; }
            golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            golem.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new net.minecraft.world.entity.ai.memory.WalkTarget(new BlockPosTracker(BlockPos.containing(spot)), 0.5F, 0));
            return;
        }
        if (!standAt(golem, spot, 1.0)) {
            if (++stalled > 400) done();
            return;
        }
        Face.toward(golem, Vec3.atCenterOf(chestPos));
        golem.playClip(visit.supply() == null ? spec.put() : spec.fetch());
        timer = 0;
        machine.to(State.REACH);
    }

    private void play(ServerLevel level, WorkGolem golem) {
        holdStill(golem);
        Face.toward(golem, Vec3.atCenterOf(chestPos));
        timer++;
        boolean put = visit.supply() == null;
        float t = timer / 20.0F;
        if (machine.is(State.REACH) && t >= (put ? spec.putOpen() : spec.fetchOpen())) {
            Container chest = HopperBlockEntity.getContainerAt(level, chestPos);
            if (chest == null) { done(); return; }
            opened = chest;
            chest.startOpen((ContainerUser) golem);
            golem.setOpenedChestPos(chestPos);
            machine.to(State.OPEN);
        }
        if (put) {
            if (machine.is(State.OPEN) && t >= spec.putRelease()) { // the hand lets go of the stack
                stowed = golem.carried().copy();
                golem.setCarried(ItemStack.EMPTY);
                golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
                machine.to(State.LET_GO);
            }
            if (machine.is(State.LET_GO) && t >= spec.putDrop()) { // and it lands in the chest
                boolean intact = opened != null && HopperBlockEntity.getContainerAt(level, chestPos) == opened; // not broken meanwhile
                ItemStack rest = intact ? insert(opened, stowed) : stowed;
                if (intact) opened.setChanged();
                stowed = ItemStack.EMPTY;
                if (!rest.isEmpty()) golem.setCarried(rest);
                machine.to(State.TRANSFERRED);
            }
        } else {
            if (machine.is(State.OPEN) && t >= spec.fetchTake()) {
                if (opened != null) {
                    take(ProfessionNeeds.of(golem).need(visit.supply()), visit.accepts(), golem, opened);
                    opened.setChanged();
                }
                machine.to(State.TRANSFERRED);
            }
            if (machine.is(State.TRANSFERRED) && spec.fetchThrow() >= 0 && t >= spec.fetchThrow()) {
                throwOut(level, golem);
                machine.to(State.THROWN);
            }
        }
        boolean transferred = put ? machine.is(State.TRANSFERRED) : spec.fetchThrow() < 0 ? machine.is(State.TRANSFERRED) : machine.is(State.THROWN);
        if (transferred && t >= (put ? spec.putClose() : spec.fetchClose())) {
            closeChest(golem);
            machine.to(State.CLOSED);
        }
        if (t >= (put ? spec.putLen() : spec.fetchLen())) {
            closeChest(golem);
            done();
        }
    }

    /** The stack is flung out onto the grass beside the golem, where it will be picked up in the ordinary way. */
    private void throwOut(ServerLevel level, WorkGolem golem) {
        ItemStack stack = golem.carried();
        if (stack.isEmpty()) return;
        golem.setCarried(ItemStack.EMPTY);
        golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
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
        golem.setPickupSuspended(false);
        closeChest(golem); // also covers interruptions (panic, damage, timeout)
        if (stowed != null && !stowed.isEmpty()) { // let go of a stack the chest never received
            if (golem.carried().isEmpty()) golem.setCarried(stowed);
            stowed = null;
        }
        chestPos = null;
    }

    private void closeChest(WorkGolem golem) {
        if (opened == null) return;
        opened.stopOpen((ContainerUser) golem);
        golem.setOpenedChestPos(null);
        opened = null;
    }

    // ---- moving the stack -------------------------------------------------------------------------------------

    /** Takes one stack of the supply (at most what the need allows) from the chest into the hand. */
    private static void take(Need need, Predicate<ItemStack> accepts, WorkGolem golem, Container chest) {
        if (need == null) return;
        if (need.isKit()) { depositLoot(golem, chest); fillPack(need, golem, chest); return; }
        if (golem.isCarrying()) return;
        int slot = slotWith(need, accepts, chest);
        if (slot < 0) return;
        ItemStack in = chest.getItem(slot);
        int n = Math.min(need.carry(), in.getCount());
        golem.setCarried(in.copyWithCount(n));
        in.shrink(n);
        if (in.isEmpty()) chest.setItem(slot, ItemStack.EMPTY);
    }

    /** Tops the pack up from the chest, each kind of the kit to its target. */
    private static void fillPack(Need need, WorkGolem golem, Container chest) {
        for (Need.Part part : need.parts()) {
            int want = part.target() - golem.packCount(part.match());
            for (int i = 0; i < chest.getContainerSize() && want > 0; i++) {
                ItemStack in = chest.getItem(i);
                if (in.isEmpty() || !part.match().test(in)) continue;
                int n = Math.min(want, in.getCount());
                int took = n - golem.packAdd(in.copyWithCount(n)).getCount(); // as much as the pack takes
                if (took <= 0) continue;
                in.shrink(took);
                if (in.isEmpty()) chest.setItem(i, ItemStack.EMPTY);
                want -= took;
            }
        }
    }

    /** Puts what the golem has dug in the chest, everything in its general slots but the cobblestone it keeps for building; what the chest cannot take stays. */
    private static void depositLoot(WorkGolem golem, Container chest) {
        int keep = Kit.RESERVE;
        for (int i = Kit.SPECIAL; i < WorkGolem.PACK_SIZE; i++) {
            ItemStack held = golem.pack().getItem(i);
            if (held.isEmpty()) continue;
            int kept = 0;
            if (Kit.COBBLE.match().test(held)) {
                kept = Math.min(keep, held.getCount());
                keep -= kept;
            }
            if (kept >= held.getCount()) continue;
            ItemStack rest = insert(chest, held.copyWithCount(held.getCount() - kept));
            int left = kept + rest.getCount();
            golem.pack().setItem(i, left <= 0 ? ItemStack.EMPTY : held.copyWithCount(left));
        }
    }

    /** Whether a visit to this chest would do the golem any good: its diggings would go in, or it has something of the kit that the pack is short of. */
    private static boolean packVisitUseful(Need need, WorkGolem golem, Container chest) {
        if (kitSlot(need, golem, chest) >= 0) return true;
        for (int i = Kit.SPECIAL; i < WorkGolem.PACK_SIZE; i++) {
            ItemStack held = golem.pack().getItem(i);
            if (held.isEmpty()) continue;
            boolean cobble = Kit.COBBLE.match().test(held);
            if ((!cobble || golem.packCount(Kit.COBBLE.match()) > Kit.RESERVE) && room(chest, held.copyWithCount(1))) return true;
        }
        return false;
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
