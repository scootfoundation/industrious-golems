package com.scottcaldwell.industriousvillagers.mixin.client;

/** A villager render state that carries the swing progress (vanilla's does not). */
public interface SwingingState {
    float industriousvillagers$getSwing();

    void industriousvillagers$setSwing(float progress);
}