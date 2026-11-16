package com.scottcaldwell.industriousvillagers.mixin;

import com.scottcaldwell.industriousvillagers.ModActivities;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Brain.class)
public abstract class BrainMixin {

    /** The schedule would otherwise pull the villager out of the task activity within 20 ticks. */
    @Inject(method = "updateActivityFromSchedule", at = @At("HEAD"), cancellable = true)
    private void industriousvillagers$holdTaskActivity(EnvironmentAttributeSystem attributes, long time, Vec3 pos, CallbackInfo ci) {
        if (((Brain<?>) (Object) this).isActive(ModActivities.TASK.get())) ci.cancel();
    }
}
