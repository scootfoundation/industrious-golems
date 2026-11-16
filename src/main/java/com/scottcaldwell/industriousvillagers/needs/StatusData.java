package com.scottcaldwell.industriousvillagers.needs;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Villager state synced to clients (a NeoForge attachment) so the status plate can be drawn.
 *
 * @param status bits 0-3 hunger fullness 0..10, bit 4 blocked (missing something it needs), bit 5 plate enabled
 * @param tools  bit i set when it carries the profession's i-th tool
 */
public record StatusData(int status, int tools) {

    public static final StatusData NONE = new StatusData(0, 0);

    public static final int HUNGER_MASK = 0xF;
    public static final int BLOCKED = 1 << 4;
    public static final int ENABLED = 1 << 5;

    public static final StreamCodec<ByteBuf, StatusData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StatusData::status,
            ByteBufCodecs.VAR_INT, StatusData::tools,
            StatusData::new);

    public int hunger() { return status & HUNGER_MASK; }

    public boolean blocked() { return (status & BLOCKED) != 0; }

    public boolean enabled() { return (status & ENABLED) != 0; }
}
