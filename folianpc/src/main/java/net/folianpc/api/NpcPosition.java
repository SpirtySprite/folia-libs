package net.folianpc.api;

import org.jetbrains.annotations.ApiStatus;
import java.util.Objects;

/** A coherent immutable packet-NPC position snapshot, independent of Bukkit entity ownership. */
@ApiStatus.Experimental
public record NpcPosition(String world, double x, double y, double z) {
    /** Requires a world name and finite coordinates. */
    public NpcPosition {
        Objects.requireNonNull(world, "world");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Invalid NPC position");
    }
}
