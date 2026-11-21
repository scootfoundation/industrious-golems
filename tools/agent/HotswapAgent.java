import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loaded into the running game by tools/Hotswap.java. Redefines every already-loaded mod class whose .class file is newer than the last
 * swap. Argument: classesDir|sinceMillis|resultFile. Only method bodies can change; the JVM refuses anything else and the class is reported.
 */
public class HotswapAgent {
    public static void agentmain(String args, Instrumentation inst) throws Exception {
        String[] a = args.split("\\|");
        Path dir = Path.of(a[0]);
        long since = Long.parseLong(a[1]);
        List<String> report = new ArrayList<>();
        Map<String, Class<?>> loaded = new HashMap<>();
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c.getName().startsWith("com.scottcaldwell.") && inst.isModifiableClass(c)) loaded.put(c.getName(), c);
        }
        int done = 0, refused = 0;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                if (Files.getLastModifiedTime(f).toMillis() <= since) continue;
                String name = dir.relativize(f).toString().replace('\\', '/');
                name = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                Class<?> c = loaded.get(name);
                if (c == null) continue; // not loaded yet: it will load the new version itself
                try {
                    inst.redefineClasses(new ClassDefinition(c, Files.readAllBytes(f)));
                    done++;
                } catch (UnsupportedOperationException | LinkageError e) {
                    refused++;
                    report.add("  needs a restart: " + name + " (" + e.getMessage() + ")");
                } catch (Throwable e) {
                    refused++;
                    report.add("  failed: " + name + " (" + e + ")");
                }
            }
        }
        report.add((done == 0 && refused == 0 ? "No loaded class has changed." : "Hot-swapped " + done + " class(es)" + (refused > 0 ? ", " + refused + " need a restart" : "") + "."));
        Files.write(Path.of(a[2]), report);
    }
}
