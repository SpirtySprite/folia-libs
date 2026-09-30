package net.foliaboard.internal.listener;

import net.foliaboard.internal.service.BoardLifecycle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class FoliaBoardListener implements Listener {
    private final BoardLifecycle lifecycle;

    public FoliaBoardListener(BoardLifecycle lifecycle) {
        this.lifecycle = lifecycle;
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
