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
    private final Set<UUID> worldLayoutOwned = ConcurrentHashMap.newKeySet();
    private final AtomicInteger objectiveCounter = new AtomicInteger();

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
            return new SidebarImpl(runtime.plugin(), runtime.adapter(), player, nextObjectiveId(), lineProcessors);
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
        worldLayoutOwned.remove(id);
        SidebarImpl removed = sidebars.remove(id);
        if (removed != null) {
            removed.close();
        }
    }

    public int count() {
        return sidebars.size();
    }

    public void markBuilderOwned(@NotNull Player player) {
        builderOwned.add(player.getUniqueId());
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
            applyLayout(player, layout);
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
            Schedulers.onEntity(runtime.plugin(), player, () -> refreshFromProvider(player, provider));
        }
    }

    @Override
    public void clearGlobal() {
        this.globalProvider = null;
        Schedulers.ScheduledHandle task = this.providerTask;
        if (task != null) {
            task.cancel();
            this.providerTask = null;
        }
    }

    private void refreshFromProvider(Player player, SidebarProvider provider) {
        if (runtime.closed() || !player.isOnline()) {
            return;
        }
        runtime.metrics().refresh();

        if (builderOwned.contains(player.getUniqueId())) {
            return;
        }
        Sidebar sidebar = sidebar(player);
        if (!provider.visible(player)) {
            sidebar.visible(false);
            return;
        }
        sidebar.visible(true);
        sidebar.title(provider.title(player));
        sidebar.lines(provider.lines(player));
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
        markBuilderOwned(player);
        Sidebar sidebar = sidebar(player);
        Schedulers.onEntity(runtime.plugin(), player, () -> {
            LayoutApplyEvent event = new LayoutApplyEvent(player, layout);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                event.getLayout().applyTo(owner, player);
                LayoutStore store = layoutStore;
                if (store != null) {
                    UUID id = player.getUniqueId();
                    String name = event.getLayout().name();
                    Schedulers.async(runtime.plugin(), () -> store.remember(id, name));
                }
            }
        });
        return sidebar;
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

    private boolean applyWorldLayoutIfAny(Player player) {
        UUID id = player.getUniqueId();
        String layoutName = worldLayouts.get(player.getWorld().getName());
        if (layoutName != null) {
            Layout layout = layout(layoutName);
            if (layout != null) {
                applyLayout(player, layout);
                worldLayoutOwned.add(id);
                return true;
            }
        } else if (worldLayoutOwned.remove(id)) {
            remove(player);
        }
        return false;
    }

    /** First half of a join: start driving the player's sidebar from the global provider, if any. */
    public void onJoinProvider(@NotNull Player player) {
        SidebarProvider provider = globalProvider;
        if (provider != null) {
            Schedulers.onEntity(runtime.plugin(), player, () -> refreshFromProvider(player, provider));
        }
    }

    /** Second half of a join: apply the global layout, the world layout, or the remembered layout. */
    public void onJoinLayouts(@NotNull Player player) {
        Layout global = globalLayout;
        if (global != null) {
            applyLayout(player, global);
        }
        boolean worldLayoutApplied = applyWorldLayoutIfAny(player);

        LayoutStore store = layoutStore;
        if (store != null && global == null && !worldLayoutApplied) {
            store.lastLayout(player.getUniqueId()).thenAccept(name -> {
                if (name != null && !runtime.closed() && player.isOnline() && layout(name) != null) {
                    applyLayout(player, name);
                }
            });
        }
    }

    public void onWorldChange(@NotNull Player player) {
        applyWorldLayoutIfAny(player);
    }

    public void onQuit(@NotNull Player player) {
        remove(player);
    }

    public void closeAll() {
        clearGlobal();
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
