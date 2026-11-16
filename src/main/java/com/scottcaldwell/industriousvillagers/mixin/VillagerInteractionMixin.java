package com.scottcaldwell.industriousvillagers.mixin;

import com.scottcaldwell.industriousvillagers.ModMenus;
import com.scottcaldwell.industriousvillagers.inventory.VillagerMenu;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public class VillagerInteractionMixin {

    /** Vanilla trading is suppressed; our professions open their inventory like a chest instead. */
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void industriousvillagers$interact(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player.getItemInHand(hand).is(Items.VILLAGER_SPAWN_EGG)) return;
        Villager villager = (Villager) (Object) this;
        // our professions and villagers with nothing to trade open the inventory; the rest still trade, and opens it when sneaking
        boolean ours = ProfessionNeeds.of(villager) != null
                || villager.getVillagerData().profession().is(net.minecraft.world.entity.npc.villager.VillagerProfession.NONE)
                || villager.getVillagerData().profession().is(net.minecraft.world.entity.npc.villager.VillagerProfession.NITWIT);
        if ((!ours && !player.isShiftKeyDown()) || !villager.isAlive() || villager.isBaby() || villager.isSleeping()) {
            cir.setReturnValue(InteractionResult.PASS);
            return;
        }
        if (player instanceof ServerPlayer serverPlayer && villager.getTradingPlayer() == null) {
            MenuType<VillagerMenu> type = ModMenus.typeFor(villager);
            if (type != null) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, playerInventory, p) -> VillagerMenu.forVillager(type, id, playerInventory, villager),
                        villager.getDisplayName()));
            }
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
