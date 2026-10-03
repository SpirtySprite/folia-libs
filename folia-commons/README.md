# folia-commons

The small pieces FoliaBoard, FoliaGUI and FoliaNPC have in common. You can use them in your own plugin
too. Shade and relocate it with the libraries you use. Plugin-bound result calls share shutdown
bookkeeping within one relocated copy; different plugin owners and relocated copies remain isolated.

## Scheduler

Runs work on the right thread on both Folia and Paper. On Folia every player and every region has its
own thread, so code that touches a player must run on that player's thread; on Paper it is all the main
thread. A `Scheduler` hides the difference.

```java
Scheduler scheduler = Scheduler.forPlugin(plugin);

scheduler.runForEntity(player, () -> player.sendMessage("Hello"), null);
scheduler.runForEntityLater(player, () -> player.sendMessage("Later"), null, 20);
TaskHandle timer = scheduler.runForEntityTimer(player, this::tick, null, 1, 20);
scheduler.runGlobal(() -> Bukkit.broadcast(Component.text("Global region")));
scheduler.runAsync(() -> saveToDatabase());
```

Need the result on another thread? `callForEntity` and `callGlobal` return a `CompletableFuture`:

```java
scheduler.callForEntity(player, () -> player.getHealth())
         .thenAccept(health -> getLogger().info("Health: " + health));
```

The future fails with a `SchedulingException` if the task could not run because the plugin is disabled or the
player left, and with your own exception if the task throws. Accepted, unfinished plugin-bound calls
also fail when the owner is disabled. Cancelling a returned future suppresses a supplier that has not
started; it cannot interrupt one already running. Custom schedulers define their own shutdown lifecycle.
`ensureForEntity` runs
the task immediately when the current thread already owns the player and only schedules when it does not.
`repeatForEntity` is a repeating task that receives a handle so it can cancel itself.

Nothing is scheduled once the plugin is disabled. One-shot methods return `false` and timers return
`TaskHandle.NOOP`, so shutdown paths do not throw.

For unit tests without a server, `Scheduler.synchronous()` runs every one-shot task immediately on the
calling thread and ignores timers.

### Location results and continuation threads

`callForLocation` reads a result on the region owning a snapshot of the supplied location. Access only
that region in its callback, even when nearby blocks belong to other independently ticking regions.

```java
Location target = location.clone();
scheduler.callForLocation(target, () -> target.getBlock().getType())
        .thenAccept(material -> scheduler.runForEntity(player,
                () -> player.sendMessage("Material: " + material), null));
```

`callAsync` returns a result from asynchronous work with the same plugin-disable and cancellation
lifecycle. Its supplier must use immutable snapshots or non-game resources, not live entities or blocks.

```java
scheduler.callAsync(() -> parseSavedData(bytes))
        .thenAccept(data -> scheduler.runForEntity(player, () -> applySavedData(player, data), null));
```

A non-async continuation can run on the completing entity, region, global, or shutdown thread. If the
future has already completed, it can run on the thread attaching the continuation. An async continuation
without an explicit executor normally uses the common pool. None of these continuations establishes
ownership of another player or region: schedule again before accessing game state.

### Cancellable work and task groups

New scheduling utilities are annotated `@ApiStatus.Experimental`. Existing `Scheduler` implementations
remain compatible through default methods. Plugin implementations cancel the actual scheduled task;
the default delayed entity/async adapters suppress callbacks if the underlying scheduler has no handle.
Cancellation cannot stop a callback that has already started. Retirement callbacks indicate entity
removal, not ordinary cancellation.

```java
TaskGroup session = new TaskGroup();
session.add(scheduler.scheduleForEntityLater(player, () -> player.sendMessage("Reminder"), null, 40));
session.add(scheduler.scheduleGlobalLater(() -> getLogger().info("Global reminder"), 40));
session.add(scheduler.scheduleAsyncLater(() -> saveToDatabase(), Duration.ofSeconds(2)));
session.add(scheduler.callForEntity(player, () -> player.getHealth()));
session.close();
```

Adding a task or future after closing a group cancels it immediately. `remove(handle)` detaches a task
without cancelling it. Completed futures are automatically released; detach completed one-shot handles
when keeping a group alive for a long time. A group is not automatically closed by player disconnect;
close it from your owning session's cleanup path.

Required entities, tasks, durations, and location worlds reject null even during shutdown. Existing tick
delays/periods clamp to one tick; asynchronous delays clamp to one millisecond, including negative values.
Duration conversions that exceed their numeric representation throw `ArithmeticException`.

### Deterministic tests

`Scheduler.deterministic()` queues work until its clock is advanced. Equal deadlines preserve submission
order. All scheduling domains share one queue: use live server tests to establish actual region ownership.

```java
try (DeterministicScheduler test = Scheduler.deterministic()) {
    CompletableFuture<String> result = test.callGlobal(() -> "ready");
    test.advanceTicks(1);
    assertEquals("ready", result.join());
    test.runGlobalTimer(this::tick, 1, 2);
    test.advance(Duration.ofMillis(250));
    test.retire(player);
    int queued = test.pendingTasks();
    long elapsedNanos = test.nanoTime();
}
```

Closing rejects new work and fails queued calls. Retirement cancels an entity's queued work and invokes
its retirement callbacks once. Callbacks execute on the advancing thread and must not block or advance
the clock recursively. `ensureForEntity` remains queued in this scheduler, while `synchronous()` executes
it immediately without consulting a live server.

## Monotonic deadlines

Use `Deadline` for cooldowns and TTLs that must not react to wall-clock adjustments. It uses
`System.nanoTime()` by default and accepts an injectable monotonic nanosecond source for tests. Budgets
must be non-negative and fit in nanoseconds; zero expires immediately.

```java
Deadline cooldown = Deadline.after(Duration.ofSeconds(5));
boolean ready = cooldown.expired();
Duration remaining = cooldown.remaining();
Deadline testDeadline = Deadline.after(Duration.ofMillis(100), testScheduler::nanoTime);
```

## ServerVersion

```java
ServerVersion version = ServerVersion.current();
version.isAtLeast(21, 4);      // 1.21.4 or newer
version.isCalendarScheme();    // 26.1 and later
ServerVersion.parse("1.21.4-R0.1-SNAPSHOT");
```

Calendar versions pass every `1.x` check, because they are newer than all of them.
Malformed or overflowing version strings become `0.0.0` and never enable a feature. Calendar numbering
is recognized from major version 26 onward; this does not extend the libraries' supported server range.

## Legacy

Converts legacy colour codes (`&a`, `§c`, `&#ff00aa`, `§x§1§2§3§4§5§6`) to MiniMessage:

```java
Legacy.toMini("&aHello &lworld");   // "<reset><green>Hello <bold>world"
Legacy.strip("&aHello");            // "Hello"
```

Incomplete and unrecognized codes remain literal. Valid codes appearing after an incomplete prefix
are still converted individually. Colors reset earlier decorations; Unicode and existing MiniMessage
markup are preserved.

## Text primitives

`net.foliacommons.text.Text` shares stateless parsing and escaping across the libraries. It does not
choose a null fallback or item italic style. The existing library-specific text helpers preserve those
choices, including FoliaGUI's italic defaults and FoliaNPC's empty-text fallback.

```java
Component mixed = Text.parse("&aHello <bold>world</bold>");
Component mini = Text.mini("<green>Hello");
Component custom = Text.mini("<player>", Placeholder.unparsed("player", playerName));
Component legacy = Text.legacyAmpersand("&aHello");
Component section = Text.legacySection("§aHello");
Component hex = Text.legacyHex("&#ff00aaHello");
String oldText = Text.toLegacy(mixed);
String markup = Text.toMini(mixed);
String plain = Text.plain(mixed);
String literal = Text.escape("<custom>\\value");
String defaultTagsOnly = Text.escapeTags("<green>value");
MiniMessage parser = Text.miniMessage();
```

`escape` escapes every tag opener and backslash, including custom tags. `escapeTags` retains the default
MiniMessage parser's escaping behavior. Parsing markup is for trusted templates; insert literal values
through escaping or unparsed placeholders. Required inputs reject null.

## Diagnostics

A small report a library can print so users can paste one block into a bug report:

```java
Diagnostics report = Diagnostics.named("MyLibrary 1.0")
        .withEnvironment()
        .section("Features")
        .ok("Sidebars")
        .degraded("Per-viewer tab names", "player info packet not found")
        .unavailable("Skins", "class not found")
        .build();

getLogger().info(report.toString());
report.healthy();    // false
report.problems();   // ["Per-viewer tab names", "Skins"]
```

Built reports are immutable snapshots. Reusing a builder cannot change an earlier report. Builders
serialize individual operations across threads; keep a complete section recipe on one thread to
preserve its grouping. Structured entries avoid parsing the report's display text:

```java
for (Diagnostics.Entry entry : report.entries()) {
    if (entry.status() == Diagnostics.Status.UNAVAILABLE) {
        getLogger().warning(entry.name() + ": " + entry.detail().orElse("unavailable"));
    }
}
```

## FoliaEnvironment

`FoliaEnvironment.isFolia()` is true on Folia and false on Paper.

## Library resources

Use an anchor class from the library and a library-specific resource name. Package relocation does not
rename resource paths, and a shaded plugin contains only one resource at each path. Distinct libraries
must use distinct names. Independent plugin class loaders can each own a resource with the same name.
Missing, unreadable, blank, or unfiltered versions return `LibraryVersion.UNKNOWN`.

```java
String version = LibraryVersion.read(MyLibrary.class, "/my-library-version.properties");
```
