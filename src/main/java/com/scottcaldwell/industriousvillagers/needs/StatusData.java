package com.scottcaldwell.industriousvillagers.needs;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * WorkGolem state synced to clients (a NeoForge attachment) so the status plate can be drawn.
 *
 * @param status bits 0-4 hunger 0..20, bits 5-9 health 0..20 (half hearts), bit 10 blocked (missing something it
 *               needs), bit 11 plate enabled
 * @param tools  bit i set when it carries the profession's i-th tool
 */
public record StatusData(int status, int tools) {

    public static final StatusData NONE = new StatusData(0, 0);

    public static final int BLOCKED = 1 << 10;
    public static final int ENABLED = 1 << 11;

    public static final StreamCodec<ByteBuf, StatusData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StatusData::status,
            ByteBufCodecs.VAR_INT, StatusData::tools,
            StatusData::new);

    public static int pack(int hunger, int health, boolean blocked) {
        return (hunger & 31) | ((health & 31) << 5) | ENABLED | (blocked ? BLOCKED : 0);
    }

    public int hunger() { return status & 31; }

    public int health() { return (status >> 5) & 31; }

    public boolean blocked() { return (status & BLOCKED) != 0; }

    public boolean enabled() { return (status & ENABLED) != 0; }
}