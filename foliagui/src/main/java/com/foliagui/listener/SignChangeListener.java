package com.foliagui.listener;

import com.foliagui.FoliaGUIService;
import com.foliagui.gui.SignGui;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Receives the text a player typed into a {@link SignGui}.
 *
 * <p>This is a listener of its own because {@link UncheckedSignChangeEvent} only exists on recent Paper versions.
 * Bukkit refuses to register a listener class that mentions a missing event class, so if this handler lived in
 * {@link GuiListener}, every menu would stop working on an older server. It is only registered when the event exists.
 */
public final class SignChangeListener implements Listener {

    private final FoliaGUIService service;

    public SignChangeListener(FoliaGUIService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onSignChange(UncheckedSignChangeEvent event) {
        if (SignGui.handleSignChange(service, event.getPlayer(), event.lines())) {
            event.setCancelled(true);
        }
    }
}
