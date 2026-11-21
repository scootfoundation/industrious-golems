import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Pushes recompiled classes into the running game (or dev server) without a restart: attaches to the JVM, loads
 * tools/agent/HotswapAgent into it, and the agent redefines the changed classes. No startup flags are needed. Run it through
 * tools/hotswap.ps1, which compiles and builds the agent jar first.
 *
 *   java tools/Hotswap.java client|server agent.jar classes-dir
 */
public class Hotswap {
    public static void main(String[] args) throws Exception {
        String which = args.length > 0 ? args[0] : "client";
        Path jar = Path.of(args.length > 1 ? args[1] : "build/hotswap/agent.jar").toAbsolutePath();
        Path classes = Path.of(args.length > 2 ? args[2] : "build/classes/java/main").toAbsolutePath();
        Path stamp = Path.of("build/hotswap-" + which + ".stamp").toAbsolutePath();
        Path result = Path.of("build/hotswap-result.txt").toAbsolutePath();
        long since = Files.exists(stamp) ? Files.getLastModifiedTime(stamp).toMillis() : 0;

        List<VirtualMachineDescriptor> games = VirtualMachine.list().stream()
                .filter(d -> d.displayName().startsWith("net.neoforged.fml.startup." + (which.equals("server") ? "Server" : "Client")))
                .toList();
        if (games.size() != 1) {
            System.out.println(games.isEmpty() ? "No running dev " + which + " found (start it with run-" + which + ".bat)."
                    : "More than one dev " + which + " is running; close one.");
            System.exit(2);
        }
        Files.deleteIfExists(result);
        VirtualMachine vm = VirtualMachine.attach(games.get(0));
        try {
            vm.loadAgent(jar.toString(), classes + "|" + since + "|" + result);
        } finally {
            vm.detach();
        }
        if (Files.exists(result)) Files.readAllLines(result).forEach(System.out::println);
        else System.out.println("The agent reported nothing; check the game's log.");
        Files.writeString(stamp, "");
        Files.setLastModifiedTime(stamp, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
    }
}
