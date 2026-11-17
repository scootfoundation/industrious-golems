package com.scottcaldwell.industriousvillagers.client;

import com.scottcaldwell.industriousvillagers.needs.Need;
import com.scottcaldwell.industriousvillagers.IndustriousVillagers;
import com.scottcaldwell.industriousvillagers.ModAttachments;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import com.scottcaldwell.industriousvillagers.needs.StatusData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.TriState;
import com.scottcaldwell.industriousvillagers.golem.WorkGolem;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws a three-line status plate above the golem: profession and tools (red cross for a missing one), then the
 * hunger bar and the health bar using the player's own HUD icons. Shown while looking at the golem, and from a
 * distance when the golem is blocked.
 */
@EventBusSubscriber(modid = IndustriousVillagers.MODID, value = Dist.CLIENT)
public final class StatusPlate {

    private static final double BLOCKED_VISIBLE_DIST_SQR = 12.0 * 12.0;
    /** Height of one text line in the name tag. */
    private static final int LINE = 10;

    /** The lines chosen when the render state was extracted, picked up again when it is drawn. */
    private static final Map<EntityRenderState, Component[]> PENDING = new WeakHashMap<>();

    private StatusPlate() { }

    @SubscribeEvent
    public static void onCanRender(RenderNameTagEvent.CanRender event) {
        if (!(event.getEntity() instanceof WorkGolem golem)) return;
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        StatusData status = golem.getData(ModAttachments.STATUS);
        if (needs == null || !status.enabled()) return;

        Minecraft mc = Minecraft.getInstance();
        boolean targeted = mc.crosshairPickEntity == golem;
        boolean nearBlocked = status.blocked() && mc.player != null
                && mc.player.distanceToSqr(golem) < BLOCKED_VISIBLE_DIST_SQR;
        if (!targeted && !nearBlocked) return;

        Component[] lines = {
                title(golem, needs, status),
                bar(status.hunger(), "hud/food_full", "hud/food_half", "hud/food_empty"),
                bar(status.health(), "hud/heart/full", "hud/heart/half", "hud/heart/container")
        };
        synchronized (PENDING) {
            PENDING.put(event.getEntityRenderState(), lines);
        }
        event.setCanRender(TriState.TRUE);
        event.setContent(lines[0]);
    }

    /** Replaces the single name line with the three plate lines, stacked upwards from the usual name position. */
    @SubscribeEvent
    public static void onDoRender(RenderNameTagEvent.DoRender event) {
        EntityRenderState state = event.getEntityRenderState();
        Component[] lines;
        synchronized (PENDING) {
            lines = PENDING.remove(state);
        }
        if (lines == null) return;
        event.setCanceled(true);
        for (int i = 0; i < lines.length; i++) {
            event.getSubmitNodeCollector().submitNameTag(event.getPoseStack(), state.nameTagAttachment,
                    (i - (lines.length - 1)) * LINE, lines[i], !state.isDiscrete, state.lightCoords,
                    state.distanceToCameraSq, event.getCameraRenderState());
        }
    }

    private static Component title(WorkGolem golem, ProfessionNeeds needs, StatusData status) {
        MutableComponent line = Component.empty();
        line.append(golem.getType().getDescription().copy().withStyle(ChatFormatting.WHITE));
        line.append(Component.literal("  "));
        java.util.List<Need> tools = needs.toolNeeds();
        for (int t = 0; t < tools.size(); t++) {
            line.append(itemSprite(tools.get(t).icon().getItem()));
            if ((status.tools() & (1 << t)) == 0) line.append(Component.literal("✘").withStyle(ChatFormatting.RED));
            line.append(Component.literal(" "));
        }
        return line;
    }

    /** Ten icons for a 0..20 value: full, half and empty, as on the player's own bars. */
    private static Component bar(int value, String full, String half, String empty) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < 10; i++) {
            int points = value - i * 2;
            line.append(guiSprite(points >= 2 ? full : points == 1 ? half : empty));
        }
        return line;
    }

    private static MutableComponent guiSprite(String path) {
        return Component.object(new AtlasSprite(AtlasIds.GUI, Identifier.withDefaultNamespace(path)));
    }

    private static MutableComponent itemSprite(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return Component.object(new AtlasSprite(AtlasIds.ITEMS, id.withPath("item/" + id.getPath())));
    }
}