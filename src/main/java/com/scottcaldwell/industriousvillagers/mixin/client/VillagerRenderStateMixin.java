package com.scottcaldwell.industriousvillagers.mixin.client;

import com.scottcaldwell.industriousvillagers.client.SwingingState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VillagerRenderState.class)
public class VillagerRenderStateMixin implements SwingingState {

    @Unique
    private float industriousvillagers$swing;

    @Override
    public float industriousvillagers$getSwing() {
        return industriousvillagers$swing;
    }

    @Override
    public void industriousvillagers$setSwing(float progress) {
        industriousvillagers$swing = progress;
    }
}