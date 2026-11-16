package com.scottcaldwell.industriousvillagers.mixin.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Villagers keep their arms crossed, so a swing (chopping, shearing, sowing, opening a chest) was invisible. While the
 * villager swings, the crossed arms (and the tool held in front of them) lift and strike down.
 */
@Mixin(VillagerModel.class)
public class VillagerModelMixin {

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private ModelPart arms;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)V", at = @At("TAIL"))
    private void industriousvillagers$swingArms(VillagerRenderState state, CallbackInfo ci) {
        float progress = ((SwingingState) state).industriousvillagers$getSwing();
        if (progress > 0.0F) {
            float swing = Mth.sin(progress * (float) Math.PI);
            this.arms.xRot = -0.75F - 1.3F * swing;
            this.arms.yRot = 0.35F * Mth.sin(progress * (float) Math.PI * 2.0F);
        } else {
            this.arms.yRot = 0.0F;
        }
    }
}