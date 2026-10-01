package net.foliaboard.internal.service;

import net.foliaboard.api.placeholder.Placeholders;
import net.foliaboard.internal.Ids;
import net.foliaboard.internal.metrics.PacketMetrics;
import net.foliaboard.internal.packet.PacketAdapter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/** What every service of one FoliaBoard instance shares. */
public final class BoardRuntime {
    private final Plugin plugin;
    private final Plugin cleanupPlugin;
    private final PacketAdapter adapter;
    private final Placeholders placeholders = new Placeholders();
    private final PacketMetrics metrics = new PacketMetrics();
    private final String namespace;
    private volatile boolean closed;

    public BoardRuntime(@NotNull Plugin plugin, @NotNull PacketAdapter adapter) {
        this(plugin, plugin, adapter);
    }

    public BoardRuntime(Plugin plugin, Plugin cleanupPlugin, PacketAdapter adapter) {
        this.plugin = plugin;
        this.cleanupPlugin = cleanupPlugin;
        this.adapter = new net.foliaboard.internal.packet.MeteredPacketAdapter(adapter, metrics);
        this.namespace = Ids.instanceNamespace();
        adapter.attachMetrics(metrics);
    }

    public @NotNull Plugin plugin() {
        return plugin;
    }

    public Plugin cleanupPlugin() {
        return cleanupPlugin;
    }

    public @NotNull PacketAdapter adapter() {
        return adapter;
    }

    public @NotNull Placeholders placeholders() {
        return placeholders;
    }

    public @NotNull PacketMetrics metrics() {
        return metrics;
    }

    public @NotNull String namespace() {
        return namespace;
    }

    public boolean closed() {
        return closed;
    }

    public void markClosed() {
        closed = true;
    }

    public void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("FoliaBoard has been closed");
        }
    }
}
