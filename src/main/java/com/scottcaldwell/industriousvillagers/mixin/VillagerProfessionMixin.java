package com.scottcaldwell.industriousvillagers.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(VillagerProfession.class)
public class VillagerProfessionMixin {

    @Mutable
    @Shadow
    public static Predicate<Holder<PoiType>> ALL_ACQUIRABLE_JOBS;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void suppressAllAcquirableJobs(CallbackInfo ci) {
        // Prevent unemployed villagers from seeking any vanilla job site block.
        // Updated in later milestones to match only our custom POI types.
        ALL_ACQUIRABLE_JOBS = holder -> false;
    }
}
