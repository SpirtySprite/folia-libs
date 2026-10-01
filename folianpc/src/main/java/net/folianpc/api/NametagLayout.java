package net.folianpc.api;

import org.jetbrains.annotations.ApiStatus;

/** Immutable hologram placement. Entity-relative offsets follow type, pose, baby state and scale. */
@ApiStatus.Experimental
public record NametagLayout(double spacing, double offset, boolean entityRelative) {
    /** Requires finite spacing and offset; spacing must be nonnegative. */
    public NametagLayout {
        if (!Double.isFinite(spacing) || !Double.isFinite(offset) || spacing < 0) {
            throw new IllegalArgumentException("Invalid nametag spacing or offset");
        }
    }

    /** Places lines 0.28 blocks apart and the bottom line 0.25 blocks above the entity. */
    public static NametagLayout defaults() { return new NametagLayout(0.28, 0.25, true); }
}
