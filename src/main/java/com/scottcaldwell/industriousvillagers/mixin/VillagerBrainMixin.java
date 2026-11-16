package com.scottcaldwell.industriousvillagers.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.scottcaldwell.industriousvillagers.ModActivities;
import com.scottcaldwell.industriousvillagers.behavior.ContainerInteraction;
import com.scottcaldwell.industriousvillagers.behavior.EatFood;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerBrainMixin {

    /** Registers the exclusive task activity (container visits, eating) on brains of our professions. */
    @Inject(method = "registerBrainGoals", at = @At("TAIL"))
    private void industriousvillagers$addTaskActivity(Brain<Villager> brain, CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        if (villager.isBaby() || needs == null) return;
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> tasks = ImmutableList.builder();
        tasks.add(Pair.of(0, new ContainerInteraction()));
        tasks.add(Pair.of(0, new EatFood()));
        if (needs.runner() != null) tasks.add(Pair.of(0, needs.runner().get()));
        brain.addActivity(ModActivities.TASK.get(), tasks.build());
    }
}
