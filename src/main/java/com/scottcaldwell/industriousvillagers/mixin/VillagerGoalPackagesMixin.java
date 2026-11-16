package com.scottcaldwell.industriousvillagers.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import com.scottcaldwell.industriousvillagers.behavior.StartTask;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerGoalPackages.class)
public class VillagerGoalPackagesMixin {

    /** Villagers without a profession of ours never work, but they still get hungry and eat. */
    @Inject(method = "getIdlePackage", at = @At("RETURN"), cancellable = true)
    private static void industriousvillagers$addEating(
            Holder<VillagerProfession> profession, float speed,
            CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
        if (profession.unwrapKey().map(ProfessionNeeds::byKey).isPresent()) return;
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> b = ImmutableList.builder();
        b.addAll(cir.getReturnValue());
        b.add(Pair.of(5, new StartTask(false)));
        cir.setReturnValue(b.build());
    }

    /** Adds the mod's extra work behaviours to the work activity of our professions. */
    @Inject(method = "getWorkPackage", at = @At("RETURN"), cancellable = true)
    private static void industriousvillagers$addWorkBehaviours(
            Holder<VillagerProfession> profession, float speed,
            CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
        if (profession.unwrapKey().map(ProfessionNeeds::byKey).isEmpty()) return;
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> b = ImmutableList.builder();
        b.addAll(cir.getReturnValue());
        b.add(Pair.of(5, new StartTask()));
        cir.setReturnValue(b.build());
    }
}
