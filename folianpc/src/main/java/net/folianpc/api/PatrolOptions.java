package net.folianpc.api;

import org.jetbrains.annotations.ApiStatus;
import java.util.Objects;

/** Immutable patrol settings. Waits use server ticks and occur after each waypoint's arrival. */
@ApiStatus.Experimental
public record PatrolOptions(double speed, long waitTicks, boolean repeat, NavigationOptions navigation) {
    /** Requires finite positive speed, nonnegative waiting ticks and navigation settings. */
    public PatrolOptions {
        Objects.requireNonNull(navigation, "navigation");
        if (!Double.isFinite(speed) || speed <= 0 || waitTicks < 0) throw new IllegalArgumentException("Invalid patrol options");
    }
    /** Walks at four blocks per second without waiting or repeating. */
    public static PatrolOptions defaults() { return new PatrolOptions(4, 0, false, NavigationOptions.defaults()); }
}
