package com.scottcaldwell.industriousgolems.mixin;

import com.scottcaldwell.industriousgolems.golem.CopperSupply;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;

/**
 * Vanilla copper golems only put things in plain and trapped chests. This lets one carrying something a work golem works with take it
 * to that golem's chest first, while the chest is short of it; see {@link CopperSupply} for which and how much.
 */
@Mixin(TransportItemsBetweenContainers.class)
public abstract class TransportItemsMixin {

    @Shadow
    private AABB getTargetSearchArea(PathfinderMob mob) {
        throw new AssertionError();
    }

    @Shadow
    private static Set<GlobalPos> getVisitedPositions(PathfinderMob mob) {
        throw new AssertionError();
    }

    @Shadow
    private static Set<GlobalPos> getUnreachablePositions(PathfinderMob mob) {
        throw new AssertionError();
    }

    @Shadow
    private TransportItemsBetweenContainers.TransportItemTarget isTargetValidToPick(PathfinderMob mob, Level level, BlockEntity entity,
                                                                              Set<GlobalPos> visited, Set<GlobalPos> unreachable, AABB area) {
        throw new AssertionError();
    }

    @Inject(method = "getTransportTarget", at = @At("HEAD"), cancellable = true)
    private void industriousgolems$golemChestFirst(ServerLevel level, PathfinderMob mob,
                                                  CallbackInfoReturnable<Optional<TransportItemsBetweenContainers.TransportItemTarget>> cir) {
        if (!CopperSupply.carriesSupply(mob)) return;
        AABB area = getTargetSearchArea(mob);
        Set<GlobalPos> visited = getVisitedPositions(mob), unreachable = getUnreachablePositions(mob);
        TransportItemsBetweenContainers.TransportItemTarget best = null;
        for (BlockEntity entity : CopperSupply.golemChests(level, area)) {
            if (best != null && entity.getBlockPos().distToCenterSqr(mob.position()) >= best.pos().distToCenterSqr(mob.position())) continue;
            TransportItemsBetweenContainers.TransportItemTarget target = isTargetValidToPick(mob, level, entity, visited, unreachable, area);
            if (target != null) best = target;
        }
        if (best != null) cir.setReturnValue(Optional.of(best));
    }

    @Inject(method = "isWantedBlock", at = @At("HEAD"), cancellable = true)
    private void industriousgolems$golemChestIsWanted(PathfinderMob mob, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (CopperSupply.belongsIn(mob, state)) cir.setReturnValue(true);
    }

    @Inject(method = "isTargetValidToPick", at = @At("RETURN"), cancellable = true)
    private void industriousgolems$onlyWhileShort(PathfinderMob mob, Level level, BlockEntity entity, Set<GlobalPos> visited, Set<GlobalPos> unreachable,
                                                 AABB area, CallbackInfoReturnable<TransportItemsBetweenContainers.TransportItemTarget> cir) {
        TransportItemsBetweenContainers.TransportItemTarget target = cir.getReturnValue();
        if (target != null && CopperSupply.belongsIn(mob, target.state()) && !CopperSupply.isShort(mob, target.container())) cir.setReturnValue(null);
    }

    @Inject(method = "matchesLeavingItemsRequirement", at = @At("HEAD"), cancellable = true)
    private static void industriousgolems$mayLeaveInGolemChest(PathfinderMob mob, Container container, CallbackInfoReturnable<Boolean> cir) {
        Container half = container instanceof net.minecraft.world.CompoundContainer both ? ((CompoundContainerAccessor) both).industriousgolems$first() : container;
        if (half instanceof com.scottcaldwell.industriousgolems.block.GolemChestEntity chest && CopperSupply.belongsIn(mob, chest.getBlockState())) {
            cir.setReturnValue(CopperSupply.isShort(mob, container)); // (an empty chest or not: this is what it is for)
        }
    }
}
