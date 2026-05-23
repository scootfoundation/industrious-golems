package com.scottcaldwell.industriousvillagers.mixin;

import com.scottcaldwell.industriousvillagers.ModProfessions;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HarvestFarmland.class)
public class HarvestFarmlandMixin {

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
}
