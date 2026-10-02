# folia-libs

Libraries for plugins that run on [Paper](https://papermc.io) and [Folia](https://papermc.io/software/folia).
Each one is safe to call from any thread and works the same on both servers.

| Library | What it does | Docs |
|---|---|---|
| **FoliaBoard** | Packet-level scoreboards: sidebars, nametags, tab lists, below-name numbers, boss bars | [foliaboard/](foliaboard/README.md) |
| **FoliaGUI** | Inventory menus: paginated, searchable, anvil, sign, merchant, confirmations | [foliagui/](foliagui/README.md) |
| **FoliaNPC** | Packet-based NPCs with skins, nametags, equipment, movement and click actions | [folianpc/](folianpc/README.md) |
| **folia-commons** | The code they share: scheduling, text, version detection | [folia-commons/](folia-commons/README.md) |

None of them ships a `plugin.yml`. Depend on the ones you need and shade them into your plugin (relocate the
packages to your own namespace), or run them inside a library plugin of your own.

## Installing

The libraries are published through [JitPack](https://jitpack.io). Use the group
`com.github.SpirtySprite.folia-libs`, the module's artifact id, and the release tag as the version:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.SpirtySprite.folia-libs</groupId>
        <artifactId>foliagui-api</artifactId>
        <version>foliagui-v1.3.1</version>
    </dependency>
</dependencies>
```

| Module | Artifact id |
|---|---|
| FoliaBoard | `foliaboard-core` |
| FoliaGUI | `foliagui-api` |
| FoliaNPC | `folianpc` |
| folia-commons | `folia-commons` |

JitPack builds normalize the module groups to the parent Maven group before installation so
published dependencies point to the Commons artifact under the same JitPack version. This runs
only in JitPack's disposable checkout through `scripts/jitpack-prepare.sh`. Normal Maven builds
retain the libraries' original group IDs. Do not run the preparation script in a working checkout
whose original Maven coordinates you want to preserve. Test preparation with
`python3 scripts/test-jitpack-prepare.py`; it uses a temporary checkout and verifies that other
POM metadata and the source files remain unchanged.

Each library's README has the details, including the `plugin.yml` setting Folia needs
(`folia-supported: true`).

## Compatibility

Every push is tested by starting real servers with a plugin that contains all four libraries (shaded and
relocated, like your own plugin would). On 1.21.8 and 1.21.11 a headless client also joins, and the tests check
that it receives the sidebar, the NPC and the menu, and that its clicks on the menu and the NPC reach the library.

| Server | Minecraft | Tested |
|---|---|---|
| Paper | 1.20.6, 1.21.4, 1.21.8, 1.21.11 | yes |
| Folia | 1.20.6, 1.21.4, 1.21.8, 1.21.11 | yes |

Some features need a newer server than the rest of the library. A library reports them in its diagnostics
(`diagnose()`) and keeps working without them:

| Feature | Needs | Library |
|---|---|---|
| Tab list ordering | Paper 1.21.2 or newer | FoliaBoard |
| Anvil text input | Paper 1.21 or newer | FoliaGUI |
| Sign text input | a Paper version with `UncheckedSignChangeEvent` (present on 1.21.8, missing on 1.21.4) | FoliaGUI |

Versions between the tested ones probably work but are not tested. Other Minecraft versions are not supported.

## Performance

These figures come from the [full Namespace run on October 2, 2026](benchmarks/results/namespace-full-2026-10-02.md),
measuring all 92 existing cases on commit `ba38e63`. The runner used Linux x86_64 with eight logical CPUs,
JDK 21.0.12.1 and JMH 1.37. Each case used two forks, three one-second warmup iterations and five one-second
measurement iterations per fork, with GC allocation profiling.

The [full report](benchmarks/results/namespace-full-2026-10-02.md) includes error margins and allocation per call.
[Raw JSON](benchmarks/results/namespace-full-2026-10-02.json) and
[run provenance](benchmarks/results/namespace-full-2026-10-02.metadata.json) are published alongside it.
Historical runs remain in [`benchmarks/results/`](benchmarks/results/); differences in hardware and code
prevent using them as a direct before/after comparison.

### How to read the numbers

| Unit | Meaning |
|---|---|
| 1 ms (millisecond) | one thousandth of a second |
| 1 µs (microsecond) | one thousandth of a millisecond |
| 1 ns (nanosecond) | one thousandth of a microsecond |

Tables below show rounded average time per call, with lower values indicating less work. A Minecraft server
has a 50 ms tick budget. The benchmarks use stand-ins for the game server and measure work on one thread;
real packet construction, network sending and live-world access add costs that these figures do not cover.
Your server's results depend on its hardware and workload.

### The short version

| Library | Measured work |
|---|---|
| **FoliaBoard** | Changing one sidebar line takes 0.094 to 0.187 µs. Parsing simple or gradient text takes about 3.5 to 6.6 µs; a cache hit takes about 4.8 ns. |
| **FoliaGUI** | An unchanged six-row menu update takes 0.331 ms, changing one slot takes 0.364 ms, and the page benchmarks range from 0.354 to 0.523 ms. |
| **FoliaNPC** | A steady visibility pass for 10,000 NPCs and 500 players takes 6.839 ms spread out and 48.059 ms crowded. Nearby player density matters. |
| **folia-commons** | Legacy colour conversion takes 0.006 to 0.178 µs, version parsing takes 0.104 µs, and diagnostics formatting takes 0.481 to 2.449 µs. |

### FoliaNPC: how many NPCs can I have?

Every two ticks (100 ms), FoliaNPC checks which players should see each NPC. For every NPC it looks at players
within that NPC's view distance (48 blocks unless changed). Players are indexed in 32-block squares once per
pass, and each NPC reads nearby squares. With 16 players or fewer, the lookup scans all players directly.
The pass runs on one thread: the main thread on Paper, the global region thread on Folia.

Time for one steady-state pass with players spread over a 4,000-block-wide world:

| NPCs | 10 players online | 100 players online | 500 players online |
|---:|---:|---:|---:|
| 100 | 0.029 ms | 0.014 ms | 0.056 ms |
| 1,000 | 0.296 ms | 0.150 ms | 0.385 ms |
| 10,000 | 4.956 ms | 3.768 ms | 6.839 ms |

The same cases with players crowded into a 400-block-wide world:

| NPCs | 10 players online | 100 players online | 500 players online |
|---:|---:|---:|---:|
| 100 | 0.029 ms | 0.079 ms | 0.348 ms |
| 1,000 | 0.346 ms | 0.747 ms | 3.417 ms |
| 10,000 | 5.859 ms | 12.306 ms | 48.059 ms |

The largest crowded case uses about 48% of the 100 ms between passes and takes about 48 ms on the executing
thread. It leaves little of a 50 ms tick budget for other work. Several 10,000-NPC cases have substantial
error margins; consult the full report before sizing a server around one average.

With a tenth of players moving each pass, the 10,000-NPC, 500-player cases measure
5.149 ms spread out and
44.059 ms crowded.
This run does not establish that movement is cheaper; the steady and moving cases are separate measurements.

Other FoliaNPC work:

| What | Time |
|---|---:|
| Find a walking route, 16 blocks | 0.031 to 0.045 ms |
| Find a walking route, 32 blocks | 0.152 to 0.225 ms |
| Find a walking route, 64 blocks | 0.566 to 0.670 ms |
| Give up on an unreachable route | 1.490 to 2.189 ms |
| Save one NPC to a plain map | 0.350 µs |
| Load one NPC from a plain map | 1.651 µs |

Route ranges cover the cases with 0% and 20% obstacles and count only the search. Reading blocks from the real
world is extra. Serialization figures cover conversion to and from a map, without file or database I/O.

### FoliaBoard: scoreboards

| What you do | Time |
|---|---:|
| Set a sidebar line to the text it already has | 0.080 to 0.178 µs |
| Change one sidebar line | 0.094 to 0.187 µs |
| Replace all lines of a 15-line sidebar | 0.294 µs |
| Fill in `%player%`, `%ping%`, `%health%`, `%level%` in a line | 0.442 µs |
| Fill in one custom placeholder | 0.137 to 0.172 µs |
| Turn `<gray>Online: <green>128` into formatted text | 3.545 µs |
| Turn a gradient title into formatted text | 6.640 µs |
| Use text the library has already converted | 4.8 ns |

Sidebar ranges cover five-line and fifteen-line sidebars. Packet construction and transmission are excluded.
Managed displays and nametag compositions do not yet have dedicated cases in this benchmark suite.

### FoliaGUI: menus

| What you do | Time |
|---|---:|
| Update a six-row menu when nothing has changed | 0.331 ms |
| Change one item and update | 0.364 ms |
| Change all 54 slots and update | 0.422 ms |
| Jump to the next page (100, 1,000 or 10,000 entries) | 0.354 to 0.456 ms |
| Step forward or wrap to the first page | 0.367 to 0.523 ms |
| Create a plain item | 0.415 µs |
| Create an item with a name and lore | 12.935 µs |
| Create an item with a name, lore and identity | 21.500 µs |
| Convert a menu title with colour codes | 0.185 µs |
| Convert a gradient title | 4.517 µs |

These figures use MockBukkit and include the simulated inventory work. In this run, unchanged and one-slot
updates remain close to a full update in cost; the measurements do not support treating them as negligible.
Page timings vary with the case and entry count; the full report lists each case and its error margin.

### folia-commons

| What | Time |
|---|---:|
| Convert plain, `&a` or hex-colour text to the modern format | 0.006 to 0.178 µs |
| Strip legacy colour codes | 0.085 µs |
| Read a Minecraft version string | 0.104 µs |
| Check an already-parsed Minecraft version | 0.575 ns |
| Build and print diagnostics (10 or 50 features) | 0.481 to 2.449 µs |

### What these numbers do not cover

- **Sending packets.** Real server work for building and sending packets is excluded.
- **Many threads at once.** These measurements show work done by one thread, without measuring region contention.
- **Display and nametag rendering.** Those APIs have no dedicated benchmark cases in this suite.
- **Your hardware.** Compare repeated runs on the same configured machine before claiming a speedup or regression.
- **Exact predictions.** Tables show rounded averages. The full report includes 99.9% confidence intervals,
  including substantial uncertainty in some NPC and text-conversion cases.

### Run them yourself

```bash
mvn -Pbenchmarks package -DskipTests -pl benchmarks -am
benchmarks/scripts/run.sh quick      # a few minutes
benchmarks/scripts/run.sh            # the full run, about half an hour
```

Or use the **Benchmarks** workflow in the repository's Actions tab with mode `full`. It runs on Namespace profile
`namespace-profile-libs` and uploads the raw JSON and report. Every case and its error margin is published in
[`benchmarks/results/`](benchmarks/results/).

## Building

```
mvn verify
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the module layout, compatibility checks and how releases work.

## License

MIT. See [LICENSE](LICENSE).
