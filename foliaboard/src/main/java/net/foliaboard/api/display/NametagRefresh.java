package net.foliaboard.api.display;

import org.jetbrains.annotations.ApiStatus;

/** Independent owner sampling and viewer rendering periods, measured in owning entity ticks. */
@ApiStatus.Experimental
public record NametagRefresh(int ownerTicks, int viewerTicks) {
    /** Requires positive periods. */
    public NametagRefresh {
        if (ownerTicks < 1 || viewerTicks < 1) throw new IllegalArgumentException("Refresh periods must be positive");
    }
    /** Samples and renders each tick. */
    public static NametagRefresh everyTick() { return new NametagRefresh(1, 1); }
}
