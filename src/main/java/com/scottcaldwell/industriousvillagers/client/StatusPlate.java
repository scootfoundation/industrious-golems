package com.scottcaldwell.industriousvillagers.client;

import com.scottcaldwell.industriousvillagers.IndustriousVillagers;
import com.scottcaldwell.industriousvillagers.ModAttachments;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import com.scottcaldwell.industriousvillagers.needs.StatusData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

/**
 * Draws a status line in the villager's name tag: profession, a hunger meter and the tool it carries (red cross when
 * it has none). Shown while looking at the villager, and from a distance when the villager is blocked.
 */
@EventBusSubscriber(modid = IndustriousVillagers.MODID, value = Dist.CLIENT)
public final class StatusPlate {

    private static final int BAR_SEGMENTS = 10;
    private static final double BLOCKED_VISIBLE_DIST_SQR = 12.0 * 12.0;

    private StatusPlate() { }

    @SubscribeEvent
    public static void onCanRender(RenderNameTagEvent.CanRender event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        StatusData status = villager.getData(ModAttachments.STATUS);
        if (needs == null || !status.enabled()) return;

        Minecraft mc = Minecraft.getInstance();
        boolean targeted = mc.crosshairPickEntity == villager;
        boolean nearBlocked = status.blocked() && mc.player != null
                && mc.player.distanceToSqr(villager) < BLOCKED_VISIBLE_DIST_SQR;
        if (!targeted && !nearBlocked) return;

        event.setCanRender(TriState.TRUE);
        event.setContent(build(villager, needs, status));
    }

    private static Component build(Villager villager, ProfessionNeeds needs, StatusData status) {
        MutableComponent line = Component.empty();
        line.append(villager.getVillagerData().profession().value().name().copy().withStyle(ChatFormatting.WHITE));
        line.append(Component.literal("  "));

        // hunger meter
        line.append(sprite(needs.foodIcon()));
        int full = status.hunger();
        ChatFormatting colour = full > 5 ? ChatFormatting.GREEN : full > 2 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        line.append(Component.literal("█".repeat(full)).withStyle(colour));
        line.append(Component.literal("█".repeat(Math.max(0, BAR_SEGMENTS - full))).withStyle(ChatFormatting.DARK_GRAY));
        line.append(Component.literal("  "));

        // tools: each shows its icon, with a red cross when the villager does not carry it
        java.util.List<ProfessionNeeds.Need> toolNeeds = needs.toolNeeds();
        for (int t = 0; t < toolNeeds.size(); t++) {
            line.append(sprite(toolNeeds.get(t).icon().getItem()));
            if ((status.tools() & (1 << t)) == 0) line.append(Component.literal("✘").withStyle(ChatFormatting.RED));
            line.append(Component.literal(" "));
        }
        return line;
    }

    private static MutableComponent sprite(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return Component.object(new AtlasSprite(AtlasIds.ITEMS, id.withPath("item/" + id.getPath())));
    }
}
