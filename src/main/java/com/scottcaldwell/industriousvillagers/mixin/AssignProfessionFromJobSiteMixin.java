package com.scottcaldwell.industriousvillagers.mixin;

import com.scottcaldwell.industriousvillagers.IndustriousVillagers;
import net.minecraft.core.Holder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Optional;

@Mixin(AssignProfessionFromJobSite.class)
public class AssignProfessionFromJobSiteMixin {

    /**
     * @author IndustriousVillagers
     * @reason Only assign professions that belong to this mod; vanilla professions are suppressed.
     */
    @Overwrite
    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.triggerIf((level, villager) -> {
            if (villager.isBaby()) return false;

            // Vanilla AssignProfessionFromJobSite requires POTENTIAL_JOB_SITE (not JOB_SITE).
            // Skip if already employed (JOB_SITE already set).
            if (villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE)) return false;
            Optional<GlobalPos> jobSiteOpt = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
            if (jobSiteOpt.isEmpty()) return false;

            GlobalPos claimedSite = jobSiteOpt.get();
            if (!claimedSite.dimension().equals(level.dimension())) return false;

            Optional<Holder<PoiType>> poiTypeOpt = level.getPoiManager()
                    .getInSquare(_ -> true, claimedSite.pos(), 0, PoiManager.Occupancy.ANY)
                    .map(PoiRecord::getPoiType)
                    .findFirst();
            if (poiTypeOpt.isEmpty()) return false;

            Holder<PoiType> poiType = poiTypeOpt.get();

            Optional<Holder<VillagerProfession>> professionOpt =
                    BuiltInRegistries.VILLAGER_PROFESSION.entrySet().stream()
                            .filter(e -> e.getKey().identifier().getNamespace().equals(IndustriousVillagers.MODID))
                            .filter(e -> e.getValue().heldJobSite().test(poiType))
                            .findFirst()
                            .flatMap(e -> BuiltInRegistries.VILLAGER_PROFESSION.get(e.getKey()));
            if (professionOpt.isEmpty()) return false;

            villager.setVillagerData(villager.getVillagerData().withProfession(professionOpt.get()));
            villager.refreshBrain(level);
            return true;
        });
    }
}
