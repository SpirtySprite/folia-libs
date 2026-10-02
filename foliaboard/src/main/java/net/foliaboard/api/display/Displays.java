package net.foliaboard.api.display;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;

/** Managed virtual displays. Created handles are registered before scheduling and initially use default styles and visibility. */
@ApiStatus.Experimental
public interface Displays {
    /** Creates fixed text; the location is copied and presentation begins asynchronously. */
    ManagedTextDisplay text(Location location, Component text);

    /** Creates a client-only passenger nametag with a 0.25-block gap and permanently enforced default self hiding. */
    ManagedTextDisplay nametag(Player player, Component text);

    /** Creates a fixed item display; location and item are copied. */
    ManagedItemDisplay item(Location location, ItemStack item);

    /** Creates a client-only item passenger with the requested gap and default self hiding. */
    ManagedItemDisplay item(Player player, double gap, ItemStack item);

    /** Returns whether the complete text, item and passenger packet backend initialized successfully. */
    boolean supported();

    /** Returns a snapshot of active handles, viewers, applied client entities and failure counters. */
    DisplayStats stats();
}
