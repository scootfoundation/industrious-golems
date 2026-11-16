package com.scottcaldwell.industriousvillagers;

import com.mojang.serialization.Codec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IndustriousVillagers.MODID);

    /** Game time of the villager's last meal; {@link Long#MIN_VALUE} until first observed. Saved with the villager. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> LAST_MEAL =
            ATTACHMENTS.register("last_meal", () -> AttachmentType.builder(() -> Long.MIN_VALUE)
                    .serialize(Codec.LONG.fieldOf("time"))
                    .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
