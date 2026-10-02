package net.foliaboard;

import net.foliaboard.api.BoardBuilder;
import net.foliaboard.api.display.Displays;
import net.foliaboard.internal.display.DisplayService;
import net.foliaboard.api.Boards;
import net.foliaboard.api.BossBarBuilder;
import net.foliaboard.api.BossBars;
import net.foliaboard.api.FoliaBoardStats;
import net.foliaboard.api.LayoutStore;
import net.foliaboard.api.ManagedBossBar;
import net.foliaboard.api.Nametag;
import net.foliaboard.api.NametagBuilder;
import net.foliaboard.api.Nametags;
import net.foliaboard.api.Objectives;
import net.foliaboard.api.ScoreObjective;
import net.foliaboard.api.Sidebar;
import net.foliaboard.api.SidebarProvider;
import net.foliaboard.api.TabBuilder;
import net.foliaboard.api.TabLayout;
import net.foliaboard.api.TabList;
import net.foliaboard.api.Tabs;
import net.foliaboard.api.hook.LineProcessor;
import net.foliaboard.api.layout.Layout;
import net.foliaboard.api.placeholder.Placeholders;
import net.foliaboard.internal.listener.FoliaBoardListener;
import net.foliacommons.diagnostics.Diagnostics;
import net.foliacommons.version.LibraryVersion;
import net.foliaboard.internal.packet.PacketAdapter;
import net.foliaboard.internal.packet.PacketAdapterFactory;
import net.foliaboard.internal.scheduler.Schedulers;
import net.foliaboard.internal.service.BoardLifecycle;
import net.foliaboard.internal.service.BoardRuntime;
import net.foliaboard.internal.service.BossBarService;
import net.foliaboard.internal.service.NametagService;
import net.foliaboard.internal.service.ObjectiveService;
import net.foliaboard.internal.service.SidebarService;
import net.foliaboard.internal.service.TabService;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Entry point of the library. One instance belongs to one plugin.
 *
 * <p>The features live in focused services: {@link #boards()}, {@link #tabs()}, {@link #bossBars()},
 * {@link #nametags()} and {@link #objectives()}. The everyday calls ({@link #createBoard},
 * {@link #sidebar}, {@link #createNametag}, {@link #nametag}, {@link #tab}, {@link #bossBar}) are also
 * available directly on this class. The other methods that used to live here still work but are
 * deprecated in favour of the services.
 */
public final class FoliaBoard {
    /** The version of this library, read from its packaged build metadata. */
    public static final String VERSION = LibraryVersion.read(FoliaBoard.class, "/foliaboard-version.properties");

    private final BoardRuntime runtime;
    private final SidebarService boards;
    private final TabService tabs;
    private final BossBarService bossBars;
    private final NametagService nametags;
    private final ObjectiveService objectives;
    private final DisplayService displays;
    private final BoardLifecycle lifecycle;
    private final FoliaBoardListener listener;

    private FoliaBoard(Plugin plugin, PacketAdapter adapter) {
        this(plugin, plugin, adapter);
    }

    private FoliaBoard(Plugin plugin, Plugin cleanupPlugin, PacketAdapter adapter) {
        this.runtime = new BoardRuntime(plugin, cleanupPlugin, adapter);
        this.boards = new SidebarService(runtime, this);
        this.tabs = new TabService(runtime);
        this.bossBars = new BossBarService(runtime);
        this.nametags = new NametagService(runtime);
        this.objectives = new ObjectiveService(runtime);
        this.displays = new DisplayService(runtime);
        this.lifecycle = new BoardLifecycle(runtime, boards, tabs, bossBars, nametags, objectives, displays);
        this.listener = new FoliaBoardListener(lifecycle, plugin, this::close);
    }

    public static @NotNull FoliaBoard create(@NotNull Plugin plugin) {
        PacketAdapter adapter = PacketAdapterFactory.create(plugin.getLogger());
        plugin.getLogger().info("FoliaBoard ready (" + (Schedulers.isFolia() ? "Folia" : "Paper") + " scheduling).");
        FoliaBoard board = new FoliaBoard(plugin, adapter);
        Bukkit.getPluginManager().registerEvents(board.listener, plugin);
        return board;
    }

    /**
     * Creates an instance with an enabled lifecycle host for cleanup after the owner is disabled.
     * The host must stay enabled until removals complete on the viewers' owning threads.
     * Register during plugin initialization. Without an independent host, close before disabling the owner.
     */
    @ApiStatus.Experimental
    public static @NotNull FoliaBoard create(@NotNull Plugin plugin, @NotNull Plugin cleanupHost) {
        java.util.Objects.requireNonNull(plugin, "plugin");
        java.util.Objects.requireNonNull(cleanupHost, "cleanupHost");
        if (!plugin.isEnabled() || !cleanupHost.isEnabled()) {
            throw new IllegalArgumentException("The owner and cleanup host must be enabled");
        }
        FoliaBoard board = new FoliaBoard(plugin, cleanupHost, PacketAdapterFactory.create(plugin.getLogger()));
        Bukkit.getPluginManager().registerEvents(board.listener, cleanupHost);
        return board;
    }

    /** Builds an instance around a given packet adapter without registering any listener. For tests. */
    @ApiStatus.Internal
    static @NotNull FoliaBoard withAdapter(@NotNull Plugin plugin, @NotNull PacketAdapter adapter) {
        return new FoliaBoard(plugin, adapter);
    }

    /** Lifecycle entry points that the Bukkit listener calls. Exposed for tests. */
    @ApiStatus.Internal
    BoardLifecycle lifecycle() {
        return lifecycle;
    }

    public @NotNull Boards boards() {
        return boards;
    }

    public @NotNull Tabs tabs() {
        return tabs;
    }

    public @NotNull BossBars bossBars() {
        return bossBars;
    }

    public @NotNull Nametags nametags() {
        return nametags;
    }

    public @NotNull Objectives objectives() {
        return objectives;
    }

    /** Returns managed text and item displays, including client-only passenger nametags. Safe from any thread. */
    @ApiStatus.Experimental
    public @NotNull Displays displays() {
        return displays;
    }

    public @NotNull FoliaBoardStats stats() {
        return new FoliaBoardStats(runtime.metrics().totalPackets(), runtime.metrics().refreshCount(),
                boards.count(), nametags.active());
    }

    /** Returns immutable per-surface request and applied-operation counters, readable from any thread. */
    @ApiStatus.Experimental
    public net.foliaboard.api.PresentationStats presentationStats() {
        return runtime.metrics().snapshot();
    }

    /**
     * A report of the server, the packet layer and which features work here. Log it or paste it into a bug
     * report.
     */
    public @NotNull Diagnostics diagnose() {
        FoliaBoardStats stats = stats();
        return Diagnostics.named("FoliaBoard " + VERSION)
                .withEnvironment()
                .section("Packet layer")
                .info("Adapter", runtime.adapter().describe())
                .section("Features")
                .ok("Sidebars")
                .ok("Nametags")
                .feature("Text, item and passenger displays", displays.supported(),
                        "the display packet backend could not initialize; other services remain available")
                .ok("Boss bars")
                .ok("Tab list header, footer and names")
                .feature("Tab list ordering", tabs.orderSupported(),
                        "needs Paper 1.21.2 or newer; use a nametag tabSort instead")
                .feature("Per-viewer tab names", tabs.perViewerSupported(),
                        "the player info packet was not found on this server")
                .section("Runtime")
                .info("Sidebars", String.valueOf(stats.activeSidebars()))
                .info("Nametags", String.valueOf(stats.activeNametags()))
                .info("Display handles", String.valueOf(displays.stats().handles()))
                .info("Display client entities", String.valueOf(displays.stats().clientEntities()))
                .info("Display transport failures", String.valueOf(displays.stats().transportFailures()))
                .info("Display provider failures", String.valueOf(displays.stats().providerFailures()))
                .info("Packets sent", String.valueOf(stats.totalPackets()))
                .info("Provider refreshes", String.valueOf(stats.providerRefreshes()))
                .info("Placeholder failures", String.valueOf(runtime.placeholders().failures()))
                .build();
    }

    public @NotNull Placeholders placeholders() {
        return runtime.placeholders();
    }

    public @NotNull Plugin plugin() {
        return runtime.plugin();
    }

    public @NotNull BoardBuilder createBoard(@NotNull Player player) {
        return boards.create(player);
    }

    public @NotNull Sidebar sidebar(@NotNull Player player) {
        return boards.sidebar(player);
    }

    public @NotNull NametagBuilder createNametag(@NotNull Player player) {
        return nametags.builder(player);
    }

    public @NotNull Nametag nametag(@NotNull Player target) {
        return nametags.get(target);
    }

    public @NotNull TabBuilder tab(@NotNull Player player) {
        return tabs.builder(player);
    }

    public @NotNull BossBarBuilder bossBar(@NotNull Player player, @NotNull String id) {
        return bossBars.builder(player, id);
    }

    public void close() {
        if (!lifecycle.close()) {
            return;
        }
        HandlerList.unregisterAll(listener);
    }

    /** @deprecated Use {@link #boards()} and its {@code sidebarIfPresent} operation. */
    @Deprecated(since = "1.1.0")
    public @Nullable Sidebar sidebarIfPresent(@NotNull Player player) {
        return boards.sidebarIfPresent(player);
    }

    /** @deprecated Use {@link #boards()} and its {@code remove} operation. */
    @Deprecated(since = "1.1.0")
    public void removeSidebar(@NotNull Player player) {
        boards.remove(player);
    }

    /** @deprecated Use {@link #boards()} and its {@code setGlobal} operation. */
    @Deprecated(since = "1.1.0")
    public void setGlobalSidebar(@NotNull Layout layout) {
        boards.setGlobal(layout);
    }

    /** @deprecated Use {@link #boards()} and its {@code setGlobal} operation. */
    @Deprecated(since = "1.1.0")
    public void setGlobalSidebar(@NotNull SidebarProvider provider) {
        boards.setGlobal(provider);
    }

    /** @deprecated Use {@link #boards()} and its {@code clearGlobal} operation. */
    @Deprecated(since = "1.1.0")
    public void clearGlobalSidebar() {
        boards.clearGlobal();
    }

    /** @deprecated Use {@link #boards()} and its {@code registerLayout} operation. */
    @Deprecated(since = "1.1.0")
    public @NotNull FoliaBoard registerLayout(@NotNull Layout layout) {
        boards.registerLayout(layout);
        return this;
    }

    /** @deprecated Use {@link #boards()} and its {@code layout} operation. */
    @Deprecated(since = "1.1.0")
    public @Nullable Layout layout(@NotNull String name) {
        return boards.layout(name);
    }

    /** @deprecated Use {@link #boards()} and its {@code unregisterLayout} operation. */
    @Deprecated(since = "1.1.0")
    public @NotNull FoliaBoard unregisterLayout(@NotNull String name) {
        boards.unregisterLayout(name);
        return this;
    }

    /** @deprecated Use {@link #boards()} and its {@code applyLayout} operation. */
    @Deprecated(since = "1.1.0")
    public @NotNull Sidebar applyLayout(@NotNull Player player, @NotNull String layoutName) {
        return boards.applyLayout(player, layoutName);
    }

    /** @deprecated Use {@link #boards()} and its {@code applyLayout} operation. */
    @Deprecated(since = "1.1.0")
    public @NotNull Sidebar applyLayout(@NotNull Player player, @NotNull Layout layout) {
        return boards.applyLayout(player, layout);
    }

    /** @deprecated Use {@link #boards()} and its {@code layoutStore} operation. */
    @Deprecated(since = "1.1.0")
    public @NotNull FoliaBoard setLayoutStore(@NotNull LayoutStore store) {
        boards.layoutStore(store);
        return this;
    }

    /** @deprecated Use {@link #boards()} and its {@code worldLayout} operation. */
    @Deprecated(since = "1.1.0")
    public @NotNull FoliaBoard setWorldLayout(@NotNull String worldName, @NotNull String layoutName) {
        boards.worldLayout(worldName, layoutName);
        return this;
    }

    /** @deprecated Use {@link #boards()} and its {@code clearWorldLayout} operation. */
    @Deprecated(since = "1.1.0")
    public @NotNull FoliaBoard clearWorldLayout(@NotNull String worldName) {
        boards.clearWorldLayout(worldName);
        return this;
    }

    /** @deprecated Use {@link #boards()} and its {@code addLineProcessor} operation. */
    @Deprecated(since = "1.1.0")
    public @NotNull FoliaBoard addLineProcessor(@NotNull LineProcessor processor) {
        boards.addLineProcessor(processor);
        return this;
    }

    /** @deprecated Use {@link #boards()} and its {@code removeLineProcessor} operation. */
    @Deprecated(since = "1.1.0")
    public @NotNull FoliaBoard removeLineProcessor(@NotNull LineProcessor processor) {
        boards.removeLineProcessor(processor);
        return this;
    }

    /** @deprecated Use {@link #nametags()} and its {@code getIfPresent} operation. */
    @Deprecated(since = "1.1.0")
    public @Nullable Nametag nametagIfPresent(@NotNull Player target) {
        return nametags.getIfPresent(target);
    }

    /** @deprecated Use {@link #objectives()} and its {@code belowName} operation. */
    @Deprecated(since = "1.1.0")
    public @NotNull ScoreObjective belowName() {
        return objectives.belowName();
    }

    /** @deprecated Use {@link #objectives()} and its {@code tabList} operation. */
    @Deprecated(since = "1.1.0")
    public @NotNull ScoreObjective tabList() {
        return objectives.tabList();
    }

    /** @deprecated Use {@link #tabs()} and its {@code get} operation. */
    @Deprecated(since = "1.1.0")
    public @Nullable TabList tabIfPresent(@NotNull Player player) {
        return tabs.get(player);
    }

    /** @deprecated Use {@link #tabs()} and its {@code remove} operation. */
    @Deprecated(since = "1.1.0")
    public void removeTab(@NotNull Player player) {
        tabs.remove(player);
    }

    /** @deprecated Use {@link #tabs()} and its {@code setGlobal} operation. */
    @Deprecated(since = "1.1.0")
    public void setGlobalTab(@NotNull TabLayout layout) {
        tabs.setGlobal(layout);
    }

    /** @deprecated Use {@link #tabs()} and its {@code clearGlobal} operation. */
    @Deprecated(since = "1.1.0")
    public void clearGlobalTab() {
        tabs.clearGlobal();
    }

    /** @deprecated Use {@link #bossBars()} and its {@code get} operation. */
    @Deprecated(since = "1.1.0")
    public @Nullable ManagedBossBar bossBarIfPresent(@NotNull Player player, @NotNull String id) {
        return bossBars.get(player, id);
    }

    /** @deprecated Use {@link #bossBars()} and its {@code hide} operation. */
    @Deprecated(since = "1.1.0")
    public void hideBossBar(@NotNull Player player, @NotNull String id) {
        bossBars.hide(player, id);
    }

    /** @deprecated Use {@link #tabs()} and its {@code name} operation. */
    @Deprecated(since = "1.1.0")
    public void tabName(@NotNull Player target, @NotNull String miniMessage) {
        tabs.name(target, miniMessage);
    }

    /** @deprecated Use {@link #tabs()} and its {@code name} operation. */
    @Deprecated(since = "1.1.0")
    public void tabName(@NotNull Player target, @NotNull ComponentLike name) {
        tabs.name(target, name);
    }

    /** @deprecated Use {@link #tabs()} and its {@code resetName} operation. */
    @Deprecated(since = "1.1.0")
    public void resetTabName(@NotNull Player target) {
        tabs.resetName(target);
    }

    /** @deprecated Use {@link #tabs()} and its {@code order} operation. */
    @Deprecated(since = "1.1.0")
    public void tabOrder(@NotNull Player target, int order) {
        tabs.order(target, order);
    }

    /** @deprecated Use {@link #tabs()} and its {@code orderSupported} operation. */
    @Deprecated(since = "1.1.0")
    public boolean tabOrderSupported() {
        return tabs.orderSupported();
    }

    /** @deprecated Use {@link #tabs()} and its {@code nameFor} operation. */
    @Deprecated(since = "1.1.0")
    public void tabNameFor(@NotNull Player viewer, @NotNull Player target, @NotNull String miniMessage) {
        tabs.nameFor(viewer, target, miniMessage);
    }

    /** @deprecated Use {@link #tabs()} and its {@code nameFor} operation. */
    @Deprecated(since = "1.1.0")
    public void tabNameFor(@NotNull Player viewer, @NotNull Player target, @NotNull ComponentLike name) {
        tabs.nameFor(viewer, target, name);
    }

    /** @deprecated Use {@link #tabs()} and its {@code resetNameFor} operation. */
    @Deprecated(since = "1.1.0")
    public void resetTabNameFor(@NotNull Player viewer, @NotNull Player target) {
        tabs.resetNameFor(viewer, target);
    }

    /** @deprecated Use {@link #tabs()} and its {@code perViewerSupported} operation. */
    @Deprecated(since = "1.1.0")
    public boolean perViewerTabSupported() {
        return tabs.perViewerSupported();
    }

    /** @deprecated Use {@link #tabs()} and its {@code headerFooter} operation. */
    @Deprecated(since = "1.1.0")
    public void tabHeaderFooter(@NotNull Player player, @NotNull String header, @NotNull String footer) {
        tabs.headerFooter(player, header, footer);
    }

    /** @deprecated Use {@link #tabs()} and its {@code headerFooter} operation. */
    @Deprecated(since = "1.1.0")
    public void tabHeaderFooter(@NotNull Player player, @NotNull ComponentLike header, @NotNull ComponentLike footer) {
        tabs.headerFooter(player, header, footer);
    }

    /** @deprecated Use {@link #tabs()} and its {@code clearHeaderFooter} operation. */
    @Deprecated(since = "1.1.0")
    public void clearTabHeaderFooter(@NotNull Player player) {
        tabs.clearHeaderFooter(player);
    }
}
