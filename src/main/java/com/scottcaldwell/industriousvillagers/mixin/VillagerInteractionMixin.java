package com.scottcaldwell.industriousvillagers.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public class VillagerInteractionMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void suppressTrading(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!player.getItemInHand(hand).is(Items.VILLAGER_SPAWN_EGG)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
