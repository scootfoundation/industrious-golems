package com.scottcaldwell.industriousgolems.golem;

import com.scottcaldwell.industriousgolems.ModEntities;
import com.scottcaldwell.industriousgolems.ModProfessions;
import com.scottcaldwell.industriousgolems.needs.NeedsIndicator;
import com.scottcaldwell.industriousgolems.offering.Offerings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import com.scottcaldwell.industriousgolems.mine.GolemPackMenu;
import com.scottcaldwell.industriousgolems.mine.Kit;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A work golem: a golem that holds a job. Which job (farmer, lumberjack, herder or miner) is fixed by its entity type, see
 * {@link ModEntities}. It claims a job site (its chest) and does its work bare-handed, carrying one stack at a time
 * between the work and the chest, but only while it is charged: it stays dormant until a player gives it what it
 * craves (see {@link Offerings}).
 * {@link GolemBrain} holds the behaviour.
 */
public class WorkGolem extends AbstractGolem implements ContainerUser {

    private static final EntityDataAccessor<Integer> UNHAPPY = SynchedEntityData.defineId(WorkGolem.class, EntityDataSerializers.INT);
    /** Index into the profession's offerings of what the golem craves now, or -1. */
    /** How many of the miner's five general slots are taken, shown by how full the sack on its back looks. */
    private static final EntityDataAccessor<Integer> PACK_FILL = SynchedEntityData.defineId(WorkGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CRAVING = SynchedEntityData.defineId(WorkGolem.class, EntityDataSerializers.INT);
    /** Charge left, in whole seconds (rounded up), for the client. */
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(WorkGolem.class, EntityDataSerializers.INT);

    /** The animation clip the golem's current task asked for (empty: none, the client picks walk / idle / waiting) and the game tick it began. */
    private static final EntityDataAccessor<String> CLIP = SynchedEntityData.defineId(WorkGolem.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Long> CLIP_START = SynchedEntityData.defineId(WorkGolem.class, EntityDataSerializers.LONG);

    /** While the game time is before this the golem stands still playing a one-off clip (being handed an offering); 0 when not. */
    private long pausedUntil;
    private int chargeTicks;
    private int lastOffering = -1;
    private @Nullable BlockPos openedChestPos;
    /** Set while the work itself drops things the golem should leave lying until it is done (mining); not saved. */
    private boolean pickupSuspended;
    /** What the golem is doing at the top level; see {@link GolemMode}. Not saved: it is read off the golem again. */
    private final com.scottcaldwell.industriousgolems.behavior.Machine.Run<GolemMode> mode = GolemMode.GRAPH.run();

    /** The mushroom golem's short-range teleport out of places it cannot walk out of. */
    private final Sneeze sneeze = new Sneeze(this);

    public GolemMode mode() {
        return mode.state();
    }

    public WorkGolem(EntityType<? extends WorkGolem> type, Level level) {
        super(type, level);
        getNavigation().setCanOpenDoors(true);
        setPersistenceRequired();
        setDropChance(EquipmentSlot.MAINHAND, 2.0F); // above 1: always dropped, even when it was not a player that killed it
    }

    /** @param range how far it can path to a target: the miner works up to 66 blocks from its chest */
    public static AttributeSupplier.Builder createAttributes(double speed, double range) {
        return Mob.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.FOLLOW_RANGE, range);
    }

    /** The profession of this golem's kind. Safe to call from the constructor chain (reads the entity type only). */
    public ModProfessions.Profession profession() {
        return ModEntities.professionOf(getType());
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanFloat(true);
        return navigation;
    }

    // ---- brain ----------------------------------------------------------------------------------------------------

    private static final Brain.Provider<WorkGolem> BRAIN_PROVIDER = GolemBrain.provider();

    @Override
    protected Brain<WorkGolem> makeBrain(Brain.Packed packed) {
        return BRAIN_PROVIDER.makeBrain(this, packed);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Brain<WorkGolem> getBrain() {
        return (Brain<WorkGolem>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profiler = Profiler.get();
        if (pausedUntil != 0 && level.getGameTime() >= pausedUntil) {
            pausedUntil = 0;
            stopClip();
        }
        if (pausedUntil == 0 && sneeze.tick(level)) {
            // sneezing its way out of a tight spot: the task waits
        } else if (pausedUntil == 0) {
            profiler.push("workGolemBrain");
            getBrain().tick(level, this);
            profiler.pop();
            profiler.push("workGolemActivity");
            GolemBrain.updateActivity(this);
            mode.to(GolemMode.of(this));
            profiler.pop();
        } else {
            getNavigation().stop();
        }
        if (getUnhappyCounter() > 0) setUnhappyCounter(getUnhappyCounter() - 1);
        Offerings.tick(this);
        NeedsIndicator.tick(this);
        super.customServerAiStep(level);
    }

    // ---- state ----------------------------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(UNHAPPY, 0);
        builder.define(PACK_FILL, 0);
        builder.define(CRAVING, -1);
        builder.define(CHARGE, 0);
        builder.define(CLIP, "");
        builder.define(CLIP_START, 0L);
    }

    /** The block this golem works from (its chest), when it has one in this level. */
    public @Nullable BlockPos jobSite(ServerLevel level) {
        return getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension().equals(level.dimension()))
                .map(GlobalPos::pos)
                .orElse(null);
    }

    // ---- animation clips ------------------------------------------------------------------------------------------

    /** Starts a clip (see {@link Anim}) now; the client plays it from its first moment. */
    public void playClip(String id) {
        entityData.set(CLIP, id);
        entityData.set(CLIP_START, level().getGameTime());
    }

    /** Plays a clip that takes the golem's whole attention: it stands still until the clip is over, then goes back to what it was doing. */
    public void playClipAndWait(String id, float seconds) {
        playClip(id);
        pausedUntil = level().getGameTime() + Math.round(seconds * 20);
        getNavigation().stop();
    }

    /** Back to the default walk / idle / waiting animation. */
    public void stopClip() {
        if (!entityData.get(CLIP).isEmpty()) entityData.set(CLIP, "");
    }

    public String clip() {
        return entityData.get(CLIP);
    }

    public long clipStart() {
        return entityData.get(CLIP_START);
    }

    // ---- charge ---------------------------------------------------------------------------------------------------

    /** Ticks of work left. The server's clock; clients only know {@link #chargeSeconds()}. */
    public int chargeTicks() {
        return chargeTicks;
    }

    public void setChargeTicks(int ticks) {
        chargeTicks = Math.max(0, Math.min(Offerings.MAX, ticks));
        int seconds = (chargeTicks + 19) / 20;
        if (entityData.get(CHARGE) != seconds) entityData.set(CHARGE, seconds);
    }

    public int chargeSeconds() {
        return entityData.get(CHARGE);
    }

    /** Whether the golem will work; a dormant one stands still until it is given what it craves. */
    public boolean isCharged() {
        return chargeSeconds() > 0;
    }

    /** Index of the profession offering the golem craves now, or -1 before it has chosen. */
    public int cravingIndex() {
        return entityData.get(CRAVING);
    }

    public void setCravingIndex(int index) {
        entityData.set(CRAVING, index);
    }

    /** Index of the offering it last accepted, so the next craving differs; -1 when none yet. */
    public int lastOffering() {
        return lastOffering;
    }

    public void setLastOffering(int index) {
        lastOffering = index;
    }

    /** Ticks left of the head shake shown when the golem cannot work. */
    public int getUnhappyCounter() {
        return entityData.get(UNHAPPY);
    }

    public void setUnhappyCounter(int ticks) {
        entityData.set(UNHAPPY, ticks);
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("ChargeTicks", chargeTicks);
        output.putInt("Craving", cravingIndex());
        output.putInt("LastOffering", lastOffering);
        if (!pack.isEmpty()) output.store("Pack", ItemStack.OPTIONAL_CODEC.listOf(), pack.getItems().stream().map(ItemStack::copy).toList());
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setChargeTicks(input.getIntOr("ChargeTicks", 0));
        setCravingIndex(input.getIntOr("Craving", -1));
        lastOffering = input.getIntOr("LastOffering", -1);
        pack.clearContent();
        input.read("Pack", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> {
            for (int i = 0; i < list.size() && i < PACK_SIZE; i++) pack.setItem(i, list.get(i));
        });
    }

    // ---- the pack --------------------------------------------------------------------------------------------------

    /**
     * The miner's inventory: a slot each for fence posts, plank beams and torches (see {@link Kit}), then general slots for whatever it digs and
     * the cobblestone it builds with; empty for the other kinds, who carry one stack in the hand.
     */
    private final SimpleContainer pack = new SimpleContainer(PACK_SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (!level().isClientSide()) entityData.set(PACK_FILL, packFill());
        }
    };
    public static final int PACK_SIZE = 8;

    public SimpleContainer pack() {
        return pack;
    }

    /** How many items in the pack match. */
    public int packCount(Predicate<ItemStack> match) {
        int n = 0;
        for (int i = 0; i < pack.getContainerSize(); i++) if (match.test(pack.getItem(i))) n += pack.getItem(i).getCount();
        return n;
    }

    /** Puts the stack in the pack: in its special slot if it has one, otherwise into the general slots. Returns what did not fit. */
    public ItemStack packAdd(ItemStack stack) {
        ItemStack rest = stack.copy();
        int special = Kit.specialSlot(rest);
        if (special >= 0) return mergeInto(rest, special, special + 1);
        return mergeInto(rest, Kit.SPECIAL, PACK_SIZE);
    }

    private ItemStack mergeInto(ItemStack rest, int from, int to) {
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int i = from; i < to && !rest.isEmpty(); i++) {
                ItemStack held = pack.getItem(i);
                if (pass == 0 && !held.isEmpty() && ItemStack.isSameItemSameComponents(held, rest)) {
                    int n = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
                    held.grow(n);
                    rest.shrink(n);
                } else if (pass == 1 && held.isEmpty()) {
                    int n = Math.min(rest.getCount(), rest.getMaxStackSize());
                    pack.setItem(i, rest.copyWithCount(n));
                    rest.shrink(n);
                }
            }
        }
        pack.setChanged();
        return rest;
    }

    /** Whether all of these would fit in the pack together. */
    public boolean packFits(java.util.List<ItemStack> stacks) {
        SimpleContainer trial = new SimpleContainer(PACK_SIZE);
        for (int i = 0; i < PACK_SIZE; i++) trial.setItem(i, pack.getItem(i).copy());
        for (ItemStack stack : stacks) {
            ItemStack rest = stack.copy();
            int special = Kit.specialSlot(rest);
            int from = special >= 0 ? special : Kit.SPECIAL, to = special >= 0 ? special + 1 : PACK_SIZE;
            for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
                for (int i = from; i < to && !rest.isEmpty(); i++) {
                    ItemStack held = trial.getItem(i);
                    if (pass == 0 && !held.isEmpty() && ItemStack.isSameItemSameComponents(held, rest)) {
                        int n = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
                        held.grow(n);
                        rest.shrink(n);
                    } else if (pass == 1 && held.isEmpty()) {
                        int n = Math.min(rest.getCount(), rest.getMaxStackSize());
                        trial.setItem(i, rest.copyWithCount(n));
                        rest.shrink(n);
                    }
                }
            }
            if (!rest.isEmpty()) return false;
        }
        return true;
    }

    /** Takes one matching item out of the pack, or returns an empty stack. */
    public ItemStack packTake(Predicate<ItemStack> match) {
        for (int i = 0; i < pack.getContainerSize(); i++) {
            ItemStack held = pack.getItem(i);
            if (held.isEmpty() || !match.test(held)) continue;
            ItemStack one = held.copyWithCount(1);
            pack.removeItem(i, 1);
            return one;
        }
        return ItemStack.EMPTY;
    }

    /** Whether the general slots hold anything worth putting in the chest: the diggings, and cobblestone beyond what it keeps for building. */
    public boolean hasLoot() {
        int cobble = 0;
        for (int i = Kit.SPECIAL; i < PACK_SIZE; i++) {
            ItemStack held = pack.getItem(i);
            if (held.isEmpty()) continue;
            if (Kit.COBBLE.match().test(held)) cobble += held.getCount();
            else return true;
        }
        return cobble > Kit.RESERVE;
    }

    /** What the client draws the sack from. */
    public int syncedPackFill() {
        return entityData.get(PACK_FILL);
    }

    /** How many of the pack's general slots are taken, out of {@code PACK_SIZE - Kit.SPECIAL}: how full the sack on its back looks. */
    public int packFill() {
        int n = 0;
        for (int i = Kit.SPECIAL; i < PACK_SIZE; i++) if (!pack.getItem(i).isEmpty()) n++;
        return n;
    }

    /** The golem's pack falls where it dies. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        Containers.dropContents(level, this, pack);
    }

    // ---- carrying -------------------------------------------------------------------------------------------------

    /** The one stack the golem carries (its main hand): fresh from its work, on the way to the chest, or seeds to sow. */
    public ItemStack carried() {
        return getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public void setCarried(ItemStack stack) {
        setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    public boolean isCarrying() {
        return !carried().isEmpty();
    }

    /** While suspended the golem does not grab drops as they land; the miner uses it to gather a whole batch at once. */
    public void setPickupSuspended(boolean suspended) {
        pickupSuspended = suspended;
    }

    @Override
    public boolean canPickUpLoot() {
        return true;
    }

    /** Whether the hand has room for this stack: empty, or already holding the same thing short of a full stack. */
    public boolean canTake(ItemStack stack) {
        if (profession() == ModProfessions.MINER) return packFits(java.util.List.of(stack.copyWithCount(1)));
        ItemStack held = carried();
        return held.isEmpty() || (ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < held.getMaxStackSize());
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return isCharged() && !pickupSuspended && canTake(stack) && profession().needs().picksUp(stack);
    }

    /** Mushroom golems land softly. */
    @Override
    public boolean causeFallDamage(double fallDistance, float damageMultiplier, DamageSource source) {
        if (profession() == ModProfessions.LUMBERJACK) return false;
        return super.causeFallDamage(fallDistance, damageMultiplier, source);
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity item) {
        pickUp(item);
    }

    /** Takes the stack lying there with a free hand; with a stack already in hand, tops it up from the same item as far as one stack goes. */
    public void pickUp(ItemEntity item) {
        if (item.isRemoved() || item.getItem().isEmpty() || !canTake(item.getItem())) return;
        ItemStack lying = item.getItem();
        if (profession() == ModProfessions.MINER) { // into the pack, as much as fits
            onItemPickup(item);
            ItemStack rest = packAdd(lying);
            int took = lying.getCount() - rest.getCount();
            take(item, took);
            lying.setCount(rest.getCount());
            if (lying.isEmpty()) item.discard();
            return;
        }
        int n = isCarrying() ? Math.min(lying.getCount(), carried().getMaxStackSize() - carried().getCount()) : lying.getCount();
        onItemPickup(item);
        if (isCarrying()) {
            ItemStack held = carried().copy();
            held.grow(n);
            setCarried(held);
        } else setCarried(lying.copyWithCount(n));
        take(item, n);
        lying.shrink(n);
        if (lying.isEmpty()) item.discard();
    }

    // ---- being given an offering ----------------------------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || player.isSecondaryUseActive()) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (!Offerings.wants(this, held)) {
            if (profession() != ModProfessions.MINER) return InteractionResult.PASS;
            if (!level().isClientSide()) player.openMenu(new SimpleMenuProvider((id, inventory, who) -> new GolemPackMenu(id, inventory, this), getDisplayName()));
            return InteractionResult.SUCCESS;
        }
        if (!level().isClientSide()) {
            Offerings.offer(this, held);
            player.getInventory().setChanged();
        }
        return InteractionResult.SUCCESS;
    }

    // ---- chests ---------------------------------------------------------------------------------------------------

    /** Which chest this golem is holding open (chests only play their lid animation for a ContainerUser). */
    public void setOpenedChestPos(@Nullable BlockPos pos) {
        openedChestPos = pos;
    }

    @Override
    public boolean hasContainerOpen(ContainerOpenersCounter counter, BlockPos pos) {
        BlockPos opened = openedChestPos;
        if (opened == null) return false;
        if (opened.equals(pos)) return true;
        BlockState state = level().getBlockState(opened);
        return state.getBlock() instanceof ChestBlock
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                && ChestBlock.getConnectedBlockPos(opened, state).equals(pos);
    }

    @Override
    public double getContainerInteractionRange() {
        return 3.0;
    }

    // ---- sounds ---------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COPPER_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COPPER_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.COPPER_GOLEM_STEP, 1.0F, 1.0F);
    }
}
