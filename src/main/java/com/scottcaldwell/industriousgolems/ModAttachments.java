package com.scottcaldwell.industriousgolems;

import com.mojang.serialization.Codec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IndustriousGolems.MODID);

    /** Health and blocked state for the client's status plate. Recomputed constantly, so not saved. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.scottcaldwell.industriousgolems.needs.StatusData>> STATUS =
            ATTACHMENTS.register("status", () -> AttachmentType
                    .builder(() -> com.scottcaldwell.industriousgolems.needs.StatusData.NONE)
                    .sync(com.scottcaldwell.industriousgolems.needs.StatusData.STREAM_CODEC)
                    .build());

    /** Game time a cow was last milked by a golem; limits how often it can be milked. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> MILKED_AT =
            ATTACHMENTS.register("milked_at", () -> AttachmentType.builder(() -> Long.MIN_VALUE)
                    .serialize(Codec.LONG.fieldOf("time"))
                    .build());

    /** Transient hand-over between the task trigger and the task activity; not saved. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.scottcaldwell.industriousgolems.behavior.TaskState>> TASK =
            ATTACHMENTS.register("task", () -> AttachmentType.builder(com.scottcaldwell.industriousgolems.behavior.TaskState::new).build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
