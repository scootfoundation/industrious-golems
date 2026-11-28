package com.scottcaldwell.industriousgolems.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scottcaldwell.industriousgolems.IndustriousGolems;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clips kept as resource files, so an edit shows up on {@code F3+T} without a rebuild. A file is
 * {@code assets/industriousgolems/animations/<kit>/<clip>.json}, where {@code <kit>} is {@code hay}, {@code mushroom},
 * {@code terracotta}, {@code stone} or {@code all} (every kind). It describes the same additive pose the code clips build:
 *
 * <pre>
 * { "length": 9.0, "loop": true, "move": 0, "hold": "front", "hold_from": 0, "hold_until": 1.0,
 *   "channels": {
 *     "head.ry": { "keys": [[0, 0], [0.7, 0.85, "o"], [1.9, 0.85]], "sin": [{ "amp": 0.04, "w": 0.9, "phase": 0 }] },
 *     "arms.rx": { "keys": [[0, 0], [1, -1.5]] }
 *   } }
 * </pre>
 *
 * A channel is {@code part.prop} (parts: body head rarm larm rleg lleg hips cap rfore lfore; props: rx ry rz x y z sx sy sz, in radians,
 * model pixels and scale changes), {@code arms.rx} / {@code arms.rz} (both arms, as {@link Pose#arms}), or {@code off.x/y/z/ry} /
 * {@code scale} for the whole golem. Its value is the keyed curve (easing {@code "s"} smooth, {@code "o"} out, {@code "i"} in, into that key;
 * held at the first and last key) plus any sines ({@code w} in radians per second). A kit's code clip of the same name is replaced.
 */
public final class ClipData extends SimplePreparableReloadListener<Map<String, Clip>> {
    private static volatile Map<String, Clip> clips = Map.of();

    /** The file clip for a kit (or for every kit), or null. */
    public static Clip find(String kit, String id) {
        Clip c = own(kit, id);
        return c != null ? c : shared(id);
    }

    /** The kit's own file clip, or null. */
    public static Clip own(String kit, String id) {
        return clips.get(kit + "/" + id);
    }

    /** The file clip for every kit, or null. */
    public static Clip shared(String id) {
        return clips.get("all/" + id);
    }

    /**
     * A stand-in for a file clip, for use inside a code sequence (which is built once): each frame it plays whatever the file holds now.
     * {@code len} is how long the sequence gives it.
     */
    public static Clip ref(String kit, String id, float len) {
        return new Clip(id, len, false, 0, (P, t, c) -> {
            Clip real = find(kit, id);
            if (real != null) real.run.run(P, t, c);
        });
    }

    @Override
    protected Map<String, Clip> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Clip> out = new HashMap<>();
        for (var entry : manager.listResources("animations", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier id = entry.getKey();
            if (!id.getNamespace().equals(IndustriousGolems.MODID)) continue;
            String[] parts = id.getPath().substring("animations/".length(), id.getPath().length() - ".json".length()).split("/");
            if (parts.length != 2) continue;
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                out.put(parts[0] + "/" + parts[1], parse(parts[1], JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception e) {
                IndustriousGolems.LOGGER.error("Bad animation {}", id, e);
            }
        }
        return out;
    }

    @Override
    protected void apply(Map<String, Clip> prepared, ResourceManager manager, ProfilerFiller profiler) {
        clips = Map.copyOf(prepared);
        IndustriousGolems.LOGGER.info("Loaded {} animation clip files", prepared.size());
    }

    // ---- parsing ----------------------------------------------------------------------------------------------

    private record Sine(double amp, double w, double phase) { }

    private record Channel(Setter set, double[][] keys, List<Sine> sines) {
        void apply(Pose P, double t) {
            double v = keys.length == 0 ? 0 : Pose.kf(t, keys);
            for (Sine s : sines) v += s.amp * Math.sin(t * s.w + s.phase);
            set.add(P, v);
        }
    }

    @FunctionalInterface
    private interface Setter {
        void add(Pose P, double v);
    }

    private static Clip parse(String id, JsonObject o) {
        float len = o.get("length").getAsFloat();
        boolean loop = o.has("loop") && o.get("loop").getAsBoolean();
        float move = o.has("move") ? o.get("move").getAsFloat() : 0;
        List<Channel> channels = new ArrayList<>();
        for (var e : o.getAsJsonObject("channels").entrySet()) {
            JsonObject c = e.getValue().getAsJsonObject();
            List<double[]> keys = new ArrayList<>();
            if (c.has("keys")) {
                for (JsonElement k : c.getAsJsonArray("keys")) {
                    JsonArray a = k.getAsJsonArray();
                    char ease = a.size() > 2 ? a.get(2).getAsString().charAt(0) : 's';
                    keys.add(new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), ease});
                }
            }
            List<Sine> sines = new ArrayList<>();
            if (c.has("sin")) {
                for (JsonElement s : c.getAsJsonArray("sin")) {
                    JsonObject so = s.getAsJsonObject();
                    sines.add(new Sine(so.get("amp").getAsDouble(), so.get("w").getAsDouble(), so.has("phase") ? so.get("phase").getAsDouble() : 0));
                }
            }
            channels.add(new Channel(setter(e.getKey()), keys.toArray(new double[0][]), sines));
        }
        Clip clip = new Clip(id, len, loop, move, (P, t, ctx) -> {
            for (Channel c : channels) c.apply(P, t);
        });
        if (o.has("hold")) {
            clip.hold(o.get("hold").getAsString(), o.has("hold_from") ? o.get("hold_from").getAsFloat() : 0,
                    o.has("hold_until") ? o.get("hold_until").getAsFloat() : 1e9F);
        }
        return clip;
    }

    private static Setter setter(String name) {
        String[] p = name.split("\\.");
        if (p.length != 2) throw new IllegalArgumentException("channel " + name);
        String part = p[0], prop = p[1];
        switch (part) {
            case "arms" -> {
                return prop.equals("rx") ? (P, v) -> P.arms(v, 0) : (P, v) -> P.arms(0, v);
            }
            case "off" -> {
                return switch (prop) {
                    case "x" -> (P, v) -> P.offX += (float) v;
                    case "y" -> (P, v) -> P.offY += (float) v;
                    case "z" -> (P, v) -> P.offZ += (float) v;
                    case "ry" -> (P, v) -> P.offRy += (float) v;
                    default -> throw new IllegalArgumentException("channel " + name);
                };
            }
            case "scale" -> {
                return (P, v) -> P.scale += (float) v;
            }
            default -> {
                Pose probe = new Pose();
                Pose.Part target = switch (part) {
                    case "body" -> probe.body;
                    case "head" -> probe.head;
                    case "rarm" -> probe.rarm;
                    case "larm" -> probe.larm;
                    case "rleg" -> probe.rleg;
                    case "lleg" -> probe.lleg;
                    case "hips" -> probe.hips;
                    case "cap" -> probe.cap;
                    case "rfore" -> probe.rfore;
                    case "lfore" -> probe.lfore;
                    default -> throw new IllegalArgumentException("channel " + name);
                };
                int index = java.util.Arrays.asList(probe.all()).indexOf(target);
                return switch (prop) {
                    case "rx" -> (P, v) -> P.all()[index].rx += (float) v;
                    case "ry" -> (P, v) -> P.all()[index].ry += (float) v;
                    case "rz" -> (P, v) -> P.all()[index].rz += (float) v;
                    case "x" -> (P, v) -> P.all()[index].x += (float) v;
                    case "y" -> (P, v) -> P.all()[index].y += (float) v;
                    case "z" -> (P, v) -> P.all()[index].z += (float) v;
                    case "sx" -> (P, v) -> P.all()[index].sx += (float) v;
                    case "sy" -> (P, v) -> P.all()[index].sy += (float) v;
                    case "sz" -> (P, v) -> P.all()[index].sz += (float) v;
                    default -> throw new IllegalArgumentException("channel " + name);
                };
            }
        }
    }
}
