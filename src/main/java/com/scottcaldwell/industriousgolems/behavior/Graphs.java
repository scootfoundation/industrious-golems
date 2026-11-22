package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.farm.TendCrops;
import com.scottcaldwell.industriousgolems.golem.GolemMode;
import com.scottcaldwell.industriousgolems.herd.HerdWork;
import com.scottcaldwell.industriousgolems.lumber.FellTree;
import com.scottcaldwell.industriousgolems.lumber.PlantSapling;
import com.scottcaldwell.industriousgolems.mine.MineRock;

import java.util.List;

/** Every state machine in the mod, in the order the diagram page shows them, and the page itself. */
public final class Graphs {
    private Graphs() { }

    public static List<Machine.Graph<?>> all() {
        return List.of(GolemMode.GRAPH, ContainerInteraction.GRAPH, PickUp.GRAPH, TendCrops.GRAPH, FellTree.GRAPH, PlantSapling.GRAPH, MineRock.GRAPH, HerdWork.GRAPH);
    }

    /** The diagram page: the template with a navigation bar and one section per machine filled in. */
    public static String page(String template) {
        StringBuilder nav = new StringBuilder(), sections = new StringBuilder();
        for (Machine.Graph<?> g : all()) {
            nav.append("<a href=\"#").append(g.id).append("\">").append(esc(g.title.split(":")[0])).append("</a>");
            sections.append(section(g));
        }
        return template.replace("<!--NAV-->", nav).replace("<!--SECTIONS-->", sections);
    }

    private static <S extends Enum<S>> String section(Machine.Graph<S> g) {
        StringBuilder b = new StringBuilder();
        b.append("<section id=\"").append(g.id).append("\"><h2>").append(esc(g.title)).append("</h2><p class=\"s\">").append(esc(g.summary)).append("</p>\n");
        b.append("<div class=\"dia\"><pre class=\"mermaid\">").append(esc(mermaid(g))).append("</pre></div>\n");
        b.append("<h3>States</h3><div class=\"tw\"><table>");
        for (S s : g.states()) {
            String note = g.note(s).replaceFirst("^\\(end\\)\\s*", "");
            b.append("<tr><td class=\"n\"><code>").append(s.name()).append("</code>").append(s.name().equals(g.start()) ? " (start)" : "").append("</td><td>")
                    .append(esc(note)).append("</td></tr>");
        }
        b.append("</table></div><h3>Transitions</h3><div class=\"tw\"><table><tr><th>From</th><th>To</th><th>Trigger</th></tr>");
        for (Machine.Edge e : g.shownEdges()) {
            String from = e.from().startsWith("*") ? e.from().substring(1) : e.from();
            b.append("<tr><td class=\"n\">").append(e.from().startsWith("*") ? esc(from) : "<code>" + from + "</code>").append("</td><td class=\"n\"><code>")
                    .append(e.to()).append("</code></td><td>").append(esc(e.when())).append("</td></tr>");
        }
        return b.append("</table></div></section>\n").toString();
    }

    /** A flowchart with every transition labelled by its trigger. */
    private static <S extends Enum<S>> String mermaid(Machine.Graph<S> g) {
        StringBuilder b = new StringBuilder("%%{init: {\"flowchart\": {\"curve\": \"basis\", \"nodeSpacing\": 60, \"rankSpacing\": 90, \"padding\": 12}}}%%\nflowchart TD\n");
        for (S s : g.states()) {
            String cls = s.name().equals(g.start()) ? ":::first" : g.note(s).startsWith("(end)") ? ":::last" : "";
            b.append("  ").append(s.name()).append("([").append(s.name()).append("])").append(cls).append('\n');
        }
        int any = 0;
        for (Machine.Edge e : g.shownEdges()) {
            String label = wrap(e.when(), 34).replace("\"", "#quot;");
            if (e.from().startsWith("*")) {
                String id = "ANY" + any++;
                b.append("  ").append(id).append("{{\"").append(e.from().substring(1)).append("\"}}:::wild\n");
                b.append("  ").append(id).append(" -.->|\"").append(label).append("\"| ").append(e.to()).append('\n');
            } else {
                b.append("  ").append(e.from()).append(" -->|\"").append(label).append("\"| ").append(e.to()).append('\n');
            }
        }
        b.append("  classDef first fill:#fff3d6,stroke:#c58b12,color:#1d1d1b,stroke-width:2px\n");
        b.append("  classDef last fill:#ececec,stroke:#8a8a8a,color:#1d1d1b\n");
        b.append("  classDef wild fill:#fde8e0,stroke:#c2410c,color:#1d1d1b,stroke-dasharray:4 3\n");
        return b.toString();
    }

    private static String wrap(String text, int width) {
        StringBuilder out = new StringBuilder();
        int line = 0;
        for (String word : text.split(" ")) {
            if (line + word.length() > width && line > 0) { out.append("<br/>"); line = 0; }
            else if (line > 0) { out.append(' '); line++; }
            out.append(word);
            line += word.length();
        }
        return out.toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
