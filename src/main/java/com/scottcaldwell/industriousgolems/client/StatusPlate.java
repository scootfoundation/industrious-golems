package com.scottcaldwell.industriousgolems.client;

import com.scottcaldwell.industriousgolems.IndustriousGolems;
import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import com.scottcaldwell.industriousgolems.offering.Offerings;
import com.scottcaldwell.industriousgolems.needs.StatusData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.TriState;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws a three-line status plate above the golem: its name and what it craves, the charge it has left, then a
 * health bar using the player's own HUD icons. Shown while looking at the golem, and from a distance when the golem
 * is blocked (dormant, or out of a supply).
 */
@EventBusSubscriber(modid = IndustriousGolems.MODID, value = Dist.CLIENT)
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
                title(golem),
                charge(golem),
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

    private static Component title(WorkGolem golem) {
        MutableComponent line = Component.empty();
        line.append(golem.getType().getDescription().copy().withStyle(ChatFormatting.WHITE));
        var offerings = golem.profession().offerings();
        int craving = golem.cravingIndex();
        if (Offerings.acceptsMore(golem) && craving >= 0 && craving < offerings.size()) {
            line.append(Component.translatable("gui.industriousgolems.wants", offerings.get(craving).displayName())
                    .withStyle(ChatFormatting.YELLOW));
        }
        return line;
    }

    private static Component charge(WorkGolem golem) {
        int seconds = golem.chargeSeconds();
        if (seconds <= 0) return Component.translatable("gui.industriousgolems.dormant").withStyle(ChatFormatting.GRAY);
        return Component.translatable("gui.industriousgolems.charge", seconds / 60, String.format("%02d", seconds % 60))
                .withStyle(ChatFormatting.GREEN);
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
}
