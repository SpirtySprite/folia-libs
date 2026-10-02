package net.foliaboard.api.display;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** A virtual display owned by FoliaBoard. All methods are safe from any thread; no live Bukkit entity is exposed. */
@ApiStatus.Experimental
public interface ManagedDisplay extends AutoCloseable {
    /** Stable library identifier, independent of recreated client entity identifiers. */
    UUID id();

    /** Replaces rendering properties for subsequent asynchronous presentation ticks. */
    void style(DisplayStyle style);

    /** Replaces the persistent visibility policy. Self visibility can change only through this explicit policy. */
    void visibility(DisplayVisibility visibility);

    /** Excludes this UUID until explicitly shown, surviving that viewer's reconnects for this handle's lifetime. */
    void hide(UUID viewer);

    /** Clears an exclusion; does not override self hiding, range, tracking or visibility conditions. */
    void show(UUID viewer);

    /** Sets global visibility without deleting the definition. */
    void visible(boolean visible);

    /** Replaces a filter run on each viewer's owning thread. Do not access other live entities from it. */
    void viewers(Predicate<Player> predicate);

    /** Moves to a fixed world position, replacing any passenger attachment. The location is copied. */
    void location(Location location);

    /** Attaches as a client-only passenger above the player's current height, with an additional vertical gap. */
    void attach(Player player, double gap);

    /** Recreates accepted presentations. Hidden viewers remain hidden. */
    void refresh();

    /** Returns the viewer UUIDs eligible at their last presentation tick as an immutable snapshot. */
    Set<UUID> viewers();

    /** Returns whether this handle has been permanently closed. */
    boolean isClosed();

    /** Permanently removes this display. Repeated calls are harmless; later mutations are rejected. */
    @Override
    void close();
}
