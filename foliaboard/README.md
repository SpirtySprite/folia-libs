# FoliaBoard

## Managed text and item displays

`displays()` is an experimental API for virtual TextDisplay and ItemDisplay entities. It creates no
world entities and never calls `addPassenger` on a Bukkit player. Attached displays are passengers
on each viewer's client, so their movement follows the player's client movement without moving
real passenger entities between Folia regions.

```java
var nametag = board.displays().nametag(player, Component.text("Player name"));
nametag.attach(player, 0.25);
nametag.textStyle(TextDisplayStyle.defaults());
nametag.style(DisplayStyle.defaults().transformed(
        DisplayTransform.identity().scaled(0.8f, 0.8f, 0.8f)));
nametag.hide(specificViewer.getUniqueId());
nametag.refresh();

var item = board.displays().item(player, 0.8, new ItemStack(Material.DIAMOND));
item.itemTransform(ItemDisplay.ItemDisplayTransform.FIXED);

var fixedText = board.displays().text(location, Component.text("Multiline\nText"));
fixedText.textFor(viewer -> Component.text(viewer.getName()));
fixedText.close();
```

Import the display API from `net.foliaboard.api.display`. All entry points are safe from any thread.
Locations and item inputs are copied; callers must not mutate an input while it is being copied.
Viewer predicates and content providers run on the viewer's owning thread. They must not read
another player's live state or perform blocking I/O. Use previously captured immutable data for
target-specific content. Provider exceptions suppress that viewer's display and increment
`displays().stats().providerFailures()`.

The default visibility policy hides attached displays from their owner, invisible targets and
spectators. It requires native player tracking, `viewer.canSee(target)`, the same world and a
48-block range. Self hiding is checked before providers and again before queued packet work.
`show(ownerUuid)`, refresh, teleport recovery and native passenger updates cannot override it.
Only an explicit policy with `selfVisible=true` enables owner visibility. UUID exclusions survive
viewer reconnects for the handle's lifetime. `visible(false)` suppresses everyone without deleting
the definition. `close()` is permanent and rejects further mutations.

Successful teleport events, world changes, death and respawn invalidate queued presentations.
After a short settling period, the owner scheduler captures the actual position and pose and
recreates accepted client displays with fresh entity IDs. Cancelled teleport events do not reset
the display. Rapid transitions invalidate older work. The owner's current passenger attachment
height determines the transform correction, including pose and scale changes. Native passenger
packets retain their passenger IDs and order; the library adds only its accepted virtual IDs.
Tracking loss destroys the virtual passengers, and tracking recovery permits recreation.

Owner disconnect closes attached handles. Create a new handle for the new login session. Fixed
displays remain registered until closed and are shown to eligible viewers after join or world
change. Virtual displays do not keep chunks loaded or leave entities in saved worlds.

Vanilla player names remain a separate scoreboard-team feature. To replace them, configure
`board.nametags().get(player).nametagVisibility(Nametag.Visibility.NEVER).apply()` as well. The
display service does not take ownership of another plugin's nametag team or restore its settings.

Check `displays().supported()` or `diagnose()` before creation. If the packet backend cannot bind,
creation reports an unsupported operation while the other FoliaBoard services remain usable.
`DisplayStats` reports handles, sessions, client entities and provider/transport failures;
`presentationStats()` includes the `DISPLAY` surface. Rendering and cleanup are asynchronous.
Other plugins that rewrite or cancel entity packets can affect client presentation; FoliaBoard
cannot guarantee visibility against a competing packet implementation or a modified client.

Text styles expose line width, ARGB background, unsigned opacity, shadow, see-through, default
background and alignment. Shared styles expose billboard, translation, quaternion rotations,
scale, lighting, interpolation, shadow, culling dimensions and glow. Item displays support every
Minecraft item rendering context. The API targets the repository's existing 1.20.6 to 1.21.11
range and adds no dependencies.

A **Folia-native, packet-level scoreboard API** for Paper & Folia. It removes the single hardest part
of scoreboards on Folia — knowing *which thread* may touch a player's board and not racing when they
move between regions — and gives you a fluent, MiniMessage-first API where **every call is safe from
any thread**.

```java
FoliaBoard board = FoliaBoard.create(this);

board.createBoard(player)
     .placeholders(true)
     .title("<gradient:#00c6ff:#0072ff><bold>MY SERVER</bold></gradient>")
     .blankLine()
     .lines("<gray>Player: <white>%player%",
            "<gray>Online: <green>%online%",
            "<gray>Ping: <aqua>%ping%ms")
     .blankLine()
     .line("<yellow>play.myserver.net")
     .build();
```

- **Zero threading work.** Call from the main thread, an async task, a region thread — anywhere.
- **Zero third-party deps.** Packets are built and sent directly. No ProtocolLib, no MegaVex.
- **MiniMessage everywhere.** Any `String` argument is parsed as MiniMessage (gradients, hover, click…).
- **Runs on Paper too.** The same jar works on plain Paper (everything just runs on the main thread).
- **Validated live on Folia 1.21.11.**

---

## Table of contents

1. [Why it exists](#why-it-exists)
2. [Requirements](#requirements)
3. [Installation](#installation)
4. [Quick start](#quick-start)
5. [Sidebars](#sidebars)
6. [Layout profiles](#layout-profiles)
7. [Nametags](#nametags)
8. [Below-name & tab-list numbers](#below-name--tab-list-numbers)
9. [Tab-list header & footer](#tab-list-header--footer)
10. [Number formats](#number-formats)
11. [Animations](#animations)
12. [Placeholders](#placeholders)
13. [MiniMessage & text](#minimessage--text)
14. [Events & hooks](#events--hooks)
15. [Async utilities](#async-utilities)
16. [Threading model](#threading-model)
17. [Performance](#performance)
18. [Lifecycle, cleanup & `/reload`](#lifecycle-cleanup--reload)
19. [API reference](#api-reference)
20. [Version support & the honest caveat](#version-support--the-honest-caveat)
21. [Building from source](#building-from-source)

---

## Why it exists

Packet scoreboard libraries are excellent but warn that their board/team objects are **not
thread-safe**. On Folia that warning is the whole game: there is no main thread, players tick on
**region threads**, they **migrate** between regions, and join/quit fires on region threads.

FoliaBoard's core idea: confine every mutation of a player's board to that player's
[`EntityScheduler`](https://docs.papermc.io/folia/reference/region-logic) — Folia's scheduler that
follows the entity across regions and runs its tasks strictly sequentially. That yields thread-safety
with **zero locks**, and it's all hidden. You describe *what* to show; FoliaBoard handles *where* and
*when* it's safe to send the packets.

---

## Requirements

- **Paper or Folia 1.20.6+** (modern per-score display components).
- **Java 21**.

---

## Installation

**1. Depend on the core library:**

```xml
	<repositories>
		<repository>
		    <id>jitpack.io</id>
		    <url>https://jitpack.io</url>
		</repository>
	</repositories>

	<dependency>
	    <groupId>com.github.SpirtySprite.folia-libs</groupId>
	    <artifactId>foliaboard-core</artifactId>
	    <version>foliaboard-v1.2.0</version>
	</dependency>
```

The library depends on `folia-commons` (`net.foliacommons:folia-commons`), which Maven pulls in for you. Shade
both into your plugin and relocate ``net.foliaboard`` and `net.foliacommons` to packages of your own.

**2. Mark your plugin Folia-ready** — required or it won't load on Folia:

```yaml
name: YourPlugin
main: com.yourplugin.YourPlugin
version: 1.0.0
api-version: '1.20'
folia-supported: true
softdepend: [PlaceholderAPI]   # optional; enables the placeholder bridge
```

---

## Quick start

Two styles — pick one.

**A. Instance (recommended):**
```java
public final class YourPlugin extends JavaPlugin {
    private FoliaBoard board;

    @Override public void onEnable()  { board = FoliaBoard.create(this); }
    @Override public void onDisable() { if (board != null) board.close(); }
}
```

**B. Static handle** (if you'd rather not pass the instance around):
```java
@Override public void onEnable()  { ScoreboardAPI.init(this); }
@Override public void onDisable() { ScoreboardAPI.shutdown(); }

// anywhere:
ScoreboardAPI.createBoard(player).title("<aqua>Hi").line("<gray>Welcome!").build();
```

For connected-player cleanup during disable, see [lifecycle hosts](#cleanup-hosts-and-metrics).

All examples below use a `board` (a `FoliaBoard`); with the static handle just write `ScoreboardAPI`
or `ScoreboardAPI.get()`.

Add `import static net.foliaboard.api.text.Text.mini;` (or use `Text.mini(...)`) when you want a
`Component` from a MiniMessage string.

---

## Sidebars

The right-hand board. There are four ways to drive one — from most convenient to most manual.

### 1. Fluent builder

```java
board.createBoard(player)
     .placeholders(true)              // resolve %built-ins% + PlaceholderAPI on strings
     .refreshEvery(4)                 // ticks between auto-refreshes of dynamic content
     .title("<gradient:#00c6ff:#0072ff><bold>MY SERVER</bold></gradient>")
     .blankLine()
     .line("<gray>Player: <white>%player%")
     .lines("<gray>Kills: <red>0", "<gray>Deaths: <red>0")   // several at once
     .blankLine()
     .line("<yellow>play.myserver.net")
     .build();                        // returns the live Sidebar
```

If any content is a placeholder string, an `Animation`, or a supplier, the board is **dynamic** and
FoliaBoard auto-refreshes it on the player's own thread — you never write a scheduler. Everything
else is painted once.

Titles and lines accept `String` (MiniMessage or legacy `&a` / `§a` / `&#ff00aa` codes), `Component`,
`Animation<Component>` or a per-player `Function<Player, String>`. Lines may also carry a
[number format](#number-formats).

Conditional lines only show when their predicate holds, and the rows below move up to fill the gap:

```java
board.createBoard(player)
     .placeholders(true)
     .title("&5&lNEXUS")
     .line("<gray>Money: <gold>%vault_eco_balance%")
     .lineIf(p -> p.hasPermission("staff"), "<red>Staff mode")
     .lineIf(p -> p.getWorld().getName().equals("event"), p -> "<aqua>Event: " + events.remaining())
     .build();
```

Refresh rate defaults to 3 ticks when an animation is present and 20 ticks for placeholder or
per-player content, so a board full of placeholders no longer resolves them six times a second.

### 2. Manual control

Full control, still safe from any thread:

```java
Sidebar sb = board.sidebar(player);         // get or create this player's sidebar
sb.title(mini("<gold>Kit Selector"));
sb.line(0, mini("<gray>Coins: <yellow>1500"));
sb.line(1, mini("<gray>Kills"), NumberFormat.fixed(mini("<red>12")));  // per-line number
sb.visible(false);                          // hide without discarding
sb.visible(true);
sb.clearLines();
sb.close();                                 // remove entirely (auto on quit)

// read-back:
Component title = sb.title();
List<Component> lines = sb.lines();
int count = sb.lineCount();
```

Only genuinely-changed lines produce packets, so frequent updates never flicker.

### 3. Global provider — one description for everyone

```java
board.boards().setGlobal(SidebarProvider.of(
    p -> mini("<aqua>MY SERVER"),
    p -> List.of(mini("<gray>Online: <green>" + Bukkit.getOnlinePlayers().size())),
    10));   // refresh every 10 ticks
```

Or implement the interface for `visible(player)` control. FoliaBoard attaches a sidebar to every
player, refreshes it per player on the right thread, and cleans up on quit.

### 4. Global layout — same shape, per-player content

```java
board.boards().setGlobal(Layout.named("main", b -> b
    .placeholders(true)
    .title("<aqua>MY SERVER")
    .line("<gray>Rank: <gold>%rank%")));
```

See [Layout profiles](#layout-profiles) for switching between several.

> **One driver per board.** A global provider/layout yields automatically to any explicit
> `createBoard(...).build()` or `boards().applyLayout(...)` for that player, so they never fight.

---

## Layout profiles

A **layout** is a reusable, named board template you can apply to any player and switch between
instantly (lobby ↔ minigame, per-world boards, …). It's just a recorded recipe of builder calls.

```java
Layout lobby = Layout.named("lobby", b -> b
    .placeholders(true)
    .title(Animations.cycle(Duration.ofMillis(400), mini("<aqua>LOBBY"), mini("<white>LOBBY")))
    .blankLine()
    .line("<gray>Rank: <gold>%rank%")
    .line("<gray>Coins: <yellow>%coins%"));

Layout minigame = Layout.named("minigame", b -> b
    .title("<red><bold>SKYWARS</bold>")
    .line("<gray>Kills", NumberFormat.fixed(mini("<red>0"))));

board.boards().registerLayout(lobby).registerLayout(minigame);

board.boards().applyLayout(player, "lobby");          // switch instantly, any time
board.boards().worldLayout("minigame_world", "minigame");  // auto-applied on join & world change
board.boards().unregisterLayout("minigame");          // remove a layout
```

- Applying a layout **replaces** the previous board cleanly (no stale leftover lines).
- Leaving a world-layout world for one with no layout **clears** the board.
- Fires a cancellable [`LayoutApplyEvent`](#events--hooks) so other plugins can override per rank/region.

---

## Nametags

Control the text around a player's name (above their head **and** in the tab list): prefix, suffix,
name colour, visibility, collision, and tab-list sort — sent as a scoreboard team, so it doesn't fight
Bukkit teams. Updates use team-*modify*, so they never flicker.

```java
board.createNametag(player)
     .prefix("<gold>[VIP] ")
     .suffix(" <gray>★")
     .color(NamedTextColor.YELLOW)
     .tabSort(10)                       // lower sorts higher in the tab list (0–9999)
     .nametagVisibility(Nametag.Visibility.ALWAYS)
     .collision(Nametag.Collision.NEVER)
     .apply();
```

### Per-viewer nametags

Show a target's name differently to different viewers — classic ally/enemy colouring:

```java
board.createNametag(player)
     .prefix("<gold>[VIP] ")
     .perViewer((viewer, target, style) -> {
         if (areAllies(viewer, target))  style.color(NamedTextColor.GREEN).prefix("<green>✦ ");
         else                            style.color(NamedTextColor.RED).prefix("<red>☠ ");
     })
     .apply();
```

The resolver runs per viewer on that viewer's thread; it starts from the global defaults. Call
`.apply()` again whenever relationships change (e.g. on a timer, or on a team-join event).

---

## Below-name & tab-list numbers

Shared objectives that show a number below every player's name, or beside their tab-list entry.

```java
board.objectives().belowName().title(mini("<red>❤")).score(player, 20);   // hearts below the name
board.objectives().tabList().score(player, player.getPing());             // ping in the tab list

board.objectives().belowName().remove(player.getName());                  // remove one entry
board.objectives().belowName().hide();                                    // hide for everyone…
board.objectives().belowName().show();                                    // …and bring it back
```

### Per-viewer numbers

```java
board.objectives().tabList().scoreFor(viewer, target.getName(), value);   // only `viewer` sees this value
board.objectives().tabList().removeFor(viewer, target.getName());         // revert to the shared value
```

Quit players are cleaned up automatically (no leaks, no phantom scores).

---

## Tab-list header & footer

```java
board.tabs().headerFooter(player,
    "<gradient:#00c6ff:#fff><bold>MY SERVER</bold></gradient>",
    "<gray>Online: <green>" + Bukkit.getOnlinePlayers().size());

board.clearTabHeaderFooter(player);
```

Accepts `String` (MiniMessage) or `Component`. Sent on the player's region thread.

### Tab-list entry styling & sorting

Style how a player appears **in the tab list**, independently of their above-head nametag, and sort
the list — **with no scoreboard team**, so it doesn't conflict with other team-based plugins.

```java
board.tabs().name(player, "<aqua>★ <white>" + player.getName());  // tab prefix ≠ above-head prefix
board.tabs().order(player, staff ? 100 : 0);                      // higher sorts higher (Paper 1.21.2+)
board.resetTabName(player);                                    // back to the vanilla name

if (!board.tabs().orderSupported()) { /* pre-1.21.2: use nametag tabSort instead */ }
```

- **Tab vs. above-head are now separate.** The above-head prefix comes from a [nametag](#nametags)
  (a team); the tab prefix comes from `tabs().name(...)` (no team). Use either or both.
- **Flicker-free, dynamic sorting.** `tabs().order(...)` changes instantly with no team-name trick.
  (On 1.21.1 and older, fall back to nametag `tabSort`.)
- **Team-conflict friendly.** Because tab styling needs no team, a server that already runs a
  team-based nametag/prefix plugin can use FoliaBoard purely for the tab list (and sidebars) without
  fighting over teams. Above-head prefixes still require a team — that's a vanilla limitation — so
  simply don't create FoliaBoard nametags if another plugin owns the above-head text.

`tabs().name`/`tabs().order` are per-target (shown the same to everyone), built on stable Paper API.

### Per-viewer tab names

To show a target a *different* tab name to *different* viewers, use the packet-level API:

```java
if (board.tabs().perViewerSupported()) {                 // 1.20.6+ with the player-info packet
    board.tabs().nameFor(viewer, target, "<red>ENEMY " + target.getName());
    board.resetTabNameFor(viewer, target);           // back to default
}
```

This is a **manual** send (no automatic lifecycle): re-apply it when you need it, e.g. on the
viewer's join or after the server resends player info. It's built on `ClientboundPlayerInfoUpdatePacket`
and **fails safe** — if the server build doesn't support it, `tabs().perViewerSupported()` returns false
and the calls no-op rather than erroring.

### Managed tab list

`board.tab(player)` builds a tab list that refreshes itself and only resends the parts that changed
(header and footer together, the entry name, the sort order):

```java
board.tab(player)
     .placeholders(true)
     .refreshEvery(20)
     .header(List.of("&5&lNEXUS", "<gray>%online% players online"))
     .footer("<gray>Ping: <white>%ping%ms")
     .name("%luckperms_prefix% <white>%player%")
     .orderByPermission("group.admin", "group.mod", "group.vip")
     .build();
```

For everyone at once, set a global layout. It is applied to online players immediately and to every
player who joins later, and closed on quit:

```java
board.tabs().setGlobal(TabLayout.of(tab -> tab
        .placeholders(true)
        .header("&5&lNEXUS")
        .footer("<gray>%online% online")
        .name(p -> (p.isOp() ? "<red>" : "<white>") + p.getName())));
board.tabs().clearGlobal();
```

`resetOnClose(true)` (the default) clears the header, footer and name when the tab closes.

---

## Boss bars

Per-player boss bars with placeholders, a dynamic progress and color, and an optional lifetime.
Bars are keyed by an id: showing a new bar with the same id replaces the old one.

```java
board.bossBar(player, "double-xp")
     .placeholders(true)
     .text("<gold>XP x2 <gray>ends in <white>%event_remaining%")
     .progress(p -> events.remainingRatio())
     .color(BossBar.Color.YELLOW)
     .refreshEvery(20)
     .hideAfter(20 * 60 * 5)
     .show();

board.bossBars().hide(player, "double-xp");
```

Progress is clamped to 0..1 (NaN becomes 0). Bars are hidden automatically when the player quits.

---

## Number formats

Control the red score number the client draws on the right of each entry (1.20.3+). Sidebars hide it
by default; override per line or on shared objectives.

```java
NumberFormat.blank();                              // hide it (sidebar default)
NumberFormat.fixed(mini("<red>✖"));                // replace it with any component
NumberFormat.styled(Style.style(NamedTextColor.GOLD));  // keep the number, restyle it
NumberFormat.defaultFormat();                      // the vanilla red number

board.sidebar(player).line(0, mini("<gray>Kills"), NumberFormat.fixed(mini("<red>12")));
```

---

## Animations

Self-timed — no ticking or registration. Call `current()` and return it; the frame is derived from the
clock.

```java
Animation<Component> title = Animations.cycle(Duration.ofMillis(400),
    mini("<aqua>HUB"), mini("<white>HUB"));

Animation<Component> marquee = Animations.scrollText(Duration.ofMillis(150),
    "welcome to the server!", 24, TextColor.color(0x8AB4F8));

Animation<Component> pulse = Animations.pulseColor(Duration.ofSeconds(2),
    "EVENT LIVE", TextColor.color(0xff0000), TextColor.color(0xffff00));

Animation<Component> typed = Animations.typewriter(Duration.ofMillis(80), Component.text("Loading…"));

Animation<Component> wave = Animations.gradientWave(Duration.ofSeconds(2), "NEXUS",
    TextColor.color(0xb44cff), TextColor.color(0x4cc9ff));

Animation<Component> frames = Animations.frames(Duration.ofMillis(500), "&5NEXUS", "&dNEXUS");
Animation<Component> alert = Animations.blink(Duration.ofMillis(500), mini("<red>!"));
Animation<Component> both = Animations.sequence(List.of(wave, frames), Duration.ofSeconds(5));
Animation<String> upper = frames.map(c -> Text.plain(c).toUpperCase(Locale.ROOT));

// use directly in a builder / layout:
board.createBoard(player).title(title).line(marquee).build();
```

`cycle`, `scrollText`, `pulseColor`, and `typewriter` are code-point safe (won't split emoji).
Animations are global wall-clock phase (every player sees the same frame at the same instant).

---

## Placeholders

A fast engine that replaces `%tokens%` using, in order: your resolvers → built-ins → PlaceholderAPI
(if installed; bridged reflectively, no hard dependency).

```java
board.placeholders().register("rank",  p -> p.isOp() ? "Admin" : "Member");
board.placeholders().register("coins", p -> economy.balance(p));

String  text = board.placeholders().apply(player, "Rank: %rank%");           // -> "Rank: Admin"
Component c  = board.placeholders().component(player, "<gray>Rank: <gold>%rank%");  // MiniMessage + %papi%
```

**Built-ins:** `%player%` / `%player_name%` / `%name%`, `%displayname%`, `%world%`, `%online%`,
`%max_players%`, `%ping%`, `%health%`, `%level%`, `%gamemode%`, `%x%` / `%y%` / `%z%`.

**Caching.** Expensive values can be cached per player for a duration. PlaceholderAPI results can be
cached the same way. Caches are dropped when the player quits.

```java
board.placeholders().register("balance", p -> economy.format(p), Duration.ofSeconds(2));
board.placeholders().cachePlaceholderApi(Duration.ofSeconds(1));
board.placeholders().invalidate("balance");
```

**Legacy colors.** Values that contain `&a`, `§a`, `&#rrggbb` or `§x§r§r§g§g§b§b` (typical of
PlaceholderAPI expansions and permission prefixes) are rendered as colors instead of raw codes. Turn it
off with `convertLegacyColors(false)`, in which case the codes are stripped.

**No double expansion.** Each `%token%` is resolved once: a value that itself contains `%other%` is
left as is, so a player can't smuggle placeholders through a nickname. PlaceholderAPI is detected
lazily, so it works even when it loads after your plugin. Tokens never contain spaces, so `50% off`
is left alone.

**Injection-safe:** in `component(...)`, placeholder *values* are escaped before parsing, so a value
like a display name containing `<red>` or a PAPI value with `<click:...>` renders literally and can't
inject formatting or click events into your board.

`%built-ins%` such as `%ping%` are cheapest read on the player's own thread — the builder/provider
already do that for you.

---

## MiniMessage & text

`Text` is the one-stop helper.

Parsing and serialization use `net.foliacommons.text.Text`. The wrapper retains its
component cache and escaping of all opening tag delimiters and backslashes.

```java
Component c   = Text.mini("<rainbow>hello</rainbow>");
Component tag = Text.mini("<hover:show_text:'<green>Click!'><click:run_command:/spawn>Spawn</click>");
String    mm  = Text.toMini(someComponent);   // round-trip back to a string
Component any = Text.parse("&6Gold <blue>and blue");   // legacy codes and MiniMessage together
Component hot = Text.cached("&5NEXUS");       // parsed once, reused
String    raw = Legacy.strip("&aHi §lthere");  // "Hi there"
String    safe = Text.escape(userInput);       // cannot inject tags
```

Every `String` argument across the API goes through MiniMessage, so you rarely need `Text` directly.

---

## Events & hooks

**Line processor** — rewrite every line/title of every board just before it's sent:

```java
board.boards().addLineProcessor((viewer, index, line) ->
    index == LineProcessor.TITLE ? line
        : line.decoration(TextDecoration.ITALIC, false));   // e.g. kill stray italics
```

**Bukkit events:**

```java
@EventHandler
public void onCreate(SidebarCreateEvent e) {
    getLogger().info("Board created for " + e.getPlayer().getName());
}

@EventHandler
public void onLayout(LayoutApplyEvent e) {          // cancellable + swappable
    if (e.getPlayer().hasPermission("vip")) e.setLayout(board.layout("vip_lobby"));
}
```

Both fire on the player's region thread (synchronous Folia-safe events).

---

## Async utilities

Folia-safe helpers for *your* surrounding work (FoliaBoard's own calls are already thread-safe):

```java
AsyncUtil.async(plugin, () -> {                       // off any game thread
    int coins = db.loadCoins(uuid);
    AsyncUtil.onPlayer(plugin, player, () ->           // hop to the player's region thread
        board.createBoard(player).line("<gold>Coins: " + coins).build());
});

AsyncUtil.asyncLater(plugin, task, Duration.ofSeconds(5));
AsyncUtil.global(plugin, () -> { /* global game state */ });
boolean folia = AsyncUtil.isFolia();
```

---

## Threading model

- **Managed presentation APIs are callable from any thread.** Mutations to a player's board are queued and applied on
  that player's region thread, in order, so nothing races.
- **On Paper** (non-Folia) everything runs on the main thread — the same code, no branches.
- Direct placeholder helpers run on their calling thread; use `componentAsync` for owner-thread rendering from arbitrary threads.
- **You never schedule anything** for scoreboard work. For your own logic, use `AsyncUtil`.

Internally: each `Sidebar` keeps *desired* state (written under a lock from any thread) and *sent*
state (touched only on the region thread inside a debounced flush that diffs and emits minimal
packets).

---

## Performance

FoliaBoard is built to stay cheap even with many players and fast, animated boards:

- **Minimal packets.** Sidebars diff desired-vs-sent state and send only changed lines. Below-name/tab
  score updates skip the broadcast entirely when the value is unchanged.
- **No re-parsing.** Dynamic placeholder lines cache their parsed MiniMessage and only re-parse when
  the resolved string actually changes.
- **Cheap conversions.** The (very common) empty component — blank spacer lines, empty titles — is
  converted to its vanilla form once and reused. Score-holder names are interned.
- **Fast send path.** Packets go out through cached `MethodHandle`s (with a reflection fallback).
- **No busy loops.** Nothing polls; work is event- and scheduler-driven, and refresh loops stop the
  instant a board closes or a player leaves.

Practical guidance: pick a `refreshEvery(...)` that matches your content — `2–4` ticks for smooth
animations, `10–20` for mostly-static boards. Static content isn't refreshed at all.

### Observability

`board.stats()` returns a snapshot for profiling TPS impact:

```java
FoliaBoardStats s = board.stats();
// s.totalPackets(), s.providerRefreshes(), s.activeSidebars(), s.activeNametags()
getLogger().info(s.toString());
```

FoliaBoard also warns (once) when a board exceeds the 15-line client limit or a single line/title is
unusually large (likely accidental payload bloat).

---

## Remembering a player's layout

Optionally persist which layout a player was on, so it's re-applied on their next join with no
join-listener glue. Back the store with anything (a map, a config, a database, a storage plugin):

```java
board.boards().layoutStore(new LayoutStore() {
    public CompletableFuture<Void> remember(UUID player, String layout) { return db.putAsync(player, layout); }
    public CompletableFuture<String> lastLayout(UUID player) { return db.getAsync(player); }
});
```

When set, `boards().applyLayout(...)` records the layout name, and FoliaBoard re-applies it on join (unless a
global or per-world layout already drives that player's board).

---

## Lifecycle, cleanup & `/reload`

- Create once in `onEnable`. `close()` cancels tasks, unregisters the listener and releases
  managed state. Its viewer removals are asynchronous and need an enabled scheduling plugin.
- For owner-disable cleanup while players remain connected, use `create(owner, lifecycleHost)`
  with an independent host that stays enabled until removals complete. The host observes owner
  disable and dispatches cleanup on each viewer's owning thread.
- A single-owner instance must close while its owner can still schedule. Calling `close()` from
  `onDisable` still releases library state, but cannot guarantee client removals after scheduling
  has been disabled. Quit cleanup and world-layout selection run through the registered listener.
- Use a full restart for server reloads. The fixture tests exercise an owner disable/enable cycle
  with a separate host; they do not make the server's `/reload` command a supported reload mechanism.

---

## API reference

| Type | Key members |
|---|---|
| `FoliaBoard` | `create(plugin)`, `boards()`, `tabs()`, `bossBars()`, `nametags()`, `objectives()`, and the shortcuts `createBoard(p)`, `sidebar(p)`, `createNametag(p)`, `nametag(p)`, `tab(p)`, `bossBar(p, id)`, plus `placeholders()`, `stats()`, `close()` |
| `Boards` | `create(p)`, `sidebar(p)`, `sidebarIfPresent(p)`, `remove(p)`, `setGlobal(provider\|layout)`, `clearGlobal()`, `registerLayout/unregisterLayout/layout`, `applyLayout(p, name\|layout)`, `worldLayout/clearWorldLayout`, `layoutStore`, `addLineProcessor/removeLineProcessor` |
| `Tabs` | `builder(p)`, `get(p)`, `remove(p)`, `setGlobal(layout)`, `clearGlobal()`, `name/resetName/order/orderSupported`, `nameFor/resetNameFor/perViewerSupported`, `headerFooter/clearHeaderFooter` |
| `BossBars` | `builder(p, id)`, `get(p, id)`, `hide(p, id)` |
| `Nametags` | `builder(p)`, `get(p)`, `getIfPresent(p)` |
| `Objectives` | `belowName()`, `tabList()` |
| `ScoreboardAPI` | `init(plugin)`, `get()`, `shutdown()`, `createBoard(p)`, `createNametag(p)`, `sidebar(p)` |
| `BoardBuilder` | `placeholders(bool)`, `refreshEvery(ticks)`, `title(...)`, `line(...)`, `lineIf(...)`, `lines(...)`, `blankLine()`, `build()` |
| `TabBuilder` / `TabList` / `TabLayout` | `header`, `footer`, `name`, `order`, `orderByPermission`, `placeholders`, `refreshEvery`, `resetOnClose`, `build()`, `refresh()`, `close()` |
| `BossBarBuilder` / `ManagedBossBar` | `text`, `progress`, `color`, `overlay`, `placeholders`, `refreshEvery`, `hideAfter`, `show()`, `refresh()`, `hide()` |
| `Sidebar` | `title(...)`, `line(...)`, `lines(...)`, `removeLine`, `clearLines`, `visible(...)`, `title()`, `lines()`, `lineCount()`, `close()` |
| `NametagBuilder` | `prefix/suffix/color/nametagVisibility/collision`, `tabSort(int)`, `perViewer(resolver)`, `apply()` |
| `Nametag` | `prefix/suffix/color/…`, `perViewer(resolver)`, `apply()`, `remove()` |
| `ScoreObjective` | `title(...)`, `score(player\|entry, v)`, `remove(entry)`, `scoreFor/removeFor(viewer,…)`, `hide()`, `show()` |
| `SidebarProvider` | `title(p)`, `lines(p)`, `visible(p)`, `refreshIntervalTicks()`, `of(...)` |
| `Layout` | `named(name, recipe)`, `applyTo(board, p)` |
| `NumberFormat` | `blank()`, `fixed(c)`, `styled(style)`, `defaultFormat()` |
| `Animations` | `cycle`, `frames`, `scrollText`, `pulseColor`, `typewriter`, `gradientWave`, `blink`, `sequence`, `mini` |
| `Placeholders` | `register(key, fn[, ttl])`, `unregister`, `invalidate`, `forget`, `cachePlaceholderApi`, `convertLegacyColors`, `apply`, `component`, `value` |
| `Text` / `Legacy` | `mini`, `parse`, `cached`, `plain`, `escape`, `toMini` / `toMini`, `strip`, `hasCodes` |
| `AsyncUtil` | `async`, `asyncLater`, `onPlayer`, `global`, `isFolia` |
| events / hooks | `SidebarCreateEvent`, `LayoutApplyEvent`, `LineProcessor` |

---

## Version support & the honest caveat

- Requires **Paper/Folia 1.20.6+**, **Java 21**. Targets the Mojang-mapped runtime and modern
  per-score display-component packets. **Validated live end-to-end on Folia 1.21.11.**
- The packet layer (`NmsPacketAdapter`) reaches into server internals by reflection, and adapts at
  load to whether score-packet fields are `Optional<…>` or `@Nullable`. It's the **only**
  version-specific file, and it fails **loudly at load** (never mid-game) if a handle can't resolve.
  If a future Minecraft release moves a field or changes a packet's shape, that one file is where you
  adjust it.

---

## Building from source

From the root of the repository:

```bash
mvn verify -pl foliaboard -am
```

This builds `folia-commons` (which FoliaBoard depends on) and FoliaBoard, runs the tests, and produces
`foliaboard/target/foliaboard-core-<version>.jar` together with the sources and javadoc jars. FoliaBoard
ships no `plugin.yml`; it is a library you shade into your own plugin.

Add `-Dfoliaboard.debug=true` to the server's JVM arguments to log every scoreboard packet.
## Presentation updates

The experimental APIs preserve existing builders and interfaces. Builders freeze their configuration
at `build()`. Later edits configure the next build. Indices are validated from 0 to 63; Minecraft
shows at most 15 rows, so include headers within that limit on each rotation page.

```java
Sidebar sidebar = board.createBoard(player)
    .title(titleAnimation).titleRefreshEvery(3)
    .line(p -> balances.current(p)).lineRefreshEvery(0, 20).build();
sidebar.refreshLine(0);
sidebar.refreshLine(-1);
sidebar.refresh();
SidebarState saved = sidebar.snapshot();
sidebar.replace(new SidebarState(Component.text("Summary"), List.of(
    new SidebarState.Line(Component.text("Coins"), Optional.of(NumberFormat.blank()))), true));
sidebar.replace(saved);
```

`replace` changes desired title, rows, formats and visibility under one lock and queues one diff,
which can send multiple packets. Snapshots are immutable desired frames. Custom implementations
inherit a sequential fallback and should override these methods for atomic updates and formats.
Dynamic title and row callbacks run on the player's owner thread at independent cadences. Failed
renderers preserve their previous value; failed processors preserve their input and processing
continues. Failures are logged. Neither callbacks nor resolvers may block for I/O.

```java
LayoutSection header = new LayoutSection("header", b -> b.title("Account").blankLine());
LayoutSection money = new LayoutSection("money", b -> b.line(p -> balances.current(p)));
Layout compact = Layout.sections("compact", List.of(header, money));
Layout detailed = compact.withSection(new LayoutSection("detail", b -> b.line("Extra details")));
board.createBoard(player).section(header.andThen(money)).build();
LayoutScope notice = board.boards().temporaryLayout(player, compact, 100);
notice.close();
try (SidebarRotation pages = board.boards().rotate(player, List.of(compact, detailed), 100)) {
    pages.next();
    pages.previous();
    int current = pages.page();
    int count = pages.pageCount();
}
```

Automatic precedence is registered world layout, global layout/provider, then remembered layout.
Manual builders and `applyLayout` take precedence over automatic selection. Temporary scopes nest
above that base. Closing the newest scope restores the next scope or reevaluates the base recipe.
A newer manual selection cancels existing scopes. Closing an older scope cannot overwrite a newer
one. Rotation uses these same lifetime rules, and shared sections provide headers. LayoutStore
callbacks run asynchronously; completions are checked against the current selection generation.
Manual layout persistence uses the layout accepted by `LayoutApplyEvent`; cancelled applications
do not write to the store. Disconnecting releases the generation entry, and rejoining assigns a
fresh generation. When automatic selection has no fallback, clearing the sidebar also removes its
refresh callback, so explicit refresh cannot restore the discarded layout.
`clearGlobal` clears both provider and layout selection for future joins and leaves manual boards.

Function-backed tab and boss-bar properties refresh every 20 ticks by default. `refreshEvery`
overrides that cadence. Boss-bar `hideAfter` expires independently, even when the refresh interval
is longer than its lifetime. Managed tabs with reset enabled restore managed header/footer, name
and order to their defaults on close, provided another owner has not replaced those fields.

## Animation playback controls

```java
AnimationTimeline<Component> timeline = Animations.timeline(Duration.ofMillis(150), frames);
timeline.pause();
timeline.seek(Duration.ofSeconds(1));
timeline.offset(Duration.ofMillis(50));
otherTimeline.synchronizeWith(timeline);
timeline.resume();
Duration position = timeline.elapsed();
boolean paused = timeline.paused();
timeline.restart();
board.createBoard(player).title(timeline).build();
AnimationTimeline<Long> clocked = new AnimationTimeline<>(nanos -> nanos, System::nanoTime);
```

Timelines use a monotonic nanosecond clock. Controls are thread-safe. Synchronization copies the
position and pause state; frame timelines loop. Existing animations remain maintained.

## Bounded caches and scheduled placeholders

```java
Placeholders placeholders = board.placeholders().cacheLimit(4096);
placeholders.pruneExpired();
long failures = placeholders.failures();
placeholders.componentAsync(Scheduler.forPlugin(this), player, "Coins: %coins%")
    .thenAccept(component -> consumeRendered(component));
```

Eviction removes individual entries. Expired placeholder values are removed on access, periodic
insertion, `cachedValues()` and explicit pruning. There is no background pruning task. Text parsing
uses a bounded access-order cache. Failed placeholder resolvers fall through to remaining resolvers
and built-ins; unresolved tokens stay visible. Failure counts appear in `diagnose()`.
Direct placeholder rendering executes on the caller's thread and requires the player's owner
thread for player/world access. Use `componentAsync` from arbitrary threads. Completion callbacks
inherit the completing thread unless explicitly scheduled.

## Cleanup hosts and metrics

```java
FoliaBoard managed = FoliaBoard.create(ownerPlugin, lifecycleHostPlugin);
PresentationStats stats = managed.presentationStats();
PresentationStats.Counters counts = stats.surface(PresentationStats.Surface.SIDEBAR);
long requested = counts.requests();
long changed = counts.changedOperations();
```

An independent host must remain enabled until removals reach all viewers. Its listener closes the
instance when the owner is disabled, and cleanup runs on each viewer's owner thread. With
`create(ownerPlugin)`, close while the owner can still schedule work: closing after it is disabled
cannot submit region cleanup. Simultaneous shutdown requires closing while the host stays enabled.
No cleanup call blocks a server thread.

Instances have separate objective and team identifiers, including relocated copies under the same
owner. Closing removes only owned identifiers. Minecraft has one sidebar, one below-name slot and
one team membership per entry; plugins must coordinate those shared surfaces. Closing does not
infer another plugin's previous display state. Tab reset restores defaults, so coordinate tab
ownership as well.

Snapshots provide counters for sidebar, team, objective, tab and boss-bar activity. Requests count
refresh or mutation attempts, including managed tab and boss-bar initial rendering, refresh and
cleanup. Changed operations count adapter calls or changed Adventure properties
after filtering and diffing, not per-surface wire packets. The existing `stats().totalPackets()`
retains its packet-adapter meaning. Retired viewers or refused scheduling may increase requests
without applying operations.
