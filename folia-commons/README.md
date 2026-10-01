# folia-commons

The small pieces FoliaBoard, FoliaGUI and FoliaNPC have in common. You can use them in your own plugin
too. Nothing here holds global state, so shading it next to another copy is harmless.

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
player left, and with your own exception if the task throws, so nothing ever hangs. `ensureForEntity` runs
the task immediately when the current thread already owns the player and only schedules when it does not.
`repeatForEntity` is a repeating task that receives a handle so it can cancel itself.

Nothing is scheduled once the plugin is disabled. One-shot methods return `false` and timers return
`TaskHandle.NOOP`, so shutdown paths do not throw.

For unit tests without a server, `Scheduler.synchronous()` runs every one-shot task immediately on the
calling thread and ignores timers.

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
