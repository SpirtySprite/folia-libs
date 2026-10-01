package net.foliaboard.internal.service;

import net.foliaboard.FoliaBoard;
import net.foliaboard.api.BoardBuilder;
import net.foliaboard.api.Boards;
import net.foliaboard.api.LayoutStore;
import net.foliaboard.api.Sidebar;
import net.foliaboard.api.SidebarProvider;
import net.foliaboard.api.event.LayoutApplyEvent;
import net.foliaboard.api.event.SidebarCreateEvent;
import net.foliaboard.api.hook.LineProcessor;
import net.foliaboard.api.layout.Layout;
import net.foliaboard.internal.Ids;
import net.foliaboard.internal.board.SidebarImpl;
import net.foliaboard.internal.scheduler.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class SidebarService implements Boards {
    private final BoardRuntime runtime;
    private final FoliaBoard owner;

    private final Map<UUID, SidebarImpl> sidebars = new ConcurrentHashMap<>();
    private final Map<UUID, Schedulers.ScheduledHandle> refreshHandles = new ConcurrentHashMap<>();
    private final List<LineProcessor> lineProcessors = new CopyOnWriteArrayList<>();
    private final Map<String, Layout> layouts = new ConcurrentHashMap<>();
    private final Map<String, String> worldLayouts = new ConcurrentHashMap<>();
    private final Set<UUID> builderOwned = ConcurrentHashMap.newKeySet();
    private final AtomicInteger objectiveCounter = new AtomicInteger();

    private final Set<UUID> manualPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Layout> rememberedLayouts = new ConcurrentHashMap<>();
    private final Map<UUID, Runnable> manualRecipes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> generations = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();
    private final Map<UUID, CopyOnWriteArrayList<Scope>> scopes = new ConcurrentHashMap<>();
    private final Map<UUID, net.foliaboard.api.SidebarState> scopeBases = new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> automatic = ThreadLocal.withInitial(() -> false);

    private volatile SidebarProvider globalProvider;
    private volatile Schedulers.ScheduledHandle providerTask;
    private volatile Layout globalLayout;
    private volatile LayoutStore layoutStore;

    public SidebarService(@NotNull BoardRuntime runtime, @NotNull FoliaBoard owner) {
        this.runtime = runtime;
        this.owner = owner;
    }

    @Override
    public @NotNull BoardBuilder create(@NotNull Player player) {
        runtime.ensureOpen();
        return new BoardBuilder(this, player);
    }

    @Override
    public @NotNull Sidebar sidebar(@NotNull Player player) {
        runtime.ensureOpen();
        boolean[] created = {false};
        SidebarImpl sidebar = sidebars.computeIfAbsent(player.getUniqueId(), id -> {
            created[0] = true;
            SidebarImpl allocated = new SidebarImpl(runtime.plugin(), runtime.adapter(), player, nextObjectiveId(), lineProcessors);
            allocated.cleanupPlugin(runtime.cleanupPlugin());
            allocated.metrics(runtime.metrics());
            return allocated;
        });
        if (created[0]) {
            Schedulers.onEntity(runtime.plugin(), player,
                    () -> Bukkit.getPluginManager().callEvent(new SidebarCreateEvent(player, sidebar)));
        }
        return sidebar;
    }

    @Override
    public @Nullable Sidebar sidebarIfPresent(@NotNull Player player) {
        return sidebars.get(player.getUniqueId());
    }

    @Override
    public void remove(@NotNull Player player) {
        UUID id = player.getUniqueId();
        cancelRefresh(id);
        builderOwned.remove(id);
        manualPlayers.remove(id);
        manualRecipes.remove(id);
        rememberedLayouts.remove(id);
        generations.put(id, sequence.incrementAndGet());
        cancelScopes(id);
        SidebarImpl removed = sidebars.remove(id);
        if (removed != null) {
            removed.close();
        }
    }

    public int count() {
        return sidebars.size();
    }

    public void markBuilderOwned(@NotNull Player player) {
        UUID id = player.getUniqueId();
        builderOwned.add(id);
        if (!automatic.get()) {
            generations.put(id, sequence.incrementAndGet());
            manualPlayers.add(id);
            cancelScopes(id);
        }
    }

    public void trackRecipe(Player player, Runnable recipe) {
        if (!automatic.get()) {
            manualRecipes.put(player.getUniqueId(), recipe);
        }
    }

    public void trackRefresh(@NotNull Player player, @Nullable Schedulers.ScheduledHandle handle) {
        Schedulers.ScheduledHandle previous = handle == null
                ? refreshHandles.remove(player.getUniqueId())
                : refreshHandles.put(player.getUniqueId(), handle);
        if (previous != null) {
            previous.cancel();
        }
    }

    public void recordRefresh() {
        runtime.metrics().refresh();
    }

    public @NotNull BoardRuntime runtime() {
        return runtime;
    }

    private void cancelRefresh(UUID id) {
        Schedulers.ScheduledHandle handle = refreshHandles.remove(id);
        if (handle != null) {
            handle.cancel();
        }
    }

    private String nextObjectiveId() {
        return Ids.sidebarObjective(runtime.namespace(), objectiveCounter.getAndIncrement());
    }

    @Override
    public void setGlobal(@NotNull Layout layout) {
        runtime.ensureOpen();
        registerLayout(layout);
        clearGlobal();
        this.globalLayout = layout;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Schedulers.onEntity(runtime.plugin(), player, () -> restoreAutomatic(player));
        }
    }

    @Override
    public void setGlobal(@NotNull SidebarProvider provider) {
        runtime.ensureOpen();
        this.globalLayout = null;
        this.globalProvider = provider;
        Schedulers.ScheduledHandle old = this.providerTask;
        if (old != null) {
            old.cancel();
        }
        int interval = Math.max(1, provider.refreshIntervalTicks());
        this.providerTask = Schedulers.globalTimer(runtime.plugin(), () -> {
            SidebarProvider current = this.globalProvider;
            if (current == null) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                Schedulers.onEntity(runtime.plugin(), player, () -> refreshFromProvider(player, current));
            }
        }, interval, interval);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Schedulers.onEntity(runtime.plugin(), player, () -> restoreAutomatic(player));
        }
    }

    @Override
    public void clearGlobal() {
        this.globalLayout = null;
        this.globalProvider = null;
        Schedulers.ScheduledHandle task = this.providerTask;
        if (task != null) {
            task.cancel();
            this.providerTask = null;
        }
    }

    private void refreshFromProvider(Player player, SidebarProvider provider) {
        if (runtime.closed() || !player.isOnline() || provider != globalProvider) {
            return;
        }
        runtime.metrics().refresh();

        if (builderOwned.contains(player.getUniqueId())) {
            return;
        }
        Sidebar sidebar = sidebar(player);
        try {
            if (!provider.visible(player)) {
                sidebar.visible(false);
                return;
            }
            sidebar.replace(new net.foliaboard.api.SidebarState(provider.title(player),
                    provider.lines(player).stream().map(net.foliaboard.api.SidebarState.Line::new).toList(), true));
        } catch (RuntimeException failure) {
            runtime.plugin().getLogger().log(java.util.logging.Level.WARNING,
                    "Sidebar provider failed; preserving the previous frame", failure);
        }
    }

    @Override
    public @NotNull Boards registerLayout(@NotNull Layout layout) {
        layouts.put(layout.name().toLowerCase(Locale.ROOT), layout);
        return this;
    }

    @Override
    public @Nullable Layout layout(@NotNull String name) {
        return layouts.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public @NotNull Boards unregisterLayout(@NotNull String name) {
        layouts.remove(name.toLowerCase(Locale.ROOT));
        return this;
    }

    @Override
    public @NotNull Sidebar applyLayout(@NotNull Player player, @NotNull String layoutName) {
        Layout layout = layout(layoutName);
        if (layout == null) {
            throw new IllegalArgumentException("No layout registered named '" + layoutName + "'");
        }
        return applyLayout(player, layout);
    }

    @Override
    public @NotNull Sidebar applyLayout(@NotNull Player player, @NotNull Layout layout) {
        runtime.ensureOpen();
        java.util.Objects.requireNonNull(layout, "layout");
        markBuilderOwned(player);
        manualRecipes.put(player.getUniqueId(), () -> renderLayout(player, layout));
        long generation = generations.get(player.getUniqueId());
        Sidebar sidebar = sidebar(player);
        Schedulers.onEntity(runtime.plugin(), player, () -> {
            if (current(player, generation)) {
                renderLayout(player, layout);
                LayoutStore store = layoutStore;
                if (store != null) {
                    UUID id = player.getUniqueId();
                    String name = layout.name();
                    Schedulers.async(runtime.plugin(), () -> {
                        try {
                            store.remember(id, name).exceptionally(failure -> {
                                runtime.plugin().getLogger().warning("Could not remember layout: " + failure);
                                return null;
                            });
                        } catch (RuntimeException failure) {
                            runtime.plugin().getLogger().warning("Could not remember layout: " + failure);
                        }
                    });
                }
            }
        });
        return sidebar;
    }

    private void renderLayout(Player player, Layout layout) {
        LayoutApplyEvent event = new LayoutApplyEvent(player, layout);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            event.getLayout().applyTo(owner, player);
        }
    }

    private boolean current(Player player, long generation) {
        return !runtime.closed() && player.isOnline()
                && java.util.Objects.equals(generations.get(player.getUniqueId()), generation);
    }

    private void automatically(Runnable action) {
        boolean previous = automatic.get();
        automatic.set(true);
        try {
            action.run();
        } finally {
            automatic.set(previous);
        }
    }

    @Override
    public net.foliaboard.api.layout.LayoutScope temporaryLayout(Player player, Layout layout) {
        return createScope(player, layout, false, 0);
    }

    @Override
    public net.foliaboard.api.layout.LayoutScope temporaryLayout(Player player, Layout layout, long ticks) {
        return createScope(player, layout, true, Math.max(1, ticks));
    }

    private Scope createScope(Player player, Layout layout, boolean timed, long ticks) {
        runtime.ensureOpen();
        Scope scope = new Scope(java.util.Objects.requireNonNull(player, "player"),
                java.util.Objects.requireNonNull(layout, "layout"));
        scopeBases.computeIfAbsent(player.getUniqueId(), id -> {
            SidebarImpl sidebar = sidebars.get(id);
            return sidebar == null ? new net.foliaboard.api.SidebarState(net.kyori.adventure.text.Component.empty(), List.of(), true)
                    : sidebar.snapshot();
        });
        generations.put(player.getUniqueId(), sequence.incrementAndGet());
        scopes.computeIfAbsent(player.getUniqueId(), id -> new CopyOnWriteArrayList<>()).add(scope);
        boolean accepted = Schedulers.onEntity(runtime.plugin(), player, () -> {
            if (!scope.isCancelled() && !runtime.closed() && player.isOnline()) {
                List<Scope> active = scopes.get(player.getUniqueId());
                if (active != null && !active.isEmpty() && active.getLast() == scope) {
                    automatically(() -> renderLayout(player, scope.layout));
                }
            }
        }, scope::cancelWithoutRestore);
        if (!accepted) {
            scope.cancelWithoutRestore();
            List<Scope> active = scopes.get(player.getUniqueId());
            if (active != null) {
                active.remove(scope);
                if (active.isEmpty()) {
                    scopes.remove(player.getUniqueId(), active);
                    scopeBases.remove(player.getUniqueId());
                }
            }
        }
        if (timed) {
            scope.lifetime.add(Schedulers.entityLater(runtime.plugin(), player, scope::close, ticks));
        }
        return scope;
    }

    private boolean hasScope(UUID id) {
        List<Scope> active = scopes.get(id);
        return active != null && active.stream().anyMatch(scope -> !scope.isCancelled());
    }

    @Override
    public net.foliaboard.api.layout.SidebarRotation rotate(Player player, List<Layout> pages, long intervalTicks) {
        List<Layout> copy = List.copyOf(pages);
        if (copy.isEmpty() || intervalTicks < 1) {
            throw new IllegalArgumentException("Rotation needs pages and a positive interval");
        }
        Scope scope = createScope(player, copy.getFirst(), false, 0);
        Rotation rotation = new Rotation(scope, copy);
        scope.lifetime.add(Schedulers.entityTaskTimer(runtime.plugin(), player, handle -> {
            if (scope.isCancelled()) {
                handle.cancel();
            } else {
                rotation.next();
            }
        }, intervalTicks, intervalTicks));
        return rotation;
    }

    private final class Rotation implements net.foliaboard.api.layout.SidebarRotation {
        private final Scope scope;
        private final List<Layout> pages;
        private final AtomicInteger page = new AtomicInteger();

        private Rotation(Scope scope, List<Layout> pages) {
            this.scope = scope;
            this.pages = pages;
        }

        @Override
        public void next() {
            select(1);
        }

        @Override
        public void previous() {
            select(-1);
        }

        private void select(int step) {
            if (scope.isCancelled()) {
                return;
            }
            int index = page.updateAndGet(current -> Math.floorMod(current + step, pages.size()));
            scope.layout = pages.get(index);
            Schedulers.onEntity(runtime.plugin(), scope.player, () -> {
                List<Scope> active = scopes.get(scope.player.getUniqueId());
                if (!scope.isCancelled() && active != null && !active.isEmpty() && active.getLast() == scope) {
                    automatically(() -> renderLayout(scope.player, scope.layout));
                }
            });
        }

        @Override
        public int page() {
            return page.get();
        }

        @Override
        public int pageCount() {
            return pages.size();
        }

        @Override
        public boolean isCancelled() {
            return scope.isCancelled();
        }

        @Override
        public void close() {
            scope.close();
        }
    }

    private void cancelScopes(UUID id) {
        scopeBases.remove(id);
        List<Scope> active = scopes.remove(id);
        if (active != null) {
            active.forEach(Scope::cancelWithoutRestore);
        }
    }

    private void restoreBase(Player player) {
        UUID id = player.getUniqueId();
        net.foliaboard.api.SidebarState baseline = scopeBases.remove(id);
        cancelRefresh(id);
        SidebarImpl sidebar = sidebars.get(id);
        if (sidebar != null) {
            sidebar.refreshAction(null);
        }
        Runnable manual = manualRecipes.get(id);
        if (manual != null) {
            automatically(manual);
        } else {
            org.bukkit.World world = player.getWorld();
            String contextualName = world == null ? null : worldLayouts.get(world.getName());
            boolean contextual = contextualName != null && layout(contextualName) != null;
            if (!contextual && globalLayout == null && globalProvider == null && !rememberedLayouts.containsKey(id)
                    && baseline != null && sidebar != null) {
                sidebar.replace(baseline);
                builderOwned.remove(id);
            } else {
                restoreAutomatic(player);
            }
        }
    }

    private final class Scope implements net.foliaboard.api.layout.LayoutScope {
        private final Player player;
        private volatile Layout layout;
        private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        private final net.foliacommons.scheduler.TaskGroup lifetime = new net.foliacommons.scheduler.TaskGroup();

        private Scope(Player player, Layout layout) {
            this.player = player;
            this.layout = layout;
        }

        @Override
        public boolean isCancelled() {
            return closed.get();
        }

        private void cancelWithoutRestore() {
            closed.set(true);
            lifetime.cancel();
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            lifetime.cancel();
            Schedulers.onEntity(runtime.plugin(), player, () -> {
                List<Scope> active = scopes.get(player.getUniqueId());
                if (active == null || active.isEmpty()) {
                    return;
                }
                boolean top = active.getLast() == this || active.getLast().isCancelled();
                active.remove(this);
                active.removeIf(Scope::isCancelled);
                if (top && !runtime.closed() && player.isOnline()) {
                    if (!active.isEmpty()) {
                        Scope next = active.getLast();
                        automatically(() -> renderLayout(player, next.layout));
                    } else {
                        scopes.remove(player.getUniqueId(), active);
                        restoreBase(player);
                    }
                }
            });
        }
    }

    @Override
    public @NotNull Boards layoutStore(@NotNull LayoutStore store) {
        this.layoutStore = store;
        return this;
    }

    @Override
    public @NotNull Boards worldLayout(@NotNull String worldName, @NotNull String layoutName) {
        worldLayouts.put(worldName, layoutName);
        return this;
    }

    @Override
    public @NotNull Boards clearWorldLayout(@NotNull String worldName) {
        worldLayouts.remove(worldName);
        return this;
    }

    @Override
    public @NotNull Boards addLineProcessor(@NotNull LineProcessor processor) {
        lineProcessors.add(processor);
        return this;
    }

    @Override
    public @NotNull Boards removeLineProcessor(@NotNull LineProcessor processor) {
        lineProcessors.remove(processor);
        return this;
    }

    private void restoreAutomatic(Player player) {
        UUID id = player.getUniqueId();
        if (runtime.closed() || !player.isOnline() || manualPlayers.contains(id) || hasScope(id)) {
            return;
        }
        org.bukkit.World world = player.getWorld();
        String name = world == null ? null : worldLayouts.get(world.getName());
        Layout worldLayout = name == null ? null : layout(name);
        if (worldLayout != null) {
            automatically(() -> renderLayout(player, worldLayout));
        } else if (globalLayout != null) {
            automatically(() -> renderLayout(player, globalLayout));
        } else if (globalProvider != null) {
            cancelRefresh(id);
            SidebarImpl sidebar = sidebars.get(id);
            if (sidebar != null) {
                sidebar.refreshAction(null);
            }
            builderOwned.remove(id);
            refreshFromProvider(player, globalProvider);
        } else if (rememberedLayouts.containsKey(id)) {
            automatically(() -> renderLayout(player, rememberedLayouts.get(id)));
        } else {
            SidebarImpl sidebar = sidebars.get(id);
            if (sidebar != null) {
                sidebar.clearLines().title(net.kyori.adventure.text.Component.empty());
            }
            cancelRefresh(id);
            builderOwned.remove(id);
        }
    }

    public void onJoinProvider(@NotNull Player player) {
        SidebarProvider provider = globalProvider;
        if (provider != null) {
            Schedulers.onEntity(runtime.plugin(), player, () -> refreshFromProvider(player, provider));
        }
    }

    public void onJoinLayouts(@NotNull Player player) {
        UUID id = player.getUniqueId();
        long generation = sequence.incrementAndGet();
        generations.put(id, generation);
        Schedulers.onEntity(runtime.plugin(), player, () -> {
            if (!current(player, generation)) {
                return;
            }
            restoreAutomatic(player);
            org.bukkit.World world = player.getWorld();
            String contextualName = world == null ? null : worldLayouts.get(world.getName());
            boolean worldConfigured = contextualName != null && layout(contextualName) != null;
            LayoutStore store = layoutStore;
            if (store != null && globalLayout == null && globalProvider == null && !worldConfigured
                    && !manualPlayers.contains(id) && !hasScope(id)) {
                Schedulers.async(runtime.plugin(), () -> {
                    try {
                        store.lastLayout(id).whenComplete((name, failure) -> {
                            if (failure != null) {
                                runtime.plugin().getLogger().warning("Could not restore layout: " + failure);
                            } else if (name != null) {
                                Schedulers.onEntity(runtime.plugin(), player, () -> {
                                    Layout remembered = layout(name);
                                    if (current(player, generation) && remembered != null && !manualPlayers.contains(id)
                                            && !hasScope(id) && globalLayout == null && globalProvider == null) {
                                        rememberedLayouts.put(id, remembered);
                                        restoreAutomatic(player);
                                    }
                                });
                            }
                        });
                    } catch (RuntimeException failure) {
                        runtime.plugin().getLogger().warning("Could not restore layout: " + failure);
                    }
                });
            }
        });
    }

    public void onWorldChange(@NotNull Player player) {
        generations.put(player.getUniqueId(), sequence.incrementAndGet());
        Schedulers.onEntity(runtime.plugin(), player, () -> restoreAutomatic(player));
    }

    public void onQuit(@NotNull Player player) {
        remove(player);
    }

    public void closeAll() {
        clearGlobal();
        scopes.keySet().forEach(this::cancelScopes);
        scopeBases.clear();
        manualRecipes.clear();
        rememberedLayouts.clear();
        manualPlayers.clear();
        generations.clear();
        for (Schedulers.ScheduledHandle handle : refreshHandles.values()) {
            handle.cancel();
        }
        refreshHandles.clear();
        for (SidebarImpl sidebar : sidebars.values()) {
            sidebar.close();
        }
        sidebars.clear();
    }
}
