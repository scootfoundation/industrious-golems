package com.scottcaldwell.industriousvillagers.behavior;

import net.minecraft.core.BlockPos;

/** Transient per-villager hand-over between {@link StartTask} and the behaviours of the task activity. */
public class TaskState {
    public enum Type { NONE, VISIT, EAT, WORK }

    public Type type = Type.NONE;
    public BlockPos chest;
    public boolean deposit;
    public boolean take;
    public long nextVisit;
    /** Profession work plan handed to that profession's task runner (type WORK). */
    public Object work;
    public long nextWork;

    public void clear() {
        type = Type.NONE;
        chest = null;
        deposit = false;
        take = false;
        work = null;
    }
}
