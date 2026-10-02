# Benchmarks

JMH benchmarks for folia-commons, FoliaBoard, FoliaGUI and FoliaNPC. They answer two questions:

1. **What does a call cost?** Sidebar updates, menu redraws, placeholder resolution, pathfinding, saving NPCs.
2. **Does cost stay flat when it should?** Unchanged content should cost (almost) nothing, and a menu page
   should cost the same whether the menu holds a hundred entries or ten thousand.

The latest numbers are in [`results/`](results/). Read [What these numbers do not tell you](#what-these-numbers-do-not-tell-you)
before quoting any of them.

The latest full suite report is [Namespace, October 2, 2026](results/namespace-full-2026-10-02.md),
with [raw JMH data](results/namespace-full-2026-10-02.json) and
[run provenance](results/namespace-full-2026-10-02.metadata.json).

## Running

From the root of the repository. The benchmarks are behind a Maven profile, so a normal `mvn verify` does
not build them.

```bash
mvn -Pbenchmarks package -DskipTests -pl benchmarks -am   # builds benchmarks/target/benchmarks.jar
benchmarks/scripts/run.sh              # full run: 2 forks, 3 warmup + 5 measured iterations of 1 s each
benchmarks/scripts/run.sh quick        # 1 fork, shorter iterations; a sanity check, not a measurement
benchmarks/scripts/run.sh smoke        # one 200 ms iteration of every case; proves they all still run
```

The script writes `benchmarks/results/<label>.json` (raw JMH output) and `<label>.md` (the report).

| Variable | Meaning |
|---|---|
| `LABEL` | Output name. Default: `<date>-<hostname>` |
| `FILTER` | Regular expression selecting benchmarks, for example `FILTER=NpcTick` |
| `PROFILE=1` | Adds JMH's GC profiler, which reports bytes allocated per operation |
| `EXTRA` | Extra JMH arguments, for example `EXTRA="-p npcs=1000 -p players=100"` |

You can also run the jar directly: `java -jar benchmarks/target/benchmarks.jar -h` lists every JMH option.

**Run them on a quiet machine.** Close other programs, do not build anything at the same time, and do not
compare numbers from different machines.

## What is measured

| Library | Benchmark | What it measures |
|---|---|---|
| folia-commons | `LegacyBenchmark` | Legacy colour codes to MiniMessage: plain text, codes, hex |
| | `ServerVersionBenchmark` | Parsing and comparing Minecraft versions |
| | `DiagnosticsBenchmark` | Building and printing a report |
| FoliaBoard | `SidebarDiffBenchmark` | Setting a sidebar line to the same value, changing one line, and replacing all lines, with 5 and 15 lines. The packet adapter counts packets and the setup fails if unchanged content sends any |
| | `PlaceholdersBenchmark` | Resolving `%placeholders%`: none, built-in, custom, cached, and through to a parsed component |
| | `TextBenchmark` | MiniMessage and legacy parsing, and the parse cache hit and miss |
| FoliaGUI | `GuiItemBenchmark` | Building a menu item, with and without the identity tag |
| | `GuiUpdateBenchmark` | Redrawing a six-row menu: nothing changed, one slot changed, all slots changed |
| | `PaginatedGuiBenchmark` | Turning pages in a menu of 100, 1,000 and 10,000 entries |
| | `GuiTextBenchmark` | Titles and templates |
| FoliaNPC | `NpcTickBenchmark` | One visibility pass over every NPC, for 100 to 10,000 NPCs and 10 to 500 players, in a sparse and a dense world, with players standing still and with a tenth of them moving |
| | `PathfindingBenchmark` | A* route length 16, 32 and 64 blocks, on an empty floor and with 20% obstacles |
| | `NpcDataBenchmark` | Building, serializing and deserializing an NPC snapshot |

`NpcTickBenchmark` is the one behind the README's claim about large NPC counts. The report adds a table of
how much of the 100 ms between passes one pass uses.

## How the benchmarks are built

- **No server.** Libraries are driven through their real classes with stand-ins for the server. FoliaGUI
  runs on MockBukkit; the others use small hand-written stubs in `support/Stubs.java`. Mockito is not used,
  because a mock records every call, which costs more than the code being measured.
- **Counting back ends.** Where the library would send packets, a counting implementation takes their
  place, so the benchmark measures the library's work and not the network.
- **Guards.** A benchmark's setup checks that it measures what its name says, for example that unchanged
  sidebar lines send no packets. A benchmark that stops doing real work fails instead of getting faster.
- **Deterministic inputs.** Positions come from a fixed seed, and results are returned so the JIT cannot
  remove the work.

## What these numbers do not tell you

- **They are not server timings.** Real packet construction and sending (reflection into the server's
  classes, Netty) is not included. The NPC numbers cover deciding who sees what, not the cost of the
  spawn packets that follow.
- **They cover the cases listed above.** Managed displays and nametag compositions do not have
  dedicated benchmark cases in this suite; their rendering or transport cost cannot be inferred
  from the sidebar figures.
- **They are single-threaded.** Folia spreads work across region threads. These numbers say how much work
  one thread does, which is the useful figure for a budget, but they say nothing about contention.
- **Hardware varies.** A result from a laptop and a result from a CI runner cannot be compared. Compare two
  runs from the same machine, before and after a change.
- **MockBukkit is not Paper.** FoliaGUI's inventory work runs against a mock, so absolute numbers there are
  a lower bound on real cost.

## Adding a benchmark

1. Put a class in `src/main/java/net/foliabench/<library>/`. Copy the annotations from an existing one
   (`@BenchmarkMode`, `@Warmup`, `@Measurement`, `@Fork`, `@State`).
2. Return the result, or pass it to a `Blackhole`.
3. Use `Stubs` for players and plugins. Do not use Mockito.
4. If it counts something (packets, shows), check the count in `@Setup` and throw if it is wrong.
5. Run `benchmarks/scripts/run.sh smoke` to check that it runs.

## Continuous integration

CI runs the `smoke` mode on every pull request so the benchmarks cannot rot. The **Benchmarks** workflow
can be started by hand from the Actions tab; it runs the full suite and uploads the JSON and the report.
The workflow uses Namespace runner profile `namespace-profile-libs`. Runner results remain
environment-specific; compare repeated measurements on the same configured runner rather than
comparing them directly with the older GitHub-hosted or desktop results.
