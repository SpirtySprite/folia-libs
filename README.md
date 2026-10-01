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
        <version>foliagui-v1.3.0</version>
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

These figures come from automated benchmarks that you can run yourself (see [benchmarks/](benchmarks/README.md)).
They answer one question: **how much work does each library add to my server?**

### How to read the numbers

| Unit | Meaning |
|---|---|
| 1 ms (millisecond) | one thousandth of a second |
| 1 µs (microsecond) | one thousandth of a millisecond |
| 1 ns (nanosecond) | one thousandth of a microsecond |

A Minecraft server has **50 ms per tick**. If everything it does in a tick takes longer than that, the server lags.
So a library step that takes 0.01 ms is irrelevant, and one that takes 50 ms is a problem.

**Where the numbers come from:** runs on GitHub's shared 4-CPU test machine, in quick mode (the FoliaNPC figures were measured again after its lookup was improved; details in
[`benchmarks/results/`](benchmarks/results/)). Your server will be faster or slower, so read the numbers as
*"about this much, and this is what makes it grow"*, not as promises. The benchmarks use stand-ins for the game
server, so they measure each library's own work. They do not include sending packets over the network.

### The short version

| Library | What to know |
|---|---|
| **FoliaBoard** | Cheap. Changing a scoreboard line costs a fraction of a microsecond. The slowest step is turning text with colours into formatted text (5–9 µs), and the library remembers the result, so repeats take about 2 ns. |
| **FoliaGUI** | Cheap. Redrawing a menu that has not changed costs about 0.2 µs. Changing one item costs about 7 µs. Turning a page takes about 0.4 ms, and it does **not** get slower when the menu holds more entries. |
| **FoliaNPC** | Cost grows with the number of NPCs times the players **near** them. Even 10,000 NPCs with 500 players spread over a large world take about 3 ms per pass. The worst case is many players crowded around many NPCs: about 20 ms for 10,000 NPCs and 500 players in the same small area. |
| **folia-commons** | Negligible. Every call is under 1 µs. |

### FoliaNPC: how many NPCs can I have?

Every 2 ticks (100 ms) FoliaNPC checks which players should see each NPC. For every NPC it only looks at the
players within that NPC's view distance (48 blocks unless you change it): the players of a world are sorted into
32 block squares once per pass, and each NPC only reads the squares around it. The cost is therefore about
`NPCs x players near them`, not `NPCs x all players`. The pass runs on one thread: the main thread on Paper, the
global region thread on Folia.

Time for one pass, with players spread out in a large world (lower is better):

| NPCs | 10 players online | 100 players online | 500 players online |
|---:|---:|---:|---:|
| 100 | 0.01 ms | 0.01 ms | 0.03 ms |
| 1,000 | 0.08 ms | 0.07 ms | 0.2 ms |
| 10,000 | 0.9 ms | 1.7 ms | 3.1 ms |

How to judge a cell, as a share of the 100 ms between passes: under 5% is fine, 5–25% is noticeable, and over 25%
is too slow. Every cell above is fine. Before this lookup existed, 10,000 NPCs with 500 players took 47.8 ms.

**The worst case is a crowd.** When every player is close to every NPC, each NPC really does have to look at every
player, and nothing can be skipped. The same table for players packed into a small 400 block world:

| NPCs | 10 players online | 100 players online | 500 players online |
|---:|---:|---:|---:|
| 100 | 0.01 ms | 0.04 ms | 0.2 ms |
| 1,000 | 0.09 ms | 0.4 ms | 1.6 ms |
| 10,000 | 1.0 ms | 4.7 ms (noisy) | 19.9 ms (noisy, noticeable) |

- **Moving players cost a little more.** With a tenth of the players moving each pass, 10,000 NPCs with 500 players
  takes 3.4 ms spread out and 27.9 ms crowded.
- **Few players costs the same as before.** With 16 players or fewer in a world the library just checks all of
  them, which is cheaper than sorting them.

Other FoliaNPC work, for comparison:

| What | Time |
|---|---:|
| Find a walking route, 16 blocks | 0.04 ms |
| Find a walking route, 32 blocks | 0.2 ms |
| Find a walking route, 64 blocks | 0.9–1.2 ms |
| Give up on a route that cannot be found | 2.3–3.1 ms (capped by the search limit) |
| Save one NPC to a plain map | 0.55 µs |
| Load one NPC from a plain map | 0.81 µs |

Saving all 10,000 NPCs takes about 5.5 ms and loading them about 8 ms. Route times count only the search;
reading blocks from the real world is extra.

### FoliaBoard: scoreboards

| What you do | Time |
|---|---:|
| Set a sidebar line to the text it already has (nothing is sent) | 0.12–0.26 µs |
| Change one sidebar line | 0.14–0.27 µs |
| Replace all lines of a 15 line sidebar | 0.44 µs |
| Fill in `%player%`, `%ping%`, `%health%`, `%level%` in a line | 0.71 µs |
| Fill in one custom placeholder | 0.23 µs |
| Turn `<gray>Online: <green>128` into formatted text | 4.9 µs |
| Turn a gradient title into formatted text | 8.6 µs |
| Use text the library has already converted (cache hit) | 0.002 µs |

The packet that tells the player about a change is not counted here. As a worked example, 100 players each seeing
15 lines that are refreshed every second, even if every line were converted from scratch (about 6 µs), is roughly
9 ms of work per second, which is under 1% of one CPU core.

### FoliaGUI: menus

| What you do | Time |
|---|---:|
| Redraw a six-row menu when nothing has changed | 0.2 µs |
| Change one item and redraw | 7 µs |
| Change all 54 slots and redraw | 0.37 ms |
| Turn a page (100, 1,000 or 10,000 entries) | 0.3–0.45 ms |
| Create a plain item | 0.6 µs |
| Convert a menu title with colour codes | 0.4 µs |
| Convert a gradient title | about 8 µs (noisy) |

Redrawing a menu costs more the more items changed, not the menu's size. Turning a page costs about the same
whether the menu holds a hundred entries or ten thousand (the small differences are within the measurement noise).
Creating an item with a name and lore is slower, in the tens of microseconds, but that measurement was unstable
in this run, so treat it as a rough figure. These menu figures were measured against a simulated server, so a real
server will be somewhat slower.

### folia-commons

| What | Time |
|---|---:|
| Convert `&a` style colour codes to the modern format | 0.01–0.26 µs |
| Read a Minecraft version string | 0.54 µs |
| Build and print a diagnostics report | 0.5–2.2 µs |

### The same benchmarks on a desktop PC

The same quick run on a Windows desktop (16 logical CPUs, JDK 25), next to the GitHub runner. Full report:
[`windows-16cpu-quick.md`](benchmarks/results/windows-16cpu-quick.md). Both runs were made **before** FoliaNPC started
looking only at nearby players, so the NPC rows show the old, slower behaviour on both machines.

| What | GitHub runner (4 CPUs) | Desktop PC (16 CPUs) |
|---|---:|---:|
| NPC pass, 1,000 NPCs, 100 players (old lookup) | 0.9 ms | 0.74 ms |
| NPC pass, 10,000 NPCs, 100 players (old lookup) | 9.9 ms | 7.7 ms |
| NPC pass, 10,000 NPCs, 500 players (old lookup) | 47.8 ms | 40.9 ms |
| Walking route, 64 blocks | 0.9–1.2 ms | 0.75–0.99 ms |
| Change one sidebar line (15 lines) | 0.14–0.27 µs | 0.27 µs |
| Turn a GUI page | 0.3–0.45 ms | 0.24–0.33 ms |

The two machines agree to within about 20%, so the conclusions do not depend on the hardware. More CPU cores do
not help here, because one pass runs on one thread.

### What these numbers do not cover

- **Sending packets.** Real server work for building and sending packets is not included.
- **Many threads at once.** Folia runs regions on separate threads. These figures show the work done by one thread.
- **Your hardware.** A faster machine gives smaller numbers. Compare two runs on the *same* machine.
- **Precision.** This was a short run, so single figures can be off by 10–30%. The sizes and the trends, such as cost
  growing with NPCs times players, are what to rely on. A longer run will replace these numbers.

### Run them yourself

```bash
mvn -Pbenchmarks package -DskipTests -pl benchmarks -am
benchmarks/scripts/run.sh quick      # a few minutes
benchmarks/scripts/run.sh            # the full run, about half an hour
```

Or use the **Benchmarks** workflow in the repository's Actions tab. The full result files, with every case and its
error margin, are in [`benchmarks/results/`](benchmarks/results/).

## Building

```
mvn verify
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the module layout, compatibility checks and how releases work.

## License

MIT. See [LICENSE](LICENSE).
