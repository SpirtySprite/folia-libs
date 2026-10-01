package net.foliacommons.scheduler;

import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class PluginCalls implements Listener {
    private static final ConcurrentMap<Plugin, PluginCalls> OWNERS = new ConcurrentHashMap<>();
    private final Plugin plugin;
    private final Set<CompletableFuture<?>> calls = new HashSet<>();
    private boolean closed;

    private PluginCalls(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    static void track(Plugin plugin, CompletableFuture<?> future) {
        if (!plugin.isEnabled()) {
            fail(future);
            return;
        }
        PluginCalls owner = OWNERS.computeIfAbsent(plugin, PluginCalls::new);
        boolean accepted;
        synchronized (owner) {
            accepted = !owner.closed && plugin.isEnabled();
            if (accepted) {
                owner.calls.add(future);
            }
        }
        if (!accepted) {
            fail(future);
            owner.close();
            return;
        }
        future.whenComplete((value, failure) -> {
            synchronized (owner) {
                owner.calls.remove(future);
            }
        });
        if (!plugin.isEnabled()) {
            owner.close();
        }
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            close();
        }
    }

    private void close() {
        List<CompletableFuture<?>> pending;
        synchronized (this) {
            closed = true;
            pending = List.copyOf(calls);
            calls.clear();
        }
        OWNERS.remove(plugin, this);
        HandlerList.unregisterAll(this);
        pending.forEach(PluginCalls::fail);
    }

    private static void fail(CompletableFuture<?> future) {
        future.completeExceptionally(new SchedulingException("The owner plugin was disabled before the call completed"));
    }
}
