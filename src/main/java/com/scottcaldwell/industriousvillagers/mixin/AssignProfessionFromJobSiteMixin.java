package com.scottcaldwell.industriousvillagers.mixin;

import net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(AssignProfessionFromJobSite.class)
public class AssignProfessionFromJobSiteMixin {

    /**
     * @author IndustriousVillagers
     * @reason Prevent all vanilla profession assignment — labourers are assigned via custom job sites in later milestones.
     */
    @Overwrite
    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.triggerIf(villager -> false);
    }
}
