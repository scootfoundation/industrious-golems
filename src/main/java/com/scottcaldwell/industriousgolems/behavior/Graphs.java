package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.farm.TendCrops;
import com.scottcaldwell.industriousgolems.golem.GolemMode;
import com.scottcaldwell.industriousgolems.herd.HerdWork;
import com.scottcaldwell.industriousgolems.lumber.FellTree;
import com.scottcaldwell.industriousgolems.mine.MineRock;

import java.util.List;

/** Every state machine in the mod, in the order the diagram page shows them. */
public final class Graphs {
    private Graphs() { }

    public static List<Machine.Graph<?>> all() {
        return List.of(GolemMode.GRAPH, ContainerInteraction.GRAPH, PickUp.GRAPH, TendCrops.GRAPH, FellTree.GRAPH, MineRock.GRAPH, HerdWork.GRAPH);
    }

    /** All the graphs as a JSON array. */
    public static String json() {
        StringBuilder b = new StringBuilder("[");
        boolean first = true;
        for (Machine.Graph<?> g : all()) {
            if (!first) b.append(",\n");
            first = false;
            b.append(g.json());
        }
        return b.append("]\n").toString();
    }
}
