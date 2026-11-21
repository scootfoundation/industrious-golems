package com.scottcaldwell.industriousgolems.client.anim;

import java.util.Map;

/** A golem kind's clips, and which of them play when nothing else is asked of it. */
public record Kit(Map<String, Clip> clips, String walk, String walkItem, String idle, String await, boolean hasHips, double armTilt) {

    public Clip get(String id) {
        return id == null ? null : clips.get(id);
    }
}
