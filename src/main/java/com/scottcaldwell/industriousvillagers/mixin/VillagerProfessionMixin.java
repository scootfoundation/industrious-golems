package com.scottcaldwell.industriousvillagers.mixin;

import com.scottcaldwell.industriousvillagers.IndustriousVillagers;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private static void redirectAllAcquirableJobs(CallbackInfo ci) {
        // Only match POI types belonging to our professions.
        // The lambda is evaluated lazily at runtime (after DeferredRegister has fired),
        // so our custom professions are visible when it executes.
        ALL_ACQUIRABLE_JOBS = poiHolder ->
                BuiltInRegistries.VILLAGER_PROFESSION.entrySet().stream()
                        .filter(e -> e.getKey().identifier().getNamespace().equals(IndustriousVillagers.MODID))
                        .anyMatch(e -> e.getValue().acquirableJobSite().test(poiHolder));
    }
}
