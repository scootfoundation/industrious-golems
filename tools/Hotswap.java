import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Pushes recompiled classes into the running game (or server) through its debug port, so a code change takes effect without a restart.
 * Run it through tools/hotswap.ps1, which compiles first. Only method bodies can change: adding or removing a field or method (or
 * changing a signature) is refused by the JVM, and the class is reported so you know the game needs restarting.
 *
 *   java tools/Hotswap.java [port] [classes-dir]
 */
public class Hotswap {
    public static void main(String[] args) throws Exception {
        String port = args.length > 0 ? args[0] : "5005";
        Path classes = Path.of(args.length > 1 ? args[1] : "build/classes/java/main");
        Path stamp = Path.of("build/hotswap-" + port + ".stamp");
        long since = Files.exists(stamp) ? Files.getLastModifiedTime(stamp).toMillis() : 0;

        AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.transport().name().equals("dt_socket")).findFirst().orElseThrow();
        Map<String, Connector.Argument> a = connector.defaultArguments();
        a.get("hostname").setValue("127.0.0.1");
        a.get("port").setValue(port);
        VirtualMachine vm;
        try {
            vm = connector.attach(a);
        } catch (Exception e) {
            System.out.println("Nothing is listening on port " + port + " - is the game running (gradlew runClient)?");
            System.exit(2);
            return;
        }
        try {
            if (!vm.canRedefineClasses()) {
                System.out.println("This JVM cannot redefine classes.");
                System.exit(3);
            }
            Map<ReferenceType, byte[]> changed = new HashMap<>();
            try (Stream<Path> files = Files.walk(classes)) {
                for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".class"))::iterator) {
                    if (Files.getLastModifiedTime(f).toMillis() <= since) continue;
                    String name = classes.relativize(f).toString().replace('\\', '/');
                    name = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                    List<ReferenceType> loaded = vm.classesByName(name);
                    if (loaded.isEmpty()) continue; // not loaded yet: it will load the new version itself
                    changed.put(loaded.get(0), Files.readAllBytes(f));
                }
            }
            if (changed.isEmpty()) {
                System.out.println("No loaded class has changed.");
            } else {
                int done = 0, refused = 0;
                for (var e : changed.entrySet()) {
                    try {
                        vm.redefineClasses(Map.of(e.getKey(), e.getValue()));
                        done++;
                    } catch (UnsupportedOperationException | LinkageError ex) {
                        refused++;
                        System.out.println("  needs a restart: " + e.getKey().name() + " (" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")");
                    } catch (Exception ex) {
                        refused++;
                        System.out.println("  failed: " + e.getKey().name() + " (" + ex + ")");
                    }
                }
                System.out.println("Hot-swapped " + done + " class(es)" + (refused > 0 ? ", " + refused + " need a restart" : "") + ".");
            }
            Files.writeString(stamp, "");
            Files.setLastModifiedTime(stamp, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
        } finally {
            vm.dispose();
        }
    }
}
