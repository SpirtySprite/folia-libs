package io.github.spirtysprite.integration;

import com.foliagui.FoliaGUI;
import com.foliagui.FoliaGUIService;
import com.foliagui.gui.Gui;
import net.foliaboard.FoliaBoard;
import net.foliacommons.FoliaEnvironment;
import net.foliacommons.diagnostics.Diagnostics;
import net.foliacommons.scheduler.Scheduler;
import net.foliacommons.scheduler.TaskGroup;
import net.foliacommons.version.ServerVersion;
import net.folianpc.api.Capabilities;
import net.folianpc.api.FoliaNpc;
import net.folianpc.api.Npc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Loads every library inside the running server, uses it, and writes {@code it-result.json} into the server
 * directory before stopping the server. The script that started the server reads that file.
 *
 * <p>Two system properties say what the server is expected to be: {@code it.expect.folia} ({@code true} or
 * {@code false}) and {@code it.expect.version} (for example {@code 1.21.4}).
 */
public final class IntegrationPlugin extends JavaPlugin implements Listener {

    private record Outcome(String name, boolean ok, String detail) {
    }

    private final List<Outcome> outcomes = new ArrayList<>();
    private Scheduler scheduler;
    private FoliaBoard board;
    private FoliaGUIService gui;
    private FoliaNpc npc;

    @Override
    public void onEnable() {
        scheduler = Scheduler.forPlugin(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private final java.util.concurrent.atomic.AtomicInteger menuClicks = new java.util.concurrent.atomic.AtomicInteger();
    private final Set<String> npcClicks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final CompletableFuture<Player> firstPlayer = new CompletableFuture<>();
    private final CompletableFuture<Player> secondPlayer = new CompletableFuture<>();

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!firstPlayer.complete(event.getPlayer())) secondPlayer.complete(event.getPlayer());
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP) {
            scheduler.runGlobal(this::runAllChecks);
        }
    }

    private void runAllChecks() {
        getLogger().info("Running the integration checks.");
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        chain = chain.thenCompose(v -> check("environment", this::environment));
        chain = chain.thenCompose(v -> check("scheduler-global", this::schedulerGlobal));
        chain = chain.thenCompose(v -> check("scheduler-async", this::schedulerAsync));
        chain = chain.thenCompose(v -> check("scheduler-entity", this::schedulerEntity));
        chain = chain.thenCompose(v -> check("scheduler-location", this::schedulerLocation));
        chain = chain.thenCompose(v -> check("scheduler-cancellation", this::schedulerCancellation));
        chain = chain.thenCompose(v -> check("board", this::board));
        chain = chain.thenCompose(v -> check("gui", this::gui));
        chain = chain.thenCompose(v -> check("npc", this::npc));
        chain = chain.thenCompose(v -> check("npc-tick", this::npcTick));
        if (Boolean.getBoolean("it.bot")) {
            chain = chain.thenCompose(v -> check("player-scenario", this::playerScenario, 120));
        }
        chain = chain.thenCompose(v -> check("shutdown", this::closeEverything));
        chain.whenComplete((ignored, failure) -> finish());
    }

    /** Runs one check, records its outcome, and never fails the chain so later checks still run. */
    private CompletableFuture<Void> check(String name, Check body) {
        return check(name, body, 30);
    }

    private CompletableFuture<Void> check(String name, Check body, int timeoutSeconds) {
        CompletableFuture<String> result;
        try {
            result = body.run().orTimeout(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Throwable thrown) {
            result = CompletableFuture.failedFuture(thrown);
        }
        return result.handle((detail, failure) -> {
            synchronized (outcomes) {
                if (failure == null) {
                    outcomes.add(new Outcome(name, true, detail == null ? "" : detail));
                    getLogger().info("PASS " + name + (detail == null || detail.isEmpty() ? "" : ": " + detail));
                } else {
                    Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure;
                    String reason = cause instanceof TimeoutException ? "timed out after " + timeoutSeconds + " s" : stackTrace(cause);
                    outcomes.add(new Outcome(name, false, reason));
                    getLogger().severe("FAIL " + name + ": " + reason);
                }
            }
            return null;
        });
    }

    @FunctionalInterface
    private interface Check {
        CompletableFuture<String> run() throws Exception;
    }

    private static CompletableFuture<String> done(String detail) {
        return CompletableFuture.completedFuture(detail);
    }

    private CompletableFuture<String> environment() {
        String expectedFolia = System.getProperty("it.expect.folia");
        String expectedVersion = System.getProperty("it.expect.version");
        if (expectedFolia != null && Boolean.parseBoolean(expectedFolia) != FoliaEnvironment.isFolia()) {
            throw new IllegalStateException("expected folia=" + expectedFolia + " but FoliaEnvironment.isFolia() is "
                    + FoliaEnvironment.isFolia());
        }
        String detected = ServerVersion.current().toString();
        if (expectedVersion != null && !expectedVersion.equals(detected)) {
            throw new IllegalStateException("expected Minecraft " + expectedVersion + " but ServerVersion.current() is "
                    + detected);
        }
        return done("folia=" + FoliaEnvironment.isFolia() + ", version=" + detected);
    }

    private CompletableFuture<String> schedulerGlobal() {
        CompletableFuture<String> result = new CompletableFuture<>();
        if (!scheduler.runGlobal(() -> result.complete("ran on " + Thread.currentThread().getName()))) {
            throw new IllegalStateException("runGlobal refused the task");
        }
        return result;
    }

    private CompletableFuture<String> schedulerAsync() {
        CompletableFuture<String> result = new CompletableFuture<>();
        if (!scheduler.runAsync(() -> result.complete("ran on " + Thread.currentThread().getName()))) {
            throw new IllegalStateException("runAsync refused the task");
        }
        return result;
    }

    private CompletableFuture<String> schedulerLocation() {
        Location location = Bukkit.getWorlds().get(0).getSpawnLocation();
        return scheduler.callForLocation(location, () -> {
            if (!Bukkit.getServer().isOwnedByCurrentRegion(location)) {
                throw new IllegalStateException("location call ran outside its owning region");
            }
            return "owned region on " + Thread.currentThread().getName();
        });
    }

    private CompletableFuture<String> schedulerCancellation() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        TaskGroup group = new TaskGroup();
        group.add(scheduler.scheduleGlobalLater(calls::incrementAndGet, 5));
        group.add(scheduler.scheduleAsyncLater(calls::incrementAndGet, java.time.Duration.ofMillis(250)));
        group.close();
        CompletableFuture<String> result = new CompletableFuture<>();
        scheduler.scheduleGlobalLater(() -> {
            if (calls.get() != 0) {
                result.completeExceptionally(new IllegalStateException("cancelled callbacks executed"));
            } else {
                result.complete("delayed global and async callbacks cancelled");
            }
        }, 10);
        return result;
    }

    /** Spawns a marker entity in the region that owns it, then schedules work on that entity. */
    private CompletableFuture<String> schedulerEntity() {
        World world = Bukkit.getWorlds().get(0);
        Location where = world.getSpawnLocation();
        CompletableFuture<String> result = new CompletableFuture<>();
        boolean accepted = scheduler.runForLocation(where, () -> {
            try {
                Entity marker = world.spawnEntity(where, EntityType.MARKER);
                boolean scheduled = scheduler.runForEntity(marker, () -> {
                    result.complete("ran for entity on " + Thread.currentThread().getName());
                    marker.remove();
                }, () -> result.completeExceptionally(new IllegalStateException("entity was retired before the task ran")));
                if (!scheduled) {
                    result.completeExceptionally(new IllegalStateException("runForEntity refused the task"));
                }
            } catch (Throwable thrown) {
                result.completeExceptionally(thrown);
            }
        });
        if (!accepted) {
            throw new IllegalStateException("runForLocation refused the task");
        }
        return result;
    }

    private CompletableFuture<String> board() {
        board = FoliaBoard.create(this);
        if (FoliaBoard.VERSION == null || FoliaBoard.VERSION.isBlank() || FoliaBoard.VERSION.equals("unknown")) {
            throw new IllegalStateException("FoliaBoard.VERSION was not read: " + FoliaBoard.VERSION);
        }
        Diagnostics report = board.diagnose();
        getLogger().info(report.toString());
        Set<String> limits = new HashSet<>();
        if (!ServerVersion.current().isAtLeast(21, 2)) {
            limits.add("Tab list ordering"); // documented: needs Paper 1.21.2
        }
        requireNoUnexpectedProblems("FoliaBoard", report, limits);
        return done("FoliaBoard " + FoliaBoard.VERSION + describe(limits));
    }

    private CompletableFuture<String> gui() {
        gui = FoliaGUI.create(this);
        if (FoliaGUI.VERSION == null || FoliaGUI.VERSION.isBlank() || FoliaGUI.VERSION.equals("unknown")) {
            throw new IllegalStateException("FoliaGUI.VERSION was not read: " + FoliaGUI.VERSION);
        }
        Gui menu = Gui.builder().service(gui).rows(3).title("Integration").create();
        if (menu.getInventory().getSize() != 27) {
            throw new IllegalStateException("a three row menu should have 27 slots, not " + menu.getInventory().getSize());
        }
        Diagnostics report = gui.diagnose();
        getLogger().info(report.toString());
        // Documented limits: these features need classes that only newer servers have, and the library must say so
        // instead of breaking.
        Set<String> limits = new HashSet<>();
        if (!classExists("io.papermc.paper.event.packet.UncheckedSignChangeEvent")) {
            limits.add("Sign text input");
        }
        if (!classExists("org.bukkit.inventory.view.AnvilView")) {
            limits.add("Anvil text input");
        }
        requireNoUnexpectedProblems("FoliaGUI", report, limits);
        // Registration can fail without an exception reaching the caller, which leaves every menu dead.
        boolean clicksHandled = java.util.Arrays.stream(
                        org.bukkit.event.inventory.InventoryClickEvent.getHandlerList().getRegisteredListeners())
                .anyMatch(registered -> registered.getListener().getClass().getSimpleName().equals("GuiListener"));
        if (!clicksHandled) {
            throw new IllegalStateException("FoliaGUI's click listener is not registered");
        }
        return done("FoliaGUI " + FoliaGUI.VERSION + describe(limits));
    }

    private CompletableFuture<String> npc() {
        npc = FoliaNpc.create(this);
        if (FoliaNpc.VERSION == null || FoliaNpc.VERSION.isBlank() || FoliaNpc.VERSION.equals("unknown")) {
            throw new IllegalStateException("FoliaNpc.VERSION was not read: " + FoliaNpc.VERSION);
        }
        Capabilities capabilities = npc.capabilities();
        Diagnostics report = npc.diagnose();
        getLogger().info(report.toString());
        if (!capabilities.complete()) {
            throw new IllegalStateException("FoliaNPC could not bind these features on this server: "
                    + capabilities.missing());
        }
        World world = Bukkit.getWorlds().get(0);
        Location where = world.getSpawnLocation();
        Npc created = npc.builder().name("IntegrationNpc").location(where).spawn();
        if (npc.stats().npcs() != 1) {
            throw new IllegalStateException("expected 1 NPC but the manager reports " + npc.stats().npcs());
        }
        created.remove();
        if (npc.stats().npcs() != 0) {
            throw new IllegalStateException("expected 0 NPCs after remove() but the manager reports " + npc.stats().npcs());
        }
        return done("FoliaNPC " + FoliaNpc.VERSION + ", all features bound");
    }

    /** Lets the NPC timer run a few passes with an NPC present, to catch errors that only appear while ticking. */
    private CompletableFuture<String> npcTick() {
        World world = Bukkit.getWorlds().get(0);
        npc.builder().name("TickedNpc").location(world.getSpawnLocation()).lookAtPlayers(true).spawn();
        CompletableFuture<String> result = new CompletableFuture<>();
        scheduler.runGlobalTimer(new Runnable() {
            private int passes;

            @Override
            public void run() {
                if (++passes == 10) {
                    result.complete("10 ticks with an NPC present, last visibility pass "
                            + String.format(Locale.ROOT, "%.3f", npc.stats().lastTickMillis()) + " ms");
                }
            }
        }, 1, 1);
        return result;
    }

    /**
     * Waits for the test bot to join, then gives it a sidebar, an NPC and an open menu, waits a moment for the packets
     * to be sent, and kicks it. The bot writes down what it received and the script checks that file.
     */
    private CompletableFuture<String> playerScenario() {
        return firstPlayer.thenCompose(player -> {
            CompletableFuture<String> done = new CompletableFuture<>();
            boolean accepted = scheduler.runForEntity(player, () -> {
                try {
                    board.boards().create(player)
                            .title("<gold>IT Board")
                            .line("<white>Line one")
                            .line("<green>Line two")
                            .build();
                    Location near = player.getLocation().add(3, 0, 0);
                    npc.builder().name("ItNpc").location(near)
                            .onClick((who, clicked, type) -> npcClicks.add(type.name()))
                            .spawn();
                    Gui menu = Gui.builder().service(gui).rows(3).title("Integration Menu").create();
                    menu.setItem(13, new com.foliagui.item.GuiItem(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND),
                            click -> menuClicks.incrementAndGet()));
                    menu.open(player);
                    installBoardFixtures(player);
                    TaskGroup scenarioTasks = new TaskGroup();
                    java.util.concurrent.atomic.AtomicBoolean finishing = new java.util.concurrent.atomic.AtomicBoolean();
                    java.util.concurrent.atomic.AtomicBoolean expectedDisconnect = new java.util.concurrent.atomic.AtomicBoolean();
                    done.whenComplete((result, failure) -> scenarioTasks.close());
                    scenarioTasks.add(scheduler.runGlobalTimer(new Runnable() {
                        private int ticks;

                        @Override
                        public void run() {
                            ticks++;
                            boolean allClicked = menuClicks.get() > 0 && npcClicks.contains("LEFT") && npcClicks.contains("RIGHT");
                            if ((ticks == 400 || allClicked && ticks >= 100) && finishing.compareAndSet(false, true)) {
                                NpcScenario.run(npc, player, scheduler).thenCompose(npcResult ->
                                        GuiScenario.run(gui, player, scheduler).thenCompose(guiResult ->
                                                DisplayScenario.run(board, player, scheduler).thenCompose(displayResult -> {
                                                    String summary = npcResult + "; " + guiResult + "; " + displayResult;
                                                    if (!Boolean.getBoolean("it.display.two")) return done(summary);
                                                    getLogger().info("DISPLAY_TWO_VIEWER_READY");
                                                    return secondPlayer.thenCompose(viewer ->
                                                            DisplayViewerScenario.run(board, player, viewer, scheduler))
                                                            .thenApply(remote -> summary + "; " + remote);
                                                })))
                                        .whenComplete((guiResult, guiFailure) -> {
                                    scheduler.runForEntity(player, () -> {
                                        expectedDisconnect.set(true);
                                        player.kick(net.kyori.adventure.text.Component.text("integration done"));
                                        String summary = "sidebar, NPC and menu sent to " + player.getName()
                                                + "; menu clicks=" + menuClicks.get() + ", NPC clicks=" + npcClicks;
                                        if (guiFailure != null) {
                                            done.completeExceptionally(guiFailure);
                                        } else if (allClicked) {
                                            done.complete(summary + "; " + guiResult);
                                        } else {
                                            done.completeExceptionally(new IllegalStateException(
                                                    "the player's clicks did not all arrive: " + summary));
                                        }
                                    }, () -> {
                                        if (!expectedDisconnect.get()) {
                                            done.completeExceptionally(new IllegalStateException("player left early"));
                                        }
                                    });
                                });
                            }
                        }
                    }, 1, 1));
                } catch (Throwable thrown) {
                    done.completeExceptionally(thrown);
                }
            }, () -> done.completeExceptionally(new IllegalStateException("the player left immediately")));
            if (!accepted) {
                done.completeExceptionally(new IllegalStateException("runForEntity refused the task"));
            }
            return done;
        });
    }

    private CompletableFuture<String> closeEverything() {
        board.close();
        npc.close();
        gui.close();
        if (!gui.isClosed()) {
            throw new IllegalStateException("the GUI service did not close");
        }
        return done("closed");
    }

    private void installBoardFixtures(Player player) throws ReflectiveOperationException {
        org.bukkit.plugin.Plugin first = Bukkit.getPluginManager().getPlugin("BoardFixtureA");
        org.bukkit.plugin.Plugin second = Bukkit.getPluginManager().getPlugin("BoardFixtureB");
        if (first == null || second == null) {
            return;
        }
        first.getClass().getMethod("install", Player.class).invoke(first, player);
        second.getClass().getMethod("install", Player.class).invoke(second, player);
        first.getClass().getMethod("hideObjective").invoke(first);
        scheduler.scheduleGlobalLater(() -> Bukkit.getPluginManager().disablePlugin(first), 20);
        scheduler.scheduleGlobalLater(() -> Bukkit.getPluginManager().enablePlugin(first), 60);
        scheduler.scheduleGlobalLater(() -> Bukkit.getPluginManager().disablePlugin(first), 80);
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, Bukkit.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    /** Fails on any problem the report lists that is not a documented limit of this server version. */
    private static void requireNoUnexpectedProblems(String library, Diagnostics report, Set<String> documentedLimits) {
        List<String> unexpected = new ArrayList<>(report.problems());
        unexpected.removeAll(documentedLimits);
        if (!unexpected.isEmpty()) {
            throw new IllegalStateException(library + " reports problems: " + unexpected);
        }
    }

    private static String describe(Set<String> limits) {
        return limits.isEmpty() ? "" : " (documented limits on this version: " + limits + ")";
    }

    private void finish() {
        boolean allPassed;
        StringBuilder json = new StringBuilder("{\n  \"checks\": [");
        synchronized (outcomes) {
            allPassed = outcomes.stream().allMatch(Outcome::ok) && !outcomes.isEmpty();
            for (int i = 0; i < outcomes.size(); i++) {
                Outcome outcome = outcomes.get(i);
                json.append(i == 0 ? "\n" : ",\n")
                        .append("    {\"name\": ").append(quote(outcome.name()))
                        .append(", \"ok\": ").append(outcome.ok())
                        .append(", \"detail\": ").append(quote(outcome.detail())).append('}');
            }
        }
        json.append("\n  ],\n  \"passed\": ").append(allPassed).append("\n}\n");
        try {
            Files.writeString(Path.of("it-result.json"), json.toString(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            getLogger().severe("Could not write it-result.json: " + failure);
        }
        getLogger().info(allPassed ? "All integration checks passed." : "Some integration checks FAILED.");
        Bukkit.shutdown();
    }

    private static String stackTrace(Throwable thrown) {
        StringWriter text = new StringWriter();
        thrown.printStackTrace(new PrintWriter(text));
        String full = text.toString();
        return full.length() > 4000 ? full.substring(0, 4000) + "..." : full;
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
