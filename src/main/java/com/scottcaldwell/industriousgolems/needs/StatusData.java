package com.scottcaldwell.industriousgolems.needs;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * WorkGolem state synced to clients (a NeoForge attachment) so the status plate can be drawn. The charge and craving
 * travel as entity data on the golem itself.
 *
 * @param status bits 0-4 health 0..20 (half hearts), bit 10 blocked (dormant, or missing a supply), bit 11 plate enabled
 */
public record StatusData(int status) {

    public static final StatusData NONE = new StatusData(0);

    public static final int BLOCKED = 1 << 10;
    public static final int ENABLED = 1 << 11;

    public static final StreamCodec<ByteBuf, StatusData> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(StatusData::new, StatusData::status);

    public static StatusData pack(int health, boolean blocked) {
        return new StatusData((health & 31) | ENABLED | (blocked ? BLOCKED : 0));
    }

    public int health() { return status & 31; }

    public boolean blocked() { return (status & BLOCKED) != 0; }

    public boolean enabled() { return (status & ENABLED) != 0; }
}
