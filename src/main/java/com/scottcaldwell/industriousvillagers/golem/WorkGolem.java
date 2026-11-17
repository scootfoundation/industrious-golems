package com.scottcaldwell.industriousvillagers.golem;

import com.mojang.serialization.Dynamic;
import com.scottcaldwell.industriousvillagers.ModEntities;
import com.scottcaldwell.industriousvillagers.ModMenus;
import com.scottcaldwell.industriousvillagers.ModProfessions;
import com.scottcaldwell.industriousvillagers.inventory.GolemMenu;
import com.scottcaldwell.industriousvillagers.needs.NeedsIndicator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * A work golem: a golem that holds a job. Which job (farmer, lumberjack or herder) is fixed by its entity type, see
 * {@link ModEntities}. It claims a job site, keeps itself fed and stocked from nearby chests, and does its work while
 * it is work time; {@link GolemBrain} holds the behaviour.
 */
public class WorkGolem extends AbstractGolem implements InventoryCarrier, ContainerUser {

    private static final Set<net.minecraft.world.item.Item> WANTED = Set.of(Items.BREAD, Items.POTATO, Items.CARROT,
            Items.WHEAT, Items.WHEAT_SEEDS, Items.BEETROOT, Items.BEETROOT_SEEDS, Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD);
    private static final EntityDataAccessor<Integer> UNHAPPY = SynchedEntityData.defineId(WorkGolem.class, EntityDataSerializers.INT);

    private final SimpleContainer inventory = new SimpleContainer(8);
    private @Nullable Player interactingPlayer;
    private @Nullable BlockPos openedChestPos;

    public WorkGolem(EntityType<? extends WorkGolem> type, Level level) {
        super(type, level);
        getNavigation().setCanOpenDoors(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
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

    @Override
    protected Brain.Provider<WorkGolem> brainProvider() {
        return GolemBrain.provider();
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return GolemBrain.makeBrain(profession(), brainProvider().makeBrain(dynamic));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Brain<WorkGolem> getBrain() {
        return (Brain<WorkGolem>) super.getBrain();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("workGolemBrain");
        getBrain().tick(level, this);
        profiler.pop();
        profiler.push("workGolemActivity");
        GolemBrain.updateActivity(this);
        profiler.pop();
        if (getUnhappyCounter() > 0) setUnhappyCounter(getUnhappyCounter() - 1);
        NeedsIndicator.tick(this);
        super.customServerAiStep(level);
    }

    // ---- state ----------------------------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(UNHAPPY, 0);
    }

    /** Ticks left of the head shake shown when the golem cannot work. */
    public int getUnhappyCounter() {
        return entityData.get(UNHAPPY);
    }

    public void setUnhappyCounter(int ticks) {
        entityData.set(UNHAPPY, ticks);
    }

    @Override
    public SimpleContainer getInventory() {
        return inventory;
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        writeInventoryToTag(output);
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        readInventoryFromTag(input);
    }

    // ---- items ----------------------------------------------------------------------------------------------------

    @Override
    public boolean canPickUpLoot() {
        return true;
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity item) {
        InventoryCarrier.pickUpItem(level, this, this, item);
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        var item = stack.getItem();
        return (WANTED.contains(item) || profession().picksUp().contains(item)) && inventory.canAddItem(stack);
    }

    // ---- the player's inventory screen ----------------------------------------------------------------------------

    public @Nullable Player getTradingPlayer() {
        return interactingPlayer;
    }

    /** The player whose inventory screen is open; the golem stands still meanwhile. */
    public void setTradingPlayer(@Nullable Player player) {
        interactingPlayer = player;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer && interactingPlayer == null) {
            var type = ModMenus.typeFor(this);
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, playerInventory, p) -> GolemMenu.forGolem(type, id, playerInventory, this), getDisplayName()));
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
