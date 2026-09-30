package net.foliaboard.internal.service;

import net.foliaboard.api.TabBuilder;
import net.foliaboard.api.TabLayout;
import net.foliaboard.api.TabList;
import net.foliaboard.api.Tabs;
import net.foliaboard.api.text.Text;
import net.foliaboard.internal.scheduler.Schedulers;
import net.foliaboard.internal.tab.TabOrder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TabService implements Tabs {
    private final BoardRuntime runtime;
    private final Map<UUID, TabList> tabs = new ConcurrentHashMap<>();
    private volatile TabLayout globalTab;

    public TabService(@NotNull BoardRuntime runtime) {
        this.runtime = runtime;
    }

    public @NotNull BoardRuntime runtime() {
        return runtime;
    }

    @Override
    public @NotNull TabBuilder builder(@NotNull Player player) {
        runtime.ensureOpen();
        return new TabBuilder(this, player);
    }

    @Override
    public @Nullable TabList get(@NotNull Player player) {
        return tabs.get(player.getUniqueId());
    }

    public void track(@NotNull Player player, @NotNull TabList tab) {
        TabList previous = tabs.put(player.getUniqueId(), tab);
        if (previous != null && previous != tab) {
            previous.close();
        }
    }

    @Override
    public void remove(@NotNull Player player) {
        TabList removed = tabs.remove(player.getUniqueId());
        if (removed != null) {
            removed.close();
        }
    }

    @Override
    public void setGlobal(@NotNull TabLayout layout) {
        runtime.ensureOpen();
        this.globalTab = layout;
        for (Player player : Bukkit.getOnlinePlayers()) {
            layout.applyTo(builder(player)).build();
        }
    }

    @Override
    public void clearGlobal() {
        this.globalTab = null;
        for (TabList tab : tabs.values()) {
            tab.close();
        }
        tabs.clear();
    }

    @Override
    public void name(@NotNull Player target, @NotNull String miniMessage) {
        name(target, Text.parse(miniMessage));
    }

    @Override
    public void name(@NotNull Player target, @NotNull ComponentLike name) {
        Component c = name.asComponent();
        Schedulers.onEntity(runtime.plugin(), target, () -> target.playerListName(c));
    }

    @Override
    public void resetName(@NotNull Player target) {
        Schedulers.onEntity(runtime.plugin(), target, () -> target.playerListName(null));
    }

    @Override
    public void order(@NotNull Player target, int order) {
        Schedulers.onEntity(runtime.plugin(), target, () -> TabOrder.set(target, order));
    }

    @Override
    public boolean orderSupported() {
        return TabOrder.supported();
    }

    @Override
    public void nameFor(@NotNull Player viewer, @NotNull Player target, @NotNull String miniMessage) {
        nameFor(viewer, target, Text.parse(miniMessage));
    }

    @Override
    public void nameFor(@NotNull Player viewer, @NotNull Player target, @NotNull ComponentLike name) {
        Component c = name.asComponent();
        Schedulers.onEntity(runtime.plugin(), viewer, () -> runtime.adapter().tabDisplayName(viewer, target, c));
    }

    @Override
    public void resetNameFor(@NotNull Player viewer, @NotNull Player target) {
        Schedulers.onEntity(runtime.plugin(), viewer, () -> runtime.adapter().tabDisplayName(viewer, target, null));
    }

    @Override
    public boolean perViewerSupported() {
        return runtime.adapter().supportsPerViewerTab();
    }

    @Override
    public void headerFooter(@NotNull Player player, @NotNull String header, @NotNull String footer) {
        headerFooter(player, Text.parse(header), Text.parse(footer));
    }

    @Override
    public void headerFooter(@NotNull Player player, @NotNull ComponentLike header, @NotNull ComponentLike footer) {
        Component h = header.asComponent();
        Component f = footer.asComponent();
        Schedulers.onEntity(runtime.plugin(), player, () -> player.sendPlayerListHeaderAndFooter(h, f));
    }

    @Override
    public void clearHeaderFooter(@NotNull Player player) {
        Schedulers.onEntity(runtime.plugin(), player,
                () -> player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty()));
    }

    public void onJoin(@NotNull Player player) {
        TabLayout layout = globalTab;
        if (layout != null) {
            layout.applyTo(builder(player)).build();
        }
    }

    public void onQuit(@NotNull Player player) {
        remove(player);
    }

    public void closeAll() {
        for (TabList tab : tabs.values()) {
            tab.close();
        }
        tabs.clear();
    }
}
