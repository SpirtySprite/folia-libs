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
        <version>foliagui-v1.0.0</version>
    </dependency>
</dependencies>
```

| Module | Artifact id |
|---|---|
| FoliaBoard | `foliaboard-core` |
| FoliaGUI | `foliagui-api` |
| FoliaNPC | `folianpc` |
| folia-commons | `folia-commons` |

Each library's README has the details, including the `plugin.yml` setting Folia needs
(`folia-supported: true`).

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

**Where the numbers come from:** one run on GitHub's shared 4-CPU test machine, in quick mode (details in
[`benchmarks/results/`](benchmarks/results/)). Your server will be faster or slower, so read the numbers as
*"about this much, and this is what makes it grow"*, not as promises. The benchmarks use stand-ins for the game
server, so they measure each library's own work. They do not include sending packets over the network.

### The short version

| Library | What to know |
|---|---|
| **FoliaBoard** | Cheap. Changing a scoreboard line costs a fraction of a microsecond. The slowest step is turning text with colours into formatted text (5–9 µs), and the library remembers the result, so repeats take about 2 ns. |
| **FoliaGUI** | Cheap. Redrawing a menu that has not changed costs about 0.2 µs. Changing one item costs about 7 µs. Turning a page takes about 0.4 ms, and it does **not** get slower when the menu holds more entries. |
| **FoliaNPC** | Cost grows with **(number of NPCs) x (players online)**. Up to about 1,000 NPCs with 100 players is well under 1 ms. Ten thousand NPCs with 500 players is about 48 ms, which is too slow. This is the one to size carefully. |
| **folia-commons** | Negligible. Every call is under 1 µs. |

### FoliaNPC: how many NPCs can I have?

Every 2 ticks (100 ms) FoliaNPC checks, for every NPC, which players should see it. Each check takes about
**10 ns**, and it is done for every NPC against every online player in the same world, even NPCs nobody is near. That makes
one pass take about `NPCs x players x 10 ns` (players counted in the NPC's world). The pass runs on one thread: the main thread on Paper, the global region thread on Folia.

Time for one pass, with players spread out in a large world (lower is better):

| NPCs | 10 players online | 100 players online | 500 players online |
|---:|---:|---:|---:|
| 100 | 0.01 ms | 0.09 ms | 0.5 ms |
| 1,000 | 0.13 ms | 0.9 ms | 5.1 ms (noticeable) |
| 10,000 | 1.3 ms | 9.9 ms (noticeable) | **47.8 ms (too slow)** |

How to judge a cell, as a share of the 100 ms between passes: under 5% is fine, 5–25% is noticeable, and over 25%
is too slow. On Paper, a 48 ms pass would take almost a whole tick by itself.

- **Crowded worlds cost a bit more.** When every player can see many NPCs (a 400 block world instead of 4,000),
  the same table is roughly 20–35% slower; 10,000 NPCs with 500 players takes 64 ms.
- **Moving players cost about the same.** With a tenth of the players moving each pass, 10,000 NPCs with 500 players takes 54 ms.
- **Planned improvement:** looking up only the NPCs near each player instead of all of them, so cost follows the NPCs
  that are actually in view.

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
