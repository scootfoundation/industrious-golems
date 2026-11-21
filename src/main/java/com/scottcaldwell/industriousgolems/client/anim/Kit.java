package com.scottcaldwell.industriousgolems.client.anim;

import java.util.Map;

/** A golem kind's clips, and which of them play when nothing else is asked of it. */
public record Kit(String name, Map<String, Clip> clips, String walk, String walkItem, String idle, String await, boolean hasHips, double armTilt) {

    public Clip get(String id) {
        if (id == null) return null;
        Clip file = ClipData.find(name, id); // a clip kept as a resource file wins over the code one of the same name
        return file != null ? file : clips.get(id);
    }
}
