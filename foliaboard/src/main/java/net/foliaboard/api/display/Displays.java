package net.foliaboard.api.display;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import java.util.function.Function;
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

    /** Creates a tracked entity text passenger with the supplied gap. */
    ManagedTextDisplay text(Entity entity, double gap, Component text);

    /** Creates a tracked entity item passenger with the supplied gap. */
    ManagedItemDisplay item(Entity entity, double gap, ItemStack item);

    /** Creates a composition using an immutable developer-defined profile. */
    ManagedNametag<Boolean> nametag(Entity entity, NametagProfile profile);

    /** Creates a composition with owner-region sampling and viewer-region rendering. Sampler results must be immutable. */
    <T> ManagedNametag<T> nametag(Entity entity, NametagProfile profile, Function<Entity, T> sampler, NametagRenderer<T> renderer);

    /** Returns this runtime's registry of developer-defined profiles. */
    NametagProfiles profiles();

    /** Returns whether the complete text, item and passenger packet backend initialized successfully. */
    boolean supported();

    /** Returns a snapshot of active handles, viewers, applied client entities and failure counters. */
    DisplayStats stats();
}
