package net.foliaboard.api.display;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

/** Runs on the viewer's owning thread using previously sampled immutable owner data. */
@ApiStatus.Experimental
@FunctionalInterface
public interface NametagRenderer<T> {
    /** Builds one complete layout. Do not read the owner's live entity from this callback. */
    NametagLayout render(Player viewer, T snapshot, NametagProfile profile);
}
