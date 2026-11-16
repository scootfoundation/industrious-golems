package com.scottcaldwell.industriousvillagers.mixin;

import com.scottcaldwell.industriousvillagers.needs.ToolWear;
import com.scottcaldwell.industriousvillagers.ModProfessions;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HarvestFarmland.class)
public class HarvestFarmlandMixin {

    /** Ticks the farmer looks at a spot before working it. */
    private static final int LOOK_TICKS = 15;

    /**
     * Redirect the vanilla FARMER profession check so that HarvestFarmland
     * activates for our custom FARMER profession rather than the suppressed vanilla one.
     */
    @Redirect(
            method = "checkExtraStartConditions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/Holder;is(Lnet/minecraft/resources/ResourceKey;)Z",
                    ordinal = 0
            )
    )
    private boolean redirectFarmerCheck(Holder<VillagerProfession> holder, ResourceKey<VillagerProfession> ignored) {
        return holder.is(ModProfessions.FARMER_KEY);
    }

    /** PRD: the farmer consumes hoes and eats, so it cannot work without a hoe or (when hungry) food. */
    @Inject(method = "checkExtraStartConditions", at = @At("HEAD"), cancellable = true)
    private void industriousvillagers$requireNeeds(ServerLevel level, Villager villager, CallbackInfoReturnable<Boolean> cir) {
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        if (needs != null && needs.workBlocked(villager)) {
            cir.setReturnValue(false);
        }
    }

    @Shadow
    private BlockPos aboveFarmlandPos;
    @Shadow
    private long nextOkStartTime;

    @Unique
    private BlockPos industriousvillagers$lookedAt;
    @Unique
    private int industriousvillagers$lookTicks;

    /**
     * Like a player would, the farmer stops and looks at the crop (or the bare soil) for a moment before reaping or
     * sowing it. The vanilla tick is skipped while it looks.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void industriousvillagers$lookBeforeActing(ServerLevel level, Villager villager, long time, CallbackInfo ci) {
        if (ProfessionNeeds.of(villager) == null || aboveFarmlandPos == null) return;
        if (!aboveFarmlandPos.closerToCenterThan(villager.position(), 1.0) || time <= nextOkStartTime) return;
        if (!aboveFarmlandPos.equals(industriousvillagers$lookedAt)) {
            industriousvillagers$lookedAt = aboveFarmlandPos;
            industriousvillagers$lookTicks = LOOK_TICKS;
        }
        if (industriousvillagers$lookTicks > 0) {
            industriousvillagers$lookTicks--;
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getNavigation().stop();
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(aboveFarmlandPos));
            ci.cancel();
        }
    }

    /** Each crop harvested wears the hoe by one use. */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z",
                    ordinal = 0
            )
    )
    private boolean industriousvillagers$wearHoeOnHarvest(ServerLevel level, BlockPos pos, boolean drop, Entity entity) {
        boolean destroyed = level.destroyBlock(pos, drop, entity);
        if (destroyed && entity instanceof Villager villager) {
            ToolWear.use(level, villager);
        }
        return destroyed;
    }
}
