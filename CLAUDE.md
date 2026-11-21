# Industrious Golems — conventions

- **State machines are explicit.** Every task runner declares its states as an enum and its transitions as a `Machine.Graph`
  (see `behavior/Machine.java`); it moves only with `machine.to(...)`, and an undeclared transition fails the game tests.
  Top-level golem behaviour is `golem/GolemMode.java`. New runners must be added to `behavior/Graphs.java`.
- **Keep the diagram current.** After changing any graph run `tools/update-state-docs.ps1` (runs the `state_graphs` game test,
  rebuilds `docs/state-machines.html`) and republish the page as an artifact.
- Game tests: `./gradlew runGameTestServer --console=plain`.
