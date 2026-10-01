package net.foliaboard.internal.listener;

import net.foliaboard.internal.service.BoardLifecycle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

public final class FoliaBoardListener implements Listener {
    private final BoardLifecycle lifecycle;
    private final Plugin owner;
    private final Runnable close;

    public FoliaBoardListener(BoardLifecycle lifecycle) {
        this(lifecycle, null, lifecycle::close);
    }

    public FoliaBoardListener(BoardLifecycle lifecycle, Plugin owner, Runnable close) {
        this.lifecycle = lifecycle;
        this.owner = owner;
        this.close = close;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() == owner) {
            close.run();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        lifecycle.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        lifecycle.onWorldChange(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lifecycle.onQuit(event.getPlayer());
    }
}
