package com.scottcaldwell.industriousvillagers.mixin.client;

import com.scottcaldwell.industriousvillagers.client.SwingingState;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copies the villager's swing progress into its render state so the model can animate it. */
@Mixin(VillagerRenderer.class)
public class VillagerRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/npc/villager/Villager;Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;F)V", at = @At("TAIL"))
    private void industriousvillagers$extractSwing(Villager villager, VillagerRenderState state, float partialTick, CallbackInfo ci) {
        ((SwingingState) state).industriousvillagers$setSwing(villager.getAttackAnim(partialTick));
    }
}