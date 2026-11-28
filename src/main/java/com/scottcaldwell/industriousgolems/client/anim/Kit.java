package com.scottcaldwell.industriousgolems.client.anim;

import java.util.Map;

/** A golem kind's clips, and which of them play when nothing else is asked of it. */
public record Kit(String name, Map<String, Clip> clips, String walk, String walkItem, String idle, String await, boolean hasHips, double armTilt) {

    public Clip get(String id) {
        if (id == null) return null;
        // the kit's own resource file wins over its code clip of the same name, and that over a file written for every kit
        Clip file = ClipData.own(name, id);
        if (file != null) return file;
        Clip code = clips.get(id);
        return code != null ? code : ClipData.shared(id);
    }
}
