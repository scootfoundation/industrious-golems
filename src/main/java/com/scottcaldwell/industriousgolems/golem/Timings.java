package com.scottcaldwell.industriousgolems.golem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.scottcaldwell.industriousgolems.IndustriousGolems;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;

/**
 * How each profession's chest visits and pick-ups are timed against its clips, read from
 * {@code data/industriousgolems/golem_timings/<profession>.json} so they can be tuned with {@code /reload} instead of a rebuild.
 * Times are seconds into the clip; a negative time means the event does not happen in that clip.
 */
public final class Timings extends SimplePreparableReloadListener<Map<String, Anim.Chest>> {
    private static volatile Map<String, Anim.Chest> chests = Map.of();
    private static volatile Map<String, Anim.Pick> picks = Map.of();

    public static Anim.Chest chest(String profession) {
        Anim.Chest c = chests.get(profession);
        return c != null ? c : chests.get("lumberjack");
    }

    public static Anim.Pick pick(String profession) {
        Anim.Pick p = picks.get(profession);
        return p != null ? p : picks.get("lumberjack");
    }

    private final Map<String, Anim.Pick> loadingPicks = new HashMap<>();

    @Override
    protected Map<String, Anim.Chest> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Anim.Chest> out = new HashMap<>();
        loadingPicks.clear();
        for (var entry : manager.listResources("golem_timings", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier id = entry.getKey();
            if (!id.getNamespace().equals(IndustriousGolems.MODID)) continue;
            String path = id.getPath();
            String profession = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject c = root.getAsJsonObject("chest"), k = root.getAsJsonObject("pick");
                out.put(profession, new Anim.Chest(str(c, "put"), f(c, "put_len"), f(c, "put_open"), f(c, "put_release"), f(c, "put_drop"), f(c, "put_close"),
                        str(c, "fetch"), f(c, "fetch_len"), f(c, "fetch_open"), f(c, "fetch_take"), f(c, "fetch_throw"), f(c, "fetch_close"),
                        d(c, "dist"), d(c, "land_x"), d(c, "land_z")));
                loadingPicks.put(profession, new Anim.Pick(str(k, "clip"), f(k, "len"), f(k, "grab"), d(k, "dist")));
            } catch (Exception e) {
                IndustriousGolems.LOGGER.error("Bad golem timings {}", id, e);
            }
        }
        return out;
    }

    @Override
    protected void apply(Map<String, Anim.Chest> prepared, ResourceManager manager, ProfilerFiller profiler) {
        if (prepared.isEmpty()) return; // keep what we had rather than leave the golems without timings
        chests = Map.copyOf(prepared);
        picks = Map.copyOf(loadingPicks);
        IndustriousGolems.LOGGER.info("Loaded golem timings now for {}", prepared.keySet());
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static float f(JsonObject o, String key) {
        return o.get(key).getAsFloat();
    }

    private static double d(JsonObject o, String key) {
        return o.get(key).getAsDouble();
    }
}
