# Industrious Golems — conventions

- **State machines are explicit.** Every task runner declares its states as an enum and its transitions as a `Machine.Graph`
  (see `behavior/Machine.java`); it moves only with `machine.to(...)`, and an undeclared transition fails the game tests.
  Top-level golem behaviour is `golem/GolemMode.java`. New runners must be added to `behavior/Graphs.java`.
- **Keep the diagram current.** After changing any graph run `tools/update-state-docs.ps1` (runs the `state_graphs` game test,
  rebuilds `docs/state-machines.html`) and republish the page as an artifact.
- Game tests: `./gradlew runGameTestServer --console=plain`.
- **Dev loop without restarts.** Run the client (`run-client.bat`), then after an edit run
  `powershell -ExecutionPolicy Bypass -File tools/hotswap.ps1` (compiles and redefines changed classes in the running game through
  by attaching to the JVM; method bodies only, a new field/method is reported as needing a restart; `-Server` targets the dev server).
  Prefer data over code for tunables: animation clips live in `assets/industriousgolems/animations/<kit|all>/<clip>.json`
  (format in `client/anim/ClipData.java`; F3+T reloads them) and chest/pick-up timings in
  `data/industriousgolems/golem_timings/<profession>.json` (`/reload`). A JSON clip replaces a code clip of the same name.
