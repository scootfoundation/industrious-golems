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

    /** Hunger, tool and blocked state for the client's status plate. Recomputed constantly, so not saved. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.scottcaldwell.industriousvillagers.needs.StatusData>> STATUS =
            ATTACHMENTS.register("status", () -> AttachmentType
                    .builder(() -> com.scottcaldwell.industriousvillagers.needs.StatusData.NONE)
                    .sync(com.scottcaldwell.industriousvillagers.needs.StatusData.STREAM_CODEC)
                    .build());

    /** Transient hand-over between the task trigger and the task activity; not saved. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.scottcaldwell.industriousvillagers.behavior.TaskState>> TASK =
            ATTACHMENTS.register("task", () -> AttachmentType.builder(com.scottcaldwell.industriousvillagers.behavior.TaskState::new).build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
